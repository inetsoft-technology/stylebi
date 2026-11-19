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
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.ListenerManager;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.listeners.JobListenerSupport;

import java.lang.reflect.Field;
import java.security.Principal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * A task with several completion conditions must run once when its parents finish together, however
 * the listener passes of the parents overlap (Bug #77582 family). The listener is called directly
 * with a context built from each parent's run, on {@link SchedulerTestHarness}'s in-memory status
 * storage and real Quartz. Split from {@link JobCompletionListenerStatusTest} because every test
 * of those classes pays about half a second to start and stop the harness.
 */
@Tag("slow")
class JobCompletionListenerParentsTogetherTest {
   private SchedulerTestHarness harness;
   private JobCompletionListener listener;
   private Field auditInstanceField;
   private Audit originalAudit;
   private Principal savedPrincipal;
   private final Map<String, AtomicInteger> runs = new ConcurrentHashMap<>();
   private final Map<String, AtomicInteger> arrivals = new ConcurrentHashMap<>();
   private final Map<String, Long> starts = new ConcurrentHashMap<>();
   private final AtomicReference<CountDownLatch> gate =
      new AtomicReference<>(new CountDownLatch(0));

   @BeforeEach
   void setUp() throws Exception {
      harness = new SchedulerTestHarness();
      listener = new JobCompletionListener("parents-together-listener");
      savedPrincipal = ThreadContext.getContextPrincipal();

      auditInstanceField = Audit.class.getDeclaredField("INSTANCE");
      auditInstanceField.setAccessible(true);
      originalAudit = (Audit) auditInstanceField.get(null);
      auditInstanceField.set(null, new Audit() {
         @Override
         public void auditAction(ActionRecord record, Principal principal) {
         }
      });
   }

   @AfterEach
   void tearDown() throws Exception {
      gate.get().countDown();
      auditInstanceField.set(null, originalAudit);
      ThreadContext.setContextPrincipal(savedPrincipal);
      harness.close();
   }

   // ---------------------------------------------------------------------------------------
   // Two or more parents finish together: the dependant is triggered once (Bug #77582 family)
   //
   // The dependant's start (its STARTED record) is written only when its run begins, so a test
   // listener holds that moment ("gate-listener" runs before the real completion listener). With
   // the gate closed, every pass of a parent sees a dependant that has not started yet, which is
   // the window two parents finishing together can fall into. Runs of the dependant are counted by
   // the end of its listener passes, not by status writes.
   // ---------------------------------------------------------------------------------------

   @Test
   void twoParentsFinishTogether_dependantStartHeld_dependantRunsExactlyOnce() throws Exception {
      installListeners(true);
      ScheduleTask p1 = parent("gated-two-p1");
      ScheduleTask p2 = parent("gated-two-p2");
      ScheduleTask child = dependant("gated-two-child", p1, p2);
      markFinished(p1);
      markFinished(p2);

      finish(p1);
      finish(p2);
      releaseGate();
      awaitSettled(child);

      assertEquals(1, runsOf(child),
                   "the dependant must run once for the two parents that finished together");
   }

   @Test
   void twoParentsFinishTogetherInReverseOrder_dependantStartHeld_dependantRunsExactlyOnce()
      throws Exception
   {
      installListeners(true);
      ScheduleTask p1 = parent("gated-reverse-p1");
      ScheduleTask p2 = parent("gated-reverse-p2");
      ScheduleTask child = dependant("gated-reverse-child", p1, p2);
      markFinished(p1);
      markFinished(p2);

      finish(p2);
      finish(p1);
      releaseGate();
      awaitSettled(child);

      assertEquals(1, runsOf(child),
                   "the dependant must run once for the two parents that finished together");
   }

   @Test
   void secondParentFinishesAfterTheDependantStarted_dependantRunsOnlyOnce() throws Exception {
      installListeners(false);
      ScheduleTask p1 = parent("control-two-p1");
      ScheduleTask p2 = parent("control-two-p2");
      ScheduleTask child = dependant("control-two-child", p1, p2);
      long longAgo = System.currentTimeMillis() - 5000;
      markFinished(p1, longAgo);
      markFinished(p2, longAgo);

      finish(p1, longAgo);
      awaitSettled(child);
      assertEquals(1, runsOf(child));
      finish(p2, System.currentTimeMillis());
      awaitSettled(child);

      assertEquals(1, runsOf(child),
                   "the first parent was already used by the first run, so the second parent's "
                      + "finish alone must not run the dependant again");
   }

   @Test
   void threeParentsFinishTogether_dependantStartHeld_dependantRunsExactlyOnce() throws Exception {
      installListeners(true);
      ScheduleTask p1 = parent("gated-three-p1");
      ScheduleTask p2 = parent("gated-three-p2");
      ScheduleTask p3 = parent("gated-three-p3");
      ScheduleTask child = dependant("gated-three-child", p1, p2, p3);
      markFinished(p1);
      markFinished(p2);
      markFinished(p3);

      finish(p1);
      finish(p2);
      finish(p3);
      releaseGate();
      awaitSettled(child);

      assertEquals(1, runsOf(child),
                   "the dependant must run once for the three parents that finished together");
   }

   @Test
   void threeParents_oneParentNeverRan_dependantRunsOnlyWhenTheThirdFinishes() throws Exception {
      installListeners(false);
      ScheduleTask p1 = parent("never-ran-p1");
      ScheduleTask p2 = parent("never-ran-p2");
      ScheduleTask p3 = parent("never-ran-p3");
      ScheduleTask child = dependant("never-ran-child", p1, p2, p3);

      finish(p1);
      finish(p2);
      assertNeverTriggered(child);

      finish(p3);
      awaitSettled(child);
      assertEquals(1, runsOf(child));
   }

   @Test
   void twoParentsFinishOnTwoThreadsAtOnce_dependantRunsExactlyOnceEveryRound() throws Exception {
      installListeners(true);
      int rounds = 50;
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
      List<String> wrong = new ArrayList<>();
      int zero = 0;
      int twice = 0;

      for(int round = 0; round < rounds && System.nanoTime() < deadline; round++) {
         gate.set(new CountDownLatch(1));
         ScheduleTask p1 = parent("race-two-p1-" + round);
         ScheduleTask p2 = parent("race-two-p2-" + round);
         ScheduleTask child = dependant("race-two-child-" + round, p1, p2);
         JobExecutionContext c1 = finishedContext(p1);
         JobExecutionContext c2 = finishedContext(p2);

         runTogether(() -> listener.jobWasExecuted(c1, null),
                     () -> listener.jobWasExecuted(c2, null));
         releaseGate();
         awaitSettled(child);

         int count = runsOf(child);
         zero += count == 0 ? 1 : 0;
         twice += count > 1 ? 1 : 0;

         if(count != 1) {
            wrong.add("round " + round + ": " + count + " runs");
         }
      }

      assertTrue(wrong.isEmpty(),
                 "the dependant must run exactly once every round, wrong rounds: " + wrong);
   }

   @Test
   void threeParentsFinishOnThreeThreadsAtOnce_dependantRunsExactlyOnceEveryRound()
      throws Exception
   {
      installListeners(true);
      int rounds = 30;
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
      List<String> wrong = new ArrayList<>();
      int zero = 0;
      int more = 0;

      for(int round = 0; round < rounds && System.nanoTime() < deadline; round++) {
         gate.set(new CountDownLatch(1));
         ScheduleTask p1 = parent("race-three-p1-" + round);
         ScheduleTask p2 = parent("race-three-p2-" + round);
         ScheduleTask p3 = parent("race-three-p3-" + round);
         ScheduleTask child = dependant("race-three-child-" + round, p1, p2, p3);
         JobExecutionContext c1 = finishedContext(p1);
         JobExecutionContext c2 = finishedContext(p2);
         JobExecutionContext c3 = finishedContext(p3);

         runTogether(() -> listener.jobWasExecuted(c1, null),
                     () -> listener.jobWasExecuted(c2, null),
                     () -> listener.jobWasExecuted(c3, null));
         releaseGate();
         awaitSettled(child);

         int count = runsOf(child);
         zero += count == 0 ? 1 : 0;
         more += count > 1 ? 1 : 0;

         if(count != 1) {
            wrong.add("round " + round + ": " + count + " runs");
         }
      }

      assertTrue(wrong.isEmpty(),
                 "the dependant must run exactly once every round, wrong rounds: " + wrong);
   }

   @Test
   void dependantAlreadyRunning_twoParentsFinishAgain_dependantIsTriggeredExactlyOnceMore()
      throws Exception
   {
      installListeners(true);
      ScheduleTask p1 = parent("busy-p1");
      ScheduleTask p2 = parent("busy-p2");
      ScheduleTask child = dependant("busy-child", p1, p2);
      long childStart = System.currentTimeMillis() - 3000;
      long parentStart = System.currentTimeMillis() - 1000;
      harness.getDao().setStatus(child.getTaskId(), Scheduler.Status.STARTED, childStart, 0L, null,
                                 false);
      markFinished(p1, parentStart);
      markFinished(p2, parentStart);

      finish(p1, parentStart);
      finish(p2, parentStart);
      releaseGate();
      awaitSettled(child);

      assertEquals(1, runsOf(child),
                   "the parents finished after the running dependant started, so it needs one "
                      + "more run, not one per parent");
   }

   @Test
   void failedParentAndFinishedParentAtOnce_dependantWaitsUntilTheFailedParentFinishes()
      throws Exception
   {
      installListeners(true);
      ScheduleTask p1 = parent("failed-at-once-p1");
      ScheduleTask p2 = parent("failed-at-once-p2");
      ScheduleTask child = dependant("failed-at-once-child", p1, p2);
      JobExecutionContext failed = finishedContext(p1);
      JobExecutionContext ok = finishedContext(p2);

      runTogether(() -> listener.jobWasExecuted(failed, new JobExecutionException("boom")),
                  () -> listener.jobWasExecuted(ok, null));
      assertNeverTriggered(child);

      finish(p1);
      releaseGate();
      awaitSettled(child);
      assertEquals(1, runsOf(child));
   }

   @Test
   void cancelledParentAndFinishedParentAtOnce_dependantWaitsUntilTheCancelledParentFinishes()
      throws Exception
   {
      installListeners(true);
      ScheduleTask p1 = parent("cancelled-at-once-p1");
      ScheduleTask p2 = parent("cancelled-at-once-p2");
      ScheduleTask child = dependant("cancelled-at-once-child", p1, p2);
      JobDataMap cancelledData = jobData(p1);
      cancelledData.put("inetsoft.cancelled", Boolean.TRUE);
      JobExecutionContext cancelled = context(p1, cancelledData, new JobDataMap(),
                                              new Date(System.currentTimeMillis() - 5000), 10L);
      JobExecutionContext ok = finishedContext(p2);

      runTogether(() -> listener.jobWasExecuted(cancelled, null),
                  () -> listener.jobWasExecuted(ok, null));
      assertNeverTriggered(child);

      finish(p1);
      releaseGate();
      awaitSettled(child);
      assertEquals(1, runsOf(child));
   }

   @Test
   void pinnedCurrentBehaviorDependantStartedInTheMillisecondAParentEndedIsTriggeredAgain()
      throws Exception
   {
      installListeners(false);
      ScheduleTask p1 = parent("same-ms-p1");
      ScheduleTask p2 = parent("same-ms-p2");
      ScheduleTask child = dependant("same-ms-child", p1, p2);
      long end = System.currentTimeMillis() - 5000;
      harness.getDao().setStatus(p2.getTaskId(), Scheduler.Status.FINISHED, end - 10, end, null,
                                 false);
      harness.getDao().setStatus(child.getTaskId(), Scheduler.Status.FINISHED, end, end + 5, null,
                                 false);

      finish(p1, end);
      awaitSettled(child);

      assertEquals(1, runsOf(child),
                   "a dependant whose last start equals the other parent's end counts as "
                      + "not started since, so it is triggered");
   }

   @Test
   void sameShortNamesInTwoOrganizations_onlyTheOrganizationsOwnParentsTriggerItsDependant()
      throws Exception
   {
      installListeners(false);
      IdentityID orgA = new IdentityID("scheduler-test", "orgA");
      IdentityID orgB = new IdentityID("scheduler-test", "orgB");
      ScheduleTask a1 = parent("shared-p1", orgA);
      ScheduleTask a2 = parent("shared-p2", orgA);
      ScheduleTask aChild = dependant("shared-child", orgA, a1, a2);
      ScheduleTask b1 = parent("shared-p1", orgB);
      ScheduleTask b2 = parent("shared-p2", orgB);
      ScheduleTask bChild = dependant("shared-child", orgB, b1, b2);
      assertNotEquals(aChild.getTaskId(), bChild.getTaskId());

      finish(a1);
      finish(a2);
      awaitSettled(aChild);
      awaitSettled(bChild);
      assertEquals(1, runsOf(aChild));
      assertEquals(0, runsOf(bChild));
      assertNeverTriggered(bChild);

      finish(b1);
      finish(b2);
      awaitSettled(aChild);
      awaitSettled(bChild);
      assertEquals(1, runsOf(aChild));
      assertEquals(1, runsOf(bChild));
   }

   @Test
   void principalOfAPoolThreadIsLeftAsFoundAfterAPassAndAfterADeleteIfNoMoreRunPass()
      throws Exception
   {
      installListeners(false);
      Principal threadUser = new XPrincipal(new IdentityID("pool-user", "host"));
      ScheduleTask normal = parent("principal-normal");
      ScheduleTask deleting = parent("principal-delete");
      deleting.setDeleteIfNoMoreRun(true);
      harness.registerTask(deleting);
      JobExecutionContext normalContext = finishedContext(normal);
      JobExecutionContext deletingContext = finishedContext(deleting);
      ExecutorService pool = Executors.newSingleThreadExecutor();

      try {
         List<Principal> after = pool.submit(() -> {
            ThreadContext.setContextPrincipal(threadUser);
            List<Principal> seen = new ArrayList<>();
            listener.jobWasExecuted(normalContext, null);
            seen.add(ThreadContext.getContextPrincipal());
            listener.jobWasExecuted(deletingContext, null);
            seen.add(ThreadContext.getContextPrincipal());
            return seen;
         }).get(10, TimeUnit.SECONDS);

         assertSame(threadUser, after.get(0));
         assertSame(threadUser, after.get(1));
      }
      finally {
         pool.shutdownNow();
         assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
      }
   }

   // ---- fixture of the tests above ----

   /**
    * Counts the passes of every job's listeners ("count-listener", after the completion listener)
    * and, with {@code gated}, holds each job's start until {@link #releaseGate()}.
    */
   private void installListeners(boolean gated) throws SchedulerException {
      ListenerManager listeners = harness.getQuartz().getListenerManager();
      JobListener completion = listeners.getJobListener("test-listener");
      listeners.removeJobListener("test-listener");
      gate.set(new CountDownLatch(gated ? 1 : 0));

      listeners.addJobListener(new JobListenerSupport() {
         @Override
         public String getName() {
            return "gate-listener";
         }

         @Override
         public void jobToBeExecuted(JobExecutionContext context) {
            arrivals.computeIfAbsent(context.getJobDetail().getKey().getName(),
                                     k -> new AtomicInteger()).incrementAndGet();

            try {
               gate.get().await(30, TimeUnit.SECONDS);
            }
            catch(InterruptedException e) {
               Thread.currentThread().interrupt();
            }
         }
      });
      listeners.addJobListener(completion);
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

   private void releaseGate() {
      gate.get().countDown();
   }

   private ScheduleTask parent(String name) {
      return task(name);
   }

   private ScheduleTask parent(String name, IdentityID owner) {
      ScheduleTask task = task(name);
      task.setOwner(owner);
      return task;
   }

   private ScheduleTask dependant(String name, ScheduleTask... parents) throws SchedulerException {
      return dependant(name, SchedulerTestHarness.TEST_OWNER, parents);
   }

   private ScheduleTask dependant(String name, IdentityID owner, ScheduleTask... parents)
      throws SchedulerException
   {
      ScheduleTask child = task(name);
      child.setOwner(owner);

      for(ScheduleTask parent : parents) {
         child.addCondition(new CompletionCondition(parent.getTaskId()));
      }

      harness.registerTask(child);
      return child;
   }

   // Every parent ends once: the end time written before the passes is the end time of that
   // parent's own pass, so the same start (and end) is used for both.
   private long startOf(ScheduleTask task) {
      return starts.computeIfAbsent(task.getTaskId(), k -> System.currentTimeMillis() - 5000);
   }

   private void markFinished(ScheduleTask task) {
      markFinished(task, startOf(task));
   }

   private void markFinished(ScheduleTask task, long start) {
      harness.getDao().setStatus(task.getTaskId(), Scheduler.Status.FINISHED, start, start + 10,
                                 null, false);
   }

   private JobExecutionContext finishedContext(ScheduleTask task) {
      return context(task, jobData(task), new JobDataMap(), new Date(startOf(task)), 10L);
   }

   private void finish(ScheduleTask task) throws Exception {
      listener.jobWasExecuted(finishedContext(task), null);
   }

   private void finish(ScheduleTask task, long start) throws Exception {
      listener.jobWasExecuted(
         context(task, jobData(task), new JobDataMap(), new Date(start), 10L), null);
   }

   private int runsOf(ScheduleTask task) {
      AtomicInteger count = runs.get(task.getTaskId());
      return count == null ? 0 : count.get();
   }

   private int pendingTriggers(ScheduleTask task) throws SchedulerException {
      int pending = 0;

      for(Trigger trigger : harness.getQuartz().getTriggersOfJob(jobKey(task))) {
         pending += trigger.getKey().getName().endsWith("-park") ? 0 : 1;
      }

      return pending;
   }

   private int executing(ScheduleTask task) throws SchedulerException {
      int count = 0;

      for(JobExecutionContext running : harness.getQuartz().getCurrentlyExecutingJobs()) {
         count += running.getJobDetail().getKey().equals(jobKey(task)) ? 1 : 0;
      }

      return count;
   }

   private static JobKey jobKey(ScheduleTask task) {
      return new JobKey(task.getTaskId(), Scheduler.GROUP_NAME);
   }

   /**
    * Waits until every trigger of the task has run to the end of its listener passes. A trigger
    * stays stored until its run is complete, and the dependants are triggered inside the passes
    * of the parents, so once those returned this is the final run count.
    */
   private void awaitSettled(ScheduleTask task) {
      await().pollInterval(Duration.ofMillis(5)).atMost(Duration.ofSeconds(5))
         .until(() -> pendingTriggers(task) == 0 && executing(task) == 0);
   }

   /** The dependant has no trigger stored and never began a run. */
   private void assertNeverTriggered(ScheduleTask task) throws SchedulerException {
      assertEquals(0, pendingTriggers(task), "no trigger may be stored for " + task.getTaskId());
      AtomicInteger started = arrivals.get(task.getTaskId());
      assertEquals(0, started == null ? 0 : started.get(), "no run may have begun");
      assertEquals(0, runsOf(task));
   }

   /** Runs the actions on their own threads, released by a barrier, and joins all of them. */
   private static void runTogether(ThrowingRunnable... actions) throws Exception {
      CyclicBarrier barrier = new CyclicBarrier(actions.length);
      List<Throwable> failures = new CopyOnWriteArrayList<>();
      List<Thread> threads = new ArrayList<>();

      for(ThrowingRunnable action : actions) {
         Thread thread = new Thread(() -> {
            try {
               barrier.await(10, TimeUnit.SECONDS);
               action.run();
            }
            catch(Throwable e) {
               failures.add(e);
            }
         }, "parent-finish");
         threads.add(thread);
         thread.start();
      }

      for(Thread thread : threads) {
         thread.join(20000);
         assertFalse(thread.isAlive(), "a parent pass did not return");
      }

      assertTrue(failures.isEmpty(), "a parent pass failed: " + failures);
   }

   @FunctionalInterface
   private interface ThrowingRunnable {
      void run() throws Exception;
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
