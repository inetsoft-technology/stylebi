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
package inetsoft.sree.internal.cluster.ignite;

import org.apache.ignite.*;
import org.apache.ignite.cache.CacheMode;
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

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77879: a node that fails while a write it made under a lock of a replicated map commits
 * must not leave a transaction on a surviving backup that blocks every later partition map
 * exchange. With an explicit cache-entry lock ({@code IgniteCache.lock}) the backup's remote
 * transaction waited forever in COMMITTING for the lock candidate Ignite had removed with the
 * failed node, and a cache could no longer be created. A lock taken by a pessimistic transaction
 * ({@link IgniteCluster#runInTransaction} and {@link IgniteDistributedMap#getForUpdate}) is
 * recovered.
 *
 * <p>Three embedded server nodes A, B and C share a replicated map configured like the product's
 * ({@code getReplicatedMap}). A writes a key whose primary is B under a lock; B's prepare request to
 * the backup C is held while A fails, as a halted JVM would, then released. A cache must then be
 * created within 30 s, and B and C must hold the same value. No Spring context is needed: only the
 * map, the transaction helper and the cache configuration are product code. Takes about 6 s.
 */
@Tag("slow")
class IgniteTransactionNodeFailureTest {
   @AfterEach
   void stopNodes() {
      HOLD.set(false);
      HELD.clear();

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
   @Timeout(120)
   void exchangeCompletesAfterTheLockingNodeFailsMidCommit() throws Exception {
      CacheConfiguration<String, String> cacheConfig =
         productReplicatedCacheConfiguration("jobstore.triggersByKey");
      Ignite a = startNode("A", 0, true);
      Ignite b = startNode("B", 1, false);
      Ignite c = startNode("C", 2, false);
      backupId = c.cluster().localNode().id();

      IgniteDistributedMap<String, String> map =
         new IgniteDistributedMap<>(a.getOrCreateCache(cacheConfig));
      b.cache(cacheConfig.getName());
      c.cache(cacheConfig.getName());
      String key = primaryKey(b, cacheConfig.getName());

      // what ClusterJobStore.storeTrigger does: lock the trigger, check it, then write it
      HOLD.set(true);
      AtomicReference<Throwable> writeFailure = new AtomicReference<>();
      Thread writer = new Thread(() -> {
         try {
            IgniteCluster.runInTransaction(a, 10, TimeUnit.SECONDS, () -> {
               map.getForUpdate(key);
               map.set(key, "value");
               return null;
            });
         }
         catch(RuntimeException ex) {
            // the node fails while the write commits
            writeFailure.set(ex);
         }
      }, "writer");
      writer.setDaemon(true);
      writer.start();

      long deadline = System.currentTimeMillis() + 30_000;

      while(HELD.isEmpty()) {
         assertTrue(System.currentTimeMillis() < deadline,
                    "the write never reached the backup: " + writeFailure.get());
         Thread.sleep(50);
      }

      Thread.sleep(500);

      // A stops responding, as a node in a long pause or a halted JVM does
      ((TcpDiscoverySpi) a.configuration().getDiscoverySpi()).simulateNodeFailure();
      ((TcpCommunicationSpi) a.configuration().getCommunicationSpi()).simulateNodeFailure();

      while(b.cluster().forServers().nodes().size() != 2 ||
         c.cluster().forServers().nodes().size() != 2)
      {
         assertTrue(System.currentTimeMillis() < deadline, "A was not failed");
         Thread.sleep(100);
      }

      // the prepare request that was in flight when A failed now reaches C
      Thread.sleep(1000);
      HOLD.set(false);

      for(Runnable held : HELD) {
         held.run();
      }

      // creating a cache needs a partition map exchange, which waits for every transaction of
      // the topology A left
      ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
         Thread thread = new Thread(r, "create-cache");
         thread.setDaemon(true);
         return thread;
      });

      try {
         Future<?> created = executor.submit(
            () -> b.getOrCreateCache(productReplicatedCacheConfiguration("afterFailure")));
         assertDoesNotThrow(() -> created.get(30, TimeUnit.SECONDS),
                            "the partition map exchange did not complete");
      }
      finally {
         executor.shutdownNow();
      }

      assertEquals(b.cache(cacheConfig.getName()).localPeek(key),
                   c.cache(cacheConfig.getName()).localPeek(key),
                   "the surviving nodes disagree on the value");
   }

   private Ignite startNode(String name, int index, boolean failing) {
      IgniteConfiguration config = new IgniteConfiguration();
      config.setIgniteInstanceName("bug77879-" + name);
      config.setConsistentId("bug77879-" + name);
      config.setWorkDirectory(workDir.resolve(name).toString());
      config.setFailureDetectionTimeout(3000);
      config.setMetricsLogFrequency(0);
      config.setPeerClassLoadingEnabled(true);
      IgniteUtils.configBinaryTypes(config);

      // the failing node stands in for the halted one, and no node may halt the test JVM as the
      // product's default handler would
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

   /**
    * Gets the configuration that {@link IgniteCluster#getReplicatedMap} creates a map's cache with.
    */
   @SuppressWarnings("unchecked")
   private static CacheConfiguration<String, String> productReplicatedCacheConfiguration(
      String name) throws Exception
   {
      Method method = IgniteCluster.class.getDeclaredMethod(
         "getCacheConfiguration", String.class, CacheMode.class, int.class);
      method.setAccessible(true);
      return (CacheConfiguration<String, String>) method.invoke(null, name, CacheMode.REPLICATED, 2);
   }

   private static String primaryKey(Ignite node, String cache) {
      for(int i = 0; ; i++) {
         String key = "MT_" + i;

         if(node.affinity(cache).isPrimary(node.cluster().localNode(), key)) {
            return key;
         }
      }
   }

   /**
    * Holds the primary's transaction prepare requests to the backup C while {@link #HOLD} is set.
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

   @TempDir
   Path workDir;
   private final List<Ignite> nodes = new ArrayList<>();
   private static volatile UUID backupId;
   private static final AtomicBoolean HOLD = new AtomicBoolean();
   private static final List<Runnable> HELD = new CopyOnWriteArrayList<>();
   private static final int DISCOVERY_PORT = 48720;
   private static final int COMMUNICATION_PORT = 48900;
}
