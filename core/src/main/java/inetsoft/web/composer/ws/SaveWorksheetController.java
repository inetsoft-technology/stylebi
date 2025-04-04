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

import inetsoft.util.*;
import inetsoft.web.composer.model.ws.SaveWSConfirmationModel;
import inetsoft.web.composer.ws.event.SaveSheetEvent;
import inetsoft.web.composer.ws.service.SaveWorksheetServiceProxy;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.command.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@Controller
public class SaveWorksheetController extends WorksheetController {
   @Autowired
   public SaveWorksheetController(SaveWorksheetServiceProxy saveWorksheetServiceProxy) {
      this.saveWorksheetServiceProxy = saveWorksheetServiceProxy;
   }

   @LoadingMask
   @MessageMapping("/composer/worksheet/save")
   public boolean saveWorksheet(@Payload SaveSheetEvent event,
      Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      return saveWorksheetServiceProxy.saveWorksheet(getRuntimeId(), event, principal, commandDispatcher);
   }

   @LoadingMask
   @MessageMapping("composer/worksheet/save-and-close")
   public void saveAndCloseWorksheet(@Payload SaveSheetEvent event,
      Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      if(this.saveWorksheet(event, principal, commandDispatcher)) {
         commandDispatcher.sendCommand(CloseSheetCommand.builder().build());
      }
   }

   @PostMapping("/api/composer/worksheet/check-primary-assembly/**")
   @ResponseBody
   public SaveWSConfirmationModel checkPrimaryAssembly(@RequestBody SaveSheetEvent event,
      @RemainingPath String runtimeId, Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return saveWorksheetServiceProxy.checkPrimaryAssembly(runtimeId, event, principal);
   }

   @GetMapping("/api/composer/worksheet/checkDependChanged")
   @ResponseBody
   public boolean checkDependChanged(@RequestParam("rid") String rid, Principal principal)
   {
    return saveWorksheetServiceProxy.checkDependChanged(rid, principal);
   }

   @GetMapping("/api/composer/worksheet/checkCycle")
   @ResponseBody
   public boolean checkCycle(@RequestParam("rid") String rid, Principal principal)
      throws Exception
   {
      rid = Tool.byteDecode(rid);
      return saveWorksheetServiceProxy.checkCycle(rid, principal);
   }

   private final SaveWorksheetServiceProxy saveWorksheetServiceProxy;
}
