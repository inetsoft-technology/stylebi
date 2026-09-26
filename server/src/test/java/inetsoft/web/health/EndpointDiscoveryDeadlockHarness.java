/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.health;

import org.apache.catalina.core.StandardServer;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.*;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.actuate.autoconfigure.availability.AvailabilityHealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.availability.AvailabilityProbesAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.servlet.ServletManagementContextAutoConfiguration;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpointDiscoverer;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.availability.ApplicationAvailabilityAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.lang.management.*;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

/**
 * Bug #76974 harness. Boots a minimal Spring Boot servlet application with StyleBI's actuator
 * shape (global lazy init, a separate management port, health/metrics/prometheus web-exposed
 * at base path "/", health probes) and forces the startup interleaving from the thread dump
 * with hooks and latches:
 * <ol>
 *   <li>A request is fired at the server port as soon as it accepts
 *       ({@link ServletWebServerInitializedEvent}, phase -1024, on main). Its thread lazily
 *       initializes the parent DispatcherServlet.</li>
 *   <li>The "child side" (main in the management child refresh, or an 8081 request thread
 *       initializing the child DispatcherServlet) reserves a {@code filterEndpoints} bin in
 *       {@code EndpointDiscoverer.getFilterEndpoint} and parks inside the mapping function,
 *       before it asks the parent bean factory for the lazy endpoint bean. This is a
 *       {@code depends-on} gate on every {@code @Endpoint} bean, so it runs inside
 *       {@code doGetBean} before {@code getSingleton}, without taking any singleton lock.</li>
 *   <li>The request thread is held (a {@code depends-on} gate on
 *       {@code healthEndpointWebMvcHandlerMapping}, again before any lock) until the child side
 *       has parked. It then takes the parent singleton lock to create that bean. An
 *       instantiation-aware post-processor signals that, and waits until the child side is
 *       parked on the parent lock at {@code DefaultSingletonBeanRegistry.getSingleton} under
 *       {@code getFilterEndpoint}. Then the request thread runs its own discovery.</li>
 * </ol>
 * Nothing in the mechanism is replaced: the discoverer, its map, the bean factories and their
 * locks are Spring's own. The only probabilistic part is whether one of the request thread's
 * {@code EndpointBean} keys lands in the bin the child side reserved (identity hash, 16-bin
 * table). The harness therefore adds lazy, never-exposed filler endpoints so that the request
 * thread inserts 11 keys (below the table's resize threshold of 12), and callers retry fresh
 * contexts.
 * <p>
 * If the parent discoverer has already finished its discovery when the request arrives (which
 * is what a fix that discovers endpoints on main during bootstrap does), the request thread is
 * not held at its gate. Main is instead held when the management web server starts, until the
 * request thread holds the parent singleton lock inside {@code healthEndpointWebMvcHandlerMapping}
 * creation, so the same overlap is still forced.
 */
final class EndpointDiscoveryDeadlockHarness {
   private EndpointDiscoveryDeadlockHarness() {
   }

   static final String HEALTH_MAPPING = "healthEndpointWebMvcHandlerMapping";
   static final String DISCOVER_PATH = "/bug76974/discover";
   private static final String CHILD_SIDE_GATE = "bug76974ChildSideGate";
   private static final String REQUEST_SIDE_GATE = "bug76974RequestSideGate";
   private static final String DISCOVERER_CLASS =
      "org.springframework.boot.actuate.endpoint.annotation.EndpointDiscoverer";
   private static final long HOOK_WAIT_MS = 10_000;

   enum Shape {
      /**
       * The dump's shape: main, in the management child refresh, is the child side; a request on
       * the server port creates {@code healthEndpointWebMvcHandlerMapping} under the parent lock.
       */
      MAIN_AND_SERVER_REQUEST,
      /**
       * The 8081 shape (rows O-3 / W-R3b): main is held when the management web server starts;
       * a management-port request initializing the child DispatcherServlet is the child side,
       * and a server-port request is the parent-lock side. Main is not in the cycle.
       */
      MANAGEMENT_REQUEST_AND_SERVER_REQUEST,
      /**
       * Row C-R5: {@code healthEndpointWebMvcHandlerMapping} is removed and the server-port
       * request calls {@code WebEndpointsSupplier.getEndpoints()} from a controller method, so
       * the two discoveries overlap but neither runs inside a parent bean factory method.
       */
      DISCOVERY_OUTSIDE_PARENT_LOCK
   }

   enum Outcome { DEADLOCK, STARTED, FAILED, TIMED_OUT }

   record Options(Shape shape, List<Class<?>> extraSources, boolean fillerEndpoints,
                  Duration attemptTimeout)
   {
      static Options of(Shape shape, List<Class<?>> extraSources) {
         return new Options(shape, extraSources, true, Duration.ofSeconds(60));
      }
   }

   /**
    * The result of one boot.
    */
   static final class Attempt {
      Outcome outcome;
      ThreadInfo[] deadlocked = new ThreadInfo[0];
      Throwable failure;
      long durationMs;
      Thread harnessMain;
      volatile Thread requestThread;
      volatile Thread childSideThread;
      volatile boolean childSideParked;
      volatile int reservedBin = -1;
      volatile int tableLength = -1;
      volatile int reservationIdentity;
      volatile boolean discoveryDoneBeforeRequest;
      volatile boolean requestHeldParentLock;
      volatile boolean childSideBlockedOnParentLock;
      volatile boolean overlapObserved;
      volatile boolean requestBlockedOnReservationSeen;
      final List<String> trace = Collections.synchronizedList(new ArrayList<>());

      Optional<ThreadInfo> deadlockedInfo(Thread thread) {
         return thread == null ? Optional.empty() : Arrays.stream(deadlocked)
            .filter(i -> i.getThreadId() == thread.threadId()).findFirst();
      }

      String describe() {
         StringBuilder sb = new StringBuilder(describeWithoutStacks());

         for(ThreadInfo info : deadlocked) {
            sb.append(format(info));
         }

         return sb.toString();
      }

      String describeWithoutStacks() {
         StringBuilder sb = new StringBuilder();
         sb.append("outcome=").append(outcome).append(" in ").append(durationMs).append(" ms")
            .append(", childSideParked=").append(childSideParked)
            .append(", reservedBin=").append(reservedBin).append('/').append(tableLength)
            .append(", requestHeldParentLock=").append(requestHeldParentLock)
            .append(", childSideBlockedOnParentLock=").append(childSideBlockedOnParentLock)
            .append(", discoveryDoneBeforeRequest=").append(discoveryDoneBeforeRequest)
            .append(", overlapObserved=").append(overlapObserved)
            .append(", requestBlockedOnReservationSeen=").append(requestBlockedOnReservationSeen)
            .append('\n');
         synchronized(trace) {
            trace.forEach(t -> sb.append("  ").append(t).append('\n'));
         }

         if(failure != null) {
            sb.append("failure: ").append(failure).append('\n');
         }

         return sb.toString();
      }
   }

   /**
    * The detector is JVM-wide, and threads abandoned by an earlier deadlocked attempt can join
    * or extend a cycle later. Only a cycle containing a thread created by this attempt counts.
    */
   private static long[] newDeadlockedThreads(ThreadMXBean mx, Set<Long> preexisting) {
      long[] ids = mx.findDeadlockedThreads();

      if(ids == null || Arrays.stream(ids).allMatch(preexisting::contains)) {
         return null;
      }

      return Arrays.stream(ids).filter(id -> !preexisting.contains(id)).toArray();
   }

   static Attempt run(Options options) {
      HookState state = new HookState(options.shape());
      Attempt attempt = state.attempt;
      Set<Long> preexisting = new HashSet<>();
      Thread.getAllStackTraces().keySet().forEach(t -> preexisting.add(t.threadId()));
      AtomicReference<ConfigurableApplicationContext> context = new AtomicReference<>();
      long start = System.nanoTime();

      Thread main = new Thread(() -> {
         try {
            SpringApplicationBuilder builder = new SpringApplicationBuilder(HarnessApplication.class)
               .web(WebApplicationType.SERVLET)
               .registerShutdownHook(false)
               .properties(harnessProperties())
               .initializers(c -> {
                  state.parent = c;
                  c.addBeanFactoryPostProcessor(new HookInstaller(state));
               })
               .listeners(new WebServerListener(state));

            if(options.fillerEndpoints()) {
               builder.sources(FillerEndpoints.class);
            }

            if(options.shape() == Shape.DISCOVERY_OUTSIDE_PARENT_LOCK) {
               builder.sources(DiscoveryController.class);
            }

            if(!options.extraSources().isEmpty()) {
               builder.sources(options.extraSources().toArray(new Class<?>[0]));
            }

            context.set(builder.run());
         }
         catch(Throwable ex) {
            attempt.failure = ex;
         }
      }, "bug76974-main");
      main.setDaemon(true);
      attempt.harnessMain = main;
      main.start();

      ThreadMXBean mx = ManagementFactory.getThreadMXBean();
      long deadline = System.currentTimeMillis() + options.attemptTimeout().toMillis();

      while(true) {
         long[] ids = newDeadlockedThreads(mx, preexisting);

         if(ids != null) {
            attempt.outcome = Outcome.DEADLOCK;
            attempt.deadlocked = mx.getThreadInfo(ids, true, true);
            break;
         }

         if(!main.isAlive()) {
            attempt.outcome = attempt.failure == null ? Outcome.STARTED : Outcome.FAILED;
            break;
         }

         if(System.currentTimeMillis() > deadline) {
            attempt.outcome = Outcome.TIMED_OUT;
            break;
         }

         Thread request = attempt.requestThread;

         if(request != null) {
            ThreadInfo info = mx.getThreadInfo(request.threadId());

            if(info != null && info.getThreadState() == Thread.State.BLOCKED &&
               info.getLockName() != null && info.getLockName().contains("ReservationNode"))
            {
               attempt.requestBlockedOnReservationSeen = true;
            }
         }

         sleep(10);
      }

      attempt.durationMs = (System.nanoTime() - start) / 1_000_000;
      state.abandon();
      System.out.print("[bug76974] " + options.shape() + " " + attempt.describeWithoutStacks());

      if(attempt.outcome == Outcome.STARTED) {
         closeBounded(context.get());
      }
      else {
         // A deadlocked context cannot be closed: close() waits for the startup/shutdown lock
         // that the stuck refresh holds, and stopping Tomcat waits for the StandardWrapper
         // monitor that the stuck request thread holds. The context is abandoned instead. Every
         // thread it leaves behind is a daemon except Tomcat's "container-N" await threads,
         // which are released here so they cannot keep the JVM alive.
         state.releaseTomcatAwaitThreads();
      }

      return attempt;
   }

   /**
    * Properties matching StyleBI's application.yaml where they matter to the race. StyleBI's
    * own application.yaml (on this module's classpath) is deliberately not loaded.
    */
   private static Map<String, Object> harnessProperties() {
      Map<String, Object> props = new LinkedHashMap<>();
      props.put("spring.config.name", "bug76974-harness-no-config-file");
      props.put("spring.main.lazy-initialization", "true");
      props.put("spring.main.banner-mode", "off");
      props.put("spring.jmx.enabled", "false");
      props.put("server.port", "0");
      props.put("server.address", "127.0.0.1");
      props.put("management.server.port", "0");
      props.put("management.server.address", "127.0.0.1");
      props.put("management.endpoints.enabled-by-default", "false");
      props.put("management.endpoints.web.base-path", "/");
      props.put("management.endpoints.web.exposure.include", "health,metrics,prometheus");
      props.put("management.endpoint.health.enabled", "true");
      props.put("management.endpoint.metrics.enabled", "true");
      props.put("management.endpoint.prometheus.enabled", "true");
      props.put("management.endpoint.health.probes.enabled", "true");
      props.put("management.metrics.use-global-registry", "false");
      props.put("logging.level.root", "WARN");
      return props;
   }

   static String format(ThreadInfo info) {
      StringBuilder sb = new StringBuilder();
      sb.append('"').append(info.getThreadName()).append("\" ").append(info.getThreadState());

      if(info.getLockName() != null) {
         sb.append(" on ").append(info.getLockName());
      }

      if(info.getLockOwnerName() != null) {
         sb.append(" owned by \"").append(info.getLockOwnerName()).append('"');
      }

      sb.append('\n');
      StackTraceElement[] stack = info.getStackTrace();

      for(int i = 0; i < stack.length; i++) {
         sb.append("\tat ").append(stack[i]).append('\n');

         for(MonitorInfo monitor : info.getLockedMonitors()) {
            if(monitor.getLockedStackDepth() == i) {
               sb.append("\t- locked ").append(monitor).append('\n');
            }
         }
      }

      for(LockInfo sync : info.getLockedSynchronizers()) {
         sb.append("\t- owns ").append(sync).append('\n');
      }

      return sb.append('\n').toString();
   }

   private static void closeBounded(ConfigurableApplicationContext context) {
      if(context == null) {
         return;
      }

      Thread closer = new Thread(context::close, "bug76974-close");
      closer.setDaemon(true);
      closer.start();

      try {
         closer.join(30_000);
      }
      catch(InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }

   private static void sleep(long ms) {
      try {
         Thread.sleep(ms);
      }
      catch(InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }

   private static boolean await(CountDownLatch latch, BooleanSupplier orElse, HookState state) {
      long deadline = System.currentTimeMillis() + HOOK_WAIT_MS;

      while(System.currentTimeMillis() < deadline && !state.abandoned) {
         if(latch.getCount() == 0) {
            return true;
         }

         if(orElse.getAsBoolean()) {
            return false;
         }

         sleep(2);
      }

      return latch.getCount() == 0;
   }

   private static boolean waitUntil(BooleanSupplier condition, HookState state) {
      long deadline = System.currentTimeMillis() + HOOK_WAIT_MS;

      while(System.currentTimeMillis() < deadline && !state.abandoned) {
         if(condition.getAsBoolean()) {
            return true;
         }

         sleep(2);
      }

      return condition.getAsBoolean();
   }

   private static boolean onStack(StackTraceElement[] stack, String className, String method) {
      return indexOf(stack, className, method) >= 0;
   }

   static int indexOf(StackTraceElement[] stack, String className, String method) {
      for(int i = 0; i < stack.length; i++) {
         if(stack[i].getClassName().equals(className) && stack[i].getMethodName().equals(method)) {
            return i;
         }
      }

      return -1;
   }

   /**
    * State shared by the hooks of one boot.
    */
   static final class HookState {
      HookState(Shape shape) {
         this.shape = shape;
      }

      void trace(String format, Object... args) {
         attempt.trace.add(String.format("%6d ms [%s] ", (System.nanoTime() - t0) / 1_000_000,
                                         Thread.currentThread().getName()) +
                              String.format(format, args));
      }

      boolean discoveryComplete() {
         WebEndpointDiscoverer discoverer = this.discoverer;

         if(discoverer == null) {
            return false;
         }

         try {
            Field field = Class.forName(DISCOVERER_CLASS).getDeclaredField("endpoints");
            field.setAccessible(true);
            return field.get(discoverer) != null;
         }
         catch(ReflectiveOperationException e) {
            throw new IllegalStateException(e);
         }
      }

      void recordReservation() {
         try {
            Field field = Class.forName(DISCOVERER_CLASS).getDeclaredField("filterEndpoints");
            field.setAccessible(true);
            Object map = field.get(discoverer);
            Field tableField = ConcurrentHashMap.class.getDeclaredField("table");
            tableField.setAccessible(true);
            Object[] table = (Object[]) tableField.get(map);

            if(table == null) {
               return;
            }

            attempt.tableLength = table.length;

            for(int i = 0; i < table.length; i++) {
               if(table[i] != null && table[i].getClass().getName().endsWith("$ReservationNode")) {
                  attempt.reservedBin = i;
                  attempt.reservationIdentity = System.identityHashCode(table[i]);
               }
            }
         }
         catch(ReflectiveOperationException | RuntimeException e) {
            trace("could not read filterEndpoints: %s", e);
         }
      }

      boolean currentThreadHoldsParentSingletonLock() {
         try {
            Field field = Class.forName(
               "org.springframework.beans.factory.support.DefaultSingletonBeanRegistry")
               .getDeclaredField("singletonLock");
            field.setAccessible(true);
            return ((ReentrantLock) field.get(parent.getBeanFactory())).isHeldByCurrentThread();
         }
         catch(ReflectiveOperationException e) {
            throw new IllegalStateException(e);
         }
      }

      boolean childSideParkedOnParentLock() {
         Thread child = attempt.childSideThread;
         Thread request = attempt.requestThread;
         ThreadInfo info = ManagementFactory.getThreadMXBean()
            .getThreadInfo(child.threadId(), Integer.MAX_VALUE);

         if(info == null || info.getThreadState() != Thread.State.WAITING ||
            request == null || info.getLockOwnerId() != request.threadId())
         {
            return false;
         }

         StackTraceElement[] stack = info.getStackTrace();
         int getSingleton = indexOf(
            stack, "org.springframework.beans.factory.support.DefaultSingletonBeanRegistry",
            "getSingleton");
         int filter = indexOf(stack, DISCOVERER_CLASS, "getFilterEndpoint");
         return getSingleton >= 0 && filter > getSingleton;
      }

      boolean mainInsideChildRefresh() {
         return onStack(
            attempt.harnessMain.getStackTrace(),
            "org.springframework.boot.actuate.autoconfigure.web.server.ChildManagementContextInitializer",
            "start");
      }

      boolean isChildSideCandidate(Thread thread) {
         return switch(shape) {
            case MAIN_AND_SERVER_REQUEST, DISCOVERY_OUTSIDE_PARENT_LOCK ->
               thread == attempt.harnessMain;
            case MANAGEMENT_REQUEST_AND_SERVER_REQUEST ->
               thread != attempt.harnessMain && thread != attempt.requestThread;
         };
      }

      /**
       * Runs on the server-port request thread before it takes the parent singleton lock.
       */
      void requestSideGate() {
         if(!armed || !requestSideClaimed.compareAndSet(false, true)) {
            return;
         }

         attempt.requestThread = Thread.currentThread();

         if(discoveryComplete()) {
            attempt.discoveryDoneBeforeRequest = true;
            trace("request side: parent discovery already complete, not held");
            return;
         }

         boolean parked = await(childSideParked, this::discoveryComplete, this);
         trace("request side released: childSideParked=%s", parked);
      }

      /**
       * Runs on the child side inside getFilterEndpoint's mapping function (bin reserved),
       * before the parent getBean takes the parent singleton lock.
       */
      void childSideGate() {
         Thread thread = Thread.currentThread();

         if(!armed || !isChildSideCandidate(thread) ||
            !onStack(thread.getStackTrace(), DISCOVERER_CLASS, "getFilterEndpoint") ||
            !childSideClaimed.compareAndSet(false, true))
         {
            return;
         }

         attempt.childSideThread = thread;
         recordReservation();
         attempt.childSideParked = true;
         trace("child side reserved bin %d of %d and parked", attempt.reservedBin,
               attempt.tableLength);
         childSideParked.countDown();
         boolean locked = await(requestHoldsParentLock, () -> requestDone.getCount() == 0, this);
         trace("child side released: requestHoldsParentLock=%s", locked);
      }

      /**
       * Runs on the request thread while it holds the parent singleton lock.
       */
      void requestHoldsParentLock(String beanName) {
         attempt.requestHeldParentLock = currentThreadHoldsParentSingletonLock();
         trace("request side holds parent singleton lock creating %s: %s", beanName,
               attempt.requestHeldParentLock);
         requestHoldsParentLock.countDown();

         if(attempt.childSideParked) {
            attempt.childSideBlockedOnParentLock =
               waitUntil(this::childSideParkedOnParentLock, this);
            trace("child side parked on parent lock under getFilterEndpoint: %s",
                  attempt.childSideBlockedOnParentLock);
         }

         attempt.overlapObserved = waitUntil(this::mainInsideChildRefresh, this);
         trace("main inside ChildManagementContextInitializer.start: %s", attempt.overlapObserved);
      }

      void fire(String name, int port, String path) {
         Thread client = new Thread(() -> {
            try {
               HttpURLConnection conn = (HttpURLConnection)
                  URI.create("http://127.0.0.1:" + port + path).toURL().openConnection();
               conn.setConnectTimeout(5_000);
               conn.setReadTimeout(120_000);
               int status = conn.getResponseCode();

               try(InputStream in = status < 400 ? conn.getInputStream() : conn.getErrorStream()) {
                  if(in != null) {
                     in.readAllBytes();
                  }
               }

               trace("%s request %s -> %d", name, path, status);
            }
            catch(Exception e) {
               trace("%s request %s failed: %s", name, path, e);
            }
            finally {
               if("server".equals(name)) {
                  requestDone.countDown();
               }

               clientsDone.countDown();
            }
         }, "bug76974-client-" + name);
         client.setDaemon(true);
         client.start();
      }

      void abandon() {
         abandoned = true;
         childSideParked.countDown();
         requestHoldsParentLock.countDown();
         requestDone.countDown();
         mainRelease.countDown();
      }

      void releaseTomcatAwaitThreads() {
         List<WebServer> servers = new ArrayList<>(webServers);

         if(parent instanceof ServletWebServerApplicationContext web && web.getWebServer() != null) {
            servers.add(web.getWebServer());
         }

         for(WebServer server : servers) {
            if(server instanceof TomcatWebServer tomcat) {
               if(tomcat.getTomcat().getServer() instanceof StandardServer standard) {
                  standard.stopAwait();
               }
            }
         }
      }

      final Shape shape;
      final Attempt attempt = new Attempt();
      final long t0 = System.nanoTime();
      volatile ConfigurableApplicationContext parent;
      volatile int serverPort;
      volatile WebEndpointDiscoverer discoverer;
      volatile boolean armed;
      volatile boolean abandoned;
      final Set<String> endpointBeanNames = ConcurrentHashMap.newKeySet();
      final List<WebServer> webServers = new CopyOnWriteArrayList<>();
      final AtomicBoolean requestSideClaimed = new AtomicBoolean();
      final AtomicBoolean childSideClaimed = new AtomicBoolean();
      final AtomicBoolean lockHookClaimed = new AtomicBoolean();
      final CountDownLatch childSideParked = new CountDownLatch(1);
      final CountDownLatch requestHoldsParentLock = new CountDownLatch(1);
      final CountDownLatch requestDone = new CountDownLatch(1);
      final CountDownLatch clientsDone = new CountDownLatch(2);
      final CountDownLatch mainRelease = new CountDownLatch(1);
   }

   /**
    * Installs the gates and the lock hook into the parent bean factory.
    */
   private record HookInstaller(HookState state) implements BeanFactoryPostProcessor {
      @Override
      public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
         beanFactory.addBeanPostProcessor(new LockHook(state));
         beanFactory.registerSingleton("bug76974HookState", state);
         beanFactory.registerSingleton(CHILD_SIDE_GATE, new Gate(state, true));
         beanFactory.registerSingleton(REQUEST_SIDE_GATE, new Gate(state, false));

         for(String name : beanFactory.getBeanDefinitionNames()) {
            Class<?> type = beanFactory.getType(name, false);

            if(type != null && AnnotatedElementUtils.hasAnnotation(type, Endpoint.class)) {
               addDependsOn(beanFactory.getBeanDefinition(name), CHILD_SIDE_GATE);
               state.endpointBeanNames.add(name);
            }
         }

         state.trace("@Endpoint beans gated: %s", new TreeSet<>(state.endpointBeanNames));

         if(state.shape == Shape.DISCOVERY_OUTSIDE_PARENT_LOCK) {
            ((BeanDefinitionRegistry) beanFactory).removeBeanDefinition(HEALTH_MAPPING);
         }
         else {
            addDependsOn(beanFactory.getBeanDefinition(HEALTH_MAPPING), REQUEST_SIDE_GATE);
         }
      }

      private static void addDependsOn(BeanDefinition definition, String name) {
         List<String> dependsOn = new ArrayList<>();

         if(definition.getDependsOn() != null) {
            dependsOn.addAll(Arrays.asList(definition.getDependsOn()));
         }

         dependsOn.add(name);
         definition.setDependsOn(dependsOn.toArray(new String[0]));
      }
   }

   /**
    * A non-singleton factory bean used only as a depends-on target. Resolving it runs
    * {@link FactoryBean#getObject()} without the singleton lock, from inside
    * {@code doGetBean} of the dependent bean and before that bean's {@code getSingleton}.
    */
   private record Gate(HookState state, boolean childSide) implements FactoryBean<Gate.Token> {
      static final class Token {
      }

      @Override
      public Token getObject() {
         if(childSide) {
            state.childSideGate();
         }
         else {
            state.requestSideGate();
         }

         return new Token();
      }

      @Override
      public Class<?> getObjectType() {
         return Token.class;
      }

      @Override
      public boolean isSingleton() {
         return false;
      }
   }

   private record LockHook(HookState state) implements InstantiationAwareBeanPostProcessor {
      @Override
      public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) {
         Thread thread = Thread.currentThread();
         boolean target = state.shape == Shape.DISCOVERY_OUTSIDE_PARENT_LOCK ?
            state.endpointBeanNames.contains(beanName) : HEALTH_MAPPING.equals(beanName);

         if(state.armed && target && thread == state.attempt.requestThread &&
            state.lockHookClaimed.compareAndSet(false, true))
         {
            state.requestHoldsParentLock(beanName);
         }

         return null;
      }

      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) {
         if(bean instanceof WebEndpointDiscoverer discoverer && state.discoverer == null) {
            state.discoverer = discoverer;
         }

         return bean;
      }
   }

   private record WebServerListener(HookState state)
      implements ApplicationListener<ServletWebServerInitializedEvent>
   {
      @Override
      public void onApplicationEvent(ServletWebServerInitializedEvent event) {
         state.webServers.add(event.getWebServer());
         String namespace = event.getApplicationContext().getServerNamespace();
         int port = event.getWebServer().getPort();

         if(namespace == null) {
            state.serverPort = port;
            state.trace("server port %d accepting", port);

            if(state.shape != Shape.MANAGEMENT_REQUEST_AND_SERVER_REQUEST) {
               state.armed = true;
               state.fire("server", port, state.shape == Shape.DISCOVERY_OUTSIDE_PARENT_LOCK ?
                  DISCOVER_PATH : "/bug76974");
            }
         }
         else if("management".equals(namespace)) {
            state.trace("management port %d accepting", port);

            if(state.shape == Shape.MANAGEMENT_REQUEST_AND_SERVER_REQUEST) {
               state.armed = true;
               state.fire("management", port, "/health/readiness");
               state.fire("server", state.serverPort, "/bug76974");
               waitUntil(() -> state.clientsDone.getCount() == 0 || state.abandoned, state);
               state.trace("main released after both requests: %s",
                           state.clientsDone.getCount() == 0);
            }
            else if(state.discoveryComplete()) {
               // Parent discovery already happened (a fix): hold main here, inside the child
               // refresh, until the request thread holds the parent lock creating
               // healthEndpointWebMvcHandlerMapping, so the overlap is still forced.
               boolean locked = await(state.requestHoldsParentLock,
                                      () -> state.requestDone.getCount() == 0, state);
               state.trace("main held at management start until request holds parent lock: %s",
                           locked);
            }
         }
      }
   }

   @Configuration(proxyBeanMethods = false)
   @ImportAutoConfiguration({
      PropertyPlaceholderAutoConfiguration.class,
      ServletWebServerFactoryAutoConfiguration.class,
      DispatcherServletAutoConfiguration.class,
      WebMvcAutoConfiguration.class,
      HttpMessageConvertersAutoConfiguration.class,
      JacksonAutoConfiguration.class,
      ApplicationAvailabilityAutoConfiguration.class,
      EndpointAutoConfiguration.class,
      WebEndpointAutoConfiguration.class,
      ManagementContextAutoConfiguration.class,
      ServletManagementContextAutoConfiguration.class,
      HealthContributorAutoConfiguration.class,
      HealthEndpointAutoConfiguration.class,
      AvailabilityHealthContributorAutoConfiguration.class,
      AvailabilityProbesAutoConfiguration.class,
      MetricsAutoConfiguration.class,
      CompositeMeterRegistryAutoConfiguration.class,
      SimpleMetricsExportAutoConfiguration.class,
      PrometheusMetricsExportAutoConfiguration.class,
      MetricsEndpointAutoConfiguration.class
   })
   static class HarnessApplication {
   }

   /**
    * Lazy, never-exposed endpoints. They exist only to add keys to the request thread's
    * discovery, which raises the chance that one of them lands in the reserved bin.
    */
   @Configuration(proxyBeanMethods = false)
   static class FillerEndpoints {
      @Bean Filler1 bug76974Filler1() { return new Filler1(); }
      @Bean Filler2 bug76974Filler2() { return new Filler2(); }
      @Bean Filler3 bug76974Filler3() { return new Filler3(); }
      @Bean Filler4 bug76974Filler4() { return new Filler4(); }
      @Bean Filler5 bug76974Filler5() { return new Filler5(); }
      @Bean Filler6 bug76974Filler6() { return new Filler6(); }
      @Bean Filler7 bug76974Filler7() { return new Filler7(); }
      @Bean Filler8 bug76974Filler8() { return new Filler8(); }
   }

   @Endpoint(id = "bug76974filler1") static class Filler1 { @ReadOperation public String read() { return "1"; } }
   @Endpoint(id = "bug76974filler2") static class Filler2 { @ReadOperation public String read() { return "2"; } }
   @Endpoint(id = "bug76974filler3") static class Filler3 { @ReadOperation public String read() { return "3"; } }
   @Endpoint(id = "bug76974filler4") static class Filler4 { @ReadOperation public String read() { return "4"; } }
   @Endpoint(id = "bug76974filler5") static class Filler5 { @ReadOperation public String read() { return "5"; } }
   @Endpoint(id = "bug76974filler6") static class Filler6 { @ReadOperation public String read() { return "6"; } }
   @Endpoint(id = "bug76974filler7") static class Filler7 { @ReadOperation public String read() { return "7"; } }
   @Endpoint(id = "bug76974filler8") static class Filler8 { @ReadOperation public String read() { return "8"; } }

   /**
    * Row C-R5: runs discovery from a handler method, i.e. not inside any bean factory method,
    * so the request thread does not hold the parent singleton lock across it.
    */
   @RestController
   static class DiscoveryController {
      DiscoveryController(ObjectProvider<WebEndpointsSupplier> suppliers, HookState state) {
         this.suppliers = suppliers;
         this.state = state;
      }

      @GetMapping(DISCOVER_PATH)
      public String discover() {
         state.requestSideGate();
         return "endpoints=" + suppliers.getObject().getEndpoints().size();
      }

      private final ObjectProvider<WebEndpointsSupplier> suppliers;
      private final HookState state;
   }
}
