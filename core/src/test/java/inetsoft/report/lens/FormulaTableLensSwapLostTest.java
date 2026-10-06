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
import inetsoft.report.filter.SortFilter;
import inetsoft.report.script.TableRow;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.util.script.ExpressionFailedException;
import inetsoft.util.script.ScriptException;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.swap.SwapFileReadException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;

import static inetsoft.util.swap.SwapLostTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

/**
 * A lost swap file that reaches a formula lens is not a null cell (bug #77912): the lens's own
 * row table reads and the formula's script both hand the reader the
 * {@link SwapFileReadException} itself, and the row whose script failed with it is not kept,
 * so a later read computes it again. An ordinary script error keeps its contract.
 *
 * Nothing produces these failures on main yet: a lost row table fragment reads as null before
 * #77895, and the script engine drops a host exception's cause before #77910. The cases inject
 * them as those changes will deliver them: a row table whose cell read throws, and an env whose
 * exec throws the failure raw or as the cause of a script error.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class FormulaTableLensSwapLostTest {
   @BeforeEach
   public void setUp() {
      env = new FailingEnv();
      env.init();
   }

   @AfterEach
   public void tearDown() throws Exception {
      if(env.engine != null) {
         env.engine.close();
      }
   }

   /**
    * A swap failure of one exec reaches the reader as itself, not as a formula error, and the
    * retry computes the row, so a running total equals the control.
    */
   @Test
   public void transientScriptSwapFailureRetriesToTheControlValues() throws Exception {
      assertTransientFailureRetries(false);
   }

   /**
    * The same when the engine reports the failure as the cause of a script error.
    */
   @Test
   public void wrappedScriptSwapFailureRetriesToTheControlValues() throws Exception {
      assertTransientFailureRetries(true);
   }

   /**
    * A swap failure that lasts fails every read of the row, each with one more exec, and a
    * sort over the lens; the rows before it stay readable.
    */
   @Test
   public void permanentScriptSwapFailureFailsEveryReadBoundedly() throws Exception {
      List<List<Object>> expected = within(CAP, () -> drain(lens(RUNNING_TOTAL)));
      SwapFileReadException lost = swapLost();
      env.reset(n -> n >= FAILED_ROW, lost, false);
      FormulaTableLens lens = lens(RUNNING_TOTAL);

      assertSame(lost, failureOf(CAP, () -> drain(lens)));
      int calls = env.calls.get();
      assertEquals(FAILED_ROW, calls, "the batch ends at the failed row");

      assertSame(lost, failureOf(CAP, () -> lens.getObject(FAILED_ROW, 2)));
      assertSame(lost, failureOf(CAP, () -> lens.moreRows(TableLens.EOT)));
      assertSame(lost, failureOf(CAP, () -> drain(lens)));
      assertSame(lost, failureOf(CAP, () -> drain(new SortFilter(lens, new int[] { 2 }, true))));
      assertTrue(env.calls.get() - calls <= 4,
                 "every read makes one attempt at most: " + (env.calls.get() - calls));
      assertEquals(expected.get(FAILED_ROW - 1).get(2),
                   within(CAP, () -> lens.getObject(FAILED_ROW - 1, 2)),
                   "a kept row is still readable");
   }

   /**
    * Unchanged: an ordinary script error reaches the reader once as a formula error, and its
    * cell then reads null.
    */
   @Test
   public void scriptErrorKeepsItsContract() throws Exception {
      String bad = "if(field['value'] == " + FAILED_ROW + ") throw new Error('boom'); " +
         "field['value']";
      FormulaTableLens lens = lens(bad);

      Throwable failure = failureOf(CAP, () -> drain(lens));
      assertInstanceOf(ExpressionFailedException.class, failure);
      assertNull(SwapFileReadException.find(failure));
      assertNull(SwapFileReadException.find(((ExpressionFailedException) failure)
                                               .getOriginalException()));

      List<List<Object>> rows = within(CAP, () -> drain(lens));
      assertEquals(N + 1, rows.size());

      for(int r = 1; r <= N; r++) {
         Object value = rows.get(r).get(2);

         if(r == FAILED_ROW) {
            assertNull(value, "the failed row's cell reads null");
         }
         else {
            assertEquals(r, ((Number) value).intValue());
         }
      }
   }

   /**
    * A lost row table cell fails the lens's read of it, and the read of an earlier row of
    * the batch that a formula makes; other cells, a cancelled lens and a disposed one still
    * read as before.
    */
   @Test
   public void lostRowTableCellIsNotANullValue() throws Exception {
      // more rows than the first batch computes, so the lens is not complete and can be cancelled
      FormulaTableLens lens = lens("field['value'] * 10", BIG);
      SwapFileReadException lost = swapLost();
      AtomicBoolean lose = new AtomicBoolean();
      XSwappableTable rows = spy(rowTable(lens));
      // lens row r is row table row r: one header row in both
      doAnswer(inv -> {
         if(lose.get() && (int) inv.getArgument(0) == FAILED_ROW) {
            throw lost;
         }

         return inv.callRealMethod();
      }).when(rows).getObject(anyInt(), anyInt());
      setField(lens, "rows", rows);

      assertTrue(within(CAP, () -> lens.moreRows(N)));
      assertEquals(FAILED_ROW * 10, ((Number) lens.getObject(FAILED_ROW, 2)).intValue());
      lose.set(true);

      assertSame(lost, failureOf(CAP, () -> lens.getObject(FAILED_ROW, 2)));
      assertEquals((FAILED_ROW - 1) * 10, ((Number) lens.getObject(FAILED_ROW - 1, 2)).intValue());
      assertEquals(FAILED_ROW, lens.getObject(FAILED_ROW, 1),
                   "a base cell is not read from the row table");

      // a formula's read of an earlier row of its own row table (field[-1])
      TableRow tableRow = (TableRow) getField(lens, "tableRow");
      Method get = TableRow.class.getDeclaredMethod("get", XTable.class, Method.class,
                                                    int.class, int.class);
      get.setAccessible(true);
      assertSame(lost, failureOf(CAP, () -> invoke(get, tableRow, lens, FAILED_ROW, 2)));
      assertEquals((FAILED_ROW - 1) * 10,
                   ((Number) invoke(get, tableRow, lens, FAILED_ROW - 1, 2)).intValue());

      lens.cancel();
      assertTrue(lens.isCancelled());
      assertNull(lens.getObject(FAILED_ROW, 2), "a cancelled lens still reads null");
      lens.dispose();
      assertNull(lens.getObject(FAILED_ROW, 2), "a disposed lens still reads null");
   }

   private void assertTransientFailureRetries(boolean wrapped) throws Exception {
      List<List<Object>> expected = within(CAP, () -> drain(lens(RUNNING_TOTAL)));

      for(int r = 1; r <= N; r++) {
         assertEquals(r * (r + 1) / 2, ((Number) expected.get(r).get(2)).intValue(),
                      "control running total of row " + r);
      }

      SwapFileReadException lost = swapLost();
      env.reset(n -> n == FAILED_ROW, lost, wrapped);
      FormulaTableLens lens = lens(RUNNING_TOTAL);

      assertSame(lost, failureOf(CAP, () -> drain(lens)),
                 "the reader gets the swap failure itself");
      assertEquals(expected, within(CAP, () -> drain(lens)),
                   "the failed row is computed again, not kept as null");
      assertEquals(N + 1, env.calls.get(), "one exec per row and one retry");
   }

   private FormulaTableLens lens(String expr) {
      return lens(expr, N);
   }

   private FormulaTableLens lens(String expr, int n) {
      return new FormulaTableLens(new DefaultTableLens(data(n)), new String[] { "f" },
                                  new String[] { expr }, env, null);
   }

   private static Object invoke(Method get, TableRow tableRow, FormulaTableLens lens, int row,
                                int col) throws Exception
   {
      try {
         return get.invoke(tableRow, lens, null, row, col);
      }
      catch(java.lang.reflect.InvocationTargetException ex) {
         throw (Exception) ex.getCause();
      }
   }

   private static Object[][] data(int n) {
      Object[][] data = new Object[n + 1][];
      data[0] = new Object[] { "key", "value" };

      for(int i = 1; i <= n; i++) {
         data[i] = new Object[] { "k" + i, i };
      }

      return data;
   }

   private static XSwappableTable rowTable(FormulaTableLens lens) throws Exception {
      return (XSwappableTable) getField(lens, "rows");
   }

   private static Object getField(FormulaTableLens lens, String name) throws Exception {
      Field field = FormulaTableLens.class.getDeclaredField(name);
      field.setAccessible(true);
      return field.get(lens);
   }

   private static void setField(FormulaTableLens lens, String name, Object value)
      throws Exception
   {
      Field field = FormulaTableLens.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(lens, value);
   }

   private static List<List<Object>> drain(TableLens table) {
      List<List<Object>> rows = new ArrayList<>();

      for(int r = 0; table.moreRows(r); r++) {
         List<Object> row = new ArrayList<>();

         for(int c = 0; c < table.getColCount(); c++) {
            row.add(table.getObject(r, c));
         }

         rows.add(row);
      }

      return rows;
   }

   /**
    * A real env whose n-th exec (from 1) fails with a lost swap file when {@code fail} says
    * so, raw or as the cause of a script error, as the script engine delivers it once a
    * host exception's cause survives it (#77910).
    */
   private static final class FailingEnv extends GraalJavaScriptEnv {
      void reset(IntPredicate fail, SwapFileReadException lost, boolean wrapped) {
         this.fail = fail;
         this.lost = lost;
         this.wrapped = wrapped;
         calls.set(0);
      }

      @Override
      public Object exec(Object script, Object scope, Object rscope, Object target)
         throws Exception
      {
         if(fail.test(calls.incrementAndGet())) {
            throw wrapped ? new ScriptException("JavaScript error", lost) : lost;
         }

         return super.exec(script, scope, rscope, target);
      }

      @Override
      protected GraalJavaScriptEngine createScriptEngine() {
         engine = super.createScriptEngine();
         return engine;
      }

      final AtomicInteger calls = new AtomicInteger();
      private volatile IntPredicate fail = n -> false;
      private volatile SwapFileReadException lost;
      private volatile boolean wrapped;
      private GraalJavaScriptEngine engine;
   }

   private static final long CAP = 30;
   private static final int N = 8;
   private static final int BIG = 200;
   private static final int FAILED_ROW = 3;
   // a running total over the formula's own earlier row
   private static final String RUNNING_TOTAL =
      "field['value'] + (field[-1] == null || field[-1]['f'] == null || " +
      "isNaN(field[-1]['f']) ? 0 : field[-1]['f'])";
   private FailingEnv env;
}
