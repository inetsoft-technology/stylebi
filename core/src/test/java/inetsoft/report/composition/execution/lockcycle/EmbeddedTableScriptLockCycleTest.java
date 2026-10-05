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

import inetsoft.report.composition.execution.*;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.SubQueryValue;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.script.graal.pool.PoolConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77301: {@code AssetQuerySandbox.getTableLens}'s {@code synchronized(table)} for an
 * {@code EmbeddedTableAssembly} (kept atomic across setup+execution since bug #73971) can hold
 * that monitor while the build blocks waiting for the worksheet's shared GraalJS
 * script-execution lock -- with the context pool off. The pool has been on by default since
 * #77123 (merged the same day as this fix), which already incidentally prevents this deadlock in
 * the default configuration (the pool removes the shared execution lock entirely, per
 * {@code WorksheetScriptEnv.getExecutionLock()}); the daily slow-tests workflow runs this
 * suite with {@code -Dlockcycle.pool=false}, since {@code script.ws.contextPool=false} remains
 * a supported, documented configuration and the deadlock is real for anyone running it: it
 * never resolves by itself, {@code stall.watchdog.mode=alert} only reports it, and the default
 * fail mode (Feature #77123) can only break it by failing one of the two queries. Unlike
 * {@link SubQueryConditionWorksheetCycleTest}, this needs no
 * sub-query, {@code Distinct}, or async worker: table A has its own plain JavaScript expression
 * column, which alone forces {@code AssetQuery}'s type probe
 * ({@code validateDataTypes}/{@code Util.getColType}) to compute formula rows through a
 * {@code FormulaTableLens} while {@code executeQuery} still holds A's monitor. Table X has an
 * expression column {@code A.length}, reading A by name from inside {@code exec()}, which holds
 * that same engine lock for the call.
 *
 * <p>Cycle (pre-fix): the builder of A holds A's monitor and waits for the engine lock in
 * {@code FormulaTableLens.lockForRow}; the builder of X holds the engine lock (computing X's own
 * formula) and waits for A's monitor inside {@code TableAssemblyScriptable.getElementTable}. Both
 * sides are ordinary requests -- neither starts out already holding anything -- so this is the
 * "two ordinary requests, cache miss, pool off" shape from the bug report, not a script-vs-script
 * race. A's builder is gated inside its own base data's first {@code moreRows()}, which happens
 * before {@code FormulaTableLens} ever attempts the engine lock (see
 * {@code FormulaTableLens.moreRows}), so it parks holding only what a same-order build would
 * already hold at that point.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
   LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
   EmbeddedTableScriptLockCycleTest.TestConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
public class EmbeddedTableScriptLockCycleTest {
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

      // the sandbox of this class is real, so -Dlockcycle.pool reaches it through the property
      // its env is chosen by; set it either way, so -Dlockcycle.pool=false runs it pool off
      // although the pool is on by default (#77123)
      SreeEnv.setProperty(PoolConfig.ENABLED, Boolean.toString(POOL));
   }

   @AfterEach
   public void tearDown() throws Exception {
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
    * Bug #77301's minimal shape: a plain embedded table with its own expression column, read by
    * name from another table's own expression column, with no sub-query in the mix. A's builder
    * is gated inside A's own base data before it ever touches the engine lock; X's builder reads
    * A by name from inside its own formula's {@code exec()}. Before the fix, releasing the gate
    * deadlocks: A's builder (holding A's monitor) blocks on the engine lock that X's builder
    * holds, while X's builder blocks entering A's monitor. With the fix, A's builder takes the
    * engine lock before A's monitor, so X's builder -- which needs that same lock to build its
    * own formula lens before it ever reads A -- simply waits its turn instead of deadlocking.
    */
   @Test
   public void formulaReadsPlainEmbeddedTableByName() throws Exception {
      Gate gate = harness.gate();
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = embedded(ws, "A", 5, gate);
      expression(a, "ax", "field['id'] * 1");
      EmbeddedTableAssembly x = embedded(ws, "X", 1, null);
      expression(x, "alen", "A.length");
      box = sandbox(ws);
      lock = box.getScriptEnv().getExecutionLock();

      // A is built on a thread holding no lock, as an ordinary request would; it parks inside
      // its own base data's first read, before FormulaTableLens ever attempts the engine lock.
      Started<Integer> populator = harness.startGated(gate,
         () -> drain(box.getTableLens("A", AssetQuerySandbox.RUNTIME_MODE)).size());
      assertTrue(gate.awaitEntered(KNOWN_CAP), "A's builder did not reach its base data");

      // X is also an ordinary request, not a script thread of anything, until its own formula
      // (A.length) starts running inside exec(); building X's own container takes the same
      // engine lock first (the fix), so it queues behind A's builder rather than reaching A's
      // monitor while holding the lock.
      Started<Integer> script = harness.start(
         () -> drain(box.getTableLens("X", AssetQuerySandbox.RUNTIME_MODE)).size());
      awaitIn(script, KNOWN_CAP, "LendableReentrantLock.lock");
      gate.release();

      assertEquals(2, harness.await(script.future, KNOWN_CAP, "X's formula reading A by name"));
      assertEquals(6, harness.await(populator.future, KNOWN_CAP, "populator of A"));
   }

   /**
    * Review round 1 finding on this bug: {@code table} needs no expression column of its own
    * for the cycle to form. A has none; its pre-condition is {@code id one of (subquery on
    * B.bx)}, and B (the sub-query's sub table) has the expression column instead. {@code
    * AssetQuery.getRuntimeTableLens} chains the pre-condition onto A's base lens before its
    * row-forcing second {@code validateDataTypes} call, still inside this class's
    * {@code synchronized(table)} for A -- so building A can still reach the script engine for
    * B's sake, exactly as it can for a column of its own. No {@code Distinct} or async worker
    * is involved: this is a strictly simpler reproduction than {@code
    * SubQueryConditionWorksheetCycleTest.formulaReadsFilteredTableFirst}, whose own, different
    * (worker-lending) cycle only starts after A's build has already finished -- this one never
    * lets A's build finish at all.
    *
    * <p>Before round 2 of this fix, {@code hasOwnScriptColumn(A)} was {@code false} (A has no
    * own column), so A's builder took no lock before its monitor here, exactly as the
    * plain-column case did before round 1's fix -- deadlocking against X's builder the same
    * way. A's builder is gated inside its own base data's first read (the same point
    * {@link #formulaReadsPlainEmbeddedTableByName} uses), which happens before A's
    * pre-condition is ever processed, so it parks holding only what a same-order build would
    * already hold at that point.
    */
   @Test
   public void formulaReadsTableWithSubQuerySubTableScriptColumn() throws Exception {
      Gate gate = harness.gate();
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = embedded(ws, "A", 5, gate);
      // same ids as A, so "id one of (subquery on B.bx)" keeps every row of A -- the
      // sub-query's presence is what this test exercises, not its filtering.
      EmbeddedTableAssembly b = embedded(ws, "B", 5, null);
      expression(b, "bx", "field['id'] * 1");
      subQueryCondition(ws, a, b);
      EmbeddedTableAssembly x = embedded(ws, "X", 1, null);
      expression(x, "alen", "A.length");
      box = sandbox(ws);
      lock = box.getScriptEnv().getExecutionLock();

      // A is built on a thread holding no lock, as an ordinary request would; it parks inside
      // its own base data's first read, before its pre-condition's sub-query on B is ever
      // touched.
      Started<Integer> populator = harness.startGated(gate,
         () -> drain(box.getTableLens("A", AssetQuerySandbox.RUNTIME_MODE)).size());
      assertTrue(gate.awaitEntered(KNOWN_CAP), "A's builder did not reach its base data");

      // X is also an ordinary request, not a script thread of anything, until its own formula
      // (A.length) starts running inside exec(); building X's own container takes the same
      // engine lock first (the fix), so it queues behind A's builder rather than reaching A's
      // monitor while holding the lock.
      Started<Integer> script = harness.start(
         () -> drain(box.getTableLens("X", AssetQuerySandbox.RUNTIME_MODE)).size());
      awaitIn(script, KNOWN_CAP, "LendableReentrantLock.lock");
      gate.release();

      assertEquals(2, harness.await(script.future, KNOWN_CAP, "X's formula reading A by name"));
      assertEquals(6, harness.await(populator.future, KNOWN_CAP, "populator of A"));
   }

   /**
    * Add the pre-condition {@code a.id one of (subquery on b.bx)} -- the shape
    * {@code SubQueryConditionWorksheetCycleTest.subQueryCondition} uses (uncorrelated), minus
    * the {@code grp} column that test's correlated variant needs and this one does not.
    */
   private static void subQueryCondition(Worksheet ws, EmbeddedTableAssembly a,
                                         EmbeddedTableAssembly b)
   {
      SubQueryValue sub = new SubQueryValue();
      sub.setQuery(b.getName());
      sub.setAttribute(b.getColumnSelection(false).getAttribute("bx"));
      sub.update(ws);
      AssetCondition condition = new AssetCondition();
      condition.setOperation(XCondition.ONE_OF);
      condition.setType(XSchema.INTEGER);
      condition.addValue(sub);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(a.getColumnSelection(false).getAttribute("id"), condition, 0));
      a.setPreConditionList(list);
   }

   /**
    * Wait until {@code started}'s thread is parked in one of {@code frames}
    * ({@code SimpleClassName.method}), or has finished, or {@code capSeconds} passed (with the
    * fix reverted the thread may never get there, or may get stuck at a different frame -- the
    * bounded wait in {@link LockCycleHarness#await} is what actually catches a real deadlock).
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

               if(java.util.Arrays.asList(frames).contains(frame)) {
                  return;
               }
            }
         }

         Thread.sleep(5);
      }
   }

   private static EmbeddedTableAssembly embedded(Worksheet ws, String name, int rows, Gate gate) {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "id" };

      for(int i = 1; i <= rows; i++) {
         data[i] = new Object[] { i };
      }

      String[] types = { XSchema.INTEGER };
      table.setEmbeddedData(gate == null ? new XEmbeddedTable(types, data)
                               : new GatedEmbeddedData(types, data, gate));
      ws.addAssembly(table);
      return table;
   }

   /**
    * Add a JavaScript expression column.
    */
   private static void expression(EmbeddedTableAssembly table, String name, String script) {
      ExpressionRef exp = new ExpressionRef(null, name);
      exp.setExpression(script);
      ColumnRef column = new ColumnRef(exp);
      column.setDataType(XSchema.INTEGER);
      ColumnSelection columns = table.getColumnSelection(false);
      columns.addAttribute(column);
      table.setColumnSelection(columns, false);
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
    * Embedded data that parks its owner thread (via {@code gate}) at its first data read (row
    * 0 is the header). This is what {@code FormulaTableLens.moreRows} reads before it ever
    * attempts the engine lock (see class javadoc), so the owner parks holding only what it
    * already held on the way in -- A's monitor, pre-fix; A's monitor and the engine lock, with
    * the fix applied -- never the engine lock alone.
    */
   private static final class GatedEmbeddedData extends XEmbeddedTable {
      GatedEmbeddedData(String[] types, Object[][] data, Gate gate) {
         super(types, data);
         this.gate = gate;
      }

      @Override
      public boolean moreRows(int row) {
         if(row > 0) {
            gate.onRead();
         }

         return super.moreRows(row);
      }

      private final Gate gate;
   }

   private LockCycleHarness harness;
   private AssetQuerySandbox box;
   private Lock lock;
}
