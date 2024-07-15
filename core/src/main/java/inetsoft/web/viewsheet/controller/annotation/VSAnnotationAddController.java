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

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.StyleConstants;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.painter.HTMLPresenter;
import inetsoft.sree.UserEnv;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.annotation.AddAnnotationEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.security.Principal;

@Controller
public class VSAnnotationAddController {
   @Autowired
   public VSAnnotationAddController(VSObjectService service,
                                    VSAnnotationService annotationService,
                                    RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.service = service;
      this.annotationService = annotationService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/annotation/add-viewsheet-annotation")
   public void addViewsheetAnnotation(@Payload AddAnnotationEvent event,
                                      @LinkUri String linkUri,
                                      Principal principal,
                                      CommandDispatcher dispatcher) throws Exception
   {
      addAnnotation(event, linkUri, principal, dispatcher, AnnotationVSAssemblyInfo.VIEWSHEET);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/annotation/add-assembly-annotation")
   public void addAssemblyAnnotation(@Payload AddAnnotationEvent event,
                                     @LinkUri String linkUri,
                                     Principal principal,
                                     CommandDispatcher dispatcher) throws Exception
   {
      addAnnotation(event, linkUri, principal, dispatcher, AnnotationVSAssemblyInfo.ASSEMBLY);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/annotation/add-data-annotation")
   public void addDataAnnotation(@Payload AddAnnotationEvent event,
                                 @LinkUri String linkUri,
                                 Principal principal,
                                 CommandDispatcher dispatcher) throws Exception
   {
      addAnnotation(event, linkUri, principal, dispatcher, AnnotationVSAssemblyInfo.DATA);
   }

   /**
    * Add an annotation to the runtime viewsheet.
    *
    * @param event        The event with properties of the annotation to add
    * @param principal    The current user
    * @param dispatcher   The command dispatcher used to update the viewsheet
    * @param assemblyType The type of annotation to add: viewsheet, assembly, or data
    */
   private void addAnnotation(final AddAnnotationEvent event,
                              final String linkUri,
                              final Principal principal,
                              final CommandDispatcher dispatcher,
                              final int assemblyType) throws Exception
   {
      // Only allow adding annotations if logged in
      if(!service.isLoggedIn(principal)) {
         return;
      }

      // Get properties from event object
      final String content = event.getContent();
      final int x = Math.max(event.getX(), 1);
      final int y = Math.max(event.getY(), 1);
      final int row = event.getRow();
      final int col = event.getCol();
      final String measureName = event.getMeasureName();
      final String parent = event.getParent();
      final RuntimeViewsheet rvs =
         service.getRuntimeViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      final Viewsheet viewsheet = rvs.getViewsheet();
      final boolean isAnnotated = AnnotationVSUtil.isAnnotated(viewsheet);
      final VSAssembly parentAssembly = (VSAssembly) viewsheet.getAssembly(parent);
      final VSAssemblyInfo parentInfo = parentAssembly != null ?
         (VSAssemblyInfo) parentAssembly.getInfo() : null;

      // Create annotation
      final AnnotationVSAssembly annotation =
         (AnnotationVSAssembly) VSEventUtil.createVSAssembly(rvs, Viewsheet.ANNOTATION_ASSET);
      assert annotation != null;
      final AnnotationVSAssemblyInfo ainfo =
         (AnnotationVSAssemblyInfo) annotation.getVSAssemblyInfo();
      AnnotationLineVSAssemblyInfo linfo = null;
      AffineTransform tx;

      if(assemblyType == AnnotationVSAssemblyInfo.ASSEMBLY) {
         tx = annotationService.getInverseScaleTransform(viewsheet, parentAssembly);
      }
      else {
         tx = new AffineTransform(); // identity
      }

      Point2D src = new Point2D.Double(x, y);
      Point2D dst = new Point2D.Double();
      tx.transform(src, dst);

      final Point annotationPosition =
         new Point((int) Math.floor(dst.getX()), (int) Math.floor(dst.getY()));
      final Point rectanglePosition = new Point(annotationPosition);

      if(assemblyType == AnnotationVSAssemblyInfo.ASSEMBLY ||
         assemblyType == AnnotationVSAssemblyInfo.DATA)
      {
         //adjust the annotation position to make it do not out of the parent.
         if(viewsheet.getViewsheetInfo().isScaleToScreen() &&
            !(parentInfo instanceof TableDataVSAssemblyInfo))
         {
            Dimension parentSize = parentInfo.getLayoutSize(false);
            parentSize = parentSize != null ? parentSize : parentInfo.getPixelSize();

            if(parentSize != null) {
               if(annotationPosition.x < 0) {
                  annotationPosition.x = 0;
               }

               if(annotationPosition.x > parentSize.width) {
                  annotationPosition.x = parentSize.width;
               }

               if(annotationPosition.y < 0) {
                  annotationPosition.y = 0;
               }

               if(annotationPosition.y > parentSize.height) {
                  annotationPosition.y = parentSize.height;
               }
            }
         }

         // Move non-viewsheet level annotation over by old default grid width to
         // create space for the line
         rectanglePosition.translate(70, 0);

         // Create line and set its properties
         VSAssembly annotationLine =
            VSEventUtil.createVSAssembly(rvs, Viewsheet.ANNOTATION_LINE_ASSET);
         assert annotationLine != null;
         linfo = (AnnotationLineVSAssemblyInfo) annotationLine.getVSAssemblyInfo();
         linfo.setStartAnchorPos(AnnotationLineVSAssemblyInfo.WEST);
         annotationLine.setPixelOffset(annotationPosition);
         linfo.setScaledPosition(new Point(x, y));
         ainfo.setLine(annotationLine.getAbsoluteName());

         if(parentInfo instanceof BaseAnnotationVSAssemblyInfo) {
            ((BaseAnnotationVSAssemblyInfo) parentInfo).addAnnotation(annotation.getAbsoluteName());
         }

         if(assemblyType == AnnotationVSAssemblyInfo.DATA) {
            ViewsheetSandbox box = rvs.getViewsheetSandbox();
            ainfo.setValue(AnnotationVSUtil.getAnnotationDataValue(box, parentAssembly, row, col,
                                                                   measureName));
            ainfo.setRow(row);
            ainfo.setCol(col);

            if(parentInfo instanceof TableDataVSAssemblyInfo) {
               linfo.setEndArrowStyleValue(StyleConstants.NONE);
            }
         }
      }

      // create rectangle after line so z-index is correct
      final AnnotationRectangleVSAssembly annotationRectangle = (AnnotationRectangleVSAssembly)
         VSEventUtil.createVSAssembly(rvs, Viewsheet.ANNOTATION_RECTANGLE_ASSET);
      assert annotationRectangle != null;
      final String rectangleName = annotationRectangle.getAbsoluteName();
      final AnnotationRectangleVSAssemblyInfo rinfo = (AnnotationRectangleVSAssemblyInfo)
         annotationRectangle.getVSAssemblyInfo();

      if(linfo != null) {
         linfo.setStartAnchorID(rectangleName);
      }

      if(assemblyType == AnnotationVSAssemblyInfo.VIEWSHEET) {
         annotationService.initDefaultViewsheetFormat(rinfo);
      }

      HTMLPresenter presenter = new HTMLPresenter();
      int pwidth = 100;
      Dimension psize = presenter.getPreferredSize(content, pwidth);

      while(psize.height > 100 && psize.width < 400) {
         psize = presenter.getPreferredSize(content, pwidth += 100);
      }

      psize.height = Math.min(psize.height, 300);

      // Set annotation properties
      annotation.setPixelOffset(annotationPosition);
      ainfo.setScaledPosition(new Point(x, y));
      boolean isViewsheet = assemblyType == AnnotationVSAssemblyInfo.VIEWSHEET;
      Point point = annotationService.getAdjustedPoint(event, rvs, psize, assemblyType);
      annotationRectangle.setPixelOffset(point);
      src = new Point2D.Double(isViewsheet ? x : point.x, point.y);
      dst = new Point2D.Double();
      tx.transform(src, dst);
      rinfo.setScaledPosition(new Point((int) Math.floor(dst.getX()),
                                        (int) Math.floor(dst.getY())));
      rinfo.setPixelSize(new Dimension(psize.width, psize.height + 15));
      ainfo.setRectangle(rectangleName);
      ainfo.setType(assemblyType);

      // Set annotation content
      annotationService.updateAnnotationContent(annotationRectangle, content);

      // if first annotation or new annotation force visible
      if(!isAnnotated || !"true".equals(UserEnv.getProperty(principal, "annotation"))) {
         UserEnv.setProperty(rvs.getUser(), "annotation", "true");
         boolean oAnnotationVisible = viewsheet.getAnnotationsVisible();
         viewsheet.setAnnotationsVisible(true);
         Object size = rvs.getProperty("viewsheet.appliedScale");

         if(viewsheet.getViewsheetInfo().isScaleToScreen() && size instanceof Dimension &&
            ((Dimension) size).height > 0 && ((Dimension) size).width > 0 )
         {
            service.refreshViewsheet(rvs, ((Dimension) size).width, ((Dimension) size).height,
               linkUri, dispatcher);
         }
         else if(!oAnnotationVisible) {
            service.refreshViewsheet(rvs, linkUri, dispatcher);
         }

         service.setViewsheetInfo(rvs, linkUri, dispatcher);
      }

      // Refresh annotations and relayout viewsheet
      annotationService.refreshAnnotation(rvs, annotation, annotationRectangle,
                                          parentAssembly, linkUri, dispatcher);
   }

   private final VSObjectService service;
   private final VSAnnotationService annotationService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
}
