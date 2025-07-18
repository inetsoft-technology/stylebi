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

package inetsoft.web.viewsheet;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.mv.MVSession;
import inetsoft.report.composition.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.WSExecution;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class EventAspectService {

   public EventAspectService(ViewsheetService viewsheetService,
                             CoreLifecycleService coreLifecycleService) {
      this.viewsheetService = viewsheetService;
      this.coreLifecycleService = coreLifecycleService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void updateViewsheet(@ClusterProxyKey String runtimeId, CommandDispatcher commandDispatcher, Principal principal) throws Exception{
      RuntimeSheet rts = viewsheetService.getSheet(runtimeId, principal);

      if(rts instanceof RuntimeViewsheet) {
         RuntimeViewsheet rvs = (RuntimeViewsheet) rts;
         Viewsheet vs = rvs.getViewsheet();
         TextVSAssembly textVSAssembly = null;

         if(vs != null) {
            textVSAssembly = vs.getWarningTextAssembly(false);

            if(textVSAssembly == null) {
               for(Assembly assembly : vs.getAssemblies()) {
                  if(assembly instanceof Viewsheet) {
                     textVSAssembly = ((Viewsheet) assembly).getWarningTextAssembly(false);
                  }
               }
            }
         }

         if(textVSAssembly != null) {
            vs.adjustWarningTextPosition();
            coreLifecycleService.addDeleteVSObject(rvs, textVSAssembly, commandDispatcher);
            coreLifecycleService.refreshVSAssembly(rvs, textVSAssembly, commandDispatcher);
         }
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setWSExecution(@ClusterProxyKey String runtimeId, boolean undoable, Principal principal) throws Exception {
      RuntimeWorksheet rws = viewsheetService.getWorksheet(runtimeId, principal);
      MVSession session = rws.getAssetQuerySandbox().getMVSession();
      WSExecution.setAssetQuerySandbox(rws.getAssetQuerySandbox());

      // if worksheet changed, re-init sql context so change in table
      // is reflected in spark sql
      if(undoable && session != null) {
         session.clearInitialized();
      }

      WSExecution.setAssetQuerySandbox(rws.getAssetQuerySandbox());
      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void clearWSExecution(@ClusterProxyKey String runtimeId) throws Exception {
      WSExecution.setAssetQuerySandbox(null);
      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void addExecutionMonitoring(@ClusterProxyKey String runtimeId) throws Exception {
      viewsheetService.addExecution(runtimeId);
      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void removeExecutionMonitoring(@ClusterProxyKey String runtimeId) throws Exception {
      viewsheetService.removeExecution(runtimeId);
      return null;
   }

   private final ViewsheetService viewsheetService;
   private final CoreLifecycleService coreLifecycleService;
}
