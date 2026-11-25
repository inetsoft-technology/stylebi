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
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.RelationElement;
import inetsoft.graph.element.RelationElement.Algorithm;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.visual.RelationVO;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A circular network is drawn inside its visible plot. A chart draw follows VGraphPair: the
 * graph at the card size, then the expanded graph at the minimum size the first reports. The
 * element lays out in the chart content box, which is larger than the plot (padding, legend).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RelationCircleFitTest {
   // a 400x240 card: the content box, and the graph inside the 10px chart padding
   private static final Card WIDE = new Card(398, 218, 378, 198);
   // a 400x600 card
   private static final Card TALL = new Card(398, 578, 378, 558);
   private static final int NONE = -1;

   @Test
   void aCircleFitsTheVisiblePlotAtAnyNodeCount() {
      for(int n : new int[] { 20, 30, 120 }) {
         Draw draw = draw(Algorithm.CIRCLE, network(n, false), WIDE, NONE, false, 1);

         assertFitsVisiblePlot(draw, n + " nodes");
      }
   }

   @Test
   void aCircleWithALegendFitsBesideIt() {
      for(int n : new int[] { 30, 120 }) {
         Draw draw = draw(Algorithm.CIRCLE, network(n, true), WIDE, GraphConstants.RIGHT,
                          false, 1);

         assertTrue(draw.vgraph().getPlotBounds().getWidth() < WIDE.graphW() - 20,
                    "premise: the legend narrows the plot");
         assertFitsVisiblePlot(draw, n + " nodes with a legend");
      }
   }

   // the plot starts above the legend, so the shrunk circle is placed from the plot y
   @Test
   void aCircleWithABottomLegendFitsAboveIt() {
      Draw draw = draw(Algorithm.CIRCLE, network(120, true), WIDE, GraphConstants.BOTTOM,
                       false, 1);

      assertTrue(draw.vgraph().getPlotBounds().getMinY() > 0,
                 "premise: the legend moves the plot");
      assertFitsVisiblePlot(draw, "120 nodes with a bottom legend");
   }

   @Test
   void aCircleWithLabelsFitsTheVisiblePlot() {
      Draw draw = draw(Algorithm.CIRCLE, network(30, false), WIDE, NONE, true, 1);

      assertFitsVisiblePlot(draw, "30 nodes with labels");
   }

   // the shrunk circle fills the shorter side less the gaps, centred
   @Test
   void aShrunkCircleKeepsItsProportions() {
      Draw draw = draw(Algorithm.CIRCLE, network(20, false), WIDE, NONE, false, 1);
      Rectangle2D plot = draw.evgraph().getPlotBounds();
      Rectangle2D nodes = nodes(draw.evgraph());
      double share = nodes.getHeight() / Math.min(plot.getWidth(), plot.getHeight());

      assertTrue(share >= 0.85 && share <= 1, "share of the shorter side: " + share);
      assertEquals(plot.getCenterX(), nodes.getCenterX(), 1, "centred across");
      assertEquals(plot.getCenterY(), nodes.getCenterY(), 1, "centred down");
   }

   // a circle with room is drawn as before: not scaled, 90% of the content box, centred
   @Test
   void aCircleWithRoomIsUnchanged() {
      for(int n : new int[] { 30, 120 }) {
         Draw draw = draw(Algorithm.CIRCLE, network(n, false), TALL, NONE, false, 1);
         Rectangle2D plot = draw.evgraph().getPlotBounds();
         Rectangle2D nodes = nodes(draw.evgraph());
         double side = Math.min(TALL.layoutW(), TALL.layoutH());

         assertEquals(1, scale(draw.evgraph()), 1e-9, n + " nodes: not scaled");
         assertEquals(0.9, nodes.getWidth() / side, 0.01, n + " nodes: share");
         assertEquals(plot.getCenterX(), nodes.getCenterX(), 1, n + " nodes: centred across");
         assertEquals(plot.getCenterY(), nodes.getCenterY(), 1, n + " nodes: centred down");
         assertFitsVisiblePlot(draw, n + " nodes on a tall card");
      }
   }

   // an author's enlarge still scrolls
   @Test
   void anEnlargedCircleStillScrolls() {
      Draw draw = draw(Algorithm.CIRCLE, network(30, false), WIDE, NONE, false, 1.5);
      Rectangle2D plot = draw.vgraph().getPlotBounds();

      assertTrue(draw.minW() > plot.getWidth() || draw.minH() > plot.getHeight(),
                 "min " + draw.minW() + "x" + draw.minH() + " in " + rect(plot));
      assertEquals(1, scale(draw.evgraph()), 1e-9, "not scaled");
   }

   // a network laid out larger than its plot still scrolls
   @Test
   void anOrganicNetworkStillScrolls() {
      Card small = new Card(WIDE.layoutW(), WIDE.layoutH(), 200, 120);
      Draw draw = draw(Algorithm.ORGANIC, network(30, false), small, NONE, false, 1);
      Rectangle2D plot = draw.vgraph().getPlotBounds();

      assertTrue(draw.minW() > plot.getWidth() || draw.minH() > plot.getHeight(),
                 "min " + draw.minW() + "x" + draw.minH() + " in " + rect(plot));
      assertEquals(1, scale(draw.evgraph()), 1e-9, "not scaled");
   }

   @Test
   void aTreeStillScrolls() {
      Draw draw = draw(Algorithm.COMPACT_TREE, tree(), WIDE, NONE, false, 1);
      Rectangle2D plot = draw.vgraph().getPlotBounds();

      assertTrue(draw.minW() > plot.getWidth() && draw.minH() > plot.getHeight(),
                 "min " + draw.minW() + "x" + draw.minH() + " in " + rect(plot));
   }

   // the cells of a faceted circle keep their natural size, so the facet scrolls
   @Test
   void aFacetedCircleStillScrolls() {
      RelationElement elem = element(Algorithm.CIRCLE, WIDE, NONE, false, 1);
      EGraph egraph = new EGraph();
      egraph.addElement(elem);
      egraph.setCoordinate(new FacetCoord(new RectCoord(new CategoricalScale("Group"), null),
                                          new RelationCoord()));
      VGraph vgraph = Plotter.getPlotter(egraph)
         .plotAndLayout(network(30, true), 0, 0, WIDE.graphW(), WIDE.graphH());

      assertNotNull(vgraph);
      assertTrue(vgraph.getMinPlotWidth() > vgraph.getPlotBounds().getWidth(),
                 "min width " + vgraph.getMinPlotWidth() + " in " + rect(vgraph.getPlotBounds()));
   }

   private static void assertFitsVisiblePlot(Draw draw, String msg) {
      Rectangle2D visible = draw.vgraph().getPlotBounds();
      Rectangle2D drawn = draw.evgraph().getPlotBounds();
      Rectangle2D nodes = nodes(draw.evgraph());

      assertEquals(visible.getWidth(), drawn.getWidth(), 0.5, msg + ": no horizontal scroll");
      assertEquals(visible.getHeight(), drawn.getHeight(), 0.5, msg + ": no vertical scroll");
      assertTrue(nodes.getMinX() >= drawn.getMinX() - 0.5 &&
                 nodes.getMaxX() <= drawn.getMaxX() + 0.5 &&
                 nodes.getMinY() >= drawn.getMinY() - 0.5 &&
                 nodes.getMaxY() <= drawn.getMaxY() + 0.5,
                 msg + ": nodes " + rect(nodes) + " outside the plot " + rect(drawn));
   }

   // the graph at the card size, then the expanded graph at the size the first asks for
   private static Draw draw(Algorithm algo, DataSet data, Card card, int legend,
                            boolean labels, double enlarge)
   {
      VGraph vgraph = Plotter.getPlotter(graph(algo, card, legend, labels, enlarge))
         .plotAndLayout(data, 0, 0, card.graphW(), card.graphH());
      assertNotNull(vgraph);
      Rectangle2D bounds = vgraph.getBounds();
      Rectangle2D plot = vgraph.getPlotBounds();
      double minW = vgraph.getMinPlotWidth();
      double minH = vgraph.getMinPlotHeight();
      int ew = (int) (Math.max(minW, plot.getWidth()) + bounds.getWidth() - plot.getWidth());
      int eh = (int) (Math.max(minH, plot.getHeight()) + bounds.getHeight() - plot.getHeight());

      EGraph egraph2 = graph(algo, card, legend, labels, enlarge);
      VGraph evgraph = Plotter.getPlotter(egraph2).plot(data);
      evgraph.layout(0, 0, ew, eh);

      if(evgraph.getCoordinate().requiresReplot()) {
         evgraph = Plotter.getPlotter(egraph2).plot(data);
         evgraph.layout(0, 0, ew, eh);
      }

      return new Draw(vgraph, evgraph, minW, minH);
   }

   private static EGraph graph(Algorithm algo, Card card, int legend, boolean labels,
                               double enlarge)
   {
      EGraph egraph = new EGraph();
      egraph.addElement(element(algo, card, legend, labels, enlarge));
      egraph.setCoordinate(new RelationCoord());

      if(legend != NONE) {
         egraph.setLegendLayout(legend);
      }

      return egraph;
   }

   // as GraphGenerator builds a relation element: 5px nodes, labels on top
   private static RelationElement element(Algorithm algo, Card card, int legend,
                                          boolean labels, double enlarge)
   {
      RelationElement elem = new RelationElement("Source", "Target");
      boolean tree = algo == Algorithm.COMPACT_TREE;
      elem.setAlgorithm(algo);
      elem.setNodeWidth(tree ? 40 : 5);
      elem.setNodeHeight(tree ? 30 : 5);
      elem.setLabelPlacement(tree ? GraphConstants.CENTER : GraphConstants.TOP);
      elem.setSizeFrame(new StaticSizeFrame(1));
      elem.setNodeSizeFrame(new StaticSizeFrame(1));
      elem.setWidthRatio(enlarge);
      elem.setHeightRatio(enlarge);
      elem.setLayoutSize(new Dimension(card.layoutW(), card.layoutH()));

      if(labels || tree) {
         elem.setTextFrame(new DefaultTextFrame("Target"));
      }

      if(legend != NONE) {
         CategoricalScale scale = new CategoricalScale("Group");
         scale.init(network(3, true));
         CategoricalColorFrame frame = new CategoricalColorFrame("Group");
         frame.setScale(scale);
         elem.setNodeColorFrame(frame);
      }

      return elem;
   }

   // a ring with a chord from every third node, in three groups
   private static DataSet network(int n, boolean groups) {
      List<Object[]> rows = new ArrayList<>();
      rows.add(groups ? new Object[] { "Group", "Source", "Target" }
                  : new Object[] { "Source", "Target" });

      for(int i = 0; i < n; i++) {
         addEdge(rows, groups, i, "N" + i, "N" + ((i + 1) % n));

         if(i % 3 == 0) {
            addEdge(rows, groups, i, "N" + i, "N" + ((i + 5) % n));
         }
      }

      return new DefaultDataSet(rows.toArray(new Object[0][]));
   }

   private static void addEdge(List<Object[]> rows, boolean groups, int i, String from,
                               String to)
   {
      rows.add(groups ? new Object[] { "G" + (i % 3 + 1), from, to } : new Object[] { from, to });
   }

   // six levels, 199 nodes: larger than the plot both ways
   private static DataSet tree() {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "Source", "Target" });
      List<String> level = List.of("R");

      for(int fan : new int[] { 2, 2, 3, 3, 4 }) {
         List<String> next = new ArrayList<>();

         for(String parent : level) {
            for(int i = 0; i < fan; i++) {
               String child = "N" + rows.size();
               rows.add(new Object[] { parent, child });
               next.add(child);
            }
         }

         level = next;
      }

      return new DefaultDataSet(rows.toArray(new Object[0][]));
   }

   private static Rectangle2D nodes(VGraph vgraph) {
      Rectangle2D nodes = null;

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof RelationVO vo) {
            nodes = nodes == null ? vo.getBounds() : nodes.createUnion(vo.getBounds());
         }
      }

      assertNotNull(nodes, "no nodes drawn");
      return nodes;
   }

   // the scale the fit applied to the laid out nodes
   private static double scale(VGraph vgraph) {
      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof RelationVO vo) {
            return vo.getScreenTransform().getScaleX();
         }
      }

      return Double.NaN;
   }

   private static String rect(Rectangle2D r) {
      return String.format("%.1f,%.1f %.1fx%.1f", r.getX(), r.getY(), r.getWidth(),
                           r.getHeight());
   }

   private record Card(int layoutW, int layoutH, int graphW, int graphH) {}

   private record Draw(VGraph vgraph, VGraph evgraph, double minW, double minH) {}
}
