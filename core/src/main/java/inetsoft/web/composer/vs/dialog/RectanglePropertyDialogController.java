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
import inetsoft.report.internal.Util;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.RectangleVSAssemblyInfo;
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
 * Controller that provides the REST endpoints for the rect property dialog.
 *
 * @since 12.3
 */
@Controller
public class RectanglePropertyDialogController {
   /**
    * Creates a new instance of <tt>LinePropertyDialogController</tt>.
    * @param vsObjectPropertyService VSObjectPropertyService instance
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    * @param viewsheetService
    */
   @Autowired
   public RectanglePropertyDialogController(
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
    * @param runtimeId the runtime identifier of the viewsheet.
    * @param objectId the runtime identifier of the rectangle object.
    *
    * @return the rectangle descriptor.
    */
   @RequestMapping(
      value = "/api/composer/vs/rectangle-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public RectanglePropertyDialogModel getRectanglePropertyDialogModel(@PathVariable("objectId") String objectId,
                                                                       @RemainingPath String runtimeId,
                                                                       Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      RectangleVSAssembly rectangleAssembly;
      RectangleVSAssemblyInfo rectangleAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         rectangleAssembly = (RectangleVSAssembly) vs.getAssembly(objectId);
         rectangleAssemblyInfo = (RectangleVSAssemblyInfo) rectangleAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      RectanglePropertyDialogModel result = new RectanglePropertyDialogModel();
      ShapeGeneralPaneModel shapeGeneralPaneModel = result.getShapeGeneralPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = shapeGeneralPaneModel.getBasicGeneralPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         shapeGeneralPaneModel.getSizePositionPaneModel();
      sizePositionPaneModel.setLocked(rectangleAssemblyInfo.getLocked());
      RectanglePropertyPaneModel rectanglePropertyPaneModel = result.getRectanglePropertyPaneModel();
      LinePropPaneModel linePropPaneModel = rectanglePropertyPaneModel.getLinePropPaneModel();
      FillPropPaneModel fillPropPaneModel = rectanglePropertyPaneModel.getFillPropPaneModel();
      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPaneModel = VSAssemblyScriptPaneModel.builder();

      basicGeneralPaneModel.setName(rectangleAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setVisible(rectangleAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setPrimary(rectangleAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setShowShadowCheckbox(true);
      basicGeneralPaneModel.setShadow(rectangleAssemblyInfo.getShadowValue());
      basicGeneralPaneModel.setObjectNames(this.vsObjectPropertyService.getObjectNames(vs, rectangleAssemblyInfo.getAbsoluteName()));

      Point pos = dialogService.getAssemblyPosition(rectangleAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(rectangleAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setContainer(rectangleAssembly.getContainer() != null);

      linePropPaneModel.setStyle(Util.getLineStyleName(rectangleAssemblyInfo.getLineStyleValue()));
      VSCompositeFormat format = rectangleAssemblyInfo.getFormat();
      rectanglePropertyPaneModel.setRadius(format.getRoundCorner());

      String colorString = format.getForegroundValue();

      if(colorString != null && (colorString.startsWith("=") || colorString.startsWith("$"))) {
         linePropPaneModel.setColor(colorString);
         linePropPaneModel.setColorValue(null);
      }
      else {
         try {
            colorString = VSObjectPropertyService.getColorHexString(colorString);
            linePropPaneModel.setColorValue(colorString);
            linePropPaneModel.setColor("Static");
         }
         catch(NumberFormatException ex) {
            // invalid value, must be entered as expression
            linePropPaneModel.setColor("=" + colorString);
         }
      }

      fillPropPaneModel.setAlpha(format.getAlphaValue());
      colorString = format.getBackgroundValue();

      if(colorString != null && (colorString.startsWith("=") || colorString.startsWith("$"))) {
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

      vsAssemblyScriptPaneModel.scriptEnabled(rectangleAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(rectangleAssemblyInfo.getScript() == null ?
                                              "" : rectangleAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   /**
    * Sets the top-level descriptor of the specified rectangle.
    *
    * @param objectId the runtime identifier of the rectangle object.
    * @param value the rectangle descriptor.
    *
    */
   @Undoable
   @LoadingMask
   @MessageMapping(value = "/composer/vs/rectangle-property-dialog-model/{objectId}")
   public void setRectanglePropertyDialogModel(@DestinationVariable("objectId") String objectId,
                                               @Payload RectanglePropertyDialogModel value,
                                               @LinkUri String linkUri,
                                               Principal principal,
                                               CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      RectangleVSAssemblyInfo rectangleAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
         vs = rvs.getViewsheet();
         RectangleVSAssembly rectangleAssembly = (RectangleVSAssembly) vs.getAssembly(objectId);
         rectangleAssemblyInfo = (RectangleVSAssemblyInfo) Tool.clone(rectangleAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      ShapeGeneralPaneModel shapeGeneralPaneModel = value.getShapeGeneralPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = shapeGeneralPaneModel.getBasicGeneralPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         shapeGeneralPaneModel.getSizePositionPaneModel();
      RectanglePropertyPaneModel rectanglePropertyPaneModel = value.getRectanglePropertyPaneModel();
      LinePropPaneModel linePropPaneModel = rectanglePropertyPaneModel.getLinePropPaneModel();
      FillPropPaneModel fillPropPaneModel = rectanglePropertyPaneModel.getFillPropPaneModel();
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel = value.getVsAssemblyScriptPaneModel();

      rectangleAssemblyInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      rectangleAssemblyInfo.setVisibleValue(basicGeneralPaneModel.getVisible());
      rectangleAssemblyInfo.setShadowValue(basicGeneralPaneModel.isShadow());

      dialogService.setAssemblySize(rectangleAssemblyInfo, sizePositionPaneModel);
      dialogService.setAssemblyPosition(rectangleAssemblyInfo, sizePositionPaneModel);
      rectangleAssemblyInfo.setLineStyleValue(Util.getStyleConstantsFromString(linePropPaneModel.getStyle()));
      VSFormat format = rectangleAssemblyInfo.getFormat().getUserDefinedFormat();
      format.setRoundCornerValue(rectanglePropertyPaneModel.getRadius());
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

      if(fillPropPaneModel.getGradientColor() != null &&
         fillPropPaneModel.getGradientColor().isApply())
      {
         format.setBackgroundValue("", false);
      }

      format.setGradientColorValue(fillPropPaneModel.getGradientColor());
      rectangleAssemblyInfo.setScriptEnabled(vsAssemblyScriptPaneModel.scriptEnabled());
      rectangleAssemblyInfo.setScript(vsAssemblyScriptPaneModel.expression());

      this.vsObjectPropertyService.editObjectProperty(
         rvs, rectangleAssemblyInfo, objectId, basicGeneralPaneModel.getName(), linkUri, principal,
         commandDispatcher);
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSDialogService dialogService;
   private final ViewsheetService viewsheetService;
}
