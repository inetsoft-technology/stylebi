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

import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GTool.computeCenterPullCurve, the helper that builds chord-bending bezier
 * curves used by circular network charts.
 */
class GToolCenterPullCurveTest {
   @Test
   void zeroSmoothing_keepsControlPointAtMidpoint() {
      Point2D src = new Point2D.Double(0, 0);
      Point2D dst = new Point2D.Double(100, 0);
      Point2D center = new Point2D.Double(50, 50);

      QuadCurve2D q = GTool.computeCenterPullCurve(src, dst, center, 0.0);

      assertEquals(50.0, q.getCtrlX(), 1e-9, "k=0 keeps control x at chord midpoint");
      assertEquals(0.0, q.getCtrlY(), 1e-9, "k=0 keeps control y at chord midpoint");
   }

   @Test
   void fullSmoothing_pullsControlPointAllTheWayToCenter() {
      Point2D src = new Point2D.Double(0, 0);
      Point2D dst = new Point2D.Double(100, 0);
      Point2D center = new Point2D.Double(50, 50);

      QuadCurve2D q = GTool.computeCenterPullCurve(src, dst, center, 1.0);

      assertEquals(50.0, q.getCtrlX(), 1e-9, "k=1 puts control x at the centre");
      assertEquals(50.0, q.getCtrlY(), 1e-9, "k=1 puts control y at the centre");
   }

   @Test
   void halfSmoothing_pullsControlPointHalfwayToCenter() {
      Point2D src = new Point2D.Double(0, 0);
      Point2D dst = new Point2D.Double(100, 0);
      Point2D center = new Point2D.Double(50, 50);

      QuadCurve2D q = GTool.computeCenterPullCurve(src, dst, center,
                                                   GTool.CIRCULAR_EDGE_SMOOTHING);

      assertEquals(50.0, q.getCtrlX(), 1e-9);
      // midpoint y is 0; centre y is 50; halfway pull yields 25.
      assertEquals(25.0, q.getCtrlY(), 1e-9, "k=0.5 puts control y halfway to centre");
   }

   @Test
   void endpointsAlwaysMatchSourceAndDestination() {
      Point2D src = new Point2D.Double(7, -3);
      Point2D dst = new Point2D.Double(11, 42);
      Point2D center = new Point2D.Double(0, 0);

      QuadCurve2D q = GTool.computeCenterPullCurve(src, dst, center, 0.7);

      assertEquals(src.getX(), q.getX1(), 1e-9);
      assertEquals(src.getY(), q.getY1(), 1e-9);
      assertEquals(dst.getX(), q.getX2(), 1e-9);
      assertEquals(dst.getY(), q.getY2(), 1e-9);
   }

   @Test
   void negativeSmoothing_extrapolatesAwayFromCenter() {
      // Documented contract: the formula extrapolates linearly through the chord midpoint
      // when smoothing < 0 (control point pushed away from center). Not used in production
      // (callers pass 0 ≤ k ≤ 1) but pinning the math prevents a future change from
      // silently flipping the sign.
      Point2D src = new Point2D.Double(0, 0);
      Point2D dst = new Point2D.Double(100, 0);
      Point2D center = new Point2D.Double(50, 50);

      QuadCurve2D q = GTool.computeCenterPullCurve(src, dst, center, -0.5);

      // midpoint y is 0; centre y is 50; pulling by -0.5 moves the control point 25 units
      // to the opposite side of the midpoint from the centre.
      assertEquals(50.0, q.getCtrlX(), 1e-9, "x stays on the chord midpoint when src/dst x are symmetric about it");
      assertEquals(-25.0, q.getCtrlY(), 1e-9, "k=-0.5 pushes control y away from centre");
   }

   @Test
   void coincidentEndpoints_produceDegenerateCurveWithoutCrash() {
      Point2D pt = new Point2D.Double(5, 5);
      Point2D center = new Point2D.Double(50, 50);

      QuadCurve2D q = GTool.computeCenterPullCurve(pt, pt, center,
                                                   GTool.CIRCULAR_EDGE_SMOOTHING);

      assertEquals(5.0, q.getX1(), 1e-9);
      assertEquals(5.0, q.getX2(), 1e-9);
      // self-edge: control point still pulls toward centre, harmless because painter just
      // draws an inward stub.
      assertEquals(27.5, q.getCtrlX(), 1e-9);
      assertEquals(27.5, q.getCtrlY(), 1e-9);
   }
}
