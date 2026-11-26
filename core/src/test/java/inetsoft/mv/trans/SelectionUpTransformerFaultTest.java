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
package inetsoft.mv.trans;

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * When {@link SelectionUpTransformer} refuses to move a selection from a child table up to
 * its parent, and the fault it reports. A selection below the MV table has to move up to
 * the MV table, otherwise the MV cannot be created for (or hit by) the viewsheet assembly.
 * Each case builds a worksheet with a selection list on the child table {@code T1} and the
 * MV on the top table, runs the transformation in analytic (MV creation) mode and checks the
 * result and the fault. The expected faults follow the rules in
 * {@code SelectionUpTransformer.transform}, {@code isMoveableToParent} and
 * {@code isMoveableColumn}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SelectionUpTransformerFaultTest {
   @AfterEach
   void resetProperties() {
      SreeEnv.resetProperty("mv.outer.moveUp", false);
      SreeEnv.resetProperty("mv.ignore.nestedSelection", false);
   }

   // ---------------------------------------------------------------- moved

   @Test
   void selectionMovesUpToAMirror() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly m1 = fx.mirror("M1", fx.bound("T1"));
      fx.select("T1");

      TransformationDescriptor desc = fx.run(m1);

      assertNoFault(desc);
      // the selection column now belongs to the MV table
      assertEquals(List.of(), desc.getSelectionColumns("T1", false));
      assertEquals(1, desc.getSelectionColumns("M1", false).size());
   }

   @Test
   void selectionMovesUpThroughAnInnerJoin() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly j1 = fx.join("J1", fx.bound("T1"), fx.bound("T2"),
                                 TableAssemblyOperator.INNER_JOIN);
      fx.select("T1");

      assertNoFault(fx.run(j1));
   }

   // ---------------------------------------------------------------- child table

   @Test
   void childWithMaxRows() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      t1.setMaxRows(5);
      TableAssembly m1 = fx.mirror("M1", t1);
      fx.select("T1");

      assertFault(fx.run(m1), "The max rows is defined in the block 'T1'");
   }

   @Test
   void childWithRanking() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      t1.setRankingConditionList(ranking(column("Amount")));
      TableAssembly m1 = fx.mirror("M1", t1);
      fx.select("T1");

      assertFault(fx.run(m1), "ranking and variable conditions in the table 'T1'");
   }

   @Test
   void childWithAggregateRanking() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      AggregateRef sum = new AggregateRef(column("Amount"), AggregateFormula.SUM);
      t1.setAggregateInfo(aggregate(sum, column("Region")));
      t1.setRankingRuntimeConditionList(ranking(sum));
      TableAssembly m1 = fx.mirror("M1", t1);
      fx.select("T1");

      assertFault(fx.run(m1), "variable aggregate ranking condition in the table 'T1'");
   }

   @Test
   void childCrosstab() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      t1.setAggregateInfo(crosstab());
      TableAssembly m1 = fx.mirror("M1", t1);
      fx.select("T1");

      assertFault(fx.run(m1), "The block 'T1' is a crosstab table");
   }

   @Test
   void childHiddenSelectionColumn() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      ((ColumnRef) t1.getColumnSelection().getAttribute("Region")).setVisible(false);
      TableAssembly m1 = fx.mirror("M1", t1);
      fx.select("T1");

      assertFault(fx.run(m1), "the selected column in the data block is hidden");
   }

   @Test
   void childNamedGroupOnSelectionColumn() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      GroupRef group = new GroupRef(column("Region"));
      group.setNamedGroupAssembly("Groups");
      t1.setAggregateInfo(aggregate(new AggregateRef(column("Amount"), AggregateFormula.SUM),
                                    group));
      TableAssembly m1 = fx.mirror("M1", t1);
      fx.select("T1");

      assertFault(fx.run(m1), "named group assembly in the child block 'T1'");
   }

   @Test
   void selectionOnAnAggregateOfTheChild() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      t1.setAggregateInfo(aggregate(new AggregateRef(column("Region"), AggregateFormula.COUNT_ALL),
                                    column("Amount")));
      TableAssembly m1 = fx.mirror("M1", t1);
      fx.select("T1");

      assertFault(fx.run(m1), "The selection column is aggregated");
   }

   // ---------------------------------------------------------------- parent table

   @Test
   void parentWithAnAggregatePostCondition() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly m1 = fx.mirror("M1", fx.bound("T1"));
      AggregateRef sum = new AggregateRef(column("Amount"), AggregateFormula.SUM);
      m1.setAggregateInfo(aggregate(sum, column("Region")));
      ConditionList post = new ConditionList();
      post.append(new ConditionItem(sum, greaterThan(10), 0));
      m1.setPostConditionList(post);
      fx.select("T1");

      assertFault(fx.run(m1), "post condition in the parent node 'M1'");
   }

   @Test
   void parentWithRanking() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly m1 = fx.mirror("M1", fx.bound("T1"));
      TableAssembly top = fx.mirror("M2", m1);
      // a ranking on a column of the parent itself
      m1.setRankingConditionList(ranking(m1.getColumnSelection().getAttribute("Amount")));
      fx.select("T1");

      assertFault(fx.run(top), "ranking condition in the parent node 'M1'");
   }

   @Test
   void parentCrosstab() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly m1 = fx.mirror("M1", fx.bound("T1"));
      m1.setAggregateInfo(crosstab());
      fx.select("T1");

      assertFault(fx.run(m1), "The parent node 'M1' is a crosstab table");
   }

   @Test
   void parentRotated() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      RotatedTableAssembly r1 = new RotatedTableAssembly(fx.ws, "R1", t1);
      fx.ws.addAssembly(r1);
      fx.select("T1");

      assertFault(fx.run(r1), "The parent node 'R1' is a rotated table");
   }

   @Test
   void parentUnpivot() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      UnpivotTableAssembly u1 = new UnpivotTableAssembly(fx.ws, "U1", t1);
      fx.ws.addAssembly(u1);
      fx.select("T1");

      assertFault(fx.run(u1), "The parent node 'U1' is an unpivot table");
   }

   @Test
   void parentWithAPercentage() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly m1 = fx.mirror("M1", fx.bound("T1"));
      AggregateRef sum = new AggregateRef(column("Amount"), AggregateFormula.SUM);
      sum.setPercentage(true);
      m1.setAggregateInfo(aggregate(sum, column("Region")));
      fx.select("T1");

      assertFault(fx.run(m1), "percentage aggregation in the parent node 'M1'");
   }

   @Test
   void parentWithANamedGroup() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly m1 = fx.mirror("M1", fx.bound("T1"));
      GroupRef group = new GroupRef(column("State"));
      group.setNamedGroupAssembly("Groups");
      m1.setAggregateInfo(aggregate(new AggregateRef(column("Amount"), AggregateFormula.SUM),
                                    column("Region"), group));
      fx.select("T1");

      assertFault(fx.run(m1), "named group assembly in the parent node 'M1'");
   }

   @Test
   void selectionOnAnAggregateOfTheParent() throws Exception {
      // at MV creation the restriction applies to the top table too
      Fixture fx = new Fixture();
      TableAssembly m1 = fx.mirror("M1", fx.bound("T1"));
      ColumnSelection pcols = m1.getColumnSelection();
      m1.setAggregateInfo(aggregate(
         new AggregateRef(pcols.getAttribute("Region"), AggregateFormula.COUNT_ALL),
         pcols.getAttribute("Amount")));
      fx.select("T1");

      assertFault(fx.run(m1), "The selection column is aggregated");
   }

   @Test
   void nestedSelections() throws Exception {
      // nested selections are ignored by default (mv.ignore.nestedSelection=true)
      SreeEnv.setProperty("mv.ignore.nestedSelection", "false");
      Fixture fx = new Fixture();
      TableAssembly m1 = fx.mirror("M1", fx.bound("T1"));
      TableAssembly top = fx.mirror("M2", m1);
      fx.select("T1");
      fx.select("M1");

      assertFault(fx.run(top), "The parent node 'M1' contains a nested selection");
   }

   @Test
   void nestedSelectionsAreIgnoredByDefault() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly m1 = fx.mirror("M1", fx.bound("T1"));
      TableAssembly top = fx.mirror("M2", m1);
      fx.select("T1");
      fx.select("M1");

      TransformationDescriptor desc = fx.run(top);

      assertFalse(reasons(desc).stream().anyMatch(r -> r.contains("nested selection")),
                  reasons(desc).toString());
   }

   // ---------------------------------------------------------------- joins

   @Test
   void selectionOnTheInnerSideOfALeftJoin() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly j1 = fx.join("J1", fx.bound("T2"), fx.bound("T1"),
                                 TableAssemblyOperator.LEFT_JOIN);
      fx.select("T1");

      assertFault(fx.run(j1), "{outer join}");
   }

   @Test
   void selectionOnTheInnerSideOfARightJoin() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly j1 = fx.join("J1", fx.bound("T1"), fx.bound("T2"),
                                 TableAssemblyOperator.RIGHT_JOIN);
      fx.select("T1");

      assertFault(fx.run(j1), "{outer join}");
   }

   @Test
   void selectionOnTheOuterSideOfALeftJoinMovesUp() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly j1 = fx.join("J1", fx.bound("T1"), fx.bound("T2"),
                                 TableAssemblyOperator.LEFT_JOIN);
      fx.select("T1");

      assertNoFault(fx.run(j1));
   }

   @Test
   void outerMoveUpPropertyAllowsTheInnerSide() throws Exception {
      SreeEnv.setProperty("mv.outer.moveUp", "true");
      Fixture fx = new Fixture();
      TableAssembly j1 = fx.join("J1", fx.bound("T2"), fx.bound("T1"),
                                 TableAssemblyOperator.LEFT_JOIN);
      fx.select("T1");

      assertNoFault(fx.run(j1));
   }

   @Test
   void selectionOnAFullOuterJoin() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly j1 = fx.join("J1", fx.bound("T1"), fx.bound("T2"),
                                 TableAssemblyOperator.FULL_JOIN);
      fx.select("T1");

      assertFault(fx.run(j1), "{outer join}");
   }

   @Test
   void selectionOnAMergeJoin() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      TableAssembly t2 = fx.bound("T2");
      TableAssemblyOperator op = new TableAssemblyOperator(
         "T1", null, "T2", null, TableAssemblyOperator.MERGE_JOIN);
      TableAssembly j1 = fx.add(new RelationalJoinTableAssembly(
         fx.ws, "J1", new TableAssembly[] { t1, t2 }, new TableAssemblyOperator[] { op }));
      fx.select("T1");

      assertFault(fx.run(j1), "{merge join}");
   }

   // ---------------------------------------------------------------- concatenation

   @Test
   void selectionUnderAUnionAll() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly c1 = fx.concat("C1", TableAssemblyOperator.UNION, false);
      fx.select("T1");

      assertFault(fx.run(c1), "The parent node 'C1' is a union table");
   }

   @Test
   void selectionOnTheRightTableOfAMinus() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly c1 = fx.concat("C1", TableAssemblyOperator.MINUS, false);
      fx.select("T2");

      assertFault(fx.run(c1), "The parent node 'C1' is a concatenated table");
   }

   @Test
   void selectionOnTheLeftTableOfAnIntersectMovesUp() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly c1 = fx.concat("C1", TableAssemblyOperator.INTERSECT, false);
      fx.select("T1");

      assertNoFault(fx.run(c1));
   }

   // ---------------------------------------------------------------- helpers

   private static List<String> reasons(TransformationDescriptor desc) {
      List<?> faults = desc.getFaults(Fixture.VS_ASSEMBLY);
      List<String> reasons = new ArrayList<>();

      if(faults != null) {
         for(Object fault : faults) {
            reasons.add(((TransformationFault) fault).getReason());
         }
      }

      return reasons;
   }

   private static void assertFault(TransformationDescriptor desc, String text) {
      List<String> reasons = reasons(desc);
      assertTrue(reasons.stream().anyMatch(r -> r.contains(text)),
                 "no fault with '" + text + "': " + reasons);
   }

   private static void assertNoFault(TransformationDescriptor desc) {
      assertEquals(List.of(), reasons(desc));
   }

   private static ColumnRef column(String name) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, name));
      ref.setDataType("Amount".equals(name) ? XSchema.INTEGER : XSchema.STRING);
      return ref;
   }

   private static AssetCondition greaterThan(int value) {
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.GREATER_THAN);
      cond.addValue(value);
      return cond;
   }

   private static ConditionList ranking(DataRef ref) {
      RankingCondition cond = new RankingCondition();
      cond.setOperation(XCondition.TOP_N);
      cond.setN(3);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(ref, cond, 0));
      return list;
   }

   private static AggregateInfo aggregate(AggregateRef aggregate, DataRef... groups) {
      AggregateInfo info = new AggregateInfo();

      for(DataRef group : groups) {
         info.addGroup(group instanceof GroupRef ? (GroupRef) group : new GroupRef(group));
      }

      info.addAggregate(aggregate);
      return info;
   }

   private static AggregateInfo crosstab() {
      // a crosstab needs two groups and an aggregate
      AggregateInfo info = aggregate(new AggregateRef(column("Amount"), AggregateFormula.SUM),
                                     column("Region"), column("State"));
      info.setCrosstab(true);
      return info;
   }

   /** A worksheet and a viewsheet with a table assembly bound to the top table. */
   private static final class Fixture {
      static final String VS_ASSEMBLY = "Table1";

      final Worksheet ws = new Worksheet();
      final Viewsheet vs = new Viewsheet() {
         @Override
         public Worksheet getBaseWorksheet() {
            return ws;
         }
      };

      TableAssembly bound(String name) {
         PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, name);
         table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "ds1", "ORDERS"));
         ColumnSelection cols = new ColumnSelection();
         cols.addAttribute(column("Region"));
         cols.addAttribute(column("State"));
         cols.addAttribute(column("Amount"));
         table.setColumnSelection(cols);
         return add(table);
      }

      TableAssembly mirror(String name, TableAssembly base) {
         return add(new MirrorTableAssembly(ws, name, base));
      }

      TableAssembly join(String name, TableAssembly left, TableAssembly right, int type) {
         TableAssemblyOperator op = new TableAssemblyOperator(
            left.getName(), column("Region"), right.getName(), column("Region"), type);
         return add(new RelationalJoinTableAssembly(
            ws, name, new TableAssembly[] { left, right }, new TableAssemblyOperator[] { op }));
      }

      TableAssembly concat(String name, int type, boolean distinct) {
         TableAssembly t1 = bound("T1");
         TableAssembly t2 = bound("T2");
         TableAssemblyOperator op = new TableAssemblyOperator(null, null, null, null, type);
         op.getOperator(0).setDistinct(distinct);
         return add(new ConcatenatedTableAssembly(
            ws, name, new TableAssembly[] { t1, t2 }, new TableAssemblyOperator[] { op }));
      }

      TableAssembly add(TableAssembly table) {
         ws.addAssembly(table);
         return table;
      }

      void select(String table) {
         SelectionListVSAssembly list =
            new SelectionListVSAssembly(vs, "List_" + table);
         list.setTableName(table);
         list.setDataRef(column("Region"));
         vs.addAssembly(list);
      }

      /** Bind a table assembly to the top table and move the selections up to it. */
      TransformationDescriptor run(TableAssembly top) {
         TableVSAssembly table = new TableVSAssembly(vs, VS_ASSEMBLY);
         table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, top.getName()));
         vs.addAssembly(table);

         TransformationDescriptor desc =
            new TransformationDescriptor(vs, ws, TransformationDescriptor.ANALYTIC_MODE);
         String name = top.getName();
         desc.reset(VS_ASSEMBLY, name, name, name);
         new SelectionUpTransformer().transform(desc);
         return desc;
      }
   }
}
