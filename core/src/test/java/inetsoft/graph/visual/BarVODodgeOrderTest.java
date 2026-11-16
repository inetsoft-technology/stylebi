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
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.geometry.ElementGeometry;
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
 * A horizontal bar can list its first category, and the first series of a cluster, at the top.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class BarVODodgeOrderTest {
   @Test
   void aReversedScaleListsTheFirstCategoryAtTheTop() {
      Object[][] rows = singleRows(3);
      List<BarVO> bars = render(single(true, false), rows);
      Map<Object, Rectangle2D> boxes = boxes(rows, bars);

      assertEquals(3, bars.size(), "premise: one bar per category");
      assertTrue(boxes.get("S1").getCenterY() > boxes.get("S2").getCenterY());
      assertTrue(boxes.get("S2").getCenterY() > boxes.get("S3").getCenterY());
   }

   @Test
   void aReversedDodgePutsTheFirstSeriesOnTop() {
      for(boolean flag : new boolean[] { true, false }) {
         Object[][] rows = clusterRows(3);
         List<BarVO> bars = render(cluster(true, flag), rows);
         assertEquals(6, bars.size(), "premise: two bars per category");

         for(List<BarVO> pair : group(rows, bars).values()) {
            double d = series(rows, pair, "P0").getPath().getBounds2D().getCenterY() -
               series(rows, pair, "P1").getPath().getBounds2D().getCenterY();

            if(flag) {
               assertTrue(d > 0, "the first series is on top, " + d);
            }
            else {
               assertTrue(d < 0, "today the first series is below, " + d);
            }
         }
      }
   }

   @Test
   void aReversedDodgeKeepsTheClusterTouchingAndCentred() {
      Object[][] rows = clusterRows(80);

      for(boolean flag : new boolean[] { false, true }) {
         List<BarVO> bars = render(cluster(true, flag), rows);
         Map<Object, Rectangle2D> slots = slots(rows, bars);
         assertEquals(160, bars.size(), "premise: two bars per category");
         double slot = bars.get(0).getSizeFactor();
         assertTrue(slot < 6, "premise: two 3px bars overflow the slot " + slot);

         for(Map.Entry<Object, List<BarVO>> cat : group(rows, bars).entrySet()) {
            Rectangle2D a = series(rows, cat.getValue(), "P0").getPath().getBounds2D();
            Rectangle2D b = series(rows, cat.getValue(), "P1").getPath().getBounds2D();
            Rectangle2D top = a.getCenterY() > b.getCenterY() ? a : b;
            Rectangle2D low = top == a ? b : a;

            assertEquals(slot / 2, a.getHeight(), 0.01, "thickness " + flag);
            assertEquals(slot / 2, b.getHeight(), 0.01, "thickness " + flag);
            assertEquals(0, top.getMinY() - low.getMaxY(), 0.01, "the pair touches " + flag);
            assertEquals(slots.get(cat.getKey()).getCenterY(),
                         (top.getMaxY() + low.getMinY()) / 2, 0.5, "the pair is centred " + flag);
         }
      }
   }

   @Test
   void aReversedDodgeCentresAClusterNarrowerThanItsSlot() {
      Object[][] rows = clusterRows(3);

      for(boolean flag : new boolean[] { false, true }) {
         List<BarVO> bars = render(cluster(true, flag, false), rows);
         Map<Object, Rectangle2D> slots = slots(rows, bars);
         assertEquals(6, bars.size(), "premise: two bars per category");

         for(Map.Entry<Object, List<BarVO>> cat : group(rows, bars).entrySet()) {
            Rectangle2D a = series(rows, cat.getValue(), "P0").getPath().getBounds2D();
            Rectangle2D b = series(rows, cat.getValue(), "P1").getPath().getBounds2D();
            Rectangle2D top = a.getCenterY() > b.getCenterY() ? a : b;
            Rectangle2D low = top == a ? b : a;
            Rectangle2D slot = slots.get(cat.getKey());

            assertEquals(32, a.getHeight(), 0.5, "thickness " + flag);
            assertEquals(32, b.getHeight(), 0.5, "thickness " + flag);
            assertTrue(slot.getHeight() > 2 * a.getHeight() + 1, "premise: the pair is narrower than the slot");
            double gap = top.getMinY() - low.getMaxY();
            assertTrue(gap >= -0.5 && gap <= 1.5, "the pair touches, gap " + gap);
            assertEquals(slot.getCenterY(), (top.getMaxY() + low.getMinY()) / 2, 0.5,
                         "the pair is centred " + flag);
         }
      }
   }

   @Test
   void aReversedDodgeKeepsBrushedBarsInsideTheirBase() {
      String all = ElementVO.ALL_PREFIX + "Sales";
      Object[][] rows = new Object[161][];
      rows[0] = new Object[] { "State", "Series", all, "Sales" };

      for(int i = 0; i < 160; i++) {
         rows[i + 1] = new Object[] { "S" + (i / 2), "P" + (i % 2), 100.0 + i, 50.0 };
      }

      IntervalElement base = new IntervalElement("State", all);
      IntervalElement brushed = new IntervalElement("State", "Sales");

      for(IntervalElement elem : new IntervalElement[] { base, brushed }) {
         elem.setColorFrame(new CategoricalColorFrame("Series"));
         elem.setSizeFrame(new StaticSizeFrame(15));
         elem.setWidthClamp(true);
         elem.setDodgeReversed(true);
      }

      brushed.setHint("overlay", base);
      base.setHint("overlaid", brushed);
      RectCoord coord = new RectCoord(reversed(), new LinearScale(all, "Sales"));
      coord.transpose();
      List<BarVO> bars = render(graph(coord, base, brushed), rows);
      assertEquals(320, bars.size(), "premise: a base and a brushed bar per row");

      for(BarVO under : bars) {
         if(element(under) != base) {
            continue;
         }

         BarVO over = bars.stream()
            .filter(b -> element(b) == brushed && b.getSubRowIndex() == under.getSubRowIndex())
            .findFirst().orElseThrow();
         Rectangle2D b0 = under.getPath().getBounds2D();
         Rectangle2D b1 = over.getPath().getBounds2D();

         assertEquals(b0.getHeight() * 0.7, b1.getHeight(), 0.05, "brushed thickness");
         assertEquals(b0.getCenterY(), b1.getCenterY(), 0.05, "brushed centre");
      }
   }

   @Test
   void aStackKeepsItsSegmentOrder() {
      Object[][] rows = new Object[4][];
      rows[0] = new Object[] { "State", "m1", "m2", "m3" };

      for(int i = 1; i < rows.length; i++) {
         rows[i] = new Object[] { "S" + i, 100.0 * i, 50.0 * i, 25.0 * i };
      }

      IntervalElement elem = new IntervalElement("State", "m1");
      elem.addVar("m2");
      elem.addVar("m3");
      elem.setCollisionModifier(GraphElement.STACK_SYMMETRIC);
      elem.setDodgeReversed(true);
      RectCoord coord = new RectCoord(reversed(), new LinearScale("m1", "m2", "m3"));
      coord.transpose();
      List<BarVO> bars = render(graph(coord, elem), rows);
      assertEquals(9, bars.size(), "premise: three segments per stack");

      for(List<BarVO> stack : group(rows, bars).values()) {
         assertEquals(3, stack.size(), "premise: a stack per slot");
         double prev = -Double.MAX_VALUE;

         for(String var : new String[] { "m1", "m2", "m3" }) {
            BarVO seg = stack.stream().filter(b -> var.equals(b.getMeasureName()))
               .findFirst().orElseThrow();
            double x = seg.getPath().getBounds2D().getMinX();
            assertTrue(x >= prev - 0.01, "segments run from the base outward, " + var + " " + x);
            prev = x;
         }
      }
   }

   @Test
   void theFlagOffChangesNothing() {
      // the flag-off order is pinned by aReversedDodgePutsTheFirstSeriesOnTop
      assertFalse(new IntervalElement("State", "Sales").isDodgeReversed(), "default off");
      IntervalElement on = new IntervalElement("State", "Sales");
      on.setDodgeReversed(true);
      assertFalse(on.equalsContent(new IntervalElement("State", "Sales")));
   }

   private static CategoricalScale reversed() {
      CategoricalScale scale = new CategoricalScale("State");
      scale.setReversed(true);
      return scale;
   }

   private static EGraph single(boolean reversed, boolean flag) {
      IntervalElement elem = new IntervalElement("State", "Sales");
      elem.setDodgeReversed(flag);
      RectCoord coord = new RectCoord(reversed ? reversed() : new CategoricalScale("State"),
                                      new LinearScale("Sales"));
      coord.transpose();
      return graph(coord, elem);
   }

   // two series dodged per category, horizontal
   private static EGraph cluster(boolean reversedScale, boolean flag) {
      return cluster(reversedScale, flag, true);
   }

   private static EGraph cluster(boolean reversedScale, boolean flag, boolean sized) {
      IntervalElement elem = new IntervalElement("State", "Sales");
      elem.setColorFrame(new CategoricalColorFrame("Series"));
      if(sized) {
         elem.setSizeFrame(new StaticSizeFrame(15));
      }

      elem.setCollisionModifier(GraphElement.MOVE_DODGE | GraphElement.MOVE_CENTER);
      elem.setWidthClamp(true);
      elem.setDodgeReversed(flag);
      RectCoord coord = new RectCoord(reversedScale ? reversed() : new CategoricalScale("State"),
                                      new LinearScale("Sales"));
      coord.transpose();
      return graph(coord, elem);
   }

   private static Object[][] clusterRows(int categories) {
      Object[][] rows = new Object[2 * categories + 1][];
      rows[0] = new Object[] { "State", "Series", "Sales" };

      for(int i = 0; i < 2 * categories; i++) {
         rows[i + 1] = new Object[] { "S" + (i / 2 + 1), "P" + (i % 2), 100.0 + i };
      }

      return rows;
   }

   private static Object[][] singleRows(int count) {
      Object[][] rows = new Object[count + 1][];
      rows[0] = new Object[] { "State", "Sales" };

      for(int i = 1; i < rows.length; i++) {
         rows[i] = new Object[] { "S" + i, 100.0 * i };
      }

      return rows;
   }

   private static EGraph graph(RectCoord coord, IntervalElement... elems) {
      EGraph graph = new EGraph();

      for(IntervalElement elem : elems) {
         graph.addElement(elem);
      }

      graph.setCoordinate(coord);
      return graph;
   }

   private static List<BarVO> render(EGraph graph, Object[][] rows) {
      List<BarVO> bars = new ArrayList<>();
      VGraph vgraph = Plotter.getPlotter(graph)
         .plotAndLayout(new DefaultDataSet(rows), 0, 0, 400, 240);

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof BarVO bar) {
            bars.add(bar);
         }
      }

      return bars;
   }

   private static Object category(Object[][] rows, BarVO bar) {
      return rows[bar.getSubRowIndex() + 1][0];
   }

   private static BarVO series(Object[][] rows, List<BarVO> bars, String name) {
      return bars.stream().filter(b -> rows[b.getSubRowIndex() + 1][1].equals(name))
         .findFirst().orElseThrow();
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

   // the drawn extent of each category's bars
   private static Map<Object, Rectangle2D> boxes(Object[][] rows, List<BarVO> bars) {
      Map<Object, Rectangle2D> boxes = new HashMap<>();

      for(BarVO bar : bars) {
         boxes.merge(category(rows, bar), bar.getPath().getBounds2D(), Rectangle2D::createUnion);
      }

      return boxes;
   }

   private static GraphElement element(BarVO bar) {
      return ((ElementGeometry) bar.getGeometry()).getElement();
   }
}
