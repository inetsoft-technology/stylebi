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
package inetsoft.uql.util.filereader;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.table.XSwappableTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How {@link CSVLoader#readCSV} decides the type of a column from all the values of it, and how
 * it splits a file into rows and columns. The expected types follow the merge rules of the code:
 * values of different numeric types make a double column, any other mix makes a string column,
 * and a column of numbers with an "N/A" or "NA" value is a string column.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CSVLoaderRulesTest {
   @TempDir
   Path tempDir;

   private int fileCount;

   /** The result of a read: the table (header in row 0) and the detected column types. */
   private record Loaded(XSwappableTable table, List<String> types, DateParseInfo info) {
      Object cell(int row, int col) {
         return table.getObject(row, col);
      }

      List<Object> row(int row) {
         List<Object> values = new ArrayList<>();

         for(int c = 0; c < table.getColCount(); c++) {
            values.add(table.getObject(row, c));
         }

         return values;
      }
   }

   private Loaded read(byte[] content, String delim, boolean firstRow, boolean removeQuote,
                       boolean detectType, int typeRows, int rowLimit, int colLimit)
      throws Exception
   {
      File file = Files.write(tempDir.resolve("data" + (fileCount++) + ".csv"), content).toFile();
      List<String> types = new ArrayList<>();
      DateParseInfo info = new DateParseInfo();
      XSwappableTable table = CSVLoader.readCSV(file, "UTF-8", removeQuote, delim, firstRow, false,
                                                new HashMap<>(), types, detectType, null,
                                                typeRows, rowLimit, colLimit, info);
      return new Loaded(table, types, info);
   }

   private Loaded read(String content) throws Exception {
      return read(content.getBytes(StandardCharsets.UTF_8), ",", true, true, true, 50000, 0, -1);
   }

   // ---- the type of a column from all of its values ----

   @Test
   void integersAndDecimalsMakeADoubleColumn() throws Exception {
      Loaded loaded = read("v\n1\n2.5\n3\n");

      assertEquals(List.of(XSchema.DOUBLE), loaded.types());
      assertEquals(1.0, loaded.cell(1, 0));
      assertEquals(2.5, loaded.cell(2, 0));
      assertEquals(3.0, loaded.cell(3, 0));
   }

   @Test
   void decimalFirstThenIntegerIsStillDouble() throws Exception {
      Loaded loaded = read("v\n2.5\n1\n");

      assertEquals(List.of(XSchema.DOUBLE), loaded.types());
      assertEquals(1.0, loaded.cell(2, 0));
   }

   @ParameterizedTest(name = "{0}")
   @CsvSource(delimiter = '|', value = {
      "numbers then text|1,2,abc", "text then numbers|abc,1,2",
      "text between numbers|1,abc,2", "numbers and a date|1,2024-02-29,3",
      "a date and text|2024-02-29,abc"
   })
   void mixOfKindsMakesAStringColumnThatKeepsTheText(String name, String values) throws Exception {
      String[] cells = values.split(",");
      Loaded loaded = read("v\n" + String.join("\n", cells) + "\n");

      assertEquals(List.of(XSchema.STRING), loaded.types(), name);

      for(int i = 0; i < cells.length; i++) {
         assertEquals(cells[i], loaded.cell(i + 1, 0), name + " row " + (i + 1));
      }
   }

   // a column of numbers with an N/A or NA value is a string column and keeps every value, so the
   // number is not lost; the kind of number it was is remembered for the user to turn it back
   @ParameterizedTest(name = "{0}")
   @CsvSource(delimiter = '|', value = {
      "N/A last|1,2,N/A", "N/A first|N/A,1,2", "NA in the middle|1,NA,3", "lower case|1,n/a,3"
   })
   void notApplicableValueMakesANumberColumnAString(String name, String values) throws Exception {
      String[] cells = values.split(",");
      Loaded loaded = read("v\n" + String.join("\n", cells) + "\n");

      assertEquals(List.of(XSchema.STRING), loaded.types(), name);

      for(int i = 0; i < cells.length; i++) {
         assertEquals(cells[i], loaded.cell(i + 1, 0), name + " row " + (i + 1));
      }
   }

   // in a column that is a string anyway the N/A stays text, nothing is turned into null
   @Test
   void notApplicableValueInATextColumnStaysText() throws Exception {
      Loaded loaded = read("v\nabc\nN/A\ndef\n");

      assertEquals(List.of(XSchema.STRING), loaded.types());
      assertEquals("N/A", loaded.cell(2, 0));
   }

   @Test
   void emptyCellsDoNotChangeTheType() throws Exception {
      Loaded loaded = read("a,b\n1,x\n,y\n3,\n");

      assertEquals(List.of(XSchema.INTEGER, XSchema.STRING), loaded.types());
      assertEquals(List.of(1, "x"), loaded.row(1));
      assertEquals(Arrays.asList(null, "y"), loaded.row(2));
      assertEquals(Arrays.asList(3, null), loaded.row(3));
   }

   @Test
   void everyColumnHasItsOwnType() throws Exception {
      Loaded loaded = read("i,d,s,t\n1,1.5,a,2024-02-29\n2,2.5,b,2024-03-01\n");

      assertEquals(List.of(XSchema.INTEGER, XSchema.DOUBLE, XSchema.STRING, XSchema.DATE),
                   loaded.types());
      assertEquals(List.of("i", "d", "s", "t"), loaded.row(0));
      assertEquals(List.of(2, 2.5, "b"), loaded.row(2).subList(0, 3));
   }

   @Test
   void isoDatesAreDateValues() throws Exception {
      Loaded loaded = read("day\n2024-02-29\n2024-03-01\n");

      assertEquals(List.of(XSchema.DATE), loaded.types());
      assertEquals(java.time.LocalDate.of(2024, 2, 29),
                   ((java.sql.Date) loaded.cell(1, 0)).toLocalDate());
      assertEquals(java.time.LocalDate.of(2024, 3, 1),
                   ((java.sql.Date) loaded.cell(2, 0)).toLocalDate());
   }

   // the loader (not TextUtil.getType) recognizes true and false, in any case, as a boolean column
   @Test
   void trueAndFalseAreABooleanColumn() throws Exception {
      Loaded loaded = read("flag\nTRUE\nfalse\nTrue\n");

      assertEquals(List.of(XSchema.BOOLEAN), loaded.types());
      assertEquals(Boolean.TRUE, loaded.cell(1, 0));
      assertEquals(Boolean.FALSE, loaded.cell(2, 0));
      assertEquals(Boolean.TRUE, loaded.cell(3, 0));
   }

   // any other value in a boolean column makes it a string column that keeps all the text
   @ParameterizedTest(name = "{0}")
   @CsvSource(delimiter = '|', value = {
      "booleans then text|true,false,yes", "booleans then a number|true,1",
      "a number then booleans|1,true", "text then booleans|yes,true"
   })
   void booleanMixedWithOtherValuesIsAStringColumn(String name, String values) throws Exception {
      String[] cells = values.split(",");
      Loaded loaded = read("v\n" + String.join("\n", cells) + "\n");

      assertEquals(List.of(XSchema.STRING), loaded.types(), name);

      for(int i = 0; i < cells.length; i++) {
         assertEquals(cells[i], loaded.cell(i + 1, 0), name + " row " + (i + 1));
      }
   }

   // without type detection every column is a string, and the text is kept as it is
   @Test
   void withoutTypeDetectionEveryColumnIsAString() throws Exception {
      Loaded loaded = read("a,b\n007,1.50\n2,3\n".getBytes(StandardCharsets.UTF_8), ",", true,
                           true, false, 50000, 0, -1);

      assertEquals(List.of(XSchema.STRING, XSchema.STRING), loaded.types());
      assertEquals(List.of("007", "1.50"), loaded.row(1));
   }

   // the type is decided from the first rows only (the header counts as the first of them), a
   // later value that does not fit is lost. Pins the current behavior.
   @Test
   void valuesBeyondTheTypeRowsThatDoNotFitBecomeNull() throws Exception {
      Loaded loaded = read("v\n1\n2\nabc\n".getBytes(StandardCharsets.UTF_8), ",", true, true,
                           true, 3, 0, -1);

      assertEquals(List.of(XSchema.INTEGER), loaded.types());
      assertEquals(1, loaded.cell(1, 0));
      assertEquals(2, loaded.cell(2, 0));
      assertNull(loaded.cell(3, 0));
   }

   // ---- headers ----

   @Test
   void firstRowIsTheHeaderWhenAsked() throws Exception {
      Loaded loaded = read("a,b\n1,2\n");

      assertEquals(List.of("a", "b"), loaded.row(0));
      assertEquals(2, loaded.table().getRowCount());
   }

   @Test
   void withoutAHeaderRowTheColumnsAreNamedAndTheFirstRowIsData() throws Exception {
      Loaded loaded = read("1,x\n2,y\n".getBytes(StandardCharsets.UTF_8), ",", false, true, true,
                           50000, 0, -1);

      assertEquals(List.of("col0", "col1"), loaded.row(0));
      assertEquals(List.of(1, "x"), loaded.row(1));
      assertEquals(List.of(2, "y"), loaded.row(2));
      assertEquals(List.of(XSchema.INTEGER, XSchema.STRING), loaded.types());
   }

   @Test
   void duplicateAndEmptyHeadersAreMadeUnique() {
      assertArrayEquals(new Object[] { "a", "a 1", "col2", "b" },
                        CSVLoader.getHeaders(new String[] { "a", "a", "", "b" }, true, -1, true));
      assertArrayEquals(new Object[] { "a", "a 1", "a 2" },
                        CSVLoader.getHeaders(new String[] { "a", "a", "a" }, true, -1, true));
   }

   @Test
   void headersAreCutAtTheColumnLimit() {
      assertArrayEquals(new Object[] { "a", "b" },
                        CSVLoader.getHeaders(new String[] { "a", "b", "c" }, true, 2, true));
   }

   @Test
   void controlCharactersInAHeaderBecomeUnderscores() {
      assertEquals("a_b", CSVLoader.toValidHeader("a\tb"));
      assertEquals("a_b", CSVLoader.toValidHeader("a\r\nb"));
      assertEquals("plain", CSVLoader.toValidHeader("plain"));
   }

   // ---- rows, columns and limits ----

   @ParameterizedTest(name = "delimiter {0}")
   @CsvSource(delimiter = '#', value = { ",#,", "TAB#\t", ";#;", "|#|" })
   void anyDelimiterSplitsTheColumns(String name, String delim) throws Exception {
      String d = "TAB".equals(name) ? "\t" : delim;
      String text = "a" + d + "b\n1" + d + "x\n";
      Loaded loaded = read(text.getBytes(StandardCharsets.UTF_8), d, true, true, true, 50000, 0, -1);

      assertEquals(List.of("a", "b"), loaded.row(0));
      assertEquals(List.of(1, "x"), loaded.row(1));
   }

   @Test
   void quotedFieldKeepsTheDelimiterAndTheNewline() throws Exception {
      Loaded loaded = read("a,b\n\"x,y\",\"line1\nline2\"\n2,z\n");

      assertEquals(3, loaded.table().getRowCount());
      assertEquals(List.of("x,y", "line1\nline2"), loaded.row(1));
      assertEquals(List.of("2", "z"), loaded.row(2));
   }

   @Test
   void quotesAreKeptWhenNotToBeRemoved() throws Exception {
      Loaded loaded = read("a\n\"x\"\n".getBytes(StandardCharsets.UTF_8), ",", true, false, true,
                           50000, 0, -1);

      assertEquals("\"x\"", loaded.cell(1, 0));
   }

   @Test
   void shortRowsAreFilledWithNullAndLongRowsAreCut() throws Exception {
      Loaded loaded = read("a,b,c\n1,2\n3,4,5,6\n");

      assertEquals(3, loaded.table().getColCount());
      assertEquals(Arrays.asList(1, 2, null), loaded.row(1));
      assertEquals(List.of(3, 4, 5), loaded.row(2));
   }

   @Test
   void emptyLinesBeforeTheHeaderAreSkipped() throws Exception {
      Loaded loaded = read("\n\na,b\n1,2\n");

      assertEquals(List.of("a", "b"), loaded.row(0));
      assertEquals(List.of(1, 2), loaded.row(1));
   }

   @Test
   void byteOrderMarkIsNotPartOfTheFirstHeader() throws Exception {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      out.write(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF });
      out.write("a,b\n1,2\n".getBytes(StandardCharsets.UTF_8));
      Loaded loaded = read(out.toByteArray(), ",", true, true, true, 50000, 0, -1);

      assertEquals(List.of("a", "b"), loaded.row(0));
   }

   @Test
   void rowLimitKeepsThatManyDataRowsAndFlagsTheTable() throws Exception {
      Loaded loaded = read("v\n1\n2\n3\n4\n".getBytes(StandardCharsets.UTF_8), ",", true, true,
                           true, 50000, 2, -1);

      assertEquals(3, loaded.table().getRowCount());
      assertEquals(List.of(2), loaded.row(2));
      assertTrue(loaded.table().isExceedLimit());
   }

   // the table is flagged when the number of rows is the limit, though nothing was cut.
   // Pins the current behavior.
   @Test
   void rowLimitEqualToTheRowCountFlagsTheTableToo() throws Exception {
      Loaded loaded = read("v\n1\n2\n".getBytes(StandardCharsets.UTF_8), ",", true, true,
                           true, 50000, 2, -1);

      assertEquals(3, loaded.table().getRowCount());
      assertTrue(loaded.table().isExceedLimit());
   }

   @Test
   void rowLimitAboveTheRowCountDoesNotFlagTheTable() throws Exception {
      Loaded loaded = read("v\n1\n2\n".getBytes(StandardCharsets.UTF_8), ",", true, true,
                           true, 50000, 3, -1);

      assertFalse(loaded.table().isExceedLimit());
   }

   @Test
   void columnLimitDropsTheColumnsBeyondItAndFlagsTheTable() throws Exception {
      Loaded loaded = read("a,b,c\n1,2,3\n".getBytes(StandardCharsets.UTF_8), ",", true, true,
                           true, 50000, 0, 2);

      assertEquals(2, loaded.table().getColCount());
      assertEquals(List.of("a", "b"), loaded.row(0));
      assertEquals(List.of(1, 2), loaded.row(1));
      assertTrue(loaded.table().isExceedLimit());
   }

   @Test
   void columnLimitAtTheColumnCountDoesNotFlagTheTable() throws Exception {
      Loaded loaded = read("a,b\n1,2\n".getBytes(StandardCharsets.UTF_8), ",", true, true,
                           true, 50000, 0, 2);

      assertFalse(loaded.table().isExceedLimit());
   }
}
