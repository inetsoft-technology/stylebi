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
import inetsoft.uql.util.TableLoadException;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.stall.LockStallException;
import inetsoft.util.swap.SwapFileReadException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.function.Supplier;

import static inetsoft.util.stall.StallTestSupport.data;
import static inetsoft.util.swap.SwapLostTestSupport.swapLost;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A freehand (calc) table formula with a top-level var, which lives in the table's var store,
 * whose read of data that was not available (a lock stall, a lost swap file, a table that
 * failed to load) failed once after the var was changed: the formula is not evaluated again on
 * that table, which would apply the change twice, but fails every read, and the table is
 * stopped, so a cache computes it again with a fresh store (bug #78133). A formula without
 * vars is still evaluated again on the same table.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CalcTableLensStallVarTest {
   @BeforeEach
   void setUp() {
      env = new GraalJavaScriptEnv();
      env.init();
   }

   @ParameterizedTest
   @ValueSource(strings = { "stall", "swap", "load" })
   void varStoreFormulaIsNotEvaluatedAgainAfterAFailedRead(String fault) {
      FailOnceTable base = new FailOnceTable(fault);
      CalcTableLens calc = calcTable(base, VAR_FORMULA);

      RuntimeException first = assertThrows(base.type(), () -> calc.getValue(0, 0));
      assertTrue(base.failed, "sanity: the data read failed");

      // not 2.0: the formula is not evaluated again on the var it changed
      Object again = valueOrFailure(() -> calc.getValue(0, 0));
      assertInstanceOf(base.type(), again, "evaluated again: " + again);
      assertEquals(first.getMessage(), ((RuntimeException) again).getMessage());
      assertTrue(calc.isStopped(), "a cache computes the table again");

      calc.invalidate();
      assertEquals(1.0, toDouble(calc.getValue(0, 0)), "a fresh var store");
      assertFalse(calc.isStopped());
   }

   /**
    * The production path: the runtime table of process(), which keeps the values in place,
    * and two formulas that share the var store.
    */
   @ParameterizedTest
   @ValueSource(strings = { "stall", "swap", "load" })
   void runtimeVarStoreFormulaIsNotEvaluatedAgainAfterAFailedRead(String fault) {
      FailOnceTable base = new FailOnceTable(fault);
      CalcTableLens calc = calcTable(base, "var c = (c || 0) + 1; c",
                                     "var c = (c || 0) + 1; data['value'][2]; c");
      RuntimeCalcTableLens runtime = calc.process();

      assertEquals(1.0, toDouble(runtime.getObject(0, 0)));
      assertThrows(base.type(), () -> runtime.getObject(0, 1));
      assertTrue(base.failed, "sanity: the data read failed");
      // not 3.0
      Object again = valueOrFailure(() -> runtime.getObject(0, 1));
      assertInstanceOf(base.type(), again, "evaluated again: " + again);
      assertEquals(1.0, toDouble(runtime.getObject(0, 0)), "a computed cell keeps its value");
      assertTrue(runtime.isStopped(), "a cache computes the table again");

      RuntimeCalcTableLens fresh = calc.process();
      assertEquals(1.0, toDouble(fresh.getObject(0, 0)));
      assertEquals(2.0, toDouble(fresh.getObject(0, 1)), "the control value");
      assertFalse(fresh.isStopped());
   }

   /**
    * Unchanged: a formula without vars is evaluated again on the same table.
    */
   @ParameterizedTest
   @ValueSource(strings = { "stall", "swap", "load" })
   void formulaWithoutVarsIsEvaluatedAgainAfterAFailedRead(String fault) {
      FailOnceTable base = new FailOnceTable(fault);
      RuntimeCalcTableLens runtime = calcTable(base, "data['value'][2] * 1").process();

      assertThrows(base.type(), () -> runtime.getObject(0, 0));
      assertEquals(3.0, toDouble(runtime.getObject(0, 0)));
      assertFalse(runtime.isStopped());
   }

   private CalcTableLens calcTable(TableLens base, String... formulas) {
      FormulaTable elem = mock(FormulaTable.class);
      when(elem.getScriptEnv()).thenReturn(env);
      when(elem.getID()).thenReturn("CalcTable1");
      when(elem.getScriptTable()).thenReturn(base);

      CalcTableLens calc = new CalcTableLens(1, formulas.length);
      calc.setElement(elem);

      for(int c = 0; c < formulas.length; c++) {
         calc.setObject(0, c, new CalcTableLens.Formula(formulas[c]));
      }

      return calc;
   }

   private static Object valueOrFailure(Supplier<Object> read) {
      try {
         return read.get();
      }
      catch(RuntimeException ex) {
         return ex;
      }
   }

   private static double toDouble(Object value) {
      assertInstanceOf(Number.class, value, "a number, not " + value);
      return ((Number) value).doubleValue();
   }

   /**
    * A base whose first read of the value of row 3 fails; the later reads succeed. A wrapper,
    * not a DefaultTableLens subclass, whose getObject a calc formula's data read does not reach.
    */
   private static final class FailOnceTable extends AbstractTableLens {
      FailOnceTable(String fault) {
         this.fault = fault;
      }

      Class<? extends RuntimeException> type() {
         return switch(fault) {
            case "stall" -> LockStallException.class;
            case "swap" -> SwapFileReadException.class;
            default -> TableLoadException.class;
         };
      }

      @Override
      public boolean moreRows(int row) {
         return table.moreRows(row);
      }

      @Override
      public int getRowCount() {
         return table.getRowCount();
      }

      @Override
      public int getColCount() {
         return table.getColCount();
      }

      @Override
      public int getHeaderRowCount() {
         return 1;
      }

      @Override
      public Object getObject(int r, int c) {
         if(r == 3 && c == 1 && !failed) {
            failed = true;
            throw switch(fault) {
               case "stall" -> new LockStallException("test.site", "worker", 1234, null);
               case "swap" -> swapLost();
               default -> new TableLoadException("failed to load", null);
            };
         }

         return table.getObject(r, c);
      }

      private final DefaultTableLens table = new DefaultTableLens(data(5));
      private final String fault;
      private volatile boolean failed;
   }

   private static final String VAR_FORMULA =
      "var cnt2 = (typeof cnt2 == 'undefined' ? 0 : cnt2) + 1; data['value'][2]; cnt2";
   private GraalJavaScriptEnv env;
}
