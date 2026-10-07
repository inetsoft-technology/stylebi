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
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.test.*;
import inetsoft.util.script.ExpressionFailedException;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.*;
import inetsoft.util.script.graal.ScriptStopTestSupport.Stops;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static inetsoft.util.swap.SwapLostTestSupport.failureOf;
import static inetsoft.util.swap.SwapLostTestSupport.within;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A formula row stopped by the script timeout is not a row with a null cell (bug #77949): the
 * stopped cells fail every read with the stop, recognizable as one, and so does every cell
 * whose formula reads them. The row is never computed again from the vars its stopped exec
 * may have changed, so no read returns a wrong value: the rows after it go on from the vars as
 * the stopped exec left them, as they always did, and a new row table (invalidate()) or a
 * new lens computes the table again with fresh vars. The stops are real 1 s timeouts of a
 * {@code while(true){}} (optionally after changing the vars) run in place of the row's exec,
 * or a stopped script exception injected there.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class FormulaTableLensTimeoutTest {
   @BeforeEach
   public void setUp() throws Exception {
      previousTimeout = ScriptStopTestSupport.setTimeout("1");
      stops = new Stops();
      env = createEnv(stops);
      env.init();
      // a formula of its own per test: the compiled scripts are cached by their source
      marker = "/*t" + NONCE.incrementAndGet() + "*/";
   }

   @AfterEach
   public void tearDown() throws Exception {
      Thread.interrupted();
      ScriptStopTestSupport.setTimeout(previousTimeout);
   }

   /**
    * The env the lenses run on: a plain Graal env here (script context pool off).
    */
   GraalJavaScriptEnv createEnv(Stops stops) {
      return new ScriptStopTestSupport.StoppingEnv(stops);
   }

   @Test
   public void timedOutRowIsAStopOnEveryReadNotANullCell() throws Exception {
      assertRunningTotalStops(false);
   }

   @Test
   public void injectedStopRowIsAStopOnEveryReadNotANullCell() throws Exception {
      assertRunningTotalStops(true);
   }

   /**
    * A formula that does not read the stopped row: only the stopped cell fails, the rows
    * after it are computed as before.
    */
   @Test
   public void rowsAfterAStoppedRowAreComputedAsBefore() throws Exception {
      String formula = "field['value'] * 10" + marker;
      stops.reset(marker, n -> n == FAILED_ROW, false);
      FormulaTableLens lens = lens(formula);

      assertStop(failureOf(CAP, () -> drain(lens)));

      for(int r = 1; r <= N; r++) {
         final int row = r;

         if(r == FAILED_ROW) {
            assertStop(failureOf(CAP, () -> lens.getObject(row, 2)));
         }
         else {
            assertEquals(r * 10, ((Number) within(CAP, () -> value(lens, row, 2))).intValue());
         }
      }

      assertEquals(1, stops.stops());
   }

   /**
    * The stopped exec changed a formula var (a Date, in place) before the timeout: the row is
    * not computed again from it, which would apply the change twice and put every later row
    * one off. The later rows go on from the var as the stopped exec left it, which counts its
    * change once, and a new row table or a new lens computes every row with a fresh var.
    */
   @Test
   public void varChangedBeforeTheTimeoutIsNotAppliedTwice() throws Exception {
      // a worksheet formula, whose top-level vars the row scope owns; the row whose base
      // column 'loop' is 1 loops after its step, until the timeout
      String formula = "var d = d || new Date(0); d.setTime(d.getTime() + 1000); " +
         "if(field['loop'] == 1) { while(true) {} } d.getTime() / 1000" + marker;
      AssetQuerySandbox box = PoolTestSupport.poolBox(pooled());
      ScriptEnv boxEnv = box.getScriptEnv();
      AtomicInteger loopRow = new AtomicInteger();
      DefaultTableLens base = loopData(loopRow);

      try {
         List<List<Object>> expected =
            within(CAP, () -> drain(FormulaTableLensVarTest.make(box, base, formula, "T")));

         for(int r = 1; r <= N; r++) {
            assertEquals(r, ((Number) expected.get(r).get(3)).intValue(), "control of row " + r);
         }

         loopRow.set(FAILED_ROW);
         TableLens lens = FormulaTableLensVarTest.make(box, base, formula, "T");

         assertStop(failureOf(CAP, () -> drain(lens)));
         // the loop was one-shot: a computation of the row again would not time out, but
         // would step the Date a second time and put every later row one off, silently
         loopRow.set(0);

         for(int read = 0; read < 2; read++) {
            for(int r = 1; r <= N; r++) {
               final int row = r;

               if(r == FAILED_ROW) {
                  assertStop(failureOf(CAP, () -> lens.getObject(row, 3)));
               }
               else {
                  assertEquals(r, ((Number) within(CAP, () -> value(lens, row, 3))).intValue(),
                               "row " + r + ", read " + read);
               }
            }
         }

         // a computation with fresh vars
         ((FormulaTableLens) lens).invalidate();
         assertEquals(expected, within(CAP, () -> drain(lens)), "a new row table, fresh vars");
         assertEquals(expected,
                      within(CAP, () -> drain(FormulaTableLensVarTest.make(box, base, formula, "T"))),
                      "a new lens, fresh vars");
      }
      finally {
         if(boxEnv instanceof WorksheetScriptEnv pooled) {
            pooled.retire();
         }
      }
   }

   /**
    * Whether the lenses of {@link #varChangedBeforeTheTimeoutIsNotAppliedTwice} run on the
    * script context pool.
    */
   boolean pooled() {
      return false;
   }

   /**
    * A column that reads the stopped column of its own row: both cells fail with the stop.
    */
   @Test
   public void timedOutCellReadByAnotherColumnOfTheRowIsAStopToo() throws Exception {
      assertNestedStop(false);
   }

   /**
    * The same when no interrupt is pending on the reading column's exec: the stop reaches it
    * as a host exception of its own script.
    */
   @Test
   public void injectedStopReadByAnotherColumnOfTheRowIsAStopToo() throws Exception {
      assertNestedStop(true);
   }

   /**
    * A timeout that lasts: each read stops at most one more row, with one timeout; a stopped
    * row fails again without one.
    */
   @Test
   public void lastingTimeoutFailsEveryReadAsAStop() throws Exception {
      List<List<Object>> expected = within(CAP, () -> drain(lens(RUNNING_TOTAL + marker)));
      stops.reset(marker, n -> n >= FAILED_ROW, false);
      FormulaTableLens lens = lens(RUNNING_TOTAL + marker);

      assertStop(failureOf(CAP, () -> drain(lens)));
      assertEquals(FAILED_ROW, stops.calls(), "the batch ends at the stopped row");
      assertStop(failureOf(CAP, () -> drain(lens)));
      assertStop(failureOf(CAP, () -> lens.getObject(FAILED_ROW, 2)));
      assertEquals(1, stops.stops(), "a stopped row fails again without a timeout");
      assertStop(failureOf(CAP, () -> lens.getObject(FAILED_ROW + 1, 2)));
      assertEquals(2, stops.stops(), "the next row runs into the timeout once");
      assertEquals(expected.get(FAILED_ROW - 1).get(2),
                   within(CAP, () -> lens.getObject(FAILED_ROW - 1, 2)),
                   "a row before it is still readable");
   }

   /**
    * A running total over the stopped row: the stopped cell and every later one fail with the
    * stop (they read it), on every read, and none is a null or a wrong value. The rows before
    * it keep their values, and a new row table computes the control values.
    */
   private void assertRunningTotalStops(boolean inject) throws Exception {
      String formula = RUNNING_TOTAL + marker;
      List<List<Object>> expected = within(CAP, () -> drain(lens(formula)));

      for(int r = 1; r <= N; r++) {
         assertEquals(r * (r + 1) / 2, ((Number) expected.get(r).get(2)).intValue(),
                      "control running total of row " + r);
      }

      stops.reset(marker, n -> n == FAILED_ROW, inject);
      FormulaTableLens lens = lens(formula);

      assertStop(failureOf(CAP, () -> drain(lens)));
      assertEquals(FAILED_ROW, stops.calls(), "the stop ends the batch");

      for(int read = 0; read < 2; read++) {
         assertStop(failureOf(CAP, () -> drain(lens)));

         for(int r = 1; r <= N; r++) {
            final int row = r;

            if(r < FAILED_ROW) {
               assertEquals(expected.get(r).get(2), within(CAP, () -> value(lens, row, 2)));
            }
            else {
               assertStop(failureOf(CAP, () -> value(lens, row, 2)));
            }
         }
      }

      assertEquals(1, stops.stops());
      assertEquals(N, stops.calls(), "each row runs once, the stopped one is not run again");

      lens.invalidate();
      assertEquals(expected, within(CAP, () -> drain(lens)), "a new row table");
   }

   private void assertNestedStop(boolean inject) throws Exception {
      // g first: its exec computes f of the same row, so f's stop happens inside g's exec
      String[] names = { "g", "f" };
      String[] formulas = { "field['f'] * 10", "field['value']" + marker };
      List<List<Object>> expected = within(CAP, () -> drain(lens(names, formulas)));

      for(int r = 1; r <= N; r++) {
         assertEquals(r, ((Number) expected.get(r).get(3)).intValue());
         assertEquals(r * 10, ((Number) expected.get(r).get(2)).intValue());
      }

      stops.reset(marker, n -> n == FAILED_ROW, inject);
      FormulaTableLens lens = lens(names, formulas);

      assertStop(failureOf(CAP, () -> drain(lens)));

      for(int r = 1; r <= N; r++) {
         for(int c = 2; c <= 3; c++) {
            final int row = r;
            final int col = c;

            if(r == FAILED_ROW) {
               assertStop(failureOf(CAP, () -> lens.getObject(row, col)));
            }
            else {
               assertEquals(expected.get(r).get(c), within(CAP, () -> value(lens, row, col)));
            }
         }
      }

      assertEquals(1, stops.stops());
      lens.invalidate();
      assertEquals(expected, within(CAP, () -> drain(lens)), "a new row table");
   }

   /**
    * The reader gets a formula failure that says it is a stop, not a script error.
    */
   static void assertStop(Throwable failure) {
      assertInstanceOf(ExpressionFailedException.class, failure);
      assertTrue(ScriptTimeoutGuard.isStop(failure), "the reader can tell a stop: " + failure);
      assertTrue(ScriptTimeoutGuard.isStop(
         ((ExpressionFailedException) failure).getOriginalException()));
   }

   private FormulaTableLens lens(String expr) {
      return lens(new String[] { "f" }, new String[] { expr });
   }

   private FormulaTableLens lens(String[] names, String[] formulas) {
      return new FormulaTableLens(new DefaultTableLens(data()), names, formulas, env, null);
   }

   private static Object value(TableLens lens, int r, int c) {
      assertTrue(lens.moreRows(r));
      return lens.getObject(r, c);
   }

   /**
    * The rows of {@link #data()} with a column 'loop', 1 in the row {@code loopRow} holds and
    * 0 in the others. Changing it fires no change event, which would invalidate the lens.
    */
   private static DefaultTableLens loopData(AtomicInteger loopRow) {
      Object[][] data = new Object[N + 1][];
      data[0] = new Object[] { "key", "value", "loop" };

      for(int i = 1; i <= N; i++) {
         data[i] = new Object[] { "k" + i, i, 0 };
      }

      return new DefaultTableLens(data) {
         @Override
         public Object getObject(int r, int c) {
            return c == 2 && r > 0 ? (r == loopRow.get() ? 1 : 0) : super.getObject(r, c);
         }
      };
   }

   private static Object[][] data() {
      Object[][] data = new Object[N + 1][];
      data[0] = new Object[] { "key", "value" };

      for(int i = 1; i <= N; i++) {
         data[i] = new Object[] { "k" + i, i };
      }

      return data;
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

   static final long CAP = 30;
   static final int N = 8;
   static final int FAILED_ROW = 3;
   // a running total over the formula's own earlier row
   static final String RUNNING_TOTAL =
      "field['value'] + (field[-1] == null || field[-1]['f'] == null || " +
      "isNaN(field[-1]['f']) ? 0 : field[-1]['f'])";
   private static final AtomicLong NONCE = new AtomicLong();
   GraalJavaScriptEnv env;
   Stops stops;
   private String marker;
   private String previousTimeout;
}
