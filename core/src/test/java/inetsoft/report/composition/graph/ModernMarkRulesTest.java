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
import inetsoft.graph.aesthetic.SizeFrame;
import inetsoft.graph.aesthetic.StaticSizeFrame;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.data.SumDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.visual.BarVO;
import inetsoft.graph.visual.ElementVO;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.LinearSizeFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.SizeFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticSizeFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
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

   @Test
   void theClampIsOnForAMarkedBarChart() {
      List<IntervalElement> bars = intervals(measureChart(GraphTypes.CHART_BAR),
                                             VizMark.MODERN_LIGHT);
      assertEquals(1, bars.size(), "premise: one bar element");
      assertTrue(bars.get(0).isWidthClamp());

      List<IntervalElement> classic = intervals(measureChart(GraphTypes.CHART_BAR), null);
      assertFalse(classic.get(0).isWidthClamp(), "classic");
   }

   @Test
   void theClampIsOffWithASizeBinding() {
      Chart chart = sizedChart(GraphTypes.CHART_BAR);
      List<IntervalElement> bars = intervals(chart, VizMark.MODERN_LIGHT);

      assertEquals(1, bars.size(), "premise: one bar element");
      assertNotNull(bars.get(0).getSizeFrame().getField(), "premise: the size is bound");
      assertFalse(bars.get(0).isWidthClamp());
   }

   @Test
   void theClampIsOffWhenTheAuthorChangedTheSize() {
      Chart chart = measureChart(GraphTypes.CHART_BAR);
      // a measure bar draws its measure's size frame
      SizeFrameWrapper size =
         ((ChartAggregateRef) chart.info().getYField(0)).getSizeFrameWrapper();
      ((StaticSizeFrameWrapper) size).setSize(25, CompositeValue.Type.USER);
      size.setChanged(true);
      List<IntervalElement> bars = intervals(chart, VizMark.MODERN_LIGHT);

      assertEquals(1, bars.size(), "premise: one bar element");
      assertEquals(25, bars.get(0).getSizeFrame().getSize(null), "premise: the author's size");
      assertFalse(bars.get(0).isWidthClamp());
   }

   @Test
   void anIntervalWithSizeBoundIsClamped() {
      List<IntervalElement> bars = intervals(sizedChart(GraphTypes.CHART_INTERVAL),
                                             VizMark.MODERN_LIGHT);

      assertEquals(1, bars.size(), "premise: one interval element");
      assertNotNull(bars.get(0).getSizeFrame().getField(), "premise: the size is bound");
      assertTrue(bars.get(0).isWidthClamp());
   }

   @Test
   void aModernGanttBarIsSize15() {
      assertEquals(15, ganttBarSize(ganttChart(), VizMark.MODERN_LIGHT), "modern");
      assertEquals(20, ganttBarSize(ganttChart(), null), "classic");
   }

   @Test
   void anAuthorsGanttSizeIsKept() {
      assertEquals(25, ganttBarSize(authorSizedGantt(), VizMark.MODERN_LIGHT));
   }

   @Test
   void anAuthorSizedGanttBarIsNotClamped() {
      BarVO plain = ganttBar(ganttChart());
      assertTrue(plain.getSizeFactor() * (1 + 29.0 * 15 / 30) / 30 > 32,
                 "premise: the default bar is wider than the cap " + plain.getSizeFactor());
      assertEquals(32, thickness(plain), 0.5, "the default modern bar is clamped");

      BarVO authored = ganttBar(authorSizedGantt());
      assertEquals((1 + 29.0 * 25 / 30) / 30 * authored.getSizeFactor(), thickness(authored), 0.5,
                   "the author's size draws unclamped");
   }

   @Test
   void anAuthorsSizeUnclampsTheWaterfallTotal() {
      List<IntervalElement> plain = intervals(measureChart(GraphTypes.CHART_WATERFALL),
                                              VizMark.MODERN_LIGHT);
      List<IntervalElement> bars = intervals(authorSized(measureChart(GraphTypes.CHART_WATERFALL)),
                                             VizMark.MODERN_LIGHT);

      assertEquals(2, bars.size(), "premise: the bars and the Total");
      assertTrue(bars.stream().anyMatch(b -> vars(b).contains(SumDataSet.SUM_HEADER_PREFIX)),
                 "premise: the Total's var has the sum prefix");
      plain.forEach(bar -> assertTrue(bar.isWidthClamp(), "premise: " + vars(bar)));
      bars.forEach(bar -> assertFalse(bar.isWidthClamp(), vars(bar)));
   }

   @Test
   void anAuthorsSizeUnclampsTheBrushedAllData() {
      Chart chart = measureChart(GraphTypes.CHART_BAR);
      Object[][] brushed = { chart.rows()[0], chart.rows()[1] };
      List<IntervalElement> plain = intervals(measureChart(GraphTypes.CHART_BAR),
                                              VizMark.MODERN_LIGHT, brushed);
      List<IntervalElement> bars = intervals(authorSized(chart), VizMark.MODERN_LIGHT, brushed);

      assertEquals(2, bars.size(), "premise: the all-data and the brushed bars");
      assertTrue(bars.stream().anyMatch(b -> b.getVar(0).startsWith(ElementVO.ALL_PREFIX)),
                 "premise: the all-data var has the brushing prefix");
      plain.forEach(bar -> assertTrue(bar.isWidthClamp(), "premise: " + vars(bar)));
      bars.forEach(bar -> assertFalse(bar.isWidthClamp(), vars(bar)));
   }

   @Test
   void aMarkedWaterfallSetsTheStepFloor() {
      List<IntervalElement> bars = intervals(measureChart(GraphTypes.CHART_WATERFALL),
                                             VizMark.MODERN_LIGHT);
      List<IntervalElement> classic = intervals(measureChart(GraphTypes.CHART_WATERFALL), null);
      List<IntervalElement> plain = intervals(measureChart(GraphTypes.CHART_BAR),
                                              VizMark.MODERN_LIGHT);

      assertEquals(2, bars.size(), "premise: the bars and the Total");
      assertEquals(2, classic.size(), "premise: the classic bars and the Total");
      bars.forEach(bar -> assertEquals(2, bar.getMinStepPx(), vars(bar)));
      classic.forEach(bar -> assertEquals(0, bar.getMinStepPx(), "classic " + vars(bar)));
      assertEquals(0, plain.get(0).getMinStepPx(), "a marked bar chart");
   }

   @Test
   void aModernIntervalIsHalfItsSlot() {
      SizeFrame modern = intervals(sizedChart(GraphTypes.CHART_INTERVAL), VizMark.MODERN_LIGHT)
         .get(0).getSizeFrame();
      SizeFrame classic = intervals(sizedChart(GraphTypes.CHART_INTERVAL), null)
         .get(0).getSizeFrame();

      assertEquals(modern.getLargest(), modern.getSmallest(), "modern");
      assertEquals(classic.getLargest() - 1, classic.getSmallest(), "classic");
   }

   // the author's 25 on the bar's measure, as the binding pane writes it
   private static Chart authorSized(Chart chart) {
      ChartAggregateRef measure = (ChartAggregateRef) chart.info().getYField(0);
      setAuthorSize(measure.getSizeFrameWrapper());
      return chart;
   }

   // the binding pane edits a Gantt bar's size on its start measure
   private static Chart authorSizedGantt() {
      Chart chart = ganttChart();
      ChartRef start = ((GanttVSChartInfo) chart.info()).getStartField();
      setAuthorSize(((ChartAggregateRef) start).getSizeFrameWrapper());
      return chart;
   }

   private static void setAuthorSize(SizeFrameWrapper size) {
      ((StaticSizeFrameWrapper) size).setSize(25, CompositeValue.Type.USER);
      size.setChanged(true);
   }

   private static String vars(IntervalElement elem) {
      return Arrays.toString(elem.getVars());
   }

   // the drawn bar of a modern Gantt chart
   private static BarVO ganttBar(Chart chart) {
      GraphGenerator gen = generator(chart, VizMark.MODERN_LIGHT, null);
      EGraph graph = gen.createEGraph();
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(gen.getData(), 0, 0, 400, 240);

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof BarVO bar) {
            return bar;
         }
      }

      return fail("premise: a Gantt bar is drawn");
   }

   private static double thickness(BarVO bar) {
      Rectangle2D box = bar.getShapes()[0].getBounds2D();
      return GTool.isHorizontal(bar.getScreenTransform()) ? box.getWidth() : box.getHeight();
   }

   private static double ganttBarSize(Chart chart, VizMark mark) {
      List<IntervalElement> gantt = intervals(chart, mark);
      assertEquals(1, gantt.size(), "premise: one gantt bar element");
      SizeFrame size = gantt.get(0).getSizeFrame();
      assertInstanceOf(StaticSizeFrame.class, size, "premise: a static gantt bar size");
      return size.getSize(null);
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

   // a chart with a measure on Size
   private static Chart sizedChart(int chartType) {
      VSChartDimensionRef state = dim("State");
      VSChartAggregateRef total = agg("Total", "Sum");
      VSChartAggregateRef qty = agg("Qty", "Sum");
      VSAestheticRef size = new VSAestheticRef();
      size.setDataRef(qty);
      size.setRTDataRef(qty);
      size.setVisualFrameWrapper(new LinearSizeFrameWrapper());
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(chartType);
      cinfo.setRTChartType(chartType);
      cinfo.addXField(state);
      cinfo.addYField(total);
      cinfo.setSizeField(size);
      cinfo.updateChartType(false);
      Object[][] rows = {
         { "State", GraphUtil.getName(total), GraphUtil.getName(qty) },
         { "CA", 200.0, 3.0 },
         { "NY", 50.0, 7.0 },
         { "TX", 1200.0, 11.0 },
      };

      return new Chart(cinfo, rows);
   }

   private static Chart ganttChart() {
      VSChartDimensionRef task = dim("Task");
      VSChartAggregateRef start = agg("Start", "Min");
      VSChartAggregateRef end = agg("End", "Max");
      GanttVSChartInfo gantt = new GanttVSChartInfo();
      gantt.setChartType(GraphTypes.CHART_GANTT);
      gantt.setRTChartType(GraphTypes.CHART_GANTT);
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
      return intervals(chart, mark, null);
   }

   // with brushed rows, the chart's rows are the all-data
   private static List<IntervalElement> intervals(Chart chart, VizMark mark, Object[][] brushed) {
      EGraph graph = generator(chart, mark, brushed).createEGraph();
      List<IntervalElement> bars = new ArrayList<>();

      for(int i = 0; i < graph.getElementCount(); i++) {
         if(graph.getElement(i) instanceof IntervalElement bar) {
            bars.add(bar);
         }
      }

      return bars;
   }

   private static GraphGenerator generator(Chart chart, VizMark mark, Object[][] brushed) {
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(chart.info());
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);
      DefaultDataSet all = new DefaultDataSet(chart.rows());

      return brushed == null
         ? GraphGenerator.getGenerator(assemblyInfo, null, all, new VariableTable(), null,
                                       XSourceInfo.NONE, null)
         : GraphGenerator.getGenerator(assemblyInfo, all, new DefaultDataSet(brushed),
                                       new VariableTable(), null, XSourceInfo.NONE, null);
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
