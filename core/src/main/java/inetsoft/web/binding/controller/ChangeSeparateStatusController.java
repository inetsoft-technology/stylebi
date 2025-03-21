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
package inetsoft.web.binding.controller;

import inetsoft.web.binding.event.ChangeSeparateStatusEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChangeSeparateStatusController {
   /**
    * Creates a new instance of <tt>ChangeSeparateStatusController</tt>.
    *  @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @Autowired
   public ChangeSeparateStatusController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      ChangeSeparateStatusServiceProxy changeSeparateStatusService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.changeSeparateStatusService = changeSeparateStatusService;
   }

   @MessageMapping("/vs/chart/changeSeparateStatus")
   public void changeSeparateStatus(@Payload ChangeSeparateStatusEvent event,
                                    Principal principal, CommandDispatcher dispatcher,
                                    @LinkUri String linkUri) throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      changeSeparateStatusService.changeSeparateStatus(id, event, principal, dispatcher, linkUri);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ChangeSeparateStatusServiceProxy changeSeparateStatusService;
}
