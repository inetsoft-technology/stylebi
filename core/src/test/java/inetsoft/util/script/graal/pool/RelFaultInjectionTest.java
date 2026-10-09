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

import inetsoft.test.*;
import inetsoft.util.script.ScriptException;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Fault injection at every seam of the worksheet script context pool (bug #77123, reliability
 * plan task 4). Each case asserts: a correct result or a loud error, no context reused
 * unclean, no claim or context leaked.
 *
 * <p>"Unclean" is detected with a marker: every script sets the implicit global
 * {@code __dirty}, which a clean deletes, and reports whether it was already set; a top-level
 * exec (its own claim) that finds it set was handed a context another claim left unclean.
 *
 * <p>The clean seam is injected without a product hook: the slot's {@code cleaner} field is
 * swapped, by reflection, for a {@link CleanHelper} whose clean function is a guest function
 * of the same context that throws (or calls a host object that throws).
 *
 * <p>The first context of the JVM initializes {@code MapData}, which reads the
 * {@code DataSpace} bean, so the class runs in its own Spring context.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class RelFaultInjectionTest {
   @BeforeEach
   void setUp() {
      leakedBefore = PoolMetrics.nodeLeakedClaims();
   }

   @AfterEach
   void tearDown() throws Exception {
      executor.shutdownNow();
      // no task may outlive the class's Spring context
      assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "a task outlived its test");

      for(WorksheetScriptEnv env : envs) {
         env.retire();
      }

      for(SlotPool pool : pools) {
         pool.retire();
      }

      assertEquals(0, SlotClaim.openClaims());
      assertEquals(leakedBefore, PoolMetrics.nodeLeakedClaims(), "a claim leaked");
   }

   // ---- SlotSource.create throws ----

   @Test
   void createThrowingOnTheFirstContextIsLoudAndLeavesNothing() throws Exception {
      FaultySource source = new FaultySource(Set.of(1));
      SlotPool pool = pool(source, PoolConfig.defaults());

      ScriptException ex = assertThrows(ScriptException.class, () -> SlotClaim.acquire(pool, false));
      assertTrue(ex.getMessage().contains("injected create failure"), ex.getMessage());
      assertEquals(0, SlotClaim.openClaims());
      assertEquals(0, source.metrics.getSize());
      assertNull(pool.primary());

      // the next checkout creates the primary and works
      try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
         assertEquals("undefined:3", exec(claim.slot(), MARKED_SUM));
         assertSame(pool.primary(), claim.slot());
      }

      assertPoolConsistent(pool, source.metrics);
   }

   @Test
   void createThrowingOnTheNthContextIsLoudAndOtherClaimsGoOn() throws Exception {
      FaultySource source = new FaultySource(Set.of(3));
      SlotPool pool = pool(source, PoolConfig.defaults());
      CountDownLatch held = new CountDownLatch(2);
      CountDownLatch done = new CountDownLatch(1);
      List<Future<String>> holders = new ArrayList<>();

      for(int i = 0; i < 2; i++) {
         holders.add(executor.submit(() -> {
            try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
               held.countDown();
               assertTrue(done.await(30, TimeUnit.SECONDS));
               return exec(claim.slot(), MARKED_SUM);
            }
         }));
      }

      assertTrue(held.await(10, TimeUnit.SECONDS));
      assertEquals(2, source.creates.get());
      // both contexts are held: the third checkout creates, and the creation fails
      ScriptException ex = assertThrows(ScriptException.class, () -> SlotClaim.acquire(pool, false));
      assertTrue(ex.getMessage().contains("injected create failure"), ex.getMessage());
      assertEquals(0, SlotClaim.openClaims());
      assertEquals(2, source.metrics.getSize());

      // the fourth creation succeeds while the holders still hold theirs
      try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
         assertEquals("undefined:3", exec(claim.slot(), MARKED_SUM));
      }

      done.countDown();

      for(Future<String> holder : holders) {
         assertEquals("undefined:3", holder.get(10, TimeUnit.SECONDS));
      }

      assertEquals(3, source.metrics.getSize());
      assertPoolConsistent(pool, source.metrics);
   }

   // ---- the clean throws ----

   @Test
   void cleanThrowingClosesTheContextInsteadOfReusingIt() throws Exception {
      FaultySource source = new FaultySource(Set.of());
      SlotPool pool = pool(source, PoolConfig.defaults());
      Slot slot;

      try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
         slot = claim.slot();
         exec(slot, MARKED_SUM);
         injectClean(slot, "(function() { throw new Error('injected clean failure'); })");
      }

      assertTrue(slot.isClosed(), "a context whose clean threw is closed");
      assertEquals(0, source.metrics.getSize());
      assertNull(pool.primary());

      try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
         assertNotSame(slot, claim.slot());
         assertEquals("undefined:3", exec(claim.slot(), MARKED_SUM));
      }

      assertPoolConsistent(pool, source.metrics);
   }

   /**
    * An Error (not a RuntimeException) thrown by host code the clean reaches. GraalJS hands it
    * to the host as a PolyglotException (a RuntimeException), so Slot.clean() fails the clean;
    * if it ever escaped as the Error, release's finally must still close the context. Either
    * way the context is closed and the claim is gone.
    */
   @Test
   void errorThrownInsideTheCleanClosesTheContext() throws Exception {
      FaultySource source = new FaultySource(Set.of());
      SlotPool pool = pool(source, PoolConfig.defaults());
      Slot[] slot = new Slot[1];
      Throwable thrown = null;

      try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
         slot[0] = claim.slot();
         exec(slot[0], MARKED_SUM);
         Value bomb = slot[0].engine().context().asValue(new Bomb());
         injectClean(slot[0], "(function(b) { return function() { b.error(); }; })", bomb);
      }
      catch(Throwable ex) {
         thrown = ex;
      }

      System.err.println("REL-FAULT clean-error surfaced as " +
                         (thrown == null ? "nothing (clean failed quietly)" : describe(thrown)));
      assertTrue(slot[0].isClosed(), "a context whose clean threw an Error is closed");
      assertEquals(0, SlotClaim.openClaims());
      assertEquals(0, source.metrics.getSize());

      try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
         assertEquals("undefined:3", exec(claim.slot(), MARKED_SUM));
      }

      assertPoolConsistent(pool, source.metrics);
   }

   // ---- the clean times out ----

   /**
    * A 1 ms clean bound over a heavy global (5000 declared globals, each a leftover the clean
    * sets to undefined). A clean that times out fails and the context is closed; one that
    * finished leaves a reusable, clean context. Either way the next claim sees no value.
    */
   @Test
   void cleanTimingOutNeverLeavesAContextUnclean() throws Exception {
      int heavyClosed = cleanWithA1msBound(5000);
      assertTrue(heavyClosed > 0, "no clean ever timed out; the case did not exercise the seam");
      // a light global: the clean often finishes within the bound and the context is reused
      cleanWithA1msBound(20);
   }

   private int cleanWithA1msBound(int globals) throws Exception {
      // leftovers are allowed, so only a timeout keeps the context from being reused
      FaultySource source = new FaultySource(Set.of());
      SlotPool pool = pool(source, new PoolConfig(60000L, 100_000, 16, 2000, 256, 8192));
      StringBuilder heavy = new StringBuilder();

      for(int i = 0; i < globals; i++) {
         heavy.append("var h").append(i).append(" = ").append(i).append(";\n");
      }

      heavy.append("__dirty = 1; 0");
      int closed = 0;
      int kept = 0;

      for(int round = 0; round < 30; round++) {
         Slot slot;

         try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
            slot = claim.slot();
            slot.cleanTimeout = Duration.ofMillis(1);
            slot.engine().context().eval("js", heavy.toString());
         }

         try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
            Slot next = claim.slot();

            if(next == slot) {
               kept++;
               assertEquals("undefined|undefined|undefined", eval(next,
                  "typeof __dirty + '|' + typeof h0 + '|' + typeof h" + (globals - 1)),
                  "a context kept after a 1 ms clean still has values");
            }
            else {
               closed++;
               assertTrue(slot.isClosed(), "a context not reused was closed");
               assertEquals("undefined", eval(next, "typeof __dirty"));
            }
         }
      }

      System.err.println("REL-FAULT clean-timeout globals=" + globals + " rounds=30 closed=" +
                         closed + " kept=" + kept);
      assertPoolConsistent(pool, source.metrics);
      return closed;
   }

   // ---- interrupts ----

   /**
    * Interrupt the exec thread at a random point 0-20 ms into an exec of about 40 ms (measured
    * and printed as execMillis), 200 times. The
    * interrupt can land in the checkout, the eval, the clean or after; each exec returns its
    * correct result or fails loudly, and the next exec on that thread and on another thread
    * gets a clean context.
    */
   @Test
   void interruptingTheExecThreadAtRandomPointsNeverLeavesAnUncleanContext() throws Exception {
      WorksheetScriptEnv env = env(PoolConfig.defaults());
      Random rnd = new Random(Long.getLong("rel.seed", 77123L));
      ExecutorService worker = Executors.newSingleThreadExecutor(daemon("rel-interrupt"));
      Map<String, Integer> outcomes = new TreeMap<>();
      int n = 20000 + rnd.nextInt(1000);
      String want = "undefined:" + ((long) n * (n - 1) / 2);
      String js = markedLoop(n);

      try {
         // warm up, and learn how long one exec takes
         long start = System.nanoTime();
         assertEquals(want, worker.submit(() -> run(env, js)).get(30, TimeUnit.SECONDS));
         long execMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

         for(int i = 0; i < 200; i++) {
            AtomicReference<Thread> thread = new AtomicReference<>();
            CountDownLatch started = new CountDownLatch(1);
            Future<Object> f = worker.submit(() -> {
               thread.set(Thread.currentThread());
               started.countDown();
               return run(env, js);
            });
            assertTrue(started.await(10, TimeUnit.SECONDS));
            Thread.sleep(rnd.nextInt(21));
            thread.get().interrupt();

            try {
               Object got = f.get(30, TimeUnit.SECONDS);
               assertEquals(want, got, "round " + i);
               outcomes.merge("ok", 1, Integer::sum);
            }
            catch(ExecutionException ex) {
               // the only loud outcome accepted: the script stopped by the interrupt
               Throwable cause = ex.getCause();
               assertTrue(cause instanceof ScriptException && cause.getMessage() != null &&
                          cause.getMessage().toLowerCase().contains("interrupt"),
                          "round " + i + ": " + describe(cause));
               outcomes.merge(cause.getClass().getSimpleName() + ": " +
                              firstLine(cause.getMessage()), 1, Integer::sum);
            }

            // the worker thread keeps no claim and gets a clean context; so does another
            Future<Object> after = worker.submit(() -> {
               Thread.interrupted();
               assertEquals(0, SlotClaim.openClaims());
               return run(env, MARKED_SUM);
            });
            assertEquals("undefined:3", after.get(30, TimeUnit.SECONDS), "round " + i);
            assertEquals("undefined:3", run(env, MARKED_SUM), "round " + i);
         }

         System.err.println("REL-FAULT interrupt rounds=200 execMillis=" + execMillis +
                            " outcomes=" + outcomes + " size=" + env.getMetrics().getSize() +
                            " creations=" + env.getMetrics().getCreations());
         assertEquals(0, worker.submit(SlotClaim::openClaims).get(10, TimeUnit.SECONDS));
         assertEnvConsistent(env);
      }
      finally {
         worker.shutdownNow();
         assertTrue(worker.awaitTermination(30, TimeUnit.SECONDS), "the worker outlived its test");
      }
   }

   // ---- eviction ----

   /**
    * idleMillis = 1 and an evictor looping on another thread while 4 threads exec: no exec
    * ever runs on a closed or unclean context.
    */
   @Test
   void evictingInALoopDuringExecsNeverBreaksOne() throws Exception {
      WorksheetScriptEnv env = env(new PoolConfig(1L, 256, 16, 2000, 256, 8192));
      AtomicBoolean stop = new AtomicBoolean();
      AtomicLong passes = new AtomicLong();
      Future<?> evictor = executor.submit(() -> {
         while(!stop.get()) {
            env.pool().evictIdle(System.currentTimeMillis());
            passes.incrementAndGet();
            // keep racing without spinning a core
            Thread.yield();
         }

         return null;
      });
      List<Future<Integer>> users = new ArrayList<>();

      for(int t = 0; t < 4; t++) {
         users.add(executor.submit(() -> {
            int ok = 0;

            for(int i = 0; i < 400; i++) {
               if(i % 3 == 0) {
                  try(SlotClaim claim = env.claimSlot()) {
                     assertEquals("undefined:3", run(env, MARKED_SUM));
                     assertEquals("number:3", run(env, MARKED_SUM), "within one claim");
                  }
               }
               else {
                  assertEquals("undefined:3", run(env, MARKED_SUM));
               }

               ok++;
            }

            return ok;
         }));
      }

      try {
         for(Future<Integer> user : users) {
            assertEquals(400, user.get(120, TimeUnit.SECONDS));
         }
      }
      finally {
         stop.set(true);
      }

      evictor.get(10, TimeUnit.SECONDS);
      System.err.println("REL-FAULT evict-loop passes=" + passes + " evictions=" +
                         env.getMetrics().getEvictions() + " creations=" +
                         env.getMetrics().getCreations());
      assertTrue(env.getMetrics().getEvictions() > 0, "the evictor never evicted");
      assertEnvConsistent(env);
   }

   // ---- retire ----

   @Test
   void retireFromAnotherThreadInsideANestedClaimClosesOnlyAtTheOuterRelease() throws Exception {
      WorksheetScriptEnv env = env(PoolConfig.defaults());
      CountDownLatch inside = new CountDownLatch(1);
      CountDownLatch retired = new CountDownLatch(1);
      AtomicReference<Slot> held = new AtomicReference<>();
      Future<String> owner = executor.submit(() -> {
         StringBuilder got = new StringBuilder();

         try(SlotClaim outer = env.claimSlot()) {
            held.set(outer.slot());

            try(SlotClaim mid = env.claimSlot(); SlotClaim inner = env.claimSlot()) {
               inside.countDown();
               assertTrue(retired.await(10, TimeUnit.SECONDS));
               assertFalse(inner.slot().isClosed());
               got.append(run(env, MARKED_SUM));
            }

            got.append(',').append(run(env, MARKED_SUM));
            assertFalse(outer.slot().isClosed(), "closed before the outer release");
         }

         return got.toString();
      });

      assertTrue(inside.await(10, TimeUnit.SECONDS));
      long start = System.nanoTime();
      env.retire();
      // generous for a loaded machine; a waiting retire would sit out the owner's 10 s latch
      assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(5), "retire waited");
      assertTrue(held.get().isDoomed());
      retired.countDown();
      // the claim's globals stay within it (the documented per-claim drift)
      assertEquals("undefined:3,number:3", owner.get(10, TimeUnit.SECONDS));
      assertTrue(held.get().isClosed(), "a retired context is closed at its outer release");
      assertEquals("undefined:3", run(env, MARKED_SUM));
      assertEnvConsistent(env);
   }

   /**
    * The same race at random timing: a retire lands 0-3 ms after the owner starts a depth-3
    * nested claim, 200 times.
    *
    * <p>The claim mostly lasts well under a millisecond, so most random retires land before or
    * after it (runs counted 3 to 26 of 200 inside). Until one has landed inside, up to a
    * deadline, more rounds retire right after the owner signals that it holds the outer claim,
    * so the case always exercises the race whatever the machine's load or timer resolution.
    */
   @Test
   void retireAtRandomPointsOfANestedClaim() throws Exception {
      WorksheetScriptEnv env = env(PoolConfig.defaults());
      Random rnd = new Random(Long.getLong("rel.seed", 77123L) + 1);
      long doomedBefore = env.getMetrics().getDoomedCloses();
      int rounds = 0;

      for(; rounds < 200; rounds++) {
         retireDuringANestedClaim(env, rounds, false, rnd.nextInt(3000));
      }

      long randomInside = env.getMetrics().getDoomedCloses() - doomedBefore;
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);

      while(env.getMetrics().getDoomedCloses() == doomedBefore && System.nanoTime() < deadline) {
         retireDuringANestedClaim(env, rounds++, true, 0);
      }

      System.err.println("REL-FAULT retire-random rounds=" + rounds + " randomInside=" +
                         randomInside + " doomedCloses=" +
                         (env.getMetrics().getDoomedCloses() - doomedBefore) + " creations=" +
                         env.getMetrics().getCreations());
      assertTrue(env.getMetrics().getDoomedCloses() > doomedBefore,
                 "no retire ever landed inside a claim in " + rounds + " rounds");
      assertEnvConsistent(env);
   }

   /**
    * One round: an owner thread runs a depth-3 nested claim and this thread retires the env
    * {@code micros} after the owner starts, or after it holds the outer claim if
    * {@code afterClaim}.
    */
   private void retireDuringANestedClaim(WorksheetScriptEnv env, int round, boolean afterClaim,
                                         int micros)
      throws Exception
   {
      CountDownLatch started = new CountDownLatch(1);
      CountDownLatch claimed = new CountDownLatch(1);
      Future<String> owner = executor.submit(() -> {
         started.countDown();
         StringBuilder got = new StringBuilder();

         try(SlotClaim a = env.claimSlot()) {
            claimed.countDown();
            got.append(run(env, MARKED_SUM));

            try(SlotClaim b = env.claimSlot()) {
               got.append(run(env, MARKED_SUM));

               try(SlotClaim c = env.claimSlot()) {
                  got.append(run(env, markedLoop(2000)));
               }
            }
         }

         return got.toString();
      });
      assertTrue((afterClaim ? claimed : started).await(10, TimeUnit.SECONDS));
      LockSupport_parkMicros(micros);
      env.retire();
      String got = owner.get(30, TimeUnit.SECONDS);
      // the first exec of the claim sees a clean context; later ones see its own marker
      assertTrue(got.equals("undefined:3number:3number:1999000"), "round " + round + ": " + got);
      assertEquals("undefined:3", run(env, MARKED_SUM), "round " + round);
   }

   // ---- reset from a script callback ----

   @Test
   void resetInsideAScriptCallbackFinishesTheScriptAndClosesTheContextAfter() throws Exception {
      WorksheetScriptEnv env = env(PoolConfig.defaults());
      env.put("cb", new Callback(env));
      run(env, "1");
      Slot primary = env.pool().primary();
      long doomed = env.getMetrics().getDoomedCloses();

      assertEquals(42.0, run(env, "__dirty = 1; cb.reset(); cb.nested('40 + 2')"));
      assertTrue(primary.isClosed(), "the reset context is closed at its release");
      assertEquals(doomed + 1, env.getMetrics().getDoomedCloses());
      // the env's variables survive the reset
      assertEquals("undefined:3", run(env, MARKED_SUM));
      assertEquals(1.0, run(env, "cb.put('x', 5)"));
      assertEquals(5.0, run(env, "x"));

      // and concurrently with another thread's execs
      Future<Integer> other = executor.submit(() -> {
         int ok = 0;

         for(int i = 0; i < 200; i++) {
            if("undefined:3".equals(run(env, MARKED_SUM))) {
               ok++;
            }
         }

         return ok;
      });

      for(int i = 0; i < 50; i++) {
         assertEquals(7.0, run(env, "__dirty = 1; cb.reset(); 3 + 4"));
      }

      assertEquals(200, other.get(60, TimeUnit.SECONDS));
      assertEnvConsistent(env);
   }

   // ---- an Error from a host callback ----

   @Test
   void errorFromAHostCallbackIsLoudAndLeavesNoUncleanContext() throws Exception {
      assertHostErrorIsContained("error");
   }

   @Test
   void outOfMemoryErrorFromAHostCallbackIsLoudAndLeavesNoUncleanContext() throws Exception {
      assertHostErrorIsContained("oom");
   }

   @Test
   void stackOverflowErrorFromAHostCallbackIsLoudAndLeavesNoUncleanContext() throws Exception {
      assertHostErrorIsContained("soe");
   }

   private void assertHostErrorIsContained(String kind) throws Exception {
      WorksheetScriptEnv env = env(PoolConfig.defaults());
      env.put("bomb", new Bomb());
      assertEquals("undefined:3", run(env, MARKED_SUM));
      Map<String, Integer> outcomes = new TreeMap<>();

      for(int i = 0; i < 20; i++) {
         // only a ScriptException carrying the host error's message is contained; anything
         // else, including a return, propagates or fails here
         ScriptException thrown = assertThrows(ScriptException.class,
            () -> run(env, "__dirty = 1; bomb." + kind + "(); 'not reached'"), kind);
         assertTrue(thrown.getMessage() != null && thrown.getMessage().contains("injected host"),
                    kind + ": " + thrown.getMessage());
         outcomes.merge(thrown.getClass().getSimpleName() + ": " + firstLine(thrown.getMessage()),
                        1, Integer::sum);
         assertEquals(0, SlotClaim.openClaims());
         assertEquals("undefined:3", run(env, MARKED_SUM), kind + " round " + i);
         assertEquals("undefined:3", executor.submit(() -> run(env, MARKED_SUM))
            .get(30, TimeUnit.SECONDS), kind + " round " + i);
      }

      System.err.println("REL-FAULT host-" + kind + " rounds=20 outcomes=" + outcomes +
                         " creations=" + env.getMetrics().getCreations() + " size=" +
                         env.getMetrics().getSize());
      assertEnvConsistent(env);

      // a new env on the shared engine still works
      WorksheetScriptEnv fresh = env(PoolConfig.defaults());
      assertEquals("undefined:3", run(fresh, MARKED_SUM));
   }

   /**
    * Host methods a script calls that throw an Error.
    */
   public static final class Bomb {
      public void error() {
         throw new InjectedError("injected host error");
      }

      public void oom() {
         throw new OutOfMemoryError("injected host OutOfMemoryError");
      }

      public void soe() {
         throw new StackOverflowError("injected host StackOverflowError");
      }
   }

   static final class InjectedError extends Error {
      InjectedError(String message) {
         super(message);
      }
   }

   // ---- helpers ----

   /**
    * A marker check plus 0+1+2: "undefined:3" on a clean context.
    */
   static final String MARKED_SUM =
      "(function() { var w = typeof __dirty; __dirty = 1; return w + ':' + (0 + 1 + 2); })()";

   static String markedLoop(int n) {
      return "(function() { var w = typeof __dirty; __dirty = 1; var s = 0; " +
         "for(var i = 0; i < " + n + "; i++) { s += i; } return w + ':' + s; })()";
   }

   private static String exec(Slot slot, String js) throws Exception {
      WsEngine engine = slot.engine();
      return String.valueOf(engine.exec(engine.compile(js), null, null));
   }

   private static String eval(Slot slot, String js) {
      return slot.engine().context().eval("js", js).toString();
   }

   private WorksheetScriptEnv env(PoolConfig config) {
      WorksheetScriptEnv env = PoolTestSupport.env(config, Map.of());
      envs.add(env);
      return env;
   }

   private SlotPool pool(FaultySource source, PoolConfig config) {
      SlotPool pool = new SlotPool(source, config, source.metrics);
      pools.add(pool);
      return pool;
   }

   private static void assertEnvConsistent(WorksheetScriptEnv env) {
      assertPoolConsistent(env.pool(), env.getMetrics());
      env.retire();
      env.pool().evictIdle(Long.MAX_VALUE);
      assertEquals(0, env.getMetrics().getSize(), "contexts left after retire + evict");
      assertTrue(env.pool().slots().isEmpty());
   }

   /**
    * At rest: every counted context is an open slot of the pool and the reverse.
    */
   private static void assertPoolConsistent(SlotPool pool, PoolMetrics metrics) {
      List<Slot> slots = pool.slots();

      for(Slot slot : slots) {
         assertFalse(slot.isClosed(), "a closed slot is still pooled");
      }

      assertEquals(slots.size(), metrics.getSize(), "counted contexts != pooled slots");
      assertTrue(metrics.getSize() <= metrics.getHighWater());
   }

   private static void LockSupport_parkMicros(int micros) {
      java.util.concurrent.locks.LockSupport.parkNanos(micros * 1000L);
   }

   private static String firstLine(String text) {
      if(text == null) {
         return "null";
      }

      int nl = text.indexOf('\n');
      return nl < 0 ? text : text.substring(0, nl);
   }

   private static String describe(Throwable ex) {
      StringBuilder text = new StringBuilder();

      for(Throwable t = ex; t != null; t = t.getCause()) {
         text.append(t.getClass().getName()).append(": ").append(firstLine(t.getMessage()))
            .append(" | ");
      }

      return text.toString();
   }

   private static ThreadFactory daemon(String name) {
      return r -> {
         Thread thread = new Thread(r, name);
         thread.setDaemon(true);
         return thread;
      };
   }

   /**
    * A slot source like the env's, failing the creations whose 1-based index is listed.
    */
   static final class FaultySource implements SlotSource {
      FaultySource(Set<Integer> failOn) {
         this.failOn = failOn;
      }

      @Override
      public EnvState state() {
         return state;
      }

      @Override
      public boolean isSQL() {
         return false;
      }

      @Override
      public Slot create(long epoch) throws Exception {
         if(failOn.contains(creates.incrementAndGet())) {
            throw new IllegalStateException("injected create failure #" + creates.get());
         }

         return Slot.create(new InitSnapshot("org0", Map.of()), state.snapshot(), epoch, false,
                            Collections.synchronizedMap(new WeakHashMap<>()), metrics);
      }

      final Set<Integer> failOn;
      final AtomicInteger creates = new AtomicInteger();
      final EnvState state = new EnvState();
      final PoolMetrics metrics = new PoolMetrics();
   }

   private long leakedBefore;
   private final ExecutorService executor = Executors.newCachedThreadPool(daemon("rel-fault"));
   private final List<WorksheetScriptEnv> envs = new ArrayList<>();
   private final List<SlotPool> pools = new ArrayList<>();
}
