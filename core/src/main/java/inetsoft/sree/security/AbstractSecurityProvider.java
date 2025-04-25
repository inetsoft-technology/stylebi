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

import inetsoft.uql.util.Identity;
import inetsoft.util.Tuple4;

import java.security.Principal;
import java.util.List;

/**
 * A skeletal implementation of a security provider.
 *
 * @author InetSoft Technology
 * @since 5.1
 */
public abstract class AbstractSecurityProvider implements SecurityProvider {
   /**
    * Creates a new instance of AbstractSecurityProvider.
    *
    * @param authentication the authentication provider.
    * @param authorization  the authorization provider.
    */
   public AbstractSecurityProvider(AuthenticationProvider authentication,
                                   AuthorizationProvider authorization)
   {
      this.authentication = authentication;
      this.authorization = authorization;

      if(authentication instanceof EditableAuthenticationProvider) {
         ((EditableAuthenticationProvider) authentication).
            addAuthenticationChangeListener(authorization);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public AuthenticationProvider getAuthenticationProvider() {
      return authentication;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public AuthorizationProvider getAuthorizationProvider() {
      return authorization;
   }

   /**
    * Gets a list of all users in the system.
    *
    * @return a list of users.
    */
   @Override
   public IdentityID[] getUsers() {
      return authentication.getUsers();
   }

   /**
    * Get a user by name.
    *
    * @param userIdentity the unique identifier of the user.
    *
    * @return the User object that encapsulates the properties of the user.
    */
   @Override
   public User getUser(IdentityID userIdentity) {
      return authentication.getUser(userIdentity);
   }

   /**
    * Gets a list of all Organization ids in the system.
    *
    * @return an array of Organizations.
    */
   @Override
   public String[] getOrganizationIDs() {
      return authentication.getOrganizationIDs();
   }

   /**
    * Gets a list of all Organization names in the system.
    *
    * @return an array of Organizations.
    */
   @Override
   public String[] getOrganizationNames() {
      return authentication.getOrganizationNames();
   }

   /**
    * Get an Organization by id.
    *
    * @param id the unique identifier of the Organization.
    *
    * @return the Organization object that encapsulates the properties of the organization.
    */
   @Override
   public Organization getOrganization(String id) {
      return authentication.getOrganization(id);
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
      return id == null || getOrganization(id) == null ? null : getOrganization(id).name;

   }

   /**
    * Get a list of all emails for a user.
    *
    * @param userIdentity the unique identifier for the user.
    * @return list of emails.
    * @deprecated use {@link inetsoft.sree.security.User#getEmails()} instead.
    */
   @Override
   @Deprecated
   @SuppressWarnings("deprecation")
   public String[] getEmails(IdentityID userIdentity) {
      return authentication.getEmails(userIdentity);
   }

   /**
    * Get a group by name.
    *
    * @param groupIdentity the name of the group.
    * @return the named group or <code>null</code> if no such group exists.
    */
   @Override
   public Group getGroup(IdentityID groupIdentity) {
      return authentication.getGroup(groupIdentity);
   }

   /**
    * Get a list of all users in a group.
    *
    * @param groupIdentity the name of the group.
    *
    * @return list of users
    */
   @Override
   public IdentityID[] getUsers(IdentityID groupIdentity) {
      return authentication.getUsers(groupIdentity);
   }

   /**
    * Get a list of all users not in any group except INDIVIDUAL.
    *
    * @return list of users
    */
   @Override
   public IdentityID[] getIndividualUsers() {
      return authentication.getIndividualUsers();
   }

   /**
    * Get a list of all roles in the system.
    *
    * @return list of roles.
    */
   @Override
   public IdentityID[] getRoles() {
      return authentication.getRoles();
   }

   /**
    * Get a list of all roles bound to specific user,
    * include the 'Everyone' role.
    *
    * @param roleIdentity the unique identifier for the user.
    *
    * @return list of roles.
    */
   @Override
   public IdentityID[] getRoles(IdentityID roleIdentity) {
      return authentication.getRoles(roleIdentity);
   }

   /**
    * Get a role object from the role ID.
    *
    * @param name the name of the role.
    *
    * @return the named role object of <code>null</code> if no such role exists.
    */
   @Override
   public Role getRole(IdentityID name) {
      return authentication.getRole(name);
   }

   /**
    * Get a list of all groups defined in the system. If groups are nested,
    * only the top level groups will be returned.
    *
    * @return list of groups.
    */
   @Override
   public IdentityID[] getGroups() {
      return authentication.getGroups();
   }

   @Override
   public String[] getUserGroups(IdentityID userId) {
      return authentication.getUserGroups(userId);
   }

   @Override
   public String[] getGroupParentGroups(IdentityID group) {
      return authentication.getGroupParentGroups(group);
   }

   @Override
   public Identity[] getGroupMembers(IdentityID groupIdentity) {
      return authentication.getGroupMembers(groupIdentity);
   }

   @Override
   public String[] getOrganizationMembers(String organizationID) {
      return authentication.getOrganizationMembers(organizationID);
   }

   @Override
   public Identity[] getRoleMembers(IdentityID roleIdentity) {
      return authentication.getRoleMembers(roleIdentity);
   }

   @Override
   public boolean isAuthenticationCaseSensitive() {
      return authentication.isAuthenticationCaseSensitive();
   }

   /**
    * Check the authentication of specific entity.
    *
    * @param userIdentity the unique identification of the user.
    * @param credential   a wrapper for some secure message, such as the user ID
    *                     and password.
    *
    * @return <code>true</code> if the authentication succeeded.
    */
   @Override
   public boolean authenticate(IdentityID userIdentity, Object credential) {
      if(!authentication.isAuthenticationCaseSensitive()) {
         IdentityID[] users = getUsers();

         // @by billh, fix customer bug bug1305742936302
         // ldap user check is case insensitive, here check it in case-sensitive
         if(users != null) {
            boolean found = false;

            for(IdentityID user1 : users) {
               if(userIdentity.equals(user1)) {
                  found = true;
                  break;
               }
            }

            if(!found) {
               return false;
            }
         }
      }

      return authentication.authenticate(userIdentity, credential);
   }

   @Override
   public void setPermission(ResourceType type, String resource, Permission perm, String orgID) {
      authorization.setPermission(type, resource, perm, orgID);
   }

   @Override
   public void setPermission(ResourceType type, IdentityID identityID, Permission perm, String orgID) {
      authorization.setPermission(type, identityID, perm, orgID);
   }

   @Override
   public void removePermission(ResourceType type, String resource, String orgID) {
      authorization.removePermission(type, resource, orgID);
   }

   @Override
   public void removePermission(ResourceType type, IdentityID resource, String orgID) {
      authorization.removePermission(type, resource, orgID);
   }

   @Override
   public Permission getPermission(ResourceType type, String resource, String orgID) {
      return authorization.getPermission(type, resource, orgID);
   }

   @Override
   public Permission getPermission(ResourceType type, IdentityID identityID, String orgID) {
      return authorization.getPermission(type, identityID, orgID);
   }

   @Override
   public List<Tuple4<ResourceType, String, String, Permission>> getPermissions() {
      return authorization.getPermissions();
   }

   /**
    * Tear down the security provider.
    */
   @Override
   public void tearDown() {
      authentication.tearDown();
      authorization.tearDown();
   }

   @Override
   public boolean checkPermission(Principal principal, ResourceType type, String resource,
                                  ResourceAction action)
   {
      return checkPermissionStrategy.checkPermission(principal, type, resource, action);
   }

   /**
    * Signals that a security object has been removed or renamed.
    *
    * @param event the object that describes the change event.
    */
   @Override
   public void authenticationChanged(AuthenticationChangeEvent event) {
   }

   /**
    * Find the concrete identity in this security provider.
    *
    * @return the identity found in this security provider, <tt>null</tt>
    * otherewise.
    */
   @Override
   public final Identity findIdentity(Identity identity) {
      return authentication.findIdentity(identity);
   }

   /**
    * Check if provider support to allocate permission to group or not.
    */
   @Override
   public boolean supportGroupPermission() {
      return authorization.supportGroupPermission();
   }

   @Override
   public String[] getIndividualEmailAddresses() {
      return authentication.getIndividualEmailAddresses();
   }

   @Override
   public boolean isSystemAdministratorRole(IdentityID roleIdentity) {
      return authentication.isSystemAdministratorRole(roleIdentity);
   }

   @Override
   public boolean isOrgAdministratorRole(IdentityID roleIdentity) {
      return authentication.isOrgAdministratorRole(roleIdentity);
   }

   @Override
   public boolean isVirtual() {
      return authentication.isVirtual();
   }

   @Override
   public void checkParameters() throws SRSecurityException {
      authentication.checkParameters();
   }

   @Override
   public boolean isCacheEnabled() {
      return authentication.isCacheEnabled() || authorization.isCacheEnabled();
   }

   @Override
   public void clearCache() {
      authentication.clearCache();
      authorization.clearCache();
   }

   @Override
   public boolean isLoading() {
      return authentication.isLoading() || authorization.isLoading();
   }

   @Override
   public long getCacheAge() {
      return Math.max(authentication.getCacheAge(), authorization.getCacheAge());
   }

   @Override
   public String getProviderName() {
      return authentication.getProviderName() + "/" + authorization.getProviderName();
   }

   @Override
   public void setProviderName(String providerName) {
   }

   @Override
   public boolean containsAnonymousUser(String orgId) {
      return authentication.containsAnonymousUser(orgId);
   }

   public void setCheckPermissionStrategy(CheckPermissionStrategy checkPermissionStrategy) {
      this.checkPermissionStrategy = checkPermissionStrategy;
   }

   private AuthenticationProvider authentication;
   private AuthorizationProvider authorization;
   private CheckPermissionStrategy checkPermissionStrategy;
}
