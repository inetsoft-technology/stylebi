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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
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
 * Contract test for {@link WsServiceHelper#initCompositeColumnSelection}: a join table builds fresh
 * passthrough {@link ColumnRef}s from its base tables (it does not clone them), so it must copy each
 * base column's {@code description} onto the passthrough column — otherwise the annotation/business
 * meaning is lost at every join hop and never reaches {@code /ws/structure} (data insight).
 *
 * <p>Mirrors {@code MirrorTableAssembly.updateColumnSelection}, which already copies the description
 * for mirror passthrough columns.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WsServiceHelperCompositeDescriptionTest {

   private static ColumnRef describedColumn(String name, String type, String description) {
      AttributeRef ref = new AttributeRef(null, name);
      ref.setDataType(type);
      ColumnRef col = new ColumnRef(ref);
      col.setDataType(type);

      if(description != null) {
         col.setDescription(description);
      }

      return col;
   }

   private static PhysicalBoundTableAssembly physical(Worksheet ws, String name, ColumnRef... cols) {
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, name);
      // A valid SourceInfo is required: the join's setColumnSelection runs checkValidity over its
      // base bound tables, which throws "Prefix is null!" if a base has no source.
      SourceInfo si = new SourceInfo(SourceInfo.PHYSICAL_TABLE, "postgres", "public." + name);
      si.setProperty(SourceInfo.SCHEMA, "public");
      si.setProperty(SourceInfo.CATALOG, "sales");
      table.setSourceInfo(si);

      ColumnSelection cs = new ColumnSelection();

      for(ColumnRef c : cols) {
         cs.addAttribute(c);
      }

      table.setColumnSelection(cs, false);
      return table;
   }

   /** Find a column by attribute name OR alias in a selection — matches how /ws/structure reads them. */
   private static ColumnRef find(ColumnSelection cs, String nameOrAlias) {
      for(int i = 0; i < cs.getAttributeCount(); i++) {
         DataRef r = cs.getAttribute(i);

         if(r instanceof ColumnRef c
            && (nameOrAlias.equals(c.getAttribute()) || nameOrAlias.equals(c.getAlias())))
         {
            return c;
         }
      }

      return null;
   }

   @Test
   void joinPassthroughColumnsInheritBaseDescriptions() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly orders = physical(ws, "ORDERS",
         describedColumn("ORDER_ID", XSchema.INTEGER, "Unique identifier for each order"),
         describedColumn("QUANTITY", XSchema.INTEGER, null));            // no description
      PhysicalBoundTableAssembly customers = physical(ws, "CUSTOMERS",
         describedColumn("CUSTOMER_ID", XSchema.INTEGER, "Unique identifier for each customer"),
         describedColumn("COMPANY_NAME", XSchema.STRING, "Company name for the customer"));
      ws.addAssembly(orders);
      ws.addAssembly(customers);

      RelationalJoinTableAssembly join = new RelationalJoinTableAssembly(
         ws, "ORD_CUST_JOIN", new TableAssembly[]{ orders, customers }, new TableAssemblyOperator[0]);

      WsServiceHelper.initCompositeColumnSelection(join);

      ColumnSelection pub = join.getColumnSelection(true);

      ColumnRef orderId = find(pub, "ORDER_ID");
      assertNotNull(orderId, "join should expose ORDER_ID");
      assertEquals("Unique identifier for each order", orderId.getDescription());

      ColumnRef customerId = find(pub, "CUSTOMER_ID");
      assertNotNull(customerId, "join should expose CUSTOMER_ID");
      assertEquals("Unique identifier for each customer", customerId.getDescription());

      ColumnRef companyName = find(pub, "COMPANY_NAME");
      assertNotNull(companyName, "join should expose COMPANY_NAME");
      assertEquals("Company name for the customer", companyName.getDescription());
   }

   @Test
   void columnWithoutBaseDescriptionStaysEmpty() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly orders = physical(ws, "ORDERS",
         describedColumn("QUANTITY", XSchema.INTEGER, null));            // no description
      PhysicalBoundTableAssembly customers = physical(ws, "CUSTOMERS",
         describedColumn("COMPANY_NAME", XSchema.STRING, "Company name for the customer"));
      ws.addAssembly(orders);
      ws.addAssembly(customers);

      RelationalJoinTableAssembly join = new RelationalJoinTableAssembly(
         ws, "ORD_CUST_JOIN", new TableAssembly[]{ orders, customers }, new TableAssemblyOperator[0]);

      WsServiceHelper.initCompositeColumnSelection(join);

      ColumnSelection pub = join.getColumnSelection(true);

      // ColumnRef.getDescription() returns "" (not null) when unset; /ws/structure then normalizes
      // that empty string to null. Either way it must NOT pick up a spurious description.
      ColumnRef quantity = find(pub, "QUANTITY");
      assertNotNull(quantity, "join should expose QUANTITY");
      assertTrue(quantity.getDescription() == null || quantity.getDescription().isEmpty(),
         "a column with no base description must not gain one");

      ColumnRef companyName = find(pub, "COMPANY_NAME");
      assertNotNull(companyName);
      assertEquals("Company name for the customer", companyName.getDescription());
   }
}
