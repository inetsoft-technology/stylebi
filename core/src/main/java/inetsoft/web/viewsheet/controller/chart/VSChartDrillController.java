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
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.chart.VSChartDrillEvent;
import inetsoft.web.viewsheet.handler.chart.VSChartDrillHandler;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Arrays;

@Controller
public class VSChartDrillController extends VSChartController<VSChartDrillEvent> {
   @Autowired
   VSChartDrillController(RuntimeViewsheetRef runtimeViewsheetRef,
                          PlaceholderService placeholderService,
                          VSBindingService bindingFactory,
                          ViewsheetService viewsheetService,
                          VSChartDrillHandler chartDrillHandler)
   {
      super(runtimeViewsheetRef, placeholderService, viewsheetService);

      this.bindingFactory = bindingFactory;
      this.chartDrillHandler = chartDrillHandler;
   }

   // from analytic.composition.event.DrillEvent.process()
   @Undoable
   @LoadingMask
   @MessageMapping("/vschart/drill")
   public void eventHandler(@Payload VSChartDrillEvent event, @LinkUri String linkUri,
                            Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      processEvent(event, principal, linkUri, dispatcher, chartState -> {
         try {
            return doDrill(event, chartState, dispatcher, principal);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      });
   }

   private int doDrill(@Payload VSChartDrillEvent event,
                       VSChartStateInfo chartState,
                       CommandDispatcher dispatcher, Principal principal)
      throws Exception
   {
      ViewsheetSandbox box = chartState.getViewsheetSandbox();
      // @by davidd, Stop all queries related to this Viewsheet to make way for
      // this Drill down request.
      box.cancelAllQueries();

      // from analytic.composition.event.DrillEvent.processChart()
      RuntimeViewsheet rvs = chartState.getRuntimeViewsheet();
      boolean viewer = rvs.isRuntime();
      boolean isX = ChartConstants.DRILL_DIRECTION_X.equals(event.getAxisType());
      VSChartInfo chartInfo = chartState.getChartInfo();
      ChartRef[] refs = isX ? chartInfo.getXFields() : chartInfo.getYFields();
      ChartRef ref = chartInfo.getFieldByName(event.getField(), true);
      ChartVSAssembly chartAssembly = chartState.getAssembly();

      if(!(ref instanceof VSDimensionRef)) {
         return VSAssembly.NONE_CHANGED;
      }

      chartDrillHandler.drill(ref, chartAssembly, event.isDrillUp(), viewer, principal, false);

      // reset axis height after drill up/down since the height is meaningless with the
      // change of binding.
      Arrays.stream(refs).filter(a -> a instanceof ChartRef)
         .forEach(a -> a.getAxisDescriptor().setAxisHeight(0));

      new ChangeChartProcessor() {
         public void process() {
            fixShapeField(chartInfo, chartInfo, getChartType(chartInfo, null));
            fixAggregateRefs(chartInfo);
         }
      }.process();

      //fix bug#37683 update to the runtime value
      box.updateAssembly(chartAssembly.getAbsoluteName());
      rvs.resetMVOptions();
      BindingModel binding = bindingFactory.createModel(chartAssembly);
      SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
      dispatcher.sendCommand(bcommand);

      return VSAssembly.INPUT_DATA_CHANGED;
   }

   private final VSBindingService bindingFactory;
   private final VSChartDrillHandler chartDrillHandler;
}
