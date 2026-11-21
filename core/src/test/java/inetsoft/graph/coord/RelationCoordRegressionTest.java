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
package inetsoft.graph.coord;

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.RelationElement;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.visual.RelationVO;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for {@link RelationCoord#fit}: a relation chart (tree, network,
 * circle) that is scaled down to fit must stay inside the plot area when the plot
 * does not start at y = 0 (e.g. legend at the bottom). Bug #82030.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RelationCoordRegressionTest {
   private static final double EPS = 0.01;

   private static final Object[][] ROWS = {
      { "Month", "State" },
      { "2019-10", "CA" }, { "2019-10", "NY" }, { "2019-10", "TX" },
      { "2019-11", "WA" }, { "2019-11", "OR" }, { "2019-11", "NV" },
      { "2019-12", "AZ" }, { "2019-12", "UT" }, { "2019-12", "ID" },
   };

   private static final Object[][] FACET_ROWS = {
      { "Region", "Month", "State" },
      { "East", "2019-10", "CA" }, { "East", "2019-10", "NY" }, { "East", "2019-10", "TX" },
      { "East", "2019-11", "WA" }, { "East", "2019-11", "OR" }, { "East", "2019-11", "NV" },
      { "West", "2019-12", "AZ" }, { "West", "2019-12", "UT" }, { "West", "2019-12", "ID" },
      { "West", "2019-11", "AK" }, { "West", "2019-11", "HI" }, { "West", "2019-11", "CO" },
   };

   /**
    * A scaled-down chart laid out at y = 50 must be the chart laid out at y = 0
    * moved down by exactly 50, and stay inside the plot.
    */
   @ParameterizedTest
   @EnumSource(value = RelationElement.Algorithm.class,
               names = { "COMPACT_TREE", "ORGANIC", "CIRCLE" })
   void scaledChartWithPlotYOffsetIsShiftedByOffset(RelationElement.Algorithm algorithm) {
      Layout atOrigin = layout(algorithm, -1, 0, 0, 120, 80);
      Layout atOffset = layout(algorithm, -1, 0, 50, 120, 80);

      assertTrue(atOrigin.ratio < 1, "chart must be scaled down: " + atOrigin);
      assertShifted(atOrigin, atOffset, 0, 50);
      assertInside(atOrigin);
      assertInside(atOffset);
   }

   /**
    * A bottom legend moves the plot down. The scaled-down chart must sit in the plot
    * exactly as it does with the same-sized legend at the top.
    */
   @ParameterizedTest
   @EnumSource(value = RelationElement.Algorithm.class,
               names = { "COMPACT_TREE", "CIRCLE" })
   void scaledChartWithBottomLegendStaysInPlot(RelationElement.Algorithm algorithm) {
      Layout top = layout(algorithm, GraphConstants.TOP, 0, 0, 160, 140);
      Layout bottom = layout(algorithm, GraphConstants.BOTTOM, 0, 0, 160, 140);

      assertTrue(bottom.ratio < 1, "chart must be scaled down: " + bottom);
      assertTrue(bottom.plot.getMinY() > 0, "bottom legend must move the plot: " + bottom);
      assertEquals(top.plot.getHeight(), bottom.plot.getHeight(), EPS);
      assertShifted(top, bottom, 0, bottom.plot.getMinY() - top.plot.getMinY());
      assertInside(bottom);
   }

   /** Control: an unscaled chart (ratio == 1) was already correct and must stay so. */
   @Test
   void unscaledChartWithPlotYOffsetIsShiftedByOffset() {
      Layout atOrigin = layout(RelationElement.Algorithm.COMPACT_TREE, -1, 0, 0, 1000, 1000);
      Layout atOffset = layout(RelationElement.Algorithm.COMPACT_TREE, -1, 0, 50, 1000, 1000);

      assertEquals(1, atOrigin.ratio, 1e-9);
      assertShifted(atOrigin, atOffset, 0, 50);
      assertInside(atOffset);
   }

   /** Control: facet cells (lower cells and a bottom legend) stay inside their cell. */
   @Test
   void facetCellsStayInsideTheirPlot() {
      assertFacetInside(true, GraphConstants.BOTTOM, 200, 260);
      assertFacetInside(false, GraphConstants.BOTTOM, 300, 200);
      assertFacetInside(true, -1, 200, 200);
   }

   private static void assertShifted(Layout base, Layout moved, double dx, double dy) {
      String msg = "expected " + moved + " to equal " + base + " moved by (" + dx + "," + dy + ")";
      assertEquals(base.ratio, moved.ratio, 1e-9, msg);
      assertEquals(base.nodes.getMinX() + dx, moved.nodes.getMinX(), EPS, msg);
      assertEquals(base.nodes.getMaxX() + dx, moved.nodes.getMaxX(), EPS, msg);
      assertEquals(base.nodes.getMinY() + dy, moved.nodes.getMinY(), EPS, msg);
      assertEquals(base.nodes.getMaxY() + dy, moved.nodes.getMaxY(), EPS, msg);
   }

   private static void assertInside(Layout layout) {
      assertTrue(isInside(layout.nodes, layout.plot), "nodes outside plot: " + layout);
   }

   private static boolean isInside(Rectangle2D nodes, Rectangle2D plot) {
      return nodes.getMinX() >= plot.getMinX() - 0.5 &&
         nodes.getMaxX() <= plot.getMaxX() + 0.5 &&
         nodes.getMinY() >= plot.getMinY() - 0.5 &&
         nodes.getMaxY() <= plot.getMaxY() + 0.5;
   }

   private static Layout layout(RelationElement.Algorithm algorithm, int legend,
                                int x, int y, int w, int h)
   {
      DefaultDataSet data = new DefaultDataSet(ROWS);
      RelationElement elem = createElement(algorithm, data, legend);
      EGraph egraph = new EGraph();
      egraph.addElement(elem);
      egraph.setCoordinate(new RelationCoord());

      if(legend >= 0) {
         egraph.setLegendLayout(legend);
      }

      VGraph vgraph = Plotter.getPlotter(egraph).plotAndLayout(data, x, y, w, h);
      Layout layout = collect(vgraph);
      assertNotNull(layout, "no relation nodes for " + algorithm);
      return layout;
   }

   private static RelationElement createElement(RelationElement.Algorithm algorithm,
                                                DefaultDataSet data, int legend)
   {
      RelationElement elem = new RelationElement("Month", "State");
      elem.setAlgorithm(algorithm);
      elem.setSizeFrame(new StaticSizeFrame());
      elem.setNodeSizeFrame(new StaticSizeFrame());

      if(legend >= 0) {
         CategoricalScale scale = new CategoricalScale("State");
         scale.init(data);
         CategoricalColorFrame frame = new CategoricalColorFrame("State");
         frame.setScale(scale);
         elem.setNodeColorFrame(frame);
      }

      return elem;
   }

   private static void assertFacetInside(boolean vertical, int legend, int w, int h) {
      DefaultDataSet data = new DefaultDataSet(FACET_ROWS);
      RelationElement elem = createElement(RelationElement.Algorithm.COMPACT_TREE, data, legend);
      CategoricalScale region = new CategoricalScale("Region");
      RectCoord outer = vertical ? new RectCoord(null, region) : new RectCoord(region, null);
      FacetCoord facet = new FacetCoord(outer, new RelationCoord());
      EGraph egraph = new EGraph();
      egraph.addElement(elem);
      egraph.setCoordinate(facet);

      if(legend >= 0) {
         egraph.setLegendLayout(legend);
      }

      Plotter.getPlotter(egraph).plotAndLayout(data, 0, 0, w, h);
      int cells = 0;

      for(Coordinate[] row : facet.getExpandedInnerCoords()) {
         for(Coordinate coord : row) {
            Layout layout = collect(coord.getVGraph());

            if(layout != null) {
               cells++;
               assertTrue(layout.ratio < 1, "facet cell must be scaled down: " + layout);
               assertInside(layout);
            }
         }
      }

      assertEquals(2, cells, "facet cells with nodes");
   }

   private static Layout collect(VGraph vgraph) {
      Rectangle2D nodes = null;
      double ratio = Double.NaN;

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof RelationVO vo) {
            nodes = nodes == null ? vo.getBounds() : nodes.createUnion(vo.getBounds());
            ratio = vo.getScreenTransform().getScaleX();
         }
      }

      return nodes == null ? null : new Layout(vgraph.getPlotBounds(), nodes, ratio);
   }

   private record Layout(Rectangle2D plot, Rectangle2D nodes, double ratio) {
      @Override
      public String toString() {
         return String.format("[ratio=%.4f plot y=%.1f..%.1f x=%.1f..%.1f nodes y=%.1f..%.1f " +
                                 "x=%.1f..%.1f]", ratio, plot.getMinY(), plot.getMaxY(),
                              plot.getMinX(), plot.getMaxX(), nodes.getMinY(), nodes.getMaxY(),
                              nodes.getMinX(), nodes.getMaxX());
      }
   }
}
