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
import inetsoft.uql.viewsheet.internal.AnnotationVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.AnnotationVSUtil;
import inetsoft.web.viewsheet.command.AnnotationChangedCommand;
import inetsoft.web.viewsheet.event.annotation.RemoveAnnotationEvent;
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class VSAnnotationRemoveService {

   public VSAnnotationRemoveService(VSObjectService service) {
      this.service = service;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void removeAnnotation(@ClusterProxyKey String id, RemoveAnnotationEvent event,
                                String linkUri,Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      // Get properties from event object
      final String[] names = event.getNames();
      final RuntimeViewsheet rvs =
         service.getRuntimeViewsheet(id, principal);
      final Viewsheet vs = rvs.getViewsheet();

      for(String name : names) {
         final VSAssembly annotation = (AnnotationVSAssembly) vs.getAssembly(name);
         final AnnotationVSAssemblyInfo ainfo =
            (AnnotationVSAssemblyInfo) annotation.getVSAssemblyInfo();
         final VSAssembly annotationRectangle =
            (AnnotationRectangleVSAssembly) vs.getAssembly(ainfo.getRectangle());
         final VSAssembly annotationLine =
            (AnnotationLineVSAssembly) vs.getAssembly(ainfo.getLine());

         // Remove annotation, rectangle, and line
         service.removeVSAssembly(rvs, annotation, linkUri, dispatcher);

         if(annotationRectangle != null) {
            service.removeVSAssembly(rvs, annotationRectangle, linkUri, dispatcher);
         }

         if(annotationLine != null) {
            service.removeVSAssembly(rvs, annotationLine, linkUri, dispatcher);
         }
      }

      if(!AnnotationVSUtil.isAnnotated(vs)) {
         service.setViewsheetInfo(rvs, linkUri, dispatcher);
      }

      dispatcher.sendCommand(AnnotationChangedCommand.of(true));

      return null;
   }


   private VSObjectService service;
}
