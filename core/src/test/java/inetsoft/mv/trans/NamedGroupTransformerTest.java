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

import inetsoft.mv.RuntimeMV;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * {@link NamedGroupTransformer}: a named group (a group on a {@link NamedRangeRef}) can not be
 * computed from an MV of its table, so the transformer moves the aggregate beneath it down to
 * the MV table, grouped by the column the named group is made of, and leaves the named group
 * and the re-aggregation above. When the MV table itself has the named group, it is renamed
 * {@code NG_<name>} and a mirror with the old name takes the aggregate.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class NamedGroupTransformerTest {
   // ---------------------------------------------------------------- not transformed

   @Test
   void aTableThatIsNotAMirrorOfTheMVTableIsNotTransformed() {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      TableAssembly t2 = fx.bound("T2");
      TableAssembly j1 = fx.join("J1", t1, t2);

      assertFalse(fx.transform(j1, t1));
      assertTrue(fx.table("T1").getAggregateInfo().isEmpty());
   }

   @Test
   void aChildWithItsOwnAggregateIsNotChanged() {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      AggregateInfo own = new AggregateInfo();
      own.addGroup(new GroupRef(fx.col(t1, "State")));
      own.addAggregate(new AggregateRef(fx.col(t1, "Amount"), AggregateFormula.MAX));
      t1.setAggregateInfo(own);
      TableAssembly m1 = fx.mirror("M1", t1);
      m1.setAggregateInfo(fx.namedGroupSum(m1, AggregateFormula.SUM));

      assertTrue(fx.transform(m1, t1));
      assertEquals(List.of("State"), groups(fx.table("T1")));
      assertEquals(List.of("Max(Amount)"), aggregates(fx.table("T1")));
   }

   @Test
   void aDistinctCountIsNotMovedDown() {
      // the distinct count of the named group can not be made from the counts of its values
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      TableAssembly m1 = fx.mirror("M1", t1);
      m1.setAggregateInfo(fx.namedGroupSum(m1, AggregateFormula.COUNT_DISTINCT));

      assertTrue(fx.transform(m1, t1));
      assertTrue(fx.table("T1").getAggregateInfo().isEmpty());
   }

   // ---------------------------------------------------------------- moved down

   @Test
   void aNamedGroupOnAMirrorMovesTheAggregateDown() {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      TableAssembly m1 = fx.mirror("M1", t1);
      m1.setAggregateInfo(fx.namedGroupSum(m1, AggregateFormula.SUM));

      assertTrue(fx.transform(m1, t1));

      // the MV table groups by the column the named group is made of and sums under the
      // id of the sub-aggregate
      assertEquals(List.of("Region"), groups(fx.table("T1")));
      assertEquals(List.of("Sum(Sum_T1_Amount)"), aggregates(fx.table("T1")));
      // the named group stays on the mirror, re-aggregating the sums
      AggregateInfo above = fx.table("M1").getAggregateInfo();
      assertInstanceOf(NamedRangeRef.class,
                       ((ColumnRef) above.getGroup(0).getDataRef()).getDataRef());
      assertEquals(1, above.getAggregateCount());
      CompositeAggregateRef sum =
         assertInstanceOf(CompositeAggregateRef.class, above.getAggregate(0));
      assertEquals(AggregateFormula.SUM, sum.getFormula());
      // re-aggregating the sum column of the MV table
      assertEquals(aggregates(fx.table("T1")), names(sum.getChildAggregates()));
   }

   @Test
   void anAverageIsMovedDownAsItsSubAggregates() {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      TableAssembly m1 = fx.mirror("M1", t1);
      m1.setAggregateInfo(fx.namedGroupSum(m1, AggregateFormula.AVG));

      assertTrue(fx.transform(m1, t1));

      // an average of averages would be wrong: the MV table keeps the parts of the average
      TableAssembly child = fx.table("T1");
      assertEquals(List.of("Region"), groups(child));
      assertEquals(List.of("Sum(Sum_T1_Amount)", "Count(Count_T1_Amount)"), aggregates(child));
      // the mirror computes the average from the parts in the MV table
      CompositeAggregateRef avg = assertInstanceOf(
         CompositeAggregateRef.class, fx.table("M1").getAggregateInfo().getAggregate(0));
      assertEquals(AggregateFormula.AVG, avg.getFormula());
      assertEquals(aggregates(child), names(avg.getChildAggregates()));
   }

   @Test
   void theColumnOfARuntimeConditionIsGroupedInTheChild() {
      // Bug #45181: the condition above needs its column in the MV table
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      TableAssembly m1 = fx.mirror("M1", t1);
      m1.setAggregateInfo(fx.namedGroupSum(m1, AggregateFormula.SUM));
      m1.setPreRuntimeConditionList(fx.equalTo(fx.col(m1, "State"), "NY"));

      assertTrue(fx.transform(m1, t1));
      assertEquals(Set.of("Region", "State"), new HashSet<>(groups(fx.table("T1"))));
   }

   @Test
   void theSecondColumnOfAnAggregateIsGroupedInTheChild() {
      // Bug #50296: the weight of a weighted average must be in the MV table
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      TableAssembly m1 = fx.mirror("M1", t1);
      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(fx.namedRange(m1)));
      info.addAggregate(new AggregateRef(fx.col(m1, "Amount"), fx.col(m1, "Weight"),
                                         AggregateFormula.WEIGHTED_AVG));
      m1.setAggregateInfo(info);

      assertTrue(fx.transform(m1, t1));
      assertTrue(groups(fx.table("T1")).contains("Weight"), groups(fx.table("T1")).toString());
   }

   @Test
   void anNthLargestIsMovedDown() {
      // pinned: only DistinctCount, Median and Mode stay on the mirror
      // (AbstractTransformer.containsNonCombinable). NthLargest has no sub-aggregates, so the
      // MV table takes the NthLargest of each Region and the mirror the NthLargest of those
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      TableAssembly m1 = fx.mirror("M1", t1);
      m1.setAggregateInfo(fx.namedGroupSum(m1, AggregateFormula.NTH_LARGEST));

      assertTrue(fx.transform(m1, t1));
      assertEquals(List.of("NthLargest(Amount)"), aggregates(fx.table("T1")));
   }

   // ---------------------------------------------------------------- on the MV table

   @Test
   void aNamedGroupOnTheMVTableRenamesItAndAddsAMirror() {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      t1.setAggregateInfo(fx.namedGroupSum(t1, AggregateFormula.SUM));
      t1.setPreRuntimeConditionList(fx.equalTo(fx.col(t1, "State"), "NY"));

      assertTrue(fx.transform(t1, t1));

      // the MV table is the renamed table, the old name is a mirror of it
      assertEquals("NG_T1", fx.desc.getMVAssembly());
      TableAssembly renamed = fx.table("NG_T1");
      MirrorTableAssembly mirror = (MirrorTableAssembly) fx.table("T1");
      assertNotNull(renamed);
      assertSame(renamed, mirror.getTableAssembly());
      // the named group and the condition are on the mirror; the renamed table is grouped by
      // the column of the named group and has no named group column
      assertInstanceOf(NamedRangeRef.class,
                       ((ColumnRef) mirror.getAggregateInfo().getGroup(0).getDataRef()).getDataRef());
      assertEquals(1, mirror.getPreRuntimeConditionList().getConditionList().getSize());
      assertTrue(groups(renamed).contains("Region"), groups(renamed).toString());
      assertEquals(0, renamed.getPreRuntimeConditionList().getConditionList().getSize());

      for(int i = 0; i < renamed.getColumnSelection().getAttributeCount(); i++) {
         DataRef ref = ((ColumnRef) renamed.getColumnSelection().getAttribute(i)).getDataRef();
         assertFalse(ref instanceof NamedRangeRef, "named group column left in " + ref);
      }
   }

   @Test
   void aNamedGroupDetailColumnMovesTheMVToTheMirrorBase() {
      // no aggregate: the MV is used from the table beneath, the named group is applied after
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      TableAssembly m1 = fx.mirror("M1", t1);
      fx.namedRange(m1);
      RuntimeMV rmv = mock(RuntimeMV.class);
      m1.setRuntimeMV(rmv);

      assertTrue(fx.transform(m1, m1));

      assertEquals("T1", fx.desc.getMVAssembly());
      assertNotNull(fx.table("T1").getRuntimeMV());
      assertNull(fx.table("M1").getRuntimeMV());
   }

   // ---------------------------------------------------------------- helpers

   private static List<String> groups(TableAssembly table) {
      List<String> names = new ArrayList<>();

      for(GroupRef group : table.getAggregateInfo().getGroups()) {
         names.add(group.getDataRef().getAttribute());
      }

      return names;
   }

   private static List<String> aggregates(TableAssembly table) {
      return names(Arrays.asList(table.getAggregateInfo().getAggregates()));
   }

   /** Each aggregate as Formula(column). */
   private static List<String> names(List<AggregateRef> aggregates) {
      List<String> names = new ArrayList<>();

      for(AggregateRef aggregate : aggregates) {
         names.add(aggregate.getFormula().getFormulaName() + "(" +
                      aggregate.getDataRef().getAttribute() + ")");
      }

      return names;
   }

   /** A worksheet and a viewsheet based on it. */
   private static final class Fixture {
      final Worksheet ws = new Worksheet();
      TransformationDescriptor desc;
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
         cols.addAttribute(typed(new AttributeRef(name, "Region"), XSchema.STRING));
         cols.addAttribute(typed(new AttributeRef(name, "State"), XSchema.STRING));
         cols.addAttribute(typed(new AttributeRef(name, "Amount"), XSchema.INTEGER));
         cols.addAttribute(typed(new AttributeRef(name, "Weight"), XSchema.INTEGER));
         table.setColumnSelection(cols);
         ws.addAssembly(table);
         return table;
      }

      TableAssembly mirror(String name, TableAssembly base) {
         MirrorTableAssembly table = new MirrorTableAssembly(ws, name, base);
         ws.addAssembly(table);
         return table;
      }

      TableAssembly join(String name, TableAssembly left, TableAssembly right) {
         TableAssemblyOperator op = new TableAssemblyOperator(
            left.getName(), col(left, "Region"), right.getName(), col(right, "Region"),
            TableAssemblyOperator.INNER_JOIN);
         RelationalJoinTableAssembly table = new RelationalJoinTableAssembly(
            ws, name, new TableAssembly[] { left, right }, new TableAssemblyOperator[] { op });
         ws.addAssembly(table);
         return table;
      }

      /** The column of the table by its attribute. */
      ColumnRef col(TableAssembly table, String attr) {
         ColumnSelection cols = table.getColumnSelection();

         for(int i = 0; i < cols.getAttributeCount(); i++) {
            ColumnRef ref = (ColumnRef) cols.getAttribute(i);

            if(attr.equals(ref.getAttribute())) {
               return ref;
            }
         }

         throw new AssertionError("no " + attr + " in " + table.getName() + ": " + cols);
      }

      /** A named group made of the Region column of the table, added to its columns. */
      ColumnRef namedRange(TableAssembly table) {
         ColumnRef range = new ColumnRef(
            new NamedRangeRef("RegionGroup", col(table, "Region").getDataRef()));
         range.setDataType(XSchema.STRING);
         ColumnSelection cols = table.getColumnSelection();
         cols.addAttribute(range);
         table.setColumnSelection(cols);
         return range;
      }

      /** Grouped by the named group of Region, the formula of Amount. */
      AggregateInfo namedGroupSum(TableAssembly table, AggregateFormula formula) {
         AggregateInfo info = new AggregateInfo();
         info.addGroup(new GroupRef(namedRange(table)));
         info.addAggregate(new AggregateRef(col(table, "Amount"), formula));
         return info;
      }

      ConditionList equalTo(DataRef ref, Object value) {
         AssetCondition cond = new AssetCondition(XSchema.STRING);
         cond.setOperation(XCondition.EQUAL_TO);
         cond.addValue(value);
         ConditionList list = new ConditionList();
         list.append(new ConditionItem(ref, cond, 0));
         return list;
      }

      /** The descriptor of a viewsheet table bound to top, with the MV table mv. */
      TransformationDescriptor descriptor(TableAssembly top, TableAssembly mv) {
         TableVSAssembly table = new TableVSAssembly(vs, "Table1");
         table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, top.getName()));
         vs.addAssembly(table);
         TransformationDescriptor desc =
            new TransformationDescriptor(vs, ws, TransformationDescriptor.ANALYTIC_MODE);
         desc.reset("Table1", top.getName(), mv.getName(), mv.getName());
         return desc;
      }

      /** Transform; the tables are then read from the worksheet of the descriptor. */
      boolean transform(TableAssembly top, TableAssembly mv) {
         desc = descriptor(top, mv);
         return new NamedGroupTransformer().transform(desc);
      }

      /** A table as the transformation left it. */
      TableAssembly table(String name) {
         return desc.getTable(name);
      }

      private static ColumnRef typed(AttributeRef ref, String type) {
         ColumnRef col = new ColumnRef(ref);
         col.setDataType(type);
         return col;
      }
   }
}
