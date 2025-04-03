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
import inetsoft.web.binding.drm.*;
import inetsoft.web.composer.model.BrowseDataModel;
import inetsoft.web.composer.model.ws.AssemblyConditionDialogModel;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

/**
 * Controller that provides endpoints for the assembly condition dialog.
 *
 * @since 12.3
 */
@Controller
public class AssemblyConditionDialogController extends WorksheetController {

   public AssemblyConditionDialogController(AssemblyConditionDialogServiceProxy dialogServiceProxy)
   {
      this.dialogServiceProxy = dialogServiceProxy;
   }

   /**
    * Gets the model of the assembly condition dialog
    *
    * @param runtimeId the runtime identifier of the worksheet.
    * @return the model object.
    */
   @RequestMapping(
      value = "/api/composer/ws/assembly-condition-dialog-model",
      method = RequestMethod.GET)
   @ResponseBody
   public AssemblyConditionDialogModel getModel(
      @RequestParam("runtimeId") String runtimeId,
      @RequestParam("assemblyName") String assemblyName,
      Principal principal) throws Exception
   {
      return dialogServiceProxy.getModel(runtimeId, assemblyName, principal);
   }

   /**
    * Sets the model of the assembly condition dialog
    *
    * @param assemblyName the name of the assembly
    * @param model        the model of the assembly condition dialog.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/ws/assembly-condition-dialog-model/{assemblyName}")
   public void setModel(
      @DestinationVariable("assemblyName") String assemblyName,
      @Payload AssemblyConditionDialogModel model,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      assemblyName = Tool.byteDecode(assemblyName);
      dialogServiceProxy.setModel(getRuntimeId(), assemblyName, model, principal, commandDispatcher);
   }

   /**
    * Browses the available data for the given data ref
    *
    * @param runtimeId    the runtime identifier of the worksheet.
    * @param dataRefModel the model of the assembly condition dialog.
    * @return the updated model.
    */
   @RequestMapping(
      value = "/api/composer/ws/assembly-condition-dialog/browse-data",
      method = RequestMethod.POST)
   @ResponseBody
   public BrowseDataModel browseData(
      @RequestParam("runtimeId") String runtimeId,
      @RequestParam("assemblyName") String assemblyName,
      @RequestBody DataRefModel dataRefModel,
      Principal principal) throws Exception
   {
      return dialogServiceProxy.browseData(runtimeId, assemblyName, dataRefModel, principal);
   }

   /**
    * Gets the available date ranges for the given worksheet
    *
    * @param runtimeId the runtime identifier of the worksheet.
    * @return the names of the date ranges.
    */
   @RequestMapping(
      value = "/api/composer/ws/assembly-condition-dialog/date-ranges",
      method = RequestMethod.GET)
   @ResponseBody
   public BrowseDataModel getDateRanges(
      @RequestParam("runtimeId") String runtimeId, Principal principal)
      throws Exception
   {
      return dialogServiceProxy.getDateRanges(runtimeId, principal);
   }

   private final AssemblyConditionDialogServiceProxy dialogServiceProxy;
}
