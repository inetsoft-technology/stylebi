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

package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.web.viewsheet.event.ChangeTabStateEvent;
import inetsoft.web.viewsheet.service.*;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class VSTabService {

   public VSTabService(CoreLifecycleService coreLifecycleService,
                       ViewsheetService viewsheetService)
   {
      this.coreLifecycleService = coreLifecycleService;
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void changeTab(@ClusterProxyKey  String vsId, String name, ChangeTabStateEvent event,
                         String linkUri, Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId,
                                                           principal);
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return null;
      }

      // Name of assembly that will be displayed
      String compName = event.getTarget();

      // Tab assembly
      VSAssembly tassembly = (VSAssembly) vs.getAssembly(name);

      if(tassembly != null) {
         ((TabVSAssemblyInfo) tassembly.getVSAssemblyInfo()).setSelectedValue(compName);
         ((TabVSAssemblyInfo) tassembly.getVSAssemblyInfo()).setSelected(null);
         coreLifecycleService.execute(rvs, name, linkUri, VSAssembly.VIEW_CHANGED, dispatcher);
      }

      return null;
   }

   private final ViewsheetService viewsheetService;
   private final CoreLifecycleService coreLifecycleService;
}
