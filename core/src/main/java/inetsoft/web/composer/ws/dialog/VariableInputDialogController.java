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

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.WSExecution;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.assembly.VariableAssemblyModelInfo;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.command.WSCollectVariablesCommand;
import inetsoft.web.composer.ws.event.WSCollectVariablesOverEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Controller
public class VariableInputDialogController extends WorksheetController {
   @GetMapping("/api/composer/ws/dialog/variable-input-dialog-model/{runtimeid}")
   @ResponseBody
   public List<VariableAssemblyModelInfo> getModel(
      @PathVariable("runtimeid") String runtimeId,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      final WorksheetService engine = getWorksheetEngine();
      RuntimeWorksheet rws = engine.getWorksheet(runtimeId, principal);
      WSCollectVariablesCommand res = WorksheetEventUtil.refreshVariables(rws, engine, true);

      return res == null ? new ArrayList<>() : res.varInfos();
   }

   @MessageMapping("ws/dialog/variable-restore")
   public void restoreVariable(Principal principal) throws Exception {
      RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      AssetQuerySandbox box = rws.getAssetQuerySandbox();

      box.restoreVariables();
   }

   /**
    * From 12.2 CollectVariablesOverEvent.
    */
   @LoadingMask
   @MessageMapping("/ws/dialog/variable-input-dialog")
   public void initVariableInfos(
      @Payload WSCollectVariablesOverEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      String wsName = rws.getEntry().getSheetName();
      Worksheet ws = rws.getWorksheet();
      VariableTable vtable = new VariableTable();
      Assembly[] assemblies = ws.getAssemblies();
      Principal user = rws.getUser();
      final WorksheetService engine = getWorksheetEngine();

      if(event.values() != null) {
         for(int i = 0; i < event.values().length; i++) {
            if(event.values()[i] == null) {
               continue;
            }

            Object[] values = new Object[event.values()[i].length];

            for(int k = 0; k < values.length; k++) {
               String tempVal = event.values()[i][k];
               values[k] = tempVal == null ? null : CoreTool.getData(event.types()[i], tempVal, true);
            }

            String vname = event.names()[i];
            boolean userSelected = event.userSelected()[i];
            boolean usedInOneOf = event.usedInOneOf()[i];
            // VariableInputDialog already call splitValue() for oneOf, which handles quoted
            // strings. so just use the value (array) as is. otherwise a quote string containing
            // comma will be split again in Tool.convertParameter(). (64030)
            Object varValue = values.length == 1 && !usedInOneOf ? values[0] : values;
            vtable.setAsIs(vname, usedInOneOf && values.getClass().isArray());
            vtable.put(vname, varValue);

            if(varValue == null && userSelected) {
               vtable.setNotIgnoredNull(vname);
            }

            // fix bug1302156734351, should keep the values as a Array.
            // vtable.put(vname, values);
            engine.setCachedProperty(user, wsName + " variable : " + vname, varValue);
         }

         box.refreshVariableTable(vtable);
      }

      box.reset();

      for(Assembly assembly : assemblies) {
         AssetEventUtil.refreshTableLastModified(ws, assembly.getName(), true);

         if(assembly instanceof TabularTableAssembly) {
            refreshTabularColumnSelection((TabularTableAssembly) assembly, box);
         }

         WorksheetEventUtil.loadTableData(rws, assembly.getName(), false, false);

         if(event.refreshColumns()) {
            WorksheetEventUtil.refreshColumnSelection(rws, assembly.getName(), true,
               commandDispatcher);
         }

         if(!event.initial()) {
            WorksheetEventUtil.refreshAssembly(
               rws, assembly.getName(), false, commandDispatcher, principal);
         }
      }

      WorksheetEventUtil.layout(rws, commandDispatcher);
      WorksheetEventUtil.refreshDateRange(ws);

      if(event.initial()) {
         WorksheetEventUtil.refreshWorksheet(
            rws, engine, false, false, commandDispatcher, principal);
      }
   }

   private void refreshTabularColumnSelection(TabularTableAssembly assembly,
                                              AssetQuerySandbox box) throws Exception
   {
      final UserVariable[] variables = assembly.getAllVariables();

      if(variables.length > 0) {
         WSExecution.setAssetQuerySandbox(box);

         try {
            assembly.loadColumnSelection(box.getVariableTable(), false, box.getQueryManager());
         }
         finally {
            WSExecution.setAssetQuerySandbox(null);
         }
      }
   }
}
