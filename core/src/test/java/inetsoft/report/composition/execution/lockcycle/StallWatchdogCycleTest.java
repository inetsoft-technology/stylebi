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
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The known lock cycles of the #76966 suite, run with the lock-stall watchdog at
 * {@code noProgressMillis=2000} (bug #76967): in fail mode each cycle ends with a
 * {@link LockStallException} within the cap instead of hanging, the engine lock is free and
 * no wait stays registered afterwards. A slow but progressing pipeline is not failed.
 *
 * <p>The cycles run with the default rule of fail mode (Feature #77123), which fails a wait
 * once the watchdog confirms its cycle; {@code StallWatchdogCycleFailOnTimeoutTest} runs them
 * with {@code stall.watchdog.failOnTimeout}, which fails on the timeout alone.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("slow")
public class StallWatchdogCycleTest {
   @BeforeEach
   public void setUp() {
      StallTestSupport.resetGlobalStallState();
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 2000, 500, dumpDir,
                                              StallPolicy.DEFAULT_MAX_DUMPS, failOnTimeout()));
      // the hung threads of known cases run earlier in this JVM stay registered
      preexisting.addAll(WaitRegistry.global().getActive());
      harness = new LockCycleHarness();
   }

   /**
    * Whether the fail-mode policy of the cycles fails on the timeout alone.
    */
   protected boolean failOnTimeout() {
      return false;
   }

   @AfterEach
   public void tearDown() throws Exception {
      harness.close();
      StallPolicy.setOverride(null);
   }

   /**
    * #76960 A2 with a script join key: the holder, the joined table's condition filter over
    * its expression column, waits in the join's XSwappableTable holding the engine lock; the
    * JoinThreads wait for the lock in {@code exec}, which computes the key of every row they
    * read. Since #77273 a join computes formula inputs before its workers start, so the cycle
    * over filtered formula inputs this case used to stall on is gone; a script run per read
    * cannot be computed ahead.
    */
   @Test
   public void hashJoinExecKeyFailsInsteadOfHanging() throws Exception {
      assumeFalse(POOL, "pool off only: the JoinThreads' exec() of the join key must wait for " +
                  "the engine lock the holder holds; pooled, each exec() claims its own " +
                  "context, so the join completes and nothing stalls " +
                  "(RelPooledCompletionTest.hashJoinExecKeyCompletesPooled)");
      harness.forceHashJoin();
      Sandbox s = harness.sandbox();
      Future<TableLens> built = harness.submit(() -> {
         TableLens left = s.execTable(ROWS, Slow.WORKERS);
         TableLens right = s.execTable(ROWS, Slow.WORKERS);
         JoinTableLens join = new JoinTableLens(left, right, new int[] { 1 }, new int[] { 1 },
                                                JoinTableLens.INNER_JOIN, true);
         return harness.track(cf2(s.formula(harness.track(join)), s.box));
      });
      TableLens outer = harness.await(built, ACTIVE_CAP, "build");
      Future<List<List<Object>>> holder = harness.submit(() -> drain(outer));

      assertFailsWithStall(holder, s);
   }

   /**
    * #76960 B (R2): T1 holds the lens monitor and waits for the engine lock, T2 holds the
    * lock and is BLOCKED on the monitor. T1, the only registered wait of the cycle, fails with
    * a stall and lets go of the monitor, T2 then completes with the right rows. SortFilter and
    * RankingTableLens rethrow the stall and sort or rank again on the next read.
    *
    * <p>The gate parks T1 between the lens and its filtered base: inside the lens monitor but
    * before the inner condition filter takes the engine lock. T2 then takes the lock and
    * blocks on the monitor before T1 goes on, so the cycle forms on every run.
    *
    * <p>Not MAX_ROWS: since bug #77311 {@code MaxRowsTableLens} reads its base without its
    * monitor, so there is no cycle to stall (see
    * {@code MonitorFirstLensCycleTest.maxRowsOverFilteredFormula}).
    */
   @ParameterizedTest
   @EnumSource(value = MonitorKind.class, names = "MAX_ROWS", mode = EnumSource.Mode.EXCLUDE)
   public void monitorFirstLensFailsOneReader(MonitorKind kind) throws Exception {
      assumeFalse(POOL, MONITOR_FIRST_POOL_OFF);
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
      // T2 reads the header row first: a cell read goes through the lens monitor without the
      // engine lock (e.g. UnionTableLens.getObject), so T2 must be past it. T1 then parks
      // inside the lens monitor without the engine lock; T2 takes the lock for its next row
      // and blocks on the monitor, then T1 goes on and waits for the lock: the cycle
      CountDownLatch headerRead = new CountDownLatch(1);
      CountDownLatch t1Parked = new CountDownLatch(1);
      Started<List<List<Object>>> t2 = harness.start(() -> {
         List<List<Object>> rows = new ArrayList<>();
         assertTrue(outer.moreRows(0));
         rows.add(row(outer, 0));
         headerRead.countDown();
         assertTrue(t1Parked.await(KNOWN_CAP, TimeUnit.SECONDS), "T1 never parked");

         for(int r = 1; outer.moreRows(r); r++) {
            rows.add(row(outer, r));
         }

         return rows;
      });
      assertTrue(headerRead.await(KNOWN_CAP, TimeUnit.SECONDS), "T2 never read the header");
      Started<List<List<Object>>> t1 = harness.startGated(gate, () -> drain(lens));
      assertTrue(gate.awaitEntered(KNOWN_CAP), "T1 never read the lens's base");
      t1Parked.countDown();
      awaitBlockedBy(t2, t1, s, KNOWN_CAP);
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
    * Bug #77152: {@code alert} mode (the default before Feature #77123) never fails a stalled
    * wait (by design, see {@link StallPolicy}), so with only the pre-#77152 detection, the
    * exact same #76960 B (R2) cycle as {@link #monitorFirstLensFailsOneReader} never turns
    * health DOWN: T1 (the lock waiter) never fails and so never releases the lens monitor,
    * and T2 (the lock holder, JVM-{@code BLOCKED} on that monitor) never completes either —
    * a genuine, permanent deadlock that {@code /health/liveness} reported UP for.
    * {@link StallWatchdog} must find this wait-for cycle and report it unreleased regardless of the per-record alert/fail mode,
    * the same way a JVM-visible deadlock always does.
    *
    * <p>The cycle is reported once T1 is stalled (the 2000 ms limit) and found again on the
    * next scan. The reason must name this cycle's own threads: other cases of this JVM may
    * have left other stalls or cycles registered.
    *
    * <p>Unlike {@link #monitorFirstLensFailsOneReader}, this test never joins T1/T2's futures:
    * in {@code alert} mode neither ever completes, so their daemon threads are intentionally
    * left stuck, and the cycle stays reported by the global watchdog, for the rest of this JVM,
    * the same as an actual, still-open lock cycle would (see {@code MonitorFirstLensCycleTest}'s
    * {@code known-deadlock} cases). That is acceptable, as an alert-mode cycle cannot be
    * released at all: no test of this module asserts that the global watchdog reports nothing
    * or that no wait is registered, each only checks its own threads' waits and reasons (e.g.
    * {@code LendableReentrantLockReclaimStallTest}, {@code XSwappableTableStallTest}, and this
    * class's {@code preexisting}).
    */
   @Test
   public void monitorFirstLensAlertModeTurnsHealthDown() throws Exception {
      assumeFalse(POOL, MONITOR_FIRST_POOL_OFF);
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.ALERT, 2000, 500, dumpDir,
                                              StallPolicy.DEFAULT_MAX_DUMPS, false));
      Gate gate = harness.gate();
      Sandbox s = harness.sandbox();
      TableLens lens = harness.track(build(MonitorKind.SORT, s, gate));
      TableLens outer = harness.track(cf2(lens, s.box));
      CountDownLatch headerRead = new CountDownLatch(1);
      CountDownLatch t1Parked = new CountDownLatch(1);
      Started<List<List<Object>>> t2 = harness.start(() -> {
         List<List<Object>> rows = new ArrayList<>();
         assertTrue(outer.moreRows(0));
         rows.add(row(outer, 0));
         headerRead.countDown();
         assertTrue(t1Parked.await(KNOWN_CAP, TimeUnit.SECONDS), "T1 never parked");

         for(int r = 1; outer.moreRows(r); r++) {
            rows.add(row(outer, r));
         }

         return rows;
      });
      assertTrue(headerRead.await(KNOWN_CAP, TimeUnit.SECONDS), "T2 never read the header");
      Started<List<List<Object>>> t1 = harness.startGated(gate, () -> drain(lens));
      assertTrue(gate.awaitEntered(KNOWN_CAP), "T1 never read the lens's base");
      t1Parked.countDown();
      awaitBlockedBy(t2, t1, s, KNOWN_CAP);
      // lets T1 go on to register its wait for the engine lock: the cycle is now complete
      // and permanent, alert mode never releases either side of it
      gate.release();

      String t1Name = "\"" + t1.thread.getName() + "\"(" + t1.thread.threadId() + ")";
      String t2Name = "\"" + t2.thread.getName() + "\"(" + t2.thread.threadId() + ")";
      StallTestSupport.awaitTrue(() -> {
         StallWatchdog.global().scan();
         String reason = StallWatchdog.global().getUnreleasedStall();
         return reason != null && java.util.Arrays.stream(reason.split("; ")).anyMatch(
            part -> part.startsWith("wait-for cycle") && part.contains(t1Name) &&
               part.contains(t2Name));
      }, KNOWN_CAP, "the wait-for cycle of T1 and T2 never turned health DOWN under alert mode");
   }

   /**
    * No false positive: a worker lent the engine lock reads a base that costs 1 s a row, for
    * 10 s, longer than the 8 s limit, and the holder completes. The limit is above the up to
    * 5 s a queued SummaryFilter worker may wait for an idle on-demand pool thread in its
    * clean-up hold (ThreadPool.cleanUp) plus one row, so no pool state makes this a stall.
    *
    * <p>The summary's base needs no engine lock, so building it starts the worker (bug
    * #77223: over a base that needs the lock, the first reader would process the rows itself
    * and nothing would be lent).
    *
    * <p>Pool off only: a pooled condition filter takes no engine lock, so there is nothing to
    * lend. The pooled shape, a slow summary worker read by a condition filter, is covered in
    * both modes by RelSlowSummaryCreditTest.
    */
   @Test
   public void slowProgressingSummaryUnderLockCompletes() throws Exception {
      assumeFalse(POOL, "pool off only: the holder lends the engine lock to the summary " +
                  "worker, which pool mode never does (RelSlowSummaryCreditTest covers the " +
                  "pooled slow summary)");
      // failOnTimeout: the strict rule, a pipeline that earns no progress credit for 8 s fails
      // even without a confirmed cycle
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 8000, 500, dumpDir,
                                              StallPolicy.DEFAULT_MAX_DUMPS, true));
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
      StallTestSupport.awaitTrue(s.lock::isLent, ACTIVE_CAP,
                                 "the holder never lent the engine lock to the worker");

      assertEquals(expected, harness.await(holder, ACTIVE_CAP, "slow holder"));
      assertLocksFree(s);
   }

   private SummaryFilter summary(Sandbox s, TableLens base) {
      return harness.track(new SummaryFilter(cf2(base, s.box), new int[] { 0 },
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
      case RANKING:
         RankingTableLens ranking = new RankingTableLens(gated(s, gate));
         ranking.setRankingColumn(1);
         ranking.setRankingN(10);
         return ranking;
      default:
         throw new IllegalArgumentException(kind.name());
      }
   }

   private static List<Object> row(TableLens table, int r) {
      List<Object> row = new ArrayList<>();

      for(int c = 0; c < table.getColCount(); c++) {
         row.add(table.getObject(r, c));
      }

      return row;
   }

   /**
    * A filtered formula base under a {@link GateFilter}: the gate owner parks at its first data
    * read there, before the inner condition filter takes the engine lock.
    */
   private static TableLens gated(Sandbox s, Gate gate) {
      return new GateFilter(s.filteredFormula(new SlowTable(ROWS, Slow.NONE)), gate);
   }

   /**
    * Wait until the thread of {@code started} holds the engine lock of {@code s} and is
    * BLOCKED on a monitor that the thread of {@code owner} holds: the lens monitor, reached
    * through the outer condition filter's moreRows. T2 also blocks on that monitor briefly for
    * a header read before it takes the lock (e.g. getColCount), which is no cycle yet.
    */
   private static void awaitBlockedBy(Started<?> started, Started<?> owner, Sandbox s,
                                      long capSeconds)
      throws InterruptedException
   {
      ThreadMXBean threads = ManagementFactory.getThreadMXBean();
      StallTestSupport.awaitTrue(() -> {
         Thread thread = started.thread;
         Thread holder = owner.thread;

         if(thread == null || holder == null || !s.lock.isLocked()) {
            return false;
         }

         ThreadInfo info = threads.getThreadInfo(thread.getId(), Integer.MAX_VALUE);

         if(info == null || info.getThreadState() != Thread.State.BLOCKED ||
            info.getLockOwnerId() != holder.getId())
         {
            return false;
         }

         for(StackTraceElement frame : info.getStackTrace()) {
            if(frame.getClassName().endsWith("PostProcessor$ConditionFilter2") &&
               frame.getMethodName().equals("moreRows"))
            {
               return true;
            }
         }

         return false;
      }, capSeconds, "T2 never blocked on the lens monitor held by T1, holding the lock");
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
      SORT, MAX_ROWS, UNION_ALL, RANKING
   }

   private static final String MONITOR_FIRST_POOL_OFF =
      "pool off only: the R2 cycle needs T2 to hold the engine lock while BLOCKED on the lens " +
      "monitor; a pooled condition filter takes no engine lock, so the cycle cannot form " +
      "(RelPooledCompletionTest.monitorFirstLens*Pooled)";
   private static final int ROWS = 120;
   @TempDir
   File dumpDir;
   private LockCycleHarness harness;
   private final Set<WaitRecord> preexisting = Collections.newSetFromMap(new IdentityHashMap<>());
}
