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
package inetsoft.sree.web.dashboard;

/*
 * Scenarios 4a/4b (matrix rows): community/core/src/test/resources/docs/org-lifecycle-resource-matrix.md,
 * section "三、其他机制" / "3.2 Dashboard" -- not duplicated here. Covers DashboardManager's
 * per-identity dashboard *preference* KeyValueStorage bucket (key space {orgId}__dashboards),
 * which is a completely independent storage mechanism from DashboardRegistry's file-based
 * VSDashboard *definitions* (covered separately by inetsoft.sree.security
 * .DashboardRegistryOrgLifecycleTest -- scenarios 4c-4f).
 *
 * DashboardManager.copyStorageData()/removeDashboardStorage() only touch the
 * KeyValueStorageManager-backed bucket -- SecurityEngine/DashboardRegistryManager (other
 * constructor dependencies) are never read by either method, so both are passed as plain mocks
 * here; only KeyValueStorageManager needs to be the real BaseTestConfiguration bean.
 */

import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.KeyValueStorage;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DashboardManagerOrgLifecycleTest {

   @Autowired
   private KeyValueStorageManager keyValueStorageManager;

   private DashboardManager manager;
   private final List<String> seededOrgIds = new ArrayList<>();

   @BeforeEach
   void setUp() {
      manager = new DashboardManager(mock(SecurityEngine.class),
                                     mock(DashboardRegistryManager.class),
                                     keyValueStorageManager);
   }

   @AfterEach
   void tearDown() {
      for(String orgId : seededOrgIds) {
         try {
            manager.removeDashboardStorage(orgId);
         }
         catch(Exception ignore) {
            // already removed by the scenario itself -- deleteStore() on an already-gone store
            // is expected to be harmless here, same tolerance as the sibling dependency-migration
            // test's tearDown()
         }
      }

      seededOrgIds.clear();
   }

   // ── scenario 4a: copyStorageData() copies the whole bucket without touching the source ──

   @Test
   void copy_seedsTargetOrg_leavesSourceOrgIntact() throws Exception {
      String fromOrgId = "dash_copy_from";
      String toOrgId = "dash_copy_to";
      String key = seedDashboardData(fromOrgId, "1:alice", List.of("Sales"), List.of("Marketing"));

      manager.copyStorageData(fromOrgId, toOrgId);

      DashboardManager.DashboardData copied = getEntry(toOrgId, key);
      assertNotNull(copied, "target org must receive the copied entry under the same key");
      assertEquals(List.of("Sales"), copied.getDashboards());
      assertEquals(List.of("Marketing"), copied.getDeselected());

      DashboardManager.DashboardData sourceStillThere = getEntry(fromOrgId, key);
      assertNotNull(sourceStillThere, "copy must not remove the source org's own entry");
      assertEquals(List.of("Sales"), sourceStillThere.getDashboards(),
                  "source org's entry must be unmodified by the copy");
   }

   // copy/rename share the exact same copyStorageData() call (see matrix row 4a) -- rename's
   // additional "delete the source" step is exercised separately by scenario 4b below via
   // removeDashboardStorage(), the same method the real rename path calls on the source org after
   // copying (IdentityService.removeStorages():1099).

   // ── scenario 4b: removeDashboardStorage() deletes the whole bucket ──

   @Test
   void delete_removesWholeStorage() throws Exception {
      String orgId = "dash_delete_org";
      String key = seedDashboardData(orgId, "1:bob", List.of("Ops"), List.of());

      manager.removeDashboardStorage(orgId);

      assertNull(getEntry(orgId, key), "delete must leave no residual entry");
   }

   // ── fixture helpers ──

   private String seedDashboardData(String orgId, String key, List<String> dashboards,
                                    List<String> deselected) throws Exception
   {
      if(!seededOrgIds.contains(orgId)) {
         seededOrgIds.add(orgId);
      }

      DashboardManager.DashboardData data = new DashboardManager.DashboardData();
      data.setDashboards(new ArrayList<>(dashboards));
      data.setDeselected(new ArrayList<>(deselected));

      KeyValueStorage<DashboardManager.DashboardData> storage = getStorage(orgId);
      storage.put(key, data).get(10L, TimeUnit.SECONDS);

      return key;
   }

   private DashboardManager.DashboardData getEntry(String orgId, String key) {
      return getStorage(orgId).get(key);
   }

   private KeyValueStorage<DashboardManager.DashboardData> getStorage(String orgId) {
      String storeId = orgId.toLowerCase() + "__dashboards";
      return keyValueStorageManager.getStorage(storeId);
   }
}
