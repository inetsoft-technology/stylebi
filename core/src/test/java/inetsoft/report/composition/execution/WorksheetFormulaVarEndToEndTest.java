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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import inetsoft.util.script.ScriptStateLint;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.IntToDoubleFunction;
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
@Tag("slow")
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

   /**
    * The tester's failing formulas of PR #5806 (04-verify §3): an owned var not assigned yet
    * reads as undefined, not null, so the "initialize once" idioms give 100, 101, ... and
    * "a", "aa", ... (not 1, 2, ... and "nulla").
    */
   @ParameterizedTest(name = "pool={0} path={1}")
   @MethodSource("modes")
   void aTypeofUndefinedInitializerRunsOnceOnTheFirstRow(boolean pool, String path)
      throws Exception
   {
      String prefix = switch(path) {
      case "plain" -> "";
      case "eval" -> "var t0 = this.field['id']; ";
      default -> "if(field['id'] < 0) { throw 'negative'; } ";
      };
      String[] formulas = {
         "var c = (typeof c == 'undefined') ? 100 : c + 1; c - 99",
         "var u = (u === undefined) ? 100 : u + 1; u - 99",
         "var s = (typeof s == 'undefined') ? '' : s; s = s + 'a'; s.length"
      };

      for(String f : formulas) {
         assertCounts(pages(box(ws("A", prefix + f), pool).getTableLens("A", RUNTIME), "out"),
                      prefix + f);
      }
   }

   /**
    * undefined and null stay apart for an owned var on every row, including a transition
    * from one to the other and back that is stored and read on the next row. Each result is
    * a code of four checks: {@code === undefined} (1), {@code == null} (2),
    * {@code typeof == 'undefined'} (4), {@code typeof == 'object'} (8), so undefined is 7
    * and null is 10. The {@code if} goes after the declarations: before them, a body is still
    * one piece and compiles on the plain path, not the multi-statement one.
    */
   @ParameterizedTest(name = "pool={0} path={1}")
   @MethodSource("modes")
   void anOwnedVarKeepsUndefinedAndNullApartFromRowToRow(boolean pool, String path)
      throws Exception
   {
      // declarations, result expression, expected value of row r
      List<Object[]> cases = List.of(
         new Object[] { "var c = (typeof c == 'undefined') ? 100 : c + 1;", "c",
                        (IntToDoubleFunction) r -> 99 + r },
         new Object[] { "var s = (typeof s == 'undefined' ? '' : s) + 'b';",
                        "(s.indexOf('null') >= 0 || s.indexOf('undefined') >= 0 ? -1 : s.length)",
                        (IntToDoubleFunction) r -> r },
         new Object[] { "var w;", code("w"), (IntToDoubleFunction) r -> 7 },
         new Object[] { "var z = null;", code("z"), (IntToDoubleFunction) r -> 10 },
         new Object[] { "var e = undefined;", code("e"), (IntToDoubleFunction) r -> 7 },
         new Object[] { "var arr = [1]; var a9 = arr[99];", code("a9"),
                        (IntToDoubleFunction) r -> 7 },
         new Object[] { "function f0() {} var q = f0();", code("q"),
                        (IntToDoubleFunction) r -> 7 },
         // null stored on row 1 is read as null (not undefined) on row 2
         new Object[] { "var n; var k = " + code("n") + "; n = null;", "k",
                        (IntToDoubleFunction) r -> r == 1 ? 7 : 10 },
         // a number, then undefined stored again: the next row reads undefined, not the number
         new Object[] { "var m; var k2 = " + code("m") +
                           "; m = (field['id'] % 2 == 0) ? undefined : 5;", "k2",
                        (IntToDoubleFunction) r -> r % 2 == 1 ? 7 : 0 });

      for(Object[] c : cases) {
         String decls = (String) c[0];
         String result = (String) c[1];
         String f = switch(path) {
         case "plain" -> decls + " " + result;
         case "eval" -> decls + " (this, " + result + ")";
         default -> decls + " if(field['id'] < 0) { throw 'negative'; } " + result;
         };
         IntToDoubleFunction expected = (IntToDoubleFunction) c[2];
         double[] v = pages(box(ws("A", f), pool).getTableLens("A", RUNTIME), "out");

         for(int r = 1; r <= ROWS; r++) {
            if(v[r] != expected.applyAsDouble(r)) {
               fail(f + ": row " + r + " is " + v[r] + ", expected " + expected.applyAsDouble(r));
            }
         }
      }
   }

   /**
    * The script-state lint as a worksheet runs it (the sandbox's scope and script env): a var
    * accumulator its table owns is not reported in either pool mode, nor is one that holds an
    * array (kept across pooled contexts, B1 residual part 2); one that holds a function is
    * reported once with the pool on only (kept only while the table stays on one context
    * there), and an undeclared global accumulator is still reported in both modes.
    */
   @Test
   void theStateLintWarnsOnlyAboutStateTheTableDoesNotKeep() throws Exception {
      Logger logger = (Logger) LoggerFactory.getLogger(ScriptStateLint.LOGGER_NAME);
      Level level = logger.getLevel();
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.setLevel(Level.WARN);
      logger.addAppender(appender);
      // a distinct text per run: the lint checks a text once per node
      String tag = " /*" + UUID.randomUUID() + "*/";

      try {
         String acc = "var acc=(acc||0)+field['value'];acc" + tag;
         String list = "var a = a || []; a.push(field['id']); a.length" + tag;

         for(boolean pool : new boolean[] { false, true }) {
            assertCounts(pages(box(ws("A", acc), pool).getTableLens("A", RUNTIME), "out"),
                         "pool " + pool);
            assertEquals(0, appender.list.size(), () -> "pool " + pool + ": " + appender.list);
         }

         assertCounts(pages(box(ws("A", list), false).getTableLens("A", RUNTIME), "out"), list);
         assertEquals(0, appender.list.size(), () -> "pool off: " + appender.list);

         for(int k = 0; k < 2; k++) {
            assertCounts(pages(box(ws("A", list), true).getTableLens("A", RUNTIME), "out"),
                         list);
         }

         assertEquals(0, appender.list.size(), () -> "pool on, array: " + appender.list);
         String fn = "var f = f || (function() { var n = 0; " +
            "return function() { return ++n; }; })(); f()" + tag;

         for(int k = 0; k < 2; k++) {
            pages(box(ws("A", fn), true).getTableLens("A", RUNTIME), "out");
         }

         assertEquals(1, appender.list.size(), () -> "pool on: " + appender.list);
         String msg = appender.list.get(0).getFormattedMessage();
         assertTrue(msg.contains("reads variable \"f\"") &&
                    msg.contains("assigns it a function or an object made with new") &&
                    msg.contains("while the table stays on one script context"),
                    msg);

         for(boolean pool : new boolean[] { false, true }) {
            appender.list.clear();
            String global = "runSum = (typeof runSum == 'undefined' ? 0 : runSum) + " +
               "field['value']; runSum /*" + pool + tag.substring(3);
            pages(box(ws("A", global), pool).getTableLens("A", RUNTIME), "out");
            assertEquals(1, appender.list.size(), () -> "pool " + pool + ": " + appender.list);
            assertTrue(appender.list.get(0).getFormattedMessage().contains("rule R2"));
         }
      }
      finally {
         logger.detachAppender(appender);
         logger.setLevel(level);
      }
   }

   // --- helpers ---

   private static String code(String x) {
      return "((" + x + " === undefined ? 1 : 0) + (" + x + " == null ? 2 : 0) + (typeof " + x +
         " == 'undefined' ? 4 : 0) + (typeof " + x + " == 'object' ? 8 : 0))";
   }

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
