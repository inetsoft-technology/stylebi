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
import inetsoft.report.*;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.filter.*;
import inetsoft.report.lens.*;
import inetsoft.report.script.viewsheet.CalcTableVSAScriptable;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.script.graal.ScriptStopTestSupport;
import inetsoft.util.script.graal.ScriptStopTestSupport.Stops;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import inetsoft.util.script.graal.pool.PoolConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78134: a script stop (a script timeout or cancel) reaching a table or script read
 * through a catch block that carved out only a lock stall, a lost swap file or a load failure
 * was treated as an ordinary error, and the reader got a partial, empty or wrong result (or
 * an unrelated NPE) instead of the stop. Each test reads a real lens whose base is a real
 * {@link FormulaTableLens} with one formula exec stopped, both fresh (the stop surfaces at
 * {@code moreRows}) and already read by another reader (it surfaces at {@code getObject}),
 * and checks that every read fails with the stop.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class, ScriptStopCatchSitesTest.TestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ScriptStopCatchSitesTest {
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      previousTimeout = ScriptStopTestSupport.setTimeout("1");
      AssetDataCache.getCache().clearCache();
      stops = new Stops();
      env = new ScriptStopTestSupport.StoppingEnv(stops);
      env.init();
      SreeEnv.setProperty(PoolConfig.ENABLED, "false");
   }

   @AfterEach
   void tearDown() throws Exception {
      Thread.interrupted();
      ScriptStopTestSupport.setTimeout(previousTimeout);
      SreeEnv.remove(PoolConfig.ENABLED);
      AssetDataCache.getCache().clearCache();
   }

   // ---- sites 1 + 2: AssetCondition / SubQueryValue ----

   /**
    * A non-correlated one-of sub-query whose sub table is stopped: the stop is no result, so
    * neither the condition's last result (optimized) nor the values read so far are taken by
    * the next evaluation. On the old code the retry returned {@code false} for a value that
    * is in the sub-query.
    */
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void subQueryConditionDoesNotKeepAStoppedResult(boolean optimized) throws Exception {
      for(boolean drained : new boolean[] { false, true }) {
         FormulaTableLens sub = formulaLens(5, 3, drained);
         SubQueryValue subQuery = new SubQueryValue();
         subQuery.setAttribute(new AttributeRef(null, "f"));
         AssetCondition condition = new AssetCondition();
         condition.setOperation(XCondition.ONE_OF);
         condition.setType(XSchema.INTEGER);
         condition.addValue(subQuery);
         condition.setOptimized(optimized);
         condition.init();
         condition.initSubTable(sub);
         condition.initMainTable(new DefaultTableLens(new Object[][] { { "v" }, { 1 } }), 0);
         String tag = "optimized=" + optimized + " drained=" + drained;

         assertStop(() -> condition.evaluate(10), tag + " first");
         assertStop(() -> condition.evaluate(10), tag + " retry of the same value");
         assertStop(() -> condition.evaluate(50), tag + " another value");
      }
   }

   // ---- U2 / U3: SortFilter, DistinctTableLens ----

   @Test
   void sortFilterFailsWithTheStop() {
      for(boolean drained : new boolean[] { false, true }) {
         SortFilter sorted = new SortFilter(formulaLens(40, 25, drained), new int[] { 2 },
                                            new boolean[] { false });
         assertStop(() -> col(sorted, 2), "drained=" + drained + " first read");
         assertStop(() -> col(sorted, 2), "drained=" + drained + " second read");
      }
   }

   /**
    * The hash (one column) and the sort (two columns) distinct shapes: a stop of the base is
    * kept like a lost swap file, every reader fails with it and the table is stopped, rather
    * than complete with the rows found so far.
    */
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void distinctTableFailsEveryReaderWithTheStop(boolean hash) {
      for(boolean drained : new boolean[] { false, true }) {
         int[] cols = hash ? new int[] { 2 } : new int[] { 1, 2 };
         DistinctTableLens distinct = new DistinctTableLens(formulaLens(40, 25, drained), cols);
         String tag = "hash=" + hash + " drained=" + drained;

         assertStop(() -> col(distinct, 2), tag + " first read");
         assertStop(() -> col(distinct, 2), tag + " second read");
         assertStop(distinct::getRowCount, tag + " row count");
         assertTrue(distinct.isStopped(), tag);
         assertTrue(AssetDataCache.isStopped(distinct), tag);
      }
   }

   /**
    * F1: a group summary over the sorted stopped table (what a worksheet group + aggregate
    * builds) fails every reader with the stop, rather than complete with no groups.
    */
   @Test
   void summaryFilterFailsEveryReaderWithTheStop() {
      for(boolean drained : new boolean[] { false, true }) {
         SummaryFilter summary = new SummaryFilter(
            new SortFilter(formulaLens(40, 25, drained), new int[] { 0 }), 2, new SumFormula(),
            null);
         String tag = "drained=" + drained;

         assertStop(() -> col(summary, 1), tag + " first read");
         assertStop(() -> col(summary, 1), tag + " second read");
         // a reported failure restarts the pass (as for a set table that may recover), the
         // restarted pass fails again on the stopped base
         assertStop(() -> summary.moreRows(TableLens.EOT), tag + " read to the end");
      }
   }

   // ---- site 3: SelfJoinTableLens / SelfJoinOperator ----

   @Test
   void selfJoinFailsEveryReaderWithTheStop() {
      for(boolean drained : new boolean[] { false, true }) {
         // value < f holds for every row, the stop is past the first rows addJoin reads
         SelfJoinTableLens join = new SelfJoinTableLens(formulaLens(40, 25, drained));
         join.addJoin(1, XConstants.LESS_JOIN, 2);
         String tag = "drained=" + drained;

         assertStop(() -> col(join, 1), tag + " first read");
         assertStop(() -> col(join, 1), tag + " second read");
         assertTrue(join.isStopped(), tag);
      }
   }

   // ---- sites 4a / 4b: CrossTabFilter, RankingTableLens ----

   @Test
   void crosstabFailsWithTheStopNotAnNpe() {
      for(boolean drained : new boolean[] { false, true }) {
         CrossTabFilter cross = new CrossTabFilter(formulaLens(40, 25, drained), new int[] { 0 },
                                                   new int[0], 2, new SumFormula());
         String tag = "drained=" + drained;

         assertStop(() -> {
            cross.moreRows(TableLens.EOT);
            cross.getRowCount();
         }, tag + " first read");
         assertStop(() -> {
            cross.moreRows(TableLens.EOT);
            cross.getRowCount();
         }, tag + " second read");
      }
   }

   @Test
   void rankingFailsEveryReaderWithTheStop() {
      for(boolean drained : new boolean[] { false, true }) {
         RankingTableLens rank = new RankingTableLens(formulaLens(40, 25, drained));
         rank.setRankingColumn(2);
         rank.setRankingN(3);
         rank.setTopRanking(true);
         String tag = "drained=" + drained;

         assertStop(() -> col(rank, 2), tag + " first read");
         // the old code ranked no rows on the second read, for good
         assertStop(() -> col(rank, 2), tag + " second read");
      }
   }

   // ---- site 7 / U4: the chart data set in the ViewsheetSandbox dmap ----

   /**
    * A chart data set over a table whose formula is stopped as the data set reads it fails
    * the reader with the stop, rather than be an empty data set, and is not cached.
    */
   @Test
   void chartDataSetOfAStoppedTableFailsAndIsNotCached() throws Exception {
      ViewsheetSandbox box = vsBox();
      VSAQuery query = mock(VSAQuery.class);
      AtomicInteger calls = new AtomicInteger();
      when(query.getData()).thenAnswer(
         inv -> new VSDataSet(formulaLens(5, calls.incrementAndGet() == 1 ? 3 : -1, false),
                              new VSDataRef[0]));

      try(MockedStatic<VSAQuery> ignored = mockQuery(query)) {
         assertStop(() -> box.getData("Chart1"), "first read");
         VSDataSet ds = (VSDataSet) box.getData("Chart1");

         assertEquals(5, ds.getRowCount());
         assertEquals(2, calls.get());
      }
   }

   /**
    * A cached chart data set whose table a later change made compute again, and stop (the
    * change listener of the data set, if it is still registered, reads it again and cannot
    * fail), is computed again by the next reader of the sandbox instead of being handed out
    * empty or stopped.
    */
   @Test
   void cachedChartDataSetOfAStoppedTableIsComputedAgain() throws Exception {
      ViewsheetSandbox box = vsBox();
      VSAQuery query = mock(VSAQuery.class);
      List<FormulaTableLens> lenses = new ArrayList<>();
      when(query.getData()).thenAnswer(inv -> {
         FormulaTableLens lens = formulaLens(5, -1, false);
         lenses.add(lens);
         return new VSDataSet(lens, new VSDataRef[0]);
      });

      try(MockedStatic<VSAQuery> ignored = mockQuery(query)) {
         VSDataSet first = (VSDataSet) box.getData("Chart1");
         assertEquals(5, first.getRowCount());

         // the table changes and its formula is stopped as it is read again. the table keeps
         // its data set's change listener weakly, so the listener may be gone: read it here
         stops.reset(marker, n -> n == 3, true);
         lenses.get(0).invalidate();
         assertStop(() -> col(lenses.get(0), 2), "reading the changed table");
         assertTrue(AssetDataCache.isStopped(first.getTable()));
         stops.reset(marker, n -> false, true);

         VSDataSet second = (VSDataSet) box.getData("Chart1");

         assertNotSame(first, second);
         assertEquals(5, second.getRowCount());
         assertEquals(2, lenses.size());
      }
   }

   // ---- site 5a: TableDataVSAScriptable ----

   /**
    * A script reading a freehand table whose formula is stopped fails with the stop, rather
    * than read no table, and the next read gets the table.
    */
   @Test
   void scriptTableOfAStoppedFreehandTableFailsWithTheStop() throws Exception {
      FormulaTable elem = mock(FormulaTable.class);
      when(elem.getScriptEnv()).thenReturn(env);
      when(elem.getID()).thenReturn("CalcTable1");
      when(elem.getScriptTable()).thenReturn(
         new DefaultTableLens(new Object[][] { { "a", "b" }, { 1, 10 } }));
      String formulaMarker = marker();
      CalcTableLens calc = new CalcTableLens(1, 1);
      calc.setElement(elem);
      calc.setObject(0, 0, new CalcTableLens.Formula("[1, 2, 3]" + formulaMarker));
      calc.setExpansion(0, 0, CalcTableLens.EXPAND_VERTICAL);
      stops.reset(formulaMarker, n -> n == 1, true);
      ViewsheetSandbox box = vsBox();
      DataVSAQuery query = mock(DataVSAQuery.class);
      when(query.getViewTableLens(any())).thenAnswer(inv -> new VSTableLens(inv.getArgument(0)));
      when(query.getData()).thenAnswer(inv -> calc.process());

      try(MockedStatic<VSAQuery> ignored = mockQuery(query)) {
         CalcTableVSAScriptable scriptable = new CalcTableVSAScriptable(box);
         scriptable.setAssembly("CalcTable1");

         assertStop(() -> scriptable.getMember("table"), "first read");
         assertNotNull(scriptable.getMember("table"), "the next read gets the table");
      }
   }

   /**
    * {@code getTable0}, reached by the highlighted member once the table array is there: a
    * stop is rethrown, and the empty placeholder is not kept for the next read.
    */
   @Test
   void scriptHighlightedOfAStoppedTableFailsWithTheStop() throws Exception {
      ViewsheetSandbox box = vsBox();
      DataVSAQuery query = mock(DataVSAQuery.class);
      AtomicInteger calls = new AtomicInteger();
      when(query.getViewTableLens(any())).thenAnswer(inv -> new VSTableLens(inv.getArgument(0)));
      when(query.getData()).thenAnswer(inv -> {
         if(calls.incrementAndGet() == 2) {
            throw ScriptStopTestSupport.stopped();
         }

         return new DefaultTableLens(new Object[][] { { "a" }, { 1 } });
      });

      try(MockedStatic<VSAQuery> ignored = mockQuery(query)) {
         CalcTableVSAScriptable scriptable = new CalcTableVSAScriptable(box);
         scriptable.setAssembly("CalcTable1");
         assertNotNull(scriptable.getMember("table"));
         box.resetDataMap("CalcTable1");

         assertStop(() -> scriptable.getMember("highlighted"), "first read");
         assertNotNull(scriptable.getMember("highlighted"));
         assertEquals(3, calls.get(), "the next read gets the table again");
      }
   }

   // ---- U6: CalcTableVSAQuery, a freehand table bound to data ----

   /**
    * A cell formula of a bound freehand table stopped as the query builds the table: the
    * query fails with the stop rather than return no table, which the sandbox caches as no
    * data.
    */
   @Test
   void boundFreehandTableQueryFailsWithTheStop() throws Exception {
      String formulaMarker = marker();
      stops.reset(formulaMarker, n -> n == 1, true);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class, RETURNS_DEEP_STUBS);
      Viewsheet vs = new Viewsheet();
      CalcTableVSAssembly cassembly = new CalcTableVSAssembly(vs, "Calc1");
      vs.addAssembly(cassembly);
      TableLayout layout = cassembly.getTableLayout();
      TableCellBinding cell =
         new TableCellBinding(CellBinding.BIND_FORMULA, "rowList(data, 'col')" + formulaMarker);
      cell.setExpansion(GroupableCellBinding.EXPAND_V);
      layout.setCellBinding(0, 0, cell);
      when(box.getViewsheet()).thenReturn(vs);
      when(box.getVariableTable()).thenReturn(new VariableTable());
      when(box.getID()).thenReturn("vs1");
      when(box.getScope().getScriptEnv()).thenReturn(env);
      TableAssembly table = mock(TableAssembly.class);
      TableLens data = new DefaultTableLens(new Object[][] { { "col" }, { "a" }, { "b" } });
      CalcTableVSAQuery query = new CalcTableVSAQuery(box, "Calc1", false) {
         @Override
         public TableAssembly getTableAssembly() {
            return table;
         }

         @Override
         protected TableLens getTableLens(TableAssembly table) {
            return data;
         }
      };

      assertStop(query::getTableLens, "the query");
      assertEquals(1, stops.stops());
      verify(box).unlockWrite();
   }

   // ---- helpers ----

   private static void assertStop(Executable read, String what) {
      Throwable ex = assertThrows(Throwable.class, read, what + ": no stop");
      assertTrue(ScriptTimeoutGuard.isStop(ex), what + ": not the stop: " + ex);
   }

   /**
    * key, value, f = value * 10, for {@code rows} rows; the exec of row {@code stopRow} is
    * stopped (none if -1), and if {@code drained}, another reader already read it to the
    * stop.
    */
   private FormulaTableLens formulaLens(int rows, int stopRow, boolean drained) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "key", "value" };

      for(int i = 1; i <= rows; i++) {
         data[i] = new Object[] { "k" + (i % 2 == 0 ? "B" : "A"), i };
      }

      marker = marker();
      stops.reset(marker, n -> n == stopRow, true);
      FormulaTableLens lens = new FormulaTableLens(
         new DefaultTableLens(data), new String[] { "f" },
         new String[] { "field['value'] * 10" + marker }, env, null);

      if(drained) {
         assertStop(() -> col(lens, 2), "draining the base");
      }

      return lens;
   }

   private static String marker() {
      return "/*s78134_" + NONCE.incrementAndGet() + "*/";
   }

   private static List<Object> col(TableLens t, int c) {
      List<Object> vals = new ArrayList<>();

      for(int r = t.getHeaderRowCount(); t.moreRows(r); r++) {
         vals.add(t.getObject(r, c));
      }

      return vals;
   }

   private static ViewsheetSandbox vsBox() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new ChartVSAssembly(vs, "Chart1"));
      vs.addAssembly(new CalcTableVSAssembly(vs, "CalcTable1"));
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.VIEWSHEET, "Stop78134", null);
      return new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
   }

   private static MockedStatic<VSAQuery> mockQuery(VSAQuery query) {
      MockedStatic<VSAQuery> st = Mockito.mockStatic(VSAQuery.class, CALLS_REAL_METHODS);
      st.when(() -> VSAQuery.createVSAQuery(any(), any(), anyInt())).thenReturn(query);
      return st;
   }

   private static final AtomicLong NONCE = new AtomicLong();
   private Stops stops;
   private ScriptStopTestSupport.StoppingEnv env;
   private String marker;
   private String previousTimeout;
}
