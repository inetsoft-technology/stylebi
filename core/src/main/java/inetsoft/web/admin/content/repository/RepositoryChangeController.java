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

import inetsoft.report.LibManager;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.Debouncer;
import inetsoft.util.DefaultDebouncer;
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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.security.Principal;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Controller
public class RepositoryChangeController {
   @Autowired
   public RepositoryChangeController(
      AssetRepository assetRepository,
      SimpMessagingTemplate messagingTemplate)
   {
      this.assetRepository = assetRepository;
      this.messagingTemplate = messagingTemplate;
      this.debouncer = new DefaultDebouncer<>();
   }

   @PostConstruct
   public void addListeners() throws Exception {
      RepletRegistry.getRegistry().addPropertyChangeListener(this.reportListener);
      assetRepository.addAssetChangeListener(this.assetListener);
      assetRepository.addAssetChangeListener(this.autoSaveListener);
      LibManager.getManager().addActionListener(this.libraryListener);
      DashboardManager.getManager().addDashboardChangeListener(this.dashboardListener);
      DataSourceRegistry.getRegistry().addRefreshedListener(dataSourceListener);
   }

   @PreDestroy
   public void removeListeners() throws Exception {
      RepletRegistry.getRegistry().removePropertyChangeListener(this.reportListener);
      assetRepository.removeAssetChangeListener(this.assetListener);
      assetRepository.removeAssetChangeListener(this.autoSaveListener);
      LibManager.getManager().removeActionListener(this.libraryListener);
      DashboardManager.getManager().removeDashboardChangeListener(this.dashboardListener);
      DataSourceRegistry.getRegistry().removeRefreshedListener(dataSourceListener);

      for(PropertyChangeListener listener : adminReportListeners.values()) {
         RepletRegistry.removeGlobalPropertyChangeListener(listener);
      }

      for(Map.Entry<Principal, PropertyChangeListener> entry : userReportListeners.entrySet()) {
         IdentityID pId = IdentityID.getIdentityIDFromKey(entry.getKey().getName());
         RepletRegistry.getRegistry(pId).removePropertyChangeListener(entry.getValue());
      }

      debouncer.close();
   }

   @SubscribeMapping(CHANGE_TOPIC)
   public void subscribeToTopic(StompHeaderAccessor header, Principal principal) throws Exception {
      final MessageHeaders messageHeaders = header.getMessageHeaders();
      final String subscriptionId =
         (String) messageHeaders.get(SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER);
      subscriptions.put(subscriptionId, principal);
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

      if(principal != null) {
         boolean isSysAdmin = isSysAdmin(principal);

         if(isSysAdmin) {
            PropertyChangeListener listener = new ReportChangeListener(null);
            adminReportListeners.put(principal, listener);
            RepletRegistry.addGlobalPropertyChangeListener(listener);
         }
         else {
            PropertyChangeListener listener = new ReportChangeListener(principal);
            userReportListeners.put(principal, listener);
            RepletRegistry.getRegistry(pId).addPropertyChangeListener(listener);
         }
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
            PropertyChangeListener listener = adminReportListeners.remove(principal);

            if(listener != null) {
               RepletRegistry.removeGlobalPropertyChangeListener(listener);
            }

            listener = userReportListeners.remove(principal);

            if(listener != null) {
               try {
                  IdentityID identityId = IdentityID.getIdentityIDFromKey(principal.getName());
                  RepletRegistry.getRegistry(identityId).removePropertyChangeListener(listener);
               }
               catch(Exception e) {
                  LOG.warn("Failed to remove registry listener for user {}", principal.getName(), e);
               }
            }
         }
      }
   }

   private boolean isSysAdmin(Principal principal) {
      try {
         IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
         SecurityProvider securityProvider = SecurityEngine.getSecurity().getSecurityProvider();
         IdentityID[] roles = securityProvider.getRoles(pId);

         if(roles != null) {
            return Arrays.stream(roles)
               .map(securityProvider::getRole)
               .filter(r -> r instanceof FSRole)
               .map(FSRole.class::cast)
               .anyMatch(FSRole::isSysAdmin);
         }
      }
      catch(Exception ignore) {
      }

      return false;
   }

   private void assetChanged(AssetChangeEvent event) {
      if(event.getChangeType() != AssetChangeEvent.ASSET_TO_BE_DELETED) {
         scheduleChangeMessage(null);
      }
   }

   private void libraryChanged(ActionEvent event) {
      scheduleChangeMessage(null);
   }

   private void dashboardChanged(DashboardChangeEvent event) {
      scheduleChangeMessage(null);
   }

   private void dataSourceChanged(PropertyChangeEvent event) {
      scheduleChangeMessage(null);
   }

   private void autoSaveChanged(AssetChangeEvent event) {
      if(event.getChangeType() != AssetChangeEvent.AUTO_SAVE_ADD) {
         scheduleChangeMessage(null);
      }
   }

   private void scheduleChangeMessage(Principal principal) {
      if(principal == null) {
         debouncer.debounce("change", 1L, TimeUnit.SECONDS, () -> sendChangeMessage());
      }
      else {
         debouncer.debounce(
            principal.getName(), 1L, TimeUnit.SECONDS, () -> sendChangeMessage(principal));
      }
   }

   private void sendChangeMessage() {
      for(Principal principal : subscriptions.values()) {
         sendChangeMessage(principal);
      }
   }

   private void sendChangeMessage(Principal principal) {
      messagingTemplate
         .convertAndSendToUser(SUtil.getUserDestination(principal), CHANGE_TOPIC, "");
   }

   private final AssetRepository assetRepository;
   private final SimpMessagingTemplate messagingTemplate;
   private final Debouncer<String> debouncer;
   private final Map<String, Principal> subscriptions = new ConcurrentHashMap<>();
   private final Map<Principal, PropertyChangeListener> adminReportListeners = new ConcurrentHashMap<>();
   private final Map<Principal, PropertyChangeListener> userReportListeners = new ConcurrentHashMap<>();

   private final ReportChangeListener reportListener = new ReportChangeListener(null);
   private final AssetChangeListener assetListener = this::assetChanged;
   private final ActionListener libraryListener = this::libraryChanged;
   private final DashboardChangeListener dashboardListener = this::dashboardChanged;
   private final PropertyChangeListener dataSourceListener = this::dataSourceChanged;
   private final AssetChangeListener autoSaveListener = this::autoSaveChanged;

   private static final String CHANGE_TOPIC = "/em-content-changed";
   private static final Logger LOG = LoggerFactory.getLogger(RepositoryChangeController.class);

   private final class ReportChangeListener implements PropertyChangeListener {
      public ReportChangeListener(Principal principal) {
         this.principal = principal;
      }

      @Override
      public void propertyChange(PropertyChangeEvent event) {
         if(!RepletRegistry.EDIT_CYCLE_EVENT.equals(event.getPropertyName()) &&
            !RepletRegistry.CHANGE_EVENT.equals(event.getPropertyName()))
         {
            scheduleChangeMessage(principal);
         }
      }

      private final Principal principal;
   }
}
