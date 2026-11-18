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

import inetsoft.graph.data.CombinedDataSetComparator;
import inetsoft.report.filter.DefaultComparer;
import inetsoft.report.filter.OthersComparator;
import inetsoft.report.filter.SortOrder;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

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
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@SuppressWarnings("unchecked")
@Tag("core")
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

   // ---- Non-DefaultComparer delegates (Bug #79739) ----

   @Test
   void manualDelegateHonoursManualOrderWithoutException() {
      Comparator<Object> comp = (Comparator<Object>) new FirstDayComparator(
         (Comparator) new ManualOrderComparer("integer", java.util.List.of(3, 2, 1)));

      assertTrue(comp.compare(3, 2) < 0, "manual order puts 3 before 2");
      assertTrue(comp.compare(2, 1) < 0, "manual order puts 2 before 1");
      assertTrue(comp.compare(1, 3) > 0);
   }

   @Test
   void othersDelegateDoesNotThrow() {
      Comparator<Object> comp = (Comparator<Object>) new FirstDayComparator(
         (Comparator) new OthersComparator());

      assertDoesNotThrow(() -> comp.compare(2, 3));
   }

   @Test
   void wrapLeavesManualOrderComparerUntouched() {
      Comparator manual = new ManualOrderComparer("integer", java.util.List.of(3, 2, 1));
      assertSame(manual, FirstDayComparator.wrap("day", manual));
   }

   @Test
   void wrapLeavesOthersAndValueOrderStyleComparersUntouched() {
      Comparator others = new OthersComparator();
      assertSame(others, FirstDayComparator.wrap("day", others));
   }

   @Test
   void wrapKeepsNullDelegateBehaviour() {
      Comparator wrapped = FirstDayComparator.wrap("day", null);
      assertInstanceOf(FirstDayComparator.class, wrapped);
   }

   @Test
   void wrapWrapsDefaultComparer() {
      Comparator wrapped = FirstDayComparator.wrap("day", new DefaultComparer());
      assertInstanceOf(FirstDayComparator.class, wrapped);
   }

   @Test
   void wrapDoesNotDoubleWrap() {
      Comparator once = FirstDayComparator.wrap("day", new DefaultComparer());
      assertSame(once, FirstDayComparator.wrap("day", once));
   }

   @Test
   void wrapCombinedAscAndOthersRotatesAndKeepsOthersLast() {
      withWeekStart("Monday", () -> {
         Comparator combined = new CombinedDataSetComparator(
            "day", new DefaultComparer(), new OthersComparator());
         Comparator wrapped = FirstDayComparator.wrap("day", combined);

         assertInstanceOf(CombinedDataSetComparator.class, wrapped);
         assertNotSame(combined, wrapped);
         Comparator<Object> c = (Comparator<Object>) wrapped;
         // Monday-first: Mon(2) < Sat(7) < Sun(1)
         assertTrue(c.compare(2, 1) < 0, "Mon before Sun");
         assertTrue(c.compare(1, 7) > 0, "Sun after Sat");
         assertTrue(c.compare(7, 2) > 0);
      });
   }

   @Test
   void wrapCombinedDescAndOthersRotatesDescending() {
      withWeekStart("Monday", () -> {
         DefaultComparer desc = new DefaultComparer();
         desc.setNegate(true);
         Comparator wrapped = FirstDayComparator.wrap(
            "day", new CombinedDataSetComparator("day", desc, new OthersComparator()));
         Comparator<Object> c = (Comparator<Object>) wrapped;

         assertTrue(c.compare(1, 2) < 0, "Sun first when descending Monday-first");
         assertTrue(c.compare(2, 7) > 0);
      });
   }

   @Test
   void wrapLeavesCombinedWithManualUntouched() {
      Comparator combined = new CombinedDataSetComparator(
         "day", new ManualOrderComparer("integer", java.util.List.of(3, 2, 1)),
         new OthersComparator());
      assertSame(combined, FirstDayComparator.wrap("day", combined));
   }

   @Test
   void wrapTreatsRcalcStyleCombinedGenerically() {
      withWeekStart("Monday", () -> {
         Comparator rcalc = (a, b) -> 0;
         Comparator wrapped = FirstDayComparator.wrap(
            "day", new CombinedDataSetComparator("day", new DefaultComparer(), rcalc));

         assertInstanceOf(CombinedDataSetComparator.class, wrapped);
         assertTrue(((Comparator<Object>) wrapped).compare(1, 2) > 0);
      });
   }

   private static void withWeekStart(String day, Runnable r) {
      String old = inetsoft.sree.SreeEnv.getProperty("week.start");
      try {
         inetsoft.sree.SreeEnv.setProperty("week.start", day);
         inetsoft.util.Tool.clearWeekStartCache();
         r.run();
      }
      finally {
         if(old == null) {
            inetsoft.sree.SreeEnv.remove("week.start");
         }
         else {
            inetsoft.sree.SreeEnv.setProperty("week.start", old);
         }

         inetsoft.util.Tool.clearWeekStartCache();
      }
   }
}
