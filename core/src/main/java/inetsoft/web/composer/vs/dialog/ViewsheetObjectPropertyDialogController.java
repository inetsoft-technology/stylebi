/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ViewsheetVSAssemblyInfo;
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
 * Controller that provides the REST endpoints for the Viewsheet property dialog.
 *
 * @since 12.3
 */
@Controller
public class ViewsheetObjectPropertyDialogController {
   /**
    * Creates a new instance of <tt>ViewsheetObjectPropertyDialogController</tt>.
    * @param vsObjectPropertyService VSObjectPropertyService instance
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    */
   @Autowired
   public ViewsheetObjectPropertyDialogController(ViewsheetService viewsheetService,
                                                  VSObjectPropertyService vsObjectPropertyService,
                                                  RuntimeViewsheetRef runtimeViewsheetRef,
                                                  VSDialogService dialogService)
   {
      this.viewsheetService = viewsheetService;
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.dialogService = dialogService;
   }

   /**
    * Gets the viewsheet property dialog model.
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId   the viewsheet object name.
    * @return the viewsheet object property dialog model.
    */
   @RequestMapping(
      value = "/api/composer/vs/viewsheet-object-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public ViewsheetObjectPropertyDialogModel getViewsheetPropertyModel(@PathVariable("objectId") String objectId,
                                                                       @RemainingPath String runtimeId,
                                                                       Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();
      Viewsheet embeddedVs = (Viewsheet) vs.getAssembly(objectId);
      ViewsheetVSAssemblyInfo info = (ViewsheetVSAssemblyInfo) embeddedVs.getVSAssemblyInfo();

      ViewsheetObjectPropertyDialogModel.Builder model =
         ViewsheetObjectPropertyDialogModel.builder();

      GeneralPropPaneModel generalPropPaneModel = new GeneralPropPaneModel();
      SizePositionPaneModel sizePositionPaneModel = new SizePositionPaneModel();

      generalPropPaneModel.setShowEnabledGroup(false);
      generalPropPaneModel.setShowSubmitCheckbox(false);

      BasicGeneralPaneModel basicGeneralPaneModel = new BasicGeneralPaneModel();
      basicGeneralPaneModel.setName(info.getAbsoluteName());
      basicGeneralPaneModel.setPrimary(info.isPrimary());
      basicGeneralPaneModel.setVisible(info.getVisibleValue());
      basicGeneralPaneModel.setObjectNames(this.vsObjectPropertyService.getObjectNames(
         vs, info.getAbsoluteName()));

      generalPropPaneModel.setBasicGeneralPaneModel(basicGeneralPaneModel);
      model.generalPropPaneModel(generalPropPaneModel);

      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPaneModel =
         VSAssemblyScriptPaneModel.builder();

      vsAssemblyScriptPaneModel.scriptEnabled(info.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(info.getScript() == null ? "" : info.getScript());
      model.vsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      Point pos = dialogService.getAssemblyPosition(info, vs);
      Dimension size = dialogService.getAssemblySize(info, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setContainer(embeddedVs.getContainer() != null);
      model.sizePositionPaneModel(sizePositionPaneModel);

      return model.build();
   }

   /**
    * Sets the specified viewsheet assembly info.
    *
    * @param objectId the viewsheet object id
    * @param model    the viewsheet property dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/viewsheet-object-property-dialog-model/{objectId}")
   public void setViewsheetPropertyModel(@DestinationVariable("objectId") String objectId,
                                          @Payload ViewsheetObjectPropertyDialogModel model,
                                          @LinkUri String linkUri,
                                          Principal principal,
                                          CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService
         .getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet vs = rvs.getViewsheet();
      Viewsheet embeddedVs = (Viewsheet) vs.getAssembly(objectId);
      ViewsheetVSAssemblyInfo info = (ViewsheetVSAssemblyInfo) embeddedVs.getVSAssemblyInfo();

      GeneralPropPaneModel generalPropPaneModel = model.generalPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      SizePositionPaneModel sizePositionPaneModel = model.sizePositionPaneModel();

      info.setVisibleValue(basicGeneralPaneModel.getVisible());
      info.setPrimary(basicGeneralPaneModel.isPrimary());

      VSAssemblyScriptPaneModel script = model.vsAssemblyScriptPaneModel();
      info.setScriptEnabled(script.scriptEnabled());
      info.setScript(script.expression());

      dialogService.setAssemblySize(info, sizePositionPaneModel);
      dialogService.setAssemblyPosition(info, sizePositionPaneModel);

      this.vsObjectPropertyService.editObjectProperty(
         rvs, info, objectId, basicGeneralPaneModel.getName(), linkUri, principal,
         commandDispatcher);
   }

   private final ViewsheetService viewsheetService;
   private final VSObjectPropertyService vsObjectPropertyService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSDialogService dialogService;
}