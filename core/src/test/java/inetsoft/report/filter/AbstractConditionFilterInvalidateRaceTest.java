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
package inetsoft.report.filter;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77273 follow-up: a reader of a condition filter racing {@code invalidate()} must get
 * the row it asks for, as long as the row exists in every row map. The answer has to come
 * from the map the reader's own population published: {@code invalidate()} may publish a
 * new, header-only map as soon as the filter's monitor is released.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class AbstractConditionFilterInvalidateRaceTest {
   /**
    * Every {@code moreRows()} of the reader is followed by an {@code invalidate()} before the
    * reader reads the row map, the interleaving a concurrent invalidator hits at random. The
    * bounded retries in {@code getBaseRowIndex()} each lose, and its last fallback must still
    * map the row: it used to snapshot a header-only map and throw
    * {@code IndexOutOfBoundsException}.
    */
   @Test
   public void baseRowIsMappedWhenEveryMoreRowsLosesToAnInvalidate() {
      Filter filter = new Filter(table(40));
      assertTrue(filter.moreRows(40));
      filter.invalidateAfterMoreRows = true;

      assertEquals(20, filter.getObject(20, 0));
      assertTrue(filter.invalidates > 0, "no invalidate was interleaved");
   }

   /**
    * One thread invalidates and re-populates the filter, another asks {@code moreRows()} for
    * rows that exist in every row map. A {@code false} answer is a silently truncated read:
    * {@code moreRows()} used to compute its answer from the row map after releasing the
    * monitor, i.e. possibly from the header-only map an invalidate had just published.
    * {@code getRowCount()} must not report a completed table shorter than every row map
    * either: it read {@code completed} and the row map separately.
    */
   @Test
   public void moreRowsFindsExistingRowsDuringInvalidate() throws Exception {
      Filter filter = new Filter(table(ROWS));
      AtomicBoolean stop = new AtomicBoolean();
      ExecutorService pool = Executors.newFixedThreadPool(2);

      try {
         Future<Integer> invalidator = pool.submit(() -> {
            int n = 0;

            while(!stop.get()) {
               filter.invalidate();

               for(int r = 0; filter.moreRows(r); r++) {
                  filter.getObject(r, 0);
               }

               n++;
            }

            return n;
         });
         Future<int[]> reader = pool.submit(() -> {
            // reads, truncated answers (moreRows false or a short row count), wrong values
            int[] result = new int[3];
            Random random = new Random(1);
            long end = System.currentTimeMillis() + RACE_MILLIS;

            try {
               while(System.currentTimeMillis() < end) {
                  int r = 1 + random.nextInt(ROWS);
                  result[0]++;

                  try {
                     int count = filter.getRowCount();

                     if(count >= 0 && count != ROWS + 1) {
                        result[1]++;
                     }

                     if(!filter.moreRows(r)) {
                        result[1]++;
                     }
                     else if(!Integer.valueOf(r).equals(filter.getObject(r, 0))) {
                        result[2]++;
                     }
                  }
                  catch(IndexOutOfBoundsException ex) {
                     result[2]++;
                  }
               }
            }
            finally {
               stop.set(true);
            }

            return result;
         });

         int[] result = reader.get(30, TimeUnit.SECONDS);
         assertTrue(invalidator.get(30, TimeUnit.SECONDS) > 0);
         assertEquals(0, result[1], "moreRows() or getRowCount() truncated the table, of " +
            result[0] + " reads");
         assertEquals(0, result[2], "wrong values or exceptions, of " + result[0] + " reads");
      }
      finally {
         stop.set(true);
         pool.shutdownNow();
      }
   }

   private static DefaultTableLens table(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] {"id"};

      for(int i = 1; i <= rows; i++) {
         data[i] = new Object[] {i};
      }

      return new DefaultTableLens(data);
   }

   private static final class Filter extends AbstractConditionFilter {
      Filter(DefaultTableLens table) {
         setTable(table);
      }

      @Override
      protected boolean checkCondition(int r) {
         return true;
      }

      @Override
      public boolean moreRows(int row) {
         boolean more = super.moreRows(row);

         if(invalidateAfterMoreRows) {
            // what a concurrent invalidator does once the population released the monitor
            invalidate();
            invalidates++;
         }

         return more;
      }

      private volatile boolean invalidateAfterMoreRows;
      private int invalidates;
   }

   private static final int ROWS = 300;
   private static final long RACE_MILLIS = 2000;
}
