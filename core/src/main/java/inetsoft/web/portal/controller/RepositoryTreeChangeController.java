/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.controller;

import inetsoft.sree.RepletRegistry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.security.Principal;

/**
 * Controller that lasts the duration of a websocket session used for refreshing
 * the repository tree when a change has occurred.
 *
 * @since 12.3
 */
@Controller
@Scope(value = "websocket", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RepositoryTreeChangeController {
   /**
    * Creates a new instance of <tt>RepositoryTreeChangeController</tt>.
    *
    * @param assetRepository   the asset repository.
    * @param messagingTemplate the messaging template
    */
   @Autowired
   public RepositoryTreeChangeController(AssetRepository assetRepository,
                                         SimpMessagingTemplate messagingTemplate,
                                         ScheduleManager scheduleManager)
   {
      this.assetRepository = assetRepository;
      this.messagingTemplate = messagingTemplate;
      this.scheduleManager = scheduleManager;
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
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

      if(principal != null) {
         RepletRegistry.getRegistry(pId)
            .removePropertyChangeListener(this.reportListener);
      }
   }

   @SubscribeMapping(COMMANDS_TOPIC)
   public void subscribeToTopic(Principal principal) throws Exception {
      this.principal = principal;
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

      if(principal != null) {
         RepletRegistry.getRegistry(pId)
            .addPropertyChangeListener(this.reportListener);
      }
   }

   private static final String COMMANDS_TOPIC = "/repository-changed";

   private final AssetRepository assetRepository;
   private final SimpMessagingTemplate messagingTemplate;
   private final ScheduleManager scheduleManager;
   private Principal principal;

   private final PropertyChangeListener reportListener = new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent event) {
         if(!RepletRegistry.EDIT_CYCLE_EVENT.equals(event.getPropertyName()) &&
            !RepletRegistry.CHANGE_EVENT.equals(event.getPropertyName()))
         {
            messagingTemplate
               .convertAndSendToUser(SUtil.getUserDestination(principal), COMMANDS_TOPIC, "");
         }
      }
   };
   private final PropertyChangeListener archiveListener = new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent event) {
         messagingTemplate
            .convertAndSendToUser(SUtil.getUserDestination(principal), COMMANDS_TOPIC, "");
      }
   };
   private final AssetChangeListener viewsheetListener = new AssetChangeListener() {
      @Override
      public void assetChanged(AssetChangeEvent event) {
         if(event.getChangeType() != AssetChangeEvent.ASSET_TO_BE_DELETED) {
            messagingTemplate
               .convertAndSendToUser(SUtil.getUserDestination(principal), COMMANDS_TOPIC, "");
         }
      }
   };
}
