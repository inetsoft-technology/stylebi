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
import inetsoft.graph.data.DataSet;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.report.internal.graph.MapHelper;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.binding.command.OpenEditGeographicCommand;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.event.ChangeGeographicEvent;
import inetsoft.web.binding.event.RefreshBindingTreeEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.ChartGeoRefModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.binding.service.VSChartBindingFactory;
import inetsoft.web.binding.service.graph.ChartGeoInfoFactory;
import inetsoft.web.viewsheet.command.RefreshVSObjectCommand;
import inetsoft.web.viewsheet.model.*;
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
public class ChangeGeographicController {
      /**
    * Set geographic.
    */
   public static final String SET_GEOGRAPHIC = "set";
   /**
    * Clear geographic.
    */
   public static final String CLEAR_GEOGRAPHIC = "clear";

   @Autowired
   public ChangeGeographicController(
      VSBindingService bindingFactory,
      RuntimeViewsheetRef runtimeViewsheetRef,
      PlaceholderService placeholderService,
      VSBindingTreeController bindingTreeController,
      VSAssemblyInfoHandler assemblyInfoHandler,
      VSChartHandler chartHandler,
      VSObjectModelFactoryService objectModelService,
      ViewsheetService viewsheetService,
      ChartGeoInfoFactory.VSChartGeoInfoFactory chartGeoInfoFactory,
      VSChartBindingFactory vsChartBindingFactory)
   {
      this.bindingFactory = bindingFactory;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.placeholderService = placeholderService;
      this.bindingTreeController = bindingTreeController;
      this.assemblyInfoHandler = assemblyInfoHandler;
      this.chartHandler = chartHandler;
      this.objectModelService = objectModelService;
      this.viewsheetService = viewsheetService;
      this.chartGeoInfoFactory = chartGeoInfoFactory;
      this.vsChartBindingFactory = vsChartBindingFactory;
   }

   @MessageMapping("/vs/chart/changeGeographic")
   public void changeGeographic(@Payload ChangeGeographicEvent event,
                                Principal principal, CommandDispatcher dispatcher,
                                @LinkUri String linkUri) throws Exception{

      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      String name = event.name();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet vs = rvs.getViewsheet();
      ChartVSAssembly chart = (ChartVSAssembly) vs.getAssembly(name);

      if(chart == null) {
         LOG.warn("Chart assembly is missing, failed to process change geographic chart " +
            "reference event: " + name);
         return;
      }

      // Handle source changed.
      if(assemblyInfoHandler.handleSourceChanged(chart, event.table(),
         "/events/vs/chart/changeGeographic", event, dispatcher, box))
      {
         return;
      }

      BindingModel obinding = bindingFactory.createModel(chart);
      String refName = event.refName();
      String type = event.type();
      vs = chart.getViewsheet();
      ChartVSAssemblyInfo oinfo = (ChartVSAssemblyInfo) chart.getChartInfo().clone();
      ChartVSAssemblyInfo ninfo = (ChartVSAssemblyInfo) bindingFactory.
         updateAssembly(event.binding(), chart).getVSAssemblyInfo();

      boolean isDim = event.isDim();

      chartHandler.fixAggregateInfo(ninfo, vs, null);

      SourceInfo osinfo = oinfo.getSourceInfo();
      SourceInfo nsinfo = ninfo.getSourceInfo();
      VSChartInfo cinfo = ninfo.getVSChartInfo();
      boolean changed = false;
      boolean sourceChanged = false;

      if(!Tool.equals(osinfo, nsinfo)) {
         List<AggregateRef> refs = copyExpressionRefs(cinfo);

         if(osinfo == null || osinfo.isEmpty()) {
            ninfo.setSourceInfo(nsinfo);
         }
         else {
            ninfo.setSourceInfo(osinfo);
         }

         changed = true;
         sourceChanged = true;
         pasteExproessionRefs(refs, cinfo);
         VSUtil.setDefaultGeoColumns(cinfo, rvs, event.table());
         assemblyInfoHandler.validateBinding(chart);
      }

      // change geographic
      chartHandler.updateGeoColumns(box, vs, chart, cinfo);
      chartHandler.changeGeographic(cinfo, refName, type, isDim);

       // fix map info
      VSChartInfo ocinfo = (VSChartInfo) cinfo.clone();
      changed = chartHandler.fixMapInfo(cinfo, refName, type) || changed;
      new ChangeChartProcessor().fixMapFrame(ocinfo, cinfo);

      // refresh map
      chart.setVSAssemblyInfo(ninfo);
      box.updateAssembly(chart.getAbsoluteName());
      placeholderService.refreshVSAssembly(rvs, chart, dispatcher);
      ninfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      cinfo = ninfo.getVSChartInfo();

      // auto detect
      if(type.equals(SET_GEOGRAPHIC)) {
         if(isDim) {
            chartHandler.updateAllGeoColumns(box, vs, chart);
            DataSet source = chartHandler.getChartData(box, chart, true);
            chartHandler.autoDetect(vs, nsinfo, cinfo, refName, source);
         }
         else if(!MapHelper.isValidType(cinfo.getMapType())) {
            cinfo.setMeasureMapType("World");
         }
      }

      String otype = oinfo.getVSChartInfo().getMapType();
      String ntype = cinfo.getMapType();
      boolean typeChanged = !Tool.equals(otype, ntype);
      changed = typeChanged || changed;

      if(sourceChanged && typeChanged && cinfo instanceof MapInfo) {
         ((MapInfo) cinfo).removeGeoFields();
         DataRef dataRef = cinfo.getGeoColumns().getAttribute(refName);

         // measure (lat/lon) is not ChartRef
         if(dataRef instanceof ChartRef) {
            ((MapInfo) cinfo).addGeoField((ChartRef) dataRef);
         }
      }

      if(changed) {
         VSSelection bselection = oinfo.getBrushSelection();
         String table = chart.getTableName();
         int hint = chartHandler.createCommands(oinfo, ninfo);
         boolean dchanged = (hint & VSAssembly.INPUT_DATA_CHANGED) ==
            VSAssembly.INPUT_DATA_CHANGED;

         // clear brush for data changed
         if(dchanged && table != null && bselection != null &&
            !bselection.isEmpty())
         {
            hint = hint | VSAssembly.OUTPUT_DATA_CHANGED;
            vs.setBrush(table, chart);
         }

         try {
            ChangedAssemblyList clist =
               placeholderService.createList(true, dispatcher, rvs, linkUri);

            box.processChange(name, hint, clist);
            placeholderService.execute(rvs, name, linkUri, clist, dispatcher, true);
            assemblyInfoHandler.checkTrap(oinfo, ninfo, obinding, dispatcher, rvs);
            //processTableChange(oinfo, ninfo, rvs, this, dispatcher);
         }
         finally {
            vs.setBrush(table, null);
         }
      }

      BindingModel bindingModel = bindingFactory.createModel(chart);
      SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(bindingModel);
      dispatcher.sendCommand(bcommand);

      VSObjectModel model = objectModelService.createModel(chart, rvs);
      RefreshVSObjectCommand rcommand = new RefreshVSObjectCommand();
      rcommand.setInfo(model);
      dispatcher.sendCommand(rcommand);

      if(type.equals(SET_GEOGRAPHIC)) {
         ChartBindingModel chartBindingModel = vsChartBindingFactory.createModel(chart);
         DataRef dataRef = cinfo.getGeoColumns().getAttribute(refName);

         if(dataRef instanceof VSChartGeoRef) {
            VSChartGeoRef vsChartGeoRef = (VSChartGeoRef) dataRef;
            ChartGeoRefModel chartGeoRefModel = chartGeoInfoFactory
               .createChartRefModel(vsChartGeoRef, cinfo, null);
            OpenEditGeographicCommand openEditGeographicCommand =
               new OpenEditGeographicCommand(chartBindingModel, chartGeoRefModel);
            dispatcher.sendCommand(openEditGeographicCommand);
         }
         else if(dataRef != null) {
            OpenEditGeographicCommand openEditGeographicCommand =
               new OpenEditGeographicCommand(chartBindingModel, dataRef.getName());
            dispatcher.sendCommand(openEditGeographicCommand);
         }
      }

      RefreshBindingTreeEvent refreshBindingTreeEvent = new RefreshBindingTreeEvent();
      refreshBindingTreeEvent.setName(name);
      bindingTreeController.getBinding(refreshBindingTreeEvent, principal, dispatcher);

      dispatcher.sendCommand(new RefreshWizardTreeTriggerCommand());
   }

   /**
    * Copy all expression refs.
    */
   private List<AggregateRef> copyExpressionRefs(VSChartInfo cinfo) {
      AggregateInfo ainfo = cinfo.getAggregateInfo();
      List<AggregateRef> refs = new ArrayList<>();

      if(ainfo == null || ainfo.isEmpty()) {
         return refs;
      }

      for(int i = 0; i < ainfo.getAggregateCount(); i++) {
         AggregateRef aref = ainfo.getAggregate(i);

         if(aref.getDataRef() instanceof ExpressionRef) {
            refs.add(aref);
         }
      }

      return refs;
   }

    /**
    * Paste the expression to aggregate info.
    */
   private void pasteExproessionRefs(List<AggregateRef> refs, VSChartInfo cinfo)
   {
      AggregateInfo ainfo = cinfo.getAggregateInfo();

      for(AggregateRef aref : refs) {
         if(!ainfo.containsAggregate(aref)) {
            ainfo.addAggregate(aref);
         }
      }
   }

   private final VSBindingService bindingFactory;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSChartBindingFactory vsChartBindingFactory;
   private final PlaceholderService placeholderService;
   private final VSBindingTreeController bindingTreeController;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
   private final VSChartHandler chartHandler;
   private final VSObjectModelFactoryService objectModelService;
   private final ChartGeoInfoFactory.VSChartGeoInfoFactory chartGeoInfoFactory;
   private final ViewsheetService viewsheetService;
   private static final Logger LOG =
      LoggerFactory.getLogger(ChangeGeographicController.class);
}
