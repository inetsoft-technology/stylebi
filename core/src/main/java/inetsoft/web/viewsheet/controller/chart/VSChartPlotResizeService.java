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
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.viewsheet.event.chart.VSChartPlotResizeEvent;
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class VSChartPlotResizeService extends VSChartControllerService<VSChartPlotResizeEvent>{

   public VSChartPlotResizeService(ViewsheetService viewsheetService,
                                   CoreLifecycleService coreLifecycleService,
                                   VSChartAreasServiceProxy vsChartAreasServiceProxy)
   {
      super(coreLifecycleService, viewsheetService, vsChartAreasServiceProxy);
   }

   @Override
   @ClusterWriteMethod
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void eventHandler(@ClusterProxyKey  String runtimeId, VSChartPlotResizeEvent event,
                            String linkUri, Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      processEvent(runtimeId, event, principal, linkUri, dispatcher, chartState -> {
         final boolean heightResized = event.isHeightResized();
         final double sizeRatio = event.getSizeRatio();
         final VSChartInfo chartInfo = chartState.getChartInfo();
         final boolean reset = event.isReset();

         if(reset) {
            chartInfo.setUnitWidthRatio(1);
            chartInfo.setUnitHeightRatio(1);
            // The percents have to go too, and back to 0 rather than 1: VGraphPair re-derives
            // unitWidthRatio/unitHeightRatio from them whenever they are >= 1, without consulting
            // the resized flags, so a reset that left them behind reported default/not-resized
            // while the old ratio was rebuilt on every subsequent layout. (1 is not the neutral
            // value here - it would re-derive the ratio as the plot's own baseline. 0 is the
            // field's declared default and the only value that turns the re-derivation off.) It
            // was invisible only because the enlargement itself, minPlot *= ratio, is gated on
            // the flag this branch does clear.
            chartInfo.setUnitWidthRatioPercent(0);
            chartInfo.setUnitHeightRatioPercent(0);
            chartInfo.setWidthResized(false);
            chartInfo.setHeightResized(false);
         }
         else {
            // for wordcloud/circular-network, the ratio is equally applied to both width/height.
            boolean square = GraphTypeUtil.isWordCloud(chartInfo) ||
               chartInfo.getChartType() == GraphTypes.CHART_CIRCULAR;

            if(!heightResized || square) {
               chartInfo.setUnitWidthRatio(sizeRatio);
               chartInfo.setUnitWidthRatioPercent(sizeRatio / chartInfo.getInitialWidthRatio());
               chartInfo.setWidthResized(true);
            }

            if(heightResized || square) {
               chartInfo.setUnitHeightRatio(sizeRatio);
               // Height branch sets the HEIGHT percent. It set the width percent, overwriting the
               // value the width branch had just computed on a square resize, and leaving the
               // height percent unset — which matters because VGraphPair recomputes
               // unitHeightRatio only when getUnitHeightRatioPercent() >= 1, so a height resize
               // was dropped on the next recompute.
               chartInfo.setUnitHeightRatioPercent(sizeRatio / chartInfo.getInitialHeightRatio());
               chartInfo.setHeightResized(true);
            }
         }

         return VSAssembly.VIEW_CHANGED;
      });

      return null;
   }
}
