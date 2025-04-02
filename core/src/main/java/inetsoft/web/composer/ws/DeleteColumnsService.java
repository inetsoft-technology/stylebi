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
import inetsoft.report.composition.*;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.delete.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.RenameInfo;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.Catalog;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSDeleteColumnsEvent;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Service
@ClusterProxy
public class DeleteColumnsService extends WorksheetControllerService {
   public DeleteColumnsService(ViewsheetService viewsheetService) {
      super(viewsheetService);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public String hasDependency(@ClusterProxyKey String rid,
                               boolean all,
                               WSDeleteColumnsEvent event,
                               Principal principal)
      throws Exception
   {
      WorksheetService engine = getWorksheetEngine();
      RuntimeWorksheet rws = engine.getWorksheet(rid, principal);

      String wsName = rws.getEntry().getName();
      String source = rws.getEntry().toIdentifier();
      ColumnRefModel[] columnRefs = event.getColumns();
      List<DeleteInfo> dinfo = new ArrayList<>();

      for(ColumnRefModel columnRef : columnRefs) {
         String displayName = columnRef.getAlias() == null ?
            columnRef.getAttribute() : columnRef.getAlias();
         DeleteInfo info = new DeleteInfo(displayName,
                                          RenameInfo.ASSET | RenameInfo.COLUMN, source, event.getTableName());
         dinfo.add(info);
      }

      DeleteDependencyInfo info = DeleteDependencyHandler.createWsDependencyInfo(dinfo, rws);

      if(all) {
         return DeleteDependencyHandler.checkDependencyStatus(info);
      }
      else {
         return DeleteDependencyHandler.hasDependency(info).toString();
      }
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void deleteColumns(
      @ClusterProxyKey String runtimeId,
      WSDeleteColumnsEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      String tname = event.getTableName();
      TableAssembly table = (TableAssembly) ws.getAssembly(tname);
      ColumnRefModel[] columnRefs = event.getColumns();

      if(table != null && columnRefs != null) {
         boolean changed = false;

         for(int i = 0; i < columnRefs.length; i++) {
            ColumnRef column = (ColumnRef) columnRefs[i].createDataRef();

            if(!allowsDeletion(ws, table, column)) {
               MessageCommand command = new MessageCommand();
               command.setMessage(Catalog.getCatalog().getString(
                  "common.columnDependency", column.getAttribute()));
               command.setType(MessageCommand.Type.WARNING);
               command.setAssemblyName(tname);
               commandDispatcher.sendCommand(command);

               continue;
            }

            boolean ok = false;

            if(table instanceof EmbeddedTableAssembly) {
               XEmbeddedTable data =
                  ((EmbeddedTableAssembly) table).getEmbeddedData();
               int index = AssetUtil.findColumn(data, column);

               if(index >= 0) {
                  data.deleteCol(index);
                  changed = true;
                  ok = true;
               }
            }

            if(!ok) {
               ColumnSelection columns = table.getColumnSelection();
               int index = columns.indexOfAttribute(column);

               if(index >= 0) {
                  if(isBeDepend(columns, column)) {
                     MessageCommand command = new MessageCommand();
                     command.setMessage(Catalog.getCatalog().getString(
                        "common.worksheetColumnsDependency"));
                     command.setType(MessageCommand.Type.WARNING);
                     command.setAssemblyName(tname);
                     commandDispatcher.sendCommand(command);
                     continue;
                  }

                  columns.removeAttribute(index);
                  table.setColumnSelection(columns);
                  changed = true;
                  ok = true;
                  AggregateInfo aggInfo = table.getAggregateInfo();

                  if(aggInfo != null) {
                     aggInfo.removeGroup(column);
                  }
               }
            }
         }

         if(changed) {
            WorksheetEventUtil.refreshColumnSelection(rws, tname, true);
            WorksheetEventUtil.loadTableData(rws, tname, true, true);
            WorksheetEventUtil.refreshAssembly(rws, tname, true, commandDispatcher, principal);
            WorksheetEventUtil.layout(rws, commandDispatcher);
            AssetEventUtil.refreshTableLastModified(ws, tname, true);
         }
      }

      return null;
   }
}
