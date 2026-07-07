/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.sree.security.support;

import inetsoft.sree.ClientInfo;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.util.Identity;
import org.mindrot.BCrypt;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

/**
 * Fluent builder that creates security test fixtures (users, roles, orgs, permissions) backed
 * by {@link FileAuthenticationProvider} and {@link FileAuthorizationProvider}.
 *
 * <p>Because {@link inetsoft.storage.KeyValueStorageManager} returns the same storage instance
 * for a given key, data written here is immediately visible to the {@link SecurityEngine}'s
 * internally-created provider instances.</p>
 *
 * <p>Typical usage:
 * <pre>{@code
 * SecurityTestDataBuilder builder = SecurityTestDataBuilder.create()
 *     .addOrg("Alpha", "alpha_id")
 *     .addRole("viewer", "alpha_id")
 *     .addUser("alice", "alpha_id", "pass")
 *     .addUserToRole("alice", "viewer", "alpha_id")
 *     .grantPermission(ResourceType.VIEWSHEET, "reports/vs1", ResourceAction.READ,
 *                      "viewer", Identity.ROLE, "alpha_id")
 *     .setup();
 * SRPrincipal alice = builder.principalOf("alice", "alpha_id");
 * // ... assertions ...
 * builder.teardown();
 * }</pre>
 */
public class SecurityTestDataBuilder {

   private String securityEnabled = "true";
   private String multiTenant = "true";

   private final Map<String, String> orgs = new LinkedHashMap<>();  // orgId → orgName
   private final List<UserSpec> users = new ArrayList<>();
   private final List<RoleSpec> roles = new ArrayList<>();
   private final List<UserRoleAssignment> userRoles = new ArrayList<>();
   private final List<PermissionSpec> permissions = new ArrayList<>();
   private final List<GroupSpec> groups = new ArrayList<>();
   private final List<UserGroupAssignment> userGroups = new ArrayList<>();
   private final List<GroupParentAssignment> groupParents = new ArrayList<>();
   private final List<RoleParentAssignment> roleParents = new ArrayList<>();
   private final List<GroupRoleAssignment> groupRoles = new ArrayList<>();
   private final List<EditedPermissionMarker> editedPermissions = new ArrayList<>();

   private FileAuthenticationProvider authcProvider;
   private FileAuthorizationProvider authzProvider;

   private SecurityTestDataBuilder() {}

   public static SecurityTestDataBuilder create() {
      return new SecurityTestDataBuilder();
   }

   // ── configuration ─────────────────────────────────────────────────────────

   public SecurityTestDataBuilder withSecurity(String enabled) {
      this.securityEnabled = enabled;
      return this;
   }

   public SecurityTestDataBuilder withMultiTenant(String enabled) {
      this.multiTenant = enabled;
      return this;
   }

   // ── data builders ─────────────────────────────────────────────────────────

   public SecurityTestDataBuilder addOrg(String orgName, String orgId) {
      orgs.put(orgId, orgName);
      return this;
   }

   public SecurityTestDataBuilder addUser(String userName, String orgId, String password) {
      users.add(new UserSpec(userName, orgId, password));
      return this;
   }

   public SecurityTestDataBuilder addRole(String roleName, String orgId) {
      roles.add(new RoleSpec(roleName, orgId, false, false));
      return this;
   }

   public SecurityTestDataBuilder addSysAdminRole(String roleName, String orgId) {
      roles.add(new RoleSpec(roleName, orgId, true, false));
      return this;
   }

   /**
    * Creates a role with no organization ({@code IdentityID(name, null)}), matching how the
    * built-in {@code Administrator}/{@code Organization Administrator} roles are constructed.
    * {@code DefaultCheckPermissionStrategy.isNotGlobalRole()} treats any role whose
    * {@code getOrganizationID()} is null as "global" and excludes it from the
    * SECURITY_ORGANIZATION-ADMIN cascade.
    */
   public SecurityTestDataBuilder addGlobalRole(String roleName) {
      roles.add(new RoleSpec(roleName, null, false, false));
      return this;
   }

   public SecurityTestDataBuilder addUserToRole(String userName, String roleName, String orgId) {
      userRoles.add(new UserRoleAssignment(userName, roleName, orgId));
      return this;
   }

   /**
    * Makes {@code roleName} a child of {@code parentRoleName} (both in {@code orgId}), i.e.
    * {@code roleName}'s {@code FSRole.setRoles()} (parent roles) includes {@code parentRoleName}.
    * {@code PermissionChecker.checkRolePermission()} walks a role identity's own
    * {@code getRoles()} recursively, so a grant on an ancestor role cascades down to roles that
    * inherit from it (same shape as {@link #addGroupParent}, but for roles).
    */
   public SecurityTestDataBuilder addRoleParent(String roleName, String parentRoleName,
                                                 String orgId)
   {
      roleParents.add(new RoleParentAssignment(roleName, parentRoleName, orgId));
      return this;
   }

   public SecurityTestDataBuilder addGroup(String groupName, String orgId) {
      groups.add(new GroupSpec(groupName, orgId));
      return this;
   }

   public SecurityTestDataBuilder addUserToGroup(String userName, String groupName, String orgId) {
      userGroups.add(new UserGroupAssignment(userName, groupName, orgId));
      return this;
   }

   /**
    * Makes {@code groupName} a child of {@code parentGroupName} (both in {@code orgId}), i.e.
    * {@code groupName}'s {@code FSGroup.setGroups()} (parent groups) includes
    * {@code parentGroupName}. {@link PermissionChecker#checkUserGroupPermission} walks this
    * chain upward from a checked group/user, so a delegated permission on an ancestor group
    * cascades down to descendants, not the other way around.
    */
   public SecurityTestDataBuilder addGroupParent(String groupName, String parentGroupName,
                                                  String orgId)
   {
      groupParents.add(new GroupParentAssignment(groupName, parentGroupName, orgId));
      return this;
   }

   /**
    * Assigns {@code roleName} to {@code groupName} ({@code FSGroup.setRoles()}), so members of
    * the group inherit the role the same way a user directly holding it would --
    * {@code PermissionChecker.checkRolePermission()}'s GROUP branch reads {@code identity.
    * getRoles()} the same as it does for a USER.
    */
   public SecurityTestDataBuilder addRoleToGroup(String roleName, String groupName, String orgId) {
      groupRoles.add(new GroupRoleAssignment(roleName, groupName, orgId));
      return this;
   }

   /**
    * Grants {@code action} on {@code resource} of {@code type} to {@code granteeId} in {@code orgId}.
    *
    * @param identityType one of {@link Identity#USER}, {@link Identity#ROLE},
    *                     {@link Identity#GROUP}, {@link Identity#ORGANIZATION}
    */
   public SecurityTestDataBuilder grantPermission(ResourceType type, String resource,
                                                   ResourceAction action,
                                                   String granteeId, int identityType,
                                                   String orgId)
   {
      permissions.add(new PermissionSpec(type, resource, action, granteeId, identityType, orgId));
      return this;
   }

   /**
    * Marks {@code resource}'s {@link Permission} as explicitly edited/saved for {@code orgId}
    * ({@link Permission#updateGrantAllByOrg(String, boolean)}, {@code true}), matching what the
    * real EM "set permission" endpoints do when an admin actually saves a resource's permission
    * page (e.g. {@code ResourcePermissionService.setResourceAdminPermissions()} calls
    * {@code permission.updateGrantAllByOrg(orgId, tableModel.hasOrgEdited())}). Without this,
    * {@code DefaultCheckPermissionStrategy}'s {@code hasOrgEditedGrantAll()} check is always
    * false, so the non-ADMIN inheritance walk in {@code checkPermission()} treats every resource
    * as "never configured" and keeps climbing past it toward the root regardless of any grants
    * recorded there -- {@link #grantPermission} alone does not reproduce a real saved-permission
    * state for that walk.
    */
   public SecurityTestDataBuilder markPermissionEdited(ResourceType type, String resource,
                                                        String orgId)
   {
      editedPermissions.add(new EditedPermissionMarker(type, resource, orgId));
      return this;
   }

   // ── lifecycle ─────────────────────────────────────────────────────────────

   /**
    * Creates and wires the security providers, persists SreeEnv settings, initialises
    * {@link SecurityEngine}, then writes all accumulated orgs/roles/users/permissions to
    * the shared {@link inetsoft.storage.KeyValueStorage}.
    */
   public SecurityTestDataBuilder setup() throws Exception {
      authcProvider = new FileAuthenticationProvider();
      authcProvider.setProviderName("Primary");
      authzProvider = new FileAuthorizationProvider();
      authzProvider.setProviderName("Primary");

      // setProviders() persists the chain to storage as a side effect;
      // SecurityEngine.init() (line below) reads it to discover provider types.
      AuthenticationChain authcChain = new AuthenticationChain();
      authcChain.setProviders(Collections.singletonList(authcProvider));

      AuthorizationChain authzChain = new AuthorizationChain();
      authzChain.setProviders(Collections.singletonList(authzProvider));

      SreeEnv.setProperty("security.enabled", securityEnabled);
      SreeEnv.setProperty("security.users.multiTenant", multiTenant);
      SreeEnv.save();

      SecurityEngine.getSecurity().init();

      // Write orgs
      for(Map.Entry<String, String> entry : orgs.entrySet()) {
         FSOrganization org = new FSOrganization(entry.getKey());
         org.setName(entry.getValue());
         authcProvider.addOrganization(org);
      }

      // Build role→parentRoles map before writing roles
      Map<IdentityID, List<IdentityID>> roleParentMap = new HashMap<>();
      for(RoleParentAssignment rp : roleParents) {
         roleParentMap
            .computeIfAbsent(new IdentityID(rp.roleName(), rp.orgId()), k -> new ArrayList<>())
            .add(new IdentityID(rp.parentRoleName(), rp.orgId()));
      }

      // Write roles
      for(RoleSpec rs : roles) {
         IdentityID roleId = new IdentityID(rs.roleName(), rs.orgId());
         FSRole role = new FSRole(roleId);
         role.setSysAdmin(rs.sysAdmin());
         role.setOrgAdmin(rs.orgAdmin());

         List<IdentityID> parentRoles = roleParentMap.getOrDefault(roleId, Collections.emptyList());

         if(!parentRoles.isEmpty()) {
            role.setRoles(parentRoles.toArray(new IdentityID[0]));
         }

         authcProvider.addRole(role);
      }

      // Build user→roles map before writing users
      Map<IdentityID, List<IdentityID>> userRoleMap = new HashMap<>();
      for(UserRoleAssignment ur : userRoles) {
         userRoleMap
            .computeIfAbsent(new IdentityID(ur.userName(), ur.orgId()), k -> new ArrayList<>())
            .add(new IdentityID(ur.roleName(), ur.orgId()));
      }

      // Build group→parentGroups map before writing groups
      Map<IdentityID, List<String>> groupParentMap = new HashMap<>();
      for(GroupParentAssignment gp : groupParents) {
         groupParentMap
            .computeIfAbsent(new IdentityID(gp.groupName(), gp.orgId()), k -> new ArrayList<>())
            .add(gp.parentGroupName());
      }

      // Build group→roles map before writing groups
      Map<IdentityID, List<IdentityID>> groupRoleMap = new HashMap<>();
      for(GroupRoleAssignment gr : groupRoles) {
         groupRoleMap
            .computeIfAbsent(new IdentityID(gr.groupName(), gr.orgId()), k -> new ArrayList<>())
            .add(new IdentityID(gr.roleName(), gr.orgId()));
      }

      // Write groups
      for(GroupSpec gs : groups) {
         IdentityID groupId = new IdentityID(gs.groupName(), gs.orgId());
         FSGroup group = new FSGroup(groupId);

         List<String> parentGroups = groupParentMap.getOrDefault(groupId, Collections.emptyList());

         if(!parentGroups.isEmpty()) {
            group.setGroups(parentGroups.toArray(new String[0]));
         }

         List<IdentityID> assignedRoles = groupRoleMap.getOrDefault(groupId, Collections.emptyList());

         if(!assignedRoles.isEmpty()) {
            group.setRoles(assignedRoles.toArray(new IdentityID[0]));
         }

         authcProvider.addGroup(group);
      }

      // Build user→groups map before writing users
      Map<IdentityID, List<String>> userGroupMap = new HashMap<>();
      for(UserGroupAssignment ug : userGroups) {
         userGroupMap
            .computeIfAbsent(new IdentityID(ug.userName(), ug.orgId()), k -> new ArrayList<>())
            .add(ug.groupName());
      }

      // Write users
      for(UserSpec us : users) {
         IdentityID userId = new IdentityID(us.userName(), us.orgId());
         FSUser user = new FSUser(userId);
         user.setPassword(BCrypt.hashpw(us.password(), BCrypt.gensalt()));
         user.setPasswordAlgorithm("bcrypt");
         user.setActive(true);

         List<IdentityID> assignedRoles = userRoleMap.getOrDefault(userId, Collections.emptyList());

         if(!assignedRoles.isEmpty()) {
            user.setRoles(assignedRoles.toArray(new IdentityID[0]));
         }

         List<String> assignedGroups = userGroupMap.getOrDefault(userId, Collections.emptyList());

         if(!assignedGroups.isEmpty()) {
            user.setGroups(assignedGroups.toArray(new String[0]));
         }

         authcProvider.addUser(user);
      }

      // Write permissions
      for(PermissionSpec ps : permissions) {
         Permission perm = authzProvider.getPermission(ps.type(), ps.resource(), ps.orgId());

         if(perm == null) {
            perm = new Permission();
         }

         Set<Permission.PermissionIdentity> grants =
            new HashSet<>(perm.getGrants(ps.action(), ps.identityType(), null));
         grants.add(new Permission.PermissionIdentity(ps.granteeId(), ps.orgId()));
         perm.setGrants(ps.action(), ps.identityType(), grants);

         authzProvider.setPermission(ps.type(), ps.resource(), perm, ps.orgId());
      }

      // Mark resources as explicitly edited (see markPermissionEdited() javadoc) -- must run
      // after the grants loop above so it marks the same Permission object just written, not an
      // empty one.
      for(EditedPermissionMarker em : editedPermissions) {
         Permission perm = authzProvider.getPermission(em.type(), em.resource(), em.orgId());

         if(perm == null) {
            perm = new Permission();
         }

         perm.updateGrantAllByOrg(em.orgId(), true);
         authzProvider.setPermission(em.type(), em.resource(), perm, em.orgId());
      }

      return this;
   }

   /**
    * Removes SreeEnv properties, closes provider storages, then re-initialises
    * {@link SecurityEngine} with security disabled so it holds no stale provider references.
    */
   public void teardown() {
      SreeEnv.remove("security.enabled");
      SreeEnv.remove("security.users.multiTenant");

      if(authcProvider != null) {
         authcProvider.tearDown();
         authcProvider = null;
      }

      if(authzProvider != null) {
         authzProvider.tearDown();
         authzProvider = null;
      }

      // Re-init with security disabled so the engine's internal provider is set to null.
      // Any double-close of the shared KeyValueStorage is silently swallowed by tearDown().
      SecurityEngine.getSecurity().init();
   }

   // ── principal factory ─────────────────────────────────────────────────────

   /**
    * Returns an {@link SRPrincipal} for {@code userName} in {@code orgId}, pre-populated with
    * the user's roles and groups. The principal is registered in SecurityEngine's session cache
    * so {@link SecurityEngine#checkPermission} does not throw {@link SecurityException} for
    * non-exempt resource types.
    *
    * <p>Requires {@link #setup()} to have been called first.</p>
    */
   public SRPrincipal principalOf(String userName, String orgId) {
      IdentityID userId = new IdentityID(userName, orgId);

      User user = authcProvider != null ? authcProvider.getUser(userId) : null;
      IdentityID[] userRoles = user != null ? user.getRoles() : new IdentityID[0];
      String[] userGroups = user != null ? user.getGroups() : new String[0];

      SRPrincipal principal = new SRPrincipal(userId, userRoles, userGroups, orgId, 1L);

      // Register as logged-in so checkPermission does not throw for non-exempt resource types
      SecurityEngine engine = SecurityEngine.getSecurity();
      @SuppressWarnings("unchecked")
      Map<ClientInfo, SRPrincipal> sessionUsers =
         (Map<ClientInfo, SRPrincipal>) ReflectionTestUtils.getField(engine, "users");

      if(sessionUsers != null) {
         sessionUsers.put(principal.getUser().getCacheKey(), principal);
      }

      return principal;
   }

   // ── internal value types ──────────────────────────────────────────────────

   private record UserSpec(String userName, String orgId, String password) {}
   private record RoleSpec(String roleName, String orgId, boolean sysAdmin, boolean orgAdmin) {}
   private record UserRoleAssignment(String userName, String roleName, String orgId) {}
   private record PermissionSpec(ResourceType type, String resource, ResourceAction action,
                                  String granteeId, int identityType, String orgId) {}
   private record GroupSpec(String groupName, String orgId) {}
   private record UserGroupAssignment(String userName, String groupName, String orgId) {}
   private record GroupParentAssignment(String groupName, String parentGroupName, String orgId) {}
   private record RoleParentAssignment(String roleName, String parentRoleName, String orgId) {}
   private record GroupRoleAssignment(String roleName, String groupName, String orgId) {}
   private record EditedPermissionMarker(ResourceType type, String resource, String orgId) {}
}
