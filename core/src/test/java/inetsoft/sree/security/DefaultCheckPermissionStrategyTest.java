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
