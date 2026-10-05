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
package inetsoft.report.composition.execution.lockcycle;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Sandbox;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Slow;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.SlowTable;
import inetsoft.report.filter.AbstractConditionFilter;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A formula lens invalidated while other threads compute or read its rows (bug #77243).
 * {@code invalidate()} swaps in a new row table without the lens lock, so a read that checked
 * the old table and read the new one got null, and a batch computing rows of the old table
 * appended them to the new one at the wrong positions, or appended an all-null row when the
 * swap nulled its row scriptable, and could mark the new table complete. A batch that saw the
 * new table before its header was added computed the base header row as the first data row,
 * shifting every row by one. Those rows stayed wrong after the invalidating thread's own
 * end-of-table read.
 *
 * <p>The formula yields -1 for the base header row and -2 for a missing base row, so a shifted
 * table is caught as a wrong value, not just as a null.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("slow")
public class FormulaTableLensInvalidateRaceTest {
   @BeforeEach
   public void setUp() {
      harness = new LockCycleHarness();
   }

   @AfterEach
   public void tearDown() throws Exception {
      harness.close();
   }

   /**
    * One thread repeatedly invalidates the lens, computes it to the end and reads every row
    * back; other threads read random rows at the same time. Every read, on either side, must
    * return the row's own value.
    */
   @Test
   public void readsDuringInvalidateReturnTheRowsOwnValue() throws Exception {
      Sandbox s = harness.control();
      FormulaTableLens lens = s.formula(new SlowTable(ROWS, Slow.NONE), "f1", FORMULA);
      lens.moreRows(TableLens.EOT);
      assertEquals(Collections.emptyMap(), readBack(lens), "before the race");

      AtomicBoolean stop = new AtomicBoolean();
      Future<Map<String, Integer>> invalidator = harness.submit(() -> {
         Map<String, Integer> bad = new TreeMap<>();
         int n = 0;

         while(!stop.get()) {
            try {
               lens.invalidate();
               lens.moreRows(TableLens.EOT);
               // the rows this thread just computed to the end must all be right, not only
               // eventually right (the corrupted rows used to persist until the next swap)
               readBack(lens).forEach((k, v) -> bad.merge("read-back " + k, v, Integer::sum));
            }
            catch(Throwable ex) {
               bad.merge("invalidator " + ex.getClass().getSimpleName(), 1, Integer::sum);
            }

            n++;
         }

         bad.put("#invalidations", n);
         return bad;
      });
      List<Future<Map<String, Integer>>> readers = new ArrayList<>();

      for(int i = 0; i < READERS; i++) {
         final int seed = i;
         readers.add(harness.submit(() -> {
            Map<String, Integer> bad = new TreeMap<>();
            Random random = new Random(seed);
            long end = System.currentTimeMillis() + DURATION_MS;
            int reads = 0;

            try {
               while(System.currentTimeMillis() < end) {
                  int r = 1 + random.nextInt(ROWS);
                  check(lens, r, bad, "reader ");
                  reads++;
               }
            }
            finally {
               stop.set(true);
            }

            bad.put("#reads", reads);
            return bad;
         }));
      }

      Map<String, Integer> bad = new TreeMap<>();

      for(Future<Map<String, Integer>> reader : readers) {
         harness.await(reader, LockCycleHarness.ACTIVE_CAP, "reader")
            .forEach((k, v) -> bad.merge(k, v, Integer::sum));
      }

      stop.set(true);
      bad.putAll(harness.await(invalidator, LockCycleHarness.ACTIVE_CAP, "invalidator"));

      int invalidations = bad.remove("#invalidations");
      int reads = bad.remove("#reads");
      String summary = "invalidations=" + invalidations + " reads=" + reads + " bad=" + bad;
      assertTrue(invalidations > 0, summary);
      assertTrue(reads > 0, summary);
      assertEquals(Collections.emptyMap(), bad, summary);
      assertEquals(Collections.emptyMap(), readBack(lens), "after the race");
   }

   /**
    * The row table is replaced in the middle of the batch a read starts, here deterministically
    * by the base's {@code moreRows()} of the row being read, as another thread's invalidate()
    * would. The batch stops without computing the row into the replaced row table, so the read
    * must compute and read it on the new row table rather than return null from the old one.
    */
   @Test
   public void readReturnsTheRowWhenTheRowTableIsReplacedDuringItsBatch() {
      Sandbox s = harness.control();
      AtomicBoolean armed = new AtomicBoolean();
      FormulaTableLens[] holder = new FormulaTableLens[1];
      SlowTable base = new SlowTable(ROWS, Slow.NONE) {
         @Override
         public boolean moreRows(int row) {
            if(row == TRIGGER_ROW && armed.compareAndSet(true, false)) {
               holder[0].invalidate();
            }

            return super.moreRows(row);
         }
      };
      FormulaTableLens lens = s.formula(base, "f1", FORMULA);
      holder[0] = lens;

      armed.set(true);
      Object value = lens.getObject(TRIGGER_ROW, FORMULA_COL);

      assertFalse(armed.get(), "the row table was not replaced during the batch");
      assertEquals(TRIGGER_ROW * 10.0, value instanceof Number ? ((Number) value).doubleValue()
         : value, "value of row " + TRIGGER_ROW);
      assertEquals(Collections.emptyMap(), readBack(lens), "after the replaced batch");
   }

   /**
    * A formula lens over a condition filter that is invalidated while the lens computes its
    * rows. The filter's invalidate() runs under its own monitor and invalidates the lens
    * through its change listener, while the lens's batch holds the lens lock and enters the
    * filter's monitor on every base read. invalidate() does not take the lens lock, so both
    * threads finish, and the lens is right once they do (bug #77243).
    */
   @Test
   public void invalidatingTheConditionFilterBaseWhileComputingDoesNotDeadlock() throws Exception {
      Sandbox s = harness.control();
      TableLens cf = LockCycleHarness.cf2(new SlowTable(ROWS, Slow.EVERYWHERE), s.box);
      FormulaTableLens lens = s.formula(cf, "f1", FORMULA);
      AtomicBoolean stop = new AtomicBoolean();
      Future<Integer> computer = harness.submit(() -> {
         int n = 0;

         try {
            for(long end = System.currentTimeMillis() + 2000;
                System.currentTimeMillis() < end; n++)
            {
               lens.moreRows(TableLens.EOT);
            }
         }
         finally {
            stop.set(true);
         }

         return n;
      });
      Future<Integer> invalidator = harness.submit(() -> {
         int n = 0;

         while(!stop.get()) {
            ((AbstractConditionFilter) cf).invalidate();
            n++;
            Thread.sleep(1);
         }

         return n;
      });

      assertTrue(harness.await(computer, LockCycleHarness.ACTIVE_CAP, "computer") > 0);
      assertTrue(harness.await(invalidator, LockCycleHarness.ACTIVE_CAP, "invalidator") > 0);
      lens.moreRows(TableLens.EOT);
      assertEquals(Collections.emptyMap(), readBack(lens), "after the race");
   }

   /**
    * Readers iterate rows that are already computed, which takes only the lens lock, while
    * another thread keeps invalidating the lens and a guest script holding the engine lock
    * reads it. A batch that checked a computed row table, and then found a new, empty one
    * published by invalidate(), used to compute its rows holding only the lens lock and take
    * the engine lock per row in exec(): the lens-then-engine order that deadlocks against the
    * guest (bug #76935). Every base read of a batch under the lens lock must already hold the
    * engine lock (bug #77243).
    */
   @Test
   public void computingRowsOfANewRowTableTakesTheEngineLockFirst() throws Exception {
      // a pooled env takes no engine lock
      assumeFalse(LockCycleHarness.POOL);
      Sandbox s = harness.control();
      ReentrantLock[] lensLock = new ReentrantLock[1];
      AtomicInteger violations = new AtomicInteger();
      SlowTable base = new SlowTable(ROWS, Slow.NONE) {
         @Override
         public boolean moreRows(int row) {
            if(lensLock[0] != null && lensLock[0].isHeldByCurrentThread() &&
               !s.lock.isHeldByCurrentThread())
            {
               violations.incrementAndGet();
            }

            return super.moreRows(row);
         }
      };
      FormulaTableLens lens = s.formula(base, "f1", FORMULA);
      lensLock[0] = lensLock(lens);
      lens.moreRows(TableLens.EOT);

      AtomicBoolean stop = new AtomicBoolean();
      List<Future<Map<String, Integer>>> tasks = new ArrayList<>();

      for(int t = 0; t < READERS; t++) {
         tasks.add(harness.submit(() -> {
            Map<String, Integer> bad = new TreeMap<>();

            while(!stop.get()) {
               for(int r = 1; r <= ROWS && !stop.get(); r++) {
                  try {
                     lens.moreRows(r);
                  }
                  catch(Throwable ex) {
                     bad.merge("iterator " + ex.getClass().getSimpleName(), 1, Integer::sum);
                  }
               }
            }

            return bad;
         }));
      }

      tasks.add(harness.submit(() -> {
         Map<String, Integer> bad = new TreeMap<>();
         Random random = new Random(7);

         while(!stop.get()) {
            try {
               s.asGuest(() -> lens.getObject(1 + random.nextInt(ROWS), FORMULA_COL));
            }
            catch(Throwable ex) {
               bad.merge("guest " + ex.getClass().getSimpleName(), 1, Integer::sum);
            }
         }

         return bad;
      }));

      Future<Integer> invalidator = harness.submit(() -> {
         int n = 0;

         try {
            for(long end = System.currentTimeMillis() + DURATION_MS;
                System.currentTimeMillis() < end; n++)
            {
               lens.invalidate();
               Thread.yield();
            }
         }
         finally {
            stop.set(true);
         }

         return n;
      });

      int invalidations = harness.await(invalidator, LockCycleHarness.ACTIVE_CAP, "invalidator");
      Map<String, Integer> bad = new TreeMap<>();

      for(Future<Map<String, Integer>> task : tasks) {
         harness.await(task, LockCycleHarness.ACTIVE_CAP, "reader")
            .forEach((k, v) -> bad.merge(k, v, Integer::sum));
      }

      if(violations.get() > 0) {
         bad.put("base read under the lens lock without the engine lock", violations.get());
      }

      String summary = "invalidations=" + invalidations + " bad=" + bad;
      assertTrue(invalidations > 0, summary);
      assertEquals(Collections.emptyMap(), bad, summary);
      lens.moreRows(TableLens.EOT);
      assertEquals(Collections.emptyMap(), readBack(lens), "after the race");
   }

   /**
    * A lens that has computed all its rows ignores cancel(): it may be shared or cached, and
    * a consumer's cancel must not empty it for the others. That still holds after it is
    * invalidated and before its rows are computed again (bug #77243).
    */
   @Test
   public void cancelAfterInvalidateDoesNotEmptyACompletedLens() {
      Sandbox s = harness.control();
      FormulaTableLens lens = s.formula(new SlowTable(ROWS, Slow.NONE), "f1", FORMULA);
      lens.moreRows(TableLens.EOT);
      lens.invalidate();
      lens.cancel();

      assertFalse(lens.isCancelled(), "cancelled");
      assertEquals(Collections.emptyMap(), readBack(lens), "after the cancel");
   }

   /**
    * The same race as {@link #readsDuringInvalidateReturnTheRowsOwnValue}, with a formula that
    * reads its own column on the previous row, so a batch reads earlier rows of the row table
    * it computes while invalidate() replaces it. Every row must hold its running total.
    */
   @Test
   public void earlierRowReadsDuringInvalidateReturnTheRowsOwnValue() throws Exception {
      Sandbox s = harness.control();
      FormulaTableLens lens = s.formula(new SlowTable(ROWS, Slow.NONE), "f1", RUNNING);
      lens.moreRows(TableLens.EOT);
      assertEquals(Collections.emptyMap(), readBack(lens, runningTotals()),
                   "before the race");

      AtomicBoolean stop = new AtomicBoolean();
      Future<Map<String, Integer>> invalidator = harness.submit(() -> {
         Map<String, Integer> bad = new TreeMap<>();
         int n = 0;

         try {
            for(long end = System.currentTimeMillis() + DURATION_MS;
                System.currentTimeMillis() < end; n++)
            {
               try {
                  lens.invalidate();
                  lens.moreRows(TableLens.EOT);
                  readBack(lens, runningTotals())
                     .forEach((k, v) -> bad.merge("read-back " + k, v, Integer::sum));
               }
               catch(Throwable ex) {
                  bad.merge("invalidator " + ex.getClass().getSimpleName(), 1, Integer::sum);
               }
            }
         }
         finally {
            stop.set(true);
         }

         bad.put("#invalidations", n);
         return bad;
      });
      List<Future<Map<String, Integer>>> readers = new ArrayList<>();

      for(int i = 0; i < READERS; i++) {
         final int seed = i;
         readers.add(harness.submit(() -> {
            Map<String, Integer> bad = new TreeMap<>();
            Random random = new Random(seed);

            while(!stop.get()) {
               int r = 1 + random.nextInt(ROWS);
               check(lens, r, runningTotals()[r], bad, "reader ");
            }

            return bad;
         }));
      }

      Map<String, Integer> bad = new TreeMap<>(
         harness.await(invalidator, LockCycleHarness.ACTIVE_CAP, "invalidator"));

      for(Future<Map<String, Integer>> reader : readers) {
         harness.await(reader, LockCycleHarness.ACTIVE_CAP, "reader")
            .forEach((k, v) -> bad.merge(k, v, Integer::sum));
      }

      int invalidations = bad.remove("#invalidations");
      String summary = "invalidations=" + invalidations + " bad=" + bad;
      assertTrue(invalidations > 0, summary);
      assertEquals(Collections.emptyMap(), bad, summary);
      assertEquals(Collections.emptyMap(), readBack(lens, runningTotals()),
                   "after the race");
   }

   /**
    * The expected values of {@link #RUNNING}, by lens row: the running total of id * 10.
    */
   private static double[] runningTotals() {
      double[] expected = new double[ROWS + 1];

      for(int r = 1; r <= ROWS; r++) {
         expected[r] = expected[r - 1] + r * 10.0;
      }

      return expected;
   }

   private static ReentrantLock lensLock(FormulaTableLens lens) throws Exception {
      Field field = FormulaTableLens.class.getDeclaredField("lock");
      field.setAccessible(true);
      return (ReentrantLock) field.get(lens);
   }

   /**
    * Read every data row of {@code lens}.
    *
    * @return the nulls, wrong values and exceptions, by kind.
    */
   private static Map<String, Integer> readBack(FormulaTableLens lens) {
      double[] expected = new double[ROWS + 1];

      for(int r = 1; r <= ROWS; r++) {
         expected[r] = r * 10.0;
      }

      return readBack(lens, expected);
   }

   /**
    * Read every data row of {@code lens}, expecting {@code expected[row]}.
    *
    * @return the nulls, wrong values and exceptions, by kind.
    */
   private static Map<String, Integer> readBack(FormulaTableLens lens, double[] expected) {
      Map<String, Integer> bad = new TreeMap<>();

      for(int r = 1; r <= ROWS; r++) {
         check(lens, r, expected[r], bad, "");
      }

      return bad;
   }

   private static void check(FormulaTableLens lens, int r, Map<String, Integer> bad,
                             String prefix)
   {
      check(lens, r, r * 10.0, bad, prefix);
   }

   private static void check(FormulaTableLens lens, int r, double expected,
                             Map<String, Integer> bad, String prefix)
   {
      try {
         Object value = lens.getObject(r, FORMULA_COL);

         if(value == null) {
            bad.merge(prefix + "null", 1, Integer::sum);
         }
         else if(!(value instanceof Number) || ((Number) value).doubleValue() != expected) {
            // -1 is the base header row: the rows were shifted by one
            bad.merge(prefix + (value instanceof Number && ((Number) value).doubleValue() == -1
               ? "header value" : "wrong value"), 1, Integer::sum);
         }
      }
      catch(Throwable ex) {
         bad.merge(prefix + ex.getClass().getSimpleName(), 1, Integer::sum);
      }
   }

   private LockCycleHarness harness;

   private static final int ROWS = 300;
   private static final int READERS = 2;
   private static final long DURATION_MS = 4000;
   // the lens column of the formula, after the base's group, value and id
   private static final int FORMULA_COL = 3;
   // the row whose read has the row table replaced during its batch
   private static final int TRIGGER_ROW = 50;
   private static final String FORMULA =
      "field['id'] == null ? -2 : (typeof field['id'] == 'string' ? -1 : field['id'] * 10)";
   // the running total of id * 10, reading this column on the previous row
   private static final String RUNNING =
      "field['id'] * 10 + (field['id'] == 1 ? 0 : field[-1]['f1'])";
}
