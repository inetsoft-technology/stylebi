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
package inetsoft.sree.schedule;

import org.junit.jupiter.api.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79056 -- {@code TimeCondition.getRetryTime} looped forever on a stored condition whose
 * values no day can satisfy (an hourly interval of 0 ms, a day of week outside 1-7, a day of
 * month that no selected month has, ...), and the scheduler start-up and every
 * {@code Scheduler.addTask} call ran it inline. The walks are now bounded and return -1.
 *
 * <p>Every call runs on a daemon thread that is abandoned after a timeout, so a regression
 * fails the test instead of hanging the build. The real {@code TimeCondition} is used; no Spring
 * context is needed because {@code getRetryTime} reaches no bean ({@code toString} does, so it
 * is never called here).
 */
@Tag("core")
class TimeConditionBoundsTest {
   private static final ZoneId UTC = ZoneId.of("UTC");
   private static final int[] ALL_DAYS = { 1, 2, 3, 4, 5, 6, 7 };
   private static final int[] ALL_MONTHS = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };
   private static final long TIMEOUT_SECONDS = 20;

   // Tuesday 2027-07-20 10:00 UTC, the "now" of the rows that need a fixed result
   private static final long CURR = ms("2027-07-20T10:00:00");

   private Locale savedLocale;
   private TimeZone savedTimeZone;

   @BeforeEach
   void setUp() {
      savedLocale = Locale.getDefault();
      savedTimeZone = TimeZone.getDefault();
      // the first day of the week of the default locale decides where the weekly walk starts
      Locale.setDefault(Locale.US);
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
   }

   @AfterEach
   void tearDown() {
      Locale.setDefault(savedLocale);
      TimeZone.setDefault(savedTimeZone);
   }

   // ---------------------------------------------------------------------------------------
   // hourly
   // ---------------------------------------------------------------------------------------

   @Test
   void hourlyIntervalBelowOneMillisecondReturnsNever() {
      // (long) (1e-7 h * 3600000) is 0, the step never advanced
      assertNever(hourly(0.0000001f, ALL_DAYS));
      assertNever(hourly(0.00000027f, ALL_DAYS));
   }

   @Test
   void hourlyIntervalNotANumberOrInfiniteReturnsNever() {
      assertNever(hourly(Float.NaN, ALL_DAYS));
      assertNever(hourly(Float.POSITIVE_INFINITY, ALL_DAYS));
      assertNever(hourly(Float.NEGATIVE_INFINITY, ALL_DAYS));
      assertNever(hourly(0f, ALL_DAYS));
      assertNever(hourly(-1f, ALL_DAYS));
   }

   @Test
   void hourlyHugeIntervalDoesNotOverflow() {
      // start += step overflowed to a negative value and the loop never ended. A step this long
      // is past the end of the window, so it behaves like 1e10 hours (which returned before).
      // (interval 1 reads the system clock, so "now" is the real one)
      long now = System.currentTimeMillis();
      long huge = call(hourly(1e30f, ALL_DAYS), now, 0);
      long large = call(hourly(1e10f, ALL_DAYS), now, 0);
      assertTrue(huge >= now);
      // (the result keeps the milliseconds of the clock, which moved between the two calls)
      assertTrue(Math.abs(large - huge) < 1000, huge + " vs " + large);
   }

   @Test
   void hourlyWithoutAValidDayOfWeekReturnsNever() {
      assertNever(hourly(1f, new int[] { 0 }));
      assertNever(hourly(1f, new int[] { 9 }));
      assertNever(hourly(1f, new int[] { 0, 8, -1 }));
      assertNever(hourly(1f, new int[0]));
   }

   @Test
   void hourlyControlsStillReturnTheSameTime() {
      // 0.5 h grid from 09:00, window 09:00-17:00, now 10:10 -> 10:30
      TimeCondition c = hourly(0.5f, ALL_DAYS);
      c.setInterval(2);
      assertEquals(ms("2027-07-20T10:30:00"), call(c, ms("2027-07-20T10:10:00"), ms("2027-07-20T10:10:00")));

      // 0.001 h is a 3.6 s step; the grid point after 10:10:00 is 09:00:00 + 1167 * 3.6 s
      c = hourly(0.001f, ALL_DAYS);
      c.setInterval(2);
      long step = (long) (0.001f * 3600000);
      long start = ms("2027-07-20T09:00:00");
      long expected = start + step * ((ms("2027-07-20T10:10:00") - start + step - 1) / step);
      assertEquals(expected, call(c, ms("2027-07-20T10:10:00"), ms("2027-07-20T10:10:00")));

      // {0,3}: the invalid element is ignored. 2027-07-20 is a Tuesday (3); the window of that
      // day is over at 20:00, so the next selected day is the Tuesday after, 07-27 09:00
      c = hourly(1f, new int[] { 0, 3 });
      c.setInterval(2);
      assertEquals(ms("2027-07-27T09:00:00"),
                   call(c, ms("2027-07-20T20:00:00"), ms("2027-07-20T20:00:00")));
   }

   // ---------------------------------------------------------------------------------------
   // weekly
   // ---------------------------------------------------------------------------------------

   @Test
   void weeklyWithoutAValidDayOfWeekReturnsNever() {
      assertNever(weekly(1, new int[] { 0 }));
      assertNever(weekly(1, new int[] { 9 }));
      assertNever(weekly(2, new int[] { 0, 8 }));
      assertNever(weekly(1, new int[0]));
   }

   @Test
   void weeklyIntervalThatOverflowsTheSkipReturnsNever() {
      // Calendar.add takes an int; (interval - 1) * 7 wrapped around in int arithmetic
      assertNever(weekly(Integer.MAX_VALUE, new int[] { 2 }));
      assertNever(weekly(620000000, new int[] { 2 }));
      assertNever(weekly(600000001, new int[] { 2 }));
      // the last interval whose skip (in days) still fits an int
      assertNever(weekly(306783380, new int[] { 2 }));
      assertTrue(call(weekly(306783379, new int[] { 2 }), CURR, CURR) > CURR);
   }

   @Test
   void weeklyEveryDaySelectedWithAnOutOfRangeFirstDayStillReturns() {
      // the "skip to days_of_week[0]" loop never ended when all of 1-7 were selected and
      // days_of_week[0] was not one of them; every day matches, so the next day is the answer
      for(int first : new int[] { 0, 8, -3 }) {
         int[] days = { first, 1, 2, 3, 4, 5, 6, 7 };

         for(int interval : new int[] { 1, 2 }) {
            long next = call(weekly(interval, days), CURR + 10 * 86400000L, CURR);
            assertTrue(next >= CURR + 10 * 86400000L, "first=" + first + " interval=" + interval);
            assertTrue(next < CURR + 10 * 86400000L + 3 * 86400000L,
                       "first=" + first + " interval=" + interval);
         }
      }
   }

   @Test
   void weeklyControlsStillReturnTheSameTime() {
      // Monday only, every 2 weeks, now Tuesday 2027-07-20 10:00. A stale lastRun must not turn
      // into "never": the walk is limited by the days from the seed to now, not by 8 days.
      long expected = ms("2027-07-26T09:00:00");
      long[] lastRuns = {
         CURR - 28 * 86400000L,                  // 4 weeks
         CURR - 77 * 86400000L,                  // 11 weeks
         CURR - 365 * 86400000L,                 // 1 year
         ms("1971-07-19T10:00:00"),              // 56 years
         ms("1970-01-05T10:00:00")               // about 20,600 days
      };

      for(long lastRun : lastRuns) {
         assertEquals(expected, call(weekly(2, new int[] { 2 }), CURR, lastRun),
                      "lastRun " + Instant.ofEpochMilli(lastRun));
      }

      // (a Saturday or Sunday seed is the week logic of Bug #79053, not covered here)
      assertTrue(call(weekly(3, new int[] { 2 }), CURR, CURR - 365 * 86400000L) >= CURR);

      // an invalid element is ignored, as before
      long mixed = call(weekly(2, new int[] { 0, 4 }), CURR, CURR);
      assertEquals(call(weekly(2, new int[] { 4, 0 }), CURR, CURR), mixed);
      assertTrue(mixed >= CURR);
      assertEquals(4, dayOfWeek(mixed));

      // interval 1 reads the clock: the next Monday is 1-7 days after "now" in real time
      long now = System.currentTimeMillis();
      long monday = call(weekly(1, new int[] { 2 }), now, now);
      assertTrue(monday >= now);
      assertEquals(DayOfWeek.MONDAY, Instant.ofEpochMilli(monday).atZone(UTC).getDayOfWeek());
      assertTrue(monday - now <= 8 * 86400000L);
   }

   // ---------------------------------------------------------------------------------------
   // monthly
   // ---------------------------------------------------------------------------------------

   @Test
   void monthlyWithoutAValidMonthReturnsNever() {
      assertNever(monthly(1, new int[] { 12 }));
      assertNever(monthly(1, new int[] { -1 }));
      assertNever(monthly(1, new int[] { -1, 12, 13 }));
      assertNever(monthly(1, new int[0]));
   }

   @Test
   void monthlyDayThatNoSelectedMonthHasReturnsNever() {
      int[] feb = { 1 };
      assertNever(monthly(30, feb));
      assertNever(monthly(31, feb));
      assertNever(monthly(40, feb));
      assertNever(monthly(-40, feb));
      assertNever(monthly(-30, feb));
      assertNever(monthly(31, new int[] { 3 }));              // April
      assertNever(monthly(31, new int[] { 1, 3, 5, 8, 10 })); // Feb, Apr, Jun, Sep, Nov
      assertNever(monthly(32, ALL_MONTHS));
      assertNever(monthly(-32, ALL_MONTHS));
      assertNever(monthly(-40, ALL_MONTHS));
      assertNever(monthly(100, ALL_MONTHS));
      assertNever(monthly(Integer.MAX_VALUE, ALL_MONTHS));
      assertNever(monthly(Integer.MIN_VALUE, ALL_MONTHS));
      assertNever(monthly(Integer.MIN_VALUE, new int[] { 1 }));
   }

   @Test
   void monthlySixthWeekReturnsNever() {
      // no month has a 6th week; it used to return a time about 2 years out that does not
      // satisfy the rule
      assertNever(nthWeekday(6, Calendar.FRIDAY, ALL_MONTHS));
      assertNever(nthWeekday(9, Calendar.FRIDAY, ALL_MONTHS));
      // a weekday that does not exist
      assertNever(nthWeekday(1, 0, ALL_MONTHS));
      assertNever(nthWeekday(1, 9, ALL_MONTHS));
   }

   @Test
   void monthlyControlsStillReturnTheSameTime() {
      // Jan 31 only
      long next = call(monthly(31, new int[] { 0 }), CURR, CURR);
      assertEquals(ms("2028-01-31T09:00:00"), next);

      // day 31, all months: July has 31 days
      assertEquals(ms("2027-07-31T09:00:00"), call(monthly(31, ALL_MONTHS), CURR, CURR));

      // day 0 is the editor default: every day
      assertEquals(ms("2027-07-21T09:00:00"), call(monthly(0, ALL_MONTHS), CURR, CURR));

      // last day, day before last day
      assertEquals(ms("2027-07-31T09:00:00"), call(monthly(-1, ALL_MONTHS), CURR, CURR));
      assertEquals(ms("2027-07-30T09:00:00"), call(monthly(-2, ALL_MONTHS), CURR, CURR));

      // an invalid month is ignored
      assertEquals(ms("2027-08-15T09:00:00"), call(monthly(15, new int[] { 7, 12 }), CURR, CURR));

      // day 15, a 2 years old lastRun does not turn into "never"
      long stale = ms("2025-06-01T10:00:00");
      assertEquals(ms("2027-08-15T09:00:00"), call(monthly(15, new int[] { 7 }), CURR, stale));
      assertEquals(ms("2027-08-15T09:00:00"), call(monthly(15, ALL_MONTHS), CURR + 20 * 86400000L, stale));

      // Feb 29 only, from just after the Feb 29 of 2096: the next one is 2104 (2100 is not a
      // leap year)
      assertEquals(ms("2104-02-29T09:00:00"),
                   call(monthly(29, new int[] { 1 }), ms("2096-03-01T00:00:00"), ms("2096-02-29T10:00:00")));
      assertEquals(ms("2028-02-29T09:00:00"),
                   call(monthly(29, new int[] { 1 }), ms("2027-08-01T00:00:00"), ms("2027-08-01T10:00:00")));

      // interval 1 reads the clock
      long now = System.currentTimeMillis();
      long first = call(monthly(1, ALL_MONTHS), now, now);
      assertTrue(first >= now);
      assertEquals(1, Instant.ofEpochMilli(first).atZone(UTC).getDayOfMonth());
   }

   @Test
   void monthlyLongestLegitimateGapStillFits() {
      // The 5th <weekday> of February is Feb 29. When a skipped leap year (2100) lies between two
      // of them, the gap is 14,609 days, the longest a valid rule can leave. The walk limit (40
      // years of days) leaves 31 days of margin; this fails if somebody trims it.
      LocalDate before = null;
      LocalDate after = null;

      for(int year = 2072; year <= 2099 && before == null; year++) {
         if(!Year.isLeap(year)) {
            continue;
         }

         LocalDate d = LocalDate.of(year, 2, 29);

         for(int y2 = year + 1; y2 < year + 45; y2++) {
            if(Year.isLeap(y2) && LocalDate.of(y2, 2, 29).getDayOfWeek() == d.getDayOfWeek()) {
               if(java.time.temporal.ChronoUnit.DAYS.between(d, LocalDate.of(y2, 2, 29)) == 14609) {
                  before = d;
                  after = LocalDate.of(y2, 2, 29);
               }

               break;
            }
         }
      }

      assertNotNull(before, "no 14,609 day gap between two 5th weekdays of February found");
      int weekday = before.getDayOfWeek().getValue() % 7 + 1; // Calendar.SUNDAY is 1
      long lastRun = before.atTime(10, 0).atZone(UTC).toInstant().toEpochMilli();
      long curr = before.plusDays(1).atStartOfDay(UTC).toInstant().toEpochMilli();

      assertEquals(after.atTime(9, 0).atZone(UTC).toInstant().toEpochMilli(),
                   call(nthWeekday(5, weekday, new int[] { 1 }), curr, lastRun));
   }

   @Test
   void monthlyNthWeekdayControlsStillReturnTheSameTime() {
      // 2nd Tuesday of every month after Tuesday 2027-07-20 -> August 10
      assertEquals(ms("2027-08-10T09:00:00"),
                   call(nthWeekday(2, Calendar.TUESDAY, ALL_MONTHS), CURR, CURR));
      // 5th Friday of any month: Friday 2027-07-30 is the 5th one of July
      assertEquals(ms("2027-07-30T09:00:00"),
                   call(nthWeekday(5, Calendar.FRIDAY, ALL_MONTHS), CURR, CURR));
   }

   // ---------------------------------------------------------------------------------------
   // untouched branches
   // ---------------------------------------------------------------------------------------

   @Test
   void otherTypesAreNotChanged() {
      // AT in the past is -1, in the future its time
      TimeCondition at = TimeCondition.at(new Date(CURR - 1000));
      assertEquals(-1, call(at, CURR, CURR));
      at = TimeCondition.at(new Date(CURR + 1000));
      assertEquals(CURR + 1000, call(at, CURR, CURR));

      // every day / every 2 days
      TimeCondition day = TimeCondition.at(9, 0, 0);
      day.setTimeZone(TimeZone.getTimeZone(UTC));
      day.setInterval(2);
      assertEquals(ms("2027-07-22T09:00:00"), call(day, CURR, CURR));

      // week of year out of range is -1 (it was already capped)
      TimeCondition woy = new TimeCondition();
      woy.setType(TimeCondition.WEEK_OF_YEAR);
      woy.setTimeZone(TimeZone.getTimeZone(UTC));
      woy.setHour(9);
      woy.setMinute(0);
      woy.setSecond(0);
      woy.setWeekOfYear(99);
      woy.setDayOfWeek(2);
      assertEquals(-1, call(woy, CURR, CURR));
   }

   // ---------------------------------------------------------------------------------------
   // helpers
   // ---------------------------------------------------------------------------------------

   private static TimeCondition base(int type) {
      TimeCondition c = new TimeCondition();
      c.setType(type);
      c.setTimeZone(TimeZone.getTimeZone(UTC));
      c.setHour(9);
      c.setMinute(0);
      c.setSecond(0);
      c.setHourEnd(17);
      c.setMinuteEnd(0);
      c.setSecondEnd(0);
      c.setInterval(1);
      return c;
   }

   private static TimeCondition hourly(float hours, int[] days) {
      TimeCondition c = base(TimeCondition.EVERY_HOUR);
      c.setHourlyInterval(hours);
      c.setDaysOfWeek(days);
      return c;
   }

   private static TimeCondition weekly(int interval, int[] days) {
      TimeCondition c = base(TimeCondition.EVERY_WEEK);
      c.setInterval(interval);
      c.setDaysOfWeek(days);
      return c;
   }

   /** Day of month rule with interval 2, so the date the walk starts from is lastRun. */
   private static TimeCondition monthly(int dayOfMonth, int[] months) {
      TimeCondition c = base(TimeCondition.EVERY_MONTH);
      c.setInterval(2);
      c.setDayOfMonth(dayOfMonth);
      c.setMonthsOfYear(months);
      return c;
   }

   /** Nth weekday rule with interval 2, so the date the walk starts from is lastRun. */
   private static TimeCondition nthWeekday(int week, int dayOfWeek, int[] months) {
      TimeCondition c = base(TimeCondition.EVERY_MONTH);
      c.setInterval(2);
      c.setWeekOfMonth(week);
      c.setDayOfWeek(dayOfWeek);
      c.setMonthsOfYear(months);
      return c;
   }

   private static void assertNever(TimeCondition condition) {
      assertEquals(-1, call(condition, System.currentTimeMillis(), 0));
   }

   /**
    * Calls the real getRetryTime on a daemon thread and fails if it does not return, instead of
    * hanging the test run.
    */
   private static long call(TimeCondition condition, long curr, long lastRun) {
      FutureTask<Long> task = new FutureTask<>(() -> condition.getRetryTime(curr, lastRun));
      Thread thread = new Thread(task, "getRetryTime-79056");
      thread.setDaemon(true);
      thread.start();

      try {
         return task.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      }
      catch(TimeoutException e) {
         fail("getRetryTime did not return in " + TIMEOUT_SECONDS + " s: type=" +
              condition.getType() + ", days=" + Arrays.toString(condition.getDaysOfWeek()) +
              ", months=" + Arrays.toString(condition.getMonthsOfYear()) +
              ", dayOfMonth=" + condition.getDayOfMonth() +
              ", hourlyInterval=" + condition.getHourlyInterval() +
              ", interval=" + condition.getInterval());
         return 0; // not reached
      }
      catch(ExecutionException | InterruptedException e) {
         throw new AssertionError(e);
      }
      finally {
         // only matters after a timeout; a hung call cannot be stopped, it is a daemon
         thread.interrupt();
      }
   }

   private static int dayOfWeek(long time) {
      return Instant.ofEpochMilli(time).atZone(UTC).getDayOfWeek().getValue() % 7 + 1;
   }

   private static long ms(String isoLocal) {
      return LocalDateTime.parse(isoLocal).atZone(UTC).toInstant().toEpochMilli();
   }
}
