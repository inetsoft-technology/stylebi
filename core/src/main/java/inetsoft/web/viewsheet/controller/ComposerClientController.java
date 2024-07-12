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
package inetsoft.web.viewsheet.controller;

import inetsoft.util.Catalog;
import inetsoft.web.composer.command.OpenComposerAssetCommand;
import inetsoft.web.composer.vs.command.OpenComposerCommand;
import inetsoft.web.composer.vs.event.CreateQueryEventCommand;
import inetsoft.web.composer.vs.event.EditViewsheetEvent;
import inetsoft.web.portal.data.EditWorksheetEvent;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Controller
@Scope(value = "websocket", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ComposerClientController {
   @Autowired
   public ComposerClientController(SimpMessagingTemplate simpMessagingTemplate) {
      this.simpMessagingTemplate = simpMessagingTemplate;
   }

   @MessageMapping("/composer/editViewsheet")
   public void editViewsheet(@Payload EditViewsheetEvent event, Principal principal,
                             SimpMessageHeaderAccessor headerAccessor,
                             CommandDispatcher commandDispatcher)
   {
      String httpSessionId =
         headerAccessor.getSessionAttributes().get("HTTP.SESSION.ID").toString();
      String simpSessionId = getFirstSimpSessionId(httpSessionId);

      if(simpSessionId == null) {
         OpenComposerCommand openComposerCommand = OpenComposerCommand.builder()
            .vsId(event.getVsId())
            .build();
         commandDispatcher.sendCommand(openComposerCommand);
         return;
      }

      MessageCommand command = new MessageCommand();
      command.setMessage(Catalog.getCatalog().getString("composer.openDashboardRepeatedly"));
      commandDispatcher.sendCommand(command);
      SimpMessageHeaderAccessor msgHeaderAccessor = SimpMessageHeaderAccessor
         .create(SimpMessageType.MESSAGE);
      msgHeaderAccessor.setSessionId(simpSessionId);
      msgHeaderAccessor.setLeaveMutable(true);
      OpenComposerAssetCommand composerAssetCommand = OpenComposerAssetCommand.builder()
         .assetId(event.getVsId())
         .viewsheet(true)
         .build();
      simpMessagingTemplate
         .convertAndSendToUser(simpSessionId, COMMANDS_TOPIC, composerAssetCommand,
            msgHeaderAccessor.getMessageHeaders());
   }

   @MessageMapping("/composer/editWorksheet")
   public void editWorksheet(@Payload EditWorksheetEvent event, Principal principal,
                             SimpMessageHeaderAccessor headerAccessor,
                             CommandDispatcher commandDispatcher)
   {
      String httpSessionId =
         headerAccessor.getSessionAttributes().get("HTTP.SESSION.ID").toString();
      String simpSessionId = getFirstSimpSessionId(httpSessionId);

      if(simpSessionId == null) {
         OpenComposerCommand openComposerCommand = OpenComposerCommand.builder()
            .vsId(event.getWsId())
            .build();
         commandDispatcher.sendCommand(openComposerCommand);
         return;
      }

      MessageCommand command = new MessageCommand();
      command.setMessage(Catalog.getCatalog().getString("composer.openWorksheetRepeatedly"));
      commandDispatcher.sendCommand(command);
      SimpMessageHeaderAccessor msgHeaderAccessor = SimpMessageHeaderAccessor
         .create(SimpMessageType.MESSAGE);
      msgHeaderAccessor.setSessionId(simpSessionId);
      msgHeaderAccessor.setLeaveMutable(true);
      OpenComposerAssetCommand composerAssetCommand = OpenComposerAssetCommand.builder()
         .assetId(event.getWsId())
         .viewsheet(false)
         .build();
      simpMessagingTemplate
         .convertAndSendToUser(simpSessionId, COMMANDS_TOPIC, composerAssetCommand,
                               msgHeaderAccessor.getMessageHeaders());
   }

   @MessageMapping("/composer/ws/query/create")
   public void createQuery(@Payload CreateQueryEventCommand event, Principal principal,
                           SimpMessageHeaderAccessor headerAccessor,
                           CommandDispatcher commandDispatcher)
   {
      String httpSessionId =
         headerAccessor.getSessionAttributes().get("HTTP.SESSION.ID").toString();
      String simpSessionId = getFirstSimpSessionId(httpSessionId);

      if(simpSessionId == null) {
         commandDispatcher.sendCommand(event);
         return;
      }

      MessageCommand command = new MessageCommand();
      command.setMessage(Catalog.getCatalog().getString("composer.createQueryRepeatedly"));
      commandDispatcher.sendCommand(command);

      SimpMessageHeaderAccessor msgHeaderAccessor = SimpMessageHeaderAccessor
         .create(SimpMessageType.MESSAGE);
      msgHeaderAccessor.setSessionId(simpSessionId);
      msgHeaderAccessor.setLeaveMutable(true);
      OpenComposerAssetCommand composerAssetCommand = OpenComposerAssetCommand.builder()
         .baseDataSource(event.getBaseDataSource())
         .baseDataSourceType(event.getBaseDataSourceType())
         .viewsheet(false)
         .wsWizard(true)
         .build();
      simpMessagingTemplate
         .convertAndSendToUser(simpSessionId, COMMANDS_TOPIC, composerAssetCommand,
            msgHeaderAccessor.getMessageHeaders());
   }

   @PreDestroy
   public void preDestroy() {
      removeFromSessionList();
   }

   @MessageMapping(COMMANDS_TOPIC + "/leave")
   public void unsubscribe() {
      removeFromSessionList();
   }

   @SubscribeMapping(COMMANDS_TOPIC)
   public void subscribe(SimpMessageHeaderAccessor headerAccessor) {
      LOCK.lock();

      try {
         httpSessionId = headerAccessor.getSessionAttributes().get("HTTP.SESSION.ID")
            .toString();
         simpSessionId = headerAccessor.getSessionId();
         List<String> simpSessionIdList =
            COMPOSER_CLIENTS.computeIfAbsent(httpSessionId, k -> new ArrayList<>());
         simpSessionIdList.add(simpSessionId);
      }
      finally {
         LOCK.unlock();
      }
   }

   private void removeFromSessionList() {
      LOCK.lock();

      try {
         List<String> simpSessionIdList = COMPOSER_CLIENTS.get(httpSessionId);

         if(simpSessionIdList != null) {
            simpSessionIdList.remove(simpSessionId);
         }
      }
      finally {
         LOCK.unlock();
      }
   }

   public static String getFirstSimpSessionId(String httpSessionId) {
      LOCK.lock();

      try {
         List<String> simpSessionIdList = COMPOSER_CLIENTS.get(httpSessionId);
         String simpSessionId = simpSessionIdList != null && simpSessionIdList
            .size() > 0 ? simpSessionIdList.get(0) : null;
         return simpSessionId;
      }
      finally {
         LOCK.unlock();
      }
   }

   private final SimpMessagingTemplate simpMessagingTemplate;
   private String httpSessionId;
   private String simpSessionId;

   private static final String COMMANDS_TOPIC = "/composer-client";
   // key = http session id, value = list of simpSessionIds
   private static final Map<String, List<String>> COMPOSER_CLIENTS = new HashMap<>();
   private static final Lock LOCK = new ReentrantLock();
}
