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
package inetsoft.web.vswizard.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSModelTrapContext;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AbstractModelTrapContext;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.composer.model.vs.SourceChangeMessage;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import inetsoft.web.vswizard.model.VSWizardConstants;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
public class VSWizardDataService {
   @Autowired
   public VSWizardDataService(ViewsheetService viewsheetService,
                              VSWizardBindingHandler bindingHandler,
                              VSWizardTemporaryInfoService temporaryInfoService)
   {
      this.viewsheetService = viewsheetService;
      this.bindingHandler = bindingHandler;
      this.temporaryInfoService = temporaryInfoService;
   }

   public SourceChangeMessage checkSourceChanged(String runtimeId, String tableName,
                                                 Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      VSTemporaryInfo vsTemporaryInfo = temporaryInfoService.getVSTemporaryInfo(rvs);
      assert vsTemporaryInfo != null;
      SourceChangeMessage sourceChangeMessage = new SourceChangeMessage();
      sourceChangeMessage.setChanged(false);

      if(!vsTemporaryInfo.isDestroyed()) {
         SourceInfo oldSource = vsTemporaryInfo.getTempChart().getSourceInfo();
         sourceChangeMessage.setChanged(bindingHandler.sourceChanged(tableName, oldSource));
      }

      return sourceChangeMessage;
   }

   public boolean treeCheckTrap(String vsId, AssetEntry[] entries,
                                Principal principal)
      throws Exception
   {
      if(entries.length <= 1) {
         return false;
      }

      if(vsId == null) {
         return false;
      }

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      VSTemporaryInfo vsTemporaryInfo = temporaryInfoService.getVSTemporaryInfo(rvs);

      if(vsTemporaryInfo == null) {
         return false;
      }

      ChartVSAssembly chart = vsTemporaryInfo.getTempChart();

      if(chart == null) {
         return false;
      }

      ChartVSAssemblyInfo oinfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();

      if(oinfo == null) {
         return false;
      }
      else {
         oinfo = (ChartVSAssemblyInfo) oinfo.clone();
      }

      this.bindingHandler.updateTemporaryFields(rvs, entries, vsTemporaryInfo);

      if(rvs != null && rvs.getViewsheetSandbox() != null) {
         rvs.getViewsheetSandbox().updateAssembly(vsTemporaryInfo.getTempChart().getAbsoluteName());
      }
      
      ChartVSAssemblyInfo ninfo =
         (ChartVSAssemblyInfo) vsTemporaryInfo.getTempChart().getVSAssemblyInfo();
      boolean trap = checkTrap0(rvs, oinfo, ninfo);
      vsTemporaryInfo.getTempChart().setVSAssemblyInfo(oinfo);

      return trap;
   }

   public boolean aggregateCheckTrap(String id, ChartBindingModel tempChartModel ,
                                     Principal principal)
      throws Exception
   {

      if(id == null) {
         return false;
      }

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      Viewsheet vs = rvs.getViewsheet();
      VSTemporaryInfo vsTemporaryInfo = temporaryInfoService.getVSTemporaryInfo(rvs);
      ChartVSAssemblyInfo chartInfo =
         (ChartVSAssemblyInfo) vsTemporaryInfo.getTempChart().getVSAssemblyInfo();

      if(chartInfo == null) {
         return false;
      }

      ChartVSAssemblyInfo oinfo = (ChartVSAssemblyInfo) chartInfo.clone();
      ChartVSAssembly tempChart = (ChartVSAssembly)
         vs.getAssembly(VSWizardConstants.TEMP_CHART_NAME);
      bindingHandler.updateTempChartAssembly(tempChartModel, tempChart);
      ChartVSAssemblyInfo ninfo =
         (ChartVSAssemblyInfo) vsTemporaryInfo.getTempChart().getVSAssemblyInfo();
      boolean trap = checkTrap0(rvs, oinfo, ninfo);
      vsTemporaryInfo.getTempChart().setVSAssemblyInfo(oinfo);

      return trap;
   }

   private boolean checkTrap0(RuntimeViewsheet rvs, ChartVSAssemblyInfo oinfo,
                              ChartVSAssemblyInfo ninfo)
   {
      VSModelTrapContext mtc = new VSModelTrapContext(rvs, true);
      boolean warning = false;

      if(mtc.isCheckTrap()) {
         AbstractModelTrapContext.TrapInfo trapInfo = mtc.checkTrap(oinfo, ninfo);
         warning = trapInfo != null && trapInfo.showWarning();
      }

      return warning;
   }

   private final ViewsheetService viewsheetService;
   private final VSWizardBindingHandler bindingHandler;
   private final VSWizardTemporaryInfoService temporaryInfoService;
}
