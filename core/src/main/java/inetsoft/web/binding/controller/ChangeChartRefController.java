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
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.internal.graph.ChangeChartDataProcessor;
import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.DrillFilterInfo;
import inetsoft.util.Tool;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.event.ChangeChartRefEvent;
import inetsoft.web.binding.handler.*;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.*;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.command.RefreshWizardTreeTriggerCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Controller
public class ChangeChartRefController {
   /**
    * Creates a new instance of <tt>ChangeChartRefController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    * @param viewsheetService
    */
   @Autowired
   public ChangeChartRefController(
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

   @MessageMapping("/vs/chart/changeChartRef")
   public void changeChartRef(@Payload ChangeChartRefEvent event,
                              Principal principal, CommandDispatcher dispatcher,
                              @LinkUri String linkUri) throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      applyDLevelChanges(event.getModel());

      String name = event.getName();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet vs = rvs.getViewsheet();
      vs.clearSharedFrames();
      ChartVSAssembly assembly = (ChartVSAssembly) vs.getAssembly(name);

      if(assembly == null) {
         LOG.warn("Chart assembly does not exist, failed to process change chart " +
                  "reference event: " + name);
         return;
      }

      BindingModel obinding = bindingFactory.createModel(assembly);
      ChartVSAssembly clone = assembly.clone();
      ChartBindingModel cmodel = event.getModel();
      cmodel = fixChartBindingModel(cmodel);
      clone = (ChartVSAssembly) bindingFactory.updateAssembly(cmodel, clone);
      ChartVSAssemblyInfo info = clone.getChartInfo();
      new ChangeChartProcessor().fixSizeFrame(info.getVSChartInfo());
      fixStaticColorFrame(info.getVSChartInfo(), event);

      ChartVSAssemblyInfo oinfo = (ChartVSAssemblyInfo) assembly.getVSAssemblyInfo().clone();
      vs = assembly.getViewsheet();
      VSSelection bselection = assembly.getBrushSelection();
      String table = assembly.getTableName();
      int hint = assembly.setVSAssemblyInfo(info);
      assembly.getChartInfo().setDateComparisonInfo(info.getDateComparisonInfo());

      if(hint != 0) {
         VSChartInfo ocinfo = oinfo.getVSChartInfo();
         VSChartInfo ncinfo = info.getVSChartInfo();
         String otype = ocinfo.getMapType();
         String ntype = ncinfo.getMapType();

         if(!Tool.equals(otype, ntype)) {
            info.setVSChartInfo(ncinfo);
            box.updateAssembly(name);
            ncinfo = info.getVSChartInfo();
         }

         chartHandler.updateGeoColumns(box, vs, assembly, ncinfo);

         boolean dchanged = (hint & VSAssembly.INPUT_DATA_CHANGED) ==
            VSAssembly.INPUT_DATA_CHANGED;

         // clear brush for data changed
         if(dchanged && table != null && bselection != null && !bselection.isEmpty()) {
            hint = hint | assembly.setBrushSelection(null);
            vs.setBrush(table, assembly);
         }

         try {
            ChangedAssemblyList clist =
               coreLifecycleService.createList(true, dispatcher, rvs, linkUri);
            box.updateAssembly(name);
            // update chart type after refreshing runtime refs.
            ncinfo.updateChartType(!ncinfo.isMultiStyles());
            GraphUtil.fixVisualFrames(ncinfo);
            new ChangeChartDataProcessor().sortRefs(assembly.getVSChartInfo());
            ChangeChartProcessor process = new ChangeChartProcessor();
            process.fixMapFrame(ocinfo, ncinfo);
            process.fixNamedGroup(ocinfo, ncinfo);
            process.syncTopN(ncinfo);
            process.fixAggregateRefs(ncinfo);
            ChangeChartProcessor.fixTarget(ocinfo, ncinfo, assembly.getChartDescriptor());

            // fix Bug #10599, refresh before executing will cause the chart paint
            // before chart data is reexecuted, and what's more,
            // coreLifecycleService.execute will refresh the assembly after
            // refreshing the data.
            // coreLifecycleService.refreshVSAssembly(rvs, assembly, dispatcher);
            box.processChange(name, hint, clist);
            refreshDrillFilter(assembly, ocinfo, ncinfo);
            coreLifecycleService.execute(rvs, name, linkUri, clist, dispatcher, true);
            assemblyInfoHandler.checkTrap(oinfo, info, obinding, dispatcher, rvs);
            assemblyInfoHandler.getGrayedOutFields(rvs, dispatcher);
         }
         finally {
            vs.setBrush(table, null);
         }
      }

      BindingModel binding = bindingFactory.createModel(assembly);
      SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
      dispatcher.sendCommand(bcommand);

      dispatcher.sendCommand(new RefreshWizardTreeTriggerCommand());
   }

   /**
    * For map chart, should update the reference in chart when change a map chart ref.
    */
   private ChartBindingModel fixChartBindingModel(ChartBindingModel model) {
      if(model == null || model.getGeoCols() == null || model.getGeoFields() == null ||
         model.getGeoCols().size() == 0 || model.getGeoFields().size() == 0)
      {
         return model;
      }

      ChartBindingModel nmodel = (ChartBindingModel) Tool.clone(model);
      List<DataRefModel> geoCols = nmodel.getGeoCols();
      List<ChartRefModel> geoFields = nmodel.getGeoFields();

      for(ChartRefModel geoField : geoFields) {
         if(!(geoField instanceof ChartGeoRefModel)) {
            continue;
         }

         ChartGeoRefModel ref = (ChartGeoRefModel) geoCols.stream()
            .filter(col -> col instanceof ChartGeoRefModel &&
               ((ChartGeoRefModel) col).getFullName().equals(geoField.getFullName()))
            .findFirst()
            .orElse(null);

         if(ref != null) {
            ((ChartGeoRefModel) geoField).setOption(ref.getOption());
         }
      }

      return nmodel;
   }

   private void refreshDrillFilter(VSAssembly assembly, VSChartInfo oinfo, VSChartInfo ninfo) {
      if(oinfo instanceof VSChartInfo) {
         ChartVSAssembly chart = (ChartVSAssembly) assembly;
         DrillFilterInfo drillInfo = chart.getDrillFilterInfo();
         assemblyInfoHandler.dateLevelChanged(drillInfo, oinfo.getXFields(), ninfo.getXFields());
         assemblyInfoHandler.dateLevelChanged(drillInfo, oinfo.getYFields(), ninfo.getYFields());
         assemblyInfoHandler.dateLevelChanged(drillInfo, oinfo.getGroupFields(),
            ninfo.getGroupFields());
      }
   }

   /**
    * Update aggregate measure when the date level of the corresponding dimension changes
    */
   public static void applyDLevelChanges(ChartBindingModel model) {
      List<ChartRefModel> refs = new ArrayList<>(model.getXFields());
      refs.addAll(model.getYFields());
      refs.addAll(model.getGroupFields());
      refs.add(model.getOpenField());
      refs.add(model.getCloseField());
      refs.add(model.getHighField());
      refs.add(model.getLowField());
      refs.add(model.getPathField());

      if(model.getColorField() != null) {
         refs.add(model.getColorField().getDataInfo());
      }

      if(model.getShapeField() != null) {
         refs.add(model.getShapeField().getDataInfo());
      }

      if(model.getSizeField() != null) {
         refs.add(model.getSizeField().getDataInfo());
      }

      for(ChartRefModel dimension : refs) {
         if(dimension instanceof ChartDimensionRefModel) {
            String name = dimension.getName();
            String dLevel = ((ChartDimensionRefModel) dimension).getDateLevel();

            if(!XSchema.isDateType(dimension.getDataType()) || dLevel == null || "".equals(dLevel)
               || "-1".equals(dLevel) || dLevel.startsWith("$") || dLevel.startsWith("="))
            {
               continue;
            }

            int level = Integer.parseInt(dLevel);
            String newName = DateRangeRef.getName(name, level);

            if(!newName.equals(dimension.getFullName())) {
               CalculatorHandler.updateAggregateColNames(refs, dimension.getFullName(), newName, level);
            }
         }
      }
   }

   /**
    * Fix static color for the edit xy aggregate chartref.
    */
   private void fixStaticColorFrame(ChartInfo cinfo, ChangeChartRefEvent event) {
      OriginalDescriptor odesc = event.getRefOriginalDescriptor();

      if(odesc == null) {
         return;
      }

      ChartRef ref = null;

      if(OriginalDescriptor.X_AXIS.equals(odesc.getSource()) &&
         odesc.getIndex() < cinfo.getXFieldCount())
      {
         ref = cinfo.getXField(odesc.getIndex());
      }
      else if(OriginalDescriptor.Y_AXIS.equals(odesc.getSource()) &&
         odesc.getIndex() < cinfo.getYFieldCount())
      {
         ref = cinfo.getYField(odesc.getIndex());
      }

      if(ref instanceof ChartAggregateRef) {
         GraphUtil.fixStaticColorFrame(ref, cinfo, (ChartAggregateRef) ref);
      }
   }

   private final VSBindingService bindingFactory;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final CoreLifecycleService coreLifecycleService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
   private final VSChartHandler chartHandler;
   private final VSChartDataHandler chartDataHandler;
   private final ViewsheetService viewsheetService;
   private static final Logger LOG =
      LoggerFactory.getLogger(ChangeChartRefController.class);
}
