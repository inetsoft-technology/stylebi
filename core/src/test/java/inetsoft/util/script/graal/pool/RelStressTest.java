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
package inetsoft.util.script.graal.pool;

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.script.ScriptException;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reliability stress of one pooled worksheet env (bug #77123, reliability plan task 4): T
 * threads run seeded random operations on one env for a fixed time, with scheduling noise at
 * the pool's before-idle seam, and pool invariants are checked while it runs and after.
 *
 * <p>Knobs: {@code -Drel.seed} (default 77123), {@code -Drel.stress.threads} (16),
 * {@code -Drel.stress.seconds} (15; runs over 120 s also need {@code -Drel.long=true}).
 *
 * <p>A top-level exec (no claim open on the thread) checks it got a clean context: every exec
 * sets the implicit global {@code __dirty}, which a clean deletes, so a top-level exec that
 * finds it was handed a context another claim left unclean. Implicit globals staying within
 * one claim are the documented "implicit globals per claim" drift, so execs inside a claim do
 * not check it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("slow")
class RelStressTest {
   enum Op { COUNTER, NESTED, PUT_READ, HELD, THROW, RESET, TIMEOUT }

   @BeforeEach
   void shortTimeout() throws Exception {
      previousTimeout = SreeEnv.getProperty("script.execution.timeout");
      SreeEnv.setProperty("script.execution.timeout", "1");
      refreshTimeout();
   }

   @AfterEach
   void restoreTimeout() throws Exception {
      SreeEnv.setProperty("script.execution.timeout", previousTimeout);
      refreshTimeout();
   }

   @Test
   void randomOperationsKeepThePoolInvariants() throws Exception {
      long seconds = Long.getLong("rel.stress.seconds", 15);
      Assumptions.assumeTrue(seconds <= 120 || Boolean.getBoolean("rel.long"),
                             "runs over 120 s need -Drel.long=true");
      Result result = stress(Long.getLong("rel.seed", 77123L),
                             Integer.getInteger("rel.stress.threads", 16), seconds);
      System.err.println("REL-STRESS " + result.summary());
      assertTrue(result.violations.isEmpty(),
                 result.violations.size() + " violation(s):\n" +
                 String.join("\n", result.violations.stream().limit(20).toList()));
   }

   /**
    * Run the stress once and return what it saw; the invariants are asserted by the caller.
    */
   static Result stress(long seed, int threads, long seconds) throws Exception {
      Result result = new Result(seed, threads, seconds);
      WorksheetScriptEnv env = PoolTestSupport.env();
      long leakedBefore = PoolMetrics.nodeLeakedClaims();
      long interruptTimeoutsBefore = PoolMetrics.nodeInterruptTimeouts();
      int nodeBefore = PoolMetrics.nodeSlots();
      env.pool().beforeIdleHook = () -> {
         int r = ThreadLocalRandom.current().nextInt(10);

         if(r < 3) {
            Thread.yield();
         }
         else if(r < 5) {
            LockSupport.parkNanos(r * 50_000L);
         }
      };

      // written by the workers, read by the watchdog
      AtomicLongArray opStart = new AtomicLongArray(threads);
      AtomicReferenceArray<Op> opNow = new AtomicReferenceArray<>(threads);
      Thread[] workers = new Thread[threads];
      int[] claimsAtEnd = new int[threads];
      AtomicBoolean stop = new AtomicBoolean();
      AtomicInteger timeoutBudget = new AtomicInteger(threads * 2);
      AtomicLong deadlineRef = new AtomicLong();
      CountDownLatch warmed = new CountDownLatch(threads);
      CountDownLatch go = new CountDownLatch(1);

      for(int t = 0; t < threads; t++) {
         int id = t;
         workers[t] = new Thread(() -> {
            Worker worker = new Worker(env, id, new Random(seed * 1_000_003L + id), result,
                                       timeoutBudget);

            try {
               // warm up (first contexts, first compiles) outside the timed, watched run
               try {
                  worker.counter();
                  worker.putRead();
               }
               catch(Throwable ex) {
                  result.violation(id, null, -1, "warm-up: " + describe(ex));
               }
               finally {
                  warmed.countDown();
               }

               go.await();
               long deadline = deadlineRef.get();

               while(!stop.get() && System.nanoTime() < deadline) {
                  Op op = worker.pick();
                  opNow.set(id, op);
                  opStart.set(id, System.nanoTime());

                  try {
                     worker.run(op);
                  }
                  catch(Throwable ex) {
                     result.violation(id, op, worker.opIndex, "unexpected " + describe(ex));
                  }
                  finally {
                     opStart.set(id, 0);
                     worker.opIndex++;
                  }

                  if(result.violations.size() > 50) {
                     stop.set(true);
                  }
               }
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
            finally {
               claimsAtEnd[id] = SlotClaim.openClaims();
            }
         }, "rel-stress-" + t);
         workers[t].setDaemon(true);
         workers[t].start();
      }

      if(!warmed.await(120, TimeUnit.SECONDS)) {
         result.violation(-1, null, 0, "warm-up did not finish:\n" + dump(workers));
         stop.set(true);
      }

      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
      deadlineRef.set(deadline);
      long[] blockedSince = new long[threads];
      go.countDown();

      // the watchdog: pool invariants that hold at any time, no thread blocked (BLOCKED,
      // WAITING or TIMED_WAITING throughout) in one op for over 5 s, and no op over 60 s
      while(!stop.get() && System.nanoTime() < deadline) {
         PoolMetrics metrics = env.getMetrics();
         int size = metrics.getSize();
         int high = metrics.getHighWater();
         result.maxSize = Math.max(result.maxSize, size);
         result.samples++;

         if(size > high) {
            result.violation(-1, null, 0, "size " + size + " > high-water " + high);
         }

         long now = System.nanoTime();

         for(int t = 0; t < threads; t++) {
            long start = opStart.get(t);
            Thread.State state = workers[t].getState();

            if(start == 0 || state == Thread.State.RUNNABLE) {
               blockedSince[t] = 0;
            }
            else if(blockedSince[t] == 0) {
               blockedSince[t] = now;
            }

            if(blockedSince[t] != 0 && now - blockedSince[t] > MILLIS.toNanos(STALL_MS)) {
               result.violation(t, opNow.get(t), 0, "thread blocked for over " + STALL_MS +
                                " ms in one op\n" + dump(workers));
               stop.set(true);
               break;
            }

            if(start != 0 && now - start > MILLIS.toNanos(OP_CAP_MS)) {
               result.violation(t, opNow.get(t), 0, "op running for over " + OP_CAP_MS +
                                " ms\n" + dump(workers));
               stop.set(true);
               break;
            }

            if(start != 0) {
               result.maxOpMillis = Math.max(result.maxOpMillis,
                                             MILLIS.convert(now - start, TimeUnit.NANOSECONDS));
            }
         }

         Thread.sleep(20);
      }

      stop.set(true);

      for(Thread worker : workers) {
         worker.join(TimeUnit.SECONDS.toMillis(30));

         if(worker.isAlive()) {
            result.violation(-1, null, 0, worker.getName() + " did not stop\n" + dump(workers));
         }
      }

      for(int t = 0; t < threads; t++) {
         if(claimsAtEnd[t] != 0) {
            result.violation(t, null, 0, claimsAtEnd[t] + " claim(s) open at the end");
         }
      }

      env.pool().beforeIdleHook = null;
      PoolMetrics metrics = env.getMetrics();

      if(PoolMetrics.nodeLeakedClaims() != leakedBefore) {
         result.violation(-1, null, 0, "leaked claims " + leakedBefore + " -> " +
                          PoolMetrics.nodeLeakedClaims());
      }

      // quiescent: every counted context is an open slot of the pool, and the reverse
      List<Slot> slots = env.pool().slots();
      long open = slots.stream().filter(s -> !s.isClosed()).count();

      if(open != slots.size()) {
         result.violation(-1, null, 0, (slots.size() - open) + " closed slot(s) still pooled");
      }

      if(metrics.getSize() != open) {
         result.violation(-1, null, 0, "size " + metrics.getSize() + " != open pooled slots " +
                          open + " (a slot was dropped without a close, or closed twice)");
      }

      if(metrics.getSize() > metrics.getHighWater()) {
         result.violation(-1, null, 0, "final size above high-water");
      }

      result.sizeAtEnd = metrics.getSize();
      result.highWater = metrics.getHighWater();
      result.creations = metrics.getCreations();
      result.doomedCloses = metrics.getDoomedCloses();
      result.evictions = metrics.getEvictions();
      result.cleans = metrics.getCleans();
      result.execs = metrics.getExecs();
      result.interruptTimeouts = PoolMetrics.nodeInterruptTimeouts() - interruptTimeoutsBefore;

      env.retire();
      env.pool().evictIdle(Long.MAX_VALUE);

      if(metrics.getSize() != 0 || !env.pool().slots().isEmpty()) {
         result.violation(-1, null, 0, "after retire + evict: size " + metrics.getSize() +
                          ", pooled " + env.pool().slots().size());
      }

      result.nodeBefore = nodeBefore;
      result.nodeAfter = PoolMetrics.nodeSlots();

      // other envs of this JVM may be collected meanwhile (their Cleaner lowers the count)
      if(result.nodeAfter > nodeBefore) {
         result.violation(-1, null, 0, "node slots " + nodeBefore + " -> " + result.nodeAfter);
      }

      return result;
   }

   /**
    * The operations of one thread. Names it puts carry its prefix, so it can check it sees
    * its own latest writes whatever the others do.
    */
   static final class Worker {
      Worker(WorksheetScriptEnv env, int id, Random rnd, Result result,
             AtomicInteger timeoutBudget)
      {
         this.env = env;
         this.id = id;
         this.rnd = rnd;
         this.result = result;
         this.timeoutBudget = timeoutBudget;
         this.p = "t" + id + "_";
      }

      Op pick() {
         int r = rnd.nextInt(1000);

         if(r < 340) {
            return Op.COUNTER;
         }
         else if(r < 490) {
            return Op.NESTED;
         }
         else if(r < 690) {
            return Op.PUT_READ;
         }
         else if(r < 800) {
            return Op.HELD;
         }
         else if(r < 994) {
            return Op.THROW;
         }
         else if(r < 998) {
            return Op.RESET;
         }

         return Op.TIMEOUT;
      }

      void run(Op op) throws Exception {
         result.count(op);

         switch(op) {
         case COUNTER -> counter();
         case NESTED -> nested(1, 1 + rnd.nextInt(4));
         case PUT_READ -> putRead();
         case HELD -> held();
         case THROW -> throwing();
         case RESET -> {
            env.reset();
            counter();
         }
         case TIMEOUT -> timeout();
         }
      }

      /**
       * Put a fresh bound, then exec a loop over it: the exec must see the latest put.
       */
      void counter() throws Exception {
         boolean top = SlotClaim.openClaims() == 0;
         int n = rnd.nextInt(200);
         env.put(p + "n", n);
         Object script = compiled("(function() { var w = typeof __dirty; __dirty = 1; " +
                                  "var k = 0; for(var i = 0; i < " + p + "n; i++) { k += i; } " +
                                  "return w + ':' + k; })()");
         String got = String.valueOf(env.exec(script, null, null, null));
         String sum = ":" + ((long) n * (n - 1) / 2);

         if(!got.endsWith(sum)) {
            result.violation(id, Op.COUNTER, opIndex, "counter n=" + n + " got " + got);
         }
         else if(top && !got.startsWith("undefined:")) {
            result.violation(id, Op.COUNTER, opIndex,
                             "a top-level exec got a context left unclean: " + got);
         }
      }

      /**
       * Nested claims share one slot and depth; a put inside one is visible at once.
       */
      void nested(int level, int max) throws Exception {
         int before = depthNow();

         try(SlotClaim claim = env.claimSlot()) {
            if(claim.depth() != before + 1) {
               result.violation(id, Op.NESTED, opIndex, "depth " + claim.depth() + " after " +
                                before);
            }

            if(SlotClaim.openClaims() != 1) {
               result.violation(id, Op.NESTED, opIndex, SlotClaim.openClaims() + " claims open");
            }

            Slot slot = claim.peekSlot();

            if(slot == null || slot.isClosed()) {
               result.violation(id, Op.NESTED, opIndex, "claimed slot " + slot);
            }

            counter();

            if(rnd.nextBoolean()) {
               String name = p + "in" + level;
               int value = rnd.nextInt(1_000_000);
               env.put(name, value);
               Object got = env.exec(compiled("String(" + name + ")"), null, null, null);

               if(!String.valueOf(value).equals(got)) {
                  result.violation(id, Op.NESTED, opIndex, "own put " + name + "=" + value +
                                   " read " + got);
               }
            }

            if(level < max) {
               nested(level + 1, max);
            }

            if(claim.peekSlot() != slot) {
               result.violation(id, Op.NESTED, opIndex, "slot changed inside a claim");
            }

            counter();
         }
      }

      /**
       * Put or remove 20 own names, then read them all in one exec.
       */
      void putRead() throws Exception {
         StringBuilder want = new StringBuilder();

         for(int i = 0; i < 20; i++) {
            String name = p + "v" + i;

            if(rnd.nextInt(3) == 0) {
               env.remove(name);
               vars.remove(name);
            }
            else {
               String value = "s" + rnd.nextInt(1_000_000);
               env.put(name, value);
               vars.put(name, value);
            }

            if(!Objects.equals(vars.get(name), env.get(name))) {
               result.violation(id, Op.PUT_READ, opIndex, "get(" + name + ") = " +
                                env.get(name) + ", want " + vars.get(name));
            }

            want.append(i == 0 ? "" : ",").append(vars.getOrDefault(name, "~"));
         }

         Object got = env.exec(compiled(readScript()), null, null, null);

         if(!want.toString().equals(got)) {
            result.violation(id, Op.PUT_READ, opIndex, "read " + got + ", want " + want);
         }
      }

      /**
       * Hold an eager claim while another op runs on this thread.
       */
      void held() throws Exception {
         try(SlotClaim claim = env.claimSlot()) {
            Op inner = switch(rnd.nextInt(5)) {
               case 0 -> Op.COUNTER;
               case 1 -> Op.PUT_READ;
               case 2 -> Op.NESTED;
               case 3 -> Op.THROW;
               default -> rnd.nextInt(20) == 0 ? Op.RESET : Op.COUNTER;
            };
            run(inner);

            if(claim.peekSlot() == null) {
               result.violation(id, Op.HELD, opIndex, "eager claim without a slot");
            }
         }
      }

      void throwing() throws Exception {
         Object script = compiled("__dirty = 1; throw new Error('" + p + "boom');");

         try {
            Object got = env.exec(script, null, null, null);
            result.violation(id, Op.THROW, opIndex, "no throw, got " + got);
         }
         catch(ScriptException ex) {
            if(ex.getMessage() == null || !ex.getMessage().contains(p + "boom")) {
               result.violation(id, Op.THROW, opIndex, "wrong error " + describe(ex));
            }
         }
      }

      void timeout() throws Exception {
         if(SlotClaim.openClaims() != 0 || timeoutBudget.decrementAndGet() < 0) {
            counter();
            return;
         }

         long start = System.nanoTime();

         try {
            Object got = env.exec(compiled("__dirty = 1; while(true) {}"), null, null, null);
            result.violation(id, Op.TIMEOUT, opIndex, "a loop returned " + got);
         }
         catch(ScriptException ex) {
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            String message = String.valueOf(ex.getMessage()).toLowerCase();

            // only the 1 s timeout's interrupt: not an early error of another kind (a
            // multi-threaded access, an IllegalStateException) wrapped in a ScriptException
            if(ms < 900 || !(message.contains("interrupt") || message.contains("timeout") ||
               message.contains("timed out")) || message.contains("multi threaded"))
            {
               result.violation(id, Op.TIMEOUT, opIndex, "not a timeout after " + ms + " ms: " +
                                describe(ex));
            }
            else {
               result.timeouts.incrementAndGet();
               result.timeoutMessages.add(firstLine(ex.getMessage()));
            }
         }

         result.timeoutMillis.addAndGet(
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
         counter();
      }

      private String readScript() {
         if(readScript == null) {
            StringBuilder js = new StringBuilder("[");

            for(int i = 0; i < 20; i++) {
               String name = p + "v" + i;
               js.append(i == 0 ? "" : ", ").append("typeof ").append(name)
                  .append(" === 'undefined' ? '~' : String(").append(name).append(")");
            }

            readScript = js.append("].join(',')").toString();
         }

         return readScript;
      }

      private int depthNow() {
         SlotClaim claim = SlotClaim.current(env.pool());
         return claim == null ? 0 : claim.depth();
      }

      private Object compiled(String js) throws Exception {
         Object script = scripts.get(js);

         if(script == null) {
            script = env.compile(js);
            scripts.put(js, script);
         }

         return script;
      }

      private final WorksheetScriptEnv env;
      private final int id;
      private final Random rnd;
      private final Result result;
      private final AtomicInteger timeoutBudget;
      private final String p;
      private final Map<String, String> vars = new HashMap<>();
      private final Map<String, Object> scripts = new HashMap<>();
      private String readScript;
      int opIndex;
   }

   static final class Result {
      Result(long seed, int threads, long seconds) {
         this.seed = seed;
         this.threads = threads;
         this.seconds = seconds;
      }

      void count(Op op) {
         ops.computeIfAbsent(op, k -> new AtomicLong()).incrementAndGet();
      }

      void violation(int thread, Op op, int opIndex, String what) {
         violations.add("seed=" + seed + " thread=" + thread + " op=" + op + " #" + opIndex +
                        ": " + what);
      }

      long totalOps() {
         return ops.values().stream().mapToLong(AtomicLong::get).sum();
      }

      String summary() {
         return "seed=" + seed + " threads=" + threads + " seconds=" + seconds +
            " ops=" + totalOps() + " " + new TreeMap<>(ops) + " violations=" + violations.size() +
            " watchdogSamples=" + samples + " maxSize=" + maxSize + " highWater=" + highWater +
            " sizeAtEnd=" + sizeAtEnd + " creations=" + creations + " doomedCloses=" +
            doomedCloses + " evictions=" + evictions + " cleans=" + cleans + " execs=" + execs +
            " timeouts=" + timeouts + " timeoutMillis=" + timeoutMillis +
            " timeoutMessages=" + timeoutMessages +
            " interruptTimeouts=" + interruptTimeouts + " maxOpMillis(sampled)=" + maxOpMillis +
            " nodeSlots=" + nodeBefore + "->" + nodeAfter;
      }

      final long seed;
      final int threads;
      final long seconds;
      final Map<Op, AtomicLong> ops = new ConcurrentHashMap<>();
      final Queue<String> violations = new ConcurrentLinkedQueue<>();
      final AtomicLong timeouts = new AtomicLong();
      final AtomicLong timeoutMillis = new AtomicLong();
      final Set<String> timeoutMessages = ConcurrentHashMap.newKeySet();
      volatile int maxSize;
      volatile long maxOpMillis;
      volatile long samples;
      int highWater;
      int sizeAtEnd;
      long creations;
      long doomedCloses;
      long evictions;
      long cleans;
      long execs;
      long interruptTimeouts;
      int nodeBefore;
      int nodeAfter;
   }

   static String firstLine(String text) {
      if(text == null) {
         return "null";
      }

      int nl = text.indexOf('\n');
      return nl < 0 ? text : text.substring(0, nl);
   }

   static String describe(Throwable ex) {
      StringBuilder text = new StringBuilder();

      for(Throwable t = ex; t != null; t = t.getCause()) {
         text.append(t.getClass().getName()).append(": ").append(t.getMessage()).append(" | ");
      }

      StackTraceElement[] stack = ex.getStackTrace();

      for(int i = 0; i < Math.min(8, stack.length); i++) {
         text.append("\n    at ").append(stack[i]);
      }

      return text.toString();
   }

   static String dump(Thread[] threads) {
      long[] ids = Arrays.stream(threads).mapToLong(Thread::getId).toArray();
      StringBuilder text = new StringBuilder();

      for(ThreadInfo info : ManagementFactory.getThreadMXBean().getThreadInfo(ids, true, true)) {
         if(info == null) {
            continue;
         }

         text.append('"').append(info.getThreadName()).append("\" ")
            .append(info.getThreadState()).append(" on ").append(info.getLockName())
            .append(" owned by ").append(info.getLockOwnerName()).append('\n');

         for(StackTraceElement element : info.getStackTrace()) {
            text.append("    at ").append(element).append('\n');
         }
      }

      return text.toString();
   }

   private static void refreshTimeout() throws Exception {
      Field field = GraalJavaScriptEngine.class.getDeclaredField("TIMEOUT_PROP");
      field.setAccessible(true);
      ((SreeEnv.Value) field.get(null)).updateValue();
   }

   private static final long STALL_MS = 5000;
   private static final long OP_CAP_MS = 60000;
   private static final TimeUnit MILLIS = TimeUnit.MILLISECONDS;
   private String previousTimeout;
}
