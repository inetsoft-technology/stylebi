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
package inetsoft.sree.schedule;

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.report.script.viewsheet.ViewsheetVSAScriptable;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.RepletRequest;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;

/**
 * Defines the common API for actions that support executing viewsheet.
 *
 * @author InetSoft Technology Corp
 * @version 13.3
 */
public interface ViewsheetSupport extends ScheduleAction {

   /**
    * Get the name of the viewsheet
    */
   String getViewsheetName();

   /**
    * Set the name of the viewsheet to execute
    */
   void setViewsheetName(String viewsheetName);

   /**
    * Get the viewsheet scope.
    */
   int getScope();

   /**
    * Get the action request object.
    */
   RepletRequest getRepletRequest();

   /**
    * Get the runtimeViewsheet
    */
   default RuntimeViewsheet getRuntimeViewsheet(Principal principal)
      throws Throwable
   {
      String id = prepareViewsheet(principal);

      if(id == null) {
         return null;
      }

      getViewsheetEntry();
      ViewsheetService engine = ViewsheetEngine.getViewsheetEngine();
      RuntimeViewsheet rvs = engine.getViewsheet(id, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null || !box.isScheduleAction()) {
         closeViewsheet(id, principal);
         return null;
      }

      ViewsheetVSAScriptable vscriptable = (ViewsheetVSAScriptable)
         box.getScope().getVSAScriptable(ViewsheetScope.VIEWSHEET_SCRIPTABLE);

      if(vscriptable != null) {
         vscriptable.addProperty("taskName", getViewsheetTaskName(principal));
      }

      return rvs;
   }

   default String getViewsheetTaskName(Principal principal) {
      if(principal == null) {
         return null;
      }

      String fullName = ((XPrincipal) principal).getProperty("__TASK_NAME__");
      int idx = fullName.indexOf(':');

      return fullName.substring(idx + 1);
   }

   default String prepareViewsheet(Principal principal) throws Throwable {
      // create a default principal so developer key can work on local machine
      if(principal == null) {
         try {
            String ipAddress = Tool.getIP();
            principal = new SRPrincipal(new ClientInfo(new IdentityID(ClientInfo.ANONYMOUS, OrganizationManager.getInstance().getCurrentOrgID()),
                                                       ipAddress), new IdentityID[0], new String[0], null, 0L);
            ((SRPrincipal) principal).setProperty("__internal__", "true");
            SecurityEngine engine = SecurityEngine.getSecurity();

            if(engine != null) {
               engine.fireLoginEvent((SRPrincipal) principal);
            }
         }
         catch(Throwable ignore) {
         }
      }

      if(principal instanceof SRPrincipal) {
         SRPrincipal srprincipal = (SRPrincipal) principal;
         boolean ignore =
            "true".equals(srprincipal.getProperty("__ignore__"));
         principal = ignore ? null : principal;
      }

      AssetEntry entry = buildAssetEntry(principal);
      entry.setProperty("_scheduler_", "true");
      entry.setProperty("taskName", getViewsheetTaskName(principal));

      return ScheduleViewsheetService.getInstance().openViewsheet(entry, getRepletRequest(),
                                                                  principal);
   }

   default AssetEntry buildAssetEntry(Principal principal) {
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      return new AssetEntry(getScope(), AssetEntry.Type.VIEWSHEET,
                            getViewsheetName(), principal != null ? pId : null);
   }

   default AssetEntry getViewsheetEntry() {
      return buildAssetEntry(null);
   }

   default void closeViewsheet(String id, Principal principal) {
      ScheduleViewsheetService.getInstance().closeViewsheet(id, principal);
   }

   Logger LOG = LoggerFactory.getLogger(ViewsheetSupport.class);
}
