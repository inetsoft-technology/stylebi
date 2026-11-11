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
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.filter.*;
import inetsoft.test.*;
import inetsoft.uql.Condition;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import inetsoft.util.script.ExpressionFailedException;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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

   // date arithmetic (vscalc: dateAdd('m', 5, ...), dateAdd('d', 4, ...)); a month added to
   // the 30th of September clamps to the end of February
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
}
