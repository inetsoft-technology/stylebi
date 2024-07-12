/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.security;

import inetsoft.sree.security.*;
import inetsoft.util.data.MapModel;
import inetsoft.report.internal.Util;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.admin.general.model.DatabaseSettingsModel;
import inetsoft.web.factory.DecodePathVariable;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.security.DeniedMultiTenancyOrgUser;

import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthenticationProviderController {
   @Autowired
   public AuthenticationProviderController(AuthenticationProviderService authenticationProviderService,
                                           DatabaseSettingsService databaseSettingsService)
   {
      this.authenticationProviderService = authenticationProviderService;
      this.databaseSettingsService = databaseSettingsService;
   }

   @GetMapping("/api/em/security/configured-authentication-providers")
   public SecurityProviderStatusList getConfiguredAuthenticationProviders() {
      return authenticationProviderService.getProviderListModel();
   }

   @GetMapping("/api/em/security/get-authentication-provider/{providerName}")
   @DeniedMultiTenancyOrgUser
   public AuthenticationProviderModel getAuthenticationProvider(@DecodePathVariable("providerName") String providerName) {
      return authenticationProviderService.getAuthenticationProvider(providerName);
   }

   @PostMapping("/api/em/security/add-authentication-provider")
   @DeniedMultiTenancyOrgUser
   public void addAuthenticationProvider(@RequestBody AuthenticationProviderModel model, Principal principal)
      throws Exception
   {
      authenticationProviderService.addAuthenticationProvider(model, model.providerName(), principal);
   }

   @PostMapping("/api/em/security/edit-authentication-provider/{providerName}")
   @DeniedMultiTenancyOrgUser
   public void editAuthenticationProvider(@PathVariable("providerName") String providerName,
                                          @RequestBody AuthenticationProviderModel model,
                                          Principal principal)
      throws Exception
   {
      authenticationProviderService.editAuthenticationProvider(providerName, model, principal);
   }

   @DeleteMapping("/api/em/security/remove-authentication-provider/{index}")
   @DeniedMultiTenancyOrgUser
   public void removeAuthenticationProvider(@PathVariable("index") int index, Principal principal)
      throws Exception
   {
      AuthenticationChain chain = authenticationProviderService.getAuthenticationChain()
         .orElseThrow(() -> new Exception("The authentication chain has not been initialized."));

      List<AuthenticationProvider> providerList = chain.getProviders();
      String providerName = providerList.get(index).getProviderName();
      authenticationProviderService.removeAuthenticationProvider(index, providerName, principal);
   }

   @GetMapping("/api/em/security/get-current-authentication-provider")
   public SecurityProviderStatus getCurrentProvider(Principal principal) {
      return authenticationProviderService.getCurrentProvider(principal);
   }

   @PostMapping("/api/em/security/reorder-authentication-providers")
   @DeniedMultiTenancyOrgUser
   public void reorderAuthenticationProviders(@RequestBody ProviderListReorderModel reorderModel)
      throws Exception
   {
      authenticationProviderService.reorderAuthenticationProviders(reorderModel);
   }

   @GetMapping("/api/em/security/clear-authentication-provider/{index}")
   @DeniedMultiTenancyOrgUser
   public SecurityProviderStatus clearAuthenticationProviderCache(@PathVariable("index") int index)
      throws Exception
   {
      return authenticationProviderService.clearAuthenticationProviderCache(index);
   }

   @GetMapping("/api/em/security/copy-authentication-provider/{providerName}")
   @DeniedMultiTenancyOrgUser
   public SecurityProviderStatus copyAuthenticationProvider(@PathVariable("providerName") String providerName,
                                                            Principal principal)
      throws Exception
   {
      ImmutableAuthenticationProviderModel authenticationProvider =
         (ImmutableAuthenticationProviderModel) authenticationProviderService.getAuthenticationProvider(providerName);
      String copyName = Util.getCopyName(providerName);
      List<String> providerNames = authenticationProviderService.getProviderListModel().providers().stream()
         .map((providerStatus) -> providerStatus.name()).collect(Collectors.toList());

      while(true) {
         if(providerNames.contains(copyName)) {
            copyName = Util.getNextCopyName(providerName, copyName);
            continue;
         }

         break;
      }

      authenticationProvider = authenticationProvider.withProviderName(copyName);
      authenticationProviderService
         .addAuthenticationProvider(authenticationProvider, copyName, principal);

      return SecurityProviderStatus.builder()
         .from(authenticationProviderService.getProviderByName(copyName))
         .build();
   }

   @PostMapping("/api/em/security/get-connection-status")
   @DeniedMultiTenancyOrgUser
   public ConnectionStatus getConnectionStatus(@RequestBody AuthenticationProviderModel model)
      throws Exception
   {
      String status = authenticationProviderService.testConnection(model);
      return new ConnectionStatus(status);
   }

   @PostMapping("/api/em/security/get-database-connection-status")
   @DeniedMultiTenancyOrgUser
   public ConnectionStatus getDatabaseConnectionStatus(@RequestBody AuthenticationProviderModel model,
                                                       Principal principal)
   {
      DatabaseAuthenticationProviderModel dbProviderModel = model.dbProviderModel();
      DatabaseSettingsModel databaseSettingsModel = DatabaseSettingsModel.builder()
         .driver(Objects.requireNonNull(dbProviderModel).driver())
         .databaseURL(dbProviderModel.url())
         .requiresLogin(dbProviderModel.requiresLogin())
         .username(dbProviderModel.user())
         .password(dbProviderModel.password())
         .defaultDB("")
         .build();
      return databaseSettingsService.testConnection(databaseSettingsModel, principal);
   }

   @PostMapping("/api/em/security/get-users")
   @DeniedMultiTenancyOrgUser
   public IdentityListModel getUsers(@RequestBody AuthenticationProviderModel model)
      throws Exception
   {
      return authenticationProviderService.getUsers(model);
   }

   @PostMapping("/api/em/security/get-user/**")
   @DeniedMultiTenancyOrgUser
   public MapModel<String, Object> getUser(@RemainingPath String userKey,
                                           @RequestBody AuthenticationProviderModel model)
      throws Exception
   {
      return authenticationProviderService.getUser(model, IdentityID.getIdentityIDFromKey(userKey));
   }

   @PostMapping("/api/em/security/get-user-emails/**")
   @DeniedMultiTenancyOrgUser
   public IdentityListModel getUserEmails(@RemainingPath String userName,
                                          @RequestBody AuthenticationProviderModel model)
      throws Exception
   {
      IdentityID uid = IdentityID.getIdentityIDFromKey(userName);
      return authenticationProviderService.getUserEmails(model, uid);
   }

   @PostMapping("/api/em/security/get-groups")
   @DeniedMultiTenancyOrgUser
   public IdentityListModel getGroups(@RequestBody AuthenticationProviderModel model)
      throws Exception
   {
      return authenticationProviderService.getGroups(model);
   }

   @PostMapping("/api/em/security/get-organizations")
   @DeniedMultiTenancyOrgUser
   public IdentityListModel getOrganizations(@RequestBody AuthenticationProviderModel model)
      throws Exception
   {
      return authenticationProviderService.getOrganizations(model);
   }

   @PostMapping("/api/em/security/get-organizationId/**")
   @DeniedMultiTenancyOrgUser
   public String getOrganizationId(@RemainingPath String name,
                                              @RequestBody AuthenticationProviderModel model)
      throws Exception
   {
      return authenticationProviderService.getOrganizationId(model, name);
   }

   @PostMapping("/api/em/security/group/users/**")
   @DeniedMultiTenancyOrgUser
   public IdentityListModel getGroupUsers(@RemainingPath String group,
                                          @RequestBody AuthenticationProviderModel model)
      throws Exception
   {
      IdentityID gId = IdentityID.getIdentityIDFromKey(group);
      return authenticationProviderService.getGroupUsers(model, gId);
   }

   @PostMapping("/api/em/security/organization-members/**")
   @DeniedMultiTenancyOrgUser
   public IdentityListModel getOrganizationMembers(@RemainingPath String org,
                                          @RequestBody AuthenticationProviderModel model)
      throws Exception
   {
      return authenticationProviderService.getOrganizationMembers(model, org);
   }

   @PostMapping("/api/em/security/get-roles")
   @DeniedMultiTenancyOrgUser
   public IdentityListModel getRolesForNewProvider(@RequestBody AuthenticationProviderModel model)
      throws Exception
   {
      return authenticationProviderService.getRoles(model);
   }

   @PostMapping("/api/em/security/get-roles/**")
   @DeniedMultiTenancyOrgUser
   public IdentityListModel getUserRoles(@RemainingPath String userKey,
                                         @RequestBody AuthenticationProviderModel model)
      throws Exception
   {
      IdentityID userID = IdentityID.getIdentityIDFromKey(userKey);
      return authenticationProviderService.getUserRoles(model, userID);
   }

   @GetMapping("/api/em/security/get-default-organization")
   public String getDefaultOrganization() {
      return Organization.getDefaultOrganizationName();
   }

   private final AuthenticationProviderService authenticationProviderService;
   private final DatabaseSettingsService databaseSettingsService;
}
