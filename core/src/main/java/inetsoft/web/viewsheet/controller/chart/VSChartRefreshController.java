/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.viewsheet.controller.chart;


import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class VSChartRefreshController extends VSChartController<VSChartRefreshEvent> {
   @Autowired
   public VSChartRefreshController(RuntimeViewsheetRef runtimeViewsheetRef,
                                   PlaceholderService placeholderService,
                                   VSObjectModelFactoryService objectModelService,
                                   ViewsheetService viewsheetService,
                                   VSRefreshController vsRefreshController)
   {
      super(runtimeViewsheetRef, placeholderService, viewsheetService);
      this.objectModelService = objectModelService;
      this.vsRefreshController = vsRefreshController;
   }

   @Override
   @MessageMapping("/vschart/refresh-chart")
   public void eventHandler(@Payload VSChartRefreshEvent event,
                            @LinkUri String linkUri,
                            Principal principal,
                            CommandDispatcher dispatcher) throws Exception
   {
      this.processEvent(event, principal, chartState -> {
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

   private final VSObjectModelFactoryService objectModelService;
   private final VSRefreshController vsRefreshController;
}
