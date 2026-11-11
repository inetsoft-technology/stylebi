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
      Object[][] rows = { { "State", "Region", "Sales" }, { "NJ", "East", 200 },
                          { "NY", "East", 3000 }, { "CA", "West", 12000 } };
      EGraph graph = new EGraph();
      IntervalElement elem = new IntervalElement("State", "Sales");
      elem.setColorFrame(new CategoricalColorFrame("State"));

      if(two) {
         elem.setShapeFrame(new CategoricalShapeFrame("Region"));
      }

      graph.addElement(elem);
      graph.setCoordinate(new RectCoord(new CategoricalScale("State"), new LinearScale("Sales")));
      graph.setLegendLayout(layout);

      if(alignment != null) {
         graph.setLegendAlignment(alignment);
      }

      return Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 400, 240);
   }
}
