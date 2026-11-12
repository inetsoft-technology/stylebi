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
package inetsoft.uql.asset;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.WeekStartUtil;
import inetsoft.uql.Condition;
import inetsoft.uql.XCondition;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The relative date conditions of a year, a quarter, a month, a week, a number of weeks and a day
 * ({@code YearCondition}, {@code QuarterCondition}, {@code MonthCondition}, {@code WeekCondition},
 * {@code WeeksCondition}, {@code DayCondition} of {@link DateCondition}): which dates the
 * condition keeps in memory ({@code evaluate}) and the range of the SQL it is pushed down as
 * ({@code toSqlCondition}). The code reads the clock itself, so every expected range is worked out
 * from the clock with {@code java.time}, and a test is skipped when the day changes while it runs.
 * A week starts on the day of the {@code week.start} property (Sunday when there is none).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateConditionRelativeRangesTest {
   private static final ZoneId ZONE = ZoneId.systemDefault();

   private static void sameDay(LocalDate start) {
      Assumptions.assumeTrue(start.equals(LocalDate.now()), "the day changed during the test");
   }

   private static Date at(LocalDate day, int hour) {
      return Date.from(day.atTime(hour, 0).atZone(ZONE).toInstant());
   }

   private static long millis(Object value) {
      return ((Date) value).getTime();
   }

   private static Timestamp ts(LocalDateTime t) {
      return Timestamp.from(t.atZone(ZONE).toInstant());
   }

   /** The range of the SQL condition is from the start of the first day to 23:59:59 of the last. */
   private static void assertRange(LocalDate first, LocalDate last, DateCondition cond) {
      Condition sql = cond.toSqlCondition(true);

      assertEquals(XCondition.BETWEEN, sql.getOperation());
      assertEquals(millis(ts(first.atStartOfDay())), millis(sql.getValue(0)), "start");
      assertEquals(millis(ts(last.atTime(23, 59, 59))), millis(sql.getValue(1)), "end");
   }

   /** The days of the range are in, the days next to it are not. */
   private static void assertKeeps(LocalDate first, LocalDate last, DateCondition cond) {
      assertTrue(cond.evaluate(at(first, 0)), "first day, midnight");
      assertTrue(cond.evaluate(at(first, 12)), "first day");
      assertTrue(cond.evaluate(at(last, 12)), "last day");
      assertTrue(cond.evaluate(at(last, 23)), "last day, evening");
      assertFalse(cond.evaluate(at(first.minusDays(1), 23)), "day before");
      assertFalse(cond.evaluate(at(last.plusDays(1), 1)), "day after");
   }

   // ---- a year ----

   @ParameterizedTest(name = "{0} years ago")
   @ValueSource(ints = { 0, 1, 3 })
   void yearConditionIsTheCalendarYear(int yearsAgo) {
      LocalDate today = LocalDate.now();
      int year = today.getYear() - yearsAgo;
      DateCondition cond = new DateCondition.YearCondition(yearsAgo);
      LocalDate first = LocalDate.of(year, 1, 1);
      LocalDate last = LocalDate.of(year, 12, 31);

      assertKeeps(first, last, cond);
      assertRange(first, last, cond);
      sameDay(today);
   }

   // ---- a quarter ----

   /** The first day of the quarter that is the given quarters and years before the current one. */
   private static LocalDate quarterStart(LocalDate today, int quartersAgo, int yearsAgo) {
      LocalDate current = LocalDate.of(today.getYear(), (today.getMonthValue() - 1) / 3 * 3 + 1, 1);
      return current.minusMonths((yearsAgo * 4L + quartersAgo) * 3);
   }

   @ParameterizedTest(name = "{0} quarters ago")
   @ValueSource(ints = { 0, 1, 2, 3, 4, 5, 9 })
   void quarterConditionIsAThreeMonthRange(int quartersAgo) {
      LocalDate today = LocalDate.now();
      DateCondition cond = new DateCondition.QuarterCondition(quartersAgo, 0);
      LocalDate first = quarterStart(today, quartersAgo, 0);
      LocalDate last = first.plusMonths(3).minusDays(1);

      assertKeeps(first, last, cond);
      assertRange(first, last, cond);
      sameDay(today);
   }

   // the years add four quarters each
   @Test
   void quarterConditionWithYearsBack() {
      LocalDate today = LocalDate.now();
      DateCondition cond = new DateCondition.QuarterCondition(1, 2);
      LocalDate first = quarterStart(today, 1, 2);
      LocalDate last = first.plusMonths(3).minusDays(1);

      assertKeeps(first, last, cond);
      assertRange(first, last, cond);
      sameDay(today);
   }

   // ---- a month ----

   @ParameterizedTest(name = "{0} months ago")
   @ValueSource(ints = { 0, 1, 2, 11, 12, 13, 25 })
   void monthConditionIsTheCalendarMonth(int monthsAgo) {
      LocalDate today = LocalDate.now();
      DateCondition cond = new DateCondition.MonthCondition(monthsAgo, 0);
      YearMonth month = YearMonth.from(today).minusMonths(monthsAgo);

      assertKeeps(month.atDay(1), month.atEndOfMonth(), cond);
      assertRange(month.atDay(1), month.atEndOfMonth(), cond);
      sameDay(today);
   }

   // the years add twelve months each, and the last day of February is the 28th or the 29th
   @Test
   void monthConditionWithYearsBack() {
      LocalDate today = LocalDate.now();
      DateCondition cond = new DateCondition.MonthCondition(2, 3);
      YearMonth month = YearMonth.from(today).minusMonths(2).minusYears(3);

      assertKeeps(month.atDay(1), month.atEndOfMonth(), cond);
      assertRange(month.atDay(1), month.atEndOfMonth(), cond);
      sameDay(today);
   }

   @Test
   void monthConditionOfFebruaryEndsOnTheLeapDay() {
      // the 29th of February of a leap year is in the month, the 1st of March is not
      LocalDate today = LocalDate.now();
      YearMonth february = YearMonth.of(2024, 2);
      int monthsAgo = (int) java.time.temporal.ChronoUnit.MONTHS.between(february,
                                                                         YearMonth.from(today));
      DateCondition cond = new DateCondition.MonthCondition(monthsAgo, 0);

      assertTrue(cond.evaluate(at(LocalDate.of(2024, 2, 29), 12)));
      assertFalse(cond.evaluate(at(LocalDate.of(2024, 3, 1), 0)));
      assertRange(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29), cond);
      sameDay(today);
   }

   // ---- a week ----

   /** The first day of the week of the day, for a week that starts on the day of the week. */
   private static LocalDate weekStart(LocalDate day, DayOfWeek first) {
      return day.minusDays((day.getDayOfWeek().getValue() - first.getValue() + 7) % 7);
   }

   @ParameterizedTest(name = "{0} weeks ago, week starts {1}")
   @org.junit.jupiter.params.provider.CsvSource({
      "0,", "1,", "3,", "0,monday", "1,monday", "4,monday", "2,saturday"
   })
   void weekConditionIsSevenDaysFromTheFirstDayOfTheWeek(int weeksAgo, String start) {
      DayOfWeek first = start == null ? DayOfWeek.SUNDAY : DayOfWeek.valueOf(start.toUpperCase());
      LocalDate today = LocalDate.now();

      WeekStartUtil.withWeekStart(start, () -> {
         DateCondition cond = new DateCondition.WeekCondition(weeksAgo);
         LocalDate firstDay = weekStart(today.minusWeeks(weeksAgo), first);

         assertKeeps(firstDay, firstDay.plusDays(6), cond);
         assertRange(firstDay, firstDay.plusDays(6), cond);
      });
      sameDay(today);
   }

   // ---- a number of weeks ----

   @ParameterizedTest(name = "weeks {0} to {1}, week starts {2}")
   @org.junit.jupiter.params.provider.CsvSource({
      "0,1,", "0,4,", "1,3,", "2,5,", "0,4,monday", "1,2,monday"
   })
   void weeksConditionIsTheWeeksFromTheOldestToTheNewest(int from, int to, String start) {
      DayOfWeek first = start == null ? DayOfWeek.SUNDAY : DayOfWeek.valueOf(start.toUpperCase());
      LocalDate today = LocalDate.now();

      WeekStartUtil.withWeekStart(start, () -> {
         DateCondition cond = new DateCondition.WeeksCondition(from, to);
         // the weeks from "from" weeks ago to "to - 1" weeks ago
         LocalDate firstDay = weekStart(today.minusWeeks(to - 1L), first);
         LocalDate lastDay = weekStart(today.minusWeeks(from), first).plusDays(6);

         assertKeeps(firstDay, lastDay, cond);
         assertRange(firstDay, lastDay, cond);
      });
      sameDay(today);
   }

   @Test
   void weeksConditionIsValidWhenFromIsBeforeToAndIgnoresNegativeValues() {
      assertTrue(new DateCondition.WeeksCondition(0, 1).isValid());
      assertFalse(new DateCondition.WeeksCondition(2, 2).isValid());
      assertFalse(new DateCondition.WeeksCondition(3, 1).isValid());

      DateCondition.WeeksCondition cond = new DateCondition.WeeksCondition(1, 4);
      cond.setFrom(-1);
      cond.setTo(-1);

      assertEquals(1, cond.getFrom());
      assertEquals(4, cond.getTo());
   }

   // ---- a day ----

   @ParameterizedTest(name = "{0} days ago")
   @ValueSource(ints = { 0, 1, 2, 7, 30 })
   void dayConditionIsTheCalendarDay(int daysAgo) {
      LocalDate today = LocalDate.now();
      // a daylight saving change moves the start of the day of the SQL range by an hour
      Assumptions.assumeTrue(ZONE.getRules().getOffset(today.atStartOfDay(ZONE).toInstant())
         .equals(ZONE.getRules().getOffset(today.minusDays(daysAgo + 1L).atStartOfDay(ZONE)
                                              .toInstant())),
         "daylight saving changes in the window");
      DateCondition cond = new DateCondition.DayCondition(daysAgo);
      LocalDate day = today.minusDays(daysAgo);

      assertKeeps(day, day, cond);
      assertRange(day, day, cond);
      sameDay(today);
   }

   // a negative number is a day to come
   @Test
   void dayConditionWithANegativeNumberIsTomorrow() {
      LocalDate today = LocalDate.now();
      DateCondition cond = new DateCondition.DayCondition(-1);

      assertTrue(cond.evaluate(at(today.plusDays(1), 12)));
      assertFalse(cond.evaluate(at(today, 12)));
      sameDay(today);
   }

   // ---- what every one of them does ----

   @Test
   void negatedConditionKeepsTheOtherDates() {
      LocalDate today = LocalDate.now();
      DateCondition year = new DateCondition.YearCondition(0);
      DateCondition quarter = new DateCondition.QuarterCondition(0, 0);
      DateCondition month = new DateCondition.MonthCondition(0, 0);
      DateCondition week = new DateCondition.WeekCondition(0);
      DateCondition weeks = new DateCondition.WeeksCondition(0, 2);
      DateCondition day = new DateCondition.DayCondition(0);

      for(DateCondition cond : new DateCondition[] { year, quarter, month, week, weeks, day }) {
         assertTrue(cond.evaluate(at(today, 12)), cond.getClass().getSimpleName());
         cond.setNegated(true);
         assertFalse(cond.evaluate(at(today, 12)), cond.getClass().getSimpleName());
         assertTrue(cond.evaluate(at(today.minusYears(5), 12)), cond.getClass().getSimpleName());
         assertTrue(cond.toSqlCondition(true).isNegated(), cond.getClass().getSimpleName());
      }

      sameDay(today);
   }

   @Test
   void valueThatIsNotADateIsNeverKept() {
      for(DateCondition cond : new DateCondition[] {
         new DateCondition.YearCondition(0), new DateCondition.QuarterCondition(0, 0),
         new DateCondition.MonthCondition(0, 0), new DateCondition.WeekCondition(0),
         new DateCondition.WeeksCondition(0, 2), new DateCondition.DayCondition(0) })
      {
         assertFalse(cond.evaluate("2024-02-29"), cond.getClass().getSimpleName());
         assertFalse(cond.evaluate(20240229), cond.getClass().getSimpleName());
         assertFalse(cond.evaluate(null), cond.getClass().getSimpleName());
      }
   }
}
