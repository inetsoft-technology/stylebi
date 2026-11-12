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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The built in date conditions that are relative to the current time (to date, last N months,
 * last N days, the Nth quarter, half year and month of a year, a part of a date). The code reads
 * the clock itself, so every expected value is worked out from the clock with {@code java.time}
 * and every test is skipped when the day changes while it runs.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateConditionBuiltinTest {
   private static final ZoneId ZONE = ZoneId.systemDefault();

   /** Stops the test when the day changed since the start (the clock is read by the code). */
   private static void sameDay(LocalDate start) {
      Assumptions.assumeTrue(start.equals(LocalDate.now()), "the day changed during the test");
   }

   private static Timestamp ts(LocalDateTime t) {
      return Timestamp.from(t.atZone(ZONE).toInstant());
   }

   private static Date date(LocalDateTime t) {
      return Date.from(t.atZone(ZONE).toInstant());
   }

   private static Date noon(LocalDate d) {
      return date(d.atTime(12, 0));
   }

   private static long millis(Object value) {
      return ((Date) value).getTime();
   }

   private static Condition between(DateCondition cond, boolean timestamp) {
      Condition sql = cond.toSqlCondition(timestamp);
      assertEquals(XCondition.BETWEEN, sql.getOperation());
      assertEquals(2, sql.getValueCount());
      return sql;
   }

   private static void assertRange(LocalDateTime from, LocalDateTime to, DateCondition cond) {
      Condition sql = between(cond, true);
      assertEquals(millis(ts(from)), millis(sql.getValue(0)), "start");
      assertEquals(millis(ts(to)), millis(sql.getValue(1)), "end");
   }

   // ---- to date: from the start of the year, quarter or month until now ----

   private static DateCondition.ToDateCondition toDate(DateCondition.REFERENCE ref, int year,
                                                       int quarter, int month)
   {
      DateCondition.ToDateCondition cond = new DateCondition.ToDateCondition();
      cond.setStartReference(ref);
      cond.setYearOffset(year);
      cond.setQuarterOffset(quarter);
      cond.setMonthOffset(month);
      return cond;
   }

   @Test
   void yearToDateStartsOnJanuaryFirstAndEndsNow() {
      LocalDate today = LocalDate.now();
      LocalDateTime start = LocalDate.of(today.getYear(), 1, 1).atStartOfDay();
      DateCondition cond = toDate(DateCondition.REFERENCE.YEAR, 0, 0, 0);

      assertTrue(cond.evaluate(ts(start)));
      assertFalse(cond.evaluate(ts(start.minusSeconds(1))));
      assertTrue(cond.evaluate(ts(start.plusSeconds(1))));
      // the end is now, and it is not included
      assertFalse(cond.evaluate(ts(LocalDateTime.now().plusMinutes(1))));
      sameDay(today);
   }

   @Test
   void quarterToDateStartsOnTheFirstDayOfTheQuarter() {
      LocalDate today = LocalDate.now();
      int month = (today.getMonthValue() - 1) / 3 * 3 + 1;
      LocalDateTime start = LocalDate.of(today.getYear(), month, 1).atStartOfDay();
      DateCondition cond = toDate(DateCondition.REFERENCE.QUARTER, 0, 0, 0);

      assertTrue(cond.evaluate(ts(start)));
      assertFalse(cond.evaluate(ts(start.minusSeconds(1))));
      sameDay(today);
   }

   @Test
   void monthToDateStartsOnTheFirstDayOfTheMonth() {
      LocalDate today = LocalDate.now();
      LocalDateTime start = today.withDayOfMonth(1).atStartOfDay();
      DateCondition cond = toDate(DateCondition.REFERENCE.MONTH, 0, 0, 0);

      assertTrue(cond.evaluate(ts(start)));
      assertFalse(cond.evaluate(ts(start.minusSeconds(1))));
      assertFalse(cond.evaluate(ts(LocalDateTime.now().plusMinutes(1))));
      sameDay(today);
   }

   // the offsets move the end back by (years * 4 + quarters) * 3 + months months, and the start
   // is the start of the month, quarter or year of that end
   @Test
   void offsetsMoveTheWholeRangeBack() {
      LocalDateTime before = LocalDateTime.now();
      // 1 year, 1 quarter and 1 month: 16 months
      LocalDateTime end = before.minusMonths(16);
      LocalDateTime start = end.toLocalDate().withDayOfMonth(1).atStartOfDay();
      DateCondition cond = toDate(DateCondition.REFERENCE.MONTH, 1, 1, 1);

      assertTrue(cond.evaluate(ts(start)));
      assertFalse(cond.evaluate(ts(start.minusSeconds(1))));
      assertTrue(cond.evaluate(ts(end.minusMinutes(1))));
      assertFalse(cond.evaluate(ts(end.plusMinutes(1))));
      // today is not in a range that ended 16 months ago
      assertFalse(cond.evaluate(ts(before)));
      sameDay(before.toLocalDate());
   }

   @Test
   void offsetYearToDateEndsOneYearAgo() {
      LocalDateTime before = LocalDateTime.now();
      LocalDateTime end = before.minusYears(1);
      LocalDateTime start = LocalDate.of(end.getYear(), 1, 1).atStartOfDay();
      DateCondition cond = toDate(DateCondition.REFERENCE.YEAR, 1, 0, 0);

      assertTrue(cond.evaluate(ts(start)));
      assertFalse(cond.evaluate(ts(start.minusSeconds(1))));
      assertTrue(cond.evaluate(ts(end.minusMinutes(1))));
      assertFalse(cond.evaluate(ts(end.plusMinutes(1))));
      sameDay(before.toLocalDate());
   }

   @Test
   void toDateSqlConditionIsBetweenStartAndNow() {
      LocalDateTime before = LocalDateTime.now();
      LocalDateTime start = before.toLocalDate().withDayOfMonth(1).atStartOfDay();
      DateCondition cond = toDate(DateCondition.REFERENCE.MONTH, 0, 0, 0);
      Condition sql = between(cond, true);

      assertEquals(millis(ts(start)), millis(sql.getValue(0)));
      // the end is the time of the call, with the milliseconds cut
      long end = millis(sql.getValue(1));
      assertTrue(end >= millis(ts(before)) / 1000 * 1000 - 1000
                 && end <= System.currentTimeMillis(), "end " + end);
      assertTrue(sql.getValue(0) instanceof Timestamp);
      assertFalse(between(cond, false).getValue(0) instanceof Timestamp);
      sameDay(before.toLocalDate());
   }

   @Test
   void toDateNegatedFlipsEvaluateAndSql() {
      LocalDate today = LocalDate.now();
      DateCondition.ToDateCondition cond = toDate(DateCondition.REFERENCE.YEAR, 0, 0, 0);
      cond.setNegated(true);
      LocalDateTime start = LocalDate.of(today.getYear(), 1, 1).atStartOfDay();

      assertFalse(cond.evaluate(ts(start)));
      assertTrue(cond.evaluate(ts(start.minusSeconds(1))));
      assertTrue(cond.toSqlCondition(true).isNegated());
      assertFalse(cond.evaluate("2024-01-01"));
      assertFalse(cond.evaluate(null));
      sameDay(today);
   }

   // ---- the last N months: a rolling window of whole months ending on today ----

   @Test
   void lastMonthsIsRollingFromTheSameDayOfTheMonthToToday() {
      LocalDate today = LocalDate.now();
      // from 0, to 3: today and the 2 months before it
      DateCondition cond = new DateCondition.MonthsCondition(0, 3);
      LocalDate first = today.minusMonths(2);

      assertRange(first.atStartOfDay(), today.atTime(23, 59, 59), cond);
      assertTrue(cond.evaluate(java.sql.Date.valueOf(first)));
      assertFalse(cond.evaluate(java.sql.Date.valueOf(first.minusDays(1))));
      assertTrue(cond.evaluate(java.sql.Date.valueOf(today)));
      assertFalse(cond.evaluate(java.sql.Date.valueOf(today.plusDays(1))));
      sameDay(today);
   }

   @Test
   void monthsFromTheFutureIncludeTheMonthsAhead() {
      LocalDate today = LocalDate.now();
      // from 2, to 3: 2 months back to 2 months ahead
      DateCondition cond = new DateCondition.MonthsCondition(2, 3);

      assertRange(today.minusMonths(2).atStartOfDay(), today.plusMonths(2).atTime(23, 59, 59), cond);
      sameDay(today);
   }

   @Test
   void monthsConditionIsValidOnlyWhenFromIsBeforeTo() {
      assertTrue(new DateCondition.MonthsCondition(0, 1).isValid());
      assertFalse(new DateCondition.MonthsCondition(1, 1).isValid());
      assertFalse(new DateCondition.MonthsCondition(3, 1).isValid());
   }

   // a timestamp is compared with the instant of the start and end, a date with the days. A
   // timestamp earlier in the day of the start is in the pushed down sql (the start is the start
   // of that day) but not in the in-memory evaluation (the start is this time of that day).
   // Pins the current behavior, reported as Bug #81178 (also later today, and a java.util.Date).
   @Test
   void timestampEarlierOnTheStartDayIsInSqlButNotInMemory() {
      LocalDateTime before = LocalDateTime.now();
      DateCondition cond = new DateCondition.MonthsCondition(0, 3);
      LocalDateTime startOfDay = before.minusMonths(2).toLocalDate().atStartOfDay();

      assertEquals(millis(ts(startOfDay)), millis(between(cond, true).getValue(0)));
      // the instant of the start, one minute later is in, one minute earlier is out
      assertTrue(cond.evaluate(ts(before.minusMonths(2).plusMinutes(1))));
      assertFalse(cond.evaluate(ts(before.minusMonths(2).minusMinutes(1))));
      sameDay(before.toLocalDate());
   }

   // ---- the last N days ----

   @Test
   void lastDaysIsTheDaysFromTodayBack() {
      LocalDate today = LocalDate.now();
      // from 0, to 3: today, yesterday and the day before
      DateCondition cond = new DateCondition.DaysCondition(0, 3);

      assertTrue(cond.evaluate(noon(today)));
      assertTrue(cond.evaluate(noon(today.minusDays(2))));
      assertFalse(cond.evaluate(noon(today.minusDays(3))));
      assertFalse(cond.evaluate(noon(today.plusDays(1))));
      sameDay(today);
   }

   @Test
   void daysFromOneToTwoIsYesterdayOnly() {
      LocalDate today = LocalDate.now();
      DateCondition cond = new DateCondition.DaysCondition(1, 2);

      assertFalse(cond.evaluate(noon(today)));
      assertTrue(cond.evaluate(noon(today.minusDays(1))));
      assertFalse(cond.evaluate(noon(today.minusDays(2))));
      sameDay(today);
   }

   @Test
   void daysSqlConditionRunsFromTheStartOfTheFirstDayToTheEndOfTheLast() {
      LocalDate today = LocalDate.now();
      // a daylight saving change in the window moves the start by an hour
      Assumptions.assumeTrue(ZONE.getRules().getOffset(today.atStartOfDay(ZONE).toInstant())
         .equals(ZONE.getRules().getOffset(today.minusDays(3).atStartOfDay(ZONE).toInstant())),
         "daylight saving changes in the window");
      DateCondition cond = new DateCondition.DaysCondition(1, 4);

      // yesterday back to 3 days ago
      assertRange(today.minusDays(3).atStartOfDay(), today.minusDays(1).atTime(23, 59, 59), cond);
      sameDay(today);
   }

   @Test
   void daysConditionIsValidOnlyWhenFromIsBeforeTo() {
      assertTrue(new DateCondition.DaysCondition(0, 1).isValid());
      assertFalse(new DateCondition.DaysCondition(2, 2).isValid());
      assertFalse(new DateCondition.DaysCondition(5, 2).isValid());
   }

   @Test
   void daysConditionNegatedAndNotADate() {
      LocalDate today = LocalDate.now();
      DateCondition cond = new DateCondition.DaysCondition(0, 1);
      cond.setNegated(true);

      assertFalse(cond.evaluate(noon(today)));
      assertTrue(cond.evaluate(noon(today.minusDays(1))));
      assertFalse(cond.evaluate("today"));
      sameDay(today);
   }

   // ---- the Nth quarter, half year and month of a year some years ago ----

   @ParameterizedTest(name = "quarter {0} of {1} years ago")
   @ValueSource(ints = { 0, 1, 2, 3 })
   void nthQuarterOfAYear(int quarter) {
      LocalDate today = LocalDate.now();

      for(int yearsAgo = 0; yearsAgo <= 2; yearsAgo++) {
         int year = today.getYear() - yearsAgo;
         DateCondition.NthQuarterCondition cond =
            new DateCondition.NthQuarterCondition(quarter, yearsAgo);
         int firstMonth = quarter * 3 + 1;
         LocalDate first = LocalDate.of(year, firstMonth, 1);
         LocalDate last = first.plusMonths(3).minusDays(1);

         assertTrue(cond.evaluate(noon(first)), "first day");
         assertTrue(cond.evaluate(noon(last)), "last day");
         assertFalse(cond.evaluate(noon(first.minusDays(1))), "day before");
         assertFalse(cond.evaluate(noon(last.plusDays(1))), "day after");
         assertRange(first.atStartOfDay(), last.atTime(23, 59, 59), cond);
      }

      sameDay(today);
   }

   @Test
   void nthQuarterOfAnotherYearIsNotInTheRange() {
      LocalDate today = LocalDate.now();
      DateCondition cond = new DateCondition.NthQuarterCondition(1, 0);

      // the same quarter of the year before
      assertFalse(cond.evaluate(noon(LocalDate.of(today.getYear() - 1, 5, 15))));
      assertTrue(cond.evaluate(noon(LocalDate.of(today.getYear(), 5, 15))));
      cond.setNegated(true);
      assertTrue(cond.evaluate(noon(LocalDate.of(today.getYear() - 1, 5, 15))));
      assertFalse(cond.evaluate(noon(LocalDate.of(today.getYear(), 5, 15))));
      sameDay(today);
   }

   // a value out of the range is ignored by the setters, the old one is kept: a guard against a
   // wrong call (the xml parser sets the fields directly)
   @Test
   void settersIgnoreValuesOutOfRange() {
      DateCondition.NthQuarterCondition quarter = new DateCondition.NthQuarterCondition(2, 1);
      quarter.setQuarterN(4);
      quarter.setQuarterN(-1);
      quarter.setYearN(-1);
      assertEquals(2, quarter.getQuarterN());
      assertEquals(1, quarter.getYearN());

      DateCondition.NthHalfYearCondition half = new DateCondition.NthHalfYearCondition(1, 3);
      half.setHalfYearN(2);
      half.setYearN(-5);
      assertEquals(1, half.getHalfYearN());
      assertEquals(3, half.getYearN());

      DateCondition.NthMonthCondition month = new DateCondition.NthMonthCondition(11, 0);
      month.setMonthN(12);
      month.setYearN(-1);
      assertEquals(11, month.getMonthN());
      assertEquals(0, month.getYearN());
   }

   @ParameterizedTest(name = "half year {0}")
   @ValueSource(ints = { 0, 1 })
   void nthHalfYearOfAYear(int half) {
      LocalDate today = LocalDate.now();

      for(int yearsAgo = 0; yearsAgo <= 2; yearsAgo++) {
         int year = today.getYear() - yearsAgo;
         DateCondition.NthHalfYearCondition cond =
            new DateCondition.NthHalfYearCondition(half, yearsAgo);
         LocalDate first = LocalDate.of(year, half * 6 + 1, 1);
         LocalDate last = first.plusMonths(6).minusDays(1);

         assertTrue(cond.evaluate(noon(first)), "first day");
         assertTrue(cond.evaluate(noon(last)), "last day");
         assertFalse(cond.evaluate(noon(first.minusDays(1))), "day before");
         assertFalse(cond.evaluate(noon(last.plusDays(1))), "day after");
         assertRange(first.atStartOfDay(), last.atTime(23, 59, 59), cond);
      }

      sameDay(today);
   }

   // the last day of the month in the sql condition, including February of a leap year
   @ParameterizedTest(name = "month {0}")
   @ValueSource(ints = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 })
   void nthMonthOfAYear(int month) {
      LocalDate today = LocalDate.now();

      for(int yearsAgo = 0; yearsAgo <= 4; yearsAgo++) {
         YearMonth ym = YearMonth.of(today.getYear() - yearsAgo, month + 1);
         DateCondition.NthMonthCondition cond = new DateCondition.NthMonthCondition(month, yearsAgo);

         assertTrue(cond.evaluate(noon(ym.atDay(1))), ym + " first day");
         assertTrue(cond.evaluate(noon(ym.atEndOfMonth())), ym + " last day");
         assertFalse(cond.evaluate(noon(ym.atDay(1).minusDays(1))), ym + " day before");
         assertFalse(cond.evaluate(noon(ym.atEndOfMonth().plusDays(1))), ym + " day after");
         assertRange(ym.atDay(1).atStartOfDay(), ym.atEndOfMonth().atTime(23, 59, 59), cond);
      }

      sameDay(today);
   }

   // ---- a part of a date ----

   @Test
   void partDateComparesTheDatePartWithTheValue() {
      // the month of the year: February is 2
      DateCondition cond = new DateCondition.PartDateCondition(DateRangeRef.MONTH_OF_YEAR_PART, 2);

      assertTrue(cond.evaluate(noon(LocalDate.of(2024, 2, 29))));
      assertTrue(cond.evaluate(noon(LocalDate.of(1999, 2, 1))));
      assertFalse(cond.evaluate(noon(LocalDate.of(2024, 3, 1))));
      assertFalse(cond.evaluate(noon(LocalDate.of(2024, 1, 31))));
   }

   // the part itself (the value of a date part column) is compared directly
   @Test
   void partDateAcceptsTheNumberOfThePart() {
      DateCondition cond = new DateCondition.PartDateCondition(DateRangeRef.MONTH_OF_YEAR_PART, 2);

      assertTrue(cond.evaluate(2));
      assertFalse(cond.evaluate(3));
      assertFalse(cond.evaluate("2"));
      assertFalse(cond.evaluate(2L));
      assertFalse(cond.evaluate(null));
   }

   @Test
   void partDateNegatedAndNotPushedDown() {
      DateCondition.PartDateCondition cond =
         new DateCondition.PartDateCondition(DateRangeRef.DAY_OF_WEEK_PART, 1);
      cond.setNegated(true);

      // 2024-02-04 is a Sunday: day of week 1
      assertFalse(cond.evaluate(noon(LocalDate.of(2024, 2, 4))));
      assertTrue(cond.evaluate(noon(LocalDate.of(2024, 2, 5))));
      // a part of a date has no sql condition, the condition is evaluated in memory
      assertNull(cond.toSqlCondition(true));
      assertTrue(cond.isValid());
   }
}
