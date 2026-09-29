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

import inetsoft.report.TableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Testing #77123 (B1 residual part 2) end to end: a worksheet embedded table whose expression
 * column keeps an array or an object cache in a top-level var, under a post condition on that
 * column, run by a real {@link AssetQuerySandbox} with the context pool on and read in pages.
 * The condition filter opens a span around each read, so every formula batch is nested in
 * another claim (refuter F1): the table's objects stay on their home, and no page takes a
 * snapshot (amendment A1). The rows match the pool off.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class, PooledWorksheetObjectVarTest.TestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PooledWorksheetObjectVarTest {
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

   static final String GROW = "var a = a || []; a.push(field['id']); a.length";
   static final String CACHE10 =
      "var c = c || {}; c['k' + (field['id'] % 10)] = field['id']; var n = (n || 0) + 1; n";

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

   /**
    * F1 probe: a post condition over the formula column, read in 100-row pages. Pool on gives
    * the pool-off rows, with no tree snapshot at all (on the design as first proposed, one per
    * page).
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "grow", "cache10" })
   void aPostConditionOverAnObjectVarTakesNoSnapshotPerPage(String what) throws Exception {
      String f = what.equals("grow") ? GROW : CACHE10;
      double[] off = run(false, f, null);
      long[] handOffs = new long[1];
      long t0 = System.nanoTime();
      double[] on = run(true, f, handOffs);
      long ms = (System.nanoTime() - t0) / 1_000_000;
      System.out.println("B1OBJ cf2 " + what + ": pool on " + ms + " ms, hand-offs " +
                         handOffs[0]);

      for(int id = 1; id <= ROWS; id++) {
         assertEquals(off[id], on[id], "id " + id);
         assertEquals(id, on[id], "id " + id);
      }

      assertEquals(0, handOffs[0], "no snapshot per page");
   }

   private double[] run(boolean pool, String formula, long[] handOffs) throws Exception {
      Worksheet ws = ws(formula);
      EmbeddedTableAssembly a = (EmbeddedTableAssembly) ws.getAssembly("A");
      // a post condition on the formula column that keeps every row
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.GREATER_THAN);
      cond.setType(XSchema.DOUBLE);
      cond.addValue(-1e18);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(a.getColumnSelection(false).getAttribute("out"), cond, 0));
      a.setPostConditionList(list);

      SreeEnv.setProperty(PoolConfig.ENABLED, Boolean.toString(pool));
      AssetDataCache.getCache().clearCache();
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      boxes.add(box);
      assertEquals(pool, box.isScriptPoolMode());
      TableLens t = box.getTableLens("A", AssetQuerySandbox.RUNTIME_MODE);
      int cid = col(t, "id");
      int cout = col(t, "out");
      double[] out = new double[ROWS + 1];

      for(int s = 1; s <= ROWS; s += 100) {
         int e = Math.min(ROWS, s + 99);
         t.moreRows(e);

         for(int r = s; r <= e; r++) {
            out[(int) num(t.getObject(r, cid))] = num(t.getObject(r, cout));
         }
      }

      if(handOffs != null) {
         handOffs[0] = PoolTestSupport.metric((WorksheetScriptEnv) box.getScriptEnv(),
                                              "HandOffs");
      }

      return out;
   }

   private static Worksheet ws(String formula) {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "A");
      Object[][] data = new Object[ROWS + 1][];
      data[0] = new Object[] { "value", "id" };

      for(int i = 1; i <= ROWS; i++) {
         data[i] = new Object[] { 1, i };
      }

      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.INTEGER, XSchema.INTEGER }, data));
      ws.addAssembly(table);
      ColumnSelection columns = table.getColumnSelection(false);
      ExpressionRef exp = new ExpressionRef(null, "out");
      exp.setExpression(formula);
      ColumnRef column = new ColumnRef(exp);
      column.setDataType(XSchema.DOUBLE);
      columns.addAttribute(column);
      table.setColumnSelection(columns, false);
      return ws;
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

   // more rows than a worksheet query computes up front, so later pages run further batches
   private static final int ROWS = 5000;
   private final List<AssetQuerySandbox> boxes = new ArrayList<>();
}
