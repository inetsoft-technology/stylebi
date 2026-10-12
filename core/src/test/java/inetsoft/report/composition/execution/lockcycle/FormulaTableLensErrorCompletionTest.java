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
import inetsoft.test.*;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.uql.table.XTableFragment;
import inetsoft.util.script.ExpressionFailedException;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A formula lens batch that ends in an exception must not mark the lens's row table complete
 * (bug #77123). A completed row table is swappable; the next read resumes after the failed row
 * and appends the remaining rows to the completed fragment, so a swap around that read loses
 * the appended values (every formula cell then reads null) or makes the append fail with an
 * NPE on every later read. Default config: pool off, stall mode ALERT.
 *
 * The swaps call {@code XTableFragment.swap(false)} on every fragment, the swapper's own call
 * with the same {@code getSwapPriority() > 0} gate; the swapper makes it once the free heap
 * is below 40% and the fragment has been idle long enough (about 73 s in NORM_MEM).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class FormulaTableLensErrorCompletionTest {
   @BeforeEach
   public void setUp() {
      StallTestSupport.resetGlobalStallState();
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.ALERT, 1000, 200, dumpDir,
                                              StallPolicy.DEFAULT_MAX_DUMPS, false));
      harness = new LockCycleHarness();
   }

   @AfterEach
   public void tearDown() throws Exception {
      harness.close();
      StallTestSupport.clearOverride();
   }

   /**
    * An end-of-table read whose script fails for one row leaves the row table open, and the
    * values survive swaps around the resumed read.
    */
   @Test
   public void endOfTableScriptErrorDoesNotCompleteTheLens() throws Exception {
      assertSurvivesSwaps(TableLens.EOT);
   }

   /**
    * The same for a read past the end, as the worksheet composer's row count read makes.
    */
   @Test
   public void pastTheEndScriptErrorDoesNotCompleteTheLens() throws Exception {
      assertSurvivesSwaps(N + 500);
   }

   /**
    * A sort over the lens swallows the first read's script error; a swap before the next
    * sort must not make its resumed read fail on every call and leave the sort header-only.
    */
   @Test
   public void sortOverAFailedLensGetsEveryRowAfterASwap() throws Exception {
      Sandbox s = harness.control();
      FormulaTableLens lens = harness.track(s.formula(new DefaultTableLens(rows(N)), "f", BAD));
      SortFilter sort = new SortFilter(lens, new int[] { 1 }, false);

      // the first sort gets the script error, logs it and serves the header only (as before)
      harness.await(harness.submit(() -> drain(sort)), ACTIVE_CAP, "first sort");
      assertFalse(rowTable(lens).isCompleted(),
                  "a batch that failed must not complete the lens's row table");

      swapRowTable(lens);
      List<List<Object>> sorted = harness.await(harness.submit(() -> drain(sort)), ACTIVE_CAP,
                                                "sort after the swap");

      assertEquals(N + 1, sorted.size(), "the sort must get every row");
      long nulls = sorted.stream().skip(1).filter(row -> row.get(2) == null).count();
      assertEquals(1, nulls, "only the failed row's formula cell is null: " + sorted);
   }

   /**
    * Not only a script error: any exception out of the batch, here a base read in the loop
    * condition, leaves the row table open, and the values survive the swaps.
    */
   @Test
   public void baseReadFailureDoesNotCompleteTheLens() throws Exception {
      List<List<Object>> expected = control(GOOD);
      IllegalStateException original = new IllegalStateException("base read failed");
      Set<Integer> failed = ConcurrentHashMap.newKeySet();
      TableLens base = new DefaultTableLens(rows(N)) {
         @Override
         public boolean moreRows(int r) {
            if(r == 15 && failed.add(r)) {
               throw original;
            }

            return super.moreRows(r);
         }
      };
      FormulaTableLens lens = harness.track(harness.control().formula(base, "f", GOOD));

      assertSame(original, StallTestSupport.failureOf(
         harness.submit(() -> lens.moreRows(TableLens.EOT)), ACTIVE_CAP));
      assertFalse(rowTable(lens).isCompleted(),
                  "a batch that failed in the base read must not complete the row table");

      swapRowTable(lens);
      harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP, "resumed read");
      swapRowTable(lens);

      assertEquals(expected, harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP,
                                           "read after the swaps"));
   }

   /**
    * Control: without an error an end-of-table read completes the row table, and the values
    * survive the swaps.
    */
   @Test
   public void endOfTableReadWithoutErrorCompletesTheLens() throws Exception {
      List<List<Object>> expected = control(GOOD);
      FormulaTableLens lens = harness.track(
         harness.control().formula(new DefaultTableLens(rows(N)), "f", GOOD));

      assertFalse(harness.await(harness.submit(() -> lens.moreRows(TableLens.EOT)), ACTIVE_CAP,
                                "end-of-table read"));
      assertTrue(rowTable(lens).isCompleted(), "a batch that reaches the end completes the lens");

      swapRowTable(lens);
      assertEquals(expected, harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP,
                                           "read after a swap"));
      swapRowTable(lens);
      assertEquals(expected, harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP,
                                           "read after the swaps"));
   }

   /**
    * Control: after a failed end-of-table read without any swap, the resumed read gets every
    * value but the failed row's, and it completes the row table.
    */
   @Test
   public void resumedReadAfterAScriptErrorCompletesTheLens() throws Exception {
      List<List<Object>> expected = control(BAD);
      FormulaTableLens lens = harness.track(
         harness.control().formula(new DefaultTableLens(rows(N)), "f", BAD));

      assertNotNull(StallTestSupport.failureOf(
         harness.submit(() -> lens.moreRows(TableLens.EOT)), ACTIVE_CAP),
         "the end-of-table read must get the script error");

      assertEquals(expected, harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP,
                                           "resumed read"));
      assertTrue(rowTable(lens).isCompleted(),
                 "the resumed read that reaches the end completes the row table");
      assertEquals(N + 1, lens.getRowCount());
   }

   /**
    * Control: a script error in an in-range read never completed the row table, and the
    * values survive the swaps.
    */
   @Test
   public void inRangeScriptErrorSurvivesSwaps() throws Exception {
      assertSurvivesSwaps(20);
   }

   /**
    * After a failed end-of-table read and a refused swap, the resumed read that reaches the
    * end completes the row table, so it is swappable again (not left open for good), and the
    * values survive the swap after it.
    */
   @Test
   public void resumedReadAfterAFailureMakesTheRowTableSwappableAgain() throws Exception {
      List<List<Object>> expected = control(BAD);
      FormulaTableLens lens = harness.track(
         harness.control().formula(new DefaultTableLens(rows(N)), "f", BAD));

      assertNotNull(StallTestSupport.failureOf(
         harness.submit(() -> lens.moreRows(TableLens.EOT)), ACTIVE_CAP));
      assertEquals(0, swapRowTable(lens), "the open row table of a failed batch is not swapped");

      assertEquals(expected, harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP,
                                           "resumed read"));
      assertTrue(rowTable(lens).isCompleted(), "the resumed read completes the row table");
      assertEquals(1, swapRowTable(lens), "the completed row table is swappable again");
      assertEquals(expected, harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP,
                                           "read after the swap"));
   }

   /**
    * More rows than one row table fragment: a script error in the first or in the last
    * fragment, with swaps around the resumed read, loses no value.
    */
   @Test
   public void multiFragmentScriptErrorSurvivesSwaps() throws Exception {
      for(int failedRow : new int[] { FAILED_ROW, BIG - 500 }) {
         List<List<Object>> expected = control(BIG, failedRow);
         FormulaTableLens lens = harness.track(
            harness.control().formula(new DefaultTableLens(rows(BIG)), "f", badAt(failedRow)));

         assertNotNull(StallTestSupport.failureOf(
            harness.submit(() -> lens.moreRows(TableLens.EOT)), ACTIVE_CAP));
         assertFalse(rowTable(lens).isCompleted(), "failed row " + failedRow);

         swapRowTable(lens);
         harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP, "resumed read");
         assertTrue(rowTable(lens).isCompleted(), "failed row " + failedRow);
         swapRowTable(lens);

         assertEquals(expected, harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP,
                                              "read after the swaps"), "failed row " + failedRow);
      }
   }

   /**
    * Unchanged: the end-of-table read of a cancelled lens still completes the row table.
    */
   @Test
   public void cancelledEndOfTableReadStillCompletesTheLens() throws Exception {
      FormulaTableLens lens = harness.track(
         harness.control().formula(new DefaultTableLens(rows(N)), "f", GOOD));

      assertTrue(harness.await(harness.submit(() -> lens.moreRows(20)), ACTIVE_CAP,
                               "in-range read"));
      assertFalse(rowTable(lens).isCompleted());
      lens.cancel();
      assertTrue(lens.isCancelled());

      assertFalse(harness.await(harness.submit(() -> lens.moreRows(TableLens.EOT)), ACTIVE_CAP,
                                "end-of-table read"));
      assertTrue(rowTable(lens).isCompleted(), "a cancelled batch still completes the lens");
   }

   /**
    * The pool on ({@code script.ws.contextPool=true}): the same script error leaves the row
    * table open, and the values survive the swaps around the resumed read.
    */
   @Test
   public void poolOnScriptErrorDoesNotCompleteTheLens() throws Exception {
      WorksheetScriptEnv env = PoolTestSupport.env();

      try {
         List<List<Object>> expected = harness.await(harness.submit(() -> drain(
            new FormulaTableLens(new DefaultTableLens(rows(N)), new String[] { "f" },
                                 new String[] { GOOD }, env, null))), ACTIVE_CAP, "control");
         expected.get(FAILED_ROW).set(2, null);
         FormulaTableLens lens = harness.track(new FormulaTableLens(
            new DefaultTableLens(rows(N)), new String[] { "f" }, new String[] { BAD }, env, null));

         Throwable failure = StallTestSupport.failureOf(
            harness.submit(() -> lens.moreRows(TableLens.EOT)), ACTIVE_CAP);
         assertInstanceOf(ExpressionFailedException.class, failure);
         assertFalse(rowTable(lens).isCompleted(),
                     "a pooled batch that failed must not complete the row table");

         swapRowTable(lens);
         harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP, "resumed read");
         assertTrue(rowTable(lens).isCompleted());
         swapRowTable(lens);

         assertEquals(expected, harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP,
                                              "read after the swaps"));
      }
      finally {
         env.retire();
      }
   }

   /**
    * Unchanged: the script error reaches the caller as an ExpressionFailedException carrying
    * the script's own error, and an Error out of the base reaches it as the same object.
    */
   @Test
   public void failureReachesTheCallerUnchanged() throws Exception {
      FormulaTableLens lens = harness.track(
         harness.control().formula(new DefaultTableLens(rows(N)), "f", BAD));
      Throwable failure = StallTestSupport.failureOf(
         harness.submit(() -> lens.moreRows(TableLens.EOT)), ACTIVE_CAP);

      assertInstanceOf(ExpressionFailedException.class, failure);
      assertTrue(String.valueOf(failure.getMessage()).contains("bad row")
                    || String.valueOf(failure.getCause()).contains("bad row"),
                 "the script's own error is kept: " + failure);

      AssertionError original = new AssertionError("base failed");
      Set<Integer> thrown = ConcurrentHashMap.newKeySet();
      TableLens base = new DefaultTableLens(rows(N)) {
         @Override
         public boolean moreRows(int r) {
            if(r == 15 && thrown.add(r)) {
               throw original;
            }

            return super.moreRows(r);
         }
      };
      FormulaTableLens lens2 = harness.track(harness.control().formula(base, "f", GOOD));

      assertSame(original, StallTestSupport.failureOf(
         harness.submit(() -> lens2.moreRows(TableLens.EOT)), ACTIVE_CAP));
      assertFalse(rowTable(lens2).isCompleted(), "an Error ends the batch as a failure too");
   }

   private void assertSurvivesSwaps(int firstRead) throws Exception {
      List<List<Object>> expected = control(BAD);
      FormulaTableLens lens = harness.track(
         harness.control().formula(new DefaultTableLens(rows(N)), "f", BAD));

      assertNotNull(StallTestSupport.failureOf(
         harness.submit(() -> lens.moreRows(firstRead)), ACTIVE_CAP),
         "the first read must get the script error");
      assertFalse(rowTable(lens).isCompleted(),
                  "a batch that failed must not complete the lens's row table");

      swapRowTable(lens);
      harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP, "resumed read");
      swapRowTable(lens);

      assertEquals(expected, harness.await(harness.submit(() -> drain(lens)), ACTIVE_CAP,
                                           "read after the swaps"),
                   "the formula values survive the swaps around the resumed read");
   }

   /**
    * The rows of a lens with {@code expr} read without any error or swap; for {@link #BAD}
    * the failed row's formula cell is null.
    */
   private List<List<Object>> control(String expr) throws Exception {
      Sandbox s = harness.control();
      List<List<Object>> expected = harness.await(
         harness.submit(() -> drain(s.formula(new DefaultTableLens(rows(N)), "f", GOOD))),
         ACTIVE_CAP, "control");

      if(BAD.equals(expr)) {
         expected.get(FAILED_ROW).set(2, null);
      }

      return expected;
   }

   /**
    * The rows of an {@code n}-row lens whose script fails for {@code failedRow} only.
    */
   private List<List<Object>> control(int n, int failedRow) throws Exception {
      Sandbox s = harness.control();
      List<List<Object>> expected = harness.await(
         harness.submit(() -> drain(s.formula(new DefaultTableLens(rows(n)), "f", GOOD))),
         ACTIVE_CAP, "control");
      expected.get(failedRow).set(2, null);
      return expected;
   }

   private static String badAt(int row) {
      return "if(field['value'] == " + row + ") throw new Error('bad row'); field['value'] + 1";
   }

   private static XSwappableTable rowTable(FormulaTableLens lens) throws Exception {
      Field field = FormulaTableLens.class.getDeclaredField("rows");
      field.setAccessible(true);
      return (XSwappableTable) field.get(lens);
   }

   /**
    * Swap every swappable fragment of the lens's row table, as the swapper does under
    * memory pressure.
    */
   private static int swapRowTable(FormulaTableLens lens) throws Exception {
      Field field = XSwappableTable.class.getDeclaredField("tables");
      field.setAccessible(true);
      int swapped = 0;

      for(Object fragment : (Object[]) field.get(rowTable(lens))) {
         if(fragment != null && ((XTableFragment) fragment).swap(false)) {
            swapped++;
         }
      }

      return swapped;
   }

   private static Object[][] rows(int n) {
      Object[][] data = new Object[n + 1][];
      data[0] = new Object[] { "key", "value" };

      for(int i = 1; i <= n; i++) {
         data[i] = new Object[] { "k" + (i % 3), i };
      }

      return data;
   }

   private static final int N = 40;
   private static final int FAILED_ROW = 15;
   // more rows than one row table fragment (8192 rows)
   private static final int BIG = 9000;
   // the script fails for exactly one data row, as a data-dependent error does
   private static final String BAD =
      "if(field['value'] == " + FAILED_ROW + ") throw new Error('bad row'); field['value'] + 1";
   private static final String GOOD = "field['value'] + 1";

   @TempDir
   File dumpDir;
   private LockCycleHarness harness;
}
