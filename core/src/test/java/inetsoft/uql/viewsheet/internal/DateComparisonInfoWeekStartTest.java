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
package inetsoft.uql.viewsheet.internal;

import inetsoft.test.*;
import inetsoft.util.script.JavaScriptEngine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code getToDateWeekOfMonth()} produces the {@code forceDcToDateWeekOfMonth} argument of
 * the {@code datePartForceWeekOfMonth('wy'|'wm'|'wmq', ...)} expressions this class emits.
 * Producer and consumer must compute the week the same way, or the month-boundary folding
 * they implement together silently stops matching.
 *
 * <p>The producer rewound to the preceding Sunday regardless of the configured week start,
 * so under a Monday start it could return week-of-month 0 -- and every consumer guards on
 * {@code forceWM > 0}, disabling the folding outright.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateComparisonInfoWeekStartTest {
   @ParameterizedTest
   @ValueSource(strings = { "sunday", "monday", "saturday" })
   void toDateWeekOfMonthMatchesTheWeekOfYearDatePart(String weekStart) {
      WeekStartUtil.withWeekStart(weekStart, () -> {
         for(Date day : everyDayOf(2021)) {
            int actual = weekToDateInfo(day).getToDateWeekOfMonth();
            int expected = (int) JavaScriptEngine.datePart("wy", day, true) % 10;

            assertEquals(expected, actual,
                         "getToDateWeekOfMonth disagrees with the datePart('wy') week it " +
                            "is compared against, for " + day + " (" + weekStart + ")");
         }
      });
   }

   /**
    * A forced week-of-month of 0 reads as "not forced" to every consumer, so the value must
    * always be a real week number.
    */
   @ParameterizedTest
   @ValueSource(strings = { "sunday", "monday", "saturday" })
   void toDateWeekOfMonthIsAlwaysARealWeek(String weekStart) {
      WeekStartUtil.withWeekStart(weekStart, () -> {
         for(Date day : everyDayOf(2021)) {
            int weekOfMonth = weekToDateInfo(day).getToDateWeekOfMonth();

            assertTrue(weekOfMonth >= 1 && weekOfMonth <= 5,
                       "forced week-of-month out of range for " + day + " (" + weekStart +
                          "): " + weekOfMonth);
         }
      });
   }

   /** Week-to-date over a year context, ending on the given day. */
   private static DateComparisonInfo weekToDateInfo(Date toDate) {
      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setLevelValue("week to date");
      interval.setContextLevelValue("year");
      interval.setEndDayAsToDate(false);
      interval.setIntervalEndDate(toDate);
      interval.setInclusive(true);

      DateComparisonInfo info = new DateComparisonInfo();
      info.setDateComparisonInterval(interval);

      return info;
   }

   private static List<Date> everyDayOf(int year) {
      List<Date> days = new ArrayList<>();
      Calendar cal = new GregorianCalendar();
      cal.clear();
      cal.set(year, Calendar.JANUARY, 1, 12, 0, 0);

      while(cal.get(Calendar.YEAR) == year) {
         days.add(cal.getTime());
         cal.add(Calendar.DATE, 1);
      }

      return days;
   }
}
