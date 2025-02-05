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
package inetsoft.web.vswizard.controller;

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSModelTrapContext;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AbstractModelTrapContext;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.binding.event.VSDndEvent;
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
import org.springframework.web.bind.annotation.*;

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

   @PutMapping("/api/vs/wizard/checktrap")
   @ResponseBody
   public boolean checktrap(@DecodeParam("vsId") String vsId,
                            @RequestBody ConvertColumnEvent event,
                            Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = ViewsheetEngine.getViewsheetEngine().getViewsheet(vsId, principal);

      if(rvs == null) {
         return false;
      }

      VSTemporaryInfo tempInfo = temporaryInfoService.getVSTemporaryInfo(rvs);
      String[] columnNames = event.columnNames();
      int changeType = event.changeType();
      ChartVSAssembly tempChart = tempInfo.getTempChart();
      ChartVSAssemblyInfo old = (ChartVSAssemblyInfo) tempChart.getVSAssemblyInfo().clone();
      VSChartInfo tempChartInfo = tempChart.getVSChartInfo();
      AggregateInfo ainfo = (AggregateInfo) tempChartInfo.getAggregateInfo().clone();

      for(String columnName : columnNames) {
         vsChartDataHandler.fixAggregateInfo(tempChartInfo, columnName, changeType);

         for(int i = tempChartInfo.getXFieldCount(); i >= 0; i--) {
            ChartRef ref = tempChartInfo.getXField(i);

            if(ref instanceof VSChartDimensionRef && Tool.equals(columnName, ref.getFullName())) {
               VSChartAggregateRef agg = new VSChartAggregateRef();
               agg.setColumnValue(columnName);
               agg.setFormulaValue(AggregateFormula.COUNT_ALL.getFormulaName());
               agg.setDataRef(((VSChartDimensionRef) ref).getDataRef());
               tempChartInfo.removeXField(i);
               tempChartInfo.addYField(agg);
            }
         }
      }

      tempChartInfo.clearRuntime();
      VSModelTrapContext context = new VSModelTrapContext(rvs, true);
      AbstractModelTrapContext.TrapInfo trapInfo = context.checkTrap(old, tempChart.getVSAssemblyInfo());
      tempChartInfo.setAggregateInfo(ainfo);
      tempChart.setVSAssemblyInfo(old);

      return trapInfo.showWarning();
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

         dispatcher.sendCommand(new RefreshWizardTreeTriggerCommand());
      }
      finally {
         box.unlockRead();
      }
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSChartHandler chartHandler;
   private final VSChartDataHandler vsChartDataHandler;
   private final ViewsheetService viewsheetService;
   private final VSWizardBindingHandler bindingHandler;
   private final VSWizardTemporaryInfoService temporaryInfoService;
}
