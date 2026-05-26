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

import inetsoft.report.internal.Util;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.Undoable;
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
   public VSAnnotationFormatController(VSAnnotationFormatServiceProxy vsAnnotationFormatServiceProxy,
                                       VSAnnotationService annotationService,
                                       RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.annotationService = annotationService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsAnnotationFormatServiceProxy = vsAnnotationFormatServiceProxy;
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
      vsAnnotationFormatServiceProxy.openFormatDialog(runtimeViewsheetRef.getRuntimeId(), event, principal, dispatcher);
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
     vsAnnotationFormatServiceProxy.updateFormat(runtimeViewsheetRef.getRuntimeId(), event, principal, linkUri, dispatcher);
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

   private final VSAnnotationService annotationService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private VSAnnotationFormatServiceProxy vsAnnotationFormatServiceProxy;
}
