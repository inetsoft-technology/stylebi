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
package inetsoft.web.admin.content.repository;

import inetsoft.report.internal.Util;
import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.RenameInfo;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.content.repository.model.*;
import inetsoft.web.admin.security.ResourcePermissionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RepositoryDashboardService {
   @Autowired
   public RepositoryDashboardService(ResourcePermissionService permissionService,
                                     SecurityProvider securityProvider,
                                     ContentRepositoryTreeService contentRepositoryTreeService,
                                     DashboardManager dashboardManager,
                                     SecurityEngine securityEngine,
                                     DependencyHandler dependencyHandler,
                                     DashboardRegistryManager dashboardRegistryManager,
                                     RenameTransformHandler renameTransformHandler)
   {
      this.permissionService = permissionService;
      this.securityProvider = securityProvider;
      this.dashboardManager = dashboardManager;
      this.contentRepositoryTreeService = contentRepositoryTreeService;
      this.securityEngine = securityEngine;
      this.dependencyHandler = dependencyHandler;
      this.dashboardRegistryManager = dashboardRegistryManager;
      this.renameTransformHandler = renameTransformHandler;
   }

   /**
    * Method for getting dashboard configuration model
    *
    * @return PresentationDashboardConfigurationModel
    */
   public RepositoryDashboardSettingsModel getSettings(String dashboardName, IdentityID owner,
                                                       Principal principal)
   {
      checkOwnerOrg(owner, principal);
      DashboardRegistry registry = owner != null ? dashboardRegistryManager.getRegistry(owner) :
         dashboardRegistryManager.getRegistry();
      dashboardName = fixDashboardName(dashboardName, owner);
      VSDashboard dashboard = (VSDashboard) registry.getDashboard(dashboardName);
      final ResourcePermissionModel tableModel = owner != null ? null :
         permissionService.getTableModel(dashboardName, ResourceType.DASHBOARD,
                                         EnumSet.of(ResourceAction.ACCESS, ResourceAction.ADMIN), principal);
      Identity user = effectiveIdentity(owner, principal);
      boolean enable = Arrays.asList(dashboardManager.getDashboards(user))
         .contains(dashboardName);
      String path = null;
      ViewsheetEntry vsEntry = dashboard.getViewsheet();

      if(vsEntry != null) {
         path = vsEntry.isMyReport() ? Tool.MY_DASHBOARD + "/" + vsEntry.getPath() : vsEntry.getPath();
      }

      return RepositoryDashboardSettingsModel.builder()
                           .name(dashboardName)
                           .oname(dashboardName)
                           .description(dashboard.getDescription())
                           .viewsheet(vsEntry != null ? vsEntry.getIdentifier() : null)
                           .path(path)
                           .enable(enable)
                           .visible(!securityEngine.isSecurityEnabled())
                           .permissions(tableModel)
                           .build();
   }

   /**
    * Rejects a client-supplied dashboard owner from another organization unless the caller is a
    * site administrator. See {@link RepositoryOwnerOrgCheck#checkOwnerOrg}.
    */
   private void checkOwnerOrg(IdentityID owner, Principal principal) {
      RepositoryOwnerOrgCheck.checkOwnerOrg(owner, principal);
   }

   private Identity effectiveIdentity(IdentityID owner, Principal principal) {
      if(!securityEngine.isSecurityEnabled()) {
         return new DefaultIdentity(XPrincipal.ANONYMOUS, Identity.USER);
      }

      if(owner != null) {
         return new DefaultIdentity(owner, Identity.USER);
      }

      XPrincipal xp = (principal instanceof XPrincipal) ? (XPrincipal) principal : null;

      return (xp != null) ? new DefaultIdentity(xp.getIdentityID(), Identity.USER)
                          : new DefaultIdentity(XPrincipal.ANONYMOUS, Identity.USER);
   }

   public RepositoryDashboardSettingsModel setSettings(String path,
                                                       RepositoryDashboardSettingsModel model,
                                                       IdentityID owner,
                                                       Principal principal)
      throws Exception
   {
      if(model.oname() == null) {
         return null;
      }

      checkOwnerOrg(owner, principal);
      IdentityID principalID = IdentityID.getIdentityIDFromKey(principal.getName());

      if((owner == null || !owner.equals(principalID)) &&
         !securityProvider.checkPermission(principal, ResourceType.DASHBOARD, model.oname(), ResourceAction.ADMIN))
      {
         throw new MessageException(Catalog.getCatalog().getString(
            "em.common.security.no.permission", model.oname()));
      }

      ActionRecord actionRecord = null;

      try {
         actionRecord = SUtil.getActionRecord(principal, ActionRecord.ACTION_NAME_EDIT, null,
                                              ActionRecord.OBJECT_TYPE_DASHBOARD);
         DashboardRegistry registry = owner != null ? dashboardRegistryManager.getRegistry(owner) :
            dashboardRegistryManager.getRegistry();
         String name = model.name();
         VSDashboard dashboard = new VSDashboard();
         String desp = model.description();
         String identifier = model.viewsheet();
         AssetEntry entry = AssetEntry.createAssetEntry(identifier);

         if(entry == null) {
            throw new MessageException(
               Catalog.getCatalog().getString("common.invalidEntry", identifier));
         }

         String oldName = model.oname();
         Dashboard oldDashboard = registry.getDashboard(oldName);
         ViewsheetEntry oldEntry = ((VSDashboard) oldDashboard).getViewsheet();

         // the caller must be able to read a newly bound, client-supplied viewsheet
         if(oldEntry == null || !Tool.equals(identifier, oldEntry.getIdentifier())) {
            AssetUtil.getAssetRepository(false)
               .checkAssetPermission(principal, entry, ResourceAction.READ);
         }

         dependencyHandler.updateDashboardDependencies(owner, oldName, false);
         ViewsheetEntry viewsheet = new ViewsheetEntry(entry.getPath(), entry.getUser());
         viewsheet.setIdentifier(identifier);
         dashboard.setViewsheet(viewsheet);
         dashboard.setDescription(desp);

         name = fixDashboardName(name, owner);
         oldName = fixDashboardName(oldName, owner);
         boolean renamed = oldDashboard != null && oldName != null && !oldName.equals(name) && !"".equals(oldName);
         actionRecord.setObjectName(Util.getObjectFullPath(RepositoryEntry.DASHBOARD, name, principal, owner));

         if(name != null && !name.equals(oldName) && registry.getDashboard(name) != null) {
            String msg = Catalog.getCatalog().getString("Duplicate Name") + ", Target Entry: " +
               name;
            actionRecord.setActionName(ActionRecord.ACTION_NAME_RENAME);
            actionRecord.setObjectName(Util.getObjectFullPath(RepositoryEntry.DASHBOARD, oldName, principal, owner));
            throw new MessageException(msg);
         }

         if(renamed) {
            registry.renameDashboard(oldName, name);
            String parent = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : null;
            path = parent != null ? parent + "/" + name : name;
            actionRecord.setObjectName(Util.getObjectFullPath(RepositoryEntry.DASHBOARD, oldName, principal, owner));
            actionRecord.setActionError("new name: " + name);

            String okey = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.DASHBOARD,
                                         oldName, null).toIdentifier();
            String nkey = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.DASHBOARD,
                                         name, null).toIdentifier();

            if(!oldName.endsWith("__GLOBAL")) {
               okey = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.DASHBOARD,
                  oldName, owner).toIdentifier();
               nkey = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.DASHBOARD,
                  name, owner).toIdentifier();
            }

            RenameInfo rinfo = new RenameInfo(okey, nkey, RenameInfo.DASHBOARD);
            renameTransformHandler.addTransformTask(rinfo);
         }

         // remove the base vs if a new vs replaces it
         if(oldDashboard instanceof VSDashboard) {
            ViewsheetEntry newEntry = dashboard.getViewsheet();

            if(!Tool.equals(newEntry, oldEntry)) {
               removeDashboardViewsheet((VSDashboard) oldDashboard, principal);
            }
         }

         IdentityID identityID = IdentityID.getIdentityIDFromKey(principal.getName());

         if(registry.getDashboard(name) == null) {
            dashboard.setCreated(System.currentTimeMillis());
            dashboard.setCreatedBy(identityID.getName());
         }

         dashboard.setLastModified(System.currentTimeMillis());
         dashboard.setLastModifiedBy(identityID.getName());

         registry.putDashboard(name, dashboard);
         dependencyHandler.updateDashboardDependencies(owner, name, true);

         //security permission part
         ResourcePermissionModel permissions = model.permissions();
         boolean security = securityEngine.isSecurityEnabled();

         if(security && permissions != null && (permissions.changed() || renamed)) {
            permissionService.setResourcePermissions(path, ResourceType.DASHBOARD,
                                                     permissions, principal);

            if(permissions.permissions() != null) {
               Permission permission = permissionService.getPermissionFromModel(permissions, principal);
               String orgId = OrganizationManager.getInstance().getCurrentOrgID();
               Set<IdentityID> userGrants = permission.getOrgScopedUserGrants(ResourceAction.ACCESS, orgId);
               Set<IdentityID> groupGrants = permission.getOrgScopedGroupGrants(ResourceAction.ACCESS, orgId);
               Set<IdentityID> roleGrants = permission.getOrgScopedRoleGrants(ResourceAction.ACCESS, orgId);
               Set<IdentityID> organizationGrants = permission.getOrgScopedOrganizationGrants(ResourceAction.ACCESS, orgId);
               setIdentityPermission(
                  userGrants.stream().map(id->id.name).collect(Collectors.toSet()), Identity.Type.USER, securityProvider.getUsers(), name, principal);
               setIdentityPermission(
                  groupGrants.stream().map(id->id.name).collect(Collectors.toSet()), Identity.Type.GROUP, securityProvider.getGroups(), name, principal);
               setIdentityPermission(
                  organizationGrants.stream().map(id->id.name).collect(Collectors.toSet()), Identity.Type.ORGANIZATION, Arrays.stream(securityProvider.getOrganizationIDs())
                                       .map(o -> new IdentityID(o,o)).toArray(IdentityID[]::new), name, principal);
               setIdentityPermission
                  (roleGrants.stream().map(id->id.name).collect(Collectors.toSet()), Identity.Type.ROLE, securityProvider.getRoles(), name, principal);
            }
            else {
               setIdentityPermission(Collections.EMPTY_SET, Identity.Type.USER,
                                     securityProvider.getUsers(), name, principal);
               setIdentityPermission(Collections.EMPTY_SET, Identity.Type.GROUP,
                                     securityProvider.getGroups(), name, principal);
               setIdentityPermission(Collections.EMPTY_SET, Identity.Type.ROLE,
                                     securityProvider.getRoles(), name, principal);
               setIdentityPermission(Collections.EMPTY_SET, Identity.Type.ORGANIZATION,
                                     Arrays.stream(securityProvider.getOrganizationIDs())
                                     .map(o -> new IdentityID(o,o)).toArray(IdentityID[]::new), name, principal);
            }
         }
         else if(!security) {
            Identity anonymous = new DefaultIdentity(XPrincipal.ANONYMOUS, Identity.USER);
            String dashboardName = name;
            boolean enable = model.enable();

            // changes the stored names in one locked read-modify-write, so a concurrent change
            // of the selection is not overwritten (Bug #77872). The deselected name is removed
            // by removeManagerDashboards() below.
            dashboardManager.updateDashboardLists(anonymous, (selected, deselected) -> {
               if(!selected.contains(dashboardName) && !deselected.contains(dashboardName) &&
                  enable)
               {
                  selected.add(dashboardName);
               }
               else if(!enable) {
                  selected.remove(dashboardName);
               }
            });

            removeManagerDashboards(anonymous, name, model.enable());
            removeManagerDashboards(new DefaultIdentity(XPrincipal.ANONYMOUS, Identity.USER), name,
                                                        model.enable());
         }

         return getSettings(name, owner, principal);
      }
      catch(Exception e) {
         if(actionRecord != null) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError(e.getMessage());
         }

         throw e;
      }
      finally {
         if(actionRecord != null) {
            Audit.getInstance().auditAction(actionRecord, principal);
         }
      }
   }

   public void removeManagerDashboards(Identity anonymous, String dashboard, boolean enabled) {
      // one locked read-modify-write of the stored names (Bug #77872)
      dashboardManager.updateDashboardLists(anonymous, (selected, deselected) -> {
         if(!enabled) {
            deselected.remove(dashboard);
         }
      });
   }

   public ContentRepositoryTreeNode addDashboard(NewRepositoryFolderRequest parentInfo,
                                                 Principal principal)
      throws Exception
   {
      checkOwnerOrg(parentInfo.getOwner(), principal);

      if(!securityProvider.checkPermission(principal, ResourceType.DASHBOARD, "/", ResourceAction.ADMIN)) {
         throw new MessageException(Catalog.getCatalog().getString(
            "em.common.security.no.permission", "/"));
      }

      ActionRecord actionRecord = null;

      try {
         actionRecord = SUtil.getActionRecord(principal, ActionRecord.ACTION_NAME_CREATE, null,
                                              ActionRecord.OBJECT_TYPE_DASHBOARD);
         IdentityID owner = parentInfo.getOwner();
         DashboardRegistry registry = dashboardRegistryManager.getRegistry(owner);
         String dashboardName = null;

         for(int i = 1; i < Integer.MAX_VALUE; i++) {
            String name = "Dashboard" + i;
            name = fixDashboardName(name, owner);

            if(registry.getDashboard(name) == null) {
               dashboardName = name;
               parentInfo.setPath(name);
               break;
            }
         }

         actionRecord.setObjectName(Util.getObjectFullPath(RepositoryEntry.DASHBOARD, dashboardName, principal, owner));
         VSDashboard dashboard = new VSDashboard();
         dashboard.setCreated(System.currentTimeMillis());
         IdentityID identityID = IdentityID.getIdentityIDFromKey(principal.getName());
         dashboard.setCreatedBy(identityID.getName());
         dashboard.setLastModified(System.currentTimeMillis());
         dashboard.setLastModifiedBy(identityID.getName());
         registry.putDashboard(dashboardName, dashboard);
         Identity identity = securityEngine.isSecurityEnabled() ?
            contentRepositoryTreeService.getIdentity((XPrincipal) principal) :
            new DefaultIdentity(XPrincipal.ANONYMOUS, Identity.USER);
         dashboardManager.addDashboard(identity, dashboardName);
      }
      catch(Exception e) {
         if(actionRecord != null) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError(e.getMessage());
         }

         throw e;
      }
      finally {
         if(actionRecord != null) {
            Audit.getInstance().auditAction(actionRecord, principal);
         }
      }

      AuthorizationProvider authz = securityProvider.getAuthorizationProvider();
      Permission perm = new Permission();
      Set<String> userGrants = new HashSet<>();
      String currentOrgID = OrganizationManager.getInstance().getCurrentOrgID();
      List<IdentityID> adminUsers = OrganizationManager.getInstance().orgAdminUsers(currentOrgID);
      IdentityID currentUser = IdentityID.getIdentityIDFromKey(principal.getName());

      if(currentOrgID.equals(currentUser.orgID)) {
         userGrants.add(currentUser.getName());
      }
      else if(!adminUsers.isEmpty()){
         userGrants.add(adminUsers.getFirst().getName());
      }
      else {
         userGrants.add(null);
      }

      for(ResourceAction action : ResourceAction.values()) {
         perm.setUserGrantsForOrg(action, userGrants, currentOrgID);
      }

      perm.updateGrantAllByOrg(currentOrgID, true);
      String warning = null;

      // Bug #78217, the dashboard is already created, a failed grant is shown as a warning
      try {
         authz.setPermission(ResourceType.DASHBOARD, parentInfo.getPath(), perm);
      }
      catch(RuntimeException e) {
         warning = AbstractAssetEngine.getCreatorPermissionWarning(
            ResourceType.DASHBOARD, parentInfo.getPath(), e);
      }

      ContentRepositoryTreeNode node = contentRepositoryTreeService.getDashboardNode(
         parentInfo.getPath(), parentInfo.getOwner());

      if(warning != null && node != null) {
         node = ContentRepositoryTreeNode.builder().from(node).warning(warning).build();
      }

      return node;
   }

   public void delete(String path, IdentityID owner, Principal principal) throws Exception {
      checkOwnerOrg(owner, principal);
      DashboardRegistry registry = dashboardRegistryManager.getRegistry(owner);

      if(owner != null && SUtil.isMyDashboard(path)) {
         path = SUtil.getUnscopedPath(path);
      }

      dependencyHandler.updateDashboardDependencies(owner, path, false);
      Dashboard dashboard = registry.getDashboard(path);
      registry.removeDashboard(path);

      if(dashboard instanceof VSDashboard) {
         removeDashboardViewsheet((VSDashboard) dashboard, principal);
      }
   }

   public RepositoryFolderDashboardSettingsModel getDashboardFolderSettings(Principal principal)
   {
      DashboardRegistry registry = dashboardRegistryManager.getRegistry();
      List<String> dashboardNames = Arrays.asList(registry.getDashboardNames());
      Identity identity = getDashboardFolderIdentity(principal);
      List<String> sortedDashboards = Arrays.asList(dashboardManager.getDashboards(identity));
      List<String> selectedDashboards = dashboardNames.stream()
         .filter(sortedDashboards::contains)
         .sorted(Comparator.comparingInt(sortedDashboards::indexOf))
         .collect(Collectors.toList());
      final ResourcePermissionModel tableModel = permissionService.getTableModel("/",
         ResourceType.DASHBOARD, EnumSet.of(ResourceAction.ADMIN), principal);
      return RepositoryFolderDashboardSettingsModel.builder()
         .dashboards(selectedDashboards)
         .permissions(tableModel)
         .build();
   }

   public RepositoryFolderDashboardSettingsModel setDashboardFolderSettings(
      RepositoryFolderDashboardSettingsModel model, Principal principal) throws Exception
   {
      if(!securityProvider.checkPermission(principal, ResourceType.DASHBOARD, "/", ResourceAction.ADMIN)) {
         throw new MessageException(Catalog.getCatalog().getString(
            "em.common.security.no.permission", "/"));
      }

      Identity identity = getDashboardFolderIdentity(principal);
      List<String> order = model.dashboards();

      // reorders the stored names in one locked read-modify-write, so a dashboard that is
      // created or renamed meanwhile is not overwritten, and the names that are not in the
      // request or that getDashboards() leaves out are kept. The request only gives the order,
      // its names are not added (Bug #77872).
      dashboardManager.updateDashboards(identity, dashboards -> {
         String[] sorted = dashboards.clone();
         Arrays.sort(sorted, Comparator.comparingInt(order::indexOf));
         return sorted;
      });

      boolean security = securityEngine.isSecurityEnabled();
      ResourcePermissionModel permissionModel = model.permissions();

      if(security && permissionModel != null && permissionModel.changed()) {
         permissionService.setResourcePermissions("/",
                                                  ResourceType.DASHBOARD,
                                                  permissionModel,
                                                  principal);
      }

      return getDashboardFolderSettings(principal);
   }

   private Identity getDashboardFolderIdentity(Principal principal) {
      String orgID = OrganizationManager.getInstance().getCurrentOrgID();
      List<IdentityID> adminUsers = OrganizationManager.getInstance().orgAdminUsers(orgID);
      IdentityID currentUser = IdentityID.getIdentityIDFromKey(principal.getName());
      Identity identity = new DefaultIdentity(XPrincipal.ANONYMOUS, Identity.USER);

      if(securityEngine.isSecurityEnabled()) {
         if(orgID.equals(currentUser.orgID)) {
            identity = contentRepositoryTreeService.getIdentity((XPrincipal) principal);
         }
         else {
            identity = adminUsers.isEmpty() ? null :
               new DefaultIdentity(adminUsers.getFirst().name, Identity.USER);
         }
      }

      return identity;
   }

   private void setIdentityPermission(Set<String> grants, Identity.Type type, IdentityID[] identities,
                                      String dashboard, Principal principal)
      throws Exception
   {
      //update granted user dashboards.
      for(String identity : grants) {
         DefaultIdentity defaultIdentity = new DefaultIdentity(identity, type.code());
         // appended in one locked read-modify-write, so a concurrent change of the identity's
         // selection is not overwritten (Bug #77872)
         dashboardManager.addDashboard(defaultIdentity, dashboard);
      }

      //update ungranted user dashboards.
      Set<IdentityID> ungrantedIdentities;

      if(grants != Collections.EMPTY_SET) {
         ungrantedIdentities = Arrays.stream(identities)
            .filter(user -> permissionService.isIdentityAuthorized(user, type, principal))
            .collect(Collectors.toSet());
      }
      else {
         ungrantedIdentities = new HashSet<>(Arrays.asList(identities));
      }

      Iterator<IdentityID> iterator = ungrantedIdentities.iterator();

      while(iterator.hasNext()) {
         IdentityID elem = iterator.next();

         //exempt previously enabled admin from being removed by permission change
         boolean isAdmin = OrganizationManager.getInstance().isSiteAdmin(elem) ||
                           OrganizationManager.getInstance().isOrgAdmin(elem);

         if(isAdmin || grants.contains(elem.name)) {
            iterator.remove();
         }
      }

      for(IdentityID user : ungrantedIdentities) {
         DefaultIdentity identity = new DefaultIdentity(user, type.code());

         // both lists are changed in one locked read-modify-write, so a concurrent change of
         // either is not overwritten (Bug #77872). As before, the name is removed from the
         // deselected names only when it is not selected.
         dashboardManager.updateDashboardLists(identity, (selected, deselected) -> {
            if(!selected.remove(dashboard)) {
               deselected.remove(dashboard);
            }
         });
      }
   }


   /**
    * Method for removing a particular viewsheet of a VS Dashboard
    *
    * @param dashboard VS Dashboard to be removed
    * @param principal the caller, used so the asset engine enforces org and owner checks
    */
   private void removeDashboardViewsheet(VSDashboard dashboard, Principal principal) {
      ViewsheetEntry ve = dashboard.getViewsheet();
      AssetRepository engine = AssetUtil.getAssetRepository(false);
      AssetEntry entry = ve == null ? null : AssetEntry.createAssetEntry(ve.getIdentifier());

      if(entry != null && entry.getScope() == AssetRepository.USER_SCOPE) {
         try {
            Viewsheet vs = (Viewsheet) engine.getSheet(entry, principal, false, AssetContent.ALL);

            if(vs.getViewsheetInfo().isComposedDashboard()) {
               engine.removeSheet(entry, principal, false);
            }
         }
         catch(Exception e) {
            // ignore
         }
      }
   }

   /**
    *
    * @return   the dashboard name for registry.
    */
   private String fixDashboardName(String dashboard, IdentityID owner) {
      if(dashboard != null && owner == null && !dashboard.endsWith("__GLOBAL")) {
         return dashboard + "__GLOBAL";
      }

      return dashboard;
   }

   private final ContentRepositoryTreeService contentRepositoryTreeService;
   private final ResourcePermissionService permissionService;
   private final SecurityProvider securityProvider;
   private final DashboardManager dashboardManager;
   private final SecurityEngine securityEngine;
   private final DependencyHandler dependencyHandler;
   private final DashboardRegistryManager dashboardRegistryManager;
   private final RenameTransformHandler renameTransformHandler;
}
