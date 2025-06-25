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

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.time.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;

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

      assertTrue(timeCondition.toString().contains("TimeCondition: at 2025-03-01"));
      assertEquals(TimeCondition.AT, timeCondition.getType());
      assertEquals(testDate, timeCondition.getDate());

      // Invalid input: null date
      Exception exception = assertThrows(NullPointerException.class, () -> {
         timeCondition = TimeCondition.at(null);
         timeCondition.toString();
      });
      assertTrue(exception.getMessage().contains("Cannot invoke \"java.util.Date.getTime()\" because \"this.time\" is null"));
   }

   @Test
   void testAtHMS() {
      // check valid input
      timeCondition = TimeCondition.at(10,35,59);
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

      //check boundary values
      timeCondition = TimeCondition.at(0, 0, 0);
      assertTrue(timeCondition.toString().contains("TimeCondition: 00:00:00"));
      timeCondition = TimeCondition.at(23, 59, 59);
      assertTrue(timeCondition.toString().contains("TimeCondition: 23:59:59"));

      //check invalid values, <0 treat as 0, > 60 will be auto calculate
      timeCondition = TimeCondition.at(-1, 60, 63);
      assertTrue(timeCondition.toString().contains("TimeCondition: 00:01:03"));

      timeCondition = TimeCondition.at(25, 61, 125);
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
   void testGetRetryTimeOnAt() {
      Date testDate = toDate("2025-03-01T10:15:23");
      TimeCondition timeCondition = TimeCondition.at(testDate);
      long currentTime = testDate.getTime();

      long retryTime = timeCondition.getRetryTime(currentTime, 0);
      assertEquals(currentTime, retryTime);

      retryTime = timeCondition.getRetryTime( toDate("2025-03-01T10:20:10").getTime(), 0);
      assertEquals(-1,  retryTime);  //execute time < current time
   }

   @Test
   void testGetRetryTimeOnEvery_Day() {
      timeCondition = TimeCondition.at(20,30,30);
      long currentTime = setCustomTimeInMillis(20,35,30);
      long retryTime = timeCondition.getRetryTime(currentTime, 0);

      assertEquals(-1,  retryTime);  //interval <=0, return -1

      // check non-weekdayonly and interval is 2
      timeCondition.setInterval(2);
      retryTime = timeCondition.getRetryTime(currentTime, 0);
      assertTrue(retryTime > currentTime);  //interval > 1
      //assertEquals(2, getDaysDifference(retryTime, currentTime));

      // check weekday only is true
      timeCondition.setWeekdayOnly(true);
      retryTime = timeCondition.getRetryTime(currentTime, 0);
      assertTrue(retryTime > currentTime);
  }

   @Test
   void testGetRetryTimeOnEvery_Week() {
      timeCondition = TimeCondition.atDaysOfWeek(new int[] {}, 20,30,30);
      long currentTime = setCustomTimeInMillis(20,35,30);
      long retryTime = timeCondition.getRetryTime(currentTime, 0);
      assertEquals(-1, retryTime);  // unselect any weeks.

      timeCondition = TimeCondition.atDaysOfWeek(new int[] {Calendar.SUNDAY, Calendar.MONDAY}, 20,30,30);
      timeCondition.setInterval(2);
      retryTime = timeCondition.getRetryTime(currentTime, 0);
      assertTrue(retryTime > currentTime);
   }

   @Test
   void testGetRetryTimeOnEvery_Hour() {
      timeCondition = TimeCondition.atHours(new int[] {}, 20,30,30);
      long currentTime = setCustomTimeInMillis(20,35,30);
      long retryTime = timeCondition.getRetryTime(currentTime, 0);
      assertEquals(-1, retryTime);  // unselect any weeks.

      timeCondition = TimeCondition.atHours(new int[] {Calendar.SUNDAY, Calendar.MONDAY, Calendar.THURSDAY}, 20,30,30);
      timeCondition.setInterval(2);
      retryTime = timeCondition.getRetryTime(currentTime, 0);
      assertTrue(retryTime > currentTime);
   }

   @Test
   void testGetRetryTimeOnEvery_Month() {
      timeCondition = TimeCondition.atDayOfMonth(5, 20,30,30);
      timeCondition.setMonthsOfYear(new int[]{});
      long currentTime = setCustomTimeInMillis(20,35,30);
      long retryTime = timeCondition.getRetryTime(currentTime, 0);
      assertEquals(-1, retryTime);  // unselect any weeks.

      timeCondition = TimeCondition.atDayOfMonth(6, 20,30,30);
      timeCondition.setMonthsOfYear(new int[]{Calendar.MARCH});
      timeCondition.setWeekOfMonth(3);
      retryTime = timeCondition.getRetryTime(currentTime, 0);
      assertTrue(retryTime > currentTime);
   }

   private Date toDate(String localDateTime) {
      return Date.from(LocalDateTime.parse(localDateTime)
                                    .atZone(ZoneId.systemDefault())  //  ZoneId.systemDefault()
                                    .toInstant());
   }

   private long setCustomTimeInMillis(int hour, int minute, int second) {
      LocalDateTime now = LocalDateTime.now();
      LocalDateTime customTime = now.withHour(hour).withMinute(minute).withSecond(second);

      return customTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
   }
}
