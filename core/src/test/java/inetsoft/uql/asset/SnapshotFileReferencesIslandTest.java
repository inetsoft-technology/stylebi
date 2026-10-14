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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.test.*;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78463, two JVMs on one cache directory (the server and the local scheduler) can both stay
 * alive after a pause longer than the failure detection timeout, as two clusters that never
 * merge. Each one then saw the other as gone, because it was not in its topology, and deleted the
 * snapshot copies that the other still read. An owner that is not in the topology is now gone
 * only if its lease in the cache directory is missing or has not changed for the lease timeout.
 *
 * The cluster of BaseTestConfiguration has this node only, so a count of another node is that of
 * a JVM in another cluster. Its lease is written as that JVM writes it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SnapshotFileReferencesIslandTest {
   @BeforeEach
   void setClock() {
      SnapshotOwnerLeases.clock = now::get;
   }

   @AfterEach
   void cleanUp() throws Exception {
      SnapshotOwnerLeases.clock = System::currentTimeMillis;

      for(Object reference : references) {
         close(reference);
      }

      Map<String, Integer> map = cluster().getMap(SnapshotEmbeddedTableAssembly.FILE_REFERENCES_MAP);
      Map<String, ?> owners = cluster().getMap(SnapshotEmbeddedTableAssembly.FILE_OWNERS_MAP);

      for(File file : created) {
         map.remove(file.getAbsolutePath());
         owners.remove(file.getAbsolutePath());
         Files.deleteIfExists(file.toPath());
      }

      created.clear();
      tables.clear();
      references.clear();
   }

   @Test
   void closeKeepsCopyOfOwnerInAnotherClusterWithLiveLease() throws Exception {
      File file = createCopy("t78463a_1_s.tdat");
      String peer = peer("a");
      addCountOfOtherJvm(file, peer);
      writeLease(peer, "1", file);

      close(addReference(file));

      assertTrue(file.exists(), "copy of a live JVM in another cluster was deleted");
      assertEquals(1, count(file), "count of a live JVM in another cluster was removed");
      assertTrue(owners(file).containsKey(peer));
   }

   @Test
   void sweepsKeepCopyOfOwnerInAnotherClusterWithLiveLease() throws Exception {
      File file = createCopy("t78463b_1_s.tdat");
      String peer = peer("b");
      addCountOfOtherJvm(file, peer);
      writeLease(peer, "1", file);

      SnapshotEmbeddedTableAssembly.removeStaleFileReferences(cluster());
      clearDataCache();

      assertTrue(file.exists(), "copy of a live JVM in another cluster was swept");
      assertEquals(1, count(file), "count of a live JVM in another cluster was removed");
   }

   @Test
   void copyIsDeletedWhenLeaseStopsChanging() throws Exception {
      File file = createCopy("t78463c_1_s.tdat");
      String peer = peer("c");
      addCountOfOtherJvm(file, peer);
      File lease = writeLease(peer, "1", file);
      // the counter is read once, then the owner stops writing it for longer than the timeout
      SnapshotEmbeddedTableAssembly.removeStaleFileReferences(cluster());
      assertEquals(1, count(file));
      now.addAndGet(SnapshotOwnerLeases.getTimeout() + 1);

      close(addReference(file));

      assertFalse(file.exists(), "copy was not deleted after the lease of its owner expired");
      assertNull(count(file), "count of an owner with an expired lease was not removed");
      assertFalse(lease.exists(), "expired lease was not deleted");
   }

   @Test
   void leaseThatChangesKeepsOwnerAlivePastTheTimeout() throws Exception {
      File file = createCopy("t78463d_1_s.tdat");
      String peer = peer("d");
      addCountOfOtherJvm(file, peer);
      writeLease(peer, "1", file);
      SnapshotEmbeddedTableAssembly.removeStaleFileReferences(cluster());
      now.addAndGet(SnapshotOwnerLeases.getTimeout() + 1);
      writeLease(peer, "2", file);

      close(addReference(file));

      assertTrue(file.exists(), "copy of an owner that refreshed its lease was deleted");
      assertEquals(1, count(file));
   }

   // a JVM of an older version writes no lease, its counts are judged by the topology alone
   @Test
   void copyOfOwnerWithoutLeaseIsDeletedAsBefore() throws Exception {
      File file = createCopy("t78463e_1_s.tdat");
      addCountOfOtherJvm(file, peer("e"));

      close(addReference(file));

      assertFalse(file.exists(), "copy of an owner without a lease was not deleted");
      assertNull(count(file));
   }

   @Test
   void cacheCleanUpKeepsLeases() throws Exception {
      String peer = peer("f");
      File lease = writeLease(peer, "1");
      // the sweep is done when this is deleted
      File copy = createCopy("t78463f_1_s.tdat");

      FileSystemService.getInstance().clearCacheFiles(null);
      waitForSweep(copy, FileSystemService.CLEAR_CACHE_FILES_THREAD);

      assertFalse(copy.exists());
      assertTrue(lease.exists(), "the cache clean-up deleted a lease");
   }

   // the node id of a client changes when it reconnects, the tables opened before are still
   // read under the old id
   @Test
   void leasesOfEveryLocalNodeIdAreKeptWhileTheirTablesAreOpen() throws Exception {
      File dir = FileSystemService.getInstance().getCacheFolder();
      String oldId = peer("g-old");
      String newId = peer("g-new");
      SnapshotOwnerLeases.acquire(dir, oldId);
      SnapshotOwnerLeases.acquire(dir, newId);
      File copy = new File(dir, "t78463g_1_s.tdat");

      try {
         assertTrue(SnapshotOwnerLeases.getLeaseFile(dir, oldId).exists());
         assertTrue(SnapshotOwnerLeases.getLeaseFile(dir, newId).exists());
         now.addAndGet(SnapshotOwnerLeases.getTimeout() + 1);
         assertTrue(SnapshotOwnerLeases.isAlive(copy.getAbsolutePath(), oldId));
         assertTrue(SnapshotOwnerLeases.isAlive(copy.getAbsolutePath(), newId));
      }
      finally {
         SnapshotOwnerLeases.release(dir, oldId);
         SnapshotOwnerLeases.release(dir, newId);
      }

      assertFalse(SnapshotOwnerLeases.getLeaseFile(dir, oldId).exists());
      assertFalse(SnapshotOwnerLeases.getLeaseFile(dir, newId).exists());
   }

   @Test
   void tableHoldsTheLeaseOfItsNodeUntilClosed() throws Exception {
      File file = createCopy("t78463h_1_s.tdat");
      File lease = SnapshotOwnerLeases.getLeaseFile(
         file.getParentFile(), cluster().getLocalNodeId());
      Object reference = addReference(file);
      assertTrue(lease.exists(), "no lease while a table is open");
      assertTrue(Files.readAllLines(lease.toPath()).contains(file.getName()),
                 "the lease doesn't list the copy that the table reads");

      close(reference);
      assertFalse(lease.exists(), "lease was not deleted when the last table was closed");
   }

   // a JVM in another cluster that opened the copy after the split has no count of it here
   @Test
   void closeKeepsCopyThatOnlyAJvmInAnotherClusterCounted() throws Exception {
      File file = createCopy("t78463j_1_s.tdat");
      writeLease(peer("j"), "1", file);

      close(addReference(file));

      assertTrue(file.exists(), "copy read by a live JVM in another cluster was deleted");
      assertNull(count(file));
   }

   @Test
   void sweepsKeepCopyThatOnlyAJvmInAnotherClusterCounted() throws Exception {
      File file = createCopy("t78463k_1_s.tdat");
      writeLease(peer("k"), "1", file);
      // the clean-up is done when this is deleted
      File sentinel = createCopy("t78463k_2_s.tdat");

      clearDataCache();
      assertTrue(file.exists(), "the data sweep deleted a copy read in another cluster");

      FileSystemService.getInstance().clearCacheFiles(null);
      waitForSweep(sentinel, FileSystemService.CLEAR_CACHE_FILES_THREAD);
      assertFalse(sentinel.exists());
      assertTrue(file.exists(), "the cache clean-up deleted a copy read in another cluster");
   }

   // the JVM in another cluster closed its tables of the copy, its count there is stale
   @Test
   void countOfLiveOwnerThatNoLongerReadsTheCopyIsRemoved() throws Exception {
      File file = createCopy("t78463l_1_s.tdat");
      File other = createCopy("t78463l_2_s.tdat");
      String peer = peer("l");
      addCountOfOtherJvm(file, peer);
      File lease = writeLease(peer, "1", other);

      close(addReference(file));

      assertFalse(file.exists(), "copy that no live table reads was not deleted");
      assertNull(count(file));
      assertTrue(lease.exists(), "the lease of a live JVM was deleted");
      assertTrue(other.exists());
   }

   @Test
   void leaseThatStopsChangingNoLongerKeepsItsCopies() throws Exception {
      File file = createCopy("t78463m_1_s.tdat");
      // the data sweep deletes the oldest file first
      assertTrue(file.setLastModified(1000L));
      writeLease(peer("m"), "1", file);
      clearDataCache();
      assertTrue(file.exists());
      now.addAndGet(SnapshotOwnerLeases.getTimeout() + 1);

      clearDataCache();

      assertFalse(file.exists(), "copy of a JVM whose lease expired was not swept");
   }

   @Test
   void orphanLeasesAreDeletedAfterADay() throws Exception {
      File old = writeLease(peer("i-old"), "1");
      File recent = writeLease(peer("i-recent"), "1");
      assertTrue(old.setLastModified(System.currentTimeMillis() - 2 * 86400000L));

      SnapshotOwnerLeases.removeOrphans();

      assertFalse(old.exists(), "orphan lease was not deleted");
      assertTrue(recent.exists(), "recent lease was deleted");
   }

   private String peer(String name) {
      return "b78463-" + name + "-" + UUID.randomUUID();
   }

   /**
    * Writes the lease of another JVM, with the copies that its live tables read.
    */
   private File writeLease(String nodeId, String counter, File... copies) throws Exception {
      File lease = SnapshotOwnerLeases.getLeaseFile(
         FileSystemService.getInstance().getCacheFolder(), nodeId);
      StringBuilder content = new StringBuilder(counter);

      for(File copy : copies) {
         content.append('\n').append(copy.getName());
      }

      Files.writeString(lease.toPath(), content);
      created.add(lease);
      return lease;
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
    * Adds a reference of a live table to a copy, as SnapshotEmbeddedTableAssembly.getTable() does
    * when it copies the data to the cache.
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

   /**
    * Adds a count of a live table of another JVM to a copy, as EmbeddedTableReference does on
    * that JVM.
    */
   private static void addCountOfOtherJvm(File file, String nodeId) {
      String path = file.getAbsolutePath();
      Lock lock = cluster().getLock(SnapshotEmbeddedTableAssembly.FILE_REFERENCES_MAP_LOCK);
      lock.lock();

      try {
         Map<String, Integer> map =
            cluster().getMap(SnapshotEmbeddedTableAssembly.FILE_REFERENCES_MAP);
         Map<String, HashMap<String, Integer>> owners =
            cluster().getMap(SnapshotEmbeddedTableAssembly.FILE_OWNERS_MAP);
         map.put(path, map.getOrDefault(path, 0) + 1);
         HashMap<String, Integer> fileOwners = owners.get(path);
         fileOwners = fileOwners == null ? new HashMap<>() : fileOwners;
         fileOwners.merge(nodeId, 1, Integer::sum);
         owners.put(path, fileOwners);
      }
      finally {
         lock.unlock();
      }
   }

   private static void close(Object reference) throws Exception {
      ((Cleaner.Reference<?>) reference).close();
   }

   private static void clearDataCache() throws Exception {
      Method clearDataCache = ClearOldCacheFilesRunnable.class.getDeclaredMethod("clearDataCache");
      clearDataCache.setAccessible(true);
      clearDataCache.invoke(new ClearOldCacheFilesRunnable());
   }

   private static void waitForSweep(File file, String threadName) throws InterruptedException {
      // the sweep runs in a background thread
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
   }

   private static Integer count(File file) {
      return cluster().<String, Integer>getMap(SnapshotEmbeddedTableAssembly.FILE_REFERENCES_MAP)
         .get(file.getAbsolutePath());
   }

   private static Map<String, Integer> owners(File file) {
      Map<String, HashMap<String, Integer>> owners =
         cluster().getMap(SnapshotEmbeddedTableAssembly.FILE_OWNERS_MAP);
      return owners.getOrDefault(file.getAbsolutePath(), new HashMap<>());
   }

   private static Cluster cluster() {
      return Cluster.getInstance();
   }

   private final AtomicLong now = new AtomicLong(System.currentTimeMillis());
   private final List<File> created = new ArrayList<>();
   private final List<XSwappableTable> tables = new ArrayList<>();
   private final List<Object> references = new ArrayList<>();
}
