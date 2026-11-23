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
package inetsoft.graph.internal;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.geom.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link MarchingSquaresContour#createShapes}. Hand derivations: the data grid is
 * padded by one ring of {@code min(levels) - 1}; a cell value v and level L give the
 * crossing fraction f = (L - pad) / (v - pad) measured from the pad side, and the
 * single-cell contour is a diamond of area 2 * (1 - f)^2 in grid units.
 */
@Tag("core")
class MarchingSquaresContourTest {
   // GeneralPath stores float coordinates, so values are exact only to about 1e-6
   private static final double DELTA = 1e-5;

   private static GeneralPath[] shapes(double[][] data, double[] levels, double w, double h) {
      return new MarchingSquaresContour(new Rectangle2D.Double(0, 0, w, h))
         .createShapes(data, levels);
   }

   private static GeneralPath shape(double[][] data, double level, double w, double h) {
      return shapes(data, new double[] { level }, w, h)[0];
   }

   /** Signed areas of the closed subpaths in order of appearance. */
   private static List<Double> subpathAreas(Path2D path) {
      List<Double> areas = new ArrayList<>();
      double[] c = new double[6];
      double sx = 0, sy = 0, px = 0, py = 0, sum = 0;
      boolean open = false;

      for(PathIterator it = path.getPathIterator(null); !it.isDone(); it.next()) {
         switch(it.currentSegment(c)) {
         case PathIterator.SEG_MOVETO:
            sx = px = c[0];
            sy = py = c[1];
            sum = 0;
            open = true;
            break;
         case PathIterator.SEG_LINETO:
            sum += px * c[1] - c[0] * py;
            px = c[0];
            py = c[1];
            break;
         case PathIterator.SEG_CLOSE:
            sum += px * sy - sx * py;
            areas.add(sum / 2);
            open = false;
            break;
         default:
            fail("unexpected segment type");
         }
      }

      assertFalse(open, "subpath not closed");
      return areas;
   }

   private static double area(Path2D path) {
      return subpathAreas(path).stream().mapToDouble(Math::abs).sum();
   }

   private static int count(Path2D path, int segmentType) {
      int n = 0;

      for(PathIterator it = path.getPathIterator(null); !it.isDone(); it.next()) {
         if(it.currentSegment(new double[6]) == segmentType) {
            n++;
         }
      }

      return n;
   }

   private static List<Point2D> vertices(Path2D path) {
      List<Point2D> pts = new ArrayList<>();
      double[] c = new double[6];

      for(PathIterator it = path.getPathIterator(null); !it.isDone(); it.next()) {
         if(it.currentSegment(c) != PathIterator.SEG_CLOSE) {
            pts.add(new Point2D.Double(c[0], c[1]));
         }
      }

      return pts;
   }

   private static void assertVertices(Path2D path, double... xy) {
      List<Point2D> actual = vertices(path);
      assertEquals(xy.length / 2, actual.size(), "vertex count of " + actual);

      for(int i = 0; i < xy.length; i += 2) {
         final double x = xy[i], y = xy[i + 1];
         assertTrue(actual.stream().anyMatch(p -> Math.abs(p.getX() - x) < DELTA &&
                                                  Math.abs(p.getY() - y) < DELTA),
                    "missing vertex (" + x + "," + y + ") in " + actual);
      }
   }

   private static void assertBounds(Path2D path, double x0, double y0, double x1, double y1) {
      Rectangle2D b = path.getBounds2D();
      assertEquals(x0, b.getMinX(), DELTA, "min x");
      assertEquals(y0, b.getMinY(), DELTA, "min y");
      assertEquals(x1, b.getMaxX(), DELTA, "max x");
      assertEquals(y1, b.getMaxY(), DELTA, "max y");
   }

   private static void assertAllFinite(Path2D path) {
      for(Point2D p : vertices(path)) {
         assertTrue(Double.isFinite(p.getX()) && Double.isFinite(p.getY()),
                    "non-finite vertex " + p);
      }
   }

   private static boolean hasNoSegments(Path2D path) {
      return path.getPathIterator(null).isDone();
   }

   // ---- single cell ----

   @Test
   @DisplayName("single value above the level gives a diamond through the crossings on the pad ring")
   void singleCellDiamond() {
      GeneralPath path = shape(new double[][] {{ 10 }}, 5, 2, 2);
      assertVertices(path, 1.0 / 6, 1, 1, 1.0 / 6, 11.0 / 6, 1, 1, 11.0 / 6);
      assertEquals(25.0 / 18, area(path), DELTA);
      assertBounds(path, 1.0 / 6, 1.0 / 6, 11.0 / 6, 11.0 / 6);
   }

   @Test
   @DisplayName("bounds width and height scale x and y independently")
   void singleCellScaledByBounds() {
      GeneralPath path = shape(new double[][] {{ 10 }}, 5, 200, 100);
      assertBounds(path, 100.0 / 6, 50.0 / 6, 1100.0 / 6, 550.0 / 6);
      assertEquals(25.0 / 18 * 5000, area(path), 0.1);
   }

   @Test
   @DisplayName("one row of two equal values gives a hexagon, columns run along x")
   void oneRowTwoColumns() {
      GeneralPath path = shape(new double[][] {{ 10, 10 }}, 5, 3, 2);
      assertVertices(path, 1.0 / 6, 1, 1, 1.0 / 6, 2, 1.0 / 6,
                     17.0 / 6, 1, 2, 11.0 / 6, 1, 11.0 / 6);
      assertEquals(55.0 / 18, area(path), DELTA);
   }

   @Test
   @DisplayName("two rows of one column give the transposed hexagon, rows run along y")
   void twoRowsOneColumn() {
      GeneralPath path = shape(new double[][] {{ 10 }, { 10 }}, 5, 2, 3);
      assertVertices(path, 1, 1.0 / 6, 1.0 / 6, 1, 1.0 / 6, 2,
                     1, 17.0 / 6, 11.0 / 6, 2, 11.0 / 6, 1);
      assertEquals(55.0 / 18, area(path), DELTA);
      assertBounds(path, 1.0 / 6, 1.0 / 6, 11.0 / 6, 17.0 / 6);
   }

   @Test
   @DisplayName("level below the data value: crossing fraction (L - pad) / (v - pad)")
   void levelBelowData() {
      // pad = -6, f = 1/16, area = 2 * (15/16)^2
      assertEquals(1.7578125, area(shape(new double[][] {{ 10 }}, -5, 2, 2)), DELTA);
   }

   @Test
   @DisplayName("negative data value and level follow the same formula")
   void negativeValues() {
      // pad = -6, f = (-5 + 6) / (-3 + 6) = 1/3, area = 2 * (2/3)^2
      assertEquals(8.0 / 9, area(shape(new double[][] {{ -3 }}, -5, 2, 2)), DELTA);
   }

   @Test
   @DisplayName("level equal to a neighbouring interior value ends the contour at that cell")
   void levelEqualsInteriorCellValue() {
      GeneralPath path = shape(new double[][] {{ 10, 5 }}, 5, 3, 2);
      assertVertices(path, 1.0 / 6, 1, 1, 1.0 / 6, 2, 1, 1, 11.0 / 6);
      assertEquals(55.0 / 36, area(path), DELTA);
   }

   // ---- multiple levels ----

   @Test
   @DisplayName("two levels give one path per level in the order of the levels, nested")
   void multipleLevelsNested() {
      GeneralPath[] paths = shapes(new double[][] {{ 10 }}, new double[] { 5, 8 }, 2, 2);
      assertEquals(2, paths.length);
      assertEquals(25.0 / 18, area(paths[0]), DELTA);
      assertEquals(2.0 / 9, area(paths[1]), DELTA);

      for(Point2D p : vertices(paths[1])) {
         assertTrue(paths[0].contains(p), "level 8 vertex " + p + " outside level 5 shape");
      }
   }

   @Test
   @DisplayName("levels given in descending order keep the order of the levels in the result")
   void levelsKeepGivenOrder() {
      GeneralPath[] paths = shapes(new double[][] {{ 10 }}, new double[] { 8, 5 }, 2, 2);
      assertEquals(2.0 / 9, area(paths[0]), DELTA);
      assertEquals(25.0 / 18, area(paths[1]), DELTA);
   }

   @Test
   @DisplayName("empty level array returns an empty array")
   void emptyLevels() {
      assertEquals(0, shapes(new double[][] {{ 10 }}, new double[0], 2, 2).length);
   }

   @Test
   @DisplayName("path extents (min x, max x, max y) of scaled shapes follow the first and last crossings")
   void pathExtentsScaled() {
      assertExtents(shape(new double[][] {{ 10 }}, 5, 200, 100),
                    100.0 / 6, 1100.0 / 6, 550.0 / 6);
      assertExtents(shape(new double[][] {{ 10, 10 }}, 5, 300, 100),
                    100.0 / 6, 1700.0 / 6, 550.0 / 6);
      assertExtents(shape(new double[][] {{ 10 }, { 10 }}, 5, 200, 300),
                    100.0 / 6, 1100.0 / 6, 850.0 / 3);
      double[][] plateau = new double[3][3];
      Arrays.stream(plateau).forEach(r -> Arrays.fill(r, 10));
      assertExtents(shape(plateau, 5, 400, 400), 100.0 / 6, 2300.0 / 6, 2300.0 / 6);
   }

   private static void assertExtents(Path2D path, double minX, double maxX, double maxY) {
      Rectangle2D b = path.getBounds2D();
      assertEquals(minX, b.getMinX(), 1e-3, "min x");
      assertEquals(maxX, b.getMaxX(), 1e-3, "max x");
      assertEquals(maxY, b.getMaxY(), 1e-3, "max y");
   }

   // ---- nothing above the level ----

   @Test
   @DisplayName("level above every value gives a path without segments")
   void levelAboveData() {
      assertTrue(hasNoSegments(shape(new double[][] {{ 10 }}, 20, 2, 2)));
   }

   @Test
   @DisplayName("level equal to the maximum value encloses nothing (strict comparison)")
   void levelEqualsMaximum() {
      GeneralPath path = shape(new double[][] {{ 10 }}, 10, 2, 2);
      assertAllFinite(path);
      assertTrue(area(path) < DELTA);
   }

   @Test
   @DisplayName("all cells equal to the level enclose nothing")
   void allCellsEqualLevel() {
      GeneralPath path = shape(new double[][] {{ 5, 5 }, { 5, 5 }}, 5, 4, 4);
      assertAllFinite(path);
      assertTrue(area(path) < DELTA);
   }

   // ---- degenerate values ----

   @Test
   @DisplayName("NaN cell value produces no non-finite coordinate")
   void nanCell() {
      GeneralPath path = assertTimeoutPreemptively(Duration.ofSeconds(10),
         () -> shape(new double[][] {{ 10, Double.NaN }}, 5, 3, 2));
      assertAllFinite(path);
   }

   @Test
   @DisplayName("huge cell value gives finite coordinates and area about 2")
   void hugeValue() {
      GeneralPath path = shape(new double[][] {{ 1e300 }}, 5, 2, 2);
      assertAllFinite(path);
      assertEquals(2.0, area(path), DELTA);
   }

   @Test
   @DisplayName("a finite point after a skipped non-finite point is kept")
   void finitePointAfterNonFinitePoint() {
      // the crossings next to the Infinity cell are NaN and are skipped; the corner (2,2) of
      // the cell valued 5 differs from the NaN point (2,NaN) only on y and must be kept
      GeneralPath path = shape(new double[][] {{ 0, Double.POSITIVE_INFINITY }, { 7, 5 }},
                               5, 3, 3);
      assertTrue(vertices(path).stream().anyMatch(p -> Math.abs(p.getX() - 2) < DELTA &&
                                                       Math.abs(p.getY() - 2) < DELTA),
                 "missing vertex (2,2) in " + vertices(path));
   }

   // ---- saddles ----

   @Test
   @DisplayName("saddle with center average above the level gives one connected subpath")
   void saddleConnected() {
      GeneralPath path = shape(new double[][] {{ 10, 0 }, { 0, 10 }}, 3, 4, 4);
      assertEquals(1, count(path, PathIterator.SEG_MOVETO));
      assertEquals(1, count(path, PathIterator.SEG_CLOSE));
   }

   @Test
   @DisplayName("saddle with center average below the level gives two subpaths")
   void saddleSeparated() {
      GeneralPath path = shape(new double[][] {{ 10, 0 }, { 0, 10 }}, 7, 4, 4);
      assertEquals(2, count(path, PathIterator.SEG_MOVETO));
      assertEquals(2, count(path, PathIterator.SEG_CLOSE));
   }

   @Test
   @DisplayName("mirrored saddle behaves like the original: one subpath at 3, two at 7")
   void saddleMirrored() {
      GeneralPath[] paths = shapes(new double[][] {{ 0, 10 }, { 10, 0 }},
                                   new double[] { 3, 7 }, 4, 4);
      assertEquals(1, count(paths[0], PathIterator.SEG_MOVETO));
      assertEquals(2, count(paths[1], PathIterator.SEG_MOVETO));
   }

   @Test
   @DisplayName("pinned current behavior: saddle with center average equal to the level is connected")
   void saddleTieIsConnected() {
      GeneralPath path = shape(new double[][] {{ 10, 0 }, { 0, 10 }}, 5, 4, 4);
      assertEquals(1, count(path, PathIterator.SEG_MOVETO));
   }

   // ---- larger grids ----

   @Test
   @DisplayName("3x3 plateau gives an octagon with cut corners")
   void plateauOctagon() {
      double[][] data = new double[3][3];
      Arrays.stream(data).forEach(r -> Arrays.fill(r, 10));
      GeneralPath path = shape(data, 5, 4, 4);
      assertEquals(217.0 / 18, area(path), DELTA);
      assertBounds(path, 1.0 / 6, 1.0 / 6, 23.0 / 6, 23.0 / 6);
   }

   @Test
   @DisplayName("two separated peaks give two subpaths of equal area")
   void twoBlobs() {
      GeneralPath path = shape(new double[][] {{ 10, 0, 10 }}, 5, 4, 2);
      List<Double> areas = subpathAreas(path);
      assertEquals(2, areas.size());
      assertEquals(10.0 / 9, Math.abs(areas.get(0)), DELTA);
      assertEquals(10.0 / 9, Math.abs(areas.get(1)), DELTA);
   }

   @Test
   @DisplayName("a hole whose start corner is passed again keeps that corner as a vertex")
   void holeThroughStartCorner() {
      // level 5: the hole starts at the corner (3,2) (a cell equal to the level) and the
      // outline passes through it again partway round
      GeneralPath path = shape(new double[][] {
         { 0, 8, 8, 10, 8 }, { 10, 5, 5, 4, 10 }, { 8, 5, 10, 10, 8 }, { 0, 8, 8, 4, 8 }},
         5, 6, 5);
      double hole = subpathAreas(path).stream().mapToDouble(Math::abs)
         .filter(a -> Math.abs(a - 25.0 / 36) < DELTA).findFirst().orElse(-1);
      assertEquals(25.0 / 36, hole, DELTA, "subpath areas " + subpathAreas(path));
      assertTrue(path.contains(3.0, 2.3));
   }

   @Test
   @DisplayName("each subpath ends at its last distinct vertex, closePath draws the closing edge")
   void noClosingDuplicate() {
      GeneralPath path = shape(new double[][] {{ 10, 0, 10 }, { 0, 10, 0 }}, 5, 5, 4);
      List<Point2D> start = new ArrayList<>();
      Point2D last = null;
      double[] c = new double[6];

      for(PathIterator it = path.getPathIterator(null); !it.isDone(); it.next()) {
         switch(it.currentSegment(c)) {
         case PathIterator.SEG_MOVETO:
            start.add(new Point2D.Double(c[0], c[1]));
            last = null;
            break;
         case PathIterator.SEG_LINETO:
            last = new Point2D.Double(c[0], c[1]);
            break;
         case PathIterator.SEG_CLOSE:
            Point2D s = start.get(start.size() - 1);
            assertFalse(last != null && last.distance(s) < DELTA, "closing duplicate of " + s);
         }
      }

      assertFalse(start.isEmpty());
   }

   // ---- invariants ----

   @Test
   @DisplayName("the bounds origin is ignored, only width and height scale the path")
   void originIgnored() {
      double[][] data = {{ 10 }};
      GeneralPath a = new MarchingSquaresContour(new Rectangle2D.Double(50, 60, 2, 2))
         .createShapes(data, new double[] { 5 })[0];
      GeneralPath b = shape(data, 5, 2, 2);
      List<Point2D> pa = vertices(a);
      List<Point2D> pb = vertices(b);
      assertEquals(pb.size(), pa.size());

      for(int i = 0; i < pa.size(); i++) {
         assertEquals(pb.get(i).getX(), pa.get(i).getX(), DELTA);
         assertEquals(pb.get(i).getY(), pa.get(i).getY(), DELTA);
      }
   }

   @Test
   @DisplayName("the input grid is not modified")
   void inputUnchanged() {
      double[][] data = {{ 1, 2, 3 }, { 4, 10, 6 }};
      double[][] copy = { data[0].clone(), data[1].clone() };
      shapes(data, new double[] { 2, 5 }, 3, 2);
      assertArrayEquals(copy[0], data[0]);
      assertArrayEquals(copy[1], data[1]);
   }

   @Test
   @DisplayName("60x60 gaussian bump: one closed nested shape per level, area shrinks with level")
   void gaussianBump() {
      double[][] data = new double[60][60];

      for(int r = 0; r < 60; r++) {
         for(int c = 0; c < 60; c++) {
            double d2 = (r - 29.5) * (r - 29.5) + (c - 29.5) * (c - 29.5);
            data[r][c] = Math.exp(-d2 / 200);
         }
      }

      GeneralPath[] paths = shapes(data, new double[] { 0.2, 0.5, 0.8 }, 600, 600);
      assertEquals(3, paths.length);

      for(GeneralPath p : paths) {
         assertEquals(count(p, PathIterator.SEG_MOVETO), count(p, PathIterator.SEG_CLOSE));
         assertTrue(count(p, PathIterator.SEG_MOVETO) >= 1);
         assertAllFinite(p);
      }

      assertTrue(area(paths[0]) > area(paths[1]));
      assertTrue(area(paths[1]) > area(paths[2]));
      assertTrue(area(paths[2]) > 0);
      assertTrue(paths[0].getBounds2D().contains(paths[1].getBounds2D()));
      assertTrue(paths[1].getBounds2D().contains(paths[2].getBounds2D()));
   }
}
