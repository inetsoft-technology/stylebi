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
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import static inetsoft.web.viewsheet.service.ComposerClientService.COMMANDS_TOPIC;

@Controller
@Scope(value = "websocket", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ComposerClientController {
   @Autowired
   public ComposerClientController(ComposerClientService composerClientService) {
      this.composerClientService = composerClientService;
   }

   @SubscribeMapping(COMMANDS_TOPIC)
   public void subscribe(SimpMessageHeaderAccessor headerAccessor) {
      composerClientService.setSessionID(() -> new String[] {
         headerAccessor.getSessionAttributes().get("HTTP.SESSION.ID").toString(),
         headerAccessor.getSessionId()
      });
   }

   private final ComposerClientService composerClientService;
}
