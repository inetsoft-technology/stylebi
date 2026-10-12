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

package inetsoft.report.lens;

import inetsoft.report.TableLens;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A read of a cross join table lens concurrent with invalidate() returns a cell of its base
 * tables, never an exception, and a loader superseded by invalidate() doesn't end the next
 * pass early (bug #77397).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class CrossJoinTableLensInvalidateRaceTest {
   /**
    * The row order depends on the base table that completes loading first, so a cell is
    * checked against the domain of its base column, not a fresh lens.
    */
   @Test
   public void readsDuringInvalidateReturnBaseCell() throws Exception {
      CrossJoinTableLens lens = new CrossJoinTableLens(new DefaultTableLens(data(LEFT, 0)),
                                                       new DefaultTableLens(data(RIGHT, 100)));
      assertFalse(lens.moreRows(TableLens.EOT));
      int count = lens.getRowCount();
      assertEquals(LEFT * RIGHT + 1, count);

      AtomicBoolean done = new AtomicBoolean();
      AtomicLong invalidations = new AtomicLong();
      Map<String, Integer> results = new ConcurrentHashMap<>();

      Thread invalidator = new Thread(() -> {
         while(!done.get()) {
            lens.invalidate();
            lens.moreRows(TableLens.EOT);
            invalidations.incrementAndGet();
         }
      }, "CrossJoinTableLensInvalidateRaceTest-invalidator");

      Thread[] readers = new Thread[READERS];

      for(int i = 0; i < readers.length; i++) {
         // one reader reads with no moreRows(), as a typed getter may
         boolean check = i != 0;

         readers[i] = new Thread(() -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();

            while(!done.get()) {
               int r = 1 + random.nextInt(count - 1);
               String result;

               try {
                  if(check) {
                     lens.moreRows(r);
                  }

                  Object left = lens.getObject(r, 0);
                  Object right = lens.getObject(r, 2);

                  if(left == null || right == null) {
                     // no row: invalidate() cleared the rows since, a reader doesn't retry
                     result = "ok";
                  }
                  else {
                     int lv = (Integer) left;
                     int rv = (Integer) right;
                     result = lv >= 1 && lv <= LEFT && rv >= 101 && rv <= 100 + RIGHT ?
                        "ok" : "wrong";
                  }
               }
               catch(Throwable ex) {
                  result = ex.getClass().getSimpleName();
               }

               results.merge(result, 1, Integer::sum);
            }
         }, "CrossJoinTableLensInvalidateRaceTest-reader-" + i);
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
    * The left loader of the first pass completes after invalidate(), while the left loader of
    * the next pass is still loading: the next pass still waits for its own loader.
    */
   @Test
   public void supersededLoaderDoesNotEndNextPassEarly() throws Exception {
      SlowTable left = new SlowTable(SLOW_LEFT);
      CrossJoinTableLens lens = new CrossJoinTableLens(left, new DefaultTableLens(data(50, 100)));

      try {
         CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(() -> lens.moreRows(1));
         left.parked.get(CAP_SECONDS, TimeUnit.SECONDS);

         lens.invalidate();
         // starts the loaders of the next pass
         lens.moreRows(0);
         left.gate.countDown();
         // the superseded loader updates the count
         Thread.sleep(150);

         assertFalse(lens.moreRows(TableLens.EOT));
         assertEquals(SLOW_LEFT * 50 + 1, lens.getRowCount());
         first.get(CAP_SECONDS, TimeUnit.SECONDS);
      }
      finally {
         left.gate.countDown();
      }
   }

   /**
    * {@code rows} rows of {@code id, value}, {@code offset + r} for row {@code r}.
    */
   private static Object[][] data(int rows, int offset) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "id", "value" };

      for(int r = 1; r <= rows; r++) {
         data[r] = new Object[] { offset + r, offset + r };
      }

      return data;
   }

   /**
    * A table that a cross join loader reads slowly, and whose first row count read by a
    * loader waits for {@link #gate}.
    */
   private static class SlowTable extends DefaultTableLens {
      SlowTable(int rows) {
         super(data(rows, 0));
      }

      @Override
      public boolean moreRows(int row) {
         if(isLoader()) {
            try {
               Thread.sleep(20);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }

         return super.moreRows(row);
      }

      @Override
      public int getRowCount() {
         if(isLoader() && parking.compareAndSet(false, true)) {
            parked.complete(Thread.currentThread());

            try {
               gate.await(CAP_SECONDS, TimeUnit.SECONDS);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }

         return super.getRowCount();
      }

      private static boolean isLoader() {
         return Thread.currentThread().getClass().getName().contains("CrossJoinTableLens$");
      }

      private final AtomicBoolean parking = new AtomicBoolean();
      private final CountDownLatch gate = new CountDownLatch(1);
      private final CompletableFuture<Thread> parked = new CompletableFuture<>();
   }

   private static final int LEFT = 7;
   private static final int RIGHT = 11;
   private static final int SLOW_LEFT = 2000;
   private static final int READERS = 3;
   private static final long DURATION_MS = 2000;
   private static final long JOIN_MS = 10000;
   private static final long CAP_SECONDS = 30;
}
