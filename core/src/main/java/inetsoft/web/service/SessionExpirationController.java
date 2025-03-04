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
import inetsoft.sree.internal.SUtil;
import inetsoft.web.session.IgniteSessionRepository;
import inetsoft.web.session.SessionExpiringSoonEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.*;

@RestController
public class SessionExpirationController implements ApplicationListener<SessionExpiringSoonEvent> {
   @Autowired
   public SessionExpirationController(SimpMessagingTemplate messagingTemplate,
                                      IgniteSessionRepository sessionRepository)
   {
      this.messagingTemplate = messagingTemplate;
      this.sessionRepository = sessionRepository;
   }

   @SubscribeMapping(TOPIC)
   public void subscribeToTopic(StompHeaderAccessor stompHeaderAccessor) throws Exception {
      String httpSessionId =
         stompHeaderAccessor.getSessionAttributes().get("HTTP.SESSION.ID").toString();
      httpSessionIdMap.put(stompHeaderAccessor.getSessionId(), httpSessionId);

      synchronized(subscribedSessionIds) {
         subscribedSessionIds.add(httpSessionId);
      }
   }

   @EventListener
   public void onSessionDisconnect(SessionDisconnectEvent event) {
      synchronized(subscribedSessionIds) {
         subscribedSessionIds.remove(event.getSessionId());
      }

      httpSessionIdMap.remove(event.getSessionId());
   }

   @Override
   public void onApplicationEvent(SessionExpiringSoonEvent event) {
      boolean subscribed = false;

      synchronized(subscribedSessionIds) {
         subscribed = subscribedSessionIds.contains(event.getSession().getId());
      }

      if(subscribed) {
         Principal principal = event.getSession().getAttribute(RepletRepository.PRINCIPAL_COOKIE);

         if(principal != null) {
            SessionExpirationModel model = SessionExpirationModel.builder()
               .remainingTime(event.getRemainingTime())
               .expiringSoon(event.isExpiringSoon())
               .nodeProtection(event.isNodeProtection())
               .build();
            messagingTemplate
               .convertAndSendToUser(SUtil.getUserDestination(principal), TOPIC, model);
         }
      }
   }

   @MessageMapping("/session/refresh")
   public void sessionRefresh(StompHeaderAccessor stompHeaderAccessor) {
      String httpSessionId = httpSessionIdMap.get(stompHeaderAccessor.getSessionId());

      if(httpSessionId != null) {
         IgniteSessionRepository.IgniteSession session = sessionRepository.findById(httpSessionId);
         session.setLastAccessedTime(Instant.now());

         SessionExpiringSoonEvent event =
            new SessionExpiringSoonEvent(this, session, 0, false, false);
         onApplicationEvent(event);
      }
   }

   private final Map<String, String> httpSessionIdMap = new HashMap<>(); // key = simp session id, value = http session id
   private final SimpMessagingTemplate messagingTemplate;
   private final IgniteSessionRepository sessionRepository;
   private final Set<String> subscribedSessionIds = new HashSet<>();
   private static final String TOPIC = "/session-expiration";
}
