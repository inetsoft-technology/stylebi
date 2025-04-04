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
import inetsoft.web.composer.model.ws.ExpressionDialogModel;
import inetsoft.web.composer.model.ws.ExpressionDialogModelValidator;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@Controller
public class ExpressionDialogController extends WorksheetController {

   public ExpressionDialogController(ExpressionDialogServiceProxy expressionDialogServiceProxy)
   {
      this.expressionDialogServiceProxy = expressionDialogServiceProxy;
   }

   @RequestMapping(
      value = "/api/composer/ws/expression-dialog-model/{runtimeId}",
      method = RequestMethod.GET)
   @ResponseBody
   public ExpressionDialogModel getExpressionModel(
      @PathVariable("runtimeId") String runtimeId,
      @RequestParam("tableName") String tableName,
      @RequestParam(value = "columnIndex", required = false) String columnIndexAsParam,
      @RequestParam(value = "showOriginalName", required = false) boolean showOriginalName,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      tableName = Tool.byteDecode(tableName);

      return expressionDialogServiceProxy.getExpressionModel(runtimeId, tableName, columnIndexAsParam,
                                                             showOriginalName, principal);
   }

   @RequestMapping(
      value = "/api/composer/ws/expression-dialog-model/{runtimeId}",
      method = RequestMethod.POST)
   @ResponseBody
   public ExpressionDialogModelValidator validateExpression(
      @PathVariable("runtimeId") String runtimeId,
      @RequestBody ExpressionDialogModel model, Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return expressionDialogServiceProxy.validateExpression(runtimeId, model, principal);
   }

   @Undoable
   @LoadingMask
   @InitWSExecution
   @MessageMapping("/ws/dialog/expression-dialog-model")
   public void setModel(
      @Payload ExpressionDialogModel model, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      expressionDialogServiceProxy.setModel(getRuntimeId(), model, principal, commandDispatcher);
   }

   private ExpressionDialogServiceProxy expressionDialogServiceProxy;
}
