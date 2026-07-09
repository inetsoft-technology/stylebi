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
 * Slice test class for permission-matrix-actions.md § S6 (For-Org-x boundary: orgAdmin denied).
 * See that doc for the full scenario table and the DEVICE/SCHEDULE_OPTION probe write-up.
 *
 * Scope is deliberately narrowed to "orgAdmin denied" -- siteAdmin's own bypass is not
 * re-asserted here, since it is structural (isSysAdmin/isSiteAdmin short-circuits before the
 * org-admin-action exclusion list is ever consulted), the same reasoning permission-matrix-
 * resources.md uses for not giving siteAdmin/orgAdmin full-access its own slice (S1).
 *
 * The main deny table is exhaustive, not a representative sample: it parameterizes directly over
 * ActionPermissionService.orgAdminActionExclusions (all 20 real entries -- 16 EM_COMPONENT + 3
 * SCHEDULE_TASK + 1 UPLOAD_DRIVERS), not a hand-picked subset. That array is a fixed, enumerable
 * list of literal production data, not an abstract mechanism to sample across resource types (the
 * way S3-S5's *-CROSS-GROUP tests sample one representative type to prove a mechanism generalizes)
 * -- a future edit that drops or mistypes one entry would only be caught by a test that actually
 * checks that specific entry. Reading the array directly (instead of copying each string into this
 * file) also means the test can't drift out of sync with the production list.
 *
 * Environment constraint (why this class mocks SUtil.isMultiTenant() instead of calling
 * PermissionMatrixVerifier bare like PermissionMatrixResourcesS2-5Test do): the mechanism under
 * test -- DefaultCheckPermissionStrategy's
 * `if(!ActionPermissionService.isOrgAdminAction(type, resource) && SUtil.isMultiTenant())
 * return false;` early-exit -- only fires when SUtil.isMultiTenant() is true. That method requires
 * LicenseManager.isEnterprise(), which is determined by Class.forName("inetsoft.enterprise.
 * EnterpriseConfig") succeeding -- a class that is not on community/core's test classpath, so
 * isEnterprise() (and therefore isMultiTenant()) is structurally false in this module no matter
 * what SecurityTestDataBuilder's "security.users.multiTenant" property is set to. Each assertion
 * below is wrapped in Mockito.mockStatic(SUtil.class, CALLS_REAL_METHODS) with isMultiTenant()
 * stubbed, following the precedent in DefaultCheckPermissionStrategyTest.
 *
 * orgAdmin is built from the provider's built-in global "Organization Administrator" role
 * (FileAuthenticationProvider.java's LoadRolesTask seeds this role, org=null, orgAdmin=true, on
 * first load -- same mechanism that seeds the built-in "Administrator" sysAdmin role) rather than
 * a custom role created through the builder: addUserToRole() assumes the user and role share one
 * orgId, which cannot express "user in org X holds a global (org=null) role", so the role is
 * attached directly via principal.setRoles() after principalOf() registers the principal in the
 * SecurityEngine session cache. SRPrincipal.createUser()'s ephemeral-SSO-identity path picks up
 * roles set this way (DefaultCheckPermissionStrategy unions them with the provider-stored user's
 * own roles), so this does not require any SecurityTestDataBuilder change.
 *
 * orgAdmin-vs-orgSecurityAdmin role-cascade-vs-permission-cascade pair (originally planned as a
 * separate S7 slice, folded in here after review -- see permission-matrix-actions.md's "orgAdmin
 * 角色级联 vs orgSecurityAdmin 权限级联" callout): checkOrgAdminPermission()'s entry guard accepts
 * EITHER holding the Organization Administrator role (isOrgAdmin) OR a SECURITY_ORGANIZATION ADMIN
 * grant (hasOrgAdminPermission), but the default branch that Action-type resources fall into
 * (`return isOrgAdmin && ActionPermissionService.isOrgAdminAction(type, resource);`) only checks
 * isOrgAdmin. orgSecurityAdmin here mirrors PermissionMatrixResourcesS2Test's persona: a plain user
 * with a SECURITY_ORGANIZATION ADMIN grant and no Organization Administrator role.
 */

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.support.*;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.security.action.ActionPermissionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.stream.Stream;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PermissionMatrixActionsS6Test {

   private static final String ORG_NAME = "actionsMatrixOrg";
   private static final String ORG_ID = "actions_matrix_org_id";

   private static final IdentityID ORG_ADMIN_ROLE = new IdentityID("Organization Administrator", null);

   // Real orgAdminActionExclusions resource key (ActionPermissionService.java), used for the
   // multiTenant-off causality-proof case below -- not a fixture placeholder.
   private static final String EM_MONITORING_CACHE = "monitoring/cache";
   // Real, non-excluded EM_COMPONENT resource key -- used for the role-cascade-vs-permission-
   // cascade pair below. Neither principal is directly granted on this resource.
   private static final String EM_MONITORING_DASHBOARDS = "monitoring/dashboards";
   private static final String DEVICE_RESOURCE = "*";
   private static final String SCHEDULE_OPTION_TIME_RANGE = "timeRange";

   private static SecurityTestDataBuilder builder;
   private static SRPrincipal orgAdmin;
   private static SRPrincipal orgSecurityAdmin;

   @BeforeAll
   static void setupAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg(ORG_NAME, ORG_ID)
         .addUser("orgAdminUser", ORG_ID, "password")
         .addUser("orgSecurityAdminUser", ORG_ID, "password")
         // orgSecurityAdmin: SECURITY_ORGANIZATION ADMIN grant, no Organization Administrator
         // role -- deliberately not granted anything on EM_MONITORING_DASHBOARDS itself.
         .grantPermission(ResourceType.SECURITY_ORGANIZATION,
                          new IdentityID(ORG_NAME, ORG_ID).convertToKey(), ResourceAction.ADMIN,
                          "orgSecurityAdminUser", Identity.USER, ORG_ID);

      // Explicit grant on every real orgAdminActionExclusions entry despite each one being
      // excluded: the point of S6 is proving the exclusion list overrides even an explicit grant,
      // not merely that an unconfigured/never-granted resource defaults to deny.
      for(Resource excluded : ActionPermissionService.orgAdminActionExclusions) {
         builder.grantPermission(excluded.getType(), excluded.getPath(), ResourceAction.ACCESS,
                                 "orgAdminUser", Identity.USER, ORG_ID);
      }

      // DEVICE is granted to orgAdminUser for the role-cascade baseline below -- it is NOT on
      // orgAdminActionExclusions. EM_MONITORING_DASHBOARDS is deliberately left ungranted for
      // both orgAdmin and orgSecurityAdmin -- the role-cascade-vs-permission-cascade pair checks
      // whether each principal's org-admin *status* alone unlocks it, not an explicit grant.
      //
      // DEVICE is granted to orgSecurityAdminUser too, but for a different reason: orgSecurityAdmin
      // holds no Organization Administrator role, so checkOrgAdminPermission()'s default-case
      // guard (`isOrgAdmin && isOrgAdminAction(...)`) evaluates to false and never short-circuits
      // checkPermission() -- unlike orgAdmin, whose role alone satisfies that guard regardless of
      // this grant or of SUtil.isMultiTenant(). Granting orgSecurityAdmin is what lets the
      // isMultiTenant()-gated exclusion check (DefaultCheckPermissionStrategy line ~256) and the
      // explicit-grant check that follows it actually execute.
      builder
         .grantPermission(ResourceType.DEVICE, DEVICE_RESOURCE, ResourceAction.ACCESS,
                          "orgAdminUser", Identity.USER, ORG_ID)
         .grantPermission(ResourceType.DEVICE, DEVICE_RESOURCE, ResourceAction.ACCESS,
                          "orgSecurityAdminUser", Identity.USER, ORG_ID)
         // SCHEDULE_OPTION:timeRange is deliberately left ungranted for both principals -- the
         // probes below check the unconfigured-default behavior, not an explicit-grant override.
         .setup();

      orgAdmin = builder.principalOf("orgAdminUser", ORG_ID);
      orgAdmin.setRoles(new IdentityID[]{ ORG_ADMIN_ROLE });

      orgSecurityAdmin = builder.principalOf("orgSecurityAdminUser", ORG_ID);
   }

   @AfterAll
   static void teardownAll() {
      if(builder != null) {
         builder.teardown();
      }
   }

   // Exhaustive: every entry in the real orgAdminActionExclusions array, not a representative
   // sample. Covers all 16 EM_COMPONENT pages (Monitoring Cache/Cluster/Log/Summary, Settings
   // General/Presentation-OrgSettings/Properties/Security-Provider/Security-SSO/Security-
   // GoogleSignIn/Content-DataSpace/Content-DriversAndPlugins/Logging/Schedule-Settings/
   // Schedule-Status, Notification) plus the 3 SCHEDULE_TASK internal tasks and UPLOAD_DRIVERS.
   @ParameterizedTest(name = "{0}:{1}")
   @MethodSource("orgAdminActionExclusionCases")
   void orgAdmin_deniedOnEveryOrgAdminActionExclusion_whenMultiTenant(
      ResourceType type, String resource) throws Exception
   {
      withMultiTenant(true, () ->
         withContextPrincipal(orgAdmin, () ->
            PermissionMatrixVerifier.of(engine())
               .resource(type, resource)
                  .expectDeny(orgAdmin, ResourceAction.ACCESS)
               .verify()));
   }

   private static Stream<Arguments> orgAdminActionExclusionCases() {
      return Arrays.stream(ActionPermissionService.orgAdminActionExclusions)
         .map(excluded -> Arguments.of(excluded.getType(), excluded.getPath()));
   }

   @Test
   void orgAdmin_emComponentMonitoringCache_allowedByGrant_whenNotMultiTenant() throws Exception {
      withMultiTenant(false, () ->
         withContextPrincipal(orgAdmin, () ->
            PermissionMatrixVerifier.of(engine())
               .resource(ResourceType.EM_COMPONENT, EM_MONITORING_CACHE)
                  .expectAllow(orgAdmin, ResourceAction.ACCESS)
               .verify()));
   }

   // ── orgAdmin role cascade vs orgSecurityAdmin permission cascade (§ S6 doc callout, folded-in
   // former S7) ─────────────────────────────────────────────────────────────────────────────

   @Test
   void orgAdmin_emComponentMonitoringDashboards_allowedViaRoleCascade_whenMultiTenant() throws Exception {
      withMultiTenant(true, () ->
         withContextPrincipal(orgAdmin, () ->
            PermissionMatrixVerifier.of(engine())
               .resource(ResourceType.EM_COMPONENT, EM_MONITORING_DASHBOARDS)
                  .expectAllow(orgAdmin, ResourceAction.ACCESS)
               .verify()));
   }

   @Test
   void orgSecurityAdmin_emComponentMonitoringDashboards_deniedDespiteOrgPermission_whenMultiTenant()
      throws Exception
   {
      withMultiTenant(true, () ->
         withContextPrincipal(orgSecurityAdmin, () ->
            PermissionMatrixVerifier.of(engine())
               .resource(ResourceType.EM_COMPONENT, EM_MONITORING_DASHBOARDS)
                  .expectDeny(orgSecurityAdmin, ResourceAction.ACCESS)
               .verify()));
   }

   // ── DEVICE / SCHEDULE_OPTION probe (§ S6 doc callout) ───────────────────────────────────
   //
   // Each resource has two tests below: an orgAdmin "role-cascade baseline" pinning the actual
   // Issue #75603/#75604 production scenario (orgAdmin's Organization Administrator role alone
   // satisfies checkOrgAdminPermission()'s default-case guard, short-circuiting checkPermission()
   // before SUtil.isMultiTenant() or any explicit grant is ever consulted -- same mechanism as
   // the role-cascade test above, not the isMultiTenant-gated exclusion check), and an
   // orgSecurityAdmin test that actually exercises that isMultiTenant-gated exclusion check plus
   // the explicit-grant / unconfigured-default machinery that follows it, since orgSecurityAdmin
   // holds no Organization Administrator role and so never satisfies that guard.

   @Test
   void orgAdmin_device_allowedViaRoleCascade_whenMultiTenant() throws Exception {
      withMultiTenant(true, () ->
         withContextPrincipal(orgAdmin, () ->
            PermissionMatrixVerifier.of(engine())
               .resource(ResourceType.DEVICE, DEVICE_RESOURCE)
                  .expectAllow(orgAdmin, ResourceAction.ACCESS)
               .verify()));
   }

   @Test
   void orgSecurityAdmin_device_allowedByGrant_whenMultiTenant_notInExclusionList() throws Exception {
      withMultiTenant(true, () ->
         withContextPrincipal(orgSecurityAdmin, () ->
            PermissionMatrixVerifier.of(engine())
               .resource(ResourceType.DEVICE, DEVICE_RESOURCE)
                  .expectAllow(orgSecurityAdmin, ResourceAction.ACCESS)
               .verify()));
   }

   @Test
   void orgAdmin_scheduleOptionTimeRange_allowedViaRoleCascade_whenMultiTenant_unconfigured()
      throws Exception
   {
      withMultiTenant(true, () ->
         withContextPrincipal(orgAdmin, () ->
            PermissionMatrixVerifier.of(engine())
               .resource(ResourceType.SCHEDULE_OPTION, SCHEDULE_OPTION_TIME_RANGE)
                  .expectAllow(orgAdmin, ResourceAction.READ)
               .verify()));
   }

   @Test
   void orgSecurityAdmin_scheduleOptionTimeRange_allowedByDefault_whenMultiTenant_unconfigured()
      throws Exception
   {
      withMultiTenant(true, () ->
         withContextPrincipal(orgSecurityAdmin, () ->
            PermissionMatrixVerifier.of(engine())
               .resource(ResourceType.SCHEDULE_OPTION, SCHEDULE_OPTION_TIME_RANGE)
                  .expectAllow(orgSecurityAdmin, ResourceAction.READ)
               .verify()));
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

   private static void withContextPrincipal(SRPrincipal principal, ThrowingRunnable action) {
      ThreadContext.setContextPrincipal(principal);

      try {
         action.run();
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
      finally {
         ThreadContext.setContextPrincipal(null);
      }
   }

   private static SecurityEngine engine() {
      return SecurityEngine.getSecurity();
   }
}
