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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MockCluster;
import inetsoft.test.*;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.util.Cleaner;
import inetsoft.util.FileSystemService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.lang.ref.Reference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78082, a snapshot copy that a table of this JVM reads is never deleted or unprotected by
 * this JVM, whatever node id its count was added under. The local count of the tables that read a
 * copy must go back to zero on every path that ends a table, or this JVM would protect the copy
 * for the rest of its life: tables opened and closed at once by many threads, a table that the
 * cleaner closes after it is dropped, and a close while the cluster can't be reached.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
   BaseTestConfiguration.class, SwapperTestConfiguration.class,
   SnapshotFileReferencesLocalCountTest.TestClusterConfiguration.class
}, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SnapshotFileReferencesLocalCountTest {
   @AfterEach
   void cleanUp() throws Exception {
      failLock = false;
      Map<String, ?> map = Cluster.getInstance().getMap(
         SnapshotEmbeddedTableAssembly.FILE_REFERENCES_MAP);
      Map<String, ?> owners = Cluster.getInstance().getMap(
         SnapshotEmbeddedTableAssembly.FILE_OWNERS_MAP);

      for(File file : created) {
         map.remove(file.getAbsolutePath());
         owners.remove(file.getAbsolutePath());
         Files.deleteIfExists(file.toPath());
      }

      created.clear();
   }

   @Test
   void localCountIsZeroAfterConcurrentOpenAndClose() throws Exception {
      File file = createCopy("t78082l_1_s.tdat");
      // a table that stays open the whole time, the copy must not be deleted before it closes.
      // it is kept reachable, so the cleaner does not close it
      XSwappableTable holderTable = new XSwappableTable();
      Object holder = newReference(holderTable, file);
      int threads = 16;
      int rounds = 300;
      ExecutorService pool = Executors.newFixedThreadPool(threads);
      CountDownLatch start = new CountDownLatch(1);
      List<Future<?>> futures = new ArrayList<>();

      try {
         for(int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
               start.await();

               for(int r = 0; r < rounds; r++) {
                  Object reference = newReference(new XSwappableTable(), file);
                  assertTrue(isInUseLocally(file));
                  close(reference);
                  assertTrue(file.exists(), "copy was deleted while a table still read it");
               }

               return null;
            }));
         }

         start.countDown();

         for(Future<?> future : futures) {
            future.get(120, TimeUnit.SECONDS);
         }
      }
      finally {
         pool.shutdownNow();
      }

      assertTrue(isInUseLocally(file), "copy of the open table is not in use");
      assertEquals(1, count(file));
      close(holder);
      Reference.reachabilityFence(holderTable);

      assertFalse(isInUseLocally(file), "local count was left after the last table closed");
      assertNull(count(file), "count was left after the last table closed");
      assertNull(owners(file), "owners were left after the last table closed");
      assertFalse(file.exists(), "copy was not deleted when its last table was closed");
   }

   @Test
   void localCountIsZeroAfterCleanerClosesDroppedTable() throws Exception {
      File file = createCopy("t78082l_2_s.tdat");
      addDroppedTable(file);
      assertTrue(isInUseLocally(file));
      long end = System.currentTimeMillis() + 30000L;

      // the cleaner thread closes the reference once the table is phantom reachable
      while((isInUseLocally(file) || file.exists()) && System.currentTimeMillis() < end) {
         System.gc();
         Thread.sleep(50L);
      }

      assertFalse(isInUseLocally(file), "local count was left after the cleaner closed the table");
      assertFalse(file.exists(), "copy was not deleted when the cleaner closed its last table");
      assertNull(count(file), "count was left after the cleaner closed the table");
   }

   @Test
   void localCountIsZeroAfterCloseWhileClusterIsDown() throws Exception {
      File file = createCopy("t78082l_3_s.tdat");
      XSwappableTable table = new XSwappableTable();
      Object reference = newReference(table, file);
      failLock = true;

      assertThrows(IllegalStateException.class, () -> close(reference));
      assertFalse(isInUseLocally(file),
                  "local count was left after a close that could not reach the cluster");

      // Bug #78096, the count in the cluster is removed by the next close, and nothing is left
      // for the cleaner to close after this test
      failLock = false;
      close(reference);
      Reference.reachabilityFence(table);

      assertNull(count(file), "count was left after the cluster could be reached again");
      assertFalse(file.exists(), "copy was not deleted when its last table was closed");
   }

   private File createCopy(String name) throws Exception {
      File file = FileSystemService.getInstance().getCacheFile(name);
      Files.write(file.toPath(), new byte[] { 1 });
      created.add(file);
      return file;
   }

   // in a method of its own, so that nothing on the test's stack keeps the table reachable
   private static void addDroppedTable(File file) throws Exception {
      Cleaner.add((Cleaner.Reference<?>) newReference(new XSwappableTable(), file));
   }

   /**
    * Adds a reference of a table to a copy, as SnapshotEmbeddedTableAssembly.getTable() does when
    * it copies the data to the cache.
    */
   private static Object newReference(XSwappableTable table, File file) throws Exception {
      Class<?> cls = Class.forName(
         SnapshotEmbeddedTableAssembly.class.getName() + "$EmbeddedTableReference");
      Constructor<?> cons = cls.getDeclaredConstructor(XSwappableTable.class, File[].class);
      cons.setAccessible(true);

      try {
         return cons.newInstance(table, new File[] { file });
      }
      catch(InvocationTargetException e) {
         throw (Exception) e.getCause();
      }
   }

   private static void close(Object reference) throws Exception {
      ((Cleaner.Reference<?>) reference).close();
   }

   private static boolean isInUseLocally(File file) {
      return SnapshotEmbeddedTableAssembly.isFileInUseLocally(file.getAbsolutePath());
   }

   private static Integer count(File file) {
      return Cluster.getInstance().<String, Integer>getMap(
         SnapshotEmbeddedTableAssembly.FILE_REFERENCES_MAP).get(file.getAbsolutePath());
   }

   private static Object owners(File file) {
      return Cluster.getInstance().getMap(SnapshotEmbeddedTableAssembly.FILE_OWNERS_MAP)
         .get(file.getAbsolutePath());
   }

   private final List<File> created = new ArrayList<>();
   private static volatile boolean failLock;

   @Configuration
   static class TestClusterConfiguration {
      // replaces the cluster of BaseTestConfiguration with one whose snapshot lock can fail,
      // as when the cluster can't be reached
      @Bean
      public Cluster cluster() {
         return new MockCluster() {
            @Override
            public Lock getLock(String name) {
               if(failLock && SnapshotEmbeddedTableAssembly.FILE_REFERENCES_MAP_LOCK.equals(name)) {
                  throw new IllegalStateException("cluster can't be reached");
               }

               return super.getLock(name);
            }
         };
      }
   }
}
