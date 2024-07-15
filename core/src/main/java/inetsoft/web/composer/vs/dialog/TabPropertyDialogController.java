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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
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
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.security.Principal;

@Controller
public class TabPropertyDialogController {
   /**
    * Creates a new instance of <tt>TabPropertyDialogController</tt>.
    * @param vsObjectPropertyService VSObjectProperty service
    * @param runtimeViewsheetRef     RuntimeViewsheetRef service
    * @param viewsheetService
    */
   @Autowired
   public TabPropertyDialogController(
      VSObjectPropertyService vsObjectPropertyService,
      RuntimeViewsheetRef runtimeViewsheetRef,
      VSObjectTreeService vsObjectTreeService,
      PlaceholderService placeholderService,
      VSDialogService dialogService,
      ViewsheetService viewsheetService)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsObjectTreeService = vsObjectTreeService;
      this.placeholderService = placeholderService;
      this.dialogService = dialogService;
      this.viewsheetService = viewsheetService;
   }

   /**
    * Gets the top-level descriptor of the tab button.
    *
    * @param runtimeId the runtime identifier of the viewsheet.
    * @param objectId the runtime identifier of the tab object.
    *
    * @return the tab descriptor.
    */
   @RequestMapping(
      value = "/api/composer/vs/tab-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public TabPropertyDialogModel getTabPropertyDialogModel(@PathVariable("objectId") String objectId,
                                                           @RemainingPath String runtimeId,
                                                           Principal principal)
      throws Exception
   {
      Viewsheet vs;
      TabVSAssembly tabAssembly;
      TabVSAssemblyInfo tabAssemblyInfo;
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.lockRead();

      try {
         vs = rvs.getViewsheet();
         tabAssembly = (TabVSAssembly) vs.getAssembly(objectId);
         tabAssemblyInfo = (TabVSAssemblyInfo) tabAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         //TODO decide what to do with exception
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

   /**
    * Sets the top-level descriptor of the specified tab button.
    *
    * @param objectId the runtime identifier of the tab object.
    * @param value the tab descriptor.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/tab-property-dialog-model/{objectId}")
   public void setTabPropertyDialogModel(@DestinationVariable("objectId") String objectId,
                                         @Payload TabPropertyDialogModel value,
                                         @LinkUri String linkUri,
                                         Principal principal,
                                         CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet viewsheet;
      TabVSAssemblyInfo tabAssemblyInfo;
      TabVSAssembly tabAssembly;
      Viewsheet vs;

      try {
         viewsheet = viewsheetService.getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
         tabAssembly = (TabVSAssembly) viewsheet.getViewsheet().getAssembly(objectId);
         tabAssemblyInfo = (TabVSAssemblyInfo) Tool.clone(tabAssembly.getVSAssemblyInfo());
         vs = viewsheet.getViewsheet();
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

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

      // @by changhongyang 2017-10-10, move tab children in addition to tab
      if(sizePositionPaneModel.getLeft() >= 0 && sizePositionPaneModel.getTop() >= 0) {
         dialogService.setContainerPosition(tabAssemblyInfo, sizePositionPaneModel,
                                            tabAssembly.getAssemblies(), vs);

         ChangedAssemblyList clist = this.placeholderService.createList(false,
            commandDispatcher, viewsheet, linkUri);
         this.placeholderService.layoutViewsheet(viewsheet, viewsheet.getID(), linkUri,
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
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSObjectTreeService vsObjectTreeService;
   private final PlaceholderService placeholderService;
   private final VSDialogService dialogService;
   private final ViewsheetService viewsheetService;
}
