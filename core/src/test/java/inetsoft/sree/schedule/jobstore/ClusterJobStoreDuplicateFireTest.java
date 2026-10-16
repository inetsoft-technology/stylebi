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
import org.springframework.context.ApplicationContext;

import java.io.*;
import java.security.Principal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #76976: in a cloud-mode cluster every server runs Quartz in-process on the shared
 * {@link ClusterJobStore}, and a task with two "run once" (AT) conditions ran three times for two
 * fire times once its runs outlasted the gap between the conditions (or ended just before the
 * second one).
 *
 * <p>Each test starts real Quartz schedulers built like {@code Scheduler.initialize0()}, each on
 * its own {@code ClusterJobStore}, sharing one cluster whose replicated maps keep and return
 * serialized copies like Ignite's. The real {@link JobCompletionListener} removes each fired AT trigger. Timings are the
 * production ones scaled by 1/10: acquire horizon (idleWaitTime) 20 s -> 2 s, misfire threshold
 * 5 s -> 0.5 s, gap between the AT conditions 60 s -> 6 s.
 */
@Tag("slow")
class ClusterJobStoreDuplicateFireTest {
   @BeforeEach
   void setUp() {
      savedAppContext = ConfigurationContext.getContext().getApplicationContext();
      // ClusterJobStore.storeJob sets the task owner as this thread's principal
      savedPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void tearDown() throws Exception {
      for(org.quartz.Scheduler scheduler : schedulers) {
         scheduler.shutdown(true);
      }

      RECORDERS.remove(recorderId);
      ConfigurationContext.getContext().setApplicationContext(savedAppContext);
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   /**
    * Run 1 outlasts the gap (W2): trigger 2 fires at t2, and when run 1 ends its completion
    * turned trigger 2's stale ACQUIRED entry back into WAITING, so trigger 2 fired again.
    */
   @Test
   void runOutlastingTheGapRunsEachFireTimeOnce() throws Exception {
      startSchedulers(new IgniteLikeCluster(), 3, "node-A", "node-B");
      long duration = GAP + 1500;
      long t1 = scheduleTwoRunOnceTriggers(duration);

      awaitQuiet(t1 + GAP, duration);
      assertOneExecutionPerFireTime();
   }

   /**
    * Run 1 ends shortly before t2 (W1) while the other node already holds trigger 2: the
    * completion reset trigger 2 to WAITING, this node acquired it again, and both nodes fired it.
    */
   @Test
   void runEndingJustBeforeASiblingHeldByTheOtherNodeRunsEachFireTimeOnce() throws Exception {
      // one worker per node, so the node running run 1 cannot acquire trigger 2 itself before
      // run 1 ends and trigger 2 can only be held by the other node
      startSchedulers(new IgniteLikeCluster(), 1, "node-A", "node-B");
      long duration = GAP - 700;
      long t1 = scheduleTwoRunOnceTriggers(duration);
      long t2 = t1 + GAP;

      await().atMost(Duration.ofMillis(t2 - System.currentTimeMillis()))
         .until(() -> !recorder().executions.isEmpty());
      String runner = recorder().executions.peek().schedulerId;
      org.quartz.Scheduler other = schedulers.stream()
         .filter(s -> !runner.equals(instanceId(s)))
         .findFirst().orElseThrow();

      // wake the other node's scheduler thread for an ordinary acquire pass while trigger 2 is
      // inside its acquire horizon and run 1 is still running (an unknown key changes nothing)
      Thread.sleep(Math.max(0, t2 - 1500 - System.currentTimeMillis()));
      other.resumeTrigger(TriggerKey.triggerKey("wake-up", "ClusterJobStoreDuplicateFireTest"));

      awaitQuiet(t2, duration);
      assertOneExecutionPerFireTime();
   }

   /**
    * Run 1 ends before trigger 2 enters any node's acquire horizon: each fire time runs once.
    */
   @Test
   void runEndingBeforeTheSiblingIsAcquiredRunsEachFireTimeOnce() throws Exception {
      startSchedulers(new IgniteLikeCluster(), 3, "node-A", "node-B");
      long duration = GAP - IDLE_WAIT - 1500;
      long t1 = scheduleTwoRunOnceTriggers(duration);

      awaitQuiet(t1 + GAP, duration);
      assertOneExecutionPerFireTime();
   }

   /**
    * Local (RMI scheduler) mode runs one scheduler on the same store, and its job class has the
    * same Quartz shape (DisallowConcurrentExecution), so a run outlasting the gap must not repeat
    * trigger 2 there either.
    */
   @Test
   void singleSchedulerRunOutlastingTheGapRunsEachFireTimeOnce() throws Exception {
      startSchedulers(new IgniteLikeCluster(), 3, "node-A");
      long duration = GAP + 1500;
      long t1 = scheduleTwoRunOnceTriggers(duration);

      awaitQuiet(t1 + GAP, duration);
      assertOneExecutionPerFireTime();
   }

   private void startSchedulers(Cluster cluster, int threads, String... instanceIds)
      throws Exception
   {
      installContext(cluster);

      for(String instanceId : instanceIds) {
         String name = "inetsoft-" + instanceId + "-" + recorderId;
         DirectSchedulerFactory factory = DirectSchedulerFactory.getInstance();
         ClusterJobStore jobStore = new ClusterJobStore();
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

   /**
    * Schedules the task's job with two AT triggers, GAP apart, from the first node, the way
    * {@code Scheduler.addTask} does. Returns the first fire time.
    */
   private long scheduleTwoRunOnceTriggers(long duration) throws Exception {
      RECORDERS.put(recorderId, new Recorder());
      String taskId = "task-76976";
      ScheduleTask task = new ScheduleTask(taskId);
      task.setOwner(OWNER);

      JobDataMap dataMap = new JobDataMap();
      dataMap.put(ScheduleTask.class.getName(), task);
      dataMap.put(RECORDER_KEY, recorderId);
      dataMap.put(DURATION_KEY, duration);
      JobDetail job = JobBuilder.newJob(SlowTaskJob.class)
         .withIdentity(taskId, inetsoft.sree.schedule.Scheduler.GROUP_NAME)
         .storeDurably(true)
         .usingJobData(dataMap)
         .build();

      long t1 = (System.currentTimeMillis() / 1000 + 3) * 1000;
      Set<Trigger> triggers = new HashSet<>();
      triggers.add(runOnceTrigger(taskId, 1, t1));
      triggers.add(runOnceTrigger(taskId, 2, t1 + GAP));
      schedulers.get(0).scheduleJob(job, triggers, true);
      return t1;
   }

   private static TimeConditionTriggerImpl runOnceTrigger(String taskId, int index, long time) {
      TimeConditionTriggerImpl trigger = new TimeConditionTriggerImpl();
      trigger.setName(taskId + "-" + index);
      trigger.setGroup(inetsoft.sree.schedule.Scheduler.GROUP_NAME);
      trigger.setJobKey(new JobKey(taskId, inetsoft.sree.schedule.Scheduler.GROUP_NAME));
      trigger.setCondition(TimeCondition.at(new Date(time)));
      trigger.setStartTime(new Date(time));
      trigger.setEndTime(new Date(time + TimeUnit.HOURS.toMillis(1)));
      trigger.setMisfireInstruction(ConditionTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
      return trigger;
   }

   /**
    * Waits until nothing is running, the last fire time plus one run has passed, and no run has
    * started for longer than it takes a revived trigger to be acquired and fired.
    */
   private void awaitQuiet(long lastFireTime, long duration) {
      long quiet = IDLE_WAIT + MISFIRE_THRESHOLD + 1000;
      await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(100)).until(() -> {
         Recorder recorder = recorder();
         long now = System.currentTimeMillis();
         return recorder.running.get() == 0 && now >= lastFireTime + duration + IDLE_WAIT &&
            now - recorder.lastStart >= quiet;
      });
   }

   private void assertOneExecutionPerFireTime() {
      List<Execution> executions = new ArrayList<>(recorder().executions);
      Map<String, Long> perTrigger = executions.stream()
         .collect(Collectors.groupingBy(e -> e.trigger, TreeMap::new, Collectors.counting()));
      Map<String, Long> expected = new TreeMap<>();
      expected.put("task-76976-1", 1L);
      expected.put("task-76976-2", 1L);
      String trace = executions.stream().map(Execution::toString)
         .collect(Collectors.joining("\n  ", "\n  ", ""));
      assertEquals(expected, perTrigger, "executions per trigger:" + trace);
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
    * {@code ConfigurationContext}, the same way {@code SchedulerTestHarness} does, but with a real
    * cluster so both stores share its maps.
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
    * A MockCluster whose replicated maps keep and hand out serialized copies, like an Ignite
    * replicated cache: a value put in the map is not shared with the caller's object, and every
    * read returns a fresh copy. Copying on read alone is not enough here, because the store puts
    * the trigger it returns to Quartz, and Quartz then updates that same object.
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
    * Stands in for ScheduleTaskCloudJob / ScheduleTaskJob, which carry the same Quartz shape:
    * DisallowConcurrentExecution and no PersistJobDataAfterExecution.
    */
   @DisallowConcurrentExecution
   public static class SlowTaskJob implements Job {
      @Override
      public void execute(JobExecutionContext context) throws JobExecutionException {
         JobDataMap data = context.getMergedJobDataMap();
         Recorder recorder = RECORDERS.get(data.getString(RECORDER_KEY));
         Execution execution = new Execution(
            instanceId(context.getScheduler()), context.getTrigger().getKey().getName(),
            context.getScheduledFireTime().getTime(), context.getFireTime().getTime());
         recorder.running.incrementAndGet();
         recorder.lastStart = execution.start;
         recorder.executions.add(execution);

         try {
            Thread.sleep(data.getLong(DURATION_KEY));
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
      volatile long lastStart;
   }

   private static final class Execution {
      Execution(String schedulerId, String trigger, long scheduledFireTime, long fireTime) {
         this.schedulerId = schedulerId;
         this.trigger = trigger;
         this.scheduledFireTime = scheduledFireTime;
         this.fireTime = fireTime;
         this.start = System.currentTimeMillis();
      }

      @Override
      public String toString() {
         return String.format("%s fired %s: scheduled %tT.%<tL, fired %tT.%<tL, ran %tT.%<tL-%tT.%<tL",
                              schedulerId, trigger, scheduledFireTime, fireTime, start, end);
      }

      final String schedulerId;
      final String trigger;
      final long scheduledFireTime;
      final long fireTime;
      final long start;
      volatile long end;
   }

   private static final long IDLE_WAIT = 2000;
   private static final long MISFIRE_THRESHOLD = 500;
   private static final long GAP = 6000;
   private static final String RECORDER_KEY = "test.recorder";
   private static final String DURATION_KEY = "test.duration";
   private static final IdentityID OWNER = new IdentityID("scheduler-test", "host");
   private static final Map<String, Recorder> RECORDERS = new ConcurrentHashMap<>();

   private final String recorderId = UUID.randomUUID().toString();
   private final List<org.quartz.Scheduler> schedulers = new ArrayList<>();
   private ApplicationContext savedAppContext;
   private Principal savedPrincipal;
}
