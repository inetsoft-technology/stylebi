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
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Context-pool regression D1 through a real worksheet query: opening a worksheet table with
 * an expression column (the query's type scan reads the first 1000 rows one by one) and
 * reading its first pages, directly or through a viewsheet table bound to it, runs the
 * expression for about the rows pool off runs it for. Before the fix the pooled open ran it
 * for 1795 rows where pool off ran it for 1001; a row-by-row reader of N rows now evaluates
 * at most about 2N + 10 (here 1277), the accepted sequential cost.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class, PooledWorksheetOpenReadAheadTest.TestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PooledWorksheetOpenReadAheadTest {
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

   @Test
   void openingAWorksheetTableRunsItsExpressionForAboutThePoolOffRows() throws Exception {
      int off = open(false, false);
      int on = open(true, false);
      assertTrue(off > 0 && off <= 1100, "pool off ran the expression " + off + " times");
      assertTrue(on <= off * 13 / 10, "pool on ran the expression " + on + " times, pool off " + off);
   }

   @Test
   void aViewsheetTableRunsItsWorksheetExpressionForAboutThePoolOffRows() throws Exception {
      int off = open(false, true);
      int on = open(true, true);
      assertTrue(off > 0 && off <= 1100, "pool off ran the expression " + off + " times");
      assertTrue(on <= off * 13 / 10, "pool on ran the expression " + on + " times, pool off " + off);
   }

   /**
    * @return the number of times the expression ran for opening the table and reading its
    * first three 100-row pages.
    */
   private int open(boolean pool, boolean viewsheet) throws Exception {
      SreeEnv.setProperty(PoolConfig.ENABLED, Boolean.toString(pool));
      // the data keys are content based: without this a run could read another's rows
      AssetDataCache.getCache().clearCache();
      Worksheet ws = ws();
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      boxes.add(box);
      assertEquals(pool, box.isScriptPoolMode());
      Counter counter = new Counter();
      box.getScriptEnv().put("counter", counter);
      TableLens table;

      if(viewsheet) {
         Object data = vsTable(ws, box).getData("TableV");
         table = data instanceof VSDataSet ? ((VSDataSet) data).getTable() : (TableLens) data;
      }
      else {
         table = box.getTableLens("A", AssetQuerySandbox.RUNTIME_MODE);
      }

      for(int s = 1; s <= 201; s += 100) {
         assertTrue(table.moreRows(s + 99));

         for(int r = s; r <= s + 99; r++) {
            table.getObject(r, 0);
         }
      }

      return counter.hits.get();
   }

   private static ViewsheetSandbox vsTable(Worksheet ws, AssetQuerySandbox box)
      throws Exception
   {
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
         "test/PooledWorksheetOpenReadAheadTest", null,
         OrganizationManager.getInstance().getCurrentOrgID());
      ViewsheetSandbox vbox = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null,
                                                   false, entry);
      Field wbox = ViewsheetSandbox.class.getDeclaredField("wbox");
      wbox.setAccessible(true);
      wbox.set(vbox, box);
      return vbox;
   }

   private static Worksheet ws() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "A");
      Object[][] data = new Object[ROWS + 1][];
      data[0] = new Object[] { "value", "id", "grp" };

      for(int i = 1; i <= ROWS; i++) {
         data[i] = new Object[] { 1, i, i % 3 };
      }

      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.INTEGER, XSchema.INTEGER, XSchema.INTEGER }, data));
      ws.addAssembly(table);
      ExpressionRef exp = new ExpressionRef(null, "out");
      exp.setExpression("counter.hit(); field['value'] + 1");
      ColumnRef column = new ColumnRef(exp);
      column.setDataType(XSchema.DOUBLE);
      ColumnSelection columns = table.getColumnSelection(false);
      columns.addAttribute(column);
      table.setColumnSelection(columns, false);
      return ws;
   }

   /**
    * A host object the expression calls once per row it runs for.
    */
   public static final class Counter {
      public int hit() {
         return hits.incrementAndGet();
      }

      final AtomicInteger hits = new AtomicInteger();
   }

   private static final int ROWS = 5000;
   private final List<AssetQuerySandbox> boxes = new ArrayList<>();
}
