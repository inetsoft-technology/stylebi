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

package inetsoft.web.composer;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class ComposerControllerErrorHandlerService {

   public ComposerControllerErrorHandlerService(ViewsheetService viewsheetService,
                                                CoreLifecycleService coreLifecycleService)
   {
      this.viewsheetService = viewsheetService;
      this.coreLifecycleService = coreLifecycleService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Boolean confirmExceptionWaitForMV(@ClusterProxyKey String runtimeId, ConfirmException e, CommandDispatcher dispatcher,
                                      Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      return coreLifecycleService.waitForMV(e, rvs, dispatcher);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void handleInvalidDependencyRollback(@ClusterProxyKey String runtimeId, Principal principal) {
      RuntimeSheet rs = viewsheetService.getSheet(runtimeId, principal);
      rs.rollback();

      return null;
   }

   private ViewsheetService viewsheetService;
   private CoreLifecycleService coreLifecycleService;
}
