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

import static org.junit.jupiter.api.Assertions.*;

/**
 * An author's stored resize percent folds into the unit ratios only when the plot follows it.
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

   // a fitted relation chart reports a min size of 1, so the initial ratio is the plot size
   private static final double INITIAL_W = 376;
   private static final double INITIAL_H = 190;
   private static final double PERCENT = 1.5;

   @Test
   void aFittedRelationChartIgnoresAStalePercent() {
      for(int type : new int[] { GraphTypes.CHART_CIRCULAR, GraphTypes.CHART_NETWORK,
                                 GraphTypes.CHART_TREE })
      {
         Chart chart = relation(type, VizMark.MODERN_LIGHT, false);
         assertTrue(chart.relation().isPlotFit(), "premise: fitted, type " + type);

         VGraphPair.applyResizePercent(chart.info(), chart.graph(), INITIAL_W, INITIAL_H);

         assertEquals(1, chart.info().getUnitWidthRatio(), 1e-9, "width, type " + type);
         assertEquals(1, chart.info().getUnitHeightRatio(), 1e-9, "height, type " + type);
      }
   }

   @Test
   void aClassicCircleAppliesThePercent() {
      Chart chart = relation(GraphTypes.CHART_CIRCULAR, null, false);
      assertFalse(chart.relation().isPlotFit(), "premise: classic");

      assertApplied(chart);
   }

   @Test
   void aResizedMarkedCircleAppliesThePercent() {
      Chart chart = relation(GraphTypes.CHART_CIRCULAR, VizMark.MODERN_LIGHT, true);
      assertFalse(chart.relation().isPlotFit(), "premise: an author's resize");

      assertApplied(chart);
   }

   @Test
   void aBarChartAppliesThePercent() {
      for(VizMark mark : new VizMark[] { null, VizMark.MODERN_LIGHT }) {
         VSChartDimensionRef state = dim("State");
         VSChartAggregateRef total = new VSChartAggregateRef();
         total.setDataRef(new BaseField("Total"));
         VSChartInfo cinfo = new DefaultVSChartInfo();
         cinfo.setChartType(GraphTypes.CHART_BAR);
         cinfo.setRTChartType(GraphTypes.CHART_BAR);
         cinfo.addXField(state);
         cinfo.addYField(total);
         cinfo.updateChartType(false);
         Object[][] rows = { { "State", "Total" }, { "CA", 200.0 }, { "NY", 3000.0 } };
         Chart chart = generate(cinfo, mark, rows);

         assertApplied(chart);
      }
   }

   private static void assertApplied(Chart chart) {
      String name = chart.info().getClass().getSimpleName();
      VGraphPair.applyResizePercent(chart.info(), chart.graph(), INITIAL_W, INITIAL_H);

      assertEquals(INITIAL_W * PERCENT, chart.info().getUnitWidthRatio(), 1e-9, "width, " + name);
      assertEquals(INITIAL_H * PERCENT, chart.info().getUnitHeightRatio(), 1e-9,
                   "height, " + name);
   }

   private static Chart relation(int chartType, VizMark mark, boolean resized) {
      VSChartDimensionRef source = dim("Parent");
      VSChartDimensionRef target = dim("Child");
      RelationVSChartInfo cinfo = new RelationVSChartInfo();
      cinfo.setChartType(chartType);
      cinfo.setRTChartType(chartType);
      cinfo.setSourceField(source);
      cinfo.setTargetField(target);
      cinfo.setRTSourceField(source);
      cinfo.setRTTargetField(target);
      cinfo.setWidthResized(resized);
      cinfo.setHeightResized(resized);
      cinfo.updateChartType(false);
      Object[][] rows = {
         { "Parent", "Child" }, { "R", "A" }, { "R", "B" }, { "A", "C" }, { "A", "D" } };
      return generate(cinfo, mark, rows);
   }

   // a stored percent from an earlier enlarge, with the size since reset
   private static Chart generate(VSChartInfo cinfo, VizMark mark, Object[][] rows) {
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);
      EGraph graph = GraphGenerator.getGenerator(
         assemblyInfo, null, new DefaultDataSet(rows), new VariableTable(), null,
         XSourceInfo.NONE, null).createEGraph();

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
