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

import inetsoft.web.composer.model.ws.CheckModelTrapModel;
import inetsoft.web.composer.ws.event.CheckModelTrapEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
public class CheckModelTrapController extends WorksheetController {
   @Autowired
   public CheckModelTrapController(CheckModelTrapServiceProxy checkModelTrapService) {
      this.checkModelTrapService = checkModelTrapService;
   }

   /**
    * From {@link inetsoft.report.composition.event.CheckModelTrapEvent}
    */
   @RequestMapping(
      value = "api/composer/worksheet/check-model-trap/{runtimeId}",
      method = RequestMethod.POST)
   @ResponseBody
   public CheckModelTrapModel checkModelTrap(
      @RequestBody CheckModelTrapEvent event, @PathVariable("runtimeId") String runtimeId,
      Principal principal) throws Exception
   {
      return checkModelTrapService.checkModelTrap(runtimeId, event, principal);
   }

   private final CheckModelTrapServiceProxy checkModelTrapService;
}
