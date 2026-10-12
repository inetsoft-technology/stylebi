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
package inetsoft.web.wiz.binding;

import inetsoft.uql.JunctionOperator;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.SNamedGroupInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.viewsheet.graph.AestheticRef;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.binding.drm.AttributeRefModel;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.NamedGroupInfoModel;
import inetsoft.web.binding.model.graph.AestheticInfo;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.binding.model.graph.OriginalDescriptor;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.graph.AestheticRefModelFactory;
import inetsoft.web.binding.service.graph.ChartAestheticService;
import inetsoft.web.binding.service.graph.ChartAggregateInfoFactory;
import inetsoft.web.binding.service.graph.ChartDimensionInfoFactory;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import inetsoft.web.composer.model.condition.ConditionExpression;
import inetsoft.web.composer.model.condition.ConditionModel;
import inetsoft.web.composer.model.condition.ConditionValueModel;
import inetsoft.web.composer.model.condition.JunctionOperatorModel;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78359: a chart read reports an inline named group as {@code namedGroupValues}, not the
 * placeholder {@code namedGroup:"Custom"}, and keeps doing so after unrelated chart writes (which
 * re-paste the promoted EXPERT model).
 */
@WizAgentTestSupport
class InlineNamedGroupReadTest {
   private static final FieldRef.NamedGroupValues GROUPS = new FieldRef.NamedGroupValues(List.of(
      new FieldRef.NamedGroupValues.Clause("West", List.of("CA", "OR")),
      new FieldRef.NamedGroupValues.Clause("Blank", List.of("", "WA"))), null);

   private static FieldRef inlineField() {
      return new FieldRef("State", "dimension", null, null, null, null, null, GROUPS);
   }

   private static void assertGroups(FieldRef ref) {
      assertNull(ref.namedGroup());
      assertNotNull(ref.namedGroupValues());
      List<FieldRef.NamedGroupValues.Clause> clauses = ref.namedGroupValues().groups();
      assertEquals(2, clauses.size());
      assertEquals("West", clauses.get(0).name());
      assertEquals(List.of("CA", "OR"), clauses.get(0).values());
      assertEquals("Blank", clauses.get(1).name());
      assertEquals(List.of("", "WA"), clauses.get(1).values());
   }

   /** Writes the field to the group shelf, then reads it back after {@code writes} pastes. */
   private static FieldRef shelfRead(FieldRef field, int writes) {
      try(AnnotationConfigApplicationContext context = context()) {
         ChartRefModelFactoryService service = context.getBean(ChartRefModelFactoryService.class);
         ChartBindingModel written = new ChartBindingModel();
         ChartBindingMutator.setShelf(written, "group", List.of(field));
         ChartRefModel model = written.getGroupFields().get(0);
         VSChartInfo cinfo = new DefaultVSChartInfo();
         cinfo.setChartType(GraphTypes.CHART_POINT);

         for(int i = 0; i < writes; i++) {
            ChartRef ref = service.pasteChartRef(cinfo, model);
            bindColumn(ref, field.column());
            model = service.createRefModel(
               ref, cinfo, new OriginalDescriptor(OriginalDescriptor.GROUP, 0));
         }

         return FieldRefFactory.from(model);
      }
   }

   private static FieldRef aestheticRead(FieldRef field, int writes) {
      try(AnnotationConfigApplicationContext context = context()) {
         AestheticRefModelFactory factory = context.getBean(AestheticRefModelFactory.class);
         ChartBindingModel written = new ChartBindingModel();
         ChartAestheticMutator.setField(written, "color", field);
         AestheticInfo info = written.getColorField();
         VSChartInfo cinfo = new DefaultVSChartInfo();
         cinfo.setChartType(GraphTypes.CHART_POINT);
         AestheticRef ref = null;

         for(int i = 0; i < writes; i++) {
            ref = factory.pasteAestheticRef(cinfo, ref, info);
            bindColumn(ref.getDataRef(), field.column());
            info = factory.createAestheticInfo(
               ref, cinfo, new OriginalDescriptor(OriginalDescriptor.COLOR, null));
         }

         return FieldRefFactory.from(info.getDataInfo());
      }
   }

   private static void bindColumn(Object ref, String column) {
      if(ref instanceof VSChartDimensionRef dim) {
         AttributeRef attribute = new AttributeRef(column);
         dim.setDataRef(attribute);

         if(dim.getNamedGroupInfo() instanceof SNamedGroupInfo named) {
            named.setDataRef(attribute);
         }
      }
   }

   @Test
   void anInlineGroupOnTheShelfReadsBackAsValuesAfterOneAndTwoWrites() {
      assertGroups(shelfRead(inlineField(), 1));
      assertGroups(shelfRead(inlineField(), 2));
   }

   @Test
   void anInlineGroupOnAnAestheticReadsBackAsValuesAfterOneAndTwoWrites() {
      assertGroups(aestheticRead(inlineField(), 1));
      assertGroups(aestheticRead(inlineField(), 2));
   }

   @Test
   void duplicateMembersCollapse() {
      FieldRef.NamedGroupValues dup = new FieldRef.NamedGroupValues(List.of(
         new FieldRef.NamedGroupValues.Clause("West", List.of("CA", "CA", "OR", "null"))), null);
      FieldRef ref = shelfRead(
         new FieldRef("State", "dimension", null, null, null, null, null, dup), 2);

      assertEquals(List.of("CA", "OR", "null"), ref.namedGroupValues().groups().get(0).values());
   }

   @Test
   void aStartingWithExpertGroupKeepsThePlaceholderRead() {
      assertCustom(chartDim("State", expert(
         group("West", condition("State", XCondition.STARTING_WITH, false, "C")))));
   }

   @Test
   void shapesOutsideTheMembershipRuleKeepThePlaceholderRead() {
      assertCustom(chartDim("State", expert(
         group("West", condition("State", XCondition.EQUAL_TO, true, "CA")))));
      assertCustom(chartDim("State", expert(
         group("West", condition("State", XCondition.EQUAL_TO, false, "CA")),
         group("East", condition("Other", XCondition.EQUAL_TO, false, "NY")))));
      assertCustom(chartDim("State", expert(
         group("West", condition("State", XCondition.EQUAL_TO, false, "CA"),
               JunctionOperator.AND, condition("State", XCondition.EQUAL_TO, false, "OR")))));
      assertCustom(chartDim("State", expert(
         group("West", condition("State", XCondition.EQUAL_TO, false, "CA")),
         group("East", condition("State", XCondition.STARTING_WITH, false, "N")))));

      ConditionModel variable = condition("State", XCondition.EQUAL_TO, false, "v");
      variable.getValues()[0].setType(ConditionValueModel.VARIABLE);
      assertCustom(chartDim("State", expert(group("West", variable))));

      ConditionModel nested = condition("State", XCondition.EQUAL_TO, false, "CA");
      nested.setLevel(1);
      assertCustom(chartDim("State", expert(group("West", nested))));
   }

   @Test
   void membershipConditionsReadAsValues() {
      ChartDimensionRefModel dim = chartDim("State", expert(group("West",
         condition("State", XCondition.ONE_OF, false, "CA", "OR"),
         JunctionOperator.OR, condition("State", XCondition.NULL, false))));

      FieldRef ref = FieldRefFactory.from(dim);

      assertEquals(List.of("CA", "OR", ""), ref.namedGroupValues().groups().get(0).values());
   }

   @Test
   void dateMembersLoseTheJdbcEscape() {
      ChartDimensionRefModel dim = chartDim("D", expert(group("Early",
         condition("D", XCondition.EQUAL_TO, false, "{d '2020-01-01'}"),
         JunctionOperator.OR,
         condition("D", XCondition.EQUAL_TO, false, "{ts '2020-01-02 10:30:00'}"))));

      FieldRef ref = FieldRefFactory.from(dim);

      assertEquals(List.of("2020-01-01", "2020-01-02 10:30:00"),
                   ref.namedGroupValues().groups().get(0).values());
   }

   /** An unparsable typed member is stored as is-null by the write path, so it reads as "". */
   @Test
   void anUnparsableTypedMemberReadsAsEmpty() {
      ChartDimensionRefModel dim = chartDim("Qty", expert(group("Odd",
         condition("Qty", XCondition.EQUAL_TO, false, 1),
         JunctionOperator.OR, condition("Qty", XCondition.NULL, false))));

      assertEquals(List.of(1, ""),
                   FieldRefFactory.from(dim).namedGroupValues().groups().get(0).values());
   }

   @Test
   void aCrosstabDimensionKeepsItsExpertReadEvenForEqualityConditions() {
      BDimensionRefModel dim = new BDimensionRefModel();
      dim.setColumnValue("State");
      dim.setNamedGroupInfo(expert(group("West",
         condition("State", XCondition.EQUAL_TO, false, "CA"))));

      FieldRef ref = FieldRefFactory.from(dim);

      assertEquals("Custom", ref.namedGroup());
      assertNull(ref.namedGroupValues());
   }

   private static void assertCustom(ChartDimensionRefModel dim) {
      FieldRef ref = FieldRefFactory.from(dim);
      assertEquals("Custom", ref.namedGroup());
      assertNull(ref.namedGroupValues());
   }

   private static ChartDimensionRefModel chartDim(String column, NamedGroupInfoModel ng) {
      ChartDimensionRefModel dim = new ChartDimensionRefModel();
      dim.setColumnValue(column);
      dim.setNamedGroupInfo(ng);
      return dim;
   }

   private static NamedGroupInfoModel expert(ConditionExpression... groups) {
      NamedGroupInfoModel model = new NamedGroupInfoModel();
      model.setType(XNamedGroupInfo.EXPERT_NAMEDGROUP_INFO);
      model.setName("Custom");
      model.setConditions(List.of(groups));
      return model;
   }

   /** Items alternate condition / junction type (an Integer at an odd index). */
   private static ConditionExpression group(String name, Object... list) {
      ConditionExpression ce = new ConditionExpression();
      ce.setName(name);
      List<Object> items = new ArrayList<>();

      for(int i = 0; i < list.length; i++) {
         if(i % 2 == 1) {
            JunctionOperatorModel junction = new JunctionOperatorModel();
            junction.setType((Integer) list[i]);
            junction.setLevel(0);
            items.add(junction);
         }
         else {
            items.add(list[i]);
         }
      }

      ce.setList(items.toArray());
      return ce;
   }

   private static ConditionModel condition(String field, int op, boolean negated, Object... vs) {
      ConditionModel cond = new ConditionModel();
      cond.setField(new AttributeRefModel(new AttributeRef(field)));
      cond.setOperation(op);
      cond.setNegated(negated);
      cond.setLevel(0);
      ConditionValueModel[] values = new ConditionValueModel[vs.length];

      for(int i = 0; i < vs.length; i++) {
         values[i] = new ConditionValueModel();
         values[i].setType(ConditionValueModel.VALUE);
         values[i].setValue(vs[i]);
      }

      cond.setValues(values);
      return cond;
   }

   private static AnnotationConfigApplicationContext context() {
      AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
      context.register(Beans.class, ChartAestheticService.class,
                       AestheticRefModelFactory.class, ChartRefModelFactoryService.class,
                       ChartAggregateInfoFactory.VSChartAggregateInfoFactory.class,
                       ChartDimensionInfoFactory.VSChartDimensionInfoFactory.class);
      context.refresh();
      return context;
   }

   @Configuration
   static class Beans {
      @Bean
      DataRefModelFactoryService dataRefModelFactoryService() {
         return new DataRefModelFactoryService(List.of(
            new AttributeRefModel.AttributeRefModelFactory(),
            new ColumnRefModel.ColumnRefModelFactory()));
      }

      @Bean
      VisualFrameModelFactoryService visualFrameModelFactoryService() {
         return new VisualFrameModelFactoryService(List.of());
      }
   }
}
