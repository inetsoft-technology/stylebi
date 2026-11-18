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
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.GridLine;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.TimeScale;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.XFormatInfo;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernGanttTicksTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedGanttLabelsEveryTick() {
      Gantt g = gantt(25, VizMark.MODERN_LIGHT, new Dimension(400, 240), false);
      RectCoord coord = (RectCoord) g.gen.createEGraph().getCoordinate();
      TimeScale time = (TimeScale) coord.getYScale();
      Insets padding = VSDensityDefaults.chartPadding(VizContext.of(g.info));
      double tasks = ModernLabelRule.columnLimitPx(
         coord.getXScale().getAxisSpec().getTextSpec().getFont(), 400);
      double plot = 400 - padding.left - padding.right - tasks;
      Font font = time.getAxisSpec().getTextSpec().getFont();

      assertTrue(time.getLabelFitPlotPx() > 0);
      assertEquals(plot, time.getLabelFitPlotPx(), 1e-9);
      assertEquals(GTool.stringWidth(new String[] { "Sep 2026" }, font) + 4,
                   time.getLabelFitLabelPx(), 1e-9);
      assertFalse(time.getAxisSpec().isLabelBetween());
   }

   @Test
   void gridLinesSitOnlyAtLabelledTicks() {
      Gantt g = gantt(25, VizMark.MODERN_LIGHT, new Dimension(400, 240), false);
      EGraph graph = g.gen.createEGraph();
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(g.gen.getData(), 0, 0, 400, 240);
      RectCoord coord = (RectCoord) vgraph.getCoordinate();
      List<Double> labelX = new ArrayList<>();
      List<String> texts = new ArrayList<>();
      List<Double> lineX = new ArrayList<>();

      for(Axis axis : coord.getAxes(false)) {
         if(!(axis.getScale() instanceof TimeScale)) {
            continue;
         }

         for(VLabel label : axis.getLabels()) {
            if(label.getZIndex() >= 0) {
               labelX.add(label.getBounds().getCenterX());
               texts.add(label.getText());
            }
         }

         for(int i = 0; i < axis.getGridLineCount(); i++) {
            GridLine line = axis.getGridLine(i);

            if(line.getZIndex() >= 0) {
               lineX.add(line.getShape().getBounds2D().getCenterX());
            }
         }
      }

      assertFalse(lineX.isEmpty(), "premise: grid lines");
      assertFalse(labelX.isEmpty(), "premise: labels");

      for(double x : lineX) {
         assertTrue(labelX.stream().anyMatch(lx -> Math.abs(lx - x) < 2),
                    "grid line at " + x + " has no label, labels at " + labelX);
      }

      assertTrue(texts.stream().anyMatch(t -> t.startsWith("Jan")), texts.toString());
      assertTrue(texts.stream().anyMatch(t -> t.startsWith("Feb")), texts.toString());
      assertTrue(texts.stream().anyMatch(t -> t.startsWith("Mar")), texts.toString());
      assertTrue(texts.get(0).contains("2026"), texts.toString());
      assertEquals(1, texts.stream().filter(t -> t.contains("2026")).count(), texts.toString());
   }

   @Test
   void anAuthorIncrementKeepsTheClassicAxis() {
      Gantt g = gantt(25, VizMark.MODERN_LIGHT, new Dimension(400, 240), false);
      g.start.getAxisDescriptor().setIncrement(7);
      GraphGenerator gen = GraphGenerator.getGenerator(g.info, null, g.gen.getData(),
                                                       new VariableTable(), null,
                                                       XSourceInfo.NONE, new Dimension(400, 240));
      TimeScale time = (TimeScale) ((RectCoord) gen.createEGraph().getCoordinate()).getYScale();

      assertEquals(0, time.getLabelFitPlotPx(), 0);
      assertTrue(time.getAxisSpec().isLabelBetween());
   }

   @Test
   void anIntradayGanttKeepsHourTicks() {
      Gantt g = gantt(8, VizMark.MODERN_LIGHT, new Dimension(400, 240), true);
      VGraph vgraph = Plotter.getPlotter(g.gen.createEGraph())
         .plotAndLayout(g.gen.getData(), 0, 0, 400, 240);
      TimeScale time = (TimeScale) ((RectCoord) vgraph.getCoordinate()).getYScale();

      assertTrue(time.getLabelFitPlotPx() > 0, "premise: the fit is on");
      assertEquals(TimeScale.HOUR, time.getType());
   }

   @Test
   void anAuthorDateFormatKeepsTheClassicAxis() {
      Gantt g = gantt(25, VizMark.MODERN_LIGHT, new Dimension(400, 240), false);
      CompositeTextFormat fmt = new CompositeTextFormat();
      fmt.setFormat(new XFormatInfo("DateFormat", "yyyy"));
      g.start.getAxisDescriptor().setAxisLabelTextFormat(fmt);
      GraphGenerator gen = GraphGenerator.getGenerator(g.info, null, g.gen.getData(),
                                                       new VariableTable(), null,
                                                       XSourceInfo.NONE, new Dimension(400, 240));
      TimeScale time = (TimeScale) ((RectCoord) gen.createEGraph().getCoordinate()).getYScale();

      assertEquals(0, time.getLabelFitPlotPx(), 0);
      assertTrue(time.getAxisSpec().isLabelBetween());
   }

   @Test
   void classicGanttsAreUnchanged() {
      Gantt g = gantt(25, null, new Dimension(400, 240), false);
      TimeScale time = (TimeScale) ((RectCoord) g.gen.createEGraph().getCoordinate()).getYScale();

      assertEquals(0, time.getLabelFitPlotPx(), 0);
      assertTrue(time.getAxisSpec().isLabelBetween());
   }

   private record Gantt(GraphGenerator gen, ChartVSAssemblyInfo info, VSChartAggregateRef start) {
   }

   private static Gantt gantt(int count, VizMark mark, Dimension size, boolean intraday) {
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
         Date from = intraday ? hour(8 + i) : date(2026, Calendar.JANUARY, 1 + i * 3);
         Date to = intraday ? hour(9 + i) : date(2026, Calendar.JANUARY, 21 + i * 3);
         rows[i] = new Object[] { "Workstream task number " + i, from, to };
      }

      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVSChartInfo(gantt);
      info.setChartDescriptor(new ChartDescriptor());
      info.setVizMark(mark);
      info.setPadding(VSDensityDefaults.chartPadding(VizContext.of(info)));
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

   private static Date hour(int hour) {
      Calendar cal = Calendar.getInstance();
      cal.clear();
      cal.set(2026, Calendar.JANUARY, 5, hour, 0);
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
