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
package inetsoft.web.portal.controller.database;

import inetsoft.sree.internal.cluster.ignite.IgniteCluster;
import inetsoft.sree.internal.cluster.ignite.IgniteClusterTestUtils;
import inetsoft.test.*;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.model.database.graph.TableDetailJoinInfo;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.cache.Cache;
import javax.cache.expiry.Duration;
import javax.cache.expiry.TouchedExpiryPolicy;
import java.awt.*;
import java.lang.reflect.*;
import java.net.ServerSocket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #78428, concurrent changes of a physical view runtime, on two embedded Ignite nodes, must
 * not overwrite each other. A writer reads the runtime, another writer changes it, and the first
 * one writes it back: without a lock, the second change is lost.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class RuntimePartitionRaceTest {
   @BeforeAll
   static void startNodes() throws Exception {
      clusterDir = Files.createTempDirectory("cluster-78428");
      int port1 = freePort();
      int port2 = freePort();
      node1 = startNode("rt78428a-" + UUID.randomUUID(), port1, port1, port2);
      node2 = startNode("rt78428b-" + UUID.randomUUID(), port2, port1, port2);
      long end = System.currentTimeMillis() + 30000L;

      while(node1.getClusterNodeIds().size() != 2 && System.currentTimeMillis() < end) {
         Thread.sleep(50L);
      }

      assertEquals(2, node1.getClusterNodeIds().size());
   }

   @AfterAll
   static void stopNodes() throws Exception {
      if(node2 != null) {
         node2.close();
      }

      if(node1 != null) {
         node1.close();
      }
   }

   /**
    * PUT graph/size reads the runtime, join/add on the other node adds a join, and the size
    * write must not drop it.
    */
   @ParameterizedTest
   @EnumSource(value = CacheMode.class, names = { "REPLICATED", "PARTITIONED" })
   void graphSizeKeepsJoinAddedOnOtherNode(CacheMode mode) throws Exception {
      services(mode);
      String id = open();
      PhysicalGraphModelService graph = new PhysicalGraphModelService(svc1, null, null);

      long joinMs = race(svc1, () -> graph.updateGraphPaneSize(id, 999, 777),
                         () -> manager(svc2).addJoin(id, join(), "public.ORDER_DETAILS"));

      RuntimePartitionService.RuntimeXPartition runtime = svc2.getRuntimePartition(id);
      assertEquals(1, runtime.getPartition().getRelationshipCount(), "the join was lost");
      assertEquals(999, runtime.getGraphWidth(), "the graph size was lost");
      assertTrue(joinMs >= 300, "the join didn't wait for the size update: " + joinMs);
   }

   /** Two requests on the same node race the same way, the cache returns a copy per read. */
   @Test
   void graphSizeKeepsJoinAddedOnSameNode() throws Exception {
      services(CacheMode.REPLICATED);
      String id = open();
      PhysicalGraphModelService graph = new PhysicalGraphModelService(svc1, null, null);
      RuntimePartitionService other = new RuntimePartitionService(node1);
      other.init();

      race(svc1, () -> graph.updateGraphPaneSize(id, 999, 777),
           () -> manager(other).addJoin(id, join(), "public.ORDER_DETAILS"));

      RuntimePartitionService.RuntimeXPartition runtime = svc1.getRuntimePartition(id);
      assertEquals(1, runtime.getPartition().getRelationshipCount(), "the join was lost");
      assertEquals(999, runtime.getGraphWidth(), "the graph size was lost");
   }

   /** PUT graph/node/width changes the bounds of a table, join/add must not be dropped. */
   @ParameterizedTest
   @EnumSource(value = CacheMode.class, names = { "REPLICATED", "PARTITIONED" })
   void nodeWidthKeepsJoinAddedOnOtherNode(CacheMode mode) throws Exception {
      services(mode);
      String id = open();
      PhysicalGraphModelService graph = new PhysicalGraphModelService(svc1, null, null);

      race(svc1, () -> graph.updateGraphNodeWidth(id, "public.ORDERS", 321),
           () -> manager(svc2).addJoin(id, join(), "public.ORDER_DETAILS"));

      XPartition partition = svc2.getPartition(id);
      assertEquals(1, partition.getRelationshipCount(), "the join was lost");
      assertEquals(321, partition.getBounds("public.ORDERS").width, "the width was lost");
   }

   /**
    * POST join reads meta-data before it adds the join. The runtime must not be locked while
    * it does, and a join added meanwhile on the other node must be kept.
    */
   @ParameterizedTest
   @EnumSource(value = CacheMode.class, names = { "REPLICATED", "PARTITIONED" })
   void slowMetaDataHoldsNoLock(CacheMode mode) throws Exception {
      services(mode);
      String id = open();
      CountDownLatch inMetaData = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      PhysicalModelService modelService = mock(PhysicalModelService.class);
      when(modelService.getMetaDataProvider(anyString())).thenAnswer(i -> {
         inMetaData.countDown();
         assertTrue(release.await(20, TimeUnit.SECONDS));
         return mock(DefaultMetaDataProvider.class);
      });
      PhysicalGraphModelController controller =
         new PhysicalGraphModelController(svc1, modelService, null, manager(svc1));
      TableDetailJoinInfo info = new TableDetailJoinInfo();
      info.setRuntimeId(id);
      info.setSourceTable("public.ORDERS");
      info.setSourceColumn("CUSTOMER_ID");
      info.setTargetTable("public.CUSTOMERS");
      info.setTargetColumn("CUSTOMER_ID");

      Future<?> create = run("j78428", () -> {
         controller.createJoin(info);
         return null;
      });

      try {
         assertTrue(inMetaData.await(10, TimeUnit.SECONDS));
         long start = System.currentTimeMillis();
         Future<?> join = run("a78428", () -> {
            manager(svc2).addJoin(id, join(), "public.ORDER_DETAILS");
            return null;
         });
         join.get(3, TimeUnit.SECONDS);
         long joinMs = System.currentTimeMillis() - start;
         assertTrue(joinMs < 2000, "the join waited for the meta-data read: " + joinMs);
      }
      finally {
         release.countDown();
      }

      create.get(20, TimeUnit.SECONDS);
      assertEquals(2, svc2.getPartition(id).getRelationshipCount(), "a join was lost");
   }

   /**
    * Runs a writer that is held between its read and its write of the runtime while another
    * writer runs. A writer that reads the runtime in a transaction is only delayed, the other
    * writer must wait for its lock. A writer without a transaction is held until the other
    * writer is done, and then writes its stale copy.
    *
    * @return how long the other writer took, in milliseconds.
    */
   private long race(RuntimePartitionService first, Runnable firstWriter, Runnable otherWriter)
      throws Exception
   {
      CountDownLatch read = new CountDownLatch(1);
      CountDownLatch otherDone = new CountDownLatch(1);
      IgniteCluster node = first == svc2 ? node2 : node1;
      gate(first, node, "w78428", read, otherDone);
      Future<?> f = run("w78428", () -> {
         firstWriter.run();
         return null;
      });
      long elapsed;

      try {
         assertTrue(read.await(10, TimeUnit.SECONDS), "the first writer didn't read the runtime");
         long start = System.currentTimeMillis();
         otherWriter.run();
         elapsed = System.currentTimeMillis() - start;
      }
      finally {
         otherDone.countDown();
      }

      f.get(20, TimeUnit.SECONDS);
      return elapsed;
   }

   /**
    * Wraps the cache of a service in a proxy that signals the read of the runtime by a thread,
    * and holds that thread's write of it.
    */
   @SuppressWarnings("unchecked")
   private static void gate(RuntimePartitionService svc, IgniteCluster node, String thread,
                            CountDownLatch read, CountDownLatch otherDone)
      throws Exception
   {
      Field field = RuntimePartitionService.class.getDeclaredField("cache");
      field.setAccessible(true);
      Cache<Object, Object> real = (Cache<Object, Object>) field.get(svc);
      Object proxy = Proxy.newProxyInstance(
         RuntimePartitionRaceTest.class.getClassLoader(), new Class<?>[] { Cache.class },
         (p, m, args) -> {
            boolean mine = thread.equals(Thread.currentThread().getName());
            boolean inTx = node.getIgniteInstance().transactions().tx() != null;

            if(mine && m.getName().equals("put")) {
               if(inTx) {
                  // the other writer can't write before this one commits, only delay it
                  Thread.sleep(500L);
               }
               else {
                  // an unlocked read-modify-write: let the other writer run before the write
                  read.countDown();
                  assertTrue(otherDone.await(10, TimeUnit.SECONDS));
               }
            }

            Object result;

            try {
               result = m.invoke(real, args);
            }
            catch(InvocationTargetException e) {
               throw e.getCause();
            }

            if(mine && inTx && m.getName().equals("get")) {
               read.countDown();
            }

            return result;
         });
      field.set(svc, proxy);
   }

   private static Future<?> run(String name, Callable<?> callable) {
      FutureTask<?> task = new FutureTask<>(callable);
      Thread thread = new Thread(task, name);
      thread.setDaemon(true);
      thread.start();
      return task;
   }

   /**
    * Creates a service on each node, using a cache of the mode.
    */
   private void services(CacheMode mode) throws Exception {
      svc1 = new RuntimePartitionService(node1);
      svc1.init();
      svc2 = new RuntimePartitionService(node2);
      svc2.init();

      if(mode == CacheMode.PARTITIONED) {
         useCache(svc1, node1, mode);
         useCache(svc2, node2, mode);
      }

      CacheConfiguration<?, ?> config =
         ((IgniteCache<?, ?>) cacheOf(svc1)).getConfiguration(CacheConfiguration.class);
      assertEquals(mode, config.getCacheMode());
   }

   /**
    * Replaces the cache of a service with one of another mode, configured like the product
    * configures it.
    */
   @SuppressWarnings("unchecked")
   private static void useCache(RuntimePartitionService svc, IgniteCluster node, CacheMode mode)
      throws Exception
   {
      Method method = IgniteCluster.class.getDeclaredMethod(
         "getCacheConfiguration", String.class, CacheMode.class, int.class);
      method.setAccessible(true);
      CacheConfiguration<String, RuntimePartitionService.RuntimeXPartition> config =
         (CacheConfiguration<String, RuntimePartitionService.RuntimeXPartition>) method.invoke(
            null, RuntimePartitionService.CACHE_NAME + "." + mode, mode, 1);
      config.setEagerTtl(true);
      Cache<String, RuntimePartitionService.RuntimeXPartition> cache =
         node.getIgniteInstance().getOrCreateCache(config).withExpiryPolicy(
            new TouchedExpiryPolicy(new Duration(TimeUnit.MINUTES, 3L)));
      Field field = RuntimePartitionService.class.getDeclaredField("cache");
      field.setAccessible(true);
      field.set(svc, cache);
   }

   private static Cache<?, ?> cacheOf(RuntimePartitionService svc) throws Exception {
      Field field = RuntimePartitionService.class.getDeclaredField("cache");
      field.setAccessible(true);
      return (Cache<?, ?>) field.get(svc);
   }

   private String open() {
      XPartition p = new XPartition("v78428");
      p.addTable("public.ORDERS", new Rectangle(10, 10, 100, 100));
      p.addTable("public.ORDER_DETAILS", new Rectangle(200, 10, 100, 100));
      p.addTable("public.CUSTOMERS", new Rectangle(400, 10, 100, 100));
      return svc1.createModel(p, "ds78428").getId();
   }

   private static PhysicalModelManagerService manager(RuntimePartitionService svc) {
      return new PhysicalModelManagerService(null, null, svc, null, null, null, null, null);
   }

   private static JoinModel join() {
      JoinModel join = new JoinModel();
      join.setColumn("ORDER_ID");
      join.setForeignTable("public.ORDERS");
      join.setForeignColumn("ORDER_ID");
      join.setType(JoinType.EQUAL);
      join.setMergingRule(MergingRule.AND);
      return join;
   }

   private static IgniteCluster startNode(String instance, int discoPort, int... ports)
      throws Exception
   {
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(Arrays.stream(ports).mapToObj(p -> "127.0.0.1:" + p).toList());
      TcpDiscoverySpi disco = new TcpDiscoverySpi();
      disco.setLocalAddress("127.0.0.1");
      disco.setLocalPort(discoPort);
      disco.setLocalPortRange(0);
      disco.setIpFinder(ipFinder);
      TcpCommunicationSpi comm = new TcpCommunicationSpi();
      comm.setLocalAddress("127.0.0.1");
      comm.setLocalPort(freePort());
      comm.setLocalPortRange(0);
      IgniteConfiguration config = IgniteCluster.getDefaultConfig(clusterDir.resolve(instance));
      config.setIgniteInstanceName(instance);
      config.setLocalHost("127.0.0.1");
      config.setDiscoverySpi(disco);
      config.setCommunicationSpi(comm);
      return IgniteClusterTestUtils.getIgniteCluster(config);
   }

   private static int freePort() throws Exception {
      try(ServerSocket socket = new ServerSocket(0)) {
         return socket.getLocalPort();
      }
   }

   private static Path clusterDir;
   private static IgniteCluster node1;
   private static IgniteCluster node2;
   private RuntimePartitionService svc1;
   private RuntimePartitionService svc2;
}
