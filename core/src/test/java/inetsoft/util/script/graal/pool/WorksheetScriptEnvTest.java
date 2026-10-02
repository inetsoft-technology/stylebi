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

import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.util.script.ScriptException;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Spec §5.1 and gates G1, G2, G2b, G3 (exec nesting), G4, G9, §6.9 (bug #76960).
 */
@Tag("core")
class WorksheetScriptEnvTest {
   /**
    * G1: two other threads hold every context of the env, one of them blocked on the caller's
    * lens lock; every entry point still completes on the caller.
    */
   @Test
   void everyEntryPointCompletesWhileOtherThreadsHoldEveryContext() throws Exception {
      WorksheetScriptEnv env = env();
      run(env, "1");
      ReentrantLock lensLock = new ReentrantLock();
      CountDownLatch held = new CountDownLatch(2);
      CountDownLatch release = new CountDownLatch(1);
      ExecutorService executor = Executors.newCachedThreadPool();

      try {
         Future<?> caller = executor.submit(() -> {
            lensLock.lock();

            try {
               executor.submit(() -> {
                  try(SlotClaim claim = env.claimSlot()) {
                     held.countDown();
                     lensLock.lock();
                     lensLock.unlock();
                  }

                  return null;
               });
               executor.submit(() -> {
                  try(SlotClaim claim = env.claimSlot()) {
                     held.countDown();
                     release.await(30, TimeUnit.SECONDS);
                  }

                  return null;
               });
               assertTrue(held.await(10, TimeUnit.SECONDS));
               assertEquals(2, env.getMetrics().getSize());

               env.put("k", 1);
               assertEquals(1, env.get("k"));
               env.remove("k");
               assertNull(env.get("k"));
               assertTrue(env.getIds(null, null, false).length > 0);
               Object script = env.compile("2 + 3");
               env.checkFunction("f", "function f(){ return 1; }");
               assertEquals(5.0, env.exec(script, null, null, null));
               env.init();
               env.setParent(null);
               env.addTopLevelParentScope(null);
               assertNull(env.getScope(null, null));
               assertNull(env.getExecutionLock());
               env.reset();
               assertEquals(5.0, env.exec(script, null, null, null));

               AssetQuerySandbox box = poolBox(true);
               assertNotNull(box.getScope());
               assertTrue(box.getScriptEnv() instanceof WorksheetScriptEnv);
               env.retire();
            }
            finally {
               lensLock.unlock();
            }

            return null;
         });

         caller.get(30, TimeUnit.SECONDS);
      }
      finally {
         release.countDown();
         executor.shutdownNow();
      }
   }

   /**
    * G1 completeness: every public method of the base env that can touch an engine or vars
    * is overridden (M6).
    */
   @Test
   void everyEngineMethodOfTheBaseEnvIsOverridden() throws Exception {
      Set<String> exempt = Set.of("getSuggestion", "getSuggestion0");

      for(Method method : GraalJavaScriptEnv.class.getDeclaredMethods()) {
         if(!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers()) ||
            exempt.contains(method.getName()))
         {
            continue;
         }

         assertNotNull(WorksheetScriptEnv.class.getDeclaredMethod(
            method.getName(), method.getParameterTypes()), method.toString());
      }
   }

   /**
    * G2: a put made after a context was created is still defined after that context's clean,
    * on the primary and on a pooled context.
    */
   @Test
   void latePutSurvivesCleanOnPrimaryAndPooledContexts() throws Exception {
      WorksheetScriptEnv env = env();
      run(env, "1");
      env.put("k", 5);
      assertEquals(5.0, run(env, "k"));
      assertEquals(5.0, run(env, "k"));

      whileHeldElsewhere(env, () -> {
         env.put("k2", 6);
         assertEquals(6.0, run(env, "k2"));
         assertEquals(5.0, run(env, "k"));
         assertEquals(6.0, run(env, "k2"));
      });

      assertEquals(2, env.getMetrics().getHighWater());
   }

   /**
    * G2b: a put inside the caller's own claim, also from a host callback, is visible at once.
    */
   @Test
   void putInsideTheOwnClaimIsVisibleAtOnce() throws Exception {
      WorksheetScriptEnv env = env();
      env.put("cb", new Callback(env));
      assertEquals(3.0, run(env, "cb.put('p', 3); p"));

      try(SlotClaim claim = env.claimSlot()) {
         env.put("q", 4);
         assertEquals(4.0, run(env, "q"));
         env.remove("q");
         assertEquals("undefined", run(env, "typeof q"));
      }
   }

   /**
    * G3: exec, then a host callback, then a nested exec on the same thread shares the claim;
    * the outer state stays intact.
    */
   @Test
   void nestedExecSharesTheOuterClaim() throws Exception {
      WorksheetScriptEnv env = env();
      env.put("cb", new Callback(env));
      assertEquals(9.0, run(env, "var outer = 7; cb.nested('var inner = 2; inner'); outer + inner"));
      // one clean for the outer compile's own claim, one for the outer exec; the nested
      // compile and exec ran inside the outer exec's claim and cleaned nothing
      assertEquals(2, env.getMetrics().getCleans());
      assertEquals("undefined", run(env, "typeof outer"));
   }

   /**
    * G4: reset while another thread is inside an exec completes without waiting, and the
    * context closes at that exec's release.
    */
   @Test
   void resetWhileAnotherThreadExecsNeverWaits() throws Exception {
      WorksheetScriptEnv env = env();
      Callback cb = new Callback(env);
      env.put("cb", cb);
      ExecutorService executor = Executors.newSingleThreadExecutor();

      try {
         Future<Object> busy = executor.submit(() -> run(env, "cb.block(); 1"));
         assertTrue(cb.entered.await(10, TimeUnit.SECONDS));

         long start = System.nanoTime();
         env.reset();
         assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(1), "reset waited");
         assertEquals(0, env.getMetrics().getDoomedCloses());

         cb.release.countDown();
         assertEquals(1.0, busy.get(10, TimeUnit.SECONDS));
         assertEquals(1, env.getMetrics().getDoomedCloses());
      }
      finally {
         cb.release.countDown();
         executor.shutdownNow();
      }
   }

   /**
    * G4/N6: a reset from inside the claiming thread's own exec closes that context only at
    * the outermost release.
    */
   @Test
   void resetFromTheOwnExecClosesAtTheOuterRelease() throws Exception {
      WorksheetScriptEnv env = env();
      env.put("cb", new Callback(env));
      assertEquals(1.0, run(env, "cb.reset(); cb.nested('1'); 1"));
      assertEquals(1, env.getMetrics().getDoomedCloses());
      assertEquals(2.0, run(env, "1 + 1"));
   }

   /**
    * G9: a context created later, on any thread, has the library the env captured.
    */
   @Test
   void laterContextsHaveTheSnapshotLibrary() throws Exception {
      WorksheetScriptEnv env = env(PoolConfig.defaults(),
                                   Map.of("libOrg", "function libOrg(){ return 'org1'; }"));
      assertEquals("org1", run(env, "libOrg()"));
      whileHeldElsewhere(env, () -> assertEquals("org1", run(env, "libOrg()")));
      assertEquals(2, env.getMetrics().getCreations());
   }

   /**
    * Spec §5.1: get() reads the env's variables, never a context's globals.
    */
   @Test
   void getReadsEnvVariablesOnly() throws Exception {
      WorksheetScriptEnv env = env();
      env.put("a", 1);
      assertEquals(5.0, run(env, "a = 5; a"));
      assertEquals(1, env.get("a"));
   }

   /**
    * Spec §6.9 (M3): the env owns the script.max.errors counts and clears them on retire.
    */
   @Test
   void errorCountsAreOwnedByTheEnvAndClearedOnRetire() throws Exception {
      WorksheetScriptEnv env = env();
      Object bad = env.compile("undefinedName_76960 + 1");
      assertThrows(ScriptException.class, () -> env.exec(bad, null, null, null));
      assertEquals(1, env.errorCounts().get(bad));
      env.retire();
      assertTrue(env.errorCounts().isEmpty());
   }

   /**
    * Spec §6.9 (ledger T8 follow-up): reset() retires the contexts and clears the counts too,
    * as main's reset re-init did.
    */
   @Test
   void resetClearsErrorCounts() throws Exception {
      WorksheetScriptEnv env = env();
      Object bad = env.compile("undefinedName_76960b + 1");
      assertThrows(ScriptException.class, () -> env.exec(bad, null, null, null));
      assertEquals(1, env.errorCounts().get(bad));
      env.reset();
      assertTrue(env.errorCounts().isEmpty());
   }

   /**
    * Spec §5.2: slots are never shared across sandboxes' envs.
    */
   @Test
   void envsNeverShareContexts() throws Exception {
      WorksheetScriptEnv a = env();
      WorksheetScriptEnv b = env();

      try(SlotClaim ca = a.claimSlot(); SlotClaim cb = b.claimSlot()) {
         assertNotSame(ca.slot(), cb.slot());
      }
   }

   /**
    * Spec §5.3: a span keeps one context for all its execs and cleans once.
    */
   @Test
   void openSpanKeepsOneContextAndCleansOnce() throws Exception {
      WorksheetScriptEnv env = env();

      try(var span = env.openSpan()) {
         assertEquals(256, span.batchRows());
         run(env, "acc = 1");
         assertEquals(2.0, run(env, "++acc"));
      }

      assertEquals(1, env.getMetrics().getCleans());
      assertEquals("undefined", run(env, "typeof acc"));
   }
}
