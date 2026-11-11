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
 * DATEADD) must not hide the function when a formula calls it by its lowercase name,
 * {@code year(field['D'])}. The fix axis is the identifier resolution in {@link TableRowScope}:
 * defer to the global function only if the name is a script function, the formulas call
 * {@code name(}, and no column is named exactly like the identifier. A bare {@code year} as a
 * value, and an exact-case column, keep resolving to the column.
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
   void exactCaseYEARCallStaysTheColumn() {
      // documents the unchanged shadowing: a column named exactly YEAR is the user's own
      // choice, so YEAR(...) is still a call of the column value
      DefaultTableLens table = new DefaultTableLens(new Object[][] {
         { "YEAR", "D" }, { 5, TS } });

      // the evaluator logs the failed call and returns null
      assertNull(FormulaEvaluator.exec("YEAR(D)", null, "field", new TableRow(table, 1)));
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

   private static Object value(TableLens t) {
      assertTrue(t.moreRows(1));
      return t.getObject(1, t.getColCount() - 1);
   }

   private static final Timestamp TS = new Timestamp(1700000000000L);
   private final List<ScriptEnv> envs = new ArrayList<>();
}
