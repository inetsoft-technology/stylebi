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
package inetsoft.web.messaging;

import inetsoft.sree.RepletRepository;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.AuthenticationService;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XPrincipal;
import inetsoft.web.admin.server.NodeProtectionService;
import inetsoft.web.security.AbstractLogoutFilter;
import inetsoft.web.session.IgniteSessionRepository;
import inetsoft.web.session.SessionDeletedEvent;
import inetsoft.web.session.SessionEvent;
import inetsoft.web.session.SessionExpiredEvent;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.session.MapSession;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77220: the websocket close code sent when an HTTP session ends. The principal name is the
 * IdentityID key ({@code name~;~org}), so a guest must be recognised by the IdentityID name rather
 * than by comparing the whole key with {@link XPrincipal#ANONYMOUS}.
 */
@Tag("core")
class SessionConnectionServiceTest {
   @BeforeEach
   void setUp() {
      cluster = mock(Cluster.class);
      service = new SessionConnectionService(
         mock(IgniteSessionRepository.class), mock(AuthenticationService.class),
         mock(ApplicationEventPublisher.class), mock(NodeProtectionService.class),
         mock(SecurityEngine.class), cluster);
      service.addSessionListener();

      ArgumentCaptor<MessageListener> captor = ArgumentCaptor.forClass(MessageListener.class);
      verify(cluster).addMessageListener(captor.capture());
      listener = captor.getValue();

      security = mock(SecurityEngine.class);
      securityStatic = mockStatic(SecurityEngine.class);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(security);
      when(security.isSecurityEnabled()).thenReturn(true);
   }

   @AfterEach
   void tearDown() {
      securityStatic.close();
   }

   @Test
   void guestIdleTimeoutClosesWithGuestSessionExpired() throws Exception {
      CloseStatus status = closeStatusFor(expired(principal(XPrincipal.ANONYMOUS), false));
      assertEquals(4003, status.getCode());
   }

   @Test
   void guestSweepClosesWithGuestSessionExpired() throws Exception {
      CloseStatus status = closeStatusFor(deleted(principal(XPrincipal.ANONYMOUS), false));
      assertEquals(4003, status.getCode());
   }

   @Test
   void guestInOtherOrgIdleTimeoutClosesWithGuestSessionExpired() throws Exception {
      CloseStatus status = closeStatusFor(expired(principal(XPrincipal.ANONYMOUS, "org1"), false));
      assertEquals(4003, status.getCode());
      status = closeStatusFor(expired(principal(XPrincipal.ANONYMOUS, null), false));
      assertEquals(4003, status.getCode());
   }

   @Test
   void userIdleTimeoutClosesWithSessionTimeout() throws Exception {
      CloseStatus status = closeStatusFor(expired(principal("admin"), false));
      assertEquals(4002, status.getCode());
   }

   @Test
   void guestLogoutClosesWithLoggedOut() throws Exception {
      CloseStatus status = closeStatusFor(deleted(principal(XPrincipal.ANONYMOUS), true));
      assertEquals(4001, status.getCode());
   }

   @Test
   void userLogoutClosesWithLoggedOut() throws Exception {
      CloseStatus status = closeStatusFor(deleted(principal("admin"), true));
      assertEquals(4001, status.getCode());
   }

   @Test
   void securityDisabledClosesNormally() throws Exception {
      when(security.isSecurityEnabled()).thenReturn(false);
      assertEquals(CloseStatus.NORMAL, closeStatusFor(expired(principal("admin"), false)));
      assertEquals(CloseStatus.NORMAL,
                   closeStatusFor(expired(principal(XPrincipal.ANONYMOUS), false)));
   }

   @Test
   void nullPrincipalClosesNormally() throws Exception {
      assertEquals(CloseStatus.NORMAL, closeStatusFor(expired(null, false)));
   }

   private CloseStatus closeStatusFor(SessionEvent event) throws Exception {
      WebSocketSession wsSession = mock(WebSocketSession.class);
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("SPRING.SESSION.ID", event.getSessionId());
      when(wsSession.getId()).thenReturn("ws-" + event.getSessionId());
      when(wsSession.getAttributes()).thenReturn(attributes);
      service.webSocketConnected(wsSession);

      listener.messageReceived(new MessageEvent(this, "node", true, event));

      ArgumentCaptor<CloseStatus> captor = ArgumentCaptor.forClass(CloseStatus.class);
      verify(wsSession).close(captor.capture());
      return captor.getValue();
   }

   private static Principal principal(String name) {
      return principal(name, "host-org");
   }

   private static Principal principal(String name, String orgID) {
      String key = new IdentityID(name, orgID).convertToKey();
      return () -> key;
   }

   private static SessionEvent expired(Principal principal, boolean loggedOut) {
      return new SessionExpiredEvent("test", session(principal, loggedOut));
   }

   private static SessionEvent deleted(Principal principal, boolean loggedOut) {
      return new SessionDeletedEvent("test", session(principal, loggedOut));
   }

   private static MapSession session(Principal principal, boolean loggedOut) {
      MapSession session = new MapSession("http-" + (++sessionCount));

      if(principal != null) {
         session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      }

      if(loggedOut) {
         session.setAttribute(AbstractLogoutFilter.LOGGED_OUT, Boolean.TRUE);
      }

      return session;
   }

   private Cluster cluster;
   private SessionConnectionService service;
   private MessageListener listener;
   private SecurityEngine security;
   private MockedStatic<SecurityEngine> securityStatic;
   private static int sessionCount = 0;
}
