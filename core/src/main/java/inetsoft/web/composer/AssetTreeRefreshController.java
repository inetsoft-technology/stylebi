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
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MessageListener;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.*;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.*;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

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

   @Autowired
   public void setSecurityEngine(SecurityEngine securityEngine) {
      this.securityEngine = securityEngine;
   }

   @Autowired
   public void setDataSourceRegistry(DataSourceRegistry dataSourceRegistry) {
      this.dataSourceRegistry = dataSourceRegistry;
   }

   @Autowired
   public void setLibManagerProvider(LibManagerProvider libManagerProvider) {
      this.libManagerProvider = libManagerProvider;
   }

   @Autowired
   public void setCluster(Cluster cluster) {
      this.cluster = cluster;
   }

   /**
    * Sets how long, in milliseconds, changes to the same folder are collected before the
    * subscribers are notified. Only for tests, the default is 2 seconds.
    */
   void setDeliveryDelay(long deliveryDelay) {
      this.deliveryDelay = deliveryDelay;
   }

   @PostConstruct
   public void addListeners() {
      assetRepository.addAssetChangeListener(listener);
      cluster.addMessageListener(clusterMessageListener);
      dataSourceRegistry.addRefreshedListener(this::dataSourceRefreshed);
      AssetRepository runtimeAssetRepository = AssetUtil.getAssetRepository(false);

      if(runtimeAssetRepository != null && runtimeAssetRepository != assetRepository) {
         runtimeAssetRepository.addAssetChangeListener(listener);
      }

      for(String orgId : securityEngine.getOrganizations()) {
         addLibManagerListener(orgId);
      }
   }

   @PreDestroy
   public void preDestroy() {
      try {
         assetRepository.removeAssetChangeListener(listener);
         cluster.removeMessageListener(clusterMessageListener);
         dataSourceRegistry.removeRefreshedListener(this::dataSourceRefreshed);
         AssetRepository runtimeAssetRepository = AssetUtil.getAssetRepository(false);

         if(runtimeAssetRepository != null && runtimeAssetRepository != assetRepository) {
            runtimeAssetRepository.removeAssetChangeListener(listener);
         }

         for(String orgId : securityEngine.getOrganizations()) {
            removeLibManagerListener(orgId);
         }

         this.debouncer.close();
      }
      catch(Exception e) {
         LOG.debug("Failed to clean up during shutdown", e);
      }
   }

   @SubscribeMapping("/asset-changed")
   public void subscribeToTopic(StompHeaderAccessor header, Principal principal) {
      final MessageHeaders messageHeaders = header.getMessageHeaders();
      final String sessionId =
         (String) messageHeaders.get(SimpMessageHeaderAccessor.SESSION_ID_HEADER);
      subscriptions.put(sessionId, principal);

      if(principal instanceof XPrincipal) {
         String orgId = ((XPrincipal) principal).getCurrentOrgId();
         addLibManagerListener(orgId);
      }
   }

   @EventListener(SessionDisconnectEvent.class)
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

   private void dataSourceRefreshed(PropertyChangeEvent event) {
      for(Principal principal : subscriptions.values()) {
         // Data Source Entry
         String orgId = ((XPrincipal) principal).getOrgId();
         AssetEntry entry = AssetEntry.createAssetEntry("0^65605^__NULL__^/^" + orgId);
         String eventOrgID = getOrgId(event);

         //only propagate asset changed event to listener if global asset or same organization, extraneous otherwise
         if(eventOrgID == null || orgId == null || Tool.equals(eventOrgID, orgId)) {
            AssetChangeEventModel eventModel = AssetChangeEventModel.builder()
               .parentEntry(entry)
               .oldIdentifier(null)
               .newIdentifier(entry.toIdentifier())
               .build();
            messagingTemplate.convertAndSendToUser(
               SUtil.getUserDestination(principal), "/asset-changed", eventModel);
         }
      }
   }

   private String getOrgId(PropertyChangeEvent event) {
      if(event instanceof inetsoft.report.PropertyChangeEvent e) {
         return e.getOrgID();
      }

      return null;
   }

   private String getOrgId(ActionEvent event) {
      if(event instanceof inetsoft.report.ActionEvent e) {
         return e.getOrgID();
      }

      return null;
   }

   private void sendMessages(AssetChangeEventModel eventModel, String orgId) {
      sendMessages(eventModel, p -> Objects.equals(orgId, OrganizationManager.getInstance().getCurrentOrgID(p)));
   }

   private void sendMessages(AssetChangeEventModel eventModel, Predicate<Principal> cond) {
      for(Principal user : subscriptions.values()) {
         if(cond.test(user)) {
            messagingTemplate.convertAndSendToUser(
               SUtil.getUserDestination(user), "/asset-changed", eventModel);
         }
      }
   }

   private void addLibManagerListener(String orgId) {
      if(!libManagerListenerOrgs.contains(orgId)) {
         libManagerProvider.getManager(orgId).addActionListener(libraryListener);
         libManagerListenerOrgs.add(orgId);
      }
   }

   private void removeLibManagerListener(String orgId) {
      libManagerProvider.getManager(orgId).removeActionListener(libraryListener);
      libManagerListenerOrgs.remove(orgId);
   }

   private AssetRepository assetRepository;
   private SimpMessagingTemplate messagingTemplate;
   private SecurityEngine securityEngine;
   private DataSourceRegistry dataSourceRegistry;
   private LibManagerProvider libManagerProvider;
   private Cluster cluster;
   private final Map<String, Principal> subscriptions = new ConcurrentHashMap<>();
   private static final Logger LOG = LoggerFactory.getLogger(AssetTreeRefreshController.class);

   private static final long BROADCAST_DELAY = 200L;
   // how long changes to the same folder are collected before the subscribers are notified
   private long deliveryDelay = 2000L;
   private final Debouncer<String> debouncer = new DefaultDebouncer<>(false);
   private final Set<String> libManagerListenerOrgs = new HashSet<>();

   private final AssetChangeListener listener = new AssetChangeListener() {
      @Override
      public void assetChanged(AssetChangeEvent event) {
         if(event.getAssetEntry() == null || !canSendEvent(event)) {
            return;
         }

         // Bug #78229, the change may be made on a node other than the one the user's
         // web socket is connected to, e.g. a save proxied to the node that holds the runtime
         // sheet. Forward it to the other nodes. A storage refresh event is already fired on
         // every node, so it is not forwarded
         if(!event.isStorageRefresh()) {
            broadcast(event);
         }

         deliver(event);
      }
   };

   /**
    * Receives the asset changes forwarded by the other cluster nodes and delivers them to the
    * subscribers on this node. It never forwards them again.
    */
   private final MessageListener clusterMessageListener = event -> {
      if(event.isLocal() || !(event.getMessage() instanceof AssetTreeChangeMessage message)) {
         // the node that sent the message has already delivered the change to its own
         // subscribers
         return;
      }

      AssetChangeEvent change = new AssetChangeEvent(
         this, message.getEntryType(), message.getChangeType(), message.getAssetEntry(),
         message.getOldName(), true, null, "Cluster: " + message.getAssetEntry(), false);

      if(change.getAssetEntry() != null && canSendEvent(change)) {
         deliver(change);
      }
   };

   private void broadcast(AssetChangeEvent event) {
      AssetTreeChangeMessage message = new AssetTreeChangeMessage(
         event.getEntryType(), event.getChangeType(), event.getAssetEntry(), event.getOldName());
      // the listener may be registered on two repositories (see addListeners()). Coalesce the
      // same change fired by both into one message. This also keeps the send off the thread
      // that made the change
      String key = "cluster" + event.getChangeType() + "|" +
         event.getAssetEntry().toIdentifier() + "|" + event.getOldName();
      debouncer.debounce(key, BROADCAST_DELAY, TimeUnit.MILLISECONDS, () -> {
         try {
            cluster.sendMessage(message);
         }
         catch(Exception e) {
            LOG.warn("Failed to send the asset change to the cluster: {}", message, e);
         }
      });
   }

   private void deliver(AssetChangeEvent event) {
      if(isConnectionInitialized()) {
         AssetChangeEventModel eventModel = AssetChangeEventModel.builder()
            .parentEntry(event.getAssetEntry().getParent())
            .oldIdentifier(event.getOldName())
            .newIdentifier(event.getAssetEntry().toIdentifier())
            .build();

         debouncer.debounce("change" + (event.getAssetEntry().getParent() != null ?
            event.getAssetEntry().getParent().toIdentifier() : ""), deliveryDelay,
                            TimeUnit.MILLISECONDS,
                            () -> sendMessages(eventModel, u -> isSameOrg(event, u))
         );
      }
   }

   private boolean isSameOrg(AssetChangeEvent event, Principal user) {
      String currentOrgID = OrganizationManager.getInstance().getCurrentOrgID(user);
      return event.getAssetEntry() == null ||
         Tool.equals(currentOrgID, event.getAssetEntry().getOrgID());
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

   private final ActionListener libraryListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
         debouncer.debounce("lib_changed" + getOrgId(event), 2, TimeUnit.SECONDS, () -> {
            AssetEntry root = new AssetEntry(
               AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.LIBRARY_FOLDER, "/", null,
               getOrgId(event));

            AssetChangeEventModel eventModel = AssetChangeEventModel.builder()
               .parentEntry(root)
               .oldIdentifier(null)
               .newIdentifier(root.toIdentifier())
               .build();

            if(eventModel != null) {
               sendMessages(eventModel, getOrgId(event));
            }
         });
      }
   };

}
