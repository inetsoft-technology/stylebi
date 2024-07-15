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

import inetsoft.mv.MVWorksheetStorage;
import inetsoft.mv.data.MVStorage;
import inetsoft.mv.fs.internal.BlockFileStorage;
import inetsoft.report.LibManager;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistry;
import inetsoft.storage.BlobStorage;
import inetsoft.storage.BlobTransaction;
import inetsoft.uql.*;
import inetsoft.uql.asset.EmbeddedTableStorage;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.service.XEngine;
import inetsoft.uql.util.*;
import inetsoft.util.*;
import inetsoft.util.audit.*;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.security.user.*;

import java.io.*;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class IdentityService {
   @Autowired
   public IdentityService(SecurityEngine securityEngine, SecurityProvider securityProvider) {
      this.securityEngine = securityEngine;
      this.securityProvider = securityProvider;
   }

   private AuthenticationProvider getProvider(String providerName) {
      AuthenticationProvider authc = securityProvider.getAuthenticationProvider();

      if(!(authc instanceof AuthenticationChain)) {
         return null;
      }

      AuthenticationChain authcChain = (AuthenticationChain) authc;
      return authcChain.getProviders().stream()
         .filter((p) -> Catalog.getCatalog().getString(p.getProviderName()).equals(providerName))
         .findFirst()
         .orElse(null);
   }

   public List<String> deleteIdentities(IdentityModel[] models, String providerName,
                                        Principal principal)
   {
      List<String> warnings = new ArrayList<>();
      Catalog catalog = Catalog.getCatalog(principal);
      AuthenticationProvider authcProvider = this.getProvider(providerName);

      if(!(authcProvider instanceof EditableAuthenticationProvider)) {
         warnings.add("Selected provider is not editable");
         return warnings;
      }

      EditableAuthenticationProvider provider = (EditableAuthenticationProvider) authcProvider;

      List<IdentityID> failedIdentities = new ArrayList<>();

      ActionRecord actionRecord = SUtil.getActionRecord(
         principal, ActionRecord.ACTION_NAME_DELETE, null,
         ActionRecord.OBJECT_TYPE_USERPERMISSION);
      StringBuilder objectName = new StringBuilder();
      List<IdentityID> deleteUserIDs = new ArrayList<>();

      try {
         for(IdentityModel identityModel : models) {
            IdentityID identityId = identityModel.identityID();
            int type = identityModel.type();

            ResourceType resourceType = ResourceType.SECURITY_USER;

            if(identityModel.type() == Identity.Type.GROUP.code()) {
               resourceType = ResourceType.SECURITY_GROUP;
            }

            if(identityModel.type() == Identity.Type.ORGANIZATION.code()) {
               if(!OrganizationManager.getInstance().isSiteAdmin(principal)) {
                  //org admin cannot delete organization
                  failedIdentities.add(identityModel.identityID());
                  warnings.add(catalog.getString("em.security.deleteOrgOrgAdmin"));
                  continue;
               }
               resourceType = ResourceType.SECURITY_ORGANIZATION;
            }

            if(identityModel.type() == Identity.Type.ROLE.code()) {
               resourceType = ResourceType.SECURITY_ROLE;
            }

            try {
               if(!securityEngine.checkPermission(principal, resourceType, identityModel.identityID().convertToKey(),
                                                  ResourceAction.ADMIN))
               {
                  failedIdentities.add(identityModel.identityID());
                  continue;
               }
            }
            catch(Exception ignore) {
               failedIdentities.add(identityModel.identityID());
               continue;
            }

            String state = IdentityInfoRecord.STATE_NONE;

            if(type == Identity.USER) {
               IdentityInfo info = getIdentityInfo(identityId, type, provider);
               state = info.isActive() ? IdentityInfoRecord.STATE_ACTIVE :
                  IdentityInfoRecord.STATE_INACTIVE;
            }

            IdentityInfoRecord identityInfoRecord =
               SUtil.getIdentityInfoRecord(
                  identityId, type, IdentityInfoRecord.ACTION_TYPE_DELETE, null, state);

            try {
               if(type == Identity.GROUP) {
                  IdentityID[] users = provider.getUsers(identityId);

                  if(users.length > 0) {
                     warnings.add(catalog.getString("em.security.delgroup"));
                     continue;
                  }
               }

               if(isSelfAndEMUser(principal, identityId, type) ||
                  isSelfRole(provider.getUser(IdentityID.getIdentityIDFromKey(principal.getName())), identityId, type))
               {
                  warnings.add(catalog.getString("em.security.delself"));
                  continue;
               }

               if(type == Identity.USER) {
                  deleteUserIDs.add(identityId);
               }

               objectName.append(identityId != null ? identityId.getName() : null).append(" ");
               syncIdentity(provider, identityId != null ? new DefaultIdentity(identityId, type) :
                  new DefaultIdentity(), null);
            }
            catch(Exception ex) {
               actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
               LOG.warn("Failed to delete identity: {}", identityId, ex);
               warnings.add("Failed to delete identity " + identityId + ".");
            }
            finally {
               if(!actionRecord.getActionStatus().equals(ActionRecord.ACTION_STATUS_FAILURE)) {
                  Audit.getInstance().auditIdentityInfo(identityInfoRecord, principal);
               }
            }
         }

         if(!failedIdentities.isEmpty()) {
            String warning = String.format(
               "Unauthorized access to resource(s) \"%s\" by user %s.",
               String.join(", ", failedIdentities.stream().map(id -> id.name).toArray(String[]::new)), principal.getName());
            LOG.warn(warning);
            warnings.add(warning);
         }

         SecurityEngine.touch();
         IdentityID[] names = new IdentityID[deleteUserIDs.size()];
         deleteUserIDs.toArray(names);
         actionRecord.setObjectName(objectName.toString());
      }
      catch(Exception e) {
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         throw e;
      }
      finally {
         Audit.getInstance().auditAction(actionRecord, principal);
      }

      return warnings;
   }

   /**
    * Get the parents of an identity.
    *
    * @param childId     the name of user/group in provider.
    * @param parentIDs   the roles/groups of child.
    * @param parentID    the name of identity being edited.
    * @param childrenIDs users/groups in values of identity.
    */
   private String[] getParents(IdentityID childId, String[] parentIDs, IdentityID parentID,
                               List<IdentityID> childrenIDs)
   {
      List<String> list = new ArrayList<>();
      Collections.addAll(list, parentIDs);

      boolean isParent = list.contains(parentID.name);
      boolean isChild = childrenIDs.contains(childId);
      boolean changed = isParent != isChild;

      if(changed) {
         if(isParent) {
            list.remove(parentID.name);
         }

         if(isChild) {
            list.add(parentID.name);
         }

         String[] ngroups = new String[list.size()];
         list.toArray(ngroups);
         return ngroups;
      }

      return null;
   }

   private IdentityID[] getRoleParents(IdentityID childId, IdentityID[] parentIDs, IdentityID parentID,
                               List<IdentityID> childrenIDs)
   {
      List<IdentityID> list = new ArrayList<>();
      Collections.addAll(list, parentIDs);

      boolean isParent = list.contains(parentID);
      boolean isChild = childrenIDs.contains(childId);
      boolean changed = isParent != isChild;

      if(changed) {
         if(isParent) {
            list.remove(parentID);
         }

         if(isChild) {
            list.add(parentID);
         }

         IdentityID[] ngroups = new IdentityID[list.size()];
         list.toArray(ngroups);
         return ngroups;
      }

      return null;
   }

   /**
    * Check role dependency.
    */
   private void checkInheritRoles(IdentityID identity, IdentityID inheritRole, EditableAuthenticationProvider provider) {
      Role prole = provider.getRole(inheritRole);

      if(prole != null) {
         IdentityID[] roles = prole.getRoles();
         List<IdentityID> rolesList = Arrays.asList(roles);

         if(rolesList.contains(identity)) {
            roles = Tool.remove(roles, identity);
            ((FSRole) prole).setRoles(roles);
            provider.setRole(prole.getIdentityID(), prole);
         }

         for(IdentityID role : roles) {
            checkInheritRoles(identity, role, provider);
         }
      }
   }

   public IdentityInfo getIdentityInfo(IdentityID identityId, int type, AuthenticationProvider provider) {
      Identity identity;

      if(type == Identity.USER) {
         identity = provider.getUser(identityId);
      }
      else if(type == Identity.GROUP) {
         identity = provider.getGroup(identityId);
      }
      else if(type == Identity.ORGANIZATION) {
         identity = provider.getOrganization(identityId.name);
      }
      else {
         identity = provider.getRole(identityId);
      }

      return new IdentityInfo(identity, provider);
   }

   /**
    * Sync quota manger and so on, if identity is removed or renamed.
    */
   private void syncIdentity(EditableAuthenticationProvider eprovider,
                             Identity identity, IdentityID oID)
      throws Exception
   {
      // TODO check permission and throw exception if not allowed to edit
      IdentityID identityId = identity.getIdentityID();
      int type = identity.getType();
      DashboardManager dmanager = DashboardManager.getManager();
      ScheduleManager smanager = ScheduleManager.getScheduleManager();
      LibManager manager = LibManager.getManager();
      Identity nid = new DefaultIdentity(identityId, type);
      Identity oid = oID == null ? null : new DefaultIdentity(oID, type);

      if(oID == null) {
         dmanager.setDashboards(nid, null);
         smanager.identityRemoved(identity, eprovider);
         manager.clear(SUtil.getOrgID(identityId));
      }
      else {
         if(!identityId.equals(oID)) {
            smanager.identityRenamed(oID, identity);
            dmanager.setDashboards(nid, dmanager.getDashboards(oid));
            dmanager.setDashboards(oid, null);
         }
      }

      AuthorizationChain authoc = (AuthorizationChain) securityProvider.getAuthorizationProvider();

      if(identity.getType() == Identity.USER) {
         //AssetRepository rep = AssetUtil.getAssetRepository(false);

         if(oID == null) {
            //delete user identityId inside of permissions
            String orgId = eprovider.getOrgId(eprovider.getUser(identityId).getOrganization());
            RepletRegistry.removeUser(identityId);
            //rep.removeUser(identityId);
            DashboardRegistry.clear(identityId);
            eprovider.removeUser(identityId);
            updateIdentityPermissions(type, identityId, null, orgId, orgId,true);
         }
         else {
            if(!identityId.equals(oID)) {
               String orgId = SUtil.getOrgID(identityId);
               //rep.renameUser(oID, identityId);
               RepletRegistry.renameUser(oID, identityId);
               DashboardRegistry.clear(oID);
               //update user identityId inside of permissions
               updateIdentityPermissions(type, oID, identityId,orgId,orgId, true);
            }

            eprovider.setUser(oID, (User) identity);
         }
      }
      else if(identity.getType() == Identity.ORGANIZATION) {

         if(oID == null) {
            String orgID = eprovider.getOrganization(identityId.name).getOrganizationID();
            DashboardRegistry.clear(identityId);
            clearDataSourceMetadata();
            deleteOrganizationMembers(identityId.name, eprovider);
            eprovider.removeOrganization(identityId.name);

            // delete organization identityId inside of permissions
            authoc.cleanOrganizationFromPermissions(orgID);

            // deleting current organization should reset curOrg
            OrganizationManager.getInstance().setCurrentOrgID(Organization.getDefaultOrganizationID());

            removeStorages(orgID);
            removeOrgProperties(orgID);
            removeOrgScopedDataSpaceElements(orgID);
            updateRepletRegistry(orgID, null);
         }
         else {
            String oId = eprovider.getOrganization(oID.name).getId();
            String id = ((Organization) identity).getId();

            if(!identityId.equals(oID)) {
               //update organization identityId inside of permissions
               if(!id.equals(oId)) {
                  updateIdentityPermissions(type, oID, identityId, oId, id, false);
                  DataCycleManager.getDataCycleManager().migrateDataCycles(oId, id);
               }
               else {
                  updateIdentityPermissions(type, oID, identityId,oId,oId, true);
               }
               DashboardRegistry.clear(oID);
               clearDataSourceMetadata();
            }
            else if (!id.equals(oId)) {
               updateIdentityPermissions(type, oID, identityId,oId, id, false);
               // delete organization identityId inside of permissions
               authoc.cleanOrganizationFromPermissions(oId);
               DataCycleManager.getDataCycleManager().migrateDataCycles(oId, id);
            }

            Organization oorg = new Organization(oID.getOrganization());
            oorg.setId(oId);

            if(!Tool.equals(oID.getOrganization(), identity.getName()) ||
               !Tool.equals(oId, ((Organization) identity).getId()))
            {
               updateStorageNames(oorg, ((Organization) identity));
               migrateDashboardRegistry(oorg, ((Organization) identity));
               updateOrgProperties(oId, id);
               updateAutoSaveFiles(oorg, ((Organization) identity));
            }

            eprovider.setOrganization(oID.name, (Organization) identity);
            updateIdentityRootPermission(oID.getName(), identity.getName(),
               ThreadContext.getContextPrincipal());

            // Update current orgID
            OrganizationManager.getInstance().setCurrentOrgID(id);
            updateRepletRegistry(oId, id);
         }
      }
      else {
         if(oID == null) {
            if(type == Identity.GROUP) {
               //delete group identityId inside of permissions
               String orgId = eprovider.getOrgId(eprovider.getGroup(identityId).getOrganization());
               eprovider.removeGroup(identityId);
               updateIdentityPermissions(type, identityId, null, orgId, orgId, true);
               updatePrincipalGroup(oID, identityId);
            }
            else {
               //delete role identityId inside of permissions
               String orgId = eprovider.getOrgId(eprovider.getRole(identityId).getOrganization());
               eprovider.removeRole(identityId);
               updateIdentityPermissions(type, identityId, null, orgId, orgId, true);
            }
         }
         else {
            if(!identityId.equals(oID)) {
               if(type == Identity.GROUP) {
                  //update group name inside of permissions
                  String orgId = eprovider.getOrgId(eprovider.getGroup(oID).getOrganization());
                  updateIdentityPermissions(type, oID, identityId, orgId, orgId, true);
                  updatePrincipalGroup(oID, identityId);
               }
               else {
                  //update role identityId inside of permissions
                  String orgId = eprovider.getRole(oID) != null && eprovider.getRole(oID).getOrganization() != null ?
                     eprovider.getOrgId(eprovider.getRole(oID).getOrganization()) :
                     null;
                  updateIdentityPermissions(type, oID, identityId, orgId, orgId, true);
                  syncRoles(eprovider, oID, identityId);
               }
            }

            if(type == Identity.GROUP) {
               eprovider.removeGroup(oID);
               eprovider.setGroup(identityId, (Group) identity);
            }
            else {
               eprovider.removeRole(oID);
               eprovider.setRole(identityId, (Role) identity);
            }
         }
      }
   }

   private void syncRoles(EditableAuthenticationProvider eprovider,
                          IdentityID orole, IdentityID nrole)
   {
      IdentityID[] roles = eprovider.getRoles();

      if(roles == null || roles.length == 0) {
         return;
      }

      Arrays.stream(roles).forEach(role -> syncRoles(eprovider, orole, nrole, role));
   }

   private void syncRoles(EditableAuthenticationProvider eprovider,
                          IdentityID oinheritRole, IdentityID ninheritRole, IdentityID roleId)
   {
      Role role = eprovider.getRole(roleId);
      IdentityID[] roles = role.getRoles();

      if(roles != null && roles.length > 0) {
         Arrays.stream(roles)
            .filter(inheritRole -> Tool.equals(inheritRole, oinheritRole))
            .forEach(inheritRole -> {
               inheritRole.setName(ninheritRole.name);
               inheritRole.setOrganization(ninheritRole.organization);
            });
         eprovider.setRole(roleId, role);
      }
   }

   private void updatePrincipalGroup(IdentityID oid, IdentityID nid) {
      XPrincipal principal = (XPrincipal) ThreadContext.getPrincipal();

      principal.setGroups(oid == null ?
                             Tool.remove(principal.getGroups(), nid.name) :
                             Tool.replace(principal.getGroups(), oid.name, nid.name));
   }

   private void updateIdentityRootPermission(String oldOrgName, String newOrgName,
                                             Principal principal)
   {
      if(Objects.equals(oldOrgName, newOrgName)) {
         return;
      }

      String rootUser = Catalog.getCatalog(principal).getString("Users");
      IdentityID oldUserRootID = new IdentityID(rootUser, oldOrgName);
      IdentityID newUserRootID = new IdentityID(rootUser, newOrgName);
      String rootGroup = Catalog.getCatalog(principal).getString("Groups");
      IdentityID oldRootGroupID = new IdentityID(rootGroup, oldOrgName);
      IdentityID newRootGroupID = new IdentityID(rootGroup, newOrgName);
      String rootRole = Catalog.getCatalog(principal).getString("Roles");
      IdentityID oldRootRoleID = new IdentityID(rootRole, oldOrgName);
      IdentityID newRootRoleID = new IdentityID(rootRole, newOrgName);
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();

      for(Tuple3<ResourceType, String, Permission> permissionSet : provider.getPermissions()) {
         ResourceType resourceType = permissionSet.getFirst();
         String sourceName = permissionSet.getSecond();
         Permission permission = permissionSet.getThird();

         if(resourceType == ResourceType.SECURITY_USER &&
            Objects.equals(sourceName, oldUserRootID.convertToKey()))
         {
            provider.removePermission(resourceType, oldUserRootID.convertToKey());
            provider.setPermission(resourceType, newUserRootID.convertToKey(), permission);
         }
         else if(resourceType == ResourceType.SECURITY_GROUP &&
            Objects.equals(sourceName, oldRootGroupID.convertToKey()))
         {
            provider.removePermission(resourceType, oldRootGroupID.convertToKey());
            provider.setPermission(resourceType, newRootGroupID.convertToKey(), permission);
         }
         else if(resourceType == ResourceType.SECURITY_ROLE &&
            Objects.equals(sourceName, oldRootRoleID.convertToKey()))
         {
            provider.removePermission(resourceType, oldRootRoleID.convertToKey());
            provider.setPermission(resourceType, newRootRoleID.convertToKey(), permission);
         }
      }
   }

   public void deleteOrganizationMembers(String orgName, EditableAuthenticationProvider eprovider) {
      IdentityID[] users = eprovider.getUsers();
      IdentityID[] groups = eprovider.getGroups();
      IdentityID[] roles = eprovider.getRoles();

      for(int i=0;i<users.length;i++) {
         FSUser user = (FSUser) eprovider.getUser(users[i]);

         if(orgName.equals(user.getOrganization())) {
            //users are tied to org, delete if deleted
            eprovider.removeUser(user.getIdentityID());
            addCopiedIdentityPermission(user.getIdentityID(), null, "", Identity.USER);
         }
      }

      for(int i=0;i<groups.length;i++) {
         FSGroup group = (FSGroup) eprovider.getGroup(groups[i]);

         if(orgName.equals(group.getOrganization())) {
            //group is tied to org, delete if deleted
            eprovider.removeGroup(group.getIdentityID());
            addCopiedIdentityPermission(group.getIdentityID(), null,"", Identity.GROUP);
         }
      }

      for(int i=0;i<roles.length;i++) {
         FSRole role = (FSRole) eprovider.getRole(roles[i]);

         if(orgName.equals(role.getOrganization())) {
            //role is tied to org, delete if deleted
            eprovider.removeRole(role.getIdentityID());
            addCopiedIdentityPermission(role.getIdentityID(), null, "", Identity.ROLE);
         }
      }

   }

   private void updateOrganizationMembers(Organization identity, EditableAuthenticationProvider eprovider) {
      String orgName = identity.getName();
      String oldOrgName = OrganizationManager.getCurrentOrgName();
      List<String> members = Arrays.asList(identity.getMembers());
      IdentityID[] users = Arrays.stream(eprovider.getUsers()).filter(u -> Tool.equals(oldOrgName, u.organization)).toArray(IdentityID[]::new);
      IdentityID[] groups = Arrays.stream(eprovider.getGroups()).filter(u -> Tool.equals(oldOrgName, u.organization)).toArray(IdentityID[]::new);
      IdentityID[] roles = Arrays.stream(eprovider.getRoles()).filter(u -> Tool.equals(oldOrgName, u.organization)).toArray(IdentityID[]::new);
      boolean orgIdChange = !OrganizationManager.getInstance().getCurrentOrgID().equals(identity.getId()) ||
                            !oldOrgName.equals(identity.getName());

      AuthorizationChain authoc = ((AuthorizationChain) securityProvider.getAuthorizationProvider());

      for(int i = 0; i < users.length; i++) {
         FSUser user = (FSUser) eprovider.getUser(users[i]);
         IdentityID oldID = user.getIdentityID();

         if(orgName.equals(user.getOrganization())) {
            if(!members.contains(user.getName())) {
               //remove permissions for this user
               updateIdentityPermissions(Identity.USER, user.getIdentityID(), null, OrganizationManager.getInstance().getCurrentOrgID(), identity.getId(), false);
               //user is tied to org, delete if removed as member
               eprovider.removeUser(user.getIdentityID());
            }
            else if(orgIdChange) {
               updateIdentityPermissions(Identity.USER, user.getIdentityID(), user.getIdentityID(),
                  OrganizationManager.getInstance().getCurrentOrgID(), identity.getId(), false);
            }
         }
         else if(members.contains(user.getName())) {
            //add to this Organization
            user.setOrganization(orgName);
            eprovider.setUser(user.getIdentityID(), user);
            eprovider.removeUser(oldID);
            ScheduleManager smanager = ScheduleManager.getScheduleManager();
            smanager.identityRenamed(oldID, user);
         }
      }

      for(int i=0;i<groups.length;i++) {
         FSGroup group = (FSGroup) eprovider.getGroup(groups[i]);

         if(orgName.equals(group.getOrganization())) {
            if(!members.contains(group.getName())) {
               //group is tied to org, delete if removed as member
               eprovider.removeGroup(group.getIdentityID());
               //remove permissions for this group
               updateIdentityPermissions(Identity.GROUP, group.getIdentityID(), null, OrganizationManager.getInstance().getCurrentOrgID(), identity.getId(), false);
            }
            //else if name change or id change, update permissions
            else if(orgIdChange) {
               //clone new group with correct name
               updateGroupForOrg(identity, group, orgName, eprovider, authoc);
            }
         }
         else if(members.contains(group.getName())) {
            //clone new group with correct name
            updateGroupForOrg(identity, group, orgName, eprovider, authoc);
         }
      }

      for(int i=0;i<roles.length;i++) {
         FSRole role = (FSRole) eprovider.getRole(roles[i]);

         if(orgName.equals(role.getOrganization())) {
            if(!members.contains(role.getName())) {
               //role is tied to org, delete if removed as member
               eprovider.removeRole(role.getIdentityID());
               //delete role from permissions
               updateIdentityPermissions(Identity.ROLE, role.getIdentityID(), null, OrganizationManager.getInstance().getCurrentOrgID(), identity.getId(), false);

            }
            else if(orgIdChange) {
               updateRoleForOrg(identity, role, orgName, eprovider, authoc);
            }
         }
         else if(members.contains(role.getName())) {
            updateRoleForOrg(identity, role, orgName, eprovider, authoc);
         }
      }
   }

   private void updateRoleForOrg(Organization identity, FSRole role, String orgName,
                                 EditableAuthenticationProvider eprovider, AuthorizationChain authoc)
   {
      boolean authUpdated = false;

      if(!OrganizationManager.getCurrentOrgName().equals(identity.getName())) {
         //clone new role with correct name
         IdentityID newName = new IdentityID(role.getName(), orgName);
         FSRole newRole = new FSRole(newName, role.getRoles());
         newRole.setDesc(role.getDescription());
         newRole.setDefaultRole(role.isDefaultRole());
         newRole.setOrgAdmin(role.isOrgAdmin());
         newRole.setSysAdmin(role.isSysAdmin());
         //do not overwrite existing role of this name
         if(eprovider.getRole(newName) == null) {
            //update role in permissions
            updateIdentitiesContainingRole(role.getIdentityID(), newName, orgName, eprovider);
            authUpdated = true;
            updateIdentityPermissions(Identity.ROLE, role.getIdentityID(), newName, OrganizationManager.getInstance().getCurrentOrgID(), identity.getId(), true);
            eprovider.setRole(newName, newRole);
            eprovider.removeRole(role.getIdentityID());
         }
         else {
            eprovider.removeRole(role.getIdentityID());
         }
      }

      if(!OrganizationManager.getInstance().getCurrentOrgID().equals(identity.getId()) && !authUpdated) {
         updateIdentityPermissions(Identity.ROLE, role.getIdentityID(), role.getIdentityID(), OrganizationManager.getInstance().getCurrentOrgID(), identity.getId(), false);
      }
   }

   private void updateGroupForOrg(Organization identity, Group group, String orgName,
                                  EditableAuthenticationProvider eprovider, AuthorizationChain authoc) {
      //if name change
      boolean authUpdated = false;
      if(!OrganizationManager.getCurrentOrgName().equals(identity.getName())) {
         IdentityID newName = new IdentityID(group.getIdentityID().name, orgName);
         FSGroup newGroup = new FSGroup(newName, group.getLocale(),
                                        group.getGroups(), group.getRoles());
         //do not overwrite existing group of this name
         if(eprovider.getGroup(newName) == null) {
            eprovider.setGroup(newName, newGroup);
            //update permission for this group
            authUpdated = true;
            updateIdentityPermissions(Identity.GROUP, group.getIdentityID(), newName, OrganizationManager.getInstance().getCurrentOrgID(), identity.getId(), true);
            eprovider.removeGroup(group.getIdentityID());
            ScheduleManager smanager = ScheduleManager.getScheduleManager();
            smanager.identityRenamed(group.getIdentityID(), newGroup);
         }
         else {
            eprovider.removeGroup(group.getIdentityID());
         }
      }

      if(!OrganizationManager.getInstance().getCurrentOrgID().equals(identity.getId()) && !authUpdated) {
         updateIdentityPermissions(Identity.GROUP, group.getIdentityID(), group.getIdentityID(), OrganizationManager.getInstance().getCurrentOrgID(), identity.getId(), false);

      }

   }

   private void updateIdentitiesContainingRole(IdentityID oldID, IdentityID newID, String orgName,
                                               EditableAuthenticationProvider eprovider) {
      IdentityID[] users = eprovider.getUsers();
      IdentityID[] groups = eprovider.getGroups();
      IdentityID[] roles = eprovider.getRoles();

      for(IdentityID userName : users) {
         FSUser user = (FSUser) eprovider.getUser(userName);

         if(orgName.equals(user.getOrganization()) && user.getRoles() != null &&
            Arrays.asList(user.getRoles()).contains(oldID))
         {
            List<IdentityID> newRoles = new ArrayList<>(Arrays.asList(user.getRoles()));
            Collections.replaceAll(newRoles, oldID, newID);
            user.setRoles(newRoles.toArray(new IdentityID[0]));
            eprovider.setUser(userName, user);
         }
      }

      for(IdentityID groupName : groups) {
         FSGroup group = (FSGroup) eprovider.getGroup(groupName);

         if(orgName.equals(group.getOrganization()) && group.getRoles() != null &&
            Arrays.asList(group.getRoles()).contains(oldID))
         {
            List<IdentityID> newRoles = new ArrayList<>(Arrays.asList(group.getRoles()));
            Collections.replaceAll(newRoles, oldID, newID);
            group.setRoles(newRoles.toArray(new IdentityID[0]));
            eprovider.setGroup(groupName, group);
         }
      }

      for(IdentityID roleName : roles) {
         FSRole role = (FSRole) eprovider.getRole(roleName);

         if(orgName.equals(role.getOrganization()) && role.getRoles() != null &&
            Arrays.asList(role.getRoles()).contains(oldID))
         {
            List<IdentityID> newRoles = new ArrayList<>(Arrays.asList(role.getRoles()));
            Collections.replaceAll(newRoles, oldID, newID);
            role.setRoles(newRoles.toArray(new IdentityID[0]));
            eprovider.setRole(roleName, role);
         }
      }
   }

   public List<IdentityModel> getRoleMembers(IdentityID roleId, AuthenticationProvider provider) {
      List<IdentityModel> roleMembers = new ArrayList<>();

      for(IdentityID userID : provider.getUsers()) {
         User user = provider.getUser(userID);
         for(IdentityID uRole : user.getRoles()) {
            if(uRole.equals(roleId)) {
               roleMembers.add(
                  IdentityModel.builder()
                     .identityID(userID)
                     .type(Identity.USER)
                     .build()
               );
            }
         }
      }
      for(IdentityID groupID : provider.getGroups()) {
         Group group = provider.getGroup(groupID);
         for(IdentityID uRole : group.getRoles()) {
            if(uRole.equals(roleId)) {
               roleMembers.add(
                  IdentityModel.builder()
                     .identityID(groupID)
                     .type(Identity.GROUP)
                     .build()
               );
            }
         }
      }
      for(String orgName : provider.getOrganizations()) {
         Organization org = provider.getOrganization(orgName);
         for(IdentityID uRole : org.getRoles()) {
            if(uRole.equals(roleId)) {
               roleMembers.add(
                  IdentityModel.builder()
                     .identityID(new IdentityID(orgName, orgName))
                     .type(Identity.ORGANIZATION)
                     .build()
               );
            }
         }
      }

      return roleMembers;
   }

   public void removeStorages(String orgID) throws Exception {
      DashboardManager.getManager().removeDashboardStorage(orgID);
      DependencyStorageService.getInstance().removeDependencyStorage(orgID);
      RecycleBin.getRecycleBin().removeStorage(orgID);
      IndexedStorage.getIndexedStorage().removeStorage(orgID);

      removeBlobStorage("__mv", orgID, MVStorage.Metadata.class);
      removeBlobStorage("__mvws", orgID, MVWorksheetStorage.Metadata.class);
      removeBlobStorage("__mvBlock", orgID, BlockFileStorage.Metadata.class);
      removeBlobStorage("__pdata", orgID, EmbeddedTableStorage.Metadata.class);
      removeBlobStorage("__library", orgID, LibManager.Metadata.class);
   }

   public void copyStorages(String oldOrgId, String newOrgId) {
      try {
         DashboardManager.getManager().copyStorageData(oldOrgId, newOrgId);
         DependencyStorageService.getInstance().copyStorageData(oldOrgId, newOrgId);
         RecycleBin.getRecycleBin().copyStorageData(oldOrgId, newOrgId);
         IndexedStorage.getIndexedStorage().copyStorageData(oldOrgId, newOrgId);
      }
      catch(Exception e) {
         LOG.warn("Could not copy Storages from "+oldOrgId+" to "+newOrgId+", "+e);
      }
   }

   private <T extends Serializable> void removeBlobStorage(String suffix, String orgID,
                             Class<T> type) throws Exception
   {
      BlobStorage<T> storage =
         SingletonManager.getInstance(BlobStorage.class, orgID.toLowerCase() + suffix, false);
      storage.deleteBlobStorage();
   }

   public void removeOrgProperties(String orgID) {
      String prefix = "inetsoft.org." + orgID + ".";

      Properties properties = SreeEnv.getProperties();
      Set<Object> orgProperties = properties.keySet().stream()
         .filter(prop -> ((String) prop).startsWith(prefix))
         .collect(Collectors.toSet());

      for(Object orgProp : orgProperties) {
         String propName = (String) orgProp;
         SreeEnv.remove(propName);
      }
   }

   public void removeOrgScopedDataSpaceElements(String orgID) {
      DataSpace dataspace = DataSpace.getDataSpace();
      String[] paths = DataSpace.getOrgScopedPaths(orgID);

      for(String path : paths) {
         dataspace.delete(path,"");
      }
   }

   private void updateRepletRegistry(String oOID, String nOID) throws Exception {
      RepletRegistry registry = RepletRegistry.getRegistry();
      String[] oldFolders = registry.getAllFolders(oOID);
      boolean removeOrg = nOID == null;

      if(!Tool.equals(oOID, nOID)) {
         for(String oldFolder : oldFolders) {
            registry.removeFolder(oldFolder, oOID);

            if(!removeOrg) {
               registry.addFolder(oldFolder, nOID);
            }
         }
      }

      if(!removeOrg) {
         for(String oldFolder : oldFolders) {
            registry.addFolder(oldFolder, nOID);
         }
      }
   }

   public void copyRepletRegistry(String oOID, String nOID) {
      try {
         RepletRegistry registry = RepletRegistry.getRegistry();
         String[] oldFolders = registry.getAllFolders(oOID);

         for(String oldFolder : oldFolders) {
            if(!Tool.MY_DASHBOARD.equals(oldFolder)) {
               registry.addFolder(oldFolder, nOID);
            }
         }
      }
      catch(Exception e) {
         LOG.warn("Could not copy registry from {} to {}", oOID, nOID, e);
      }
   }

   public void migrateDashboardRegistry(Organization oorg, Organization norg) {
      DashboardRegistry.migrateRegistry(null, oorg, norg);

      for(IdentityID user : securityEngine.getUsers()) {
         DashboardRegistry.migrateRegistry(user, oorg, norg);
      }
   }

   public void copyDashboardRegistry(String oOID, String nOID) {
      DashboardRegistry.copyRegistry(null, oOID, nOID);

      for(IdentityID user : securityEngine.getUsers()) {
         DashboardRegistry.copyRegistry(user, oOID, nOID);
      }
   }

   private void clearDataSourceMetadata() throws Exception {
      XRepository repository = XFactory.getRepository();
      String[] dsNames = repository.getDataSourceNames();

      if(repository instanceof XEngine) {
         for(String datasource : dsNames) {
            ((XEngine) repository).removeMetaDataFiles(datasource);
         }
      }
   }

   private void updateStorageNames(Organization oorg, Organization norg) throws Exception {
      String oId = oorg.getId();
      String id = norg.getId();

      DashboardManager.getManager().migrateStorageData(oId, id);
      DependencyStorageService.getInstance().migrateStorageData(oId, id);
      RecycleBin.getRecycleBin().migrateStorageData(oId, id);
      IndexedStorage.getIndexedStorage().migrateStorageData(oorg, norg);

      updateBlobStorageName("__mv", oId, id, MVStorage.Metadata.class);
      updateBlobStorageName("__mvws", oId, id, MVWorksheetStorage.Metadata.class);
      updateBlobStorageName("__mvBlock", oId, id, BlockFileStorage.Metadata.class);
      updateBlobStorageName("__pdata", oId, id, EmbeddedTableStorage.Metadata.class);
      updateLibraryStorage(oId, id);
   }

   private <T extends Serializable> void updateBlobStorageName(String suffix, String oId, String id,
                                                               Class<T> type) throws Exception
   {
      BlobStorage<T> oStorage =
         SingletonManager.getInstance(BlobStorage.class, oId.toLowerCase() + suffix, false);
      BlobStorage<T> nStorage =
         SingletonManager.getInstance(BlobStorage.class, id.toLowerCase() + suffix, false);

      List<String> paths = oStorage.paths().collect(Collectors.toList());

      for(String path : paths) {
         try(InputStream input = oStorage.getInputStream(path);
             BlobTransaction<T> tx = nStorage.beginTransaction();
             OutputStream output = tx.newStream(path, type.getConstructor().newInstance()))
         {
            IOUtils.copy(input, output);
            tx.commit();
         }
      }

      oStorage.deleteBlobStorage();
   }

   private void updateLibraryStorage(String oId, String id) throws Exception {
      BlobStorage<LibManager.Metadata> oStorage =
      SingletonManager.getInstance(BlobStorage.class, oId.toLowerCase() + "__library", false);
      BlobStorage<LibManager.Metadata> nStorage =
         SingletonManager.getInstance(BlobStorage.class, id.toLowerCase() + "__library", false);

      List<String> paths = oStorage.paths().collect(Collectors.toList());

      for(String path : paths) {
         LibManager.Metadata metadata = oStorage.getMetadata(path);

         if(metadata.isDirectory()) {
            nStorage.createDirectory(path, metadata);
         }
         else {
            try(InputStream input = oStorage.getInputStream(path);
                BlobTransaction<LibManager.Metadata> tx = nStorage.beginTransaction();
                OutputStream output = tx.newStream(path, new LibManager.Metadata()))
            {
               IOUtils.copy(input, output);
               tx.commit();
            }
         }
      }

      oStorage.deleteBlobStorage();
   }

   private void updateOrgProperties(String oId, String id) {
      String oldPrefix = "inetsoft.org." + oId + ".";
      String newPrefix = "inetsoft.org." + id + ".";

      Properties properties = SreeEnv.getProperties();
      Set<Object> orgProperties = properties.keySet().stream()
         .filter(prop -> ((String) prop).startsWith(oldPrefix))
         .collect(Collectors.toSet());

      for(Object orgProp : orgProperties) {
         String oldName = (String) orgProp;
         String newName = newPrefix + (oldName).substring(oldPrefix.length());
         SreeEnv.setProperty(newName, SreeEnv.getProperty(oldName));
         SreeEnv.remove(oldName);
      }
   }

   /**
    * Get the warning string when change the user name or password of login
    * user.
    */
   public String getTimeOutWarning(Object ticket, String oldName) {
      IdentityID userid = null;

      if(ticket instanceof DefaultTicket) {
         userid = ((DefaultTicket) ticket).getName();
      }

      if(userid == null || !oldName.equals(userid.name)) {
         return null;
      }

      return Catalog.getCatalog().getString("em.servlet.sessionTimeout");
   }

   public void createIdentityPermissions(IdentityID identityID, ResourceType resourceType,
                                         Principal principal)
   {
      AuthorizationProvider authoc = securityProvider.getAuthorizationProvider();
      Permission perm = new Permission();
      Set<String> userGrants = new HashSet<>();
      IdentityID identity = IdentityID.getIdentityIDFromKey(principal.getName());

      if(Tool.equals(identityID.getOrganization(), identity.getOrganization())) {
         userGrants.add(identity.getName());
      }

      perm.setUserGrantsForOrg(ResourceAction.ADMIN, userGrants, OrganizationManager.getInstance().getCurrentOrgID());
      authoc.setPermission(resourceType, identityID, perm);
   }

   public List<IdentityModel> getPermission(String resourceName, ResourceType resourceType,
                                     Principal principal)
   {
      ResourceAction action;
      EnumSet<ResourceAction> actions;
      String rootOrgRoleName = Organization.getRootOrgRoleName(principal);
      String orgId = OrganizationManager.getInstance().getCurrentOrgID(principal);

      if(resourceType == ResourceType.SECURITY_ROLE && !"*".equals(resourceName) &&
         !Objects.equals(rootOrgRoleName, resourceName))
      {
         action = ResourceAction.ASSIGN;
         // Bug #38498, admin permission on a role implicitly grants assign permission
         actions = EnumSet.of(ResourceAction.ADMIN, ResourceAction.ASSIGN);
      }
      else {
         action = ResourceAction.ADMIN;
         actions = EnumSet.of(ResourceAction.ADMIN);
      }

      AuthorizationProvider authz = this.securityProvider.getAuthorizationProvider();
      Permission resourcePerm = authz.getPermission(resourceType, resourceName);
      Set<IdentityModel> grants = new HashSet<>();
      grants.addAll(getIdentityGrants(resourcePerm, action, Identity.USER, orgId));
      grants.addAll(getIdentityGrants(resourcePerm, action, Identity.ROLE, orgId));
      grants.addAll(getIdentityGrants(resourcePerm, action, Identity.GROUP, orgId));
      grants.addAll(getIdentityGrants(resourcePerm, action, Identity.ORGANIZATION, orgId));

      return grants.stream()
         .filter(identity -> securityProvider.checkAnyPermission(
            principal, getResourceType(identity.type()), identity.identityID().convertToKey(), actions))
         .collect(Collectors.toList());
   }

   public List<IdentityModel> getPermission(IdentityID resourceID, ResourceType resourceType, String orgID,
                                     Principal principal)
   {
      String roleRootName = Catalog.getCatalog(principal).getString("Roles");
      boolean roleRoot = resourceType == ResourceType.SECURITY_ROLE &&
         resourceID.getName().equals(roleRootName);
      ResourceAction action = roleRoot ?
         ResourceAction.ASSIGN : ResourceAction.ADMIN;
      EnumSet<ResourceAction> actions = roleRoot ?
         EnumSet.of(ResourceAction.ASSIGN) : EnumSet.of(ResourceAction.ADMIN);

      AuthorizationProvider authz = this.securityProvider.getAuthorizationProvider();
      Permission resourcePerm = authz.getPermission(resourceType, resourceID);
      Set<IdentityModel> grants = new HashSet<>();
      grants.addAll(getIdentityGrants(resourcePerm, action, Identity.USER, orgID));
      grants.addAll(getIdentityGrants(resourcePerm, action, Identity.ROLE, orgID));
      grants.addAll(getIdentityGrants(resourcePerm, action, Identity.GROUP, orgID));
      grants.addAll(getIdentityGrants(resourcePerm, action, Identity.ORGANIZATION, orgID));

      return grants.stream()
         .filter(identity -> securityProvider.checkAnyPermission(
            principal, getResourceType(identity.type()), identity.identityID().convertToKey(), actions))
         .collect(Collectors.toList());
   }

   private List<IdentityModel> getIdentityGrants(Permission resourcePerm, ResourceAction action,
                                                 int type, String orgId)
   {
      Set<IdentityID> grantNames = resourcePerm == null ? new HashSet<>() :
         resourcePerm.getOrgScopedGrants(action, type, orgId);
      return grantNames.stream()
         .map(id -> IdentityModel.builder().identityID(id).type(type).build())
         .collect(Collectors.toList());
   }

   public void setIdentityPermissions(IdentityID oldID, IdentityID newID, ResourceType resourceType,
                                      Principal principal, List<IdentityModel> permittedIdentities, String newOrg)
   {
      setIdentityPermissions(oldID, newID, resourceType, principal,
         permittedIdentities, newOrg, null);
   }

   public void setIdentityPermissions(IdentityID oldID, IdentityID newID,
                                      ResourceType resourceType, Principal principal,
                                      List<IdentityModel> permittedIdentities, String newOrg,
                                      String newOrgId)
   {
      String currOrg = newOrg;

      if(newOrg == null || newOrg.isEmpty()) {
         currOrg = OrganizationManager.getCurrentOrgName();
      }

      ResourceAction action;
      EnumSet<ResourceAction> actions;
      String rootOrgRoleName = new IdentityID(Catalog.getCatalog(principal).getString("Organization Roles"), newOrg).convertToKey();

      if(resourceType == ResourceType.SECURITY_ROLE &&
         !Catalog.getCatalog(principal).getString("Roles").equals(newID.name) &&
         !Objects.equals(rootOrgRoleName, newID.convertToKey()))
      {
         action = ResourceAction.ASSIGN;
         // Bug #38498, admin permission on a role implicitly grants assign permission
         actions = EnumSet.of(ResourceAction.ADMIN, ResourceAction.ASSIGN);
      }
      else {
         action = ResourceAction.ADMIN;
         actions = EnumSet.of(ResourceAction.ADMIN);
      }

      AuthorizationProvider authzProvider = securityProvider.getAuthorizationProvider();
      Permission permission = authzProvider.getPermission(resourceType, oldID);
      Set<String> userGrants = new HashSet<>();
      Set<String> groupGrants = new HashSet<>();
      Set<String> roleGrants = new HashSet<>();
      Set<String> globalRoleGrants = new HashSet<>();
      Set<String> organizationGrants = new HashSet<>();

      if(permittedIdentities != null) {
         for(IdentityModel idModel : permittedIdentities) {
            switch(idModel.type()) {
            case Identity.USER:
               userGrants.add(idModel.identityID().name);
               break;
            case Identity.GROUP:
               groupGrants.add(idModel.identityID().name);
               break;
            case Identity.ROLE:
               if(idModel.identityID().organization == null) {
                  globalRoleGrants.add(idModel.identityID().name);
               }
               else {
                  roleGrants.add(idModel.identityID().name);
               }
               break;
            case Identity.ORGANIZATION:
               organizationGrants.add(idModel.identityID().name);
               break;
            }
         }
      }

      if(permission != null) {
         EnumSet<ResourceAction> adminAction = EnumSet.of(ResourceAction.ADMIN);

         // If the principal does not have admin permission on some identities
         // they will not show up in the permittedIdentities parameter, so we need to re-add them

         for(Permission.PermissionIdentity userGrant : permission.getUserGrants(action)) {
            if(!securityProvider.checkAnyPermission(
               principal, getResourceType(Identity.USER),
               new IdentityID(userGrant.getName(), userGrant.getOrganization()).convertToKey(),
               adminAction))
            {
               userGrants.add(userGrant.getName());
            }
         }

         for(Permission.PermissionIdentity groupGrant : permission.getGroupGrants(action)) {
            if(!securityProvider.checkAnyPermission(
               principal, getResourceType(Identity.GROUP),
               new IdentityID(groupGrant.getName(), groupGrant.getOrganization()).convertToKey(),
               adminAction))
            {
               groupGrants.add(groupGrant.getName());
            }
         }

         for(Permission.PermissionIdentity roleGrant : permission.getRoleGrants(action)) {
            if(!securityProvider.checkAnyPermission(
               principal, getResourceType(Identity.ROLE), new IdentityID(roleGrant.getName(),
                  securityProvider.getOrgNameFromID(roleGrant.getOrganization())).convertToKey(),
               adminAction))
            {
               if(roleGrant.getOrganization() == null) {
                  globalRoleGrants.add(roleGrant.getName());
               }
               else {
                  roleGrants.add(roleGrant.getName());
               }
            }
         }

         for(Permission.PermissionIdentity orgGrant : permission.getOrganizationGrants(action)) {
            if(!securityProvider.checkAnyPermission(
               principal, getResourceType(Identity.ORGANIZATION),
               new IdentityID(orgGrant.getName(), orgGrant.getOrganization()).convertToKey(),
               adminAction))
            {
               organizationGrants.add(orgGrant.getName());
            }
         }
      }

      String orgId = newOrgId == null || Tool.isEmptyString(newOrgId) ? securityProvider.getOrgId(currOrg) : newOrgId;

      if(permission == null) {
         permission = new Permission();
      }

      permission.setUserGrantsForOrg(action, userGrants, orgId);
      permission.setGroupGrantsForOrg(action, groupGrants, orgId);
      permission.setRoleGrantsForOrg(action, roleGrants, orgId);
      permission.setRoleGrantsForOrg(action, globalRoleGrants, null);
      permission.setOrganizationGrantsForOrg(action, organizationGrants, orgId);
      permission.updateGrantAllByOrg(orgId, true);
      authzProvider.setPermission(resourceType, newID, permission);
   }

   private ResourceType getResourceType(int type) {
      ResourceType resourceType;
      switch(type) {
      case Identity.USER:
         resourceType = ResourceType.SECURITY_USER;
         break;
      case Identity.GROUP:
         resourceType = ResourceType.SECURITY_GROUP;
         break;
      case Identity.ROLE:
         resourceType = ResourceType.SECURITY_ROLE;
         break;
      case Identity.ORGANIZATION:
         resourceType = ResourceType.SECURITY_ORGANIZATION;
         break;
      default:
         return null;
      }

      return resourceType;
   }

   /**
    * Update an identity.
    */
   public void setIdentity(Identity identity,
                           EntityModel model,
                           AuthenticationProvider provider,
                           Principal principal)
      throws Exception
   {
      final ActionRecord actionRecord =
         SUtil.getActionRecord(principal, ActionRecord.ACTION_NAME_EDIT,
                               null, ActionRecord.OBJECT_TYPE_USERPERMISSION);
      IdentityInfoRecord identityInfoRecord = null;

      try {
         final int type = identity.getType();
         identityInfoRecord = getIdentityInfoRecord(model, type,
                                                    identity.getIdentityID(), provider, actionRecord);

         if(!(provider instanceof EditableAuthenticationProvider)) {
            return;
         }

         SecurityEngine.touch();
         EditableAuthenticationProvider eprovider = (EditableAuthenticationProvider) provider;
         IdentityID[] pusers = provider.getUsers();
         IdentityID[] pgroups = provider.getGroups();
         String[] porgs = provider.getOrganizations();
         pgroups = (pgroups == null) ? new IdentityID[0] : pgroups;
         final List<IdentityModel> members = model.members();
         List<IdentityID> userV = new ArrayList<>();
         List<IdentityID> groupV = new ArrayList<>();
         List<IdentityID> orgV = new ArrayList<>();
         List<IdentityID> roleV = new ArrayList<>();
         Map<IdentityID, String> memberMap = new HashMap<>();

         for(IdentityModel member : members) {
            memberMap.put(member.identityID(), member.parentNode());

            if(member.type() == Identity.USER) {
               userV.add(member.identityID());
            }
            else if(member.type() == Identity.ORGANIZATION) {
               orgV.add(member.identityID());
            }
            else if(member.type() == Identity.ROLE) {
               roleV.add(member.identityID());
            }
            else {
               groupV.add(member.identityID());
            }
         }

         if(type == Identity.USER) {
            setUserInfo((EditUserPaneModel) model, eprovider, groupV);
         }
         else if(type == Identity.GROUP) {
            setGroupInfo((FSGroup) identity, (EditGroupPaneModel) model, eprovider, pusers,
                         pgroups, userV, groupV, memberMap);
         }
         else if(type == Identity.ROLE) {
            setRoleInfo((EditRolePaneModel) model, eprovider, pusers, pgroups, porgs,
                        userV, groupV, orgV, principal);
         }
         else if(type == Identity.ORGANIZATION) {
            setOrganizationInfo((FSOrganization) identity, (EditOrganizationPaneModel) model,
                        eprovider, principal);
         }
      }
      catch(Exception e) {
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         throw e;
      }
      finally {
         if(identity.getType() == Identity.ORGANIZATION) {
            String org = (principal instanceof XPrincipal) ? ((XPrincipal) principal).getOrgId() : null;
            actionRecord.setOrganizationId(org);
         }

         Audit.getInstance().auditAction(actionRecord, principal);

         if(identityInfoRecord != null) {
            Audit.getInstance().auditIdentityInfo(identityInfoRecord, principal);
         }
      }
   }

   private IdentityInfoRecord getIdentityInfoRecord(EntityModel model,
                                                    int type,
                                                    IdentityID oldID,
                                                    AuthenticationProvider authenticationProvider,
                                                    ActionRecord actionRecord)
      throws Exception
   {
      final String name = model.name();
      actionRecord.setObjectName(name);
      String state = IdentityInfoRecord.STATE_NONE;

      if(type == Identity.USER) {
         state = ((EditUserPaneModel) model).status() ? IdentityInfoRecord.STATE_ACTIVE :
            IdentityInfoRecord.STATE_INACTIVE;
      }

      if(!name.equals(oldID.name)) {
         IdentityID[] identityIds = new IdentityID[0];
         switch(type) {
         case Identity.GROUP:
            identityIds = authenticationProvider.getGroups();
            break;
         case Identity.ORGANIZATION:
            identityIds = Arrays.stream(authenticationProvider.getOrganizations())
                           .map(o -> new IdentityID(o, oldID.organization)).toArray(IdentityID[]::new);
            break;
         case Identity.USER:
            identityIds = authenticationProvider.getUsers();
            break;
         case Identity.ROLE:
            identityIds = authenticationProvider.getRoles();
            break;
         }

         if(Tool.contains(identityIds, new IdentityID(name, oldID.organization))) {
            final String err = Catalog.getCatalog().getString("common.duplicateName");
            throw new Exception(err);
         }

         return SUtil.getIdentityInfoRecord(new IdentityID(name, oldID.organization), type, IdentityInfoRecord.ACTION_TYPE_RENAME,
                                            "Rename " + oldID.name + " to " + name, state);
      }
      else {
         return SUtil.getIdentityInfoRecord(oldID, type, IdentityInfoRecord.ACTION_TYPE_MODIFY,
                                            null, state);
      }
   }

   private void setUserInfo(EditUserPaneModel model,
                            EditableAuthenticationProvider eprovider,
                            List<IdentityID> groupV)
      throws Exception
   {
      List<IdentityModel> members = model.members();
      ArrayList<String> memberNames = new ArrayList<>();

      for(IdentityModel member : members) {
         memberNames.add(member.identityID().name);
      }

      String[] emails = model.email() == null || model.email().isEmpty()
         ? new String[0] : model.email().split(",");

      FSUser user = new FSUser(new IdentityID(model.name(), model.organization()));
      user.setRoles(model.roles().toArray(new IdentityID[0]));
      user.setGroups(memberNames.toArray(new String[0]));
      user.setEmails(emails);
      user.setAlias(model.alias());
      user.setActive(model.status());
      user.setOrganization(model.organization());
      addOrganizationMember(model.organization(),model.name(),eprovider);

      Properties localeProperties = SUtil.loadLocaleProperties();
      String localeString = null;

      Set<String> localeKeys = (Set) localeProperties.keySet();

      for(String key : localeKeys) {
         if(localeProperties.getProperty(key).equals(model.locale())) {
            localeString = key;
         }
      }

      user.setLocale(localeString);

      IdentityID oIdentity = new IdentityID(model.oldName(), model.organization());
      IdentityID[] groups = new IdentityID[groupV.size()];
      groupV.toArray(groups);
      user.setGroups(Arrays.stream(groups).map(id -> id.name).toArray(String[]::new));
      User oldUser = eprovider.getUser(oIdentity);

      if(model.password() != null) {
         HashedPassword npw = Tool.hash(model.password(), "bcrypt");

         if(oldUser != null && npw != null) {
            if(!npw.getHash().equals(oldUser.getPassword())) {
               user.setPassword(npw.getHash());
               user.setPasswordAlgorithm(npw.getAlgorithm());
               user.setPasswordSalt(null);
               user.setAppendPasswordSalt(false);
            }
         }
      }
      else {
         user.setPassword(oldUser.getPassword());
         user.setPasswordAlgorithm(oldUser.getPasswordAlgorithm());
         user.setPasswordSalt(oldUser.getPasswordSalt());
         user.setAppendPasswordSalt(oldUser.isAppendPasswordSalt());
      }

      syncIdentity(eprovider, user, oIdentity);
   }

   /**
    * Update group info
    */
   private void setGroupInfo(FSGroup oldGroup, EditGroupPaneModel model,
                             EditableAuthenticationProvider eprovider,
                             IdentityID[] pusers, IdentityID[] pgroups,
                             List<IdentityID> userV, List<IdentityID> groupV,
                             Map<IdentityID, String> memberMap) throws Exception
   {
      final IdentityID id = new IdentityID(model.name(), model.organization());
      final IdentityID oID = new IdentityID(oldGroup.getName(), oldGroup.getOrganization());
      final String locale = oldGroup.getLocale();
      final List<IdentityModel> members = model.members();
      final String[] memberNames = members.stream()
         .map(m -> m.identityID().name)
         .toArray(String[]::new);
      final IdentityID[] roles = model.roles().toArray(new IdentityID[0]);

      final FSGroup group = new FSGroup(id, locale, memberNames, roles);

      group.setOrganization(model.organization());
      addOrganizationMember(model.organization(),model.name(),eprovider);

      IdentityID[] mgroups = new IdentityID[groupV.size()];
      groupV.toArray(mgroups);

      // check overwrite
      if(!oID.equals(id) && Arrays.asList(pgroups).contains(id)) {
         pgroups = Tool.remove(pgroups, id);
      }

      for(IdentityID mgroup : mgroups) {
         checkInheritGroups(mgroup.getName(), eprovider, oldGroup);
      }

      for(IdentityID pgroupID : pgroups) {
         FSGroup pgroup = (FSGroup) eprovider.getGroup(pgroupID);

         if(pgroup != null && oID.name.equals(pgroup.getName()) &&
            Tool.equals(oID.organization, pgroup.getOrganization()))
         {
            group.setGroups(pgroup.getGroups());
            syncIdentity(eprovider, group, oID);
            continue;
         }

         String[] arr = getParents(pgroup.getIdentityID(), pgroup.getGroups(), id, groupV);

         if(arr != null) {
            pgroup.setGroups(arr);
            eprovider.setGroup(pgroup.getIdentityID(), pgroup);
         }
      }

      for(IdentityID puserID : pusers) {
         FSUser puser = (FSUser) eprovider.getUser(puserID);

         if(puser == null || !Tool.equals(puser.getOrganization(),group.getOrganization())) {
            continue;
         }

         String deletedGroup = memberMap.get(puser.getIdentityID());
         String[] arr = getParents(puser.getIdentityID(), puser.getGroups(), id, userV);

         if(arr == null) {
            continue;
         }

         List<String> tmpList = Arrays.asList(arr);
         List<String> arrList = new ArrayList<>(tmpList);

         for(int j = 0; deletedGroup != null && j < arrList.size(); j++) {
            if(deletedGroup.equals(arrList.get(j))) {
               arrList.remove(deletedGroup);
               break;
            }
         }

         arr = arrList.toArray(new String[0]);
         puser.setGroups(arr);
         eprovider.setUser(puser.getIdentityID(), puser);
      }
   }

   /**
    * Set roleInfo, check members changes or not.
    */
   private void setRoleInfo(EditRolePaneModel model,
                            EditableAuthenticationProvider eprovider,
                            IdentityID[] pusers, IdentityID[] pgroups, String[] porgs,
                            List<IdentityID> userV, List<IdentityID> groupV, List<IdentityID> orgV,
                            Principal principal)
      throws Exception
   {
      IdentityID newOrgID = new IdentityID(model.name(), model.organization());
      IdentityID oldOrgID = new IdentityID(model.oldName(), model.organization());

      FSRole role = new FSRole(newOrgID, model.description());
      role.setDefaultRole(model.defaultRole());
      role.setSysAdmin(model.isSysAdmin());
      role.setOrgAdmin(model.isOrgAdmin());
      role.setRoles(model.roles().toArray(new IdentityID[0]));

      IdentityID[] roles = role.getRoles();

      for(IdentityID roleName : roles) {
         checkInheritRoles(oldOrgID, roleName, eprovider);
      }

      syncIdentity(eprovider, role, new IdentityID(model.oldName(), model.organization()));

      for(IdentityID pgroupID : pgroups) {
         FSGroup pgroup = (FSGroup) eprovider.getGroup(pgroupID);

         if(pgroup == null || !OrganizationManager.getCurrentOrgName().equals(pgroup.getOrganization())) {
            continue;
         }

         IdentityID[] arr = getRoleParents(pgroup.getIdentityID(), pgroup.getRoles(),
                                   newOrgID, groupV);

         if(arr != null) {
            pgroup.setRoles(arr);
         }

         if(!newOrgID.equals(oldOrgID)) {
            IdentityID[] proles = Arrays.stream(pgroup.getRoles()).filter(r -> !r.equals(oldOrgID)).toArray(IdentityID[]::new);

            pgroup.setRoles(proles);
         }

         if(arr != null || !newOrgID.equals(oldOrgID)) {
            eprovider.setGroup(pgroup.getIdentityID(), pgroup);
         }
      }

      for(IdentityID puserName : pusers) {
         if((role.getOrganization() != null && !Tool.equals(puserName.organization, role.getOrganization())))
         {
            continue;
         }


         FSUser puser = (FSUser) eprovider.getUser(puserName);

         if(puser == null || (model.organization() != null && !OrganizationManager.getCurrentOrgName().equals(puser.getOrganization()))) {
            continue;
         }

         IdentityID[] arr = getRoleParents(puser.getIdentityID(), puser.getRoles(), newOrgID, userV);

         if(arr != null) {
            puser.setRoles(arr);
         }

         if(!newOrgID.equals(oldOrgID)) {
            IdentityID[] proles = Arrays.stream(puser.getRoles()).filter(r -> !r.equals(oldOrgID)).toArray(IdentityID[]::new);

            puser.setRoles(proles);
         }

         if(arr != null || !newOrgID.equals(oldOrgID)) {
            eprovider.setUser(puser.getIdentityID(), puser);
         }
      }

      for(String porgName : porgs) {
         FSOrganization porg = (FSOrganization) eprovider.getOrganization(porgName);

         if(porg == null) {
            continue;
         }

         if(orgV.contains(porgName)) {
            eprovider.setOrganization(porgName, porg);
         }
      }
   }

   private void setOrganizationInfo(FSOrganization oldOrg, EditOrganizationPaneModel model,
                                    EditableAuthenticationProvider eprovider, Principal principal)
      throws Exception
   {
      final String name = model.name();
      final String id = model.id();
      FSOrganization newOrg = new FSOrganization(name);
      List<IdentityModel> members = model.members();

      List<String> memberNames = members.stream()
         .map(IdentityModel::identityID)
         .map(i -> i.name)
         .collect(Collectors.toList());
      String[] oldMembers = oldOrg.getMembers();

      if(oldMembers != null) {
         Arrays.stream(oldMembers)
            .filter(m -> securityProvider.getUser(new IdentityID(m, oldOrg.getName())) != null)
            .filter(m -> !securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                                           m, ResourceAction.ADMIN))
            .forEach(m -> memberNames.add(m));
      }

      for(IdentityModel member : model.members()) {
         if(member.type() == Identity.ROLE) {
            Role role = eprovider.getRole(member.identityID());

            //global roles cannot be assigned as members of an organization
            if(role != null && role.getOrganization() == null) {
               throw new MessageException(Catalog.getCatalog().getString("em.security.GlobalRoleMemberError"));
            }
         }
      }

      newOrg.setId(id);
      newOrg.setMembers(memberNames.toArray(new String[0]));
      newOrg.setActive(model.status());

      String fromOrgID = eprovider.getOrganization(model.oldName()) != null ?
         eprovider.getOrganization(model.oldName()).getId() : null;

      if(!memberNames.isEmpty() &&
         (model.oldName() == null ||
            !Tool.equals(oldOrg.getMembers(), memberNames) ||
            !Tool.equals(model.oldName(), model.name()) ||
            !Tool.equals(fromOrgID, model.id())))
      {
         updateOrganizationMembers(newOrg, eprovider);
      }



      if(!Tool.equals(fromOrgID, model.id())) {
         updateOrgScopedDataSpace(fromOrgID, model.id());
      }

      Properties localeProperties = SUtil.loadLocaleProperties();
      String localeString = null;
      Set<String> localeKeys = (Set) localeProperties.keySet();

      for(String key : localeKeys) {
         if(localeProperties.getProperty(key).equals(model.locale())) {
            localeString = key;
         }
      }

      newOrg.setLocale(localeString);
      newOrg.setTheme(model.theme());
      syncIdentity(eprovider, newOrg, new IdentityID(model.oldName(), model.oldName()));
   }

   private void updateOrgScopedDataSpace(String oldID, String newID) {
      DataSpace dataspace = DataSpace.getDataSpace();
      String[] paths = DataSpace.getOrgScopedPaths(oldID);

      for(String path : paths) {
         if(!dataspace.exists(null, path)) {
            continue;
         }

         String toPath = path.replace(oldID, newID);
         dataspace.rename(path, toPath);
      }
   }

   private void addOrganizationMember(String orgName, String memberName, EditableAuthenticationProvider provider) {
      Organization org = provider.getOrganization(orgName);
      List<String> members = org.getMembers() != null ? new ArrayList<>(Arrays.asList(org.getMembers())) : new ArrayList<>();
      members.add(memberName);
      provider.setOrganization(orgName, org);
   }

   /**
    * Check group dependency.
    */
   private void checkInheritGroups(String memberOfGroup,
                                   EditableAuthenticationProvider provider,
                                   FSGroup group)
   {
      if(group != null) {
         String[] pgroups = group.getGroups();
         pgroups = (pgroups == null) ? new String[0] : pgroups;

         List<String> groupsList = Arrays.asList(pgroups);

         if(groupsList.contains(memberOfGroup)) {
            pgroups = Tool.remove(pgroups, memberOfGroup);
            group.setGroups(pgroups);
            provider.setGroup(group.getIdentityID(), group);
         }

         for(String pgroupName : pgroups) {
            final Group childGroup = provider.getGroup(new IdentityID(pgroupName, group.getOrganization()));
            checkInheritGroups(memberOfGroup, provider, (FSGroup) childGroup);
         }
      }
   }

   /**
    * Check if the em user is self.
    */
   private boolean isSelfAndEMUser(Principal principal, IdentityID identityID, int type) {
      if(type != Identity.USER) {
         return false;
      }

      return identityID.equals(IdentityID.getIdentityIDFromKey(principal.getName()));
   }

   /**
    * Check if the role is self.
    */
   private boolean isSelfRole(Identity principal, IdentityID identityID, int type) {
      if(type != Identity.ROLE) {
         return false;
      }

      if(principal == null || principal.getRoles() == null) {
         return false;
      }

      return Arrays.asList(principal.getRoles()).contains(identityID);
   }

   public void clearRootPermittedIdentities(String orgName, String orgID, Principal principal) {
      //Users
      String rootUserName = Catalog.getCatalog().getString("Users");
      IdentityID fromRootUserID = new IdentityID(rootUserName, orgName);

      setIdentityPermissions(fromRootUserID, fromRootUserID, ResourceType.SECURITY_USER, principal,
                             Collections.emptyList(), orgName, orgID);

      //Groups
      String rootGroupName = Catalog.getCatalog().getString("Groups");
      IdentityID fromRootGroupID = new IdentityID(rootGroupName, orgName);
      setIdentityPermissions(fromRootGroupID, fromRootGroupID, ResourceType.SECURITY_GROUP, principal,
                             Collections.emptyList(), orgName, orgID);

      //Roles
      String rootRoleName = Catalog.getCatalog().getString("Roles");
      IdentityID fromRootID = new IdentityID(rootRoleName, orgName);
      setIdentityPermissions(fromRootID, fromRootID, ResourceType.SECURITY_ROLE, principal,
                             Collections.emptyList(), orgName, orgID);

      String rootOrgRoleName = Catalog.getCatalog().getString("Organization Roles");
      IdentityID fromRootOrgRoleID = new IdentityID(rootOrgRoleName, orgName);
      setIdentityPermissions(fromRootOrgRoleID, fromRootOrgRoleID, ResourceType.SECURITY_ROLE, principal,
                             Collections.emptyList(), orgName, orgID);

      //Organization
      IdentityID oldOrgID = new IdentityID(orgName, orgName);
      setIdentityPermissions(oldOrgID, oldOrgID, ResourceType.SECURITY_ORGANIZATION, principal,
                             Collections.emptyList(), orgName, orgID);
   }

   public void updateIdentityPermissions(int type, IdentityID oldName, IdentityID newName, String oldOrgId, String newOrgId, boolean doReplace) {
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      Organization oldOrganization = oldOrgId == null ?
         null : provider.getOrganization(provider.getOrgNameFromID(oldOrgId));

      for(Tuple3<ResourceType, String, Permission> permissionSet : provider.getPermissions()) {
         for (ResourceAction action : ResourceAction.values()) {
            Permission permission = permissionSet.getThird();

            if(permission != null) {
               if(type == Identity.USER) {
                  for(IdentityID granteeName : permission.getOrgScopedUserGrants(action, oldOrganization).toArray(new IdentityID[0])) {
                     if(oldName != null && oldName.name.equals(granteeName.name)) {
                        //rename old grantee to new name
                        updateIdentityPermission(type, newName, oldName, oldOrganization, newOrgId, permission, action, permissionSet.getFirst(), permissionSet.getSecond(), doReplace);
                     }
                  }
               }
               else if(type == Identity.GROUP) {
                  for(IdentityID granteeName : permission.getOrgScopedGroupGrants(action, oldOrganization).toArray(new IdentityID[0])) {
                     if(oldName != null && oldName.name.equals(granteeName.name)) {
                        //rename old grantee to new name
                        updateIdentityPermission(type, newName, oldName, oldOrganization, newOrgId, permission, action, permissionSet.getFirst(), permissionSet.getSecond(), doReplace);
                     }
                  }
               }
               else if(type == Identity.ROLE) {
                  for(IdentityID granteeName : permission.getOrgScopedRoleGrants(action, oldOrganization).toArray(new IdentityID[0])) {
                     if(oldName != null && oldName.name.equals(granteeName.name)) {
                        //rename old grantee to new name
                        updateIdentityPermission(type, newName, oldName, oldOrganization, newOrgId, permission, action, permissionSet.getFirst(), permissionSet.getSecond(), doReplace);
                     }
                  }
               }
               else if(type == Identity.ORGANIZATION) {
                  for(IdentityID granteeName : permission.getOrgScopedOrganizationGrants(action, oldOrganization).toArray(new IdentityID[0])) {
                     if(oldName != null && oldName.name.equals(granteeName.name)) {
                        updateIdentityPermission(type, newName, oldName, oldOrganization, newOrgId, permission, action, permissionSet.getFirst(), permissionSet.getSecond(), doReplace);
                     }
                  }
               }
            }
         }
      }
   }

   private void updateIdentityPermission(int type, IdentityID newName, IdentityID oldName, Organization oldOrg, String newOrgId, Permission permission,
                                         ResourceAction action, ResourceType rType, String rpath, boolean doReplace) {
      Set<String> userGrants = new HashSet<>();
      Set<String> groupGrants = new HashSet<>();
      Set<String> roleGrants = new HashSet<>();
      Set<String> organizationGrants = new HashSet<>();
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();

      if(permission != null) {
         if(doReplace) {
            permission.getOrgScopedUserGrants(action, oldOrg).stream()
               .map(id -> id.name)
               .filter(u -> !u.equals(oldName.name))
               .forEach(userGrants::add);
            permission.getOrgScopedGroupGrants(action, oldOrg).stream()
               .map(id -> id.name)
               .filter(u -> !u.equals(oldName.name))
               .forEach(groupGrants::add);
            permission.getOrgScopedRoleGrants(action, oldOrg).stream()
               .map(id -> id.name)
               .filter(u -> !u.equals(oldName.name))
               .forEach(roleGrants::add);
            permission.getOrgScopedOrganizationGrants(action, oldOrg).stream()
               .map(id -> id.name)
               .filter(u -> !u.equals(oldName.name))
               .forEach(organizationGrants::add);

            permission.setUserGrantsForOrg(action, new HashSet<>(), oldOrg.getOrganizationID());
            permission.setGroupGrantsForOrg(action, new HashSet<>(), oldOrg.getOrganizationID());
            permission.setRoleGrantsForOrg(action, new HashSet<>(), oldOrg.getOrganizationID());
            permission.setOrganizationGrantsForOrg(action, new HashSet<>(), oldOrg.getOrganizationID());
         }
      }
      else {
         permission = new Permission();
      }

      if(newName !=null && !newName.name.isEmpty()) {
         switch(type) {
         case Identity.USER:
            userGrants.add(newName.name);
            break;
         case Identity.GROUP:
            groupGrants.add(newName.name);
            break;
         case Identity.ROLE:
            roleGrants.add(newName.name);
            break;
         case Identity.ORGANIZATION:
            organizationGrants.add(newName.name);
            break;
         }
      }

      if(!userGrants.isEmpty()) {
         permission.setUserGrantsForOrg(action, userGrants, newOrgId);
      }

      if(!groupGrants.isEmpty()) {
         permission.setGroupGrantsForOrg(action, groupGrants, newOrgId);
      }

      if(!roleGrants.isEmpty()) {
         permission.setRoleGrantsForOrg(action, roleGrants, newOrgId);
      }

      if(!organizationGrants.isEmpty()) {
         permission.setOrganizationGrantsForOrg(action, organizationGrants, newOrgId);
      }

      if(permission.isOrgInPerm(action, newOrgId)) {
         permission.updateGrantAllByOrg(newOrgId, true);
      }

      provider.setPermission(rType, rpath, permission);
   }


   public void addCopiedIdentityPermission(IdentityID fromIdentity, IdentityID newIdentity, String newOrgId, int type) {
      //delete organization name inside of permissions
      if(type == Identity.ORGANIZATION) {
         String oid = SecurityEngine.getSecurity().getSecurityProvider().getOrganization(fromIdentity.organization).getId();
         String id = SecurityEngine.getSecurity().getSecurityProvider().getOrganization(newIdentity.organization) != null ?
                     SecurityEngine.getSecurity().getSecurityProvider().getOrganization(newIdentity.organization).getId():
                     newIdentity.name.equals(Organization.getTemplateOrganizationName()) ?
                     Organization.getTemplateOrganizationID() :
                        newOrgId;
         updateIdentityPermissions(type, fromIdentity, newIdentity, oid , id, false);
      }
      else {
         String orgName;
         SecurityProvider sProvider = SecurityEngine.getSecurity().getSecurityProvider();

         switch(type) {
            case(Identity.USER):
               orgName = sProvider.getUser(fromIdentity) != null ? sProvider.getUser(fromIdentity).getOrganization() :
                                                               OrganizationManager.getCurrentOrgName();
               break;
            case(Identity.GROUP):
               orgName = sProvider.getGroup(fromIdentity) != null ? sProvider.getGroup(fromIdentity).getOrganization() :
                                                                OrganizationManager.getCurrentOrgName();
               break;
            default:
               orgName = sProvider.getRole(fromIdentity) != null ? sProvider.getRole(fromIdentity).getOrganization() :
                                                               OrganizationManager.getCurrentOrgName();
               break;
         }

         String orgId = sProvider.getOrgId(orgName);
         updateIdentityPermissions(type, fromIdentity, newIdentity, orgId, newOrgId, false);
      }
   }

   private void updateAutoSaveFiles(Organization oorg, Organization norg) {
      if(oorg.getName().equals(norg.getName())) {
         return;
      }

      FileSystemService fileSystemService = FileSystemService.getInstance();
      String path = SreeEnv.getProperty("sree.home") + "/" + "autoSavedFile/recycle";
      File folder = fileSystemService.getFile(path);

      if(!folder.exists()) {
         return;
      }

      File[] list = folder.listFiles();

      if(list == null) {
         return;
      }

      for(File file : list) {
         String asset = file.getName();

         if(!file.isFile()) {
            continue;
         }

         String[] attrs = Tool.split(asset, '^');

         if(attrs.length > 3) {
            String user = attrs[2];
            user = "anonymous".equals(user) ? "_NULL_" : user;

            if(user == null || Tool.equals(user, "_NULL_")) {
               continue;
            }

            IdentityID userID = IdentityID.getIdentityIDFromKey(user);

            if(oorg.getName().equals(userID.getOrganization())) {
               userID.setOrganization(norg.getName());
               attrs[2] = userID.convertToKey();
               String newFilePath = path + "/" + String.join("^", attrs);
               File newFile = fileSystemService.getFile(newFilePath);
               fileSystemService.rename(file, newFile);
            }
         }
      }
   }

   private final SecurityEngine securityEngine;
   private final SecurityProvider securityProvider;
   private final Logger LOG = LoggerFactory.getLogger(IdentityService.class);
}
