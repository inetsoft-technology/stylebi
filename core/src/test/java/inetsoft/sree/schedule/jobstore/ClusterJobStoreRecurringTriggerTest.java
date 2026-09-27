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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #76976: the store keeps the triggers of a non-concurrent job blocked while one of them
 * runs and releases them when it completes. A recurring trigger whose run outlasts its interval
 * must be released after every run, so it keeps firing, and must never run concurrently.
 *
 * <p>Two real Quartz schedulers built like {@code Scheduler.initialize0()} share one cluster whose
 * replicated maps keep and hand out serialized copies, like Ignite's. Timings are the production
 * ones scaled by 1/10 (acquire horizon 2 s, misfire threshold 0.5 s).
 */
@Tag("core")
class ClusterJobStoreRecurringTriggerTest {
   @BeforeEach
   void setUp() {
      savedAppContext = ConfigurationContext.getContext().getApplicationContext();
      savedPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void tearDown() throws Exception {
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
      RECORDERS.put(recorderId, new Recorder());
      String taskId = "task-76976-recurring";

      JobDataMap dataMap = new JobDataMap();
      ScheduleTask task = new ScheduleTask(taskId);
      task.setOwner(OWNER);
      dataMap.put(ScheduleTask.class.getName(), task);
      dataMap.put(RECORDER_KEY, recorderId);
      JobDetail job = JobBuilder.newJob(SlowTaskJob.class)
         .withIdentity(taskId, inetsoft.sree.schedule.Scheduler.GROUP_NAME)
         .storeDurably(true)
         .usingJobData(dataMap)
         .build();

      long start = (System.currentTimeMillis() / 1000 + 2) * 1000;
      IntervalTrigger trigger = new IntervalTrigger();
      trigger.setName(taskId + "-1");
      trigger.setGroup(inetsoft.sree.schedule.Scheduler.GROUP_NAME);
      trigger.setJobKey(job.getKey());
      // a daily condition, so the completion listener keeps the trigger
      trigger.setCondition(TimeCondition.at(1, 0, 0));
      trigger.setStartTime(new Date(start));
      trigger.setEndTime(new Date(start + TimeUnit.HOURS.toMillis(1)));
      trigger.setMisfireInstruction(ConditionTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
      schedulers.get(0).scheduleJob(job, Collections.singleton(trigger), true);

      Thread.sleep(start + OBSERVATION - System.currentTimeMillis());

      Recorder recorder = recorder();
      List<Execution> executions = new ArrayList<>(recorder.executions);
      executions.sort(Comparator.comparingLong(e -> e.start));
      String trace = executions.stream().map(Execution::toString)
         .collect(Collectors.joining("\n  ", "\n  ", ""));

      assertEquals(1, recorder.maxRunning.get(), "runs overlapped:" + trace);
      assertTrue(executions.size() >= 3, "trigger stopped firing:" + trace);

      for(int i = 1; i < executions.size(); i++) {
         long idle = executions.get(i).start - executions.get(i - 1).end;
         assertTrue(idle <= MAX_IDLE, "trigger was not released after run " + i + ":" + trace);
      }
   }

   private void startSchedulers(String... instanceIds) throws Exception {
      installContext(new IgniteLikeCluster());

      for(String instanceId : instanceIds) {
         String name = "inetsoft-" + instanceId + "-" + recorderId;
         DirectSchedulerFactory factory = DirectSchedulerFactory.getInstance();
         ClusterJobStore jobStore = new ClusterJobStore();
         jobStore.setMisfireThreshold(MISFIRE_THRESHOLD);
         factory.createScheduler(
            name, instanceId, new SimpleThreadPool(3, Thread.NORM_PRIORITY),
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
         Recorder recorder = RECORDERS.get(context.getMergedJobDataMap().getString(RECORDER_KEY));
         Execution execution = new Execution(instanceId(context.getScheduler()));
         recorder.maxRunning.accumulateAndGet(recorder.running.incrementAndGet(), Math::max);
         recorder.executions.add(execution);

         try {
            Thread.sleep(DURATION);
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
   }

   private static final class Execution {
      Execution(String schedulerId) {
         this.schedulerId = schedulerId;
         this.start = System.currentTimeMillis();
      }

      @Override
      public String toString() {
         return String.format("%s ran %tT.%<tL-%tT.%<tL", schedulerId, start, end);
      }

      final String schedulerId;
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
   private static final IdentityID OWNER = new IdentityID("scheduler-test", "host");
   private static final Map<String, Recorder> RECORDERS = new ConcurrentHashMap<>();

   private final String recorderId = UUID.randomUUID().toString();
   private final List<org.quartz.Scheduler> schedulers = new ArrayList<>();
   private ApplicationContext savedAppContext;
   private Principal savedPrincipal;
}
