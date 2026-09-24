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
import inetsoft.report.filter.DefaultTableFilter;
import inetsoft.report.filter.SortFilter;
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
    * lock and is BLOCKED on the monitor. T1, the only registered wait of the cycle, fails with
    * a stall and lets go of the monitor, T2 then completes with the right rows. SortFilter
    * rethrows the stall and sorts again on the next read; RANKING is left out, because
    * RankingTableLens.validate still logs and swallows any exception.
    *
    * <p>The gate parks T1 between the lens and its filtered base: inside the lens monitor but
    * before the inner condition filter takes the engine lock. T2 then takes the lock and
    * blocks on the monitor before T1 goes on, so the cycle forms on every run.
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
      // T1 parks inside the lens monitor without the engine lock; T2 takes the lock and parks
      // on the monitor (BLOCKED), then T1 goes on and waits for the lock: the cycle
      Started<List<List<Object>>> t1 = harness.startGated(gate, () -> drain(lens));
      assertTrue(gate.awaitEntered(KNOWN_CAP), "T1 never read the lens's base");
      Started<List<List<Object>>> t2 = harness.start(() -> drain(outer));
      awaitState(t2, Thread.State.BLOCKED, KNOWN_CAP);
      gate.release();

      Object o1 = outcome(t1.future);
      Object o2 = outcome(t2.future);
      assertTrue(o1 instanceof Throwable, "T1, the lock waiter, must fail with a stall: " + o1);
      StallTestSupport.stallIn((Throwable) o1);
      assertEquals(expectedOuter, o2, "T2, the lock holder, completes with every row");
      assertEquals(expectedLens, harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP,
                                               "lens after the stall"),
                   "the lens kept no partial state after the stall");
      assertLocksFree(s);
   }

   /**
    * No false positive: a worker lent the engine lock reads a base that costs 1 s a row, for
    * 10 s, longer than the 8 s limit, and the holder completes. The limit is above the up to
    * 5 s a queued SummaryFilter worker may wait for an idle on-demand pool thread in its
    * clean-up hold (ThreadPool.cleanUp) plus one row, so no pool state makes this a stall.
    */
   @Test
   public void slowProgressingSummaryUnderLockCompletes() throws Exception {
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 8000, 500, dumpDir));
      Sandbox control = harness.control();
      List<List<Object>> expected = harness.await(harness.submit(
         () -> drain(cf2(summary(control, new StallTestSupport.SlowTable(10, 0)), null))),
         ACTIVE_CAP, "control pipeline");

      Sandbox s = harness.sandbox();
      SummaryFilter summary = summary(s, new StallTestSupport.SlowTable(10, 1000));
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
      case SORT:
         return new SortFilter(gated(s, gate), new int[] { 1 });
      case MAX_ROWS:
         return new MaxRowsTableLens(gated(s, gate), 100000);
      case UNION_ALL:
         UnionTableLens union = new UnionTableLens(gated(s, gate), gated(s, gate));
         union.setDistinct(false);
         return union;
      default:
         throw new IllegalArgumentException(kind.name());
      }
   }

   /**
    * A filtered formula base under a {@link GateFilter}: the gate owner parks at its first data
    * read there, before the inner condition filter takes the engine lock.
    */
   private static TableLens gated(Sandbox s, Gate gate) {
      return new GateFilter(s.filteredFormula(new SlowTable(ROWS, Slow.NONE)), gate);
   }

   /**
    * Wait until the thread of {@code started} is in {@code state}.
    */
   private static void awaitState(Started<?> started, Thread.State state, long capSeconds)
      throws InterruptedException
   {
      StallTestSupport.awaitTrue(
         () -> started.thread != null && started.thread.getState() == state, capSeconds,
         "the thread never reached " + state);
   }

   /**
    * Passes its base through; the owner of the gate parks at its first data row read.
    */
   private static final class GateFilter extends DefaultTableFilter {
      GateFilter(TableLens table, Gate gate) {
         super(table);
         this.gate = gate;
      }

      @Override
      public boolean moreRows(int row) {
         if(row >= 1) {
            gate.onRead();
         }

         return super.moreRows(row);
      }

      private final Gate gate;
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

   public enum MonitorKind {
      SORT, MAX_ROWS, UNION_ALL
   }

   private static final int ROWS = 120;
   @TempDir
   File dumpDir;
   private LockCycleHarness harness;
   private final Set<WaitRecord> preexisting = Collections.newSetFromMap(new IdentityHashMap<>());
}
