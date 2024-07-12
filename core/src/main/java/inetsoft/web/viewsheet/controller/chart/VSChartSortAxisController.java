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
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.chart.VSChartSortAxisEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.ArrayList;

@Controller
public class VSChartSortAxisController extends VSChartController<VSChartSortAxisEvent> {
   @Autowired
   public VSChartSortAxisController(RuntimeViewsheetRef runtimeViewsheetRef,
                                    PlaceholderService placeholderService,
                                    VSBindingService bindingFactory,
                                    ViewsheetService viewsheetService)
   {
      super(runtimeViewsheetRef, placeholderService, viewsheetService);
      this.bindingFactory = bindingFactory;
   }

   /**
    * Sorts the Axis
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if the plot spacing could not be resized.
    */
   // from analytic.composition.event.SortAxisEvent.process()
   @Undoable
   @LoadingMask
   @MessageMapping("/vschart/sort-axis")
   public void eventHandler(@Payload VSChartSortAxisEvent event, @LinkUri String linkUri,
                            Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      processEvent(event, principal, linkUri, dispatcher, chartState -> {
         VSChartInfo chartInfo = chartState.getChartInfo();
         XDimensionRef innerdim = GraphUtil.getInnerDimRef(chartInfo, false);

         if(innerdim == null) {
            return VSAssembly.NONE_CHANGED;
         }

         int order = XConstants.SORT_VALUE_DESC;
         String sortOp = event.getSortOp();

         if("Asc".equals(sortOp)) {
            order = XConstants.SORT_ASC;
         }
         else if("Desc".equals(sortOp)) {
            order = XConstants.SORT_VALUE_ASC;
         }

         innerdim.setOrder(order);
         ((VSDimensionRef) innerdim).setSortByColValue(event.getSortField());

         if((innerdim.getOrder() & OrderInfo.SORT_SPECIFIC) != OrderInfo.SORT_SPECIFIC &&
            ((VSDimensionRef) innerdim).getManualOrderList() != null)
         {
            ((VSDimensionRef) innerdim).setManualOrderList(new ArrayList());
         }

         // sorting ignored for time series
         innerdim.setTimeSeries(false);
         ChartVSAssembly chartAssembly = (ChartVSAssembly) chartState
            .getRuntimeViewsheet().getViewsheet().getAssembly(event.getChartName());
         BindingModel binding = bindingFactory.createModel(chartAssembly);
         SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
         dispatcher.sendCommand(bcommand);

         return VSAssembly.OUTPUT_DATA_CHANGED;
      });
   }

   private final VSBindingService bindingFactory;
}
