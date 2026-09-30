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

import inetsoft.sree.schedule.quartz.TimeConditionTriggerImpl;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for Redmine Bug #77297: when a task's stop-on date is the day of a fire, the
 * trigger end time was the stop-on date at the time of day of the fire (with the stop-on date's
 * milliseconds), which is not after the fire time (the fire time carries the start date's
 * milliseconds), so the trigger was reported as "Not scheduled" and never acquired.
 */
@Tag("core")
class SchedulerStopOnDayTest {
   // the scheduler thread acquires triggers up to this far ahead of now (Scheduler.IDLE_WAIT_TIME)
   private static final long IDLE_WAIT_TIME = 20000L;
   private static final long HOUR = 3600000L;
   private static final long DAY = 24 * HOUR;

   private TimeZone originalDefault;

   @BeforeEach
   void save() {
      originalDefault = TimeZone.getDefault();
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
   }

   @AfterEach
   void restore() {
      TimeZone.setDefault(originalDefault);
   }

   @Test
   void sameDayStopOnAtFireIsScheduled() {
      long fire = utc(2026, Calendar.OCTOBER, 1, 9, 0, 0);
      // start date now - 60s with arbitrary millis, stop on one hour after the fire (P1-22)
      TimeConditionTriggerImpl trigger =
         schedule(TimeCondition.at(new Date(fire)), fire, fire - 3600000L + 719, fire + HOUR, null);

      assertFiresAt(trigger, fire);
   }

   @Test
   void sameDayStopOnFireIsScheduledRegardlessOfMillis() {
      long fire = utc(2026, Calendar.OCTOBER, 1, 9, 0, 0);

      // stop-on millis before, equal to and after the start millis
      assertFiresAt(schedule(TimeCondition.at(new Date(fire)), fire, fire - 60000L + 900,
                             fire + HOUR + 100, null), fire);
      assertFiresAt(schedule(TimeCondition.at(new Date(fire)), fire, fire - 60000L,
                             fire + HOUR, null), fire);
      assertFiresAt(schedule(TimeCondition.at(new Date(fire)), fire, fire - 60000L + 100,
                             fire + HOUR + 900, null), fire);
      // stop on the same day before the time of the fire
      assertFiresAt(schedule(TimeCondition.at(new Date(fire)), fire, fire - 60000L,
                             fire - HOUR, null), fire);
   }

   @Test
   void sameDayStopOnUsesTaskTimeZone() {
      // 2026-10-01 21:00 EDT == 2026-10-02 01:00 UTC
      long fire = utc(2026, Calendar.OCTOBER, 2, 1, 0, 0);
      TimeCondition condition = TimeCondition.at(new Date(fire));
      condition.setTimeZone(TimeZone.getTimeZone("America/New_York"));

      // stop on 2026-10-01 19:30 EDT == 2026-10-01 23:30 UTC, the same day as the fire in the
      // task time zone but the day before it in UTC
      TimeConditionTriggerImpl trigger = schedule(condition, fire, fire - 60000L + 500,
                                                  fire - 90 * 60000L, "America/New_York");
      assertFiresAt(trigger, fire);

      // the same stop-on date is the day before the fire in the JVM time zone (UTC) used when
      // the task has no time zone, so the end time is unchanged
      long fireUtc = utc(2026, Calendar.OCTOBER, 2, 1, 0, 0);
      trigger = schedule(TimeCondition.at(new Date(fireUtc)), fireUtc, fireUtc - 60000L,
                         fireUtc - 90 * 60000L, null);
      assertEquals(utc(2026, Calendar.OCTOBER, 1, 1, 0, 0), trigger.getEndTime().getTime());
   }

   @Test
   void nextDayStopOnIsUnchanged() {
      long fire = utc(2026, Calendar.OCTOBER, 1, 9, 0, 0);
      long end = fire + DAY - 2 * HOUR + 719;
      TimeConditionTriggerImpl trigger =
         schedule(TimeCondition.at(new Date(fire)), fire, fire - 60000L, end, null);

      // the stop-on day at the time of the fire, with the stop-on millis
      assertEquals(fire + DAY + 719, trigger.getEndTime().getTime());
      assertFiresAt(trigger, fire);
   }

   @Test
   void dailyTaskStopOnDayIsUnchangedExceptForTheFirstFire() {
      TimeCondition daily = TimeCondition.at(9, 0, 0);
      long stopOn = utc(2026, Calendar.OCTOBER, 5, 0, 0, 0);

      // scheduled before the stop-on day: ends at the time of the fire on the stop-on day, so the
      // fire on the stop-on day is not run
      long fire = utc(2026, Calendar.OCTOBER, 1, 9, 0, 0);
      TimeConditionTriggerImpl trigger = schedule(daily, fire, fire - DAY, stopOn, null);
      assertEquals(utc(2026, Calendar.OCTOBER, 5, 9, 0, 0), trigger.getEndTime().getTime());
      assertFiresAt(trigger, fire);

      // scheduled on the stop-on day before the fire: that fire is run, the next one is not
      fire = utc(2026, Calendar.OCTOBER, 5, 9, 0, 0);
      trigger = schedule(daily, fire, fire - DAY, stopOn, null);
      assertFiresAt(trigger, fire);
      assertTrue(fire + DAY > trigger.getEndTime().getTime());

      // scheduled on the stop-on day after the fire: the next fire is the day after, not run
      fire = utc(2026, Calendar.OCTOBER, 6, 9, 0, 0);
      trigger = schedule(daily, fire, fire - 2 * DAY, stopOn, null);
      assertEquals(utc(2026, Calendar.OCTOBER, 5, 9, 0, 0), trigger.getEndTime().getTime());
      assertTrue(trigger.computeFirstFireTime(null).getTime() > trigger.getEndTime().getTime());
   }

   private static TimeConditionTriggerImpl schedule(TimeCondition condition, long next,
                                                    long startDate, long endDate,
                                                    String timeZone)
   {
      ScheduleTask task = mock(ScheduleTask.class);
      when(task.getStartDate()).thenReturn(new Date(startDate));
      when(task.getEndDate()).thenReturn(new Date(endDate));
      when(task.getTimeZone()).thenReturn(timeZone);
      when(task.getTaskId()).thenReturn("admin~;~host-org:b77297");

      TimeConditionTriggerImpl trigger = new TimeConditionTriggerImpl();
      trigger.setCondition(condition);
      Scheduler.setStartAndEndTime(next, trigger, task);
      return trigger;
   }

   private static void assertFiresAt(TimeConditionTriggerImpl trigger, long fire) {
      Date nextFireTime = trigger.computeFirstFireTime(null);
      long endTime = trigger.getEndTime().getTime();

      // the fire time is the fire of the condition, with the start date's millis
      assertEquals(fire / 1000, nextFireTime.getTime() / 1000);
      // counted by Scheduler.updateNextRun()
      assertTrue(nextFireTime.getTime() < endTime,
                 "end time " + endTime + " must be after the fire " + nextFireTime.getTime());
      // not filtered out by ClusterJobStore's TriggersPredicate when acquired ahead of the fire
      assertTrue(endTime >= nextFireTime.getTime() + IDLE_WAIT_TIME,
                 "end time " + endTime + " must allow acquiring the fire " + nextFireTime.getTime());
   }

   private static long utc(int year, int month, int day, int hour, int minute, int second) {
      Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
      cal.clear();
      cal.set(year, month, day, hour, minute, second);
      return cal.getTimeInMillis();
   }
}
