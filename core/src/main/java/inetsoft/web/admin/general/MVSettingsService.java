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
package inetsoft.web.admin.general;

import inetsoft.mv.MVManager;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.content.repository.MVSupportService;
import inetsoft.web.admin.general.model.MVSettingsModel;
import inetsoft.web.admin.general.model.MVType;
import inetsoft.web.viewsheet.AuditUser;
import inetsoft.web.viewsheet.Audited;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

import static inetsoft.web.admin.schedule.ScheduleCycleService.getCyclePermissionID;

@Service
public class MVSettingsService {
   public MVSettingsService(MVSupportService mvSupport, SecurityEngine securityEngine) {
      this.mvSupport = mvSupport;
      this.securityEngine = securityEngine;
   }

   public MVSettingsModel getModel(Principal principal) throws Exception {
      DataCycleManager dmgr = DataCycleManager.getDataCycleManager();
      Set<String> cycleNames = new TreeSet<>(Comparator.naturalOrder());
      String orgId = OrganizationManager.getInstance().getCurrentOrgID(principal);
      boolean isEnterprise = LicenseManager.getInstance().isEnterprise();

      if(!SUtil.isMultiTenant() || !isEnterprise) {
         for(Enumeration<String> cycles = dmgr.getDataCycles(orgId); cycles.hasMoreElements(); ) {
            String cycleName = cycles.nextElement();

            if(securityEngine.checkPermission(principal, ResourceType.SCHEDULE_CYCLE,
                                              getCyclePermissionID(cycleName, orgId), ResourceAction.ACCESS))
            {
               cycleNames.add(cycleName);
            }
         }
      }

      String cycle = MVManager.getManager().getDefaultCycle();
      cycle = cycle == null ? "" : cycle;

      return MVSettingsModel.builder()
         .onDemand("true".equals(SreeEnv.getProperty("mv.ondemand")))
         .onDemandDefault("true".equals(SreeEnv.getProperty("mv.enabled.all")))
         .defaultCycle(cycle)
         .metadata("true".equals(SreeEnv.getProperty("mv.metadata")))
         .required("true".equals(SreeEnv.getProperty("mv.required")))
         .cycles(cycleNames)
         .build();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "General-Materialized View",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void setModel(MVSettingsModel model,
                        @SuppressWarnings("unused") @AuditUser Principal principal)
      throws Exception
   {
      DataCycleManager dmgr = DataCycleManager.getDataCycleManager();
      dmgr.setDefaultCycle(model.defaultCycle());
      dmgr.save();
      MVManager.getManager().setDefaultCycle(model.defaultCycle());

      SreeEnv.setProperty("mv.ondemand", model.onDemand() + "");
      SreeEnv.setProperty("mv.enabled.all", model.onDemandDefault() + "");
      SreeEnv.setProperty("mv.metadata", model.metadata() + "");
      SreeEnv.setProperty("mv.required", model.required() + "");
      SreeEnv.save();
   }

   private final MVSupportService mvSupport;
   private final SecurityEngine securityEngine;
   private static final Logger LOG = LoggerFactory.getLogger(MVSettingsService.class);
}