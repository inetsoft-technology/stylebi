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

import inetsoft.mv.fs.FSService;
import inetsoft.mv.mr.XJobPool;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.portal.*;
import inetsoft.sree.web.dashboard.DashboardRegistry;
import inetsoft.uql.util.AbstractIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import inetsoft.web.admin.security.IdentityModel;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.user.IdentityThemeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
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
    * @param newOrgID the organization name of the newly created org
    */
   @Override
   public void copyOrganization(Organization fromOrganization, String newOrgID, IdentityService identityService,
                                IdentityThemeService themeService, Principal principal, boolean replace)
   {
      copyOrganization(fromOrganization, null, newOrgID, null, identityService, themeService, principal, replace);
   }

   /**
    * copy one organization's details and save new Organization
    *
    * @param fromOrganization the organization to copy from.
    * @param newOrgID the organization name of the newly created org
    */
   @Override
   public void copyOrganization(Organization fromOrganization, Organization editedNewOrganization,
                                String newOrgID, String newOrgName,
                                IdentityService identityService, IdentityThemeService themeService,
                                Principal principal, boolean replace)
   {
      FSOrganization newOrg = new FSOrganization(newOrgID);
      newOrg.setName(newOrgName == null ? newOrgID : newOrgName);
      identityService.addCopiedIdentityPermission(fromOrganization.getIdentityID(), new IdentityID(newOrg.getName(), newOrgID),
                                                  newOrgID, Identity.ORGANIZATION, replace);
      String fromOrgId = fromOrganization.getId();
      copyScopedProperties(fromOrgId, newOrgID, replace);
      copyDataSpace(fromOrganization, newOrg, replace);
      copyThemes(fromOrgId, newOrgID);

      if(replace) {
         clearScopedProperties(fromOrgId);
         DashboardRegistry.clear(fromOrganization.getIdentityID());
         identityService.updateOrgProperties(fromOrgId, newOrgID);
         identityService.updateAutoSaveFiles(fromOrganization, newOrg, principal);
         identityService.updateTaskSaveFiles(fromOrganization, newOrg);
         identityService.updateIdentityPermissions(Identity.ORGANIZATION, fromOrganization.getIdentityID(),
                                                   newOrg.getIdentityID(), fromOrgId, newOrgID, true);

         try {
            identityService.clearDataSourceMetadata();
         }
         catch(Exception e) {
            LOG.warn("Unable to clear DataSource Metadata: "+ e);
         }
      }
      else {
         copyRootPermittedIdentities(fromOrganization, newOrg.getName(), newOrgID, identityService, principal, replace);
      }

      List<IdentityID> addedRoles = new ArrayList<>();
      List<IdentityID> addedMembers = new ArrayList<>();
      List<IdentityID> fromOrgRoles = Arrays.stream(fromOrganization.getRoles()).toList();

      for(IdentityID roleIdentity : getRoles()) {
         Role role = getRole(roleIdentity);

         if(role != null && fromOrgId.equals(role.getOrganizationID())) {
            IdentityID newID = copyRoleToOrganization(roleIdentity, newOrgID, fromOrgId, identityService, principal);

            if(newID != null && !newID.name.isEmpty()) {
               if(!newID.equals(roleIdentity)) {
                  addedMembers.add(newID);
               }
            }

            if(fromOrgRoles.contains(roleIdentity)) {
               addedRoles.add(newID);
            }

            if(replace) {
               removeRole(roleIdentity);
            }
         }
      }

      for(IdentityID userID : getUsers()) {
         if(getUser(userID).getOrganizationID().equals(fromOrgId)) {
            IdentityID newID = copyUserToOrganization(userID, newOrgID, fromOrgId, identityService, principal);

            if(newID != null && !newID.name.isEmpty()) {
               addedMembers.add(newID);
            }

            if(replace) {
               removeUser(userID);
            }
         }
      }

      for(IdentityID groupID : getGroups()) {
         if(getGroup(groupID).getOrganizationID().equals(fromOrgId)) {
            IdentityID newID = copyGroupToOrganization(groupID, newOrgID, fromOrgId, identityService, principal);

            if(newID != null && !newID.name.isEmpty()) {
               addedMembers.add(newID);
            }
         }
      }

      PortalThemesManager manager = PortalThemesManager.getManager();
      DataSpace dataSpace = DataSpace.getDataSpace();
      String viewsheet = manager.getCssEntries().get(fromOrgId);

      if(viewsheet != null) {
         String[] viewsheetFile = viewsheet.split("/");
         String cssName = viewsheetFile[1];
         String odir = "portal/" + fromOrgId;
         String dir = "portal/" + newOrgID;

         try(InputStream in = dataSpace.getInputStream(odir, cssName)) {
            if(in != null) {
               dataSpace.withOutputStream(dir, cssName, out -> Tool.copyTo(in, out));
            }
         }
         catch(IOException e) {
            throw new RuntimeException(e);
         }

         manager.addCSSEntry(newOrgID, newOrgID + "/" + cssName);
         manager.save();
      }

      // edit org update members has been process in the IdentityService#updateOrganizationMembers
      if(editedNewOrganization != null && replace) {
         newOrg.setMembers(editedNewOrganization.getMembers());
         newOrg.setLocale(editedNewOrganization.getLocale());
         newOrg.setTheme(editedNewOrganization.getTheme());
      }
      else {
         newOrg.setMembers(addedMembers.stream().map(id -> id.name).toArray(String[]::new));
         newOrg.setLocale(fromOrganization.getLocale());
         newOrg.setTheme(fromOrganization.getTheme());
      }

      addOrganization(newOrg);

      identityService.copyStorages(fromOrganization, newOrg, replace);
      identityService.copyRepletRegistry(fromOrgId, newOrgID);

      if(!replace) {
         try {
            OrganizationManager.runInOrgScope(newOrgID, () -> {
               identityService.updateAutoSaveFiles(fromOrganization, newOrg, principal);

               return null;
            });
         }
         catch(Exception e) {
            LOG.warn("Unable to migrate Auto Save Files: "+ e);
         }
      }

      try {
         DataCycleManager.getDataCycleManager().migrateDataCycles(fromOrganization, newOrg, replace);
      }
      catch(Exception e) {
         LOG.warn("Unable to migrate Data Cycles: "+ e);
      }

      if(replace) {
         removeOrganization(fromOrgId);
         identityService.removeOrgProperties(fromOrgId);
         identityService.removeOrgScopedDataSpaceElements(fromOrganization);
         themeService.removeTheme(fromOrgId);
         FSService.clearServerNodeCache(fromOrgId);
         XJobPool.resetOrgCache(fromOrgId);
         manager.removeCSSEntry(fromOrgId);
         manager.save();

         try{
            identityService.updateRepletRegistry(fromOrgId, null);
            identityService.removeStorages(fromOrgId);
         }
         catch(Exception e) {
            LOG.warn("Unable to remove old organization storage: "+e);
         }
      }
      else {
         identityService.copyDashboardRegistry(fromOrganization, newOrg);
      }
   }

   private void copyThemes(String fromOrgId, String toOrgId) {
      if(Tool.isEmptyString(fromOrgId)) {
         return;
      }

      CustomThemesManager manager = CustomThemesManager.getManager();
      manager.loadThemes();
      Set<CustomTheme> themes = new HashSet<>(manager.getCustomThemes());

      manager.getCustomThemes().stream()
         .filter(t -> Tool.equals(t.getOrgID(), fromOrgId))
         .forEach(t -> {
            try {
               CustomTheme clone = (CustomTheme) t.clone();
               clone.setOrgID(toOrgId);

               if(t.getOrganizations().contains(fromOrgId)) {
                  List<String> newOrgs = clone.getOrganizations();
                  newOrgs.remove(fromOrgId);
                  newOrgs.add(toOrgId);
                  clone.setOrganizations(newOrgs);
               }

               clone.setJarPath(clone.getJarPath().replace(fromOrgId, toOrgId));

               themes.add(clone);
            }
            catch(Exception ex) {
               LOG.error("Failed to clone custom theme", ex);
            }
         });

      manager.setCustomThemes(themes);
      manager.save();
   }

   protected void clearScopedProperties(String oldOrgId) {
      //loop through properties, delete any containing .thisOrg.
      Properties properties = SreeEnv.getProperties();
      String oldOrgIdentifier = "inetsoft.org." + oldOrgId;

      for(Enumeration<?> e = properties.propertyNames(); e.hasMoreElements();) {
         String pName = (String) e.nextElement();

         if (pName.startsWith(oldOrgIdentifier)) {
            SreeEnv.remove(pName);
         }
      }
   }

   private void copyDataSpace(Organization fromOrg, Organization toOrg, boolean replace) {
      DataSpace dataspace = DataSpace.getDataSpace();
      String[] paths = dataspace.getOrgScopedPaths(fromOrg);
      String fromOrgId = fromOrg.getId();
      String toOrgId = toOrg.getId();

      for(String path : paths) {
         String newPath = path.replace(fromOrgId, toOrgId);

         if(replace) {
            dataspace.rename(path, newPath);
         }
         else {
            dataspace.copy(path, newPath);
         }
      }
   }

   private void copyScopedProperties(String fromOrgId, String newOrgId, boolean replace) {
      Properties properties = SreeEnv.getProperties();
      String oldOrgIdentifier = "inetsoft.org." + fromOrgId;
      String newOrgPrefix = "inetsoft.org." + newOrgId;
      Enumeration<?> enumeration = properties.propertyNames();

      while(enumeration.hasMoreElements()) {
         String pName = (String) enumeration.nextElement();

         if(pName.startsWith(oldOrgIdentifier)) {
            String baseName = pName.substring(oldOrgIdentifier.length());
            String updatedName = newOrgPrefix + baseName;
            SreeEnv.setProperty(updatedName, properties.getProperty(pName));

            if(replace) {
               SreeEnv.remove(pName);
            }
         }
      }
   }

   private void copyRootPermittedIdentities(Organization fromOrganization, String newOrgName, String newOrgID,
                                            IdentityService identityService, Principal principal, boolean replace) {
      //Users
      String rootUserName = "Users";
      String fromOrgID = fromOrganization.getId();
      IdentityID fromRootUserID = new IdentityID(rootUserName, fromOrgID);
      IdentityID toRootUserID = new IdentityID(rootUserName, newOrgID);
      List<IdentityModel> rootUserPIDs = identityService.getPermission(fromRootUserID, ResourceType.SECURITY_USER, fromOrgID, principal);

      identityService.setIdentityPermissions(toRootUserID, toRootUserID, ResourceType.SECURITY_USER, principal,
                                             copyPermittedIDs(rootUserPIDs, fromOrgID, newOrgID), newOrgID);
      //Groups
      String rootGroupName = "Groups";
      IdentityID fromRootGroupID = new IdentityID(rootGroupName, fromOrgID);
      IdentityID toRootGroupID = new IdentityID(rootGroupName, newOrgID);
      List<IdentityModel> rootGroupPIDs = identityService.getPermission(fromRootGroupID, ResourceType.SECURITY_GROUP, fromOrgID, principal);

      identityService.setIdentityPermissions(toRootGroupID, toRootGroupID, ResourceType.SECURITY_GROUP, principal,
                                             copyPermittedIDs(rootGroupPIDs, fromOrgID, newOrgID), newOrgID);
      //Roles
      String rootRoleName = "Roles";
      IdentityID fromRootID = new IdentityID(rootRoleName, fromOrgID);
      IdentityID toRootID = new IdentityID(rootRoleName, newOrgID);
      List<IdentityModel> rootRolePIDs = identityService.getPermission(fromRootID, ResourceType.SECURITY_ROLE, fromOrgID, principal);

      identityService.setIdentityPermissions(toRootID, toRootID, ResourceType.SECURITY_ROLE, principal,
                                             copyPermittedIDs(rootRolePIDs, fromOrgID, newOrgID), newOrgID);

      String rootOrgRoleName = "Organization Roles";
      IdentityID fromRootOrgRoleID = new IdentityID(rootOrgRoleName, fromOrgID);
      IdentityID toRootOrgRoleID = new IdentityID(rootOrgRoleName, newOrgID);
      List<IdentityModel> rootOrgRolePIDs = identityService.getPermission(fromRootOrgRoleID, ResourceType.SECURITY_ROLE, fromOrgID, principal);

      identityService.setIdentityPermissions(toRootOrgRoleID, toRootOrgRoleID, ResourceType.SECURITY_ROLE, principal,
                                             copyPermittedIDs(rootOrgRolePIDs, fromOrgID, newOrgID), newOrgID);

      if(replace) {
         identityService.setIdentityPermissions(fromRootUserID, fromRootUserID, ResourceType.SECURITY_USER,
                                                 principal, null, null);
         identityService.setIdentityPermissions(fromRootGroupID, fromRootGroupID, ResourceType.SECURITY_GROUP,
                                                 principal, null, null);
         identityService.setIdentityPermissions(fromRootID, fromRootID, ResourceType.SECURITY_ROLE,
                                                principal, null, null);
         identityService.setIdentityPermissions(fromRootOrgRoleID, fromRootOrgRoleID, ResourceType.SECURITY_ROLE,
                                                principal, null, null);
      }
   }

   public List<IdentityModel> copyPermittedIDs(List<IdentityModel> fromIDs, String fromOrgId, String newOrgId ) {
      List<IdentityModel> updatedPIds = new ArrayList<>();
      for(IdentityModel id : fromIDs) {
         switch(id.type()) {
         case Identity.USER:
            if(getUser(id.identityID()) != null && fromOrgId.equals(getUser(id.identityID()).getOrganizationID())) {
               IdentityID newName = new IdentityID(id.identityID().name, newOrgId);
               updatedPIds.add(IdentityModel.builder().identityID(newName).type(Identity.USER).build());
            }
            break;
         case Identity.GROUP:
            if(getGroup(id.identityID()) != null && fromOrgId.equals(getGroup(id.identityID()).getOrganizationID())) {
               IdentityID newName = new IdentityID(id.identityID().name, newOrgId);
               updatedPIds.add(IdentityModel.builder().identityID(newName).type(Identity.GROUP).build());
            }
            break;
         case Identity.ROLE:
            if(getRole(id.identityID()) != null && fromOrgId.equals(getRole(id.identityID()).getOrganizationID())) {
               IdentityID newName = new IdentityID(id.identityID().name, newOrgId);
               updatedPIds.add(IdentityModel.builder().identityID(newName).type(Identity.ROLE).build());
            }
            break;
         }
      }
      return updatedPIds;
   }

   private IdentityID copyRoleToOrganization(IdentityID roleIdentity, String orgID, String fromOrgID, IdentityService identityService, Principal principal) {
      FSRole fromRole = (FSRole) getRole(roleIdentity);
      if(fromRole != null) {
         IdentityID newRoleID = new IdentityID(roleIdentity.name, orgID);

         if(newRoleID != null && !newRoleID.name.isEmpty()) {
            FSRole newRole = new FSRole(newRoleID);

            newRole.setOrganization(orgID);
            newRole.setRoles(copyIdentityRoles(fromRole, orgID));
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

   private IdentityID copyUserToOrganization(IdentityID memberID, String orgID, String fromOrgID, IdentityService identityService, Principal principal) {
      User fromUser = getUser(memberID);

      if(fromUser != null) {
         IdentityID newID = new IdentityID(memberID.name, orgID);

         FSUser newUser = new FSUser(newID);
         newUser.setGroups(fromUser.getGroups());
         newUser.setAlias(fromUser.getAlias());
         newUser.setLocale(fromUser.getLocale());
         newUser.setActive(fromUser.isActive());
         newUser.setRoles(copyIdentityRoles(fromUser, orgID));
         newUser.setOrganization(orgID);
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

   private IdentityID copyGroupToOrganization(IdentityID memberID, String orgID, String fromOrgID, IdentityService identityService, Principal principal) {
      Group fromGroup = getGroup(memberID);

      if(fromGroup != null) {
         IdentityID newID = new IdentityID(memberID.name, orgID);
         FSGroup newGroup = new FSGroup(newID);

         newGroup.setLocale(fromGroup.getLocale());
         newGroup.setGroups(fromGroup.getGroups());
         newGroup.setOrganization(orgID);
         newGroup.setRoles(copyIdentityRoles(fromGroup, orgID));

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
      List<IdentityModel> updatedUPermIds = copyPermittedIDs(uPermIds, fromIdentity.orgID, toIdentity.orgID);

      if(!updatedUPermIds.isEmpty()) {
         identityService.setIdentityPermissions(toIdentity, toIdentity,rType, principal,
                                                updatedUPermIds, newOrgID);
      }
   }

   private IdentityID[] copyIdentityRoles(AbstractIdentity fromID, String orgName) {
      ArrayList<IdentityID> newRoles = new ArrayList<>();

      for(IdentityID roleName : fromID.getRoles()) {
         Role role = getRole(roleName);

         if(role != null) {
            boolean isGlobal = roleName.orgID == null;

            if(isGlobal && isSystemAdministratorRole(roleName)) {
               continue;
            }

            IdentityID newRoleID = isGlobal ? roleName : new IdentityID(roleName.name, orgName);
            newRoles.add(newRoleID);
         }
      }

      return newRoles.toArray(new IdentityID[0]);
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

   @Override
   public void removeGroup(IdentityID groupIdentity, boolean removed) {
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
   public void removeOrganization(String id) {
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
