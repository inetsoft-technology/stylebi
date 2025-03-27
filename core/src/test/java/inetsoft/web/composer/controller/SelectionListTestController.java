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
package inetsoft.web.composer.controller;

import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
public class SelectionListTestController {

   @Autowired
   public SelectionListTestController(SelectionListTestServiceProxy selectionListTestService)
   {
      this.selectionListTestService = selectionListTestService;
   }

   /**
    * Applies a new selection for a selection list.
    *
    * @param assemblyName the absolute name of the selection list assembly.
    * @param principal    a principal identifying the current user.
    *
    * @throws Exception if the selection could not be applied.
    */
   @RequestMapping(value="/test/vs/applySelection", method = RequestMethod.POST)
   @ResponseBody
   public String applySelection(@RequestParam("viewsheetId") String vsId,
      @RequestParam("assemblyName") String assemblyName,
      @RequestParam("type") String type,
      @RequestParam(value = "state", defaultValue = "0") Integer state,
      @RequestParam(value = "selectStart", defaultValue = "-1") Integer selectStart,
      @RequestParam(value = "selectEnd", defaultValue = "-1") Integer selectEnd,
      @RequestParam(value = "value", required = false) String value,
      Principal principal,
      @LinkUri String linkUri
      ) throws Exception
   {
      return selectionListTestService.applySelection(vsId, assemblyName, type, state, selectStart,
                                              selectEnd, value, principal, linkUri);
   }

   private final SelectionListTestServiceProxy selectionListTestService;
}
