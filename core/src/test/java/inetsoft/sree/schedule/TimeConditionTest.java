/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.sree.schedule;

import inetsoft.test.*;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.io.StringReader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/*
 * Intent vs implementation suspects: Bug #75498
 *
 * [BUG-TC-1] getRetryTime(curr) single-arg -> intent: uses real lastRun from scheduler
 *            actual: AbstractConditionTrigger.getFireTimeAfter() passes curr as lastRun
 *                    -> interval > 1 fires on wrong cadence on repeat
 *            Fix: pass Scheduler.getLastScheduledRunTime() instead of curr
 *
 * [BUG-TC-2] getRetryTime() EVERY_HOUR with hourlyInterval <= 0 -> intent: return -1 (no valid slot)
 *            actual: infinite loop in the hourly advancement loop
 *            Fix: guard with hourlyInterval <= 0 -> return -1 before entering the loop
 *
 * [BUG-TC-3] getRetryTime() WEEK_OF_YEAR type -> intent: finds next occurrence of the target week
 *            actual: no switch branch for WEEK_OF_YEAR (type=5); weekOfYear is ignored
 *            Fix: add WEEK_OF_YEAR case in getRetryTime switch  TimeCondition.java
 *
 * [BUG-TC-4] parseXML() -> intent: restores any saved timeType including WEEK_OF_YEAR (5)
 *            actual: switch rejects timeType 5 -> throws or silently discards; saved tasks unloadable
 *            Fix: add case 5 in parseXML switch  TimeCondition.java
 *
 * [BUG-TC-5] check() repeating types -> intent: returns true when scheduled time is reached
 *            actual: scheduleTime updated only for AT type; check() always false for repeating types
 *            Fix: update scheduleTime inside getRetryTime for all repeating types
 */

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class TimeConditionTest {
   private TimeCondition timeCondition;
   private TimeZone Asia_Shanghai = TimeZone.getTimeZone(ZoneId.of("Asia/Shanghai"));
   private static Locale originalLocale;
   private static TimeZone originalTimeZone;

   @BeforeAll
   static void setUp() {
      originalTimeZone = TimeZone.getDefault();
      originalLocale = Locale.getDefault();

      TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("US/Eastern")));
      Locale.setDefault(Locale.US);
   }

   @AfterAll
   static void after() {
      TimeZone.setDefault(originalTimeZone);
      Locale.setDefault(originalLocale);
   }

   @Test
   void testAtDate() {
      Date testDate = toDate("2025-03-01T10:15:23");
      timeCondition = TimeCondition.at(testDate);

      assertThat(timeCondition.toString(), Matchers.containsString("TimeCondition: at 2025-03-01"));
      assertEquals(TimeCondition.AT, timeCondition.getType());
      assertEquals(testDate, timeCondition.getDate());
   }

   @Test
   void testAtHMS() {
      timeCondition = TimeCondition.at(10, 35, 59);
      timeCondition.setTimeZone(Asia_Shanghai);
      timeCondition.setWeekdayOnly(true);
      timeCondition.setInterval(2);
      assertEquals(10, timeCondition.getHour());
      assertEquals(35, timeCondition.getMinute());
      assertEquals(59, timeCondition.getSecond());
      assertEquals(TimeCondition.EVERY_DAY, timeCondition.getType());
      assertTrue(timeCondition.isWeekdayOnly());
      assertEquals(2, timeCondition.getInterval());
      assertTrue(timeCondition.toString().contains("TimeCondition: 10:35:59(China Standard Time)"));

      timeCondition = TimeCondition.at(0, 0, 0);
      assertEquals(0, timeCondition.getHour());
      assertEquals(0, timeCondition.getMinute());
      assertEquals(0, timeCondition.getSecond());
      assertTrue(timeCondition.toString().contains("TimeCondition: 00:00:00"));

      timeCondition = TimeCondition.at(23, 59, 59);
      assertEquals(23, timeCondition.getHour());
      assertEquals(59, timeCondition.getMinute());
      assertEquals(59, timeCondition.getSecond());
      assertTrue(timeCondition.toString().contains("TimeCondition: 23:59:59"));
   }

   @Test
   void testAtHMSToStringNormalizesOutOfRangeValues() {
      // at() stores raw values; toString() formats via Calendar which rolls over overflow
      timeCondition = TimeCondition.at(-1, 60, 63);
      assertEquals(-1, timeCondition.getHour());
      assertEquals(60, timeCondition.getMinute());
      assertEquals(63, timeCondition.getSecond());
      assertTrue(timeCondition.toString().contains("TimeCondition: 00:01:03"));

      timeCondition = TimeCondition.at(25, 61, 125);
      assertEquals(25, timeCondition.getHour());
      assertEquals(61, timeCondition.getMinute());
      assertEquals(125, timeCondition.getSecond());
      assertTrue(timeCondition.toString().contains("TimeCondition: 02:03:05"));
   }

   @ParameterizedTest
   @MethodSource("provideDayOfMonthWithDayTestCases")
   void testAtDayOfMonthWithDay(int dayOfMonth,  int[]  monthsOfYear, String expectedString) {
      timeCondition = TimeCondition.atDayOfMonth(dayOfMonth, 0, 0, 0);
      timeCondition.setMonthsOfYear(monthsOfYear);

      assertEquals(dayOfMonth, timeCondition.getDayOfMonth());
      assertEquals(TimeCondition.EVERY_MONTH, timeCondition.getType());
      assertTrue(timeCondition.toString().contains(expectedString));
   }
   private static Stream<Arguments> provideDayOfMonthWithDayTestCases() {
      return Stream.of(
         arguments(15, new int[] {0, 11}, "Day 15 of month January, December of year"),
         arguments(1, new int[] {3}, "Day 1 of month April of year"),
         arguments(32, new int[] {13}, "Day 32 of month 13 of year"), // invalid day, invalid month
         arguments(-1, new int[] {-1}, "Last day of month -1 of year"), // invalid day, invalid month
         arguments(0, new int[] {0}, "Day 0 of month January of year"), // invalid day
         arguments(-2, new int[] {0}, "Day before last day of month January of year")
      );
   }

   @ParameterizedTest
   @MethodSource("provideDayOfMonthWithWeekTestCases")
   void testAtDayOfMonthWithWeek(int dayOfMonth,  int weekOfMonth, int dayOfWeek, int[]  monthsOfYear, String expectedString) {
      timeCondition = TimeCondition.atDayOfMonth(dayOfMonth, 0, 0, 0);

      timeCondition.setWeekOfMonth(weekOfMonth);
      timeCondition.setDayOfWeek(dayOfWeek);
      timeCondition.setMonthsOfYear(monthsOfYear);

      assertEquals(dayOfMonth, timeCondition.getDayOfMonth());
      assertEquals(weekOfMonth, timeCondition.getWeekOfMonth());
      assertEquals(dayOfWeek, timeCondition.getDayOfWeek());
      assertEquals(TimeCondition.EVERY_MONTH, timeCondition.getType());
      assertTrue(timeCondition.toString().contains(expectedString));
   }
   private static Stream<Arguments> provideDayOfMonthWithWeekTestCases() {
      return Stream.of(
         arguments(15, 1, Calendar.SUNDAY,  new int[] {0, 11}, "1st Sunday of month January, December of year"),
         arguments(1, 5, Calendar.MONDAY, new int[] {3}, "5th Monday of month April of year"),
         arguments(32, 6, 8, new int[] {13}, "6th 8 of month 13 of year"), // invalid dayOfMonth, invalid weekOfMonth, invalid dayOfWeek
         arguments(-1, -1, -1, new int[] {-1}, "Last day of month -1 of year"), //invalid dayOfMonth, invalid weekOfMonth, invalid dayOfWeek
         arguments(0, 0, 0, new int[] {0}, "Day 0 of month January of year")// invalid dayOfMonth, invalid weekOfMonth, invalid dayOfWeek
      );
   }

   @ParameterizedTest
   @MethodSource("provideDaysOfWeekTestCases")
   void testAtDaysOfWeek(int[] daysOfWeek,  int interval, int expectedType, String expectCondition) {
      TimeCondition condition = TimeCondition.atDaysOfWeek(daysOfWeek, 23, 23, 23);
      condition.setInterval(interval);

      assertEquals(expectedType, condition.getType());
      assertEquals(interval, condition.getInterval());
      assertArrayEquals(daysOfWeek, condition.getDaysOfWeek());
      assertTrue(condition.toString().contains(expectCondition));
   }
   private static Stream<Arguments> provideDaysOfWeekTestCases() {
      return Stream.of(
         arguments(new int[]{Calendar.SUNDAY}, 2, TimeCondition.EVERY_WEEK, "TimeCondition: Sunday of Week, every 2 week(s)"),
         arguments(new int[]{Calendar.FRIDAY, Calendar.MONDAY}, 0, TimeCondition.EVERY_WEEK, "TimeCondition: Friday, Monday of Week"), //valid daysOf week, invalid interval
         arguments(new int[]{0, 9}, -1, TimeCondition.EVERY_WEEK, "TimeCondition: 0, 9 of Week")   //invalid values
      );
   }

   @ParameterizedTest
   @MethodSource("provideHoursTestCases")
   void testAtHours(int hour, int minute, int second, float interval,  int expectedType, String expectCondition) {
      TimeCondition condition = TimeCondition.atHours(new int[] {Calendar.SUNDAY, Calendar.MONDAY}, hour, minute, second);
      condition.setHourlyInterval(interval);
      assertEquals(expectedType, condition.getType());
      assertEquals(interval, condition.getHourlyInterval());
      assertTrue(condition.toString().contains(expectCondition));
   }
   private static Stream<Arguments> provideHoursTestCases() {
      return Stream.of(
         arguments(23, 59, 59, 2, TimeCondition.EVERY_HOUR, "TimeCondition: Hour of Sunday, Monday, every 2.0 hour(s)"),
         arguments(0, 0, 0, 0, TimeCondition.EVERY_HOUR, "TimeCondition: Hour of Sunday, Monday"), //valid hour, invalid interval
         arguments(-1, 62, 126, -1, TimeCondition.EVERY_HOUR, "TimeCondition: Hour of Sunday, Monday")   //invalid values
      );
   }

   @ParameterizedTest
   @MethodSource("provideWeekOfMonthTestCases")
   void testAtWeekOfMonth(int weekOfMonth, int dayOfWeek, String expectedString) {
      TimeCondition condition = TimeCondition.atWeekOfMonth(weekOfMonth, dayOfWeek, 0, 0, 0);

      assertEquals(weekOfMonth, condition.getWeekOfMonth());
      assertEquals(dayOfWeek, condition.getDayOfWeek());
      assertEquals(TimeCondition.EVERY_MONTH, condition.getType());
      assertTrue(condition.toString().contains(expectedString));
   }

   private static Stream<Arguments> provideWeekOfMonthTestCases() {
      return Stream.of(
         // Valid inputs
         arguments(1, Calendar.SUNDAY, "1st Sunday of month"),
         arguments(5, Calendar.FRIDAY,  "5th Friday of month"),

         // Invalid inputs
         arguments(0, Calendar.SUNDAY, "Day 0 of month"), // Invalid weekOfMonth
         arguments(-1, -1, "Day 0 of month"), // Negative weekOfMonth
         arguments(6, 8,  "6th 8 of month") // Invalid dayOfWeek, hour, minute, second
      );
   }

   /**
    * atWeekOfYear and atDayOfWeek didn't use by other class, so only basic check.
    */
   @Test
   void testAtWeekOfYearDay() {
      TimeCondition condition = TimeCondition.atWeekOfYear(1, Calendar.SUNDAY, 23, 59, 59);
      assertEquals(1, condition.getWeekOfYear());
      assertEquals( Calendar.SUNDAY, condition.getDayOfWeek());
      assertEquals(TimeCondition.WEEK_OF_YEAR, condition.getType());

      condition = TimeCondition.atWeekOfYear(53, -1, 0,0,0);
      assertEquals(53, condition.getWeekOfYear());
      assertEquals( -1, condition.getDayOfWeek());

      condition = TimeCondition.atDayOfWeek(1, 23,23,23);
      assertArrayEquals(new int[] {1}, condition.getDaysOfWeek());
      assertEquals(TimeCondition.EVERY_WEEK, condition.getType());
      assertTrue(condition.toString().contains("Sunday of Week"));

      condition = TimeCondition.atDayOfWeek(-1, 23,23,23);
      assertArrayEquals(new int[] {-1}, condition.getDaysOfWeek());
      assertTrue(condition.toString().contains("-1 of Week"));
   }

   @Test
   void testSomeSetGetFunctions() {
      TimeCondition condition = new TimeCondition();
      condition.setDate(toDate("2025-02-01T00:00:00"));
      assertEquals(condition.getDate(),toDate("2025-02-01T00:00:00"));

      condition.setDayOfMonth(2);
      assertEquals(2, condition.getDayOfMonth());

      condition.setWeekOfYear(53);
      assertEquals(53, condition.getWeekOfYear());

      condition.setHour(25);
      assertEquals(25, condition.getHour());

      condition.setMinute(63);
      assertEquals(63, condition.getMinute());

      condition.setSecond(120);
      assertEquals(120, condition.getSecond());

      condition.setHourEnd(2);
      assertEquals(2, condition.getHourEnd());

      condition.setMinuteEnd(50);
      assertEquals(50, condition.getMinuteEnd());

      condition.setSecondEnd(32);
      assertEquals(32, condition.getSecondEnd());

      int[] days = new int[] {0,6};
      condition.setDaysOfWeek(days);
      assertEquals(days, condition.getDaysOfWeek());

      int[] months = new int[] {0,11,7};
      condition.setMonthsOfYear(months);
      assertEquals(months, condition.getMonthsOfYear());

      TimeRange mockTimeRange = mock(TimeRange.class);
      condition.setTimeRange(mockTimeRange);
      assertEquals(mockTimeRange, condition.getTimeRange());

      condition.setAJAX(true);
      assertTrue(condition.isAJAX());
   }

   @ParameterizedTest
   @CsvSource({
      "30, 0, 30",
      "30, 15, 45",
      "30, -15, 15",
       "55, 10, 5",
      "59, 1, 0",
      "5, -10, 55",
      "0, -1, 59",
      "0, 0, 0",
      "59, 20, 19"
   })
   void testGetMinute(int baseMinute, int offset, int expectedMinute) {
      TimeCondition condition = new TimeCondition();
      assertEquals(expectedMinute, condition.getMinute(baseMinute, offset));
   }

   @Test
   void testCheckAtCondition() {
      Date scheduled = toDate("2025-03-01T10:15:23");
      TimeCondition condition = TimeCondition.at(scheduled);
      long scheduledMillis = scheduled.getTime();

      assertFalse(condition.check(scheduledMillis));

      long retry = condition.getRetryTime(toDate("2025-03-01T10:00:00").getTime(), 0);
      assertEquals(scheduledMillis, retry);
      assertTrue(condition.check(scheduledMillis));
      assertTrue(condition.check(scheduledMillis + 1000));
      assertFalse(condition.check(scheduledMillis - 1));
   }

   @Test
   void testGetRetryTimeSingleArgUsesCurrentAsLastRun() {
      Date scheduled = toDate("2025-03-01T10:15:23");
      TimeCondition condition = TimeCondition.at(scheduled);
      long curr = toDate("2025-03-01T10:00:00").getTime();

      assertEquals(condition.getRetryTime(curr, curr), condition.getRetryTime(curr));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("provideGetRetryTimeCases")
   void testGetRetryTime(String name, RetryTimeCase testCase) {
      testCase.run();
   }

   @FunctionalInterface
   private interface RetryTimeCase {
      void run();
   }

   @Test
   void everyDayWeekdayOnlySkipsWeekend() {
      // today = 2025-03-07 (Friday); curr = Friday 12:00 (past 09:00 slot)
      // algorithm skips Saturday (2025-03-08) and Sunday (2025-03-09) → Monday 2025-03-10 09:00
      TimeCondition condition = TimeCondition.at(9, 0, 0);
      condition.setWeekdayOnly(true);
      long curr = toFixedMillis("2025-03-07T12:00:00");
      long retry;
      try(MockedStatic<Calendar> ignored = mockTodayAs("2025-03-07T00:00:00")) {
         retry = condition.getRetryTime(curr, 0);
      }
      assertEquals(toFixedMillis("2025-03-10T09:00:00"), retry);
   }

   @Test
   void everyWeekFindsNextSelectedWeekday() {
      // today = 2025-03-04 (Tuesday); curr = 20:35:30 (past 20:30:30 slot)
      // scans Tuesday-Saturday of current week: none in [SUN=1, MON=2]
      // no extra weeks (interval=1) → advances to next Sunday: 2025-03-09 20:30:30
      TimeCondition condition = TimeCondition.atDaysOfWeek(
         new int[] {Calendar.SUNDAY, Calendar.MONDAY}, 20, 30, 30);
      condition.setInterval(1);
      long curr = toFixedMillis("2025-03-04T20:35:30");
      long retry;
      try(MockedStatic<Calendar> ignored = mockTodayAs("2025-03-04T00:00:00")) {
         retry = condition.getRetryTime(curr, 0);
      }
      assertEquals(toFixedMillis("2025-03-09T20:30:30"), retry);
   }

   @Test
   void everyWeekIntervalUsesLastRunAsAnchor() {
      // interval=2 → cal1 is anchored to lastRun (not Calendar.getInstance()), so the result is deterministic.
      // lastRun  = 2025-03-03 (Monday) 08:00 — base week
      // curr     = 2025-03-12 (Wednesday) 09:00 — past the last Monday's slot
      // Algorithm: scans Mon-Sat of lastRun's week (all < curr), adds (2-1)*7=7 days to Saturday → 2025-03-15,
      //            then advances to next Monday: 2025-03-17 08:00.
      TimeCondition condition = TimeCondition.atDaysOfWeek(new int[] {Calendar.MONDAY}, 8, 0, 0);
      condition.setInterval(2);
      long lastRun = toFixedMillis("2025-03-03T08:00:00"); // Monday
      long curr    = toFixedMillis("2025-03-12T09:00:00"); // Wednesday, past the last Monday slot
      assertEquals(toFixedMillis("2025-03-17T08:00:00"), condition.getRetryTime(curr, lastRun));
   }

   @Test
   void everyHourFindsNextTickInsideWindow() {
      // today = 2025-03-04 (Tuesday); window 08:00-14:00, hourlyInterval=2h; curr = 09:30
      // start=08:00 < curr → advance 2h → 10:00 >= curr → return 2025-03-04T10:00:00
      TimeCondition condition = TimeCondition.atHours(new int[] {Calendar.TUESDAY}, 8, 0, 0);
      condition.setHourlyInterval(2f);
      condition.setHourEnd(14);
      condition.setMinuteEnd(0);
      condition.setSecondEnd(0);
      long curr = toFixedMillis("2025-03-04T09:30:00");
      long retry;
      try(MockedStatic<Calendar> ignored = mockTodayAs("2025-03-04T00:00:00")) {
         retry = condition.getRetryTime(curr, 0);
      }
      assertEquals(toFixedMillis("2025-03-04T10:00:00"), retry);
   }

   @Test
   void everyMonthFindsNextNthWeekdayOfSelectedMonth() {
      // today = 2025-03-01 (Saturday); curr = Saturday 21:00 (past 20:30:30 slot)
      // scan: 1st Tue=Mar 4, 2nd Tue=Mar 11, 3rd Tue=Mar 18 (date 18 ∈ [15,21]) → stop
      TimeCondition condition = TimeCondition.atDayOfMonth(1, 20, 30, 30);
      condition.setMonthsOfYear(new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11});
      condition.setWeekOfMonth(3);
      condition.setDayOfWeek(Calendar.TUESDAY);
      long curr = toFixedMillis("2025-03-01T21:00:00");
      long retry;
      try(MockedStatic<Calendar> ignored = mockTodayAs("2025-03-01T00:00:00")) {
         retry = condition.getRetryTime(curr, 0);
      }
      assertEquals(toFixedMillis("2025-03-18T20:30:30"), retry);
   }

   @Test
   void everyMonthFindsNextFixedDayOfSelectedMonth() {
      // today = 2025-03-01 (Saturday); curr = Saturday 10:00 (past 09:00 slot)
      // scan: Mar 2..14 (day ≠ 15) → Mar 15 09:00 → stop
      TimeCondition condition = TimeCondition.atDayOfMonth(15, 9, 0, 0);
      condition.setMonthsOfYear(new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11});
      long curr = toFixedMillis("2025-03-01T10:00:00");
      long retry;
      try(MockedStatic<Calendar> ignored = mockTodayAs("2025-03-01T00:00:00")) {
         retry = condition.getRetryTime(curr, 0);
      }
      assertEquals(toFixedMillis("2025-03-15T09:00:00"), retry);
   }

   private static void assertRetryTimeEquals(long expected, long actual) {
      assertEquals(expected / 1000, actual / 1000);
   }

   private static void assertExactRetryTime(long expected, long actual) {
      assertEquals(expected, actual);
   }

   private static Stream<Arguments> provideGetRetryTimeCases() {
      return Stream.of(
         arguments("AT returns exact time when still in the future",
            (RetryTimeCase) () -> {
               Date scheduled = toFixedDate("2025-03-01T10:15:23");
               TimeCondition condition = TimeCondition.at(scheduled);
               assertExactRetryTime(scheduled.getTime(), condition.getRetryTime(scheduled.getTime(), 0));
            }),
         arguments("AT returns -1 when scheduled time has passed",
            (RetryTimeCase) () -> {
               Date scheduled = toFixedDate("2025-03-01T10:15:23");
               TimeCondition condition = TimeCondition.at(scheduled);
               assertEquals(-1, condition.getRetryTime(toFixedMillis("2025-03-01T10:20:10"), 0));
            }),
         arguments("EVERY_DAY returns -1 when interval is not positive",
            (RetryTimeCase) () -> {
               TimeCondition condition = TimeCondition.at(20, 30, 30);
               long curr = todayAt(20, 35, 30);
               assertEquals(-1, condition.getRetryTime(curr, 0));
            }),
         arguments("EVERY_DAY interval 1 keeps same day when slot is still ahead",
            (RetryTimeCase) () -> {
               TimeCondition condition = TimeCondition.at(20, 30, 30);
               condition.setInterval(1);
               long curr = todayAt(8, 0, 0);
               Calendar expected = calendarWithScheduledTime(20, 30, 30);
               assertRetryTimeEquals(expected.getTimeInMillis(), condition.getRetryTime(curr, 0));
            }),
         arguments("EVERY_DAY interval 1 advances one day after slot has passed",
            (RetryTimeCase) () -> {
               TimeCondition condition = TimeCondition.at(20, 30, 30);
               condition.setInterval(1);
               long curr = todayAt(20, 35, 30);
               Calendar expected = calendarWithScheduledTime(20, 30, 30);
               expected.add(Calendar.DATE, 1);
               assertRetryTimeEquals(expected.getTimeInMillis(), condition.getRetryTime(curr, 0));
            }),
         arguments("EVERY_DAY interval 2 advances two days after slot has passed",
            (RetryTimeCase) () -> {
               TimeCondition condition = TimeCondition.at(20, 30, 30);
               condition.setInterval(2);
               long curr = todayAt(20, 35, 30);
               Calendar expected = calendarWithScheduledTime(20, 30, 30);
               expected.add(Calendar.DATE, 2);
               assertRetryTimeEquals(expected.getTimeInMillis(), condition.getRetryTime(curr, 0));
            }),
         arguments("EVERY_WEEK returns -1 when no weekday is selected",
            (RetryTimeCase) () -> {
               TimeCondition condition = TimeCondition.atDaysOfWeek(new int[] {}, 20, 30, 30);
               assertEquals(-1, condition.getRetryTime(todayAt(20, 35, 30), 0));
            }),
         arguments("EVERY_HOUR returns -1 when no weekday is selected",
            (RetryTimeCase) () -> {
               TimeCondition condition = TimeCondition.atHours(new int[] {}, 20, 30, 30);
               assertEquals(-1, condition.getRetryTime(todayAt(20, 35, 30), 0));
            }),
         arguments("EVERY_MONTH returns -1 when no month is selected",
            (RetryTimeCase) () -> {
               TimeCondition condition = TimeCondition.atDayOfMonth(5, 20, 30, 30);
               condition.setMonthsOfYear(new int[] {});
               assertEquals(-1, condition.getRetryTime(todayAt(20, 35, 30), 0));
            })
      );
   }

   private static int todayDayOfWeek() {
      return Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
   }

   private static Calendar calendarWithScheduledTime(int hour, int minute, int second) {
      Calendar cal = Calendar.getInstance();
      cal.set(Calendar.HOUR_OF_DAY, hour);
      cal.set(Calendar.MINUTE, minute);
      cal.set(Calendar.SECOND, second);
      return cal;
   }

   private static long todayAt(int hour, int minute, int second) {
      return todayAt(hour, minute, second, 0);
   }

   private static long todayAt(int hour, int minute, int second, int dayOffset) {
      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.DATE, dayOffset);
      cal.set(Calendar.HOUR_OF_DAY, hour);
      cal.set(Calendar.MINUTE, minute);
      cal.set(Calendar.SECOND, second);
      cal.set(Calendar.MILLISECOND, 0);
      return cal.getTimeInMillis();
   }

   // Mocks Calendar.getInstance(TimeZone) to return a fresh GregorianCalendar anchored to the
   // given local date-time string, making getRetryTime() results deterministic. The caller is
   // responsible for closing the returned MockedStatic (use try-with-resources).
   private static MockedStatic<Calendar> mockTodayAs(String localDateTime) {
      long epoch = toFixedMillis(localDateTime);
      MockedStatic<Calendar> mocked = mockStatic(Calendar.class, Mockito.CALLS_REAL_METHODS);
      mocked.when(() -> Calendar.getInstance(any(TimeZone.class)))
            .thenAnswer(inv -> {
               GregorianCalendar cal = new GregorianCalendar((TimeZone) inv.getArgument(0));
               cal.setTimeInMillis(epoch);
               return cal;
            });
      return mocked;
   }

   private static Date toFixedDate(String localDateTime) {
      return Date.from(LocalDateTime.parse(localDateTime)
         .atZone(ZoneId.systemDefault())
         .toInstant());
   }

   private static long toFixedMillis(String localDateTime) {
      return toFixedDate(localDateTime).getTime();
   }

   private Date toDate(String localDateTime) {
      return toFixedDate(localDateTime);
   }

   private static Element parseConditionXml(String xml) throws Exception {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      return factory.newDocumentBuilder()
         .parse(new InputSource(new StringReader(xml)))
         .getDocumentElement();
   }

   // ─────────────────────────────────────────────────────────────────────────
   // XML round-trip serialization
   // ─────────────────────────────────────────────────────────────────────────

   @Nested
   @DisplayName("XML round-trip serialization")
   class XmlRoundTrip {

      private TimeCondition writeAndParse(TimeCondition condition) throws Exception {
         StringWriter sw = new StringWriter();
         condition.writeXML(new java.io.PrintWriter(sw));
         TimeCondition loaded = new TimeCondition();
         loaded.parseXML(parseConditionXml(sw.toString()));
         return loaded;
      }

      @Test
      void atRoundTrip() throws Exception {
         Date date = toFixedDate("2025-06-15T14:30:00");
         TimeCondition loaded = writeAndParse(TimeCondition.at(date));
         assertEquals(TimeCondition.AT, loaded.getType());
         assertEquals(date.getTime() / 1000, loaded.getDate().getTime() / 1000);
      }

      @Test
      void everyDayRoundTrip() throws Exception {
         TimeCondition original = TimeCondition.at(9, 30, 15);
         original.setInterval(3);
         TimeCondition loaded = writeAndParse(original);
         assertEquals(TimeCondition.EVERY_DAY, loaded.getType());
         assertEquals(9, loaded.getHour());
         assertEquals(30, loaded.getMinute());
         assertEquals(15, loaded.getSecond());
         assertEquals(3, loaded.getInterval());
         assertFalse(loaded.isWeekdayOnly());
      }

      @Test
      void everyDayWeekdayOnlyRoundTrip() throws Exception {
         TimeCondition original = TimeCondition.at(8, 0, 0);
         original.setWeekdayOnly(true);
         TimeCondition loaded = writeAndParse(original);
         assertEquals(TimeCondition.EVERY_DAY, loaded.getType());
         assertTrue(loaded.isWeekdayOnly());
      }

      @Test
      void everyWeekRoundTrip() throws Exception {
         TimeCondition original = TimeCondition.atDaysOfWeek(
            new int[]{Calendar.MONDAY, Calendar.WEDNESDAY}, 10, 0, 0);
         original.setInterval(2);
         TimeCondition loaded = writeAndParse(original);
         assertEquals(TimeCondition.EVERY_WEEK, loaded.getType());
         assertArrayEquals(new int[]{Calendar.MONDAY, Calendar.WEDNESDAY}, loaded.getDaysOfWeek());
         assertEquals(10, loaded.getHour());
         assertEquals(2, loaded.getInterval());
      }

      @Test
      void everyHourRoundTrip() throws Exception {
         TimeCondition original = TimeCondition.atHours(
            new int[]{Calendar.TUESDAY, Calendar.THURSDAY}, 8, 30, 0);
         original.setHourlyInterval(2.5f);
         original.setHourEnd(17);
         original.setMinuteEnd(30);
         original.setSecondEnd(0);
         TimeCondition loaded = writeAndParse(original);
         assertEquals(TimeCondition.EVERY_HOUR, loaded.getType());
         assertArrayEquals(new int[]{Calendar.TUESDAY, Calendar.THURSDAY}, loaded.getDaysOfWeek());
         assertEquals(8, loaded.getHour());
         assertEquals(30, loaded.getMinute());
         assertEquals(2.5f, loaded.getHourlyInterval(), 0.001f);
         assertEquals(17, loaded.getHourEnd());
         assertEquals(30, loaded.getMinuteEnd());
      }

      @Test
      void everyMonthByDayOfMonthRoundTrip() throws Exception {
         TimeCondition original = TimeCondition.atDayOfMonth(15, 9, 0, 0);
         original.setMonthsOfYear(new int[]{Calendar.JANUARY, Calendar.JULY});
         TimeCondition loaded = writeAndParse(original);
         assertEquals(TimeCondition.EVERY_MONTH, loaded.getType());
         assertEquals(15, loaded.getDayOfMonth());
         assertArrayEquals(new int[]{Calendar.JANUARY, Calendar.JULY}, loaded.getMonthsOfYear());
         assertEquals(9, loaded.getHour());
      }

      @Test
      void everyMonthByWeekOfMonthRoundTrip() throws Exception {
         TimeCondition original = TimeCondition.atWeekOfMonth(2, Calendar.FRIDAY, 14, 0, 0);
         original.setMonthsOfYear(new int[]{Calendar.MARCH, Calendar.SEPTEMBER});
         TimeCondition loaded = writeAndParse(original);
         assertEquals(TimeCondition.EVERY_MONTH, loaded.getType());
         assertEquals(2, loaded.getWeekOfMonth());
         assertEquals(Calendar.FRIDAY, loaded.getDayOfWeek());
         assertArrayEquals(new int[]{Calendar.MARCH, Calendar.SEPTEMBER}, loaded.getMonthsOfYear());
         assertEquals(14, loaded.getHour());
      }

      @Test
      void timezonePreservedAcrossRoundTrip() throws Exception {
         TimeCondition original = TimeCondition.at(10, 0, 0);
         original.setTimeZone(Asia_Shanghai);
         TimeCondition loaded = writeAndParse(original);
         assertEquals(Asia_Shanghai.getID(), loaded.getTimeZone().getID());
      }
   }

   // ─────────────────────────────────────────────────────────────────────────
   // equals()
   // ─────────────────────────────────────────────────────────────────────────

   @Nested
   @DisplayName("equals()")
   class EqualsTests {

      @Test
      void sameFieldsAreEqual() {
         assertEquals(TimeCondition.at(10, 30, 0), TimeCondition.at(10, 30, 0));
      }

      @Test
      void nullReturnsFalse() {
         assertFalse(TimeCondition.at(10, 0, 0).equals(null));
      }

      @Test
      void nonTimeConditionReturnsFalse() {
         assertFalse(TimeCondition.at(10, 0, 0).equals("not a condition"));
      }

      @Test
      void differentTypeNotEqual() {
         assertNotEquals(TimeCondition.at(10, 0, 0),
            TimeCondition.at(toFixedDate("2025-06-01T10:00:00")));
      }

      @Test
      void differentHourNotEqual() {
         assertNotEquals(TimeCondition.at(10, 0, 0), TimeCondition.at(11, 0, 0));
      }

      @Test
      void differentMinuteNotEqual() {
         assertNotEquals(TimeCondition.at(10, 30, 0), TimeCondition.at(10, 45, 0));
      }

      @Test
      void differentIntervalNotEqual() {
         TimeCondition a = TimeCondition.at(10, 0, 0);
         a.setInterval(1);
         TimeCondition b = TimeCondition.at(10, 0, 0);
         b.setInterval(2);
         assertNotEquals(a, b);
      }

      @Test
      void differentDaysOfWeekNotEqual() {
         assertNotEquals(
            TimeCondition.atDaysOfWeek(new int[]{Calendar.MONDAY}, 10, 0, 0),
            TimeCondition.atDaysOfWeek(new int[]{Calendar.TUESDAY}, 10, 0, 0));
      }

      @Test
      void differentMonthsOfYearNotEqual() {
         TimeCondition a = TimeCondition.atDayOfMonth(1, 9, 0, 0);
         a.setMonthsOfYear(new int[]{Calendar.JANUARY});
         TimeCondition b = TimeCondition.atDayOfMonth(1, 9, 0, 0);
         b.setMonthsOfYear(new int[]{Calendar.FEBRUARY});
         assertNotEquals(a, b);
      }

      @Test
      void differentHourlyIntervalNotEqual() {
         TimeCondition a = TimeCondition.atHours(new int[]{Calendar.MONDAY}, 8, 0, 0);
         a.setHourlyInterval(1f);
         TimeCondition b = TimeCondition.atHours(new int[]{Calendar.MONDAY}, 8, 0, 0);
         b.setHourlyInterval(2f);
         assertNotEquals(a, b);
      }

      @Test
      void differentWeekdayOnlyNotEqual() {
         TimeCondition a = TimeCondition.at(9, 0, 0);
         a.setWeekdayOnly(true);
         assertNotEquals(a, TimeCondition.at(9, 0, 0));
      }

      @Test
      void differentTimeZoneLabelNotEqual() {
         TimeCondition a = TimeCondition.at(9, 0, 0);
         a.setTimeZoneLabel("Server Time");
         TimeCondition b = TimeCondition.at(9, 0, 0);
         b.setTimeZoneLabel("Local Time");
         assertNotEquals(a, b);
      }

      @Test
      void timezoneFieldNotIncludedInEquality() {
         // equals() compares timeZoneLabel but NOT the TimeZone object itself
         TimeCondition a = TimeCondition.at(9, 0, 0);
         a.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
         TimeCondition b = TimeCondition.at(9, 0, 0);
         b.setTimeZone(TimeZone.getTimeZone("US/Eastern"));
         assertEquals(a, b);
      }
   }

   // ─────────────────────────────────────────────────────────────────────────
   // writeAttributes with ajax=true
   // ─────────────────────────────────────────────────────────────────────────

   @Nested
   @DisplayName("writeAttributes with ajax=true")
   class AjaxAttributeTests {

      private String toXml(TimeCondition condition) {
         StringWriter sw = new StringWriter();
         condition.writeXML(new java.io.PrintWriter(sw));
         return sw.toString();
      }

      @Test
      void everyDayAjaxFalseKeepsOriginalHour() {
         TimeCondition condition = TimeCondition.at(15, 0, 0);
         condition.setTimeZone(Asia_Shanghai);
         condition.setAJAX(false);
         assertTrue(toXml(condition).contains("hour=\"15\""));
      }

      @Test
      void everyDayAjaxTrueOffsetsHourByTimezone() {
         // Default timezone: US/Eastern (raw UTC-5); condition TZ: Asia/Shanghai (raw UTC+8)
         // hoff = (-5h − (+8h)) = −13; getHour(15, −13) = 2
         TimeCondition condition = TimeCondition.at(15, 0, 0);
         condition.setTimeZone(Asia_Shanghai);
         condition.setAJAX(true);
         assertTrue(toXml(condition).contains("hour=\"2\""));
      }

      @Test
      void atTypeAjaxTrueAddsTzoffsetAttribute() {
         TimeCondition condition = TimeCondition.at(toFixedDate("2025-06-15T14:30:00"));
         condition.setAJAX(true);
         assertTrue(toXml(condition).contains("tzoffset=\""));
      }
   }

   // ─────────────────────────────────────────────────────────────────────────
   // EVERY_MONTH with weekOfMonth = 0 (edge-case ambiguity)
   // ─────────────────────────────────────────────────────────────────────────

   @Test
   void everyMonthWeekOfMonthZeroXmlRoundTripLosesWeekOfMonth() {
      // weekOfMonth=0: writeAttributes condition is `> 0`, so it writes dayOfMonth
      // instead of weekOfMonth.  The round-tripped condition therefore has
      // weekOfMonth=-1 (default), not 0 — the value is silently dropped.
      TimeCondition condition = TimeCondition.atDayOfMonth(10, 9, 0, 0);
      condition.setWeekOfMonth(0);
      condition.setMonthsOfYear(new int[]{Calendar.JANUARY});

      StringWriter sw = new StringWriter();
      condition.writeXML(new java.io.PrintWriter(sw));
      String xml = sw.toString();

      assertTrue(xml.contains("dayOfMonth=\"10\""), "weekOfMonth=0 serialises as dayOfMonth");
      assertFalse(xml.contains("weekOfMonth"), "weekOfMonth=0 must NOT appear in XML");
   }

   @Test
   void everyMonthWeekOfMonthZeroSchedulingIgnoresDayOfMonth() {
      // getRetryTime: weekOfMonth=0 satisfies >= 0, so enters the week-path.
      // isNthSpecifiedDayOfMonth() returns true unconditionally when week_of_month <= 0,
      // meaning dayOfMonth is not checked — any day in the selected month qualifies.
      // toString() still shows "Day 10 of month …" because its branch is `week_of_month > 0`.
      int thisMonth = Calendar.getInstance().get(Calendar.MONTH);
      TimeCondition condition = TimeCondition.atDayOfMonth(10, 9, 0, 0);
      condition.setWeekOfMonth(0);
      condition.setMonthsOfYear(new int[]{thisMonth});

      assertTrue(condition.toString().contains("Day 10 of month"));
      long retry = condition.getRetryTime(todayAt(10, 30, 0), 0);
      assertTrue(retry > 0, "should return a valid retry time without hanging");
   }

   @Nested
   @Disabled("Known production bugs — remove @Disabled after fix (see class javadoc BUG-TC-*)")
   @Tag("known-bug")
   @DisplayName("Known production bugs (@Disabled until fixed)")
   class KnownBugs {

      @Test
      @DisplayName("BUG-TC-1: single-arg getRetryTime (Quartz path) ignores real lastRun")
      // BUG-TC-1 | getRetryTime(curr) uses curr as lastRun instead of real scheduler lastRun
      // Location : AbstractConditionTrigger.getFireTimeAfter()
      // Fix      : pass Scheduler.getLastScheduledRunTime() as lastRun argument
      void quartzSingleArgRetryIgnoresSchedulerLastRun() {
         Calendar monday = Calendar.getInstance();
         monday.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
         monday.set(Calendar.HOUR_OF_DAY, 8);
         monday.set(Calendar.MINUTE, 0);
         monday.set(Calendar.SECOND, 0);
         monday.set(Calendar.MILLISECOND, 0);

         if(monday.getTimeInMillis() > System.currentTimeMillis()) {
            monday.add(Calendar.WEEK_OF_YEAR, -1);
         }

         Calendar tuesdayAfternoon = (Calendar) monday.clone();
         tuesdayAfternoon.add(Calendar.DATE, 1);
         tuesdayAfternoon.set(Calendar.HOUR_OF_DAY, 15);

         TimeCondition condition = TimeCondition.at(8, 0, 0);
         condition.setInterval(2);

         long lastRun = monday.getTimeInMillis();
         long curr = tuesdayAfternoon.getTimeInMillis();
         long correct = condition.getRetryTime(curr, lastRun);
         long quartzPath = condition.getRetryTime(curr + 30000);

         assertRetryTimeEquals(correct, quartzPath);
      }

      @Test
      @Timeout(2)
      @DisplayName("BUG-TC-2: EVERY_HOUR hourlyInterval=0 must not hang")
      // BUG-TC-2 | hourlyInterval <= 0 causes infinite loop in getRetryTime EVERY_HOUR branch
      // Location : TimeCondition.java getRetryTime() EVERY_HOUR case
      // Fix      : guard with if (hourlyInterval <= 0) return -1 before the advancement loop
      void everyHourZeroIntervalMustNotHang() {
         TimeCondition condition = TimeCondition.atHours(new int[] {todayDayOfWeek()}, 8, 0, 0);
         condition.setHourlyInterval(0f);
         condition.setHourEnd(14);
         condition.setMinuteEnd(0);
         condition.setSecondEnd(0);

         assertEquals(-1, condition.getRetryTime(todayAt(9, 30, 0), 0));
      }

      @Test
      @DisplayName("BUG-TC-3: WEEK_OF_YEAR getRetryTime does not match target week")
      // BUG-TC-3 | WEEK_OF_YEAR type has no switch branch in getRetryTime; weekOfYear is ignored
      // Location : TimeCondition.java getRetryTime() switch statement
      // Fix      : add case WEEK_OF_YEAR that advances to the correct week-of-year
      void weekOfYearRetryIgnoresWeekOfYear() {
         TimeCondition condition = TimeCondition.atWeekOfYear(20, Calendar.MONDAY, 9, 0, 0);
         long curr = toFixedMillis("2025-05-01T12:00:00");
         Calendar retry = Calendar.getInstance();
         retry.setTimeInMillis(condition.getRetryTime(curr, 0));

         assertEquals(20, retry.get(Calendar.WEEK_OF_YEAR));
      }

      @Test
      @DisplayName("BUG-TC-4: parseXML rejects timeType=WEEK_OF_YEAR (5)")
      // BUG-TC-4 | parseXML switch has no case for timeType 5; tasks with WEEK_OF_YEAR type unloadable
      // Location : TimeCondition.java parseXML() switch statement
      // Fix      : add case 5 -> setType(WEEK_OF_YEAR) and restore weekOfYear attribute
      void parseXmlRejectsWeekOfYearType() throws Exception {
         TimeCondition written = TimeCondition.atWeekOfYear(1, Calendar.SUNDAY, 10, 0, 0);
         StringWriter writer = new StringWriter();
         written.writeXML(new java.io.PrintWriter(writer));
         Element element = parseConditionXml(writer.toString());

         TimeCondition loaded = new TimeCondition();
         assertDoesNotThrow(() -> loaded.parseXML(element));
      }

      @Test
      @DisplayName("BUG-TC-5: check() stays false for repeating types after getRetryTime")
      // BUG-TC-5 | check() always returns false for repeating types; scheduleTime never updated
      // Location : TimeCondition.java check() — only AT type sets scheduleTime in getRetryTime
      // Fix      : update scheduleTime inside getRetryTime for EVERY_DAY / EVERY_WEEK / EVERY_MONTH / EVERY_HOUR
      void checkAlwaysFalseForRepeatingTypes() {
         TimeCondition condition = TimeCondition.at(8, 0, 0);
         condition.setInterval(1);
         long curr = todayAt(9, 0, 0);
         long retry = condition.getRetryTime(curr, 0);
         assertTrue(retry > 0);

         assertTrue(condition.check(retry));
      }
   }
}
