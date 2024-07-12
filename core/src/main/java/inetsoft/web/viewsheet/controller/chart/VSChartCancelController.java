/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.viewsheet.controller.chart;


import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.util.QueryManager;
import inetsoft.web.viewsheet.event.chart.CancelEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class VSChartCancelController extends VSChartController<CancelEvent> {
   @Autowired
   public VSChartCancelController(RuntimeViewsheetRef runtimeViewsheetRef,
                                  PlaceholderService placeholderService,
                                  ViewsheetService viewsheetService)
   {
      super(runtimeViewsheetRef, placeholderService, viewsheetService);
   }

   @Override
   @MessageMapping("/vschart/cancel-query")
   public void eventHandler(@Payload CancelEvent event,
                            @LinkUri String linkUri,
                            Principal principal,
                            CommandDispatcher dispatcher) throws Exception
   {
      this.processEvent(event, principal, chartState -> {
         cancelQuery(event, chartState, principal, dispatcher);
      });
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
