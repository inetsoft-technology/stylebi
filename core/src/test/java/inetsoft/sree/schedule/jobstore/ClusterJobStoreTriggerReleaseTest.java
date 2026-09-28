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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
      startSchedulers(1, true, "node-A", "node-B");
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
      RECORDERS.put(recorderId, new Recorder());
      String taskId = "task-76976-recurring";
      JobDataMap dataMap = new JobDataMap();
      ScheduleTask task = new ScheduleTask(taskId);
      task.setOwner(OWNER);
      dataMap.put(ScheduleTask.class.getName(), task);
      dataMap.put(RECORDER_KEY, recorderId);
      return JobBuilder.newJob(SlowTaskJob.class)
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
      startSchedulers(3, false, instanceIds);
   }

   private void startSchedulers(int threads, boolean stoppable, String... instanceIds)
      throws Exception
   {
      cluster = new IgniteLikeCluster();
      installContext(cluster);

      for(String instanceId : instanceIds) {
         String name = "inetsoft-" + instanceId + "-" + recorderId;
         DirectSchedulerFactory factory = DirectSchedulerFactory.getInstance();
         ClusterJobStore jobStore = stoppable ? new StoppingJobStore() : new ClusterJobStore();

         if(stoppable) {
            stores.put(instanceId, (StoppingJobStore) jobStore);
         }

         jobStore.setMisfireThreshold(MISFIRE_THRESHOLD);
         factory.createScheduler(
            name, instanceId, new SimpleThreadPool(threads, Thread.NORM_PRIORITY),
            jobStore, null, 0, IDLE_WAIT, -1);
         org.quartz.Scheduler scheduler = factory.getScheduler(name);
         scheduler.getListenerManager().addJobListener(
            new JobCompletionListener("TaskCompletionListener"));
         scheduler.start();
         schedulers.add(scheduler);
      }
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
    * A store whose node is going away. Once stopping, the scheduler's own release of acquired
    * triggers is ignored (Quartz can miss it on halt), and a dying node's store releases nothing
    * on shutdown either.
    */
   private static final class StoppingJobStore extends ClusterJobStore {
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
   }

   /**
    * A MockCluster whose replicated maps keep and hand out serialized copies, like an Ignite
    * replicated cache.
    */
   private static final class IgniteLikeCluster extends MockCluster {
      @Override
      public <K, V> DistributedMap<K, V> getReplicatedMap(String name) {
         return new CopyingMap<>(super.getReplicatedMap(name));
      }
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
   private Cluster cluster;
   private final Map<String, StoppingJobStore> stores = new HashMap<>();
   private Principal savedPrincipal;
}
