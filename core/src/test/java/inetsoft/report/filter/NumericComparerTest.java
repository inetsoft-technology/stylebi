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
package inetsoft.report.filter;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link NumericComparer#compare(Object, Object)}.
 */
@Tag("core")
class NumericComparerTest {
   private final NumericComparer comparer = new NumericComparer();

   @Test
   void compare_datesByTime() {
      java.sql.Date jan = java.sql.Date.valueOf("2024-01-01");
      java.sql.Date mar = java.sql.Date.valueOf("2024-03-01");

      assertTrue(comparer.compare(jan, mar) < 0);
      assertTrue(comparer.compare(mar, jan) > 0);
      assertEquals(0, comparer.compare(jan, java.sql.Date.valueOf("2024-01-01")));
   }

   @Test
   void compare_mixedDateSubclassesByTime() {
      Date d = new Date(5_000L);
      Timestamp ts = new Timestamp(5_000L);
      Time later = new Time(6_000L);

      assertEquals(0, comparer.compare(d, ts));
      assertEquals(0, comparer.compare(ts, d));
      assertTrue(comparer.compare(ts, later) < 0);
      assertTrue(comparer.compare(later, d) > 0);
   }

   @Test
   void compare_dateRanksAfterUnparsableAndNullAndBeforeNumbers() {
      Date d = new Date(5_000L);

      assertTrue(comparer.compare(d, 1) < 0);
      assertTrue(comparer.compare(1, d) > 0);
      assertTrue(comparer.compare(d, "42") < 0);
      assertTrue(comparer.compare("42", d) > 0);
      assertTrue(comparer.compare(d, "n/a") > 0);
      assertTrue(comparer.compare("n/a", d) < 0);
      assertTrue(comparer.compare(d, null) > 0);
      assertTrue(comparer.compare(null, d) < 0);
   }

   @Test
   void compare_nonDateBehaviorUnchanged() {
      assertTrue(comparer.compare(1, 2) < 0);
      assertTrue(comparer.compare(2.5, 1) > 0);
      assertEquals(0, comparer.compare(3, 3.0));
      assertTrue(comparer.compare("10", 9) > 0);
      assertTrue(comparer.compare("2", "10") < 0);
      assertEquals(0, comparer.compare("n/a", "x"));
      assertEquals(-1, comparer.compare("n/a", 1));
      assertEquals(1, comparer.compare(1, "n/a"));
      assertEquals(0, comparer.compare(null, null));
      assertEquals(0, comparer.compare(null, "n/a"));
      assertEquals(-1, comparer.compare(null, 1));
      assertEquals(1, comparer.compare(1, null));
   }

   @Test
   void sort_mixedValues_doesNotThrowAndIsOrdered() {
      Random random = new Random(80583);

      for(int trial = 0; trial < 500; trial++) {
         List<Object> values = new ArrayList<>();

         for(int i = 0; i < 64; i++) {
            switch(random.nextInt(6)) {
            case 0 -> values.add(new java.sql.Date(random.nextInt(1000) * 86_400_000L));
            case 1 -> values.add(new Timestamp(random.nextInt(1000) * 86_400_000L));
            case 2 -> values.add(random.nextInt(100));
            case 3 -> values.add(String.valueOf(random.nextInt(100)));
            case 4 -> values.add("n/a");
            default -> values.add(null);
            }
         }

         Object[] arr = values.toArray();
         assertDoesNotThrow(() -> Arrays.sort(arr, comparer::compare));

         for(int i = 1; i < arr.length; i++) {
            assertTrue(comparer.compare(arr[i - 1], arr[i]) <= 0,
                       "not sorted at " + i + ": " + arr[i - 1] + " > " + arr[i]);
         }
      }
   }

   @Test
   void compare_isAntisymmetricAndTransitiveOverSampleValues() {
      Object[] samples = {
         null, "n/a", "x", new Date(1_000L), new java.sql.Date(2_000L),
         new Timestamp(2_000L), new Time(3_000L), -5, "0", 0.5, "7", 100L
      };

      for(Object a : samples) {
         assertEquals(0, comparer.compare(a, a));

         for(Object b : samples) {
            assertEquals(Integer.signum(comparer.compare(a, b)),
                         -Integer.signum(comparer.compare(b, a)),
                         "not antisymmetric: " + a + ", " + b);

            for(Object c : samples) {
               if(comparer.compare(a, b) <= 0 && comparer.compare(b, c) <= 0) {
                  assertTrue(comparer.compare(a, c) <= 0,
                             "not transitive: " + a + ", " + b + ", " + c);
               }
            }
         }
      }
   }

   /**
    * Random lists per sort test. 13.8 runs 20000; StyleBI's NumberParser throws on non-numeric
    * text, which makes 20000 take about 30 s per test, so the class would need @Tag("slow").
    * 2000 lists of 2 to 150 values still reach TimSort's comparator contract check.
    */
   private static final int ITERATIONS = 2000;

   /**
    * The StyleBI NumericComparer of before Bug #80583 (13.8: #80539), the order every non-date
    * list must keep. StyleBI parses with org.jnumbers.NumberParser, 13.8 with
    * NumberParserWrapper, so blank and non-numeric strings rank differently between the two.
    */
   private static final Comparator<Object> BASE_ORDER = (v1, v2) -> {
      double val1 = Double.NaN;
      double val2 = Double.NaN;

      try {
         val1 = (v1 instanceof Number) ? ((Number) v1).doubleValue() :
            org.jnumbers.NumberParser.getDouble(v1.toString());
      }
      catch(Exception ex) {
         v1 = null;
      }

      try {
         val2 = (v2 instanceof Number) ? ((Number) v2).doubleValue() :
            org.jnumbers.NumberParser.getDouble(v2.toString());
      }
      catch(Exception ex) {
         v2 = null;
      }

      if(v1 == null) {
         return (v2 == null) ? 0 : -1;
      }
      else if(v2 == null) {
         return 1;
      }

      return Double.compare(val1, val2);
   };

   private static Object randomValue(Random random, boolean dates) {
      int kind = random.nextInt(dates ? 9 : 7);

      switch(kind) {
      case 0: return null;
      case 1: return "";
      case 2: return " ";
      case 3: return "abc";
      case 4: return Integer.toString(random.nextInt(20) - 5);
      case 5: return random.nextInt(20) - 5;
      case 6: return random.nextInt(8) / 2.0;
      case 7: return new Date(random.nextInt(6) * 1_000_000L);
      default: return new Timestamp(random.nextInt(6) * 1_000_000L);
      }
   }

   // Bug #80539: a list mixing dates with other values (blank strings among them) sorts without
   // an error
   @Test
   void sort_mixedDateListsWithBlanks_keepsComparatorContract() {
      Random random = new Random(80539L);
      int failures = 0;

      for(int i = 0; i < ITERATIONS; i++) {
         int size = 2 + random.nextInt(150);
         List<Object> list = new ArrayList<>();

         for(int j = 0; j < size; j++) {
            list.add(randomValue(random, true));
         }

         try {
            Object[] array = list.toArray();
            Arrays.sort(array, comparer);
            Collections.sort(list, comparer);
         }
         catch(IllegalArgumentException ex) {
            failures++;
         }
      }

      assertEquals(0, failures, "lists whose sort violated the comparator contract");
   }

   // Bug #80539: lists without dates keep the order of the comparer before the fix
   @Test
   void sort_nonDateLists_keepBaseOrder() {
      Random random = new Random(80540L);

      for(int i = 0; i < ITERATIONS; i++) {
         int size = 2 + random.nextInt(150);
         Object[] expected = new Object[size];

         for(int j = 0; j < size; j++) {
            expected[j] = randomValue(random, false);
         }

         Object[] actual = expected.clone();
         Arrays.sort(expected, BASE_ORDER);
         Arrays.sort(actual, comparer);
         assertArrayEquals(expected, actual);
      }
   }
}
