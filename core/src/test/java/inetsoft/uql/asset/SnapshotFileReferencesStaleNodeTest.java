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
package inetsoft.uql.asset;

import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.internal.cluster.ignite.IgniteCluster;
import inetsoft.sree.internal.cluster.ignite.IgniteClusterTestUtils;
import inetsoft.test.*;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.util.*;
import inetsoft.util.swap.XSwapper;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78082, the reference counts of the snapshot copies in the cache directory were removed
 * only by the JVM that added them, and the map lives as long as any node is up, so the counts of
 * a node that stopped with live snapshot tables (e.g. in a rolling restart) stayed in the map.
 * Every later copy of the same snapshot, which has the same cache path, was then not deleted when
 * its last table was closed, and the cache sweeps skipped it. A count must stop protecting its
 * file once the node that added it is gone, but not while that node is up, and a count of a JVM
 * of an older version, which has no owner, must still protect its file.
 *
 * The maps are of a real two-node Ignite cluster with the product configuration. The node that
 * stops is a separate Ignite node, the other node stays up, as in a rolling restart.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
   BaseTestConfiguration.class, SwapperTestConfiguration.class,
   SnapshotFileReferencesStaleNodeTest.TestClusterConfiguration.class
}, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SnapshotFileReferencesStaleNodeTest {
   @BeforeAll
   static void startSurvivor() throws Exception {
      clusterDir = Files.createTempDirectory("cluster-78082");
      survivorPort = freePort();
      survivor = startNode("s78082-survivor", survivorPort);
   }

   @AfterAll
   static void stopSurvivor() throws Exception {
      current = null;

      if(survivor != null) {
         survivor.close();
      }

      if(clusterDir != null) {
         try(var paths = Files.walk(clusterDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
         }
      }
   }

   @BeforeEach
   void startStoppingNode() throws Exception {
      stopping = startNode("s78082-stopping", freePort());
      waitForNodes(2);
   }

   @AfterEach
   void cleanUp() throws Exception {
      current = survivor;

      if(stopping != null) {
         stopping.close();
         stopping = null;
      }

      Map<String, Integer> map = survivor.getMap(SnapshotEmbeddedTableAssembly.FILE_REFERENCES_MAP);
      Map<String, ?> owners = survivor.getMap(SnapshotEmbeddedTableAssembly.FILE_OWNERS_MAP);

      for(File file : created) {
         map.remove(file.getAbsolutePath());
         owners.remove(file.getAbsolutePath());
         Files.deleteIfExists(file.toPath());
      }

      created.clear();
      tables.clear();
      references.clear();
      current = null;
   }

   @Test
   void laterCopyIsDeletedWhenItsLastTableClosesAfterTheOwnerNodeStopped() throws Exception {
      File file = createCopy("t78082a_1_s.tdat");
      current = stopping;
      addReference(file);
      assertEquals(1, survivorCount(file));

      stopNode();
      // the same snapshot is loaded again on the node that stayed up, at the same cache path
      Object reference = addReference(file);
      assertEquals(2, survivorCount(file));
      close(reference);

      assertFalse(file.exists(), "copy was not deleted when its last table was closed");
      assertNull(survivorCount(file), "count of the stopped node was not removed");
      assertFalse(survivorOwners().containsKey(file.getAbsolutePath()), "owners were not removed");
   }

   @Test
   void closeKeepsCopyThatAnotherLiveNodeHolds() throws Exception {
      File file = createCopy("t78082b_1_s.tdat");
      current = stopping;
      addReference(file);
      current = survivor;
      Object reference = addReference(file);
      close(reference);

      assertTrue(file.exists(), "copy held by another live node was deleted");
      assertEquals(1, survivorCount(file));
   }

   @Test
   void clearDataCacheDeletesCopyOfStoppedNodeOnly() throws Exception {
      Fixture fixture = new Fixture("c");
      long now = System.currentTimeMillis();
      // the sweep goes from the oldest file and stops after one delete when not low on disk,
      // so the kept files come first: a wrong delete of one of them leaves the stale one
      assertTrue(fixture.live.setLastModified(now - 1000 * 86400000L));
      assertTrue(fixture.older.setLastModified(now - 999 * 86400000L));
      assertTrue(fixture.stale.setLastModified(now - 998 * 86400000L));

      Method clearDataCache = ClearOldCacheFilesRunnable.class.getDeclaredMethod("clearDataCache");
      clearDataCache.setAccessible(true);
      clearDataCache.invoke(new ClearOldCacheFilesRunnable());

      fixture.assertSwept();
   }

   @Test
   void clearCacheFilesDeletesCopyOfStoppedNodeOnly() throws Exception {
      Fixture fixture = new Fixture("d");

      FileSystemService.getInstance().clearCacheFiles(null);
      waitForSweep(fixture.stale, FileSystemService.CLEAR_CACHE_FILES_THREAD);

      fixture.assertSwept();
   }

   @Test
   void startupSweepRemovesCountsOfStoppedNode() throws Exception {
      Fixture fixture = new Fixture("e");
      XSwapper swapper = new XSwapper();

      try {
         waitForSweep(fixture.stale, XSwapper.CACHE_SWEEP_THREAD);
      }
      finally {
         swapper.stop();
      }

      // the startup sweep deletes the copies whatever their counts, the counts must go too
      assertNull(survivorCount(fixture.stale), "count of the stopped node was not removed");
      assertEquals(1, survivorCount(fixture.live), "count of a live node was removed");
      assertEquals(1, survivorCount(fixture.older), "count of an older JVM was removed");
   }

   /**
    * A copy held by the node that stops, one held by the node that stays up and one counted by a
    * JVM of an older version (a count without an owner), all past every age gate. The node has
    * stopped when this returns.
    */
   private final class Fixture {
      Fixture(String name) throws Exception {
         stale = createCopy("t78082" + name + "_1_s.tdat");
         live = createCopy("t78082" + name + "_2_s.tdat");
         older = createCopy("t78082" + name + "_3_s.tdat");
         current = stopping;
         addReference(stale);
         current = survivor;
         addReference(live);
         survivor.<String, Integer>getMap(SnapshotEmbeddedTableAssembly.FILE_REFERENCES_MAP)
            .put(older.getAbsolutePath(), 1);
         stopNode();
      }

      void assertSwept() {
         assertFalse(stale.exists(), "copy of the stopped node was not deleted");
         assertTrue(live.exists(), "copy of a live node was deleted");
         assertTrue(older.exists(), "copy of an older JVM was deleted");
         assertNull(survivorCount(stale), "count of the stopped node was not removed");
         assertFalse(survivorOwners().containsKey(stale.getAbsolutePath()),
                     "owners of the stopped node were not removed");
         assertEquals(1, survivorCount(live), "count of a live node was removed");
         assertEquals(1, survivorCount(older), "count of an older JVM was removed");
      }

      final File stale;
      final File live;
      final File older;
   }

   private File createCopy(String name) throws Exception {
      File file = FileSystemService.getInstance().getCacheFile(name);
      Files.write(file.toPath(), new byte[] { 1, 2, 3 });
      // older than any age gate of the sweeps
      assertTrue(file.setLastModified(System.currentTimeMillis() - 24 * 3600000L));
      created.add(file);
      return file;
   }

   /**
    * Adds a reference of a live table to a copy on the current node, as
    * SnapshotEmbeddedTableAssembly.getTable() does when it copies the data to the cache.
    */
   private Object addReference(File file) throws Exception {
      Class<?> cls = Class.forName(
         SnapshotEmbeddedTableAssembly.class.getName() + "$EmbeddedTableReference");
      Constructor<?> cons = cls.getDeclaredConstructor(XSwappableTable.class, File[].class);
      cons.setAccessible(true);
      XSwappableTable table = new XSwappableTable();
      Object reference = cons.newInstance(table, new File[] { file });
      // keep the table and the reference reachable, so the cleaner does not close it
      tables.add(table);
      references.add(reference);
      return reference;
   }

   private static void close(Object reference) throws Exception {
      ((Cleaner.Reference<?>) reference).close();
   }

   private void stopNode() throws Exception {
      stopping.close();
      stopping = null;
      current = survivor;
      waitForNodes(1);
   }

   private static void waitForNodes(int count) throws InterruptedException {
      long end = System.currentTimeMillis() + 30000L;

      while(survivor.getClusterNodeIds().size() != count && System.currentTimeMillis() < end) {
         Thread.sleep(50L);
      }

      assertEquals(count, survivor.getClusterNodeIds().size());
   }

   private static void waitForSweep(File file, String threadName) throws InterruptedException {
      // the sweeps run in a background thread
      long end = System.currentTimeMillis() + 30000L;

      while(file.exists() && System.currentTimeMillis() < end) {
         Thread.sleep(50L);
      }

      for(Thread thread : Thread.getAllStackTraces().keySet()) {
         if(threadName.equals(thread.getName())) {
            thread.join(30000L);
            assertFalse(thread.isAlive(), "sweep thread did not finish");
         }
      }

      // a removal of stale counts that is still running holds the lock
      Lock lock = survivor.getLock(SnapshotEmbeddedTableAssembly.FILE_REFERENCES_MAP_LOCK);
      lock.lock();
      lock.unlock();
   }

   private static Integer survivorCount(File file) {
      return survivor.<String, Integer>getMap(SnapshotEmbeddedTableAssembly.FILE_REFERENCES_MAP)
         .get(file.getAbsolutePath());
   }

   private static Map<String, ?> survivorOwners() {
      return survivor.getMap(SnapshotEmbeddedTableAssembly.FILE_OWNERS_MAP);
   }

   /**
    * Starts a node from the production configuration with discovery and communication pinned to
    * loopback ports, as RuntimeQueryServiceQuotedSqlClusterTest does.
    */
   private static IgniteCluster startNode(String name, int discoPort) throws Exception {
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(List.of("127.0.0.1:" + survivorPort, "127.0.0.1:" + discoPort));
      TcpDiscoverySpi disco = new TcpDiscoverySpi();
      disco.setLocalAddress("127.0.0.1");
      disco.setLocalPort(discoPort);
      disco.setLocalPortRange(0);
      disco.setIpFinder(ipFinder);
      TcpCommunicationSpi comm = new TcpCommunicationSpi();
      comm.setLocalAddress("127.0.0.1");
      comm.setLocalPort(freePort());
      comm.setLocalPortRange(0);

      String instance = name + "-" + UUID.randomUUID();
      IgniteConfiguration config = IgniteCluster.getDefaultConfig(clusterDir.resolve(instance));
      config.setIgniteInstanceName(instance);
      config.setLocalHost("127.0.0.1");
      config.setDiscoverySpi(disco);
      config.setCommunicationSpi(comm);
      return IgniteClusterTestUtils.getIgniteCluster(config);
   }

   private static int freePort() throws Exception {
      try(ServerSocket socket = new ServerSocket(0)) {
         return socket.getLocalPort();
      }
   }

   private static Path clusterDir;
   private static int survivorPort;
   private static IgniteCluster survivor;
   private IgniteCluster stopping;
   // the Ignite node that the snapshot maps and the local node id are read from
   private static volatile IgniteCluster current;
   private final List<File> created = new ArrayList<>();
   private final List<XSwappableTable> tables = new ArrayList<>();
   private final List<Object> references = new ArrayList<>();

   @Configuration
   static class TestClusterConfiguration {
      // replaces the cluster of BaseTestConfiguration with one whose snapshot maps and locks,
      // local node id and node ids are of the current Ignite node
      @Bean
      public Cluster cluster() {
         return new MockCluster() {
            @Override
            public <K, V> DistributedMap<K, V> getMap(String name) {
               IgniteCluster node = current;
               return node != null && name.startsWith(SNAPSHOT_PREFIX) ?
                  node.getMap(name) : super.getMap(name);
            }

            @Override
            public Lock getLock(String name) {
               IgniteCluster node = current;
               return node != null && name.startsWith(SNAPSHOT_PREFIX) ?
                  node.getLock(name) : super.getLock(name);
            }

            @Override
            public String getLocalNodeId() {
               IgniteCluster node = current;
               return node != null ? node.getLocalNodeId() : super.getLocalNodeId();
            }

            @Override
            public Set<String> getClusterNodeIds() {
               IgniteCluster node = current;
               return node != null ? node.getClusterNodeIds() : super.getClusterNodeIds();
            }
         };
      }

      private static final String SNAPSHOT_PREFIX = "inetsoft.snapshot.";
   }
}
