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
import inetsoft.web.composer.model.ws.VariableAssemblyDialogModel;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

/**
 * Controller that provides the REST endpoints for the variable assembly dialog.
 *
 * @since 12.3
 */
@Controller
public class VariableAssemblyDialogController extends WorksheetController {

   public VariableAssemblyDialogController(VariableAssemblyDialogServiceProxy dialogServiceProxy)
   {
      this.dialogServiceProxy = dialogServiceProxy;
   }

   /**
    * Gets the top-level descriptor of the assembly.
    *
    * @param runtimeId the runtime identifier of the worksheet.
    *
    * @return the assembly descriptor.
    */
   @RequestMapping(
      value = "/api/composer/ws/variable-assembly-dialog-model/{runtimeid}",
      method = RequestMethod.GET)
   @ResponseBody
   public VariableAssemblyDialogModel getVariableAssemblyDialogModel(
      @PathVariable("runtimeid") String runtimeId,
      @RequestParam(value = "variable", required = false) String variableName,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return dialogServiceProxy.getVariableAssemblyDialogModel(runtimeId, variableName, principal);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/ws/dialog/variable-assembly-dialog-model")
   public void setVariableAssemblyProperties(
      @Payload VariableAssemblyDialogModel model,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      dialogServiceProxy.setVariableAssemblyProperties(super.getRuntimeId(), model,
                                                       principal, commandDispatcher);
   }

   private VariableAssemblyDialogServiceProxy dialogServiceProxy;
}
