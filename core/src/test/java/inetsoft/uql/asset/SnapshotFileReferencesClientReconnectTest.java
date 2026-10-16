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
 * Bug #78082, a client node (e.g. a cloud runner job) gets a new node id when it reconnects to
 * the cluster, so the snapshot counts it added before look like the counts of a node that is
 * gone while its tables are still open, and any node may remove them. The copy must not be
 * deleted while a table of the client still reads it, and must still be deleted when the last
 * one is closed.
 *
 * A real Ignite server and a real client-mode Ignite node with the product configuration, the
 * reconnect is forced by the server failing the client.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
   BaseTestConfiguration.class, SwapperTestConfiguration.class,
   SnapshotFileReferencesClientReconnectTest.TestClusterConfiguration.class
}, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SnapshotFileReferencesClientReconnectTest {
   @BeforeAll
   static void startServer() throws Exception {
      clusterDir = Files.createTempDirectory("cluster-78082c");
      serverPort = freePort();
      server = startNode("c78082-server", serverPort, false);
   }

   @AfterAll
   static void stopServer() throws Exception {
      current = null;

      if(server != null) {
         server.close();
      }

      if(clusterDir != null) {
         try(var paths = Files.walk(clusterDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
         }
      }
   }

   @BeforeEach
   void startClient() throws Exception {
      client = startNode("c78082-client", freePort(), true);
      waitForNodes(2);
   }

   @AfterEach
   void cleanUp() throws Exception {
      current = server;

      // the tables of a test that failed are still open
      for(Object reference : new ArrayList<>(references)) {
         close(reference);
      }

      if(client != null) {
         client.close();
         client = null;
      }

      Map<String, Integer> map = server.getMap(SnapshotEmbeddedTableAssembly.FILE_REFERENCES_MAP);
      Map<String, ?> owners = server.getMap(SnapshotEmbeddedTableAssembly.FILE_OWNERS_MAP);

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
   void sweepKeepsCopyOfTableOpenedBeforeReconnect() throws Exception {
      File file = createCopy("t78082r_1_s.tdat");
      current = client;
      Object reference = addReference(file);
      reconnectClient(client.getLocalNodeId());

      clearDataCache();
      assertTrue(file.exists(), "copy of a table still open on the reconnected client was deleted");

      close(reference);
      assertFalse(file.exists(), "copy was not deleted when its last table was closed");
      assertNull(count(file), "count was not removed when the last table was closed");
   }

   @Test
   void closeKeepsCopyOfTableOpenedBeforeReconnect() throws Exception {
      File file = createCopy("t78082s_1_s.tdat");
      current = client;
      Object first = addReference(file);
      reconnectClient(client.getLocalNodeId());
      // the same snapshot is opened again after the reconnect, and that table is closed
      Object second = addReference(file);
      assertEquals(2, count(file));
      close(second);
      assertTrue(file.exists(), "copy of a table still open on the reconnected client was deleted");

      close(first);
      assertFalse(file.exists(), "copy was not deleted when its last table was closed");
      assertNull(count(file), "count was not removed when the last table was closed");
   }

   @Test
   void copyIsKeptAfterAnotherNodeRemovedTheCountOfTheOldId() throws Exception {
      File file = createCopy("t78082t_1_s.tdat");
      current = client;
      Object first = addReference(file);
      String oldId = client.getLocalNodeId();
      reconnectClient(oldId);
      // a server removed the count of the old id, which is not in the cluster anymore
      removeCountAsOtherJvm(file, oldId);
      assertNull(count(file));

      Object second = addReference(file);
      close(second);
      assertTrue(file.exists(), "copy of a table still open on the reconnected client was deleted");

      clearDataCache();
      assertTrue(file.exists(), "copy of a table still open on the reconnected client was swept");

      close(first);
      assertFalse(file.exists(), "copy was not deleted when its last table was closed");
   }

   private static void clearDataCache() throws Exception {
      Method clearDataCache = ClearOldCacheFilesRunnable.class.getDeclaredMethod("clearDataCache");
      clearDataCache.setAccessible(true);
      clearDataCache.invoke(new ClearOldCacheFilesRunnable());
   }

   /**
    * Removes the count of a node from a file, as removeStaleFileReferences() does on a JVM that
    * doesn't read the file.
    */
   private static void removeCountAsOtherJvm(File file, String nodeId) {
      String path = file.getAbsolutePath();
      Lock lock = server.getLock(SnapshotEmbeddedTableAssembly.FILE_REFERENCES_MAP_LOCK);
      lock.lock();

      try {
         Map<String, Integer> map = server.getMap(SnapshotEmbeddedTableAssembly.FILE_REFERENCES_MAP);
         Map<String, HashMap<String, Integer>> owners =
            server.getMap(SnapshotEmbeddedTableAssembly.FILE_OWNERS_MAP);
         HashMap<String, Integer> fileOwners = owners.get(path);
         int dead = fileOwners.remove(nodeId);
         int total = map.get(path) - dead;

         if(fileOwners.isEmpty()) {
            owners.remove(path);
         }
         else {
            owners.put(path, fileOwners);
         }

         if(total <= 0) {
            map.remove(path);
         }
         else {
            map.put(path, total);
         }
      }
      finally {
         lock.unlock();
      }
   }

   private void reconnectClient(String oldId) throws Exception {
      serverSpi.failNode(UUID.fromString(oldId), null);
      long end = System.currentTimeMillis() + 60000L;

      while(System.currentTimeMillis() < end) {
         try {
            String id = client.getLocalNodeId();

            if(!id.equals(oldId) && server.getClusterNodeIds().contains(id) &&
               client.getClusterNodeIds().size() == 2)
            {
               // the caches of the client work again
               client.getMap(SnapshotEmbeddedTableAssembly.FILE_REFERENCES_MAP).size();
               return;
            }
         }
         catch(Exception ignore) {
         }

         Thread.sleep(100L);
      }

      fail("client did not reconnect with a new id");
   }

   private File createCopy(String name) throws Exception {
      File file = FileSystemService.getInstance().getCacheFile(name);
      Files.write(file.toPath(), new byte[] { 1, 2, 3 });
      // older than any age gate of the sweeps, as a copy whose content matched an old file
      assertTrue(file.setLastModified(System.currentTimeMillis() - 24 * 3600000L));
      created.add(file);
      return file;
   }

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

   private void close(Object reference) throws Exception {
      int index = references.indexOf(reference);
      references.remove(index);
      tables.remove(index);
      ((Cleaner.Reference<?>) reference).close();
   }

   private static void waitForNodes(int count) throws InterruptedException {
      long end = System.currentTimeMillis() + 30000L;

      while(server.getClusterNodeIds().size() != count && System.currentTimeMillis() < end) {
         Thread.sleep(50L);
      }

      assertEquals(count, server.getClusterNodeIds().size());
   }

   private static Integer count(File file) {
      return server.<String, Integer>getMap(SnapshotEmbeddedTableAssembly.FILE_REFERENCES_MAP)
         .get(file.getAbsolutePath());
   }

   private static IgniteCluster startNode(String name, int discoPort, boolean clientMode)
      throws Exception
   {
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(List.of("127.0.0.1:" + serverPort));
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
      config.setClientMode(clientMode);
      config.setDiscoverySpi(disco);
      config.setCommunicationSpi(comm);

      if(!clientMode) {
         serverSpi = disco;
      }

      return IgniteClusterTestUtils.getIgniteCluster(config);
   }

   private static int freePort() throws Exception {
      try(ServerSocket socket = new ServerSocket(0)) {
         return socket.getLocalPort();
      }
   }

   private static Path clusterDir;
   private static int serverPort;
   private static IgniteCluster server;
   private static TcpDiscoverySpi serverSpi;
   private IgniteCluster client;
   // the Ignite node that the snapshot maps and the local node id are read from
   private static volatile IgniteCluster current;
   private final List<File> created = new ArrayList<>();
   private final List<XSwappableTable> tables = new ArrayList<>();
   private final List<Object> references = new ArrayList<>();

   @Configuration
   static class TestClusterConfiguration {
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
