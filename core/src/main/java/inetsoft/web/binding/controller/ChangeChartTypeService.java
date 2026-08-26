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
import inetsoft.graph.data.DataSet;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.GraphFormatUtil;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.report.internal.graph.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.DateComparisonUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.event.*;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Optional;

@Service
@ClusterProxy
public class ChangeChartTypeService {
   /**
    * Set geographic.
    */
   public static final String SET_GEOGRAPHIC = "set";

   public ChangeChartTypeService(
      VSBindingService bindingFactory,
      RuntimeViewsheetRef runtimeViewsheetRef, CoreLifecycleService coreLifecycleService,
      ChartRefModelFactoryService chartRefService,
      ChangeSeparateStatusController changeSeparateController,
      VSAssemblyInfoHandler assemblyInfoHandler, VSChartHandler chartHandler,
      VSBindingTreeController bindingTreeController,
      ViewsheetService viewsheetService)
   {
      this.bindingFactory = bindingFactory;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.coreLifecycleService = coreLifecycleService;
      this.chartRefService = chartRefService;
      this.changeSeparateController = changeSeparateController;
      this.assemblyInfoHandler = assemblyInfoHandler;
      this.chartHandler = chartHandler;
      this.bindingTreeController = bindingTreeController;
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   @ClusterWriteMethod
   public Void changeChartType(@ClusterProxyKey String id, ChangeChartTypeEvent event,
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
      ChartVSAssembly chart = (ChartVSAssembly) vs.getAssembly(name);

      if(chart == null) {
         LOG.warn("Chart assembly is missing, failed to process change chart type event: {}", name);
         return null;
      }

      if(!GraphTypeUtil.checkChartStylePermission(event.getType())) {
         MessageCommand command = new MessageCommand();
         Catalog catalog = Catalog.getCatalog();
         String msg = catalog.getString("chartTypes.user.noPermission",
                                        GraphTypes.getDisplayName(event.getType()) + " " + catalog.getString("Chart"));
         command.setMessage(msg);
         command.setType(MessageCommand.Type.ERROR);
         dispatcher.sendCommand(command);
         return null;
      }

      BindingModel obinding = bindingFactory.createModel(chart);
      ChartVSAssemblyInfo oinfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo().clone();
      ChartVSAssemblyInfo ninfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      ChartDescriptor desc = ninfo.getChartDescriptor();
      PlotDescriptor plotDesc = desc.getPlotDescriptor();
      VSChartInfo cinfo = ninfo.getVSChartInfo();

      int oldType = cinfo.getChartType();
      int newType = event.getType();
      boolean omulti = cinfo.isMultiStyles();
      boolean nmulti = event.isMulti();
      boolean ostackMeasures = plotDesc.isStackMeasures();
      boolean nstackMeasures = event.isStackMeasures();
      boolean separate = event.isSeparate();
      String refName = event.getRef();
      VSChartAggregateRef ref = null;

      if(refName != null && !Tool.equals("", refName)) {
         ChartRef cref = cinfo.getFieldByName(refName, true);

         if(cref instanceof VSChartAggregateRef) {
            ref = (VSChartAggregateRef) cref;
            oldType = ref.getChartType();
         }
      }

      if(ref != null && (ref.isVariable() || ref.isScript())) {
         box.get().executeDynamicValues(name, ref.getDynamicValues());
      }

      // make sure the old aesthetic fields will not override the new one
      cinfo.clearRuntime();

      if(oldType == newType) {
         new ChangeChartTypeProcessor(oldType, newType,
                                      omulti, nmulti, ref, cinfo, false, desc).processMultiChanged();
         handleMulti(name, omulti, nmulti, separate, chart, principal, dispatcher, linkUri);

         if(ostackMeasures == nstackMeasures) {
            // clearRuntime() above discarded the runtime aesthetic fields, and this is the only
            // exit that does not go on to rebuild them -- every other path below reaches
            // updateAssembly. Without this, setting a chart to the type it already has leaves its
            // colour, shape and size bindings in the design-time info, where every read reports
            // them, and absent from what renders: the chart draws as though nothing were bound to
            // those channels, and stays that way until some later call happens to take the full
            // path. Found by asserting a chart's current type, which is the cheapest no-op a
            // caller can make and the most likely one to be made "just to be sure".
            box.get().updateAssembly(chart.getAbsoluteName());
            return null;
         }
      }

      vs = chart.getViewsheet();
      String table = chart.getTableName();

      if(GraphTypes.isGeo(newType) && !GraphTypes.isGeo(oldType)) {
         box.get().updateAssembly(chart.getAbsoluteName());
         cinfo = ninfo.getVSChartInfo();
         chartHandler.updateGeoColumns(box.get(), vs, chart, cinfo);
      }

      try {
         cinfo = (VSChartInfo) new ChangeChartTypeProcessor(oldType, newType, omulti, nmulti, ref,
                                                            cinfo, false, desc)
            .setStrictFieldPlacement(true)
            .process();
      }
      catch(ChangeChartTypeProcessor.FieldPlacementException e) {
         // ChangeChartTypeProcessor refuses a retype that has nowhere to put a bound field (a
         // measure on colour heading for a pie, measures with no free aesthetic channel heading
         // for a treemap) rather than destroying it silently. Three things have to happen here and
         // none of them can be left to the caller.
         //
         // Caught by its own type, not by IllegalArgumentException: process() is a long chain and
         // an unrelated argument failure anywhere in it would otherwise be rolled back silently
         // and reported to the user as though it were a considered refusal.
         //
         // First, the refusal is not atomic on its own. It lands before any field is moved, but
         // fixChartInfo() has already run by then and stamps the new type onto the live info when
         // the two types share a ChartInfo class (bar -> pie), so the assembly is left claiming a
         // type whose binding it never got. Restoring the clone taken at the top of this method is
         // the only way to make the whole call a no-op, which is what a refusal has to be.
         //
         // Second, clearRuntime() ran above, before we knew the retype would be refused. Unwinding
         // without updateAssembly() would leave the chart exactly as the same-type branch above
         // used to: aesthetics reported by every read and absent from what renders.
         //
         // Third, composer services report failure by dispatching a MessageCommand, not by
         // throwing -- see the permission refusal above. Doing the same here means the browser
         // gets a dialog naming the field instead of a 500, and the agent tier gets a loud error
         // for free: CapturingCommandDispatcher turns an ERROR command into a
         // CommandErrorException. And like that refusal, the text is catalogued rather than a raw
         // literal: the exception carries the key and its arguments so the dialog arrives in the
         // user's own language.
         chart.setVSAssemblyInfo(oinfo);
         box.get().updateAssembly(chart.getAbsoluteName());

         MessageCommand command = new MessageCommand();
         command.setMessage(
            Catalog.getCatalog().getString(e.getCatalogKey(), e.getArguments()));
         command.setType(MessageCommand.Type.ERROR);
         dispatcher.sendCommand(command);
         return null;
      }

      SourceInfo sourceInfo = ninfo.getSourceInfo();
      new ChangeChartProcessor().fixParetoSorting(cinfo);

      if(cinfo instanceof VSMapInfo) {
         ninfo.setVSChartInfo(cinfo);
         box.get().updateAssembly(chart.getAbsoluteName());
         new ChangeChartProcessor().fixSizeField(cinfo, GraphTypes.CHART_MAP);
         new ChangeChartProcessor().fixMapFrame(oinfo.getVSChartInfo(), cinfo);
         cinfo = ninfo.getVSChartInfo();
         VSMapInfo minfo = (VSMapInfo) cinfo;
         // make sure rt geo columns are populated
         chartHandler.updateGeoColumns(box.get(), vs, chart, minfo);
         boolean colsChanged = fixGeoColumns(minfo);
         chartHandler.updateGeoColumns(box.get(), vs, chart, minfo);
         DataSet source = chartHandler.getChartData(box.get(), chart);
         autoDetect(vs, minfo, sourceInfo, source);
      }

      ninfo.setVSChartInfo(cinfo);
      GraphFormatUtil.fixDefaultNumberFormat(chart.getChartDescriptor(), cinfo);
      box.get().updateAssembly(chart.getAbsoluteName());
      new ChangeChartProcessor().fixSizeFrame(ninfo.getVSChartInfo());
      ChangeChartProcessor.fixTarget(oinfo.getVSChartInfo(), cinfo, desc);
      handleMulti(name, omulti, nmulti, separate, chart, principal, dispatcher, linkUri);
      plotDesc.setStackMeasures(nstackMeasures);

      if(GraphTypes.isGantt(cinfo.getChartType()) || GraphTypes.isTreemap(cinfo.getChartType()) ||
         GraphTypes.isCandle(cinfo.getChartType()))
      {
         plotDesc.setValuesVisible(false);
      }

      if(!DateComparisonUtil.isDateComparisonChartTypeChanged(ninfo, oinfo)) {
         Catalog catalog = Catalog.getCatalog();
         String msg = catalog.getString("date.comparison.changeChartType.warning");
         Tool.addUserMessage(msg);
      }

      int hint = chartHandler.createCommands(oinfo, ninfo);
      boolean dchanged = (hint & VSAssembly.INPUT_DATA_CHANGED) == VSAssembly.INPUT_DATA_CHANGED;
      VSSelection bselection = oinfo.getBrushSelection();

      // clear brush for data changed
      if(dchanged && table != null && bselection != null && !bselection.isEmpty()) {
         hint = hint | chart.setBrushSelection(null);
         vs.setBrush(table, chart);
      }

      try {
         ChangedAssemblyList clist = coreLifecycleService.createList(true, dispatcher, rvs, linkUri);
         box.get().processChange(name, hint, clist);
         coreLifecycleService.execute(rvs, name, linkUri, clist, dispatcher, true);
         assemblyInfoHandler.checkTrap(oinfo, ninfo, obinding, dispatcher, rvs);
      }
      finally {
         vs.setBrush(table, null);
      }

      BindingModel binding = bindingFactory.createModel(chart);
      SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
      dispatcher.sendCommand(bcommand);

      if(GraphTypes.isMap(newType)) {
         RefreshBindingTreeEvent refreshBindingTreeEvent = new RefreshBindingTreeEvent();
         refreshBindingTreeEvent.setName(name);
         bindingTreeController.getBinding(refreshBindingTreeEvent, principal, dispatcher);
      }

      return null;
   }


   private void handleMulti(String name, boolean omulti, boolean nmulti, boolean separate,
                            ChartVSAssembly chart, Principal principal,
                            CommandDispatcher dispatcher, String linkUri)
      throws Exception
   {
      if(omulti != nmulti && chart != null) {
         ChangeSeparateStatusEvent cevent = new ChangeSeparateStatusEvent(name, nmulti, separate);
         changeSeparateController.changeSeparateStatus(cevent, principal, dispatcher, linkUri);
      }
   }

   /**
    * Auto detect map type, layer and mapping.
    */
   private void autoDetect(Viewsheet vs, VSMapInfo minfo,
                           SourceInfo sourceInfo, DataSet source)
   {
      ColumnSelection rcols = minfo.getRTGeoColumns();
      ColumnSelection cols = minfo.getGeoColumns();

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         DataRef ref = cols.getAttribute(i);

         if(!(ref instanceof VSChartGeoRef)) {
            continue;
         }

         VSChartGeoRef col = (VSChartGeoRef) ref;
         VSChartGeoRef rcol = (VSChartGeoRef) rcols.getAttribute(i);
         GeographicOption opt = col.getGeographicOption();
         String refName = rcol.getName();
         MapHelper.autoDetect(vs, sourceInfo, minfo, opt, refName, source);
      }

      chartHandler.copyGeoColumns(minfo);
   }

   /**
    * Fix geographic column selection, add geo field to geo column selection
    * if it does not include.
    */
   private boolean fixGeoColumns(VSMapInfo minfo) {
      ChartRef[] gflds = minfo.getRTGeoFields();

      for(ChartRef gfld1 : gflds) {
         VSChartGeoRef gfld = (VSChartGeoRef) gfld1;
         String name = gfld.getName();

         if(minfo.isGeoColumn(name)) {
            continue;
         }

         GeographicOption gopt = gfld.getGeographicOption();
         VSChartGeoRef gcol = (VSChartGeoRef)
            chartHandler.changeGeographic(minfo, name, SET_GEOGRAPHIC, true);
         GeographicOption copt = gcol.getGeographicOption();

         copt.setLayerValue(gopt.getLayerValue());
         copt.setMapping(gopt.getMapping());
      }

      return gflds.length > 0;
   }

   private final VSBindingService bindingFactory;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final CoreLifecycleService coreLifecycleService;
   private final ChartRefModelFactoryService chartRefService;
   private final ChangeSeparateStatusController changeSeparateController;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
   private final VSBindingTreeController bindingTreeController;
   private final VSChartHandler chartHandler;
   private final ViewsheetService viewsheetService;
   private static final Logger LOG =
      LoggerFactory.getLogger(ChangeChartTypeService.class);
}
