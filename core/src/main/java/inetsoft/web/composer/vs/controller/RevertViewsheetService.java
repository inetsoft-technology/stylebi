/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VizModernizeUtil;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.springframework.stereotype.Service;

import java.security.Principal;

/**
 * Revert the focused dashboard: clear the mark on its marked assemblies and give them back the
 * chrome a dashboard created without the gate would have. Composer only, write permission required,
 * and offered under both gate states - unlike Modernize, which needs an open gate.
 */
@Service
@ClusterProxy
public class RevertViewsheetService {
   public RevertViewsheetService(ViewsheetService viewsheetService,
                                 CoreLifecycleService coreLifecycleService,
                                 AssetRepository assetRepository)
   {
      this.viewsheetService = viewsheetService;
      this.coreLifecycleService = coreLifecycleService;
      this.assetRepository = assetRepository;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   @ClusterWriteMethod
   public Void revert(@ClusterProxyKey String runtimeId, Principal principal,
                      CommandDispatcher dispatcher, String linkUri)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      try {
         // scope-aware: a private dashboard is its owner's, a global one needs the grant
         assetRepository.checkAssetPermission(principal, rvs.getEntry(), ResourceAction.WRITE);
         Viewsheet vs = rvs.getViewsheet();

         if(vs == null || VizModernizeUtil.revert(vs) == 0) {
            // nothing changed, but the client already cleared its revertable flag when it sent the
            // event; dispatch so it recomputes and the menu entry doesn't stay hidden
            coreLifecycleService.setViewsheetInfo(rvs, linkUri, dispatcher);
            return null;
         }

         ChangedAssemblyList clist =
            coreLifecycleService.createList(false, dispatcher, rvs, linkUri);
         coreLifecycleService.setViewsheetInfo(rvs, linkUri, dispatcher);
         coreLifecycleService.refreshViewsheet(rvs, rvs.getID(), linkUri, dispatcher, false, false,
                                               true, clist);
         return null;
      }
      catch(Exception ex) {
         // the client cleared the revertable flag optimistically when it sent the event; on failure
         // (e.g. permission denial) dispatch the recomputed flag so the entry comes back for the
         // rest of the session, then rethrow so the failure still propagates
         coreLifecycleService.setViewsheetInfo(rvs, linkUri, dispatcher);
         throw ex;
      }
   }

   private final ViewsheetService viewsheetService;
   private final CoreLifecycleService coreLifecycleService;
   private final AssetRepository assetRepository;
}
