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
 * Covers permission-test-architecture-design.md scenarios 13–18B (multi-tenant isolation).
 *
 * Two organisations — "alpha" (orgA) and "beta" (orgB) — are set up with real
 * FileAuthenticationProvider / FileAuthorizationProvider.  Each scenario exercises a distinct
 * isolation boundary in DefaultCheckPermissionStrategy.
 *
 * Design note: MultiTenantTestFixture is not used here because it does not natively support
 * a site-admin user (scenario 17) or the role-name collision setup required for scenario 18B.
 * Using SecurityTestDataBuilder directly allows a single SecurityEngine.init() call and gives
 * full control over the test data.
 *
 * Scenarios:
 *   13  – regular user READ own-org viewsheet → true
 *   14  – regular user READ cross-org viewsheet → false
 *   15  – regular user READ cross-org data source → false
 *   16  – custom admin-role user WRITE cross-org SECURITY_USER → false
 *   17  – site admin (Administrator role) READ any-org viewsheet → true
 *   18  – same username in different orgs = independent IdentityID, no permission bleed
 *   18A – own-org admin-role user ADMIN own-org viewsheet → true
 *   18B – same role name in different orgs does NOT share grants across orgs
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.support.*;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MultiTenantIsolationTest {

   // ── resource paths (org-specific to avoid same-path/same-orgId collisions) ─

   static final String ALPHA_VS = "reports/alpha_vs";  // VIEWSHEET  — orgA scope only
   static final String BETA_VS  = "reports/beta_vs";   // VIEWSHEET  — orgB scope only
   static final String ALPHA_DS = "ds/alpha_ds";       // DATA_SOURCE — orgA scope only
   static final String BETA_DS  = "ds/beta_ds";        // DATA_SOURCE — orgB scope only

   // ── shared state ──────────────────────────────────────────────────────────

   static SecurityTestDataBuilder builder;
   static String orgAId;
   static String orgBId;

   // Principals used across scenarios
   static SRPrincipal aliceOrgA;   // "alice" in orgA (scenarios 13, 14, 15, 18)
   static SRPrincipal aliceOrgB;   // "alice" in orgB — same name, independent identity (18)
   static SRPrincipal adminA;      // orgA admin-role user (18A)
   static SRPrincipal adminB;      // orgB admin-role user (16)
   static SRPrincipal siteAdmin;   // user with built-in Administrator role (17)
   static SRPrincipal editorA;     // orgA "editor" role user (18B positive)
   static SRPrincipal editorB;     // orgB "editor" role user (18B negative)

   // ── lifecycle ─────────────────────────────────────────────────────────────

   @BeforeAll
   static void setUp() throws Exception {
      orgAId = "alpha_id";
      orgBId = "beta_id";

      builder = SecurityTestDataBuilder.create()
         // ── organisations ────────────────────────────────────────────────────
         .addOrg("alpha", orgAId)
         .addOrg("beta",  orgBId)

         // ── orgA roles ───────────────────────────────────────────────────────
         .addRole("alpha_id_admin", orgAId)
         .addSysAdminRole("Administrator", orgAId)  // FSRole.sysAdmin=true; isSystemAdministratorRole() checks the flag
         .addRole("editor",         orgAId)  // 18B: same name as orgB's "editor", different IdentityID

         // ── orgB roles ───────────────────────────────────────────────────────
         .addRole("beta_id_admin", orgBId)
         .addRole("editor",        orgBId)   // 18B: same name as orgA's "editor", no READ grant on ALPHA_VS

         // ── orgA users ───────────────────────────────────────────────────────
         .addUser("alice",     orgAId, "password")           // scenarios 13, 14, 15, 18
         .addUser("adminA",    orgAId, "password")           // scenario 18A
         .addUserToRole("adminA",    "alpha_id_admin", orgAId)
         .addUser("siteAdmin", orgAId, "password")           // scenario 17
         .addUserToRole("siteAdmin", "Administrator",  orgAId)
         .addUser("editorA",   orgAId, "password")           // scenario 18B positive
         .addUserToRole("editorA",   "editor",         orgAId)

         // ── orgB users ───────────────────────────────────────────────────────
         .addUser("alice",   orgBId, "password")   // same username as orgA's alice (scenario 18)
         .addUser("adminB",  orgBId, "password")   // scenario 16
         .addUserToRole("adminB",  "beta_id_admin", orgBId)
         .addUser("editorB", orgBId, "password")   // scenario 18B negative
         .addUserToRole("editorB", "editor",        orgBId)

         // ── permissions: orgA scope ──────────────────────────────────────────
         .grantPermission(ResourceType.VIEWSHEET, ALPHA_VS, ResourceAction.READ,
                          "alpha_id_admin", Identity.ROLE, orgAId)
         .grantPermission(ResourceType.VIEWSHEET, ALPHA_VS, ResourceAction.ADMIN,
                          "alpha_id_admin", Identity.ROLE, orgAId)
         .grantPermission(ResourceType.VIEWSHEET, ALPHA_VS, ResourceAction.READ,
                          "alice", Identity.USER, orgAId)
         .grantPermission(ResourceType.VIEWSHEET, ALPHA_VS, ResourceAction.READ,
                          "editor", Identity.ROLE, orgAId)   // 18B: orgA editor CAN read
         .grantPermission(ResourceType.DATA_SOURCE, ALPHA_DS, ResourceAction.READ,
                          "alpha_id_admin", Identity.ROLE, orgAId)
         .grantPermission(ResourceType.DATA_SOURCE, ALPHA_DS, ResourceAction.ADMIN,
                          "alpha_id_admin", Identity.ROLE, orgAId)
         .grantPermission(ResourceType.DATA_SOURCE, ALPHA_DS, ResourceAction.READ,
                          "alice", Identity.USER, orgAId)

         // ── permissions: orgB scope ──────────────────────────────────────────
         .grantPermission(ResourceType.VIEWSHEET, BETA_VS, ResourceAction.READ,
                          "beta_id_admin", Identity.ROLE, orgBId)
         .grantPermission(ResourceType.VIEWSHEET, BETA_VS, ResourceAction.ADMIN,
                          "beta_id_admin", Identity.ROLE, orgBId)
         .grantPermission(ResourceType.VIEWSHEET, BETA_VS, ResourceAction.READ,
                          "alice", Identity.USER, orgBId)
         .grantPermission(ResourceType.DATA_SOURCE, BETA_DS, ResourceAction.READ,
                          "beta_id_admin", Identity.ROLE, orgBId)
         .grantPermission(ResourceType.DATA_SOURCE, BETA_DS, ResourceAction.ADMIN,
                          "beta_id_admin", Identity.ROLE, orgBId)
         .grantPermission(ResourceType.DATA_SOURCE, BETA_DS, ResourceAction.READ,
                          "alice", Identity.USER, orgBId)
         // 18B: orgB scope has NO "editor" role grant on ALPHA_VS — deliberately omitted

         .setup();

      aliceOrgA = builder.principalOf("alice",     orgAId);
      aliceOrgB = builder.principalOf("alice",     orgBId);
      adminA    = builder.principalOf("adminA",    orgAId);
      adminB    = builder.principalOf("adminB",    orgBId);
      siteAdmin = builder.principalOf("siteAdmin", orgAId);
      editorA   = builder.principalOf("editorA",   orgAId);
      editorB   = builder.principalOf("editorB",   orgBId);

      // Disable the "data source everyone" fallback so that cross-org DATA_SOURCE
      // access is governed solely by explicit permission grants (scenario 15).
      // Default value "true" would allow READ on any data source with no explicit
      // permission configured, bypassing org isolation.
      SreeEnv.setProperty("security.datasource.everyone", "false");
      SecurityEngine.updateSecurityDatasourceEveryoneValue();
   }

   @AfterAll
   static void tearDown() {
      SreeEnv.remove("security.datasource.everyone");
      SecurityEngine.updateSecurityDatasourceEveryoneValue();

      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   // ── scenario 13: same-org regular user READ ───────────────────────────────

   @Test
   void scenario13_sameOrg_regularUser_read_allowed() throws Exception {
      assertTrue(
         engine().checkPermission(aliceOrgA, ResourceType.VIEWSHEET, ALPHA_VS, ResourceAction.READ),
         "alice@orgA must READ orgA viewsheet (direct user grant)");
   }

   // ── scenario 14: cross-org viewsheet read denied ──────────────────────────

   @Test
   void scenario14_crossOrg_viewsheet_read_denied() throws Exception {
      assertFalse(
         engine().checkPermission(aliceOrgA, ResourceType.VIEWSHEET, BETA_VS, ResourceAction.READ),
         "alice@orgA must NOT READ orgB viewsheet — orgA permission scope has no grant for BETA_VS");
   }

   // ── scenario 15: cross-org data source read denied ────────────────────────

   @Test
   void scenario15_crossOrg_dataSource_read_denied() throws Exception {
      assertFalse(
         engine().checkPermission(aliceOrgA, ResourceType.DATA_SOURCE, BETA_DS, ResourceAction.READ),
         "alice@orgA must NOT READ orgB data source — orgA permission scope has no grant for BETA_DS");
   }

   // ── scenario 16: custom admin-role user WRITE on cross-org SECURITY_USER ──

   @Test
   void scenario16_crossOrg_adminRole_securityUserWrite_denied() throws Exception {
      // resource = IdentityID key of alice@orgA  ("alice~;~alpha_id")
      String aliceAKey = aliceOrgA.getName();
      assertFalse(
         engine().checkPermission(adminB, ResourceType.SECURITY_USER, aliceAKey, ResourceAction.WRITE),
         "adminB (beta_id_admin — a custom role, not built-in OrgAdmin) must NOT WRITE orgA user");
   }

   // ── scenario 17: site admin bypasses all org boundaries ───────────────────

   @Test
   void scenario17_siteAdmin_crossOrg_viewsheet_allowed() throws Exception {
      // siteAdmin has "Administrator" role with FSRole.sysAdmin=true.
      // isSystemAdministratorRole() checks ((FSRole) role).isSysAdmin() — returns true →
      // isSysAdmin = true → unconditional allow regardless of org boundary.
      assertTrue(
         engine().checkPermission(siteAdmin, ResourceType.VIEWSHEET, BETA_VS, ResourceAction.READ),
         "site admin (Administrator role with sysAdmin=true) must READ viewsheet in any org");
   }

   // ── scenario 18: same username in different orgs → independent identities ─

   @Test
   void scenario18_sameUsername_differentOrg_permissionsAreIndependent() {
      // IdentityID("alice", orgAId) ≠ IdentityID("alice", orgBId)
      // Each has its own permission grant; neither bleeds into the other's org scope.
      PermissionMatrixVerifier.of(engine())
         .resource(ResourceType.VIEWSHEET, ALPHA_VS)
            .expectAllow(aliceOrgA, ResourceAction.READ)  // direct grant in orgA scope
            .expectDeny(aliceOrgB,  ResourceAction.READ)  // no orgB-scoped grant for ALPHA_VS
         .verify();
   }

   // ── scenario 18A: own-org admin-role user ADMIN own-org viewsheet ─────────

   @Test
   void scenario18A_sameOrg_adminRole_admin_allowed() throws Exception {
      // ADMIN permission lookup calls provider.getPermission(type, resource) WITHOUT orgId;
      // that variant resolves the storage key using OrganizationManager.getCurrentOrgID()
      // (thread-local), which defaults to "host-org" when no principal is on the thread.
      // Set the context principal so the key matches the "alpha_id"-scoped stored grant.
      ThreadContext.setContextPrincipal(adminA);
      try {
         assertTrue(
            engine().checkPermission(adminA, ResourceType.VIEWSHEET, ALPHA_VS, ResourceAction.ADMIN),
            "adminA (alpha_id_admin role) must have ADMIN on orgA viewsheet");
      }
      finally {
         ThreadContext.setContextPrincipal(null);
      }
   }

   // ── scenario 18B: same role name, different org → no permission bleed ─────

   @Test
   void scenario18B_sameRoleName_differentOrg_noPermissionBleed() {
      // "editor" role in orgA has READ on ALPHA_VS.
      // "editor" role in orgB has the same name but is a different IdentityID("editor", orgBId);
      // it has NO grant on ALPHA_VS — verifies role permissions are keyed by IdentityID, not name.
      PermissionMatrixVerifier.of(engine())
         .resource(ResourceType.VIEWSHEET, ALPHA_VS)
            .expectAllow(editorA, ResourceAction.READ)  // editor@orgA → READ (role grant in orgA scope)
            .expectDeny(editorB,  ResourceAction.READ)  // editor@orgB → no grant in orgB scope
         .verify();
   }

   // ── helper ────────────────────────────────────────────────────────────────

   private static SecurityEngine engine() {
      return SecurityEngine.getSecurity();
   }
}
