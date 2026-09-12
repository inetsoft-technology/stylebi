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

import inetsoft.util.*;
import inetsoft.web.composer.ws.event.*;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@Controller
public class ConcatenateTablesController extends WorksheetController {
   public ConcatenateTablesController(ConcatenateTablesServiceProxy concatenateTablesService) {
      this.concatenateTablesService = concatenateTablesService;
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/concatenate-tables")
   public void concatenateTables(
      @Payload WSConcatenateEvent event,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      concatenateTablesService.concatenateTables(getRuntimeId(), event, principal, commandDispatcher);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/concatenate/add-table")
   public void addSubTable(
      @Payload WSConcatAddSubTableEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      concatenateTablesService.addSubTable(getRuntimeId(), event, principal, commandDispatcher);
   }

   @RequestMapping(
      value = "api/composer/worksheet/concat/compatible-insertion-tables/{runtimeid}",
      method = RequestMethod.GET)
   @ResponseBody
   public List<String> getCompatibleInsertionTables(
      @PathVariable("runtimeid") String runtimeId,
      @RequestParam("concatTable") String concatTable,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return concatenateTablesService
         .getCompatibleInsertionTables(runtimeId, concatTable, principal);
   }

   /**
    * Checks the compatibility of the source table and the rest of the tables
    * in the worksheet.
    */
   @MessageMapping("/composer/worksheet/concat/compatibility")
   public void checkCompatibility(
      @Payload ConcatCompatibilityEvent event,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      concatenateTablesService
         .checkCompatibility(getRuntimeId(), event, principal, commandDispatcher);
   }

   private final ConcatenateTablesServiceProxy concatenateTablesService;
}
