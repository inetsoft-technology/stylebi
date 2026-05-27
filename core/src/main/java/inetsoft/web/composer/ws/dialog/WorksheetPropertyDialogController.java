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
import inetsoft.web.composer.model.ws.WorksheetPropertyDialogModel;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

/**
 * Controller that provides the REST endpoints for the worksheet property
 * dialog.
 *
 * @since 12.3
 */
@Controller
public class WorksheetPropertyDialogController extends WorksheetController {


   public WorksheetPropertyDialogController(WorksheetPropertyDialogServiceProxy dialogServiceProxy)
   {
      this.dialogServiceProxy = dialogServiceProxy;
   }

   /**
    * Gets the top-level descriptor of the specified worksheet.
    *
    * @param runtimeId the runtime identifier of the worksheet.
    *
    * @return the worksheet descriptor.
    */
   @RequestMapping(
      value = "/api/composer/ws/dialog/worksheet-property-dialog-model/{runtimeId}",
      method = RequestMethod.GET)
   @ResponseBody
   public WorksheetPropertyDialogModel getWorksheetInfo(
      @PathVariable("runtimeId") String runtimeId, Principal principal) throws Exception
   {
      return dialogServiceProxy.getWorksheetInfo(Tool.byteDecode(runtimeId), principal);
   }


   /**
    * Sets the top-level descriptor of the specified worksheet.
    *
    * @param runtimeId the runtime identifier of the worksheet.
    * @param model     the worksheet descriptor.
    * @param principal the current user principal
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/ws/dialog/worksheet-property-dialog-model/{runtimeId}")
   public void setWorksheetInfo(
      @DestinationVariable("runtimeId") String runtimeId,
      @Payload WorksheetPropertyDialogModel model,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      dialogServiceProxy.setWorksheetInfo(runtimeId, model, principal, commandDispatcher);
   }

   private final WorksheetPropertyDialogServiceProxy dialogServiceProxy;
}
