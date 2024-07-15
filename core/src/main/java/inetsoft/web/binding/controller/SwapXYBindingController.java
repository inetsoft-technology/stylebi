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
package inetsoft.web.binding.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.graph.SwapXYBindingProcessor;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.event.ChangeSeparateStatusEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSChartHandler;
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

@Controller
public class SwapXYBindingController {
   /**
    * Creates a new instance of <tt>SwapXYBindingController</tt>.
    *  @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    * @param chartHandler
    * @param viewsheetService
    */
   @Autowired
   public SwapXYBindingController(
      VSBindingService bindingFactory,
      RuntimeViewsheetRef runtimeViewsheetRef,
      PlaceholderService placeholderService,
      VSAssemblyInfoHandler assemblyInfoHandler,
      VSChartHandler chartHandler, ViewsheetService viewsheetService)
   {
      this.bindingFactory = bindingFactory;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.placeholderService = placeholderService;
      this.assemblyInfoHandler = assemblyInfoHandler;
      this.chartHandler = chartHandler;
      this.viewsheetService = viewsheetService;
   }

   @MessageMapping("/vs/chart/swapXYBinding")
   public void swapXYBinding(@Payload ChangeSeparateStatusEvent event,
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
      String srctable = null;
      box.lockRead();

      try {
         ChartVSAssembly chart = (ChartVSAssembly) vs.getAssembly(name);

         if(chart == null) {
            LOG.warn("Chart assembly is missing, failed to process swap XY binding event: " + name);
            return;
         }

         BindingModel obinding = bindingFactory.createModel(chart);
         vs = chart.getViewsheet();
         srctable = chart.getTableName();
         ChartVSAssemblyInfo ninfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
         ChartVSAssemblyInfo oinfo = (ChartVSAssemblyInfo) ninfo.clone();
         // fix info
         fixInfo(ninfo, vs);

         int hint = chart.setVSAssemblyInfo(ninfo);
         box.updateAssembly(chart.getAbsoluteName());
         placeholderService.refreshVSAssembly(rvs, chart, dispatcher);
         hint = hint | chartHandler.createCommands(oinfo, ninfo);
         boolean dchanged = (hint & VSAssembly.INPUT_DATA_CHANGED) == VSAssembly.INPUT_DATA_CHANGED;
         VSSelection bselection = oinfo.getBrushSelection();

         // clear brush for data changed
         if(dchanged && srctable != null && bselection != null && !bselection.isEmpty()) {
            hint = hint | chart.setBrushSelection(null);
            vs.setBrush(srctable, chart);
         }

         // swap ratio when x/y is swapped
         VSChartInfo cinfo = ninfo.getVSChartInfo();
         boolean wresize = cinfo.isWidthResized();
         boolean hresize = cinfo.isHeightResized();
         double wratio = cinfo.getUnitWidthRatio();
         double hratio = cinfo.getUnitHeightRatio();
         final double originalWidthRatio = cinfo.getInitialWidthRatio();
         final double originalHeightRatio = cinfo.getInitialHeightRatio();

         ChangedAssemblyList clist = placeholderService.createList(true, dispatcher, rvs, linkUri);
         box.processChange(name, hint, clist);
         placeholderService.execute(rvs, name, linkUri, hint, dispatcher);
         assemblyInfoHandler.checkTrap(oinfo, ninfo, obinding, dispatcher, rvs);

         final VSChartInfo ncinfo = chart.getVSChartInfo();
         final double initialWidthRatio = ncinfo.getInitialWidthRatio();
         final double initialHeightRatio = ncinfo.getInitialHeightRatio();

         // after generating the new chart, transform the width/height scale factor to a multiple
         // of the other's scale factor and re-execute
         if(Math.abs(initialWidthRatio - originalWidthRatio) > 0.01 ||
            Math.abs(initialHeightRatio - originalHeightRatio) > 0.01) {
            ncinfo.setWidthResized(hresize);
            ncinfo.setHeightResized(wresize);
            ncinfo.setUnitWidthRatio(hratio / originalHeightRatio * initialWidthRatio);
            ncinfo.setUnitHeightRatio(wratio / originalWidthRatio * initialHeightRatio);
            placeholderService.execute(rvs, name, linkUri, hint, dispatcher);
         }

         // force ratio to be recalculated
         box.getVGraphPair(name);

         BindingModel binding = bindingFactory.createModel(chart);
         SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
         dispatcher.sendCommand(bcommand);
      }
      finally {
         box.unlockRead();

         if(srctable != null) {
            vs.setBrush(srctable, null);
         }
      }
   }

   /**
    * Fix binding info.
    */
   private void fixInfo(ChartVSAssemblyInfo info, Viewsheet vs) {
      VSChartInfo cinfo = info.getVSChartInfo();
      fixFields(cinfo);
      ChartDescriptor desc = info.getChartDescriptor();
      new SwapXYBindingProcessor(cinfo, desc).process();
   }

   private void fixFields(VSChartInfo cinfo) {
      ChartRef[] xfields = cinfo.getXFields();
      ChartRef[] yfields = cinfo.getYFields();
      cinfo.removeXFields();
      cinfo.removeYFields();

      for(int i = 0; i < xfields.length; i++) {
         ChartRef xref = xfields[i];

         if(xref instanceof VSDimensionRef) {
            ((VSDimensionRef) xref).setDateLevel(-1);
         }

         cinfo.addXField(i, xref);
      }

      for(int i = 0; i < yfields.length; i++) {
         ChartRef yref = yfields[i];

         if(yref instanceof VSDimensionRef) {
            ((VSDimensionRef) yref).setDateLevel(-1);
         }

         cinfo.addYField(i, yref);
      }
   }

   private final VSBindingService bindingFactory;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final PlaceholderService placeholderService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
   private final VSChartHandler chartHandler;
   private final ViewsheetService viewsheetService;
   private static final Logger LOG =
      LoggerFactory.getLogger(SwapXYBindingController.class);
}
