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

import inetsoft.web.binding.drm.CalculateRefModel;
import inetsoft.web.binding.event.ModifyCalculateFieldEvent;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
public class ModifyCalculateFieldController {
   /**
    * Creates a new instance of <tt>ModifyCalculateFieldController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @Autowired
   public ModifyCalculateFieldController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      ModifyCalculateFieldServiceProxy modifyCalculateFieldService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.modifyCalculateFieldService = modifyCalculateFieldService;
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/vs/calculate/modifyCalculateField")
   public void modifyCalculateField(@Payload ModifyCalculateFieldEvent event,
      Principal principal, CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      modifyCalculateFieldService.modifyCalculateField(id, event, principal, dispatcher, linkUri);
   }

   @RequestMapping(
      value = "/api/vs/calculate/get-in-use-assemblies/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public String getInUseAssemblies(@RemainingPath String runtimeId,
                                    @RequestParam("tname") String tname,
                                    @RequestParam("refname") String refname,
                                    Principal principal)
      throws Exception
   {

      return modifyCalculateFieldService.getInUseAssemblies(runtimeId, tname, refname, principal);
   }

   @RequestMapping(
      value = "/api/vs/calculate/checkCalcTrap",
      method = RequestMethod.PUT
   )
   @ResponseBody
   public String checkTrap(@RequestParam("runtimeId") String runtimeId,
                           @RequestParam("tname") String tname,
                           @RequestParam("refname") String refname,
                           @RequestParam("create") String create,
                           @RequestBody(required = false) CalculateRefModel calc,
                           Principal principal)
      throws Exception
   {
      return modifyCalculateFieldService.checkTrap(runtimeId, tname, refname, create, calc, principal);
   }


   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ModifyCalculateFieldServiceProxy modifyCalculateFieldService;
}
