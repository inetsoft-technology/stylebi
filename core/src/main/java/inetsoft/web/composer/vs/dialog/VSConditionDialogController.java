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

import inetsoft.util.Tool;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.BrowseDataModel;
import inetsoft.web.composer.model.vs.VSConditionDialogModel;
import inetsoft.web.composer.ws.assembly.ConditionTrapModel;
import inetsoft.web.composer.ws.assembly.ConditionTrapValidator;
import inetsoft.web.viewsheet.HandleAssetExceptions;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

/**
 * Controller that provides the REST endpoints for the vs condition dialog.
 *
 * @since 12.3
 */
@Controller
public class VSConditionDialogController {
   @Autowired
   public VSConditionDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                      VSConditionDialogServiceProxy vsConditionDialogServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsConditionDialogServiceProxy = vsConditionDialogServiceProxy;
   }

   /**
    * Gets the model of the assembly condition dialog
    *
    * @param runtimeId the runtime identifier of the worksheet.
    * @return the model object.
    */
   @RequestMapping(
      value = "/api/composer/vs/vs-condition-dialog-model",
      method = RequestMethod.GET)
   @ResponseBody
   public VSConditionDialogModel getModel(@RequestParam("runtimeId") String runtimeId,
      @RequestParam("assemblyName") String assemblyName,
      Principal principal) throws Exception
   {
      return vsConditionDialogServiceProxy.getModel(runtimeId, assemblyName, principal);
   }

   /**
    * Sets the model of the vs condition dialog
    *
    * @param assemblyName the identifier of the assembly.
    * @param model        the model of the vs condition dialog.
    */
   @Undoable
   @HandleAssetExceptions
   @MessageMapping("/composer/vs/vs-condition-dialog-model/{assemblyName}")
   public void setModel(@DestinationVariable("assemblyName") String assemblyName,
      @Payload VSConditionDialogModel model, @LinkUri String linkUri,
      Principal principal, CommandDispatcher commandDispatcher)
      throws Exception
   {
      vsConditionDialogServiceProxy.setModel(runtimeViewsheetRef.getRuntimeId(), assemblyName,
                                             model, linkUri, principal, commandDispatcher);
   }

   /**
    * Gets the available date ranges for the given viewsheet
    *
    * @param runtimeId the runtime identifier of the viewsheet.
    * @return the names of the date ranges.
    */
   @RequestMapping(
      value = "/api/composer/vs/vs-condition-dialog/date-ranges",
      method = RequestMethod.GET
   )
   @ResponseBody
   public BrowseDataModel getDateRanges(
      @RequestParam("runtimeId") String runtimeId, Principal principal)
      throws Exception
   {
      return vsConditionDialogServiceProxy.getDateRanges(runtimeId, principal);
   }

   /**
    * Browses the available data for the given data ref
    *
    * @param runtimeId    the runtime identifier of the viewsheet.
    * @param dataRefModel the model of the vs condition dialog.
    * @return the updated model.
    */
   @RequestMapping(
      value = "/api/composer/vs/vs-condition-dialog/browse-data",
      method = RequestMethod.POST
   )
   @ResponseBody
   public BrowseDataModel browseData(
      @RequestParam("runtimeId") String runtimeId,
      @RequestParam("tableName") String tableName,
      @RequestParam("assemblyName") String assemblyName,
      @RequestParam(value = "highlight", required = false) Boolean highlight,
      @RequestBody DataRefModel dataRefModel,
      Principal principal) throws Exception
   {
      return vsConditionDialogServiceProxy.browseData(runtimeId, tableName, assemblyName,
                                                      highlight, dataRefModel, principal);
   }

   /**
    * Checks whether a new condition list will cause a trap.
    * Also finds the trap-causing columns for a given condition list.
    *
    * @param model     the model containing the old and new condition lists
    * @param runtimeId the runtime id
    * @param principal the user principal
    *
    * @return the condition trap validator if there is one, null otherwise
    */
   @PostMapping("/api/composer/viewsheet/check-condition-trap/{runtimeId}")
   @ResponseBody
   public ConditionTrapValidator checkConditionTrap(
      @RequestBody() ConditionTrapModel model, @PathVariable("runtimeId") String runtimeId,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return vsConditionDialogServiceProxy.checkConditionTrap(runtimeId, model, principal);
   }


   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSConditionDialogServiceProxy vsConditionDialogServiceProxy;
}
