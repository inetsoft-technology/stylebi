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
import inetsoft.util.MessageException;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.composer.ws.TableModeController;
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
                                    VSLayoutService vsLayoutService)
   {
      super(viewsheetService);
      this.vsLayoutService = vsLayoutService;
   }

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

         TableModeController.setDefaultTableMode(table, box);
         AssetEventUtil.layoutResultantTable(assembly, assembly, table);
         WorksheetEventUtil.createAssembly(rws, table, commandDispatcher, principal);
         WorksheetEventUtil.refreshColumnSelection(rws, nname, false);
         WorksheetEventUtil.loadTableData(rws, nname, false, false);
         WorksheetEventUtil.refreshAssembly(rws, nname, false, commandDispatcher, principal);
         WorksheetEventUtil.layout(rws, commandDispatcher);
      }

      return null;
   }

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

      if(table.getHeaderColumns() == event.getModel().getLevel()) {
         return null;
      }

      table.setHeaderColumns(event.getModel().getLevel());
      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      TableModeController.setDefaultTableMode(table, box);
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
