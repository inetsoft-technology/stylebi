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

import inetsoft.web.binding.event.GetVSObjectModelEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * This class handles get vsobjectmodel from the server.
 */
@Controller
public class VSViewController {
   /**
    * Creates a new instance of <tt>VSViewController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @Autowired
   public VSViewController(
           RuntimeViewsheetRef runtimeViewsheetRef,
           VSViewServiceProxy vsViewService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsViewService = vsViewService;
   }

   /**
    * This method is to get vsobjectmodel for edit binding pane.
    *
    */
   @MessageMapping(value="/vsview/object/model")
   public void getModel(@Payload GetVSObjectModelEvent event, Principal principal,
      CommandDispatcher dispatcher) throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      vsViewService.getModel(id, event, principal, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSViewServiceProxy vsViewService;
}
