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
package inetsoft.web.notifications;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
public class NotificationService implements MessageListener {
   @Autowired
   public NotificationService(SimpMessagingTemplate messagingTemplate, Cluster cluster) {
      this.messagingTemplate = messagingTemplate;
      this.cluster = cluster;
   }

   @PostConstruct
   public void addListener() {
      cluster.addMessageListener(this);
   }

   @PreDestroy
   public void removeListener() {
      try {
         cluster.removeMessageListener(this);
      }
      catch(Exception e) {
         LOG.debug("Failed to remove listener during shutdown", e);
      }
   }

   @Override
   public void messageReceived(MessageEvent event) {
      if(event.getMessage() instanceof UserNotificationMessage message) {
         // the node that sent the message has already delivered it to the user's web socket
         // sessions on that node. Never broadcast it, it is for one user only
         if(!event.isLocal()) {
            sendToUser(message.getDestination(), message.getNotification());
         }
      }
      else if(event.getMessage() instanceof NotificationMessage notification) {
         messagingTemplate.convertAndSend("/notifications", notification);
      }
   }

   public void sendNotification(String message) throws Exception {
      NotificationMessage notification = NotificationMessage.builder().message(message).build();
      cluster.sendMessage(notification);
   }

   /**
    * Sends a notification to one user. The user's web socket may be connected to any cluster
    * node, and a user destination only reaches the sessions on the node that sends it, so the
    * notification is delivered on this node and forwarded to the other nodes too. Failures are
    * logged, not thrown.
    */
   public void sendNotificationToUser(String message, Principal principal) {
      String destination = SUtil.getUserDestination(principal);

      if(destination == null) {
         LOG.info("Notification not sent, no user destination for principal {}: {}",
                  principal, message);
         return;
      }

      NotificationMessage notification = NotificationMessage.builder().message(message).build();
      sendToUser(destination, notification);

      // Bug #78248, the user's web socket may be connected to another node
      UserNotificationMessage userMessage = new UserNotificationMessage(destination, notification);

      try {
         cluster.sendMessage(userMessage);
      }
      catch(Exception e) {
         LOG.warn("Failed to send the notification to the cluster: {}", userMessage, e);
      }
   }

   private void sendToUser(String destination, NotificationMessage notification) {
      try {
         messagingTemplate.convertAndSendToUser(destination, "/notifications", notification);
      }
      catch(Exception e) {
         LOG.warn("Failed to send the notification to user {}: {}", destination, notification, e);
      }
   }

   private final SimpMessagingTemplate messagingTemplate;
   private final Cluster cluster;
   private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);
}
