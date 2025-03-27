/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.viewsheet.service;

import inetsoft.sree.internal.cluster.*;
import inetsoft.web.messaging.WebsocketConnectionEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

@Component
public class CommandDispatcherService
   implements MessageListener, ApplicationListener<WebsocketConnectionEvent>
{
   public CommandDispatcherService(SimpMessagingTemplate template) {
      this.template = template;
   }

   @PostConstruct
   public void registerListener() {
      cluster = Cluster.getInstance();
      sessions = cluster.getReplicatedMap(getClass().getName() + ".sessions");
      cluster.addMessageListener(this);
   }

   @PreDestroy
   public void deregisterListener() {
      if(cluster != null) {
         cluster.removeMessageListener(this);
      }
   }

   public void convertAndSendToUser(String user, String destination, Object payload,
                                    Map<String, Object> headers) throws MessagingException
   {
      String sessionId = SimpMessageHeaderAccessor.getSessionId(headers);
      String node = sessions.get(sessionId);

      if(cluster.getLocalMember().equals(node)) {
         template.convertAndSendToUser(user, destination, payload, headers);
      }
      else if(node != null) {
         String id = UUID.randomUUID().toString();
         CommandMessageResponse response;

         try {
            response = cluster.exchangeMessages(
               node, new CommandMessage(id, user, destination, payload, headers),
               e -> filterCommandMessageResponse(id, e));
         }
         catch(Exception e) {
            throw new MessagingException("Failed to dispatch message to primary node", e);
         }

         if(response.getError() instanceof MessagingException ex) {
            throw ex;
         }

         if(response.getError() != null) {
            throw new MessagingException(
               "Failed to dispatch message to primary node", response.getError());
         }
      }
   }

   @Override
   public void onApplicationEvent(WebsocketConnectionEvent event) {
      String sessionId = event.getSessionId();

      if(sessionId != null) {
         if(event.isConnected()) {
            sessions.put(sessionId, cluster.getLocalMember());
         }
         else {
            sessions.remove(sessionId);
         }
      }
   }

   @Override
   public void messageReceived(MessageEvent event) {
      if(event.getMessage() instanceof CommandMessage message) {
         CommandMessageResponse response;

         try {
            template.convertAndSendToUser(
               message.getUser(), message.getDestination(), message.getPayload(),
               message.getHeaders());
            response = new CommandMessageResponse(message.getId());
         }
         catch(Exception e) {
            response = new CommandMessageResponse(message.getId(), e);
         }

         try {
            cluster.sendMessage(event.getSender(), response);
         }
         catch(Exception e) {
            LOG.error("Failed to send command message response", e);
         }
      }
   }

   private CommandMessageResponse filterCommandMessageResponse(String id, MessageEvent event) {
      if(event.getMessage() instanceof CommandMessageResponse response &&
         response.getId().equals(id))
      {
         return response;
      }

      return null;
   }

   private final SimpMessagingTemplate template;
   private Cluster cluster;
   private DistributedMap<String, String> sessions;
   private static final Logger LOG = LoggerFactory.getLogger(CommandDispatcherService.class);

   public static final class CommandMessage implements Serializable {
      public CommandMessage(String id, String user, String destination, Object payload,
                            Map<String, Object> headers)
      {
         this.id = id;
         this.user = user;
         this.destination = destination;
         this.payload = payload;
         this.headers = headers;
      }

      public String getId() {
         return id;
      }

      public String getUser() {
         return user;
      }

      public String getDestination() {
         return destination;
      }

      public Object getPayload() {
         return payload;
      }

      public Map<String, Object> getHeaders() {
         return headers;
      }

      private final String id;
      private final String user;
      private final String destination;
      private final Object payload;
      private final Map<String, Object> headers;
   }

   public static final class CommandMessageResponse implements Serializable {
      public CommandMessageResponse(String id) {
         this(id, null);
      }

      public CommandMessageResponse(String id, Throwable error) {
         this.id = id;
         this.error = error;
      }

      public String getId() {
         return id;
      }

      public Throwable getError() {
         return error;
      }

      private final String id;
      private final Throwable error;
   }
}
