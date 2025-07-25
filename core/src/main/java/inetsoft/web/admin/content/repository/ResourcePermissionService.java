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

import inetsoft.report.LibManager;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Identity;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.security.ResourcePermissionModel;
import inetsoft.web.admin.security.ResourcePermissionTableModel;
import inetsoft.web.viewsheet.*;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class ResourcePermissionService {
   @Autowired
   public ResourcePermissionService(SecurityProvider securityProvider,
                                    SecurityEngine securityEngine)
   {
      this.securityProvider = securityProvider;
      this.securityEngine = securityEngine;
   }

   public ResourcePermissionModel getTableModel(String path, ResourceType type,
                                                EnumSet<ResourceAction> actions,
                                                Principal principal)
   {
      return getTableModel(path, type, actions, principal, false);
   }

   public ResourcePermissionModel getTableModel(String path, ResourceType type,
                                                EnumSet<ResourceAction> actions,
                                                Principal principal, boolean tableStyleFolder)
   {
      boolean isRoot = "/".equals(path) &&
         (type == ResourceType.ASSET || type == ResourceType.REPORT || type == ResourceType.DASHBOARD) ||
         (type == ResourceType.LIBRARY && "*".equals(path) ||
         (type == ResourceType.DATA_SOURCE_FOLDER && "/".equals(path)) ||
         (type == ResourceType.SCHEDULE_TASK_FOLDER && "/".equals(path)));
      boolean isDenyLabel = isRoot || type == ResourceType.SCHEDULE_CYCLE || type == ResourceType.SCHEDULE_TIME_RANGE;
      String label = isDenyLabel ? Catalog.getCatalog().getString("Deny access to all users") : null;
      return getTableModel(path, type, actions, label, principal, tableStyleFolder);
   }

   public ResourcePermissionModel getTableModel(String path, ResourceType type, EnumSet<ResourceAction> actions,
                                                String label, Principal principal)
   {
      return getTableModel(path, type, actions, label, principal, false);
   }

   public ResourcePermissionModel getTableModel(String path, ResourceType type, EnumSet<ResourceAction> actions,
                                                String label, Principal principal, boolean tableStyleFolder)
   {
      if(label == null) {
         label = "Use Parent Permissions";
      }

      if(type == ResourceType.CUBE) {
         actions = EnumSet.of(ResourceAction.READ);
      }

      List<ResourcePermissionTableModel> resourcePermissions = getResourcePermissions(path, type, principal, tableStyleFolder);

      boolean hasOrgEdited = hasOrgEditedPerm(path, type, tableStyleFolder);

      ResourcePermissionModel.Builder builder =
         ResourcePermissionModel.builder()
            .permissions(resourcePermissions != null && !resourcePermissions.isEmpty() ? resourcePermissions :
                            hasOrgEdited ? Collections.emptyList() : null)
            .displayActions(actions)
            .hasOrgEdited(hasOrgEdited)
            .derivePermissionLabel(Catalog.getCatalog().getString(label))
            .securityEnabled(securityEngine.isSecurityEnabled())
            .requiresBoth(Boolean.parseBoolean(SreeEnv.getProperty("permission.andCondition",false, true)));

      boolean hasGrantReadToAll = type == ResourceType.DATA_SOURCE_FOLDER && "/".equals(path) ||
         type == ResourceType.TABLE_STYLE_LIBRARY && catalog.getString("*").equals(path) ||
         type == ResourceType.SCRIPT_LIBRARY && catalog.getString("*").equals(path) ||
         type == (ResourceType.SCHEDULE_TASK_FOLDER) && "/".equals(path);

      if(hasGrantReadToAll) {
         switch(type) {
         case DATA_SOURCE_FOLDER:
            builder
               .grantReadToAll("true".equals(SreeEnv.getProperty("security.datasource.everyone", "true")))
               .grantReadToAllLabel(catalog.getString("em.security.datasourceUsedByEveryone"));
            break;
         case TABLE_STYLE_LIBRARY:
            builder
               .grantReadToAll("true".equals(SreeEnv.getProperty("security.tablestyle.everyone", "true")))
               .grantReadToAllLabel(catalog.getString("em.security.tablestyleUsedByEveryone"));
            break;
         case SCRIPT_LIBRARY:
            builder
               .grantReadToAll("true".equals(SreeEnv.getProperty("security.script.everyone", "true")))
               .grantReadToAllLabel(catalog.getString("em.security.scriptUsedByEveryone"));
            break;
         case SCHEDULE_TASK_FOLDER:
            builder
               .grantReadToAll("true".equals(SreeEnv.getProperty("security.scheduletask.everyone", "true")))
               .grantReadToAllLabel(catalog.getString("em.security.scheduleTaskUsedByEveryone"));
            break;
         }
      }

      return builder.grantReadToAllVisible(hasGrantReadToAll).build();

   }


//   public boolean parsePermissionOrganization(String permName, Identity.Type type, String orgID) {
//      String orgName = "";
//
//      for(String org : securityProvider.getOrganizations()) {
//         if(securityProvider.getOrganization(org).getOrganizationID().equals(orgID)) {
//            orgName = org;
//            break;
//         }
//      }
//
//      switch(type) {
//      case USER :
//         User u = securityProvider.getUser(permName);
//         return u.getOrganization() == null || u.getOrganization().equals(orgName);
//      case GROUP:
//         Group g = securityProvider.getGroup(permName);
//         return g.getOrganization() == null || g.getOrganization().equals(orgName);
//      case ROLE:
//         Role r = securityProvider.getRole(permName);
//         return r.getOrganization() == null || r.getOrganization().equals(orgName);
//      case ORGANIZATION:
//         return permName.equals(orgName);
//      default:
//         return false;
//      }
//   }

   /**
    * Get repository permission table model
    *
    * @param path         path of resource
    * @param resourceType type of resource
    * @param principal    current principal
    *
    * @return the resource's current permissions mapped by actions for each user/group/role/organization
    */
   private List<ResourcePermissionTableModel> getResourcePermissions(String path,
                                                                     ResourceType resourceType,
                                                                     Principal principal,
                                                                     boolean tableStyleFolder)
   {
      String resourcePath = getPermissionResourcePath(path, resourceType, tableStyleFolder);
      Permission permission =
         securityProvider.getAuthorizationProvider().getPermission(resourceType, resourcePath);

      if(permission == null) {
         return null;
      }

      return getModelFromPermission(permission, principal);
   }

   public static String getPermissionResourcePath(String path, ResourceType resourceType,
                                                  boolean tableStyleFolder)
   {
      if(path == null || resourceType == null) {
         return path;
      }

      if(resourceType == ResourceType.DATA_SOURCE) {
         return getDataSourceResourceName(path);
      }
      else if(!tableStyleFolder && ResourceType.TABLE_STYLE == resourceType) {
         XTableStyle tableStyle = LibManager.getManager().getTableStyle(path);
         return tableStyle == null ? path : tableStyle.getID();
      }

      return path;
   }

   /**
    * Get the set of organizations that have use parent permission inheritance on this resource
    *
    * @param path         path of resource
    * @param resourceType type of resource
    * @param principal    current principal
    *
    * @return the set of organizations that use parent inheritance on the resource
    */
   private boolean hasOrgEditedPerm(String path, ResourceType resourceType, boolean tableStyleFolder)
   {
      String resourcePath =
         getPermissionResourcePath(path, resourceType, tableStyleFolder);
      Permission permission =
         securityProvider.getAuthorizationProvider().getPermission(resourceType, resourcePath);

      if(permission == null) {
         return false;
      }

      return permission.hasOrgEditedGrantAll(OrganizationManager.getInstance().getCurrentOrgID());
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectType = ActionRecord.OBJECT_TYPE_OBJECTPERMISSION
   )
   public void setResourcePermissions(@AuditObjectName String path, ResourceType resourceType,
                                      ResourcePermissionModel tableModel,
                                      @AuditUser Principal principal)
      throws IOException
   {
      setResourcePermissions0(path, resourceType, tableModel, principal);
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectType = ActionRecord.OBJECT_TYPE_OBJECTPERMISSION
   )
   public void setResourcePermissions(String path, ResourceType resourceType,
                                      @SuppressWarnings("unused") @AuditObjectName String auditPath,
                                      ResourcePermissionModel tableModel,
                                      @AuditUser Principal principal)
      throws IOException
   {
      setResourcePermissions(path, resourceType, auditPath, tableModel, principal, false);
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectType = ActionRecord.OBJECT_TYPE_OBJECTPERMISSION
   )
   public void setResourcePermissions(String path, ResourceType resourceType,
                                      @SuppressWarnings("unused") @AuditObjectName String auditPath,
                                      ResourcePermissionModel tableModel,
                                      @AuditUser Principal principal, boolean tableStyleFolder)
      throws IOException
   {
      setResourcePermissions0(path, resourceType, tableModel, principal, tableStyleFolder);
   }


   /**
    * Remove the old asset permission and set it to new asset for rename asset.
    * @param oldPath old asset path before rename.
    * @param newPath new asset path after rename
    * @param resourceType asset resourceType.
    */
   public void updateResourcePermissions(String oldPath, String newPath, ResourceType resourceType)
   {
      final AuthorizationProvider provider = securityProvider.getAuthorizationProvider();

      if(provider instanceof VirtualAuthorizationProvider) {
         return;
      }

      Permission permission = provider.getPermission(resourceType, oldPath);

      if(permission != null) {
         securityProvider.setPermission(ResourceType.ASSET, newPath, permission);
         securityProvider.removePermission(ResourceType.ASSET, oldPath);
      }
   }

   private void setResourcePermissions0(String path, ResourceType resourceType,
                                        ResourcePermissionModel tableModel,
                                        Principal principal)
      throws IOException
   {
      setResourcePermissions0(path, resourceType, tableModel, principal, false);
   }

   private void setResourcePermissions0(String path, ResourceType resourceType,
                                        ResourcePermissionModel tableModel,
                                        Principal principal, boolean tableStyleFolder)
      throws IOException
   {
      final AuthorizationProvider provider = securityProvider.getAuthorizationProvider();

      if(provider instanceof VirtualAuthorizationProvider || tableModel == null) {
         return;
      }

      if(tableModel.grantReadToAllVisible()) {
         switch(resourceType) {
         case DATA_SOURCE_FOLDER:
            if("/".equals(path)) {
               SreeEnv.setProperty("security.datasource.everyone",
                                   Boolean.toString(tableModel.grantReadToAll()));
               SecurityEngine.updateSecurityDatasourceEveryoneValue();
            }

            break;
         case TABLE_STYLE_LIBRARY:
            SreeEnv.setProperty("security.tablestyle.everyone",
                                Boolean.toString(tableModel.grantReadToAll()));
            SecurityEngine.updateSecurityTablestyleEveryoneValue();
            break;
         case SCRIPT_LIBRARY:
            SreeEnv.setProperty("security.script.everyone",
                                Boolean.toString(tableModel.grantReadToAll()));
            SecurityEngine.updateSecurityScriptEveryoneValue();
            break;
         case SCHEDULE_TASK_FOLDER:
            if("/".equals(path)) {
               SreeEnv.setProperty("security.scheduletask.everyone",
                  Boolean.toString(tableModel.grantReadToAll()));
               SecurityEngine.updateSecuritySchduletaskEveryoneValue();
            }

            break;
         }
      }

      String resourcePath = getPermissionResourcePath(path, resourceType, tableStyleFolder);
      SreeEnv.setProperty("permission.andCondition", String.valueOf(tableModel.requiresBoth()), true);
      SreeEnv.save();

      Permission permission = provider.getPermission(resourceType, resourcePath);
      String orgID = OrganizationManager.getInstance().getCurrentOrgID();
      boolean siteAdmin = OrganizationManager.getInstance().isSiteAdmin(principal);
      //String orgName = XUtil.getCurrentOrgName();

      if(tableModel.permissions() == null) {
         if(permission == null) {
            return;
         }

         //set all grants for this organization to null
         String orgId = OrganizationManager.getInstance().getCurrentOrgID();

         for(ResourceAction action : ResourceAction.values()) {
            for(Identity.Type identityType : Identity.Type.values()) {
               // non site admin should not clear global role permission.
               if(identityType == Identity.Type.ROLE && siteAdmin) {
                  permission.setGrantsOrgScoped(action, identityType.code(),
                     Collections.emptySet(), null);
               }

               permission.setGrantsOrgScoped(action, identityType.code(), Collections.emptySet(), orgId);
            }
         }

         permission.updateGrantAllByOrg(orgId, tableModel.hasOrgEdited());
         provider.setPermission(resourceType, resourcePath, permission);
         return;
      }

      for(ResourceAction action : tableModel.displayActions()) {
         Set<String> userGrants = new HashSet<>();
         Set<String> groupGrants = new HashSet<>();
         Set<String> roleGrants = new HashSet<>();
         Set<String> globalRoleGrants = new HashSet<>();
         Set<String> organizationGrants = new HashSet<>();

         List<ResourcePermissionTableModel> permissions =
            Objects.requireNonNull(tableModel.permissions());

         if(permission != null) {
            permission.getOrgScopedUserGrants(action, OrganizationManager.getInstance().getCurrentOrgID()).stream()
               .filter(u -> !isIdentityAuthorized(u, Identity.Type.USER, principal))
               .forEach(uid -> userGrants.add(uid.name));
            permission.getOrgScopedGroupGrants(action, OrganizationManager.getInstance().getCurrentOrgID()).stream()
               .filter(u -> !isIdentityAuthorized(u, Identity.Type.GROUP, principal))
               .forEach(gid -> groupGrants.add(gid.name));
            permission.getOrgScopedRoleGrants(action, OrganizationManager.getInstance().getCurrentOrgID()).stream()
               .filter(u -> !isIdentityAuthorized(u, Identity.Type.ROLE, principal))
               .forEach(rid -> roleGrants.add(rid.name));
            permission.getOrgScopedOrganizationGrants(action, OrganizationManager.getInstance().getCurrentOrgID()).stream()
               .filter(u -> !isIdentityAuthorized(u, Identity.Type.ORGANIZATION, principal))
               .forEach(oid -> organizationGrants.add(oid.name));
         }
         else {
            permission = new Permission();
         }

         for(ResourcePermissionTableModel permissionModel : permissions) {
            if(permissionModel.actions().contains(action)) {
               switch(permissionModel.type()) {
               case USER:
                  userGrants.add(permissionModel.identityID().name);
                  break;
               case GROUP:
                  groupGrants.add(permissionModel.identityID().name);
                  break;
               case ROLE:
                  if(permissionModel.identityID().orgID == null) {
                     globalRoleGrants.add(permissionModel.identityID().name);
                  }
                  else {
                     roleGrants.add(permissionModel.identityID().name);
                  }

                  break;
               case ORGANIZATION:
                  organizationGrants.add(permissionModel.identityID().name);
                  break;
               }
            }
         }

         if(!userGrants.isEmpty()) {
            permission.setUserGrantsForOrg(action, userGrants, orgID);
         }
         else {
            permission.setUserGrantsForOrg(action, Collections.emptySet(), orgID);
         }

         if(!groupGrants.isEmpty()) {
            permission.setGroupGrantsForOrg(action, groupGrants, orgID);
         }
         else {
            permission.setGroupGrantsForOrg(action, Collections.emptySet(), orgID);
         }

         if(!roleGrants.isEmpty()) {
            permission.setRoleGrantsForOrg(action, roleGrants, orgID);
         }
         else {
            permission.setRoleGrantsForOrg(action, Collections.emptySet(), orgID);
         }

         if(!globalRoleGrants.isEmpty()) {
            permission.setRoleGrantsForOrg(action, globalRoleGrants, null);
         }
         else if(siteAdmin) {
            permission.setRoleGrantsForOrg(action, Collections.emptySet(), null);
         }

         if(!organizationGrants.isEmpty()) {
            permission.setOrganizationGrantsForOrg(action, organizationGrants, orgID);
         }
         else {
            permission.setOrganizationGrantsForOrg(action, Collections.emptySet(), orgID);
         }
      }

      permission.updateGrantAllByOrg(orgID, tableModel.hasOrgEdited());

      resetAdditionalDatasource(principal, resourcePath, resourceType);
      provider.setPermission(resourceType, resourcePath, permission);
      SecurityEngine.touch();
   }

   /**
    * Reset current additional datasource for current user.
    * @param principal   current login user.
    * @param resourcePath  the target resource path.
    * @param resourceType  the target resource type.
    */
   private void resetAdditionalDatasource(Principal principal, String resourcePath,
                                          ResourceType resourceType)
   {
      if(!(principal instanceof XPrincipal) || resourceType != ResourceType.DATA_SOURCE ||
         resourcePath == null || resourcePath.indexOf("::") == -1)
      {
         return;
      }

      if(securityProvider.checkPermission(
         principal, ResourceType.DATA_SOURCE, resourcePath, ResourceAction.READ))
      {
         ((XPrincipal) principal).setProperty(resourcePath, "r");
      }
      else {
         ((XPrincipal) principal).setProperty(resourcePath, null);
      }
   }

   /**
    * @param repositoryEntryType int representing a RepositoryEntry type
    *
    * @return returns the corresponding ResourceType
    */
   Resource getRepositoryResourceType(int repositoryEntryType, String path) {
      final ResourceType type;
      String resourcePath = path;

      switch(repositoryEntryType) {
      case RepositoryEntry.TRASHCAN:
      case RepositoryEntry.VIEWSHEET:
      case RepositoryEntry.AUTO_SAVE_VS:
      case RepositoryEntry.REPOSITORY | RepositoryEntry.FOLDER:
      case RepositoryEntry.FOLDER:
         type = ResourceType.REPORT;
         break;
      case RepositoryEntry.DATA_SOURCE:
         type = ResourceType.DATA_SOURCE;
         resourcePath = getDataSourceResourceName(resourcePath);
         break;
      case RepositoryEntry.PARTITION:
      case RepositoryEntry.PARTITION | RepositoryEntry.FOLDER:
         return getPartitionResource(resourcePath);
      case RepositoryEntry.VPM:
      case RepositoryEntry.DATA_SOURCE | RepositoryEntry.FOLDER:
         type = ResourceType.DATA_SOURCE;
         break;
      case RepositoryEntry.DATA_SOURCE_FOLDER:
         type = ResourceType.DATA_SOURCE_FOLDER;
         break;
      case RepositoryEntry.QUERY:
         type = ResourceType.QUERY;
         break;
      case RepositoryEntry.LOGIC_MODEL:
      case RepositoryEntry.LOGIC_MODEL | RepositoryEntry.FOLDER:
         return getLogicalModelResourceName(resourcePath);
      case RepositoryEntry.QUERY | RepositoryEntry.FOLDER:
         type = ResourceType.QUERY_FOLDER;
         break;
      case RepositoryEntry.DATA_MODEL | RepositoryEntry.FOLDER:
         type = ResourceType.DATA_MODEL_FOLDER;
         break;
      case RepositoryEntry.SCRIPT:
         type = ResourceType.SCRIPT;
         break;
      case RepositoryEntry.SCRIPT | RepositoryEntry.FOLDER:
         type = ResourceType.SCRIPT_LIBRARY;
         break;
      case RepositoryEntry.TABLE_STYLE:
         type = ResourceType.TABLE_STYLE;
         break;
      case RepositoryEntry.TABLE_STYLE | RepositoryEntry.FOLDER:
         type = "*".equals(path) ? ResourceType.TABLE_STYLE_LIBRARY : ResourceType.TABLE_STYLE;
         break;
      case RepositoryEntry.WORKSHEET:
      case RepositoryEntry.AUTO_SAVE_WS:
      case RepositoryEntry.WORKSHEET_FOLDER:
         type = ResourceType.ASSET;
         break;
      case RepositoryEntry.LIBRARY_FOLDER:
         type = ResourceType.LIBRARY;
         resourcePath = "*";
         break;
      case RepositoryEntry.PROTOTYPE:
         type = ResourceType.PROTOTYPE;
         break;
      case RepositoryEntry.PROTOTYPE | RepositoryEntry.FOLDER:
         type = ResourceType.PROTOTYPE;
         resourcePath = "*";
         break;
      case RepositoryEntry.TRASHCAN | RepositoryEntry.FOLDER:
      case RepositoryEntry.RECYCLEBIN_FOLDER:
         type = ResourceType.REPORT;
         resourcePath = "/";
         break;
      case RepositoryEntry.DASHBOARD:
      case RepositoryEntry.DASHBOARD_FOLDER:
         type = ResourceType.DASHBOARD;
         break;
      case RepositoryEntry.CUBE:
         type = ResourceType.CUBE;
         resourcePath = getCubeResourceName(path);
         break;
      case RepositoryEntry.SCHEDULE_TASK:
         type = ResourceType.SCHEDULE_TASK;
         break;
      case RepositoryEntry.SCHEDULE_TASK | RepositoryEntry.FOLDER:
         type = ResourceType.SCHEDULE_TASK_FOLDER;
         break;
      default:
         type = null;
      }

      return new Resource(type, resourcePath);
   }

   boolean siteAdminOtherOrg(IdentityID identity, Identity.Type type, Principal principal) {
      //add old permissions back if belonging to another org
      if(!OrganizationManager.getInstance().isSiteAdmin(principal)) {
         return false;
      }

      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();

      String orgId = OrganizationManager.getInstance().getCurrentOrgID();

      switch(type) {
      case USER:
         User user = provider.getUser(identity);
         return !orgId.equals(user.getOrganizationID());
       case GROUP:
          Group group = provider.getGroup(identity);
          return !orgId.equals(group.getOrganizationID());
      case ROLE:
         Role role = provider.getRole(identity);
         return !orgId.equals(role.getOrganizationID());
      case ORGANIZATION:
         return !orgId.equals(identity.orgID);
      default:
         return false;
      }
   }

   boolean isIdentityAuthorized(IdentityID identity, Identity.Type type, Principal principal) {
      final ResourceType resourceType;
      Principal oprincipal = ThreadContext.getContextPrincipal();

      try {
         ThreadContext.setContextPrincipal(principal);
         // Only show users from the same organization and site admins (if permission allows)
         SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
         String orgID = OrganizationManager.getInstance().getCurrentOrgID(principal);
         String org = Arrays.stream(provider.getOrganizationIDs())
            .map(provider::getOrganization)
            .filter((o) -> o.getOrganizationID().equals(orgID))
            .map(Organization::getId)
            .findFirst()
            .orElse("");

         switch(type) {
         case USER:
            User user = provider.getUser(identity);

            if(user != null) {
               IdentityID[] roles = user == null ? new IdentityID[0] : provider.getRoles(user.getIdentityID());
               boolean userIsSiteAdmin = Arrays.stream(provider.getAllRoles(roles))
                  .anyMatch(provider::isSystemAdministratorRole);

               if(userIsSiteAdmin || org.equals(user.getOrganizationID())) {
                  resourceType = ResourceType.SECURITY_USER;
                  break;
               }
            }

            return false;
         case GROUP:
            if(provider.getGroup(identity) != null && org.equals(provider.getGroup(identity).getOrganizationID())) {
               resourceType = ResourceType.SECURITY_GROUP;
               break;
            }

            return false;
         case ROLE:
            Role role = provider.getRole(identity);

            if(role == null) {
               IdentityID globalID = new IdentityID(identity.name, null);
               role = provider.getRole(globalID);
               identity = globalID;
            }

            boolean roleIsSiteAdmin = role != null && Arrays.stream(
                  provider.getAllRoles(new IdentityID[]{ role.getIdentityID() }))
               .anyMatch(provider::isSystemAdministratorRole);
            boolean userIsAdmin = OrganizationManager.getInstance().isSiteAdmin(principal);

            if((!roleIsSiteAdmin || userIsAdmin) || org.equals(role.getOrganizationID())) {
               resourceType = ResourceType.SECURITY_ROLE;
               break;
            }

            return false;
         case ORGANIZATION:
            if(org.equals(identity.orgID)) {
               resourceType = ResourceType.SECURITY_ORGANIZATION;
               break;
            }

            return false;
         default:
            return false;
         }

         return securityProvider.checkPermission(principal, resourceType, identity.convertToKey(),
                                                 ResourceAction.ADMIN);
      }
      finally {
         ThreadContext.setContextPrincipal(oprincipal);
      }
   }

   private List<ResourcePermissionTableModel> getIdentityActions(Permission perm,
                                                                 Identity.Type type,
                                                                 Principal principal)
   {
      Map<IdentityID, EnumSet<ResourceAction>> identities = getActions(perm, type);
      Set<IdentityID> ids = identities.keySet();

      // don't display global role in non site admin permission table.
      if(!OrganizationManager.getInstance().isSiteAdmin(principal)) {
         ids = ids.stream().filter(identityID -> identityID.getOrgID() != null).collect(Collectors.toSet());
      }

      if(!ids.isEmpty()) {
         ids = ids.stream().map(identity -> CompletableFuture.supplyAsync(() ->
               isIdentityAuthorized(identity, type, principal) ? identity : null
            ))
            .map(CompletableFuture::join)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
      }

      Map<IdentityID, EnumSet<ResourceAction>> nidentities = new HashMap<>();
      ids.stream().forEach(id -> nidentities.put(id, identities.get(id)));

      return createModels(nidentities, type);
   }

   /**
    * Get all entities with Identity.Type and a list of the actions they have for
    * a certain resource
    *
    * @param permission The resource permission
    * @param type       The entity type
    **/
   private Map<IdentityID, EnumSet<ResourceAction>> getActions(Permission permission,
                                                           Identity.Type type)
   {
      Map<IdentityID, EnumSet<ResourceAction>> entities = new HashMap<>();

      for(ResourceAction action : ResourceAction.values()) {
         // All entities with specific grant
         Set<IdentityID> entitiesWithGrant = permission.getOrgScopedGrants(action, type.code(), OrganizationManager.getInstance().getCurrentOrgID());

         // Construct map of actions each entity has
         for(IdentityID entity : entitiesWithGrant) {
            entities.computeIfAbsent(entity, e -> EnumSet.noneOf(ResourceAction.class)).add(action);
         }
      }

      return entities;
   }

   private List<ResourcePermissionTableModel> createModels(
      Map<IdentityID, EnumSet<ResourceAction>> entities,
      Identity.Type type)
   {
      return entities.keySet().stream()
         .map(name -> ResourcePermissionTableModel.builder()
            .identityID(name)
            .type(type)
            .actions(entities.get(name))
            .build())
         .collect(Collectors.toList());
   }

//   private String getBaseName(String fullName, Identity.Type type) {
//      String baseName = null;
//
//      if(Identity.Type.GROUP.equals(type)) {
//         Group group = securityProvider.getGroup(fullName);
//
//         if(group != null) {
//            baseName = group.getBaseName();
//         }
//      }
//      else if(Identity.Type.ROLE.equals(type)) {
//         Role role = securityProvider.getRole(fullName);
//
//         if(role != null) {
//            baseName = role.getBaseName();
//         }
//      }
//
//      return baseName == null ? fullName : baseName;
//   }

   private List<IdentityID> getAuthorizedIdentities(Identity.Type type, Principal principal) {
      IdentityID[] identities;

      switch(type) {
      case USER:
         identities = securityProvider.getAuthenticationProvider().getUsers();
         break;
      case GROUP:
         identities = securityProvider.getAuthenticationProvider().getGroups();
         break;
      case ROLE:
         identities = securityProvider.getAuthenticationProvider().getRoles();
         break;
      case ORGANIZATION:
         identities = Arrays.stream(securityProvider.getAuthenticationProvider().getOrganizationIDs())
                            .map(id -> new IdentityID(securityProvider.getAuthenticationProvider().getOrgNameFromID(id), id)).toArray(IdentityID[]::new);
         break;
      default:
         return Collections.emptyList();
      }

      return Arrays.stream(identities)
         .map(identity -> CompletableFuture.supplyAsync(() ->
            isIdentityAuthorized(identity, type, principal) ? identity : null
         ))
         .map(CompletableFuture::join)
         .filter(Objects::nonNull)
         .collect(Collectors.toList());
   }

   private void clearActionGrants(Permission permission, EnumSet<ResourceAction> actions, String orgId) {
      for(ResourceAction action : actions) {
         permission.setUserGrantsForOrg(action, new HashSet<>(), orgId);
         permission.setGroupGrantsForOrg(action, new HashSet<>(), orgId);
         permission.setRoleGrantsForOrg(action, new HashSet<>(), orgId);
         permission.setOrganizationGrantsForOrg(action, new HashSet<>(), orgId);
      }
   }

   public Permission getPermissionFromModel(ResourcePermissionModel permissionModel,
                                            Principal principal)
   {
      if(permissionModel != null) {
         Permission permission = new Permission();
         String orgId = OrganizationManager.getInstance().getCurrentOrgID();
         clearActionGrants(permission, permissionModel.displayActions(), orgId);

         for(ResourceAction action : permissionModel.displayActions()) {
            Objects.requireNonNull(permissionModel.permissions()).stream()
               .filter(model -> model.actions().contains(action))
               .collect(Collectors.groupingBy(ResourcePermissionTableModel::type))
               .forEach((type, models) -> {
                           final Set<String> grants = models.stream()
                              .map(ResourcePermissionTableModel::identityID)
                              .filter(identity -> isIdentityAuthorized(identity, type, principal))
                              .map(id -> id.name)
                              .collect(Collectors.toSet());
                           permission.setGrantsOrgScoped(action, type.code(), grants, orgId);
                        }
               );
         }

         return permission;
      }

      return null;
   }

   public List<ResourcePermissionTableModel> getModelFromPermission(Permission permission,
                                                                    Principal principal)
   {
      List<ResourcePermissionTableModel> permissions = new ArrayList<>();

      if(permission != null) {
         permissions.addAll(getIdentityActions(permission, Identity.Type.USER, principal));
         permissions.addAll(getIdentityActions(permission, Identity.Type.GROUP, principal));
         permissions.addAll(getIdentityActions(permission, Identity.Type.ROLE, principal));
         permissions.addAll(getIdentityActions(permission, Identity.Type.ORGANIZATION, principal));
      }

      return permissions;
   }

   public static String getDataSourceResourceName(String resourcePath) {
      if(resourcePath.contains("/")) {
         // may be additional connection
         for(String ds : DataSourceRegistry.getRegistry().getDataSourceFullNames()) {
            if(resourcePath.startsWith(ds + "/")) {
               resourcePath = ds + "::" + resourcePath.substring(ds.length() + 1);
               break;
            }
         }
      }

      return resourcePath;
   }

   private Resource getPartitionResource(String path) {
      boolean hasFolder = path.indexOf(XUtil.DATAMODEL_FOLDER_SPLITER) != -1;
      String[] strs = Tool.split(path, '^');
      String database = null;
      String folder = null;

      if(strs.length > 0) {
         database = strs[0];
      }

      if(hasFolder && strs.length > 2) {
         folder = strs[2];
      }

      ResourceType type = ResourceType.DATA_SOURCE;
      StringBuffer buffer = new StringBuffer();
      buffer.append(database);

      if(!StringUtils.isEmpty(folder)) {
         buffer.append("/");
         buffer.append(folder);
         type = ResourceType.DATA_MODEL_FOLDER;
      }

      return new Resource(type, buffer.toString());
   }

   public static Resource getLogicalModelResourceName(String path) {
      boolean hasFolder = path.indexOf(XUtil.DATAMODEL_FOLDER_SPLITER) != -1;
      String[] strs = Tool.split(path, '^');

      String database = null;
      String folder = null;
      String logicalName = null;
      String extend = null;

      if(hasFolder) {
         if(strs.length > 0) {
            database = strs[0];
         }

         if(strs.length > 2) {
            folder = strs[2];
         }

         if(strs.length > 3) {
            logicalName = strs[3];
         }

         if(strs.length > 4) {
            extend = strs[4];
         }
      }

      if(!hasFolder) {
         if(strs.length > 0) {
            database = strs[0];
         }

         if(strs.length > 1) {
            logicalName = strs[1];
         }

         if(strs.length > 2) {
            extend = strs[2];
         }
      }

      if("(Default Connection)".equals(extend)) {
         extend = null;
      }

      ResourceType type = null;

      if(!StringUtils.isEmpty(extend)) {
         type = !StringUtils.isEmpty(folder) ?
            ResourceType.DATA_MODEL_FOLDER : ResourceType.DATA_SOURCE;
         path = database + "::" + extend + (folder != null ? "/" + folder : "");
      }
      else {
         type = ResourceType.QUERY;
         path = logicalName + "::" + database +
            (folder != null ? XUtil.DATAMODEL_FOLDER_SPLITER + folder : "");
      }

      return new Resource(type, path);
   }

   private String getCubeResourceName(String path) {
      int index = path.lastIndexOf('/');
      return path.substring(0, index) + "::" + path.substring(index + 1);
   }

   static final EnumSet<ResourceAction> ADMIN_ACTIONS = EnumSet.of(
      ResourceAction.READ, ResourceAction.WRITE, ResourceAction.DELETE, ResourceAction.ADMIN);
   static final EnumSet<ResourceAction> ADMIN_SHARE_ACTIONS = EnumSet.of(
      ResourceAction.READ, ResourceAction.WRITE, ResourceAction.DELETE, ResourceAction.SHARE,
      ResourceAction.ADMIN);

   private final Catalog catalog = Catalog.getCatalog();
   private final SecurityEngine securityEngine;
   private final SecurityProvider securityProvider;
}
