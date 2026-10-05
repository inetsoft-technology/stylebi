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
import inetsoft.report.LibManagerProvider;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.*;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.portal.*;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.sree.web.SessionLicenseManager;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.storage.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XEngine;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import inetsoft.util.audit.*;
import inetsoft.util.css.CSSDictionary;
import inetsoft.util.log.LogManager;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.session.IgniteSessionRepository;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.security.user.*;
import org.apache.commons.io.IOUtils;
import org.passay.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class IdentityService {
   @Autowired
   public IdentityService(SecurityEngine securityEngine,
                          SecurityProvider securityProvider,
                          IdentityThemeService themeService,
                          AuthenticationService authenticationService,
                          BlobStorageManager blobStorageManager,
                          FavoritesService favoritesService,
                          Cluster cluster,
                          MVManager mvManager,
                          DataCycleManager dataCycleManager,
                          DataSourceRegistry dataSourceRegistry,
                          LogManager logManager,
                          LicenseManager licenseManager,
                          ScheduleManager scheduleManager,
                          IndexedStorage indexedStorage,
                          Optional<ScheduleServer> scheduleServer,
                          ScheduleClient scheduleClient, CustomThemesManager customThemesManager,
                          SessionLicenseServiceProvider sessionLicenseServiceProvider,
                          DashboardRegistryManager dashboardRegistryManager,
                          LibManagerProvider libManagerProvider,
                          DashboardManager dashboardManager,
                          PortalThemesManager portalThemesManager,
                          RecycleBin recycleBin,
                          DataSpace dataSpace,
                          DependencyStorageService dependencyStorageService,
                          ExternalStorageService externalStorageService,
                          XRepository xRepository,
                          RepletRegistryManager repletRegistryManager,
                          Optional<IgniteSessionRepository> sessionRepository)
   {
      this.securityEngine = securityEngine;
      this.securityProvider = securityProvider;
      this.themeService = themeService;
      this.authenticationService = authenticationService;
      this.blobStorageManager = blobStorageManager;
      this.favoritesService = favoritesService;
      this.cluster = cluster;
      this.mvManager = mvManager;
      this.dataCycleManager = dataCycleManager;
      this.dataSourceRegistry = dataSourceRegistry;
      this.logManager = logManager;
      this.licenseManager = licenseManager;
      this.scheduleManager = scheduleManager;
      this.indexedStorage = indexedStorage;
      this.scheduleServer = scheduleServer.orElse(null);
      this.scheduleClient = scheduleClient;
      this.customThemesManager = customThemesManager;
      this.sessionLicenseServiceProvider = sessionLicenseServiceProvider;
      this.dashboardRegistryManager = dashboardRegistryManager;
      this.libManagerProvider = libManagerProvider;
      this.dashboardManager = dashboardManager;
      this.portalThemesManager = portalThemesManager;
      this.recycleBin = recycleBin;
      this.dataSpace = dataSpace;
      this.dependencyStorageService = dependencyStorageService;
      this.externalStorageService = externalStorageService;
      this.xRepository = xRepository;
      this.repletRegistryManager = repletRegistryManager;
      this.sessionRepository = sessionRepository.orElse(null);
   }

   private AuthenticationProvider getProvider(String providerName) {
      AuthenticationProvider authc = securityProvider.getAuthenticationProvider();

      if(!(authc instanceof AuthenticationChain)) {
         return null;
      }

      AuthenticationChain authcChain = (AuthenticationChain) authc;
      return authcChain.getProviders().stream()
         .filter((p) -> Catalog.getCatalog().getString(p.getProviderName()).equals(providerName) || p.getProviderName().equals(providerName))
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

            if(isIdentityDeleteDenied(principal, resourceType, identityId, type)) {
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

               if(isSelfDelete(principal, identityId, type, provider)) {
                  warnings.add(catalog.getString("em.security.delself"));
                  continue;
               }

               if(type == Identity.USER) {
                  logoutSession(identityId);
               }

               cluster.sendMessage(new IdentityChangedMessage(type, null, identityId));

               syncIdentity(provider, identityId != null ? new DefaultIdentity(identityId, type) :
                  new DefaultIdentity(), null);

               // only mark as deleted after syncIdentity succeeds, so favorites
               // aren't stripped for users whose deletion failed
               if(type == Identity.USER) {
                  deleteUserIDs.add(identityId);
               }
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

         // sweep shared-asset favorites once for all deleted users (one scan per org)
         removeUserFavorites(deleteUserIDs);

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

   /**
    * Compute, without deleting anything, which scheduled tasks would be affected if the given
    * identities were deleted: tasks owned by a user (deleted) and tasks where a user/group is
    * the "execute as" (reset to the task owner). Only user/group deletions affect tasks.
    */
   public DeleteIdentitiesTaskImpactResponse getDeleteTaskImpacts(IdentityModel[] models,
                                                                  String providerName,
                                                                  Principal principal)
   {
      // track (org, task name) so distinct tasks that share a name across orgs aren't merged
      Set<TaskRef> ownedTasks = new LinkedHashSet<>();
      Set<TaskRef> executeAsTasks = new LinkedHashSet<>();
      Set<TaskRef> refusedTasks = new LinkedHashSet<>();
      AuthenticationProvider authcProvider = this.getProvider(providerName);

      if(authcProvider instanceof EditableAuthenticationProvider provider) {
         for(IdentityModel model : models) {
            int type = model.type();

            if(type != Identity.USER && type != Identity.GROUP) {
               continue;
            }

            ResourceType resourceType = type == Identity.GROUP ?
               ResourceType.SECURITY_GROUP : ResourceType.SECURITY_USER;

            // gate by the same admin permission the delete enforces, so an admin can't enumerate
            // another org's task names by posting identities they aren't allowed to administer
            try {
               if(!securityEngine.checkPermission(principal, resourceType,
                  model.identityID().convertToKey(), ResourceAction.ADMIN))
               {
                  continue;
               }
            }
            catch(Exception ignore) {
               continue;
            }

            Identity identity = new DefaultIdentity(model.identityID(), type);
            String targetOrg = model.identityID() == null ? null : model.identityID().orgID;

            try {
               // resolve the impact in the target identity's org so a site/host admin deleting a
               // user in another org (e.g. the self org) still sees that org's affected tasks
               ScheduleManager.IdentityTaskImpact impact = targetOrg == null
                  ? scheduleManager.getIdentityRemovalImpact(identity, provider)
                  : OrganizationManager.runInOrgScope(
                     targetOrg, () -> scheduleManager.getIdentityRemovalImpact(identity, provider));
               impact.ownedTasks().forEach(name -> ownedTasks.add(new TaskRef(targetOrg, name)));
               impact.executeAsTasks().forEach(name -> executeAsTasks.add(new TaskRef(targetOrg, name)));
               impact.refusedTasks().forEach(name -> refusedTasks.add(new TaskRef(targetOrg, name)));
            }
            catch(Exception ex) {
               LOG.warn("Failed to compute schedule task impact for {}", model.identityID(), ex);
            }
         }
      }

      // a task whose owner is being deleted is removed entirely, so don't also report it as a reset
      executeAsTasks.removeAll(ownedTasks);
      refusedTasks.removeAll(ownedTasks);

      return DeleteIdentitiesTaskImpactResponse.builder()
         .ownedTasks(toDisplayNames(ownedTasks))
         .executeAsTasks(toDisplayNames(executeAsTasks))
         .refusedTasks(toDisplayNames(refusedTasks))
         .build();
   }

   /**
    * Render task refs for display, qualifying a name with its org only when the same name
    * appears under more than one org so cross-org duplicates stay distinct.
    */
   private static List<String> toDisplayNames(Set<TaskRef> refs) {
      Map<String, Set<String>> orgsByName = new LinkedHashMap<>();

      for(TaskRef ref : refs) {
         orgsByName.computeIfAbsent(ref.name(), k -> new LinkedHashSet<>()).add(ref.org());
      }

      List<String> names = new ArrayList<>();

      for(TaskRef ref : refs) {
         if(ref.org() != null && orgsByName.get(ref.name()).size() > 1) {
            names.add(ref.name() + " (" + ref.org() + ")");
         }
         else {
            names.add(ref.name());
         }
      }

      return names;
   }

   private record TaskRef(String org, String name) {
   }

   private void logoutSession(IdentityID user) {
      SessionLicenseManager sessionLicenseManager =
         sessionLicenseServiceProvider.getSessionLicenseManager();

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
                                       IdentityID oldParentID, List<IdentityID> childrenIDs,
                                       Principal principal)
   {
      List<IdentityID> list = new ArrayList<>();
      Collections.addAll(list, parentIDs);

      boolean isParent = list.contains(parentID);
      boolean isChild = childrenIDs.contains(childId);
      String currentOrgID = OrganizationManager.getInstance().getCurrentOrgID(principal);

      if(parentID.getOrgID() == null && !Tool.equals(currentOrgID, childId.getOrgID())) {
         // a site administrator is shown every organization's members of a global role, so the
         // submitted members are authoritative; anyone else only carries a rename over
         if(!isCrossOrgEditAllowed(principal)) {
            isChild = list.contains(oldParentID);
         }
      }

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
    * A global role can be held by the users, groups and roles of every organization, which the
    * provider only cleans up in the removed role's own organization. This is done here rather than
    * in the provider's removeRole, which also runs on a rename and would drop the members that a
    * rename carries over. Only the editable FS* identities are updated.
    */
   private void removeGlobalRoleFromMembers(EditableAuthenticationProvider eprovider,
                                            IdentityID roleId, List<Identity> stripped)
   {
      for(IdentityID userId : eprovider.getUsers()) {
         User user = eprovider.getUser(userId);

         if(user instanceof FSUser fsUser && Arrays.asList(user.getRoles()).contains(roleId)) {
            fsUser.setRoles(Tool.remove(user.getRoles(), roleId));
            eprovider.setUser(userId, user);
            stripped.add(new DefaultIdentity(userId, Identity.USER));
         }
      }

      for(IdentityID groupId : eprovider.getGroups()) {
         Group group = eprovider.getGroup(groupId);

         if(group instanceof FSGroup fsGroup && Arrays.asList(group.getRoles()).contains(roleId)) {
            fsGroup.setRoles(Tool.remove(group.getRoles(), roleId));
            eprovider.setGroup(groupId, group);
            stripped.add(new DefaultIdentity(groupId, Identity.GROUP));
         }
      }

      for(IdentityID otherId : eprovider.getRoles()) {
         Role role = eprovider.getRole(otherId);

         if(role instanceof FSRole fsRole && !otherId.equals(roleId) &&
            Arrays.asList(role.getRoles()).contains(roleId))
         {
            fsRole.setRoles(Tool.remove(role.getRoles(), roleId));
            eprovider.setRole(otherId, role);
            stripped.add(new DefaultIdentity(otherId, Identity.ROLE));
         }
      }
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

      // The identity lookup can transiently return null (e.g. cache/storage
      // staleness or a concurrent modification while an identity is being
      // deleted). Guard against it so IdentityInfo isn't constructed from a
      // null identity, which would throw an NPE that gets logged as a
      // misleading "Failed to create info object for identity: null" error.
      if(identity == null) {
         LOG.debug("Identity not found, returning empty info: {} (type={})", identityId, type);
         return new IdentityInfo();
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
      DashboardManager dmanager = dashboardManager;
      ScheduleManager smanager = scheduleManager;

      Identity nid = new DefaultIdentity(identityId, type);
      Identity oid = oID == null ? null : new DefaultIdentity(oID, type);

      if(oID == null) {
         // a deleted user, group or role is cleaned up in removeIdentity(), after it has been
         // removed from the provider, so a failed removal keeps its tasks, dashboards and
         // memberships
         if(type == Identity.ORGANIZATION) {
            dmanager.setDashboards(nid, null);
            smanager.identityRemoved(identity, eprovider);
         }
      }
      else {
         if((type == Identity.USER || type == Identity.GROUP) && !identityId.equals(oID)) {
            smanager.identityRenamed(oID, identity);
            dmanager.setDashboards(nid, dmanager.getDashboards(oid));
            dmanager.setDashboards(oid, null);
            dmanager.removeDashboards(oid);
            dashboardRegistryManager.clear(oID);
         }
      }

      AuthorizationChain authoc = (AuthorizationChain) securityProvider.getAuthorizationProvider();

      if(identity.getType() == Identity.USER) {
         //AssetRepository rep = AssetUtil.getAssetRepository(false);
         if(oID == null) {
            // read before the user is removed. A user stored without an organization belongs to
            // the default organization, so its organization is never null, which would match
            // every organization's themes
            User user = eprovider.getUser(identityId);
            String userOrgId = user != null ? user.getOrganizationID() :
               identityId.orgID != null ? identityId.orgID : Organization.getDefaultOrganizationID();
            removeIdentity(eprovider, identity, userOrgId, () -> eprovider.removeUser(identityId));
            //delete user identityId inside of permissions
            updateIdentityPermissions(type, identityId, null, identityId.orgID, identityId.orgID,true);
            removeUserScopedAssets(identity);
            UserEnv.removeUser(identityId);
            AutoSaveUtils.deleteUserAutoSaveFiles(identityId);
            if(user != null) {
               removeIdentityFromThemes(identityId, user.getOrganizationID(), CustomTheme::getUsers);
            }
            else {
               // nothing was deleted, so a same-named user of another organization keeps its theme
               LOG.debug("User {} not found, skipping the custom theme cleanup", identityId);
            }
         }
         else {
            if(!identityId.equals(oID)) {
               String orgId = identityId.orgID;
               //rep.renameUser(oID, identityId);
               repletRegistryManager.renameUser(oID, identityId);
               dashboardRegistryManager.clear(identityId);
               dashboardRegistryManager.renameUser(oID, identityId);
               dashboardRegistryManager.clear(oID);
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
            String deletedOrgID = identityId.orgID;

            // Evict the org's cached dashboard registries: the global registry and the
            // registries of all of its users, including users the provider does not list (SSO,
            // virtual) and entries cached under the lowercased current-org id. The org
            // identity's key (orgId__orgName) matches none of them.
            dashboardRegistryManager.clearOrganization(deletedOrgID);

            clearDataSourceMetadata();

            if(oOrg != null) {
               String orgID = oOrg.getOrganizationID();
               PortalThemesManager themesManager = portalThemesManager;
               eprovider.removeOrganization(identityId.orgID);

               // delete organization identityId inside of permissions
               authoc.cleanOrganizationFromPermissions(orgID);

               dataCycleManager.clearDataCycles(orgID);
               removeOrgProperties(orgID);
               removeOrgScopedDataSpaceElements(oOrg);
               updateRepletRegistry(orgID, null);
               removeOrganizationThemes(orgID);
               themesManager.removeCSSEntry(orgID);
               themesManager.removeLogoEntry(orgID);
               themesManager.removeFaviconEntry(orgID);
               themesManager.removeWelcomePage(orgID);
               CSSDictionary.resetDictionaryCache();
               themesManager.save();
               removeStorages(orgID);
               favoritesService.removeFavorites(orgID);
               dataSourceRegistry.clearCache(orgID);
               FSService.clearServerNodeCache(orgID);
               XJobPool.resetOrgCache(orgID);
               repletRegistryManager.clearOrgCache(orgID);
               logManager.removeOrgLogLevels(orgID);
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
                                          this, themeService, dashboardRegistryManager, dataCycleManager,
                                          ThreadContext.getContextPrincipal(), true);
               logManager.renameOrgLogLevels(oId, id);
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
               removeIdentity(eprovider, identity, orgId, () -> eprovider.removeGroup(identityId));
               updateIdentityPermissions(type, identityId, null, orgId, orgId, true);
               updatePrincipalGroup(oID, identityId);
               removeIdentityFromThemes(identityId, orgId, CustomTheme::getGroups);
            }
            else {
               //delete role identityId inside of permissions
               String orgId = eprovider.getRole(identityId).getOrganizationID();
               removeIdentity(eprovider, identity, orgId, () -> eprovider.removeRole(identityId));
               updateIdentityPermissions(type, identityId, null, orgId, orgId, true);
               // a global role (null organization) is removed from every organization's themes
               removeIdentityFromThemes(identityId, orgId, CustomTheme::getRoles);
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

   /**
    * Removes a deleted user, group or role from the provider, then clears its dashboards, schedule
    * tasks and (for a user) portal registry. The cleanup runs only once the identity is gone, so
    * that a failed removal, which tells the admin to delete the identity again, keeps them. A
    * global role is first removed from the members of every organization (Bug #77354: a failed
    * member update keeps the role), and is given back to those members if the role is kept.
    *
    * @param orgId the organization of the identity, read before it is removed.
    * @param remove removes the identity from the provider.
    */
   private void removeIdentity(EditableAuthenticationProvider eprovider, Identity identity,
                               String orgId, Runnable remove)
   {
      IdentityID identityId = identity.getIdentityID();
      int type = identity.getType();
      List<Identity> strippedMembers = new ArrayList<>();

      try {
         if(type == Identity.ROLE && identityId.orgID == null) {
            removeGlobalRoleFromMembers(eprovider, identityId, strippedMembers);
         }

         remove.run();
      }
      catch(RuntimeException ex) {
         // the removal can fail after the identity was removed, e.g. on a storage timeout or a
         // failing change listener. It cannot be deleted again then, so it is cleaned up now.
         if(identityExists(eprovider, identityId, type)) {
            restoreGlobalRoleMembers(eprovider, identityId, strippedMembers, ex);
            throw ex;
         }

         LOG.warn("Removing the identity {} failed, but it is no longer in the provider, " +
                     "cleaning it up", identityId, ex);
      }

      cleanUpRemovedIdentity(identity, orgId);
   }

   private boolean identityExists(EditableAuthenticationProvider eprovider, IdentityID identityId,
                                  int type)
   {
      try {
         return switch(type) {
            case Identity.USER -> eprovider.getUser(identityId) != null;
            case Identity.GROUP -> eprovider.getGroup(identityId) != null;
            default -> eprovider.getRole(identityId) != null;
         };
      }
      catch(Exception e) {
         LOG.debug("Failed to check whether the identity {} still exists", identityId, e);
         // assume it does, so its tasks and dashboards are kept
         return true;
      }
   }

   /**
    * Gives a global role whose removal failed back to the members it was removed from. This is
    * best-effort: a failure is logged and attached to the removal error, which is reported.
    */
   private void restoreGlobalRoleMembers(EditableAuthenticationProvider eprovider,
                                         IdentityID roleId, List<Identity> members,
                                         RuntimeException removalError)
   {
      for(Identity member : members) {
         IdentityID memberId = member.getIdentityID();

         try {
            if(member.getType() == Identity.USER) {
               User user = eprovider.getUser(memberId);

               if(user instanceof FSUser fsUser && !Arrays.asList(user.getRoles()).contains(roleId)) {
                  fsUser.setRoles(addRole(user.getRoles(), roleId));
                  eprovider.setUser(memberId, user);
               }
            }
            else if(member.getType() == Identity.GROUP) {
               Group group = eprovider.getGroup(memberId);

               if(group instanceof FSGroup fsGroup &&
                  !Arrays.asList(group.getRoles()).contains(roleId))
               {
                  fsGroup.setRoles(addRole(group.getRoles(), roleId));
                  eprovider.setGroup(memberId, group);
               }
            }
            else {
               Role role = eprovider.getRole(memberId);

               if(role instanceof FSRole fsRole && !Arrays.asList(role.getRoles()).contains(roleId)) {
                  fsRole.setRoles(addRole(role.getRoles(), roleId));
                  eprovider.setRole(memberId, role);
               }
            }
         }
         catch(Exception e) {
            LOG.error("Failed to give the global role {} back to {} after its removal failed",
                      roleId, memberId, e);
            removalError.addSuppressed(e);
         }
      }
   }

   private static IdentityID[] addRole(IdentityID[] roles, IdentityID roleId) {
      IdentityID[] result = Arrays.copyOf(roles, roles.length + 1);
      result[roles.length] = roleId;
      return result;
   }

   /**
    * Clears the dashboards, schedule tasks and portal registry of a user, group or role that
    * has been removed from the provider. A failure is only logged, because the identity is
    * already gone and the rest of its cleanup must not be skipped.
    */
   private void cleanUpRemovedIdentity(Identity identity, String orgId) {
      IdentityID identityId = identity.getIdentityID();
      Identity nid = new DefaultIdentity(identityId, identity.getType());

      try {
         dashboardManager.setDashboards(nid, null);
      }
      catch(Exception e) {
         LOG.warn("Failed to remove the dashboards of the deleted identity {}", identityId, e);
      }

      try {
         scheduleManager.identityRemoved(identity, orgId);
      }
      catch(Exception e) {
         LOG.warn("Failed to update the schedule tasks of the deleted identity {}", identityId, e);
      }

      if(identity.getType() == Identity.USER) {
         try {
            repletRegistryManager.removeUser(identityId);
         }
         catch(Exception e) {
            LOG.warn("Failed to remove the portal registry of the deleted user {}", identityId, e);
         }

         try {
            dashboardRegistryManager.clear(identityId);
         }
         catch(Exception e) {
            LOG.warn("Failed to clear the dashboard registry of the deleted user {}", identityId, e);
         }
      }
   }

   /**
    * Removes a deleted user, group or role from the custom themes, so that a new identity with
    * the same name does not inherit the theme. This is done last in the delete branch and a
    * failure is only logged, because the identity has already been removed from the provider
    * and the rest of its cleanup must not be skipped.
    */
   private void removeIdentityFromThemes(IdentityID identityId, String orgID,
                                         Function<CustomTheme, List<String>> fn)
   {
      try {
         themeService.removeIdentity(identityId.name, orgID, fn);
      }
      catch(Exception e) {
         LOG.warn("Failed to remove the deleted identity {} from the custom themes", identityId, e);
      }
   }

   /**
    * Removes the themes of a deleted organization. A failure is only logged: the themes are
    * left unchanged when they cannot be read reliably (Bug #77222), and the rest of the
    * organization's cleanup must not be skipped.
    */
   private void removeOrganizationThemes(String orgID) {
      try {
         themeService.removeTheme(orgID);
      }
      catch(Exception e) {
         LOG.error("Failed to remove the custom themes of the deleted organization {}", orgID, e);
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

   /**
    * Delete a member that was dropped from an organization with the same cleanup as
    * deleteIdentities(). The cleanup runs in the scope of the edited organization, because some
    * of it (the dashboards) is keyed by the current organization, which differs when a site
    * admin updates another organization through the REST API. If the cleanup fails, the member
    * is still removed from the provider, so dropping a member always deletes it.
    *
    * @return {@code true} if the member was removed from the provider.
    */
   private boolean removeDroppedMember(EditableAuthenticationProvider eprovider, IdentityID id,
                                       int type, String orgID)
   {
      try {
         OrganizationManager.runInOrgScope(orgID, () -> {
            syncIdentity(eprovider, new DefaultIdentity(id, type), null);
            return null;
         });
      }
      catch(Exception ex) {
         LOG.warn("Failed to clean up the removed organization member: {}", id, ex);
         Tool.addUserMessage(Catalog.getCatalog(ThreadContext.getContextPrincipal())
                                .getString("em.security.orgMemberCleanupFailed", id.getName()));

         // the removal may have failed, which keeps the member and its dashboards and schedule
         // tasks. Remove it again and, if that succeeds, run the cleanup that was skipped
         String memberOrgId = null;
         boolean removed = false;

         try {
            if(type == Identity.USER) {
               User user = eprovider.getUser(id);

               if(user != null) {
                  memberOrgId = user.getOrganizationID();
                  eprovider.removeUser(id);
                  removed = true;
               }
            }
            else if(type == Identity.GROUP) {
               Group group = eprovider.getGroup(id);

               if(group != null) {
                  memberOrgId = group.getOrganizationID();
                  eprovider.removeGroup(id);
                  removed = true;
               }
            }
            else if(type == Identity.ROLE) {
               Role role = eprovider.getRole(id);

               if(role != null) {
                  memberOrgId = role.getOrganizationID();
                  eprovider.removeRole(id);
                  removed = true;
               }
            }
         }
         catch(Exception removeEx) {
            LOG.warn("Failed to remove the organization member: {}", id, removeEx);
            return false;
         }

         if(removed) {
            String cleanupOrgId = memberOrgId != null ? memberOrgId :
               id.orgID != null ? id.orgID : orgID;

            try {
               OrganizationManager.runInOrgScope(orgID, () -> {
                  cleanUpRemovedIdentity(new DefaultIdentity(id, type), cleanupOrgId);
                  return null;
               });
            }
            catch(Exception cleanupEx) {
               LOG.warn("Failed to clean up the removed organization member: {}", id, cleanupEx);
            }
         }
      }

      try {
         if(type == Identity.USER) {
            logoutSession(id);
         }

         cluster.sendMessage(new IdentityChangedMessage(type, null, id));
      }
      catch(Exception ex) {
         LOG.warn("Failed to notify the removal of the organization member: {}", id, ex);
      }

      return true;
   }

   private void updateOrganizationMembers(Organization identity, List<IdentityModel> memberModels,
                                          String oldOrgID,
                                          EditableAuthenticationProvider eprovider,
                                          Principal principal)
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
         .filter(newGroup -> Arrays.stream(groups).noneMatch(oldGroup -> oldGroup.getName().equals(newGroup.identityID().getName())))
         .map(IdentityModel::identityID)
         .toArray(IdentityID[]::new);
      IdentityID[] newRoles = memberModels.stream()
         .filter(member -> member.type() == Identity.ROLE)
         .filter(newRole -> Arrays.stream(roles).noneMatch(oldRole -> oldRole.getName().equals(newRole.identityID().getName())))
         .map(IdentityModel::identityID)
         .toArray(IdentityID[]::new);
      // compare with the edited organization's old id, not the current organization, which differs
      // when a site admin updates another organization (REST, shell), otherwise every kept member
      // is "moved" to its own id, i.e. written and removed
      boolean orgIdChange = !Tool.equals(oldOrgID, identity.getId());

      AuthorizationChain authoc = ((AuthorizationChain) securityProvider.getAuthorizationProvider());
      List<IdentityID> droppedUsers = new ArrayList<>();

      // delete the dropped groups and roles before any member moves to a new organization id, the
      // provider only removes a deleted group or role from the identities of its own organization
      for(IdentityID group : groups) {
         if(!members.contains(group.getName())) {
            //group is tied to org, delete if removed as member
            removeDroppedMember(eprovider, group, Identity.GROUP, oldOrgID);
         }
      }

      for(IdentityID role : roles) {
         if(!members.contains(role.getName())) {
            //role is tied to org, delete if removed as member
            removeDroppedMember(eprovider, role, Identity.ROLE, oldOrgID);
         }
      }

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

            //Update replet registry here.
            repletRegistryManager.changeOrgID(oldID, oldOrgID, identity.getId(), false);
            dashboardRegistryManager.migrateRegistry(oldID, securityProvider.getOrganization(oldOrgID), identity);

            // Re-scope the user's own permission grants to the new organization, symmetric with
            // updateRoleForOrg()/updateGroupForOrg(). Without this, permissions granted directly
            // to the user (e.g. portal-tab access) stay scoped to the old org id and are lost when
            // the org id changes, because the role/group re-scoping relocates the permission keys
            // to the new org without carrying the user grantee over. (Bug #75721)
            updateIdentityPermissions(Identity.USER, oldID, user.getIdentityID(),
               oldOrgID, identity.getId(), true);

            eprovider.setUser(user.getIdentityID(), user);
            eprovider.removeUser(oldID);
            repletRegistryManager.renameUser(oldID, user.getIdentityID());
            // Move em favorites to new user
            favoritesService.moveFavorites(oldID.convertToKey(),
                                           user.getIdentityID().convertToKey());
         }
         else if(!members.contains(user.getName())) {
            // like deleteIdentities(), never delete the requesting user, which would also have to
            // log out the session of the request that is being processed
            if(principal != null && isSelfAndEMUser(principal, oldID, Identity.USER)) {
               Tool.addUserMessage(Catalog.getCatalog().getString("em.security.delself"));
               continue;
            }

            if(removeDroppedMember(eprovider, oldID, Identity.USER, oldOrgID)) {
               droppedUsers.add(oldID);
            }
         }
      }

      // sweep the favorites once for all dropped users, as deleteIdentities() does
      removeUserFavorites(droppedUsers);

      for(int i = 0; i < newUsers.length; i ++) {
         // never replace an existing user with a blank one
         if(eprovider.getUser(newUsers[i]) != null) {
            LOG.warn("Skipping organization member, user already exists: {}", newUsers[i]);
            continue;
         }

         FSUser user = new FSUser(newUsers[i]);
         eprovider.setUser(user.getIdentityID(), user);
      }

      for(int i = 0; i < groups.length; i++) {
         // the dropped groups are already deleted
         if(!members.contains(groups[i].getName())) {
            continue;
         }

         FSGroup group = (FSGroup) eprovider.getGroup(groups[i]);

         if(Tool.equals(oldOrgID, group.getOrganizationID())) {
            //if name change or id change, update permissions
            if(orgIdChange) {
               //clone new group with correct name
               updateGroupForOrg(identity, group, orgID, oldOrgID, eprovider, authoc);
            }
         }
         else if(members.contains(group.getName())) {
            //clone new group with correct name
            updateGroupForOrg(identity, group, orgID, oldOrgID, eprovider, authoc);
         }
      }

      for(int i = 0; i < newGroups.length; i ++) {
         if(eprovider.getGroup(newGroups[i]) != null) {
            LOG.warn("Skipping organization member, group already exists: {}", newGroups[i]);
            continue;
         }

         FSGroup group = new FSGroup(newGroups[i]);
         eprovider.setGroup(group.getIdentityID(), group);
      }

      for(int i = 0; i < roles.length; i++) {
         // the dropped roles are already deleted
         if(!members.contains(roles[i].getName())) {
            continue;
         }

         FSRole role = (FSRole) eprovider.getRole(roles[i]);

         if(Tool.equals(oldOrgID, role.getOrganizationID())) {
            if(orgIdChange) {
               updateRoleForOrg(identity, role, orgID, oldOrgID, eprovider, authoc);
            }
         }
         else if(members.contains(role.getName())) {
            updateRoleForOrg(identity, role, orgID, oldOrgID, eprovider, authoc);
         }
      }

      for(int i = 0; i < newRoles.length; i ++) {
         if(eprovider.getRole(newRoles[i]) != null) {
            LOG.warn("Skipping organization member, role already exists: {}", newRoles[i]);
            continue;
         }

         FSRole role = new FSRole(newRoles[i]);
         eprovider.setRole(role.getIdentityID(), role);
      }
   }

   private void updateRoleForOrg(Organization identity, FSRole role, String orgID, String oldOrgID,
                                 EditableAuthenticationProvider eprovider, AuthorizationChain authoc)
   {
      boolean authUpdated = false;

      if(!Tool.equals(oldOrgID, identity.getId())) {
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
            updateIdentityPermissions(Identity.ROLE, role.getIdentityID(), newName, oldOrgID, identity.getId(), true);
            eprovider.setRole(newName, newRole);
            eprovider.removeRole(role.getIdentityID());
         }
         else {
            eprovider.removeRole(role.getIdentityID());
         }
      }

      if(!Tool.equals(oldOrgID, identity.getId()) && !authUpdated) {
         updateIdentityPermissions(Identity.ROLE, role.getIdentityID(), role.getIdentityID(), oldOrgID, identity.getId(), false);
      }
   }

   private void updateGroupForOrg(Organization identity, Group group, String orgName,
                                  String oldOrgID, EditableAuthenticationProvider eprovider,
                                  AuthorizationChain authoc) {
      //if name change
      boolean authUpdated = false;
      if(!Tool.equals(oldOrgID, identity.getId())) {
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
               oldOrgID, identity.getId(), true);
            eprovider.removeGroup(group.getIdentityID(), false);
         }
         else {
            eprovider.removeGroup(group.getIdentityID(), false);
         }
      }

      if(!Tool.equals(oldOrgID, identity.getId()) && !authUpdated) {
         updateIdentityPermissions(
            Identity.GROUP, group.getIdentityID(), group.getIdentityID(),
            oldOrgID, identity.getId(), false);

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
      dashboardManager.removeDashboardStorage(orgID);
      dependencyStorageService.removeDependencyStorage(orgID);
      recycleBin.removeStorage(orgID);
      indexedStorage.removeStorage(orgID);
      libManagerProvider.getManager(orgID).close();

      removeBlobStorage("__mv", orgID, MVStorage.Metadata.class);
      removeBlobStorage("__mvws", orgID, MVWorksheetStorage.Metadata.class);
      removeBlobStorage("__mvBlock", orgID, BlockFileStorage.Metadata.class);
      removeBlobStorage("__pdata", orgID, EmbeddedTableStorage.Metadata.class);
      removeBlobStorage("__library", orgID, LibManager.Metadata.class);
      removeBlobStorage("__tableCacheStore", orgID, LibManager.Metadata.class);
      removeBlobStorage("__autoSave", orgID, AutoSaveUtils.Metadata.class);
      EmbeddedDataCacheHandler.clearOrgCache(orgID);
   }

   public void copyStorages(Organization oOrg, Organization nOrg, boolean rename) {
      try {
         dashboardManager.copyStorageData(oOrg.getId(), nOrg.getId());
         dependencyStorageService.copyStorageData(oOrg, nOrg);
         recycleBin.migrateStorageData(oOrg, nOrg);
         updateLibraryStorage(oOrg.getId(), nOrg.getId(), true);
         indexedStorage.copyStorageData(oOrg, nOrg, rename);
         indexedStorage.setInitialized(nOrg.getId());

         //FSService.copyServerNode(oOrg.getId(), nOrg.getId(), true);
         updateBlobStorageName("__mvws", oOrg.getId(), nOrg.getId(), MVWorksheetStorage.Metadata.class, true);
         updateBlobStorageName("__pdata", oOrg.getId(), nOrg.getId(), EmbeddedTableStorage.Metadata.class, true);
         updateBlobStorageName("__autoSave", oOrg.getId(), nOrg.getId(), AutoSaveUtils.Metadata.class, true);
         updateBlobStorageName("__mvBlock", oOrg.getId(), nOrg.getId(), BlockFileStorage.Metadata.class, true);
         mvManager.migrateStorageData(oOrg, nOrg, !rename);

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
            () -> scheduleManager.getScheduleTasks(orgId));
      }
      catch(Exception e) {
         LOG.warn("Could not get tasks from: "+ orgId);
      }

      if(scheduleTasks == null || scheduleServer == null) {
         return;
      }

      for(ScheduleTask scheduleTask : scheduleTasks) {
         scheduleClient.taskAdded(scheduleTask);
      }
   }

   private void removeOldOrgTaskFormScheduleServer(String oorgId)
      throws RemoteException
   {
      Vector<ScheduleTask> scheduleTasks = scheduleManager.getScheduleTasks(oorgId);

      if(scheduleTasks == null || scheduleServer == null) {
         return;
      }

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
         blobStorageManager.getStorage(orgID.toLowerCase() + suffix, false);
      storage.deleteBlobStorage();
   }

   public void removeOrgProperties(String orgID) {
      // the stored names have the org ID lower case, the log level suffix keeps its case
      String prefix = PropertiesEngine.getOrgPropertyPrefix(orgID);

      Properties properties = SreeEnv.getProperties();
      Set<Object> orgProperties = properties.keySet().stream()
         .filter(prop -> ((String) prop).startsWith(prefix) || ((String) prop).startsWith("log.")
            && ((String) prop).endsWith("^" + orgID))
         .collect(Collectors.toSet());

      for(Object orgProp : orgProperties) {
         String propName = (String) orgProp;
         SreeEnv.remove(propName);
      }
   }

   public void removeOrgScopedDataSpaceElements(Organization oorg) {
      DataSpace dataspace = dataSpace;
      String[] paths = dataspace.getOrgScopedPaths(oorg);

      for(String path : paths) {
         dataspace.delete(path,"");
      }
   }

   public void updateRepletRegistry(String oOID, String nOID) throws Exception {
      RepletRegistry oldRegistry = repletRegistryManager.getRegistry(oOID);
      RepletRegistry newRegistry = repletRegistryManager.getRegistry(oOID);
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
         RepletRegistry oldRegistry = repletRegistryManager.getRegistry(oOID);
         RepletRegistry newRegistry = repletRegistryManager.getRegistry(nOID);
         String[] oldFolders = oldRegistry.getAllFolders();

         for(String oldFolder : oldFolders) {
            if(!Tool.MY_DASHBOARD.equals(oldFolder)) {
               newRegistry.addFolder(oldFolder, false);
            }
         }

         repletRegistryManager.copyFolderContextMap(oOID, nOID);
         IdentityID[] orgUsers = securityEngine.getOrgUsers(oOID);

         if(orgUsers != null) {
            for(IdentityID orgUser : orgUsers) {
               IdentityID newUser = new IdentityID(orgUser.name, nOID);
               repletRegistryManager.copyUser(orgUser, newUser);
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
      dashboardRegistryManager.copyRegistry(null, oorg, norg);

      for(IdentityID user : securityEngine.getOrgUsers(oorg.getId())) {
         dashboardRegistryManager.copyRegistry(user, oorg, norg);
      }
   }

   /**
    * Carries a user's EM favorites over to their copy in another organization, so an
    * organization clone or rename does not leave the copied user with no favorites.
    *
    * @param fromID  the identity of the user in the source organization.
    * @param toID    the identity of the user's copy in the target organization.
    * @param replace {@code true} when the source organization is being replaced/renamed, so
    *                the favorites are moved rather than copied.
    */
   public void copyUserFavorites(IdentityID fromID, IdentityID toID, boolean replace) {
      if(replace) {
         favoritesService.moveFavorites(fromID.convertToKey(), toID.convertToKey());
      }
      else {
         favoritesService.copyFavorites(fromID.convertToKey(), toID.convertToKey());
      }
   }

   public void clearDataSourceMetadata() throws Exception {
      String[] dsNames = xRepository.getDataSourceNames();

      if(xRepository instanceof XEngine) {
         for(String datasource : dsNames) {
            ((XEngine) xRepository).removeMetaDataFiles(datasource);
         }
      }
   }

   private <T extends Serializable> void updateBlobStorageName(String suffix, String oId, String id,
                                                               Class<T> type, boolean copy) throws Exception
   {
      BlobStorage<T> oStorage =
         blobStorageManager.getStorage(oId.toLowerCase() + suffix, false);
      BlobStorage<T> nStorage =
         blobStorageManager.getStorage(id.toLowerCase() + suffix, false);

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
             blobStorageManager.getStorage(oId.toLowerCase() + "__library", false);
          BlobStorage<LibManager.Metadata> nStorage =
             blobStorageManager.getStorage(id.toLowerCase() + "__library", false))
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

      String oldPrefix = PropertiesEngine.getOrgPropertyPrefix(oId);
      String newPrefix = PropertiesEngine.getOrgPropertyPrefix(id);
      Properties properties = SreeEnv.getProperties();

      // a change of only the org ID's case keeps the scoped property names, so there is
      // nothing to move; the log level properties still need it, their suffix keeps its case
      if(!oldPrefix.equals(newPrefix)) {
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

      updateOrgLogProperties(properties, oId, id);
   }

   /**
    * Moves the organization's log level properties (e.g. {@code log.USER.level.bob^oldOrg}) to
    * the new organization ID. This must happen before {@link #removeOrgProperties(String)}
    * removes the properties of the old organization, because removing a log level property also
    * resets its running level (Bug #77006).
    */
   private void updateOrgLogProperties(Properties properties, String oId, String id) {
      String oldSuffix = "^" + oId;
      Set<String> logProperties = properties.keySet().stream()
         .map(prop -> (String) prop)
         .filter(prop -> prop.startsWith("log.") && prop.endsWith(oldSuffix))
         .collect(Collectors.toSet());

      for(String oldName : logProperties) {
         String value = properties.getProperty(oldName);

         if(value != null) {
            String newName =
               oldName.substring(0, oldName.length() - oldSuffix.length()) + "^" + id;
            SreeEnv.setProperty(newName, value);
         }

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
      Permission resourcePerm = authz.getPermission(resourceType, resourceID, orgID);
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
      setIdentityPermissions(
         oldID, newID, resourceType, principal, permittedIdentities, newOrgId, null);
   }

   /**
    * Writes the grant of who may administer (or assign) an identity, keeping the existing
    * grantees the principal cannot administer, since they are not in the requested list.
    *
    * @param oldID               the key the existing grant is stored under.
    * @param newID               the key to store the grant under. The grant at {@code oldID} is
    *                            removed when it differs.
    * @param permittedIdentities the requested grantees. {@code null} is the same as an empty
    *                            list, except that the edited grant-all flag is not set.
    * @param newOrgId            the grantee scope: the org the requested grantees are written
    *                            for. When null or empty, the bucket org if one is given,
    *                            otherwise the ambient current org.
    * @param bucketOrgId         the org whose permission storage holds the grant, where it is
    *                            read, written and removed, and whose existing grantees are kept.
    *                            {@code null} uses the ambient current org, for callers that run
    *                            in the identity's org. Callers that do not (Bug #77271) pass the
    *                            org explicitly.
    */
   public void setIdentityPermissions(IdentityID oldID, IdentityID newID,
                                      ResourceType resourceType, Principal principal,
                                      List<IdentityModel> permittedIdentities,
                                      String newOrgId, String bucketOrgId)
   {
      // with an explicit bucket, never fall back to the ambient org for the grantee scope
      if(bucketOrgId != null && (newOrgId == null || newOrgId.isEmpty())) {
         newOrgId = bucketOrgId;
      }

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
      Permission permission = bucketOrgId == null ?
         authzProvider.getPermission(resourceType, oldID) :
         authzProvider.getPermission(resourceType, oldID, bucketOrgId);
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
         // they will not show up in the permittedIdentities parameter, so we need to re-add them.
         // They are read from the bucket org, so grants scoped to another org are left as they
         // are rather than re-scoped to orgId.
         Set<Permission.PermissionIdentity> userGrantIds = bucketOrgId == null ?
            permission.getUserGrants(action) : permission.getUserGrants(action, bucketOrgId);
         Set<Permission.PermissionIdentity> groupGrantIds = bucketOrgId == null ?
            permission.getGroupGrants(action) : permission.getGroupGrants(action, bucketOrgId);
         Set<Permission.PermissionIdentity> roleGrantIds = bucketOrgId == null ?
            permission.getRoleGrants(action) : permission.getRoleGrants(action, bucketOrgId);
         Set<Permission.PermissionIdentity> orgGrantIds = bucketOrgId == null ?
            permission.getOrganizationGrants(action) :
            permission.getOrganizationGrants(action, bucketOrgId);

         for(Permission.PermissionIdentity userGrant : userGrantIds) {
            if(!securityProvider.checkAnyPermission(
               principal, getResourceType(Identity.USER),
               new IdentityID(userGrant.getName(), userGrant.getOrganizationID()).convertToKey(),
               adminAction))
            {
               userGrants.add(userGrant.getName());
            }
         }

         for(Permission.PermissionIdentity groupGrant : groupGrantIds) {
            if(!securityProvider.checkAnyPermission(
               principal, getResourceType(Identity.GROUP),
               new IdentityID(groupGrant.getName(), groupGrant.getOrganizationID()).convertToKey(),
               adminAction))
            {
               groupGrants.add(groupGrant.getName());
            }
         }

         for(Permission.PermissionIdentity roleGrant : roleGrantIds) {
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

         for(Permission.PermissionIdentity orgGrant : orgGrantIds) {
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

      // only a site admin may change global (org-less) role grants. non site admins do
      // not see them (see getPermission), so keep the existing ones unchanged and
      // ignore any global role rows in the request.
      if(OrganizationManager.getInstance().isSiteAdmin(principal)) {
         permission.setRoleGrantsForOrg(action, globalRoleGrants, null);
      }

      permission.setOrganizationGrantsForOrg(action, organizationGrants, orgId);

      if(permittedIdentities != null && !permittedIdentities.isEmpty()) {
         permission.updateGrantAllByOrg(orgId, true);
      }

      if(bucketOrgId == null) {
         authzProvider.setPermission(resourceType, newID, permission);
      }
      else {
         authzProvider.setPermission(resourceType, newID, permission, bucketOrgId);
      }

      // the grant was re-keyed, so drop the old key rather than leave a stale copy behind
      if(!Objects.equals(oldID, newID)) {
         removePermission(authzProvider, resourceType, oldID, bucketOrgId);
      }

      // an org's self grant is keyed by its mutable name, and before the first rename the name
      // is the id, so also drop a stale (org id, org id) grant left by an earlier rename
      if(resourceType == ResourceType.SECURITY_ORGANIZATION && newID.orgID != null &&
         !newID.orgID.equals(newID.name))
      {
         removePermission(
            authzProvider, resourceType, new IdentityID(newID.orgID, newID.orgID), bucketOrgId);
      }
   }

   /**
    * Removes a grant from the given bucket org, or from the ambient current org when it is null.
    */
   private static void removePermission(AuthorizationProvider authzProvider,
                                        ResourceType resourceType, IdentityID identityID,
                                        String bucketOrgId)
   {
      if(bucketOrgId == null) {
         authzProvider.removePermission(resourceType, identityID);
      }
      else {
         authzProvider.removePermission(resourceType, identityID, bucketOrgId);
      }
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
    * The ids and names of the default and self organizations are hard-coded constants, so they
    * cannot be changed on any save path. A null new name is no change, but a null new id is
    * refused because setOrganizationInfo treats it as a rename, as the EM does.
    */
   private void checkDefaultOrganizationRename(Organization oldOrg, EditOrganizationPaneModel model) {
      String oldId = oldOrg.getId();
      String oldName = oldOrg.getName();
      boolean reserved =
         Organization.getDefaultOrganizationID().equalsIgnoreCase(oldId) ||
         Organization.getSelfOrganizationID().equalsIgnoreCase(oldId) ||
         Organization.getDefaultOrganizationName().equals(oldName) ||
         Organization.getSelfOrganizationName().equals(oldName);

      if(!reserved) {
         return;
      }

      if(!Tool.equals(oldId, model.id())) {
         throw new MessageException(Catalog.getCatalog().getString("em.security.writeDefaultOrgId"));
      }

      if(model.name() != null && !model.name().equals(oldName)) {
         throw new MessageException(Catalog.getCatalog().getString("em.security.writeDefaultOrgName"));
      }
   }

   /**
    * Rejects assigning a theme to an organization by a caller who is not a site administrator
    * unless the theme is global or owned by the organization being edited. The edited
    * organization's original id is used because its own themes are only moved to a new id after
    * the theme is assigned. The default theme and the theme already stored on the organization
    * are always allowed. A missing theme and another organization's theme get the same error so
    * that theme ids cannot be probed.
    */
   public void checkOrganizationTheme(Organization oldOrg, EditOrganizationPaneModel model,
                                      Principal principal)
   {
      String themeId = model.theme();

      if(!SUtil.isMultiTenant() || !securityEngine.isSecurityEnabled() ||
         OrganizationManager.getInstance().isSiteAdmin(principal))
      {
         return;
      }

      if(Tool.isEmptyString(themeId) || CustomTheme.DEFAULT_THEME_ID.equals(themeId) ||
         Tool.equals(themeId, oldOrg.getTheme()))
      {
         return;
      }

      String orgId = oldOrg.getId();
      boolean assignable = customThemesManager.getCustomThemes().stream()
         .anyMatch(t -> themeId.equals(t.getId()) &&
            (Tool.isEmptyString(t.getOrgID()) || t.getOrgID().equalsIgnoreCase(orgId)));

      if(!assignable) {
         throw new java.lang.SecurityException(
            "Unauthorized attempt to assign theme \"" + themeId + "\" to organization \"" + orgId +
            "\" by user " + principal);
      }
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
         final Identity oldIdentity = (Identity) identity.clone();
         identityInfoRecord = getIdentityInfoRecord(model, type,
                                                    identity.getIdentityID(), provider, actionRecord);

         if(!(provider instanceof EditableAuthenticationProvider)) {
            return;
         }

         if(type == Identity.ORGANIZATION) {
            checkDefaultOrganizationRename((Organization) identity, (EditOrganizationPaneModel) model);
            OrganizationIdRules.checkRename(((Organization) identity).getId(),
                                            ((EditOrganizationPaneModel) model).id());
            checkOrganizationTheme((Organization) identity, (EditOrganizationPaneModel) model,
                                   principal);
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

         checkSystemAdminGrant(oldIdentity, model, groupV, principal);
         Identity newIdentity = null;

         if(type == Identity.USER) {
            newIdentity = setUserInfo((FSUser) identity, (EditUserPaneModel) model, eprovider, groupV);
         }
         else if(type == Identity.GROUP) {
            newIdentity = setGroupInfo((FSGroup) identity, (EditGroupPaneModel) model, eprovider, pusers,
                                       pgroups, userV, groupV, memberMap);
         }
         else if(type == Identity.ROLE) {
            newIdentity = setRoleInfo((EditRolePaneModel) model, eprovider, pusers, pgroups, porgs,
                                      userV, groupV, orgV, principal);
         }
         else if(type == Identity.ORGANIZATION) {
            newIdentity = setOrganizationInfo((FSOrganization) identity, (EditOrganizationPaneModel) model,
                                              eprovider, principal);
         }

         cluster.sendMessage(
            new IdentityChangedMessage(type, newIdentity != null ? newIdentity.getIdentityID() : null,
                                       oldIdentity.getIdentityID()));
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
            licenseManager.userChanged();
         }
      }
   }

   /**
    * Rejects an edit by a caller who is not a site administrator when the edit would grant
    * system administrator privileges: adding a role or group that carries a system administrator
    * role, setting the sysAdmin flag on a role, or editing an identity that already grants system
    * administrator (e.g. adding members to the Administrator role). The endpoint permission check
    * only proves the caller may edit the identity, not that it may grant site-wide privileges.
    * <p>
    * {@code groupV} is only consulted for user edits. A group's or role's own parent chain can
    * only change by editing the would-be parent, which is covered by the "already grants system
    * administrator" check on that parent.
    */
   private void checkSystemAdminGrant(Identity oldIdentity, EntityModel model,
                                      List<IdentityID> groupV, Principal principal)
   {
      if(!securityEngine.isSecurityEnabled() ||
         OrganizationManager.getInstance().isSiteAdmin(principal))
      {
         return;
      }

      AuthenticationProvider provider = securityProvider.getAuthenticationProvider();
      int type = oldIdentity.getType();
      Set<IdentityID> oldRoles = new HashSet<>();
      IdentityID[] addedGroups = new IdentityID[0];
      boolean granted = false;

      if(oldIdentity instanceof User user) {
         addRoles(oldRoles, user.getRoles());
         IdentityID[] oldGroups = toGroupIDs(user.getGroups(), user.getOrganizationID());
         Set<IdentityID> oldGroupSet = new HashSet<>(Arrays.asList(oldGroups));
         // setUserInfo() stores membership by group name in the edited user's organization
         String newOrgID = model instanceof EditUserPaneModel userModel ?
            userModel.organization() : user.getOrganizationID();
         addedGroups = groupV.stream()
            .map(g -> new IdentityID(g.name, newOrgID))
            .filter(g -> !oldGroupSet.contains(g))
            .toArray(IdentityID[]::new);
         granted = grantsSystemAdmin(provider, oldRoles.toArray(new IdentityID[0]), oldGroups) ||
            grantsSystemAdmin(provider, new IdentityID[0], addedGroups);
      }
      else if(oldIdentity instanceof Group group) {
         addRoles(oldRoles, group.getRoles());
         granted = grantsSystemAdmin(
            provider, group.getRoles(),
            toGroupIDs(new String[] { group.getName() }, group.getOrganizationID()));
      }
      else if(oldIdentity instanceof Role role) {
         addRoles(oldRoles, role.getRoles());
         granted = grantsSystemAdmin(provider, new IdentityID[] { role.getIdentityID() }, null) ||
            model instanceof EditRolePaneModel roleModel && roleModel.isSysAdmin();
      }

      // Organization edits are intentionally not checked: setOrganizationInfo() does not persist
      // model.roles() and rejects global roles (such as Administrator) as organization members.
      // Revisit this if organization roles are ever saved through setIdentity().
      IdentityID[] addedRoles = new IdentityID[0];

      if(type == Identity.USER || type == Identity.GROUP || type == Identity.ROLE) {
         addedRoles = model.roles().stream()
            .filter(r -> r != null && !oldRoles.contains(r))
            .toArray(IdentityID[]::new);
      }

      if(!granted && (type == Identity.USER || type == Identity.GROUP || type == Identity.ROLE)) {
         granted = grantsSystemAdmin(provider, addedRoles, null);
      }

      if(granted) {
         throw new java.lang.SecurityException(
            "Unauthorized attempt to grant system administrator privileges via \"" +
            oldIdentity.getIdentityID() + "\" by user " + principal);
      }

      checkOrgAdminGrant(provider, oldIdentity, model, principal);

      // Only additions are checked, so roles already stored on the identity that the caller
      // could not assign itself are kept and an unchanged re-save still passes. addedGroups is
      // only set for user edits: in a group edit groupV holds member groups, not parents.
      checkRoleAssignment(provider, addedRoles, addedGroups, oldIdentity.getIdentityID(), principal);
   }

   /**
    * Bug #77498, the organization administrator twin of the system administrator checks in
    * {@link #checkSystemAdminGrant}. Rejects an edit by a caller who is neither a site nor an
    * organization administrator (OrganizationManager.isOrgAdmin(), role based) when the edit
    * would grant organization administrator or take over an organization administrator:
    * <ul>
    *    <li>setting the isOrgAdmin flag on a role that isn't stored with it;</li>
    *    <li>adding a member user or member group to a group or role that grants organization
    *    administrator. Every added member is checked, not only the caller. Members the identity
    *    already has are not, so an unchanged re-save still passes.</li>
    * </ul>
    * Roles and parent groups added to the identity are checked by {@link #checkRoleAssignment}.
    * Organization members of a role are not checked, setRoleInfo() doesn't store them. A user
    * that grants organization administrator is protected by the endpoint gate (SECURITY_USER
    * ADMIN, which DefaultCheckPermissionStrategy only gives site and org admins on such a
    * user), so an unchanged re-save of such a user stays allowed here (Bug #77381).
    */
   private void checkOrgAdminGrant(AuthenticationProvider provider, Identity oldIdentity,
                                   EntityModel model, Principal principal)
   {
      if(OrganizationManager.getInstance().isOrgAdmin(principal)) {
         return;
      }

      IdentityID id = oldIdentity.getIdentityID();
      String reason = null;

      if(oldIdentity instanceof Group group) {
         if(addsMember(provider, model, id, false) &&
            isOrgAdminTarget(provider, group.getRoles(), new IdentityID[] { id }))
         {
            reason = "add members to organization administrator group";
         }
      }
      else if(oldIdentity instanceof Role role) {
         boolean storedFlag = role instanceof FSRole fsRole && fsRole.isOrgAdmin();

         if(model instanceof EditRolePaneModel roleModel && roleModel.isOrgAdmin() && !storedFlag) {
            reason = "set the organization administrator flag on role";
         }
         else if(addsMember(provider, model, id, true) &&
            isOrgAdminTarget(provider, new IdentityID[] { id }, null))
         {
            reason = "add members to organization administrator role";
         }
      }

      if(reason != null) {
         throw new java.lang.SecurityException(
            "Unauthorized attempt to " + reason + " \"" + id + "\" by user " + principal);
      }
   }

   /**
    * Determines if an edited group or role gets a member user or member group it doesn't
    * already have. Members that don't exist are skipped, they are never written.
    *
    * @param target the stored id of the edited group or role.
    * @param role   true when the target is a role, false when it is a group.
    */
   private static boolean addsMember(AuthenticationProvider provider, EntityModel model,
                                     IdentityID target, boolean role)
   {
      for(IdentityModel member : model.members()) {
         IdentityID memberID = member == null ? null : member.identityID();

         if(memberID == null) {
            continue;
         }

         Identity identity;

         if(member.type() == Identity.USER) {
            identity = provider.getUser(memberID);
         }
         else if(member.type() == Identity.GROUP) {
            identity = provider.getGroup(memberID);
         }
         else {
            continue;
         }

         if(identity == null) {
            continue;
         }

         boolean existing = role ?
            identity.getRoles() != null && Arrays.asList(identity.getRoles()).contains(target) :
            identity.getGroups() != null && Arrays.asList(identity.getGroups()).contains(target.name) &&
               Tool.equals(identity.getOrganizationID(), target.orgID);

         if(!existing) {
            return true;
         }
      }

      return false;
   }

   /**
    * Determines if holding the given roles, or being a member of the given groups, makes an
    * identity an organization administrator, the same as OrganizationManager.isOrgAdmin(): the
    * built-in Organization Administrator role doesn't count when multi-tenancy is disabled.
    * Used for the existing identity an edit targets. {@link #grantsOrgAdmin}, used for added
    * roles and groups, also counts that role when multi-tenancy is disabled.
    */
   private static boolean isOrgAdminTarget(AuthenticationProvider provider, IdentityID[] roles,
                                           IdentityID[] groups)
   {
      List<IdentityID> allRoles =
         roles == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(roles));

      if(groups != null && groups.length > 0) {
         for(IdentityID groupID : provider.getAllGroups(groups)) {
            Group group = provider.getGroup(groupID);

            if(group != null && group.getRoles() != null) {
               allRoles.addAll(Arrays.asList(group.getRoles()));
            }
         }
      }

      if(allRoles.isEmpty()) {
         return false;
      }

      IdentityID[] expanded = provider.getAllRoles(allRoles.toArray(new IdentityID[0]));

      return expanded != null && Arrays.stream(expanded)
         .anyMatch(role -> role != null && provider.isOrgAdministratorRole(role) &&
            (!"Organization Administrator".equals(role.name) || SUtil.isMultiTenant()));
   }

   /**
    * Rejects giving roles or parent groups to a new user or group when the caller, who is not
    * a site administrator, may not assign every requested role or, when a requested role or
    * group leads to an organization administrator role, is not an organization administrator.
    * Identity creation adds the new identity to the provider directly, bypassing setIdentity(),
    * so create paths must call this with every requested role and parent group.
    *
    * @param roles        the roles requested for the new identity.
    * @param parentGroups the parent groups requested for the new identity.
    * @param principal    the caller.
    */
   public void checkAssignableRoles(Collection<IdentityID> roles, Collection<IdentityID> parentGroups,
                                    Principal principal)
   {
      if(!securityEngine.isSecurityEnabled() ||
         OrganizationManager.getInstance().isSiteAdmin(principal))
      {
         return;
      }

      IdentityID[] roleArr = roles == null ? new IdentityID[0] :
         roles.stream().filter(Objects::nonNull).distinct().toArray(IdentityID[]::new);
      IdentityID[] groupArr = parentGroups == null ? new IdentityID[0] :
         parentGroups.stream().filter(Objects::nonNull).distinct().toArray(IdentityID[]::new);
      checkRoleAssignment(securityProvider.getAuthenticationProvider(), roleArr, groupArr, null,
                          principal);
   }

   /**
    * Checks the roles and parent groups being added to an identity by a caller that is not a
    * site administrator. This mirrors which roles the EM role tree offers as assignable
    * (UserTreeService.getOrgRoleList): an organization administrator role, whether added
    * directly, inherited or reached through a parent group, may only be granted by an
    * organization administrator. Any other added role is assignable when it belongs to the
    * current organization and the caller is an organization administrator or holds ADMIN on the
    * roles root, or when the caller holds ASSIGN or ADMIN on the role itself. Roles of another
    * organization are never assignable.
    */
   private void checkRoleAssignment(AuthenticationProvider provider, IdentityID[] addedRoles,
                                    IdentityID[] addedGroups, IdentityID target,
                                    Principal principal)
   {
      if(addedRoles.length == 0 && (addedGroups == null || addedGroups.length == 0)) {
         return;
      }

      OrganizationManager orgManager = OrganizationManager.getInstance();
      boolean orgAdmin = orgManager.isOrgAdmin(principal);
      String via = target == null ? "" : " via \"" + target + "\"";

      if(!orgAdmin && grantsOrgAdmin(provider, addedRoles, addedGroups)) {
         throw new java.lang.SecurityException(
            "Unauthorized attempt to grant organization administrator privileges" + via +
            " by user " + principal);
      }

      String currOrgID = orgManager.getCurrentOrgID(principal);
      Boolean rootAdmin = null;

      for(IdentityID role : addedRoles) {
         if(role == null) {
            continue;
         }

         boolean assignable;

         if(role.orgID != null && !role.orgID.equalsIgnoreCase(currOrgID)) {
            assignable = false;
         }
         else if(orgAdmin && (role.orgID != null || provider.isOrgAdministratorRole(role))) {
            assignable = true;
         }
         else {
            if(role.orgID != null && rootAdmin == null) {
               rootAdmin = hasRoleRootAdmin(principal);
            }

            assignable = role.orgID != null && rootAdmin ||
               securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                                role.convertToKey(), ResourceAction.ASSIGN) ||
               securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                                role.convertToKey(), ResourceAction.ADMIN);
         }

         if(!assignable) {
            throw new java.lang.SecurityException(
               "Unauthorized attempt to assign role \"" + role + "\"" + via + " by user " +
               principal);
         }
      }
   }

   /**
    * Determines if the caller holds ADMIN on the roles root of the current organization, which
    * makes every role of that organization assignable in the EM role tree.
    */
   private boolean hasRoleRootAdmin(Principal principal) {
      return securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                              Organization.getRootOrgRoleName(principal),
                                              ResourceAction.ADMIN) ||
         securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                          Organization.getRootRoleName(principal),
                                          ResourceAction.ADMIN);
   }

   /**
    * Rejects creating a user or group under the given parent group by a caller who is not a site
    * administrator when the parent group, or one of its ancestors, grants system administrator.
    * Identity creation adds the new identity to the provider directly, bypassing setIdentity().
    */
   public void checkSystemAdminParentGroup(String parentGroup, String orgID, Principal principal) {
      if(parentGroup == null || !securityEngine.isSecurityEnabled() ||
         OrganizationManager.getInstance().isSiteAdmin(principal))
      {
         return;
      }

      AuthenticationProvider provider = securityProvider.getAuthenticationProvider();

      IdentityID[] groups = new IdentityID[] { new IdentityID(parentGroup, orgID) };

      if(grantsSystemAdmin(provider, new IdentityID[0], groups)) {
         throw new java.lang.SecurityException(
            "Unauthorized attempt to grant system administrator privileges via parent group \"" +
            parentGroup + "\" by user " + principal);
      }

      // a parent group leading to an organization administrator role would make the new
      // identity (and so its creator, which gets ADMIN on it) an organization administrator
      if(!OrganizationManager.getInstance().isOrgAdmin(principal) &&
         grantsOrgAdmin(provider, new IdentityID[0], groups))
      {
         throw new java.lang.SecurityException(
            "Unauthorized attempt to grant organization administrator privileges via parent " +
            "group \"" + parentGroup + "\" by user " + principal);
      }
   }

   /**
    * Determines if a caller that is not a site administrator is trying to act on a user, group
    * or role that grants system administrator, which only a site administrator may do.
    */
   private boolean isSystemAdminTargetDenied(IdentityID identityId, int type, Principal principal) {
      if(identityId == null || !securityEngine.isSecurityEnabled() ||
         OrganizationManager.getInstance().isSiteAdmin(principal))
      {
         return false;
      }

      AuthenticationProvider provider = securityProvider.getAuthenticationProvider();

      if(type == Identity.USER) {
         User user = provider.getUser(identityId);
         return user != null && grantsSystemAdmin(
            provider, user.getRoles(), toGroupIDs(user.getGroups(), user.getOrganizationID()));
      }
      else if(type == Identity.GROUP) {
         return grantsSystemAdmin(provider, new IdentityID[0], new IdentityID[] { identityId });
      }
      else if(type == Identity.ROLE) {
         return grantsSystemAdmin(provider, new IdentityID[] { identityId }, null);
      }

      return false;
   }

   private static void addRoles(Set<IdentityID> set, IdentityID[] roles) {
      if(roles != null) {
         set.addAll(Arrays.asList(roles));
      }
   }

   private static IdentityID[] toGroupIDs(String[] names, String orgID) {
      return names == null ? new IdentityID[0] :
         Arrays.stream(names).map(n -> new IdentityID(n, orgID)).toArray(IdentityID[]::new);
   }

   /**
    * Determines if the given roles, or the roles held by the given groups and their ancestors,
    * include or inherit a system administrator role. A non-existent role whose name matches the
    * global system administrator role is treated as a spoofed grant.
    */
   private static boolean grantsSystemAdmin(AuthenticationProvider provider, IdentityID[] roles,
                                            IdentityID[] groups)
   {
      List<IdentityID> allRoles =
         roles == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(roles));

      if(groups != null) {
         for(IdentityID groupID : provider.getAllGroups(groups)) {
            Group group = provider.getGroup(groupID);

            if(group != null && group.getRoles() != null) {
               allRoles.addAll(Arrays.asList(group.getRoles()));
            }
         }
      }

      for(IdentityID role : provider.getAllRoles(allRoles.toArray(new IdentityID[0]))) {
         if(role == null) {
            continue;
         }

         if(provider.isSystemAdministratorRole(role) ||
            role.orgID != null && provider.getRole(role) == null &&
            provider.isSystemAdministratorRole(new IdentityID(role.name, null)))
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Determines if the given roles, or the roles held by the given groups and their ancestors,
    * include or inherit an organization administrator role (the global Organization
    * Administrator role or any role flagged as an organization administrator role).
    */
   private static boolean grantsOrgAdmin(AuthenticationProvider provider, IdentityID[] roles,
                                         IdentityID[] groups)
   {
      List<IdentityID> allRoles =
         roles == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(roles));

      if(groups != null && groups.length > 0) {
         for(IdentityID groupID : provider.getAllGroups(groups)) {
            Group group = provider.getGroup(groupID);

            if(group != null && group.getRoles() != null) {
               allRoles.addAll(Arrays.asList(group.getRoles()));
            }
         }
      }

      if(allRoles.isEmpty()) {
         return false;
      }

      return Arrays.stream(provider.getAllRoles(allRoles.toArray(new IdentityID[0])))
         .anyMatch(role -> role != null && provider.isOrgAdministratorRole(role));
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
            throw new MessageException(err);
         }

         actionRecord.setActionError("new name:" + name);
         return SUtil.getIdentityInfoRecord(new IdentityID(model.oldName(), oldID.orgID),
                                            type, IdentityInfoRecord.ACTION_TYPE_RENAME,
                                            "Rename " + oldID.name + " to " + name, state);
      }
      else {
         actionRecord.setObjectName(model.name());
         return SUtil.getIdentityInfoRecord(oldID, type, IdentityInfoRecord.ACTION_TYPE_MODIFY,
                                            null, state);
      }
   }

   private Identity setUserInfo(FSUser ouser, EditUserPaneModel model,
                                EditableAuthenticationProvider eprovider, List<IdentityID> groupV)
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
      renameOrganizationMember(model.organization(), ouser.getName(), model.name(), eprovider);

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
            validatePasswordStrength(model.password());
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
         else if(oldUser != null) {
            user.setPassword(oldUser.getPassword());
            user.setPasswordAlgorithm(oldUser.getPasswordAlgorithm());
            user.setPasswordSalt(oldUser.getPasswordSalt());
            user.setAppendPasswordSalt(oldUser.isAppendPasswordSalt());

            if(!Tool.equals(oldUser.getName(), user.getName())) {
               logoutSession(oIdentity);
            }
         }
      }

      syncIdentity(eprovider, user, oIdentity);

      if(sessionRepository != null) {
         try {
            sessionRepository.updatePrincipalRolesAndGroups(oIdentity, user.getRoles(), user.getGroups(), eprovider);
         }
         catch(Exception e) {
            LOG.warn("Failed to update live session principals for user {}", oIdentity, e);
         }
      }

      return user;
   }

   private static final PasswordValidator PASSWORD_VALIDATOR = new PasswordValidator(
      new LengthRule(8, 72),
      new CharacterRule(EnglishCharacterData.UpperCase, 1),
      new CharacterRule(EnglishCharacterData.LowerCase, 1),
      new CharacterRule(EnglishCharacterData.Digit, 1),
      new CharacterRule(EnglishCharacterData.Special, 1)
   );

   public static void validatePasswordStrength(String password) {
      if(password == null) {
         throw new MessageException(Catalog.getCatalog().getString("viewer.password.pwdRule"));
      }

      RuleResult result = PASSWORD_VALIDATOR.validate(new PasswordData(password));

      if(!result.isValid()) {
         throw new MessageException(Catalog.getCatalog().getString("viewer.password.pwdRule"));
      }
   }

   /**
    * Update group info
    */
   private Identity setGroupInfo(FSGroup oldGroup, EditGroupPaneModel model,
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
      renameOrganizationMember(model.organization(), oldGroup.getName(), model.name(), eprovider);

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

      return group;
   }

   /**
    * Set roleInfo, check members changes or not.
    */
   private Identity setRoleInfo(EditRolePaneModel model,
                                EditableAuthenticationProvider eprovider,
                                IdentityID[] pusers, IdentityID[] pgroups, String[] porgs,
                                List<IdentityID> userV, List<IdentityID> groupV,
                                List<IdentityID> orgV, Principal principal)
      throws Exception
   {
      IdentityID newOrgID = new IdentityID(model.name(), model.organization());
      IdentityID oldOrgID = new IdentityID(model.oldName(), model.organization());

      if(model.organization() != null &&
         !model.organization().equals(OrganizationManager.getInstance().getCurrentOrgID()) &&
         !isCrossOrgEditAllowed(principal))
      {
         throw new java.lang.SecurityException(
            "Unauthorized attempt to edit role \"" + oldOrgID + "\" of another organization by user " +
            principal);
      }

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

         if(pgroup == null || (role.getOrganizationID() != null &&
            !Tool.equals(pgroup.getOrganizationID(), role.getOrganizationID())))
         {
            continue;
         }

         IdentityID[] arr = getRoleParents(pgroup.getIdentityID(), pgroup.getRoles(), newOrgID,
                                           oldOrgID, groupV, principal);

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

         if(puser == null) {
            continue;
         }

         IdentityID[] arr = getRoleParents(puser.getIdentityID(), puser.getRoles(), newOrgID,
                                           oldOrgID, userV, principal);

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

      return role;
   }

   /**
    * Determines if the caller may change the role membership of identities outside of its current
    * organization, which only a site administrator may do.
    */
   private boolean isCrossOrgEditAllowed(Principal principal) {
      return !securityEngine.isSecurityEnabled() ||
         OrganizationManager.getInstance().isSiteAdmin(principal);
   }

   private Identity setOrganizationInfo(FSOrganization oldOrg, EditOrganizationPaneModel model,
                                        EditableAuthenticationProvider eprovider,
                                        Principal principal)
      throws Exception
   {
      final String name = model.name();
      final String id = model.id();
      FSOrganization newOrg = new FSOrganization(id);
      newOrg.setName(name);
      List<IdentityModel> members = model.members();

      List<String> memberNames = members.stream()
         .map(IdentityModel::identityID)
         .map(i -> i.name)
         .collect(Collectors.toList());
      String[] oldMembers = oldOrg.getMembers();

      if(oldMembers != null) {
         // members hidden from the editor are never sent back by the client, so keep them
         Arrays.stream(oldMembers)
            .filter(m -> securityProvider.getUser(new IdentityID(m, oldOrg.getId())) != null)
            .filter(m -> isOrgMemberHiddenFrom(new IdentityID(m, oldOrg.getId()), principal))
            .filter(m -> !memberNames.contains(m))
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

         // an existing identity of another organization must never be taken over as a member,
         // updateOrganizationMembers() would overwrite it with a blank identity
         if(isExistingIdentityOfOtherOrg(member, oldOrg.getId(), eprovider)) {
            throw new MessageException(Catalog.getCatalog().getString(
               "em.security.orgMemberFromOtherOrg", member.identityID().getName()));
         }
      }

      // a dropped group or role is deleted, so it must pass the same checks as deleteIdentities().
      // Called after the validation, so a rejected save leaves no message behind on the thread.
      keepUndeletableGroupsAndRoles(oldOrg.getId(), memberNames, eprovider, principal);

      newOrg.setMembers(memberNames.toArray(new String[0]));
      newOrg.setActive(model.status());

      // the old organization is the one being edited, a name lookup can resolve another
      // organization with the same display name
      String oldID = oldOrg.getId();
      oldID = oldID != null ? oldID : id;
      Organization fromOrg = eprovider.getOrganization(oldID);
      String fromOrgID = fromOrg != null ? fromOrg.getId() : null;

      // Tool.equals(String[], List) is always false (an array never equals a non-array), so compare
      // membership by content instead; order is not significant to updateOrganizationMembers() below,
      // which tests membership with List.contains(), not by position.
      Set<String> oldMemberSet = oldOrg.getMembers() != null ?
         new HashSet<>(Arrays.asList(oldOrg.getMembers())) : Collections.emptySet();

      if(model.oldName() == null ||
            !oldMemberSet.equals(new HashSet<>(memberNames)) ||
            !Tool.equals(fromOrgID, model.id()))
      {
         updateOrganizationMembers(newOrg, members, oldID, eprovider, principal);
      }

      // only an id change migrates, a name change is a same-id save
      if(fromOrg != null && !Tool.equals(fromOrgID, newOrg.getId())) {
         dashboardRegistryManager.migrateRegistry(null, fromOrg, newOrg);
         repletRegistryManager.getRegistry(fromOrgID).shutdown();
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

      String theme = getEligibleOrgTheme(model.theme(), oldOrg.getTheme(), oldOrg.getId());

      if(fromOrg != null && Tool.equals(fromOrg.getId(), newOrg.getId()) &&
         fromOrg instanceof FSOrganization)
      {
         fromOrg.setName(name);
         ((FSOrganization) fromOrg).setActive(model.status());
         ((FSOrganization) fromOrg).setLocale(localeString);
         updateCustomThemeOrganization(fromOrg.getTheme(), theme, fromOrgID, fromOrgID);
         ((FSOrganization) fromOrg).setTheme(theme);
         eprovider.setOrganization(fromOrgID, fromOrg);

         return fromOrg;
      }

      newOrg.setLocale(localeString);
      updateCustomThemeOrganization(fromOrg.getTheme(), theme, fromOrgID, newOrg.getId());
      newOrg.setTheme(theme);
      String syncOldName = model.oldName();
      syncIdentity(eprovider, newOrg, new IdentityID(syncOldName, oldID));

      return newOrg;
   }

   /**
    * Determines if an organization's user member is hidden from the given principal in the
    * organization member list. A caller that is not a site administrator can't see users it has
    * no admin permission on, nor site administrator users, whatever fallback grant it may hold.
    */
   public boolean isOrgMemberHiddenFrom(IdentityID userID, Principal principal) {
      if(!securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                           userID.convertToKey(), ResourceAction.ADMIN))
      {
         return true;
      }

      return OrganizationManager.getInstance().isSiteAdmin(securityProvider, userID) &&
         !(principal instanceof XPrincipal &&
            OrganizationManager.getInstance().isSiteAdmin(principal));
   }

   private boolean isExistingIdentityOfOtherOrg(IdentityModel member, String orgID,
                                                EditableAuthenticationProvider eprovider)
   {
      IdentityID identityID = member.identityID();

      if(identityID == null || identityID.getOrgID() == null ||
         Tool.equals(identityID.getOrgID(), orgID))
      {
         return false;
      }

      return switch(member.type()) {
         case Identity.USER -> eprovider.getUser(identityID) != null;
         case Identity.GROUP -> eprovider.getGroup(identityID) != null;
         case Identity.ROLE -> eprovider.getRole(identityID) != null;
         default -> false;
      };
   }

   /**
    * Gets the theme to store as the default of an organization. Only a global theme or a theme
    * owned by the organization may be used, any other requested theme is ignored and the
    * organization keeps its current theme. An empty theme or the default theme id
    * ({@link CustomTheme#DEFAULT_THEME_ID}) clears the organization default.
    *
    * @param theme        the requested theme id.
    * @param currentTheme the current theme id of the organization.
    * @param orgID        the current id of the organization.
    *
    * @return the theme id to store.
    */
   private String getEligibleOrgTheme(String theme, String currentTheme, String orgID) {
      if(Tool.isEmptyString(theme) || CustomTheme.isReservedId(theme)) {
         return null;
      }

      if(Tool.equals(theme, currentTheme)) {
         return currentTheme;
      }

      boolean eligible = customThemesManager.getCustomThemes().stream()
         .anyMatch(t -> Tool.equals(t.getId(), theme) &&
            (Tool.isEmptyString(t.getOrgID()) || Tool.equals(t.getOrgID(), orgID)));

      if(!eligible) {
         LOG.warn("Ignoring theme {} for organization {} because it is not a global theme or " +
                     "a theme of the organization", theme, orgID);
         return currentTheme;
      }

      return theme;
   }

   private void updateCustomThemeOrganization(String oldThemeId, String themeID, String oldOrgID, String newOrgID) {
      if(!Tool.equals(oldThemeId, themeID)) {
         // the organization is partly saved at this point: a theme store that cannot be read
         // reliably (Bug #77222) leaves the themes unchanged and is only logged, so the rest of
         // the save is not skipped
         try {
            customThemesManager.updateCustomThemes(themes -> {
               boolean modified = false;

               if(oldThemeId != null) {
                  CustomTheme oldTheme = themes.stream()
                     .filter(t -> Tool.equals(t.getId(), oldThemeId))
                     .findFirst().orElse(null);

                  if(oldTheme != null) {
                     oldTheme.getOrganizations().remove(oldOrgID);
                     modified = true;
                  }
               }

               if(themeID != null) {
                  CustomTheme theme = themes.stream()
                     .filter(t -> Tool.equals(t.getId(), themeID))
                     .findFirst().orElse(null);

                  if(theme != null) {
                     List<String> themeOrgs = theme.getOrganizations();

                     if(!themeOrgs.contains(newOrgID)) {
                        themeOrgs.add(newOrgID);
                     }

                     theme.setOrganizations(themeOrgs);
                     modified = true;
                  }
               }

               return modified ? themes : null;
            });
         }
         catch(IllegalStateException e) {
            LOG.error("Failed to update the custom themes of organization {}", newOrgID, e);
         }

         if(themeID != null) {
            customThemesManager.setOrgSelectedTheme(themeID, newOrgID);
         }
         else {
            customThemesManager.setOrgSelectedTheme("default", newOrgID);
         }

         // If org ID changed, clean up old org's selection property
         if(!Tool.equals(oldOrgID, newOrgID)) {
            customThemesManager.setOrgSelectedTheme("default", oldOrgID);
         }
      }
   }

   private void updateOrgScopedDataSpace(Organization oorg, Organization norg) {
      DataSpace dataspace = dataSpace;
      String[] paths = dataspace.getOrgScopedPaths(oorg);

      for(String path : paths) {
         if(!dataspace.exists(null, path)) {
            continue;
         }

         String toPath = OrgScopedPaths.rewrite(path, oorg.getId(), norg.getId());

         if(Tool.equals(toPath, path)) {
            continue;
         }

         dataspace.rename(path, toPath);
      }
   }

   /**
    * Replaces a renamed identity's name in the organization's member list. Does nothing when
    * the name is unchanged.
    */
   private void renameOrganizationMember(String orgID, String oldName, String newName,
                                         EditableAuthenticationProvider provider)
   {
      if(Tool.equals(oldName, newName)) {
         return;
      }

      Organization org = provider.getOrganization(orgID);

      if(org == null) {
         return;
      }

      List<String> members = org.getMembers() != null ? new ArrayList<>(Arrays.asList(org.getMembers())) : new ArrayList<>();
      members.remove(oldName);

      if(!members.contains(newName)) {
         members.add(newName);
      }

      org.setMembers(members.toArray(new String[0]));
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
    * Determines if the principal is refused the deletion of a user, group or role because it has
    * no admin permission on it, or because it grants system administrator and the principal is
    * not a site administrator. Shared by deleteIdentities() and the organization member update,
    * so that dropping a member from an organization is checked the same as deleting it.
    */
   private boolean isIdentityDeleteDenied(Principal principal, ResourceType resourceType,
                                          IdentityID identityId, int type)
   {
      try {
         if(!securityEngine.checkPermission(principal, resourceType, identityId.convertToKey(),
                                            ResourceAction.ADMIN))
         {
            return true;
         }
      }
      catch(Exception ignore) {
         return true;
      }

      // only a site admin may delete an identity that grants system administrator
      return isSystemAdminTargetDenied(identityId, type, principal);
   }

   /**
    * Determines if the principal is deleting its own user, a role its user holds directly or
    * through its groups, or one of its groups or their ancestor groups.
    */
   private boolean isSelfDelete(Principal principal, IdentityID identityId, int type,
                                AuthenticationProvider provider)
   {
      if(isSelfAndEMUser(principal, identityId, type)) {
         return true;
      }

      User user = provider.getUser(IdentityID.getIdentityIDFromKey(principal.getName()));

      if(type == Identity.GROUP) {
         return user != null &&
            Arrays.asList(provider.getAllGroups(getUserGroupIDs(user))).contains(identityId);
      }

      return isSelfRole(user, identityId, type, provider);
   }

   /**
    * Gets the ids of the user's groups. They are in the user's own organization, not the current
    * one, so that a site admin who switched into another organization does not match that
    * organization's groups of the same names.
    */
   private static IdentityID[] getUserGroupIDs(User user) {
      String[] groups = user.getGroups();

      if(groups == null) {
         return new IdentityID[0];
      }

      return Arrays.stream(groups)
         .map(g -> new IdentityID(g, user.getOrganizationID()))
         .toArray(IdentityID[]::new);
   }

   /**
    * Keeps the groups and roles dropped from an organization's member list that the principal
    * may not delete, by adding them back to the member names, so that the member update neither
    * deletes them nor leaves them out of the organization's members. The organization's groups
    * and roles are read from the provider, the same set the member update deletes from, because
    * the stored member list of an organization is not kept up to date.
    */
   private void keepUndeletableGroupsAndRoles(String orgID, List<String> memberNames,
                                              EditableAuthenticationProvider eprovider,
                                              Principal principal)
   {
      List<IdentityModel> dropped = new ArrayList<>();
      Arrays.stream(eprovider.getGroups())
         .filter(g -> Tool.equals(orgID, g.orgID) && !memberNames.contains(g.name))
         .forEach(g -> dropped.add(
            IdentityModel.builder().identityID(g).type(Identity.GROUP).build()));
      Arrays.stream(eprovider.getRoles())
         .filter(r -> Tool.equals(orgID, r.orgID) && !memberNames.contains(r.name))
         .forEach(r -> dropped.add(
            IdentityModel.builder().identityID(r).type(Identity.ROLE).build()));
      Catalog catalog = Catalog.getCatalog(principal);
      List<String> unauthorized = new ArrayList<>();

      for(IdentityModel member : dropped) {
         IdentityID id = member.identityID();
         int type = member.type();
         ResourceType resourceType = type == Identity.GROUP ?
            ResourceType.SECURITY_GROUP : ResourceType.SECURITY_ROLE;

         if(isIdentityDeleteDenied(principal, resourceType, id, type)) {
            if(isSystemAdminTargetDenied(id, type, principal)) {
               Tool.addUserMessage(
                  catalog.getString("em.security.orgAdmin.identityPermissionDenied"));
            }
            else {
               unauthorized.add(id.getName());
            }
         }
         else if(principal != null && isSelfDelete(principal, id, type, eprovider)) {
            Tool.addUserMessage(catalog.getString("em.security.delself"));
         }
         // like deleteIdentities(), never delete a group that still has users. A user remains if
         // it is still a member or it is the requester, which the member update never deletes.
         // Users dropped in this save are deleted before the groups, so they don't count.
         else if(type == Identity.GROUP &&
            Arrays.stream(eprovider.getUsers(id)).anyMatch(
               u -> memberNames.contains(u.getName()) ||
                  principal != null && isSelfAndEMUser(principal, u, Identity.USER)))
         {
            Tool.addUserMessage(catalog.getString("em.security.delgroup"));
         }
         else {
            continue;
         }

         if(!memberNames.contains(id.getName())) {
            memberNames.add(id.getName());
         }
      }

      if(!unauthorized.isEmpty()) {
         String warning = String.format(
            "Unauthorized access to resource(s) \"%s\" by user %s.",
            String.join(", ", unauthorized), principal != null ? principal.getName() : null);
         LOG.warn(warning);
         Tool.addUserMessage(catalog.getString("em.common.security.no.permission",
                                               String.join(", ", unauthorized)));
      }
   }

   /**
    * Check if the role is held by the user, directly or through its groups and their ancestor
    * groups.
    */
   private boolean isSelfRole(User user, IdentityID identityID, int type,
                              AuthenticationProvider provider)
   {
      if(type != Identity.ROLE || user == null) {
         return false;
      }

      if(user.getRoles() != null && Arrays.asList(user.getRoles()).contains(identityID)) {
         return true;
      }

      for(IdentityID groupID : provider.getAllGroups(getUserGroupIDs(user))) {
         Group group = provider.getGroup(groupID);

         if(group != null && group.getRoles() != null &&
            Arrays.asList(group.getRoles()).contains(identityID))
         {
            return true;
         }
      }

      return false;
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

   private void removeUserScopedAssets(Identity identity) {
      if(identity == null || identity.getType() != Identity.USER) {
         return;
      }

      final IdentityID identityID = identity.getIdentityID();
      IndexedStorage.Filter filter = key -> {
         AssetEntry entry = AssetEntry.createAssetEntry(key);
         return entry != null && Tool.equals(entry.getUser(), identityID);
      };
      Set<String> keys = indexedStorage.getKeys(filter, identityID.getOrgID());
      keys.stream().forEach(key -> indexedStorage.remove(key));
   }

   /**
    * Remove deleted users from the favoritesUser lists of shared assets they had favorited,
    * so no dangling references are left behind. Scans each affected organization's folders
    * once for the whole batch, so a bulk delete costs one sweep per org rather than one per
    * user.
    */
   private void removeUserFavorites(Collection<IdentityID> identityIDs) {
      if(identityIDs == null || identityIDs.isEmpty()) {
         return;
      }

      favoritesService.removeFavorites(identityIDs);

      Map<String, Set<String>> userKeysByOrg = new HashMap<>();

      for(IdentityID id : identityIDs) {
         if(id != null && id.getOrgID() != null) {
            userKeysByOrg.computeIfAbsent(id.getOrgID(), o -> new HashSet<>())
               .add(id.convertToKey());
         }
      }

      IndexedStorage.Filter filter = key -> {
         AssetEntry entry = AssetEntry.createAssetEntry(key);
         return entry != null && entry.isFolder();
      };

      for(Map.Entry<String, Set<String>> e : userKeysByOrg.entrySet()) {
         String orgID = e.getKey();
         Set<String> userKeys = e.getValue();

         for(String key : indexedStorage.getKeys(filter, orgID)) {
            try {
               XMLSerializable data = indexedStorage.getXMLSerializable(key, null, orgID);

               if(data instanceof AssetFolder folder) {
                  boolean changed = false;

                  for(AssetEntry folderEntry : folder.getEntries()) {
                     for(String userKey : userKeys) {
                        if(folderEntry.getFavoritesUsers().contains(userKey)) {
                           folderEntry.deleteFavoritesUser(userKey);
                           changed = true;
                        }
                     }
                  }

                  if(changed) {
                     OrganizationManager.runInOrgScope(orgID, () -> {
                        indexedStorage.putXMLSerializable(key, folder);
                        return null;
                     });
                  }
               }
            }
            catch(Exception ex) {
               LOG.warn("Failed to remove deleted-user favorites from {}", key, ex);
            }
         }
      }
   }

   public void updateIdentityPermissions(int type, IdentityID oldName, IdentityID newName, String oldOrgId, String newOrgId, boolean doReplace) {
      SecurityProvider provider = securityEngine.getSecurityProvider();
      Organization oldOrganization = oldOrgId == null || oldOrgId.isEmpty() ?
         null : provider.getOrganization(oldOrgId);

      //iterate through all providers when updating permissions, else only first is found and set permissions can be lost
      for(AuthorizationProvider aprovider : securityEngine.getAuthorizationChain().get().getProviders()) {
         List<Tuple4<ResourceType, String, String, Permission>> permissionSetList = null;

         try {
            permissionSetList = aprovider.getPermissions();
         }
         catch(UnsupportedOperationException e) {
            // no-op
         }

         if(permissionSetList != null) {
            for(Tuple4<ResourceType, String, String, Permission> permissionSet : permissionSetList) {
               ResourceType resourceType = permissionSet.getFirst();
               String resourceOrgID = permissionSet.getSecond();
               String path = permissionSet.getThird();
               Permission permission = permissionSet.getForth();

               if(permission == null) {
                  continue;
               }

               if(resourceOrgID != null && !Tool.equals(resourceOrgID, oldOrgId) && !Tool.equals(resourceOrgID, newOrgId) ||
                  newName == null && path.contains(IdentityID.KEY_DELIMITER) && IdentityID.getIdentityIDFromKey(path).orgID != null &&
                     !Tool.equals(IdentityID.getIdentityIDFromKey(path).orgID, oldName.orgID) &&
                     !Tool.equals(IdentityID.getIdentityIDFromKey(path).orgID, oldOrgId))
               {
                  //skip permissions not in this organization
                  continue;
               }

               if(containsOrgID(path, oldName.getOrgID()) && newName == null) {
                  aprovider.removePermission(resourceType, path, resourceOrgID);
               }

               for(ResourceAction action : ResourceAction.values()) {
                  if(permission != null) {
                     if(type == Identity.USER) {
                        boolean empty = permission.isBlank();

                        for(IdentityID granteeName : permission.getOrgScopedUserGrants(action, oldOrganization).toArray(new IdentityID[0])) {
                           if(oldName != null && oldName.name.equals(granteeName.name)) {
                              //rename old grantee to new name
                              updateIdentityPermission(type, newName, oldName, oldOrganization, newOrgId, permission, action);
                           }
                        }

                        // remove self user created resources when deleting self user.
                        if(permission.isBlank() && !empty && oldName != null &&
                           Tool.equals(oldName.getOrgID(), Organization.getSelfOrganizationID()))
                        {
                           removeSelfResource(resourceType, path);
                        }
                     }
                     else if(type == Identity.GROUP) {
                        for(IdentityID granteeName : permission.getOrgScopedGroupGrants(action, oldOrganization).toArray(new IdentityID[0])) {
                           if(oldName != null && oldName.name.equals(granteeName.name)) {
                              //rename old grantee to new name
                              updateIdentityPermission(type, newName, oldName, oldOrganization, newOrgId, permission, action);
                           }
                        }
                     }
                     else if(type == Identity.ROLE) {
                        for(IdentityID granteeName : permission.getOrgScopedRoleGrants(action, oldOrganization).toArray(new IdentityID[0])) {
                           if(oldName != null && oldName.name.equals(granteeName.name)) {
                              //rename old grantee to new name
                              updateIdentityPermission(type, newName, oldName, oldOrganization, newOrgId, permission, action);
                           }
                        }
                     }
                     else if(type == Identity.ORGANIZATION) {
                        updateOrgIdentitiesPermission(newName, oldName, oldOrganization, newOrgId, permission, action);
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

               updatePermission(aprovider, permissionSet, oldOrgName, newOrgName, oldOrgId, newOrgId, doReplace, type);
            }
         }
      }
   }

   private void removeSelfResource(ResourceType resourceType, String path) {
      if(Tool.isEmptyString(path)) {
         return;
      }

      // Bug #77725, a data source and a folder that share the path are kept, and so is the user
      // delete
      try {
         if(resourceType == ResourceType.DATA_SOURCE) {
            dataSourceRegistry.removeDataSource(path);
         }
         else if(resourceType == ResourceType.DATA_SOURCE_FOLDER) {
            dataSourceRegistry.removeDataSourceFolder(path);
         }
      }
      catch(MessageException e) {
         LOG.warn("Kept {} {} of the deleted user: {}", resourceType, path, e.getMessage());
      }
   }

   private void updatePermission(AuthorizationProvider provider,
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
         provider.removePermission(type, path, oorgId);
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
                                              ResourceAction action)
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
         String oldOrgId = oldOrg == null ? null : oldOrg.getOrganizationID();

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

            if(!Tool.equals(oldOrgId, newOrgId)) {
               permission.removeGrantAllByOrg(oldOrgId);
            }
         }
         else if(permission.getOrgEditedGrantAll().containsKey(oldOrgId) &&
            permission.getOrgEditedGrantAll().get(oldOrgId) &&
            !Tool.equals(oldOrgId, newOrgId))
         {
            permission.updateGrantAllByOrg(newOrgId, true);
            permission.removeGrantAllByOrg(oldOrgId);
         }
      }
   }

   private void updateIdentityPermission(int type, IdentityID newIdentityID, IdentityID oldIdentityID,
                                         Organization oldOrg, String newOrgId, Permission permission,
                                         ResourceAction action)
   {
      Set<Permission.PermissionIdentity> orgScopedGrants = new HashSet<>();

      if(permission != null) {
         switch(type) {
         case Identity.USER:
            orgScopedGrants = permission.getAllUserGrants(action);
            break;
         case Identity.GROUP:
            orgScopedGrants = permission.getAllGroupGrants(action);
            break;
         case Identity.ROLE:
            orgScopedGrants = permission.getAllRoleGrants(action);
            break;
         case Identity.ORGANIZATION:
            orgScopedGrants = permission.getOrganizationGrants(action);
            break;
         }
      }
      else {
         permission = new Permission();
      }

      Set<Permission.PermissionIdentity> grants = new HashSet<>();

      if(orgScopedGrants != null) {
         // remove identity, keep every other grantee. The grants are PermissionIdentity
         // objects, so compare the name and organization rather than the IdentityID itself.
         if(newIdentityID == null) {
            orgScopedGrants.stream()
               .filter(identityID -> !(Tool.equals(identityID.getName(), oldIdentityID.getName()) &&
                  Tool.equals(identityID.getOrganizationID(), oldIdentityID.getOrgID())))
               .forEach(identityID -> grants.add(identityID));
         }
         // sync id
         else if(newIdentityID != null) {
            orgScopedGrants.stream()
               .map(identityID -> Tool.equals(identityID.getName(), oldIdentityID.getName()) &&
                  Tool.equals(identityID.getOrganizationID(), oldIdentityID.getOrgID()) ?
                  new Permission.PermissionIdentity(newIdentityID) : identityID)
               .forEach(identityID -> grants.add(identityID));
         }
      }

      permission.setGrants(action, type, grants);

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
         SecurityProvider sProvider = securityEngine.getSecurityProvider();

         updateIdentityPermissions(type, fromIdentity, newIdentity, orgId, newOrgId, replace);
      }
   }

   // `principal` is unused now that the target bucket is resolved via an explicit storageOrgId
   // rather than the ambient principal/org context -- kept for call-site/API stability (the
   // rename call site in AbstractEditableAuthenticationProvider and existing test mocks all
   // still call this 3-arg form).
   public void updateAutoSaveFiles(Organization oorg, Organization norg, Principal principal) {
      updateAutoSaveFilesInBucket(oorg, norg, oorg.getId());
   }

   /**
    * Named distinctly from {@link #updateAutoSaveFiles(Organization, Organization, Principal)}
    * rather than overloaded on it -- a same-name {@code (Organization, Organization, String)}
    * overload makes {@code any(), any(), any()} Mockito stubs against this class ambiguous at
    * compile time (neither {@code Principal} nor {@code String} is more specific than the other).
    *
    * @param storageOrgId the id of the organization whose blob bucket currently holds the auto
    *                      save files to migrate in place -- see
    *                      {@link AutoSaveUtils#migrateAutoSaveFiles(Organization, Organization, String)}.
    */
   public void updateAutoSaveFilesInBucket(Organization oorg, Organization norg, String storageOrgId) {
      AutoSaveUtils.migrateAutoSaveFiles(oorg, norg, storageOrgId);
   }

   public void updateTaskSaveFiles(Organization oorganization, Organization norganization) {
      String oorg = oorganization.getId();
      String norg = norganization.getId();

      if(Tool.equals(oorg, norg)) {
         return;
      }

      // a reserved id names a system folder of external storage (or collides with the base
      // path), so moving it would move system data along with, or into, the organization files
      if(OrganizationIdRules.isReserved(oorg) || OrganizationIdRules.isReserved(norg)) {
         LOG.warn(
            "Did not move the external storage files of organization {} to {}, the organization " +
            "ID is reserved. Move the organization files manually.", oorg, norg);
         return;
      }

      // the files are in a folder named by the id at the top of external storage, and in each
      // save location that is not an FTP server (location/orgId/user/...)
      List<String> locations = OrganizationIdRules.getSaveLocationPaths();
      List<String[]> folders = new ArrayList<>();
      folders.add(new String[] { oorg, norg });

      for(String location : locations) {
         folders.add(new String[] { location + "/" + oorg, location + "/" + norg });
      }

      boolean failed = false;

      for(String[] folder : folders) {
         // a folder that is, or holds, a save location has the files of every organization
         if(OrganizationIdRules.containsSaveLocation(folder[0], locations) ||
            OrganizationIdRules.containsSaveLocation(folder[1], locations))
         {
            LOG.warn(
               "Did not move the external storage folder {} to {}, the folder is used by a " +
               "server save location. Move the organization files manually.", folder[0], folder[1]);
            continue;
         }

         // keep moving the other folders when one fails
         try {
            externalStorageService.renameFolder(folder[0], folder[1]);
         }
         catch(Exception e) {
            LOG.warn("Failed to rename folder {} to {} for organization {}",
                     folder[0], folder[1], oorg, e);
            failed = true;
         }
      }

      if(failed) {
         // one localized message for every failure, never the raw exception text, which can
         // contain server paths
         Tool.addUserMessage(Catalog.getCatalog(ThreadContext.getContextPrincipal())
                                .getString("em.organization.renameIssue"));
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
      SecurityProvider provider = securityEngine.getSecurityProvider();
      IdentityID orgIdentityID = IdentityID.getIdentityIDFromKey(orgKey);

      String dataBaseListString = getOrganizationDatabaseListString(orgIdentityID, principal);

      int orgUsers = (int) Arrays.stream(provider.getUsers())
         .filter(user -> user.orgID.equals(orgIdentityID.orgID))
         .count();

      return
         Catalog.getCatalog().getString("em.security.orgDetailString",orgIdentityID.name,dataBaseListString, orgUsers);
   }

   private String getOrganizationDatabaseListString(IdentityID orgIdentityID, Principal principal) {
      List<String> dataSourceNames = new ArrayList<>(dataSourceRegistry.getSubfolderNames(null, false, orgIdentityID.orgID));
      dataSourceNames.addAll(new ArrayList<>(dataSourceRegistry.getSubDataSourceNames(null, false, orgIdentityID.orgID)));
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
   private final BlobStorageManager blobStorageManager;
   private final FavoritesService favoritesService;
   private final Cluster cluster;
   private final MVManager mvManager;
   private final DataCycleManager dataCycleManager;
   private final DataSourceRegistry dataSourceRegistry;
   private final LogManager logManager;
   private final LicenseManager licenseManager;
   private final ScheduleManager scheduleManager;
   private final IndexedStorage indexedStorage;
   private final ScheduleServer scheduleServer;
   private final ScheduleClient scheduleClient;
   private final CustomThemesManager customThemesManager;
   private final SessionLicenseServiceProvider sessionLicenseServiceProvider;
   private final DashboardRegistryManager dashboardRegistryManager;
   private final LibManagerProvider libManagerProvider;
   private final DashboardManager dashboardManager;
   private final PortalThemesManager portalThemesManager;
   private final RecycleBin recycleBin;
   private final DataSpace dataSpace;
   private final DependencyStorageService dependencyStorageService;
   private final ExternalStorageService externalStorageService;
   private final XRepository xRepository;
   private final RepletRegistryManager repletRegistryManager;
   private final IgniteSessionRepository sessionRepository;
}