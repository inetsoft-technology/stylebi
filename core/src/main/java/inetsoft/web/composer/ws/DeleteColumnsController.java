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

import inetsoft.web.composer.ws.event.WSDeleteColumnsEvent;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
public class DeleteColumnsController extends WorksheetController {
   public DeleteColumnsController(DeleteColumnsServiceProxy deleteColumnsService) {
      this.deleteColumnsService = deleteColumnsService;
   }

   @PostMapping("/api/composer/worksheet/delete-columns/check-dependency/**")
   @ResponseBody
   public String hasDependency(@RemainingPath String rid,
                               @RequestParam(value = "all", required = false) boolean all,
                               @RequestBody WSDeleteColumnsEvent event,
                               Principal principal)
      throws Exception
   {
      return deleteColumnsService.hasDependency(rid, all, event, principal);
   }

   @Undoable
   @LoadingMask
   @InitWSExecution
   @MessageMapping("/composer/worksheet/delete-columns")
   public void deleteColumns(
      @Payload WSDeleteColumnsEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      deleteColumnsService.deleteColumns(getRuntimeId(), event, principal, commandDispatcher);
   }

   private final DeleteColumnsServiceProxy deleteColumnsService;
}
