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
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
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
}
