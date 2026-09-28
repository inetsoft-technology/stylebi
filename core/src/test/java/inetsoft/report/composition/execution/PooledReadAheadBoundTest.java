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
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.DistinctTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.test.*;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.SlotClaim;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static inetsoft.report.composition.execution.PoolOffConditionFilterLockingTest.allRows;
import static inetsoft.report.composition.execution.PooledBatchClaimTest.poolBox;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pool-on read-ahead (context-pool regression brief D1, final review M5): a pooled formula
 * lens, or a condition filter over one, evaluates scripts for about the rows pool off would
 * on a first, bounded or random read; batches grow only while a table is read row by row. A
 * condition filter never reads ahead on its own, and asks its base for the rows a population
 * needs in one read, so the formula lens below sees one bounded request, not an open scan.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class PooledReadAheadBoundTest {
   @Test
   public void formulaMoreRowsReadsWhatPoolOffReads() {
      assertEquals(100, baseRead(false, false, ONCE_100), "pool off");
      assertEquals(100, baseRead(true, false, ONCE_100), "pool on");
   }

   @Test
   public void formulaPagesReadWhatPoolOffReads() {
      assertEquals(1000, baseRead(false, false, PAGES), "pool off");
      assertEquals(1000, baseRead(true, false, PAGES), "pool on");
   }

   @Test
   public void formulaJumpReadsWhatPoolOffReads() {
      assertEquals(5100, baseRead(false, false, JUMP), "pool off");
      assertEquals(5100, baseRead(true, false, JUMP), "pool on");
   }

   @Test
   public void filterOverFormulaMoreRowsReadsNearPoolOff() {
      int off = baseRead(false, true, ONCE_100);
      int on = baseRead(true, true, ONCE_100);
      assertTrue(on <= off, "pool on read base row " + on + ", pool off " + off);
   }

   @Test
   public void filterOverFormulaPagesReadNearPoolOff() {
      int off = baseRead(false, true, PAGES);
      int on = baseRead(true, true, PAGES);
      assertTrue(on <= off, "pool on read base row " + on + ", pool off " + off);
   }

   @Test
   public void filterOverFormulaJumpReadsNearPoolOff() {
      int off = baseRead(false, true, JUMP);
      int on = baseRead(true, true, JUMP);
      assertTrue(on <= off, "pool on read base row " + on + ", pool off " + off);
   }

   /**
    * A random jump followed by a row-by-row read (a viewsheet scrolled to a deep row, then
    * read on) stays near pool off, bare and under a filter.
    */
   @Test
   public void jumpThenRowByRowReadStaysNearPoolOff() {
      Consumer<TableLens> read = lens -> {
         lens.moreRows(5000);

         for(int r = 5001; r <= 5100 && lens.moreRows(r); r++) {
            lens.getObject(r, 3);
         }
      };

      for(boolean filter : new boolean[] { false, true }) {
         int off = baseRead(false, filter, read);
         int on = baseRead(true, filter, read);
         assertTrue(on <= off + 25, "filter " + filter + ": pool on read base row " + on +
                       ", pool off " + off);
      }
   }

   /**
    * The filter's own read-ahead no longer compounds with the formula lens's sequential
    * growth (a row-by-row reader of N rows evaluates at most about 2N + 10).
    */
   @Test
   public void filterOverFormulaRowByRowReadStaysNearTwiceTheRows() {
      int on = baseRead(true, true, SEQ_100);
      assertTrue(on <= 200, "pool on read base row " + on);
   }

   /**
    * The D1 visible consequence: a formula that fails on row 150 fails a pool-on read of the
    * first 100 rows only if pool off would.
    */
   @Test
   public void formulaErrorPastTheRequestedRowsDoesNotFail() {
      String expr = "if(field['id'] == 150) { throw 'row 150'; } field['value'] + 1";

      for(boolean pool : new boolean[] { false, true }) {
         for(boolean filter : new boolean[] { false, true }) {
            for(Consumer<TableLens> read : List.of(ONCE_100, FIRST_PAGE)) {
               Chain chain = new Chain(pool, filter, expr);
               assertDoesNotThrow(() -> read.accept(chain.top),
                                  "pool " + pool + ", filter " + filter);
            }
         }
      }
   }

   /**
    * Side effects on rows nobody asked for: a script that counts its rows in a Java object
    * runs for the requested rows, and for the filter no more than a few rows past them.
    */
   @Test
   public void sideEffectsRunForAboutTheRequestedRows() {
      for(boolean pool : new boolean[] { false, true }) {
         Counter counter = new Counter();
         Chain bare = new Chain(pool, false, COUNTING, counter);
         ONCE_100.accept(bare.top);
         assertEquals(100, counter.hits.get(), "bare formula, pool " + pool);

         counter = new Counter();
         Chain filtered = new Chain(pool, true, COUNTING, counter);
         ONCE_100.accept(filtered.top);
         assertTrue(counter.hits.get() <= 110, "filter, pool " + pool + ": " + counter.hits);
      }
   }

   /**
    * Review r1 finding 1: a bounded read followed by a re-read of its rows with moreRows(r)
    * (the usual paged loop) is not a sequential read. Re-reading the last computed row must
    * not start the next pooled batch, so no row past the page is computed.
    */
   @Test
   public void reReadingAPageComputesNoRowPastIt() {
      for(boolean filter : new boolean[] { false, true }) {
         for(Consumer<TableLens> read : List.of(REPROBE_FIRST_PAGE, REPROBE_DEEP_PAGE)) {
            int off = baseRead(false, filter, read);
            int on = baseRead(true, filter, read);
            assertTrue(on <= off, "filter " + filter + ": pool on read base row " + on +
                          ", pool off " + off);

            if(!filter) {
               assertEquals(read == REPROBE_FIRST_PAGE ? 100 : 5000, on, "bare formula");
            }
         }
      }
   }

   /**
    * Review r1 finding 1, the visible consequence: a formula that fails on the first row past
    * what pool off reads for a page re-read with moreRows(r) fails neither pool off nor on.
    */
   @Test
   public void formulaErrorJustPastAReReadPageDoesNotFail() {
      for(boolean filter : new boolean[] { false, true }) {
         for(Consumer<TableLens> read : List.of(REPROBE_FIRST_PAGE, REPROBE_DEEP_PAGE)) {
            int errorRow = baseRead(false, filter, read) + 1;
            String expr = "if(field['id'] == " + errorRow + ") { throw 'row " + errorRow +
               "'; } field['value'] + 1";

            for(boolean pool : new boolean[] { false, true }) {
               Chain chain = new Chain(pool, filter, expr);
               assertDoesNotThrow(() -> read.accept(chain.top),
                                  "pool " + pool + ", filter " + filter + ", row " + errorRow);
            }
         }
      }
   }

   /**
    * Review r1 finding 2: a far or EOT population of a pooled filter asks the formula lens
    * below for its rows in reads of at most maxBatchRows rows, so one lens-lock hold covers
    * no more than one pooled batch; every row is still mapped, with the right values.
    */
   @Test
   public void farFilterPopulationReadsTheFormulaInBoundedBatches() {
      PoolConfig def = PoolConfig.defaults();
      PoolConfig config = new PoolConfig(def.idleMillis(), def.cleanThreshold(),
                                         def.warnSlotsPerSandbox(), def.warnSlotsPerNode(),
                                         def.batchRows(), 1000);
      WorksheetScriptEnv env = PoolTestSupport.env(config, java.util.Map.of());
      int rows = 5000;
      long[] deepest = new long[1];
      long[] largestJump = new long[1];
      FormulaTableLens formula = new FormulaTableLens(
         PooledBatchClaimTest.table(rows), new String[] {"f"},
         new String[] {"field['value'] + 1"}, env, null)
      {
         @Override
         public boolean moreRows(int row) {
            // an EOT read is one unbounded batch
            long target = row == EOT ? Integer.MAX_VALUE : row;
            largestJump[0] = Math.max(largestJump[0], target - deepest[0]);
            deepest[0] = Math.max(deepest[0], target);
            return super.moreRows(row);
         }
      };
      TableLens filter = PostProcessor.filter(formula, allRows(), poolBox(env));

      try {
         assertFalse(filter.moreRows(Integer.MAX_VALUE));
         assertTrue(largestJump[0] <= 1000, "largest single read " + largestJump[0]);
         assertEquals(rows + 1, filter.getRowCount());

         for(int r = 1; r <= rows; r++) {
            assertEquals((r % 30) + 1, ((Number) filter.getObject(r, 3)).intValue(), "row " + r);
         }

         assertEquals(0, SlotClaim.openClaims());
      }
      finally {
         env.retire();
      }
   }

   /**
    * M5: a pooled filter over a script-free lens asks it for the rows pool off asks for.
    */
   @Test
   public void filterOverScriptFreeLensReadsWhatPoolOffReads() {
      for(boolean pool : new boolean[] { false, true }) {
         int[] asked = new int[1];
         DistinctTableLens distinct = new DistinctTableLens(PooledBatchClaimTest.table(5000)) {
            @Override
            public boolean moreRows(int row) {
               if(row != EOT) {
                  asked[0] = Math.max(asked[0], row);
               }

               return super.moreRows(row);
            }
         };
         TableLens filter = PostProcessor.filter(distinct, allRows(), box(env(pool), pool));

         assertTrue(filter.moreRows(100));
         assertEquals(100, asked[0], "pool " + pool);
         assertEquals(0, SlotClaim.openClaims());
      }
   }

   /**
    * P1 (Testing #77123): a table's top-level var keeps counting from row to row with the
    * smaller pooled batches, under a filter and bare, read row by row, by page or at once.
    */
   @Test
   public void formulaVarAccumulatorCountsEveryRowWithSmallBatches() throws Exception {
      AssetQuerySandbox box = PoolTestSupport.poolBox(true);
      WorksheetScriptEnv env = (WorksheetScriptEnv) box.getScriptEnv();

      try {
         for(boolean filter : new boolean[] { false, true }) {
            for(int mode = 0; mode < 3; mode++) {
               TableLens lens = PostProcessor.formula(
                  PooledBatchClaimTest.table(3000), new String[] { "out" },
                  new String[] { "var acc = (acc || 0) + 1; acc" }, env, box.getScope(), null,
                  "T", null, List.of(Double.class), new boolean[] { false });

               if(filter) {
                  lens = PostProcessor.filter(lens, allRows(), box);
               }

               if(mode == 1) {
                  for(int s = 1; s <= 2901; s += 100) {
                     assertTrue(lens.moreRows(s + 99));
                  }
               }
               else if(mode == 2) {
                  assertFalse(lens.moreRows(Integer.MAX_VALUE));
               }

               for(int r = 1; r <= 3000; r++) {
                  assertTrue(lens.moreRows(r));
                  assertEquals(r, ((Number) lens.getObject(r, 3)).doubleValue(),
                               "filter " + filter + ", mode " + mode + ", row " + r);
               }
            }
         }
      }
      finally {
         env.retire();
      }
   }

   private static void reprobe(TableLens lens, int start, int end) {
      lens.moreRows(end);

      for(int r = start; r <= end && lens.moreRows(r); r++) {
         lens.getObject(r, 3);
      }
   }

   private static int baseRead(boolean pool, boolean filter, Consumer<TableLens> read) {
      Chain chain = new Chain(pool, filter, "field['value'] + 1");
      read.accept(chain.top);
      return chain.deepest[0];
   }

   private static ScriptEnv env(boolean pool) {
      if(pool) {
         return PoolTestSupport.env();
      }

      GraalJavaScriptEnv env = new GraalJavaScriptEnv();
      env.init();
      return env;
   }

   private static AssetQuerySandbox box(ScriptEnv env, boolean pool) {
      if(pool) {
         return poolBox(env);
      }

      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.peekScriptEnv()).thenReturn(env);
      when(box.isScriptPoolMode()).thenReturn(false);
      return box;
   }

   /**
    * A formula lens over a base that records the deepest row asked of it, optionally under
    * a condition filter that keeps every row.
    */
   private static final class Chain {
      Chain(boolean pool, boolean filter, String expr) {
         this(pool, filter, expr, null);
      }

      Chain(boolean pool, boolean filter, String expr, Counter counter) {
         ScriptEnv env = env(pool);

         if(counter != null) {
            env.put("counter", counter);
         }

         DefaultTableLens base = new DefaultTableLens(PooledBatchClaimTest.table(ROWS)) {
            @Override
            public boolean moreRows(int row) {
               if(row != EOT) {
                  deepest[0] = Math.max(deepest[0], row);
               }

               return super.moreRows(row);
            }
         };
         TableLens lens = new FormulaTableLens(base, new String[] {"f"}, new String[] {expr},
                                               env, null);
         top = filter ? PostProcessor.filter(lens, allRows(), box(env, pool)) : lens;
      }

      final int[] deepest = new int[1];
      final TableLens top;
   }

   /**
    * A host object a script calls once per row it runs for.
    */
   public static final class Counter {
      public int hit() {
         return hits.incrementAndGet();
      }

      final AtomicInteger hits = new AtomicInteger();
   }

   private static final Consumer<TableLens> ONCE_100 = lens -> lens.moreRows(100);
   private static final Consumer<TableLens> FIRST_PAGE = lens -> {
      lens.moreRows(100);

      for(int r = 1; r <= 100; r++) {
         lens.getObject(r, 3);
      }
   };
   private static final Consumer<TableLens> SEQ_100 = lens -> {
      for(int r = 1; r <= 100 && lens.moreRows(r); r++) {
         lens.getObject(r, 3);
      }
   };
   private static final Consumer<TableLens> PAGES = lens -> {
      for(int s = 1; s <= 901; s += 100) {
         lens.moreRows(s + 99);

         for(int r = s; r <= s + 99; r++) {
            lens.getObject(r, 3);
         }
      }
   };
   // the usual paged loop: a bounded read, then each row checked with moreRows(r)
   private static final Consumer<TableLens> REPROBE_FIRST_PAGE = lens -> reprobe(lens, 1, 100);
   private static final Consumer<TableLens> REPROBE_DEEP_PAGE = lens -> reprobe(lens, 4901, 5000);
   private static final Consumer<TableLens> JUMP = lens -> {
      lens.moreRows(5000);
      lens.moreRows(5100);
   };
   private static final String COUNTING = "counter.hit(); field['value'] + 1";
   private static final int ROWS = 50_000;
}
