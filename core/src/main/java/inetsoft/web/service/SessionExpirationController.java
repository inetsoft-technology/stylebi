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

package inetsoft.web.service;

import inetsoft.sree.RepletRepository;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XPrincipal;
import inetsoft.web.session.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class SessionExpirationController {
   @Autowired
   public SessionExpirationController(SimpMessagingTemplate messagingTemplate,
                                      IgniteSessionRepository sessionRepository,
                                      Cluster cluster)
   {
      this.messagingTemplate = messagingTemplate;
      this.sessionRepository = sessionRepository;
      this.cluster = cluster;
   }

   @PostConstruct
   public void addSessionListener() {
      cluster.addMessageListener(listener);
   }

   @SubscribeMapping(TOPIC)
   public void subscribeToTopic(StompHeaderAccessor stompHeaderAccessor) throws Exception {
      String httpSessionId =
         stompHeaderAccessor.getSessionAttributes().get("HTTP.SESSION.ID").toString();
      httpSessionIdMap.put(stompHeaderAccessor.getSessionId(), httpSessionId);
   }

   @EventListener(SessionDisconnectEvent.class)
   public void onSessionDisconnect(SessionDisconnectEvent event) {
      httpSessionIdMap.remove(event.getSessionId());
   }

   private void messageReceived(MessageEvent event) {
      Object message = event.getMessage();

      if(message instanceof SessionExpiringSoonEvent expiringSoonEvent) {
         onSessionExpiringSoonEvent(expiringSoonEvent);
      }
   }

   private void onSessionExpiringSoonEvent(SessionExpiringSoonEvent event) {
      Principal principal = event.getPrincipalCookie();

      // a guest is not asked to stay logged in, the page is reloaded with a new guest
      // session when the session expires (see SessionConnectionService)
      boolean guest = principal != null && isGuest(principal);

      if(principal == null || (guest && !event.isNodeProtection())) {
         return;
      }

      SessionExpirationModel model = SessionExpirationModel.builder()
         .remainingTime(event.getRemainingTime())
         .expiringSoon(event.isExpiringSoon())
         .nodeProtection(event.isNodeProtection())
         .guest(guest)
         .build();

      // Bug #80406: send to the windows of the expiring HTTP session only. The user destination
      // is shared by every window of the principal (and by all anonymous windows when security
      // is disabled), so a warning for an idle session would reappear in the active window
      // after "stay logged in" refreshed only its own session.
      for(Map.Entry<String, String> entry : httpSessionIdMap.entrySet()) {
         if(Objects.equals(entry.getValue(), event.getSessionId())) {
            sendToWindow(entry.getKey(), model);
         }
      }
   }

   private void sendToWindow(String stompSessionId, SessionExpirationModel model) {
      SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
      headerAccessor.setSessionId(stompSessionId);
      headerAccessor.setLeaveMutable(true);
      messagingTemplate.convertAndSendToUser(
         stompSessionId, TOPIC, model, headerAccessor.getMessageHeaders());
   }

   private boolean isGuest(Principal principal) {
      IdentityID identity = IdentityID.getIdentityIDFromKey(principal.getName());
      return identity != null && XPrincipal.ANONYMOUS.equals(identity.getName()) &&
         SecurityEngine.getSecurity().isSecurityEnabled();
   }

   @MessageMapping("/session/refresh")
   public void sessionRefresh(StompHeaderAccessor stompHeaderAccessor) {
      String httpSessionId = httpSessionIdMap.get(stompHeaderAccessor.getSessionId());

      if(httpSessionId != null) {
         IgniteSessionRepository.IgniteSession session = sessionRepository.findById(httpSessionId);

         if(session == null) {
            return;
         }

         session.setLastAccessedTime(Instant.now());

         SessionExpiringSoonEvent event =
            new SessionExpiringSoonEvent(this, session, 0, false, false);
         onSessionExpiringSoonEvent(event);
      }
   }

   private final Cluster cluster;
   private final MessageListener listener = this::messageReceived;
   private final Map<String, String> httpSessionIdMap = new ConcurrentHashMap<>(); // key = simp session id, value = http session id
   private final SimpMessagingTemplate messagingTemplate;
   private final IgniteSessionRepository sessionRepository;
   private static final String TOPIC = "/session-expiration";
}
