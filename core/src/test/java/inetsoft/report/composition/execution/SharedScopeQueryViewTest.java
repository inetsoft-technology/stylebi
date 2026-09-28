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
package inetsoft.report.composition.execution;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.report.script.formula.AssetQueryScope;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.ExpressionValue;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

/**
 * Bug #77123 (H3a D-1): in pool mode (bug #76960, spec §6.5) the formula steps no longer write
 * the query's mode and parameters onto the sandbox's shared {@link AssetQueryScope}, so a script
 * that still ran on that scope read worksheet tables in mode 0. Each case first runs a formula
 * step of a RUNTIME_MODE query (verbatim from AssetQuery's expression-column step), which on
 * main leaves that mode on the shared scope, then evaluates a script at one of the sites that
 * used the shared scope, and checks that pool on reads the table in the same mode as pool off.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SharedScopeQueryViewTest {
   @Test
   void poolModeSqlMergedPreConditionReadsTablesInTheQueryMode() throws Exception {
      assertEquals(List.of(QUERY_MODE), preCondition(true));
   }

   @Test
   void poolOffSqlMergedPreConditionIsUnchanged() throws Exception {
      assertEquals(List.of(QUERY_MODE), preCondition(false));
   }

   @Test
   void poolModeWorksheetGlobalInAFormulaColumnIsTheQueryView() throws Exception {
      Harness h = new Harness(true);
      // T1.length (4 rows, header included) + 100 when worksheet.parameter is the query's parameters
      assertEquals(104.0, h.formulaColumn(
         "worksheet['T1'].length + (worksheet.parameter.q == 'queryValue' ? 100 : 0)"));
      assertEquals(List.of(QUERY_MODE), h.modes);
   }

   @Test
   void poolOffWorksheetGlobalInAFormulaColumnIsUnchanged() throws Exception {
      Harness h = new Harness(false);
      assertEquals(104.0, h.formulaColumn(
         "worksheet['T1'].length + (worksheet.parameter.q == 'queryValue' ? 100 : 0)"));
      assertEquals(List.of(QUERY_MODE), h.modes);
   }

   @Test
   void poolModeWorksheetNamedTableStillWinsOverTheViewItself() throws Exception {
      Harness h = new Harness(true);
      h.ws.addAssembly(new EmbeddedTableAssembly(h.ws, "worksheet"));
      Object value = h.formulaColumn("worksheet == null ? 'none' : 'table'");
      assertEquals("table", value);
      // a table named worksheet resolves to the table, exactly as on the shared scope
      AssetQueryScope view = h.box.getScope().queryView(h.vars, QUERY_MODE);
      assertNotSame(view, view.getMember("worksheet"));
   }

   @Test
   void poolModeCacheKeyVariableScriptReadsTablesInTheQueryMode() throws Exception {
      assertEquals(List.of(QUERY_MODE), cacheKeyVariable(true));
   }

   @Test
   void poolOffCacheKeyVariableScriptIsUnchanged() throws Exception {
      assertEquals(List.of(QUERY_MODE), cacheKeyVariable(false));
   }

   /**
    * Evaluate a JS condition value that reads T1 the way a pre-condition merged into SQL does
    * (PreConditionListHandler.getExpression), and return the modes T1 was read in.
    */
   private static List<Integer> preCondition(boolean pool) throws Exception {
      Harness h = new Harness(pool);
      h.formulaStep();

      PreAssetQuery query = Mockito.mock(PreAssetQuery.class, Mockito.CALLS_REAL_METHODS);
      setField(query, "box", h.box);
      setField(query, "mode", QUERY_MODE);

      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.EQUAL_TO);
      cond.setType(XSchema.INTEGER);
      ExpressionValue value = new ExpressionValue();
      value.setExpression("T1.length");
      value.setType(ExpressionValue.JAVASCRIPT);
      cond.addValue(value);

      Class<?> type = Class.forName(PreAssetQuery.class.getName() + "$PreConditionListHandler");
      Constructor<?> ctor = type.getDeclaredConstructor(PreAssetQuery.class);
      ctor.setAccessible(true);
      Object handler = ctor.newInstance(query);
      Method getExpression = type.getDeclaredMethod(
         "getExpression", inetsoft.uql.Condition.class, Object.class, VariableTable.class,
         boolean.class);
      getExpression.setAccessible(true);

      try {
         getExpression.invoke(handler, cond, value, new VariableTable(), true);
      }
      catch(InvocationTargetException ex) {
         // only the script evaluation matters here; the SQL expression built from its value
         // needs a real query
         if(h.modes.isEmpty()) {
            throw ex;
         }
      }

      assertFalse(h.modes.isEmpty(), "the script did not read T1");
      return h.modes;
   }

   /**
    * Build a data-cache key for a sandbox whose parameters hold a JavaScript variable value
    * that reads T1, and return the modes T1 was read in.
    */
   private static List<Integer> cacheKeyVariable(boolean pool) throws Exception {
      Harness h = new Harness(pool);
      ExpressionValue value = new ExpressionValue();
      value.setExpression("T1.length");
      value.setType(ExpressionValue.JAVASCRIPT);
      h.boxVars.put("x", value);
      h.formulaStep();

      EmbeddedTableAssembly t2 = new EmbeddedTableAssembly(h.ws, "T2");
      h.ws.addAssembly(t2);
      AssetDataCache.getCacheKey(t2, h.box, null, QUERY_MODE, false, null);

      assertFalse(h.modes.isEmpty(), "the script did not read T1");
      return h.modes;
   }

   private static void setField(Object obj, String name, Object value) throws Exception {
      Field field = PreAssetQuery.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(obj, value);
   }

   private static final class Harness {
      Harness(boolean pool) throws Exception {
         this.pool = pool;
         box = PoolTestSupport.poolBox(pool);
         ws.addAssembly(new EmbeddedTableAssembly(ws, "T1"));
         doReturn(ws).when(box).getWorksheet();
         boxVars.put("p", "boxValue");
         doReturn(boxVars).when(box).getVariableTable();
         DefaultTableLens t1 = new DefaultTableLens(new Object[][] {{"a"}, {1}, {2}, {3}});
         doAnswer(inv -> {
            modes.add(inv.getArgument(1));
            return t1;
         }).when(box).getTableLens(eq("T1"), anyInt(), any());
         // the query's own parameters, as AssetQuerySandbox.executeQuery builds them
         vars.put("q", "queryValue");
         vars.put("__HINT_PREVIEW__", "false");
      }

      /**
       * The scope choice of AssetQuery's expression-column step, verbatim: on main it writes
       * the query's parameters and mode onto the shared scope.
       */
      AssetQueryScope formulaStep() {
         AssetQueryScope scope = box.getScope();

         if(box.isScriptPoolMode()) {
            scope = scope.queryView(vars, QUERY_MODE);
         }
         else {
            scope.setVariableTable(vars);
            scope.setMode(QUERY_MODE);
         }

         return scope;
      }

      /** Run a formula column the way AssetQuery hands it its scope. */
      Object formulaColumn(String script) {
         AssetQueryScope scope = formulaStep();
         FormulaTableLens lens = new FormulaTableLens(
            new DefaultTableLens(new Object[][] {{"v"}, {1}}), new String[] {"f"},
            new String[] {script}, box.getScriptEnv(),
            box.isScriptPoolMode() ? scope : box.getScope());
         assertTrue(lens.moreRows(1));
         return lens.getObject(1, 1);
      }

      final boolean pool;
      final AssetQuerySandbox box;
      final Worksheet ws = new Worksheet();
      final VariableTable boxVars = new VariableTable();
      final VariableTable vars = new VariableTable();
      final List<Integer> modes = new CopyOnWriteArrayList<>();
   }

   private static final int QUERY_MODE = AssetQuerySandbox.RUNTIME_MODE;
}
