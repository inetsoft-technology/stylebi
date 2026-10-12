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

import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.util.stall.LockStallException;
import inetsoft.util.swap.SwapFileReadException;
import inetsoft.util.swap.SwapLostTestSupport.LostTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import static inetsoft.util.swap.SwapLostTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class SelfJoinTableLensTest {
   @Test
   public void testSerialize() throws Exception {
      SelfJoinTableLens originalTable = new SelfJoinTableLens(XTableUtil.getDefaultTableLens());
      originalTable.addJoin(1, SelfJoinTableLens.NOT_EQUAL_JOIN, 2);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(SelfJoinTableLens.class, deserializedTable.getClass());
   }

   /**
    * A swap file read failure of the base escapes the join instead of silently leaving the
    * join looking complete with only the matches found before the failure (bug #77651).
    */
   @Test
   public void baseSwapFileReadFailureEscapesTheJoin() {
      SwapFileReadException failure =
         new SwapFileReadException(new File("does-not-exist.dat"), new IOException("gone"));
      FailingBase base = new FailingBase(failure, 5);
      SelfJoinTableLens lens = new SelfJoinTableLens(base);
      lens.addJoin(0, SelfJoinTableLens.INNER_JOIN, 1);

      assertSwapOf(failure, Assertions.assertThrows(
         SwapFileReadException.class, () -> lens.moreRows(XTable.EOT)));
   }

   /**
    * A lost swap file of the base fails the cell reads of the join, not its moreRows. It
    * fails the read of the join instead of leaving the join with only the rows that matched
    * before or between the lost rows (bug #77651).
    */
   @Test
   public void lostSwapFileOfTheBaseCellsFailsTheRead() throws Exception {
      SwapFileReadException lost = swapLost();
      SelfJoinTableLens lens = selfJoin(new LostTable(ids(40), 21, lost));

      assertSwapOf(lost, swapIn(failureOf(15, () -> drain(lens))));
   }

   /**
    * A lost swap file wrapped in another exception is found in the cause chain.
    */
   @Test
   public void wrappedLostSwapFileOfTheBaseCellsFailsTheRead() throws Exception {
      SwapFileReadException lost = swapLost();
      SelfJoinTableLens lens =
         selfJoin(new LostTable(ids(40), 21, new RuntimeException("wrapped", lost)));

      assertSwapOf(lost, swapIn(failureOf(15, () -> drain(lens))));
   }

   /**
    * A lock stall of a base cell read fails the read of the join, it does not drop the row
    * from the join (bug #76967).
    */
   @Test
   public void stallOfTheBaseCellsFailsTheRead() throws Exception {
      LockStallException stall = new LockStallException("nested.site", "worker", 1234, null);
      SelfJoinTableLens lens = selfJoin(new LostTable(ids(40), 21, stall));
      Throwable failure = failureOf(15, () -> drain(lens));

      assertNotNull(LockStallException.find(failure), "not a lock stall: " + failure);
   }

   /**
    * A wrapped lock stall of a base cell read is found in the cause chain.
    */
   @Test
   public void wrappedStallOfTheBaseCellsFailsTheRead() throws Exception {
      LockStallException stall = new LockStallException("nested.site", "worker", 1234, null);
      SelfJoinTableLens lens =
         selfJoin(new LostTable(ids(40), 21, new RuntimeException("wrapped", stall)));
      Throwable failure = failureOf(15, () -> drain(lens));

      assertNotNull(LockStallException.find(failure), "not a lock stall: " + failure);
   }

   /**
    * A value of another type in a join column still only drops its row from the join, the
    * read does not fail.
    */
   @Test
   public void mixedTypeRowIsDroppedWithoutFailingTheRead() throws Exception {
      Object[][] data = new Object[6][];
      data[0] = new Object[] { "date1", "date2" };

      for(int r = 1; r < data.length; r++) {
         Date date = new Date(86_400_000L * r);
         data[r] = new Object[] { date, date };
      }

      data[3][0] = "bad";
      SelfJoinTableLens lens = selfJoin(new DefaultTableLens(data));
      List<List<Object>> rows = within(15, () -> drain(lens));

      assertEquals(5, rows.size(), "the header and the 4 rows without the bad value");
      assertFalse(rows.stream().anyMatch(row -> "bad".equals(row.get(0))));
   }

   private static SelfJoinTableLens selfJoin(DefaultTableLens base) {
      SelfJoinTableLens lens = new SelfJoinTableLens(base);
      lens.addJoin(0, SelfJoinTableLens.INNER_JOIN, 1);
      return lens;
   }

   /**
    * {@code rows} rows of {@code id, id2}, both {@code i} for row {@code i}.
    */
   private static Object[][] ids(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "id", "id2" };

      for(int r = 1; r <= rows; r++) {
         data[r] = new Object[] { r, r };
      }

      return data;
   }

   /**
    * A base whose data rows from {@code failAtRow} on fail with {@code failure}. Rows before
    * that match the join (col0 == col1).
    */
   private static final class FailingBase extends DefaultTableLens {
      FailingBase(RuntimeException failure, int failAtRow) {
         super(new Object[][] {
            { "id", "value" }, { 1, 1 }, { 2, 2 }, { 3, 3 }, { 4, 4 }, { 5, 5 }, { 6, 6 } });
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
}
