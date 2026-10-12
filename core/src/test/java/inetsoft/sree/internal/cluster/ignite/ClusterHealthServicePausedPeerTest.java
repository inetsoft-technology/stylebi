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

import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.storage.KeyValueStorage;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.util.health.ClusterHealthService;
import inetsoft.util.health.ClusterHealthStatus;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.lang.IgniteFuture;
import org.apache.ignite.lang.IgniteRunnable;
import org.apache.ignite.plugin.extensions.communication.Message;
import org.apache.ignite.spi.communication.CommunicationListener;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #78366: while one server node was paused ({@code docker pause}) but still in the
 * topology, the healthy nodes' readiness check reported not-ready, because the check called
 * {@code IgniteCache.size()}, a cluster-wide task that waits for a reply from every data node.
 * Real nodes in one JVM, one of them muted at the communication layer (discovery untouched, so
 * it stays in the topology like a paused peer before the failure detection timeout): the
 * healthy nodes must keep reporting ready the whole time.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class ClusterHealthServicePausedPeerTest {
   @TempDir
   Path clusterDir;

   @Test
   void healthyNodesStayReadyWhilePeerIsPaused() throws Exception {
      int[] discoveryPorts = { freePort(), freePort(), freePort() };
      List<String> addresses = new ArrayList<>();

      for(int port : discoveryPorts) {
         addresses.add("127.0.0.1:" + port);
      }

      List<IgniteCluster> nodes = new ArrayList<>();
      List<ClusterHealthService> health = new ArrayList<>();
      MutableCommunicationSpi pausedComm = null;

      try {
         for(int i = 0; i < 3; i++) {
            MutableCommunicationSpi comm = new MutableCommunicationSpi();
            nodes.add(start("n" + (i + 1), discoveryPorts[i], addresses, comm));
            pausedComm = comm;
         }

         await().atMost(Duration.ofSeconds(60)).until(() ->
            nodes.stream().allMatch(n -> servers(n) == 3));

         DistributedMap<String, String> map = nodes.get(0).getReplicatedMap(SREE_PROPERTIES_MAP);
         map.put("key", "value");

         for(int i = 0; i < 2; i++) {
            health.add(new ClusterHealthService(nodes.get(i), loadedStorage()));
            assertReady(health.get(i), "n" + (i + 1) + " before the pause");
         }

         // pause n3: it stays in the topology but answers no communication message
         pausedComm.pause();

         // the cluster-wide size() now waits on n3, which shows the pause is effective
         IgniteFuture<Integer> size = nodes.get(0).getIgniteInstance()
            .cache(SREE_PROPERTIES_MAP).sizeAsync();
         Thread.sleep(2000L);
         assertFalse(size.isDone(), "a cluster-wide size() waits for the paused peer");

         // longer than the 5000 ms probe bound: the healthy nodes stay ready the whole time
         long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PAUSE_MILLIS);

         while(System.nanoTime() - end < 0) {
            assertReady(health.get(0), "n1 while n3 is paused");
            assertReady(health.get(1), "n2 while n3 is paused");
            Thread.sleep(500L);
         }

         assertEquals(3, servers(nodes.get(0)), "n3 stayed in the topology during the pause");

         pausedComm.resume();
         size.get(30, TimeUnit.SECONDS);
         assertReady(health.get(0), "n1 after the pause");
         assertReady(health.get(1), "n2 after the pause");
      }
      finally {
         if(pausedComm != null) {
            pausedComm.resume();
         }

         health.forEach(ClusterHealthService::close);

         for(int i = nodes.size() - 1; i >= 0; i--) {
            nodes.get(i).close();
         }
      }
   }

   private IgniteCluster start(String name, int discoveryPort, List<String> addresses,
                               TcpCommunicationSpi comm) throws Exception
   {
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(addresses);
      TcpDiscoverySpi disco = new TcpDiscoverySpi();
      disco.setLocalAddress("127.0.0.1");
      disco.setLocalPort(discoveryPort);
      disco.setLocalPortRange(0);
      disco.setIpFinder(ipFinder);
      comm.setLocalAddress("127.0.0.1");
      comm.setLocalPort(freePort());
      comm.setLocalPortRange(0);

      IgniteConfiguration config = IgniteCluster.getDefaultConfig(clusterDir.resolve(name));
      config.setIgniteInstanceName("bug78366-" + name + "-" + UUID.randomUUID());
      config.setLocalHost("127.0.0.1");
      config.setConsistentId(name);
      config.setDiscoverySpi(disco);
      config.setCommunicationSpi(comm);
      return new IgniteCluster(config);
   }

   private static KeyValueStorageManager loadedStorage() {
      KeyValueStorageManager kvm = mock(KeyValueStorageManager.class);
      KeyValueStorage<?> storage = mock(KeyValueStorage.class);
      when(storage.isLoaded()).thenReturn(true);
      doReturn(storage).when(kvm).peekStorage(eq("sreeProperties"));
      return kvm;
   }

   private static void assertReady(ClusterHealthService service, String what) {
      long start = System.nanoTime();
      ClusterHealthStatus status = service.getStatus();
      long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      assertTrue(status.isReady(), what + ": " + status.getMessage() + " after " + millis + " ms");
   }

   private static int servers(IgniteCluster node) {
      try {
         return node.getIgniteInstance().cluster().forServers().nodes().size();
      }
      catch(Exception e) {
         return -1;
      }
   }

   private static int freePort() throws Exception {
      try(ServerSocket socket = new ServerSocket(0)) {
         return socket.getLocalPort();
      }
   }

   private static final String SREE_PROPERTIES_MAP = "inetsoft.storage.kv.sreeProperties";
   private static final long PAUSE_MILLIS = 8000L;

   /**
    * A communication SPI that can stop handling incoming messages, like a paused process. Every
    * node uses this class, since Ignite requires the same communication SPI on all nodes.
    */
   static class MutableCommunicationSpi extends TcpCommunicationSpi {
      @Override
      public void setListener(CommunicationListener<Message> listener) {
         super.setListener(listener == null ? null : new CommunicationListener<>() {
            @Override
            public void onMessage(UUID nodeId, Message msg, IgniteRunnable msgC) {
               awaitResume();
               listener.onMessage(nodeId, msg, msgC);
            }

            @Override
            public void onDisconnected(UUID nodeId) {
               listener.onDisconnected(nodeId);
            }
         });
      }

      void pause() {
         gate = new CountDownLatch(1);
      }

      void resume() {
         gate.countDown();
      }

      private void awaitResume() {
         try {
            gate.await();
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
         }
      }

      private volatile CountDownLatch gate = new CountDownLatch(0);
   }
}
