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
import inetsoft.util.stall.StallPolicy;
import inetsoft.util.stall.StallTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A read of a minus or intersect table lens concurrent with invalidate() returns the row of
 * the lens, never an exception, and a read right after invalidate() waits for the row
 * instead of failing. A failed read never leaves another row cached for its index
 * (bug #77397).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class SetTableLensInvalidateRaceTest {
   @BeforeEach
   public void setUp() {
      StallTestSupport.resetGlobalStallState();
      // the production policy: a wait fails only once a cycle is confirmed
      StallPolicy.setOverride(
         new StallPolicy(StallPolicy.Mode.FAIL, 1000, 200, dumpDir, 20, false));
   }

   @AfterEach
   public void tearDown() {
      StallTestSupport.clearOverride();
   }

   @Test
   public void minusReadsDuringInvalidateReturnRow() throws Exception {
      readsDuringInvalidate(MinusTableLens::new, data(ROWS), data(0));
   }

   @Test
   public void intersectReadsDuringInvalidateReturnRow() throws Exception {
      readsDuringInvalidate(IntersectTableLens::new, data(ROWS), data(ROWS));
   }

   private void readsDuringInvalidate(BiFunction<TableLens, TableLens, SetTableLens> create,
                                      Object[][] left, Object[][] right)
      throws Exception
   {
      // the rows are in the order of the merged table's keys, not of the base, so compare
      // with a fresh lens
      SetTableLens fresh = create.apply(new DefaultTableLens(left), new DefaultTableLens(right));
      assertFalse(fresh.moreRows(TableLens.EOT));
      int count = fresh.getRowCount();
      SetTableLens lens = create.apply(new DefaultTableLens(left), new DefaultTableLens(right));
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
      }, "SetTableLensInvalidateRaceTest-invalidator");

      Thread[] readers = new Thread[READERS];

      for(int i = 0; i < readers.length; i++) {
         readers[i] = new Thread(() -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();

            while(!done.get()) {
               int r = 1 + random.nextInt(count - 1);
               String result;

               try {
                  // the typed getters and the style getters as well as getObject()
                  boolean ok = switch(random.nextInt(3)) {
                     case 0 -> Objects.equals(fresh.getObject(r, 0), lens.getObject(r, 0));
                     case 1 -> fresh.getDouble(r, 1) == lens.getDouble(r, 1);
                     default -> fresh.getAlignment(r, 1) == lens.getAlignment(r, 1);
                  };

                  result = ok ? "ok" : "wrong";
               }
               catch(Throwable ex) {
                  result = ex.getClass().getSimpleName();
               }

               results.merge(result, 1, Integer::sum);
            }
         }, "SetTableLensInvalidateRaceTest-reader-" + i);
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
      // no wrong value or exception
      assertEquals(1, results.size(), summary);
   }

   /**
    * A typed getter or a style getter right after invalidate(), with no moreRows(), waits for
    * the row of the next pass.
    */
   @Test
   public void typedGetterAfterInvalidateReadsRow() {
      MinusTableLens fresh = minus(data(ROWS), data(0));
      assertFalse(fresh.moreRows(TableLens.EOT));
      MinusTableLens lens = minus(data(ROWS), data(0));
      assertFalse(lens.moreRows(TableLens.EOT));

      lens.invalidate();
      assertEquals(fresh.getDouble(5, 1), lens.getDouble(5, 1));
      lens.invalidate();
      assertFalse(lens.isNull(6, 0));
      lens.invalidate();
      assertEquals(fresh.getInt(7, 0), lens.getInt(7, 0));
      lens.invalidate();
      assertEquals(fresh.getAlignment(8, 0), lens.getAlignment(8, 0));
   }

   /**
    * A read that finds no row right after invalidate() doesn't leave the last row read cached
    * for its index: the row read next is the right one, over an unchanged base.
    */
   @Test
   public void failedReadDoesNotCacheOtherRow() {
      MinusTableLens lens = minus(data(ROWS), data(0));
      assertFalse(lens.moreRows(TableLens.EOT));
      Object value3 = lens.getObject(3, 0);
      Object value7 = lens.getObject(7, 0);
      assertNotEquals(value3, value7);

      assertEquals(value3, lens.getObject(3, 0));
      lens.invalidate();
      assertDoesNotThrow(() -> lens.isNull(7, 0));

      assertEquals(value7, lens.getObject(7, 0));
      assertFalse(lens.moreRows(TableLens.EOT));
      assertEquals(value7, lens.getObject(7, 0));
   }

   /**
    * After a base change and invalidate(), a read returns the row of the new pass, not the
    * row cached by the old one.
    */
   @Test
   public void invalidateAfterBaseChangeReadsNewRow() {
      DefaultTableLens right = new DefaultTableLens(new Object[][] { { "id", "value" },
                                                                     { 99, 99 } });
      MinusTableLens lens = new MinusTableLens(new DefaultTableLens(data(3)), right);
      assertEquals(1, lens.getObject(1, 0));

      right.setData(data(1));
      lens.invalidate();

      MinusTableLens fresh = minus(data(3), data(1));
      assertEquals(fresh.getObject(1, 0), lens.getObject(1, 0));
      assertEquals(2, lens.getObject(1, 0));
   }

   private static MinusTableLens minus(Object[][] left, Object[][] right) {
      return new MinusTableLens(new DefaultTableLens(left), new DefaultTableLens(right));
   }

   /**
    * {@code rows} rows of {@code id, value}, both {@code r} for row {@code r}.
    */
   private static Object[][] data(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "id", "value" };

      for(int r = 1; r <= rows; r++) {
         data[r] = new Object[] { r, r };
      }

      return data;
   }

   @TempDir
   File dumpDir;

   private static final int ROWS = 300;
   private static final int READERS = 3;
   private static final long DURATION_MS = 2000;
   private static final long JOIN_MS = 10000;
}
