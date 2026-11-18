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

import inetsoft.sree.schedule.quartz.JobCompletionListener;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import org.junit.jupiter.api.*;
import org.quartz.*;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81692 -- the task-finish audit record of a task stopped by a user carries a reason,
 * instead of a failure with a blank error.
 */
@Tag("core")
class JobCompletionListenerStatusTest {
   private SchedulerTestHarness harness;
   private Field auditInstanceField;
   private Audit originalAudit;
   private final AtomicReference<ActionRecord> recorded = new AtomicReference<>();

   @BeforeEach
   void setUp() throws Exception {
      harness = new SchedulerTestHarness();
      auditInstanceField = Audit.class.getDeclaredField("INSTANCE");
      auditInstanceField.setAccessible(true);
      originalAudit = (Audit) auditInstanceField.get(null);
      auditInstanceField.set(null, new Audit() {
         @Override
         public void auditAction(ActionRecord record, Principal principal) {
            recorded.set(record);
         }
      });
   }

   @AfterEach
   void tearDown() throws Exception {
      auditInstanceField.set(null, originalAudit);
      harness.close();
   }

   private void complete(boolean cancelled, JobExecutionException failure) throws Exception {
      ScheduleTask task = new ScheduleTask("t-81692-" + System.nanoTime());
      task.setOwner(SchedulerTestHarness.TEST_OWNER);
      task.addAction(mock(ScheduleAction.class));

      JobDetail detail = JobBuilder.newJob(Job.class)
         .withIdentity(task.getTaskId(), Scheduler.GROUP_NAME).build();
      detail.getJobDataMap().put(ScheduleTask.class.getName(), task);

      if(cancelled) {
         detail.getJobDataMap().put("inetsoft.cancelled", Boolean.TRUE);
      }

      JobExecutionContext context = mock(JobExecutionContext.class);
      when(context.getJobDetail()).thenReturn(detail);
      when(context.getMergedJobDataMap()).thenReturn(new JobDataMap());
      when(context.getFireTime()).thenReturn(new Date());
      when(context.getJobRunTime()).thenReturn(10L);
      when(context.getScheduler()).thenReturn(harness.getQuartz());

      new JobCompletionListener("TaskCompletionListener").jobWasExecuted(context, failure);
   }

   @Test
   void auditRecordOfAStoppedTaskCarriesAReason() throws Exception {
      complete(true, null);

      ActionRecord record = recorded.get();
      assertNotNull(record);
      assertEquals(ActionRecord.ACTION_STATUS_FAILURE, record.getActionStatus());
      assertNotNull(record.getActionError());
      assertTrue(record.getActionError().contains("The Task Was Stopped"),
                 record.getActionError());
   }

   @Test
   void auditRecordOfAFailedTaskKeepsItsError() throws Exception {
      complete(false, new JobExecutionException("boom"));

      ActionRecord record = recorded.get();
      assertEquals(ActionRecord.ACTION_STATUS_FAILURE, record.getActionStatus());
      assertTrue(record.getActionError().contains("boom"));
      assertFalse(record.getActionError().contains("The Task Was Stopped"));
   }

   @Test
   void auditRecordOfAnUnexpectedInterruptionIsNotLabelledStopped() throws Exception {
      complete(false, new JobExecutionException("interrupted unexpectedly"));

      ActionRecord record = recorded.get();
      assertEquals(ActionRecord.ACTION_STATUS_FAILURE, record.getActionStatus());
      assertTrue(record.getActionError().contains("interrupted unexpectedly"));
      assertFalse(record.getActionError().contains("The Task Was Stopped"));
   }

   @Test
   void auditRecordOfAFinishedTaskIsSuccess() throws Exception {
      complete(false, null);

      ActionRecord record = recorded.get();
      assertEquals(ActionRecord.ACTION_STATUS_SUCCESS, record.getActionStatus());
      assertFalse(record.getActionError().contains("The Task Was Stopped"));
   }
}
