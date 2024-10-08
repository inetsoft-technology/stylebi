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
package inetsoft.test;

import inetsoft.uql.XTable;
import org.junit.jupiter.api.Assertions;

/**
 * Utility for printing, comparing tables.
 */
public class XTableUtil {

   public static void assertEquals(XTable table, Object[][] arr) {
      table.moreRows(XTable.EOT);
      Assertions.assertEquals(arr.length, table.getRowCount(), "Wrong number of rows");

      for(int i = 0; table.moreRows(i); i++) {
         Assertions.assertEquals(arr[i].length, table.getColCount(),
                                 "Wrong number of columns");

         for(int c = 0; c < table.getColCount(); c++) {
            Assertions.assertEquals(arr[i][c], table.getObject(i, c),
                                    "Wrong value in cell [" + i + "," + c + "]");
         }
      }
   }

   public static void assertEquals(XTable expected, XTable actual) {
      int colCount = expected.getColCount();
      Assertions.assertEquals(colCount, actual.getColCount(),"Incorrect column count");

      for(int r = 0; expected.moreRows(r) && actual.moreRows(r); r++) {
         for(int c = 0; c < colCount; c++) {
            Assertions.assertEquals(
               expected.getObject(r, c), actual.getObject(r, c),
               "Incorrect value at [row=" + r + ",col=" + c + "]");
         }
      }

      Assertions.assertEquals(
         expected.getRowCount(), actual.getRowCount(), "Incorrect row count");
   }

}
