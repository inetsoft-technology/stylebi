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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
public class NotificationService implements MessageListener {
   @Autowired
   public NotificationService(SimpMessagingTemplate messagingTemplate) {
      this.messagingTemplate = messagingTemplate;
   }

   @PostConstruct
   public void addListener() {
      cluster = Cluster.getInstance();
      cluster.addMessageListener(this);
   }

   @PreDestroy
   public void removeListener() {
      if(cluster != null) {
         cluster.removeMessageListener(this);
      }
   }

   @Override
   public void messageReceived(MessageEvent event) {
      if(event.getMessage() instanceof NotificationMessage) {
         NotificationMessage notification = (NotificationMessage) event.getMessage();
         messagingTemplate.convertAndSend("/notifications", notification);
      }
   }

   public void sendNotification(String message) throws Exception {
      NotificationMessage notification = NotificationMessage.builder().message(message).build();
      Cluster.getInstance().sendMessage(notification);
   }

   public void sendNotificationToUser(String message, Principal principal) {
      NotificationMessage notification = NotificationMessage.builder().message(message).build();
      messagingTemplate.convertAndSendToUser(SUtil.getUserDestination(principal), "/notifications",
                                             notification);
   }

   private final SimpMessagingTemplate messagingTemplate;
   private Cluster cluster;
}
