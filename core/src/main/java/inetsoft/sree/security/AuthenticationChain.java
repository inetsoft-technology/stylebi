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

import inetsoft.sree.internal.SUtil;
import inetsoft.uql.util.Identity;

import java.util.*;

/**
 * {@code AuthenticationChain} wraps a list of authentication providers. When authorizing or getting
 * a user, group or role, it tries each provider in sequence, returning the first successful or
 * non-null result. When listing users, groups and roles, it returns the union of those returned by
 * the providers, in no particular order.
 */
public class AuthenticationChain
   extends SecurityChain<AuthenticationProvider> implements AuthenticationProvider
{
   public AuthenticationChain() {
      super();
      initialize();
   }

   @Override
   public User getUser(IdentityID userIdentity) {
      return stream()
         .map(p -> p.getUser(userIdentity))
         .filter(Objects::nonNull)
         .findFirst()
         .orElseGet(() -> getExternalUser(userIdentity));
   }

   @Override
   public Organization getOrganization(String id) {
      return stream()
         .map(p -> p.getOrganization(id))
         .filter(Objects::nonNull)
         .findFirst()
         .orElse(null);
   }

   @Override
   public String getOrgIdFromName(String name) {
      for(String oid : getOrganizationIDs()) {
         if(getOrganization(oid).getName().equals(name)) {
            return oid;
         }
      }
      return null;
   }

   @Override
   public String getOrgNameFromID(String id) {
      return getOrganization(id) == null ? null : getOrganization(id).name;

   }

   private User getExternalUser(IdentityID name) {
      UserProvider provider = UserProvider.getInstance();

      if(provider != null) {
         return provider.getUser(name);
      }

      return null;
   }

   @Override
   public IdentityID[] getUsers() {
      return stream()
         .flatMap(p -> Arrays.stream(p.getUsers()))
         .distinct()
         .toArray(IdentityID[]::new);
   }

@Override
   public String[] getOrganizationIDs() {
      List<AuthenticationProvider> providers = getProviders();

      if(providers == null || providers.isEmpty()) {
         return new String[0];
      }

      return stream()
         .flatMap(p -> Arrays.stream(p.getOrganizationIDs()))
         .distinct()
         .toArray(String[]::new);
   }

   @Override
   public String[] getOrganizationNames() {
      List<AuthenticationProvider> providers = getProviders();

      if(providers == null || providers.isEmpty()) {
         return new String[0];
      }

      return stream()
         .flatMap(p -> Arrays.stream(p.getOrganizationNames()))
         .distinct()
         .toArray(String[]::new);
   }

   @Override
   public IdentityID[] getUsers(IdentityID groupIdentity) {
      return stream()
         .flatMap(p -> Arrays.stream(p.getUsers(groupIdentity)))
         .distinct()
         .toArray(IdentityID[]::new);
   }

   @Override
   @Deprecated
   public String[] getEmails(IdentityID userIdentity) {
      return stream()
         .flatMap(p -> Arrays.stream(p.getEmails(userIdentity)))
         .distinct()
         .toArray(String[]::new);
   }

   @Override
   public IdentityID[] getIndividualUsers() {
      return stream()
         .flatMap(p -> Arrays.stream(p.getIndividualUsers()))
         .distinct()
         .toArray(IdentityID[]::new);
   }

   @Override
   public IdentityID[] getRoles() {
      return stream()
         .flatMap(p -> Arrays.stream(p.getRoles()))
         .distinct()
         .toArray(IdentityID[]::new);
   }

   public IdentityID[] getProviderRoles(String providerName) {
      return stream()
         .filter(p -> p.getProviderName().equals(providerName))
         .flatMap(p -> Arrays.stream(p.getRoles()))
         .distinct()
         .toArray(IdentityID[]::new);
   }

   @Override
   public IdentityID[] getRoles(IdentityID roleIdentity) {
      Optional<IdentityID[]> roles = stream()
         .map(p -> p.getRoles(roleIdentity))
         .filter(proleList -> proleList.length > 0)
         .findFirst();

      if(roles.isPresent()) {
         return roles.stream().flatMap(Arrays::stream)
            .distinct()
            .filter(role -> isRoleVisible(role))
            .toArray(IdentityID[]::new);
      }
      else {
         User externalUser = getExternalUser(roleIdentity);
         return externalUser == null ? new IdentityID[0] : externalUser.getRoles();
      }
   }

   private boolean isRoleVisible(IdentityID role) {
      if(!SUtil.isMultiTenant() && "Organization Administrator".equals(role.name)) {
         return false;
      }

      return true;
   }

   @Override
   public Role getRole(IdentityID roleIdentity) {
      return stream()
         .map(p -> p.getRole(roleIdentity))
         .filter(Objects::nonNull)
         .findFirst()
         .orElse(null);
   }

   public Role getProviderRole(IdentityID roleid, String providerName) {
      return stream()
         .filter(p -> p.getProviderName().equals(providerName))
         .map(p -> p.getRole(roleid))
         .filter(Objects::nonNull)
         .findFirst()
         .orElse(null);
   }

   @Override
   public Group getGroup(IdentityID groupIdentity) {
      return stream()
         .map(p -> p.getGroup(groupIdentity))
         .filter(Objects::nonNull)
         .findFirst()
         .orElse(null);
   }

   @Override
   public IdentityID[] getGroups() {
      return stream()
         .flatMap(p -> Arrays.stream(p.getGroups()))
         .distinct()
         .toArray(IdentityID[]::new);
   }

   @Override
   public String[] getUserGroups(IdentityID userId) {
      String[] groups = stream()
         .flatMap(p -> Arrays.stream(p.getUserGroups(userId)))
         .distinct()
         .toArray(String[]::new);

      if(groups.length > 0) {
         return groups;
      }
      else {
         User externalUser = getExternalUser(userId);
         return externalUser == null ? new String[0] : externalUser.getGroups();
      }
   }

   @Override
   public String[] getGroupParentGroups(IdentityID group) {
      return stream()
         .flatMap(p -> Arrays.stream(p.getGroupParentGroups(group)))
         .distinct()
         .toArray(String[]::new);
   }

   @Override
   public boolean authenticate(IdentityID userIdentity, Object credential) {
      for(AuthenticationProvider provider : getProviders()) {
         // Use the first provider that contains the user to authenticate. This is intentionally
         // different from checking if the authentication is successful with any provider.
         if(provider.getUser(userIdentity) != null) {
            return provider.authenticate(userIdentity, credential);
         }
      }

      return false;
   }

   @Override
   public Identity findIdentity(Identity identity) {
      return stream()
         .map(p -> p.findIdentity(identity))
         .filter(Objects::nonNull)
         .findFirst()
         .orElse(null);
   }

   @Override
   public String[] getIndividualEmailAddresses() {
      return stream()
         .flatMap(p -> Arrays.stream(p.getIndividualEmailAddresses()))
         .distinct()
         .toArray(String[]::new);
   }

   @Override
   public boolean isAuthenticationCaseSensitive() {
      // return false if any is not case sensitive.
      for(AuthenticationProvider provider : getProviderList()) {
         if(!provider.isAuthenticationCaseSensitive()) {
            return false;
         }
      }

      return true;
   }

   @Override
   public boolean isVirtual() {
      // return false if any is not virtual
      for(AuthenticationProvider provider : getProviderList()) {
         if(!provider.isVirtual()) {
            return false;
         }
      }

      return true;
   }

   @Override
   public void checkParameters() throws SRSecurityException {
      for(AuthenticationProvider provider : getProviderList()) {
         provider.checkParameters();
      }
   }

   @Override
   public boolean isCacheEnabled() {
      return stream().anyMatch(AuthenticationProvider::isCacheEnabled);
   }

   @Override
   public boolean isLoading() {
      return stream().anyMatch(AuthenticationProvider::isLoading);
   }

   @Override
   public void clearCache() {
      stream().forEach(AuthenticationProvider::clearCache);
   }

   @Override
   public long getCacheAge() {
      return stream().mapToLong(AuthenticationProvider::getCacheAge).max().orElse(0L);
   }

   @Override
   public void tearDown() {
      synchronized(listeners) {
         listeners.clear();
      }

      dispose();
   }

   @Override
   String getConfigFile() {
      return "authc-chain.json";
   }

   @Override
   void dispose(AuthenticationProvider provider) {
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
   void setProviderList(List<AuthenticationProvider> list) {
      stream()
         .filter(EditableAuthenticationProvider.class::isInstance)
         .map(EditableAuthenticationProvider.class::cast)
         .forEach(l -> l.removeAuthenticationChangeListener(changeDelegate));

      super.setProviderList(list);

      stream()
         .filter(EditableAuthenticationProvider.class::isInstance)
         .map(EditableAuthenticationProvider.class::cast)
         .forEach(l -> l.addAuthenticationChangeListener(changeDelegate));
   }

   @Override
   public boolean isSystemAdministratorRole(IdentityID roleId) {
      return stream().filter(p -> p.getRole(roleId) != null).findFirst().stream().anyMatch(p -> p.isSystemAdministratorRole(roleId));
   }

   @Override
   public boolean isOrgAdministratorRole(IdentityID roleId) {
      return stream().filter(p -> p.getRole(roleId) != null).findFirst().stream().anyMatch(p -> p.isOrgAdministratorRole(roleId));
   }

   @Override
   public boolean containsAnonymousUser(String orgId) {
      return stream().anyMatch(p -> p.containsAnonymousUser(orgId));
   }

   /**
    * Adds a listener that is notified whenever an {@link EditableAuthenticationProvider} in this
    * chain has been modified.
    *
    * @param l the listener to add.
    */
   public void addAuthenticationChangeListener(AuthenticationChangeListener l) {
      synchronized(listeners) {
         listeners.add(l);
      }
   }

   /**
    * Removes a listener from the notification list.
    *
    * @param l the listener to remove.
    *
    * @see #addAuthenticationChangeListener(AuthenticationChangeListener)
    */
   public void removeAuthenticationChangeListener(AuthenticationChangeListener l) {
      synchronized(listeners) {
         listeners.remove(l);
      }
   }

   private String chainName = "Authentication Chain";
   private final Set<AuthenticationChangeListener> listeners = new LinkedHashSet<>();

   private final AuthenticationChangeListener changeDelegate = event -> {
      synchronized(listeners) {
         listeners.forEach(l -> l.authenticationChanged(event));
      }
   };
}
