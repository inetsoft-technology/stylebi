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
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.assembly.VariableAssemblyModelInfo;
import inetsoft.web.composer.ws.event.WSCollectVariablesOverEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@Controller
public class VariableInputDialogController extends WorksheetController {

   public VariableInputDialogController(VariableInputDialogServiceProxy dialogServiceProxy) {
      this.dialogServiceProxy = dialogServiceProxy;
   }

   @GetMapping("/api/composer/ws/dialog/variable-input-dialog-model/{runtimeid}")
   @ResponseBody
   public List<VariableAssemblyModelInfo> getModel(
      @PathVariable("runtimeid") String runtimeId,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return dialogServiceProxy.getModel(runtimeId, principal);
   }

   @MessageMapping("ws/dialog/variable-restore")
   public void restoreVariable(Principal principal) throws Exception {
      dialogServiceProxy.restoreVariable(super.getRuntimeId(), principal);
   }

   /**
    * From 12.2 CollectVariablesOverEvent.
    */
   @LoadingMask
   @MessageMapping("/ws/dialog/variable-input-dialog")
   public void initVariableInfos(
      @Payload WSCollectVariablesOverEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      dialogServiceProxy.initVariableInfos(super.getRuntimeId(), event, principal, commandDispatcher);
   }

   private final VariableInputDialogServiceProxy dialogServiceProxy;
}
