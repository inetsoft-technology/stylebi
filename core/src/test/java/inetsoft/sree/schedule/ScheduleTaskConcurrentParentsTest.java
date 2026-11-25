/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #81706, a task with completion conditions on several parents must run once when the parents
 * finish within milliseconds of each other. A barrier before the completion listener starts the
 * listener passes of all the parents together, which is the interleaving that made every pass see
 * the other parents finished and trigger the dependant.
 */
@Tag("slow")
class ScheduleTaskConcurrentParentsTest {
   private SchedulerTestHarness harness;
   private final Map<String, AtomicInteger> runs = new ConcurrentHashMap<>();
   private volatile CyclicBarrier barrier;

   @BeforeEach
   void setUp() throws Exception {
      harness = new SchedulerTestHarness();
      ListenerManager listeners = harness.getQuartz().getListenerManager();
      JobListener completionListener = listeners.getJobListener("test-listener");
      listeners.removeJobListener("test-listener");

      // Holds each parent's listener pass until all the parents have reached it
      listeners.addJobListener(new JobListenerSupport() {
         @Override
         public String getName() {
            return "barrier-listener";
         }

         @Override
         public void jobWasExecuted(JobExecutionContext context, JobExecutionException e) {
            CyclicBarrier current = barrier;

            if(current != null && context.getJobDetail().getKey().getName().startsWith("p")) {
               try {
                  current.await(10, TimeUnit.SECONDS);
               }
               catch(Exception ex) {
                  throw new IllegalStateException(ex);
               }
            }
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
      barrier = null;
      harness.close();
   }

   @Test
   void twoParents_manyRounds_dependentRunsOncePerRound() throws Exception {
      ScheduleTask p1 = register(buildTask("p1-rounds"));
      ScheduleTask p2 = register(buildTask("p2-rounds"));
      ScheduleTask dep = register(buildTask("dep-rounds", p1, p2));

      for(int round = 1; round <= 30; round++) {
         runTogether(round, p1, p2);
         waitForRuns(dep, round);
         assertRunsStay(dep, round);
      }
   }

   @Test
   void threeParents_manyRounds_dependentRunsOncePerRound() throws Exception {
      ScheduleTask p1 = register(buildTask("p1-three"));
      ScheduleTask p2 = register(buildTask("p2-three"));
      ScheduleTask p3 = register(buildTask("p3-three"));
      ScheduleTask dep = register(buildTask("dep-three", p1, p2, p3));

      for(int round = 1; round <= 20; round++) {
         runTogether(round, p1, p2, p3);
         waitForRuns(dep, round);
         assertRunsStay(dep, round);
      }
   }

   // the dependant is still running when the parents finish together again
   @Test
   void dependentRunning_parentsFinishTogetherAgain_runsOnceMore() throws Throwable {
      CountDownLatch release = new CountDownLatch(1);
      ScheduleTask p1 = register(buildTask("p1-busy"));
      ScheduleTask p2 = register(buildTask("p2-busy"));
      ScheduleTask dep = buildTask("dep-busy", blockingAction(release, 1));
      dep.addCondition(new CompletionCondition(p1.getTaskId()));
      dep.addCondition(new CompletionCondition(p2.getTaskId()));
      registerSerial(dep);

      runTogether(1, p1, p2);
      harness.waitForStatus(dep.getTaskId(), Scheduler.Status.STARTED, Duration.ofSeconds(10));

      runTogether(2, p1, p2);
      release.countDown();
      waitForRuns(dep, 2);
      assertRunsStay(dep, 2);
   }

   // a manual run of the dependant between the parents is not claimed, but still counts (#77582)
   @Test
   void manualRunBetweenParents_dependentDoesNotRunAgain() throws Exception {
      ScheduleTask p1 = register(buildTask("p1-manual"));
      ScheduleTask p2 = register(buildTask("p2-manual"));
      ScheduleTask dep = register(buildTask("dep-manual", p1, p2));

      run(p1, 1);
      run(dep, 1);
      run(p2, 1);
      assertRunsStay(dep, 1);
   }

   // the claim is not lost when the dependant's own status is rewritten, and goes with the status
   // when the task is removed, so a recreated task starts unclaimed
   @Test
   void claim_survivesStatusWrites_clearedWithStatus() {
      ScheduleStatusDao dao = harness.getDao();
      assertNull(dao.getDependantClaim("dep-claim"));

      dao.setDependantClaim("dep-claim", Map.of("p1", 10L, "p2", 20L));
      dao.setStatus("dep-claim", Scheduler.Status.STARTED, 30L, 0L, null, false);
      dao.setStatus("dep-claim", Scheduler.Status.FINISHED, 30L, 40L, null, false);
      assertEquals(Map.of("p1", 10L, "p2", 20L), dao.getDependantClaim("dep-claim"));

      dao.clearStatus("dep-claim");
      assertNull(dao.getDependantClaim("dep-claim"));
   }

   // -----------------------------------------------------------------------
   // Helpers
   // -----------------------------------------------------------------------

   private ScheduleTask register(ScheduleTask task) throws SchedulerException {
      harness.registerTask(task);
      return task;
   }

   private void registerSerial(ScheduleTask task) throws SchedulerException {
      JobDetail job = JobBuilder.newJob(ScheduleTaskMultiCompletionTest.SerialTaskJob.class)
         .withIdentity(task.getTaskId(), Scheduler.GROUP_NAME)
         .storeDurably()
         .build();
      job.getJobDataMap().put(ScheduleTask.class.getName(), task);
      harness.getQuartz().addJob(job, false);
   }

   /** Triggers the parents and waits until the listeners have handled their n-th run. */
   private void runTogether(int n, ScheduleTask... parents) throws SchedulerException {
      barrier = new CyclicBarrier(parents.length);

      for(ScheduleTask parent : parents) {
         harness.triggerNow(parent.getTaskId());
      }

      for(ScheduleTask parent : parents) {
         waitForRuns(parent, n);
      }
   }

   private void run(ScheduleTask task, int n) throws SchedulerException {
      barrier = null;
      harness.triggerNow(task.getTaskId());
      waitForRuns(task, n);
   }

   private void waitForRuns(ScheduleTask task, int n) {
      await().atMost(Duration.ofSeconds(10)).until(() -> runs(task) >= n);
      assertEquals(n, runs(task), task.getTaskId());
   }

   /** Asserts that the run count doesn't change, so a second trigger would have run by now. */
   private void assertRunsStay(ScheduleTask task, int n) {
      await().during(Duration.ofMillis(250)).atMost(Duration.ofSeconds(5))
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
}
