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
package inetsoft.web.service;

import inetsoft.sree.RepletRepository;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XPrincipal;
import inetsoft.web.session.IgniteSessionRepository;
import inetsoft.web.session.SessionExpiringSoonEvent;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.session.MapSession;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77220: a guest must not get the "session about to expire" dialog, whose timer logs the
 * guest out to the login page. The page is reloaded as a guest when the session expires instead.
 */
@Tag("core")
class SessionExpirationControllerTest {
   @BeforeEach
   void setUp() throws Exception {
      Cluster cluster = mock(Cluster.class);
      messagingTemplate = mock(SimpMessagingTemplate.class);
      SessionExpirationController controller = new SessionExpirationController(
         messagingTemplate, mock(IgniteSessionRepository.class), cluster);
      controller.addSessionListener();

      ArgumentCaptor<MessageListener> captor = ArgumentCaptor.forClass(MessageListener.class);
      verify(cluster).addMessageListener(captor.capture());
      listener = captor.getValue();

      security = mock(SecurityEngine.class);
      securityStatic = mockStatic(SecurityEngine.class);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(security);
      when(security.isSecurityEnabled()).thenReturn(true);

      StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("HTTP.SESSION.ID", SESSION_ID);
      accessor.setSessionId("ws-1");
      accessor.setSessionAttributes(attributes);
      controller.subscribeToTopic(accessor);
      this.controller = controller;
   }

   @AfterEach
   void tearDown() {
      securityStatic.close();
   }

   @Test
   void userExpiringSoonIsSent() {
      expiringSoon("admin", "host-org", false);
      verifySent(1);
   }

   @Test
   void guestExpiringSoonIsNotSent() {
      expiringSoon(XPrincipal.ANONYMOUS, "host-org", false);
      verifySent(0);
   }

   @Test
   void guestInOtherOrgExpiringSoonIsNotSent() {
      expiringSoon(XPrincipal.ANONYMOUS, "org1", false);
      verifySent(0);
   }

   @Test
   void guestNodeProtectionIsSent() {
      expiringSoon(XPrincipal.ANONYMOUS, "host-org", true);
      verifySent(1);
   }

   // Bug #77340: the client must know the node protection warning is for a guest, so the
   // timer end does not log the guest out to the login page
   @Test
   void guestNodeProtectionIsMarkedAsGuest() {
      expiringSoon(XPrincipal.ANONYMOUS, "host-org", true);
      Assertions.assertTrue(sentModel().guest());
   }

   @Test
   void userNodeProtectionIsNotMarkedAsGuest() {
      expiringSoon("admin", "host-org", true);
      Assertions.assertFalse(sentModel().guest());
   }

   @Test
   void userExpiringSoonIsNotMarkedAsGuest() {
      expiringSoon("admin", "host-org", false);
      Assertions.assertFalse(sentModel().guest());
   }

   @Test
   void securityDisabledExpiringSoonIsSent() {
      when(security.isSecurityEnabled()).thenReturn(false);
      expiringSoon(XPrincipal.ANONYMOUS, "host-org", false);
      verifySent(1);
   }

   // Bug #80406: a warning for one HTTP session must not reach another window of the same
   // principal (all anonymous windows share one principal when security is disabled), or "stay
   // logged in" in the active window never silences the idle window's warning
   @Test
   void warningIsSentOnlyToTheExpiringHttpSession() throws Exception {
      subscribe("ws-2", "http-2");
      expiringSoon("admin", "host-org", false, SESSION_ID);
      verify(messagingTemplate).convertAndSendToUser(
         eq("ws-1"), eq("/session-expiration"), any(Object.class), any(MessageHeaders.class));
      verify(messagingTemplate, never()).convertAndSendToUser(
         eq("ws-2"), anyString(), any(Object.class), any(MessageHeaders.class));
   }

   @Test
   void warningIsSentToEveryWindowOfTheHttpSession() throws Exception {
      subscribe("ws-2", SESSION_ID);
      expiringSoon("admin", "host-org", false);
      verifySent(2);
   }

   @Test
   void disconnectedWindowGetsNoWarning() throws Exception {
      controller.onSessionDisconnect(
         new SessionDisconnectEvent(this, mock(Message.class), "ws-1", CloseStatus.NORMAL));
      expiringSoon("admin", "host-org", false);
      verifySent(0);
   }

   // the headers the controller sends must resolve to the window's own subscription
   @Test
   void sentMessageResolvesToTheWindowSubscription() {
      expiringSoon("admin", "host-org", false);
      ArgumentCaptor<MessageHeaders> captor = ArgumentCaptor.forClass(MessageHeaders.class);
      verify(messagingTemplate).convertAndSendToUser(
         eq("ws-1"), eq("/session-expiration"), any(Object.class), captor.capture());

      DefaultUserDestinationResolver resolver =
         new DefaultUserDestinationResolver(mock(SimpUserRegistry.class));
      SimpMessageHeaderAccessor sent = SimpMessageHeaderAccessor.create();
      sent.copyHeaders(captor.getValue());
      sent.setDestination("/user/ws-1/session-expiration");
      UserDestinationResult sendResult = resolver.resolveDestination(
         MessageBuilder.createMessage(new byte[0], sent.getMessageHeaders()));

      SimpMessageHeaderAccessor sub = SimpMessageHeaderAccessor.create(SimpMessageType.SUBSCRIBE);
      sub.setSessionId("ws-1");
      sub.setSubscriptionId("0");
      sub.setDestination("/user/session-expiration");
      UserDestinationResult subResult = resolver.resolveDestination(
         MessageBuilder.createMessage(new byte[0], sub.getMessageHeaders()));

      Assertions.assertNotNull(sendResult);
      Assertions.assertNotNull(subResult);
      Assertions.assertEquals(subResult.getTargetDestinations(),
                              sendResult.getTargetDestinations());
   }

   private void subscribe(String stompId, String httpId) throws Exception {
      StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("HTTP.SESSION.ID", httpId);
      accessor.setSessionId(stompId);
      accessor.setSessionAttributes(attributes);
      controller.subscribeToTopic(accessor);
   }

   private void expiringSoon(String name, String orgID, boolean nodeProtection) {
      expiringSoon(name, orgID, nodeProtection, SESSION_ID);
   }

   private void expiringSoon(String name, String orgID, boolean nodeProtection, String httpId) {
      String key = new IdentityID(name, orgID).convertToKey();
      Principal principal = () -> key;
      MapSession session = new MapSession(httpId);
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      SessionExpiringSoonEvent event =
         new SessionExpiringSoonEvent("test", session, 30000L, true, nodeProtection);
      listener.messageReceived(new MessageEvent(this, "node", true, event));
   }

   private void verifySent(int count) {
      verify(messagingTemplate, times(count))
         .convertAndSendToUser(anyString(), eq("/session-expiration"), any(Object.class),
                             any(MessageHeaders.class));
   }

   private SessionExpirationModel sentModel() {
      ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
      verify(messagingTemplate)
         .convertAndSendToUser(anyString(), eq("/session-expiration"), captor.capture(),
                             any(MessageHeaders.class));
      return (SessionExpirationModel) captor.getValue();
   }

   private SessionExpirationController controller;
   private SimpMessagingTemplate messagingTemplate;
   private MessageListener listener;
   private SecurityEngine security;
   private MockedStatic<SecurityEngine> securityStatic;
   private static final String SESSION_ID = "http-1";
}
