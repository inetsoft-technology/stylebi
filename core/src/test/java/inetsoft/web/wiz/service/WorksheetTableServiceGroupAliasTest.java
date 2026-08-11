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
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.PhysicalBoundTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.wiz.model.WorksheetTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for a group-by {@code alias} in {@link WorksheetTableService#applyAggregateInfo},
 * mirroring the aggregate alias that has always been supported.
 *
 * <p>Why it matters beyond tidier names: an unaliased dateGroupLevel group is named after the
 * expression {@code DateRangeRef} renders — {@code Month(T.due_date)} — which is NOT a SQL alias. It
 * therefore cannot be referenced from a downstream {@code sql:true} expression column under any form
 * (a bare reference reports "column does not exist"; a table-qualified one reports "missing FROM-clause
 * entry"), which made the canonical {@code COALESCE(left_key, right_key)} shared axis over a FULL join
 * unexpressible in pushed-down SQL. Aliasing the group gives it an addressable name.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetTableServiceGroupAliasTest {
   private static final ObjectMapper MAPPER = new ObjectMapper();

   private static WorksheetTableService service() {
      // applyAggregateInfo uses only its parameters + table state + static helpers, never instance
      // dependencies (mirrors WorksheetTableServiceAggregateDescriptionTest).
      return new WorksheetTableService(null, null, null, null, null, null, null, null, null);
   }

   private static WorksheetTable.AggregateInfo aggInfo(String json) throws Exception {
      return MAPPER.readValue(json, WorksheetTable.AggregateInfo.class);
   }

   /** Find a public output column by attribute name OR alias, matching how /ws/structure reads them. */
   private static ColumnRef output(PhysicalBoundTableAssembly table, String nameOrAlias) {
      ColumnSelection pub = table.getColumnSelection(true);

      for(int i = 0; i < pub.getAttributeCount(); i++) {
         DataRef ref = pub.getAttribute(i);

         if(ref instanceof ColumnRef col
            && (nameOrAlias.equals(col.getAttribute()) || nameOrAlias.equals(col.getAlias())))
         {
            return col;
         }
      }

      return null;
   }

   private static PhysicalBoundTableAssembly ordersTable() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "orders");
      ColumnSelection cs = new ColumnSelection();
      AttributeRef dueRef = new AttributeRef(null, "DUE_DATE");
      dueRef.setDataType(XSchema.DATE);
      cs.addAttribute(new ColumnRef(dueRef));
      AttributeRef idRef = new AttributeRef(null, "ORDER_ID");
      idRef.setDataType(XSchema.STRING);
      cs.addAttribute(new ColumnRef(idRef));
      table.setColumnSelection(cs, false);
      return table;
   }

   @Test
   void aliasesADateGroupedColumnSoItIsAddressableInsteadOfARenderedExpression() throws Exception {
      WorksheetTable.AggregateInfo info = aggInfo("""
         {
           "groups": [
             { "fieldName": "DUE_DATE", "dateGroupLevel": "month", "alias": "due_month" }
           ],
           "aggregates": [
             { "fieldName": "ORDER_ID", "formula": "Count", "alias": "order_count" }
           ]
         }
         """);
      PhysicalBoundTableAssembly table = ordersTable();

      service().applyAggregateInfo(table, info);

      assertNotNull(output(table, "due_month"),
                    "the date-grouped column must be reachable by its alias, not only by Month(DUE_DATE)");
      assertNotNull(output(table, "order_count"), "the aggregate alias must still work as before");
   }

   @Test
   void aliasesAPlainGroupColumnToo() throws Exception {
      WorksheetTable.AggregateInfo info = aggInfo("""
         {
           "groups": [ { "fieldName": "ORDER_ID", "alias": "order_key" } ],
           "aggregates": []
         }
         """);
      PhysicalBoundTableAssembly table = ordersTable();

      service().applyAggregateInfo(table, info);

      assertNotNull(output(table, "order_key"));
   }

   @Test
   void leavesTheRenderedNameAloneWhenNoAliasIsGiven() throws Exception {
      // The pre-existing behaviour, pinned: omitting the alias must keep the DateRangeRef name rather
      // than inventing one, so this change cannot move an existing caller's column names.
      WorksheetTable.AggregateInfo info = aggInfo("""
         {
           "groups": [ { "fieldName": "DUE_DATE", "dateGroupLevel": "month" } ],
           "aggregates": [ { "fieldName": "ORDER_ID", "formula": "Count", "alias": "order_count" } ]
         }
         """);
      PhysicalBoundTableAssembly table = ordersTable();

      service().applyAggregateInfo(table, info);

      ColumnSelection pub = table.getColumnSelection(true);
      boolean hasRenderedName = false;

      for(int i = 0; i < pub.getAttributeCount(); i++) {
         if(pub.getAttribute(i) instanceof ColumnRef col) {
            String attr = col.getAttribute();

            if(attr != null && attr.contains("DUE_DATE") && attr.contains("(")) {
               hasRenderedName = true;
               assertNull(col.getAlias(), "an unaliased group must not acquire an alias");
            }
         }
      }

      assertTrue(hasRenderedName, "the date-grouped column should still carry its rendered name");
   }

   @Test
   void anEmptyAliasIsIgnoredRatherThanBlankingTheColumnName() throws Exception {
      WorksheetTable.AggregateInfo info = aggInfo("""
         {
           "groups": [ { "fieldName": "ORDER_ID", "alias": "  " } ],
           "aggregates": []
         }
         """);
      PhysicalBoundTableAssembly table = ordersTable();

      service().applyAggregateInfo(table, info);

      ColumnRef out = output(table, "ORDER_ID");
      assertNotNull(out, "a blank alias must leave the column reachable by its own name");
      assertTrue(out.getAlias() == null || out.getAlias().isBlank());
   }
}
