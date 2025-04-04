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
package inetsoft.web.composer.ws;

import inetsoft.web.composer.ws.event.WSAssemblyEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller
public class UpdateMirrorController extends WorksheetController {

   public UpdateMirrorController(UpdateMirrorServiceProxy updateMirrorServiceProxy)
   {
      this.updateMirrorServiceProxy = updateMirrorServiceProxy;
   }

   @LoadingMask
   @MessageMapping("/composer/worksheet/update-mirror")
   public void updateMirror(
      @Payload WSAssemblyEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      updateMirrorServiceProxy.updateMirror(getRuntimeId(), event, principal, commandDispatcher);
   }

   private final UpdateMirrorServiceProxy updateMirrorServiceProxy;
}
