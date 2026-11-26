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

import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * The static helpers of {@link AbstractTransformer} that the MV analyzer, the transformers and
 * the selection queries share: whether a condition is dynamic (so the table can not get an MV),
 * whether an aggregate can be combined from an MV, whether a selection is on an aggregate, the
 * column mapping between a table and its child, and the ranking checks.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                 AbstractTransformerTest.RegistryConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AbstractTransformerTest {
   /**
    * The data source registry that isAggregateCombinable reads the database type from; the
    * data source of the test tables is not registered, so the type is unknown.
    */
   @Configuration
   static class RegistryConfiguration {
      @Bean
      DataSourceRegistry dataSourceRegistry() {
         return mock(DataSourceRegistry.class);
      }
   }

   // ---------------------------------------------------------------- isDynamicFilter

   @Test
   void anEmptyFilterIsNotDynamic() {
      Fixture fx = new Fixture();
      TransformationDescriptor desc = fx.descriptor(fx.bound("T1"));

      assertFalse(AbstractTransformer.isDynamicFilter(desc, null, null));
      assertFalse(AbstractTransformer.isDynamicFilter(desc, new ConditionList(), null));
   }

   @Test
   void aConstantConditionIsNotDynamic() {
      Fixture fx = new Fixture();
      TransformationDescriptor desc = fx.descriptor(fx.bound("T1"));

      assertFalse(AbstractTransformer.isDynamicFilter(desc, equalTo("Region", "East"), null));
   }

   @Test
   void aVariableMakesTheConditionDynamic() {
      Fixture fx = new Fixture();
      TransformationDescriptor desc = fx.descriptor(fx.bound("T1"));

      assertTrue(AbstractTransformer.isDynamicFilter(
         desc, equalTo("Region", new UserVariable("region")), null));
   }

   @Test
   void aSessionVariableMakesTheConditionDynamic() {
      Fixture fx = new Fixture();
      TransformationDescriptor desc = fx.descriptor(fx.bound("T1"));

      assertTrue(AbstractTransformer.isDynamicFilter(desc, equalTo("Region", "$(_USER_)"), null));
      // the name alone is a value, not a variable
      assertFalse(AbstractTransformer.isDynamicFilter(desc, equalTo("Region", "_USER_"), null));
   }

   @Test
   void aColumnOfATableBoundToAnInputMakesTheConditionDynamic() {
      // Bug #48975: the user can change the values of a table bound to an input
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      fx.bound("Input1");
      fx.input("Input1");
      TransformationDescriptor desc = fx.descriptor(t1);
      DataRef inputColumn = new AttributeRef("Input1", "Region");

      assertTrue(desc.isInputDynamicTable("Input1"));
      assertTrue(AbstractTransformer.isDynamicFilter(desc, equalTo("Region", inputColumn), null));
      assertFalse(AbstractTransformer.isDynamicFilter(
         desc, equalTo("Region", new AttributeRef("T1", "State")), null));
   }

   @Test
   void aSubqueryOnATableWithASelectionIsDynamic() {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      fx.bound("S1");
      fx.select("S1", "Region");
      TransformationDescriptor desc = fx.descriptor(t1);
      // the descriptor also puts a pseudo condition of the selection on the table, which
      // would make the table dynamic by itself; the selection alone must be enough
      desc.getTable("S1").setPreRuntimeConditionList(new ConditionList());

      assertTrue(AbstractTransformer.isDynamicFilter(desc, inSubquery("S1"), null));
   }

   @Test
   void aSubqueryOnATableWithARuntimeConditionIsDynamic() {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      TableAssembly s1 = fx.bound("S1");
      s1.setPreRuntimeConditionList(equalTo("Region", "East"));
      TransformationDescriptor desc = fx.descriptor(t1);

      assertTrue(AbstractTransformer.isDynamicFilter(desc, inSubquery("S1"), null));
   }

   @Test
   void aSubqueryOnAStaticTableIsNotDynamic() {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      TableAssembly s1 = fx.bound("S1");
      s1.setPreConditionList(equalTo("Region", "East"));
      TransformationDescriptor desc = fx.descriptor(t1);

      assertFalse(AbstractTransformer.isDynamicFilter(desc, inSubquery("S1"), null));
   }

   @Test
   void aSubqueryOnAComposedTableIsDynamicWhenATableBeneathIs() {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      TableAssembly s1 = fx.bound("S1");
      s1.setPreConditionList(equalTo("Region", new UserVariable("region")));
      fx.mirror("M1", s1);
      TransformationDescriptor desc = fx.descriptor(t1);

      assertTrue(AbstractTransformer.isDynamicFilter(desc, inSubquery("M1"), null));
   }

   // ---------------------------------------------------------------- isAggregateCombinable

   @Test
   void aSumByAGroupIsCombinable() {
      TableAssembly t1 = new Fixture().bound("T1");

      assertTrue(AbstractTransformer.isAggregateCombinable(t1, sumOfAmountBy("Region")));
   }

   @Test
   void aCrosstabIsNotCombinable() {
      TableAssembly t1 = new Fixture().bound("T1");
      AggregateInfo info = sumOfAmountBy("Region", "State");
      info.setCrosstab(true);

      assertFalse(AbstractTransformer.isAggregateCombinable(t1, info));
   }

   @Test
   void aDateGroupIsNotCombinable() {
      TableAssembly t1 = new Fixture().bound("T1");
      AggregateInfo info = sumOfAmountBy("Region");
      info.getGroup(0).setDateGroup(DateRangeRef.YEAR_INTERVAL);

      assertFalse(AbstractTransformer.isAggregateCombinable(t1, info));
   }

   @Test
   void aPercentageIsNotCombinable() {
      TableAssembly t1 = new Fixture().bound("T1");
      AggregateInfo info = sumOfAmountBy("Region");
      info.getAggregate(0).setPercentage(true);

      assertFalse(AbstractTransformer.isAggregateCombinable(t1, info));
   }

   @Test
   void anAggregateOnAnExpressionIsNotCombinable() {
      TableAssembly t1 = new Fixture().bound("T1");
      ExpressionRef expr = new ExpressionRef(null, "Double");
      expr.setExpression("field['Amount'] * 2");
      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(column("Region")));
      info.addAggregate(new AggregateRef(new ColumnRef(expr), AggregateFormula.SUM));

      assertFalse(AbstractTransformer.isAggregateCombinable(t1, info));
   }

   @Test
   void aSecondColumnOfAnotherTableIsNotCombinable() {
      TableAssembly t1 = new Fixture().bound("T1");
      AggregateInfo same = new AggregateInfo();
      same.addAggregate(new AggregateRef(new ColumnRef(new AttributeRef("T1", "Amount")),
                                         new ColumnRef(new AttributeRef("T1", "Weight")),
                                         AggregateFormula.WEIGHTED_AVG));
      AggregateInfo other = new AggregateInfo();
      other.addAggregate(new AggregateRef(new ColumnRef(new AttributeRef("T1", "Amount")),
                                          new ColumnRef(new AttributeRef("T2", "Weight")),
                                          AggregateFormula.WEIGHTED_AVG));

      assertTrue(AbstractTransformer.isAggregateCombinable(t1, same));
      assertFalse(AbstractTransformer.isAggregateCombinable(t1, other));
   }

   @Test
   void aFormulaThatCanNotBeCombinedIsNotCombinable() {
      TableAssembly t1 = new Fixture().bound("T1");
      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(column("Region")));
      info.addAggregate(new AggregateRef(column("Amount"), AggregateFormula.COUNT_DISTINCT));

      assertFalse(AbstractTransformer.isAggregateCombinable(t1, info));
   }

   // ---------------------------------------------------------------- isSelectionOnAggregate

   @Test
   void aSelectionOnAGroupIsNotOnTheAggregate() {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      t1.setAggregateInfo(sumOfAmountBy("Region"));
      fx.select("T1", "Region");

      assertFalse(AbstractTransformer.isSelectionOnAggregate(t1, fx.descriptor(t1)));
   }

   @Test
   void aRuntimeConditionOnTheAggregatedColumnIsOnTheAggregate() {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      t1.setAggregateInfo(sumOfAmountBy("Region"));
      TransformationDescriptor desc = fx.descriptor(t1);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(t1.getAggregateInfo().getAggregate(0), greaterThan(10), 0));
      t1.setPreRuntimeConditionList(list);

      assertTrue(AbstractTransformer.isSelectionOnAggregate(t1, desc));
   }

   // ---------------------------------------------------------------- column mapping

   @Test
   void theChildColumnIsFoundByItsAttributeWhenTheEntityIsTheChild() {
      ColumnSelection child = columns("T1", "Region", "Amount");

      ColumnRef[] refs = AbstractTransformer.getColumnRef(
         child, "T1", new AttributeRef("T1", "Amount"));

      assertSame(child.getAttribute(1), refs[0]);
      assertNull(AbstractTransformer.getColumnRef(child, "T1", new AttributeRef("T2", "Amount")));
   }

   @Test
   void anAliasOfTheChildColumnIsMatchedFirst() {
      ColumnSelection child = columns("T1", "Region", "Amount");
      ColumnRef total = new ColumnRef(new AttributeRef("T1", "Sales"));
      total.setAlias("Amount");
      child.addAttribute(total);

      ColumnRef[] refs = AbstractTransformer.getColumnRef(
         child, "T1", new AttributeRef("T1", "Amount"));

      assertSame(total, refs[0]);
   }

   @Test
   void aDateRangeAddsTheRangeColumnToTheChild() {
      ColumnSelection child = columns("T1", "Region", "Date");
      DateRangeRef year = new DateRangeRef("Year(Date)", new AttributeRef("T1", "Date"),
                                           DateRangeRef.YEAR_INTERVAL);

      ColumnRef[] refs = AbstractTransformer.getColumnRef(child, "T1", year);

      DateRangeRef range = (DateRangeRef) refs[0].getDataRef();
      assertEquals(DateRangeRef.YEAR_INTERVAL, range.getDateOption());
      assertTrue(child.containsAttribute(refs[0]));
      // the parent column is the result of the range, by its name
      assertEquals("Year(Date)", refs[1].getAttribute());
      assertEquals("T1", refs[1].getEntity());
   }

   @Test
   void theParentColumnIsFoundByTheChildTableAndAttribute() {
      ColumnSelection parent = columns("T1", "Region", "Amount");
      parent.addAttribute(new ColumnRef(new AttributeRef("T2", "Amount")));

      ColumnRef found = AbstractTransformer.getParentColumn(
         parent, "T2", new ColumnRef(new AttributeRef(null, "Amount")));
      ColumnRef missing = AbstractTransformer.getParentColumn(
         parent, "T3", new ColumnRef(new AttributeRef(null, "Amount")));

      assertSame(parent.getAttribute(2), found);
      // not in the parent: a new column of the child table
      assertFalse(parent.containsAttribute(missing));
      assertEquals("T3", missing.getEntity());
      assertEquals("Amount", missing.getAttribute());
   }

   @Test
   void aParentColumnThatDoesNotExistIsAnError() {
      ColumnSelection parent = columns("M1", "Region");
      ColumnSelection child = columns("T1", "Region");

      assertThrows(RuntimeException.class, () -> AbstractTransformer.getChildColumn(
         parent, child, "T1", new AttributeRef("M1", "Amount")));
   }

   // ---------------------------------------------------------------- ranking

   @Test
   void aRankingOnAColumnOfTheTableIsARanking() {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      TableAssembly t2 = fx.bound("T2");
      t1.setRankingConditionList(topN(column("Region"), null));
      t2.setRankingConditionList(topN(column("Other"), null));

      assertTrue(AbstractTransformer.containsRanking(t1));
      assertFalse(AbstractTransformer.containsRanking(t2));
   }

   @Test
   void aRuntimeRankingByAnAggregateIsAGroupRanking() {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      TableAssembly t2 = fx.bound("T2");
      AggregateRef sum = new AggregateRef(column("Amount"), AggregateFormula.SUM);
      t1.setRankingRuntimeConditionList(topN(column("Region"), sum));
      t2.setRankingRuntimeConditionList(topN(column("Region"), null));

      assertTrue(AbstractTransformer.containsGroupRanking(t1));
      assertFalse(AbstractTransformer.containsGroupRanking(t2));
   }

   // ---------------------------------------------------------------- helpers

   private static ColumnRef column(String name) {
      ColumnRef col = new ColumnRef(new AttributeRef(null, name));
      col.setDataType("Amount".equals(name) ? XSchema.INTEGER : XSchema.STRING);
      return col;
   }

   private static ColumnSelection columns(String entity, String... names) {
      ColumnSelection cols = new ColumnSelection();

      for(String name : names) {
         cols.addAttribute(new ColumnRef(new AttributeRef(entity, name)));
      }

      return cols;
   }

   private static AggregateInfo sumOfAmountBy(String... groups) {
      AggregateInfo info = new AggregateInfo();

      for(String group : groups) {
         info.addGroup(new GroupRef(column(group)));
      }

      info.addAggregate(new AggregateRef(column("Amount"), AggregateFormula.SUM));
      return info;
   }

   private static ConditionList equalTo(String column, Object value) {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue(value);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(column(column), cond, 0));
      return list;
   }

   private static AssetCondition greaterThan(int value) {
      AssetCondition cond = new AssetCondition(XSchema.INTEGER);
      cond.setOperation(XCondition.GREATER_THAN);
      cond.addValue(value);
      return cond;
   }

   /** Region one of the Region values of the query table. */
   private static ConditionList inSubquery(String query) {
      SubQueryValue sub = new SubQueryValue();
      sub.setQuery(query);
      sub.setAttribute(column("Region"));
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.ONE_OF);
      cond.addValue(sub);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(column("Region"), cond, 0));
      return list;
   }

   private static ConditionList topN(DataRef ref, DataRef aggregate) {
      RankingCondition cond = new RankingCondition();
      cond.setOperation(XCondition.TOP_N);
      cond.setN(3);
      cond.setDataRef(aggregate);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(ref, cond, 0));
      return list;
   }

   /** A worksheet and a viewsheet based on it. */
   private static final class Fixture {
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
         ws.addAssembly(table);
         return table;
      }

      TableAssembly mirror(String name, TableAssembly base) {
         MirrorTableAssembly table = new MirrorTableAssembly(ws, name, base);
         ws.addAssembly(table);
         return table;
      }

      void select(String table, String column) {
         SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "List_" + table);
         list.setTableName(table);
         list.setDataRef(column(column));
         vs.addAssembly(list);
      }

      /** A combo box bound to the Region column of the table. */
      void input(String table) {
         ComboBoxVSAssembly combo = new ComboBoxVSAssembly(vs, "Combo_" + table);
         combo.setTableName(table);
         combo.setColumn(column("Region"));
         vs.addAssembly(combo);
      }

      /** The descriptor of a viewsheet table bound to the table. */
      TransformationDescriptor descriptor(TableAssembly top) {
         TableVSAssembly table = new TableVSAssembly(vs, "Table1");
         table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, top.getName()));
         vs.addAssembly(table);
         TransformationDescriptor desc =
            new TransformationDescriptor(vs, ws, TransformationDescriptor.ANALYTIC_MODE);
         String name = top.getName();
         desc.reset("Table1", name, name, name);
         return desc;
      }
   }
}
