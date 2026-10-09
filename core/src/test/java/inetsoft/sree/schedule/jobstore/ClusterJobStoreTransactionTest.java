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

import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.internal.cluster.ignite.*;
import inetsoft.util.ConfigurationContext;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.*;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.quartz.*;
import org.quartz.Calendar;
import org.quartz.impl.triggers.SimpleTriggerImpl;
import org.quartz.spi.*;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77879: the store's sections run in pessimistic Ignite transactions instead of holding
 * explicit cache-entry locks. A trigger that fires joins the job's running record to its own
 * transaction, so a failure rolls both back, and a transaction that fails when a run completes
 * is logged without stopping the release of the job's triggers. Runs on one embedded Ignite node
 * with the product's replicated cache configuration.
 */
@Tag("core")
class ClusterJobStoreTransactionTest {
   @BeforeAll
   static void startIgnite(@TempDir Path workDir) {
      IgniteConfiguration config = new IgniteConfiguration();
      config.setIgniteInstanceName("ClusterJobStoreTransactionTest");
      config.setWorkDirectory(workDir.toString());
      config.setMetricsLogFrequency(0);
      IgniteUtils.configBinaryTypes(config);

      TcpDiscoverySpi discovery = new TcpDiscoverySpi();
      discovery.setLocalAddress("127.0.0.1");
      discovery.setLocalPort(48740);
      discovery.setLocalPortRange(5);
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(List.of("127.0.0.1:48740..48744"));
      discovery.setIpFinder(ipFinder);
      config.setDiscoverySpi(discovery);

      TcpCommunicationSpi communication = new TcpCommunicationSpi();
      communication.setLocalAddress("127.0.0.1");
      communication.setLocalPort(48840);
      communication.setLocalPortRange(10);
      config.setCommunicationSpi(communication);

      DataStorageConfiguration storage = new DataStorageConfiguration();
      storage.getDefaultDataRegionConfiguration()
         .setInitialSize(64L << 20)
         .setMaxSize(64L << 20);
      config.setDataStorageConfiguration(storage);
      ignite = Ignition.start(config);
   }

   @AfterAll
   static void stopIgnite() {
      if(ignite != null) {
         ignite.close();
         ignite = null;
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      for(String name : ignite.cacheNames()) {
         ignite.cache(name).clear();
      }

      savedAppContext = ConfigurationContext.getContext().getApplicationContext();
      cluster = new IgniteBackedCluster();
      installContext(cluster);
      store = new ClusterJobStore();
      store.initialize(null, mock(SchedulerSignaler.class));
      store.schedulerStarted();
      triggersByKey = cluster.getReplicatedMap("jobstore.triggersByKey");
      runningJobs = cluster.getReplicatedMap("jobstore.runningJobs");
   }

   @AfterEach
   void tearDown() {
      FailingTrigger.FAIL = false;
      ConfigurationContext.getContext().setApplicationContext(savedAppContext);
   }

   /**
    * Firing a trigger of a job that disallows concurrent execution records the run within the
    * trigger's transaction: startRun joins it instead of starting a nested one, which Ignite
    * refuses.
    */
   @Test
   void firedTriggerRecordsTheRunInItsTransaction() throws Exception {
      JobDetail job = createJob();
      OperableTrigger trigger = createTrigger(new SimpleTriggerImpl(), job);
      store.storeJobAndTrigger(job, trigger);

      OperableTrigger acquired = acquire();
      List<TriggerFiredResult> results = store.triggersFired(List.of(acquired));

      assertEquals(1, results.size());
      assertNotNull(results.get(0).getTriggerFiredBundle(), "the trigger did not fire");
      RunningJob run = runningJobs.get(job.getKey());
      assertNotNull(run, "the run was not recorded");
      assertEquals(acquired.getFireInstanceId(), run.getFireInstanceId());
      assertEquals(TriggerState.BLOCKED, triggersByKey.get(trigger.getKey()).getState());
   }

   /**
    * A fire that fails after the run was recorded rolls back the record with the trigger, so the
    * record does not hold back the job's other triggers and the trigger stays acquired.
    */
   @Test
   void failedFireRollsBackTheRunRecord() throws Exception {
      JobDetail job = createJob();
      OperableTrigger trigger = createTrigger(new FailingTrigger(), job);
      store.storeJobAndTrigger(job, trigger);
      OperableTrigger acquired = acquire();
      FailingTrigger.FAIL = true;

      assertThrows(IllegalStateException.class, () -> store.triggersFired(List.of(acquired)));

      assertNull(runningJobs.get(job.getKey()), "the failed fire left its run record");
      TriggerWrapper stored = triggersByKey.get(trigger.getKey());
      assertEquals(TriggerState.ACQUIRED, stored.getState());
      assertEquals(acquired.getFireInstanceId(), stored.trigger.getFireInstanceId());
   }

   /**
    * When the transaction that removes the run record fails, the completion still releases the
    * job's blocked trigger; the record stops being honoured at the task timeout.
    */
   @Test
   void failedRunRecordRemovalStillReleasesTheTrigger() throws Exception {
      JobDetail job = createJob();
      OperableTrigger trigger = createTrigger(new SimpleTriggerImpl(), job);
      store.storeJobAndTrigger(job, trigger);
      OperableTrigger acquired = acquire();
      store.triggersFired(List.of(acquired));
      cluster.failures.set(1);

      assertDoesNotThrow(() -> store.triggeredJobComplete(
         acquired, job, Trigger.CompletedExecutionInstruction.NOOP));

      assertEquals(TriggerState.WAITING, triggersByKey.get(trigger.getKey()).getState());
      assertNotNull(runningJobs.get(job.getKey()), "the failed removal was committed");
   }

   /**
    * A completion whose transactions all fail is logged and leaves the store as it was.
    */
   @Test
   void failedReleaseIsLogged() throws Exception {
      JobDetail job = createJob();
      OperableTrigger trigger = createTrigger(new SimpleTriggerImpl(), job);
      store.storeJobAndTrigger(job, trigger);
      OperableTrigger acquired = acquire();
      store.triggersFired(List.of(acquired));
      cluster.failures.set(2);

      assertDoesNotThrow(() -> store.triggeredJobComplete(
         acquired, job, Trigger.CompletedExecutionInstruction.NOOP));

      assertEquals(TriggerState.BLOCKED, triggersByKey.get(trigger.getKey()).getState());
      assertNotNull(runningJobs.get(job.getKey()));
   }

   /**
    * Removing a job's last trigger removes the job too, after the trigger's transaction.
    */
   @Test
   void removingTheLastTriggerRemovesTheJob() throws Exception {
      JobDetail job = JobBuilder.newJob(NonConcurrentJob.class)
         .withIdentity("job", "ClusterJobStoreTransactionTest").build();
      OperableTrigger trigger = createTrigger(new SimpleTriggerImpl(), job);
      store.storeJobAndTrigger(job, trigger);

      assertTrue(store.removeTrigger(trigger.getKey()));

      assertNull(triggersByKey.get(trigger.getKey()));
      assertFalse(store.checkExists(job.getKey()), "the orphaned job was not removed");
      assertTrue(store.getTriggerKeys(
         org.quartz.impl.matchers.GroupMatcher.anyTriggerGroup()).isEmpty());
   }

   /**
    * Pausing and resuming a job changes each of its triggers, each in its own transaction.
    */
   @Test
   void pauseAndResumeJob() throws Exception {
      JobDetail job = createJob();
      OperableTrigger trigger1 = createTrigger(new SimpleTriggerImpl(), job);
      OperableTrigger trigger2 = createTrigger(new SimpleTriggerImpl(), job);
      trigger2.setKey(TriggerKey.triggerKey("trigger2", "ClusterJobStoreTransactionTest"));
      store.storeJobAndTrigger(job, trigger1);
      store.storeTrigger(trigger2, false);

      store.pauseJob(job.getKey());
      assertEquals(Trigger.TriggerState.PAUSED, store.getTriggerState(trigger1.getKey()));
      assertEquals(Trigger.TriggerState.PAUSED, store.getTriggerState(trigger2.getKey()));

      store.resumeJob(job.getKey());
      assertEquals(Trigger.TriggerState.NORMAL, store.getTriggerState(trigger1.getKey()));
      assertEquals(Trigger.TriggerState.NORMAL, store.getTriggerState(trigger2.getKey()));
   }

   private OperableTrigger acquire() throws JobPersistenceException {
      List<OperableTrigger> acquired =
         store.acquireNextTriggers(System.currentTimeMillis() + 60_000, 1, 0L);
      assertEquals(1, acquired.size(), "the trigger was not acquired");
      return acquired.get(0);
   }

   private static JobDetail createJob() {
      return JobBuilder.newJob(NonConcurrentJob.class)
         .withIdentity("job", "ClusterJobStoreTransactionTest")
         .storeDurably()
         .build();
   }

   private static OperableTrigger createTrigger(SimpleTriggerImpl trigger, JobDetail job) {
      trigger.setKey(TriggerKey.triggerKey("trigger", "ClusterJobStoreTransactionTest"));
      trigger.setJobKey(job.getKey());
      trigger.setStartTime(new Date(System.currentTimeMillis() + 1000));
      trigger.setRepeatCount(SimpleTrigger.REPEAT_INDEFINITELY);
      trigger.setRepeatInterval(TimeUnit.HOURS.toMillis(1));
      trigger.computeFirstFireTime(null);
      return trigger;
   }

   private static void installContext(Cluster cluster) {
      PropertiesEngine properties = mock(PropertiesEngine.class);
      when(properties.getProperty(any(String.class), any(String.class), anyBoolean()))
         .thenAnswer(inv -> inv.getArgument(1));
      ApplicationContext context = mock(ApplicationContext.class);
      when(context.getBean(Cluster.class)).thenReturn(cluster);
      when(context.getBean(PropertiesEngine.class)).thenReturn(properties);
      ConfigurationContext.getContext().setApplicationContext(context);
   }

   @SuppressWarnings("unchecked")
   private static <K, V> org.apache.ignite.IgniteCache<K, V> cache(String name) {
      try {
         Method method = IgniteCluster.class.getDeclaredMethod(
            "getCacheConfiguration", String.class, CacheMode.class, int.class);
         method.setAccessible(true);
         CacheConfiguration<K, V> config =
            (CacheConfiguration<K, V>) method.invoke(null, name, CacheMode.REPLICATED, 2);
         return ignite.getOrCreateCache(config);
      }
      catch(ReflectiveOperationException e) {
         throw new IllegalStateException(e);
      }
   }

   /**
    * A cluster whose replicated maps and transactions are Ignite's, and whose next transactions
    * can be made to fail as a timed out one would.
    */
   private static final class IgniteBackedCluster extends MockCluster {
      @Override
      public <K, V> DistributedMap<K, V> getReplicatedMap(String name) {
         return new IgniteDistributedMap<>(cache(name));
      }

      @Override
      public <K, V> MultiMap<K, V> getReplicatedMultiMap(String name) {
         return new IgniteMultiMap<>(ClusterJobStoreTransactionTest.<K, Collection<V>>cache(name));
      }

      @Override
      public String getLocalMember() {
         return "test-member";
      }

      @Override
      public <T, E extends Exception> T runInTransaction(long timeout, TimeUnit unit,
                                                          TransactionalAction<T, E> action)
         throws E
      {
         if(failures.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            throw new DistributedTransactionException("The transaction timed out");
         }

         return IgniteCluster.runInTransaction(ignite, timeout, unit, action);
      }

      final AtomicInteger failures = new AtomicInteger();
   }

   @DisallowConcurrentExecution
   public static class NonConcurrentJob implements Job {
      @Override
      public void execute(JobExecutionContext context) {
      }
   }

   /**
    * A trigger whose firing fails while {@link #FAIL} is set.
    */
   public static class FailingTrigger extends SimpleTriggerImpl {
      @Override
      public void triggered(Calendar calendar) {
         if(FAIL) {
            throw new IllegalStateException("firing failed");
         }

         super.triggered(calendar);
      }

      static volatile boolean FAIL;
   }

   private static Ignite ignite;
   private ApplicationContext savedAppContext;
   private IgniteBackedCluster cluster;
   private ClusterJobStore store;
   private DistributedMap<TriggerKey, TriggerWrapper> triggersByKey;
   private DistributedMap<JobKey, RunningJob> runningJobs;
}
