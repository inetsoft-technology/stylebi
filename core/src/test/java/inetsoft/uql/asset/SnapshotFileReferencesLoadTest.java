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
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.table.SnapshotEmbeddedTableDataCache;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.Cleaner;
import inetsoft.util.FileSystemService;
import inetsoft.util.swap.XSwapper;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.lang.ref.Reference;
import java.lang.reflect.Constructor;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78095, a snapshot copy in the cache directory was deleted while a live table read it. The
 * startup sweep of XSwapper deleted every copy older than its grace period whatever its count,
 * a load that kept an existing copy with equal contents left its old modification time, and the
 * count of a load was added only after the copy was kept or copied, so the close of the previous
 * last table of the copy deleted it while it was loaded. The table then read nulls in place of
 * the rows, and was cached. A copy must be counted before it is kept or copied, every deleter
 * must keep a counted copy, and a load whose copy can't be read must fail and not be cached.
 *
 * The tables are loaded by the real SnapshotEmbeddedTableAssembly.getTable() from a stored
 * worksheet, as in SnapshotSaveDataFailureTest. The snapshot maps and lock are of a real Ignite
 * node with the product configuration. The deleter in the copy window runs at a fixed point of
 * the load: when the load removes the temporary copy whose contents equal the kept copy.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
   BaseTestConfiguration.class, SwapperTestConfiguration.class,
   SnapshotFileReferencesLoadTest.TestConfiguration.class
}, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SnapshotFileReferencesLoadTest {
   @BeforeAll
   static void startNode() throws Exception {
      clusterDir = Files.createTempDirectory("cluster-78095");
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

      String instance = "s78095-" + UUID.randomUUID();
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
      onKeep = null;
      Thread.interrupted();
      SnapshotEmbeddedTableDataCache.getInstance().clear();

      for(Object reference : references) {
         ((Cleaner.Reference<?>) reference).close();
      }

      references.clear();
      tables.clear();

      // the cleaner removes the counts of the loaded tables once they are collected, so that
      // it does not do it after the node or the context of this class is gone
      long end = System.currentTimeMillis() + 30000L;

      while(!copies.stream().allMatch(f -> count(f) == null && !isInUseLocally(f)) &&
         System.currentTimeMillis() < end)
      {
         System.gc();
         Thread.sleep(50L);
      }

      for(File file : copies) {
         assertNull(count(file), "count of a collected table was not removed: " + file);
         assertFalse(isInUseLocally(file), "local count of a collected table was not removed");
         Files.deleteIfExists(file.toPath());
      }

      copies.clear();
   }

   // 1a and 1b: another JVM on the same cache directory (e.g. the scheduler JVM of the server)
   // starts its swapper while this one holds a table that reloaded an old copy
   @Test
   void startupSweepKeepsCopyOfReloadedTable() throws Exception {
      String xml = save("a");
      XSwappableTable first = load(xml);
      File copy = copyOf(xml);
      assertTrue(copy.exists());
      assertEquals(1, count(copy));
      // the first table read its rows long ago
      assertTrue(copy.setLastModified(System.currentTimeMillis() - 24 * 3600000L));

      // e.g. a table of a worksheet whose data cache entry was collected
      SnapshotEmbeddedTableDataCache.getInstance().clear();
      XSwappableTable reloaded = load(xml);
      assertNotSame(first, reloaded);
      assertEquals(2, count(copy));

      runStartupSweepOfOtherJvm();

      assertTrue(copy.exists(), "copy of live tables deleted by the startup sweep");
      assertEquals("a4", reloaded.getObject(5, 1));
      assertEquals("a49", reloaded.getObject(50, 1));
      assertEquals("a4", first.getObject(5, 1));
      Reference.reachabilityFence(first);
   }

   // 1b race: the previous last table of the copy is closed while the copy is loaded again
   @Test
   void closeOfPreviousTableWhileCopyIsLoadedKeepsCopy() throws Exception {
      String xml = save("b");
      File copy = copyOf(xml);
      writeCopy(xml, copy);
      Object previous = addReference(copy);
      assertEquals(1, count(copy));

      onKeep = file -> {
         try {
            ((Cleaner.Reference<?>) previous).close();
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      };
      XSwappableTable table = load(xml);
      assertNull(onKeep, "the copy was not kept by the load");

      assertTrue(copy.exists(), "copy deleted by the close of the previous table while loaded");
      assertEquals(1, count(copy));
      assertNotNull(table);
      assertEquals("b0", table.getObject(1, 1));
      assertEquals("b4", table.getObject(5, 1));
      assertEquals("b49", table.getObject(50, 1));
   }

   // a deleter that does not read the counts (a JVM of an older version, a tmp cleaner) deletes
   // the copy while it is loaded
   @Test
   void copyDeletedWhileLoadedFailsTheLoad() throws Exception {
      String xml = save("c");
      File copy = copyOf(xml);
      writeCopy(xml, copy);
      onKeep = file -> assertTrue(copy.delete());

      SnapshotEmbeddedTableAssembly assembly = parse(xml);
      assertNull(assembly.getTable(), "a table whose copy is gone was loaded");
      assertNull(onKeep, "the copy was not kept by the load");
      assertNull(SnapshotEmbeddedTableDataCache.getInstance().get(cacheKey(assembly)),
                 "a table whose copy is gone was cached");

      // the next load copies it again
      XSwappableTable table = load(xml);
      assertTrue(copy.exists());
      assertEquals("c4", table.getObject(5, 1));
   }

   // the footer of the copy can't be read, as on a thread that is interrupted while it loads
   @Test
   void footerNotReadFailsTheLoad() throws Exception {
      String xml = save("d");
      File copy = copyOf(xml);
      writeCopy(xml, copy);
      onKeep = file -> Thread.currentThread().interrupt();

      SnapshotEmbeddedTableAssembly assembly = parse(xml);
      XSwappableTable table;

      try {
         table = assembly.getTable();
      }
      finally {
         Thread.interrupted();
      }

      assertNull(onKeep, "the copy was not kept by the load");
      assertTrue(copy.exists());
      assertNull(table, "a table whose copy could not be read was loaded");
      assertNull(SnapshotEmbeddedTableDataCache.getInstance().get(cacheKey(assembly)),
                 "a table whose copy could not be read was cached");

      table = load(xml);
      assertEquals("d4", table.getObject(5, 1));
   }

   // the stored data of a table is gone (bug #78029): the table still loads with null rows and
   // is not cached. The copy is counted before the copy loop finds its data missing, cleanUp
   // checks that no count is left once the table is collected
   @Test
   void tableWithoutStoredDataLoadsIncomplete() throws Exception {
      String xml = save("f");
      File copy = copyOf(xml);
      SnapshotEmbeddedTableAssembly assembly = parse(xml);
      EmbeddedTableStorage.getInstance().removeTable(assembly.getDataPaths()[0] + "_s.tdat");

      XSwappableTable table = assembly.getTable();

      assertNotNull(table, "a table without stored data was not loaded");
      assertFalse(copy.exists());
      assertNull(SnapshotEmbeddedTableDataCache.getInstance().get(cacheKey(assembly)),
                 "a table without stored data was cached");
   }

   /**
    * Runs the startup sweep of the swapper of another JVM, which has a seed of its own.
    */
   private void runStartupSweepOfOtherJvm() throws Exception {
      // a file of no one past the grace period, its delete shows that the sweep ran
      File signal = FileSystemService.getInstance().getCacheFile("s78095sweep_1.tdat");
      Files.write(signal.toPath(), new byte[] { 1 });
      assertTrue(signal.setLastModified(
         System.currentTimeMillis() - XSwapper.SWAP_FILE_GRACE_PERIOD - 1000L));
      XSwapper swapper = new XSwapper();

      try {
         long end = System.currentTimeMillis() + 30000L;

         while(signal.exists() && System.currentTimeMillis() < end) {
            Thread.sleep(50L);
         }

         for(Thread thread : Thread.getAllStackTraces().keySet()) {
            if(XSwapper.CACHE_SWEEP_THREAD.equals(thread.getName())) {
               thread.join(30000L);
               assertFalse(thread.isAlive(), "sweep thread did not finish");
            }
         }

         assertFalse(signal.exists(), "the startup sweep did not run");
      }
      finally {
         swapper.stop();
         Files.deleteIfExists(signal.toPath());
      }
   }

   /**
    * Saves a worksheet with a snapshot table of 50 rows, as WorksheetEngine.setSheet does.
    *
    * @return the stored XML.
    */
   private String save(String tag) throws Exception {
      XSwappableTable data = new XSwappableTable(2, false);
      data.addRow(new Object[] { "a", "b" });

      for(int i = 0; i < 50; i++) {
         data.addRow(new Object[] { i, tag + i });
      }

      data.complete();
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly assembly = new SnapshotEmbeddedTableAssembly(ws, NAME);
      ws.addAssembly(assembly);
      assembly.setEmbeddedData(new XEmbeddedTable(data));
      boolean saved = false;

      try {
         SnapshotEmbeddedTableAssembly.writeDataFilesForSave(ws);
         StringWriter buf = new StringWriter();
         PrintWriter writer = new PrintWriter(buf);
         ws.writeXML(writer);
         writer.flush();
         saved = true;
         String xml = buf.toString();
         copies.add(copyOf(xml));
         return xml;
      }
      finally {
         SnapshotEmbeddedTableAssembly.finishSave(ws, saved);
         data.dispose();
      }
   }

   private static SnapshotEmbeddedTableAssembly parse(String xml) throws Exception {
      Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
      Worksheet ws = new Worksheet();
      ws.parseXML(root);
      return (SnapshotEmbeddedTableAssembly) ws.getAssembly(NAME);
   }

   /**
    * Loads the table of a stored worksheet, as opening the worksheet in a session does.
    */
   private XSwappableTable load(String xml) throws Exception {
      XSwappableTable table = parse(xml).getTable();
      assertNotNull(table, "table not loaded");
      table.moreRows(XTable.EOT);
      tables.add(table);
      return table;
   }

   /**
    * Gets the copy in the cache directory of the data file of a stored worksheet.
    */
   private static File copyOf(String xml) throws Exception {
      String[] paths = parse(xml).getDataPaths();
      assertEquals(1, paths.length);
      return FileSystemService.getInstance().getCacheFile(paths[0] + "_s.tdat");
   }

   /**
    * Writes the copy of the data file, as a load of the table did before.
    */
   private static void writeCopy(String xml, File copy) throws Exception {
      String path = parse(xml).getDataPaths()[0] + "_s.tdat";

      try(InputStream in = EmbeddedTableStorage.getInstance().readTable(path)) {
         Files.copy(in, copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }

      assertTrue(copy.setLastModified(System.currentTimeMillis() - 24 * 3600000L));
   }

   /**
    * Adds a reference of a live table to a copy, as a load of the table does.
    */
   private Object addReference(File file) throws Exception {
      Class<?> cls = Class.forName(
         SnapshotEmbeddedTableAssembly.class.getName() + "$EmbeddedTableReference");
      Constructor<?> cons = cls.getDeclaredConstructor(XSwappableTable.class, File[].class);
      cons.setAccessible(true);
      XSwappableTable table = new XSwappableTable();
      Object reference = cons.newInstance(table, new File[] { file });
      // keep the table reachable, so the cleaner does not close the reference
      tables.add(table);
      references.add(reference);
      return reference;
   }

   private static String cacheKey(SnapshotEmbeddedTableAssembly assembly) throws Exception {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      assembly.printEmbeddedDataKey(writer);
      writer.flush();
      return buf.toString();
   }

   private static boolean isInUseLocally(File file) {
      return SnapshotEmbeddedTableAssembly.isFileInUseLocally(file.getAbsolutePath());
   }

   private static Integer count(File file) {
      return node.<String, Integer>getMap(SnapshotEmbeddedTableAssembly.FILE_REFERENCES_MAP)
         .get(file.getAbsolutePath());
   }

   private static int freePort() throws Exception {
      try(ServerSocket socket = new ServerSocket(0)) {
         return socket.getLocalPort();
      }
   }

   private static Path clusterDir;
   private static volatile IgniteCluster node;
   // runs once when a load keeps the copy it found, before the table reads it
   private static volatile Consumer<File> onKeep;
   private final List<File> copies = new ArrayList<>();
   private final List<XSwappableTable> tables = new ArrayList<>();
   private final List<Object> references = new ArrayList<>();
   private static final String NAME = "T78095";

   @Configuration
   static class TestConfiguration {
      // replaces the cluster of BaseTestConfiguration with one whose snapshot maps and lock and
      // node ids are of the Ignite node
      @Bean
      public Cluster cluster() {
         return new MockCluster() {
            @Override
            public <K, V> DistributedMap<K, V> getMap(String name) {
               IgniteCluster ignite = node;
               return ignite != null && name.startsWith(SNAPSHOT_PREFIX) ?
                  ignite.getMap(name) : super.getMap(name);
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

      // the load removes its temporary copy when the copy it found has equal contents
      @Bean
      public FileSystemService fileSystemService(Cluster cluster,
                                                 ApplicationEventPublisher eventPublisher)
      {
         return new FileSystemService(cluster, eventPublisher) {
            @Override
            public void remove(File file, int period) {
               Consumer<File> hook = onKeep;

               if(hook != null && file.getName().endsWith("_s.tdat")) {
                  onKeep = null;
                  hook.accept(file);
               }

               super.remove(file, period);
            }
         };
      }

      @Bean
      public EmbeddedTableStorage embeddedTableStorage(BlobStorageManager manager) {
         return new EmbeddedTableStorage(manager);
      }

      private static final String SNAPSHOT_PREFIX = "inetsoft.snapshot.";
   }
}
