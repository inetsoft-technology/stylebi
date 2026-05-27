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
import inetsoft.report.internal.Util;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.OvalVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;

@Service
@ClusterProxy
public class OvalPropertyDialogService {

   public OvalPropertyDialogService(VSObjectPropertyService vsObjectPropertyService,
                                    VSDialogService dialogService,
                                    ViewsheetService viewsheetService)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.dialogService = dialogService;
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public OvalPropertyDialogModel getOvalPropertyDialogModel(@ClusterProxyKey String runtimeId,
                                                             String objectId, Principal principal)
      throws  Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      OvalVSAssembly ovalAssembly;
      OvalVSAssemblyInfo ovalAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         ovalAssembly = (OvalVSAssembly) vs.getAssembly(objectId);
         ovalAssemblyInfo = (OvalVSAssemblyInfo) ovalAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         throw e;
      }

      OvalPropertyDialogModel result = new OvalPropertyDialogModel();
      ShapeGeneralPaneModel shapeGeneralPaneModel = result.getShapeGeneralPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel =
         shapeGeneralPaneModel.getBasicGeneralPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         shapeGeneralPaneModel.getSizePositionPaneModel();
      sizePositionPaneModel.setLocked(ovalAssemblyInfo.getLocked());
      OvalPropertyPaneModel ovalPropertyPaneModel = result.getOvalPropertyPaneModel();
      LinePropPaneModel linePropPaneModel = ovalPropertyPaneModel.getLinePropPaneModel();
      FillPropPaneModel fillPropPaneModel = ovalPropertyPaneModel.getFillPropPaneModel();
      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPaneModel =
         VSAssemblyScriptPaneModel.builder();

      basicGeneralPaneModel.setName(ovalAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setVisible(ovalAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setPrimary(ovalAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setShowShadowCheckbox(true);
      basicGeneralPaneModel.setShadow(ovalAssemblyInfo.getShadowValue());
      basicGeneralPaneModel.setObjectNames(this.vsObjectPropertyService.getObjectNames(
         vs, ovalAssemblyInfo.getAbsoluteName()));

      Point pos = dialogService.getAssemblyPosition(ovalAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(ovalAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setContainer(ovalAssembly.getContainer() != null);

      linePropPaneModel.setStyle(Util.getLineStyleName(ovalAssemblyInfo.getLineStyleValue()));
      VSCompositeFormat format = ovalAssemblyInfo.getFormat();
      String colorString = format.getForegroundValue();

      if(colorString != null && (colorString.startsWith("=") ||
         colorString.startsWith("$")))
      {
         linePropPaneModel.setColor(colorString);
         linePropPaneModel.setColorValue(null);
      }
      else {
         colorString = VSObjectPropertyService.getColorHexString(colorString);
         linePropPaneModel.setColorValue(colorString);
         linePropPaneModel.setColor("Static");
      }

      fillPropPaneModel.setAlpha(format.getAlphaValue());
      colorString = format.getBackgroundValue();

      if(colorString != null && (colorString.startsWith("=") ||
         colorString.startsWith("$")))
      {
         fillPropPaneModel.setColor(colorString);
         fillPropPaneModel.setColorValue(null);
      }
      else {
         colorString = VSObjectPropertyService.getColorHexString(colorString);
         fillPropPaneModel.setColorValue(colorString);
         fillPropPaneModel.setColor("Static");
      }

      GradientColor gradientColor = format.getGradientColorValue();

      if(gradientColor != null) {
         fillPropPaneModel.setGradientColor(gradientColor);
      }

      vsAssemblyScriptPaneModel.scriptEnabled(ovalAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(ovalAssemblyInfo.getScript() == null ?
                                              "" : ovalAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setOvalPropertyDialogModel(@ClusterProxyKey String runtimeId, String objectId,
                                          OvalPropertyDialogModel value, String linkUri,
                                          Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      OvalVSAssemblyInfo ovalAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         OvalVSAssembly ovalAssembly = (OvalVSAssembly) vs.getAssembly(objectId);
         ovalAssemblyInfo = (OvalVSAssemblyInfo) Tool.clone(ovalAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         throw e;
      }

      ShapeGeneralPaneModel shapeGeneralPaneModel = value.getShapeGeneralPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = shapeGeneralPaneModel.getBasicGeneralPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         shapeGeneralPaneModel.getSizePositionPaneModel();
      OvalPropertyPaneModel ovalPropertyPaneModel = value.getOvalPropertyPaneModel();
      LinePropPaneModel linePropPaneModel = ovalPropertyPaneModel.getLinePropPaneModel();
      FillPropPaneModel fillPropPaneModel = ovalPropertyPaneModel.getFillPropPaneModel();
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel = value.getVsAssemblyScriptPaneModel();

      ovalAssemblyInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      ovalAssemblyInfo.setVisibleValue(basicGeneralPaneModel.getVisible());
      ovalAssemblyInfo.setShadowValue(basicGeneralPaneModel.isShadow());

      dialogService.setAssemblySize(ovalAssemblyInfo, sizePositionPaneModel);
      dialogService.setAssemblyPosition(ovalAssemblyInfo, sizePositionPaneModel);

      ovalAssemblyInfo.setLineStyleValue(Util.getStyleConstantsFromString(linePropPaneModel.getStyle()));
      VSFormat format = ovalAssemblyInfo.getFormat().getUserDefinedFormat();
      String colorString = linePropPaneModel.getColor();

      if("Static".equals(colorString)) {
         format.setForegroundValue(Integer.decode(linePropPaneModel.getColorValue()) + "");
      }
      else {
         format.setForegroundValue(colorString);
      }

      format.setAlphaValue(fillPropPaneModel.getAlpha());
      colorString = fillPropPaneModel.getColor();

      if("Static".equals(colorString)) {
         String str = fillPropPaneModel.getColorValue();

         if(str == null || str.isEmpty()) {
            format.setBackgroundValue("");
         }
         else {
            format.setBackgroundValue(Integer.decode(str) + "");
         }
      }
      else {
         format.setBackgroundValue(colorString);
      }

      format.setGradientColorValue(fillPropPaneModel.getGradientColor());
      ovalAssemblyInfo.setScriptEnabled(vsAssemblyScriptPaneModel.scriptEnabled());
      ovalAssemblyInfo.setScript(vsAssemblyScriptPaneModel.expression());

      this.vsObjectPropertyService.editObjectProperty(
         rvs, ovalAssemblyInfo, objectId, basicGeneralPaneModel.getName(), linkUri, principal,
         commandDispatcher);

      return null;
   }


   private final VSObjectPropertyService vsObjectPropertyService;
   private final VSDialogService dialogService;
   private final ViewsheetService viewsheetService;
}
