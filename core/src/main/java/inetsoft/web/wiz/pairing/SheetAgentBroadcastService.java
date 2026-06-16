/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.pairing;

import inetsoft.report.composition.RuntimeSheet;
import inetsoft.web.composer.ws.command.RefreshWorksheetCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CommandDispatcherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
public class SheetAgentBroadcastService {
   private static final Logger LOG = LoggerFactory.getLogger(SheetAgentBroadcastService.class);

   @Autowired
   public SheetAgentBroadcastService(CommandDispatcherService commandDispatcherService) {
      this.commandDispatcherService = commandDispatcherService;
   }

   /**
    * Push a refresh to the browser that owns this runtime so its open composer re-renders.
    * Omits inetsoftClientId intentionally — Angular's broadcast-accept clause re-renders
    * when inetsoftClientId is absent.
    *
    * @param rs        the runtime sheet (supplies socketSessionId + socketUserName)
    * @param sheetType which refresh command to send
    * @param runtimeId the sheet runtime ID (used as the RUNTIME_ID_ATTR header value)
    * @param owner     the agent principal (used as a fallback user name if socketUserName is null)
    */
   public void broadcastRefresh(RuntimeSheet rs, SheetType sheetType, String runtimeId,
                                Principal owner)
   {
      String sessionId = rs.getSocketSessionId();

      if(sessionId == null) {
         LOG.debug("Pairing broadcast skipped — no socket session recorded (runtimeId={})", runtimeId);
         return;
      }

      String user = rs.getSocketUserName();

      if(user == null) {
         user = owner == null ? null : owner.getName();
      }

      SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create();
      headers.setSessionId(sessionId);
      headers.setLeaveMutable(true);
      headers.setNativeHeader(CommandDispatcher.RUNTIME_ID_ATTR, runtimeId);
      // deliberately NO inetsoftClientId -> Angular re-renders on broadcast

      Object command = buildCommand(sheetType);
      commandDispatcherService.convertAndSendToUser(
         user, CommandDispatcher.COMMANDS_TOPIC, command, headers.getMessageHeaders());

      LOG.info("Pairing broadcast refresh sent (sheetType={}, runtimeId={}, sessionId={})",
               sheetType, runtimeId, sessionId);
   }

   private Object buildCommand(SheetType sheetType) {
      return switch(sheetType) {
         case WORKSHEET -> RefreshWorksheetCommand.builder().build();
         case VIEWSHEET -> buildViewsheetRefreshCommand();
      };
   }

   private Object buildViewsheetRefreshCommand() {
      // TODO: replace with the correct viewsheet refresh command once identified.
      // No dedicated RefreshViewsheetCommand exists in the codebase yet; using
      // RefreshWorksheetCommand as a placeholder until it is added.
      return RefreshWorksheetCommand.builder().build();
   }

   private final CommandDispatcherService commandDispatcherService;
}
