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
package inetsoft.uql.asset.sync;

/*
 * Mechanism 1: dependency reverse-index migration (DependencyStorageService).
 *
 * Scenario list and mechanism write-up live in
 * community/core/src/test/resources/docs/org-lifecycle-resource-matrix.md,
 * section "一、机制一：依赖反向索引迁移" -- not duplicated here.
 *
 * Landed: 1a (copy), 1b (rename), 1c (delete), 1d (duplicate/retried migration is idempotent),
 * 1e (two source orgs migrating into the same target org merge additively instead of clobbering
 * each other), 1f (two threads racing the same rename concurrently -- no exception, no duplicate
 * or lost data, regardless of interleaving), 1g (a KeyValueStorage reference silently reports
 * zero entries once closed, instead of throwing -- the exact client-visible symptom that would
 * fire if KeyValueStorageManager's shared 50-store LRU cache evicts+closes a storage that
 * migrateStorageData() is still holding a reference to; see the matrix doc's "已确认的生产风险"
 * for why this is a real, not hypothetical, production concern in a busy multi-tenant cluster).
 */

import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.PermissionMatrixOrgLifecycleTest;
import inetsoft.storage.KeyValueStorage;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.AssetRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  PermissionMatrixOrgLifecycleTest.CopyOnReadClusterConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class OrgLifecycleDependencyMigrationTest {

   @Autowired
   private KeyValueStorageManager keyValueStorageManager;

   private DependencyStorageService service;
   private final List<String> seededOrgIds = new ArrayList<>();

   @BeforeEach
   void setUp() {
      service = new DependencyStorageService(keyValueStorageManager);
   }

   @AfterEach
   void tearDown() {
      for(String orgId : seededOrgIds) {
         try {
            service.removeDependencyStorage(orgId);
         }
         catch(Exception ignore) {
            // already removed by the scenario itself -- deleteStore()/close() on an
            // already-gone store is a documented no-op (see scenario 1d), nothing to clean up
         }
      }

      seededOrgIds.clear();
   }

   // ── scenario 1a: copy leaves the source org's data untouched ──

   @Test
   void copy_seedsTargetOrg_leavesSourceOrgIntact() throws Exception {
      String fromOrgId = "dep_copy_from";
      String toOrgId = "dep_copy_to";
      String key = seedDependency(fromOrgId, "/report1", "/lookup1");

      service.copyStorageData(org(fromOrgId), org(toOrgId));

      String newKey = AssetEntry.createAssetEntry(key)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier(true);
      DependenciesInfo migrated = (DependenciesInfo) service.getWithOrg(newKey, toOrgId);
      assertNotNull(migrated, "copy must produce a same-shape key under the target org");
      assertEquals(toOrgId, ((AssetEntry) migrated.getDependencies().get(0)).getOrgID(),
                  "the copied AssetObject's own org attribution must follow the target org");

      // must be verified under the copy-on-read Cluster (class-level @ContextConfiguration) --
      // syncDependencyData() mutates the RenameTransformObject read from oStorage in place, so
      // this assertion would falsely pass even if it corrupted the source under a plain MockCluster
      DependenciesInfo sourceStillIntact = (DependenciesInfo) service.getWithOrg(key, fromOrgId);
      assertNotNull(sourceStillIntact, "copy must not remove the source org's own key");
      assertEquals(fromOrgId, ((AssetEntry) sourceStillIntact.getDependencies().get(0)).getOrgID(),
                  "the source org's own copy of the AssetObject must still point at the source org");
   }

   // ── scenario 1b: rename migrates and removes the source, no orphan ──

   @Test
   void rename_migratesAndRemovesSource_noOrphan() throws Exception {
      String fromOrgId = "dep_rename_from";
      String toOrgId = "dep_rename_to";
      String key = seedDependency(fromOrgId, "/report2", "/lookup2");

      service.migrateStorageData(org(fromOrgId), org(toOrgId), true);

      String newKey = AssetEntry.createAssetEntry(key)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier(true);
      assertNotNull(service.getWithOrg(newKey, toOrgId), "target org must receive the migrated key");
      assertNull(service.getWithOrg(key, fromOrgId),
                "source org's key must be gone -- no orphan left behind after rename");
   }

   // ── scenario 1c: delete removes the whole bucket ──

   @Test
   void delete_removesWholeStorage() throws Exception {
      String orgId = "dep_delete_org";
      String key = seedDependency(orgId, "/report3", "/lookup3");

      service.removeDependencyStorage(orgId);

      assertNull(service.getWithOrg(key, orgId), "delete must leave no residual entry");
   }

   // ── scenario 1d: a duplicate/retried migration call is idempotent ──

   @Test
   void duplicateRenameInvocation_secondCallIsNoOpNotError() throws Exception {
      String fromOrgId = "dep_retry_from";
      String toOrgId = "dep_retry_to";
      String key = seedDependency(fromOrgId, "/report4", "/lookup4");

      // first call: a genuine migration
      service.migrateStorageData(org(fromOrgId), org(toOrgId), true);

      // second call: simulates a duplicate cluster request (double submit, retried-after-timeout
      // client, etc.) hitting an already-migrated source org -- must not throw, must not duplicate
      // or corrupt data in the target
      assertDoesNotThrow(() -> service.migrateStorageData(org(fromOrgId), org(toOrgId), true),
                        "retrying migrateStorageData against an already-empty source org must " +
                        "be a safe no-op, since concurrent duplicate org-rename requests are " +
                        "plausible in a cluster");

      String newKey = AssetEntry.createAssetEntry(key)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier(true);
      assertNotNull(service.getWithOrg(newKey, toOrgId),
                   "the target org's data from the first call must still be there after the retry");
   }

   // ── scenario 1e: two different source orgs migrating into the same target merge additively ──

   @Test
   void twoSourceOrgs_migrateIntoSameTarget_mergeAdditively() throws Exception {
      String sourceAId = "dep_merge_a";
      String sourceBId = "dep_merge_b";
      String targetId = "dep_merge_target";

      String keyA = seedDependency(sourceAId, "/reportA", "/lookupA");
      String keyB = seedDependency(sourceBId, "/reportB", "/lookupB");

      service.migrateStorageData(org(sourceAId), org(targetId), true);
      service.migrateStorageData(org(sourceBId), org(targetId), true);

      String newKeyA = AssetEntry.createAssetEntry(keyA)
         .cloneAssetEntry(new Organization(targetId)).toIdentifier(true);
      String newKeyB = AssetEntry.createAssetEntry(keyB)
         .cloneAssetEntry(new Organization(targetId)).toIdentifier(true);

      assertNotNull(service.getWithOrg(newKeyA, targetId),
                    "target org must retain org A's migrated data after a later, unrelated " +
                    "migration from org B lands in the same target");
      assertNotNull(service.getWithOrg(newKeyB, targetId),
                    "target org must also have org B's migrated data");
   }

   // ── scenario 1f: two threads racing the same rename concurrently ──

   @Test
   @Timeout(30)
   void concurrentDuplicateRename_noExceptionNoDuplicateNoLoss() throws Exception {
      String fromOrgId = "dep_concurrent_from";
      String toOrgId = "dep_concurrent_to";
      seedDependency(fromOrgId, "/reportC1", "/lookupC1");
      seedDependency(fromOrgId, "/reportC2", "/lookupC2");

      Organization fromOrg = org(fromOrgId);
      Organization toOrg = org(toOrgId);

      // no lock guards DependencyStorageService.migrateStorageData() -- two admins (or a client
      // retry racing the original request) triggering the same org rename from different cluster
      // nodes at the same time is exactly the "多人在集群中操作" scenario this test targets.
      ExecutorService executor = Executors.newFixedThreadPool(2);
      CyclicBarrier barrier = new CyclicBarrier(2);

      try {
         Callable<Void> task = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            service.migrateStorageData(fromOrg, toOrg, true);
            return null;
         };

         List<Future<Void>> futures = executor.invokeAll(List.of(task, task));

         for(Future<Void> future : futures) {
            // must not surface an uncaught exception to either caller regardless of which
            // thread's read/write/delete sequence interleaves first
            assertDoesNotThrow(() -> future.get(10, TimeUnit.SECONDS),
                              "concurrent duplicate migration must not throw from either caller");
         }
      }
      finally {
         executor.shutdownNow();
      }

      assertEquals(2, countEntries(toOrgId), "target org must end up with both dependencies " +
                  "exactly once -- no duplicates from the race, no loss from the race");
      assertEquals(0, countEntries(fromOrgId), "source org must end up fully migrated away, " +
                  "regardless of which of the two racing calls performed the actual delete");
   }

   // ── scenario 1g: a storage reference silently empties once closed, instead of throwing ──

   @Test
   void closedStorageReference_streamSilentlyEmpty_insteadOfThrowing() throws Exception {
      String orgId = "dep_closed_storage_org";
      seedDependency(orgId, "/reportD", "/lookupD");

      String storeId = orgId.toLowerCase() + "__" + "dependencyStorage";
      KeyValueStorage<RenameTransformObject> storage = keyValueStorageManager.getStorage(storeId);
      assertEquals(1, storage.stream().count(), "sanity check: the seeded entry is really there");

      // KeyValueStorageManager caps its shared cache at 50 open stores (MAX_SIZE) across every
      // KeyValueStorage consumer in the process -- dependency storage, dashboard preferences,
      // autosave, etc. all share this one cache. Its Caffeine removalListener closes whichever
      // store gets evicted. This directly simulates that eviction against a reference that
      // migrateStorageData() would still be holding mid-method.
      storage.close();

      assertEquals(0, storage.stream().count(),
                  "LocalKeyValueStorage.stream()/keys() return Stream.empty() once closed " +
                  "instead of throwing -- so migrateStorageData() would silently observe zero " +
                  "dependency records (skip putAll) and then still delete the real, populated " +
                  "source-org store via removeOld, with no exception or log surfaced anywhere");
   }

   // ── fixture helpers ──

   private Organization org(String id) {
      if(!seededOrgIds.contains(id)) {
         seededOrgIds.add(id);
      }

      return new Organization(id);
   }

   /**
    * Seeds a {@link DependenciesInfo} with one embedded {@link AssetEntry} dependency under the
    * given org, via the same public {@code put()} API production code uses (no reflection into
    * the private per-org storage accessor).
    */
   private String seedDependency(String orgId, String path, String depPath) throws Exception {
      if(!seededOrgIds.contains(orgId)) {
         seededOrgIds.add(orgId);
      }

      String key = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                  path, null, orgId).toIdentifier(true);
      AssetEntry dependency = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                             AssetEntry.Type.WORKSHEET, depPath, null, orgId);
      DependenciesInfo info = new DependenciesInfo();
      info.setDependencies(new ArrayList<>(List.of((AssetObject) dependency)));

      OrganizationManager.runInOrgScope(orgId, () -> {
         service.put(key, info);
         return null;
      });

      return key;
   }

   private long countEntries(String orgId) throws Exception {
      String storeId = orgId.toLowerCase() + "__" + "dependencyStorage";
      KeyValueStorage<RenameTransformObject> storage = keyValueStorageManager.getStorage(storeId);
      return storage.stream().count();
   }
}
