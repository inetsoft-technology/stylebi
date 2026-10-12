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

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.failure.NoOpFailureHandler;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78364: {@link IgniteCluster#sendMessage(java.io.Serializable)} sent to the whole topology
 * with one {@code sendOrdered}. A cloud-runner task container is an Ignite client started with
 * {@code forceClientToServerConnections}, which a server cannot dial: it asks the client to
 * connect back and waits up to {@code failureDetectionTimeout}. When such a client leaves without
 * a graceful Ignite stop (container killed, OOM, network loss) it stays in the topology until
 * discovery drops it, and meanwhile every broadcast stalled its caller for that timeout and then
 * threw, although every live node had received the message.
 * <p>
 * Two embedded servers, a live force-mode client and a force-mode client whose discovery and
 * communication SPIs are failed, as a killed container's are. A graceful {@code close()} does not
 * reproduce the bug. The real {@code sendMessage} runs on a bare {@link IgniteCluster} whose
 * {@code ignite} is a server or the live client.
 */
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IgniteClusterBroadcastClientFailureTest {
   @BeforeAll
   void startCluster(@TempDir Path workDir) throws Exception {
      this.workDir = workDir;
      serverA = startNode("a", 0, false);
      serverB = startNode("b", 1, false);
      deadClient = startNode("c", 2, true);
      liveClient = startNode("d", 3, true);
      topic = messageTopic();

      for(Ignite node : nodes) {
         BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
         received.put(node.name(), queue);
         node.message().localListen(topic, (UUID sender, Object message) -> {
            queue.add(message);
            return true;
         });
      }

      // every node gets a whole-topology broadcast while all members are alive, which also opens
      // the communication connections the later sends use
      IgniteCluster cluster = newBareCluster(serverA);

      try {
         cluster.sendMessage("baseline");

         for(Ignite node : nodes) {
            assertEquals("baseline", received.get(node.name()).poll(10, TimeUnit.SECONDS),
                         node.name() + " did not get the baseline message");
         }
      }
      finally {
         closeSender(cluster);
      }

      deadClientId = deadClient.cluster().localNode().id();
      ((TcpDiscoverySpi) deadClient.configuration().getDiscoverySpi()).simulateNodeFailure();
      ((TcpCommunicationSpi) deadClient.configuration().getCommunicationSpi()).simulateNodeFailure();
      received.get(deadClient.name()).clear();
   }

   @AfterAll
   void stopCluster() {
      List<Ignite> reversed = new ArrayList<>(nodes);
      Collections.reverse(reversed);

      for(Ignite node : reversed) {
         try {
            node.close();
         }
         catch(Throwable ignore) {
         }
      }
   }

   @Test
   void serverSenderDoesNotFailOrStallOnDeadClientAndLiveClientStillReceives() throws Exception {
      IgniteCluster cluster = newBareCluster(serverA);

      try {
         assertNotNull(serverA.cluster().node(deadClientId),
                       "the dead client must still be in the topology for this test");

         long elapsed = timedSend(cluster, "from-server-1");
         assertTrue(elapsed < SEND_LIMIT_MILLIS,
                    "the broadcast blocked for " + elapsed + " ms on the dead client");

         // the second send is queued behind the stalled send to the dead client, if anything is
         elapsed = timedSend(cluster, "from-server-2");
         assertTrue(elapsed < SEND_LIMIT_MILLIS,
                    "the second broadcast blocked for " + elapsed + " ms on the dead client");

         assertReceivedOnce(serverA, "from-server-1", "from-server-2");
         assertReceivedOnce(serverB, "from-server-1", "from-server-2");
         assertReceivedOnce(liveClient, "from-server-1", "from-server-2");
         assertNotNull(serverA.cluster().node(deadClientId),
                       "the dead client left the topology before the assertions completed");
      }
      finally {
         closeSender(cluster);
      }
   }

   @Test
   void clientSenderDoesNotFailOrStallOnDeadClientAndLocalNodeReceivesOnce() throws Exception {
      IgniteCluster cluster = newBareCluster(liveClient);

      try {
         assertNotNull(liveClient.cluster().node(deadClientId),
                       "the dead client must still be in the topology for this test");

         long elapsed = timedSend(cluster, "from-client");
         assertTrue(elapsed < SEND_LIMIT_MILLIS,
                    "the broadcast blocked for " + elapsed + " ms on the dead client");

         assertReceivedOnce(serverA, "from-client");
         assertReceivedOnce(serverB, "from-client");
         assertReceivedOnce(liveClient, "from-client");
      }
      finally {
         closeSender(cluster);
      }
   }

   private static long timedSend(IgniteCluster cluster, String message) {
      long start = System.nanoTime();
      assertDoesNotThrow(() -> cluster.sendMessage(message));
      return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
   }

   /**
    * Asserts that the node gets the messages in order, well within the failure detection timeout,
    * and no copy of any of them.
    */
   private void assertReceivedOnce(Ignite node, String... messages) throws InterruptedException {
      BlockingQueue<Object> queue = received.get(node.name());

      for(String message : messages) {
         assertEquals(message, queue.poll(RECEIVE_LIMIT_MILLIS, TimeUnit.MILLISECONDS),
                      node.name() + " did not get " + message + " in time");
      }

      Object extra = queue.poll(300, TimeUnit.MILLISECONDS);
      assertNull(extra, node.name() + " got an extra message: " + extra);
   }

   private IgniteCluster newBareCluster(Ignite ignite) throws Exception {
      IgniteCluster cluster = mock(IgniteCluster.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      setField(cluster, "ignite", ignite);
      setField(cluster, "clientMessageSender",
               new ClientMessageSender(ignite, topic, CLIENT_FAILURE_DETECTION_TIMEOUT));
      return cluster;
   }

   /**
    * Closes the cluster's client sender and waits for its threads, one of which may be blocked
    * on the dead client, so that none outlives the test.
    */
   private static void closeSender(IgniteCluster cluster) throws Exception {
      Field field = IgniteCluster.class.getDeclaredField("clientMessageSender");
      field.setAccessible(true);
      ((ClientMessageSender) field.get(cluster)).close();
      long deadline = System.currentTimeMillis() + 10_000;

      while(senderThreadsAlive()) {
         assertTrue(System.currentTimeMillis() < deadline, "client sender threads did not stop");
         Thread.sleep(50);
      }
   }

   private static boolean senderThreadsAlive() {
      return Thread.getAllStackTraces().keySet().stream()
         .anyMatch(t -> t.isAlive() && t.getName().startsWith("IgniteClientMessages-"));
   }

   private static void setField(Object target, String name, Object value) throws Exception {
      Field field = IgniteCluster.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
   }

   private static String messageTopic() throws Exception {
      Field field = IgniteCluster.class.getDeclaredField("MESSAGE_TOPIC");
      field.setAccessible(true);
      return (String) field.get(null);
   }

   private Ignite startNode(String name, int index, boolean client) {
      IgniteConfiguration config = new IgniteConfiguration();
      config.setIgniteInstanceName("bug78364-" + name);
      config.setConsistentId("bug78364-" + name);
      config.setWorkDirectory(workDir.resolve(name).toString());
      config.setFailureDetectionTimeout(FAILURE_DETECTION_TIMEOUT);
      // keeps the dead client in the topology for the whole test, as discovery does for up to
      // 30 s in production
      config.setClientFailureDetectionTimeout(CLIENT_FAILURE_DETECTION_TIMEOUT);
      config.setMetricsLogFrequency(0);
      config.setPeerClassLoadingEnabled(true);
      config.setClientMode(client);
      config.setFailureHandler(new NoOpFailureHandler());
      IgniteUtils.configBinaryTypes(config);

      TcpDiscoverySpi discovery = new TcpDiscoverySpi();
      discovery.setLocalAddress("127.0.0.1");
      discovery.setLocalPort(PORTS[index]);
      discovery.setLocalPortRange(0);
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(List.of("127.0.0.1:" + PORTS[0], "127.0.0.1:" + PORTS[1]));
      discovery.setIpFinder(ipFinder);
      config.setDiscoverySpi(discovery);

      TcpCommunicationSpi communication = new TcpCommunicationSpi();
      communication.setLocalAddress("127.0.0.1");
      communication.setLocalPort(PORTS[NODE_COUNT + index]);
      communication.setLocalPortRange(0);
      // as IgniteCluster.getDefaultConfig() sets for a cloud-runner task container
      communication.setForceClientToServerConnections(client);
      config.setCommunicationSpi(communication);

      if(!client) {
         DataStorageConfiguration storage = new DataStorageConfiguration();
         storage.getDefaultDataRegionConfiguration()
            .setInitialSize(64L << 20)
            .setMaxSize(64L << 20);
         config.setDataStorageConfiguration(storage);
      }

      Ignite node = Ignition.start(config);
      nodes.add(node);
      return node;
   }

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

   private Path workDir;
   private Ignite serverA;
   private Ignite serverB;
   private Ignite deadClient;
   private Ignite liveClient;
   private UUID deadClientId;
   private String topic;
   private final List<Ignite> nodes = new ArrayList<>();
   private final Map<String, BlockingQueue<Object>> received = new ConcurrentHashMap<>();

   private static final long FAILURE_DETECTION_TIMEOUT = 3000;
   private static final long CLIENT_FAILURE_DETECTION_TIMEOUT = 60_000;
   // well under FAILURE_DETECTION_TIMEOUT, which is how long a send to the dead client blocks
   private static final long SEND_LIMIT_MILLIS = 1000;
   private static final long RECEIVE_LIMIT_MILLIS = 1500;
   private static final int NODE_COUNT = 4;
   // the discovery ports of the nodes, then their communication ports
   private static final int[] PORTS = freePorts(NODE_COUNT * 2);
}
