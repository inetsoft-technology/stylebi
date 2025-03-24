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
import inetsoft.graph.internal.GTool;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.sree.UserEnv;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.event.annotation.ToggleAnnotationStatusEvent;
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;

@Service
@ClusterProxy
public class VSAnnotationToggleService {

   public VSAnnotationToggleService(VSObjectService service) {
      this.service = service;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void toggleAnnotationStatus(@ClusterProxyKey String id, ToggleAnnotationStatusEvent event,
                                      Principal principal, String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      final boolean status = event.getStatus();
      UserEnv.setProperty(principal, "annotation", GTool.toString(status));
      RuntimeViewsheet rvs =
         service.getRuntimeViewsheet(id, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      viewsheet.setAnnotationsVisible(status);
      Object size = rvs.getProperty("viewsheet.appliedScale");

      if(size instanceof Dimension && viewsheet.getViewsheetInfo().isScaleToScreen() &&
         (rvs.isViewer() || rvs.isPreview()))
      {
         service.refreshViewsheet(rvs, ((Dimension) size).width, ((Dimension) size).height,
                                  linkUri, dispatcher);
      }
      else {
         service.refreshViewsheet(rvs, linkUri, dispatcher);
      }

      return null;
   }

   private VSObjectService service;
}
