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
package inetsoft.test.lockorder;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.lang.management.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.jar.*;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * A test-only lock-order recorder (bug #77123, reliability plan task 5). It records, per
 * thread, the order in which locks are taken as edges {@code held -> acquired} between lock
 * <em>classes</em>, and finds cycles in that graph with Tarjan's SCC algorithm. A cycle is a
 * potential deadlock even if no run hung.
 *
 * <p>What is recorded:
 * <ul>
 * <li>{@code LendableReentrantLock} (the script engine locks) and
 * {@code java.util.concurrent.locks.ReentrantLock}, by ByteBuddy retransformation with
 * {@link Advice} on {@code <init>}, {@code lock}, {@code lockInterruptibly}, {@code tryLock}
 * and {@code unlock}. The advice calls {@link inetsoft.test.lockorder.boot.LockHook}, which is
 * injected into the bootstrap class loader so {@code java.base} can reach it.</li>
 * <li>Java monitors held around those locks: at every acquisition the thread's locked monitors
 * are read with {@code ThreadMXBean.getThreadInfo(id, true, false)} (edges monitor -> lock; per
 * thread and acquisition shape the first {@value #SNAPSHOT_FULL} acquisitions are read, then one
 * in {@value #SNAPSHOT_EVERY}, as a per-row engine lock would otherwise slow the suites past their
 * timeouts), and
 * a sampler reads the threads holding a recorded lock every {@value #SAMPLE_MILLIS} ms: a
 * monitor they hold that they did not hold when they took the lock, or a monitor they are
 * BLOCKED on, gives an edge lock -> monitor. Lock -> monitor edges are therefore sampled, not
 * exhaustive; a BLOCKED wait (every real deadlock) is always seen.</li>
 * </ul>
 * Not recorded: monitor -> monitor edges (the JVM's own deadlock detection covers real monitor
 * cycles), {@code ReentrantReadWriteLock}, {@code StampedLock}, other AQS synchronizers,
 * {@code Condition} waits, and waits for a thread or a future (lock lending waits).
 *
 * <p>Nodes: a {@code LendableReentrantLock} or {@code ReentrantLock} is named by its allocation
 * site (the outermost constructor of the chain that allocated it, e.g.
 * {@code LRL@inetsoft.util.script.graal.pool.WsEngine}); a {@code ReentrantLock} allocated by a
 * class outside {@code inetsoft.} (a JDK queue's or executor's, Truffle's, Spring's: internal to
 * that class), or allocated before recording started, is not recorded (counted in
 * {@link #ignoredEvents}). A monitor is named by its class,
 * and a plain {@code Object}/{@code Class} monitor by the class of the frame it was first seen
 * locked or awaited in, kept per object identity hash for the run
 * ({@code MON@java.lang.Object@inetsoft...AssetQuerySandbox}).
 *
 * <p>Edge kinds: {@code BLOCKING} ({@code lock()}, {@code lockInterruptibly()}, monitor entry),
 * {@code TIMED} ({@code tryLock(time)}), {@code TRY} ({@code tryLock()}), {@code PRIVATE} (a
 * blocking acquisition of an instance no thread but its creator has used yet, such as an engine
 * locking itself in its own init before it is published: it cannot wait). A timed edge is
 * recorded at the attempt, like a blocking one: a {@code tryLock(time)} retried in a loop (the
 * formula lens's bounded lock wait) is as blocking as its retry policy. {@link #findCycles()}
 * uses blocking and timed edges; {@code TRY} and {@code PRIVATE} edges never wait and close no
 * cycle. An edge between two locks of the
 * same class is kept when the instances differ (a self-loop, e.g. two sandboxes' engine locks
 * nested both ways); a re-entry of the same instance is not an edge.
 */
public final class LockOrderRecorder {
   public enum Kind { BLOCKING, TIMED, TRY, PRIVATE }

   /**
    * Install the instrumentation and return the recorder; recording is off until
    * {@link #start()}. Balance it with {@link #uninstall()}, as the JVM is shared by later tests.
    */
   public static synchronized LockOrderRecorder install() {
      if(instance == null) {
         LockOrderRecorder recorder = new LockOrderRecorder();
         recorder.instrument();
         instance = recorder;
      }

      return instance;
   }

   private LockOrderRecorder() {
   }

   /**
    * Remove the instrumentation: stop recording and the sampler, detach the hook, restore the
    * original bytecode of the lock classes and drop every recorded lock. The bootstrap copy of
    * the hook stays loaded (a class cannot be unloaded) and does nothing without a sink. A later
    * {@link #install()} instruments again.
    */
   public static synchronized void uninstall() {
      LockOrderRecorder recorder = instance;

      if(recorder == null) {
         return;
      }

      instance = null;
      recorder.recording = false;

      try {
         HOOK.getField("sink").set(null, null);
      }
      catch(ReflectiveOperationException ex) {
         throw new IllegalStateException(ex);
      }

      Thread thread = recorder.sampler;

      if(thread != null) {
         thread.interrupt();

         try {
            thread.join(10000);
         }
         catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
         }

         if(thread.isAlive()) {
            throw new IllegalStateException("the lock-order sampler did not stop");
         }

         recorder.sampler = null;
      }

      if(recorder.transformer != null &&
         !recorder.transformer.reset(recorder.inst, AgentBuilder.RedefinitionStrategy.RETRANSFORMATION))
      {
         throw new IllegalStateException("the lock instrumentation could not be reset");
      }

      recorder.names.clear();
      HOLDERS.clear();
      PLAIN_MONITORS.clear();
   }

   /**
    * @return whether the instrumentation is installed now.
    */
   public static synchronized boolean isInstalled() {
      return instance != null;
   }

   public void start() {
      recording = true;

      if(sampler == null) {
         sampler = new Thread(this::sampleLoop, "lockorder-sampler");
         sampler.setDaemon(true);
         sampler.start();
      }
   }

   public void stop() {
      recording = false;
   }

   public boolean isRecording() {
      return recording;
   }

   /**
   * Forget every edge (not the lock names).
   */
   public void reset() {
      edges.clear();
      events.set(0);
      ignoredEvents.set(0);
      samples.set(0);
      unread.set(0);
      errors.set(0);
      hookErrors().set(0);
   }

   /**
    * @return the errors the hook and the sampler swallowed (never let reach a lock's caller);
    *         any is a gap in the recorded graph.
    */
   public long errors() {
      return errors.get() + hookErrors().get();
   }

   private static AtomicLong hookErrors() {
      try {
         return (AtomicLong) HOOK.getField("errors").get(null);
      }
      catch(ReflectiveOperationException ex) {
         throw new IllegalStateException(ex);
      }
   }

   /**
    * Name the test the next edges are first seen in, for the report.
    */
   public static void setCurrentTest(String name) {
      currentTest = name;
   }

   public Collection<Edge> edges() {
      return new ArrayList<>(edges.values());
   }

   public long events() {
      return events.get();
   }

   public long ignoredEvents() {
      return ignoredEvents.get();
   }

   public long samples() {
      return samples.get();
   }

   /**
    * @return the cycles of blocking and timed edges: each strongly connected component of more than one
    *         node, and each self-loop between distinct instances.
    */
   public List<Cycle> findCycles() {
      return findCycles(EnumSet.of(Kind.BLOCKING, Kind.TIMED));
   }

   public List<Cycle> findCycles(Set<Kind> kinds) {
      Map<String, List<Edge>> out = new TreeMap<>();

      for(Edge edge : edges.values()) {
         if(kinds.contains(edge.kind)) {
            out.computeIfAbsent(edge.from, k -> new ArrayList<>()).add(edge);
            out.computeIfAbsent(edge.to, k -> new ArrayList<>());
         }
      }

      List<Cycle> cycles = new ArrayList<>();

      for(Set<String> scc : new Tarjan(out).run()) {
         List<Edge> inside = new ArrayList<>();

         for(String node : scc) {
            for(Edge edge : out.get(node)) {
               if(scc.contains(edge.to)) {
                  inside.add(edge);
               }
            }
         }

         if(scc.size() > 1 || !inside.isEmpty()) {
            cycles.add(new Cycle(new TreeSet<>(scc), inside));
         }
      }

      return cycles;
   }

   /**
    * @return a text report: counts, every cycle with a sample stack per edge, and every edge.
    */
   public String report(String label) {
      List<Cycle> cycles = findCycles();
      List<Edge> all = new ArrayList<>(edges.values());
      all.sort(Comparator.comparing((Edge e) -> e.from).thenComparing(e -> e.to)
                  .thenComparing(e -> e.kind));
      StringWriter text = new StringWriter();
      PrintWriter out = new PrintWriter(text);
      out.println(summary(label));
      out.println();

      for(Cycle cycle : cycles) {
         out.println("CYCLE " + cycle.nodes + (cycle.involvesScriptLock() ? " [script lock]" : ""));

         for(Edge edge : cycle.edges) {
            out.println("  " + edge.describe());
            out.println(indent(edge.stack, "      "));
         }
      }

      out.println();
      out.println("EDGES");

      for(Edge edge : all) {
         out.println("  " + edge.describe());
      }

      out.flush();
      return text.toString();
   }

   public String summary(String label) {
      List<Cycle> cycles = findCycles();
      long blocking = edges.values().stream().filter(e -> e.kind == Kind.BLOCKING).count();
      long script = cycles.stream().filter(Cycle::involvesScriptLock).count();
      return "LOCKORDER label=" + label + " edges=" + edges.size() + " blockingEdges=" +
         blocking + " cycles=" + cycles.size() + " scriptLockCycles=" + script + " events=" +
         events.get() + " ignoredEvents=" + ignoredEvents.get() + " unreadAcquisitions=" + unread.get() +
         " samples=" + samples.get() + " errors=" + errors();
   }

   // ---- instrumentation ----

   private void instrument() {
      try {
         // loaded first, so it is retransformed with ReentrantLock and checked below
         Class.forName("inetsoft.util.script.LendableReentrantLock");
         inst = ByteBuddyAgent.install();

         if(HOOK == null) {
            HOOK = injectHook(inst);
         }

         Class<?> hook = HOOK;
         @SuppressWarnings("unchecked")
         BiConsumer<Object, Integer> sink = this::onEvent;
         hook.getField("sink").set(null, sink);
         List<String> errors = new CopyOnWriteArrayList<>();

         transformer = new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
            .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
            .ignore(nameStartsWith("net.bytebuddy."))
            .assureReadEdgeTo(inst, hook)
            .with(new AgentBuilder.Listener.Adapter() {
               @Override
               public void onError(String typeName, ClassLoader loader, JavaModule module,
                                   boolean loaded, Throwable throwable)
               {
                  errors.add(typeName + ": " + throwable);
               }

               @Override
               public void onTransformation(TypeDescription type, ClassLoader loader,
                                            JavaModule module, boolean loaded,
                                            DynamicType dynamicType)
               {
                  transformed.add(type.getName());
               }
            })
            .type(named("java.util.concurrent.locks.ReentrantLock")
                     .or(named("inetsoft.util.script.LendableReentrantLock")))
            .transform((builder, type, loader, module, domain) -> builder
               .visit(Advice.to(InitAdvice.class).on(isConstructor()))
               .visit(Advice.to(AcquireAdvice.class).on(
                  named("lock").and(takesNoArguments()).or(named("lockInterruptibly"))))
               .visit(Advice.to(TryAdvice.class).on(named("tryLock").and(takesNoArguments())))
               .visit(Advice.to(TimedAdvice.class).on(named("tryLock").and(takesArguments(2))))
               .visit(Advice.to(UnlockAdvice.class).on(named("unlock"))))
            .installOn(inst);

         if(!errors.isEmpty() || transformed.size() < 2) {
            throw new IllegalStateException("lock instrumentation failed: transformed " +
                                            transformed + ", errors " + errors);
         }
      }
      catch(Exception ex) {
         throw new IllegalStateException("Cannot install the lock-order recorder", ex);
      }
   }

   /**
    * Put {@code LockHook} on the bootstrap class path, so the advice inlined into JDK classes
    * resolves it; application classes find the same copy by parent-first delegation.
    */
   private static Class<?> injectHook(Instrumentation inst) throws Exception {
      String name = "inetsoft.test.lockorder.boot.LockHook";
      String resource = name.replace('.', '/') + ".class";
      byte[] bytes;

      try(InputStream in = LockOrderRecorder.class.getClassLoader().getResourceAsStream(resource)) {
         bytes = Objects.requireNonNull(in, resource).readAllBytes();
      }

      Path jar = Files.createTempFile("lockorder-boot", ".jar");
      jar.toFile().deleteOnExit();

      try(JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
         out.putNextEntry(new JarEntry(resource));
         out.write(bytes);
         out.closeEntry();
      }

      inst.appendToBootstrapClassLoaderSearch(new JarFile(jar.toFile()));
      Class<?> hook = Class.forName(name, true, null);

      if(hook.getClassLoader() != null) {
         throw new IllegalStateException("LockHook is not a bootstrap class");
      }

      return hook;
   }

   public static final class InitAdvice {
      @Advice.OnMethodExit
      static void exit(@Advice.This Object lock) {
         inetsoft.test.lockorder.boot.LockHook.event(lock, inetsoft.test.lockorder.boot.LockHook.CREATED);
      }
   }

   public static final class AcquireAdvice {
      @Advice.OnMethodEnter
      static void enter(@Advice.This Object lock) {
         inetsoft.test.lockorder.boot.LockHook.event(lock, inetsoft.test.lockorder.boot.LockHook.ENTER);
      }

      @Advice.OnMethodExit(onThrowable = Throwable.class)
      static void exit(@Advice.This Object lock, @Advice.Thrown Throwable thrown) {
         inetsoft.test.lockorder.boot.LockHook.event(
            lock, thrown == null ? inetsoft.test.lockorder.boot.LockHook.ACQUIRED :
               inetsoft.test.lockorder.boot.LockHook.FAILED);
      }
   }

   public static final class TryAdvice {
      @Advice.OnMethodExit(onThrowable = Throwable.class)
      static void exit(@Advice.This Object lock, @Advice.Return boolean acquired,
                       @Advice.Thrown Throwable thrown)
      {
         if(thrown == null && acquired) {
            inetsoft.test.lockorder.boot.LockHook.event(
               lock, inetsoft.test.lockorder.boot.LockHook.TRY_ACQUIRED);
         }
      }
   }

   public static final class TimedAdvice {
      @Advice.OnMethodEnter
      static void enter(@Advice.This Object lock) {
         inetsoft.test.lockorder.boot.LockHook.event(
            lock, inetsoft.test.lockorder.boot.LockHook.TIMED_ENTER);
      }

      @Advice.OnMethodExit(onThrowable = Throwable.class)
      static void exit(@Advice.This Object lock, @Advice.Return boolean acquired,
                       @Advice.Thrown Throwable thrown)
      {
         inetsoft.test.lockorder.boot.LockHook.event(
            lock, thrown == null && acquired ? inetsoft.test.lockorder.boot.LockHook.TIMED_ACQUIRED :
               inetsoft.test.lockorder.boot.LockHook.FAILED);
      }
   }

   public static final class UnlockAdvice {
      @Advice.OnMethodExit(onThrowable = Throwable.class)
      static void exit(@Advice.This Object lock, @Advice.Thrown Throwable thrown) {
         if(thrown == null) {
            inetsoft.test.lockorder.boot.LockHook.event(
               lock, inetsoft.test.lockorder.boot.LockHook.UNLOCK);
         }
      }
   }

   // ---- events ----

   private void onEvent(Object lock, Integer kind) {
      ThreadState state = STATE.get();

      if(state.inHook) {
         return;
      }

      state.inHook = true;

      try {
         if(kind == 0) {
            if(recording) {
               String site = siteName(lock);

               if(site != IGNORED) {
                  names.put(new IdentityKey(lock), new Site(site, Thread.currentThread()));
               }
            }

            return;
         }

         if(!recording) {
            // an unlock of a lock taken while recording keeps the held list right
            if(kind == 6) {
               state.release(lock);
            }

            return;
         }

         Site site = names.get(new IdentityKey(lock));
         String node;
         // only its creating thread ever used it so far: this acquisition cannot wait
         boolean unshared = false;

         if(site != null) {
            node = site.node;

            if(site.sole != Thread.currentThread()) {
               site.sole = null;
            }
            else {
               unshared = true;
            }
         }
         else if(lock.getClass().getName().endsWith("LendableReentrantLock")) {
            node = "LRL@unknown";
         }
         else {
            ignoredEvents.incrementAndGet();
            return;
         }

         events.incrementAndGet();

         switch(kind) {
         case 1 -> state.pending = acquireEdges(state, lock, node,
                                                unshared ? Kind.PRIVATE : Kind.BLOCKING);
         case 2 -> state.acquired(lock, node, state.pending);
         case 3 -> state.pending = null;
         case 4 -> state.acquired(lock, node, acquireEdges(state, lock, node, Kind.TRY));
         case 5 -> state.acquired(lock, node, state.pending);
         case 7 -> state.pending = acquireEdges(state, lock, node,
                                                unshared ? Kind.PRIVATE : Kind.TIMED);
         case 6 -> state.release(lock);
         default -> { }
         }
      }
      finally {
         state.inHook = false;
      }
   }

   /**
    * Record the edges of the calling thread taking {@code lock}: from each lock it holds, and
    * from each monitor it holds; and the lock -> monitor edges its current monitors show.
    *
    * @return the identity hashes of the monitors held now, for later lock -> monitor edges.
    */
   private int[] acquireEdges(ThreadState state, Object lock, String node, Kind kind) {
      Held[] held = state.held;

      for(Held h : held) {
         if(h.lock == lock) {
            // a re-entry is not an ordering, and the monitors held now are not new
            return h.monitors;
         }
      }

      // Reading the thread's monitors is costly (a stack walk), and a hot path (a filter
      // taking the engine lock per row) repeats one acquisition millions of times: per thread,
      // each acquisition shape (lock class + held lock classes) is read the first
      // SNAPSHOT_FULL times, then one time in SNAPSHOT_EVERY. Lock -> lock edges are recorded
      // every time; an unread acquisition does not know its monitors, so no lock -> monitor
      // edge is derived from it except a BLOCKED wait.
      StringBuilder shape = new StringBuilder(node);

      for(Held h : held) {
         shape.append('|').append(h.node);
      }

      int seen = state.shapes.merge(shape.toString(), 1, Integer::sum);

      if(seen > SNAPSHOT_FULL && seen % SNAPSHOT_EVERY != 0) {
         unread.incrementAndGet();

         for(Held h : held) {
            addEdge(h.node, node, kind, NO_STACK, "held lock");
         }

         return null;
      }

      ThreadInfo info = THREADS.getThreadInfo(new long[] { Thread.currentThread().getId() },
                                              true, false)[0];
      MonitorInfo[] monitors = info == null ? new MonitorInfo[0] : info.getLockedMonitors();
      StackTraceElement[] stack = info == null ? new StackTraceElement[0] : info.getStackTrace();
      List<Mon> mons = monitors(monitors);
      int[] hashes = new int[mons.size()];

      for(int i = 0; i < hashes.length; i++) {
         hashes[i] = mons.get(i).hash;
      }

      for(Held h : held) {
         addEdge(h.node, node, kind, stack, "held lock");

         for(Mon mon : mons) {
            if(!h.heldAtAcquire(mon.hash)) {
               addEdge(h.node, mon.node, Kind.BLOCKING, stack, "monitor taken after");
            }
         }
      }

      for(Mon mon : mons) {
         addEdge(mon.node, node, kind, stack, "held monitor");
      }

      return hashes;
   }

   private void sampleLoop() {
      while(true) {
         try {
            Thread.sleep(SAMPLE_MILLIS);

            if(recording) {
               sample();
            }
         }
         catch(InterruptedException ex) {
            return;
         }
         catch(Throwable ex) {
            // keep sampling, but count it: a failing sample drops edges
            errors.incrementAndGet();
         }
      }
   }

   private void sample() {
      Map<Long, Held[]> snapshot = new HashMap<>();

      for(ThreadState state : HOLDERS.keySet()) {
         Held[] held = state.held;

         if(held.length > 0 && state.thread.isAlive()) {
            snapshot.put(state.thread.getId(), held);
         }
      }

      if(snapshot.isEmpty()) {
         return;
      }

      long[] ids = snapshot.keySet().stream().mapToLong(Long::longValue).toArray();
      ThreadInfo[] infos = THREADS.getThreadInfo(ids, true, false);
      samples.incrementAndGet();

      for(ThreadInfo info : infos) {
         if(info == null || inRecorder(info.getStackTrace())) {
            continue;
         }

         Held[] held = snapshot.get(info.getThreadId());
         ThreadState state = byId(info.getThreadId());

         // the thread took or released a lock while it was read: the read is not coherent
         if(state == null || state.held != held) {
            continue;
         }

         List<Mon> mons = monitors(info.getLockedMonitors());
         Mon blockedOn = null;

         if(info.getThreadState() == Thread.State.BLOCKED && info.getLockInfo() != null &&
            info.getStackTrace().length > 0)
         {
            blockedOn = mon(info.getLockInfo().getClassName(),
                            info.getLockInfo().getIdentityHashCode(),
                            info.getStackTrace()[0]);
         }

         for(Held h : held) {
            for(Mon mon : mons) {
               if(!h.heldAtAcquire(mon.hash)) {
                  addEdge(h.node, mon.node, Kind.BLOCKING, info.getStackTrace(),
                          "sampled: monitor taken after");
               }
            }

            if(blockedOn != null) {
               addEdge(h.node, blockedOn.node, Kind.BLOCKING, info.getStackTrace(),
                       "sampled: BLOCKED on monitor");
            }
         }
      }
   }

   private static ThreadState byId(long id) {
      for(ThreadState state : HOLDERS.keySet()) {
         if(state.thread.getId() == id) {
            return state;
         }
      }

      return null;
   }

   private void addEdge(String from, String to, Kind kind, StackTraceElement[] stack,
                        String how)
   {
      EdgeKey key = new EdgeKey(from, to, kind);
      Edge edge = edges.get(key);

      if(edge == null) {
         // the first occurrence of an edge always keeps a sample stack, also when this
         // acquisition was not read
         StackTraceElement[] sample = stack.length > 0 ? stack : new Throwable().getStackTrace();
         edge = edges.computeIfAbsent(key, k -> new Edge(from, to, kind, how, currentTest,
                                                         Thread.currentThread().getName(),
                                                         format(sample)));
      }

      edge.count.incrementAndGet();
   }

   // ---- naming ----

   private static String siteName(Object lock) {
      boolean lendable = lock.getClass().getName().endsWith("LendableReentrantLock");
      List<StackWalker.StackFrame> frames = WALKER.walk(s -> s.limit(64).toList());
      String site = null;
      boolean chain = false;

      for(StackWalker.StackFrame frame : frames) {
         String cls = frame.getClassName();

         if(isRecorder(cls) || cls.startsWith("java.util.concurrent.locks.") ||
            cls.endsWith("LendableReentrantLock"))
         {
            continue;
         }

         if(site == null) {
            // the allocating class: a lock the JDK or a library allocates for itself (a
            // queue's, an executor's, Truffle's) is internal to that class and not recorded
            if(!lendable && !cls.startsWith("inetsoft.")) {
               return IGNORED;
            }

            site = cls;
            chain = frame.getMethodName().equals("<init>");
         }
         else if(chain && frame.getMethodName().equals("<init>") && cls.startsWith("inetsoft.")) {
            // a field initializer runs in the constructor chain: name the outermost class
            site = cls;
         }
         else {
            break;
         }
      }

      return (lendable ? "LRL@" : "RL@") + site;
   }

   private static List<Mon> monitors(MonitorInfo[] monitors) {
      List<Mon> list = new ArrayList<>();

      for(MonitorInfo monitor : monitors) {
         StackTraceElement frame = monitor.getLockedStackFrame();

         // a lock's own internal monitor, or the recorder's
         if(frame != null && (frame.getClassName().endsWith("LendableReentrantLock") ||
            isRecorder(frame.getClassName())))
         {
            continue;
         }

         list.add(mon(monitor.getClassName(), monitor.getIdentityHashCode(), frame));
      }

      return list;
   }

   private static Mon mon(String cls, int hash, StackTraceElement frame) {
      String node = "MON@" + cls;

      if((cls.equals("java.lang.Object") || cls.equals("java.lang.Class")) && frame != null) {
         // one name per object, from the frame it was first seen locked (or awaited) in: its
         // holder and a thread blocked on it name it from different frames
         String first = node + "@" + frame.getClassName();
         node = PLAIN_MONITORS.computeIfAbsent(hash, h -> first);
      }

      return new Mon(node, hash);
   }

   private static boolean inRecorder(StackTraceElement[] stack) {
      for(StackTraceElement element : stack) {
         if(isRecorder(element.getClassName())) {
            return true;
         }
      }

      return false;
   }

   /**
    * @return whether a frame of this class is the recorder's own (not a test's).
    */
   private static boolean isRecorder(String cls) {
      return cls.equals(RECORDER) || cls.startsWith(RECORDER + "$") ||
         cls.startsWith("inetsoft.test.lockorder.boot.");
   }

   private static String format(StackTraceElement[] stack) {
      StringBuilder text = new StringBuilder();
      int n = 0;

      for(StackTraceElement element : stack) {
         String cls = element.getClassName();

         if(isRecorder(cls) || cls.startsWith("sun.management") ||
            cls.startsWith("java.lang.management") || cls.startsWith("com.sun.management"))
         {
            continue;
         }

         text.append("at ").append(element).append('\n');

         if(++n == 30) {
            break;
         }
      }

      return text.toString();
   }

   private static String indent(String text, String prefix) {
      return prefix + text.strip().replace("\n", "\n" + prefix);
   }

   // ---- model ----

   public static final class Edge {
      Edge(String from, String to, Kind kind, String how, String test, String thread,
           String stack)
      {
         this.from = from;
         this.to = to;
         this.kind = kind;
         this.how = how;
         this.test = test;
         this.thread = thread;
         this.stack = stack;
      }

      public String from() {
         return from;
      }

      public String to() {
         return to;
      }

      public Kind kind() {
         return kind;
      }

      public String stack() {
         return stack;
      }

      public long count() {
         return count.get();
      }

      String describe() {
         return from + " -> " + to + " [" + kind + ", " + how + ", x" + count.get() +
            ", first in " + test + " on " + thread + "]";
      }

      final String from;
      final String to;
      final Kind kind;
      final String how;
      final String test;
      final String thread;
      final String stack;
      final AtomicLong count = new AtomicLong();
   }

   public record Cycle(SortedSet<String> nodes, List<Edge> edges) {
      /**
       * @return whether a script engine lock is part of the cycle.
       */
      public boolean involvesScriptLock() {
         return nodes.stream().anyMatch(n -> n.startsWith("LRL@"));
      }
   }

   private record EdgeKey(String from, String to, Kind kind) {
   }

   private record Mon(String node, int hash) {
   }

   /**
    * A recorded lock: its node, and the only thread that used it so far (its creator), or
    * {@code null} once another thread used it.
    */
   private static final class Site {
      Site(String node, Thread sole) {
         this.node = node;
         this.sole = sole;
      }

      final String node;
      volatile Thread sole;
   }

   private static final class Held {
      Held(Object lock, String node, int[] monitors) {
         this.lock = lock;
         this.node = node;
         this.monitors = monitors;
      }

      /**
       * @return whether the monitor was held when this lock was taken; {@code true} when that
       *         is unknown (the acquisition was not read), so no edge is derived from it.
       */
      boolean heldAtAcquire(int hash) {
         if(monitors == null) {
            return true;
         }

         for(int h : monitors) {
            if(h == hash) {
               return true;
            }
         }

         return false;
      }

      final Object lock;
      final String node;
      final int[] monitors;
      int count = 1;
   }

   private static final class ThreadState {
      ThreadState() {
         this.thread = Thread.currentThread();
      }

      void acquired(Object lock, String node, int[] monitors) {
         pending = null;

         for(Held h : held) {
            if(h.lock == lock) {
               h.count++;
               return;
            }
         }

         Held[] next = Arrays.copyOf(held, held.length + 1);
         next[held.length] = new Held(lock, node, monitors);
         held = next;
         HOLDERS.put(this, Boolean.TRUE);
      }

      void release(Object lock) {
         for(int i = held.length - 1; i >= 0; i--) {
            if(held[i].lock == lock) {
               if(--held[i].count > 0) {
                  return;
               }

               Held[] next = new Held[held.length - 1];
               System.arraycopy(held, 0, next, 0, i);
               System.arraycopy(held, i + 1, next, i, held.length - i - 1);
               held = next;

               if(next.length == 0) {
                  HOLDERS.remove(this);
               }

               return;
            }
         }
      }

      final Thread thread;
      volatile Held[] held = new Held[0];
      int[] pending;
      final Map<String, Integer> shapes = new HashMap<>();
      boolean inHook;
   }

   private record IdentityKey(Object lock) {
      @Override
      public boolean equals(Object obj) {
         return obj instanceof IdentityKey other && other.lock == lock;
      }

      @Override
      public int hashCode() {
         return System.identityHashCode(lock);
      }
   }

   /**
    * Tarjan's strongly connected components, iterative, over a sorted adjacency map.
    */
   private static final class Tarjan {
      Tarjan(Map<String, List<Edge>> graph) {
         this.graph = graph;
      }

      List<Set<String>> run() {
         for(String node : graph.keySet()) {
            if(!index.containsKey(node)) {
               connect(node);
            }
         }

         return result;
      }

      private void connect(String root) {
         Deque<Object[]> work = new ArrayDeque<>();
         work.push(new Object[] { root, 0 });
         enter(root);

         while(!work.isEmpty()) {
            Object[] frame = work.peek();
            String node = (String) frame[0];
            int i = (Integer) frame[1];
            List<Edge> out = graph.get(node);

            if(i < out.size()) {
               frame[1] = i + 1;
               String next = out.get(i).to;

               if(!index.containsKey(next)) {
                  enter(next);
                  work.push(new Object[] { next, 0 });
               }
               else if(onStack.contains(next)) {
                  low.put(node, Math.min(low.get(node), index.get(next)));
               }

               continue;
            }

            work.pop();

            if(!work.isEmpty()) {
               String parent = (String) work.peek()[0];
               low.put(parent, Math.min(low.get(parent), low.get(node)));
            }

            if(low.get(node).equals(index.get(node))) {
               Set<String> scc = new TreeSet<>();
               String member;

               do {
                  member = stack.pop();
                  onStack.remove(member);
                  scc.add(member);
               }
               while(!member.equals(node));

               result.add(scc);
            }
         }
      }

      private void enter(String node) {
         index.put(node, counter);
         low.put(node, counter);
         counter++;
         stack.push(node);
         onStack.add(node);
      }

      private final Map<String, List<Edge>> graph;
      private final Map<String, Integer> index = new HashMap<>();
      private final Map<String, Integer> low = new HashMap<>();
      private final Deque<String> stack = new ArrayDeque<>();
      private final Set<String> onStack = new HashSet<>();
      private final List<Set<String>> result = new ArrayList<>();
      private int counter;
   }

   static final long SAMPLE_MILLIS = 2;
   static final int SNAPSHOT_FULL = 64;
   static final int SNAPSHOT_EVERY = 64;
   private static final StackTraceElement[] NO_STACK = new StackTraceElement[0];
   private static final String RECORDER = LockOrderRecorder.class.getName();
   private static final String IGNORED = "<ignored>";
   private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
   private static final StackWalker WALKER = StackWalker.getInstance();
   private static final ThreadLocal<ThreadState> STATE = ThreadLocal.withInitial(ThreadState::new);
   private static final Map<ThreadState, Boolean> HOLDERS = new ConcurrentHashMap<>();
   // identity hash -> name of a plain Object/Class monitor (a reused hash after a collection
   // may inherit an older name; rare, and it can only merge two nodes)
   private static final Map<Integer, String> PLAIN_MONITORS = new ConcurrentHashMap<>();
   private static LockOrderRecorder instance;
   // the bootstrap LockHook, injected once per JVM
   private static Class<?> HOOK;
   private static volatile String currentTest = "?";

   private final Map<IdentityKey, Site> names = new ConcurrentHashMap<>();
   private final Map<EdgeKey, Edge> edges = new ConcurrentHashMap<>();
   private final Set<String> transformed = ConcurrentHashMap.newKeySet();
   private final AtomicLong events = new AtomicLong();
   private final AtomicLong ignoredEvents = new AtomicLong();
   private final AtomicLong samples = new AtomicLong();
   private final AtomicLong unread = new AtomicLong();
   private volatile boolean recording;
   private Thread sampler;
   private Instrumentation inst;
   private net.bytebuddy.agent.builder.ResettableClassFileTransformer transformer;
   private final AtomicLong errors = new AtomicLong();
}
