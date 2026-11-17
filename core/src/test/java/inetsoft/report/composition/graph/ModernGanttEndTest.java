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
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.scale.TimeScale;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSDensityDefaults;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernGanttEndTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedGanttEndsAboutOnePaddingBeforeTheEdge() {
      Gantt g = gantt(8, VizMark.MODERN_LIGHT, new Dimension(400, 240));
      EGraph graph = g.gen.createEGraph();
      VGraph vgraph = Plotter.getPlotter(graph)
         .plotAndLayout(g.gen.getData(), 0, 0, 400, 240);
      Scale time = ((RectCoord) vgraph.getCoordinate()).getYScale();
      double p = VSDensityDefaults.chartPadding(VizContext.of(g.info)).right;

      assertInstanceOf(TimeScale.class, time, "premise: the time scale");
      double lastEnd = date(2026, Calendar.JANUARY, 21 + 8 * 3).getTime();
      double plotWidth = vgraph.getPlotBounds().getWidth();
      double gap = (time.getMax() - lastEnd) / (time.getMax() - time.getMin()) * plotWidth;

      assertTrue(gap >= 0.9 * p && gap <= 1.5 * p, "gap " + gap + " for padding " + p);
   }

   @Test
   void classicAndAuthorMaxGanttsTouchTheEdge() {
      Gantt classic = gantt(8, null, new Dimension(400, 240));
      TimeScale time = (TimeScale) ((RectCoord) classic.gen.createEGraph().getCoordinate())
         .getYScale();
      assertEquals(0, time.getEndPadRatio(), 0);

      Gantt marked = gantt(8, VizMark.MODERN_LIGHT, new Dimension(400, 240));
      long max = date(2026, Calendar.MARCH, 1).getTime();
      marked.start.getAxisDescriptor().setMaximum(max);
      TimeScale scale = (TimeScale) ((RectCoord) marked.gen.createEGraph().getCoordinate())
         .getYScale();
      scale.init(marked.gen.getData());

      assertEquals(max, scale.getMax(), 0);
   }

   @Test
   void aNarrowPlotGetsNoPad() {
      assertEquals(0, GanttGraphGenerator.endPadRatio(12, 20), 0);
      assertEquals(12.0 / 188, GanttGraphGenerator.endPadRatio(12, 200), 1e-9);
   }

   private record Gantt(GraphGenerator gen, ChartVSAssemblyInfo info, VSChartAggregateRef start) {
   }

   private static Gantt gantt(int count, VizMark mark, Dimension size) {
      VSChartDimensionRef task = dim("Task");
      VSChartAggregateRef start = agg("Start");
      start.setFormulaValue("Min");
      VSChartAggregateRef end = agg("End");
      end.setFormulaValue("Max");
      GanttVSChartInfo gantt = new GanttVSChartInfo();
      gantt.setChartType(GraphTypes.CHART_GANTT);
      gantt.setRTChartType(GraphTypes.CHART_GANTT);
      gantt.addYField(task);
      gantt.setStartField(start);
      gantt.setEndField(end);
      gantt.setRTYFields(new ChartRef[] { task });
      gantt.setRTStartField(start);
      gantt.setRTEndField(end);
      Object[][] rows = new Object[count + 1][];
      rows[0] = new Object[] { "Task", GraphUtil.getName(start), GraphUtil.getName(end) };

      for(int i = 1; i <= count; i++) {
         rows[i] = new Object[] { "Workstream task number " + i,
                                  date(2026, Calendar.JANUARY, 1 + i * 3),
                                  date(2026, Calendar.JANUARY, 21 + i * 3) };
      }

      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVSChartInfo(gantt);
      info.setChartDescriptor(new ChartDescriptor());
      info.setVizMark(mark);
      GraphGenerator gen = GraphGenerator.getGenerator(info, null, new DefaultDataSet(rows),
                                                       new VariableTable(), null,
                                                       XSourceInfo.NONE, size);
      return new Gantt(gen, info, start);
   }

   private static Date date(int year, int month, int day) {
      Calendar cal = Calendar.getInstance();
      cal.clear();
      cal.set(year, month, day);
      return cal.getTime();
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField(name));
      dim.setGroupColumnValue(name);
      return dim;
   }

   private static VSChartAggregateRef agg(String name) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new BaseField(name));
      agg.setFormulaValue("Sum");
      return agg;
   }
}
