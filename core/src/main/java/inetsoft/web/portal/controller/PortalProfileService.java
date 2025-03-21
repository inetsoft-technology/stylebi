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

package inetsoft.web.portal.controller;

import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.WorksheetEngine;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class PortalProfileService {

   public PortalProfileService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public String getRecordsKey(@ClusterProxyKey String name, Boolean isViewsheet, Principal principal) throws Exception {
      String key = name;

      if(isViewsheet) {
         RuntimeViewsheet rvs = viewsheetService.getViewsheet(name, principal);

         if(rvs != null) {
            key = rvs.getViewsheetSandbox().getID();
         }
      }

      return key;
   }


   private ViewsheetService viewsheetService;
}
