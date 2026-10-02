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

import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A read of a sort filter concurrent with invalidate() returns the mapped cell, never the
 * header or a cell of a disposed row map (bug #77242).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class SortFilterInvalidateRaceTest {
   @Test
   public void readsDuringInvalidateReturnMappedCell() throws Exception {
      Object[][] data = new Object[ROWS + 1][];
      data[0] = new Object[] { "id", "value" };

      for(int b = 1; b <= ROWS; b++) {
         data[b] = new Object[] { b, b };
      }

      // descending on value: sorted row r maps to base row ROWS + 1 - r
      SortFilter filter = new SortFilter(new DefaultTableLens(data), new int[] { 1 }, false);
      Assertions.assertTrue(filter.moreRows(ROWS));

      AtomicBoolean done = new AtomicBoolean();
      AtomicLong invalidations = new AtomicLong();
      Map<String, Integer> results = new ConcurrentHashMap<>();

      Thread invalidator = new Thread(() -> {
         while(!done.get()) {
            filter.invalidate();
            filter.moreRows(TableLens.EOT);
            invalidations.incrementAndGet();
         }
      }, "SortFilterInvalidateRaceTest-invalidator");

      Thread[] readers = new Thread[READERS];

      for(int i = 0; i < readers.length; i++) {
         readers[i] = new Thread(() -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();

            while(!done.get()) {
               int r = 1 + random.nextInt(ROWS);
               String result;

               try {
                  Object value = filter.getObject(r, 1);

                  if(Integer.valueOf(ROWS + 1 - r).equals(value)) {
                     result = "ok";
                  }
                  else if("value".equals(value)) {
                     result = "header";
                  }
                  else {
                     result = "wrong";
                  }
               }
               catch(Throwable ex) {
                  result = ex.getClass().getSimpleName();
               }

               results.merge(result, 1, Integer::sum);
            }
         }, "SortFilterInvalidateRaceTest-reader-" + i);
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

      Assertions.assertFalse(invalidator.isAlive(), "invalidator did not stop");

      for(Thread reader : readers) {
         Assertions.assertFalse(reader.isAlive(), "reader did not stop");
      }

      String summary = "invalidations=" + invalidations.get() + " results=" + results;
      Assertions.assertTrue(invalidations.get() > 0, summary);
      Assertions.assertTrue(results.getOrDefault("ok", 0) > 0, summary);
      Assertions.assertEquals(0, results.getOrDefault("header", 0), summary);
      Assertions.assertEquals(0, results.getOrDefault("NullPointerException", 0), summary);
      Assertions.assertEquals(0, results.getOrDefault("IndexOutOfBoundsException", 0), summary);
      // no other wrong value or exception either
      Assertions.assertEquals(1, results.size(), summary);
   }

   private static final int ROWS = 2000;
   private static final int READERS = 2;
   private static final long DURATION_MS = 2500;
   private static final long JOIN_MS = 10000;
}
