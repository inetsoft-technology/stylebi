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
package inetsoft.report.composition.graph;

import inetsoft.graph.EGraph;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The drawing rules GraphGenerator hands the graph package for a chart under the modern mark.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernMarkRulesTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedBarChartFloorsItsCorners() {
      List<IntervalElement> bars = intervals(measureChart(GraphTypes.CHART_BAR),
                                             VizMark.MODERN_LIGHT);

      assertEquals(1, bars.size(), "premise: one bar element");
      assertTrue(bars.get(0).isCornerFloor());
   }

   @Test
   void aClassicBarChartDoesNot() {
      List<IntervalElement> bars = intervals(measureChart(GraphTypes.CHART_BAR), null);

      assertEquals(1, bars.size(), "premise: one bar element");
      assertFalse(bars.get(0).isCornerFloor());
   }

   @Test
   void everyBarFamilyTypeIsFloored() {
      assertAllFloored(GraphTypes.CHART_PARETO, 1);
      assertAllFloored(GraphTypes.CHART_WATERFALL, 2);
      assertAllFloored(GraphTypes.CHART_INTERVAL, 1);

      List<IntervalElement> gantt = intervals(ganttChart(), VizMark.MODERN_LIGHT);
      assertEquals(1, gantt.size(), "premise: one gantt bar element");
      assertTrue(gantt.get(0).isCornerFloor(), "gantt");
   }

   private static void assertAllFloored(int chartType, int count) {
      List<IntervalElement> bars = intervals(measureChart(chartType), VizMark.MODERN_LIGHT);
      assertEquals(count, bars.size(), "premise: bar elements of " + chartType);

      for(IntervalElement bar : bars) {
         assertTrue(bar.isCornerFloor(), "chart type " + chartType);
      }
   }

   private record Chart(VSChartInfo info, Object[][] rows) {
   }

   private static Chart measureChart(int chartType) {
      VSChartDimensionRef state = dim("State");
      VSChartAggregateRef total = agg("Total", "Sum");
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(chartType);
      cinfo.setRTChartType(chartType);
      cinfo.addXField(state);
      cinfo.addYField(total);
      cinfo.updateChartType(false);
      Object[][] rows = {
         { "State", GraphUtil.getName(total) },
         { "CA", 200.0 },
         { "NY", -50.0 },
         { "TX", 1200.0 },
      };

      return new Chart(cinfo, rows);
   }

   private static Chart ganttChart() {
      VSChartDimensionRef task = dim("Task");
      VSChartAggregateRef start = agg("Start", "Min");
      VSChartAggregateRef end = agg("End", "Max");
      GanttVSChartInfo gantt = new GanttVSChartInfo();
      gantt.addYField(task);
      gantt.setStartField(start);
      gantt.setEndField(end);
      gantt.setRTYFields(new ChartRef[] { task });
      gantt.setRTStartField(start);
      gantt.setRTEndField(end);
      Object[][] rows = {
         { "Task", GraphUtil.getName(start), GraphUtil.getName(end) },
         { "Plan", new Date(1_700_000_000_000L), new Date(1_700_600_000_000L) },
         { "Build", new Date(1_700_600_000_000L), new Date(1_701_800_000_000L) },
      };

      return new Chart(gantt, rows);
   }

   private static List<IntervalElement> intervals(Chart chart, VizMark mark) {
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(chart.info());
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);
      EGraph graph = GraphGenerator.getGenerator(
         assemblyInfo, null, new DefaultDataSet(chart.rows()), new VariableTable(), null,
         XSourceInfo.NONE, null).createEGraph();
      List<IntervalElement> bars = new ArrayList<>();

      for(int i = 0; i < graph.getElementCount(); i++) {
         if(graph.getElement(i) instanceof IntervalElement bar) {
            bars.add(bar);
         }
      }

      return bars;
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField(name));
      dim.setGroupColumnValue(name);
      return dim;
   }

   private static VSChartAggregateRef agg(String name, String formula) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new BaseField(name));
      agg.setFormulaValue(formula);
      return agg;
   }
}
