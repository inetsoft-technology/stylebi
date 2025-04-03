/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.uql.asset.*;
import inetsoft.web.composer.model.ws.ConcatenationTypeDialogModel;
import inetsoft.web.composer.model.ws.TableAssemblyOperatorModel;
import inetsoft.web.composer.ws.WorksheetControllerService;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;
import java.security.Principal;

@Service
@ClusterProxy
public class ConcatentationTypeDialogService extends WorksheetControllerService {

   public ConcatentationTypeDialogService(ViewsheetService viewsheetService)
   {
      super(viewsheetService);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ConcatenationTypeDialogModel getConcatenationType(@ClusterProxyKey String runtimeId,
                                                            String concatenatedTableName,
                                                            String leftTableName,
                                                            String rightTableName,
                                                            Principal principal) throws Exception
   {
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

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void updateConcatenationType(@ClusterProxyKey String runtimeId, ConcatenationTypeDialogModel model,
                                       Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(runtimeId, principal);
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

      return null;
   }
}
