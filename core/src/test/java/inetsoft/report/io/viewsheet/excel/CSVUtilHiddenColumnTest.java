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
package inetsoft.report.io.viewsheet.excel;

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * Bug #79579. CSVUtil.writeTableDataAssembly printed the delimiter keyed on the absolute column
 * index (c > 0), so when leading columns were hidden (width 0) every row started with an empty
 * field. The delimiter must be printed only after a field was written on the row.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CSVUtilHiddenColumnTest {
   private static String export(int[] widths, String delim, String quote, boolean keepHeader)
      throws Exception
   {
      DefaultTableLens base = new DefaultTableLens(3, 3);
      String[][] data = {{"Year", "Sum", "Cnt"}, {"a,b", "1", "2"}, {"y3", "3", "4"}};

      for(int r = 0; r < 3; r++) {
         for(int c = 0; c < 3; c++) {
            base.setObject(r, c, data[r][c]);
         }
      }

      base.setHeaderRowCount(1);
      VSTableLens lens = new VSTableLens(base);

      if(widths != null) {
         lens.setColumnWidths(widths);
      }

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      CSVUtil.writeTableDataAssembly(lens, out, delim, quote, keepHeader);
      return out.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
   }

   @Test
   void noneHidden() throws Exception {
      assertEquals("\"Year\",\"Sum\",\"Cnt\"\n\"a,b\",\"1\",\"2\"\n\"y3\",\"3\",\"4\"\n",
                   export(new int[] { 80, 80, 80 }, ",", "\"", true));
   }

   @Test
   void firstHidden() throws Exception {
      assertEquals("\"Sum\",\"Cnt\"\n\"1\",\"2\"\n\"3\",\"4\"\n",
                   export(new int[] { 0, 80, 80 }, ",", "\"", true));
   }

   @Test
   void firstHiddenNoHeader() throws Exception {
      assertEquals("\"1\",\"2\"\n\"3\",\"4\"\n",
                   export(new int[] { 0, 80, 80 }, ",", "\"", false));
   }

   @Test
   void firstTwoHidden() throws Exception {
      assertEquals("\"Cnt\"\n\"2\"\n\"4\"\n",
                   export(new int[] { 0, 0, 80 }, ",", "\"", true));
   }

   @Test
   void middleHidden() throws Exception {
      assertEquals("\"Year\",\"Cnt\"\n\"a,b\",\"2\"\n\"y3\",\"4\"\n",
                   export(new int[] { 80, 0, 80 }, ",", "\"", true));
   }

   @Test
   void lastHidden() throws Exception {
      assertEquals("\"Year\",\"Sum\"\n\"a,b\",\"1\"\n\"y3\",\"3\"\n",
                   export(new int[] { 80, 80, 0 }, ",", "\"", true));
   }

   @Test
   void allHiddenWritesEmptyLines() throws Exception {
      assertEquals("\n\n\n", export(new int[] { 0, 0, 0 }, ",", "\"", true));
      assertEquals("\n\n", export(new int[] { 0, 0, 0 }, ",", "\"", false));
   }

   @Test
   void firstHiddenTabDelimited() throws Exception {
      assertEquals("\"Sum\"\t\"Cnt\"\n\"1\"\t\"2\"\n\"3\"\t\"4\"\n",
                   export(new int[] { 0, 80, 80 }, "\t", "\"", true));
   }

   @Test
   void firstHiddenNoQuote() throws Exception {
      assertEquals("Sum;Cnt\n1;2\n3;4\n", export(new int[] { 0, 80, 80 }, ";", "", true));
   }

   @Test
   void quotingStillAppliesWithFirstHidden() throws Exception {
      assertEquals("\"b,c\"\n\"a,b\"\n", exportQuoting());
   }

   private static String exportQuoting() throws Exception {
      DefaultTableLens base = new DefaultTableLens(2, 3);
      String[][] data = {{"h0", "b,c", "d"}, {"z", "a,b", "x"}};

      for(int r = 0; r < 2; r++) {
         for(int c = 0; c < 3; c++) {
            base.setObject(r, c, data[r][c]);
         }
      }

      base.setHeaderRowCount(1);
      VSTableLens lens = new VSTableLens(base);
      lens.setColumnWidths(new int[] { 0, 80, 0 });
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      CSVUtil.writeTableDataAssembly(lens, out, ",", "", true);
      return out.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
   }
}
