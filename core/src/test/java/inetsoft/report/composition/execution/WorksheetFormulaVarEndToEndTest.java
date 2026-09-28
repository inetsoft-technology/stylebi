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

import inetsoft.mv.MVManager;
import inetsoft.report.TableLens;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Testing #77123 end to end: a worksheet embedded table with a {@code var} accumulator
 * expression column, run by a real {@link AssetQuerySandbox} (data cache, column map and
 * post-processing chain), counts 1..N on every compile path and in both pool modes, whether
 * the table is read in 100-row pages, read in full as an export does, or read by a viewsheet
 * table bound to it; two tables of one sandbox with the same var name don't share it; a reset
 * table starts over; and a post condition, a sort and a summary over the column see the
 * accumulated values.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class, WorksheetFormulaVarEndToEndTest.TestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetFormulaVarEndToEndTest {
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      // the viewsheet table query asks the MV manager whether its table is materialized
      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   @AfterEach
   void tearDown() {
      SreeEnv.remove(PoolConfig.ENABLED);

      for(AssetQuerySandbox box : boxes) {
         if(box.peekScriptEnv() instanceof WorksheetScriptEnv env) {
            env.retire();
         }
      }

      boxes.clear();
   }

   static Stream<Arguments> modes() {
      List<Arguments> args = new ArrayList<>();

      for(boolean pool : new boolean[] { false, true }) {
         for(String path : new String[] { "plain", "eval", "multi" }) {
            args.add(Arguments.of(pool, path));
         }
      }

      return args.stream();
   }

   @ParameterizedTest(name = "pool={0} path={1}")
   @MethodSource("modes")
   void aWorksheetVarAccumulatorCountsEveryRowHoweverTheTableIsRead(boolean pool, String path)
      throws Exception
   {
      String f = formula(path);
      // preview: the table is read page by page, past the rows the query computed up front
      assertCounts(pages(box(ws("A", f), pool).getTableLens("A", RUNTIME), "out"), "pages");
      assertCounts(pages(box(ws("A", f), pool).getTableLens("A", LIVE), "out"), "live pages");

      // export: the whole table at once
      TableLens all = box(ws("A", f), pool).getTableLens("A", RUNTIME);
      all.moreRows(TableLens.EOT);
      assertCounts(sequential(all, "out"), "export");

      // a viewsheet table bound to the worksheet table
      Object data = vsTable(ws("A", f), pool).getData("TableV");
      TableLens vs = data instanceof VSDataSet ? ((VSDataSet) data).getTable() : (TableLens) data;
      vs.moreRows(TableLens.EOT);
      assertCounts(sequential(vs, "out"), "viewsheet table");
   }

   @ParameterizedTest(name = "pool={0} path={1}")
   @MethodSource("modes")
   void twoTablesOfOneSandboxDoNotShareTheirVar(boolean pool, String path) throws Exception {
      String f = formula(path);
      Worksheet ws = ws("A", f);
      addTable(ws, "B", f);
      addTable(ws, "C", "typeof acc == 'undefined' ? -1 : acc");
      AssetQuerySandbox box = box(ws, pool);
      TableLens a = box.getTableLens("A", RUNTIME);
      TableLens b = box.getTableLens("B", RUNTIME);
      int ca = col(a, "out");
      int cb = col(b, "out");
      double[] va = new double[ROWS + 1];
      double[] vb = new double[ROWS + 1];

      // interleaved pages: A and B run their batches in turn on the same env
      for(int s = 1; s <= ROWS; s += 100) {
         a.moreRows(s + 99);
         b.moreRows(s + 99);

         for(int r = s; r < s + 100 && r <= ROWS; r++) {
            va[r] = num(a.getObject(r, ca));
            vb[r] = num(b.getObject(r, cb));
         }
      }

      assertCounts(va, "A");
      assertCounts(vb, "B");

      // neither leaves its var to another table or to the env's global scope
      TableLens c = box.getTableLens("C", RUNTIME);
      c.moreRows(TableLens.EOT);
      double[] vc = sequential(c, "out");

      for(int r = 1; r <= ROWS; r++) {
         assertEquals(-1.0, vc[r], "reader row " + r);
      }

      ScriptEnv env = box.getScriptEnv();
      assertEquals("undef", env.exec(env.compile(
         "typeof acc == 'undefined' ? 'undef' : acc"), null, null, null));
   }

   @ParameterizedTest(name = "pool={0} path={1}")
   @MethodSource("modes")
   void aResetTableStartsOver(boolean pool, String path) throws Exception {
      Worksheet ws = ws("A", formula(path));
      AssetQuerySandbox box = box(ws, pool);
      assertCounts(pages(box.getTableLens("A", RUNTIME), "out"), "first run");

      box.resetTableLens("A");
      AssetDataCache.getCache().clearCache();
      assertCounts(pages(box.getTableLens("A", RUNTIME), "out"), "after reset");

      // a selection-like runtime condition on a base column, then the table runs again
      EmbeddedTableAssembly a = (EmbeddedTableAssembly) ws.getAssembly("A");
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.GREATER_THAN);
      cond.setType(XSchema.INTEGER);
      cond.addValue(1000);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(a.getColumnSelection(false).getAttribute("id"), cond, 0));
      a.setPreRuntimeConditionList(list);
      box.resetTableLens("A");
      AssetDataCache.getCache().clearCache();
      TableLens t = box.getTableLens("A", RUNTIME);
      t.moreRows(TableLens.EOT);
      int cid = col(t, "id");
      int cout = col(t, "out");
      assertEquals(ROWS - 1000, t.getRowCount() - 1);

      // the expression column runs before the condition: its count is the base row number
      for(int r = 1; r < t.getRowCount(); r++) {
         assertEquals(num(t.getObject(r, cid)), num(t.getObject(r, cout)), "row " + r);
      }
   }

   @ParameterizedTest(name = "pool={0} path={1}")
   @MethodSource("modes")
   void conditionSortAndSummaryReadTheAccumulatedValues(boolean pool, String path)
      throws Exception
   {
      String f = formula(path);

      // post condition on the expression column
      Worksheet ws = ws("A", f);
      EmbeddedTableAssembly a = (EmbeddedTableAssembly) ws.getAssembly("A");
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.GREATER_THAN);
      cond.setType(XSchema.DOUBLE);
      cond.addValue(ROWS - 10.0);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(a.getColumnSelection(false).getAttribute("out"), cond, 0));
      a.setPostConditionList(list);
      TableLens t = box(ws, pool).getTableLens("A", RUNTIME);
      t.moreRows(TableLens.EOT);
      int c = col(t, "out");
      assertEquals(10, t.getRowCount() - 1, "condition rows");

      for(int r = 1; r <= 10; r++) {
         assertEquals(ROWS - 10.0 + r, num(t.getObject(r, c)), "condition row " + r);
      }

      // descending sort on the expression column
      ws = ws("A", f);
      a = (EmbeddedTableAssembly) ws.getAssembly("A");
      SortInfo sort = new SortInfo();
      SortRef ref = new SortRef(a.getColumnSelection(false).getAttribute("out"));
      ref.setOrder(XConstants.SORT_DESC);
      sort.addSort(ref);
      a.setSortInfo(sort);
      t = box(ws, pool).getTableLens("A", RUNTIME);
      t.moreRows(TableLens.EOT);
      c = col(t, "out");
      assertEquals(ROWS, t.getRowCount() - 1, "sorted rows");

      for(int r = 1; r <= ROWS; r++) {
         assertEquals(ROWS + 1.0 - r, num(t.getObject(r, c)), "sorted row " + r);
      }

      // sum of the expression column grouped by id % 3
      ws = ws("A", f);
      a = (EmbeddedTableAssembly) ws.getAssembly("A");
      ColumnSelection cols = a.getColumnSelection(false);
      AggregateInfo agg = new AggregateInfo();
      agg.addGroup(new GroupRef(cols.getAttribute("grp")));
      agg.addAggregate(new AggregateRef(cols.getAttribute("out"), AggregateFormula.SUM));
      a.setAggregateInfo(agg);
      t = box(ws, pool).getTableLens("A", RUNTIME);
      t.moreRows(TableLens.EOT);
      assertEquals(4, t.getRowCount(), "groups");

      for(int r = 1; r < t.getRowCount(); r++) {
         int grp = ((Number) t.getObject(r, col(t, "grp"))).intValue();
         double expected = 0;

         for(int i = 1; i <= ROWS; i++) {
            expected += i % 3 == grp ? i : 0;
         }

         assertEquals(expected, num(t.getObject(r, col(t, "out"))), "sum of group " + grp);
      }
   }

   // --- helpers ---

   private static String formula(String path) {
      return switch(path) {
      case "plain" -> "var acc = (acc || 0) + field['value']; acc";
      case "eval" -> "var acc = (acc || 0) + this.field['value']; acc";
      default -> "var acc = (acc || 0) + field['value']; if(acc < 0) { acc = 0; } acc";
      };
   }

   private AssetQuerySandbox box(Worksheet ws, boolean pool) {
      SreeEnv.setProperty(PoolConfig.ENABLED, Boolean.toString(pool));
      // the data keys are content based: without this a run could read another's rows
      AssetDataCache.getCache().clearCache();
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      boxes.add(box);
      assertEquals(pool, box.isScriptPoolMode());
      return box;
   }

   private ViewsheetSandbox vsTable(Worksheet ws, boolean pool) throws Exception {
      Viewsheet vs = new Viewsheet();
      // as RefreshVariableTest: the base worksheet is wired directly
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, ws);
      TableVSAssembly table = new TableVSAssembly(vs, "TableV");
      table.setSourceInfo(new SourceInfo(XSourceInfo.ASSET, null, "A"));
      ColumnSelection cols = new ColumnSelection();

      for(String name : new String[] { "value", "id", "out" }) {
         cols.addAttribute(new ColumnRef(new AttributeRef(null, name)));
      }

      table.setColumnSelection(cols);
      vs.addAssembly(table);
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         "test/WorksheetFormulaVarEndToEndTest", null,
         OrganizationManager.getInstance().getCurrentOrgID());
      ViewsheetSandbox vbox = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null,
                                                   false, entry);
      Field wbox = ViewsheetSandbox.class.getDeclaredField("wbox");
      wbox.setAccessible(true);
      wbox.set(vbox, box(ws, pool));
      return vbox;
   }

   private static Worksheet ws(String name, String formula) {
      Worksheet ws = new Worksheet();
      addTable(ws, name, formula);
      return ws;
   }

   private static void addTable(Worksheet ws, String name, String formula) {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);
      Object[][] data = new Object[ROWS + 1][];
      data[0] = new Object[] { "value", "id", "grp" };

      for(int i = 1; i <= ROWS; i++) {
         data[i] = new Object[] { 1, i, i % 3 };
      }

      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.INTEGER, XSchema.INTEGER, XSchema.INTEGER }, data));
      ws.addAssembly(table);
      // the output column is not named like the var: such a var is the cell
      ExpressionRef exp = new ExpressionRef(null, "out");
      exp.setExpression(formula);
      ColumnRef column = new ColumnRef(exp);
      column.setDataType(XSchema.DOUBLE);
      ColumnSelection columns = table.getColumnSelection(false);
      columns.addAttribute(column);
      table.setColumnSelection(columns, false);
   }

   private static int col(TableLens t, String name) {
      t.moreRows(0);

      for(int c = 0; c < t.getColCount(); c++) {
         if(name.equals(String.valueOf(t.getObject(0, c)))) {
            return c;
         }
      }

      throw new AssertionError("no column " + name);
   }

   private static double[] pages(TableLens t, String name) {
      int c = col(t, name);
      double[] v = new double[ROWS + 1];

      for(int s = 1; s <= ROWS; s += 100) {
         t.moreRows(s + 99);

         for(int r = s; r < s + 100 && r <= ROWS; r++) {
            v[r] = num(t.getObject(r, c));
         }
      }

      return v;
   }

   private static double[] sequential(TableLens t, String name) {
      int c = col(t, name);
      double[] v = new double[ROWS + 1];

      for(int r = 1; r <= ROWS && t.moreRows(r); r++) {
         v[r] = num(t.getObject(r, c));
      }

      return v;
   }

   private static void assertCounts(double[] v, String what) {
      for(int r = 1; r <= ROWS; r++) {
         if(v[r] != r) {
            fail(what + ": row " + r + " is " + v[r] + ", expected " + r);
         }
      }
   }

   private static double num(Object o) {
      return o instanceof Number ? ((Number) o).doubleValue() : Double.NaN;
   }

   // more rows than a worksheet query computes up front (about 1000 pool off, 1800 on), so
   // the pages run further batches
   private static final int ROWS = 3000;
   private static final int RUNTIME = AssetQuerySandbox.RUNTIME_MODE;
   private static final int LIVE = AssetQuerySandbox.LIVE_MODE;
   private final List<AssetQuerySandbox> boxes = new ArrayList<>();
}
