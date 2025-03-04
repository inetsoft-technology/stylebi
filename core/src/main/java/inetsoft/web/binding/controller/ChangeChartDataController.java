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
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.graph.ChangeChartDataProcessor;
import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.graph.VSSelection;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.event.ChangeChartRefEvent;
import inetsoft.web.binding.handler.*;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.ChartBindingModel;
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

@Controller
public class ChangeChartDataController {
   /**
    * Creates a new instance of <tt>ChangeChartDataController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    * @param viewsheetService
    */
   @Autowired
   public ChangeChartDataController(
      VSBindingService bindingFactory,
      RuntimeViewsheetRef runtimeViewsheetRef, CoreLifecycleService coreLifecycleService,
      VSAssemblyInfoHandler assemblyInfoHandler, VSChartHandler chartHandler,
      VSChartDataHandler chartDataHandler, ViewsheetService viewsheetService)
   {
      this.bindingFactory = bindingFactory;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.coreLifecycleService = coreLifecycleService;
      this.assemblyInfoHandler = assemblyInfoHandler;
      this.chartHandler = chartHandler;
      this.chartDataHandler = chartDataHandler;
      this.viewsheetService = viewsheetService;
   }

   @MessageMapping("/vs/chart/changeChartData")
   public void changeChartData(@Payload ChangeChartRefEvent event,
                               Principal principal, CommandDispatcher dispatcher,
                               @LinkUri String linkUri) throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      String name = event.getName();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet vs = rvs.getViewsheet();
      ChartVSAssembly assembly = (ChartVSAssembly) vs.getAssembly(name);

      if(assembly == null) {
         LOG.warn(
            "Chart assembly does not exist, failed to process change chart " +
            "reference event: " + name);
         return;
      }

      BindingModel obinding = bindingFactory.createModel(assembly);
      ChartVSAssembly clone = (ChartVSAssembly) assembly.clone();
      ChartBindingModel cmodel = event.getModel();
      clone = (ChartVSAssembly) bindingFactory.updateAssembly(cmodel, clone);
      ChartVSAssemblyInfo ninfo = clone.getChartInfo();
      vs = assembly.getViewsheet();
      String table = assembly.getTableName();
      ChartVSAssemblyInfo oinfo =
         (ChartVSAssemblyInfo) assembly.getVSAssemblyInfo().clone();
      // fix info
      chartHandler.fixAggregateInfo(ninfo, vs, null);

      ChartVSAssemblyInfo ninfoCopy = (ChartVSAssemblyInfo) ninfo.clone();
      int hint = assembly.setVSAssemblyInfo(ninfo);

      // update again after chart info is finalized
      try {
         box.updateAssembly(assembly.getAbsoluteName());
      }
      catch(Exception e) {
         // ignore it
      }

      ninfo = (ChartVSAssemblyInfo) assembly.getVSAssemblyInfo();
      VSChartInfo cinfo = ninfo.getVSChartInfo();
      cinfo = (VSChartInfo) new ChangeChartDataProcessor(cinfo).process();
      ChangeChartProcessor.fixGroupOption(cinfo);
      ninfo.setVSChartInfo(cinfo);
      chartHandler.fixShapeField(ninfo.getVSChartInfo(),
                                 chartDataHandler.getChartType(ninfo.getVSChartInfo()));
      ChangeChartProcessor pro = new ChangeChartProcessor();
      VSChartInfo ocinfo = oinfo.getVSChartInfo();
      VSChartInfo ncinfo = ninfo.getVSChartInfo();

      // @by ChrisSpagnoli bug1412008160666 #1 2014-10-28
      // Clear custom tip, if it contains numeric references
      if(chartDataHandler.tipFormatContainsNumericReferences(ocinfo.getToolTipValue())) {
         ncinfo.setToolTipValue(null);
      }

      pro.fixMapFrame(ocinfo, ncinfo);
      pro.fixNamedGroup(ocinfo, ncinfo);
      coreLifecycleService.refreshVSAssembly(rvs, assembly, dispatcher);
      hint = hint | chartHandler.createCommands(oinfo, ninfoCopy);

      boolean dchanged = (hint & VSAssembly.INPUT_DATA_CHANGED) ==
         VSAssembly.INPUT_DATA_CHANGED;
      VSSelection bselection = oinfo.getBrushSelection();

      // clear brush for data changed
      if(dchanged && table != null && bselection != null &&
         !bselection.isEmpty())
      {
         hint = hint | assembly.setBrushSelection(null);
         vs.setBrush(table, assembly);
      }

      try {
         ChangedAssemblyList clist =
            coreLifecycleService.createList(true, dispatcher, rvs, linkUri);
         box.processChange(name, hint, clist);
         coreLifecycleService.execute(rvs, name, linkUri, clist, dispatcher, true);
         assemblyInfoHandler.checkTrap(oinfo, ninfo, obinding, dispatcher, rvs);
         //processTableChange(oinfo, ninfo, rvs, this, command);
      }
      finally {
         vs.setBrush(table, null);
      }

      BindingModel binding = bindingFactory.createModel(assembly);
      SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
      dispatcher.sendCommand(bcommand);
   }

   private final VSBindingService bindingFactory;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final CoreLifecycleService coreLifecycleService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
   private final VSChartHandler chartHandler;
   private final VSChartDataHandler chartDataHandler;
   private final ViewsheetService viewsheetService;
   private static final Logger LOG =
      LoggerFactory.getLogger(ChangeChartDataController.class);
}
