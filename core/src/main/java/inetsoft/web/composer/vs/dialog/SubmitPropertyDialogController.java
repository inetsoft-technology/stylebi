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
import inetsoft.uql.viewsheet.SubmitVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.SubmitVSAssemblyInfo;
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
 * Controller that provides the REST endpoints for the submit property dialog.
 *
 * @since 12.3
 */
@Controller
public class SubmitPropertyDialogController {
   /**
    * Creates a new instance of <tt>SubmitPropertyDialogController</tt>.
    * @param vsObjectPropertyService VSObjectProperty service
    * @param runtimeViewsheetRef     RuntimeViewsheetRef service
    * @param viewsheetService
    */
   @Autowired
   public SubmitPropertyDialogController(
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
    * Gets the top-level descriptor of the submit button.
    *
    * @param runtimeId the runtime identifier of the viewsheet.
    * @param objectId the runtime identifier of the submit object.
    *
    * @return the submit descriptor.
    */
   @RequestMapping(
      value = "/api/composer/vs/submit-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public SubmitPropertyDialogModel getSubmitPropertyDialogModel(@PathVariable("objectId") String objectId,
                                                                 @RemainingPath String runtimeId,
                                                                 Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      SubmitVSAssembly submitAssembly;
      SubmitVSAssemblyInfo submitAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         submitAssembly = (SubmitVSAssembly) vs.getAssembly(objectId);
         submitAssemblyInfo = (SubmitVSAssemblyInfo) submitAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      SubmitPropertyDialogModel result = new SubmitPropertyDialogModel();
      SubmitGeneralPaneModel submitGeneralPaneModel = result.getSubmitGeneralPaneModel();
      LabelPropPaneModel labelPropPaneModel = submitGeneralPaneModel.getLabelPropPaneModel();
      GeneralPropPaneModel generalPropPaneModel = submitGeneralPaneModel.getGeneralPropPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         submitGeneralPaneModel.getSizePositionPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();

      ClickableScriptPaneModel.Builder clickableScriptPaneModel =
         ClickableScriptPaneModel.builder();

      generalPropPaneModel.setShowEnabledGroup(true);
      generalPropPaneModel.setEnabled(submitAssemblyInfo.getEnabledValue());

      Point pos = dialogService.getAssemblyPosition(submitAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(submitAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setContainer(submitAssembly.getContainer() != null);

      basicGeneralPaneModel.setName(submitAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setRefresh(submitAssemblyInfo.isRefresh());
      basicGeneralPaneModel.setPrimary(submitAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setVisible(submitAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setObjectNames(
         this.vsObjectPropertyService.getObjectNames(vs, submitAssemblyInfo.getAbsoluteName()));

      labelPropPaneModel.setLabel(submitAssemblyInfo.getLabelName());

      String script = submitAssemblyInfo.getScript() == null ? "" : submitAssemblyInfo.getScript();
      String onClick = submitAssemblyInfo.getOnClick() == null ? "" :submitAssemblyInfo.getOnClick();
      clickableScriptPaneModel.scriptEnabled(submitAssemblyInfo.isScriptEnabled());
      clickableScriptPaneModel.scriptExpression(script);
      clickableScriptPaneModel.onClickExpression(onClick);
      result.setClickableScriptPaneModel(clickableScriptPaneModel.build());

      return result;
   }

   /**
    * Sets the top-level descriptor of the specified submit button.
    *
    * @param objectId the runtime identifier of the submit object.
    * @param value the submit descriptor.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/submit-property-dialog-model/{objectId}")
   public void setSubmitPropertyDialogModel(@DestinationVariable("objectId") String objectId,
                                            @Payload SubmitPropertyDialogModel value,
                                            @LinkUri String linkUri,
                                            Principal principal,
                                            CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet viewsheet;
      SubmitVSAssemblyInfo submitAssemblyInfo;

      try {
         viewsheet = viewsheetService.getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
         SubmitVSAssembly submitAssembly = (SubmitVSAssembly) viewsheet.getViewsheet().getAssembly(objectId);
         submitAssemblyInfo = (SubmitVSAssemblyInfo) Tool.clone(submitAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      SubmitGeneralPaneModel submitGeneralPaneModel = value.getSubmitGeneralPaneModel();
      LabelPropPaneModel labelPropPaneModel = submitGeneralPaneModel.getLabelPropPaneModel();
      GeneralPropPaneModel generalPropPaneModel = submitGeneralPaneModel.getGeneralPropPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         submitGeneralPaneModel.getSizePositionPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      ClickableScriptPaneModel clickableScriptPaneModel = value.getClickableScriptPaneModel();

      submitAssemblyInfo.setEnabledValue(generalPropPaneModel.getEnabled());

      dialogService.setAssemblySize(submitAssemblyInfo, sizePositionPaneModel);
      dialogService.setAssemblyPosition(submitAssemblyInfo, sizePositionPaneModel);

      submitAssemblyInfo.setRefreshValue(basicGeneralPaneModel.isRefresh() + "");
      submitAssemblyInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      submitAssemblyInfo.setVisibleValue(basicGeneralPaneModel.getVisible());

      submitAssemblyInfo.setLabelName(labelPropPaneModel.getLabel());

      submitAssemblyInfo.setScriptEnabled(clickableScriptPaneModel.scriptEnabled());

      submitAssemblyInfo.setOnClick(clickableScriptPaneModel.onClickExpression() == null ? "" : clickableScriptPaneModel.onClickExpression());
      submitAssemblyInfo.setScript(clickableScriptPaneModel.scriptExpression() == null ? "" : clickableScriptPaneModel.scriptExpression());

      this.vsObjectPropertyService.editObjectProperty(
         viewsheet, submitAssemblyInfo, objectId, basicGeneralPaneModel.getName(), linkUri,
         principal, commandDispatcher);
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSDialogService dialogService;
   private final ViewsheetService viewsheetService;
}