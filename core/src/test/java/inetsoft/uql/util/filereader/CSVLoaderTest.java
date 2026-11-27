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
package inetsoft.uql.util.filereader;

import inetsoft.test.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.table.XSwappableTable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class CSVLoaderTest {
   /**
    * Bug: a column mixing plain numbers with currency formatted values had the values
    * that didn't match the column format replaced with null. The column should be
    * detected as a string column and all values kept as-is.
    */
   @Test
   public void mixedNumberAndCurrencyKeptAsString() throws Exception {
      String[] values = { "299.99", "89.5", "149", "$499.99", "12.99", "$24.99", "9.99" };
      List<String> types = new ArrayList<>();
      XSwappableTable table = readColumn("price", values, types);

      Assertions.assertEquals(XSchema.STRING, types.get(0));

      for(int i = 0; i < values.length; i++) {
         Assertions.assertEquals(values[i], table.getObject(i + 1, 0));
      }
   }

   /**
    * Same as above but the currency value comes first, so the column format changes
    * from currency to plain number instead.
    */
   @Test
   public void mixedCurrencyAndNumberKeptAsString() throws Exception {
      String[] values = { "$499.99", "299.99", "$24.99" };
      List<String> types = new ArrayList<>();
      XSwappableTable table = readColumn("price", values, types);

      Assertions.assertEquals(XSchema.STRING, types.get(0));

      for(int i = 0; i < values.length; i++) {
         Assertions.assertEquals(values[i], table.getObject(i + 1, 0));
      }
   }

   @Test
   public void currencyOnlyDetectedAsNumber() throws Exception {
      List<String> types = new ArrayList<>();
      XSwappableTable table = readColumn("price", new String[]{ "$499.99", "$24.99" }, types);

      Assertions.assertEquals(XSchema.DOUBLE, types.get(0));
      Assertions.assertEquals(499.99, ((Number) table.getObject(1, 0)).doubleValue(), 0.001);
      Assertions.assertEquals(24.99, ((Number) table.getObject(2, 0)).doubleValue(), 0.001);
   }

   @Test
   public void percentOnlyDetectedAsNumber() throws Exception {
      List<String> types = new ArrayList<>();
      XSwappableTable table = readColumn("rate", new String[]{ "15.50%", "8.25%" }, types);

      Assertions.assertEquals(XSchema.DOUBLE, types.get(0));
      Assertions.assertEquals(0.155, ((Number) table.getObject(1, 0)).doubleValue(), 0.001);
      Assertions.assertEquals(0.0825, ((Number) table.getObject(2, 0)).doubleValue(), 0.001);
   }

   @Test
   public void plainNumberOnlyDetectedAsNumber() throws Exception {
      List<String> types = new ArrayList<>();
      XSwappableTable table = readColumn("price", new String[]{ "299.99", "89.5" }, types);

      Assertions.assertEquals(XSchema.DOUBLE, types.get(0));
      Assertions.assertEquals(299.99, ((Number) table.getObject(1, 0)).doubleValue(), 0.001);
      Assertions.assertEquals(89.5, ((Number) table.getObject(2, 0)).doubleValue(), 0.001);
   }

   @Test
   public void integerOnlyDetectedAsInteger() throws Exception {
      List<String> types = new ArrayList<>();
      XSwappableTable table = readColumn("qty", new String[]{ "342", "856" }, types);

      Assertions.assertEquals(XSchema.INTEGER, types.get(0));
      Assertions.assertEquals(342, table.getObject(1, 0));
      Assertions.assertEquals(856, table.getObject(2, 0));
   }

   /**
    * Bug #82506: a value that can't be converted to the detected type appearing only after
    * the scanned rows was silently replaced by null. Scanning every row detects the mixed
    * column so it is loaded as a string and the values are kept.
    */
   @Test
   public void notApplicableAfterTypeRowsScannedWithFullDetection() throws Exception {
      DateParseInfo info = new DateParseInfo();
      List<String> types = new ArrayList<>();
      XSwappableTable table = readIdValue(60000, 50001, Integer.MAX_VALUE, 0, types, info);

      Assertions.assertEquals(XSchema.STRING, types.get(1));
      Assertions.assertTrue(info.getProspectTypeMap().containsKey(1));
      Assertions.assertEquals("N/A", table.getObject(50001, 1));
      Assertions.assertEquals("N/A", table.getObject(60000, 1));
      Assertions.assertEquals("5", table.getObject(5, 1));
   }

   /**
    * Control: the change within the scanned rows behaves the same with full detection.
    */
   @Test
   public void notApplicableWithinTypeRows() throws Exception {
      DateParseInfo info = new DateParseInfo();
      List<String> types = new ArrayList<>();
      XSwappableTable table = readIdValue(60000, 49000, 50000, 0, types, info);

      Assertions.assertEquals(XSchema.STRING, types.get(1));
      Assertions.assertTrue(info.getProspectTypeMap().containsKey(1));
      Assertions.assertEquals("N/A", table.getObject(49000, 1));
      Assertions.assertEquals("N/A", table.getObject(60000, 1));
   }

   /**
    * The row limit case keeps scanning max(rowLimit, 50000) rows; rows past the row limit
    * aren't loaded so nothing is lost.
    */
   @Test
   public void rowLimitBehaviorUnchanged() throws Exception {
      DateParseInfo info = new DateParseInfo();
      List<String> types = new ArrayList<>();
      XSwappableTable table = readIdValue(60000, 55000, 50000, 10000, types, info);

      Assertions.assertNotEquals(XSchema.STRING, types.get(1));
      Assertions.assertTrue(info.getProspectTypeMap().isEmpty());
      Assertions.assertEquals(10001, table.getRowCount());
   }

   /**
    * Without full detection the old window is still observable (documents the old behavior
    * for callers that pass a typeRows cap).
    */
   @Test
   public void cappedDetectionStillNullsLateValues() throws Exception {
      DateParseInfo info = new DateParseInfo();
      List<String> types = new ArrayList<>();
      XSwappableTable table = readIdValue(60000, 50001, 50000, 0, types, info);

      Assertions.assertNotEquals(XSchema.STRING, types.get(1));
      Assertions.assertNull(table.getObject(50001, 1));
   }

   /**
    * Continue-as-string flow: the column the preview reported is loaded as string.
    */
   @Test
   public void ignoreTypeColumnsLoadsAsString() throws Exception {
      DateParseInfo info = new DateParseInfo();
      info.setIgnoreTypeColumns(new ArrayList<>(List.of(1)));
      List<String> types = new ArrayList<>();
      XSwappableTable table = readIdValue(60000, 50001, Integer.MAX_VALUE, 0, types, info);

      Assertions.assertEquals(XSchema.STRING, types.get(1));
      Assertions.assertEquals("N/A", table.getObject(50001, 1));
   }

   /**
    * Two column file id,v with numeric v, except "N/A" from data row naRow on.
    */
   private XSwappableTable readIdValue(int rows, int naRow, int typeRows, int rowLimit,
                                       List<String> types, DateParseInfo info)
      throws Exception
   {
      StringBuilder content = new StringBuilder("id,v\n");

      for(int i = 1; i <= rows; i++) {
         content.append(i).append(',').append(i >= naRow ? "N/A" : String.valueOf(i))
            .append('\n');
      }

      File file = Files.write(tempDir.resolve("idv" + naRow + "_" + typeRows + ".csv"),
                              content.toString().getBytes(StandardCharsets.UTF_8)).toFile();

      return CSVLoader.readCSV(file, "UTF-8", true, ",", true, false, new HashMap<>(), types,
                               true, null, typeRows, rowLimit, -1, info);
   }

   /**
    * Read a single column csv file, the returned table has the header in row 0.
    */
   private XSwappableTable readColumn(String header, String[] values, List<String> types)
      throws Exception
   {
      StringBuilder content = new StringBuilder(header).append('\n');

      for(String value : values) {
         content.append(value).append('\n');
      }

      File file = Files.write(tempDir.resolve(header + ".csv"),
                              content.toString().getBytes(StandardCharsets.UTF_8)).toFile();

      return CSVLoader.readCSV(file, "UTF-8", true, ",", true, false, new HashMap<>(), types,
                               true, null, 50000, 0, -1, new DateParseInfo());
   }

   @TempDir
   Path tempDir;
}
