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
import inetsoft.test.*;
import inetsoft.util.script.ExpressionFailedException;
import inetsoft.util.script.graal.*;
import inetsoft.util.script.graal.ScriptStopTestSupport.Stops;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static inetsoft.util.swap.SwapLostTestSupport.failureOf;
import static inetsoft.util.swap.SwapLostTestSupport.within;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A formula row stopped by the script timeout is not kept with a null cell (bug #77949): the
 * reader gets the stop, recognizable as one, and a later read computes the row again, so a
 * running total equals the control. The stops are real 1 s timeouts of a {@code while(true){}}
 * run in place of the row's exec, or a stopped script exception injected there.
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
   public void timedOutRowIsComputedAgainNotReadAsNull() throws Exception {
      assertStopRetries(false);
   }

   @Test
   public void injectedStopRowIsComputedAgainNotReadAsNull() throws Exception {
      assertStopRetries(true);
   }

   /**
    * A column that reads the stopped column of its own row: neither cell is kept.
    */
   @Test
   public void timedOutCellReadByAnotherColumnOfTheRowIsComputedAgain() throws Exception {
      assertNestedStopRetries(false);
   }

   /**
    * The same when no interrupt is pending on the reading column's exec: the stop reaches it
    * as a host exception of its own script.
    */
   @Test
   public void injectedStopReadByAnotherColumnOfTheRowIsComputedAgain() throws Exception {
      assertNestedStopRetries(true);
   }

   /**
    * A timeout that lasts fails every read of the row as a stop, one timeout per read; the
    * rows before it stay readable.
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
      assertEquals(3, stops.stops(), "one timeout per read");
      assertEquals(expected.get(FAILED_ROW - 1).get(2),
                   within(CAP, () -> lens.getObject(FAILED_ROW - 1, 2)),
                   "a kept row is still readable");
   }

   private void assertStopRetries(boolean inject) throws Exception {
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
      assertEquals(expected, within(CAP, () -> drain(lens)),
                   "the stopped row is computed again, not kept as null");
      assertEquals(N + 1, stops.calls(), "one exec per row and one retry");
      assertEquals(1, stops.stops());
   }

   private void assertNestedStopRetries(boolean inject) throws Exception {
      // g first: its exec computes f of the same row, so f's stop happens inside g's exec
      String[] names = { "g", "f" };
      String[] formulas = { "field['f'] * 10", RUNNING_TOTAL + marker };
      List<List<Object>> expected = within(CAP, () -> drain(lens(names, formulas)));

      for(int r = 1; r <= N; r++) {
         assertEquals(r * (r + 1) / 2, ((Number) expected.get(r).get(3)).intValue());
         assertEquals(r * (r + 1) * 5, ((Number) expected.get(r).get(2)).intValue());
      }

      stops.reset(marker, n -> n == FAILED_ROW, inject);
      FormulaTableLens lens = lens(names, formulas);

      assertStop(failureOf(CAP, () -> drain(lens)));
      assertEquals(expected, within(CAP, () -> drain(lens)),
                   "neither cell of the stopped row is kept as null");
      assertEquals(1, stops.stops());
   }

   /**
    * The reader gets a formula failure that says it is a stop, not a script error.
    */
   private static void assertStop(Throwable failure) {
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
