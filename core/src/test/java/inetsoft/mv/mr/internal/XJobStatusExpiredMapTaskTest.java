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
package inetsoft.mv.mr.internal;

import inetsoft.mv.fs.FSConfig;
import inetsoft.mv.fs.FSService;
import inetsoft.mv.mr.XJob;
import inetsoft.mv.mr.XMapResult;
import inetsoft.mv.mr.XMapTask;
import inetsoft.mv.mr.XReduceTask;
import inetsoft.sree.SreeEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies what {@link XJobStatus#update()} does with a map task that outlives
 * {@code fs.map.expired}. There is no retry: the map task runs in the local
 * {@link XMapTaskPool} on the only node there is, so the job fails and the abandoned task is
 * cancelled. These tests pin that contract, and pin the failure message to name the property
 * that caused it rather than claiming a re-dispatch was attempted.
 */
@Tag("core")
class XJobStatusExpiredMapTaskTest {
   @Test
   void expiredMapTaskFailsTheJobNamingTheProperty() throws Exception {
      XJobStatus status = newStartedStatus();

      try(MockedStatic<FSService> fs = mockStatic(FSService.class);
          MockedStatic<SreeEnv> sree = mockStatic(SreeEnv.class);
          MockedStatic<XMapTaskPool> pool = mockStatic(XMapTaskPool.class))
      {
         FSConfig config = expiringConfig();
         fs.when(FSService::getConfig).thenReturn(config);

         assertTrue(status.update(), "an expired map task must complete the job");
         assertTrue(status.isCompleted());

         String reason = status.getReason();
         assertNotNull(reason);
         assertTrue(reason.contains("fs.map.expired"),
                    "the failure must name the property that caused it: " + reason);
         assertFalse(reason.contains("re-dispatched"),
                     "no re-dispatch is attempted, so the message must not claim one: " + reason);
      }
   }

   @Test
   void givingUpCancelsTheStillRunningMapTask() throws Exception {
      XJobStatus status = newStartedStatus();

      try(MockedStatic<FSService> fs = mockStatic(FSService.class);
          MockedStatic<SreeEnv> sree = mockStatic(SreeEnv.class);
          MockedStatic<XMapTaskPool> pool = mockStatic(XMapTaskPool.class))
      {
         FSConfig config = expiringConfig();
         fs.when(FSService::getConfig).thenReturn(config);

         status.update();

         // the expired task is still executing; its result is now discarded, so stop it
         pool.verify(() -> XMapTaskPool.cancel(eq(JOB_ID)));
      }
   }

   @Test
   void aMapTaskWithinItsDeadlineLeavesTheJobRunning() throws Exception {
      XJobStatus status = newStartedStatus();

      try(MockedStatic<FSService> fs = mockStatic(FSService.class);
          MockedStatic<SreeEnv> sree = mockStatic(SreeEnv.class);
          MockedStatic<XMapTaskPool> pool = mockStatic(XMapTaskPool.class))
      {
         FSConfig config = mock(FSConfig.class);
         when(config.getExpired()).thenReturn(Integer.MAX_VALUE);
         fs.when(FSService::getConfig).thenReturn(config);

         assertFalse(status.update(), "a task within its deadline must not complete the job");
         assertFalse(status.isCompleted());
         pool.verifyNoInteractions();
      }
   }

   /**
    * A task cancelled mid-scan returns the rows it had read so far as a block that looks
    * complete (SubMVQuery.execute breaks its row loops, then still calls group.complete()),
    * so a result arriving after the job has failed must not reach the reducer.
    */
   @Test
   void aResultArrivingAfterTheJobFailedIsNotMergedIntoTheReducer() throws Exception {
      XJobStatus status = newStartedStatus();
      XReduceTask reducer = mock(XReduceTask.class);
      set(status, "reducer", reducer);
      set(status, "bset", new java.util.HashSet<>(java.util.List.of("block-0")));

      try(MockedStatic<FSService> fs = mockStatic(FSService.class);
          MockedStatic<SreeEnv> sree = mockStatic(SreeEnv.class);
          MockedStatic<XMapTaskPool> pool = mockStatic(XMapTaskPool.class))
      {
         FSConfig config = expiringConfig();
         fs.when(FSService::getConfig).thenReturn(config);

         status.update();
         assertTrue(status.isCompleted());

         XMapResult late = mock(XMapResult.class);
         when(late.getXBlock()).thenReturn("block-0");
         when(late.getHost()).thenReturn("localhost");
         status.addResult(late);

         verify(reducer, never()).add(late);
      }
   }

   @Test
   void aResultForARunningJobStillReachesTheReducer() throws Exception {
      XJobStatus status = newStartedStatus();
      XReduceTask reducer = mock(XReduceTask.class);
      set(status, "reducer", reducer);
      set(status, "bset", new java.util.HashSet<>(java.util.List.of("block-0")));

      XMapResult result = mock(XMapResult.class);
      when(result.getXBlock()).thenReturn("block-0");
      when(result.getHost()).thenReturn("localhost");

      try(MockedStatic<SreeEnv> sree = mockStatic(SreeEnv.class)) {
         status.addResult(result);
      }

      verify(reducer).add(result);
   }

   /**
    * A streaming consumer already holds the live table handed out by an earlier
    * complete(true, false, ...); the failure path never completes the reducer, so without
    * this the consumer blocks on a table that is never terminated.
    */
   @Test
   void failingTheJobTerminatesTheReducerForAStreamingConsumer() throws Exception {
      XJobStatus status = newStartedStatus();
      XReduceTask reducer = mock(XReduceTask.class);
      set(status, "reducer", reducer);

      try(MockedStatic<FSService> fs = mockStatic(FSService.class);
          MockedStatic<SreeEnv> sree = mockStatic(SreeEnv.class);
          MockedStatic<XMapTaskPool> pool = mockStatic(XMapTaskPool.class))
      {
         FSConfig config = expiringConfig();
         fs.when(FSService::getConfig).thenReturn(config);

         status.update();

         verify(reducer).cancel();
         verify(reducer, never()).complete(org.mockito.ArgumentMatchers.anyBoolean());
      }
   }

   private static FSConfig expiringConfig() {
      FSConfig config = mock(FSConfig.class);
      when(config.getExpired()).thenReturn((int) MAP_TASK_DEADLINE);
      return config;
   }

   /**
    * Builds an XJobStatus holding one started, uncompleted map task, without going through
    * startJob() -- which needs a real block file system.
    */
   @SuppressWarnings("unchecked")
   private static XJobStatus newStartedStatus() throws Exception {
      XJob job = mock(XJob.class);
      when(job.getID()).thenReturn(JOB_ID);
      when(job.getXFile()).thenReturn("mv-file");
      when(job.isCancelled()).thenReturn(false);

      XMapTask task = mock(XMapTask.class);
      when(task.getHost()).thenReturn("localhost");
      when(task.getXBlock()).thenReturn("block-0");

      XJobStatus status = new XJobStatus(JOB_ID);
      set(status, "job", job);
      set(status, "started", System.currentTimeMillis());
      set(status, "wperiod", Integer.MAX_VALUE);

      XMapStatus mstatus = new XMapStatus(task);
      mstatus.start();
      // backdate the map task so it is deterministically past MAP_TASK_DEADLINE
      Field mstarted = XMapStatus.class.getDeclaredField("started");
      mstarted.setAccessible(true);
      mstarted.setLong(mstatus, System.currentTimeMillis() - 10 * MAP_TASK_DEADLINE);
      Field mmap = XJobStatus.class.getDeclaredField("mmap");
      mmap.setAccessible(true);
      ((Map<XMapStatus, XMapStatus>) mmap.get(status)).put(mstatus, mstatus);

      return status;
   }

   private static void set(XJobStatus status, String name, Object value) throws Exception {
      Field field = XJobStatus.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(status, value);
   }

   private static final String JOB_ID = "job-1";
   private static final long MAP_TASK_DEADLINE = 1000;
}
