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
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.RelationElement;
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

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An author's stored resize percent folds into an axis's unit ratio only while that axis is
 * resized, so a percent Reset Size left behind is never applied. A relation chart folds it
 * against the initial ratio 1 it reports, so its size holds from draw to draw.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VGraphPairResizePercentTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   // the initial ratios a draw measured: the plot size over the min size
   private static final double INITIAL_W = 376;
   private static final double INITIAL_H = 190;
   private static final double PERCENT = 1.5;

   private static final int[] RELATIONS = {
      GraphTypes.CHART_CIRCULAR, GraphTypes.CHART_NETWORK, GraphTypes.CHART_TREE };

   @Test
   void aFittedRelationChartIgnoresAStalePercent() {
      for(int type : RELATIONS) {
         Chart chart = relation(type, VizMark.MODERN_LIGHT, false);
         assertTrue(chart.relation().isPlotFit(), "premise: fitted, type " + type);

         assertIgnored(chart, "type " + type);
      }
   }

   @Test
   void aClassicRelationChartIgnoresAStalePercent() {
      for(int type : RELATIONS) {
         Chart chart = relation(type, null, false);
         assertFalse(chart.relation().isPlotFit(), "premise: classic, type " + type);

         assertIgnored(chart, "type " + type);
      }
   }

   @Test
   void aResizedClassicRelationChartAppliesThePercent() {
      for(int type : RELATIONS) {
         assertApplied(relation(type, null, true), 1, 1);
      }
   }

   @Test
   void aResizedMarkedCircleAppliesThePercent() {
      Chart chart = relation(GraphTypes.CHART_CIRCULAR, VizMark.MODERN_LIGHT, true);
      assertFalse(chart.relation().isPlotFit(), "premise: an author's resize");

      assertApplied(chart, 1, 1);
   }

   @Test
   void aBarChartIgnoresAStalePercent() {
      for(VizMark mark : new VizMark[] { null, VizMark.MODERN_LIGHT }) {
         assertIgnored(bar(mark, false, false), "mark " + mark);
      }
   }

   @Test
   void aResizedBarChartAppliesThePercent() {
      for(VizMark mark : new VizMark[] { null, VizMark.MODERN_LIGHT }) {
         assertApplied(bar(mark, true, true), INITIAL_W, INITIAL_H);
      }
   }

   @Test
   void aPercentAppliesOnlyToItsResizedAxis() {
      Chart width = bar(null, true, false);
      VGraphPair.applyResizePercent(width.info(), width.graph(), INITIAL_W, INITIAL_H);

      assertEquals(INITIAL_W * PERCENT, width.info().getUnitWidthRatio(), 1e-9, "width resized");
      assertEquals(1, width.info().getUnitHeightRatio(), 0, "height not resized");

      Chart height = bar(null, false, true);
      VGraphPair.applyResizePercent(height.info(), height.graph(), INITIAL_W, INITIAL_H);

      assertEquals(1, height.info().getUnitWidthRatio(), 0, "width not resized");
      assertEquals(INITIAL_H * PERCENT, height.info().getUnitHeightRatio(), 1e-9,
                   "height resized");
   }

   @Test
   void aRelationElementTakesTheUnitRatioOfAResizedAxisOnly() {
      boolean[][] flags = { { false, false }, { true, false }, { false, true }, { true, true } };

      for(int type : RELATIONS) {
         for(VizMark mark : new VizMark[] { null, VizMark.MODERN_LIGHT }) {
            for(boolean[] resized : flags) {
               // unit ratios a stale percent folded in before the chart was saved
               Chart chart = relationWithRatios(type, mark, resized[0], resized[1], 2.5, 1.5);
               RelationElement elem = chart.relation();
               String msg = "type " + type + ", mark " + mark + ", resized " +
                  resized[0] + "/" + resized[1];

               assertEquals(resized[0] ? 2.5 : 1, elem.getWidthRatio(), 0, "width, " + msg);
               assertEquals(resized[1] ? 1.5 : 1, elem.getHeightRatio(), 0, "height, " + msg);

               // the slider starts from the stored ratio, so the fold drops a stale one
               VGraphPair.applyResizePercent(chart.info(), chart.graph(), INITIAL_W, INITIAL_H);

               assertEquals(resized[0] ? 2.5 : 1, chart.info().getUnitWidthRatio(), 0,
                            "stored width, " + msg);
               assertEquals(resized[1] ? 1.5 : 1, chart.info().getUnitHeightRatio(), 0,
                            "stored height, " + msg);
            }
         }
      }
   }

   @Test
   void aResizedRelationChartKeepsItsSizeFromDrawToDraw() {
      // type, width resized, height resized: a circle's resize is square
      int[][] cases = {
         { GraphTypes.CHART_NETWORK, 1, 0 }, { GraphTypes.CHART_NETWORK, 0, 1 },
         { GraphTypes.CHART_TREE, 1, 0 }, { GraphTypes.CHART_TREE, 0, 1 },
         { GraphTypes.CHART_CIRCULAR, 1, 1 } };

      for(VizMark mark : new VizMark[] { null, VizMark.MODERN_LIGHT }) {
         for(int[] c : cases) {
            boolean width = c[1] == 1;
            boolean height = c[2] == 1;
            RelationVSChartInfo cinfo = relationInfo(c[0], width, height);
            double w = width ? PERCENT : 1;
            double h = height ? PERCENT : 1;
            // an author's x1.5 enlarge: a relation chart's slider starts from the ratio 1
            cinfo.setUnitWidthRatio(w);
            cinfo.setUnitHeightRatio(h);
            cinfo.setUnitWidthRatioPercent(width ? PERCENT : 0);
            cinfo.setUnitHeightRatioPercent(height ? PERCENT : 0);

            for(int draw = 0; draw < 4; draw++) {
               String msg = "type " + c[0] + ", mark " + mark + ", resized " + width + "/" +
                  height + ", draw " + draw;
               RelationElement elem = drawAndFold(cinfo, mark);

               assertFalse(elem.isPlotFit(), "premise: an author's resize, " + msg);
               assertEquals(w, elem.getWidthRatio(), 1e-9, "drawn width, " + msg);
               assertEquals(h, elem.getHeightRatio(), 1e-9, "drawn height, " + msg);
               assertEquals(w, cinfo.getUnitWidthRatio(), 1e-9, "stored width, " + msg);
               assertEquals(h, cinfo.getUnitHeightRatio(), 1e-9, "stored height, " + msg);
            }
         }
      }
   }

   @Test
   void aSavedResizedRelationChartDrawsAtItsPercentFromTheFirstDraw() {
      for(VizMark mark : new VizMark[] { null, VizMark.MODERN_LIGHT }) {
         for(int type : RELATIONS) {
            RelationVSChartInfo cinfo = relationInfo(type, true, true);
            // ratios an earlier fold stored against the plot size before the chart was saved
            cinfo.setUnitWidthRatio(3.584905660377358);
            cinfo.setUnitHeightRatio(0.4014);
            cinfo.setUnitWidthRatioPercent(PERCENT);
            cinfo.setUnitHeightRatioPercent(PERCENT);

            for(int draw = 0; draw < 3; draw++) {
               String msg = "type " + type + ", mark " + mark + ", draw " + draw;
               RelationElement elem = drawAndFold(cinfo, mark);

               assertEquals(PERCENT, elem.getWidthRatio(), 1e-9, "drawn width, " + msg);
               assertEquals(PERCENT, elem.getHeightRatio(), 1e-9, "drawn height, " + msg);
               assertEquals(PERCENT, cinfo.getUnitWidthRatio(), 1e-9, "stored width, " + msg);
               assertEquals(PERCENT, cinfo.getUnitHeightRatio(), 1e-9, "stored height, " + msg);
            }
         }
      }
   }

   private static void assertApplied(Chart chart, double initialW, double initialH) {
      String name = chart.info().getClass().getSimpleName();
      VGraphPair.applyResizePercent(chart.info(), chart.graph(), INITIAL_W, INITIAL_H);

      assertEquals(initialW * PERCENT, chart.info().getUnitWidthRatio(), 1e-9, "width, " + name);
      assertEquals(initialH * PERCENT, chart.info().getUnitHeightRatio(), 1e-9,
                   "height, " + name);
   }

   // one draw as VGraphPair makes it: generate, lay out in the card, then fold the percent
   private static RelationElement drawAndFold(VSChartInfo cinfo, VizMark mark) {
      DefaultDataSet data = new DefaultDataSet(NETWORK_ROWS);
      EGraph egraph = createEGraph(cinfo, mark, data);
      VGraph vgraph = Plotter.getPlotter(egraph).plotAndLayout(data, 0, 0, 382, 194);
      assertNotNull(vgraph);
      Rectangle2D plot = vgraph.getPlotBounds();

      VGraphPair.applyResizePercent(
         cinfo, egraph, plot.getWidth() / Math.min(vgraph.getMinPlotWidth(), 1000000),
         plot.getHeight() / Math.min(vgraph.getMinPlotHeight(), 1000000));
      return (RelationElement) egraph.getElement(0);
   }

   private static void assertIgnored(Chart chart, String message) {
      VGraphPair.applyResizePercent(chart.info(), chart.graph(), INITIAL_W, INITIAL_H);

      assertEquals(1, chart.info().getUnitWidthRatio(), 0, "width, " + message);
      assertEquals(1, chart.info().getUnitHeightRatio(), 0, "height, " + message);
   }

   private static Chart bar(VizMark mark, boolean widthResized, boolean heightResized) {
      VSChartDimensionRef state = dim("State");
      VSChartAggregateRef total = new VSChartAggregateRef();
      total.setDataRef(new BaseField("Total"));
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.setRTChartType(GraphTypes.CHART_BAR);
      cinfo.addXField(state);
      cinfo.addYField(total);
      cinfo.setWidthResized(widthResized);
      cinfo.setHeightResized(heightResized);
      cinfo.updateChartType(false);
      Object[][] rows = { { "State", "Total" }, { "CA", 200.0 }, { "NY", 3000.0 } };
      return generate(cinfo, mark, rows);
   }

   private static Chart relation(int chartType, VizMark mark, boolean resized) {
      RelationVSChartInfo cinfo = relationInfo(chartType, resized, resized);
      return generate(cinfo, mark, RELATION_ROWS);
   }

   private static Chart relationWithRatios(int chartType, VizMark mark, boolean widthResized,
                                           boolean heightResized, double widthRatio,
                                           double heightRatio)
   {
      RelationVSChartInfo cinfo = relationInfo(chartType, widthResized, heightResized);
      cinfo.setUnitWidthRatio(widthRatio);
      cinfo.setUnitHeightRatio(heightRatio);
      return new Chart(cinfo, createEGraph(cinfo, mark, new DefaultDataSet(RELATION_ROWS)));
   }

   private static RelationVSChartInfo relationInfo(int chartType, boolean widthResized,
                                                   boolean heightResized)
   {
      VSChartDimensionRef source = dim("Parent");
      VSChartDimensionRef target = dim("Child");
      RelationVSChartInfo cinfo = new RelationVSChartInfo();
      cinfo.setChartType(chartType);
      cinfo.setRTChartType(chartType);
      cinfo.setSourceField(source);
      cinfo.setTargetField(target);
      cinfo.setRTSourceField(source);
      cinfo.setRTTargetField(target);
      cinfo.setWidthResized(widthResized);
      cinfo.setHeightResized(heightResized);
      cinfo.updateChartType(false);
      return cinfo;
   }

   private static final Object[][] RELATION_ROWS = {
      { "Parent", "Child" }, { "R", "A" }, { "R", "B" }, { "A", "C" }, { "A", "D" } };

   private static final Object[][] NETWORK_ROWS = networkRows();

   // 30 nodes in clusters of five (a hub with four leaves), the hubs chained
   private static Object[][] networkRows() {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "Parent", "Child" });

      for(int i = 1; i < 30; i++) {
         int from = i % 5 != 0 ? i / 5 * 5 : i - 5;
         rows.add(new Object[] { "N" + from, "N" + i });
      }

      return rows.toArray(new Object[0][]);
   }

   private static EGraph createEGraph(VSChartInfo cinfo, VizMark mark, DefaultDataSet data) {
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);
      return GraphGenerator.getGenerator(
         assemblyInfo, null, data, new VariableTable(), null, XSourceInfo.NONE, null)
         .createEGraph();
   }

   // a stored percent from an earlier enlarge, with the size since reset
   private static Chart generate(VSChartInfo cinfo, VizMark mark, Object[][] rows) {
      EGraph graph = createEGraph(cinfo, mark, new DefaultDataSet(rows));

      cinfo.setUnitWidthRatio(1);
      cinfo.setUnitHeightRatio(1);
      cinfo.setUnitWidthRatioPercent(PERCENT);
      cinfo.setUnitHeightRatioPercent(PERCENT);
      return new Chart(cinfo, graph);
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField(name));
      dim.setGroupColumnValue(name);
      return dim;
   }

   private record Chart(VSChartInfo info, EGraph graph) {
      RelationElement relation() {
         assertTrue(graph.getElementCount() > 0, "premise: an element");
         return (RelationElement) graph.getElement(0);
      }
   }
}
