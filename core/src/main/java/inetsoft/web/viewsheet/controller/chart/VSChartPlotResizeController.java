/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.chart.VSChartPlotResizeEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class VSChartPlotResizeController extends VSChartController<VSChartPlotResizeEvent> {
   @Autowired
   public VSChartPlotResizeController(RuntimeViewsheetRef runtimeViewsheetRef,
                                      PlaceholderService placeholderService,
                                      ViewsheetService viewsheetService)
   {
      super(runtimeViewsheetRef, placeholderService, viewsheetService);
   }

   /**
    * Resizes the plot
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if the plot spacing could not be resized.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/vschart/resize-plot")
   public void eventHandler(@Payload VSChartPlotResizeEvent event, @LinkUri String linkUri,
                            Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      this.processEvent(event, principal, linkUri, dispatcher, chartState -> {
         final boolean heightResized = event.isHeightResized();
         final double sizeRatio = event.getSizeRatio();
         final VSChartInfo chartInfo = chartState.getChartInfo();
         final boolean reset = event.isReset();

         if(reset) {
            chartInfo.setUnitWidthRatio(1);
            chartInfo.setUnitHeightRatio(1);
            chartInfo.setWidthResized(false);
            chartInfo.setHeightResized(false);
         }
         else {
            // for wordcloud/circular-network, the ratio is equally applied to both width/height.
            boolean square = GraphTypeUtil.isWordCloud(chartInfo) ||
               chartInfo.getChartType() == GraphTypes.CHART_CIRCULAR;

            if(!heightResized || square) {
               chartInfo.setUnitWidthRatio(sizeRatio);
               chartInfo.setWidthResized(true);
            }

            if(heightResized || square) {
               chartInfo.setUnitHeightRatio(sizeRatio);
               chartInfo.setHeightResized(true);
            }
         }

         return VSAssembly.VIEW_CHANGED;
      });
   }
}
