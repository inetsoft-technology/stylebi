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
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.test.*;
import inetsoft.util.script.ScriptEnv;
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

import java.util.*;

import static inetsoft.report.composition.execution.PoolOffConditionFilterLockingTest.allRows;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Batch claims (bug #76960, spec §5.3, §6.7, §14.3, §14.8; gates G3, G3b, the sequential
 * part of G6): under sequential access a formula lens or a condition filter cleans its
 * pooled context once per batch of at least batchRows rows, never per row; script globals
 * live for the outermost claimed span.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class PooledBatchClaimTest {
   @Test
   public void sequentialFormulaReadsCleanOncePerBatch() {
      WorksheetScriptEnv env = PoolTestSupport.env();
      FormulaTableLens formula = formula(table(ROWS), env, "field['value'] + 1");

      for(int r = 1; formula.moreRows(r); r++) {
         assertEquals((r % 30) + 1.0, formula.getObject(r, 3));
      }

      assertCleansPerBatch(env);
   }

   @Test
   public void sequentialFilterReadsCleanOncePerBatch() {
      WorksheetScriptEnv env = PoolTestSupport.env();
      TableLens filter = PostProcessor.filter(formula(table(ROWS), env, "field['value'] + 1"),
                                              allRows(), poolBox(env));

      for(int r = 1; filter.moreRows(r); r++) {
         assertEquals((r % 30) + 1.0, filter.getObject(r, 3));
      }

      assertCleansPerBatch(env);
      assertEquals(0, SlotClaim.openClaims());
   }

   /**
    * G3b: a filter drain keeps the formula's globals across every formula batch inside the
    * one filter span, and cleans them at its end.
    */
   @Test
   public void filterDrainKeepsFormulaGlobalsForTheWholeSpan() {
      WorksheetScriptEnv env = PoolTestSupport.env();
      TableLens filter = PostProcessor.filter(formula(table(1000), env, ACCUMULATOR),
                                              allRows(), poolBox(env));

      assertFalse(filter.moreRows(Integer.MAX_VALUE));

      for(int r = 1; r <= 1000; r++) {
         assertEquals((double) r, filter.getObject(r, 3), "row " + r);
      }

      assertEquals(1, env.getMetrics().getCleans());
   }

   /**
    * G3b and clean determinism: a bare formula batch is cleaned at its own end, so the
    * accumulator restarts at 1 in the next batch, which is at least batchRows rows later.
    */
   @Test
   public void bareFormulaBatchIsCleanedAtItsEnd() {
      WorksheetScriptEnv env = PoolTestSupport.env();
      FormulaTableLens formula = formula(table(1000), env, ACCUMULATOR);
      int reset = -1;

      for(int r = 1; formula.moreRows(r); r++) {
         double value = (Double) formula.getObject(r, 3);

         if(r > 1 && value == 1.0 && reset < 0) {
            reset = r;
         }

         if(reset < 0) {
            assertEquals((double) r, value, "row " + r);
         }
      }

      assertTrue(reset - 1 >= env.getConfig().batchRows(), "batch was " + (reset - 1));
   }

   /**
    * G3: FTL_outer(CF2(FTL_inner)). The outer accumulator runs across one outer batch while
    * the inner population runs between its rows on the same thread, sharing the claim.
    */
   @Test
   public void outerFormulaStateSurvivesInnerPopulation() {
      WorksheetScriptEnv env = PoolTestSupport.env();
      TableLens filter = PostProcessor.filter(formula(table(1000), env, "1"), allRows(),
                                              poolBox(env));
      FormulaTableLens outer = new FormulaTableLens(filter, new String[] {"g"},
                                                    new String[] {ACCUMULATOR}, env, null);

      assertTrue(outer.moreRows(1));

      for(int r = 1; r <= env.getConfig().batchRows(); r++) {
         assertEquals((double) r, outer.getObject(r, 4), "row " + r);
      }
   }

   /**
    * Spec §6.7: reading rows the filter has already mapped needs no claim or context.
    */
   @Test
   public void mappedRowsNeedNoClaim() {
      WorksheetScriptEnv env = PoolTestSupport.env();
      TableLens filter = PostProcessor.filter(formula(table(1000), env, "1"), allRows(),
                                              poolBox(env));
      assertFalse(filter.moreRows(Integer.MAX_VALUE));
      long cleans = env.getMetrics().getCleans();

      for(int r = 0; r <= 1000; r++) {
         assertTrue(filter.moreRows(r));
      }

      assertEquals(cleans, env.getMetrics().getCleans());
   }

   /**
    * Spec §14.14: under sequential reads a bare formula lens's batches double from batchRows
    * up to maxBatchRows, and never exceed it. Each bare batch is cleaned at its end, so the
    * accumulator restarts at 1 in the next one; a batch spans its read-ahead plus the row
    * that asked for it.
    */
   @Test
   public void sequentialFormulaBatchesDoubleUpToTheCap() {
      WorksheetScriptEnv env = geometricEnv(1024);
      List<Integer> runs = runs(formula(table(8000), env, ACCUMULATOR), 1, 8000);

      assertEquals(List.of(257, 513, 1025, 1025, 1025, 1025, 1025), runs.subList(0, 7));
      runs.forEach(run -> assertTrue(run <= 1025, "batch of " + run + " rows"));
   }

   /**
    * Spec §14.14: a non-sequential access starts the batches over at batchRows.
    */
   @Test
   public void formulaBatchesResetOnARandomAccess() {
      WorksheetScriptEnv env = geometricEnv(4096);
      FormulaTableLens formula = formula(table(8000), env, ACCUMULATOR);

      // rows 1-1795 are computed; reading row 1795 itself would ask for the next batch
      assertEquals(List.of(257, 513, 1024), runs(formula, 1, 1794));
      // skip ahead of the first row not yet computed (1796)
      assertTrue(formula.moreRows(1798));
      assertEquals(List.of(257, 513), runs(formula, 1796, 1796 + 257 + 513 - 1));
   }

   /**
    * Spec §14.14: under sequential reads a pooled filter's populations double from batchRows
    * up to maxBatchRows, and never exceed it.
    */
   @Test
   public void sequentialFilterBatchesDoubleUpToTheCap() {
      WorksheetScriptEnv env = geometricEnv(1024);
      TableLens filter = PostProcessor.filter(formula(table(8000), env, "1"), allRows(),
                                              poolBox(env));
      List<Integer> batches = new ArrayList<>();
      int mapped = mappedRows(filter);

      for(int r = 1; filter.moreRows(r); r++) {
         int now = mappedRows(filter);

         if(now != mapped) {
            batches.add(now - mapped);
            mapped = now;
         }
      }

      assertEquals(List.of(256, 512, 1024, 1024, 1024, 1024, 1024), batches.subList(0, 7));
      batches.forEach(batch -> assertTrue(batch <= 1024, "batch of " + batch + " rows"));
      assertEquals(0, SlotClaim.openClaims());
   }

   /**
    * Spec §14.14: a non-sequential access to a pooled filter starts its populations over at
    * batchRows.
    */
   @Test
   public void filterBatchesResetOnARandomAccess() {
      WorksheetScriptEnv env = geometricEnv(4096);
      TableLens filter = PostProcessor.filter(formula(table(8000), env, "1"), allRows(),
                                              poolBox(env));

      for(int r = 1; r < 1793; r++) {
         assertTrue(filter.moreRows(r));
      }

      assertEquals(1 + 256 + 512 + 1024, mappedRows(filter));
      // skip ahead of the first row not yet mapped (1793)
      assertTrue(filter.moreRows(1795));
      assertEquals(1793 + 256, mappedRows(filter));
      assertTrue(filter.moreRows(1793 + 256));
      assertEquals(1793 + 256 + 512, mappedRows(filter));
   }

   /**
    * perf-g6 (c)(i): a long sequential scan pays O(log(max/batchRows) + rows/max) cleans.
    */
   @Test
   public void longSequentialScanCleansLogarithmically() {
      WorksheetScriptEnv env = PoolTestSupport.env();
      FormulaTableLens formula = formula(table(100_000), env, "field['value'] + 1");

      for(int r = 1; formula.moreRows(r); r++) {
         formula.getObject(r, 3);
      }

      PoolConfig config = env.getConfig();
      long bound = 5 + 100_000 / config.maxBatchRows() + 2;
      assertTrue(env.getMetrics().getCleans() <= bound,
                 "cleans " + env.getMetrics().getCleans() + " > " + bound);
   }

   /**
    * perf-g6 (c)(ii): an early-stop consumer of a fresh lens computes at most one batchRows
    * batch beyond the row it asked for.
    */
   @Test
   public void earlyStopReadsAtMostOneBatchAhead() {
      WorksheetScriptEnv env = PoolTestSupport.env();
      int[] deepest = new int[1];
      DefaultTableLens base = new DefaultTableLens(table(5000)) {
         @Override
         public boolean moreRows(int row) {
            deepest[0] = Math.max(deepest[0], row);
            return super.moreRows(row);
         }
      };

      assertTrue(formula(base, env, "field['value'] + 1").moreRows(300));
      assertTrue(deepest[0] <= 300 + env.getConfig().batchRows() + 1,
                 "computed through base row " + deepest[0]);
   }

   private static WorksheetScriptEnv geometricEnv(int maxBatchRows) {
      return PoolTestSupport.env(new PoolConfig(60000L, 256, 16, 2000, 256, maxBatchRows),
                                 Map.of());
   }

   /**
    * @return the lengths of the accumulator's runs over rows {@code from..to}, read in order.
    */
   private static List<Integer> runs(FormulaTableLens formula, int from, int to) {
      List<Integer> runs = new ArrayList<>();
      int run = 0;

      for(int r = from; r <= to && formula.moreRows(r); r++) {
         double value = (Double) formula.getObject(r, 3);

         if(value == 1.0 && run > 0) {
            runs.add(run);
            run = 0;
         }

         run++;
      }

      runs.add(run);
      return runs;
   }

   private static int mappedRows(TableLens filter) {
      int count = filter.getRowCount();
      return count < 0 ? -count - 1 : count;
   }

   static AssetQuerySandbox poolBox(ScriptEnv env) {
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.peekScriptEnv()).thenReturn(env);
      when(box.isScriptPoolMode()).thenReturn(true);
      return box;
   }

   static FormulaTableLens formula(TableLens base, ScriptEnv env, String expr) {
      return new FormulaTableLens(base, new String[] {"f"}, new String[] {expr}, env, null);
   }

   static DefaultTableLens table(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] {"group", "value", "id"};

      for(int i = 1; i <= rows; i++) {
         data[i] = new Object[] {"k" + (i % 6), i % 30, i};
      }

      return new DefaultTableLens(data);
   }

   private static void assertCleansPerBatch(WorksheetScriptEnv env) {
      long cleans = env.getMetrics().getCleans();
      assertTrue(cleans <= ROWS / env.getConfig().batchRows() + 1,
                 "cleans " + cleans + " for " + ROWS + " rows");
      assertTrue(env.getMetrics().cleansPerExec() <= 1.0 / 200,
                 "cleansPerExec " + env.getMetrics().cleansPerExec());
   }

   static final String ACCUMULATOR = "typeof acc == 'undefined' ? (acc = 1) : ++acc";
   static final int ROWS = 10000;
}
