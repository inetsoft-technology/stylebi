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
package inetsoft.web.composer;

import inetsoft.report.LibManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.Debouncer;
import inetsoft.util.DefaultDebouncer;
import inetsoft.web.composer.model.AssetChangeEventModel;
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
import java.beans.PropertyChangeEvent;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Controller
public class AssetTreeRefreshController {
   @Autowired
   public void setAssetRepository(AssetRepository assetRepository) {
      this.assetRepository = assetRepository;
   }

   @Autowired
   public void setMessagingTemplate(SimpMessagingTemplate messagingTemplate) {
      this.messagingTemplate = messagingTemplate;
   }

   @PostConstruct
   public void addListeners() {
      assetRepository.addAssetChangeListener(listener);
      LibManager.getManager().addActionListener(libraryListener);
      DataSourceRegistry.getRegistry().addRefreshedListener(this::dataSourceRefreshed);
      AssetRepository runtimeAssetRepository = AssetUtil.getAssetRepository(false);

      if(runtimeAssetRepository != null && runtimeAssetRepository != assetRepository) {
         runtimeAssetRepository.addAssetChangeListener(listener);
      }
   }

   @PreDestroy
   public void preDestroy() throws Exception {
      assetRepository.removeAssetChangeListener(listener);
      LibManager.getManager().removeActionListener(libraryListener);
      DataSourceRegistry.getRegistry().removeRefreshedListener(this::dataSourceRefreshed);
      AssetRepository runtimeAssetRepository = AssetUtil.getAssetRepository(false);

      if(runtimeAssetRepository != null && runtimeAssetRepository != assetRepository) {
         runtimeAssetRepository.removeAssetChangeListener(listener);
      }

      this.debouncer.close();
   }

   @SubscribeMapping("/asset-changed")
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

   private void dataSourceRefreshed(PropertyChangeEvent event) {
      for(Principal principal : subscriptions.values()) {
         // Data Source Entry
         String orgId = ((XPrincipal) principal).getOrgId();
         AssetEntry entry = AssetEntry.createAssetEntry("0^65605^__NULL__^/^" + orgId);
         AssetChangeEventModel eventModel = AssetChangeEventModel.builder()
            .parentEntry(entry)
            .oldIdentifier(null)
            .newIdentifier(entry.toIdentifier())
            .build();
         messagingTemplate.convertAndSendToUser(
            SUtil.getUserDestination(principal), "/asset-changed", eventModel);
      }
   }

   private void sendMessages(AssetChangeEventModel eventModel) {
      for(Principal user : subscriptions.values()) {
         messagingTemplate.convertAndSendToUser(
            SUtil.getUserDestination(user), "/asset-changed", eventModel);
      }
   }

   private AssetRepository assetRepository;
   private SimpMessagingTemplate messagingTemplate;
   private final Map<String, Principal> subscriptions = new ConcurrentHashMap<>();

   final private Debouncer<String> debouncer = new DefaultDebouncer<>(false);
   private static final String TABLE_STYLE = "Table Style";
   private static final String SCRIPT = "Script Function";

   private final AssetChangeListener listener = new AssetChangeListener() {
      @Override
      public void assetChanged(AssetChangeEvent event) {
         if(isConnectionInitialized() && canSendEvent(event)) {
            AssetChangeEventModel eventModel = AssetChangeEventModel.builder()
               .parentEntry(event.getAssetEntry().getParent())
               .oldIdentifier(event.getOldName())
               .newIdentifier(event.getAssetEntry().toIdentifier())
               .build();

            debouncer.debounce("change" + (event.getAssetEntry().getParent() != null ?
               event.getAssetEntry().getParent().toIdentifier() : ""), 2, TimeUnit.SECONDS, () -> sendMessages(eventModel)
            );
         }
      }

      private boolean canSendEvent(AssetChangeEvent event) {
         return event.isRoot() && event.getChangeType() != AssetChangeEvent.AUTO_SAVE_ADD &&
            (event.getChangeType() != AssetChangeEvent.ASSET_TO_BE_DELETED &&
               event.getAssetEntry().getParent() != null ||
               event.getChangeType() == AssetChangeEvent.ASSET_RENAMED);
      }

      private boolean isConnectionInitialized() {
         return messagingTemplate != null && !subscriptions.isEmpty();
      }
   };

   private final ActionListener libraryListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
         int changeType = event.getID();
         AssetChangeEventModel eventModel = null;

         if(changeType == LibManager.SCRIPT_ADDED || changeType == LibManager.SCRIPT_REMOVED ||
            changeType == LibManager.SCRIPT_MODIFIED)
         {
            AssetEntry scriptRootEntry = new AssetEntry(AssetRepository.COMPONENT_SCOPE,
                                                        AssetEntry.Type.SCRIPT_FOLDER, "/" + SCRIPT, null);

            eventModel = AssetChangeEventModel.builder()
               .parentEntry(scriptRootEntry)
               .oldIdentifier(null)
               .newIdentifier(scriptRootEntry.toIdentifier())
               .build();
         }

         if(changeType == LibManager.STYLE_ADDED || changeType == LibManager.STYLE_REMOVED ||
            changeType == LibManager.STYLE_MODIFIED)
         {
            AssetEntry scriptRootEntry = new AssetEntry(AssetRepository.COMPONENT_SCOPE,
                                                        AssetEntry.Type.TABLE_STYLE_FOLDER, "/" + TABLE_STYLE, null);

            eventModel = AssetChangeEventModel.builder()
               .parentEntry(scriptRootEntry)
               .oldIdentifier(null)
               .newIdentifier(scriptRootEntry.toIdentifier())
               .build();
         }

         if(eventModel != null) {
            sendMessages(eventModel);
         }
      }
   };

}
