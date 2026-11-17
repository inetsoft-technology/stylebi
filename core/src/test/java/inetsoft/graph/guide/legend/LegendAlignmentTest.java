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
package inetsoft.graph.guide.legend;

import inetsoft.graph.EGraph;
import inetsoft.graph.GraphConstants;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.CategoricalShapeFrame;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A top or bottom legend row aligns to the plot's horizontal extent, and an unset alignment or a
 * side legend keeps the layout as it was.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LegendAlignmentTest {
   @Test
   void startPutsTheLegendAtThePlotsLeftEdge() {
      VGraph today = chart(GraphConstants.TOP, null, false);
      assertTrue(Math.abs(legend(today, 0).getX() - today.getPlotBounds().getX()) > 5,
                 "premise: today's legend is not at the plot's edge");

      VGraph vgraph = chart(GraphConstants.TOP, GraphConstants.LEFT_ALIGNMENT, false);
      assertEquals(vgraph.getPlotBounds().getX(), legend(vgraph, 0).getX(), 0.5);
   }

   @Test
   void endPutsItAtThePlotsRightEdge() {
      VGraph vgraph = chart(GraphConstants.TOP, GraphConstants.RIGHT_ALIGNMENT, false);
      assertEquals(vgraph.getPlotBounds().getMaxX(), legend(vgraph, 0).getMaxX(), 0.5);
   }

   @Test
   void centreCentresItOnThePlot() {
      VGraph today = chart(GraphConstants.TOP, null, false);
      assertTrue(Math.abs(legend(today, 0).getCenterX() - today.getPlotBounds().getCenterX()) > 5,
                 "premise: today's legend centres on the chart, not the plot");

      VGraph vgraph = chart(GraphConstants.TOP, GraphConstants.CENTER_ALIGNMENT, false);
      assertEquals(vgraph.getPlotBounds().getCenterX(), legend(vgraph, 0).getCenterX(), 0.5);
   }

   @Test
   void twoLegendsSitSideBySideFromThePlotEdge() {
      VGraph vgraph = chart(GraphConstants.TOP, GraphConstants.LEFT_ALIGNMENT, true);
      assertEquals(2, vgraph.getLegendGroup().getLegendCount(), "premise: two legends");
      Rectangle2D plot = vgraph.getPlotBounds();
      Rectangle2D first = legend(vgraph, 0);
      Rectangle2D second = legend(vgraph, 1);

      assertEquals(plot.getX(), first.getX(), 0.5);
      assertEquals(first.getMaxX(), second.getX(), 0.5);
      assertTrue(first.getWidth() + second.getWidth() < plot.getWidth(),
                 "each legend takes its content width, not a share of the row");
   }

   @Test
   void bottomFollowsTheSameRule() {
      VGraph start = chart(GraphConstants.BOTTOM, GraphConstants.LEFT_ALIGNMENT, false);
      assertEquals(start.getPlotBounds().getX(), legend(start, 0).getX(), 0.5);

      VGraph end = chart(GraphConstants.BOTTOM, GraphConstants.RIGHT_ALIGNMENT, false);
      assertEquals(end.getPlotBounds().getMaxX(), legend(end, 0).getMaxX(), 0.5);
   }

   @Test
   void unsetIsToday() {
      for(int layout : new int[] { GraphConstants.TOP, GraphConstants.BOTTOM }) {
         for(boolean two : new boolean[] { false, true }) {
            assertSameLayout(chart(layout, null, two), chart(layout, 0, two));
         }
      }
   }

   @Test
   void aSideLegendIgnoresIt() {
      assertSameLayout(chart(GraphConstants.RIGHT, null, false),
                       chart(GraphConstants.RIGHT, GraphConstants.LEFT_ALIGNMENT, false));
   }

   @Test
   void aWiderRowStartsAtThePlotEdgeAndRunsPastIt() {
      VGraph v = wideRow(GraphConstants.LEFT_ALIGNMENT);
      assertContentWidthRow(v);
      assertEquals(expectedX(v, v.getPlotBounds().getX()), rowMinX(v), 0.5);
      assertTrue(rowMaxX(v) > v.getPlotBounds().getMaxX() - 0.5);
      assertTrue(rowMaxX(v) <= groupMaxX(v) + 0.5);
   }

   @Test
   void aWiderRowEndsAtThePlotEdgeAndExtendsLeft() {
      VGraph v = wideRow(GraphConstants.RIGHT_ALIGNMENT);
      assertContentWidthRow(v);
      assertEquals(expectedX(v, v.getPlotBounds().getMaxX() - rowNeed(v)), rowMinX(v), 0.5);
      assertTrue(rowMinX(v) < v.getPlotBounds().getX() - 1);
      assertTrue(rowMinX(v) >= v.getLegendGroup().getBounds().getX() - 0.5);
   }

   @Test
   void aWiderRowCentresOnThePlotThenStaysInTheGroup() {
      VGraph v = wideRow(GraphConstants.CENTER_ALIGNMENT);
      assertContentWidthRow(v);
      double want = v.getPlotBounds().getCenterX() - rowNeed(v) / 2;
      assertEquals(expectedX(v, want), rowMinX(v), 0.5);
   }

   @Test
   void aRowWiderThanTheChartFallsBackToToday() {
      VGraph a = chart(GraphConstants.TOP, GraphConstants.LEFT_ALIGNMENT, true, 60);
      VGraph b = chart(GraphConstants.TOP, null, true, 60);
      assertTrue(rowNeed(a) > a.getLegendGroup().getBounds().getWidth(), "premise: row > chart");
      assertSameLayout(b, a);
   }

   @Test
   void aWiderRowStartsExactlyAtThePlotEdgeWhenItFitsToTheGroupEdge() {
      VGraph v = insetRow(GraphConstants.LEFT_ALIGNMENT);
      assertContentWidthRow(v);
      assertEquals(v.getPlotBounds().getX(), rowMinX(v), 0.5);
      assertTrue(rowMaxX(v) > v.getPlotBounds().getMaxX() + 1);
      assertTrue(rowMaxX(v) <= groupMaxX(v) + 0.5);
   }

   @Test
   void aWiderRowEndsExactlyAtThePlotEdgeWhenItFitsToTheGroupEdge() {
      VGraph v = insetRow(GraphConstants.RIGHT_ALIGNMENT);
      assertContentWidthRow(v);
      assertEquals(v.getPlotBounds().getMaxX(), rowMaxX(v), 0.5);
      assertTrue(rowMinX(v) < v.getPlotBounds().getX() - 1);
      assertTrue(rowMinX(v) >= v.getLegendGroup().getBounds().getX() - 0.5);
   }

   @Test
   void aWiderRowCentresExactlyOnThePlotWhenItFitsTheGroup() {
      VGraph v = insetRow(GraphConstants.CENTER_ALIGNMENT);
      assertContentWidthRow(v);
      assertEquals(v.getPlotBounds().getCenterX(), (rowMinX(v) + rowMaxX(v)) / 2, 0.5);
   }

   @Test
   void aWiderRowEndingPastTheGroupsLeftEdgeStartsAtTheEdge() {
      VGraph v = leftClampRow();
      assertContentWidthRow(v);
      assertTrue(v.getPlotBounds().getMaxX() - rowNeed(v) < v.getLegendGroup().getBounds().getX() - 1,
                 "premise: ending at the plot's right edge would start left of the group");
      assertEquals(v.getLegendGroup().getBounds().getX(), rowMinX(v), 0.5);
      assertTrue(rowMaxX(v) <= groupMaxX(v) + 0.5);
   }

   // a row that fits the group but not between its left edge and the plot's right edge
   private static VGraph leftClampRow() {
      for(int w = 100; w <= 460; w += 2) {
         VGraph v = chart(GraphConstants.TOP, GraphConstants.RIGHT_ALIGNMENT, true, w, true);
         double need = rowNeed(v);
         Rectangle2D plot = v.getPlotBounds();
         Rectangle2D g = v.getLegendGroup().getBounds();

         if(plot.getX() > g.getX() + 2 && need > plot.getMaxX() - g.getX() + 2 &&
            need <= g.getWidth() - 2)
         {
            return v;
         }
      }

      fail("no width gives a row that needs the left clamp");
      return null;
   }

   // a right-axis chart whose row is wider than the plot and fits on both sides of the plot
   private static VGraph insetRow(int alignment) {
      for(int w = 100; w <= 460; w += 2) {
         VGraph v = chart(GraphConstants.TOP, alignment, true, w, true);
         double need = rowNeed(v);
         Rectangle2D plot = v.getPlotBounds();
         Rectangle2D g = v.getLegendGroup().getBounds();

         if(need > plot.getWidth() + 2 && plot.getX() + need <= g.getMaxX() - 2 &&
            plot.getMaxX() - need >= g.getX() + 2 &&
            plot.getCenterX() - need / 2 >= g.getX() + 2 &&
            plot.getCenterX() + need / 2 <= g.getMaxX() - 2)
         {
            return v;
         }
      }

      fail("no width gives a row wider than the plot that fits around it");
      return null;
   }

   // a chart width where the two-legend row is wider than the plot but fits the chart
   private static VGraph wideRow(int alignment) {
      for(int w = 100; w <= 460; w += 2) {
         VGraph v = chart(GraphConstants.TOP, alignment, true, w);
         double need = rowNeed(v);

         if(need > v.getPlotBounds().getWidth() + 2 &&
            need <= v.getLegendGroup().getBounds().getWidth() - 2)
         {
            return v;
         }
      }

      fail("no width gives a row wider than the plot but within the chart");
      return null;
   }

   private static void assertContentWidthRow(VGraph v) {
      assertEquals(rowNeed(v), rowMaxX(v) - rowMinX(v), 0.5, "legends keep their content widths");
   }

   // the wanted left edge, shifted just enough to keep the row inside the group
   private static double expectedX(VGraph v, double wanted) {
      Rectangle2D g = v.getLegendGroup().getBounds();
      return Math.max(g.getX(), Math.min(g.getMaxX() - rowNeed(v), wanted));
   }

   private static double rowNeed(VGraph v) {
      LegendGroup g = v.getLegendGroup();
      double h = g.getBounds().getHeight();
      double w = g.getBounds().getWidth();
      double t = 0;

      for(int i = 0; i < g.getLegendCount(); i++) {
         t += g.getLegend(i).getPreferredWidth(h, w);
      }

      return t;
   }

   private static double rowMinX(VGraph v) {
      return legend(v, 0).getX();
   }

   private static double rowMaxX(VGraph v) {
      return legend(v, v.getLegendGroup().getLegendCount() - 1).getMaxX();
   }

   private static double groupMaxX(VGraph v) {
      return v.getLegendGroup().getBounds().getMaxX();
   }

   private static void assertSameLayout(VGraph expected, VGraph actual) {
      LegendGroup group = expected.getLegendGroup();
      assertEquals(group.getLegendCount(), actual.getLegendGroup().getLegendCount());
      assertEquals(group.getBounds(), actual.getLegendGroup().getBounds());
      assertEquals(expected.getPlotBounds(), actual.getPlotBounds());

      for(int i = 0; i < group.getLegendCount(); i++) {
         assertEquals(legend(expected, i), legend(actual, i), "legend " + i);
      }
   }

   private static Rectangle2D legend(VGraph vgraph, int index) {
      return vgraph.getLegendGroup().getLegend(index).getBounds();
   }

   // a null alignment leaves the property untouched; two adds a shape legend
   private static VGraph chart(int layout, Integer alignment, boolean two) {
      return chart(layout, alignment, two, 400);
   }

   private static VGraph chart(int layout, Integer alignment, boolean two, int width) {
      return chart(layout, alignment, two, width, false);
   }

   // secondary adds a right Y axis, so the plot ends before the group's right edge
   private static VGraph chart(int layout, Integer alignment, boolean two, int width,
                               boolean secondary)
   {
      Object[][] rows = { { "State", "Region", "Sales", "Total" }, { "NJ", "East", 200, 5000 },
                          { "NY", "East", 3000, 7000 }, { "CA", "West", 12000, 9000 } };
      EGraph graph = new EGraph();
      IntervalElement elem = new IntervalElement("State", "Sales");
      elem.setColorFrame(new CategoricalColorFrame("State"));

      if(two) {
         elem.setShapeFrame(new CategoricalShapeFrame("Region"));
      }

      graph.addElement(elem);
      RectCoord coord = new RectCoord(new CategoricalScale("State"), new LinearScale("Sales"));

      if(secondary) {
         graph.addElement(new IntervalElement("State", "Total"));
         coord.setYScale2(new LinearScale("Total"));
      }

      graph.setCoordinate(coord);
      graph.setLegendLayout(layout);

      if(alignment != null) {
         graph.setLegendAlignment(alignment);
      }

      return Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, width, 240);
   }
}
