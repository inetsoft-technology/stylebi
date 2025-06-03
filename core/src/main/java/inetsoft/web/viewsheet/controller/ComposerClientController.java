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

import inetsoft.web.viewsheet.service.ComposerClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.*;

import static inetsoft.web.viewsheet.service.ComposerClientService.COMMANDS_TOPIC;

@Controller
public class ComposerClientController {
   @Autowired
   public ComposerClientController(ComposerClientService composerClientService) {
      this.composerClientService = composerClientService;
   }

   @SubscribeMapping(COMMANDS_TOPIC)
   public void subscribe(SimpMessageHeaderAccessor headerAccessor) {
      composerClientService.setSessionID(() -> new String[]{
         headerAccessor.getSessionAttributes().get("HTTP.SESSION.ID").toString(),
         headerAccessor.getSessionId()
      });
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
      Message<byte[]> message = event.getMessage();
      StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
      composerClientService.removeFromSessionList(headers);
   }

   private final ComposerClientService composerClientService;
}
