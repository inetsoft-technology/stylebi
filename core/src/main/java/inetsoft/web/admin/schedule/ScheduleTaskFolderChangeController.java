/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.web.admin.schedule;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.uql.asset.*;
import inetsoft.util.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.concurrent.TimeUnit;

@Controller
@Scope(value = "websocket", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ScheduleTaskFolderChangeController {
   @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
   @Autowired
   public ScheduleTaskFolderChangeController(AssetRepository assetRepository,
                                             SimpMessagingTemplate messagingTemplate)
   {
      this.assetRepository = assetRepository;
      this.messagingTemplate = messagingTemplate;
      this.debouncer = new DefaultDebouncer<>();
   }

   @PostConstruct
   public void initCluster() {
      cluster = Cluster.getInstance();
   }

   @PreDestroy
   public synchronized void removeListener() {
      if(listener != null) {
         cluster.removeMessageListener(listener);
         listener = null;
      }

      if(assetListener != null) {
         assetRepository.removeAssetChangeListener(this.assetListener);
      }
   }

   @SubscribeMapping(FOLDER_TOPIC)
   public synchronized void subscribeFolderChange(Principal principal) {
      if(assetListener == null) {
         assetListener = this::portalFolderChanged;
         subscriber = principal;
         assetRepository.addAssetChangeListener(assetListener);
      }
   }

   @SubscribeMapping(EM_FOLDER_TOPIC)
   public synchronized void subscribeEmFolderChange(Principal principal) {
      if(assetListener == null) {
         assetListener = this::emFolderChanged;
         subscriber = principal;
         assetRepository.addAssetChangeListener(assetListener);
      }
   }

   private void portalFolderChanged(AssetChangeEvent event) {
      folderChanged(event, true);
   }

   private void emFolderChanged(AssetChangeEvent event) {
      folderChanged(event, false);
   }

   private void folderChanged(AssetChangeEvent event, boolean portal) {
      if(event.getChangeType() != AssetChangeEvent.ASSET_TO_BE_DELETED &&
         event.getAssetEntry() != null && event.getAssetEntry().isScheduleTaskFolder())
      {
         debouncer.debounce("task_folder_change", 1L, TimeUnit.SECONDS,
            ()-> messagingTemplate.convertAndSendToUser(SUtil.getUserDestination(subscriber),
               portal ? FOLDER_TOPIC : EM_FOLDER_TOPIC, ""));
      }
   }

   private Cluster cluster;
   private MessageListener listener;
   private Principal subscriber;
   private AssetChangeListener assetListener;
   private final AssetRepository assetRepository;
   private final SimpMessagingTemplate messagingTemplate;
   private final Debouncer<String> debouncer;

   private static final String EM_FOLDER_TOPIC = "/em-schedule-folder-changed";
   private static final String FOLDER_TOPIC = "/schedule-folder-changed";
}
