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

import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.AssetQuery;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.util.Tool;
import org.springframework.stereotype.Service;
import java.security.Principal;

@Service
@ClusterProxy
public class ShowPlanService {

   public ShowPlanService(WorksheetService worksheetService) {
      this.worksheetService = worksheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public QueryTreeModel.QueryNode showPlan(@ClusterProxyKey String runtimeId, String tname,
                                                             Principal principal) throws Exception
   {
      RuntimeWorksheet rws = worksheetService.getWorksheet(Tool.byteDecode(runtimeId), principal);
      tname = Tool.byteDecode(tname);
      Worksheet ws = rws.getWorksheet();
      TableAssembly table = (TableAssembly) ws.getAssembly(tname);

      if(table != null) {
         AssetQuerySandbox box = rws.getAssetQuerySandbox();
         int mode = AssetQuerySandbox.RUNTIME_MODE; // always use runtime mode
         table = (TableAssembly) table.clone();
         AssetQuery query = AssetQuery.createAssetQuery(
            table, mode, box, false, -1L, true, true);
         query.setLevel(0);

         return query.getQueryPlan();

      }

      return null;
   }

   private WorksheetService worksheetService;
}
