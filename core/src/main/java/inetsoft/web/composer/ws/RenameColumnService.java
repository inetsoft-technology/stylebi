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

package inetsoft.web.composer.ws;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.uql.asset.*;
import inetsoft.util.Catalog;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSRenameColumnEvent;
import inetsoft.web.composer.ws.event.WSRenameColumnEventValidator;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.HashSet;

@Service
@ClusterProxy
public class RenameColumnService extends WorksheetControllerService {
   public RenameColumnService(ViewsheetService viewsheetService) {
      super(viewsheetService);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public WSRenameColumnEventValidator validateOpen(
      @ClusterProxyKey String runtimeId,
      WSRenameColumnEvent event,
      Principal principal) throws Exception
   {
      RuntimeWorksheet rws = super.getWorksheetEngine().getWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      String tname = event.tableName();
      TableAssembly table = (TableAssembly) ws.getAssembly(tname);
      HashSet<String> nameset = new HashSet<>();
      nameset.add(tname);

      if(table != null &&
         AssetEventUtil.hasDependent(table, ws, nameset))
      {
         String message =
            Catalog.getCatalog().getString("assembly.rename.dependency");

         return WSRenameColumnEventValidator.builder()
            .modifyDependencies(message)
            .build();
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void renameColumn(
      @ClusterProxyKey String runtimeId,
      WSRenameColumnEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      String tname = event.tableName();
      String alias = event.newAlias();
      TableAssembly table = (TableAssembly) ws.getAssembly(tname);
      ColumnRef column = (ColumnRef) table.getColumnSelection()
         .getAttribute(event.columnName());

      if(column == null) {
         return null;
      }

      HashSet<String> nameset = new HashSet<>();
      nameset.add(tname);

      // Choose to not modify dependent columns
      if(!event.modifyDependencies() && AssetEventUtil.hasDependent(table, ws, nameset)) {
         return null;
      }

      if(!canRenameColumn(ws, table, column, commandDispatcher)) {
         return null;
      }

      RenameColumnController.renameColumn(ws, commandDispatcher, table, column, alias);
      WorksheetEventUtil.refreshColumnSelection(rws, tname, true);
      WorksheetEventUtil.loadTableData(rws, tname, true, true);
      WorksheetEventUtil.refreshAssembly(rws, tname, true, commandDispatcher, principal);

      AssetEventUtil.refreshTableLastModified(ws, tname, true);
      return null;
   }

   private boolean canRenameColumn(Worksheet ws, TableAssembly table, ColumnRef column,
                                   CommandDispatcher dispatcher)
   {
      if(!allowsDeletion(ws, table, column)) {
         MessageCommand command = new MessageCommand();
         command.setMessage(Catalog.getCatalog().getString(
            "common.columnDependency", column.getAttribute()));
         command.setType(MessageCommand.Type.WARNING);
         command.setAssemblyName(table.getName());
         dispatcher.sendCommand(command);
         return false;
      }

      return true;
   }
}
