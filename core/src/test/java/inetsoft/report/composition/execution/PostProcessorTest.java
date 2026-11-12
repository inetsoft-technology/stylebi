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

package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.filter.*;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.Condition;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import inetsoft.util.script.ExpressionFailedException;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
public class PostProcessorTest {
   @Test
   public void testSerializeConditionFilter2() throws Exception {
      final Condition condition = new Condition();
      condition.setOperation(Condition.GREATER_THAN);
      condition.addValue(2);
      condition.setType(XSchema.INTEGER);
      final ConditionGroup conditionGroup = new ConditionGroup();
      conditionGroup.addCondition(1, condition, 0);

      TableLens originalTable = PostProcessor.filter(XTableUtil.getDefaultTableLens(),
                                                     conditionGroup);
      TestSerializeUtils.serializeAndDeserialize(originalTable);
   }

   @Test
   public void testSerializeTableSummaryFilter2() throws Exception {
      int[] sumCols = new int[]{ 1, 2 };
      Formula[] formulas = new Formula[]{
         new SumFormula(),
         new AverageFormula()
      };
      TableLens originalTable = PostProcessor.tableSummary(XTableUtil.getDefaultTableLens(),
                                                           sumCols, formulas);
      TestSerializeUtils.serializeAndDeserialize(originalTable);
   }

   // -----------------------------------------------------------------------
   // formula(): detail (row-level) calculated fields (Test Case #80957, vscalc G5).
   // AssetQuery.addExpression collects the expression columns and calls formula() with the
   // declared class of each column (Tool.getDataClass of its data type); FormulaTableLens
   // runs the script per row and converts the result to that class with Tool.getData,
   // keeping the script result when the conversion gives null. The expressions are the
   // shapes vscalc uses; expected values are computed by hand.
   // -----------------------------------------------------------------------

   private static final String[] COLS = { "price", "customer_id", "region_id", "state",
                                          "city", "orderdate" };

   private static Object[][] rows(Object[]... rows) {
      Object[][] data = new Object[rows.length + 1][];
      data[0] = COLS;
      System.arraycopy(rows, 0, data, 1, rows.length);
      return data;
   }

   /** formula() as AssetQuery calls it: one expression column of the given data type. */
   private static TableLens calc(Object[][] data, String expression, String dataType) {
      List<Class<?>> types = new ArrayList<>();
      types.add(Tool.getDataClass(dataType));
      TableLens table = PostProcessor.formula(
         new DefaultTableLens(data), new String[] { "calc" }, new String[] { expression },
         new GraalJavaScriptEnv(), null, null, "T", new ArrayList<>(), types,
         new boolean[1]);
      table.moreRows(TableLens.EOT);
      return table;
   }

   /** The calculated column of every data row. */
   private static List<Object> values(TableLens table) {
      List<Object> values = new ArrayList<>();
      int col = table.getColCount() - 1;

      for(int r = 1; table.moreRows(r); r++) {
         values.add(table.getObject(r, col));
      }

      return values;
   }

   private static Timestamp ts(String value) {
      return Timestamp.valueOf(value + " 00:00:00");
   }

   // arithmetic (vscalc: price/customer_id)
   @Test
   void formula_divideTwoColumns_givesDouble() {
      TableLens t = calc(rows(new Object[] { 10.0, 4, 1, "NY", "Albany", null },
                              new Object[] { 7.5, 3, 1, "NY", "Albany", null }),
                         "field['price'] / field['customer_id']", XSchema.DOUBLE);
      assertEquals(Arrays.asList(2.5, 2.5), values(t));
   }

   // arithmetic in an integer column (vscalc: CUSTOMER_ID*REGION_ID, customer_id+10): the
   // script number is converted to the declared Integer
   @Test
   void formula_integerArithmetic_convertedToInteger() {
      Object[][] data = rows(new Object[] { 1.0, 3, 4, "NY", "Albany", null },
                             new Object[] { 1.0, 5, 2, "NY", "Albany", null });
      assertEquals(Arrays.asList(12, 10),
                   values(calc(data, "field['customer_id'] * field['region_id']",
                               XSchema.INTEGER)));
      assertEquals(Arrays.asList(13, 15),
                   values(calc(data, "field['customer_id'] + 10", XSchema.INTEGER)));
   }

   // subtraction with fractions and a negative result (vscalc: TotalPurchased-TotalReturned)
   @Test
   void formula_subtraction_keepsFractionAndSign() {
      TableLens t = calc(rows(new Object[] { 100.5, 20, 1, "NY", "Albany", null },
                              new Object[] { 10.25, 30, 1, "NY", "Albany", null }),
                         "field['price'] - field['customer_id']", XSchema.DOUBLE);
      assertEquals(Arrays.asList(80.5, -19.75), values(t));
   }

   // divide by zero and a null operand follow JavaScript: x / 0 is Infinity (returned as
   // null, ScriptValueConverter turns Infinity into null) and null * 2 is 0.
   // Current behavior, not in #80953 (that covers aggregate calc fields)
   @Test
   void formula_divideByZeroAndNullOperand_followJavaScript() {
      Object[][] data = rows(new Object[] { 10.0, 0, 1, "NY", "Albany", null },
                             new Object[] { null, 2, 1, "NY", "Albany", null });
      // 10 / 0 is Infinity, null / 2 is 0
      assertEquals(Arrays.asList(null, 0.0),
                   values(calc(data, "field['price'] / field['customer_id']",
                               XSchema.DOUBLE)));
      // 10 * 2, null * 2
      assertEquals(Arrays.asList(20.0, 0.0),
                   values(calc(data, "field['price'] * 2", XSchema.DOUBLE)));
   }

   // string concatenation (vscalc: field['State']+"_"+field['City'])
   @Test
   void formula_concatenateTwoColumns() {
      TableLens t = calc(rows(new Object[] { 1.0, 1, 1, "NY", "Albany", null },
                              new Object[] { 1.0, 1, 1, "CA", "Fresno", null }),
                         "field['state'] + '_' + field['city']", XSchema.STRING);
      assertEquals(Arrays.asList("NY_Albany", "CA_Fresno"), values(t));
   }

   // a null in concatenation is the text "null" (JavaScript). Current behavior, not confirmed
   @Test
   void formula_concatenateWithNull_givesTheTextNull() {
      TableLens t = calc(rows(new Object[] { 1.0, 1, 1, null, "Albany", null }),
                         "field['state'] + '_' + field['city']", XSchema.STRING);
      assertEquals(Arrays.asList("null_Albany"), values(t));
   }

   // concat(): vscalc writes concat(field['customers:state'],'a') as a SQL expression. When
   // the query cannot be merged into the database, AssetQuery.addExpression runs the same
   // text as script (ExpressionRef.getScriptExpression), and the script has no concat, so
   // reading the formula table fails (end to end, AssetQuery then shows the design-time
   // sample table, see AssetQueryExpressionColumnTest). Current behavior
   @Test
   void formula_sqlFunctionRunAsScript_failsTheTable() {
      ExpressionFailedException ex = assertThrows(ExpressionFailedException.class, () ->
         calc(rows(new Object[] { 1.0, 1, 1, "NY", "Albany", null }),
              "concat(field['state'], 'a')", XSchema.STRING));
      assertTrue(ex.getMessage().contains("concat is not defined"), ex.getMessage());
   }

   // date parts (vscalc: CALC.quarter / year / day); 2001-08-15 is in quarter 3
   @Test
   void formula_dateParts() {
      Object[][] data = rows(new Object[] { 1.0, 1, 1, "NY", "Albany", ts("2001-08-15") });
      assertEquals(Arrays.asList(3), values(calc(data, "CALC.quarter(field['orderdate'])",
                                                 XSchema.INTEGER)));
      assertEquals(Arrays.asList(2001), values(calc(data, "CALC.year(field['orderdate'])",
                                                    XSchema.INTEGER)));
      assertEquals(Arrays.asList(15), values(calc(data, "CALC.day(field['orderdate'])",
                                                  XSchema.INTEGER)));
   }

   // date arithmetic (vscalc: dateAdd('m', 5, ...), dateAdd('d', 4, ...)); 5 months added
   // to the 30th of September clamp to the end of February
   @Test
   void formula_dateAdd_monthsAndDays() {
      Object[][] data = rows(new Object[] { 1.0, 1, 1, "NY", "Albany", ts("2001-08-31") });
      assertEquals(ts("2002-01-31"), values(calc(data, "dateAdd('m', 5, field['orderdate'])",
                                                 XSchema.TIME_INSTANT)).get(0));
      assertEquals(ts("2001-09-04"), values(calc(data, "dateAdd('d', 4, field['orderdate'])",
                                                 XSchema.TIME_INSTANT)).get(0));

      Object[][] end = rows(new Object[] { 1.0, 1, 1, "NY", "Albany", ts("2001-09-30") });
      assertEquals(ts("2002-02-28"), values(calc(end, "dateAdd('m', 5, field['orderdate'])",
                                                 XSchema.TIME_INSTANT)).get(0));
   }

   // CALC.edate (vscalc: CALC.edate(field['orderdate'], 2)) clamps to the month end across a
   // year boundary too (#80953 question 6, fixed)
   @Test
   void formula_edate_clampsToMonthEndAcrossYears() {
      Object[][] data = rows(new Object[] { 1.0, 1, 1, "NY", "Albany", ts("2000-12-31") },
                             new Object[] { 1.0, 1, 1, "NY", "Albany", ts("2001-01-15") });
      List<Object> result = values(calc(data, "CALC.edate(field['orderdate'], 2)",
                                        XSchema.TIME_INSTANT));
      assertEquals(ts("2001-02-28"), result.get(0));
      assertEquals(ts("2001-03-15"), result.get(1));
   }

   // a declared integer column with a fractional result: the value is converted to Integer
   @Test
   void formula_declaredIntegerWithFraction_convertedToInteger() {
      TableLens t = calc(rows(new Object[] { 7.0, 2, 1, "NY", "Albany", null }),
                         "field['price'] / field['customer_id']", XSchema.INTEGER);
      Object value = values(t).get(0);
      assertInstanceOf(Integer.class, value);
      assertEquals(3, value);
   }

   // a declared boolean column
   @Test
   void formula_declaredBoolean() {
      TableLens t = calc(rows(new Object[] { 7.0, 2, 1, "NY", "Albany", null },
                              new Object[] { 3.0, 2, 1, "NY", "Albany", null }),
                         "field['price'] > 5", XSchema.BOOLEAN);
      assertEquals(Arrays.asList(true, false), values(t));
   }

   // a result that cannot be converted to the declared type is kept as the script gave it
   // (Tool.getData returns null, FormulaTableLens keeps the original)
   @Test
   void formula_unconvertibleResult_keptAsIs() {
      TableLens t = calc(rows(new Object[] { 1.0, 1, 1, "NY", "Albany", null }),
                         "field['state'] + 'x'", XSchema.INTEGER);
      assertEquals(Arrays.asList("NYx"), values(t));
   }

   // the column-map shortcut: every expression is field['x'] with the source column's type,
   // so no script runs and the source values are mapped
   @Test
   void formula_plainFieldOfTheSameType_isMappedWithoutScript() {
      TableLens t = calc(rows(new Object[] { 1.0, 1, 1, "NY", "Albany", null },
                              new Object[] { 1.0, 1, 1, "CA", "Fresno", null }),
                         "field['state']", XSchema.STRING);
      assertInstanceOf(ColumnMapFilter.class, t);
      assertEquals("calc", t.getObject(0, t.getColCount() - 1));
      assertEquals(Arrays.asList("NY", "CA"), values(t));
   }

   // field['x'] with another declared type is not a shortcut: the script runs and the value
   // is converted (state "12" as an integer)
   @Test
   void formula_plainFieldOfAnotherType_runsTheScript() {
      TableLens t = calc(rows(new Object[] { 1.0, 1, 1, "12", "Albany", null }),
                         "field['state']", XSchema.INTEGER);
      assertFalse(t instanceof ColumnMapFilter);
      assertEquals(Arrays.asList(12), values(t));
   }

   // -----------------------------------------------------------------------
   // Test Case #80957 hardening (vscalc matrix rows P*, T*)
   // -----------------------------------------------------------------------

   private static Object[][] one(Object price, Object customerId, Object state, Object city,
                                 Object orderdate)
   {
      return rows(new Object[] { price, customerId, 1, state, city, orderdate });
   }

   private static Object[][] oneDate(Object orderdate) {
      return one(1.0, 1, "NY", "Albany", orderdate);
   }

   private static Object first(Object[][] data, String expression, String type) {
      return values(calc(data, expression, type)).get(0);
   }

   private static Object[][] onePrice(Object price) {
      return one(price, 2, "NY", "Albany", null);
   }

   @Test
   @DisplayName("Edate clamps to the last day of a leap February")
   void edate_leapYear() {
      assertEquals(Timestamp.valueOf("2004-02-29 00:00:00"),
                   first(oneDate(ts("2003-12-31")), "CALC.edate(field['orderdate'], 2)",
                         XSchema.TIME_INSTANT));
   }

   @Test
   @DisplayName("Edate with negative months")
   void edate_negativeMonths() {
      assertEquals(ts("2000-02-29"), first(oneDate(ts("2000-03-31")),
                                           "CALC.edate(field['orderdate'], -1)",
                                           XSchema.TIME_INSTANT));
      assertEquals(ts("2000-11-30"), first(oneDate(ts("2001-01-31")),
                                           "CALC.edate(field['orderdate'], -2)",
                                           XSchema.TIME_INSTANT));
   }

   @Test
   @DisplayName("January 31 plus one month is the end of February")
   void edate_january31() {
      assertEquals(ts("2001-02-28"), first(oneDate(ts("2001-01-31")),
                                           "CALC.edate(field['orderdate'], 1)",
                                           XSchema.TIME_INSTANT));
   }

   @Test
   @DisplayName("Edate keeps the time of day")
   void edate_keepsTimeOfDay() {
      assertEquals(Timestamp.valueOf("2001-02-28 13:45:10"),
                   first(oneDate(Timestamp.valueOf("2001-01-31 13:45:10")),
                         "CALC.edate(field['orderdate'], 1)", XSchema.TIME_INSTANT));
   }

   @Test
   @DisplayName("Edate takes the months as numeric text")
   void edate_monthsAsText() {
      assertEquals(ts("2001-04-15"), first(oneDate(ts("2001-01-15")),
                                           "CALC.edate(field['orderdate'], '3')",
                                           XSchema.TIME_INSTANT));
   }

   @Test
   @DisplayName("Eomonth is the last day of the month the given months away")
   void eomonth() {
      assertEquals(ts("2015-06-30"), first(oneDate(ts("2015-07-31")),
                                           "CALC.eomonth(field['orderdate'], -1)",
                                           XSchema.TIME_INSTANT));
      assertEquals(ts("2016-02-29"), first(oneDate(ts("2016-01-15")),
                                           "CALC.eomonth(field['orderdate'], 1)",
                                           XSchema.TIME_INSTANT));
   }

   @Test
   @DisplayName("Date arithmetic of a null date is null")
   void nullDate_arithmeticIsNull() {
      Object[][] data = oneDate(null);

      for(String expression : new String[] { "dateAdd('m', 1, field['orderdate'])",
                                             "CALC.edate(field['orderdate'], 1)",
                                             "CALC.eomonth(field['orderdate'], 1)" })
      {
         assertNull(first(data, expression, XSchema.TIME_INSTANT), expression);
      }
   }

   @Disabled("Bug #81204: CALC.quarter(null) returns 1 instead of -1")
   @Test
   @DisplayName("The quarter of a null date is -1, like year, month, day and hour")
   void nullDate_quarterIsMinusOne() {
      assertEquals(-1, first(oneDate(null), "CALC.quarter(field['orderdate'])", XSchema.INTEGER));
   }

   @Test
   @DisplayName("PIN(current behavior, pending dev #80953): year, month, day and hour " +
      "of a null date are -1")
   void pin_nullDate_partsAreMinusOne() {
      for(String part : new String[] { "year", "month", "day", "hour" }) {
         assertEquals(-1, first(oneDate(null), "CALC." + part + "(field['orderdate'])",
                                XSchema.INTEGER), part);
      }
   }

   @Disabled("Bug #81200: Declared Integer clamps a larger result")
   @Test
   @DisplayName("A declared Integer never turns a value beyond int into a clamped number")
   void integerBeyondInt_isNotClamped() {
      Object value = first(onePrice(5000.0), "field['price'] * 1000000", XSchema.INTEGER);
      assertTrue(value == null || ((Number) value).doubleValue() == 5e9, "value " + value);
   }

   @Test
   @DisplayName("A number declared String is its JavaScript text")
   void numberDeclaredString() {
      assertEquals("20", first(onePrice(10.0), "field['price'] * 2", XSchema.STRING));
   }

   @Test
   @DisplayName("Text and numbers declared Boolean")
   void declaredBoolean_fromTextAndNumbers() {
      Object[][] data = onePrice(1.0);
      assertEquals(true, first(data, "'true'", XSchema.BOOLEAN));
      assertEquals(true, first(data, "1", XSchema.BOOLEAN));
      assertEquals(true, first(data, "'1'", XSchema.BOOLEAN));
      assertEquals(false, first(data, "0", XSchema.BOOLEAN));
      assertEquals(false, first(data, "'false'", XSchema.BOOLEAN));
      assertEquals(false, first(data, "'no'", XSchema.BOOLEAN));
   }

   @Test
   @DisplayName("PIN(current behavior, pending dev #80953): 'yes' declared Boolean is false")
   void pin_yesIsFalse() {
      assertEquals(false, first(onePrice(1.0), "'yes'", XSchema.BOOLEAN));
   }

   @Test
   @DisplayName("Date and time declared types give the declared class")
   void declaredDateAndTime() {
      Timestamp stamp = Timestamp.valueOf("2001-08-15 13:45:10");
      Object[][] data = oneDate(stamp);
      Object date = first(data, "field['orderdate']", XSchema.DATE);
      assertEquals(java.sql.Date.class, date.getClass());
      assertEquals("2001-08-15", date.toString());

      Object time = first(data, "field['orderdate']", XSchema.TIME);
      assertEquals(java.sql.Time.class, time.getClass());
      assertEquals("13:45:10", time.toString());

      assertEquals(stamp, first(data, "field['orderdate']", XSchema.TIME_INSTANT));

      Object added = first(oneDate(ts("2001-08-15")), "dateAdd('d', 1, field['orderdate'])",
                           XSchema.DATE);
      assertEquals(java.sql.Date.class, added.getClass());
      assertEquals("2001-08-16", added.toString());
   }

   @Test
   @DisplayName("Undefined, NaN, Infinity and null results are null")
   void undefinedNanInfinityNull_areNull() {
      for(String type : new String[] { XSchema.DOUBLE, XSchema.INTEGER, XSchema.STRING }) {
         for(String expression : new String[] { "undefined", "Math.sqrt(-1)", "0/0", "1/0",
                                                "null" })
         {
            assertNull(first(onePrice(1.0), expression, type), type + " " + expression);
         }

         // an empty text is kept as it is
         assertEquals("", first(onePrice(1.0), "''", type), type);
      }
   }

   @Test
   @DisplayName("Several expression columns in one call")
   void severalExpressionColumns() {
      Object[][] data = rows(new Object[] { 10.0, 1, 1, "NY", "Albany", ts("2001-08-15") },
                             new Object[] { 2.5, 2, 1, "CA", "Fresno", ts("2002-02-01") });
      List<Class<?>> types = new ArrayList<>();
      types.add(Double.class);
      types.add(String.class);
      types.add(Integer.class);
      TableLens table = PostProcessor.formula(
         new DefaultTableLens(data), new String[] { "twice", "bang", "year" },
         new String[] { "field['price'] * 2", "field['state'] + '!'",
                        "CALC.year(field['orderdate'])" },
         new GraalJavaScriptEnv(), null, null, "T", new ArrayList<>(), types, new boolean[3]);
      table.moreRows(TableLens.EOT);
      int base = table.getColCount() - 3;
      assertEquals("twice", table.getObject(0, base));
      assertEquals("bang", table.getObject(0, base + 1));
      assertEquals("year", table.getObject(0, base + 2));
      assertEquals(20.0, table.getObject(1, base));
      assertEquals("NY!", table.getObject(1, base + 1));
      assertEquals(2001, table.getObject(1, base + 2));
      assertEquals(5.0, table.getObject(2, base));
      assertEquals("CA!", table.getObject(2, base + 1));
      assertEquals(2002, table.getObject(2, base + 2));
   }

   @Test
   @DisplayName("A table without rows and with one row")
   void emptyAndSingleRow() {
      assertEquals(0, values(calc(rows(), "field['price'] * 2", XSchema.DOUBLE)).size());
      assertEquals(Arrays.asList(4.0),
                   values(calc(onePrice(2.0), "field['price'] * 2", XSchema.DOUBLE)));
   }

   @Test
   @DisplayName("Quotes, a newline and non-BMP characters pass through the script")
   void specialCharacters() {
      String city = "Zoë\nX😀";
      assertEquals("O'Brien|" + city,
                   first(one(1.0, 1, "O'Brien", city, null),
                         "field['state'] + '|' + field['city']", XSchema.STRING));
   }

   @Test
   @DisplayName("An integer column declared Double is converted, the same type is mapped")
   void shortcutVersusConversion() {
      Object[][] data = rows(new Object[] { 1.0, 3, 1, "NY", "Albany", null },
                             new Object[] { 7.0, 7, 1, "NY", "Albany", null });
      TableLens toDouble = calc(data, "field['customer_id']", XSchema.DOUBLE);
      assertInstanceOf(inetsoft.report.lens.FormulaTableLens.class, toDouble);
      assertEquals(Arrays.asList(3.0, 7.0), values(toDouble));

      TableLens same = calc(data, "field['customer_id']", XSchema.INTEGER);
      assertInstanceOf(ColumnMapFilter.class, same);
      assertEquals(Arrays.asList(3, 7), values(same));

      TableLens toInteger = calc(data, "field['price']", XSchema.INTEGER);
      assertInstanceOf(inetsoft.report.lens.FormulaTableLens.class, toInteger);
      assertEquals(Arrays.asList(1, 7), values(toInteger));
   }

   @Test
   @DisplayName("A script error in a row fails the table")
   void scriptErrorOnOneRow() {
      Object[][] data = rows(new Object[] { 1.0, 1, 1, "NY", "Albany", null },
                             new Object[] { 1.0, 2, 1, null, "Albany", null });
      assertThrows(ExpressionFailedException.class,
                   () -> calc(data, "field['state'].length + 1", XSchema.INTEGER));
   }

   @Test
   @DisplayName("A column name with spaces, a dollar sign or a quote")
   void specialColumnNames() {
      Object[][] data = { { "Total Price ($)", "O'Brien" }, { 4.0, "x" }, { 6.5, "y" } };
      List<Class<?>> types = new ArrayList<>();
      types.add(Double.class);
      types.add(String.class);
      TableLens table = PostProcessor.formula(
         new DefaultTableLens(data), new String[] { "twice", "copy" },
         new String[] { "field['Total Price ($)'] * 2", "field[\"O'Brien\"]" },
         new GraalJavaScriptEnv(), null, null, "T", new ArrayList<>(), types, new boolean[2]);
      table.moreRows(TableLens.EOT);
      int base = table.getColCount() - 2;
      assertEquals(8.0, table.getObject(1, base));
      assertEquals("x", table.getObject(1, base + 1));
      assertEquals(13.0, table.getObject(2, base));
      assertEquals("y", table.getObject(2, base + 1));
   }

   @Test
   @DisplayName("PIN(current behavior, pending dev #80953): a declared Integer truncates " +
      "toward zero")
   void pin_integerTruncatesTowardZero() {
      assertEquals(Arrays.asList(2, 3, -3, 2),
                   values(calc(rows(new Object[] { 2.5, 1, 1, "NY", "Albany", null },
                                    new Object[] { 3.5, 1, 1, "NY", "Albany", null },
                                    new Object[] { -3.5, 1, 1, "NY", "Albany", null },
                                    new Object[] { 2.999999, 1, 1, "NY", "Albany", null }),
                               "field['price']", XSchema.INTEGER)));
   }

   // the identity cells (declared equals the natural result), one provider
   @ParameterizedTest(name = "declared type equals the natural result: {0}")
   @MethodSource("identityCells")
   void identityCells(String type, String expression, Object expected) {
      Object[][] data = rows(new Object[] { 10.0, 4, 1, "NY", "Albany", ts("2001-08-31") });
      Object value = values(calc(data, expression, type)).get(0);
      assertEquals(expected, value);
   }

   static java.util.stream.Stream<Arguments> identityCells() {
      return java.util.stream.Stream.of(
         Arguments.of(XSchema.INTEGER, "field['customer_id'] + 10", 14),
         Arguments.of(XSchema.DOUBLE, "field['price'] / field['customer_id']", 2.5),
         Arguments.of(XSchema.STRING, "field['state'] + '_' + field['city']", "NY_Albany"),
         Arguments.of(XSchema.BOOLEAN, "field['price'] > 5", true),
         Arguments.of(XSchema.TIME_INSTANT, "dateAdd('d', 4, field['orderdate'])",
                      Timestamp.valueOf("2001-09-04 00:00:00")),
         Arguments.of(XSchema.DATE, "dateAdd('d', 4, field['orderdate'])",
                      java.sql.Date.valueOf("2001-09-04")));
   }

   @Test
   @DisplayName("A numeric result declared String or Boolean follows JavaScript")
   void numericResult_declaredStringAndBoolean() {
      Object[][] data = one(7.0, 2, "NY", "Albany", null);
      String expression = "field['price'] / field['customer_id']";
      assertEquals("3.5", first(data, expression, XSchema.STRING));
      assertEquals(true, first(data, expression, XSchema.BOOLEAN));
      // declared Integer truncates (PIN)
      assertEquals(3, first(data, expression, XSchema.INTEGER));
   }

   @Test
   @DisplayName("A text result is kept when it cannot be converted, converted when it can")
   void textResult_perDeclaredType() {
      Object[][] data = onePrice(1.0);

      for(String type : new String[] { XSchema.INTEGER, XSchema.DOUBLE, XSchema.DATE,
                                       XSchema.TIME_INSTANT, XSchema.TIME })
      {
         assertEquals("abc", first(data, "'abc'", type), type);
      }

      assertEquals(12.5, first(data, "'12.5'", XSchema.DOUBLE));
      assertEquals(12, first(data, "'12'", XSchema.INTEGER));
      assertEquals(true, first(data, "'true'", XSchema.BOOLEAN));
      assertEquals("2001-08-15", first(data, "'2001-08-15'", XSchema.DATE).toString());
      assertEquals(ts("2001-08-15"), first(data, "'2001-08-15'", XSchema.TIME_INSTANT));
      assertEquals(Timestamp.valueOf("2001-08-15 10:20:30"),
                   first(data, "'2001-08-15 10:20:30'", XSchema.TIME_INSTANT));
   }

   @Test
   @DisplayName("PIN(current behavior, pending dev #80953): 'abc' declared Boolean is " +
      "false and '12.5' declared Integer is 12")
   void pin_booleanAndIntegerFromText() {
      Object[][] data = onePrice(1.0);
      assertEquals(false, first(data, "'abc'", XSchema.BOOLEAN));
      assertEquals(12, first(data, "'12.5'", XSchema.INTEGER));
   }

   @Test
   @DisplayName("A date result declared Double, Integer or String")
   void dateResult_declaredOtherTypes() {
      Object[][] data = oneDate(ts("2001-08-15"));
      assertEquals(2001.0, first(data, "CALC.year(field['orderdate'])", XSchema.DOUBLE));
      assertEquals(ts("2001-08-15"), first(data, "field['orderdate']", XSchema.INTEGER));
      assertEquals(ts("2001-08-15"), first(data, "field['orderdate']", XSchema.DOUBLE));
   }

   @Test
   @DisplayName("PIN(current behavior, pending dev #80953): a date declared String is " +
      "the Timestamp text")
   void pin_dateDeclaredString() {
      assertEquals("2001-08-15 00:00:00.0",
                   first(oneDate(ts("2001-08-15")), "field['orderdate']", XSchema.STRING));
   }

   @Test
   @DisplayName("A Boolean result declared String is the JavaScript text, other types keep it")
   void booleanResult_declaredOtherTypes() {
      Object[][] data = rows(new Object[] { 7.0, 1, 1, "NY", "Albany", null },
                             new Object[] { 3.0, 1, 1, "NY", "Albany", null });
      assertEquals(Arrays.asList("true", "false"),
                   values(calc(data, "field['price'] > 5", XSchema.STRING)));
      assertEquals(Arrays.asList(true, false),
                   values(calc(data, "field['price'] > 5", XSchema.INTEGER)));
      assertEquals(Arrays.asList(true, false),
                   values(calc(data, "field['price'] > 5", XSchema.DOUBLE)));
   }

   @Test
   @DisplayName("A null column and undefined stay null for every declared type")
   void nullAndUndefined_perDeclaredType() {
      Object[][] data = oneDate(null);

      for(String type : new String[] { XSchema.INTEGER, XSchema.DOUBLE, XSchema.STRING,
                                       XSchema.DATE, XSchema.TIME_INSTANT, XSchema.BOOLEAN,
                                       XSchema.TIME })
      {
         assertNull(first(data, "field['orderdate']", type), type + " null column");
         assertNull(first(data, "undefined", type), type + " undefined");
      }
   }

   @Test
   @DisplayName("PIN(current behavior, pending dev #80953): null * 1 is the number 0 " +
      "converted to every declared type")
   void pin_nullTimesOne() {
      Object[][] data = oneDate(null);
      assertEquals(0, first(data, "null * 1", XSchema.INTEGER));
      assertEquals(0.0, first(data, "null * 1", XSchema.DOUBLE));
      assertEquals("0", first(data, "null * 1", XSchema.STRING));
      assertEquals(false, first(data, "null * 1", XSchema.BOOLEAN));
      assertEquals(new java.sql.Date(0).toString(),
                   first(data, "null * 1", XSchema.DATE).toString());
      assertEquals(new Timestamp(0), first(data, "null * 1", XSchema.TIME_INSTANT));
      assertEquals(new java.sql.Time(0).toString(),
                   first(data, "null * 1", XSchema.TIME).toString());
   }
}
