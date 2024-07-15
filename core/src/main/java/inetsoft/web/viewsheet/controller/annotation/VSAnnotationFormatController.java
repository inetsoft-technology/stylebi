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
package inetsoft.web.viewsheet.controller.annotation;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.Util;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.OpenAnnotationFormatDialogCommand;
import inetsoft.web.viewsheet.event.annotation.OpenAnnotationFormatDialogEvent;
import inetsoft.web.viewsheet.event.annotation.UpdateAnnotationFormatEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.dialog.AnnotationFormatDialogModel;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;

@Controller
public class VSAnnotationFormatController {
   @Autowired
   public VSAnnotationFormatController(VSObjectService service,
                                       VSAnnotationService annotationService,
                                       RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.service = service;
      this.annotationService = annotationService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   /**
    * This method creates an AnnotationFormatDialogModel and sends a command with the
    * format model and name of the annotation
    *
    * @param event      The event with the name of the annotation to format
    * @param principal  The current user
    * @param dispatcher The command dispatcher
    */
   @MessageMapping("/annotation/open-format-dialog")
   public void openFormatDialog(@Payload OpenAnnotationFormatDialogEvent event,
                                Principal principal,
                                CommandDispatcher dispatcher) throws Exception
   {
      // get annotation and related assemblies
      final RuntimeViewsheet rvs =
         service.getRuntimeViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      final Viewsheet viewsheet = rvs.getViewsheet();
      final String name = event.getName();
      final AnnotationVSAssembly assembly = (AnnotationVSAssembly) viewsheet.getAssembly(name);
      final AnnotationVSAssemblyInfo info = (AnnotationVSAssemblyInfo) assembly.getVSAssemblyInfo();
      final AnnotationRectangleVSAssembly rectangle =
         (AnnotationRectangleVSAssembly) viewsheet.getAssembly(info.getRectangle());
      final AnnotationLineVSAssembly line =
         (AnnotationLineVSAssembly) viewsheet.getAssembly(info.getLine());

      // create model from line and rectangle
      final AnnotationFormatDialogModel formatDialogModel =
         createFormatDialogModel(rectangle, line);

      boolean forChart =
        AnnotationVSUtil.getBaseAssembly(viewsheet, name) instanceof ChartVSAssembly;

      final OpenAnnotationFormatDialogCommand command =
         OpenAnnotationFormatDialogCommand.builder()
                                          .formatDialogModel(formatDialogModel)
                                          .assemblyName(name)
                                          .forChart(forChart)
                                          .annotationType(info.getType())
                                          .build();
      dispatcher.sendCommand(command);
   }

   /**
    * This method takes the {@link AnnotationFormatDialogModel} and updates its format
    * in the runtime viewsheet, then refreshes the annotation
    *
    * @param event      The event containing the name of the annotation to format and the
    *                   new format modal to apply
    * @param principal  The current user
    * @param dispatcher The command dispatcher
    */
   @Undoable
   @MessageMapping("/annotation/update-format")
   public void updateFormat(@Payload UpdateAnnotationFormatEvent event,
                            Principal principal,
                            @LinkUri String linkUri,
                            CommandDispatcher dispatcher) throws Exception
   {
      // get annotation and related assemblies
      final RuntimeViewsheet rvs =
         service.getRuntimeViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      final Viewsheet viewsheet = rvs.getViewsheet();
      final String name = event.getName();
      final AnnotationFormatDialogModel model = event.getFormatModel();
      final AnnotationVSAssembly annotation =
         (AnnotationVSAssembly) viewsheet.getAssembly(name);
      final AnnotationVSAssemblyInfo info =
         (AnnotationVSAssemblyInfo) annotation.getVSAssemblyInfo();

      // get parent
      final String parent = AnnotationVSUtil.getAnnotationParentName(viewsheet, name);
      final VSAssembly parentAssembly = (VSAssembly) viewsheet.getAssembly(parent);

      // get rectangle
      final AnnotationRectangleVSAssembly rectangle =
         (AnnotationRectangleVSAssembly) viewsheet.getAssembly(info.getRectangle());

      // get line
      final AnnotationLineVSAssembly line =
         (AnnotationLineVSAssembly) viewsheet.getAssembly(info.getLine());

      // Apply format from model to rectangle and line
      applyFormatDialogModel(rectangle, line, model);

      // Refresh annotation and relayout viewsheet
      annotationService.refreshAnnotation(rvs, annotation, rectangle,
                                          parentAssembly, linkUri, dispatcher);
   }

   /**
    * Reset the AnnotationFormatDialog to the initialized state for an assembly annotation.
    */
   @GetMapping("/annotation/resetAssemblyFormat")
   @ResponseBody
   public AnnotationFormatDialogModel resetAssemblyFormat() {
      final AnnotationRectangleVSAssembly rectangle = new AnnotationRectangleVSAssembly();
      final AnnotationLineVSAssembly line = new AnnotationLineVSAssembly();
      final VSAssemblyInfo rInfo = rectangle.getVSAssemblyInfo();
      final VSAssemblyInfo lInfo = line.getVSAssemblyInfo();
      annotationService.initDefaultAssemblyFormat((AnnotationRectangleVSAssemblyInfo) rInfo,
                                                  (AnnotationLineVSAssemblyInfo) lInfo);
      return createFormatDialogModel(rectangle, line);
   }

   /**
    * Reset the AnnotationFormatDialog to the initialized state for a viewsheet annotation.
    */
   @GetMapping("/annotation/resetViewsheetFormat")
   @ResponseBody
   public AnnotationFormatDialogModel resetViewsheetFormat() {
      final AnnotationRectangleVSAssembly rectangle = new AnnotationRectangleVSAssembly();
      final VSAssemblyInfo rInfo = rectangle.getVSAssemblyInfo();
      annotationService.initDefaultViewsheetFormat((AnnotationRectangleVSAssemblyInfo) rInfo);
      return createFormatDialogModel(rectangle, null);
   }

   /**
    * Reset the AnnotationFormatDialog to the initialized state for an assembly annotation.
    */
   @GetMapping("/annotation/resetDataFormat")
   @ResponseBody
   public AnnotationFormatDialogModel resetDataFormat() {
      final AnnotationRectangleVSAssembly rectangle = new AnnotationRectangleVSAssembly();
      final AnnotationLineVSAssembly line = new AnnotationLineVSAssembly();
      final VSAssemblyInfo lInfo = line.getVSAssemblyInfo();
      final VSAssemblyInfo rInfo = rectangle.getVSAssemblyInfo();
      annotationService.initDefaultDataFormat((AnnotationRectangleVSAssemblyInfo) rInfo,
                                              (AnnotationLineVSAssemblyInfo) lInfo);
      return createFormatDialogModel(rectangle, line);
   }

   /**
    * Create an {@link AnnotationFormatDialogModel} to be used to open the Annotation
    * Format Dialog
    *
    * @param rectangle The annotation rectangle assembly to get the formatting from
    * @param line      The annotation line assembly to get the formatting from
    *
    * @return A new AnnotationFormatDialogModel
    */
   private AnnotationFormatDialogModel createFormatDialogModel(
      AnnotationRectangleVSAssembly rectangle,
      AnnotationLineVSAssembly line)
   {
      final AnnotationRectangleVSAssemblyInfo rectangleInfo =
         (AnnotationRectangleVSAssemblyInfo) rectangle.getVSAssemblyInfo();

      final VSCompositeFormat rectangleFormat = rectangleInfo.getFormat();
      final int boxAlpha = rectangleFormat.getAlpha();
      final String borderColor = Tool.toString(rectangleFormat.getForeground());
      final int borderRadius = rectangleFormat.getRoundCorner();
      final String borderStyle = Util.getLineStyleName(rectangleInfo.getLineStyle());
      final String fillColor = Tool.toString(rectangleFormat.getBackground());
      final AnnotationFormatDialogModel.Builder builder =
         AnnotationFormatDialogModel.builder()
                                    .boxAlpha(boxAlpha)
                                    .boxBorderColor(borderColor)
                                    .boxBorderRadius(borderRadius)
                                    .boxBorderStyle(borderStyle)
                                    .boxFillColor(fillColor);

      if(line != null) {
         final AnnotationLineVSAssemblyInfo lineInfo =
            (AnnotationLineVSAssemblyInfo) line.getVSAssemblyInfo();

         final VSCompositeFormat lineFormat = lineInfo.getFormat();
         final String lineColor = Tool.toString(lineFormat.getForeground());
         final int lineEnd = lineInfo.getEndArrowStyle();
         final String lineStyle = Util.getLineStyleName(lineInfo.getLineStyle());
         final boolean lineVisible = lineInfo.isVisible();

         builder.lineColor(lineColor)
                .lineEnd(lineEnd)
                .lineStyle(lineStyle)
                .lineVisible(lineVisible);
      }

      return builder.build();
   }

   /**
    * Apply the format model to the annotation
    *
    * @param rectangle The annotation rectangle to format
    * @param line      the annotation line to format
    */
   private void applyFormatDialogModel(final AnnotationRectangleVSAssembly rectangle,
                                       final AnnotationLineVSAssembly line,
                                       final AnnotationFormatDialogModel model)
   {
      final RectangleVSAssemblyInfo rectangleInfo =
         (RectangleVSAssemblyInfo) rectangle.getVSAssemblyInfo();
      final VSCompositeFormat rectangleFormat = rectangleInfo.getFormat();
      final VSFormat rectangleUserFormat = rectangleFormat.getUserDefinedFormat();
      final int alpha = model.getBoxAlpha();
      final String fillColor = model.getBoxFillColor();
      final String borderColor = model.getBoxBorderColor();
      final int borderRadius = model.getBoxBorderRadius();
      final int borderStyle = Util.getStyleConstantsFromString(model.getBoxBorderStyle());

      rectangleUserFormat.setAlphaValue(alpha);
      rectangleUserFormat.setForegroundValue(borderColor);
      rectangleUserFormat.setBackgroundValue(fillColor);
      rectangleUserFormat.setRoundCornerValue(borderRadius);
      rectangleInfo.setLineStyleValue(borderStyle);

      if(line != null) {
         final AnnotationLineVSAssemblyInfo lineInfo =
            (AnnotationLineVSAssemblyInfo) line.getVSAssemblyInfo();
         final VSCompositeFormat lineFormat = lineInfo.getFormat();
         final VSFormat lineUserFormat = lineFormat.getUserDefinedFormat();
         final String lineColor = model.getLineColor();
         final int lineEnd = model.getLineEnd();
         final int lineStyle = Util.getStyleConstantsFromString(model.getLineStyle());
         final boolean lineVisible = model.getLineVisible();

         lineUserFormat.setForegroundValue(lineColor);
         lineInfo.setEndArrowStyleValue(lineEnd);
         lineInfo.setLineStyleValue(lineStyle);
         lineInfo.setVisibleValue(lineVisible ? "show" : "hide");
      }
   }

   private final VSObjectService service;
   private final VSAnnotationService annotationService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
}
