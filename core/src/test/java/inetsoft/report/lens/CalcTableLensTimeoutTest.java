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
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.test.*;
import inetsoft.util.script.ScriptException;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.script.graal.ScriptStopTestSupport;
import inetsoft.util.script.graal.ScriptStopTestSupport.Stops;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.atomic.AtomicLong;

import static inetsoft.util.stall.StallTestSupport.data;
import static inetsoft.util.swap.SwapLostTestSupport.failureOf;
import static inetsoft.util.swap.SwapLostTestSupport.within;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A freehand (calc) table formula stopped by the script timeout is not an error cell (bug
 * #77949): the reader gets the stop, and a later read evaluates the formula again, in the
 * table's own cell cache, in the runtime table's in-place cache, in the expansion of
 * process(), and in a cell that reads the stopped cell by name. The stops are real 1 s
 * timeouts of a {@code while(true){}} run in place of the formula's exec, or a stopped
 * script exception injected there.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class CalcTableLensTimeoutTest {
   @BeforeEach
   public void setUp() throws Exception {
      previousTimeout = ScriptStopTestSupport.setTimeout("1");
      stops = new Stops();
      env = new ScriptStopTestSupport.StoppingEnv(stops);
      env.init();
      // a formula of its own per test: the compiled scripts are cached by their source
      marker = "/*c" + NONCE.incrementAndGet() + "*/";
   }

   @AfterEach
   public void tearDown() throws Exception {
      Thread.interrupted();
      ScriptStopTestSupport.setTimeout(previousTimeout);
   }

   @Test
   public void timedOutFormulaIsEvaluatedAgainNotAnErrorCell() throws Exception {
      assertCellRetries(false);
   }

   @Test
   public void injectedStopFormulaIsEvaluatedAgainNotAnErrorCell() throws Exception {
      assertCellRetries(true);
   }

   /**
    * The runtime table caches a value in place of the formula: after a stop the formula is
    * put back.
    */
   @Test
   public void timedOutRuntimeFormulaIsEvaluatedAgain() throws Exception {
      CalcTableLens calc = calcTable(1, 2);
      calc.setObject(0, 0, new CalcTableLens.Formula("41 + 1" + marker));
      calc.setObject(0, 1, new CalcTableLens.Formula("'x'"));
      RuntimeCalcTableLens runtime = within(CAP, calc::process);
      stops.reset(marker, n -> n == 1, false);

      assertStop(failureOf(CAP, () -> runtime.getObject(0, 0)));
      assertEquals(42, ((Number) within(CAP, () -> runtime.getObject(0, 0))).intValue());
      assertEquals(2, stops.calls());
   }

   /**
    * The expansion of process() reads the stopped formula: process() fails with the stop
    * instead of returning a table with an error cell, and processes again.
    */
   @Test
   public void timedOutExpansionFailsProcessAndProcessesAgain() throws Exception {
      CalcTableLens calc = calcTable(1, 1);
      calc.setObject(0, 0, new CalcTableLens.Formula("[1, 2, 3]" + marker));
      calc.setExpansion(0, 0, CalcTableLens.EXPAND_VERTICAL);
      stops.reset(marker, n -> n == 1, false);

      assertStop(failureOf(CAP, calc::process));
      RuntimeCalcTableLens runtime = within(CAP, calc::process);
      assertNotNull(runtime);
      assertEquals(3, runtime.getRowCount());

      for(int r = 0; r < 3; r++) {
         assertEquals(r + 1, ((Number) runtime.getObject(r, 0)).intValue());
      }
   }

   /**
    * A cell that reads the stopped cell by name ($a) is not cached with a wrong value or an
    * error either.
    */
   @Test
   public void timedOutCellReadByNameIsEvaluatedAgain() throws Exception {
      assertNamedReadRetries(false);
   }

   /**
    * The same when no interrupt is pending on the reading cell's exec: the stop reaches it as
    * a host exception of its own script.
    */
   @Test
   public void injectedStopCellReadByNameIsEvaluatedAgain() throws Exception {
      assertNamedReadRetries(true);
   }

   /**
    * Unchanged: an ordinary script error is an error cell, evaluated once.
    */
   @Test
   public void scriptErrorIsStillACachedErrorCell() throws Exception {
      CalcTableLens calc = calcTable(1, 1);
      calc.setObject(0, 0, new CalcTableLens.Formula("throw new Error('boom')" + marker));
      stops.reset(marker, n -> false, false);

      Object cell = within(CAP, () -> calc.getValue(0, 0));
      assertInstanceOf(String.class, cell);
      assertTrue(((String) cell).startsWith("ERROR: "), "an error cell, not " + cell);
      assertEquals(cell, within(CAP, () -> calc.getValue(0, 0)));
      assertEquals(1, stops.calls(), "an error cell is cached");
   }

   private void assertCellRetries(boolean inject) throws Exception {
      CalcTableLens calc = calcTable(1, 1);
      calc.setObject(0, 0, new CalcTableLens.Formula("41 + 1" + marker));
      stops.reset(marker, n -> n == 1, inject);

      assertStop(failureOf(CAP, () -> calc.getValue(0, 0)));
      assertEquals(42, ((Number) within(CAP, () -> calc.getValue(0, 0))).intValue(),
                   "the stopped formula is evaluated again");
      assertEquals(42, ((Number) within(CAP, () -> calc.getValue(0, 0))).intValue());
      assertEquals(2, stops.calls(), "a value is cached");
   }

   private void assertNamedReadRetries(boolean inject) throws Exception {
      CalcTableLens calc = calcTable(1, 2);
      calc.setObject(0, 0, new CalcTableLens.Formula("41 + 1" + marker));
      calc.setCellName(0, 0, "a");
      calc.setObject(0, 1, new CalcTableLens.Formula("$a * 10"));
      RuntimeCalcTableLens runtime = within(CAP, calc::process);
      stops.reset(marker, n -> n == 1, inject);

      assertStop(failureOf(CAP, () -> runtime.getObject(0, 1)));
      assertEquals(420, ((Number) within(CAP, () -> runtime.getObject(0, 1))).intValue(),
                   "the reading cell is evaluated again");
      assertEquals(42, ((Number) within(CAP, () -> runtime.getObject(0, 0))).intValue());
      assertEquals(2, stops.calls());
   }

   private static void assertStop(Throwable failure) {
      assertInstanceOf(ScriptException.class, failure);
      assertTrue(ScriptTimeoutGuard.isStop(failure), "the reader gets the stop: " + failure);
   }

   /**
    * A calc table whose formulas run on the stopping env.
    */
   private CalcTableLens calcTable(int rows, int cols) {
      FormulaTable elem = mock(FormulaTable.class);
      when(elem.getScriptEnv()).thenReturn(env);
      when(elem.getID()).thenReturn("CalcTable1");
      when(elem.getScriptTable()).thenReturn(new DefaultTableLens(data(5)));

      CalcTableLens calc = new CalcTableLens(rows, cols);
      calc.setElement(elem);
      return calc;
   }

   private static final long CAP = 30;
   private static final AtomicLong NONCE = new AtomicLong();
   private GraalJavaScriptEnv env;
   private Stops stops;
   private String marker;
   private String previousTimeout;
}
