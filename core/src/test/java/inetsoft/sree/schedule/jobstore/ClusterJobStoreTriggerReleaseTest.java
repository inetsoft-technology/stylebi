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
 * <p>Two real Quartz schedulers built like {@code Scheduler.initialize0()} share one cluster whose
 * replicated maps keep and hand out serialized copies, like Ignite's. Timings are the production
 * ones scaled by 1/10 (acquire horizon 2 s, misfire threshold 0.5 s).
 */
@Tag("core")
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
    * A run that never completes (its node died or was scaled in) stands in here as a run that
    * hangs until the test ends. The task's run-once condition must still fire at its time, and
    * its completion must release the hung run's recurring trigger, as before the fix.
    */
   @Test
   void runThatNeverCompletesDoesNotStopTheOtherConditions() throws Exception {
      startSchedulers("node-A", "node-B");
      long start = (System.currentTimeMillis() / 1000 + 2) * 1000;
      long runOnce = start + 3000;
      JobDetail job = createJob();

      IntervalTrigger recurring = recurringTrigger(job, 1, start);
      recurring.getJobDataMap().put(HANG_KEY, true);

      TimeConditionTriggerImpl atTrigger = runOnceTrigger(job, 2, runOnce, 500);

      Set<Trigger> triggers = new HashSet<>();
      triggers.add(recurring);
      triggers.add(atTrigger);
      schedulers.get(0).scheduleJob(job, triggers, true);

      Thread.sleep(runOnce + 500 + MAX_IDLE + 1000 - System.currentTimeMillis());

      List<Execution> executions = executions();
      String trace = trace(executions);
      Map<String, Long> perTrigger = executions.stream()
         .collect(Collectors.groupingBy(e -> e.trigger, TreeMap::new, Collectors.counting()));

      assertEquals(1L, perTrigger.getOrDefault(atTrigger.getName(), 0L),
                   "run-once condition did not fire once:" + trace);
      assertTrue(perTrigger.getOrDefault(recurring.getName(), 0L) >= 2,
                 "hung run's recurring trigger was never released:" + trace);
      assertDistinctFireTimes(executions, trace);
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
      // one worker per node, so the node running run 1 cannot acquire trigger 2 itself before
      // run 1 ends and trigger 2 can only be held by the other node
      startSchedulers(1, "node-A", "node-B");
      long t1 = (System.currentTimeMillis() / 1000 + 3) * 1000;
      long t2 = t1 + GAP;
      JobDetail job = createJob();
      TimeConditionTriggerImpl trigger1 = runOnceTrigger(job, 1, t1, GAP - 700);
      TimeConditionTriggerImpl trigger2 = runOnceTrigger(job, 2, t2, 500);
      Set<Trigger> triggers = new HashSet<>();
      triggers.add(trigger1);
      triggers.add(trigger2);
      schedulers.get(0).scheduleJob(job, triggers, true);

      await().atMost(Duration.ofMillis(t2 - System.currentTimeMillis()))
         .until(() -> !recorder().executions.isEmpty());
      String runner = recorder().executions.peek().schedulerId;
      org.quartz.Scheduler other = schedulers.stream()
         .filter(s -> !runner.equals(instanceId(s)))
         .findFirst().orElseThrow();

      // wake the other node's scheduler thread for an ordinary acquire pass while trigger 2 is
      // inside its acquire horizon and run 1 is still running (an unknown key changes nothing)
      Thread.sleep(Math.max(0, t2 - 1500 - System.currentTimeMillis()));
      other.resumeTrigger(TriggerKey.triggerKey("wake-up", "ClusterJobStoreTriggerReleaseTest"));
      Thread.sleep(Math.max(0, t2 - 1200 - System.currentTimeMillis()));

      DistributedMap<TriggerKey, TriggerWrapper> triggersByKey =
         cluster.getReplicatedMap("jobstore.triggersByKey");
      TriggerWrapper held = triggersByKey.get(trigger2.getKey());
      assertEquals(TriggerState.ACQUIRED, held.getState(), "trigger 2 not held: " + held);

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
      await().atMost(Duration.ofMillis(INTERVAL + MAX_IDLE))
         .until(() -> !recorder().executions.isEmpty());
      return recorder().executions.peek().schedulerId;
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
      public void lock(K key) {
         delegate.lock(key);
      }

      @Override
      public void lock(K key, long leaseTime, TimeUnit timeUnit) {
         delegate.lock(key, leaseTime, timeUnit);
      }

      @Override
      public void unlock(K key) {
         delegate.unlock(key);
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
   private static final long GAP = 6000;
   private static final long DURATION = INTERVAL + 1500;
   private static final long OBSERVATION = 15000;
   // a released trigger is past due, so it fires as soon as the completing node is signalled;
   // allow a full acquire horizon plus the misfire threshold before calling it stranded
   private static final long MAX_IDLE = IDLE_WAIT + MISFIRE_THRESHOLD + 500;
   private static final String RECORDER_KEY = "test.recorder";
   private static final String DURATION_KEY = "test.duration";
   private static final String HANG_KEY = "test.hang";
   private static final IdentityID OWNER = new IdentityID("scheduler-test", "host");
   private static final Map<String, Recorder> RECORDERS = new ConcurrentHashMap<>();

   private final String recorderId = UUID.randomUUID().toString();
   private final List<org.quartz.Scheduler> schedulers = new ArrayList<>();
   private ApplicationContext savedAppContext;
   private IgniteLikeCluster cluster;
   private final Map<String, StoppingJobStore> stores = new HashMap<>();
   private Principal savedPrincipal;
}
