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

import inetsoft.sree.internal.SUtil;
import inetsoft.uql.util.Identity;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.admin.security.action.ActionPermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

public class DefaultCheckPermissionStrategy implements CheckPermissionStrategy {
   public DefaultCheckPermissionStrategy(SecurityProvider provider) {
      this.provider = provider;
   }

   //IdentityID as resource passed as String key
   @Override
   public boolean checkPermission(Principal principal, ResourceType type,
                                  String resource, ResourceAction action)
   {
      Identity identity = SUtil.getExecuteIdentity(principal);
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      IdentityID[] roles = null;
      IdentityID[] groups = null;
      String organization = null;
      PermissionChecker checker = new PermissionChecker(provider);
      IdentityID curOrgID = new IdentityID(OrganizationManager.getCurrentOrgName(),OrganizationManager.getCurrentOrgName());

      //check admin permissions at org level
      if(isSecurityIdentity(type) && isNotGlobalRole(type, IdentityID.getIdentityIDFromKey(resource)) && provider.getPermission(ResourceType.SECURITY_ORGANIZATION, curOrgID) != null) {
         Permission permission =
            provider.getPermission(ResourceType.SECURITY_ORGANIZATION, curOrgID);

         if(checker.checkPermission(identity, permission, ResourceAction.ADMIN, true)) {
            return true;
         }
      }

      if(identity.getType() == Identity.USER) {
         identity = (principal instanceof SRPrincipal) ?
            ((SRPrincipal) principal).createUser() : null;
         identity = identity == null ? provider.getUser(pId) : identity;

         if(identity != null) {
            roles = identity.getRoles();
            String identityOrg = identity.getOrganization();
            groups = Arrays.stream(identity.getGroups()).map(u -> new IdentityID(u,identityOrg)).toArray(IdentityID[]::new);
            organization = identity.getOrganization();
         }
      }
      else if(identity.getType() == Identity.GROUP) {
         identity = provider.getGroup(pId);

         if(identity != null) {
            roles = identity.getRoles();
            String identityOrg = identity.getOrganization();
            groups = Arrays.stream(identity.getGroups()).map(u -> new IdentityID(u,identityOrg)).toArray(IdentityID[]::new);
            organization = identity.getOrganization();
         }
      }
      else if(identity.getType() == Identity.ROLE) {
         identity = provider.getRole(pId);

         if(identity != null) {
            roles = new IdentityID[]{identity.getIdentityID()};
         }
      }
      else if(identity.getType() == Identity.ORGANIZATION) {
         identity = provider.getOrganization(pId.name);
         if(identity != null) {
            organization = identity.getName();
         }
      }
      else {
         identity = null;
         roles = provider.getRoles(pId);
         groups = Arrays.stream(provider.getUserGroups(pId)).map(u -> new IdentityID(u,pId.organization)).toArray(IdentityID[]::new);
         organization = provider.getOrganization(pId.name).name;
      }

      //return true if admin permissions over root role
      if(type.equals(ResourceType.SECURITY_ROLE)) {
         String rootRole = Organization.getRootRoleName(principal);
         String rootOrgRole = Organization.getRootOrgRoleName(principal);
         Role role = provider.getRole(IdentityID.getIdentityIDFromKey(resource));
         Permission orgRoleRootPer;

         if(role != null && role.getOrganization() != null) {
            orgRoleRootPer = provider.getPermission(type, rootOrgRole);
         }
         else {
            orgRoleRootPer = provider.getPermission(type, rootRole);
         }

         if(orgRoleRootPer != null && (role == null ||
            Tool.equals(role.getOrganization(), OrganizationManager.getCurrentOrgName())) &&
            checker.checkPermission(identity, orgRoleRootPer, action, true))
         {
            return true;
         }
      }
      else if(type.equals(ResourceType.SECURITY_GROUP)) {
         IdentityID rootGroup = new IdentityID(Catalog.getCatalog(principal).getString("Groups"), OrganizationManager.getCurrentOrgName());
         Permission rootGroupPerm = provider.getPermission(type, rootGroup);

         if(rootGroupPerm != null && checker.checkPermission(identity, rootGroupPerm, action, true))
         {
            return true;
         }
      }
      //return true if admin permissions over root role
      else if(type.equals(ResourceType.SECURITY_USER)) {
         IdentityID rootUser = new IdentityID(Catalog.getCatalog(principal).getString("Users"), OrganizationManager.getCurrentOrgName());
         Permission rootUserPerm = provider.getPermission(type, rootUser);

         if(rootUserPerm != null && checker.checkPermission(identity, rootUserPerm, action, true))
         {
            return true;
         }

         // if requested user is member of a group and group root has admin, allow access
         User resourceUser = provider.getUser((IdentityID.getIdentityIDFromKey(resource)));
         String[] userGroups = resourceUser == null ? null : resourceUser.getGroups();
          if(userGroups != null && userGroups.length > 0) {
            IdentityID rootGroup = new IdentityID(Catalog.getCatalog(principal).getString("Groups"), OrganizationManager.getCurrentOrgName());
            Permission rootGroupPerm = provider.getPermission(ResourceType.SECURITY_GROUP, rootGroup);

            if(rootGroupPerm != null && checker.checkPermission(identity, rootGroupPerm, action, true))
            {
               return true;
            }
         }
      }

      // Bug #40590, always check permission for additional connection (backward
      // compatibility)
      if((roles != null || groups != null || organization != null) &&
         !(action == ResourceAction.READ && (type == ResourceType.DATA_SOURCE ||
            type == ResourceType.DATA_SOURCE_FOLDER) && resource.contains("::")))
      {
         final HashSet<IdentityID> baseRoles = new HashSet<>();

         if(roles != null) {
            baseRoles.addAll(Arrays.asList(roles));
         }

         //check base roles of assigned Organization
         if(identity.getOrganization() != null && provider.getOrganization(identity.getOrganization()) != null) {
            IdentityID[] orgRoles = provider.getOrganization(identity.getOrganization()).getRoles();

            if(orgRoles != null) {
               baseRoles.addAll(Arrays.asList(orgRoles));
            }
         }

         if(groups != null) {
            Arrays.stream(provider.getAllGroups(groups))
               .map(name -> provider.getGroup(name))
               .filter(Objects::nonNull)
               .flatMap(g -> Arrays.stream(g.getRoles()))
               .forEach(baseRoles::add);
         }

         final boolean isSysAdmin = Arrays
            .stream(provider.getAllRoles(baseRoles.toArray(new IdentityID[0])))
            .anyMatch(provider::isSystemAdministratorRole);

         if(isSysAdmin || OrganizationManager.getInstance().isSiteAdmin(principal)) {
            return true;
         }

         //if admin permissions to this resource, return true
         boolean hasResourcePermission = provider.getPermission(type, resource) != null &&
            provider.getPermission(type, resource)
               .getOrgScopedUserGrants(ResourceAction.ASSIGN, OrganizationManager.getInstance().getCurrentOrgID())
               .contains(pId);

         if(hasResourcePermission) {
            return true;
         }

         if(checkOrgAdminPermission(type, resource, organization, baseRoles, principal)) {
            return true;
         }
      }

      if(identity == null) {
         return false;
      }

      Permission perm = getPermission(type, resource, action);
      String orgID = OrganizationManager.getInstance().getCurrentOrgID(principal);

      boolean useParent = (perm == null) || (!perm.hasOrgEditedGrantAll(orgID));
      boolean inheritedPermission = useParent || isSecurityIdentity(type);

      if(!ActionPermissionService.isOrgAdminAction(type, resource) && SUtil.isMultiTenant()) {
         return false;
      }

      // has organization user grants but has no organization grants, no need to check parent.
      if(perm != null && checker.checkPermission(identity, perm, action, true)) {
         return true;
      }

      if(useParent) {
         // when no permission defined for my report, the default value is true
         if(type == ResourceType.MY_DASHBOARDS ||
            type == ResourceType.REPORT && Tool.MY_DASHBOARD.equals(resource))
         {
            return true;
         }

         if(type == ResourceType.VIEWSHEET_TOOLBAR_ACTION ||
            type == ResourceType.REPORT_EXPORT ||
            type == ResourceType.VIEWSHEET_ACTION ||
            type == ResourceType.SHARE ||
            type == ResourceType.PORTAL_TAB && "Design".equals(resource) ||
            type == ResourceType.PORTAL_TAB && "Report".equals(resource) ||
            type == ResourceType.PORTAL_TAB && "Schedule".equals(resource) ||
            type == ResourceType.PORTAL_TAB && "Dashboard".equals(resource) ||
            type == ResourceType.SCHEDULE_OPTION ||
            type == ResourceType.PORTAL_REPOSITORY_TREE_DRAG_AND_DROP ||
            type == ResourceType.MATERIALIZATION ||
            type == ResourceType.CHART_TYPE_FOLDER)
         {
            return true;
         }

         LOG.debug("No permissions found for " + resource);
      }

      while(type.isHierarchical() && useParent && isActualResource(resource, type)) {
         Resource parent = type.getParent(resource);

         if(parent != null) {
            perm = provider.getPermission(parent.getType(), parent.getPath());
            useParent = (perm == null) ||  !perm.hasOrgEditedGrantAll(orgID);
            resource = parent.getPath();
            type = parent.getType();

            if(type == ResourceType.REPORT && Tool.MY_DASHBOARD.equals(resource)) {
               return true;
            }

            LOG.debug("Retrieve parent permission [{}]: {}", parent, perm);
         }
         else {
            parent = type.getRoot();

            if(parent != null) {
               perm = provider.getPermission(parent.getType(), parent.getPath());
            }

            if(perm == null) {
               return false;
            }

            break;
         }
      }

      if(inheritedPermission && isSecurityIdentity(type) && type != ResourceType.SECURITY_ROLE) {
         if(perm != null && checker.checkPermission(identity, perm, action, true)) {
            return true;
         }
      }

      if(type == ResourceType.SECURITY_ROLE) {
         Permission rolePerm = provider.getPermission(type, new IdentityID(Catalog.getCatalog().getString("Organization Roles"), organization));

         if(rolePerm != null && checker.checkPermission(identity, rolePerm, action, true)) {
            return true;
         }
      }

      if(inheritedPermission &&
         (type == ResourceType.SECURITY_GROUP || type == ResourceType.SECURITY_USER))
      {
         // we technically support membership in multiple groups, so we need to treat this as graph
         // traversal instead of walking up a tree path
         Set<Resource> visited = new HashSet<>();
         Deque<Resource> queue = new ArrayDeque<>();
         getSecurityResourceParents(new Resource(type, resource), organization).forEach(queue::addLast);

         while(!queue.isEmpty()) {
            Resource current = queue.removeFirst();
            perm = provider.getPermission(current.getType(), current.getPath());
            useParent = (perm == null) || !perm.hasOrgEditedGrantAll(orgID);

            if(perm != null && checker.checkPermission(identity, perm, action, true)) {
               return true;
            }

            if(!useParent) {
               break;
            }

            visited.add(current);
            getSecurityResourceParents(current, organization).stream()
               .filter(r -> !visited.contains(r))
               .forEach(queue::addLast);
         }
      }

      if(inheritedPermission) {
         if(type == ResourceType.SECURITY_USER) {
            // check for the user/role wildcard
            perm = provider.getPermission(type, orgID);

            if(perm != null && checker.checkPermission(identity, perm, action, true)) {
               return true;
            }
         }

         if(type == ResourceType.SECURITY_USER ||
            type == ResourceType.SECURITY_GROUP ||
            type == ResourceType.SECURITY_ROLE)
         {
            if((perm == null) || !perm.hasOrgEditedGrantAll(orgID) || type == ResourceType.SECURITY_ROLE) {
               Permission orgPerm = provider.getPermission(ResourceType.SECURITY_ORGANIZATION, new IdentityID(organization, organization));

               if(orgPerm != null && checker.checkPermission(identity, orgPerm, ResourceAction.ADMIN, true)) {
                  return true;
               }
            }
         }
      }

      if(perm == null) {
         return false;
      }

      if(inheritedPermission) {
         // when permissions are inherited from the parent, delete permission
         // should be given to all entities that have write or delete permission
         // on the parent

         perm = (Permission) perm.clone();

         Set<IdentityID> deleteIdentities = perm.getOrgScopedGroupGrants(ResourceAction.DELETE, orgID);
         Set<IdentityID> writeIdentities = perm.getOrgScopedGroupGrants(ResourceAction.WRITE, orgID);
         Set<IdentityID> newIdentities = new HashSet<>();

         newIdentities.addAll(deleteIdentities);
         newIdentities.addAll(writeIdentities);

         if(!newIdentities.isEmpty()) {
            perm.setGroupGrantsForOrg(ResourceAction.DELETE, newIdentities.stream()
                                 .map(id -> id.name).collect(Collectors.toSet()), orgID);
         }

         deleteIdentities = perm.getOrgScopedUserGrants(ResourceAction.DELETE, orgID);
         writeIdentities = perm.getOrgScopedUserGrants(ResourceAction.WRITE, orgID);
         newIdentities.clear();

         newIdentities.addAll(deleteIdentities);
         newIdentities.addAll(writeIdentities);

         if(!newIdentities.isEmpty()) {
            perm.setUserGrantsForOrg(ResourceAction.DELETE, newIdentities.stream()
                           .map(id -> id.name).collect(Collectors.toSet()), orgID);
         }

         deleteIdentities = perm.getOrgScopedRoleGrants(ResourceAction.DELETE, orgID);
         writeIdentities = perm.getOrgScopedRoleGrants(ResourceAction.WRITE, orgID);
         newIdentities.clear();

         newIdentities.addAll(deleteIdentities);
         newIdentities.addAll(writeIdentities);

         if(!newIdentities.isEmpty()) {
            perm.setRoleGrantsForOrg(ResourceAction.DELETE, newIdentities.stream()
                           .map(id -> id.name).collect(Collectors.toSet()), orgID);
         }

         deleteIdentities = perm.getOrgScopedOrganizationGrants(ResourceAction.DELETE, orgID);
         writeIdentities = perm.getOrgScopedOrganizationGrants(ResourceAction.WRITE, orgID);
         newIdentities.clear();

         newIdentities.addAll(deleteIdentities);
         newIdentities.addAll(writeIdentities);

         if(!newIdentities.isEmpty()) {
            perm.setOrganizationGrantsForOrg(ResourceAction.DELETE, newIdentities.stream()
                           .map(id -> id.name).collect(Collectors.toSet()), orgID);
         }
      }

      return checker.checkPermission(identity, perm, action, true);
   }

   private boolean isNotGlobalRole(ResourceType type, IdentityID name) {
      return !type.equals(ResourceType.SECURITY_ROLE) || provider.getRole(name) == null ||
             (provider.getRole(name).getOrganization() != null &&
             !provider.getRole(name).getOrganization().isEmpty());
   }

   private boolean isSecurityIdentity(ResourceType type) {
      return type.equals(ResourceType.SECURITY_USER) ||
         type.equals(ResourceType.SECURITY_GROUP) ||
         type.equals(ResourceType.SECURITY_ROLE) ||
         type.equals(ResourceType.SECURITY_ORGANIZATION);
   }

   private boolean checkOrgAdminPermission(ResourceType type, String resource, String organization,
                                           HashSet<IdentityID> baseRoles, Principal principal)
   {
      AuthenticationProvider currProvider =
         !(principal instanceof SRPrincipal) || SUtil.isInternalUser((SRPrincipal) principal) ?
            getCurrentProvider(principal) : provider;
      currProvider = currProvider == null ? provider : currProvider;
      boolean isSiteAdmin;
      final boolean isOrgAdmin = Arrays
         .stream(currProvider.getAllRoles(baseRoles.toArray(new IdentityID[0])))
         .anyMatch(currProvider::isOrgAdministratorRole);
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      IdentityID resourceID = IdentityID.getIdentityIDFromKey(resource);
      String orgId = currProvider.getOrgId(organization);
      IdentityID orgIdentityID = new IdentityID(organization, organization);
      Permission orgPermissions = provider.getPermission(ResourceType.SECURITY_ORGANIZATION, orgIdentityID);
      final boolean hasOrgAdminPermission = organization != null &&
            orgPermissions != null && orgPermissions.getOrgScopedUserGrants(ResourceAction.ADMIN, orgId) != null &&
            orgPermissions.getOrgScopedUserGrants(ResourceAction.ADMIN, orgId).contains(pId);

      if(!isOrgAdmin && !hasOrgAdminPermission) {
         return false;
      }

      // Org Admin has permission over all identities within an organization except site admins
      switch(type) {
      case SECURITY_USER:
         if(resource.equals(new IdentityID("*", organization).convertToKey()) ||
            new IdentityID(Catalog.getCatalog(principal).getString("Users"), organization).convertToKey().equals(resource))
         {
            return true;
         }

         User user = currProvider.getUser(resourceID);

         // Bug #66393, SSO user do not in provider, so just equals org name.
         if(user == null) {
            return Objects.equals(resourceID.getOrganization(), organization);
         }

         IdentityID[] userRoles = currProvider.getRoles(user.getIdentityID());
         isSiteAdmin = Arrays.stream(currProvider.getAllRoles(userRoles))
            .anyMatch(currProvider::isSystemAdministratorRole);

         return !isSiteAdmin && organization.equals(currProvider.getUser(resourceID).getOrganization());
      case SECURITY_GROUP:
         if(resource.equals(new IdentityID("*",organization).convertToKey())) {
            return false;
         }

         String groupRoot =
            Catalog.getCatalog(principal).getString("Groups");

         if(resource.equals(new IdentityID(groupRoot, organization).convertToKey())) {
            return true;
         }

         IdentityID[] groupRoles = currProvider.getGroup(resourceID) != null ?
            currProvider.getGroup(resourceID).getRoles() : new IdentityID[0];
         isSiteAdmin = Arrays.stream(currProvider.getAllRoles(groupRoles))
            .anyMatch(currProvider::isSystemAdministratorRole);

         return !isSiteAdmin && currProvider.getGroup(resourceID) != null &&
            organization.equals(currProvider.getGroup(resourceID).getOrganization());
      case SECURITY_ROLE:
         String roleRoot = new IdentityID(Catalog.getCatalog(principal).getString("Roles"), organization).convertToKey();
         String roleOrgRoot = new IdentityID(Catalog.getCatalog(principal).getString("Organization Roles"),organization).convertToKey();
         IdentityID identityID = new IdentityID("*", organization);

         if(resource.equals(roleRoot) || resource.equals(roleOrgRoot) ||
            identityID.convertToKey().equals(resource))
         {
            return true;
         }

         Role role = currProvider.getRole(resourceID);

         if(role == null) {
            return false;
         }

         IdentityID[] roles = new IdentityID[]{role.getIdentityID()};
         isSiteAdmin = Arrays.stream(currProvider.getAllRoles(roles))
            .anyMatch(currProvider::isSystemAdministratorRole);

         return !isSiteAdmin && (currProvider.getRole(resourceID).getOrganization() == null ||
                 organization.equals(currProvider.getRole(resourceID).getOrganization()));
      case SECURITY_ORGANIZATION:
         if(resource.equals("*")) {
            return false;
         }

         return Tool.equals(organization, resource) || new IdentityID(organization, organization).convertToKey().equals(resource);
      default:
         return isOrgAdmin && ActionPermissionService.isOrgAdminAction(type, resource);
      }
   }

   private AuthenticationProvider getCurrentProvider(Principal principal) {
      if(provider.getAuthenticationProvider() instanceof AuthenticationChain) {
         AuthenticationChain chain = (AuthenticationChain) provider.getAuthenticationProvider();
         Optional<AuthenticationProvider> currProvider = chain.getProviders()
            .stream()
            .filter(p -> p.getUser(IdentityID.getIdentityIDFromKey(principal.getName())) != null)
            .findFirst();

         if(currProvider.isPresent()) {
            return currProvider.get();
         }
      }

      return null;
   }

   private Permission getPermission(final ResourceType type, final String resource,
                                    final ResourceAction action)
   {
      String orgId = OrganizationManager.getInstance().getCurrentOrgID();

      if(action == ResourceAction.ADMIN) {
         // admin permissions are cumulative on the entire resource path
         ResourceType currentType = type;
         String currentResource = resource;

         Set<IdentityID> users = new HashSet<>();
         Set<IdentityID> roles = new HashSet<>();
         Set<IdentityID> groups = new HashSet<>();
         Set<IdentityID> organizations = new HashSet<>();
         Map<String, Boolean> orgUpdatedList = new HashMap<>();

         Permission perm = provider.getPermission(type, currentResource);

         if(perm != null) {
            users.addAll(perm.getOrgScopedUserGrants(action, orgId));
            roles.addAll(perm.getOrgScopedRoleGrants(action, orgId));
            groups.addAll(perm.getOrgScopedGroupGrants(action, orgId));
            organizations.addAll(perm.getOrgScopedOrganizationGrants(action, orgId));
            orgUpdatedList.put(orgId, perm.hasOrgEditedGrantAll(orgId));
         }

         Resource parent = currentType.getParent(currentResource);

         while(currentType.isHierarchical() && isActualResource(currentResource, currentType)) {
            if(parent != null) {
               perm = provider.getPermission(parent.getType(), parent.getPath());
               currentResource = parent.getPath();
               currentType = parent.getType();
               parent = currentType.getParent(currentResource); //Check next parent early

               if(perm != null) {
                  users.addAll(perm.getOrgScopedUserGrants(action, orgId));
                  roles.addAll(perm.getOrgScopedRoleGrants(action, orgId));
                  groups.addAll(perm.getOrgScopedGroupGrants(action, orgId));
                  organizations.addAll(perm.getOrgScopedOrganizationGrants(action, orgId));
                  orgUpdatedList.put(orgId, perm.hasOrgEditedGrantAll(orgId));
               }

               if(parent != null && parent.getType() == ResourceType.LIBRARY) {
                  perm = provider.getPermission(parent.getType(), parent.getPath());
                  currentResource = parent.getPath();
                  currentType = parent.getType();

                  if(perm != null) {
                     users.addAll(perm.getOrgScopedUserGrants(action, orgId));
                     roles.addAll(perm.getOrgScopedRoleGrants(action, orgId));
                     groups.addAll(perm.getOrgScopedGroupGrants(action, orgId));
                     organizations.addAll(perm.getOrgScopedOrganizationGrants(action, orgId));
                     orgUpdatedList.put(orgId, perm.hasOrgEditedGrantAll(orgId));
                  }
               }

               if(currentType == ResourceType.REPORT && Tool.MY_DASHBOARD.equals(currentResource)) {
                  break;
               }
            }
            else {
               parent = currentType.getRoot();

               if(parent != null) {
                  perm = provider.getPermission(parent.getType(), parent.getPath());

                  if(perm != null) {
                     users.addAll(perm.getOrgScopedUserGrants(action, orgId));
                     roles.addAll(perm.getOrgScopedRoleGrants(action, orgId));
                     groups.addAll(perm.getOrgScopedGroupGrants(action, orgId));
                     organizations.addAll(perm.getOrgScopedOrganizationGrants(action, orgId));
                     orgUpdatedList.put(orgId, perm.hasOrgEditedGrantAll(orgId));
                  }
               }

               break;
            }
         }

         if(currentType == ResourceType.SECURITY_GROUP || currentType == ResourceType.SECURITY_USER) {
            // we technically support membership in multiple groups, so we need to treat this as graph
            // traversal instead of walking up a tree path

            Set<Resource> visited = new HashSet<>();
            Deque<Resource> queue = new ArrayDeque<>();
            getSecurityResourceParents(new Resource(currentType, currentResource)).forEach(queue::addLast);

            while(!queue.isEmpty()) {
               Resource current = queue.removeFirst();
               perm = provider.getPermission(current.getType(), current.getPath());

               if(perm != null) {
                  users.addAll(perm.getOrgScopedUserGrants(action, orgId));
                  roles.addAll(perm.getOrgScopedRoleGrants(action, orgId));
                  groups.addAll(perm.getOrgScopedGroupGrants(action, orgId));
                  organizations.addAll(perm.getOrgScopedOrganizationGrants(action, orgId));
               }

               visited.add(current);
               getSecurityResourceParents(current).stream()
                  .filter(r -> !visited.contains(r))
                  .forEach(queue::addLast);
            }
         }

         if(currentType == ResourceType.SECURITY_USER) {
            // check for the user/role wildcard
            perm = provider.getPermission(currentType, new IdentityID(Catalog.getCatalog().getString("Users"), OrganizationManager.getCurrentOrgName()));

            if(perm != null) {
               users.addAll(perm.getOrgScopedUserGrants(action, orgId));
               roles.addAll(perm.getOrgScopedRoleGrants(action, orgId));
               groups.addAll(perm.getOrgScopedGroupGrants(action, orgId));
               organizations.addAll(perm.getOrgScopedOrganizationGrants(action, orgId));
            }
         }
         else if(currentType == ResourceType.SECURITY_ROLE) {

            perm = provider.getPermission(currentType, new IdentityID(Catalog.getCatalog().getString("Organization Roles"), OrganizationManager.getCurrentOrgName()));

            if(perm != null) {
               users.addAll(perm.getOrgScopedUserGrants(action, orgId));
               roles.addAll(perm.getOrgScopedRoleGrants(action, orgId));
               groups.addAll(perm.getOrgScopedGroupGrants(action, orgId));
               organizations.addAll(perm.getOrgScopedOrganizationGrants(action, orgId));
            }

            perm = provider.getPermission(currentType, new IdentityID(Catalog.getCatalog().getString("Roles"), OrganizationManager.getCurrentOrgName()));

            if(perm != null) {
               users.addAll(perm.getOrgScopedUserGrants(action, orgId));
               roles.addAll(perm.getOrgScopedRoleGrants(action, orgId));
               groups.addAll(perm.getOrgScopedGroupGrants(action, orgId));
               organizations.addAll(perm.getOrgScopedOrganizationGrants(action, orgId));
            }
         }

         Permission permission = new Permission();
         permission.setUserGrantsForOrg(action, users.stream().map(id-> id.name).collect(Collectors.toSet()), orgId);
         permission.setRoleGrantsForOrg(action, roles.stream().map(id-> id.name).collect(Collectors.toSet()), orgId);
         permission.setGroupGrantsForOrg(action, groups.stream().map(id-> id.name).collect(Collectors.toSet()), orgId);
         permission.setOrganizationGrantsForOrg(action, organizations.stream().map(id-> id.name).collect(Collectors.toSet()), orgId);
         permission.setOrgEditedGrantAll(orgUpdatedList);
         return permission;
      }
      else {
         return provider.getPermission(type, resource);
      }
   }

   private boolean isActualResource(String currentResource, ResourceType resourceType) {
      if(isSecurityIdentity(resourceType) && resourceType != ResourceType.SECURITY_ORGANIZATION) {
         String rootIDKey = new IdentityID("*", OrganizationManager.getCurrentOrgName()).convertToKey();
         return !"/".equals(currentResource) && !rootIDKey.equals(currentResource) ||
            (rootIDKey.equals(currentResource) && (ResourceType.SCRIPT_LIBRARY == resourceType) ||
               ResourceType.TABLE_STYLE_LIBRARY == resourceType);
      }
      else {
         return !"/".equals(currentResource) && !"*".equals(currentResource) ||
            ("*".equals(currentResource) && (ResourceType.SCRIPT_LIBRARY == resourceType) ||
               ResourceType.TABLE_STYLE_LIBRARY == resourceType);
      }
   }

   private Set<Resource> getSecurityResourceParents(Resource resource) {
      return getSecurityResourceParents(resource, null);
   }

   private Set<Resource> getSecurityResourceParents(Resource resource, String org) {
      String[] groups;
      IdentityID resourceID = IdentityID.getIdentityIDFromKey(resource.getPath());
      Catalog catalog = Catalog.getCatalog();

      if(resource.getType() == ResourceType.SECURITY_USER &&
         !new IdentityID(catalog.getString("Users"), org).convertToKey().equals(resource.getPath()))
      {
         groups = provider.getUserGroups(resourceID);
      }
      else if(resource.getType() == ResourceType.SECURITY_GROUP &&
         !new IdentityID(catalog.getString("Groups"), org).convertToKey().equals(resource.getPath()))
      {
         groups = provider.getGroupParentGroups(resourceID);
      }
      else if((resource.getType() == ResourceType.SECURITY_GROUP ||
         resource.getType() == ResourceType.SECURITY_USER) && new IdentityID(catalog.getString("Groups"), org).convertToKey().equals(resource.getPath()) &&
         !Tool.isEmptyString(org))
      {
         return Collections.singleton(new Resource(ResourceType.SECURITY_ORGANIZATION, org));
      }
      else {
         groups = null;
      }

      if(groups == null) {
         return Collections.emptySet();
      }
      else if(groups.length == 0 && resource.getType() == ResourceType.SECURITY_GROUP &&
         !new IdentityID(catalog.getString("Groups"), org).convertToKey().equals(resource.getPath()))
      {
         return Collections.singleton(new Resource(ResourceType.SECURITY_GROUP, new IdentityID(catalog.getString("Groups"), org).convertToKey()));
      }
      else if(groups.length == 0 && resource.getType() == ResourceType.SECURITY_USER &&
         !Tool.isEmptyString(org))
      {
         return Collections.singleton(new Resource(ResourceType.SECURITY_ORGANIZATION, org));
      }
      else {
         return Arrays.stream(groups)
            .map(name -> new IdentityID(name, resourceID.organization))
            .map(g -> new Resource(ResourceType.SECURITY_GROUP, g.convertToKey()))
            .collect(Collectors.toSet());
      }
   }

   private final SecurityProvider provider;
   private final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
