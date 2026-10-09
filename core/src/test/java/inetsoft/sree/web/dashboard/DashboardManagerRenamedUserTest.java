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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/*
 * Bug #78101 regression coverage.
 *
 * A user rename records the old name in a cluster map, which a dashboard create checks, so that
 * a create of the old name that starts on another node after the rename (when the old name is no
 * longer in the security provider) fails. Two managers stand for two nodes: the record is in the
 * cluster map, not in a manager.
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
      node1.addRenamedUser(new IdentityID("u0", "OrgA"));

      assertTrue(node2.isRenamedUser(new IdentityID("u0", "OrgA")));
      // the org of the current-org path is lowercased
      assertTrue(node2.isRenamedUser(new IdentityID("u0", "orga")));
      assertFalse(node2.isRenamedUser(new IdentityID("u1", "OrgA")));
      assertFalse(node2.isRenamedUser(new IdentityID("u0", "OrgB")));
      assertFalse(node2.isRenamedUser(null));
   }

   @Test
   void expiredRenameIsIgnoredAndRemoved() {
      String old = new IdentityID("u0", "orga").convertToKey();
      renamedUsers().put(old, System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));

      assertFalse(node2.isRenamedUser(new IdentityID("u0", "orga")));

      node1.addRenamedUser(new IdentityID("u1", "orga"));

      assertFalse(renamedUsers().containsKey(old), "expired record removed by the next rename");
      assertTrue(node2.isRenamedUser(new IdentityID("u1", "orga")));
   }

   private static DashboardManager newManager() {
      return new DashboardManager(mock(SecurityEngine.class), mock(DashboardRegistryManager.class),
                                  mock(KeyValueStorageManager.class));
   }

   private static Map<String, Long> renamedUsers() {
      return Cluster.getInstance().getReplicatedMap(MAP);
   }
}
