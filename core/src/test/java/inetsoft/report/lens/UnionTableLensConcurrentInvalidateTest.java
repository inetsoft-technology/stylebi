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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two readers draining a shared non-distinct union, which reads its bases without its monitor,
 * while a third thread invalidates it, each see every row of the union once, in order, and the
 * union reports the right row count once they finish (bug #77874).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
public class UnionTableLensConcurrentInvalidateTest {
   @Test
   public void concurrentDrainsDuringInvalidateSeeEveryRowOnce() throws Exception {
      ExecutorService pool = Executors.newFixedThreadPool(3);
      List<Object> expected = expected();
      Map<String, Integer> results = new TreeMap<>();
      long end = System.currentTimeMillis() + STRESS_MS;
      int trials = 0;
      int invalidates = 0;

      try {
         for(; System.currentTimeMillis() < end; trials++) {
            UnionTableLens union = union();
            CountDownLatch start = new CountDownLatch(1);
            AtomicBoolean draining = new AtomicBoolean(true);
            List<Future<String>> readers = new ArrayList<>();

            for(int i = 0; i < 2; i++) {
               readers.add(pool.submit(() -> {
                  start.await();
                  List<Object> values = new ArrayList<>();

                  try {
                     for(int r = 1; union.moreRows(r); r++) {
                        values.add(union.getObject(r, 1));
                     }
                  }
                  catch(Throwable ex) {
                     return ex.getClass().getSimpleName();
                  }

                  return expected.equals(values) ? "ok" : "wrong rows " + values.size();
               }));
            }

            Future<Integer> invalidator = pool.submit(() -> {
               start.await();
               int count = 0;

               while(draining.get()) {
                  union.invalidate();
                  count++;
                  Thread.yield();
               }

               return count;
            });

            start.countDown();

            try {
               for(Future<String> reader : readers) {
                  results.merge(reader.get(JOIN_MS, TimeUnit.MILLISECONDS), 1, Integer::sum);
               }
            }
            finally {
               draining.set(false);
            }

            invalidates += invalidator.get(JOIN_MS, TimeUnit.MILLISECONDS);

            // settled: the published count and the rows are those of the whole union
            String state = "trial " + trials;
            assertEquals(2 * ROWS + 1, union.getRowCount(), state);
            assertTrue(union.moreRows(2 * ROWS), state);
            assertFalse(union.moreRows(2 * ROWS + 1), state);
            assertEquals(-ROWS, union.getObject(2 * ROWS, 1), state);
            assertFalse(union.moreRows(TableLens.EOT), state);
            assertEquals(2 * ROWS + 1, union.getRowCount(), state);
         }
      }
      finally {
         pool.shutdownNow();
      }

      String summary = "trials=" + trials + " invalidates=" + invalidates + " results=" + results;
      assertTrue(invalidates > 0, summary);
      assertEquals(Map.of("ok", 2 * trials), results, summary);
   }

   // union all of a left table with values 1..ROWS and a right table with values -1..-ROWS,
   // both loading their rows as they are read
   private static UnionTableLens union() {
      UnionTableLens union = new UnionTableLens(new LoadingTable(1), new LoadingTable(-1));
      union.setDistinct(false);
      return union;
   }

   private static List<Object> expected() {
      List<Object> values = new ArrayList<>();

      for(int b = 1; b <= ROWS; b++) {
         values.add(b);
      }

      for(int b = 1; b <= ROWS; b++) {
         values.add(-b);
      }

      return values;
   }

   private static Object[][] data(int sign) {
      Object[][] data = new Object[ROWS + 1][];
      data[0] = new Object[] { "id", "value" };

      for(int b = 1; b <= ROWS; b++) {
         data[b] = new Object[] { b, sign * b };
      }

      return data;
   }

   /**
    * A table that reports a negative (loading) row count until a reader reached its end, like
    * a lens still reading its own base.
    */
   private static final class LoadingTable extends DefaultTableLens {
      LoadingTable(int sign) {
         super(data(sign));
      }

      @Override
      public boolean moreRows(int row) {
         if(row != EOT && row <= ROWS) {
            loaded.accumulateAndGet(row + 1, Math::max);
            Thread.yield();
            return true;
         }

         loaded.set(ROWS + 1);
         return false;
      }

      @Override
      public int getRowCount() {
         int loaded = this.loaded.get();
         return loaded > ROWS ? ROWS + 1 : -loaded - 1;
      }

      private final AtomicInteger loaded = new AtomicInteger();
   }

   private static final int ROWS = 200;
   private static final long STRESS_MS = 2000;
   private static final long JOIN_MS = 10000;
}
