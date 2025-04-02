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

package inetsoft.web.binding.dnd.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.execution.DataMap;
import inetsoft.report.composition.execution.VSAQuery;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameContext;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.controller.ChangeChartAestheticController;
import inetsoft.web.binding.dnd.ChartAestheticDropTarget;
import inetsoft.web.binding.event.VSDndEvent;
import inetsoft.web.binding.handler.*;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.List;
import java.util.Set;

@Service
@ClusterProxy
public class VSChartDndService {

   public VSChartDndService(ViewsheetService viewsheetService,
                            VSChartBindingHandler chartHandler,
                            VSChartDataHandler dataHandler,
                            VSAssemblyInfoHandler assemblyInfoHandler,
                            VSBindingService bfactory,
                            CoreLifecycleService coreLifecycleService)
   {
      this.viewsheetService = viewsheetService;
      this.chartHandler = chartHandler;
      this.dataHandler = dataHandler;
      this.assemblyInfoHandler = assemblyInfoHandler;
      this.bfactory = bfactory;
      this.coreLifecycleService = coreLifecycleService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void addRemoveColumns(@ClusterProxyKey String runtimeId, VSDndEvent event,
                                Principal principal,String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      if(rvs == null) {
         return null;
      }

      ChartVSAssembly assembly = (ChartVSAssembly) rvs.getViewsheet().getAssembly(event.name());
      ChartVSAssembly clone = (ChartVSAssembly) assembly.clone();
      chartHandler.addRemoveColumns(rvs, clone, event.getTransfer(), event.getDropTarget());
      applyChartInfo(rvs, assembly, (ChartVSAssemblyInfo) clone.getInfo(), dispatcher,
                     event, linkUri, true);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void addColumns(@ClusterProxyKey String runtimeId, VSDndEvent event, Principal principal,
                          String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      if(rvs == null) {
         return null;
      }

      ChartVSAssembly assembly = (ChartVSAssembly) rvs.getViewsheet().getAssembly(event.name());
      ChartVSAssembly oassembly = assembly.clone();
      ChartVSAssemblyInfo oinfo = (ChartVSAssemblyInfo) oassembly.getInfo();
      VSChartInfo chartInfo = oinfo.getVSChartInfo();
      int geoSize = chartInfo instanceof VSMapInfo ?
         ((VSMapInfo) chartInfo).getGeoFieldCount() : 0;

      if(oinfo.getVSChartInfo().getFields().length + geoSize + event.getEntries().length >
         Util.getOrganizationMaxColumn())
      {
         MessageCommand command = new MessageCommand();
         command.setMessage(Util.getColumnLimitMessage());
         command.setType(MessageCommand.Type.ERROR);
         dispatcher.sendCommand(command);

         return null;
      }

      ChartVSAssembly nassembly = assembly.clone();
      ChartVSAssemblyInfo ninfo = (ChartVSAssemblyInfo) nassembly.getInfo();
      boolean check = false;

      // Handle source changed.
      if(sourceChanged(assembly, event.getTable())) {
         check = true;
         assemblyInfoHandler.changeSource(nassembly, event.getTable(), event.getSourceType());
         VSChartInfo vsChartInfo = ninfo.getVSChartInfo();
         VSUtil.setDefaultGeoColumns(vsChartInfo, rvs, event.getTable());
         AggregateInfo ainfo = vsChartInfo.getAggregateInfo();

         if(ainfo != null) {
            List<DataRef> calcFields = ainfo.getFormulaFields();
            Set<String> calcFieldsRefs = ainfo.removeFormulaFields(calcFields);
            vsChartInfo.removeFormulaField(calcFieldsRefs);
         }
      }

      if(ninfo.getSourceInfo() == null) {
         ninfo.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, event.getTable()));
      }

      if(check) {
         SourceInfo source = nassembly.getSourceInfo();

         if(source.getType() == SourceInfo.VS_ASSEMBLY) {
            VSAQuery query = VSAQuery.createVSAQuery(rvs.getViewsheetSandbox(),
                                                     nassembly, DataMap.DETAIL);
            query.createAssemblyTable(nassembly.getTableName());
         }
      }

      if(check || VSUtil.isVSAssemblyBinding(event.getTable())) {
         assemblyInfoHandler.validateBinding(nassembly);
      }

      Viewsheet vs = rvs.getViewsheet();

      if(vs != null) {
         // when drop field to color, should clear old static colors and reassign color for chart.
         if(event.getDropTarget() instanceof ChartAestheticDropTarget &&
            "6".equals(((ChartAestheticDropTarget) event.getDropTarget()).getDropType()))
         {
            ChartAestheticDropTarget target = (ChartAestheticDropTarget) event.getDropTarget();
            vs.clearSharedFrames();
            VSChartHandler.clearColorFrame(nassembly.getVSChartInfo(), false,
                                           // only clear the target aggr color frame. (60178)
                                           (target.getAggr() != null ? target.getAggr().createDataRef() : null));
         }

         CategoricalColorFrameContext.getContext().setSharedFrames(vs.getSharedFrames());
      }

      chartHandler.addColumns(rvs, nassembly, event.getEntries(), event.getDropTarget(),
                              dispatcher, linkUri);

      applyChartInfo(rvs, assembly, oinfo, ninfo,
                     dispatcher, event, "/events/vschart/dnd/addColumns", linkUri, true);
      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void removeColumns(@ClusterProxyKey String runtimeId, VSDndEvent event, Principal principal,
                             String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      if(rvs == null) {
         return null;
      }

      ChartVSAssembly assembly = (ChartVSAssembly) rvs.getViewsheet().getAssembly(event.name());
      ChartVSAssembly clone = assembly.clone();
      chartHandler.removeColumns(rvs, clone, event.getTransfer(), dispatcher, linkUri);
      GraphUtil.syncWorldCloudColor(clone.getVSChartInfo());
      applyChartInfo(rvs, assembly, (ChartVSAssemblyInfo)clone.getInfo(), dispatcher, event, linkUri, false);

      return null;
   }

   private void applyChartInfo(RuntimeViewsheet rvs, VSAssembly assembly,
                               ChartVSAssemblyInfo clone, CommandDispatcher dispatcher, VSDndEvent event,
                               String linkUri, boolean addedColumns)
      throws Exception
   {
      applyChartInfo(rvs, assembly, null, clone, dispatcher, event, null, linkUri, addedColumns);
   }

   private void applyChartInfo(RuntimeViewsheet rvs, VSAssembly assembly,
                               ChartVSAssemblyInfo oinfo, ChartVSAssemblyInfo clone,
                               CommandDispatcher dispatcher, VSDndEvent event,
                               String url, String linkUri, boolean addedColumns)
      throws Exception
   {
      (new ChangeChartProcessor()).fixSizeFrame(clone.getVSChartInfo());
      dataHandler.changeChartData(rvs, oinfo, clone, url, event, dispatcher);

      if(addedColumns) {
         ChangeChartAestheticController.syncWorldCloudColor(
            clone.getVSChartInfo(), event.getDropTarget());
         CSSChartStyles.apply(clone.getChartDescriptor(), clone.getVSChartInfo(),
                              null, clone.getCssParentParameters());
      }

      final BindingModel binding = bfactory.createModel(assembly);
      final SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
      dispatcher.sendCommand(bcommand);
      coreLifecycleService.refreshVSAssembly(rvs, assembly, dispatcher);

   }

   private boolean sourceChanged(VSAssembly assembly, String table) {
      SourceInfo sinfo = ((DataVSAssemblyInfo) assembly.getInfo()).getSourceInfo();
      return sinfo == null || assemblyInfoHandler.sourceChanged(table, assembly);
   }

   private final ViewsheetService viewsheetService;
   private final VSChartBindingHandler chartHandler;
   private final VSChartDataHandler dataHandler;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
   private final VSBindingService bfactory;
   private final CoreLifecycleService coreLifecycleService;



}
