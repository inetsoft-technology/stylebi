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
import inetsoft.sree.internal.cluster.ignite.IgniteCluster;
import inetsoft.uql.XPrincipal;
import inetsoft.util.ConfigurationContext;
import inetsoft.web.viewsheet.service.RuntimeViewsheetManager;
import inetsoft.analytic.composition.ViewsheetService;
import org.apache.ignite.*;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.configuration.*;
import org.apache.ignite.failure.NoOpFailureHandler;
import org.apache.ignite.failure.StopNodeFailureHandler;
import org.apache.ignite.internal.managers.communication.GridIoMessage;
import org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxPrepareRequest;
import org.apache.ignite.lang.IgniteInClosure;
import org.apache.ignite.plugin.extensions.communication.Message;
import org.apache.ignite.spi.IgniteSpiException;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.quartz.*;
import org.quartz.impl.triggers.SimpleTriggerImpl;
import org.quartz.spi.*;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77879: the store's real sections, run on a node that fails while they commit, must not
 * leave a transaction on a surviving backup that blocks every later partition map exchange, and
 * must leave the survivors with the same, all-or-nothing state. Unlike
 * {@code IgniteTransactionNodeFailureTest}, which writes one key of one map, these transactions
 * span the store's caches: storeTrigger (trigger, job read, two multimaps) and triggersFired
 * (trigger, job, and the running record of startRun, which joins the trigger's transaction).
 * Also covers a node that fails while it holds the locks before it commits, and
 * RuntimeViewsheetManager.
 *
 * <p>Three embedded server nodes A, B and C; the store runs on A. B's (and A's) prepare requests
 * to the backup C are held while A fails, then released. Takes about 30 s.
 */
@Tag("slow")
class ClusterJobStoreNodeFailureTest {
   @BeforeEach
   void startNodes() {
      savedAppContext = ConfigurationContext.getContext().getApplicationContext();
      a = startNode("A", 0, true);
      b = startNode("B", 1, false);
      c = startNode("C", 2, false);
      backupId = c.cluster().localNode().id();

      for(String name : CACHES) {
         a.getOrCreateCache(productConfiguration(name));
         b.cache(name);
         c.cache(name);
      }
   }

   @AfterEach
   void stopNodes() {
      HOLD.set(false);
      HELD.clear();
      ConfigurationContext.getContext().setApplicationContext(savedAppContext);

      for(Ignite node : nodes) {
         try {
            node.close();
         }
         catch(RuntimeException ignore) {
         }
      }

      nodes.clear();
   }

   @Test
   @Timeout(180)
   void storeTriggerOnAFailingNode() throws Exception {
      ClusterJobStore storeA = createStore(a);
      JobDetail job = createJob(false);
      storeA.storeJob(job, false);
      OperableTrigger trigger = createTrigger(job, primaryTriggerKey(b));

      failWhileCommitting(() -> {
         storeA.storeTrigger(trigger, false);
         return null;
      });

      assertExchangeCompletes();
      assertSurvivorsAgree();
      assertTriggerIndexed(trigger.getKey(), job.getKey());

      // the survivors can lock and write the trigger
      ClusterJobStore storeB = createStore(b);
      assertTimeoutPreemptively(java.time.Duration.ofSeconds(30),
                                () -> storeB.storeTrigger(trigger, true));
      assertNotNull(storeB.retrieveTrigger(trigger.getKey()));
      assertSurvivorsAgree();
      assertTriggerIndexed(trigger.getKey(), job.getKey());
   }

   @Test
   @Timeout(180)
   void triggersFiredOnAFailingNode() throws Exception {
      ClusterJobStore storeA = createStore(a);
      JobDetail job = createJob(true);
      OperableTrigger trigger = createTrigger(job, primaryTriggerKey(b));
      storeA.storeJobAndTrigger(job, trigger);
      List<OperableTrigger> acquired =
         storeA.acquireNextTriggers(System.currentTimeMillis() + 60_000, 1, 0L);
      assertEquals(1, acquired.size(), "the trigger was not acquired");

      failWhileCommitting(() -> storeA.triggersFired(acquired));

      assertExchangeCompletes();
      assertSurvivorsAgree();

      // the trigger and the run record (joined by startRun) commit or roll back together
      TriggerWrapper tw = (TriggerWrapper) b.cache(TRIGGERS).localPeek(trigger.getKey());
      Object run = b.cache(RUNNING).localPeek(job.getKey());
      assertNotNull(tw);

      if(tw.getState() == TriggerState.BLOCKED) {
         assertNotNull(run, "the fired trigger committed without its run record");
      }
      else {
         assertEquals(TriggerState.ACQUIRED, tw.getState());
         assertNull(run, "the run record committed without the fired trigger");
      }
   }

   @Test
   @Timeout(180)
   void nodeFailsWhileHoldingTheLocks() throws Exception {
      String key = primaryTriggerKey(b).getName();
      IgniteCache<String, String> cacheA = a.cache("bug77879.locks");
      IgniteDistributedMap<String, String> mapA = new IgniteDistributedMap<>(cacheA);
      CountDownLatch locked = new CountDownLatch(1);
      Thread holder = new Thread(() -> {
         try {
            IgniteCluster.runInTransaction(a, 5, TimeUnit.MINUTES, () -> {
               mapA.getForUpdate(key);
               mapA.set(key, "A");
               locked.countDown();
               Thread.sleep(Long.MAX_VALUE);
               return null;
            });
         }
         catch(Exception ignore) {
         }
      }, "lock-holder");
      holder.setDaemon(true);
      holder.start();
      assertTrue(locked.await(30, TimeUnit.SECONDS));

      failA();
      assertExchangeCompletes();

      IgniteDistributedMap<String, String> mapB =
         new IgniteDistributedMap<>(b.<String, String>cache("bug77879.locks"));
      assertTimeoutPreemptively(java.time.Duration.ofSeconds(30), () ->
         IgniteCluster.runInTransaction(b, 20, TimeUnit.SECONDS, () -> {
            assertNull(mapB.getForUpdate(key), "the failed node's write was committed");
            mapB.set(key, "B");
            return null;
         }));
      assertEquals("B", c.cache("bug77879.locks").localPeek(key));
      assertSurvivorsAgree();
   }

   /**
    * getForUpdate excludes a concurrent update of the key from another node: no update is lost.
    */
   @Test
   @Timeout(180)
   void updatesFromTwoNodesAreNotLost() throws Exception {
      String key = primaryTriggerKey(c).getName();
      int updates = 100;
      CyclicBarrier start = new CyclicBarrier(2);
      List<Future<?>> results = new ArrayList<>();
      ExecutorService executor = Executors.newFixedThreadPool(2);

      try {
         for(Ignite node : List.of(a, b)) {
            IgniteDistributedMap<String, Integer> map =
               new IgniteDistributedMap<>(node.<String, Integer>cache("bug77879.locks"));
            results.add(executor.submit(() -> {
               start.await();

               for(int i = 0; i < updates; i++) {
                  IgniteCluster.runInTransaction(node, 30, TimeUnit.SECONDS, () -> {
                     Integer value = map.getForUpdate(key);
                     map.set(key, value == null ? 1 : value + 1);
                     return null;
                  });
               }

               return null;
            }));
         }

         for(Future<?> result : results) {
            result.get(120, TimeUnit.SECONDS);
         }
      }
      finally {
         executor.shutdownNow();
      }

      assertEquals(2 * updates, c.cache("bug77879.locks").localPeek(key));
   }

   /**
    * Two transactions on two nodes that lock two keys in opposite orders end at the timeout with a
    * DistributedTransactionException, write nothing and leave both keys unlocked.
    */
   @Test
   @Timeout(180)
   void deadlockAcrossNodesRollsBack() throws Exception {
      String key1 = primaryTriggerKey(b).getName();
      String key2 = primaryTriggerKey(c).getName();
      CyclicBarrier bothLocked = new CyclicBarrier(2);
      ExecutorService executor = Executors.newFixedThreadPool(2);
      List<Future<?>> results = new ArrayList<>();

      try {
         for(Ignite node : List.of(a, b)) {
            boolean first = node == a;
            IgniteDistributedMap<String, String> map =
               new IgniteDistributedMap<>(node.<String, String>cache("bug77879.locks"));
            results.add(executor.submit(() -> IgniteCluster.runInTransaction(
               node, 5, TimeUnit.SECONDS, () -> {
                  map.getForUpdate(first ? key1 : key2);
                  map.set(first ? key1 : key2, node.name());
                  bothLocked.await(10, TimeUnit.SECONDS);
                  map.getForUpdate(first ? key2 : key1);
                  map.set(first ? key2 : key1, node.name());
                  return null;
               })));
         }

         int failed = 0;

         for(Future<?> result : results) {
            try {
               result.get(60, TimeUnit.SECONDS);
            }
            catch(ExecutionException ex) {
               assertInstanceOf(DistributedTransactionException.class, ex.getCause());
               failed++;
            }
         }

         assertTrue(failed > 0, "the deadlock was not broken");
      }
      finally {
         executor.shutdownNow();
      }

      // whatever committed is a whole transaction, and both keys can be locked again
      IgniteDistributedMap<String, String> mapC =
         new IgniteDistributedMap<>(c.<String, String>cache("bug77879.locks"));
      assertTimeoutPreemptively(java.time.Duration.ofSeconds(20), () ->
         IgniteCluster.runInTransaction(c, 10, TimeUnit.SECONDS, () -> {
            assertEquals(mapC.getForUpdate(key1), mapC.getForUpdate(key2),
                         "a partial transaction was committed");
            return null;
         }));
      assertSurvivorsAgree();
   }

   @Test
   @Timeout(180)
   void runtimeViewsheetManagerOnAFailingNode() throws Exception {
      String sessionId = primaryKey(b, OPEN_SHEETS);
      XPrincipal user = mock(XPrincipal.class);
      when(user.getSessionID()).thenReturn(sessionId);
      RuntimeViewsheetManager manager =
         new RuntimeViewsheetManager(mock(ViewsheetService.class), new NodeCluster(a));
      manager.init();

      failWhileCommitting(() -> {
         manager.sheetOpened(user, "sheet-1");
         return null;
      });

      assertExchangeCompletes();
      assertSurvivorsAgree();

      RuntimeViewsheetManager managerB =
         new RuntimeViewsheetManager(mock(ViewsheetService.class), new NodeCluster(b));
      managerB.init();
      assertTimeoutPreemptively(java.time.Duration.ofSeconds(30),
                                () -> managerB.sheetOpened(user, "sheet-2"));
      assertSurvivorsAgree();
   }

   /**
    * Runs an action on A, and fails A while B's prepare request to the backup C is held.
    */
   private void failWhileCommitting(Callable<?> action) throws Exception {
      HOLD.set(true);
      Thread writer = new Thread(() -> {
         try {
            action.call();
         }
         catch(Throwable ignore) {
            // A fails while the transaction commits
         }
      }, "writer");
      writer.setDaemon(true);
      writer.start();

      long deadline = System.currentTimeMillis() + 30_000;

      while(HELD.isEmpty()) {
         assertTrue(System.currentTimeMillis() < deadline, "the write never reached the backup");
         Thread.sleep(50);
      }

      Thread.sleep(500);
      failA();
      Thread.sleep(1000);
      HOLD.set(false);

      for(Runnable held : HELD) {
         held.run();
      }
   }

   private void failA() throws InterruptedException {
      ((TcpDiscoverySpi) a.configuration().getDiscoverySpi()).simulateNodeFailure();
      ((TcpCommunicationSpi) a.configuration().getCommunicationSpi()).simulateNodeFailure();
      long deadline = System.currentTimeMillis() + 30_000;

      while(b.cluster().forServers().nodes().size() != 2 ||
         c.cluster().forServers().nodes().size() != 2)
      {
         assertTrue(System.currentTimeMillis() < deadline, "A was not failed");
         Thread.sleep(100);
      }
   }

   private void assertExchangeCompletes() {
      ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
         Thread thread = new Thread(r, "create-cache");
         thread.setDaemon(true);
         return thread;
      });

      try {
         Future<?> created = executor.submit(
            () -> b.getOrCreateCache(productConfiguration("afterFailure")));
         assertDoesNotThrow(() -> created.get(30, TimeUnit.SECONDS),
                            "the partition map exchange did not complete");
      }
      finally {
         executor.shutdownNow();
      }
   }

   private void assertSurvivorsAgree() {
      for(String name : CACHES) {
         assertEquals(entries(b, name), entries(c, name), "B and C disagree on " + name);
      }
   }

   private static Map<Object, Object> entries(Ignite node, String name) {
      Map<Object, Object> map = new HashMap<>();

      for(javax.cache.Cache.Entry<Object, Object> e : node.cache(name).withKeepBinary()
         .localEntries(CachePeekMode.PRIMARY, CachePeekMode.BACKUP))
      {
         map.put(e.getKey(), e.getValue());
      }

      return map;
   }

   /**
    * Checks that the trigger, its group's list and its job's list agree on B.
    */
   @SuppressWarnings("unchecked")
   private void assertTriggerIndexed(TriggerKey key, JobKey jobKey) {
      boolean stored = b.cache(TRIGGERS).localPeek(key) != null;
      Collection<TriggerKey> group =
         (Collection<TriggerKey>) b.cache(TRIGGERS_BY_GROUP).localPeek(key.getGroup());
      Collection<TriggerKey> byJob =
         (Collection<TriggerKey>) b.cache(TRIGGERS_BY_JOB).localPeek(jobKey);
      assertEquals(stored, group != null && group.contains(key), "triggersByGroup");
      assertEquals(stored, byJob != null && byJob.contains(key), "triggersByJob");
   }

   private ClusterJobStore createStore(Ignite node) throws Exception {
      installContext(new NodeCluster(node));
      ClusterJobStore store = new ClusterJobStore();
      store.initialize(null, mock(SchedulerSignaler.class));
      store.schedulerStarted();
      return store;
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

   private static JobDetail createJob(boolean nonConcurrent) {
      return JobBuilder.newJob(nonConcurrent ? NonConcurrentJob.class : ConcurrentJob.class)
         .withIdentity("job", "ClusterJobStoreNodeFailureTest")
         .storeDurably()
         .build();
   }

   private static OperableTrigger createTrigger(JobDetail job, TriggerKey key) {
      SimpleTriggerImpl trigger = new SimpleTriggerImpl();
      trigger.setKey(key);
      trigger.setJobKey(job.getKey());
      trigger.setStartTime(new Date(System.currentTimeMillis() + 1000));
      trigger.setRepeatCount(SimpleTrigger.REPEAT_INDEFINITELY);
      trigger.setRepeatInterval(TimeUnit.HOURS.toMillis(1));
      trigger.computeFirstFireTime(null);
      return trigger;
   }

   private TriggerKey primaryTriggerKey(Ignite node) {
      for(int i = 0; ; i++) {
         TriggerKey key = TriggerKey.triggerKey("MT_" + i, "ClusterJobStoreNodeFailureTest");

         if(node.affinity(TRIGGERS).isPrimary(node.cluster().localNode(), key)) {
            return key;
         }
      }
   }

   private static String primaryKey(Ignite node, String cache) {
      for(int i = 0; ; i++) {
         String key = "S_" + i;

         if(node.affinity(cache).isPrimary(node.cluster().localNode(), key)) {
            return key;
         }
      }
   }

   private Ignite startNode(String name, int index, boolean failing) {
      IgniteConfiguration config = new IgniteConfiguration();
      config.setIgniteInstanceName("bug77879-store-" + name);
      config.setConsistentId("bug77879-store-" + name);
      config.setWorkDirectory(workDir.resolve(name).toString());
      config.setFailureDetectionTimeout(3000);
      config.setMetricsLogFrequency(0);
      config.setPeerClassLoadingEnabled(true);
      IgniteUtils.configBinaryTypes(config);
      config.setFailureHandler(failing ? new NoOpFailureHandler() : new StopNodeFailureHandler());

      TcpDiscoverySpi discovery = new TcpDiscoverySpi();
      discovery.setLocalAddress("127.0.0.1");
      discovery.setLocalPort(DISCOVERY_PORT);
      discovery.setLocalPortRange(10);
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(List.of(
         "127.0.0.1:" + DISCOVERY_PORT + ".." + (DISCOVERY_PORT + 9)));
      discovery.setIpFinder(ipFinder);
      config.setDiscoverySpi(discovery);

      TcpCommunicationSpi communication = new HoldingCommunicationSpi();
      communication.setLocalAddress("127.0.0.1");
      communication.setLocalPort(COMMUNICATION_PORT + index * 10);
      communication.setLocalPortRange(10);
      config.setCommunicationSpi(communication);

      DataStorageConfiguration storage = new DataStorageConfiguration();
      storage.getDefaultDataRegionConfiguration()
         .setInitialSize(64L << 20)
         .setMaxSize(64L << 20);
      config.setDataStorageConfiguration(storage);

      Ignite node = Ignition.start(config);
      nodes.add(node);
      return node;
   }

   @SuppressWarnings("unchecked")
   private static <K, V> CacheConfiguration<K, V> productConfiguration(String name) {
      try {
         Method method = IgniteCluster.class.getDeclaredMethod(
            "getCacheConfiguration", String.class, CacheMode.class, int.class);
         method.setAccessible(true);
         return (CacheConfiguration<K, V>) method.invoke(null, name, CacheMode.REPLICATED, 2);
      }
      catch(ReflectiveOperationException e) {
         throw new IllegalStateException(e);
      }
   }

   /**
    * A cluster whose replicated maps and transactions are those of one Ignite node.
    */
   private static final class NodeCluster extends MockCluster {
      NodeCluster(Ignite node) {
         this.node = node;
      }

      @Override
      public <K, V> DistributedMap<K, V> getReplicatedMap(String name) {
         return new IgniteDistributedMap<>(node.<K, V>cache(name));
      }

      @Override
      public <K, V> MultiMap<K, V> getReplicatedMultiMap(String name) {
         return new IgniteMultiMap<>(node.<K, Collection<V>>cache(name));
      }

      @Override
      public String getLocalMember() {
         return node.name();
      }

      @Override
      public <T, E extends Exception> T runInTransaction(long timeout, TimeUnit unit,
                                                          TransactionalAction<T, E> action)
         throws E
      {
         return IgniteCluster.runInTransaction(node, timeout, unit, action);
      }

      private final Ignite node;
   }

   /**
    * Holds the transaction prepare requests to the backup C while {@link #HOLD} is set.
    */
   public static class HoldingCommunicationSpi extends TcpCommunicationSpi {
      @Override
      public void sendMessage(ClusterNode node, Message msg,
                              IgniteInClosure<IgniteException> ackClosure)
         throws IgniteSpiException
      {
         if(HOLD.get() && node.id().equals(backupId) && msg instanceof GridIoMessage &&
            ((GridIoMessage) msg).message() instanceof GridDhtTxPrepareRequest)
         {
            HELD.add(() -> super.sendMessage(node, msg, ackClosure));
            return;
         }

         super.sendMessage(node, msg, ackClosure);
      }
   }

   @DisallowConcurrentExecution
   public static class NonConcurrentJob implements Job {
      @Override
      public void execute(JobExecutionContext context) {
      }
   }

   public static class ConcurrentJob implements Job {
      @Override
      public void execute(JobExecutionContext context) {
      }
   }

   @TempDir
   Path workDir;
   private Ignite a;
   private Ignite b;
   private Ignite c;
   private ApplicationContext savedAppContext;
   private final List<Ignite> nodes = new ArrayList<>();
   private static volatile UUID backupId;
   private static final AtomicBoolean HOLD = new AtomicBoolean();
   private static final List<Runnable> HELD = new CopyOnWriteArrayList<>();
   private static final String TRIGGERS = "jobstore.triggersByKey";
   private static final String TRIGGERS_BY_GROUP = "jobstore.triggersByGroup";
   private static final String TRIGGERS_BY_JOB = "jobstore.triggersByJob";
   private static final String RUNNING = "jobstore.runningJobs";
   private static final String OPEN_SHEETS =
      RuntimeViewsheetManager.class.getName() + ".openSheetsMap";
   private static final List<String> CACHES = List.of(
      "jobstore.jobsByKey", TRIGGERS, "jobstore.jobsByGroup", TRIGGERS_BY_GROUP, TRIGGERS_BY_JOB,
      "jobstore.calendarsByName", RUNNING, OPEN_SHEETS, "bug77879.locks");
   private static final int DISCOVERY_PORT = 48960;
   private static final int COMMUNICATION_PORT = 49000;
}
