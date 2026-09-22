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
package inetsoft.report.composition.execution;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for bug #76918: a live jstack-confirmed deadlock between
 * {@code AbstractConditionFilter}'s {@code synchronized(this)} monitor (entered via
 * {@code moreRows()}, held by a thread populating {@code PostProcessor$ConditionFilter2}'s
 * row map) and the GraalJS engine's per-sandbox {@code ReentrantLock} (already held by a
 * *different* thread mid-script, whose guest JS row-member read re-enters the very same
 * filter instance via {@code TableRow.getMember -> AbstractConditionFilter.getObject ->
 * getBaseRowIndex -> moreRows}).
 *
 * <p>Thread A: {@code AbstractConditionFilter.moreRows()} (AbstractConditionFilter.java:185)
 * takes the filter's monitor (line 193), then -- still holding it -- calls
 * {@code table.moreRows(baseRow)} (line 202), which can cascade into
 * {@code FormulaTableLens.exec() -> GraalJavaScriptEngine.exec()}'s {@code lock.lock()}
 * (GraalJavaScriptEngine.java:1196) to evaluate a calculated field.
 *
 * <p>Thread B: already inside {@code GraalJavaScriptEngine.exec()}'s locked region
 * (line 1233, {@code context.eval(...)}) for a *different* formula, when the guest script's
 * own row-member access re-enters {@code AbstractConditionFilter.getObject/getBaseRowIndex/
 * moreRows} (AbstractConditionFilter.java:300/132/193) on the *same*, shared filter instance
 * (shared across concurrent chart-tile threads via {@code AssetDataCache}) -- and blocks on
 * the monitor Thread A holds. Classic AB-BA.
 *
 * <p>The fix ({@code PostProcessor.ConditionFilter2.moreRows()}) acquires the sandbox's
 * script-execution lock -- the exact same lock {@code GraalJavaScriptEngine.exec()} uses --
 * *before* delegating to the inherited, unmodified {@code synchronized(this)} critical
 * section, for every path into the filter. This does not narrow or remove either lock's
 * critical section (the monitor still protects {@code rowmap}/{@code baseRow}/
 * {@code completed} exactly as before; the engine lock still covers the full
 * {@code context.eval()}), so neither of the data races a naive narrowing would reintroduce
 * (row-mapping corruption, or GraalVM's single-thread-per-Context requirement) is affected.
 * It only changes *when* the engine lock is requested: always no later than the monitor, for
 * every code path, which makes the two locks' acquisition order globally consistent and
 * breaks the cycle. A thread already holding the engine lock (Thread B) just re-acquires it
 * reentrantly and proceeds straight to the monitor; a thread about to trigger script
 * execution (Thread A) must wait for the engine lock *before* it can take the monitor, so it
 * is never able to hold the monitor while blocking on the engine lock.
 *
 * <p>This test reproduces the lock-acquisition shape with a minimal stand-in for
 * {@code ConditionFilter2} (a plain {@code synchronized(this)} monitor around a callback that
 * mirrors the nested {@code table.moreRows()} call) and a real {@link ReentrantLock} standing
 * in for the GraalJS engine's per-sandbox lock, rather than driving the real
 * {@code AbstractConditionFilter} (whose row map needs a live Spring context for its
 * {@code XSwapper}-backed {@code XSwappableIntList}) through a full
 * {@code AssetQuerySandbox}/GraalJS/viewsheet harness -- the same trade-off
 * {@code CalcTableGraalLockOrderingTest} documents for bug #76905. Both threads run as daemon
 * threads with bounded waits, so a failed assertion here cannot hang the build even if a
 * future change reintroduces the deadlock.
 */
@Tag("core")
public class ConditionFilterGraalLockOrderingTest {
   /**
    * Reproduces the exact cycle from the confirmed jstack dump using the *pre-fix* shape
    * (nothing orders the filter's monitor against the engine lock) and asserts it reliably
    * deadlocks within a bounded time. This proves the interleaving below is a genuine
    * reproduction of the bug, not a vacuous one.
    */
   @Test
   public void unorderedAcquisitionDeadlocksAgainstScriptEngineLock() throws Exception {
      runInterleaving(false, false);
   }

   /**
    * Same interleaving, but with the fix's engine-lock-before-monitor ordering applied,
    * matching {@code PostProcessor.ConditionFilter2.moreRows()}'s current behavior: both
    * threads must complete promptly.
    */
   @Test
   public void engineLockOrderingPreventsDeadlock() throws Exception {
      runInterleaving(true, true);
   }

   private void runInterleaving(boolean guardEnabled, boolean expectPromptCompletion)
      throws Exception
   {
      ReentrantLock engineLock = new ReentrantLock();
      CountDownLatch scriptHasEngineLock = new CountDownLatch(1);
      CountDownLatch populateReachedMonitor = new CountDownLatch(1);

      // Mirrors table.moreRows(baseRow) cascading into FormulaTableLens.exec() ->
      // GraalJavaScriptEngine.exec()'s lock.lock() (GraalJavaScriptEngine.java:1196), called
      // from *inside* the filter's synchronized(this) block (AbstractConditionFilter.java:202).
      Runnable populate = () -> {
         populateReachedMonitor.countDown();
         engineLock.lock();
         engineLock.unlock();
      };

      // Stand-in for PostProcessor.ConditionFilter2: guardEnabled==false is the pre-fix shape
      // (AbstractConditionFilter.moreRows()'s bare synchronized(this)); guardEnabled==true is
      // the fix (acquire the engine lock first, exactly like ConditionFilter2.moreRows()).
      ConditionFilterStandIn filter = new ConditionFilterStandIn(populate, engineLock, guardEnabled);

      ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
         Thread t = new Thread(r);
         t.setDaemon(true);
         return t;
      });

      try {
         // Mirrors a *different*, already-executing GraalJS script
         // (GraalJavaScriptEngine.exec() holding the engine lock) whose guest JS re-enters
         // this same filter via a row-member read (TableRow.getMember ->
         // AbstractConditionFilter.getObject/getBaseRowIndex/moreRows).
         Future<?> scriptThread = pool.submit(() -> {
            engineLock.lock();

            try {
               scriptHasEngineLock.countDown();

               // Only wait for genuine monitor contention in the unguarded case: with the
               // fix applied, the populate thread blocks on the engine lock *before* ever
               // reaching the monitor, so this latch would never fire and there is nothing
               // to wait for.
               if(!guardEnabled) {
                  assertTrue(populateReachedMonitor.await(10, TimeUnit.SECONDS),
                     "populate thread never entered the filter's monitor");
               }

               filter.moreRows();
            }
            finally {
               engineLock.unlock();
            }

            return null;
         });

         // Mirrors AbstractConditionFilter.moreRows()'s row-population path
         // (PostProcessor.ConditionFilter2, reached e.g. via getBaseRowIndex()).
         Future<?> populateThread = pool.submit(() -> {
            assertTrue(scriptHasEngineLock.await(10, TimeUnit.SECONDS),
               "script thread never acquired the engine lock");
            filter.moreRows();
            return null;
         });

         if(expectPromptCompletion) {
            scriptThread.get(5, TimeUnit.SECONDS);
            populateThread.get(5, TimeUnit.SECONDS);
         }
         else {
            assertThrows(TimeoutException.class, () -> {
               scriptThread.get(3, TimeUnit.SECONDS);
               populateThread.get(3, TimeUnit.SECONDS);
            }, "expected the unordered interleaving to deadlock, but both threads completed");
         }
      }
      finally {
         pool.shutdownNow();
      }
   }

   /**
    * Mirrors {@code PostProcessor.ConditionFilter2}/{@code AbstractConditionFilter}'s
    * {@code moreRows()} lock shape: a {@code synchronized(this)} monitor guarding a callback
    * that can itself need the GraalJS engine lock. When {@code guardEnabled}, the engine lock
    * is acquired before the monitor, matching the real fix.
    */
   private static final class ConditionFilterStandIn {
      private final Runnable populate;
      private final ReentrantLock engineLock;
      private final boolean guardEnabled;

      ConditionFilterStandIn(Runnable populate, ReentrantLock engineLock, boolean guardEnabled) {
         this.populate = populate;
         this.engineLock = engineLock;
         this.guardEnabled = guardEnabled;
      }

      void moreRows() {
         if(!guardEnabled) {
            synchronized(this) {
               populate.run();
            }
            return;
         }

         engineLock.lock();

         try {
            synchronized(this) {
               populate.run();
            }
         }
         finally {
            engineLock.unlock();
         }
      }
   }
}
