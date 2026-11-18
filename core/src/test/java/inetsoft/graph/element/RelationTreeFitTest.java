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
package inetsoft.graph.element;

import inetsoft.graph.EGraph;
import inetsoft.graph.GraphConstants;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.DefaultTextFrame;
import inetsoft.graph.aesthetic.LinearSizeFrame;
import inetsoft.graph.aesthetic.SizeFrame;
import inetsoft.graph.aesthetic.StaticSizeFrame;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.FacetCoord;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.coord.RelationCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.geometry.RelationEdgeGeometry;
import inetsoft.graph.geometry.RelationGeometry;
import inetsoft.graph.mxgraph.model.mxCell;
import inetsoft.graph.mxgraph.model.mxGeometry;
import inetsoft.graph.mxgraph.util.mxPoint;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.visual.RelationEdgeVO;
import inetsoft.graph.visual.RelationVO;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A plot-fitted tree closes its sibling gaps, then shrinks to its plot's breadth down to
 * 75% of its natural size, past which it scrolls.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RelationTreeFitTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   // the gap RelationCoord leaves around the drawing
   private static final int GAP = 4;

   @Test
   void aTreeThatFitsKeepsItsNaturalLayout() {
      for(boolean plotFit : new boolean[] { true, false }) {
         Tree tree = tree(fan(4), plotFit, false, false, 376, 190, null);
         List<mxGeometry> leaves = tree.leaves();
         String msg = "plotFit " + plotFit;

         assertEquals(4, leaves.size(), "premise: four leaves");
         assertEquals(plotFit, tree.elem().getLaidOutBox() != null, "premise: " + msg);
         assertBoxes(leaves, 44, 30, 0.01, msg);
         assertGaps(leaves, false, 24, 0.01, msg);
      }
   }

   @Test
   void theCanonicalTreeClosesGapsThenShrinks() {
      Tree tree = tree(canonical(), true, false, false, 376, 190, null);
      Dimension2D box = tree.coord().getPlotBox();
      double s = box.getWidth() / 460.0;
      List<mxGeometry> leaves = tree.leaves();

      assertEquals(9, leaves.size(), "premise: nine leaves");
      assertTrue(s > 0.75 && s < 1, "premise: the scale is above the floor, " + s);
      assertBoxes(leaves, 44 * s, 30 * s, 1, "canonical");
      assertGaps(leaves, false, 8 * s, 1, "canonical");

      double breadth = tree.breadth(false);
      assertTrue(breadth <= box.getWidth() + 0.01, "breadth " + breadth + " of " + box);
      assertEquals(box.getWidth(), breadth, 1, "the breadth fills the box");
      assertTrue(tree.vgraph().getMinPlotWidth() <= tree.vgraph().getPlotBounds().getWidth(),
                 "min width " + tree.vgraph().getMinPlotWidth() + " of " +
                 tree.vgraph().getPlotBounds().getWidth());
      assertEdgesMeetTheirNodes(tree);
   }

   @Test
   void levelGapsShrinkOnlyWithTheScale() {
      Tree tree = tree(canonical(), true, false, false, 376, 190, null);
      double s = tree.coord().getPlotBox().getWidth() / 460.0;
      Map<String, mxGeometry> nodes = tree.nodes();

      for(String parent : new String[] { "N1", "N2", "N3", "N4" }) {
         double gap = verticalGap(nodes.get(parent), nodes.get("L" + parent + "_1"));
         assertEquals(22 * s, gap, 1, "level gap under " + parent);
      }

      double gap = verticalGap(nodes.get("N5"), nodes.get("LN5_1"));
      assertEquals(22 * s, gap, 1, "level gap under N5");
   }

   @Test
   void aWideTreeStopsAtSeventyFivePercentAndScrolls() {
      Tree tree = tree(wide(), true, false, false, 376, 190, null);
      List<mxGeometry> leaves = tree.leaves();

      assertEquals(40, leaves.size(), "premise: forty leaves");
      assertBoxes(leaves, 33, 23, 1, "wide");
      assertGaps(leaves, false, 6, 1, "wide");
      assertTrue(tree.vgraph().getMinPlotWidth() > tree.vgraph().getPlotBounds().getWidth(),
                 "scrolls horizontally");
   }

   @Test
   void everyOrientationFitsItsBreadth() {
      for(boolean flipped : new boolean[] { false, true }) {
         String msg = flipped ? "right-to-left" : "left-to-right";
         Tree tree = tree(canonical(), true, true, flipped, 376, 300, null);
         Dimension2D box = tree.coord().getPlotBox();
         double s = box.getHeight() / 334.0;
         List<mxGeometry> leaves = tree.leaves();

         assertTrue(s > 0.75 && s < 1, msg + ": premise: the scale is above the floor, " + s);
         assertBoxes(leaves, 44 * s, 30 * s, 1, msg);
         assertGaps(leaves, true, 8 * s, 1, msg);

         double breadth = tree.breadth(true);
         assertTrue(breadth <= box.getHeight() + 0.01, msg + ": breadth " + breadth);
         assertEquals(box.getHeight(), breadth, 1, msg + ": the breadth fills the box");
         assertTrue(tree.vgraph().getMinPlotHeight() <=
                    tree.vgraph().getPlotBounds().getHeight(), msg + ": no vertical scroll");

         Tree narrow = tree(canonical(), true, true, flipped, 200, 300, null);
         assertEquals(leaves.get(0).getWidth(), narrow.leaves().get(0).getWidth(), 0.01,
                      msg + ": the plot width plays no part");
      }

      Tree bottomUp = tree(canonical(), true, false, true, 376, 190, null);
      Dimension2D box = bottomUp.coord().getPlotBox();
      double s = box.getWidth() / 460.0;
      List<mxGeometry> leaves = bottomUp.leaves();

      assertBoxes(leaves, 44 * s, 30 * s, 1, "bottom-to-top");
      assertGaps(leaves, false, 8 * s, 1, "bottom-to-top");
      assertEquals(box.getWidth(), bottomUp.breadth(false), 1, "bottom-to-top breadth");
   }

   @Test
   void aSizeBoundTreeKeepsItsProportions() {
      double[] smalls = new double[2];
      double[] ratios = new double[2];

      for(int i = 0; i < 2; i++) {
         LinearSizeFrame size = new LinearSizeFrame("Qty");
         size.setLargest(5);
         Tree tree = tree(sized(), i == 0, false, false, 376, 190, size);
         Map<String, mxGeometry> nodes = tree.nodes();
         double small = nodes.get("LN1_1").getWidth();
         double large = nodes.get("LN1_2").getWidth();

         assertTrue(large > small + 1, "premise: two leaf sizes, " + small + " " + large);
         smalls[i] = small;
         ratios[i] = small / large;
      }

      assertTrue(smalls[0] < smalls[1] - 1, "premise: the fitted tree shrinks");
      assertEquals(ratios[1], ratios[0], 0.02);
   }

   @Test
   void aTinyBoxFloorsTheTreeScale() {
      Tree tree = tree(canonical(), true, false, false, 60, 40, null);

      assertBoxes(tree.leaves(), 33, 23, 1, "tiny");

      for(mxGeometry g : tree.nodes().values()) {
         assertTrue(Double.isFinite(g.getWidth()) && g.getWidth() > 0 &&
                    Double.isFinite(g.getHeight()) && g.getHeight() > 0, "" + g);
      }
   }

   @Test
   void aClassicTreeKeepsTodaysLayout() {
      Tree tree = tree(canonical(), false, false, false, 376, 190, null);
      List<mxGeometry> leaves = tree.leaves();

      assertNull(tree.elem().getLaidOutBox());
      assertBoxes(leaves, 44, 30, 0.01, "classic");
      assertGaps(leaves, false, 24, 0.01, "classic");
      assertTrue(tree.vgraph().getMinPlotWidth() > tree.vgraph().getPlotBounds().getWidth(),
                 "scrolls horizontally");
   }

   @Test
   void aScrollingTreeIsAsLargeAsItsNodes() {
      for(boolean flipped : new boolean[] { false, true }) {
         String msg = flipped ? "bottom-to-top" : "top-to-bottom";
         Tree tree = labelled(deep(), true, false, flipped, 376, 190, GraphConstants.CENTER);
         Rectangle2D extent = tree.extent();
         VGraph vgraph = tree.vgraph();

         assertTrue(vgraph.getMinPlotWidth() > vgraph.getPlotBounds().getWidth(),
                    msg + ": premise: scrolls horizontally");
         assertEquals(extent.getHeight() + 2 * GAP, vgraph.getMinPlotHeight(), 2,
                      msg + ": min height for " + extent);
         assertEquals(extent.getWidth() + 2 * GAP, vgraph.getMinPlotWidth(), 2,
                      msg + ": min width for " + extent);
      }
   }

   @Test
   void anExpandedTreeOpensWithItsRootAtTheEdge() {
      for(boolean flipped : new boolean[] { false, true }) {
         String msg = flipped ? "bottom-to-top" : "top-to-bottom";
         Tree tree = labelled(deep(), true, false, flipped, 376, 190, GraphConstants.CENTER);
         assertTrue(tree.vgraph().getMinPlotHeight() > tree.vgraph().getPlotBounds().getHeight(),
                    msg + ": premise: scrolls down");

         Tree expanded = expand(tree, deep(), false, flipped, GraphConstants.CENTER);
         Rectangle2D plot = expanded.vgraph().getPlotBounds();
         Rectangle2D root = expanded.root();
         // screen y grows upward, so the top-to-bottom root is at the plot's max y
         double gap = flipped ? root.getMinY() - plot.getMinY() : plot.getMaxY() - root.getMaxY();

         assertTrue(gap >= 0 && gap <= root.getHeight(),
                    msg + ": the root is " + gap + " from the edge of " + plot);
      }
   }

   @Test
   void aSidewaysTreeThatFitsItsDepthDoesNotScrollAcross() {
      for(boolean flipped : new boolean[] { false, true }) {
         String msg = flipped ? "right-to-left" : "left-to-right";
         Tree tree = labelled(big(), true, true, flipped, 376, 190, GraphConstants.CENTER);
         Rectangle2D extent = tree.extent();
         VGraph vgraph = tree.vgraph();
         double plotW = vgraph.getPlotBounds().getWidth();

         assertTrue(vgraph.getMinPlotHeight() > vgraph.getPlotBounds().getHeight(),
                    msg + ": premise: scrolls vertically");
         assertTrue(extent.getWidth() + 2 * GAP < plotW, msg + ": premise: the depth fits");
         assertTrue(vgraph.getMinPlotWidth() <= plotW,
                    msg + ": min width " + vgraph.getMinPlotWidth() + " of " + plotW);
         assertEquals(extent.getWidth() + 2 * GAP, vgraph.getMinPlotWidth(), 2,
                      msg + ": min width for " + extent);
         assertEquals(extent.getHeight() + 2 * GAP, vgraph.getMinPlotHeight(), 2,
                      msg + ": min height for " + extent);
      }
   }

   @Test
   void aTreeWithOutsideLabelsKeepsRoomForThem() {
      Tree tree = labelled(deep(), true, false, false, 376, 190, GraphConstants.TOP);
      Rectangle2D extent = tree.extent();
      VGraph vgraph = tree.vgraph();
      double label = tree.maxLabelRoom();

      assertTrue(label > 5, "premise: labels outside the nodes, " + label);
      assertTrue(vgraph.getMinPlotWidth() > vgraph.getPlotBounds().getWidth(),
                 "premise: scrolls horizontally");
      assertTrue(vgraph.getMinPlotHeight() >= extent.getHeight() + 2 * GAP + label - 2,
                 "min height " + vgraph.getMinPlotHeight() + " for " + extent + " and " + label);
      assertTrue(vgraph.getMinPlotHeight() <= extent.getHeight() + 2 * GAP + label + 2,
                 "min height " + vgraph.getMinPlotHeight() + " for " + extent + " and " + label);

      Tree expanded = expand(tree, deep(), false, false, GraphConstants.TOP);
      Rectangle2D plot = expanded.vgraph().getPlotBounds();
      Rectangle2D root = expanded.root();
      double gap = plot.getMaxY() - root.getMaxY();

      assertTrue(gap >= 0 && gap <= root.getHeight() + label,
                 "the root is " + gap + " from the top of " + plot);
   }

   @Test
   void aClassicTreeKeepsTodaysMinimumSize() {
      for(boolean horizontal : new boolean[] { false, true }) {
         String msg = horizontal ? "left-to-right" : "top-to-bottom";
         Tree tree = labelled(big(), false, horizontal, false, 376, 190, GraphConstants.CENTER);
         VGraph vgraph = tree.vgraph();
         Rectangle2D visuals = tree.visuals();
         Rectangle2D extent = tree.extent();
         double scaleX = Math.abs(vgraph.getScreenTransform().getScaleX());
         double scaleY = Math.abs(vgraph.getScreenTransform().getScaleY());
         double height = horizontal ? tree.integerExtent().getHeight() + 2 * GAP
            : visuals.getHeight() / scaleY + 2 * GAP;

         assertNull(tree.elem().getLaidOutBox(), msg + ": premise: classic");
         assertTrue(Math.max(vgraph.getMinPlotWidth() - extent.getWidth(),
                             vgraph.getMinPlotHeight() - extent.getHeight()) > 2 * GAP + 20,
                    msg + ": premise: the labels widen the classic size");
         assertEquals(visuals.getWidth() / scaleX + 2 * GAP, vgraph.getMinPlotWidth(), 0.01,
                      msg + ": min width");
         assertEquals(height, vgraph.getMinPlotHeight(), 0.01, msg + ": min height");
      }
   }

   @Test
   void aFacetedTreeKeepsTodaysMinimumSize() {
      RelationElement elem = labelledElement(false, false, GraphConstants.CENTER);
      elem.setPlotFit(true);
      EGraph egraph = new EGraph();
      egraph.addElement(elem);
      FacetCoord facet = new FacetCoord(new RectCoord(new CategoricalScale("Group"), null),
                                        new RelationCoord());
      egraph.setCoordinate(facet);
      VGraph vgraph = Plotter.getPlotter(egraph).plotAndLayout(faceted(), 0, 0, 376, 190);
      assertNotNull(vgraph);
      int cells = 0;

      for(Coordinate[] row : facet.getExpandedInnerCoords()) {
         for(Coordinate inner : row) {
            RelationCoord coord = (RelationCoord) inner;
            VGraph cell = coord.getVGraph();
            Rectangle2D visuals = union(cell, true);
            double scaleX = Math.abs(cell.getScreenTransform().getScaleX());
            double scaleY = Math.abs(cell.getScreenTransform().getScaleY());

            assertNull(elem.getLaidOutBox(), "premise: a faceted tree has no box");
            assertEquals(visuals.getWidth() / scaleX + 2 * GAP, coord.getUnitMinWidth(), 0.01,
                         "cell min width");
            assertEquals(visuals.getHeight() / scaleY + 2 * GAP, coord.getUnitMinHeight(), 0.01,
                         "cell min height");
            cells++;
         }
      }

      assertTrue(cells > 1, "premise: several cells");
   }

   private static void assertBoxes(List<mxGeometry> boxes, double w, double h, double delta,
                                   String message)
   {
      for(mxGeometry g : boxes) {
         assertEquals(w, g.getWidth(), delta, message + ": width of " + g);
         assertEquals(h, g.getHeight(), delta, message + ": height of " + g);
      }
   }

   // the gaps between neighbouring leaves along the breadth
   private static void assertGaps(List<mxGeometry> leaves, boolean horizontal, double gap,
                                  double delta, String message)
   {
      for(int i = 1; i < leaves.size(); i++) {
         mxGeometry a = leaves.get(i - 1);
         mxGeometry b = leaves.get(i);
         double actual = horizontal ? b.getY() - (a.getY() + a.getHeight())
            : b.getX() - (a.getX() + a.getWidth());
         assertEquals(gap, actual, delta, message + ": gap " + i);
      }
   }

   // each routed edge starts on its source box and ends on its target box
   private static void assertEdgesMeetTheirNodes(Tree tree) {
      int count = 0;

      for(int i = 0; i < tree.vgraph().getVisualCount(); i++) {
         if(tree.vgraph().getVisual(i) instanceof RelationEdgeVO vo &&
            vo.getGeometry() instanceof RelationEdgeGeometry geo)
         {
            mxCell edge = geo.getEdge();
            List<mxPoint> points = edge.getGeometry().getPoints();
            assertTrue(points != null && points.size() >= 2, "premise: a routed edge");
            assertOnBox(points.get(0), edge.getSource().getGeometry(), edge.getId());
            assertOnBox(points.get(points.size() - 1), edge.getTarget().getGeometry(),
                        edge.getId());
            count++;
         }
      }

      assertTrue(count > 0, "premise: edges");
   }

   private static void assertOnBox(mxPoint p, mxGeometry box, String message) {
      assertTrue(p.getX() >= box.getX() - 1 && p.getX() <= box.getX() + box.getWidth() + 1 &&
                 p.getY() >= box.getY() - 1 && p.getY() <= box.getY() + box.getHeight() + 1,
                 message + ": " + p + " off " + box);
   }

   // the space between a parent and its child across the level
   private static double verticalGap(mxGeometry parent, mxGeometry child) {
      return Math.max(parent.getY() - (child.getY() + child.getHeight()),
                      child.getY() - (parent.getY() + parent.getHeight()));
   }

   // R with n leaves
   private static DefaultDataSet fan(int n) {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "From", "To" });

      for(int i = 1; i <= n; i++) {
         rows.add(new Object[] { "R", "LR_" + i });
      }

      return new DefaultDataSet(rows.toArray(new Object[0][]));
   }

   // R with N1..N5, N1..N4 with two leaves each and N5 with one
   private static DefaultDataSet canonical() {
      return new DefaultDataSet(treeRows(new int[] { 2, 2, 2, 2, 1 }, false));
   }

   // R with N1..N4, each with ten leaves
   private static DefaultDataSet wide() {
      return new DefaultDataSet(treeRows(new int[] { 10, 10, 10, 10 }, false));
   }

   // the canonical tree with a size measure of 1 or 9 on alternate leaves
   private static DefaultDataSet sized() {
      return new DefaultDataSet(treeRows(new int[] { 2, 2, 2, 2, 1 }, true));
   }

   private static Object[][] treeRows(int[] leafCounts, boolean qty) {
      List<Object[]> rows = new ArrayList<>();
      rows.add(qty ? new Object[] { "From", "To", "Qty" } : new Object[] { "From", "To" });

      for(int i = 1; i <= leafCounts.length; i++) {
         rows.add(qty ? new Object[] { "R", "N" + i, 5 } : new Object[] { "R", "N" + i });
      }

      for(int i = 1; i <= leafCounts.length; i++) {
         for(int j = 1; j <= leafCounts[i - 1]; j++) {
            String leaf = "LN" + i + "_" + j;
            rows.add(qty ? new Object[] { "N" + i, leaf, j % 2 == 1 ? 1 : 9 }
                     : new Object[] { "N" + i, leaf });
         }
      }

      return rows.toArray(new Object[0][]);
   }

   private static Tree tree(DefaultDataSet data, boolean plotFit, boolean horizontal,
                            boolean flipped, int w, int h, SizeFrame nodeSize)
   {
      RelationElement elem = new RelationElement("From", "To");
      elem.setAlgorithm(RelationElement.Algorithm.COMPACT_TREE);
      elem.setSizeFrame(new StaticSizeFrame(1));
      elem.setNodeSizeFrame(nodeSize != null ? nodeSize : new StaticSizeFrame(1));
      elem.setHorizontal(horizontal);
      elem.setFlipped(flipped);
      elem.setPlotFit(plotFit);
      EGraph egraph = new EGraph();
      egraph.addElement(elem);
      RelationCoord coord = new RelationCoord();
      egraph.setCoordinate(coord);

      VGraph vgraph = Plotter.getPlotter(egraph).plotAndLayout(data, 0, 0, w, h);
      assertNotNull(vgraph);
      return new Tree(coord, elem, horizontal, vgraph);
   }

   // R with N1..N6, each with nine leaves
   private static DefaultDataSet big() {
      return new DefaultDataSet(treeRows(new int[] { 9, 9, 9, 9, 9, 9 }, false));
   }

   // six levels, 199 nodes: deeper and wider than the plot even at the floor
   private static DefaultDataSet deep() {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "From", "To" });
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

   // two groups, each a root with nine leaves
   private static DefaultDataSet faceted() {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "Group", "From", "To" });

      for(int g = 1; g <= 2; g++) {
         for(int i = 1; i <= 9; i++) {
            rows.add(new Object[] { "G" + g, "R" + g, "L" + g + "_" + i });
         }
      }

      return new DefaultDataSet(rows.toArray(new Object[0][]));
   }

   // a tree element with a label on every node, as a tree chart draws it
   private static RelationElement labelledElement(boolean horizontal, boolean flipped,
                                                  int placement)
   {
      RelationElement elem = new RelationElement("From", "To");
      elem.setAlgorithm(RelationElement.Algorithm.COMPACT_TREE);
      elem.setSizeFrame(new StaticSizeFrame(1));
      elem.setNodeSizeFrame(new StaticSizeFrame(1));
      elem.setTextFrame(new DefaultTextFrame("To"));
      elem.setLabelPlacement(placement);
      elem.setHorizontal(horizontal);
      elem.setFlipped(flipped);
      return elem;
   }

   private static Tree labelled(DefaultDataSet data, boolean plotFit, boolean horizontal,
                                boolean flipped, int w, int h, int placement)
   {
      RelationElement elem = labelledElement(horizontal, flipped, placement);
      elem.setPlotFit(plotFit);
      EGraph egraph = new EGraph();
      egraph.addElement(elem);
      RelationCoord coord = new RelationCoord();
      egraph.setCoordinate(coord);

      VGraph vgraph = Plotter.getPlotter(egraph).plotAndLayout(data, 0, 0, w, h);
      assertNotNull(vgraph);
      return new Tree(coord, elem, horizontal, vgraph);
   }

   // plot a fitted tree again at the size a scrolling chart gives its expanded graph
   private static Tree expand(Tree tree, DefaultDataSet data, boolean horizontal,
                              boolean flipped, int placement)
   {
      VGraph vgraph = tree.vgraph();
      Rectangle2D graph = vgraph.getBounds();
      Rectangle2D plot = vgraph.getPlotBounds();
      int w = (int) (Math.max(vgraph.getMinPlotWidth(), plot.getWidth()) +
         graph.getWidth() - plot.getWidth());
      int h = (int) (Math.max(vgraph.getMinPlotHeight(), plot.getHeight()) +
         graph.getHeight() - plot.getHeight());
      return labelled(data, true, horizontal, flipped, w, h, placement);
   }

   // the union of the visuals' screen bounds, or of the nodes' only
   private static Rectangle2D union(VGraph vgraph, boolean all) {
      Rectangle2D union = null;

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(all || vgraph.getVisual(i) instanceof RelationVO) {
            Rectangle2D b = vgraph.getVisual(i).getBounds();

            if(b != null) {
               union = union == null ? b : union.createUnion(b);
            }
         }
      }

      assertNotNull(union, "premise: visuals");
      return union;
   }

   private record Tree(RelationCoord coord, RelationElement elem, boolean horizontal,
                       VGraph vgraph)
   {
      // the laid-out node boxes in layout space
      Rectangle2D extent() {
         Rectangle2D union = null;

         for(mxGeometry g : nodes().values()) {
            Rectangle2D b = new Rectangle2D.Double(g.getX(), g.getY(), g.getWidth(),
                                                   g.getHeight());
            union = union == null ? b : union.createUnion(b);
         }

         return union;
      }

      // the laid-out node boxes rounded to whole pixels
      Rectangle2D integerExtent() {
         Rectangle2D union = null;

         for(mxGeometry g : nodes().values()) {
            union = union == null ? g.getRectangle() : union.createUnion(g.getRectangle());
         }

         return union;
      }

      // the room a node's outside label adds to its height
      double maxLabelRoom() {
         return nodes().values().stream()
            .mapToDouble(g -> g.getOuterHeight() - g.getHeight()).max().orElse(0);
      }

      Rectangle2D visuals() {
         return union(vgraph, true);
      }

      // the root node in screen space
      Rectangle2D root() {
         for(int i = 0; i < vgraph.getVisualCount(); i++) {
            if(vgraph.getVisual(i) instanceof RelationVO vo &&
               vo.getGeometry() instanceof RelationGeometry geo && geo.isRoot())
            {
               return vo.getBounds();
            }
         }

         return fail("premise: a root");
      }

      // the node boxes in layout space, by node name
      Map<String, mxGeometry> nodes() {
         Map<String, mxGeometry> map = new HashMap<>();

         for(int i = 0; i < vgraph.getVisualCount(); i++) {
            if(vgraph.getVisual(i) instanceof RelationVO vo &&
               vo.getGeometry() instanceof RelationGeometry geo)
            {
               map.put(String.valueOf(geo.getMxCell().getValue()),
                       geo.getMxCell().getGeometry());
            }
         }

         assertFalse(map.isEmpty(), "premise: nodes");
         return map;
      }

      // the leaves along the breadth
      List<mxGeometry> leaves() {
         List<mxGeometry> list = new ArrayList<>();

         nodes().forEach((name, g) -> {
            if(name.startsWith("L")) {
               list.add(g);
            }
         });

         list.sort(Comparator.comparingDouble(g -> horizontal ? g.getY() : g.getX()));
         return list;
      }

      // the extent of all nodes along the breadth
      double breadth(boolean alongY) {
         double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;

         for(mxGeometry g : nodes().values()) {
            min = Math.min(min, alongY ? g.getY() : g.getX());
            max = Math.max(max, alongY ? g.getY() + g.getHeight() : g.getX() + g.getWidth());
         }

         return max - min;
      }
   }
}
