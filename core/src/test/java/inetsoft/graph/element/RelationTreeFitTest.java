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
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.LinearSizeFrame;
import inetsoft.graph.aesthetic.SizeFrame;
import inetsoft.graph.aesthetic.StaticSizeFrame;
import inetsoft.graph.coord.RelationCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.geometry.RelationEdgeGeometry;
import inetsoft.graph.geometry.RelationGeometry;
import inetsoft.graph.mxgraph.model.mxCell;
import inetsoft.graph.mxgraph.model.mxGeometry;
import inetsoft.graph.mxgraph.util.mxPoint;
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

   private record Tree(RelationCoord coord, RelationElement elem, boolean horizontal,
                       VGraph vgraph)
   {
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
