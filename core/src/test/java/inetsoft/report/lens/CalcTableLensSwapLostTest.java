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
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.test.*;
import inetsoft.util.script.ScriptException;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.swap.SwapFileReadException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.FileNotFoundException;

import static inetsoft.util.stall.StallTestSupport.data;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A freehand (calc) table whose base lost a swap file fails its reader with the swap file
 * read failure instead of showing no table or a cached error cell (bug #77909). Any other
 * failure is handled as before.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
public class CalcTableLensSwapLostTest {
   /**
    * The expansion reads the lazy value list of {@code rowList} from a base whose swapped
    * rows are lost.
    */
   @Test
   public void lostBaseInRowListExpansionFailsProcess() {
      SwapFileReadException lost = lost();
      CalcTableLens calc = expandingCalcTable(new LostTable(lost), "rowList(data, 'value')");

      assertSame(lost, assertThrows(SwapFileReadException.class, calc::process));
   }

   @Test
   public void wrappedSwapFailureFailsProcess() {
      SwapFileReadException lost = lost();
      CalcTableLens calc = new CalcTableLens(2, 2) {
         @Override
         public synchronized RuntimeCalcTableLens process0() {
            throw new IllegalStateException("wrapped", lost);
         }
      };

      // the swap failure itself, not the wrapper
      assertSame(lost, assertThrows(SwapFileReadException.class, calc::process));
   }

   @Test
   public void otherProcessFailureReturnsNoTableAsBefore() {
      CalcTableLens calc = new CalcTableLens(2, 2) {
         @Override
         public synchronized RuntimeCalcTableLens process0() {
            throw new IllegalStateException("boom", new RuntimeException("not a swap"));
         }
      };

      assertNull(calc.process());
   }

   /**
    * An ordinary script error over a readable base is still an error cell of a processed table.
    */
   @Test
   public void scriptErrorIsAnErrorCellOfAProcessedTable() {
      CalcTableLens calc =
         expandingCalcTable(new DefaultTableLens(data(5)), "noSuchFunction(data)");
      RuntimeCalcTableLens runtime = calc.process();

      assertNotNull(runtime);
      Object cell = runtime.getObject(0, 0);
      assertInstanceOf(String.class, cell);
      assertTrue(((String) cell).startsWith("ERROR: "), "an error cell, not " + cell);
   }

   /**
    * A formula that read a lost swap file: the script error keeps the swap failure as its
    * cause.
    */
   @Test
   public void swapFailureInAFormulaIsNotAnErrorCell() {
      SwapFileReadException lost = lost();
      CalcTableLens calc = formulaLens(new ScriptException("JavaScript error: lost", lost));

      assertSame(lost, assertThrows(SwapFileReadException.class, () -> calc.getValue(0, 0)));
      // the cell is not cached as empty: a later read fails too
      assertSame(lost, assertThrows(SwapFileReadException.class, () -> calc.getValue(0, 0)));
   }

   /**
    * The runtime lens caches a formula's value in place of the formula: after a swap failure
    * the formula is put back, so the cell is not read as empty later.
    */
   @Test
   public void swapFailureInARuntimeFormulaIsNotAnEmptyCellLater() {
      SwapFileReadException lost = lost();
      ScriptException failure = new ScriptException("JavaScript error: lost", lost);
      RuntimeCalcTableLens runtime = new RuntimeCalcTableLens(new CalcTableLens(2, 2)) {
         @Override
         protected Object evaluate(int row, int col, Formula expr) {
            throw failure;
         }
      };
      runtime.setObject(0, 0, new CalcTableLens.Formula("data['value']"));

      assertSame(lost, assertThrows(SwapFileReadException.class, () -> runtime.getObject(0, 0)));
      assertSame(lost, assertThrows(SwapFileReadException.class, () -> runtime.getObject(0, 0)));
   }

   @Test
   public void otherFormulaErrorIsAnErrorCellAsBefore() {
      CalcTableLens calc = formulaLens(new ScriptException("bad formula"));

      assertEquals("ERROR: bad formula", calc.getValue(0, 0));
   }

   @Test
   public void formulaErrorWithAnotherCauseIsAnErrorCellAsBefore() {
      CalcTableLens calc = formulaLens(
         new ScriptException("bad formula", new IllegalArgumentException("not a swap")));

      assertEquals("ERROR: bad formula", calc.getValue(0, 0));
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
    * A 1x1 calc table over {@code base} whose only cell expands vertically on
    * {@code formula}, evaluated by a real GraalJS env.
    */
   private static CalcTableLens expandingCalcTable(TableLens base, String formula) {
      GraalJavaScriptEnv env = new GraalJavaScriptEnv();
      env.init();
      FormulaTable elem = mock(FormulaTable.class);
      when(elem.getScriptEnv()).thenReturn(env);
      when(elem.getID()).thenReturn("CalcTable1");
      when(elem.getScriptTable()).thenReturn(base);

      CalcTableLens calc = new CalcTableLens(1, 1);
      calc.setElement(elem);
      calc.setObject(0, 0, new CalcTableLens.Formula(formula));
      calc.setExpansion(0, 0, CalcTableLens.EXPAND_VERTICAL);
      return calc;
   }

   private static SwapFileReadException lost() {
      return new SwapFileReadException(new File("s123_4.tdat"),
                                       new FileNotFoundException("gone"));
   }

   /**
    * A table whose data rows were swapped out and their swap file lost; the header is still
    * in memory.
    */
   private static final class LostTable extends DefaultTableLens {
      LostTable(SwapFileReadException lost) {
         super(data(5));
         this.lost = lost;
      }

      @Override
      public Object getObject(int r, int c) {
         if(r > 0) {
            throw lost;
         }

         return super.getObject(r, c);
      }

      @Override
      public Object getData(int r, int c) {
         if(r > 0) {
            throw lost;
         }

         return super.getData(r, c);
      }

      private final SwapFileReadException lost;
   }
}
