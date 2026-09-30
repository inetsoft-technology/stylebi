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
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.ScriptSpan;
import inetsoft.util.script.graal.pool.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * G10 piece Q (Testing #77123) end to end: a real {@link AssetQuerySandbox} query build (a
 * mirror over an embedded table, formula columns with lens-owned vars, JavaScript condition
 * values) runs all its scripts on one lazily claimed pooled context, with the rows of the
 * pool off; a formula table batch inside a build keeps its lens-owned objects exact
 * (residency, amendment A1: no snapshot at a nested batch end); a cancel and a reset during a
 * build.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class, PooledQueryBuildClaimTest.TestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PooledQueryBuildClaimTest {
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      // a data-cached table checks its MV state (the dashboard case)
      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   static final String ACC = "var acc = (acc || 0) + field['id']; acc";
   static final String GROW = "var a = a || []; a.push(field['id']); a.length";
   static final String OBJ = "var o = o || {s: 0}; o.s += field['id']; o.s";
   static final String CACHE10 =
      "var c = c || {}; c['k' + (field['id'] % 10)] = field['id']; var n = (n || 0) + 1; n";
   static final String COUNT = "var n = (n || 0) + 1; n";
   static final String DATE =
      "var d = d || new Date(0); d = new Date(d.getTime() + 1000); d.getTime() / 1000";

   @AfterEach
   void tearDown() {
      SreeEnv.remove(PoolConfig.ENABLED);

      for(AssetQuerySandbox box : boxes) {
         if(box.peekScriptEnv() instanceof WorksheetScriptEnv env) {
            env.retire();
         }
      }

      boxes.clear();
      assertEquals(0, SlotClaim.openClaims(), "a claim was left open");
   }

   /**
    * A build of a mirror over a table with six formula columns (number and Date vars, no
    * object var) and JavaScript condition values on both takes one context; on main each
    * formula batch, condition value and compile took its own.
    */
   @Test
   void aBuildWithManyFormulasAndConditionsTakesOneClaim() throws Exception {
      AssetQuerySandbox box = sandbox(true, mirrorWorksheet(ROWS, false));
      WorksheetScriptEnv env = (WorksheetScriptEnv) box.getScriptEnv();
      long before = env.getMetrics().getCheckouts();
      long cleansBefore = env.getMetrics().getCleans();
      TableLens m = box.getTableLens("M", AssetQuerySandbox.RUNTIME_MODE);
      long build = env.getMetrics().getCheckouts() - before;
      long buildCleans = env.getMetrics().getCleans() - cleansBefore;
      List<List<Object>> rows = drain(m);
      System.out.println("G10Q build claims " + build + ", cleans " + buildCleans +
                         "; after the full read claims " +
                         (env.getMetrics().getCheckouts() - before) + ", cleans " +
                         env.getMetrics().getCleans() + ", execs " + env.getMetrics().getExecs());

      assertEquals(1, build, "claims of one query build");
      assertEquals(1, buildCleans, "cleans of one query build");
      assertEquals(0, env.getMetrics().getBuildYields());
      assertEquals(ROWS, rows.size() - 1);
      assertRowsExact(rows);
   }

   /**
    * With an object var (a resident table): the table's first batch, at the build's top
    * level, makes the build's slot the home, and the build gives it back at that batch's end
    * (one yield, G10 piece Q); each later batch runs on a claim of its own, which the build
    * does not adopt and which is given back as the home at the batch end (Testing #77123,
    * cond-home, round 3). The rows stay exact and match the pool off.
    */
   @Test
   void aBuildWithAnObjectVarTableRunsItsLaterBatchesOnClaimsOfTheirOwn() throws Exception {
      AssetQuerySandbox box = sandbox(true, mirrorWorksheet(ROWS, true));
      WorksheetScriptEnv env = (WorksheetScriptEnv) box.getScriptEnv();
      TableLens m = box.getTableLens("M", AssetQuerySandbox.RUNTIME_MODE);
      long build = env.getMetrics().getCheckouts();
      List<List<Object>> rows = drain(m);
      System.out.println("G10Q object-var build claims " + build + ", claims after the " +
                         "full read " + env.getMetrics().getCheckouts() + ", cleans " +
                         env.getMetrics().getCleans());

      assertEquals(1, env.getMetrics().getBuildYields(), "the build gave its slot back once");
      assertTrue(env.getMetrics().getCheckouts() > build, "no batch took a claim of its own");
      assertEquals(ROWS, rows.size() - 1);
      assertRowsExact(rows);
      assertEquals(drain(sandbox(false, mirrorWorksheet(ROWS, true))
                            .getTableLens("M", AssetQuerySandbox.RUNTIME_MODE)), rows);
   }

   @Test
   void theRowsMatchThePoolOff() throws Exception {
      for(String name : new String[] { "A", "M" }) {
         List<List<Object>> off = drain(sandbox(false, mirrorWorksheet(ROWS, true))
                                           .getTableLens(name, AssetQuerySandbox.RUNTIME_MODE));
         List<List<Object>> on = drain(sandbox(true, mirrorWorksheet(ROWS, true))
                                          .getTableLens(name, AssetQuerySandbox.RUNTIME_MODE));
         assertEquals(off, on, name);
         assertEquals(ROWS + 1, on.size(), name);
      }
   }

   /**
    * Residency (Testing #77123, cond-home): the table's first batch runs at the build's top
    * level, so its slot becomes the home and the build gives it back at the batch end, with
    * no tree (round 3: a tree there cost every build a second context). Every later batch of the resident table takes a claim of its own,
    * the idle home at its end: also in a later build whose first script is not a batch of
    * the table, it takes the home and needs no pull. Every row is exact throughout.
    */
   @Test
   void aTableBatchInsideABuildKeepsItsObjectVarsExact() throws Exception {
      int rows = 5000;
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = embedded(ws, "A", rows);
      expression(a, "out", GROW);
      AssetQuerySandbox box = sandbox(true, ws);
      WorksheetScriptEnv env = (WorksheetScriptEnv) box.getScriptEnv();
      TableLens t = box.getTableLens("A", AssetQuerySandbox.RUNTIME_MODE);
      assertEquals(0, PoolTestSupport.metric(env, "HandOffs"), "no tree at the build's top");
      assertEquals(1, env.getMetrics().getBuildYields(), "the home given back");
      int cid = col(t, "id");
      int cout = col(t, "out");
      readPages(t, cid, cout, 1, 1000);
      long pulls = PoolTestSupport.metric(env, "Pulls");

      try(SlotClaim.Build ignored = SlotClaim.openBuild()) {
         // the build's first script is no batch of the table: its context is not the home
         assertEquals(2.0, env.exec(env.compile("1 + 1"), null, null, null));
         readPages(t, cid, cout, 1001, 2000);
         assertEquals(pulls, PoolTestSupport.metric(env, "Pulls"), "pulls in the build");
      }

      readPages(t, cid, cout, 2001, rows);
   }

   /**
    * Round 2 (review finding 1, A1): a thread reads rows of a table whose array var lives on
    * a context while it is inside a query build, then another thread reads later rows of the
    * same lens. The batch at the build's top level ends the build's hold on its context, so
    * the home is idle and the other thread pulls the array: every row exact, as with the pool
    * off, and no second context (round 1: the build held the home, the other thread read the
    * array as undefined, row 5001 = 3938). Also with the read in an outer span of the build
    * (a condition filter, another table's batch). The lens's first batch runs at the build's
    * top level, so the build gives its slot back as the home there (one yield); the other
    * thread's batch runs on a claim of its own and takes the idle home (Testing #77123,
    * cond-home, round 3).
    */
   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void anotherThreadReadsATableWhileABuildHoldsItsFirstBatch(boolean nested) throws Exception {
      Map<Boolean, List<Double>> results = new HashMap<>();
      SreeEnv.setProperty(PoolConfig.BATCH_ROWS, "100");
      SreeEnv.setProperty(PoolConfig.MAX_BATCH_ROWS, "100");
      ExecutorService other = Executors.newSingleThreadExecutor();

      try {
         for(boolean pool : new boolean[] { false, true }) {
            Worksheet ws = new Worksheet();
            EmbeddedTableAssembly a = embedded(ws, "A", 20000);
            expression(a, "out", GROW);
            AssetQuerySandbox box = sandbox(pool, ws);
            List<Double> values = new ArrayList<>();

            try(SlotClaim.Build ignored = pool ? SlotClaim.openBuild() : null) {
               TableLens t = box.getTableLens("A", AssetQuerySandbox.RUNTIME_MODE);
               int cid = col(t, "id");
               int cout = col(t, "out");
               try(ScriptSpan outer = nested ? box.getScriptEnv().openSpan() : ScriptSpan.NONE) {
                  values.addAll(readValues(t, cout, 1, 100));
               }

               values.addAll(other.submit(() -> readValues(t, cout, 5001, 5100))
                                .get(120, TimeUnit.SECONDS));

               for(int i = 0; i < 100; i++) {
                  assertEquals(i + 1.0, values.get(i), "row " + (i + 1));
                  assertEquals(i + 5001.0, values.get(100 + i), "row " + (i + 5001));
               }

               assertEquals(5100.0, num(t.getObject(5100, cid)));

               if(box.getScriptEnv() instanceof WorksheetScriptEnv env) {
                  assertEquals(1, env.getMetrics().getBuildYields(), "yields");
               }
            }

            results.put(pool, values);
         }
      }
      finally {
         other.shutdownNow();
         SreeEnv.remove(PoolConfig.BATCH_ROWS);
         SreeEnv.remove(PoolConfig.MAX_BATCH_ROWS);
      }

      assertEquals(results.get(false), results.get(true), "pool on vs off");
   }

   /**
    * Round 2 (tester's finding, single user): an aggregate or distinct over a table with
    * object vars reads the formula table on an on-demand worker thread while the query build
    * runs on its own. Repeated builds in one sandbox give the pool-off rows every time, with
    * no lost-var warning (round 1: 19 of 20 builds wrong, the build held the home).
    */
   @ParameterizedTest
   @ValueSource(strings = { "agg", "distinct", "mirrorAgg" })
   void onDemandWorkersOfABuildReadItsObjectVarTableExactly(String shape) throws Exception {
      String name = shape.equals("mirrorAgg") ? "M" : "A";
      List<List<Object>> off = drain(sandbox(false, workerWorksheet(shape, 3000))
                                        .getTableLens(name, AssetQuerySandbox.RUNTIME_MODE));
      AssetQuerySandbox box = sandbox(true, workerWorksheet(shape, 3000));

      for(int b = 0; b < 5; b++) {
         if(b > 0) {
            box.resetTableLens(name);
         }

         assertEquals(off, drain(box.getTableLens(name, AssetQuerySandbox.RUNTIME_MODE)),
                      shape + " build " + b);
      }
   }

   /**
    * Round 2 (tester's dashboard case): four aggregate assemblies over one data-cached table
    * with object vars, built and read at once on four threads, all get the same cached lens
    * of the table. Every table matches the pool off in every round (round 1: 16 of 20 wrong,
    * a build held the home the other threads' workers needed).
    */
   @Test
   void assembliesSharingACachedObjectVarTableMatchThePoolOff() throws Exception {
      int k = 4;
      List<List<List<Object>>> truth = new ArrayList<>();
      AssetDataCache.getCache().clearCache();
      AssetQuerySandbox off = sandbox(false, dashboardWorksheet(k, 3000));

      for(int i = 0; i < k; i++) {
         truth.add(drain(off.getTableLens("B" + i, AssetQuerySandbox.RUNTIME_MODE)));
      }

      AssetQuerySandbox box = sandbox(true, dashboardWorksheet(k, 3000));
      ExecutorService threads = Executors.newFixedThreadPool(k);

      try {
         for(int round = 0; round < 4; round++) {
            AssetDataCache.getCache().clearCache();
            box.resetTableLens();
            CyclicBarrier barrier = new CyclicBarrier(k);
            List<Future<List<List<Object>>>> tables = new ArrayList<>();

            for(int i = 0; i < k; i++) {
               String name = "B" + i;
               tables.add(threads.submit(() -> {
                  barrier.await(60, TimeUnit.SECONDS);
                  return drain(box.getTableLens(name, AssetQuerySandbox.RUNTIME_MODE));
               }));
            }

            for(int i = 0; i < k; i++) {
               assertEquals(truth.get(i), tables.get(i).get(120, TimeUnit.SECONDS),
                            "B" + i + " round " + round);
            }
         }
      }
      finally {
         threads.shutdownNow();
         AssetDataCache.getCache().clearCache();
      }
   }

   // A with object and array vars, in the data cache, and k aggregate mirrors B0..Bk-1 of it
   private static Worksheet dashboardWorksheet(int k, int rows) {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = embedded(ws, "A", rows);
      a.setProperty("no_cache", null);
      expression(a, "obj", OBJ);
      expression(a, "arr", GROW);

      for(int i = 0; i < k; i++) {
         MirrorTableAssembly b = new MirrorTableAssembly(ws, "B" + i, a);
         b.setProperty("no_cache", "true");
         ws.addAssembly(b);
         b.update();
         ColumnSelection cols = b.getColumnSelection(false);
         AggregateInfo agg = new AggregateInfo();
         agg.addGroup(new GroupRef(cols.getAttribute("grp")));
         agg.addAggregate(new AggregateRef(cols.getAttribute("obj"), AggregateFormula.SUM));
         agg.addAggregate(new AggregateRef(cols.getAttribute("arr"), AggregateFormula.MAX));
         b.setAggregateInfo(agg);
      }

      return ws;
   }

   private static Worksheet workerWorksheet(String shape, int rows) {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = embedded(ws, "A", rows);
      expression(a, "obj", OBJ);
      expression(a, "arr", GROW);
      TableAssembly top = a;

      if(shape.equals("mirrorAgg")) {
         MirrorTableAssembly m = new MirrorTableAssembly(ws, "M", a);
         m.setProperty("no_cache", "true");
         ws.addAssembly(m);
         m.update();
         top = m;
      }

      if(shape.equals("distinct")) {
         a.setDistinct(true);
      }
      else {
         ColumnSelection cols = top.getColumnSelection(false);
         AggregateInfo agg = new AggregateInfo();
         agg.addGroup(new GroupRef(cols.getAttribute("grp")));
         agg.addAggregate(new AggregateRef(cols.getAttribute("obj"), AggregateFormula.SUM));
         agg.addAggregate(new AggregateRef(cols.getAttribute("arr"), AggregateFormula.MAX));
         top.setAggregateInfo(agg);
      }

      return ws;
   }

   private static List<Double> readValues(TableLens t, int col, int from, int to) {
      List<Double> values = new ArrayList<>();
      t.moreRows(to);

      for(int r = from; r <= to; r++) {
         values.add(num(t.getObject(r, col)));
      }

      return values;
   }

   /**
    * A cancel during a build stops its formula table; the build's claim is released when
    * the build ends, and its context is clean and reused.
    */
   @Test
   void aCancelDuringABuildReleasesItsClaim() throws Exception {
      AssetQuerySandbox box = sandbox(true, new Worksheet());
      WorksheetScriptEnv env = (WorksheetScriptEnv) box.getScriptEnv();
      Canceller cb = new Canceller();
      env.put("cb", cb);
      FormulaTableLens lens;

      try(SlotClaim.Build ignored = SlotClaim.openBuild()) {
         lens = formulaLens(box, "leak = 1; cb.hit(field['id']); field['id'] * 2");
         cb.lens = lens;
         lens.moreRows(TableLens.EOT);
         assertTrue(lens.isCancelled());
         assertTrue(cb.hits < ROWS, "the cancel did not stop the table: " + cb.hits);
         assertEquals(3.0, env.exec(env.compile("1 + 2"), null, null, null));
      }

      assertEquals(0, SlotClaim.openClaims());
      assertEquals(1, env.getMetrics().getCleans());
      cb.lens = null;
      FormulaTableLens next;

      try(SlotClaim.Build ignored = SlotClaim.openBuild()) {
         next = formulaLens(box, "typeof leak + ':' + field['id'] * 2");
         next.moreRows(TableLens.EOT);
      }

      for(int r = 1; r <= ROWS; r++) {
         assertEquals("undefined:" + (r * 2), String.valueOf(next.getObject(r, 1)), "row " + r);
      }

      assertEquals(1, env.getMetrics().getCreations(), "the context was not reused");
   }

   /**
    * A reset of the env by a formula during a build: the build ends on its context (amendment
    * 4), its rows are exact, and the next build of the table runs on a fresh context.
    */
   @Test
   void aResetDuringAQueryBuildKeepsItsRows() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = embedded(ws, "A", ROWS);
      expression(a, "sum", ACC + "; if(field['id'] == 10) cb.reset(); acc");
      AssetQuerySandbox box = sandbox(true, ws);
      WorksheetScriptEnv env = (WorksheetScriptEnv) box.getScriptEnv();
      env.put("cb", new PoolTestSupport.Callback(env));

      for(int round = 0; round < 2; round++) {
         if(round > 0) {
            box.resetTableLens("A");
         }

         TableLens t = box.getTableLens("A", AssetQuerySandbox.RUNTIME_MODE);
         t.moreRows(TableLens.EOT);
         int cid = col(t, "id");
         int cacc = col(t, "sum");

         for(int r = 1; r <= ROWS; r++) {
            int id = (int) num(t.getObject(r, cid));
            assertEquals((double) id * (id + 1) / 2, num(t.getObject(r, cacc)), "row " + r);
         }
      }

      assertEquals(0, env.getMetrics().getSwaps());
      assertTrue(env.getMetrics().getCreations() >= 2, "the retired context was reused");
   }


   public static final class Canceller {
      public void hit(Object id) {
         hits++;

         if(lens != null && id instanceof Number n && n.intValue() == 50) {
            lens.cancel();
         }
      }

      volatile FormulaTableLens lens;
      volatile int hits;
   }

   private FormulaTableLens formulaLens(AssetQuerySandbox box, String formula) {
      Object[][] data = new Object[ROWS + 1][];
      data[0] = new Object[] { "id" };

      for(int i = 1; i <= ROWS; i++) {
         data[i] = new Object[] { i };
      }

      ScriptEnv env = box.getScriptEnv();
      return new FormulaTableLens(new DefaultTableLens(data), new String[] { "f" },
                                  new String[] { formula }, env, box.getScope());
   }

   private static void readPages(TableLens t, int cid, int cout, int from, int to) {
      for(int s = from; s <= to; s += 100) {
         int e = Math.min(to, s + 99);
         t.moreRows(e);

         for(int r = s; r <= e; r++) {
            assertEquals(num(t.getObject(r, cid)), num(t.getObject(r, cout)), "row " + r);
         }
      }
   }

   private static void assertRowsExact(List<List<Object>> rows) {
      List<Object> header = rows.get(0);
      int id = header.indexOf("id");
      int acc = header.indexOf("sum");
      int n = header.indexOf("cnt");
      int dt = header.indexOf("dt");
      int acc2 = header.indexOf("acc2");

      for(int r = 1; r < rows.size(); r++) {
         List<Object> row = rows.get(r);
         double i = num(row.get(id));
         assertEquals(i * (i + 1) / 2, num(row.get(acc)), "acc row " + r);
         assertEquals(i, num(row.get(n)), "n row " + r);
         assertEquals(i, num(row.get(dt)), "dt row " + r);
         assertEquals(i * (i + 1), num(row.get(acc2)), "acc2 row " + r);
      }
   }

   // A with six formula columns (lens-owned number, Date and, if objects, object vars, each
   // named unlike its column: a column name in the formula reads the column) and JavaScript
   // condition values, and a mirror M of A with a formula column and a JavaScript condition
   private static Worksheet mirrorWorksheet(int rows, boolean objects) {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = embedded(ws, "A", rows);
      expression(a, "sum", ACC);
      expression(a, "x2", "Math.sqrt(field['value']) + field['grp'] * 2");
      expression(a, "s", "'r' + field['id'] + '-' + field['grp']");
      expression(a, "cnt", objects ? CACHE10 : COUNT);
      expression(a, "dt", DATE);
      expression(a, "g", "field['grp'] > 2 ? 'hi' : 'lo'");
      jsCondition(a, "id", "Math.min(0, 1)", "Math.max(-5, -10)");
      MirrorTableAssembly m = new MirrorTableAssembly(ws, "M", a);
      m.setProperty("no_cache", "true");
      ws.addAssembly(m);
      m.update();
      expression(m, "acc2", "field['sum'] * 2");
      jsCondition(m, "id", "Math.min(0, 2)");
      return ws;
   }

   private static EmbeddedTableAssembly embedded(Worksheet ws, String name, int rows) {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "value", "id", "grp" };

      for(int i = 1; i <= rows; i++) {
         data[i] = new Object[] { i * 7, i, i % 5 };
      }

      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.INTEGER, XSchema.INTEGER, XSchema.INTEGER }, data));
      table.setProperty("no_cache", "true");
      ws.addAssembly(table);
      return table;
   }

   private static void expression(TableAssembly table, String name, String formula) {
      ColumnSelection columns = table.getColumnSelection(false);
      ExpressionRef exp = new ExpressionRef(null, name);
      exp.setExpression(formula);
      ColumnRef column = new ColumnRef(exp);
      column.setDataType(formula.contains("'r'") || formula.contains("'hi'") ?
                            XSchema.STRING : XSchema.DOUBLE);
      columns.addAttribute(column);
      table.setColumnSelection(columns, false);
   }

   // JavaScript condition values that keep every row (ids are positive)
   private static void jsCondition(TableAssembly table, String column, String... values) {
      ConditionList list = new ConditionList();

      for(String js : values) {
         AssetCondition cond = new AssetCondition();
         cond.setOperation(XCondition.GREATER_THAN);
         cond.setType(XSchema.INTEGER);
         ExpressionValue value = new ExpressionValue();
         value.setExpression(js);
         value.setType(ExpressionValue.JAVASCRIPT);
         cond.addValue(value);

         if(list.getSize() > 0) {
            list.append(new JunctionOperator(JunctionOperator.AND, 0));
         }

         list.append(new ConditionItem(table.getColumnSelection(false).getAttribute(column),
                                       cond, 0));
      }

      table.setPostConditionList(list);
   }

   private AssetQuerySandbox sandbox(boolean pool, Worksheet ws) {
      SreeEnv.setProperty(PoolConfig.ENABLED, Boolean.toString(pool));
      AssetDataCache.getCache().clearCache();
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      boxes.add(box);
      assertEquals(pool, box.isScriptPoolMode());
      return box;
   }

   private static List<List<Object>> drain(TableLens t) {
      List<List<Object>> rows = new ArrayList<>();

      for(int r = 0; t.moreRows(r); r++) {
         List<Object> row = new ArrayList<>();

         for(int c = 0; c < t.getColCount(); c++) {
            Object o = t.getObject(r, c);
            row.add(o instanceof Number n ? (Object) n.doubleValue() : o);
         }

         rows.add(row);
      }

      return rows;
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

   private static double num(Object o) {
      return o instanceof Number ? ((Number) o).doubleValue() : Double.NaN;
   }

   private static final int ROWS = 300;
   private final List<AssetQuerySandbox> boxes = new ArrayList<>();
}
