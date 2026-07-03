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
 *   - identityAdmin-user(instance), identityAdmin-group(instance), identityAdmin-role
 *
 * Deliberately NOT covered yet (left for a later batch):
 *   - identityAdmin-user/group WILDCARD grants (SECURITY_USER/GROUP "*") — the production
 *     wildcard lookup keys the permission by resource=orgID, not "*"
 *     (DefaultCheckPermissionStrategy L371: `provider.getPermission(type, orgID)`), which
 *     needs a dedicated spike/verification before encoding it as a fixture.
 *   - the org-less global role negative case (needs a not-yet-existing
 *     SecurityTestDataBuilder.addGlobalRole() helper).
 *   - S2-GRANTEE-VARIETY / S2-GLOBAL-ROLE-ROOT / S2-ROOT-CASCADE / S2-GROUP-CHAIN — these are
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

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
   private static final String ASSET_RESOURCE = "mx/folder/item";

   private static SecurityTestDataBuilder builder;

   private static SRPrincipal orgSecurityAdmin;
   private static SRPrincipal identityAdminUser;
   private static SRPrincipal identityAdminGroupInstUser;
   private static SRPrincipal identityAdminRoleUser;

   @BeforeAll
   static void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg(ORG_NAME, ORG_ID)

         .addRole(TARGET_ROLE, ORG_ID)
         .addRole(ANOTHER_ROLE, ORG_ID)

         .addUser("orgSecurityAdmin", ORG_ID, "password")
         .addUser("identityAdminUser", ORG_ID, "password")
         .addUser(TARGET_USER, ORG_ID, "password")
         .addUser(ANOTHER_USER, ORG_ID, "password")
         .addUser("identityAdminGroupInstUser", ORG_ID, "password")
         .addUser("identityAdminRoleUser", ORG_ID, "password")

         // orgSecurityAdmin: ADMIN on SECURITY_ORGANIZATION cascades to all SECURITY_USER/
         // SECURITY_GROUP instances and org-owned SECURITY_ROLE instances (see
         // DefaultCheckPermissionStrategy L57-67). That check reads the permission via
         // provider.getPermission(SECURITY_ORGANIZATION, IdentityID(orgName, orgId)), so the
         // resource key here must equal that IdentityID's convertToKey() output, not the bare
         // org id.
         .grantPermission(ResourceType.SECURITY_ORGANIZATION,
                          new IdentityID(ORG_NAME, ORG_ID).convertToKey(),
                          ResourceAction.ADMIN, "orgSecurityAdmin", Identity.USER, ORG_ID)

         // identityAdmin-user(instance): ADMIN on one specific SECURITY_USER instance
         .grantPermission(ResourceType.SECURITY_USER, TARGET_USER, ResourceAction.ADMIN,
                          "identityAdminUser", Identity.USER, ORG_ID)

         // identityAdmin-group(instance): ADMIN on one specific SECURITY_GROUP instance
         .grantPermission(ResourceType.SECURITY_GROUP, TARGET_GROUP, ResourceAction.ADMIN,
                          "identityAdminGroupInstUser", Identity.USER, ORG_ID)

         // identityAdmin-role: ASSIGN (not ADMIN) on one specific SECURITY_ROLE instance
         .grantPermission(ResourceType.SECURITY_ROLE, TARGET_ROLE, ResourceAction.ASSIGN,
                          "identityAdminRoleUser", Identity.USER, ORG_ID)

         .setup();

      orgSecurityAdmin = builder.principalOf("orgSecurityAdmin", ORG_ID);
      identityAdminUser = builder.principalOf("identityAdminUser", ORG_ID);
      identityAdminGroupInstUser = builder.principalOf("identityAdminGroupInstUser", ORG_ID);
      identityAdminRoleUser = builder.principalOf("identityAdminRoleUser", ORG_ID);
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

   @Test
   void orgSecurityAdmin_crossTypeCascade_securityUserAdmin_allowedWithoutDirectGrant() {
      withContextPrincipal(orgSecurityAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_USER, TARGET_USER)
               .expectAllow(orgSecurityAdmin, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void orgSecurityAdmin_crossTypeCascade_securityGroupAdmin_allowedWithoutDirectGrant() {
      withContextPrincipal(orgSecurityAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_GROUP, ANOTHER_GROUP)
               .expectAllow(orgSecurityAdmin, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void orgSecurityAdmin_crossTypeCascade_orgOwnedRoleAdmin_allowedWithoutDirectGrant() {
      withContextPrincipal(orgSecurityAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_ROLE, ANOTHER_ROLE)
               .expectAllow(orgSecurityAdmin, ResourceAction.ADMIN)
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

   // NOTE: the following two cases are disabled, not deleted — see conversation/PR notes.
   // Per the design doc (permission-test-architecture-design.md L448, permission-matrix-
   // resources.md row 46-47), ASSIGN must NOT imply WRITE/DELETE. Actual behavior contradicts
   // this: DefaultCheckPermissionStrategy.java L232-240 has an unconditional
   // `hasResourcePermission` check — gated only by the user having *any* roles/groups/org (i.e.
   // true for virtually every authenticated principal), not by ResourceType or by the requested
   // `action` — that looks up ResourceAction.ASSIGN grants specifically (not the action actually
   // being checked) and returns true on a match:
   //
   //   boolean hasResourcePermission = provider.getPermission(type, resource, orgID) != null &&
   //      provider.getPermission(type, resource, orgID)
   //         .getOrgScopedUserGrants(ResourceAction.ASSIGN, ...).contains(pId);
   //   if(hasResourcePermission) { return true; }
   //
   // The comment above that block reads "if admin permissions to this resource, return true",
   // which checks ResourceAction.ADMIN in every other cascade path in this file — this one
   // checks ASSIGN instead, so it looks like ADMIN was probably intended. Confirmed empirically:
   // identityAdminRoleUser (ASSIGN-only on targetRole) gets WRITE, DELETE, and ADMIN all
   // allowed on targetRole, not just ASSIGN. This is either a real bug (a `Assign Permissions`
   // grant silently upgrading to full R/W/D/A control of the role) or the design doc is stale;
   // needs a product-owner call before encoding either behavior as "correct" here.
   @Disabled("DefaultCheckPermissionStrategy L232-240 grants WRITE/DELETE to ASSIGN-only "
      + "identities via an ASSIGN-keyed hasResourcePermission check — contradicts the design "
      + "doc's 'ASSIGN does not imply WRITE/DELETE' claim; needs a decision on which is correct "
      + "before asserting either way")
   @Test
   void identityAdminRoleUser_writeOnTargetRole_denied_assignDoesNotImplyWrite() {
      withContextPrincipal(identityAdminRoleUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SECURITY_ROLE, TARGET_ROLE)
               .expectDeny(identityAdminRoleUser, ResourceAction.WRITE)
            .verify());
   }

   @Disabled("See identityAdminRoleUser_writeOnTargetRole_denied_assignDoesNotImplyWrite")
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
