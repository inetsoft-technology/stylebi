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
package inetsoft.sree.schedule.quartz;

import inetsoft.sree.schedule.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.ThreadContext;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import org.junit.jupiter.api.*;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.lang.reflect.Field;
import java.security.Principal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * How {@code JobCompletionListener.jobWasExecuted} maps the outcome of a run (the "cancelled" flag
 * the job leaves in the job data and the exception, if any) to the stored status, the audit record
 * and the dependants that are triggered. The listener is called directly with a context built
 * from the run's data, on {@link SchedulerTestHarness}'s in-memory status storage and real Quartz.
 */
@Tag("core")
class JobCompletionListenerStatusTest {
   private SchedulerTestHarness harness;
   private JobCompletionListener listener;
   private Field auditInstanceField;
   private Audit originalAudit;
   private Principal savedPrincipal;
   private final List<ActionRecord> records = new CopyOnWriteArrayList<>();
   private final List<Principal> auditPrincipals = new CopyOnWriteArrayList<>();

   @BeforeEach
   void setUp() throws Exception {
      harness = new SchedulerTestHarness();
      listener = new JobCompletionListener("status-test-listener");
      savedPrincipal = ThreadContext.getContextPrincipal();

      auditInstanceField = Audit.class.getDeclaredField("INSTANCE");
      auditInstanceField.setAccessible(true);
      originalAudit = (Audit) auditInstanceField.get(null);
      auditInstanceField.set(null, new Audit() {
         @Override
         public void auditAction(ActionRecord record, Principal principal) {
            records.add(record);
            auditPrincipals.add(principal);
         }
      });
   }

   @AfterEach
   void tearDown() throws Exception {
      auditInstanceField.set(null, originalAudit);
      ThreadContext.setContextPrincipal(savedPrincipal);
      harness.close();
   }

   @Test
   void cancelledRunIsStoredAsInterruptedWithItsRunTimeAndNoError() throws Exception {
      ScheduleTask task = task("cancelled-run");
      JobDataMap data = jobData(task);
      data.put("inetsoft.cancelled", Boolean.TRUE);
      Date fired = new Date(System.currentTimeMillis() - 5000);

      listener.jobWasExecuted(context(task, data, new JobDataMap(), fired, 1234L), null);

      ScheduleStatusDao.Status status = harness.getDao().getStatus(task.getTaskId());
      assertEquals(Scheduler.Status.INTERRUPTED, status.getStatus());
      assertEquals(fired.getTime(), status.getStartTime());
      assertEquals(fired.getTime() + 1234L, status.getEndTime());
      assertNull(status.getError());
   }

   @Test
   void pinnedCurrentBehaviorCancelledRunThatAlsoFailedIsStoredAsInterruptedWithoutTheError()
      throws Exception
   {
      ScheduleTask task = task("cancelled-failed-run");
      JobDataMap data = jobData(task);
      data.put("inetsoft.cancelled", Boolean.TRUE);

      listener.jobWasExecuted(context(task, data, new JobDataMap(), new Date(), 10L),
                              new JobExecutionException("task failed after stop"));

      ScheduleStatusDao.Status status = harness.getDao().getStatus(task.getTaskId());
      assertEquals(Scheduler.Status.INTERRUPTED, status.getStatus());
      assertNull(status.getError(), "the original error text is not kept for a cancelled run");
   }

   @Test
   void runWithoutCancelledFlagAndWithoutErrorIsStoredAsFinished() throws Exception {
      ScheduleTask task = task("finished-run");

      listener.jobWasExecuted(
         context(task, jobData(task), new JobDataMap(), new Date(), 10L), null);

      assertEquals(Scheduler.Status.FINISHED,
                   harness.getDao().getStatus(task.getTaskId()).getStatus());
   }

   @Test
   void runWithoutCancelledFlagAndWithErrorIsStoredAsFailedWithTheErrorText() throws Exception {
      ScheduleTask task = task("failed-run");

      listener.jobWasExecuted(context(task, jobData(task), new JobDataMap(), new Date(), 10L),
                              new JobExecutionException("boom"));

      ScheduleStatusDao.Status status = harness.getDao().getStatus(task.getTaskId());
      assertEquals(Scheduler.Status.FAILED, status.getStatus());
      assertNotNull(status.getError());
      assertTrue(status.getError().contains("boom"), status.getError());
   }

   @Test
   void onlyBooleanTrueCountsAsTheCancelledFlag() throws Exception {
      ScheduleTask falseTask = task("flag-false-run");
      JobDataMap falseData = jobData(falseTask);
      falseData.put("inetsoft.cancelled", Boolean.FALSE);
      ScheduleTask textTask = task("flag-text-run");
      JobDataMap textData = jobData(textTask);
      textData.put("inetsoft.cancelled", "true");

      listener.jobWasExecuted(
         context(falseTask, falseData, new JobDataMap(), new Date(), 10L), null);
      listener.jobWasExecuted(
         context(textTask, textData, new JobDataMap(), new Date(), 10L), null);

      assertEquals(Scheduler.Status.FINISHED,
                   harness.getDao().getStatus(falseTask.getTaskId()).getStatus());
      assertEquals(Scheduler.Status.FINISHED,
                   harness.getDao().getStatus(textTask.getTaskId()).getStatus());
   }

   @Test
   void interruptedParentDoesNotTriggerDependantButFinishedParentDoes() throws Exception {
      ScheduleTask parent = task("dep-parent-interrupted");
      ScheduleTask child = task("dep-child-of-interrupted");
      child.addCondition(new CompletionCondition(parent.getTaskId()));
      harness.registerTask(child);

      JobDataMap data = jobData(parent);
      data.put("inetsoft.cancelled", Boolean.TRUE);
      listener.jobWasExecuted(context(parent, data, new JobDataMap(), new Date(), 10L), null);

      await().during(Duration.ofMillis(800)).atMost(Duration.ofSeconds(5))
         .until(() -> harness.getDao().getStatus(child.getTaskId()) == null);

      // control: the same wiring with a finished parent triggers the dependant
      ScheduleTask finishedParent = task("dep-parent-finished");
      ScheduleTask finishedChild = task("dep-child-of-finished");
      finishedChild.addCondition(new CompletionCondition(finishedParent.getTaskId()));
      harness.registerTask(finishedChild);

      listener.jobWasExecuted(
         context(finishedParent, jobData(finishedParent), new JobDataMap(), new Date(), 10L), null);

      harness.waitForStatus(finishedChild.getTaskId(), Scheduler.Status.FINISHED,
                            Duration.ofSeconds(5));
   }

   @Test
   void auditRecordOfAStoppedTaskCarriesAReason() throws Exception {
      ScheduleTask task = task("audited-stopped-run");
      JobDataMap data = jobData(task);
      data.put("inetsoft.cancelled", Boolean.TRUE);

      listener.jobWasExecuted(context(task, data, new JobDataMap(), new Date(), 10L), null);

      assertEquals(1, records.size());
      String error = records.get(0).getActionError();
      assertTrue(error != null && !error.isBlank(),
                 "a stopped task's audit record must say why it is not a success: " + error);
   }

   @Test
   void runWithoutRunNowKeyStoresStatusWithItsStartAsLastScheduledStart() throws Exception {
      ScheduleTask task = task("no-run-now-key");
      Date fired = new Date(System.currentTimeMillis() - 2000);

      assertDoesNotThrow(() -> listener.jobWasExecuted(
         context(task, jobData(task), new JobDataMap(), fired, 10L), null));

      ScheduleStatusDao.Status status = harness.getDao().getStatus(task.getTaskId());
      assertEquals(Scheduler.Status.FINISHED, status.getStatus());
      assertEquals(fired.getTime(), status.getLastScheduledStartTime());
   }

   @Test
   void internalTaskIsAuditedAsTheUserThatTriggeredItNotTheThreadsPrincipal() throws Exception {
      Principal triggerUser = new XPrincipal(new IdentityID("trigger-user", "host"));
      Principal threadUser = new XPrincipal(new IdentityID("thread-user", "host"));
      ThreadContext.setContextPrincipal(threadUser);
      // the harness' mock security provider knows no organization, which an audit record of a
      // real principal needs
      inetsoft.sree.security.AuthenticationProvider authentication = ConfigurationContext
         .getContext().getApplicationContext().getBean(inetsoft.sree.security.SecurityEngine.class)
         .getSecurityProvider();
      inetsoft.sree.security.Organization organization =
         mock(inetsoft.sree.security.Organization.class);
      when(organization.getName()).thenReturn("host");
      when(authentication.getOrganization(any())).thenReturn(organization);
      ScheduleTask task = new ScheduleTask(InternalScheduledTaskService.BALANCE_TASKS,
                                           ScheduleTask.Type.INTERNAL_TASK);
      task.setOwner(SchedulerTestHarness.TEST_OWNER);
      task.addAction(mock(ScheduleAction.class));
      JobDataMap merged = new JobDataMap();
      merged.put("principal", triggerUser);

      listener.jobWasExecuted(context(task, jobData(task), merged, new Date(), 10L), null);

      assertEquals(1, auditPrincipals.size());
      assertSame(triggerUser, auditPrincipals.get(0));
   }

   @Test
   void stoppedBackgroundMVStageOneRemovesItselfAndItsStageTwo() throws Exception {
      ScheduleTask stage1 = mvTask("mv-stop-stage1");
      ScheduleTask stage2 = mvTask("mv-stop-stage2");
      stage2.addCondition(new CompletionCondition(stage1.getTaskId()));
      harness.registerTask(stage1);
      harness.registerTask(stage2);

      JobDataMap data = jobData(stage1);
      data.put("inetsoft.cancelled", Boolean.TRUE);
      listener.jobWasExecuted(context(stage1, data, new JobDataMap(), new Date(), 10L), null);

      assertFalse(harness.getQuartz().checkExists(key(stage2)),
                  "stage 2 can never run once stage 1 was stopped");
      assertFalse(harness.getQuartz().checkExists(key(stage1)));
   }

   @Test
   void failedBackgroundMVStageOneRemovesStageTwoButKeepsItself() throws Exception {
      ScheduleTask stage1 = mvTask("mv-fail-stage1");
      ScheduleTask stage2 = mvTask("mv-fail-stage2");
      stage2.addCondition(new CompletionCondition(stage1.getTaskId()));
      harness.registerTask(stage1);
      harness.registerTask(stage2);

      listener.jobWasExecuted(context(stage1, jobData(stage1), new JobDataMap(), new Date(), 10L),
                              new JobExecutionException("boom"));

      assertFalse(harness.getQuartz().checkExists(key(stage2)));
      assertTrue(harness.getQuartz().checkExists(key(stage1)), "stage 1 keeps its error");
      assertEquals(Scheduler.Status.FAILED,
                   harness.getDao().getStatus(stage1.getTaskId()).getStatus());
   }

   @Test
   void stoppedOrdinaryTaskKeepsItsDependants() throws Exception {
      ScheduleTask parent = task("ordinary-stop-parent");
      parent.setDeleteIfNoMoreRun(true);
      ScheduleTask child = task("ordinary-stop-child");
      child.setDeleteIfNoMoreRun(true);
      child.addCondition(new CompletionCondition(parent.getTaskId()));
      harness.registerTask(parent);
      harness.registerTask(child);

      JobDataMap data = jobData(parent);
      data.put("inetsoft.cancelled", Boolean.TRUE);
      listener.jobWasExecuted(context(parent, data, new JobDataMap(), new Date(), 10L), null);

      assertTrue(harness.getQuartz().checkExists(key(child)));
      assertTrue(harness.getQuartz().checkExists(key(parent)));
   }

   private static org.quartz.JobKey key(ScheduleTask task) {
      return new org.quartz.JobKey(task.getTaskId(), Scheduler.GROUP_NAME);
   }

   private ScheduleTask mvTask(String name) {
      ScheduleTask task = new ScheduleTask(name, ScheduleTask.Type.MV_TASK);
      task.setOwner(SchedulerTestHarness.TEST_OWNER);
      task.setDeleteIfNoMoreRun(true);
      task.setEditable(false);
      task.addAction(mock(ScheduleAction.class));
      return task;
   }

   private ScheduleTask task(String name) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(SchedulerTestHarness.TEST_OWNER);
      task.addAction(mock(ScheduleAction.class));
      return task;
   }

   private static JobDataMap jobData(ScheduleTask task) {
      JobDataMap data = new JobDataMap();
      data.put(ScheduleTask.class.getName(), task);
      return data;
   }

   private JobExecutionContext context(ScheduleTask task, JobDataMap detailData,
                                       JobDataMap mergedData, Date fireTime, long runTime)
   {
      JobDetail detail = JobBuilder.newJob(SchedulerTestHarness.HarnessTaskJob.class)
         .withIdentity(task.getTaskId(), Scheduler.GROUP_NAME)
         .storeDurably()
         .build();
      detail.getJobDataMap().putAll(detailData);
      JobExecutionContext context = mock(JobExecutionContext.class);
      when(context.getJobDetail()).thenReturn(detail);
      when(context.getMergedJobDataMap()).thenReturn(mergedData);
      when(context.getFireTime()).thenReturn(fireTime);
      when(context.getJobRunTime()).thenReturn(runTime);
      when(context.getScheduler()).thenReturn(harness.getQuartz());
      return context;
   }
}
