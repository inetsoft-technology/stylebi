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

package inetsoft.web.viewsheet.controller.table;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.web.viewsheet.event.table.LoadTableDataEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class BaseTableLoadDataService extends BaseTableService<LoadTableDataEvent> {
   public BaseTableLoadDataService(CoreLifecycleService coreLifecycleService,
                                  ViewsheetService viewsheetService)
   {
      super(coreLifecycleService, viewsheetService);
   }


   @Override
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void eventHandler(@ClusterProxyKey String runtimeId,
                            LoadTableDataEvent event,
                            Principal principal,
                            CommandDispatcher dispatcher,
                            String linkUri) throws Exception
   {

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      String assemblyName = event.getAssemblyName();
      int start = event.getStart();
      int rowCount = event.getRowCount();
      int mode = 0;
      loadTableData(rvs, assemblyName, mode, start, rowCount, linkUri, dispatcher);

      return null;
   }
}
