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

import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BarVO.computeFullBarBounds geometry.
 */
class BarVOStackRoundingTest {

   // -----------------------------------------------------------------------
   // Vertical bars
   // -----------------------------------------------------------------------

   @Test
   void vertical_openTop_fullBarExtendsAboveSegment() {
      // Segment at y=40, h=20. interval=20, cumulative=50, total=80.
      // scale = 20/20 = 1. stackDim = 80. innerOffset = (50-20)*1 = 30.
      // openDir=1 (top): inner end at bottom → fullBarY = 40 - 30 = 10
      Rectangle2D seg = new Rectangle2D.Double(10, 40, 50, 20);
      Rectangle2D full = BarVO.computeFullBarBounds(seg, true, 1, 20, 50, 80);

      assertEquals(10.0, full.getX(), 1e-9);
      assertEquals(10.0, full.getY(), 1e-9);
      assertEquals(50.0, full.getWidth(), 1e-9);
      assertEquals(80.0, full.getHeight(), 1e-9);
   }

   @Test
   void vertical_openBottom_fullBarExtendsBelowSegment() {
      // Segment at y=40, h=20. interval=20, cumulative=50, total=80.
      // scale=1. stackDim=80. innerOffset=30.
      // openDir=0 (bottom): inner end at top → fullBarY = 40+20+30-80 = 10
      Rectangle2D seg = new Rectangle2D.Double(10, 40, 50, 20);
      Rectangle2D full = BarVO.computeFullBarBounds(seg, true, 0, 20, 50, 80);

      assertEquals(10.0, full.getX(), 1e-9);
      assertEquals(10.0, full.getY(), 1e-9);
      assertEquals(50.0, full.getWidth(), 1e-9);
      assertEquals(80.0, full.getHeight(), 1e-9);
   }

   // -----------------------------------------------------------------------
   // Horizontal bars
   // -----------------------------------------------------------------------

   @Test
   void horizontal_openRight_fullBarExtendsRight() {
      // Segment at x=20, w=30. interval=30, cumulative=60, total=100.
      // scale = 30/30 = 1. stackDim = 100. innerOffset = (60-30)*1 = 30.
      // openDir=2 (right): inner end at left → fullBarX = 20 - 30 = -10
      Rectangle2D seg = new Rectangle2D.Double(20, 5, 30, 40);
      Rectangle2D full = BarVO.computeFullBarBounds(seg, false, 2, 30, 60, 100);

      assertEquals(-10.0, full.getX(), 1e-9);
      assertEquals(5.0, full.getY(), 1e-9);
      assertEquals(100.0, full.getWidth(), 1e-9);
      assertEquals(40.0, full.getHeight(), 1e-9);
   }

   @Test
   void horizontal_openLeft_fullBarExtendsLeft() {
      // Segment at x=20, w=30. interval=30, cumulative=60, total=100.
      // scale=1. stackDim=100. innerOffset=30.
      // openDir=3 (left): inner end at right → fullBarX = 20+30+30-100 = -20
      Rectangle2D seg = new Rectangle2D.Double(20, 5, 30, 40);
      Rectangle2D full = BarVO.computeFullBarBounds(seg, false, 3, 30, 60, 100);

      assertEquals(-20.0, full.getX(), 1e-9);
      assertEquals(5.0, full.getY(), 1e-9);
      assertEquals(100.0, full.getWidth(), 1e-9);
      assertEquals(40.0, full.getHeight(), 1e-9);
   }

   // -----------------------------------------------------------------------
   // Edge case: single-segment stack
   // -----------------------------------------------------------------------

   @Test
   void singleSegment_fullBarEqualsSeg() {
      // cumulative == total == interval → innerOffset = 0, full bar == segment
      Rectangle2D seg = new Rectangle2D.Double(10, 20, 60, 40);
      Rectangle2D full = BarVO.computeFullBarBounds(seg, true, 1, 40, 40, 40);

      assertEquals(seg.getX(), full.getX(), 1e-9);
      assertEquals(seg.getY(), full.getY(), 1e-9);
      assertEquals(seg.getWidth(), full.getWidth(), 1e-9);
      assertEquals(seg.getHeight(), full.getHeight(), 1e-9);
   }
}
