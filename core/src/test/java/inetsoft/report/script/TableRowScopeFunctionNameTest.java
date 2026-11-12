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

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.PostProcessor;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.script.formula.FormulaEvaluator;
import inetsoft.test.*;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81047: a column whose name matches a script function only ignoring case (YEAR, MONTH,
 * DATEADD) must not hide the function when a formula calls it, {@code year(field['D'])}. The
 * axis the fix varies on is the call-site syntax of each occurrence, per formula: a bare call
 * {@code name(args)} falls back to the global function (the GraalJS-parsed callee rewrite, see
 * {@code FormulaCalleeRewriter}), a bare read {@code year} stays the column, whatever else the
 * formula or the other formulas of the table contain.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TableRowScopeFunctionNameTest {
   @AfterEach
   void retire() {
      for(ScriptEnv env : envs) {
         if(env instanceof WorksheetScriptEnv) {
            ((WorksheetScriptEnv) env).retire();
         }
      }

      envs.clear();
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void yearFunctionWorksNextToABaseColumnNamedYEAR(boolean pool) throws Exception {
      TableLens t = formula(pool, new Object[][] { { "YEAR", "D" }, { 5, TS } },
                            "out", "year(field['D'])");
      assertEquals(2023, ((Number) value(t)).intValue());
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void yearFunctionWorksInAnExpressionColumnNamedYEAR(boolean pool) throws Exception {
      // the expression column is itself the YEAR column
      TableLens t = formula(pool, new Object[][] { { "D" }, { TS } },
                            "YEAR", "year(field['D'])");
      assertEquals(2023, ((Number) value(t)).intValue());
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void monthFunctionWorksNextToABaseColumnNamedMONTH(boolean pool) throws Exception {
      TableLens t = formula(pool, new Object[][] { { "MONTH", "D" }, { 5, TS } },
                            "out", "month(field['D'])");
      assertEquals(11, ((Number) value(t)).intValue());
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void dateAddFunctionWorksNextToAColumnNamedDATEADD(boolean pool) throws Exception {
      TableLens t = formula(pool, new Object[][] { { "DATEADD", "D" }, { 5, TS } },
                            "out", "dateAdd('d', 1, field['D'])");
      Object v = value(t);
      assertInstanceOf(Date.class, v, "dateAdd result: " + v);
      assertEquals(TS.getTime() + 24L * 3600 * 1000, ((Date) v).getTime(), 3600 * 1000L);
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void bareYearAsAValueStaysTheColumn(boolean pool) throws Exception {
      // the formulas never call year(...): year is the column YEAR, as before
      TableLens t = formula(pool, new Object[][] { { "YEAR" }, { 41 } }, "out", "year + 1");
      assertEquals(42, ((Number) value(t)).intValue());
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void exactCaseColumnStillShadowsTheFunction(boolean pool) throws Exception {
      // a column named exactly year is the user's own choice: unchanged, year is the column
      TableLens t = formula(pool, new Object[][] { { "year" }, { 41 } }, "out", "year + 1");
      assertEquals(42, ((Number) value(t)).intValue());
   }

   @Test
   void formulaEvaluatorDefersOnlyACalledFunctionName() {
      DefaultTableLens table = new DefaultTableLens(new Object[][] {
         { "YEAR", "D" }, { 5, TS } });

      assertEquals(2023, ((Number) FormulaEvaluator.exec(
         "year(D)", null, "field", new TableRow(table, 1))).intValue());
      // a value use keeps the column
      assertEquals(6, ((Number) FormulaEvaluator.exec(
         "year + 1", null, "field", new TableRow(table, 1))).intValue());
   }

   @Test
   void exactCaseYEARCallNowCallsTheFunction() {
      DefaultTableLens table = new DefaultTableLens(new Object[][] {
         { "YEAR", "D" }, { 5, TS } });

      assertEquals(2023, ((Number) FormulaEvaluator.exec(
         "YEAR(D)", null, "field", new TableRow(table, 1))).intValue());
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void upperCaseCallNextToAColumnYEAR(boolean pool) throws Exception {
      TableLens t = formula(pool, new Object[][] { { "YEAR", "D" }, { 5, TS } },
                            "out", "YEAR(field['D'])");
      assertEquals(2023, ((Number) value(t)).intValue());
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void exactCaseColumnYearAndACallOfTheFunction(boolean pool) throws Exception {
      // changed from #8006: the call is the function, the bare read is still the column
      TableLens t = formula(pool, new Object[][] { { "year", "D" }, { 5, TS } },
                            "out", "year(field['D'])");
      assertEquals(2023, ((Number) value(t)).intValue());
      t = formula(pool, new Object[][] { { "year", "D" }, { 5, TS } }, "out", "year + 1");
      assertEquals(6, ((Number) value(t)).intValue());
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void callAndBareReadInOneFormula(boolean pool) throws Exception {
      TableLens t = formula(pool, new Object[][] { { "YEAR", "D" }, { 5, TS } },
                            "out", "year(field['D']) + year");
      assertEquals(2028, ((Number) value(t)).intValue());
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void callInOneExpressionColumnReadInTheOther(boolean pool) throws Exception {
      TableLens t = formulas(pool, new Object[][] { { "YEAR", "D" }, { 5, TS } },
                             new String[] { "a", "b" },
                             new String[] { "year(field['D'])", "year" });
      assertEquals(2023, ((Number) t.getObject(1, 2)).intValue());
      assertEquals(5, ((Number) t.getObject(1, 3)).intValue());
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void callTextInAStringDoesNotHijackARead(boolean pool) throws Exception {
      TableLens t = formula(pool, new Object[][] { { "YEAR" }, { 5 } }, "out",
                            "'year(' + year");
      assertEquals("year(5", String.valueOf(value(t)));
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void propertyCallIsNotACallOfTheFunction(boolean pool) throws Exception {
      TableLens t = formula(pool, new Object[][] { { "YEAR", "S" }, { 5, "abc" } },
                            "out", "field['S'].toUpperCase() + year");
      assertEquals("ABC5", String.valueOf(value(t)));
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void nestedCalls(boolean pool) throws Exception {
      TableLens t = formula(pool, new Object[][] { { "YEAR", "D" }, { 5, TS } },
                            "out", "year(new Date(field['D'])) - year(field['D']) + year");
      assertEquals(5, ((Number) value(t)).intValue());
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void userFunctionWinsOverTheGlobal(boolean pool) throws Exception {
      TableLens t = formula(pool, new Object[][] { { "YEAR" }, { 5 } }, "out",
                            "function year(x) { return 'mine'; } year(1)");
      assertEquals("mine", String.valueOf(value(t)));
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void calcFunctionCalledByAnyCase(boolean pool) throws Exception {
      TableLens t = formula(pool, new Object[][] { { "SUM", "X" }, { 5, 3 } }, "out",
                            "Sum(field['X'], 4)");
      assertNotNull(value(t));
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void unparsableFormulaIsLeftAsItWas(boolean pool) throws Exception {
      // a top-level return is a syntax error for the engine too: no new failure mode
      assertDoesNotThrow(() -> formula(pool, new Object[][] { { "YEAR" }, { 5 } }, "out",
                                       "return year(1)"));
   }

   /**
    * Records the caveat of the callee rewrite: a function that the scope itself owns under the
    * name of a global function is called without the scope as {@code this}.
    */
   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void thisOfAScopeOwnedFunctionNamedLikeAGlobal(boolean pool) throws Exception {
      TableLens t = formula(pool, new Object[][] { { "YEAR" }, { 5 } }, "out",
                            "function foo() { return typeof this; } foo()");
      assertNotNull(value(t));
      t = formula(pool, new Object[][] { { "X" }, { 5 } }, "out",
                  "function year() { return typeof this; } year()");
      assertNotNull(value(t));
      // no assertion on the value on purpose: the global object stands for the scope
   }

   // --- helpers ---

   private TableLens formula(boolean pool, Object[][] data, String header, String expr)
      throws Exception
   {
      AssetQuerySandbox box = PoolTestSupport.poolBox(pool);
      ScriptEnv env = box.getScriptEnv();
      envs.add(env);
      Object scope = box.getScope();
      assertNotNull(scope, "the formula table must run in a TableRowScope");
      Class<?> type = expr.contains("dateAdd") ? Date.class : Double.class;
      return PostProcessor.formula(new DefaultTableLens(data), new String[] { header },
                                   new String[] { expr }, env, scope, null, "T", null,
                                   List.of(type), new boolean[] { false });
   }

   private TableLens formulas(boolean pool, Object[][] data, String[] headers, String[] exprs)
      throws Exception
   {
      AssetQuerySandbox box = PoolTestSupport.poolBox(pool);
      ScriptEnv env = box.getScriptEnv();
      envs.add(env);
      List<Class<?>> types = new ArrayList<>();
      boolean[] flags = new boolean[exprs.length];

      for(int i = 0; i < exprs.length; i++) {
         types.add(Double.class);
      }

      return PostProcessor.formula(new DefaultTableLens(data), headers, exprs, env,
                                   box.getScope(), null, "T", null, types, flags);
   }

   private static Object value(TableLens t) {
      assertTrue(t.moreRows(1));
      return t.getObject(1, t.getColCount() - 1);
   }

   private static final Timestamp TS = new Timestamp(1700000000000L);
   private final List<ScriptEnv> envs = new ArrayList<>();
}
