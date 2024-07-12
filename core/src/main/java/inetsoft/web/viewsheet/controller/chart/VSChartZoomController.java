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
import inetsoft.analytic.composition.event.ChartEvent;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.DataMap;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.DateComparisonUtil;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.ClearChartLoadingCommand;
import inetsoft.web.viewsheet.event.chart.VSChartZoomEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class VSChartZoomController extends VSChartController<VSChartZoomEvent> {
   @Autowired
   public VSChartZoomController(RuntimeViewsheetRef runtimeViewsheetRef,
                                PlaceholderService placeholderService,
                                ViewsheetService viewsheetService)
   {
      super(runtimeViewsheetRef, placeholderService, viewsheetService);
   }

   /**
    * Zoom the chart
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if the chart could not be zoomed.
    */
   // from analytic.composition.event.ZoomEvent.process()
   @Undoable
   @LoadingMask
   @MessageMapping("/vschart/zoom")
   public void eventHandler(@Payload VSChartZoomEvent event, @LinkUri String linkUri,
                            Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      processEvent(event, principal, linkUri, dispatcher, chartState -> {
         try {
            return doZoom(event, chartState, linkUri, dispatcher);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
         finally {
            // clear chart loading mask after zoom finished
            dispatcher.sendCommand(event.getChartName(), new ClearChartLoadingCommand());
         }
      });
   }

   private int doZoom(VSChartZoomEvent event,
                      VSChartStateInfo chartState,
                      String linkUri,
                      CommandDispatcher dispatcher)
      throws Exception
   {
      String name = event.getChartName();
      String selected = event.getSelected();
      boolean rangeSelection = event.getRangeSelection();
      boolean exclude = event.getExclude();
      ChartVSAssembly chartAssembly = chartState.getAssembly();
      VSSelection bselection = chartAssembly.getBrushSelection();
      String table = chartAssembly.getTableName();
      ViewsheetSandbox box = chartState.getViewsheetSandbox();
      Viewsheet vs = chartState.getViewsheet();
      int hint = 0;

      try {
         // clear brush for zoom and brush are exclusive
         if(bselection != null && !bselection.isEmpty()) {
            hint = hint | chartAssembly.setBrushSelection(null);
            vs.setBrush(table, chartAssembly);
         }

         VGraphPair pair = box.getVGraphPair(name);

         if(pair == null || !isSelectionActionSupported(pair, dispatcher)) {
            return VSAssembly.NONE_CHANGED;
         }

         VGraph vgraph = pair.getRealSizeVGraph();

         if(vgraph == null) {
            return VSAssembly.NONE_CHANGED;
         }

         VSDataSet alens = (VSDataSet) box.getData(name, true, DataMap.ZOOM);
         DataSet vdset = vgraph.getCoordinate().getDataSet();
         VSChartInfo chartInfo = chartState.getChartInfo();
         RuntimeViewsheet rvs = chartState.getRuntimeViewsheet();
         String ctype = ((ChartVSAssemblyInfo)
            VSEventUtil.getAssemblyInfo(rvs, chartAssembly)).getCubeType();
         VSDataSet lens = vdset instanceof VSDataSet
            ? (VSDataSet) vdset : (VSDataSet) box.getData(name);
         VSSelection selection = ChartEvent.getVSSelection(
            selected, lens, alens, vdset, rangeSelection, chartInfo,
            false, ctype, true, false, false);
         PlotDescriptor plot = chartState.getChartDescriptor().getPlotDescriptor();

         // clear web map zoom
         plot.setZoom(1);
         plot.setPanX(0);
         plot.setPanY(0);
         plot.setLonLat(null);

         if(selection != null) {
            DateComparisonUtil.fixDatePartSelection(chartAssembly, lens, selection);
         }

         // cancel zoom
         if(selected == null) {
            hint = hint | chartAssembly.setExcludeSelection(selection);
            hint = hint | chartAssembly.setZoomSelection(selection);
         }
         // exclude data points
         else if(exclude && selection != null) {
            // append exclusion condition
            VSSelection sel0 = chartAssembly.getExcludeSelection();

            if(sel0 != null) {
               for(int i = 0; i < sel0.getPointCount(); i++) {
                  selection.addPoint(sel0.getPoint(i));
               }
            }

            hint = hint | chartAssembly.setExcludeSelection(selection);
         }
         // zoom in
         else {
            hint = hint | chartAssembly.setZoomSelection(selection);
         }

         box.updateAssembly(chartAssembly.getAbsoluteName());

         ChangedAssemblyList clist = new ChangedAssemblyList(false);
         box.processChange(name, hint, clist);
         execute(rvs, name, linkUri, hint, dispatcher);

         // share categorical frame
         if(selection != null && !selection.isEmpty()) {
            box.getVGraphPair(name);
         }
      }
      finally {
//         command.addCommand(new MessageCommand("", MessageCommand.OK));
//         vs.setBrush(table, null);
      }

      return -1;
   }

}
