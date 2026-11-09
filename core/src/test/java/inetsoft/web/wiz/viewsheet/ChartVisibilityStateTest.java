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
package inetsoft.web.wiz.viewsheet;

import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80331: a hidden legend, axis or axis title read back as absent. These build real
 * descriptors and chart info, because the decision rule (bound aesthetics only, NO_LEGEND layout,
 * descriptor state) is where the defect lived.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartVisibilityStateTest {
   // ── legends ───────────────────────────────────────────────────────────────

   @Test
   void aVisibleBoundLegendIsNotHidden() {
      ChartDescriptor desc = new ChartDescriptor();
      VSChartInfo info = colorBound();

      assertFalse(ChartVisibilityState.hiddenLegends(desc, info, false).any());
   }

   @Test
   void aBoundLegendWhoseDescriptorIsOffIsHiddenAndNamed() {
      ChartDescriptor desc = new ChartDescriptor();
      desc.getLegendsDescriptor().getColorLegendDescriptor().setVisible(false);

      ChartVisibilityState.HiddenLegends hidden =
         ChartVisibilityState.hiddenLegends(desc, colorBound(), false);

      assertEquals(List.of(new ChartVisibilityState.HiddenLegend("color", "Region")),
                   hidden.hidden());
      assertEquals("color [Region]", hidden.describe());
   }

   /**
    * Hiding every legend writes every descriptor, including the channels with nothing bound, so a
    * color-only chart must report only color - not shape or size.
    */
   @Test
   void hideAllOnAColorOnlyChartReportsOnlyColor() {
      ChartDescriptor desc = new ChartDescriptor();
      LegendsDescriptor legends = desc.getLegendsDescriptor();
      legends.getColorLegendDescriptor().setVisible(false);
      legends.getShapeLegendDescriptor().setVisible(false);
      legends.getSizeLegendDescriptor().setVisible(false);

      List<ChartVisibilityState.HiddenLegend> hidden =
         ChartVisibilityState.hiddenLegends(desc, colorBound(), false).hidden();

      assertEquals(1, hidden.size());
      assertEquals("color", hidden.get(0).channel());
   }

   @Test
   void aNonBoundChannelWithAnOffDescriptorIsNotHidden() {
      ChartDescriptor desc = new ChartDescriptor();
      desc.getLegendsDescriptor().getSizeLegendDescriptor().setVisible(false);

      assertFalse(ChartVisibilityState.hiddenLegends(desc, colorBound(), false).any());
   }

   /** The legend layout set to none hides every bound legend although the descriptors are on. */
   @Test
   void theNoLegendLayoutHidesTheBoundLegends() {
      ChartDescriptor desc = new ChartDescriptor();
      desc.getLegendsDescriptor().setLayout(LegendsDescriptor.NO_LEGEND);

      ChartVisibilityState.HiddenLegends hidden =
         ChartVisibilityState.hiddenLegends(desc, colorBound(), false);

      assertTrue(hidden.any());
      assertEquals("color", hidden.hidden().get(0).channel());
   }

   @Test
   void theNoLegendLayoutOnAChartWithNothingBoundHidesNothing() {
      ChartDescriptor desc = new ChartDescriptor();
      desc.getLegendsDescriptor().setLayout(LegendsDescriptor.NO_LEGEND);

      assertFalse(ChartVisibilityState.hiddenLegends(
         desc, new DefaultVSChartInfo(), false).any());
   }

   @Test
   void maxModeReadsTheMaxModeTwinFlag() {
      ChartDescriptor desc = new ChartDescriptor();
      desc.getLegendsDescriptor().getColorLegendDescriptor().setMaxModeVisible(false);
      VSChartInfo info = colorBound();

      assertFalse(ChartVisibilityState.hiddenLegends(desc, info, false).any());
      assertTrue(ChartVisibilityState.hiddenLegends(desc, info, true).any());
   }

   // ── axis titles ───────────────────────────────────────────────────────────

   @Test
   void reportsWhichAxisTitlesAreShown() {
      ChartDescriptor desc = new ChartDescriptor();
      desc.getTitlesDescriptor().getYTitleDescriptor().setVisible(false);

      Map<String, Boolean> titles =
         ChartVisibilityState.titleVisibility(desc, List.of("x", "y"), false);

      assertEquals(Boolean.TRUE, titles.get("x"));
      assertEquals(Boolean.FALSE, titles.get("y"));
      assertFalse(titles.containsKey("chart"), "the chart title is read with titleVisible");
   }

   // ── axes ──────────────────────────────────────────────────────────────────

   @Test
   void axisIsVisibleHiddenOrPartial() {
      VSChartInfo info = axisChart();
      assertEquals("visible", ChartVisibilityState.axisVisibility(
         info, List.of("x"), false).get("x"));

      setAxes(info, true, false);
      assertEquals("partial", ChartVisibilityState.axisVisibility(
         info, List.of("x"), false).get("x"));

      setAxes(info, false, false);
      assertEquals("hidden", ChartVisibilityState.axisVisibility(
         info, List.of("x"), false).get("x"));
   }

   // ── relation charts (Bug #80822) ──────────────────────────────────────────

   private static RelationVSChartInfo relation(boolean tree, boolean edgeSize,
                                               boolean nodeSize)
   {
      RelationVSChartInfo info = new RelationVSChartInfo();

      if(tree) {
         info.setChartType(GraphTypes.CHART_TREE);
      }

      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(dimension("Edge"));
      info.setColorField(color);
      VSAestheticRef nodeColor = new VSAestheticRef();
      nodeColor.setDataRef(dimension("Node"));
      info.setNodeColorField(nodeColor);

      if(edgeSize) {
         VSAestheticRef size = new VSAestheticRef();
         size.setDataRef(dimension("EdgeSz"));
         info.setSizeField(size);
      }

      if(nodeSize) {
         VSAestheticRef size = new VSAestheticRef();
         size.setDataRef(dimension("NodeSz"));
         info.setNodeSizeField(size);
      }

      return info;
   }

   private static List<ChartVisibilityState.HiddenLegend> hidden(RelationVSChartInfo info,
                                                                  ChartDescriptor desc,
                                                                  boolean maxMode)
   {
      return ChartVisibilityState.hiddenLegends(desc, info, maxMode).hidden();
   }

   @Test
   void relationEdgeColorHiddenThroughItsOwnDescriptorIsReported() {
      RelationVSChartInfo info = relation(false, false, false);
      info.getColorField().getLegendDescriptor().setVisible(false);

      ChartVisibilityState.HiddenLegends h =
         ChartVisibilityState.hiddenLegends(new ChartDescriptor(), info, false);

      assertTrue(h.any());
      assertEquals(List.of(new ChartVisibilityState.HiddenLegend("color", "Edge")), h.hidden());
   }

   @Test
   void relationHideAllReportsEdgeAndNode() {
      RelationVSChartInfo info = relation(false, true, true);
      ChartDescriptor desc = new ChartDescriptor();
      desc.getLegendsDescriptor().getColorLegendDescriptor().setVisible(false);
      desc.getLegendsDescriptor().getSizeLegendDescriptor().setVisible(false);
      info.getColorField().getLegendDescriptor().setVisible(false);
      info.getSizeField().getLegendDescriptor().setVisible(false);
      info.getNodeColorField().getLegendDescriptor().setVisible(false);
      info.getNodeSizeField().getLegendDescriptor().setVisible(false);

      assertEquals(List.of(new ChartVisibilityState.HiddenLegend("color", "Edge"),
                           new ChartVisibilityState.HiddenLegend("size", "EdgeSz"),
                           new ChartVisibilityState.HiddenLegend("node-color", "Node"),
                           new ChartVisibilityState.HiddenLegend("node-size", "NodeSz")),
                   hidden(info, desc, false));
   }

   @Test
   void relationShowAllReportsNothing() {
      assertTrue(hidden(relation(false, true, true), new ChartDescriptor(), false).isEmpty());
   }

   @Test
   void relationNodeColorHiddenOnlyReportsNodeColor() {
      RelationVSChartInfo info = relation(false, false, false);
      info.getNodeColorField().getLegendDescriptor().setVisible(false);

      assertEquals(List.of(new ChartVisibilityState.HiddenLegend("node-color", "Node")),
                   hidden(info, new ChartDescriptor(), false));
   }

   @Test
   void relationEdgeSizeHiddenIsReported() {
      RelationVSChartInfo info = relation(false, true, false);
      info.getSizeField().getLegendDescriptor().setVisible(false);

      assertEquals(List.of(new ChartVisibilityState.HiddenLegend("size", "EdgeSz")),
                   hidden(info, new ChartDescriptor(), false));
   }

   @Test
   void treeEdgeColorHiddenIsReported() {
      RelationVSChartInfo info = relation(true, false, false);
      info.getColorField().getLegendDescriptor().setVisible(false);

      assertEquals(List.of(new ChartVisibilityState.HiddenLegend("color", "Edge")),
                   hidden(info, new ChartDescriptor(), false));
   }

   @Test
   void relationSharedOffOnlyDoesNotReportEdgeBecauseTheRendererDrawsIt() {
      ChartDescriptor desc = new ChartDescriptor();
      desc.getLegendsDescriptor().getColorLegendDescriptor().setVisible(false);

      assertTrue(hidden(relation(false, false, false), desc, false).isEmpty());
   }

   @Test
   void relationNoLegendLayoutReportsEveryBoundLegend() {
      ChartDescriptor desc = new ChartDescriptor();
      desc.getLegendsDescriptor().setLayout(LegendsDescriptor.NO_LEGEND);

      assertEquals(2, hidden(relation(false, false, false), desc, false).size());
   }

   @Test
   void relationMaxModeReadsTheOwnMaxModeFlag() {
      RelationVSChartInfo info = relation(false, false, false);
      info.getColorField().getLegendDescriptor().setMaxModeVisible(false);

      assertTrue(hidden(info, new ChartDescriptor(), false).isEmpty());
      assertEquals(List.of(new ChartVisibilityState.HiddenLegend("color", "Edge")),
                   hidden(info, new ChartDescriptor(), true));
   }

   @Test
   void regularChartStillReadsTheSharedDescriptor() {
      ChartDescriptor desc = new ChartDescriptor();
      desc.getLegendsDescriptor().getColorLegendDescriptor().setVisible(false);
      VSChartInfo info = colorBound();
      info.getColorField().getLegendDescriptor().setVisible(true);

      assertEquals(List.of(new ChartVisibilityState.HiddenLegend("color", "Region")),
                   ChartVisibilityState.hiddenLegends(desc, info, false).hidden());
   }

   // ── helpers ───────────────────────────────────────────────────────────────

   private static VSChartInfo colorBound() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(dimension("Region"));
      info.setColorField(color);
      return info;
   }

   private static VSChartInfo axisChart() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Region"));
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("Total");
      agg.setFormulaValue("Sum");
      info.addYField(agg);
      return info;
   }

   private static void setAxes(VSChartInfo info, boolean label, boolean line) {
      for(ChartRef ref : info.getXFields()) {
         ref.getAxisDescriptor().setLabelVisible(label);
         ref.getAxisDescriptor().setLineVisible(line);
      }

      info.getAxisDescriptor().setLabelVisible(label);
      info.getAxisDescriptor().setLineVisible(line);
   }

   private static VSChartDimensionRef dimension(String name) {
      DataRef column = new ColumnRef(new AttributeRef(null, name));
      return new VSChartDimensionRef(column);
   }
}
