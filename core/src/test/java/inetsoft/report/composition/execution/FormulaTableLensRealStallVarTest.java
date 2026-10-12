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
import inetsoft.report.filter.SumFormula;
import inetsoft.report.filter.SummaryFilter;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.script.TableArray;
import inetsoft.test.*;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import inetsoft.util.stall.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static inetsoft.util.stall.StallTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A worksheet formula table whose formulas own a var, after a row stalled or read data that
 * was not available inside its script once (bug #78133): a stall the watchdog throws while a
 * script reads another table through {@code TableArray}, readers racing the row table that
 * replaces the stalled one, and a stateless control that recomputes on the same row table.
 * Pool off and on.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FormulaTableLensRealStallVarTest {
   @BeforeEach
   void setUp() {
      resetGlobalStallState();
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 1000, 200, dumpDir,
                                              StallPolicy.DEFAULT_MAX_DUMPS, true));
      pool = readerPool();
   }

   @AfterEach
   void tearDown() {
      if(gated != null) {
         gated.open();
      }

      pool.shutdownNow();
      clearOverride();

      if(env instanceof WorksheetScriptEnv pooled) {
         pooled.retire();
      }
   }

   /**
    * A watchdog-thrown stall (no injected exception): row 3's script reads another table
    * through TableArray, whose SummaryFilter stalls; after the gate opens the same lens gives
    * the control values.
    */
   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void realStallThroughTableArray(boolean pool) throws Exception {
      gated = new GatedTable(30);
      SummaryFilter summary = new SummaryFilter(gated, new int[] { 0 }, new int[] { 1 },
                                                new SumFormula(), null);
      AssetQuerySandbox box = PoolTestSupport.poolBox(pool);
      env = box.getScriptEnv();
      env.put("S", new TableArray(summary));
      // start the summary's background worker outside a script, it blocks on the gate
      summary.getRowCount();
      TableLens lens = formula(box, new DefaultTableLens(data(ROWS)),
         "var k = (k || 0) + 1; var x = (field['value'] == 3 ? S['value'][0] : 0); k");

      Throwable failure = failureOf(this.pool.submit(() -> read(lens)), 20);
      assertEquals("SummaryFilter.waitForRow", stallIn(failure).getSite());
      gated.open();

      assertEquals(expected(), this.pool.submit(() -> read(lens)).get(30, TimeUnit.SECONDS));
      assertEquals(expected(), this.pool.submit(() -> read(lens)).get(30, TimeUnit.SECONDS));
   }

   /**
    * A stateless formula with a real scope owns no var: its row table is kept, and the same
    * lens computes the stalled row again, not the rows before it.
    */
   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void statelessFormulaRecomputesOnTheSameInstance(boolean pool) throws Exception {
      AssetQuerySandbox box = PoolTestSupport.poolBox(pool);
      env = box.getScriptEnv();
      FailOnce base = new FailOnce(3);
      TableLens lens = formula(box, base, "field['value'] * 10");

      assertThrows(LockStallException.class, () -> read(lens));
      List<Integer> want = new ArrayList<>();

      for(int r = 1; r <= ROWS; r++) {
         want.add(r * 10);
      }

      assertEquals(want, read(lens));
      assertEquals(want, read(lens));
      // the stalled row is read once more, the rows before it are not computed again
      assertEquals(ROWS + 1, base.valueReads.get(), "value reads: " + base.valueReads.get());
   }

   /**
    * Concurrent readers of a stateful lens whose row (3 to 6) fails once: every reader that retries
    * after the failure gets the control values, under the row table replacement.
    */
   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void concurrentReadersAcrossTheReplacement(boolean pool) throws Exception {
      for(int round = 0; round < 10; round++) {
         AssetQuerySandbox box = PoolTestSupport.poolBox(pool);
         env = box.getScriptEnv();
         FailOnce base = new FailOnce(3 + round % 4);
         TableLens lens = formula(box, base, "var k = (k || 0) + 1; var v = field['value']; k");
         CyclicBarrier start = new CyclicBarrier(6);
         List<Future<List<Integer>>> readers = new ArrayList<>();

         for(int i = 0; i < 6; i++) {
            readers.add(this.pool.submit(() -> {
               start.await();

               for(int attempt = 0; ; attempt++) {
                  try {
                     return read(lens);
                  }
                  catch(LockStallException ex) {
                     assertTrue(attempt < 3, "failed again: " + ex);
                  }
               }
            }));
         }

         for(Future<List<Integer>> reader : readers) {
            assertEquals(expected(), reader.get(30, TimeUnit.SECONDS), "round " + round);
         }

         assertTrue(base.failed.get(), "sanity: the read failed");

         if(env instanceof WorksheetScriptEnv pooled) {
            pooled.retire();
         }

         env = null;
      }
   }

   private static TableLens formula(AssetQuerySandbox box, TableLens base, String formula) {
      return PostProcessor.formula(base, new String[] { "acc" }, new String[] { formula },
                                   box.getScriptEnv(), box.getScope(), null, "T", null,
                                   List.of(Double.class), new boolean[1]);
   }

   private static List<Integer> expected() {
      List<Integer> values = new ArrayList<>();

      for(int r = 1; r <= ROWS; r++) {
         values.add(r);
      }

      return values;
   }

   private static List<Integer> read(TableLens lens) {
      List<Integer> values = new ArrayList<>();

      for(int r = 1; lens.moreRows(r); r++) {
         Object value = lens.getObject(r, 2);
         values.add(value instanceof Number ? ((Number) value).intValue() : null);
      }

      return values;
   }

   private static final class FailOnce extends DefaultTableLens {
      FailOnce(int row) {
         super(data(ROWS));
         this.row = row;
      }

      @Override
      public Object getObject(int r, int c) {
         if(r >= 1 && c == 1) {
            valueReads.incrementAndGet();

            if(r == row && failed.compareAndSet(false, true)) {
               throw new LockStallException("test.site", "worker", 1234, null);
            }
         }

         return super.getObject(r, c);
      }

      private final int row;
      final AtomicBoolean failed = new AtomicBoolean();
      final AtomicInteger valueReads = new AtomicInteger();
   }

   private static final int ROWS = 8;
   @TempDir
   File dumpDir;
   private ExecutorService pool;
   private GatedTable gated;
   private ScriptEnv env;
}
