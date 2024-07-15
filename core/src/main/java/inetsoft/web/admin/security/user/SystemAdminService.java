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
package inetsoft.web.admin.security.user;

// Service to provide helper methods for ensuring that there is always a system administrator present in the system

import inetsoft.sree.security.*;
import inetsoft.uql.util.Identity;
import inetsoft.web.admin.security.IdentityModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SystemAdminService {
   @Autowired
   public SystemAdminService(SecurityProvider securityProvider) {
      this.securityProvider = securityProvider;
   }

   public boolean hasSystemAdminAfterDelete(Set<Identity> identities) {
      Set<IdentityModification> deletions = identities.stream()
         .map(IdentityModification::new)
         .collect(Collectors.toSet());
      deletions.forEach(d -> d.setDelete(true));

      return hasSysAdmin(deletions);
   }

   public boolean hasOrgAdminAfterDelete(Set<Identity> identities) {
      Set<IdentityModification> deletions = identities.stream()
         .map(IdentityModification::new)
         .collect(Collectors.toSet());
      deletions.forEach(d -> d.setDelete(true));

      return hasOrgAdmin(deletions);
   }

   /**
    * Determine if there will be a user with a System Admin role after an identity is modified
    * in a way that could lead to no system admin
    * <p>
    * Modifications that could cause a state where there is no sys admin include:
    * Delete user/group/role
    * Remove role assigned to user or group or role inheritance
    * Remove user/group from group
    * <p>
    * Returns true if a System Administrator user will exist after the potential modifications are made
    */
   public boolean hasSysAdmin(Set<IdentityModification> identityModifications) {
      AuthenticationProvider authentication = securityProvider.getAuthenticationProvider();

      // System Admin Roles
      List<IdentityModification> roleModifications = identityModifications.stream()
         .filter(i -> i.getType() == Identity.ROLE)
         .collect(Collectors.toList());

      List<IdentityID> sysAdminRoles = getSysAdminRoles(roleModifications, authentication);

      // Check for System Admin Users
      for(IdentityID adminRole : sysAdminRoles) {
         List<IdentityID> users = usersWithRole(adminRole, identityModifications);

         // Return true if a user is found with a system admin role assigned to it
         if(users.size() > 0) {
            return true;
         }
      }

      return false;
   }

   public boolean hasOrgAdmin(Set<IdentityModification> identityModifications) {
      AuthenticationProvider authentication = securityProvider.getAuthenticationProvider();

      // System Admin Roles
      List<IdentityModification> roleModifications = identityModifications.stream()
         .filter(i -> i.getType() == Identity.ROLE)
         .collect(Collectors.toList());

      List<IdentityID> orgAdminRoles = getOrgAdminRoles(roleModifications, authentication);

      // as long as an orgAdminRole exists, return true
      return orgAdminRoles != null && !orgAdminRoles.isEmpty();
   }

   // Returns roles with system administrator privileges given that certain roles no longer exist
   private List<IdentityID> getSysAdminRoles(List<IdentityModification> roleModifications,
                                         AuthenticationProvider provider)
   {
      // Name of role that has the system admin property removed
      final IdentityID removedSysAdmin = roleModifications.size() > 0 &&
         roleModifications.get(0).isSysAdminRemoved() ? roleModifications.get(0).getIdentityID() : null;

      // Always filter out all roles that are being deleted
      List<IdentityID> deletedRoles = roleModifications.stream()
         .filter(IdentityModification::isDelete)
         .map(IdentityModification::getIdentityID)
         .collect(Collectors.toList());

      // Roles with the system administrator property
      List<IdentityID> sysAdminRoles = Arrays.stream(securityProvider.getRoles())
         .filter(provider::isSystemAdministratorRole)
         .filter(r -> !deletedRoles.contains(r) && !r.equals(removedSysAdmin))
         .collect(Collectors.toList());

      List<IdentityID> rolesQueue = new ArrayList<>(sysAdminRoles);

      while(rolesQueue.size() > 0) {
         // Filter out deleted roles
         sysAdminRoles.removeAll(deletedRoles);

         // Roles that are System Admin roles through their inheritance
         IdentityID role = rolesQueue.remove(0);
         List<IdentityID> inheritedRoles = getInheritingRoles(role);

         // Filter out inherited roles that are being removed
         roleModifications.stream()
            .filter(i -> i.getIdentityID().equals(role))
            .findFirst()
            .ifPresent(roleMod -> inheritedRoles.removeAll(roleMod.getRemovedRoles()));

         sysAdminRoles.addAll(inheritedRoles);
         rolesQueue.addAll(inheritedRoles);
      }

      return sysAdminRoles;
   }

   // Returns roles with org administrator privileges given that certain roles no longer exist
   private List<IdentityID> getOrgAdminRoles(List<IdentityModification> roleModifications,
                                         AuthenticationProvider provider)
   {
      // Name of role that has the system admin property removed
      final IdentityID removedOrgAdmin = roleModifications.size() > 0 &&
         roleModifications.get(0).isOrgAdminRemoved() ? roleModifications.get(0).getIdentityID() : null;

      // Always filter out all roles that are being deleted
      List<IdentityID> deletedRoles = roleModifications.stream()
         .filter(IdentityModification::isDelete)
         .map(IdentityModification::getIdentityID)
         .collect(Collectors.toList());

      // Roles with the org administrator property
      List<IdentityID> orgAdminRoles = Arrays.stream(securityProvider.getRoles())
         .filter(provider::isOrgAdministratorRole)
         .filter(r -> !deletedRoles.contains(r) && !r.equals(removedOrgAdmin))
         .filter(r -> provider.getRole(r).getOrganization() == null) //global roles only
         .collect(Collectors.toList());

      List<IdentityID> rolesQueue = new ArrayList<>(orgAdminRoles);

      while(rolesQueue.size() > 0) {
         // Filter out deleted roles
         orgAdminRoles.removeAll(deletedRoles);

         // Roles that are System Admin roles through their inheritance
         IdentityID role = rolesQueue.remove(0);
         List<IdentityID> inheritedRoles = getInheritingRoles(role);

         // Filter out inherited roles that are being removed
         roleModifications.stream()
            .filter(i -> i.getIdentityID().equals(role))
            .findFirst()
            .ifPresent(roleMod -> inheritedRoles.removeAll(roleMod.getRemovedRoles()));

         orgAdminRoles.addAll(inheritedRoles);
         rolesQueue.addAll(inheritedRoles);
      }

      return orgAdminRoles;
   }

   // returns roles that inherit from the selected role
   private List<IdentityID> getInheritingRoles(IdentityID roleId) {
      return Arrays.stream(securityProvider.getRoles())
         .filter(r -> Arrays.asList(securityProvider.getRole(r).getRoles()).contains(roleId))
         .collect(Collectors.toCollection(ArrayList::new));
   }

   // returns users that have the selected role or have it through their groups
   private List<IdentityID> usersWithRole(IdentityID roleId, Set<IdentityModification> identityModifications) {
      List<IdentityID> groups = new ArrayList<>();
      List<IdentityID> users = new ArrayList<>();
      List<String> organizations = new ArrayList<>();
      IdentityModification roleModification = identityModifications.stream()
         .filter(i -> roleId.equals(i.getIdentityID()) && i.getType() == Identity.ROLE)
         .findFirst().orElse(null);

      for(Identity member : securityProvider.getRoleMembers(roleId)) {
         if(identityHasRole(member, roleId, identityModifications)) {
            if(member.getType() == Identity.USER) {
               users.add(member.getIdentityID());
            }
            else if(member.getType() == Identity.ORGANIZATION) {
               organizations.add(member.getName());
            }
            else {
               groups.add(member.getIdentityID());
            }
         }
      }

      if(roleModification != null) {
         groups.removeAll(roleModification.getRemovedGroups());
         users.removeAll(roleModification.getRemovedUsers());
         organizations.removeAll(roleModification.getRemovedOrganizations());
      }

      while(groups.size() > 0) {
         IdentityID group = groups.remove(0);

         for(Identity member : securityProvider.getGroupMembers(group)) {
            if(identityHasRole(member, roleId, identityModifications) &&
               identityHasGroup(member, group.name, identityModifications))
            {
               if(member.getType() == Identity.USER) {
                  users.add(member.getIdentityID());
               }
               else if(roleModification != null) {
                  groups.add(member.getIdentityID());
               }
            }
         }

         if(roleModification != null) {
            groups.removeAll(roleModification.getRemovedGroups());
            users.removeAll(roleModification.getRemovedUsers());
         }
      }

      while(organizations.size() > 0) {
         String orgName = organizations.remove(0);
         Organization org = securityProvider.getOrganization(orgName);
         if(Arrays.asList(org.getRoles()).contains(roleId)) {
            users.add(new IdentityID(orgName, roleId.organization));
         }
      }

      return users.stream().distinct().collect(Collectors.toList());
   }

   // Returns true if the identity will have the role assigned after changes are made
   private boolean identityHasRole(Identity identity, IdentityID role,
                                   Set<IdentityModification> identityModifications)
   {
      return identityModifications.stream()
         .filter(i -> identity.getIdentityID().equals(i.getIdentityID()) && i.getType() == identity.getType())
         .noneMatch(i -> i.isDelete() || i.getRemovedRoles().contains(role));
   }

   // Returns true if the identity will be a member of the group after changes are made
   private boolean identityHasGroup(Identity identity, String group,
                                    Set<IdentityModification> modificationSet)
   {
      return modificationSet.stream()
         .filter(i -> identity.getIdentityID().equals(i.getIdentityID()) && i.getType() == identity.getType())
         .noneMatch(i -> i.isDelete() || i.getRemovedGroups().contains(group));
   }

   public Identity createIdentity(IdentityID id, int type) {
      if(type == Identity.USER) {
         return new User(id);
      }
      else if(type == Identity.GROUP) {
         return new Group(id);
      }
      else if(type == Identity.ROLE) {
         return new Role(id);
      }
      else if(type == Identity.ORGANIZATION) {
         return new Organization(id.name);
      }

      return null;
   }

   public IdentityModification getUserModification(User user, EditUserPaneModel model,
                                                   Principal principal)
   {
      IdentityModification change = new IdentityModification(user);

      List<IdentityID> newGroupList = model.members().stream()
         .filter(i -> i.type() == Identity.GROUP)
         .map(IdentityModel::identityID)
         .collect(Collectors.toList());
      List<IdentityID> removedGroups = Arrays.stream(user.getGroups())
         .filter(g -> securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP, g, ResourceAction.ADMIN))
         .map(g -> new IdentityID(g, model.organization()))
         .filter(g -> !newGroupList.contains(g))
         .collect(Collectors.toList());
      change.setRemovedGroups(removedGroups);

      List<IdentityID> removedRoles = Arrays.stream(user.getRoles())
         .filter(r -> securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE, r.convertToKey(), ResourceAction.ASSIGN))
         .filter(r -> !model.roles().contains(r))
         .collect(Collectors.toList());
      change.setRemovedRoles(removedRoles);

      return change;
   }

   public IdentityModification getGroupModification(Group group, EditGroupPaneModel model,
                                                    Principal principal)
   {
      IdentityModification change = new IdentityModification(group);

      List<IdentityID> newUsersList = model.members().stream()
         .filter(i -> i.type() == Identity.USER)
         .map(IdentityModel::identityID)
         .collect(Collectors.toList());
      List<IdentityID> removedUsers = Arrays.stream(securityProvider.getGroupMembers(group.getIdentityID()))
         .filter(i -> i.getType() == Identity.USER)
         .filter(u -> !newUsersList.contains(u.getIdentityID()) && securityProvider.checkPermission(
            principal, ResourceType.SECURITY_USER, u.getIdentityID().convertToKey(), ResourceAction.ADMIN))
         .map(Identity::getIdentityID)
         .collect(Collectors.toList());
      change.setRemovedUsers(removedUsers);

      List<IdentityID> newGroupList = model.members().stream()
         .filter(i -> i.type() == Identity.GROUP)
         .map(IdentityModel::identityID)
         .collect(Collectors.toList());
      List<IdentityID> removedGroups = Arrays.stream(securityProvider.getGroupMembers(group.getIdentityID()))
         .filter(i -> i.getType() == Identity.GROUP)
         .filter(g -> !newGroupList.contains(g.getIdentityID()) && securityProvider.checkPermission(
            principal, ResourceType.SECURITY_GROUP, g.getIdentityID().convertToKey(), ResourceAction.ADMIN))
         .map(Identity::getIdentityID)
         .collect(Collectors.toList());
      change.setRemovedGroups(removedGroups);

      List<IdentityID> removedRoles = Arrays.stream(group.getRoles())
         .filter(r -> securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE, r.convertToKey(), ResourceAction.ASSIGN))
         .filter(r -> !model.roles().contains(r))
         .collect(Collectors.toList());
      change.setRemovedRoles(removedRoles);

      return change;
   }

   public IdentityModification getRoleModification(Role role, EditRolePaneModel model,
                                                   Principal principal)
   {
      IdentityModification change = new IdentityModification(role);

      List<IdentityID> newUsersList = model.members().stream()
         .filter(i -> i.type() == Identity.USER)
         .map(IdentityModel::identityID)
         .collect(Collectors.toList());
      List<IdentityID> removedUsers = Arrays.stream(securityProvider.getRoleMembers(role.getIdentityID()))
         .filter(i -> i.getType() == Identity.USER)
         .filter(u -> !newUsersList.contains(u.getIdentityID()) && securityProvider.checkPermission(
            principal, ResourceType.SECURITY_USER, u.getIdentityID().convertToKey(), ResourceAction.ADMIN))
         .map(Identity::getIdentityID)
         .collect(Collectors.toList());
      change.setRemovedUsers(removedUsers);

      List<IdentityID> newGroupList = model.members().stream()
         .filter(i -> i.type() == Identity.GROUP)
         .map(IdentityModel::identityID)
         .collect(Collectors.toList());
      List<IdentityID> removedGroups = Arrays.stream(securityProvider.getRoleMembers(role.getIdentityID()))
         .filter(i -> i.getType() == Identity.GROUP)
         .filter(g -> !newGroupList.contains(g.getIdentityID()) && securityProvider.checkPermission(
            principal, ResourceType.SECURITY_GROUP, g.getIdentityID().convertToKey(), ResourceAction.ADMIN))
         .map(Identity::getIdentityID)
         .collect(Collectors.toList());
      change.setRemovedGroups(removedGroups);

      List<String> newOrganizationList = model.members().stream()
         .filter(i -> i.type() == Identity.ORGANIZATION)
         .map(IdentityModel::identityID)
         .map(o -> o.name)
         .collect(Collectors.toList());
      List<String> removedOrganizations = Arrays.stream(securityProvider.getRoleMembers(role.getIdentityID()))
         .filter(i -> i.getType() == Identity.ORGANIZATION)
         .filter(g -> !newOrganizationList.contains(g.getName()) && securityProvider.checkPermission(
            principal, ResourceType.SECURITY_ORGANIZATION, g.getName(), ResourceAction.ADMIN))
         .map(Identity::getName)
         .collect(Collectors.toList());
      change.setRemovedOrganizations(removedOrganizations);

      List<IdentityID> roles = Arrays.stream(role.getRoles())
         .filter(r -> !model.roles().contains(r) && securityProvider.checkPermission(
            principal, ResourceType.SECURITY_ROLE, r.convertToKey(), ResourceAction.ASSIGN))
         .collect(Collectors.toList());
      change.setRemovedRoles(roles);
      change.setSysAdminRemoved(!model.isSysAdmin());
      change.setOrgAdminRemoved(!model.isOrgAdmin());
      return change;
   }

   public IdentityModification getOrganizationModification(Organization oldOrg, EditOrganizationPaneModel model, Principal principal) {
      IdentityModification change = new IdentityModification(oldOrg);

      List<IdentityID> newUsersList = model.members().stream()
         .filter(i -> i.type() == Identity.USER)
         .map(IdentityModel::identityID)
         .collect(Collectors.toList());
      List<IdentityID> removedUsers = Arrays.stream(securityProvider.getOrganizationMemberIdentities(oldOrg.getIdentityID().name))
         .filter(i -> i.getType() == Identity.USER)
         .filter(u -> !newUsersList.contains(u.getIdentityID()) && securityProvider.checkPermission(
            principal, ResourceType.SECURITY_USER, u.getIdentityID().convertToKey(), ResourceAction.ADMIN))
         .map(Identity::getIdentityID)
         .collect(Collectors.toList());
      change.setRemovedUsers(removedUsers);

      List<IdentityID> removedRoles = Arrays.stream(oldOrg.getRoles())
         .filter(r -> securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE, r.convertToKey(), ResourceAction.ASSIGN))
         .filter(r -> !model.roles().contains(r))
         .collect(Collectors.toList());
      change.setRemovedRoles(removedRoles);

      return change;

   }

   private final SecurityProvider securityProvider;
}
