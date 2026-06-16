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
package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.web.composer.ws.command.RefreshWorksheetCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CommandDispatcherService;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
public class WorksheetAgentBroadcastService {
   private final CommandDispatcherService dispatcher;

   public WorksheetAgentBroadcastService(CommandDispatcherService dispatcher) {
      this.dispatcher = dispatcher;
   }

   /** Push a refresh to the browser that owns this runtime so its open composer re-renders. */
   public void broadcastRefresh(RuntimeWorksheet rws, String runtimeId, Principal owner) {
      String sessionId = rws.getSocketSessionId();

      if(sessionId == null) {
         return;   // browser not attached; nothing to refresh
      }

      // Mirror SharedFilterService: target the owner's STOMP session with a runtime-id
      // header. Intentionally OMIT inetsoftClientId so the Angular client treats the frame
      // as a broadcast and re-renders.
      SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create();
      headers.setSessionId(sessionId);
      headers.setLeaveMutable(true);
      headers.setNativeHeader(CommandDispatcher.RUNTIME_ID_ATTR, runtimeId);

      dispatcher.convertAndSendToUser(owner.getName(), CommandDispatcher.COMMANDS_TOPIC,
                                      RefreshWorksheetCommand.builder().build(),
                                      headers.getMessageHeaders());
   }
}
