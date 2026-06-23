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
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link inetsoft.sree.schedule.TimeCondition} (community scheduler engine).
 *
 * <p>Does not cover {@code inetsoft.enterprise.web.api.schedule.TimeCondition} (REST DTO) or
 * {@code ScheduleApiService.convertCondition()} — see enterprise module for API mapping tests.
 *
 * <h2>Known production bugs Issue #75498 (documented here; production source not changed)</h2>
 * <ul>
 *    <li><b>BUG-TC-1</b> — {@code AbstractConditionTrigger.getFireTimeAfter()} calls
 *       {@code getRetryTime(curr)} (single-arg), so {@code lastRun} defaults to {@code curr}
 *       instead of {@code Scheduler.getLastScheduledRunTime()}. Breaks {@code interval &gt; 1}
 *       for {@code EVERY_DAY} / {@code EVERY_WEEK} on repeat fires. Covered by
 *       {@link KnownBugs#quartzSingleArgRetryIgnoresSchedulerLastRun()}.</li>
 *    <li><b>BUG-TC-2</b> — {@code EVERY_HOUR} with {@code hourlyInterval &lt;= 0} can infinite-loop
 *       in {@code getRetryTime}. Covered by {@link KnownBugs#everyHourZeroIntervalMustNotHang()}.</li>
 *    <li><b>BUG-TC-3</b> — {@code WEEK_OF_YEAR} (type=5): no {@code getRetryTime} switch branch.
 *       Covered by {@link KnownBugs#weekOfYearRetryIgnoresWeekOfYear()}.</li>
 *    <li><b>BUG-TC-4</b> — {@code parseXML} rejects legacy {@code timeType} 2/3/4/5; tasks saved
 *       with those types cannot be reloaded. Covered by {@link KnownBugs#parseXmlRejectsWeekOfYearType()}.</li>
 *    <li><b>BUG-TC-5</b> — {@code check()} only meaningful for {@code AT}; repeating types never
 *       update {@code scheduleTime}. Covered by {@link KnownBugs#checkAlwaysFalseForRepeatingTypes()}.</li>
 * </ul>
 *
 * <h2>Known-bug tests</h2>
 * <p>{@link KnownBugs} documents unfixed production issues. All cases are {@code @Disabled}
 * until fixed — remove {@code @Disabled} on the nested class (or individual methods) to verify.
 * Filter with {@code @Tag("known-bug")} when enabling selectively in CI.
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
         arguments("EVERY_DAY weekdayOnly skips weekend and times before curr",
            (RetryTimeCase) () -> {
               TimeCondition condition = TimeCondition.at(9, 0, 0);
               condition.setWeekdayOnly(true);
               long curr = nextDayOfWeekAt(Calendar.SATURDAY, 12, 0, 0);
               assertRetryTimeEquals(expectedWeekdayOnlyRetry(9, 0, 0, curr),
                  condition.getRetryTime(curr, 0));
            }),
         arguments("EVERY_WEEK returns -1 when no weekday is selected",
            (RetryTimeCase) () -> {
               TimeCondition condition = TimeCondition.atDaysOfWeek(new int[] {}, 20, 30, 30);
               assertEquals(-1, condition.getRetryTime(todayAt(20, 35, 30), 0));
            }),
         arguments("EVERY_WEEK finds next selected weekday at scheduled time",
            (RetryTimeCase) () -> {
               TimeCondition condition = TimeCondition.atDaysOfWeek(
                  new int[] {Calendar.SUNDAY, Calendar.MONDAY}, 20, 30, 30);
               condition.setInterval(1);
               long curr = todayAt(20, 35, 30);
               assertRetryTimeEquals(expectedEveryWeekRetry(condition, curr, 0),
                  condition.getRetryTime(curr, 0));
            }),
         arguments("EVERY_WEEK interval uses lastRun as anchor when greater than 1",
            (RetryTimeCase) () -> {
               TimeCondition condition = TimeCondition.atDaysOfWeek(
                  new int[] {Calendar.MONDAY}, 8, 0, 0);
               condition.setInterval(2);
               long lastRun = todayAt(8, 0, 0, -14);
               long curr = todayAt(9, 0, 0);
               assertRetryTimeEquals(expectedEveryWeekRetry(condition, curr, lastRun),
                  condition.getRetryTime(curr, lastRun));
            }),
         arguments("EVERY_HOUR returns -1 when no weekday is selected",
            (RetryTimeCase) () -> {
               TimeCondition condition = TimeCondition.atHours(new int[] {}, 20, 30, 30);
               assertEquals(-1, condition.getRetryTime(todayAt(20, 35, 30), 0));
            }),
         arguments("EVERY_HOUR finds next tick inside the hourly window",
            (RetryTimeCase) () -> {
               TimeCondition condition = TimeCondition.atHours(new int[] {todayDayOfWeek()}, 8, 0, 0);
               condition.setHourlyInterval(2f);
               condition.setHourEnd(14);
               condition.setMinuteEnd(0);
               condition.setSecondEnd(0);
               long curr = todayAt(9, 30, 0);
               assertRetryTimeEquals(expectedEveryHourRetry(condition, curr, 0),
                  condition.getRetryTime(curr, 0));
            }),
         arguments("EVERY_MONTH returns -1 when no month is selected",
            (RetryTimeCase) () -> {
               TimeCondition condition = TimeCondition.atDayOfMonth(5, 20, 30, 30);
               condition.setMonthsOfYear(new int[] {});
               assertEquals(-1, condition.getRetryTime(todayAt(20, 35, 30), 0));
            }),
         arguments("EVERY_MONTH finds next nth weekday of selected month",
            (RetryTimeCase) () -> {
               int month = Calendar.getInstance().get(Calendar.MONTH);
               TimeCondition condition = TimeCondition.atDayOfMonth(1, 20, 30, 30);
               condition.setMonthsOfYear(new int[] {month});
               condition.setWeekOfMonth(3);
               condition.setDayOfWeek(Calendar.TUESDAY);
               long curr = todayAt(21, 0, 0);
               assertRetryTimeEquals(expectedEveryMonthRetry(condition, curr, 0),
                  condition.getRetryTime(curr, 0));
            }),
         arguments("EVERY_MONTH finds next fixed day of selected month",
            (RetryTimeCase) () -> {
               int month = Calendar.getInstance().get(Calendar.MONTH);
               TimeCondition condition = TimeCondition.atDayOfMonth(15, 9, 0, 0);
               condition.setMonthsOfYear(new int[] {month});
               long curr = todayAt(10, 0, 0);
               assertRetryTimeEquals(expectedEveryMonthByDayRetry(condition, curr, 0),
                  condition.getRetryTime(curr, 0));
            })
      );
   }

   private static long expectedWeekdayOnlyRetry(int hour, int minute, int second, long curr) {
      Calendar expected = calendarWithScheduledTime(hour, minute, second);

      while(expected.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
         expected.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY ||
         expected.getTimeInMillis() < curr)
      {
         expected.add(Calendar.DATE, 1);
      }

      return expected.getTimeInMillis();
   }

   private static long expectedEveryWeekRetry(TimeCondition condition, long curr, long lastRun) {
      Calendar cal1 = Calendar.getInstance(condition.getTimeZone());

      if(condition.getInterval() > 1 && lastRun > 0) {
         cal1.setTimeInMillis(lastRun);
      }

      cal1.set(Calendar.HOUR_OF_DAY, condition.getHour());
      cal1.set(Calendar.MINUTE, condition.getMinute());
      cal1.set(Calendar.SECOND, condition.getSecond());

      int[] daysOfWeek = condition.getDaysOfWeek();
      boolean satisfied = false;

      for(int day = cal1.get(Calendar.DAY_OF_WEEK); day < 8; day++) {
         cal1.set(Calendar.DAY_OF_WEEK, day);

         if(containsDay(daysOfWeek, day) && cal1.getTimeInMillis() >= curr) {
            satisfied = true;
            break;
         }
      }

      if(!satisfied) {
         if(condition.getInterval() > 1) {
            cal1.add(Calendar.DATE, (condition.getInterval() - 1) * 7);
         }

         while(containsDay(daysOfWeek, cal1.get(Calendar.DAY_OF_WEEK)) &&
            cal1.get(Calendar.DAY_OF_WEEK) != daysOfWeek[0])
         {
            cal1.add(Calendar.DATE, 1);
         }

         while(!containsDay(daysOfWeek, cal1.get(Calendar.DAY_OF_WEEK)) ||
            cal1.getTimeInMillis() < curr)
         {
            cal1.add(Calendar.DATE, 1);
         }
      }

      return cal1.getTimeInMillis();
   }

   private static long expectedEveryHourRetry(TimeCondition condition, long curr, long lastRun) {
      Calendar cal1 = Calendar.getInstance(condition.getTimeZone());

      if(condition.getInterval() > 1 && lastRun > 0) {
         cal1.setTimeInMillis(lastRun);
      }

      cal1.set(Calendar.HOUR_OF_DAY, condition.getHour());
      cal1.set(Calendar.MINUTE, condition.getMinute());
      cal1.set(Calendar.SECOND, condition.getSecond());

      int[] daysOfWeek = condition.getDaysOfWeek();
      boolean found = false;
      long start = cal1.getTimeInMillis();

      if(containsDay(daysOfWeek, cal1.get(Calendar.DAY_OF_WEEK))) {
         Calendar calEnd = Calendar.getInstance(condition.getTimeZone());
         calEnd.setTime(new Date(curr));
         calEnd.set(Calendar.HOUR_OF_DAY, condition.getHourEnd());
         calEnd.set(Calendar.MINUTE, condition.getMinuteEnd());
         calEnd.set(Calendar.SECOND, condition.getSecondEnd());
         long end = calEnd.getTimeInMillis();
         long interval = (long) (condition.getHourlyInterval() * 3600000);

         while(start < end && end > curr) {
            if(start >= curr) {
               found = true;
               break;
            }

            start += interval;
         }
      }

      if(found) {
         cal1.setTimeInMillis(start);
      }
      else {
         while(true) {
            cal1.add(Calendar.DATE, 1);

            if(containsDay(daysOfWeek, cal1.get(Calendar.DAY_OF_WEEK))) {
               break;
            }
         }
      }

      return cal1.getTimeInMillis();
   }

   private static long expectedEveryMonthRetry(TimeCondition condition, long curr, long lastRun) {
      Calendar cal1 = Calendar.getInstance(condition.getTimeZone());

      if(condition.getInterval() > 1 && lastRun > 0) {
         cal1.setTimeInMillis(lastRun);
      }

      cal1.set(Calendar.HOUR_OF_DAY, condition.getHour());
      cal1.set(Calendar.MINUTE, condition.getMinute());
      cal1.set(Calendar.SECOND, condition.getSecond());

      int maxDays = 2 * 365;

      while(!isNthSpecifiedDayOfMonth(condition, cal1) ||
         cal1.getTimeInMillis() < curr ||
         !containsDay(condition.getMonthsOfYear(), cal1.get(Calendar.MONTH)))
      {
         cal1.add(Calendar.DATE, 1);

         if(maxDays-- < 0) {
            break;
         }
      }

      return cal1.getTimeInMillis();
   }

   private static long expectedEveryMonthByDayRetry(TimeCondition condition, long curr, long lastRun) {
      Calendar cal1 = Calendar.getInstance(condition.getTimeZone());

      if(condition.getInterval() > 1 && lastRun > 0) {
         cal1.setTimeInMillis(lastRun);
      }

      cal1.set(Calendar.HOUR_OF_DAY, condition.getHour());
      cal1.set(Calendar.MINUTE, condition.getMinute());
      cal1.set(Calendar.SECOND, condition.getSecond());

      while(!containsDay(condition.getMonthsOfYear(), cal1.get(Calendar.MONTH)) ||
         cal1.getTimeInMillis() < curr ||
         condition.getDayOfMonth() < 0 &&
            !isNthLastDayOfMonth(-condition.getDayOfMonth(), cal1) ||
         condition.getDayOfMonth() > 0 &&
            !isNthFirstDayOfMonth(condition.getDayOfMonth(), cal1))
      {
         cal1.add(Calendar.DATE, 1);
      }

      return cal1.getTimeInMillis();
   }

   private static boolean isNthSpecifiedDayOfMonth(TimeCondition condition, Calendar calendar) {
      if(condition.getWeekOfMonth() <= 0) {
         return true;
      }

      int date = calendar.get(Calendar.DATE);
      boolean isInNthWeek = date <= 7 * condition.getWeekOfMonth() &&
         date > 7 * (condition.getWeekOfMonth() - 1);

      return isInNthWeek && calendar.get(Calendar.DAY_OF_WEEK) == condition.getDayOfWeek();
   }

   private static boolean isNthFirstDayOfMonth(int dayOfMonth, Calendar calendar) {
      return dayOfMonth == calendar.get(Calendar.DATE);
   }

   private static boolean isNthLastDayOfMonth(int n, Calendar calendar) {
      Calendar cal1 = Calendar.getInstance(calendar.getTimeZone());
      Calendar cal2 = Calendar.getInstance(calendar.getTimeZone());
      cal1.setTime(calendar.getTime());
      cal2.setTime(calendar.getTime());

      int month = calendar.get(Calendar.MONTH);
      cal1.add(Calendar.DATE, n - 1);
      int month1 = cal1.get(Calendar.MONTH);
      cal2.add(Calendar.DATE, n);
      int month2 = cal2.get(Calendar.MONTH);

      return (month1 == month && month2 != month) || (month1 != month && month2 == month);
   }

   private static boolean containsDay(int[] values, int value) {
      for(int candidate : values) {
         if(candidate == value) {
            return true;
         }
      }

      return false;
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

   private static long nextDayOfWeekAt(int dayOfWeek, int hour, int minute, int second) {
      Calendar cal = Calendar.getInstance();
      cal.set(Calendar.HOUR_OF_DAY, hour);
      cal.set(Calendar.MINUTE, minute);
      cal.set(Calendar.SECOND, second);
      cal.set(Calendar.MILLISECOND, 0);

      while(cal.get(Calendar.DAY_OF_WEEK) != dayOfWeek) {
         cal.add(Calendar.DATE, 1);
      }

      return cal.getTimeInMillis();
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
      void weekOfYearRetryIgnoresWeekOfYear() {
         TimeCondition condition = TimeCondition.atWeekOfYear(20, Calendar.MONDAY, 9, 0, 0);
         long curr = toFixedMillis("2025-05-01T12:00:00");
         Calendar retry = Calendar.getInstance();
         retry.setTimeInMillis(condition.getRetryTime(curr, 0));

         assertEquals(20, retry.get(Calendar.WEEK_OF_YEAR));
      }

      @Test
      @DisplayName("BUG-TC-4: parseXML rejects timeType=WEEK_OF_YEAR (5)")
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
