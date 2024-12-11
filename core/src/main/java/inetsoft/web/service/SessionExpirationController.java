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
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.web.MapSession;
import inetsoft.web.MapSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
public class SessionExpirationController {
   @Autowired
   public SessionExpirationController(SimpMessagingTemplate messagingTemplate,
                                      MapSessionRepository mapSessionRepository)
   {
      this.messagingTemplate = messagingTemplate;
      this.mapSessionRepository = mapSessionRepository;
   }

   @SubscribeMapping(TOPIC)
   public void subscribeToTopic(StompHeaderAccessor stompHeaderAccessor) throws Exception {
      String httpSessionId =
         stompHeaderAccessor.getSessionAttributes().get("HTTP.SESSION.ID").toString();
      httpSessionIdMap.put(stompHeaderAccessor.getSessionId(), httpSessionId);
      mapSessionRepository.addSessionExpiringSoonListener(httpSessionId, this.listener);
   }

   @EventListener
   public void onSessionDisconnect(SessionDisconnectEvent event) {
      mapSessionRepository.removeSessionExpiringSoonListener(httpSessionIdMap.get(event.getSessionId()));
      httpSessionIdMap.remove(event.getSessionId());
   }

   private void sessionExpiringSoon(MapSessionRepository.SessionExpiringSoonEvent event) {
      Principal principal = event.getSession().getAttribute(RepletRepository.PRINCIPAL_COOKIE);

      if(principal != null) {
         SessionExpirationModel model = SessionExpirationModel.builder()
            .remainingTime(event.getRemainingTime())
            .build();
         messagingTemplate
            .convertAndSendToUser(SUtil.getUserDestination(principal), TOPIC, model);
      }
   }

   @MessageMapping("/session/refresh")
   public void sessionRefresh(StompHeaderAccessor stompHeaderAccessor) {
      String httpSessionId = httpSessionIdMap.get(stompHeaderAccessor.getSessionId());

      if(httpSessionId != null) {
         MapSession session = mapSessionRepository.findById(httpSessionId);
         session.setLastAccessedTime(Instant.now());
      }
   }

   @GetMapping("/api/session/session-timeout")
   public SessionExpirationModel sessionTimeout() {
      String property = SreeEnv.getProperty("http.session.timeout");
      long timeout = Long.parseLong(property) * 1000; // s to ms
      return SessionExpirationModel.builder()
         .sessionTimeout(timeout)
         .build();
   }

   private final Map<String, String> httpSessionIdMap = new HashMap<>(); // key = simp session id, value = http session id
   private final SimpMessagingTemplate messagingTemplate;
   private final MapSessionRepository mapSessionRepository;
   private final MapSessionRepository.SessionExpiringSoonListener listener = this::sessionExpiringSoon;
   private static final String TOPIC = "/session-expiration";
}
