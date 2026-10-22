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
package inetsoft.web.wiz.worksheet;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.wiz.pairing.TestWorksheets;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79137: set_group_aggregate applies a column-attached named group only to a column that
 * resolves to the group's attached source, the way the Group and Aggregate dialog decides it.
 * GroupRef.update() compares the attribute name alone, so an embedded or expression column that
 * merely shares the name used to be accepted.
 */
@Tag("core")
@WizAgentTestSupport
class NamedGroupSource79137Test {
   private static final String DS = "Examples/Orders";

   // ------------------------------------------------------------------------ refused

   @Test
   void embeddedTableWithSameNamedColumnIsRefused() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = embedded(ws, "Other");
      NamedGroupAssembly group = physicalGroup(ws, "SalesRegion", "SA.CUSTOMERS", "STATE");

      assertFalse(applies(t, "STATE", group));
   }

   @Test
   void expressionColumnWithSameNameIsRefused() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "Expr", "CODE");
      ExpressionRef expr = new ExpressionRef(null, null, "STATE");
      expr.setExpression("field['CODE']");
      expr.setDataType(XSchema.STRING);
      ColumnSelection cs = t.getColumnSelection(false);
      cs.addAttribute(new ColumnRef(expr));
      t.setColumnSelection(cs, false);
      ws.addAssembly(t);
      NamedGroupAssembly group = physicalGroup(ws, "SalesRegion", "SA.CUSTOMERS", "STATE");

      assertFalse(applies(t, "STATE", group));
   }

   @Test
   void physicalTableOfAnotherSourceIsRefused() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly t = physical(ws, "Suppliers", "SA.SUPPLIERS", "STATE");
      NamedGroupAssembly group = physicalGroup(ws, "SalesRegion", "SA.CUSTOMERS", "STATE");

      assertFalse(applies(t, "STATE", group));
   }

   /**
    * An add_sql_query table is a SQLBoundTableAssembly (a BoundTableAssembly) whose SourceInfo is
    * the datasource itself, never the physical table, so a group attached to a physical table is
    * refused on it, as the dialog does.
    */
   @Test
   void sqlQueryTableIsRefused() {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t = new SQLBoundTableAssembly(ws, "Q");
      ((SQLBoundTableAssemblyInfo) t.getInfo()).setSourceInfo(
         new SourceInfo(SourceInfo.PHYSICAL_TABLE, DS, DS));
      setColumns(t, new ColumnRef(new AttributeRef(null, "STATE")));
      ws.addAssembly(t);
      NamedGroupAssembly group = physicalGroup(ws, "SalesRegion", "SA.CUSTOMERS", "STATE");

      assertFalse(applies(t, "STATE", group));
   }

   @Test
   void mirrorOfTheAssetGroupsOwnTableIsRefused() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = embedded(ws, "T");
      MirrorTableAssembly m = mirror(ws, "M", t);
      NamedGroupAssembly group = assetGroup(ws, "G", "T", "STATE");

      assertTrue(applies(t, "STATE", group));
      // newly refused: the dialog cannot create a worksheet-table-attached group, and the
      // attachment is not resolved through mirrors
      assertFalse(applies(m, "T.STATE", group));
   }

   // ------------------------------------------------------------------------ still applies

   @Test
   void boundTableOfTheGroupsSourceApplies() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly t = physical(ws, "CUSTOMERS", "SA.CUSTOMERS", "STATE");
      NamedGroupAssembly group = physicalGroup(ws, "SalesRegion", "SA.CUSTOMERS", "STATE");

      assertTrue(applies(t, "STATE", group));
   }

   @Test
   void mirrorOfBoundTableApplies() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly t = physical(ws, "CUSTOMERS", "SA.CUSTOMERS", "STATE");
      MirrorTableAssembly m = mirror(ws, "CustMirror", t);
      MirrorTableAssembly mm = mirror(ws, "CustMirror2", m);
      NamedGroupAssembly group = physicalGroup(ws, "SalesRegion", "SA.CUSTOMERS", "STATE");

      assertTrue(applies(m, "CUSTOMERS.STATE", group));
      assertTrue(applies(mm, "CustMirror.CUSTOMERS.STATE", group));
   }

   @Test
   void renamedColumnOfBoundTableApplies() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly t = physical(ws, "CUSTOMERS", "SA.CUSTOMERS", "STATE");
      ((ColumnRef) t.getColumnSelection(false).getAttribute(0)).setAlias("Region");
      NamedGroupAssembly group = physicalGroup(ws, "SalesRegion", "SA.CUSTOMERS", "STATE");

      assertTrue(applies(t, "Region", group));
   }

   @Test
   void joinDerivedTableApplies() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly left = physical(ws, "CUSTOMERS", "SA.CUSTOMERS", "ID", "STATE");
      PhysicalBoundTableAssembly right = physical(ws, "ORDERS", "SA.ORDERS", "CID", "AMOUNT");
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setLeftTable("CUSTOMERS");
      op.setRightTable("ORDERS");
      op.setLeftAttribute(new AttributeRef(null, "ID"));
      op.setRightAttribute(new AttributeRef(null, "CID"));
      op.setOperation(TableAssemblyOperator.INNER_JOIN);
      TableAssemblyOperator top = new TableAssemblyOperator();
      top.addOperator(op);
      RelationalJoinTableAssembly join = new RelationalJoinTableAssembly(
         ws, "J", new TableAssembly[] { left, right }, new TableAssemblyOperator[] { top });
      ws.addAssembly(join);
      // the join's selection is filled by the query layer; build it the same way it does,
      // with each column qualified by its subtable
      ColumnSelection joined = new ColumnSelection();

      for(TableAssembly sub : new TableAssembly[] { left, right }) {
         ColumnSelection subCols = sub.getColumnSelection(true);

         for(int i = 0; i < subCols.getAttributeCount(); i++) {
            joined.addAttribute(new ColumnRef(
               AssetUtil.getOuterAttribute(sub.getName(), subCols.getAttribute(i))));
         }
      }

      join.setColumnSelection(joined, false);
      NamedGroupAssembly group = physicalGroup(ws, "SalesRegion", "SA.CUSTOMERS", "STATE");

      assertTrue(applies(join, "CUSTOMERS.STATE", group));
      // a column of the other source of the same join is still refused
      assertFalse(applies(join, "ORDERS.AMOUNT",
                          physicalGroup(ws, "AmountGroup", "SA.CUSTOMERS", "AMOUNT")));
   }

   @Test
   void logicalModelEntityTableAndItsMirrorApplyModelScopedGroup() {
      Worksheet ws = new Worksheet();
      BoundTableAssembly t = new BoundTableAssembly(ws, "Customer");
      t.setSourceInfo(new SourceInfo(SourceInfo.MODEL, DS, "Order Model"));
      ColumnRef state = new ColumnRef(new AttributeRef("Customer", "state"));
      state.setDataType(XSchema.STRING);
      setColumns(t, state);
      ws.addAssembly(t);
      MirrorTableAssembly m = mirror(ws, "CustomerMirror", t);

      DefaultNamedGroupAssembly group = new DefaultNamedGroupAssembly(ws, "G");
      group.setAttachedType(AttachedAssembly.COLUMN_ATTACHED);
      group.setAttachedSource(new SourceInfo(SourceInfo.MODEL, DS, "Order Model"));
      group.setAttachedAttribute(new ColumnRef(new AttributeRef("Customer", "state")));
      ws.addAssembly(group);

      assertTrue(applies(t, "Customer.state", group));
      assertTrue(applies(m, "Customer.Customer.state", group));
   }

   @Test
   void assetGroupOnItsOwnTableApplies() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = embedded(ws, "T");

      assertTrue(applies(t, "STATE", assetGroup(ws, "G", "T", "STATE")));
      assertFalse(applies(t, "AMT", assetGroup(ws, "G2", "T", "STATE")));
   }

   @Test
   void dataTypeAttachedGroupAppliesToAnyTable() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = embedded(ws, "T");
      DefaultNamedGroupAssembly group = new DefaultNamedGroupAssembly(ws, "G");
      group.setAttachedType(AttachedAssembly.DATA_TYPE_ATTACHED);
      group.setAttachedDataType(XSchema.STRING);
      ws.addAssembly(group);

      assertTrue(applies(t, "STATE", group));
   }

   // ------------------------------------------------------------------------ helpers

   private static boolean applies(TableAssembly t, String column, NamedGroupAssembly group) {
      ColumnSelection cs = t.getColumnSelection(false);
      DataRef ref = cs.getAttribute(column);
      assertNotNull(ref, "column " + column + " in " + cs);
      return WorksheetMutationSupport.namedGroupAppliesToColumn(t, (ColumnRef) ref, group);
   }

   private static EmbeddedTableAssembly embedded(Worksheet ws, String name) {
      EmbeddedTableAssembly t = new EmbeddedTableAssembly(ws, name);
      t.setEmbeddedData(new XEmbeddedTable(new String[] { XSchema.STRING, XSchema.INTEGER },
         new Object[][] { { "STATE", "AMT" }, { "CA", 10 }, { "NJ", 20 } }));
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "STATE")));
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "AMT")));
      t.setColumnSelection(cs, false);
      ws.addAssembly(t);
      return t;
   }

   private static PhysicalBoundTableAssembly physical(Worksheet ws, String name, String source,
                                                      String... columns)
   {
      PhysicalBoundTableAssembly t = new PhysicalBoundTableAssembly(ws, name);
      t.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, DS, source));
      ColumnRef[] refs = new ColumnRef[columns.length];

      for(int i = 0; i < columns.length; i++) {
         refs[i] = new ColumnRef(new AttributeRef(null, columns[i]));
         refs[i].setDataType(XSchema.STRING);
      }

      setColumns(t, refs);
      ws.addAssembly(t);
      return t;
   }

   private static void setColumns(BoundTableAssembly t, ColumnRef... refs) {
      ColumnSelection cs = new ColumnSelection();

      for(ColumnRef ref : refs) {
         cs.addAttribute(ref);
      }

      t.setColumnSelection(cs);
   }

   private static MirrorTableAssembly mirror(Worksheet ws, String name, TableAssembly base) {
      MirrorTableAssembly m = new MirrorTableAssembly(ws, name, base);
      ws.addAssembly(m);
      m.update();
      return m;
   }

   private static NamedGroupAssembly physicalGroup(Worksheet ws, String name, String source,
                                                   String attribute)
   {
      DefaultNamedGroupAssembly group = new DefaultNamedGroupAssembly(ws, name);
      group.setAttachedType(AttachedAssembly.COLUMN_ATTACHED);
      group.setAttachedSource(new SourceInfo(SourceInfo.PHYSICAL_TABLE, DS, source));
      group.setAttachedAttribute(new ColumnRef(new AttributeRef(null, attribute)));
      ws.addAssembly(group);
      return group;
   }

   private static NamedGroupAssembly assetGroup(Worksheet ws, String name, String table,
                                                String attribute)
   {
      DefaultNamedGroupAssembly group = new DefaultNamedGroupAssembly(ws, name);
      group.setAttachedType(AttachedAssembly.COLUMN_ATTACHED);
      group.setAttachedSource(new SourceInfo(SourceInfo.ASSET, null, table));
      group.setAttachedAttribute(new ColumnRef(new AttributeRef(null, attribute)));
      ws.addAssembly(group);
      return group;
   }
}
