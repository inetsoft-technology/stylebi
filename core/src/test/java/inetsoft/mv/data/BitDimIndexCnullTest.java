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
package inetsoft.mv.data;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #79403: the dictionary's {@code cnull} flag only says the dictionary has a null slot
 * (key 0). A block can carry that slot without a row for it (incremental append, or delete of
 * the only null rows), so {@link BitDimIndex} must skip key 0 and not the first position of
 * its sorted keys, or the rows of the smallest real key are silently dropped.
 *
 * <p>Reporter's data [10, 20, 30, 50, 10, 20] has dictionary indices 1..4 (0 is the null slot):
 * row 0 and row 4 share the smallest key 1.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class BitDimIndexCnullTest {
   // ---- cnull=true, no null row (the bug) ----

   @Test
   void notEqualsKeepsSmallestKey() {
      assertEquals(rows(0, 2, 3, 4), noNull().getRows("=", 2, true, true));
   }

   @Test
   void notEqualsSmallestKey() {
      assertEquals(rows(1, 2, 3, 5), noNull().getRows("=", 1, true, true));
   }

   @Test
   void notInKeepsSmallestKey() {
      assertEquals(rows(0, 3, 4), noNull().getRows("IN", new long[] { 2, 3 }, true, true));
   }

   @Test
   void lessThanKeepsSmallestKey() {
      assertEquals(rows(0, 1, 4, 5), noNull().getRows("<", 3, true));
   }

   @Test
   void lessOrEqualsKeepsSmallestKey() {
      assertEquals(rows(0, 1, 4, 5), noNull().getRows("<=", 2, true));
   }

   @Test
   void notBetweenKeepsSmallestKey() {
      assertEquals(rows(0, 3, 4), noNull().getRows("BETWEEN", new long[] { 2, 3 }, true, true));
   }

   @Test
   void emptyNotInReturnsAllRows() {
      assertEquals(rows(0, 1, 2, 3, 4, 5),
                   noNull().getRows("IN", new long[0], true, true));
      assertEquals(rows(0, 1, 2, 3, 4, 5), noNull().getAllRows(true));
   }

   @Test
   void notGreaterOrEqualsKeepsSmallestKey() {
      assertEquals(rows(0, 1, 4, 5), noNull().getRows(">=", 3, true, true));
   }

   // ---- controls: null row present ----

   @Test
   void nullRowStillExcluded() {
      // keys: row 0 null (0), rows 1.. -> 1,2,3,4,1,2
      BitDimIndex idx = index(0, 1, 2, 3, 4, 1, 2);
      assertEquals(rows(2, 3, 4, 6), idx.getRows("=", 1, true, true));
      assertEquals(rows(1, 2, 5, 6), idx.getRows("<", 3, true));
      assertEquals(rows(1, 2, 3, 5, 6), idx.getRows("<=", 3, true));
      assertEquals(rows(1, 2, 3, 4, 5, 6), idx.getAllRows(true));
      assertEquals(rows(1, 2, 3, 4, 5, 6), idx.getRows("IN", new long[0], true, true));
   }

   @Test
   void isNotNullWithStaleSlot() {
      assertEquals(rows(0, 1, 2, 3, 4, 5), noNull().getRows("null", 0, true, true));
      assertEquals(rows(1, 2, 3, 4, 5, 6),
                   index(0, 1, 2, 3, 4, 1, 2).getRows("null", 0, true, true));
   }

   @Test
   void cnullFalseUnchanged() {
      // key 0 is a real value when the dictionary has no null slot
      BitDimIndex idx = index(0, 1, 2, 0);
      assertEquals(rows(0, 1, 3), idx.getRows("<", 2, false));
      assertEquals(rows(0, 1, 2, 3), idx.getAllRows(false));
      assertEquals(rows(1, 2), idx.getRows("=", 0, true, false));
   }

   @Test
   void positiveOperatorsUnchanged() {
      BitDimIndex idx = noNull();
      assertEquals(rows(1, 5), idx.getRows("=", 2, true));
      assertEquals(rows(1, 2, 5), idx.getRows("IN", new long[] { 2, 3 }, true));
      assertEquals(rows(2, 3), idx.getRows(">", 2, true));
      assertEquals(rows(1, 2, 3, 5), idx.getRows(">=", 2, true));
      assertEquals(rows(1, 2, 5), idx.getRows("BETWEEN", new long[] { 2, 3 }, true));
   }

   // ---- edge cases ----

   @Test
   void emptyBlock() {
      assertEquals(rows(), index().getAllRows(true));
      assertEquals(rows(), index().getRows("<", 3, true));
   }

   @Test
   void singleRealKey() {
      BitDimIndex idx = index(1, 1);
      assertEquals(rows(0, 1), idx.getAllRows(true));
      assertEquals(rows(0, 1), idx.getRows("=", 5, true, true));
      assertEquals(rows(), idx.getRows("=", 1, true, true));
   }

   @Test
   void allNullBlock() {
      BitDimIndex idx = index(0, 0, 0);
      assertEquals(rows(), idx.getAllRows(true));
      assertEquals(rows(), idx.getRows("<", 3, true));
   }

   @Test
   void gapBeforeFirstKey() {
      // number-range dictionaries can start real keys above 1
      BitDimIndex idx = index(2, 3, 2);
      assertEquals(rows(0, 1, 2), idx.getAllRows(true));
      assertEquals(rows(0, 2), idx.getRows("<", 3, true));
   }

   /** Reporter's data [10, 20, 30, 50, 10, 20] with a stale null slot (keys 1..4). */
   private static BitDimIndex noNull() {
      return index(1, 2, 3, 4, 1, 2);
   }

   /** Build an index where {@code keys[row]} is the dictionary index of each row. */
   private static BitDimIndex index(int... keys) {
      BitDimIndex idx = new BitDimIndex();

      for(int row = 0; row < keys.length; row++) {
         idx.addKey(keys[row], row);
      }

      idx.complete();
      return idx;
   }

   private static BitSet rows(int... rows) {
      BitSet set = new BitSet();

      for(int row : rows) {
         set.set(row);
      }

      return set;
   }
}
