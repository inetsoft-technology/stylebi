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

import inetsoft.util.*;
import inetsoft.web.composer.model.ws.*;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.viewsheet.HandleAssetExceptions;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.command.CloseSheetCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@Controller
public class SaveWorksheetDialogController extends WorksheetController {

   public SaveWorksheetDialogController(SaveWorksheetDialogServiceProxy dialogServiceProxy)
   {
      this.dialogServiceProxy = dialogServiceProxy;
   }

   @GetMapping("api/composer/ws/dialog/save-worksheet-dialog-model/{runtimeId}")
   @ResponseBody
   public SaveWorksheetDialogModel getSaveWorksheetInfo(
      @PathVariable("runtimeId") String runtimeId, Principal principal) throws Exception
   {
      return dialogServiceProxy.getSaveWorksheetInfo(runtimeId, principal);
   }

   @PostMapping("api/composer/ws/dialog/save-worksheet-dialog-model/{runtimeId}")
   @ResponseBody
   public SaveWorksheetDialogModelValidator validateSaveWorksheet(
      @PathVariable("runtimeId") String runtimeId,
      @RequestBody SaveWorksheetDialogModel model, Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return dialogServiceProxy.validateSaveWorksheet(runtimeId, model, principal);
   }

   @LoadingMask
   @MessageMapping("/composer/ws/dialog/save-worksheet-dialog-model/")
   @HandleAssetExceptions
   public void saveWorksheet(
      @Payload SaveWorksheetDialogModel model, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      dialogServiceProxy.saveWorksheet(getRuntimeId(), model, principal, commandDispatcher);
   }

   @LoadingMask
   @MessageMapping("/composer/ws/dialog/save-worksheet-dialog-model/save-and-close")
   public void saveAndCloseWorksheet(
      @Payload SaveWorksheetDialogModel model, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      this.saveWorksheet(model,principal,commandDispatcher);
      commandDispatcher.sendCommand(CloseSheetCommand.builder().build());
   }

   private SaveWorksheetDialogServiceProxy dialogServiceProxy;
}
