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
import inetsoft.web.composer.ws.event.WSColumnTypeEvent;
import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@Controller
public class ColumnTypeDialogController extends WorksheetController {

   public ColumnTypeDialogController(ColumnTypeDialogServiceProxy dialogServiceProxy) {
      this.dialogServiceProxy = dialogServiceProxy;
   }
   /**
    * If this method is successful, the column type will change and then the socket controller
    * should be invoked by the client. Otherwise, it will throw an error message and not
    * change the column type.
    */
   @RequestMapping(
      value = "/api/composer/ws/column-type-validation/{runtimeId}",
      method = RequestMethod.POST)
   @ResponseBody
   public String updateColumnType(@PathVariable("runtimeId") String runtimeId,
                                  @RequestBody WSColumnTypeEvent event,
                                  Principal principal)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return dialogServiceProxy.updateColumnType(runtimeId, event, principal);
   }



   @Undoable
   @LoadingMask
   @InitWSExecution
   @MessageMapping("composer/worksheet/column-type")
   public void updateColumnType(
      @Payload WSColumnTypeEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      dialogServiceProxy.updateColumnType(getRuntimeId(), event, principal, commandDispatcher);
   }

   private final ColumnTypeDialogServiceProxy dialogServiceProxy;
}
