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
import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.report.internal.graph.ChangeSeparateStatusProcessor;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.event.ChangeSeparateStatusEvent;
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
public class ChangeSeparateStatusController {
   /**
    * Creates a new instance of <tt>ChangeSeparateStatusController</tt>.
    *  @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @Autowired
   public ChangeSeparateStatusController(
      VSBindingService bindingFactory,
      RuntimeViewsheetRef runtimeViewsheetRef,
      PlaceholderService placeholderService,
      VSChartHandler chartHandler, ViewsheetService viewsheetService)
   {
      this.bindingFactory = bindingFactory;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.placeholderService = placeholderService;
      this.chartHandler = chartHandler;
      this.viewsheetService = viewsheetService;
   }

   @MessageMapping("/vs/chart/changeSeparateStatus")
   public void changeSeparateStatus(@Payload ChangeSeparateStatusEvent event,
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
      box.lockRead();

      try {
         ChartVSAssembly chart = (ChartVSAssembly) vs.getAssembly(name);

         if(chart == null) {
            LOG.warn("Chart assembly is missing, failed to process change separate " +
                  "status event: " + name);
            return;
         }

         vs = chart.getViewsheet();
         String table = chart.getTableName();
         ChartVSAssemblyInfo ninfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
         ChartVSAssemblyInfo oinfo = (ChartVSAssemblyInfo) ninfo.clone();
         ChartDescriptor cdesc = ninfo.getChartDescriptor();
         VSChartInfo cinfo = ninfo.getVSChartInfo();
         boolean separated = event.isSeparate() || GraphTypes.isTreemap(cinfo.getChartType()) ||
            GraphTypes.isMekko(cinfo.getChartType()) ||
            GraphTypes.isScatteredContour(cinfo.getChartType());
         boolean multi = event.isMulti();

         // the multi styles should be changed in the processor later
         cinfo.setSeparatedGraph(oinfo.getVSChartInfo().isSeparatedGraph());
         cinfo.setMultiStyles(oinfo.getVSChartInfo().isMultiStyles());
         new ChangeSeparateStatusProcessor(cinfo, cdesc).process(separated, multi);
         new ChangeChartProcessor().fixSizeFrame(cinfo);

         box.updateAssembly(chart.getAbsoluteName());
         int hint = chartHandler.createCommands(oinfo, ninfo);
         boolean dchanged = (hint & VSAssembly.INPUT_DATA_CHANGED) ==
            VSAssembly.INPUT_DATA_CHANGED;
         VSSelection bselection = oinfo.getBrushSelection();

         // clear brush for data changed
         if(dchanged && table != null && bselection != null && !bselection.isEmpty()) {
            hint = hint | chart.setBrushSelection(null);
            vs.setBrush(table, chart);
         }

         try {
            ChangedAssemblyList clist =
               placeholderService.createList(true, dispatcher, rvs, linkUri);
            box.processChange(name, hint, clist);
            placeholderService.execute(rvs, name, linkUri, clist, dispatcher, true);
         }
         finally {
            vs.setBrush(table, null);
         }

         //refreshVSAssembly should to be executed after placeholderService.execute()
         //or refresh error image.
         placeholderService.refreshVSAssembly(rvs, chart, dispatcher);
         BindingModel binding = bindingFactory.createModel(chart);
         SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
         dispatcher.sendCommand(bcommand);
      }
      finally {
         box.unlockRead();
      }
   }

   private final VSBindingService bindingFactory;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final PlaceholderService placeholderService;
   private final VSChartHandler chartHandler;
   private final ViewsheetService viewsheetService;
   private static final Logger LOG =
      LoggerFactory.getLogger(ChangeSeparateStatusController.class);
}
