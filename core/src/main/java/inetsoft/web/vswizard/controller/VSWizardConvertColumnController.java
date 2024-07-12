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
package inetsoft.web.vswizard.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.binding.handler.VSChartDataHandler;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.vswizard.HandleWizardExceptions;
import inetsoft.web.vswizard.command.RefreshWizardTreeTriggerCommand;
import inetsoft.web.vswizard.event.ConvertColumnEvent;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import inetsoft.web.vswizard.model.VSWizardConstants;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Controller
public class VSWizardConvertColumnController {

   @Autowired
   public VSWizardConvertColumnController(ViewsheetService viewsheetService,
                                          VSWizardBindingHandler bindingHandler,
                                          VSChartHandler chartHandler,
                                          VSChartDataHandler vsChartDataHandler,
                                          RuntimeViewsheetRef runtimeViewsheetRef,
                                          VSWizardTemporaryInfoService temporaryInfoService)
   {
      this.bindingHandler = bindingHandler;
      this.chartHandler = chartHandler;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.viewsheetService = viewsheetService;
      this.vsChartDataHandler = vsChartDataHandler;
      this.temporaryInfoService = temporaryInfoService;
   }

   @HandleWizardExceptions
   @MessageMapping("/vs/wizard/convertColumn")
   public void convertColumn(@Payload ConvertColumnEvent event,
                             Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.lockRead();

      try {
         AssetEntry baseEntry = vs.getBaseEntry();
         VSTemporaryInfo tempInfo = temporaryInfoService.getVSTemporaryInfo(rvs);
         String[] columnNames = event.columnNames();
         int changeType = event.changeType();
         ChartVSAssembly tempChart = tempInfo.getTempChart();
         VSChartInfo tempChartInfo = tempChart.getVSChartInfo();
         SourceInfo oldSource = tempChart.getSourceInfo();
         SourceInfo newSource = bindingHandler.getCurrentSource(
            new AssetEntry[]{ event.currentEntry() }, event.tableName());

         //change source
         boolean changeSource =
            bindingHandler.changeSource(newSource, oldSource, event,
                                        tempInfo, vs, "/events/vs/wizard/convertColumn", dispatcher);

         if(changeSource || tempChartInfo.getAggregateInfo() == null) {
            return;
         }
         else if(event.confirmed()) {
            VSUtil.setDefaultGeoColumns(tempChartInfo, rvs, event.tableName());
         }

         AggregateInfo aggInfo = (AggregateInfo) tempChartInfo.getAggregateInfo().clone();

         for(String columnName : columnNames) {
            vsChartDataHandler.fixAggregateInfo(tempChartInfo, columnName, changeType);
            boolean isGeo = tempChartInfo.getGeoColumns().getAttribute(columnName) != null;
            boolean detectRequired;

            if(isGeo) {
               boolean todim = changeType == VSWizardConstants.CONVERT_TO_DIMENSION;
               boolean isdim = !todim;
               boolean isDate = vsChartDataHandler.isDate(tempChartInfo, columnName);

               detectRequired = todim && !isDate;
               chartHandler.changeGeographic(tempChartInfo, columnName, VSChartHandler.CLEAR_GEOGRAPHIC, isdim);

               if(detectRequired) {
                  chartHandler.changeGeographic(tempChartInfo, columnName, VSChartHandler.SET_GEOGRAPHIC, todim);
               }
            }
         }

         AggregateInfo naggInfo = tempChartInfo.getAggregateInfo();

         List<TableAssembly> assemblies = bindingHandler.getLMTableAssemblies(baseEntry, principal);
         List<String> oldColumnPaths =
            getColumnPaths(baseEntry, aggInfo, assemblies, event.tableName() + "/", columnNames);
         List<String> newColumnPaths =
            getColumnPaths(baseEntry, naggInfo, assemblies, event.tableName() + "/", columnNames);

         dispatcher.sendCommand(new RefreshWizardTreeTriggerCommand());
      }
      finally {
         box.unlockRead();
      }
   }

   private List<String> getColumnPaths(AssetEntry baseEntry, AggregateInfo aggInfo,
                                       List<TableAssembly> assemblies,
                                       String tableName, String[] colNames)
   {
      List<String> colPaths = new ArrayList<>();

      for (String colName : colNames) {
         TableAssembly assembly = bindingHandler.getLMTableAssembly(assemblies, colName);
         DataRef ref = bindingHandler.getDataRef(aggInfo, colName);

         if(baseEntry.isLogicModel() && assembly != null) {
            tableName = assembly.getName() + "/";
         }
         else if(baseEntry.isLogicModel()) {
            tableName = "";
         }

         colPaths.add(bindingHandler.getColumnPath(aggInfo, ref, tableName));
      }

      return colPaths;
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSChartHandler chartHandler;
   private final VSChartDataHandler vsChartDataHandler;
   private final ViewsheetService viewsheetService;
   private final VSWizardBindingHandler bindingHandler;
   private final VSWizardTemporaryInfoService temporaryInfoService;
}
