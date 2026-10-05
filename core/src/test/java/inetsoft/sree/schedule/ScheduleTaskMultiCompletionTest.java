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
import org.quartz.*;
import org.quartz.listeners.JobListenerSupport;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static inetsoft.sree.schedule.Scheduler.Status.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #77582, a task with several completion conditions must run once every parent task has
 * finished since the dependent task last started, whatever order the parents run in.
 *
 * <p>Each parent is run to completion before the next step, so the parents never overlap unless
 * a test says so. Every wait is on the end of a listener pass (see {@link #runs}), not on a status
 * write, so a "does not fire" assertion starts only after the dependency check has been made.
 */
@Tag("slow")
class ScheduleTaskMultiCompletionTest {
   private SchedulerTestHarness harness;
   private final Map<String, AtomicInteger> runs = new ConcurrentHashMap<>();

   @BeforeEach
   void setUp() throws Exception {
      harness = new SchedulerTestHarness();
      ListenerManager listeners = harness.getQuartz().getListenerManager();
      JobListener completionListener = listeners.getJobListener("test-listener");
      listeners.removeJobListener("test-listener");

      // Runs before JobCompletionListener so that a dependent triggered by it always starts at
      // least a few milliseconds after the parent's recorded end time. Without the gap the two
      // times may fall in the same millisecond on a fast machine, which makes the "parent hasn't
      // finished since the dependent started" checks below depend on timing.
      listeners.addJobListener(new JobListenerSupport() {
         @Override
         public String getName() {
            return "gap-listener";
         }

         @Override
         public void jobWasExecuted(JobExecutionContext context, JobExecutionException e) {
            sleep(20);
         }
      });

      listeners.addJobListener(completionListener);

      // Runs after JobCompletionListener, counts completed listener passes per task.
      listeners.addJobListener(new JobListenerSupport() {
         @Override
         public String getName() {
            return "count-listener";
         }

         @Override
         public void jobWasExecuted(JobExecutionContext context, JobExecutionException e) {
            runs.computeIfAbsent(context.getJobDetail().getKey().getName(),
                                 k -> new AtomicInteger()).incrementAndGet();
         }
      });
   }

   @AfterEach
   void tearDown() {
      harness.close();
   }

   // m01
   @Test
   void parentsRunInOrder_dependentRunsOnce() throws Exception {
      ScheduleTask p1 = register(buildTask("p1-order"));
      ScheduleTask p2 = register(buildTask("p2-order"));
      ScheduleTask dep = register(buildTask("dep-order", p1, p2));

      run(p1, 1);
      assertRunsStay(dep, 0);
      run(p2, 1);
      waitForRuns(dep, 1);
      assertRunsStay(dep, 1);
   }

   // m02
   @Test
   void parentsRunInReverseOrder_dependentRunsOnce() throws Exception {
      ScheduleTask p1 = register(buildTask("p1-reverse"));
      ScheduleTask p2 = register(buildTask("p2-reverse"));
      ScheduleTask dep = register(buildTask("dep-reverse", p1, p2));

      run(p2, 1);
      assertRunsStay(dep, 0);
      run(p1, 1);
      waitForRuns(dep, 1);
      assertRunsStay(dep, 1);
   }

   // m03
   @Test
   void oneParentRunsAgain_dependentWaitsForTheOther() throws Exception {
      ScheduleTask p1 = register(buildTask("p1-rerun"));
      ScheduleTask p2 = register(buildTask("p2-rerun"));
      ScheduleTask dep = register(buildTask("dep-rerun", p1, p2));

      run(p1, 1);
      run(p2, 1);
      waitForRuns(dep, 1);

      run(p1, 2);
      assertRunsStay(dep, 1);
      run(p2, 2);
      waitForRuns(dep, 2);
      assertRunsStay(dep, 2);
   }

   // m04
   @Test
   void failedParent_blocksUntilItFinishes() throws Throwable {
      ScheduleAction action = mock(ScheduleAction.class);
      doThrow(new RuntimeException("simulated failure")).doNothing().when(action).run(any());
      ScheduleTask p1 = register(buildTask("p1-fail", action));
      ScheduleTask p2 = register(buildTask("p2-fail"));
      ScheduleTask dep = register(buildTask("dep-fail", p1, p2));

      run(p1, 1);
      assertEquals(FAILED, harness.getDao().getStatus(p1.getTaskId()).getStatus());
      run(p2, 1);
      assertRunsStay(dep, 0);

      run(p1, 2);
      assertEquals(FINISHED, harness.getDao().getStatus(p1.getTaskId()).getStatus());
      waitForRuns(dep, 1);
   }

   // m05
   @Test
   void dependentNeverRan_oldParentStatusCounts() throws Exception {
      ScheduleTask p1 = register(buildTask("p1-old"));
      ScheduleTask p2 = register(buildTask("p2-old"));
      ScheduleTask dep = register(buildTask("dep-old", p1, p2));
      long weekAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
      harness.getDao().setStatus(p1.getTaskId(), FINISHED, weekAgo, weekAgo + 1000L, null, false);

      run(p2, 1);
      waitForRuns(dep, 1);
   }

   // m06
   @Test
   void dependentRunDirectly_parentsMustAllFinishAgain() throws Exception {
      ScheduleTask p1 = register(buildTask("p1-direct"));
      ScheduleTask p2 = register(buildTask("p2-direct"));
      ScheduleTask dep = register(buildTask("dep-direct", p1, p2));

      run(p1, 1);
      run(p2, 1);
      waitForRuns(dep, 1);

      run(dep, 2);
      run(p1, 2);
      assertRunsStay(dep, 2);
      run(p2, 2);
      waitForRuns(dep, 3);
   }

   // m07
   @Test
   void singleParent_dependentRunsEveryTime() throws Exception {
      ScheduleTask p1 = register(buildTask("p1-single"));
      ScheduleTask dep = register(buildTask("dep-single", p1));

      for(int i = 1; i <= 3; i++) {
         run(p1, i);
         waitForRuns(dep, i);
      }

      assertRunsStay(dep, 3);
   }

   // m08, the dependent's last start was recorded by a node whose clock is ahead
   @Test
   void singleParent_dependentStartInTheFuture_stillRuns() throws Exception {
      ScheduleTask p1 = register(buildTask("p1-skew"));
      ScheduleTask dep = register(buildTask("dep-skew", p1));
      long ahead = System.currentTimeMillis() + 5000L;
      harness.getDao().setStatus(dep.getTaskId(), FINISHED, ahead, ahead + 100L, null, false);

      run(p1, 1);
      waitForRuns(dep, 1);
   }

   // m09
   @Test
   void overlappingParents_dependentRunsOnce() throws Throwable {
      CountDownLatch release = new CountDownLatch(1);
      ScheduleTask p1 = register(buildTask("p1-overlap", blockingAction(release, 1)));
      ScheduleTask p2 = register(buildTask("p2-overlap"));
      ScheduleTask dep = register(buildTask("dep-overlap", p1, p2));

      harness.triggerNow(p1.getTaskId());
      harness.waitForStatus(p1.getTaskId(), STARTED, Duration.ofSeconds(10));
      run(p2, 1);
      assertRunsStay(dep, 0);

      release.countDown();
      waitForRuns(p1, 1);
      waitForRuns(dep, 1);
      assertRunsStay(dep, 1);
   }

   // m10, parents finish again while the dependent is still running
   @Test
   void dependentRunning_parentsFinishAgain_runsOnceMore() throws Throwable {
      CountDownLatch release = new CountDownLatch(1);
      ScheduleTask p1 = register(buildTask("p1-busy"));
      ScheduleTask p2 = register(buildTask("p2-busy"));
      ScheduleTask dep = buildTask("dep-busy", blockingAction(release, 1));
      dep.addCondition(new CompletionCondition(p1.getTaskId()));
      dep.addCondition(new CompletionCondition(p2.getTaskId()));
      registerSerial(dep);

      run(p1, 1);
      run(p2, 1);
      harness.waitForStatus(dep.getTaskId(), STARTED, Duration.ofSeconds(10));

      run(p1, 2);
      run(p2, 2);
      release.countDown();
      waitForRuns(dep, 2);
      assertRunsStay(dep, 2);
   }

   // the reported case: parents with their own daily time conditions, run manually one after
   // the other like Scheduler.runTask does (runNow), on two days in a row
   @Test
   void dailyParentsRunNow_dependentRunsOnceEachRound() throws Exception {
      ScheduleTask p1 = buildTask("p1-daily");
      p1.addCondition(TimeCondition.at(1, 30, 0));
      ScheduleTask p2 = buildTask("p2-daily");
      p2.addCondition(TimeCondition.at(2, 0, 0));
      register(p1);
      register(p2);
      ScheduleTask dep = register(buildTask("dep-daily", p1, p2));

      for(int i = 1; i <= 2; i++) {
         runNow(p1, i);
         assertRunsStay(dep, i - 1);
         runNow(p2, i);
         waitForRuns(dep, i);
         assertRunsStay(dep, i);
      }
   }

   @Test
   void disabledDependent_isNotTriggered() throws Exception {
      ScheduleTask p1 = register(buildTask("p1-disabled"));
      ScheduleTask p2 = register(buildTask("p2-disabled"));
      ScheduleTask dep = buildTask("dep-disabled", p1, p2);
      dep.setEnabled(false);
      register(dep);

      run(p1, 1);
      run(p2, 1);
      assertRunsStay(dep, 0);
   }

   // -----------------------------------------------------------------------
   // Helpers
   // -----------------------------------------------------------------------

   private ScheduleTask register(ScheduleTask task) throws SchedulerException {
      harness.registerTask(task);
      return task;
   }

   /**
    * Registers a task with a job that can't run concurrently, like the production
    * ScheduleTaskJob, so a trigger fired while the task is running is queued.
    */
   private void registerSerial(ScheduleTask task) throws SchedulerException {
      JobDetail job = JobBuilder.newJob(SerialTaskJob.class)
         .withIdentity(task.getTaskId(), Scheduler.GROUP_NAME)
         .storeDurably()
         .build();
      job.getJobDataMap().put(ScheduleTask.class.getName(), task);
      harness.getQuartz().addJob(job, false);
   }

   /** Triggers the task and waits until the listeners have handled its n-th run. */
   private void run(ScheduleTask task, int n) throws SchedulerException {
      harness.triggerNow(task.getTaskId());
      waitForRuns(task, n);
   }

   /** Triggers the task with the job data Scheduler.runTask() uses for a manual run. */
   private void runNow(ScheduleTask task, int n) throws SchedulerException {
      JobDataMap data = new JobDataMap();
      data.put("runNow", true);
      harness.getQuartz().triggerJob(new JobKey(task.getTaskId(), Scheduler.GROUP_NAME), data);
      waitForRuns(task, n);
   }

   private void waitForRuns(ScheduleTask task, int n) {
      await().atMost(Duration.ofSeconds(10)).until(() -> runs(task) >= n);
      assertEquals(n, runs(task), task.getTaskId());
   }

   /**
    * Asserts that the run count doesn't change. A dependent is triggered synchronously in the
    * listener pass of its parent, which has already ended here, so it would start well within
    * this window.
    */
   private void assertRunsStay(ScheduleTask task, int n) {
      await().during(Duration.ofMillis(700)).atMost(Duration.ofSeconds(5))
         .until(() -> runs(task) == n);
   }

   private int runs(ScheduleTask task) {
      AtomicInteger count = runs.get(task.getTaskId());
      return count == null ? 0 : count.get();
   }

   private ScheduleTask buildTask(String name, ScheduleTask... parents) {
      return buildTask(name, mock(ScheduleAction.class), parents);
   }

   private ScheduleTask buildTask(String name, ScheduleAction action, ScheduleTask... parents) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(SchedulerTestHarness.TEST_OWNER);
      task.setEnabled(true);
      task.addAction(action);

      for(ScheduleTask parent : parents) {
         task.addCondition(new CompletionCondition(parent.getTaskId()));
      }

      return task;
   }

   /** An action whose first {@code blocked} runs wait for the latch. */
   private ScheduleAction blockingAction(CountDownLatch release, int blocked) throws Throwable {
      AtomicInteger calls = new AtomicInteger();
      ScheduleAction action = mock(ScheduleAction.class);
      doAnswer(inv -> {
         if(calls.incrementAndGet() <= blocked) {
            assertTrue(release.await(30, TimeUnit.SECONDS));
         }

         return null;
      }).when(action).run(any());
      return action;
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      }
      catch(InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }

   @DisallowConcurrentExecution
   public static class SerialTaskJob extends SchedulerTestHarness.HarnessTaskJob {
   }
}
