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
import inetsoft.sree.internal.cluster.MultiMap;
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
import java.util.Collection;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/**
 * Bug #79465: the bookmark lock map is one cluster-wide Ignite cache. When any node destroyed it
 * (the manager did so in its @PreDestroy), every other node's manager kept a proxy to the stopped
 * cache and threw CacheStoppedException on each viewsheet close until the JVM restarted. Runs two
 * embedded server nodes in one JVM; the manager of node A must keep working after node B
 * destroys the cache.
 *
 * Bug #82336: a lock is stored with the id of the node that holds the runtime sheet. When that
 * node leaves the cluster (killed or stopped), the sheet is gone and no node sweeps it, so the
 * lock must no longer refuse other users; a node that reloads the sheet re-asserts the lock.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
@Tag("cluster")
class BookmarkLockManagerClusterTest {
   private static final String MAP_NAME = "BOOKMARK_LOCK_MAP";

   @BeforeAll
   static void startIgnite(@TempDir Path workDir) {
      ipFinder = new TcpDiscoveryVmIpFinder(true);
      clusterDir = workDir;
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

   @Test
   void lockOfStoppedNodeIsNotReported() throws Exception {
      try(MockedStatic<Cluster> cluster = mockStatic(Cluster.class)) {
         lockOnStoppedNode(cluster, manager -> manager.lock("asset/bm82336a", "alice", "rt82336a"));
         BookmarkLockManager managerA = new BookmarkLockManager();

         assertNull(managerA.getLockedBookmarkUser("asset/bm82336a", "bob"),
                    "the lock of a node that left the cluster must not refuse other users");

         // the session-end close of the reloaded sheet must stay a no-op on the dead entry
         assertDoesNotThrow(() -> managerA.unlockAll("alice", "rt82336a"));
         assertNull(clusterA.<String, BookmarkLockManager.BookmarkLock>getMultiMap(MAP_NAME)
                       .get("asset/bm82336a"));
      }
   }

   @Test
   void lockOfLiveNodeIsReported() {
      try(MockedStatic<Cluster> cluster = mockStatic(Cluster.class)) {
         cluster.when(Cluster::getInstance).thenReturn(clusterB);
         new BookmarkLockManager().lock("asset/bm82336b", "alice", "rt82336b");

         cluster.when(Cluster::getInstance).thenReturn(clusterA);
         BookmarkLockManager managerA = new BookmarkLockManager();
         assertEquals("alice", managerA.getLockedBookmarkUser("asset/bm82336b", "bob"));
         assertNull(managerA.getLockedBookmarkUser("asset/bm82336b", "alice"));
         managerA.unlockAll("alice", "rt82336b");
         assertNull(managerA.getLockedBookmarkUser("asset/bm82336b", "bob"));
      }
   }

   @Test
   void lockWithoutNodeIdIsReported() {
      // written by an older version, whose holder cannot be checked
      MultiMap<String, BookmarkLockManager.BookmarkLock> map = clusterA.getMultiMap(MAP_NAME);
      map.put("asset/bm82336c", new BookmarkLockManager.BookmarkLock("alice", "rt82336c"));

      try(MockedStatic<Cluster> cluster = mockStatic(Cluster.class)) {
         cluster.when(Cluster::getInstance).thenReturn(clusterA);
         BookmarkLockManager managerA = new BookmarkLockManager();
         assertEquals("alice", managerA.getLockedBookmarkUser("asset/bm82336c", "bob"));
         managerA.unlockAll("alice", "rt82336c");
         assertNull(managerA.getLockedBookmarkUser("asset/bm82336c", "bob"));
      }
   }

   @Test
   void lockAndRelockOnLiveNodeReplaceTheDeadNodesEntry() throws Exception {
      try(MockedStatic<Cluster> cluster = mockStatic(Cluster.class)) {
         lockOnStoppedNode(cluster, manager -> {
            manager.lock("asset/bm82336d", "alice", "rt82336d");
            manager.lock("asset/bm82336e", "alice", "rt82336d");
            manager.lock("asset/bm82336f", "alice", "rt82336d");
            manager.lock("asset/bm82336f", "carol", "rt82336x");
         });
         BookmarkLockManager managerA = new BookmarkLockManager();

         // the sheet's own lock call on the node that reloaded it
         managerA.lock("asset/bm82336d", "alice", "rt82336d");
         assertEquals("alice", managerA.getLockedBookmarkUser("asset/bm82336d", "bob"));
         assertSingleLockOf(clusterA.getLocalNodeId(), "asset/bm82336d");

         // the reload hook re-asserts every lock the sheet still holds
         managerA.relockAll("alice", "rt82336d");
         assertEquals("alice", managerA.getLockedBookmarkUser("asset/bm82336e", "bob"));
         assertSingleLockOf(clusterA.getLocalNodeId(), "asset/bm82336e");

         // another sheet's lock of the dead node is left alone
         assertNull(managerA.getLockedBookmarkUser("asset/bm82336f", "alice"));
         assertEquals("alice", managerA.getLockedBookmarkUser("asset/bm82336f", "carol"));

         // a lock released before the reload is not taken again
         managerA.unlock("asset/bm82336d", "alice", "rt82336d");
         managerA.relockAll("alice", "rt82336d");
         assertNull(managerA.getLockedBookmarkUser("asset/bm82336d", "bob"));

         managerA.unlockAll("alice", "rt82336d");
         managerA.unlockAll("carol", "rt82336x");
         assertNull(managerA.getLockedBookmarkUser("asset/bm82336e", "bob"));
         assertNull(managerA.getLockedBookmarkUser("asset/bm82336f", "alice"));
      }
   }

   /**
    * Starts a third node, takes the locks there and stops it, waiting until node A no longer
    * sees it. Leaves Cluster.getInstance() returning node A.
    */
   private static void lockOnStoppedNode(MockedStatic<Cluster> cluster,
                                         Consumer<BookmarkLockManager> locker)
      throws InterruptedException
   {
      IgniteCluster clusterC = IgniteClusterTestUtils.getIgniteCluster(
         "lockC" + System.nanoTime(), ipFinder, clusterDir);
      String nodeC = clusterC.getLocalNodeId();

      try {
         cluster.when(Cluster::getInstance).thenReturn(clusterC);
         locker.accept(new BookmarkLockManager());
         Collection<BookmarkLockManager.BookmarkLock> written =
            clusterA.<String, BookmarkLockManager.BookmarkLock>getMultiMap(MAP_NAME).values();
         assertTrue(written.stream().anyMatch(l -> nodeC.equals(l.getNodeId())),
                    "premise: the locks carry the id of the node that took them");
      }
      finally {
         clusterC.close();
         cluster.when(Cluster::getInstance).thenReturn(clusterA);
      }

      long end = System.currentTimeMillis() + 30000L;

      while(clusterA.getClusterNodeIds().contains(nodeC) && System.currentTimeMillis() < end) {
         Thread.sleep(50L);
      }

      assertFalse(clusterA.getClusterNodeIds().contains(nodeC), "premise: node C has left");
   }

   private static void assertSingleLockOf(String nodeId, String key) {
      Collection<BookmarkLockManager.BookmarkLock> locks =
         clusterA.<String, BookmarkLockManager.BookmarkLock>getMultiMap(MAP_NAME).get(key);
      assertNotNull(locks);
      assertEquals(1, locks.size(), "the dead node's entry must be replaced, not duplicated");
      assertEquals(nodeId, locks.iterator().next().getNodeId());
   }

   private static TcpDiscoveryVmIpFinder ipFinder;
   private static Path clusterDir;
   private static IgniteCluster clusterA;
   private static IgniteCluster clusterB;
}
