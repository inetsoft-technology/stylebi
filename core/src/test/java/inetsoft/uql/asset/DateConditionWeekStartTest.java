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
package inetsoft.uql.asset;

import inetsoft.test.*;
import inetsoft.uql.Condition;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for Bug #76876: {@link DateCondition.WeekCondition#toSqlCondition} and
 * {@link DateCondition.WeeksCondition#toSqlCondition} (SQL-generation path, used when the
 * condition is pushed down to a JDBC query), and {@link DateCondition#getWeeks} (used by
 * {@code evaluate()}, the in-memory row-filtering path used for embedded/non-SQL tables), all
 * rewound/bucketed weeks using Sunday-anchored logic (respectively {@code Calendar.DAY_OF_WEEK}
 * and raw epoch-day arithmetic), ignoring the configured {@code week.start} (via
 * {@link inetsoft.util.Tool#getFirstDayOfWeek()}) -- same class of bug as #76529.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class DateConditionWeekStartTest {
   @Test
   void weekCondition_sundayWeekStart_windowStartsOnSunday() {
      WeekStartUtil.withWeekStart(null, () -> {
         Condition cond = new DateCondition.WeekCondition(0).toSqlCondition(false);
         assertEquals(Calendar.SUNDAY, dayOfWeek((Date) cond.getValue(0)));
      });
   }

   @Test
   void weekCondition_mondayWeekStart_windowStartsOnMonday() {
      WeekStartUtil.withWeekStart("monday", () -> {
         Condition cond = new DateCondition.WeekCondition(0).toSqlCondition(false);
         assertEquals(Calendar.MONDAY, dayOfWeek((Date) cond.getValue(0)));
      });
   }

   @Test
   void weekCondition_mondayWeekStart_windowSpansSevenDays() {
      WeekStartUtil.withWeekStart("monday", () -> {
         Condition cond = new DateCondition.WeekCondition(0).toSqlCondition(false);
         Date start = (Date) cond.getValue(0);
         Date end = (Date) cond.getValue(1);
         assertEquals(6, daysBetween(start, end));
      });
   }

   @Test
   void weeksCondition_sundayWeekStart_windowStartsOnSunday() {
      WeekStartUtil.withWeekStart(null, () -> {
         Condition cond = new DateCondition.WeeksCondition(0, 4).toSqlCondition(false);
         assertEquals(Calendar.SUNDAY, dayOfWeek((Date) cond.getValue(0)));
      });
   }

   @Test
   void weeksCondition_mondayWeekStart_windowStartsOnMonday() {
      WeekStartUtil.withWeekStart("monday", () -> {
         Condition cond = new DateCondition.WeeksCondition(0, 4).toSqlCondition(false);
         assertEquals(Calendar.MONDAY, dayOfWeek((Date) cond.getValue(0)));
      });
   }

   @Test
   void weeksCondition_mondayWeekStart_windowSpansConfiguredWeeks() {
      WeekStartUtil.withWeekStart("monday", () -> {
         Condition cond = new DateCondition.WeeksCondition(0, 4).toSqlCondition(false);
         Date start = (Date) cond.getValue(0);
         Date end = (Date) cond.getValue(1);
         assertEquals(4 * 7 - 1, daysBetween(start, end));
      });
   }

   // ---- evaluate() / getWeeks() -- the in-memory path used for embedded/non-SQL tables ----

   @Test
   void weekCondition_evaluate_mondayWeekStart_mondayIsThisWeekSundayIsLastWeek() {
      WeekStartUtil.withWeekStart("monday", () -> {
         Date mondayOfThisWeek = mostRecentDayOfWeek(Calendar.MONDAY);
         Date sundayBeforeThat = addDays(mondayOfThisWeek, -1);

         assertEquals(true, new DateCondition.WeekCondition(0).evaluate(mondayOfThisWeek),
            "the current Monday-anchored week's first day should be \"this week\"");
         assertEquals(false, new DateCondition.WeekCondition(0).evaluate(sundayBeforeThat),
            "the day before a Monday week start belongs to last week, not this week");
         assertEquals(true, new DateCondition.WeekCondition(1).evaluate(sundayBeforeThat),
            "the day before a Monday week start should be \"last week\"");
      });
   }

   @Test
   void weekCondition_evaluate_sundayWeekStart_sundayIsThisWeek() {
      WeekStartUtil.withWeekStart(null, () -> {
         Date sundayOfThisWeek = mostRecentDayOfWeek(Calendar.SUNDAY);

         assertEquals(true, new DateCondition.WeekCondition(0).evaluate(sundayOfThisWeek),
            "the current Sunday-anchored week's first day should be \"this week\"");
      });
   }

   private static int dayOfWeek(Date date) {
      Calendar cal = Calendar.getInstance();
      cal.setTime(date);
      return cal.get(Calendar.DAY_OF_WEEK);
   }

   private static long daysBetween(Date start, Date end) {
      return TimeUnit.MILLISECONDS.toDays(end.getTime() - start.getTime());
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
