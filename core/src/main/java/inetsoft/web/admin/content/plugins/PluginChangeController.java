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

package inetsoft.web.admin.content.plugins;

import inetsoft.sree.internal.SUtil;
import inetsoft.util.*;
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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Controller
public class PluginChangeController {
   @Autowired
   public PluginChangeController(SimpMessagingTemplate messagingTemplate) {
      this.debouncer = new DefaultDebouncer<>();
      this.messagingTemplate = messagingTemplate;
   }

   @PostConstruct
   public void addListeners() {
      Plugins.getInstance().addActionListener(this.pluginListener);
   }

   @PreDestroy
   public void removeListeners() {
      Plugins.getInstance().removeActionListener(this.pluginListener);
   }

   @SubscribeMapping(CHANGE_TOPIC)
   public void subscribeToTopic(StompHeaderAccessor header, Principal principal) {
      final MessageHeaders messageHeaders = header.getMessageHeaders();
      final String subscriptionId =
         (String) messageHeaders.get(SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER);
      subscriptions.put(subscriptionId, principal);
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
      final String subscriptionId =
         (String) headers.get(SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER);

      if(subscriptionId != null) {
         subscriptions.remove(subscriptionId);
      }
   }

   private void pluginChanged(ActionEvent actionEvent) {
      debouncer.debounce("change", 1L, TimeUnit.SECONDS, this::sendChangeMessage);
   }

   private void sendChangeMessage() {
      for(Principal principal : subscriptions.values()) {
         messagingTemplate
            .convertAndSendToUser(SUtil.getUserDestination(principal), CHANGE_TOPIC, "");
      }
   }

   private final Map<String, Principal> subscriptions = new ConcurrentHashMap<>();
   private final Debouncer<String> debouncer;
   private final SimpMessagingTemplate messagingTemplate;
   private static final String CHANGE_TOPIC = "/em-plugin-changed";
   private final ActionListener pluginListener = this::pluginChanged;
}
