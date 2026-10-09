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
 * is logged without stopping the release of the job's triggers. A trigger whose transaction fails
 * in an acquire pass or a fired batch does not keep the other triggers of the batch from being
 * acquired or fired. Runs on one embedded Ignite node
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

      int[] ports = freePorts(2);
      TcpDiscoverySpi discovery = new TcpDiscoverySpi();
      discovery.setLocalAddress("127.0.0.1");
      discovery.setLocalPort(ports[0]);
      discovery.setLocalPortRange(0);
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(List.of("127.0.0.1:" + ports[0]));
      discovery.setIpFinder(ipFinder);
      config.setDiscoverySpi(discovery);

      TcpCommunicationSpi communication = new TcpCommunicationSpi();
      communication.setLocalAddress("127.0.0.1");
      communication.setLocalPort(ports[1]);
      communication.setLocalPortRange(0);
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
    * record does not hold back the job's other triggers. The failure is returned as the trigger's
    * result, for which Quartz releases the trigger.
    */
   @Test
   void failedFireRollsBackTheRunRecord() throws Exception {
      JobDetail job = createJob();
      OperableTrigger trigger = createTrigger(new FailingTrigger(), job);
      store.storeJobAndTrigger(job, trigger);
      OperableTrigger acquired = acquire();
      FailingTrigger.FAIL = true;

      List<TriggerFiredResult> results = store.triggersFired(List.of(acquired));

      assertEquals(1, results.size());
      assertInstanceOf(IllegalStateException.class, results.get(0).getException());
      assertNull(results.get(0).getTriggerFiredBundle());
      assertNull(runningJobs.get(job.getKey()), "the failed fire left its run record");
      TriggerWrapper stored = triggersByKey.get(trigger.getKey());
      assertEquals(TriggerState.ACQUIRED, stored.getState());
      assertEquals(acquired.getFireInstanceId(), stored.trigger.getFireInstanceId());

      handleAsQuartz(List.of(acquired), results);

      assertEquals(TriggerState.WAITING, triggersByKey.get(trigger.getKey()).getState(),
                   "the failed trigger was not released");
   }

   /**
    * A trigger whose transaction fails in the middle of an acquire pass is skipped, left as it
    * was, and the triggers acquired before and after it are still returned, so none of them stays
    * acquired by this node without the scheduler knowing.
    */
   @Test
   void failedAcquireOfOneTriggerKeepsTheOthers() throws Exception {
      List<OperableTrigger> triggers = storeThreeTriggers(new SimpleTriggerImpl());
      TriggerState before = triggersByKey.get(triggers.get(1).getKey()).getState();
      cluster.failAfter.set(1);

      List<OperableTrigger> acquired =
         store.acquireNextTriggers(System.currentTimeMillis() + 60_000, 3, 0L);

      assertEquals(List.of(triggers.get(0).getKey(), triggers.get(2).getKey()),
                   acquired.stream().map(Trigger::getKey).toList());
      assertEquals(TriggerState.ACQUIRED,
                   triggersByKey.get(triggers.get(0).getKey()).getState());
      assertEquals(before, triggersByKey.get(triggers.get(1).getKey()).getState(),
                   "the failed acquisition changed the trigger");
      assertEquals(TriggerState.ACQUIRED,
                   triggersByKey.get(triggers.get(2).getKey()).getState());

      List<OperableTrigger> next =
         store.acquireNextTriggers(System.currentTimeMillis() + 60_000, 3, 0L);
      assertEquals(List.of(triggers.get(1).getKey()),
                   next.stream().map(Trigger::getKey).toList(),
                   "the skipped trigger was not acquired by the next pass");
   }

   /**
    * When every trigger's transaction fails, nothing was acquired and the failure is reported to
    * the scheduler.
    */
   @Test
   void failedAcquireOfEveryTriggerIsReported() throws Exception {
      List<OperableTrigger> triggers = storeThreeTriggers(new SimpleTriggerImpl());
      cluster.failures.set(3);

      assertThrows(DistributedTransactionException.class, () ->
         store.acquireNextTriggers(System.currentTimeMillis() + 60_000, 3, 0L));

      for(OperableTrigger trigger : triggers) {
         assertNotEquals(TriggerState.ACQUIRED, triggersByKey.get(trigger.getKey()).getState());
      }
   }

   /**
    * A trigger whose fire transaction fails in the middle of a batch gets a result with the
    * failure, for which Quartz releases it; the triggers before and after it fire.
    */
   @Test
   void failedFireTransactionInABatchKeepsTheOthers() throws Exception {
      List<OperableTrigger> acquired = acquireThree(new SimpleTriggerImpl());
      cluster.failAfter.set(1);

      List<TriggerFiredResult> results = store.triggersFired(acquired);

      assertFiredExceptTheMiddle(acquired, results, DistributedTransactionException.class);
   }

   /**
    * A trigger whose firing throws in the middle of a batch gets a result with the failure, and
    * the triggers before and after it fire.
    */
   @Test
   void failedFireInABatchKeepsTheOthers() throws Exception {
      List<OperableTrigger> acquired = acquireThree(new FailingTrigger());
      FailingTrigger.FAIL = true;

      List<TriggerFiredResult> results = store.triggersFired(acquired);

      assertFiredExceptTheMiddle(acquired, results, IllegalStateException.class);
   }

   private void assertFiredExceptTheMiddle(List<OperableTrigger> acquired,
                                           List<TriggerFiredResult> results,
                                           Class<? extends Exception> failure)
   {
      assertEquals(3, results.size(), "each trigger must get its result");

      for(int i : new int[] { 0, 2 }) {
         OperableTrigger trigger = acquired.get(i);
         assertNotNull(results.get(i).getTriggerFiredBundle(), trigger + " did not fire");
         assertEquals(trigger.getKey(),
                      results.get(i).getTriggerFiredBundle().getTrigger().getKey());
         assertEquals(TriggerState.BLOCKED, triggersByKey.get(trigger.getKey()).getState());
         assertNotNull(runningJobs.get(trigger.getJobKey()), "the run was not recorded");
      }

      OperableTrigger middle = acquired.get(1);
      assertInstanceOf(failure, results.get(1).getException());
      assertNull(results.get(1).getTriggerFiredBundle());
      assertNull(runningJobs.get(middle.getJobKey()), "the failed fire left its run record");
      assertEquals(TriggerState.ACQUIRED, triggersByKey.get(middle.getKey()).getState());

      handleAsQuartz(acquired, results);

      assertEquals(TriggerState.WAITING, triggersByKey.get(middle.getKey()).getState(),
                   "the failed trigger was not released");
      assertEquals(TriggerState.BLOCKED, triggersByKey.get(acquired.get(0).getKey()).getState());
      assertEquals(TriggerState.BLOCKED, triggersByKey.get(acquired.get(2).getKey()).getState());
   }

   /**
    * Stores three triggers, each of its own job, due one after the other; the middle one is
    * created from the given instance.
    */
   private List<OperableTrigger> storeThreeTriggers(SimpleTriggerImpl middle) throws Exception {
      List<OperableTrigger> triggers = new ArrayList<>();

      for(int i = 0; i < 3; i++) {
         JobDetail job = createJob("job" + i);
         OperableTrigger trigger = createTrigger(
            i == 1 ? middle : new SimpleTriggerImpl(), job, "trigger" + i, 1000L * (i + 1));
         store.storeJobAndTrigger(job, trigger);
         triggers.add(trigger);
      }

      return triggers;
   }

   private List<OperableTrigger> acquireThree(SimpleTriggerImpl middle) throws Exception {
      List<OperableTrigger> triggers = storeThreeTriggers(middle);
      List<OperableTrigger> acquired =
         store.acquireNextTriggers(System.currentTimeMillis() + 60_000, 3, 0L);
      assertEquals(triggers.stream().map(Trigger::getKey).toList(),
                   acquired.stream().map(Trigger::getKey).toList());
      return acquired;
   }

   /**
    * Handles fired results as Quartz 2.3.2's {@code QuartzSchedulerThread} does: the trigger of a
    * result with a runtime exception or without a bundle is released.
    */
   private void handleAsQuartz(List<OperableTrigger> triggers, List<TriggerFiredResult> results) {
      for(int i = 0; i < results.size(); i++) {
         TriggerFiredResult result = results.get(i);

         if(result.getException() instanceof RuntimeException ||
            result.getTriggerFiredBundle() == null)
         {
            store.releaseAcquiredTrigger(triggers.get(i));
         }
      }
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
      return createJob("job");
   }

   private static JobDetail createJob(String name) {
      return JobBuilder.newJob(NonConcurrentJob.class)
         .withIdentity(name, "ClusterJobStoreTransactionTest")
         .storeDurably()
         .build();
   }

   private static OperableTrigger createTrigger(SimpleTriggerImpl trigger, JobDetail job) {
      return createTrigger(trigger, job, "trigger", 1000L);
   }

   private static OperableTrigger createTrigger(SimpleTriggerImpl trigger, JobDetail job,
                                                String name, long delay)
   {
      trigger.setKey(TriggerKey.triggerKey(name, "ClusterJobStoreTransactionTest"));
      trigger.setJobKey(job.getKey());
      trigger.setStartTime(new Date(System.currentTimeMillis() + delay));
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
         // only an outermost transaction is counted, a joined one fails with it
         if(ignite.transactions().tx() == null &&
            (failures.getAndUpdate(n -> Math.max(0, n - 1)) > 0 ||
             failAfter.getAndUpdate(n -> n >= 0 ? n - 1 : n) == 0))
         {
            throw new DistributedTransactionException("The transaction timed out");
         }

         return IgniteCluster.runInTransaction(ignite, timeout, unit, action);
      }

      // the number of next transactions that fail
      final AtomicInteger failures = new AtomicInteger();
      // the number of next transactions that pass before one fails, or -1
      final AtomicInteger failAfter = new AtomicInteger(-1);
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

   /**
    * Picks distinct free ports, as the other embedded Ignite tests do, so the topology can't
    * pick up a node of another test or fork.
    */
   private static int[] freePorts(int count) {
      java.net.ServerSocket[] sockets = new java.net.ServerSocket[count];
      int[] ports = new int[count];

      try {
         for(int i = 0; i < count; i++) {
            sockets[i] = new java.net.ServerSocket(0);
            ports[i] = sockets[i].getLocalPort();
         }

         return ports;
      }
      catch(java.io.IOException ex) {
         throw new java.io.UncheckedIOException(ex);
      }
      finally {
         for(java.net.ServerSocket socket : sockets) {
            if(socket != null) {
               try {
                  socket.close();
               }
               catch(java.io.IOException ignore) {
               }
            }
         }
      }
   }
}
