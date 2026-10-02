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

import inetsoft.test.*;
import inetsoft.uql.table.SnapshotEmbeddedTableDataCache;
import inetsoft.uql.table.XSwappableTable;
import org.apache.commons.io.output.StringBuilderWriter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Threads that find a snapshot embedded table already cached by another thread while waiting
 * for the per-key lock must release that lock, so no later open of the same table parks
 * forever (bug #77334).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class SnapshotEmbeddedTableAssemblyLockTest {
   @BeforeEach
   public void setUp() throws Exception {
      dataPaths = new String[] { "bug77334-" + UUID.randomUUID() };
      cacheKey = cacheKey(assembly());
   }

   @AfterEach
   public void tearDown() {
      SnapshotEmbeddedTableDataCache.getInstance().remove(cacheKey);
   }

   @Test
   public void queuedOpensAllReturnTheTableLoadedByAnotherThread() throws Exception {
      SnapshotEmbeddedTableDataCache cache = SnapshotEmbeddedTableDataCache.getInstance();
      ReentrantLock lock = cache.getLock(cacheKey);
      XSwappableTable loaded = new XSwappableTable();
      int contenders = 3;
      AtomicReferenceArray<XSwappableTable> results = new AtomicReferenceArray<>(contenders);
      List<Thread> threads = new ArrayList<>();

      // this thread stands in for the loader: it holds the key lock while the others queue
      lock.lock();

      try {
         for(int i = 0; i < contenders; i++) {
            SnapshotEmbeddedTableAssembly assembly = assembly();
            int index = i;
            threads.add(start("contender-" + i, () -> results.set(index, assembly.getTable())));
         }

         awaitQueueLength(lock, contenders);
         cache.set(cacheKey, loaded);
      }
      finally {
         lock.unlock();
      }

      for(Thread thread : threads) {
         thread.join(TIMEOUT);
         assertFalse(thread.isAlive(), thread.getName() + " is still waiting for the key lock");
      }

      for(int i = 0; i < contenders; i++) {
         assertSame(loaded, results.get(i), "contender-" + i + " did not get the cached table");
      }

      assertFalse(lock.isLocked(), "the key lock is still held after every open returned");
   }

   @Test
   public void lockFetchedBeforeEvictionIsNotLeftHeld() throws Exception {
      SnapshotEmbeddedTableDataCache cache = SnapshotEmbeddedTableDataCache.getInstance();
      ReentrantLock lock = cache.getLock(cacheKey);
      XSwappableTable loaded = new XSwappableTable();
      SnapshotEmbeddedTableAssembly assembly = assembly();
      Thread contender;

      lock.lock();

      try {
         contender = start("contender", assembly::getTable);
         awaitQueueLength(lock, 1);
         cache.set(cacheKey, loaded);
      }
      finally {
         lock.unlock();
      }

      contender.join(TIMEOUT);
      assertFalse(contender.isAlive(), "contender is still waiting for the key lock");
      assertSame(loaded, assembly.getTable());

      // the entry is evicted while another thread already holds a reference to the old lock,
      // as in the window between CACHE.remove and CACHE_LOCK.remove
      cache.remove(cacheKey);
      assertNotSame(lock, cache.getLock(cacheKey));

      boolean[] acquired = new boolean[1];
      Thread loader = start("loader", () -> {
         try {
            acquired[0] = lock.tryLock(TIMEOUT, TimeUnit.MILLISECONDS);

            if(acquired[0]) {
               lock.unlock();
            }
         }
         catch(InterruptedException ignore) {
         }
      });

      loader.join(TIMEOUT * 2);
      assertFalse(loader.isAlive());
      assertTrue(acquired[0], "the old key lock is still held by a thread that already returned");
      assertFalse(lock.isLocked());
   }

   private SnapshotEmbeddedTableAssembly assembly() throws Exception {
      SnapshotEmbeddedTableAssembly assembly = new SnapshotEmbeddedTableAssembly();
      Field field = SnapshotEmbeddedTableAssembly.class.getDeclaredField("dataPaths");
      field.setAccessible(true);
      field.set(assembly, dataPaths.clone());
      return assembly;
   }

   private static String cacheKey(SnapshotEmbeddedTableAssembly assembly) throws Exception {
      StringBuilderWriter writer = new StringBuilderWriter();
      assembly.printEmbeddedDataKey(new PrintWriter(writer));
      return writer.toString();
   }

   private static Thread start(String name, Runnable task) {
      Thread thread = new Thread(task, "bug77334-" + name);
      // a thread parked on a leaked lock never returns, so it must not keep the JVM alive
      thread.setDaemon(true);
      thread.start();
      return thread;
   }

   private static void awaitQueueLength(ReentrantLock lock, int length) throws Exception {
      long deadline = System.currentTimeMillis() + TIMEOUT;

      while(lock.getQueueLength() < length) {
         assertTrue(System.currentTimeMillis() < deadline,
                    "contenders did not queue on the key lock");
         Thread.sleep(10);
      }
   }

   private static final long TIMEOUT = 5000;
   private String[] dataPaths;
   private String cacheKey;
}
