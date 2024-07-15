/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import inetsoft.report.filter.SortFilter;
import inetsoft.sree.SreeEnv;
import inetsoft.test.SreeHome;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * For bug #46758
 */
@SreeHome
class RightJoinWithNullsTest {
   private static String forceHash;
   private TableLens expected;
   private TableLens left;
   private TableLens right;

   @BeforeAll
   static void beforeAll() {
      forceHash = SreeEnv.getProperty("join.table.forceHash");
   }

   @BeforeEach
   void setUp() {
      // expected table and left and right input tables are populated from the results of
      // the join defined in join.sql
      DefaultTableLens expected = new DefaultTableLens(new Object[][] {
         {"Year", "QuarterOfYear", "CustomerId", "Quarter", "MonthOfYear", "OrderNumber"},
         {null, null, null, null, null, 113},
         {null, null, null, "2001 1st", "Feb", 176},
         {null, null, null, "2001 1st", "Jan", 337},
         {null, null, null, "2001 1st", "Mar", 325},
         {null, null, null, "2001 2nd", "Apr", 181},
         {null, null, null, "2001 2nd", "Jun", 463},
         {null, null, null, "2001 2nd", "May", 92},
         {null, null, null, "2001 3rd", "Aug", 21},
         {null, null, null, "2001 3rd", "Jul", 315},
         {null, null, null, "2001 3rd", "Sep", 69},
         {null, null, null, "2001 4th", "Dec", 87},
         {null, null, null, "2001 4th", "Nov", 252},
         {null, null, null, "2001 4th", "Oct", 153},
         {null, null, null, "2002 1st", "Feb", 153},
         {null, null, null, "2002 1st", "Jan", 256},
         {null, null, null, "2002 1st", "Mar", 65},
         {null, null, null, "2002 2nd", "Apr", 163},
         {null, null, null, "2002 2nd", "Jun", 293},
         {null, null, null, "2002 2nd", "May", 129},
         {null, null, null, "2002 3rd", "Aug", 306},
         {null, null, null, "2002 3rd", "Jul", 303},
         {null, null, null, "2002 3rd", "Sep", 63},
         {null, null, null, "2002 4th", "Dec", 146},
         {null, null, null, "2002 4th", "Nov", 142},
         {null, null, null, "2002 4th", "Oct", 554},
         {null, null, null, "2003 1st", "Feb", 78},
         {null, null, null, "2003 1st", "Jan", 162},
         {null, null, null, "2003 1st", "Mar", 342},
         {null, null, null, "2003 2nd", "Apr", 162},
         {null, null, null, "2003 2nd", "May", 242},
         {null, null, null, "2003 3rd", "Aug", 278},
         {null, null, null, "2003 3rd", "Jul", 248},
         {null, null, null, "2003 3rd", "Sep", 155},
         {null, null, null, "2003 4th", "Dec", 270},
         {null, null, null, "2003 4th", "Nov", 118},
         {null, null, null, "2003 4th", "Oct", 48}
      });
      expected.setHeaderRowCount(1);
      this.expected = new SortFilter(expected, new int[] { 0, 1, 2, 3, 4, 5 }, true);
      this.expected.moreRows(XTable.EOT);
      left = new DefaultTableLens(new Object[][] {
         { "Year", "Quarter", "CustomerId" },
         { null, null, 22 },
         { "2001", "1st", 129 },
         { "2001", "2nd", 120 },
         { "2001", "3rd", 84 },
         { "2001", "4th", 70 },
         { "2002", "1st", 88 },
         { "2002", "2nd", 90 },
         { "2002", "3rd", 81 },
         { "2002", "4th", 152 },
         { "2003", "1st", 111 },
         { "2003", "2nd", 59 },
         { "2003", "3rd", 135 },
         { "2003", "4th", 66 }
      });
      ((DefaultTableLens) left).setHeaderRowCount(1);
      right = new DefaultTableLens(new Object[][] {
         { "Quarter", "Month", "OrderNumber" },
         { null, null, 113 },
         { "2001 1st", "Jan", 337 },
         { "2001 1st", "Feb", 176 },
         { "2001 1st", "Mar", 325 },
         { "2001 2nd", "Apr", 181 },
         { "2001 2nd", "May", 92 },
         { "2001 2nd", "Jun", 463 },
         { "2001 3rd", "Jul", 315 },
         { "2001 3rd", "Aug", 21 },
         { "2001 3rd", "Sep", 69 },
         { "2001 4th", "Oct", 153 },
         { "2001 4th", "Nov", 252 },
         { "2001 4th", "Dec", 87 },
         { "2002 1st", "Jan", 256 },
         { "2002 1st", "Feb", 153 },
         { "2002 1st", "Mar", 65 },
         { "2002 2nd", "Apr", 163 },
         { "2002 2nd", "May", 129 },
         { "2002 2nd", "Jun", 293 },
         { "2002 3rd", "Jul", 303 },
         { "2002 3rd", "Aug", 306 },
         { "2002 3rd", "Sep", 63 },
         { "2002 4th", "Oct", 554 },
         { "2002 4th", "Nov", 142 },
         { "2002 4th", "Dec", 146 },
         { "2003 1st", "Jan", 162 },
         { "2003 1st", "Feb", 78 },
         { "2003 1st", "Mar", 342 },
         { "2003 2nd", "Apr", 162 },
         { "2003 2nd", "May", 242 },
         { "2003 3rd", "Jul", 248 },
         { "2003 3rd", "Aug", 278 },
         { "2003 3rd", "Sep", 155 },
         { "2003 4th", "Oct", 48 },
         { "2003 4th", "Nov", 118 },
         { "2003 4th", "Dec", 270 }
      });
      ((DefaultTableLens) right).setHeaderRowCount(1);
   }

   @AfterAll
   static void afterAll() {
      SreeEnv.setProperty("join.table.forceHash", forceHash);
   }

   @Test
   void testHashJoin() {
      int[] cols = { 0 };
      SreeEnv.setProperty("join.table.forceHash", "true");
      JoinTableLens joined = new JoinTableLens(
         left, right, cols, cols, JoinTableLens.RIGHT_OUTER_JOIN, true);
      cols = new int[joined.getColCount()];

      for(int i = 0; i < cols.length; i++) {
         cols[i] = i;
      }

      SortFilter sorted = new SortFilter(joined, cols, true);
      sorted.moreRows(XTable.EOT);
      assertTablesEqual(expected, sorted);
   }

   @Test
   void testMergeJoin() {
      int[] cols = { 0 };
      SreeEnv.setProperty("join.table.forceHash", "false");
      JoinTableLens joined = new JoinTableLens(
         left, right, cols, cols, JoinTableLens.RIGHT_OUTER_JOIN, true);
      cols = new int[joined.getColCount()];

      for(int i = 0; i < cols.length; i++) {
         cols[i] = i;
      }

      SortFilter sorted = new SortFilter(joined, cols, true);
      sorted.moreRows(XTable.EOT);
      assertTablesEqual(expected, sorted);
   }

   private void assertTablesEqual(TableLens expected, TableLens actual) {
      int rowCount = expected.getRowCount();
      int colCount = expected.getColCount();

      assertEquals(rowCount, actual.getRowCount());
      assertEquals(colCount, actual.getColCount());

      for(int row = 0; row < rowCount; row++) {
         for(int col = 0; col < colCount; col++) {
            Object expectedValue = expected.getObject(row, col);
            Object actualValue = expected.getObject(row, col);
            assertEquals(
               expectedValue, actualValue, "Values different at [row=" + row + ",col=" + col + "]");
         }
      }
   }
}
