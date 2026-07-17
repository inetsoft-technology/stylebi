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

import inetsoft.graph.aesthetic.GShape;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Shape;
import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression guard for the relation-edge shape clipping added for Bug #75655: edges must attach to
 * the actual painted node shape boundary (e.g. a triangle's slanted edge) rather than the midpoint
 * of the node's bounding box side, which for non-box-touching shapes fell in empty space.
 *
 * Exercises the pure-geometry helpers {@code RelationEdgeVO.clipToBoundary} and
 * {@code RelationEdgeVO.segIntersect} directly (package-private for testability).
 */
@Tag("core")
class RelationEdgeVOClipTest {
   private static final double EPS = 1e-6;
   // flattened curves (ellipse) are approximated, so allow a small tolerance for CIRCLE
   private static final double FLAT_EPS = 1.5;

   // 100x100 box centered at (50,50) for all node-shape cases
   private static Shape shape(GShape gshape) {
      return gshape.getShape(0, 0, 100, 100);
   }

   private static Point2D center() {
      return new Point2D.Double(50, 50);
   }

   @Test
   void triangleClipsToSlantedRightEdge() {
      // TRIANGLE: apex (50,100), base (0,0)-(100,0). A horizontal ray to the right from the center
      // crosses the right slanted edge (100,0)-(50,100) at y=50, i.e. x=75 — NOT the bbox side
      // midpoint (100,50). This is the exact defect from Bug #75655.
      Point2D p = RelationEdgeVO.clipToBoundary(shape(GShape.TRIANGLE), center(),
                                                new Point2D.Double(200, 50));
      assertNotNull(p);
      assertEquals(75.0, p.getX(), EPS);
      assertEquals(50.0, p.getY(), EPS);
   }

   @Test
   void triangleClipsToApex() {
      // straight up from the center hits the apex at (50,100)
      Point2D p = RelationEdgeVO.clipToBoundary(shape(GShape.TRIANGLE), center(),
                                                new Point2D.Double(50, 300));
      assertNotNull(p);
      assertEquals(50.0, p.getX(), EPS);
      assertEquals(100.0, p.getY(), EPS);
   }

   @Test
   void triangleClipsToBase() {
      // straight down from the center hits the base edge at (50,0)
      Point2D p = RelationEdgeVO.clipToBoundary(shape(GShape.TRIANGLE), center(),
                                                new Point2D.Double(50, -300));
      assertNotNull(p);
      assertEquals(50.0, p.getX(), EPS);
      assertEquals(0.0, p.getY(), EPS);
   }

   @Test
   void diamondClipsToRightVertex() {
      // DIAMOND vertices: (50,0),(100,50),(50,100),(0,50); horizontal ray exits at (100,50)
      Point2D p = RelationEdgeVO.clipToBoundary(shape(GShape.DIAMOND), center(),
                                                new Point2D.Double(200, 50));
      assertNotNull(p);
      assertEquals(100.0, p.getX(), EPS);
      assertEquals(50.0, p.getY(), EPS);
   }

   @Test
   void squareClipsToRightSide() {
      // SQUARE is the full bounding box; horizontal ray exits at the right side (100,50)
      Point2D p = RelationEdgeVO.clipToBoundary(shape(GShape.SQUARE), center(),
                                                new Point2D.Double(200, 50));
      assertNotNull(p);
      assertEquals(100.0, p.getX(), EPS);
      assertEquals(50.0, p.getY(), EPS);
   }

   @Test
   void circleClipsToRadialBoundary() {
      // CIRCLE (ellipse) of radius 50; horizontal ray exits near the rightmost point (100,50).
      // Flattened to line segments, so allow a small tolerance.
      Point2D p = RelationEdgeVO.clipToBoundary(shape(GShape.CIRCLE), center(),
                                                new Point2D.Double(200, 50));
      assertNotNull(p);
      assertEquals(100.0, p.getX(), FLAT_EPS);
      assertEquals(50.0, p.getY(), FLAT_EPS);
   }

   @Test
   void clipReturnsNullWhenRayMissesShape() {
      // a ray that starts and points away from the shape never crosses its boundary
      Point2D p = RelationEdgeVO.clipToBoundary(shape(GShape.TRIANGLE),
                                                new Point2D.Double(500, 500),
                                                new Point2D.Double(900, 900));
      assertNull(p);
   }

   @Test
   void segIntersectCrossing() {
      Point2D p = RelationEdgeVO.segIntersect(new Point2D.Double(0, 0),
                                              new Point2D.Double(10, 0), 5, -5, 5, 5);
      assertNotNull(p);
      assertEquals(5.0, p.getX(), EPS);
      assertEquals(0.0, p.getY(), EPS);
   }

   @Test
   void segIntersectParallelReturnsNull() {
      Point2D p = RelationEdgeVO.segIntersect(new Point2D.Double(0, 0),
                                              new Point2D.Double(10, 0), 0, 5, 10, 5);
      assertNull(p);
   }

   @Test
   void segIntersectBeyondSegmentReturnsNull() {
      // the infinite lines cross at (20,0) but that is past the end of segment p1-p2
      Point2D p = RelationEdgeVO.segIntersect(new Point2D.Double(0, 0),
                                              new Point2D.Double(10, 0), 20, -5, 20, 5);
      assertNull(p);
   }

   @Test
   void segIntersectEndpointTouch() {
      // touching at a shared endpoint (t=1, u=0) still counts as an intersection
      Point2D p = RelationEdgeVO.segIntersect(new Point2D.Double(0, 0),
                                              new Point2D.Double(10, 0), 10, 0, 10, 10);
      assertNotNull(p);
      assertEquals(10.0, p.getX(), EPS);
      assertEquals(0.0, p.getY(), EPS);
   }
}
