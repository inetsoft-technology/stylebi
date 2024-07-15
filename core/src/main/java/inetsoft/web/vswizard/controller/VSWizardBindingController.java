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
package inetsoft.web.vswizard.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.AssetTreeModel;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.Util;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import inetsoft.web.binding.command.SetGrayedOutFieldsCommand;
import inetsoft.web.binding.command.VSTrapCommand;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSTreeHandler;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.vs.SourceChangeMessage;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import inetsoft.web.vswizard.HandleWizardExceptions;
import inetsoft.web.vswizard.Recommend;
import inetsoft.web.vswizard.command.*;
import inetsoft.web.vswizard.event.*;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import inetsoft.web.vswizard.model.*;
import inetsoft.web.vswizard.model.recommender.*;
import inetsoft.web.vswizard.recommender.VSRecommendationFactory;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import inetsoft.web.vswizard.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.*;

/**
 * vs wizard binding tree controller.
 */
@Controller
public class VSWizardBindingController {

   @Autowired
   public VSWizardBindingController(VSTreeHandler treeHandler,
                                    ViewsheetService viewsheetService,
                                    VSWizardDataService wizardDataService,
                                    VSWizardBindingHandler bindingHandler,
                                    VSAssemblyInfoHandler assemblyHandler,
                                    RuntimeViewsheetRef runtimeViewsheetRef,
                                    VSWizardTemporaryInfoService temporaryInfoService,
                                    VSRecommendationFactoryService recommenderService)
   {
      this.treeHandler = treeHandler;
      this.bindingHandler = bindingHandler;
      this.assemblyHandler = assemblyHandler;
      this.viewsheetService = viewsheetService;
      this.wizardDataService = wizardDataService;
      this.recommenderService = recommenderService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.temporaryInfoService = temporaryInfoService;
   }

   @LoadingMask
   @HandleWizardExceptions
   @MessageMapping("/vswizard/binding/tree")
   public void getBindingTree(@Payload GetBindingTreeEvent event,
                              CommandDispatcher dispatcher, Principal principal)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         LOGGER.error("Get runtimeId failed!");
         return;
      }

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.lockRead();

      try {
         VSTemporaryInfo temporaryInfo = temporaryInfoService.getVSTemporaryInfo(rvs);

         if(temporaryInfo == null || temporaryInfo.getTempChart() == null) {
            LOGGER.error("Get temporaryInfo failed!");
            return;
         }

         AssetTreeModel assetTreeModel = treeHandler.getChartTreeModel(
            viewsheetService.getAssetRepository(), rvs, temporaryInfo.getTempChart().getChartInfo(),
            true, principal);
         TreeNodeModel model = treeHandler.createTreeNodeModel(
            (AssetTreeModel.Node) assetTreeModel.getRoot(), principal);
         DataRefModel[] grayedOutFields = assemblyHandler.getGrayedOutFields(rvs);

         VSWizardTreeInfoModel treeInfo = new VSWizardTreeInfoModel();
         treeInfo.setGrayedOutFields(grayedOutFields);
         treeInfo.setTempAssemblyName(VSWizardConstants.TEMP_CHART_NAME);
         treeInfo.setTempBinding(bindingHandler.createTempChartBinding(temporaryInfo));

         RefreshWizardTreeCommand command =
            new RefreshWizardTreeCommand(model, treeInfo, event.reload(), false);
         dispatcher.sendCommand(command);
      }
      finally {
         box.unlockRead();
      }
   }

   @LoadingMask
   @Recommend
   @HandleWizardExceptions
   @MessageMapping("/vswizard/binding/tree/node-changed")
   public void bindingTreeNodeChanged(@Payload RefreshBindingFieldsEvent event,
                                      CommandDispatcher dispatcher, Principal principal,
                                      @LinkUri String linkUri)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      box.cancel(true);
      box.lockWrite();

      try {
         VSTemporaryInfo vsTemporaryInfo = temporaryInfoService.getVSTemporaryInfo(rvs);

         if(vsTemporaryInfo.isDestroyed()) {
            return;
         }

         if(event.selectedEntries().length > Util.getOrganizationMaxColumn()) {
            MessageCommand command = new MessageCommand();
            command.setMessage(Catalog.getCatalog().getString(
               "common.limited.column.wizard", Util.getOrganizationMaxColumn()));
            command.setType(MessageCommand.Type.ERROR);

            dispatcher.sendCommand(command);
            return;
         }

         boolean trap = wizardDataService.treeCheckTrap(id, event.selectedEntries(), principal);

         if(!StringUtils.isEmpty(event.tableName())) {
            SourceChangeMessage sourceChange = wizardDataService.checkSourceChanged(
               id, event.tableName(), principal);

            if(sourceChange.getChanged()) {
               dispatcher.sendCommand(new VSWizardSourceChangeCommand());

               return;
            }
         }

         if(trap) {
            VSTrapCommand trapCommand = new VSTrapCommand();
            trapCommand.addEvent("/events/vswizard/binding/tree/refresh-fields", event);
            dispatcher.sendCommand(trapCommand);
         }
         else {
            refreshBindingRefs(event, dispatcher, principal, linkUri);
         }
      }
      finally {
         box.unlockWrite();
      }
   }

   @Recommend
   @HandleWizardExceptions
   @MessageMapping("/vswizard/binding/tree/refresh-fields")
   public void refreshBindingRefs(@Payload RefreshBindingFieldsEvent event,
                                  CommandDispatcher dispatcher, Principal principal,
                                  @LinkUri String linkUri)
      throws Exception
   {
      AssetEntry[] entries = event.selectedEntries();
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         LOGGER.error("Get runtimeId failed!");
         return;
      }

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      box.cancel(true);
      box.lockWrite();

      try {
         // binding hcanged, clear out cached dataset. (44576)
         box.resetDataMap(VSWizardConstants.TEMP_CHART_NAME);

         VSTemporaryInfo vsTemporaryInfo = temporaryInfoService.getVSTemporaryInfo(rvs);
         VSWizardOriginalModel originalModel = vsTemporaryInfo.getOriginalModel();

         if(!StringUtils.isEmpty(event.tableName())) {
            // Handle source changed.
            SourceInfo oldSource = vsTemporaryInfo.getTempChart().getSourceInfo();
            SourceInfo newSource = bindingHandler.getCurrentSource(entries, event.tableName());
            boolean change = bindingHandler.changeSource(newSource, oldSource, vsTemporaryInfo, vs);

            if(change || oldSource == null) {
               VSUtil.setDefaultGeoColumns(vsTemporaryInfo.getTempChart().getVSChartInfo(), rvs,
                                           event.tableName());
               dispatcher.sendCommand(new RefreshWizardTreeTriggerCommand());
            }
         }

         if(!WizardRecommenderUtil.isLatestRecommend(vsTemporaryInfo)) {
            return;
         }

         try {
            dispatcher.sendCommand(new ShowRecommendLoadingCommand());

            // if reload (the first time wizard is shown), this is not triggered by a user
            // selection. the temp info is already initialized and should not be re-created
            // from entries. the entries as the date-level set to the default according to
            // data range, and it may be different from existing date-level in binding.
            // calling updateTemporaryFields() will cause the temp info to have a different
            // date level than in the binding. (34368)
            if(!event.reload()) {
               this.bindingHandler.updateTemporaryFields(rvs, entries, vsTemporaryInfo);
            }
         }
         finally {
            dispatcher.sendCommand(new ClearRecommendLoadingCommand());
         }

         if(event.reload() && originalModel != null && originalModel.getOriginalName() != null &&
            originalModel.getTempBinding() == null) {
            ChartVSAssembly tempChart = temporaryInfoService.getTempChart(id, principal);
            Assembly original = vs.getAssembly(originalModel.getOriginalName());
            //sync brush
            bindingHandler.syncChartAssembly(original, tempChart);
            originalModel.setTempBinding(tempChart.clone());

            //wizard should not apply max mode
            if(original instanceof ChartVSAssembly) {
               ChartVSAssembly chart = (ChartVSAssembly) original;
               ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
               info.setMaxSize(null);
               vs.setMaxMode(false);
            }
         }

         if(originalModel != null && originalModel.getOriginalName() != null) {
            bindingHandler.clearOriginalBrush(vs, originalModel.getOriginalName());
         }

         wizardRecommendation(entries, rvs, vsTemporaryInfo, id, linkUri, dispatcher,
                              principal, event.reload());
         DataRefModel[] grayedOutFields = assemblyHandler.getGrayedOutFields(rvs);
         dispatcher.sendCommand(new SetGrayedOutFieldsCommand(grayedOutFields));
      }
      finally {
         box.unlockWrite();
      }
   }

   public void wizardRecommendation(AssetEntry[] entries, RuntimeViewsheet rvs,
                                    VSTemporaryInfo temporaryInfo, String id,
                                    String linkUri, CommandDispatcher dispatcher,
                                    Principal principal)
      throws Exception
   {
      wizardRecommendation(entries, rvs, temporaryInfo, id, linkUri, dispatcher, principal, false);
   }

   private void wizardRecommendation(AssetEntry[] entries, RuntimeViewsheet rvs,
                                    VSTemporaryInfo temporaryInfo, String id,
                                    String linkUri, CommandDispatcher dispatcher,
                                    Principal principal, boolean reload)
      throws Exception
   {
      VSRecommendationModel model = getVSRecommendationModel(entries, rvs, temporaryInfo, id,
                                       linkUri, dispatcher, principal, reload);

      if(!WizardRecommenderUtil.isLatestRecommend(temporaryInfo)) {
         return;
      }

      temporaryInfo.setRecommendationModel(model);

      if(model != null && !StringUtils.isEmpty(temporaryInfo.getDescription()) &&
         !VSRecommendType.ORIGINAL_TYPE.equals(model.getSelectedType()))
      {
         temporaryInfo.setDescription(null);
         RefreshDescriptionCommand descCommand = new RefreshDescriptionCommand(null);
         dispatcher.sendCommand(descCommand);
      }

      // send Command
      RefreshRecommendCommand refreshRecommendCommand = new RefreshRecommendCommand(model);
      dispatcher.sendCommand(refreshRecommendCommand);
      VSWizardOriginalModel originalModel = temporaryInfo.getOriginalModel();

      // add primary assembly to viewsheet
      if(originalModel == null || originalModel.getOriginalType() == null || !reload) {
         bindingHandler.updatePrimaryAssembly(model, rvs, false, linkUri, dispatcher);
      }
   }

   private VSRecommendationModel getVSRecommendationModel(AssetEntry[] entries,
                                                          RuntimeViewsheet rvs,
                                                          VSTemporaryInfo tempInfo,
                                                          String id,
                                                          String linkUri,
                                                          CommandDispatcher dispatcher,
                                                          Principal principal,
                                                          boolean reload)
      throws Exception
   {
      dispatcher.sendCommand(new ShowRecommendLoadingCommand());
      VSRecommendationModel model;

      try {
         VSRecommendationFactory factory = recommenderService.getFactory();
         WizardRecommenderUtil.refreshStartEndDate(rvs.getViewsheetSandbox(), entries, tempInfo);
         model = factory.recommend(new VSWizardData(entries,
            (VSTemporaryInfo) tempInfo.clone()), principal);
      }
      finally {
         dispatcher.sendCommand(new ClearRecommendLoadingCommand());
      }

      if(model == null || !WizardRecommenderUtil.isLatestRecommend(tempInfo)) {
         return null;
      }

      VSWizardOriginalModel origModel = tempInfo.getOriginalModel();
      VSRecommendType selectType = tempInfo.getSelectedType();

      if(selectType != null && model.findRecommendation(selectType, false) != null) {
         model.setSelectedType(selectType);
      }

      VSSubType selectedSubType = tempInfo.getSelectedSubType();
      VSObjectRecommendation vr = model.findRecommendation(model.getSelectedType(), false);

      if(origModel != null && !StringUtils.isEmpty(origModel.getOriginalName())) {
         // add original type
         model.setOriginalType(origModel.getOriginalType());

         // set selectedSubType if the current chart binding matches a recommended chart
         if(selectedSubType == null && vr != null && origModel.getTempBinding() != null) {
            ChartInfo oinfo = origModel.getOriginalChartInfo();

            if(oinfo != null) {
               for(VSSubType subType : vr.getSubTypes()) {
                  if(subType instanceof ChartSubType) {
                     ChartInfo subInfo = ((ChartSubType) subType).getChartInfo();

                     if(subInfo == null || subInfo.getChartType() != oinfo.getChartType()) {
                        continue;
                     }

                     if(matchChart(subInfo, oinfo)) {
                        selectedSubType = subType;
                        break;
                     }
                  }
               }
            }
         }

         if(origModel.getOriginalType() != null && reload) {
            bindingHandler.addOriginalAsPrimary(id, principal, linkUri, false,  dispatcher);
            model.setSelectedType(VSRecommendType.ORIGINAL_TYPE);
         }
      }

      if(selectedSubType != null && vr != null) {
         int index = vr.getSubTypes().indexOf(selectedSubType);

         if(index < 0 && selectedSubType instanceof ChartSubType) {
            index = ((ChartSubType) selectedSubType).findBestMatch(vr.getSubTypes());
         }

         if(index >= 0) {
            vr.setSelectedIndex(index);
         }
         else {
            tempInfo.setSelectedSubType(null);
         }
      }
      else {
         tempInfo.setSelectedSubType(null);
      }

      return model;
   }

   // check if two chart binding matches
   private boolean matchChart(ChartInfo info1, ChartInfo info2) {
      if(info1.getChartType() != info2.getChartType()) {
         return false;
      }

      return matchRefs(info1.getXFields(), info2.getXFields()) &&
         matchRefs(info1.getYFields(), info2.getYFields()) &&
         matchRefs(info1.getGroupFields(), info2.getGroupFields()) &&
         matchAestheticRef(info1.getColorField(), info2.getColorField()) &&
         matchAestheticRef(info1.getShapeField(), info2.getShapeField()) &&
         matchAestheticRef(info1.getSizeField(), info2.getSizeField()) &&
         matchAestheticRef(info1.getTextField(), info2.getTextField());
   }

   // used by matchChart
   private boolean matchRefs(ChartRef[] refs1, ChartRef[] refs2) {
      if(refs1 == null || refs2 == null) {
         return refs1 == refs2;
      }

      if(refs1.length != refs2.length) {
         return false;
      }

      for(int i = 0; i < refs1.length; i++) {
         if(!refs1[i].getClass().equals(refs2[i].getClass())) {
            return false;
         }

         if(!refs1[i].getFullName().equals(refs2[i].getFullName())) {
            return false;
         }
      }

      return true;
   }

   // used by matchChart
   private boolean matchAestheticRef(AestheticRef ref1, AestheticRef ref2) {
      if(ref1 == null || ref2 == null) {
         return ref1 == ref2;
      }

      return Objects.equals(ref1.getDataRef(), ref2.getDataRef()) &&
         Objects.equals(ref1.getVisualFrameWrapper(), ref2.getVisualFrameWrapper());
   }

   @Recommend
   @MessageMapping("/vswizard/binding/refresh")
   @HandleWizardExceptions
   public void refreshBindingInfo(@Payload UpdateVsWizardBindingEvent event,
                                  CommandDispatcher dispatcher, Principal principal,
                                  @LinkUri String linkUri)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         LOGGER.error("Get runtimeId failed!");
         return;
      }

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      VSTemporaryInfo vsTemporaryInfo = temporaryInfoService.getVSTemporaryInfo(rvs);
      // The calculation of cardinality should start as early as possible
      WizardRecommenderUtil.calcCardinalities(box, (VSTemporaryInfo) vsTemporaryInfo.clone(),
         event.getSelectedNodes());

      box.cancel(true);
      box.lockWrite();

      try {
         vsTemporaryInfo.setAutoOrder(event.isAutoOrder());
         ChartVSAssembly tempChart = temporaryInfoService.getTempChart(id, principal);
         ChartBindingModel tempChartModel = event.getBindingModel();

         if(event.getDeleteFormatColumn() != null) {
            vsTemporaryInfo.removeFormat(event.getDeleteFormatColumn());
         }

         if(!WizardRecommenderUtil.isLatestRecommend(vsTemporaryInfo) || tempChart == null) {
            return;
         }

         bindingHandler.updateTempChartAssembly(tempChartModel, tempChart);
         DataRefModel[] grayedOutFields = assemblyHandler.getGrayedOutFields(rvs);
         dispatcher.sendCommand(new SetGrayedOutFieldsCommand(grayedOutFields));

         wizardRecommendation(event.getSelectedNodes(), rvs, vsTemporaryInfo, id,
                              linkUri, dispatcher, principal);
      }
      finally {
         box.unlockWrite();
      }
   }

   @HandleWizardExceptions
   @MessageMapping("/vswizard/binding/update-columns")
   public void updateColumns(@Payload UpdateColumnsEvent event,
                             CommandDispatcher dispatcher, Principal principal,
                             @LinkUri String linkUri)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         LOGGER.error("Get runtimeId failed!");
         return;
      }

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.lockWrite();

      try {
         VSTemporaryInfo vsTemporaryInfo = temporaryInfoService.getVSTemporaryInfo(rvs);
         int from = event.getTransfer();
         int target = event.getDropTarget();
         int to = from < target - 1 ? target - 1 : target;

         if(from == target || from < 0 || to < 0) {
            return;
         }

         VSRecommendationModel model = vsTemporaryInfo.getRecommendationModel();

         if(model != null && model.getSelectedType() == VSRecommendType.ORIGINAL_TYPE
            && StringUtils.hasText(event.getObjectType()))
         {
            model.setSelectedType("VSTable".equals(event.getObjectType()) ?
                                     VSRecommendType.TABLE : VSRecommendType.FILTER);

            RefreshRecommendCommand refreshRecommendCommand =
               new RefreshRecommendCommand(model);
            dispatcher.sendCommand(refreshRecommendCommand);
         }

         if(model != null && (model.getSelectedType() == VSRecommendType.TABLE ||
            model.getSelectedType() == VSRecommendType.ORIGINAL_TYPE)) {
            VSTableRecommendation vr = vsTemporaryInfo.getRecommendationModel()
               .getRecommendationList().stream()
               .filter(r -> r instanceof VSTableRecommendation)
               .map(r -> (VSTableRecommendation) r)
               .findFirst()
               .orElse(null);

            if(vr != null) {
               ColumnSelection cols = vr.getColumns();

               if(from < cols.getAttributeCount()) {
                  DataRef fDataRef = cols.getAttribute(from);
                  cols.removeAttribute(from);
                  cols.addAttribute(to, fDataRef);
                  bindingHandler.updatePrimaryAssembly(model, rvs, false, linkUri, dispatcher, true);
               }
            }
         }
         else if(model != null && model.getSelectedType() == VSRecommendType.FILTER) {
            VSFilterRecommendation vr = vsTemporaryInfo.getRecommendationModel()
               .getRecommendationList().stream()
               .filter(r -> r instanceof VSFilterRecommendation)
               .map(r -> (VSFilterRecommendation) r)
               .findFirst()
               .orElse(null);

            if(vr != null) {
               DataRef[] refs = vr.getDataRefs();
               List<DataRef> reflist = new ArrayList<>(Arrays.asList(refs));
               DataRef fDataRef = reflist.get(from);
               reflist.remove(from);
               reflist.add(to, fDataRef);
               vr.setDataRefs(reflist.toArray(new DataRef[0]));
               bindingHandler.updatePrimaryAssembly(model, rvs, false, linkUri, dispatcher, true);
            }
         }
      }
      finally {
         box.unlockWrite();
      }
   }

   private final VSTreeHandler treeHandler;
   private final ViewsheetService viewsheetService;
   private final VSWizardBindingHandler bindingHandler;
   private final VSAssemblyInfoHandler assemblyHandler;
   private final VSWizardDataService wizardDataService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSWizardTemporaryInfoService temporaryInfoService;
   private final VSRecommendationFactoryService recommenderService;

   private static final Logger LOGGER = LoggerFactory.getLogger(VSWizardBindingController.class);
}
