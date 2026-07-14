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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.asset.ExpressionValue;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.wiz.model.WorksheetTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for {@link WorksheetTableService#buildConditionList}.
 *
 * <p>The bug: a {@code preAggregateCondition} that references a column NOT in the table's
 * {@link ColumnSelection} caused {@code appendConditionItem} to return early (the field resolved to
 * {@code null}) WITHOUT appending a {@code ConditionItem} — but the {@link inetsoft.uql.JunctionOperator}
 * for that item had already been appended by {@code buildConditionList}. The resulting
 * {@link ConditionList} violated its required item/operator alternation (a junction landed at an even
 * index), surfacing downstream as
 * {@code "class inetsoft.uql.JunctionOperator cannot be cast to class inetsoft.uql.ConditionItem"}.
 * With a single condition the same early-return silently DROPPED the filter (no error, wrong results).
 *
 * <p>The fix makes an unresolvable condition field fail loud with a clear, field-named error, so a
 * malformed {@code ConditionList} can never be built and a dropped filter is never silent.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetTableServiceConditionTest {
   private static final ObjectMapper MAPPER = new ObjectMapper();

   private static WorksheetTableService service() {
      // buildConditionList and its callees use only their parameters, never instance state,
      // so null dependencies are safe here.
      return new WorksheetTableService(null, null, null, null, null, null, null, null, null);
   }

   private static ColumnSelection columns(String... names) {
      ColumnSelection cs = new ColumnSelection();

      for(String n : names) {
         cs.addAttribute(new ColumnRef(new AttributeRef(null, n)));
      }

      return cs;
   }

   private static WorksheetTable request(String json) throws Exception {
      return MAPPER.readValue(json, WorksheetTable.class);
   }

   @Test
   void compoundConditionWithUnresolvedFieldFailsLoud() throws Exception {
      // Two leaves joined by "and"; the FIRST references a column not in the selection.
      WorksheetTable req = request("""
         {
           "preAggregateCondition": [
             { "field": "deleted", "operation": "EQUAL_TO", "values": [{ "type": "VALUE", "value": 0 }] },
             { "field": "sales_stage", "operation": "EQUAL_TO", "junction": "and",
               "values": [{ "type": "VALUE", "value": "Closed Won" }] }
           ]
         }
         """);

      // 'deleted' is intentionally NOT selected; 'sales_stage' is.
      ColumnSelection cs = columns("sales_stage");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
         service().buildConditionList(cs, req.getPreAggregateCondition(), new Worksheet(), false));
      assertTrue(ex.getMessage().contains("deleted"),
                 "error should name the unresolved column, got: " + ex.getMessage());
   }

   @Test
   void singleConditionWithUnresolvedFieldFailsLoud() throws Exception {
      // Previously silently dropped (no error, filter ignored) — must now fail loud.
      WorksheetTable req = request("""
         {
           "preAggregateCondition": [
             { "field": "deleted", "operation": "EQUAL_TO", "values": [{ "type": "VALUE", "value": 0 }] }
           ]
         }
         """);

      ColumnSelection cs = columns("sales_stage");

      assertThrows(IllegalArgumentException.class, () ->
         service().buildConditionList(cs, req.getPreAggregateCondition(), new Worksheet(), false));
   }

   @Test
   void compoundConditionWithResolvedFieldsBuildsAlternatingList() throws Exception {
      // Both columns selected → a well-formed alternating list: Condition, Junction, Condition.
      WorksheetTable req = request("""
         {
           "preAggregateCondition": [
             { "field": "deleted", "operation": "EQUAL_TO", "values": [{ "type": "VALUE", "value": 0 }] },
             { "field": "sales_stage", "operation": "EQUAL_TO", "junction": "and",
               "values": [{ "type": "VALUE", "value": "Closed Won" }] }
           ]
         }
         """);

      ColumnSelection cs = columns("deleted", "sales_stage");

      ConditionList list = service().buildConditionList(
         cs, req.getPreAggregateCondition(), new Worksheet(), false);

      assertEquals(3, list.getSize(), "two leaves joined by a junction => 3 list elements");
      assertTrue(list.isConditionItem(0), "index 0 must be a ConditionItem");
      assertTrue(list.isJunctionOperator(1), "index 1 must be a JunctionOperator");
      assertTrue(list.isConditionItem(2), "index 2 must be a ConditionItem");
   }

   // ── FIELD operand (compare a column against another column, e.g. amount > stage_avg). Regression
   // for the silent match-all bug: the FIELD arm used to wrap the bare column name in a JAVASCRIPT
   // ExpressionValue (an undefined JS identifier), so the operand evaluated to nothing and the filter
   // matched ALL rows. It must resolve to the referenced column's DataRef instead. ──

   @Test
   void fieldOperandResolvesToDataRefNotExpressionValue() throws Exception {
      WorksheetTable req = request("""
         {
           "preAggregateCondition": [
             { "field": "amount", "operation": "GREATER_THAN",
               "values": [{ "type": "FIELD", "value": "stage_avg" }] }
           ]
         }
         """);

      ColumnSelection cs = columns("amount", "stage_avg");

      ConditionList list = service().buildConditionList(
         cs, req.getPreAggregateCondition(), new Worksheet(), false);

      Condition cond = list.getConditionItem(0).getCondition();
      assertEquals(1, cond.getValueCount(), "FIELD operand => exactly one operand value");

      Object operand = cond.getValue(0);
      assertFalse(operand instanceof ExpressionValue,
                  "FIELD operand must NOT be a JS ExpressionValue (the bug)");
      assertTrue(operand instanceof DataRef,
                 "FIELD operand must be a DataRef, got: " + operand.getClass().getName());
      assertEquals("stage_avg", ((DataRef) operand).getName(),
                   "resolved DataRef must be the referenced column");
   }

   @Test
   void fieldOperandUnknownColumnFailsLoud() throws Exception {
      // An unresolvable FIELD column must throw (naming it), not silently drop to a no-op operand.
      WorksheetTable req = request("""
         {
           "preAggregateCondition": [
             { "field": "amount", "operation": "GREATER_THAN",
               "values": [{ "type": "FIELD", "value": "nonexistent_col" }] }
           ]
         }
         """);

      ColumnSelection cs = columns("amount", "stage_avg");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
         service().buildConditionList(cs, req.getPreAggregateCondition(), new Worksheet(), false));
      assertTrue(ex.getMessage().contains("nonexistent_col"),
                 "error should name the unresolved FIELD column, got: " + ex.getMessage());
   }

   // ── dateGroupLevel on a condition. Regression for the silent match-all bug: a condition's
   // dateGroupLevel wraps the column in a DateRangeRef but — unlike the working GROUP BY path in
   // applyAggregateInfo — never registered the synthetic column into the table's private
   // ColumnSelection. An unregistered column that fails to SQL-merge falls back to StyleBI's
   // in-memory evaluation, which can't resolve it and defaults to matching every row. The fix
   // threads the private ColumnSelection down so the synthetic column gets registered exactly
   // like applyAggregateInfo's GROUP BY wrap does. ──

   @Test
   void conditionWithIntervalDateGroupLevelRegistersSyntheticColumn() throws Exception {
      WorksheetTable req = request("""
         {
           "preAggregateCondition": [
             { "field": "order_date", "operation": "EQUAL_TO", "dateGroupLevel": "year",
               "values": [{ "type": "VALUE", "value": 2025 }] }
           ]
         }
         """);

      ColumnSelection cs = columns("order_date");
      ColumnSelection privateCs = columns("order_date");

      ConditionList list = service().buildConditionList(
         cs, req.getPreAggregateCondition(), new Worksheet(), false, privateCs);

      String expectedName = DateRangeRef.getName(
         "order_date", inetsoft.web.wiz.service.WizDateLevelUtil.getDateGroupLevel("year"));

      DataRef conditionRef = list.getConditionItem(0).getAttribute();
      assertEquals(expectedName, conditionRef.getName(),
                   "condition must reference the date-grouped synthetic column");

      DataRef registered = privateCs.getAttribute(expectedName);
      assertNotNull(registered,
                     "synthetic date-group column must be registered into the private " +
                     "ColumnSelection so the query engine can resolve it");
      assertTrue(registered instanceof ColumnRef && !((ColumnRef) registered).isVisible(),
                 "synthetic date-group column must be a hidden helper, not an output column");
   }

   @Test
   void conditionWithPartDateGroupLevelRegistersSyntheticColumn() throws Exception {
      WorksheetTable req = request("""
         {
           "preAggregateCondition": [
             { "field": "order_date", "operation": "LESS_THAN", "dateGroupLevel": "month of year",
               "values": [{ "type": "VALUE", "value": 5 }] }
           ]
         }
         """);

      ColumnSelection cs = columns("order_date");
      ColumnSelection privateCs = columns("order_date");

      ConditionList list = service().buildConditionList(
         cs, req.getPreAggregateCondition(), new Worksheet(), false, privateCs);

      String expectedName = DateRangeRef.getName(
         "order_date", inetsoft.web.wiz.service.WizDateLevelUtil.getDateGroupLevel("month of year"));

      DataRef conditionRef = list.getConditionItem(0).getAttribute();
      assertEquals(expectedName, conditionRef.getName(),
                   "condition must reference the date-grouped synthetic column");

      DataRef registered = privateCs.getAttribute(expectedName);
      assertNotNull(registered,
                     "Part-type dateGroupLevel synthetic column must also be registered — this " +
                     "is the case that previously silently matched every row");
   }

   @Test
   void conditionDateGroupLevelReusesRegisteredColumnAcrossLeaves() throws Exception {
      // Two leaves on the same field + level must not create two private-selection entries.
      WorksheetTable req = request("""
         {
           "preAggregateCondition": [
             { "field": "order_date", "operation": "GREATER_THAN", "dateGroupLevel": "year",
               "values": [{ "type": "VALUE", "value": 2020 }] },
             { "field": "order_date", "operation": "LESS_THAN", "dateGroupLevel": "year",
               "junction": "and",
               "values": [{ "type": "VALUE", "value": 2026 }] }
           ]
         }
         """);

      ColumnSelection cs = columns("order_date");
      ColumnSelection privateCs = columns("order_date");
      int before = privateCs.getAttributeCount();

      service().buildConditionList(cs, req.getPreAggregateCondition(), new Worksheet(), false, privateCs);

      assertEquals(before + 1, privateCs.getAttributeCount(),
                   "the same synthetic date-group column must be reused, not duplicated");
   }

   @Test
   void conditionWithoutDateGroupLevelLeavesPrivateSelectionUntouched() throws Exception {
      // No dateGroupLevel => no synthetic column, and a null privateCs (as existing callers pass)
      // must remain safe.
      WorksheetTable req = request("""
         {
           "preAggregateCondition": [
             { "field": "sales_stage", "operation": "EQUAL_TO",
               "values": [{ "type": "VALUE", "value": "Closed Won" }] }
           ]
         }
         """);

      ColumnSelection cs = columns("sales_stage");

      ConditionList list = service().buildConditionList(
         cs, req.getPreAggregateCondition(), new Worksheet(), false, null);

      assertEquals("sales_stage", list.getConditionItem(0).getAttribute().getName());
   }
}
