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

package inetsoft.web.viewsheet.controller.chart;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.util.QueryManager;
import inetsoft.web.viewsheet.event.chart.CancelEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class VSChartCancelService extends VSChartControllerService<CancelEvent> {
   public VSChartCancelService(CoreLifecycleService coreLifecycleService,
                               ViewsheetService viewsheetService,
                               VSChartAreasServiceProxy vsChartAreasService)
   {
      super(coreLifecycleService, viewsheetService, vsChartAreasService);
   }

   @Override
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void eventHandler(@ClusterProxyKey String runtimeId,
                            CancelEvent event,
                            String linkUri, Principal principal,
                            CommandDispatcher dispatcher) throws Exception
   {
      this.processEvent(runtimeId, event, principal, chartState -> {
         cancelQuery(event, chartState, principal, dispatcher);
      });

      return null;
   }

   private void cancelQuery(CancelEvent event,
                            VSChartStateInfo chartState,
                            Principal principal,
                            CommandDispatcher dispatcher)
   {
      ViewsheetSandbox box = chartState.getViewsheetSandbox();
      String name = chartState.getChartAssemblyInfo().getAbsoluteName();

      // cancel the ongoing query
      QueryManager mgr = box.getQueryManager(name);
      mgr.cancel();
   }
}
