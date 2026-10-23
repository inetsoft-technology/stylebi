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
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.impl.triggers.SimpleTriggerImpl;
import org.quartz.spi.*;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79262: the job and trigger indexes of the store are multimaps that must not keep a copy of
 * a key that one removal cannot drop. A key stored twice (nodes replacing the same task at once,
 * or a node starting while another has the task stored) and removed once used to stay in the
 * group index with no job behind it, so every later job lookup of the group found a null job.
 * Runs on one embedded Ignite node with the product's replicated cache configuration and several
 * store instances over the same caches, as the nodes of a cluster are.
 */
@Tag("slow")
class ClusterJobStoreOrphanKeyTest {
   @BeforeAll
   static void startIgnite(@TempDir Path workDir) {
      IgniteConfiguration config = new IgniteConfiguration();
      config.setIgniteInstanceName("ClusterJobStoreOrphanKeyTest");
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
      installContext(new IgniteBackedCluster());
      nodes = new ClusterJobStore[NODES];

      for(int i = 0; i < NODES; i++) {
         nodes[i] = new ClusterJobStore();
         nodes[i].initialize(null, mock(SchedulerSignaler.class));
         nodes[i].schedulerStarted();
      }
   }

   @AfterEach
   void tearDown() {
      ConfigurationContext.getContext().setApplicationContext(savedAppContext);
   }

   /**
    * Control: the replace done one node at a time, as a single node does it, leaves nothing.
    */
   @Test
   void sequentialReplaceAndRemoveLeavesNoKey() throws Exception {
      JobDetail job = createJob();

      for(int i = 0; i < 5; i++) {
         nodes[0].removeJob(job.getKey());
         nodes[0].storeJob(job, true);
      }

      assertEquals(Set.of(job.getKey()), jobKeys());
      assertNotNull(nodes[1].retrieveJob(job.getKey()));

      nodes[0].removeJob(job.getKey());
      assertEquals(Set.of(), jobKeys());
   }

   /**
    * The interleaving of two nodes replacing the same job: both delete, then both store. One
    * removal afterwards must leave no key behind.
    */
   @Test
   void interleavedReplaceLeavesNoOrphanAfterOneRemoval() throws Exception {
      JobDetail job = createJob();
      nodes[0].storeJob(job, true);

      nodes[0].removeJob(job.getKey());
      nodes[1].removeJob(job.getKey());
      nodes[0].storeJob(job, true);
      nodes[1].storeJob(job, true);

      nodes[2].removeJob(job.getKey());

      assertNull(nodes[2].retrieveJob(job.getKey()));
      assertEquals(Set.of(), jobKeys(), "the group index lists a job that has no job detail");
   }

   /**
    * The startup load of a second node replaces a job another node has stored, with no delete.
    */
   @Test
   void replaceOfStoredJobLeavesNoOrphanAfterOneRemoval() throws Exception {
      JobDetail job = createJob();
      nodes[0].storeJob(job, true);
      nodes[1].storeJob(job, true);

      nodes[0].removeJob(job.getKey());

      assertEquals(Set.of(), jobKeys(), "the group index lists a job that has no job detail");
   }

   /**
    * The nodes replacing the same job at once, as the data cycle's MV change event makes them,
    * then one removal: no key may remain, in any round.
    */
   @Test
   void concurrentReplaceLeavesNoOrphanAfterOneRemoval() throws Exception {
      ExecutorService pool = Executors.newFixedThreadPool(NODES);

      try {
         for(int round = 0; round < ROUNDS; round++) {
            JobDetail job = createJob("job" + round);
            CyclicBarrier barrier = new CyclicBarrier(NODES);
            List<Future<?>> futures = new ArrayList<>();

            for(ClusterJobStore node : nodes) {
               futures.add(pool.submit(() -> {
                  barrier.await(30, TimeUnit.SECONDS);
                  // what Scheduler.addTask does: delete the job, then schedule it with replace
                  node.removeJob(job.getKey());
                  node.storeJob(job, true);
                  return null;
               }));
            }

            for(Future<?> future : futures) {
               future.get(60, TimeUnit.SECONDS);
            }

            nodes[0].removeJob(job.getKey());
            assertEquals(Set.of(), jobKeys(),
                         "an orphan key remains after one removal in round " + round);
         }
      }
      finally {
         pool.shutdownNow();
      }
   }

   /**
    * The trigger indexes have the same shape: a trigger replaced and removed once leaves no key.
    */
   @Test
   void replacedTriggerLeavesNoOrphanAfterOneRemoval() throws Exception {
      JobDetail job = createJob();
      nodes[0].storeJob(job, true);
      nodes[0].storeTrigger(createTrigger(job), true);
      nodes[1].storeTrigger(createTrigger(job), true);

      nodes[2].removeTrigger(TriggerKey.triggerKey("trigger", GROUP));

      assertEquals(Set.of(), nodes[2].getTriggerKeys(GroupMatcher.triggerGroupEquals(GROUP)));
      assertTrue(nodes[2].getTriggersForJob(job.getKey()).isEmpty());
   }

   private Set<JobKey> jobKeys() throws JobPersistenceException {
      return nodes[0].getJobKeys(GroupMatcher.jobGroupEquals(GROUP));
   }

   private static JobDetail createJob() {
      return createJob("job");
   }

   private static JobDetail createJob(String name) {
      return JobBuilder.newJob(NoopJob.class).withIdentity(name, GROUP).storeDurably().build();
   }

   private static OperableTrigger createTrigger(JobDetail job) {
      SimpleTriggerImpl trigger = new SimpleTriggerImpl();
      trigger.setKey(TriggerKey.triggerKey("trigger", GROUP));
      trigger.setJobKey(job.getKey());
      trigger.setStartTime(new Date(System.currentTimeMillis() + 60000L));
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
    * A cluster whose replicated maps and transactions are Ignite's.
    */
   private static final class IgniteBackedCluster extends MockCluster {
      @Override
      public <K, V> DistributedMap<K, V> getReplicatedMap(String name) {
         return new IgniteDistributedMap<>(cache(name));
      }

      @Override
      public <K, V> MultiMap<K, V> getReplicatedMultiMap(String name) {
         return new IgniteMultiMap<>(ClusterJobStoreOrphanKeyTest.<K, Collection<V>>cache(name));
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
         return IgniteCluster.runInTransaction(ignite, timeout, unit, action);
      }
   }

   public static class NoopJob implements Job {
      @Override
      public void execute(JobExecutionContext context) {
      }
   }

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

   private static final String GROUP = "ClusterJobStoreOrphanKeyTest";
   private static final int NODES = 3;
   private static final int ROUNDS = 40;
   private static Ignite ignite;
   private ApplicationContext savedAppContext;
   private ClusterJobStore[] nodes;
}
