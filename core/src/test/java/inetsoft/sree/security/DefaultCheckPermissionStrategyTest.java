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

import inetsoft.sree.internal.SUtil;
import inetsoft.test.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DefaultCheckPermissionStrategy — unit tests
 *
 * checkPermission() decision tree:
 *  ├─ [A] isAllowedDefaultGlobalVSAction?           YES → true  (not tested: low ROI)
 *  ├─ [B] isSysAdmin / isSiteAdmin?                 YES → true
 *  ├─ [C] isOrgAdmin + EM resource special handling → true / false  (existing coverage)
 *  ├─ [D] provider.getPermission() direct check     → true / false
 *  ├─ [E] perm==null + default-granted type         → true
 *  ├─ [F] perm==null + hierarchical fallback        → true / false
 *  └─ [G] no permission on non-default type         → false  (covered in [D][F])
 *
 * KEY contracts:
 *   - Any principal with system administrator role bypasses all permission checks
 *   - Specific resource types are allowed by default when no permission is configured
 *   - Hierarchical resources fall back to ancestor permissions when no direct grant exists
 *
 * Design gaps (not tested):
 *   - [A] Shared global VS: depends on isDefaultVSGloballyVisible, low ROI
 *   - [H] SECURITY_USER/GROUP BFS group traversal: deserves its own test class
 *
 * [Path B][Path E] use Spring-based strategy (real SecurityEngine, no storage writes)
 * [Path D][Path F] use mock-based strategy (SecurityProvider stubbed, no storage I/O)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class DefaultCheckPermissionStrategyTest {

   // Spring-based strategy — uses real SecurityEngine
   static DefaultCheckPermissionStrategy strategy;
   static SRPrincipal sysAdmin;
   static SRPrincipal org_admin;
   static SRPrincipal normalUser;

   // Mock-based strategy — provider is fully stubbed, no storage I/O
   static final String TEST_ORG  = "testOrg";
   static final String TEST_USER = "testUser";
   static final String TEST_ROLE = "testRole";
   SecurityProvider mockProvider;
   DefaultCheckPermissionStrategy mockStrategy;
   SRPrincipal mockUser;

   @BeforeAll
   static void before(@Autowired SecurityEngine securityEngine) throws Exception {
      securityEngine.enableSecurity();
      SUtil.setMultiTenant(true);

      strategy = new DefaultCheckPermissionStrategy(securityEngine.getSecurityProvider());

      // [Path B] "Administrator" satisfies isSystemAdministratorRole() (AuthenticationProvider default)
      IdentityID[] sysAdminRoles = { new IdentityID("Administrator", null) };
      sysAdmin = new SRPrincipal(new IdentityID("sysAdmin", "org0"), sysAdminRoles,
                                  new String[0], "org0", Tool.getSecureRandom().nextLong());

      IdentityID[] orgAdministrator = { new IdentityID("Organization Administrator", null) };
      org_admin = new SRPrincipal(new IdentityID("org_admin", "org0"), orgAdministrator,
                                   new String[0], "org0", Tool.getSecureRandom().nextLong());

      IdentityID[] everyoneRoles = { new IdentityID("Everyone", "org0") };
      normalUser = new SRPrincipal(new IdentityID("normalUser", "org0"), everyoneRoles,
                                    new String[0], "org0", Tool.getSecureRandom().nextLong());
   }

   @BeforeEach
   void setUpMockStrategy() {
      // mock-based setup: strategy driven entirely by stubbed provider calls
      mockProvider = Mockito.mock(SecurityProvider.class);
      mockStrategy = new DefaultCheckPermissionStrategy(mockProvider);

      mockUser = new SRPrincipal(
         new IdentityID(TEST_USER, TEST_ORG),
         new IdentityID[]{ new IdentityID(TEST_ROLE, TEST_ORG) },
         new String[0], TEST_ORG, Tool.getSecureRandom().nextLong());

      // user not in provider → SSO identity derived from SRPrincipal itself is used (lines 70-95)
      lenient().when(mockProvider.getUser(any())).thenReturn(null);
      lenient().when(mockProvider.getGroup(any())).thenReturn(null);
      lenient().when(mockProvider.getRole(any())).thenReturn(null);
      lenient().when(mockProvider.getRoles(any())).thenReturn(new IdentityID[0]);
      lenient().when(mockProvider.getUserGroups(any())).thenReturn(new String[0]);
      lenient().when(mockProvider.getAllGroups(any(IdentityID[].class))).thenReturn(new IdentityID[0]);
      // no admin role elevation
      lenient().when(mockProvider.isSystemAdministratorRole(any())).thenReturn(false);
      lenient().when(mockProvider.isOrgAdministratorRole(any())).thenReturn(false);
      // no role hierarchy: return the input roles unchanged
      lenient().when(mockProvider.getAllRoles(any(IdentityID[].class))).thenAnswer(inv -> inv.getArgument(0));
      lenient().when(mockProvider.getOrgNameFromID(anyString())).thenReturn(TEST_ORG);
      // no permissions by default; individual tests override for specific resources
      lenient().when(mockProvider.getPermission(any(ResourceType.class), anyString(), anyString())).thenReturn(null);
      lenient().when(mockProvider.getPermission(any(ResourceType.class), anyString())).thenReturn(null);
      lenient().when(mockProvider.getPermission(any(ResourceType.class), any(IdentityID.class))).thenReturn(null);
      // suppress org-level admin permission check (DefaultCheckPermissionStrategy line 59)
      lenient().when(mockProvider.getPermission(eq(ResourceType.SECURITY_ORGANIZATION), any(IdentityID.class))).thenReturn(null);
      // authentication chain stub used by getCurrentProvider (line 596)
      AuthenticationProvider mockAuthProvider = Mockito.mock(AuthenticationProvider.class);
      lenient().when(mockProvider.getAuthenticationProvider()).thenReturn(mockAuthProvider);
      // organization stub for PermissionChecker.checkRolePermission (line 191)
      Organization mockOrg = Mockito.mock(Organization.class);
      lenient().when(mockOrg.getOrganizationID()).thenReturn(TEST_ORG);
      lenient().when(mockOrg.getRoles()).thenReturn(new IdentityID[0]);
      lenient().when(mockProvider.getOrganization(anyString())).thenReturn(mockOrg);
   }

   // ─────────────────────────────────────────────────────────────────
   // Existing tests (unchanged)
   // ─────────────────────────────────────────────────────────────────

   @Test
   void checkNormalUserNoPermissionOfEM() {
      try(MockedStatic<SUtil> mocked = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS)) {
         mocked.when(SUtil::isMultiTenant).thenReturn(true);

         assertFalse(
            strategy.checkPermission(normalUser, ResourceType.EM, "*", ResourceAction.ACCESS),
            "normal user no permission of em");
      }
   }

   @ParameterizedTest
   @MethodSource("provideEMTestCases")
   void checkOrgAdminEMPermissions(String[] pages, Boolean[] expectedResults, String message) {
      ArrayList<Boolean> actualResults = new ArrayList<>();

      try(MockedStatic<SUtil> mocked = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS)) {
         mocked.when(SUtil::isMultiTenant).thenReturn(true);

         for(String page : pages) {
            actualResults.add(
               strategy.checkPermission(org_admin, ResourceType.EM_COMPONENT, page,
                                        ResourceAction.ACCESS));
         }
      }
      assertArrayEquals(expectedResults, actualResults.toArray(new Boolean[0]), message);
   }

   private static Stream<Arguments> provideEMTestCases() {
      return Stream.of(
         Arguments.of(
            new String[]{"monitoring/cache", "monitoring/cluster", "monitoring/log",
                         "monitoring/viewsheets", "monitoring/queries", "monitoring/summary", "monitoring/users"},
            new Boolean[]{false, false, false, true, true, false, true},
            "The org administrator permission of monitor not right"
         ),
         Arguments.of(
            new String[]{"auditing", "notification", "settings", "monitoring",
                         "settings/general", "settings/content", "settings/logging", "settings/presentation",
                         "settings/properties", "settings/schedule", "settings/security"},
            new Boolean[]{true, false, true, true, false, true, false, true, false, true, true},
            "The org administrator permission of EM not right"
         ),
         Arguments.of(
            new String[]{"settings/security/provider", "settings/security/sso",
                         "settings/security/actions", "settings/security/users", "settings/security/googleSignIn"},
            new Boolean[]{false, false, true, true, false},
            "The org administrator permission of security not right"
         ),
         Arguments.of(
            new String[]{"settings/content/data-space", "settings/content/drivers-and-plugins",
                         "settings/content/repository", "settings/content/materialized-views"},
            new Boolean[]{false, false, true, true},
            "The org administrator permission of content not right"
         ),
         Arguments.of(
            new String[]{"settings/schedule/settings", "settings/schedule/cycles",
                         "settings/schedule/status", "settings/schedule/tasks"},
            new Boolean[]{false, true, false, true},
            "The org administrator permission of schedule not right"
         ),
         Arguments.of(
            new String[]{"settings/presentation/themes",
                         "settings/presentation/org-settings", "settings/presentation/settings"},
            new Boolean[]{true, false, true},
            "The org administrator permission of presentation not right"
         )
      );
   }

   // ─────────────────────────────────────────────────────────────────
   // [Path C] Org admin checking a role that does not exist
   // ─────────────────────────────────────────────────────────────────

   // checkOrgAdminPermission's SECURITY_ROLE branch (line 571-575) unconditionally returned
   // false when the target role did not exist, unlike the analogous SECURITY_USER branch
   // (line 536-538, "Bug #66393") which falls back to comparing orgIDs. This meant an org
   // admin looking up a non-existent role in their own org got permission denied (401)
   // instead of reaching the "not found" (404) check in the calling API layer. (Bug #75545)
   @Test
   void orgAdminCanCheckNonExistentRoleInOwnOrg() {
      String resource = new IdentityID("noExistRole", "org0").convertToKey();

      try(MockedStatic<SUtil> mocked = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS)) {
         mocked.when(SUtil::isMultiTenant).thenReturn(true);

         assertTrue(
            strategy.checkPermission(org_admin, ResourceType.SECURITY_ROLE, resource, ResourceAction.ADMIN),
            "org admin should have admin permission over a non-existent role in their own org " +
            "so the caller can surface a 'not found' rather than 'forbidden' result");
      }
   }

   // Security boundary: a missing role in a *different* org must still be denied, so this
   // doesn't leak role existence/permission across organizations.
   @Test
   void orgAdminCannotCheckNonExistentRoleInOtherOrg() {
      String resource = new IdentityID("noExistRole", "otherOrg").convertToKey();

      try(MockedStatic<SUtil> mocked = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS)) {
         mocked.when(SUtil::isMultiTenant).thenReturn(true);

         assertFalse(
            strategy.checkPermission(org_admin, ResourceType.SECURITY_ROLE, resource, ResourceAction.ADMIN),
            "org admin must not gain permission over a non-existent role outside their own org");
      }
   }

   // ─────────────────────────────────────────────────────────────────
   // [Path B] System administrator bypasses all permission checks
   // ─────────────────────────────────────────────────────────────────

   // [Path B] Principal with "Administrator" role causes isSysAdmin==true (line 206-210),
   // short-circuiting all remaining evaluation and returning true unconditionally.
   // Condition: xPrincipal.getAllRoles(provider) contains a role where isSystemAdministratorRole()==true
   @ParameterizedTest
   @MethodSource("sysAdminResourceCases")
   void sysAdminBypassesAllResources(ResourceType type, String resource, ResourceAction action) {
      try(MockedStatic<SUtil> mocked = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS)) {
         mocked.when(SUtil::isMultiTenant).thenReturn(true);

         assertTrue(
            strategy.checkPermission(sysAdmin, type, resource, action),
            "system admin must bypass permission checks for " + type + "/" + resource);
      }
   }

   private static Stream<Arguments> sysAdminResourceCases() {
      return Stream.of(
         // ✓ regular viewsheet read
         Arguments.of(ResourceType.VIEWSHEET, "vs/test", ResourceAction.READ),
         // ✓ asset (hierarchical content resource) with write action
         Arguments.of(ResourceType.ASSET, "/asset/test", ResourceAction.WRITE),
         // ✓ data source read
         Arguments.of(ResourceType.DATA_SOURCE, "myDS", ResourceAction.READ),
         // ✓ EM page excluded from org-admin — sysAdmin must still return true
         Arguments.of(ResourceType.EM_COMPONENT, "settings/security/provider", ResourceAction.ACCESS)
      );
   }

   // ─────────────────────────────────────────────────────────────────
   // [Path E] Specific resource types are allowed by default when no permission is configured
   // ─────────────────────────────────────────────────────────────────

   // [Path E] When perm==null (no permission configured) and useParent==true, certain resource
   // types return true from the default-granted block (lines 269-291) without consulting any grant.
   // Condition: type matches the default-granted block; no explicit permission defined
   @ParameterizedTest
   @MethodSource("defaultGrantedResourceCases")
   void noPermissionDefaultsToGranted(ResourceType type, String resource) {
      try(MockedStatic<SUtil> mocked = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS)) {
         mocked.when(SUtil::isMultiTenant).thenReturn(true);

         assertTrue(
            strategy.checkPermission(normalUser, type, resource, ResourceAction.READ),
            "resource type " + type + " should be granted by default when no permission is defined");
      }
   }

   private static Stream<Arguments> defaultGrantedResourceCases() {
      return Stream.of(
         // ✓ user-owned dashboard area is always accessible
         Arguments.of(ResourceType.MY_DASHBOARDS, "myDashboard"),
         // ✓ toolbar action with no custom restriction
         Arguments.of(ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Print"),
         // ✓ report export format
         Arguments.of(ResourceType.REPORT_EXPORT, "PDF"),
         // ✓ share panel
         Arguments.of(ResourceType.SHARE, "share"),
         // ✓ portal tabs with no custom grants
         Arguments.of(ResourceType.PORTAL_TAB, "Dashboard"),
         Arguments.of(ResourceType.PORTAL_TAB, "Schedule"),
         // ✓ schedule option
         Arguments.of(ResourceType.SCHEDULE_OPTION, "option"),
         // ✓ AI assistant
         Arguments.of(ResourceType.AI_ASSISTANT, "ai")
      );
   }

   // ─────────────────────────────────────────────────────────────────
   // [Path D] Direct permission grant on a resource controls access — mock-based
   // ─────────────────────────────────────────────────────────────────

   // [Path D] provider.getPermission() returns a Permission object; the result is determined
   // by whether the identity (user directly or via role) appears in the grant entries.
   // Condition: perm!=null; PermissionChecker.checkPermission(identity, perm, action) evaluated at line 261
   @ParameterizedTest
   @MethodSource("directPermissionCases")
   void directPermissionGrantControls(Permission perm, boolean expected) {
      try(MockedStatic<SUtil> sutilMock = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
          MockedStatic<OrganizationManager> omMock =
             Mockito.mockStatic(OrganizationManager.class, Mockito.CALLS_REAL_METHODS))
      {
         sutilMock.when(SUtil::isMultiTenant).thenReturn(false);
         sutilMock.when(() -> SUtil.isInternalUser(any())).thenReturn(false);

         OrganizationManager mockOM = mock(OrganizationManager.class);
         omMock.when(OrganizationManager::getInstance).thenReturn(mockOM);
         omMock.when(OrganizationManager::getCurrentOrgName).thenReturn(TEST_ORG);
         when(mockOM.getCurrentOrgID()).thenReturn(TEST_ORG);
         when(mockOM.getCurrentOrgID(any())).thenReturn(TEST_ORG);
         when(mockOM.isSiteAdmin(any(Principal.class))).thenReturn(false);

         when(mockProvider.getPermission(eq(ResourceType.VIEWSHEET), eq("vs/test"), eq(TEST_ORG)))
            .thenReturn(perm);

         assertEquals(expected,
            mockStrategy.checkPermission(mockUser, ResourceType.VIEWSHEET, "vs/test", ResourceAction.READ));
      }
   }

   private static Stream<Arguments> directPermissionCases() {
      return Stream.of(
         // ✓ user directly granted READ
         Arguments.of(grantedPermission(TEST_USER, TEST_ORG, ResourceAction.READ, false), true),
         // ✓ role held by the user is granted READ
         Arguments.of(roleGrantedPermission(TEST_ROLE, TEST_ORG, ResourceAction.READ, false), true),
         // ✗ no permission defined → falls through to return false
         Arguments.of(null, false)
      );
   }

   // [Path D] SECURITY_ROLE with only ASSIGN must not imply READ/WRITE/DELETE/ADMIN (#75567).
   // Regression: hasResourcePermission incorrectly checked ASSIGN grants instead of ADMIN,
   // granting full access to any action when only ASSIGN was configured.
   @ParameterizedTest
   @MethodSource("securityRoleAssignOnlyCases")
   void securityRoleAssignOnlyDoesNotImplyOtherActions(ResourceAction action, boolean expected) {
      String roleResource = new IdentityID("targetRole", TEST_ORG).convertToKey();
      Permission assignOnlyPerm = grantedPermission(TEST_USER, TEST_ORG, ResourceAction.ASSIGN, false);

      try(MockedStatic<SUtil> sutilMock = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
          MockedStatic<OrganizationManager> omMock =
             Mockito.mockStatic(OrganizationManager.class, Mockito.CALLS_REAL_METHODS))
      {
         sutilMock.when(SUtil::isMultiTenant).thenReturn(false);
         sutilMock.when(() -> SUtil.isInternalUser(any())).thenReturn(false);

         OrganizationManager mockOM = mock(OrganizationManager.class);
         omMock.when(OrganizationManager::getInstance).thenReturn(mockOM);
         omMock.when(OrganizationManager::getCurrentOrgName).thenReturn(TEST_ORG);
         when(mockOM.getCurrentOrgID()).thenReturn(TEST_ORG);
         when(mockOM.getCurrentOrgID(any())).thenReturn(TEST_ORG);
         when(mockOM.isSiteAdmin(any(Principal.class))).thenReturn(false);

         when(mockProvider.getPermission(eq(ResourceType.SECURITY_ROLE), eq(roleResource), eq(TEST_ORG)))
            .thenReturn(assignOnlyPerm);

         assertEquals(expected,
            mockStrategy.checkPermission(mockUser, ResourceType.SECURITY_ROLE, roleResource, action));
      }
   }

   private static Stream<Arguments> securityRoleAssignOnlyCases() {
      return Stream.of(
         Arguments.of(ResourceAction.ASSIGN, true),
         Arguments.of(ResourceAction.READ, false),
         Arguments.of(ResourceAction.WRITE, false),
         Arguments.of(ResourceAction.DELETE, false),
         Arguments.of(ResourceAction.ADMIN, false)
      );
   }

   // Bug #75574: an org administrator (SECURITY_ORGANIZATION ADMIN on their own org) must not
   // be granted SECURITY_ROLE ADMIN on the global system administrator role via the org-admin
   // fallback (line ~382). That fallback previously granted access to ANY role once the caller
   // had org-admin permission on their own org, without checking the target role's org or
   // whether it is the system administrator role.
   @Test
   void orgAdminCannotAccessGlobalSystemAdministratorRole() {
      IdentityID sysAdminRoleId = new IdentityID("Administrator", null);
      String sysAdminRoleResource = sysAdminRoleId.convertToKey();
      IdentityID orgIdentityID = new IdentityID(TEST_ORG, TEST_ORG);

      Role sysAdminRole = mock(Role.class);
      when(sysAdminRole.getIdentityID()).thenReturn(sysAdminRoleId);
      when(sysAdminRole.getOrganizationID()).thenReturn(null);

      Permission orgAdminPermission = grantedPermission(TEST_USER, TEST_ORG, ResourceAction.ADMIN, false);

      try(MockedStatic<SUtil> sutilMock = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
          MockedStatic<OrganizationManager> omMock =
             Mockito.mockStatic(OrganizationManager.class, Mockito.CALLS_REAL_METHODS))
      {
         sutilMock.when(SUtil::isMultiTenant).thenReturn(false);
         sutilMock.when(() -> SUtil.isInternalUser(any())).thenReturn(false);

         OrganizationManager mockOM = mock(OrganizationManager.class);
         omMock.when(OrganizationManager::getInstance).thenReturn(mockOM);
         omMock.when(OrganizationManager::getCurrentOrgName).thenReturn(TEST_ORG);
         when(mockOM.getCurrentOrgID()).thenReturn(TEST_ORG);
         when(mockOM.getCurrentOrgID(any())).thenReturn(TEST_ORG);
         when(mockOM.isSiteAdmin(any(Principal.class))).thenReturn(false);

         when(mockProvider.getRole(eq(sysAdminRoleId))).thenReturn(sysAdminRole);
         when(mockProvider.isSystemAdministratorRole(eq(sysAdminRoleId))).thenReturn(true);
         when(mockProvider.isOrgAdministratorRole(eq(new IdentityID(TEST_ROLE, TEST_ORG)))).thenReturn(true);
         when(mockProvider.getPermission(eq(ResourceType.SECURITY_ORGANIZATION), eq(orgIdentityID)))
            .thenReturn(orgAdminPermission);
         when(mockProvider.getPermission(eq(ResourceType.SECURITY_ORGANIZATION), eq(orgIdentityID), eq(TEST_ORG)))
            .thenReturn(orgAdminPermission);

         assertFalse(
            mockStrategy.checkPermission(mockUser, ResourceType.SECURITY_ROLE, sysAdminRoleResource,
                                         ResourceAction.ADMIN),
            "org administrator must not gain ADMIN over the global system administrator role");
      }
   }

   // Bug #75574 (follow-up): a plain user granted ADMIN specifically on the "Organization Roles"
   // node of their own org (a standard delegation action, not requiring the org-admin role or
   // SECURITY_ORGANIZATION permission) must not be able to manage global/org-less roles such as
   // "Administrator" or "Organization Administrator" through that grant.
   @Test
   void organizationRolesDelegationCannotAccessGlobalRole() {
      IdentityID sysAdminRoleId = new IdentityID("Administrator", null);
      String sysAdminRoleResource = sysAdminRoleId.convertToKey();
      IdentityID orgRolesNodeId = new IdentityID("Organization Roles", TEST_ORG);

      Role sysAdminRole = mock(Role.class);
      when(sysAdminRole.getIdentityID()).thenReturn(sysAdminRoleId);
      when(sysAdminRole.getOrganizationID()).thenReturn(null);

      Permission orgRolesPermission = grantedPermission(TEST_USER, TEST_ORG, ResourceAction.ADMIN, false);

      try(MockedStatic<SUtil> sutilMock = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
          MockedStatic<OrganizationManager> omMock =
             Mockito.mockStatic(OrganizationManager.class, Mockito.CALLS_REAL_METHODS))
      {
         sutilMock.when(SUtil::isMultiTenant).thenReturn(false);
         sutilMock.when(() -> SUtil.isInternalUser(any())).thenReturn(false);

         OrganizationManager mockOM = mock(OrganizationManager.class);
         omMock.when(OrganizationManager::getInstance).thenReturn(mockOM);
         omMock.when(OrganizationManager::getCurrentOrgName).thenReturn(TEST_ORG);
         when(mockOM.getCurrentOrgID()).thenReturn(TEST_ORG);
         when(mockOM.getCurrentOrgID(any())).thenReturn(TEST_ORG);
         when(mockOM.isSiteAdmin(any(Principal.class))).thenReturn(false);

         // mockUser is NOT an org admin — access comes only from the direct "Organization
         // Roles" node grant, not from any org-admin role or SECURITY_ORGANIZATION permission.
         when(mockProvider.getRole(eq(sysAdminRoleId))).thenReturn(sysAdminRole);
         when(mockProvider.isSystemAdministratorRole(eq(sysAdminRoleId))).thenReturn(true);
         when(mockProvider.getPermission(eq(ResourceType.SECURITY_ROLE), eq(orgRolesNodeId), eq(TEST_ORG)))
            .thenReturn(orgRolesPermission);

         assertFalse(
            mockStrategy.checkPermission(mockUser, ResourceType.SECURITY_ROLE, sysAdminRoleResource,
                                         ResourceAction.ADMIN),
            "\"Organization Roles\" delegation must not extend to global/org-less roles");
      }
   }

   // Bug #75574 follow-up [tester-reported Bug 1]: checkOrgAdminPermission()'s SECURITY_ROLE
   // case previously allowed ANY org-less role through via a "getOrganizationID() == null"
   // disjunct, gated only by isSiteAdmin (which checks isSystemAdministratorRole). The built-in
   // "Organization Administrator" role is global (org-less) but is NOT itself flagged as the
   // system administrator role, so it slipped past that guard — letting any org-admin-permission
   // holder manage the very role that grants org-admin privilege in the first place.
   @Test
   void orgAdminCannotAccessGlobalOrganizationAdministratorRole() {
      IdentityID orgAdminRoleId = new IdentityID("Organization Administrator", null);
      String orgAdminRoleResource = orgAdminRoleId.convertToKey();
      IdentityID orgIdentityID = new IdentityID(TEST_ORG, TEST_ORG);

      Role orgAdminRole = mock(Role.class);
      when(orgAdminRole.getIdentityID()).thenReturn(orgAdminRoleId);
      when(orgAdminRole.getOrganizationID()).thenReturn(null);

      Permission orgAdminPermission = grantedPermission(TEST_USER, TEST_ORG, ResourceAction.ADMIN, false);

      try(MockedStatic<SUtil> sutilMock = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
          MockedStatic<OrganizationManager> omMock =
             Mockito.mockStatic(OrganizationManager.class, Mockito.CALLS_REAL_METHODS))
      {
         sutilMock.when(SUtil::isMultiTenant).thenReturn(false);
         sutilMock.when(() -> SUtil.isInternalUser(any())).thenReturn(false);

         OrganizationManager mockOM = mock(OrganizationManager.class);
         omMock.when(OrganizationManager::getInstance).thenReturn(mockOM);
         omMock.when(OrganizationManager::getCurrentOrgName).thenReturn(TEST_ORG);
         when(mockOM.getCurrentOrgID()).thenReturn(TEST_ORG);
         when(mockOM.getCurrentOrgID(any())).thenReturn(TEST_ORG);
         when(mockOM.isSiteAdmin(any(Principal.class))).thenReturn(false);

         when(mockProvider.getRole(eq(orgAdminRoleId))).thenReturn(orgAdminRole);
         // NOT flagged as system administrator — that's exactly the gap: isSiteAdmin checked
         // isSystemAdministratorRole only, never isOrgAdministratorRole or org-less-ness alone.
         when(mockProvider.isSystemAdministratorRole(eq(orgAdminRoleId))).thenReturn(false);
         // Stubbed against the actual target role's identity (org-less "Organization
         // Administrator"), matching what production FileAuthenticationProvider reports for
         // that built-in role — NOT the caller's own role, so this genuinely exercises the
         // isGlobalOrgAdminRole branch in DefaultCheckPermissionStrategy.
         when(mockProvider.isOrgAdministratorRole(eq(orgAdminRoleId))).thenReturn(true);
         when(mockProvider.getPermission(eq(ResourceType.SECURITY_ORGANIZATION), eq(orgIdentityID)))
            .thenReturn(orgAdminPermission);
         when(mockProvider.getPermission(eq(ResourceType.SECURITY_ORGANIZATION), eq(orgIdentityID), eq(TEST_ORG)))
            .thenReturn(orgAdminPermission);

         assertFalse(
            mockStrategy.checkPermission(mockUser, ResourceType.SECURITY_ROLE, orgAdminRoleResource,
                                         ResourceAction.ADMIN),
            "org administrator must not gain ADMIN over the global Organization Administrator role");
         assertFalse(
            mockStrategy.checkPermission(mockUser, ResourceType.SECURITY_ROLE, orgAdminRoleResource,
                                         ResourceAction.WRITE),
            "org administrator must not gain WRITE over the global Organization Administrator role");
         assertFalse(
            mockStrategy.checkPermission(mockUser, ResourceType.SECURITY_ROLE, orgAdminRoleResource,
                                         ResourceAction.DELETE),
            "org administrator must not gain DELETE over the global Organization Administrator role");
      }
   }

   // Bug #75706: an org admin must still be able to READ (view, e.g. the EM Security → Users →
   // "Organization Administrator" mode pane) the built-in, org-less "Organization Administrator"
   // role — that's the role that grants their own privilege, and hiding it produced a misleading
   // "System Administrator level identities" permission error. Visibility is the only exception;
   // ADMIN/WRITE/DELETE remain blocked (see orgAdminCannotAccessGlobalOrganizationAdministratorRole).
   @Test
   void orgAdminCanReadGlobalOrganizationAdministratorRole() {
      IdentityID orgAdminRoleId = new IdentityID("Organization Administrator", null);
      String orgAdminRoleResource = orgAdminRoleId.convertToKey();
      IdentityID orgIdentityID = new IdentityID(TEST_ORG, TEST_ORG);

      Role orgAdminRole = mock(Role.class);
      when(orgAdminRole.getIdentityID()).thenReturn(orgAdminRoleId);
      when(orgAdminRole.getOrganizationID()).thenReturn(null);

      try(MockedStatic<SUtil> sutilMock = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
          MockedStatic<OrganizationManager> omMock =
             Mockito.mockStatic(OrganizationManager.class, Mockito.CALLS_REAL_METHODS))
      {
         sutilMock.when(SUtil::isMultiTenant).thenReturn(false);
         sutilMock.when(() -> SUtil.isInternalUser(any())).thenReturn(false);

         OrganizationManager mockOM = mock(OrganizationManager.class);
         omMock.when(OrganizationManager::getInstance).thenReturn(mockOM);
         omMock.when(OrganizationManager::getCurrentOrgName).thenReturn(TEST_ORG);
         when(mockOM.getCurrentOrgID()).thenReturn(TEST_ORG);
         when(mockOM.getCurrentOrgID(any())).thenReturn(TEST_ORG);
         when(mockOM.isSiteAdmin(any(Principal.class))).thenReturn(false);

         when(mockProvider.getRole(eq(orgAdminRoleId))).thenReturn(orgAdminRole);
         when(mockProvider.isSystemAdministratorRole(eq(orgAdminRoleId))).thenReturn(false);
         when(mockProvider.isOrgAdministratorRole(eq(orgAdminRoleId))).thenReturn(true);
         // caller's own role must itself be org-admin-flagged to reach the org-admin switch at
         // all (isOrgAdmin gate) — mirrors a real org admin, distinct from the target-role stub.
         when(mockProvider.isOrgAdministratorRole(eq(new IdentityID(TEST_ROLE, TEST_ORG)))).thenReturn(true);
         // deliberately no SECURITY_ORGANIZATION ADMIN grant configured — READ visibility must
         // come solely from the org-admin-permission + built-in-role exception, not delegation.
         when(mockProvider.getPermission(eq(ResourceType.SECURITY_ORGANIZATION), eq(orgIdentityID)))
            .thenReturn(null);
         when(mockProvider.getPermission(eq(ResourceType.SECURITY_ORGANIZATION), eq(orgIdentityID), eq(TEST_ORG)))
            .thenReturn(null);

         assertTrue(
            mockStrategy.checkPermission(mockUser, ResourceType.SECURITY_ROLE, orgAdminRoleResource,
                                         ResourceAction.READ),
            "org administrator must be able to view the global Organization Administrator role");
      }
   }

   // Bug #75706 follow-up: isOrgAdministratorRole() may match by role NAME alone (e.g.
   // DatabaseAuthenticationProvider matches any org's custom role sharing a configured name),
   // independent of which org actually owns that role instance. The read-visibility exception
   // must require the role to be genuinely org-less, not merely name-flagged, so it can't be used
   // to view a same-named custom role that actually belongs to a different org.
   @Test
   void readExceptionDoesNotApplyToSameNamedRoleScopedToAnotherOrg() {
      String otherOrg = "otherOrg";
      IdentityID customRoleId = new IdentityID("Organization Administrator", otherOrg);
      String customRoleResource = customRoleId.convertToKey();

      Role customRole = mock(Role.class);
      when(customRole.getIdentityID()).thenReturn(customRoleId);
      when(customRole.getOrganizationID()).thenReturn(otherOrg);

      try(MockedStatic<SUtil> sutilMock = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
          MockedStatic<OrganizationManager> omMock =
             Mockito.mockStatic(OrganizationManager.class, Mockito.CALLS_REAL_METHODS))
      {
         sutilMock.when(SUtil::isMultiTenant).thenReturn(false);
         sutilMock.when(() -> SUtil.isInternalUser(any())).thenReturn(false);

         OrganizationManager mockOM = mock(OrganizationManager.class);
         omMock.when(OrganizationManager::getInstance).thenReturn(mockOM);
         omMock.when(OrganizationManager::getCurrentOrgName).thenReturn(TEST_ORG);
         when(mockOM.getCurrentOrgID()).thenReturn(TEST_ORG);
         when(mockOM.getCurrentOrgID(any())).thenReturn(TEST_ORG);
         when(mockOM.isSiteAdmin(any(Principal.class))).thenReturn(false);

         when(mockProvider.getRole(eq(customRoleId))).thenReturn(customRole);
         when(mockProvider.isSystemAdministratorRole(eq(customRoleId))).thenReturn(false);
         // flagged as an org-admin-type role purely by name, same as the built-in role would be —
         // but this instance belongs to otherOrg, not TEST_ORG, and is not org-less.
         when(mockProvider.isOrgAdministratorRole(eq(customRoleId))).thenReturn(true);
         // caller's own role must itself be org-admin-flagged to reach the org-admin switch at
         // all (isOrgAdmin gate) — otherwise this would trivially return false for the wrong reason.
         when(mockProvider.isOrgAdministratorRole(eq(new IdentityID(TEST_ROLE, TEST_ORG)))).thenReturn(true);

         assertFalse(
            mockStrategy.checkPermission(mockUser, ResourceType.SECURITY_ROLE, customRoleResource,
                                         ResourceAction.READ),
            "a same-named org-admin-flagged role belonging to a different org must not be readable");
      }
   }

   // Bug #75574 follow-up [tester-reported Bug 2 / S2-GLOBAL-ROLE-ROOT]: the private ADMIN-
   // cumulative getPermission() helper unconditionally merged grants from BOTH the
   // "Organization Roles" and "Roles" (global) permission roots into one synthetic Permission
   // for any SECURITY_ROLE ADMIN check, regardless of which root the checked role actually
   // belongs to. An ADMIN grant on an org's "Organization Roles" root must not leak onto a
   // global (org-less) role, and vice versa.
   @Test
   void organizationRolesRootGrantDoesNotLeakOntoGlobalRoleViaAdminCumulativeHelper() {
      IdentityID globalRoleId = new IdentityID("Designer", null);
      String globalRoleResource = globalRoleId.convertToKey();
      IdentityID orgRolesNodeId = new IdentityID("Organization Roles", TEST_ORG);

      Role globalRole = mock(Role.class);
      when(globalRole.getIdentityID()).thenReturn(globalRoleId);
      when(globalRole.getOrganizationID()).thenReturn(null);

      Permission orgRolesRootPermission = grantedPermission(TEST_USER, TEST_ORG, ResourceAction.ADMIN, false);

      try(MockedStatic<SUtil> sutilMock = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
          MockedStatic<OrganizationManager> omMock =
             Mockito.mockStatic(OrganizationManager.class, Mockito.CALLS_REAL_METHODS))
      {
         sutilMock.when(SUtil::isMultiTenant).thenReturn(false);
         sutilMock.when(() -> SUtil.isInternalUser(any())).thenReturn(false);

         OrganizationManager mockOM = mock(OrganizationManager.class);
         omMock.when(OrganizationManager::getInstance).thenReturn(mockOM);
         omMock.when(OrganizationManager::getCurrentOrgName).thenReturn(TEST_ORG);
         when(mockOM.getCurrentOrgID()).thenReturn(TEST_ORG);
         when(mockOM.getCurrentOrgID(any())).thenReturn(TEST_ORG);
         when(mockOM.isSiteAdmin(any(Principal.class))).thenReturn(false);

         when(mockProvider.getRole(eq(globalRoleId))).thenReturn(globalRole);
         // grant only exists on the "Organization Roles" root (2-arg lookup, exactly as used
         // by the ADMIN-cumulative helper) — never on the "Roles" (global) root, and never as
         // a SECURITY_ORGANIZATION or org-admin-role grant, to isolate this code path.
         when(mockProvider.getPermission(eq(ResourceType.SECURITY_ROLE), eq(orgRolesNodeId)))
            .thenReturn(orgRolesRootPermission);

         assertFalse(
            mockStrategy.checkPermission(mockUser, ResourceType.SECURITY_ROLE, globalRoleResource,
                                         ResourceAction.ADMIN),
            "ADMIN on the org's \"Organization Roles\" root must not leak onto a global role");
      }
   }

   // PR #4187 review follow-up: the ADMIN-cumulative helper's SECURITY_ROLE branch checked only
   // "does the target role have SOME org" (getOrganizationID() != null), not "does that org match
   // the current org context" — so an ADMIN grant on org A's "Organization Roles" node leaked
   // onto a specific role belonging to a completely different org B, a cross-tenant escalation
   // distinct from the org-less/global-role leak covered above.
   @Test
   void organizationRolesRootGrantDoesNotLeakOntoDifferentOrgsRole() {
      String otherOrg = "otherOrg";
      IdentityID otherOrgRoleId = new IdentityID("otherOrgRole", otherOrg);
      String otherOrgRoleResource = otherOrgRoleId.convertToKey();
      IdentityID orgRolesNodeId = new IdentityID("Organization Roles", TEST_ORG);

      Role otherOrgRole = mock(Role.class);
      when(otherOrgRole.getIdentityID()).thenReturn(otherOrgRoleId);
      when(otherOrgRole.getOrganizationID()).thenReturn(otherOrg);

      Permission orgRolesRootPermission = grantedPermission(TEST_USER, TEST_ORG, ResourceAction.ADMIN, false);

      try(MockedStatic<SUtil> sutilMock = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
          MockedStatic<OrganizationManager> omMock =
             Mockito.mockStatic(OrganizationManager.class, Mockito.CALLS_REAL_METHODS))
      {
         sutilMock.when(SUtil::isMultiTenant).thenReturn(false);
         sutilMock.when(() -> SUtil.isInternalUser(any())).thenReturn(false);

         OrganizationManager mockOM = mock(OrganizationManager.class);
         omMock.when(OrganizationManager::getInstance).thenReturn(mockOM);
         omMock.when(OrganizationManager::getCurrentOrgName).thenReturn(TEST_ORG);
         when(mockOM.getCurrentOrgID()).thenReturn(TEST_ORG);
         when(mockOM.getCurrentOrgID(any())).thenReturn(TEST_ORG);
         when(mockOM.isSiteAdmin(any(Principal.class))).thenReturn(false);

         when(mockProvider.getRole(eq(otherOrgRoleId))).thenReturn(otherOrgRole);
         // grant only exists on the caller's own org's "Organization Roles" root — never on the
         // target role's own org, and never as a SECURITY_ORGANIZATION or org-admin-role grant,
         // to isolate this code path.
         when(mockProvider.getPermission(eq(ResourceType.SECURITY_ROLE), eq(orgRolesNodeId)))
            .thenReturn(orgRolesRootPermission);

         assertFalse(
            mockStrategy.checkPermission(mockUser, ResourceType.SECURITY_ROLE, otherOrgRoleResource,
                                         ResourceAction.ADMIN),
            "ADMIN on org A's \"Organization Roles\" root must not leak onto org B's role");
      }
   }

   // ─────────────────────────────────────────────────────────────────
   // [Path F] Hierarchical resource traversal falls back to parent — mock-based
   // ─────────────────────────────────────────────────────────────────

   // [Path F] When a hierarchical resource (ASSET) has no direct permission, the strategy
   // climbs the path hierarchy. Access is granted if an ancestor with hasOrgEditedGrantAll==true
   // holds a matching grant; denied when no qualifying ancestor exists.
   // Condition: type.isHierarchical()==true, useParent==true, loop exits when perm.hasOrgEditedGrantAll==true
   @ParameterizedTest
   @MethodSource("hierarchicalFallbackCases")
   void hierarchicalFallbackToParent(String parentPath, Permission parentPerm, boolean expected) {
      try(MockedStatic<SUtil> sutilMock = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
          MockedStatic<OrganizationManager> omMock =
             Mockito.mockStatic(OrganizationManager.class, Mockito.CALLS_REAL_METHODS))
      {
         sutilMock.when(SUtil::isMultiTenant).thenReturn(false);
         sutilMock.when(() -> SUtil.isInternalUser(any())).thenReturn(false);

         OrganizationManager mockOM = mock(OrganizationManager.class);
         omMock.when(OrganizationManager::getInstance).thenReturn(mockOM);
         omMock.when(OrganizationManager::getCurrentOrgName).thenReturn(TEST_ORG);
         when(mockOM.getCurrentOrgID()).thenReturn(TEST_ORG);
         when(mockOM.getCurrentOrgID(any())).thenReturn(TEST_ORG);
         when(mockOM.isSiteAdmin(any(Principal.class))).thenReturn(false);

         // leaf has no direct permission
         when(mockProvider.getPermission(eq(ResourceType.ASSET), eq("/assets/sub/leaf"), eq(TEST_ORG)))
            .thenReturn(null);
         // Always pin both ancestor levels per invocation to avoid parameterized-test stubbing bleed.
         when(mockProvider.getPermission(eq(ResourceType.ASSET), eq("/assets/sub"), eq(TEST_ORG)))
            .thenReturn("/assets/sub".equals(parentPath) ? parentPerm : null);
         when(mockProvider.getPermission(eq(ResourceType.ASSET), eq("/assets"), eq(TEST_ORG)))
            .thenReturn("/assets".equals(parentPath) ? parentPerm : null);

         assertEquals(expected,
            mockStrategy.checkPermission(mockUser, ResourceType.ASSET, "/assets/sub/leaf", ResourceAction.READ));
      }
   }

   private static Stream<Arguments> hierarchicalFallbackCases() {
      // ASSET.getParent(path) uses path.lastIndexOf("/") → strips the segment after the last "/"
      // so getParent("/assets/sub/leaf") = "/assets/sub"  (no trailing slash)
      // and getParent("/assets/sub")     = "/assets"
      return Stream.of(
         // ✓ immediate parent (/assets/sub) holds the grant and stops traversal
         Arguments.of("/assets/sub", grantedPermission(TEST_USER, TEST_ORG, ResourceAction.READ, true), true),
         // ✓ grandparent (/assets) holds the grant; immediate parent returns null
         Arguments.of("/assets", grantedPermission(TEST_USER, TEST_ORG, ResourceAction.READ, true), true),
         // ✗ no ancestor has any permission → traversal reaches root, returns false
         Arguments.of("/assets/sub", null, false)
      );
   }

   // ─────────────────────────────────────────────────────────────────
   // Permission builder helpers
   // ─────────────────────────────────────────────────────────────────

   /**
    * Builds a Permission with a user grant.
    * orgEdited=true causes the hierarchical loop to stop at this permission (hasOrgEditedGrantAll check).
    */
   private static Permission grantedPermission(String user, String orgId,
                                               ResourceAction action, boolean orgEdited)
   {
      Permission perm = new Permission();
      perm.setUserGrantsForOrg(action, Set.of(user), orgId);

      if(orgEdited) {
         perm.setOrgEditedGrantAll(Map.of(orgId, true));
      }

      return perm;
   }

   /**
    * Builds a Permission with a role grant.
    * orgEdited=true causes the hierarchical loop to stop at this permission.
    */
   private static Permission roleGrantedPermission(String role, String orgId,
                                                   ResourceAction action, boolean orgEdited)
   {
      Permission perm = new Permission();
      perm.setRoleGrantsForOrg(action, Set.of(role), orgId);

      if(orgEdited) {
         perm.setOrgEditedGrantAll(Map.of(orgId, true));
      }

      return perm;
   }
}
