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

package inetsoft.web.vswizard.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import inetsoft.util.script.ScriptException;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSTreeHandler;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.command.*;
import inetsoft.web.vswizard.event.*;
import inetsoft.web.vswizard.handler.*;
import inetsoft.web.vswizard.model.*;
import inetsoft.web.vswizard.model.recommender.*;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import inetsoft.web.vswizard.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.util.*;
import java.util.List;

import static inetsoft.web.vswizard.recommender.execution.WizardDataExecutor.CACHE_ID_PREFIX;

@Service
@ClusterProxy
public class VSWizardObjectService {

   public VSWizardObjectService(VSTreeHandler treeHandler,
                                SyncChartHandler syncChartHandler,
                                SyncTableHandler syncTableHandler,
                                ViewsheetService viewsheetService,
                                WizardVSObjectService objectService,
                                VSWizardObjectHandler objectHandler,
                                CoreLifecycleService coreLifecycleService,
                                VSAssemblyInfoHandler assemblyHandler,
                                VSWizardBindingHandler bindingHandler,
                                WizardViewsheetService wizardVSService,
                                SyncCrosstabHandler syncCrosstabHandler,
                                VSWizardTemporaryInfoService temporaryInfoService)
   {
      this.treeHandler = treeHandler;
      this.objectService = objectService;
      this.objectHandler = objectHandler;
      this.bindingHandler = bindingHandler;
      this.assemblyHandler = assemblyHandler;
      this.wizardVSService = wizardVSService;
      this.syncTableHandler = syncTableHandler;
      this.viewsheetService = viewsheetService;
      this.syncChartHandler = syncChartHandler;
      this.coreLifecycleService = coreLifecycleService;
      this.syncCrosstabHandler = syncCrosstabHandler;
      this.temporaryInfoService = temporaryInfoService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void openViewsheetWizardObject(@ClusterProxyKey String vsId, OpenWizardObjectEvent event, Principal principal,
                                         CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      // click cancel from full editor back to objectWizard, there is no need to destroy tempInfo
      boolean goBack = "cancel".equals(event.getBindingOption());

      if(rvs == null) {
         LOGGER.info("Sheet does not exist!");
         return null;
      }

      final Viewsheet vs = rvs.getViewsheet();
      AssetEntry baseEntry = vs.getBaseEntry();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.lockWrite();

      try {
         if(rvs.getRuntimeWorksheet() != null) {
            rvs.getRuntimeWorksheet().getWorksheet().getWorksheetInfo().setTempMaxRow(100);
         }

         // For clean temp failed.
         if(temporaryInfoService.existTemporary(event.getRuntimeId(), principal) && !goBack &&
            !WizardRecommenderUtil.isTempAssembly(event.getAssemblyName()))
         {
            temporaryInfoService.destroyTemporary(event.getRuntimeId(), principal);
         }

         if(!goBack || temporaryInfoService.getVSTemporaryInfo(rvs) == null) {
            temporaryInfoService.initTemporary(event.getRuntimeId(), principal,
                                               new Point(event.getX(), event.getY()));
         }

         SetWizardBindingTreeNodesCommand command = new SetWizardBindingTreeNodesCommand();
         // click cancel without to recommend
         command.setToRecommand(!goBack);
         VSTemporaryInfo temporaryInfo = temporaryInfoService.getVSTemporaryInfo(rvs);
         ChartVSAssembly tempChart = temporaryInfoService.getTempChart(event.getRuntimeId(),
                                                                       principal);

         // reload assembly info when edit an assembly in wizard.
         if(event.getAssemblyName() != null) { // edit
            VSAssembly assembly = vs.getAssembly(event.getAssemblyName());

            if(assembly == null) {
               LOGGER.error("Failed to open wizard for: " + event.getAssemblyName());
               return null;
            }

            clearOriginalSelections(rvs, assembly, linkUri, dispatcher);
            assembly.setWizardEditing(true);
            assembly = (VSAssembly) assembly.clone();
            VSWizardOriginalModel originalModel = temporaryInfo.getOriginalModel();
            SourceInfo source = objectHandler.reloadSourceInfo(assembly, tempChart);

            if(originalModel == null) {
               originalModel = new VSWizardOriginalModel();
               temporaryInfo.setOriginalModel(originalModel);
            }

            if(assembly instanceof ChartVSAssembly) {
               originalModel.setOriginalChartInfo(((ChartVSAssembly) assembly).getVSChartInfo());
            }

            if(!goBack) {
               // keep position of edit assembly for component wizard.
               temporaryInfo.setPosition(assembly.getPixelOffset());
               originalModel.setOriginalName(event.getAssemblyName());
               // selected current type
               originalModel.setOriginalType(VSRecommendType.ORIGINAL_TYPE);

               if(source == null) {
                  originalModel.setEmptyAssembly(true);
               }

               objectHandler.reloadGeoCols(assembly, tempChart);
               objectHandler.reloadAggegateInfo(temporaryInfo, rvs.getViewsheet(), assembly, tempChart);
               bindingHandler.reloadFormats(temporaryInfo, rvs, assembly);
               bindingHandler.reloadLegendVisible(rvs, assembly);
               // convert the binding of assembly to temp chart.
               bindingHandler.convertBinding(vs, tempChart, assembly, temporaryInfo);
               //sync brush
               bindingHandler.syncChartAssembly(assembly, tempChart);

            }
            else if(source == null) {
               originalModel.setEmptyAssembly(true);
            }

            // reload selected nodes
            List<String> selectedPaths = null;

            if(!goBack) {
               selectedPaths = bindingHandler.getSelectedPath(
                  tempChart.getAggregateInfo(), assembly, baseEntry, principal);
            }
            else {
               selectedPaths = bindingHandler.getSelectedPath(
                  tempChart.getAggregateInfo(), tempChart, baseEntry, principal);
            }

            command.setSelectedPaths(selectedPaths);
         }
         else {
            temporaryInfo.setAutoOrder(true);
         }

         OpenObjectWizardCommand openCommand = new OpenObjectWizardCommand(true);
         dispatcher.sendCommand(openCommand);

         AssetTreeModel assetTreeModel = treeHandler.getChartTreeModel(
            viewsheetService.getAssetRepository(), rvs,
            temporaryInfo.getTempChart().getChartInfo(), true, principal);
         TreeNodeModel model = treeHandler.createTreeNodeModel(
            (AssetTreeModel.Node) assetTreeModel.getRoot(), principal);
         DataRefModel[] grayedOutFields = assemblyHandler.getGrayedOutFields(rvs);

         VSWizardTreeInfoModel treeInfo = new VSWizardTreeInfoModel();
         treeInfo.setGrayedOutFields(grayedOutFields);
         treeInfo.setTempAssemblyName(VSWizardConstants.TEMP_CHART_NAME);
         treeInfo.setTempBinding(bindingHandler.createTempChartBinding(temporaryInfo));

         RefreshWizardTreeCommand cmd = new RefreshWizardTreeCommand(model, treeInfo, true, true);
         InitWizardBindingTreeCommand initBindingTreeCommand = new InitWizardBindingTreeCommand();
         initBindingTreeCommand.setRefreshWizardTreeCommand(cmd);
         initBindingTreeCommand.setSetWizardBindingTreeNodesCommand(command);
         dispatcher.sendCommand(initBindingTreeCommand);
      }
      finally {
         if(rvs.getRuntimeWorksheet() != null) {
            rvs.getRuntimeWorksheet().getWorksheet().getWorksheetInfo().setTempMaxRow(-1);
         }

         box.unlockWrite();
      }

      return null;
   }

   private Void clearOriginalSelections(RuntimeViewsheet rvs, VSAssembly assembly, String uri,
                                        CommandDispatcher dispatcher)
      throws Exception
   {
      if(!(assembly instanceof SelectionVSAssembly)) {
         return null;
      }

      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      String name = assembly.getAbsoluteName();
      final String fname = name;
      final int dot = name.indexOf('.');

      if(dot >= 0) {
         String bname = name.substring(0, dot);
         Assembly vsc = vs.getAssembly(bname);
         ViewsheetSandbox box2 = box.getSandbox(bname);

         if(vsc instanceof Viewsheet && box2 != null) {
            vs = (Viewsheet) vsc;
            box = box2;
            name = name.substring(dot + 1);
         }
      }

      final Worksheet ws = vs.getBaseWorksheet();
      final AssemblyEntry assemblyEntry = assembly.getAssemblyEntry();

      final Set<SelectionVSAssembly> relatedSelections = new HashSet<>();
      final AssemblyRef[] refs = vs.getDependings(assemblyEntry);
      final List<Assembly> rlist = new ArrayList<>();

      final SelectionVSAssembly sassembly = (SelectionVSAssembly) assembly;
      final List<String> tableNames = sassembly.getTableNames();

      for(String tname : tableNames) {
         final SelectionVSAssembly[] sarr = box.getSelectionVSAssemblies(tname);

         final Optional<SelectionVSAssembly> relatedSelection = Arrays.stream(sarr)
            .filter(item -> item != assembly)
            .findFirst();

         // one selection is sufficient because other related selections will be updated
         // during processing in ViewsheetSandbox.
         relatedSelection.ifPresent(relatedSelections::add);
      }

      for(int i = 0; i < refs.length; i++) {
         AssemblyEntry entry = refs[i].getEntry();
         Assembly tassembly = null;

         if(entry.isWSAssembly()) {
            tassembly = ws != null ? ws.getAssembly(entry) : null;

            // reexecute runtime condition list
            if(tassembly instanceof TableAssembly &&
               !(assembly instanceof EmbeddedTableVSAssembly))
            {
               ((TableAssembly) tassembly).setPreRuntimeConditionList(null);
               ((TableAssembly) tassembly).setPostRuntimeConditionList(null);
               AssemblyRef[] refs2 = vs.getDependeds(entry);

               for(int j = 0; refs2 != null && j < refs2.length; j++) {
                  Assembly assembly2 = vs.getAssembly(refs2[j].getEntry());

                  if(assembly2 instanceof SelectionVSAssembly) {
                     rlist.add(assembly2);
                  }
               }
            }
         }
         else if(!name.equals(entry.getName())) {
            tassembly = vs.getAssembly(entry);
         }

         if(tassembly != null) {
            rlist.add(tassembly);
         }
      }

      Assembly[] rarr = new Assembly[rlist.size()];
      rlist.toArray(rarr);
      AssemblyRef[] vrefs = vs.getViewDependings(assemblyEntry);

      ViewsheetInfo vinfo = vs.getViewsheetInfo();
      vinfo.setFilterID(assembly.getAbsoluteName(), null);
      vinfo.removeLocalID(name, true);

      // reexecute depending assemblies
      if(rarr.length > 0) {
         ChangedAssemblyList clist = new ChangedAssemblyList();
         box.reset(null, rarr, clist, false, false, null);
         coreLifecycleService.execute(rvs, fname, uri, clist, dispatcher, false);
      }

      // reprocess associated assemblies
      if(!relatedSelections.isEmpty()) {
         try {
            int hint = VSAssembly.OUTPUT_DATA_CHANGED;

            for(SelectionVSAssembly relatedSelection : relatedSelections) {
               coreLifecycleService.execute(
                  rvs, relatedSelection.getAbsoluteName(), uri, hint, dispatcher);
            }
         }
         catch(ConfirmDataException ex) {
            // do nothing, it doesn't need the confirm data exception
            // when remove assembly
         }
         catch(ScriptException ex) {
            List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

            if(exs != null) {
               exs.add(ex);
            }
         }
      }

      // refresh views dependings
      for(int i = 0; i < vrefs.length; i++) {
         AssemblyEntry entry = vrefs[i].getEntry();

         if(entry.isVSAssembly()) {
            try {
               box.executeView(entry.getName(), true);
            }
            catch(ConfirmDataException ex) {
               // do nothing, it doesn't need the confirm data exception
               // when remove assembly
            }
            catch(ScriptException ex) {
               List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

               if(exs != null) {
                  exs.add(ex);
               }
            }
         }
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void refreshObjectWizard(@ClusterProxyKey String id, Principal principal,
                                   CommandDispatcher dispatcher, String linkUri) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.lockRead();

      try {
         VSTemporaryInfo vsTemporaryInfo = temporaryInfoService.getVSTemporaryInfo(rvs);
         VSRecommendationModel recommendModel = vsTemporaryInfo.getRecommendationModel();
         VSRecommendType selectedType = recommendModel != null ? recommendModel.getSelectedType() : null;

         //1.send recommend command
         RefreshRecommendCommand refreshRecommendCommand =
            new RefreshRecommendCommand(vsTemporaryInfo.getRecommendationModel());
         dispatcher.sendCommand(refreshRecommendCommand);

         //2.update primary assembly
         if(VSRecommendType.ORIGINAL_TYPE.equals(selectedType)) {
            bindingHandler.addOriginalAsPrimary(id, principal, linkUri, false, dispatcher);
         }
         else {
            VSAssembly latestAssembly = WizardRecommenderUtil.getTempAssembly(rvs.getViewsheet());
            bindingHandler.updatePrimaryAssembly(rvs, linkUri, latestAssembly, false, false, dispatcher);
         }
      }
      finally {
         box.unlockRead();
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void updateObjectByTempAssembly(@ClusterProxyKey String rid, UpdateWizardObjectEvent event, Principal principal,
                                          CommandDispatcher dispatcher, String linkUri) throws Exception
   {
      String assemblyName = event.getAssemblyName();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(rid, principal);
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.lockWrite();

      try {
         VSAssembly tempAssembly = vs.getAssembly(assemblyName);
         VSTemporaryInfo vsTemporaryInfo = temporaryInfoService.getVSTemporaryInfo(rvs);
         RuntimeViewsheet orvs = getOriginalRViewsheet(rvs, principal);
         orvs.getViewsheetSandbox().getVariableTable().addAll(rvs.getViewsheetSandbox().getVariableTable());
         OpenObjectWizardCommand command = new OpenObjectWizardCommand();

         // click cancel on objectWizard and finish on binding.
         if(vsTemporaryInfo == null) {
            orvs.addCheckpoint(orvs.getViewsheet().prepareCheckpoint());
            dispatcher.sendCommand(command);

            return null;
         }

         Viewsheet ovs = orvs.getViewsheet();
         updateCalcFields(vs, ovs);
         ovs.copyDimensionColors(vs);
         VSWizardOriginalModel originalModel = vsTemporaryInfo.getOriginalModel();
         String oname = originalModel == null ? null : originalModel.getOriginalName();

         if(oname == null && !WizardRecommenderUtil.isTempAssembly(event.getOriginalName())) {
            oname = event.getOriginalName();
         }

         VSAssembly originalAssembly = oname == null ? null : ovs.getAssembly(oname);
         tempAssembly = objectHandler.updateAssemblyByTemporary(orvs, rvs, tempAssembly,
                                                                originalAssembly, dispatcher, linkUri);
         temporaryInfoService.destroyTemporary(rid, principal);
         //sync drill filter assembly
         syncDrillFilterAssembly(orvs, vs, originalAssembly == null);

         orvs.setBindingID(null);
         vs = orvs.getViewsheet();

         // refresh in wizard grid pane.
         if(VSWizardEditModes.WIZARD_DASHBOARD.equals(event.getOriginalMode())) {
            if(vs.getAssembly(tempAssembly.getName()) != null) {
               vs.removeAssembly(tempAssembly.getName());
            }

            Point newRowAndCol = wizardVSService.updateGridRowsAndNewBlock(vs.getAssemblies(),
                                                                           dispatcher);
            this.objectService.addVsObject(orvs, tempAssembly, newRowAndCol.y, newRowAndCol.x,
                                           originalAssembly != null,
                                           linkUri, principal, dispatcher);
            command.setOpen(true);
         }
         else {
            vs.addAssembly(tempAssembly, originalAssembly == null);
            AssemblyChangedCommand cmd = new AssemblyChangedCommand();
            cmd.setName(tempAssembly.getAbsoluteName());

            if(originalAssembly != null &&
               !Tool.equals(tempAssembly.getAbsoluteName(), originalAssembly.getAbsoluteName()))
            {
               cmd.setOname(originalAssembly.getAbsoluteName());
            }

            dispatcher.sendCommand(cmd);
         }

         orvs.addCheckpoint(vs.prepareCheckpoint());
         dispatcher.sendCommand(command);
      }
      finally {
         box.unlockWrite();
      }

      return null;
   }

   private Void syncDrillFilterAssembly(RuntimeViewsheet orvs, Viewsheet vs, boolean adjust) {
      for(Assembly assembly : vs.getAssemblies()) {
         if(assembly instanceof DrillFilterVSAssembly &&
            !WizardRecommenderUtil.isTempAssembly(assembly.getName()) &&
            orvs.getViewsheet().getAssembly(assembly.getName()) != null)
         {
            orvs.getViewsheet().addAssembly((VSAssembly) assembly, adjust);
         }
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void switchToMeta(@ClusterProxyKey String id, SwitchToMetaModeEvent event,
                            Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.lockRead();

      try {
         cancelExecution(rvs);

         if(!VSWizardEditModes.WIZARD_DASHBOARD.equals(event.getOriginalMode())) {
            rvs = getOriginalRViewsheet(rvs, principal);
         }

         Viewsheet vs = rvs.getViewsheet();
         ViewsheetInfo vinfo = vs.getViewsheetInfo();
         vinfo.setMetadata(true);
         rvs.addCheckpoint(rvs.getViewsheet().prepareCheckpoint());
      }
      finally {
         box.unlockRead();
      }

      return null;
   }

   private Void cancelExecution(RuntimeViewsheet rvs) {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.cancel();
      AssetDataCache.cancel(CACHE_ID_PREFIX + box.getID());

      return null;
   }

   /**
    * Get the original runtime viewsheet directly
    * @param rvs
    * @param principal
    */
   private RuntimeViewsheet getOriginalRViewsheet(RuntimeViewsheet rvs,Principal principal)
      throws Exception
   {
      String dirOriginalId = objectHandler.getOriginalRuntimeId(rvs.getID(), principal);
      RuntimeViewsheet dirOriginalVS = viewsheetService.getViewsheet(dirOriginalId, principal);

      return dirOriginalVS;
   }

   private Void updateCalcFields(Viewsheet vs, Viewsheet ovs) {
      if(vs == ovs) {
         return null;
      }

      // remove old
      for(String source : ovs.getCalcFieldSources()) {
         ovs.removeCalcField(source);
      }

      for(String source : ovs.getAggrFieldSources()) {
         ovs.removeAggrField(source);
      }

      // add new
      for(String source : vs.getCalcFieldSources()) {
         CalculateRef[] newCalcFields = vs.getCalcFields(source);

         if(newCalcFields != null) {
            Arrays.stream(newCalcFields).forEach(field -> {
               CalculateRef calc = new CalculateRef(field.isBaseOnDetail());
               calc.setDataRef((DataRef) field.getDataRef().clone());
               calc.setFake(field.isFake());
               calc.copyAttributes(field);
               calc.setDcRuntime(field.isDcRuntime());
               ovs.addCalcField(source, calc);
            });
         }
      }

      for(String source : vs.getAggrFieldSources()) {
         AggregateRef[] aggregateRefs = vs.getAggrFields(source);

         if(aggregateRefs != null) {
            Arrays.stream(aggregateRefs).forEach(agg ->
                                                    ovs.addAggrField(source, (AggregateRef) agg.clone()));
         }
      }

      return null;
   }


   private final VSTreeHandler treeHandler;
   private final VSAssemblyInfoHandler assemblyHandler;
   private final ViewsheetService viewsheetService;
   private final WizardVSObjectService objectService;
   private final VSWizardObjectHandler objectHandler;
   private final CoreLifecycleService coreLifecycleService;
   private final VSWizardBindingHandler bindingHandler;
   private final WizardViewsheetService wizardVSService;
   private final VSWizardTemporaryInfoService temporaryInfoService;
   private final SyncChartHandler syncChartHandler;
   private final SyncTableHandler syncTableHandler;
   private final SyncCrosstabHandler syncCrosstabHandler;
   private static final Logger LOGGER = LoggerFactory.getLogger(VSWizardObjectService.class);

}
