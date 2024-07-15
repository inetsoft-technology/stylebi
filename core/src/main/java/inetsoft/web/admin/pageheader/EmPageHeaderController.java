/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.pageheader;

import inetsoft.mv.MVManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XUtil;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Tool;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class EmPageHeaderController {
   @GetMapping("/api/em/pageheader/get-pageheader-model")
   public EmPageHeaderModel getPageHeaderModel(@RequestParam("provider") String providerName,
                                               @RequestParam(value = "propertyChanged", required = false) boolean propertyChanged,
                                               Principal principal)
      throws Exception
   {
      List<String> orgs = null;
      List<String> orgIDs = null;
      String currOrgID = null;
      boolean isMultiTenant = SUtil.isMultiTenant();

      if(SecurityEngine.getSecurity().isSecurityEnabled() &&
         OrganizationManager.getInstance().isSiteAdmin(principal))
      {
         SecurityProvider securityProvider = SecurityEngine.getSecurity().getSecurityProvider();
         providerName = !Tool.isEmptyString(providerName) ?
            providerName : ((XPrincipal) principal).getProperty("curr_provider_name");
         AuthenticationProvider provider = XUtil.getSecurityProvider(providerName);

         orgs = Arrays.stream(provider.getOrganizations())
            .filter(o -> securityProvider.checkPermission(
               principal, ResourceType.SECURITY_ORGANIZATION, o, ResourceAction.ADMIN))
            .sorted()
            .collect(Collectors.toList());

         orgIDs = orgs.stream()
            .map(name -> securityProvider.getOrganization(name).getOrganizationID())
            .collect(Collectors.toList());

         if(propertyChanged) {
            currOrgID = provider.getOrganizationId(provider.getOrganizations()[0]);
            ((XPrincipal) principal).setProperty("curr_org_id", currOrgID);
            ((XPrincipal) principal).setProperty("curr_provider_name", providerName);
         }
         else {
            currOrgID = ((XPrincipal) principal).getProperty("curr_org_id");
         }

         if(currOrgID == null) {
            currOrgID = OrganizationManager.getInstance().getCurrentOrgID();
         }

         //if organization doesn't exist in the list, return first item
         if(!orgIDs.isEmpty() && !orgIDs.contains(currOrgID)) {
            currOrgID = orgIDs.get(0);
            EmPageHeaderModel model = new EmPageHeaderModel(
               orgs, orgIDs, currOrgID, provider.getProviderName(), isMultiTenant);
            setCurrOrg(model, principal);
         }
      }

      return new EmPageHeaderModel(orgs, orgIDs, currOrgID, providerName, isMultiTenant);
   }

   @PostMapping("/api/em/pageheader/organization")
   public void setCurrOrg(@RequestBody EmPageHeaderModel model, Principal principal) throws Exception {
      // User must be a site administrator to access organizations
      if(SecurityEngine.getSecurity().isSecurityEnabled() && !OrganizationManager.getInstance().isSiteAdmin(principal)) {
         return;
      }

      String orgID = model.currOrgID();
      String providerName = model.providerName();
      IndexedStorage indexedStorage = IndexedStorage.getIndexedStorage();
      ((XPrincipal) principal).setProperty("curr_org_id", orgID);
      ((XPrincipal) principal).setProperty("curr_provider_name", providerName);

      if(!indexedStorage.isInitialized(orgID)) {
         DataSourceRegistry.getRegistry().init();
         MVManager.getManager().initMVDefMap();

         indexedStorage.setInitialized(orgID);
      }

      SUtil.setAdditionalDatasource((XPrincipal) principal);
   }

}
