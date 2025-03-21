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

package inetsoft.web.vswizard.controller;

import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.web.vswizard.model.VSWizardOriginalModel;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Service
@ClusterProxy
public class VSWizardObjectToolbarService {

   public VSWizardObjectToolbarService(VSWizardTemporaryInfoService temporaryInfoService,
                                       ViewsheetService viewsheetService)
   {
      this.viewsheetService = viewsheetService;
      this.temporaryInfoService = temporaryInfoService;
   }


   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void gotoFullEditor(@ClusterProxyKey String id,
      @RequestParam(value = "assemblyName", required = false) String assemblyName,
      Principal principal)
      throws Exception
   {

      RuntimeViewsheet rvs = this.viewsheetService.getViewsheet(id, principal);

      if(rvs == null) {
         LOGGER.error("The viewsheet does not exist: {}", id);
         return null;
      }

      if(assemblyName == null) {
         // may be null if the chart is not bound (Bug #44232)
         return null;
      }

      Viewsheet vs = rvs.getViewsheet();
      Assembly assembly = vs.getAssembly(assemblyName);

      if(assembly == null) {
         LOGGER.error("The assembly does not exist: {}", assemblyName);
         return null;
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

      return null;
   }

   private final VSWizardTemporaryInfoService temporaryInfoService;
   private final ViewsheetService viewsheetService;

   private static final Logger LOGGER = LoggerFactory.getLogger(VSWizardObjectToolbarService.class);
}
