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
import inetsoft.sree.schedule.quartz.TimeConditionTriggerImpl;
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
 * Bug #79133 -- the weeks of an every-N-weeks condition start on the {@code week.start} of the
 * organization that owns the task, not on the site-wide value and not on the value of the
 * organization of the thread that happens to compute the next run.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  TimeConditionOrgWeekStartTest.StatusConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TimeConditionOrgWeekStartTest {
   /**
    * The status of the tasks: none, so a task is a never-run task, except the one that has run.
    */
   @org.springframework.context.annotation.Configuration
   static class StatusConfig {
      @org.springframework.context.annotation.Bean
      ScheduleStatusDao scheduleStatusDao() {
         ScheduleStatusDao dao = mock(ScheduleStatusDao.class);
         ScheduleStatusDao.Status status = mock(ScheduleStatusDao.Status.class);
         when(status.getLastScheduledStartTime()).thenReturn(ms("2027-07-21T09:00:00"));
         when(dao.getStatus(RAN_ID)).thenReturn(status);
         return dao;
      }
   }

   private static final String RAN_ID = "owner" + IdentityID.KEY_DELIMITER + "org79133mon:ran-79133";
   private static final ZoneId UTC = ZoneId.of("UTC");
   private static final String MON = "org79133mon";
   private static final String FRI = "org79133fri";
   private static final String NONE = "org79133none";
   private static final String OTHER = "org79133other";

   private Locale savedLocale;
   private TimeZone savedTimeZone;
   private String savedWeekStart;
   private java.security.Principal savedContextPrincipal;
   private java.security.Principal savedPrincipal;

   @BeforeEach
   void setUp() {
      savedLocale = Locale.getDefault();
      savedTimeZone = TimeZone.getDefault();
      savedWeekStart = SreeEnv.getProperty("week.start");
      savedContextPrincipal = ThreadContext.getContextPrincipal();
      savedPrincipal = ThreadContext.getPrincipal();
      Locale.setDefault(Locale.US);
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
      SreeEnv.setProperty("week.start", "");
      Tool.clearWeekStartCache();
      SreeEnv.setProperty("inetsoft.org." + MON + ".week.start", "monday");
      SreeEnv.setProperty("inetsoft.org." + FRI + ".week.start", "friday");
      SreeEnv.setProperty("inetsoft.org." + OTHER + ".week.start", "wednesday");
   }

   @AfterEach
   void tearDown() {
      Locale.setDefault(savedLocale);
      TimeZone.setDefault(savedTimeZone);
      ThreadContext.setContextPrincipal(savedContextPrincipal);
      ThreadContext.setPrincipal(savedPrincipal);
      SreeEnv.setProperty("week.start", savedWeekStart == null ? "" : savedWeekStart);
      Tool.clearWeekStartCache();

      for(String org : new String[] { MON, FRI, OTHER }) {
         SreeEnv.remove("inetsoft.org." + org + ".week.start");
      }
   }

   @Test
   void eachOrganizationFollowsItsOwnWeekStart() {
      runOnUnrelatedThread(() -> {
         // Sunday + Wednesday every 2 weeks, last run Wednesday 07-21
         assertEquals(ms("2027-07-25T09:00:00"), call(2, days(1, 4), "2027-07-21T09:00:00", MON));
         assertEquals(ms("2027-08-01T09:00:00"), call(2, days(1, 4), "2027-07-21T09:00:00", FRI));
         assertEquals(ms("2027-08-01T09:00:00"), call(2, days(1, 4), "2027-07-21T09:00:00", NONE));
         assertEquals(ms("2027-08-01T09:00:00"), call(2, days(1, 4), "2027-07-21T09:00:00", null));

         // Monday + Friday every 2 weeks, last run Monday 07-19
         assertEquals(ms("2027-07-23T09:00:00"), call(2, days(2, 6), "2027-07-19T09:00:00", MON));
         assertEquals(ms("2027-07-30T09:00:00"), call(2, days(2, 6), "2027-07-19T09:00:00", FRI));
         assertEquals(ms("2027-07-23T09:00:00"), call(2, days(2, 6), "2027-07-19T09:00:00", NONE));
      });
   }

   @Test
   void organizationWithoutOverrideUsesSiteWeekStart() {
      WeekStartUtil.withWeekStart("monday", () -> runOnUnrelatedThread(() -> {
         assertEquals(ms("2027-07-25T09:00:00"), call(2, days(1, 4), "2027-07-21T09:00:00", NONE));
         assertEquals(ms("2027-07-25T09:00:00"), call(2, days(1, 4), "2027-07-21T09:00:00", null));
         // the organization override still wins over the site value
         assertEquals(ms("2027-08-01T09:00:00"), call(2, days(1, 4), "2027-07-21T09:00:00", FRI));
      }));
   }

   @Test
   void organizationIdIsCaseInsensitive() {
      assertEquals(ms("2027-07-25T09:00:00"),
                   call(2, days(1, 4), "2027-07-21T09:00:00", MON.toUpperCase()));
   }

   @Test
   void twoArgumentCallStaysOnSiteWeekStart() {
      runOnUnrelatedThread(() -> {
         long last = ms("2027-07-21T09:00:00");
         assertEquals(ms("2027-08-01T09:00:00"),
                      weekly(2, days(1, 4)).getRetryTime(last + 30_000, last));
      });
   }

   @Test
   void firstFireAndLaterFiresAgree() {
      // every fire of the series anchors the weeks on the same day, so the series does not
      // shift between the first fire after the save and the later ones
      TimeCondition condition = weekly(2, days(1, 4));
      long next = condition.getRetryTime(ms("2027-07-21T09:00:30"), ms("2027-07-21T09:00:00"), MON);
      assertEquals(ms("2027-07-25T09:00:00"), next);
      // the next Monday weeks are 08-02..08-08: Wednesday 08-04, then Sunday 08-08
      long second = condition.getRetryTime(next + 30_000, next, MON);
      assertEquals(ms("2027-08-04T09:00:00"), second);
      assertEquals(ms("2027-08-08T09:00:00"),
                   condition.getRetryTime(second + 30_000, second, MON));
   }

   @Test
   void triggerResolvesTheOrganizationFromTheTaskId() {
      long last = ms("2027-07-21T09:00:00");
      TimeConditionTriggerImpl trigger = trigger("owner" + IdentityID.KEY_DELIMITER + MON +
                                                 ":task-1", weekly(2, days(1, 4)));
      assertEquals(new Date(ms("2027-07-25T09:00:00")), trigger.getFireTimeAfter(new Date(last)));

      trigger = trigger("owner" + IdentityID.KEY_DELIMITER + FRI + ":task-1",
                        weekly(2, days(1, 4)));
      assertEquals(new Date(ms("2027-08-01T09:00:00")), trigger.getFireTimeAfter(new Date(last)));

      // no organization in the id, the site value
      trigger = trigger("task-1", weekly(2, days(1, 4)));
      assertEquals(new Date(ms("2027-08-01T09:00:00")), trigger.getFireTimeAfter(new Date(last)));
   }

   @Test
   void taskOrganizationComesFromTheTaskId() {
      ScheduleTask task = new ScheduleTask("t");
      task.setOwner(new IdentityID("owner", MON));
      assertEquals(MON, task.getOrgId());
      assertEquals(MON, ScheduleTask.getOrgId(task.getTaskId()));
      assertNull(ScheduleTask.getOrgId("internal-task"));
      assertNull(ScheduleTask.getOrgId(null));
      assertNull(ScheduleTask.getOrgId("name" + IdentityID.KEY_DELIMITER + "null:x"));
   }

   @Test
   void neverRunTaskFirstFireFollowsItsOrganization() {
      // a brand-new task has no last run: the first fire and the later fires of one task must
      // use the same weeks, here the Monday weeks of its org
      runOnUnrelatedThread(() -> {
         long now = ms("2027-07-21T09:00:30");
         ScheduleTask task = task("owner", MON, "never-run-79133", weekly(2, days(1, 4)));
         long first = task.getRetryTime(now);
         assertEquals(ms("2027-07-25T09:00:00"), first);
         // the later fire, computed from the first one, stays in the same weeks
         long second = weekly(2, days(1, 4)).getRetryTime(first + 30_000, first, task.getOrgId());
         assertEquals(ms("2027-08-04T09:00:00"), second);
         assertTrue(task.hasNextRuntime(now));

         // another org on the same thread, same condition: its own weeks
         assertEquals(ms("2027-08-01T09:00:00"),
                      task("owner", FRI, "never-run-79133", weekly(2, days(1, 4))).getRetryTime(now));
         // an id without an org, the site value
         ScheduleTask site = new ScheduleTask("never-run-79133-site");
         site.addCondition(weekly(2, days(1, 4)));
         assertEquals(ms("2027-08-01T09:00:00"), site.getRetryTime(now));
      });
   }

   @Test
   void taskOrganizationOfColonCycleHostAndMvIds() {
      String d = IdentityID.KEY_DELIMITER;
      // ':' in the task name does not move the org
      assertEquals(MON, ScheduleTask.getOrgId("owner" + d + MON + ":a:b:c"));
      assertEquals(MON, ScheduleTask.getOrgId("owner" + d + MON + ":nightly__job"));
      // data cycle task: owner key, "__", name; the name may contain ':'
      assertEquals(MON, ScheduleTask.getOrgId("owner" + d + MON + "__cycle"));
      assertEquals(MON, ScheduleTask.getOrgId("owner" + d + MON + "__cycle:x"));
      // host org
      assertEquals("host-org", ScheduleTask.getOrgId("admin" + d + "host-org:task"));
      // the global org key and no org
      assertNull(ScheduleTask.getOrgId("admin" + d + "__GLOBAL__:task"));
      assertNull(ScheduleTask.getOrgId("admin" + d + "__GLOBAL__" + "__cycle"));
      // an MV task id has no owner key
      assertNull(ScheduleTask.getOrgId("MV Task:1^128^__NULL__^f1/vs1^host-org"));
      assertNull(ScheduleTask.getOrgId(""));

      ScheduleTask cycle = new ScheduleTask("cycle", ScheduleTask.Type.CYCLE_TASK);
      cycle.setOwner(new IdentityID("owner", MON));
      assertEquals(MON, cycle.getOrgId());
   }

   @Test
   void taskThatHasRunUsesItsLastRunAndOrganization() {
      runOnUnrelatedThread(() -> {
         // the same series as the never-run task, seeded by the stored last run
         ScheduleTask task = task("owner", MON, "ran-79133", weekly(2, days(1, 4)));
         assertEquals(RAN_ID, task.getTaskId());
         assertEquals(ms("2027-07-25T09:00:00"), task.getRetryTime(ms("2027-07-21T09:00:30")));
      });
   }

   private static ScheduleTask task(String owner, String org, String name, TimeCondition c) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(new IdentityID(owner, org));
      task.addCondition(c);
      return task;
   }

   @Test
   void everyWeekOfOneIsNotAffected() {
      runOnUnrelatedThread(() -> {
         // the N=1 result carries a few milliseconds of its own, compare it to the second
         long expected = call(1, days(1, 4), "2027-07-21T09:00:00", null) / 1000;
         assertEquals(ms("2027-07-25T09:00:00") / 1000, expected);

         for(String org : new String[] { MON, FRI, NONE, OTHER }) {
            assertEquals(expected, call(1, days(1, 4), "2027-07-21T09:00:00", org) / 1000, org);
         }
      });
   }

   @Test
   void threadStateIsRestored() {
      java.security.Principal before = ThreadContext.getContextPrincipal();
      java.security.Principal beforeThread = ThreadContext.getPrincipal();
      call(2, days(1, 4), "2027-07-21T09:00:00", MON);
      assertSame(before, ThreadContext.getContextPrincipal());
      assertSame(beforeThread, ThreadContext.getPrincipal());

      runOnUnrelatedThread(() -> {
         java.security.Principal p = ThreadContext.getContextPrincipal();
         call(2, days(1, 4), "2027-07-21T09:00:00", FRI);
         assertSame(p, ThreadContext.getContextPrincipal());
      });
   }

   /**
    * Run with a principal of an unrelated organization on the thread, whose own week.start
    * (Wednesday) would give different results, as a scheduler thread has after another task ran.
    */
   private void runOnUnrelatedThread(Runnable action) {
      OrganizationManager manager = mock(OrganizationManager.class);
      when(manager.getCurrentOrgID()).thenReturn(OTHER);
      SRPrincipal principal = new SRPrincipal(
         new IdentityID("unrelated", OTHER), new IdentityID[0], new String[0], OTHER, 1L);
      java.security.Principal oldContext = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(principal);

      try(MockedStatic<OrganizationManager> om = mockStatic(OrganizationManager.class)) {
         om.when(OrganizationManager::getInstance).thenReturn(manager);
         // the thread really reads the unrelated organization's value
         assertEquals("wednesday", SreeEnv.getProperty("week.start"));
         action.run();
         assertEquals("wednesday", SreeEnv.getProperty("week.start"));
      }
      finally {
         ThreadContext.setContextPrincipal(oldContext);
      }
   }

   private static long call(int interval, int[] days, String lastRun, String orgId) {
      long last = ms(lastRun);
      return weekly(interval, days).getRetryTime(last + 30_000, last, orgId);
   }

   private static TimeConditionTriggerImpl trigger(String taskId, TimeCondition condition) {
      TimeConditionTriggerImpl trigger = new TimeConditionTriggerImpl();
      trigger.setName(taskId + "-1");
      trigger.setCondition(condition);
      return trigger;
   }

   private static int[] days(int... days) {
      return days;
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
}
