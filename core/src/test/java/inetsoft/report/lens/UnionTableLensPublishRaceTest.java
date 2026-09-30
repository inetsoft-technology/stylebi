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

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrent first readers of a non-distinct union all see every row of the union (bug #77365).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class UnionTableLensPublishRaceTest {
   /**
    * Readers that call isNull(r, c), which reads the row without moreRows(r), race with
    * readers that check moreRows(r) and then read the cell.
    */
   @Test
   public void concurrentIsNullAndMoreRowsReadersSeeEveryRow() throws Exception {
      ExecutorService pool = Executors.newFixedThreadPool(READERS);
      Map<String, Integer> results = new TreeMap<>();
      long end = System.currentTimeMillis() + STRESS_MS;
      int trials = 0;

      try {
         for(; System.currentTimeMillis() < end; trials++) {
            UnionTableLens union = union();
            CountDownLatch start = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();

            for(int i = 0; i < READERS; i++) {
               boolean nullReader = i % 2 == 0;

               futures.add(pool.submit(() -> {
                  start.await();
                  // the last row of the union: a row of the right table
                  int r = 2 * ROWS;

                  try {
                     if(nullReader) {
                        return union.isNull(r, 1) ? "null" : "ok";
                     }

                     if(!union.moreRows(r)) {
                        return "no row";
                     }

                     return Integer.valueOf(-ROWS).equals(union.getObject(r, 1)) ? "ok" : "wrong";
                  }
                  catch(Throwable ex) {
                     return ex.getClass().getSimpleName();
                  }
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

   // union all of a left table with values 1..ROWS and a right table with values -1..-ROWS
   private static UnionTableLens union() {
      UnionTableLens union = new UnionTableLens(new DefaultTableLens(data(1)),
                                                new DefaultTableLens(data(-1)));
      union.setDistinct(false);
      return union;
   }

   private static Object[][] data(int sign) {
      Object[][] data = new Object[ROWS + 1][];
      data[0] = new Object[] { "id", "value" };

      for(int b = 1; b <= ROWS; b++) {
         data[b] = new Object[] { b, sign * b };
      }

      return data;
   }

   private static final int ROWS = 100;
   private static final long STRESS_MS = 6000;
   private static final int READERS = 4;
   private static final long JOIN_MS = 10000;
}
