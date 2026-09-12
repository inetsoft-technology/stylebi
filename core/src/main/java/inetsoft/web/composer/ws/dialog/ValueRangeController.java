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
import inetsoft.web.composer.model.ws.ValueRangeDialogModel;
import inetsoft.web.composer.model.ws.ValueRangeDialogModelValidator;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@Controller
public class ValueRangeController extends WorksheetController {

   public ValueRangeController(ValueRangeServiceProxy valueRangeServiceProxy) {
      this.valueRangeServiceProxy = valueRangeServiceProxy;
   }

   /**
    * Populates the dialog model.
    *
    * @param runtimeId runtimeId of the worksheet.
    * @param tableName name of TableAssembly.
    * @param expColumnName name of the expression column.
    * @param fromColumn name of the column the expression is sourced from.
    * @param numeric whether the value is numeric or not.
    * @param principal user principal.
    * @return model if valid, null otherwise.
    */
   @RequestMapping(
      value = "/api/composer/ws/value-range-option-dialog-model/{runtimeid}",
      method = RequestMethod.GET)
   @ResponseBody
   public ValueRangeDialogModel valueRangeModel(
      @PathVariable("runtimeid") String runtimeId,
      @RequestParam("table") String tableName,
      @RequestParam(name = "expcolumn", required = false) String expColumnName,
      @RequestParam(name = "fromcolumn", required = false) String fromColumn,
      @RequestParam("numeric") boolean numeric,
      Principal principal) throws Exception
   {
      tableName = Tool.byteDecode(tableName);
      return valueRangeServiceProxy.valueRangeModel(Tool.byteDecode(runtimeId), tableName,
                                                    expColumnName, fromColumn, numeric, principal);
   }

   /**
    * Edits an existing range column.
    *
    * @param model dialog model.
    * @param tableName name of TableAssembly.
    * @param principal user principal.
    * @param commandDispatcher command dispatcher.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/value-range/{tableName}")
   public void editValueRange(
      @Payload ValueRangeDialogModel model,
      @DestinationVariable("tableName") String tableName,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      tableName = Tool.byteDecode(tableName);
      valueRangeServiceProxy.editValueRange(super.getRuntimeId(), model, tableName,
                                            principal, commandDispatcher);
   }

   @RequestMapping(
      value = "/api/composer/ws/value-range-option-dialog-model/validate/{runtimeid}",
      method = RequestMethod.POST)
   @ResponseBody
   public ValueRangeDialogModelValidator validateValueRange(
      @RequestBody ValueRangeDialogModel model,
      @PathVariable("runtimeid") String runtimeId,
      @RequestParam("table") String tableName, Principal principal) throws Exception
   {
      tableName = Tool.byteDecode(tableName);
      return valueRangeServiceProxy.validateValueRange(runtimeId, model, tableName, principal);
   }

   /**
    * Creates a new range column.
    *
    * @param model dialog model.
    * @param tableName name of TableAssembly.
    * @param fromColumn name of the column the expression is sourced from.
    * @param principal user principal.
    * @param commandDispatcher command dispatcher.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/value-range/{tableName}/{columnName}")
   public void newValueRange(
      @Payload ValueRangeDialogModel model,
      @DestinationVariable("tableName") String tableName,
      @DestinationVariable("columnName") String fromColumn,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      tableName = Tool.byteDecode(tableName);
      fromColumn = Tool.byteDecode(fromColumn);
      valueRangeServiceProxy.newValueRange(super.getRuntimeId(), model, tableName,
                                           fromColumn, principal, commandDispatcher);
   }

   private final ValueRangeServiceProxy valueRangeServiceProxy;
}
