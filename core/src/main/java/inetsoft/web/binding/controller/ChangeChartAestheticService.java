/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
import inetsoft.cluster.*;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.internal.graph.ChangeChartDataProcessor;
import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.event.ChangeChartRefEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.util.*;
import java.util.List;

@Service
@ClusterProxy
public class ChangeChartAestheticService {
   public ChangeChartAestheticService(
      VSBindingService bindingFactory,
      RuntimeViewsheetRef runtimeViewsheetRef, CoreLifecycleService coreLifecycleService,
      VSAssemblyInfoHandler assemblyInfoHandler, VSChartHandler chartHandler,
      ViewsheetService viewsheetService)
   {
      this.bindingFactory = bindingFactory;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.coreLifecycleService = coreLifecycleService;
      this.assemblyInfoHandler = assemblyInfoHandler;
      this.chartHandler = chartHandler;
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   @ClusterWriteMethod
   public Void changeChartAesthetic(@ClusterProxyKey String id, ChangeChartRefEvent event,
                                    Principal principal, CommandDispatcher dispatcher,
                                    String linkUri) throws Exception
   {
      String name = event.getName();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

      if(box.isEmpty()) {
         return null;
      }

      Viewsheet vs = rvs.getViewsheet();
      final ChartVSAssembly assembly = (ChartVSAssembly) vs.getAssembly(name);

      if(assembly == null) {
         LOG.warn("Chart assembly is missing, could not apply aesthetic: {}", name);
         return null;
      }

      synchronized(assembly) {
         BindingModel obinding = bindingFactory.createModel(assembly);

         if("color".equals(event.getFieldType()) || "shape".equals(event.getFieldType())) {
            vs = assembly.getViewsheet();
            vs.clearSharedFrames();
         }

         VSChartHandler.clearColorFrame(assembly.getVSChartInfo(), false, null);
         String table = assembly.getTableName();
         ChartVSAssembly clone = assembly.clone();
         ChartBindingModel cmodel = event.getModel();
         Map<String, Color> oDimColors = getDimensionColor(assembly, vs);
         clone = (ChartVSAssembly) bindingFactory.updateAssembly(cmodel, clone);
         // The frontend model never carries static-item styling (only text), so a model-driven
         // rebuild would wipe panel-set static formatting. Carry it over from the live assembly.
         preserveAllTextLayoutStaticFormatting(assembly.getVSChartInfo(), clone.getVSChartInfo());
         ChartVSAssemblyInfo ninfo = clone.getChartInfo();
         ChartVSAssemblyInfo oinfo = (ChartVSAssemblyInfo) assembly.getVSAssemblyInfo().clone();
         chartHandler.fixAggregateInfo(ninfo, vs, null);
         ChangeChartAestheticController.fixAggregateRefSizeField(ninfo.getVSChartInfo());

         int hint = assembly.setVSAssemblyInfo(ninfo);
         box.get().updateAssembly(assembly.getAbsoluteName());

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
               coreLifecycleService.createList(true, dispatcher, rvs, linkUri);
            box.get().processChange(name, hint, clist);
            coreLifecycleService.execute(rvs, name, linkUri, clist, dispatcher, true);
            assemblyInfoHandler.checkTrap(oinfo, ninfo, obinding, dispatcher, rvs);
         }
         finally {
            vs.setBrush(table, null);
         }

         BindingModel binding = bindingFactory.createModel(assembly);
         SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
         dispatcher.sendCommand(bcommand);
      }

      return null;
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

   // Static text items are styled via the Format panel, which writes directly to the backend
   // TextLayoutItem's inline fields (color/font/bold/italic). The frontend layout model never
   // carries that styling (the designer's Text Properties only edits text), so a layout
   // round-trip would wipe it. Carry the existing static items' styling onto the incoming
   // layout, matched by text, so panel-set static formatting survives layout edits.
   //
   // NOTE on ownership: chart-level plot-layout static formatting is owned by
   // ChartInfoModelBuilder.carryOverStaticItemFormat, which runs inside updateAssembly() against the
   // ChartVSAssemblyInfo descriptor that is actually rendered. The chart-level branch below reads
   // VSChartInfo.getChartDescriptor() (a separate transient field that is typically null/stale at
   // this point), so it is a harmless redundant net there. The per-aggregate branch IS load-bearing:
   // updateChartInfo never rebuilds per-aggregate layouts, and these read each aggregate's own
   // TextLayout off the ref, so this is the only place per-aggregate static formatting is preserved.
   static void preserveAllTextLayoutStaticFormatting(VSChartInfo oldInfo, VSChartInfo newInfo) {
      if(oldInfo == null || newInfo == null) {
         return;
      }

      PlotDescriptor oldPlot = oldInfo.getChartDescriptor() != null
         ? oldInfo.getChartDescriptor().getPlotDescriptor() : null;
      PlotDescriptor newPlot = newInfo.getChartDescriptor() != null
         ? newInfo.getChartDescriptor().getPlotDescriptor() : null;

      if(oldPlot != null && newPlot != null) {
         preserveStaticFormatting(oldPlot.getTextLayout(), newPlot.getTextLayout());
      }

      // Per-aggregate: match aggregates by full name.
      Map<String, ChartAggregateRef> oldAggrs = new HashMap<>();

      for(ChartAggregateRef aggr : oldInfo.getAestheticAggregateRefs(false)) {
         oldAggrs.put(aggr.getFullName(), aggr);
      }

      for(ChartAggregateRef newAggr : newInfo.getAestheticAggregateRefs(false)) {
         ChartAggregateRef oldAggr = oldAggrs.get(newAggr.getFullName());

         if(oldAggr != null) {
            preserveStaticFormatting(oldAggr.getTextLayout(), newAggr.getTextLayout());
         }
      }
   }

   // Delegates to the single canonical implementation on TextLayout so the chart-level path
   // (ChartInfoModelBuilder) and this per-aggregate net share identical precedence semantics.
   static void preserveStaticFormatting(inetsoft.uql.viewsheet.graph.TextLayout oldLayout,
                                        inetsoft.uql.viewsheet.graph.TextLayout newLayout)
   {
      inetsoft.uql.viewsheet.graph.TextLayout.carryStaticItemFormatting(oldLayout, newLayout);
   }

   private final VSBindingService bindingFactory;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final CoreLifecycleService coreLifecycleService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
   private final VSChartHandler chartHandler;
   private final ViewsheetService viewsheetService;
   private static final Logger LOG =
      LoggerFactory.getLogger(ChangeChartAestheticService.class);
}
