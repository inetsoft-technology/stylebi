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
package inetsoft.graph.visual;

import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.geom.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AreaVO.buildCombinedCurvedAreaShape path-construction logic.
 * Verifies edge cases (empty, single-point, all-NaN), contiguous runs, and NaN-split runs.
 */
class AreaVOCombinedCurvedShapeTest {
   @Test
   void emptyInput_returnsNull() {
      Shape shape = AreaVO.buildCombinedCurvedAreaShape(new Point2D[0], new Point2D[0]);
      assertNull(shape, "empty input must produce no path");
   }

   @Test
   void singlePoint_returnsNull() {
      Point2D[] pts = { new Point2D.Double(10, 80) };
      Point2D[] basepts = { new Point2D.Double(10, 0) };
      Shape shape = AreaVO.buildCombinedCurvedAreaShape(pts, basepts);
      assertNull(shape, "single-point input cannot form a segment, must produce no path");
   }

   @Test
   void allNaN_returnsNull() {
      Point2D[] pts = {
         new Point2D.Double(Double.NaN, Double.NaN),
         new Point2D.Double(Double.NaN, Double.NaN),
         new Point2D.Double(Double.NaN, Double.NaN)
      };
      Point2D[] basepts = { pts[0], pts[1], pts[2] };
      Shape shape = AreaVO.buildCombinedCurvedAreaShape(pts, basepts);
      assertNull(shape, "all-NaN input must produce no path");
   }

   @Test
   void twoPointRun_producesOneClosedCurvedSubpath() {
      Point2D[] pts = {
         new Point2D.Double(0, 80),
         new Point2D.Double(100, 80)
      };
      Point2D[] basepts = {
         new Point2D.Double(0, 0),
         new Point2D.Double(100, 0)
      };

      Shape shape = AreaVO.buildCombinedCurvedAreaShape(pts, basepts);
      assertNotNull(shape);

      int[] segCounts = countSegments(shape);
      // Expected geometry: M (start) → C (top curve) → L (right vertical) → C (base curve) → Z
      assertEquals(1, segCounts[0], "expected exactly one moveTo");
      assertEquals(1, segCounts[1], "expected exactly one lineTo (right vertical)");
      assertEquals(2, segCounts[2], "expected two cubicTo (one top, one base)");
      assertEquals(1, segCounts[3], "expected exactly one closePath");
   }

   @Test
   void multiPointRun_producesProportionalCurves() {
      // 5 points → 4 top segments + 1 vertical right + 4 base segments + 1 close = 4 C, 1 L, 4 C, Z
      Point2D[] pts = new Point2D[5];
      Point2D[] basepts = new Point2D[5];

      for(int i = 0; i < 5; i++) {
         pts[i] = new Point2D.Double(i * 25, 80 + (i % 2 == 0 ? 0 : 10));
         basepts[i] = new Point2D.Double(i * 25, 0);
      }

      Shape shape = AreaVO.buildCombinedCurvedAreaShape(pts, basepts);
      assertNotNull(shape);

      int[] segCounts = countSegments(shape);
      assertEquals(1, segCounts[0], "one moveTo per closed subpath");
      assertEquals(1, segCounts[1], "one lineTo (right vertical)");
      assertEquals(8, segCounts[2], "expected 4 top + 4 base = 8 cubic curves");
      assertEquals(1, segCounts[3], "one closePath");
   }

   @Test
   void nanGapMidArray_producesTwoClosedSubpaths() {
      // points 0..1 valid, point 2 NaN, points 3..4 valid → two runs each contributing a subpath
      Point2D[] pts = {
         new Point2D.Double(0, 80),
         new Point2D.Double(25, 90),
         new Point2D.Double(Double.NaN, Double.NaN),
         new Point2D.Double(75, 90),
         new Point2D.Double(100, 80)
      };
      Point2D[] basepts = {
         new Point2D.Double(0, 0),
         new Point2D.Double(25, 0),
         new Point2D.Double(Double.NaN, Double.NaN),
         new Point2D.Double(75, 0),
         new Point2D.Double(100, 0)
      };

      Shape shape = AreaVO.buildCombinedCurvedAreaShape(pts, basepts);
      assertNotNull(shape);

      int[] segCounts = countSegments(shape);
      // Each 2-point run → 1 M, 1 L (right vertical), 2 C (top + base), 1 Z
      assertEquals(2, segCounts[0], "one moveTo per non-NaN run = 2");
      assertEquals(2, segCounts[1], "one right-vertical lineTo per run = 2");
      assertEquals(4, segCounts[2], "two cubic curves per run × 2 runs = 4");
      assertEquals(2, segCounts[3], "one closePath per run = 2");
   }

   @Test
   void nanInBaseptsAlone_skipsThatIndex() {
      // pts all valid, but basepts[1] is NaN — the helper must split at that index too
      Point2D[] pts = {
         new Point2D.Double(0, 80),
         new Point2D.Double(50, 90),
         new Point2D.Double(100, 80)
      };
      Point2D[] basepts = {
         new Point2D.Double(0, 0),
         new Point2D.Double(Double.NaN, Double.NaN),
         new Point2D.Double(100, 0)
      };

      Shape shape = AreaVO.buildCombinedCurvedAreaShape(pts, basepts);
      // Index 0 alone (basepts[1] invalid stops the run); index 2 alone (no neighbor).
      // Both runs are single-point, so no subpath should be produced.
      assertNull(shape, "no run of length >= 2 when basepts[1] is NaN between three pts");
   }

   /**
    * Walk the path and tally segments: [moveTos, lineTos, cubicTos, closes].
    * QuadTos are not produced by buildCombinedCurvedAreaShape so they are not counted.
    */
   private static int[] countSegments(Shape shape) {
      int[] counts = new int[4];
      PathIterator it = shape.getPathIterator(null);
      double[] coords = new double[6];

      while(!it.isDone()) {
         switch(it.currentSegment(coords)) {
         case PathIterator.SEG_MOVETO:  counts[0]++; break;
         case PathIterator.SEG_LINETO:  counts[1]++; break;
         case PathIterator.SEG_CUBICTO: counts[2]++; break;
         case PathIterator.SEG_CLOSE:   counts[3]++; break;
         }

         it.next();
      }

      return counts;
   }
}
