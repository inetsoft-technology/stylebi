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
import inetsoft.graph.data.DataSet;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
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
import inetsoft.web.viewsheet.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChangeChartTypeController {
   /**
    * Set geographic.
    */
   public static final String SET_GEOGRAPHIC = "set";
   /**
    * Creates a new instance of <tt>ChangeChartTypeController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    * @param viewsheetService
    */
   @Autowired
   public ChangeChartTypeController(
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

   @MessageMapping("/vs/chart/changeChartType")
   public void changeChartType(@Payload ChangeChartTypeEvent event,
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
      ChartVSAssembly chart = (ChartVSAssembly) vs.getAssembly(name);

      if(chart == null) {
         LOG.warn("Chart assembly is missing, failed to process change chart type event: " + name);
         return;
      }

      if(!GraphTypeUtil.checkChartStylePermission(event.getType())) {
         MessageCommand command = new MessageCommand();
         Catalog catalog = Catalog.getCatalog();
         String msg = catalog.getString("chartTypes.user.noPermission",
            GraphTypes.getDisplayName(event.getType()) + " " + catalog.getString("Chart"));
         command.setMessage(msg);
         command.setType(MessageCommand.Type.ERROR);
         dispatcher.sendCommand(command);
         return;
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
         box.executeDynamicValues(name, ref.getDynamicValues());
      }

      // make sure the old aesthetic fields will not override the new one
      cinfo.clearRuntime();

      if(oldType == newType) {
         new ChangeChartTypeProcessor(oldType, newType,
            omulti, nmulti, ref, cinfo, false, desc).processMultiChanged();
         handleMulti(name, omulti, nmulti, separate, chart, principal, dispatcher, linkUri);

         if(ostackMeasures == nstackMeasures) {
            return;
         }
      }

      vs = chart.getViewsheet();
      String table = chart.getTableName();

      if(GraphTypes.isGeo(newType) && !GraphTypes.isGeo(oldType)) {
         box.updateAssembly(chart.getAbsoluteName());
         cinfo = ninfo.getVSChartInfo();
         chartHandler.updateGeoColumns(box, vs, chart, cinfo);
      }

      cinfo = (VSChartInfo) new ChangeChartTypeProcessor(oldType, newType,
         omulti, nmulti, ref, cinfo, false, desc).process();
      SourceInfo sourceInfo = ninfo.getSourceInfo();
      new ChangeChartProcessor().fixParetoSorting(cinfo);

      if(cinfo instanceof VSMapInfo) {
         ninfo.setVSChartInfo(cinfo);
         box.updateAssembly(chart.getAbsoluteName());
         new ChangeChartProcessor().fixSizeField(cinfo, GraphTypes.CHART_MAP);
         new ChangeChartProcessor().fixMapFrame(oinfo.getVSChartInfo(), cinfo);
         cinfo = ninfo.getVSChartInfo();
         VSMapInfo minfo = (VSMapInfo) cinfo;
         // make sure rt geo columns are populated
         chartHandler.updateGeoColumns(box, vs, chart, minfo);
         boolean colsChanged = fixGeoColumns(minfo);
         chartHandler.updateGeoColumns(box, vs, chart, minfo);
         DataSet source = chartHandler.getChartData(box, chart);
         autoDetect(vs, minfo, sourceInfo, source);
      }

      ninfo.setVSChartInfo(cinfo);
      GraphFormatUtil.fixDefaultNumberFormat(chart.getChartDescriptor(), cinfo);
      box.updateAssembly(chart.getAbsoluteName());
      new ChangeChartProcessor().fixSizeFrame(ninfo.getVSChartInfo());
      ChangeChartProcessor.fixTarget(oinfo.getVSChartInfo(), cinfo, desc);
      handleMulti(name, omulti, nmulti, separate, chart, principal, dispatcher, linkUri);
      plotDesc.setStackMeasures(nstackMeasures);
      plotDesc.setValuesVisible(!GraphTypes.isGantt(cinfo.getChartType()) &&
         !GraphTypes.isTreemap(cinfo.getChartType()) && !GraphTypes.isCandle(cinfo.getChartType()));

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
         box.processChange(name, hint, clist);
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
      LoggerFactory.getLogger(ChangeChartTypeController.class);
}
