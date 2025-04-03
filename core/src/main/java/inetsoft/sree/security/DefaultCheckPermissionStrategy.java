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

import inetsoft.sree.internal.SUtil;
import inetsoft.uql.XPrincipal;
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
      if(isAllowedDefaultGlobalVSAction(principal, action, type, resource)) {
         return true;
      }

      Identity identity = SUtil.getExecuteIdentity(principal);
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      IdentityID[] roles = null;
      IdentityID[] groups = null;
      String organization = null;
      PermissionChecker checker = new PermissionChecker(provider);
      IdentityID curOrgID = new IdentityID(OrganizationManager.getCurrentOrgName(),
                                           OrganizationManager.getInstance().getCurrentOrgID());

      //check admin permissions at org level
      if(isSecurityIdentity(type) && isNotGlobalRole(type, IdentityID.getIdentityIDFromKey(resource)) &&
         provider.getPermission(ResourceType.SECURITY_ORGANIZATION, curOrgID) != null)
      {
         Permission permission =
            provider.getPermission(ResourceType.SECURITY_ORGANIZATION, curOrgID);

         if(checker.checkPermission(identity, permission, ResourceAction.ADMIN, true)) {
            return true;
         }
      }

      if(identity.getType() == Identity.USER) {
         Identity ssoIdentity = principal instanceof SRPrincipal ?
            ((SRPrincipal) principal).createUser() : null;
         identity = provider.getUser(pId);

         if(ssoIdentity != null) {
            roles = ssoIdentity.getRoles();
            String identityOrg = ssoIdentity.getOrganizationID();
            groups = Arrays.stream(ssoIdentity.getGroups()).map(u -> new IdentityID(u,identityOrg)).toArray(IdentityID[]::new);
            organization = ssoIdentity.getOrganizationID();

            if(identity != null) {
               Set<IdentityID> combinedRoles = new HashSet<>();
               Collections.addAll(combinedRoles, roles);
               Collections.addAll(combinedRoles, identity.getRoles());
               roles = combinedRoles.toArray(new IdentityID[0]);

               Set<IdentityID> combinedGroups = new HashSet<>();
               Collections.addAll(combinedGroups, groups);
               Collections.addAll(combinedGroups, Arrays.stream(identity.getGroups()).map(u ->
                                  new IdentityID(u, identityOrg)).toArray(IdentityID[]::new));
               groups = combinedGroups.toArray(new IdentityID[0]);
            }
            else {
               identity = ssoIdentity;
            }
         }
         else if(identity != null) {
            roles = identity.getRoles();
            String identityOrg = identity.getOrganizationID();
            groups = Arrays.stream(identity.getGroups()).map(u -> new IdentityID(u,identityOrg)).toArray(IdentityID[]::new);
            organization = identity.getOrganizationID();
         }
      }
      else if(identity.getType() == Identity.GROUP) {
         identity = provider.getGroup(pId);

         if(identity != null) {
            roles = identity.getRoles();
            String identityOrg = identity.getOrganizationID();
            groups = Arrays.stream(identity.getGroups()).map(u -> new IdentityID(u,identityOrg)).toArray(IdentityID[]::new);
            organization = identity.getOrganizationID();
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
            organization = identity.getOrganizationID();
         }
      }
      else {
         identity = null;
         roles = provider.getRoles(pId);
         groups = Arrays.stream(provider.getUserGroups(pId)).map(u -> new IdentityID(u,pId.orgID)).toArray(IdentityID[]::new);
         organization = provider.getOrganization(pId.orgID).getOrganizationID();
      }

      //return true if admin permissions over root role
      if(type.equals(ResourceType.SECURITY_ROLE)) {
         String rootRole = Organization.getRootRoleName(principal);
         String rootOrgRole = Organization.getRootOrgRoleName(principal);
         Role role = provider.getRole(IdentityID.getIdentityIDFromKey(resource));
         Permission orgRoleRootPer;

         if(role != null && role.getOrganizationID() != null) {
            orgRoleRootPer = provider.getPermission(type, rootOrgRole);
         }
         else {
            orgRoleRootPer = provider.getPermission(type, rootRole);
         }

         if(orgRoleRootPer != null && (role == null ||
            Tool.equals(role.getOrganizationID(), OrganizationManager.getInstance().getCurrentOrgID())) &&
            checker.checkPermission(identity, orgRoleRootPer, action, true))
         {
            return true;
         }
      }
      else if(type.equals(ResourceType.SECURITY_GROUP)) {
         IdentityID rootGroup = new IdentityID("Groups", OrganizationManager.getInstance().getCurrentOrgID());
         Permission rootGroupPerm = provider.getPermission(type, rootGroup);

         if(rootGroupPerm != null && checker.checkPermission(identity, rootGroupPerm, action, true))
         {
            return true;
         }
      }
      //return true if admin permissions over root role
      else if(type.equals(ResourceType.SECURITY_USER)) {
         IdentityID rootUser = new IdentityID("Users", OrganizationManager.getInstance().getCurrentOrgID());
         Permission rootUserPerm = provider.getPermission(type, rootUser);

         if(rootUserPerm != null && checker.checkPermission(identity, rootUserPerm, action, true))
         {
            return true;
         }

         // if requested user is member of a group and group root has admin, allow access
         User resourceUser = provider.getUser((IdentityID.getIdentityIDFromKey(resource)));
         String[] userGroups = resourceUser == null ? null : resourceUser.getGroups();
          if(userGroups != null && userGroups.length > 0) {
            IdentityID rootGroup = new IdentityID("Groups", OrganizationManager.getInstance().getCurrentOrgID());
            Permission rootGroupPerm = provider.getPermission(ResourceType.SECURITY_GROUP, rootGroup);

            if(rootGroupPerm != null && checker.checkPermission(identity, rootGroupPerm, action, true))
            {
               return true;
            }
         }
      }

      XPrincipal xPrincipal = (principal instanceof XPrincipal) ? (XPrincipal) principal
         : new XPrincipal(new IdentityID(principal.getName(), pId.getOrgID()));

      xPrincipal.setRoles(roles);

      if(groups != null) {
         xPrincipal.setGroups(Arrays.stream(groups).map(g -> g.getName()).toArray(String[]::new));
      }

      // Bug #40590, always check permission for additional connection (backward
      // compatibility)
      if((roles != null || groups != null || organization != null) &&
         !(action == ResourceAction.READ && (type == ResourceType.DATA_SOURCE ||
            type == ResourceType.DATA_SOURCE_FOLDER) && resource.contains("::")))
      {
        final boolean isSysAdmin = Arrays.stream(xPrincipal.getAllRoles(provider))
            .anyMatch(provider::isSystemAdministratorRole);

         if(isSysAdmin || OrganizationManager.getInstance().isSiteAdmin(principal)) {
            return true;
         }

         final boolean isOrgAdministrator = Arrays.stream(xPrincipal.getAllRoles(provider))
            .anyMatch(provider::isOrgAdministratorRole);

         if(isOrgAdministrator && type == ResourceType.EM_COMPONENT &&
            "settings/content/data-space".equals(resource))
         {
            return false;
         }

         if(isOrgAdministrator) {
            IdentityID identityID = IdentityID.getIdentityIDFromKey(resource);

            if(identityID != null && "INETSOFT_SYSTEM".equals(identityID.name) &&
               identityID.orgID.endsWith("DataCycle Task"))
            {
               return true;
            }
         }

         //if admin permissions to this resource, return true
         boolean hasResourcePermission = provider.getPermission(type, resource) != null &&
            provider.getPermission(type, resource)
               .getOrgScopedUserGrants(ResourceAction.ASSIGN, OrganizationManager.getInstance().getCurrentOrgID())
               .contains(pId);

         if(hasResourcePermission) {
            return true;
         }

         if(checkOrgAdminPermission(type, resource, organization, xPrincipal)) {
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
         Permission rolePerm = provider.getPermission(type, new IdentityID("Organization Roles", organization));

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

   /**
    * Check if the target resource and action should be allowed for shared global vs.
    * @param principal the current login in user.
    * @param action    the resource action.
    * @param type      the resource type.
    * @param resource  the resource name.
    * @return
    */
   private boolean isAllowedDefaultGlobalVSAction(Principal principal, ResourceAction action,
                                                  ResourceType type, String resource)
   {
      String currOrgID = OrganizationManager.getInstance().getCurrentOrgID();
      String orgID = principal instanceof XPrincipal ? ((XPrincipal) principal).getOrgId() : null;

      if(!SUtil.isDefaultVSGloballyVisible(principal) || Tool.equals(orgID, currOrgID) ||
         !Organization.getDefaultOrganizationID().equals(currOrgID) || action != ResourceAction.READ)
      {
         return false;
      }

      if(type == ResourceType.VIEWSHEET_TOOLBAR_ACTION) {
         return !"Edit".equals(resource) && !"Import".equals(resource) && !"Schedule".equals(resource);
      }

      return type == ResourceType.CHART_TYPE || type == ResourceType.SHARE;
   }

   private boolean isNotGlobalRole(ResourceType type, IdentityID name) {
      return !type.equals(ResourceType.SECURITY_ROLE) || provider.getRole(name) == null ||
             (provider.getRole(name).getOrganizationID() != null &&
             !provider.getRole(name).getOrganizationID().isEmpty());
   }

   private boolean isSecurityIdentity(ResourceType type) {
      return type.equals(ResourceType.SECURITY_USER) ||
         type.equals(ResourceType.SECURITY_GROUP) ||
         type.equals(ResourceType.SECURITY_ROLE) ||
         type.equals(ResourceType.SECURITY_ORGANIZATION);
   }

   private boolean checkOrgAdminPermission(ResourceType type, String resource, String orgID,
                                           XPrincipal principal)
   {
      AuthenticationProvider currProvider =
         !(principal instanceof SRPrincipal) || SUtil.isInternalUser(principal) ?
            getCurrentProvider(principal) : provider;
      currProvider = currProvider == null ? provider : currProvider;
      boolean isSiteAdmin;
      final boolean isOrgAdmin = Arrays
         .stream(principal.getAllRoles(currProvider))
         .anyMatch(currProvider::isOrgAdministratorRole);
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      IdentityID resourceID = IdentityID.getIdentityIDFromKey(resource);
      IdentityID orgIdentityID = new IdentityID(currProvider.getOrgNameFromID(orgID), orgID);
      Permission orgPermissions = provider.getPermission(ResourceType.SECURITY_ORGANIZATION, orgIdentityID);
      final boolean hasOrgAdminPermission = orgID != null &&
            orgPermissions != null && orgPermissions.getOrgScopedUserGrants(ResourceAction.ADMIN, orgID) != null &&
            orgPermissions.getOrgScopedUserGrants(ResourceAction.ADMIN, orgID).contains(pId);

      if(!isOrgAdmin && !hasOrgAdminPermission) {
         return false;
      }

      // Org Admin has permission over all identities within an orgID except site admins
      switch(type) {
      case SECURITY_USER:
         if(resource.equals(new IdentityID("*", orgID).convertToKey()) ||
            new IdentityID("Users", orgID).convertToKey().equals(resource))
         {
            return true;
         }

         User user = currProvider.getUser(resourceID);

         // Bug #66393, SSO user do not in provider, so just equals org name.
         if(user == null) {
            return Objects.equals(resourceID.getOrgID(), orgID);
         }

         IdentityID[] userRoles = currProvider.getRoles(user.getIdentityID());
         isSiteAdmin = Arrays.stream(currProvider.getAllRoles(userRoles))
            .anyMatch(currProvider::isSystemAdministratorRole);

         return !isSiteAdmin && orgID.equals(currProvider.getUser(resourceID).getOrganizationID());
      case SECURITY_GROUP:
         if(resource.equals(new IdentityID("*",orgID).convertToKey()) ||
            resource.equals(new IdentityID("Groups", orgID).convertToKey()))
         {
            return true;
         }

         IdentityID[] groupRoles = currProvider.getGroup(resourceID) != null ?
            currProvider.getGroup(resourceID).getRoles() : new IdentityID[0];
         isSiteAdmin = Arrays.stream(currProvider.getAllRoles(groupRoles))
            .anyMatch(currProvider::isSystemAdministratorRole);

         return !isSiteAdmin && currProvider.getGroup(resourceID) != null &&
            orgID.equals(currProvider.getGroup(resourceID).getOrganizationID());
      case SECURITY_ROLE:
         String roleRoot = new IdentityID("Roles", orgID).convertToKey();
         String roleOrgRoot = new IdentityID("Organization Roles", orgID).convertToKey();
         IdentityID identityID = new IdentityID("*", orgID);

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

         return !isSiteAdmin && (currProvider.getRole(resourceID).getOrganizationID() == null ||
                 orgID.equals(currProvider.getRole(resourceID).getOrganizationID()));
      case SECURITY_ORGANIZATION:
         if(resource.equals("*")) {
            return false;
         }

         return Tool.equals(orgID, resourceID.getOrgID()) ||
            new IdentityID(currProvider.getOrgNameFromID(orgID), orgID).convertToKey().equals(resource);
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
            perm = provider.getPermission(currentType, new IdentityID("Users", OrganizationManager.getInstance().getCurrentOrgID()));

            if(perm != null) {
               users.addAll(perm.getOrgScopedUserGrants(action, orgId));
               roles.addAll(perm.getOrgScopedRoleGrants(action, orgId));
               groups.addAll(perm.getOrgScopedGroupGrants(action, orgId));
               organizations.addAll(perm.getOrgScopedOrganizationGrants(action, orgId));
            }
         }
         else if(currentType == ResourceType.SECURITY_ROLE) {

            perm = provider.getPermission(currentType, new IdentityID("Organization Roles", OrganizationManager.getInstance().getCurrentOrgID()));

            if(perm != null) {
               users.addAll(perm.getOrgScopedUserGrants(action, orgId));
               roles.addAll(perm.getOrgScopedRoleGrants(action, orgId));
               groups.addAll(perm.getOrgScopedGroupGrants(action, orgId));
               organizations.addAll(perm.getOrgScopedOrganizationGrants(action, orgId));
            }

            perm = provider.getPermission(currentType, new IdentityID("Roles", OrganizationManager.getInstance().getCurrentOrgID()));

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
         String rootIDKey = new IdentityID("*", OrganizationManager.getInstance().getCurrentOrgID()).convertToKey();
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
         !new IdentityID("Users", org).convertToKey().equals(resource.getPath()))
      {
         groups = provider.getUserGroups(resourceID);
      }
      else if(resource.getType() == ResourceType.SECURITY_GROUP &&
         !new IdentityID("Groups", org).convertToKey().equals(resource.getPath()))
      {
         groups = provider.getGroupParentGroups(resourceID);
      }
      else if((resource.getType() == ResourceType.SECURITY_GROUP ||
         resource.getType() == ResourceType.SECURITY_USER) && new IdentityID("Groups", org).convertToKey().equals(resource.getPath()) &&
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
         !new IdentityID("Groups", org).convertToKey().equals(resource.getPath()))
      {
         return Collections.singleton(new Resource(ResourceType.SECURITY_GROUP, new IdentityID("Groups", org).convertToKey()));
      }
      else if(groups.length == 0 && resource.getType() == ResourceType.SECURITY_USER &&
         !Tool.isEmptyString(org))
      {
         return Collections.singleton(new Resource(ResourceType.SECURITY_ORGANIZATION, org));
      }
      else {
         return Arrays.stream(groups)
            .map(name -> new IdentityID(name, resourceID.orgID))
            .map(g -> new Resource(ResourceType.SECURITY_GROUP, g.convertToKey()))
            .collect(Collectors.toSet());
      }
   }

   private final SecurityProvider provider;
   private final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
