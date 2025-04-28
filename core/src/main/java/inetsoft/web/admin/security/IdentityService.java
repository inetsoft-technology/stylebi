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

import inetsoft.mv.MVManager;
import inetsoft.mv.MVWorksheetStorage;
import inetsoft.mv.data.MVStorage;
import inetsoft.mv.fs.FSService;
import inetsoft.mv.fs.internal.BlockFileStorage;
import inetsoft.mv.mr.XJobPool;
import inetsoft.report.LibManager;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.*;
import inetsoft.sree.web.SessionLicenseManager;
import inetsoft.sree.web.SessionLicenseService;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistry;
import inetsoft.storage.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XEngine;
import inetsoft.uql.util.*;
import inetsoft.util.*;
import inetsoft.util.audit.*;
import inetsoft.util.css.CSSDictionary;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.favorites.FavoriteList;
import inetsoft.web.admin.schedule.IdentityChangedMessage;
import inetsoft.web.admin.security.user.*;

import java.io.*;
import java.rmi.RemoteException;
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
   public IdentityService(SecurityEngine securityEngine,
                          SecurityProvider securityProvider,
                          IdentityThemeService themeService,
                          AuthenticationService authenticationService)
   {
      this.securityEngine = securityEngine;
      this.securityProvider = securityProvider;
      this.themeService = themeService;
      this.authenticationService = authenticationService;
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
               objectName.append(identityId != null ? identityId.getName() : null).append(" ");

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
                  logoutSession(identityId);
               }

               Cluster.getInstance().sendMessage(new IdentityChangedMessage(type, identityId));

               syncIdentity(provider, identityId != null ? new DefaultIdentity(identityId, type) :
                  new DefaultIdentity(), null);
            }
            catch(Exception ex) {
               actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
               LOG.warn("Failed to delete identity: {}", identityId, ex);
               warnings.add("Failed to delete identity " + identityId + ".");
            }
            finally {
               if(identityInfoRecord != null &&
                  !actionRecord.getActionStatus().equals(ActionRecord.ACTION_STATUS_FAILURE))
               {
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

         if(!warnings.isEmpty()) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         }
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

   private void logoutSession(IdentityID user) {
      SessionLicenseManager sessionLicenseManager =
         SessionLicenseService.getSessionLicenseService();

      if(sessionLicenseManager == null) {
         return;
      }

      Set<SRPrincipal> principals = sessionLicenseManager.getActiveSessions();
      Iterator<SRPrincipal> iterator  = principals.iterator();

      while(iterator.hasNext()) {
         SRPrincipal principal = iterator.next();

         if(Tool.equals(principal.getIdentityID(), user)) {
            authenticationService.logout(principal, true);
         }
      }
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
         identity = provider.getOrganization(identityId.orgID);
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
         manager.clear(identityId.orgID);
      }
      else {
         if((type == Identity.USER || type == Identity.GROUP) && !identityId.equals(oID)) {
            smanager.identityRenamed(oID, identity);
            dmanager.setDashboards(nid, dmanager.getDashboards(oid));
            dmanager.setDashboards(oid, null);
            dmanager.removeDashboards(oid);
            DashboardRegistry.clear(oID);
         }
      }

      AuthorizationChain authoc = (AuthorizationChain) securityProvider.getAuthorizationProvider();

      if(identity.getType() == Identity.USER) {
         //AssetRepository rep = AssetUtil.getAssetRepository(false);
         if(oID == null) {
            //delete user identityId inside of permissions
            RepletRegistry.removeUser(identityId);
            //rep.removeUser(identityId);
            DashboardRegistry.clear(identityId);
            eprovider.removeUser(identityId);
            updateIdentityPermissions(type, identityId, null, identityId.orgID, identityId.orgID,true);
         }
         else {
            if(!identityId.equals(oID)) {
               String orgId = identityId.orgID;
               //rep.renameUser(oID, identityId);
               RepletRegistry.renameUser(oID, identityId);
               DashboardRegistry.clear(identityId);
               DashboardRegistry.renameUser(oID, identityId);
               DashboardRegistry.clear(oID);
               updateUserAutoSaveFiles(oID, identityId);
               //update user identityId inside of permissions
               updateIdentityPermissions(type, oID, identityId, orgId, orgId, true);
            }

            eprovider.setUser(oID, (User) identity);
         }
      }
      else if(identity.getType() == Identity.ORGANIZATION) {
         if(oID == null) {
            Organization oOrg = eprovider.getOrganization(identityId.orgID);
            DashboardRegistry.clear(identityId);
            clearDataSourceMetadata();

            if(oOrg != null) {
               String orgID = oOrg.getOrganizationID();
               PortalThemesManager themesManager = PortalThemesManager.getManager();
               eprovider.removeOrganization(identityId.orgID);

               // delete organization identityId inside of permissions
               authoc.cleanOrganizationFromPermissions(orgID);

               DataCycleManager.getDataCycleManager().clearDataCycles(orgID);
               removeOrgProperties(orgID);
               removeOrgScopedDataSpaceElements(oOrg);
               updateRepletRegistry(orgID, null);
               themeService.removeTheme(orgID);
               themesManager.removeCSSEntry(orgID);
               CSSDictionary.resetDictionaryCache();
               themesManager.save();
               removeStorages(orgID);
               DataSourceRegistry.getRegistry().clearCache(orgID);
               FSService.clearServerNodeCache(orgID);
               XJobPool.resetOrgCache(orgID);
               RepletRegistry.clearOrgCache(orgID);
            }

            // deleting current organization should reset curOrg
            OrganizationManager.getInstance().setCurrentOrgID(Organization.getDefaultOrganizationID());
         }
         else {
            String oId = oID.orgID;
            String id = ((Organization) identity).getId();
            Organization oldOrg = eprovider.getOrganization(oId);

            if(!identityId.equals(oID)) {
               eprovider.copyOrganization(oldOrg, (Organization) identity, id, identity.getName(),
                  this, themeService, ThreadContext.getContextPrincipal(), true);
            }

            // Update current orgID
            OrganizationManager.getInstance().setCurrentOrgID(id);
         }
      }
      else {
         if(oID == null) {
            if(type == Identity.GROUP) {
               //delete group identityId inside of permissions
               String orgId = eprovider.getGroup(identityId).getOrganizationID();
               eprovider.removeGroup(identityId);
               updateIdentityPermissions(type, identityId, null, orgId, orgId, true);
               updatePrincipalGroup(oID, identityId);
            }
            else {
               //delete role identityId inside of permissions
               String orgId = eprovider.getRole(identityId).getOrganizationID();
               eprovider.removeRole(identityId);
               updateIdentityPermissions(type, identityId, null, orgId, orgId, true);
            }
         }
         else {
            boolean changed = !identityId.equals(oID);

            if(changed) {
               if(type == Identity.GROUP) {
                  //update group name inside of permissions
                  String orgId = eprovider.getGroup(oID).getOrganizationID();
                  updateIdentityPermissions(type, oID, identityId, orgId, orgId, true);
                  updatePrincipalGroup(oID, identityId);
               }
               else {
                  //update role identityId inside of permissions
                  String orgId = eprovider.getRole(oID) != null && eprovider.getRole(oID).getOrganizationID() != null ?
                     eprovider.getRole(oID).getOrganizationID() : null;
                  updateIdentityPermissions(type, oID, identityId, orgId, orgId, true);
                  syncRoles(eprovider, oID, identityId);
               }
            }

            if(type == Identity.GROUP) {
               eprovider.setGroup(identityId, (Group) identity);
            }
            else {
               eprovider.setRole(identityId, (Role) identity);
            }

            if(changed) {
               if(type == Identity.GROUP) {
                  eprovider.removeGroup(oID);
               }
               else {
                  eprovider.removeRole(oID);
               }
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
               inheritRole.setOrgID(ninheritRole.orgID);
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

   private void updateOrgEditedGrantAll(Permission permission, String oorgId, String norgId) {
      Map<String, Boolean> orgEditedGrantAll = permission.getOrgEditedGrantAll();
      Map<String, Boolean> newOrgEditedGrantAll = Tool.deepCloneMap(orgEditedGrantAll);
      boolean changed = !Tool.equals(oorgId, norgId);

      if(!changed || !orgEditedGrantAll.keySet().contains(oorgId)) {
         return;
      }

      if(isOrgInPerm(permission, norgId)) {
         newOrgEditedGrantAll.put(norgId, orgEditedGrantAll.get(oorgId));
      }

      permission.setOrgEditedGrantAll(newOrgEditedGrantAll);
   }

   private boolean isOrgInPerm(Permission permission, String norgId) {
      for(ResourceAction action: ResourceAction.values()) {
         if(permission.isOrgInPerm(action, norgId)) {
            return true;
         }
      }

      return false;
   }

   public void deleteOrganizationMembers(String orgID, EditableAuthenticationProvider eprovider) {
      IdentityID[] users = eprovider.getUsers();
      IdentityID[] groups = eprovider.getGroups();
      IdentityID[] roles = eprovider.getRoles();
      KeyValueStorage<FavoriteList> favorites =
         SingletonManager.getInstance(KeyValueStorage.class, "emFavorites");

      for(int i = 0; i < users.length; i++) {
         FSUser user = (FSUser) eprovider.getUser(users[i]);

         if(orgID.equals(user.getOrganizationID())) {
            //users are tied to org, delete if deleted
            RepletRegistry.removeUser(user.getIdentityID());
            eprovider.removeUser(user.getIdentityID());
            addCopiedIdentityPermission(user.getIdentityID(), null, "", Identity.USER, false);
            favorites.remove(user.getIdentityID().convertToKey());
         }
      }

      for(int i = 0; i < groups.length; i++) {
         FSGroup group = (FSGroup) eprovider.getGroup(groups[i]);

         if(orgID.equals(group.getOrganizationID())) {
            //group is tied to org, delete if deleted
            eprovider.removeGroup(group.getIdentityID());
            addCopiedIdentityPermission(group.getIdentityID(), null,"", Identity.GROUP, false);
         }
      }

      for(int i = 0; i < roles.length; i++) {
         FSRole role = (FSRole) eprovider.getRole(roles[i]);

         if(orgID.equals(role.getOrganizationID())) {
            //role is tied to org, delete if deleted
            eprovider.removeRole(role.getIdentityID());
            addCopiedIdentityPermission(role.getIdentityID(), null, "", Identity.ROLE, false);
         }
      }

   }

   private void updateOrganizationMembers(Organization identity, List<IdentityModel> memberModels,
                                          String oldOrgID,
                                          EditableAuthenticationProvider eprovider)
   {
      String orgID = identity.getId();
      List<String> members = Arrays.asList(identity.getMembers());
      IdentityID[] users = Arrays.stream(eprovider.getUsers()).filter(u -> Tool.equals(oldOrgID, u.orgID)).toArray(IdentityID[]::new);
      IdentityID[] groups = Arrays.stream(eprovider.getGroups()).filter(u -> Tool.equals(oldOrgID, u.orgID)).toArray(IdentityID[]::new);
      IdentityID[] roles = Arrays.stream(eprovider.getRoles()).filter(u -> Tool.equals(oldOrgID, u.orgID)).toArray(IdentityID[]::new);
      IdentityID[] newUsers = memberModels.stream()
         .filter(member -> member.type() == Identity.USER)
         .filter(newUser -> Arrays.stream(users).noneMatch(oldUser -> oldUser.getName().equals(newUser.identityID().getName())))
         .map(IdentityModel::identityID).toArray(IdentityID[]::new);
      IdentityID[] newGroups = memberModels.stream()
         .filter(member -> member.type() == Identity.GROUP)
         .filter(newGroup -> Arrays.stream(users).noneMatch(oldGroup -> oldGroup.getName().equals(newGroup.identityID().getName())))
         .map(IdentityModel::identityID)
         .toArray(IdentityID[]::new);
      IdentityID[] newRoles = memberModels.stream()
         .filter(member -> member.type() == Identity.ROLE)
         .filter(newRole -> Arrays.stream(users).noneMatch(oldRole -> oldRole.getName().equals(newRole.identityID().getName())))
         .map(IdentityModel::identityID)
         .toArray(IdentityID[]::new);
      boolean orgIdChange = !OrganizationManager.getInstance().getCurrentOrgID().equals(identity.getId());
      boolean orgNameChanged = !Tool.equals(orgIdChange, oldOrgID);

      AuthorizationChain authoc = ((AuthorizationChain) securityProvider.getAuthorizationProvider());
      KeyValueStorage<FavoriteList> favorites =
         SingletonManager.getInstance(KeyValueStorage.class, "emFavorites");

      for(int i = 0; i < users.length; i++) {
         FSUser user = (FSUser) eprovider.getUser(users[i]);
         IdentityID oldID = user.getIdentityID();

         if(orgIdChange && members.contains(user.getName())) {
            //add to this Organization
            user.setOrganization(orgID);
            IdentityID[] userRoles = user.getRoles();

            for(IdentityID identityID : userRoles) {
               if(Tool.equals(identityID.getOrgID(), oldOrgID)) {
                  identityID.setOrgID(orgID);
               }
            }

            if(orgIdChange || orgNameChanged) {
               //Update replet registry here.
               RepletRegistry.changeOrgID(oldID, OrganizationManager.getInstance().getCurrentOrgID(), identity.getId());
               DashboardRegistry.migrateRegistry(oldID, securityProvider.getOrganization(OrganizationManager.getInstance().getCurrentOrgID()), identity);
            }

            eprovider.setUser(user.getIdentityID(), user);
            eprovider.removeUser(oldID);
            RepletRegistry.renameUser(oldID, user.getIdentityID());
            // Move em favorites to new user
            FavoriteList userFav = favorites.get(oldID.convertToKey());

            if(userFav != null) {
               favorites.put(user.getIdentityID().convertToKey(), userFav);
               favorites.remove(oldID.convertToKey());
            }
         }
         else if(!members.contains(user.getName())) {
            eprovider.removeUser(oldID);
         }
      }

      for(int i = 0; i < newUsers.length; i ++) {
         FSUser user = new FSUser(newUsers[i]);
         eprovider.setUser(user.getIdentityID(), user);
      }

      for(int i = 0; i < groups.length; i++) {
         FSGroup group = (FSGroup) eprovider.getGroup(groups[i]);

         if(orgID.equals(group.getOrganizationID())) {
            if(!members.contains(group.getName())) {
               //group is tied to org, delete if removed as member
               eprovider.removeGroup(group.getIdentityID());
            }
            //else if name change or id change, update permissions
            else if(orgIdChange) {
               //clone new group with correct name
               updateGroupForOrg(identity, group, orgID, eprovider, authoc);
            }
         }
         else if(members.contains(group.getName())) {
            //clone new group with correct name
            updateGroupForOrg(identity, group, orgID, eprovider, authoc);
         }
      }

      for(int i = 0; i < newGroups.length; i ++) {
         FSGroup group = new FSGroup(newGroups[i]);
         eprovider.setGroup(group.getIdentityID(), group);
      }

      for(int i = 0; i < roles.length; i++) {
         FSRole role = (FSRole) eprovider.getRole(roles[i]);

         if(orgID.equals(role.getOrganizationID())) {
            if(!members.contains(role.getName())) {
               //role is tied to org, delete if removed as member
               eprovider.removeRole(role.getIdentityID());
            }
            else if(orgIdChange) {
               updateRoleForOrg(identity, role, orgID, eprovider, authoc);
            }
         }
         else if(members.contains(role.getName())) {
            updateRoleForOrg(identity, role, orgID, eprovider, authoc);
         }
      }

      for(int i = 0; i < newRoles.length; i ++) {
         FSRole role = new FSRole(newRoles[i]);
         eprovider.setRole(role.getIdentityID(), role);
      }
   }

   private void updateRoleForOrg(Organization identity, FSRole role, String orgID,
                                 EditableAuthenticationProvider eprovider, AuthorizationChain authoc)
   {
      boolean authUpdated = false;

      if(!OrganizationManager.getInstance().getCurrentOrgID().equals(identity.getId())) {
         //clone new role with correct name
         IdentityID newName = new IdentityID(role.getName(), orgID);
         FSRole newRole = new FSRole(newName, role.getRoles());
         newRole.setDesc(role.getDescription());
         newRole.setDefaultRole(role.isDefaultRole());
         newRole.setOrgAdmin(role.isOrgAdmin());
         newRole.setSysAdmin(role.isSysAdmin());
         //do not overwrite existing role of this name
         if(eprovider.getRole(newName) == null) {
            //update role in permissions
            updateIdentitiesContainingRole(role.getIdentityID(), newName, orgID, eprovider);
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
      if(!OrganizationManager.getInstance().getCurrentOrgID().equals(identity.getId())) {
         IdentityID newName = new IdentityID(group.getIdentityID().name, orgName);
         FSGroup newGroup = new FSGroup(newName, group.getLocale(),
                                        group.getGroups(), group.getRoles());
         //do not overwrite existing group of this name
         if(eprovider.getGroup(newName) == null) {
            eprovider.setGroup(newName, newGroup);
            //update permission for this group
            authUpdated = true;
            updateIdentityPermissions(
               Identity.GROUP, group.getIdentityID(), newName,
               OrganizationManager.getInstance().getCurrentOrgID(), identity.getId(), true);
            eprovider.removeGroup(group.getIdentityID(), false);
         }
         else {
            eprovider.removeGroup(group.getIdentityID(), false);
         }
      }

      if(!OrganizationManager.getInstance().getCurrentOrgID().equals(identity.getId()) && !authUpdated) {
         updateIdentityPermissions(
            Identity.GROUP, group.getIdentityID(), group.getIdentityID(),
            OrganizationManager.getInstance().getCurrentOrgID(), identity.getId(), false);

      }

   }

   private void updateIdentitiesContainingRole(IdentityID oldID, IdentityID newID, String orgID,
                                               EditableAuthenticationProvider eprovider) {
      IdentityID[] users = eprovider.getUsers();
      IdentityID[] groups = eprovider.getGroups();
      IdentityID[] roles = eprovider.getRoles();

      for(IdentityID userName : users) {
         FSUser user = (FSUser) eprovider.getUser(userName);

         if(orgID.equals(user.getOrganizationID()) && user.getRoles() != null &&
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

         if(orgID.equals(group.getOrganizationID()) && group.getRoles() != null &&
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

         if(orgID.equals(role.getOrganizationID()) && role.getRoles() != null &&
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
               String identityIDLabel = userID.getOrgID() != null ?
                  provider.getOrgNameFromID(userID.getOrgID()) : null;

               roleMembers.add(
                  IdentityModel.builder()
                     .identityID(userID)
                     .type(Identity.USER)
                     .identityIDLabel(identityIDLabel)
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
      for(String orgID : provider.getOrganizationIDs()) {
         Organization org = provider.getOrganization(orgID);
         for(IdentityID uRole : org.getRoles()) {
            if(uRole.equals(roleId)) {
               roleMembers.add(
                  IdentityModel.builder()
                     .identityID(new IdentityID(provider.getOrgNameFromID(orgID), orgID))
                     .type(Identity.ORGANIZATION)
                     .build()
               );
            }
         }
      }

      return roleMembers;
   }

   public void removeStorages(String orgID) throws Exception {
      removeOldOrgTaskFormScheduleServer(orgID);
      DashboardManager.getManager().removeDashboardStorage(orgID);
      DependencyStorageService.getInstance().removeDependencyStorage(orgID);
      RecycleBin.getRecycleBin().removeStorage(orgID);
      IndexedStorage.getIndexedStorage().removeStorage(orgID);

      removeBlobStorage("__mv", orgID, MVStorage.Metadata.class);
      removeBlobStorage("__mvws", orgID, MVWorksheetStorage.Metadata.class);
      removeBlobStorage("__mvBlock", orgID, BlockFileStorage.Metadata.class);
      removeBlobStorage("__pdata", orgID, EmbeddedTableStorage.Metadata.class);
      removeBlobStorage("__library", orgID, LibManager.Metadata.class);
      removeBlobStorage("__autoSave", orgID, LibManager.Metadata.class);
   }

   public void copyStorages(Organization oOrg, Organization nOrg, boolean rename) {
      try {
         DashboardManager.getManager().copyStorageData(oOrg.getId(), nOrg.getId());
         DependencyStorageService.getInstance().copyStorageData(oOrg, nOrg);
         RecycleBin.getRecycleBin().copyStorageData(oOrg.getId(), nOrg.getId());
         updateLibraryStorage(oOrg.getId(), nOrg.getId(), true);
         IndexedStorage.getIndexedStorage().copyStorageData(oOrg, nOrg, rename);

         //FSService.copyServerNode(oOrg.getId(), nOrg.getId(), true);
         updateBlobStorageName("__mvws", oOrg.getId(), nOrg.getId(), BlockFileStorage.Metadata.class, true);
         updateBlobStorageName("__pdata", oOrg.getId(), nOrg.getId(), BlockFileStorage.Metadata.class, true);
         updateBlobStorageName("__autoSave", oOrg.getId(), nOrg.getId(), BlockFileStorage.Metadata.class, true);
         updateBlobStorageName("__mvBlock", oOrg.getId(), nOrg.getId(), BlockFileStorage.Metadata.class, true);
         MVManager.getManager().migrateStorageData(oOrg, nOrg, !rename);

         addNewOrgTaskToScheduleServer(nOrg.getOrganizationID());
      }
      catch(Exception e) {
         LOG.warn("Could not copy Storages from "+ oOrg.getId() +" to "+ nOrg.getId() +", " + e);
      }
   }

   private void addNewOrgTaskToScheduleServer(String orgId) throws RemoteException {
      Vector<ScheduleTask> scheduleTasks = new Vector<>();

      try {
         scheduleTasks = OrganizationManager.runInOrgScope(orgId,
            () -> ScheduleManager.getScheduleManager().getScheduleTasks(orgId));
      }
      catch(Exception e) {
         LOG.warn("Could not get tasks from: "+ orgId);
      }

      ScheduleServer scheduleServer = ScheduleServer.getInstance();

      if(scheduleTasks == null || scheduleServer == null) {
         return;
      }

      for(ScheduleTask scheduleTask : scheduleTasks) {
         ScheduleClient.getScheduleClient().taskAdded(scheduleTask);
      }
   }

   private void removeOldOrgTaskFormScheduleServer(String oorgId)
      throws RemoteException
   {
      ScheduleManager scheduleManager = ScheduleManager.getScheduleManager();
      Vector<ScheduleTask> scheduleTasks = scheduleManager.getScheduleTasks(oorgId);
      ScheduleServer scheduleServer = ScheduleServer.getInstance();

      if(scheduleTasks == null || scheduleServer == null) {
         return;
      }

      ScheduleClient scheduleClient = ScheduleClient.getScheduleClient();

      for(ScheduleTask scheduleTask : scheduleTasks) {
         // should not remove the global task.
         if(ScheduleManager.isInternalTask(scheduleTask.getName())) {
            continue;
         }

         scheduleClient.taskRemoved(scheduleTask.getTaskId());
      }

      scheduleClient.removeTaskCacheOfOrg(oorgId);
      scheduleManager.removeTaskCacheOfOrg(oorgId);
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

   public void removeOrgScopedDataSpaceElements(Organization oorg) {
      DataSpace dataspace = DataSpace.getDataSpace();
      String[] paths = dataspace.getOrgScopedPaths(oorg);

      for(String path : paths) {
         dataspace.delete(path,"");
      }
   }

   public void updateRepletRegistry(String oOID, String nOID) throws Exception {
      RepletRegistry oldRegistry = RepletRegistry.getRegistry(oOID);
      RepletRegistry newRegistry = RepletRegistry.getRegistry(oOID);
      String[] oldFolders = oldRegistry.getAllFolders();
      boolean removeOrg = nOID == null;

      if(!Tool.equals(oOID, nOID)) {
         for(String oldFolder : oldFolders) {
            oldRegistry.removeFolder(oldFolder, true, false, false);

            if(!removeOrg) {
               newRegistry.addFolder(oldFolder, false);
            }
         }
      }
   }

   public void copyRepletRegistry(String oOID, String nOID) {
      String tempCurrID = OrganizationManager.getInstance().getCurrentOrgID();
      OrganizationManager.getInstance().setCurrentOrgID(nOID);

      try {
         RepletRegistry oldRegistry = RepletRegistry.getRegistry(oOID);
         RepletRegistry newRegistry = RepletRegistry.getRegistry(nOID);
         String[] oldFolders = oldRegistry.getAllFolders();

         for(String oldFolder : oldFolders) {
            if(!Tool.MY_DASHBOARD.equals(oldFolder)) {
               newRegistry.addFolder(oldFolder, false);
            }
         }

         RepletRegistry.copyFolderContextMap(oOID, nOID);
         IdentityID[] orgUsers = securityEngine.getOrgUsers(oOID);

         if(orgUsers != null) {
            for(IdentityID orgUser : orgUsers) {
               IdentityID newUser = new IdentityID(orgUser.name, nOID);
               RepletRegistry.copyUser(orgUser, newUser);
            }
         }

         newRegistry.save();
      }
      catch(Exception e) {
         LOG.warn("Could not copy registry from {} to {}", oOID, nOID, e);
      }

      OrganizationManager.getInstance().setCurrentOrgID(tempCurrID);
   }

   public void copyDashboardRegistry(Organization oorg, Organization norg) {
      DashboardRegistry.copyRegistry(null, oorg, norg);

      for(IdentityID user : securityEngine.getOrgUsers(oorg.getId())) {
         DashboardRegistry.copyRegistry(user, oorg, norg);
      }
   }

   public void clearDataSourceMetadata() throws Exception {
      XRepository repository = XFactory.getRepository();
      String[] dsNames = repository.getDataSourceNames();

      if(repository instanceof XEngine) {
         for(String datasource : dsNames) {
            ((XEngine) repository).removeMetaDataFiles(datasource);
         }
      }
   }

   private <T extends Serializable> void updateBlobStorageName(String suffix, String oId, String id,
                                                               Class<T> type, boolean copy) throws Exception
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

      if(!copy) {
         oStorage.deleteBlobStorage();
      }
   }

   private void updateLibraryStorage(String oId, String id, boolean copy) throws Exception {
      try(BlobStorage<LibManager.Metadata> oStorage =
             SingletonManager.getInstance(BlobStorage.class, oId.toLowerCase() + "__library", false);
          BlobStorage<LibManager.Metadata> nStorage =
             SingletonManager.getInstance(BlobStorage.class, id.toLowerCase() + "__library", false))
      {
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

         if(!copy) {
            oStorage.deleteBlobStorage();
         }
      }
   }

   public void updateOrgProperties(String oId, String id) {
      if(Tool.equals(oId, id)) {
         return;
      }

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

      if(Tool.equals(identityID.getOrgID(), identity.getOrgID())) {
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
      ResourceAction action = ResourceAction.ADMIN;;
      EnumSet<ResourceAction> actions = EnumSet.of(ResourceAction.ADMIN);

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

   public void setIdentityPermissions(IdentityID oldID, IdentityID newID,
                                      ResourceType resourceType, Principal principal,
                                      List<IdentityModel> permittedIdentities,
                                      String newOrgId)
   {
      String currOrgId = newOrgId;

      if(newOrgId == null || newOrgId.isEmpty()) {
         currOrgId = OrganizationManager.getInstance().getCurrentOrgID();
      }

      ResourceAction action;
      String rootOrgRoleName = new IdentityID("Organization Roles", newOrgId).convertToKey();

      if(resourceType == ResourceType.SECURITY_ROLE &&
         !"Roles".equals(newID.name) && !Objects.equals(rootOrgRoleName, newID.convertToKey()))
      {
         action = ResourceAction.ASSIGN;
      }
      else {
         action = ResourceAction.ADMIN;
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
               if(idModel.identityID().orgID == null) {
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
               new IdentityID(userGrant.getName(), userGrant.getOrganizationID()).convertToKey(),
               adminAction))
            {
               userGrants.add(userGrant.getName());
            }
         }

         for(Permission.PermissionIdentity groupGrant : permission.getGroupGrants(action)) {
            if(!securityProvider.checkAnyPermission(
               principal, getResourceType(Identity.GROUP),
               new IdentityID(groupGrant.getName(), groupGrant.getOrganizationID()).convertToKey(),
               adminAction))
            {
               groupGrants.add(groupGrant.getName());
            }
         }

         for(Permission.PermissionIdentity roleGrant : permission.getRoleGrants(action)) {
            if(!securityProvider.checkAnyPermission(
               principal, getResourceType(Identity.ROLE), new IdentityID(roleGrant.getName(), roleGrant.getOrganizationID()).convertToKey(),
               adminAction))
            {
               if(roleGrant.getOrganizationID() == null) {
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
               new IdentityID(orgGrant.getName(), orgGrant.getOrganizationID()).convertToKey(),
               adminAction))
            {
               organizationGrants.add(orgGrant.getName());
            }
         }
      }

      String orgId = newOrgId == null || Tool.isEmptyString(newOrgId) ? currOrgId : newOrgId;

      if(permission == null) {
         permission = new Permission();
      }

      permission.setUserGrantsForOrg(action, userGrants, orgId);
      permission.setGroupGrantsForOrg(action, groupGrants, orgId);
      permission.setRoleGrantsForOrg(action, roleGrants, orgId);
      permission.setRoleGrantsForOrg(action, globalRoleGrants, null);
      permission.setOrganizationGrantsForOrg(action, organizationGrants, orgId);

      if(permittedIdentities != null && !permittedIdentities.isEmpty()) {
         permission.updateGrantAllByOrg(orgId, true);
      }

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
         String[] porgs = provider.getOrganizationIDs();
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
            setUserInfo((FSUser) identity, (EditUserPaneModel) model, eprovider, groupV);
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

         Cluster.getInstance().sendMessage(new IdentityChangedMessage(type, identity.getIdentityID()));
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

         if(!SUtil.isMultiTenant()) {
            LicenseManager.getInstance().userChanged();
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
      actionRecord.setObjectName(model.oldName());
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
            identityIds = Arrays.stream(authenticationProvider.getOrganizationNames())
               .map(o -> new IdentityID(o, oldID.orgID)).toArray(IdentityID[]::new);
            break;
         case Identity.USER:
            identityIds = authenticationProvider.getUsers();
            break;
         case Identity.ROLE:
            identityIds = authenticationProvider.getRoles();
            break;
         }

         if(Tool.contains(identityIds, new IdentityID(name, oldID.orgID))) {
            final String err = Catalog.getCatalog().getString("common.duplicateName");
            throw new Exception(err);
         }

         actionRecord.setActionError("new name:" + name);
         return SUtil.getIdentityInfoRecord(new IdentityID(model.oldName(), oldID.orgID),
                                            type, IdentityInfoRecord.ACTION_TYPE_RENAME,
                                            "Rename " + oldID.name + " to " + name, state);
      }
      else {
         return SUtil.getIdentityInfoRecord(oldID, type, IdentityInfoRecord.ACTION_TYPE_MODIFY,
                                            null, state);
      }
   }

   private void setUserInfo(FSUser ouser, EditUserPaneModel model,
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
      user.setAlias(!Tool.isEmptyString(model.alias()) ? model.alias() : null);
      user.setActive(model.status());
      user.setOrganization(model.organization());
      user.setGoogleSSOId(ouser.getGoogleSSOId());
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

      IdentityID oIdentity = IdentityID.getIdentityIDFromKey(model.oldIdentityKey());
      IdentityID[] groups = new IdentityID[groupV.size()];
      groupV.toArray(groups);
      user.setGroups(Arrays.stream(groups).map(id -> id.name).toArray(String[]::new));
      User oldUser = eprovider.getUser(oIdentity);

      if(oldUser == null || Tool.isEmptyString(oldUser.getGoogleSSOId())) {
         if(model.password() != null) {
            HashedPassword npw = Tool.hash(model.password(), "bcrypt");

            if(oldUser != null && npw != null) {
               if(!npw.getHash().equals(oldUser.getPassword())) {
                  user.setPassword(npw.getHash());
                  user.setPasswordAlgorithm(npw.getAlgorithm());
                  user.setPasswordSalt(null);
                  user.setAppendPasswordSalt(false);
                  logoutSession(oIdentity);
               }
            }
         }
         else {
            user.setPassword(oldUser.getPassword());
            user.setPasswordAlgorithm(oldUser.getPasswordAlgorithm());
            user.setPasswordSalt(oldUser.getPasswordSalt());
            user.setAppendPasswordSalt(oldUser.isAppendPasswordSalt());
         }
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
      final IdentityID oID = new IdentityID(oldGroup.getName(), oldGroup.getOrganizationID());
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

         if(pgroup == null || !Tool.equals(pgroup.getOrganizationID(), group.getOrganizationID())) {
            continue;
         }

         if(oID.name.equals(pgroup.getName()) && Tool.equals(oID.orgID, pgroup.getOrganizationID()))
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

         if(puser == null || !Tool.equals(puser.getOrganizationID(), group.getOrganizationID())) {
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

         if(pgroup == null || !OrganizationManager.getInstance().getCurrentOrgID().equals(pgroup.getOrganizationID())) {
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
         if((role.getOrganizationID() != null && !Tool.equals(puserName.orgID, role.getOrganizationID())))
         {
            continue;
         }


         FSUser puser = (FSUser) eprovider.getUser(puserName);

         if(puser == null || (model.organization() != null && !OrganizationManager.getInstance().getCurrentOrgID().equals(puser.getOrganizationID()))) {
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

      for(String porgId : porgs) {
         FSOrganization porg = (FSOrganization) eprovider.getOrganization(porgId);

         if(porg == null) {
            continue;
         }

         if(orgV.contains(porgId)) {
            eprovider.setOrganization(porgId, porg);
         }
      }

      if(!newOrgID.equals(oldOrgID) && principal instanceof XPrincipal) {
         ((XPrincipal) principal).updateRoles(eprovider);
      }
   }

   private void setOrganizationInfo(FSOrganization oldOrg, EditOrganizationPaneModel model,
                                    EditableAuthenticationProvider eprovider, Principal principal)
      throws Exception
   {
      final String name = model.name();
      final String id = model.id();
      FSOrganization newOrg = new FSOrganization(id);
      newOrg.setName(name);
      List<IdentityModel> members = model.members();

      if(oldOrg != null && !Tool.equals(oldOrg.getName(), newOrg.getName()) &&
         Tool.equals(oldOrg.getId(), newOrg.getId()))
      {
         Organization org = eprovider.getOrganization(id);
         org.setName(newOrg.getName());
         eprovider.setOrganization(id, org);

         return;
      }

      List<String> memberNames = members.stream()
         .map(IdentityModel::identityID)
         .map(i -> i.name)
         .collect(Collectors.toList());
      String[] oldMembers = oldOrg.getMembers();

      if(oldMembers != null) {
         Arrays.stream(oldMembers)
            .filter(m -> securityProvider.getUser(new IdentityID(m, oldOrg.getId())) != null)
            .filter(m -> !securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                                           m, ResourceAction.ADMIN))
            .forEach(m -> memberNames.add(m));
      }

      for(IdentityModel member : model.members()) {
         if(member.type() == Identity.ROLE) {
            Role role = eprovider.getRole(member.identityID());

            //global roles cannot be assigned as members of an organization
            if(role != null && role.getOrganizationID() == null) {
               throw new MessageException(Catalog.getCatalog().getString("em.security.GlobalRoleMemberError"));
            }
         }
      }

      newOrg.setMembers(memberNames.toArray(new String[0]));
      newOrg.setActive(model.status());

      String oldID = eprovider.getOrgIdFromName(model.oldName());
      Organization fromOrg = eprovider.getOrganization(oldID);
      String fromOrgID = fromOrg != null ? fromOrg.getId() : null;

      if(model.oldName() == null ||
            !Tool.equals(oldOrg.getMembers(), memberNames) ||
            !Tool.equals(fromOrgID, model.id()))
      {
         updateOrganizationMembers(newOrg, members, oldID, eprovider);
      }

      if(fromOrg != null && !Tool.equals(fromOrg, newOrg)) {
         DashboardRegistry.migrateRegistry(null, fromOrg, newOrg);
         RepletRegistry.getRegistry(fromOrgID).shutdown();
         updateOrgScopedDataSpace(fromOrg, newOrg);
      }

      Properties localeProperties = SUtil.loadLocaleProperties();
      String localeString = null;
      Set<String> localeKeys = (Set) localeProperties.keySet();

      for(String key : localeKeys) {
         if(localeProperties.getProperty(key).equals(model.locale())) {
            localeString = key;
         }
      }

      if(fromOrg != null && Tool.equals(fromOrg.getId(), newOrg.getId()) &&
         fromOrg instanceof FSOrganization)
      {
         ((FSOrganization) fromOrg).setLocale(localeString);
         ((FSOrganization) fromOrg).setTheme(model.theme());
         eprovider.setOrganization(fromOrgID, fromOrg);

         return;
      }

      newOrg.setLocale(localeString);
      newOrg.setTheme(model.theme());
      syncIdentity(eprovider, newOrg, new IdentityID(model.oldName(), eprovider.getOrgIdFromName(model.oldName())));
   }

   private void updateOrgScopedDataSpace(Organization oorg, Organization norg) {
      DataSpace dataspace = DataSpace.getDataSpace();
      String[] paths = dataspace.getOrgScopedPaths(oorg);

      for(String path : paths) {
         if(!dataspace.exists(null, path)) {
            continue;
         }

         String toPath;

         toPath = path.replace(oorg.getId(), norg.getId());

         if(Tool.equals(toPath, path)) {
            continue;
         }

         dataspace.rename(path, toPath);
      }
   }

   private void addOrganizationMember(String orgID, String memberName, EditableAuthenticationProvider provider) {
      Organization org = provider.getOrganization(orgID);
      List<String> members = org.getMembers() != null ? new ArrayList<>(Arrays.asList(org.getMembers())) : new ArrayList<>();
      members.add(memberName);
      provider.setOrganization(orgID, org);
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
            final Group childGroup = provider.getGroup(new IdentityID(pgroupName, group.getOrganizationID()));
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

   public void clearRootPermittedIdentities(String orgID, Principal principal) {
      AuthorizationProvider authzProvider = securityProvider.getAuthorizationProvider();
      //Users
      String rootUserName = "Users";
      IdentityID fromRootUserID = new IdentityID(rootUserName, orgID);
      authzProvider.removePermission(ResourceType.SECURITY_USER, fromRootUserID);

      //Groups
      String rootGroupName = "Groups";
      IdentityID fromRootGroupID = new IdentityID(rootGroupName, orgID);
      authzProvider.removePermission(ResourceType.SECURITY_GROUP, fromRootGroupID);

      //Roles
      String rootRoleName = "Roles";
      IdentityID fromRootID = new IdentityID(rootRoleName, orgID);
      authzProvider.removePermission(ResourceType.SECURITY_ROLE, fromRootID);

      String rootOrgRoleName = "Organization Roles";
      IdentityID fromRootOrgRoleID = new IdentityID(rootOrgRoleName, orgID);
      authzProvider.removePermission(ResourceType.SECURITY_ROLE, fromRootOrgRoleID);

      //Organization
      IdentityID oldOrgID = new IdentityID(securityProvider.getOrgNameFromID(orgID), orgID);
      authzProvider.removePermission(ResourceType.SECURITY_ORGANIZATION, oldOrgID);
   }

   public void updateIdentityPermissions(int type, IdentityID oldName, IdentityID newName, String oldOrgId, String newOrgId, boolean doReplace) {
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      Organization oldOrganization = oldOrgId == null || oldOrgId.isEmpty() ?
         null : provider.getOrganization(oldOrgId);

      for(Tuple4<ResourceType, String, String, Permission> permissionSet : provider.getPermissions()) {
         String resourceOrgID = permissionSet.getSecond();
         String path = permissionSet.getThird();
         Permission permission = permissionSet.getForth();

         if(permission == null) {
            continue;
         }

         if(resourceOrgID != null && !Tool.equals(resourceOrgID, oldOrgId) ||
            newName == null && path.contains(IdentityID.KEY_DELIMITER) && IdentityID.getIdentityIDFromKey(path).orgID != null &&
               !Tool.equals(IdentityID.getIdentityIDFromKey(path).orgID,oldName.orgID) &&
               !Tool.equals(IdentityID.getIdentityIDFromKey(path).orgID,oldOrgId))
         {
            //skip permissions not in this organization
            continue;
         }

         if(containsOrgID(path, oldName.getOrgID()) && newName == null) {
            provider.removePermission(permissionSet.getFirst(), path, resourceOrgID);
         }

         for(ResourceAction action : ResourceAction.values()) {
            if(permission != null) {
               if(type == Identity.USER) {
                  for(IdentityID granteeName : permission.getOrgScopedUserGrants(action, oldOrganization).toArray(new IdentityID[0])) {
                     if(oldName != null && oldName.name.equals(granteeName.name)) {
                        //rename old grantee to new name
                        updateIdentityPermission(type, newName, oldName, oldOrganization, newOrgId, permission, action, permissionSet.getFirst(), path, doReplace);
                     }
                  }
               }
               else if(type == Identity.GROUP) {
                  for(IdentityID granteeName : permission.getOrgScopedGroupGrants(action, oldOrganization).toArray(new IdentityID[0])) {
                     if(oldName != null && oldName.name.equals(granteeName.name)) {
                        //rename old grantee to new name
                        updateIdentityPermission(type, newName, oldName, oldOrganization, newOrgId, permission, action, permissionSet.getFirst(), path, doReplace);
                     }
                  }
               }
               else if(type == Identity.ROLE) {
                  for(IdentityID granteeName : permission.getOrgScopedRoleGrants(action, oldOrganization).toArray(new IdentityID[0])) {
                     if(oldName != null && oldName.name.equals(granteeName.name)) {
                        //rename old grantee to new name
                        updateIdentityPermission(type, newName, oldName, oldOrganization, newOrgId, permission, action, permissionSet.getFirst(), path, doReplace);
                     }
                  }
               }
               else if(type == Identity.ORGANIZATION) {
                  updateOrgIdentitiesPermission(newName, oldName, oldOrganization, newOrgId, permission, action, doReplace);
               }
            }
         }

         String oldOrgName;
         String newOrgName;

         if(type == Identity.ORGANIZATION &&
            permissionSet.getFirst() == ResourceType.SECURITY_ORGANIZATION &&
            newName != null && oldName != null)
         {
            oldOrgName = oldName.getName();
            newOrgName = newName.getName();
         }
         else {
            oldOrgName = oldName != null && oldName.getOrgID() != null ? oldName.getName() : null;
            newOrgName = newName != null && newName.getName() != null ? newName.getName() : null;
         }

         updatePermission(provider, permissionSet, oldOrgName, newOrgName, oldOrgId, newOrgId, doReplace, type);
      }
   }

   private void updatePermission(SecurityProvider provider,
                                 Tuple4<ResourceType, String, String, Permission> permissionSet,
                                 String oIdentityName, String nIdentityName, String oorgId,
                                 String norgId, boolean doReplace, int changeIdentityType)
   {
      ResourceType type = permissionSet.getFirst();
      String resourceOrgID = permissionSet.getSecond();
      String path = permissionSet.getThird();
      Permission permission = permissionSet.getForth();
      updateOrgEditedGrantAll(permission, oorgId, norgId);
      String newPath = getNewPermissionPath(permissionSet.getFirst(), path, oorgId, norgId,
         oIdentityName, nIdentityName, changeIdentityType);

      if(newPath != null) {
         provider.setPermission(type, newPath, permission, norgId);
      }
      else {
         provider.setPermission(type, path, permission, norgId);
      }

      if(doReplace && (!Tool.equals(oorgId, norgId) || !Tool.equals(newPath, path))) {
         provider.removePermission(type, path, resourceOrgID);
      }
   }

   private boolean containsOrgID(String path, String oorgID) {
      if(path != null && path.contains(IdentityID.KEY_DELIMITER)) {
         IdentityID identityID = IdentityID.getIdentityIDFromKey(path);

         return Tool.equals(oorgID, identityID.getOrgID());
      }

      return false;
   }

   private String getNewPermissionPath(ResourceType type, String path,
                                       String oorgID, String norgID, String oIdentityName,
                                       String nIdentityName, int changeIdentityType)
   {
      if(type == ResourceType.SECURITY_ORGANIZATION &&
         Tool.equals(path, new IdentityID(oIdentityName, oorgID).convertToKey()))
      {
         return new IdentityID(nIdentityName, norgID).convertToKey();
      }

      if(type == ResourceType.SCHEDULE_TASK) {
         if(changeIdentityType == Identity.USER) {
            IdentityID oldIdentityID = new IdentityID(oIdentityName, oorgID);
            IdentityID newIdentityID = new IdentityID(nIdentityName, norgID);
            ScheduleTaskMetaData taskMetaData = ScheduleManager.getTaskMetaData(path);

            if(Tool.equals(taskMetaData.getTaskOwnerId(), oldIdentityID.convertToKey())) {
               taskMetaData.setTaskOwnerId(newIdentityID.convertToKey());

               return taskMetaData.getTaskId();
            }
         }
         else if(changeIdentityType == Identity.ORGANIZATION) {
            ScheduleTaskMetaData taskMetaData = ScheduleManager.getTaskMetaData(path);
            IdentityID ownerIdentityID = IdentityID.getIdentityIDFromKey(taskMetaData.getTaskOwnerId());

            if(ownerIdentityID != null && Tool.equals(ownerIdentityID.getOrgID(), oorgID)) {
               ownerIdentityID = new IdentityID(ownerIdentityID.getName(), norgID);
               taskMetaData.setTaskOwnerId(ownerIdentityID.convertToKey());

               return taskMetaData.getTaskId();
            }
         }
      }

      String newPath = path;

      if(containsOrgID(path, oorgID) && !Tool.equals(oorgID, norgID)) {
         IdentityID identityID = IdentityID.getIdentityIDFromKey(path);
         identityID.setOrgID(norgID);
         newPath = identityID.convertToKey();
      }

      return newPath;
   }

   private void updateOrgIdentitiesPermission(IdentityID newName, IdentityID oldName,
                                              Organization oldOrg, String newOrgId, Permission permission,
                                              ResourceAction action, boolean doReplace)
   {
      Set<String> userGrants = new HashSet<>();
      Set<String> groupGrants = new HashSet<>();
      Set<String> roleGrants = new HashSet<>();
      Set<String> organizationGrants = new HashSet<>();

      if(permission != null) {
         if(newName != null && !newName.name.isEmpty()) {
            permission.getOrgScopedUserGrants(action, oldOrg).stream()
               .map(id -> id.name)
               .forEach(userGrants::add);
            permission.getOrgScopedGroupGrants(action, oldOrg).stream()
               .map(id -> id.name)
               .forEach(groupGrants::add);
            permission.getOrgScopedRoleGrants(action, oldOrg).stream()
               .map(id -> id.name)
               .forEach(roleGrants::add);
            permission.getOrgScopedOrganizationGrants(action, oldOrg).stream()
               .map(id -> newName.getName())
               .forEach(organizationGrants::add);
         }
      }
      else {
         permission = new Permission();
      }

      if(newName != null && !newName.name.isEmpty() && !organizationGrants.contains(newName.name)) {
         organizationGrants = organizationGrants.stream()
            .map(org -> {
               if(Tool.equals(org, oldName.getName())) {
                  return newName.getName();
               }

               return org;
            })
            .collect(Collectors.toSet());
      }

      if(oldOrg != null) {
         String oldOrgId = doReplace ? oldOrg.getOrganizationID() : null;

         if(!userGrants.isEmpty()) {
            permission.setUserGrantsForOrg(action, userGrants, oldOrgId, newOrgId);
         }

         if(!groupGrants.isEmpty()) {
            permission.setGroupGrantsForOrg(action, groupGrants, oldOrgId, newOrgId);
         }

         if(!roleGrants.isEmpty()) {
            permission.setRoleGrantsForOrg(action, roleGrants, oldOrgId, newOrgId);
         }

         if(!organizationGrants.isEmpty()) {
            permission.setOrganizationGrantsForOrg(action, organizationGrants, oldOrgId, newOrgId);
         }

         if(permission.isOrgInPerm(action, newOrgId)) {
            permission.updateGrantAllByOrg(newOrgId, true);

            if(doReplace && !Tool.equals(oldOrgId, newOrgId)) {
               permission.removeGrantAllByOrg(oldOrgId);
            }
         }
      }
   }

   private void updateIdentityPermission(int type, IdentityID newIdentityID, IdentityID oldIdentityID, Organization oldOrg, String newOrgId, Permission permission,
                                         ResourceAction action, ResourceType rType, String rpath, boolean doReplace) {
      Set<String> identityGrants = new HashSet<>();

      if(permission != null) {
         if(doReplace || newIdentityID == null) {
            Set<IdentityID> orgScopedGrants = null;

            switch(type) {
            case Identity.USER:
               orgScopedGrants = permission.getOrgScopedUserGrants(action, oldOrg);
               permission.setUserGrantsForOrg(action, new HashSet<>(), oldOrg.getId());
               break;
            case Identity.GROUP:
               orgScopedGrants = permission.getOrgScopedGroupGrants(action, oldOrg);
               permission.setGroupGrantsForOrg(action, new HashSet<>(), oldOrg.getId());
               break;
            case Identity.ROLE:
               orgScopedGrants = permission.getOrgScopedRoleGrants(action, oldOrg);
               permission.setRoleGrantsForOrg(action, new HashSet<>(), oldOrg.getId());
               break;
            case Identity.ORGANIZATION:
               orgScopedGrants = permission.getOrgScopedOrganizationGrants(action, oldOrg);
               permission.setOrganizationGrantsForOrg(action, new HashSet<>(), oldOrg.getId());
               break;
            }

            if(orgScopedGrants != null) {
               orgScopedGrants.stream()
                  .map(id -> id.name)
                  .filter(u -> !u.equals(oldIdentityID.name))
                  .forEach(identityGrants::add);
            }
         }
      }
      else {
         permission = new Permission();
      }

      if(newIdentityID != null && !newIdentityID.name.isEmpty()) {
         identityGrants.add(newIdentityID.name);
      }

      switch(type) {
      case Identity.USER:
         permission.setUserGrantsForOrg(action, identityGrants, newOrgId);
         break;
      case Identity.GROUP:
         permission.setGroupGrantsForOrg(action, identityGrants, newOrgId);
         break;
      case Identity.ROLE:
         permission.setRoleGrantsForOrg(action, identityGrants, newOrgId);
         break;
      case Identity.ORGANIZATION:
         permission.setOrganizationGrantsForOrg(action, identityGrants, newOrgId);
         break;
      }

      if(permission.isOrgInPerm(action, newOrgId)) {
         permission.updateGrantAllByOrg(newOrgId, true);
      }
   }


   public void addCopiedIdentityPermission(IdentityID fromIdentity, IdentityID newIdentity,
                                           String newOrgId, int type, boolean replace) {
      //delete organization name inside of permissions
      if(type == Identity.ORGANIZATION) {
         String oid = fromIdentity.orgID;
         String id = newIdentity.orgID;
         updateIdentityPermissions(type, fromIdentity, newIdentity, oid , id, replace);
      }
      else {
         String orgId = fromIdentity.getOrgID();
         SecurityProvider sProvider = SecurityEngine.getSecurity().getSecurityProvider();

         updateIdentityPermissions(type, fromIdentity, newIdentity, orgId, newOrgId, replace);
      }
   }

   public void updateAutoSaveFiles(Organization oorg, Organization norg, Principal principal) {
      AutoSaveUtils.migrateAutoSaveFiles(oorg, norg, principal);
   }

   public void updateTaskSaveFiles(Organization oorganization, Organization norganization) {
      String oorg = oorganization.getId();
      String norg = norganization.getId();

      if(Tool.equals(oorg, norg)) {
         return;
      }

      try {
         ExternalStorageService.getInstance().renameFolder(oorg, norg);
      }
      catch(Exception e) {
         LOG.warn("Failed to rename folder for organization", oorg, e);
      }
   }

   private void updateUserAutoSaveFiles(IdentityID oID, IdentityID nID) {
      if(oID.equals(nID)) {
         return;
      }

      Principal principal = ThreadContext.getContextPrincipal();
      List<String> list = AutoSaveUtils.getAutoSavedFiles(principal, true);

      if(list.isEmpty()) {
         return;
      }

      for(String file : list) {
         String asset = AutoSaveUtils.getName(file);
         String[] attrs = Tool.split(asset, '^');

         if(attrs.length > 3) {
            String fileUser = attrs[2];
            fileUser = "anonymous".equals(fileUser) ? "_NULL_" : fileUser;

            if(fileUser == null || Tool.equals(fileUser, "_NULL_")) {
               continue;
            }

            IdentityID fileUserID = IdentityID.getIdentityIDFromKey(fileUser);

            if(fileUserID.equals(oID)) {
               attrs[2] = nID.convertToKey();
               String newFilePath = AutoSaveUtils.RECYCLE_PREFIX + String.join("^", attrs);
               AutoSaveUtils.renameAutoSaveFile(file, newFilePath, principal);
            }
         }
      }
   }

   public String getOrganizationDetailString(String orgKey, Principal principal) {
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      IdentityID orgIdentityID = IdentityID.getIdentityIDFromKey(orgKey);

      String dataBaseListString = getOrganizationDatabaseListString(orgIdentityID, principal);

      int orgUsers = (int) Arrays.stream(provider.getUsers())
         .filter(user -> user.orgID.equals(orgIdentityID.orgID))
         .count();

      return
         Catalog.getCatalog().getString("em.security.orgDetailString",orgIdentityID.name,dataBaseListString, orgUsers);
   }

   private String getOrganizationDatabaseListString(IdentityID orgIdentityID, Principal principal) {
      final DataSourceRegistry registry = DataSourceRegistry.getRegistry();

      List<String> dataSourceNames = new ArrayList<>(registry.getSubfolderNames(null, false, orgIdentityID.orgID));
      dataSourceNames.addAll(new ArrayList<>(registry.getSubDataSourceNames(null, false, orgIdentityID.orgID)));
      Collections.sort(dataSourceNames);

      StringBuilder datasourceListString = new StringBuilder("{ ");

      for(int i=0; i< dataSourceNames.size();i++) {
         if(i != dataSourceNames.size() - 1) {
            datasourceListString.append(dataSourceNames.get(i)).append(", ");

            if(i>4 && i%5 == 0) {
               //line break every 5 datasources
               datasourceListString.append("\n");
            }
         }
         else {
           datasourceListString.append(dataSourceNames.get(i));
         }
      }
      datasourceListString.append(" }\n");

      return datasourceListString.toString();
   }

   private final SecurityEngine securityEngine;
   private final SecurityProvider securityProvider;
   private final IdentityThemeService themeService;
   private final Logger LOG = LoggerFactory.getLogger(IdentityService.class);
   private final AuthenticationService authenticationService;
}