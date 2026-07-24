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
 * Contract test for {@link WorksheetTableService#applyAggregateInfo}: a {@code description} on a
 * group-by field or an aggregate must be persisted onto the corresponding PUBLIC output column, so
 * that {@code /ws/structure} (and hence data insight's {@code primaryTableFields}) reads it back.
 *
 * <p>The public output column is a CLONE of the private column that the group/aggregate references
 * (see {@code AbstractTableAssembly.setColumnSelection}); {@code ColumnRef.clone()} preserves the
 * description, so setting it on the private target — as the service does — reaches the readback.
 * This suite drives the package-private {@code applyAggregateInfo} directly, mirroring
 * {@code WorksheetTableServiceWindowColumnsTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetTableServiceAggregateDescriptionTest {
   private static final ObjectMapper MAPPER = new ObjectMapper();

   private static WorksheetTableService service() {
      // applyAggregateInfo uses only its parameters + table state + static helpers, never instance
      // dependencies, so null deps are safe (mirrors WorksheetTableServiceWindowColumnsTest).
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

   @Test
   void groupAndAggregateDescriptionsSurviveToPublicOutputColumns() throws Exception {
      WorksheetTable.AggregateInfo info = aggInfo("""
         {
           "groups": [
             { "fieldName": "CUSTOMER_ID", "description": "The customer identifier" }
           ],
           "aggregates": [
             { "fieldName": "ORDER_ID", "formula": "DistinctCount", "alias": "TOTAL_ORDERS",
               "description": "Distinct count of orders per customer" }
           ]
         }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "orders");
      ColumnSelection cs = new ColumnSelection();
      AttributeRef custRef = new AttributeRef(null, "CUSTOMER_ID");
      custRef.setDataType(XSchema.STRING);
      cs.addAttribute(new ColumnRef(custRef));
      AttributeRef orderRef = new AttributeRef(null, "ORDER_ID");
      orderRef.setDataType(XSchema.STRING);
      cs.addAttribute(new ColumnRef(orderRef));
      table.setColumnSelection(cs, false);

      service().applyAggregateInfo(table, info);

      ColumnRef groupOut = output(table, "CUSTOMER_ID");
      assertNotNull(groupOut, "group-by output column CUSTOMER_ID should exist");
      assertEquals("The customer identifier", groupOut.getDescription());

      ColumnRef aggOut = output(table, "TOTAL_ORDERS");
      assertNotNull(aggOut, "aggregate output column TOTAL_ORDERS should exist");
      assertEquals("Distinct count of orders per customer", aggOut.getDescription());
   }

   @Test
   void absentDescriptionLeavesOutputColumnDescriptionEmpty() throws Exception {
      WorksheetTable.AggregateInfo info = aggInfo("""
         {
           "groups": [ { "fieldName": "CUSTOMER_ID" } ],
           "aggregates": [
             { "fieldName": "ORDER_ID", "formula": "Count", "alias": "ORDER_COUNT" }
           ]
         }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "orders");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "CUSTOMER_ID")));
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "ORDER_ID")));
      table.setColumnSelection(cs, false);

      service().applyAggregateInfo(table, info);

      // ColumnRef.getDescription() returns "" (not null) when unset — matches /ws/structure's
      // Tool.isEmptyString normalization, which then reports it as null downstream.
      ColumnRef groupOut = output(table, "CUSTOMER_ID");
      assertNotNull(groupOut);
      assertTrue(groupOut.getDescription() == null || groupOut.getDescription().isEmpty());

      ColumnRef aggOut = output(table, "ORDER_COUNT");
      assertNotNull(aggOut);
      assertTrue(aggOut.getDescription() == null || aggOut.getDescription().isEmpty());
   }

   /**
    * The PR's core new behavior: a group-by dimension that already carries an INHERITED description
    * (e.g. a physical-column annotation carried through join/mirror passthrough) keeps that
    * description — the model's group description does NOT overwrite it. An aggregate MEASURE, by
    * contrast, still takes the model's description, since aggregation changes the column's meaning.
    */
   @Test
   void groupKeepsInheritedDescriptionOverModelDescription() throws Exception {
      WorksheetTable.AggregateInfo info = aggInfo("""
         {
           "groups": [
             { "fieldName": "COMPANY_NAME", "description": "Company name from the joined customers and orders data" }
           ],
           "aggregates": [
             { "fieldName": "ORDER_ID", "formula": "DistinctCount", "alias": "TOTAL_ORDERS",
               "description": "Distinct count of orders per customer" }
           ]
         }
         """);

      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "orders");
      ColumnSelection cs = new ColumnSelection();
      // Seed COMPANY_NAME with an inherited annotation BEFORE aggregation, simulating a passthrough
      // dimension that carried its physical-column annotation through join/mirror.
      AttributeRef companyRef = new AttributeRef(null, "COMPANY_NAME");
      companyRef.setDataType(XSchema.STRING);
      ColumnRef companyCol = new ColumnRef(companyRef);
      companyCol.setDataType(XSchema.STRING);
      companyCol.setDescription("The names of the companies associated with each customer");
      cs.addAttribute(companyCol);
      AttributeRef orderRef = new AttributeRef(null, "ORDER_ID");
      orderRef.setDataType(XSchema.STRING);
      cs.addAttribute(new ColumnRef(orderRef));
      table.setColumnSelection(cs, false);

      service().applyAggregateInfo(table, info);

      // Group dimension keeps the inherited annotation, NOT the model's lineage-restating one.
      ColumnRef groupOut = output(table, "COMPANY_NAME");
      assertNotNull(groupOut, "group-by output column COMPANY_NAME should exist");
      assertEquals("The names of the companies associated with each customer",
                   groupOut.getDescription(),
                   "inherited annotation must win over the model's group description");

      // Aggregate measure still takes the model's description.
      ColumnRef aggOut = output(table, "TOTAL_ORDERS");
      assertNotNull(aggOut);
      assertEquals("Distinct count of orders per customer", aggOut.getDescription());
   }
}
