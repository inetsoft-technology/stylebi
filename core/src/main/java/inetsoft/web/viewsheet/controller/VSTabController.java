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
package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.ChangeTabStateEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class VSTabController {
   /**
    * Creates a new instance of <tt>VSTabController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    * @param viewsheetService
    */
   @Autowired
   public VSTabController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      CoreLifecycleService coreLifecycleService,
      ViewsheetService viewsheetService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.coreLifecycleService = coreLifecycleService;
      this.viewsheetService = viewsheetService;
   }

   @Undoable
   @MessageMapping("/tab/changetab/{name}")
   public void changeTab(@DestinationVariable("name") String name,
                         @Payload ChangeTabStateEvent event, @LinkUri String linkUri,
                         Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeViewsheetRef.getRuntimeId(),
                                                           principal);
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
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
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final CoreLifecycleService coreLifecycleService;
   private final ViewsheetService viewsheetService;
}
