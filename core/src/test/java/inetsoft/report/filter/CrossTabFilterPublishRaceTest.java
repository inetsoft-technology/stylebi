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

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A crosstab read while its first pass is still running returns the finished crosstab, and an
 * invalidate() that lands while a pass is running is not lost (bug #77365).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class CrossTabFilterPublishRaceTest {
   /**
    * A first reader that arrives while the pass fills the crosstab gets the filled cells, not
    * the empty cells of an array published before it is filled.
    */
   @Test
   public void readDuringFirstPassReturnsFilledCells() throws Exception {
      List<List<Object>> expected = cells(crosstab(new DefaultTableLens(data(ROWS)),
                                                   new SumFormula()));
      List<String> failures = new ArrayList<>();

      for(int run = 0; run < GATED_RUNS; run++) {
         GatedFormula.Gate gate = new GatedFormula.Gate();
         CrossTabFilter filter = crosstab(new DefaultTableLens(data(ROWS)),
                                          new GatedFormula(gate));
         // park the pass only once it has published its data array
         gate.arm(() -> getData(filter) != null);

         Thread pass = start(filter::checkInit, "pass");
         gate.awaitParked();

         Reader reader = new Reader(() -> cells(filter));
         reader.startAndAwaitDoneOrBlocked();
         gate.open();
         pass.join(JOIN_MS);
         List<List<Object>> actual = reader.get();

         assertFalse(pass.isAlive(), "the pass did not finish");

         if(!expected.equals(actual)) {
            failures.add("run " + run + ": " + diff(expected, actual));
         }
      }

      assertTrue(failures.isEmpty(), failures.size() + "/" + GATED_RUNS +
         " runs returned a crosstab that is not the finished one: " + failures);
   }

   /**
    * An invalidate() that lands while the pass aggregates a row TopN crosstab neither wipes
    * the totals of that pass nor is lost.
    */
   @Test
   public void invalidateDuringTopNAggregationKeepsTotals() throws Exception {
      Supplier<CrossTabFilter> fresh = () -> topN(new DefaultTableLens(data(ROWS)));
      List<List<Object>> expected = cells(fresh.get());
      List<String> failures = new ArrayList<>();
      int afterReinvalidate = 0;

      for(int run = 0; run < GATED_RUNS; run++) {
         GatedTable base = new GatedTable(data(ROWS), ROWS, VALUE_COL);
         CrossTabFilter filter = topN(base);
         base.gate.arm(null);

         Thread pass = start(filter::checkInit, "pass");
         base.gate.awaitParked();
         Object dataAtInvalidate = getData(filter);
         invalidateWithoutBlocking(filter, base.gate);
         base.gate.open();
         pass.join(JOIN_MS);
         assertFalse(pass.isAlive(), "the pass did not finish");
         assertNull(dataAtInvalidate, "the gate is in the aggregation phase");

         List<List<Object>> actual = cells(filter);

         if(!expected.equals(actual)) {
            failures.add("run " + run + ": " + diff(expected, actual));
         }

         filter.invalidate();

         if(expected.equals(cells(filter))) {
            afterReinvalidate++;
         }
      }

      assertEquals(GATED_RUNS, afterReinvalidate, "an invalidate() with no pass running " +
         "recomputes the crosstab");
      assertTrue(failures.isEmpty(), failures.size() + "/" + GATED_RUNS +
         " runs returned wrong cells after an invalidate() during the pass: " + failures);
   }

   /**
    * A base change (and its invalidate()) that lands while the pass aggregates is picked up,
    * not swallowed by the pass publishing its result.
    */
   @Test
   public void baseChangeDuringAggregationIsNotLost() throws Exception {
      Object[][] changed = data(ROWS);
      changed[1][VALUE_COL] = CHANGED_VALUE;
      List<List<Object>> expected = cells(crosstab(new DefaultTableLens(changed),
                                                   new SumFormula()));
      List<String> failures = new ArrayList<>();

      for(int run = 0; run < GATED_RUNS; run++) {
         GatedTable base = new GatedTable(data(ROWS), ROWS, VALUE_COL);
         CrossTabFilter filter = crosstab(base, new SumFormula());
         base.gate.arm(null);

         Thread pass = start(filter::checkInit, "pass");
         base.gate.awaitParked();
         assertNull(getData(filter), "the gate is in the aggregation phase");
         // row 1 was aggregated already. invalidate as the change event does: the event's
         // listener is only weakly held by the base, so after a gc the event may not come
         Thread change = start(() -> {
            base.setObject(1, VALUE_COL, CHANGED_VALUE);
            filter.invalidate();
         }, "change");
         change.join(JOIN_MS);

         if(change.isAlive()) {
            base.gate.open();
            fail("the base change blocked on the running pass");
         }

         base.gate.open();
         pass.join(JOIN_MS);
         assertFalse(pass.isAlive(), "the pass did not finish");

         List<List<Object>> actual = cells(filter);

         if(!expected.equals(actual)) {
            failures.add("run " + run + ": " + diff(expected, actual));
         }
      }

      assertTrue(failures.isEmpty(), failures.size() + "/" + GATED_RUNS +
         " runs kept the values from before the base change: " + failures);
   }

   /**
    * A read concurrent with an invalidate(); getRowCount() loop returns the crosstab cell,
    * never null, the grand total label or an exception.
    */
   @Test
   public void readsDuringInvalidateReturnCell() throws Exception {
      CrossTabFilter filter = crosstab(new DefaultTableLens(data(ROWS)), new SumFormula());
      List<List<Object>> expected = cells(filter);
      AtomicBoolean done = new AtomicBoolean();
      AtomicLong invalidations = new AtomicLong();
      Map<String, Integer> results = new ConcurrentHashMap<>();

      Thread invalidator = new Thread(() -> {
         while(!done.get()) {
            filter.invalidate();
            filter.getRowCount();
            invalidations.incrementAndGet();
         }
      }, "CrossTabFilterPublishRaceTest-invalidator");

      Thread[] readers = new Thread[READERS];

      for(int i = 0; i < readers.length; i++) {
         readers[i] = new Thread(() -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();

            while(!done.get()) {
               // a body cell of the crosstab
               int r = 1 + random.nextInt(expected.size() - 2);
               int c = 1 + random.nextInt(expected.get(0).size() - 2);
               String result;

               try {
                  Object value = filter.getObject(r, c);
                  result = Objects.equals(expected.get(r).get(c), value) ? "ok" :
                     value == null ? "null" : "wrong " + value;
               }
               catch(Throwable ex) {
                  result = ex.getClass().getSimpleName();
               }

               results.merge(result, 1, Integer::sum);
            }
         }, "CrossTabFilterPublishRaceTest-reader-" + i);
      }

      invalidator.start();

      for(Thread reader : readers) {
         reader.start();
      }

      Thread.sleep(STRESS_MS);
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

   // row header r0..r(RGROUPS-1), column header c0..c(CGROUPS-1), value b
   private static Object[][] data(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "row", "col", "value" };

      for(int b = 1; b <= rows; b++) {
         data[b] = new Object[] { "r" + (b % RGROUPS), "c" + (b % CGROUPS), b };
      }

      return data;
   }

   private static CrossTabFilter crosstab(DefaultTableLens base, Formula formula) {
      return new CrossTabFilter(base, new int[] { 0 }, new int[] { 1 },
                                new int[] { VALUE_COL }, new Formula[] { formula });
   }

   // top 2 rows by the sum, with Others
   private static CrossTabFilter topN(DefaultTableLens base) {
      CrossTabFilter filter = crosstab(base, new SumFormula());
      filter.setRowTopN(0, 0, 2, false, true);
      return filter;
   }

   private static List<List<Object>> cells(CrossTabFilter filter) {
      List<List<Object>> cells = new ArrayList<>();

      for(int r = 0; filter.moreRows(r); r++) {
         List<Object> row = new ArrayList<>();

         for(int c = 0; c < filter.getColCount(); c++) {
            row.add(filter.getObject(r, c));
         }

         cells.add(row);
      }

      return cells;
   }

   private static String diff(List<List<Object>> expected, List<List<Object>> actual) {
      if(expected.size() != actual.size()) {
         return "rows " + actual.size() + " != " + expected.size() + ": " + actual;
      }

      StringBuilder sb = new StringBuilder();

      for(int r = 0; r < expected.size(); r++) {
         if(!expected.get(r).equals(actual.get(r))) {
            sb.append(" row ").append(r).append(' ').append(actual.get(r)).append(" != ")
               .append(expected.get(r));
         }
      }

      return sb.toString();
   }

   private static Object getData(CrossTabFilter filter) {
      try {
         return DATA.get(filter);
      }
      catch(IllegalAccessException ex) {
         throw new IllegalStateException(ex);
      }
   }

   // invalidate() must not wait for the pass it lands in
   private static void invalidateWithoutBlocking(CrossTabFilter filter, GatedFormula.Gate gate)
      throws InterruptedException
   {
      Thread invalidate = start(filter::invalidate, "invalidate");
      invalidate.join(JOIN_MS);

      if(invalidate.isAlive()) {
         gate.open();
         fail("invalidate() blocked on the running pass");
      }
   }

   private static Thread start(Runnable runnable, String name) {
      Thread thread = new Thread(runnable, "CrossTabFilterPublishRaceTest-" + name);
      thread.setDaemon(true);
      thread.start();
      return thread;
   }

   /**
    * Reads on its own thread. Waits until the read is done or the reader waits (for the
    * parked pass), so the read is known to have run while the pass was parked.
    */
   private static final class Reader {
      Reader(Supplier<List<List<Object>>> read) {
         thread = start(() -> {
            try {
               result.complete(read.get());
            }
            catch(Throwable ex) {
               result.completeExceptionally(ex);
            }
         }, "reader");
      }

      void startAndAwaitDoneOrBlocked() throws InterruptedException {
         long end = System.currentTimeMillis() + JOIN_MS;

         while(!result.isDone() && System.currentTimeMillis() < end) {
            Thread.State state = thread.getState();

            if(state == Thread.State.BLOCKED || state == Thread.State.WAITING ||
               state == Thread.State.TIMED_WAITING)
            {
               return;
            }

            Thread.sleep(1);
         }
      }

      List<List<Object>> get() throws Exception {
         try {
            return result.get(JOIN_MS, TimeUnit.MILLISECONDS);
         }
         catch(ExecutionException ex) {
            // report an exception as the result that differs from the finished crosstab
            return List.of(List.of(ex.getCause().toString()));
         }
      }

      private final Thread thread;
      private final CompletableFuture<List<List<Object>>> result = new CompletableFuture<>();
   }

   /**
    * A base whose first read of (row, col) once armed parks until the gate opens.
    */
   private static final class GatedTable extends DefaultTableLens {
      GatedTable(Object[][] data, int row, int col) {
         super(data);
         this.row = row;
         this.col = col;
      }

      @Override
      public Object getObject(int r, int c) {
         if(r == row && c == col) {
            gate.park();
         }

         return super.getObject(r, c);
      }

      final GatedFormula.Gate gate = new GatedFormula.Gate();
      private final int row;
      private final int col;
   }

   private static final Field DATA;

   static {
      try {
         DATA = CrossTabFilter.class.getDeclaredField("data");
         DATA.setAccessible(true);
      }
      catch(NoSuchFieldException ex) {
         throw new ExceptionInInitializerError(ex);
      }
   }

   private static final int ROWS = 600;
   private static final int RGROUPS = 20;
   private static final int CGROUPS = 6;
   private static final int VALUE_COL = 2;
   private static final int CHANGED_VALUE = 100000;
   private static final int GATED_RUNS = 20;
   private static final int READERS = 4;
   private static final long STRESS_MS = 4000;
   private static final long JOIN_MS = 10000;
}
