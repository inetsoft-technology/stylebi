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
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.XFactory;
import inetsoft.uql.asset.*;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class CancelLoadingService extends WorksheetControllerService{
   CancelLoadingService(ViewsheetService service) {
      super(service);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void cancelLoading(
      @ClusterProxyKey String runtimeId,
      Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = getRuntimeWorksheet(runtimeId, principal);
      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      Worksheet ws = rws.getWorksheet();

      if(box == null || ws == null) {
         return null;
      }

      Assembly[] assemblies = ws.getAssemblies();

      for(Assembly assembly : assemblies) {
         if(assembly instanceof TableAssembly) {
            TableAssembly table = (TableAssembly) assembly;
            table.setEditMode(false);
            table.setLiveData(false);
            table.setRuntime(false);
            table.setRuntimeSelected(false);
         }
      }

      if(box.getQueryManager() != null) {
         box.getQueryManager().cancel();
      }

      XFactory.getRepository().refreshMetaData();
      box.reset();
      WorksheetEventUtil.refreshWorksheet(
         rws, getWorksheetEngine(), false, false, commandDispatcher, principal);
      return null;
   }
}
