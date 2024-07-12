/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.controller;

import inetsoft.sree.internal.SUtil;
import inetsoft.uql.asset.*;
import inetsoft.uql.service.DataSourceRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.security.Principal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Controller
public class DataTreeChangeController {
   @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
   public DataTreeChangeController(AssetRepository assetRepository,
                                   SimpMessagingTemplate messagingTemplate)
   {
      this.assetRepository = assetRepository;
      this.messagingTemplate = messagingTemplate;
   }

   @PostConstruct
   public void addListeners() {
      assetRepository.addAssetChangeListener(this.assetListener);
      DataSourceRegistry.getRegistry().addModifiedListener(this.dataSourceListener);
   }

   @PreDestroy
   public void removeListeners() {
      assetRepository.removeAssetChangeListener(this.assetListener);
      DataSourceRegistry.getRegistry().removeModifiedListener(this.dataSourceListener);
   }

   @SubscribeMapping(CHANGE_TOPIC)
   public void subscribeToTopic(StompHeaderAccessor stompHeaders, Principal principal) {
      String user = SUtil.getUserDestination(principal);

      if(user != null) {
         subscriptions.put(stompHeaders.getSessionId(), user);
      }
   }

   @EventListener
   public void onSessionUnsubscribe(SessionUnsubscribeEvent event) {
      StompHeaderAccessor stompHeaders = StompHeaderAccessor.wrap(event.getMessage());
      subscriptions.remove(stompHeaders.getSessionId());
   }

   @EventListener
   public void onSessionDisconnect(SessionDisconnectEvent event) {
      subscriptions.remove(event.getSessionId());
   }

   private void assetChanged(AssetChangeEvent event) {
      if(event.getChangeType() != AssetChangeEvent.ASSET_TO_BE_DELETED &&
         event.getAssetEntry() != null &&
         (event.getAssetEntry().getType() == AssetEntry.Type.WORKSHEET ||
            event.getAssetEntry().getType() == AssetEntry.Type.FOLDER))
      {
         sendChangeMessage();
      }
   }

   private void dataSourceChanged(PropertyChangeEvent event) {
      sendChangeMessage();
   }

   private void sendChangeMessage() {
      for(String user : subscriptions.values()) {
         messagingTemplate.convertAndSendToUser(user, CHANGE_TOPIC, "");
      }
   }

   private final AssetRepository assetRepository;
   private final SimpMessagingTemplate messagingTemplate;
   private final ConcurrentMap<String, String> subscriptions = new ConcurrentHashMap<>();

   private final AssetChangeListener assetListener = this::assetChanged;
   private final PropertyChangeListener dataSourceListener = this::dataSourceChanged;

   private static final String CHANGE_TOPIC = "/data-changed";
}
