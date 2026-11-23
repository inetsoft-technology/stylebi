/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.graph.geometry;

import inetsoft.graph.EGraph;
import inetsoft.graph.GGraph;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.LineElement;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.visual.LineVO;
import inetsoft.graph.visual.VisualObject;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests LineGeometry: tuple bookkeeping, min and max x, the compareTo order that decides the
 * painting order of stacked areas, the clearTuple contract and the hand-over to the visual
 * object.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LineGeometryTest {
   private static final double NAN = Double.NaN;
   private RectCoord coord;
   private GGraph graph;
   private LineElement elem;

   @BeforeEach
   void setUp() {
      LinearScale xs = new LinearScale("x");
      xs.setMin(0);
      xs.setMax(10);
      LinearScale ys = new LinearScale("y");
      ys.setMin(0);
      ys.setMax(100);
      coord = new RectCoord(xs, ys);
      EGraph egraph = new EGraph();
      egraph.setCoordinate(coord);
      graph = egraph.createGGraph(coord, new DefaultDataSet(new Object[][] { { "x", "y" } }));
      elem = new LineElement("x", "y");
   }

   @Test
   @DisplayName("addTuple keeps the insertion order and counts the tuples")
   void addTupleOrder() {
      LineGeometry line = line(p(1, 5), p(2, 6), p(3, 7));

      assertEquals(3, line.getTupleCount());
      assertArrayEquals(p(1, 5), line.getTuple(0));
      assertArrayEquals(p(2, 6), line.getTuple(1));
      assertArrayEquals(p(3, 7), line.getTuple(2));
   }

   @Test
   @DisplayName("removeTuple returns the removed tuple and shifts the later ones")
   void removeTuple() {
      LineGeometry line = line(p(1, 5), p(2, 6), p(3, 7));

      assertArrayEquals(p(2, 6), line.removeTuple(1));
      assertEquals(2, line.getTupleCount());
      assertArrayEquals(p(3, 7), line.getTuple(1));
   }

   @Test
   @DisplayName("min and max x are taken over all tuples, whatever their order")
   void minMaxX() {
      LineGeometry line = line(p(3, 1), p(1, 1), p(2, 1));

      assertEquals(1, line.getMinX(coord), 1e-9);
      assertEquals(3, line.getMaxX(coord), 1e-9);
   }

   @Test
   @DisplayName("min and max x of an empty line are Double.MAX_VALUE (pinned behavior)")
   void minMaxXEmptyLine() {
      LineGeometry line = line();

      assertEquals(Double.MAX_VALUE, line.getMinX(coord));
      assertEquals(Double.MAX_VALUE, line.getMaxX(coord));
   }

   // ---------------------------------------------------------------- drawing order

   @Test
   @DisplayName("the higher line sorts first so that it is drawn first and not covered")
   void higherLineFirst() {
      LineGeometry a = line(p(1, 5), p(2, 5));
      LineGeometry b = line(p(1, 10), p(2, 10));

      assertEquals(1, a.compareTo(b));
      assertEquals(-1, b.compareTo(a));
   }

   @Test
   @DisplayName("the first shared x with different y decides the order")
   void firstSharedXDecides() {
      LineGeometry a = line(p(1, 5), p(2, 1));
      LineGeometry b = line(p(1, 3), p(2, 9));

      assertEquals(-1, a.compareTo(b));
   }

   @Test
   @DisplayName("a tie at the first x moves the decision to the next x")
   void tieMovesToNextX() {
      LineGeometry a = line(p(1, 5), p(2, 9));
      LineGeometry b = line(p(1, 5), p(2, 3));

      assertEquals(-1, a.compareTo(b));
   }

   @Test
   @DisplayName("lines with different x grids are compared at the x they share")
   void differentXGridsAlignedByX() {
      LineGeometry a = line(p(0, 1), p(1, 9));
      LineGeometry b = line(p(1, 5));

      assertEquals(-1, a.compareTo(b));
   }

   @Test
   @DisplayName("lines that start at different x are aligned by x even when the other line is taller later")
   void differentXGridsAlignedByXTallerLater() {
      LineGeometry a = line(p(0, 1), p(1, 9));
      LineGeometry b = line(p(1, 5), p(2, 20));

      assertEquals(-1, a.compareTo(b));
      assertEquals(1, b.compareTo(a));
   }

   @Test
   @DisplayName("a null at the first x puts that line after the other, and the reverse before")
   void nullAtFirstX() {
      LineGeometry a = line(p(1, NAN), p(2, 5));
      LineGeometry b = line(p(1, 3), p(2, 5));

      assertEquals(1, a.compareTo(b));
      assertEquals(-1, b.compareTo(a));
   }

   @Test
   @DisplayName("nulls at the same x are skipped, the next x decides")
   void bothNullAtFirstX() {
      LineGeometry a = line(p(1, NAN), p(2, 7));
      LineGeometry b = line(p(1, NAN), p(2, 2));

      assertEquals(-1, a.compareTo(b));
   }

   @Test
   @DisplayName("lines with no shared x: the taller line sorts first")
   void noSharedXTallerFirst() {
      LineGeometry a = line(p(0, 5));
      LineGeometry b = line(p(1, 9));

      assertEquals(1, a.compareTo(b));
      assertEquals(-1, b.compareTo(a));
   }

   @Test
   @DisplayName("identical lines compare equal, also with a null, and a line equals itself")
   void identicalLinesEqual() {
      LineGeometry a = line(p(1, 5), p(2, 6));
      LineGeometry b = line(p(1, 5), p(2, 6));
      LineGeometry c = line(p(1, NAN), p(2, 6));
      LineGeometry d = line(p(1, NAN), p(2, 6));

      assertEquals(0, a.compareTo(b));
      assertEquals(0, a.compareTo(a));
      assertEquals(0, c.compareTo(d));
      assertEquals(0, c.compareTo(c));
   }

   @Test
   @DisplayName("compareTo is antisymmetric for random lines on one x grid with ties and nulls")
   void compareToAntisymmetric() {
      Random rnd = new Random(6013);

      for(int i = 0; i < 200; i++) {
         LineGeometry a = randomLine(rnd, true);
         LineGeometry b = randomLine(rnd, true);

         assertEquals(Integer.signum(a.compareTo(b)), -Integer.signum(b.compareTo(a)),
                      "pair " + i);
      }
   }

   @Test
   @DisplayName("sorting random lines never raises the first-x y along the list")
   void sortedOrderNeverIncreasesAtFirstX() {
      Random rnd = new Random(6014);
      List<LineGeometry> lines = new ArrayList<>();

      for(int i = 0; i < 20; i++) {
         lines.add(randomLine(rnd, false));
      }

      Collections.sort(lines);

      for(int i = 1; i < lines.size(); i++) {
         double prev = lines.get(i - 1).getTuple(0)[1];
         double cur = lines.get(i).getTuple(0)[1];
         assertTrue(cur <= prev, "position " + i + ": " + cur + " after " + prev);
      }
   }

   // ---------------------------------------------------------------- contract

   @Test
   @DisplayName("clearTuple keeps the tuples of the line (the radar tooltip needs them)")
   void clearTupleKeepsLine() {
      LineGeometry line = line(p(1, 5), p(2, 6));
      line.clearTuple();

      assertEquals(2, line.getTupleCount());
      assertArrayEquals(p(2, 6), line.getTuple(1));
   }

   @Test
   @DisplayName("createVisual hands the indexes and the closed flag over to the line visual")
   void createVisualCarriesIndexes() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "x", "y" }, { 1.0, 10.0 }, { 2.0, 20.0 } });
      EGraph egraph = new EGraph();
      LineElement e = new LineElement("x", "y");
      egraph.addElement(e);
      egraph.setCoordinate(coord);
      GGraph g = egraph.createGGraph(coord, ds);
      LineGeometry geom = (LineGeometry) g.getGeometry(0);

      geom.setColIndex(2);
      geom.setSubRowIndexes(new int[] { 1, 0 });
      geom.setRowIndexes(new int[] { 1, 0 });
      geom.setClosed(true);
      VisualObject vo = geom.createVisual(coord);

      LineVO line = assertInstanceOf(LineVO.class, vo);
      assertEquals(2, line.getColIndex());
      assertArrayEquals(new int[] { 1, 0 }, line.getSubRowIndexes());
      assertArrayEquals(new int[] { 1, 0 }, line.getRowIndexes());
      assertTrue(line.isClosed());
   }

   // ---------------------------------------------------------------- helpers

   private static double[] p(double x, double y) {
      return new double[] { x, y };
   }

   private LineGeometry line(double[]... tuples) {
      LineGeometry geom = new LineGeometry(elem, graph, "y", null);

      for(double[] t : tuples) {
         geom.addTuple(t);
      }

      return geom;
   }

   private LineGeometry randomLine(Random rnd, boolean withNull) {
      double[][] tuples = new double[4][];

      for(int i = 0; i < tuples.length; i++) {
         int pick = rnd.nextInt(withNull ? 4 : 3);
         tuples[i] = p(i, pick == 3 ? NAN : pick + 1);
      }

      return line(tuples);
   }
}
