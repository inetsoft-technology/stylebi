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
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.script.formula.TableAssemblyScriptable;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.script.graal.ScriptStopTestSupport;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78134 end to end: a worksheet expression column whose script is stopped by the real
 * script timeout (one row runs for 1.5 s, past a 1 s timeout). The reader of the table, of a
 * table filtered by a sub-query on it, of a crosstab on it, or a script reading it, fails
 * with the stop. It used to get the design table (a meta sample value), no rows, a partial
 * sub-query, an NPE or no table, and the caches kept the wrong table for the next reader.
 * With the timeout raised, the same sandbox and a new one (another session) get every row.
 * Slow: every case waits for the real timeout and then computes the slow row again.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class, WorksheetScriptStopTest.TestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("slow")
class WorksheetScriptStopTest {
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      // a cached table is checked against its materialized view (none here)
      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      previousTimeout = ScriptStopTestSupport.setTimeout("1");
      AssetDataCache.getCache().clearCache();
      SreeEnv.setProperty(PoolConfig.ENABLED, "false");
   }

   @AfterEach
   void tearDown() throws Exception {
      Thread.interrupted();
      ScriptStopTestSupport.setTimeout(previousTimeout);
      SreeEnv.remove(PoolConfig.ENABLED);

      for(AssetQuerySandbox box : boxes) {
         if(box.peekScriptEnv() instanceof WorksheetScriptEnv env) {
            env.retire();
         }
      }

      boxes.clear();
      AssetDataCache.getCache().clearCache();
   }

   /**
    * U1: a stop in the first rows, which the query reads to type its columns, used to turn
    * the table into the design table, which the sandbox then kept.
    */
   @ParameterizedTest
   @ValueSource(ints = { AssetQuerySandbox.RUNTIME_MODE, AssetQuerySandbox.LIVE_MODE })
   void stoppedExpressionFailsTheReader(int mode) throws Exception {
      Worksheet ws = ws(20, 12, false, false, false);
      AssetQuerySandbox box = box(ws);

      assertStop(() -> col(box.getTableLens("S", mode), "out"), "the first read");

      ScriptStopTestSupport.setTimeout("10");
      assertEquals(ids(20), col(box.getTableLens("S", mode), "out"), "the same sandbox");
      assertEquals(ids(20), col(box(ws).getTableLens("S", mode), "out"), "a new sandbox");
   }

   /**
    * An expression error that is no stop keeps the design table and the warning, so that the
    * column can be corrected.
    */
   @ParameterizedTest
   @ValueSource(ints = { AssetQuerySandbox.RUNTIME_MODE, AssetQuerySandbox.LIVE_MODE })
   void expressionErrorStillGetsTheDesignTable(int mode) throws Exception {
      Worksheet ws = ws(20, 12, false, false, false);
      EmbeddedTableAssembly s = (EmbeddedTableAssembly) ws.getAssembly("S");
      ColumnRef out = (ColumnRef) s.getColumnSelection(false).getAttribute("out");
      ((ExpressionRef) out.getDataRef()).setExpression(
         "if(field['id'] == 12) { throw new Error('bad row'); } field['id']");

      List<Exception> warnings = new ArrayList<>();
      WorksheetService.ASSET_EXCEPTIONS.set(warnings);

      try {
         TableLens table = box(ws).getTableLens("S", mode);

         assertNotNull(table);
         assertFalse(table.moreRows(2), "the design table, not the data");
         // the error names the failed column, so that it can be corrected
         assertEquals(1, warnings.size(), "the expression error is reported: " + warnings);
         assertTrue(warnings.get(0).getMessage().contains("out"), warnings.get(0).getMessage());
         assertTrue(warnings.get(0).getMessage().contains("bad row"),
                    warnings.get(0).getMessage());
      }
      finally {
         WorksheetService.ASSET_EXCEPTIONS.remove();
      }
   }

   /**
    * Sites 1/2, U2, U3, U5: a sub-query condition on a table whose expression is stopped,
    * eagerly (first rows) and lazily (past them) through the sort (two columns) and hash
    * (one column) distinct of the sub table. A used to get no rows or a partial result,
    * and a new sandbox got it from the shared cache.
    */
   @ParameterizedTest
   @CsvSource({ "20, 12, false", "1500, 1200, false", "1500, 1200, true" })
   void subQueryOfAStoppedTableFailsTheReader(int rows, int slow, boolean hideId)
      throws Exception
   {
      Worksheet ws = ws(rows, slow, hideId, true, false);
      AssetQuerySandbox box = box(ws);

      assertStop(() -> col(box.getTableLens("A", AssetQuerySandbox.RUNTIME_MODE), "id"),
                 "the first read");

      ScriptStopTestSupport.setTimeout("10");
      assertEquals(ids(rows), col(box.getTableLens("A", AssetQuerySandbox.RUNTIME_MODE), "id"),
                   "the same sandbox");
      assertEquals(ids(rows), col(box(ws).getTableLens("A", AssetQuerySandbox.RUNTIME_MODE), "id"),
                   "a new sandbox");
   }

   /**
    * Site 5b: a script reading a table whose expression is stopped used to get the design
    * table's sample value.
    */
   @Test
   void scriptElementTableFailsWithTheStop() throws Exception {
      AssetQuerySandbox box = box(ws(20, 12, false, false, false));
      TableAssemblyScriptable s =
         new TableAssemblyScriptable("S", box, AssetQuerySandbox.RUNTIME_MODE);

      assertStop(s::getElementTable, "the script read");
   }

   /**
    * Sites 4a + 5b: a crosstab on the stopped column, stopped past the first rows, used to
    * fail with an NPE, which the script read turned into no table.
    */
   @Test
   void crosstabOfAStoppedTableFailsWithTheStop() throws Exception {
      Worksheet ws = ws(1500, 1200, false, false, true);
      AssetQuerySandbox box = box(ws);
      TableAssemblyScriptable s =
         new TableAssemblyScriptable("S", box, AssetQuerySandbox.RUNTIME_MODE);

      assertStop(s::getElementTable, "the script read");

      ScriptStopTestSupport.setTimeout("10");
      TableLens cross = box(ws).getTableLens("S", AssetQuerySandbox.RUNTIME_MODE);
      cross.moreRows(TableLens.EOT);
      // the header rows, then a = 0, a = 1 and the grand total
      assertEquals(3, cross.getRowCount() - cross.getHeaderRowCount());
   }

   /**
    * F1: a group + aggregate on the stopped column, stopped past the first rows, used to give
    * no groups and no error (SortFilter -> SummaryFilter).
    */
   @Test
   void groupAggregateOfAStoppedTableFailsTheReader() throws Exception {
      Worksheet ws = ws(1500, 1200, false, false, false);
      EmbeddedTableAssembly s = (EmbeddedTableAssembly) ws.getAssembly("S");
      ColumnSelection columns = s.getColumnSelection(false);
      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(columns.getAttribute("b")));
      info.addAggregate(new AggregateRef(columns.getAttribute("out"), AggregateFormula.SUM));
      s.setAggregateInfo(info);
      AssetQuerySandbox box = box(ws);

      assertStop(() -> col(box.getTableLens("S", AssetQuerySandbox.RUNTIME_MODE), "b"),
                 "the first read");

      ScriptStopTestSupport.setTimeout("10");
      List<Object> groups = List.of(0, 1, 2);
      assertEquals(groups, col(box.getTableLens("S", AssetQuerySandbox.RUNTIME_MODE), "b"),
                   "the same sandbox");
      assertEquals(groups, col(box(ws).getTableLens("S", AssetQuerySandbox.RUNTIME_MODE), "b"),
                   "a new sandbox");
   }

   private static void assertStop(Executable read, String what) {
      Throwable ex = assertThrows(Throwable.class, read, what + ": no stop");
      assertTrue(ScriptTimeoutGuard.isStop(ex), what + ": not the stop: " + ex);
   }

   private AssetQuerySandbox box(Worksheet ws) {
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      boxes.add(box);
      return box;
   }

   /**
    * S: embedded id (1..rows), a = id % 2, b = id % 3, and the expression out = id, whose
    * row {@code slow} runs for 1.5 s. A: embedded id (1..rows), with the pre-condition
    * {@code id one of (S.out)} if {@code subQuery}.
    */
   private static Worksheet ws(int rows, int slow, boolean hideId, boolean subQuery,
                               boolean crosstab)
   {
      Worksheet ws = new Worksheet();
      String[] types = { XSchema.INTEGER, XSchema.INTEGER, XSchema.INTEGER };
      Object[][] sdata = new Object[rows + 1][];
      sdata[0] = new Object[] { "id", "a", "b" };

      for(int i = 1; i <= rows; i++) {
         sdata[i] = new Object[] { i, i % 2, i % 3 };
      }

      EmbeddedTableAssembly s = new EmbeddedTableAssembly(ws, "S");
      s.setEmbeddedData(new XEmbeddedTable(types, sdata));
      ws.addAssembly(s);
      ColumnSelection columns = s.getColumnSelection(false);
      ExpressionRef exp = new ExpressionRef(null, "out");
      // a source of its own: the script cache is global and keyed by the source
      exp.setExpression("if(field['id'] == " + slow + ") { var t0 = Date.now(); " +
         "while(Date.now() - t0 < 1500) {} } field['id'] /*ws78134_" + NONCE.incrementAndGet() +
         "*/");
      ColumnRef out = new ColumnRef(exp);
      out.setDataType(XSchema.INTEGER);
      columns.addAttribute(out);

      if(hideId) {
         for(String name : new String[] { "id", "a", "b" }) {
            ((ColumnRef) columns.getAttribute(name)).setVisible(false);
         }
      }

      s.setColumnSelection(columns, false);

      if(crosstab) {
         AggregateInfo info = new AggregateInfo();
         info.setCrosstab(true);
         info.addGroup(new GroupRef(columns.getAttribute("a")));
         info.addGroup(new GroupRef(columns.getAttribute("b")));
         info.addAggregate(new AggregateRef(columns.getAttribute("out"), AggregateFormula.SUM));
         s.setAggregateInfo(info);
      }

      if(subQuery) {
         Object[][] adata = new Object[rows + 1][];
         adata[0] = new Object[] { "id" };

         for(int i = 1; i <= rows; i++) {
            adata[i] = new Object[] { i };
         }

         EmbeddedTableAssembly a = new EmbeddedTableAssembly(ws, "A");
         a.setEmbeddedData(new XEmbeddedTable(new String[] { XSchema.INTEGER }, adata));
         ws.addAssembly(a);
         SubQueryValue sub = new SubQueryValue();
         sub.setQuery("S");
         sub.setAttribute(s.getColumnSelection(false).getAttribute("out"));
         assertTrue(sub.update(ws));
         AssetCondition cond = new AssetCondition();
         cond.setOperation(XCondition.ONE_OF);
         cond.setType(XSchema.INTEGER);
         cond.addValue(sub);
         ConditionList list = new ConditionList();
         list.append(new ConditionItem(a.getColumnSelection(false).getAttribute("id"), cond, 0));
         a.setPreConditionList(list);
      }

      return ws;
   }

   private static List<Object> ids(int rows) {
      List<Object> ids = new ArrayList<>();

      for(int i = 1; i <= rows; i++) {
         ids.add(i);
      }

      return ids;
   }

   private static List<Object> col(TableLens t, String name) {
      int col = -1;
      t.moreRows(0);

      for(int c = 0; c < t.getColCount(); c++) {
         if(name.equals(String.valueOf(t.getObject(0, c)))) {
            col = c;
         }
      }

      assertTrue(col >= 0, "no column " + name);
      List<Object> vals = new ArrayList<>();

      for(int r = t.getHeaderRowCount(); t.moreRows(r); r++) {
         Object val = t.getObject(r, col);
         vals.add(val instanceof Number num ? num.intValue() : val);
      }

      return vals;
   }

   private static final AtomicLong NONCE = new AtomicLong();
   private final List<AssetQuerySandbox> boxes = new ArrayList<>();
   private String previousTimeout;
}
