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
import inetsoft.report.internal.Util;
import inetsoft.uql.viewsheet.LineVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.LineVSAssemblyInfo;
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
 * Controller that provides the REST endpoints for the line property dialog.
 *
 * @since 12.3
 */
@Controller
public class LinePropertyDialogController {

   /**
    * Creates a new instance of <tt>LinePropertyDialogController</tt>.
    * @param vsObjectPropertyService VSObjectPropertyService instance
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    * @param viewsheetService
    */
   @Autowired
   public LinePropertyDialogController(
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
    * Gets the top-level descriptor of the line.
    *
    * @param runtimeId the runtime identifier of the viewsheet.
    * @param objectId the runtime identifier of the line object.
    *
    * @return the line descriptor.
    */
   @GetMapping(value = "/api/composer/vs/line-property-dialog-model/{objectId}/**")
   @ResponseBody
   public LinePropertyDialogModel getLinePropertyDialogModel(@PathVariable("objectId") String objectId,
                                                             @RemainingPath String runtimeId,
                                                             Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();
      LineVSAssembly lineAssembly = (LineVSAssembly) vs.getAssembly(objectId);
      LineVSAssemblyInfo lineAssemblyInfo = (LineVSAssemblyInfo) lineAssembly.getVSAssemblyInfo();
      LinePropertyDialogModel result = new LinePropertyDialogModel();
      ShapeGeneralPaneModel shapeGeneralPaneModel = result.getShapeGeneralPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel =
         shapeGeneralPaneModel.getBasicGeneralPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         shapeGeneralPaneModel.getSizePositionPaneModel();
      sizePositionPaneModel.setLocked(lineAssemblyInfo.getLocked());
      LinePropertyPaneModel linePropertyPaneModel = result.getLinePropertyPaneModel();
      LinePropPaneModel linePropPaneModel = linePropertyPaneModel.getLinePropPaneModel();
      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPaneModel =
         VSAssemblyScriptPaneModel.builder();

      basicGeneralPaneModel.setName(lineAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setVisible(lineAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setPrimary(lineAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setShowShadowCheckbox(false);
      basicGeneralPaneModel.setShadow(lineAssemblyInfo.getShadowValue());
      basicGeneralPaneModel.setObjectNames(this.vsObjectPropertyService.getObjectNames(
         vs, lineAssemblyInfo.getAbsoluteName()));

      Point pos = dialogService.getAssemblyPosition(lineAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(lineAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setContainer(lineAssembly.getContainer() != null);

      linePropertyPaneModel.setBegin(lineAssemblyInfo.getBeginArrowStyleValue());
      linePropertyPaneModel.setEnd(lineAssemblyInfo.getEndArrowStyleValue());

      linePropPaneModel.setStyle(
         Util.getLineStyleName(lineAssemblyInfo.getLineStyleValue()));
      String colorString = lineAssemblyInfo.getFormat().getForegroundValue();

      if(colorString != null && (colorString.startsWith("=") ||
         colorString.startsWith("$")))
      {
         linePropPaneModel.setColorValue(null);
         linePropPaneModel.setColor(colorString);
      }
      else {
         colorString = VSObjectPropertyService.getColorHexString(colorString);
         linePropPaneModel.setColorValue(colorString);
         linePropPaneModel.setColor("Static");
      }

      vsAssemblyScriptPaneModel.scriptEnabled(lineAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(lineAssemblyInfo.getScript() == null ?
                                              "" : lineAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   /**
    * Sets the top-level descriptor of the specified line.
    *
    * @param objectId the runtime identifier of the line object.
    * @param value the line descriptor.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/line-property-dialog-model/{objectId}")
   public void setLinePropertyDialogModel(@DestinationVariable("objectId") String objectId,
                                          @Payload LinePropertyDialogModel value,
                                          @LinkUri String linkUri,
                                          Principal principal,
                                          CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet vs = rvs.getViewsheet();
      LineVSAssembly lineAssembly = (LineVSAssembly) vs.getAssembly(objectId);
      LineVSAssemblyInfo lineAssemblyInfo = (LineVSAssemblyInfo) Tool.clone(lineAssembly.getVSAssemblyInfo());

      ShapeGeneralPaneModel shapeGeneralPaneModel = value.getShapeGeneralPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = shapeGeneralPaneModel.getBasicGeneralPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         shapeGeneralPaneModel.getSizePositionPaneModel();
      LinePropertyPaneModel linePropertyPaneModel = value.getLinePropertyPaneModel();
      LinePropPaneModel linePropPaneModel = linePropertyPaneModel.getLinePropPaneModel();
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel = value.getVsAssemblyScriptPaneModel();

      lineAssemblyInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      lineAssemblyInfo.setVisibleValue(basicGeneralPaneModel.getVisible());
      lineAssemblyInfo.setShadowValue(basicGeneralPaneModel.isShadow());

      dialogService.setAssemblyPosition(lineAssemblyInfo, sizePositionPaneModel);
      Dimension osize = dialogService.getAssemblySize(lineAssemblyInfo, vs);

      if(sizePositionPaneModel.getWidth() > 0 && sizePositionPaneModel.getHeight() > 0 &&
         (sizePositionPaneModel.getWidth() != osize.width ||
         sizePositionPaneModel.getHeight() != osize.height))
      {
         Dimension size = new Dimension(sizePositionPaneModel.getWidth(),
                                        sizePositionPaneModel.getHeight());
         Point startPoint = lineAssemblyInfo.getStartPos();
         Point endPoint = lineAssemblyInfo.getEndPos();

         // if size is width/height to 1, it should be a vertical/horizontal line instead
         // of having 1 pixel difference, since the line itself should take up 1 pixel size
         // in that case. (54290)
         int relX = size.width == 1 ? 0: size.width;
         int relY = size.height == 1 && relX > 0 ? 0 : size.height;

         // @by changhongyang 2017-10-10, calculate how the line stretches when resized through the
         // property dialog
         // end at right-upper position of start, set end x and start y to match size
         if(endPoint.x >= startPoint.x && endPoint.y < startPoint.y) {
            lineAssemblyInfo.setEndPos(new Point(relX, endPoint.y));
            lineAssemblyInfo.setStartPos(new Point(startPoint.x, relY));
         }
         // end at right-lower position of start, set end x/y to match size
         else if(endPoint.x > startPoint.x && endPoint.y >= startPoint.y) {
            lineAssemblyInfo.setEndPos(new Point(relX, relY));
         }
         // end at left-lower position of start, set end y and start x to match size
         else if(endPoint.x <= startPoint.x && endPoint.y > startPoint.y) {
            lineAssemblyInfo.setEndPos(new Point(endPoint.x, relY));
            lineAssemblyInfo.setStartPos(new Point(relX, startPoint.y));
         }
         // end at left-upper position of start, set start x/y to match size
         else if(endPoint.x < startPoint.x && endPoint.y <= startPoint.y) {
            lineAssemblyInfo.setStartPos(new Point(relX, relY));
         }
         else {
            lineAssemblyInfo.setEndPos(new Point(0, 0));
            lineAssemblyInfo.setStartPos(new Point(relX, relY));
         }

         if(lineAssemblyInfo.getLayoutSize() != null) {
            lineAssemblyInfo.setLayoutSize(size);
         }

         lineAssemblyInfo.setPixelSize(size);
      }

      lineAssemblyInfo.setBeginArrowStyleValue(linePropertyPaneModel.getBegin());
      lineAssemblyInfo.setEndArrowStyleValue(linePropertyPaneModel.getEnd());

      lineAssemblyInfo.setLineStyleValue(Util.getStyleConstantsFromString(linePropPaneModel.getStyle()));
      String colorString = linePropPaneModel.getColor();

      if("Static".equals(colorString)) {
         lineAssemblyInfo.getFormat().getUserDefinedFormat()
            .setForegroundValue(Integer.decode(linePropPaneModel.getColorValue()) + "");
      }
      else {
         lineAssemblyInfo.getFormat().getUserDefinedFormat().setForegroundValue(colorString);
      }

      lineAssemblyInfo.setScriptEnabled(vsAssemblyScriptPaneModel.scriptEnabled());
      lineAssemblyInfo.setScript(vsAssemblyScriptPaneModel.expression());

      this.vsObjectPropertyService.editObjectProperty(
         rvs, lineAssemblyInfo, objectId, basicGeneralPaneModel.getName(), linkUri, principal,
         commandDispatcher);
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSDialogService dialogService;
   private final ViewsheetService viewsheetService;
}
