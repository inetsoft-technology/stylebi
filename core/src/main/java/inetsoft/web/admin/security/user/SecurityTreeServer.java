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

package inetsoft.web.admin.security.user;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.security.db.DatabaseAuthenticationProvider;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.server.LicenseInfo;
import inetsoft.web.admin.server.ServerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SecurityTreeServer {
   @Autowired
   public SecurityTreeServer(AuthenticationProviderService authenticationProviderService,
                             ServerService serverService,
                             SecurityEngine securityEngine,
                             UserTreeService userTreeService)
   {
      this.authenticationProviderService = authenticationProviderService;
      this.userTreeService = userTreeService;
      this.securityEngine = securityEngine;
      this.namedUsers = serverService.getLicenseInfos().stream()
         .anyMatch(licenseInfo -> LicenseInfo.NAMED_USER.equals(licenseInfo.getType()));
   }

   public SecurityTreeRootModel getSecurityTree(String providerName, Principal principal,
                                                boolean isPermissions, boolean providerChanged)
   {
      return getSecurityTree(providerName, principal, isPermissions, providerChanged, false, false);
   }

   public SecurityTreeRootModel getSecurityTree(String providerName, Principal principal,
                                                boolean isPermissions, boolean providerChanged,
                                                boolean hideOrgAdminRole, boolean isTimeRange)
   {
      boolean isMultiTenant = SUtil.isMultiTenant();
      securityProvider = securityEngine.getSecurityProvider();
      final AuthenticationProvider provider = providerName == null ?
         securityProvider.getAuthenticationProvider() :
         authenticationProviderService.getProviderByName(providerName);
      boolean editable = providerName == null || provider instanceof EditableAuthenticationProvider;

      try {
         if(providerChanged) {
            Comparator<String> comp = XUtil.getOrganizationComparator();
            List<String> orgIDs = Arrays.stream(provider.getOrganizationIDs())
               .filter(o -> securityProvider.checkPermission(
                  principal, ResourceType.SECURITY_ORGANIZATION, o, ResourceAction.ADMIN))
               .sorted(comp)
               .collect(Collectors.toList());
            String currOrgId = orgIDs.size() > 0 ? orgIDs.get(0) :
               Organization.getDefaultOrganizationID();
            ((XPrincipal) principal).setProperty("curr_org_id", currOrgId);
            ((XPrincipal) principal).setProperty("curr_provider_name", providerName);
         }

         IdentityID identityID = IdentityID.getIdentityIDFromKey(principal.getName());
         String currOrgID = isTimeRange ? identityID.getOrgID() :
            OrganizationManager.getInstance().getCurrentOrgID(principal);
         currOrgID = currOrgID == null ? Organization.getDefaultOrganizationID() : currOrgID;
         String[] orgIds = provider.getOrganizationIDs();

         if(orgIds.length != 0 && !Arrays.stream(orgIds).toList().contains(currOrgID)) {
            throw new InvalidOrgException(Catalog.getCatalog().getString("em.security.invalidOrganizationPassed"));
         }

         String currOrgName = provider.getOrgNameFromID(currOrgID);
         boolean isEnterprise = LicenseManager.getInstance().isEnterprise();
         isMultiTenant = isEnterprise && isMultiTenant;

         if(!isMultiTenant) {
            return SecurityTreeRootModel.builder()
               .users(userTreeService.getUserRoot(provider, principal, isMultiTenant, currOrgID, currOrgName))
               .groups(userTreeService.getGroupRoot(provider, principal, isMultiTenant, currOrgID))
               .roles(userTreeService.getRoleTree(provider, principal, isMultiTenant, currOrgID))
               .editable(editable)
               .isMultiTenant(isMultiTenant)
               .namedUsers(namedUsers)
               .build();
         }
         else {
            return SecurityTreeRootModel.builder()
               .roles(userTreeService.getRoleTree(provider, principal, isMultiTenant, currOrgID))
               .organizations(userTreeService.createOrgSecurityTreeNode(new IdentityID(currOrgName, currOrgID), currOrgName, provider, principal, false, isPermissions, hideOrgAdminRole))
               .editable(editable)
               .isMultiTenant(isMultiTenant)
               .namedUsers(namedUsers)
               .build();
         }
      }
      catch(Exception ex) {
         if(provider instanceof DatabaseAuthenticationProvider dbProvider) {
            try {
               dbProvider.testConnection();
            }
            catch(Exception ex0) {
               throw new MessageException(Catalog.getCatalog().getString("em.security.provider.db.failedConnected"));
            }
         }

         throw ex;
      }
   }

   private final boolean namedUsers;
   private final UserTreeService userTreeService;
   private final SecurityEngine securityEngine;
   private final AuthenticationProviderService authenticationProviderService;
   private SecurityProvider securityProvider;
}
