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
import inetsoft.analytic.composition.event.ChartVSSelectionUtil;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.DataMap;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.graph.VSSelection;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.command.ClearChartLoadingCommand;
import inetsoft.web.viewsheet.event.chart.VSChartBrushEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class VSChartBrushController extends VSChartController<VSChartBrushEvent> {
   @Autowired
   public VSChartBrushController(RuntimeViewsheetRef runtimeViewsheetRef,
                                 CoreLifecycleService coreLifecycleService,
                                 ViewsheetService viewsheetService)
   {
      super(runtimeViewsheetRef, coreLifecycleService, viewsheetService);
   }

   /**
    * Brush the chart
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if the chart could not be brushed.
    */
   // from analytic.composition.event.BrushEvent.process()
   @Undoable
   @LoadingMask
   @MessageMapping("/vschart/brush")
   @HandleAssetExceptions
   public void eventHandler(@Payload VSChartBrushEvent event, @LinkUri String linkUri,
                            Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      processEvent(event, principal, chartState -> {
         try {
            brush(event, chartState, linkUri, dispatcher);
         }
         catch(Exception e) {
            LOG.error("Failed to brush: " + e, e);
            throw new RuntimeException(e);
         }
      });
   }

   private void brush(VSChartBrushEvent event, VSChartStateInfo chartState, String linkUri,
                      CommandDispatcher dispatcher) throws Exception
   {
      if(chartState == null) {
         return;
      }

      String name = event.getChartName();
      String selected = event.getSelected();
      boolean rangeSelection = event.getRangeSelection();
      final ViewsheetSandbox box = chartState.getViewsheetSandbox();

      VGraphPair pair = box.getVGraphPair(name);

      if(pair == null) {
         return;
      }

      boolean cscript = pair.isChangedByScript();

      // do not apply brush for chart has script
      if(cscript && (selected != null && selected.length() > 0)) {
         //command.addCommand(new MessageCommand("", MessageCommand.OK));
         return;
      }

      syncFormData(box, chartState.getAssembly());
      VGraph vgraph = pair.getRealSizeVGraph();

      if(vgraph == null || !isSelectionActionSupported(pair, dispatcher)) {
         return;
      }

      VSDataSet alens = (VSDataSet) box.getData(name, true, DataMap.ZOOM);
      ChartVSAssembly chartAssembly = chartState.getAssembly();
      VSChartInfo chartInfo = chartState.getChartInfo();
      RuntimeViewsheet rvs = chartState.getRuntimeViewsheet();
      String ctype = ((ChartVSAssemblyInfo)
         VSEventUtil.getAssemblyInfo(rvs, chartAssembly)).getCubeType();
      DataSet vdset = vgraph.getCoordinate().getDataSet();
      VSDataSet lens = vdset instanceof VSDataSet
         ? (VSDataSet) vdset : (VSDataSet) box.getData(name);
      // with Others expanded
      VSSelection selection = ChartVSSelectionUtil.getVSSelection(
         selected, lens, alens, vdset, rangeSelection, chartInfo, false, ctype, true, false, false);
      // without Others expanded
      VSSelection selection2 = ChartVSSelectionUtil.getVSSelection(
         selected, lens, alens, vdset, rangeSelection, chartInfo, false, ctype, false, false, false);
      VSSelection oselection = chartAssembly.getBrushSelection();
      String table = chartAssembly.getTableName();

      // if expanding Others results in empty selection, just brush 'Others' as is. (52213)
      if(selection == null && selection2 != null) {
         selection = selection2.clone();
      }

      if(Tool.equals(oselection, selection)) {
         return;
      }

      if(selection2 == null) {
         return;
      }

      DateComparisonUtil.fixDatePartSelection(chartAssembly, lens, selection);
      DateComparisonUtil.fixDatePartSelection(chartAssembly, lens, selection2);
      selection.setOrigSelection(selection2);
      Viewsheet vs = chartState.getViewsheet();
      box.lockRead();

      try {
         // clear the others
         vs.setBrush(table, chartAssembly);
         int hint = chartAssembly.setBrushSelection(selection);
         box.updateAssembly(name);
         ChangedAssemblyList clist = createList(false, dispatcher, rvs, linkUri);
         box.processChange(name, hint, clist);
         execute(rvs, name, linkUri, hint, dispatcher);
      }
      finally {
         box.unlockRead();
         vs.setBrush(table, null);
      }
   }

   /**
    * Clear form data if binding source is form table,
    * to avoid using submitted form data when brush chart.
    */
   private void syncFormData(ViewsheetSandbox box, ChartVSAssembly chartAssembly) throws Exception
   {
      SourceInfo sinfo = chartAssembly.getSourceInfo();

      if(!box.isRuntime() || sinfo == null || sinfo.isEmpty() ||
         sinfo.getType() != XSourceInfo.VS_ASSEMBLY)
      {
         return;
      }

      String tname = sinfo.getSource();

      if(!VSUtil.isVSAssemblyBinding(tname)) {
         return;
      }

      tname = VSUtil.getVSAssemblyBinding(tname);
      FormTableLens lens = box.getFormTableLens(tname);

      if(lens != null) {
         box.syncFormData(tname);
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(VSChartBrushController.class);
}
