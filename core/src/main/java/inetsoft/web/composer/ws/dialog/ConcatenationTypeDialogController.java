/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.ws.dialog;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.uql.asset.*;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.ws.ConcatenationTypeDialogModel;
import inetsoft.web.composer.model.ws.TableAssemblyOperatorModel;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
public class ConcatenationTypeDialogController extends WorksheetController {
   @RequestMapping(
      value = "/api/composer/ws/concatenation-type-dialog/{runtimeId}",
      method = RequestMethod.GET)
   @ResponseBody
   public ConcatenationTypeDialogModel getConcatenationType(
      @PathVariable("runtimeId") String runtimeId,
      @RequestParam("concatenatedTable") String concatenatedTableName,
      @RequestParam("leftTable") String leftTableName,
      @RequestParam("rightTable") String rightTableName,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeWorksheet rws = super.getWorksheetEngine().getWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      ConcatenatedTableAssembly concatenatedTable =
         (ConcatenatedTableAssembly) ws.getAssembly(concatenatedTableName);

      if(concatenatedTable != null) {
         TableAssemblyOperator operators = concatenatedTable.getOperator(leftTableName, rightTableName);
         TableAssemblyOperator.Operator operator = operators.getKeyOperator();

         TableAssemblyOperatorModel operatorModel = new TableAssemblyOperatorModel();
         operatorModel.setOperation(operator.getOperation());
         operatorModel.setDistinct(operator.isDistinct());
         operatorModel.setLtable(operator.getLeftTable());
         operatorModel.setRtable(operator.getRightTable());

         return ConcatenationTypeDialogModel.builder()
            .concatenatedTableName(concatenatedTableName)
            .leftTableName(leftTableName)
            .rightTableName(rightTableName)
            .operator(operatorModel)
            .build();
      }

      return null;
   }

   @Undoable
   @LoadingMask
   @MessageMapping("composer/worksheet/concatenation-type-dialog")
   public void updateConcatenationType(
      @Payload ConcatenationTypeDialogModel model, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      String tname = model.concatenatedTableName();
      String lname = model.leftTableName();
      String rname = model.rightTableName();
      boolean all = model.all();
      TableAssemblyOperator.Operator operator = WorksheetEventUtil.convertOperator(ws, model.operator());
      ConcatenatedTableAssembly table =
         (ConcatenatedTableAssembly) ws.getAssembly(tname);

      if(table != null) {
         TableAssembly[] tableAssemblies = table.getTableAssemblies(false);

         for(int i = 0; i < tableAssemblies.length - 1; i++) {
            if(lname.equals(tableAssemblies[i].getName()) &&
               rname.equals(tableAssemblies[i + 1].getName()) || all)
            {
               TableAssemblyOperator op = new TableAssemblyOperator();
               op.addOperator(operator);
               table.setOperator(i, op);
            }
         }

         WorksheetEventUtil.loadTableData(rws, tname, true, true);
         WorksheetEventUtil.refreshAssembly(rws, tname, true, commandDispatcher, principal);
         AssetEventUtil.refreshTableLastModified(ws, tname, true);
      }
   }
}
