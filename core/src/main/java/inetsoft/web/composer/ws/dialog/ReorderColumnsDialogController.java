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
import inetsoft.web.composer.model.ws.ReorderColumnsDialogModel;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@Controller
public class ReorderColumnsDialogController extends WorksheetController {

   public ReorderColumnsDialogController(ReorderColumnsDialogServiceProxy dialogServiceProxy)
   {
      this.dialogServiceProxy = dialogServiceProxy;
   }

   @RequestMapping(
      value = "/api/composer/ws/dialog/reorder-columns-dialog-model/{runtimeid}",
      method = RequestMethod.GET)
   @ResponseBody
   public ReorderColumnsDialogModel getModel(
      @PathVariable("runtimeid") String runtimeId,
      @RequestParam("table") String tableName,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      tableName = Tool.byteDecode(tableName);

      return dialogServiceProxy.getModel(runtimeId, tableName, principal);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/ws/dialog/reorder-columns-dialog-model/{table}")
   public void reorderColumns(
      @Payload ReorderColumnsDialogModel model,
      @DestinationVariable("table") String tableName,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      tableName = Tool.byteDecode(tableName);
      dialogServiceProxy.reorderColumns(getRuntimeId(), model, tableName, principal, commandDispatcher);
   }

   private ReorderColumnsDialogServiceProxy dialogServiceProxy;
}
