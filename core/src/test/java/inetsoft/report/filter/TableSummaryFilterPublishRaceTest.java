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
import inetsoft.report.composition.execution.PostProcessor;
import inetsoft.report.lens.DefaultTableLens;
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
 * Concurrent first readers of a summary-only table summary filter all get the summary, never
 * a null or a summary accumulated twice (bug #77365).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class TableSummaryFilterPublishRaceTest {
   /**
    * A reader that arrives while the first reader builds the summary gets the summary, and the
    * summary is not accumulated twice into the shared formula.
    */
   @Test
   public void readDuringSummaryBuildIsNotAccumulatedTwice() throws Exception {
      Object expected = summary(new DefaultTableLens(data()), new SumFormula());
      List<String> failures = new ArrayList<>();

      for(int run = 0; run < GATED_RUNS; run++) {
         GatedTable base = new GatedTable(data(), ROWS, 1);
         TableLens filter = PostProcessor.tableSummary(base, new int[] { 1 },
                                                       new Formula[] { new SumFormula() });
         base.gate.arm(null);

         CompletableFuture<Object> first = read(filter);
         base.gate.awaitParked();
         CompletableFuture<Object> secondRead = read(filter);
         awaitDoneOrWaiting(secondRead);
         base.gate.open();
         Object firstValue = first.get(JOIN_MS, TimeUnit.MILLISECONDS);
         Object second = secondRead.get(JOIN_MS, TimeUnit.MILLISECONDS);
         Object after = filter.getObject(1, 1);

         if(!expected.equals(firstValue) || !expected.equals(second) ||
            !expected.equals(after))
         {
            failures.add("run " + run + ": first=" + firstValue + " second=" + second +
                            " after=" + after);
         }
      }

      assertTrue(failures.isEmpty(), failures.size() + "/" + GATED_RUNS +
         " runs did not return the summary " + expected + ": " + failures);
   }

   /**
    * A reader that arrives while the first reader fills the summary row gets the summary, not
    * the empty cell of a row published before it is filled.
    */
   @Test
   public void readDuringSummaryFillReturnsSummary() throws Exception {
      Object expected = summary(new DefaultTableLens(data()), new SumFormula());
      List<String> failures = new ArrayList<>();

      for(int run = 0; run < GATED_RUNS; run++) {
         GatedFormula.Gate gate = new GatedFormula.Gate();
         TableLens filter = PostProcessor.tableSummary(new DefaultTableLens(data()),
                                                       new int[] { 1 },
                                                       new Formula[] { new GatedFormula(gate) });
         gate.arm(null);

         CompletableFuture<Object> first = read(filter);
         gate.awaitParked();
         CompletableFuture<Object> second = read(filter);
         awaitDoneOrWaiting(second);
         gate.open();
         Object firstValue = first.get(JOIN_MS, TimeUnit.MILLISECONDS);
         Object secondValue = second.get(JOIN_MS, TimeUnit.MILLISECONDS);

         if(!expected.equals(firstValue) || !expected.equals(secondValue)) {
            failures.add("run " + run + ": first=" + firstValue + " second=" + secondValue);
         }
      }

      assertTrue(failures.isEmpty(), failures.size() + "/" + GATED_RUNS +
         " runs did not return the summary " + expected + ": " + failures);
   }

   /**
    * Concurrent first readers of a fresh filter all get the summary (no gate).
    */
   @Test
   public void concurrentFirstReadersReturnSummary() throws Exception {
      Object expected = summary(new DefaultTableLens(data()), new SumFormula());
      ExecutorService pool = Executors.newFixedThreadPool(READERS);
      Map<String, Integer> results = new TreeMap<>();
      int trials = 0;

      try {
         for(; trials < TRIALS; trials++) {
            TableLens filter = PostProcessor.tableSummary(new DefaultTableLens(data()),
                                                          new int[] { 1 },
                                                          new Formula[] { new SumFormula() });
            CountDownLatch start = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();

            for(int i = 0; i < READERS; i++) {
               futures.add(pool.submit(() -> {
                  start.await();

                  try {
                     Object value = filter.getObject(1, 1);
                     return expected.equals(value) ? "ok" :
                        value == null ? "null" : "wrong";
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

   private static Object[][] data() {
      Object[][] data = new Object[ROWS + 1][];
      data[0] = new Object[] { "id", "value" };

      for(int b = 1; b <= ROWS; b++) {
         data[b] = new Object[] { b, b };
      }

      return data;
   }

   private static Object summary(TableLens base, Formula formula) {
      return PostProcessor.tableSummary(base, new int[] { 1 }, new Formula[] { formula })
         .getObject(1, 1);
   }

   // the summary-only filter has one header row, so the summary is row 1
   private static CompletableFuture<Object> read(TableLens filter) {
      CompletableFuture<Object> result = new CompletableFuture<>();
      Thread thread = new Thread(() -> {
         try {
            result.complete(filter.getObject(1, 1));
         }
         catch(Throwable ex) {
            result.complete(ex.toString());
         }
      }, "TableSummaryFilterPublishRaceTest-reader");
      thread.setDaemon(true);
      thread.start();
      READERS_BY_RESULT.put(result, thread);
      return result;
   }

   // the read is done, or waits for the parked reader
   private static void awaitDoneOrWaiting(CompletableFuture<Object> read)
      throws InterruptedException
   {
      Thread thread = READERS_BY_RESULT.get(read);
      long end = System.currentTimeMillis() + JOIN_MS;

      while(!read.isDone() && System.currentTimeMillis() < end) {
         Thread.State state = thread.getState();

         if(state == Thread.State.BLOCKED || state == Thread.State.WAITING ||
            state == Thread.State.TIMED_WAITING)
         {
            return;
         }

         Thread.sleep(1);
      }
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

   private static final Map<CompletableFuture<Object>, Thread> READERS_BY_RESULT =
      new ConcurrentHashMap<>();
   private static final int ROWS = 2000;
   private static final int GATED_RUNS = 20;
   private static final int TRIALS = 2000;
   private static final int READERS = 4;
   private static final long JOIN_MS = 10000;
}
