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

import static inetsoft.util.swap.SwapLostTestSupport.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class RankingTableLensTest {
   @Test
   public void testSerialize() throws Exception {
      RankingTableLens originalTable = new RankingTableLens(XTableUtil.getDefaultTableLens());
      originalTable.setRankingN(1);
      originalTable.setRankingColumn(2);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(RankingTableLens.class, deserializedTable.getClass());
   }

   /**
    * A lock stall of the base while ranking reaches the reader, it is never an empty or
    * unranked table (bug #76967); once the base recovers, the next read ranks it in full.
    */
   @Test
   public void baseStallWhileRankingReachesTheReader() {
      LockStallException stall = new LockStallException("nested.site", "worker", 1234, null);
      FailingBase base = new FailingBase(stall);
      RankingTableLens ranking = ranking(base);

      Assertions.assertSame(stall, Assertions.assertThrows(
         LockStallException.class, () -> ranking.moreRows(1)));

      base.failure = null;
      RankingTableLens control = ranking(new FailingBase(null));
      Assertions.assertEquals(3, ranking.getRowCount(), "the header and the top 2");

      for(int r = 1; r < 3; r++) {
         Assertions.assertEquals(control.getObject(r, 1), ranking.getObject(r, 1),
                                 "row " + r + " ranked in full after the stall");
      }
   }

   /**
    * A wrapped lock stall is found in the cause chain.
    */
   @Test
   public void wrappedBaseStallWhileRankingReachesTheReader() {
      LockStallException stall = new LockStallException("nested.site", "worker", 1234, null);
      RankingTableLens ranking = ranking(new FailingBase(new RuntimeException("wrapped", stall)));

      Assertions.assertSame(stall, Assertions.assertThrows(
         LockStallException.class, () -> ranking.moreRows(1)));
   }

   /**
    * Any other failure while ranking is logged and the table has no data rows, as before.
    */
   @Test
   public void baseFailureWhileRankingIsLoggedNotThrown() {
      RankingTableLens ranking = ranking(new FailingBase(new IllegalStateException("failed")));

      Assertions.assertFalse(ranking.moreRows(1));
      Assertions.assertEquals(1, ranking.getRowCount());
   }

   /**
    * A lost swap file of the base while ranking fails the read, it is never a table without
    * data rows (bug #77651). The ranking is not kept, so the next read fails too while the
    * file is lost.
    */
   @Test
   public void lostSwapFileWhileRankingFailsEveryRead() throws Exception {
      SwapFileReadException lost = swapLost();
      RankingTableLens ranking = ranking(new LostTable(values(40), 21, lost));

      Assertions.assertSame(lost, swapIn(failureOf(15, ranking::getRowCount)), "first read");
      Assertions.assertSame(lost, swapIn(failureOf(15, ranking::getRowCount)), "second read");
   }

   /**
    * A wrapped lost swap file is found in the cause chain.
    */
   @Test
   public void wrappedLostSwapFileWhileRankingFailsTheRead() throws Exception {
      SwapFileReadException lost = swapLost();
      RankingTableLens ranking =
         ranking(new LostTable(values(40), 21, new RuntimeException("wrapped", lost)));

      Assertions.assertSame(lost, swapIn(failureOf(15, () -> ranking.moreRows(1))));
   }

   /**
    * {@code rows} rows of {@code key, value}, value {@code i} for row {@code i}.
    */
   private static Object[][] values(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "key", "value" };

      for(int r = 1; r <= rows; r++) {
         data[r] = new Object[] { "k" + r, r };
      }

      return data;
   }

   private static RankingTableLens ranking(DefaultTableLens base) {
      RankingTableLens ranking = new RankingTableLens(base);
      ranking.setRankingColumn(1);
      ranking.setRankingN(2);
      return ranking;
   }

   /**
    * Five rows whose value cells fail with {@code failure} while it is set, i.e. while the
    * ranking compares them.
    */
   private static final class FailingBase extends DefaultTableLens {
      FailingBase(RuntimeException failure) {
         super(new Object[][] { { "key", "value" }, { "a", 3 }, { "b", 5 }, { "c", 1 },
                                { "d", 4 }, { "e", 2 } });
         this.failure = failure;
      }

      @Override
      public Object getObject(int r, int c) {
         RuntimeException failure = this.failure;

         if(failure != null && r >= 1 && c == 1) {
            throw failure;
         }

         return super.getObject(r, c);
      }

      volatile RuntimeException failure;
   }
}
