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

package inetsoft.web.binding.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.binding.event.GetVSObjectModelEvent;
import inetsoft.web.viewsheet.controller.table.BaseTableService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class VSViewService {
   public VSViewService(
      CoreLifecycleService holder,
      ViewsheetService viewsheetService)
   {
      this.holder = holder;
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void getModel(@ClusterProxyKey String id,
                        GetVSObjectModelEvent event, Principal principal,
                        CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      VSAssembly assembly = (VSAssembly)viewsheet.getAssembly(event.getName());
      holder.refreshVSObject(assembly, rvs, null, box, dispatcher);

      if(assembly instanceof TableDataVSAssembly) {
         int hint = VSAssembly.BINDING_CHANGED;
         holder.execute(rvs, event.getName(), null, hint, dispatcher);
         BaseTableService.loadTableData(
            rvs, event.getName(), 0, 0, 100, null, dispatcher);
      }

      return null;
   }

   private final CoreLifecycleService holder;
   private final ViewsheetService viewsheetService;
}
