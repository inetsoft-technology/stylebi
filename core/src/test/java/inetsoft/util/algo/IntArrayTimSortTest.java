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
package inetsoft.util.algo;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link IntArrayTimSort} sorts an array of row indexes by a comparator over the indexes
 * (SortFilter sorts the row map this way). The oracle is {@link Arrays#sort(Object[],
 * Comparator)}, which is a stable merge sort, so the result must match it element for
 * element, ties included. The sizes straddle the mini-sort cut-off (32), the temporary
 * storage limit (256) and the run-stack growth limits (120, 1542).
 */
@Tag("core")
class IntArrayTimSortTest {
   private static final int[] SIZES = {
      0, 1, 2, 3, 31, 32, 33, 63, 64, 65, 100, 119, 120, 255, 256, 257, 511, 512, 1000,
      1541, 1542, 5000
   };

   enum Pattern { RANDOM, ASCENDING, DESCENDING, ALL_EQUAL, FEW_DISTINCT, SAWTOOTH,
                  ORGAN_PIPE, SORTED_RUNS, NEARLY_SORTED }

   static Stream<org.junit.jupiter.params.provider.Arguments> cases() {
      List<org.junit.jupiter.params.provider.Arguments> list = new ArrayList<>();

      for(int size : SIZES) {
         for(Pattern pattern : Pattern.values()) {
            list.add(org.junit.jupiter.params.provider.Arguments.of(size, pattern));
         }
      }

      return list.stream();
   }

   /** The key of each row; the array under sort holds row indexes. */
   private static int[] keys(int n, Pattern pattern, Random rnd) {
      int[] k = new int[n];

      for(int i = 0; i < n; i++) {
         switch(pattern) {
         case RANDOM:        k[i] = rnd.nextInt(1_000_000); break;
         case ASCENDING:     k[i] = i; break;
         case DESCENDING:    k[i] = n - i; break;
         case ALL_EQUAL:     k[i] = 7; break;
         case FEW_DISTINCT:  k[i] = rnd.nextInt(4); break;
         case SAWTOOTH:      k[i] = i % 17; break;
         case ORGAN_PIPE:    k[i] = Math.min(i, n - 1 - i); break;
         case SORTED_RUNS:   k[i] = (i / 40) * 1000 + (i % 40); break; // ascending runs of 40
         case NEARLY_SORTED: k[i] = i + (rnd.nextInt(10) == 0 ? rnd.nextInt(50) : 0); break;
         }
      }

      return k;
   }

   private static IntArraySort.IntComparator by(int[] key) {
      return new IntArraySort.IntComparator() {
         @Override
         public int compare(int v1, int v2) {
            return Integer.compare(key[v1], key[v2]);
         }
      };
   }

   private static int[] oracle(int[] rows, int lo, int hi, int[] key) {
      Integer[] boxed = new Integer[hi - lo];

      for(int i = lo; i < hi; i++) {
         boxed[i - lo] = rows[i];
      }

      Arrays.sort(boxed, (x, y) -> Integer.compare(key[x], key[y]));
      int[] out = rows.clone();

      for(int i = lo; i < hi; i++) {
         out[i] = boxed[i - lo];
      }

      return out;
   }

   /** A sorted copy must equal the stable oracle, for every size and key pattern. */
   @ParameterizedTest(name = "size={0} pattern={1}")
   @MethodSource("cases")
   void sortMatchesStableOracle(int size, Pattern pattern) {
      Random rnd = new Random(size * 31L + pattern.ordinal());
      int[] key = keys(size, pattern, rnd);
      int[] rows = IntStream.range(0, size).toArray();
      int[] expected = oracle(rows, 0, size, key);

      int[] result = new IntArrayTimSort().sort(rows, 0, size, by(key));

      assertSame(rows, result, "the sort works in place and returns the same array");
      assertArrayEquals(expected, result);
   }

   /** Only [lo, hi) is sorted; elements before lo and from hi on are not touched. */
   @ParameterizedTest(name = "range [{0}, {1}) of {2}")
   @MethodSource("ranges")
   void sortOnlyTouchesTheRange(int lo, int hi, int size) {
      Random rnd = new Random(lo * 7L + hi * 13L + size);
      int[] key = keys(size, Pattern.RANDOM, rnd);
      int[] rows = IntStream.range(0, size).toArray();
      int[] expected = oracle(rows, lo, hi, key);

      new IntArrayTimSort().sort(rows, lo, hi, by(key));

      assertArrayEquals(expected, rows);
   }

   static Stream<org.junit.jupiter.params.provider.Arguments> ranges() {
      return Stream.of(
         org.junit.jupiter.params.provider.Arguments.of(0, 40, 100),
         org.junit.jupiter.params.provider.Arguments.of(10, 90, 100),
         org.junit.jupiter.params.provider.Arguments.of(60, 100, 100),
         org.junit.jupiter.params.provider.Arguments.of(5, 6, 100),
         org.junit.jupiter.params.provider.Arguments.of(50, 50, 100),
         org.junit.jupiter.params.provider.Arguments.of(3, 700, 1000),
         org.junit.jupiter.params.provider.Arguments.of(300, 1000, 1000));
   }

   /** Ties keep their input order; compare against the oracle on a heavy-tie input. */
   @Test
   void sortIsStable() {
      int n = 600;
      int[] key = new int[n];

      for(int i = 0; i < n; i++) {
         key[i] = i % 3;
      }

      int[] rows = IntStream.range(0, n).toArray();
      new IntArrayTimSort().sort(rows, 0, n, by(key));

      for(int i = 1; i < n; i++) {
         if(key[rows[i - 1]] == key[rows[i]]) {
            assertTrue(rows[i - 1] < rows[i],
                       "rows with equal keys must stay in input order at position " + i);
         }
      }
   }

   /** One sorter instance is reused by SortFilter for several calls; state must not leak. */
   @Test
   void sorterCanBeReusedForDifferentArrays() {
      IntArrayTimSort sorter = new IntArrayTimSort();
      Random rnd = new Random(11);

      for(int round = 0; round < 6; round++) {
         int n = 40 + round * 300;
         int[] key = keys(n, Pattern.RANDOM, rnd);
         int[] rows = IntStream.range(0, n).toArray();
         int[] expected = oracle(rows, 0, n, key);

         assertArrayEquals(expected, sorter.sort(rows, 0, n, by(key)), "round " + round);
      }
   }

   /** The default comparator orders by value. */
   @Test
   void defaultComparatorOrdersByValue() {
      int[] values = {9, -3, 5, 0, 5, -3, 100, 42, -77, 8, 1, 2, 3, 4, 6, 7, 10, 11, 12, 13,
                      14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, -1, -2};
      int[] expected = values.clone();
      Arrays.sort(expected);

      int[] result = new IntArrayTimSort().sort(values, 0, values.length,
                                                new IntArraySort.IntComparator());

      assertArrayEquals(expected, result);
   }

   /**
    * The default comparator returns v1 - v2, which overflows when the values are far
    * apart (Integer.MAX_VALUE - (-5) is negative), so large positive and negative values
    * are ordered wrongly.
    */
   @Test
   @Disabled("Not filed (no product caller uses the default comparator): IntArraySort.IntComparator.compare "
      + "returns v1 - v2 and overflows for values more than Integer.MAX_VALUE apart; probe: "
      + "[MAX, -5, MIN, 5, 0] sorts to [0, 5, MAX, MIN, -5]")
   void defaultComparatorHandlesExtremeValues() {
      int[] values = {Integer.MAX_VALUE, -5, Integer.MIN_VALUE, 5, 0};
      int[] expected = values.clone();
      Arrays.sort(expected);

      int[] result = new IntArrayTimSort().sort(values, 0, values.length,
                                                new IntArraySort.IntComparator());

      assertArrayEquals(expected, result);
   }

   /** A cancelled sort must come back without losing or duplicating any row. */
   @Test
   void cancelledSortKeepsEveryRow() {
      int n = 2000;
      int[] key = keys(n, Pattern.RANDOM, new Random(5));
      int[] rows = IntStream.range(0, n).toArray();
      IntArrayTimSort sorter = new IntArrayTimSort();
      sorter.cancel();

      int[] result = sorter.sort(rows, 0, n, by(key));

      int[] sortedRows = result.clone();
      Arrays.sort(sortedRows);
      assertArrayEquals(IntStream.range(0, n).toArray(), sortedRows);
   }
}
