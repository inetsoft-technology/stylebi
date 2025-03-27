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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ViewsheetVSAssemblyInfo;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;

@Service
@ClusterProxy
public class ViewsheetObjectPropertyDialogService {

   public ViewsheetObjectPropertyDialogService(ViewsheetService viewsheetService,
                                               VSObjectPropertyService vsObjectPropertyService,
                                               VSDialogService dialogService)
   {
      this.viewsheetService = viewsheetService;
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.dialogService = dialogService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ViewsheetObjectPropertyDialogModel getViewsheetPropertyModel(@ClusterProxyKey String runtimeId,
                                                                       String objectId, Principal principal)
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

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setViewsheetPropertyModel(@ClusterProxyKey String runtimeId, String objectId,
                                         ViewsheetObjectPropertyDialogModel model, String linkUri,
                                         Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
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

      return null;
   }


   private final ViewsheetService viewsheetService;
   private final VSObjectPropertyService vsObjectPropertyService;
   private final VSDialogService dialogService;
}
