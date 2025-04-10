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
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.security.Principal;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Controller
@Scope(value = "websocket", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RepositoryChangeController {
   @Autowired
   public RepositoryChangeController(
      AssetRepository assetRepository,
      @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") SimpMessagingTemplate messagingTemplate,
      ScheduleManager scheduleManager)
   {
      this.assetRepository = assetRepository;
      this.messagingTemplate = messagingTemplate;
      this.scheduleManager = scheduleManager;
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
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      IdentityID[] users = SecurityEngine.getSecurity().getSecurityProvider().getUsers();

      if(principal != null && Tool.contains(users, pId)) {
         RepletRegistry.getRegistry(pId)
            .removePropertyChangeListener(this.reportListener);

         if(isSysAdmin(principal)) {
            RepletRegistry.removeGlobalPropertyChangeListener(this.reportListener);
         }
      }

      debouncer.close();
   }

   @SubscribeMapping(CHANGE_TOPIC)
   public void subscribeToTopic(Principal principal) throws Exception {
      this.principal = principal;
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

      if(principal != null) {
         boolean isSysAdmin = isSysAdmin(principal);

         if(isSysAdmin) {
            RepletRegistry.addGlobalPropertyChangeListener(this.reportListener);
         }
         else {
            RepletRegistry.getRegistry(pId)
               .addPropertyChangeListener(this.reportListener);
         }
      }
   }

   private boolean isSysAdmin(Principal principal) {
      try {
         IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
         SecurityProvider securityProvider = SecurityEngine.getSecurity().getSecurityProvider();
         IdentityID[] roles = securityProvider.getRoles(pId);

         if(roles != null) {
            return Arrays.stream(roles).anyMatch(r -> {
               Role role = securityProvider.getRole(r);

               if(role instanceof FSRole && ((FSRole) role).isSysAdmin()) {
                  return true;
               }

               return false;
            });
         }
      }
      catch(Exception ignore) {
      }

      return false;
   }

   private void reportPropertyChanged(PropertyChangeEvent event) {
      if(!RepletRegistry.EDIT_CYCLE_EVENT.equals(event.getPropertyName()) &&
         !RepletRegistry.CHANGE_EVENT.equals(event.getPropertyName()))
      {
         scheduleChangeMessage();
      }
   }

   private void archivePropertyChanged(PropertyChangeEvent event) {
      scheduleChangeMessage();
   }

   private void assetChanged(AssetChangeEvent event) {
      if(event.getChangeType() != AssetChangeEvent.ASSET_TO_BE_DELETED) {
         scheduleChangeMessage();
      }
   }

   private void libraryChanged(ActionEvent event) {
      scheduleChangeMessage();
   }

   private void dashboardChanged(DashboardChangeEvent event) {
      scheduleChangeMessage();
   }

   private void dataSourceChanged(PropertyChangeEvent event) {
      scheduleChangeMessage();
   }

   private void queryChanged(PropertyChangeEvent event) {
      scheduleChangeMessage();
   }

   private void autoSaveChanged(AssetChangeEvent event) {
      if(event.getChangeType() != AssetChangeEvent.AUTO_SAVE_ADD) {
         scheduleChangeMessage();
      }
   }

   private void scheduleChangeMessage() {
      debouncer.debounce("change", 1L, TimeUnit.SECONDS, this::sendChangeMessage);
   }

   private void sendChangeMessage() {
      messagingTemplate
         .convertAndSendToUser(SUtil.getUserDestination(principal), CHANGE_TOPIC, "");
   }

   private final AssetRepository assetRepository;
   private final SimpMessagingTemplate messagingTemplate;
   private Principal principal;
   private final Debouncer<String> debouncer;
   private final ScheduleManager scheduleManager;

   private final PropertyChangeListener reportListener = this::reportPropertyChanged;
   private final PropertyChangeListener archiveListener = this::archivePropertyChanged;
   private final AssetChangeListener assetListener = this::assetChanged;
   private final ActionListener libraryListener = this::libraryChanged;
   private final DashboardChangeListener dashboardListener = this::dashboardChanged;
   private final PropertyChangeListener dataSourceListener = this::dataSourceChanged;
   private final PropertyChangeListener queryListener = this::queryChanged;
   private final AssetChangeListener autoSaveListener = this::autoSaveChanged;

   private static final String CHANGE_TOPIC = "/em-content-changed";
}
