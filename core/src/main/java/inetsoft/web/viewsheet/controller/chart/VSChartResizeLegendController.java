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
import inetsoft.graph.GraphConstants;
import inetsoft.graph.internal.DimensionD;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.viewsheet.graph.LegendDescriptor;
import inetsoft.uql.viewsheet.graph.LegendsDescriptor;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.chart.VSChartLegendResizeEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;

@Controller
public class VSChartResizeLegendController extends VSChartController<VSChartLegendResizeEvent> {
   @Autowired
   public VSChartResizeLegendController(RuntimeViewsheetRef runtimeViewsheetRef,
                                        CoreLifecycleService coreLifecycleService,
                                        ViewsheetService viewsheetService)
   {
      super(runtimeViewsheetRef, coreLifecycleService, viewsheetService);
   }

   /**
    * Resizes the legend(s)
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if the plot spacing could not be resized.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/vschart/resize-legend")
   public void eventHandler(@Payload VSChartLegendResizeEvent event,
                            @LinkUri String linkUri,
                            Principal principal,
                            CommandDispatcher dispatcher) throws Exception
   {
      processEvent(event, principal, linkUri, dispatcher, chartState -> {
         LegendsDescriptor legendsDes = chartState.getLegendsDes();

         //use at least one pixel by one pixel
         if(legendsDes != null && legendsDes.getLayout() != LegendsDescriptor.IN_PLACE) {
            try {
               Dimension maxSize = chartState.getChartAssemblyInfo().getMaxSize();
               int maxWidth = 1;
               int maxHeight = 1;

               if(maxSize != null) {
                  maxWidth = maxSize.width;
                  maxHeight = maxSize.height;
               }

               double width = event.getLegendWidth() / maxWidth;
               double height = event.getLegendHeight() / maxHeight;
               int layout = legendsDes.getLayout();

               if(layout == GraphConstants.RIGHT || layout == GraphConstants.LEFT) {
                  width += legendsDes.getGap();
               }
               else if(layout == GraphConstants.BOTTOM || layout == GraphConstants.TOP) {
                  height += legendsDes.getGap();
               }

               DimensionD size = new DimensionD(width, height);
               legendsDes.setPreferredSize(size);
            }
            catch(Exception ex) {
               throw new RuntimeException(ex);
            }
         }
         else {
            LegendDescriptor ldesc = GraphUtil.getLegendDescriptor(
               chartState.getChartInfo(), legendsDes, event.getField(),
               event.getTargetFields(), event.getLegendType(), event.isNodeAesthetic());

            if(ldesc != null) {
               double nw = event.getLegendWidth();
               double nh = event.getLegendHeight();
               Dimension maxSize = event.getMaxSize();

               if(maxSize != null) {
                  ldesc.setPreferredSize(new DimensionD(nw / maxSize.width, nh / maxSize.height));
               }
               else {
                  ldesc.setPreferredSize(new DimensionD(nw, nh));
               }
            }
         }

         return -1;
      });
   }
}
