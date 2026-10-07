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
package inetsoft.report.composition.execution.lockcycle;

import inetsoft.report.TableFilter;
import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.*;
import inetsoft.report.filter.ConditionFilter;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.script.graal.ScriptScope;
import inetsoft.util.script.graal.pool.PoolConfig;
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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77158: the #76965 cycle on a worksheet the product builds itself, with the context
 * pool off (this suite's default; the product default is on since #77123). Table A has a
 * pre-condition "id one of (sub-query on B.bx)", where
 * {@code bx} is a JavaScript expression column of B, so the sub table is
 * {@code DistinctTableLens(FormulaTableLens)} on the worksheet sandbox's engine; A's own base
 * has no script, so A's condition filter populates under its monitor without the engine lock.
 * Table X has an expression column {@code A.length}, which reads A by name inside
 * {@code exec}, holding the engine lock.
 *
 * <p>Cycle: the populator of A holds the filter's monitor and waits for the sub table's
 * distinct worker; the worker waits for the engine lock in the formula lens; the thread
 * computing X holds the engine lock and waits for the filter's monitor.
 *
 * <p>The sub table is larger than the rows {@code AssetQuery.validateDataTypes} computes
 * while A is built. If building A leaves a distinct worker reading it, the case parks that
 * worker before it takes the engine lock until the other threads are in place. Since bug
 * #77223 a sub table whose base needs the engine lock starts no worker while it is built, and
 * the first reader computes it, so the cases assert only that every reader finishes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class, SubQueryConditionWorksheetCycleTest.TestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
@Timeout(value = 120, unit = TimeUnit.SECONDS)
public class SubQueryConditionWorksheetCycleTest {
   /**
    * A real data cache, so the worksheet's tables are cached as in the product.
    */
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }
   }

   @BeforeEach
   public void setUp() {
      harness = new LockCycleHarness();

      // the sandboxes of this class are real, so -Dlockcycle.pool reaches them through the
      // property their env is chosen by; set it either way, so -Dlockcycle.pool=false runs
      // them pool off although the pool is on by default (Feature #77123)
      SreeEnv.setProperty(PoolConfig.ENABLED, Boolean.toString(POOL));
   }

   @AfterEach
   public void tearDown() throws Exception {
      if(worker != null) {
         worker.release();
      }

      harness.close();

      // disposing takes the engine lock, which a deadlocked case leaves held forever; a
      // pooled env has none
      if(box != null && lock == null) {
         box.dispose();
      }
      else if(box != null && lock.tryLock()) {
         try {
            box.dispose();
         }
         finally {
            lock.unlock();
         }
      }

      SreeEnv.remove(PoolConfig.ENABLED);
   }

   /**
    * @param correlated the sub-query is correlated ({@code B.grp = A.grp}).
    */
   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   public void formulaReadsFilteredTableByName(boolean correlated) throws Exception {
      worker = new WorkerGate(PARK_ROW);
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = embedded(ws, "A", 20, null);
      EmbeddedTableAssembly b = embedded(ws, "B", SUB_ROWS, worker);
      expression(b, "bx", "field['id'] * 1");
      EmbeddedTableAssembly x = embedded(ws, "X", 1, null);
      expression(x, "alen", "A.length");
      subQueryCondition(ws, a, b, correlated);
      box = sandbox(ws);
      lock = box.getScriptEnv().getExecutionLock();

      // A is built and populated on a thread holding no lock, as a viewsheet or data
      // request would; building A may start the sub table's distinct worker
      Started<Integer> populator = harness.start(() -> {
         TableLens table = box.getTableLens("A", AssetQuerySandbox.RUNTIME_MODE);
         return drain(table).size();
      });
      awaitIn(populator, KNOWN_CAP, "DistinctTableLens.moreRows");

      // X's formula reads A by name, holding the engine lock
      Started<Integer> script = harness.start(
         () -> drain(box.getTableLens("X", AssetQuerySandbox.RUNTIME_MODE)).size());
      // without the fix it blocks on A's filter; with it, it waits for the engine lock in X's
      // formula lens before it reaches A
      awaitIn(script, KNOWN_CAP, "AbstractConditionFilter.moreRows", "LendableReentrantLock.lock");
      worker.release();

      assertEquals(2, harness.await(script.future, KNOWN_CAP, "formula reading A by name"));
      assertTrue(harness.await(populator.future, KNOWN_CAP, "populator of A") > 1);
   }

   /**
    * The condition filter of a mirror M of A (M has a plain pre-condition of its own) has
    * A's condition filter in its base chain. A fix that makes only A's filter take the engine
    * lock first (because of its sub-query) leaves M's filter taking its monitor first, and M's
    * population then waits for the engine lock inside M's monitor, when it reaches A's filter.
    * X's formula holds the engine lock while it reads another table G, then reads M by name.
    */
   @Test
   public void formulaReadsMirrorOfFilteredTableByName() throws Exception {
      Gate gate = harness.gate();
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = embedded(ws, "A", 20, null);
      EmbeddedTableAssembly b = embedded(ws, "B", 10, null);
      expression(b, "bx", "field['id'] * 1");
      subQueryCondition(ws, a, b, false);
      MirrorTableAssembly m = new MirrorTableAssembly(ws, "M", a);
      ws.addAssembly(m);
      m.update();
      AssetCondition positive = new AssetCondition();
      positive.setOperation(XCondition.GREATER_THAN);
      positive.setType(XSchema.INTEGER);
      positive.addValue(0);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(m.getColumnSelection(false).getAttribute("id"), positive, 0));
      m.setPreConditionList(list);
      embedded(ws, "G", 1, (Hook) r -> gate.onRead());
      EmbeddedTableAssembly x = embedded(ws, "X", 1, null);
      expression(x, "mlen", "G.length + M.length");
      box = sandbox(ws);
      lock = box.getScriptEnv().getExecutionLock();
      TableLens mirror = harness.await(harness.submit(
         () -> box.getTableLens("M", AssetQuerySandbox.RUNTIME_MODE)), KNOWN_CAP, "building M");

      // X's formula holds the engine lock and is parked in G
      Started<Integer> script = harness.startGated(gate,
         () -> drain(box.getTableLens("X", AssetQuerySandbox.RUNTIME_MODE)).size());
      assertTrue(gate.awaitEntered(KNOWN_CAP), "X's formula did not reach G");

      // M is populated on a thread holding no lock; it may have to wait for the engine lock
      Started<Integer> populator = harness.start(() -> drain(mirror).size());
      awaitIn(populator, KNOWN_CAP, "LendableReentrantLock.lock");
      gate.release();

      assertEquals(2, harness.await(script.future, KNOWN_CAP, "formula reading M by name"));
      assertEquals(11, harness.await(populator.future, KNOWN_CAP, "populator of M"));
   }

   /**
    * A correlated sub-query over a sub table that is the formula lens itself, which is what a
    * SQL-bound sub table becomes when its DISTINCT is pushed into the SQL (an embedded sub
    * table cannot be merged, so the case unwraps the product's {@code DistinctTableLens}
    * after building). Each row whose main value changes reads the sub table again, and the
    * end-of-table probe takes the engine lock even though the formula lens is complete. No
    * script reads A by name: the engine lock holder is the formula lens of a mirror M of A
    * with an expression column, which reads A's rows while it computes.
    */
   @Test
   public void mirrorFormulaOverCorrelatedFilter() throws Exception {
      Gate gate = harness.gate();
      Worksheet ws = new Worksheet();
      // more rows than building M computes, so A is still being populated afterwards
      EmbeddedTableAssembly a = embedded(ws, "A", SUB_ROWS, new Hook() {
         @Override
         public void onMoreRows(int row) {
         }

         @Override
         public void onGetObject(int row) {
            // M's formula is reading A while it holds the engine lock
            if(JavaScriptEngine.holdsScriptLock()) {
               gate.onRead();
            }
         }
      });
      EmbeddedTableAssembly b = embedded(ws, "B", 10, null);
      expression(b, "bx", "field['grp'] * 1");
      // grp one of (B.bx where B.grp = A.grp): keeps every row
      subQueryCondition(ws, a, b, true, "grp");
      MirrorTableAssembly m = new MirrorTableAssembly(ws, "M", a);
      ws.addAssembly(m);
      m.update();
      expression(m, "mx", "field['id'] * 2");
      box = sandbox(ws);
      lock = box.getScriptEnv().getExecutionLock();
      TableLens mirror = harness.await(harness.submit(
         () -> box.getTableLens("M", AssetQuerySandbox.RUNTIME_MODE)), KNOWN_CAP, "building M");
      TableLens filter = unwrapDistinctSubTable(mirror);

      // M's formula holds the engine lock and is parked reading A
      Started<Integer> formula = harness.startGated(gate, () -> drain(mirror).size());
      assertTrue(gate.awaitEntered(KNOWN_CAP), "M's formula did not read A");

      // another reader of A populates it further and re-reads the sub table
      Started<Integer> populator = harness.start(() -> drain(filter).size());
      awaitIn(populator, KNOWN_CAP, "LendableReentrantLock.lock");
      gate.release();

      int rows = harness.await(formula.future, KNOWN_CAP, "M's formula reading A");
      assertEquals(rows, (int) harness.await(populator.future, KNOWN_CAP, "reader of A"));
   }

   /**
    * Review round 1 of the fix, B1: the populator of A is a script thread of another engine,
    * e.g. a viewsheet onLoad script reading A (a viewsheet scope has its own env). Once A's
    * filter takes the worksheet engine lock first, that thread holds it while it waits for the
    * sub table's distinct worker, and a thread inside {@code exec} cannot lend it
    * ({@code canLendScriptLocks}), so the worker, which needs the lock in the formula lens,
    * never finishes. Before the fix the thread held no worksheet engine lock and the worker
    * finished.
    */
   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   public void foreignEngineScriptThreadPopulatesFilteredTable(boolean correlated)
      throws Exception
   {
      worker = new WorkerGate(PARK_ROW);
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = embedded(ws, "A", 20, null);
      EmbeddedTableAssembly b = embedded(ws, "B", SUB_ROWS, worker);
      expression(b, "bx", "field['id'] * 1");
      subQueryCondition(ws, a, b, correlated);
      box = sandbox(ws);
      lock = box.getScriptEnv().getExecutionLock();
      TableLens table = harness.await(harness.submit(
         () -> box.getTableLens("A", AssetQuerySandbox.RUNTIME_MODE)), KNOWN_CAP, "building A");

      Started<Integer> script = harness.start(() -> asForeignScript(() -> drain(table).size()));
      awaitIn(script, KNOWN_CAP, "DistinctTableLens.moreRows");
      worker.release();

      assertTrue(harness.await(script.future, KNOWN_CAP, "script of another engine reading A") > 1);
   }

   /**
    * {@link #formulaReadsFilteredTableByName} with the readers in the other order: A is built
    * on a thread holding no lock, which starts the sub table's distinct worker, and then X's
    * formula {@code A.length} is the first to populate A. That thread is inside {@code exec}
    * on the worksheet engine, so A's filter re-enters the engine lock and waits for the
    * worker holding it; lending it is correctly refused (the context is in use on this
    * thread), and the worker needs it in the formula lens.
    *
    * <p>Not fixed by #77158. Bug #77223: building the sub table no longer starts a worker
    * that needs the engine lock; X's formula computes it.
    */
   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   public void formulaReadsFilteredTableFirst(boolean correlated) throws Exception {
      worker = new WorkerGate(PARK_ROW);
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = embedded(ws, "A", 20, null);
      EmbeddedTableAssembly b = embedded(ws, "B", SUB_ROWS, worker);
      expression(b, "bx", "field['id'] * 1");
      EmbeddedTableAssembly x = embedded(ws, "X", 1, null);
      expression(x, "alen", "A.length");
      subQueryCondition(ws, a, b, correlated);
      box = sandbox(ws);
      lock = box.getScriptEnv().getExecutionLock();
      harness.await(harness.submit(
         () -> box.getTableLens("A", AssetQuerySandbox.RUNTIME_MODE)), KNOWN_CAP, "building A");

      Started<List<List<Object>>> script = harness.start(
         () -> drain(box.getTableLens("X", AssetQuerySandbox.RUNTIME_MODE)));
      awaitIn(script, KNOWN_CAP, "DistinctTableLens.moreRows");
      worker.release();

      // every row of A has its id among B's, and its grp among them when correlated
      assertFormulaRead(21, harness.await(script.future, KNOWN_CAP, "X's formula reading A first"));
   }

   /**
    * The same shape without a sub-query: the condition filter of a mirror M of a distinct
    * table D with a script expression column has D's {@code DistinctTableLens} in its base
    * chain, so it takes the worksheet engine lock first on main too, and a script thread of
    * another engine reading M waits for D's worker holding that lock. Deadlocked on main
    * before #77158, which lends that lock to the worker from a script thread of another engine.
    */
   @Test
   public void foreignEngineScriptThreadPopulatesFilterOverDistinct() throws Exception {
      worker = new WorkerGate(PARK_ROW);
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly d = embedded(ws, "D", SUB_ROWS, worker);
      expression(d, "dx", "field['id'] * 1");
      d.setDistinct(true);
      MirrorTableAssembly m = new MirrorTableAssembly(ws, "M", d);
      ws.addAssembly(m);
      m.update();
      AssetCondition positive = new AssetCondition();
      positive.setOperation(XCondition.GREATER_THAN);
      positive.setType(XSchema.INTEGER);
      positive.addValue(0);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(m.getColumnSelection(false).getAttribute("id"), positive, 0));
      m.setPreConditionList(list);
      box = sandbox(ws);
      lock = box.getScriptEnv().getExecutionLock();
      TableLens mirror = harness.await(harness.submit(
         () -> box.getTableLens("M", AssetQuerySandbox.RUNTIME_MODE)), KNOWN_CAP, "building M");

      Started<Integer> script = harness.start(() -> asForeignScript(() -> drain(mirror).size()));
      awaitIn(script, KNOWN_CAP, "DistinctTableLens.moreRows");
      worker.release();

      assertEquals(SUB_ROWS + 1,
                   (int) harness.await(script.future, KNOWN_CAP, "script of another engine reading M"));
   }

   /**
    * Bug #77223: {@link #formulaReadsFilteredTableFirst} without a sub-query. A distinct table
    * D with a script expression column is built on a thread holding no lock, and X's formula
    * {@code D.length} is then the first to read it, inside {@code exec} on the same engine.
    */
   @Test
   public void distinctTableReadFirstByFormula() throws Exception {
      worker = new WorkerGate(PARK_ROW);
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly d = embedded(ws, "D", SUB_ROWS, worker);
      expression(d, "dx", "field['id'] * 1");
      d.setDistinct(true);
      EmbeddedTableAssembly x = embedded(ws, "X", 1, null);
      expression(x, "dlen", "D.length");

      // D.length counts the header row
      readFirstByFormula(ws, "D", SUB_ROWS + 1, "DistinctTableLens.moreRows");
   }

   /**
    * Bug #77223: the same with a grouped table G, whose summary worker reads the formula lens
    * through a sort; X's formula {@code G.length} waits for the summary's rows.
    */
   @Test
   public void groupedTableReadFirstByFormula() throws Exception {
      // 50 groups and the header row
      groupedTableReadFirst("G.length", 51);
   }

   /**
    * Bug #77223: {@link #groupedTableReadFirstByFormula} with a formula reading a cell of G
    * before anything asks G for more rows. The element read asks G's size first, so it waits
    * in the same place.
    */
   @Test
   public void groupedTableCellReadFirstByFormula() throws Exception {
      // G's columns are count(id), gx; the count of group gx = 0
      groupedTableReadFirst("G[1][0]", SUB_ROWS / 50);
   }

   /**
    * Bug #77223: a distinct mirror M of a table S sorted on its script expression column, so
    * the distinct worker reads the formula lens through S's sort filter.
    */
   @Test
   public void distinctMirrorOfSortedTableReadFirstByFormula() throws Exception {
      worker = new WorkerGate(PARK_ROW);
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly s = embedded(ws, "S", SUB_ROWS, worker);
      expression(s, "sx", "field['id'] * 1");
      SortInfo sort = new SortInfo();
      sort.addSort(new SortRef(s.getColumnSelection(false).getAttribute("sx")));
      s.setSortInfo(sort);
      MirrorTableAssembly m = new MirrorTableAssembly(ws, "M", s);
      ws.addAssembly(m);
      m.update();
      m.setDistinct(true);
      EmbeddedTableAssembly x = embedded(ws, "X", 1, null);
      expression(x, "mlen", "M.length");

      readFirstByFormula(ws, "M", SUB_ROWS + 1, "DistinctTableLens.moreRows",
                         "SortFilter.checkInit", "SortFilter.moreRows");
   }

   /**
    * Bug #77223: a union U of a table P with a script expression column is not affected, since
    * the set lens reads every base row on the thread building it.
    */
   @Test
   public void unionTableReadFirstByFormula() throws Exception {
      worker = new WorkerGate(PARK_ROW);
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly p = embedded(ws, "P", SUB_ROWS, worker);
      expression(p, "px", "field['id'] * 1");
      EmbeddedTableAssembly q = embedded(ws, "Q", 10, null);
      expression(q, "qx", "field['id'] * 1");
      TableAssemblyOperator union = new TableAssemblyOperator();
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setOperation(TableAssemblyOperator.UNION);
      op.setLeftTable("P");
      op.setRightTable("Q");
      union.addOperator(op);
      ws.addAssembly(new ConcatenatedTableAssembly(
         ws, "U", new TableAssembly[] { p, q }, new TableAssemblyOperator[] { union }));
      EmbeddedTableAssembly x = embedded(ws, "X", 1, null);
      expression(x, "ulen", "U.length");

      // Q's rows are rows of P
      readFirstByFormula(ws, "U", SUB_ROWS + 1, "SetTableLens.moreRows");
      assertFalse(worker.hasParked(), "a lens worker read P's rows");
   }

   /**
    * Bug #77223 review round 1, F1 (O6's intended shape): an embedded table A, sorted and
    * distinct, small enough that building it computes every formula row, is built on a thread
    * holding no lock while X's formula reads {@code A.length}. The builder holds A's monitor
    * ({@code AssetQuerySandbox.getTableLens}) for the rest of the build, and X holds the
    * engine lock while it waits for that monitor, so nothing the build does after computing
    * the formula rows may wait for the engine lock. The builder parks inside the monitor after
    * the formula rows are computed, until X waits for the monitor.
    */
   @Test
   public void sortedDistinctEmbeddedTableBuiltWhileFormulaReadsIt() throws Exception {
      BuilderGate builder = new BuilderGate(20);
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = embedded(ws, "A", 20, builder);
      expression(a, "ax", "field['id'] * 1");
      SortInfo sort = new SortInfo();
      sort.addSort(new SortRef(a.getColumnSelection(false).getAttribute("ax")));
      a.setSortInfo(sort);
      a.setDistinct(true);
      EmbeddedTableAssembly x = embedded(ws, "X", 1, null);
      expression(x, "alen", "A.length");
      box = sandbox(ws);
      lock = box.getScriptEnv().getExecutionLock();

      Started<Integer> building = harness.start(() -> {
         builder.own();
         return drain(box.getTableLens("A", AssetQuerySandbox.RUNTIME_MODE)).size();
      });
      assertTrue(builder.awaitEntered(KNOWN_CAP), "the builder of A never read A's last row");
      Started<List<List<Object>>> script = harness.start(
         () -> drain(box.getTableLens("X", AssetQuerySandbox.RUNTIME_MODE)));
      releaseAfter(builder, script);

      assertEquals(21, (int) harness.await(building.future, KNOWN_CAP, "the builder of A"));
      assertFormulaRead(21, harness.await(script.future, KNOWN_CAP, "X's formula reading A"));
   }

   /**
    * Let {@code builder}'s owner go on once {@code next} is parked (blocked or waiting) or
    * finished.
    */
   private static void releaseAfter(BuilderGate builder, Started<?> next)
      throws InterruptedException
   {
      LockCycleHarness.awaitParked(next, KNOWN_CAP);
      builder.release();
   }

   private void groupedTableReadFirst(String formula, int expected) throws Exception {
      worker = new WorkerGate(PARK_ROW);
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly g = embedded(ws, "G", SUB_ROWS, worker);
      expression(g, "gx", "field['id'] % 50");
      ColumnSelection columns = g.getColumnSelection(false);
      AggregateInfo aggregate = new AggregateInfo();
      aggregate.addGroup(new GroupRef(columns.getAttribute("gx")));
      aggregate.addAggregate(new AggregateRef(columns.getAttribute("id"), AggregateFormula.COUNT_ALL));
      g.setAggregateInfo(aggregate);
      EmbeddedTableAssembly x = embedded(ws, "X", 1, null);
      expression(x, "gval", formula);

      readFirstByFormula(ws, "G", expected, "SummaryFilter.waitForRow",
                         "SummaryFilter.moreRows", "SummaryFilter.getObject");
   }

   /**
    * Build {@code table} on a thread holding no lock, as a viewsheet or data request would,
    * then compute X, whose formula reads {@code table} by name inside {@code exec}. If building
    * the table left a worker running, the worker cannot finish before X waits in one of
    * {@code frames}; if it did not, X computes the table itself. Either way X must finish,
    * and its formula must have read {@code expected}.
    */
   private void readFirstByFormula(Worksheet ws, String table, int expected, String... frames)
      throws Exception
   {
      box = sandbox(ws);
      lock = box.getScriptEnv().getExecutionLock();
      harness.await(harness.submit(
         () -> box.getTableLens(table, AssetQuerySandbox.RUNTIME_MODE)), KNOWN_CAP,
                    "building " + table);

      Started<List<List<Object>>> script = harness.start(
         () -> drain(box.getTableLens("X", AssetQuerySandbox.RUNTIME_MODE)));
      awaitIn(script, KNOWN_CAP, frames);
      worker.release();

      assertFormulaRead(expected, harness.await(script.future, KNOWN_CAP,
                                                "X's formula reading " + table + " first"));
   }

   /**
    * Check that X has its one row, and that its formula column (after id and grp) is
    * {@code expected}.
    */
   private static void assertFormulaRead(int expected, List<List<Object>> rows) {
      assertEquals(2, rows.size());
      assertEquals(expected, ((Number) rows.get(1).get(2)).intValue(), "the value X's formula read");
   }

   /**
    * Run {@code task} as a script of another engine does, inside {@code exec}: holding the
    * execution lock of its own env (a viewsheet scope's env is never pooled) and flagged as a
    * script thread. This is what {@code LockCycleHarness.Sandbox.asGuest} does, with an env
    * that is not the worksheet sandbox's.
    */
   private <T> T asForeignScript(Callable<T> task) throws Exception {
      GraalJavaScriptEnv env = new GraalJavaScriptEnv();
      env.init();
      Lock foreign = env.getExecutionLock();
      assertNotSame(lock, foreign);
      foreign.lock();
      JavaScriptEngine.pushExecScriptable(mock(ScriptScope.class));

      try {
         return task.call();
      }
      finally {
         JavaScriptEngine.popExecScriptable();
         foreign.unlock();
      }
   }

   /**
    * Find A's condition filter in M's chain and make its sub-query read the formula lens
    * under the sub table's {@code DistinctTableLens} directly.
    */
   private static TableLens unwrapDistinctSubTable(TableLens mirror) throws Exception {
      XTable table = mirror;

      while(!table.getClass().getName().endsWith("ConditionFilter2")) {
         table = ((TableFilter) table).getTable();
      }

      Field conditions = ConditionFilter.class.getDeclaredField("conditions");
      conditions.setAccessible(true);
      Field sarr = AssetConditionGroup.class.getDeclaredField("sarr");
      sarr.setAccessible(true);
      Field subField = AssetCondition.class.getDeclaredField("sub");
      subField.setAccessible(true);
      Field stableField = SubQueryValue.class.getDeclaredField("stable");
      stableField.setAccessible(true);
      AssetCondition condition =
         ((AssetCondition[]) sarr.get(conditions.get(table)))[0];
      SubQueryValue sub = (SubQueryValue) subField.get(condition);
      XTable stable = (XTable) stableField.get(sub);

      while(!(stable instanceof FormulaTableLens)) {
         stable = ((TableFilter) stable).getTable();
      }

      sub.initSubTable(stable);
      return (TableLens) table;
   }

   /**
    * A real sandbox of {@code ws}, on pooled script contexts with {@code -Dlockcycle.pool}.
    */
   private static AssetQuerySandbox sandbox(Worksheet ws) {
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      assertEquals(POOL, box.isScriptPoolMode());
      return box;
   }

   /**
    * Wait until {@code started}'s thread is parked in one of {@code frames}
    * ({@code SimpleClassName.method}), or has finished, or {@code capSeconds} passed (with the
    * fix the thread may never get there).
    */
   private static void awaitIn(Started<?> started, long capSeconds, String... frames)
      throws InterruptedException
   {
      long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(capSeconds);

      while(System.currentTimeMillis() < deadline && !started.future.isDone()) {
         Thread thread = started.thread;

         if(thread != null && thread.getState() != Thread.State.RUNNABLE) {
            for(StackTraceElement element : thread.getStackTrace()) {
               String cls = element.getClassName();
               String frame = cls.substring(cls.lastIndexOf('.') + 1) + "." +
                  element.getMethodName();

               if(Arrays.asList(frames).contains(frame)) {
                  return;
               }
            }
         }

         Thread.sleep(5);
      }
   }

   private static EmbeddedTableAssembly embedded(Worksheet ws, String name, int rows,
                                                 Hook gate)
   {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "id", "grp" };

      for(int i = 1; i <= rows; i++) {
         data[i] = new Object[] { i, i % 3 };
      }

      String[] types = { XSchema.INTEGER, XSchema.INTEGER };
      table.setEmbeddedData(gate == null ? new XEmbeddedTable(types, data)
                               : new GatedData(types, data, gate));
      ws.addAssembly(table);
      return table;
   }

   /**
    * Add a JavaScript expression column.
    */
   private static void expression(TableAssembly table, String name, String script) {
      ExpressionRef exp = new ExpressionRef(null, name);
      exp.setExpression(script);
      ColumnRef column = new ColumnRef(exp);
      column.setDataType(XSchema.INTEGER);
      ColumnSelection columns = table.getColumnSelection(false);
      columns.addAttribute(column);
      table.setColumnSelection(columns, false);
   }

   /**
    * Add the pre-condition {@code A.id one of (B.bx)}, correlated on {@code grp} if asked.
    */
   private static void subQueryCondition(Worksheet ws, TableAssembly a, TableAssembly b,
                                         boolean correlated)
   {
      subQueryCondition(ws, a, b, correlated, "id");
   }

   /**
    * Add the pre-condition {@code A.<column> one of (B.bx)}, correlated on {@code grp} if
    * asked.
    */
   private static void subQueryCondition(Worksheet ws, TableAssembly a, TableAssembly b,
                                         boolean correlated, String column)
   {
      SubQueryValue sub = new SubQueryValue();
      sub.setQuery(b.getName());
      sub.setAttribute(b.getColumnSelection(false).getAttribute("bx"));

      if(correlated) {
         sub.setSubAttribute(b.getColumnSelection(false).getAttribute("grp"));
         sub.setMainAttribute(a.getColumnSelection(false).getAttribute("grp"));
      }

      sub.update(ws);
      AssetCondition condition = new AssetCondition();
      condition.setOperation(XCondition.ONE_OF);
      condition.setType(XSchema.INTEGER);
      condition.addValue(sub);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(a.getColumnSelection(false).getAttribute(column), condition, 0));
      a.setPreConditionList(list);
   }

   /**
    * Called by {@link GatedData}.
    */
   private interface Hook {
      void onMoreRows(int row);

      default void onGetObject(int row) {
      }
   }

   /**
    * Parks the first lens worker that asks for row {@code row} of the data while holding no
    * script lock, i.e. the formula lens's prefetch before it takes the engine lock.
    */
   private static final class WorkerGate implements Hook {
      WorkerGate(int row) {
         this.row = row;
      }

      @Override
      public void onMoreRows(int r) {
         if(r >= row && !isHarnessThread() && !JavaScriptEngine.holdsScriptLock() &&
            parked.getCount() > 0)
         {
            parked.countDown();

            try {
               // bounded, so a case that never releases cannot park the worker forever
               released.await(3 * KNOWN_CAP, TimeUnit.SECONDS);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }
      }

      boolean hasParked() {
         return parked.getCount() == 0;
      }

      void release() {
         released.countDown();
      }

      private final int row;
      private final CountDownLatch parked = new CountDownLatch(1);
      private final CountDownLatch released = new CountDownLatch(1);
   }

   /**
    * Parks its owner at its first read of the cell values of row {@code row} while it holds no
    * script lock, i.e. after the formula rows are computed (a formula batch reads the row
    * holding the engine lock).
    */
   private static final class BuilderGate implements Hook {
      BuilderGate(int row) {
         this.row = row;
      }

      void own() {
         owner = Thread.currentThread();
      }

      @Override
      public void onMoreRows(int r) {
      }

      @Override
      public void onGetObject(int r) {
         if(r == row && Thread.currentThread() == owner && !JavaScriptEngine.holdsScriptLock() &&
            entered.getCount() > 0)
         {
            entered.countDown();

            try {
               // bounded, so a case that never releases cannot park the owner forever
               released.await(3 * KNOWN_CAP, TimeUnit.SECONDS);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }
      }

      boolean awaitEntered(long capSeconds) throws InterruptedException {
         return entered.await(capSeconds, TimeUnit.SECONDS);
      }

      void release() {
         released.countDown();
      }

      private final int row;
      private volatile Thread owner;
      private final CountDownLatch entered = new CountDownLatch(1);
      private final CountDownLatch released = new CountDownLatch(1);
   }

   /**
    * Embedded data whose {@code moreRows} passes through a hook. Clones keep the hook, so the
    * worksheet's copies of the table share it.
    */
   private static final class GatedData extends XEmbeddedTable {
      GatedData(String[] types, Object[][] data, Hook gate) {
         super(types, data);
         this.gate = gate;
      }

      @Override
      public boolean moreRows(int row) {
         gate.onMoreRows(row);
         return super.moreRows(row);
      }

      @Override
      public Object getObject(int r, int c) {
         if(r > 0) {
            gate.onGetObject(r);
         }

         return super.getObject(r, c);
      }

      private final Hook gate;
   }

   // more rows than validateDataTypes (1000) and the formula lens's batches compute while A
   // is built
   private static final int SUB_ROWS = 5000;
   private static final int PARK_ROW = 2500;

   private LockCycleHarness harness;
   private WorkerGate worker;
   private AssetQuerySandbox box;
   private Lock lock;
}
