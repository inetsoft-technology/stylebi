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
 * Slice test class for permission-matrix-special.md (Region 3: authentication context / account
 * status pathway tests -- not yet implemented below, see class body note; Region 4: host-org /
 * SELF built-in organization default-permission behavior). See that doc for the full scenario
 * tables.
 *
 * Uses SecurityTestDataBuilder directly rather than MultiTenantTestFixture, same reasoning as
 * MultiTenantIsolationTest: full control over fixture data. No fixture change was needed --
 * host-org/SELF are auto-seeded by FileAuthenticationProvider.init() (invoked by every provider
 * method, including addUser()) via LoadOrganizationsTask, independent of
 * SecurityTestDataBuilder's own addOrg() bookkeeping, so addUser(name, Organization.
 * getSelfOrganizationID(), pw) works with no addOrg() call first. Test isolation is safe across
 * classes: TestKeyValueEngine (community/core/src/test/java/inetsoft/test/TestKeyValueEngine.java)
 * is a plain in-memory ConcurrentHashMap tied to this class's own Spring context, and
 * @DirtiesContext(AFTER_CLASS) below discards that context (and its storage) once this class
 * finishes.
 *
 * Region 3 (authentication context / account status pathway tests -- Google OAuth SSO, inactive
 * account, Login As) is documented in permission-matrix-special.md but has no test methods here
 * yet; all three rows are still [待补]. Everything below is Region 4.
 *
 * Three scenarios from Region 4's original five were dropped after review, recorded as [不补] in
 * the doc:
 *   - org self-signup landing (UserSignupService.java:169-170: SUtil.isMultiTenant() ? SELF :
 *     host-org) is a single ternary with low bug risk, and what it actually lands users into is
 *     exactly what the scenarios below already verify; testing the ternary itself would mean
 *     exercising UserSignupService/HTTP registration, a different layer than this class's "call
 *     SecurityEngine.checkPermission() directly" scope.
 *   - SELF DATA_SOURCE_FOLDER root fallback ("isSelfAndNotAdmin", originally cited at
 *     SecurityEngine.java L1086-1092): that line only exists on the checkPermission(Principal,
 *     ResourceType, IdentityID, ResourceAction) overload. Every real production call site for
 *     DATA_SOURCE_FOLDER (DatasourcesTreeService, DataSourceBrowserService, GettingStartedService,
 *     plus SecurityEngine's own internal recursive calls) uses the sibling checkPermission(...,
 *     String resource, ...) overload instead (L820-943), whose own DATA_SOURCE_FOLDER root
 *     fallback (L919-924) has no SELF-specific branch at all -- READ defaults to allowed for
 *     every org unconditionally. Verified empirically: calling the String overload (the only one
 *     any real caller or any other test in this suite uses) shows no SELF/created-org difference.
 *     The isSelfAndNotAdmin branch is unreachable dead code for this resource type.
 *
 * Scenario 3 -- SELF default permission list (FileAuthorizationProvider.
 * addDefaultPermissionForSelfOrg(), L416-436): grants ACCESS on CREATE_DATA_SOURCE (wildcard),
 * PORTAL_TAB (Data), PHYSICAL_TABLE (wildcard), CROSS_JOIN (wildcard), FREE_FORM_SQL (wildcard),
 * and READ on REPORT (root) and ASSET (root), to the
 * ORGANIZATION identity "Self Organization" -- so ANY SELF-org user gets these with zero explicit
 * grants, purely through the organization-level grant path in PermissionChecker.
 * checkUserGroupOrganizationPermission(). None of these seven (type, resource) pairs are on
 * DefaultCheckPermissionStrategy's unrelated-to-org default-allow list (L273-287) and all seven
 * types are non-hierarchical or resolve to a parent-less root ("/"), so a plain user in an
 * ordinary created org gets a clean deny on all seven for comparison.
 *
 * Scenario 5 -- host-org cross-org visibility bypass (DefaultCheckPermissionStrategy.
 * isOpeningShareGlobalAsset(), L511-517 + isAllowedDefaultGlobalVSAction(), L493-509): despite the
 * "host-org" name, this does NOT give host-org's own users anything special. It fires for a user
 * from ANY OTHER org, when the ambient "current org" context (OrganizationManager.
 * getCurrentOrgID(), which falls back to host-org whenever no ThreadContext principal is set)
 * equals host-org, and SUtil.isDefaultVSGloballyVisible(principal) is true for that user. When it
 * fires, checkPermission() returns true unconditionally for CHART_TYPE/SHARE READ, before any
 * explicit grant or deny is even consulted. SUtil.isDefaultVSGloballyVisible() requires
 * SUtil.isMultiTenant() -- structurally false on community/core's test classpath (same
 * LicenseManager.isEnterprise() classpath-probe issue as PermissionMatrixActionsS6Test), so it is
 * mocked via Mockito.mockStatic(SUtil.class, CALLS_REAL_METHODS), following that class's
 * precedent -- plus the "security.exposeDefaultOrgToAll" property and a non-site-admin principal.
 *
 * SHARE was picked over CHART_TYPE for this contrast because CHART_TYPE has its own,
 * already-tested default-bypass mechanism (SecurityEngine's CHART_TYPE parent-retry fallback,
 * Bug #70538, covered by PermissionMatrixActionsS8Test's S8-CHART-TYPE-HIERARCHY/-ASYMMETRY
 * sections) that would confound which mechanism produced an ALLOW. SHARE has no such entanglement,
 * but it IS on DefaultCheckPermissionStrategy's default-allow list (L276), so the resource used
 * here is marked explicitly edited-with-no-grant (SecurityTestDataBuilder.markPermissionEdited())
 * to get a real baseline DENY to contrast against.
 *
 * The two assertions below need opposite ThreadContext setups, because
 * Permission.hasOrgEditedGrantAll() is checked against the SAME ambient current-org value as
 * isOpeningShareGlobalAsset() above: the "mechanism off" test pins ThreadContext to
 * createdOrgPlainUser so that ambient org matches CREATED_ORG_ID (where the edited-empty marker
 * was recorded) instead of defaulting to host-org and letting the default-allow-list fire for the
 * wrong reason (same fix MultiTenantIsolationTest.scenario18A applies, and for the same root
 * cause); the "mechanism on" test leaves ThreadContext unset specifically so that ambient org
 * defaults to host-org, which isOpeningShareGlobalAsset() requires.
 *
 * Login As -- BasicAuthenticationFilter.checkLoginAs() permission gate (Region 3). This is only
 * the permission-gating half of the Login As scenario; the other half (the actual identity switch)
 * is blocked by a confirmed bug in the re-authentication step -- see permission-matrix-special.md's
 * "Login As 详细场景" section for the full writeup -- so this class does not attempt to test the
 * end-to-end switch. checkLoginAs() is private, so it is invoked via
 * org.springframework.test.util.ReflectionTestUtils.invokeMethod() on a BasicAuthenticationFilter
 * instance built with Mockito-mocked constructor args (neither dependency is read by
 * checkLoginAs() itself).
 *
 * OrganizationManager.getUserOrgId(Principal) -- checkLoginAs()'s "same org" check -- has no
 * enterprise override on community/core's test classpath, so the community base implementation
 * (OrganizationManager.java) runs, which unconditionally returns Organization.
 * getDefaultOrganizationID() regardless of which principal is passed in. So for every non-site-admin
 * operator tested here, "same org" in practice means "target is in host-org" -- not a per-operator
 * comparison, structurally, the same kind of community-vs-enterprise classpath gap as
 * SUtil.isMultiTenant() elsewhere in this suite.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.support.*;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import inetsoft.web.security.BasicAuthenticationFilter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PermissionMatrixSpecialTest {

   private static final String CREATED_ORG_NAME = "acme";
   private static final String CREATED_ORG_ID = "acme_id";

   // Never granted -- exists only so scenario 5's baseline (mechanism off) has a real, explicit
   // deny to contrast against SHARE being on the default-allow list.
   private static final String SHARE_RESOURCE = "specialOrgDefaultsTestShare";

   private static final String HOST_ORG_ID = Organization.getDefaultOrganizationID();

   private static SecurityTestDataBuilder builder;
   private static SRPrincipal selfPlainUser;
   private static SRPrincipal createdOrgPlainUser;

   private static SRPrincipal loginAsSiteAdmin;
   private static SRPrincipal loginAsOperatorWithGrant;
   private static SRPrincipal loginAsOperatorWithoutGrant;
   private static IdentityID loginAsTarget;
   private static IdentityID loginAsTargetInOtherOrg;
   private static IdentityID loginAsTargetSiteAdmin;

   @BeforeAll
   static void setUpAll() throws Exception {
      loginAsTarget = new IdentityID("loginAsTarget", HOST_ORG_ID);
      loginAsTargetInOtherOrg = new IdentityID("loginAsTargetInOtherOrg", CREATED_ORG_ID);
      loginAsTargetSiteAdmin = new IdentityID("loginAsTargetSiteAdmin", HOST_ORG_ID);

      builder = SecurityTestDataBuilder.create()
         .addOrg(CREATED_ORG_NAME, CREATED_ORG_ID)
         .addUser("selfPlainUser", Organization.getSelfOrganizationID(), "password")
         .addUser("createdOrgPlainUser", CREATED_ORG_ID, "password")
         .markPermissionEdited(ResourceType.SHARE, SHARE_RESOURCE, CREATED_ORG_ID)

         .addSysAdminRole("LoginAsSiteAdminRole", HOST_ORG_ID)
         .addUser("loginAsSiteAdmin", HOST_ORG_ID, "password")
         .addUserToRole("loginAsSiteAdmin", "LoginAsSiteAdminRole", HOST_ORG_ID)

         .addUser("loginAsOperatorWithGrant", HOST_ORG_ID, "password")
         .addUser("loginAsOperatorWithoutGrant", HOST_ORG_ID, "password")
         .addUser(loginAsTarget.name, HOST_ORG_ID, "password")
         .addUser(loginAsTargetInOtherOrg.name, CREATED_ORG_ID, "password")

         .addSysAdminRole("LoginAsTargetAdminRole", HOST_ORG_ID)
         .addUser(loginAsTargetSiteAdmin.name, HOST_ORG_ID, "password")
         .addUserToRole(loginAsTargetSiteAdmin.name, "LoginAsTargetAdminRole", HOST_ORG_ID)

         .grantPermission(ResourceType.SECURITY_USER, loginAsTarget.convertToKey(),
            ResourceAction.ADMIN, "loginAsOperatorWithGrant", Identity.USER, HOST_ORG_ID)

         .setup();

      selfPlainUser = builder.principalOf("selfPlainUser", Organization.getSelfOrganizationID());
      createdOrgPlainUser = builder.principalOf("createdOrgPlainUser", CREATED_ORG_ID);

      loginAsSiteAdmin = builder.principalOf("loginAsSiteAdmin", HOST_ORG_ID);
      loginAsOperatorWithGrant = builder.principalOf("loginAsOperatorWithGrant", HOST_ORG_ID);
      loginAsOperatorWithoutGrant = builder.principalOf("loginAsOperatorWithoutGrant", HOST_ORG_ID);
   }

   @AfterAll
   static void tearDownAll() {
      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   // ── scenario 3: SELF default permission list ──────────────────────────────

   @Test
   void selfOrgUser_defaultPermissionList_allowedWithoutAnyExplicitGrant() {
      PermissionMatrixVerifier.of(engine())
         .resource(ResourceType.CREATE_DATA_SOURCE, "*")
            .expectAllow(selfPlainUser, ResourceAction.ACCESS)
            .expectDeny(createdOrgPlainUser, ResourceAction.ACCESS)
         .resource(ResourceType.PORTAL_TAB, "Data")
            .expectAllow(selfPlainUser, ResourceAction.ACCESS)
            .expectDeny(createdOrgPlainUser, ResourceAction.ACCESS)
         .resource(ResourceType.PHYSICAL_TABLE, "*")
            .expectAllow(selfPlainUser, ResourceAction.ACCESS)
            .expectDeny(createdOrgPlainUser, ResourceAction.ACCESS)
         .resource(ResourceType.CROSS_JOIN, "*")
            .expectAllow(selfPlainUser, ResourceAction.ACCESS)
            .expectDeny(createdOrgPlainUser, ResourceAction.ACCESS)
         .resource(ResourceType.FREE_FORM_SQL, "*")
            .expectAllow(selfPlainUser, ResourceAction.ACCESS)
            .expectDeny(createdOrgPlainUser, ResourceAction.ACCESS)
         .resource(ResourceType.REPORT, "/")
            .expectAllow(selfPlainUser, ResourceAction.READ)
            .expectDeny(createdOrgPlainUser, ResourceAction.READ)
         .resource(ResourceType.ASSET, "/")
            .expectAllow(selfPlainUser, ResourceAction.READ)
            .expectDeny(createdOrgPlainUser, ResourceAction.READ)
         .verify();
   }

   // ── scenario 5: host-org cross-org global-visibility bypass ───────────────

   @Test
   void nonHostOrgUser_shareDefaultVisibility_deniedWhenMechanismOff() throws Exception {
      // hasOrgEditedGrantAll() is checked against OrganizationManager.getCurrentOrgID(), which
      // (like MultiTenantIsolationTest.scenario18A) falls back to host-org whenever no
      // ThreadContext principal is set -- mismatching the edited marker recorded against
      // CREATED_ORG_ID and letting the unrelated default-allow-list fire instead. The mechanism
      // under test here is off regardless (isMultiTenant real value is false), so pinning the
      // context principal only fixes this org-id resolution; it does not affect the assertion.
      ThreadContext.setContextPrincipal(createdOrgPlainUser);

      try {
         assertFalse(
            engine().checkPermission(createdOrgPlainUser, ResourceType.SHARE, SHARE_RESOURCE,
                                     ResourceAction.READ),
            "createdOrgPlainUser must be denied SHARE by its own edited-empty permission when " +
            "the global-visibility mechanism is off");
      }
      finally {
         ThreadContext.setContextPrincipal(null);
      }
   }

   @Test
   void nonHostOrgUser_shareDefaultVisibility_allowedWhenMechanismOn() throws Exception {
      ThreadContext.setContextPrincipal(null);

      withMultiTenant(true, () -> {
         SreeEnv.setProperty("security.exposeDefaultOrgToAll", "true");

         try {
            assertTrue(
               engine().checkPermission(createdOrgPlainUser, ResourceType.SHARE, SHARE_RESOURCE,
                                        ResourceAction.READ),
               "createdOrgPlainUser must be allowed SHARE via isOpeningShareGlobalAsset() when " +
               "the ambient org context resolves to host-org and exposeDefaultOrgToAll is on, " +
               "despite the resource's own edited-empty permission denying it");
         }
         finally {
            SreeEnv.remove("security.exposeDefaultOrgToAll");
         }
      });
   }

   // ── Login As: checkLoginAs() permission gate ────────────────────────────────

   @Test
   void checkLoginAs_siteAdminOperator_anyTarget_allowed() {
      assertTrue(checkLoginAs(loginAsSiteAdmin, loginAsTarget),
         "site admin must be allowed to log in as any target");
      assertTrue(checkLoginAs(loginAsSiteAdmin, loginAsTargetSiteAdmin),
         "site admin must be allowed to log in as another site admin");
      assertTrue(checkLoginAs(loginAsSiteAdmin, loginAsTargetInOtherOrg),
         "site admin must be allowed to log in as a target in a different org");
   }

   @Test
   void checkLoginAs_nonSiteAdminOperator_sameOrgTargetWithSecurityUserAdminGrant_allowed() {
      assertTrue(checkLoginAs(loginAsOperatorWithGrant, loginAsTarget),
         "non-site-admin with SECURITY_USER ADMIN on the target must be allowed");
   }

   @Test
   void checkLoginAs_nonSiteAdminOperator_sameOrgTargetWithoutGrant_denied() {
      assertFalse(checkLoginAs(loginAsOperatorWithoutGrant, loginAsTarget),
         "non-site-admin without SECURITY_USER ADMIN on the target must be denied");
   }

   @Test
   void checkLoginAs_nonSiteAdminOperator_targetInDifferentOrg_denied() {
      assertFalse(checkLoginAs(loginAsOperatorWithGrant, loginAsTargetInOtherOrg),
         "non-site-admin must be denied for a target outside their own org, regardless of grants");
   }

   @Test
   void checkLoginAs_nonSiteAdminOperator_targetIsSiteAdmin_denied() {
      assertFalse(checkLoginAs(loginAsOperatorWithGrant, loginAsTargetSiteAdmin),
         "non-site-admin must be denied when the target is a site admin, regardless of grants");
   }

   // ── helpers ────────────────────────────────────────────────────────────────

   private interface ThrowingRunnable {
      void run() throws Exception;
   }

   private static void withMultiTenant(boolean multiTenant, ThrowingRunnable action) throws Exception {
      try(MockedStatic<SUtil> mocked = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS)) {
         mocked.when(SUtil::isMultiTenant).thenReturn(multiTenant);
         action.run();
      }
   }

   private static boolean checkLoginAs(SRPrincipal operator, IdentityID target) {
      BasicAuthenticationFilter filter = new BasicAuthenticationFilter(
         Mockito.mock(SessionLicenseServiceProvider.class), Mockito.mock(AuthenticationService.class));

      return ReflectionTestUtils.invokeMethod(
         filter, "checkLoginAs", operator, engine().getSecurityProvider(), target);
   }

   private static SecurityEngine engine() {
      return SecurityEngine.getSecurity();
   }
}
