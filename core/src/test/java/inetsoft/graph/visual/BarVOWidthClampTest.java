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
package inetsoft.graph.visual;

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.StaticSizeFrame;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.FacetCoord;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.geometry.ElementGeometry;
import inetsoft.graph.internal.GTool;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A width-clamped bar draws 3 to 32 screen pixels thick, scaled about its cluster's centre so a
 * cluster stays touching and centred on its slot.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class BarVOWidthClampTest {
   @Test
   void aWideSlotCapsAt32() {
      List<BarVO> bars = render(barChart(true, false), barRows(3));
      Map<Object, Rectangle2D> slots = slots(barRows(3), bars);
      assertEquals(3, bars.size(), "premise: one bar per category");

      for(BarVO bar : bars) {
         Rectangle2D box = bar.getPath().getBounds2D();
         assertTrue(bar.getSizeFactor() / 2 > 32, "premise: a wide slot " + bar.getSizeFactor());
         assertEquals(32, box.getWidth(), 0.5, "thickness");
         assertEquals(slots.get(category(barRows(3), bar)).getCenterX(), box.getCenterX(),
                      0.5, "centred on its slot");
      }
   }

   @Test
   void aNarrowSlotFloorsAt3() {
      List<BarVO> bars = render(barChart(true, false), barRows(150));
      assertEquals(150, bars.size(), "premise: one bar per category");

      for(BarVO bar : bars) {
         double t = bar.getPath().getBounds2D().getWidth();
         assertTrue(bar.getSizeFactor() / 2 < 3, "premise: a narrow slot " + bar.getSizeFactor());
         assertTrue(t >= 3 - 0.01, "thickness " + t);
      }
   }

   @Test
   void aClusterStaysTouchingAndCentred() {
      Object[][] rows = new Object[7][];
      rows[0] = new Object[] { "State", "Series", "Sales" };

      for(int i = 0; i < 6; i++) {
         rows[i + 1] = new Object[] { "S" + (i / 2), "P" + (i % 2), 100.0 + 10 * i };
      }

      IntervalElement elem = new IntervalElement("State", "Sales");
      elem.setColorFrame(new CategoricalColorFrame("Series"));
      elem.setCollisionModifier(GraphElement.MOVE_DODGE | GraphElement.MOVE_CENTER);
      elem.setWidthClamp(true);
      List<BarVO> bars = render(graph(new RectCoord(new CategoricalScale("State"),
                                                    new LinearScale("Sales")), elem), rows);
      Map<Object, Rectangle2D> slots = slots(rows, bars);
      assertEquals(6, bars.size(), "premise: two bars per category");

      for(Map.Entry<Object, List<BarVO>> slot : group(rows, bars).entrySet()) {
         List<BarVO> pair = slot.getValue();
         assertEquals(2, pair.size(), "premise: a pair per slot");
         pair.sort(Comparator.comparingDouble(b -> b.getPath().getBounds2D().getX()));
         Rectangle2D left = pair.get(0).getPath().getBounds2D();
         Rectangle2D right = pair.get(1).getPath().getBounds2D();

         assertEquals(32, left.getWidth(), 0.5, "left thickness");
         assertEquals(32, right.getWidth(), 0.5, "right thickness");
         double gap = right.getMinX() - left.getMaxX();
         assertTrue(gap >= -0.5 && gap <= 1.5, "the pair touches, gap " + gap);
         assertEquals(slots.get(slot.getKey()).getCenterX(),
                      (left.getMinX() + right.getMaxX()) / 2, 0.5, "the pair is centred");
      }
   }

   @Test
   void aHorizontalBarClampsItsHeight() {
      List<BarVO> bars = render(barChart(true, true), barRows(3));
      Map<Object, Rectangle2D> slots = slots(barRows(3), bars);
      assertEquals(3, bars.size(), "premise: one bar per category");

      for(BarVO bar : bars) {
         Rectangle2D box = bar.getPath().getBounds2D();
         assertTrue(bar.getSizeFactor() / 2 > 32, "premise: a wide slot " + bar.getSizeFactor());
         assertEquals(32, box.getHeight(), 0.5, "thickness");
         assertEquals(slots.get(category(barRows(3), bar)).getCenterY(), box.getCenterY(),
                      0.5, "centred on its slot");
      }
   }

   @Test
   void aStackClampsEverySegmentAlike() {
      Object[][] rows = new Object[4][];
      rows[0] = new Object[] { "State", "m1", "m2", "m3" };

      for(int i = 1; i < rows.length; i++) {
         rows[i] = new Object[] { "S" + i, 100.0 * i, 50.0 * i, 25.0 * i };
      }

      IntervalElement elem = new IntervalElement("State", "m1");
      elem.addVar("m2");
      elem.addVar("m3");
      elem.setCollisionModifier(GraphElement.STACK_SYMMETRIC);
      elem.setWidthClamp(true);
      List<BarVO> bars = render(graph(new RectCoord(new CategoricalScale("State"),
                                                    new LinearScale("m1", "m2", "m3")), elem),
                                rows);
      assertEquals(9, bars.size(), "premise: three segments per stack");

      for(List<BarVO> stack : group(rows, bars).values()) {
         assertEquals(3, stack.size(), "premise: a stack per slot");
         Rectangle2D first = stack.get(0).getPath().getBounds2D();

         for(BarVO seg : stack) {
            Rectangle2D box = seg.getPath().getBounds2D();
            assertEquals(32, box.getWidth(), 0.5, "segment thickness");
            assertEquals(first.getX(), box.getX(), 0.01, "segment x");
         }
      }
   }

   @Test
   void aFacetedChartStaysClamped() {
      Object[][] rows = new Object[7][];
      rows[0] = new Object[] { "Region", "State", "Sales" };

      for(int i = 0; i < 6; i++) {
         rows[i + 1] = new Object[] { "R" + (i / 3), "S" + (i % 3), 100.0 + 10 * i };
      }

      IntervalElement elem = new IntervalElement("State", "Sales");
      elem.setWidthClamp(true);
      RectCoord outer = new RectCoord(new CategoricalScale("Region"),
                                      GTool.createFakeScale(null));
      FacetCoord coord = new FacetCoord(outer, new RectCoord(new CategoricalScale("State"),
                                                             new LinearScale("Sales")));
      // wide enough that the facet bars need the cap
      List<BarVO> bars = render(graph(coord, elem), rows, 800);
      assertEquals(6, bars.size(), "premise: one bar per region and state");

      for(BarVO bar : bars) {
         Rectangle2D unclamped = bar.getScreenTransform().createTransformedShape(bar.getShape())
            .getBounds2D();
         double t = bar.getPath().getBounds2D().getWidth();
         assertTrue(unclamped.getWidth() > 32, "premise: a wide facet slot " + unclamped);
         assertTrue(t >= 3 - 0.01 && t <= 32 + 0.01, "thickness " + t);
      }
   }

   @Test
   void theFacetRescaleClampsTheRescaledWidth() {
      List<BarVO> classic = render(barChart(false, false), barRows(3));
      List<BarVO> bars = render(barChart(true, false), barRows(3));

      for(int i = 0; i < bars.size(); i++) {
         BarVO bar = bars.get(i);
         double unclamped = classic.get(i).getPath().getBounds2D().getWidth();
         assertTrue(unclamped * 0.5 < 32 && unclamped * 0.5 > 3, "premise: half is in range");

         bar.scaleToSizeFactor(bar.getSizeFactor() * 0.5);
         assertEquals(unclamped * 0.5, bar.getPath().getBounds2D().getWidth(), 0.5,
                      "half of the unclamped width");

         bar.scaleToSizeFactor(bar.getSizeFactor() * 0.01);
         assertEquals(3, bar.getPath().getBounds2D().getWidth(), 0.01, "floored");
      }
   }

   @Test
   void aBrushedBarStaysInsideItsClampedBase() {
      String all = ElementVO.ALL_PREFIX + "Sales";
      Object[][] rows = new Object[7][];
      rows[0] = new Object[] { "State", "Series", all, "Sales" };

      for(int i = 0; i < 6; i++) {
         rows[i + 1] = new Object[] { "S" + (i / 2), "P" + (i % 2), 100.0 + 10 * i, 50.0 };
      }

      for(boolean clamp : new boolean[] { false, true }) {
         IntervalElement base = new IntervalElement("State", all);
         IntervalElement brushed = new IntervalElement("State", "Sales");

         for(IntervalElement elem : new IntervalElement[] { base, brushed }) {
            elem.setColorFrame(new CategoricalColorFrame("Series"));
            elem.setWidthClamp(clamp);
         }

         brushed.setHint("overlay", base);
         base.setHint("overlaid", brushed);
         List<BarVO> bars = render(graph(new RectCoord(new CategoricalScale("State"),
                                                       new LinearScale(all, "Sales")),
                                         base, brushed), rows);
         assertEquals(12, bars.size(), "premise: a base and a brushed bar per row");

         for(BarVO under : bars) {
            if(element(under) != base) {
               continue;
            }

            BarVO over = bars.stream()
               .filter(b -> element(b) == brushed && b.getSubRowIndex() == under.getSubRowIndex())
               .findFirst().orElseThrow();
            Rectangle2D b0 = under.getPath().getBounds2D();
            Rectangle2D b1 = over.getPath().getBounds2D();

            if(clamp) {
               assertEquals(32, b0.getWidth(), 0.5, "base thickness");
            }

            assertEquals(b0.getWidth() * 0.7, b1.getWidth(), 0.5, "brushed thickness, " + clamp);
            assertEquals(b0.getCenterX(), b1.getCenterX(), 0.5, "brushed centre, " + clamp);
         }
      }
   }

   @Test
   void classicWidthsUnchanged() {
      for(BarVO bar : render(barChart(false, false), barRows(3))) {
         assertEquals(0.5 * bar.getSizeFactor(), bar.getPath().getBounds2D().getWidth(), 0.5,
                      "no size frame");
      }

      EGraph graph = barChart(false, false);
      graph.getElement(0).setSizeFrame(new StaticSizeFrame(15));

      for(BarVO bar : render(graph, barRows(3))) {
         assertEquals((1 + 29.0 * 15 / 30) / 30 * bar.getSizeFactor(),
                      bar.getPath().getBounds2D().getWidth(), 0.5, "static size 15");
      }
   }

   private static EGraph barChart(boolean clamp, boolean transposed) {
      IntervalElement elem = new IntervalElement("State", "Sales");
      elem.setWidthClamp(clamp);
      RectCoord coord = new RectCoord(new CategoricalScale("State"), new LinearScale("Sales"));

      if(transposed) {
         coord.transpose();
      }

      return graph(coord, elem);
   }

   private static Object[][] barRows(int count) {
      Object[][] rows = new Object[count + 1][];
      rows[0] = new Object[] { "State", "Sales" };

      for(int i = 1; i < rows.length; i++) {
         rows[i] = new Object[] { "S" + i, 100.0 * i };
      }

      return rows;
   }

   private static EGraph graph(Coordinate coord, IntervalElement... elems) {
      EGraph graph = new EGraph();

      for(IntervalElement elem : elems) {
         graph.addElement(elem);
      }

      graph.setCoordinate(coord);
      return graph;
   }

   private static List<BarVO> render(EGraph graph, Object[][] rows) {
      return render(graph, rows, 400);
   }

   private static List<BarVO> render(EGraph graph, Object[][] rows, int width) {
      List<BarVO> bars = new ArrayList<>();
      collect(Plotter.getPlotter(graph)
                 .plotAndLayout(new DefaultDataSet(rows), 0, 0, width, 240), bars);
      return bars;
   }

   // the bars of a graph and of its facets
   private static void collect(VGraph vgraph, List<BarVO> bars) {
      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof BarVO bar) {
            bars.add(bar);
         }
         else if(vgraph.getVisual(i) instanceof GraphVO sub) {
            collect(sub.getVGraph(), bars);
         }
      }
   }

   // the first column of the bar's data row
   private static Object category(Object[][] rows, BarVO bar) {
      return rows[bar.getSubRowIndex() + 1][0];
   }

   private static Map<Object, List<BarVO>> group(Object[][] rows, List<BarVO> bars) {
      Map<Object, List<BarVO>> groups = new LinkedHashMap<>();

      for(BarVO bar : bars) {
         groups.computeIfAbsent(category(rows, bar), k -> new ArrayList<>()).add(bar);
      }

      return groups;
   }

   // the screen extent of each category's unclamped bars, which classic draws on its slot
   private static Map<Object, Rectangle2D> slots(Object[][] rows, List<BarVO> bars) {
      Map<Object, Rectangle2D> slots = new HashMap<>();

      for(BarVO bar : bars) {
         Rectangle2D box = bar.getScreenTransform().createTransformedShape(bar.getShape())
            .getBounds2D();
         slots.merge(category(rows, bar), box, Rectangle2D::createUnion);
      }

      return slots;
   }

   private static GraphElement element(BarVO bar) {
      return ((ElementGeometry) bar.getGeometry()).getElement();
   }
}
