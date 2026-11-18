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
package inetsoft.report.lens;

import inetsoft.report.TableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81091: the sanity checks of the JoinTable constructor completed the table and returned,
 * but that only left the abstract constructor: HashJoinTable and MergeJoinTable went on to
 * start their scanners (zero key columns joined every row with every row into the completed
 * table) or threw (a key index past the column count, in the merge engine). Every invalid input
 * must leave an empty table for both engines, with no join thread started and no exception.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class JoinTableInvalidColumnsTest {
   private static final int ROWS = 50;

   @AfterEach
   void resetForceHash() {
      SreeEnv.remove("join.table.forceHash");
   }

   @Test
   void zeroKeyColumnsHash() {
      assertEmpty(hash(new int[0], new int[0]));
   }

   @Test
   void zeroKeyColumnsMerge() {
      assertEmpty(merge(new int[0], new int[0]));
   }

   @Test
   void mismatchedKeyCountHash() {
      assertEmpty(hash(new int[] { 0, 1 }, new int[] { 0 }));
   }

   @Test
   void mismatchedKeyCountMerge() {
      assertEmpty(merge(new int[] { 0, 1 }, new int[] { 0 }));
   }

   @Test
   void leftIndexPastColumnsHash() {
      assertEmpty(hash(new int[] { 2 }, new int[] { 0 }));
   }

   @Test
   void leftIndexPastColumnsMerge() {
      assertEmpty(merge(new int[] { 2 }, new int[] { 0 }));
   }

   @Test
   void rightIndexPastColumnsHash() {
      assertEmpty(hash(new int[] { 0 }, new int[] { 5 }));
   }

   @Test
   void rightIndexPastColumnsMerge() {
      assertEmpty(merge(new int[] { 0 }, new int[] { 5 }));
   }

   @Test
   void negativeIndexHash() {
      assertEmpty(hash(new int[] { -1 }, new int[] { 0 }));
   }

   @Test
   void negativeIndexMerge() {
      assertEmpty(merge(new int[] { 0 }, new int[] { -1 }));
   }

   @Test
   void lensZeroKeyColumnsBothEngines() {
      for(String forceHash : new String[] { "true", "false" }) {
         SreeEnv.setProperty("join.table.forceHash", forceHash);
         JoinTableLens lens = new JoinTableLens(base(), base(), new int[0], new int[0]);
         lens.moreRows(XTable.EOT);
         assertEquals(0, lens.getRowCount(), "forceHash=" + forceHash);
      }
   }

   @Test
   void lensBadIndexBothEngines() {
      for(String forceHash : new String[] { "true", "false" }) {
         SreeEnv.setProperty("join.table.forceHash", forceHash);
         JoinTableLens lens = assertDoesNotThrow(
            () -> new JoinTableLens(base(), base(), new int[] { 7 }, new int[] { 0 }),
            "forceHash=" + forceHash);
         lens.moreRows(XTable.EOT);
         assertEquals(0, lens.getRowCount(), "forceHash=" + forceHash);
      }
   }

   private static JoinTable hash(int[] leftCols, int[] rightCols) {
      return new HashJoinTable(base(), base(), leftCols, rightCols, JoinTableLens.INNER_JOIN,
                               true, Integer.MAX_VALUE);
   }

   private static JoinTable merge(int[] leftCols, int[] rightCols) {
      return new MergeJoinTable(base(), base(), leftCols, rightCols, JoinTableLens.INNER_JOIN,
                                true, Integer.MAX_VALUE);
   }

   private static void assertEmpty(JoinTable join) {
      // no scanner may have been created
      for(Thread thread : join.getWorkerThreads()) {
         assertNull(thread, "a join thread was started for invalid join columns");
      }

      join.moreRows(XTable.EOT);
      assertEquals(0, join.getRowCount());
   }

   private static TableLens base() {
      DefaultTableLens base = new DefaultTableLens(ROWS + 1, 2);
      base.setHeaderRowCount(1);
      base.setObject(0, 0, "id");
      base.setObject(0, 1, "k");

      for(int i = 1; i <= ROWS; i++) {
         base.setObject(i, 0, i);
         base.setObject(i, 1, i % 2);
      }

      return base;
   }
}
