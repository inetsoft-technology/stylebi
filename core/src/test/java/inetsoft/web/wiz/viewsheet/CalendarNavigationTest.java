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
package inetsoft.web.wiz.viewsheet;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80449 part 1: set_calendar_display's month/secondMonth. The painter and the browser read
 * currdate1/currdate2 as "year-month" with a 0-based month ("year" in year view), the tool takes
 * ISO "YYYY-MM" (1-based), and the painter ignores the stored value whenever dates are selected.
 */
@Tag("core")
class CalendarNavigationTest {
   private static final String[] OPEN = { null, null };

   private static CalendarDisplayService.Navigation resolve(String month, String second,
                                                            boolean dual, boolean year,
                                                            boolean hasDates, String[] range,
                                                            String cur1, String cur2)
   {
      return CalendarDisplayService.resolveNavigation(
         month, second, dual, year, hasDates, range, cur1, cur2, "Cal1");
   }

   @Test
   void storesTheMonthInThePainterFormatWithAZeroBasedMonth() {
      CalendarDisplayService.Navigation nav =
         resolve("2024-03", null, false, false, false, OPEN, "", null);
      assertEquals("2024-2", nav.date1());
      assertNull(nav.date2());
   }

   @Test
   void storesJanuaryAndDecemberAtTheEdgesOfTheZeroBasedRange() {
      assertEquals("2024-0", resolve("2024-01", null, false, false, false, OPEN, "", null).date1());
      assertEquals("2024-11", resolve("2024-12", null, false, false, false, OPEN, "", null).date1());
   }

   @Test
   void storesAYearOnlyInYearView() {
      CalendarDisplayService.Navigation nav =
         resolve("2023", null, false, true, false, OPEN, "2024-3", null);
      assertEquals("2023", nav.date1());
   }

   @Test
   void refusesAMonthShapeInYearViewAndAYearShapeInMonthView() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> resolve("2024-03", null, false, true, false, OPEN, "", null));
      assertTrue(e.getMessage().contains("'month'"), e.getMessage());
      assertTrue(e.getMessage().contains("YYYY"), e.getMessage());

      e = assertThrows(IllegalArgumentException.class,
         () -> resolve("2024", null, false, false, false, OPEN, "", null));
      assertTrue(e.getMessage().contains("YYYY-MM"), e.getMessage());
   }

   @Test
   void refusesMalformedMonths() {
      for(String bad : new String[] { "2024-13", "2024-00", "abc", "2024-3", "24-03", "2024/03" }) {
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> resolve(bad, null, false, false, false, OPEN, "", null), bad);
         assertTrue(e.getMessage().contains("'month'"), e.getMessage());
      }
   }

   @Test
   void refusesAMonthWhileDatesAreSelected() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> resolve("2024-03", null, false, false, true, OPEN, "", null));
      assertTrue(e.getMessage().contains("'month'"), e.getMessage());
      assertTrue(e.getMessage().contains("has dates selected"), e.getMessage());
      assertTrue(e.getMessage().contains("clear_calendar"), e.getMessage());
      assertTrue(e.getMessage().contains("set_calendar_dates"), e.getMessage());
      assertTrue(e.getMessage().contains("Nothing was changed"), e.getMessage());
   }

   @Test
   void refusesASecondMonthOnASingleCalendar() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> resolve("2024-03", "2024-04", false, false, false, OPEN, "", null));
      assertTrue(e.getMessage().contains("'secondMonth'"), e.getMessage());
      assertTrue(e.getMessage().contains("double"), e.getMessage());
   }

   @Test
   void doubleCalendarStoresBothAndAllowsEqualMonths() {
      CalendarDisplayService.Navigation nav =
         resolve("2024-03", "2024-04", true, false, false, OPEN, "", null);
      assertEquals("2024-2", nav.date1());
      assertEquals("2024-3", nav.date2());

      nav = resolve("2024-03", "2024-03", true, false, false, OPEN, "", null);
      assertEquals("2024-2", nav.date1());
      assertEquals("2024-2", nav.date2());
   }

   @Test
   void refusesASecondMonthBeforeTheFirst() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> resolve("2024-05", "2024-04", true, false, false, OPEN, "", null));
      assertTrue(e.getMessage().contains("'secondMonth'"), e.getMessage());
      assertTrue(e.getMessage().contains("before"), e.getMessage());

      // only secondMonth given: compared with the first calendar as it is now
      assertThrows(IllegalArgumentException.class,
         () -> resolve(null, "2023-12", true, false, false, OPEN, "2024-2", "2024-3"));
      // only month given, later than the second calendar as it is now
      assertThrows(IllegalArgumentException.class,
         () -> resolve("2025-01", null, true, false, false, OPEN, "2024-2", "2024-3"));
   }

   @Test
   void yearViewDoubleCalendarComparesYears() {
      assertThrows(IllegalArgumentException.class,
         () -> resolve("2025", "2024", true, true, false, OPEN, "", null));
      assertEquals("2024", resolve("2024", "2024", true, true, false, OPEN, "", null).date2());
   }

   @Test
   void refusesAMonthOutsideTheSelectableRangeWithAZeroBasedComparison() {
      String[] range = { "2024-0-1", "2024-11-31" };

      assertEquals("2024-0", resolve("2024-01", null, false, false, false, range, "", null).date1());
      assertEquals("2024-11", resolve("2024-12", null, false, false, false, range, "", null).date1());

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> resolve("2025-01", null, false, false, false, range, "", null));
      assertTrue(e.getMessage().contains("'month'"), e.getMessage());
      assertTrue(e.getMessage().contains("2024-01"), e.getMessage());
      assertTrue(e.getMessage().contains("2024-12"), e.getMessage());

      assertThrows(IllegalArgumentException.class,
         () -> resolve("2023-12", null, false, false, false, range, "", null));
   }

   @Test
   void yearViewRangeComparesYearsOnly() {
      String[] range = { "2022-5-1", "2024-2-1" };
      assertEquals("2022", resolve("2022", null, false, true, false, range, "", null).date1());
      assertEquals("2024", resolve("2024", null, false, true, false, range, "", null).date1());
      assertThrows(IllegalArgumentException.class,
         () -> resolve("2025", null, false, true, false, range, "", null));
   }

   @Test
   void anUnboundCalendarHasNoRangeCheck() {
      assertEquals("2099-0", resolve("2099-01", null, false, false, false, null, "", null).date1());
   }

   @Test
   void returnsNullWhenNeitherMonthIsGiven() {
      assertNull(resolve(null, null, false, false, false, OPEN, "", null));
      assertNull(resolve("", " ", false, false, true, OPEN, "", null));
   }

   @Test
   void displayMonthReadsTheStoredShapeAsIso() {
      assertEquals("2024-03", CalendarDisplayService.displayMonth("2024-2", false));
      assertEquals("2024-12", CalendarDisplayService.displayMonth("2024-11", false));
      assertEquals("2024", CalendarDisplayService.displayMonth("2024", true));
   }

   @Test
   void displayMonthInYearViewTakesTheLeadingYearOfEitherShape() {
      assertEquals("2024", CalendarDisplayService.displayMonth("2024-3", true));
   }

   @Test
   void displayMonthIsNullWhenNothingIsStored() {
      assertNull(CalendarDisplayService.displayMonth(null, false));
      assertNull(CalendarDisplayService.displayMonth("", false));
      assertNull(CalendarDisplayService.displayMonth("garbage", false));
   }

   @Test
   void monthViewDisplayMonthWithAYearOnlyValueIsNull() {
      assertNull(CalendarDisplayService.displayMonth("2024", false));
   }
}
