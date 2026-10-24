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

package inetsoft.uql.viewsheet.internal;

import inetsoft.report.filter.ColumnHighlight;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.RankingCondition;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79289 (VCF-036): renaming a calc field rewrote the field a highlight condition tests, but
 * left a field-typed comparison VALUE ({type:"field", field:"Sum(Net Revenue)"}) on the old name.
 * The old value loop called {@code renameDataRef} on the elements of {@code getDataRefValues()}, a
 * detached copy, and {@code renameDataRef} only knows {@code ColumnRef}/{@code VSDimensionRef};
 * the {@code BaseField} a crosstab highlight operand really is was silently skipped.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSUtilRenameConditionValueRefTest {
   private static BaseField aggregate(String name) {
      BaseField field = new BaseField(null, name);
      field.setDataType(XSchema.DOUBLE);
      return field;
   }

   private static ConditionList listOf(DataRef attribute, Condition condition) {
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(attribute, condition, 0));
      return list;
   }

   private static Condition greaterThan(Object... values) {
      Condition condition = new Condition();
      condition.setOperation(XCondition.GREATER_THAN);

      for(Object value : values) {
         condition.addValue(value);
      }

      return condition;
   }

   private static Object value(ConditionList list, int index) {
      return list.getConditionItem(0).getCondition().getValue(index);
   }

   @Test
   void renamesABaseFieldValueOperandAndKeepsItsType() {
      ConditionList list = listOf(aggregate("Sum(Product:Total)"),
         greaterThan(aggregate("Sum(Net Revenue)")));

      VSUtil.renameConditionListRef(list, "Net Revenue", "Net Sales");

      Object operand = value(list, 0);
      assertInstanceOf(BaseField.class, operand);
      assertEquals("Sum(Net Sales)", ((DataRef) operand).getName());
      assertEquals(XSchema.DOUBLE, ((DataRef) operand).getDataType());
      assertEquals("Sum(Product:Total)", list.getConditionItem(0).getAttribute().getName(),
         "the tested field is not the renamed one and must stay as it was");
   }

   @Test
   void renamesAPlainAttributeValueOperand() {
      ConditionList list = listOf(aggregate("Sum(Product:Total)"),
         greaterThan(new AttributeRef(null, "Net Revenue")));

      VSUtil.renameConditionListRef(list, "Net Revenue", "Net Sales");

      assertEquals("Net Sales", ((DataRef) value(list, 0)).getName());
   }

   @Test
   void renamesAColumnRefValueOperand() {
      ConditionList list = listOf(aggregate("Sum(Product:Total)"),
         greaterThan(new ColumnRef(new AttributeRef(null, "Net Revenue"))));

      VSUtil.renameConditionListRef(list, "Net Revenue", "Net Sales");

      assertEquals("Net Sales", ((DataRef) value(list, 0)).getName());
   }

   @Test
   void renamesOnlyTheMatchingOperandAmongSeveral() {
      Condition between = new Condition();
      between.setOperation(XCondition.BETWEEN);
      between.addValue(aggregate("Sum(Net Revenue)"));
      between.addValue(aggregate("Sum(Tax)"));
      ConditionList list = listOf(aggregate("Sum(Product:Total)"), between);

      VSUtil.renameConditionListRef(list, "Net Revenue", "Net Sales");

      assertEquals("Sum(Net Sales)", ((DataRef) value(list, 0)).getName());
      assertEquals("Sum(Tax)", ((DataRef) value(list, 1)).getName());
   }

   @Test
   void renamesAFieldInsideAMultiValueOperand() {
      Condition oneOf = new Condition();
      oneOf.setOperation(XCondition.ONE_OF);
      oneOf.addValue(new Object[] { aggregate("Sum(Net Revenue)"), aggregate("Sum(Tax)") });
      ConditionList list = listOf(aggregate("Sum(Product:Total)"), oneOf);

      VSUtil.renameConditionListRef(list, "Net Revenue", "Net Sales");

      Object[] operands = (Object[]) value(list, 0);
      assertEquals("Sum(Net Sales)", ((DataRef) operands[0]).getName());
      assertEquals("Sum(Tax)", ((DataRef) operands[1]).getName());
   }

   @Test
   void leavesConstantsAndUnrelatedFieldsAlone() {
      ConditionList list = listOf(aggregate("Sum(Product:Total)"), greaterThan(3000000d));
      Condition other = greaterThan(aggregate("Sum(Tax)"));
      list.append(new ConditionItem(aggregate("Sum(Product:Total)"), other, 0));

      VSUtil.renameConditionListRef(list, "Net Revenue", "Net Sales");

      assertEquals(3000000d, value(list, 0));
      assertEquals("Sum(Tax)", ((DataRef) other.getValue(0)).getName());
   }

   @Test
   void renamesTheGroupFieldOfARankingCondition() {
      RankingCondition ranking = new RankingCondition();
      ranking.setOperation(XCondition.TOP_N);
      ranking.setN(3);
      ranking.setDataRef(aggregate("Sum(Net Revenue)"));
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(aggregate("Sum(Product:Total)"), ranking, 0));

      VSUtil.renameConditionListRef(list, "Net Revenue", "Net Sales");

      assertEquals("Sum(Net Sales)", ranking.getDataRef().getName());
   }

   @Test
   void reachesTheOperandOfAHighlightThroughTheHighlightGroup() {
      ConditionList list = listOf(aggregate("Sum(Product:Total)"),
         greaterThan(aggregate("Sum(Net Revenue)")));
      ColumnHighlight highlight = new ColumnHighlight();
      highlight.setConditionGroup(list);
      HighlightGroup group = new HighlightGroup();
      group.addHighlight("TotGtNet", highlight);

      VSUtil.renameHLConditionListRef(group, "Net Revenue", "Net Sales");

      assertEquals("Sum(Net Sales)", ((DataRef) value(list, 0)).getName());
   }
}
