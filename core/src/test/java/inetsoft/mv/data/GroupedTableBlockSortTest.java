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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the sort of {@link GroupedTableBlock#complete()} ({@code sortRows}) against the
 * original {@link MVRow.RowComparator} sort, and that {@link MVRow#hashCode()} spreads the
 * groups of multi-dimension blocks (Bug #79408).
 *
 * <p>Neither the rows nor the sort touch any Spring bean, so no context is needed.
 */
@Tag("core")
class GroupedTableBlockSortTest {
   @Test
   void packedSortMatchesComparatorOnRandomCases() {
      Random rnd = new Random(79408);

      for(int c = 0; c < 3000; c++) {
         int d = rnd.nextInt(5);
         int n = 1 + rnd.nextInt(c % 10 == 0 ? 2000 : 60);
         boolean[] orders = new boolean[d];
         long[] base = new long[d];
         long[] span = new long[d];

         for(int i = 0; i < d; i++) {
            orders[i] = rnd.nextBoolean();

            switch(rnd.nextInt(5)) {
            case 0:
               base[i] = 0;
               span[i] = 1 + rnd.nextInt(50);
               break;
            case 1:
               base[i] = -rnd.nextInt(1000);
               span[i] = 1 + rnd.nextInt(2000);
               break;
            case 2:
               base[i] = Long.MIN_VALUE;
               span[i] = 1 + rnd.nextInt(100);
               break;
            case 3:
               base[i] = Long.MAX_VALUE - 100;
               span[i] = 100;
               break;
            default:
               // range overflows long and forces the comparator fallback
               base[i] = Long.MIN_VALUE;
               span[i] = Long.MAX_VALUE;
               break;
            }
         }

         assertSameOrder(randomRows(rnd, n, d, base, span), orders, "case " + c);
      }
   }

   @Test
   void fallsBackWhenPackedKeyExceeds62Bits() {
      Random rnd = new Random(1);
      List<MVRow> rows = new ArrayList<>();
      Set<List<Long>> seen = new HashSet<>();

      // 3 dims of ~31 bits each: 93 bits, cannot be packed
      while(rows.size() < 500) {
         long[] g = { rnd.nextInt() & 0x7FFFFFFFL, rnd.nextInt() & 0x7FFFFFFFL,
                      rnd.nextInt() & 0x7FFFFFFFL };

         if(seen.add(Arrays.asList(g[0], g[1], g[2]))) {
            rows.add(new MVRow(g, new double[0]));
         }
      }

      assertSameOrder(rows, new boolean[] { true, false, true }, "wide");
   }

   @Test
   void singleRowAndMissingOrdersDoNotThrow() {
      List<MVRow> one = new ArrayList<>();
      one.add(new MVRow(new long[] { 5 }, new double[0]));

      assertDoesNotThrow(() -> GroupedTableBlock.sortRows(one, null));
      assertDoesNotThrow(() -> GroupedTableBlock.sortRows(one, new boolean[0]));
      assertEquals(1, one.size());
      assertDoesNotThrow(() -> GroupedTableBlock.sortRows(new ArrayList<>(), null));
   }

   /**
    * 2 dimensions x 1000 dictionary indices used to hash to ~32k distinct values (31 * a + b),
    * making every {@code RowMap} lookup walk a chain of ~28 nodes.
    */
   @Test
   void hashCodeSpreadsTwoDimensionGroups() {
      Set<Integer> hashes = new HashSet<>();

      for(int a = 0; a < 1000; a++) {
         for(int b = 0; b < 1000; b++) {
            hashes.add(new MVRow(new long[] { a, b }, new double[0]).hashCode());
         }
      }

      assertTrue(hashes.size() > 950_000, "distinct hashes: " + hashes.size());
   }

   @Test
   void hashCodeSpreadsThreeDimensionGroups() {
      Set<Integer> hashes = new HashSet<>();

      for(int a = 0; a < 100; a++) {
         for(int b = 0; b < 100; b++) {
            for(int c = 0; c < 100; c++) {
               hashes.add(new MVRow(new long[] { a, b, c }, new double[0]).hashCode());
            }
         }
      }

      assertTrue(hashes.size() > 950_000, "distinct hashes: " + hashes.size());
   }

   @Test
   void hashCodeOfSingleDimensionIsTheValue() {
      assertEquals(7, new MVRow(new long[] { 7 }, new double[0]).hashCode());
   }

   private static List<MVRow> randomRows(Random rnd, int n, int d, long[] base, long[] span) {
      List<MVRow> rows = new ArrayList<>();
      Set<List<Long>> seen = new HashSet<>();

      for(int r = 0; r < n; r++) {
         long[] g = new long[d];
         List<Long> key = new ArrayList<>();

         for(int i = 0; i < d; i++) {
            long off = span[i] == Long.MAX_VALUE ? rnd.nextLong() & Long.MAX_VALUE :
               (long) (rnd.nextDouble() * span[i]);
            g[i] = base[i] + off;

            // exercise the extremes of the range
            if(rnd.nextInt(20) == 0) {
               g[i] = rnd.nextBoolean() ? base[i] : base[i] + span[i];
            }

            key.add(g[i]);
         }

         // groups are unique in a block (RowMap)
         if(seen.add(key)) {
            rows.add(new MVRow(g, new double[0]));
         }
      }

      Collections.shuffle(rows, rnd);
      return rows;
   }

   private static void assertSameOrder(List<MVRow> rows, boolean[] orders, String msg) {
      List<MVRow> expected = new ArrayList<>(rows);
      List<MVRow> actual = new ArrayList<>(rows);

      if(expected.size() > 1) {
         Collections.sort(expected, new MVRow.RowComparator(orders));
      }

      GroupedTableBlock.sortRows(actual, orders);
      assertEquals(expected.size(), actual.size(), msg);

      for(int i = 0; i < expected.size(); i++) {
         assertSame(expected.get(i), actual.get(i), msg + " at " + i);
      }
   }
}
