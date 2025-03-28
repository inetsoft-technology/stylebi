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
package inetsoft.web.composer.vs.dialog;

import inetsoft.util.*;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.command.CloseSheetCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@Controller
public class SaveViewsheetDialogController {
   @Autowired
   public SaveViewsheetDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                        SaveViewsheetDialogService saveViewsheetDialogService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.saveViewsheetDialogService = saveViewsheetDialogService;
   }

   @RequestMapping(
      value = "/api/composer/vs/save-viewsheet-dialog-model/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public SaveViewsheetDialogModel getSaveViewsheetInfo(
      @RemainingPath String runtimeId, Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return saveViewsheetDialogService.getSaveViewsheetInfo(runtimeId, principal);
   }

   @RequestMapping(
      value = "/api/composer/vs/save-viewsheet-dialog-model/{runtimeId}",
      method = RequestMethod.POST)
   @ResponseBody
   public SaveViewsheetDialogModelValidator validateSaveViewSheet(
      @PathVariable("runtimeId") String runtimeId,
      @RequestBody SaveViewsheetDialogModel model, Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return saveViewsheetDialogService.validateSaveViewSheet(runtimeId, model, principal);
   }

   @MessageMapping("/composer/vs/save-viewsheet-dialog-model")
   public void saveViewsheet(
      @Payload SaveViewsheetDialogModel model, @LinkUri String linkUri,
      Principal principal, CommandDispatcher commandDispatcher)
      throws Exception
   {
      saveViewsheetDialogService.saveViewsheet(runtimeViewsheetRef.getRuntimeId(), model,
                                               linkUri, principal, commandDispatcher);
   }

   @LoadingMask
   @MessageMapping("/composer/vs/save-viewsheet-dialog-model/save-and-close")
   public void saveAndClose(
      @Payload SaveViewsheetDialogModel model, @LinkUri String linkUri,
      Principal principal, CommandDispatcher commandDispatcher)
      throws Exception
   {
      saveViewsheet(model, linkUri, principal, commandDispatcher);
      commandDispatcher.sendCommand(CloseSheetCommand.builder().build());
   }

   @GetMapping("/api/composer/viewsheet/recycleAutoSave")
   @ResponseBody
   public void recycleAutoSave(Principal user) {
      AutoSaveUtils.recycleUserAutoSave(user);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final SaveViewsheetDialogService saveViewsheetDialogService;
}
