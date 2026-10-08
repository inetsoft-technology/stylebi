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
package inetsoft.report.lens;

import inetsoft.report.FormulaTable;
import inetsoft.report.TableLens;
import inetsoft.report.filter.SumFormula;
import inetsoft.report.filter.SummaryFilter;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.test.*;
import inetsoft.util.script.ScriptException;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.stall.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static inetsoft.util.stall.StallTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A freehand (calc) table over a stalled lens fails its reader with the stall instead of
 * showing no table or an error cell (bug #76967, final review I3). Any other failure is
 * handled as before.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
public class CalcTableLensStallTest {
   @BeforeEach
   public void setUp() {
      resetGlobalStallState();
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 1000, 200, dumpDir,
                                              StallPolicy.DEFAULT_MAX_DUMPS, true));
      pool = readerPool();
   }

   @AfterEach
   public void tearDown() {
      if(gated != null) {
         gated.open();
      }

      pool.shutdownNow();
      StallTestSupport.clearOverride();
   }

   /**
    * The expansion reads a stalled summary (as the processors read the script table).
    */
   @Test
   public void stalledBaseFailsProcess() throws Exception {
      gated = new GatedTable(30);
      SummaryFilter summary = summary(gated);
      CalcTableLens calc = new CalcTableLens(2, 2) {
         @Override
         public synchronized RuntimeCalcTableLens process0() {
            summary.moreRows(1);
            return super.process0();
         }
      };

      assertEquals("SummaryFilter.waitForRow",
                   stallIn(failureOf(pool.submit(calc::process), 15)).getSite());
   }

   @Test
   public void otherProcessFailureReturnsNoTableAsBefore() throws Exception {
      CalcTableLens calc = new CalcTableLens(2, 2) {
         @Override
         public synchronized RuntimeCalcTableLens process0() {
            throw new IllegalStateException("boom");
         }
      };

      assertNull(pool.submit(calc::process).get(15, TimeUnit.SECONDS));
   }

   /**
    * A formula that read a stalled lens: the script error keeps the stall as its cause.
    */
   @Test
   public void stallInAFormulaIsNotAnErrorCell() throws Exception {
      LockStallException original = new LockStallException("nested.site", "worker", 1234, null);
      CalcTableLens calc = formulaLens(new ScriptException("JavaScript error: stalled", original));

      LockStallException stall = stallIn(failureOf(pool.submit(() -> calc.getValue(0, 0)), 15));
      assertTrue(stall == original || stall.getCause() == original, "the formula's stall");
      // the cell is not cached as empty: a later read fails too
      assertNotNull(stallIn(failureOf(pool.submit(() -> calc.getValue(0, 0)), 15)));
   }

   /**
    * The runtime lens caches a formula's value in place of the formula: after a stall the
    * formula is put back, so the cell is not read as empty later.
    */
   @Test
   public void stallInARuntimeFormulaIsNotAnEmptyCellLater() throws Exception {
      LockStallException original = new LockStallException("nested.site", "worker", 1234, null);
      ScriptException failure = new ScriptException("JavaScript error: stalled", original);
      RuntimeCalcTableLens runtime = new RuntimeCalcTableLens(new CalcTableLens(2, 2)) {
         @Override
         protected Object evaluate(int row, int col, Formula expr) {
            throw failure;
         }
      };
      runtime.setObject(0, 0, new CalcTableLens.Formula("data['value']"));

      assertNotNull(stallIn(failureOf(pool.submit(() -> runtime.getObject(0, 0)), 15)));
      assertNotNull(stallIn(failureOf(pool.submit(() -> runtime.getObject(0, 0)), 15)));
   }

   @Test
   public void otherFormulaErrorIsAnErrorCellAsBefore() throws Exception {
      CalcTableLens calc = formulaLens(new ScriptException("bad formula"));

      assertEquals("ERROR: bad formula",
                   pool.submit(() -> calc.getValue(0, 0)).get(15, TimeUnit.SECONDS));
   }

   private static CalcTableLens formulaLens(ScriptException failure) {
      CalcTableLens calc = new CalcTableLens(2, 2) {
         @Override
         protected Object evaluate(int row, int col, Formula expr) {
            throw failure;
         }
      };
      calc.setObject(0, 0, new CalcTableLens.Formula("data['value']"));
      return calc;
   }

   /**
    * #77123: cell B = {@code $A + 1}, where A's formula reads a stalled summary through
    * {@code data}. The named-cell reference must not turn A's stall into null, or B would
    * complete as {@code null + 1} and cache that wrong value. The reader gets the stall, and
    * once the base is released B is evaluated again and is right.
    */
   @Test
   public void stalledNamedCellReferenceIsNotCachedAsAWrongValue() throws Exception {
      gated = new GatedTable(30);
      RuntimeCalcTableLens runtime = referencingCalcTable(summary(gated));

      LockStallException stall =
         stallIn(failureOf(pool.submit(() -> runtime.getObject(0, 1)), 15));
      assertEquals("SummaryFilter.waitForRow", stall.getSite());

      gated.open();
      // neither B nor A was cached from the stalled evaluation
      assertEquals(expectedB(), toDouble(pool.submit(() -> runtime.getObject(0, 1))
                                            .get(15, TimeUnit.SECONDS)));
   }

   /**
    * ALERT (the default) is unchanged: the reference read keeps waiting on the stalled
    * summary, the wait is alerted, and B is right once the base is released.
    */
   @Test
   public void alertModeNamedCellReferenceWaitsAndCompletes() throws Exception {
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.ALERT, 1000, 200, dumpDir,
                                              StallPolicy.DEFAULT_MAX_DUMPS, false));
      gated = new GatedTable(30);
      RuntimeCalcTableLens runtime = referencingCalcTable(summary(gated));
      int dumps = WaitRegistry.global().getDumper().getDumpCount();
      Future<Object> reader = pool.submit(() -> runtime.getObject(0, 1));

      try {
         awaitTrue(() -> WaitRegistry.global().getDumper().getDumpCount() > dumps, 15,
                   "the summary wait was never alerted");
         assertFalse(reader.isDone(), "the reader must still be waiting");
      }
      finally {
         gated.open();
      }

      assertEquals(expectedB(), toDouble(reader.get(15, TimeUnit.SECONDS)));
   }

   /**
    * B's value over an unstalled base: the first summary value of {@link #summary}, plus 1.
    */
   private static double expectedB() {
      RuntimeCalcTableLens runtime = referencingCalcTable(summary(new DefaultTableLens(data(30))));
      Object a = runtime.getObject(0, 0);
      assertNotNull(a, "sanity: A has a value over an unstalled base");
      double b = toDouble(runtime.getObject(0, 1));
      assertEquals(toDouble(a) + 1, b, "sanity: B = $A + 1");
      return b;
   }

   private static double toDouble(Object value) {
      assertInstanceOf(Number.class, value, "a number, not " + value);
      return ((Number) value).doubleValue();
   }

   /**
    * A 1x2 calc table over {@code base} with A = {@code data['value'][0]} and
    * B = {@code $A + 1}, evaluated by a real GraalJS env. Call on a reader thread or with an
    * unstalled base.
    */
   private static RuntimeCalcTableLens referencingCalcTable(TableLens base) {
      GraalJavaScriptEnv env = new GraalJavaScriptEnv();
      env.init();
      FormulaTable elem = mock(FormulaTable.class);
      when(elem.getScriptEnv()).thenReturn(env);
      when(elem.getID()).thenReturn("CalcTable1");
      when(elem.getScriptTable()).thenReturn(base);

      CalcTableLens calc = new CalcTableLens(1, 2);
      calc.setElement(elem);
      calc.setObject(0, 0, new CalcTableLens.Formula("data['value'][0]"));
      calc.setCellName(0, 0, "A");
      calc.setObject(0, 1, new CalcTableLens.Formula("$A + 1"));
      // start the summary's worker outside of a script, as for a summary another request
      // is already computing: inside a script it would be computed on the reader thread
      base.getRowCount();
      return calc.process();
   }

   private static SummaryFilter summary(TableLens base) {
      return new SummaryFilter(base, new int[] { 0 }, new int[] { 1 }, new SumFormula(), null);
   }

   @TempDir
   File dumpDir;
   private ExecutorService pool;
   private StallTestSupport.GatedTable gated;
}
