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
package inetsoft.web.portal.controller;

import inetsoft.sree.RepletRegistry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller that lasts the duration of a websocket session used for refreshing
 * the repository tree when a change has occurred.
 *
 * @since 12.3
 */
@Controller
public class RepositoryTreeChangeController {
   /**
    * Creates a new instance of <tt>RepositoryTreeChangeController</tt>.
    *
    * @param assetRepository   the asset repository.
    * @param messagingTemplate the messaging template
    */
   @Autowired
   public RepositoryTreeChangeController(AssetRepository assetRepository,
                                         SimpMessagingTemplate messagingTemplate)
   {
      this.assetRepository = assetRepository;
      this.messagingTemplate = messagingTemplate;
   }

   @PostConstruct
   public void postConstruct() throws Exception {
      RepletRegistry.getRegistry().addPropertyChangeListener(this.reportListener);
      assetRepository.addAssetChangeListener(this.viewsheetListener);
   }

   @PreDestroy
   public void preDestroy() throws Exception {
      RepletRegistry.getRegistry().removePropertyChangeListener(this.reportListener);
      assetRepository.removeAssetChangeListener(this.viewsheetListener);

      for(Map.Entry<Principal, PropertyChangeListener> e : reportListeners.entrySet()) {
         IdentityID pId = IdentityID.getIdentityIDFromKey(e.getKey().getName());
         RepletRegistry.getRegistry(pId).removePropertyChangeListener(e.getValue());
      }
   }

   @SubscribeMapping(COMMANDS_TOPIC)
   public void subscribeToTopic(StompHeaderAccessor header, Principal principal) throws Exception {
      final MessageHeaders messageHeaders = header.getMessageHeaders();
      final String subscriptionId =
         (String) messageHeaders.get(SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER);
      subscriptions.put(subscriptionId, principal);

      if(principal != null) {
         IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
         PropertyChangeListener listener = new ReportListener(principal);
         reportListeners.put(principal, listener);
         RepletRegistry.getRegistry(pId).addPropertyChangeListener(listener);
      }
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
         Principal principal = subscriptions.remove(subscriptionId);

         if(principal != null) {
            PropertyChangeListener listener = reportListeners.remove(principal);

            if(listener != null) {
               try {
                  IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
                  RepletRegistry.getRegistry(pId).removePropertyChangeListener(listener);
               }
               catch(Exception e) {
                  LOG.warn(
                     "Failed to remove registry listener for user {}", principal.getName(), e);
               }
            }
         }
      }
   }

   private void handleReportChanged(PropertyChangeEvent event) {
      if(!RepletRegistry.EDIT_CYCLE_EVENT.equals(event.getPropertyName()) &&
         !RepletRegistry.CHANGE_EVENT.equals(event.getPropertyName()))
      {
         for(Principal principal : subscriptions.values()) {
            messagingTemplate
               .convertAndSendToUser(SUtil.getUserDestination(principal), COMMANDS_TOPIC, "");
         }
      }
   }

   private static final String COMMANDS_TOPIC = "/repository-changed";

   private final AssetRepository assetRepository;
   private final SimpMessagingTemplate messagingTemplate;
   private final PropertyChangeListener reportListener = this::handleReportChanged;
   private final Map<String, Principal> subscriptions = new ConcurrentHashMap<>();
   private final Map<Principal, PropertyChangeListener> reportListeners = new ConcurrentHashMap<>();

   private static final Logger LOG = LoggerFactory.getLogger(RepositoryTreeChangeController.class);

   private final AssetChangeListener viewsheetListener = new AssetChangeListener() {
      @Override
      public void assetChanged(AssetChangeEvent event) {
         if(event.getChangeType() != AssetChangeEvent.ASSET_TO_BE_DELETED) {
            for(Principal principal : subscriptions.values()) {
               messagingTemplate
                  .convertAndSendToUser(SUtil.getUserDestination(principal), COMMANDS_TOPIC, "");
            }
         }
      }
   };

   private final class ReportListener implements PropertyChangeListener {
      public ReportListener(Principal principal) {
         this.principal = principal;
      }

      @Override
      public void propertyChange(PropertyChangeEvent event) {
         if(!RepletRegistry.EDIT_CYCLE_EVENT.equals(event.getPropertyName()) &&
            !RepletRegistry.CHANGE_EVENT.equals(event.getPropertyName()))
         {
            messagingTemplate
               .convertAndSendToUser(SUtil.getUserDestination(principal), COMMANDS_TOPIC, "");
         }
      }

      private final Principal principal;
   }
}
