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
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.report.internal.graph.ChangeChartDataProcessor;
import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.dnd.ChartAestheticDropTarget;
import inetsoft.web.binding.dnd.DropTarget;
import inetsoft.web.binding.event.ChangeChartRefEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSChartHandler;
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

import java.awt.*;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@Controller
public class ChangeChartAestheticController {
   /**
    * Creates a new instance of <tt>ChangeChartRefController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    * @param viewsheetService
    */
   @Autowired
   public ChangeChartAestheticController(
      VSBindingService bindingFactory,
      RuntimeViewsheetRef runtimeViewsheetRef, PlaceholderService placeholderService,
      VSAssemblyInfoHandler assemblyInfoHandler, VSChartHandler chartHandler,
      ViewsheetService viewsheetService)
   {
      this.bindingFactory = bindingFactory;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.placeholderService = placeholderService;
      this.assemblyInfoHandler = assemblyInfoHandler;
      this.chartHandler = chartHandler;
      this.viewsheetService = viewsheetService;
   }

   @MessageMapping("/vs/chart/changeChartAesthetic")
   public void changeChartAesthetic(@Payload ChangeChartRefEvent event,
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
      final ChartVSAssembly assembly = (ChartVSAssembly) vs.getAssembly(name);

      if(assembly == null) {
         LOG.warn("Chart assembly is missing, could not apply aesthetic: " + name);
         return;
      }

      synchronized(assembly) {
         BindingModel obinding = bindingFactory.createModel(assembly);

         if("color".equals(event.getFieldType())) {
            vs = assembly.getViewsheet();
            vs.clearSharedFrames();
         }

         VSChartHandler.clearColorFrame(assembly.getVSChartInfo(), false, null);
         String table = assembly.getTableName();
         ChartVSAssembly clone = assembly.clone();
         ChartBindingModel cmodel = event.getModel();
         Map<String, Color> oDimColors = getDimensionColor(assembly, vs);
         clone = (ChartVSAssembly) bindingFactory.updateAssembly(cmodel, clone);
         ChartVSAssemblyInfo ninfo = clone.getChartInfo();
         ChartVSAssemblyInfo oinfo = (ChartVSAssemblyInfo) assembly.getVSAssemblyInfo().clone();
         chartHandler.fixAggregateInfo(ninfo, vs, null);
         fixAggregateRefSizeField(ninfo.getVSChartInfo());

         int hint = assembly.setVSAssemblyInfo(ninfo);
         box.updateAssembly(assembly.getAbsoluteName());

         // fix bug1352448598261, chart type is not valid when in flex side,
         // so GraphUtil.as.fixVisualFrame may cause invalid result, here
         // fix it again
         new ChangeChartDataProcessor(ninfo.getVSChartInfo(), false).process();

         ChangeChartProcessor pro = new ChangeChartProcessor();
         VSChartInfo ocinfo = oinfo.getVSChartInfo();
         VSChartInfo ncinfo = ninfo.getVSChartInfo();
         pro.copyDimensionColors(ocinfo, ncinfo, vs);
         pro.fixMapFrame(ocinfo, ncinfo);
         pro.fixAestheticNamedGroup(ncinfo);
         GraphUtil.syncWorldCloudColor(ncinfo);
         hint = hint | chartHandler.createCommands(oinfo, ninfo);
         boolean dchanged = (hint & VSAssembly.INPUT_DATA_CHANGED) ==
            VSAssembly.INPUT_DATA_CHANGED;
         VSSelection bselection = oinfo.getBrushSelection();

         // clear brush for data changed
         if(dchanged && table != null && bselection != null && !bselection.isEmpty()) {
            hint = hint | assembly.setBrushSelection(null);
            vs.setBrush(table, assembly);
         }

         try {
            ChangedAssemblyList clist =
               placeholderService.createList(true, dispatcher, rvs, linkUri);
            box.processChange(name, hint, clist);
            placeholderService.execute(rvs, name, linkUri, clist, dispatcher, true);
            assemblyInfoHandler.checkTrap(oinfo, ninfo, obinding, dispatcher, rvs);
         }
         finally {
            vs.setBrush(table, null);
         }

         BindingModel binding = bindingFactory.createModel(assembly);
         SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
         dispatcher.sendCommand(bcommand);
      }
   }
   /**
    * Copy static color to word cloud text color when added text field.
    */
   public static void syncWorldCloudColor(ChartInfo ncinfo, DropTarget target) {
      if(target instanceof ChartAestheticDropTarget) {
         int dropType = Integer.parseInt(((ChartAestheticDropTarget) target).getDropType());

         if(dropType == ChartConstants.DROP_REGION_TEXT) {
            GraphUtil.syncWorldCloudColor(ncinfo);
         }
      }
   }

   /**
    * Fix the SizeField of the chart aggregateRef.
    */
   public static void fixAggregateRefSizeField(ChartInfo vinfo) {
      if(vinfo.isMultiAesthetic()) {
         return;
      }

      VSDataRef[] arr = vinfo.getFields();

      for(VSDataRef ref : arr) {
         if(ref instanceof ChartAggregateRef) {
            ((ChartAggregateRef) ref).setSizeField(null);
         }
      }
   }

   /**
    * Get chart color filed dimension global colors.
    */
   private Map<String, Color> getDimensionColor(ChartVSAssembly chart, Viewsheet vs) {
      AestheticRef cfield = chart.getVSChartInfo().getColorField();

      if(cfield != null && cfield.getVisualFrame() != null) {
         VisualFrame colorFrame = cfield.getVisualFrame();
         String field = colorFrame.getField();

         if(field != null) {
            return vs.getDimensionColors(field);
         }
      }

      return new HashMap<>();
   }

   private final VSBindingService bindingFactory;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final PlaceholderService placeholderService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
   private final VSChartHandler chartHandler;
   private final ViewsheetService viewsheetService;
   private static final Logger LOG =
      LoggerFactory.getLogger(ChangeChartAestheticController.class);
}
