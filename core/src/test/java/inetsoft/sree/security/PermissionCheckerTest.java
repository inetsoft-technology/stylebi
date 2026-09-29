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
 * PermissionChecker unit tests  (Strategy 1B — behavior orchestration)
 *
 * checkPermission() scenario table:
 *  [Deny]         permission==null / all grants empty + no org permission           → false
 *  [Direct]       USER/GROUP/ROLE/ORGANIZATION identity directly in matching grants → true
 *  [NullId]       identity == null                                                  → false
 *  [Indirect]     user→group / user→role / user→group→role /                       → true  (recursive)
 *                 user→org-role / user→roleA→roleB (role parent chain)
 *  [DeniedTraversal] group not in grants (chain broken) /                           → false
 *                    recursive=false blocks group traversal
 *  [Indirect #5]  org-level ORGANIZATION grant matches                             → true
 *  [Cycle]        circular group membership (A→B→A) terminates                     → false
 *  [AND]          AND mode: both/one/neither half satisfied                         → true/false/true/true
 *  [PerOrg]       permission.andCondition resolved per identity org, not one shared
 *                 cache (Bug #77253): two orgs back to back get their own AND/OR mode
 *  [NullOrg]      identity with no org (global role) resolves andCondition from the
 *                 thread's current org (host-org / tenant org), then the global key
 */

import inetsoft.sree.SreeEnv;
import inetsoft.uql.util.Identity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// This class replaces SreeEnv with a MockedStatic and clears PermissionChecker's static
// per-org andCondition cache. Force same-thread execution to avoid races when test
// parallelism is enabled.
@Tag("core")
@Execution(ExecutionMode.SAME_THREAD)
class PermissionCheckerTest {

   private static final String ORG = "testOrg";
   private static final String AND_COND = "permission.andCondition";

   private SecurityProvider mockProvider;
   // SreeEnv is mocked so no PropertiesEngine / Spring context is needed; unstubbed
   // getProperty() calls return null → OR mode.
   private MockedStatic<SreeEnv> sreeEnv;
   private PermissionChecker checker;

   @BeforeEach
   void setUp() throws Exception {
      sreeEnv = mockStatic(SreeEnv.class);
      PermissionChecker.resetAndConditionCache();

      mockProvider = mock(SecurityProvider.class);
      lenient().when(mockProvider.getGroup(any())).thenReturn(null);
      lenient().when(mockProvider.getRole(any())).thenReturn(null);
      lenient().when(mockProvider.getOrganization(anyString())).thenReturn(null);
      lenient().when(mockProvider.getOrgNameFromID(anyString())).thenReturn(ORG);

      checker = new PermissionChecker(mockProvider);
   }

   @AfterEach
   void tearDown() {
      sreeEnv.close();
      PermissionChecker.resetAndConditionCache();
   }

   // ─── [Deny] null or empty permission ─────────────────────────────────────
   //
   // [Deny #1] permission == null → short-circuits before any grant evaluation
   // [Deny #2] empty Permission  → early-return guard fires:
   //           isUserGroupEmpty && isRoleEmpty && !organizationPermission

   @ParameterizedTest(name = "{0}")
   @MethodSource("denyCases")
   void checkPermission_noValidPermission_returnsFalse(String desc, Permission perm) {
      assertFalse(checker.checkPermission(user("alice", ORG), perm, ResourceAction.READ, false));
   }

   private static Stream<Arguments> denyCases() {
      return Stream.of(
         // ✗ null permission — guard at line 47
         Arguments.of("null permission", null),
         // ✗ empty permission — early-return guard fires
         Arguments.of("all grants empty", new Permission())
      );
   }

   // ─── [Direct] identity directly matched in grants ─────────────────────────
   //
   // [Allow]      USER  identity in userGrants        (recursive=false)
   // [Direct #2]  GROUP identity in groupGrants       (recursive=false)
   // [Direct #3]  ROLE  identity in roleGrants        (recursive=false, checkRolePermission isRole branch)
   // [Direct #4]  ORGANIZATION identity in orgGrants  (recursive=false, uses identity.getName() directly —
   //              does NOT call provider.getOrgNameFromID, unlike the USER path in [Indirect #5])

   @ParameterizedTest(name = "{0}")
   @MethodSource("directGrantCases")
   void checkPermission_directGrant_returnsTrue(
      String desc, inetsoft.uql.util.Identity identity, Permission perm)
   {
      assertTrue(checker.checkPermission(identity, perm, ResourceAction.READ, false));
   }

   private static Stream<Arguments> directGrantCases() {
      return Stream.of(
         // ✓ user directly in userGrants
         Arguments.of("USER identity directly granted",
            new User(new IdentityID("alice", ORG), new String[0], new String[0], new IdentityID[0], "", ""),
            permForUser("alice", ORG, ResourceAction.READ)),

         // ✓ GROUP identity in groupGrants — checkUserGroupPermission GROUP branch
         Arguments.of("GROUP identity directly granted",
            new Group(new IdentityID("dev-team", ORG)),
            permForGroup("dev-team", ORG, ResourceAction.READ)),

         // ✓ ROLE identity in roleGrants — checkRolePermission isRole branch
         Arguments.of("ROLE identity directly granted",
            new Role(new IdentityID("analyst", ORG)),
            permForRole("analyst", ORG, ResourceAction.READ)),

         // ✓ ORGANIZATION identity in orgGrants — checkUserGroupOrganizationPermission
         //   uses identity.getName() (not provider.getOrgNameFromID) for the org name lookup
         Arguments.of("ORGANIZATION identity directly granted",
            new Organization(new IdentityID("TestOrg", ORG)),
            permForOrg("TestOrg", ORG, ResourceAction.READ))
      );
   }

   // ─── [NullId] null identity (standalone — requires OrganizationManager mock) ──

   // [Scenario: NullId] identity == null → all three internal check helpers return false;
   // no identity can ever match a grant → false even when permission has grants
   @Test
   void checkPermission_nullIdentity_returnsFalse() {
      try(MockedStatic<OrganizationManager> omMock =
             mockStatic(OrganizationManager.class, CALLS_REAL_METHODS))
      {
         OrganizationManager mockOM = mock(OrganizationManager.class);
         omMock.when(OrganizationManager::getInstance).thenReturn(mockOM);
         when(mockOM.getCurrentOrgID()).thenReturn(ORG);

         assertFalse(checker.checkPermission(
            null, permForUser("alice", ORG, ResourceAction.READ), ResourceAction.READ, false));
      }
   }

   // ─── [Indirect] recursive success paths ──────────────────────────────────
   //
   // [Indirect #1] user → group → permission
   // [Indirect #3] user → role  → permission
   // [Indirect #4] user → group → group's role → permission  (Risk 3: two-hop chain)
   // [Indirect #7] user → org-level role → permission        (Risk 3: org roles expand permissions)
   // [Indirect #8] user → roleA → roleB (parent) → permission
   //               roleA has no direct grant; roleA's parent roleB has grant
   //               exercises the recursive role-parent traversal inside checkRolePermission

   @ParameterizedTest(name = "{0}")
   @MethodSource("indirectGrantCases")
   void checkPermission_indirectGrant_returnsTrue(
      String desc,
      Consumer<SecurityProvider> providerSetup,
      inetsoft.uql.util.Identity identity,
      Permission perm)
   {
      providerSetup.accept(mockProvider);
      assertTrue(checker.checkPermission(identity, perm, ResourceAction.READ, true));
   }

   private static Stream<Arguments> indirectGrantCases() {
      IdentityID devTeamID    = new IdentityID("dev-team",   ORG);
      IdentityID analystID    = new IdentityID("analyst",    ORG);
      IdentityID orgAnalystID = new IdentityID("org-analyst", ORG);
      IdentityID roleAID      = new IdentityID("roleA", ORG);
      IdentityID roleBID      = new IdentityID("roleB", ORG);

      return Stream.of(
         // ✓ [Indirect #1] alice in "dev-team"; group "dev-team" has READ grant
         Arguments.of(
            "user→group: group holds the grant",
            (Consumer<SecurityProvider>) p ->
               when(p.getGroup(devTeamID)).thenReturn(new Group(devTeamID)),
            new User(new IdentityID("alice", ORG), new String[0], new String[]{"dev-team"},
                     new IdentityID[0], "", ""),
            permForGroup("dev-team", ORG, ResourceAction.READ)),

         // ✓ [Indirect #3] alice has role "analyst"; role has READ grant
         Arguments.of(
            "user→role: role holds the grant",
            (Consumer<SecurityProvider>) p ->
               when(p.getRole(analystID)).thenReturn(new Role(analystID)),
            new User(new IdentityID("alice", ORG), new String[0], new String[0],
                     new IdentityID[]{analystID}, "", ""),
            permForRole("analyst", ORG, ResourceAction.READ)),

         // ✓ [Indirect #4] alice in "dev-team"; dev-team holds "analyst"; role has READ grant
         //   Risk 3: two-hop chain (group lookup + role lookup)
         Arguments.of(
            "user→group→role: role holds the grant (two-hop chain)",
            (Consumer<SecurityProvider>) p -> {
               Group devTeam = new Group(devTeamID, null, new String[0], new IdentityID[]{analystID});
               when(p.getGroup(devTeamID)).thenReturn(devTeam);
               when(p.getRole(analystID)).thenReturn(new Role(analystID));
            },
            new User(new IdentityID("alice", ORG), new String[0], new String[]{"dev-team"},
                     new IdentityID[0], "", ""),
            permForRole("analyst", ORG, ResourceAction.READ)),

         // ✓ [Indirect #7] alice has no direct roles; org provides "org-analyst"; role has READ grant
         //   Risk 3: org-level roles silently expand every member's effective permissions
         Arguments.of(
            "user→org-role: org-level role expands effective permissions",
            (Consumer<SecurityProvider>) p -> {
               Organization mockOrg = mock(Organization.class);
               when(mockOrg.getRoles()).thenReturn(new IdentityID[]{orgAnalystID});
               when(p.getOrganization(ORG)).thenReturn(mockOrg);
               when(p.getRole(orgAnalystID)).thenReturn(new Role(orgAnalystID));
            },
            new User(new IdentityID("alice", ORG), new String[0], new String[0],
                     new IdentityID[0], "", ""),
            permForRole("org-analyst", ORG, ResourceAction.READ)),

         // ✓ [Indirect #8] alice has roleA; roleA.getRoles()=[roleB]; roleB has READ grant
         //   exercises the recursive role-parent traversal inside checkRolePermission
         Arguments.of(
            "user→roleA→roleB: role parent chain (roleA inherits roleB which holds the grant)",
            (Consumer<SecurityProvider>) p -> {
               Role roleA = new Role(roleAID, new IdentityID[]{roleBID});
               when(p.getRole(roleAID)).thenReturn(roleA);
               when(p.getRole(roleBID)).thenReturn(new Role(roleBID));
            },
            new User(new IdentityID("alice", ORG), new String[0], new String[0],
                     new IdentityID[]{roleAID}, "", ""),
            permForRole("roleB", ORG, ResourceAction.READ))
      );
   }

   // ─── [DeniedTraversal] group traversal denied ─────────────────────────────
   //
   // [DeniedTraversal #1] alice in "dev-team"; grant is for "other-group" not "dev-team"
   //                      → group chain broken, alice denied  (recursive=true)
   // [DeniedTraversal #2] alice in "dev-team"; grant IS for "dev-team" but recursive=false
   //                      → traversal gated by the recursive flag, alice denied

   @ParameterizedTest(name = "{0}")
   @MethodSource("deniedTraversalCases")
   void checkPermission_groupTraversalDenied_returnsFalse(
      String desc,
      Consumer<SecurityProvider> providerSetup,
      inetsoft.uql.util.Identity identity,
      Permission perm,
      boolean recursive)
   {
      providerSetup.accept(mockProvider);
      assertFalse(checker.checkPermission(identity, perm, ResourceAction.READ, recursive));
   }

   private static Stream<Arguments> deniedTraversalCases() {
      IdentityID devTeamID = new IdentityID("dev-team", ORG);
      Consumer<SecurityProvider> stubDevTeam =
         p -> when(p.getGroup(devTeamID)).thenReturn(new Group(devTeamID));
      User aliceInDevTeam = new User(new IdentityID("alice", ORG), new String[0],
                                     new String[]{"dev-team"}, new IdentityID[0], "", "");

      return Stream.of(
         // ✗ [DeniedTraversal #1] alice's group not in grants — grant belongs to "other-group"
         Arguments.of(
            "group chain broken: alice's group not in group grants",
            stubDevTeam,
            aliceInDevTeam,
            permForGroup("other-group", ORG, ResourceAction.READ),
            true),

         // ✗ [DeniedTraversal #2] dev-team has grant but recursive=false disables traversal
         //   verifies the if(recursive) gate in checkUserGroupPermission
         Arguments.of(
            "recursive=false blocks group traversal even when the group has a grant",
            stubDevTeam,
            aliceInDevTeam,
            permForGroup("dev-team", ORG, ResourceAction.READ),
            false)
      );
   }

   // ─── [Indirect #5] org-level ORGANIZATION grant (standalone — unique setup) ─

   // [Scenario: Indirect #5] user has no direct/role grant; USER identity falls through to
   // checkUserGroupOrganizationPermission, which calls provider.getOrgNameFromID(orgId) to
   // resolve the org name and then calls permission.checkOrganization → true
   @Test
   void checkPermission_grantedViaOrganization_returnsTrue() {
      when(mockProvider.getOrgNameFromID(ORG)).thenReturn("TestOrg");

      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, Identity.ORGANIZATION,
                     Set.of(new Permission.PermissionIdentity("TestOrg", ORG)));

      assertTrue(checker.checkPermission(
         user("alice", ORG), perm, ResourceAction.READ, false));
   }

   // ─── [Cycle] circular group membership (standalone) ──────────────────────

   // [Scenario: Cycle] groupA→groupB→groupA; identitiesChecked cuts the back-edge → false
   // Risk 3: without cycle detection this would cause StackOverflowError
   @Test
   void checkPermission_circularGroupMembership_terminatesAndReturnsFalse() {
      Group groupA = new Group(new IdentityID("groupA", ORG), null, new String[]{"groupB"}, new IdentityID[0]);
      Group groupB = new Group(new IdentityID("groupB", ORG), null, new String[]{"groupA"}, new IdentityID[0]);
      when(mockProvider.getGroup(new IdentityID("groupA", ORG))).thenReturn(groupA);
      when(mockProvider.getGroup(new IdentityID("groupB", ORG))).thenReturn(groupB);

      // "nobody" grant keeps isUserGroupEmpty=false so the early-return guard does not fire
      assertFalse(checker.checkPermission(
         new User(new IdentityID("alice", ORG), new String[0], new String[]{"groupA"},
                  new IdentityID[0], "", ""),
         permForUser("nobody", ORG, ResourceAction.READ),
         ResourceAction.READ, true));
   }

   // ─── [AND] AND mode combinations ─────────────────────────────────────────
   //
   // Result formula: (isUserGroupEmpty || userGroupGranted) && (isRoleEmpty || roleGranted)
   //
   // [AND #1] both sides granted                               → true
   // [AND #2] user granted, role NOT granted                   → false  (Risk 3)
   // [AND #3] user/group side empty (skip via isUserGroupEmpty), role granted → true
   // [AND #4] role side empty (skip via isRoleEmpty), user granted            → true

   @ParameterizedTest(name = "{0}")
   @MethodSource("andModeCases")
   void checkPermission_andMode(
      String desc,
      Consumer<SecurityProvider> providerSetup,
      inetsoft.uql.util.Identity identity,
      Permission perm,
      boolean recursive,
      boolean expected)
   {
      sreeEnv.when(() -> SreeEnv.getProperty(AND_COND, false, false)).thenReturn("true");
      providerSetup.accept(mockProvider);
      assertEquals(expected, checker.checkPermission(identity, perm, ResourceAction.READ, recursive));
   }

   private static Stream<Arguments> andModeCases() {
      IdentityID analystID = new IdentityID("analyst", ORG);
      User aliceWithRole = new User(new IdentityID("alice", ORG), new String[0], new String[0],
                                    new IdentityID[]{analystID}, "", "");
      User alicePlain    = new User(new IdentityID("alice", ORG), new String[0], new String[0],
                                    new IdentityID[0], "", "");

      Consumer<SecurityProvider> stubAnalystRole =
         p -> when(p.getRole(analystID)).thenReturn(new Role(analystID));
      Consumer<SecurityProvider> noSetup = p -> {};

      Permission bothGranted = new Permission();
      bothGranted.setGrants(ResourceAction.READ, Identity.USER,
                            Set.of(new Permission.PermissionIdentity("alice",   ORG)));
      bothGranted.setGrants(ResourceAction.READ, Identity.ROLE,
                            Set.of(new Permission.PermissionIdentity("analyst", ORG)));

      Permission userGrantedOtherRole = new Permission();
      userGrantedOtherRole.setGrants(ResourceAction.READ, Identity.USER,
                                     Set.of(new Permission.PermissionIdentity("alice",      ORG)));
      userGrantedOtherRole.setGrants(ResourceAction.READ, Identity.ROLE,
                                     Set.of(new Permission.PermissionIdentity("other-role", ORG)));

      return Stream.of(
         // ✓ [AND #1] (false || true) && (false || true) = true
         Arguments.of("AND: both user and role granted",
                      stubAnalystRole, aliceWithRole, bothGranted, true, true),

         // ✗ [AND #2] (false || true) && (false || false) = false
         //   Risk 3: one unsatisfied half denies even when the other passes
         Arguments.of("AND: user granted but role not matched",
                      stubAnalystRole, aliceWithRole, userGrantedOtherRole, true, false),

         // ✓ [AND #3] (true || …) && (false || true) = true  — isUserGroupEmpty short-circuits
         Arguments.of("AND: user/group grants empty (skip), role granted",
                      stubAnalystRole, aliceWithRole,
                      permForRole("analyst", ORG, ResourceAction.READ), true, true),

         // ✓ [AND #4] (false || true) && (true || …) = true  — isRoleEmpty short-circuits
         Arguments.of("AND: role grants empty (skip), user granted",
                      noSetup, alicePlain,
                      permForUser("alice", ORG, ResourceAction.READ), false, true)
      );
   }

   // ─── [PerOrg] permission.andCondition resolved per organization (Bug #77253) ───
   //
   // The property is org-scoped (inetsoft.org.<orgID>.permission.andCondition, falling back to
   // the global key). Two orgs checked back to back, inside the cache timeout, must each get
   // their own mode; a single shared cache would serve the first org's value to the second.
   //
   // Probe permission: user granted, role side set but not matched → AND=false, OR=true.

   @Test
   void checkPermission_andConditionResolvedPerOrgBackToBack() {
      sreeEnv.when(() -> SreeEnv.getProperty("inetsoft.org.orga." + AND_COND, false, false))
         .thenReturn("true");
      sreeEnv.when(() -> SreeEnv.getProperty(AND_COND, false, false)).thenReturn("false");

      // orga → AND (org key), orgb → OR (global fallback), then orga again → still AND
      assertFalse(checker.checkPermission(userWithRole("orga"), andProbe("orga"),
                                          ResourceAction.READ, true));
      assertTrue(checker.checkPermission(userWithRole("orgb"), andProbe("orgb"),
                                         ResourceAction.READ, true));
      assertFalse(checker.checkPermission(userWithRole("orga"), andProbe("orga"),
                                          ResourceAction.READ, true));
   }

   @Test
   void checkPermission_orgAndConditionOverridesGlobal() {
      sreeEnv.when(() -> SreeEnv.getProperty("inetsoft.org.orgb." + AND_COND, false, false))
         .thenReturn("false");
      sreeEnv.when(() -> SreeEnv.getProperty(AND_COND, false, false)).thenReturn("true");

      // orgb explicitly OR, orga falls back to the global AND
      assertTrue(checker.checkPermission(userWithRole("orgb"), andProbe("orgb"),
                                         ResourceAction.READ, true));
      assertFalse(checker.checkPermission(userWithRole("orga"), andProbe("orga"),
                                          ResourceAction.READ, true));
   }

   // ─── [NullOrg] identity without an organization (Bug #77253 review I-1) ───
   //
   // A global role under a virtual role principal (MV generation, scheduled tasks) has a null
   // organization. andCondition is then resolved from the thread's current org, where the EM
   // permission save writes it (host-org on single-tenant), before the global key.
   //
   // Probe: global role granted, global user side set but not matched → AND=false, OR=true.

   @ParameterizedTest(name = "current org {0}")
   @MethodSource("currentOrgs")
   void checkPermission_nullOrgIdentity_usesCurrentOrgAndCondition(String currentOrg) {
      sreeEnv.when(() -> SreeEnv.getProperty(
         "inetsoft.org." + currentOrg + "." + AND_COND, false, false)).thenReturn("true");

      try(MockedStatic<OrganizationManager> omMock = mockCurrentOrg(currentOrg)) {
         assertFalse(checker.checkPermission(new Role(new IdentityID("r", null)),
                                             globalRoleProbe(), ResourceAction.READ, true));
      }
   }

   @ParameterizedTest(name = "current org {0}")
   @MethodSource("currentOrgs")
   void checkPermission_nullOrgIdentity_noAndConditionSet_usesOr(String currentOrg) {
      // control: the same probe passes in OR mode, so the assertion above really tests AND
      try(MockedStatic<OrganizationManager> omMock = mockCurrentOrg(currentOrg)) {
         assertTrue(checker.checkPermission(new Role(new IdentityID("r", null)),
                                            globalRoleProbe(), ResourceAction.READ, true));
      }
   }

   private static Stream<String> currentOrgs() {
      // single-tenant (host-org) and a multi-tenant org
      return Stream.of(Organization.getDefaultOrganizationID(), "orga");
   }

   @Test
   void checkPermission_orgScopedIdentity_ignoresCurrentOrgAndCondition() {
      // thread org is orga (AND), identity is in orgb (no key → OR): identity org wins
      sreeEnv.when(() -> SreeEnv.getProperty("inetsoft.org.orga." + AND_COND, false, false))
         .thenReturn("true");

      try(MockedStatic<OrganizationManager> omMock = mockCurrentOrg("orga")) {
         assertTrue(checker.checkPermission(userWithRole("orgb"), andProbe("orgb"),
                                            ResourceAction.READ, true));
      }
   }

   private static MockedStatic<OrganizationManager> mockCurrentOrg(String orgID) {
      MockedStatic<OrganizationManager> omMock =
         mockStatic(OrganizationManager.class, CALLS_REAL_METHODS);
      OrganizationManager mockOM = mock(OrganizationManager.class);
      omMock.when(OrganizationManager::getInstance).thenReturn(mockOM);
      lenient().when(mockOM.getCurrentOrgID()).thenReturn(orgID);
      return omMock;
   }

   private static Permission globalRoleProbe() {
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, Identity.ROLE,
                     Set.of(new Permission.PermissionIdentity("r", null)));
      perm.setGrants(ResourceAction.READ, Identity.USER,
                     Set.of(new Permission.PermissionIdentity("someone", "__GLOBAL__")));
      return perm;
   }

   private User userWithRole(String org) {
      IdentityID roleID = new IdentityID("analyst", org);
      lenient().when(mockProvider.getRole(roleID)).thenReturn(new Role(roleID));
      return new User(new IdentityID("alice", org), new String[0], new String[0],
                      new IdentityID[]{ roleID }, "", "");
   }

   private static Permission andProbe(String org) {
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, Identity.USER,
                     Set.of(new Permission.PermissionIdentity("alice", org)));
      perm.setGrants(ResourceAction.READ, Identity.ROLE,
                     Set.of(new Permission.PermissionIdentity("other-role", org)));
      return perm;
   }

   // ─── Shared factories (static — used by both @MethodSource and test bodies) ─

   private static User user(String name, String org) {
      return new User(new IdentityID(name, org),
                      new String[0], new String[0], new IdentityID[0], "", "");
   }

   private static Permission permForUser(String name, String org, ResourceAction action) {
      Permission perm = new Permission();
      perm.setGrants(action, Identity.USER, Set.of(new Permission.PermissionIdentity(name, org)));
      return perm;
   }

   private static Permission permForGroup(String name, String org, ResourceAction action) {
      Permission perm = new Permission();
      perm.setGrants(action, Identity.GROUP, Set.of(new Permission.PermissionIdentity(name, org)));
      return perm;
   }

   private static Permission permForRole(String name, String org, ResourceAction action) {
      Permission perm = new Permission();
      perm.setGrants(action, Identity.ROLE, Set.of(new Permission.PermissionIdentity(name, org)));
      return perm;
   }

   private static Permission permForOrg(String name, String org, ResourceAction action) {
      Permission perm = new Permission();
      perm.setGrants(action, Identity.ORGANIZATION, Set.of(new Permission.PermissionIdentity(name, org)));
      return perm;
   }
}
