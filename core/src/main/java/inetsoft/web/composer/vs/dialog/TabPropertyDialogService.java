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

package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.TabVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.VSObjectTreeNode;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.command.PopulateVSObjectTreeCommand;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;

@Service
@ClusterProxy
public class TabPropertyDialogService {

   public TabPropertyDialogService(VSObjectPropertyService vsObjectPropertyService,
                                   VSObjectTreeService vsObjectTreeService,
                                   CoreLifecycleService coreLifecycleService,
                                   VSDialogService dialogService,
                                   ViewsheetService viewsheetService)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.vsObjectTreeService = vsObjectTreeService;
      this.coreLifecycleService = coreLifecycleService;
      this.dialogService = dialogService;
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public TabPropertyDialogModel getTabPropertyDialogModel(@ClusterProxyKey String runtimeId,
                                                           String objectId, Principal principal) throws Exception
   {
      Viewsheet vs;
      TabVSAssembly tabAssembly;
      TabVSAssemblyInfo tabAssemblyInfo;
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow(
         () -> new ExpiredSheetException(runtimeId, principal));
      box.lockRead();

      try {
         vs = rvs.getViewsheet();
         tabAssembly = (TabVSAssembly) vs.getAssembly(objectId);
         tabAssemblyInfo = (TabVSAssemblyInfo) tabAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         throw e;
      }
      finally {
         box.unlockRead();
      }

      TabPropertyDialogModel result = new TabPropertyDialogModel();
      TabGeneralPaneModel tabGeneralPaneModel = result.getTabGeneralPaneModel();
      GeneralPropPaneModel generalPropPaneModel = tabGeneralPaneModel.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      TabListPaneModel tabListPaneModel = tabGeneralPaneModel.getTabListPaneModel();
      SizePositionPaneModel sizePositionPaneModel = tabGeneralPaneModel.getSizePositionPaneModel();
      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPaneModel = VSAssemblyScriptPaneModel.builder();

      tabGeneralPaneModel.setBottomTabs(tabAssemblyInfo.getBottomTabsValue());

      generalPropPaneModel.setShowEnabledGroup(true);
      generalPropPaneModel.setEnabled(tabAssemblyInfo.getEnabledValue());

      basicGeneralPaneModel.setName(tabAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setPrimary(tabAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setVisible(tabAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setObjectNames(
         this.vsObjectPropertyService.getObjectNames(vs, tabAssemblyInfo.getAbsoluteName()));

      tabListPaneModel.setAssemblies(tabAssemblyInfo.getAssemblies());
      tabListPaneModel.setLabels(tabAssemblyInfo.getLabelsValue());

      Point pos = dialogService.getAssemblyPosition(tabAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(tabAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setContainer(tabAssembly.getContainer() != null);

      vsAssemblyScriptPaneModel.scriptEnabled(tabAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(tabAssemblyInfo.getScript() == null ?
                                              "" : tabAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   @ClusterWriteMethod
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setTabPropertyDialogModel(@ClusterProxyKey String runtimeId, String objectId,
                                         TabPropertyDialogModel value, String linkUri,
                                         Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet viewsheet;
      TabVSAssemblyInfo tabAssemblyInfo;
      TabVSAssembly tabAssembly;
      Viewsheet vs;

      try {
         viewsheet = viewsheetService.getViewsheet(runtimeId, principal);
         tabAssembly = (TabVSAssembly) viewsheet.getViewsheet().getAssembly(objectId);
         tabAssemblyInfo = (TabVSAssemblyInfo) Tool.clone(tabAssembly.getVSAssemblyInfo());
         vs = viewsheet.getViewsheet();
      }
      catch(Exception e) {
         throw e;
      }

      // Capture the current value before any modifications so we can detect a change below.
      boolean oldBottomTabs = tabAssemblyInfo.getBottomTabsValue();

      TabGeneralPaneModel tabGeneralPaneModel = value.getTabGeneralPaneModel();
      GeneralPropPaneModel generalPropPaneModel = tabGeneralPaneModel.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      TabListPaneModel tabListPaneModel = tabGeneralPaneModel.getTabListPaneModel();
      SizePositionPaneModel sizePositionPaneModel = tabGeneralPaneModel.getSizePositionPaneModel();
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel = value.getVsAssemblyScriptPaneModel();

      tabAssemblyInfo.setEnabledValue(generalPropPaneModel.getEnabled());

      tabAssemblyInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      tabAssemblyInfo.setVisibleValue(basicGeneralPaneModel.getVisible());

      String[] assemblies = tabListPaneModel.getAssemblies();
      String[] labels = tabListPaneModel.getLabels();

      for(int i = 0; i < labels.length; i++) {
         if(labels[i] == null || labels[i].isEmpty()) {
            labels[i] = assemblies[i];
         }
      }

      tabAssemblyInfo.setAssemblies(assemblies);
      tabAssemblyInfo.setLabelsValue(labels);

      tabAssemblyInfo.setScriptEnabled(vsAssemblyScriptPaneModel.scriptEnabled());
      tabAssemblyInfo.setScript(vsAssemblyScriptPaneModel.expression());

      // must precede repositionForBottomTabs: isBottomTabs() uses the dvalue fallback
      tabAssemblyInfo.setBottomTabsValue(tabGeneralPaneModel.getBottomTabs());

      if(oldBottomTabs != tabGeneralPaneModel.getBottomTabs()) {
         int originalTop = tabAssemblyInfo.getPixelOffset() != null
            ? tabAssemblyInfo.getPixelOffset().y : -1;

         // getAssemblyPosition() (which populated this dialog, in getTabPropertyDialogModel
         // above) prefers the tab's scaled position over its master pixel position whenever
         // one is set -- so if this tab has a scaled position, the value the client actually
         // submitted back in sizePositionPaneModel is the ORIGINAL SCALED top, not the master
         // top captured above. Capture it before reposition so the sync check below compares
         // against the coordinate space the model's value actually came from (Bug #76407
         // round 2 -- without this, the sync below never fires for a scaled-position tab,
         // sizePositionPaneModel keeps the stale scaled top, and setContainerPosition's own
         // scaled/master-collapsing behavior a few lines down overwrites the scaled position
         // we're about to reconcile with that stale value, undoing this fix within the same
         // request).
         Point originalScaledPos = tabAssemblyInfo.getLayoutPosition(true);
         boolean hasScaledPos = originalScaledPos != tabAssemblyInfo.getLayoutPosition(false);
         int originalScaledTop = hasScaledPos ? originalScaledPos.y : -1;

         TabVSAssemblyInfo.repositionForBottomTabs(tabAssemblyInfo, vs,
                                                   tabGeneralPaneModel.getBottomTabs());
         // scaled space: keep mobile/responsive layout preview flush too (Bug #76407)
         TabVSAssemblyInfo.repositionForBottomTabsInScaledSpace(tabAssemblyInfo, vs,
                                                   tabGeneralPaneModel.getBottomTabs());

         // sync position model only when the user didn't explicitly change the position;
         // if they did, let setContainerPosition translate the whole group to the new Y
         if(hasScaledPos) {
            if(sizePositionPaneModel.getTop() == originalScaledTop) {
               Point newScaledPos = tabAssemblyInfo.getLayoutPosition(true);

               if(newScaledPos != null) {
                  sizePositionPaneModel.setTop(newScaledPos.y);
               }
            }
         }
         else if(sizePositionPaneModel.getTop() == originalTop) {
            Point newTabPos = tabAssemblyInfo.getPixelOffset();

            if(newTabPos != null) {
               sizePositionPaneModel.setTop(newTabPos.y);
            }
         }
      }

      // @by changhongyang 2017-10-10, move tab children in addition to tab
      if(sizePositionPaneModel.getLeft() >= 0 && sizePositionPaneModel.getTop() >= 0) {
         dialogService.setContainerPosition(tabAssemblyInfo, sizePositionPaneModel,
                                            tabAssemblyInfo.getAssemblies(), vs);

         ChangedAssemblyList clist = this.coreLifecycleService.createList(false,
                                                                          commandDispatcher, viewsheet, linkUri);
         this.coreLifecycleService.layoutViewsheet(viewsheet, viewsheet.getID(), linkUri,
                                                   commandDispatcher, tabAssembly.getAbsoluteName(), clist);
      }

      dialogService.setAssemblySize(tabAssemblyInfo, sizePositionPaneModel);

      for(int i = 0; i < labels.length; i++) {
         if(labels[i] == null || labels[i].isEmpty()) {
            labels[i] = assemblies[i];
         }
      }

      tabAssemblyInfo.setAssemblies(assemblies);
      tabAssemblyInfo.setLabelsValue(labels);

      tabAssemblyInfo.setScriptEnabled(vsAssemblyScriptPaneModel.scriptEnabled());
      tabAssemblyInfo.setScript(vsAssemblyScriptPaneModel.expression());

      this.vsObjectPropertyService.editObjectProperty(
         viewsheet, tabAssemblyInfo, objectId, basicGeneralPaneModel.getName(), linkUri, principal,
         commandDispatcher);

      VSObjectTreeNode tree = vsObjectTreeService.getObjectTree(viewsheet);
      PopulateVSObjectTreeCommand treeCommand = new PopulateVSObjectTreeCommand(tree);
      commandDispatcher.sendCommand(treeCommand);

      return null;
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final VSObjectTreeService vsObjectTreeService;
   private final CoreLifecycleService coreLifecycleService;
   private final VSDialogService dialogService;
   private final ViewsheetService viewsheetService;
}
