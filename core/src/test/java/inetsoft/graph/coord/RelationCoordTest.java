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
import inetsoft.graph.aesthetic.StaticSizeFrame;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.RelationElement;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.visual.GraphVO;
import inetsoft.graph.visual.RelationVO;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The relation (tree) coordinate: fit, centring and scaling of the laid out nodes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class RelationCoordTest {
   private static final double EPS = 1e-6;
   private static final Object[][] TREE = {
      { "Src", "Tgt" }, { "R", "A" }, { "R", "B" }, { "R", "C" }, { "A", "D" }
   };

   private static VGraph plot(Object[][] rows, double x, double y, double w, double h) {
      EGraph graph = new EGraph();
      RelationElement elem = new RelationElement("Src", "Tgt");
      elem.setSizeFrame(new StaticSizeFrame());
      elem.setNodeSizeFrame(new StaticSizeFrame());
      elem.setAlgorithm(RelationElement.Algorithm.COMPACT_TREE);
      graph.addElement(elem);
      RelationCoord coord = new RelationCoord();
      graph.setCoordinate(coord);
      graph.setScale("Src", new CategoricalScale("Src"));
      graph.setScale("Tgt", new CategoricalScale("Tgt"));
      return Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), (int) x, (int) y,
                                                     (int) w, (int) h);
   }

   private static void collect(VGraph vg, List<RelationVO> list) {
      for(int i = 0; i < vg.getVisualCount(); i++) {
         Visualizable v = vg.getVisual(i);

         if(v instanceof RelationVO) {
            list.add((RelationVO) v);
         }
         else if(v instanceof GraphVO) {
            collect(((GraphVO) v).getVGraph(), list);
         }
      }
   }

   private static List<RelationVO> nodes(VGraph vg) {
      List<RelationVO> list = new ArrayList<>();
      collect(vg, list);
      return list;
   }

   private static Rectangle2D union(List<RelationVO> nodes) {
      Rectangle2D u = null;

      for(RelationVO vo : nodes) {
         Rectangle2D b = vo.getBounds();
         u = u == null ? (Rectangle2D) b.clone() : u.createUnion(b);
      }

      return u;
   }

   private static void assertInside(Rectangle2D outer, Rectangle2D inner, double tol) {
      assertTrue(inner.getX() >= outer.getX() - tol && inner.getY() >= outer.getY() - tol &&
                 inner.getMaxX() <= outer.getMaxX() + tol &&
                 inner.getMaxY() <= outer.getMaxY() + tol,
                 inner + " is not inside " + outer);
   }

   private static Point2D m(VGraph vg, double x, double y) {
      return vg.getScreenTransform().transform(new Point2D.Double(x, y), null);
   }

   private static void assertBounds(double x, double y, double w, double h, Rectangle2D r) {
      assertEquals(x, r.getX(), EPS, "x of " + r);
      assertEquals(y, r.getY(), EPS, "y of " + r);
      assertEquals(w, r.getWidth(), EPS, "width of " + r);
      assertEquals(h, r.getHeight(), EPS, "height of " + r);
   }

   @Test
   @DisplayName("fit without nodes only adds the gap offset")
   void fitWithoutVisuals() {
      RelationCoord c = new RelationCoord();
      VGraph vg = new VGraph(c);
      c.fit(0, 0, 300, 200);
      assertBounds(0, 0, 299, 199, vg.getPlotBounds());
      assertEquals(4, m(vg, 0, 0).getX(), EPS);
      assertEquals(4, m(vg, 0, 0).getY(), EPS);
      assertEquals(104, m(vg, 100, 50).getX(), EPS);
      assertEquals(54, m(vg, 100, 50).getY(), EPS);
   }

   @Test
   @DisplayName("fit at an offset moves the plot and the origin by the offset")
   void fitOffset() {
      RelationCoord c = new RelationCoord();
      VGraph vg = new VGraph(c);
      c.fit(10, 20, 300, 200);
      assertBounds(10, 20, 299, 199, vg.getPlotBounds());
      assertEquals(14, m(vg, 0, 0).getX(), EPS);
      assertEquals(24, m(vg, 0, 0).getY(), EPS);
   }

   @Test
   @DisplayName("the nodes lie inside the plot when there is room")
   void containedRoomy() {
      VGraph vg = plot(TREE, 0, 0, 600, 400);
      List<RelationVO> nodes = nodes(vg);
      assertEquals(5, nodes.size());
      assertInside(vg.getPlotBounds(), union(nodes), 0);
   }

   @Test
   @DisplayName("the nodes lie inside a small plot after scaling down")
   void containedScaledDown() {
      VGraph vg = plot(TREE, 0, 0, 120, 80);
      assertInside(vg.getPlotBounds(), union(nodes(vg)), 1);
   }

   @Test
   @DisplayName("the nodes lie inside a small plot that starts below the origin")
   void containedScaledDownAtOffset() {
      VGraph vg = plot(TREE, 0, 50, 120, 80);
      assertInside(vg.getPlotBounds(), union(nodes(vg)), 1);
   }

   @Test
   @DisplayName("the plot of a small area at an offset is the area minus one pixel in width and height")
   void plotBoundsAtOffset() {
      VGraph vg = plot(TREE, 0, 50, 120, 80);
      assertBounds(0, 50, 118, 78, vg.getPlotBounds());
   }

   @Test
   @DisplayName("the nodes are centred horizontally")
   void centredHorizontally() {
      VGraph vg = plot(TREE, 0, 0, 600, 400);
      assertEquals(300, union(nodes(vg)).getCenterX(), 1);
   }

   @Test
   @DisplayName("the nodes are centred vertically, also when the area starts below the origin")
   void centredVertically() {
      VGraph vg = plot(TREE, 0, 0, 600, 400);
      assertEquals(200, union(nodes(vg)).getCenterY(), 1);

      VGraph shifted = plot(TREE, 0, 50, 600, 400);
      assertEquals(250, union(nodes(shifted)).getCenterY(), 1);
   }

   @Test
   @DisplayName("scaling down keeps the aspect ratio of the nodes")
   void aspectKept() {
      Rectangle2D big = union(nodes(plot(TREE, 0, 0, 600, 400)));
      Rectangle2D small = union(nodes(plot(TREE, 0, 0, 120, 80)));
      double bigRatio = big.getWidth() / big.getHeight();
      double smallRatio = small.getWidth() / small.getHeight();
      assertEquals(bigRatio, smallRatio, bigRatio * 0.02);
   }

   @Test
   @DisplayName("extra room is not used to enlarge the nodes")
   void neverScaledUp() {
      Rectangle2D a = union(nodes(plot(TREE, 0, 0, 600, 400)));
      Rectangle2D b = union(nodes(plot(TREE, 0, 0, 1200, 800)));
      assertEquals(a.getWidth(), b.getWidth(), 0.5);
      assertEquals(a.getHeight(), b.getHeight(), 0.5);
   }

   @Test
   @DisplayName("the unit minimum size is the natural size and does not depend on the area")
   void unitMinSizeUnscaled() {
      VGraph big = plot(TREE, 0, 0, 600, 400);
      VGraph small = plot(TREE, 0, 0, 120, 80);
      Coordinate cb = big.getCoordinate();
      Coordinate cs = small.getCoordinate();
      assertEquals(cb.getUnitMinWidth(), cs.getUnitMinWidth(), 1);
      assertEquals(cb.getUnitMinHeight(), cs.getUnitMinHeight(), 1);
   }

   @Test
   @DisplayName("without nodes every unit size is 100, like the treemap coordinate")
   void unitSizeWithoutVisuals() {
      RelationCoord c = new RelationCoord();
      new VGraph(c);
      assertEquals(100, c.getUnitMinWidth(), EPS);
      assertEquals(100, c.getUnitMinHeight(), EPS);
      assertEquals(100, c.getUnitPreferredWidth(), EPS);
      assertEquals(100, c.getUnitPreferredHeight(), EPS);
   }

   @Test
   @DisplayName("a self loop does not break the layout")
   void selfLoop() {
      Object[][] rows = { { "Src", "Tgt" }, { "R", "R" }, { "R", "A" } };
      VGraph vg = assertDoesNotThrow(() -> plot(rows, 0, 0, 600, 400));

      for(RelationVO vo : nodes(vg)) {
         Rectangle2D b = vo.getBounds();
         assertTrue(Double.isFinite(b.getX()) && Double.isFinite(b.getY()) &&
                    Double.isFinite(b.getWidth()) && Double.isFinite(b.getHeight()), "" + b);
      }
   }

   @Test
   @DisplayName("empty data gives no nodes and the full plot area")
   void emptyData() {
      Object[][] rows = { { "Src", "Tgt" } };
      VGraph vg = assertDoesNotThrow(() -> plot(rows, 0, 0, 600, 400));
      assertTrue(nodes(vg).isEmpty());
      assertBounds(0, 0, 598, 398, vg.getPlotBounds());
   }

   @Test
   @DisplayName("the root of a tree is drawn above its children")
   void rootOnTop() {
      VGraph vg = plot(TREE, 0, 0, 600, 400);
      RelationVO root = null;
      double childY = Double.NaN;

      for(RelationVO vo : nodes(vg)) {
         if("R".equals(String.valueOf(((inetsoft.graph.geometry.RelationGeometry) vo.getGeometry()).getMxCell().getValue()))) {
            root = vo;
         }
         else if("B".equals(String.valueOf(((inetsoft.graph.geometry.RelationGeometry) vo.getGeometry()).getMxCell().getValue()))) {
            childY = vo.getBounds().getCenterY();
         }
      }

      assertNotNull(root, "root node");
      assertTrue(root.getBounds().getCenterY() > childY,
                 "root " + root.getBounds().getCenterY() + " child " + childY);
   }

   @Test
   @DisplayName("facet of trees: the nodes of each tile lie inside the plot of that tile")
   void facetOfTrees() {
      Object[][] rows = {
         { "Grp", "Src", "Tgt" },
         { "g1", "R", "A" }, { "g1", "R", "B" }, { "g1", "A", "D" },
         { "g2", "R", "A" }, { "g2", "R", "C" }
      };
      EGraph graph = new EGraph();
      RelationElement elem = new RelationElement("Src", "Tgt");
      elem.setSizeFrame(new StaticSizeFrame());
      elem.setNodeSizeFrame(new StaticSizeFrame());
      elem.setAlgorithm(RelationElement.Algorithm.COMPACT_TREE);
      graph.addElement(elem);
      CategoricalScale gs = new CategoricalScale("Grp");
      graph.setScale("Grp", gs);
      graph.setScale("Src", new CategoricalScale("Src"));
      graph.setScale("Tgt", new CategoricalScale("Tgt"));
      FacetCoord facet = new FacetCoord(new RectCoord(gs, null),
                                        new Coordinate[] { new RelationCoord() }, true);
      graph.setCoordinate(facet);
      VGraph vg = Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 800, 400);

      int tiles = 0;

      for(TileCoord[] row : facet.getTileCoords()) {
         for(TileCoord tile : row) {
            tiles++;
            Coordinate inner = tile.getCoordinates()[0];
            List<RelationVO> list = nodes(inner.getVGraph());
            assertFalse(list.isEmpty(), "tile without nodes");
            assertInside(inner.getVGraph().getPlotBounds(), union(list), 1);
         }
      }

      assertEquals(2, tiles);
   }

   @Test
   @DisplayName("pinned: the coordinate has no dimensions, scales or axes of its own")
   void constantMembers() {
      RelationCoord c = new RelationCoord();
      assertEquals(0, c.getDimCount());
      assertEquals(0, c.getScales().length);
      assertEquals(0, c.getAxes(true).length);
      assertEquals(0, c.getPosition(new double[] { 1, 2 }).getX(), EPS);
      assertEquals(0, c.getPosition(new double[] { 1, 2 }).getY(), EPS);
      assertEquals(0, c.getIntervalSize(5), EPS);
   }
}
