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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.command.ClearChartLoadingCommand;
import inetsoft.web.viewsheet.command.RefreshVSObjectCommand;
import inetsoft.web.viewsheet.controller.VSRefreshController;
import inetsoft.web.viewsheet.event.VSRefreshEvent;
import inetsoft.web.viewsheet.event.chart.VSChartRefreshEvent;
import inetsoft.web.viewsheet.model.*;
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class VSChartRefreshService extends VSChartControllerService<VSChartRefreshEvent> {

   public VSChartRefreshService(CoreLifecycleService coreLifecycleService,
                                VSObjectModelFactoryService objectModelService,
                                ViewsheetService viewsheetService,
                                VSRefreshController vsRefreshController,
                                VSChartAreasServiceProxy vsChartAreasServiceProxy)
   {
      super(coreLifecycleService, viewsheetService, vsChartAreasServiceProxy);

      this.objectModelService = objectModelService;
      this.vsRefreshController = vsRefreshController;
   }

   @Override
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void eventHandler(@ClusterProxyKey String runtimeId, VSChartRefreshEvent event,
                            String linkUri, Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      processEvent(runtimeId, event, principal, chartState -> {
         try {
            refreshChart(chartState, dispatcher, linkUri, principal);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
         finally {
            // clear chart loading mask after refresh finished
            dispatcher.sendCommand(event.getChartName(), new ClearChartLoadingCommand());
         }
      });

      return null;
   }

   private void refreshChart(VSChartStateInfo chartState, CommandDispatcher dispatcher,
                             String linkUri, Principal principal) throws Exception
   {
      String name = chartState.getChartAssemblyInfo().getAbsoluteName();
      RuntimeViewsheet rvs = chartState.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(rvs.getEmbedAssemblyInfo() != null) {
         VSRefreshEvent refresh = VSRefreshEvent.builder()
            .confirmed(true)
            .initing(false)
            .build();
         this.vsRefreshController.refreshViewsheet(refresh, principal, dispatcher, linkUri);
         return;
      }

      box.lockRead();

      try {
         Viewsheet vs = rvs.getViewsheet();
         ChartVSAssembly chart = (ChartVSAssembly) vs.getAssembly(name);
         VSObjectModel model = objectModelService.createModel(chart, rvs);
         RefreshVSObjectCommand rcommand = new RefreshVSObjectCommand();
         rcommand.setInfo(model);
         rcommand.setForce(true);
         rcommand.setWizardTemporary(chart.isWizardTemporary());
         dispatcher.sendCommand(rcommand);
      }
      finally {
         box.unlockRead();
      }
   }

   private final VSRefreshController vsRefreshController;
   private final VSObjectModelFactoryService objectModelService;

}
