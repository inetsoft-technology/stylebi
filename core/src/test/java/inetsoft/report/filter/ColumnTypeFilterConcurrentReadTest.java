/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.filter;

import inetsoft.report.lens.AbstractTableLens;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrent readers of a column type filter each get the value of the cell they read, never
 * the value of another cell or an exception, also while the filter is invalidated
 * (bug #77397).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class ColumnTypeFilterConcurrentReadTest {
   /**
    * Concurrent first readers of fresh filters, with no invalidate() at all.
    */
   @Test
   public void concurrentReadersGetOwnCell() throws Exception {
      ExecutorService pool = Executors.newFixedThreadPool(READERS);
      Map<String, Integer> results = new TreeMap<>();
      long end = System.currentTimeMillis() + DURATION_MS;
      int trials = 0;

      try {
         for(; System.currentTimeMillis() < end; trials++) {
            ColumnTypeFilter filter = filter();
            // the cache exists before the readers start
            filter.getObject(1, 0);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();

            for(int i = 0; i < READERS; i++) {
               futures.add(pool.submit(() -> {
                  start.await();
                  return readCells(filter, 2 * ROWS);
               }));
            }

            start.countDown();

            for(Future<String> future : futures) {
               results.merge(future.get(JOIN_MS, TimeUnit.MILLISECONDS), 1, Integer::sum);
            }
         }
      }
      finally {
         pool.shutdownNow();
      }

      String summary = "trials=" + trials + " readers=" + READERS + " results=" + results;
      assertTrue(results.getOrDefault("ok", 0) > 0, summary);
      assertEquals(1, results.size(), summary);
   }

   /**
    * Readers concurrent with an invalidate() loop.
    */
   @Test
   public void readsDuringInvalidateReturnOwnCell() throws Exception {
      ColumnTypeFilter filter = filter();
      AtomicBoolean done = new AtomicBoolean();
      AtomicLong invalidations = new AtomicLong();
      Map<String, Integer> results = new ConcurrentHashMap<>();

      Thread invalidator = new Thread(() -> {
         while(!done.get()) {
            filter.invalidate();
            invalidations.incrementAndGet();
         }
      }, "ColumnTypeFilterConcurrentReadTest-invalidator");

      Thread[] readers = new Thread[READERS];

      for(int i = 0; i < readers.length; i++) {
         readers[i] = new Thread(() -> {
            while(!done.get()) {
               results.merge(readCells(filter, 100), 1, Integer::sum);
            }
         }, "ColumnTypeFilterConcurrentReadTest-reader-" + i);
      }

      invalidator.start();

      for(Thread reader : readers) {
         reader.start();
      }

      Thread.sleep(DURATION_MS);
      done.set(true);
      invalidator.join(JOIN_MS);

      for(Thread reader : readers) {
         reader.join(JOIN_MS);
      }

      assertFalse(invalidator.isAlive(), "invalidator did not stop");

      for(Thread reader : readers) {
         assertFalse(reader.isAlive(), "reader did not stop");
      }

      String summary = "invalidations=" + invalidations.get() + " results=" + results;
      assertTrue(invalidations.get() > 0, summary);
      assertTrue(results.getOrDefault("ok", 0) > 0, summary);
      assertEquals(1, results.size(), summary);
   }

   /**
    * Read {@code count} random cells, and report "ok" or the first wrong value or exception.
    */
   private static String readCells(ColumnTypeFilter filter, int count) {
      ThreadLocalRandom random = ThreadLocalRandom.current();

      try {
         for(int i = 0; i < count; i++) {
            int r = 1 + random.nextInt(ROWS);
            int c = random.nextInt(2);
            Object value = filter.getObject(r, c);

            if(!Objects.equals(value(r, c), value)) {
               return "wrong";
            }
         }

         return "ok";
      }
      catch(Throwable ex) {
         return ex.getClass().getSimpleName();
      }
   }

   private static ColumnTypeFilter filter() {
      return new ColumnTypeFilter(new ArrayTable(), new String[] { "integer", "string" });
   }

   private static Object value(int r, int c) {
      return c == 0 ? (Object) r : "s" + r;
   }

   /**
    * A base that returns a fixed value per cell and caches nothing itself.
    */
   private static final class ArrayTable extends AbstractTableLens {
      @Override
      public int getRowCount() {
         return ROWS + 1;
      }

      @Override
      public int getColCount() {
         return 2;
      }

      @Override
      public Object getObject(int r, int c) {
         return r == 0 ? (c == 0 ? "id" : "name") : VALUES[r][c];
      }

      private static final Object[][] VALUES = new Object[ROWS + 1][2];

      static {
         for(int r = 1; r <= ROWS; r++) {
            VALUES[r][0] = value(r, 0);
            VALUES[r][1] = value(r, 1);
         }
      }
   }

   private static final int ROWS = 2000;
   private static final int READERS = 4;
   private static final long DURATION_MS = 2000;
   private static final long JOIN_MS = 10000;
}
