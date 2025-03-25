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

package inetsoft.web.viewsheet.controller.annotation;

import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.viewsheet.command.RemoveVSObjectCommand;
import inetsoft.web.viewsheet.event.annotation.UpdateAnnotationEvent;
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.security.Principal;

@Service
@ClusterProxy
public class VSAnnotationUpdateService {

   public VSAnnotationUpdateService(VSObjectService service,
                                    VSAnnotationService annotationService)
   {
      this.service = service;
      this.annotationService = annotationService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void updateAnnotation(@ClusterProxyKey String id, UpdateAnnotationEvent event,
                                Principal principal, String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      // Get properties from event object
      final String content = event.getContent();
      final Rectangle newBounds = event.getNewBounds();
      final RuntimeViewsheet rvs =
         service.getRuntimeViewsheet(id, principal);
      final Viewsheet viewsheet = rvs.getViewsheet();
      final String name = event.getName();
      final String parent = AnnotationVSUtil.getAnnotationParentName(viewsheet, name);
      final AnnotationVSAssembly annotation =
         (AnnotationVSAssembly) viewsheet.getAssembly(name);
      final AnnotationVSAssemblyInfo ainfo =
         (AnnotationVSAssemblyInfo) annotation.getVSAssemblyInfo();
      final AnnotationRectangleVSAssembly annotationRectangle =
         (AnnotationRectangleVSAssembly) viewsheet.getAssembly(ainfo.getRectangle());
      final VSAssembly parentAssembly = viewsheet.getAssembly(parent);
      AffineTransform tx;
      int assemblyType = ainfo.getType();

      if(assemblyType == AnnotationVSAssemblyInfo.ASSEMBLY) {
         tx = annotationService.getInverseScaleTransform(viewsheet, parentAssembly);
      }
      else {
         tx = new AffineTransform(); // identity
      }

      // Update annotation content
      annotationService.updateAnnotationContent(annotationRectangle, content);

      // translate and transform the annotation rectangle by the delta rect
      // send VSRemoveObjectCommand if the size set is different from the event size
      if(!annotationRectangle.getBounds().equals(newBounds) &&
         annotationService.transformRectangle(annotationRectangle, newBounds, tx))
      {
         RemoveVSObjectCommand command = new RemoveVSObjectCommand();
         command.setName(annotation.getAbsoluteName());
         dispatcher.sendCommand(command);
      }

      // Refresh annotations and re-layout viewsheet
      annotationService.refreshAnnotation(rvs, annotation, annotationRectangle,
                                          parentAssembly, linkUri, dispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void updateAnnotationEndpoint(@ClusterProxyKey String id, UpdateAnnotationEvent event,
                                        Principal principal,
                                        @LinkUri String linkUri,
                                        CommandDispatcher dispatcher) throws Exception
   {
      final Rectangle newBounds = event.getNewBounds();
      final RuntimeViewsheet rvs =
         service.getRuntimeViewsheet(id, principal);
      final Viewsheet viewsheet = rvs.getViewsheet();
      final String name = event.getName();
      final AnnotationVSAssembly annotation =
         (AnnotationVSAssembly) viewsheet.getAssembly(name);
      final AnnotationVSAssemblyInfo ainfo =
         (AnnotationVSAssemblyInfo) annotation.getVSAssemblyInfo();
      final AnnotationRectangleVSAssembly annotationRectangle =
         (AnnotationRectangleVSAssembly) viewsheet.getAssembly(ainfo.getRectangle());
      final AnnotationLineVSAssembly annotationLine =
         (AnnotationLineVSAssembly) viewsheet.getAssembly(ainfo.getLine());
      final VSAssemblyInfo lineInfo = annotationLine.getVSAssemblyInfo();
      String parent = AnnotationVSUtil.getAnnotationParentName(viewsheet, name);
      VSAssembly parentAssembly = (VSAssembly) viewsheet.getAssembly(parent);

      // translate the line
      AffineTransform tx = annotationService.getInverseScaleTransform(viewsheet, parentAssembly);
      Point2D src = new Point2D.Double(newBounds.getX(), newBounds.getY());
      Point2D dst = new Point2D.Double();
      tx.transform(src, dst);
      Point newOffset = new Point((int) Math.round(dst.getX()), (int) Math.round(dst.getY()));
      annotationLine.setPixelOffset(newOffset);
      ainfo.setPixelOffset(newOffset);

      // modify layout position if set
      if(lineInfo.getLayoutPosition() != null) {
         final Point newLayoutPosition = new Point(newBounds.x, newBounds.y);
         lineInfo.setScaledPosition(newLayoutPosition);
         ainfo.setScaledPosition(newLayoutPosition);
      }

      // refresh annotation
      annotationService.refreshAnnotation(rvs, annotation, annotationRectangle,
                                          parentAssembly, linkUri, dispatcher);
      return null;
   }

   private final VSObjectService service;
   private final VSAnnotationService annotationService;
}
