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
import inetsoft.sree.internal.cluster.MembershipEvent;
import inetsoft.sree.internal.cluster.MembershipListener;
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
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

   @Test
   void relockDoesNotRetakeALockReleasedAfterItsSnapshot() {
      String key = "asset/bm82336g";
      MultiMap<String, BookmarkLockManager.BookmarkLock> map = clusterA.getMultiMap(MAP_NAME);
      // a lock of the sheet's dead owner, still in the map when the sheet is reloaded here
      map.put(key, new BookmarkLockManager.BookmarkLock(
         "alice", "rt82336g", UUID.randomUUID().toString()));

      // relockAll reads the entry set, then the sheet is disposed on another thread (the
      // session-end close), whose unlockAll removes the lock before the relock writes it
      MultiMap<String, BookmarkLockManager.BookmarkLock> racingMap = spy(map);
      doAnswer(invocation -> {
         Object entries = invocation.callRealMethod();
         map.remove(key, new BookmarkLockManager.BookmarkLock("alice", "rt82336g"));
         return entries;
      }).when(racingMap).entrySet();

      Cluster racingCluster = mock(Cluster.class);
      doReturn(racingMap).when(racingCluster).getMultiMap(MAP_NAME);
      when(racingCluster.getLocalNodeId()).thenReturn(clusterA.getLocalNodeId());
      when(racingCluster.getClusterNodeIds()).thenAnswer(i -> clusterA.getClusterNodeIds());

      try(MockedStatic<Cluster> cluster = mockStatic(Cluster.class)) {
         cluster.when(Cluster::getInstance).thenReturn(racingCluster);
         BookmarkLockManager manager = new BookmarkLockManager();
         manager.relockAll("alice", "rt82336g");

         assertNull(map.get(key), "a lock released after the snapshot must not be taken again");
         assertNull(manager.getLockedBookmarkUser(key, "bob"));
      }
      finally {
         map.delete(key);
      }
   }

   @Test
   void replaceSwapsAPresentValueAndNeverInserts() {
      String key = "asset/bm82336h";
      MultiMap<String, BookmarkLockManager.BookmarkLock> map = clusterA.getMultiMap(MAP_NAME);
      BookmarkLockManager.BookmarkLock dead =
         new BookmarkLockManager.BookmarkLock("alice", "rt82336h", "dead-node");
      BookmarkLockManager.BookmarkLock live =
         new BookmarkLockManager.BookmarkLock("alice", "rt82336h", clusterA.getLocalNodeId());
      BookmarkLockManager.BookmarkLock other =
         new BookmarkLockManager.BookmarkLock("carol", "rt82336y", "dead-node");

      try {
         assertFalse(map.replace(key, dead, live), "an absent key is not inserted");
         assertNull(map.get(key));

         map.put(key, other);
         assertFalse(map.replace(key, dead, live), "an absent value is not inserted");
         assertEquals(List.of(other), new ArrayList<>(map.get(key)));

         map.put(key, dead);
         assertTrue(map.replace(key, dead, live));
         Collection<BookmarkLockManager.BookmarkLock> locks = map.get(key);
         assertEquals(2, locks.size());
         assertTrue(locks.stream().anyMatch(
            l -> l.equals(other) && "dead-node".equals(l.getNodeId())));
         assertTrue(locks.stream().anyMatch(
            l -> l.equals(live) && clusterA.getLocalNodeId().equals(l.getNodeId())));
      }
      finally {
         map.delete(key);
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
      CountDownLatch left = new CountDownLatch(1);
      MembershipListener listener = new MembershipListener() {
         @Override
         public void memberAdded(MembershipEvent event) {
         }

         @Override
         public void memberRemoved(MembershipEvent event) {
            if(!clusterA.getClusterNodeIds().contains(nodeC)) {
               left.countDown();
            }
         }
      };
      clusterA.addMembershipListener(listener);

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

      try {
         // the node-left event, or C already gone before the listener could see it
         assertTrue(left.await(30, TimeUnit.SECONDS) ||
                       !clusterA.getClusterNodeIds().contains(nodeC),
                    "premise: node A sees node C leave");
      }
      finally {
         clusterA.removeMembershipListener(listener);
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
