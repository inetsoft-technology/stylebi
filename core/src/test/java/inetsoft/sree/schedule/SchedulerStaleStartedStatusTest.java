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

import inetsoft.sree.SreeEnv;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.quartz.JobKey;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #81917: {@code Scheduler.getScheduleActivities} (behind the public status and the EM task
 * list) must not report a task as running when its STARTED status is older than the task
 * timeout, because the node that executed the run died and nobody writes FINISHED.
 */
@Tag("core")
class SchedulerStaleStartedStatusTest {
   private static final String TASK = "stale-started-task";
   private static final long TIMEOUT = 180000L;

   private SchedulerTestHarness harness;
   private Scheduler scheduler;

   @BeforeEach
   void setUp() throws Exception {
      harness = new SchedulerTestHarness();
      org.quartz.Scheduler quartz = mock(org.quartz.Scheduler.class);
      when(quartz.getCurrentlyExecutingJobs()).thenReturn(Collections.emptyList());
      when(quartz.getJobKeys(any())).thenReturn(
         Collections.singleton(new JobKey(TASK, Scheduler.GROUP_NAME)));

      scheduler = mock(Scheduler.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      Field quartzField = Scheduler.class.getDeclaredField("scheduler");
      quartzField.setAccessible(true);
      quartzField.set(scheduler, quartz);
      Field runningField = Scheduler.class.getDeclaredField("running");
      runningField.setAccessible(true);
      runningField.set(scheduler, new AtomicBoolean(true));
   }

   @AfterEach
   void tearDown() throws Exception {
      harness.close();
   }

   @Test
   void startedStatusOlderThanTheTimeoutIsFailedNotRunning() throws Exception {
      long start = System.currentTimeMillis() - TIMEOUT - 5000;
      setStarted(start);

      TaskActivity activity = activities("180000").get(TASK);

      assertEquals("Failed", activity.getLastRunStatus());
      assertEquals(start, activity.getLastRunStart());
      assertEquals(start + TIMEOUT, activity.getLastRunEnd());
      assertNotNull(activity.getError());
   }

   @Test
   void startedStatusWithinTheTimeoutIsStillRunning() throws Exception {
      long start = System.currentTimeMillis() - 1000;
      setStarted(start);

      TaskActivity activity = activities("180000").get(TASK);

      assertEquals("Running", activity.getLastRunStatus());
      assertEquals(start, activity.getLastRunStart());
      assertEquals(0L, activity.getLastRunEnd());
   }

   @Test
   void startedStatusNeverExpiresWithAnUnboundedTimeout() throws Exception {
      setStarted(System.currentTimeMillis() - 10 * TIMEOUT);

      TaskActivity activity = activities("0").get(TASK);

      assertEquals("Running", activity.getLastRunStatus());
   }

   @Test
   void finishedStatusIsUnchanged() throws Exception {
      long start = System.currentTimeMillis() - 2 * TIMEOUT;
      harness.getDao().setStatus(
         TASK, Scheduler.Status.FINISHED, start, start + 1000, null, false);

      TaskActivity activity = activities("180000").get(TASK);

      assertEquals("Finished", activity.getLastRunStatus());
      assertEquals(start + 1000, activity.getLastRunEnd());
   }

   private void setStarted(long start) {
      harness.getDao().setStatus(TASK, Scheduler.Status.STARTED, start, 0L, null, false);
   }

   private Map<String, TaskActivity> activities(String timeout) throws Exception {
      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("schedule.task.timeout")).thenReturn(timeout);
         return scheduler.getScheduleActivities();
      }
   }
}
