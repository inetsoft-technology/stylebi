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

import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.wiz.model.WorksheetColumnInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link WsServiceHelper#extractPrimaryTableFields}, the field-description method
 * now shared between {@link GenerateWsService} and {@link WorksheetTableService}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WsServiceHelperPrimaryTableFieldsTest {
   private static PhysicalBoundTableAssembly physicalTable(Worksheet ws, String assemblyName) {
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, assemblyName);
      SourceInfo si = new SourceInfo(SourceInfo.PHYSICAL_TABLE, "postgres", "public." + assemblyName);
      si.setProperty(SourceInfo.SCHEMA, "public");
      si.setProperty(SourceInfo.CATALOG, "sales");
      table.setSourceInfo(si);

      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(typedColumn("id", XSchema.INTEGER));
      cs.addAttribute(typedColumn("amount", XSchema.DOUBLE));
      table.setColumnSelection(cs, false);
      return table;
   }

   private static ColumnRef typedColumn(String name, String type) {
      AttributeRef ref = new AttributeRef(null, name);

      if(type != null) {
         ref.setDataType(type);
      }

      ColumnRef col = new ColumnRef(ref);

      if(type != null) {
         col.setDataType(type);
      }

      return col;
   }

   @Test
   void physicalTableUsesOverrideAsTableName() {
      Worksheet ws = new Worksheet();
      // Assembly name differs from the DB table name (e.g. LLM picked "orders_table").
      PhysicalBoundTableAssembly table = physicalTable(ws, "orders_table");
      ws.addAssembly(table);

      List<WorksheetColumnInfo> fields =
         WsServiceHelper.extractPrimaryTableFields(ws, table, "ORDERS");

      assertEquals(2, fields.size());
      assertEquals("id", fields.get(0).getName());
      assertEquals("ORDERS", fields.get(0).getTable(), "override should be recorded, not assembly name");
      assertEquals("public", fields.get(0).getSchema());
      assertEquals("sales", fields.get(0).getCatalog());
      assertEquals(XSchema.INTEGER, fields.get(0).getType());
      assertEquals("ORDERS", fields.get(1).getTable());
   }

   @Test
   void physicalTableFallsBackToAssemblyNameWhenOverrideNull() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = physicalTable(ws, "orders_table");
      ws.addAssembly(table);

      List<WorksheetColumnInfo> fields =
         WsServiceHelper.extractPrimaryTableFields(ws, table, null);

      assertEquals(2, fields.size());
      assertEquals("orders_table", fields.get(0).getTable(),
         "null override should fall back to the assembly name");
   }

   @Test
   void joinTableResolvesSubTableSourceFromEntity() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly orders = physicalTable(ws, "ORDERS");
      PhysicalBoundTableAssembly customers = physicalTable(ws, "CUSTOMERS");
      ws.addAssembly(orders);
      ws.addAssembly(customers);

      RelationalJoinTableAssembly join = new RelationalJoinTableAssembly(
         ws, "join1", new TableAssembly[]{ orders, customers }, new TableAssemblyOperator[0]);

      // Composite columns carry the sub-table assembly name as their entity.
      ColumnSelection joinCs = new ColumnSelection();
      AttributeRef idRef = new AttributeRef("ORDERS", "id");
      idRef.setDataType(XSchema.INTEGER);
      ColumnRef idCol = new ColumnRef(idRef);
      idCol.setDataType(XSchema.INTEGER);
      joinCs.addAttribute(idCol);
      join.setColumnSelection(joinCs, false);
      ws.addAssembly(join);

      List<WorksheetColumnInfo> fields =
         WsServiceHelper.extractPrimaryTableFields(ws, join, null);

      assertEquals(1, fields.size());
      assertEquals("id", fields.get(0).getName());
      assertEquals("ORDERS", fields.get(0).getTable(), "table should be the sub-table assembly name");
      assertEquals("public", fields.get(0).getSchema());
      assertEquals("sales", fields.get(0).getCatalog());
   }

   @Test
   void compositeColumnsWithoutEntityAreSkipped() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly orders = physicalTable(ws, "ORDERS");
      PhysicalBoundTableAssembly customers = physicalTable(ws, "CUSTOMERS");
      ws.addAssembly(orders);
      ws.addAssembly(customers);

      RelationalJoinTableAssembly join = new RelationalJoinTableAssembly(
         ws, "join1", new TableAssembly[]{ orders, customers }, new TableAssemblyOperator[0]);

      // Mirror-style columns lack an entity name; the helper skips them (empty result).
      ColumnSelection joinCs = new ColumnSelection();
      joinCs.addAttribute(typedColumn("id", XSchema.INTEGER));
      join.setColumnSelection(joinCs, false);
      ws.addAssembly(join);

      List<WorksheetColumnInfo> fields =
         WsServiceHelper.extractPrimaryTableFields(ws, join, null);

      assertTrue(fields.isEmpty(), "columns without an entity name should be skipped");
   }

   @Test
   void nullPrimaryTableReturnsEmptyList() {
      assertTrue(WsServiceHelper.extractPrimaryTableFields(new Worksheet(), null, null).isEmpty());
   }
}
