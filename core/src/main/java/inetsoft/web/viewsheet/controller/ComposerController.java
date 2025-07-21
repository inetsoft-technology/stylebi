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

package inetsoft.web.viewsheet.controller;

import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.web.composer.command.OpenComposerAssetCommand;
import inetsoft.web.composer.vs.command.OpenComposerCommand;
import inetsoft.web.composer.vs.event.CreateQueryEventCommand;
import inetsoft.web.composer.vs.event.EditViewsheetEvent;
import inetsoft.web.portal.data.EditWorksheetEvent;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.ComposerClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

import static inetsoft.web.viewsheet.service.ComposerClientService.COMMANDS_TOPIC;

@Controller
public class ComposerController {
   @Autowired
   public ComposerController(SimpMessagingTemplate simpMessagingTemplate,
                             ComposerClientService composerClientService,
                             SecurityProvider securityProvider)
   {
      this.simpMessagingTemplate = simpMessagingTemplate;
      this.composerClientService = composerClientService;
      this.securityProvider = securityProvider;
   }

   @MessageMapping("/composer/editViewsheet")
   public void editViewsheet(@Payload EditViewsheetEvent event, Principal principal,
                             SimpMessageHeaderAccessor headerAccessor,
                             CommandDispatcher commandDispatcher)
   {
      String httpSessionId =
         headerAccessor.getSessionAttributes().get("HTTP.SESSION.ID").toString();
      String simpSessionId = composerClientService.getFirstSimpSessionId(httpSessionId);

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
      String simpSessionId = composerClientService.getFirstSimpSessionId(httpSessionId);
      boolean canWorksheet = securityProvider.checkPermission(
         principal, ResourceType.WORKSHEET, "*", ResourceAction.ACCESS);

      if(canWorksheet && simpSessionId == null) {
         OpenComposerCommand openComposerCommand = OpenComposerCommand.builder()
            .vsId(event.getWsId())
            .build();
         commandDispatcher.sendCommand(openComposerCommand);
         return;
      }

      MessageCommand command = new MessageCommand();
      command.setMessage(Catalog.getCatalog().getString(canWorksheet?
                         "composer.openWorksheetRepeatedly" : "composer.openWorksheetNoPermission"));
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
      String simpSessionId = composerClientService.getFirstSimpSessionId(httpSessionId);

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

   @MessageMapping(COMMANDS_TOPIC + "/leave")
   public void unsubscribe(StompHeaderAccessor stompHeaderAccessor) {
      composerClientService.removeFromSessionList(stompHeaderAccessor);
   }

   private final SimpMessagingTemplate simpMessagingTemplate;
   private final ComposerClientService composerClientService;
   private final SecurityProvider securityProvider;
}
