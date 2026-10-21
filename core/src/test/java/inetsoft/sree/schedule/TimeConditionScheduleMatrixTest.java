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
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Time zone / DST matrix for {@link TimeCondition} (Testing #79041).
 *
 * <p>Each case builds a condition the way the EM editor does
 * ({@code ScheduleConditionService}), then computes one year of fire times the way the
 * scheduler does: the first with {@code getRetryTime(now)} ({@code Scheduler.addTask}), each
 * later one with {@code getRetryTime(prev + 30s, prev)} at the moment {@code prev} fires
 * ({@code ClusterJobStore.triggersFired} -> {@code TimeConditionTriggerImpl.getFireTimeAfter}).
 * The result is compared with an independent {@code java.time} oracle: the condition fires
 * once at its wall-clock time on every day it describes.</p>
 *
 * <p>The year runs from 2027-07-01 to 2028-07-01, so it has both DST changes of every zone
 * below and the leap day 2028-02-29. For a wall time that a DST change skips or repeats, the
 * oracle uses the rule proposed for D1 in #79041 (not decided yet): a skipped time moves later
 * by the length of the gap, and a repeated time fires once, at its first occurrence.</p>
 */
@Tag("core")
class TimeConditionScheduleMatrixTest {
   @BeforeEach
   void saveDefaults() {
      defaultZone = TimeZone.getDefault();
      defaultLocale = Locale.getDefault();
   }

   @AfterEach
   void restoreDefaults() {
      TimeZone.setDefault(defaultZone);
      Locale.setDefault(defaultLocale);
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("dayCases")
   void conditionFiresOnceAtItsWallClockTime(String name, ZoneId zone, DaySpec spec,
                                             Locale locale)
   {
      assertDaySpec(name, zone, spec, locale, TIMES);
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("hourCases")
   void hourlyConditionFiresAtItsWallClockTimes(String name, ZoneId zone, HourSpec spec) {
      assertChain(spec.name, zone, spec.create(zone), hourOracle(spec, zone));
   }

   @ParameterizedTest(name = "server zone {0}")
   @ValueSource(strings = { "UTC", "Asia/Shanghai", "America/New_York" })
   void fireTimesDoNotDependOnTheServerTimeZone(String serverZone) {
      TimeZone.setDefault(TimeZone.getTimeZone(serverZone));
      List<Executable> checks = new ArrayList<>();

      for(String id : List.of("America/New_York", "Australia/Lord_Howe", "Asia/Kolkata")) {
         ZoneId zone = ZoneId.of(id);

         for(DaySpec spec : List.of(DAILY, WEEKLY_MON_WED_FRI, MONTHLY_LAST_DAY)) {
            for(LocalTime time : TIMES) {
               checks.add(() -> assertChain(id + " " + spec.name + " at " + time, zone,
                  spec.create(time, zone), dayOracle(spec, time, zone)));
            }
         }
      }

      assertAll(checks);
   }

   // Current behavior, recorded in #79041: the fire time keeps the milliseconds of the clock
   // reading the calculation starts from.
   @Test
   void fireTimeKeepsTheMillisecondsOfTheClock() {
      ZoneId zone = ZoneId.of("UTC");
      TimeCondition condition = DAILY.create(LocalTime.of(9, 0), zone);
      long now = ZonedDateTime.of(2027, 7, 1, 8, 0, 0, 123_000_000, zone)
         .toInstant().toEpochMilli();

      long next = condition.getRetryTime(now, now, now);

      assertEquals(ZonedDateTime.of(2027, 7, 1, 9, 0, 0, 123_000_000, zone).toInstant(),
                   Instant.ofEpochMilli(next));
   }

   @Disabled("Bug #79052: a DST change makes a time condition fire twice or at the wrong time")
   @ParameterizedTest(name = "{0}")
   @MethodSource("dstCases")
   void conditionFiresOnceAtItsWallClockTimeAcrossDstChanges(String name, ZoneId zone,
                                                             DaySpec spec, Locale locale)
   {
      assertDaySpec(name, zone, spec, locale, DST_TIMES);
   }

   @Disabled("Bug #79052: a DST change makes a time condition fire twice or at the wrong time")
   @ParameterizedTest(name = "{0}")
   @MethodSource("dstHourCases")
   void hourlyConditionStartingInADstGapFiresInsideItsWindow(String name, ZoneId zone,
                                                             HourSpec spec)
   {
      assertChain(spec.name, zone, spec.create(zone), hourOracle(spec, zone));
   }

   @Disabled("Bug #79052: a DST change makes a time condition fire twice or at the wrong time")
   @ParameterizedTest(name = "{0} {1} {2} -> {3}")
   @CsvSource({
      // spring forward: the wall time doesn't exist and moves later by the gap
      "America/New_York,    2028-03-12, 02:30, 2028-03-12T03:30-04:00",
      "Europe/London,       2028-03-26, 01:30, 2028-03-26T02:30+01:00",
      "Australia/Lord_Howe, 2027-10-03, 02:15, 2027-10-03T02:45+11:00",
      // fall back: the wall time happens twice and fires once, the first time
      "America/New_York,    2027-11-07, 01:30, 2027-11-07T01:30-04:00",
      "Europe/London,       2027-10-31, 01:30, 2027-10-31T01:30+01:00",
      "Australia/Lord_Howe, 2028-04-02, 01:45, 2028-04-02T01:45+11:00"
   })
   void dailyConditionFiresOnceOnADstChangeDay(String zoneId, String day, String time,
                                               String expected)
   {
      ZoneId zone = ZoneId.of(zoneId);
      LocalDate date = LocalDate.parse(day);
      TimeCondition condition = DAILY.create(LocalTime.parse(time), zone);
      long prev = ZonedDateTime.of(date.minusDays(1), LocalTime.parse(time), zone)
         .toInstant().toEpochMilli();
      List<Instant> fires = new ArrayList<>();

      for(int i = 0; i < 3; i++) {
         prev = condition.getRetryTime(prev + 30000, prev, prev);

         if(Instant.ofEpochMilli(prev).atZone(zone).toLocalDate().equals(date)) {
            fires.add(Instant.ofEpochMilli(prev));
         }
      }

      assertEquals(List.of(OffsetDateTime.parse(expected).toInstant()), fires,
                   "fires on " + day);
   }

   @Disabled("Bug #79053: \"every N weeks\" runs every week or skips selected days")
   @ParameterizedTest(name = "{0}")
   @MethodSource("everyNWeeksBugCases")
   void everyNWeeksFiresOnlyInEveryNthWeek(String name, ZoneId zone, DaySpec spec,
                                           Locale locale)
   {
      assertDaySpec(name, zone, spec, locale, TIMES);
   }

   // Quartz calls Trigger.triggered() up to 2 ms before the fire time
   // (QuartzSchedulerThread waits until less than 2 ms are left), so "now" can still be the
   // previous day when a task fires at midnight.
   @Disabled("Not filed yet, see the WP2-1 note on Testing #79041: a task that fires at 00:00:00 " +
             "can fire again right away")
   @ParameterizedTest(name = "{0} at {1}")
   @CsvSource({
      "UTC, 00:00:00", "America/New_York, 00:00:00", "Asia/Shanghai, 00:00:00",
      "Asia/Kolkata, 00:00:00"
   })
   void triggerFiredJustBeforeMidnightGetsTheNextDaysFireTime(String zoneId, String time) {
      ZoneId zone = ZoneId.of(zoneId);
      LocalTime at = LocalTime.parse(time);
      TimeCondition condition = DAILY.create(at, zone);
      long prev = ZonedDateTime.of(LocalDate.of(2027, 7, 2), at, zone).toInstant().toEpochMilli();

      long next = condition.getRetryTime(prev + 30000, prev, prev - 2);

      assertEquals(ZonedDateTime.of(LocalDate.of(2027, 7, 3), at, zone).toInstant(),
                   Instant.ofEpochMilli(next),
                   "next fire after the one at " + format(prev, zone));
   }

   private static Stream<Arguments> dayCases() {
      List<Arguments> cases = new ArrayList<>();

      for(String id : ZONES) {
         for(DaySpec spec : DAY_SPECS) {
            // only the weekly calculation uses the locale's first day of the week
            List<Locale> locales = spec.weekly ? List.of(Locale.US, Locale.CHINA) :
               List.of(Locale.US);

            for(Locale locale : locales) {
               if(!spec.bugLocales.contains(locale)) {
                  cases.add(dayCase(id, spec, locale));
               }
            }
         }
      }

      return cases.stream();
   }

   private static Stream<Arguments> dstCases() {
      return DST_ZONES.stream().flatMap(id -> DAY_SPECS.stream()
         .filter(spec -> !spec.bugLocales.contains(Locale.US))
         .map(spec -> dayCase(id, spec, Locale.US)));
   }

   private static Stream<Arguments> everyNWeeksBugCases() {
      return DAY_SPECS.stream().flatMap(spec -> spec.bugLocales.stream()
         .map(locale -> dayCase("UTC", spec, locale)));
   }

   private static Stream<Arguments> hourCases() {
      return ZONES.stream().flatMap(id -> HOUR_SPECS.stream()
         .map(spec -> Arguments.of(id + " " + spec.name, ZoneId.of(id), spec)));
   }

   private static Stream<Arguments> dstHourCases() {
      return DST_ZONES.stream()
         .map(id -> Arguments.of(id + " " + HOURLY_FROM_GAP.name, ZoneId.of(id), HOURLY_FROM_GAP));
   }

   private static Arguments dayCase(String id, DaySpec spec, Locale locale) {
      String name = id + " " + spec.name + (spec.weekly ? " " + locale : "");
      return Arguments.of(name, ZoneId.of(id), spec, locale);
   }

   private void assertDaySpec(String name, ZoneId zone, DaySpec spec, Locale locale,
                              List<LocalTime> times)
   {
      Locale.setDefault(locale);
      List<Executable> checks = new ArrayList<>();

      for(LocalTime time : times) {
         checks.add(() -> assertChain(spec.name + " at " + time, zone,
            spec.create(time, zone), dayOracle(spec, time, zone)));
      }

      assertAll(name, checks);
   }

   private static void assertChain(String name, ZoneId zone, TimeCondition condition,
                                   List<Instant> expected)
   {
      List<Instant> actual = assertTimeoutPreemptively(
         Duration.ofSeconds(10), () -> chain(condition, zone), name + " did not return");

      if(!expected.equals(actual)) {
         fail(name + " in " + zone + ": " + describeDifference(expected, actual, zone));
      }
   }

   /**
    * Fire times between FROM and TO, computed the way Scheduler.addTask and
    * TimeConditionTriggerImpl.getFireTimeAfter compute them.
    */
   private static List<Instant> chain(TimeCondition condition, ZoneId zone) {
      long start = FROM.atZone(zone).toInstant().toEpochMilli();
      long end = TO.atZone(zone).toInstant().toEpochMilli();
      List<Instant> fires = new ArrayList<>();
      long next = condition.getRetryTime(start, start, start);

      while(next >= 0 && next < end && fires.size() < MAX_FIRES) {
         if(!fires.isEmpty() && next <= fires.get(fires.size() - 1).toEpochMilli()) {
            fires.add(Instant.ofEpochMilli(next)); // keep the bad value for the report
            break;
         }

         fires.add(Instant.ofEpochMilli(next));
         next = condition.getRetryTime(next + 30000, next, next);
      }

      return fires;
   }

   private static List<Instant> dayOracle(DaySpec spec, LocalTime time, ZoneId zone) {
      Instant start = FROM.atZone(zone).toInstant();
      Instant end = TO.atZone(zone).toInstant();
      LocalDate startDate = FROM.toLocalDate();
      List<Instant> fires = new ArrayList<>();

      for(LocalDate day = startDate; !day.isAfter(TO.toLocalDate()); day = day.plusDays(1)) {
         if(spec.runsOn.test(new Day(day, startDate))) {
            Instant fire = resolve(day, time, zone);

            if(!fire.isBefore(start) && fire.isBefore(end)) {
               fires.add(fire);
            }
         }
      }

      return fires;
   }

   private static List<Instant> hourOracle(HourSpec spec, ZoneId zone) {
      Instant start = FROM.atZone(zone).toInstant();
      Instant end = TO.atZone(zone).toInstant();
      long step = (long) (spec.hours * 3600000);
      List<Instant> fires = new ArrayList<>();

      for(LocalDate day = FROM.toLocalDate(); !day.isAfter(TO.toLocalDate());
          day = day.plusDays(1))
      {
         if(!spec.days.contains(day.getDayOfWeek())) {
            continue;
         }

         Instant windowEnd = resolve(day, spec.end, zone);

         // steps are elapsed time, so a DST change shifts the wall-clock times after it
         for(Instant fire = resolve(day, spec.start, zone); fire.isBefore(windowEnd);
             fire = fire.plusMillis(step))
         {
            if(!fire.isBefore(start) && fire.isBefore(end)) {
               fires.add(fire);
            }
         }
      }

      return fires;
   }

   /**
    * The instant a wall-clock time stands for: a time in the spring-forward gap moves later
    * by the length of the gap, and a time in the fall-back overlap takes its first occurrence.
    */
   private static Instant resolve(LocalDate day, LocalTime time, ZoneId zone) {
      return ZonedDateTime.ofLocal(LocalDateTime.of(day, time), zone, null).toInstant();
   }

   private static String describeDifference(List<Instant> expected, List<Instant> actual,
                                            ZoneId zone)
   {
      int n = Math.min(expected.size(), actual.size());
      int i = 0;

      while(i < n && expected.get(i).equals(actual.get(i))) {
         i++;
      }

      String prev = i > 0 ? format(expected.get(i - 1), zone) : "(none)";
      String want = i < expected.size() ? format(expected.get(i), zone) : "(no more fires)";
      String got = i < actual.size() ? format(actual.get(i), zone) : "(no more fires)";

      return String.format(
         "%d fires expected, %d computed; first difference at fire #%d (after %s): " +
         "expected %s, computed %s", expected.size(), actual.size(), i + 1, prev, want, got);
   }

   private static String format(Instant instant, ZoneId zone) {
      return instant.atZone(zone).toOffsetDateTime().toString();
   }

   private static String format(long millis, ZoneId zone) {
      return format(Instant.ofEpochMilli(millis), zone);
   }

   private static int[] calendarDays(DayOfWeek... days) {
      // java.util.Calendar numbers the days 1 (Sunday) to 7 (Saturday)
      return Arrays.stream(days).mapToInt(d -> d.getValue() % 7 + 1).sorted().toArray();
   }

   private static long sundayWeek(LocalDate day) {
      LocalDate sunday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
      return ChronoUnit.WEEKS.between(LocalDate.of(2000, 1, 2), sunday);
   }

   private static boolean isWeekday(LocalDate day) {
      return day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY;
   }

   private static boolean isNthWeekday(LocalDate day, int n, DayOfWeek dayOfWeek) {
      return day.getDayOfWeek() == dayOfWeek && (day.getDayOfMonth() - 1) / 7 + 1 == n;
   }

   /** A date the oracle checks, and the date the schedule starts from. */
   private record Day(LocalDate date, LocalDate start) {
   }

   private interface DayRule {
      boolean test(Day day);
   }

   /**
    * @param weekly     whether the condition is weekly, whose calculation depends on the
    *                   locale's first day of the week.
    * @param bugLocales default locales in which the condition is known to fire on the wrong
    *                   days (Bug #79053).
    */
   private record DaySpec(String name, boolean weekly, Set<Locale> bugLocales,
                          Function<LocalTime, TimeCondition> factory, DayRule runsOn)
   {
      DaySpec(String name, Function<LocalTime, TimeCondition> factory, DayRule runsOn) {
         this(name, false, Set.of(), factory, runsOn);
      }

      TimeCondition create(LocalTime time, ZoneId zone) {
         TimeCondition condition = factory.apply(time);
         condition.setTimeZone(TimeZone.getTimeZone(zone));
         return condition;
      }

      @Override
      public String toString() {
         return name;
      }
   }

   private record HourSpec(String name, Set<DayOfWeek> days, LocalTime start, LocalTime end,
                           float hours)
   {
      TimeCondition create(ZoneId zone) {
         TimeCondition condition = TimeCondition.atHours(
            calendarDays(days.toArray(new DayOfWeek[0])),
            start.getHour(), start.getMinute(), start.getSecond());
         condition.setHourEnd(end.getHour());
         condition.setMinuteEnd(end.getMinute());
         condition.setSecondEnd(end.getSecond());
         condition.setHourlyInterval(hours);
         condition.setInterval(1);
         condition.setTimeZone(TimeZone.getTimeZone(zone));
         return condition;
      }

      @Override
      public String toString() {
         return name;
      }
   }

   private static TimeCondition daily(LocalTime time, int interval, boolean weekdayOnly) {
      TimeCondition condition =
         TimeCondition.at(time.getHour(), time.getMinute(), time.getSecond());
      condition.setInterval(interval);
      condition.setWeekdayOnly(weekdayOnly);
      return condition;
   }

   private static TimeCondition weekly(LocalTime time, int interval, DayOfWeek... days) {
      TimeCondition condition = TimeCondition.atDaysOfWeek(
         calendarDays(days), time.getHour(), time.getMinute(), time.getSecond());
      condition.setInterval(interval);
      return condition;
   }

   private static TimeCondition monthlyDay(LocalTime time, int dayOfMonth, int... months) {
      TimeCondition condition = TimeCondition.atDayOfMonth(
         dayOfMonth, time.getHour(), time.getMinute(), time.getSecond());
      condition.setMonthsOfYear(months);
      return condition;
   }

   private static TimeCondition monthlyWeek(LocalTime time, int week, DayOfWeek day) {
      TimeCondition condition = TimeCondition.atWeekOfMonth(
         week, calendarDays(day)[0], time.getHour(), time.getMinute(), time.getSecond());
      condition.setMonthsOfYear(ALL_MONTHS);
      return condition;
   }

   private static DaySpec weeklySpec(String name, int interval, Set<Locale> bugLocales,
                                     DayOfWeek... days)
   {
      Set<DayOfWeek> selected = EnumSet.copyOf(Arrays.asList(days));
      // weeks start on Sunday, as the editor lists them
      DayRule rule = d -> selected.contains(d.date().getDayOfWeek()) &&
         (sundayWeek(d.date()) - sundayWeek(d.start())) % interval == 0;
      return new DaySpec(name, true, bugLocales, t -> weekly(t, interval, days), rule);
   }

   private static final LocalDateTime FROM = LocalDateTime.of(2027, 7, 1, 12, 34, 56);
   private static final LocalDateTime TO = LocalDateTime.of(2028, 7, 1, 0, 0);
   private static final int MAX_FIRES = 20000;

   // zones with DST in the north, in the south, and with a 30-minute change
   private static final List<String> DST_ZONES = List.of(
      "America/New_York", "Europe/London", "Australia/Sydney", "Australia/Lord_Howe");

   // plus zones without DST, with half- and quarter-hour offsets
   private static final List<String> ZONES = List.of(
      "UTC", "America/New_York", "Europe/London", "Australia/Sydney", "Australia/Lord_Howe",
      "Asia/Kolkata", "Asia/Kathmandu", "Asia/Shanghai");

   // times that no DST change of the zones above skips or repeats
   private static final List<LocalTime> TIMES = List.of(
      LocalTime.of(0, 0), LocalTime.of(0, 0, 15), LocalTime.of(9, 0), LocalTime.of(23, 59, 59));

   // times that the DST changes of one or more zones above skip or repeat
   private static final List<LocalTime> DST_TIMES = List.of(
      LocalTime.of(1, 30), LocalTime.of(1, 45), LocalTime.of(2, 15), LocalTime.of(2, 30));

   private static final int[] ALL_MONTHS = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };

   private static final DaySpec DAILY = new DaySpec(
      "daily", t -> daily(t, 1, false), d -> true);
   private static final DaySpec WEEKLY_MON_WED_FRI = weeklySpec(
      "weekly Mon/Wed/Fri", 1, Set.of(), DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
   private static final DaySpec MONTHLY_LAST_DAY = new DaySpec(
      "monthly last day", t -> monthlyDay(t, TimeCondition.LAST_DAY_OF_MONTH, ALL_MONTHS),
      d -> d.date().getDayOfMonth() == d.date().lengthOfMonth());

   private static final List<DaySpec> DAY_SPECS = List.of(
      DAILY,
      new DaySpec("every 2 days", t -> daily(t, 2, false),
                  d -> ChronoUnit.DAYS.between(d.start(), d.date()) % 2 == 0),
      new DaySpec("weekdays", t -> daily(t, 1, true), d -> isWeekday(d.date())),
      WEEKLY_MON_WED_FRI,
      weeklySpec("weekly Sat/Sun", 1, Set.of(), DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
      weeklySpec("every 2 weeks Mon/Wed", 2, Set.of(), DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
      weeklySpec("every 2 weeks Sat/Sun", 2, Set.of(Locale.CHINA),
                 DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
      weeklySpec("every 2 weeks Sun/Wed", 2, Set.of(Locale.CHINA),
                 DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY),
      weeklySpec("every 2 weeks Sun", 2, Set.of(Locale.CHINA), DayOfWeek.SUNDAY),
      weeklySpec("every 2 weeks Sat", 2, Set.of(Locale.US, Locale.CHINA), DayOfWeek.SATURDAY),
      new DaySpec("monthly day 1", t -> monthlyDay(t, 1, ALL_MONTHS),
                  d -> d.date().getDayOfMonth() == 1),
      new DaySpec("monthly day 29", t -> monthlyDay(t, 29, ALL_MONTHS),
                  d -> d.date().getDayOfMonth() == 29),
      MONTHLY_LAST_DAY,
      new DaySpec("monthly day before last",
                  t -> monthlyDay(t, TimeCondition.LAST_DAY_OF_MONTH - 1, ALL_MONTHS),
                  d -> d.date().getDayOfMonth() == d.date().lengthOfMonth() - 1),
      new DaySpec("February 29 only", t -> monthlyDay(t, 29, 1),
                  d -> d.date().getMonth() == Month.FEBRUARY && d.date().getDayOfMonth() == 29),
      new DaySpec("last day of Jan/Apr/Jul/Oct",
                  t -> monthlyDay(t, TimeCondition.LAST_DAY_OF_MONTH, 0, 3, 6, 9),
                  d -> d.date().getDayOfMonth() == d.date().lengthOfMonth() &&
                     EnumSet.of(Month.JANUARY, Month.APRIL, Month.JULY, Month.OCTOBER)
                        .contains(d.date().getMonth())),
      new DaySpec("1st Monday", t -> monthlyWeek(t, 1, DayOfWeek.MONDAY),
                  d -> isNthWeekday(d.date(), 1, DayOfWeek.MONDAY)),
      new DaySpec("4th Sunday", t -> monthlyWeek(t, 4, DayOfWeek.SUNDAY),
                  d -> isNthWeekday(d.date(), 4, DayOfWeek.SUNDAY)),
      new DaySpec("5th Friday", t -> monthlyWeek(t, 5, DayOfWeek.FRIDAY),
                  d -> isNthWeekday(d.date(), 5, DayOfWeek.FRIDAY)));

   private static final Set<DayOfWeek> EVERY_DAY = EnumSet.allOf(DayOfWeek.class);
   private static final Set<DayOfWeek> WEEKDAYS = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

   private static final List<HourSpec> HOUR_SPECS = List.of(
      new HourSpec("hourly 00:00-04:00 every 1 h", EVERY_DAY,
                   LocalTime.of(0, 0), LocalTime.of(4, 0), 1f),
      new HourSpec("hourly 00:30-03:30 every 0.5 h", EVERY_DAY,
                   LocalTime.of(0, 30), LocalTime.of(3, 30), 0.5f),
      new HourSpec("hourly weekdays 05:00-23:00 every 1.5 h", WEEKDAYS,
                   LocalTime.of(5, 0), LocalTime.of(23, 0), 1.5f),
      new HourSpec("hourly 00:00-23:59:59 every 1 h", EVERY_DAY,
                   LocalTime.of(0, 0), LocalTime.of(23, 59, 59), 1f));

   // starts at a time that the London spring-forward change skips
   private static final HourSpec HOURLY_FROM_GAP = new HourSpec(
      "hourly weekdays 01:00-23:00 every 1.5 h", WEEKDAYS,
      LocalTime.of(1, 0), LocalTime.of(23, 0), 1.5f);

   private TimeZone defaultZone;
   private Locale defaultLocale;
}
