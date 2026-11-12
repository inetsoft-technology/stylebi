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
package inetsoft.uql.util;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.XTable;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Changing the type of the columns of a table whose data is in the worksheet
 * ({@link XEmbeddedTable#setDataTypes}), which is what the dialog of the type of a column does to
 * an embedded table and to an un-pivoted one. The cells of the column are converted one by one;
 * the expected values are written by hand. The rules are the ones of the code: a number can be
 * written with a thousands separator, a percent sign, a dollar sign or in brackets as a negative;
 * a cell that cannot be converted is an error, unless the conversion is forced (it is then null)
 * or the column is a number and the cell is "N/A" or "NA" (null); a date is parsed with the format
 * of the column when there is one (an empty cell, "N/A" and "n/a" are null), and without a format
 * a date that cannot be read is null.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class XEmbeddedTableSetDataTypesTest {
   /** A table of one text column "c" with the values. */
   private static XEmbeddedTable column(Object... values) {
      Object[][] data = new Object[values.length + 1][];
      data[0] = new Object[] { "c" };

      for(int i = 0; i < values.length; i++) {
         data[i + 1] = new Object[] { values[i] };
      }

      return new XEmbeddedTable(new String[] { XSchema.STRING }, data);
   }

   private static List<Object> change(XEmbeddedTable table, String type, Format format,
                                      boolean force) throws Exception
   {
      table.setDataTypes(List.of(0), List.of(type), java.util.Collections.singletonList(format),
                         null, List.of(force));
      table.moreRows(XTable.EOT);
      List<Object> values = new ArrayList<>();

      for(int r = table.getHeaderRowCount(); r < table.getRowCount(); r++) {
         values.add(table.getObject(r, 0));
      }

      return values;
   }

   private static List<Object> change(String type, boolean force, Object... values)
      throws Exception
   {
      return change(column(values), type, null, force);
   }

   // ---- text to a number ----

   @Test
   void textIsReadAsAnInteger() throws Exception {
      assertEquals(List.of(12, 7, -5), change(XSchema.INTEGER, false, "12", "007", "-5"));
   }

   @Test
   void textIsReadAsADouble() throws Exception {
      assertEquals(List.of(1.5, -0.25, 3.0), change(XSchema.DOUBLE, false, "1.5", "-0.25", "3"));
   }

   // a thousands separator, a dollar sign, a percent sign, and brackets for a negative number
   @Test
   void formattedNumbersAreRead() throws Exception {
      assertEquals(List.of(1234.5, 12.5, -3.0, -3.0, 0.5),
                   change(XSchema.DOUBLE, false, "1,234.5", "$12.5", "-$3", "($3)", "50%"));
   }

   @Test
   void numberIsReadAsAnotherNumberTypeWhenItFits() throws Exception {
      assertEquals(List.of(1234, 12), change(XSchema.INTEGER, false, "1,234", "$12"));
   }

   // a percent is a fraction (the double column reads it itself); a column of integers gets the
   // fraction as an integer, 50% is 0.5 and so 0. Pins the current behavior.
   @Test
   void percentInAnIntegerColumnIsTheFractionRoundedDown() throws Exception {
      assertEquals(List.of(0), change(XSchema.INTEGER, false, "50%"));
   }

   // ---- a cell that cannot be converted ----

   @Test
   void textThatIsNotANumberIsAnErrorWhenTheConversionIsNotForced() {
      assertThrows(Exception.class, () -> change(XSchema.INTEGER, false, "1", "abc"));
      assertThrows(Exception.class, () -> change(XSchema.DOUBLE, false, "1.5", "abc"));
   }

   // the number format reads the digits at the start and leaves the rest: "12abc" is 12, not an
   // error (the upload of a file reads "12abc" as text, it checks that the whole text is read).
   // Pins the current behavior, which may be unintended.
   @Test
   void textThatStartsWithANumberIsReadAsThatNumber() throws Exception {
      assertEquals(List.of(12.0, 1.5), change(XSchema.DOUBLE, false, "12abc", "1.5x"));
   }

   @Test
   void textThatIsNotANumberIsNullWhenTheConversionIsForced() throws Exception {
      assertEquals(java.util.Arrays.asList(1, null, 3),
                   change(XSchema.INTEGER, true, "1", "abc", "3"));
   }

   // "N/A" and "NA", in any case, are no number and no error
   @ParameterizedTest(name = "[{0}]")
   @ValueSource(strings = { "N/A", "n/a", "NA", "na", "Na" })
   void notApplicableValueIsNullInANumberColumn(String value) throws Exception {
      assertEquals(java.util.Arrays.asList(1, null), change(XSchema.INTEGER, false, "1", value));
   }

   @Test
   void emptyCellIsNullInANumberColumn() throws Exception {
      assertEquals(java.util.Arrays.asList(1, null), change(XSchema.INTEGER, false, "1", ""));
   }

   // ---- to text ----

   @Test
   void numberIsWrittenAsText() throws Exception {
      XEmbeddedTable table = new XEmbeddedTable(new String[] { XSchema.INTEGER },
                                                new Object[][] { { "c" }, { 12 }, { -5 } });

      assertEquals(List.of("12", "-5"), change(table, XSchema.STRING, null, false));
   }

   // ---- a date ----

   private static void assertDate(LocalDate expected, Object actual) {
      assertTrue(actual instanceof java.sql.Date, "" + actual);
      assertEquals(expected, ((java.sql.Date) actual).toLocalDate());
   }

   @Test
   void dateIsReadWithTheFormatOfTheColumn() throws Exception {
      List<Object> values = change(column("29/02/2024", "01/12/1999"), XSchema.DATE,
                                   new SimpleDateFormat("dd/MM/yyyy"), false);

      assertEquals(2, values.size());
      assertDate(LocalDate.of(2024, 2, 29), values.get(0));
      assertDate(LocalDate.of(1999, 12, 1), values.get(1));
   }

   // the same text means another day with another format
   @Test
   void theFormatDecidesWhichPartIsTheMonth() throws Exception {
      List<Object> dayFirst = change(column("03/04/2024"), XSchema.DATE,
                                     new SimpleDateFormat("dd/MM/yyyy"), false);
      List<Object> monthFirst = change(column("03/04/2024"), XSchema.DATE,
                                       new SimpleDateFormat("MM/dd/yyyy"), false);

      assertDate(LocalDate.of(2024, 4, 3), dayFirst.get(0));
      assertDate(LocalDate.of(2024, 3, 4), monthFirst.get(0));
   }

   @Test
   void dateWithATimeIsATimestampOrATime() throws Exception {
      List<Object> instant = change(column("29/02/2024 13:45"), XSchema.TIME_INSTANT,
                                    new SimpleDateFormat("dd/MM/yyyy HH:mm"), false);
      List<Object> time = change(column("13:45:30"), XSchema.TIME,
                                 new SimpleDateFormat("HH:mm:ss"), false);

      assertTrue(instant.get(0) instanceof java.sql.Timestamp);
      assertEquals(LocalDateTime.of(2024, 2, 29, 13, 45),
                   ((java.sql.Timestamp) instant.get(0)).toLocalDateTime());
      assertTrue(time.get(0) instanceof java.sql.Time);
      assertEquals(LocalTime.of(13, 45, 30), ((java.sql.Time) time.get(0)).toLocalTime());
   }

   // with a format, an empty cell and "N/A" are null, whatever the conversion
   @Test
   void emptyAndNotApplicableCellsAreNullWithAFormat() throws Exception {
      List<Object> values = change(column("29/02/2024", "", "N/A", "n/a"), XSchema.DATE,
                                   new SimpleDateFormat("dd/MM/yyyy"), false);

      assertEquals(4, values.size());
      assertDate(LocalDate.of(2024, 2, 29), values.get(0));
      assertNull(values.get(1));
      assertNull(values.get(2));
      assertNull(values.get(3));
   }

   @Test
   void dateThatDoesNotFitTheFormatIsAnErrorUnlessTheConversionIsForced() throws Exception {
      assertThrows(Exception.class, () -> change(column("29/02/2024", "not a date"),
                                                 XSchema.DATE, new SimpleDateFormat("dd/MM/yyyy"),
                                                 false));

      List<Object> forced = change(column("29/02/2024", "not a date"), XSchema.DATE,
                                   new SimpleDateFormat("dd/MM/yyyy"), true);

      assertDate(LocalDate.of(2024, 2, 29), forced.get(0));
      assertNull(forced.get(1));
   }

   // without a format a date that cannot be read is null, not an error
   @Test
   void dateThatCannotBeReadWithoutAFormatIsNull() throws Exception {
      List<Object> values = change(column("2024-02-29", "not a date"), XSchema.DATE, null, false);

      assertDate(LocalDate.of(2024, 2, 29), values.get(0));
      assertNull(values.get(1));
   }

   // ---- the table ----

   @Test
   void typeOfTheColumnIsTheNewType() throws Exception {
      XEmbeddedTable table = column("1", "2");
      change(table, XSchema.INTEGER, null, false);

      assertEquals(Integer.class, table.getColType(0));
   }

   // only the columns in the list change, the rows keep their order, and the same text gives the
   // same value
   @Test
   void otherColumnsAndTheOrderOfTheRowsAreKept() throws Exception {
      XEmbeddedTable table = new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.STRING, XSchema.STRING },
         new Object[][] { { "a", "n", "s" }, { "x", "3", "p" }, { "y", "1", "q" },
                          { "z", "3", "r" } });
      table.setDataTypes(List.of(1), List.of(XSchema.INTEGER),
                         java.util.Collections.singletonList(null), null, List.of(false));
      table.moreRows(XTable.EOT);

      assertEquals(4, table.getRowCount());
      assertEquals(List.of("x", 3, "p"), row(table, 1));
      assertEquals(List.of("y", 1, "q"), row(table, 2));
      assertEquals(List.of("z", 3, "r"), row(table, 3));
      assertEquals(String.class, table.getColType(0));
      assertEquals(Integer.class, table.getColType(1));
      assertEquals(String.class, table.getColType(2));
   }

   private static List<Object> row(XTable table, int r) {
      List<Object> row = new ArrayList<>();

      for(int c = 0; c < table.getColCount(); c++) {
         row.add(table.getObject(r, c));
      }

      return row;
   }
}
