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
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.*;
import inetsoft.report.composition.execution.lockcycle.StallWatchdogCycleTest.MonitorKind;
import inetsoft.report.filter.AbstractConditionFilter;
import inetsoft.report.filter.DefaultTableFilter;
import inetsoft.report.filter.SortFilter;
import inetsoft.report.lens.*;
import inetsoft.test.*;
import inetsoft.util.script.graal.pool.*;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pool-on counterparts (Testing #77123, reliability scenario A1) of lock-stall cases that
 * assert a pool-off cycle: StallWatchdogCycleTest's monitor-first and script-join-key cycles
 * (pool off only), FormulaLensLockStallTest.formulaScriptStallReachesTheReader and
 * GuestReaderCycleTest.pastCompletedMapReaderAfterInvalidate. The last two have their own
 * pool-on branch since #5933, run with {@code -Dlockcycle.pool=true}; the counterparts here run
 * pooled in the default build too and add the overlap evidence (no registered wait of the
 * reader, a script run on another context while the guest's claim stays open, a thread holding
 * a claimed context). Each builds the same shape on pooled contexts, with the lock-stall
 * watchdog in fail mode at 2000 ms and {@code failOnTimeout}, and asserts that every
 * thread completes with the rows of a control pipeline and that no stall or wait-for cycle is
 * reported: with pooled contexts no thread waits for another thread's engine lock, so the cycle
 * class these cases pin down cannot form. They always run pooled, whatever
 * {@code -Dlockcycle.pool} says.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class RelPooledCompletionTest {
   @BeforeEach
   public void setUp() {
      StallTestSupport.resetGlobalStallState();
      // failOnTimeout: the strict rule, any wait without progress for 2 s fails the case, not
      // only a confirmed cycle (the default rule), so a false stall cannot pass as completion
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 2000, 500, dumpDir,
                                              StallPolicy.DEFAULT_MAX_DUMPS, true));
      // pooled whatever -Dlockcycle.pool says, so the default build runs these cases
      harness = new LockCycleHarness(true);
   }

   @AfterEach
   public void tearDown() throws Exception {
      harness.close();

      StallTestSupport.clearOverride();
   }

   /**
    * The monitor-first shape of StallWatchdogCycleTest.monitorFirstLensFailsOneReader over the
    * product lenses, pooled: T1 parks inside the lens monitor, T2 blocks on that monitor
    * inside the outer condition filter. Pool off T2 held the engine lock there and T1 then
    * waited for it (the #76960 B R2 cycle; since bug #77874 these lenses take the lock first
    * pool off, see MonitorFirstLensCycleTest); pooled, T1 needs no engine lock, so once
    * released both complete with every row.
    *
    * <p>Not MAX_ROWS: since bug #77311 {@code MaxRowsTableLens} reads its base without its
    * monitor, so T2 never blocks on it; the pinned case excludes it too. Not UNION_ALL for the
    * same reason: since bug #77874 the non-distinct {@code UnionTableLens} reads its bases
    * without its monitor. SORT and RANKING still hold their monitor here, pooled: no engine
    * lock is found in the chain, so they read as before.
    */
   @ParameterizedTest
   @EnumSource(value = MonitorKind.class, names = { "MAX_ROWS", "UNION_ALL" },
               mode = EnumSource.Mode.EXCLUDE)
   public void monitorFirstLensCompletesPooled(MonitorKind kind) {
      assertTimeoutPreemptively(CAP, () -> monitorFirst(kind, (t1, t2) -> {}));
   }

   /**
    * StallWatchdogCycleTest.monitorFirstLensAlertModeTurnsHealthDown, pooled, in alert mode
    * (the shipped mode before Feature #77123 made fail the default). Pool off the cycle forms
    * once the gate lets T1 go on to wait for the engine lock T2 holds, and alert mode never
    * releases either side of it, so neither thread ever completes. The evidence here is that both complete in alert mode, with every row, after
    * T2 was seen BLOCKED on the lens monitor T1 holds; a scan afterwards reports no stall or
    * cycle of them and neither has a registered wait.
    */
   @Test
   public void monitorFirstLensAlertModeCompletesPooled() {
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.ALERT, 2000, 500, dumpDir,
                                              StallPolicy.DEFAULT_MAX_DUMPS, false));
      assertTimeoutPreemptively(CAP, () -> {
         Started<?>[] threads = monitorFirst(MonitorKind.SORT, (t1, t2) -> {});
         assertNoCycleOf(threads[0].thread, threads[1].thread);
      });
   }

   /**
    * StallWatchdogCycleTest.hashJoinExecKeyFailsInsteadOfHanging, pooled: the holder drains a
    * condition filter over a formula lens over a hash join whose join key is computed by
    * {@code exec()} per cell the JoinThreads read. Pool off the JoinThreads wait for the engine
    * lock the holder holds (#76960 A2 with a script join key); pooled each exec() claims its own
    * context, and the holder completes with the control's rows.
    *
    * <p>The overlap is forced, not left to timing: a JoinThread is held (running) at a row of
    * its input until the holder is seen in its registered JoinTable.moreRows wait for the
    * joined rows, and only then let go.
    */
   @Test
   public void hashJoinExecKeyCompletesPooled() {
      assertTimeoutPreemptively(CAP, () -> {
         harness.forceHashJoin();
         Sandbox control = harness.control();
         List<List<Object>> expected = harness.await(
            harness.submit(() -> drain(join(control, control.execTable(ROWS, Slow.NONE)))),
            ACTIVE_CAP, "control");

         Sandbox s = harness.sandbox();
         HoldFilter held = new HoldFilter(s.execTable(ROWS, Slow.NONE), ROWS / 2);
         TableLens outer = harness.await(harness.submit(() -> join(s, held)), ACTIVE_CAP,
                                         "build");
         Started<List<List<Object>>> holder = harness.start(() -> drain(outer));
         assertTrue(held.reached.await(KNOWN_CAP, TimeUnit.SECONDS),
                    "no JoinThread read its input");
         StallTestSupport.awaitTrue(() -> holder.thread != null &&
            WaitRegistry.global().getActive().stream().anyMatch(
               r -> r.getThread() == holder.thread && "JoinTable.moreRows".equals(r.getWhat())),
            KNOWN_CAP, "the holder never waited in the join for the held JoinThread");
         assertFalse(holder.future.isDone(), "the holder finished while a JoinThread was held");
         held.release();

         assertEquals(expected, harness.await(holder.future, ACTIVE_CAP, "holder"));
         assertNoWaitOf(holder.thread);
      });
   }

   /**
    * GuestReaderCycleTest.pastCompletedMapReaderAfterInvalidate, pooled: a guest holds a
    * claimed context. The end of a completed filtered formula table is probed, then the base
    * grows and the formula lens and filter are invalidated. Pool off the reader of the reset
    * map waits for the engine lock the guest holds; pooled it completes with the new rows while
    * the guest still holds its context, and the table then drains to the grown size.
    *
    * <p>The guest's claim is eager, so it keeps one context checked out the whole time: the
    * scripts the reader runs for the new rows (the env's exec count rises) run on another
    * context, and the guest still finds its own claim open once it is let go.
    */
   @Test
   public void pastCompletedMapReaderAfterInvalidateCompletesPooled() {
      assertTimeoutPreemptively(CAP, () -> {
         Sandbox s = harness.sandbox();
         ResizableTable base = new ResizableTable(2 * INV_ROWS, INV_ROWS);
         FormulaTableLens formula = s.formula(base, "f", "field['value'] + 1");
         TableLens cf = harness.track(cf2(harness.track(formula), s.box));
         int end = harness.await(harness.submit(() -> drain(cf).size()), ACTIVE_CAP, "drain");
         assertEquals(INV_ROWS + 1, end);

         CountDownLatch held = new CountDownLatch(1);
         CountDownLatch go = new CountDownLatch(1);
         Future<Object> guest = harness.submit(() -> s.asGuest(() -> {
            held.countDown();
            assertTrue(go.await(3 * KNOWN_CAP, TimeUnit.SECONDS), "the guest was never let go");
            assertEquals(1, SlotClaim.openClaims(), "the guest's claim was not open to the end");
            return null;
         }));
         assertTrue(held.await(ACTIVE_CAP, TimeUnit.SECONDS), "the guest never took its context");
         PoolMetrics metrics = ((WorksheetScriptEnv) s.env).getMetrics();

         try {
            assertFalse(harness.await(harness.submit(() -> cf.moreRows(end)), ACTIVE_CAP,
                                      "probing past the completed map while the guest holds"));
            base.setVisibleRows(2 * INV_ROWS);
            formula.invalidate();
            ((AbstractConditionFilter) cf).invalidate();
            long execs = metrics.getExecs();
            Started<Boolean> reader = harness.start(() -> cf.moreRows(end));
            assertTrue(harness.await(reader.future, ACTIVE_CAP, "the reader of the reset map"),
                       "the reader of the reset map must find the new rows");
            assertTrue(metrics.getExecs() > execs,
                       "the reader ran no script for the new rows while the guest held its context");
            assertNoWaitOf(reader.thread);
         }
         finally {
            go.countDown();
         }

         harness.await(guest, ACTIVE_CAP, "the guest");
         assertEquals(2 * INV_ROWS + 1, harness.await(harness.submit(() -> drain(cf).size()),
                                                      ACTIVE_CAP, "draining the reset map"));
      });
   }

   /**
    * FormulaLensLockStallTest.formulaScriptStallReachesTheReader, pooled: while another thread
    * holds the primary context's engine lock, or holds a claimed context as a thread inside a
    * script does, a reader of a formula lens on the same env completes with every row instead
    * of stalling on that lock.
    */
   @ParameterizedTest
   @EnumSource(Holder.class)
   public void formulaLensReaderCompletesWhileAScriptHolds(Holder hold) {
      assertTimeoutPreemptively(CAP, () -> {
         Sandbox control = harness.control();
         List<List<Object>> expected = harness.await(
            harness.submit(() -> drain(control.formula(new DefaultTableLens(rows(20))))),
            ACTIVE_CAP, "control");
         Sandbox s = harness.sandbox();
         TableLens lens = harness.track(s.formula(new DefaultTableLens(rows(20))));
         CountDownLatch held = new CountDownLatch(1);
         CountDownLatch release = new CountDownLatch(1);
         Future<Boolean> holder = harness.submit(() -> {
            if(hold == Holder.ENGINE_LOCK) {
               s.lock.lock();
            }

            AutoCloseable claim = hold == Holder.SCRIPT_CLAIM ? s.holdScript() : null;

            try {
               held.countDown();
               return release.await(3 * KNOWN_CAP, TimeUnit.SECONDS);
            }
            finally {
               if(claim != null) {
                  claim.close();
               }
               else {
                  s.lock.unlock();
               }
            }
         });
         assertTrue(held.await(10, TimeUnit.SECONDS), "the holder never took " + hold);

         try {
            Started<List<List<Object>>> reader = harness.start(() -> drain(lens));
            assertEquals(expected, harness.await(reader.future, ACTIVE_CAP, "reader"),
                         "the reader completes while the holder holds " + hold);
            assertNoWaitOf(reader.thread);
         }
         finally {
            release.countDown();
         }

         assertTrue(harness.await(holder, ACTIVE_CAP, "holder"));
      });
   }

   /**
    * Run the monitor-first shape of StallWatchdogCycleTest on a pooled sandbox and check both
    * threads complete with the control's rows.
    *
    * @return T1 and T2.
    */
   private Started<?>[] monitorFirst(MonitorKind kind, WhileBlocked whileBlocked)
      throws Exception
   {
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
      // the pool-off cycle's first half: T2 blocked on the lens monitor T1 holds, inside the
      // outer condition filter (which holds no engine lock pooled)
      awaitBlockedOn(t2, t1, KNOWN_CAP);

      try {
         whileBlocked.run(t1, t2);
      }
      finally {
         gate.release();
      }

      assertEquals(expectedLens, harness.await(t1.future, ACTIVE_CAP, "T1, the monitor holder"),
                   "T1 completes with every row");
      assertEquals(expectedOuter, harness.await(t2.future, ACTIVE_CAP, "T2, the filter reader"),
                   "T2 completes with every row");
      assertNoWaitOf(t1.thread);
      assertNoWaitOf(t2.thread);
      return new Started<?>[] { t1, t2 };
   }

   /**
    * The shape of the pinned case, with {@code left} as the join's left input: a condition
    * filter over a formula lens over a hash join keyed by a script-computed column.
    */
   private TableLens join(Sandbox s, TableLens left) {
      TableLens right = s.execTable(ROWS, Slow.NONE);
      JoinTableLens join = new JoinTableLens(left, right, new int[] { 1 }, new int[] { 1 },
                                             JoinTableLens.INNER_JOIN, true);
      return harness.track(cf2(s.formula(harness.track(join)), s.box));
   }

   private static TableLens build(MonitorKind kind, Sandbox s, Gate gate) {
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

   private static TableLens gated(Sandbox s, Gate gate) {
      return new GateFilter(s.filteredFormula(new SlowTable(MONITOR_ROWS, Slow.NONE)), gate);
   }

   private static List<Object> row(TableLens table, int r) {
      List<Object> row = new ArrayList<>();

      for(int c = 0; c < table.getColCount(); c++) {
         row.add(table.getObject(r, c));
      }

      return row;
   }

   private static Object[][] rows(int n) {
      Object[][] data = new Object[n + 1][];
      data[0] = new Object[] { "key", "value" };

      for(int i = 1; i <= n; i++) {
         data[i] = new Object[] { "k" + (i % 3), i };
      }

      return data;
   }

   /**
    * Wait until the thread of {@code started} is BLOCKED on a monitor the thread of
    * {@code owner} holds, inside a condition filter's moreRows.
    */
   private static void awaitBlockedOn(Started<?> started, Started<?> owner, long capSeconds)
      throws InterruptedException
   {
      ThreadMXBean threads = ManagementFactory.getThreadMXBean();
      StallTestSupport.awaitTrue(() -> {
         Thread thread = started.thread;
         Thread holder = owner.thread;

         if(thread == null || holder == null) {
            return false;
         }

         ThreadInfo info = threads.getThreadInfo(thread.threadId(), Integer.MAX_VALUE);

         if(info == null || info.getThreadState() != Thread.State.BLOCKED ||
            info.getLockOwnerId() != holder.threadId())
         {
            return false;
         }

         return Arrays.stream(info.getStackTrace()).anyMatch(
            frame -> frame.getClassName().endsWith("PostProcessor$ConditionFilter2") &&
               frame.getMethodName().startsWith("moreRows"));
      }, capSeconds, "T2 never blocked on the lens monitor held by T1");
   }

   /**
    * Scan the watchdog: no unreleased stall or wait-for cycle names either thread, and
    * neither thread has a registered wait.
    */
   private static void assertNoCycleOf(Thread t1, Thread t2) {
      String t1Name = "\"" + t1.getName() + "\"(" + t1.threadId() + ")";
      String t2Name = "\"" + t2.getName() + "\"(" + t2.threadId() + ")";
      StallWatchdog.global().scan();
      String reason = StallWatchdog.global().getUnreleasedStall();
      assertFalse(reason != null && Arrays.stream(reason.split("; ")).anyMatch(
                     part -> part.contains(t1Name) || part.contains(t2Name)),
                  "a stall or cycle of T1/T2 was reported: " + reason);
      assertTrue(WaitRegistry.global().getActive().stream()
                    .noneMatch(r -> r.getThread() == t1 || r.getThread() == t2),
                 "T1 or T2 has a registered wait");
   }

   /**
    * No wait of {@code thread} stays registered, e.g. a stalled one.
    */
   private static void assertNoWaitOf(Thread thread) throws InterruptedException {
      StallTestSupport.awaitTrue(() -> WaitRegistry.global().getActive().stream()
                                    .noneMatch(r -> r.getThread() == thread), 15,
                                 "a wait of " + thread.getName() + " is still registered");
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

   /**
    * Passes its base through; a lens worker (a JoinThread) reads row {@code holdAt} only after
    * {@link #release()}, spinning meanwhile: RUNNABLE, so the join's reader is credited while
    * it waits, and a hold longer than the stall limit is no stall.
    */
   private static final class HoldFilter extends DefaultTableFilter {
      HoldFilter(TableLens table, int holdAt) {
         super(table);
         this.holdAt = holdAt;
      }

      @Override
      public Object getObject(int r, int c) {
         if(r == holdAt && !isHarnessThread() && !released) {
            reached.countDown();
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3 * KNOWN_CAP);

            while(!released && System.nanoTime() - end < 0) {
               Thread.onSpinWait();
            }
         }

         return super.getObject(r, c);
      }

      void release() {
         released = true;
      }

      private final int holdAt;
      final CountDownLatch reached = new CountDownLatch(1);
      private volatile boolean released;
   }

   /**
    * A table showing only its first {@code visible} rows until told to show more, as
    * GuestReaderCycleTest's.
    */
   private static final class ResizableTable extends SlowTable {
      ResizableTable(int rows, int visible) {
         super(rows, Slow.NONE);
         this.visible = visible;
      }

      void setVisibleRows(int rows) {
         visible = rows;
      }

      @Override
      public boolean moreRows(int row) {
         return row <= visible;
      }

      @Override
      public int getRowCount() {
         return visible + 1;
      }

      private volatile int visible;
   }

   /**
    * What a monitor-first case does while T2 is blocked on the lens monitor T1 holds.
    */
   @FunctionalInterface
   private interface WhileBlocked {
      void run(Started<?> t1, Started<?> t2) throws Exception;
   }

   public enum Holder {
      /** The primary context's engine lock, as the pool-off case holds it. */
      ENGINE_LOCK,
      /** A claimed context, as a thread inside a pooled script holds it. */
      SCRIPT_CLAIM
   }

   private static final Duration CAP = Duration.ofSeconds(120);
   private static final int ROWS = 120;
   private static final int MONITOR_ROWS = 120;
   private static final int INV_ROWS = 300;
   @TempDir
   File dumpDir;
   private LockCycleHarness harness;
}
