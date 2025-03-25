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
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.web.viewsheet.service.RuntimeViewsheetManager;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class ViewsheetControllerService {

   public ViewsheetControllerService(RuntimeViewsheetManager runtimeViewsheetManager,
                                     ViewsheetService viewsheetService)
   {
      this.runtimeViewsheetManager = runtimeViewsheetManager;
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void closeViewsheet(@ClusterProxyKey String rvId, Principal principal)
   {
      try {
         viewsheetService.closeViewsheet(rvId, principal);

         if(runtimeViewsheetManager != null) {
            runtimeViewsheetManager.sheetClosed(rvId);
         }
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to close viewsheet", e);
      }

      return null;
   }


   private final RuntimeViewsheetManager runtimeViewsheetManager;
   private final ViewsheetService viewsheetService;
}
