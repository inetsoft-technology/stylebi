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
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.execution.*;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XQuery;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.TabularTableAssembly;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSAssemblyEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;
import java.security.Principal;

@Service
@ClusterProxy
public class WSQueryService extends WorksheetControllerService {

   public WSQueryService(ViewsheetService viewsheetService)
   {
      super(viewsheetService);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void runQuery(@ClusterProxyKey String runtimeId, WSAssemblyEvent event, Principal principal,
                        CommandDispatcher commandDispatcher) throws Exception
   {
      final RuntimeWorksheet rws = getRuntimeWorksheet(runtimeId, principal);
      final TableAssembly table = (TableAssembly) rws.getWorksheet().getAssembly(
         event.getAssemblyName());

      if(table != null) {
         final AssetQuerySandbox box = rws.getAssetQuerySandbox();
         final int mode = WorksheetEventUtil.getMode(table);

         if(mode == AssetQuerySandbox.RUNTIME_MODE) {
            box.getVariableTable().remove(XQuery.HINT_MAX_ROWS);
         }

         box.resetTableLens(table.getName(), mode);
         DataKey key = AssetDataCache.getCacheKey(
            table, box, null, WorksheetEventUtil.getMode(table), true);
         AssetDataCache.getCache().remove(key);
         AssetQueryCacheNormalizer.clearCache(table, box);
         TableAssembly clone = (TableAssembly) table.clone();
         AssetQuery query = AssetQuery.createAssetQuery(
            clone, mode, box, false, -1L, true, false);

         if(query instanceof BoundQuery) {
            VariableTable vars = box.getVariableTable().clone();
            vars.put(XQuery.HINT_PREVIEW, AssetQuerySandbox.isLiveMode(mode) + "");
            ((BoundQuery) query).clearQueryCache(vars);
         }

         try {
            if(table instanceof TabularTableAssembly) {
               ((TabularTableAssembly) table).loadColumnSelection
                  (rws.getAssetQuerySandbox().getVariableTable(), true,
                   rws.getAssetQuerySandbox().getQueryManager());
            }

            box.getVariableTable().put("__refresh_report__", "true");
            WorksheetEventUtil.loadTableData(rws, table.getName(), false, false);
            WorksheetEventUtil.refreshAssembly(
               rws, table.getName(), false, commandDispatcher, principal);
         }
         finally {
            box.getVariableTable().remove("__refresh_report__");
         }
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void stopQuery(@ClusterProxyKey String runtimeId, WSAssemblyEvent event,
                         Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      final RuntimeWorksheet rws = getRuntimeWorksheet(runtimeId, principal);
      final TableAssembly table = (TableAssembly) rws.getWorksheet().getAssembly(
         event.getAssemblyName());

      if(table != null) {
         final AssetQuerySandbox box = rws.getAssetQuerySandbox();
         final int mode = WorksheetEventUtil.getMode(table);
         final TableLens tableLens = box.getTableLens(table.getName(), mode);
         final CancellableTableLens cancelTable = (CancellableTableLens) Util.getNestedTable(
            tableLens, CancellableTableLens.class);

         if(cancelTable != null) {
            cancelTable.cancel();
         }
         else {
            box.getQueryManager().cancel();
         }

         WorksheetEventUtil.refreshAssembly(
            rws, table.getName(), false, commandDispatcher, principal);
      }

      return null;
   }

}
