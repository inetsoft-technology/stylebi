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
package inetsoft.sree.security;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.util.AbstractIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import inetsoft.web.admin.security.IdentityModel;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.user.IdentityThemeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.*;

/**
 * Skeleton implementation of an editable authentication module.
 *
 * @author InetSoft Technology
 * @since  8.5
 */
public abstract class AbstractEditableAuthenticationProvider
   extends AbstractAuthenticationProvider
   implements EditableAuthenticationProvider
{
   /**
    * Add a user to the system.
    *
    * @param user the user to add.
    */
   @Override
   public void addUser(User user) {
   }

   /**
    * Add a group to the system.
    *
    * @param group the group to add.
    */
   @Override
   public void addGroup(Group group) {
   }

   /**
    * Add a role to the system.
    *
    * @param role the role to add.
    */
   @Override
   public void addRole(Role role) {
   }

   /**
    * Add an organization to the system.
    *
    * @param organization the organization to add.
    */
   @Override
   public void addOrganization(Organization organization) {
   }

   /**
    * copy one organization's details and save new Organization
    *
    * @param fromOrganization the organization to copy from.
    * @param newOrgName the organization name of the newly created org
    */
   @Override
   public void copyOrganization(Organization fromOrganization, String newOrgName, IdentityService identityService,
                                IdentityThemeService themeService, Principal principal){
      FSOrganization newOrg = new FSOrganization(newOrgName);

      if(newOrgName.equals(Organization.getTemplateOrganizationName())) {
         //clear template before assigning new identities and storage
         if(getOrganization(newOrgName) != null) {
            cleanTemplateOrganization(identityService, principal);
            removeOrganization(newOrgName);
         }

         newOrg.setId(Organization.getTemplateOrganizationID());
      }
      else {
         newOrg.setId(newOrgName);
      }

      identityService.addCopiedIdentityPermission(fromOrganization.getIdentityID(), new IdentityID(newOrgName, newOrgName), newOrg.getId(), Identity.ORGANIZATION);

      copyScopedProperties(fromOrganization.getId(),newOrg.getId());

      copyDataSpace(fromOrganization.getId(), newOrg.getId());

      copyRootPermittedIdentities(fromOrganization, newOrgName, newOrg.getId(), identityService, principal);

      List<IdentityID> addedRoles = new ArrayList<IdentityID>();
      List<IdentityID> addedMembers = new ArrayList<IdentityID>();
      List<IdentityID> fromOrgRoles = Arrays.stream(fromOrganization.getRoles()).toList();

      for(IdentityID roleIdentity : getRoles()) {
         Role role = getRole(roleIdentity);

         if(role != null && fromOrganization.getName().equals(role.getOrganization())) {
            IdentityID newID = copyRoleToOrganization(roleIdentity, newOrgName, newOrg.getId(), fromOrganization.getId(), identityService, principal);

            if(newID != null && !newID.name.isEmpty()) {
               if(!newID.equals(roleIdentity)) {
                  identityService.addCopiedIdentityPermission(roleIdentity, newID, newOrg.getId(), Identity.ROLE);
                  addedMembers.add(newID);
               }
            }
            if(fromOrgRoles.contains(roleIdentity)) {
               addedRoles.add(newID);
            }
         }
      }

      for(IdentityID userID : getUsers()) {
         if(getUser(userID).getOrganization().equals(fromOrganization.getName())) {
            IdentityID newID = copyUserToOrganization(userID, newOrgName, newOrg.getId(), fromOrganization.getId(), identityService, principal);

            if(newID != null && !newID.name.isEmpty()) {
               identityService.addCopiedIdentityPermission(userID, newID, newOrg.getId(), Identity.USER);
               addedMembers.add(newID);
            }
         }
      }

      for(IdentityID groupID : getGroups()) {
         if(getGroup(groupID).getOrganization().equals(fromOrganization.getName())) {
            IdentityID newID = copyGroupToOrganization(groupID, newOrgName, newOrg.getId(), fromOrganization.getId(), identityService, principal);

            if(newID != null && !newID.name.isEmpty()) {
               identityService.addCopiedIdentityPermission(groupID, newID, newOrg.getId(), Identity.GROUP);
               addedMembers.add(newID);
            }
         }
      }

      newOrg.setMembers(addedMembers.stream().map(id -> id.name).toArray(String[]::new));
      newOrg.setProperties(fromOrganization.getProperties());
      newOrg.setLocale(fromOrganization.getLocale());
      newOrg.setTheme(fromOrganization.getTheme());

      addOrganization(newOrg);

      identityService.copyStorages(fromOrganization.getOrganizationID(),newOrg.getId());
      identityService.copyRepletRegistry(fromOrganization.getOrganizationID(), newOrg.getId());
      identityService.copyDashboardRegistry(fromOrganization.getOrganizationID(), newOrg.getId());
   }

   protected void clearScopedProperties(String oldOrgId) {
      //loop through properties, delete any containing .thisOrg.
      Properties properties = SreeEnv.getProperties();

      for(Enumeration<?> e = properties.propertyNames(); e.hasMoreElements();) {
         String pName = (String) e.nextElement();
         String oldOrgIdentifier = "inetsoft.org" + oldOrgId;
         if (pName.startsWith(oldOrgIdentifier)) {
            SreeEnv.remove(pName);
         }
      }
   }

   private void copyDataSpace(String fromOrgID, String toOrgID) {
      DataSpace dataspace = DataSpace.getDataSpace();
      String[] paths = DataSpace.getOrgScopedPaths(fromOrgID);

      for(String path : paths) {
         if(dataspace.isDirectory(path)) {
            String newPath = path.replace(fromOrgID, toOrgID);
            dataspace.copy(path, newPath);
         }
      }
   }


   private void copyScopedProperties(String fromOrgId, String newOrgId) {
      Properties properties = SreeEnv.getProperties();

      for(Enumeration<?> e = properties.propertyNames(); e.hasMoreElements();) {
         String pName = (String) e.nextElement();
         String oldOrgIdentifier = "inetsoft.org." + fromOrgId;
         if (pName.startsWith(oldOrgIdentifier)) {
            String baseName = pName.replace(oldOrgIdentifier,"");
            String updatedName = "inetsoft.org." + newOrgId  + baseName;
            SreeEnv.setProperty(updatedName, SreeEnv.getProperty(pName));
         }
      }
   }

   private void copyRootPermittedIdentities(Organization fromOrganization, String newOrgName, String newOrgID, IdentityService identityService, Principal principal) {
      //Users
      String rootUserName = Catalog.getCatalog().getString("Users");
      IdentityID fromRootUserID = new IdentityID(rootUserName, fromOrganization.getName());
      IdentityID toRootUserID = new IdentityID(rootUserName, newOrgName);
      List<IdentityModel> rootUserPIDs = identityService.getPermission(fromRootUserID, ResourceType.SECURITY_USER, fromOrganization.getId(), principal);

      identityService.setIdentityPermissions(toRootUserID, toRootUserID, ResourceType.SECURITY_USER, principal,
                                             copyPermittedIDs(rootUserPIDs, fromOrganization.getName(), newOrgName), newOrgName, newOrgID);
      //Groups
      String rootGroupName = Catalog.getCatalog().getString("Groups");
      IdentityID fromRootGroupID = new IdentityID(rootGroupName, fromOrganization.getName());
      IdentityID toRootGroupID = new IdentityID(rootGroupName, newOrgName);
      List<IdentityModel> rootGroupPIDs = identityService.getPermission(fromRootGroupID, ResourceType.SECURITY_GROUP, fromOrganization.getId(), principal);

      identityService.setIdentityPermissions(toRootGroupID, toRootGroupID, ResourceType.SECURITY_GROUP, principal,
                                             copyPermittedIDs(rootGroupPIDs, fromOrganization.getName(), newOrgName), newOrgName, newOrgID);
      //Roles
      String rootRoleName = Catalog.getCatalog().getString("Roles");
      IdentityID fromRootID = new IdentityID(rootRoleName, fromOrganization.getName());
      IdentityID toRootID = new IdentityID(rootRoleName, newOrgName);
      List<IdentityModel> rootRolePIDs = identityService.getPermission(fromRootID, ResourceType.SECURITY_ROLE, fromOrganization.getId(), principal);

      identityService.setIdentityPermissions(toRootID, toRootID, ResourceType.SECURITY_ROLE, principal,
                                             copyPermittedIDs(rootRolePIDs, fromOrganization.getName(), newOrgName), newOrgName, newOrgID);

      String rootOrgRoleName = Catalog.getCatalog().getString("Organization Roles");
      IdentityID fromRootOrgRoleID = new IdentityID(rootOrgRoleName, fromOrganization.getName());
      IdentityID toRootOrgRoleID = new IdentityID(rootOrgRoleName, newOrgName);
      List<IdentityModel> rootOrgRolePIDs = identityService.getPermission(fromRootOrgRoleID, ResourceType.SECURITY_ROLE, fromOrganization.getId(), principal);

      identityService.setIdentityPermissions(toRootOrgRoleID, toRootOrgRoleID, ResourceType.SECURITY_ROLE, principal,
                                             copyPermittedIDs(rootOrgRolePIDs, fromOrganization.getName(), newOrgName), newOrgName, newOrgID);
      //Organization
      IdentityID oldOrgID = new IdentityID(fromOrganization.getName(), fromOrganization.getName());
      IdentityID newOrgIdentityID = new IdentityID(newOrgName, newOrgName);
      List<IdentityModel> rootOrgPIDs = identityService.getPermission(oldOrgID, ResourceType.SECURITY_ORGANIZATION, fromOrganization.getId(), principal);

      identityService.setIdentityPermissions(newOrgIdentityID, newOrgIdentityID, ResourceType.SECURITY_ORGANIZATION, principal,
                                             copyPermittedIDs(rootOrgPIDs, fromOrganization.getName(), newOrgName), newOrgName, newOrgID);
   }

   public List<IdentityModel> copyPermittedIDs(List<IdentityModel> fromIDs, String fromOrgName, String newOrgName ) {
      List<IdentityModel> updatedPIds = new ArrayList<>();
      for(IdentityModel id : fromIDs) {
         switch(id.type()) {
         case Identity.USER:
            if(getUser(id.identityID()) != null && fromOrgName.equals(getUser(id.identityID()).getOrganization())) {
               IdentityID newName = new IdentityID(id.identityID().name, newOrgName);
               updatedPIds.add(IdentityModel.builder().identityID(newName).type(Identity.USER).build());
            }
            break;
         case Identity.GROUP:
            if(getGroup(id.identityID()) != null && fromOrgName.equals(getGroup(id.identityID()).getOrganization())) {
               IdentityID newName = new IdentityID(id.identityID().name, newOrgName);
               updatedPIds.add(IdentityModel.builder().identityID(newName).type(Identity.GROUP).build());
            }
            break;
         case Identity.ROLE:
            if(getRole(id.identityID()) != null && fromOrgName.equals(getRole(id.identityID()).getOrganization())) {
               IdentityID newName = new IdentityID(id.identityID().name, newOrgName);
               updatedPIds.add(IdentityModel.builder().identityID(newName).type(Identity.ROLE).build());
            }
            break;
         }
      }
      return updatedPIds;
   }

   private IdentityID copyRoleToOrganization(IdentityID roleIdentity, String orgName, String orgID, String fromOrgID, IdentityService identityService, Principal principal) {
      FSRole fromRole = (FSRole) getRole(roleIdentity);
      if(fromRole != null) {
         IdentityID newRoleID = new IdentityID(roleIdentity.name, orgName);

         if(newRoleID != null && !newRoleID.name.isEmpty()) {
            FSRole newRole = new FSRole(newRoleID);

            newRole.setOrganization(orgName);
            newRole.setRoles(copyIdentityRoles(fromRole, orgName));
            newRole.setDefaultRole(fromRole.isDefaultRole());
            newRole.setDesc(fromRole.getDescription());
            newRole.setSysAdmin(fromRole.isSysAdmin());
            newRole.setOrgAdmin(fromRole.isOrgAdmin());

            updatePermittedIdentities(Identity.ROLE, identityService, principal, roleIdentity, newRoleID, orgID, fromOrgID);
            addRole(newRole);

            return newRoleID;
         }
         else {
            return roleIdentity;
         }
      }
      else {
         return null;
      }
   }

   private IdentityID copyUserToOrganization(IdentityID memberID, String orgName, String orgID, String fromOrgID, IdentityService identityService, Principal principal) {
      User fromUser = getUser(memberID);

      if(fromUser != null) {
         IdentityID newID = new IdentityID(memberID.name, orgName);

         FSUser newUser = new FSUser(newID);
         newUser.setGroups(fromUser.getGroups());
         newUser.setAlias(fromUser.getAlias());
         newUser.setLocale(fromUser.getLocale());
         newUser.setActive(fromUser.isActive());
         newUser.setRoles(copyIdentityRoles(fromUser, orgName));
         newUser.setOrganization(orgName);
         HashedPassword hash = Tool.hash("success123", "bcrypt");
         newUser.setPassword(hash.getHash());
         newUser.setPasswordAlgorithm(hash.getAlgorithm());

         updatePermittedIdentities(Identity.USER, identityService, principal, memberID, newID, orgID, fromOrgID);
         addUser(newUser);

         return newID;
      }
      else
      {
         addUser(new FSUser(memberID));
         return memberID;
      }
   }

   private IdentityID copyGroupToOrganization(IdentityID memberID, String orgName, String orgID, String fromOrgID, IdentityService identityService, Principal principal) {
      Group fromGroup = getGroup(memberID);

      if(fromGroup != null) {
         IdentityID newID = new IdentityID(memberID.name, orgName);
         FSGroup newGroup = new FSGroup(newID);

         newGroup.setLocale(fromGroup.getLocale());
         newGroup.setGroups(fromGroup.getGroups());
         newGroup.setOrganization(orgName);
         newGroup.setRoles(copyIdentityRoles(fromGroup, orgName));

         updatePermittedIdentities(Identity.GROUP, identityService, principal, memberID, newID, orgID, fromOrgID);
         addGroup(newGroup);

         return newID;
      }
      else {
         return null;
      }
   }

   public void updatePermittedIdentities(int type, IdentityService identityService, Principal principal, IdentityID fromIdentity, IdentityID toIdentity, String newOrgID, String fromOrgID) {
      ResourceType rType;

      switch(type) {
      case Identity.GROUP:
         rType = ResourceType.SECURITY_GROUP;
         break;
      case Identity.ROLE:
         rType = ResourceType.SECURITY_ROLE;
         break;
      case Identity.ORGANIZATION:
         rType = ResourceType.SECURITY_ORGANIZATION;
         break;
      default:
         rType = ResourceType.SECURITY_USER;
      }
      List<IdentityModel> uPermIds = identityService.getPermission(fromIdentity, rType, fromOrgID, principal);
      List<IdentityModel> updatedUPermIds = copyPermittedIDs(uPermIds, fromIdentity.organization, toIdentity.organization);

      if(!updatedUPermIds.isEmpty()) {
         identityService.setIdentityPermissions(toIdentity, toIdentity,rType, principal,
                                                updatedUPermIds, toIdentity.organization, newOrgID);
      }
   }

   private IdentityID[] copyIdentityRoles(AbstractIdentity fromID, String orgName) {
      ArrayList<IdentityID> newRoles = new ArrayList<>();
      for(IdentityID roleName : fromID.getRoles()) {
         Role role = getRole(roleName);

         if (role != null) {
            boolean isGlobal = roleName.organization == null;

            if(isGlobal) {
               newRoles.add(roleName);
            }
            else {
               IdentityID newRoleID = new IdentityID(roleName.name, orgName);
               newRoles.add(newRoleID);
            }
         }
      }
      return newRoles.toArray(new IdentityID[0]);
   }

   public void cleanTemplateOrganization(IdentityService identityService, Principal principal) {
      String templateOrgName = Organization.getTemplateOrganizationName();
      String templateOrgID = Organization.getTemplateOrganizationID();
      try {
         identityService.updateIdentityPermissions(Identity.ORGANIZATION, new IdentityID(templateOrgName, templateOrgName), null, "", "", true);
         identityService.deleteOrganizationMembers(templateOrgName, this);
         clearScopedProperties(Organization.getTemplateOrganizationID());
         identityService.removeStorages(templateOrgID);
         identityService.removeOrgProperties(templateOrgID);
         identityService.removeOrgScopedDataSpaceElements(templateOrgID);
         identityService.clearRootPermittedIdentities(templateOrgName, templateOrgID, principal);
      }
      catch(Exception e) {
         LOG.warn("Could not clear Organization " + templateOrgName + ", " + e);
      }
   }

   /**
    * Set user.
    *
    * @param oldIdentity old user name.
    * @param user        the new user.
    */
   @Override
   public void setUser(IdentityID oldIdentity, User user) {
   }

   /**
    * Set group.
    *
    * @param oldIdentity old group name.
    * @param group       the new group.
    */
   @Override
   public void setGroup(IdentityID oldIdentity, Group group) {
   }

   /**
    * Set role.
    *
    * @param oldIdentity old role name.
    * @param role        the new role.
    */
   @Override
   public void setRole(IdentityID oldIdentity, Role role) {
   }

   @Override
   public void setOrganization(String oname, Organization organization) {
   }

   /**
    * Remove a user from the system.
    *
    * @param userIdentity the name of the user to remove.
    */
   @Override
   public void removeUser(IdentityID userIdentity) {
   }

   /**
    * Remove a group from the system.
    *
    * @param groupIdentity the name of the group to remove.
    */
   @Override
   public void removeGroup(IdentityID groupIdentity) {
   }

   /**
    * Remove a role from the system.
    *
    * @param roleIdentity the name of the role to remove.
    */
   @Override
   public void removeRole(IdentityID roleIdentity) {
   }

   @Override
   public void removeOrganization(String name) {
   }

   /**
    * Change the password for an entity. It is supported only on security
    * realms that use passwords.
    *
    * @param userIdentity the unique identifier of the user.
    * @param password     the new password.
    *
    * @throws SRSecurityException if the password could not be changed.
    */
   @Override
   public void changePassword(IdentityID userIdentity, String password)
      throws SRSecurityException
   {
   }

   /**
    * Adds a listener that is notified when a security object is removed or
    * renamed.
    *
    * @param l the listener to add.
    */
   @Override
   public void addAuthenticationChangeListener(AuthenticationChangeListener l) {
      listeners.add(l);
   }

   /**
    * Removes a change listener from the notification list.
    *
    * @param l the listener to remove.
    */
   @Override
   public void removeAuthenticationChangeListener
      (AuthenticationChangeListener l)
   {
      listeners.remove(l);
   }

   /**
    * Notifies all registered listeners that a security object has been removed
    * or renamed.
    *
    * @param oldIdentity the old name of the security object.
    * @param newIdentity the new name of the security object.
    * @param type        the type of the security object. The value of this parameter
    *                    must be one of the type constants defined in
    *                    {@link inetsoft.uql.util.Identity}.
    * @param removed     <code>true</code> if the security object has been removed;
    *                    <code>false</code> otherwise.
    */
   protected void fireAuthenticationChanged(IdentityID oldIdentity, IdentityID newIdentity,
                                            String oldOrgID, String newOrgID, int type, boolean removed)
   {
      AuthenticationChangeEvent evt = null;

      for(AuthenticationChangeListener listener : listeners) {
         if(evt == null) {
            evt = new AuthenticationChangeEvent(this, oldIdentity, newIdentity, oldOrgID, newOrgID, type,
                                                removed);
         }

         listener.authenticationChanged(evt);
      }
   }

   private HashSet<AuthenticationChangeListener> listeners = new HashSet<>();
   private final Logger LOG = LoggerFactory.getLogger(AbstractEditableAuthenticationProvider.class);

}
