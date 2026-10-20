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

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.GanttGraphGenerator;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.binding.controller.ChangeChartAestheticService;
import inetsoft.web.binding.drm.AttributeRefModel;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.event.ChangeChartRefEvent;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.binding.service.VSChartBindingFactory;
import inetsoft.web.binding.service.graph.AestheticRefModelFactory;
import inetsoft.web.binding.service.graph.AllChartAggregateInfoFactory;
import inetsoft.web.binding.service.graph.ChartAestheticService;
import inetsoft.web.binding.service.graph.ChartAggregateInfoFactory;
import inetsoft.web.binding.service.graph.ChartDimensionInfoFactory;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.awt.Dimension;
import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78918, the reporter's chart: y = [PHASE, TASK_NAME], start = Min(START_DATE),
 * end = Max(END_DATE), milestone = Min(MILESTONE_DATE), with the start measure already coloured
 * by a measure (what {@code ChangeChartTypeProcessor.copyToGantt} leaves). Also the neighbours
 * on real chart-info classes: a milestone shelf cleared after the bind, start and milestone on the
 * same column, a real multi-style bar chart, and a plain bar chart.
 */
@WizAgentTestSupport
class ChartAestheticGanttReporterScenarioTest {
   private static final FieldRef PHASE = new FieldRef("PHASE", "dimension", null, null, null);

   @Test
   void theReportersGanttIsColouredByPhaseOnBarsAndMarkers() throws Exception {
      GanttVSChartInfo info = reportersGantt("MILESTONE_DATE");
      VSAestheticRef byMeasure = new VSAestheticRef();
      byMeasure.setDataRef(measure("START_DATE", AggregateFormula.MIN));
      ((ChartBindable) info.getStartField()).setColorField(byMeasure);
      Pipeline gantt = pipeline(info);

      gantt.service.setField("tok", principal(), "Chart1", "color", PHASE, null, "");

      assertEquals("PHASE", colorOf(gantt.gantt().getStartField()));
      assertEquals("PHASE", colorOf(gantt.gantt().getMilestoneField()));
      assertNull(colorOf(gantt.gantt().getEndField()));
      assertNull(gantt.gantt().getColorField());
      Map<String, Object> color = gantt.read("color");
      assertEquals("PHASE", color.get("field"));
      assertEquals(true, color.get("acceptsField"));
      assertEquals("PHASE", assertInstanceOf(FieldRef.class, color.get("fieldRef")).column());

      List<GraphElement> elements = render(gantt.gantt());
      assertPhase(elements.get(0).getColorFrame(), "the bars");
      assertPhase(elements.get(1).getColorFrame(), "the markers");
   }

   /** The milestone shelf cleared after the bind: the start keeps PHASE and clear still works. */
   @Test
   void clearingTheMilestoneShelfAfterTheBindKeepsTheBarsColouredAndClearable() throws Exception {
      Pipeline gantt = pipeline(reportersGantt("MILESTONE_DATE"));
      gantt.service.setField("tok", principal(), "Chart1", "color", PHASE, null, "");

      gantt.gantt().setMilestoneField(null);

      Map<String, Object> color = gantt.read("color");
      assertEquals("PHASE", color.get("field"));
      assertEquals(true, color.get("acceptsField"));
      assertNull(color.get("fieldsByMeasure"));

      gantt.service.clearField("tok", principal(), "Chart1", "color", "");

      assertNull(colorOf(gantt.gantt().getStartField()));
      assertNull(gantt.read("color").get("field"));
   }

   /** Start and milestone on the same column and formula: both get the field, no ambiguity. */
   @Test
   void startAndMilestoneOnTheSameColumnBothTakeTheField() throws Exception {
      Pipeline gantt = pipeline(reportersGantt("START_DATE"));
      assertEquals(GraphUtil.getName(gantt.gantt().getStartField()),
                   GraphUtil.getName(gantt.gantt().getMilestoneField()));

      gantt.service.setField("tok", principal(), "Chart1", "color", PHASE, null, "");

      assertEquals("PHASE", colorOf(gantt.gantt().getStartField()));
      assertEquals("PHASE", colorOf(gantt.gantt().getMilestoneField()));
      Map<String, Object> color = gantt.read("color");
      assertEquals("PHASE", color.get("field"));
      assertNull(color.get("fieldsByMeasure"));

      gantt.service.clearField("tok", principal(), "Chart1", "color", "");

      assertNull(colorOf(gantt.gantt().getStartField()));
      assertNull(colorOf(gantt.gantt().getMilestoneField()));
   }

   /** A real multi-style bar chart still gets the original refusal and remedy, set and clear. */
   @Test
   void aRealMultiStyleBarChartKeepsTheOriginalRefusal() throws Exception {
      VSChartInfo info = barChart();
      info.setMultiStyles(true);
      Pipeline bar = pipeline(info);
      assertTrue(info.isMultiAesthetic());

      for(boolean clear : new boolean[] { false, true }) {
         Exception e = assertThrows(Exception.class, () -> {
            if(clear) {
               bar.service.clearField("tok", principal(), "Chart1", "color", "");
            }
            else {
               bar.service.setField("tok", principal(), "Chart1", "color", PHASE, null, "");
            }
         });
         assertTrue(e.getMessage().contains("This chart is multi-style"), e.getMessage());
         assertTrue(e.getMessage().contains("multi:false"), e.getMessage());
      }

      assertEquals(false, bar.read("color").get("acceptsField"),
                   "the read agrees with the refused write");
   }

   @Test
   void aPlainBarChartStillBindsTheChartLevelColorField() throws Exception {
      Pipeline bar = pipeline(barChart());

      bar.service.setField("tok", principal(), "Chart1", "color", PHASE, null, "");

      assertEquals("PHASE", bar.chart.getVSChartInfo().getColorField().getFullName());
      Map<String, Object> color = bar.read("color");
      assertEquals("PHASE", color.get("field"));
      assertEquals(true, color.get("acceptsField"));
   }

   private static GanttVSChartInfo reportersGantt(String milestoneColumn) {
      GanttVSChartInfo info = new GanttVSChartInfo();
      info.setChartType(GraphTypes.CHART_GANTT);
      info.setMultiStyles(false);
      info.addYField(dimension("PHASE"));
      info.addYField(dimension("TASK_NAME"));
      info.setStartField(measure("START_DATE", AggregateFormula.MIN));
      info.setEndField(measure("END_DATE", AggregateFormula.MAX));
      info.setMilestoneField(measure(milestoneColumn, AggregateFormula.MIN));
      assertTrue(info.isMultiAesthetic() && !info.isMultiStyles());
      return info;
   }

   private static VSChartInfo barChart() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      info.addXField(dimension("TASK_NAME"));
      info.addYField(measure("START_DATE", AggregateFormula.COUNT_ALL));
      return info;
   }

   private static String colorOf(ChartRef ref) {
      AestheticRef field = ref == null ? null : ((ChartBindable) ref).getColorField();
      return field == null ? null : field.getFullName();
   }

   private static void assertPhase(VisualFrame frame, String where) {
      assertInstanceOf(CategoricalColorFrame.class, frame, where);
      assertEquals(GraphUtil.getName(dimension("PHASE")), frame.getField(), where);
   }

   /** As ChartAestheticGanttFieldTest renders: the runtime refs a live query builds, by hand. */
   private static List<GraphElement> render(GanttVSChartInfo info) {
      info.setRTStartField((VSChartRef) info.getStartField());
      info.setRTEndField((VSChartRef) info.getEndField());
      info.setRTMilestoneField((VSChartRef) info.getMilestoneField());

      for(ChartRef ref : new ChartRef[] { info.getStartField(), info.getMilestoneField() }) {
         if(((ChartBindable) ref).getColorField() instanceof VSAestheticRef aesthetic &&
            aesthetic.getDataRef() instanceof VSChartDimensionRef dim)
         {
            dim.setDataRef(new AttributeRef("PHASE"));
            aesthetic.setRTDataRef((VSDataRef) dim);
         }
      }

      String start = GraphUtil.getName(info.getStartField());
      String end = GraphUtil.getName(info.getEndField());
      String milestone = GraphUtil.getName(info.getMilestoneField());
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { start, end, milestone, GraphUtil.getName(dimension("PHASE")) },
         { 100.0, 200.0, 150.0, "Design" },
         { 200.0, 300.0, 250.0, "Build" },
      });
      Generator gen = new Generator(info, data);
      gen.build(new String[] { start, end, milestone });
      return List.of(gen.element(0), gen.element(1));
   }

   private static final class Generator extends GanttGraphGenerator {
      Generator(GanttVSChartInfo info, DefaultDataSet data) {
         super(info, new ChartDescriptor(), null, data, null, null, 0, new Dimension(400, 300));
      }

      void build(String[] names) {
         createElement(GraphTypes.CHART_GANTT, names, null);
      }

      GraphElement element(int i) {
         return graph.getElement(i);
      }
   }

   private static VSChartDimensionRef dimension(String column) {
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setGroupColumnValue(column);
      dim.setDataRef(new AttributeRef(column));
      return dim;
   }

   private static VSChartAggregateRef measure(String column, AggregateFormula formula) {
      VSChartAggregateRef aggregate = new VSChartAggregateRef();
      aggregate.setColumnValue(column);
      aggregate.setDataRef(new AttributeRef(column));
      aggregate.setFormula(formula);
      return aggregate;
   }

   private static Principal principal() {
      return () -> "admin";
   }

   private record Pipeline(ChartAestheticAgentService service, ChartVSAssembly chart) {
      GanttVSChartInfo gantt() {
         return (GanttVSChartInfo) chart.getVSChartInfo();
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> read(String channel) throws Exception {
         return (Map<String, Object>) service.read("tok", principal(), "Chart1").get(channel);
      }
   }

   /** The same harness as ChartAestheticGanttFieldTest, for any chart info. */
   private static Pipeline pipeline(VSChartInfo info) throws Exception {
      VSChartBindingFactory factory = realVSChartBindingFactory();

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.setVSChartInfo(info);
      vs.addAssembly(chart);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return List.of();
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenAnswer(
         invocation -> factory.createModel(invocation.getArgument(0)));
      ChangeChartAestheticService aesthetics = mock(ChangeChartAestheticService.class);
      doAnswer(invocation -> {
         ChangeChartRefEvent event = invocation.getArgument(1);
         factory.updateAssembly(event.getModel(), chart);
         return null;
      }).when(aesthetics).changeChartAesthetic(any(), any(), any(), any(), any());

      return new Pipeline(new ChartAestheticAgentService(
         sessions, binding, aesthetics, mock(DataRefModelFactoryService.class)), chart);
   }

   private static VSChartBindingFactory realVSChartBindingFactory() {
      try(AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
         context.register(Beans.class, ChartAestheticService.class,
                          AestheticRefModelFactory.class, ChartRefModelFactoryService.class,
                          ChartAggregateInfoFactory.VSChartAggregateInfoFactory.class,
                          ChartDimensionInfoFactory.VSChartDimensionInfoFactory.class,
                          AllChartAggregateInfoFactory.class, VSChartBindingFactory.class);
         context.scan("inetsoft.web.binding.service.graph.aesthetic");
         context.refresh();
         return context.getBean(VSChartBindingFactory.class);
      }
   }

   @Configuration
   static class Beans {
      @Bean
      DataRefModelFactoryService dataRefModelFactoryService() {
         return new DataRefModelFactoryService(List.of(
            new AttributeRefModel.AttributeRefModelFactory(),
            new ColumnRefModel.ColumnRefModelFactory()));
      }
   }
}
