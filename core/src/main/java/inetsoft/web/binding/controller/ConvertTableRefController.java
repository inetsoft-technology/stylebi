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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.web.binding.event.ConvertTableRefEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ConvertTableRefController {
   /**
    * Creates a new instance of <tt>ConvertTableRefController</tt>.
    *
    */
   @Autowired
   public ConvertTableRefController(
                                    RuntimeViewsheetRef runtimeViewsheetRef,
                                    ConvertTableRefControllerServiceProxy convertTableRefservice,
                                    ViewsheetService viewsheetService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.convertTableRefservice = convertTableRefservice;
      this.viewsheetService = viewsheetService;
   }

   @MessageMapping("/vs/table/convertRef")
   public void convertTableRef(@Payload ConvertTableRefEvent event,
                               Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      convertTableRefservice.convertTableRef(id, event, principal, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ConvertTableRefControllerServiceProxy convertTableRefservice;
   public final ViewsheetService viewsheetService;
}
