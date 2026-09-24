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
package inetsoft.report.composition.execution.lockcycle;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Sandbox;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Slow;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.SlowTable;
import inetsoft.report.filter.SumFormula;
import inetsoft.report.filter.SummaryFilter;
import inetsoft.report.lens.*;
import inetsoft.test.*;
import inetsoft.util.stall.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The known lock cycles of the #76966 suite, run with the lock-stall watchdog at
 * {@code noProgressMillis=2000} (bug #76967): in fail mode each cycle ends with a
 * {@link LockStallException} within the cap instead of hanging, the engine lock is free and
 * no wait stays registered afterwards. A slow but progressing pipeline is not failed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class StallWatchdogCycleTest {
   @BeforeEach
   public void setUp() {
      StallTestSupport.resetGlobalStallState();
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 2000, 500, dumpDir));
      // the hung threads of known cases run earlier in this JVM stay registered
      preexisting.addAll(WaitRegistry.global().getActive());
      harness = new LockCycleHarness();
   }

   @AfterEach
   public void tearDown() throws Exception {
      harness.close();
      StallPolicy.setOverride(null);
   }

   /**
    * #76960 A1: the holder waits in CrossJoinTableLens.moreRows holding the engine lock, the
    * WaitingThreads wait for the lock in their input condition filters.
    */
   @Test
   public void crossJoinFirstTouchFailsInsteadOfHanging() throws Exception {
      Sandbox s = harness.sandbox();
      TableLens left = s.filteredFormula(new SlowTable(ROWS, Slow.WORKERS));
      TableLens right = s.filteredFormula(new SlowTable(ROWS, Slow.WORKERS));
      TableLens outer = harness.track(cf2(harness.track(new CrossJoinTableLens(left, right)), s.box));
      Future<List<List<Object>>> holder = harness.submit(() -> {
         assertTrue(outer.moreRows(1));
         return drain(outer);
      });

      assertFailsWithStall(holder, s);
   }

   /**
    * #76960 A2: the holder waits in the join's XSwappableTable holding the engine lock, the
    * JoinThreads wait for the lock in their input condition filters.
    */
   @Test
   public void hashJoinFilteredInputsFailsInsteadOfHanging() throws Exception {
      harness.forceHashJoin();
      Sandbox s = harness.sandbox();
      Future<TableLens> built = harness.submit(() -> {
         TableLens left = s.filteredFormula(new SlowTable(ROWS, Slow.WORKERS));
         TableLens right = s.filteredFormula(new SlowTable(ROWS, Slow.WORKERS));
         JoinTableLens join = new JoinTableLens(left, right, new int[] { 1 }, new int[] { 1 },
                                                JoinTableLens.INNER_JOIN, true);
         return harness.track(cf2(harness.track(join), s.box));
      });
      TableLens outer = harness.await(built, ACTIVE_CAP, "build");
      Future<List<List<Object>>> holder = harness.submit(() -> drain(outer));

      assertFailsWithStall(holder, s);
   }

   /**
    * #76960 B (R2): T1 holds the lens monitor and waits for the engine lock, T2 holds the
    * lock and is BLOCKED on the monitor. T1 fails with a stall and lets go of the monitor,
    * T2 then completes with the right rows. SORT and RANKING are left out: SortFilter and
    * RankingTableLens swallow exceptions (phase-1 limitation, see the plan).
    */
   @ParameterizedTest
   @EnumSource(MonitorKind.class)
   public void monitorFirstLensFailsOneReader(MonitorKind kind) throws Exception {
      Gate gate = harness.gate();
      Sandbox control = harness.control();
      TableLens controlLens = harness.track(build(kind, control, gate));
      List<List<Object>> expectedLens =
         harness.await(harness.submit(() -> drain(controlLens)), ACTIVE_CAP, "control lens");
      List<List<Object>> expectedOuter = harness.await(
         harness.submit(() -> drain(cf2(controlLens, null))), ACTIVE_CAP, "control filter");

      Sandbox s = harness.sandbox();
      TableLens lens = harness.track(build(kind, s, gate));
      TableLens outer = harness.track(cf2(lens, s.box));
      // T1 parks at its first base read inside the lens monitor, T2 starts and parks on the
      // cycle, then T1 goes on: the cycle forms on every run, not only when T1 is slow enough
      Started<List<List<Object>>> t1 = harness.startGated(gate, () -> drain(lens));
      assertTrue(gate.awaitEntered(KNOWN_CAP), "T1 never read the lens's base");
      Started<List<List<Object>>> t2 = harness.start(() -> drain(outer));
      releaseAfter(gate, t2, KNOWN_CAP);

      Object o1 = outcome(t1.future);
      Object o2 = outcome(t2.future);
      assertTrue(o1 instanceof Throwable || o2 instanceof Throwable,
                 "the cycle must end with a stall of one reader");
      checkOutcome(o1, expectedLens);
      checkOutcome(o2, expectedOuter);
      assertLocksFree(s);
   }

   /**
    * No false positive: a worker lent the engine lock reads a base that costs 300 ms a row,
    * for longer than the 1 s limit, and the holder completes.
    */
   @Test
   public void slowProgressingSummaryUnderLockCompletes() throws Exception {
      // the control pipeline runs with a long limit: its SummaryFilter worker may sit in the
      // on-demand pool's queue for up to 5 s while an idle pool thread is in its clean-up
      // hold (ThreadPool.cleanUp). It also wakes that thread up, so the worker of the slow
      // pipeline starts at once and the 1 s limit measures the slow pipeline only
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 60000, 500, dumpDir));
      Sandbox control = harness.control();
      List<List<Object>> expected = harness.await(harness.submit(
         () -> drain(cf2(summary(control, new StallTestSupport.SlowTable(8, 0)), null))),
         ACTIVE_CAP, "control pipeline");
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 1000, 500, dumpDir));

      Sandbox s = harness.sandbox();
      SummaryFilter summary = summary(s, new StallTestSupport.SlowTable(8, 300));
      TableLens outer = harness.track(cf2(summary, s.box));
      // start the worker unlocked (query build time), then drain under the lock
      harness.await(harness.submit(() -> summary.getRowCount()), ACTIVE_CAP, "getRowCount");
      Future<List<List<Object>>> holder = harness.submit(() -> drain(outer));

      assertEquals(expected, harness.await(holder, ACTIVE_CAP, "slow holder"));
      assertLocksFree(s);
   }

   private SummaryFilter summary(Sandbox s, TableLens base) {
      return harness.track(new SummaryFilter(s.filteredFormula(base), new int[] { 0 },
                                             new int[] { 1 }, new SumFormula(), null));
   }

   private TableLens build(MonitorKind kind, Sandbox s, Gate gate) {
      switch(kind) {
      case MAX_ROWS:
         return new MaxRowsTableLens(
            s.filteredFormula(new SlowTable(MONITOR_ROWS, Slow.EVERYWHERE, gate)), 100000);
      case UNION_ALL:
         UnionTableLens union = new UnionTableLens(
            s.filteredFormula(new SlowTable(MONITOR_ROWS, Slow.EVERYWHERE, gate)),
            s.filteredFormula(new SlowTable(MONITOR_ROWS, Slow.EVERYWHERE, gate)));
         union.setDistinct(false);
         return union;
      default:
         throw new IllegalArgumentException(kind.name());
      }
   }

   private void assertFailsWithStall(Future<?> holder, Sandbox s) throws Exception {
      long start = System.nanoTime();
      Exception failure = assertThrows(Exception.class,
                                       () -> harness.await(holder, KNOWN_CAP, "holder"));
      long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

      StallTestSupport.stallIn(failure);
      assertTrue(elapsed < KNOWN_CAP * 1000, "stall took " + elapsed + " ms");
      assertLocksFree(s);
   }

   private void assertLocksFree(Sandbox s) throws InterruptedException {
      StallTestSupport.awaitTrue(() -> !s.lock.isLocked() && !s.lock.isLent(), 15,
                                 "the engine lock is still held or lent after the stall");
      StallTestSupport.awaitTrue(() -> preexisting.containsAll(WaitRegistry.global().getActive()),
                                 15, "a wait is still registered after the stall");
   }

   private static Object outcome(Future<?> future) throws Exception {
      try {
         return future.get(KNOWN_CAP, TimeUnit.SECONDS);
      }
      catch(ExecutionException ex) {
         return ex.getCause();
      }
   }

   private static void checkOutcome(Object outcome, List<List<Object>> expected) {
      if(outcome instanceof Throwable) {
         StallTestSupport.stallIn((Throwable) outcome);
      }
      else {
         assertEquals(expected, outcome, "a reader that completes must see every row");
      }
   }

   public enum MonitorKind {
      MAX_ROWS, UNION_ALL
   }

   private static final int ROWS = 120;
   // as in MonitorFirstLensCycleTest: with fewer rows the union reader may not need the
   // engine lock again after T2 took it, and no cycle forms
   private static final int MONITOR_ROWS = 300;
   @TempDir
   File dumpDir;
   private LockCycleHarness harness;
   private final Set<WaitRecord> preexisting = Collections.newSetFromMap(new IdentityHashMap<>());
}
