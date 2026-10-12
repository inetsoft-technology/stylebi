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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A read of a ranking table lens concurrent with invalidate() returns the ranked row, never
 * the header, a cell of a disposed or partly filled row list, or an exception, and its row
 * count is the count of the complete ranking (bug #77397).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class RankingTableLensInvalidateRaceTest {
   @Test
   public void readsDuringInvalidateReturnRankedRow() throws Exception {
      RankingTableLens fresh = ranking();
      assertFalse(fresh.moreRows(TableLens.EOT));
      int count = fresh.getRowCount();
      assertEquals(TOP + 1, count);
      RankingTableLens lens = ranking();
      assertFalse(lens.moreRows(TableLens.EOT));

      AtomicBoolean done = new AtomicBoolean();
      AtomicLong invalidations = new AtomicLong();
      Map<String, Integer> results = new ConcurrentHashMap<>();

      Thread invalidator = new Thread(() -> {
         while(!done.get()) {
            lens.invalidate();
            lens.moreRows(TableLens.EOT);
            invalidations.incrementAndGet();
         }
      }, "RankingTableLensInvalidateRaceTest-invalidator");

      Thread[] readers = new Thread[READERS];

      for(int i = 0; i < readers.length; i++) {
         boolean counts = i == 0;

         readers[i] = new Thread(() -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();

            while(!done.get()) {
               int r = 1 + random.nextInt(count - 1);
               String result;

               try {
                  if(counts) {
                     int n = lens.getRowCount();
                     result = n == count ? "ok" : "count " + n;
                  }
                  else {
                     Object value = lens.getObject(r, 1);

                     if(Objects.equals(fresh.getObject(r, 1), value)) {
                        result = "ok";
                     }
                     else if("value".equals(value)) {
                        result = "header";
                     }
                     else {
                        result = "wrong";
                     }
                  }
               }
               catch(Throwable ex) {
                  result = ex.getClass().getSimpleName();
               }

               results.merge(result, 1, Integer::sum);
            }
         }, "RankingTableLensInvalidateRaceTest-reader-" + i);
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
      // no header, wrong value, other count or exception
      assertEquals(1, results.size(), summary);
   }

   /**
    * The top {@link #TOP} of {@link #ROWS} rows of {@code id, value} on the value.
    */
   private static RankingTableLens ranking() {
      Object[][] data = new Object[ROWS + 1][];
      data[0] = new Object[] { "id", "value" };

      for(int r = 1; r <= ROWS; r++) {
         // distinct values in an order that is not the base order
         data[r] = new Object[] { r, (r * 7919) % 1009 };
      }

      RankingTableLens lens = new RankingTableLens(new DefaultTableLens(data));
      lens.setRankingColumn(1);
      lens.setEqualityKept(false);
      lens.setRankingN(TOP);
      lens.setTopRanking(true);
      return lens;
   }

   private static final int ROWS = 300;
   private static final int TOP = 100;
   private static final int READERS = 3;
   private static final long DURATION_MS = 2000;
   private static final long JOIN_MS = 10000;
}
