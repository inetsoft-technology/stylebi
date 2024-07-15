/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.lens;

import inetsoft.report.internal.Util;
import inetsoft.sree.SreeEnv;
import inetsoft.test.SreeHome;
import inetsoft.test.XTableUtil;
import org.junit.jupiter.api.*;

@SreeHome
class MergeJoinTableTest {
   private static String forceHash;

   @BeforeAll
   static void setProperties() {
      forceHash = SreeEnv.getProperty("join.table.forceHash");
      SreeEnv.setProperty("join.table.forceHash", "false");
   }

   @AfterAll
   static void resetProperties() {
      SreeEnv.setProperty("join.table.forceHash", forceHash);
   }

   @Test
   void testInnerJoinAllMatch() {
      testJoin(new Object[][]{
                  { "ID", "LEFT_VALUE" },
                  { 1, "LA" },
                  { 2, "LB" },
                  { 3, "LC" },
                  { 4, "LD" }
               },
               new Object[][]{
                  { "ID", "RIGHT_VALUE" },
                  { 1, "RA" },
                  { 2, "RB" },
                  { 3, "RC" },
                  { 4, "RD" }
               },
               new Object[][]{
                  { "ID", "LEFT_VALUE", "ID", "RIGHT_VALUE" },
                  { 1, "LA", 1, "RA" },
                  { 2, "LB", 2, "RB" },
                  { 3, "LC", 3, "RC" },
                  { 4, "LD", 4, "RD" }
               },
               JoinTableLens.INNER_JOIN);
   }

   @Test
   void testInnerJoinWithNull() {
      testJoin(new Object[][]{
                  { "ID", "LEFT_VALUE" },
                  { null, "LNULL" },
                  { 1, "LA" },
                  { 2, "LB" },
                  { 3, "LC" },
                  { 4, "LD" }
               },
               new Object[][]{
                  { "ID", "RIGHT_VALUE" },
                  { null, "RNULL" },
                  { 1, "RA" },
                  { 2, "RB" },
                  { 3, "RC" },
                  { 4, "RD" }
               },
               new Object[][]{
                  { "ID", "LEFT_VALUE", "ID", "RIGHT_VALUE" },
                  { 1, "LA", 1, "RA" },
                  { 2, "LB", 2, "RB" },
                  { 3, "LC", 3, "RC" },
                  { 4, "LD", 4, "RD" }
               },
               JoinTableLens.INNER_JOIN);
   }

   @Test
   void testInnerJoinWithDuplicate() {
      testJoin(new Object[][]{
                  { "ID", "LEFT_VALUE" },
                  { 1, "LA1" },
                  { 1, "LA2" },
                  { 2, "LB" },
                  { 3, "LC" },
                  { 4, "LD" }
               },
               new Object[][]{
                  { "ID", "RIGHT_VALUE" },
                  { 1, "RA" },
                  { 2, "RB1" },
                  { 2, "RB2" },
                  { 3, "RC" },
                  { 4, "RD" }
               },
               new Object[][]{
                  { "ID", "LEFT_VALUE", "ID", "RIGHT_VALUE" },
                  { 1, "LA1", 1, "RA" },
                  { 1, "LA2", 1, "RA" },
                  { 2, "LB", 2, "RB1" },
                  { 2, "LB", 2, "RB2" },
                  { 3, "LC", 3, "RC" },
                  { 4, "LD", 4, "RD" }
               },
               JoinTableLens.INNER_JOIN);
   }

   @Test
   void testInnerJoinNoneMatch() {
      testJoin(new Object[][]{
                  { "ID", "LEFT_VALUE" },
                  { 1, "LA" },
                  { 2, "LB" },
                  { 3, "LC" },
                  { 4, "LD" }
               },
               new Object[][]{
                  { "ID", "RIGHT_VALUE" },
                  { 5, "RA" },
                  { 6, "RB" },
                  { 7, "RC" },
                  { 8, "RD" }
               },
               new Object[][]{
                  { "ID", "LEFT_VALUE", "ID", "RIGHT_VALUE" }
               },
               JoinTableLens.INNER_JOIN);
   }

   @Test
   void testInnerJoinSomeMatch() {
      testJoin(new Object[][]{
                  { "ID", "LEFT_VALUE" },
                  { 1, "LA" },
                  { 2, "LB" },
                  { 3, "LC" },
                  { 4, "LD" }
               },
               new Object[][]{
                  { "ID", "RIGHT_VALUE" },
                  { 1, "RA" },
                  { 5, "RB" },
                  { 6, "RC" },
                  { 4, "RD" }
               },
               new Object[][]{
                  { "ID", "LEFT_VALUE", "ID", "RIGHT_VALUE" },
                  { 1, "LA", 1, "RA" },
                  { 4, "LD", 4, "RD" }
               },
               JoinTableLens.INNER_JOIN);
   }

   @Test
   void testLeftJoinAllMatch() {
      testJoin(new Object[][]{
                  { "ID", "LEFT_VALUE" },
                  { 1, "LA" },
                  { 2, "LB" },
                  { 3, "LC" },
                  { 4, "LD" }
               },
               new Object[][]{
                  { "ID", "RIGHT_VALUE" },
                  { 1, "RA" },
                  { 2, "RB" },
                  { 3, "RC" },
                  { 4, "RD" }
               },
               new Object[][]{
                  { "ID", "LEFT_VALUE", "ID", "RIGHT_VALUE" },
                  { 1, "LA", 1, "RA" },
                  { 2, "LB", 2, "RB" },
                  { 3, "LC", 3, "RC" },
                  { 4, "LD", 4, "RD" }
               },
               JoinTableLens.LEFT_OUTER_JOIN);
   }

   @Test
   void testLeftJoinWithNull() {
      testJoin(new Object[][]{
                  { "ID", "LEFT_VALUE" },
                  { null, "LNULL" },
                  { 1, "LA" },
                  { 2, "LB" },
                  { 3, "LC" },
                  { 4, "LD" }
               },
               new Object[][]{
                  { "ID", "RIGHT_VALUE" },
                  { null, "RNULL" },
                  { 1, "RA" },
                  { 2, "RB" },
                  { 3, "RC" },
                  { 4, "RD" }
               },
               new Object[][]{
                  { "ID", "LEFT_VALUE", "ID", "RIGHT_VALUE" },
                  { null, "LNULL", null, null },
                  { 1, "LA", 1, "RA" },
                  { 2, "LB", 2, "RB" },
                  { 3, "LC", 3, "RC" },
                  { 4, "LD", 4, "RD" }
               },
               JoinTableLens.LEFT_OUTER_JOIN);
   }

   @Test
   void testLeftJoinWithDuplicates() {
      testJoin(new Object[][]{
                  { "ID", "LEFT_VALUE" },
                  { 1, "LA1" },
                  { 1, "LA2" },
                  { 2, "LB" },
                  { 3, "LC" },
                  { 4, "LD" }
               },
               new Object[][]{
                  { "ID", "RIGHT_VALUE" },
                  { 1, "RA" },
                  { 2, "RB" },
                  { 3, "RC" },
                  { 4, "RD" }
               },
               new Object[][]{
                  { "ID", "LEFT_VALUE", "ID", "RIGHT_VALUE" },
                  { 1, "LA1", 1, "RA" },
                  { 1, "LA2", 1, "RA" },
                  { 2, "LB", 2, "RB" },
                  { 3, "LC", 3, "RC" },
                  { 4, "LD", 4, "RD" }
               },
               JoinTableLens.LEFT_OUTER_JOIN);
   }

   @Test
   void testLeftJoinWithUnmatchedDuplicates() {
      testJoin(new Object[][]{
                  { "ID", "LEFT_VALUE" },
                  { 1, "LA1" },
                  { 1, "LA2" },
                  { 2, "LB" },
                  { 3, "LC" },
                  { 4, "LD" }
               },
               new Object[][]{
                  { "ID", "RIGHT_VALUE" },
                  { 5, "RA" },
                  { 2, "RB" },
                  { 3, "RC" },
                  { 4, "RD" }
               },
               new Object[][]{
                  { "ID", "LEFT_VALUE", "ID", "RIGHT_VALUE" },
                  { 1, "LA1", null, null },
                  { 1, "LA2", null, null },
                  { 2, "LB", 2, "RB" },
                  { 3, "LC", 3, "RC" },
                  { 4, "LD", 4, "RD" }
               },
               JoinTableLens.LEFT_OUTER_JOIN);
   }

   @Test
   void testLeftJoinNoneMatch() {
      testJoin(new Object[][]{
                  { "ID", "LEFT_VALUE" },
                  { 1, "LA" },
                  { 2, "LB" },
                  { 3, "LC" },
                  { 4, "LD" }
               },
               new Object[][]{
                  { "ID", "RIGHT_VALUE" },
                  { 5, "RA" },
                  { 6, "RB" },
                  { 7, "RC" },
                  { 8, "RD" }
               },
               new Object[][]{
                  { "ID", "LEFT_VALUE", "ID", "RIGHT_VALUE" },
                  { 1, "LA", null, null },
                  { 2, "LB", null, null },
                  { 3, "LC", null, null },
                  { 4, "LD", null, null }
               },
               JoinTableLens.LEFT_OUTER_JOIN);
   }

   @Test
   void testLeftJoinSomeMatch() {
      testJoin(new Object[][]{
                  { "ID", "LEFT_VALUE" },
                  { 1, "LA" },
                  { 2, "LB" },
                  { 3, "LC" },
                  { 4, "LD" }
               },
               new Object[][]{
                  { "ID", "RIGHT_VALUE" },
                  { 1, "RA" },
                  { 5, "RB" },
                  { 6, "RC" },
                  { 4, "RD" }
               },
               new Object[][]{
                  { "ID", "LEFT_VALUE", "ID", "RIGHT_VALUE" },
                  { 1, "LA", 1, "RA" },
                  { 2, "LB", null, null },
                  { 3, "LC", null, null },
                  { 4, "LD", 4, "RD" }
               },
               JoinTableLens.LEFT_OUTER_JOIN);
   }

   @Test
   void testLeftJoinSomeMatchWithTrailing() {
      testJoin(new Object[][]{
                  { "ID", "LEFT_VALUE" },
                  { 1, "LA" },
                  { 2, "LB" },
                  { 3, "LC" },
                  { 4, "LD" },
                  { 7, "LE" },
                  { 8, "LF" }
               },
               new Object[][]{
                  { "ID", "RIGHT_VALUE" },
                  { 1, "RA" },
                  { 5, "RB" },
                  { 6, "RC" },
                  { 4, "RD" }
               },
               new Object[][]{
                  { "ID", "LEFT_VALUE", "ID", "RIGHT_VALUE" },
                  { 1, "LA", 1, "RA" },
                  { 2, "LB", null, null },
                  { 3, "LC", null, null },
                  { 4, "LD", 4, "RD" },
                  { 7, "LE", null, null },
                  { 8, "LF", null, null }
               },
               JoinTableLens.LEFT_OUTER_JOIN);
   }

   @Test
   void testRightJoinAllMatch() {
      testJoin(new Object[][]{
                  { "ID", "LEFT_VALUE" },
                  { 1, "LA" },
                  { 2, "LB" },
                  { 3, "LC" },
                  { 4, "LD" }
               },
               new Object[][]{
                  { "ID", "RIGHT_VALUE" },
                  { 1, "RA" },
                  { 2, "RB" },
                  { 3, "RC" },
                  { 4, "RD" }
               },
               new Object[][]{
                  { "ID", "LEFT_VALUE", "ID", "RIGHT_VALUE" },
                  { 1, "LA", 1, "RA" },
                  { 2, "LB", 2, "RB" },
                  { 3, "LC", 3, "RC" },
                  { 4, "LD", 4, "RD" }
               },
               JoinTableLens.RIGHT_OUTER_JOIN);
   }

   @Test
   void testRightJoinWithNull() {
      testJoin(new Object[][]{
                  { "ID", "LEFT_VALUE" },
                  { null, "LNULL" },
                  { 1, "LA" },
                  { 2, "LB" },
                  { 3, "LC" },
                  { 4, "LD" }
               },
               new Object[][]{
                  { "ID", "RIGHT_VALUE" },
                  { null, "RNULL" },
                  { 1, "RA" },
                  { 2, "RB" },
                  { 3, "RC" },
                  { 4, "RD" }
               },
               new Object[][]{
                  { "ID", "LEFT_VALUE", "ID", "RIGHT_VALUE" },
                  { null, null, null, "RNULL" },
                  { 1, "LA", 1, "RA" },
                  { 2, "LB", 2, "RB" },
                  { 3, "LC", 3, "RC" },
                  { 4, "LD", 4, "RD" }
               },
               JoinTableLens.RIGHT_OUTER_JOIN);
   }

   @Test
   void testRightJoinWithDuplicates() {
      testJoin(new Object[][]{
                  { "ID", "LEFT_VALUE" },
                  { 1, "LA" },
                  { 2, "LB" },
                  { 3, "LC" },
                  { 4, "LD" }
               },
               new Object[][]{
                  { "ID", "RIGHT_VALUE" },
                  { 1, "RA1" },
                  { 1, "RA2" },
                  { 2, "RB" },
                  { 3, "RC" },
                  { 4, "RD" }
               },
               new Object[][]{
                  { "ID", "LEFT_VALUE", "ID", "RIGHT_VALUE" },
                  { 1, "LA", 1, "RA1" },
                  { 1, "LA", 1, "RA2" },
                  { 2, "LB", 2, "RB" },
                  { 3, "LC", 3, "RC" },
                  { 4, "LD", 4, "RD" }
               },
               JoinTableLens.RIGHT_OUTER_JOIN);
   }

   @Test
   void testRightJoinWithUnmatchedDuplicates() {
      testJoin(new Object[][]{
                  { "ID", "LEFT_VALUE" },
                  { 5, "LA" },
                  { 2, "LB" },
                  { 3, "LC" },
                  { 4, "LD" }
               },
               new Object[][]{
                  { "ID", "RIGHT_VALUE" },
                  { 1, "RA1" },
                  { 1, "RA2" },
                  { 2, "RB" },
                  { 3, "RC" },
                  { 4, "RD" }
               },
               new Object[][]{
                  { "ID", "LEFT_VALUE", "ID", "RIGHT_VALUE" },
                  { null, null, 1, "RA1" },
                  { null, null, 1, "RA2" },
                  { 2, "LB", 2, "RB" },
                  { 3, "LC", 3, "RC" },
                  { 4, "LD", 4, "RD" }
               },
               JoinTableLens.RIGHT_OUTER_JOIN);
   }

   @Test
   void testRightJoinNoneMatch() {
      testJoin(new Object[][]{
                  { "ID", "LEFT_VALUE" },
                  { 1, "LA" },
                  { 2, "LB" },
                  { 3, "LC" },
                  { 4, "LD" }
               },
               new Object[][]{
                  { "ID", "RIGHT_VALUE" },
                  { 5, "RA" },
                  { 6, "RB" },
                  { 7, "RC" },
                  { 8, "RD" }
               },
               new Object[][]{
                  { "ID", "LEFT_VALUE", "ID", "RIGHT_VALUE" },
                  { null, null, 5, "RA" },
                  { null, null, 6, "RB" },
                  { null, null, 7, "RC" },
                  { null, null, 8, "RD" }
               },
               JoinTableLens.RIGHT_OUTER_JOIN);
   }

   @Test
   void testRightJoinSomeMatch() {
      testJoin(new Object[][]{
                  { "ID", "LEFT_VALUE" },
                  { 1, "LA" },
                  { 2, "LB" },
                  { 3, "LC" },
                  { 4, "LD" }
               },
               new Object[][]{
                  { "ID", "RIGHT_VALUE" },
                  { 1, "RA" },
                  { 5, "RB" },
                  { 6, "RC" },
                  { 4, "RD" }
               },
               new Object[][]{
                  { "ID", "LEFT_VALUE", "ID", "RIGHT_VALUE" },
                  { null, null, 5, "RB" },
                  { null, null, 6, "RC" },
                  { 1, "LA", 1, "RA" },
                  { 4, "LD", 4, "RD" }
               },
               JoinTableLens.RIGHT_OUTER_JOIN);
   }

   @Test
   void testRightJoinSomeMatchWithTrailing() {
      testJoin(new Object[][]{
                  { "ID", "LEFT_VALUE" },
                  { 1, "LA" },
                  { 2, "LB" },
                  { 3, "LC" },
                  { 4, "LD" }
               },
               new Object[][]{
                  { "ID", "RIGHT_VALUE" },
                  { 1, "RA" },
                  { 5, "RB" },
                  { 6, "RC" },
                  { 4, "RD" },
                  { 7, "RE" },
                  { 8, "RF" }
               },
               new Object[][]{
                  { "ID", "LEFT_VALUE", "ID", "RIGHT_VALUE" },
                  { null, null, 5, "RB" },
                  { null, null, 6, "RC" },
                  { null, null, 7, "RE" },
                  { null, null, 8, "RF" },
                  { 1, "LA", 1, "RA" },
                  { 4, "LD", 4, "RD" }
               },
               JoinTableLens.RIGHT_OUTER_JOIN);
   }

   private void testJoin(Object[][] leftData, Object[][] rightData, Object[][] expected,
                         int type)
   {
      DefaultTableLens left = new DefaultTableLens(leftData);
      DefaultTableLens right = new DefaultTableLens(rightData);
      int[] cols = { 0 };
      JoinTableLens joined = new JoinTableLens(left, right, cols, cols, type, true);

      try {
         XTableUtil.assertEquals(joined, expected);
      }
      catch(AssertionError e) {
         Util.printTable(joined);
         throw e;
      }
   }
}
