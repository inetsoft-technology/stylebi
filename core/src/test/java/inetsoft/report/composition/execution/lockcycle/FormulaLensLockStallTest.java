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
import inetsoft.report.filter.SortFilter;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.report.script.TableArray;
import inetsoft.test.*;
import inetsoft.util.stall.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.*;
import static inetsoft.util.stall.StallTestSupport.assertStallOf;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The formula lens lock is bounded by the lock-stall watchdog (bug #76967): a lens-lock
 * waiter whose owner is blocked on a monitor the waiter holds fails with a stall and lets
 * go, and the owner then completes; a waiter behind a slowly progressing batch completes.
 * Pool off (the harness default); the pool-on batch hold is the same lock, and a formula
 * script never waits for a held context there (see {@link #formulaScriptStallReachesTheReader}).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("slow")
public class FormulaLensLockStallTest {
   @BeforeEach
   public void setUp() {
      StallTestSupport.resetGlobalStallState();
      // failOnTimeout: the stall cases wait for a lock a stuck thread holds, which is no
      // wait-for cycle, so the shipped rule (fail only a confirmed cycle) would only report them
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 1000, 200, dumpDir,
                                              StallPolicy.DEFAULT_MAX_DUMPS, true));
      harness = new LockCycleHarness();
   }

   @AfterEach
   public void tearDown() throws Exception {
      harness.close();
      StallTestSupport.clearOverride();
   }

   /**
    * FTL_R2 without the engine lock, which no longer forms since #5576 (bug #76935): the owner
    * loads its base rows before it takes any lock, so it blocks on the monitor T2 holds while
    * holding nothing, and T2 takes the locks and completes. Neither thread stalls.
    */
   @Test
   public void monitorHolderAndLensOwnerBothComplete() throws Exception {
      Sandbox s = harness.control();
      List<List<Object>> expected = harness.await(
         harness.submit(() -> drain(s.formula(new DefaultTableLens(rows(40))))),
         ACTIVE_CAP, "control");
      MonitorTable base = new MonitorTable(rows(40), 5, 0);
      FormulaTableLens lens = harness.track(s.formula(base));
      CountDownLatch waiterHasMonitor = new CountDownLatch(1);
      AtomicReference<Thread> waiterThread = new AtomicReference<>();

      Future<Object> waiter = harness.submit(() -> {
         waiterThread.set(Thread.currentThread());

         synchronized(base.monitor) {
            waiterHasMonitor.countDown();
            assertTrue(base.entered.await(10, TimeUnit.SECONDS), "the owner never reached row 5");

            try {
               lens.moreRows(30);
               return "completed";
            }
            catch(LockStallException ex) {
               return ex;
            }
         }
      });
      assertTrue(waiterHasMonitor.await(10, TimeUnit.SECONDS));
      Future<List<List<Object>>> owner = harness.submit(() -> drain(lens));

      assertEquals("completed", harness.await(waiter, ACTIVE_CAP, "monitor holder"));
      assertEquals(expected, harness.await(owner, ACTIVE_CAP, "lens owner"),
                   "the owner completes with every row once the monitor holder lets go");
      // only this test's waiter: hung threads of earlier cycle tests may still be registered
      StallTestSupport.awaitTrue(
         () -> WaitRegistry.global().getActive().stream()
            .noneMatch(r -> r.getThread() == waiterThread.get()), 15,
         "a wait is still registered after the reads");
   }

   /**
    * The lens-lock wait itself is bounded: with the lens lock held by a parked thread, a reader
    * that took the engine lock first (#5576's order) fails with a stall at the lens lock and
    * does not leave the engine locked, which would hang every later script of the sandbox.
    */
   @Test
   public void lensLockStallReleasesTheEngineLock() throws Exception {
      Sandbox s = harness.sandbox();
      FormulaTableLens lens = harness.track(s.formula(new DefaultTableLens(rows(40))));
      ReentrantLock lensLock = lensLock(lens);
      CountDownLatch held = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      Future<Object> owner = harness.submit(() -> {
         lensLock.lock();

         try {
            held.countDown();
            release.await();
         }
         finally {
            lensLock.unlock();
         }

         return null;
      });
      assertTrue(held.await(10, TimeUnit.SECONDS), "the owner never took the lens lock");

      try {
         Object outcome = harness.await(harness.submit(() -> {
            try {
               lens.moreRows(30);
               return "completed";
            }
            catch(LockStallException ex) {
               return ex;
            }
         }), ACTIVE_CAP, "reader");

         assertTrue(outcome instanceof LockStallException, "the reader must fail, got " + outcome);
         assertEquals("FormulaTableLens.moreRows", ((LockStallException) outcome).getSite());
         assertFalse(s.lock.isLocked(), "the stalled reader left the engine locked");
      }
      finally {
         release.countDown();
      }

      harness.await(owner, ACTIVE_CAP, "lens lock owner");
      assertTrue(lens.moreRows(30), "the lens works again once the lock is free");
   }

   private static ReentrantLock lensLock(FormulaTableLens lens) throws Exception {
      Field field = FormulaTableLens.class.getDeclaredField("lock");
      field.setAccessible(true);
      return (ReentrantLock) field.get(lens);
   }

   /**
    * No false positive: the owner's batch computes a row every 300 ms under the lens lock,
    * for longer than the 1 s limit; the waiter completes with the right rows.
    */
   @Test
   public void waiterBehindAProgressingBatchCompletes() throws Exception {
      Sandbox s = harness.control();
      List<List<Object>> expected = harness.await(
         harness.submit(() -> drain(s.formula(new DefaultTableLens(rows(8))))),
         ACTIVE_CAP, "control");
      MonitorTable base = new MonitorTable(rows(8), 1, 300);
      FormulaTableLens lens = harness.track(s.formula(base));

      Future<List<List<Object>>> owner = harness.submit(() -> drain(lens));
      assertTrue(base.entered.await(10, TimeUnit.SECONDS));
      Future<Boolean> waiter = harness.submit(() -> lens.moreRows(8));

      assertTrue(harness.await(waiter, ACTIVE_CAP, "waiter behind a slow batch"));
      assertEquals(expected, harness.await(owner, ACTIVE_CAP, "slow owner"));
   }

   /**
    * The formula's script waits for an engine lock held by a stuck thread: the lens reader
    * gets the stall itself, not a formula error, a null cell or the end of the table, and the
    * half-computed row is not kept, so a read after the holder let go sees every value.
    * With the pool on, a held context is never waited for: the reader gets every value while
    * the holder is stuck.
    */
   @Test
   public void formulaScriptStallReachesTheReader() throws Exception {
      Sandbox s = harness.control();
      List<List<Object>> expected = harness.await(
         harness.submit(() -> drain(s.formula(new DefaultTableLens(rows(20))))),
         ACTIVE_CAP, "control");
      FormulaTableLens lens = harness.track(s.formula(new DefaultTableLens(rows(20))));
      CountDownLatch held = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      Future<Boolean> holder = harness.submit(() -> {
         s.lock.lock();

         try {
            held.countDown();
            return release.await(3 * KNOWN_CAP, TimeUnit.SECONDS);
         }
         finally {
            s.lock.unlock();
         }
      });
      assertTrue(held.await(10, TimeUnit.SECONDS), "the holder never took the engine lock");

      if(POOL) {
         // pool mode never waits for a context (spec I6): the primary's lock is skipped with
         // tryLock and the formula runs on another slot, so there is no stall to report and
         // the reader must get every value while the holder is still stuck
         try {
            assertEquals(expected, harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP,
                                                 "reader while the primary is held"),
                         "a held primary context must not block or change the formula");
            assertFalse(holder.isDone(), "the holder let go before the reader finished");
         }
         finally {
            release.countDown();
         }

         assertTrue(harness.await(holder, ACTIVE_CAP, "holder"));
         return;
      }

      Throwable failure =
         StallTestSupport.failureOf(harness.submit(() -> drain(lens)), ACTIVE_CAP);
      assertInstanceOf(LockStallException.class, failure, "the reader must get the stall");
      assertEquals("LendableReentrantLock.lock", ((LockStallException) failure).getSite());

      release.countDown();
      assertTrue(harness.await(holder, ACTIVE_CAP, "holder"));
      assertEquals(expected, harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP,
                                           "reader after the stall"),
                   "no half-computed row is kept after the stall");
   }

   /**
    * The formula's script calls into Java code whose wait stalled, e.g. a read of another
    * table: the stall crosses the script engine and reaches the lens reader, as a copy of it
    * (bug #78084), not as a script error.
    */
   @Test
   public void stallInsideTheScriptReachesTheReader() throws Exception {
      LockStallException original = new LockStallException("nested.site", "worker", 1234, null);
      Sandbox s = harness.control();
      s.env.put("stalledHost", new StalledHost(original));
      FormulaTableLens lens = harness.track(
         s.formula(new DefaultTableLens(rows(20)), "f", "stalledHost.value()"));

      Throwable failure =
         StallTestSupport.failureOf(harness.submit(() -> drain(lens)), ACTIVE_CAP);
      assertStallOf(original, failure,
                    "the reader must get the stall thrown inside the script");
   }

   /**
    * End to end through a sort: the stall inside the formula's script escapes the SortFilter
    * over the formula lens instead of leaving its rows unsorted.
    */
   @Test
   public void stallInsideTheScriptEscapesASortOverTheLens() throws Exception {
      LockStallException original = new LockStallException("nested.site", "worker", 1234, null);
      Sandbox s = harness.control();
      s.env.put("stalledHost", new StalledHost(original));
      FormulaTableLens lens = harness.track(
         s.formula(new DefaultTableLens(rows(20)), "f", "stalledHost.value()"));
      SortFilter sorted = harness.track(new SortFilter(lens, new int[] { 2 }));

      Throwable failure =
         StallTestSupport.failureOf(harness.submit(() -> drain(sorted)), ACTIVE_CAP);
      assertStallOf(original, failure, "the sort must not swallow the stall");
   }

   /**
    * The formula's script reads a field of a table whose read stalls: the field read does not
    * turn the stall into a null value, the lens reader gets it as it is.
    */
   @Test
   public void stalledFieldReadReachesTheReader() throws Exception {
      LockStallException original = new LockStallException("nested.site", "worker", 1234, null);
      Sandbox s = harness.control();
      java.util.Set<Integer> failed = java.util.concurrent.ConcurrentHashMap.newKeySet();
      // the first read of row 3's value, the script's, stalls; the reader's reads do not
      TableLens base = new DefaultTableLens(rows(20)) {
         @Override
         public Object getObject(int r, int c) {
            if(r == 3 && c == 1 && failed.add(r)) {
               throw original;
            }

            return super.getObject(r, c);
         }
      };
      FormulaTableLens lens = harness.track(s.formula(base, "f", "field['value'] + 1"));

      Throwable failure =
         StallTestSupport.failureOf(harness.submit(() -> drain(lens)), ACTIVE_CAP);
      assertStallOf(original, failure, "the reader must get the stall of the field read");
   }

   /**
    * A read to the end of the table (as a sort or a crosstab does) whose batch stalls
    * part way must not mark the lens's row table complete (bug #77123): a completed row
    * table is swappable, the resumed read then appends rows to a completed fragment, and
    * after the fragment is swapped around the resumed read the appended state is lost and
    * every formula cell reads null, with no error.
    */
   @Test
   public void stalledEndOfTableReadDoesNotCompleteTheLens() throws Exception {
      final int n = 40;
      Sandbox s = harness.control();
      List<List<Object>> expected = harness.await(
         harness.submit(() -> drain(s.formula(new DefaultTableLens(rows(n)),
                                              "f", "field['value'] + 1"))),
         ACTIVE_CAP, "control");
      LockStallException original = new LockStallException("nested.site", "worker", 1234, null);
      java.util.Set<Integer> failed = java.util.concurrent.ConcurrentHashMap.newKeySet();
      // the first read of row 15's value, the script's, stalls; later reads do not
      TableLens base = new DefaultTableLens(rows(n)) {
         @Override
         public Object getObject(int r, int c) {
            if(r == 15 && c == 1 && failed.add(r)) {
               throw original;
            }

            return super.getObject(r, c);
         }
      };
      FormulaTableLens lens = harness.track(s.formula(base, "f", "field['value'] + 1"));

      Throwable failure = StallTestSupport.failureOf(
         harness.submit(() -> lens.moreRows(TableLens.EOT)), ACTIVE_CAP);
      assertStallOf(original, failure, "the end-of-table read must get the stall");
      assertFalse(rowTable(lens).isCompleted(),
                  "a batch that stalled must not complete the lens's row table");

      swapRowTable(lens);
      harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP, "resumed read");
      swapRowTable(lens);

      assertEquals(expected, harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP,
                                           "read after the swaps"),
                   "the formula values survive the swaps around the resumed read");
   }

   /**
    * After an end-of-table read stalled, the next end-of-table read that succeeds completes
    * the lens's row table (bug #77123): the stall leaves it open only until a batch reaches
    * the end, and the values are right without any swap.
    */
   @Test
   public void endOfTableReadAfterAStallCompletesTheLens() throws Exception {
      final int n = 40;
      Sandbox s = harness.control();
      List<List<Object>> expected = harness.await(
         harness.submit(() -> drain(s.formula(new DefaultTableLens(rows(n)),
                                              "f", "field['value'] + 1"))),
         ACTIVE_CAP, "control");
      LockStallException original = new LockStallException("nested.site", "worker", 1234, null);
      java.util.Set<Integer> failed = java.util.concurrent.ConcurrentHashMap.newKeySet();
      TableLens base = new DefaultTableLens(rows(n)) {
         @Override
         public Object getObject(int r, int c) {
            if(r == 15 && c == 1 && failed.add(r)) {
               throw original;
            }

            return super.getObject(r, c);
         }
      };
      FormulaTableLens lens = harness.track(s.formula(base, "f", "field['value'] + 1"));

      assertStallOf(original, StallTestSupport.failureOf(
         harness.submit(() -> lens.moreRows(TableLens.EOT)), ACTIVE_CAP));
      assertFalse(rowTable(lens).isCompleted());

      assertFalse(harness.await(harness.submit(() -> lens.moreRows(TableLens.EOT)), ACTIVE_CAP,
                                "end-of-table read after the stall"));
      assertTrue(rowTable(lens).isCompleted(),
                 "a batch that reaches the end after the stall completes the row table");
      assertEquals(n + 1, lens.getRowCount());
      assertEquals(expected, harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP,
                                           "read after the resumed end-of-table read"));
   }

   /**
    * The base read in the batch loop condition stalls, outside the row's own catch: the
    * reader gets that stall as it is, the row table is left open, and a resumed read with
    * swaps around it gives every formula value (bug #77123).
    */
   @Test
   public void baseStallInTheLoopConditionReachesTheReader() throws Exception {
      final int n = 40;
      Sandbox s = harness.control();
      List<List<Object>> expected = harness.await(
         harness.submit(() -> drain(s.formula(new DefaultTableLens(rows(n)),
                                              "f", "field['value'] + 1"))),
         ACTIVE_CAP, "control");
      LockStallException original = new LockStallException("nested.site", "worker", 1234, null);
      java.util.Set<Integer> failed = java.util.concurrent.ConcurrentHashMap.newKeySet();
      // the first moreRows(15) stalls, as a stalled async base lens would; later ones do not
      TableLens base = new DefaultTableLens(rows(n)) {
         @Override
         public boolean moreRows(int r) {
            if(r == 15 && failed.add(r)) {
               throw original;
            }

            return super.moreRows(r);
         }
      };
      FormulaTableLens lens = harness.track(s.formula(base, "f", "field['value'] + 1"));

      Throwable failure = StallTestSupport.failureOf(
         harness.submit(() -> lens.moreRows(TableLens.EOT)), ACTIVE_CAP);
      assertSame(original, failure, "the base read's stall must reach the reader unchanged");
      assertFalse(failed.isEmpty(), "the base read in the loop condition never stalled");
      assertFalse(rowTable(lens).isCompleted(),
                  "a batch that stalled in the base read must not complete the row table");

      swapRowTable(lens);
      harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP, "resumed read");
      swapRowTable(lens);

      assertEquals(expected, harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP,
                                           "read after the swaps"));
   }

   /**
    * Alert mode (the default) never fails a wait: an end-of-table read that waits on the
    * lens lock for longer than the limit is only alerted, then completes the lens as before
    * (bug #77123 changes nothing without a thrown stall).
    */
   @Test
   public void alertModeEndOfTableReadCompletesTheLens() throws Exception {
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.ALERT, 1000, 200, dumpDir,
                                              StallPolicy.DEFAULT_MAX_DUMPS, false));
      final int n = 40;
      Sandbox s = harness.control();
      List<List<Object>> expected = harness.await(
         harness.submit(() -> drain(s.formula(new DefaultTableLens(rows(n)),
                                              "f", "field['value'] + 1"))),
         ACTIVE_CAP, "control");
      FormulaTableLens lens = harness.track(
         s.formula(new DefaultTableLens(rows(n)), "f", "field['value'] + 1"));
      ReentrantLock lensLock = lensLock(lens);
      CountDownLatch held = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      Future<Object> owner = harness.submit(() -> {
         lensLock.lock();

         try {
            held.countDown();
            release.await();
         }
         finally {
            lensLock.unlock();
         }

         return null;
      });
      assertTrue(held.await(10, TimeUnit.SECONDS), "the owner never took the lens lock");
      int dumps = WaitRegistry.global().getDumper().getDumpCount();
      Future<Boolean> reader = harness.submit(() -> lens.moreRows(TableLens.EOT));

      try {
         // past the 1 s limit: the wait is alerted, not failed
         StallTestSupport.awaitTrue(
            () -> WaitRegistry.global().getDumper().getDumpCount() > dumps, 15,
            "the lens-lock wait was never alerted");
         assertFalse(reader.isDone(), "the reader must still be waiting, got a result");
      }
      finally {
         release.countDown();
      }

      harness.await(owner, ACTIVE_CAP, "lens lock owner");
      assertFalse(harness.await(reader, ACTIVE_CAP, "alerted reader"));
      assertTrue(rowTable(lens).isCompleted(), "the alerted read completes the row table");
      assertEquals(expected, harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP,
                                           "read after the alerted read"));
   }

   private static inetsoft.uql.table.XSwappableTable rowTable(FormulaTableLens lens)
      throws Exception
   {
      Field field = FormulaTableLens.class.getDeclaredField("rows");
      field.setAccessible(true);
      return (inetsoft.uql.table.XSwappableTable) field.get(lens);
   }

   /**
    * Swap every swappable fragment of the lens's row table, as the swapper does under
    * memory pressure.
    */
   private static void swapRowTable(FormulaTableLens lens) throws Exception {
      Field field = inetsoft.uql.table.XSwappableTable.class.getDeclaredField("tables");
      field.setAccessible(true);

      for(Object fragment : (Object[]) field.get(rowTable(lens))) {
         if(fragment != null) {
            ((inetsoft.uql.table.XTableFragment) fragment).swap(false);
         }
      }
   }

   /**
    * The formula reads a column of another table through {@code TableArray}
    * ({@code T2['value'][0]}) whose formula lens lock is held by a parked thread: the
    * column read's stall reaches the outer lens reader as it is (bug #77123), not a null
    * column that the script would turn into its fallback value.
    */
   @Test
   public void stalledTableArrayColumnReadReachesTheReader() throws Exception {
      Sandbox s = harness.control();
      Sandbox s2 = harness.control();
      FormulaTableLens t2 = harness.track(s2.formula(new DefaultTableLens(rows(20))));
      s.env.put("T2", new TableArray(t2));
      FormulaTableLens lens = harness.track(
         s.formula(new DefaultTableLens(rows(20)), "f",
                   "var c = T2['value']; c == null ? -1 : c[0]"));
      ReentrantLock t2Lock = lensLock(t2);
      CountDownLatch held = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      Future<Object> owner = harness.submit(() -> {
         t2Lock.lock();

         try {
            held.countDown();
            release.await();
         }
         finally {
            t2Lock.unlock();
         }

         return null;
      });
      assertTrue(held.await(10, TimeUnit.SECONDS), "the owner never took T2's lens lock");

      try {
         Throwable failure =
            StallTestSupport.failureOf(harness.submit(() -> drain(lens)), ACTIVE_CAP);
         assertInstanceOf(LockStallException.class, failure,
                          "the reader must get the stall of T2's column read, got " + failure);
         assertEquals("FormulaTableLens.moreRows", ((LockStallException) failure).getSite());
      }
      finally {
         release.countDown();
      }

      harness.await(owner, ACTIVE_CAP, "T2 lens lock owner");
   }

   /**
    * Alert mode (the default): the same {@code TableArray} column read only waits past the
    * limit, is alerted, and gives the right column once T2's lock is free.
    */
   @Test
   public void alertModeTableArrayColumnReadCompletes() throws Exception {
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.ALERT, 1000, 200, dumpDir,
                                              StallPolicy.DEFAULT_MAX_DUMPS, false));
      Sandbox s = harness.control();
      Sandbox s2 = harness.control();
      FormulaTableLens t2 = harness.track(s2.formula(new DefaultTableLens(rows(20))));
      s.env.put("T2", new TableArray(t2));
      FormulaTableLens lens = harness.track(
         s.formula(new DefaultTableLens(rows(20)), "f",
                   "var c = T2['value']; c == null ? -1 : c[0]"));
      ReentrantLock t2Lock = lensLock(t2);
      CountDownLatch held = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      Future<Object> owner = harness.submit(() -> {
         t2Lock.lock();

         try {
            held.countDown();
            release.await();
         }
         finally {
            t2Lock.unlock();
         }

         return null;
      });
      assertTrue(held.await(10, TimeUnit.SECONDS), "the owner never took T2's lens lock");
      int dumps = WaitRegistry.global().getDumper().getDumpCount();
      Future<List<List<Object>>> reader = harness.submit(() -> drain(lens));

      try {
         StallTestSupport.awaitTrue(
            () -> WaitRegistry.global().getDumper().getDumpCount() > dumps, 15,
            "the T2 lens-lock wait was never alerted");
         assertFalse(reader.isDone(), "the reader must still be waiting, got a result");
      }
      finally {
         release.countDown();
      }

      harness.await(owner, ACTIVE_CAP, "T2 lens lock owner");
      List<List<Object>> result = harness.await(reader, ACTIVE_CAP, "alerted reader");
      assertEquals(21, result.size());

      for(int r = 1; r < result.size(); r++) {
         List<Object> row = result.get(r);
         // T2['value'][0] is the first data value of T2, 1
         assertEquals(1, ((Number) row.get(row.size() - 1)).intValue(), "row " + r + ": " + row);
      }
   }

   @Test
   public void uncontendedLensRegistersNothing() throws Exception {
      Sandbox s = harness.control();
      FormulaTableLens lens = harness.track(s.formula(new DefaultTableLens(rows(20))));
      long before = WaitRegistry.global().getBeginCount();

      harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP, "drain");

      assertEquals(before, WaitRegistry.global().getBeginCount());
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
    * A base whose rows from {@code gate} on are read under {@link #monitor}, optionally
    * slowly; {@link #entered} opens on the first such read.
    */
   private static final class MonitorTable extends DefaultTableLens {
      MonitorTable(Object[][] data, int gate, long millisPerRow) {
         super(data);
         this.gate = gate;
         this.millisPerRow = millisPerRow;
      }

      @Override
      public boolean moreRows(int row) {
         if(row >= gate && row != TableLens.EOT) {
            entered.countDown();

            synchronized(monitor) {
               pause();
            }
         }

         return super.moreRows(row);
      }

      private void pause() {
         if(millisPerRow <= 0) {
            return;
         }

         try {
            Thread.sleep(millisPerRow);
         }
         catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
         }
      }

      final Object monitor = new Object();
      final CountDownLatch entered = new CountDownLatch(1);
      private final int gate;
      private final long millisPerRow;
   }

   /**
    * A host object whose method fails with a lock stall, as a nested table read would.
    */
   public static final class StalledHost {
      StalledHost(LockStallException failure) {
         this.failure = failure;
      }

      public Object value() {
         throw failure;
      }

      private final LockStallException failure;
   }

   @TempDir
   File dumpDir;
   private LockCycleHarness harness;
}
