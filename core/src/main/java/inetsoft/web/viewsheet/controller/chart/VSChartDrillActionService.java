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
import inetsoft.graph.VGraph;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.binding.handler.VSChartDataHandler;
import inetsoft.web.viewsheet.event.chart.VSChartDrillActionEvent;
import inetsoft.web.viewsheet.handler.VSDrillHandler;
import inetsoft.web.viewsheet.handler.chart.VSChartDrillHandler;
import inetsoft.web.viewsheet.model.ChartDrillFilterAction;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Service
@ClusterProxy
public class VSChartDrillActionService extends VSChartControllerService<VSChartDrillActionEvent> {
   public VSChartDrillActionService(CoreLifecycleService coreLifecycleService,
                                    ViewsheetService viewsheetService,
                                    VSChartAreasServiceProxy vsChartAreasService,
                                    VSChartDataHandler dataHandler,
                                    VSChartDrillHandler chartDrillHandler,
                                    VSDrillHandler vsDrillHandler)
   {
      super(coreLifecycleService, viewsheetService, vsChartAreasService);

      this.chartDrillHandler = chartDrillHandler;
      this.vsDrillHandler = vsDrillHandler;
      this.dataHandler = dataHandler;
   }

   @Override
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void eventHandler(@ClusterProxyKey String runtimeId,
                            VSChartDrillActionEvent event,
                            String linkUri,
                            Principal principal,
                            CommandDispatcher dispatcher) throws Exception
   {
      processEvent(runtimeId, event, principal, linkUri, dispatcher, chartState -> {
         try {
            return drillAction(event, chartState, dispatcher, linkUri, principal);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      });

      return null;
   }



   public int drillAction(VSChartDrillActionEvent event, VSChartStateInfo chartState,
                          CommandDispatcher dispatcher, String linkUri, Principal principal)
      throws Exception
   {
      if(chartState == null) {
         return VSAssembly.NONE_CHANGED;
      }

      String name = event.getChartName();
      String selected = event.getSelected();
      boolean rangeSelection = event.getRangeSelection();
      final ViewsheetSandbox box = chartState.getViewsheetSandbox();
      RuntimeViewsheet rvs = chartState.getRuntimeViewsheet();
      Viewsheet vs = rvs.getViewsheet();
      ChartVSAssembly assembly = (ChartVSAssembly) vs.getAssembly(name);
      ChartVSAssemblyInfo oinfo = (ChartVSAssemblyInfo) assembly.getVSAssemblyInfo().clone();
      VGraphPair pair = box.getVGraphPair(name, true);

      if(pair == null) {
         return VSAssembly.NONE_CHANGED;
      }

      VGraph vgraph = pair.getRealSizeVGraph();
      VSDataSet lens = (VSDataSet) box.getData(name);
      ChartVSAssembly chart = chartState.getAssembly();

      if(lens == null && pair.isChangedByScript0() || vgraph == null) {
         //command.addCommand(new MessageCommand(Catalog.getCatalog().getString(
         //   "action.script.graph"), MessageCommand.INFO));
         return VSAssembly.NONE_CHANGED;
      }

      VSSelection selection = chartDrillHandler.getVSSelection(rvs, chart, vgraph,
                                                               selected, rangeSelection);

      List<String> fields = getSelectedFieldName(selection, chart);

      if(fields.size() == 0) {
         return 0;
      }

      ChartDrillFilterAction filterInfo  = new ChartDrillFilterAction();
      filterInfo.setSelected(selected);
      filterInfo.setRangeSelection(rangeSelection);
      filterInfo.setFields(fields)
         .setAssemblyName(name)
         .setDrillUp(event.isDrillUp());
      vsDrillHandler.processDrillAction(filterInfo, dispatcher, linkUri, principal);

      // runtime info may be used during processing, delay clearing it until after all
      // processing has completed.
      chart.getVSChartInfo().clearRuntime();
      ChartVSAssemblyInfo ninfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      ChangeChartProcessor pro = new ChangeChartProcessor();
      pro.fixAggregateRefs(ninfo.getVSChartInfo(), oinfo.getVSChartInfo());
      dataHandler.changeChartAesthetic(rvs, ninfo);

      return VSAssembly.INPUT_DATA_CHANGED;
   }

   private List<String> getSelectedFieldName(VSSelection selection, ChartVSAssembly chartAssembly) {
      List<String> fieldNames = new ArrayList<>();

      if(selection == null) {
         return fieldNames;
      }

      VSChartInfo chartInfo = chartAssembly.getVSChartInfo();

      for(int i = 0; i < selection.getPointCount(); i++) {
         VSPoint point = selection.getPoint(i);

         for(int j = point.getValueCount() - 1; j >= 0; j--) {
            VSFieldValue value = point.getValue(j);
            ChartRef ref = chartDrillHandler.getFieldByName(chartInfo, value.getFieldName());

            if(!(ref instanceof VSDimensionRef) || fieldNames.contains(value.getFieldName())
               || ((VSDimensionRef) ref).isDynamic())
            {
               continue;
            }

            fieldNames.add(ref.getFullName());
         }
      }

      return fieldNames;
   }

   private final VSChartDrillHandler chartDrillHandler;
   private final VSDrillHandler vsDrillHandler;
   private final VSChartDataHandler dataHandler;
}
