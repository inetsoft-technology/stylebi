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
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.FacetCoord;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.guide.axis.VDimensionLabel;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.scale.TimeScale;
import inetsoft.graph.visual.BarVO;
import inetsoft.graph.visual.GraphVO;
import inetsoft.report.composition.region.AxisArea;
import inetsoft.report.composition.region.ChartArea;
import inetsoft.report.composition.region.DefaultArea;
import inetsoft.report.composition.region.DimensionLabelArea;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XConstants;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A marked horizontal bar lists its first category at the top, as the Gantt does.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernTopDownOrderTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedHorizontalBarReversesItsCategories() {
      EGraph graph = generator(horizontal(GraphTypes.CHART_BAR), VizMark.MODERN_LIGHT)
         .createEGraph();

      assertTrue(reversed(scaleOf(graph, "Store")));
      assertFalse(intervals(graph).isEmpty(), "premise: a bar element");
      intervals(graph).forEach(e -> assertTrue(e.isDodgeReversed()));
   }

   @Test
   void anAscendingSortPutsAAtTheTop() {
      Chart chart = horizontal(GraphTypes.CHART_BAR);
      chart.rows()[1][0] = "C";
      chart.rows()[2][0] = "A";
      chart.rows()[3][0] = "B";
      GraphGenerator gen = generator(chart, VizMark.MODERN_LIGHT, ds -> ds.setComparator(
         "Store", (a, b) -> ((String) a).compareTo((String) b)));

      assertEquals("A", topCategory(gen));
   }

   @Test
   void aDescendingTopNPutsTheLargestAtTheTop() {
      Chart chart = horizontal(GraphTypes.CHART_BAR);
      GraphGenerator gen = generator(chart, VizMark.MODERN_LIGHT, ds -> ds.setComparator(
         "Store", (a, b) -> ((String) b).compareTo((String) a)));

      assertEquals("S3", topCategory(gen));
   }

   @Test
   void aStackedBarAndAnIntervalReverse() {
      for(int type : new int[] { GraphTypes.CHART_BAR_STACK, GraphTypes.CHART_INTERVAL }) {
         EGraph graph = generator(type == GraphTypes.CHART_BAR_STACK
                                     ? withColor(horizontal(type)) : horizontal(type),
                                  VizMark.MODERN_LIGHT).createEGraph();

         assertTrue(reversed(scaleOf(graph, "Store")), "type " + type);
         assertFalse(intervals(graph).isEmpty(), "premise: a bar element " + type);
         intervals(graph).forEach(e -> assertTrue(e.isDodgeReversed(), "type " + type));
      }
   }

   @Test
   void aClassicHorizontalBarKeepsItsOrder() {
      EGraph graph = generator(horizontal(GraphTypes.CHART_BAR), null).createEGraph();

      assertFalse(reversed(scaleOf(graph, "Store")));
      intervals(graph).forEach(e -> assertFalse(e.isDodgeReversed()));
   }

   @Test
   void aMarkedVerticalBarKeepsItsOrder() {
      EGraph graph = generator(vertical(), VizMark.MODERN_LIGHT).createEGraph();

      assertFalse(reversed(scaleOf(graph, "Store")));
      assertFalse(intervals(graph).isEmpty(), "premise: a bar element");
      intervals(graph).forEach(e -> assertFalse(e.isDodgeReversed()));
   }

   @Test
   void otherHorizontalChartsKeepTodaysOrder() {
      List<Chart> charts = new ArrayList<>();

      for(int type : new int[] { GraphTypes.CHART_POINT, GraphTypes.CHART_LINE,
                                 GraphTypes.CHART_3D_BAR, GraphTypes.CHART_WATERFALL,
                                 GraphTypes.CHART_PARETO, GraphTypes.CHART_FUNNEL,
                                 GraphTypes.CHART_BOXPLOT })
      {
         charts.add(horizontal(type));
      }

      VSChartAggregateRef bar = agg("Total");
      bar.setChartType(GraphTypes.CHART_BAR);
      bar.setRTChartType(GraphTypes.CHART_BAR);
      VSChartAggregateRef line = agg("Qty");
      line.setChartType(GraphTypes.CHART_LINE);
      line.setRTChartType(GraphTypes.CHART_LINE);
      charts.add(build(GraphTypes.CHART_BAR, List.of(bar, line), List.of(dim("Store")), true));

      for(Chart chart : charts) {
         EGraph graph;

         try {
            graph = generator(chart, VizMark.MODERN_LIGHT).createEGraph();
         }
         catch(RuntimeException ex) {
            fail("premise: the chart builds, type " + chart.info().getChartType(), ex);
            return;
         }

         Scale store = scaleOf(graph, "Store");
         assertNotNull(store, "premise: a Store scale, type " + chart.info().getChartType());
         assertFalse(reversed(store), "type " + chart.info().getChartType());
         intervals(graph).forEach(e -> assertFalse(e.isDodgeReversed(),
                                                   "type " + chart.info().getChartType()));
      }
   }

   @Test
   void aMarkedGanttIsReversedOnceWithoutTheFlag() {
      Chart chart = ganttChart();
      EGraph graph = generator(chart, VizMark.MODERN_LIGHT).createEGraph();
      EGraph classic = generator(ganttChart(), null).createEGraph();

      assertTrue(reversed(scaleOf(graph, "Task")));
      assertEquals(reversed(scaleOf(classic, "Task")), reversed(scaleOf(graph, "Task")));
      assertFalse(intervals(graph).isEmpty(), "premise: a gantt bar element");
      intervals(graph).forEach(e -> assertFalse(e.isDodgeReversed()));
   }

   @Test
   void facetOuterYDimensionsReverse() {
      Chart chart = build(GraphTypes.CHART_BAR, List.of(agg("Total")),
                          List.of(dim("Region"), dim("Store")), false);
      EGraph graph = generator(chart, VizMark.MODERN_LIGHT).createEGraph();
      EGraph classic = generator(build(GraphTypes.CHART_BAR, List.of(agg("Total")),
                                       List.of(dim("Region"), dim("Store")), false), null)
         .createEGraph();

      assertTrue(reversed(scaleOf(graph, "Region")));
      assertTrue(reversed(scaleOf(graph, "Store")));
      assertFalse(reversed(scaleOf(classic, "Region")));
      assertFalse(reversed(scaleOf(classic, "Store")));
   }

   @Test
   void facetOuterXDimensionsDoNot() {
      Chart chart = build(GraphTypes.CHART_BAR, List.of(dim("Region"), agg("Total")),
                          List.of(dim("Store")), false);
      EGraph graph = generator(chart, VizMark.MODERN_LIGHT).createEGraph();

      assertFalse(reversed(scaleOf(graph, "Region")));
      assertTrue(reversed(scaleOf(graph, "Store")));
   }

   @Test
   void aSeparatedChartReversesItsSharedScale() {
      Chart chart = build(GraphTypes.CHART_BAR, List.of(agg("Total"), agg("Qty")),
                          List.of(dim("Store")), false);
      chart.info().setSeparatedGraph(true);
      EGraph graph = generator(chart, VizMark.MODERN_LIGHT).createEGraph();
      List<Scale> stores = new ArrayList<>();
      collectScales(graph.getCoordinate(), "Store", stores);

      assertFalse(stores.isEmpty(), "premise: a Store scale");
      stores.forEach(s -> assertTrue(reversed(s)));
      assertTrue(stores.stream().allMatch(s -> s == stores.get(0)), "one shared scale");
      intervals(graph).forEach(e -> assertTrue(e.isDodgeReversed()));
   }

   @Test
   void aBrushedChartFlagsEveryBar() {
      Chart chart = horizontal(GraphTypes.CHART_BAR);
      Object[][] brushed = { chart.rows()[0], chart.rows()[1] };
      EGraph graph = generator(chart, VizMark.MODERN_LIGHT, brushed).createEGraph();

      assertEquals(2, intervals(graph).size(), "premise: the all-data and the brushed bar");
      intervals(graph).forEach(e -> assertTrue(e.isDodgeReversed()));
   }

   @Test
   void aClusteredBarsLegendKeepsItsOrder() {
      Chart chart = withColor(horizontal(GraphTypes.CHART_BAR));
      GraphGenerator gen = generator(chart, VizMark.MODERN_LIGHT);
      EGraph graph = gen.createEGraph();
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(gen.getData(), 0, 0, 400, 240);
      VisualFrame frame = graph.getElement(0).getColorFrame();
      Object[] legend = frame.getValues();
      List<BarVO> bars = bars(vgraph);

      assertEquals("P0", legend[0], "the legend lists P0 first");

      for(String store : new String[] { "S1", "S2", "S3" }) {
         double p0 = centerY(bars, gen.getData(), store, "P0");
         double p1 = centerY(bars, gen.getData(), store, "P1");
         assertTrue(p0 > p1, "P0 is the top bar of " + store);
      }
   }

   @Test
   void aTimeSeriesYDateStaysBottomUp() {
      VSChartDimensionRef day = dim("Day");
      day.setDataType(XSchema.DATE);
      day.setDateLevelValue(String.valueOf(XConstants.DAY_DATE_GROUP));
      day.setTimeSeries(true);
      Chart chart = build(GraphTypes.CHART_BAR, List.of(agg("Total")), List.of(day), false);
      long start = 1_700_000_000_000L;
      Object[][] rows = new Object[6][];
      rows[0] = new Object[] { GraphUtil.getName(day), GraphUtil.getName(agg("Total")) };

      for(int i = 1; i < 6; i++) {
         rows[i] = new Object[] { new java.sql.Date(start + i * 86_400_000L), 10.0 * i };
      }

      EGraph graph = generator(new Chart(chart.info(), rows), VizMark.MODERN_LIGHT)
         .createEGraph();
      Scale y = scaleOf(graph, "Day");

      assertTrue(y instanceof TimeScale, "premise: a time scale " + y);
      assertFalse(reversed(y));
      intervals(graph).forEach(e -> assertFalse(e.isDodgeReversed()));
   }

   @Test
   void dcLinesFollowTheBars() {
      GraphGenerator marked = generator(horizontal(GraphTypes.CHART_BAR), VizMark.MODERN_LIGHT);
      marked.createEGraph();
      GraphGenerator classic = generator(horizontal(GraphTypes.CHART_BAR), null);
      classic.createEGraph();

      assertFalse(marked.reverseDcLines());
      assertTrue(classic.reverseDcLines());

      VSChartDimensionRef day = dim("Day");
      day.setDataType(XSchema.DATE);
      day.setDateLevelValue(String.valueOf(XConstants.DAY_DATE_GROUP));
      day.setTimeSeries(true);
      Chart chart = build(GraphTypes.CHART_BAR, List.of(agg("Total")), List.of(day), false);
      Object[][] rows = new Object[3][];
      rows[0] = new Object[] { GraphUtil.getName(day), GraphUtil.getName(agg("Total")) };
      rows[1] = new Object[] { new java.sql.Date(1_700_000_000_000L), 10.0 };
      rows[2] = new Object[] { new java.sql.Date(1_700_086_400_000L), 20.0 };
      GraphGenerator time = generator(new Chart(chart.info(), rows), VizMark.MODERN_LIGHT);
      time.createEGraph();

      assertTrue(time.reverseDcLines());
   }

   @Test
   void theTopLabelRegionIsTheFirstCategory() {
      Chart chart = horizontal(GraphTypes.CHART_BAR);
      chart.rows()[1][0] = "C";
      chart.rows()[2][0] = "A";
      chart.rows()[3][0] = "B";
      GraphGenerator gen = generator(chart, VizMark.MODERN_LIGHT, ds -> ds.setComparator(
         "Store", (a, b) -> ((String) a).compareTo((String) b)));
      EGraph graph = gen.createEGraph();
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(gen.getData(), 0, 0, 400, 240);
      ChartArea area = new ChartArea(vgraph, graph, gen.getData(), null, chart.info());
      AxisArea left = area.getLeftYAxisArea();
      assertNotNull(left, "premise: a left axis area");
      DimensionLabelArea top = null;

      for(DefaultArea child : left.getAllAreas()) {
         if(child instanceof DimensionLabelArea label && (top == null ||
            label.getRegion().getBounds().getY() < top.getRegion().getBounds().getY()))
         {
            top = label;
         }
      }

      assertNotNull(top, "premise: label areas");
      assertEquals("A", ((VDimensionLabel) top.getVisualizable()).getValue());
   }

   private static String topCategory(GraphGenerator gen) {
      EGraph graph = gen.createEGraph();
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(gen.getData(), 0, 0, 400, 240);
      DataSet data = gen.getData();
      BarVO top = null;

      for(BarVO bar : bars(vgraph)) {
         if(top == null || center(bar) > center(top)) {
            top = bar;
         }
      }

      assertNotNull(top, "premise: bars");
      return (String) data.getData("Store", top.getSubRowIndex());
   }

   private static double center(BarVO bar) {
      return bar.getBounds().getCenterY();
   }

   private static double centerY(List<BarVO> bars, DataSet data, String store, String series) {
      for(BarVO bar : bars) {
         if(store.equals(data.getData("Store", bar.getSubRowIndex())) &&
            series.equals(data.getData("Series", bar.getSubRowIndex())))
         {
            return center(bar);
         }
      }

      fail("no bar for " + store + " " + series);
      return 0;
   }

   private static List<BarVO> bars(VGraph vgraph) {
      List<BarVO> bars = new ArrayList<>();

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof BarVO bar) {
            bars.add(bar);
         }
         else if(vgraph.getVisual(i) instanceof GraphVO sub) {
            bars.addAll(bars(sub.getVGraph()));
         }
      }

      return bars;
   }

   private static List<IntervalElement> intervals(EGraph graph) {
      List<IntervalElement> list = new ArrayList<>();

      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);

         if(elem instanceof IntervalElement interval) {
            list.add(interval);
         }
      }

      return list;
   }

   private static boolean reversed(Scale scale) {
      return scale instanceof CategoricalScale cat && cat.isReversed();
   }

   private static Scale scaleOf(EGraph graph, String field) {
      for(Scale scale : graph.getCoordinate().getScales()) {
         if(scale.getFields().length > 0 && scale.getFields()[0].contains(field)) {
            return scale;
         }
      }

      return null;
   }

   private static void collectScales(Coordinate coord, String field, List<Scale> out) {
      for(Scale scale : coord.getScales()) {
         if(scale.getFields().length > 0 && scale.getFields()[0].contains(field)) {
            out.add(scale);
         }
      }

      if(coord instanceof FacetCoord facet) {
         collectScales(facet.getOuterCoordinate(), field, out);

         for(Coordinate inner : facet.getInnerCoordinates()) {
            collectScales(inner, field, out);
         }
      }
   }

   private record Chart(VSChartInfo info, Object[][] rows) {
   }

   // Store on Y, Total on X
   private static Chart horizontal(int type) {
      return build(type, List.of(agg("Total")), List.of(dim("Store")), false);
   }

   private static Chart vertical() {
      return build(GraphTypes.CHART_BAR, List.of(dim("Store")), List.of(agg("Total")), false);
   }

   // adds a Series colour dimension and its rows
   private static Chart withColor(Chart chart) {
      VSChartDimensionRef series = dim("Series");
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(series);
      color.setRTDataRef(series);
      color.setVisualFrameWrapper(new CategoricalColorFrameWrapper());
      chart.info().setColorField(color);
      Object[][] src = chart.rows();
      List<Object[]> rows = new ArrayList<>();
      Object[] header = Arrays.copyOf(src[0], src[0].length + 1);
      header[src[0].length] = "Series";
      rows.add(header);

      for(int i = 1; i < src.length; i++) {
         for(String s : new String[] { "P0", "P1" }) {
            Object[] row = Arrays.copyOf(src[i], src[i].length + 1);
            row[src[i].length] = s;
            row[src[i].length - 1] = ((Number) src[i][src[i].length - 1]).doubleValue() +
               ("P1".equals(s) ? 5 : 0);
            rows.add(row);
         }
      }

      return new Chart(chart.info(), rows.toArray(new Object[0][]));
   }

   // the dimensions come first in each row, then the measures
   private static Chart build(int type, List<? extends ChartRef> xs, List<? extends ChartRef> ys,
                              boolean multi)
   {
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(type);
      cinfo.setRTChartType(type);
      List<ChartRef> all = new ArrayList<>();

      for(ChartRef x : xs) {
         cinfo.addXField(x);
         all.add(x);
      }

      for(ChartRef y : ys) {
         cinfo.addYField(y);
         all.add(y);
      }

      if(multi) {
         cinfo.setMultiStyles(true);
      }

      cinfo.updateChartType(false);

      for(ChartRef ref : all) {
         if(multi && ref instanceof VSChartAggregateRef agg) {
            agg.setRTChartType(agg.getChartType());
         }
      }

      List<ChartRef> dims = new ArrayList<>();
      List<ChartRef> measures = new ArrayList<>();

      for(ChartRef ref : all) {
         (ref instanceof VSChartDimensionRef ? dims : measures).add(ref);
      }

      List<ChartRef> cols = new ArrayList<>(dims);
      cols.addAll(measures);
      List<Object[]> rows = new ArrayList<>();
      rows.add(cols.stream().map(GraphUtil::getName).toArray());
      List<List<String>> combos = new ArrayList<>();
      combos.add(new ArrayList<>());

      for(ChartRef dim : dims) {
         String[] cats = "Region".equals(GraphUtil.getName(dim)) ? new String[] { "R1", "R2" }
            : new String[] { "S1", "S2", "S3" };
         List<List<String>> next = new ArrayList<>();

         for(List<String> combo : combos) {
            for(String cat : cats) {
               List<String> c = new ArrayList<>(combo);
               c.add(cat);
               next.add(c);
            }
         }

         combos = next;
      }

      int n = 0;

      for(List<String> combo : combos) {
         Object[] row = new Object[cols.size()];
         n++;

         for(int i = 0; i < cols.size(); i++) {
            row[i] = i < combo.size() ? combo.get(i) : 10.0 * n + i;
         }

         rows.add(row);
      }

      return new Chart(cinfo, rows.toArray(new Object[0][]));
   }

   private static Chart ganttChart() {
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
      Object[][] rows = {
         { "Task", GraphUtil.getName(start), GraphUtil.getName(end) },
         { "Plan", new Date(1_700_000_000_000L), new Date(1_700_600_000_000L) },
         { "Build", new Date(1_700_600_000_000L), new Date(1_701_800_000_000L) },
      };

      return new Chart(gantt, rows);
   }

   private static GraphGenerator generator(Chart chart, VizMark mark) {
      return generator(chart, mark, ds -> {});
   }

   private static GraphGenerator generator(Chart chart, VizMark mark,
                                           java.util.function.Consumer<DefaultDataSet> fix)
   {
      DefaultDataSet all = new DefaultDataSet(chart.rows());
      fix.accept(all);
      return generator(chart, mark, all, null);
   }

   private static GraphGenerator generator(Chart chart, VizMark mark, Object[][] brushed) {
      return generator(chart, mark, new DefaultDataSet(chart.rows()),
                       new DefaultDataSet(brushed));
   }

   private static GraphGenerator generator(Chart chart, VizMark mark, DataSet all,
                                           DataSet brushed)
   {
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(chart.info());
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);
      Dimension size = new Dimension(400, 240);

      return brushed == null
         ? GraphGenerator.getGenerator(assemblyInfo, null, all, new VariableTable(), null,
                                       XSourceInfo.NONE, size)
         : GraphGenerator.getGenerator(assemblyInfo, all, brushed, new VariableTable(), null,
                                       XSourceInfo.NONE, size);
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
