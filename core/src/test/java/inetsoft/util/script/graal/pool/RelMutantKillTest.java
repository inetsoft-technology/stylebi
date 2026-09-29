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

import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests added by the mutation run (bug #77123, seat mut): each kills a hand mutant of the pool
 * that the existing suites let survive (docs/teams/2026-09-29-pool-reliability/mutation).
 */
@Tag("core")
class RelMutantKillTest {
   public static final class H {
      public void keep(Map<String, Object> m) {
         kept = m;
      }

      volatile Map<String, Object> kept;
   }

   public static final class Hook {
      Hook(Runnable body) {
         this.body = body;
      }

      public void fire() {
         body.run();
      }

      private final Runnable body;
   }

   @AfterEach
   void tearDown() {
      ex.shutdownNow();

      for(WorksheetScriptEnv env : envs) {
         env.retire();
      }

      for(SlotPool pool : pools) {
         pool.retire();
      }

      assertEquals(0, SlotClaim.openClaims());
   }

   /**
    * A plain object passed to a Java method is one host copy, nested objects included: a nested
    * live view would follow the script's later writes, and could not be read on another thread
    * once the context moves on. (Written for mutant 16, which it does not kill: see
    * selfContainingObjectPassedToJavaFailsAsTooDeep.)
    */
   @Test
   void nestedObjectOfAJavaMapArgumentIsACopy() throws Exception {
      WorksheetScriptEnv env = env();
      H h = new H();
      env.put("h", h);

      run(env, "var o = {a: {b: 1, c: [1, 2]}}; h.keep(o); o.a.b = 2; o.a.c.push(3); o.a.d = 4; 0");

      Map<String, Object> kept = h.kept;
      assertInstanceOf(CopyMap.class, kept.get("a"), "the nested object is a host copy");
      String owner = String.valueOf(kept);
      String worker = ex.submit(() -> String.valueOf(h.kept)).get(10, TimeUnit.SECONDS);
      assertEquals("{a={b=1, c=[1, 2]}}", owner, "the copy never follows the script's writes");
      assertEquals(owner, worker, "every thread reads the same copy");
   }

   /**
    * Mutant 16 (CopyMap shallow, nested values through Value.as): Value.as(Object) reaches the
    * same HostAccess mappings, so nested values are still copied, but past the copier's depth
    * guard: a plain object that contains itself must fail as "nested too deeply", a script
    * error, never overflow the host stack while it is copied for a Java argument.
    */
   @Test
   void selfContainingObjectPassedToJavaFailsAsTooDeep() throws Exception {
      WorksheetScriptEnv env = env();
      env.put("h", new H());

      Exception ex = assertThrows(Exception.class,
                                  () -> run(env, "var o = {a: 1}; o.self = o; h.keep(o); 0"));
      assertTrue(String.valueOf(ex.getMessage()).contains("nested too deeply"),
                 "failed as " + ex);
      // the context is still usable
      assertEquals(3.0, run(env, "1 + 2"));
   }

   /**
    * Mutant 32 (release keeps a slot retired during its clean): a retire that lands while the
    * releasing thread cleans its slot dooms it, and release closes it at once: it never goes
    * idle, not even until closeIfRetired's second look.
    */
   @Test
   void slotRetiredDuringItsCleanNeverGoesIdle() throws Exception {
      WorksheetScriptEnv env = env();
      SlotPool pool = env.pool();
      AtomicInteger idled = new AtomicInteger();
      Slot slot;

      try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
         slot = claim.slot();
         Value hook = slot.engine().context().asValue(new Hook(pool::retire));
         injectClean(slot, "(function(h) { return function() { h.fire(); return {leftovers: 0, " +
                     "failed: false, restored: 0, removed: 0, tooMany: false}; }; })", hook);
         pool.beforeIdleHook = idled::incrementAndGet;
      }
      finally {
         pool.beforeIdleHook = null;
      }

      assertTrue(slot.isClosed(), "the retired slot is closed at its release");
      assertEquals(0, idled.get(), "the retired slot never went idle");
      assertEquals(0, env.getMetrics().getSize());
   }

   /**
    * Mutant 19 (retire does not advance the epoch): a retire that lands while a checkout
    * creates a context, before the pool can see it to doom it, still retires that context:
    * its epoch is older than the pool's at prepare, so it is closed, and the checkout takes a
    * fresh one.
    */
   @Test
   void retireDuringACreationRetiresTheCreatedContext() throws Exception {
      SlotPool[] pool = new SlotPool[1];
      RetiringSource source = new RetiringSource(() -> {
         // another thread resets the env while this one builds its context
         return ex.submit(() -> pool[0].retire()).get(10, TimeUnit.SECONDS);
      });
      pool[0] = new SlotPool(source, PoolConfig.defaults(), source.metrics);
      pools.add(pool[0]);

      try(SlotClaim claim = SlotClaim.acquire(pool[0], false)) {
         assertEquals(2, source.created.size(), "the context created across the retire is replaced");
         assertTrue(source.created.get(0).isClosed(), "the context created across the retire is closed");
         assertSame(source.created.get(1), claim.slot());
      }

      assertEquals(1, source.metrics.getSize());
   }

   /**
    * Runs a hook inside its first creation, after the new slot's epoch is stamped.
    */
   static final class RetiringSource implements SlotSource {
      RetiringSource(Callable<?> hook) {
         this.hook = hook;
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
         Slot slot = Slot.create(new InitSnapshot("org0", Map.of()), state.snapshot(), epoch, false,
                                 Collections.synchronizedMap(new WeakHashMap<>()), metrics);
         created.add(slot);

         if(created.size() == 1) {
            hook.call();
         }

         return slot;
      }

      final Callable<?> hook;
      final List<Slot> created = new CopyOnWriteArrayList<>();
      final EnvState state = new EnvState();
      final PoolMetrics metrics = new PoolMetrics();
   }

   /**
    * Mutant 28 (WsExecContext.exit drops the outer exec's mark): after a nested exec on the
    * same context returns, the outer exec is still the pooled context executing on this
    * thread, so its plain-object result still crosses the boundary as a host copy.
    */
   @Test
   void outerExecKeepsItsBoundaryAfterANestedExec() throws Exception {
      WorksheetScriptEnv env = env();
      env.put("cb", new Nested(env));

      Object result = run(env, "cb.run('1'); ({a: 1, b: {c: 2}})");

      assertInstanceOf(CopyMap.class, result, "the outer result is a host copy");
      assertEquals("{a=1.0, b={c=2.0}}", String.valueOf(result), "main's shapes");
   }

   public static final class Nested {
      Nested(WorksheetScriptEnv env) {
         this.env = env;
      }

      public Object run(String js) throws Exception {
         return PoolTestSupport.run(env, js);
      }

      private final WorksheetScriptEnv env;
   }

   private WorksheetScriptEnv env() {
      WorksheetScriptEnv env = PoolTestSupport.env();
      envs.add(env);
      return env;
   }

   private final ExecutorService ex = Executors.newFixedThreadPool(2);
   private final List<WorksheetScriptEnv> envs = new ArrayList<>();
   private final List<SlotPool> pools = new ArrayList<>();
}
