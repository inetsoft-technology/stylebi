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

package inetsoft.uql.asset;

import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.internal.cluster.ignite.IgniteCluster;
import inetsoft.sree.internal.cluster.ignite.IgniteClusterTestUtils;
import inetsoft.test.*;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.util.Cleaner;
import inetsoft.util.FileSystemService;
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

import javax.cache.CacheException;
import java.io.File;
import java.lang.ref.Reference;
import java.lang.reflect.*;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78096, EmbeddedTableReference added the counts of its files to the cluster maps and to the
 * local count, and nothing removed the counts it had added when a later step failed, as the
 * object that could remove them was never created. The copies were then never deleted. A
 * failure between the total count and the owner of a file left a total that the removal of
 * stale counts never removes, and a thread interrupted while the counts were added kept the
 * cluster lock, as IgniteLock.unlock() threw on it. The counts a reference added must be removed
 * exactly once, whatever step failed, and the lock must be released.
 *
 * The maps and the lock are of a real Ignite node with the product configuration. A failure of
 * a map is injected by a proxy around the real map of the node, either as the exception that
 * IgniteDistributedMap throws when its retries are used up, or by only interrupting the thread
 * so that Ignite itself fails.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
   BaseTestConfiguration.class, SwapperTestConfiguration.class,
   SnapshotFileReferencesPartialRegistrationTest.TestClusterConfiguration.class
}, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SnapshotFileReferencesPartialRegistrationTest {
   @BeforeAll
   static void startNode() throws Exception {
      clusterDir = Files.createTempDirectory("cluster-78096");
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      int discoPort = freePort();
      ipFinder.setAddresses(List.of("127.0.0.1:" + discoPort));
      TcpDiscoverySpi disco = new TcpDiscoverySpi();
      disco.setLocalAddress("127.0.0.1");
      disco.setLocalPort(discoPort);
      disco.setLocalPortRange(0);
      disco.setIpFinder(ipFinder);
      TcpCommunicationSpi comm = new TcpCommunicationSpi();
      comm.setLocalAddress("127.0.0.1");
      comm.setLocalPort(freePort());
      comm.setLocalPortRange(0);

      String instance = "s78096-" + UUID.randomUUID();
      IgniteConfiguration config = IgniteCluster.getDefaultConfig(clusterDir.resolve(instance));
      config.setIgniteInstanceName(instance);
      config.setLocalHost("127.0.0.1");
      config.setDiscoverySpi(disco);
      config.setCommunicationSpi(comm);
      node = IgniteClusterTestUtils.getIgniteCluster(config);
   }

   @AfterAll
   static void stopNode() throws Exception {
      if(node != null) {
         node.close();
         node = null;
      }

      if(clusterDir != null) {
         try(var paths = Files.walk(clusterDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
         }
      }
   }

   @AfterEach
   void cleanUp() throws Exception {
      failMap = null;
      failKey = null;
      Thread.interrupted();
      Map<String, Integer> map = node.getMap(SnapshotEmbeddedTableAssembly.FILE_REFERENCES_MAP);
      Map<String, ?> owners = node.getMap(SnapshotEmbeddedTableAssembly.FILE_OWNERS_MAP);

      for(File file : created) {
         map.remove(file.getAbsolutePath());
         owners.remove(file.getAbsolutePath());
         Files.deleteIfExists(file.toPath());
      }

      created.clear();
   }

   @Test
   void countsAddedBeforeAFailedTotalAreRemovedWhenTheTableIsCollected() throws Exception {
      File file1 = createCopy("t78096a_1_s.tdat");
      File file2 = createCopy("t78096a_2_s.tdat");
      failOn(SnapshotEmbeddedTableAssembly.FILE_REFERENCES_MAP, file2, false);

      assertThrows(CacheException.class, () -> addDroppedTable(file1, file2));
      assertEquals(1, count(file1));
      assertNotNull(owners(file1));
      assertNull(count(file2));
      assertFalse(isInUseLocally(file1), "local count of a failed registration was kept");
      assertFalse(isInUseLocally(file2), "local count of a failed registration was kept");

      failKey = null;
      waitForCleaner(file1);

      assertNull(count(file1), "count of a failed registration was not removed");
      assertNull(owners(file1), "owner of a failed registration was not removed");
      assertFalse(file1.exists(), "copy of a failed registration was not deleted");
   }

   @Test
   void totalAddedWithoutItsOwnerIsRemovedWhenTheTableIsCollected() throws Exception {
      File file = createCopy("t78096b_1_s.tdat");
      failOn(SnapshotEmbeddedTableAssembly.FILE_OWNERS_MAP, file, false);

      assertThrows(CacheException.class, () -> addDroppedTable(file));
      // the removal of stale counts never removes a total without an owner
      assertEquals(1, count(file));
      assertNull(owners(file));

      failKey = null;
      waitForCleaner(file);

      assertNull(count(file), "total without an owner was not removed");
      assertFalse(file.exists(), "copy of a failed registration was not deleted");
   }

   @Test
   void registrationOnInterruptedThreadReleasesTheLockAndAddsEveryCount() throws Exception {
      File file1 = createCopy("t78096c_1_s.tdat");
      File file2 = createCopy("t78096c_2_s.tdat");
      // the interrupt is the only failure, any exception is thrown by Ignite
      failOn(SnapshotEmbeddedTableAssembly.FILE_REFERENCES_MAP, file1, true);
      XSwappableTable table = new XSwappableTable();
      Object reference = newReference(table, file1, file2);
      failKey = null;

      assertTrue(Thread.interrupted(), "interrupt was lost");
      assertTrue(isLockFreeOnOtherThread(), "snapshot lock is still held after the registration");
      assertEquals(1, count(file1));
      assertEquals(1, count(file2));
      assertTrue(isInUseLocally(file1));
      assertTrue(isInUseLocally(file2));

      close(reference);
      Reference.reachabilityFence(table);

      assertNull(count(file1), "count was left after the last table closed");
      assertNull(count(file2), "count was left after the last table closed");
      assertFalse(isInUseLocally(file1));
      assertFalse(file1.exists(), "copy was not deleted when its last table was closed");
      assertFalse(file2.exists(), "copy was not deleted when its last table was closed");
   }

   @Test
   void secondCloseDoesNotRemoveTheCountOfAnotherTable() throws Exception {
      File file = createCopy("t78096d_1_s.tdat");
      XSwappableTable table1 = new XSwappableTable();
      XSwappableTable table2 = new XSwappableTable();
      Object first = newReference(table1, file);
      Object second = newReference(table2, file);

      // e.g. a close by a caller and then by the cleaner when the table is collected
      close(first);
      close(first);

      assertEquals(1, count(file), "a second close removed the count of another table");
      assertTrue(isInUseLocally(file), "a second close removed the local count of another table");
      assertTrue(file.exists(), "copy was deleted while a table still read it");

      close(second);
      Reference.reachabilityFence(table1);
      Reference.reachabilityFence(table2);

      assertNull(count(file));
      assertFalse(isInUseLocally(file));
      assertFalse(file.exists(), "copy was not deleted when its last table was closed");
   }

   private File createCopy(String name) throws Exception {
      File file = FileSystemService.getInstance().getCacheFile(name);
      Files.write(file.toPath(), new byte[] { 1, 2, 3 });
      created.add(file);
      return file;
   }

   private static void failOn(String map, File file, boolean interrupt) {
      failInterrupt = interrupt;
      failMap = map;
      failKey = file.getAbsolutePath();
   }

   // in a method of its own, so that nothing on the test's stack keeps the table reachable
   private static void addDroppedTable(File... files) throws Exception {
      newReference(new XSwappableTable(), files);
   }

   /**
    * Adds a reference of a table to copies, as SnapshotEmbeddedTableAssembly.getTable() does
    * when it copies the data to the cache.
    */
   private static Object newReference(XSwappableTable table, File... files) throws Exception {
      Class<?> cls = Class.forName(
         SnapshotEmbeddedTableAssembly.class.getName() + "$EmbeddedTableReference");
      Constructor<?> cons = cls.getDeclaredConstructor(XSwappableTable.class, File[].class);
      cons.setAccessible(true);

      try {
         return cons.newInstance(table, files);
      }
      catch(InvocationTargetException e) {
         throw (Exception) e.getCause();
      }
   }

   private static void close(Object reference) throws Exception {
      ((Cleaner.Reference<?>) reference).close();
   }

   private static void waitForCleaner(File file) throws InterruptedException {
      // the cleaner thread closes the reference once the table is phantom reachable
      long end = System.currentTimeMillis() + 30000L;

      while((count(file) != null || file.exists()) && System.currentTimeMillis() < end) {
         System.gc();
         Thread.sleep(50L);
      }
   }

   private static boolean isLockFreeOnOtherThread() throws Exception {
      FutureTask<Boolean> task = new FutureTask<>(() -> {
         Lock lock = node.getLock(SnapshotEmbeddedTableAssembly.FILE_REFERENCES_MAP_LOCK);

         if(lock.tryLock(5, TimeUnit.SECONDS)) {
            lock.unlock();
            return true;
         }

         return false;
      });

      Thread thread = new Thread(task, "t78096-other");
      thread.start();
      return task.get(30, TimeUnit.SECONDS);
   }

   private static boolean isInUseLocally(File file) {
      return SnapshotEmbeddedTableAssembly.isFileInUseLocally(file.getAbsolutePath());
   }

   private static Integer count(File file) {
      return node.<String, Integer>getMap(SnapshotEmbeddedTableAssembly.FILE_REFERENCES_MAP)
         .get(file.getAbsolutePath());
   }

   private static Object owners(File file) {
      return node.getMap(SnapshotEmbeddedTableAssembly.FILE_OWNERS_MAP)
         .get(file.getAbsolutePath());
   }

   private static int freePort() throws Exception {
      try(ServerSocket socket = new ServerSocket(0)) {
         return socket.getLocalPort();
      }
   }

   private static Path clusterDir;
   private static volatile IgniteCluster node;
   // the put of this key into this map fails, by an exception or by an interrupt
   private static volatile String failMap;
   private static volatile String failKey;
   private static volatile boolean failInterrupt;
   private final List<File> created = new ArrayList<>();

   @Configuration
   static class TestClusterConfiguration {
      // replaces the cluster of BaseTestConfiguration with one whose snapshot maps and lock and
      // local node id are of the Ignite node, and whose snapshot maps can fail
      @Bean
      public Cluster cluster() {
         return new MockCluster() {
            @Override
            public <K, V> DistributedMap<K, V> getMap(String name) {
               IgniteCluster ignite = node;
               return ignite != null && name.startsWith(SNAPSHOT_PREFIX) ?
                  failing(name, ignite.getMap(name)) : super.getMap(name);
            }

            @Override
            public Lock getLock(String name) {
               IgniteCluster ignite = node;
               return ignite != null && name.startsWith(SNAPSHOT_PREFIX) ?
                  ignite.getLock(name) : super.getLock(name);
            }

            @Override
            public String getLocalNodeId() {
               IgniteCluster ignite = node;
               return ignite != null ? ignite.getLocalNodeId() : super.getLocalNodeId();
            }

            @Override
            public Set<String> getClusterNodeIds() {
               IgniteCluster ignite = node;
               return ignite != null ? ignite.getClusterNodeIds() : super.getClusterNodeIds();
            }
         };
      }

      @SuppressWarnings("unchecked")
      private static <K, V> DistributedMap<K, V> failing(String name, DistributedMap<K, V> map) {
         return (DistributedMap<K, V>) Proxy.newProxyInstance(
            DistributedMap.class.getClassLoader(), new Class<?>[] { DistributedMap.class },
            (proxy, method, args) -> {
               if("put".equals(method.getName()) && name.equals(failMap) &&
                  args[0].equals(failKey))
               {
                  if(failInterrupt) {
                     Thread.currentThread().interrupt();
                  }
                  else {
                     throw new CacheException("injected: client disconnected");
                  }
               }

               try {
                  return method.invoke(map, args);
               }
               catch(InvocationTargetException e) {
                  throw e.getCause();
               }
            });
      }

      private static final String SNAPSHOT_PREFIX = "inetsoft.snapshot.";
   }
}
