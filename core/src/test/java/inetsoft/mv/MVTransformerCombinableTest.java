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
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Whether an aggregate can be computed from the results of the MV blocks
 * ({@link AggregateFormula#isCombinable()} and the two {@code MVTransformer.isCombinable}
 * methods). A non-combinable aggregate cannot be distributed over the blocks of an MV; the one
 * exception is a single distinct count, which the MV supports by breaking its blocks at the
 * distinct column.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVTransformerCombinableTest {
   // ---------------------------------------------------------------- AggregateFormula

   @Test
   void formulasThatNeedAllValuesAreNotCombinable() {
      AggregateFormula[] formulas = {
         AggregateFormula.COUNT_DISTINCT, AggregateFormula.MEDIAN, AggregateFormula.MODE,
         AggregateFormula.PRODUCT, AggregateFormula.CONCAT, AggregateFormula.NTH_LARGEST,
         AggregateFormula.NTH_SMALLEST, AggregateFormula.NTH_MOST_FREQUENT,
         AggregateFormula.PTH_PERCENTILE
      };

      assertAll(Arrays.stream(formulas).map(
         f -> () -> assertFalse(f.isCombinable(), f.getName())));
   }

   @Test
   void formulasWithMergeablePartialResultsAreCombinable() {
      AggregateFormula[] formulas = {
         AggregateFormula.NONE, AggregateFormula.AVG, AggregateFormula.COUNT_ALL,
         AggregateFormula.MAX, AggregateFormula.MIN, AggregateFormula.SUM,
         AggregateFormula.FIRST, AggregateFormula.LAST, AggregateFormula.CORRELATION,
         AggregateFormula.COVARIANCE, AggregateFormula.VARIANCE,
         AggregateFormula.STANDARD_DEVIATION, AggregateFormula.POPULATION_VARIANCE,
         AggregateFormula.POPULATION_STANDARD_DEVIATION, AggregateFormula.WEIGHTED_AVG,
         AggregateFormula.SUMSQ, AggregateFormula.SUMWT, AggregateFormula.AGGREGATE
      };

      assertAll(Arrays.stream(formulas).map(
         f -> () -> assertTrue(f.isCombinable(), f.getName())));
   }

   // ---------------------------------------------------------------- aggregate info

   @Test
   void noAggregateIsCombinable() {
      List<String> desc = new ArrayList<>();
      assertTrue(MVTransformer.isCombinable((AggregateInfo) null, desc));
      assertTrue(MVTransformer.isCombinable(new AggregateInfo(), desc));
      assertEquals(List.of(), desc);
   }

   @Test
   void combinableFormulasOnPlainGroups() {
      List<String> desc = new ArrayList<>();
      AggregateInfo info = info(new GroupRef(column("Region")),
                                aggregate("Amount", AggregateFormula.SUM),
                                aggregate("Price", AggregateFormula.AVG));

      assertTrue(MVTransformer.isCombinable(info, desc));
      assertEquals(List.of(), desc);
   }

   @Test
   void oneNonCombinableFormulaIsReported() {
      List<String> desc = new ArrayList<>();
      AggregateInfo info = info(new GroupRef(column("Region")),
                                aggregate("Amount", AggregateFormula.SUM),
                                aggregate("Price", AggregateFormula.MEDIAN));

      assertFalse(MVTransformer.isCombinable(info, desc));
      assertEquals(1, desc.size(), desc.toString());
      assertTrue(desc.get(0).startsWith("Field 'Price' with formula"), desc.get(0));
      assertTrue(desc.get(0).endsWith("is not combinable."), desc.get(0));
   }

   @Test
   void singleDistinctCountIsCombinable() {
      List<String> desc = new ArrayList<>();
      AggregateInfo info = info(new GroupRef(column("Region")),
                                aggregate("Customer", AggregateFormula.COUNT_DISTINCT),
                                aggregate("Amount", AggregateFormula.SUM));

      // the MV breaks its blocks at the distinct column, so one distinct count is supported
      assertTrue(MVTransformer.isCombinable(info, desc));
      assertEquals(List.of(), desc);
   }

   @Test
   void twoDistinctCountsAreNotCombinable() {
      List<String> desc = new ArrayList<>();
      AggregateInfo info = info(null,
                                aggregate("Customer", AggregateFormula.COUNT_DISTINCT),
                                aggregate("Product", AggregateFormula.COUNT_DISTINCT));

      assertFalse(MVTransformer.isCombinable(info, desc));
      assertEquals(1, desc.size(), desc.toString());
      assertTrue(desc.get(0).startsWith("Only one distinct count formula"), desc.get(0));
   }

   @Test
   void distinctCountWithAnotherNonCombinableIsNotCombinable() {
      List<String> desc = new ArrayList<>();
      AggregateInfo info = info(null,
                                aggregate("Customer", AggregateFormula.COUNT_DISTINCT),
                                aggregate("Price", AggregateFormula.MODE));

      assertFalse(MVTransformer.isCombinable(info, desc));
      // only the mode is described, the single distinct count is not a reason on its own
      assertEquals(1, desc.size(), desc.toString());
      assertTrue(desc.get(0).startsWith("Field 'Price' with formula"), desc.get(0));
   }

   @Test
   void namedGroupIsNotCombinable() {
      List<String> desc = new ArrayList<>();
      AggregateInfo info = info(new GroupRef(namedGroup("Region")),
                                aggregate("Amount", AggregateFormula.SUM));

      assertFalse(MVTransformer.isCombinable(info, desc));
      // the description names the column under the named group
      assertEquals(List.of("Field 'Region' with a named group is not combinable"), desc);
   }

   @Test
   void descriptionListIsOptional() {
      AggregateInfo info = info(new GroupRef(namedGroup("Region")),
                                aggregate("Customer", AggregateFormula.COUNT_DISTINCT),
                                aggregate("Product", AggregateFormula.COUNT_DISTINCT),
                                aggregate("Price", AggregateFormula.MEDIAN));

      assertFalse(MVTransformer.isCombinable(info, null));
   }

   // ---------------------------------------------------------------- table assembly

   @Test
   void rootTableWithoutRuntimeMVIsNotCombinable() throws Exception {
      TableAssembly table = table(null, null, new ColumnSelection(), null);
      assertFalse(MVTransformer.isCombinable(table, true));
   }

   @Test
   void plainChildTableWithoutRuntimeMVIsCombinable() throws Exception {
      // not on an MV itself, the MV may be on another table of the tree
      TableAssembly table = table(null, info(null, aggregate("Price", AggregateFormula.MEDIAN)),
                                  new ColumnSelection(), null);
      assertTrue(MVTransformer.isCombinable(table, false));
   }

   @Test
   void physicalMVWithoutDefinitionIsNotCombinable() throws Exception {
      TableAssembly table = table(physical("mv1"), null, new ColumnSelection(), null);

      try(MockedStatic<MVManager> ignored = manager("mv1", null)) {
         assertFalse(MVTransformer.isCombinable(table, true));
      }
   }

   @Test
   void physicalMVWithCombinableAggregate() throws Exception {
      TableAssembly table = table(physical("mv1"),
                                  info(new GroupRef(column("Region")),
                                       aggregate("Amount", AggregateFormula.SUM)),
                                  new ColumnSelection(), null);

      try(MockedStatic<MVManager> ignored = manager("mv1", definition(false))) {
         assertTrue(MVTransformer.isCombinable(table, true));
      }
   }

   @Test
   void physicalMVWithNonCombinableAggregate() throws Exception {
      TableAssembly table = table(physical("mv1"),
                                  info(null, aggregate("Price", AggregateFormula.MEDIAN)),
                                  new ColumnSelection(), null);

      try(MockedStatic<MVManager> ignored = manager("mv1", definition(false))) {
         assertFalse(MVTransformer.isCombinable(table, true));
      }
   }

   @Test
   void detailColumnWithNamedGroupIsNotCombinable() throws Exception {
      // no aggregate: the columns of the table are checked instead
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(column("Amount"));
      cols.addAttribute(namedGroup("Region"));
      TableAssembly table = table(physical("mv1"), new AggregateInfo(), cols, null);

      try(MockedStatic<MVManager> ignored = manager("mv1", definition(false))) {
         assertFalse(MVTransformer.isCombinable(table, true));
      }
   }

   @Test
   void detailColumnsWithoutNamedGroupAreCombinable() throws Exception {
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(column("Amount"));
      cols.addAttribute(column("Region"));
      TableAssembly table = table(physical("mv1"), new AggregateInfo(), cols, null);

      try(MockedStatic<MVManager> ignored = manager("mv1", definition(false))) {
         assertTrue(MVTransformer.isCombinable(table, true));
      }
   }

   @Test
   void runtimeConditionOnNamedGroupIsNotCombinable() throws Exception {
      ConditionList conds = new ConditionList();
      Condition cond = new Condition(XSchema.STRING);
      cond.addValue("East");
      conds.append(new ConditionItem(namedGroup("Region"), cond, 0));
      TableAssembly table = table(physical("mv1"),
                                  info(null, aggregate("Amount", AggregateFormula.SUM)),
                                  new ColumnSelection(), conds);

      try(MockedStatic<MVManager> ignored = manager("mv1", definition(false))) {
         assertFalse(MVTransformer.isCombinable(table, true));
      }
   }

   @Test
   void logicalModelMVMissingAColumnAsksToRegenerate() throws Exception {
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(column("Region"));
      TableAssembly table = table(physical("mv1"),
                                  info(new GroupRef(column("Region")), (AggregateRef[]) null),
                                  cols, null);

      // the definition has no columns at all
      try(MockedStatic<MVManager> ignored = manager("mv1", definition(true))) {
         MVExecutionException ex = assertThrows(
            MVExecutionException.class, () -> MVTransformer.isCombinable(table, true));
         assertTrue(ex.getMessage().contains("Please regenerate MV: Region"), ex.getMessage());
      }
   }

   @Test
   void composedTableChecksEveryChild() throws Exception {
      TableAssembly good = table(physical("mv1"),
                                 info(null, aggregate("Amount", AggregateFormula.SUM)),
                                 new ColumnSelection(), null);
      TableAssembly bad = table(physical("mv1"),
                                info(null, aggregate("Price", AggregateFormula.MEDIAN)),
                                new ColumnSelection(), null);
      TableAssembly plain = table(null, null, new ColumnSelection(), null);

      try(MockedStatic<MVManager> ignored = manager("mv1", definition(false))) {
         // the join itself has no runtime MV, it is checked through its children
         assertTrue(MVTransformer.isCombinable(composed(good, plain), false));
         assertFalse(MVTransformer.isCombinable(composed(good, bad), false));
         // a composed root without its own runtime MV is refused before the children
         assertFalse(MVTransformer.isCombinable(composed(good, plain), true));
      }
   }

   // ---------------------------------------------------------------- helpers

   private static ColumnRef column(String name) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, name));
      ref.setDataType(XSchema.STRING);
      return ref;
   }

   private static ColumnRef namedGroup(String name) {
      DataRef base = new AttributeRef(null, name);
      return new ColumnRef(new NamedRangeRef(name + "_group", base));
   }

   private static AggregateRef aggregate(String name, AggregateFormula formula) {
      return new AggregateRef(column(name), formula);
   }

   private static AggregateInfo info(GroupRef group, AggregateRef... aggregates) {
      AggregateInfo info = new AggregateInfo();

      if(group != null) {
         info.addGroup(group);
      }

      if(aggregates != null) {
         for(AggregateRef aggregate : aggregates) {
            info.addAggregate(aggregate);
         }
      }

      return info;
   }

   private static RuntimeMV physical(String mv) {
      RuntimeMV rmv = mock(RuntimeMV.class);
      when(rmv.isPhysical()).thenReturn(true);
      when(rmv.getMV()).thenReturn(mv);
      return rmv;
   }

   private static TableAssembly table(RuntimeMV rmv, AggregateInfo info, ColumnSelection cols,
                                      ConditionList conds)
   {
      TableAssembly table = mock(TableAssembly.class);
      when(table.getName()).thenReturn("T");
      when(table.getRuntimeMV()).thenReturn(rmv);
      when(table.getAggregateInfo()).thenReturn(info);
      when(table.getColumnSelection(true)).thenReturn(cols);
      when(table.getColumnSelection(false)).thenReturn(cols);
      when(table.getColumnSelection()).thenReturn(cols);
      when(table.getPreRuntimeConditionList()).thenReturn(conds);
      return table;
   }

   private static TableAssembly composed(TableAssembly... children) {
      ComposedTableAssembly table = mock(ComposedTableAssembly.class);
      when(table.getName()).thenReturn("J");
      when(table.getTableAssemblies(false)).thenReturn(children);
      return table;
   }

   private static MVDef definition(boolean logicalModel) {
      MVDef def = new MVDef();
      def.setLogicalModel(logicalModel);
      return def;
   }

   private static MockedStatic<MVManager> manager(String name, MVDef def) {
      MVManager manager = mock(MVManager.class);
      when(manager.get(name)).thenReturn(def);
      MockedStatic<MVManager> statics = mockStatic(MVManager.class);
      statics.when(MVManager::getManager).thenReturn(manager);
      return statics;
   }
}
