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
import inetsoft.graph.geometry.RelationGeometry;
import inetsoft.graph.mxgraph.model.mxGeometry;
import inetsoft.graph.visual.RelationVO;
import inetsoft.graph.visual.VOText;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A plot-fitted circular network lays out inside the plot box and settles in one replot.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RelationPlotFitTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aFittedCircleStaysInsideThePlot() {
      for(int n : new int[] { 8, 30, 120 }) {
         assertInside(circle(n, true, false, 376, 190), "plain, " + n + " nodes");
      }

      Circle legend = circle(30, true, true, 376, 190);
      assertTrue(legend.vgraph().getPlotBounds().getWidth() < 376 - 20, "premise: a legend");
      assertInside(legend, "with a legend");
   }

   @Test
   void aFittedCircleIsAtMostEightyPercent() {
      Circle c = circle(8, true, false, 376, 190);
      Dimension2D box = c.coord().getPlotBox();
      double min = Math.min(box.getWidth(), box.getHeight());
      Rectangle2D union = union(c);

      assertTrue(union.getWidth() <= 0.8 * min + 1, "width " + union.getWidth());
      assertTrue(union.getHeight() <= 0.8 * min + 1, "height " + union.getHeight());
      assertTrue(Math.max(union.getWidth(), union.getHeight()) >= 0.6 * min,
                 "larger side " + union);

      // the plot centres the nodes together with their labels
      Rectangle2D drawn = union;

      for(int i = 0; i < c.vgraph().getVisualCount(); i++) {
         if(c.vgraph().getVisual(i) instanceof VOText text) {
            drawn = drawn.createUnion(text.getBounds());
         }
      }

      Rectangle2D plot = c.vgraph().getPlotBounds();
      assertEquals(plot.getCenterX(), drawn.getCenterX(), 2, "centre x");
      assertEquals(plot.getCenterY(), drawn.getCenterY(), 2, "centre y");
   }

   @Test
   void aFittedCircleDoesNotScroll() {
      Circle c = circle(120, true, false, 376, 190);
      Rectangle2D plot = c.vgraph().getPlotBounds();

      assertTrue(c.vgraph().getMinPlotWidth() < plot.getWidth());
      assertTrue(c.vgraph().getMinPlotHeight() < plot.getHeight());
   }

   @Test
   void oneReplotSettlesTheBox() {
      Circle c = circle(30, true, false, 376, 190);
      Dimension2D box = c.coord().getPlotBox();

      assertNotNull(c.elem().getLaidOutBox());
      assertEquals(box.getWidth(), c.elem().getLaidOutBox().getWidth(), 0.5);
      assertEquals(box.getHeight(), c.elem().getLaidOutBox().getHeight(), 0.5);
      assertFalse(c.coord().requiresReplot());
   }

   @Test
   void aBrushedFittedChartSettlesInOneReplot() {
      Circle c = circle(30, true, false, 376, 190, true);

      assertFalse(c.coord().requiresReplot());

      List<RelationVO> base = nodes(c.vgraph(), c.elem());
      List<RelationVO> overlay = nodes(c.vgraph(), c.overlay());
      assertFalse(overlay.isEmpty(), "premise: overlay nodes");

      for(RelationVO vo : overlay) {
         Rectangle2D b = vo.getShapes()[0].getBounds2D();
         assertTrue(base.stream().anyMatch(n -> {
            Rectangle2D a = n.getShapes()[0].getBounds2D();
            return Math.abs(a.getCenterX() - b.getCenterX()) <= 1 &&
               Math.abs(a.getCenterY() - b.getCenterY()) <= 1;
         }), "overlay node " + b + " is not on a base node");
      }
   }

   @Test
   void aTinyBoxGivesNoNegativeLayout() {
      Circle c = circle(30, true, false, 60, 40);

      assertTrue(c.elem().getLayoutRadius() >= 0);

      for(RelationVO vo : nodes(c.vgraph(), c.elem())) {
         Rectangle2D b = vo.getShapes()[0].getBounds2D();
         assertTrue(Double.isFinite(b.getX()) && Double.isFinite(b.getY()) &&
                    Double.isFinite(b.getWidth()) && Double.isFinite(b.getHeight()), "" + b);
      }
   }

   @Test
   void aClassicCircleKeepsTodaysLayout() {
      Circle c = circle(120, false, false, 376, 190);

      assertNull(c.elem().getLaidOutBox());
      assertFalse(c.coord().requiresReplot());

      double maxNode = 0;

      for(RelationVO vo : nodes(c.vgraph(), c.elem())) {
         mxGeometry g = ((RelationGeometry) vo.getGeometry()).getMxCell().getGeometry();
         maxNode = Math.max(maxNode, Math.max(g.getWidth(), g.getHeight()));
      }

      assertTrue(c.elem().getLayoutRadius() >= 120 * maxNode * 0.75 / Math.PI - 0.5,
                 "radius " + c.elem().getLayoutRadius());
      assertTrue(c.vgraph().getMinPlotWidth() > c.vgraph().getPlotBounds().getWidth(),
                 "scrolls horizontally");
   }

   @Test
   void aFacetedCircleLaysOutWithoutABox() {
      DefaultDataSet data = data(30);
      RelationElement elem = element(false);
      elem.setPlotFit(true);
      EGraph egraph = new EGraph();
      egraph.addElement(elem);
      RelationCoord coord = new RelationCoord();
      coord.setParentCoordinate(new FacetCoord());
      egraph.setCoordinate(coord);

      elem.createGeometry(data, new GGraph(egraph, coord, data));

      assertNull(elem.getLaidOutBox());
      assertNull(coord.getPlotBox());
   }

   private static void assertInside(Circle c, String message) {
      Rectangle2D plot = c.vgraph().getPlotBounds();
      List<RelationVO> nodes = nodes(c.vgraph(), c.elem());
      assertFalse(nodes.isEmpty(), "premise: nodes, " + message);

      for(RelationVO vo : nodes) {
         Rectangle2D b = vo.getShapes()[0].getBounds2D();
         assertTrue(b.getMinX() >= plot.getMinX() - 1 && b.getMaxX() <= plot.getMaxX() + 1 &&
                    b.getMinY() >= plot.getMinY() - 1 && b.getMaxY() <= plot.getMaxY() + 1,
                    message + ": " + b + " outside " + plot);
      }
   }

   private static Rectangle2D union(Circle c) {
      Rectangle2D union = null;

      for(RelationVO vo : nodes(c.vgraph(), c.elem())) {
         Rectangle2D b = vo.getShapes()[0].getBounds2D();
         union = union == null ? b : union.createUnion(b);
      }

      assertNotNull(union);
      return union;
   }

   private static List<RelationVO> nodes(VGraph vgraph, RelationElement elem) {
      List<RelationVO> list = new ArrayList<>();

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof RelationVO vo &&
            ((RelationGeometry) vo.getGeometry()).getElement() == elem)
         {
            list.add(vo);
         }
      }

      return list;
   }

   private static DefaultDataSet data(int n) {
      Object[][] rows = new Object[n][];
      rows[0] = new Object[] { "From", "To" };

      for(int i = 1; i < n; i++) {
         rows[i] = new Object[] { "R", "T" + i };
      }

      return new DefaultDataSet(rows);
   }

   private static RelationElement element(boolean legend) {
      RelationElement elem = new RelationElement("From", "To");
      elem.setAlgorithm(RelationElement.Algorithm.CIRCLE);
      elem.setNodeWidth(5);
      elem.setNodeHeight(5);
      elem.setLabelPlacement(GraphConstants.TOP);
      elem.setSizeFrame(new StaticSizeFrame(1));
      elem.setNodeSizeFrame(new StaticSizeFrame(1));

      if(legend) {
         elem.setNodeColorFrame(new CategoricalColorFrame("To"));
      }

      return elem;
   }

   private static Circle circle(int n, boolean plotFit, boolean legend, int w, int h) {
      return circle(n, plotFit, legend, w, h, false);
   }

   private static Circle circle(int n, boolean plotFit, boolean legend, int w, int h,
                                boolean brushed)
   {
      RelationElement elem = element(legend);
      elem.setTextFrame(new DefaultTextFrame("To"));
      elem.setPlotFit(plotFit);
      EGraph egraph = new EGraph();
      egraph.addElement(elem);
      RelationElement overlay = null;

      if(brushed) {
         overlay = element(legend);
         overlay.setTextFrame(new DefaultTextFrame("To"));
         overlay.setPlotFit(plotFit);
         overlay.setHint("overlay", elem);
         elem.setHint("overlaid", overlay);
         egraph.addElement(overlay);
      }

      RelationCoord coord = new RelationCoord();
      egraph.setCoordinate(coord);

      if(legend) {
         egraph.setLegendLayout(GraphConstants.RIGHT);
      }

      VGraph vgraph = Plotter.getPlotter(egraph).plotAndLayout(data(n), 0, 0, w, h);
      assertNotNull(vgraph);
      return new Circle(coord, elem, overlay, vgraph);
   }

   private record Circle(RelationCoord coord, RelationElement elem, RelationElement overlay,
                         VGraph vgraph)
   {
   }
}
