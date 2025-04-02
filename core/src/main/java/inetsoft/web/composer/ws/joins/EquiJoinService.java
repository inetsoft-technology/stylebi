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

package inetsoft.web.composer.ws.joins;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.web.composer.ws.WorksheetControllerService;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSEquiJoinEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Enumeration;

@Service
@ClusterProxy
public class EquiJoinService extends WorksheetControllerService {

   public EquiJoinService(ViewsheetService viewsheetService)
   {
      super(viewsheetService);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setEquiJoin(@ClusterProxyKey String runtimeId, WSEquiJoinEvent event, Principal principal,
                           CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      RelationalJoinTableAssembly joinTable =
         (RelationalJoinTableAssembly) ws.getAssembly(event.joinTable());
      Enumeration operatorTables = joinTable.getOperatorTables();

      while(operatorTables.hasMoreElements()) {
         String[] pair = (String[]) operatorTables.nextElement();
         String leftTable = pair[0];
         String rightTable = pair[1];
         TableAssemblyOperator tableOperator = joinTable.getOperator(leftTable, rightTable);

         for(TableAssemblyOperator.Operator operator : tableOperator.getOperators()) {
            if(!event.leftSubtable().equals(leftTable) ||
               !event.rightSubtable().equals(rightTable))
            {
               continue;
            }

            if(operator.getOperation() == XConstants.INNER_JOIN ||
               operator.getOperation() == XConstants.LEFT_JOIN ||
               operator.getOperation() == XConstants.RIGHT_JOIN ||
               operator.getOperation() == XConstants.FULL_JOIN)
            {
               operator.setOperation(event.operation());
            }
         }
      }

      WorksheetEventUtil.refreshColumnSelection(
         rws, joinTable.getName(), false);
      WorksheetEventUtil.loadTableData(
         rws, joinTable.getAbsoluteName(), true, true);
      WorksheetEventUtil.refreshAssembly(
         rws, joinTable.getAbsoluteName(), true, commandDispatcher, principal);
      AssetEventUtil.refreshTableLastModified(
         ws, joinTable.getAbsoluteName(), true);

      return null;
   }
}
