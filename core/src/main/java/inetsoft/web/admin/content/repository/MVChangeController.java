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
package inetsoft.web.admin.content.repository;

import inetsoft.mv.MVManager;
import inetsoft.report.internal.Util;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.Debouncer;
import inetsoft.util.DefaultDebouncer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.*;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.security.Principal;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Controller
public class MVChangeController implements MessageListener {
   @Autowired
   public MVChangeController(SimpMessagingTemplate messagingTemplate)
   {
      this.messagingTemplate = messagingTemplate;
      this.debouncer = new DefaultDebouncer<>();
   }

   @PostConstruct
   public void addListeners() {
      MVManager.getManager().addPropertyChangeListener(this.mvListener);
      Cluster.getInstance().addMessageListener(this);
   }

   @PreDestroy
   public void removeListeners() throws Exception {
      MVManager.getManager().removePropertyChangeListener(this.mvListener);
      Cluster.getInstance().removeMessageListener(this);
      debouncer.close();
   }

   @SubscribeMapping(CHANGE_TOPIC)
   public void subscribeToTopic(StompHeaderAccessor header, Principal principal) throws Exception {
      final MessageHeaders messageHeaders = header.getMessageHeaders();
      final String sessionId =
         (String) messageHeaders.get(SimpMessageHeaderAccessor.SESSION_ID_HEADER);
      subscriptions.put(sessionId, principal);
   }

   @EventListener
   public void handleUnsubscribe(SessionUnsubscribeEvent event) {
      removeSubscription(event);
   }

   @EventListener
   public void handleDisconnect(SessionDisconnectEvent event) {
      removeSubscription(event);
   }

   private void removeSubscription(AbstractSubProtocolEvent event) {
      final Message<byte[]> message = event.getMessage();
      final MessageHeaders headers = message.getHeaders();
      final String sessionId =
         (String) headers.get(SimpMessageHeaderAccessor.SESSION_ID_HEADER);

      if(sessionId != null) {
         subscriptions.remove(sessionId);
      }
   }

   private void mvPropertyChanged(PropertyChangeEvent event) {
      if(MVManager.MV_CHANGE_EVENT.equals(event.getPropertyName())) {
         String orgId = Util.getOrgIdFromEventSource(event.getSource());
         scheduleChangeMessage(orgId);
      }
   }

   private void scheduleChangeMessage(String orgId) {
      debouncer.debounce("change-" + orgId, 1L, TimeUnit.SECONDS, () -> sendChangeMessage(orgId));
   }

   private void sendChangeMessage(String orgId) {
      for(Principal principal : subscriptions.values()) {
         if(Objects.equals(orgId, OrganizationManager.getInstance().getCurrentOrgID(principal))) {
            messagingTemplate
               .convertAndSendToUser(SUtil.getUserDestination(principal), CHANGE_TOPIC, "");
         }
      }
   }

   @Override
   public void messageReceived(MessageEvent event) {
      if(event.getMessage() instanceof SimpleMessage simpleMessage) {
         if(MVManager.MV_CHANGE_EVENT.equals(simpleMessage.getMessage())) {
            scheduleChangeMessage(simpleMessage.getOrgID());
         }
      }
   }

   private final Map<String, Principal> subscriptions = new ConcurrentHashMap<>();

   private final SimpMessagingTemplate messagingTemplate;
   private final Debouncer<String> debouncer;
   private final PropertyChangeListener mvListener = this::mvPropertyChanged;

   private static final String CHANGE_TOPIC = "/em-mv-changed";
}
