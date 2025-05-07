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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.event.DelayVisibilityEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class DelayVisibilityController {
   public DelayVisibilityController(RuntimeViewsheetRef runtimeViewsheetRef,
                                    ViewsheetService viewsheetService,
                                    CoreLifecycleService lifecycleService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.viewsheetService = viewsheetService;
      this.lifecycleService = lifecycleService;
   }

   @MessageMapping("/vs/showDelayedVisibility")
   public void onShowDelayedVisibility(@Payload DelayVisibilityEvent event,
                                       Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = (RuntimeViewsheet)
         viewsheetService.getSheet(runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null || box == null) {
         return;
      }

      for(String name : event.assemblies()) {
         VSAssembly assembly = vs.getAssembly(name);

         if(assembly != null) {
            assembly.getVSAssemblyInfo().setVisible(true);
            assembly.getVSAssemblyInfo().setControlByScript(true);

            // the embedded viewsheet's visibility was originally false, it and its children
            // would have never been added, need to make sure it has been added before sending
            // the refresh event
            if((assembly instanceof Viewsheet) || assembly.isEmbedded()) {
               lifecycleService.addDeleteVSObject(rvs, assembly, dispatcher, true);
            }

            lifecycleService.refreshVSAssembly(rvs, assembly, dispatcher);
         }
      }
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ViewsheetService viewsheetService;
   private final CoreLifecycleService lifecycleService;
}
