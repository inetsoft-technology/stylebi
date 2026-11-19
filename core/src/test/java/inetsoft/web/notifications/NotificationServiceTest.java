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

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.internal.cluster.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.DestinationUserNameProvider;

import java.io.*;
import java.security.Principal;
import java.util.*;
import java.util.stream.Stream;

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

   // ---- destination resolution ----

   @Test
   void principalWithNullNameIsNotSentAndDoesNotThrow() {
      assertNothingSent(() -> null);
   }

   @Test
   @DisplayName("pinned current behavior: a provider returning null drops the notification, "
      + "the principal name is not used as a fallback")
   void providerWithNullDestinationDoesNotFallBackToName() {
      assertNothingSent(new ProviderPrincipal("alice", null));
   }

   @Test
   void providerDestinationTakesPrecedenceOverPrincipalName() {
      nodeA.service.sendNotificationToUser(
         "message", new ProviderPrincipal("alice", "alice[s1]@h"));

      verify(nodeA.template, times(1))
         .convertAndSendToUser(eq("alice[s1]@h"), eq("/notifications"), any(Object.class));
      verify(nodeB.template, times(1))
         .convertAndSendToUser(eq("alice[s1]@h"), eq("/notifications"), any(Object.class));
      verify(nodeA.template, never())
         .convertAndSendToUser(eq("alice"), anyString(), any(Object.class));
      assertEquals(1, bus.sent.size());
      assertEquals("alice[s1]@h", ((UserNotificationMessage) bus.sent.get(0)).getDestination());
   }

   @Test
   void userNamesDifferingOnlyByCaseAreDistinctDestinations() {
      nodeA.service.sendNotificationToUser("one", () -> "Alice");
      nodeA.service.sendNotificationToUser("two", () -> "alice");

      for(Node node : List.of(nodeA, nodeB)) {
         verify(node.template, times(1))
            .convertAndSendToUser(eq("Alice"), eq("/notifications"), any(Object.class));
         verify(node.template, times(1))
            .convertAndSendToUser(eq("alice"), eq("/notifications"), any(Object.class));
         verify(node.template, times(2))
            .convertAndSendToUser(anyString(), anyString(), any(Object.class));
      }
   }

   @Test
   void slashInUserNameIsEncodedInTheChannelDestination() {
      List<Message<?>> messages = new ArrayList<>();
      MessageChannel channel = (message, timeout) -> messages.add(message);
      NotificationService service =
         new NotificationService(new SimpMessagingTemplate(channel), mock(Cluster.class));
      NotificationMessage notification = NotificationMessage.builder().message("hi").build();

      service.sendNotificationToUser("hi", () -> "dom/alice");

      assertEquals(1, messages.size());
      assertEquals("/user/dom%2Falice/notifications",
                   SimpMessageHeaderAccessor.getDestination(messages.get(0).getHeaders()));
      assertEquals(notification, messages.get(0).getPayload());
   }

   @Test
   void encodedSlashInUserNameDoesNotThrowAndStillReachesTheCluster() throws Exception {
      MessageChannel channel = (message, timeout) -> true;
      Cluster cluster = mock(Cluster.class);
      NotificationService service =
         new NotificationService(new SimpMessagingTemplate(channel), cluster);

      assertDoesNotThrow(() -> service.sendNotificationToUser("hi", () -> "a%2Fb"));

      ArgumentCaptor<Serializable> sent = ArgumentCaptor.forClass(Serializable.class);
      verify(cluster, times(1)).sendMessage(sent.capture());
      assertEquals("a%2Fb", ((UserNotificationMessage) sent.getValue()).getDestination());
   }

   // ---- failure isolation ----

   @Test
   void failureOnOneRemoteNodeDoesNotStopOtherNodesOrThrow() {
      Node nodeC = new Node("C");
      doThrow(new IllegalStateException("node B down")).when(nodeB.template)
         .convertAndSendToUser(anyString(), anyString(), any(Object.class));

      assertDoesNotThrow(() -> nodeA.service.sendNotificationToUser("message", USER));

      verify(nodeC.template, times(1))
         .convertAndSendToUser(eq(DESTINATION), eq("/notifications"), any(Object.class));
      nodeC.service.removeListener();
   }

   @Test
   void localDeliveryFailureDoesNotThrowAndStillForwardsToTheCluster() {
      doThrow(new IllegalStateException("local down")).when(nodeA.template)
         .convertAndSendToUser(anyString(), anyString(), any(Object.class));

      assertDoesNotThrow(() -> nodeA.service.sendNotificationToUser("message", USER));

      assertEquals(1, bus.sent.size());
      verify(nodeB.template, times(1))
         .convertAndSendToUser(eq(DESTINATION), eq("/notifications"), any(Object.class));
   }

   @Test
   void removeListenerFailureIsSwallowed() {
      Cluster cluster = mock(Cluster.class);
      doThrow(new IllegalStateException("shutting down")).when(cluster)
         .removeMessageListener(any());
      NotificationService service =
         new NotificationService(mock(SimpMessagingTemplate.class), cluster);

      assertDoesNotThrow(service::removeListener);
   }

   @Test
   void broadcastClusterFailureReachesTheCaller() throws Exception {
      Cluster cluster = mock(Cluster.class);
      Exception boom = new Exception("boom");
      doThrow(boom).when(cluster).sendMessage(any());
      NotificationService service =
         new NotificationService(mock(SimpMessagingTemplate.class), cluster);

      Exception thrown = assertThrows(Exception.class, () -> service.sendNotification("m"));

      assertSame(boom, thrown);
   }

   // ---- message content ----

   @Test
   void emptyBroadcastMessageIsDeliveredToEveryNode() throws Exception {
      nodeA.service.sendNotification("");

      for(Node node : List.of(nodeA, nodeB)) {
         assertEquals("", capturedBroadcastText(node));
      }
   }

   @ParameterizedTest(name = "text variant {index}")
   @MethodSource("texts")
   void userNotificationTextArrivesVerbatimOnTheRemoteNode(String text) {
      nodeA.service.sendNotificationToUser(text, USER);

      ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
      verify(nodeB.template, times(1))
         .convertAndSendToUser(eq(DESTINATION), eq("/notifications"), payload.capture());
      assertEquals(text, ((NotificationMessage) payload.getValue()).message());
   }

   @ParameterizedTest(name = "text variant {index}")
   @MethodSource("texts")
   void broadcastTextArrivesVerbatimOnEveryNode(String text) throws Exception {
      nodeA.service.sendNotification(text);

      for(Node node : List.of(nodeA, nodeB)) {
         assertEquals(text, capturedBroadcastText(node));
      }
   }

   @ParameterizedTest(name = "text variant {index}")
   @MethodSource("texts")
   void notificationMessageSurvivesJavaSerialization(String text) {
      NotificationMessage original = NotificationMessage.builder().message(text).build();

      NotificationMessage copy = (NotificationMessage) Bus.roundTrip(original);

      assertEquals(original, copy);
      assertEquals(text, copy.message());
   }

   @ParameterizedTest(name = "text variant {index}")
   @MethodSource("texts")
   void userNotificationMessageSurvivesJavaSerialization(String text) {
      NotificationMessage notification = NotificationMessage.builder().message(text).build();
      UserNotificationMessage original = new UserNotificationMessage("alice[s1]@h", notification);

      UserNotificationMessage copy = (UserNotificationMessage) Bus.roundTrip(original);

      assertEquals("alice[s1]@h", copy.getDestination());
      assertEquals(notification, copy.getNotification());
   }

   @ParameterizedTest(name = "text variant {index}")
   @MethodSource("texts")
   void notificationMessageSurvivesJsonRoundTrip(String text) throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      NotificationMessage original = NotificationMessage.builder().message(text).build();

      NotificationMessage copy =
         mapper.readValue(mapper.writeValueAsString(original), NotificationMessage.class);

      assertEquals(original, copy);
   }

   @Test
   void javaAndJsonRoundTripsGiveTheSameMessage() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      NotificationMessage original = NotificationMessage.builder().message(TRICKY).build();

      NotificationMessage viaJava = (NotificationMessage) Bus.roundTrip(original);
      NotificationMessage viaJson =
         mapper.readValue(mapper.writeValueAsString(original), NotificationMessage.class);

      assertEquals(viaJava, viaJson);
   }

   @Test
   void notificationMessageJsonHasExactlyTheMessageField() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      NotificationMessage message = NotificationMessage.builder().message("hi").build();

      JsonNode json = mapper.readTree(mapper.writeValueAsString(message));

      assertTrue(json.isObject());
      assertEquals(1, json.size());
      assertEquals("hi", json.get("message").asText());
   }

   @Test
   void jsonWithoutMessageIsRejectedBecauseTheMessageIsRequired() {
      JsonMappingException e = assertThrows(
         JsonMappingException.class,
         () -> new ObjectMapper().readValue("{}", NotificationMessage.class));

      assertInstanceOf(IllegalStateException.class, e.getCause());
   }

   @Test
   void jsonWithNullMessageIsRejectedLikeAMissingMessage() {
      JsonMappingException e = assertThrows(
         JsonMappingException.class,
         () -> new ObjectMapper().readValue("{\"message\":null}", NotificationMessage.class));

      assertInstanceOf(IllegalStateException.class, e.getCause());
   }

   @Test
   void builderRejectsNullAndMissingMessage() {
      NullPointerException npe = assertThrows(
         NullPointerException.class, () -> NotificationMessage.builder().message(null));
      assertEquals("message", npe.getMessage());

      IllegalStateException ise = assertThrows(
         IllegalStateException.class, () -> NotificationMessage.builder().build());
      assertTrue(ise.getMessage().contains("message"), ise.getMessage());
   }

   @Test
   void broadcastOfNullMessageIsRejectedAndNothingIsSentToTheCluster() {
      NullPointerException e = assertThrows(
         NullPointerException.class, () -> nodeA.service.sendNotification(null));

      assertEquals("message", e.getMessage());
      assertTrue(bus.sent.isEmpty(), "nothing must be sent to the cluster: " + bus.sent);
   }

   @Test
   void notificationMessagesAreEqualByText() {
      NotificationMessage a = NotificationMessage.builder().message("a").build();
      NotificationMessage same = NotificationMessage.builder().message("a").build();

      assertEquals(a, same);
      assertEquals(a.hashCode(), same.hashCode());
      assertNotEquals(a, NotificationMessage.builder().message("A").build());
      assertNotEquals(a, NotificationMessage.builder().message("a ").build());
   }

   // ---- UserNotificationMessage ----

   @Test
   void userNotificationMessageReturnsItsConstructorArguments() {
      NotificationMessage notification = NotificationMessage.builder().message("m").build();

      UserNotificationMessage message = new UserNotificationMessage("d", notification);

      assertEquals("d", message.getDestination());
      assertSame(notification, message.getNotification());
   }

   @Test
   void userNotificationMessageToStringToleratesNulls() {
      String text = assertDoesNotThrow(() -> new UserNotificationMessage(null, null).toString());

      assertTrue(text.contains("null"), text);
   }

   @Test
   @DisplayName("pinned current behavior: UserNotificationMessage.toString text")
   void userNotificationMessageToStringText() {
      NotificationMessage notification = NotificationMessage.builder().message("m").build();

      assertEquals("UserNotificationMessage{destination='d', "
                      + "notification=NotificationMessage{message=m}}",
                   new UserNotificationMessage("d", notification).toString());
   }

   // ---- messageReceived with foreign payloads, node symmetry ----

   @ParameterizedTest(name = "payload {0}, local {1}")
   @MethodSource("foreignPayloads")
   void unknownClusterPayloadIsIgnored(Object payload, boolean local) {
      SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
      Cluster cluster = mock(Cluster.class);
      NotificationService service = new NotificationService(template, cluster);

      assertDoesNotThrow(
         () -> service.messageReceived(new MessageEvent(this, "X", local, payload)));

      verifyNoInteractions(template, cluster);
   }

   @Test
   void senderAndReceiverDeliverTheSameDestinationAndPayload() {
      nodeA.service.sendNotificationToUser("message", USER);

      ArgumentCaptor<String> destA = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<Object> payloadA = ArgumentCaptor.forClass(Object.class);
      ArgumentCaptor<String> destB = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<Object> payloadB = ArgumentCaptor.forClass(Object.class);
      verify(nodeA.template).convertAndSendToUser(
         destA.capture(), eq("/notifications"), payloadA.capture());
      verify(nodeB.template).convertAndSendToUser(
         destB.capture(), eq("/notifications"), payloadB.capture());

      assertEquals(destA.getValue(), destB.getValue());
      assertEquals(payloadA.getValue(), payloadB.getValue());
   }

   @Test
   void sameMessageSentTwiceIsDeliveredTwiceOnEveryNode() {
      nodeA.service.sendNotificationToUser("again", USER);
      nodeA.service.sendNotificationToUser("again", USER);

      NotificationMessage expected = NotificationMessage.builder().message("again").build();

      for(Node node : List.of(nodeA, nodeB)) {
         verify(node.template, times(2))
            .convertAndSendToUser(eq(DESTINATION), eq("/notifications"), eq(expected));
      }
   }

   // ---- helpers ----

   private void assertNothingSent(Principal principal) {
      assertDoesNotThrow(() -> nodeA.service.sendNotificationToUser("message", principal));

      assertTrue(bus.sent.isEmpty(), "nothing must be sent to the cluster: " + bus.sent);

      for(Node node : List.of(nodeA, nodeB)) {
         verifyNoInteractions(node.template);
      }
   }

   private static String capturedBroadcastText(Node node) {
      ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
      verify(node.template, times(1)).convertAndSend(eq("/notifications"), payload.capture());
      return ((NotificationMessage) payload.getValue()).message();
   }

   private static Stream<String> texts() {
      return Stream.of("", "héllo 世界 😀", TRICKY, "x".repeat(100_000));
   }

   private static Stream<Arguments> foreignPayloads() {
      return Stream.of("text", 42, null).flatMap(
         payload -> Stream.of(true, false).map(local -> Arguments.of(payload, local)));
   }

   private static final String TRICKY = "say \"hi\"\nline2\ttab \\ back 'q'";

   private record ProviderPrincipal(String name, String destination)
      implements Principal, DestinationUserNameProvider
   {
      @Override
      public String getName() {
         return name;
      }

      @Override
      public String getDestinationUserName() {
         return destination;
      }
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
