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
import inetsoft.graph.geometry.RelationGeometry;
import inetsoft.graph.mxgraph.model.mxGeometry;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.visual.ElementVO;
import inetsoft.graph.visual.FormVO;
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

import java.awt.*;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A plot-fitted relation chart lays out once per graph: the first plot, which has no plot
 * box yet and is always replotted, skips its layout. A chart draw follows VGraphPair: the
 * graph at the card size, then the expanded graph at the size the first one asks for.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RelationLayoutOnceTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aFittedChartLaysOutOncePerGraph() {
      for(Algorithm algo : FITTED) {
         Draw draw = draw(algo, true, data(algo), false);

         assertEquals(2, draw.main().plots, algo + ": premise: the graph is replotted");
         assertEquals(2, draw.expanded().plots,
                      algo + ": premise: the expanded graph is replotted");
         assertEquals(1, draw.main().layouts, algo + ": layouts of the graph");
         assertEquals(1, draw.expanded().layouts, algo + ": layouts of the expanded graph");
         assertEquals(2, draw.main().layouts + draw.expanded().layouts, algo + ": per draw");
      }
   }

   @Test
   void aClassicChartKeepsItsSinglePlot() {
      for(Algorithm algo : FITTED) {
         Draw draw = draw(algo, false, data(algo), false);

         assertEquals(1, draw.main().plots, algo + ": plots of the graph");
         assertEquals(1, draw.main().layouts, algo + ": layouts of the graph");
         assertEquals(1, draw.expanded().plots, algo + ": plots of the expanded graph");
         assertEquals(1, draw.expanded().layouts, algo + ": layouts of the expanded graph");
         assertNull(draw.main().getLaidOutBox(), algo + ": no plot box");
         assertTrue(draw.main().placed && draw.expanded().placed, algo + ": nodes placed");
      }
   }

   // the drawing equals one whose first plot laid out in the layout size, as before
   @Test
   void theDrawingMatchesALaidOutFirstPlot() {
      for(Algorithm algo : FITTED) {
         Draw once = draw(algo, true, data(algo), false);
         Draw twice = draw(algo, true, data(algo), true);

         assertEquals(2, twice.main().layouts, algo + ": premise: the reference lays out twice");
         assertEquals(2, twice.expanded().layouts,
                      algo + ": premise: the reference lays out the expanded graph twice");
         assertTrue(once.main().placed && once.expanded().placed, algo + ": nodes placed");
         assertEquals(twice.print(), once.print(), algo.toString());
         assertLabels(twice.labels(), once.labels(), algo.toString());
      }
   }

   @Test
   void aScrollingTreeKeepsItsMinimumAndExpandedSize() {
      Draw once = draw(Algorithm.COMPACT_TREE, true, tree(), false);
      Draw twice = draw(Algorithm.COMPACT_TREE, true, tree(), true);
      Rectangle2D plot = once.vgraph().getPlotBounds();

      assertTrue(once.minW() > plot.getWidth() && once.minH() > plot.getHeight(),
                 "premise: the tree scrolls both ways, min " + once.minW() + "x" + once.minH());
      assertEquals(twice.minW(), once.minW(), 0, "min width");
      assertEquals(twice.minH(), once.minH(), 0, "min height");
      assertEquals(twice.ew(), once.ew(), "expanded width");
      assertEquals(twice.eh(), once.eh(), "expanded height");
   }

   @Test
   void aFittedCircleAddsItsRingOnce() {
      for(boolean plotFit : new boolean[] { true, false }) {
         Draw draw = draw(Algorithm.CIRCLE, plotFit, data(Algorithm.CIRCLE), false, true);
         String msg = plotFit ? "fitted" : "classic";

         assertEquals(1, draw.graph().getFormCount(), msg + ": forms of the graph");
         assertEquals(1, draw.graph2().getFormCount(), msg + ": forms of the expanded graph");
         assertEquals(1, formVOs(draw.vgraph()), msg + ": rings drawn in the graph");
         assertEquals(1, formVOs(draw.evgraph()), msg + ": rings drawn in the expanded graph");
      }
   }

   // a first plot whose fallback box happens to equal the plot box is still replotted
   @Test
   void aFirstPlotWithoutABoxIsAlwaysReplotted() {
      for(Algorithm algo : FITTED) {
         Dimension2D box = draw(algo, true, data(algo), false).main().getLaidOutBox();
         CountingElement elem = element(algo, true);
         elem.setLayoutSize(new Dimension((int) box.getWidth(), (int) box.getHeight()));
         assertEquals(box.getWidth(), elem.getLayoutSize().getWidth(), 0,
                      algo + ": premise: a whole-pixel box");
         assertEquals(box.getHeight(), elem.getLayoutSize().getHeight(), 0,
                      algo + ": premise: a whole-pixel box");
         EGraph egraph = graph(elem, false);
         VGraph vgraph = Plotter.getPlotter(egraph).plotAndLayout(data(algo), 0, 0, W, H);

         assertNotNull(vgraph);
         assertTrue(elem.placed, algo + ": the drawn nodes are laid out");
         assertEquals(1, elem.layouts, algo + ": layouts");
         assertEquals(box.getWidth(), elem.getLaidOutBox().getWidth(), 0.5, algo.toString());
         assertEquals(box.getHeight(), elem.getLaidOutBox().getHeight(), 0.5, algo.toString());
         assertFalse(egraph.getCoordinate().requiresReplot(), algo + ": settled");
      }
   }

   // a plot leaves the plot box known, so the replot lays out in it
   @Test
   void theReplotAlwaysLaysOut() {
      for(Algorithm algo : FITTED) {
         CountingElement elem = element(algo, true);
         EGraph egraph = graph(elem, false);
         RelationCoord coord = (RelationCoord) egraph.getCoordinate();
         Plotter plotter = Plotter.getPlotter(egraph);

         plotter.plot(data(algo)).layout(0, 0, W, H);
         assertNotNull(coord.getPlotBox(), algo + ": the plot box is known");
         assertTrue(coord.requiresReplot(), algo + ": a replot is asked for");

         VGraph vgraph = plotter.plot(data(algo));
         vgraph.layout(0, 0, W, H);

         assertTrue(elem.placed, algo + ": the replot lays out");
         assertEquals(coord.getPlotBox().getWidth(), elem.getLaidOutBox().getWidth(), 0.5);
         assertEquals(coord.getPlotBox().getHeight(), elem.getLaidOutBox().getHeight(), 0.5);
         assertFalse(coord.requiresReplot(), algo + ": settled");
      }
   }

   // a facet never replots its cells, so a fitted cell lays out in its only plot
   @Test
   void aFacetedFittedChartLaysOutInItsOnlyPlot() {
      for(Algorithm algo : FITTED) {
         CountingElement elem = element(algo, true);
         EGraph egraph = new EGraph();
         egraph.addElement(elem);
         egraph.setCoordinate(new FacetCoord(new RectCoord(new CategoricalScale("Group"), null),
                                             new RelationCoord()));
         VGraph vgraph = Plotter.getPlotter(egraph).plotAndLayout(faceted(), 0, 0, W, H);

         assertNotNull(vgraph);
         assertTrue(elem.plots > 1, algo + ": premise: several cells");
         assertEquals(elem.plots, elem.layouts, algo + ": every cell is laid out");
      }
   }

   private static final int W = 376;
   private static final int H = 190;
   // overlapping labels that share a centre are shaken apart at random, by under 0.01px
   private static final double LABEL_TOLERANCE = 0.5;
   private static final Algorithm[] FITTED = {
      Algorithm.ORGANIC, Algorithm.CIRCLE, Algorithm.COMPACT_TREE };

   private static Draw draw(Algorithm algo, boolean plotFit, DataSet data, boolean laidOutFirst) {
      return draw(algo, plotFit, data, laidOutFirst, false);
   }

   // the graph at the card size, then the expanded graph replotted once if it asks
   private static Draw draw(Algorithm algo, boolean plotFit, DataSet data,
                            boolean laidOutFirst, boolean ring)
   {
      CountingElement main = element(algo, plotFit);
      CountingElement expanded = element(algo, plotFit);

      if(ring) {
         main.addShapeBorder(GShape.CIRCLE, Color.GRAY, GLine.MEDIUM_DASH);
         expanded.addShapeBorder(GShape.CIRCLE, Color.GRAY, GLine.MEDIUM_DASH);
      }

      EGraph egraph = graph(main, laidOutFirst);
      VGraph vgraph = Plotter.getPlotter(egraph).plotAndLayout(data, 0, 0, W, H);
      assertNotNull(vgraph);
      Rectangle2D bounds = vgraph.getBounds();
      Rectangle2D plot = vgraph.getPlotBounds();
      double minW = vgraph.getMinPlotWidth();
      double minH = vgraph.getMinPlotHeight();
      int ew = (int) (Math.max(minW, plot.getWidth()) + bounds.getWidth() - plot.getWidth());
      int eh = (int) (Math.max(minH, plot.getHeight()) + bounds.getHeight() - plot.getHeight());

      EGraph egraph2 = graph(expanded, laidOutFirst);
      VGraph evgraph = Plotter.getPlotter(egraph2).plot(data);
      evgraph.layout(0, 0, ew, eh);

      if(evgraph.getCoordinate().requiresReplot()) {
         evgraph = Plotter.getPlotter(egraph2).plot(data);
         evgraph.layout(0, 0, ew, eh);
      }

      return new Draw(egraph, egraph2, main, expanded, vgraph, evgraph, minW, minH, ew, eh);
   }

   // a laid-out first plot gets a plot box equal to the layout size it falls back to
   private static EGraph graph(RelationElement elem, boolean laidOutFirst) {
      EGraph egraph = new EGraph();
      egraph.addElement(elem);
      RelationCoord coord = new RelationCoord();
      egraph.setCoordinate(coord);

      if(laidOutFirst) {
         Dimension size = elem.getLayoutSize();
         coord.setCoordBounds(new Rectangle2D.Double(0, 0, size.width + 9, size.height + 9));
         assertEquals(size.getWidth(), coord.getPlotBox().getWidth(), 0, "premise: seeded");
         assertEquals(size.getHeight(), coord.getPlotBox().getHeight(), 0, "premise: seeded");
      }

      return egraph;
   }

   private static CountingElement element(Algorithm algo, boolean plotFit) {
      CountingElement elem = new CountingElement();
      boolean tree = algo == Algorithm.COMPACT_TREE;
      elem.setAlgorithm(algo);
      elem.setSizeFrame(new StaticSizeFrame(1));
      elem.setNodeSizeFrame(new StaticSizeFrame(1));
      elem.setTextFrame(new DefaultTextFrame("To"));
      elem.setLabelPlacement(tree ? GraphConstants.CENTER : GraphConstants.TOP);

      if(!tree) {
         elem.setNodeWidth(10);
         elem.setNodeHeight(10);
      }

      elem.setLayoutSize(new Dimension(W, H));
      elem.setPlotFit(plotFit);
      return elem;
   }

   private static DataSet data(Algorithm algo) {
      return algo == Algorithm.COMPACT_TREE ? tree() : network();
   }

   // clusters of five (a hub with four leaves), hubs chained, 30 nodes
   private static DataSet network() {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "From", "To" });

      for(int i = 1; i < 30; i++) {
         int from = i % 5 != 0 ? i / 5 * 5 : i - 5;
         rows.add(new Object[] { "N" + from, "N" + i });
      }

      return new DefaultDataSet(rows.toArray(new Object[0][]));
   }

   // six levels, 199 nodes: larger than the plot both ways
   private static DataSet tree() {
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
   private static DataSet faceted() {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "Group", "From", "To" });

      for(int g = 1; g <= 2; g++) {
         for(int i = 1; i <= 9; i++) {
            rows.add(new Object[] { "G" + g, "R" + g, "L" + g + "_" + i });
         }
      }

      return new DefaultDataSet(rows.toArray(new Object[0][]));
   }

   private static int formVOs(VGraph vgraph) {
      int count = 0;

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof FormVO) {
            count++;
         }
      }

      return count;
   }

   // the visuals, with where their labels are when asked, less the rings
   private static String print(VGraph vgraph, double minW, double minH, boolean texts) {
      StringBuilder sb = new StringBuilder();
      sb.append("bounds ").append(rect(vgraph.getBounds()))
         .append(" plot ").append(rect(vgraph.getPlotBounds()))
         .append(String.format(" min %.3fx%.3f", minW, minH));

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         Visualizable visual = vgraph.getVisual(i);

         if(visual instanceof FormVO || !texts && visual instanceof VOText) {
            continue;
         }

         sb.append('\n').append(visual.getClass().getSimpleName());

         // label boxes are compared by labels(), with a tolerance
         if(!(visual instanceof VOText)) {
            sb.append(' ').append(rect(visual.getBounds()));
         }

         if(texts && visual instanceof ElementVO vo && vo.getVOText() != null) {
            sb.append(" text");
         }
      }

      return sb.toString();
   }

   // the label boxes, in the order print() lists them
   private static List<Rectangle2D> labels(VGraph vgraph) {
      List<Rectangle2D> labels = new ArrayList<>();

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         Visualizable visual = vgraph.getVisual(i);

         if(visual instanceof VOText) {
            labels.add(visual.getBounds());
         }
         else if(visual instanceof ElementVO vo && vo.getVOText() != null) {
            labels.add(vo.getVOText().getBounds());
         }
      }

      return labels;
   }

   private static void assertLabels(List<Rectangle2D> expected, List<Rectangle2D> actual,
                                    String msg)
   {
      assertEquals(expected.size(), actual.size(), msg + ": labels");

      for(int i = 0; i < expected.size(); i++) {
         Rectangle2D e = expected.get(i);
         Rectangle2D a = actual.get(i);
         String at = msg + ": label " + i + ", expected " + rect(e) + " but was " + rect(a);

         assertEquals(e.getX(), a.getX(), LABEL_TOLERANCE, at);
         assertEquals(e.getY(), a.getY(), LABEL_TOLERANCE, at);
         assertEquals(e.getWidth(), a.getWidth(), LABEL_TOLERANCE, at);
         assertEquals(e.getHeight(), a.getHeight(), LABEL_TOLERANCE, at);
      }
   }

   private static String rect(Rectangle2D r) {
      return r == null ? "null" : String.format("%.3f,%.3f,%.3f,%.3f", r.getX(), r.getY(),
                                                r.getWidth(), r.getHeight());
   }

   private record Draw(EGraph graph, EGraph graph2, CountingElement main,
                       CountingElement expanded, VGraph vgraph, VGraph evgraph, double minW,
                       double minH, int ew, int eh)
   {
      // the graph's labels are not drawn, and its crowded label layout varies between runs
      String print() {
         return "graph " + RelationLayoutOnceTest.print(vgraph, minW, minH, false) +
            "\nexpanded " + ew + "x" + eh + " " +
            RelationLayoutOnceTest.print(evgraph, evgraph.getMinPlotWidth(),
                                         evgraph.getMinPlotHeight(), true);
      }

      List<Rectangle2D> labels() {
         return RelationLayoutOnceTest.labels(evgraph);
      }
   }

   // counts the plots and the ones whose nodes a layout moved off the corner they start at
   private static final class CountingElement extends RelationElement {
      CountingElement() {
         super("From", "To");
      }

      @Override
      public void createGeometry(DataSet data, GGraph graph) {
         super.createGeometry(data, graph);
         placed = isPlaced(graph);
         plots++;
         layouts += placed ? 1 : 0;
      }

      private boolean isPlaced(GGraph graph) {
         for(int i = 0; i < graph.getGeometryCount(); i++) {
            if(graph.getGeometry(i) instanceof RelationGeometry geo && geo.getElement() == this) {
               mxGeometry g = geo.getMxCell().getGeometry();

               if(g.getX() != 0 || g.getY() != 0) {
                  return true;
               }
            }
         }

         return false;
      }

      private int plots;
      private int layouts;
      private boolean placed;
   }
}
