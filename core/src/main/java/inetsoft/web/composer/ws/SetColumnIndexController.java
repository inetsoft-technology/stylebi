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

import inetsoft.web.composer.ws.event.WSSetColumnIndexEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller
public class SetColumnIndexController extends WorksheetController {

   public SetColumnIndexController(SetColumnIndexServiceProxy serviceProxy)
   {
      this.serviceProxy = serviceProxy;
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/set-column-index")
   public void setColumnIndex(@Payload WSSetColumnIndexEvent event, Principal principal,
                              CommandDispatcher commandDispatcher) throws Exception
   {
      serviceProxy.setColumnIndex(getRuntimeId(), event, principal, commandDispatcher);
   }

   private SetColumnIndexServiceProxy serviceProxy;
}
