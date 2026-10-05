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
import inetsoft.report.filter.SumFormula;
import inetsoft.report.filter.SummaryFilter;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.internal.table.XTableLens;
import inetsoft.test.*;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.util.stall.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Candidate finding F2 of the context-pool reliability run (Testing #77123):
 * {@code StallWatchdogCycleTest.slowProgressingSummaryUnderLockCompletes} failed with a
 * LockStallException with {@code -Dlockcycle.pool=true}. That test's base,
 * {@code StallTestSupport.SlowTable}, pays its cost once per moreRows call that reaches a new
 * highest row, not once per row: pool off the formula lens loads its base with one look-ahead
 * call before its locks ({@code FormulaTableLens.lockForRow}), so the base cost 2 s, not 10 s;
 * the pooled batch read it row by row, 11 s inside one base read of the summary worker, asleep.
 * These cases give a base a real per-row cost, 1 s a row for 10 rows, over the 8 s limit, with
 * the pool on or off (run each with and without {@code -Dlockcycle.pool}). Each completes in
 * both modes:
 *
 * <ul>
 *   <li>spent asleep in a wait that nothing registers: the summary worker reads its base one
 *   row per call, so its progress counter moves every second;</li>
 *   <li>spent where the watchdog sees it, rows arriving in an {@link XSwappableTable} (the
 *   shape of a slow query) or a running thread.</li>
 * </ul>
 *
 * <p>One base read that sleeps longer than the limit, in no registered wait, stalls in both
 * modes: a sleeping, unregistered worker earns no blocker credit (merge brief W2, not a pool
 * defect).
 *
 * <p>The summary's base is a condition filter that needs no engine lock, as in
 * {@code slowProgressingSummaryUnderLockCompletes}: since bug #77223 a summary over a base that
 * needs the lock starts no worker pool off (its first reader computes it inline under the
 * lock), so there would be no worker to credit or to stall.
 *
 * <p>The original F2 shape, a summary over a filtered formula lens, is characterised on its
 * own: pooled it still starts a worker, whose formula lens pre-loads the rest of its base in
 * one read (#5857), so it false-stalls with the pool on only
 * ({@code knownPoolOnW2FormulaBaseReadStalls}); pool off it computes inline and completes
 * ({@code f2FormulaBaseComputesInlinePoolOff}). Those run in the mode they name, whatever
 * {@code -Dlockcycle.pool} says.
 *
 * <p>Every case pins its stall rule. The completion cases and the two W2 characterisations
 * run in fail mode with {@code stall.watchdog.failOnTimeout=true}, the strict opt-in rule that
 * fails a wait on the timeout alone: completion there proves the credit, and the W2 false
 * stalls exist only there. Under the shipped fail rule (Feature #77123: fail only a confirmed
 * wait-for cycle or JVM deadlock) the same W2 reads are reported as a stall, with a thread
 * dump, and complete ({@code w2SleepingUnregisteredReadOnlyAlertsUnderTheDefaultRule},
 * {@code poolOnW2FormulaBaseReadOnlyAlertsUnderTheDefaultRule}).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("slow")
public class RelSlowSummaryCreditTest {
   @BeforeEach
   public void setUp() {
      StallTestSupport.resetGlobalStallState();
      harness = new LockCycleHarness();
   }

   @AfterEach
   public void tearDown() throws Exception {
      harness.close();
      StallPolicy.setOverride(null);
   }

   /**
    * Characterises the known limitation W2 of the merge brief (a slow producer behind an
    * unregistered wait) under the opt-in {@code failOnTimeout} rule, it is not a behaviour to
    * keep: a fix of W2 should turn this into a completion test. The worker's first base read
    * sleeps 18 s, outside any registered wait, far over the 8 s limit, so the stall always
    * fires first (the holder fails at about 8 s and does not wait for the worker): failed as a
    * stall with the pool on or off, because the watchdog cannot tell this worker from a hung
    * one. The holder starts once the worker is inside the base: a
    * holder that finds no worker running computes the summary itself, asleep in no wait at
    * all, and completes.
    */
   @Test
   public void knownW2SleepingUnregisteredReadStallsInBothModes() throws Exception {
      policy(8000, true);
      Sandbox s = harness.sandbox();
      AtomicInteger paid = new AtomicInteger();
      PerRowTable base = new PerRowTable(ROWS, () -> sleep(paid.getAndIncrement() == 0 ? W2_READ_MILLIS : 0));
      SummaryFilter summary = summary(s, base);
      TableLens outer = harness.track(cf2(summary, s.box));
      harness.await(harness.submit(() -> summary.getRowCount()), ACTIVE_CAP, "getRowCount");
      assertTrue(base.paying.await(ACTIVE_CAP, TimeUnit.SECONDS), "the worker never read the base");
      assertTrue(base.firstByWorker, "the base was read by a harness thread, not the worker");

      Throwable failure =
         StallTestSupport.failureOf(harness.submit(() -> drain(outer)), 2 * ACTIVE_CAP);
      StallTestSupport.stallIn(failure);
   }

   /**
    * A per-row cost spent asleep, outside any registered wait, 1 s a row for 10 s, over the
    * 8 s limit: the summary worker gets one row back per base read, so its progress moves every
    * second and the holder completes with the pool on or off. The holder starts once the
    * worker is inside the base, so it waits for the worker instead of computing the summary.
    */
   @Test
   public void sleepingPerRowBaseCompletesInBothModes() throws Exception {
      policy(8000, true);
      List<List<Object>> expected = expected();
      Sandbox s = harness.sandbox();
      PerRowTable base = new PerRowTable(ROWS, () -> sleep(1000));
      SummaryFilter summary = summary(s, base);
      TableLens outer = harness.track(cf2(summary, s.box));
      harness.await(harness.submit(() -> summary.getRowCount()), ACTIVE_CAP, "getRowCount");
      assertTrue(base.paying.await(ACTIVE_CAP, TimeUnit.SECONDS), "the worker never read the base");
      assertTrue(base.firstByWorker, "the base was read by a harness thread, not the worker");

      assertEquals(expected, harness.await(harness.submit(() -> drain(outer)), 2 * ACTIVE_CAP,
                                           "sleeping holder"));
   }

   /**
    * The original F2 shape on current main, pooled: a summary over a filtered formula lens
    * over a base that sleeps {@link #F2_ROW_MILLIS} a row in no registered wait. Pooled a
    * SummaryFilter over a formula lens still starts a worker (ChainScriptLock has no lock in
    * pool mode), and the formula lens pre-loads its base in one read before it computes a
    * batch, so the worker's progress does not move for all of those rows and the holder
    * fails as a stall in fail mode with {@code failOnTimeout}. Characterises a pool-specific
    * case of the known W2 limitation, reachable only with the opt-in
    * {@code stall.watchdog.failOnTimeout=true} (the shipped fail rule and alert mode only
    * report it, see {@link #poolOnW2FormulaBaseReadOnlyAlertsUnderTheDefaultRule}): pool off
    * the same shape starts no worker since #77223 and completes, see
    * {@link #f2FormulaBaseComputesInlinePoolOff}. A W2 fix should turn this into a completion
    * test.
    */
   @Test
   public void knownPoolOnW2FormulaBaseReadStalls() throws Exception {
      harness.close();
      harness = new LockCycleHarness(true);
      policy(8000, true);
      Sandbox s = harness.sandbox();
      PerRowTable base = new PerRowTable(ROWS, () -> sleep(F2_ROW_MILLIS));
      SummaryFilter summary = f2Summary(s, base);
      TableLens outer = harness.track(cf2(summary, s.box));
      harness.await(harness.submit(() -> summary.getRowCount()), ACTIVE_CAP, "getRowCount");
      assertTrue(base.paying.await(ACTIVE_CAP, TimeUnit.SECONDS), "the worker never read the base");
      assertTrue(base.firstByWorker, "the base was read by a harness thread, not the worker");

      Throwable failure =
         StallTestSupport.failureOf(harness.submit(() -> drain(outer)), 2 * ACTIVE_CAP);
      StallTestSupport.stallIn(failure);
      // the worker's read goes on after the holder failed
      StallTestSupport.awaitTrue(() -> base.paidRows() == ROWS, 2 * ACTIVE_CAP,
                                 "the worker never read the whole base");
      assertTrue(base.maxRowsPerCall() * F2_ROW_MILLIS > 8000,
                 "no base read cost more than the limit, rows paid per read: " + base.calls);
   }

   /**
    * The pool-off twin of {@link #knownPoolOnW2FormulaBaseReadStalls}: since #77223 a summary
    * over a base that needs the engine lock starts no worker, so the holder computes it
    * inline, asleep in no wait, and completes with every row.
    */
   @Test
   public void f2FormulaBaseComputesInlinePoolOff() throws Exception {
      harness.close();
      harness = new LockCycleHarness(false);
      policy(8000, true);
      Sandbox control = harness.control();
      List<List<Object>> expected = harness.await(harness.submit(
         () -> drain(cf2(f2Summary(control, new DefaultTableLens(StallTestSupport.data(ROWS))),
                         null))), ACTIVE_CAP, "control pipeline");
      Sandbox s = harness.sandbox();
      PerRowTable base = new PerRowTable(ROWS, () -> sleep(F2_ROW_MILLIS));
      SummaryFilter summary = f2Summary(s, base);
      TableLens outer = harness.track(cf2(summary, s.box));
      harness.await(harness.submit(() -> summary.getRowCount()), ACTIVE_CAP, "getRowCount");

      assertEquals(expected, harness.await(harness.submit(() -> drain(outer)), 2 * ACTIVE_CAP,
                                           "holder computing inline"));
      assertFalse(base.firstByWorker, "a lens worker read the base: a worker was started");
   }

   /**
    * {@link #knownW2SleepingUnregisteredReadStallsInBothModes} under the shipped fail rule
    * (Feature #77123, no {@code failOnTimeout}): the worker's 18 s read in no registered wait
    * is still a stall the watchdog cannot credit, so it is reported with a thread dump, but it
    * is no wait-for cycle, so nothing fails and the holder completes with every row, with the
    * pool on or off.
    */
   @Test
   public void w2SleepingUnregisteredReadOnlyAlertsUnderTheDefaultRule() throws Exception {
      policy(8000, false);
      List<List<Object>> expected = expected();
      Sandbox s = harness.sandbox();
      AtomicInteger paid = new AtomicInteger();
      PerRowTable base = new PerRowTable(ROWS, () -> sleep(paid.getAndIncrement() == 0 ? W2_READ_MILLIS : 0));
      SummaryFilter summary = summary(s, base);
      TableLens outer = harness.track(cf2(summary, s.box));
      harness.await(harness.submit(() -> summary.getRowCount()), ACTIVE_CAP, "getRowCount");
      assertTrue(base.paying.await(ACTIVE_CAP, TimeUnit.SECONDS), "the worker never read the base");
      assertTrue(base.firstByWorker, "the base was read by a harness thread, not the worker");

      AtomicReference<String> holder = new AtomicReference<>();
      assertEquals(expected, harness.await(harness.submit(() -> drainAs(holder, outer)),
                                           2 * ACTIVE_CAP, "holder behind the W2 read"));
      assertStallReportedFor(holder.get());
   }

   /**
    * {@link #knownPoolOnW2FormulaBaseReadStalls} under the shipped fail rule (Feature #77123,
    * no {@code failOnTimeout}): the pooled worker's one long base read is reported with a
    * thread dump, but it is no wait-for cycle, so nothing fails and the holder completes with
    * every row.
    */
   @Test
   public void poolOnW2FormulaBaseReadOnlyAlertsUnderTheDefaultRule() throws Exception {
      harness.close();
      harness = new LockCycleHarness(true);
      policy(8000, false);
      Sandbox control = harness.control();
      List<List<Object>> expected = harness.await(harness.submit(
         () -> drain(cf2(f2Summary(control, new DefaultTableLens(StallTestSupport.data(ROWS))),
                         null))), ACTIVE_CAP, "control pipeline");
      Sandbox s = harness.sandbox();
      PerRowTable base = new PerRowTable(ROWS, () -> sleep(F2_ROW_MILLIS));
      SummaryFilter summary = f2Summary(s, base);
      TableLens outer = harness.track(cf2(summary, s.box));
      harness.await(harness.submit(() -> summary.getRowCount()), ACTIVE_CAP, "getRowCount");
      assertTrue(base.paying.await(ACTIVE_CAP, TimeUnit.SECONDS), "the worker never read the base");
      assertTrue(base.firstByWorker, "the base was read by a harness thread, not the worker");

      AtomicReference<String> holder = new AtomicReference<>();
      assertEquals(expected, harness.await(harness.submit(() -> drainAs(holder, outer)),
                                           2 * ACTIVE_CAP,
                                           "holder behind the pooled formula base read"));
      // the read pattern of the failOnTimeout case, so the stall condition was there
      assertTrue(base.maxRowsPerCall() * F2_ROW_MILLIS > 8000,
                 "no base read cost more than the limit, rows paid per read: " + base.calls);
      assertStallReportedFor(holder.get());
   }

   /**
    * A per-row cost spent running (RUNNABLE), over the 8 s limit: the worker is credited while
    * it runs, so the holder completes with the pool on or off.
    */
   @Test
   public void runningBaseCompletesInBothModes() throws Exception {
      policy(8000, true);
      List<List<Object>> expected = expected();
      Sandbox s = harness.sandbox();
      SummaryFilter summary = summary(s, new PerRowTable(ROWS, () -> spin(1000)));
      TableLens outer = harness.track(cf2(summary, s.box));
      harness.await(harness.submit(() -> summary.getRowCount()), ACTIVE_CAP, "getRowCount");

      assertEquals(expected, harness.await(harness.submit(() -> drain(outer)), 2 * ACTIVE_CAP,
                                           "running holder"));
   }

   /**
    * A per-row cost spent waiting for rows a producer adds to an XSwappableTable, 1 s a row,
    * over the 8 s limit, as a slow query fills its table: the worker's credit-only wait there
    * moves with the rows, so the holder completes with the pool on or off.
    */
   @Test
   public void producerFedBaseCompletesInBothModes() throws Exception {
      policy(8000, true);
      List<List<Object>> expected = expected();
      XSwappableTable rows = new XSwappableTable(2, false);
      Object[][] data = StallTestSupport.data(ROWS);
      rows.addRow(data[0]);
      Sandbox s = harness.sandbox();
      SummaryFilter summary = summary(s, new XTableLens(rows));
      TableLens outer = harness.track(cf2(summary, s.box));
      harness.await(harness.submit(() -> summary.getRowCount()), ACTIVE_CAP, "getRowCount");
      Future<Void> producer = harness.submit(() -> {
         rows.setProducer(Thread.currentThread());

         for(int i = 1; i < data.length; i++) {
            sleep(1000);
            rows.addRow(data[i]);
         }

         rows.complete();
         return null;
      });

      assertEquals(expected, harness.await(harness.submit(() -> drain(outer)), 2 * ACTIVE_CAP,
                                           "producer-fed holder"));
      harness.await(producer, ACTIVE_CAP, "producer");
   }

   private List<List<Object>> expected() throws Exception {
      XSwappableTable rows = new XSwappableTable(2, false);

      for(Object[] row : StallTestSupport.data(ROWS)) {
         rows.addRow(row);
      }

      rows.complete();
      Sandbox control = harness.control();
      return harness.await(harness.submit(
         () -> drain(cf2(summary(control, new XTableLens(rows)), null))),
         ACTIVE_CAP, "control pipeline");
   }

   /**
    * Fail mode at {@code noProgressMillis}.
    *
    * @param failOnTimeout fail a stall on the timeout alone (the opt-in
    *                      {@code stall.watchdog.failOnTimeout}), or only a confirmed wait-for
    *                      cycle (the shipped rule, Feature #77123).
    */
   private void policy(long noProgressMillis, boolean failOnTimeout) {
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, noProgressMillis, 500,
                                              dumpDir, StallPolicy.DEFAULT_MAX_DUMPS,
                                              failOnTimeout));
   }

   /**
    * Drain {@code table}, recording the name of the draining thread first.
    */
   private static List<List<Object>> drainAs(AtomicReference<String> thread, TableLens table) {
      thread.set(Thread.currentThread().getName());
      return drain(table);
   }

   /**
    * Assert that a thread dump in this test's dump directory reports a stall of a wait of
    * {@code thread}, by the head the watchdog writes for it ({@code WaitRecord.describe}), so a
    * dump of another thread's wait (e.g. a wait an earlier class left registered) is no match.
    */
   private void assertStallReportedFor(String thread) throws IOException {
      assertNotNull(thread, "the holder never ran");
      String head = " on thread \"" + thread + "\", no progress for ";
      File[] dumps = dumpDir.listFiles((dir, name) -> name.startsWith("stall-dump-"));
      List<String> heads = new ArrayList<>();

      for(File dump : dumps == null ? new File[0] : dumps) {
         try(BufferedReader in = Files.newBufferedReader(dump.toPath(), StandardCharsets.UTF_8)) {
            String first = in.readLine();

            if(first != null && first.contains(head)) {
               return;
            }

            heads.add(first);
         }
      }

      fail("no stall of the holder " + thread + " was reported, dump heads: " + heads);
   }

   /**
    * The summary of the original F2 shape: over a filtered formula lens over {@code base}.
    */
   private SummaryFilter f2Summary(Sandbox s, TableLens base) {
      return harness.track(new SummaryFilter(s.filteredFormula(base), new int[] { 0 },
                                             new int[] { 1 }, new SumFormula(), null));
   }

   private SummaryFilter summary(Sandbox s, TableLens base) {
      return harness.track(new SummaryFilter(cf2(base, s.box), new int[] { 0 },
                                             new int[] { 1 }, new SumFormula(), null));
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      }
      catch(InterruptedException ex) {
         Thread.currentThread().interrupt();
      }
   }

   private static void spin(long millis) {
      long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);

      while(System.nanoTime() - end < 0) {
         Thread.onSpinWait();
      }
   }

   /**
    * A table that pays {@code cost} once for every data row it is the first to reach, however
    * many rows one moreRows call covers.
    */
   private static final class PerRowTable extends DefaultTableLens {
      PerRowTable(int rows, Runnable cost) {
         super(StallTestSupport.data(rows));
         this.rows = rows;
         this.cost = cost;
      }

      @Override
      public boolean moreRows(int row) {
         int last = Math.min(row, rows);
         int paid = 0;

         while(true) {
            int next;

            synchronized(this) {
               if(reached >= last) {
                  break;
               }

               next = ++reached;
            }

            if(next >= 1) {
               if(next == 1) {
                  firstByWorker = !isHarnessThread();
                  paying.countDown();
               }

               cost.run();
               paid++;
            }
         }

         if(paid > 0) {
            calls.add(paid);
         }

         return super.moreRows(row);
      }

      private final int rows;
      private final Runnable cost;
      private int reached;
      /** Counted down when the first row's cost starts. */
      final CountDownLatch paying = new CountDownLatch(1);
      /** Whether a lens worker, not a test thread, paid the first row's cost. */
      volatile boolean firstByWorker;
      /** The rows each moreRows call that paid any paid for, in call order. */
      final List<Integer> calls = new java.util.concurrent.CopyOnWriteArrayList<>();

      int paidRows() {
         return calls.stream().mapToInt(Integer::intValue).sum();
      }

      int maxRowsPerCall() {
         return calls.stream().mapToInt(Integer::intValue).max().orElse(0);
      }
   }

   private static final int ROWS = 10;
   // the W2 case's one read, far over the 8 s limit
   private static final long W2_READ_MILLIS = 18000;
   // the F2 shape's per-row cost
   private static final long F2_ROW_MILLIS = 1500;
   @TempDir
   File dumpDir;
   private LockCycleHarness harness;
}
