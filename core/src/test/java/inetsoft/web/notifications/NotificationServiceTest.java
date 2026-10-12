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
package inetsoft.web.notifications;

import inetsoft.sree.internal.cluster.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.io.*;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78248: in a cluster, a notification sent to one user must reach the user's web socket
 * on whichever node it is connected to, not only on the node that handled the request. Each
 * "node" is a real {@link NotificationService} with its own messaging template. The nodes are
 * connected by a message bus that behaves like Ignite's {@code sendOrdered}: every node, the
 * sender included, receives a serialized copy of the message, and the sender's copy is marked
 * local.
 */
@Tag("core")
class NotificationServiceTest {
   @BeforeEach
   void setUp() {
      nodeA = new Node("A");
      nodeB = new Node("B");
   }

   @AfterEach
   void tearDown() {
      nodeA.service.removeListener();
      nodeB.service.removeListener();
   }

   @Test
   void userNotificationReachesEveryNodeOnceAndIsNeverBroadcast() {
      // the request runs on node A, the user's web socket may be on node B
      nodeA.service.sendNotificationToUser("Save the data source first", USER);

      ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
      verify(nodeB.template, times(1))
         .convertAndSendToUser(eq(DESTINATION), eq("/notifications"), payload.capture());
      assertEquals("Save the data source first",
                   ((NotificationMessage) payload.getValue()).message());

      // the origin delivers once, its own cluster copy is not delivered again
      verify(nodeA.template, times(1))
         .convertAndSendToUser(eq(DESTINATION), eq("/notifications"), any(Object.class));

      // a per-user notification must never reach every connected user
      for(Node node : List.of(nodeA, nodeB)) {
         verify(node.template, never()).convertAndSend(anyString(), any(Object.class));
         verify(node.template, never())
            .convertAndSendToUser(argThat(d -> !DESTINATION.equals(d)), anyString(),
                                  any(Object.class));
      }

      assertEquals(1, bus.sent.size());
      assertInstanceOf(UserNotificationMessage.class, bus.sent.get(0));
   }

   @Test
   void broadcastNotificationIsStillDeliveredToAllUsersOnEveryNode() throws Exception {
      nodeA.service.sendNotification("maintenance");

      for(Node node : List.of(nodeA, nodeB)) {
         verify(node.template, times(1)).convertAndSend(eq("/notifications"), any(Object.class));
         verify(node.template, never())
            .convertAndSendToUser(anyString(), anyString(), any(Object.class));
      }
   }

   @Test
   void nullDestinationIsNotSentAndDoesNotThrow() {
      assertDoesNotThrow(() -> nodeA.service.sendNotificationToUser("message", null));

      assertTrue(bus.sent.isEmpty(), "nothing must be sent to the cluster: " + bus.sent);

      for(Node node : List.of(nodeA, nodeB)) {
         verifyNoInteractions(node.template);
      }
   }

   @Test
   void clusterFailureDoesNotThrowAndStillDeliversLocally() throws Exception {
      Cluster failing = mock(Cluster.class);
      doThrow(new IllegalStateException("cluster down")).when(failing).sendMessage(any());
      SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
      NotificationService service = new NotificationService(template, failing);

      assertDoesNotThrow(() -> service.sendNotificationToUser("message", USER));

      verify(template, times(1))
         .convertAndSendToUser(eq(DESTINATION), eq("/notifications"), any(Object.class));
   }

   private static final String DESTINATION = "alice[42]@10.0.0.5";
   private static final Principal USER = () -> DESTINATION;

   private final Bus bus = new Bus();
   private Node nodeA;
   private Node nodeB;

   private final class Node {
      Node(String name) {
         this.name = name;
         Cluster cluster = mock(Cluster.class);

         try {
            doAnswer(invocation -> {
               bus.send(name, invocation.getArgument(0));
               return null;
            }).when(cluster).sendMessage(any());
         }
         catch(Exception e) {
            throw new AssertionError(e);
         }

         doAnswer(invocation -> {
            bus.listeners.put(name, invocation.getArgument(0));
            return null;
         }).when(cluster).addMessageListener(any());
         doAnswer(invocation -> {
            bus.listeners.remove(name);
            return null;
         }).when(cluster).removeMessageListener(any());

         service = new NotificationService(template, cluster);
         service.addListener();
      }

      final String name;
      final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
      final NotificationService service;
   }

   /**
    * Delivers a message to every node, the sender included, like Ignite's {@code sendOrdered}.
    * The message is serialized and deserialized for each node, as it is when it crosses nodes.
    */
   private static final class Bus {
      void send(String sender, Serializable message) {
         sent.add(message);

         for(Map.Entry<String, MessageListener> e : listeners.entrySet()) {
            boolean local = e.getKey().equals(sender);
            e.getValue().messageReceived(
               new MessageEvent(this, sender, local, roundTrip(message)));
         }
      }

      private static Object roundTrip(Serializable message) {
         try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();

            try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
               out.writeObject(message);
            }

            try(ObjectInputStream in =
                   new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
            {
               return in.readObject();
            }
         }
         catch(IOException | ClassNotFoundException e) {
            throw new AssertionError("message is not serializable: " + message, e);
         }
      }

      final Map<String, MessageListener> listeners = new LinkedHashMap<>();
      final List<Serializable> sent = new ArrayList<>();
   }
}
