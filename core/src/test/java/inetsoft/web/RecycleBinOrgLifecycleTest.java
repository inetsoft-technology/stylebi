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
package inetsoft.web;

/*
 * Scenarios 11a-11c (matrix rows): community/core/src/test/resources/docs/org-lifecycle-resource-matrix.md,
 * section "三、其他机制" / "3.7 Recycle Bin". RecycleBin's own methods (copyStorageData()/
 * removeStorage()/migrateEntries()) are all public, so they are called directly -- same style as
 * 3.6's OrgLifecycleRepletRegistryIntegrationTest -- rather than through the full
 * IdentityService.copyStorages()/removeStorages() orchestration.
 *
 * "Current org" for RecycleBin.getEntry()/addEntry()/getEntries() (the no-explicit-orgID overloads)
 * resolves via OrganizationManager.getCurrentOrgID() -> ThreadContext.getContextPrincipal() ->
 * OrganizationContextHolder, NOT OrganizationManager.setCurrentOrgID() (that setter is a no-op stub
 * in community -- confirmed by reading OrganizationManager.java:61-62). The actAs() helper below
 * mirrors OrgLifecycleScopedPropertiesIntegrationTest's pattern for switching "current org" in a
 * test: install a throwaway SRPrincipal via ThreadContext plus OrganizationContextHolder.
 *
 * 11b side discovery: migrateEntries() mutates the Entry objects in the Map handed to it
 * in-memory (originalUser/permission), but has no storage.put() call of its own anywhere in its
 * body -- so even INDEPENDENTLY of the already-confirmed "never called by copyStorages()" gap,
 * wiring it in as-is would not be sufficient on its own; a caller would also need to re-persist
 * each mutated Entry. See the test method for the empirical confirmation.
 */

import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrganizationContextHolder;
import inetsoft.sree.security.Permission;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RecycleBinOrgLifecycleTest {

   @Autowired
   private KeyValueStorageManager keyValueStorageManager;

   private RecycleBin recycleBin;

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      OrganizationContextHolder.setCurrentOrgId(null);
   }

   // ── scenario 11a: copyStorageData(oId, id) -- copy/rename shared, whole-bucket KV copy, no source deletion ──

   @Test
   void copy_copyStorageData_wholeBucketCopied_sourceUntouched() {
      recycleBin = new RecycleBin(keyValueStorageManager);
      String fromOrgId = "recyclebin_11a_from";
      String toOrgId = "recyclebin_11a_to";

      IdentityID originalUser = new IdentityID("alice", fromOrgId);
      Permission permission = new Permission();
      permission.setGrants(ResourceAction.READ, Identity.USER,
                           Set.of(new Permission.PermissionIdentity("alice", fromOrgId)));

      actAs(fromOrgId);
      recycleBin.addEntry("Recycle Bin/abc123", "My Dashboards/report1", "report1", permission,
                          RepositoryEntry.VIEWSHEET, AssetRepository.USER_SCOPE, originalUser);

      recycleBin.copyStorageData(fromOrgId, toOrgId);

      actAs(toOrgId);
      RecycleBin.Entry migrated = recycleBin.getEntry("Recycle Bin/abc123");
      assertNotNull(migrated, "copy must produce the entry under the target org's own bucket");
      assertEquals("My Dashboards/report1", migrated.getOriginalPath());
      assertEquals("report1", migrated.getName());
      assertEquals(RepositoryEntry.VIEWSHEET, migrated.getType());

      // pins the 11b finding from the copy side: copyStorageData() is a pure whole-bucket KV
      // copy -- key AND value bytes are carried over unchanged, so the migrated entry's
      // originalUser still names the SOURCE org, not the target org it now lives under.
      assertEquals(fromOrgId, migrated.getOriginalUser().getOrgID(),
                  "CURRENT (documents 11b): copyStorageData() never rewrites entry contents, so " +
                  "originalUser's org segment is still the source org after copy");

      actAs(fromOrgId);
      RecycleBin.Entry source = recycleBin.getEntry("Recycle Bin/abc123");
      assertNotNull(source, "copy must not remove the source org's own entry");
      assertEquals(fromOrgId, source.getOriginalUser().getOrgID(),
                  "source org's entry must be completely untouched by the copy");
   }

   // ── scenario 11b: migrateEntries() -- confirmed dead code, plus two more gaps if it weren't ──

   @Test
   void migrateEntries_identityRewritten_permissionGrantsRewritten_butNeverPersisted() {
      recycleBin = new RecycleBin(keyValueStorageManager);
      String fromOrgId = "recyclebin_11b_from";
      String toOrgId = "recyclebin_11b_to";

      IdentityID originalUser = new IdentityID("alice", fromOrgId);
      Permission permission = new Permission();
      permission.setGrants(ResourceAction.READ, Identity.USER,
                           Set.of(new Permission.PermissionIdentity("alice", fromOrgId)));

      actAs(fromOrgId);
      recycleBin.addEntry("Recycle Bin/def456", "My Dashboards/report2", "report2", permission,
                          RepositoryEntry.VIEWSHEET, AssetRepository.USER_SCOPE, originalUser);

      // Mirrors the real orchestration: copyStorages() only ever calls copyStorageData() (11a) --
      // migrateEntries() is never invoked from there (confirmed by codebase-wide search, no call
      // site anywhere). Calling it here by hand characterizes what it WOULD do if it were wired
      // in, rather than asserting on dead code that never runs in production.
      recycleBin.copyStorageData(fromOrgId, toOrgId);

      actAs(toOrgId);
      RecycleBin.Entry migratedInMemory = recycleBin.getEntry("Recycle Bin/def456");
      Map<String, RecycleBin.Entry> data = new HashMap<>();
      data.put("Recycle Bin/def456", migratedInMemory);

      Organization oorg = new Organization(fromOrgId);
      Organization norg = new Organization(toOrgId);

      // "current org" must be the SOURCE org for the permission-grant half of this call to do
      // anything at all -- see the sibling scenario below for what happens otherwise. Charitable
      // case first: proves the rewrite logic itself is correct before showing where it breaks.
      actAs(fromOrgId);
      recycleBin.migrateEntries(data, oorg, norg);

      assertEquals(toOrgId, migratedInMemory.getOriginalUser().getOrgID(),
                  "migrateEntries() DOES correctly rewrite originalUser's org segment on the " +
                  "Entry object it's handed, regardless of current-org context");
      Permission.PermissionIdentity expectedGrant =
         new Permission.PermissionIdentity("alice", toOrgId);
      assertTrue(containsGrant(
                    migratedInMemory.getPermission().getGrants(ResourceAction.READ, Identity.USER, toOrgId),
                    expectedGrant),
                "with current org == source org at call time, migrateEntries() DOES correctly " +
                "rewrite the permission's USER grant org segment too");

      // But it has no storage.put()/persist call anywhere in its own body -- confirmed by re-reading
      // the SAME path from storage: if the mutation had been persisted, this would also read back
      // the target org. Documents a SECOND gap beyond 11b's "never called" finding: even if this
      // method were wired into copyStorages(), it would need an explicit re-put per entry to have
      // any observable effect at all.
      RecycleBin.Entry reread = recycleBin.getEntry("Recycle Bin/def456");
      assertEquals(fromOrgId, reread.getOriginalUser().getOrgID(),
                  "documents a second gap beyond 11b's dead-code finding: migrateEntries()'s " +
                  "in-memory mutation is never written back to the KeyValueStorage -- a fresh " +
                  "read of the same key still shows the un-migrated originalUser, so wiring this " +
                  "method into copyStorages() as-is would still not fix 11b on its own");
   }

   // Discovered while building the charitable-path test above: migratePermissionGrants()
   // (RecycleBin.java:198-266) reads each grant set via the 2-arg Permission.getGrants(action,
   // identityType) overload, which silently filters to OrganizationManager.getCurrentOrgID() --
   // NOT the source org being migrated (there is a 3-arg overload that takes an explicit orgId,
   // but migratePermissionGrants() doesn't use it). So the permission-grant half of
   // migrateEntries() only does anything if the thread's ambient "current org" happens to equal
   // the entry's own grant org at the exact moment migrateEntries() is called -- a THIRD gap,
   // layered underneath the "never called" (11b) and "never persisted" (above) ones: even a
   // future fix that wires this method in and adds a persist call would still need to get this
   // current-org coupling right, or the permission rewrite would silently no-op depending on
   // unrelated ambient thread state -- the same general shape of risk already flagged for Data
   // Cycle in 5f/5h (三、3.3) and Presentation settings in Issue #75769 (三、3.8).
   @Test
   void migrateEntries_permissionGrantsSilentlyUnchanged_whenCurrentOrgDoesNotMatchGrantsOrg() {
      recycleBin = new RecycleBin(keyValueStorageManager);
      String fromOrgId = "recyclebin_11b_ctxgap_from";
      String toOrgId = "recyclebin_11b_ctxgap_to";

      IdentityID originalUser = new IdentityID("alice", fromOrgId);
      Permission permission = new Permission();
      permission.setGrants(ResourceAction.READ, Identity.USER,
                           Set.of(new Permission.PermissionIdentity("alice", fromOrgId)));

      actAs(fromOrgId);
      recycleBin.addEntry("Recycle Bin/jkl012", "My Dashboards/report4", "report4", permission,
                          RepositoryEntry.VIEWSHEET, AssetRepository.USER_SCOPE, originalUser);
      recycleBin.copyStorageData(fromOrgId, toOrgId);

      actAs(toOrgId);
      RecycleBin.Entry migratedInMemory = recycleBin.getEntry("Recycle Bin/jkl012");
      Map<String, RecycleBin.Entry> data = new HashMap<>();
      data.put("Recycle Bin/jkl012", migratedInMemory);

      // current org left at toOrgId (a plausible real value -- e.g. the calling thread's
      // ambient org after a rename, or simply whatever it happened to be before this call) --
      // NOT switched to fromOrgId this time.
      recycleBin.migrateEntries(data, new Organization(fromOrgId), new Organization(toOrgId));

      assertEquals(toOrgId, migratedInMemory.getOriginalUser().getOrgID(),
                  "originalUser is rewritten unconditionally, independent of current-org context " +
                  "-- this half of migrateEntries() does not have the gap described below");

      Permission.PermissionIdentity stillSourceGrant =
         new Permission.PermissionIdentity("alice", fromOrgId);
      assertTrue(containsGrant(
                    migratedInMemory.getPermission().getGrants(ResourceAction.READ, Identity.USER, fromOrgId),
                    stillSourceGrant),
                "documents a third gap: with current org != the grant's own org at call time, " +
                "Permission.getGrants(action, identityType) (the 2-arg overload " +
                "migratePermissionGrants() uses internally) filters by " +
                "OrganizationManager.getCurrentOrgID(), not by the source org being migrated -- " +
                "so it returns an empty set here, migratePermissionGrants()'s " +
                "'if(!grants.isEmpty())' guard skips setGrants() entirely, and the permission " +
                "grant is left completely unchanged, still naming the source org");
   }

   // ── scenario 11c: removeStorage(orgID) -- whole bucket deleted ──

   @Test
   void delete_removeStorage_wholeBucketDeleted() throws Exception {
      recycleBin = new RecycleBin(keyValueStorageManager);
      String orgId = "recyclebin_11c_delete";

      actAs(orgId);
      recycleBin.addEntry("Recycle Bin/ghi789", "/report3", "report3", null,
                          RepositoryEntry.VIEWSHEET, AssetRepository.GLOBAL_SCOPE, null);
      assertNotNull(recycleBin.getEntry("Recycle Bin/ghi789"),
                   "precondition: entry must exist before delete");

      recycleBin.removeStorage(orgId);

      actAs(orgId);
      assertNull(recycleBin.getEntry("Recycle Bin/ghi789"),
                "delete must remove the whole bucket -- entry must be gone");
   }

   // ── fixture helpers ──

   private static void actAs(String orgId) {
      ThreadContext.setContextPrincipal(new SRPrincipal(new IdentityID("tester", orgId),
         new IdentityID[0], new String[0], orgId, 1L));
      OrganizationContextHolder.setCurrentOrgId(orgId);
   }

   /**
    * {@code Permission.PermissionIdentity} overrides {@code equals()} but not {@code
    * hashCode()} -- a separate, minor pre-existing inconsistency unrelated to what this file is
    * investigating -- so {@code HashSet.contains()} is not reliable for it (a value-equal
    * instance can land in the wrong bucket). This checks membership by iterating and calling
    * {@code equals()} directly instead.
    */
   private static boolean containsGrant(Set<Permission.PermissionIdentity> grants,
                                        Permission.PermissionIdentity expected)
   {
      for(Permission.PermissionIdentity grant : grants) {
         if(grant.equals(expected)) {
            return true;
         }
      }

      return false;
   }
}
