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
package inetsoft.web.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.web.security.SessionAccessDispatcher;
import inetsoft.web.viewsheet.event.TouchAssetEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.socket.server.SessionRepositoryMessageInterceptor;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;

/**
 * Interceptor that updates the session's last access time for all messages except for no-op touch
 * asset event messages. This ensures that the session remains valid while any websocket activity
 * is taking place, but an idle viewsheet will not artificially extend the life of the session.
 */
public class SessionAccessInterceptor implements ExecutorChannelInterceptor {
   @SuppressWarnings("unchecked")
   public SessionAccessInterceptor(SessionRepository<? extends Session> sessionRepository,
                                   ObjectMapper objectMapper)
   {
      this.sessionRepository = (SessionRepository<Session>) sessionRepository;
      this.objectMapper = objectMapper;
   }

   @Override
   public Message<?> beforeHandle(Message<?> message, MessageChannel channel,
                                  MessageHandler handler)
   {
      SimpMessageType messageType = SimpMessageHeaderAccessor.getMessageType(message.getHeaders());

      if(messageType == SimpMessageType.MESSAGE) {
         MessageAttributes attributes = new MessageAttributes(message);
         Principal principal = attributes.getHeaderAccessor().getUser();

         boolean updateSession = true;
         String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());

         if("/events/composer/touch-asset".equals(destination)) {
            try {
               TouchAssetEvent event =
                  objectMapper.readValue((byte[]) message.getPayload(), TouchAssetEvent.class);
               updateSession =
                  event.design() || event.changed() || event.update() || event.wallboard();
            }
            catch(Exception e) {
               LOG.error("Failed to parse touch asset event", e);
            }
         }

         if(updateSession) {
            Map<String, Object> sessionAttributes =
               SimpMessageHeaderAccessor.getSessionAttributes(message.getHeaders());
            String sessionId = SessionRepositoryMessageInterceptor.getSessionId(sessionAttributes);
            Session session = sessionRepository.findById(sessionId);

            if(session != null) {
               session.setLastAccessedTime(Instant.now());
               this.sessionRepository.save(session);

               SessionAccessDispatcher.access(
                  this,
                  () -> principal,
                  () -> sessionId,
                  () -> sessionAttributes);
            }
         }
      }

      return message;
   }

   private final SessionRepository<Session> sessionRepository;
   private final ObjectMapper objectMapper;
   private static final Logger LOG = LoggerFactory.getLogger(SessionAccessInterceptor.class);
}
