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

import inetsoft.web.binding.event.ApplyVSAssemblyInfoEvent;
import inetsoft.web.binding.event.RefreshVSBindingEvent;
import inetsoft.web.viewsheet.model.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class VSBindingModelController {
   /**
    * Creates a new instance of <tt>ViewsheetBindingController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @Autowired
   public VSBindingModelController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      VSBindingModelServiceProxy vsBindingModelService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsBindingModelService = vsBindingModelService;
   }

   @MessageMapping("/vs/binding/getbinding")
   public void getBinding(@Payload RefreshVSBindingEvent event,
      Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      vsBindingModelService.getBinding(id, event, principal, dispatcher);
   }

   @MessageMapping("/vs/binding/setbinding")
   public void setBinding(@Payload ApplyVSAssemblyInfoEvent event,
      Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      vsBindingModelService.setBinding(id, event, principal, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSBindingModelServiceProxy vsBindingModelService;
}
