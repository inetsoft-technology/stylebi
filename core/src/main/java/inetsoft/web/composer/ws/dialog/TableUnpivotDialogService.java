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
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.MessageException;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.composer.ws.TableModeService;
import inetsoft.web.composer.ws.WorksheetControllerService;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSUnpivotDialogEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;

@Service
@ClusterProxy
public class TableUnpivotDialogService extends WorksheetControllerService {

   public TableUnpivotDialogService(ViewsheetService viewsheetService,
                                    VSLayoutService vsLayoutService,
                                    DataSourceRegistry dataSourceRegistry)
   {
      super(viewsheetService, dataSourceRegistry);
      this.vsLayoutService = vsLayoutService;
   }

   @ClusterWriteMethod
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void createUnpivotTable(@ClusterProxyKey String runtimeId, WSUnpivotDialogEvent event, Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      String name = event.getAssemblyName();
      TableAssembly assembly = (TableAssembly) ws.getAssembly(name);

      if(assembly != null) {
         int x = assembly.getPixelOffset().x;
         int y = assembly.getPixelOffset().y;
         int hcol = event.getModel().getLevel();
         ColumnSelection columns = assembly.getColumnSelection();
         // The number of header is at least 1, see AssetUtil.unpivot()
         int colLength = Math.max(1, Math.min(hcol, columns.getAttributeCount() - 1));
         final String nname = AssetUtil.getNextName(ws, AbstractSheet.TABLE_ASSET);
         UnpivotTableAssembly table = new UnpivotTableAssembly(ws, nname, assembly);

         table.setLiveData(true);
         table.setPixelOffset(new Point(x, y));
         table.setHeaderColumns(hcol);

         ws.addAssembly(table);
         AssetQuerySandbox box = rws.getAssetQuerySandbox();

         TableModeService.setDefaultTableMode(table, box);
         AssetEventUtil.layoutResultantTable(assembly, assembly, table);
         WorksheetEventUtil.createAssembly(rws, table, commandDispatcher, principal);
         WorksheetEventUtil.refreshColumnSelection(rws, nname, false);
         WorksheetEventUtil.loadTableData(rws, nname, false, false);
         WorksheetEventUtil.refreshAssembly(rws, nname, false, commandDispatcher, principal);
         WorksheetEventUtil.layout(rws, commandDispatcher);
      }

      return null;
   }

   @ClusterWriteMethod
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void changeUnpivotTableRowHeaders(@ClusterProxyKey String runtimeId, WSUnpivotDialogEvent event,
                                            Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      String tableName = event.getAssemblyName();
      RuntimeWorksheet rws = super.getRuntimeWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      Assembly assembly = ws.getAssembly(tableName);

      if(!(assembly instanceof UnpivotTableAssembly)) {
         return null;
      }

      UnpivotTableAssembly table = (UnpivotTableAssembly) assembly;
      int oldHeaderColumns = table.getHeaderColumns();
      int newHeaderColumns = event.getModel().getLevel();

      if(oldHeaderColumns == newHeaderColumns) {
         return null;
      }

      TableAssembly src = table.getTableAssembly();
      int colCount = src == null ? 0 : src.getColumnSelection(false).getAttributeCount();

      // Same bound check as the agent API's editUnpivot (bug 76517/WBS-036). Unlike editUnpivot,
      // this Composer UI call site had no validation on the level value it receives from
      // WSUnpivotDialogEvent at all, so an out-of-range level fell straight through to
      // AssetUtil.checkUnpivotShrinkTypeConflict and threw an uncaught
      // ArrayIndexOutOfBoundsException instead of this friendly message.
      if(newHeaderColumns < 0 || newHeaderColumns >= colCount) {
         throw new MessageException(
            "headerColumns (" + newHeaderColumns + ") must be between 0 and " +
            (colCount - 1) + " (table has " + colCount + " columns).");
      }

      // Same guard as the agent API's editUnpivot (bug 76517/WBS-036): shrinking the header
      // column count moves a column into the melted range, and AssetUtil.unpivot has no way to
      // reject an incompatible move -- it silently coerces the melted column to STRING and
      // injects one phantom "column name as data" row per source row.
      if(newHeaderColumns < oldHeaderColumns && src != null) {
         String conflict = AssetUtil.checkUnpivotShrinkTypeConflict(
            src.getColumnSelection(false), oldHeaderColumns, newHeaderColumns);

         if(conflict != null) {
            throw new MessageException(conflict);
         }
      }

      table.setHeaderColumns(newHeaderColumns);
      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      TableModeService.setDefaultTableMode(table, box);
      WorksheetEventUtil.createAssembly(rws, table, commandDispatcher, principal);
      WorksheetEventUtil.refreshColumnSelection(rws, tableName, true);
      AssetUtil.validateConditions(table.getColumnSelection(), table);
      WorksheetEventUtil.loadTableData(rws, tableName, true, false);

      try {
         WorksheetEventUtil.refreshAssembly(rws, tableName, true, commandDispatcher, principal);
         WorksheetEventUtil.layout(rws, commandDispatcher);
      }
      catch(MessageException ex) {
         if(ex.getCause() instanceof CrossJoinException) {
            this.vsLayoutService.makeUndoable(rws, commandDispatcher, null);
         }

         throw ex;
      }

      return null;
   }

   private VSLayoutService vsLayoutService;
}
