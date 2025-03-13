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

package inetsoft.web.admin.schedule;

import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;
import inetsoft.util.DefaultDebouncer;
import inetsoft.util.Tool;
import inetsoft.web.service.BaseSubscribeChangHandler;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.DestinationPatternsMessageCondition;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.concurrent.TimeUnit;

@Service
@Lazy(false)
public class ScheduleUsersChangeService extends BaseSubscribeChangHandler implements MessageListener {
   public ScheduleUsersChangeService(SimpMessagingTemplate messageTemplate) {
      super(messageTemplate);
      Cluster.getInstance().addMessageListener(this);
   }

   @PreDestroy
   public void destroyInstance() throws Exception {
      this.debouncer.close();
      Cluster.getInstance().removeMessageListener(this);
   }

   @EventListener
   public void handleUnsubscribe(SessionUnsubscribeEvent event) {
      super.handleUnsubscribe(event);
   }

   @EventListener
   public void handleDisconnect(SessionDisconnectEvent event) {
      super.handleDisconnect(event);
   }

   @Override
   public Object getData(BaseSubscriber subscriber) {
      return "";
   }

   public Object addSubscriber(StompHeaderAccessor headerAccessor) {
      final String sessionId = headerAccessor.getSessionId();
      final MessageHeaders messageHeaders = headerAccessor.getMessageHeaders();
      final String destination = (String) messageHeaders
         .get(SimpMessageHeaderAccessor.DESTINATION_HEADER);
      final String lookupDestination = (String) messageHeaders
         .get(DestinationPatternsMessageCondition.LOOKUP_DESTINATION_HEADER);
      final String subscriptionId =
         (String) messageHeaders.get(SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER);
      final BaseSubscribeChangHandler.BaseSubscriber subscriber =
         new BaseSubscribeChangHandler.BaseSubscriber(sessionId, subscriptionId,
            lookupDestination, destination, headerAccessor.getUser());

      return addSubscriber(subscriber);
   }

   @Override
   public void messageReceived(MessageEvent event) {
      if(event.getMessage() instanceof IdentityChangedMessage message) {
         IdentityID identity = message.getIdentity();

         if(identity == null) {
            return;
         }

         getSubscribers().stream()
            .filter(sub -> sub.getUser() instanceof XPrincipal)
            .filter(sub -> Tool.equals(((XPrincipal) sub.getUser()).getCurrentOrgId(),
                                       identity.getOrgID()))
            .forEach(sub -> {
               this.debouncer.debounce(identity.orgID, 1L, TimeUnit.SECONDS,
                  () -> sendToSubscriber(sub));
            });
      }
   }

   private final DefaultDebouncer<String> debouncer = new DefaultDebouncer<>();
}
