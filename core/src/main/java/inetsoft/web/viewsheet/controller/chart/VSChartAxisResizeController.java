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
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.chart.VSChartAxisResizeEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class VSChartAxisResizeController extends VSChartController<VSChartAxisResizeEvent> {
   @Autowired
   public VSChartAxisResizeController(RuntimeViewsheetRef runtimeViewsheetRef,
                                      PlaceholderService placeholderService,
                                      ViewsheetService viewsheetService)
   {
      super(runtimeViewsheetRef, placeholderService, viewsheetService);
   }

   /**
    * Resizes an axis vertically
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if the chart axis could not be resized.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/vschart/resize-axis")
   public void eventHandler(@Payload VSChartAxisResizeEvent event,
                            @LinkUri String linkUri,
                            Principal principal,
                            CommandDispatcher dispatcher) throws Exception
   {
      processEvent(event, principal, linkUri, dispatcher, chartState -> {
         String axisType = event.getAxisType();
         AxisDescriptor axisDesc = getAxisDesc(chartState, event.getAxisField(),
                                               "x".equals(axisType));

         if(axisDesc != null) {
            try {
               if("x".equals(axisType)) {
                  axisDesc.setAxisHeight(event.getAxisSize());
               }
               else if("y".equals(axisType)) {
                  axisDesc.setAxisWidth(event.getAxisSize());
               }
               else {
                  throw new RuntimeException("Invalid event properties");
               }

               chartState.getChartInfo().clearRuntime();
            }
            catch(Exception ex) {
               throw new RuntimeException(ex);
            }
         }

         return VSAssembly.VIEW_CHANGED;
      });
   }

   // from LayoutLegendEvent.process()
   // Note: chart region handler has some extra logic that might affect the descriptor returned
   private AxisDescriptor getAxisDesc(VSChartStateInfo chartState,
                                      String axisField, boolean xAxisFlag)
   {
      VSChartInfo chartInfo = chartState.getChartInfo();
      ChartVSAssemblyInfo assemblyInfo = chartState.getChartAssemblyInfo();
      ChartRef ref = null;

      if(assemblyInfo != null) {
         ref = (ChartRef) chartState.getChartAssemblyInfo().getDCBIndingRef(axisField);
      }

      if(ref == null) {
         ref = chartInfo.getFieldByName(axisField, false);
      }

      AxisDescriptor axisDesc = null;

      if(ref == null && assemblyInfo != null) {
         ref = (ChartRef) assemblyInfo.getDCBIndingRef(axisField);
      }

      if(chartInfo.isSeparatedGraph() && !(chartInfo instanceof MergedChartInfo)
         || GraphUtil.isCategorical(ref) || chartInfo instanceof GanttChartInfo)
      {
         if(ref != null) {
            axisDesc = ref.getAxisDescriptor();
         }
         // for mekko chart, y axis
         else {
            axisDesc = chartInfo.getAxisDescriptor();
         }
      }
      else if(ref instanceof ChartAggregateRef && ((ChartAggregateRef) ref).isSecondaryY()) {
         axisDesc = chartInfo.getAxisDescriptor2();
      }
      // in merged graph, if there is measure on y, x uses ref descriptor
      // this needs to be the same as DefaultGraphGenerator
      else if(xAxisFlag && ref != null && !GraphUtil.getMeasures(chartInfo.getYFields()).isEmpty())
      {
         axisDesc = ref.getAxisDescriptor();
      }
      else {
         axisDesc = chartInfo.getAxisDescriptor();
      }

      return axisDesc;
   }
}
