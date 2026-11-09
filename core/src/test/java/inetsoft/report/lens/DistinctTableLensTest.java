/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.lens;

import inetsoft.report.TableFilter;
import inetsoft.report.TableLens;
import inetsoft.report.filter.SortFilter;
import inetsoft.report.filter.SortedTable;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.util.swap.SwapFileReadException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static inetsoft.util.swap.SwapLostTestSupport.assertSwapOf;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class DistinctTableLensTest {
   @Test
   public void testSerialize() throws Exception {
      DistinctTableLens originalTable = new DistinctTableLens(XTableUtil.getDefaultTableLens());
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(DistinctTableLens.class, deserializedTable.getClass());
   }

   /**
    * Common case: the base is not already sorted on the distinct columns, so
    * {@code sortDistinct} reads through a nested {@code SortFilter}. A swap file read failure
    * of the base escapes instead of silently leaving the distinct table looking complete and
    * empty (bug #77651).
    */
   @Test
   public void baseSwapFileReadFailureEscapesSortDistinctCommonCase() {
      SwapFileReadException failure =
         new SwapFileReadException(new File("does-not-exist.dat"), new IOException("gone"));
      FailingBase base = new FailingBase(failure, 3);
      DistinctTableLens lens = new DistinctTableLens(base, DISTINCT_COLS, false);

      assertSwapOf(failure, Assertions.assertThrows(
         SwapFileReadException.class, () -> lens.moreRows(XTable.EOT)));
   }

   /**
    * Sub-case: the base is already a compatible sorted table/filter on the same distinct
    * columns, so {@code sortDistinct0} reads the base directly with no nested
    * {@code SortFilter} in between. A swap file read failure of the base still escapes
    * instead of silently leaving the distinct table looking complete with only the rows
    * found before the failure (bug #77651).
    */
   @Test
   public void baseSwapFileReadFailureEscapesSortDistinctPresortedBase() {
      SwapFileReadException failure =
         new SwapFileReadException(new File("does-not-exist.dat"), new IOException("gone"));
      FailingSortedBase base = new FailingSortedBase(failure, 3);
      DistinctTableLens lens = new DistinctTableLens(base, DISTINCT_COLS, false);

      assertSwapOf(failure, Assertions.assertThrows(
         SwapFileReadException.class, () -> lens.moreRows(XTable.EOT)));
   }

   /**
    * Sibling path: a single distinct column (or {@code stable=true}) dispatches to
    * {@code hashDistinct} instead of {@code sortDistinct}. A swap file read failure of the
    * base escapes there too, instead of silently leaving the distinct table looking complete
    * with only the rows found before the failure (bug #77651).
    */
   @Test
   public void baseSwapFileReadFailureEscapesHashDistinct() {
      SwapFileReadException failure =
         new SwapFileReadException(new File("does-not-exist.dat"), new IOException("gone"));
      FailingBase base = new FailingBase(failure, 3);
      DistinctTableLens lens = new DistinctTableLens(base, DISTINCT_COLS_SINGLE, false);

      assertSwapOf(failure, Assertions.assertThrows(
         SwapFileReadException.class, () -> lens.moreRows(XTable.EOT)));
   }

   /**
    * A reader waiting for the worker's rows keeps waiting for the whole table when it is
    * interrupted, but must not lose the interrupt (bug #78135).
    */
   @Test
   public void readerInterruptIsKept() throws Exception {
      SlowBase base = new SlowBase(100);
      DistinctTableLens lens = new DistinctTableLens(base, DISTINCT_COLS_SINGLE, false);
      AtomicInteger rows = new AtomicInteger();
      AtomicBoolean interrupted = new AtomicBoolean();
      Thread reader = new Thread(() -> {
         lens.moreRows(XTable.EOT);
         interrupted.set(Thread.currentThread().isInterrupted());
         rows.set(lens.getRowCount());
      }, "distinct-78135-reader");
      base.reader = reader;
      reader.setDaemon(true);
      reader.start();

      Thread.sleep(300);
      reader.interrupt();
      reader.join(TimeUnit.SECONDS.toMillis(60));

      Assertions.assertFalse(reader.isAlive(), "the reader never returned");
      Assertions.assertEquals(101, rows.get(), "the distinct table must be complete");

      for(int i = 1; i <= 100; i++) {
         Assertions.assertEquals(i, lens.getObject(i, 0));
      }

      Assertions.assertTrue(interrupted.get(), "the reader's interrupt was lost");
   }

   /**
    * The base is reused only when its leading sort columns are exactly the distinct columns
    * (in any order). The base is sorted descending while the distinct sort filter sorts
    * ascending, so the output order shows whether the base was reused (bug #80695).
    */
   @Test
   public void reusesBaseSortedOnDistinctColumnsInAnyOrder() {
      // reused: rows in base (descending) order, adjacent duplicates removed
      Assertions.assertEquals(List.of("2,2", "2,1", "1,2", "1,1"), distinctOf(0, 1));
      Assertions.assertEquals(List.of("2,2", "1,2", "2,1", "1,1"), distinctOf(1, 0));
      Assertions.assertEquals(
         List.of("2,2,2", "2,2,1", "2,1,2", "2,1,1", "1,2,2", "1,1,2", "1,1,1"),
         distinctOf(0, 1, 2));
      Assertions.assertEquals(
         List.of("2,2,2", "1,2,2", "2,1,2", "1,1,2", "2,2,1", "2,1,1", "1,1,1"),
         distinctOf(2, 1, 0));
   }

   /**
    * Distinct columns that are not the leading sort columns of the base are not adjacent in
    * the base (another sort column comes between them), so the base must not be reused
    * (bug #80695).
    */
   @Test
   public void doesNotReuseBaseWhenDistinctColumnsAreNotTheLeadingSortColumns() {
      // not reused: sorted ascending on the distinct columns, each value once
      Assertions.assertEquals(List.of("1,1", "1,2", "2,1", "2,2"), distinctOf(0, 2));
      Assertions.assertEquals(List.of("1,1", "1,2", "2,1", "2,2"), distinctOf(1, 2));
   }

   /**
    * The reuse decision itself: the base instance is returned only when its leading sort
    * columns are exactly the distinct columns (bug #80695).
    */
   @Test
   public void createSortedTableReturnsBaseOnlyWhenSortedOnDistinctColumns() {
      int[][] reuse = { { 0, 1 }, { 1, 0 }, { 0, 1, 2 }, { 2, 1, 0 } };
      int[][] noReuse = { { 0, 2 }, { 1, 2 }, { 0, 2, 2 } };

      for(int[] cols : reuse) {
         SortFilter base = presorted();
         Assertions.assertSame(base, new DistinctTableLens(base, cols, false).createSortedTable(),
                               "must reuse the base for " + java.util.Arrays.toString(cols));
      }

      for(int[] cols : noReuse) {
         SortFilter base = presorted();
         Assertions.assertNotSame(base,
                                  new DistinctTableLens(base, cols, false).createSortedTable(),
                                  "must not reuse the base for " + java.util.Arrays.toString(cols));
      }
   }

   /**
    * Duplicated distinct columns (a,c,c) of a base sorted on (a,b,c) are not the leading sort
    * columns, so the base is not reused and each (a,c) value is returned once (bug #80695).
    */
   @Test
   public void doesNotReuseBaseForDuplicatedDistinctColumns() {
      Assertions.assertEquals(List.of("1,1,1", "1,2,2", "2,1,1", "2,2,2"), distinctOf(0, 2, 2));
   }

   @Test
   public void isSortedOnRequiresTwoWaySetEqualityOfTheLeadingSortColumns() {
      int[] abc = { 0, 1, 2 };

      Assertions.assertTrue(DistinctTableLens.isSortedOn(abc, new int[] { 0, 1 }));
      Assertions.assertTrue(DistinctTableLens.isSortedOn(abc, new int[] { 1, 0 }));
      Assertions.assertTrue(DistinctTableLens.isSortedOn(abc, new int[] { 2, 1, 0 }));
      Assertions.assertFalse(DistinctTableLens.isSortedOn(abc, new int[] { 0, 2 }));
      Assertions.assertFalse(DistinctTableLens.isSortedOn(abc, new int[] { 1, 2 }));
      Assertions.assertFalse(DistinctTableLens.isSortedOn(abc, new int[] { 0, 2, 2 }));
      Assertions.assertFalse(DistinctTableLens.isSortedOn(new int[] { 0 }, new int[] { 0, 1 }));
      Assertions.assertFalse(DistinctTableLens.isSortedOn(null, new int[] { 0, 1 }));
      // no distinct column: unchanged, the base is reused
      Assertions.assertTrue(DistinctTableLens.isSortedOn(new int[0], new int[0]));
   }

   private static List<String> distinctOf(int... cols) {
      DistinctTableLens lens =
         new DistinctTableLens(presorted(), cols, false);
      List<String> rows = new ArrayList<>();
      lens.moreRows(XTable.EOT);

      for(int r = lens.getHeaderRowCount(); lens.moreRows(r); r++) {
         StringBuilder row = new StringBuilder();

         for(int i = 0; i < cols.length; i++) {
            row.append(i == 0 ? "" : ",").append(lens.getObject(r, cols[i]));
         }

         rows.add(row.toString());
      }

      return rows;
   }

   /**
    * A base sorted on (a,b,c) descending, while the sort filter of a distinct table that
    * does not reuse its base sorts ascending.
    */
   private static SortFilter presorted() {
      return new SortFilter(new DefaultTableLens(UNSORTED_DATA), new int[] { 0, 1, 2 }, false);
   }

   /**
    * Sorted on (a,b,c) descending: (2,2,2) (2,2,2) (2,2,1) (2,1,2) (2,1,1) (1,2,2) (1,1,2)
    * (1,1,1).
    */
   private static final Object[][] UNSORTED_DATA = {
      { "a", "b", "c" },
      { 1, 1, 2 }, { 2, 2, 1 }, { 2, 1, 1 }, { 2, 2, 2 }, { 1, 1, 1 },
      { 2, 1, 2 }, { 1, 2, 2 }, { 2, 2, 2 }
   };

   private static final int[] DISTINCT_COLS = { 0, 1 };
   private static final int[] DISTINCT_COLS_SINGLE = { 0 };

   private static final Object[][] DATA = {
      { "key", "value" }, { "b", 1 }, { "a", 2 }, { "c", 3 }, { "a", 4 }, { "b", 5 }, { "d", 6 }
   };

   /**
    * A base with distinct values 1..rows in column 0 that the distinct worker reads slowly:
    * 20 ms a row on any thread but the reader and the test thread.
    */
   private static final class SlowBase extends DefaultTableLens {
      SlowBase(int rows) {
         super(rows + 1, 1);
         setHeaderRowCount(1);
         setObject(0, 0, "id");

         for(int i = 1; i <= rows; i++) {
            setObject(i, 0, i);
         }

         owner = Thread.currentThread();
      }

      @Override
      public Object getObject(int r, int c) {
         Thread thread = Thread.currentThread();

         if(r > 0 && thread != owner && thread != reader) {
            try {
               Thread.sleep(20);
            }
            catch(InterruptedException ex) {
               thread.interrupt();
            }
         }

         return super.getObject(r, c);
      }

      private final Thread owner;
      volatile Thread reader;
   }

   /**
    * A base whose data rows from {@code failAtRow} on fail with {@code failure}.
    */
   private static class FailingBase extends DefaultTableLens {
      FailingBase(RuntimeException failure, int failAtRow) {
         super(DATA);
         this.failure = failure;
         this.failAtRow = failAtRow;
      }

      @Override
      public boolean moreRows(int row) {
         if(row >= failAtRow) {
            throw failure;
         }

         return super.moreRows(row);
      }

      private final RuntimeException failure;
      private final int failAtRow;
   }

   /**
    * A {@link FailingBase} that also reports itself as already sorted on the distinct
    * columns, so {@code DistinctTableLens.createSortedTable()} reuses it directly instead of
    * wrapping it in a new {@code SortFilter}.
    */
   private static final class FailingSortedBase extends FailingBase implements SortedTable, TableFilter {
      FailingSortedBase(RuntimeException failure, int failAtRow) {
         super(failure, failAtRow);
      }

      @Override
      public int[] getSortCols() {
         return DISTINCT_COLS;
      }

      @Override
      public boolean[] getOrders() {
         return new boolean[] { true, true };
      }

      @Override
      public void setComparer(int col, inetsoft.report.Comparer comp) {
      }

      @Override
      public inetsoft.report.Comparer getComparer(int col) {
         return null;
      }

      @Override
      public TableLens getTable() {
         return this;
      }

      @Override
      public void setTable(TableLens table) {
      }

      @Override
      public void invalidate() {
      }

      @Override
      public int getBaseRowIndex(int row) {
         return row;
      }

      @Override
      public int getBaseColIndex(int col) {
         return col;
      }
   }
}
