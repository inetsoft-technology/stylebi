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

   private static final int[] DISTINCT_COLS = { 0, 1 };
   private static final int[] DISTINCT_COLS_SINGLE = { 0 };

   private static final Object[][] DATA = {
      { "key", "value" }, { "b", 1 }, { "a", 2 }, { "c", 3 }, { "a", 4 }, { "b", 5 }, { "d", 6 }
   };

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
