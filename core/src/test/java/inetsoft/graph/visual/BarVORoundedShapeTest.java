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
 * Unit tests for BarVO.buildRoundedBarShape geometry.
 */
class BarVORoundedShapeTest {

   // -----------------------------------------------------------------------
   // roundAllCorners = true
   // -----------------------------------------------------------------------

   @Test
   void roundAllCorners_returnsRoundRectangle() {
      Rectangle2D bounds = new Rectangle2D.Double(0, 0, 100, 40);
      Shape shape = BarVO.buildRoundedBarShape(bounds, 0.3, 0, true);
      assertInstanceOf(RoundRectangle2D.class, shape);
   }

   @Test
   void roundAllCorners_arcDiameterMatchesFraction() {
      Rectangle2D bounds = new Rectangle2D.Double(0, 0, 100, 40);
      // shortDim = min(100, 40) = 40; arc = min(0.3 * 40, 40/2) = min(12, 20) = 12; diameter = 24
      Shape shape = BarVO.buildRoundedBarShape(bounds, 0.3, 0, true);
      RoundRectangle2D rr = (RoundRectangle2D) shape;
      assertEquals(24.0, rr.getArcWidth(), 1e-9);
      assertEquals(24.0, rr.getArcHeight(), 1e-9);
   }

   @Test
   void roundAllCorners_arcCappedAtHalfHeight() {
      // radiusFraction * w = 0.5 * 100 = 50, but h/2 = 15 — cap applies
      Rectangle2D bounds = new Rectangle2D.Double(0, 0, 100, 30);
      Shape shape = BarVO.buildRoundedBarShape(bounds, 0.5, 0, true);
      RoundRectangle2D rr = (RoundRectangle2D) shape;
      assertEquals(30.0, rr.getArcWidth(), 1e-9); // arc = 15, diameter = 30
   }

   // -----------------------------------------------------------------------
   // roundAllCorners = false — bounding box containment and corner sharpness
   // -----------------------------------------------------------------------

   @Test
   void direction0_shapeContainedInBounds() {
      Rectangle2D bounds = new Rectangle2D.Double(10, 20, 60, 40);
      Shape shape = BarVO.buildRoundedBarShape(bounds, 0.25, 0, false);
      assertTrue(bounds.contains(shape.getBounds2D()),
                 "Shape must fit within the declared bounds");
   }

   @Test
   void direction1_shapeContainedInBounds() {
      Rectangle2D bounds = new Rectangle2D.Double(10, 20, 60, 40);
      Shape shape = BarVO.buildRoundedBarShape(bounds, 0.25, 1, false);
      assertTrue(bounds.contains(shape.getBounds2D()),
                 "Shape must fit within the declared bounds");
   }

   @Test
   void direction2_shapeContainedInBounds() {
      Rectangle2D bounds = new Rectangle2D.Double(0, 0, 40, 60);
      Shape shape = BarVO.buildRoundedBarShape(bounds, 0.25, 2, false);
      assertTrue(bounds.contains(shape.getBounds2D()),
                 "Horizontal-right shape must fit within the declared bounds");
   }

   @Test
   void direction3_shapeContainedInBounds() {
      Rectangle2D bounds = new Rectangle2D.Double(0, 0, 40, 60);
      Shape shape = BarVO.buildRoundedBarShape(bounds, 0.25, 3, false);
      assertTrue(bounds.contains(shape.getBounds2D()),
                 "Horizontal-left shape must fit within the declared bounds");
   }

   /** Direction 0: base is at y+h (top in Y-up). Top corners must be sharp. */
   @Test
   void direction0_baseCornerIsSharp() {
      Rectangle2D bounds = new Rectangle2D.Double(0, 0, 100, 50);
      Shape shape = BarVO.buildRoundedBarShape(bounds, 0.3, 0, false);
      // Top-left corner (0, 50) must be inside the shape
      assertTrue(shape.contains(0.5, 49.5),
                 "Base (top) corner region must be sharp (corner point inside shape)");
   }

   /** Direction 1: base is at y (bottom in Y-up). Bottom corners must be sharp. */
   @Test
   void direction1_baseCornerIsSharp() {
      Rectangle2D bounds = new Rectangle2D.Double(0, 0, 100, 50);
      Shape shape = BarVO.buildRoundedBarShape(bounds, 0.3, 1, false);
      // Bottom-left corner (0, 0) must be inside the shape
      assertTrue(shape.contains(0.5, 0.5),
                 "Base (bottom) corner region must be sharp");
   }

   /** Degenerate: very large radius fraction clamps to h/2 without errors. */
   @Test
   void radiusFractionClampedToHalfHeight() {
      Rectangle2D bounds = new Rectangle2D.Double(0, 0, 200, 20);
      Shape shape = BarVO.buildRoundedBarShape(bounds, 1.0, 1, false);
      assertNotNull(shape);
      assertTrue(bounds.contains(shape.getBounds2D()));
   }

   /** roundAllCorners on a wide horizontal bar: arc must scale with radiusFraction, not be capped at h/2 always. */
   @Test
   void roundAllCorners_horizontalBar_arcBasedOnShortDimension() {
      // Wide horizontal bar: w=200, h=20. shortDim = min(200, 20) = 20.
      // radiusFraction=0.1 → arc = min(0.1*20, 10) = 2; diameter = 4
      // radiusFraction=0.4 → arc = min(0.4*20, 10) = 8; diameter = 16
      // Without fix: both would cap at h/2=10 (diameter=20), losing fraction distinction.
      Rectangle2D bounds = new Rectangle2D.Double(0, 0, 200, 20);
      RoundRectangle2D rr1 = (RoundRectangle2D) BarVO.buildRoundedBarShape(bounds, 0.1, 0, true);
      RoundRectangle2D rr2 = (RoundRectangle2D) BarVO.buildRoundedBarShape(bounds, 0.4, 0, true);
      assertEquals(4.0,  rr1.getArcWidth(), 1e-9);
      assertEquals(16.0, rr2.getArcWidth(), 1e-9);
      assertTrue(rr1.getArcWidth() < rr2.getArcWidth(), "Larger fraction must produce larger arc");
   }

   /** Unknown direction throws IllegalArgumentException. */
   @Test
   void unknownDirection_throwsIllegalArgumentException() {
      Rectangle2D bounds = new Rectangle2D.Double(0, 0, 100, 40);
      assertThrows(IllegalArgumentException.class,
                   () -> BarVO.buildRoundedBarShape(bounds, 0.3, 99, false));
   }
}
