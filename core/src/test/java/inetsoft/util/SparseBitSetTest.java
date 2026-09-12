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
package inetsoft.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SparseBitSetTest {

   @Test
   void setAndGetBit() {
      SparseBitSet bs = new SparseBitSet();
      bs.set(5);
      assertTrue(bs.get(5));
   }

   @Test
   void unsetBitReturnsFalse() {
      SparseBitSet bs = new SparseBitSet();
      assertFalse(bs.get(42));
   }

   @Test
   void andIntersection() {
      SparseBitSet a = new SparseBitSet();
      a.set(1);
      a.set(2);
      a.set(3);

      SparseBitSet b = new SparseBitSet();
      b.set(2);
      b.set(3);
      b.set(4);

      a.and(b);

      assertFalse(a.get(1));
      assertTrue(a.get(2));
      assertTrue(a.get(3));
      assertFalse(a.get(4));
   }

   @Test
   void andDisjointProducesEmptySet() {
      SparseBitSet a = new SparseBitSet();
      a.set(1);
      a.set(2);

      SparseBitSet b = new SparseBitSet();
      b.set(3);
      b.set(4);

      a.and(b);

      assertEquals(0, a.size());
      assertFalse(a.get(1));
      assertFalse(a.get(2));
   }

   @Test
   void andNotDifference() {
      SparseBitSet a = new SparseBitSet();
      a.set(1);
      a.set(2);
      a.set(3);

      SparseBitSet b = new SparseBitSet();
      b.set(2);
      b.set(3);

      a.andNot(b);

      assertTrue(a.get(1));
      assertFalse(a.get(2));
      assertFalse(a.get(3));
   }

   @Test
   void orUnion() {
      SparseBitSet a = new SparseBitSet();
      a.set(1);
      a.set(2);

      SparseBitSet b = new SparseBitSet();
      b.set(3);
      b.set(4);

      a.or(b);

      assertTrue(a.get(1));
      assertTrue(a.get(2));
      assertTrue(a.get(3));
      assertTrue(a.get(4));
   }

   @Test
   void xorSymmetricDifference() {
      SparseBitSet a = new SparseBitSet();
      a.set(1);
      a.set(2);
      a.set(3);

      SparseBitSet b = new SparseBitSet();
      b.set(2);
      b.set(3);
      b.set(4);

      a.xor(b);

      assertTrue(a.get(1));
      assertFalse(a.get(2));
      assertFalse(a.get(3));
      assertTrue(a.get(4));
   }

   @Test
   void clearSpecificBit() {
      SparseBitSet bs = new SparseBitSet();
      bs.set(10);
      bs.set(20);

      bs.clear(10);

      assertFalse(bs.get(10));
      assertTrue(bs.get(20));
   }

   @Test
   void cardinalityAfterSetsAndClears() {
      SparseBitSet bs = new SparseBitSet();
      bs.set(1);
      bs.set(2);
      bs.set(3);
      bs.set(4);
      assertEquals(4, bs.size());

      bs.clear(2);
      assertEquals(3, bs.size());
   }

   @Test
   void nextSetBitFindsNextSet() {
      SparseBitSet bs = new SparseBitSet();
      bs.set(5);
      bs.set(10);
      bs.set(20);

      assertEquals(5, bs.nextSetBit(0));
      assertEquals(10, bs.nextSetBit(6));
      assertEquals(20, bs.nextSetBit(11));
   }

   @Test
   void nextSetBitReturnsNegativeOneWhenNoneLeft() {
      SparseBitSet bs = new SparseBitSet();
      bs.set(3);

      // RoaringBitmap.nextValue(long) returns (long)Integer.MAX_VALUE + 1 when absent;
      // that value cast to int is -1.
      int result = bs.nextSetBit(4);
      assertEquals(-1, result);
   }
}
