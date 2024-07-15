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
package inetsoft.web.vswizard.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.vswizard.model.VSWizardOriginalModel;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * vs wizard object toolbar controller.
 */
@RestController
public class VSWizardObjectToolbarController {

   @Autowired
   public VSWizardObjectToolbarController(ViewsheetService viewsheetService,
                                          VSWizardTemporaryInfoService temporaryInfoService)
   {
      this.viewsheetService = viewsheetService;
      this.temporaryInfoService = temporaryInfoService;
   }

   @GetMapping("/api/vswizard/object/toolbar/full-editor")
   public void gotoFullEditor(
      @RequestParam("id") String id,
      @RequestParam(value = "assemblyName", required = false) String assemblyName,
      Principal principal)
      throws Exception
   {

      RuntimeViewsheet rvs = this.viewsheetService.getViewsheet(id, principal);

      if(rvs == null) {
         LOGGER.error("The viewsheet does not exist: {}", id);
         return;
      }

      if(assemblyName == null) {
         // may be null if the chart is not bound (Bug #44232)
         return;
      }

      Viewsheet vs = rvs.getViewsheet();
      Assembly assembly = vs.getAssembly(assemblyName);

      if(assembly == null) {
         LOGGER.error("The assembly does not exist: {}", assemblyName);
         return;
      }

      VSTemporaryInfo vsTemporaryInfo = temporaryInfoService.getVSTemporaryInfo(rvs);
      VSWizardOriginalModel originalModel = vsTemporaryInfo.getOriginalModel();

      if(originalModel != null) {
         String originalName = originalModel.getOriginalName();
         Assembly originalAssembly = vs.getAssembly(originalName);

         if(originalAssembly != null &&
            assembly.getAssemblyType() == originalAssembly.getAssemblyType())
         {
            assembly.setPixelSize(originalAssembly.getPixelSize());
         }
      }
   }

   private final ViewsheetService viewsheetService;
   private final VSWizardTemporaryInfoService temporaryInfoService;

   private static final Logger LOGGER = LoggerFactory.getLogger(VSWizardObjectToolbarController.class);
}
