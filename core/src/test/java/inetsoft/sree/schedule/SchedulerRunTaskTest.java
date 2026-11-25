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
import inetsoft.util.ThreadContext;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.quartz.*;

import java.lang.reflect.Field;
import java.security.Principal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@code Scheduler.runTask} (Run Now) and {@code Scheduler.cancelTask}: when a started status
 * still blocks a new run, what the triggered job receives, and which executing job gets
 * interrupted. The Scheduler is a mock that calls the real methods, on a mock Quartz scheduler
 * for the unit rows and on the real in-process Quartz of {@link SchedulerTestHarness} for the
 * end-to-end rows. The status goes through the harness' real {@code ScheduleStatusDao}.
 */
@Tag("slow")
class SchedulerRunTaskTest {
   private static final String TASK = "run-now-task";

   private SchedulerTestHarness harness;
   private Scheduler scheduler;
   private org.quartz.Scheduler quartz;
   private Field auditInstanceField;
   private Audit originalAudit;
   private Principal savedPrincipal;

   @BeforeEach
   void setUp() throws Exception {
      harness = new SchedulerTestHarness();
      savedPrincipal = ThreadContext.getContextPrincipal();

      auditInstanceField = Audit.class.getDeclaredField("INSTANCE");
      auditInstanceField.setAccessible(true);
      originalAudit = (Audit) auditInstanceField.get(null);
      auditInstanceField.set(null, new Audit() {
         @Override
         public void auditAction(ActionRecord record, Principal principal) {
         }
      });

      quartz = mock(org.quartz.Scheduler.class);
      scheduler = newScheduler(quartz, true);
      BlockingInterruptableJob.reset();
   }

   @AfterEach
   void tearDown() throws Exception {
      BlockingInterruptableJob.release.countDown();
      auditInstanceField.set(null, originalAudit);
      ThreadContext.setContextPrincipal(savedPrincipal);
      harness.close();
   }

   // ----- runTask: the started status that blocks a run -----

   @Test
   void pinnedCurrentBehaviorRunTaskDoesNothingWhenSchedulerIsNotRunning() throws Exception {
      scheduler = newScheduler(quartz, false);
      setStatus(Scheduler.Status.STARTED, System.currentTimeMillis());

      assertDoesNotThrow(() -> scheduler.runTask(TASK));

      verify(quartz, never()).triggerJob(any(JobKey.class), any(JobDataMap.class));
   }

   @Test
   void runTaskTriggersTheJobOfTheTaskWhenThereIsNoStatus() throws Exception {
      scheduler.runTask(TASK);

      verify(quartz).triggerJob(eq(new JobKey(TASK, Scheduler.GROUP_NAME)), any(JobDataMap.class));
   }

   @Test
   void runTaskRefusesWhileTheStartedStatusIsWithinTheTimeout() throws Exception {
      setStatus(Scheduler.Status.STARTED, System.currentTimeMillis() - 1000);

      SchedulerException error = runTaskExpectingRefusal("600000");

      assertEquals("Task is still running: " + TASK, error.getMessage());
      verify(quartz, never()).triggerJob(any(JobKey.class), any(JobDataMap.class));
   }

   @Test
   void runTaskIgnoresAStartedStatusOlderThanTheTimeout() throws Exception {
      setStatus(Scheduler.Status.STARTED, System.currentTimeMillis() - 600000 - 5000);

      runTaskWithTimeout("600000");

      verify(quartz).triggerJob(any(JobKey.class), any(JobDataMap.class));
   }

   @Test
   void runTaskAcceptsAStatusAsOldAsTheTimeoutAndRefusesAFreshOne() throws Exception {
      setStatus(Scheduler.Status.STARTED, System.currentTimeMillis() - 1500);
      runTaskWithTimeout("1500");
      verify(quartz).triggerJob(any(JobKey.class), any(JobDataMap.class));

      setStatus(Scheduler.Status.STARTED, System.currentTimeMillis() - 1500 + 60000);
      assertNotNull(runTaskExpectingRefusal("1500"));
      verify(quartz, times(1)).triggerJob(any(JobKey.class), any(JobDataMap.class));
   }

   @ParameterizedTest(name = "pinned current behavior: timeout [{0}] treats every started status " +
      "as expired")
   @ValueSource(strings = { "0", "-1" })
   void runTaskPinnedCurrentBehaviorNonPositiveTimeoutAlwaysAllowsANewRun(String timeout)
      throws Exception
   {
      setStatus(Scheduler.Status.STARTED, System.currentTimeMillis());

      runTaskWithTimeout(timeout);

      verify(quartz).triggerJob(any(JobKey.class), any(JobDataMap.class));
   }

   @Test
   void runTaskFallsBackToTheDefaultTimeoutWhenTheValueIsNotANumber() throws Exception {
      setStatus(Scheduler.Status.STARTED, System.currentTimeMillis());

      SchedulerException error = runTaskExpectingRefusal("abc");

      assertTrue(error.getMessage().contains("still running"), error.getMessage());
   }

   @ParameterizedTest(name = "a {0} status does not block a new run")
   @EnumSource(value = Scheduler.Status.class, names = { "FINISHED", "FAILED", "INTERRUPTED" })
   void runTaskOnlyAStartedStatusBlocks(Scheduler.Status status) throws Exception {
      setStatus(status, System.currentTimeMillis());

      runTaskWithTimeout("600000");

      verify(quartz).triggerJob(any(JobKey.class), any(JobDataMap.class));
   }

   @Test
   void runTaskTreatsAStartedStatusWithoutStartTimeAsStale() throws Exception {
      setStatus(Scheduler.Status.STARTED, 0L);

      runTaskWithTimeout("600000");

      verify(quartz).triggerJob(any(JobKey.class), any(JobDataMap.class));
   }

   // ----- runTask: the data handed to the job -----

   @Test
   void runTaskPassesRunNowAndTheCallingPrincipalToTheJob() throws Exception {
      Principal caller = mock(Principal.class);
      ThreadContext.setContextPrincipal(caller);

      scheduler.runTask(TASK);

      JobDataMap data = capturedData();
      assertEquals(Boolean.TRUE, data.get("runNow"));
      assertSame(caller, data.get("principal"));
   }

   @Test
   void runTaskWithoutAThreadPrincipalPassesNoPrincipalToTheJob() throws Exception {
      ThreadContext.setContextPrincipal(null);

      assertDoesNotThrow(() -> scheduler.runTask(TASK));

      JobDataMap data = capturedData();
      assertEquals(Boolean.TRUE, data.get("runNow"));
      assertFalse(data.get("principal") instanceof Principal);
   }

   @Test
   void runTaskIsRefusedWhileARunIsInProgressAndAcceptedAfterItFinished() throws Throwable {
      scheduler = newScheduler(harness.getQuartz(), true);
      ScheduleTask task = new ScheduleTask(TASK);
      task.setOwner(SchedulerTestHarness.TEST_OWNER);
      CountDownLatch release = new CountDownLatch(1);
      ScheduleAction blocking = mock(ScheduleAction.class);
      doAnswer(inv -> {
         release.await(10, TimeUnit.SECONDS);
         return null;
      }).when(blocking).run(any());
      task.addAction(blocking);
      harness.registerTask(task);

      try {
         harness.triggerNow(task.getTaskId());
         harness.waitForStatus(task.getTaskId(), Scheduler.Status.STARTED, Duration.ofSeconds(5));

         SchedulerException error = assertThrows(SchedulerException.class,
                                                 () -> scheduler.runTask(task.getTaskId()));
         assertTrue(error.getMessage().contains("Task is still running"), error.getMessage());

         release.countDown();
         ScheduleStatusDao.Status finished = harness.waitForStatus(
            task.getTaskId(), Scheduler.Status.FINISHED, Duration.ofSeconds(5));
         long firstStart = finished.getStartTime();

         assertDoesNotThrow(() -> scheduler.runTask(task.getTaskId()));

         await().atMost(Duration.ofSeconds(5)).until(() -> {
            ScheduleStatusDao.Status status = harness.getDao().getStatus(task.getTaskId());
            return status != null && status.getStartTime() != firstStart;
         });
      }
      finally {
         release.countDown();
      }
   }

   // ----- cancelTask -----

   @Test
   void pinnedCurrentBehaviorCancelTaskDoesNothingWhenSchedulerIsNotRunning() throws Exception {
      scheduler = newScheduler(quartz, false);
      InterruptableJob job = mock(InterruptableJob.class);
      JobExecutionContext executing = executing(TASK, job);
      when(quartz.getCurrentlyExecutingJobs()).thenReturn(List.of(executing));

      scheduler.cancelTask(TASK);

      verify(job, never()).interrupt();
   }

   @Test
   void cancelTaskInterruptsTheExecutingJobOfTheTaskExactlyOnce() throws Exception {
      InterruptableJob job = mock(InterruptableJob.class);
      JobExecutionContext executing = executing(TASK, job);
      when(quartz.getCurrentlyExecutingJobs()).thenReturn(List.of(executing));

      scheduler.cancelTask(TASK);

      verify(job, times(1)).interrupt();
   }

   @Test
   void cancelTaskLeavesJobsOfOtherTasksAlone() throws Exception {
      InterruptableJob other1 = mock(InterruptableJob.class);
      InterruptableJob other2 = mock(InterruptableJob.class);
      JobExecutionContext executing1 = executing("other-task-1", other1);
      JobExecutionContext executing2 = executing("other-task-2", other2);
      when(quartz.getCurrentlyExecutingJobs()).thenReturn(List.of(executing1, executing2));

      scheduler.cancelTask(TASK);

      verify(other1, never()).interrupt();
      verify(other2, never()).interrupt();
   }

   @Test
   void pinnedCurrentBehaviorCancelTaskIgnoresNonInterruptableJobsAndEmptyLists()
      throws Exception
   {
      Job plain = mock(Job.class);
      JobExecutionContext executing = executing(TASK, plain);
      when(quartz.getCurrentlyExecutingJobs()).thenReturn(List.of(executing));
      assertDoesNotThrow(() -> scheduler.cancelTask(TASK));

      when(quartz.getCurrentlyExecutingJobs()).thenReturn(List.of());
      assertDoesNotThrow(() -> scheduler.cancelTask(TASK));
   }

   @Test
   void cancelTaskInterruptsARunningJobAndTheNextRunFinishesNormally() throws Exception {
      scheduler = newScheduler(harness.getQuartz(), true);
      ScheduleTask task = new ScheduleTask(TASK);
      task.setOwner(SchedulerTestHarness.TEST_OWNER);
      task.addAction(mock(ScheduleAction.class));
      JobDetail job = JobBuilder.newJob(BlockingInterruptableJob.class)
         .withIdentity(task.getTaskId(), Scheduler.GROUP_NAME)
         .storeDurably()
         .build();
      job.getJobDataMap().put(ScheduleTask.class.getName(), task);
      harness.getQuartz().addJob(job, true);

      harness.triggerNow(task.getTaskId());
      assertTrue(BlockingInterruptableJob.started.await(5, TimeUnit.SECONDS));

      scheduler.cancelTask(task.getTaskId());

      assertTrue(BlockingInterruptableJob.interrupted.await(5, TimeUnit.SECONDS),
                 "the running job must receive interrupt()");
      harness.waitForStatus(task.getTaskId(), Scheduler.Status.INTERRUPTED,
                            Duration.ofSeconds(5));

      BlockingInterruptableJob.blockNextRun = false;
      harness.triggerNow(task.getTaskId());
      await().atMost(Duration.ofSeconds(5)).until(() -> {
         ScheduleStatusDao.Status status = harness.getDao().getStatus(task.getTaskId());
         return status != null && status.getStatus() == Scheduler.Status.FINISHED;
      });
   }

   // ----- helpers -----

   private static Scheduler newScheduler(org.quartz.Scheduler quartz, boolean running)
      throws Exception
   {
      Scheduler scheduler = mock(Scheduler.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      Field quartzField = Scheduler.class.getDeclaredField("scheduler");
      quartzField.setAccessible(true);
      quartzField.set(scheduler, quartz);
      Field runningField = Scheduler.class.getDeclaredField("running");
      runningField.setAccessible(true);
      runningField.set(scheduler, new AtomicBoolean(running));
      return scheduler;
   }

   private void setStatus(Scheduler.Status status, long startTime) {
      harness.getDao().setStatus(TASK, status, startTime, 0L, null, false);
   }

   private void runTaskWithTimeout(String timeout) throws Exception {
      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("schedule.task.timeout")).thenReturn(timeout);
         scheduler.runTask(TASK);
      }
   }

   private SchedulerException runTaskExpectingRefusal(String timeout) {
      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("schedule.task.timeout")).thenReturn(timeout);
         return assertThrows(SchedulerException.class, () -> scheduler.runTask(TASK));
      }
   }

   private JobDataMap capturedData() throws Exception {
      ArgumentCaptor<JobDataMap> captor = ArgumentCaptor.forClass(JobDataMap.class);
      verify(quartz).triggerJob(any(JobKey.class), captor.capture());
      return captor.getValue();
   }

   private static JobExecutionContext executing(String taskName, Job job) {
      JobDetail detail = JobBuilder.newJob(BlockingInterruptableJob.class)
         .withIdentity(taskName, Scheduler.GROUP_NAME)
         .build();
      JobExecutionContext context = mock(JobExecutionContext.class);
      when(context.getJobDetail()).thenReturn(detail);
      when(context.getJobInstance()).thenReturn(job);
      return context;
   }

   /** A job that blocks until it is interrupted and then marks its run as cancelled. */
   public static class BlockingInterruptableJob implements InterruptableJob {
      static volatile CountDownLatch started = new CountDownLatch(1);
      static volatile CountDownLatch interrupted = new CountDownLatch(1);
      static volatile CountDownLatch release = new CountDownLatch(1);
      static volatile boolean blockNextRun = true;
      private volatile boolean cancelled;

      static void reset() {
         started = new CountDownLatch(1);
         interrupted = new CountDownLatch(1);
         release = new CountDownLatch(1);
         blockNextRun = true;
      }

      @Override
      public void execute(JobExecutionContext context) {
         if(!blockNextRun) {
            return;
         }

         started.countDown();

         try {
            release.await(10, TimeUnit.SECONDS);
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
         }
         finally {
            if(cancelled) {
               context.getJobDetail().getJobDataMap().put("inetsoft.cancelled", true);
            }
         }
      }

      @Override
      public void interrupt() {
         cancelled = true;
         interrupted.countDown();
         release.countDown();
      }
   }
}
