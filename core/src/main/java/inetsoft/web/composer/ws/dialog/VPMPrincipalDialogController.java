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
package inetsoft.web.composer.ws.dialog;

import inetsoft.util.Tool;
import inetsoft.web.composer.model.ws.VPMPrincipalDialogModel;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
public class VPMPrincipalDialogController extends WorksheetController {
   @Autowired
   public VPMPrincipalDialogController(VPMPrincipalDialogServiceProxy dialogServiceProxy)
   {
      this.dialogServiceProxy = dialogServiceProxy;
   }

   @GetMapping("api/composer/ws/dialog/vpm-principal-dialog/{runtimeId}")
   @ResponseBody
   public VPMPrincipalDialogModel getVPMPrincipalModel(
      @PathVariable("runtimeId") String runtimeId, Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return dialogServiceProxy.getVPMPrincipalModel(runtimeId, principal);
   }

   @LoadingMask
   @Undoable
   @InitWSExecution
   @MessageMapping("/composer/ws/dialog/vpm-principal-dialog")
   public void setVPMPrincipalModel(
      @Payload VPMPrincipalDialogModel model, CommandDispatcher commandDispatcher,
      Principal principal) throws Exception
   {
      if(!isVpmSelectable()) {
         return;
      }

      dialogServiceProxy.setVPMPrincipalModel(super.getRuntimeId(), model,
                                              commandDispatcher, principal);
   }

   private boolean isVpmSelectable() {
      return false;
   }

   private final VPMPrincipalDialogServiceProxy dialogServiceProxy;
}
