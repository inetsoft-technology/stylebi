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
 * Bug #77999: a column named like a builtin that {@link TableRowScope} defers to (Date when
 * a formula contains {@code new Date(}, Math, Array) must not hide that builtin. Under GraalJS
 * the scope is reached through {@code with(...)}, where a name the scope reports present is
 * read from it even when its value is null, so {@code new Date(...)} failed with "TypeError:
 * instantiate on null failed due to: Message not supported".
 *
 * <p>The formula tables are given a non-null scope, as the worksheet path does: without one
 * the formulas do not run in the TableRowScope and the bug does not show.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TableRowScopeBuiltinNameTest {
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
   void newDateWorksInAnExpressionColumnNamedDate(boolean pool) throws Exception {
      TableLens t = formula(pool, new Object[][] { { "EXEC_TIMESTAMP" }, { TS } },
                            "Date", "new Date(field['EXEC_TIMESTAMP'])");
      assertDate(t);
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void newDateWorksNextToABaseColumnNamedDate(boolean pool) throws Exception {
      // a lowercase name and a qualified name both resolve as Date in TableRow
      assertDate(formula(pool, new Object[][] { { "EXEC_TIMESTAMP", "date" }, { TS, 1 } },
                         "out", "new Date(field['EXEC_TIMESTAMP'])"));
      assertDate(formula(pool, new Object[][] { { "EXEC_TIMESTAMP", "X.DATE" }, { TS, 1 } },
                         "out", "new Date(field['EXEC_TIMESTAMP'])"));
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void aMathColumnDoesNotHideTheBuiltin(boolean pool) throws Exception {
      TableLens math = formula(pool, new Object[][] { { "Math", "v" }, { 7, 2.6 } },
                               "out", "Math.round(field['v'])");
      assertEquals(3, ((Number) value(math)).intValue());
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void anArrayColumnDoesNotHideTheBuiltin(boolean pool) throws Exception {
      TableLens array = formula(pool, new Object[][] { { "Array" }, { 7 } },
                                "out", "Array.isArray([1]) ? 1 : 0");
      assertEquals(1, ((Number) value(array)).intValue());
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void bareDateReadsTheColumnWithoutNewDate(boolean pool) throws Exception {
      // no "new Date(" in the formula: Date is the column, as in Rhino
      TableLens t = formula(pool, new Object[][] { { "Date" }, { 42 } }, "out", "Date + 1");
      assertEquals(43, ((Number) value(t)).intValue());
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void fieldDateReadsTheColumnNextToNewDate(boolean pool) throws Exception {
      TableLens t = formula(pool, new Object[][] { { "Date" }, { 42 } }, "out",
                            "field['Date'] + new Date(0).getTime()");
      assertEquals(42, ((Number) value(t)).intValue());
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void reporterBlockWithDateAndDateTimeExpressionColumns(boolean pool) throws Exception {
      // the reported User Sessions block: qualified timestamp columns and two expression
      // columns, Date and DateTime, both new Date(field[...]); the DateTime column failed too,
      // only because its sibling expression column is named Date
      AssetQuerySandbox box = PoolTestSupport.poolBox(pool);
      ScriptEnv env = box.getScriptEnv();
      envs.add(env);
      Object scope = box.getScope();
      assertNotNull(scope, "the formula table must run in a TableRowScope");
      Timestamp exec = new Timestamp(1700000500000L);
      TableLens t = PostProcessor.formula(
         new DefaultTableLens(new Object[][] {
            { "SR_SESSION1.OP_TIMESTAMP", "SR_SESSION1.EXEC_TIMESTAMP" }, { TS, exec } }),
         new String[] { "Date", "DateTime" },
         new String[] { "new Date(field['SR_SESSION1.OP_TIMESTAMP'])",
                        "new Date(field['SR_SESSION1.EXEC_TIMESTAMP'])" },
         env, scope, null, "T", null, List.of(Date.class, Date.class),
         new boolean[] { false, false });

      assertTrue(t.moreRows(1));
      Object date = t.getObject(1, t.getColCount() - 2);
      Object dateTime = t.getObject(1, t.getColCount() - 1);
      assertInstanceOf(Date.class, date, "Date column: " + date);
      assertInstanceOf(Date.class, dateTime, "DateTime column: " + dateTime);
      assertEquals(TS.getTime(), ((Date) date).getTime());
      assertEquals(exec.getTime(), ((Date) dateTime).getTime());
   }

   @Test
   void formulaEvaluatorRowScopeDoesNotHideTheBuiltins() {
      DefaultTableLens table = new DefaultTableLens(new Object[][] {
         { "Date", "Math", "Array" }, { 42, 7, 8 } });

      assertEquals(5, ((Number) FormulaEvaluator.exec(
         "new Date(5).getTime()", null, "field", new TableRow(table, 1))).intValue());
      assertEquals(3, ((Number) FormulaEvaluator.exec(
         "Math.round(2.6)", null, "field", new TableRow(table, 1))).intValue());
      assertEquals(Boolean.TRUE, FormulaEvaluator.exec(
         "Array.isArray([1])", null, "field", new TableRow(table, 1)));
      // without "new Date(" the bare name is still the column
      assertEquals(43, ((Number) FormulaEvaluator.exec(
         "Date + 1", null, "field", new TableRow(table, 1))).intValue());

      // with an outer scope, the second branch of exec
      PoolTestSupport.MapScope outer = new PoolTestSupport.MapScope();
      assertEquals(5, ((Number) FormulaEvaluator.exec(
         "new Date(5).getTime()", outer, "field", new TableRow(table, 1))).intValue());
   }

   // --- helpers ---

   private TableLens formula(boolean pool, Object[][] data, String header, String expr)
      throws Exception
   {
      return formula(pool, data, header, expr, expr.contains("new Date(field") ?
         Date.class : Double.class);
   }

   private TableLens formula(boolean pool, Object[][] data, String header, String expr,
                             Class<?> type)
      throws Exception
   {
      AssetQuerySandbox box = PoolTestSupport.poolBox(pool);
      ScriptEnv env = box.getScriptEnv();
      envs.add(env);
      Object scope = box.getScope();
      assertNotNull(scope, "the formula table must run in a TableRowScope");
      return PostProcessor.formula(new DefaultTableLens(data), new String[] { header },
                                   new String[] { expr }, env, scope, null, "T", null,
                                   List.of(type), new boolean[] { false });
   }

   private static Object value(TableLens t) {
      assertTrue(t.moreRows(1));
      return t.getObject(1, t.getColCount() - 1);
   }

   private static void assertDate(TableLens t) {
      Object v = value(t);
      assertInstanceOf(Date.class, v, "new Date(...) result: " + v);
      assertEquals(TS.getTime(), ((Date) v).getTime());
   }

   private static final Timestamp TS = new Timestamp(1700000000000L);
   private final List<ScriptEnv> envs = new ArrayList<>();
}
