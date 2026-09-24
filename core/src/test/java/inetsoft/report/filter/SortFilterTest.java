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

package inetsoft.report.filter;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.util.stall.LockStallException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class SortFilterTest {
   @Test
   public void testSerialize() throws Exception {
      int[] sortCols = new int[] { 1 };
      SortFilter originalTable = new SortFilter(XTableUtil.getDefaultTableLens(),
                                                sortCols, true);

      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(SortFilter.class, deserializedTable.getClass());
      SortFilter deserializedFilter = (SortFilter) deserializedTable;
      Assertions.assertArrayEquals(sortCols, deserializedFilter.getSortCols());
      Assertions.assertArrayEquals(originalTable.getOrders(), deserializedFilter.getOrders());
   }

   /**
    * A failure of the base while sorting is logged and the filter has no rows, as before
    * bug #76967.
    */
   @Test
   public void baseFailureIsLoggedNotThrown() {
      SortFilter filter =
         new SortFilter(new FailingBase(new IllegalStateException("base failed")), SORT_COLS, true);

      Assertions.assertFalse(filter.moreRows(1));
   }

   /**
    * A lock stall of the base escapes the sort, it is never the end of the table (bug #76967).
    */
   @Test
   public void baseStallEscapesTheSort() {
      LockStallException stall = new LockStallException("nested.site", "worker", 1234, null);
      SortFilter filter = new SortFilter(new FailingBase(stall), SORT_COLS, true);

      Assertions.assertSame(stall, Assertions.assertThrows(
         LockStallException.class, () -> filter.moreRows(1)));
   }

   @Test
   public void wrappedBaseStallEscapesTheSort() {
      LockStallException stall = new LockStallException("nested.site", "worker", 1234, null);
      SortFilter filter =
         new SortFilter(new FailingBase(new RuntimeException("wrapped", stall)), SORT_COLS, true);

      Assertions.assertSame(stall, Assertions.assertThrows(
         LockStallException.class, () -> filter.moreRows(1)));
   }

   /**
    * A base whose data rows from row 3 on fail with {@code failure}.
    */
   private static final class FailingBase extends DefaultTableLens {
      FailingBase(RuntimeException failure) {
         super(new Object[][] {
            { "key", "value" }, { "b", 1 }, { "a", 2 }, { "c", 3 }, { "a", 4 }, { "b", 5 } });
         this.failure = failure;
      }

      @Override
      public boolean moreRows(int row) {
         if(row >= 3) {
            throw failure;
         }

         return super.moreRows(row);
      }

      private final RuntimeException failure;
   }

   private static final int[] SORT_COLS = { 0 };
}
