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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.UploadVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.UploadVSAssemblyInfo;
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
 * Controller that provides the REST endpoints for the upload property dialog.
 *
 * @since 12.3
 */
@Controller
public class UploadPropertyDialogController {
   /**
    * Creates a new instance of <tt>UploadPropertyDialogController</tt>.
    * @param vsObjectPropertyService VSObjectPropertyService instance
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    * @param viewsheetService
    */
   @Autowired
   public UploadPropertyDialogController(
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
    * Gets the top-level descriptor of the rectangle.
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId   the object id
    * @return the rectangle descriptor.
    */
   @RequestMapping(
      value = "/api/composer/vs/upload-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public UploadPropertyDialogModel getUploadPropertyDialogModel(@PathVariable("objectId") String objectId,
                                                                 @RemainingPath String runtimeId,
                                                                 Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      UploadVSAssembly uploadAssembly;
      UploadVSAssemblyInfo uploadAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         uploadAssembly = (UploadVSAssembly) vs.getAssembly(objectId);
         uploadAssemblyInfo = (UploadVSAssemblyInfo) uploadAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      UploadPropertyDialogModel result = new UploadPropertyDialogModel();
      UploadGeneralPaneModel uploadGeneralPaneModel = result.getUploadGeneralPaneModel();
      LabelPropPaneModel labelPropPaneModel =
         uploadGeneralPaneModel.getLabelPropPaneModel();
      GeneralPropPaneModel generalPropPaneModel =
         uploadGeneralPaneModel.getGeneralPropPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         uploadGeneralPaneModel.getSizePositionPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel =
         generalPropPaneModel.getBasicGeneralPaneModel();
      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPaneModel =
         VSAssemblyScriptPaneModel.builder();

      generalPropPaneModel.setShowEnabledGroup(true);
      generalPropPaneModel.setEnabled(uploadAssemblyInfo.getEnabledValue());
      generalPropPaneModel.setShowSubmitCheckbox(true);
      generalPropPaneModel.setSubmitOnChange(
         Boolean.valueOf(uploadAssemblyInfo.getSubmitOnChangeValue()));

      Point pos = dialogService.getAssemblyPosition(uploadAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(uploadAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setContainer(uploadAssembly.getContainer() != null);

      basicGeneralPaneModel.setName(uploadAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setPrimary(uploadAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setVisible(uploadAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setObjectNames(this.vsObjectPropertyService.getObjectNames(
         vs, uploadAssemblyInfo.getAbsoluteName()));

      labelPropPaneModel.setLabel(uploadAssemblyInfo.getLabelName());

      vsAssemblyScriptPaneModel.scriptEnabled(uploadAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(uploadAssemblyInfo.getScript() == null ?
                                              "" : uploadAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   /**
    * Sets the top-level descriptor of the specified rectangle.
    *
    * @param objectId   the object id
    * @param value the worksheet descriptor.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/upload-property-dialog-model/{objectId}")
   public void setUploadPropertyDialogModel(@DestinationVariable("objectId") String objectId,
                                            @Payload UploadPropertyDialogModel value,
                                            @LinkUri String linkUri,
                                            Principal principal,
                                            CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet viewsheet;
      UploadVSAssemblyInfo uploadAssemblyInfo;

      try {
         viewsheet = viewsheetService.getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
         UploadVSAssembly uploadAssembly = (UploadVSAssembly) viewsheet.getViewsheet().getAssembly(objectId);
         uploadAssemblyInfo = (UploadVSAssemblyInfo) Tool.clone(uploadAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      UploadGeneralPaneModel uploadGeneralPaneModel = value.getUploadGeneralPaneModel();
      LabelPropPaneModel labelPropPaneModel = uploadGeneralPaneModel.getLabelPropPaneModel();
      GeneralPropPaneModel generalPropPaneModel = uploadGeneralPaneModel.getGeneralPropPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         uploadGeneralPaneModel.getSizePositionPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel = value.getVsAssemblyScriptPaneModel();

      uploadAssemblyInfo.setEnabledValue(generalPropPaneModel.getEnabled());
      uploadAssemblyInfo.setSubmitOnChangeValue(generalPropPaneModel.isSubmitOnChange() + "");

      dialogService.setAssemblySize(uploadAssemblyInfo, sizePositionPaneModel);
      dialogService.setAssemblyPosition(uploadAssemblyInfo, sizePositionPaneModel);

      uploadAssemblyInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      uploadAssemblyInfo.setVisibleValue(basicGeneralPaneModel.getVisible());

      uploadAssemblyInfo.setLabelName(labelPropPaneModel.getLabel());

      uploadAssemblyInfo.setScriptEnabled(vsAssemblyScriptPaneModel.scriptEnabled());
      uploadAssemblyInfo.setScript(vsAssemblyScriptPaneModel.expression());

      this.vsObjectPropertyService.editObjectProperty(
         viewsheet, uploadAssemblyInfo, objectId, basicGeneralPaneModel.getName(), linkUri,
         principal, commandDispatcher);
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSDialogService dialogService;
   private final ViewsheetService viewsheetService;
}