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
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.util.Catalog;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;
import java.security.Principal;

@Service
@ClusterProxy
public class ReplaceColumnsService extends WorksheetControllerService {

   public ReplaceColumnsService(ViewsheetService viewsheetService)
   {
      super(viewsheetService);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public WSReplaceColumnsEventValidator validateReplaceColumns(@ClusterProxyKey String runtimeId,
                                                                WSReplaceColumnsEvent event, Principal principal) throws Exception
   {
      RuntimeWorksheet rws = super.getWorksheetEngine().getWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      String tname = event.tableName();
      TableAssembly table = (TableAssembly) ws.getAssembly(tname);

      // just BoundTableAssembly support, that is same with insert.
      if(table == null || !(table instanceof BoundTableAssembly)) {
         return null;
      }

      WSReplaceColumnsEventValidator.Builder builder = WSReplaceColumnsEventValidator.builder();

      ColumnSelection columns = table.getColumnSelection();
      ColumnRef column =  (ColumnRef)columns.getAttribute(event.targetColumn());

      //valid remove
      if(column != null) {
         if(!allowsDeletion(ws, table, column)) {
            builder.deletion(Catalog.getCatalog().getString(
               "common.columnDependency", column.getAttribute()));

            return builder.build();
         }
         else if(isBeDepend(columns, column)) {
            builder.deletion(Catalog.getCatalog().getString(
               "common.worksheetColumnsDependency"));

            return builder.build();
         }
      }
      else {
         builder.deletion(Catalog.getCatalog().getString(
            "common.invalidTableColumn", event.targetColumn()));

         return builder.build();
      }

      //valid insert
      if(event.entries() != null) {
         WSInsertColumnsEvent insertEvent = WSInsertColumnsEvent.builder()
            .entries(event.entries())
            .index(event.index())
            .name(event.tableName())
            .build();

         WSInsertColumnsEventValidator validator =
            validateInsertColumns0(rws, insertEvent, principal, column);

         builder.trap(validator.trap());
      }

      return builder.build();
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void replaceColumns(@ClusterProxyKey String runtimeId, WSReplaceColumnsEvent event,
                              Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      String tname = event.tableName();
      String targetColumn = event.targetColumn();
      TableAssembly assembly = (TableAssembly) ws.getAssembly(tname);

      if(assembly == null || !(assembly instanceof BoundTableAssembly)) {
         return null;
      }
      ColumnSelection columns = assembly.getColumnSelection();
      ColumnRef column =  (ColumnRef)columns.getAttribute(targetColumn);

      AssetEntry[] entries = event.entries();
      int[] columnIndices = event.columnIndices();

      //insert entries, drag column from tree
      if(entries != null) {
         //delete column
         //when ws has group, some columns may hide, so we should use name to remove column in ColumnSelection
         columns.removeAttribute(column);
         assembly.setColumnSelection(columns);
         //insert entries
         insertColumns0(tname, event.index(), event.entries(), columns, rws, assembly);
      }
      //reset column index, drag column of its table
      else if(columnIndices != null) {
         setColumnIndex0(tname, event.index(), rws, columnIndices, true);
      }

      AggregateInfo aggInfo = assembly.getAggregateInfo();

      if(aggInfo != null) {
         aggInfo.removeGroup(column);
      }

      WorksheetEventUtil.refreshColumnSelection(rws, tname, true);
      WorksheetEventUtil.loadTableData(rws, tname, true, true);
      WorksheetEventUtil.refreshAssembly(rws, tname, true, commandDispatcher, principal);
      WorksheetEventUtil.layout(rws, commandDispatcher);
      AssetEventUtil.refreshTableLastModified(ws, tname, true);

      return null;
   }

}
