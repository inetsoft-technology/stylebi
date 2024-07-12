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
package inetsoft.sree.security;

import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.user.IdentityThemeService;

import java.security.Principal;

/**
 * Interface for authentication providers that allow their users, groups, and
 * roles to be modified.
 *
 * @author InetSoft Technology
 * @since  8.5
 */
public interface EditableAuthenticationProvider extends AuthenticationProvider {
   /**
    * Add a user to the system.
    *
    * @param user the user to add.
    */
   void addUser(User user);

   /**
    * Add a group to the system.
    *
    * @param group the group to add.
    */
   void addGroup(Group group);

   /**
    * Add a role to the system.
    *
    * @param role the role to add.
    */
   void addRole(Role role);

   /**
    * Add an organization to the system.
    *
    * @param organization the organization to add.
    */
   void addOrganization(Organization organization);

   /**
    * copy one organization's details and save new Organization
    *
    * @param fromOrganization the organization to copy from.
    * @param newOrgName the organization name of the newly created org
    */
   void copyOrganization(Organization fromOrganization, String newOrgName, IdentityService identityService,
                         IdentityThemeService themeService, Principal principal);

   /**
    * Set user.
    *
    * @param oldIdentity old user name.
    * @param user        the new user.
    */
   void setUser(IdentityID oldIdentity, User user);

   /**
    * Set group.
    *
    * @param oldIdentity old group name.
    * @param group       the new group.
    */
   void setGroup(IdentityID oldIdentity, Group group);

   /**
    * Set role.
    *
    * @param oldIdentity old role name.
    * @param role        the new role.
    */
   void setRole(IdentityID oldIdentity, Role role);

   void setOrganization(String oname, Organization org);

   /**
    * Remove a user from the system.
    *
    * @param userIdentity the name of the user to remove.
    */
   void removeUser(IdentityID userIdentity);

   /**
    * Remove a group from the system.
    *
    * @param groupIdentity the name of the group to remove.
    */
   void removeGroup(IdentityID groupIdentity);

   /**
    * Remove a role from the system.
    *
    * @param roleIdentity the name of the role to remove.
    */
   void removeRole(IdentityID roleIdentity);

   void removeOrganization(String name);

   /**
    * Change the password for an entity. It is supportted only on security
    * realms that use passwords.
    *
    * @param userIdentity the unique identifier of the user.
    * @param password     the new password.
    *
    * @throws SRSecurityException if the password could not be changed.
    */
   void changePassword(IdentityID userIdentity, String password)
      throws SRSecurityException;   
   
   /**
    * Adds a listener that is notified when a security object is removed or
    * renamed.
    *
    * @param l the listener to add.
    */
   void addAuthenticationChangeListener(AuthenticationChangeListener l);
   
   /**
    * Removes a change listener from the notification list.
    *
    * @param l the listener to remove.
    */
   void removeAuthenticationChangeListener(AuthenticationChangeListener l);
}
