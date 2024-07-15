/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.MaxObjectEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class MaxModeAssemblyController {
   @Autowired
   public MaxModeAssemblyController(ViewsheetService viewsheetService,
                                    RuntimeViewsheetRef runtimeViewsheetRef,
                                    MaxModeAssemblyService maxModeAssemblyService)
   {
      this.viewsheetService = viewsheetService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.maxModeAssemblyService = maxModeAssemblyService;
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/vs/assembly/max-mode/toggle")
   public void toggleMaxMode(MaxObjectEvent event,
                              Principal principal, CommandDispatcher dispatcher,
                              @LinkUri String linkUri)
      throws Exception
   {
      final RuntimeViewsheet rvs =
         viewsheetService.getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      maxModeAssemblyService.toggleMaxMode(rvs, event.assemblyName(), event.maxSize(), dispatcher,
         linkUri);
   }

   private ViewsheetService viewsheetService;
   private RuntimeViewsheetRef runtimeViewsheetRef;
   private MaxModeAssemblyService maxModeAssemblyService;
}
