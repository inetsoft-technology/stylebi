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
package inetsoft.web.binding.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.graph.data.DataSet;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.graph.ChangeChartDataProcessor;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.event.ConvertChartRefEvent;
import inetsoft.web.binding.event.RefreshBindingTreeEvent;
import inetsoft.web.binding.handler.*;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Objects;

@Controller
public class ConvertChartRefController {
   /**
    * Convert to measrue.
    */
   public static final int CONVERT_TO_MEASURE = VSEventUtil.CONVERT_TO_MEASURE;
   /**
    * Convert to dimension.
    */
   public static final int CONVERT_TO_DIMENSION = VSEventUtil.CONVERT_TO_DIMENSION;
   /**
    * Set geographic.
    */
   public static final String SET_GEOGRAPHIC = "set";
   /**
    * Clear geographic.
    */
   public static final String CLEAR_GEOGRAPHIC = "clear";

   /**
    * Creates a new instance of <tt>ConvertChartRefController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    * @param chartHandler
    * @param viewsheetService
    */
   @Autowired
   public ConvertChartRefController(
      VSBindingService bindingFactory,
      RuntimeViewsheetRef runtimeViewsheetRef,
      CoreLifecycleService coreLifecycleService,
      VSBindingTreeController bindingTreeController,
      VSAssemblyInfoHandler assemblyInfoHandler,
      VSChartHandler chartHandler,
      VSChartDataHandler chartDataHandler,
      ViewsheetService viewsheetService)
   {
      this.bindingFactory = bindingFactory;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.coreLifecycleService = coreLifecycleService;
      this.bindingTreeController = bindingTreeController;
      this.assemblyInfoHandler = assemblyInfoHandler;
      this.chartHandler = chartHandler;
      this.chartDataHandler = chartDataHandler;
      this.viewsheetService = viewsheetService;
   }

   @MessageMapping("/vs/chart/convertRef")
   public void convertChartRef(@Payload ConvertChartRefEvent event,
                               Principal principal, CommandDispatcher dispatcher,
                               @LinkUri String linkUri) throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      String name = event.name();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet vs = rvs.getViewsheet();
      ChartVSAssembly chart = (ChartVSAssembly) vs.getAssembly(name);

      if(chart == null) {
         LOG.warn("Chart assembly is missing, failed to process convert chart " +
                     "reference event: " + name);
         return;
      }

      // type is lost in json serialization, needed for confirm
      event.binding().setType("chart");
      String tableName = VSUtil.getTableName(event.table());

      BindingModel obinding = bindingFactory.createModel(chart);
      String[] refNames = event.refNames();
      int changeType = event.changeType();
      vs = chart.getViewsheet();
      String table = chart.getTableName();
      ChartVSAssemblyInfo oinfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo().clone();
      ChartVSAssembly nchart = (ChartVSAssembly) bindingFactory.updateAssembly(event.binding(), chart);
      ChartVSAssemblyInfo ninfo = (ChartVSAssemblyInfo) nchart.getVSAssemblyInfo();

      // Handle source changed.
      if(assemblyInfoHandler.handleSourceChanged(nchart, tableName,
                                                 "/events/vs/chart/convertRef",
                                                 event, dispatcher, box))
      {
         return;
      }

      VSSelection bselection = oinfo.getBrushSelection();
      // new source
      SourceInfo osinfo = oinfo.getSourceInfo();
      SourceInfo nsinfo = ninfo.getSourceInfo();
      boolean nsrc = !Objects.equals(nsinfo, osinfo);

      // new source? create default aggregate info
      if(nsrc) {
         ninfo.setSourceInfo(nsinfo);
         VSUtil.setDefaultGeoColumns(ninfo.getVSChartInfo(), rvs, event.table());
         ninfo.getVSChartInfo().setAggregateInfo(new AggregateInfo());
         chartHandler.fixAggregateInfo(ninfo, vs, null);
      }
      else {
         ninfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      }

      VSChartInfo vsChartInfo = ninfo.getVSChartInfo();

      for(String refName : refNames) {
         // apply conversion
         chartDataHandler.fixAggregateInfo(vsChartInfo, refName, changeType);

         // removed the bound data ref
         boolean changed = chartDataHandler.fixChartInfo(vsChartInfo, refName, changeType) || nsrc;
         boolean detectRequired = false;
         boolean isGeo = vsChartInfo.isGeoColumn(refName);

         // fix geo columns

         if(!Tool.equals(osinfo, nsinfo)) {
            // don't clear aggregate info which is converted just now
            AggregateInfo ainfo = vsChartInfo.getAggregateInfo();
            vsChartInfo.getGeoColumns().clear();

            if(vsChartInfo instanceof MapInfo) {
               ((MapInfo) vsChartInfo).removeGeoFields();
            }

            vsChartInfo.setAggregateInfo(ainfo);
         }

         // keep geographic
         chartHandler.updateGeoColumns(box, vs, chart, vsChartInfo);

         if(isGeo) {
            boolean todim = changeType == CONVERT_TO_DIMENSION;
            boolean isdim = !todim;
            boolean refChanged = chartHandler.fixMapInfo(vsChartInfo, refName, CLEAR_GEOGRAPHIC);
            boolean isDate = chartDataHandler.isDate(oinfo.getVSChartInfo(), refName);

            detectRequired = todim && !isDate;
            chartHandler.changeGeographic(vsChartInfo, refName, CLEAR_GEOGRAPHIC, isdim);

            if(detectRequired) {
               chartHandler.changeGeographic(vsChartInfo, refName, SET_GEOGRAPHIC, todim);
            }

            chartHandler.updateGeoColumns(box, vs, chart, vsChartInfo);
            changed = changed || refChanged || detectRequired;
         }

         int hint = 0;

         // new source? set new chart assembly info
         if(nsrc) {
            hint = chart.setVSAssemblyInfo(ninfo);
         }

         box.updateAssembly(chart.getAbsoluteName());

         if(changed) {
            // clear runtime fields so the aesthetic fields won't be restored
            // in update() of VSChartInfo
            vsChartInfo.clearRuntime();

            vsChartInfo = (VSChartInfo) new ChangeChartDataProcessor(vsChartInfo).process();
            ninfo.setVSChartInfo(vsChartInfo);
            hint |= chartHandler.createCommands(oinfo, ninfo);
            boolean dchanged = (hint & VSAssembly.INPUT_DATA_CHANGED) ==
               VSAssembly.INPUT_DATA_CHANGED;

            // clear brush for data changed
            if(dchanged && table != null && bselection != null && !bselection.isEmpty()) {
               hint = hint | VSAssembly.OUTPUT_DATA_CHANGED;
               vs.setBrush(table, chart);
            }

            try {
               ChangedAssemblyList clist =
                  coreLifecycleService.createList(true, dispatcher, rvs, linkUri);
               box.processChange(name, hint, clist);
               coreLifecycleService.execute(rvs, name, linkUri, clist, dispatcher, true);
            }
            finally {
               vs.setBrush(table, null);
            }

            if(detectRequired) {
               DataSet source = chartHandler.getChartData(box, chart);
               SourceInfo sourceInfo = chart.getSourceInfo();
               chartHandler.autoDetect(vs, sourceInfo, vsChartInfo, refName, source);
            }
         }
      }

      assemblyInfoHandler.checkTrap(oinfo, ninfo, obinding, dispatcher, rvs);
      BindingModel binding = bindingFactory.createModel(chart);
      SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
      dispatcher.sendCommand(bcommand);

      RefreshBindingTreeEvent refreshBindingTreeEvent = new RefreshBindingTreeEvent();
      refreshBindingTreeEvent.setName(name);
      bindingTreeController.getBinding(refreshBindingTreeEvent, principal, dispatcher);
   }

   private final VSBindingService bindingFactory;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final CoreLifecycleService coreLifecycleService;
   private final VSBindingTreeController bindingTreeController;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
   private final VSChartHandler chartHandler;
   private final VSChartDataHandler chartDataHandler;
   private final ViewsheetService viewsheetService;
   private static final Logger LOG =
      LoggerFactory.getLogger(ConvertChartRefController.class);
}
