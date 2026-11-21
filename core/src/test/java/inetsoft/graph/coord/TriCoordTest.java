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

import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.geom.Point2D;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Barycentric position of the ternary coordinate; the axis length is 1000.
 */
@Tag("core")
class TriCoordTest {
   private static final double EPS = 1e-6;
   private static final double SIN60 = Math.sin(Math.PI / 3);

   private static LinearScale scale(double min, double max) {
      LinearScale s = new LinearScale("v");
      s.setMin(min);
      s.setMax(max);
      return s;
   }

   private static TriCoord coord(double min, double max) {
      return new TriCoord(scale(min, max));
   }

   private static void assertPoint(double x, double y, Point2D p) {
      assertEquals(x, p.getX(), EPS, "x of " + p);
      assertEquals(y, p.getY(), EPS, "y of " + p);
   }

   @Test
   @DisplayName("the x vertex (1,0,0) is the bottom right corner")
   void vertexX() {
      assertPoint(1000, 0, coord(0, 1).getPosition(new double[] { 1, 0, 0 }));
   }

   @Test
   @DisplayName("the y vertex (0,1,0) is the apex")
   void vertexY() {
      assertPoint(500, 1000, coord(0, 1).getPosition(new double[] { 0, 1, 0 }));
   }

   @Test
   @DisplayName("the z vertex (0,0,1) is the bottom left corner")
   void vertexZ() {
      assertPoint(0, 0, coord(0, 1).getPosition(new double[] { 0, 0, 1 }));
   }

   @Test
   @DisplayName("equal shares map to the centre")
   void centre() {
      double t = 1.0 / 3;
      assertPoint(500, 1000.0 / 3, coord(0, 1).getPosition(new double[] { t, t, t }));
   }

   @Test
   @DisplayName("edge midpoints are the midpoints of the vertex pairs")
   void edgeMidpoints() {
      TriCoord c = coord(0, 1);
      assertPoint(750, 500, c.getPosition(new double[] { .5, .5, 0 }));
      assertPoint(500, 0, c.getPosition(new double[] { .5, 0, .5 }));
      assertPoint(250, 500, c.getPosition(new double[] { 0, .5, .5 }));
   }

   @Test
   @DisplayName("a sum other than 1 only counts as ratios")
   void sumNotOne() {
      assertPoint(500, 1000.0 / 3, coord(0, 1).getPosition(new double[] { .2, .2, .2 }));
   }

   static Stream<Arguments> scaleInvariance() {
      Stream.Builder<Arguments> b = Stream.builder();

      for(double[] t : new double[][] { { 10, 30, 60 }, { 5, 5, 90 }, { 70, 20, 10 } }) {
         for(double k : new double[] { 0.5, 2, 10 }) {
            b.add(Arguments.of(t, k));
         }
      }

      return b.build();
   }

   @ParameterizedTest
   @MethodSource("scaleInvariance")
   @DisplayName("scaling all three values by a factor keeps the position")
   void scaleInvariant(double[] t, double k) {
      TriCoord c = coord(0, 100);
      Point2D expected = c.getPosition(t);
      Point2D actual = c.getPosition(new double[] { t[0] * k, t[1] * k, t[2] * k });
      assertPoint(expected.getX(), expected.getY(), actual);
   }

   @Test
   @DisplayName("the position is affine on tuples of constant sum, negative component included")
   void affineOnConstantSum() {
      TriCoord c = coord(0, 100);
      double[][][] pairs = {
         { { 10, 60, 30 }, { 50, 20, 30 } },
         { { -10, 60, 50 }, { 70, 10, 20 } },
         { { 100, 0, 0 }, { 0, 0, 100 } }
      };

      for(double[][] pair : pairs) {
         double[] a = pair[0];
         double[] b = pair[1];
         double[] mid = { (a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2 };
         Point2D pa = c.getPosition(a);
         Point2D pb = c.getPosition(b);
         assertPoint((pa.getX() + pb.getX()) / 2, (pa.getY() + pb.getY()) / 2,
                     c.getPosition(mid));
      }
   }

   @Test
   @DisplayName("a scale starting at 10 maps its minimum to zero share like a scale from 0")
   void scaleWithOffset() {
      TriCoord c = coord(10, 110);
      assertPoint(1000, 0, c.getPosition(new double[] { 110, 10, 10 }));
      assertPoint(500, 1000, c.getPosition(new double[] { 10, 110, 10 }));
      assertPoint(0, 0, c.getPosition(new double[] { 10, 10, 110 }));
   }

   @Test
   @DisplayName("without a scale values above 1 are capped like the unit scale")
   void noScale() {
      TriCoord c = new TriCoord();
      assertPoint(1000, 0, c.getPosition(new double[] { 1, 0, 0 }));
      assertPoint(750, 500, c.getPosition(new double[] { .5, .5, 0 }));
   }

   @Test
   @DisplayName("all values at the scale minimum do not throw (a zero-sum tuple has no position)")
   void allAtMinimum() {
      assertDoesNotThrow(() -> coord(0, 1).getPosition(new double[] { 0, 0, 0 }));
   }

   @Test
   @DisplayName("cyclic permutations keep the distance to the centroid in the equilateral metric")
   void cyclicSymmetry() {
      TriCoord c = coord(0, 1);
      Point2D centre = c.getPosition(new double[] { 1.0 / 3, 1.0 / 3, 1.0 / 3 });

      for(double[] t : new double[][] { { .6, .3, .1 }, { .2, .2, .6 } }) {
         double[] d = new double[3];

         for(int i = 0; i < 3; i++) {
            Point2D p = c.getPosition(new double[] { t[i], t[(i + 1) % 3], t[(i + 2) % 3] });
            d[i] = Math.hypot(p.getX() - centre.getX(), (p.getY() - centre.getY()) * SIN60);
         }

         assertEquals(d[0], d[1], 1e-6);
         assertEquals(d[0], d[2], 1e-6);
      }
   }

   @Test
   @DisplayName("only the last three values of a longer tuple are used")
   void extraLeadingDimensions() {
      TriCoord c = coord(0, 1);
      assertPoint(1000, 0, c.getPosition(new double[] { 9, 1, 0, 0 }));
      Point2D expected = c.getPosition(new double[] { .2, .2, .6 });
      assertPoint(expected.getX(), expected.getY(),
                  c.getPosition(new double[] { 9, 8, .2, .2, .6 }));
   }

   @ParameterizedTest
   @org.junit.jupiter.params.provider.ValueSource(ints = { 0, 1, 2 })
   @DisplayName("a tuple with fewer than three values is rejected")
   void tooFewDimensions(int length) {
      TriCoord c = coord(0, 1);
      assertThrows(RuntimeException.class, () -> c.getPosition(new double[length]));
   }

   @Test
   @DisplayName("an interval is converted to its share of the axis length")
   void intervalSize() {
      LinearScale s1 = scale(0, 1);
      assertEquals(250, new TriCoord(s1).getIntervalSize(0.25), EPS);
      assertEquals(250, coord(0, 100).getIntervalSize(25), EPS);
      assertEquals(0, coord(0, 1).getIntervalSize(0), EPS);
   }

   @Test
   @DisplayName("a clone gets a new scale instance with the same range and shares the range")
   void cloneOwnsScale() {
      for(boolean viaFlag : new boolean[] { false, true }) {
         TriCoord c = coord(0, 100);
         TriCoord copy = viaFlag ? (TriCoord) c.clone(true) : (TriCoord) c.clone();
         Scale cs = copy.getScale();
         assertNotSame(c.getScale(), cs);
         assertEquals(0, cs.getMin(), EPS);
         assertEquals(100, cs.getMax(), EPS);

         // the range is shared by default, so a change of the clone is seen by the original
         ((LinearScale) cs).setMax(10);
         assertEquals(10, c.getScale().getMax(), EPS);
      }
   }
}
