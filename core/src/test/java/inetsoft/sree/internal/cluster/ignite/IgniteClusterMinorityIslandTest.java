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
import inetsoft.util.config.InetsoftConfig;
import inetsoft.util.health.ClusterHealthService;
import inetsoft.util.health.ClusterHealthStatus;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.configuration.ClientConnectorConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.failure.FailureContext;
import org.apache.ignite.failure.StopNodeFailureHandler;
import org.apache.ignite.spi.IgniteSpiOperationTimeoutException;
import org.apache.ignite.spi.IgniteSpiOperationTimeoutHelper;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoveryIoSession;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.ignite.spi.discovery.tcp.messages.TcpDiscoveryAbstractMessage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.net.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #78105: after a discovery split, Ignite never merges the two clusters again, and the node
 * left alone is a valid one-node cluster. The cluster health check reported it ready, so the
 * load balancer kept sending users to both clusters. Real nodes in one JVM, split at the
 * discovery layer and then healed: the node in the smaller cluster must report not-ready, the
 * larger cluster must stay ready, and nodes with nothing foreign answering must stay ready. In
 * the three one-node islands case exactly one node must survive. A node that restarts and
 * rejoins must never make the cluster look like an island, and an island must report ready
 * again once the larger cluster is gone.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class IgniteClusterMinorityIslandTest {
   @TempDir
   Path clusterDir;

   @BeforeEach
   void reset() {
      BLOCKED.clear();
      FAILURES.clear();
   }

   @AfterEach
   void cleanup() {
      BLOCKED.clear();
      InetsoftConfig.getInstance().getCluster().setMinorityIslandHalt(false);
   }

   @Test
   void smallerClusterReportsNotReadyAndLargerStaysReady() throws Exception {
      Nodes nodes = startNodes(3);

      try {
         ClusterHealthService[] health = nodes.health();

         // nothing foreign answers (the spare IP finder address has nothing behind it): every
         // node stays ready for longer than the confirmation window
         Thread.sleep(CONFIRM_MILLIS + 4 * CHECK_MILLIS);

         for(int i = 0; i < 3; i++) {
            assertReady(health[i], "n" + (i + 1) + " before the split");
         }

         // cut n3 off from n1 and n2 at the discovery layer, both ways, then heal the network
         int[] ports = nodes.discoveryPorts();
         BLOCKED.put(ports[2], new HashSet<>(Set.of(ports[0], ports[1])));
         BLOCKED.put(ports[0], new HashSet<>(Set.of(ports[2])));
         BLOCKED.put(ports[1], new HashSet<>(Set.of(ports[2])));
         await().atMost(Duration.ofSeconds(90)).until(() ->
            servers(nodes.get(2)) == 1 && servers(nodes.get(0)) == 2 &&
               servers(nodes.get(1)) == 2);
         BLOCKED.clear();

         // n3 stays alone after the heal (Ignite never merges) and must report not-ready
         await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(250)).until(() ->
            !health[2].getStatus().isReady());
         ClusterHealthStatus island = health[2].getStatus();
         assertFalse(island.isReady());
         assertTrue(island.getMessage().contains("minority cluster island"), island.getMessage());
         assertEquals(1, servers(nodes.get(2)), "the islands did not merge");

         // the larger cluster stays ready the whole time
         for(int round = 0; round < 8; round++) {
            assertReady(health[0], "n1 in the larger cluster");
            assertReady(health[1], "n2 in the larger cluster");
            Thread.sleep(CHECK_MILLIS);
         }

         assertTrue(FAILURES.isEmpty(), "halting is off by default: " + FAILURES);

         // once the island's node is gone, the larger cluster has nothing foreign answering
         nodes.close(2);
         Thread.sleep(CONFIRM_MILLIS + 4 * CHECK_MILLIS);
         assertReady(health[0], "n1 after n3 stopped");
         assertReady(health[1], "n2 after n3 stopped");
      }
      finally {
         nodes.closeAll();
      }
   }

   @Test
   void threeOneNodeIslandsLeaveExactlyOneSurvivor() throws Exception {
      InetsoftConfig.getInstance().getCluster().setMinorityIslandHalt(true);
      Nodes nodes = startNodes(3);

      try {
         ClusterHealthService[] health = nodes.health();
         int[] ports = nodes.discoveryPorts();

         for(int i = 0; i < 3; i++) {
            Set<Integer> others = new HashSet<>();

            for(int j = 0; j < 3; j++) {
               if(i != j) {
                  others.add(ports[j]);
               }
            }

            BLOCKED.put(ports[i], others);
         }

         await().atMost(Duration.ofSeconds(90)).until(() ->
            servers(nodes.get(0)) == 1 && servers(nodes.get(1)) == 1 &&
               servers(nodes.get(2)) == 1);
         BLOCKED.clear();

         // the tie between one-node clusters goes to the node that started first (n1); the
         // other two raise a segmentation failure, which halts them in production and stops
         // them here
         await().atMost(Duration.ofSeconds(60)).until(() -> FAILURES.size() >= 2);
         Thread.sleep(CONFIRM_MILLIS + 4 * CHECK_MILLIS);
         assertEquals(List.of("n2:SEGMENTATION", "n3:SEGMENTATION"),
                      FAILURES.stream().sorted().toList());
         assertReady(health[0], "n1, the surviving one-node cluster");
         assertNull(nodes.get(0).getMinorityIslandMessage());
      }
      finally {
         nodes.closeAll();
      }
   }

   @Test
   void restartedNodeRejoiningNeverMakesTheClusterAnIsland() throws Exception {
      Nodes nodes = startNodes(3);

      try {
         // n3 restarts: while it starts and joins, n1 and n2 must never see a better cluster
         nodes.close(2);
         ExecutorService executor = Executors.newSingleThreadExecutor();
         List<String> islands = new ArrayList<>();

         try {
            Future<IgniteCluster> restarted = executor.submit(() -> nodes.restart(2));

            while(!restarted.isDone()) {
               collectIslands(nodes, islands, 0, 1);
               Thread.sleep(50L);
            }

            restarted.get();
         }
         finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
         }

         await().atMost(Duration.ofSeconds(60)).until(() ->
            nodes.list.stream().allMatch(n -> servers(n) == 3));
         long end = System.currentTimeMillis() + CONFIRM_MILLIS + 6 * CHECK_MILLIS;

         while(System.currentTimeMillis() < end) {
            collectIslands(nodes, islands, 0, 1, 2);
            Thread.sleep(50L);
         }

         assertTrue(islands.isEmpty(), islands.toString());
         ClusterHealthService[] health = nodes.health();

         for(int i = 0; i < 3; i++) {
            assertReady(health[i], "n" + (i + 1) + " after n3 rejoined");
         }
      }
      finally {
         nodes.closeAll();
      }
   }

   @Test
   void islandIsReadyAgainOnceTheLargerClusterIsGone() throws Exception {
      Nodes nodes = startNodes(3);

      try {
         ClusterHealthService[] health = nodes.health();
         int[] ports = nodes.discoveryPorts();
         BLOCKED.put(ports[2], new HashSet<>(Set.of(ports[0], ports[1])));
         BLOCKED.put(ports[0], new HashSet<>(Set.of(ports[2])));
         BLOCKED.put(ports[1], new HashSet<>(Set.of(ports[2])));
         await().atMost(Duration.ofSeconds(90)).until(() ->
            servers(nodes.get(2)) == 1 && servers(nodes.get(0)) == 2);
         BLOCKED.clear();
         await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(250)).until(() ->
            !health[2].getStatus().isReady());

         // the larger cluster stops: nothing better answers, so n3 is the deployment again
         nodes.close(0);
         nodes.close(1);
         await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(250)).until(() ->
            health[2].getStatus().isReady());
         assertNull(nodes.get(2).getMinorityIslandMessage());
      }
      finally {
         nodes.closeAll();
      }
   }

   /**
    * Another, larger StyleBI deployment on the same host answers at a thin client port that
    * this deployment's spare IP finder address maps to (offset 2 -> client base + 2). It is not
    * a cluster of this deployment, so it must not make this one a minority island.
    */
   @Test
   void otherDeploymentOnSharedHostIsNotAnIsland() throws Exception {
      int clientBase = freePortRun(6);
      Nodes ours = startNodes("y", 2, clientBase, 5);
      Nodes other = null;

      try {
         // binds client ports base + 2 .. base + 4, after ours took base and base + 1
         other = startNodes("x", 3, clientBase, 5);
         ClusterHealthService[] health = ours.health();
         Thread.sleep(CONFIRM_MILLIS + 6 * CHECK_MILLIS);

         for(int i = 0; i < 2; i++) {
            assertReady(health[i], "y" + (i + 1) + " next to another deployment");
            assertNull(ours.get(i).getMinorityIslandMessage());
         }

         for(int i = 0; i < 3; i++) {
            assertNull(other.get(i).getMinorityIslandMessage(), "x" + (i + 1));
         }
      }
      finally {
         if(other != null) {
            other.closeAll();
         }

         ours.closeAll();
      }
   }

   private static void collectIslands(Nodes nodes, List<String> islands, int... indexes) {
      for(int index : indexes) {
         String message = nodes.get(index).getMinorityIslandMessage();

         if(message != null) {
            islands.add("n" + (index + 1) + ": " + message);
         }
      }
   }

   private static void assertReady(ClusterHealthService health, String what) {
      ClusterHealthStatus status = health.getStatus();
      assertTrue(status.isReady(), what + ": " + status.getMessage());
   }

   private Nodes startNodes(int count) throws Exception {
      return startNodes("n", count, freePortRun(count + 1), count);
   }

   /**
    * Starts a cluster of {@code count} nodes whose IP finder lists its own discovery port run
    * plus one spare address.
    *
    * @param clientBase  the client connector base port.
    * @param clientRange the client connector port range.
    */
   private Nodes startNodes(String prefix, int count, int clientBase, int clientRange)
      throws Exception
   {
      // like production, every node has the same base ports and binds the first free one
      int discoveryBase = freePortRun(count + 1);
      List<String> addresses = new ArrayList<>();

      // one more address than nodes, with nothing behind it
      for(int i = 0; i <= count; i++) {
         addresses.add("127.0.0.1:" + (discoveryBase + i));
      }

      Nodes nodes = new Nodes();
      nodes.prefix = prefix;
      nodes.starter = name ->
         start(name, discoveryBase, count, clientBase, clientRange, addresses);

      for(int i = 0; i < count; i++) {
         nodes.add(nodes.starter.start(prefix + (i + 1)));
      }

      await().atMost(Duration.ofSeconds(60)).until(() ->
         nodes.list.stream().allMatch(n -> servers(n) == count));

      DistributedMap<String, String> map =
         nodes.get(0).getReplicatedMap("inetsoft.storage.kv.sreeProperties");
      map.put("key", "value");
      return nodes;
   }

   private IgniteCluster start(String name, int discoveryBase, int portRange, int clientBase,
                               int clientRange, List<String> addresses) throws Exception
   {
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(addresses);
      PartitionableDiscoverySpi disco = new PartitionableDiscoverySpi();
      disco.setLocalAddress("127.0.0.1");
      disco.setLocalPort(discoveryBase);
      disco.setLocalPortRange(portRange);
      disco.setIpFinder(ipFinder);
      TcpCommunicationSpi comm = new TcpCommunicationSpi();
      comm.setLocalAddress("127.0.0.1");
      comm.setLocalPort(freePort());
      comm.setLocalPortRange(0);
      ClientConnectorConfiguration connector = new ClientConnectorConfiguration();
      connector.setHost("127.0.0.1");
      connector.setPort(clientBase);
      connector.setPortRange(clientRange);

      IgniteConfiguration config = IgniteCluster.getDefaultConfig(clusterDir.resolve(name));
      config.setIgniteInstanceName("bug78105-" + name + "-" + UUID.randomUUID());
      config.setLocalHost("127.0.0.1");
      config.setConsistentId(name);
      config.setDiscoverySpi(disco);
      config.setCommunicationSpi(comm);
      config.setClientConnectorConfiguration(connector);
      // production uses Ignite's default StopNodeOrHaltFailureHandler, which would halt this
      // test JVM on SEGMENTATION; record the call and stop the node instead
      config.setFailureHandler(new StopNodeFailureHandler() {
         @Override
         public boolean handle(Ignite ignite, FailureContext ctx) {
            FAILURES.add(name + ":" + ctx.type());
            return super.handle(ignite, ctx);
         }
      });

      // make the start times of the nodes distinct, so the tie-break is by start order
      Thread.sleep(20L);
      return new IgniteCluster(config, CHECK_MILLIS, CONFIRM_MILLIS);
   }

   private static int servers(IgniteCluster node) {
      try {
         return node.getIgniteInstance().cluster().forServers().nodes().size();
      }
      catch(Exception e) {
         return -1;
      }
   }

   private static int freePort() throws IOException {
      try(ServerSocket socket = new ServerSocket(0)) {
         return socket.getLocalPort();
      }
   }

   /**
    * Finds a run of free ports below the ephemeral range, so that neither the other sockets of
    * the test nor outgoing connections take a port of the run before a node binds it.
    */
   private static int freePortRun(int count) throws IOException {
      Random random = new Random();

      for(int attempt = 0; attempt < 50; attempt++) {
         // Linux starts its ephemeral range at 32768, Windows at 49152
         int base = 20000 + random.nextInt(12000);
         boolean free = true;

         for(int i = 0; i < count && free; i++) {
            try(ServerSocket ignored = new ServerSocket(base + i, 50,
                                                        InetAddress.getByName("127.0.0.1")))
            {
               // free
            }
            catch(IOException e) {
               free = false;
            }
         }

         if(free) {
            return base;
         }
      }

      throw new IOException("No run of " + count + " free ports");
   }

   private static final class Nodes {
      void add(IgniteCluster node) {
         list.add(node);
      }

      IgniteCluster get(int index) {
         return list.get(index);
      }

      int[] discoveryPorts() {
         return list.stream()
            .mapToInt(n -> ((TcpDiscoveryNode) n.getIgniteInstance().cluster().localNode())
               .discoveryPort())
            .toArray();
      }

      ClusterHealthService[] health() {
         KeyValueStorageManager kvm = mock(KeyValueStorageManager.class);
         KeyValueStorage<?> storage = mock(KeyValueStorage.class);
         when(storage.isLoaded()).thenReturn(true);
         doReturn(storage).when(kvm).peekStorage(eq("sreeProperties"));
         ClusterHealthService[] health = new ClusterHealthService[list.size()];

         for(int i = 0; i < health.length; i++) {
            health[i] = new ClusterHealthService(list.get(i), kvm);
            services.add(health[i]);
         }

         return health;
      }

      void close(int index) {
         IgniteCluster node = list.get(index);

         if(closed.add(index)) {
            try {
               node.close();
            }
            catch(Exception ignore) {
               // already stopped by the failure handler
            }
         }
      }

      /**
       * Starts a new node in place of a stopped one, with the same ports and IP finder.
       */
      IgniteCluster restart(int index) throws Exception {
         IgniteCluster node = starter.start(prefix + (index + 1));
         list.set(index, node);
         closed.remove(index);
         return node;
      }

      void closeAll() {
         services.forEach(ClusterHealthService::close);

         // stop the survivors last, so no node sees the others leave as a failure
         for(int i = list.size() - 1; i >= 0; i--) {
            close(i);
         }
      }

      private final List<IgniteCluster> list = new ArrayList<>();
      private final List<ClusterHealthService> services = new ArrayList<>();
      private final Set<Integer> closed = new HashSet<>();
      private NodeStarter starter;
      private String prefix;
   }

   @FunctionalInterface
   private interface NodeStarter {
      IgniteCluster start(String name) throws Exception;
   }

   /**
    * Refuses discovery connections and writes between chosen ports, keyed by the port the
    * local node actually bound.
    */
   static class PartitionableDiscoverySpi extends TcpDiscoverySpi {
      private boolean blocked(int remotePort) {
         TcpDiscoveryNode local = (TcpDiscoveryNode) getLocalNode();

         if(local == null) {
            return false;
         }

         Set<Integer> set = BLOCKED.get(local.discoveryPort());
         return set != null && set.contains(remotePort);
      }

      @Override
      protected Socket openSocket(Socket sock, InetSocketAddress remAddr,
                                  IgniteSpiOperationTimeoutHelper timeoutHelper)
         throws IOException, IgniteSpiOperationTimeoutException
      {
         if(blocked(remAddr.getPort())) {
            sock.close();
            throw new ConnectException("partitioned (test) " + remAddr);
         }

         return super.openSocket(sock, remAddr, timeoutHelper);
      }

      @Override
      protected void writeMessage(TcpDiscoveryIoSession ses, TcpDiscoveryAbstractMessage msg,
                                  long timeout) throws IOException, IgniteCheckedException
      {
         if(blocked(ses.socket().getPort())) {
            throw new SocketException("partitioned (test) write to " + ses.socket().getPort());
         }

         super.writeMessage(ses, msg, timeout);
      }

      @Override
      protected void writeToSocket(Socket sock, TcpDiscoveryAbstractMessage msg, byte[] data,
                                   long timeout) throws IOException
      {
         if(blocked(sock.getPort())) {
            throw new SocketException("partitioned (test) write to " + sock.getPort());
         }

         super.writeToSocket(sock, msg, data, timeout);
      }
   }

   private static final long CHECK_MILLIS = 500L;
   private static final long CONFIRM_MILLIS = 3000L;
   private static final Map<Integer, Set<Integer>> BLOCKED = new ConcurrentHashMap<>();
   private static final List<String> FAILURES = new CopyOnWriteArrayList<>();
}
