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
import inetsoft.web.composer.model.ws.TablePropertyDialogModel;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@Controller
public class TablePropertyDialogController extends WorksheetController {

   public TablePropertyDialogController(TablePropertyDialogServiceProxy tablePropertyDialogServiceProxy)
   {
      this.dialogServiceProxy = tablePropertyDialogServiceProxy;
   }

   @RequestMapping(
      value = "api/composer/ws/dialog/table-property-dialog-model/{runtimeId}",
      method = RequestMethod.GET)
   @ResponseBody
   public TablePropertyDialogModel getModel(
      @PathVariable("runtimeId") String runtimeId,
      @RequestParam("table") String name,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return dialogServiceProxy.getModel(runtimeId, name, principal);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/ws/dialog/table-property-dialog-model")
   public void saveProperties(
      @Payload TablePropertyDialogModel model,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      dialogServiceProxy.saveProperties(getRuntimeId(), model, principal, commandDispatcher);
   }

   private final TablePropertyDialogServiceProxy dialogServiceProxy;
}
