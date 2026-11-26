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
package inetsoft.mv;

import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * How a worksheet table on a worksheet MV is rewritten at runtime
 * ({@link WSMVTransformer#transform}): the runtime conditions of the composed tables are pushed
 * down to the bound tables that have the MV ({@code WSMVSelectionDownTransformer}), and the
 * aggregate of a mirror is pushed down to the bound table ({@code WSMVAggregateDownTransformer}),
 * so the MV query can filter and group. Each case checks both that a condition or aggregate
 * that can move does move, and that one that must not move stays where it is.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class WSMVTransformerTest {
   // ---------------------------------------------------------------- selection down

   @Test
   void mirrorConditionMovesDownToTheBoundTable() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly m1 = mirror(ws, "M1", bound(ws, "T1", true));
      m1.setPreRuntimeConditionList(list(item(column(m1, "Region"), "East")));

      ComposedTableAssembly result = (ComposedTableAssembly) WSMVTransformer.transform(m1);
      TableAssembly t1 = child(result, "T1");

      assertEquals(0, size(result.getPreRuntimeConditionList()));
      assertEquals(1, size(t1.getPreRuntimeConditionList()));
      assertEquals("Region", t1.getPreRuntimeConditionList().getConditionList()
         .getAttribute(0).getAttribute());
      assertEquals("true", t1.getProperty("selection.down"));
   }

   @Test
   void tableWithoutWorksheetMVKeepsItsCondition() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly m1 = mirror(ws, "M1", bound(ws, "T1", false));
      m1.setPreRuntimeConditionList(list(item(column(m1, "Region"), "East")));

      ComposedTableAssembly result = (ComposedTableAssembly) WSMVTransformer.transform(m1);

      assertEquals(1, size(result.getPreRuntimeConditionList()));
      assertEquals(0, size(child(result, "T1").getPreRuntimeConditionList()));
   }

   @Test
   void innerJoinConditionGoesToItsTable() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly j1 = join(ws, bound(ws, "T1", true), bound(ws, "T2", true),
                              TableAssemblyOperator.INNER_JOIN);
      j1.setPreRuntimeConditionList(list(item(column(j1, "T1", "Region"), "East")));

      ComposedTableAssembly result = (ComposedTableAssembly) WSMVTransformer.transform(j1);

      assertEquals(0, size(result.getPreRuntimeConditionList()));
      assertEquals(1, size(child(result, "T1").getPreRuntimeConditionList()));
      assertEquals(0, size(child(result, "T2").getPreRuntimeConditionList()));
   }

   @Test
   void innerJoinAndConditionsAreSplitByTable() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly j1 = join(ws, bound(ws, "T1", true), bound(ws, "T2", true),
                              TableAssemblyOperator.INNER_JOIN);
      j1.setPreRuntimeConditionList(list(item(column(j1, "T1", "Region"), "East"),
                                         JunctionOperator.AND,
                                         item(column(j1, "T2", "State"), "NJ")));

      ComposedTableAssembly result = (ComposedTableAssembly) WSMVTransformer.transform(j1);

      assertEquals(0, size(result.getPreRuntimeConditionList()));
      TableAssembly t1 = child(result, "T1");
      TableAssembly t2 = child(result, "T2");
      assertEquals(1, size(t1.getPreRuntimeConditionList()));
      assertEquals("Region", t1.getPreRuntimeConditionList().getConditionList()
         .getAttribute(0).getAttribute());
      assertEquals(1, size(t2.getPreRuntimeConditionList()));
      assertEquals("State", t2.getPreRuntimeConditionList().getConditionList()
         .getAttribute(0).getAttribute());
   }

   @Test
   void orAcrossJoinedTablesStaysOnTheJoin() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly j1 = join(ws, bound(ws, "T1", true), bound(ws, "T2", true),
                              TableAssemblyOperator.INNER_JOIN);
      j1.setPreRuntimeConditionList(list(item(column(j1, "T1", "Region"), "East"),
                                         JunctionOperator.OR,
                                         item(column(j1, "T2", "State"), "NJ")));

      ComposedTableAssembly result = (ComposedTableAssembly) WSMVTransformer.transform(j1);

      assertEquals(3, size(result.getPreRuntimeConditionList()));
      assertEquals(0, size(child(result, "T1").getPreRuntimeConditionList()));
      assertEquals(0, size(child(result, "T2").getPreRuntimeConditionList()));
   }

   @Test
   void outerJoinConditionStaysOnTheJoin() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly j1 = join(ws, bound(ws, "T1", true), bound(ws, "T2", true),
                              TableAssemblyOperator.LEFT_JOIN);
      j1.setPreRuntimeConditionList(list(item(column(j1, "T1", "Region"), "East")));

      ComposedTableAssembly result = (ComposedTableAssembly) WSMVTransformer.transform(j1);

      assertEquals(1, size(result.getPreRuntimeConditionList()));
      assertEquals(0, size(child(result, "T1").getPreRuntimeConditionList()));
   }

   @Test
   void subQueryValueStaysOnTheParent() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly m1 = mirror(ws, "M1", bound(ws, "T1", true));
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.ONE_OF);
      cond.addValue(new SubQueryValue());
      m1.setPreRuntimeConditionList(list(new ConditionItem(column(m1, "Region"), cond, 0)));

      ComposedTableAssembly result = (ComposedTableAssembly) WSMVTransformer.transform(m1);

      assertEquals(1, size(result.getPreRuntimeConditionList()));
      assertEquals(0, size(child(result, "T1").getPreRuntimeConditionList()));
   }

   @Test
   void columnValueStaysOnTheParent() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly m1 = mirror(ws, "M1", bound(ws, "T1", true));
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue(column(m1, "State"));
      m1.setPreRuntimeConditionList(list(new ConditionItem(column(m1, "Region"), cond, 0)));

      ComposedTableAssembly result = (ComposedTableAssembly) WSMVTransformer.transform(m1);

      assertEquals(1, size(result.getPreRuntimeConditionList()));
      assertEquals(0, size(child(result, "T1").getPreRuntimeConditionList()));
   }

   @Test
   void conditionOnAnAggregateOfTheChildStaysOnTheParent() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly m1 = mirror(ws, "M1", bound(ws, "T1", true));
      m1.setAggregateInfo(aggregate(m1, "Region", "Amount"));
      TableAssembly m2 = mirror(ws, "M2", m1);
      m2.setPreRuntimeConditionList(list(item(column(m2, "Amount"), 10)));

      ComposedTableAssembly result = (ComposedTableAssembly) WSMVTransformer.transform(m2);

      assertEquals(1, size(result.getPreRuntimeConditionList()));
      assertEquals(0, size(child(result, "M1").getPreRuntimeConditionList()));
   }

   @Test
   void conditionOnAGroupOfTheChildMovesDown() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly m1 = mirror(ws, "M1", bound(ws, "T1", true));
      m1.setAggregateInfo(aggregate(m1, "Region", "Amount"));
      TableAssembly m2 = mirror(ws, "M2", m1);
      m2.setPreRuntimeConditionList(list(item(column(m2, "Region"), "East")));

      ComposedTableAssembly result = (ComposedTableAssembly) WSMVTransformer.transform(m2);

      assertEquals(0, size(result.getPreRuntimeConditionList()));
   }

   @Test
   void rotatedTableKeepsItsCondition() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t1 = bound(ws, "T1", true);
      RotatedTableAssembly r1 = new RotatedTableAssembly(ws, "R1", t1);
      ws.addAssembly(r1);
      r1.setPreRuntimeConditionList(list(item(new ColumnRef(new AttributeRef("R1", "Region")),
                                              "East")));

      ComposedTableAssembly result = (ComposedTableAssembly) WSMVTransformer.transform(r1);

      assertEquals(1, size(result.getPreRuntimeConditionList()));
      assertEquals(0, size(child(result, "T1").getPreRuntimeConditionList()));
   }

   @Test
   void tablesWithACommonAncestorAreNotTransformed() throws Exception {
      // a condition pushed down to the shared bound table would filter both join sides
      Worksheet ws = new Worksheet();
      TableAssembly t1 = bound(ws, "T1", true);
      TableAssembly m1 = mirror(ws, "M1", t1);
      TableAssembly m2 = mirror(ws, "M2", t1);
      TableAssembly j1 = join(ws, m1, m2, TableAssemblyOperator.INNER_JOIN);
      j1.setPreRuntimeConditionList(list(item(column(j1, "M1", "Region"), "East")));

      ComposedTableAssembly result = (ComposedTableAssembly) WSMVTransformer.transform(j1);

      assertEquals(1, size(result.getPreRuntimeConditionList()));
      assertEquals(0, size(child(result, "M1").getPreRuntimeConditionList()));
   }

   // ---------------------------------------------------------------- aggregate down

   @Test
   void mirrorAggregateMovesDownToTheBoundTable() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly m1 = mirror(ws, "M1", bound(ws, "T1", true));
      m1.setAggregateInfo(aggregate(m1, "Region", "Amount"));

      ComposedTableAssembly result = (ComposedTableAssembly) WSMVTransformer.transform(m1);
      TableAssembly t1 = child(result, "T1");

      assertTrue(result.getAggregateInfo().isEmpty());
      assertEquals(1, t1.getAggregateInfo().getGroupCount());
      assertEquals(1, t1.getAggregateInfo().getAggregateCount());
      assertEquals("Region", t1.getAggregateInfo().getGroup(0).getAttribute());
      assertEquals("true", t1.getProperty("aggregate.down"));
      assertEquals("true", result.getProperty("aggregate.down"));
   }

   @Test
   void postConditionMovesDownWithTheAggregate() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly m1 = mirror(ws, "M1", bound(ws, "T1", true));
      AggregateInfo ainfo = aggregate(m1, "Region", "Amount");
      m1.setAggregateInfo(ainfo);
      m1.setPostConditionList(list(new ConditionItem(ainfo.getAggregate(0), greaterThan(10), 0)));

      ComposedTableAssembly result = (ComposedTableAssembly) WSMVTransformer.transform(m1);
      TableAssembly t1 = child(result, "T1");

      assertEquals(0, size(result.getPostConditionList()));
      assertEquals(1, size(t1.getPostRuntimeConditionList()));
   }

   @Test
   void aggregateMovesDownThroughAMirrorWithoutAggregate() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly m1 = mirror(ws, "M1", bound(ws, "T1", true));
      TableAssembly m2 = mirror(ws, "M2", m1);
      m2.setAggregateInfo(aggregate(m2, "Region", "Amount"));

      ComposedTableAssembly result = (ComposedTableAssembly) WSMVTransformer.transform(m2);
      TableAssembly t1 = child((ComposedTableAssembly) child(result, "M1"), "T1");

      assertTrue(result.getAggregateInfo().isEmpty());
      assertEquals(1, t1.getAggregateInfo().getAggregateCount());
   }

   @Test
   void crosstabAggregateStaysOnTheMirror() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly m1 = mirror(ws, "M1", bound(ws, "T1", true));
      AggregateInfo ainfo = aggregate(m1, "Region", "Amount");
      ainfo.addGroup(new GroupRef(column(m1, "State")));
      ainfo.setCrosstab(true);
      m1.setAggregateInfo(ainfo);

      assertAggregateStays(m1);
   }

   @Test
   void percentageAggregateStaysOnTheMirror() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly m1 = mirror(ws, "M1", bound(ws, "T1", true));
      AggregateInfo ainfo = aggregate(m1, "Region", "Amount");
      ainfo.getAggregate(0).setPercentage(true);
      m1.setAggregateInfo(ainfo);

      assertAggregateStays(m1);
   }

   @Test
   void namedGroupAggregateStaysOnTheMirror() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly m1 = mirror(ws, "M1", bound(ws, "T1", true));
      AggregateInfo ainfo = aggregate(m1, "Region", "Amount");
      NamedGroupInfo groups = new NamedGroupInfo();
      groups.setGroupCondition("East", list(item(column(m1, "Region"), "NJ")));
      ainfo.getGroup(0).setNamedGroupInfo(groups);
      m1.setAggregateInfo(ainfo);

      assertAggregateStays(m1);
   }

   @Test
   void namedRangeGroupStaysOnTheMirror() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly m1 = mirror(ws, "M1", bound(ws, "T1", true));
      ColumnRef range = new ColumnRef(new NamedRangeRef("RegionRange",
                                                        column(m1, "Region").getDataRef()));
      AggregateInfo ainfo = new AggregateInfo();
      ainfo.addGroup(new GroupRef(range));
      ainfo.addAggregate(new AggregateRef(column(m1, "Amount"), AggregateFormula.SUM));
      m1.setAggregateInfo(ainfo);

      assertAggregateStays(m1);
   }

   @Test
   void boundTableWithItsOwnAggregateKeepsTheMirrorAggregate() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t1 = bound(ws, "T1", true);
      t1.setAggregateInfo(aggregate(t1, "State", "Amount"));
      TableAssembly m1 = mirror(ws, "M1", t1);
      m1.setAggregateInfo(aggregate(m1, "State", "Amount"));

      assertAggregateStays(m1);
   }

   @Test
   void joinAggregateStaysOnTheJoin() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly j1 = join(ws, bound(ws, "T1", true), bound(ws, "T2", true),
                              TableAssemblyOperator.INNER_JOIN);
      // both columns come from T1, so only the join itself keeps the aggregate up
      AggregateInfo ainfo = new AggregateInfo();
      ainfo.addGroup(new GroupRef(column(j1, "T1", "Region")));
      ainfo.addAggregate(new AggregateRef(column(j1, "T1", "Amount"), AggregateFormula.SUM));
      j1.setAggregateInfo(ainfo);

      assertAggregateStays(j1);
   }

   // ---------------------------------------------------------------- helpers

   private static void assertAggregateStays(TableAssembly table) throws Exception {
      ComposedTableAssembly result = (ComposedTableAssembly) WSMVTransformer.transform(table);

      assertFalse(result.getAggregateInfo().isEmpty());
      assertNull(result.getProperty("aggregate.down"));

      for(TableAssembly child : result.getTableAssemblies(false)) {
         assertNull(child.getProperty("aggregate.down"), child.getName());
      }
   }

   private static TableAssembly bound(Worksheet ws, String name, boolean wsmv) {
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, name);
      table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "ds1", "ORDERS"));
      ColumnSelection cols = new ColumnSelection();

      for(String col : new String[] { "Region", "State", "Amount" }) {
         ColumnRef ref = new ColumnRef(new AttributeRef(name, col));
         ref.setDataType("Amount".equals(col) ? XSchema.INTEGER : XSchema.STRING);
         cols.addAttribute(ref);
      }

      table.setColumnSelection(cols);

      if(wsmv) {
         RuntimeMV rmv = mock(RuntimeMV.class);
         when(rmv.isWSMV()).thenReturn(true);
         table.setRuntimeMV(rmv);
      }

      ws.addAssembly(table);
      return table;
   }

   private static TableAssembly mirror(Worksheet ws, String name, TableAssembly base) {
      MirrorTableAssembly table = new MirrorTableAssembly(ws, name, base);
      ws.addAssembly(table);
      return table;
   }

   private static TableAssembly join(Worksheet ws, TableAssembly left, TableAssembly right,
                                     int type)
   {
      TableAssemblyOperator op = new TableAssemblyOperator(
         left.getName(), column(left, "Region"), right.getName(), column(right, "Region"), type);
      RelationalJoinTableAssembly table = new RelationalJoinTableAssembly(
         ws, "J1", new TableAssembly[] { left, right }, new TableAssemblyOperator[] { op });
      // a join column has the name of its table as entity
      ColumnSelection cols = new ColumnSelection();

      for(TableAssembly sub : new TableAssembly[] { left, right }) {
         ColumnSelection subcols = sub.getColumnSelection(true);

         for(int i = 0; i < subcols.getAttributeCount(); i++) {
            DataRef col = subcols.getAttribute(i);
            ColumnRef ref = new ColumnRef(new AttributeRef(sub.getName(), col.getAttribute()));
            ref.setDataType(col.getDataType());
            cols.addAttribute(ref);
         }
      }

      table.setColumnSelection(cols);
      ws.addAssembly(table);
      return table;
   }

   /** The column of the table itself. */
   private static ColumnRef column(TableAssembly table, String attr) {
      return (ColumnRef) table.getColumnSelection().getAttribute(attr);
   }

   /** The column of a join that comes from the given table. */
   private static ColumnRef column(TableAssembly join, String entity, String attr) {
      ColumnSelection cols = join.getColumnSelection();

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         ColumnRef ref = (ColumnRef) cols.getAttribute(i);

         if(entity.equals(ref.getEntity()) && attr.equals(ref.getAttribute())) {
            return ref;
         }
      }

      throw new AssertionError("no " + entity + "." + attr + " in " + cols);
   }

   private static TableAssembly child(ComposedTableAssembly table, String name) {
      for(TableAssembly child : table.getTableAssemblies(false)) {
         if(name.equals(child.getName())) {
            return child;
         }
      }

      throw new AssertionError("no child " + name + " in " + table.getName());
   }

   private static AggregateInfo aggregate(TableAssembly table, String group, String sum) {
      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(column(table, group)));
      info.addAggregate(new AggregateRef(column(table, sum), AggregateFormula.SUM));
      return info;
   }

   private static ConditionItem item(DataRef ref, Object value) {
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue(value);
      return new ConditionItem(ref, cond, 0);
   }

   private static AssetCondition greaterThan(int value) {
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.GREATER_THAN);
      cond.addValue(value);
      return cond;
   }

   /** A condition list of items joined by the given junctions (item, junction, item ...). */
   private static ConditionList list(Object... parts) {
      ConditionList list = new ConditionList();

      for(Object part : parts) {
         if(part instanceof ConditionItem) {
            list.append((ConditionItem) part);
         }
         else {
            list.append(new JunctionOperator((Integer) part, 0));
         }
      }

      return list;
   }

   private static int size(ConditionListWrapper wrapper) {
      return wrapper == null || wrapper.getConditionList() == null ?
         0 : wrapper.getConditionList().getSize();
   }
}
