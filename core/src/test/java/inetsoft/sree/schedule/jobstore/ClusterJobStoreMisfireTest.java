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
package inetsoft.sree.schedule.jobstore;

import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.internal.cluster.ignite.*;
import inetsoft.sree.schedule.TimeCondition;
import inetsoft.sree.schedule.quartz.TimeConditionTriggerImpl;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.quartz.*;
import org.quartz.impl.triggers.SimpleTriggerImpl;
import org.quartz.spi.*;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * How a real {@code ClusterJobStore} on a real embedded Ignite node handles a trigger whose fire
 * time is in the past when it is acquired: the misfire threshold, the misfire instruction, the
 * schedule that continues afterwards, and the notifications. Time inputs are relative to the wall
 * clock because the store reads it.
 */
@Tag("core")
class ClusterJobStoreMisfireTest {
   private static final String GROUP = "ClusterJobStoreMisfireTest";
   private static final long HOUR = 3600000L;

   @BeforeAll
   static void startIgnite(@TempDir Path workDir) {
      IgniteConfiguration config = new IgniteConfiguration();
      config.setIgniteInstanceName("ClusterJobStoreMisfireTest");
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
      PropertiesEngine properties = mock(PropertiesEngine.class);
      when(properties.getProperty(any(String.class), any(String.class), anyBoolean()))
         .thenAnswer(inv -> inv.getArgument(1));
      ApplicationContext context = mock(ApplicationContext.class);
      when(context.getBean(Cluster.class)).thenReturn(cluster);
      when(context.getBean(PropertiesEngine.class)).thenReturn(properties);
      ConfigurationContext.getContext().setApplicationContext(context);
      triggersByKey = cluster.getReplicatedMap("jobstore.triggersByKey");

      signaler = mock(SchedulerSignaler.class);
      store = new ClusterJobStore();
      store.initialize(null, signaler);
      store.schedulerStarted();
   }

   @AfterEach
   void tearDown() {
      try {
         store.shutdown();
      }
      finally {
         ConfigurationContext.getContext().setApplicationContext(savedAppContext);
      }
   }

   // ----- the misfire instruction -----

   @Test
   void fireOnceNowMovesAPastDueTriggerToNowWhenAcquired() throws Exception {
      TimeConditionTriggerImpl trigger = hourlyTrigger("fire-once-now", 1, -60_000);

      long before = System.currentTimeMillis();
      OperableTrigger acquired = acquireOne(trigger);

      assertTrue(acquired.getNextFireTime().getTime() >= before - 1000,
                 "the fire time must move to now, was " + acquired.getNextFireTime());
   }

   @Test
   void smartPolicyIsTreatedLikeFireOnceNowWhenAcquired() throws Exception {
      TimeConditionTriggerImpl trigger = hourlyTrigger("smart-policy", 0, -60_000);

      long before = System.currentTimeMillis();
      OperableTrigger acquired = acquireOne(trigger);

      assertTrue(acquired.getNextFireTime().getTime() >= before - 1000,
                 "the fire time must move to now, was " + acquired.getNextFireTime());
   }

   @Test
   void ignoreMisfirePolicyAcquiresWithTheOriginalFireTime() throws Exception {
      TimeConditionTriggerImpl trigger = hourlyTrigger("ignore-policy", -1, -60_000);
      Date original = trigger.getNextFireTime();

      OperableTrigger acquired = acquireOne(trigger);

      assertEquals(original, acquired.getNextFireTime());
   }

   // ----- the misfire threshold -----

   @Test
   void triggerLateByLessThanTheThresholdIsNotMisfiredAndOneLateByMoreIs() throws Exception {
      store.setMisfireThreshold(60_000);
      TimeConditionTriggerImpl within = hourlyTrigger("threshold-within", 1, -50_000);
      Date withinOriginal = within.getNextFireTime();

      OperableTrigger acquiredWithin = acquireOne(within);
      assertEquals(withinOriginal, acquiredWithin.getNextFireTime(),
                   "50 s late is inside a 60 s threshold");
      verify(signaler, never()).notifyTriggerListenersMisfired(any());

      TimeConditionTriggerImpl beyond = hourlyTrigger("threshold-beyond", 1, -70_000);
      long before = System.currentTimeMillis();
      OperableTrigger acquiredBeyond = acquireOne(beyond);
      assertTrue(acquiredBeyond.getNextFireTime().getTime() >= before - 1000,
                 "70 s late is beyond a 60 s threshold: " + acquiredBeyond.getNextFireTime());
   }

   @ParameterizedTest(name = "a trigger 2 s late misfires with threshold {0}")
   @ValueSource(longs = { 0L, -1L })
   void nonPositiveThresholdMakesAnyLateTriggerMisfire(long threshold) throws Exception {
      store.setMisfireThreshold(threshold);
      TimeConditionTriggerImpl trigger = hourlyTrigger("threshold-zero-" + threshold, 1, -2000);

      long before = System.currentTimeMillis();
      OperableTrigger acquired = acquireOne(trigger);

      assertTrue(acquired.getNextFireTime().getTime() >= before - 1000,
                 "must have misfired to now, was " + acquired.getNextFireTime());
   }

   @Test
   void misfireListenersAreNotifiedOnceForAMisfireAndNotWithinTheThreshold() throws Exception {
      TimeConditionTriggerImpl late = hourlyTrigger("notify-late", 1, -60_000);
      acquireOne(late);
      verify(signaler, times(1)).notifyTriggerListenersMisfired(any());

      TimeConditionTriggerImpl onTime = hourlyTrigger("notify-on-time", 1, -1000);
      acquireOne(onTime);
      verify(signaler, times(1)).notifyTriggerListenersMisfired(any());
   }

   // ----- the schedule that continues after a misfire -----

   @Test
   void hourlyTriggerMissedFiveTimesFiresOnceAndContinuesWithTheNextSlot() throws Exception {
      TimeConditionTriggerImpl trigger = hourlyTrigger("missed-five-hours", 1, -5 * HOUR);
      JobDetail job = job("missed-five-hours-job");
      trigger.setJobKey(job.getKey());
      long before = System.currentTimeMillis();
      store.storeJobAndTrigger(job, trigger);

      List<OperableTrigger> first = store.acquireNextTriggers(
         System.currentTimeMillis() + 1000, 1, 0L);
      assertEquals(1, first.size(), "the missed trigger must be acquired once");

      List<TriggerFiredResult> fired = store.triggersFired(first);
      assertNotNull(fired.get(0).getTriggerFiredBundle(), "the trigger must fire");

      Date next = triggersByKey.get(trigger.getKey()).trigger.getNextFireTime();
      assertNotNull(next);
      assertTrue(next.getTime() > before + 30_000, "the next slot is in the future: " + next);
      assertTrue(next.getTime() <= System.currentTimeMillis() + HOUR + 30_000,
                 "the next slot is the next hourly one: " + next);
      assertTrue(store.acquireNextTriggers(System.currentTimeMillis() + 1000, 1, 0L).isEmpty(),
                 "the missed slots must not be fired one by one");
   }

   @Test
   void runOnceTriggerInThePastFiresOnceThenCompletesAndIsNeverAcquiredAgain() throws Exception {
      TimeConditionTriggerImpl trigger = new TimeConditionTriggerImpl();
      trigger.setKey(TriggerKey.triggerKey("run-once-past", GROUP));
      trigger.setCondition(TimeCondition.at(new Date(System.currentTimeMillis() - HOUR)));
      trigger.setMisfireInstruction(1);
      trigger.setNextFireTime(new Date(System.currentTimeMillis() - HOUR));
      JobDetail job = job("run-once-past-job");
      trigger.setJobKey(job.getKey());
      store.storeJobAndTrigger(job, trigger);

      List<OperableTrigger> first = store.acquireNextTriggers(
         System.currentTimeMillis() + 1000, 1, 0L);
      assertEquals(1, first.size(), "the past run-once trigger must fire once");
      store.triggersFired(first);

      TriggerWrapper stored = triggersByKey.get(trigger.getKey());
      assertNull(stored.trigger.getNextFireTime());
      assertEquals(TriggerState.COMPLETE, stored.getState());
      assertTrue(store.acquireNextTriggers(System.currentTimeMillis() + 1000, 1, 0L).isEmpty());
   }

   @Test
   void triggerWhoseRemainingFiresAreAllInThePastIsCompletedByTheMisfire() throws Exception {
      SimpleTriggerImpl trigger = new SimpleTriggerImpl();
      trigger.setKey(TriggerKey.triggerKey("misfire-to-end", GROUP));
      long now = System.currentTimeMillis();
      trigger.setStartTime(new Date(now - 3 * HOUR));
      trigger.setEndTime(new Date(now + HOUR));
      trigger.setRepeatCount(1);
      trigger.setRepeatInterval(HOUR);
      trigger.setMisfireInstruction(
         SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT);
      trigger.setNextFireTime(new Date(now - 2 * HOUR));
      JobDetail job = job("misfire-to-end-job");
      trigger.setJobKey(job.getKey());
      store.storeJobAndTrigger(job, trigger);

      List<OperableTrigger> acquired = store.acquireNextTriggers(now + 1000, 1, 0L);

      assertTrue(acquired.isEmpty(), "a trigger with nothing left to fire must not be acquired");
      verify(signaler, times(1)).notifySchedulerListenersFinalized(any());
      TriggerState state = triggersByKey.get(trigger.getKey()).getState();
      assertTrue(state == TriggerState.COMPLETE || state == TriggerState.STATE_COMPLETED,
                 "the trigger must be marked completed but is " + state);
      assertTrue(store.acquireNextTriggers(now + 1000, 1, 0L).isEmpty());
   }

   @Test
   void pausedTriggerIsNeitherMisfiredNorAcquired() throws Exception {
      TimeConditionTriggerImpl trigger = hourlyTrigger("paused-misfire", 1, -60_000);
      Date original = trigger.getNextFireTime();
      JobDetail job = job("paused-misfire-job");
      trigger.setJobKey(job.getKey());
      store.storeJobAndTrigger(job, trigger);
      store.pauseTrigger(trigger.getKey());

      List<OperableTrigger> acquired = store.acquireNextTriggers(
         System.currentTimeMillis() + 1000, 1, 0L);

      assertTrue(acquired.isEmpty());
      assertEquals(original, triggersByKey.get(trigger.getKey()).trigger.getNextFireTime());
      verify(signaler, never()).notifyTriggerListenersMisfired(any());
   }

   @Test
   void misfireOnlyChangesTheFireTimesOfTheTrigger() throws Exception {
      TimeConditionTriggerImpl trigger = hourlyTrigger("keeps-identity", 1, -60_000);
      trigger.getJobDataMap().put("param", "value");
      JobDetail job = job("keeps-identity-job");
      trigger.setJobKey(job.getKey());
      store.storeJobAndTrigger(job, trigger);

      OperableTrigger acquired = store.acquireNextTriggers(
         System.currentTimeMillis() + 1000, 1, 0L).get(0);

      assertEquals(trigger.getKey(), acquired.getKey());
      assertEquals(job.getKey(), acquired.getJobKey());
      assertEquals("value", acquired.getJobDataMap().get("param"));
      assertEquals(trigger.getCondition(), ((TimeConditionTriggerImpl) acquired).getCondition());
   }

   // ----- helpers -----

   /**
    * Stores an hourly trigger whose next fire time is the given offset from now, and acquires it.
    */
   private OperableTrigger acquireOne(TimeConditionTriggerImpl trigger) throws Exception {
      JobDetail job = job(trigger.getKey().getName() + "-job");
      trigger.setJobKey(job.getKey());
      store.storeJobAndTrigger(job, trigger);
      List<OperableTrigger> acquired = store.acquireNextTriggers(
         System.currentTimeMillis() + 1000, 1, 0L);
      assertEquals(1, acquired.size(), "the trigger was not acquired");
      return acquired.get(0);
   }

   private TimeConditionTriggerImpl hourlyTrigger(String name, int misfireInstruction,
                                                  long nextOffsetMillis)
   {
      TimeCondition condition = TimeCondition.atHours(
         new int[] { 1, 2, 3, 4, 5, 6, 7 }, 0, 0, 0);
      condition.setHourlyInterval(1f);
      condition.setHourEnd(23);
      condition.setMinuteEnd(59);
      condition.setSecondEnd(59);
      condition.setTimeZone(TimeZone.getTimeZone("UTC"));
      TimeConditionTriggerImpl trigger = new TimeConditionTriggerImpl();
      trigger.setKey(TriggerKey.triggerKey(name, GROUP));
      trigger.setCondition(condition);
      trigger.setMisfireInstruction(misfireInstruction);
      trigger.setNextFireTime(new Date(System.currentTimeMillis() + nextOffsetMillis));
      return trigger;
   }

   private static JobDetail job(String name) {
      return JobBuilder.newJob(PlainJob.class).withIdentity(name, GROUP).storeDurably().build();
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

   /** A cluster whose replicated maps and transactions are Ignite's. */
   private static final class IgniteBackedCluster extends MockCluster {
      @Override
      public <K, V> DistributedMap<K, V> getReplicatedMap(String name) {
         return new IgniteDistributedMap<>(cache(name));
      }

      @Override
      public <K, V> MultiMap<K, V> getReplicatedMultiMap(String name) {
         return new IgniteMultiMap<>(ClusterJobStoreMisfireTest.<K, Collection<V>>cache(name));
      }

      @Override
      public String getLocalMember() {
         return "test-member";
      }

      @Override
      public <T, E extends Exception> T runInTransaction(long timeout,
                                                          java.util.concurrent.TimeUnit unit,
                                                          TransactionalAction<T, E> action)
         throws E
      {
         return IgniteCluster.runInTransaction(ignite, timeout, unit, action);
      }
   }

   /** A job that may run concurrently, so that a fired trigger is WAITING and not BLOCKED. */
   public static class PlainJob implements Job {
      @Override
      public void execute(JobExecutionContext context) {
      }
   }

   private static Ignite ignite;
   private ApplicationContext savedAppContext;
   private IgniteBackedCluster cluster;
   private ClusterJobStore store;
   private SchedulerSignaler signaler;
   private DistributedMap<TriggerKey, TriggerWrapper> triggersByKey;

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
