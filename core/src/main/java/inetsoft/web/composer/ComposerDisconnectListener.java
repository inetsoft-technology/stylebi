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
package inetsoft.web.composer;

import inetsoft.web.viewsheet.service.RuntimeViewsheetManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.*;

import java.security.Principal;

@Component
public class ComposerDisconnectListener {
   @Autowired
   public ComposerDisconnectListener(RuntimeViewsheetManager runtimeViewsheetManager) {
      this.runtimeViewsheetManager = runtimeViewsheetManager;
   }

   /**
    * On websocket disconnect, attempts to clean up the socket's runtime sheet if it has
    * not been closed already.
    */
   @EventListener
   public void sessionDisconnected(SessionDisconnectEvent sessionDisconnectEvent) {
      Principal principal = sessionDisconnectEvent.getUser();
      runtimeViewsheetManager.sessionEnded(principal);
   }

   @EventListener
   public void sessionConnected(SessionConnectEvent event) {
      runtimeViewsheetManager.sessionConnected(event.getUser());
   }

   private final RuntimeViewsheetManager runtimeViewsheetManager;
}
