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

import inetsoft.util.Tool;
import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.binding.event.ModifyAggregateFieldEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
public class ModifyAggregateFieldController {
   /**
    * Creates a new instance of <tt>ModifyAggregateFieldController</tt>.
    */
   @Autowired
   public ModifyAggregateFieldController(ModifyAggregateFieldServiceProxy modifyAggregateFieldService) {
      this.modifyAggregateFieldService = modifyAggregateFieldService;
   }

   @PostMapping("/api/vs/calculate/modifyAggregateField")
   @ResponseBody
   public void modifyAggregateField(@DecodeParam("vsId") String id,
                                    @RequestBody ModifyAggregateFieldEvent event,
                                    Principal principal) throws Exception
   {
      if(id == null) {
         return;
      }

      modifyAggregateFieldService.modifyAggregateField(id, event, principal);
   }

   @PostMapping("/api/vs/calculate/removeAggregateField")
   @ResponseBody
   public void removeAggregateField(@DecodeParam("vsId") String vsId,
                                    @RequestBody ModifyAggregateFieldEvent event,
                                    Principal principal)
      throws Exception
   {

     String id = Tool.byteDecode(vsId);

      if(id == null) {
         return;
      }

      modifyAggregateFieldService.removeAggregateField(id, event, principal);
   }

   private final ModifyAggregateFieldServiceProxy modifyAggregateFieldService;
}
