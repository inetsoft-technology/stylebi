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
package inetsoft.sree.schedule.jobstore;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.sree.internal.cluster.MockCluster;
import inetsoft.sree.schedule.*;
import inetsoft.sree.schedule.cloudrunner.ScheduleTaskCloudJob;
import inetsoft.sree.schedule.quartz.*;
import inetsoft.sree.security.*;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.quartz.*;
import org.quartz.impl.DirectSchedulerFactory;
import org.quartz.simpl.SimpleThreadPool;
import org.quartz.spi.OperableTrigger;
import org.springframework.context.ApplicationContext;

import java.io.*;
import java.time.Duration;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #76976: the store keeps a fired trigger of a non-concurrent job blocked while it runs and
 * releases the job's blocked and acquired triggers when a run of the job completes. A recurring
 * trigger whose run outlasts its interval must be released after every run, so it keeps firing
 * once per fire time and never concurrently; a run that never completes must not keep the task's
 * other conditions from firing; and a trigger acquired by a node that then stopped or died must
 * still run, exactly once, on another node.
 *
 * <p>Bug #77245: a hold whose owner can no longer complete it (its cluster node left the
 * cluster, including a node restarted on the same member name, which rejoins with a new node id)
 * must be released by another node's next acquire pass, but never while its run may still be
 * executing, and never because another live node shares its member name.
 *
 * <p>Bug #77202: a job disallows concurrent execution across all of its triggers and all nodes.
 * A condition that comes due while another condition's run is in flight fires once when that run
 * completes, or as soon as the run is no longer honoured: its node left the cluster or the task
 * timeout passed, so a run that never completes delays the other conditions but never stops them.
 *
 * <p>Two real Quartz schedulers built like {@code Scheduler.initialize0()} share one cluster whose
 * replicated maps keep and hand out serialized copies, like Ignite's. Timings are the production
 * ones scaled by 1/10 (acquire horizon 2 s, misfire threshold 0.5 s).
 */
@Tag("slow")
class ClusterJobStoreTriggerReleaseTest {
   @BeforeEach
   void setUp() {
      savedAppContext = ConfigurationContext.getContext().getApplicationContext();
      savedPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void tearDown() throws Exception {
      Recorder recorder = recorder();

      if(recorder != null) {
         recorder.release.countDown();
      }

      for(org.quartz.Scheduler scheduler : schedulers) {
         scheduler.shutdown(true);
      }

      RECORDERS.remove(recorderId);
      taskTimeout = DEFAULT_TIMEOUT;
      ConfigurationContext.getContext().setApplicationContext(savedAppContext);
      // storing the job sets the task owner as the thread's principal
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   @Test
   void recurringRunOutlastingTheIntervalKeepsFiringAndNeverOverlaps() throws Exception {
      startSchedulers("node-A", "node-B");
      long start = (System.currentTimeMillis() / 1000 + 2) * 1000;
      JobDetail job = createJob();
      IntervalTrigger trigger = recurringTrigger(job, 1, start);
      schedulers.get(0).scheduleJob(job, Collections.singleton(trigger), true);

      Thread.sleep(start + OBSERVATION - System.currentTimeMillis());

      Recorder recorder = recorder();
      List<Execution> executions = executions();
      String trace = trace(executions);

      assertEquals(1, recorder.maxRunning.get(), "runs overlapped:" + trace);
      assertTrue(executions.size() >= 3, "trigger stopped firing:" + trace);
      assertDistinctFireTimes(executions, trace);

      for(int i = 1; i < executions.size(); i++) {
         long idle = executions.get(i).start - executions.get(i - 1).end;
         assertTrue(idle <= MAX_IDLE, "trigger was not released after run " + i + ":" + trace);
      }
   }

   /**
    * Bug #77202: a run that never completes (a hung action) keeps the task's run-once condition
    * from firing only until the task timeout, after which that condition fires once. The hung
    * run's own recurring trigger is released at the timeout too, not by the other condition's
    * completion (before #77202 it was, and then fired on top of its own run), and it fires only
    * after the run-once condition's run, since its run-once fire time is older. One node, so the
    * two released triggers are acquired in fire time order.
    */
   @Test
   void runThatNeverCompletesDoesNotStopTheOtherConditions() throws Exception {
      taskTimeout = "6000";
      startSchedulers("node-A");
      long start = (System.currentTimeMillis() / 1000 + 2) * 1000;
      long runOnce = start + 1000;
      JobDetail job = createJob();

      IntervalTrigger recurring = recurringTrigger(job, 1, start);
      recurring.getJobDataMap().put(HANG_KEY, true);

      TimeConditionTriggerImpl atTrigger = runOnceTrigger(job, 2, runOnce, 500);

      Set<Trigger> triggers = new HashSet<>();
      triggers.add(recurring);
      triggers.add(atTrigger);
      schedulers.get(0).scheduleJob(job, triggers, true);

      Execution hung = awaitFirstExecution();
      Thread.sleep(Math.max(0, hung.start + 6000 - 500 - System.currentTimeMillis()));
      assertEquals(1, executions().size(),
                   "a condition fired during the run before the task timeout:" +
                      trace(executions()));

      Thread.sleep(hung.start + 6000 + 500 + MAX_IDLE + 1000 - System.currentTimeMillis());

      List<Execution> executions = executions();
      String trace = trace(executions);
      List<Execution> ats = runsOf(executions, atTrigger);
      List<Execution> recurringRuns = runsOf(executions, recurring);

      assertEquals(1, ats.size(), "run-once condition did not fire once:" + trace);
      assertTrue(ats.get(0).start >= hung.start + 6000 - SLACK,
                 "run-once condition fired before the task timeout:" + trace);
      assertEquals(2, recurringRuns.size(),
                   "hung run's recurring trigger was not released once at the timeout:" + trace);
      assertTrue(recurringRuns.get(1).start >= ats.get(0).end,
                 "recurring trigger ran on top of the run-once condition's run:" + trace);
      assertDistinctFireTimes(executions, trace);
   }

   /**
    * Bug #77202: a lone recurring trigger whose run hangs past the task timeout on a live node is
    * released at the timeout, and not before, since no other run's completion releases it.
    */
   @Test
   void hungRunsOwnTriggerIsReleasedAtTheTaskTimeout() throws Exception {
      taskTimeout = "5000";
      startSchedulers(1, "node-A", "node-B");
      scheduleHungRecurringTrigger(SlowTaskJob.class);
      Execution hung = awaitFirstExecution();

      Thread.sleep(Math.max(0, hung.start + 5000 - 500 - System.currentTimeMillis()));
      assertEquals(1, executions().size(),
                   "released before the task timeout:" + trace(executions()));

      await().atMost(Duration.ofMillis(500 + MAX_IDLE + 1000))
         .until(() -> recorder().executions.size() >= 2);

      List<Execution> executions = executions();
      String trace = trace(executions);
      assertTrue(executions.get(1).start >= hung.start + 5000 - SLACK,
                 "released before the task timeout:" + trace);
      assertDistinctFireTimes(executions, trace);
   }

   /**
    * Bug #77202: a refused fire keeps Quartz's results paired with its triggers by index (a result
    * without a bundle), and leaves the trigger waiting with its fire time.
    */
   @Test
   void refusedFireReturnsAnEmptyResultAndKeepsTheTrigger() throws Exception {
      cluster = new IgniteLikeCluster();
      installContext(cluster);
      cluster.nodeIds.add("node-A");
      StoppingJobStore store = new StoppingJobStore("node-A", "node-A");
      store.initialize(null, mock(org.quartz.spi.SchedulerSignaler.class));
      long time = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);
      JobDetail job = createJob();
      TimeConditionTriggerImpl trigger = runOnceTrigger(job, 2, time, 500);
      store.storeJobAndTrigger(job, trigger);

      DistributedMap<TriggerKey, TriggerWrapper> triggersByKey =
         cluster.getReplicatedMap("jobstore.triggersByKey");
      OperableTrigger acquired =
         (OperableTrigger) triggersByKey.get(trigger.getKey()).trigger.clone();
      acquired.setFireInstanceId("store-A-2");
      triggersByKey.put(trigger.getKey(), TriggerWrapper.newOwnedTriggerWrapper(
         acquired, TriggerState.ACQUIRED, "node-A", "node-A", "store-A-"));
      DistributedMap<JobKey, RunningJob> runningJobs =
         cluster.getReplicatedMap("jobstore.runningJobs");
      runningJobs.put(job.getKey(), new RunningJob(
         "store-A-1", TriggerKey.triggerKey("other", "ClusterJobStoreTriggerReleaseTest"),
         "node-A", "node-A", System.currentTimeMillis()));

      List<org.quartz.spi.TriggerFiredResult> results =
         store.triggersFired(Collections.singletonList(acquired));
      TriggerWrapper stored = triggersByKey.get(trigger.getKey());

      assertEquals(1, results.size(), "results not paired with the triggers");
      assertNull(results.get(0).getTriggerFiredBundle(), "refused fire returned a bundle");
      assertEquals(TriggerState.WAITING, stored.getState(), "refused trigger: " + stored);
      assertEquals(time, stored.getNextFireTime(), "refused trigger lost its fire time");
      assertEquals("store-A-1", runningJobs.get(job.getKey()).getFireInstanceId(),
                   "refused fire replaced the running record");
   }

   /**
    * Bug #77202: a condition that comes due while another condition's run is in flight does not
    * start a second run of the task on either node; it fires once, as soon as that run completes.
    */
   @Test
   void conditionDueDuringARunFiresOnceAfterTheRunCompletes() throws Exception {
      startSchedulers("node-A", "node-B");
      long t1 = (System.currentTimeMillis() / 1000 + 2) * 1000;
      JobDetail job = createJob();
      TimeConditionTriggerImpl trigger1 = runOnceTrigger(job, 1, t1, 5000);
      TimeConditionTriggerImpl trigger2 = runOnceTrigger(job, 2, t1 + 1500, 500);
      Set<Trigger> triggers = new HashSet<>();
      triggers.add(trigger1);
      triggers.add(trigger2);
      schedulers.get(0).scheduleJob(job, triggers, true);

      Thread.sleep(t1 + 5000 + 500 + MAX_IDLE + 1000 - System.currentTimeMillis());

      List<Execution> executions = executions();
      String trace = trace(executions);
      Execution run1 = runsOf(executions, trigger1).get(0);
      List<Execution> runs2 = runsOf(executions, trigger2);

      assertEquals(1, recorder().maxRunning.get(), "runs overlapped:" + trace);
      assertEquals(1, runs2.size(), "condition 2 did not fire exactly once:" + trace);
      assertTrue(runs2.get(0).start >= run1.end, "condition 2 ran before run 1 ended:" + trace);
      assertTrue(runs2.get(0).start - run1.end <= MAX_IDLE,
                 "condition 2 was not fired when run 1 completed:" + trace);
   }

   /**
    * Bug #77202: the node running the task dies mid-run and leaves the cluster while another
    * condition is held back by that run. The condition must be released and fire once, on the
    * surviving node, without waiting for the task timeout.
    */
   @Test
   void conditionHeldBackByARunOfADeadNodeFiresOnceOnAnotherNode() throws Exception {
      startSchedulers(1, "node-A", "node-B");
      long t1 = (System.currentTimeMillis() / 1000 + 2) * 1000;
      long t2 = t1 + 1500;
      JobDetail job = createJob();
      TimeConditionTriggerImpl trigger1 = runOnceTrigger(job, 1, t1, 0);
      trigger1.getJobDataMap().put(HANG_KEY, true);
      TimeConditionTriggerImpl trigger2 = runOnceTrigger(job, 2, t2, 500);
      Set<Trigger> triggers = new HashSet<>();
      triggers.add(trigger1);
      triggers.add(trigger2);
      schedulers.get(0).scheduleJob(job, triggers, true);

      String runner = awaitFirstRun();
      Thread.sleep(t2 + MAX_IDLE - System.currentTimeMillis());
      assertEquals(1, executions().size(),
                   "condition 2 started during the run:" + trace(executions()));

      long killed = System.currentTimeMillis();
      killNode(runner, true);
      await().atMost(Duration.ofMillis(MAX_IDLE + 1000))
         .until(() -> recorder().executions.size() >= 2);
      Thread.sleep(MAX_IDLE);

      List<Execution> executions = executions();
      String trace = trace(executions);
      List<Execution> runs2 = runsOf(executions, trigger2);
      assertEquals(1, runs2.size(), "condition 2 did not fire exactly once:" + trace);
      assertNotEquals(runner, runs2.get(0).schedulerId, "fired on the dead node:" + trace);
      assertTrue(runs2.get(0).start >= killed, "fired before the node died:" + trace);
   }

   /**
    * Bug #77202: a run that outlasts the task timeout on a live node (ScheduleTask cancels a run
    * at the timeout, so it can only be hung) no longer holds back the task's other conditions:
    * the held-back condition fires once after the timeout, and not before.
    */
   @Test
   void conditionHeldBackByARunPastTheTaskTimeoutFiresOnce() throws Exception {
      taskTimeout = "5000";
      startSchedulers(1, "node-A", "node-B");
      long t1 = (System.currentTimeMillis() / 1000 + 2) * 1000;
      JobDetail job = createJob();
      TimeConditionTriggerImpl trigger1 = runOnceTrigger(job, 1, t1, 0);
      trigger1.getJobDataMap().put(HANG_KEY, true);
      TimeConditionTriggerImpl trigger2 = runOnceTrigger(job, 2, t1 + 1000, 500);
      Set<Trigger> triggers = new HashSet<>();
      triggers.add(trigger1);
      triggers.add(trigger2);
      schedulers.get(0).scheduleJob(job, triggers, true);

      Execution run1 = awaitFirstExecution();
      Thread.sleep(Math.max(0, run1.start + 5000 - 500 - System.currentTimeMillis()));
      assertEquals(1, executions().size(),
                   "condition 2 started before the task timeout:" + trace(executions()));

      await().atMost(Duration.ofMillis(500 + MAX_IDLE + 1000))
         .until(() -> recorder().executions.size() >= 2);
      Thread.sleep(MAX_IDLE);

      List<Execution> executions = executions();
      String trace = trace(executions);
      List<Execution> runs2 = runsOf(executions, trigger2);
      assertEquals(1, runs2.size(), "condition 2 did not fire exactly once:" + trace);
      assertTrue(runs2.get(0).start >= run1.start + 5000 - SLACK,
                 "condition 2 fired before the task timeout:" + trace);
   }

   /**
    * Bug #77202: Scheduler.addTask deletes and re-adds the task's job whenever the task is edited.
    * An edit during a run must not let another condition start a second run.
    */
   @Test
   void editingTheTaskDuringARunKeepsItsOtherConditionsWaiting() throws Exception {
      startSchedulers("node-A", "node-B");
      long t1 = (System.currentTimeMillis() / 1000 + 2) * 1000;
      long t2 = t1 + 2500;
      JobDetail job = createJob();
      TimeConditionTriggerImpl trigger1 = runOnceTrigger(job, 1, t1, 5000);
      Set<Trigger> triggers = new HashSet<>();
      triggers.add(trigger1);
      triggers.add(runOnceTrigger(job, 2, t2, 500));
      schedulers.get(0).scheduleJob(job, triggers, true);

      awaitFirstRun();
      schedulers.get(0).deleteJob(job.getKey());
      TimeConditionTriggerImpl trigger2 = runOnceTrigger(job, 2, t2, 500);
      schedulers.get(0).scheduleJob(job, Collections.singleton(trigger2), true);

      Thread.sleep(t1 + 5000 + 500 + MAX_IDLE + 1000 - System.currentTimeMillis());

      List<Execution> executions = executions();
      String trace = trace(executions);
      Execution run1 = runsOf(executions, trigger1).get(0);
      List<Execution> runs2 = runsOf(executions, trigger2);

      assertEquals(1, recorder().maxRunning.get(), "runs overlapped:" + trace);
      assertEquals(1, runs2.size(), "condition 2 did not fire exactly once:" + trace);
      assertTrue(runs2.get(0).start >= run1.end, "condition 2 ran before run 1 ended:" + trace);
   }

   /**
    * Bug #77202: the rule deciding if a job's run still holds back its other triggers.
    */
   @Test
   void runIsHonouredOnlyWhileItMayStillBeExecuting() {
      RunningJob run = new RunningJob(
         "fire-1", TriggerKey.triggerKey("t", "ClusterJobStoreTriggerReleaseTest"), "id-A",
         "172.18.0.3:5701", 1000);
      Set<String> both = Set.of("id-A", "id-B");
      Set<String> onlyB = Set.of("id-B");

      assertTrue(ClusterJobStore.isRunning(run, both, false, 500, 1499), "owner in the cluster");
      assertFalse(ClusterJobStore.isRunning(run, both, false, 500, 1500), "past the bound");
      assertFalse(ClusterJobStore.isRunning(run, onlyB, false, 500, 1000),
                  "owner left the cluster");
      assertTrue(ClusterJobStore.isRunning(run, onlyB, true, 500, 1499),
                 "a cloud run outlives its owner");
      assertFalse(ClusterJobStore.isRunning(run, onlyB, true, 500, 1500),
                  "a cloud run past the bound");
      assertTrue(ClusterJobStore.isRunning(run, null, false, 500, 1499), "topology unknown");
      assertFalse(ClusterJobStore.isRunning(run, null, false, 500, 1500),
                  "topology unknown, past the bound");
      assertFalse(ClusterJobStore.isRunning(null, both, false, 500, 1000), "no run");

      OperableTrigger trigger = (OperableTrigger) TriggerBuilder.newTrigger()
         .withIdentity("stale-rule", "ClusterJobStoreTriggerReleaseTest")
         .forJob("stale-rule-job", "ClusterJobStoreTriggerReleaseTest")
         .build();
      TriggerWrapper blocked = TriggerWrapper.newOwnedTriggerWrapper(
         trigger, TriggerState.BLOCKED, "id-A", "172.18.0.3:5701", "store-1-");
      long since = blocked.getOwnedSince();
      assertFalse(ClusterJobStore.isStale(blocked, 500, since + 499), "within the bound");
      assertTrue(ClusterJobStore.isStale(blocked, 500, since + 500), "past the bound");
      assertFalse(ClusterJobStore.isStale(
         TriggerWrapper.newTriggerWrapper(blocked, TriggerState.BLOCKED), 500, since + 500),
                  "no recorded owner (error state)");
   }

   /**
    * Bug #77202: a run is honoured for the effective task timeout, a cloud runner job's for the
    * timeout plus the 5-minute launch margin, also when its node left.
    */
   @Test
   void runIsHonouredForTheTaskTimeout() throws Exception {
      taskTimeout = "0";
      startSchedulers(1, "node-A");
      ClusterJobStore store = stores.get("node-A");
      long start = (System.currentTimeMillis() / 1000 + 3600) * 1000;
      JobDetail localJob = createJob(SlowTaskJob.class);
      schedulers.get(0).scheduleJob(
         localJob, Collections.singleton(recurringTrigger(localJob, 1, start)), true);
      JobKey cloudKey =
         JobKey.jobKey("task-77202-cloud", inetsoft.sree.schedule.Scheduler.GROUP_NAME);
      JobDetail cloudJob = JobBuilder.newJob(SlowCloudJob.class).withIdentity(cloudKey)
         .storeDurably(true).usingJobData(localJob.getJobDataMap()).build();
      schedulers.get(0).addJob(cloudJob, true);

      // a timeout that is not positive falls back to the default
      long timeout = ScheduleTask.DEFAULT_TASK_TIMEOUT;
      long margin = TimeUnit.MINUTES.toMillis(5);
      RunningJob run = new RunningJob(
         "fire-1", TriggerKey.triggerKey("t", "ClusterJobStoreTriggerReleaseTest"), "node-A",
         "node-A", 1000);
      Set<String> live = Set.of("node-A");
      Set<String> gone = Set.of("node-B");

      assertTrue(store.isRunning(localJob.getKey(), run, live, 1000 + timeout - 1), "local");
      assertFalse(store.isRunning(localJob.getKey(), run, live, 1000 + timeout), "local stale");
      assertFalse(store.isRunning(localJob.getKey(), run, gone, 1000), "local owner gone");
      assertTrue(store.isRunning(cloudKey, run, gone, 1000 + timeout + margin - 1), "cloud");
      assertFalse(store.isRunning(cloudKey, run, gone, 1000 + timeout + margin), "cloud stale");
   }

   /**
    * A node acquires trigger 2 shortly before its time and then stops gracefully (scale-in,
    * restart, rolling upgrade). Quartz releases the acquisition on halt only if its scheduler
    * thread is waiting for the fire time at that moment; this test suppresses that release, so the
    * store's own shutdown must release it: the trigger runs exactly once, on the other node.
    */
   @Test
   void triggerHeldByAStoppedNodeRunsOnceOnAnotherNode() throws Exception {
      runTriggerHeldByNodeThatGoesAway(false);
   }

   /**
    * Same as above, but the node dies: nothing on it releases the acquisition. The completion of
    * the run on the other node must release it, and the trigger runs exactly once.
    */
   @Test
   void triggerHeldByADeadNodeRunsOnceAfterTheOtherRunCompletes() throws Exception {
      runTriggerHeldByNodeThatGoesAway(true);
   }

   private void runTriggerHeldByNodeThatGoesAway(boolean dies) throws Exception {
      // Bug #77202, no node acquires trigger 2 while run 1 is in flight, so the other node must
      // acquire it before run 1 starts: node-A alone takes trigger 1 (inside its acquire horizon
      // when scheduled), then node-B joins and takes trigger 2 as soon as it is inside its horizon.
      // One worker per node, so node-A cannot also hold trigger 2.
      startSchedulers(1, "node-A");
      String runner = "node-A";
      // inside the acquire horizon, so node-A acquires it as soon as it is scheduled
      long t1 = System.currentTimeMillis() + IDLE_WAIT - 100;
      long t2 = t1 + 1500;
      JobDetail job = createJob();
      TimeConditionTriggerImpl trigger1 = runOnceTrigger(job, 1, t1, 800);
      TimeConditionTriggerImpl trigger2 = runOnceTrigger(job, 2, t2, 500);
      Set<Trigger> triggers = new HashSet<>();
      triggers.add(trigger1);
      triggers.add(trigger2);
      schedulers.get(0).scheduleJob(job, triggers, true);

      await().atMost(Duration.ofMillis(Math.max(0, t1 - 600 - System.currentTimeMillis())))
         .until(() -> storedTrigger(trigger1).getState() == TriggerState.ACQUIRED);
      org.quartz.Scheduler other = startScheduler("node-B", "node-B", "node-B", 1);

      // wake the other node's scheduler thread for an ordinary acquire pass once trigger 2 is
      // inside its acquire horizon (an unknown key changes nothing)
      Thread.sleep(Math.max(0, t2 - 1950 - System.currentTimeMillis()));
      other.resumeTrigger(TriggerKey.triggerKey("wake-up", "ClusterJobStoreTriggerReleaseTest"));
      await().atMost(Duration.ofMillis(Math.max(0, t1 - 150 - System.currentTimeMillis())))
         .until(() -> storedTrigger(trigger2).getState() == TriggerState.ACQUIRED);

      TriggerWrapper held = storedTrigger(trigger2);
      assertEquals(TriggerState.ACQUIRED, held.getState(), "trigger 2 not held: " + held);
      assertEquals("node-B", held.getOwnerNode(), "trigger 2 not held by node-B: " + held);

      StoppingJobStore otherStore = stores.get(instanceId(other));
      otherStore.stopping = true;
      otherStore.dies = dies;
      other.shutdown(false);

      Thread.sleep(t2 + 500 + MAX_IDLE + 1500 - System.currentTimeMillis());

      List<Execution> executions = executions();
      String trace = trace(executions);
      List<Execution> runs2 = executions.stream()
         .filter(e -> e.trigger.equals(trigger2.getName()))
         .collect(Collectors.toList());
      Execution run1 = executions.get(0);

      assertEquals(1, runs2.size(), "trigger 2 did not run exactly once:" + trace);
      assertEquals(runner, runs2.get(0).schedulerId, "trigger 2 ran on the stopped node:" + trace);
      assertTrue(runs2.get(0).start >= run1.end, "trigger 2 ran before run 1 ended:" + trace);
   }

   /**
    * Bug #77245: the node running a recurring trigger dies mid-run and leaves the cluster. The
    * surviving node must release the dead node's hold on its next acquire pass and fire the
    * missed fire times exactly once. The catch-up run hangs too, and since its node is alive its
    * hold must not be released.
    */
   @Test
   void runOfADeadNodeIsReleasedAndCaughtUpOnceOnAnotherNode() throws Exception {
      startSchedulers(1, "node-A", "node-B");
      IntervalTrigger trigger = scheduleHungRecurringTrigger(SlowTaskJob.class);
      String runner = awaitFirstRun();
      killNode(runner, true);

      await().atMost(Duration.ofMillis(MAX_IDLE + INTERVAL))
         .until(() -> recorder().executions.size() >= 2);
      Thread.sleep(3 * INTERVAL + MAX_IDLE);

      List<Execution> executions = executions();
      String trace = trace(executions);
      TriggerWrapper held = storedTrigger(trigger);

      assertEquals(2, executions.size(), "not exactly one catch-up run:" + trace);
      assertNotEquals(runner, executions.get(1).schedulerId, "caught up on the dead node:" + trace);
      assertDistinctFireTimes(executions, trace);
      assertEquals(TriggerState.BLOCKED, held.getState(), "live run was released: " + held);
      assertEquals(executions.get(1).schedulerId, held.getOwnerNode(), "wrong owner: " + held);
   }

   /**
    * Bug #77245: a hung run whose node is still in the cluster keeps its hold.
    */
   @Test
   void runOfALiveNodeIsNotReleased() throws Exception {
      startSchedulers(1, "node-A", "node-B");
      IntervalTrigger trigger = scheduleHungRecurringTrigger(SlowTaskJob.class);
      String runner = awaitFirstRun();

      Thread.sleep(4 * INTERVAL + MAX_IDLE);

      List<Execution> executions = executions();
      TriggerWrapper held = storedTrigger(trigger);

      assertEquals(1, executions.size(), "live run was released:" + trace(executions));
      assertEquals(TriggerState.BLOCKED, held.getState(), "live run was released: " + held);
      assertEquals(runner, held.getOwnerNode(), "wrong owner: " + held);
   }

   /**
    * Bug #77245: two live nodes on different hosts can have the same member name (containers
    * with the same bridge IP and discovery port behind NAT) while Ignite clusters them by node id.
    * Neither may release the other's hold.
    */
   @Test
   void runOfALiveNodeWithTheSameMemberNameIsNotReleased() throws Exception {
      cluster = new IgniteLikeCluster();
      installContext(cluster);
      startScheduler("node-A", "node-A", "172.18.0.3:5701", 1);
      startScheduler("node-B", "node-B", "172.18.0.3:5701", 1);
      IntervalTrigger trigger = scheduleHungRecurringTrigger(SlowTaskJob.class);
      String runner = awaitFirstRun();

      Thread.sleep(4 * INTERVAL + MAX_IDLE);

      List<Execution> executions = executions();
      TriggerWrapper held = storedTrigger(trigger);

      assertEquals(1, executions.size(), "live run was released:" + trace(executions));
      assertEquals(1, recorder().maxRunning.get(), "runs overlapped:" + trace(executions));
      assertEquals(TriggerState.BLOCKED, held.getState(), "live run was released: " + held);
      assertEquals(runner, held.getOwnerNode(), "wrong owner: " + held);
   }

   /**
    * Bug #77245: a node acquired a trigger and died before firing it. The ACQUIRED hold must be
    * released by another node, which fires the trigger once.
    */
   @Test
   void acquiredTriggerOfADeadNodeIsReleasedAndFiredOnce() throws Exception {
      startSchedulers(1, "node-B");
      // outside the acquire horizon, so node-B cannot acquire it before the hold is replaced
      long start = (System.currentTimeMillis() / 1000 + 3 * IDLE_WAIT / 1000 + 1) * 1000;
      JobDetail job = createJob();
      TimeConditionTriggerImpl trigger = runOnceTrigger(job, 1, start, 500);
      schedulers.get(0).scheduleJob(job, Collections.singleton(trigger), true);

      DistributedMap<TriggerKey, TriggerWrapper> triggersByKey =
         cluster.getReplicatedMap("jobstore.triggersByKey");
      TriggerWrapper stored = triggersByKey.get(trigger.getKey());
      OperableTrigger acquired = (OperableTrigger) stored.trigger.clone();
      acquired.setFireInstanceId("dead-store-1");
      // node-A is not in the cluster: it acquired the trigger and died
      triggersByKey.put(trigger.getKey(), TriggerWrapper.newOwnedTriggerWrapper(
         acquired, TriggerState.ACQUIRED, "node-A", "node-A", "dead-store-"));

      await().atMost(Duration.ofMillis(start + MAX_IDLE + 500 - System.currentTimeMillis()))
         .until(() -> !recorder().executions.isEmpty());
      Thread.sleep(MAX_IDLE);

      List<Execution> executions = executions();
      String trace = trace(executions);
      assertEquals(1, executions.size(), "not fired exactly once:" + trace);
      assertEquals("node-B", executions.get(0).schedulerId, "not fired by the live node:" + trace);
      assertEquals(start, executions.get(0).scheduledFireTime, "wrong fire time:" + trace);
   }

   /**
    * Bug #77245: a cloud runner job's hold is kept for the effective task timeout plus the
    * 5-minute launch margin, and not a moment less; any other hold needs no wait.
    */
   @Test
   void cloudJobHoldIsTheTaskTimeoutPlusTheLaunchMargin() throws Exception {
      startSchedulers(1, "node-A");
      ClusterJobStore store = stores.get("node-A");
      long start = (System.currentTimeMillis() / 1000 + 3600) * 1000;
      JobDetail cloudJob = createJob(SlowCloudJob.class);
      IntervalTrigger trigger = recurringTrigger(cloudJob, 1, start);
      schedulers.get(0).scheduleJob(cloudJob, Collections.singleton(trigger), true);

      TriggerWrapper stored = storedTrigger(trigger);
      TriggerWrapper blocked = TriggerWrapper.newOwnedTriggerWrapper(
         stored.trigger, TriggerState.BLOCKED, "gone", "gone", "gone-");
      TriggerWrapper acquired = TriggerWrapper.newOwnedTriggerWrapper(
         stored.trigger, TriggerState.ACQUIRED, "gone", "gone", "gone-");
      long timeout = ScheduleTask.getTaskTimeout();
      timeout = timeout > 0 ? timeout : ScheduleTask.DEFAULT_TASK_TIMEOUT;
      long hold = store.getOrphanedRunHold(blocked);
      long since = blocked.getOwnedSince();
      Set<String> live = Set.of("node-A");

      assertEquals(timeout + TimeUnit.MINUTES.toMillis(5), hold, "cloud hold");
      assertEquals(0, store.getOrphanedRunHold(acquired), "nothing launched before firing");
      assertFalse(ClusterJobStore.isOrphaned(blocked, live, () -> hold, since + hold - 1),
                  "released before the task timeout plus the launch margin");
      assertTrue(ClusterJobStore.isOrphaned(blocked, live, () -> hold, since + hold),
                 "not released after the task timeout plus the launch margin");

      schedulers.get(0).deleteJob(cloudJob.getKey());
      JobDetail localJob = createJob(SlowTaskJob.class);
      IntervalTrigger localTrigger = recurringTrigger(localJob, 1, start);
      schedulers.get(0).scheduleJob(localJob, Collections.singleton(localTrigger), true);
      TriggerWrapper localBlocked = TriggerWrapper.newOwnedTriggerWrapper(
         storedTrigger(localTrigger).trigger, TriggerState.BLOCKED, "gone", "gone", "gone-");
      assertEquals(0, store.getOrphanedRunHold(localBlocked), "an in-JVM run dies with its node");
   }

   /**
    * Bug #77245: the scheduler is stopped and started in the same JVM (Scheduler.stop() and
    * start()) while a run is in flight. The old run keeps executing, so the new store must not
    * release its hold; the old run's completion releases it, and runs never overlap.
    */
   @Test
   void runOfAStoreRestartedInTheSameJvmIsNotReleased() throws Exception {
      startSchedulers(1, "node-A");
      IntervalTrigger trigger = scheduleHungRecurringTrigger(SlowTaskJob.class);
      awaitFirstRun();
      schedulers.get(0).shutdown(false);
      // the same Ignite node, so the same node id and member
      startScheduler("node-A2", "node-A", "node-A", 1);

      Thread.sleep(3 * INTERVAL + MAX_IDLE);

      List<Execution> executions = executions();
      TriggerWrapper held = storedTrigger(trigger);
      assertEquals(1, executions.size(), "running run was released:" + trace(executions));
      assertEquals(TriggerState.BLOCKED, held.getState(), "running run was released: " + held);

      recorder().release.countDown();
      await().atMost(Duration.ofMillis(MAX_IDLE + INTERVAL))
         .until(() -> recorder().executions.size() >= 2);

      executions = executions();
      String trace = trace(executions);
      assertEquals(1, recorder().maxRunning.get(), "runs overlapped:" + trace);
      assertEquals("node-A2", executions.get(1).schedulerId, "not fired by the new store:" + trace);
      assertDistinctFireTimes(executions, trace);
   }

   /**
    * Bug #77245: the node dies mid-run and comes back on the same address and port, i.e. with
    * the same member name, but in a new JVM, so as a new cluster node with a new id. The old
    * node id left the cluster, so its hold must be released and the missed fire times fired
    * exactly once.
    */
   @Test
   void runOfAPreviousJvmOnTheSameMemberIsReleasedAndCaughtUpOnce() throws Exception {
      startSchedulers(1, "node-A");
      scheduleHungRecurringTrigger(SlowTaskJob.class);
      awaitFirstRun();
      killNode("node-A", true);
      startScheduler("node-A2", "node-A2", "node-A", 1);

      await().atMost(Duration.ofMillis(MAX_IDLE + INTERVAL))
         .until(() -> recorder().executions.size() >= 2);
      Thread.sleep(2 * INTERVAL + MAX_IDLE);

      List<Execution> executions = executions();
      String trace = trace(executions);
      assertEquals(2, executions.size(), "not exactly one catch-up run:" + trace);
      assertEquals("node-A2", executions.get(1).schedulerId, "not caught up by the new JVM:" + trace);
      assertDistinctFireTimes(executions, trace);
   }

   /**
    * Bug #77245: a cloud runner job's run is a container that keeps running when the node that
    * launched it dies, until its platform stops it at the task timeout. Its hold must not be
    * released as soon as the node leaves.
    */
   @Test
   void runOfACloudJobIsNotReleasedWhenItsNodeLeaves() throws Exception {
      startSchedulers(1, "node-A", "node-B");
      IntervalTrigger trigger = scheduleHungRecurringTrigger(SlowCloudJob.class);
      String runner = awaitFirstRun();
      killNode(runner, true);

      Thread.sleep(3 * INTERVAL + MAX_IDLE);

      List<Execution> executions = executions();
      TriggerWrapper held = storedTrigger(trigger);
      assertEquals(1, executions.size(), "cloud run was released:" + trace(executions));
      assertEquals(TriggerState.BLOCKED, held.getState(), "cloud run was released: " + held);
   }

   /**
    * Bug #77245: the orphan rule itself, including the cases the scheduler tests do not reach.
    */
   @Test
   void orphanedOnlyWhenTheOwnerIsGone() {
      OperableTrigger trigger = (OperableTrigger) TriggerBuilder.newTrigger()
         .withIdentity("orphan-rule", "ClusterJobStoreTriggerReleaseTest")
         .forJob("orphan-rule-job", "ClusterJobStoreTriggerReleaseTest")
         .build();
      TriggerWrapper held = TriggerWrapper.newOwnedTriggerWrapper(
         trigger, TriggerState.BLOCKED, "id-A", "172.18.0.3:5701", "store-1-");
      TriggerWrapper acquired = TriggerWrapper.newOwnedTriggerWrapper(
         trigger, TriggerState.ACQUIRED, "id-A", "172.18.0.3:5701", "store-1-");
      long since = held.getOwnedSince();
      Set<String> both = Set.of("id-A", "id-B");
      Set<String> onlyB = Set.of("id-B");

      assertFalse(ClusterJobStore.isOrphaned(held, both, () -> 0, since), "owner in the cluster");
      assertTrue(ClusterJobStore.isOrphaned(held, onlyB, () -> 0, since),
                 "owner left the cluster");
      assertTrue(ClusterJobStore.isOrphaned(acquired, onlyB, () -> 0, since),
                 "acquired by an owner that left the cluster");
      assertFalse(ClusterJobStore.isOrphaned(acquired, both, () -> 0, since),
                  "acquired by an owner in the cluster");
      assertFalse(ClusterJobStore.isOrphaned(held, null, () -> 0, since), "topology unknown");
      assertFalse(ClusterJobStore.isOrphaned(held, onlyB, () -> 1000, since + 999),
                  "run may still be executing");
      assertTrue(ClusterJobStore.isOrphaned(held, onlyB, () -> 1000, since + 1000),
                 "run can no longer be executing");
      assertFalse(ClusterJobStore.isOrphaned(held, both, () -> {
         throw new AssertionError("hold computed for a live owner");
      }, since), "hold computed for a live owner");

      TriggerWrapper legacy = TriggerWrapper.newTriggerWrapper(trigger, TriggerState.BLOCKED);
      assertFalse(ClusterJobStore.isOrphaned(legacy, onlyB, () -> 0, since),
                  "no recorded owner");

      // the completion error instructions store BLOCKED from the held entry: no owner is kept,
      // so the error state is never released when the node that ran it dies
      TriggerWrapper errored = TriggerWrapper.newTriggerWrapper(held, TriggerState.BLOCKED);
      assertNull(errored.getOwnerNode(), "error state kept the owner: " + errored);
      assertNull(errored.getOwnedSince(), "error state kept the owner: " + errored);
      assertFalse(ClusterJobStore.isOrphaned(errored, onlyB, () -> 0, since),
                  "error state released");
   }

   private IntervalTrigger scheduleHungRecurringTrigger(Class<? extends Job> jobClass)
      throws Exception
   {
      long start = (System.currentTimeMillis() / 1000 + 2) * 1000;
      JobDetail job = createJob(jobClass);
      IntervalTrigger trigger = recurringTrigger(job, 1, start);
      trigger.getJobDataMap().put(HANG_KEY, true);
      schedulers.get(0).scheduleJob(job, Collections.singleton(trigger), true);
      return trigger;
   }

   private String awaitFirstRun() {
      return awaitFirstExecution().schedulerId;
   }

   private Execution awaitFirstExecution() {
      await().atMost(Duration.ofMillis(INTERVAL + MAX_IDLE))
         .until(() -> !recorder().executions.isEmpty());
      return recorder().executions.peek();
   }

   private static List<Execution> runsOf(List<Execution> executions, Trigger trigger) {
      return executions.stream()
         .filter(e -> e.trigger.equals(trigger.getKey().getName()))
         .collect(Collectors.toList());
   }

   /**
    * Kills a node mid-run: nothing on it releases or completes anything, and if it leaves the
    * cluster its member is removed from the topology.
    */
   private void killNode(String instanceId, boolean leaves) throws SchedulerException {
      StoppingJobStore store = stores.get(instanceId);
      store.stopping = true;
      store.dies = true;
      schedulers.stream().filter(s -> instanceId.equals(instanceId(s))).findFirst().orElseThrow()
         .shutdown(false);

      if(leaves) {
         cluster.nodeIds.remove(store.getLocalNodeId());
      }
   }

   private TriggerWrapper storedTrigger(Trigger trigger) {
      DistributedMap<TriggerKey, TriggerWrapper> triggersByKey =
         cluster.getReplicatedMap("jobstore.triggersByKey");
      return triggersByKey.get(trigger.getKey());
   }

   private static TimeConditionTriggerImpl runOnceTrigger(JobDetail job, int index, long time,
                                                          long duration)
   {
      TimeConditionTriggerImpl trigger = new TimeConditionTriggerImpl();
      trigger.setName(job.getKey().getName() + "-" + index);
      trigger.setGroup(inetsoft.sree.schedule.Scheduler.GROUP_NAME);
      trigger.setJobKey(job.getKey());
      trigger.setCondition(TimeCondition.at(new Date(time)));
      trigger.setStartTime(new Date(time));
      trigger.setEndTime(new Date(time + TimeUnit.HOURS.toMillis(1)));
      trigger.setMisfireInstruction(ConditionTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
      trigger.getJobDataMap().put(DURATION_KEY, duration);
      return trigger;
   }

   /**
    * Each trigger's executions have distinct scheduled fire times, i.e. no fire time ran twice.
    */
   private static void assertDistinctFireTimes(List<Execution> executions, String trace) {
      Set<String> fireTimes = new HashSet<>();

      for(Execution execution : executions) {
         assertTrue(fireTimes.add(execution.trigger + "@" + execution.scheduledFireTime),
                    "a fire time ran twice:" + trace);
      }
   }

   private JobDetail createJob() {
      return createJob(SlowTaskJob.class);
   }

   private JobDetail createJob(Class<? extends Job> jobClass) {
      RECORDERS.put(recorderId, new Recorder());
      String taskId = "task-76976-recurring";
      JobDataMap dataMap = new JobDataMap();
      ScheduleTask task = new ScheduleTask(taskId);
      task.setOwner(OWNER);
      dataMap.put(ScheduleTask.class.getName(), task);
      dataMap.put(RECORDER_KEY, recorderId);
      return JobBuilder.newJob(jobClass)
         .withIdentity(taskId, inetsoft.sree.schedule.Scheduler.GROUP_NAME)
         .storeDurably(true)
         .usingJobData(dataMap)
         .build();
   }

   private static IntervalTrigger recurringTrigger(JobDetail job, int index, long start) {
      IntervalTrigger trigger = new IntervalTrigger();
      trigger.setName(job.getKey().getName() + "-" + index);
      trigger.setGroup(inetsoft.sree.schedule.Scheduler.GROUP_NAME);
      trigger.setJobKey(job.getKey());
      // a daily condition, so the completion listener keeps the trigger
      trigger.setCondition(TimeCondition.at(1, 0, 0));
      trigger.setStartTime(new Date(start));
      trigger.setEndTime(new Date(start + TimeUnit.HOURS.toMillis(1)));
      trigger.setMisfireInstruction(ConditionTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
      return trigger;
   }

   private List<Execution> executions() {
      List<Execution> executions = new ArrayList<>(recorder().executions);
      executions.sort(Comparator.comparingLong(e -> e.start));
      return executions;
   }

   private static String trace(List<Execution> executions) {
      return executions.stream().map(Execution::toString)
         .collect(Collectors.joining("\n  ", "\n  ", ""));
   }

   private void startSchedulers(String... instanceIds) throws Exception {
      startSchedulers(3, instanceIds);
   }

   private void startSchedulers(int threads, String... instanceIds) throws Exception {
      cluster = new IgniteLikeCluster();
      installContext(cluster);

      for(String instanceId : instanceIds) {
         startScheduler(instanceId, instanceId, instanceId, threads);
      }
   }

   /**
    * Starts a scheduler whose store runs on the cluster node with the given id and member name,
    * and adds the node to the cluster.
    */
   private org.quartz.Scheduler startScheduler(String instanceId, String nodeId, String member,
                                               int threads)
      throws Exception
   {
      String name = "inetsoft-" + instanceId + "-" + recorderId;
      DirectSchedulerFactory factory = DirectSchedulerFactory.getInstance();
      StoppingJobStore jobStore = new StoppingJobStore(nodeId, member);
      stores.put(instanceId, jobStore);
      cluster.nodeIds.add(nodeId);

      jobStore.setMisfireThreshold(MISFIRE_THRESHOLD);
      factory.createScheduler(
         name, instanceId, new SimpleThreadPool(threads, Thread.NORM_PRIORITY),
         jobStore, null, 0, IDLE_WAIT, -1);
      org.quartz.Scheduler scheduler = factory.getScheduler(name);
      scheduler.getListenerManager().addJobListener(
         new JobCompletionListener("TaskCompletionListener"));
      scheduler.start();
      schedulers.add(scheduler);
      return scheduler;
   }

   private Recorder recorder() {
      return RECORDERS.get(recorderId);
   }

   private static String instanceId(org.quartz.Scheduler scheduler) {
      try {
         return scheduler.getSchedulerInstanceId();
      }
      catch(SchedulerException e) {
         throw new IllegalStateException(e);
      }
   }

   /**
    * Registers the test doubles the store and the listener look up through
    * {@code ConfigurationContext}, with a real cluster so both stores share its maps.
    */
   private static void installContext(Cluster cluster) {
      SecurityEngine engine = mock(SecurityEngine.class);
      SecurityProvider provider = mock(SecurityProvider.class);
      when(engine.getSecurityProvider()).thenReturn(provider);
      when(provider.getAuthenticationProvider()).thenReturn(mock(AuthenticationProvider.class));

      XSessionService sessionService = mock(XSessionService.class);
      when(sessionService.createSessionID(any(), any())).thenReturn("test-session-id");

      PropertiesEngine properties = mock(PropertiesEngine.class);
      when(properties.getProperty(eq("local.host.name"))).thenReturn("localhost");
      when(properties.getProperty(eq("local.host.name"), anyBoolean())).thenReturn("localhost");
      when(properties.getProperty(eq("local.host.name"), anyBoolean(), anyBoolean()))
         .thenReturn("localhost");
      when(properties.getProperty(any(String.class), any(String.class), anyBoolean()))
         .thenAnswer(inv -> inv.getArgument(1));
      when(properties.getProperty(eq("schedule.task.timeout"), anyBoolean()))
         .thenAnswer(inv -> taskTimeout);

      LicenseManager licenseManager = mock(LicenseManager.class);
      when(licenseManager.getAvailableCpuCount()).thenReturn(2);

      ApplicationContext context = mock(ApplicationContext.class);
      when(context.getBean(Cluster.class)).thenReturn(cluster);
      when(context.getBean(SecurityEngine.class)).thenReturn(engine);
      when(context.getBean(XSessionService.class)).thenReturn(sessionService);
      when(context.getBean(PropertiesEngine.class)).thenReturn(properties);
      when(context.getBean(LicenseManager.class)).thenReturn(licenseManager);
      when(context.getBean(ScheduleStatusDao.class)).thenReturn(mock(ScheduleStatusDao.class));
      ConfigurationContext.getContext().setApplicationContext(context);
   }

   /**
    * A time condition trigger that recurs every {@link #INTERVAL} ms, aligned to the interval.
    * The product's conditions recur at most every hour, too slow for a unit test.
    */
   public static class IntervalTrigger extends TimeConditionTriggerImpl {
      @Override
      public Date getFireTimeAfter(Date afterTime) {
         return afterTime == null ? null :
            new Date((afterTime.getTime() / INTERVAL + 1) * INTERVAL);
      }
   }

   /**
    * A store on its own cluster node, which may be going away. Once stopping, the
    * scheduler's own release of acquired triggers is ignored (Quartz can miss it on halt), and a
    * dying node's store releases nothing on shutdown either.
    */
   private static final class StoppingJobStore extends ClusterJobStore {
      StoppingJobStore(String nodeId, String member) {
         this.nodeId = nodeId;
         this.member = member;
      }

      @Override
      String getLocalNodeId() {
         return nodeId;
      }

      @Override
      String getLocalMember() {
         return member;
      }

      @Override
      public void releaseAcquiredTrigger(OperableTrigger trigger) {
         if(!stopping) {
            super.releaseAcquiredTrigger(trigger);
         }
      }

      @Override
      public void shutdown() {
         if(!dies) {
            super.shutdown();
         }
      }

      volatile boolean stopping;
      volatile boolean dies;
      private final String nodeId;
      private final String member;
   }

   /**
    * A MockCluster whose replicated maps keep and hand out serialized copies, like an Ignite
    * replicated cache, and whose topology is the nodes the test put in it.
    */
   private static final class IgniteLikeCluster extends MockCluster {
      @Override
      public <K, V> DistributedMap<K, V> getReplicatedMap(String name) {
         return new CopyingMap<>(super.getReplicatedMap(name));
      }

      @Override
      public Set<String> getClusterNodeIds() {
         return new HashSet<>(nodeIds);
      }

      final Set<String> nodeIds = ConcurrentHashMap.newKeySet();
   }

   private static final class CopyingMap<K, V> extends AbstractMap<K, V>
      implements DistributedMap<K, V>
   {
      CopyingMap(DistributedMap<K, V> delegate) {
         this.delegate = delegate;
      }

      @SuppressWarnings("unchecked")
      private static <T> T copy(T value) {
         if(value == null) {
            return null;
         }

         try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();

            try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
               out.writeObject(value);
            }

            try(ObjectInputStream in =
                   new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
            {
               return (T) in.readObject();
            }
         }
         catch(IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Failed to copy a cache value", e);
         }
      }

      @Override
      public V get(Object key) {
         return copy(delegate.get(key));
      }

      @Override
      public boolean containsKey(Object key) {
         return delegate.containsKey(key);
      }

      @Override
      public V put(K key, V value) {
         return copy(delegate.put(key, copy(value)));
      }

      @Override
      public V remove(Object key) {
         return copy(delegate.remove(key));
      }

      @Override
      public Set<Entry<K, V>> entrySet() {
         Set<Entry<K, V>> entries = new LinkedHashSet<>();

         for(Entry<K, V> entry : delegate.entrySet()) {
            entries.add(new SimpleImmutableEntry<>(entry.getKey(), copy(entry.getValue())));
         }

         return entries;
      }

      @Override
      public V getForUpdate(K key) {
         return copy(delegate.getForUpdate(key));
      }

      @Override
      public void set(K key, V value) {
         delegate.set(key, copy(value));
      }

      @Override
      public void removeAll(Set<? extends K> keys) {
         delegate.removeAll(keys);
      }

      @Override
      public void removeAll() {
         delegate.removeAll();
      }

      private final DistributedMap<K, V> delegate;
   }

   /**
    * Stands in for ScheduleTaskCloudJob / ScheduleTaskJob: DisallowConcurrentExecution and no
    * PersistJobDataAfterExecution. Each run outlasts the trigger's interval.
    */
   @DisallowConcurrentExecution
   public static class SlowTaskJob implements Job {
      @Override
      public void execute(JobExecutionContext context) throws JobExecutionException {
         run(context);
      }

      static void run(JobExecutionContext context) {
         JobDataMap data = context.getMergedJobDataMap();
         Recorder recorder = RECORDERS.get(data.getString(RECORDER_KEY));
         Execution execution = new Execution(
            instanceId(context.getScheduler()), context.getTrigger().getKey().getName(),
            context.getScheduledFireTime().getTime());
         recorder.maxRunning.accumulateAndGet(recorder.running.incrementAndGet(), Math::max);
         recorder.executions.add(execution);

         try {
            if(data.containsKey(HANG_KEY)) {
               recorder.release.await(1, TimeUnit.MINUTES);
            }
            else {
               Thread.sleep(data.containsKey(DURATION_KEY) ? data.getLong(DURATION_KEY) : DURATION);
            }
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
         }
         finally {
            execution.end = System.currentTimeMillis();
            recorder.running.decrementAndGet();
         }
      }
   }

   /**
    * Stands in for ScheduleTaskCloudJob: the store tells a cloud runner job by its class.
    */
   public static class SlowCloudJob extends ScheduleTaskCloudJob {
      @Override
      public void execute(JobExecutionContext context) {
         SlowTaskJob.run(context);
      }
   }

   private static final class Recorder {
      final Queue<Execution> executions = new ConcurrentLinkedQueue<>();
      final AtomicInteger running = new AtomicInteger();
      final AtomicInteger maxRunning = new AtomicInteger();
      final CountDownLatch release = new CountDownLatch(1);
   }

   private static final class Execution {
      Execution(String schedulerId, String trigger, long scheduledFireTime) {
         this.schedulerId = schedulerId;
         this.trigger = trigger;
         this.scheduledFireTime = scheduledFireTime;
         this.start = System.currentTimeMillis();
      }

      @Override
      public String toString() {
         return String.format("%s fired %s: scheduled %tT.%<tL, ran %tT.%<tL-%tT.%<tL",
                              schedulerId, trigger, scheduledFireTime, start, end);
      }

      final String schedulerId;
      final String trigger;
      final long scheduledFireTime;
      final long start;
      volatile long end;
   }

   private static final long IDLE_WAIT = 2000;
   private static final long MISFIRE_THRESHOLD = 500;
   private static final long INTERVAL = 2000;
   private static final long DURATION = INTERVAL + 1500;
   private static final long OBSERVATION = 15000;
   // a released trigger is past due, so it fires as soon as the completing node is signalled;
   // allow a full acquire horizon plus the misfire threshold before calling it stranded
   private static final long MAX_IDLE = IDLE_WAIT + MISFIRE_THRESHOLD + 500;
   private static final String RECORDER_KEY = "test.recorder";
   private static final String DURATION_KEY = "test.duration";
   private static final String HANG_KEY = "test.hang";
   private static final String DEFAULT_TIMEOUT = "600000";
   // the store stamps a run's start before the job starts, so the release is measured from a
   // moment that can be this much earlier than the recorded start of the run
   private static final long SLACK = 500;
   // schedule.task.timeout as the stores read it
   private static volatile String taskTimeout = DEFAULT_TIMEOUT;
   private static final IdentityID OWNER = new IdentityID("scheduler-test", "host");
   private static final Map<String, Recorder> RECORDERS = new ConcurrentHashMap<>();

   private final String recorderId = UUID.randomUUID().toString();
   private final List<org.quartz.Scheduler> schedulers = new ArrayList<>();
   private ApplicationContext savedAppContext;
   private IgniteLikeCluster cluster;
   private final Map<String, StoppingJobStore> stores = new HashMap<>();
   private Principal savedPrincipal;
}
