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
package inetsoft.sree.security;

import inetsoft.util.Tuple3;

import java.util.List;
import java.util.Objects;

/**
 * {@code AuthorizationChain} wraps a list of authorization providers. When getting a permission,
 * it tries each provider in sequence, returning the first non-null result. When setting a
 * permission, it checks each provider in sequence to see if the permission is already defined in a
 * provider. If an existing permission is found, it is set on the provider from which it was
 * obtained. If an existing permission is not found, it is set on the first provider in the chain.
 * When removing a permission, the permission is removed from all providers in the list.
 */
public class AuthorizationChain
   extends SecurityChain<AuthorizationProvider> implements AuthorizationProvider
{
   public AuthorizationChain() {
      super();
      initialize();
   }

   @Override
   public void setPermission(ResourceType type, String resource, Permission perm) {
      for(AuthorizationProvider provider : getProviderList()) {
         if(provider.getPermission(type, resource) != null) {
            provider.setPermission(type, resource, perm);
            return;
         }
      }

      // not found in any provider, set it on the first
      getProviderList().get(0).setPermission(type, resource, perm);
   }

   @Override
   public void setPermission(ResourceType type, IdentityID identityID, Permission perm) {
      for(AuthorizationProvider provider : getProviderList()) {
         if(provider.getPermission(type, identityID) != null) {
            provider.setPermission(type, identityID, perm);
            return;
         }
      }

      // not found in any provider, set it on the first
      getProviderList().get(0).setPermission(type, identityID, perm);
   }

   @Override
   public void removePermission(ResourceType type, String resource) {
      stream()
         .forEach(p -> p.removePermission(type, resource));
   }

   @Override
   public void removePermission(ResourceType type, IdentityID resourceID) {
      stream()
         .forEach(p -> p.removePermission(type, resourceID));
   }

   @Override
   public Permission getPermission(ResourceType type, String resource) {
      return stream()
         .map(p -> p.getPermission(type, resource))
         .filter(Objects::nonNull)
         .findFirst()
         .orElse(null);
   }

   @Override
   public Permission getPermission(ResourceType type, IdentityID identityID) {
      return stream()
         .map(p -> p.getPermission(type, identityID))
         .filter(Objects::nonNull)
         .findFirst()
         .orElse(null);
   }

   @Override
   public List<Tuple3<ResourceType, String, Permission>> getPermissions() {
      for(AuthorizationProvider provider : getProviders()) {
         try {
            return provider.getPermissions();
         }
         catch(UnsupportedOperationException e) {
            // no-op
         }
      }

      throw new UnsupportedOperationException();
   }

   public void cleanOrganizationFromPermissions(String orgId) {
      for(AuthorizationProvider provider : getProviders()) {
         try {
            if(provider instanceof FileAuthorizationProvider) {
               ((FileAuthorizationProvider) provider).cleanOrganizationFromPermissions(orgId);
            }
         }
         catch(UnsupportedOperationException e) {
            // no-op
         }
      }
   }

   @Override
   public boolean supportGroupPermission() {
      return stream()
         .map(AuthorizationProvider::supportGroupPermission)
         .filter(f -> f)
         .findFirst()
         .orElse(false);
   }

   @Override
   public void tearDown() {
      dispose();
   }

   @Override
   public void authenticationChanged(AuthenticationChangeEvent event) {
      stream().forEach(p -> p.authenticationChanged(event));
   }

   @Override
   String getConfigFile() {
      return "authz-chain.json";
   }

   @Override
   void dispose(AuthorizationProvider provider) {
      provider.tearDown();
   }

   @Override
   public String getProviderName() {
      return chainName;
   }

   @Override
   public void setProviderName(String providerName) {
      this.chainName = providerName;
   }

   @Override
   public boolean isCacheEnabled() {
      return stream().anyMatch(AuthorizationProvider::isCacheEnabled);
   }

   @Override
   public boolean isLoading() {
      return stream().anyMatch(AuthorizationProvider::isLoading);
   }

   @Override
   public void clearCache() {
      stream().forEach(AuthorizationProvider::clearCache);
   }

   @Override
   public long getCacheAge() {
      return stream().mapToLong(AuthorizationProvider::getCacheAge).max().orElse(0L);
   }

   private String chainName = "Authorization Chain";
}
