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
package inetsoft.storage;

import inetsoft.sree.internal.cluster.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.config.*;
import inetsoft.web.factory.EngineConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.management.*;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76975: on a simultaneous cluster cold start, the {@code sreeProperties} singleton service
 * can be placed on a node whose {@code main} thread is still refreshing the Spring context. The
 * service thread runs another node's {@link LoadKeyValueTask}, which looks up the
 * {@link KeyValueEngine} through Spring before {@code main} has created it. Spring uses strict
 * singleton locking during {@code onRefresh}, so the service thread parks on the singleton lock
 * that {@code main} holds. {@code main} then creates the engine and waits for its own load, which
 * is queued behind the parked task on the same serial service thread, until the 3-minute timeout.
 *
 * <p>The context is built from the real {@link StorageConfiguration}, {@link EngineConfiguration}
 * and {@link KeyValueStorageManager} bean definitions, all lazy as with
 * {@code spring.main.lazy-initialization}. The work that {@code main} does while creating
 * {@code SecurityEngine} runs inside the creation of a bean requested from {@code onRefresh}, as
 * {@code getWebServerFactory} does in the server, so {@code main} holds the singleton lock
 * throughout. Only the {@code cluster} bean instance is replaced, because {@code IgniteCluster}
 * joins a real Ignite cluster; the dependencies of the real {@code cluster} factory method, and
 * any {@code depends-on}, are still resolved first. {@link ServiceCluster} plays the singleton
 * service: it runs the submitted tasks in order on one thread named like Ignite's.</p>
 *
 * <p>Each attempt is bounded: {@code main} waits {@link #OWN_LOAD_WAIT_MS} for its load instead
 * of 3 minutes, then leaves the bean creation and releases the lock, which lets the parked task
 * finish. No thread is left blocked after a test.</p>
 */
@Tag("core")
class KeyValueLoadLockCycleTest {
   @BeforeEach
   void saveContext() {
      previousContext = ConfigurationContext.getContext().getApplicationContext();
   }

   @AfterEach
   void restoreContext() {
      ConfigurationContext.getContext().setApplicationContext(previousContext);
   }

   /**
    * The observed interleaving: the service starts after the cluster join and before
    * {@code main} created the engine, with another node's load already queued. Both loads must
    * complete while {@code main} is still creating beans.
    */
   @Test
   void loadQueuedBeforeEngineExistsCompletesWhileMainHoldsSingletonLock() throws Exception {
      Attempt attempt = run(false);

      assertTrue(attempt.foreignLoadDoneAfterRefresh && attempt.ownLoadDoneAfterRefresh,
                 () -> "the loads did not complete even after main left the bean creation\n" +
                    attempt.describe());
      assertEquals("true", attempt.mapAfterRefresh.get("security.enabled"),
                   () -> "the store was not filled after main left the bean creation\n" +
                      attempt.describe());
      assertNull(attempt.ownLoadTimeout,
                 () -> "main's own load of " + STORE + " did not complete within " +
                    OWN_LOAD_WAIT_MS + " ms while main was creating beans\n" + attempt.describe());
   }

   /**
    * Control: when the engine already exists when the service thread runs the queued load, the
    * lookup never takes the singleton lock and both loads complete while {@code main} holds it.
    */
   @Test
   void loadQueuedAfterEngineExistsCompletesWhileMainHoldsSingletonLock() throws Exception {
      Attempt attempt = run(true);

      assertNull(attempt.ownLoadTimeout,
                 () -> "main's own load did not complete with the engine created first\n" +
                    attempt.describe());
      assertFalse(attempt.serviceStackAtWait.contains(SINGLETON_LOCK_FRAME),
                  () -> "the service thread waited on the singleton lock for an existing bean\n" +
                     attempt.describe());
      assertEquals("true", attempt.mapAfterRefresh.get("security.enabled"), attempt::describe);
   }

   private Attempt run(boolean engineFirst) throws Exception {
      Attempt attempt = new Attempt();
      StartupContext context = new StartupContext();
      context.register(StorageConfiguration.class, EngineConfiguration.class,
                       KeyValueStorageManager.class);
      context.addBeanFactoryPostProcessor(beanFactory -> {
         DefaultListableBeanFactory factory = (DefaultListableBeanFactory) beanFactory;

         for(String name : factory.getBeanDefinitionNames()) {
            factory.getBeanDefinition(name).setLazyInit(true);
         }

         factory.removeBeanDefinition("inetsoftConfig");
         factory.registerSingleton("inetsoftConfig", createConfig());
         factory.addBeanPostProcessor(new ClusterSubstitution(factory, attempt));
      });
      context.registerBean(STARTER, Object.class, () -> {
         runMainSide(context, attempt, engineFirst);
         return new Object();
      }, definition -> definition.setLazyInit(true));

      ConfigurationContext.getContext().setApplicationContext(context);

      try {
         context.refresh();
         attempt.foreignLoadDoneAfterRefresh = awaitDone(attempt.foreignLoad);
         attempt.ownLoadDoneAfterRefresh = awaitDone(attempt.ownLoad);
         attempt.mapAfterRefresh = new HashMap<>(attempt.cluster.getReplicatedMap(MAP));
      }
      finally {
         context.close();

         if(attempt.cluster != null) {
            attempt.cluster.shutdownService();
         }
      }

      return attempt;
   }

   /**
    * What {@code main} does in the server between the cluster join and its own load, while it
    * holds the singleton lock.
    */
   private static void runMainSide(StartupContext context, Attempt attempt,
                                   boolean engineFirst)
   {
      attempt.main = Thread.currentThread();

      if(engineFirst) {
         context.getBean(KeyValueStorageManager.class);
      }

      Cluster cluster = context.getBean(Cluster.class);
      // another node's load, already queued when this node starts the service
      attempt.foreignLoad = cluster.submit(STORE, new LoadKeyValueTask<String>(STORE));
      awaitServiceWaitingOrDone(attempt);
      attempt.engineExistedWhenServiceWaited =
         context.getBeanFactory().containsSingleton("keyValueEngine");

      // PropertiesEngine -> KeyValueStorageManager -> keyValueEngine, then the own load
      context.getBean(KeyValueStorageManager.class);
      KeyValueEngine engine = context.getBean(KeyValueEngine.class);
      engine.put(STORE, "security.enabled", "true");
      attempt.ownLoad = cluster.submit(STORE, new LoadKeyValueTask<String>(STORE));

      try {
         attempt.ownLoad.get(OWN_LOAD_WAIT_MS, TimeUnit.MILLISECONDS);
      }
      catch(TimeoutException e) {
         attempt.ownLoadTimeout = e;
         attempt.captureAtTimeout();
      }
      catch(Exception e) {
         attempt.ownLoadFailure = e;
      }
   }

   /**
    * Waits until the service thread is waiting (on anything) or has finished the queued load.
    * {@code main} does nothing else meanwhile, so the service thread cannot be waiting for a
    * class that {@code main} is initializing.
    */
   private static void awaitServiceWaitingOrDone(Attempt attempt) {
      long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);

      while(System.nanoTime() < end) {
         Thread service = attempt.cluster.serviceThread;

         if(attempt.foreignLoad.isDone()) {
            attempt.serviceStackAtWait = "(queued load already completed)";
            return;
         }

         if(service != null) {
            Thread.State state = service.getState();

            if(state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
               attempt.serviceStateAtWait = state;
               attempt.serviceStackAtWait = format(service.getStackTrace());
               return;
            }
         }

         Thread.onSpinWait();
      }

      fail("the service thread neither waited nor completed the queued load");
   }

   private static boolean awaitDone(Future<?> future) {
      if(future == null) {
         return false;
      }

      try {
         future.get(10, TimeUnit.SECONDS);
         return true;
      }
      catch(Exception e) {
         return false;
      }
   }

   private InetsoftConfig createConfig() {
      InetsoftConfig config = new InetsoftConfig();
      KeyValueConfig keyValue = new KeyValueConfig();
      keyValue.setType("test");
      config.setKeyValue(keyValue);
      BlobConfig blob = new BlobConfig();
      blob.setType("local");
      FilesystemConfig filesystem = new FilesystemConfig();
      filesystem.setDirectory(tempDir.resolve("blob").toString());
      blob.setFilesystem(filesystem);
      config.setBlob(blob);
      return config;
   }

   private static String format(StackTraceElement[] stack) {
      StringBuilder sb = new StringBuilder();

      for(StackTraceElement element : stack) {
         sb.append("\n      at ").append(element);
      }

      return sb.toString();
   }

   /**
    * Calls {@code getBean(STARTER)} from {@code onRefresh}, before
    * {@code finishBeanFactoryInitialization}, like {@code ServletWebServerApplicationContext}
    * creating the web server factory.
    */
   private static final class StartupContext extends AnnotationConfigApplicationContext {
      @Override
      protected void onRefresh() {
         super.onRefresh();
         getBean(STARTER);
      }
   }

   /**
    * Replaces the {@code IgniteCluster} instance of the real {@code cluster} bean definition.
    * Returning an instance before instantiation keeps everything Spring does before it: the
    * {@code depends-on} beans are created in {@code doGetBean}, and the non-lazy parameters of the
    * real factory method are resolved here, so an ordering expressed either way still applies.
    */
   private static final class ClusterSubstitution implements InstantiationAwareBeanPostProcessor {
      ClusterSubstitution(DefaultListableBeanFactory factory, Attempt attempt) {
         this.factory = factory;
         this.attempt = attempt;
      }

      @Override
      public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName)
         throws BeansException
      {
         if(!"cluster".equals(beanName)) {
            return null;
         }

         for(Method method : EngineConfiguration.class.getDeclaredMethods()) {
            if(method.getName().equals("cluster") &&
               AnnotatedElementUtils.hasAnnotation(method, Bean.class))
            {
               for(Parameter parameter : method.getParameters()) {
                  if(!AnnotatedElementUtils.hasAnnotation(parameter, Lazy.class)) {
                     factory.getBean(parameter.getType());
                  }
               }
            }
         }

         attempt.cluster = new ServiceCluster();
         return attempt.cluster;
      }

      private final DefaultListableBeanFactory factory;
      private final Attempt attempt;
   }

   /**
    * Runs singleton-service tasks one at a time on a single thread named like the Ignite service
    * thread in the thread dumps.
    */
   static final class ServiceCluster extends MockCluster {
      @Override
      public Future<?> submit(String serviceId, SingletonRunnableTask task) {
         return CompletableFuture.runAsync(() -> {
            serviceThread = Thread.currentThread();
            task.run();
         }, service);
      }

      void shutdownService() {
         service.shutdownNow();
      }

      volatile Thread serviceThread;
      private final ExecutorService service = Executors.newSingleThreadExecutor(r -> {
         Thread thread = new Thread(r, "cluster-service-" + STORE);
         thread.setDaemon(true);
         return thread;
      });
   }

   private static final class Attempt {
      void captureAtTimeout() {
         ThreadMXBean threads = ManagementFactory.getThreadMXBean();
         Thread service = cluster.serviceThread;

         if(service != null) {
            serviceAtTimeout = threads.getThreadInfo(new long[] { service.threadId() }, true, true)[0];
         }

         mainAtTimeout = threads.getThreadInfo(new long[] { main.threadId() }, true, true)[0];
      }

      String describe() {
         StringBuilder sb = new StringBuilder();
         sb.append("  engine existed when the service thread waited: ")
            .append(engineExistedWhenServiceWaited)
            .append("\n  service thread state when main continued: ").append(serviceStateAtWait)
            .append("\n  service thread stack when main continued:").append(serviceStackAtWait)
            .append("\n  own load timeout: ").append(ownLoadTimeout)
            .append("\n  own load failure: ").append(ownLoadFailure)
            .append("\n  loads done after main released the lock: foreign=")
            .append(foreignLoadDoneAfterRefresh).append(", own=").append(ownLoadDoneAfterRefresh)
            .append("\n  store after main released the lock: ").append(mapAfterRefresh);

         if(serviceAtTimeout != null) {
            sb.append("\n  service thread at the timeout: ").append(serviceAtTimeout.getThreadName())
               .append(" ").append(serviceAtTimeout.getThreadState())
               .append(" on ").append(serviceAtTimeout.getLockName())
               .append(" owned by \"").append(serviceAtTimeout.getLockOwnerName()).append('"')
               .append(format(serviceAtTimeout.getStackTrace()));
         }

         if(mainAtTimeout != null) {
            sb.append("\n  main locked synchronizers at the timeout: ")
               .append(Arrays.toString(mainAtTimeout.getLockedSynchronizers()));
         }

         return sb.toString();
      }

      volatile Thread main;
      volatile ServiceCluster cluster;
      volatile Future<?> foreignLoad;
      volatile Future<?> ownLoad;
      volatile Thread.State serviceStateAtWait;
      volatile String serviceStackAtWait = "";
      volatile boolean engineExistedWhenServiceWaited;
      volatile TimeoutException ownLoadTimeout;
      volatile Exception ownLoadFailure;
      volatile ThreadInfo serviceAtTimeout;
      volatile ThreadInfo mainAtTimeout;
      volatile boolean foreignLoadDoneAfterRefresh;
      volatile boolean ownLoadDoneAfterRefresh;
      volatile Map<Object, Object> mapAfterRefresh = Map.of();
   }

   @TempDir
   Path tempDir;
   private ApplicationContext previousContext;

   private static final String STORE = "sreeProperties";
   private static final String MAP = "inetsoft.storage.kv." + STORE;
   private static final String STARTER = "bug76975Starter";
   private static final String SINGLETON_LOCK_FRAME =
      "DefaultSingletonBeanRegistry.getSingleton";
   private static final long OWN_LOAD_WAIT_MS = 3000L;
}
