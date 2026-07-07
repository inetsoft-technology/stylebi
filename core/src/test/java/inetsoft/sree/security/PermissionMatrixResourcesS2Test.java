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
 * boundary). Sibling slices S3/S4/S5 each get their own PermissionMatrixResourcesS{N}Test class
 * instead of one combined PermissionMatrixResourcesTest — S2 alone already spans ~40 planned
 * cases across five sub-scenarios (main table, GRANTEE-VARIETY, GLOBAL-ROLE-ROOT, ROOT-CASCADE,
 * GROUP-CHAIN), and S2-S5 test genuinely different production mechanisms (identity cascade vs.
 * ADMIN parent/child rules vs. content-access links vs. folder inheritance) that share little
 * fixture setup, so splitting loses no reuse. See docs/superpowers/plans/
 * 2026-06-30-permission-test-phase2.md "Revision note" for why this replaced the original
 * single-file MatrixTestCase/UserType parameterized design.
 *
 * Covers permission-matrix-resources.md — S2 main table (SECURITY_* boundary), first batch:
 *   - orgSecurityAdmin cross-type cascade (DefaultCheckPermissionStrategy L57-67)
 *   - orgSecurityAdmin global-role negative controls (both @Disabled — CONFIRMED PRODUCTION
 *     BUG, not a fixture artifact: reproduced live via direct API call against a real EM
 *     deployment, bypassing the UI's tree-visibility hiding. See
 *     orgSecurityAdmin_adminOnGlobalRole_denied_negativeControl's comment for the confirmed
 *     mechanism (checkOrgAdminPermission() L591) and
 *     orgSecurityAdmin_adminOnGlobalSysAdminRole_denied_negativeControl's comment for an
 *     unresolved second discrepancy (live-confirmed but not yet reproduced in this fixture))
 *   - identityAdmin-user(instance), identityAdmin-user(wildcard), identityAdmin-group(instance),
 *     identityAdmin-role (its WRITE/DELETE negative-control cases were @Disabled pending Issue
 *     #75567 — ASSIGN was being keyed instead of ADMIN in DefaultCheckPermissionStrategy L232-240,
 *     silently upgrading an ASSIGN-only grant to full WRITE/DELETE/ADMIN; fixed by commit
 *     b9049488a and re-enabled)
 *   - S2-ROOT-CASCADE (rootUserAdmin/rootGroupAdmin/rootRoleAdmin) — the "Users"/"Groups"/
 *     "Organization Roles" synthetic root resources, a THIRD independent cascade mechanism
 *     distinct from both the SECURITY_ORGANIZATION cascade (orgSecurityAdmin) and the
 *     SECURITY_USER-only wildcard (identityAdminWildUser) above, even though all three end up
 *     looking like "manages every instance of this type"
 *   - S2-GLOBAL-ROLE-ROOT (rootGlobalRoleAdmin, plus two @Disabled cases reusing rootRoleAdmin/
 *     rootGlobalRoleAdmin) — CONFIRMED DESIGN VIOLATION distinct from Issue #75574: the "Roles"
 *     (global) and "Organization Roles" (org) roots are supposed to be independent but leak into
 *     each other for the ADMIN action. See
 *     rootGlobalRoleAdmin_adminOnOrgRole_denied_rootsShouldBeIndependent's comment for the
 *     confirmed mechanism (the private getPermission() ADMIN-cumulative helper, L744-763, merges
 *     both roots' grants into one set)
 *
 * identityAdmin-user WILDCARD (verified): the production wildcard lookup
 * (DefaultCheckPermissionStrategy L369-376) keys the permission by resource=orgID (the literal
 * org ID string), not "*" and not an IdentityID key — confirmed by tracing
 * provider.getPermission(SECURITY_USER, orgID)'s 2-arg-to-3-arg delegation down to
 * AuthorizationChain.fixOrgID(). See identityAdminWildUser_adminOnAnyUser_allowed's fixture
 * comment in setUp() for the exact mechanism.
 *
 * S2-ROOT-CASCADE (verified): all three roots use the same
 * IdentityID(rootName, orgId).convertToKey() resource-key convention (DefaultCheckPermissionStrategy
 * L134-186) — confirmed Organization.getRootOrgRoleName()/getRootRoleName() both already return
 * a pre-converted key string, so there's no special-casing needed for SECURITY_ROLE vs
 * SECURITY_USER/SECURITY_GROUP here (unlike the wildcard convention above, which genuinely does
 * differ by type).
 *
 * Deliberately NOT covered yet (left for a later batch):
 *   - identityAdmin-group WILDCARD grants (SECURITY_GROUP) — has no equivalent wildcard block
 *     in DefaultCheckPermissionStrategy near the SECURITY_USER one (L369-376 is SECURITY_USER-
 *     only); needs its own spike to confirm whether/how group wildcards are keyed before
 *     encoding a fixture, don't assume it mirrors the user convention just verified.
 *   - S2-GRANTEE-VARIETY / S2-GLOBAL-ROLE-ROOT / S2-GROUP-CHAIN — these are
 *     separate scenarios in the design doc, not part of the main table.
 *
 * Note on identityAdmin-group(instance): the checked principal here is always a plain USER,
 * so no actual Group entity needs to exist for "targetGroup"/"anotherGroup" — SECURITY_GROUP
 * resource paths are just permission-storage keys in this scenario, not real group lookups.
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

   private static final String TARGET_USER = "targetUser";
   private static final String ANOTHER_USER = "anotherUser";
   private static final String TARGET_GROUP = "targetGroup";
   private static final String ANOTHER_GROUP = "anotherGroup";
   private static final String TARGET_ROLE = "targetRole";
   private static final String ANOTHER_ROLE = "anotherRole";
   private static final String GLOBAL_ROLE = "globalRole0";
   private static final String GLOBAL_SYSADMIN_ROLE = "globalSysAdminRole0";
   private static final String ASSET_RESOURCE = "mx/folder/item";

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

   @BeforeAll
   static void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg(ORG_NAME, ORG_ID)

         .addRole(TARGET_ROLE, ORG_ID)
         .addRole(ANOTHER_ROLE, ORG_ID)
         // Negative control for orgSecurityAdmin's cascade: a role with NO organization
         // (IdentityID(name, null)), matching how built-in Administrator/Organization
         // Administrator roles are constructed. isNotGlobalRole() should exclude it.
         .addGlobalRole(GLOBAL_ROLE)
         // Mirrors the REAL built-in "Administrator" role: org=null AND sysAdmin=true
         // (FileAuthenticationProvider.java L990: new IdentityID("Administrator", null)).
         .addSysAdminRole(GLOBAL_SYSADMIN_ROLE, null)

         .addUser("orgSecurityAdmin", ORG_ID, "password")
         .addUser("identityAdminUser", ORG_ID, "password")
         .addUser("identityAdminWildUser", ORG_ID, "password")
         .addUser(TARGET_USER, ORG_ID, "password")
         .addUser(ANOTHER_USER, ORG_ID, "password")
         .addUser("identityAdminGroupInstUser", ORG_ID, "password")
         .addUser("identityAdminRoleUser", ORG_ID, "password")
         .addUser("rootUserAdmin", ORG_ID, "password")
         .addUser("rootGroupAdmin", ORG_ID, "password")
         .addUser("rootRoleAdmin", ORG_ID, "password")
         .addUser("rootGlobalRoleAdmin", ORG_ID, "password")

         // orgSecurityAdmin fixture mirrors the exact repro confirmed live and filed as
         // Issue #75574: a user with NO role assignment at all, granted EM access directly
         // (via Security Actions, not through any role) plus "Administrator Permission" set
         // on the org node in Security > Users (= SECURITY_ORGANIZATION ADMIN on this org,
         // identity type USER). The EM grant isn't consumed by any assertion in this class
         // (assertions call SecurityEngine.checkPermission() directly for SECURITY_ROLE, the
         // same check RoleController.getRole()'s @Secured gate performs -- the EM_COMPONENT
         // half of that gate is a separate, orthogonal check), but it's included so this
         // fixture matches the confirmed real-world persona 1:1, in case a future controller-
         // level test needs it.
         .grantPermission(ResourceType.EM, "*", ResourceAction.ACCESS,
                          "orgSecurityAdmin", Identity.USER, ORG_ID)

         // ADMIN on SECURITY_ORGANIZATION cascades to all SECURITY_USER/SECURITY_GROUP
         // instances and org-owned SECURITY_ROLE instances (see DefaultCheckPermissionStrategy
         // L57-67). That check reads the permission via
         // provider.getPermission(SECURITY_ORGANIZATION, IdentityID(orgName, orgId)), so the
         // resource key here must equal that IdentityID's convertToKey() output, not the bare
         // org id.
         .grantPermission(ResourceType.SECURITY_ORGANIZATION,
                          new IdentityID(ORG_NAME, ORG_ID).convertToKey(),
                          ResourceAction.ADMIN, "orgSecurityAdmin", Identity.USER, ORG_ID)

         // identityAdmin-user(instance): ADMIN on one specific SECURITY_USER instance
         .grantPermission(ResourceType.SECURITY_USER, TARGET_USER, ResourceAction.ADMIN,
                          "identityAdminUser", Identity.USER, ORG_ID)

         // identityAdmin-user(wildcard): ADMIN on the SECURITY_USER "wildcard", which the
         // production wildcard check (DefaultCheckPermissionStrategy L369-376) looks up via
         // provider.getPermission(SECURITY_USER, orgID) -- a 2-arg call that resolves to
         // getPermission(SECURITY_USER, orgID-as-resource-string, null), and the null org
         // param gets fixed up to OrganizationManager.getCurrentOrgID() (i.e. whatever
         // ThreadContext's current org is at check time, via withContextPrincipal). So the
         // resource key to grant against is the literal org ID string, NOT "*" and NOT an
         // IdentityID(...).convertToKey() -- a completely different convention from
         // orgSecurityAdmin's SECURITY_ORGANIZATION grant above.
         .grantPermission(ResourceType.SECURITY_USER, ORG_ID, ResourceAction.ADMIN,
                          "identityAdminWildUser", Identity.USER, ORG_ID)

         // identityAdmin-group(instance): ADMIN on one specific SECURITY_GROUP instance
         .grantPermission(ResourceType.SECURITY_GROUP, TARGET_GROUP, ResourceAction.ADMIN,
                          "identityAdminGroupInstUser", Identity.USER, ORG_ID)

         // identityAdmin-role: ASSIGN (not ADMIN) on one specific SECURITY_ROLE instance
         .grantPermission(ResourceType.SECURITY_ROLE, TARGET_ROLE, ResourceAction.ASSIGN,
                          "identityAdminRoleUser", Identity.USER, ORG_ID)

         // S2-ROOT-CASCADE: "Users"/"Groups"/"Organization Roles" are literal synthetic
         // resources DefaultCheckPermissionStrategy checks unconditionally before any
         // per-instance lookup (L134-186), independent of the SECURITY_ORGANIZATION mechanism
         // above (orgSecurityAdmin) and the SECURITY_USER-only wildcard above
         // (identityAdminWildUser) -- three separate mechanisms that all end up looking like
         // "manages every instance of this type". All three roots use the same
         // IdentityID(rootName, orgId).convertToKey() resource-key convention: Users/Groups via
         // the 2-arg IdentityID overload of getPermission() (which internally calls
         // convertToKey()), Organization Roles via Organization.getRootOrgRoleName(), which
         // itself returns `new IdentityID("Organization Roles", orgId).convertToKey()` already
         // converted to a String.
         .grantPermission(ResourceType.SECURITY_USER,
                          new IdentityID("Users", ORG_ID).convertToKey(),
                          ResourceAction.ADMIN, "rootUserAdmin", Identity.USER, ORG_ID)
         .grantPermission(ResourceType.SECURITY_GROUP,
                          new IdentityID("Groups", ORG_ID).convertToKey(),
                          ResourceAction.ADMIN, "rootGroupAdmin", Identity.USER, ORG_ID)
         .grantPermission(ResourceType.SECURITY_ROLE,
                          new IdentityID("Organization Roles", ORG_ID).convertToKey(),
                          ResourceAction.ADMIN, "rootRoleAdmin", Identity.USER, ORG_ID)

         // S2-GLOBAL-ROLE-ROOT: the "Roles" global root -- despite the name, its OWN storage key
         // is IdentityID("Roles", ORG_ID) (current org, not org=null); what makes it "global" is
         // that DefaultCheckPermissionStrategy routes global (org-less) roles to check against
         // THIS root, while org-scoped roles check against the "Organization Roles" root instead
         // (L141: `role.getOrganizationID() != null` decides which one).
         .grantPermission(ResourceType.SECURITY_ROLE,
                          new IdentityID("Roles", ORG_ID).convertToKey(),
                          ResourceAction.ADMIN, "rootGlobalRoleAdmin", Identity.USER, ORG_ID)

         .setup();

      orgSecurityAdmin = builder.principalOf("orgSecurityAdmin", ORG_ID);
      identityAdminUser = builder.principalOf("identityAdminUser", ORG_ID);
      identityAdminWildUser = builder.principalOf("identityAdminWildUser", ORG_ID);
      identityAdminGroupInstUser = builder.principalOf("identityAdminGroupInstUser", ORG_ID);
      identityAdminRoleUser = builder.principalOf("identityAdminRoleUser", ORG_ID);
      rootUserAdmin = builder.principalOf("rootUserAdmin", ORG_ID);
      rootGroupAdmin = builder.principalOf("rootGroupAdmin", ORG_ID);
      rootRoleAdmin = builder.principalOf("rootRoleAdmin", ORG_ID);
      rootGlobalRoleAdmin = builder.principalOf("rootGlobalRoleAdmin", ORG_ID);
   }

   @AfterAll
   static void tearDown() {
      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   // ── orgSecurityAdmin: cross-type cascade off SECURITY_ORGANIZATION ADMIN ──────
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

   // Same shape across all three cases (same principal, same ThreadContext wrapper, same
   // ADMIN action, plain-string resource key) — parameterized rather than three near-identical
   // @Test methods. Unlike the abandoned file-wide MatrixTestCase/UserType DSL (see class-level
   // javadoc), this doesn't need to encode cross-scenario fixture differences (ThreadContext
   // wrapping, convertToKey() keys) since every row here already shares them.
   //
   // The "orgOwnedRoleAdmin_allowedWithoutDirectGrant" case (ANOTHER_ROLE, an org-scoped role)
   // is the positive control confirming org-scoped role management works correctly for this
   // persona -- matches the live repro's "组织内 role 获取，正确" finding. Only the GLOBAL role
   // case (below, orgSecurityAdmin_adminOnGlobalRole_denied_negativeControl and its companion)
   // is the confirmed bug (Issue #75574).
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

   // Negative control: isNotGlobalRole() (DefaultCheckPermissionStrategy L489-493) blocks the
   // cascade's early-exit when the checked role exists and has no organizationID (i.e. is a
   // global role, org-less like the built-in Administrator/Organization Administrator). The
   // resource key must be GLOBAL_ROLE's own convertToKey() output (name + org=null) so that
   // IdentityID.getIdentityIDFromKey(resource) resolves back to the same org-less identity that
   // provider.getRole() was stored under -- a bare "globalRole0" string would instead resolve
   // its org from ThreadContext (via getIdentityIDFromKey's fallback branch), which would look
   // up a *different*, non-existent IdentityID(globalRole0, ORG_ID) and defeat the negative
   // control by making isNotGlobalRole() see "role not found" (also true) for the wrong reason.
   //
   // CONFIRMED PRODUCT BUG — Issue #75574. Not a JUnit-fixture artifact or a hypothesis pending
   // verification: reproduced live by product against a real deployment. Repro (org "org-test",
   // user "security-user" with NO role assignment at all, granted EM access directly via
   // Security Actions, and "Administrator Permission" set on the org-test org node in
   // Security > Users -- i.e. SECURITY_ORGANIZATION ADMIN, identity type USER, exactly this
   // fixture's orgSecurityAdmin): logged into that org's EM, the "Roles" (global) tree node is
   // correctly hidden in the UI, and fetching an org-scoped role is correctly allowed (see the
   // "orgOwnedRoleAdmin_allowedWithoutDirectGrant" positive control above) -- but a direct
   // browser-console call bypassing the UI,
   //   GET /sree/api/em/security/providers/Primary/roles/Organization%20Administrator~~_3b_~~__GLOBAL__/
   // returns HTTP 200 with the real role payload, `"editable":true` included.
   //
   // GLOBAL_ROLE (org=null, sysAdmin=false, orgAdmin=false) mirrors the real built-in
   // "Organization Administrator" role's shape (org=null, sysAdmin=false, orgAdmin=true --
   // FileAuthenticationProvider.java L1038-1041; sysAdmin is the flag that matters here, not
   // orgAdmin) used in that repro. Root cause traced in this fixture (temporary debug prints in
   // DefaultCheckPermissionStrategy.checkPermission(), reverted after diagnosis -- not left in
   // the production file): isNotGlobalRole() correctly evaluates to false and the L58-67
   // SECURITY_ORGANIZATION-ADMIN cascade is correctly skipped for GLOBAL_ROLE. The ALLOW instead
   // comes from a completely different, independent cascade: checkOrgAdminPermission()
   // (L525-616), called unconditionally later in the same method whenever the principal either
   // holds an org-admin role or -- as here -- has ADMIN on SECURITY_ORGANIZATION for the current
   // org. checkOrgAdminPermission()'s SECURITY_ROLE branch (L583-605) explicitly returns true for
   // org-less roles whose own sysAdmin flag is false:
   //
   //   return !isSiteAdmin && (currProvider.getRole(resourceID).getOrganizationID() == null ||
   //           orgID.equals(currProvider.getRole(resourceID).getOrganizationID()));
   //
   // (`isSiteAdmin` here means "is the CHECKED ROLE itself sysAdmin-flagged", not "is the calling
   // principal a site admin".) The `getOrganizationID() == null` disjunct is not a missing-guard
   // oversight like the ASSIGN/ADMIN mixup below -- it reads as a deliberate choice to treat
   // org-less, non-sysAdmin-flagged roles as manageable by every org's admin-permission holders.
   // Confirmed with the product owner that this is NOT intended: only site admin, or a user
   // holding the global "Administrator" role, should be able to manage global roles -- see
   // permission-matrix-resources.md for the tracked row.
   @Disabled("Issue #75574 — checkOrgAdminPermission()'s SECURITY_ROLE branch "
      + "(DefaultCheckPermissionStrategy L591) explicitly allows org-admin-permission holders to "
      + "manage org-less, non-sysAdmin global roles, bypassing the UI's tree-visibility "
      + "filtering; confirmed product bug, not yet fixed in production; re-enable once "
      + "DefaultCheckPermissionStrategy is patched")
   @Test
   void orgSecurityAdmin_adminOnGlobalRole_denied_negativeControl() {
      withContextPrincipal(orgSecurityAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_ROLE, new IdentityID(GLOBAL_ROLE, null).convertToKey())
               .expectDeny(orgSecurityAdmin, ResourceAction.ADMIN)
            .verify());
   }

   // Companion negative control mirroring the real built-in "Administrator" role's exact shape
   // (org=null, sysAdmin=TRUE -- FileAuthenticationProvider.java L1033-1036), not just any
   // org-less role. Same Issue #75574 repro: fetching this role live
   // (`GET .../roles/Administrator~~_3b_~~__GLOBAL__/`) also returned 200 with `"editable":true`
   // for the org-scoped orgSecurityAdmin persona -- confirmed product bug, same as GLOBAL_ROLE
   // above, not a hypothesis.
   //
   // This is deliberately a SEPARATE test from the one above, not a duplicate: this fixture's
   // reproduction of the exact same role shape (org=null, sysAdmin=true) currently returns deny
   // through every path in DefaultCheckPermissionStrategy, including checkOrgAdminPermission()'s
   // `!isSiteAdmin` guard, which is specifically written to protect sysAdmin-flagged roles and
   // does its job correctly here for BOTH actions RoleController.getRole() actually checks:
   // verified ADMIN (the @Secured gate's check, asserted below) AND ASSIGN (the separate check
   // at RoleController.java L232-236 that computes the `editableRoles` response field) both
   // return deny in this fixture. That rules out the leading hypothesis for why this fixture
   // doesn't reproduce the live leak. This is a real, currently-unexplained gap in this test's
   // realism, not a doubt about the bug itself (the live 200 + editable:true is confirmed,
   // independent of whether this fixture can reproduce it): something about the real/staging
   // deployment (additional grant, provider config, caching) differs from this isolated
   // single-provider fixture in a way not yet identified. The assertion below is written to the
   // DESIGN INTENT (deny) as the target regression guard; treat it as an incomplete guard for
   // Issue #75574's "Administrator" case specifically until someone with access to the live/
   // staging environment closes this gap -- don't assume re-enabling it later means the
   // production bug for this role is fixed.
   @Disabled("Issue #75574 — confirmed live that orgSecurityAdmin can view the real, "
      + "sysAdmin-flagged 'Administrator' global role via direct API call (200, editable:true) "
      + "-- but this exact JUnit reproduction returns deny for both ADMIN and ASSIGN actions "
      + "through every traced code path, contradicting the live result. Root cause not fully "
      + "isolated (see comment above); needs a live/staging debugging session, not just this "
      + "fixture, before this test can guard the regression")
   @Test
   void orgSecurityAdmin_adminOnGlobalSysAdminRole_denied_negativeControl() {
      withContextPrincipal(orgSecurityAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_ROLE,
                     new IdentityID(GLOBAL_SYSADMIN_ROLE, null).convertToKey())
               .expectDeny(orgSecurityAdmin, ResourceAction.ADMIN)
            .verify());
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

   // ── identityAdmin-user(instance) ──────────────────────────────────────────────
   //
   // Every check below is wrapped in withContextPrincipal, even the ones that never grant
   // ADMIN directly: DefaultCheckPermissionStrategy's ADMIN-cumulative branch resolves the
   // storage org via provider.getPermission(type, resource) — a 2-arg overload that drops the
   // orgId and falls back to OrganizationManager's ThreadContext-backed "current org" — and
   // SecurityEngine.checkPermission() always retries a denied non-ADMIN action with ADMIN
   // (L826-832), so that ambient org resolution can be reached from *any* of these checks, not
   // just the ones that assert ADMIN directly.

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
   //
   // Grant is on ResourceType.SECURITY_USER with resource=ORG_ID (see the grantPermission
   // comment in setUp() for why) -- not "*", not an IdentityID key. The wildcard check
   // (DefaultCheckPermissionStrategy L369-376) ignores the originally-requested resource
   // string entirely and just looks up provider.getPermission(SECURITY_USER, orgID), so it
   // applies identically no matter which specific user is being checked.

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

   // ── identityAdmin-group(instance) ─────────────────────────────────────────────

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

   // ── identityAdmin-role (ASSIGN, not ADMIN) ────────────────────────────────────

   @Test
   void identityAdminRoleUser_assignOnTargetRole_allowed() {
      withContextPrincipal(identityAdminRoleUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_ROLE, TARGET_ROLE)
               .expectAllow(identityAdminRoleUser, ResourceAction.ASSIGN)
            .verify());
   }

   // Previously disabled: DefaultCheckPermissionStrategy.java L232-240 had an unconditional
   // `hasResourcePermission` check that looked up ResourceAction.ASSIGN grants (instead of ADMIN)
   // and returned true on a match, silently upgrading an ASSIGN-only grant to full WRITE/DELETE/
   // ADMIN. Fixed by commit b9049488a ("fixed 75567: Fix privilege escalation where ASSIGN
   // implied full role admin access") — that check now keys on ResourceAction.ADMIN like every
   // other cascade path in this file. Re-verified passing after the fix; re-enabled.
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

   // ── S2-ROOT-CASCADE ────────────────────────────────────────────────────────────
   //
   // "Users"/"Groups"/"Organization Roles" root ADMIN grants cascade to EVERY instance of that
   // identity type (DefaultCheckPermissionStrategy L134-186 checks the synthetic root
   // unconditionally, before any per-resource grant) -- distinct from both orgSecurityAdmin's
   // SECURITY_ORGANIZATION cascade and identityAdminWildUser's SECURITY_USER-only wildcard
   // above; all three happen to look like "manages every instance" but are three independent
   // code paths.

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
      // the same Permission object regardless of the originally-requested action (L150
      // checker.checkPermission(identity, orgRoleRootPer, action, true)), so a root ADMIN grant
      // covers WRITE the same way it covered ASSIGN above.
      withContextPrincipal(rootRoleAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_ROLE, TARGET_ROLE)
               .expectAllow(rootRoleAdmin, ResourceAction.WRITE)
            .verify());
   }

   // ── S2-GLOBAL-ROLE-ROOT ──────────────────────────────────────────────────────────
   //
   // "Roles" (global root, granted to rootGlobalRoleAdmin above) and "Organization Roles" (org
   // root, granted to rootRoleAdmin) are supposed to be two independent nodes: a global-role
   // administrator manages global (org-less) roles, an org-role administrator manages that org's
   // own roles, and neither should cross over into the other's territory.
   //
   // CONFIRMED DESIGN VIOLATION (found while writing this slice, not previously known) --
   // distinct from Issue #75574. Root-caused with the same temporary-debug-prints technique used
   // elsewhere in this file (added to DefaultCheckPermissionStrategy.java, reverted after
   // diagnosis -- not left in the production file). The row-118 positive case
   // (rootGlobalRoleAdmin managing a global role) passes, but NOT via the dedicated root-check
   // block at L134-154 that this scenario was expected to exercise -- that block's guard
   // `Tool.equals(role.getOrganizationID(), currentOrgId)` is `Tool.equals(null, "matrix_org_id")`
   // for a global role, which resolves to `null == "matrix_org_id"` (false), so the block never
   // fires for ANY global-role check, positive or negative. The actual ALLOW for all three rows
   // comes from a completely different method: the private
   // `getPermission(ResourceType, String, ResourceAction, String)` helper (L625+), which for the
   // ADMIN action takes a special "cumulative permission" path. Its SECURITY_ROLE branch
   // (L744-763) queries BOTH `IdentityID("Organization Roles", currentOrg)` AND
   // `IdentityID("Roles", currentOrg)` and merges their user/role/group/organization ADMIN grants
   // into ONE combined synthetic Permission, regardless of which root the checked role actually
   // belongs under:
   //
   //   else if(currentType == ResourceType.SECURITY_ROLE) {
   //      perm = provider.getPermission(currentType, new IdentityID("Organization Roles", ...));
   //      if(perm != null) { users.addAll(...); ... }         // merged into the same sets
   //      perm = provider.getPermission(currentType, new IdentityID("Roles", ...));
   //      if(perm != null) { users.addAll(...); ... }         // merged into the same sets
   //   }
   //
   // Net effect: ADMIN on EITHER root grants ADMIN on EVERY role, global or org-scoped -- the
   // two roots are not actually independent for the ADMIN action, contradicting the design
   // intent this whole slice is meant to verify. Rows 119/120 (the negative/independence checks)
   // are asserted to the DESIGN INTENT (deny) and marked @Disabled, not asserted as "allow" to
   // match current behavior.
   //
   // IMPORTANT ASYMMETRY -- confirmed real-world reachability differs between the two roots, even
   // though they share the exact same buggy code path:
   //   - rootRoleAdmin's grant (ADMIN on "Organization Roles") IS reachable through completely
   //     ordinary EM usage in a multi-tenant deployment: any org admin can grant Administrator
   //     Permission on that org's "Organization Roles" node to a delegate today, and that
   //     delegate then also gets ADMIN on every global role. This is the operationally
   //     significant half of this finding.
   //   - rootGlobalRoleAdmin's grant (ADMIN on "Roles") is NOT reachable through the EM UI in a
   //     multi-tenant deployment: `UsersSettingsViewComponent.showPageEdit()` (frontend,
   //     `users-settings-view.component.ts` L328-338) explicitly suppresses the entire edit panel
   //     -- so the Administrator Permissions table never even renders -- whenever the selected
   //     tree node is a ROLE-type root whose name is anything other than "Organization Roles".
   //     This looks like a deliberate, pre-existing guard against delegating the global root, and
   //     it appears to work as intended for the UI. Whether a direct API call to
   //     RoleController's edit-role endpoint (bypassing the UI, the same "UI hides it but the API
   //     doesn't independently enforce it" pattern behind Issue #75574) can still write this
   //     permission has NOT been tested and is an open follow-up, not confirmed either way.
   //     `rootGlobalRoleAdmin` IS still reachable in non-multi-tenant deployments, where
   //     `showPageEdit()` allows editing this node unconditionally.
   //
   // The two @Test methods below using rootGlobalRoleAdmin are kept despite the UI-reachability
   // caveat, deliberately: this test layer verifies SecurityEngine.checkPermission()'s own logic
   // given a Permission object that exists, independent of which UI/API path (if any) created it
   // -- see the architecture design doc's "权限逻辑测试" layer definition ("与 HTTP 无关"). Do not
   // read passing/failing status here as a claim about multi-tenant EM UI reachability; for that
   // claim, see the asymmetry note above.

   @Test
   void rootGlobalRoleAdmin_adminOnGlobalRole_allowed() {
      withContextPrincipal(rootGlobalRoleAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_ROLE, new IdentityID(GLOBAL_ROLE, null).convertToKey())
               .expectAllow(rootGlobalRoleAdmin, ResourceAction.ADMIN)
            .verify());
   }

   @Disabled("Design-violation bug (found while implementing S2-GLOBAL-ROLE-ROOT, distinct from "
      + "Issue #75574) -- DefaultCheckPermissionStrategy's private getPermission() ADMIN-cumulative "
      + "helper (L744-763) merges 'Organization Roles' and 'Roles' root grants into one set for "
      + "any SECURITY_ROLE check, so a global-root admin also gets ADMIN on org-owned roles; see "
      + "the comment above for the full trace. Needs a product decision + fix before asserting "
      + "either direction as \"correct\"")
   @Test
   void rootGlobalRoleAdmin_adminOnOrgRole_denied_rootsShouldBeIndependent() {
      withContextPrincipal(rootGlobalRoleAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_ROLE, TARGET_ROLE)
               .expectDeny(rootGlobalRoleAdmin, ResourceAction.ADMIN)
            .verify());
   }

   @Disabled("Same design-violation bug as rootGlobalRoleAdmin_adminOnOrgRole_denied_"
      + "rootsShouldBeIndependent, opposite direction: the org root (Organization Roles) also "
      + "leaks ADMIN onto global roles via the same merged-cumulative-permission helper")
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
