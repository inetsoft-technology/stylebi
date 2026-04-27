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
package inetsoft.report.internal;

import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

class BoundsTest {

   // ── Constructors ──────────────────────────────────────────────────────

   @Test
   void defaultConstructorCreatesZeroBounds() {
      Bounds b = new Bounds();
      assertEquals(0f, b.x);
      assertEquals(0f, b.y);
      assertEquals(0f, b.width);
      assertEquals(0f, b.height);
   }

   @Test
   void fourArgConstructorSetsFields() {
      Bounds b = new Bounds(1.5f, 2.5f, 3.0f, 4.0f);
      assertEquals(1.5f, b.x);
      assertEquals(2.5f, b.y);
      assertEquals(3.0f, b.width);
      assertEquals(4.0f, b.height);
   }

   @Test
   void copyConstructorCopiesFields() {
      Bounds orig = new Bounds(1f, 2f, 3f, 4f);
      Bounds copy = new Bounds(orig);
      assertEquals(orig.x, copy.x);
      assertEquals(orig.y, copy.y);
      assertEquals(orig.width, copy.width);
      assertEquals(orig.height, copy.height);
   }

   @Test
   void rectangleConstructorCopiesIntFields() {
      Rectangle rect = new Rectangle(10, 20, 30, 40);
      Bounds b = new Bounds(rect);
      assertEquals(10f, b.x);
      assertEquals(20f, b.y);
      assertEquals(30f, b.width);
      assertEquals(40f, b.height);
   }

   // ── round() ───────────────────────────────────────────────────────────

   @Test
   void roundIntegerAlignedBoundsUnchanged() {
      // All values already integers → ceil has no effect
      Bounds b = new Bounds(2f, 3f, 4f, 5f);
      Bounds r = b.round();
      assertEquals(2f, r.x);
      assertEquals(3f, r.y);
      assertEquals(4f, r.width);
      assertEquals(5f, r.height);
   }

   @Test
   void roundCeilsXAndY() {
      // x=1.2, y=2.7 → ceil(x)=2, ceil(y)=3
      Bounds b = new Bounds(1.2f, 2.7f, 3.0f, 4.0f);
      Bounds r = b.round();
      assertEquals(2f, r.x);
      assertEquals(3f, r.y);
   }

   @Test
   void roundWidthPreservesRightEdge() {
      // x=1.2, width=3.0 → right=4.2, ceil(right)=5, ceil(x)=2 → width=5-2=3
      Bounds b = new Bounds(1.2f, 0f, 3.0f, 4.0f);
      Bounds r = b.round();
      assertEquals(2f, r.x);
      assertEquals(3f, r.width);  // ceil(4.2) - ceil(1.2) = 5 - 2 = 3
   }

   @Test
   void roundHeightPreservesBottomEdge() {
      // y=1.5, height=2.5 → bottom=4.0, ceil(bottom)=4, ceil(y)=2 → height=4-2=2
      Bounds b = new Bounds(0f, 1.5f, 4.0f, 2.5f);
      Bounds r = b.round();
      assertEquals(2f, r.y);
      assertEquals(2f, r.height); // ceil(4.0) - ceil(1.5) = 4 - 2 = 2
   }

   // ── getRectangle() ────────────────────────────────────────────────────

   @Test
   void getRectangleConvertsExactIntegerBounds() {
      Bounds b = new Bounds(10f, 20f, 30f, 40f);
      Rectangle r = b.getRectangle();
      assertEquals(10, r.x);
      assertEquals(20, r.y);
      assertEquals(30, r.width);
      assertEquals(40, r.height);
   }

   @Test
   void getRectangleWithFractionalOrigin() {
      // x=1.9, y=2.1, width=3.0, height=4.0
      // right=4.9, bottom=6.1
      // ix=(int)1.9=1, iy=(int)2.1=2
      // iw=(int)(4.9-1)=(int)3.9=3, ih=(int)(6.1-2)=(int)4.1=4
      Bounds b = new Bounds(1.9f, 2.1f, 3.0f, 4.0f);
      Rectangle r = b.getRectangle();
      assertEquals(1, r.x);
      assertEquals(2, r.y);
      assertEquals(3, r.width);
      assertEquals(4, r.height);
   }

   // ── equals ────────────────────────────────────────────────────────────

   @Test
   void equalsSameBoundsIsTrue() {
      Bounds a = new Bounds(1f, 2f, 3f, 4f);
      Bounds b = new Bounds(1f, 2f, 3f, 4f);
      assertEquals(a, b);
   }

   @Test
   void equalsDifferentXIsFalse() {
      Bounds a = new Bounds(1f, 2f, 3f, 4f);
      Bounds b = new Bounds(9f, 2f, 3f, 4f);
      assertNotEquals(a, b);
   }

   @Test
   void equalsDifferentYIsFalse() {
      Bounds a = new Bounds(1f, 2f, 3f, 4f);
      Bounds b = new Bounds(1f, 9f, 3f, 4f);
      assertNotEquals(a, b);
   }

   @Test
   void equalsDifferentWidthIsFalse() {
      Bounds a = new Bounds(1f, 2f, 3f, 4f);
      Bounds b = new Bounds(1f, 2f, 9f, 4f);
      assertNotEquals(a, b);
   }

   @Test
   void equalsDifferentHeightIsFalse() {
      Bounds a = new Bounds(1f, 2f, 3f, 4f);
      Bounds b = new Bounds(1f, 2f, 3f, 9f);
      assertNotEquals(a, b);
   }

   @Test
   void equalsNullReturnsFalse() {
      Bounds a = new Bounds(1f, 2f, 3f, 4f);
      assertFalse(a.equals(null));
   }
}
