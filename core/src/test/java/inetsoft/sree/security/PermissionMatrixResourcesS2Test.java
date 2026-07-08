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
package inetsoft.sree.security;

/*
 * Slice test class for permission-matrix-resources.md § S2 (SECURITY_* identity management
 * boundary). See that doc for the full scenario tables and root-cause write-ups of the bugs
 * tracked here -- comments in this file are kept to code-level specifics that aren't obvious
 * from the doc (fixture wiring, resource-key format gotchas), not restated narrative.
 *
 * Fixture setup (see the addXxxFixtures() helpers called from setUp()) and test methods below
 * are both organized to mirror the doc's own subsections:
 *   S2.1 Organization              -- addOrganizationFixtures() / orgSecurityAdmin_*
 *   S2.2 Users                     -- addUsersFixtures()        / identityAdminUser_*,
 *                                      identityAdminWildUser_*, rootUserAdmin_*,
 *                                      S2-GRANTEE-VARIETY (viaRoleUser/noRoleUser/viaGroupUser/
 *                                      viaSubGroupUser/viaAnyOneOfThreeUser)
 *   S2.3 Groups                    -- addGroupsFixtures()       / identityAdminGroupInstUser_*,
 *                                      rootGroupAdmin_*, S2-GROUP-CHAIN (chainAdmin/midChainAdmin)
 *   S2.4 Organization Roles/Roles  -- addRolesFixtures()        / identityAdminRoleUser_*,
 *                                      rootRoleAdmin_*, S2-GLOBAL-ROLE-ROOT (rootGlobalRoleAdmin)
 *
 * identityAdmin-group(wildcard) has no fixture/test here: investigated and confirmed to not be a
 * separate mechanism from the S2.3 root cascade below (see permission-matrix-resources.md § S2.3).
 */

import inetsoft.sree.security.support.*;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.stream.Stream;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PermissionMatrixResourcesS2Test {

   private static final String ORG_NAME = "matrix_org";
   private static final String ORG_ID = "matrix_org_id";

   // Shared across sections: checked as resources by S2.1's cross-type-cascade positive controls,
   // and as the primary subjects of S2.2/S2.3/S2.4's own scenarios below.
   private static final String TARGET_USER = "targetUser";
   private static final String ANOTHER_USER = "anotherUser";
   private static final String TARGET_GROUP = "targetGroup";
   private static final String ANOTHER_GROUP = "anotherGroup";
   private static final String TARGET_ROLE = "targetRole";
   private static final String ANOTHER_ROLE = "anotherRole";
   private static final String GLOBAL_ROLE = "globalRole0";
   private static final String GLOBAL_SYSADMIN_ROLE = "globalSysAdminRole0";
   private static final String ASSET_RESOURCE = "mx/folder/item";

   // S2.2 / S2-GRANTEE-VARIETY fixture identifiers
   private static final String TARGET_USER_2 = "targetUser2";
   private static final String TARGET_USER_3 = "targetUser3";
   private static final String GRANTEE_ROLE = "role0";
   private static final String GRANTEE_GROUP = "group0";
   private static final String GRANTEE_SUBGROUP = "group1";
   private static final String UNRELATED_USER_3 = "unrelatedUser3";
   private static final String UNRELATED_GROUP_3 = "unrelatedGroup3";

   // S2.3 / S2-GROUP-CHAIN fixture identifiers: a 3-level group chain
   // (CHAIN_GROUP_0 -> CHAIN_GROUP_1 -> CHAIN_GROUP_2) plus an unrelated sibling group.
   private static final String CHAIN_GROUP_0 = "adminChainGroup0";
   private static final String CHAIN_GROUP_1 = "adminChainGroup1";
   private static final String CHAIN_GROUP_2 = "adminChainGroup2";
   private static final String CHAIN_SIBLING_GROUP = "adminChainSiblingGroup";

   private static SecurityTestDataBuilder builder;

   private static SRPrincipal orgSecurityAdmin;
   private static SRPrincipal identityAdminUser;
   private static SRPrincipal identityAdminWildUser;
   private static SRPrincipal identityAdminGroupInstUser;
   private static SRPrincipal identityAdminRoleUser;
   private static SRPrincipal rootUserAdmin;
   private static SRPrincipal rootGroupAdmin;
   private static SRPrincipal rootRoleAdmin;
   private static SRPrincipal rootGlobalRoleAdmin;
   private static SRPrincipal viaRoleUser;
   private static SRPrincipal noRoleUser;
   private static SRPrincipal viaGroupUser;
   private static SRPrincipal viaSubGroupUser;
   private static SRPrincipal viaAnyOneOfThreeUser;
   private static SRPrincipal chainAdmin;
   private static SRPrincipal midChainAdmin;

   // ── fixture setup ──────────────────────────────────────────────────────────

   @BeforeAll
   static void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create().addOrg(ORG_NAME, ORG_ID);

      addSharedIdentities(builder);
      addOrganizationFixtures(builder);
      addUsersFixtures(builder);
      addGroupsFixtures(builder);
      addRolesFixtures(builder);

      builder.setup();
      initPrincipals();
   }

   /** Roles/users referenced by more than one S2.x section below. */
   private static void addSharedIdentities(SecurityTestDataBuilder b) {
      b.addRole(TARGET_ROLE, ORG_ID)
       .addRole(ANOTHER_ROLE, ORG_ID)
       .addUser(TARGET_USER, ORG_ID, "password")
       .addUser(ANOTHER_USER, ORG_ID, "password")
       // Negative control for S2.1's cascade: a role with NO organization (IdentityID(name,
       // null)), matching how built-in Administrator/Organization Administrator roles are
       // constructed. isNotGlobalRole() should exclude it.
       .addGlobalRole(GLOBAL_ROLE)
       // Mirrors the REAL built-in "Administrator" role: org=null AND sysAdmin=true
       // (FileAuthenticationProvider.java L990: new IdentityID("Administrator", null)).
       .addSysAdminRole(GLOBAL_SYSADMIN_ROLE, null);
      // TARGET_GROUP/ANOTHER_GROUP intentionally have no addGroup() call below -- every check
      // against them uses a plain USER principal, so they're just permission-storage keys in
      // this fixture, not real group lookups.
   }

   /** S2.1 — Organization: orgSecurityAdmin's SECURITY_ORGANIZATION-ADMIN cross-type cascade. */
   private static void addOrganizationFixtures(SecurityTestDataBuilder b) {
      b.addUser("orgSecurityAdmin", ORG_ID, "password")
       // A user with no role assignment, granted EM access directly (not through a role) plus
       // ADMIN on this org's SECURITY_ORGANIZATION resource. The EM grant isn't consumed by any
       // assertion here (assertions call SecurityEngine.checkPermission() directly) but keeps the
       // fixture matching the real-world persona 1:1.
       .grantPermission(ResourceType.EM, "*", ResourceAction.ACCESS,
                        "orgSecurityAdmin", Identity.USER, ORG_ID)
       // Resource key must be IdentityID(orgName, orgId).convertToKey() -- that's what
       // DefaultCheckPermissionStrategy's L57-67 cascade reads via
       // provider.getPermission(SECURITY_ORGANIZATION, IdentityID(orgName, orgId)).
       .grantPermission(ResourceType.SECURITY_ORGANIZATION,
                        new IdentityID(ORG_NAME, ORG_ID).convertToKey(),
                        ResourceAction.ADMIN, "orgSecurityAdmin", Identity.USER, ORG_ID);
   }

   /**
    * S2.2 — Users: identityAdminUser (instance), identityAdminWildUser (wildcard), rootUserAdmin
    * (root cascade), and S2-GRANTEE-VARIETY (grantee can be a ROLE/GROUP, not just a USER).
    */
   private static void addUsersFixtures(SecurityTestDataBuilder b) {
      b.addUser("identityAdminUser", ORG_ID, "password")
       .addUser("identityAdminWildUser", ORG_ID, "password")
       .addUser("rootUserAdmin", ORG_ID, "password")

       .grantPermission(ResourceType.SECURITY_USER, TARGET_USER, ResourceAction.ADMIN,
                        "identityAdminUser", Identity.USER, ORG_ID)

       // Wildcard resource key is the literal org ID string (NOT "*", NOT an IdentityID key) --
       // DefaultCheckPermissionStrategy's L369-376 wildcard check does a 2-arg
       // provider.getPermission(SECURITY_USER, orgID) lookup that resolves the org via
       // ThreadContext, independent of which specific user is being checked.
       .grantPermission(ResourceType.SECURITY_USER, ORG_ID, ResourceAction.ADMIN,
                        "identityAdminWildUser", Identity.USER, ORG_ID)

       // Root cascade key is IdentityID("Users", orgId).convertToKey() -- a synthetic resource
       // DefaultCheckPermissionStrategy checks unconditionally before any per-instance lookup,
       // independent of both the SECURITY_ORGANIZATION cascade (S2.1) and the wildcard above.
       .grantPermission(ResourceType.SECURITY_USER,
                        new IdentityID("Users", ORG_ID).convertToKey(),
                        ResourceAction.ADMIN, "rootUserAdmin", Identity.USER, ORG_ID);

      addGranteeVarietyFixtures(b);
   }

   /**
    * S2-GRANTEE-VARIETY: an Administrator Permissions grantee can be a ROLE or a GROUP (with
    * ordinary group-membership/parent-chain inheritance), not just a USER, and multiple grantee
    * types on the same resource combine with OR semantics.
    */
   private static void addGranteeVarietyFixtures(SecurityTestDataBuilder b) {
      b.addRole(GRANTEE_ROLE, ORG_ID)
       .addGroup(GRANTEE_GROUP, ORG_ID)
       .addGroup(GRANTEE_SUBGROUP, ORG_ID)
       .addGroupParent(GRANTEE_SUBGROUP, GRANTEE_GROUP, ORG_ID)

       .addUser(TARGET_USER_2, ORG_ID, "password")
       .addUser(TARGET_USER_3, ORG_ID, "password")
       .addUser("viaRoleUser", ORG_ID, "password")
       .addUser("noRoleUser", ORG_ID, "password")
       .addUser("viaGroupUser", ORG_ID, "password")
       .addUser("viaSubGroupUser", ORG_ID, "password")
       .addUser("viaAnyOneOfThreeUser", ORG_ID, "password")

       .addUserToRole("viaRoleUser", GRANTEE_ROLE, ORG_ID)
       .addUserToRole("viaAnyOneOfThreeUser", GRANTEE_ROLE, ORG_ID)
       // noRoleUser deliberately does NOT hold GRANTEE_ROLE -- negative control.
       .addUserToGroup("viaGroupUser", GRANTEE_GROUP, ORG_ID)
       // viaSubGroupUser is only a direct member of the CHILD group1, not group0 itself.
       .addUserToGroup("viaSubGroupUser", GRANTEE_SUBGROUP, ORG_ID)

       // targetUser2: granted to a ROLE grantee (role0) and a GROUP grantee (group0).
       .grantPermission(ResourceType.SECURITY_USER, TARGET_USER_2, ResourceAction.ADMIN,
                        GRANTEE_ROLE, Identity.ROLE, ORG_ID)
       .grantPermission(ResourceType.SECURITY_USER, TARGET_USER_2, ResourceAction.ADMIN,
                        GRANTEE_GROUP, Identity.GROUP, ORG_ID)

       // targetUser3: granted to a USER, a ROLE, and a GROUP at once (OR semantics).
       // viaAnyOneOfThreeUser only matches the ROLE arm; UNRELATED_USER_3/UNRELATED_GROUP_3
       // are placeholder grantees standing in for the two arms that don't apply to it.
       .grantPermission(ResourceType.SECURITY_USER, TARGET_USER_3, ResourceAction.ADMIN,
                        UNRELATED_USER_3, Identity.USER, ORG_ID)
       .grantPermission(ResourceType.SECURITY_USER, TARGET_USER_3, ResourceAction.ADMIN,
                        GRANTEE_ROLE, Identity.ROLE, ORG_ID)
       .grantPermission(ResourceType.SECURITY_USER, TARGET_USER_3, ResourceAction.ADMIN,
                        UNRELATED_GROUP_3, Identity.GROUP, ORG_ID);
   }

   /**
    * S2.3 — Groups: identityAdminGroupInstUser (instance), rootGroupAdmin (root cascade), and
    * S2-GROUP-CHAIN (ADMIN on a group resource cascades down its descendant-group chain).
    */
   private static void addGroupsFixtures(SecurityTestDataBuilder b) {
      b.addUser("identityAdminGroupInstUser", ORG_ID, "password")
       .addUser("rootGroupAdmin", ORG_ID, "password")

       .grantPermission(ResourceType.SECURITY_GROUP, TARGET_GROUP, ResourceAction.ADMIN,
                        "identityAdminGroupInstUser", Identity.USER, ORG_ID)
       .grantPermission(ResourceType.SECURITY_GROUP,
                        new IdentityID("Groups", ORG_ID).convertToKey(),
                        ResourceAction.ADMIN, "rootGroupAdmin", Identity.USER, ORG_ID);

      addGroupChainFixtures(b);
   }

   /**
    * S2-GROUP-CHAIN: a 3-level group chain (CHAIN_GROUP_0 -> CHAIN_GROUP_1 -> CHAIN_GROUP_2) plus
    * an unrelated sibling group. Unlike S2-GRANTEE-VARIETY above (grantee is a group, login user
    * is a member of it), here ADMIN is granted directly on a group RESOURCE and the checked
    * resource is a descendant group -- DefaultCheckPermissionStrategy walks the checked
    * resource's own ancestor-group chain looking for a match. A ≥3-level chain is needed to
    * distinguish a real multi-hop walk from a single-parent check.
    */
   private static void addGroupChainFixtures(SecurityTestDataBuilder b) {
      b.addGroup(CHAIN_GROUP_0, ORG_ID)
       .addGroup(CHAIN_GROUP_1, ORG_ID)
       .addGroup(CHAIN_GROUP_2, ORG_ID)
       .addGroup(CHAIN_SIBLING_GROUP, ORG_ID)
       .addGroupParent(CHAIN_GROUP_1, CHAIN_GROUP_0, ORG_ID)
       .addGroupParent(CHAIN_GROUP_2, CHAIN_GROUP_1, ORG_ID)

       .addUser("chainAdmin", ORG_ID, "password")
       .addUser("midChainAdmin", ORG_ID, "password")

       // Resource key MUST be the converted IdentityID key (new IdentityID(name,
       // orgId).convertToKey()), NOT the bare group name used elsewhere in this file for
       // direct-instance checks: the descendant-side ancestor walk that discovers this grant
       // (getSecurityResourceParents()) reconstructs each ancestor in that converted format.
       .grantPermission(ResourceType.SECURITY_GROUP,
                        new IdentityID(CHAIN_GROUP_0, ORG_ID).convertToKey(),
                        ResourceAction.ADMIN, "chainAdmin", Identity.USER, ORG_ID)
       .grantPermission(ResourceType.SECURITY_GROUP,
                        new IdentityID(CHAIN_GROUP_1, ORG_ID).convertToKey(),
                        ResourceAction.ADMIN, "midChainAdmin", Identity.USER, ORG_ID);
   }

   /**
    * S2.4 — Organization Roles/Roles: identityAdminRoleUser (ASSIGN, not ADMIN), rootRoleAdmin
    * (org root cascade), rootGlobalRoleAdmin (S2-GLOBAL-ROLE-ROOT global root).
    */
   private static void addRolesFixtures(SecurityTestDataBuilder b) {
      b.addUser("identityAdminRoleUser", ORG_ID, "password")
       .addUser("rootRoleAdmin", ORG_ID, "password")
       .addUser("rootGlobalRoleAdmin", ORG_ID, "password")

       .grantPermission(ResourceType.SECURITY_ROLE, TARGET_ROLE, ResourceAction.ASSIGN,
                        "identityAdminRoleUser", Identity.USER, ORG_ID)
       .grantPermission(ResourceType.SECURITY_ROLE,
                        new IdentityID("Organization Roles", ORG_ID).convertToKey(),
                        ResourceAction.ADMIN, "rootRoleAdmin", Identity.USER, ORG_ID)
       // "Roles" is the global root -- its OWN storage key is still IdentityID("Roles", ORG_ID)
       // (current org, not org=null); DefaultCheckPermissionStrategy routes global (org-less)
       // roles to check against THIS root instead of "Organization Roles".
       .grantPermission(ResourceType.SECURITY_ROLE,
                        new IdentityID("Roles", ORG_ID).convertToKey(),
                        ResourceAction.ADMIN, "rootGlobalRoleAdmin", Identity.USER, ORG_ID);
   }

   private static void initPrincipals() {
      orgSecurityAdmin = builder.principalOf("orgSecurityAdmin", ORG_ID);
      identityAdminUser = builder.principalOf("identityAdminUser", ORG_ID);
      identityAdminWildUser = builder.principalOf("identityAdminWildUser", ORG_ID);
      identityAdminGroupInstUser = builder.principalOf("identityAdminGroupInstUser", ORG_ID);
      identityAdminRoleUser = builder.principalOf("identityAdminRoleUser", ORG_ID);
      rootUserAdmin = builder.principalOf("rootUserAdmin", ORG_ID);
      rootGroupAdmin = builder.principalOf("rootGroupAdmin", ORG_ID);
      rootRoleAdmin = builder.principalOf("rootRoleAdmin", ORG_ID);
      rootGlobalRoleAdmin = builder.principalOf("rootGlobalRoleAdmin", ORG_ID);
      viaRoleUser = builder.principalOf("viaRoleUser", ORG_ID);
      noRoleUser = builder.principalOf("noRoleUser", ORG_ID);
      viaGroupUser = builder.principalOf("viaGroupUser", ORG_ID);
      viaSubGroupUser = builder.principalOf("viaSubGroupUser", ORG_ID);
      viaAnyOneOfThreeUser = builder.principalOf("viaAnyOneOfThreeUser", ORG_ID);
      chainAdmin = builder.principalOf("chainAdmin", ORG_ID);
      midChainAdmin = builder.principalOf("midChainAdmin", ORG_ID);
   }

   @AfterAll
   static void tearDown() {
      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   // ════════════════════════════════════════════════════════════════════════════
   // S2.1 — Organization (orgSecurityAdmin cross-type cascade)
   // ════════════════════════════════════════════════════════════════════════════
   //
   // The cascade check resolves "current org" via OrganizationManager, which is backed by
   // ThreadContext rather than the principal argument passed to checkPermission(), so these
   // assertions run with the principal set as the thread's context principal.

   @Test
   void orgSecurityAdmin_adminOnOwnOrgSecurityOrganization_allowed() {
      withContextPrincipal(orgSecurityAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_ORGANIZATION,
                     new IdentityID(ORG_NAME, ORG_ID).convertToKey())
               .expectAllow(orgSecurityAdmin, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void orgSecurityAdmin_noContentPermission_assetReadDenied() {
      withContextPrincipal(orgSecurityAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_RESOURCE)
               .expectDeny(orgSecurityAdmin, ResourceAction.READ)
            .verify());
   }

   // Parameterized: same principal/ThreadContext wrapper/ADMIN action across all three resource
   // types -- the SECURITY_ORGANIZATION-ADMIN cascade applies to SECURITY_USER/GROUP/ROLE alike,
   // without a direct grant on any of them. orgOwnedRoleAdmin_allowedWithoutDirectGrant covers an
   // org-scoped role; the global-role case is covered separately below (denied).
   @ParameterizedTest(name = "{0}")
   @MethodSource("crossTypeCascadeCases")
   void orgSecurityAdmin_crossTypeCascade_allowedWithoutDirectGrant(
      String caseName, ResourceType type, String resource)
   {
      withContextPrincipal(orgSecurityAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(type, resource)
               .expectAllow(orgSecurityAdmin, ResourceAction.ADMIN)
            .verify());
   }

   private static Stream<Arguments> crossTypeCascadeCases() {
      return Stream.of(
         Arguments.of("securityUserAdmin_allowedWithoutDirectGrant",
                      ResourceType.SECURITY_USER, TARGET_USER),
         Arguments.of("securityGroupAdmin_allowedWithoutDirectGrant",
                      ResourceType.SECURITY_GROUP, ANOTHER_GROUP),
         Arguments.of("orgOwnedRoleAdmin_allowedWithoutDirectGrant",
                      ResourceType.SECURITY_ROLE, ANOTHER_ROLE)
      );
   }

   @Test
   void orgSecurityAdmin_crossTypeCascade_grantsOriginalAction_notAdminOnly() {
      // Proves the cascade allows the ORIGINALLY requested action (WRITE, ASSIGN), not just
      // ADMIN — i.e. it is "allow this action outright", not "implicitly promote to ADMIN and
      // fall back through the RWD bundle".
      withContextPrincipal(orgSecurityAdmin, () -> {
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_USER, TARGET_USER)
               .expectAllow(orgSecurityAdmin, ResourceAction.WRITE)
            .resource(ResourceType.SECURITY_ROLE, ANOTHER_ROLE)
               .expectAllow(orgSecurityAdmin, ResourceAction.ASSIGN)
            .verify();
      });
   }

   // Negative control: orgSecurityAdmin's SECURITY_ORGANIZATION-ADMIN cascade does not extend to
   // global (org-less) roles.
   //
   // Resource key must be GLOBAL_ROLE's own convertToKey() output (name + org=null): a bare
   // "globalRole0" string would resolve its org from ThreadContext instead, looking up a
   // *different*, non-existent IdentityID and defeating the negative control.
   @Test
   void orgSecurityAdmin_adminOnGlobalRole_denied_negativeControl() {
      withContextPrincipal(orgSecurityAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_ROLE, new IdentityID(GLOBAL_ROLE, null).convertToKey())
               .expectDeny(orgSecurityAdmin, ResourceAction.ADMIN)
            .verify());
   }

   // Companion negative control for the real built-in sysAdmin-flagged "Administrator" role, not
   // just any org-less role.
   @Test
   void orgSecurityAdmin_adminOnGlobalSysAdminRole_denied_negativeControl() {
      withContextPrincipal(orgSecurityAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_ROLE,
                     new IdentityID(GLOBAL_SYSADMIN_ROLE, null).convertToKey())
               .expectDeny(orgSecurityAdmin, ResourceAction.ADMIN)
            .verify());
   }

   // ════════════════════════════════════════════════════════════════════════════
   // S2.2 — Users
   // ════════════════════════════════════════════════════════════════════════════

   // ── identityAdmin-user(instance) ──────────────────────────────────────────────
   //
   // Every check below is wrapped in withContextPrincipal, even the ones that never grant ADMIN
   // directly: DefaultCheckPermissionStrategy's ADMIN-cumulative branch resolves the storage org
   // via a 2-arg provider.getPermission(type, resource) overload that falls back to
   // OrganizationManager's ThreadContext-backed "current org", and SecurityEngine.checkPermission()
   // always retries a denied non-ADMIN action with ADMIN, so that resolution matters for every
   // check here, not just the ones asserting ADMIN directly.

   @Test
   void identityAdminUser_adminOnTargetUser_allowed() {
      withContextPrincipal(identityAdminUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_USER, TARGET_USER)
               .expectAllow(identityAdminUser, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void identityAdminUser_adminOnAnotherUser_denied_doesNotCrossInstance() {
      withContextPrincipal(identityAdminUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_USER, ANOTHER_USER)
               .expectDeny(identityAdminUser, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void identityAdminUser_writeAndDeleteOnTargetUser_allowed_adminImpliesRWD() {
      // ADMIN -> READ/WRITE/DELETE fallback lives in SecurityEngine.checkPermission() (it
      // retries with ADMIN whenever the originally requested action is denied), not in this
      // fixture's grant data.
      withContextPrincipal(identityAdminUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_USER, TARGET_USER)
               .expectAllow(identityAdminUser, ResourceAction.WRITE, ResourceAction.DELETE)
            .verify());
   }

   // ── identityAdmin-user(wildcard) ────────────────────────────────────────────────

   @ParameterizedTest(name = "{0}")
   @MethodSource("wildcardUserAdminCases")
   void identityAdminWildUser_adminOnAnyUser_allowed(String caseName, String targetUser) {
      withContextPrincipal(identityAdminWildUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_USER, targetUser)
               .expectAllow(identityAdminWildUser, ResourceAction.ADMIN)
            .verify());
   }

   private static Stream<Arguments> wildcardUserAdminCases() {
      return Stream.of(
         Arguments.of("targetUser_allowed", TARGET_USER),
         Arguments.of("anotherUser_allowed_notInstanceScoped", ANOTHER_USER)
      );
   }

   @Test
   void identityAdminWildUser_noContentPermission_assetReadDenied() {
      withContextPrincipal(identityAdminWildUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_RESOURCE)
               .expectDeny(identityAdminWildUser, ResourceAction.READ)
            .verify());
   }

   // ── root cascade (Users) ───────────────────────────────────────────────────────
   //
   // "Users" root ADMIN cascades to EVERY user instance (DefaultCheckPermissionStrategy L134-186
   // checks the synthetic root unconditionally, before any per-resource grant) -- a third,
   // independent mechanism distinct from both the SECURITY_ORGANIZATION cascade (S2.1) and the
   // wildcard above.

   @ParameterizedTest(name = "{0}")
   @MethodSource("rootUserCascadeCases")
   void rootUserAdmin_adminOnAnyUser_allowed(String caseName, String targetUser) {
      withContextPrincipal(rootUserAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_USER, targetUser)
               .expectAllow(rootUserAdmin, ResourceAction.ADMIN)
            .verify());
   }

   private static Stream<Arguments> rootUserCascadeCases() {
      return Stream.of(
         Arguments.of("targetUser_allowed", TARGET_USER),
         Arguments.of("anotherUser_allowed_notLimitedToConfiguredInstances", ANOTHER_USER)
      );
   }

   // ── S2-GRANTEE-VARIETY ──────────────────────────────────────────────────────

   @Test
   void viaRoleUser_adminOnTargetUser2_allowed_grantedToRoleNotUser() {
      withContextPrincipal(viaRoleUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_USER, TARGET_USER_2)
               .expectAllow(viaRoleUser, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void noRoleUser_adminOnTargetUser2_denied_doesNotHoldGrantedRole() {
      // Negative control: same targetUser2 grant (role0), but this principal never holds role0.
      withContextPrincipal(noRoleUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_USER, TARGET_USER_2)
               .expectDeny(noRoleUser, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void viaGroupUser_adminOnTargetUser2_allowed_grantedToGroupDirectMember() {
      withContextPrincipal(viaGroupUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_USER, TARGET_USER_2)
               .expectAllow(viaGroupUser, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void viaSubGroupUser_adminOnTargetUser2_allowed_inheritsThroughParentGroup() {
      // viaSubGroupUser is a member of group1 (a CHILD of the granted group0), not group0 itself
      // -- PermissionChecker.checkUserGroupPermission walks group1's own parent-group chain
      // (FSGroup.getGroups()) up to group0 and finds the grant there.
      withContextPrincipal(viaSubGroupUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_USER, TARGET_USER_2)
               .expectAllow(viaSubGroupUser, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void viaAnyOneOfThreeUser_adminOnTargetUser3_allowed_orSemanticsMatchesOneOfThree() {
      // targetUser3's grant has three simultaneous grantees (a USER, role0, and a GROUP); this
      // principal only holds role0 -- the other two arms belong to identities it has no relation
      // to. Default OR semantics means matching just the role arm is enough.
      withContextPrincipal(viaAnyOneOfThreeUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_USER, TARGET_USER_3)
               .expectAllow(viaAnyOneOfThreeUser, ResourceAction.ADMIN)
            .verify());
   }

   // ════════════════════════════════════════════════════════════════════════════
   // S2.3 — Groups
   // ════════════════════════════════════════════════════════════════════════════

   // ── identityAdmin-group(instance) ─────────────────────────────────────────────
   //
   // The checked principal here is always a plain USER, so no actual Group entity needs to
   // exist for "targetGroup"/"anotherGroup" -- SECURITY_GROUP resource paths are just
   // permission-storage keys in this scenario, not real group lookups.

   @Test
   void identityAdminGroupInstUser_adminOnTargetGroup_allowed() {
      withContextPrincipal(identityAdminGroupInstUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_GROUP, TARGET_GROUP)
               .expectAllow(identityAdminGroupInstUser, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void identityAdminGroupInstUser_adminOnAnotherGroup_denied_doesNotCrossInstance() {
      withContextPrincipal(identityAdminGroupInstUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_GROUP, ANOTHER_GROUP)
               .expectDeny(identityAdminGroupInstUser, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void identityAdminGroupInstUser_writeOnTargetGroup_allowed_adminImpliesRWD() {
      withContextPrincipal(identityAdminGroupInstUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_GROUP, TARGET_GROUP)
               .expectAllow(identityAdminGroupInstUser, ResourceAction.WRITE)
            .verify());
   }

   // ── root cascade (Groups) ──────────────────────────────────────────────────────

   @ParameterizedTest(name = "{0}")
   @MethodSource("rootGroupCascadeCases")
   void rootGroupAdmin_adminOnAnyGroup_allowed(String caseName, String targetGroup) {
      withContextPrincipal(rootGroupAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_GROUP, targetGroup)
               .expectAllow(rootGroupAdmin, ResourceAction.ADMIN)
            .verify());
   }

   private static Stream<Arguments> rootGroupCascadeCases() {
      return Stream.of(
         Arguments.of("targetGroup_allowed", TARGET_GROUP),
         Arguments.of("anotherGroup_allowed_notLimitedToConfiguredInstances", ANOTHER_GROUP)
      );
   }

   // ── S2-GROUP-CHAIN ──────────────────────────────────────────────────────────
   //
   // ADMIN granted directly on a group RESOURCE cascades down to its descendant groups (walking
   // the resource's OWN ancestor-group chain, DefaultCheckPermissionStrategy L339-366 /
   // getSecurityResourceParents()), but never climbs back up to ancestors or the "Groups" root.

   @Test
   void chainAdmin_adminOnChildGroup_allowed_oneHopDown() {
      withContextPrincipal(chainAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_GROUP, CHAIN_GROUP_1)
               .expectAllow(chainAdmin, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void chainAdmin_adminOnGrandchildGroup_allowed_twoHopsDown() {
      withContextPrincipal(chainAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_GROUP, CHAIN_GROUP_2)
               .expectAllow(chainAdmin, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void chainAdmin_adminOnUnrelatedSiblingGroup_denied_notOnChain() {
      withContextPrincipal(chainAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_GROUP, CHAIN_SIBLING_GROUP)
               .expectDeny(chainAdmin, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void midChainAdmin_adminOnChildGroup_allowed_stillCascadesDownward() {
      // midChainAdmin only holds ADMIN on the MIDDLE group (CHAIN_GROUP_1), not the root -- the
      // downward cascade to its own child (CHAIN_GROUP_2) still applies.
      withContextPrincipal(midChainAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_GROUP, CHAIN_GROUP_2)
               .expectAllow(midChainAdmin, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void midChainAdmin_adminOnParentGroup_denied_doesNotClimbUpward() {
      withContextPrincipal(midChainAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_GROUP, CHAIN_GROUP_0)
               .expectDeny(midChainAdmin, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void midChainAdmin_adminOnGroupsRoot_denied_doesNotClimbToRoot() {
      withContextPrincipal(midChainAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_GROUP, new IdentityID("Groups", ORG_ID).convertToKey())
               .expectDeny(midChainAdmin, ResourceAction.ADMIN)
            .verify());
   }

   // ════════════════════════════════════════════════════════════════════════════
   // S2.4 — Organization Roles / Roles
   // ════════════════════════════════════════════════════════════════════════════

   // ── identityAdmin-role (ASSIGN, not ADMIN) ────────────────────────────────────

   @Test
   void identityAdminRoleUser_assignOnTargetRole_allowed() {
      withContextPrincipal(identityAdminRoleUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_ROLE, TARGET_ROLE)
               .expectAllow(identityAdminRoleUser, ResourceAction.ASSIGN)
            .verify());
   }

   // Key negative path: ASSIGN does not imply WRITE.
   @Test
   void identityAdminRoleUser_writeOnTargetRole_denied_assignDoesNotImplyWrite() {
      withContextPrincipal(identityAdminRoleUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_ROLE, TARGET_ROLE)
               .expectDeny(identityAdminRoleUser, ResourceAction.WRITE)
            .verify());
   }

   @Test
   void identityAdminRoleUser_deleteOnTargetRole_denied_assignDoesNotImplyDelete() {
      withContextPrincipal(identityAdminRoleUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_ROLE, TARGET_ROLE)
               .expectDeny(identityAdminRoleUser, ResourceAction.DELETE)
            .verify());
   }

   @Test
   void identityAdminRoleUser_assignOnAnotherRole_denied_doesNotCrossInstance() {
      withContextPrincipal(identityAdminRoleUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_ROLE, ANOTHER_ROLE)
               .expectDeny(identityAdminRoleUser, ResourceAction.ASSIGN)
            .verify());
   }

   @Test
   void identityAdminRoleUser_noContentPermission_assetReadDenied() {
      withContextPrincipal(identityAdminRoleUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_RESOURCE)
               .expectDeny(identityAdminRoleUser, ResourceAction.READ)
            .verify());
   }

   // ── root cascade (Organization Roles) ─────────────────────────────────────────

   @Test
   void rootRoleAdmin_assignOnTargetRole_allowed() {
      withContextPrincipal(rootRoleAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_ROLE, TARGET_ROLE)
               .expectAllow(rootRoleAdmin, ResourceAction.ASSIGN)
            .verify());
   }

   @Test
   void rootRoleAdmin_writeOnTargetRole_allowed_rootAdminImpliesRWD() {
      // Root-level ADMIN (unlike an individual role's ASSIGN-only "Administrator Permissions"
      // grant, see identityAdminRoleUser above) DOES get WRITE/DELETE -- the root check reuses
      // the same Permission object regardless of the originally-requested action, so a root
      // ADMIN grant covers WRITE the same way it covered ASSIGN above.
      withContextPrincipal(rootRoleAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_ROLE, TARGET_ROLE)
               .expectAllow(rootRoleAdmin, ResourceAction.WRITE)
            .verify());
   }

   // ── S2-GLOBAL-ROLE-ROOT ──────────────────────────────────────────────────────
   //
   // "Roles" (global root) and "Organization Roles" (org root) are independent: ADMIN on one does
   // not extend to roles owned by the other.

   @Test
   void rootGlobalRoleAdmin_adminOnGlobalRole_allowed() {
      withContextPrincipal(rootGlobalRoleAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_ROLE, new IdentityID(GLOBAL_ROLE, null).convertToKey())
               .expectAllow(rootGlobalRoleAdmin, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void rootGlobalRoleAdmin_adminOnOrgRole_denied_rootsShouldBeIndependent() {
      withContextPrincipal(rootGlobalRoleAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_ROLE, TARGET_ROLE)
               .expectDeny(rootGlobalRoleAdmin, ResourceAction.ADMIN)
            .verify());
   }

   // Opposite direction: the org root (Organization Roles) does not extend to global roles.
   @Test
   void rootRoleAdmin_adminOnGlobalRole_denied_rootsShouldBeIndependent() {
      withContextPrincipal(rootRoleAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_ROLE, new IdentityID(GLOBAL_ROLE, null).convertToKey())
               .expectDeny(rootRoleAdmin, ResourceAction.ADMIN)
            .verify());
   }

   // ── helpers ────────────────────────────────────────────────────────────────

   /**
    * Runs {@code action} with {@code principal} set as the thread's context principal, then
    * always restores {@code null}. Needed for assertions that depend on
    * {@link OrganizationManager}'s ThreadContext-backed "current org" resolution.
    */
   private static void withContextPrincipal(SRPrincipal principal, Runnable action) {
      ThreadContext.setContextPrincipal(principal);

      try {
         action.run();
      }
      finally {
         ThreadContext.setContextPrincipal(null);
      }
   }

   private static SecurityEngine engine() {
      return SecurityEngine.getSecurity();
   }
}
