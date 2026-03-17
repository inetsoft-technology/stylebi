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
package inetsoft.mv.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BitSetTest {

   // ── add() sequential ──────────────────────────────────────────────────

   @Test
   void sequentialAddsFormRangeAfterComplete() {
      BitSet bs = new BitSet();
      bs.add(0);
      bs.add(1);
      bs.add(2);
      bs.complete();

      assertEquals(3, bs.rowCount());
      assertTrue(bs.get(0));
      assertTrue(bs.get(1));
      assertTrue(bs.get(2));
   }

   @Test
   void nonSequentialAddForcesCommitOfPreviousRange() {
      BitSet bs = new BitSet();
      // build range 0..2
      bs.add(0);
      bs.add(1);
      bs.add(2);
      // gap: adding 5 forces commit of [0,3) and starts new range at 5
      bs.add(5);
      bs.complete();

      assertEquals(4, bs.rowCount());
      assertTrue(bs.get(0));
      assertTrue(bs.get(1));
      assertTrue(bs.get(2));
      assertFalse(bs.get(3));
      assertFalse(bs.get(4));
      assertTrue(bs.get(5));
   }

   @Test
   void singleAddCommittedByComplete() {
      BitSet bs = new BitSet();
      bs.add(7);
      bs.complete();

      assertEquals(1, bs.rowCount());
      assertTrue(bs.get(7));
   }

   // ── set(int) ──────────────────────────────────────────────────────────

   @Test
   void setIndividualBit() {
      BitSet bs = new BitSet();
      bs.set(10);

      assertTrue(bs.get(10));
      assertFalse(bs.get(9));
      assertFalse(bs.get(11));
   }

   @Test
   void setDoesNotRequireComplete() {
      BitSet bs = new BitSet();
      bs.set(42);

      // set() writes directly to the bitmap; no complete() needed
      assertEquals(1, bs.rowCount());
      assertTrue(bs.get(42));
   }

   // ── set(int, int) ─────────────────────────────────────────────────────

   @Test
   void setRangeOfBits() {
      BitSet bs = new BitSet();
      // set rows 3..6 (non-inclusive end → rows 3,4,5,6 should be set? check impl)
      // The impl uses RoaringBitmap.flip(startrow, endrow) which is exclusive at end
      bs.set(3, 7); // sets bits 3,4,5,6

      assertTrue(bs.get(3));
      assertTrue(bs.get(4));
      assertTrue(bs.get(5));
      assertTrue(bs.get(6));
      assertFalse(bs.get(7));
      assertFalse(bs.get(2));
      assertEquals(4, bs.rowCount());
   }

   @Test
   void setRangeSingleBit() {
      BitSet bs = new BitSet();
      bs.set(5, 6); // exactly one bit: index 5

      assertTrue(bs.get(5));
      assertFalse(bs.get(6));
      assertEquals(1, bs.rowCount());
   }

   // ── complete() ────────────────────────────────────────────────────────

   @Test
   void completeCommitsPendingRange() {
      BitSet bs = new BitSet();
      bs.add(0);
      bs.add(1);

      // before complete() the bits are not yet committed
      bs.complete();

      assertEquals(2, bs.rowCount());
   }

   @Test
   void completeCalledTwiceIsIdempotent() {
      BitSet bs = new BitSet();
      bs.add(0);
      bs.add(1);
      bs.complete();
      bs.complete(); // second call should not double-count

      assertEquals(2, bs.rowCount());
   }

   @Test
   void afterCompleteNewAddsStartFresh() {
      BitSet bs = new BitSet();
      bs.add(0);
      bs.add(1);
      bs.complete();

      bs.add(5);
      bs.add(6);
      bs.complete();

      assertEquals(4, bs.rowCount());
      assertTrue(bs.get(0));
      assertTrue(bs.get(1));
      assertTrue(bs.get(5));
      assertTrue(bs.get(6));
   }

   // ── isEmpty / rowCount ────────────────────────────────────────────────

   @Test
   void newBitSetIsEmpty() {
      BitSet bs = new BitSet();
      assertTrue(bs.isEmpty());
      assertEquals(0, bs.rowCount());
   }

   @Test
   void notEmptyAfterSet() {
      BitSet bs = new BitSet();
      bs.set(1);
      assertFalse(bs.isEmpty());
   }

   // ── and / andNot / or / xor ───────────────────────────────────────────

   @Test
   void andReturnsIntersection() {
      BitSet a = new BitSet();
      a.set(1); a.set(2); a.set(3);

      BitSet b = new BitSet();
      b.set(2); b.set(3); b.set(4);

      BitSet result = a.and(b);

      assertFalse(result.get(1));
      assertTrue(result.get(2));
      assertTrue(result.get(3));
      assertFalse(result.get(4));
      assertEquals(2, result.rowCount());
   }

   @Test
   void andNotReturnsSubtraction() {
      BitSet a = new BitSet();
      a.set(1); a.set(2); a.set(3);

      BitSet b = new BitSet();
      b.set(2); b.set(3);

      BitSet result = a.andNot(b);

      assertTrue(result.get(1));
      assertFalse(result.get(2));
      assertFalse(result.get(3));
      assertEquals(1, result.rowCount());
   }

   @Test
   void orReturnsUnion() {
      BitSet a = new BitSet();
      a.set(1); a.set(2);

      BitSet b = new BitSet();
      b.set(3); b.set(4);

      BitSet result = a.or(b);

      assertTrue(result.get(1));
      assertTrue(result.get(2));
      assertTrue(result.get(3));
      assertTrue(result.get(4));
      assertEquals(4, result.rowCount());
   }

   @Test
   void xorReturnsSymmetricDifference() {
      BitSet a = new BitSet();
      a.set(1); a.set(2); a.set(3);

      BitSet b = new BitSet();
      b.set(2); b.set(3); b.set(4);

      BitSet result = a.xor(b);

      assertTrue(result.get(1));
      assertFalse(result.get(2));
      assertFalse(result.get(3));
      assertTrue(result.get(4));
      assertEquals(2, result.rowCount());
   }
}
