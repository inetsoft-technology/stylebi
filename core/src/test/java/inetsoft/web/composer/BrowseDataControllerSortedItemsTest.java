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
package inetsoft.web.composer;

import inetsoft.test.*;
import inetsoft.uql.AbstractCondition;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80105: the Browse dialog de-duplicated the values of a data source column with
 * List.contains, which is quadratic. These tests pin the old output (values and order) and
 * guard the scaling.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class BrowseDataControllerSortedItemsTest {
   /** The algorithm used before the fix. */
   private static Object[] legacy(List<Object> raw, String dtype) {
      ArrayList<Object> dlist = new ArrayList<>();

      for(Object data : raw) {
         if(data != null && !dlist.contains(data)) {
            dlist.add(data);
         }
      }

      Object[] arr = dlist.toArray();
      Tool.qsort(arr, true);
      Object[] items = new Object[arr.length];

      for(int i = 0; i < arr.length; i++) {
         items[i] = isNumber(dtype) ? arr[i] : AbstractCondition.getValueString(arr[i], dtype);
      }

      return items;
   }

   private static boolean isNumber(String dtype) {
      return inetsoft.uql.asset.internal.AssetUtil.isNumberType(dtype);
   }

   private static void assertSame(List<Object> raw, String dtype) {
      assertArrayEquals(legacy(raw, dtype), BrowseDataController.toSortedItems(raw, dtype));
   }

   @Test
   void stringsWithDuplicatesNullsAndCase() {
      assertSame(Arrays.asList("b", null, "a", "B", "b", "A", "", null, "a ", "10", "9"),
                 XSchema.STRING);
   }

   @Test
   void mixedNumericClasses() {
      // 1 (Integer), 1L, 1.0 are not equals() to each other and must all survive
      assertSame(Arrays.asList(3, 1, 1L, 1.0, 2.5, 3, 3L, (short) 2, 2.5f, 2.5, null, 1),
                 XSchema.DOUBLE);
      assertSame(Arrays.asList(3, 1, 1L, 1.0, 2.5, 3, 3L, (short) 2, 2.5f, 2.5, null, 1),
                 XSchema.STRING);
   }

   @Test
   void mixedStringsAndNumbers() {
      assertSame(Arrays.asList("x", 2, "1", 1, "2", 1.5, "abc", "", 7L), XSchema.STRING);
   }

   @Test
   void datesTimesAndTimestamps() {
      long base = 1_700_000_000_000L;
      List<Object> raw = new ArrayList<>();

      for(int i = 0; i < 6; i++) {
         long t = base + (i % 4) * 86_400_000L;
         raw.add(new java.sql.Date(t));
         raw.add(new java.sql.Timestamp(t));
         raw.add(new java.util.Date(t));
         raw.add(new java.sql.Time(t));
      }

      raw.add(null);
      assertSame(raw, XSchema.DATE);
      assertSame(raw, XSchema.TIME_INSTANT);
      assertSame(raw, XSchema.TIME);
   }

   @Test
   void allNullsAndEmpty() {
      assertEquals(0, BrowseDataController.toSortedItems(Arrays.asList(null, null),
                                                         XSchema.STRING).length);
      assertEquals(0, BrowseDataController.toSortedItems(new ArrayList<>(),
                                                         XSchema.STRING).length);
   }

   @Test
   void randomMixedInputMatchesLegacy() {
      Random r = new Random(42);
      List<Object> raw = new ArrayList<>();

      for(int i = 0; i < 3000; i++) {
         switch(r.nextInt(6)) {
         case 0: raw.add("s" + r.nextInt(400)); break;
         case 1: raw.add("S" + r.nextInt(400)); break;
         case 2: raw.add(r.nextInt(400)); break;
         case 3: raw.add((long) r.nextInt(400)); break;
         case 4: raw.add(r.nextInt(400) / 4.0); break;
         default: raw.add(null);
         }
      }

      assertSame(raw, XSchema.STRING);
      assertSame(raw, XSchema.DOUBLE);
   }

   @Test
   void largeDistinctSetIsNotQuadratic() {
      int n = 60_000;
      Random r = new Random(1);
      List<Object> raw = new ArrayList<>();

      for(int i = 0; i < n; i++) {
         raw.add("Customer_" + Integer.toHexString(r.nextInt()) + "_" + i);
      }

      raw.addAll(new ArrayList<>(raw.subList(0, n / 10)));
      Collections.shuffle(raw, r);

      // the quadratic version needs more than 10 seconds for this input
      Object[] items = assertTimeoutPreemptively(
         Duration.ofSeconds(8), () -> BrowseDataController.toSortedItems(raw, XSchema.STRING));
      assertEquals(n, items.length);
   }
}
