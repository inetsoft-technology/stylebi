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

import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.uql.XTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.text.TextOutput;
import inetsoft.uql.util.filereader.DelimitedFileReader;
import org.junit.jupiter.api.Assertions;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

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
      Assertions.assertDoesNotThrow(() -> {
         expected.moreRows(XTable.EOT);
         actual.moreRows(XTable.EOT);
         int colCount = expected.getColCount();
         Assertions.assertEquals(colCount, actual.getColCount(), "Incorrect column count");

         for(int r = 0; r < expected.getRowCount(); r++) {
            Assertions.assertEquals(
               expected.moreRows(r), actual.moreRows(r), "Incorrect moreRows(" + r + ") result");

            for(int c = 0; c < colCount; c++) {
               Assertions.assertEquals(
                  expected.getObject(r, c), actual.getObject(r, c),
                  "Incorrect value at [row=" + r + ",col=" + c + "]");
            }
         }

         Assertions.assertEquals(
            expected.getRowCount(), actual.getRowCount(), "Incorrect row count");
      });
   }

   public static Object[][] getDefaultData() {
      return new Object[][]{
         { "col1", "col2", "col3" },
         { "a", 1, 5.0 },
         { "b", 3, 10.0 },
         { "b", 1, 2.5 },
         { "c", 1, 3.0 }
      };
   }

   public static TableLens getDefaultTableLens() {
      return new DefaultTableLens(getDefaultData());
   }

   public static DefaultDataSet getDefaultDataSet() {
      return new DefaultDataSet(getDefaultData());
   }

   public static XTableNode getDefaultXTableNode() throws Exception {
      String content =
         "col1,col2,col3\n" +
            "a,1,5.0\n" +
            "b,3,10.0\n" +
            "b,1,2.5\n" +
            "c,1,3.0\n";
      TextOutput toutput = new TextOutput();
      DelimitedFileReader reader = new DelimitedFileReader();
      return reader.read(new ByteArrayInputStream(content.getBytes()), "UTF8",
                                     null, toutput, -1, 3, true,
                                     ",", false);
   }

   public static Date date(String date) {
      return new Date(LocalDate.parse(date).atStartOfDay()
                         .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
   }
}
