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
 * Area 5: organization lifecycle operations (delete / rename / merge / copy) -- permission
 * authorization layer.
 *
 * Scenario list, mechanism breakdown, and Rule 2/Rule 3 verification notes live in
 * community/core/src/test/resources/inetsoft/sree/security/permission-matrix-org-lifecycle.md,
 * not duplicated here. This single file carries every Area 5 scenario, not split into multiple
 * test classes per scenario (same structure as Area 3/4's PermissionMatrixSpecialTest).
 *
 * Landed: scenario 1 (delete), scenario 2 (rename org ID), scenario 3 (rename username),
 * scenario 4 (merge-rejection guard), scenario 5 (copy inheritance -- the new org gets its own
 * independent, equivalent grant, the source org's own grant is untouched, as expected; an early
 * version of this test misdiagnosed this as "the source org's permission gets corrupted in
 * place", root-caused to the shared MockCluster lacking production Ignite's copy-on-read
 * semantics -- see this file's CopyOnReadClusterConfig and the "Test Methodology" section of
 * permission-matrix-org-lifecycle.md).
 * Scenario 6 (copy strips a member's global Administrator role membership -- driven via direct
 * reflection into copyIdentityRoles(), since that method itself is the exact mechanism Rule 3
 * describes, same precedent as the checkDuplicateOrgIDs() probe).
 * Scenario 7a `[not added]`: renaming an org's ID preserves a member's global Administrator
 * role, confirmed as the expected behavior via real product testing; not automated -- driving
 * copyUserToOrganization()/copyIdentityRoles() directly (the same probe style as scenario 6)
 * strips the role, which does not match the real rename flow's outcome, and reproducing the
 * full call chain is disproportionate to this one assertion -- see the comment above the method.
 * Scenario 7b (renaming a username does not go through copyIdentityRoles() at all, the role
 * survives untouched, `[landed]`).
 * Scenario 8 (renaming an org's ID and then renaming it back -- round trip -- leaves the final
 * permission state identical to before the round trip, with no orphaned key).
 * Scenario 9 (renaming an org's ID re-scopes a member's own directly-granted permission to the
 * new org rather than dropping it -- Bug #75721, the USER + org-ID-change combination that
 * scenarios 2 (ROLE + org-ID change) and 3 (USER + same-org username rename) did not cover).
 */

import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.sree.internal.cluster.MockCluster;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.security.IdentityModel;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.user.EditOrganizationPaneModel;
import inetsoft.web.admin.security.user.UserTreeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/*
 * Overrides the shared MockCluster bean (BaseTestConfiguration.cluster()) with a copy-on-read
 * variant, scoped to this test class only via its own @Configuration below -- NOT a change to
 * the shared MockCluster class, so no other test in the suite is affected.
 *
 * Why: real production storage (FileAuthorizationProvider's KeyValueStorage) is backed by
 * Cluster.getReplicatedMap(), which in production is a real IgniteCache. Ignite's IgniteCache
 * defaults to copyOnRead=true (this project's IgniteCluster.getCacheConfiguration() never sets
 * it to false) specifically so that mutating a value fetched from the cache never corrupts the
 * entry actually stored there. MockCluster's map is a plain ConcurrentHashMap with no such
 * protection -- get() returns the literal stored reference. That gap is what originally made
 * scenario 5 look like it found a data-corruption bug in AbstractEditableAuthenticationProvider
 * .copyOrganization(replace=false) (mutating a Permission fetched for the source org, then
 * persisting that same mutated object under the new org's key, appeared to corrupt the source
 * org's own stored copy too) -- a finding that could not be reproduced in the real application,
 * because real Ignite's copy-on-read means the source org's already-persisted entry is never
 * touched by mutating a separately-fetched copy. See
 * permission-matrix-org-lifecycle.md scenario 5 for the full writeup.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  PermissionMatrixOrgLifecycleTest.CopyOnReadClusterConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
public class PermissionMatrixOrgLifecycleTest {

   private static final String RESOURCE = "reports/org_lifecycle_vs";

   private SecurityTestDataBuilder builder;

   @AfterEach
   void tearDown() {
      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   // ── scenario 1: deleting an organization cleans only that organization's permissions ──

   @Test
   void delete_cleanOrganizationFromPermissions_removesOnlyTargetOrgGrants() throws Exception {
      String orgAId = "orglifecycle_del_a";
      String orgBId = "orglifecycle_del_b";

      builder = SecurityTestDataBuilder.create()
         .addOrg("OrgLifecycleA", orgAId)
         .addOrg("OrgLifecycleB", orgBId)
         .addRole("viewer", orgAId)
         .addRole("viewer", orgBId)
         .grantPermission(ResourceType.VIEWSHEET, RESOURCE, ResourceAction.READ,
                          "viewer", Identity.ROLE, orgAId)
         .grantPermission(ResourceType.VIEWSHEET, RESOURCE, ResourceAction.READ,
                          "viewer", Identity.ROLE, orgBId)
         .setup();

      AuthorizationChain chain = SecurityEngine.getSecurity().getAuthorizationChain()
         .orElseThrow(() -> new AssertionError("expected an AuthorizationChain"));

      assertNotNull(chain.getPermission(ResourceType.VIEWSHEET, RESOURCE, orgAId),
                    "precondition: orgA grant must exist before cleanup");
      assertNotNull(chain.getPermission(ResourceType.VIEWSHEET, RESOURCE, orgBId),
                    "precondition: orgB grant must exist before cleanup");

      chain.cleanOrganizationFromPermissions(orgAId);

      assertNull(chain.getPermission(ResourceType.VIEWSHEET, RESOURCE, orgAId),
                "deleting orgA must remove its VIEWSHEET permission");
      assertNotNull(chain.getPermission(ResourceType.VIEWSHEET, RESOURCE, orgBId),
                    "orgB's permission must survive orgA's deletion — no cross-org bleed");
   }

   // ── scenario 2: renaming an org's ID migrates its resource permissions to the new key ──

   @Test
   void renameOrgId_updateIdentityPermissions_migratesGrantToTargetOrgAndRemovesSourceKey()
      throws Exception
   {
      String fromOrgId = "orglifecycle_rename_from";
      String fromOrgName = "OrgLifecycleRenameFrom";
      String toOrgId = "orglifecycle_rename_to";
      String toOrgName = "OrgLifecycleRenameTo";

      builder = SecurityTestDataBuilder.create()
         .addOrg(fromOrgName, fromOrgId)
         .addOrg(toOrgName, toOrgId)
         .addRole("viewer", fromOrgId)
         .grantPermission(ResourceType.VIEWSHEET, RESOURCE, ResourceAction.READ,
                          "viewer", Identity.ROLE, fromOrgId)
         .setup();

      AuthorizationChain chain = SecurityEngine.getSecurity().getAuthorizationChain()
         .orElseThrow(() -> new AssertionError("expected an AuthorizationChain"));

      assertNotNull(chain.getPermission(ResourceType.VIEWSHEET, RESOURCE, fromOrgId),
                    "precondition: source-org grant must exist before rename");

      try {
         // Exercises the exact call AbstractEditableAuthenticationProvider.copyOrganization(...,
         // replace=true) makes for an org ID rename, without driving the full heavyweight
         // method — IdentityThemeService/DashboardRegistryManager/DataCycleManager/DataSpace
         // etc. are unrelated to permission migration and are never touched by this call for a
         // plain VIEWSHEET grant, so every other constructor argument can stay null.
         //
         // Note: in the real product flow, UserTreeService.editOrganization() only reaches
         // copyOrganization(replace=true) after checkDuplicateOrgIDs() confirms toOrgId does
         // NOT already belong to another organization (see scenario 4) — this probe's toOrgId
         // is likewise a brand-new, non-colliding ID, matching the only reachable case.
         IdentityService identityService = new IdentityService(
            SecurityEngine.getSecurity(), null, null, null, null, null, null, null, null, null,
            null, null, null, null, Optional.empty(), null, null, null, null, null, null, null,
            null, null, null, null, null, null, Optional.empty());

         identityService.updateIdentityPermissions(Identity.ORGANIZATION,
            new IdentityID(fromOrgName, fromOrgId), new IdentityID(toOrgName, toOrgId),
            fromOrgId, toOrgId, true);

         assertNotNull(chain.getPermission(ResourceType.VIEWSHEET, RESOURCE, toOrgId),
                       "rename must migrate the source org's grant to the target org's key");
         assertNull(chain.getPermission(ResourceType.VIEWSHEET, RESOURCE, fromOrgId),
                   "rename must remove the source-org key, not leave an orphaned entry");
      }
      finally {
         // The builder only tracks the permission it wrote at fromOrgId for teardown; the
         // migrated copy at toOrgId is this test's own side effect and must be cleaned up
         // explicitly so it does not leak into later tests sharing the same KeyValueStorage.
         chain.removePermission(ResourceType.VIEWSHEET, RESOURCE, toOrgId);
      }
   }

   // ── scenario 3: renaming a username (same org) renames the grantee, not the resource key ──

   @Test
   void renameUsername_updateIdentityPermissions_renamesGranteeWithinSameOrgKey() throws Exception {
      String orgId = "orglifecycle_renameuser_org";
      String orgName = "OrgLifecycleRenameUser";

      builder = SecurityTestDataBuilder.create()
         .addOrg(orgName, orgId)
         .addUser("alice", orgId, "password")
         .grantPermission(ResourceType.VIEWSHEET, RESOURCE, ResourceAction.READ,
                          "alice", Identity.USER, orgId)
         .setup();

      AuthorizationChain chain = SecurityEngine.getSecurity().getAuthorizationChain()
         .orElseThrow(() -> new AssertionError("expected an AuthorizationChain"));

      Permission before = chain.getPermission(ResourceType.VIEWSHEET, RESOURCE, orgId);
      assertNotNull(before, "precondition: alice's grant must exist before rename");
      assertTrue(before.getOrgScopedUserGrants(ResourceAction.READ, orgId).stream()
                    .anyMatch(id -> id.name.equals("alice")),
                "precondition: alice must be a READ grantee before rename");

      // Unlike an org-ID rename (scenario 2), renaming a user does NOT move the permission to a
      // different storage key — the org stays the same, so IdentityService.updateIdentityPermissions()
      // rewrites the grantee name in place inside the same (type, path, orgId) Permission object
      // (via updateIdentityPermission()'s PermissionIdentity swap), rather than migrating keys.
      IdentityService identityService = new IdentityService(
         SecurityEngine.getSecurity(), null, null, null, null, null, null, null, null, null,
         null, null, null, null, Optional.empty(), null, null, null, null, null, null, null,
         null, null, null, null, null, null, Optional.empty());

      identityService.updateIdentityPermissions(Identity.USER,
         new IdentityID("alice", orgId), new IdentityID("bob", orgId), orgId, orgId, true);

      Permission after = chain.getPermission(ResourceType.VIEWSHEET, RESOURCE, orgId);
      assertNotNull(after, "the permission must still exist at the same (unchanged) key");
      assertFalse(after.getOrgScopedUserGrants(ResourceAction.READ, orgId).stream()
                     .anyMatch(id -> id.name.equals("alice")),
                 "renaming the user must remove the old grantee name");
      assertTrue(after.getOrgScopedUserGrants(ResourceAction.READ, orgId).stream()
                    .anyMatch(id -> id.name.equals("bob")),
                "renaming the user must add the new grantee name in its place");
   }

   // ── scenario 4: renaming an org to an already-existing org ID must be rejected, no side effects ──

   @Test
   void renameOrgToExistingId_checkDuplicateOrgIDs_rejectsAndLeavesPermissionsUnchanged()
      throws Exception
   {
      String orgAId = "orglifecycle_dup_a";
      String orgAName = "OrgLifecycleDupA";
      String orgBId = "orglifecycle_dup_b";
      String orgBName = "OrgLifecycleDupB";

      builder = SecurityTestDataBuilder.create()
         .addOrg(orgAName, orgAId)
         .addOrg(orgBName, orgBId)
         .addRole("viewer", orgAId)
         .addRole("viewer", orgBId)
         .grantPermission(ResourceType.VIEWSHEET, RESOURCE, ResourceAction.READ,
                          "viewer", Identity.ROLE, orgAId)
         .grantPermission(ResourceType.VIEWSHEET, RESOURCE, ResourceAction.READ,
                          "viewer", Identity.ROLE, orgBId)
         .setup();

      AuthorizationChain chain = SecurityEngine.getSecurity().getAuthorizationChain()
         .orElseThrow(() -> new AssertionError("expected an AuthorizationChain"));

      assertTrue(hasViewerReadGrant(chain, orgAId), "precondition: orgA grant must exist");
      assertTrue(hasViewerReadGrant(chain, orgBId), "precondition: orgB grant must exist");

      Organization orgA = SecurityEngine.getSecurity().getSecurityProvider().getOrganization(orgAId);

      // Attempting to rename orgA's ID to orgB's already-existing ID — this is the "merge" case
      // that turned out to be unreachable via the real product flow (see design doc). This test
      // drives the actual guard, UserTreeService.checkDuplicateOrgIDs(), rather than the internal
      // permission-migration method (scenarios 2/3), because the guard itself — not the migration
      // logic — is what's supposed to make "merge" impossible. It's private, so it's invoked via
      // reflection, matching the existing precedent for private-method probes in this test suite
      // (e.g. checkLoginAs() in PermissionMatrixSpecialTest). Only securityEngine is needed here —
      // AuthenticationProviderService/SystemAdminService/etc. are for the rest of editOrganization()
      // (theme copy, sysAdmin-count safety checks), not for this duplicate-ID guard.
      UserTreeService userTreeService = new UserTreeService(
         null, null, null, null, SecurityEngine.getSecurity(), null, null, null, null, null, null,
         null, null, null, null, null);

      EditOrganizationPaneModel model = EditOrganizationPaneModel.builder()
         .name(orgAName)
         .oldName(orgAName)
         .id(orgBId)
         .theme(null)
         .build();

      MessageException thrown = assertThrows(MessageException.class,
         () -> ReflectionTestUtils.invokeMethod(
            userTreeService, "checkDuplicateOrgIDs", model, orgA),
         "renaming orgA to orgB's existing ID must be rejected before any permission changes");

      assertEquals(Catalog.getCatalog().getString("em.duplicateOrganizationID"), thrown.getMessage());

      assertTrue(hasViewerReadGrant(chain, orgAId),
                "rejected rename must leave orgA's own permissions untouched");
      assertTrue(hasViewerReadGrant(chain, orgBId),
                "rejected rename must leave orgB's permissions untouched — no partial merge");
   }

   // ── scenario 5: copying an org (replace=false) — does the new org inherit the source's
   //    content permission grants? ──

   @Test
   void copy_replaceFalse_contentPermissionGrantIsCopiedToNewOrg() throws Exception {
      String fromOrgId = "orglifecycle_copy_from";
      String fromOrgName = "OrgLifecycleCopyFrom";
      String toOrgId = "orglifecycle_copy_to";
      String toOrgName = "OrgLifecycleCopyTo";

      builder = SecurityTestDataBuilder.create()
         .addOrg(fromOrgName, fromOrgId)
         .addRole("viewer", fromOrgId)
         .addSysAdminRole("Administrator", fromOrgId)
         .addUser("admin", fromOrgId, "password")
         .addUserToRole("admin", "Administrator", fromOrgId)
         .grantPermission(ResourceType.VIEWSHEET, RESOURCE, ResourceAction.READ,
                          "viewer", Identity.ROLE, fromOrgId)
         .setup();

      SRPrincipal admin = builder.principalOf("admin", fromOrgId);

      AuthorizationChain chain = SecurityEngine.getSecurity().getAuthorizationChain()
         .orElseThrow(() -> new AssertionError("expected an AuthorizationChain"));

      assertTrue(hasViewerReadGrant(chain, fromOrgId), "precondition: source-org grant must exist");

      AuthenticationProvider authc = SecurityEngine.getSecurity().getSecurityProvider()
         .getAuthenticationProvider();
      FileAuthenticationProvider fileProvider =
         (FileAuthenticationProvider) ((AuthenticationChain) authc).getProviders().get(0);
      Organization fromOrg = fileProvider.getOrganization(fromOrgId);

      try {
         try {
            // Drives the real copy(replace=false) entry point — AbstractEditableAuthenticationProvider
            // .copyOrganization() — rather than a hand-picked sub-method, because the question this
            // scenario asks ("does the overall copy path preserve content permission grants?") is
            // exactly the kind of question a hand-picked sub-method could answer wrong by omission.
            // Only securityEngine + securityProvider are needed for the permission-copying parts
            // (identityService.getPermission()/setIdentityPermissions() read securityProvider
            // directly, not securityEngine.getSecurityProvider()); IdentityThemeService/
            // DashboardRegistryManager/DataCycleManager are for themes/dashboards/data-cycles, all
            // out of scope (see design doc's TODO list) and passed null.
            IdentityService identityService = new IdentityService(
               SecurityEngine.getSecurity(), SecurityEngine.getSecurity().getSecurityProvider(),
               null, null, null, null, null, null, null, null, null, null, null, null, // positions 3-14
               Optional.empty(),                                                    // position 15
               null, null, null, null, null, null, null, null, null, null, null, null, null, // 16-28
               Optional.empty());                                                   // position 29

            fileProvider.copyOrganization(fromOrg, toOrgId, identityService, null, null, null,
                                          admin, false, "TestCopyPassw0rd!");
         }
         catch(NoSuchBeanDefinitionException expected) {
            // copyThemes() (unrelated to permission copying — theme handling, out of scope) fails in
            // this minimal test context because CustomThemesManager.getManager()'s static Spring-bean
            // lookup finds nothing. It runs AFTER addCopiedIdentityPermission() (the step that
            // copies/migrates permissions, including our VIEWSHEET grant), so this catch does not
            // affect the permission-layer result below. Narrowly typed so an unrelated RuntimeException
            // elsewhere in copyOrganization() fails loudly here instead of being masked.
         }

         // See permission-matrix-org-lifecycle.md scenario 5 for the full writeup:
         // copyOrganization(replace=false) calls
         // addCopiedIdentityPermission(..., Identity.ORGANIZATION, replace=false), which reaches
         // IdentityService.updateIdentityPermissions() -> updateOrgIdentitiesPermission() ->
         // Permission.setRoleGrantsForOrg(action, roles, oldOrgId, newOrgId). That method mutates a
         // Permission instance fetched via FileAuthorizationProvider.getPermissions() (strips the
         // oldOrgId-scoped grant, adds a newOrgId-scoped one) and persists the mutated instance under
         // the new org's key. Production's Cluster.getReplicatedMap() is backed by a real
         // IgniteCache, which defaults to copyOnRead=true (this project's
         // IgniteCluster.getCacheConfiguration() never overrides it to false) -- every get() returns
         // an independent deserialized copy, so mutating it can never affect what's already
         // persisted under a different (or the same) key. This test runs against
         // CopyOnReadClusterConfig (below), which reproduces that guarantee, so the assertions below
         // reflect the actual production behavior: the source org keeps its own grant untouched,
         // and the new org gets its own independent, equivalent grant.
         assertTrue(hasViewerReadGrant(chain, toOrgId),
                   "the new org gets a working grant, scoped to itself, as Rule 1 intends");
         assertTrue(hasViewerReadGrant(chain, fromOrgId),
                   "the source org's own grant must be untouched by a supposedly non-destructive copy");
      }
      finally {
         // copyOrganization(replace=false) creates toOrgId itself plus its own copies of
         // fromOrgId's "viewer"/"Administrator" roles and "admin" user (see
         // copyRoleToOrganization()/copyUserToOrganization()) — none of that is tracked by
         // SecurityTestDataBuilder, since only fromOrgId was registered via .addOrg(). Without
         // explicit cleanup here, the org, its roles/user, and the migrated permission below
         // would all leak into later tests sharing the same KeyValueStorage-backed
         // FileAuthenticationProvider. Runs in `finally` so it happens even if an assertion above
         // fails.
         chain.removePermission(ResourceType.VIEWSHEET, RESOURCE, toOrgId);
         fileProvider.removeUser(new IdentityID("admin", toOrgId));
         fileProvider.removeRole(new IdentityID("Administrator", toOrgId));
         fileProvider.removeRole(new IdentityID("viewer", toOrgId));
         fileProvider.removeOrganization(toOrgId);
      }
   }

   // ── scenario 6: copying an org strips a member's global Administrator role, keeps ordinary
   //    org-scoped roles (re-scoped to the new org) ──

   @Test
   void copy_userWithGlobalAdministratorRole_copyIdentityRoles_stripsGlobalRoleButKeepsOrgScopedRoles()
      throws Exception
   {
      String fromOrgId = "orglifecycle_striprole_from";
      String fromOrgName = "OrgLifecycleStripRoleFrom";
      String toOrgId = "orglifecycle_striprole_to";

      builder = SecurityTestDataBuilder.create()
         .addOrg(fromOrgName, fromOrgId)
         .addRole("viewer", fromOrgId)
         .setup();

      AuthenticationProvider authc = SecurityEngine.getSecurity().getSecurityProvider()
         .getAuthenticationProvider();
      FileAuthenticationProvider fileProvider =
         (FileAuthenticationProvider) ((AuthenticationChain) authc).getProviders().get(0);

      // The built-in global "Administrator" role (IdentityID(name, null)) is bootstrapped once
      // into the shared "defaultSecurityRoles" KeyValueStorage the first time any
      // FileAuthenticationProvider touches role storage in this JVM (see
      // FileAuthenticationProvider.LoadRolesTask.initialize()) -- the addRole("viewer", ...)
      // call in .setup() above already triggered that lazy bootstrap if no earlier test had.
      // This precondition confirms it exists and is a sysAdmin role, exactly as
      // AbstractEditableAuthenticationProvider.copyIdentityRoles() expects, rather than silently
      // testing against a role that doesn't exist.
      IdentityID globalAdminRoleId = new IdentityID("Administrator", null);
      assertTrue(fileProvider.isSystemAdministratorRole(globalAdminRoleId),
                "precondition: the bootstrap global Administrator role must exist and be a " +
                "sysAdmin role");

      // A throwaway in-memory identity standing in for "a user who was manually granted the
      // global Administrator role" (Rule 3) -- copyIdentityRoles() only reads fromID.getRoles(),
      // so this identity never needs to be persisted via the builder or FileAuthenticationProvider.
      FSUser carol = new FSUser(new IdentityID("carol", fromOrgId));
      carol.setRoles(new IdentityID[] {
         new IdentityID("viewer", fromOrgId), globalAdminRoleId
      });

      // copyIdentityRoles() is private -- it is itself the exact mechanism Rule 3 identifies
      // ("skips global Administrator role membership"), so this drives it directly via
      // reflection rather than through the heavier copyOrganization()/copyUserToOrganization()
      // entry point (same precedent as scenario 4's checkDuplicateOrgIDs() probe).
      IdentityID[] copiedRoles = ReflectionTestUtils.invokeMethod(
         fileProvider, "copyIdentityRoles", carol, toOrgId);

      assertFalse(Arrays.asList(copiedRoles).contains(globalAdminRoleId),
                 "cloning must strip the global Administrator role membership (Rule 3)");
      assertTrue(Arrays.asList(copiedRoles).contains(new IdentityID("viewer", toOrgId)),
                "cloning must still carry over an ordinary org-scoped role, re-scoped to the " +
                "new org -- confirms the copy isn't wholesale-dropping every role");
   }

   // ── scenario 7a [not added]: renaming an org's ID preserves a member's global Administrator role
   //    -- confirmed expected behavior via real product testing. Not automated here: driving
   //    copyUserToOrganization()/copyIdentityRoles() directly (as scenario 6 does for copy)
   //    strips the role, which does NOT match the real rename flow's outcome -- the real flow
   //    goes through IdentityService.setOrganizationInfo() -> syncIdentity() ->
   //    AbstractEditableAuthenticationProvider.copyOrganization(..., replace=true) with real
   //    IdentityThemeService/DashboardRegistryManager/DataCycleManager collaborators, and
   //    reproducing that full chain in a lightweight unit test is disproportionate to this one
   //    assertion. See permission-matrix-org-lifecycle.md scenario 7a for the confirmed behavior. ──

   // ── scenario 7b: renaming a USERNAME (same org) never calls copyIdentityRoles() at all --
   //    IdentityService.setUserInfo()/syncIdentity() persist whatever roles the edit form
   //    submitted via a plain FileAuthenticationProvider.setUser() call, so a member's global
   //    Administrator role survives a username rename untouched -- consistent with 7a's
   //    confirmed behavior that renaming (org ID or username) preserves the global Administrator
   //    role. ──

   @Test
   void renameUsername_setUser_currentBehaviorPreservesGlobalAdministratorRole() throws Exception {
      String orgId = "orglifecycle_renameuserrole_org";
      String orgName = "OrgLifecycleRenameUserRole";

      builder = SecurityTestDataBuilder.create()
         .addOrg(orgName, orgId)
         .addRole("viewer", orgId)
         .addUser("dave", orgId, "password")
         .addUserToRole("dave", "viewer", orgId)
         .setup();

      AuthenticationProvider authc = SecurityEngine.getSecurity().getSecurityProvider()
         .getAuthenticationProvider();
      FileAuthenticationProvider fileProvider =
         (FileAuthenticationProvider) ((AuthenticationChain) authc).getProviders().get(0);

      IdentityID globalAdminRoleId = new IdentityID("Administrator", null);
      assertTrue(fileProvider.isSystemAdministratorRole(globalAdminRoleId),
                "precondition: the bootstrap global Administrator role must exist and be a " +
                "sysAdmin role");

      IdentityID daveId = new IdentityID("dave", orgId);
      FSUser dave = (FSUser) fileProvider.getUser(daveId);
      dave.setRoles(new IdentityID[] { new IdentityID("viewer", orgId), globalAdminRoleId });
      fileProvider.setUser(daveId, dave);

      IdentityID davidId = new IdentityID("david", orgId);

      try {
         // Mirrors what IdentityService.setUserInfo()/syncIdentity() actually do for a plain
         // username rename (same org): build a new FSUser carrying forward the SAME roles array
         // the admin's edit form submitted (unchanged, since the admin is only renaming, not
         // editing roles), then call FileAuthenticationProvider.setUser(oldIdentity, newUser)
         // directly -- no copyIdentityRoles() anywhere on this path.
         FSUser renamed = (FSUser) fileProvider.getUser(daveId);
         FSUser newUser = new FSUser(davidId);
         newUser.setRoles(renamed.getRoles());
         newUser.setOrganization(orgId);

         fileProvider.setUser(daveId, newUser);

         FSUser after = (FSUser) fileProvider.getUser(davidId);
         assertNotNull(after, "the renamed user must exist under the new name");
         assertTrue(Arrays.asList(after.getRoles()).contains(globalAdminRoleId),
                   "a plain username rename does not go through copyIdentityRoles(), so the " +
                   "global Administrator role membership survives untouched -- consistent with " +
                   "7a's confirmed org-ID rename behavior (both preserve the role)");
         assertTrue(Arrays.asList(after.getRoles()).contains(new IdentityID("viewer", orgId)),
                   "the ordinary org-scoped role must also survive (same org, no re-scoping " +
                   "needed)");
      }
      finally {
         // dave's own key was already removed by fileProvider.setUser() above (it deletes the
         // old key before inserting the new one) -- the builder only knows to tear down "dave",
         // so the renamed "david" key is this test's own side effect to clean up.
         fileProvider.removeUser(davidId);
      }
   }

   // ── scenario 8: renaming an org's ID and then renaming it back (A -> B -> A) must leave the
   //    permission state identical to before the round trip, with no orphaned key at B and no
   //    duplicate/leftover data at A ──

   @Test
   void renameOrgIdRoundTrip_updateIdentityPermissions_leavesStateIdenticalToBeforeTheRoundTrip()
      throws Exception
   {
      String orgAId = "orglifecycle_roundtrip_a";
      String orgAName = "OrgLifecycleRoundTripA";
      String orgBId = "orglifecycle_roundtrip_b";
      String orgBName = "OrgLifecycleRoundTripB";

      // Both orgs must be real, resolvable Organization records for the round trip to exercise
      // updateIdentityPermissions() correctly: it resolves `oldOrgId` to an Organization via
      // provider.getOrganization(oldOrgId) and passes that object (not the raw id string) into
      // Permission.getOrgScopedRoleGrants(action, Organization) -- if that lookup misses (as it
      // would for orgB if it were never created), the Organization overload silently falls back
      // to the *global* org scope instead of orgB's, and the hop finds nothing to migrate. In the
      // real product flow both orgs are always real Organization records at the relevant moment
      // (copyOrganizationInternal() creates the target before removing the source), so both are
      // created upfront here too, matching scenario 2's own setup.
      builder = SecurityTestDataBuilder.create()
         .addOrg(orgAName, orgAId)
         .addOrg(orgBName, orgBId)
         .addRole("viewer", orgAId)
         .grantPermission(ResourceType.VIEWSHEET, RESOURCE, ResourceAction.READ,
                          "viewer", Identity.ROLE, orgAId)
         .setup();

      AuthorizationChain chain = SecurityEngine.getSecurity().getAuthorizationChain()
         .orElseThrow(() -> new AssertionError("expected an AuthorizationChain"));

      Permission before = chain.getPermission(ResourceType.VIEWSHEET, RESOURCE, orgAId);
      assertNotNull(before, "precondition: orgA's grant must exist before the round trip");
      Set<IdentityID> beforeGrantees = before.getOrgScopedRoleGrants(ResourceAction.READ, orgAId);
      assertTrue(beforeGrantees.stream().anyMatch(id -> id.name.equals("viewer")),
                "precondition: viewer must hold the READ grant before the round trip");

      IdentityService identityService = new IdentityService(
         SecurityEngine.getSecurity(), null, null, null, null, null, null, null, null, null,
         null, null, null, null, Optional.empty(), null, null, null, null, null, null, null,
         null, null, null, null, null, null, Optional.empty());

      // A -> B
      identityService.updateIdentityPermissions(Identity.ORGANIZATION,
         new IdentityID(orgAName, orgAId), new IdentityID(orgBName, orgBId), orgAId, orgBId, true);

      assertNotNull(chain.getPermission(ResourceType.VIEWSHEET, RESOURCE, orgBId),
                    "mid-trip: the grant must have migrated to B's key");
      assertNull(chain.getPermission(ResourceType.VIEWSHEET, RESOURCE, orgAId),
                "mid-trip: A's key must be gone, not left behind as an orphan");

      // B -> A (rename back)
      identityService.updateIdentityPermissions(Identity.ORGANIZATION,
         new IdentityID(orgBName, orgBId), new IdentityID(orgAName, orgAId), orgBId, orgAId, true);

      Permission after = chain.getPermission(ResourceType.VIEWSHEET, RESOURCE, orgAId);
      assertNotNull(after, "round trip: the grant must be back under A's original key");
      assertNull(chain.getPermission(ResourceType.VIEWSHEET, RESOURCE, orgBId),
                "round trip: B's key must not be left behind as an orphan");

      Set<IdentityID> afterGrantees = after.getOrgScopedRoleGrants(ResourceAction.READ, orgAId);
      assertEquals(beforeGrantees, afterGrantees,
                  "round trip: the final grantee set at A's key must be identical to the " +
                  "pre-round-trip set -- no duplicate or leftover data from the trip through B");
   }

   // ── scenario 9: renaming an org's ID re-scopes a member's *own* directly-granted permission
   //    to the new org, rather than dropping it (Bug #75721) ──
   //
   // This is the USER + org-ID-change combination that had no coverage before: scenario 2 covers
   // org-ID change for a ROLE grantee, scenario 3 covers a USER grantee but only for a same-org
   // username rename. The reported bug was that a permission granted directly to a *user* was
   // lost on an org-ID rename, because updateOrganizationMembers()'s user branch re-scoped only
   // the user's roles and never made the equivalent updateIdentityPermissions(Identity.USER, ...)
   // call for the user's own grants -- the fix adds exactly that call.
   //
   // Unlike scenarios 2/3/8 (which drive updateIdentityPermissions() directly), this drives the
   // private updateOrganizationMembers() itself via reflection, because the fix is an *added call
   // site* inside that method, not a change to updateIdentityPermissions() (whose USER branch is
   // unrelated pre-existing code, already exercised by scenario 3). Asserting only the lower-level
   // method would still pass with the fix reverted; driving updateOrganizationMembers() makes the
   // test fail if the added call is removed -- with no roles/groups in the org, that call is the
   // only thing that touches the user's grant, so without it the grant is simply left orphaned at
   // the old org id and never appears under the new one.
   //
   // updateOrganizationMembers() only touches three collaborators beyond permission re-scoping,
   // all unrelated to it: keyValueStorageManager (em-favorites move -- alice has none, so the
   // mocked storage's null get() short-circuits it), and repletRegistryManager /
   // dashboardRegistryManager (replet/dashboard registry migration -- void, mocked to no-op).
   // securityEngine/securityProvider are the real ones so the permission read/write hits the same
   // AuthorizationChain the assertions inspect. The current org id is read from the thread
   // principal (OrganizationManager.getCurrentOrgID()), so it is set to the *source* org for the
   // duration of the call -- that is the "old" org id the rename moves grants away from.
   @Test
   @SuppressWarnings("unchecked")
   void renameOrgId_updateOrganizationMembers_reScopesUserOwnGrantToTargetOrg() throws Exception {
      String fromOrgId = "orglifecycle_userrescope_from";
      String fromOrgName = "OrgLifecycleUserReScopeFrom";
      String toOrgId = "orglifecycle_userrescope_to";
      String toOrgName = "OrgLifecycleUserReScopeTo";

      builder = SecurityTestDataBuilder.create()
         .addOrg(fromOrgName, fromOrgId)
         .addOrg(toOrgName, toOrgId)
         .addUser("alice", fromOrgId, "password")
         .grantPermission(ResourceType.VIEWSHEET, RESOURCE, ResourceAction.READ,
                          "alice", Identity.USER, fromOrgId)
         .setup();

      AuthorizationChain chain = SecurityEngine.getSecurity().getAuthorizationChain()
         .orElseThrow(() -> new AssertionError("expected an AuthorizationChain"));
      FileAuthenticationProvider fileProvider = (FileAuthenticationProvider)
         ((AuthenticationChain) SecurityEngine.getSecurity().getSecurityProvider()
            .getAuthenticationProvider()).getProviders().get(0);

      Permission before = chain.getPermission(ResourceType.VIEWSHEET, RESOURCE, fromOrgId);
      assertNotNull(before, "precondition: alice's own grant must exist at the source org");
      assertTrue(before.getOrgScopedUserGrants(ResourceAction.READ, fromOrgId).stream()
                    .anyMatch(id -> id.name.equals("alice")),
                "precondition: alice must be a READ grantee at the source org before the rename");

      // Collaborators unrelated to permission re-scoping (see method comment): the favorites
      // service is a no-op mock so the em-favorites move is skipped; the two registry managers
      // are no-op.
      FavoritesService favoritesService = mock(FavoritesService.class);
      RepletRegistryManager repletRegistryManager = mock(RepletRegistryManager.class);
      DashboardRegistryManager dashboardRegistryManager = mock(DashboardRegistryManager.class);

      IdentityService identityService = new IdentityService(
         SecurityEngine.getSecurity(), SecurityEngine.getSecurity().getSecurityProvider(),
         null, null, null, favoritesService, null, null, null, null,
         null, null, null, null, Optional.empty(), null, null, null,
         dashboardRegistryManager, null, null, null, null, null, null, null, null,
         repletRegistryManager, Optional.empty());

      // The renamed org: same members ("alice"), new id. updateOrganizationMembers() reads the
      // current org id (the source) from the thread principal, compares it to identity.getId()
      // (the target) to detect the id change, and re-scopes each member's grants across the two.
      FSOrganization renamedOrg = new FSOrganization(toOrgId);
      renamedOrg.setName(toOrgName);
      renamedOrg.setMembers(new String[]{ "alice" });

      try {
         ThreadContext.setContextPrincipal(builder.principalOf("alice", fromOrgId));

         // memberModels is empty: it only drives creation of brand-new members, and alice is an
         // existing user carried over by the rename, not a newly-added one.
         ReflectionTestUtils.invokeMethod(identityService, "updateOrganizationMembers",
            renamedOrg, new ArrayList<IdentityModel>(), fromOrgId, fileProvider);

         Permission after = chain.getPermission(ResourceType.VIEWSHEET, RESOURCE, toOrgId);
         assertNotNull(after,
                       "renaming the org must re-scope alice's own grant to the target org, not " +
                       "drop it (Bug #75721)");
         assertTrue(after.getOrgScopedUserGrants(ResourceAction.READ, toOrgId).stream()
                       .anyMatch(id -> id.name.equals("alice")),
                   "alice must remain a READ grantee under the target org after the rename");
         assertNull(chain.getPermission(ResourceType.VIEWSHEET, RESOURCE, fromOrgId),
                   "the source-org key must be removed, not left behind as an orphan");
      }
      finally {
         // Reset the thread principal so the source-org context does not leak into later tests.
         ThreadContext.setContextPrincipal(null);
         // The builder tracks alice@fromOrgId and the grant it wrote at fromOrgId; the rename's
         // side effects at toOrgId (alice moved there, grant re-scoped there) are this test's own
         // and must be cleaned up explicitly so they do not leak into later tests sharing storage.
         chain.removePermission(ResourceType.VIEWSHEET, RESOURCE, toOrgId);
         fileProvider.removeUser(new IdentityID("alice", toOrgId));
      }
   }

   private static boolean hasViewerReadGrant(AuthorizationChain chain, String orgId) {
      Permission permission = chain.getPermission(ResourceType.VIEWSHEET, RESOURCE, orgId);
      return permission != null && permission.getOrgScopedRoleGrants(ResourceAction.READ, orgId)
         .stream().anyMatch(id -> id.name.equals("viewer"));
   }

   // ── copy-on-read Cluster override, scoped to this test class only (see class-level comment) ──

   @Configuration
   public static class CopyOnReadClusterConfig {
      @Bean
      public Cluster cluster() {
         return new CopyOnReadCluster();
      }
   }

   /**
    * Public (not just package-private) so other org-lifecycle test classes outside this package
    * (e.g. {@code inetsoft.uql.asset.sync.OrgLifecycleDependencyMigrationTest}) can reuse the same
    * copy-on-read simulation instead of duplicating it -- see the Global Constraints section of
    * {@code docs/superpowers/plans/2026-07-14-org-lifecycle-resource-integrity.md}.
    */
   public static class CopyOnReadCluster extends MockCluster {
      @Override
      public <K, V> DistributedMap<K, V> getReplicatedMap(String name) {
         return new CopyOnReadDistributedMap<>(super.getReplicatedMap(name));
      }
   }

   /**
    * Wraps a {@link DistributedMap} so every value handed back by a read-style method is an
    * independent deep copy, matching {@code IgniteCache}'s default {@code copyOnRead=true} --
    * see the class-level comment on {@link PermissionMatrixOrgLifecycleTest} for why this matters.
    */
   public static class CopyOnReadDistributedMap<K, V> implements DistributedMap<K, V> {
      private final DistributedMap<K, V> delegate;

      CopyOnReadDistributedMap(DistributedMap<K, V> delegate) {
         this.delegate = delegate;
      }

      @SuppressWarnings("unchecked")
      private static <T> T deepCopy(T value) {
         if(value == null) {
            return null;
         }

         try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();

            try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
               out.writeObject(value);
            }

            try(ObjectInputStream in =
                   new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
            {
               return (T) in.readObject();
            }
         }
         catch(IOException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to deep-copy value for copy-on-read simulation", e);
         }
      }

      @Override
      public V get(Object key) {
         return deepCopy(delegate.get(key));
      }

      @Override
      public V getOrDefault(Object key, V defaultValue) {
         V value = delegate.get(key);
         return value != null ? deepCopy(value) : defaultValue;
      }

      @Override
      public Set<Entry<K, V>> entrySet() {
         Set<Entry<K, V>> copy = new LinkedHashSet<>();

         for(Entry<K, V> entry : delegate.entrySet()) {
            copy.add(new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), deepCopy(entry.getValue())));
         }

         return copy;
      }

      @Override
      public Collection<V> values() {
         List<V> copy = new ArrayList<>();

         for(V value : delegate.values()) {
            copy.add(deepCopy(value));
         }

         return copy;
      }

      @Override
      public int size() {
         return delegate.size();
      }

      @Override
      public boolean isEmpty() {
         return delegate.isEmpty();
      }

      @Override
      public boolean containsKey(Object key) {
         return delegate.containsKey(key);
      }

      @Override
      public boolean containsValue(Object value) {
         return delegate.containsValue(value);
      }

      @Override
      public V put(K key, V value) {
         return delegate.put(key, value);
      }

      @Override
      public V remove(Object key) {
         return delegate.remove(key);
      }

      @Override
      public void putAll(Map<? extends K, ? extends V> m) {
         delegate.putAll(m);
      }

      @Override
      public void clear() {
         delegate.clear();
      }

      @Override
      public Set<K> keySet() {
         return delegate.keySet();
      }

      @Override
      public void lock(K key) {
         delegate.lock(key);
      }

      @Override
      public void lock(K key, long leaseTime, TimeUnit timeUnit) {
         delegate.lock(key, leaseTime, timeUnit);
      }

      @Override
      public void unlock(K key) {
         delegate.unlock(key);
      }

      @Override
      public void set(K key, V value) {
         delegate.set(key, value);
      }

      @Override
      public void removeAll(Set<? extends K> keys) {
         delegate.removeAll(keys);
      }

      @Override
      public void removeAll() {
         delegate.removeAll();
      }
   }
}
