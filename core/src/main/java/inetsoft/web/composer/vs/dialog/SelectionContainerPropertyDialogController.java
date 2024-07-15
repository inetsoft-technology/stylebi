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
package inetsoft.web.composer.vs.dialog;


import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.CurrentSelectionVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CurrentSelectionVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.vs.*;
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

/**
 * Controller that provides the REST endpoints for the Selection Container
 * dialog
 *
 * @since 12.3
 */
@Controller
public class SelectionContainerPropertyDialogController {
   /**
    * Creates a new instance of <tt>SelectionContainerPropertyController</tt>.
    * @param vsObjectPropertyService VSObjectPropertyService instance
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    * @param viewsheetService
    */
   @Autowired
   public SelectionContainerPropertyDialogController(
      VSObjectPropertyService vsObjectPropertyService,
      RuntimeViewsheetRef runtimeViewsheetRef,
      VSDialogService dialogService,
      ViewsheetService viewsheetService)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.dialogService = dialogService;
      this.viewsheetService = viewsheetService;
   }

   /**
    * Gets the selection container property dialog model.
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId   the selection container id
    * @return the selection container property dialog model.
    */
   @RequestMapping(
      value = "/api/composer/vs/selection-container-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public SelectionContainerPropertyDialogModel getSelectionContainerPropertyModel(@PathVariable("objectId") String objectId,
                                                                                   @RemainingPath String runtimeId,
                                                                                   Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      CurrentSelectionVSAssembly selectionContainerAssembly;
      CurrentSelectionVSAssemblyInfo selectionContainerAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         selectionContainerAssembly = (CurrentSelectionVSAssembly) vs.getAssembly(objectId);
         selectionContainerAssemblyInfo = (CurrentSelectionVSAssemblyInfo) selectionContainerAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      SelectionContainerPropertyDialogModel result = new SelectionContainerPropertyDialogModel();
      SelectionContainerGeneralPaneModel selectionContainerGeneralPaneModel = result.getSelectionContainerGeneralPaneModel();
      TitlePropPaneModel titlePropPaneModel = selectionContainerGeneralPaneModel.getTitlePropPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         selectionContainerGeneralPaneModel.getSizePositionPaneModel();
      GeneralPropPaneModel generalPropPaneModel = selectionContainerGeneralPaneModel.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPaneModel = VSAssemblyScriptPaneModel.builder();

      selectionContainerGeneralPaneModel.setShowCurrentSelection(selectionContainerAssemblyInfo.getShowCurrentSelectionValue());
      selectionContainerGeneralPaneModel.setAdhocEnabled(selectionContainerAssemblyInfo.getAdhocEnabledValue());

      titlePropPaneModel.setVisible(selectionContainerAssemblyInfo.getTitleVisibleValue());
      titlePropPaneModel.setTitle(selectionContainerAssemblyInfo.getTitleValue());

      Point pos = dialogService.getAssemblyPosition(selectionContainerAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(selectionContainerAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setTitleHeight(selectionContainerAssemblyInfo.getTitleHeightValue());
      sizePositionPaneModel.setContainer(selectionContainerAssembly.getContainer() != null);

      generalPropPaneModel.setShowEnabledGroup(true);
      generalPropPaneModel.setEnabled(selectionContainerAssemblyInfo.getEnabledValue());

      basicGeneralPaneModel.setName(selectionContainerAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setPrimary(selectionContainerAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setVisible(selectionContainerAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setObjectNames(this.vsObjectPropertyService.getObjectNames(
         vs, selectionContainerAssemblyInfo.getAbsoluteName()));

      vsAssemblyScriptPaneModel.scriptEnabled(
         selectionContainerAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(
         selectionContainerAssemblyInfo.getScript() == null ?
            "" : selectionContainerAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   /**
    * Sets the specified selection container assembly info.
    *
    * @param objectId   the selection container id
    * @param value the selection container property dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/selection-container-property-dialog-model/{objectId}")
   public void setSelectionContainerPropertyModel(@DestinationVariable("objectId") String objectId,
                                                  @Payload SelectionContainerPropertyDialogModel value,
                                                  @LinkUri String linkUri,
                                                  Principal principal,
                                                  CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet viewsheet;
      Viewsheet vs;
      CurrentSelectionVSAssembly selectionContainerAssembly;
      CurrentSelectionVSAssemblyInfo selectionContainerAssemblyInfo;

      try {
         viewsheet = viewsheetService.getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
         vs = viewsheet.getViewsheet();
         selectionContainerAssembly = (CurrentSelectionVSAssembly) viewsheet.getViewsheet()
            .getAssembly(objectId);
         selectionContainerAssemblyInfo = (CurrentSelectionVSAssemblyInfo) Tool.clone(selectionContainerAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      SelectionContainerGeneralPaneModel selectionContainerGeneralPaneModel = value.getSelectionContainerGeneralPaneModel();
      TitlePropPaneModel titlePropPaneModel = selectionContainerGeneralPaneModel.getTitlePropPaneModel();
      SizePositionPaneModel sizePositionPaneModel = selectionContainerGeneralPaneModel.getSizePositionPaneModel();
      GeneralPropPaneModel generalPropPaneModel = selectionContainerGeneralPaneModel.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel = value.getVsAssemblyScriptPaneModel();

      selectionContainerAssemblyInfo.setShowCurrentSelectionValue(selectionContainerGeneralPaneModel.isShowCurrentSelection());
      selectionContainerAssemblyInfo.setAdhocEnabledValue(selectionContainerGeneralPaneModel.isAdhocEnabled());

      selectionContainerAssemblyInfo.setTitleVisibleValue(titlePropPaneModel.isVisible());
      selectionContainerAssemblyInfo.setTitleValue(titlePropPaneModel.getTitle());

      //When moving selection container, also move  selection container children
      dialogService.setContainerPosition(selectionContainerAssemblyInfo, sizePositionPaneModel,
                                         selectionContainerAssembly.getAssemblies(), vs);
      selectionContainerAssemblyInfo.setTitleHeightValue(sizePositionPaneModel.getTitleHeight());
      //When resizing selection container, also resize selection container children
      dialogService.setContainerSize(selectionContainerAssemblyInfo, sizePositionPaneModel,
                                    selectionContainerAssembly.getAssemblies(), vs);

      selectionContainerAssemblyInfo.setEnabledValue(generalPropPaneModel.getEnabled());

      selectionContainerAssemblyInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      selectionContainerAssemblyInfo.setVisibleValue(basicGeneralPaneModel.getVisible());

      selectionContainerAssemblyInfo.setScriptEnabled(vsAssemblyScriptPaneModel.scriptEnabled());
      selectionContainerAssemblyInfo.setScript(vsAssemblyScriptPaneModel.expression());

      this.vsObjectPropertyService.editObjectProperty(
         viewsheet, selectionContainerAssemblyInfo, objectId, basicGeneralPaneModel.getName(),
         linkUri, principal, commandDispatcher);
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSDialogService dialogService;
   private final ViewsheetService viewsheetService;
}
