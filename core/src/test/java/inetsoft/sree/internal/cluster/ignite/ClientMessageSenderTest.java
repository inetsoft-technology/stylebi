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
import org.apache.ignite.IgniteMessaging;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.cluster.ClusterNode;
import org.junit.jupiter.api.*;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78364: the per-client queues of {@link ClientMessageSender}, on a mocked Ignite so that a
 * client's send can be blocked or failed deterministically. Covers what the embedded-cluster
 * {@link IgniteClusterBroadcastClientFailureTest} does not: the bounded queue overflows without
 * blocking the caller, a failed client is skipped for the skip window and then resumes, the
 * queue of a client that left the topology is closed even while its thread is stuck in a send,
 * and {@code close()} stops every thread.
 */
@Tag("core")
class ClientMessageSenderTest {
   @BeforeEach
   void setUp() {
      ignite = mock(Ignite.class);
      org.apache.ignite.IgniteCluster cluster = mock(org.apache.ignite.IgniteCluster.class);
      ClusterGroup clients = mock(ClusterGroup.class);
      ClusterGroup remoteClients = mock(ClusterGroup.class);
      when(ignite.cluster()).thenReturn(cluster);
      when(cluster.forClients()).thenReturn(clients);
      when(clients.forRemotes()).thenReturn(remoteClients);
      when(remoteClients.nodes()).thenAnswer(inv -> new ArrayList<>(topology.values()));
      when(cluster.node(any(UUID.class))).thenAnswer(inv -> topology.get(inv.getArgument(0)));
      when(cluster.forNodeId(any(UUID.class))).thenAnswer(inv -> groups.get(inv.getArgument(0)));
      when(ignite.message(any(ClusterGroup.class))).thenAnswer(inv -> messaging.get(inv.getArgument(0)));
   }

   @AfterEach
   void tearDown() throws Exception {
      if(sender != null) {
         sender.close();
      }

      awaitNoSenderThreads();
   }

   @Test
   void fullQueueDropsNewMessagesWithoutBlockingAndKeepsOrder() throws Exception {
      CountDownLatch release = new CountDownLatch(1);
      Client stuck = addClient(release);
      Client live = addClient(null);
      sender = new ClientMessageSender(ignite, TOPIC, 60_000);

      sender.send(0);
      assertTrue(stuck.entered.await(5, TimeUnit.SECONDS), "the first send did not start");

      int total = ClientMessageSender.MAX_QUEUED_MESSAGES + 500;
      long start = System.nanoTime();

      for(int i = 1; i < total; i++) {
         sender.send(i);
      }

      long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      assertTrue(elapsed < 2000, "queuing for a stuck client blocked the caller for " + elapsed + " ms");

      // the live client is not held up by the stuck one and gets every message, in order
      assertEquals(range(0, total), live.awaitReceived(total));

      // the stuck client gets the one in flight plus a full queue, in order, and nothing more
      release.countDown();
      int expected = 1 + ClientMessageSender.MAX_QUEUED_MESSAGES;
      stuck.awaitReceived(expected);
      Thread.sleep(200);
      assertEquals(expected, stuck.received.size(), "messages beyond the queue bound were sent");
      assertEquals(range(0, expected), new ArrayList<>(stuck.received));
   }

   @Test
   void failedClientIsSkippedForTheSkipWindowThenResumes() throws Exception {
      Client flaky = addClient(null);
      Client live = addClient(null);
      flaky.failNext = true;
      sender = new ClientMessageSender(ignite, TOPIC, 500);

      sender.send("fails");
      assertTrue(flaky.entered.await(5, TimeUnit.SECONDS), "the failing send did not start");
      awaitTrue(() -> flaky.calls.get() == 1 && !flaky.failNext, "the failure was not processed");
      Thread.sleep(100); // lets the sender record the failure after the send threw

      sender.send("skipped");
      assertEquals(List.of("fails", "skipped"), live.awaitReceived(2));
      Thread.sleep(200);
      assertEquals(1, flaky.calls.get(), "a message for a failed client was sent in the skip window");

      Thread.sleep(500);
      sender.send("resumed");
      assertEquals(List.of("resumed"), flaky.awaitReceived(1));
      assertEquals(List.of("fails", "skipped", "resumed"), live.awaitReceived(3));
   }

   @Test
   void queueOfClientThatLeftIsClosedEvenWhileItsSendIsStuck() throws Exception {
      Client departed = addClient(new CountDownLatch(1)); // never released
      Client live = addClient(null);
      sender = new ClientMessageSender(ignite, TOPIC, 60_000);

      sender.send("before");
      assertTrue(departed.entered.await(5, TimeUnit.SECONDS), "the stuck send did not start");
      assertTrue(senderThreadAlive(departed.id));

      topology.remove(departed.id);
      sender.send("after");

      awaitTrue(() -> !senderThreadAlive(departed.id),
                "the thread of a client that left the topology is still alive");
      assertEquals(List.of("before", "after"), live.awaitReceived(2));
      assertEquals(0, departed.received.size());
   }

   @Test
   void closeStopsEveryThreadAndLaterSendsAreIgnored() throws Exception {
      Client stuck = addClient(new CountDownLatch(1)); // never released
      Client live = addClient(null);
      sender = new ClientMessageSender(ignite, TOPIC, 60_000);

      sender.send("one");
      assertTrue(stuck.entered.await(5, TimeUnit.SECONDS), "the stuck send did not start");
      assertEquals(List.of("one"), live.awaitReceived(1));

      sender.close();
      awaitNoSenderThreads();

      sender.send("two");
      Thread.sleep(200);
      assertEquals(1, live.received.size(), "a closed sender still sent a message");
      assertFalse(senderThreadAlive(live.id), "a closed sender started a thread");
   }

   private Client addClient(CountDownLatch blockFirst) {
      Client client = new Client(blockFirst);
      ClusterNode node = mock(ClusterNode.class);
      when(node.id()).thenReturn(client.id);
      ClusterGroup group = mock(ClusterGroup.class);
      IgniteMessaging msg = mock(IgniteMessaging.class);
      doAnswer(inv -> {
         client.onSend(inv.getArgument(1));
         return null;
      }).when(msg).sendOrdered(eq(TOPIC), any(), anyLong());
      topology.put(client.id, node);
      groups.put(client.id, group);
      messaging.put(group, msg);
      return client;
   }

   private static List<Object> range(int from, int to) {
      List<Object> list = new ArrayList<>();

      for(int i = from; i < to; i++) {
         list.add(i);
      }

      return list;
   }

   private static void awaitTrue(java.util.function.BooleanSupplier condition, String message)
      throws InterruptedException
   {
      long deadline = System.currentTimeMillis() + 5000;

      while(!condition.getAsBoolean()) {
         assertTrue(System.currentTimeMillis() < deadline, message);
         Thread.sleep(20);
      }
   }

   private static void awaitNoSenderThreads() throws InterruptedException {
      awaitTrue(() -> Thread.getAllStackTraces().keySet().stream()
                   .noneMatch(t -> t.isAlive() && t.getName().startsWith(THREAD_PREFIX)),
                "client sender threads did not stop");
   }

   private static boolean senderThreadAlive(UUID id) {
      return Thread.getAllStackTraces().keySet().stream()
         .anyMatch(t -> t.isAlive() && t.getName().equals(THREAD_PREFIX + id));
   }

   private static final class Client {
      Client(CountDownLatch blockFirst) {
         this.blockFirst = blockFirst;
      }

      void onSend(Serializable message) throws InterruptedException {
         int call = calls.incrementAndGet();
         entered.countDown();

         if(failNext) {
            failNext = false;
            throw new org.apache.ignite.IgniteException("simulated failure to reach " + id);
         }

         if(call == 1 && blockFirst != null) {
            blockFirst.await(); // interrupted by shutdownNow()
         }

         received.add(message);
      }

      List<Object> awaitReceived(int count) throws InterruptedException {
         awaitTrue(() -> received.size() >= count,
                   "client got " + received.size() + " of " + count + " messages");
         return new ArrayList<>(received);
      }

      final UUID id = UUID.randomUUID();
      final CountDownLatch blockFirst;
      final CountDownLatch entered = new CountDownLatch(1);
      final java.util.concurrent.atomic.AtomicInteger calls =
         new java.util.concurrent.atomic.AtomicInteger();
      final List<Object> received = new CopyOnWriteArrayList<>();
      volatile boolean failNext;
   }

   private Ignite ignite;
   private ClientMessageSender sender;
   private final Map<UUID, ClusterNode> topology = new ConcurrentHashMap<>();
   private final Map<UUID, ClusterGroup> groups = new ConcurrentHashMap<>();
   private final Map<ClusterGroup, IgniteMessaging> messaging = new ConcurrentHashMap<>();

   private static final String TOPIC = "test-topic";
   private static final String THREAD_PREFIX = "IgniteClientMessages-";
}
