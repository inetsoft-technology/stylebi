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
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.script.JavaScriptEngine;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77158: the #76965 cycle on a worksheet the product builds itself, with the context
 * pool off (the default). Table A has a pre-condition "id one of (sub-query on B.bx)", where
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
 * while A is built, so the distinct worker started then is still reading it. The case parks
 * that worker before it takes the engine lock until the other two threads are in place.
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
   }

   @AfterEach
   public void tearDown() throws Exception {
      if(worker != null) {
         worker.release();
      }

      harness.close();

      if(box != null && lock != null && lock.tryLock()) {
         try {
            box.dispose();
         }
         finally {
            lock.unlock();
         }
      }
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
      box = new AssetQuerySandbox(ws);
      lock = box.getScriptEnv().getExecutionLock();

      // A is built and populated on a thread holding no lock, as a viewsheet or data
      // request would; building A starts the sub table's distinct worker
      Started<Integer> populator = harness.start(() -> {
         TableLens table = box.getTableLens("A", AssetQuerySandbox.RUNTIME_MODE);
         assertTrue(worker.awaitParked(KNOWN_CAP), "the distinct worker did not reach row " + PARK_ROW);
         return drain(table).size();
      });
      awaitIn(populator, "DistinctTableLens", "moreRows", KNOWN_CAP);

      // X's formula reads A by name, holding the engine lock
      Started<Integer> script = harness.start(
         () -> drain(box.getTableLens("X", AssetQuerySandbox.RUNTIME_MODE)).size());
      awaitIn(script, "AbstractConditionFilter", "moreRows", KNOWN_CAP);
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
      box = new AssetQuerySandbox(ws);
      lock = box.getScriptEnv().getExecutionLock();
      TableLens mirror = harness.await(harness.submit(
         () -> box.getTableLens("M", AssetQuerySandbox.RUNTIME_MODE)), KNOWN_CAP, "building M");

      // X's formula holds the engine lock and is parked in G
      Started<Integer> script = harness.startGated(gate,
         () -> drain(box.getTableLens("X", AssetQuerySandbox.RUNTIME_MODE)).size());
      assertTrue(gate.awaitEntered(KNOWN_CAP), "X's formula did not reach G");

      // M is populated on a thread holding no lock; it may have to wait for the engine lock
      Started<Integer> populator = harness.start(() -> drain(mirror).size());
      awaitIn(populator, "LendableReentrantLock", "lock", KNOWN_CAP);
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
      box = new AssetQuerySandbox(ws);
      lock = box.getScriptEnv().getExecutionLock();
      TableLens mirror = harness.await(harness.submit(
         () -> box.getTableLens("M", AssetQuerySandbox.RUNTIME_MODE)), KNOWN_CAP, "building M");
      TableLens filter = unwrapDistinctSubTable(mirror);

      // M's formula holds the engine lock and is parked reading A
      Started<Integer> formula = harness.startGated(gate, () -> drain(mirror).size());
      assertTrue(gate.awaitEntered(KNOWN_CAP), "M's formula did not read A");

      // another reader of A populates it further and re-reads the sub table
      Started<Integer> populator = harness.start(() -> drain(filter).size());
      awaitIn(populator, "LendableReentrantLock", "lock", KNOWN_CAP);
      gate.release();

      int rows = harness.await(formula.future, KNOWN_CAP, "M's formula reading A");
      assertEquals(rows, (int) harness.await(populator.future, KNOWN_CAP, "reader of A"));
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
    * Wait until {@code started}'s thread is parked in {@code cls.method}, or {@code capSeconds}
    * passed (with the fix the thread may never get there).
    */
   private static void awaitIn(Started<?> started, String cls, String method, long capSeconds)
      throws InterruptedException
   {
      long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(capSeconds);

      while(System.currentTimeMillis() < deadline && !started.future.isDone()) {
         Thread thread = started.thread;

         if(thread != null && thread.getState() != Thread.State.RUNNABLE) {
            for(StackTraceElement element : thread.getStackTrace()) {
               if(element.getClassName().endsWith("." + cls) &&
                  element.getMethodName().equals(method))
               {
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
    * Parks the first lens worker that asks for row {@code row} of the data while holding no
    * script lock, i.e. the formula lens's prefetch before it takes the engine lock.
    */
   /**
    * Called by {@link GatedData#moreRows}.
    */
   private interface Hook {
      void onMoreRows(int row);

      default void onGetObject(int row) {
      }
   }

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

      boolean awaitParked(long capSeconds) throws InterruptedException {
         return parked.await(capSeconds, TimeUnit.SECONDS);
      }

      void release() {
         released.countDown();
      }

      private final int row;
      private final CountDownLatch parked = new CountDownLatch(1);
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
