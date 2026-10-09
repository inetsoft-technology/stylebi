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

import inetsoft.sree.ViewsheetEntry;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Bug #78101 regression coverage.
 *
 * A user rename records the old name in a cluster map, which a dashboard create checks, so that
 * a create of the old name that starts on another node after the rename (when the old name is no
 * longer in the security provider) fails. Two managers stand for two nodes: the record is in the
 * cluster map, not in a manager. The record also holds the viewsheets of the creates refused
 * while the rename moves the user's assets, which the rename removes once it has moved them.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DashboardManagerRenamedUserTest {
   private static final String MAP =
      DashboardManager.class.getName() + ".renamedUsers";

   private DashboardManager node1;
   private DashboardManager node2;

   @BeforeEach
   void setUp() {
      node1 = newManager();
      node2 = newManager();
      renamedUsers().clear();
   }

   @AfterEach
   void tearDown() {
      renamedUsers().clear();
   }

   @Test
   void renamedUserIsSeenByAnotherManager() {
      node1.addRenamedUser(new IdentityID("u0", "OrgA"), new IdentityID("u0b", "OrgA"));

      assertTrue(node2.isRenamedUser(new IdentityID("u0", "OrgA")));
      assertEquals(new IdentityID("u0b", "OrgA"), node2.getRenamedUser(new IdentityID("u0", "OrgA")));
      // the org of the current-org path is lowercased
      assertTrue(node2.isRenamedUser(new IdentityID("u0", "orga")));
      assertFalse(node2.isRenamedUser(new IdentityID("u1", "OrgA")));
      assertFalse(node2.isRenamedUser(new IdentityID("u0", "OrgB")));
      assertFalse(node2.isRenamedUser(null));
   }

   @Test
   void expiredRenameIsIgnoredAndRemoved() {
      String old = new IdentityID("u0", "orga").convertToKey();
      renamedUsers().put(old, new DashboardManager.RenamedUser(
         System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1), new IdentityID("u0b", "orga"),
         false, List.of()));

      assertFalse(node2.isRenamedUser(new IdentityID("u0", "orga")));

      node1.addRenamedUser(new IdentityID("u1", "orga"), new IdentityID("u1b", "orga"));

      assertFalse(renamedUsers().containsKey(old), "expired record removed by the next rename");
      assertTrue(node2.isRenamedUser(new IdentityID("u1", "orga")));
   }

   // A create refused while the rename moves the user's assets records its viewsheet, and the
   // rename takes the records once it has moved them; a create refused after that removes its
   // viewsheet itself
   @Test
   void refusedViewsheetIsRecordedUntilTheAssetsAreMoved() {
      IdentityID old = new IdentityID("u0", "orga");
      IdentityID renamed = new IdentityID("u0b", "orga");

      assertFalse(node1.addRefusedViewsheet(old, "vs0"), "no rename: removed by the create");

      node1.addRenamedUser(old, renamed);

      assertTrue(node1.addRefusedViewsheet(old, "vs1"));
      assertTrue(node2.addRefusedViewsheet(old, "vs2"));
      assertEquals(List.of("vs1", "vs2"), node2.takeRefusedViewsheets(old));
      assertFalse(node1.addRefusedViewsheet(old, "vs3"), "moved: removed by the create");
      assertEquals(List.of(), node1.takeRefusedViewsheets(old));
      assertEquals(renamed, node2.getRenamedUser(old), "the rename is still recorded");
   }

   @Test
   void refusedViewsheetIsRemovedUnderBothNamesWithoutPermissions() throws Exception {
      IdentityID old = new IdentityID("u0", "orga");
      IdentityID renamed = new IdentityID("u0b", "orga");
      AssetEntry entry = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "d1", old);
      AssetEntry used = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                                       "d2", old);
      AssetRepository engine = mock(AssetRepository.class);
      List<String> removed = new ArrayList<>();
      when(engine.containsEntry(any())).thenReturn(true);
      doAnswer(inv -> {
         AssetEntry sheet = inv.getArgument(0);
         removed.add(sheet.getUser().getName() + "/" + sheet.getPath() + ":" +
                        Boolean.TRUE.equals(AssetRepository.IGNORE_PERM.get()));
         return null;
      }).when(engine).removeSheet(any(), any(), anyBoolean());
      // a dashboard of the new name uses its viewsheet d2
      DashboardRegistryManager registryManager = mock(DashboardRegistryManager.class);
      DashboardRegistry registry = mock(DashboardRegistry.class);
      VSDashboard dashboard = new VSDashboard();
      dashboard.setViewsheet(new ViewsheetEntry("d2", renamed));
      when(registry.getDashboardNames()).thenReturn(new String[] { "x" });
      when(registry.getDashboard("x")).thenReturn(dashboard);
      when(registryManager.getRegistry(renamed)).thenReturn(registry);
      DashboardManager manager = new DashboardManager(
         mock(SecurityEngine.class), registryManager, mock(KeyValueStorageManager.class));

      try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class, CALLS_REAL_METHODS)) {
         assetUtil.when(() -> AssetUtil.getAssetRepository(anyBoolean())).thenReturn(engine);
         manager.removeRefusedViewsheet(entry, renamed, mock(Principal.class));
         manager.removeRefusedViewsheet(used, renamed, mock(Principal.class));
         manager.removeRefusedViewsheet(entry, null, mock(Principal.class));
      }

      assertEquals(List.of("u0/d1:true", "u0b/d1:true", "u0/d2:true", "u0/d1:true"), removed);
      verify(engine, times(4)).removeSheet(any(), any(), eq(true));
      assertNotEquals(Boolean.TRUE, AssetRepository.IGNORE_PERM.get(), "the flag is cleared");
   }

   private static DashboardManager newManager() {
      return new DashboardManager(mock(SecurityEngine.class), mock(DashboardRegistryManager.class),
                                  mock(KeyValueStorageManager.class));
   }

   private static Map<String, DashboardManager.RenamedUser> renamedUsers() {
      return Cluster.getInstance().getReplicatedMap(MAP);
   }
}
