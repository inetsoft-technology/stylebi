/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.graph.model.dialog;

import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SreeHome()
class ChartPlotOptionsPaneModelTest {

   private ChartPlotOptionsPaneModel modelFor(int chartType) {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(chartType);
      PlotDescriptor plotDesc = new PlotDescriptor();
      return new ChartPlotOptionsPaneModel(info, plotDesc);
   }

   // --- barCornerRadiusVisible ---

   @Test
   void barCornerRadiusVisible_barChart() {
      assertTrue(modelFor(GraphTypes.CHART_BAR).isBarCornerRadiusVisible());
   }

   @Test
   void barCornerRadiusVisible_intervalChart() {
      assertTrue(modelFor(GraphTypes.CHART_INTERVAL).isBarCornerRadiusVisible());
   }

   @Test
   void barCornerRadiusVisible_stackedBarChart() {
      assertTrue(modelFor(GraphTypes.CHART_BAR_STACK).isBarCornerRadiusVisible());
   }

   @Test
   void barCornerRadiusVisible_lineChart() {
      assertFalse(modelFor(GraphTypes.CHART_LINE).isBarCornerRadiusVisible());
   }

   @Test
   void barCornerRadiusVisible_paretoChart() {
      assertTrue(modelFor(GraphTypes.CHART_PARETO).isBarCornerRadiusVisible());
   }

   @Test
   void barCornerRadiusVisible_waterfallChart() {
      assertTrue(modelFor(GraphTypes.CHART_WATERFALL).isBarCornerRadiusVisible());
   }

   // --- barRoundAllCornersVisible ---

   @Test
   void barRoundAllCornersVisible_barChart() {
      assertTrue(modelFor(GraphTypes.CHART_BAR).isBarRoundAllCornersVisible());
   }

   @Test
   void barRoundAllCornersVisible_intervalChart() {
      // Interval charts always round all corners in the rendering engine;
      // the checkbox must be hidden to avoid giving the impression of user control.
      assertFalse(modelFor(GraphTypes.CHART_INTERVAL).isBarRoundAllCornersVisible());
   }

   @Test
   void barRoundAllCornersVisible_stackedBarChart() {
      assertTrue(modelFor(GraphTypes.CHART_BAR_STACK).isBarRoundAllCornersVisible());
   }

   @Test
   void barRoundAllCornersVisible_paretoChart() {
      assertTrue(modelFor(GraphTypes.CHART_PARETO).isBarRoundAllCornersVisible());
   }

   @Test
   void barRoundAllCornersVisible_waterfallChart() {
      // Waterfall always rounds all corners (each segment floats with no zero baseline);
      // the checkbox is hidden, same pattern as interval.
      assertFalse(modelFor(GraphTypes.CHART_WATERFALL).isBarRoundAllCornersVisible());
   }

   // --- updateChartPlotOptionsPaneModel: barRoundAllCorners save guard ---

   @Test
   void updateModel_setsBarRoundAllCornersTrueForIntervalChart() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_INTERVAL);
      PlotDescriptor plotDesc = new PlotDescriptor();
      // Stale descriptor value of false (e.g. created before interval support was added)
      plotDesc.setBarRoundAllCorners(false);

      ChartPlotOptionsPaneModel model = new ChartPlotOptionsPaneModel(info, plotDesc);
      // Simulate the frontend sending barRoundAllCorners=false (checkbox is hidden)
      model.setBarRoundAllCorners(false);
      model.updateChartPlotOptionsPaneModel(info, plotDesc);

      assertTrue(plotDesc.isBarRoundAllCorners(),
         "barRoundAllCorners should be forced true for interval charts to match GraphGenerator");
   }

   @Test
   void updateModel_savesBarRoundAllCornersForBarChart() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      PlotDescriptor plotDesc = new PlotDescriptor();
      plotDesc.setBarRoundAllCorners(false);

      ChartPlotOptionsPaneModel model = new ChartPlotOptionsPaneModel(info, plotDesc);
      model.setBarRoundAllCorners(true);
      model.updateChartPlotOptionsPaneModel(info, plotDesc);

      assertTrue(plotDesc.isBarRoundAllCorners(),
         "barRoundAllCorners should be persisted for bar charts");
   }

   @Test
   void updateModel_savesBarRoundAllCornersFalseForBarChart() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      PlotDescriptor plotDesc = new PlotDescriptor();
      plotDesc.setBarRoundAllCorners(true);

      ChartPlotOptionsPaneModel model = new ChartPlotOptionsPaneModel(info, plotDesc);
      model.setBarRoundAllCorners(false);
      model.updateChartPlotOptionsPaneModel(info, plotDesc);

      assertFalse(plotDesc.isBarRoundAllCorners(),
         "barRoundAllCorners=false should be persisted for bar charts");
   }

   @Test
   void updateModel_savesBarRoundAllCornersForStackedBarChart() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR_STACK);
      PlotDescriptor plotDesc = new PlotDescriptor();
      plotDesc.setBarRoundAllCorners(false);

      ChartPlotOptionsPaneModel model = new ChartPlotOptionsPaneModel(info, plotDesc);
      model.setBarRoundAllCorners(true);
      model.updateChartPlotOptionsPaneModel(info, plotDesc);

      assertTrue(plotDesc.isBarRoundAllCorners(),
         "barRoundAllCorners should be persisted for stacked bar charts");
   }

   @Test
   void updateModel_savesBarRoundAllCornersFalseForStackedBarChart() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR_STACK);
      PlotDescriptor plotDesc = new PlotDescriptor();
      plotDesc.setBarRoundAllCorners(true);

      ChartPlotOptionsPaneModel model = new ChartPlotOptionsPaneModel(info, plotDesc);
      model.setBarRoundAllCorners(false);
      model.updateChartPlotOptionsPaneModel(info, plotDesc);

      assertFalse(plotDesc.isBarRoundAllCorners(),
         "barRoundAllCorners=false should be persisted for stacked bar charts");
   }

   @Test
   void updateModel_setsBarRoundAllCornersTrueForWaterfallChart() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_WATERFALL);
      PlotDescriptor plotDesc = new PlotDescriptor();
      // Stale descriptor value of false (e.g. created before waterfall rounding was added)
      plotDesc.setBarRoundAllCorners(false);

      ChartPlotOptionsPaneModel model = new ChartPlotOptionsPaneModel(info, plotDesc);
      // Frontend sends barRoundAllCorners=false because the checkbox is hidden
      model.setBarRoundAllCorners(false);
      model.updateChartPlotOptionsPaneModel(info, plotDesc);

      assertTrue(plotDesc.isBarRoundAllCorners(),
         "barRoundAllCorners should be forced true for waterfall charts to match GraphGenerator");
   }

   @Test
   void updateModel_savesBarRoundAllCornersForParetoChart() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_PARETO);
      PlotDescriptor plotDesc = new PlotDescriptor();
      plotDesc.setBarRoundAllCorners(false);

      ChartPlotOptionsPaneModel model = new ChartPlotOptionsPaneModel(info, plotDesc);
      model.setBarRoundAllCorners(true);
      model.updateChartPlotOptionsPaneModel(info, plotDesc);

      assertTrue(plotDesc.isBarRoundAllCorners(),
         "barRoundAllCorners should be persisted for pareto charts");
   }

   @Test
   void updateModel_savesBarRoundAllCornersFalseForParetoChart() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_PARETO);
      PlotDescriptor plotDesc = new PlotDescriptor();
      plotDesc.setBarRoundAllCorners(true);

      ChartPlotOptionsPaneModel model = new ChartPlotOptionsPaneModel(info, plotDesc);
      model.setBarRoundAllCorners(false);
      model.updateChartPlotOptionsPaneModel(info, plotDesc);

      assertFalse(plotDesc.isBarRoundAllCorners(),
         "barRoundAllCorners=false should be persisted for pareto charts");
   }

   @Test
   void updateModel_resetsBarRoundAllCornersFalseForLineChart() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_LINE);
      PlotDescriptor plotDesc = new PlotDescriptor();
      // Stale descriptor value left from when the chart was a different type
      plotDesc.setBarRoundAllCorners(true);

      ChartPlotOptionsPaneModel model = new ChartPlotOptionsPaneModel(info, plotDesc);
      model.updateChartPlotOptionsPaneModel(info, plotDesc);

      assertFalse(plotDesc.isBarRoundAllCorners(),
         "barRoundAllCorners should be reset to false for chart types where rounding doesn't apply");
   }

   // --- smoothLinesVisible ---

   @Test
   void smoothLinesVisible_areaChart() {
      assertTrue(modelFor(GraphTypes.CHART_AREA).isSmoothLinesVisible());
   }

   @Test
   void smoothLinesVisible_stackedAreaChart() {
      assertTrue(modelFor(GraphTypes.CHART_AREA_STACK).isSmoothLinesVisible());
   }

   @Test
   void smoothLinesVisible_lineChart() {
      assertTrue(modelFor(GraphTypes.CHART_LINE).isSmoothLinesVisible());
   }

   @Test
   void smoothLinesVisible_stackedLineChart() {
      assertTrue(modelFor(GraphTypes.CHART_LINE_STACK).isSmoothLinesVisible());
   }

   @Test
   void smoothLinesVisible_stepAreaChartHidden() {
      // Step variants render as discrete rectangles; smooth would conflict with step semantics.
      assertFalse(modelFor(GraphTypes.CHART_STEP_AREA).isSmoothLinesVisible());
   }

   @Test
   void smoothLinesVisible_stepStackedAreaChartHidden() {
      assertFalse(modelFor(GraphTypes.CHART_STEP_AREA_STACK).isSmoothLinesVisible());
   }

   @Test
   void smoothLinesVisible_stepLineChartHidden() {
      assertFalse(modelFor(GraphTypes.CHART_STEP).isSmoothLinesVisible());
   }

   @Test
   void smoothLinesVisible_stepStackedLineChartHidden() {
      assertFalse(modelFor(GraphTypes.CHART_STEP_STACK).isSmoothLinesVisible());
   }

   @Test
   void smoothLinesVisible_jumpLineChartHidden() {
      assertFalse(modelFor(GraphTypes.CHART_JUMP).isSmoothLinesVisible());
   }

   @Test
   void smoothLinesVisible_barChartHidden() {
      assertFalse(modelFor(GraphTypes.CHART_BAR).isSmoothLinesVisible());
   }

   @Test
   void smoothLinesVisible_pieChartHidden() {
      assertFalse(modelFor(GraphTypes.CHART_PIE).isSmoothLinesVisible());
   }

   // --- treeLayoutVisible ---

   @Test
   void treeLayoutVisible_treeChart() {
      assertTrue(modelFor(GraphTypes.CHART_TREE).isTreeLayoutVisible());
   }

   @Test
   void treeLayoutVisible_networkChartHidden() {
      // Network charts use ORGANIC layout; orientation is not a meaningful concept.
      assertFalse(modelFor(GraphTypes.CHART_NETWORK).isTreeLayoutVisible());
   }

   @Test
   void treeLayoutVisible_circularChartHidden() {
      // Circular charts use CIRCLE layout; orientation is not a meaningful concept.
      assertFalse(modelFor(GraphTypes.CHART_CIRCULAR).isTreeLayoutVisible());
   }

   @Test
   void treeLayoutVisible_barChartHidden() {
      assertFalse(modelFor(GraphTypes.CHART_BAR).isTreeLayoutVisible());
   }
}
