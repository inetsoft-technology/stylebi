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

package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CurrentSelectionVSAssemblyInfo;
import inetsoft.web.composer.vs.objects.event.VSObjectEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;

@Service
@ClusterProxy
public class ComposerCurrentSelectionService {

   public ComposerCurrentSelectionService(CoreLifecycleService coreLifecycleService,
                                          ViewsheetService viewsheetService)
   {
      this.coreLifecycleService = coreLifecycleService;
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setTitleRatio(@ClusterProxyKey String runtimeId, double ratio,
                             VSObjectEvent event, Principal principal,
                             CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      String name = event.getName();

      if(viewsheet == null) {
         return null;
      }

      VSAssembly assembly = (VSAssembly) viewsheet.getAssembly(name);

      if(!(assembly instanceof CurrentSelectionVSAssembly)) {
         return null;
      }

      // sanity check
      Dimension size = viewsheet.getPixelSize(assembly.getVSAssemblyInfo());
      // reserve 50px for the mini-toolbar, otherwise resizer is covered
      ratio = Math.max(0.05, Math.min((size.width - 50.0) / size.width, ratio));

      CurrentSelectionVSAssembly containerAssembly = (CurrentSelectionVSAssembly) assembly;
      ((CurrentSelectionVSAssemblyInfo) containerAssembly.getInfo()).setTitleRatio(ratio);
      coreLifecycleService.refreshVSAssembly(rvs, assembly, dispatcher);

      return null;
   }

   private final CoreLifecycleService coreLifecycleService;
   private final ViewsheetService viewsheetService;
}
