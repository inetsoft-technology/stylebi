/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.viewsheet;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.ignite.IgniteCluster;
import inetsoft.sree.internal.cluster.ignite.IgniteClusterTestUtils;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/**
 * Bug #79465: the bookmark lock map is one cluster-wide Ignite cache. When any node destroyed it
 * (the manager did so in its @PreDestroy), every other node's manager kept a proxy to the stopped
 * cache and threw CacheStoppedException on each viewsheet close until the JVM restarted. Runs two
 * embedded server nodes in one JVM; the manager of node A must keep working after node B
 * destroys the cache.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class BookmarkLockManagerClusterTest {
   private static final String MAP_NAME = "BOOKMARK_LOCK_MAP";

   @BeforeAll
   static void startIgnite(@TempDir Path workDir) {
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);
      clusterA = IgniteClusterTestUtils.getIgniteCluster("lockA", ipFinder, workDir);
      clusterB = IgniteClusterTestUtils.getIgniteCluster("lockB", ipFinder, workDir);
   }

   @AfterAll
   static void stopIgnite() {
      clusterB.close();
      clusterA.close();
      clusterA = clusterB = null;
   }

   @Test
   void managerSurvivesCacheDestroyedByAnotherNode() {
      try(MockedStatic<Cluster> cluster = mockStatic(Cluster.class)) {
         cluster.when(Cluster::getInstance).thenReturn(clusterA);
         BookmarkLockManager manager = new BookmarkLockManager();
         manager.lock("asset/bm", "alice", "rt1");
         assertEquals("alice", manager.getLockedBookmarkUser("asset/bm", "bob"));

         // what another node's shutdown did: stops the cache on every node
         clusterB.destroyMap(MAP_NAME);

         assertDoesNotThrow(() -> manager.unlockAll("alice", "rt1"));
         assertDoesNotThrow(() -> manager.lock("asset/bm", "carol", "rt2"));
         assertEquals("carol", manager.getLockedBookmarkUser("asset/bm", "bob"));
         assertDoesNotThrow(() -> manager.unlock("asset/bm", "carol", "rt2"));
         assertNull(manager.getLockedBookmarkUser("asset/bm", "bob"));
      }
   }

   @Test
   void locksAreSharedBetweenNodes() {
      BookmarkLockManager managerA;
      BookmarkLockManager managerB;

      try(MockedStatic<Cluster> cluster = mockStatic(Cluster.class)) {
         cluster.when(Cluster::getInstance).thenReturn(clusterA);
         managerA = new BookmarkLockManager();
         managerA.lock("asset/bm2", "alice", "rt3");

         cluster.when(Cluster::getInstance).thenReturn(clusterB);
         managerB = new BookmarkLockManager();
         assertEquals("alice", managerB.getLockedBookmarkUser("asset/bm2", "bob"));
         managerB.unlockAll("alice", "rt3");

         cluster.when(Cluster::getInstance).thenReturn(clusterA);
         assertNull(managerA.getLockedBookmarkUser("asset/bm2", "bob"));
      }
   }

   private static IgniteCluster clusterA;
   private static IgniteCluster clusterB;
}
