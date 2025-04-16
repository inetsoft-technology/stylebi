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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrganizationManager;
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
      this.orgManager = OrganizationManager.getInstance();
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
         subscriptionOrgs.put(user, orgManager.getCurrentOrgID(principal));
      }
   }

   @EventListener
   public void onSessionUnsubscribe(SessionUnsubscribeEvent event) {
      StompHeaderAccessor stompHeaders = StompHeaderAccessor.wrap(event.getMessage());
      String user = subscriptions.remove(stompHeaders.getSessionId());
      subscriptionOrgs.remove(user);
   }

   @EventListener
   public void onSessionDisconnect(SessionDisconnectEvent event) {
      String user = subscriptions.remove(event.getSessionId());
      subscriptionOrgs.remove(user);
   }

   private void assetChanged(AssetChangeEvent event) {
      if(event.getChangeType() != AssetChangeEvent.ASSET_TO_BE_DELETED &&
         event.getAssetEntry() != null &&
         (event.getAssetEntry().getType() == AssetEntry.Type.WORKSHEET ||
            event.getAssetEntry().getType() == AssetEntry.Type.FOLDER))
      {
         sendChangeMessage(event.getAssetEntry().getOrgID());
      }
   }

   private void dataSourceChanged(PropertyChangeEvent event) {
      sendChangeMessage((String) event.getOldValue());
   }

   private void sendChangeMessage(String orgId) {
      boolean isDefaultOrg = Organization.getDefaultOrganizationID().equals(orgId);
      boolean isDefaultOrgPublic = Boolean.parseBoolean(
         SreeEnv.getProperty("security.exposeDefaultOrgToAll", "false"));

      // Only notify organizations able to view the asset
      for(String user : subscriptions.values()) {
         String userOrgId = subscriptionOrgs.get(user);

         if(orgId == null || orgId.equals(userOrgId)) {
            messagingTemplate.convertAndSendToUser(user, CHANGE_TOPIC, "");
         }
         else if(isDefaultOrg) {
            String orgScopedProperty = "security." + userOrgId + ".exposeDefaultOrgToAll";

            if(isDefaultOrgPublic || Boolean.parseBoolean(
               SreeEnv.getProperty(orgScopedProperty)))
            {
               messagingTemplate.convertAndSendToUser(user, CHANGE_TOPIC, "");
            }
         }
      }
   }

   private final AssetRepository assetRepository;
   private final SimpMessagingTemplate messagingTemplate;
   private final OrganizationManager orgManager;
   private final ConcurrentMap<String, String> subscriptions = new ConcurrentHashMap<>();
   private final ConcurrentMap<String, String> subscriptionOrgs = new ConcurrentHashMap<>();

   private final AssetChangeListener assetListener = this::assetChanged;
   private final PropertyChangeListener dataSourceListener = this::dataSourceChanged;

   private static final String CHANGE_TOPIC = "/data-changed";
}
