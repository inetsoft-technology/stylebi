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
package inetsoft.report.script;

import inetsoft.report.filter.SumFormula;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.report.script.formula.FormulaEvaluator;
import inetsoft.report.script.graal.ReportGraalJavaScriptEngine;
import inetsoft.test.*;
import inetsoft.uql.script.XTableArray;
import inetsoft.util.script.ScriptException;
import inetsoft.util.script.graal.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

import static inetsoft.util.swap.SwapLostTestSupport.failureOf;
import static inetsoft.util.swap.SwapLostTestSupport.within;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78076: a script read of a formula column whose formula was stopped by the script
 * timeout fails with the stop, as a {@code table[3]['F']} read does since #77949. Before, a
 * by-name read ({@code table['F']}), a range read with a condition or an expression on
 * {@code rowValue}, and a range summary caught the stop and read null (or selected the stopped
 * row), so the script completed with a wrong value. The stop is real: a formula that
 * busy-waits 3 s in row 3 under a 1 s {@code script.execution.timeout}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TableReadStopTest {
   @BeforeEach
   void setUp() throws Exception {
      previousTimeout = ScriptStopTestSupport.setTimeout("1");
      // one stopped lens for every test: a stopped row is a stop on each later read of the
      // lens (#77949), so the tests need not wait for a timeout each
      if(lens == null) {
         GraalJavaScriptEnv env = new GraalJavaScriptEnv();
         env.init();
         // a formula of its own: the compiled scripts are cached by their source
         String formula = "if(field['id'] == " + STOPPED_ROW + ") { var t0 = Date.now(); " +
            "while(Date.now() - t0 < 3000) {} } field['id'] * 10 /*s" + NONCE.incrementAndGet() +
            "*/";
         Object[][] data = new Object[ROWS + 1][];
         data[0] = new Object[] { "id" };

         for(int i = 1; i <= ROWS; i++) {
            data[i] = new Object[] { i };
         }

         lens = new FormulaTableLens(new DefaultTableLens(data), new String[] { "F" },
                                     new String[] { formula }, env, null);
      }

      // precondition: the row's formula is stopped, and each later read of it is the stop
      assertStop(failureOf(CAP, () -> lens.getObject(STOPPED_ROW, 1)));
      scope = new MapScope();
   }

   @AfterEach
   void tearDown() throws Exception {
      Thread.interrupted();
      ScriptStopTestSupport.setTimeout(previousTimeout);
   }

   @AfterAll
   static void tearDownClass() {
      lens = null;
   }

   @Test
   void byNameReadIsAStop() throws Exception {
      TableArray table = new TableArray(lens);
      assertStop(failureOf(CAP, () -> table.getMember("F")));
      assertStop(failureOf(CAP, () -> ((TableArray) table.getMember("*")).getMember("F")));
      assertStop(failureOf(CAP, () -> table.getMember("F?true")));
      // control: the row read was already a stop (#77949), and another row reads its value
      assertStop(failureOf(CAP, () -> ((TableRow) table.getArrayElement(STOPPED_ROW))
         .getMember("F")));
      assertEquals(20, ((Number) within(CAP, () -> ((TableRow) table.getArrayElement(2))
         .getMember("F"))).intValue());
   }

   @Test
   void xTableArrayByNameReadIsAStop() throws Exception {
      XTableArray table = new XTableArray(lens);
      assertStop(failureOf(CAP, () -> table.getMember("F")));
      assertStop(failureOf(CAP, () -> table.hasMember("F")));
   }

   @Test
   void scriptReadingAColumnByNameIsStopped() throws Exception {
      scope.putMember("t", new TableArray(lens));
      assertScriptStopped("'completed: ' + t['F']");
   }

   @Test
   void scriptRangeConditionOnAStoppedCellIsStopped() throws Exception {
      scope.putMember("t", new TableArray(lens));
      assertScriptStopped("'completed: ' + t['id?rowValue[\"F\"] > 0']");
   }

   @Test
   void scriptRangeExpressionOnAStoppedCellIsStopped() throws Exception {
      scope.putMember("t", new TableArray(lens));
      assertScriptStopped("'completed: ' + t['=rowValue[\"F\"]']");
   }

   @Test
   void formulaEvaluatorReadOfAStoppedCellIsAStop() throws Exception {
      assertStop(failureOf(CAP, () -> FormulaEvaluator.exec(
         "rowValue['F']", scope, "rowValue", new TableRow(lens, STOPPED_ROW))));
      // control: another row, and an ordinary script error keeps its null value
      assertEquals(20, ((Number) within(CAP, () -> FormulaEvaluator.exec(
         "rowValue['F']", scope, "rowValue", new TableRow(lens, 2)))).intValue());
      assertNull(within(CAP, () -> FormulaEvaluator.exec(
         "throw new Error('boom')", scope, "rowValue", new TableRow(lens, 2))));
   }

   @Test
   void formulaEvaluatorTimeoutIsAStop() throws Exception {
      assertStop(failureOf(CAP, () -> FormulaEvaluator.exec(
         ScriptStopTestSupport.LOOP + "/*fe" + NONCE.incrementAndGet() + "*/", scope, "field",
         new TableRow(lens, 1))));
   }

   @Test
   void rangeSummaryOverAStoppedCellIsAStop() throws Exception {
      // a non-positional range (summarize's range.getCells catch)
      assertStop(failureOf(CAP, () -> ReportGraalJavaScriptEngine.summarize(
         lens, "F?true", "sum", new SumFormula(), null, scope)));
      // a condition that reads the stopped cell (RangeProcessor's condition catch)
      assertStop(failureOf(CAP, () -> ReportGraalJavaScriptEngine.summarize(
         lens, "id", "sum", new SumFormula(), "rowValue['F'] > 0", scope)));
      // control: a condition that does not read it
      assertEquals(10, ((Number) within(CAP, () -> ReportGraalJavaScriptEngine.summarize(
         lens, "id", "sum", new SumFormula(), "rowValue['id'] > 0", scope))).intValue());
   }

   /**
    * A script that reads the table completes with no value: its exec fails as stopped.
    */
   private void assertScriptStopped(String source) throws Exception {
      GraalJavaScriptEnv env = new GraalJavaScriptEnv();
      env.init();
      Object script = env.compile(source);
      Throwable failure = failureOf(CAP, (Callable<Object>) () -> env.exec(script, scope, null, null));
      assertInstanceOf(ScriptException.class, failure);
      assertTrue(((ScriptException) failure).isStopped(), "stopped: " + failure);
   }

   private static void assertStop(Throwable failure) {
      assertNotNull(failure, "the read failed");
      assertTrue(ScriptTimeoutGuard.isStop(failure), "the read is a stop: " + failure);
   }

   /**
    * A plain scope for the scripts and formula conditions.
    */
   private static final class MapScope implements ScriptScope {
      @Override
      public Object getMember(String name) {
         return members.get(name);
      }

      @Override
      public boolean hasMember(String name) {
         return members.containsKey(name);
      }

      @Override
      public void putMember(String name, Object value) {
         members.put(name, value);
      }

      @Override
      public boolean removeMember(String name) {
         return members.remove(name) != null;
      }

      @Override
      public Object[] getMemberKeys() {
         return members.keySet().toArray();
      }

      private final Map<String, Object> members = new LinkedHashMap<>();
   }

   private static final long CAP = 30;
   private static final int ROWS = 4;
   private static final int STOPPED_ROW = 3;
   private static final AtomicLong NONCE = new AtomicLong();
   private static FormulaTableLens lens;
   private MapScope scope;
   private String previousTimeout;
}
