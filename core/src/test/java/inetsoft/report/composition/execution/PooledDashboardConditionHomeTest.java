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
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.script.graal.pool.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
 * Testing #77123 (cond-home): a dashboard of assemblies that share one data-cached worksheet
 * table whose formula columns keep an object and an array in top-level vars. Each assembly is
 * a mirror of the table with a JavaScript post condition and a formula column of its own, so
 * a condition filter's population reads the mirror's formula table, whose batches read the
 * shared table's rows. The assemblies are read at once on several threads, with the pool on,
 * and every one must match the pool off. On main the thread whose population ran a batch of
 * the shared table kept its context, the home of the table's objects, until its population
 * ended, while another thread's batch of the table needed them: they were lost (about one
 * table in ten wrong).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class, PooledDashboardConditionHomeTest.TestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PooledDashboardConditionHomeTest {
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      // a data-cached table checks its MV state
      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   static final String OBJ = "var o = o || {s: 0}; o.s += field['id']; o.s";
   static final String ARR = "var a = a || []; a.push(field['id']); a.length";

   @AfterEach
   void tearDown() {
      SreeEnv.remove(PoolConfig.ENABLED);

      for(AssetQuerySandbox box : boxes) {
         if(box.peekScriptEnv() instanceof WorksheetScriptEnv env) {
            env.retire();
         }
      }

      boxes.clear();
      AssetDataCache.getCache().clearCache();
      assertEquals(0, SlotClaim.openClaims(), "a claim was left open");
   }

   /**
    * The condition shape (the tester's dashboard case), and the aggregate and sort shapes
    * next to it, which were already exact: k assemblies on k threads, several rounds, each
    * table equal to the pool off. The loss on main depends on timing (in the tester's runs
    * 0 to 20 % of the condition tables per run); PooledLensObjectVarTest's
    * aReadWhileAnOuterClaimIsOpenTakesTheGivenBackHome reproduces it deterministically.
    */
   @ParameterizedTest(name = "{0} k={1}")
   @CsvSource({ "cond, 2", "cond, 4", "agg, 4", "sort, 4" })
   void assembliesSharingACachedObjectVarTableMatchThePoolOff(String kind, int k)
      throws Exception
   {
      int rounds = kind.equals("cond") ? 12 : 3;
      List<List<List<Object>>> truth = new ArrayList<>();
      AssetQuerySandbox off = sandbox(false, dashboardWorksheet(k, ROWS, kind));

      for(int i = 0; i < k; i++) {
         truth.add(drain(off.getTableLens("B" + i, AssetQuerySandbox.RUNTIME_MODE)));
      }

      assertEquals(kind.equals("agg") ? 6 : ROWS + 1, truth.get(0).size(),
                   "a group per grp, or every row (the condition keeps them all)");
      AssetQuerySandbox box = sandbox(true, dashboardWorksheet(k, ROWS, kind));
      ExecutorService threads = Executors.newFixedThreadPool(k);
      List<String> wrong = new ArrayList<>();

      try {
         for(int round = 0; round < rounds; round++) {
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
               if(!truth.get(i).equals(tables.get(i).get(120, TimeUnit.SECONDS))) {
                  wrong.add("B" + i + " round " + round);
               }
            }
         }
      }
      finally {
         threads.shutdownNow();
      }

      assertTrue(wrong.isEmpty(), () -> wrong.size() + " of " + k * rounds +
         " tables differ from the pool off: " + wrong);
   }

   // A with object and array vars, in the data cache, and k mirrors B0..Bk-1 of it
   private static Worksheet dashboardWorksheet(int k, int rows, String kind) {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = new EmbeddedTableAssembly(ws, "A");
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "value", "id", "grp" };

      for(int i = 1; i <= rows; i++) {
         data[i] = new Object[] { i * 7, i, i % 5 };
      }

      a.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.INTEGER, XSchema.INTEGER, XSchema.INTEGER }, data));
      ws.addAssembly(a);
      expression(a, "v", OBJ);
      expression(a, "v2", ARR);

      for(int i = 0; i < k; i++) {
         MirrorTableAssembly b = new MirrorTableAssembly(ws, "B" + i, a);
         b.setProperty("no_cache", "true");
         ws.addAssembly(b);
         b.update();
         ColumnSelection cols = b.getColumnSelection(false);

         if(kind.equals("agg")) {
            AggregateInfo agg = new AggregateInfo();
            agg.addGroup(new GroupRef(cols.getAttribute("grp")));
            agg.addAggregate(new AggregateRef(cols.getAttribute("v"), AggregateFormula.SUM));
            agg.addAggregate(new AggregateRef(cols.getAttribute("v2"), AggregateFormula.MAX));
            b.setAggregateInfo(agg);
         }
         else if(kind.equals("sort")) {
            SortInfo sort = new SortInfo();
            SortRef ref = new SortRef(cols.getAttribute("v"));
            ref.setOrder(XConstants.SORT_DESC);
            sort.addSort(ref);
            b.setSortInfo(sort);
         }
         else {
            // a JavaScript condition value that keeps every row (ids are positive), and a
            // formula column of the mirror's own over the shared table's object var
            jsCondition(b, "id", "Math.min(0, " + i + ")");
            expression(b, "w", "field['v'] + 1");
         }
      }

      return ws;
   }

   private static void expression(TableAssembly table, String name, String formula) {
      ColumnSelection columns = table.getColumnSelection(false);
      ExpressionRef exp = new ExpressionRef(null, name);
      exp.setExpression(formula);
      ColumnRef column = new ColumnRef(exp);
      column.setDataType(XSchema.DOUBLE);
      columns.addAttribute(column);
      table.setColumnSelection(columns, false);
   }

   private static void jsCondition(TableAssembly table, String column, String js) {
      ConditionList list = new ConditionList();
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.GREATER_THAN);
      cond.setType(XSchema.INTEGER);
      ExpressionValue value = new ExpressionValue();
      value.setExpression(js);
      value.setType(ExpressionValue.JAVASCRIPT);
      cond.addValue(value);
      list.append(new ConditionItem(table.getColumnSelection(false).getAttribute(column),
                                    cond, 0));
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

   private static final int ROWS = 3000;
   private final List<AssetQuerySandbox> boxes = new ArrayList<>();
}
