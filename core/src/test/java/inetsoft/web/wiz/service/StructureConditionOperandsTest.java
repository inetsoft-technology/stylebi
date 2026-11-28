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

package inetsoft.web.wiz.service;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.ExpressionValue;
import inetsoft.uql.asset.SubQueryValue;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.web.wiz.model.WorksheetStructure;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Condition leaves keep non-literal operands and the column the table exposes; worksheet
 * aggregates report the column they read (sourceField).
 */
@Tag("core")
class StructureConditionOperandsTest {
   private static ConditionList list(DataRef ref, int operation, Object... values) {
      Condition condition = new Condition();
      condition.setOperation(operation);

      for(Object value : values) {
         condition.addValue(value);
      }

      ConditionList list = new ConditionList();
      list.append(new ConditionItem(ref, condition, 0));
      return list;
   }

   private static WorksheetStructure.ConditionLeaf collect(ConditionList list,
                                                           ColumnSelection selection)
   {
      List<WorksheetStructure.ConditionLeaf> out = new ArrayList<>();
      MetadataApiService.collectStructureConditions(list, "post", out, null, selection);
      assertEquals(1, out.size());
      return out.get(0);
   }

   private static ColumnRef aliased(String attribute, String alias) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, attribute));

      if(alias != null) {
         ref.setAlias(alias);
      }

      return ref;
   }

   @Test
   void subqueryOperandIsRenderedAsSelect() {
      SubQueryValue sub = new SubQueryValue();
      sub.setQuery("CATEGORY_TOTAL_COUNT");
      sub.setAttribute(new AttributeRef(null, "TOTAL_CATEGORY_COUNT"));

      WorksheetStructure.ConditionLeaf leaf = collect(
         list(new AttributeRef(null, "N"), XCondition.EQUAL_TO, sub), null);

      assertTrue(leaf.getValues().isEmpty());
      assertEquals(1, leaf.getOperands().size());
      assertEquals("subquery", leaf.getOperands().get(0).getKind());
      assertEquals("(SELECT TOTAL_CATEGORY_COUNT FROM CATEGORY_TOTAL_COUNT)",
                   leaf.getOperands().get(0).getText());
      assertNull(leaf.getOperands().get(0).getLanguage());
   }

   @Test
   void correlatedSubqueryAddsWhere() {
      SubQueryValue sub = new SubQueryValue();
      sub.setQuery("Q");
      sub.setAttribute(aliased("A", "Total"));
      sub.setSubAttribute(new AttributeRef(null, "K"));
      sub.setMainAttribute(aliased("MK", "MainKey"));

      WorksheetStructure.ConditionLeaf leaf = collect(
         list(new AttributeRef(null, "N"), XCondition.EQUAL_TO, sub), null);

      assertEquals("(SELECT Total FROM Q WHERE Q.K = MainKey)",
                   leaf.getOperands().get(0).getText());
   }

   @Test
   void fieldOperandUsesExposedName() {
      WorksheetStructure.ConditionLeaf leaf = collect(
         list(new AttributeRef(null, "N"), XCondition.GREATER_THAN,
              aliased("Other", "OtherAlias")), null);

      assertEquals("field", leaf.getOperands().get(0).getKind());
      assertEquals("OtherAlias", leaf.getOperands().get(0).getText());
   }

   @Test
   void variableOperand() {
      WorksheetStructure.ConditionLeaf leaf = collect(
         list(new AttributeRef(null, "N"), XCondition.EQUAL_TO, new UserVariable("minSales")),
         null);

      assertEquals("variable", leaf.getOperands().get(0).getKind());
      assertEquals("$(minSales)", leaf.getOperands().get(0).getText());
   }

   @Test
   void expressionOperandCarriesLanguage() {
      ExpressionValue expression = new ExpressionValue();
      expression.setExpression("field[\"A\"] * 2");
      expression.setType(ExpressionValue.JAVASCRIPT);

      WorksheetStructure.ConditionLeaf leaf = collect(
         list(new AttributeRef(null, "N"), XCondition.EQUAL_TO, expression), null);

      assertEquals("expression", leaf.getOperands().get(0).getKind());
      assertEquals("field[\"A\"] * 2", leaf.getOperands().get(0).getText());
      assertEquals(ExpressionValue.JAVASCRIPT, leaf.getOperands().get(0).getLanguage());
   }

   @Test
   void literalsStayInValuesAndOperandsFollow() {
      WorksheetStructure.ConditionLeaf leaf = collect(
         list(new AttributeRef(null, "N"), XCondition.BETWEEN, "1", new UserVariable("hi")),
         null);

      assertEquals(List.of("1"), leaf.getValues());
      assertEquals(1, leaf.getOperands().size());
      assertEquals("$(hi)", leaf.getOperands().get(0).getText());
   }

   @Test
   void literalOnlyConditionHasNoOperandsOrColumn() {
      WorksheetStructure.ConditionLeaf leaf = collect(
         list(new AttributeRef(null, "N"), XCondition.EQUAL_TO, "x"), null);

      assertNull(leaf.getOperands());
      assertNull(leaf.getColumn());
   }

   @Test
   void columnComesFromAliasOnTheRef() {
      WorksheetStructure.ConditionLeaf leaf = collect(
         list(aliased("CATEGORY_ID", "DISTINCT_CATEGORY_COUNT"), XCondition.EQUAL_TO, "1"),
         null);

      assertEquals("CATEGORY_ID", leaf.getField());
      assertEquals("DISTINCT_CATEGORY_COUNT", leaf.getColumn());
   }

   @Test
   void columnIsResolvedThroughTheTableSelection() {
      ColumnSelection selection = new ColumnSelection();
      selection.addAttribute(aliased("CATEGORY_ID", "Category"));

      WorksheetStructure.ConditionLeaf leaf = collect(
         list(new AttributeRef(null, "CATEGORY_ID"), XCondition.EQUAL_TO, "1"), selection);

      assertEquals("Category", leaf.getColumn());
   }

   @Test
   void columnIsAbsentWhenEqualToFieldOrUnresolvable() {
      assertNull(collect(list(aliased("A", "A"), XCondition.EQUAL_TO, "1"), null).getColumn());

      ColumnSelection selection = new ColumnSelection();
      selection.addAttribute(aliased("OTHER", "Alias"));
      assertNull(collect(list(new AttributeRef(null, "A"), XCondition.EQUAL_TO, "1"),
                         selection).getColumn());
      assertNull(collect(list(new AttributeRef(null, "A"), XCondition.EQUAL_TO, "1"),
                         null).getColumn());
   }

   @Test
   void aggregateSourceFieldPresentOnlyWhenDifferent() {
      AggregateInfo info = new AggregateInfo();
      info.addAggregate(new AggregateRef(
         aliased("CATEGORY_ID", "DISTINCT_CATEGORY_COUNT"), AggregateFormula.COUNT_DISTINCT));
      info.addAggregate(new AggregateRef(aliased("SALES", null), AggregateFormula.SUM));

      List<WorksheetStructure.AggMeasure> aggregates =
         MetadataApiService.extractStructureAggregate(info).getAggregates();

      assertEquals("CATEGORY_ID", aggregates.get(0).getSourceField());
      assertNull(aggregates.get(1).getSourceField());
   }

   @Test
   void formatClauseRendersColumnAndOperands() {
      WorksheetStructure.ConditionLeaf leaf = new WorksheetStructure.ConditionLeaf();
      leaf.setField("CATEGORY_ID");
      leaf.setColumn("DISTINCT_CATEGORY_COUNT");
      leaf.setOperation("EQUAL_TO");
      leaf.setValues(List.of("5"));
      leaf.setOperands(List.of(
         new WorksheetStructure.Operand("subquery", "(SELECT T FROM Q)", null)));

      assertEquals("DISTINCT_CATEGORY_COUNT EQUAL_TO 5, (SELECT T FROM Q)",
                   AssemblyStateReader.formatClause(leaf));

      leaf.setColumn(null);
      leaf.setValues(new ArrayList<>());
      assertEquals("CATEGORY_ID EQUAL_TO (SELECT T FROM Q)",
                   AssemblyStateReader.formatClause(leaf));
   }
}
