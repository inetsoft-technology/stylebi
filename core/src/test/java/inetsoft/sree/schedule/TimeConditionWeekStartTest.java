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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79053 -- an "every N weeks" condition (N &gt; 1) ended the skipped week on the Saturday
 * of the <em>JVM locale's</em> week and then compared with {@code days_of_week[0]}, so on a
 * Monday-first host Sunday, Sunday+Wednesday and Saturday+Sunday sets fired every Sunday, a
 * Saturday-only set fired every week, and {@code [7, 1]} behaved as {@code [7]}. The weeks now
 * start on the site-wide {@code week.start} day (default Sunday), whatever the locale.
 *
 * <p>The expected values come from an independent java.time model of the rule: the rest of the
 * seed's week, then the days from the start of the week N weeks after the seed's week.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TimeConditionWeekStartTest {
   private static final ZoneId UTC = ZoneId.of("UTC");
   private static final long DAY = 86400000L;

   private Locale savedLocale;
   private TimeZone savedTimeZone;
   private String savedWeekStart;
   private java.security.Principal savedPrincipal;

   @BeforeEach
   void setUp() {
      savedLocale = Locale.getDefault();
      savedTimeZone = TimeZone.getDefault();
      savedWeekStart = SreeEnv.getProperty("week.start");
      savedPrincipal = ThreadContext.getContextPrincipal();
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
   }

   @AfterEach
   void tearDown() {
      Locale.setDefault(savedLocale);
      TimeZone.setDefault(savedTimeZone);
      ThreadContext.setContextPrincipal(savedPrincipal);
      SreeEnv.setProperty("week.start", savedWeekStart == null ? "" : savedWeekStart);
      Tool.clearWeekStartCache();
      SreeEnv.remove("inetsoft.org.org79053.week.start");
   }

   // ---------------------------------------------------------------------------------------
   // the reporter's cases, Monday-first hosts, week.start not set (Sunday weeks)
   // ---------------------------------------------------------------------------------------

   @Test
   void sundayOnlyEveryTwoWeeks() {
      for(Locale locale : mondayFirst()) {
         Locale.setDefault(locale);
         // lastRun Sunday 07-18: the next run is Sunday 08-01, not Sunday 07-25
         assertEquals(ms("2027-08-01T09:00:00"),
                      run(2, days(1), "2027-07-18T09:00:00", 30_000), locale.toString());
      }
   }

   @Test
   void sundayAndWednesdayEveryTwoWeeks() {
      for(Locale locale : mondayFirst()) {
         Locale.setDefault(locale);
         // Wednesday 07-21 is in the Sunday week 07-18..07-24, the next one is 08-01
         assertEquals(ms("2027-08-01T09:00:00"),
                      run(2, days(1, 4), "2027-07-21T09:00:00", 30_000), locale.toString());
         // Sunday 08-01, then Wednesday 08-04 in the same week
         assertEquals(ms("2027-08-04T09:00:00"),
                      run(2, days(1, 4), "2027-08-01T09:00:00", 30_000), locale.toString());
      }
   }

   @Test
   void saturdayAndSundayEveryTwoWeeks() {
      for(Locale locale : mondayFirst()) {
         Locale.setDefault(locale);
         // Saturday 07-17 ends the week 07-11..07-17; the next two weeks start on 07-25
         assertEquals(ms("2027-07-25T09:00:00"),
                      run(2, days(7, 1), "2027-07-17T09:00:00", 30_000), locale.toString());
         // Sunday 07-25 is followed by the Saturday 07-31 of its own week, and that Saturday
         // by the Sunday two weeks after the week it ends
         assertEquals(ms("2027-07-31T09:00:00"),
                      run(2, days(7, 1), "2027-07-25T09:00:00", 30_000), locale.toString());
         assertEquals(ms("2027-08-08T09:00:00"),
                      run(2, days(7, 1), "2027-07-31T09:00:00", 30_000), locale.toString());
      }
   }

   @Test
   void saturdayOnlyEveryNWeeksInEveryLocale() {
      for(Locale locale : new Locale[] { Locale.US, Locale.GERMANY, Locale.UK, Locale.CHINA }) {
         Locale.setDefault(locale);
         assertEquals(ms("2027-07-31T09:00:00"),
                      run(2, days(7), "2027-07-17T09:00:00", 30_000), locale.toString());
         assertEquals(ms("2027-08-07T09:00:00"),
                      run(3, days(7), "2027-07-17T09:00:00", 30_000), locale.toString());
      }
   }

   @Test
   void dayOrderDoesNotMatter() {
      for(Locale locale : mondayFirst()) {
         Locale.setDefault(locale);
         assertEquals(run(2, days(1, 7), "2027-07-17T09:00:00", 30_000),
                      run(2, days(7, 1), "2027-07-17T09:00:00", 30_000), locale.toString());
         assertEquals(run(3, days(4, 1, 6), "2027-07-21T09:00:00", 30_000),
                      run(3, days(6, 4, 1), "2027-07-21T09:00:00", 30_000), locale.toString());
      }
   }

   /**
    * The one-time effect for a Monday-first host that keeps the default week.start: a
    * Sunday lastRun on a set without Sunday. It used to skip the following Monday-first week
    * as well; the Sunday week now continues on Monday.
    */
   @Test
   void sundayLastRunOnAMondayFirstHostFiresEarlierOnce() {
      Locale.setDefault(Locale.GERMANY);
      assertEquals(ms("2027-06-28T09:00:00"),
                   run(2, days(2), "2027-06-27T09:00:00", 30_000));
   }

   // ---------------------------------------------------------------------------------------
   // week.start
   // ---------------------------------------------------------------------------------------

   @Test
   void mondayWeekStartPairsSundayWithThePreviousWeek() {
      WeekStartUtil.withWeekStart("monday", () -> {
         for(Locale locale : new Locale[] { Locale.US, Locale.GERMANY }) {
            Locale.setDefault(locale);
            // week Mon 07-19..Sun 07-25: Wednesday 07-21 is followed by Sunday 07-25
            assertEquals(ms("2027-07-25T09:00:00"),
                         run(2, days(1, 4), "2027-07-21T09:00:00", 30_000), locale.toString());
            // Sunday 07-25 ends its week; the next two weeks start Monday 08-02, Wednesday 08-04
            assertEquals(ms("2027-08-04T09:00:00"),
                         run(2, days(1, 4), "2027-07-25T09:00:00", 30_000), locale.toString());
         }
      });
   }

   @Test
   void defaultAndInvalidWeekStartIsSunday() {
      for(String value : new String[] { null, "", "garbage", "SUNDAY", "Sunday" }) {
         WeekStartUtil.withWeekStart(value, () -> {
            Locale.setDefault(Locale.GERMANY);
            assertEquals(ms("2027-08-01T09:00:00"),
                         run(2, days(1, 4), "2027-07-21T09:00:00", 30_000), String.valueOf(value));
         });
      }
   }

   @Test
   void weekStartIsCaseInsensitive() {
      WeekStartUtil.withWeekStart("MoNdAy", () -> {
         Locale.setDefault(Locale.US);
         assertEquals(ms("2027-07-25T09:00:00"),
                      run(2, days(1, 4), "2027-07-21T09:00:00", 30_000));
      });
   }

   @Test
   void threadOrganizationWeekStartDoesNotChangeTheCycle() {
      Locale.setDefault(Locale.US);
      // the site-wide value stays unset (Sunday); the org of the calling thread says Monday
      SreeEnv.setProperty("inetsoft.org.org79053.week.start", "monday");
      OrganizationManager manager = mock(OrganizationManager.class);
      when(manager.getCurrentOrgID()).thenReturn("org79053");
      ThreadContext.setContextPrincipal(new SRPrincipal(
         new IdentityID("tester", "org79053"), new IdentityID[0], new String[0], "org79053", 1L));

      try(MockedStatic<OrganizationManager> om = mockStatic(OrganizationManager.class)) {
         om.when(OrganizationManager::getInstance).thenReturn(manager);

         // the org override is really visible to the thread, so the next row is meaningful
         assertEquals("monday", SreeEnv.getProperty("week.start"));
         assertEquals(Calendar.MONDAY, Tool.parseFirstDayOfWeek(SreeEnv.getProperty("week.start")));

         // Bug #79133: the two-argument call has no task, so it is the site value whatever org the
         // thread is in; a task's own org is passed to the three-argument call
         // Sunday weeks (Monday weeks would give 07-25)
         assertEquals(ms("2027-08-01T09:00:00"),
                      run(2, days(1, 4), "2027-07-21T09:00:00", 30_000));
      }
   }

   @Test
   void parseFirstDayOfWeek() {
      assertEquals(Calendar.SUNDAY, Tool.parseFirstDayOfWeek(null));
      assertEquals(Calendar.SUNDAY, Tool.parseFirstDayOfWeek(""));
      assertEquals(Calendar.SUNDAY, Tool.parseFirstDayOfWeek("funday"));
      assertEquals(Calendar.MONDAY, Tool.parseFirstDayOfWeek("Monday"));
      assertEquals(Calendar.TUESDAY, Tool.parseFirstDayOfWeek("tuesday"));
      assertEquals(Calendar.WEDNESDAY, Tool.parseFirstDayOfWeek("WEDNESDAY"));
      assertEquals(Calendar.THURSDAY, Tool.parseFirstDayOfWeek("thursday"));
      assertEquals(Calendar.FRIDAY, Tool.parseFirstDayOfWeek("friday"));
      assertEquals(Calendar.SATURDAY, Tool.parseFirstDayOfWeek("saturday"));
   }

   // ---------------------------------------------------------------------------------------
   // stale lastRun keeps today's rule
   // ---------------------------------------------------------------------------------------

   @Test
   void staleLastRunFiresOnTheFirstSelectedDayOnOrAfterNow() {
      for(Locale locale : new Locale[] { Locale.US, Locale.GERMANY }) {
         Locale.setDefault(locale);
         // Monday only, lastRun Monday 2026-07-20, now Tuesday 2027-07-20: the next Monday
         // 2027-07-26 (the odd week; the phase of the cycle is not kept)
         long curr = ms("2027-07-20T10:00:00");
         assertEquals(ms("2027-07-26T09:00:00"),
                      call(weekly(2, days(2)), curr, ms("2026-07-20T09:00:00")), locale.toString());
         assertEquals(ms("2027-07-26T09:00:00"),
                      call(weekly(2, days(2)), curr, ms("1971-07-19T09:00:00")), locale.toString());
      }
   }

   // ---------------------------------------------------------------------------------------
   // the whole space against the model
   // ---------------------------------------------------------------------------------------

   @Test
   void allDaySetsMatchTheModelForEveryLocaleAndWeekStart() {
      String[] weekStarts = { null, "sunday", "monday", "wednesday", "saturday", "garbage" };
      Locale[] locales = { Locale.US, Locale.GERMANY, Locale.CHINA };
      long[] currOffsets = { 30_000, 3 * DAY, 8 * DAY, 15 * DAY, 100 * DAY, 400 * DAY };
      int checked = 0;

      for(String weekStart : weekStarts) {
         int firstDay = Tool.parseFirstDayOfWeek(weekStart);
         SreeEnv.setProperty("week.start", weekStart == null ? "" : weekStart);
         Tool.clearWeekStartCache();

         for(Locale locale : locales) {
            Locale.setDefault(locale);

            for(int interval = 2; interval <= 3; interval++) {
               for(int mask = 1; mask < 128; mask++) {
                  int[] days = fromMask(mask);
                  TimeCondition condition = weekly(interval, days);

                  for(int seedDay = 0; seedDay < 14; seedDay++) {
                     // 2027-07-11 is a Sunday
                     long lastRun = ms("2027-07-11T09:00:00") + seedDay * DAY;

                     for(long offset : currOffsets) {
                        long curr = lastRun + offset;
                        assertEquals(model(days, interval, firstDay, lastRun, curr),
                                     call(condition, curr, lastRun),
                                     "week.start " + weekStart + " " + locale + " N=" + interval +
                                     " days " + Arrays.toString(days) + " lastRun " +
                                     Instant.ofEpochMilli(lastRun) + " curr +" + offset);
                        checked++;
                     }
                  }
               }
            }
         }
      }

      assertEquals(6 * 3 * 2 * 127 * 14 * 6, checked);
   }

   /** Interval 1 does not depend on the week anchor and is exactly the code before the fix. */
   @Test
   void intervalOneIsUnchanged() {
      long[] currOffsets = { 30_000, 2 * DAY, 9 * DAY };

      for(Locale locale : new Locale[] { Locale.US, Locale.GERMANY, Locale.CHINA }) {
         Locale.setDefault(locale);

         for(String weekStart : new String[] { "", "monday", "saturday" }) {
            SreeEnv.setProperty("week.start", weekStart);
            Tool.clearWeekStartCache();

            for(int mask = 1; mask < 128; mask++) {
               int[] days = fromMask(mask);
               TimeCondition condition = weekly(1, days);

               for(long offset : currOffsets) {
                  long actual;
                  long expected;
                  long before;
                  int attempts = 0;

                  // the clock is read inside getRetryTime: use only a result that the old
                  // algorithm gives for the same second before and after the call
                  do {
                     long now = System.currentTimeMillis();
                     before = oldWeekly(days, now, now + offset);
                     actual = condition.getRetryTime(now + offset, 0);
                     expected = oldWeekly(days, System.currentTimeMillis(),
                                          System.currentTimeMillis() + offset);
                     assertTrue(before > 0);
                     attempts++;
                  }
                  while(before / 1000 != expected / 1000 && attempts < 5);

                  assertEquals(expected / 1000, actual / 1000,
                               locale + " week.start '" + weekStart + "' days " +
                               Arrays.toString(days) + " +" + offset);
               }
            }
         }
      }
   }

   // ---------------------------------------------------------------------------------------
   // helpers
   // ---------------------------------------------------------------------------------------

   private static Locale[] mondayFirst() {
      return new Locale[] { Locale.GERMANY, Locale.UK, Locale.CHINA };
   }

   private static int[] days(int... days) {
      return days;
   }

   private static int[] fromMask(int mask) {
      int[] days = new int[Integer.bitCount(mask)];
      int n = 0;

      for(int i = 0; i < 7; i++) {
         if((mask & (1 << i)) != 0) {
            days[n++] = i + 1;
         }
      }

      return days;
   }

   private static long run(int interval, int[] days, String lastRun, long afterMillis) {
      long last = ms(lastRun);
      return call(weekly(interval, days), last + afterMillis, last);
   }

   private static long call(TimeCondition condition, long curr, long lastRun) {
      return condition.getRetryTime(curr, lastRun);
   }

   private static TimeCondition weekly(int interval, int[] days) {
      TimeCondition c = new TimeCondition();
      c.setType(TimeCondition.EVERY_WEEK);
      c.setTimeZone(TimeZone.getTimeZone(UTC));
      c.setHour(9);
      c.setMinute(0);
      c.setSecond(0);
      c.setInterval(interval);
      c.setDaysOfWeek(days);
      return c;
   }

   private static long ms(String utc) {
      return LocalDateTime.parse(utc).atZone(UTC).toInstant().toEpochMilli();
   }

   private static int calendarDay(LocalDate date) {
      return date.getDayOfWeek().getValue() % 7 + 1; // Calendar.SUNDAY is 1
   }

   private static long slot(LocalDate date) {
      return date.atTime(9, 0).atZone(UTC).toInstant().toEpochMilli();
   }

   /**
    * The rule: the days of the seed's week from the seed on, then the days from the first day
    * of the week N weeks after the seed's week; the first selected day whose time is not before
    * curr. Never inside a week that is skipped.
    */
   private static long model(int[] days, int interval, int firstDay, long lastRun, long curr) {
      Set<Integer> selected = new HashSet<>();

      for(int day : days) {
         selected.add(day);
      }

      LocalDate seed = Instant.ofEpochMilli(lastRun).atZone(UTC).toLocalDate();
      int index = Math.floorMod(calendarDay(seed) - firstDay, 7);

      for(int k = 0; k <= 6 - index; k++) {
         LocalDate d = seed.plusDays(k);

         if(selected.contains(calendarDay(d)) && slot(d) >= curr) {
            return slot(d);
         }
      }

      LocalDate start = seed.minusDays(index).plusDays(7L * interval);
      LocalDate today = Instant.ofEpochMilli(curr).atZone(UTC).toLocalDate();

      // days before curr's date cannot qualify, so the walk may begin there
      LocalDate d = start.isBefore(today.minusDays(1)) ? today.minusDays(1) : start;

      for(int k = 0; k < 20; k++) {
         if(selected.contains(calendarDay(d)) && slot(d) >= curr) {
            return slot(d);
         }

         d = d.plusDays(1);
      }

      throw new AssertionError("no day found");
   }

   /** The EVERY_WEEK code of before Bug #79053, for interval 1, seeded by the clock. */
   private static long oldWeekly(int[] days, long now, long curr) {
      Calendar cal1 = Calendar.getInstance(TimeZone.getTimeZone(UTC));
      cal1.setTimeInMillis(now);
      cal1.set(Calendar.HOUR_OF_DAY, 9);
      cal1.set(Calendar.MINUTE, 0);
      cal1.set(Calendar.SECOND, 0);

      for(int day = cal1.get(Calendar.DAY_OF_WEEK); day < 8; day++) {
         cal1.set(Calendar.DAY_OF_WEEK, day);

         if(contains(days, day) && cal1.getTimeInMillis() >= curr) {
            return cal1.getTimeInMillis();
         }
      }

      boolean all = true;

      for(int day = 1; day < 8; day++) {
         all &= contains(days, day);
      }

      if(!all || contains(days, days[0])) {
         while(contains(days, cal1.get(Calendar.DAY_OF_WEEK)) &&
            cal1.get(Calendar.DAY_OF_WEEK) != days[0])
         {
            cal1.add(Calendar.DATE, 1);
         }
      }

      while(!contains(days, cal1.get(Calendar.DAY_OF_WEEK)) || cal1.getTimeInMillis() < curr) {
         cal1.add(Calendar.DATE, 1);
      }

      return cal1.getTimeInMillis();
   }

   private static boolean contains(int[] days, int day) {
      for(int d : days) {
         if(d == day) {
            return true;
         }
      }

      return false;
   }
}
