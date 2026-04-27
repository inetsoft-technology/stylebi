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
package inetsoft.graph.internal;

import inetsoft.report.filter.DefaultComparer;
import inetsoft.report.filter.SortOrder;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FirstDayComparator}.
 *
 * Day-of-week integer values (Calendar constants):
 *   SUNDAY=1, MONDAY=2, TUESDAY=3, WEDNESDAY=4, THURSDAY=5, FRIDAY=6, SATURDAY=7
 *
 * The algorithm: if a day value < firstDay, add 7 to it so that days
 * before the first day of the week appear at the end (after the last day).
 *
 * IMPORTANT: Because FirstDayComparator extends DefaultComparer (which inherits
 * compare(int,int) from DefaultComparer), calling comp.compare(3, 2) where comp
 * is typed as FirstDayComparator will resolve to compare(int,int) NOT
 * compare(Object,Object). To exercise the day-of-week logic in compare(Object,Object),
 * we must cast to Comparator or pass boxed Integer values explicitly.
 */
@SuppressWarnings("unchecked")
class FirstDayComparatorTest {

   // Helper: invoke the compare(Object, Object) method using the Comparator interface
   private static int cmp(FirstDayComparator comp, Object v1, Object v2) {
      return ((Comparator<Object>) comp).compare(v1, v2);
   }

   // ---- SortOrder (Crosstab) constructor — Ascending ----

   @Test
   void ascendingWithSundayFirstDaySunLessThanMon() {
      SortOrder sortOrder = new SortOrder(SortOrder.SORT_ASC);
      FirstDayComparator comp = new FirstDayComparator(sortOrder);

      // In US locale (SUNDAY=1 first), all days 1-7 are >= 1, so no shift applied.
      assertTrue(cmp(comp, 1, 2) < 0, "Sun < Mon ascending");
      assertTrue(cmp(comp, 2, 1) > 0, "Mon > Sun ascending");
      assertEquals(0, cmp(comp, 3, 3), "Tue == Tue");
   }

   @Test
   void ascendingEarlierDayBeforeLaterDay() {
      SortOrder sortOrder = new SortOrder(SortOrder.SORT_ASC);
      FirstDayComparator comp = new FirstDayComparator(sortOrder);

      assertTrue(cmp(comp, 2, 6) < 0, "Mon < Fri");
      assertTrue(cmp(comp, 6, 7) < 0, "Fri < Sat");
   }

   @Test
   void descendingReverseOrderForAdjacentDays() {
      SortOrder sortOrder = new SortOrder(SortOrder.SORT_DESC);
      FirstDayComparator comp = new FirstDayComparator(sortOrder);

      // Tuesday(3) > Monday(2); in desc, larger day → result negative
      assertTrue(cmp(comp, 3, 2) < 0, "Tue > Mon in desc → compare(Tue,Mon) < 0");
      assertTrue(cmp(comp, 2, 3) > 0, "Mon < Tue in desc → compare(Mon,Tue) > 0");
      assertEquals(0, cmp(comp, 3, 3), "Tue == Tue");
   }

   @Test
   void descendingWednesdayBeforeMonday() {
      SortOrder sortOrder = new SortOrder(SortOrder.SORT_DESC);
      FirstDayComparator comp = new FirstDayComparator(sortOrder);

      // Wednesday(4) > Monday(2) → in desc compare(Wed, Mon) < 0
      assertTrue(cmp(comp, 4, 2) < 0, "Wed 'before' Mon in desc");
      assertTrue(cmp(comp, 2, 4) > 0);
   }

   // ---- Comparator (Chart) constructor ----

   @Test
   void chartComparatorAscendingOrder() {
      DefaultComparer delegate = new DefaultComparer();
      delegate.setNegate(false); // ascending
      FirstDayComparator comp = new FirstDayComparator((Comparator<?>) delegate);

      assertTrue(cmp(comp, 2, 5) < 0, "Mon < Thu");
      assertTrue(cmp(comp, 7, 3) > 0, "Sat > Tue");
      assertEquals(0, cmp(comp, 4, 4));
   }

   @Test
   void chartComparatorDescendingOrderViaNegate() {
      DefaultComparer delegate = new DefaultComparer();
      delegate.setNegate(true); // negate means descending
      FirstDayComparator comp = new FirstDayComparator((Comparator<?>) delegate);

      // Thursday(5) > Monday(2) → in negate-desc, compare(Thu, Mon) < 0
      assertTrue(cmp(comp, 5, 2) < 0, "Thu 'before' Mon in desc");
      assertTrue(cmp(comp, 2, 5) > 0, "Mon 'after' Thu in desc");
   }

   // ---- Non-numeric / null values return 0 ----

   @Test
   void nonNumericValuesReturnZero() {
      SortOrder sortOrder = new SortOrder(SortOrder.SORT_ASC);
      FirstDayComparator comp = new FirstDayComparator(sortOrder);

      // String values: neither is a Number, so body short-circuits to return 0
      assertEquals(0, cmp(comp, "Monday", "Tuesday"));
   }

   @Test
   void nullValuesReturnZero() {
      SortOrder sortOrder = new SortOrder(SortOrder.SORT_ASC);
      FirstDayComparator comp = new FirstDayComparator(sortOrder);

      assertEquals(0, cmp(comp, null, null));
      assertEquals(0, cmp(comp, null, Integer.valueOf(3)));
      assertEquals(0, cmp(comp, Integer.valueOf(3), null));
   }

   @Test
   void mixedNullAndNonNullReturnZero() {
      SortOrder sortOrder = new SortOrder(SortOrder.SORT_DESC);
      FirstDayComparator comp = new FirstDayComparator(sortOrder);

      assertEquals(0, cmp(comp, null, "anything"));
   }

   @Test
   void equalDayValuesReturnZeroRegardlessOfSortDirection() {
      SortOrder sortAsc  = new SortOrder(SortOrder.SORT_ASC);
      SortOrder sortDesc = new SortOrder(SortOrder.SORT_DESC);
      FirstDayComparator compAsc  = new FirstDayComparator(sortAsc);
      FirstDayComparator compDesc = new FirstDayComparator(sortDesc);

      assertEquals(0, cmp(compAsc, 5, 5));
      assertEquals(0, cmp(compDesc, 5, 5));
   }

   @Test
   void chartComparatorWithNullValuesReturnZero() {
      DefaultComparer delegate = new DefaultComparer();
      FirstDayComparator comp = new FirstDayComparator((Comparator<?>) delegate);

      assertEquals(0, cmp(comp, null, null));
      assertEquals(0, cmp(comp, null, Integer.valueOf(1)));
   }

   // ---- Long/Double Number subtypes work the same ----

   @Test
   void numberSubclassesAreAcceptedAscending() {
      SortOrder sortAsc = new SortOrder(SortOrder.SORT_ASC);
      FirstDayComparator comp = new FirstDayComparator(sortAsc);

      assertTrue(cmp(comp, Long.valueOf(2L), Long.valueOf(5L)) < 0);
      assertTrue(cmp(comp, Double.valueOf(6.0), Double.valueOf(3.0)) > 0);
   }

   @Test
   void numberSubclassesAreAcceptedDescending() {
      SortOrder sortDesc = new SortOrder(SortOrder.SORT_DESC);
      FirstDayComparator comp = new FirstDayComparator(sortDesc);

      // Thursday(5) > Wednesday(4) → in desc, compare(5,4) < 0
      assertTrue(cmp(comp, Double.valueOf(5.0), Double.valueOf(4.0)) < 0);
      assertTrue(cmp(comp, Long.valueOf(3L), Long.valueOf(6L)) > 0);
   }
}
