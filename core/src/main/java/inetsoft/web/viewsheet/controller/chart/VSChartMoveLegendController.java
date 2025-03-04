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
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.graph.LegendDescriptor;
import inetsoft.uql.viewsheet.graph.LegendsDescriptor;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.chart.VSChartLegendRelocateEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.awt.geom.Point2D;
import java.security.Principal;

@Controller
public class VSChartMoveLegendController
   extends VSChartController<VSChartLegendRelocateEvent> {
   @Autowired
   public VSChartMoveLegendController(RuntimeViewsheetRef runtimeViewsheetRef,
                                      CoreLifecycleService coreLifecycleService,
                                      ViewsheetService viewsheetService)
   {
      super(runtimeViewsheetRef, coreLifecycleService, viewsheetService);
   }

   /**
    * Relocates the legend(s)
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if the plot spacing could not be resized.
    */
   @Override
   @Undoable
   @LoadingMask
   @MessageMapping("/vschart/move-legend")
   public void eventHandler(@Payload VSChartLegendRelocateEvent event,
                            @LinkUri String linkUri,
                            Principal principal,
                            CommandDispatcher dispatcher) throws Exception
   {
      processEvent(event, principal, linkUri, dispatcher, chartState -> {
         return moveLegend(event, chartState);
      });
   }

   private int moveLegend(@Payload VSChartLegendRelocateEvent event, VSChartStateInfo chartState) {
      final LegendsDescriptor legendsDes = chartState.getLegendsDes();
      final int layout = legendsDes.getLayout();
      final int newLayout = event.getLegendPosition();
      final int hint = layout != newLayout ? VSAssembly.VIEW_CHANGED : -1;
      legendsDes.setLayout(newLayout);
      chartState.getChartAssemblyInfo().setRTChartDescriptor(null);

      // only setPosition() if the legend is located in the "center"
      if(newLayout == LegendsDescriptor.IN_PLACE) {
         LegendDescriptor ldesc = GraphUtil.getLegendDescriptor(
            chartState.getChartInfo(), legendsDes, event.getField(),
            event.getTargetFields(), event.getLegendType(), event.isNodeAesthetic());

         if(ldesc != null) {
            Point2D.Double pos = new Point2D.Double(event.getLegendX(), event.getLegendY());
            ldesc.setPosition(pos);
         }
      }

      return hint;
   }
}
