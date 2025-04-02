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
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.execution.DataMap;
import inetsoft.report.composition.execution.VSAQuery;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.controller.VSTableLayoutService;
import inetsoft.web.binding.dnd.CalcDropTarget;
import inetsoft.web.binding.dnd.CalcTableTransfer;
import inetsoft.web.binding.event.VSDndEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSCalcTableBindingHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;
import inetsoft.uql.asset.ConfirmException;

import java.awt.*;
import java.security.Principal;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Service
@ClusterProxy
public class VSCalcTableDndService {

   public VSCalcTableDndService(VSBindingService bfactory,
                                VSTableLayoutService tableLayoutService,
                                VSCalcTableBindingHandler calcTableHandler,
                                ViewsheetService viewsheetService,
                                CoreLifecycleService coreLifecycleService,
                                VSAssemblyInfoHandler assemblyInfoHandler)
   {
      this.viewsheetService = viewsheetService;
      this.calcTableHandler = calcTableHandler;
      this.bfactory = bfactory;
      this.coreLifecycleService = coreLifecycleService;
      this.tableLayoutService = tableLayoutService;
      this.assemblyInfoHandler = assemblyInfoHandler;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void addRemoveColumns(@ClusterProxyKey String runtimeVS, VSDndEvent event, Principal principal,
                                String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeVS, principal);

      if(rvs == null) {
         return null;
      }

      CalcTableVSAssembly assembly = (CalcTableVSAssembly) rvs.getViewsheet().getAssembly(event.name());
      CalcTableVSAssembly clone = (CalcTableVSAssembly) assembly.clone();
      CalcTableTransfer transfer = (CalcTableTransfer)event.getTransfer();
      CalcDropTarget target = (CalcDropTarget)event.getDropTarget();
      calcTableHandler.addRemoveColumns(clone, transfer.getDragRect(), target.getDropRect());
      applyAssemblyInfo(rvs, assembly, (VSAssemblyInfo)clone.getInfo(), dispatcher,
                        event, linkUri, null);

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

      CalcTableVSAssembly assembly = (CalcTableVSAssembly) rvs.getViewsheet().getAssembly(event.name());
      CalcTableVSAssembly nassembly = (CalcTableVSAssembly) assembly.clone();
      CalcTableVSAssemblyInfo ninfo = (CalcTableVSAssemblyInfo) nassembly.getInfo();

      // Handle source changed.
      if(sourceChanged(assembly, event.getTable())) {
         assemblyInfoHandler.changeSource(assembly, event.getTable(), event.getSourceType());
         CalcTableVSAssemblyInfo vsCalcTableInfo =
            (CalcTableVSAssemblyInfo) nassembly.getVSAssemblyInfo();

         if(vsCalcTableInfo != null) {
            AggregateInfo ainfo =  vsCalcTableInfo.getAggregateInfo();

            if(ainfo != null) {
               List<DataRef> calcFields = ainfo.getFormulaFields();
               Set<String> calcFieldsRefs = ainfo.removeFormulaFields(calcFields);
               nassembly.getTableLayout().clearFormulaBinding(calcFieldsRefs);
            }
         }
      }

      if(ninfo.getSourceInfo() == null) {
         ninfo.setSourceInfo(new SourceInfo(event.getSourceType(), null, event.getTable()));
      }

      CalcDropTarget target = (CalcDropTarget) event.getDropTarget();
      calcTableHandler.addColumns(nassembly, event.getEntries(), target.getDropRect(), rvs);
      applyAssemblyInfo(rvs, assembly, nassembly, dispatcher, event,
                        "/events/vscalctable/dnd/addColumns", linkUri);

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

      CalcTableVSAssembly assembly = (CalcTableVSAssembly) rvs.getViewsheet().getAssembly(event.name());
      CalcTableVSAssembly clone = (CalcTableVSAssembly) assembly.clone();
      CalcTableTransfer transfer = (CalcTableTransfer) event.getTransfer();
      calcTableHandler.removeColumns(clone, transfer.getDragRect());
      applyAssemblyInfo(rvs, assembly, (VSAssemblyInfo) clone.getInfo(), dispatcher, event,
                        linkUri, null);

      return null;
   }

   private void createDndCommands(RuntimeViewsheet rvs, VSAssembly assembly,
                                    CommandDispatcher dispatcher, VSDndEvent event, String linkUri) throws Exception
   {
      final BindingModel binding = bfactory.createModel(assembly);
      final SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
      dispatcher.sendCommand(bcommand);
      coreLifecycleService.refreshVSAssembly(rvs, assembly, dispatcher);

      String name = assembly.getInfo().getAbsoluteName();
      Rectangle rect = null;

      if(event.getDropTarget() != null) {
         rect = ((CalcDropTarget) event.getDropTarget()).getDropRect();
      }
      else {
         rect = ((CalcTableTransfer) event.getTransfer()).getDragRect();
      }

      CalcTableVSAssembly calc = (CalcTableVSAssembly) assembly;
      dispatcher.sendCommand(name,
                             tableLayoutService.createCellBindingCommand(rvs, calc, rect.y, rect.x));
      dispatcher.sendCommand(name, tableLayoutService.createTableLayoutCommand(rvs, calc));
   }

   protected void applyAssemblyInfo(RuntimeViewsheet rvs, VSAssembly oassembly,
                                    VSAssembly nassembly, CommandDispatcher dispatcher,
                                    VSDndEvent event, String url, String linkUri)
      throws Exception
   {
      applyAssemblyInfo(rvs, oassembly, nassembly, dispatcher,
         event, url, linkUri, null, null);
   }

   protected void applyAssemblyInfo(RuntimeViewsheet rvs, VSAssembly oassembly,
                                    VSAssembly nassembly, CommandDispatcher dispatcher,
                                    VSDndEvent event, String url, String linkUri,
                                    Consumer<VSCrosstabInfo> updateCalculate,
                                    BiConsumer<Map<TableDataPath, VSCompositeFormat>, TableDataPath> clearAliasFormatProcessor)
      throws Exception
   {
      // validate current binding when source changed.
      if(oassembly instanceof DataVSAssembly) {
         SourceInfo osource = ((DataVSAssemblyInfo) oassembly.getInfo()).getSourceInfo();
         SourceInfo nsource = ((DataVSAssemblyInfo) nassembly.getInfo()).getSourceInfo();

         if(osource != null && osource.getSource() != null && nsource != null) {
            if(!osource.getSource().equals(nsource.getSource()) &&
               nsource.getType() == SourceInfo.VS_ASSEMBLY)
            {
               VSAQuery query = VSAQuery.createVSAQuery(rvs.getViewsheetSandbox(),
                                                        nassembly, DataMap.DETAIL);
               query.createAssemblyTable(nassembly.getTableName());
            }

            if(!osource.getSource().equals(nsource.getSource()) ||
               VSUtil.isVSAssemblyBinding(event.getTable()))
            {
               assemblyInfoHandler.validateBinding(nassembly);
            }
         }
      }

      applyAssemblyInfo(rvs, oassembly, (VSAssemblyInfo) nassembly.getInfo(), dispatcher,
         event, url, linkUri, updateCalculate, clearAliasFormatProcessor);
   }

   protected void applyAssemblyInfo(RuntimeViewsheet rvs, VSAssembly assembly,
                                    VSAssemblyInfo clone, CommandDispatcher dispatcher,
                                    VSDndEvent event, String linkUri,
                                    Consumer<VSCrosstabInfo> updateCalculate)
      throws Exception
   {
      applyAssemblyInfo(rvs, assembly, clone, dispatcher, event, null, linkUri, updateCalculate);
   }

   protected void applyAssemblyInfo(RuntimeViewsheet rvs, VSAssembly assembly,
                                    VSAssemblyInfo clone, CommandDispatcher dispatcher,
                                    VSDndEvent event, String url, String linkUri,
                                    Consumer<VSCrosstabInfo> updateCalculate)
      throws Exception
   {
      applyAssemblyInfo(rvs, assembly, clone, dispatcher, event, url,
         linkUri, updateCalculate, null);
   }

   protected void applyAssemblyInfo(RuntimeViewsheet rvs, VSAssembly assembly,
      VSAssemblyInfo clone, CommandDispatcher dispatcher, VSDndEvent event,
      String url, String linkUri, Consumer<VSCrosstabInfo> updateCalculate,
      BiConsumer<Map<TableDataPath, VSCompositeFormat>, TableDataPath> clearAliasFormatProcessor)
      throws Exception
   {
      assemblyInfoHandler.apply(rvs, clone, viewsheetService, event.confirmed(),
         event.checkTrap(), false, false, dispatcher, url,
         event, linkUri, updateCalculate, clearAliasFormatProcessor);

      try {
         createDndCommands(rvs, assembly, dispatcher, event, linkUri);
      }
      catch(ConfirmException ex) {
         if(!coreLifecycleService.waitForMV(ex, rvs, dispatcher)) {
            throw ex;
         }
      }
   }

   protected boolean sourceChanged(VSAssembly assembly, String table) {
      SourceInfo sinfo = ((DataVSAssemblyInfo) assembly.getInfo()).getSourceInfo();
      return sinfo == null || assemblyInfoHandler.sourceChanged(table, assembly);
   }

   private final ViewsheetService viewsheetService;
   private final VSCalcTableBindingHandler calcTableHandler;
   private final VSBindingService bfactory;
   private final CoreLifecycleService coreLifecycleService;
   private final VSTableLayoutService tableLayoutService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;


}
