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

import inetsoft.web.composer.ws.event.WSRemoveAssembliesEvent;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@Controller
public class WSRemoveAssembliesController extends WorksheetController {

   public WSRemoveAssembliesController(WSRemoveAssembliesServiceProxy serviceProxy)
   {
      this.serviceProxy = serviceProxy;
   }

   @PostMapping("/api/composer/worksheet/remove-assemblies/check-dependency/**")
   @ResponseBody
   public String hasSourceDependency(@RemainingPath String rid,
                                     @RequestParam(value = "all", required = false) boolean all,
                                     @RequestBody WSRemoveAssembliesEvent event,
                                     Principal principal) throws Exception
   {
      return serviceProxy.hasSourceDependency(rid, all, event, principal);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/remove-assemblies")
   public void removeAssemblies(
      @Payload WSRemoveAssembliesEvent event,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      serviceProxy.removeAssemblies(getRuntimeId(), event, principal, commandDispatcher);
   }

   private final WSRemoveAssembliesServiceProxy serviceProxy;
}
