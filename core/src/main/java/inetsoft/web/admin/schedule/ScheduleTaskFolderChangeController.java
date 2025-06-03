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
import inetsoft.uql.asset.*;
import inetsoft.util.Debouncer;
import inetsoft.util.DefaultDebouncer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Controller
public class ScheduleTaskFolderChangeController {
   @Autowired
   public ScheduleTaskFolderChangeController(AssetRepository assetRepository,
                                             SimpMessagingTemplate messagingTemplate)
   {
      this.assetRepository = assetRepository;
      this.messagingTemplate = messagingTemplate;
      this.debouncer = new DefaultDebouncer<>();
   }

   @PostConstruct
   public void addListener() {
      assetRepository.addAssetChangeListener(assetListener);
   }

   @PreDestroy
   public synchronized void removeListener() {
      assetRepository.removeAssetChangeListener(this.assetListener);
   }

   @SubscribeMapping(FOLDER_TOPIC)
   public synchronized void subscribeFolderChange(StompHeaderAccessor header, Principal principal) {
      final MessageHeaders messageHeaders = header.getMessageHeaders();
      final String subscriptionId =
         (String) messageHeaders.get(SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER);
      folderSubscriptions.put(subscriptionId, principal);
   }

   @SubscribeMapping(EM_FOLDER_TOPIC)
   public synchronized void subscribeEmFolderChange(StompHeaderAccessor header, Principal principal) {
      final MessageHeaders messageHeaders = header.getMessageHeaders();
      final String subscriptionId =
         (String) messageHeaders.get(SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER);
      emFolderSubscriptions.put(subscriptionId, principal);
   }

   private void folderChanged(AssetChangeEvent event) {
      if(event.getChangeType() != AssetChangeEvent.ASSET_TO_BE_DELETED &&
         event.getAssetEntry() != null && event.getAssetEntry().isScheduleTaskFolder()) {
         debouncer.debounce(
            "task_folder_change", 1L, TimeUnit.SECONDS, () -> sendFolderChanged(true));
         debouncer.debounce(
            "em_task_folder_change", 1L, TimeUnit.SECONDS, () -> sendFolderChanged(false));
      }
   }

   private void sendFolderChanged(boolean portal) {
      Collection<Principal> subscribers;
      String topic;

      if(portal) {
         subscribers = folderSubscriptions.values();
         topic = FOLDER_TOPIC;
      }
      else {
         subscribers = emFolderSubscriptions.values();
         topic = EM_FOLDER_TOPIC;
      }

      for(Principal subscriber : subscribers) {
         messagingTemplate.convertAndSendToUser(SUtil.getUserDestination(subscriber), topic, "");
      }
   }

   private final Map<String, Principal> folderSubscriptions = new ConcurrentHashMap<>();
   private final Map<String, Principal> emFolderSubscriptions = new ConcurrentHashMap<>();
   private final AssetChangeListener assetListener = this::folderChanged;
   private final AssetRepository assetRepository;
   private final SimpMessagingTemplate messagingTemplate;
   private final Debouncer<String> debouncer;

   private static final String EM_FOLDER_TOPIC = "/em-schedule-folder-changed";
   private static final String FOLDER_TOPIC = "/schedule-folder-changed";
}
