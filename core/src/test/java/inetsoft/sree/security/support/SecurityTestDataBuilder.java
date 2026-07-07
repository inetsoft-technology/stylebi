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

      // Write roles
      for(RoleSpec rs : roles) {
         FSRole role = new FSRole(new IdentityID(rs.roleName(), rs.orgId()));
         role.setSysAdmin(rs.sysAdmin());
         role.setOrgAdmin(rs.orgAdmin());
         authcProvider.addRole(role);
      }

      // Build user→roles map before writing users
      Map<IdentityID, List<IdentityID>> userRoleMap = new HashMap<>();
      for(UserRoleAssignment ur : userRoles) {
         userRoleMap
            .computeIfAbsent(new IdentityID(ur.userName(), ur.orgId()), k -> new ArrayList<>())
            .add(new IdentityID(ur.roleName(), ur.orgId()));
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
}
