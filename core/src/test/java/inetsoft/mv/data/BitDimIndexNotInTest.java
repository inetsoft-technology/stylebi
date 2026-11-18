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

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #79406: a NOT IN list of several values on a {@link BitDimIndex} is evaluated as
 * (all rows) minus (rows of the listed keys). It must return exactly what the per-value path
 * returns (the AND of {@code getRows("=", v, true, cnull)}), including the corner cases where
 * cnull skips key 0 and where listed values sort below the first key.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class BitDimIndexNotInTest {
   @Test
   void basic() {
      BitDimIndex idx = index(1, 2, 3, 4, 1, 2);
      assertEquals(rows(0, 4, 3), idx.getRows("IN", new long[] { 2, 3 }, true, true));
      assertEquals(rows(2, 3), idx.getRows("IN", new long[] { 1, 2, 9 }, true, false));
   }

   @Test
   void repeatedListsUseCachedAllRows() {
      BitDimIndex idx = index(0, 1, 2, 3, 4, 1, 2);
      BitSet first = idx.getRows("IN", new long[] { 1, 2 }, true, true);
      assertEquals(first, idx.getRows("IN", new long[] { 1, 2 }, true, true));
      assertEquals(rows(1, 2, 5, 6), idx.getRows("IN", new long[] { 3, 4 }, true, true));
   }

   @Test
   void duplicatesAndMissingValues() {
      BitDimIndex idx = index(1, 2, 3, 1);
      assertEquals(rows(1, 2), idx.getRows("IN", new long[] { 1, 1, 7 }, true, true));
      assertEquals(rows(0, 1, 2, 3), idx.getRows("IN", new long[] { 7, 8 }, true, true));
   }

   @Test
   void emptyAndOpenEndMarkerKeepGenericPath() {
      BitDimIndex idx = index(1, 2, 3);
      assertEquals(reference(idx, new long[] { Integer.MIN_VALUE, 2 }, true),
                   idx.getRows("IN", new long[] { Integer.MIN_VALUE, 2 }, true, true));
      assertEquals(rows(0, 1, 2), idx.getRows("IN", new long[0], true, true));
   }

   @Test
   void emptyBlock() {
      assertEquals(rows(), index().getRows("IN", new long[] { 1, 2 }, true, true));
      assertEquals(rows(), index().getRows("IN", new long[] { 1, 2 }, true, false));
   }

   @Test
   void positiveInUnchanged() {
      BitDimIndex idx = index(1, 2, 3, 4, 1, 2);
      assertEquals(rows(1, 2, 5), idx.getRows("IN", new long[] { 2, 3 }, false, true));
   }

   /** Random indexes (keys with gaps, with and without key 0, some negative) vs the old path. */
   @Test
   void randomEquivalence() {
      Random rnd = new Random(79406);
      int checked = 0;

      for(int trial = 0; trial < 3000; trial++) {
         boolean cnull = rnd.nextBoolean();
         int nrow = rnd.nextInt(40);
         int lo = rnd.nextInt(4) == 0 ? -3 : 0;
         int span = 1 + rnd.nextInt(12);
         int[] keys = new int[nrow];

         for(int i = 0; i < nrow; i++) {
            keys[i] = lo + rnd.nextInt(span) * (1 + rnd.nextInt(2));
         }

         BitDimIndex idx = index(keys);

         for(int q = 0; q < 6; q++) {
            long[] vals = new long[1 + rnd.nextInt(6)];

            for(int i = 0; i < vals.length; i++) {
               vals[i] = lo - 2 + rnd.nextInt(2 * span + 5);
            }

            assertEquals(reference(idx, vals, cnull), idx.getRows("IN", vals, true, cnull),
                         "keys=" + java.util.Arrays.toString(keys) + " vals=" +
                         java.util.Arrays.toString(vals) + " cnull=" + cnull);
            checked++;
         }
      }

      assertEquals(18000, checked);
   }

   /** The per-value path: AND over the values of "key != v". */
   private static BitSet reference(BitDimIndex idx, long[] vals, boolean cnull) {
      BitSet res = null;

      for(long v : vals) {
         BitSet set = idx.getRows("=", v, true, cnull);
         res = res == null ? set : res.and(set);
      }

      return res == null ? new BitSet() : res;
   }

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
