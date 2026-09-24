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

import inetsoft.report.TableLens;
import inetsoft.report.filter.SumFormula;
import inetsoft.report.filter.SummaryFilter;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.test.*;
import inetsoft.util.script.ScriptException;
import inetsoft.util.stall.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static inetsoft.util.stall.StallTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

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
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 1000, 200, dumpDir));
      pool = readerPool();
   }

   @AfterEach
   public void tearDown() {
      if(gated != null) {
         gated.open();
      }

      pool.shutdownNow();
      StallPolicy.setOverride(null);
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

   private static SummaryFilter summary(TableLens base) {
      return new SummaryFilter(base, new int[] { 0 }, new int[] { 1 }, new SumFormula(), null);
   }

   @TempDir
   File dumpDir;
   private ExecutorService pool;
   private StallTestSupport.GatedTable gated;
}
