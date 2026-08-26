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
    * Bug: a column's format/type is decided from only the sampled (typeRows) rows. A
    * later row past that scan window that only partially matches the cached format
    * (e.g. "50%" against the plain-number format cached for "100.5"/"200.5") was
    * silently accepted as a wrong value (50.0) instead of falling through to the
    * type-specific numeric fallback (0.5).
    */
   @Test
   public void percentRowBeyondScanWindowNotSilentlyMisparsed() throws Exception {
      List<String> types = new ArrayList<>();
      XSwappableTable table = readColumn("rate",
         new String[]{ "100.5", "200.5", "50%" }, types, 2);

      Assertions.assertEquals(XSchema.DOUBLE, types.get(0));
      Assertions.assertEquals(100.5, ((Number) table.getObject(1, 0)).doubleValue(), 0.001);
      Assertions.assertEquals(200.5, ((Number) table.getObject(2, 0)).doubleValue(), 0.001);
      Assertions.assertEquals(0.5, ((Number) table.getObject(3, 0)).doubleValue(), 0.001);
   }

   /**
    * Read a single column csv file, the returned table has the header in row 0.
    */
   private XSwappableTable readColumn(String header, String[] values, List<String> types)
      throws Exception
   {
      return readColumn(header, values, types, 50000);
   }

   private XSwappableTable readColumn(String header, String[] values, List<String> types,
                                       int typeRows)
      throws Exception
   {
      StringBuilder content = new StringBuilder(header).append('\n');

      for(String value : values) {
         content.append(value).append('\n');
      }

      File file = Files.write(tempDir.resolve(header + ".csv"),
                              content.toString().getBytes(StandardCharsets.UTF_8)).toFile();

      return CSVLoader.readCSV(file, "UTF-8", true, ",", true, false, new HashMap<>(), types,
                               true, null, typeRows, 0, -1, new DateParseInfo());
   }

   @TempDir
   Path tempDir;
}
