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
package inetsoft.sree.schedule.jobstore;

import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.internal.cluster.ignite.*;
import inetsoft.util.ConfigurationContext;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheInterceptor;
import org.apache.ignite.cache.CacheInterceptorAdapter;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.*;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.quartz.*;
import org.quartz.impl.DirectSchedulerFactory;
import org.quartz.impl.triggers.SimpleTriggerImpl;
import org.quartz.simpl.SimpleThreadPool;
import org.quartz.spi.*;
import org.springframework.context.ApplicationContext;

import javax.cache.Cache;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78116: a trigger left ACQUIRED by this live node without its scheduler holding it must be
 * released by the next acquire pass, so it fires again. Real ClusterJobStore on a real embedded
 * Ignite node; the scheduler tests are driven by a real Quartz scheduler thread.
 */
@Tag("core")
class ClusterJobStoreLostAcquisitionTest {
   @BeforeAll
   static void startIgnite(@TempDir Path workDir) {
      IgniteConfiguration config = new IgniteConfiguration();
      config.setIgniteInstanceName("ClusterJobStoreLostAcquisitionTest");
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
   void setUp() {
      for(String name : ignite.cacheNames()) {
         ignite.cache(name).clear();
      }

      savedAppContext = ConfigurationContext.getContext().getApplicationContext();
      cluster = new IgniteBackedCluster();
      installContext(cluster);
      triggersByKey = cluster.getReplicatedMap("jobstore.triggersByKey");
      RUNS.set(0);
      HeuristicInterceptor.ARMED = false;
      HeuristicInterceptor.FIRED = false;
   }

   @AfterEach
   void tearDown() throws Exception {
      try {
         if(scheduler != null) {
            scheduler.shutdown(true);
            scheduler = null;
         }

         for(ClusterJobStore store : stores) {
            store.shutdown();
         }
      }
      finally {
         stores.clear();
         HeuristicInterceptor.ARMED = false;
         ConfigurationContext.getContext().setApplicationContext(savedAppContext);
      }
   }

   /**
    * The acquire transaction's commit fails while Ignite applies the ACQUIRED write (a real
    * heuristic outcome, which leaves the write in place), so the store skips the trigger as if
    * it was rolled back and the scheduler never gets it.
    */
   @Test
   void acquireWhoseCommitFailedAfterItsWriteFiresOnALaterPass() throws Exception {
      HeuristicInterceptor.ARMED = true;
      startAndSchedule();

      awaitRuns(1);

      assertTrue(HeuristicInterceptor.FIRED, "the acquire commit did not fail");
      assertTrue(cluster.heuristic, "the acquire commit failure was not a heuristic outcome");
      assertTrue(RUNS.get() >= 1, "the trigger never fired: " + state());
   }

   /**
    * The fire transaction is rolled back, and the release Quartz then asks for fails too, so the
    * trigger stays acquired by this node without the scheduler holding it.
    */
   @Test
   void failedFireWhoseReleaseFailedFiresOnALaterPass() throws Exception {
      cluster.failOnce("triggersFired", Mode.ROLLED_BACK);
      cluster.failOnce("releaseAcquiredTrigger", Mode.BEFORE_ACTION);
      startAndSchedule();

      awaitRuns(1);

      assertTrue(cluster.plan.isEmpty(), "not every failure was injected: " + cluster.plan);
      assertTrue(RUNS.get() >= 1, "the trigger never fired: " + state());
   }

   /**
    * A scheduler stopped in this JVM failed to release its acquired trigger. Its node is still in
    * the cluster, so the store of the next scheduler in the JVM must release it.
    */
   @Test
   void triggerLeftByAStoppedStoreOfThisNodeIsAcquiredAgain() throws Exception {
      TriggerKey key = storeTrigger(newStore());
      ClusterJobStore stopped = newStore();
      OperableTrigger lost = acquireOne(stopped);
      cluster.failOnce("releaseOwnAcquiredTriggers", Mode.BEFORE_ACTION);
      stores.remove(stopped);
      stopped.shutdown();
      assertEquals(TriggerState.ACQUIRED, triggersByKey.get(key).getState(),
                   "the stopped store released the trigger");

      ClusterJobStore store = newStore();
      OperableTrigger again = acquireOne(store);

      assertNotEquals(lost.getFireInstanceId(), again.getFireInstanceId());
      assertNotNull(store.triggersFired(List.of(again)).get(0).getTriggerFiredBundle(),
                    "the trigger acquired again did not fire");
   }

   /**
    * A trigger acquired by another store of this JVM that has not shut down may still be held by
    * its scheduler, so it is left as it is.
    */
   @Test
   void triggerAcquiredByAnotherLiveStoreOfThisNodeIsKept() throws Exception {
      ClusterJobStore other = newStore();
      TriggerKey key = storeTrigger(other);
      OperableTrigger held = acquireOne(other);
      ClusterJobStore store = newStore();

      assertTrue(store.acquireNextTriggers(System.currentTimeMillis() + 60_000, 1, 0L).isEmpty(),
                 "the trigger held by the other store was acquired");
      TriggerWrapper stored = triggersByKey.get(key);
      assertEquals(TriggerState.ACQUIRED, stored.getState());
      assertEquals(held.getFireInstanceId(), stored.trigger.getFireInstanceId());
   }

   /**
    * Once a lost acquisition is released and the trigger is acquired again, the stale copy can
    * neither fire nor release it, so the trigger fires once.
    */
   @Test
   void staleCopyOfAReleasedAcquisitionDoesNotFire() throws Exception {
      ClusterJobStore store = newStore();
      TriggerKey key = storeTrigger(store);
      OperableTrigger lost = acquireOne(store);
      OperableTrigger again = acquireOne(store);

      assertNotEquals(lost.getFireInstanceId(), again.getFireInstanceId());
      assertNull(store.triggersFired(List.of(lost)).get(0).getTriggerFiredBundle(),
                 "the stale copy fired");
      store.releaseAcquiredTrigger(lost);
      assertEquals(TriggerState.ACQUIRED, triggersByKey.get(key).getState(),
                   "the stale copy released the new acquisition");
      assertNotNull(store.triggersFired(List.of(again)).get(0).getTriggerFiredBundle(),
                    "the new acquisition did not fire");
   }

   private void startAndSchedule() throws Exception {
      String name = "lost-acquisition-" + UUID.randomUUID();
      ClusterJobStore store = new ClusterJobStore();
      DirectSchedulerFactory factory = DirectSchedulerFactory.getInstance();
      factory.createScheduler(name, name, new SimpleThreadPool(1, Thread.NORM_PRIORITY), store,
                              null, 0, 500, -1);
      scheduler = factory.getScheduler(name);
      JobDetail job = createJob();
      scheduler.scheduleJob(job, createTrigger(job, 300));
      scheduler.start();
   }

   private ClusterJobStore newStore() throws SchedulerException {
      ClusterJobStore store = new ClusterJobStore();
      store.initialize(null, mock(SchedulerSignaler.class));
      store.schedulerStarted();
      stores.add(store);
      return store;
   }

   private static TriggerKey storeTrigger(ClusterJobStore store) throws JobPersistenceException {
      JobDetail job = createJob();
      SimpleTriggerImpl trigger = createTrigger(job, 1000);
      trigger.computeFirstFireTime(null);
      store.storeJobAndTrigger(job, trigger);
      return trigger.getKey();
   }

   private static OperableTrigger acquireOne(ClusterJobStore store)
      throws JobPersistenceException
   {
      List<OperableTrigger> acquired =
         store.acquireNextTriggers(System.currentTimeMillis() + 60_000, 1, 0L);
      assertEquals(1, acquired.size(), "the trigger was not acquired");
      return acquired.get(0);
   }

   private static JobDetail createJob() {
      return JobBuilder.newJob(CountingJob.class)
         .withIdentity("job", "ClusterJobStoreLostAcquisitionTest")
         .storeDurably()
         .build();
   }

   private static SimpleTriggerImpl createTrigger(JobDetail job, long delay) {
      SimpleTriggerImpl trigger = new SimpleTriggerImpl();
      trigger.setKey(TriggerKey.triggerKey("trigger", "ClusterJobStoreLostAcquisitionTest"));
      trigger.setJobKey(job.getKey());
      trigger.setStartTime(new Date(System.currentTimeMillis() + delay));
      trigger.setRepeatCount(SimpleTrigger.REPEAT_INDEFINITELY);
      trigger.setRepeatInterval(1000);
      return trigger;
   }

   private String state() {
      TriggerWrapper tw =
         triggersByKey.get(TriggerKey.triggerKey("trigger", "ClusterJobStoreLostAcquisitionTest"));
      return String.valueOf(tw);
   }

   private static void awaitRuns(int runs) throws InterruptedException {
      long end = System.currentTimeMillis() + 8000;

      while(RUNS.get() < runs && System.currentTimeMillis() < end) {
         Thread.sleep(50);
      }
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

         if("jobstore.triggersByKey".equals(name)) {
            config.setInterceptor((CacheInterceptor<K, V>) new HeuristicInterceptor());
         }

         return ignite.getOrCreateCache(config);
      }
      catch(ReflectiveOperationException e) {
         throw new IllegalStateException(e);
      }
   }

   private enum Mode {
      // fails as a lock timeout does, before the action runs
      BEFORE_ACTION,
      // the action runs, then the transaction fails before it commits and is rolled back
      ROLLED_BACK
   }

   /**
    * A cluster whose replicated maps and transactions are Ignite's, and whose transaction made by
    * a given store method can be made to fail once.
    */
   private static final class IgniteBackedCluster extends MockCluster {
      @Override
      public <K, V> DistributedMap<K, V> getReplicatedMap(String name) {
         return new IgniteDistributedMap<>(cache(name));
      }

      @Override
      public <K, V> MultiMap<K, V> getReplicatedMultiMap(String name) {
         return new IgniteMultiMap<>(
            ClusterJobStoreLostAcquisitionTest.<K, Collection<V>>cache(name));
      }

      @Override
      public String getLocalMember() {
         return "test-member";
      }

      synchronized void failOnce(String method, Mode mode) {
         plan.put(method, mode);
      }

      /**
       * Gets the failure planned for the store method that makes the transaction, if any.
       */
      private synchronized Mode take() {
         for(StackTraceElement e : Thread.currentThread().getStackTrace()) {
            if(e.getClassName().equals(ClusterJobStore.class.getName())) {
               Mode mode = plan.remove(e.getMethodName());

               if(mode != null) {
                  return mode;
               }
            }
         }

         return null;
      }

      @Override
      public <T, E extends Exception> T runInTransaction(long timeout, TimeUnit unit,
                                                          TransactionalAction<T, E> action)
         throws E
      {
         // only an outermost transaction fails, a joined one fails with it
         Mode mode = ignite.transactions().tx() == null ? take() : null;

         if(mode == Mode.BEFORE_ACTION) {
            throw new DistributedTransactionException("The transaction timed out");
         }

         try {
            if(mode == Mode.ROLLED_BACK) {
               return IgniteCluster.runInTransaction(ignite, timeout, unit, () -> {
                  action.run();
                  throw new DistributedTransactionException("The transaction failed to commit");
               });
            }

            return IgniteCluster.runInTransaction(ignite, timeout, unit, action);
         }
         catch(DistributedTransactionException ex) {
            if(hasCause(ex, org.apache.ignite.transactions.TransactionHeuristicException.class)) {
               heuristic = true;
            }

            throw ex;
         }
      }

      private static boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
         for(Throwable t = ex; t != null; t = t.getCause()) {
            if(type.isInstance(t)) {
               return true;
            }
         }

         return false;
      }

      final Map<String, Mode> plan = new HashMap<>();
      // whether a transaction failed with a heuristic outcome
      volatile boolean heuristic;
   }

   /**
    * Throws once, while armed, when Ignite applies an ACQUIRED trigger write while committing,
    * which Ignite reports as a heuristic outcome and which leaves the write in place.
    */
   public static final class HeuristicInterceptor extends CacheInterceptorAdapter<Object, Object> {
      @Override
      public void onAfterPut(Cache.Entry<Object, Object> entry) {
         if(ARMED && entry.getValue() instanceof TriggerWrapper tw &&
            tw.getState() == TriggerState.ACQUIRED)
         {
            ARMED = false;
            FIRED = true;
            throw new IllegalStateException("failed while applying the commit");
         }
      }

      static volatile boolean ARMED;
      static volatile boolean FIRED;
   }

   @DisallowConcurrentExecution
   public static class CountingJob implements Job {
      @Override
      public void execute(JobExecutionContext context) {
         RUNS.incrementAndGet();
      }
   }

   private static final AtomicInteger RUNS = new AtomicInteger();
   private static Ignite ignite;
   private ApplicationContext savedAppContext;
   private IgniteBackedCluster cluster;
   private org.quartz.Scheduler scheduler;
   private final List<ClusterJobStore> stores = new ArrayList<>();
   private DistributedMap<TriggerKey, TriggerWrapper> triggersByKey;

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
