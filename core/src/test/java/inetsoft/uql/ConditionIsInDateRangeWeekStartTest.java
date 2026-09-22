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
package inetsoft.uql;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for Bug #76876: {@code Condition}'s private {@code getWeeks()} (used by
 * {@link Condition#isInDateRange} for the "this week"/"last week"/etc. relative-range strings)
 * is a duplicate of the same Sunday-hardcoded week-bucketing logic fixed in
 * {@link inetsoft.uql.asset.DateCondition#getWeeks}, and had the same defect: it ignored the
 * configured week start ({@link inetsoft.util.Tool#getFirstDayOfWeek()}).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ConditionIsInDateRangeWeekStartTest {
   @Test
   void mondayWeekStart_mondayIsThisWeekSundayIsLastWeek() {
      WeekStartUtil.withWeekStart("monday", () -> {
         Condition cond = new Condition();
         Date mondayOfThisWeek = mostRecentDayOfWeek(Calendar.MONDAY);
         Date sundayBeforeThat = addDays(mondayOfThisWeek, -1);

         assertTrue(cond.isInDateRange("this week", mondayOfThisWeek),
            "the current Monday-anchored week's first day should be \"this week\"");
         assertFalse(cond.isInDateRange("this week", sundayBeforeThat),
            "the day before a Monday week start belongs to last week, not this week");
         assertTrue(cond.isInDateRange("last week", sundayBeforeThat),
            "the day before a Monday week start should be \"last week\"");
      });
   }

   @Test
   void sundayWeekStart_sundayIsThisWeek() {
      WeekStartUtil.withWeekStart(null, () -> {
         Condition cond = new Condition();
         Date sundayOfThisWeek = mostRecentDayOfWeek(Calendar.SUNDAY);

         assertTrue(cond.isInDateRange("this week", sundayOfThisWeek),
            "the current Sunday-anchored week's first day should be \"this week\"");
      });
   }

   /**
    * The most recent occurrence of the given {@code Calendar} day-of-week constant, on or
    * before today (today itself if today already is that day).
    */
   private static Date mostRecentDayOfWeek(int targetDayOfWeek) {
      Calendar cal = Calendar.getInstance();
      int daysSince = (cal.get(Calendar.DAY_OF_WEEK) - targetDayOfWeek + 7) % 7;
      cal.add(Calendar.DATE, -daysSince);
      return cal.getTime();
   }

   private static Date addDays(Date date, int days) {
      Calendar cal = Calendar.getInstance();
      cal.setTime(date);
      cal.add(Calendar.DATE, days);
      return cal.getTime();
   }
}
