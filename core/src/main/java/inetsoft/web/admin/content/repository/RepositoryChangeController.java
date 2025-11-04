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
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MessageListener;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.*;
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
import java.io.Serializable;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Lazy;

@Lazy(false)
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
      this.cluster = Cluster.getInstance();
   }

   @PostConstruct
   public void addListeners() throws Exception {
      RepletRegistry.getRegistry().addPropertyChangeListener(this.reportListener);
      assetRepository.addAssetChangeListener(this.assetListener);
      assetRepository.addAssetChangeListener(this.autoSaveListener);
      DashboardManager.getManager().addDashboardChangeListener(this.dashboardListener);
      DataSourceRegistry.getRegistry().addRefreshedListener(dataSourceListener);
      cluster.addMessageListener(this.clusterMessageListener);

      for(String orgId : SecurityEngine.getSecurity().getOrganizations()) {
         addLibManagerListener(orgId);
      }
   }

   @PreDestroy
   public void removeListeners() throws Exception {
      closed = true;
      RepletRegistry.getRegistry().removePropertyChangeListener(this.reportListener);
      assetRepository.removeAssetChangeListener(this.assetListener);
      assetRepository.removeAssetChangeListener(this.autoSaveListener);
      DashboardManager.getManager().removeDashboardChangeListener(this.dashboardListener);
      DataSourceRegistry.getRegistry().removeRefreshedListener(dataSourceListener);
      cluster.removeMessageListener(this.clusterMessageListener);

      for(PropertyChangeListener listener : adminReportListeners.values()) {
         RepletRegistry.removeGlobalPropertyChangeListener(listener);
      }

      for(Map.Entry<Principal, PropertyChangeListener> entry : userReportListeners.entrySet()) {
         IdentityID pId = IdentityID.getIdentityIDFromKey(entry.getKey().getName());
         RepletRegistry.getRegistry(pId).removePropertyChangeListener(entry.getValue());
      }

      for(String orgId : SecurityEngine.getSecurity().getOrganizations()) {
         removeLibManagerListener(orgId);
      }

      debouncer.close();
   }

   @SubscribeMapping(CHANGE_TOPIC)
   public void subscribeToTopic(StompHeaderAccessor header, Principal principal) throws Exception {
      final MessageHeaders messageHeaders = header.getMessageHeaders();
      final String sessionId =
         (String) messageHeaders.get(SimpMessageHeaderAccessor.SESSION_ID_HEADER);
      subscriptions.put(sessionId, principal);
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

      if(principal instanceof XPrincipal) {
         String orgId = ((XPrincipal) principal).getCurrentOrgId();
         addLibManagerListener(orgId);
      }
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
         Principal principal = subscriptions.remove(sessionId);

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
         scheduleChangeMessage(null, getOrgId(event));
      }
   }

   private void libraryChanged(ActionEvent event) {
      scheduleChangeMessage(null, getOrgId(event));
   }

   private void dashboardChanged(DashboardChangeEvent event) {
      scheduleChangeMessage(null, event == null ? null : event.getOrgID());
   }

   private void dataSourceChanged(PropertyChangeEvent event) {
      scheduleChangeMessage(null, getOrgId(event));
   }

   private void autoSaveChanged(AssetChangeEvent event) {
      if(event.getChangeType() != AssetChangeEvent.AUTO_SAVE_ADD) {
         scheduleChangeMessage(null, getOrgId(event));
      }
   }

   private String getOrgId(AssetChangeEvent event) {
      AssetEntry entry = event.getAssetEntry();
      return entry == null ? null : entry.getOrgID();
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

   private void scheduleChangeMessage(Principal principal, String orgId) {
      scheduleChangeMessage(principal, orgId, true);
   }

   private void scheduleChangeMessage(Principal principal, String orgId, boolean toCluster) {
      if(toCluster) {
         try {
            cluster.sendMessage(new RepositoryChangeMessage(principal, orgId));
         }
         catch(Exception e) {
            LOG.warn("Failed to send cluster message", e);
         }
      }

      if(principal != null) {
         debouncer.debounce(
            principal.getName(), 1L, TimeUnit.SECONDS, () -> sendChangeMessage(principal));
      }
      else if(orgId != null) {
         debouncer.debounce(orgId, 1L, TimeUnit.SECONDS, () -> sendChangeMessage(orgId));
      }
      else {
         debouncer.debounce("change", 1L, TimeUnit.SECONDS, () -> sendChangeMessage());
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

   private void sendChangeMessage(String orgId) {
      for(Principal principal : subscriptions.values()) {
         String principalOrgID = OrganizationManager.getInstance().getCurrentOrgID(principal) == null ?
            null : OrganizationManager.getInstance().getCurrentOrgID(principal).toLowerCase();

         if(Tool.equals(orgId, principalOrgID)) {
            messagingTemplate
               .convertAndSendToUser(SUtil.getUserDestination(principal), CHANGE_TOPIC, "");
         }
      }
   }

   private final MessageListener clusterMessageListener = (event) -> {
      if(event.getMessage() instanceof RepositoryChangeMessage) {
         RepositoryChangeMessage change = (RepositoryChangeMessage) event.getMessage();
         scheduleChangeMessage(null, change.getOrgId(), false);
      }
   };

   private void addLibManagerListener(String orgId) {
      if(!libManagerListenerOrgs.contains(orgId)) {
         LibManager.getManager(orgId).addActionListener(libraryListener);
         libManagerListenerOrgs.add(orgId);
      }
   }

   private void removeLibManagerListener(String orgId) {
      LibManager.getManager(orgId).removeActionListener(libraryListener);
      libManagerListenerOrgs.remove(orgId);
   }

   private volatile boolean closed = false;
   private final AssetRepository assetRepository;
   private final SimpMessagingTemplate messagingTemplate;
   private final Debouncer<String> debouncer;
   private final Map<String, Principal> subscriptions = new ConcurrentHashMap<>();
   private final Map<Principal, PropertyChangeListener> adminReportListeners = new ConcurrentHashMap<>();
   private final Map<Principal, PropertyChangeListener> userReportListeners = new ConcurrentHashMap<>();
   private final Set<String> libManagerListenerOrgs = new HashSet<>();

   private final ReportChangeListener reportListener = new ReportChangeListener(null);
   private final AssetChangeListener assetListener = this::assetChanged;
   private final ActionListener libraryListener = this::libraryChanged;
   private final DashboardChangeListener dashboardListener = this::dashboardChanged;
   private final PropertyChangeListener dataSourceListener = this::dataSourceChanged;
   private final AssetChangeListener autoSaveListener = this::autoSaveChanged;
   private final Cluster cluster;

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
            scheduleChangeMessage(principal, getOrgId(event));
         }
      }

      private final Principal principal;
   }

   public static final class RepositoryChangeMessage implements Serializable {
      private final String principalName;
      private final String orgId;

      public RepositoryChangeMessage(Principal principal, String orgId) {
         this.principalName = (principal != null) ? principal.getName() : null;
         this.orgId = orgId;
      }

      public String getPrincipalName() {
         return principalName;
      }

      public String getOrgId() {
         return orgId;
      }
   }
}
