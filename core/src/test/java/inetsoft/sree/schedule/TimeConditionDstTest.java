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

import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Bug #79052 -- {@code TimeCondition.getRetryTime} walked the dates with {@code Calendar.add} and
 * set the wall time on the same calendar, so a daylight saving change left the hour stuck for the
 * rest of the walk (02:30 on the day after a spring-forward gap ran at 03:30, then 03:30 on every
 * later day), a repeated wall time ran twice, and the hard-coded one hour broke the 30-minute and
 * midnight changes.
 *
 * <p>Every case chains the fire times as the Quartz trigger does: the clock is the previous fire
 * time plus a few milliseconds, {@code curr} is the previous fire time plus 30 seconds and
 * {@code lastRun} is the previous fire time. The clock is the only input of an every-day,
 * every-week (N = 1), every-hour and every-month condition, so it is stubbed through
 * {@code Calendar.getInstance(TimeZone)} as {@link TimeConditionTest} does.
 *
 * <p>The instant a wall time runs at on a change day is today's choice (a skipped time runs later
 * by the length of the gap, a repeated time at the later of its two occurrences), see the
 * expected values in the "Q1/Q2" cases.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TimeConditionDstTest {
   private static final int[] ALL_MONTHS = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };
   private static final int[] ALL_DAYS = { 1, 2, 3, 4, 5, 6, 7 };

   private Locale savedLocale;
   private TimeZone savedTimeZone;

   @BeforeEach
   void setUp() {
      savedLocale = Locale.getDefault();
      savedTimeZone = TimeZone.getDefault();
      Locale.setDefault(Locale.US);
      // not the zone of any condition below: the walk must use the zone of the condition
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
   }

   @AfterEach
   void tearDown() {
      Locale.setDefault(savedLocale);
      TimeZone.setDefault(savedTimeZone);
   }

   // ---------------------------------------------------------------------------------------
   // helpers
   // ---------------------------------------------------------------------------------------

   private static long ms(String zone, String localDateTime) {
      return LocalDateTime.parse(localDateTime).atZone(ZoneId.of(zone)).toInstant().toEpochMilli();
   }

   private static MockedStatic<Calendar> clock(long epoch) {
      MockedStatic<Calendar> mocked = mockStatic(Calendar.class, Mockito.CALLS_REAL_METHODS);
      mocked.when(() -> Calendar.getInstance(any(TimeZone.class))).thenAnswer(inv -> {
         GregorianCalendar cal = new GregorianCalendar((TimeZone) inv.getArgument(0));
         cal.setTimeInMillis(epoch);
         return cal;
      });
      return mocked;
   }

   private static long retry(TimeCondition condition, long clock, long curr, long lastRun) {
      try(MockedStatic<Calendar> ignored = clock(clock)) {
         return condition.getRetryTime(curr, lastRun);
      }
   }

   private static TimeCondition in(TimeCondition condition, String zone) {
      condition.setTimeZone(TimeZone.getTimeZone(zone));
      return condition;
   }

   /**
    * The next {@code count} fire times as the scheduler computes them, each as the offset date
    * time in the zone of the condition (for example 2028-03-12T03:30-04:00).
    */
   private static List<String> chain(TimeCondition condition, String zone, String startLocal,
                                     int count)
   {
      long start = ms(zone, startLocal);
      List<String> fires = new ArrayList<>();
      long fire = retry(condition, start, start, 0);

      for(int i = 0; i < count; i++) {
         assertTrue(fire > 0, "no fire time after " + fires);
         fires.add(text(zone, fire));
         // the trigger fires a few milliseconds late: the clock is the fire time plus the lag
         fire = retry(condition, fire + 7, fire + 30000, fire);
      }

      return fires;
   }

   private static String text(String zone, long millis) {
      return Instant.ofEpochMilli(millis).atZone(ZoneId.of(zone)).toOffsetDateTime().toString();
   }

   private static void assertChain(List<String> expected, List<String> actual) {
      assertEquals(String.join("\n", expected), String.join("\n", actual));
   }

   private static void assertOncePerLocalDate(List<String> fires) {
      List<String> dates = fires.stream().map(f -> f.substring(0, 10)).collect(Collectors.toList());
      assertEquals(new LinkedHashSet<>(dates).size(), dates.size(), "a date fired twice: " + fires);
   }

   private static TimeCondition daily(int h, int m, int s) {
      TimeCondition condition = TimeCondition.at(h, m, s);
      condition.setInterval(1);
      return condition;
   }

   private static TimeCondition monthly(int day, int h, int m, int s) {
      TimeCondition condition = TimeCondition.atDayOfMonth(day, h, m, s);
      condition.setMonthsOfYear(ALL_MONTHS);
      return condition;
   }

   // ---------------------------------------------------------------------------------------
   // every day, the reporter's cases
   // ---------------------------------------------------------------------------------------

   @Test
   void dailyAfterASpringForwardGapRunsAtTheWallTimeAgain() {
      // 2028-03-12 02:30 does not exist in New York. Before the fix the hour stayed stuck:
      // 03-12 03:30, then 03-13 03:30, 03-14 03:30 ...
      assertChain(List.of(
         "2028-03-11T02:30-05:00",
         "2028-03-12T03:30-04:00", // Q1: skipped, later by the gap
         "2028-03-13T02:30-04:00",
         "2028-03-14T02:30-04:00"),
         chain(in(daily(2, 30, 0), "America/New_York"), "America/New_York",
               "2028-03-10T12:00:00", 4));
   }

   @Test
   void dailyAtARepeatedWallTimeRunsOnceOnTheFallBackDay() {
      // 2027-11-07 01:30 happens twice in New York; before the fix it ran at both
      List<String> fires = chain(in(daily(1, 30, 0), "America/New_York"), "America/New_York",
                                 "2027-11-05T12:00:00", 4);
      assertOncePerLocalDate(fires);
      // Q2 (provisional): the later occurrence, as Calendar resolved it
      assertChain(List.of(
         "2027-11-06T01:30-04:00",
         "2027-11-07T01:30-05:00",
         "2027-11-08T01:30-05:00",
         "2027-11-09T01:30-05:00"), fires);
   }

   @Test
   void dailyInLondonAcrossTheSpringForwardGap() {
      assertChain(List.of(
         "2028-03-25T01:30Z",
         "2028-03-26T02:30+01:00", // 01:30 does not exist
         "2028-03-27T01:30+01:00",
         "2028-03-28T01:30+01:00"),
         chain(in(daily(1, 30, 0), "Europe/London"), "Europe/London", "2028-03-24T12:00:00", 4));
   }

   @Test
   void dailyInLondonAcrossTheFallBackDay() {
      List<String> fires = chain(in(daily(1, 30, 0), "Europe/London"), "Europe/London",
                                 "2028-10-27T12:00:00", 4);
      assertOncePerLocalDate(fires);
      assertChain(List.of(
         "2028-10-28T01:30+01:00",
         "2028-10-29T01:30Z",
         "2028-10-30T01:30Z",
         "2028-10-31T01:30Z"), fires);
   }

   @Test
   void dailyInLordHoweAcrossThe30MinuteGap() {
      // Lord Howe moves 02:00 to 02:30 on 2027-10-03: 02:15 is skipped, by 30 minutes, not 1 hour
      assertChain(List.of(
         "2027-10-02T02:15+10:30",
         "2027-10-03T02:45+11:00",
         "2027-10-04T02:15+11:00",
         "2027-10-05T02:15+11:00"),
         chain(in(daily(2, 15, 0), "Australia/Lord_Howe"), "Australia/Lord_Howe",
               "2027-10-01T12:00:00", 4));
   }

   @Test
   void dailyInLordHoweAcrossThe30MinuteOverlap() {
      // 01:30-02:00 on 2027-04-04 happens twice (+11:00, then +10:30)
      List<String> fires = chain(in(daily(1, 45, 0), "Australia/Lord_Howe"), "Australia/Lord_Howe",
                                 "2027-04-02T12:00:00", 4);
      assertOncePerLocalDate(fires);
      assertChain(List.of(
         "2027-04-03T01:45+11:00",
         "2027-04-04T01:45+10:30",
         "2027-04-05T01:45+10:30",
         "2027-04-06T01:45+10:30"), fires);
   }

   @Test
   void dailyInCairoAcrossTheMidnightGap() {
      // Cairo moves 00:00 to 01:00 on 2027-04-30: there is no midnight that day
      assertChain(List.of(
         "2027-04-29T00:30+02:00",
         "2027-04-30T01:30+03:00",
         "2027-05-01T00:30+03:00",
         "2027-05-02T00:30+03:00"),
         chain(in(daily(0, 30, 0), "Africa/Cairo"), "Africa/Cairo", "2027-04-28T12:00:00", 4));
      assertChain(List.of(
         "2027-04-29T00:00+02:00",
         "2027-04-30T01:00+03:00",
         "2027-05-01T00:00+03:00"),
         chain(in(daily(0, 0, 0), "Africa/Cairo"), "Africa/Cairo", "2027-04-28T12:00:00", 3));
   }

   @Test
   void dailyInCairoAcrossTheMidnightOverlap() {
      // Cairo moves 24:00 back to 23:00 on 2027-10-28: 23:30 happens twice
      List<String> fires = chain(in(daily(23, 30, 0), "Africa/Cairo"), "Africa/Cairo",
                                 "2027-10-26T12:00:00", 4);
      assertOncePerLocalDate(fires);
      assertEquals("2027-10-28T23:30+02:00", fires.get(2));
      assertEquals("2027-10-29T23:30+02:00", fires.get(3));
   }

   @Test
   void dailyEveryTwoDaysAfterAGapRunsAtTheWallTimeAgain() {
      // N > 1 starts from the date of lastRun in the zone of the condition
      TimeCondition condition = in(daily(2, 30, 0), "America/New_York");
      condition.setInterval(2);
      long lastRun = ms("America/New_York", "2028-03-10T02:30:00");
      long next = retry(condition, lastRun + 7, lastRun + 30000, lastRun);
      assertEquals("2028-03-12T03:30-04:00", text("America/New_York", next));
      next = retry(condition, next + 7, next + 30000, next);
      assertEquals("2028-03-14T02:30-04:00", text("America/New_York", next));
   }

   @Test
   void dailyEveryTwoDaysUsesTheDateOfLastRunInTheZoneOfTheCondition() {
      // 23:30 in New York on 03-10 is already 03-11 in UTC, the default zone of the JVM here
      TimeCondition condition = in(daily(2, 30, 0), "America/New_York");
      condition.setInterval(2);
      long lastRun = ms("America/New_York", "2028-03-10T23:30:00");
      long next = retry(condition, lastRun + 7, lastRun + 30000, lastRun);
      assertEquals("2028-03-12T03:30-04:00", text("America/New_York", next));
   }

   @Test
   void dailyEveryTwoDaysUsesTheDateOfLastRunInTheZoneOfTheConditionWithoutAGap() {
      // no change near: 23:30 on 07-10 in New York is 07-11 in UTC, the default zone of the JVM
      TimeCondition condition = in(daily(2, 30, 0), "America/New_York");
      condition.setInterval(2);
      long lastRun = ms("America/New_York", "2028-07-10T23:30:00");
      long next = retry(condition, lastRun + 7, lastRun + 30000, lastRun);
      assertEquals("2028-07-12T02:30-04:00", text("America/New_York", next));
   }

   @Test
   void weeklyWithDaysInClickOrderRunsOnEverySelectedDay() {
      // the portal editor stores the days in the order they were clicked: Fri, Sun, Wed, Sat, Mon.
      // The walk used to skip the selected days that follow a Saturday run (Sun and Mon).
      TimeCondition condition = in(
         TimeCondition.atDaysOfWeek(new int[] { 6, 1, 4, 7, 2 }, 9, 0, 0), "UTC");
      assertChain(List.of(
         "2027-07-18T09:00Z", "2027-07-19T09:00Z", "2027-07-21T09:00Z", "2027-07-23T09:00Z",
         "2027-07-24T09:00Z", "2027-07-25T09:00Z", "2027-07-26T09:00Z"),
         chain(condition, "UTC", "2027-07-17T12:00:00", 7));
   }

   @Test
   void hourlyFromMidnightDoesNotFireAgainWhenTheTriggerIsEarly() {
      // all days, 00:00-23:00 every hour. The trigger fires up to 2 ms early, while the clock is
      // still on the previous date; the next fire must not be the instant that just fired.
      TimeCondition condition = hourly("UTC", 0, 23, 1f);
      long fire = ms("UTC", "2027-07-21T00:00:00");

      for(long lag : new long[] { -2, -1, 0, 7 }) {
         assertEquals("2027-07-21T01:00Z",
                      text("UTC", retry(condition, fire + lag, fire + 30000, fire)), "lag " + lag);
      }

      // the same in a zone, on a day the clock changes
      condition = hourly("America/New_York", 0, 23, 1f);
      fire = ms("America/New_York", "2028-03-12T00:00:00");
      assertEquals("2028-03-12T01:00-05:00",
                   text("America/New_York", retry(condition, fire - 2, fire + 30000, fire)));
   }

   @Test
   void hourlyFromMidnightOnNonConsecutiveDaysEarlyTrigger() {
      // Tuesday only: the clock of the early trigger is on Monday, which is not selected
      TimeCondition condition = in(TimeCondition.atHours(new int[] { 3 }, 0, 0, 0), "UTC");
      condition.setHourEnd(23);
      condition.setHourlyInterval(1f);
      long fire = ms("UTC", "2027-07-20T00:00:00"); // Tuesday
      assertEquals("2027-07-20T01:00Z", text("UTC", retry(condition, fire - 2, fire + 30000, fire)));
      // after the last tick of Tuesday the next one is the next Tuesday
      fire = ms("UTC", "2027-07-20T22:00:00");
      assertEquals("2027-07-27T00:00Z", text("UTC", retry(condition, fire + 7, fire + 30000, fire)));
   }

   @Test
   void hourlyFromMidnightOnConsecutiveDaysAfterTheLastTick() {
      // Mon-Wed 00:00-23:00 every hour: after Tuesday 22:00 the next tick is Wednesday 00:00
      TimeCondition condition = in(TimeCondition.atHours(new int[] { 2, 3, 4 }, 0, 0, 0), "UTC");
      condition.setHourEnd(23);
      condition.setHourlyInterval(1f);
      long fire = ms("UTC", "2027-07-20T22:00:00");
      assertEquals("2027-07-21T00:00Z", text("UTC", retry(condition, fire + 7, fire + 30000, fire)));
      // an early trigger of that Wednesday 00:00 fire gets 01:00
      fire = ms("UTC", "2027-07-21T00:00:00");
      assertEquals("2027-07-21T01:00Z", text("UTC", retry(condition, fire - 2, fire + 30000, fire)));
   }

   @Test
   void hourlyTriggerThatFiresAfterMidnightGetsTheNextWindowOfTheNewDate() {
      // Fri and Sat 10:45-21:45 every hour. The Friday 20:45 fire is delayed past midnight (a
      // blocked job): the window of Saturday is next, the whole Saturday must not be skipped.
      TimeCondition condition = in(TimeCondition.atHours(new int[] { 6, 7 }, 10, 45, 0), "UTC");
      condition.setHourEnd(21);
      condition.setMinuteEnd(45);
      condition.setHourlyInterval(1f);
      long fire = ms("UTC", "2027-07-23T20:45:00"); // Friday
      long clock = ms("UTC", "2027-07-24T00:00:05");
      assertEquals("2027-07-24T10:45Z", text("UTC", retry(condition, clock, fire + 30000, fire)));
   }

   @Test
   void dailyWeekdaysOnlyAfterAGapRunsAtTheWallTime() {
      // Fri 03-10, Mon 03-13 (the day after the Sunday gap), Tue 03-14
      assertChain(List.of(
         "2028-03-10T02:30-05:00",
         "2028-03-13T02:30-04:00",
         "2028-03-14T02:30-04:00"),
         chain(weekdays(in(daily(2, 30, 0), "America/New_York")), "America/New_York",
               "2028-03-09T12:00:00", 3));
   }

   @Test
   void dailyWeekdaysOnlyAroundTheGapSundayRunsAtTheWallTime() {
      // 2027-03-14 is the gap Sunday in New York: Friday, then Monday and Tuesday exactly
      assertChain(List.of(
         "2027-03-12T02:30-05:00",
         "2027-03-15T02:30-04:00",
         "2027-03-16T02:30-04:00"),
         chain(weekdays(in(daily(2, 30, 0), "America/New_York")), "America/New_York",
               "2027-03-11T12:00:00", 3));
   }

   private static TimeCondition weekdays(TimeCondition condition) {
      condition.setWeekdayOnly(true);
      return condition;
   }

   @Test
   void dailyJustBeforeMidnightDoesNotFireAgainRightAway() {
      // The trigger fires up to 2 ms early, while the clock is still on the previous date. The
      // single step used to give today's 00:00:00 plus a day, 1 s after the fire, and the task ran
      // twice in a row.
      TimeCondition condition = in(daily(0, 0, 0), "UTC");
      long fire = ms("UTC", "2027-07-20T00:00:00") - 2;
      long next = retry(condition, fire, fire + 30000, fire);
      assertEquals("2027-07-21T00:00Z", text("UTC", next));
   }

   @Test
   void firesAtTheWallTimeWithZeroMilliseconds() {
      TimeCondition condition = in(daily(9, 0, 0), "UTC");
      long clock = ms("UTC", "2027-07-20T08:00:00") + 123;
      long next = retry(condition, clock, clock, 0);
      assertEquals(ms("UTC", "2027-07-20T09:00:00"), next);
      assertEquals(0, next % 1000);
   }

   // ---------------------------------------------------------------------------------------
   // every week
   // ---------------------------------------------------------------------------------------

   @Test
   void weeklyMondayWednesdayFridayAfterAGapRunsAtTheWallTime() {
      TimeCondition condition = in(
         TimeCondition.atDaysOfWeek(new int[] { 2, 4, 6 }, 2, 30, 0), "America/New_York");
      // Fri 03-10, then Monday 03-13 the day after the gap (it used to run at 01:30 and 02:30)
      assertChain(List.of(
         "2028-03-10T02:30-05:00",
         "2028-03-13T02:30-04:00",
         "2028-03-15T02:30-04:00",
         "2028-03-17T02:30-04:00"),
         chain(condition, "America/New_York", "2028-03-09T12:00:00", 4));
   }

   @Test
   void weeklySundayOnTheGapDayRunsLaterOnce() {
      TimeCondition condition = in(
         TimeCondition.atDaysOfWeek(new int[] { 1 }, 2, 30, 0), "America/New_York");
      assertChain(List.of(
         "2028-03-05T02:30-05:00",
         "2028-03-12T03:30-04:00",
         "2028-03-19T02:30-04:00"),
         chain(condition, "America/New_York", "2028-03-01T12:00:00", 3));
   }

   @Test
   void weeklySundayOnTheFallBackDayRunsOnce() {
      TimeCondition condition = in(
         TimeCondition.atDaysOfWeek(new int[] { 1 }, 1, 30, 0), "America/New_York");
      List<String> fires = chain(condition, "America/New_York", "2027-10-30T12:00:00", 3);
      assertOncePerLocalDate(fires);
      assertChain(List.of(
         "2027-10-31T01:30-04:00",
         "2027-11-07T01:30-05:00",
         "2027-11-14T01:30-05:00"), fires);
   }

   @Test
   void weeklyEveryTwoWeeksAfterAGapRunsAtTheWallTime() {
      TimeCondition condition = in(
         TimeCondition.atDaysOfWeek(new int[] { 2 }, 2, 30, 0), "America/New_York");
      condition.setInterval(2);
      long lastRun = ms("America/New_York", "2028-02-28T02:30:00");
      long next = retry(condition, lastRun + 7, lastRun + 30000, lastRun);
      assertEquals("2028-03-13T02:30-04:00", text("America/New_York", next));
      next = retry(condition, next + 7, next + 30000, next);
      assertEquals("2028-03-27T02:30-04:00", text("America/New_York", next));
   }

   @Test
   void weeklyInLordHoweOnTheGapAndOverlapSundays() {
      // the changes are on Sundays: 2027-10-03 02:00 -> 02:30, 2027-04-04 02:00 -> 01:30
      assertChain(List.of(
         "2027-09-26T02:15+10:30",
         "2027-10-03T02:45+11:00",
         "2027-10-10T02:15+11:00"),
         chain(in(TimeCondition.atDaysOfWeek(new int[] { 1 }, 2, 15, 0), "Australia/Lord_Howe"),
               "Australia/Lord_Howe", "2027-09-25T12:00:00", 3));
      List<String> fires = chain(
         in(TimeCondition.atDaysOfWeek(new int[] { 1 }, 1, 45, 0), "Australia/Lord_Howe"),
         "Australia/Lord_Howe", "2027-03-30T12:00:00", 3);
      assertOncePerLocalDate(fires);
      assertChain(List.of(
         "2027-04-04T01:45+10:30",
         "2027-04-11T01:45+10:30",
         "2027-04-18T01:45+10:30"), fires);
   }

   @Test
   void monthlyInLordHoweOnTheGapDayRunsLaterByTheGap() {
      assertChain(List.of(
         "2027-09-03T02:15+10:30",
         "2027-10-03T02:45+11:00",
         "2027-11-03T02:15+11:00"),
         chain(in(monthly(3, 2, 15, 0), "Australia/Lord_Howe"), "Australia/Lord_Howe",
               "2027-08-20T12:00:00", 3));
   }

   // ---------------------------------------------------------------------------------------
   // every month
   // ---------------------------------------------------------------------------------------

   @Test
   void monthlyOnTheDayAfterAGapRunsAtTheWallTime() {
      assertChain(List.of(
         "2028-02-13T02:30-05:00",
         "2028-03-13T02:30-04:00",
         "2028-04-13T02:30-04:00"),
         chain(in(monthly(13, 2, 30, 0), "America/New_York"), "America/New_York",
               "2028-02-01T12:00:00", 3));
   }

   @Test
   void monthlyOnTheGapDayRunsLaterOnce() {
      assertChain(List.of(
         "2028-02-12T02:30-05:00",
         "2028-03-12T03:30-04:00",
         "2028-04-12T02:30-04:00"),
         chain(in(monthly(12, 2, 30, 0), "America/New_York"), "America/New_York",
               "2028-02-01T12:00:00", 3));
   }

   @Test
   void monthlyLastDayAcrossTheCairoMidnightGap() {
      TimeCondition condition = in(monthly(-1, 0, 30, 0), "Africa/Cairo");
      // 2027-04-30 is the last day of April and has no midnight
      assertChain(List.of(
         "2027-03-31T00:30+02:00",
         "2027-04-30T01:30+03:00",
         "2027-05-31T00:30+03:00"),
         chain(condition, "Africa/Cairo", "2027-03-15T12:00:00", 3));
   }

   @Test
   void monthlyNthWeekdayAcrossAGap() {
      // 2nd Sunday of March 2028 is the gap day in New York
      TimeCondition condition = in(TimeCondition.atDayOfMonth(1, 2, 30, 0), "America/New_York");
      condition.setMonthsOfYear(ALL_MONTHS);
      condition.setWeekOfMonth(2);
      condition.setDayOfWeek(Calendar.SUNDAY);
      assertChain(List.of(
         "2028-02-13T02:30-05:00",
         "2028-03-12T03:30-04:00",
         "2028-04-09T02:30-04:00"),
         chain(condition, "America/New_York", "2028-02-01T12:00:00", 3));
   }

   // ---------------------------------------------------------------------------------------
   // every hour
   // ---------------------------------------------------------------------------------------

   @Test
   void hourlyInLondonNeverRunsBeforeTheWindowStart() {
      // 01:30-23:00 every 1.5 h, every day: the last tick of 03-25 is 22:30
      TimeCondition condition = in(TimeCondition.atHours(ALL_DAYS, 1, 30, 0), "Europe/London");
      condition.setHourEnd(23);
      condition.setHourlyInterval(1.5f);
      // 01:30 does not exist on 03-26, the window starts at 02:30 BST; it used to run at 00:00
      assertChain(List.of(
         "2028-03-25T22:30Z",
         "2028-03-26T02:30+01:00",
         "2028-03-26T04:00+01:00",
         "2028-03-26T05:30+01:00"),
         chain(condition, "Europe/London", "2028-03-25T21:10:00", 4));
   }

   @Test
   void hourlyOnWeekdaysDoesNotRunBeforeTheWindowStartOnMondayAfterTheGap() {
      TimeCondition condition = in(
         TimeCondition.atHours(new int[] { 2, 3, 4, 5, 6 }, 1, 30, 0), "Europe/London");
      condition.setHourEnd(23);
      condition.setHourlyInterval(1.5f);
      // Friday 03-24 22:30 is the last tick of the day: the next one is the start of the window on
      // Monday 03-27, not 00:00 (before the window start)
      long friday = ms("Europe/London", "2028-03-24T22:30:00");
      long next = retry(condition, friday + 7, friday + 30000, friday);
      assertEquals("2028-03-27T01:30+01:00", text("Europe/London", next));
   }

   @Test
   void hourlyTicksOnAGapDayAreElapsedHoursFromTheWindowStart() {
      TimeCondition condition = in(TimeCondition.atHours(ALL_DAYS, 1, 0, 0), "America/New_York");
      condition.setHourEnd(5);
      condition.setHourlyInterval(1f);
      // 02:00 does not exist on 2028-03-12, so the tick after 01:00 EST is 03:00 EDT
      assertChain(List.of(
         "2028-03-12T01:00-05:00",
         "2028-03-12T03:00-04:00",
         "2028-03-12T04:00-04:00",
         "2028-03-13T01:00-04:00"),
         chain(condition, "America/New_York", "2028-03-12T00:10:00", 4));
   }

   @Test
   void hourlyInLordHoweUsesTheWindowStartOfTheDay() {
      TimeCondition condition = in(TimeCondition.atHours(ALL_DAYS, 1, 0, 0), "Australia/Lord_Howe");
      condition.setHourEnd(4);
      condition.setHourlyInterval(1f);
      // 2027-10-03: 02:00-02:30 is skipped
      assertChain(List.of(
         "2027-10-03T01:00+10:30",
         "2027-10-03T02:30+11:00",
         "2027-10-03T03:30+11:00",
         "2027-10-04T01:00+11:00"),
         chain(condition, "Australia/Lord_Howe", "2027-10-03T00:10:00", 4));
   }

   // ---------------------------------------------------------------------------------------
   // controls: a zone without a change, results as before
   // ---------------------------------------------------------------------------------------

   @Test
   void utcResultsAreTheWallTimes() {
      assertChain(List.of(
         "2027-07-20T09:00Z", "2027-07-21T09:00Z", "2027-07-22T09:00Z"),
         chain(in(daily(9, 0, 0), "UTC"), "UTC", "2027-07-20T08:00:00", 3));
      assertChain(List.of(
         "2027-07-21T09:00Z", "2027-07-23T09:00Z", "2027-07-26T09:00Z"),
         chain(in(TimeCondition.atDaysOfWeek(new int[] { 2, 4, 6 }, 9, 0, 0), "UTC"), "UTC",
               "2027-07-20T10:00:00", 3));
      assertChain(List.of(
         "2027-07-20T11:00Z", "2027-07-20T13:00Z", "2027-07-21T09:00Z", "2027-07-21T11:00Z"),
         chain(hourly("UTC", 9, 15, 2f), "UTC", "2027-07-20T10:00:00", 4));
      assertChain(List.of(
         "2027-07-31T23:30Z", "2027-08-31T23:30Z", "2027-09-30T23:30Z"),
         chain(in(monthly(-1, 23, 30, 0), "UTC"), "UTC", "2027-07-20T10:00:00", 3));
   }

   private static TimeCondition hourly(String zone, int from, int to, float every) {
      TimeCondition condition = in(TimeCondition.atHours(ALL_DAYS, from, 0, 0), zone);
      condition.setHourEnd(to);
      condition.setHourlyInterval(every);
      return condition;
   }
}
