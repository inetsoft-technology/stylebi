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

import inetsoft.report.filter.*;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class AssetQueryTest {

   @Test
   public void testGroupedExpressionColumnIsRecognised() {
      ExpressionRef expr = new ExpressionRef(null, "Range@customer_id");
      ColumnRef column = new ColumnRef(expr);
      column.setVisible(true);

      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(column));

      boolean recognized = info.containsGroup(column)
         || info.getGroup(column) != null
         || info.getGroup(column.getName()) != null;

      Assertions.assertTrue(recognized,
         "AggregateInfo must find a grouped expression column via at least one lookup path");
   }

   @Test
   public void testSerializeFormatTableLens() throws Exception {
      AssetQuery.FormatTableLens originalTable = new AssetQuery.FormatTableLens(XTableUtil.getDefaultTableLens());
      originalTable.setFormat(1, XUtil.getDefaultDateFormat(DateRangeRef.MONTH_OF_YEAR_PART, XSchema.DATE));
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(AssetQuery.FormatTableLens.class, deserializedTable.getClass());
   }

   @Test
   public void testSerializeXArrayTable() throws Exception {
      AssetQuery.XArrayTable originalTable = new AssetQuery.XArrayTable();
      originalTable.setData(new Object[]{ "a", 1, 2.0 });
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(AssetQuery.XArrayTable.class, deserializedTable.getClass());
   }

   @Test
   public void testSerializeSummaryFilter2() throws Exception {
      AssetQuery.SummaryFilter2 originalTable =
         new AssetQuery.SummaryFilter2(XTableUtil.getDefaultTableLens(), new int[]{ 0 },
                                       new int[]{ 2 }, new Formula[]{ new SumFormula() }, false,
                                       null, new String[]{ "col2" });
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(AssetQuery.SummaryFilter2.class, deserializedTable.getClass());
   }

   private static ExpressionRef script(String expression) {
      ExpressionRef exp = new ExpressionRef(null, "Calc");
      exp.setExpression(expression);
      return exp;
   }

   private static XFormatInfo format(String format) {
      return new XFormatInfo(format, "spec");
   }

   private static boolean inherits(String expression, String refName, XFormatInfo refFormat,
                                   String calcType)
   {
      return AssetQuery.inheritsReferencedFormat(
         script(expression), new AttributeRef(null, refName), refFormat, calcType);
   }

   /**
    * Bug #79289 (VCF-035): an integer calc field computed from a percent or currency column took
    * that column's format (Math.round(field['Discount'] * 100) rendered 500%). #78971 only stopped
    * it for a date format.
    */
   @Test
   public void testCalcFieldDoesNotInheritPercentOrCurrencyOfAComputedValue() {
      XFormatInfo percent = format(TableFormat.PERCENT_FORMAT);
      XFormatInfo currency = format(TableFormat.CURRENCY_FORMAT);
      XFormatInfo decimal = format(TableFormat.DECIMAL_FORMAT);

      for(XFormatInfo ref : new XFormatInfo[] { percent, currency, decimal }) {
         Assertions.assertFalse(
            inherits("Math.round(field['Discount'] * 100)", "Discount", ref, XSchema.INTEGER));
         Assertions.assertFalse(
            inherits("field['Discount'] * 100", "Discount", ref, XSchema.DOUBLE));
         Assertions.assertFalse(
            inherits("field['Total'] + field['Tax']", "Total", ref, XSchema.DOUBLE));
         Assertions.assertFalse(
            inherits("field['Total'] > 10 ? field['Total'] : 0", "Total", ref, XSchema.DOUBLE));
      }
   }

   @Test
   public void testCalcFieldInheritsTheFormatOfAColumnItPassesThroughUnchanged() {
      XFormatInfo percent = format(TableFormat.PERCENT_FORMAT);
      XFormatInfo currency = format(TableFormat.CURRENCY_FORMAT);

      Assertions.assertTrue(inherits("field['Discount']", "Discount", percent, XSchema.DOUBLE));
      Assertions.assertTrue(inherits("  field['Total'] ;", "Total", currency, XSchema.DOUBLE));
      Assertions.assertTrue(inherits("field[\"Total\"]", "Total", currency, XSchema.DOUBLE));
      Assertions.assertTrue(inherits("field['Discount']", "Discount", null, XSchema.DOUBLE),
                            "no format on the column: nothing to leak, nothing changes");
      Assertions.assertFalse(inherits("field['Other']", "Discount", percent, XSchema.DOUBLE),
                             "a read of a different column is not a pass-through of this one");
   }

   /** The #78971 behavior stays: a date format follows date calc fields only. */
   @Test
   public void testDateFormatStillFollowsOnlyDateCalcFields() {
      XFormatInfo date = format(TableFormat.DATE_FORMAT);

      Assertions.assertFalse(inherits("field['Order:Date']", "Order:Date", date, XSchema.INTEGER));
      Assertions.assertFalse(
         inherits("field['Order:Date'].getMonth()", "Order:Date", date, XSchema.INTEGER));
      Assertions.assertTrue(inherits("field['Order:Date']", "Order:Date", date, XSchema.DATE));
      Assertions.assertTrue(
         inherits("new Date(field['Order:Date'].getTime() + 86400000)", "Order:Date", date,
                  XSchema.DATE));
      Assertions.assertTrue(
         inherits("new Date(field['Order:Date'].getTime())", "Order:Date", date,
                  XSchema.TIME_INSTANT));
   }

   @Test
   public void testSerializeSummaryFilter2Condition() throws Exception {
      ColumnRef ref = new ColumnRef(new AttributeRef("col1"));
      Condition cond = new Condition();
      cond.addValue("b");
      cond.setOperation(XCondition.EQUAL_TO);
      ConditionItem item = new ConditionItem(ref, cond, 0);
      ConditionList conditionList = new ConditionList();
      conditionList.append(item);
      ConditionGroup conditionGroup = new ConditionGroup(conditionList);
      AssetQuery.SummaryFilter2 originalTable =
         new AssetQuery.SummaryFilter2(XTableUtil.getDefaultTableLens(), new int[]{ 0 },
                                       new int[]{ 2 }, new Formula[]{ new SumFormula() }, false,
                                       conditionGroup, new String[]{ "col2" });
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(AssetQuery.SummaryFilter2.class, deserializedTable.getClass());
   }
}
