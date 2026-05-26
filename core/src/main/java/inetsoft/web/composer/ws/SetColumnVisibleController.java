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
package inetsoft.web.composer.ws;

import inetsoft.util.Tool;
import inetsoft.web.composer.model.ws.ShowHideColumnsDialogModel;
import inetsoft.web.composer.ws.event.WSSetColumnVisibilityEvent;
import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@Controller
public class SetColumnVisibleController extends WorksheetController {

   public SetColumnVisibleController(SetColumnVisibleServiceProxy setColumnVisibleServiceProxy)
   {
      this.setColumnVisibleServiceProxy = setColumnVisibleServiceProxy;
   }

   /** From 12.2 SetColumnVisibleEvent */
   @Undoable
   @LoadingMask
   @InitWSExecution
   @MessageMapping("/composer/worksheet/set-column-visibility")
   public void setColumnVisibility(
      @Payload WSSetColumnVisibilityEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      setColumnVisibleServiceProxy.setColumnVisibility(getRuntimeId(), event, principal, commandDispatcher) ;
   }

   @RequestMapping(
      value = "/api/composer/ws/dialog/show-hide-columns-dialog-model/{runtimeid}",
      method = RequestMethod.GET)
   @ResponseBody
   public ShowHideColumnsDialogModel getModel(
      @PathVariable("runtimeid") String runtimeId,
      @RequestParam("table") String tableName,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      tableName = Tool.byteDecode(tableName);
      return setColumnVisibleServiceProxy.getModel(runtimeId, tableName, principal);
   }

   private final SetColumnVisibleServiceProxy setColumnVisibleServiceProxy;
}
