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
package inetsoft.web.admin.security;

import inetsoft.report.internal.Util;
import inetsoft.sree.security.AuthorizationChain;
import inetsoft.sree.security.AuthorizationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class AuthorizationProviderController {
   @Autowired
   public AuthorizationProviderController(AuthorizationProviderService authorizationProviderService) {
      this.authorizationProviderService = authorizationProviderService;
   }

   @GetMapping("/api/em/security/configured-authorization-providers")
   public SecurityProviderStatusList getConfiguredAuthorizationProviders() {
      return authorizationProviderService.getProviderListModel();
   }

   @GetMapping("/api/em/security/get-authorization-provider/{providerName}")
   public AuthorizationProviderModel getAuthorizationProvider(@PathVariable("providerName") String providerName) {
      return authorizationProviderService.getAuthorizationProvider(providerName);
   }

   @PostMapping("/api/em/security/add-authorization-provider")
   public void addAuthorizationProvider(@RequestBody AuthorizationProviderModel model, Principal principal) throws Exception {
      authorizationProviderService.addAuthorizationProvider(model, model.providerName(), principal);
   }

   @PostMapping("/api/em/security/edit-authorization-provider/{providerName}")
   public void editAuthorizationProvider(@PathVariable("providerName") String providerName,
                                         @RequestBody AuthorizationProviderModel model, Principal principal) throws Exception
   {
      authorizationProviderService.editAuthorizationProvider(providerName, model, principal);
   }

   @DeleteMapping("/api/em/security/remove-authorization-provider/{index}")
   public void removeAuthorizationProvider(@PathVariable("index") int index, Principal principal) throws Exception {
      AuthorizationChain chain = authorizationProviderService.getAuthorizationChain()
         .orElseThrow(() -> new Exception("The authorization chain has not been initialized."));

      List<AuthorizationProvider> providerList = chain.getProviders();
      String providerName = providerList.get(index).getProviderName();
      authorizationProviderService.removeAuthorizationProvider(index, providerName, principal);
   }

   @PostMapping("/api/em/security/reorder-authorization-providers")
   public void reorderAuthorizationProviders(@RequestBody ProviderListReorderModel reorderModel) throws Exception {
      authorizationProviderService.reorderAuthorizationProviders(reorderModel);
   }

   @GetMapping("/api/em/security/clear-authorization-provider/{index}")
   public SecurityProviderStatus clearAuthorizationProviderCache(@PathVariable("index") int index)
      throws Exception
   {
      return authorizationProviderService.clearAuthorizationProviderCache(index);
   }

   @GetMapping("/api/em/security/copy-authorization-provider/{providerName}")
   public SecurityProviderStatus copyAuthorizationProviderCache(@PathVariable("providerName") String providerName,
                                                                Principal principal)
      throws Exception
   {
      ImmutableAuthorizationProviderModel authorizationProvider =
         (ImmutableAuthorizationProviderModel) authorizationProviderService.getAuthorizationProvider(providerName);
      String copyName = Util.getCopyName(providerName);
      List<String> providerNames = authorizationProviderService.getProviderListModel().providers().stream()
         .map((providerStatus) -> providerStatus.name()).collect(Collectors.toList());

      while(true) {
         if(providerNames.contains(copyName)) {
            copyName = Util.getNextCopyName(providerName, copyName);
            continue;
         }

         break;
      }

      authorizationProvider = authorizationProvider.withProviderName(copyName);
      authorizationProviderService.addAuthorizationProvider(authorizationProvider, copyName, principal);

      return SecurityProviderStatus.builder()
         .from(authorizationProviderService.getProviderByName(copyName))
         .build();
   }

   private final AuthorizationProviderService authorizationProviderService;
}
