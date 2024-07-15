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
package inetsoft.web.admin.content.repository;

import inetsoft.report.internal.Util;
import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.content.repository.model.*;
import inetsoft.web.admin.security.ResourcePermissionModel;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RepositoryDashboardService {
   public RepositoryDashboardService(ResourcePermissionService permissionService,
                                     SecurityProvider securityProvider,
                                     ContentRepositoryTreeService contentRepositoryTreeService,
                                     AnalyticRepository analyticRepository)
   {
      this.permissionService = permissionService;
      this.securityProvider = securityProvider;
      dashboardManager = DashboardManager.getManager();
      this.contentRepositoryTreeService = contentRepositoryTreeService;
      this.analyticRepository = analyticRepository;
   }

   /**
    * Method for getting dashboard configuration model
    *
    * @return PresentationDashboardConfigurationModel
    */
   public RepositoryDashboardSettingsModel getSettings(String dashboardName, IdentityID owner,
                                                       Principal principal)
   {
      DashboardRegistry registry = owner != null ? DashboardRegistry.getRegistry(owner) :
         DashboardRegistry.getRegistry();
      dashboardName = fixDashboardName(dashboardName, owner);
      VSDashboard dashboard = (VSDashboard) registry.getDashboard(dashboardName);
      final ResourcePermissionModel tableModel = owner != null ? null :
         permissionService.getTableModel(dashboardName, ResourceType.DASHBOARD,
                                         EnumSet.of(ResourceAction.ACCESS, ResourceAction.ADMIN), principal);
      Identity anonymous = new DefaultIdentity(XPrincipal.ANONYMOUS, Identity.ROLE);
      boolean enable = Arrays.asList(dashboardManager.getDashboards(anonymous))
         .contains(dashboardName) ||
         Arrays.asList(dashboardManager.getDeselectedDashboards(anonymous)).contains(dashboardName);
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
                           .visible(!SecurityEngine.getSecurity().isSecurityEnabled())
                           .permissions(tableModel)
                           .build();
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

      ActionRecord actionRecord = null;

      try {
         actionRecord = SUtil.getActionRecord(principal, ActionRecord.ACTION_NAME_EDIT, null,
                                              ActionRecord.OBJECT_TYPE_DASHBOARD);
         DashboardRegistry registry = owner != null ? DashboardRegistry.getRegistry(owner) :
            DashboardRegistry.getRegistry();
         String name = model.name();
         VSDashboard dashboard = new VSDashboard();
         String desp = model.description();
         String identifier = model.viewsheet();
         AssetEntry entry = Objects.requireNonNull(AssetEntry.createAssetEntry(identifier));
         String oldName = model.oname();
         Dashboard oldDashboard = registry.getDashboard(oldName);
         ViewsheetEntry oldEntry = ((VSDashboard) oldDashboard).getViewsheet();
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
            actionRecord.setActionName(ActionRecord.ACTION_NAME_RENAME);
            actionRecord.setObjectName(Util.getObjectFullPath(RepositoryEntry.DASHBOARD, oldName, principal, owner));
         }

         // remove the base vs if a new vs replaces it
         if(oldDashboard instanceof VSDashboard) {
            ViewsheetEntry newEntry = dashboard.getViewsheet();

            if(!Tool.equals(newEntry, oldEntry)) {
               removeDashboardViewsheet((VSDashboard) oldDashboard);
            }
         }

         if(registry.getDashboard(name) == null) {
            dashboard.setCreated(System.currentTimeMillis());
            dashboard.setCreatedBy(principal.getName());
         }

         dashboard.setLastModified(System.currentTimeMillis());
         dashboard.setLastModifiedBy(principal.getName());

         registry.addDashboard(name, dashboard);
         registry.save();

         //security permission part
         ResourcePermissionModel permissions = model.permissions();
         boolean security = SecurityEngine.getSecurity().isSecurityEnabled();

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
                  organizationGrants.stream().map(id->id.name).collect(Collectors.toSet()), Identity.Type.ORGANIZATION, Arrays.stream(securityProvider.getOrganizations())
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
                                     Arrays.stream(securityProvider.getOrganizations())
                                     .map(o -> new IdentityID(o,o)).toArray(IdentityID[]::new), name, principal);
            }
         }
         else if(!security) {
            Identity anonymous = new DefaultIdentity(XPrincipal.ANONYMOUS, Identity.ROLE);
            List<String> selectedDashboards = new ArrayList<>(
               Arrays.asList(dashboardManager.getDashboards(anonymous)));
            List<String> deselectedDashboards = new ArrayList<>(
               Arrays.asList(dashboardManager.getDeselectedDashboards(anonymous)));

            if(!selectedDashboards.contains(name) && !deselectedDashboards.contains(name)
               && model.enable())
            {
               selectedDashboards.add(name);
            }
            else if(!model.enable()) {
               if(selectedDashboards.contains(name)) {
                  selectedDashboards.remove(name);
               }
               else if(deselectedDashboards.contains(name)) {
                  deselectedDashboards.remove(name);
               }
            }

            dashboardManager.setDashboards(anonymous, selectedDashboards.toArray(new String[0]));
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
      List<String> deselectedDashboards = new ArrayList<>(
         Arrays.asList(dashboardManager.getDeselectedDashboards(anonymous)));

      if(deselectedDashboards.contains(dashboard) && !enabled) {
         deselectedDashboards.remove(dashboard);
      }

      dashboardManager.setDeselectedDashboards(
         anonymous, deselectedDashboards.toArray(new String[0]));
   }

   public ContentRepositoryTreeNode addDashboard(NewRepositoryFolderRequest parentInfo,
                                                 Principal principal)
      throws Exception
   {
      ActionRecord actionRecord = null;

      try {
         actionRecord = SUtil.getActionRecord(principal, ActionRecord.ACTION_NAME_CREATE, null,
                                              ActionRecord.OBJECT_TYPE_DASHBOARD);
         IdentityID owner = parentInfo.getOwner();
         DashboardRegistry registry = DashboardRegistry.getRegistry(owner);
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
         dashboard.setCreatedBy(principal.getName());
         dashboard.setLastModified(System.currentTimeMillis());
         dashboard.setLastModifiedBy(principal.getName());
         registry.addDashboard(dashboardName, dashboard);
         registry.save();
         Identity identity = SecurityEngine.getSecurity().isSecurityEnabled() ?
            contentRepositoryTreeService.getIdentity((XPrincipal) principal) :
            new DefaultIdentity(XPrincipal.ANONYMOUS, Identity.ROLE);
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
      userGrants.add(IdentityID.getIdentityIDFromKey(principal.getName()).getName());

      for(ResourceAction action : ResourceAction.values()) {
         perm.setUserGrantsForOrg(action, userGrants, OrganizationManager.getInstance().getCurrentOrgID());
      }

      authz.setPermission(ResourceType.DASHBOARD, parentInfo.getPath(), perm);
      return contentRepositoryTreeService.getDashboardNode(parentInfo.getPath(),
                                                           parentInfo.getOwner());
   }

   public void delete(String path, IdentityID owner) throws Exception {
      DashboardRegistry registry = DashboardRegistry.getRegistry(owner);

      if(owner != null && SUtil.isMyDashboard(path)) {
         path = SUtil.getUnscopedPath(path);
      }

      Dashboard dashboard = registry.getDashboard(path);
      registry.removeDashboard(path);
      registry.save();

      if(dashboard instanceof VSDashboard) {
         removeDashboardViewsheet((VSDashboard) dashboard);
      }
   }

   public RepositoryFolderDashboardSettingsModel getDashboardFolderSettings(Principal principal)
   {
      DashboardRegistry registry = DashboardRegistry.getRegistry();
      List<String> dashboardNames = Arrays.asList(registry.getDashboardNames());
      Identity identity = SecurityEngine.getSecurity().isSecurityEnabled() ?
         contentRepositoryTreeService.getIdentity((XPrincipal) principal) :
         new DefaultIdentity(XPrincipal.ANONYMOUS, Identity.ROLE);
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
      Identity identity = SecurityEngine.getSecurity().isSecurityEnabled() ?
         contentRepositoryTreeService.getIdentity((XPrincipal) principal) :
         new DefaultIdentity(XPrincipal.ANONYMOUS, Identity.ROLE);
      List<String> all = Arrays.asList(dashboardManager.getDashboards(identity));
      all.sort(Comparator.comparingInt(model.dashboards()::indexOf));
      dashboardManager.setDashboards(identity, all.toArray(new String[0]));
      boolean security = SecurityEngine.getSecurity().isSecurityEnabled();
      ResourcePermissionModel permissionModel = model.permissions();

      if(security && permissionModel != null && permissionModel.changed()) {
         permissionService.setResourcePermissions("/",
                                                  ResourceType.DASHBOARD,
                                                  permissionModel,
                                                  principal);
      }

      return getDashboardFolderSettings(principal);
   }

   private void setIdentityPermission(Set<String> grants, Identity.Type type, IdentityID[] identities,
                                      String dashboard, Principal principal)
      throws Exception
   {
      //update granted user dashboards.
      for(String identity : grants) {
         DefaultIdentity defaultIdentity = new DefaultIdentity(identity, type.code());
         String[] dashboards = dashboardManager.getDashboards(defaultIdentity);
         List<String> dashboardsList = new ArrayList<>(Arrays.asList(dashboards));

         if(!dashboardsList.contains(dashboard)) {
            dashboardsList.add(dashboard);
            dashboardManager.setDashboards(defaultIdentity, dashboardsList.toArray(new String[0]));
         }
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

         if(grants.contains(elem.name)) {
            iterator.remove();
         }
      }

      for(IdentityID user : ungrantedIdentities) {
         DefaultIdentity identity = new DefaultIdentity(user, type.code());
         String[] dashboards = dashboardManager.getDashboards(identity);
         List<String> dashboardsList = new ArrayList<>(Arrays.asList(dashboards));
         String[] deselectedDashboards = dashboardManager.getDeselectedDashboards(identity);
         List<String> deselectedList = new ArrayList<>(Arrays.asList(deselectedDashboards));
         boolean remove = dashboardsList.remove(dashboard) || deselectedList.remove(dashboard);

         if(remove) {
            dashboardManager.setDashboards(identity, dashboardsList.toArray(new String[0]));
            dashboardManager.setDeselectedDashboards(identity, deselectedList.toArray(new String[0]));
         }
      }
   }


   /**
    * Method for removing a particular viewsheet of a VS Dashboard
    *
    * @param dashboard VS Dashboard to be removed
    */
   private void removeDashboardViewsheet(VSDashboard dashboard) {
      ViewsheetEntry ve = dashboard.getViewsheet();
      AssetRepository engine = AssetUtil.getAssetRepository(false);
      AssetEntry entry = ve == null ? null : AssetEntry.createAssetEntry(ve.getIdentifier());

      if(entry != null && entry.getScope() == AssetRepository.USER_SCOPE) {
         try {
            Principal user = new XPrincipal(entry.getUser());
            Viewsheet vs = (Viewsheet) engine.getSheet(entry, user, false, AssetContent.ALL);

            if(vs.getViewsheetInfo().isComposedDashboard()) {
               engine.removeSheet(entry, user, false);
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
   private AnalyticRepository analyticRepository;
}
