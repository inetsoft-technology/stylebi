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

import inetsoft.graph.data.DataSet;
import inetsoft.mv.MVManager;
import inetsoft.report.XSessionManager;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.script.viewsheet.ChartVSAScriptable;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #76862 (reopened, S5): a script that writes into a chart's binding after the chart's
 * data was computed. {@code ChartVSAScriptable.bindingInfo} edits the chart's live
 * {@link VSChartInfo} in place; the next {@link ViewsheetSandbox#getData(String)} must
 * return data for the new binding, whichever assembly owns the script. Same JDBC fixture as
 * {@link ChartTopNOthersSortTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  ChartScriptWriteDataCacheTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartScriptWriteDataCacheTest {
   private static final String DB = "memory:bug76862";

   @Configuration
   static class TestConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      @Bean
      public Plugins plugins(BlobStorageManager blobStorageManager, Cluster cluster,
                             ApplicationEventPublisher eventPublisher)
      {
         return new Plugins(blobStorageManager.getStorage("plugins", true), cluster, eventPublisher);
      }

      @Bean
      public ConnectionPoolFactory connectionPoolFactory() {
         DataSource ds = derby();
         ConnectionPoolFactory factory = mock(ConnectionPoolFactory.class);
         when(factory.getConnectionPool(any(), any())).thenReturn(ds);
         return factory;
      }

      @Bean
      public Drivers drivers(Plugins plugins, ConnectionPoolFactory connectionPoolFactory) {
         return new Drivers(plugins, connectionPoolFactory);
      }

      @Bean
      public Config config(Plugins plugins) {
         return new Config(plugins);
      }

      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }

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

      @Bean
      public XSessionManager xSessionManager(XSessionService sessionService) throws Exception {
         XRepository dataService = mock(XRepository.class);
         when(dataService.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
            .thenAnswer(inv -> {
               XQuery query = inv.getArgument(1);
               VariableTable vars = inv.getArgument(2);
               JDBCHandler handler = new JDBCHandler();
               handler.connect(query.getDataSource(), vars);
               return handler.execute(query, vars, inv.getArgument(3), inv.getArgument(5));
            });
         return new XSessionManager(dataService, sessionService, mock(DataSourceRegistry.class));
      }
   }

   @BeforeAll
   static void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table st");
         }
         catch(Exception ignore) {
         }

         stmt.executeUpdate("create table st (state varchar(10), num int)");
         String[][] rows = { { "AZ", "1" }, { "CA", "51" }, { "CO", "2" }, { "MA", "3" },
                             { "NY", "40" }, { "TX", "30" }, { "WA", "20" } };

         for(String[] row : rows) {
            stmt.executeUpdate("insert into st values ('" + row[0] + "', " + row[1] + ")");
         }
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      SreeEnv.setProperty(PoolConfig.ENABLED, "false");
      AssetDataCache.getCache().clearCache();
      buildViewsheet();
   }

   @AfterEach
   void tearDown() {
      SreeEnv.remove(PoolConfig.ENABLED);

      if(box != null && box.peekScriptEnv() instanceof WorksheetScriptEnv env) {
         env.retire();
      }

      AssetDataCache.getCache().clearCache();
   }

   /**
    * Top 3 by Sum(NUM) is CA, NY, TX; bottom 3 is AZ, CO, MA. The write runs through
    * {@link inetsoft.report.script.viewsheet.ViewsheetScope#execute(String, String)}, the
    * call both the viewer ({@code executeScript}) and the agent script service make, once
    * as the chart's own script and once as a Text's script.
    */
   @ParameterizedTest(name = "script owner {0}")
   @ValueSource(strings = { CHART, TEXT })
   void scriptWriteIsReflectedInNextGetData(String owner) throws Exception {
      Object before = vbox.getData(CHART);
      assertEquals(TOP3, states(before), "cache warm-up");

      vbox.getScope().execute(SCRIPT, owner);
      assertEquals("" + XCondition.BOTTOM_N, dim.getRankingOptionValue(), "design ref written");

      Object after = vbox.getData(CHART);
      assertEquals(BOTTOM3, states(after),
         "getData after the script write (owner " + owner + "): same object as before=" +
         (after == before) + ", " + cacheState());
   }

   /**
    * Regenerating the runtime refs without touching the data map is not a remedy: the next
    * getData must still reflect the write (refresh R3 / rtref D6).
    */
   @Test
   void scriptWriteIsReflectedAfterRuntimeRefRefreshOnly() throws Exception {
      Object before = vbox.getData(CHART);
      assertEquals(TOP3, states(before), "cache warm-up");

      vbox.getScope().execute(SCRIPT, TEXT);
      vbox.updateAssembly(chart);

      Object after = vbox.getData(CHART);
      assertEquals(BOTTOM3, states(after),
         "getData after the write + updateAssembly only: same object as before=" +
         (after == before) + ", " + cacheState());
   }

   /**
    * A data reset alone is enough: the re-query rebuilds the runtime refs from the written
    * design ref (refresh R2 / rtref D2).
    */
   @Test
   void dataResetAloneMakesScriptWriteVisible() throws Exception {
      assertEquals(TOP3, states(vbox.getData(CHART)), "cache warm-up");

      vbox.getScope().execute(SCRIPT, TEXT);
      assertEquals(XCondition.TOP_N, rtRanking(),
         "runtime ref is rebuilt only by a data execution, not by the write itself");

      vbox.resetDataMap(CHART);

      assertEquals(BOTTOM3, states(vbox.getData(CHART)), cacheState());
      assertEquals(XCondition.BOTTOM_N, rtRanking(), "runtime ref after the re-query");
   }

   /**
    * Bug #78759: a script in another assembly writes the chart's aggregate formula through a
    * bindingInfo function other than the ranking ones, and reads the data in the same run. The
    * query must be run again for the new formula.
    */
   @Test
   void formulaWriteFromTextScriptIsReflectedInNextGetData() throws Exception {
      assertTrue(headers(vbox.getData(CHART)).contains("Sum(NUM)"), "cache warm-up");

      vbox.getScope().execute(FORMULA_SCRIPT, TEXT);

      List<String> headers = headers(vbox.getData(CHART));
      assertTrue(headers.contains("Max(NUM)"), "headers after setFormula: " + headers);
      assertFalse(headers.contains("Sum(NUM)"), "headers after setFormula: " + headers);
   }

   /**
    * A property assignment on bindingInfo reaches the live info through putMember, not a
    * function call, and must invalidate the same way.
    */
   @Test
   void propertyAssignmentIsReflectedInNextGetData() throws Exception {
      assertTrue(headers(vbox.getData(CHART)).contains(STATE), "cache warm-up");

      vbox.getScope().execute(CHART + ".bindingInfo.xFields = [];", TEXT);

      List<String> headers = headers(vbox.getData(CHART));
      assertFalse(headers.contains(STATE), "headers after xFields = []: " + headers);
   }

   /**
    * A script that writes the value the binding already has, e.g. on every refresh, keeps both
    * the cached data and the cached graph (Bug #76862).
    */
   @Test
   void unchangedWriteKeepsDataAndGraph() throws Exception {
      Object before = vbox.getData(CHART);
      VGraphPair pair = cacheGraph();

      vbox.getScope().execute(
         CHART + ".bindingInfo.setFormula('NUM', 'Sum', Chart.BINDING_FIELD);" +
         CHART + ".bindingInfo.setTopNReverse('" + STATE + "', false, Chart.BINDING_FIELD);", TEXT);

      assertSame(before, vbox.getData(CHART), "data cache");
      assertSame(pair, graphCache().get(CHART), "graph cache");
   }

   /**
    * A changed data binding drops the cached graph together with the data.
    */
   @Test
   void dataWriteDropsCachedGraph() throws Exception {
      vbox.getData(CHART);
      cacheGraph();

      vbox.getScope().execute(FORMULA_SCRIPT, TEXT);

      assertNull(graphCache().get(CHART), "graph cache");
   }

   /**
    * A writer that throws after it changed the binding leaves that change in the info, so the
    * caches must be invalidated anyway.
    */
   @Test
   void throwingWriterAfterPartialWriteStillInvalidates() throws Exception {
      Object before = vbox.getData(CHART);
      VGraphPair pair = cacheGraph();
      ChartVSAScriptable scriptable = (ChartVSAScriptable) vbox.getScope().getVSAScriptable(CHART);
      Method guard = ChartVSAScriptable.class.getDeclaredMethod("guardInfoWrite", Supplier.class);
      guard.setAccessible(true);

      InvocationTargetException ex = assertThrows(InvocationTargetException.class,
         () -> guard.invoke(scriptable, (Supplier<Object>) () -> {
            ((VSAggregateRef) chart.getVSChartInfo().getYFields()[0]).setFormulaValue("Max");
            throw new IllegalStateException("fails after the write");
         }));

      assertInstanceOf(IllegalStateException.class, ex.getCause());
      assertNotSame(before, vbox.getData(CHART), "data cache");
      assertNull(graphCache().get(CHART), "graph cache");
      assertNotSame(pair, graphCache().get(CHART));
   }

   /**
    * Chart.query replaces the chart's source and fields, which is not a bindingInfo write but is
    * as invisible to the caches.
    */
   @Test
   void queryAssignmentDropsCachedData() throws Exception {
      Object before = vbox.getData(CHART);
      cacheGraph();

      vbox.getScope().execute(CHART + ".query = 'T1';", TEXT);

      assertNotSame(before, vbox.getData(CHART), "data cache");
      assertNull(graphCache().get(CHART), "graph cache");
   }

   /**
    * The chart query reads rankPerGroup and sortOthersLast from the runtime descriptor, which
    * the binding snapshot does not hold, so a script write to either drops the cached data and
    * graph.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "rankPerGroup", "sortOthersLast" })
   void descriptorQueryPropertyWriteDropsCachedData(String property) throws Exception {
      Object before = vbox.getData(CHART);
      cacheGraph();
      ChartDescriptor desc = ((ChartVSAScriptable) vbox.getScope().getVSAScriptable(CHART))
         .getRTChartDescriptor();
      assertNotNull(desc, "runtime descriptor");
      boolean current = property.equals("rankPerGroup") ? desc.isRankPerGroup() :
         desc.isSortOthersLast();

      vbox.getScope().execute(CHART + "." + property + " = " + !current + ";", TEXT);

      assertNotSame(before, vbox.getData(CHART), "data cache");
      assertNull(graphCache().get(CHART), "graph cache");
   }

   /**
    * Writing the descriptor property's current value keeps both caches.
    */
   @Test
   void unchangedDescriptorWriteKeepsDataAndGraph() throws Exception {
      Object before = vbox.getData(CHART);
      VGraphPair pair = cacheGraph();
      ChartDescriptor desc = ((ChartVSAScriptable) vbox.getScope().getVSAScriptable(CHART))
         .getRTChartDescriptor();
      assertNotNull(desc, "runtime descriptor");

      vbox.getScope().execute(CHART + ".rankPerGroup = " + desc.isRankPerGroup() + ";", TEXT);

      assertSame(before, vbox.getData(CHART), "data cache");
      assertSame(pair, graphCache().get(CHART), "graph cache");
   }

   /**
    * A getter is not a writer: it runs through the script function without a snapshot, so
    * whatever it touches is left alone, while any other function name is guarded.
    */
   @Test
   void getterCallIsNotGuarded() throws Exception {
      Object before = vbox.getData(CHART);
      ChartVSAScriptable scriptable = (ChartVSAScriptable) vbox.getScope().getVSAScriptable(CHART);
      VSAggregateRef agg = (VSAggregateRef) chart.getVSChartInfo().getYFields()[0];

      scriptable.guardCall("getFormula", () -> {
         agg.setFormulaValue("Max");
         return null;
      });
      assertSame(before, vbox.getData(CHART), "a getter call must not snapshot or reset");

      scriptable.guardCall("setFormula", () -> {
         agg.setFormulaValue("Min");
         return null;
      });
      assertNotSame(before, vbox.getData(CHART), "a writer call is guarded");
   }

   /**
    * Reading through the bindingInfo getters leaves both caches as they were.
    */
   @Test
   void getterScriptKeepsDataAndGraph() throws Exception {
      Object before = vbox.getData(CHART);
      VGraphPair pair = cacheGraph();

      vbox.getScope().execute(
         CHART + ".bindingInfo.getFormula('NUM', Chart.BINDING_FIELD);" +
         CHART + ".bindingInfo.isTopNReverse('" + STATE + "', Chart.BINDING_FIELD);", TEXT);

      assertSame(before, vbox.getData(CHART), "data cache");
      assertSame(pair, graphCache().get(CHART), "graph cache");
   }

   private VGraphPair cacheGraph() throws Exception {
      VGraphPair pair = mock(VGraphPair.class);
      graphCache().put(CHART, pair);
      return pair;
   }

   @SuppressWarnings("unchecked")
   private Map<String, VGraphPair> graphCache() throws Exception {
      Field field = ViewsheetSandbox.class.getDeclaredField("pairs");
      field.setAccessible(true);
      return (Map<String, VGraphPair>) field.get(vbox);
   }

   private static List<String> headers(Object obj) {
      assertInstanceOf(DataSet.class, obj, "chart data");
      DataSet data = (DataSet) obj;
      List<String> headers = new ArrayList<>();

      for(int c = 0; c < data.getColCount(); c++) {
         headers.add(data.getHeader(c));
      }

      return headers;
   }

   private String cacheState() throws Exception {
      Field field = ViewsheetSandbox.class.getDeclaredField("dKeyMap");
      field.setAccessible(true);
      Object key = ((DataMap) field.get(vbox)).get(CHART, DataMap.NORMAL);
      return "dKeyMap entry is the live VSChartInfo=" + (key == chart.getVSChartInfo()) +
         ", design ranking=" + dim.getRankingOptionValue() +
         ", runtime ranking=" + rtRanking();
   }

   private int rtRanking() {
      ChartRef[] refs = chart.getVSChartInfo().getRTXFields();
      return refs.length == 0 ? -1 : ((VSDimensionRef) refs[0]).getRankingOption();
   }

   private void buildViewsheet() throws Exception {
      Worksheet ws = new Worksheet();
      sqlTable(ws);
      Viewsheet vs = new Viewsheet();
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, ws);

      chart = new ChartVSAssembly(vs, CHART);
      chart.setSourceInfo(new SourceInfo(XSourceInfo.ASSET, null, "T1"));
      VSChartInfo info = chart.getVSChartInfo();
      dim = new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, STATE)));
      dim.setGroupColumnValue(STATE);
      dim.setOrder(XConstants.SORT_ASC);
      dim.setRankingOptionValue(XCondition.TOP_N + "");
      dim.setRankingNValue("3");
      dim.setRankingColValue("Sum(NUM)");
      info.addXField(dim);
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("NUM");
      agg.setFormulaValue("Sum");
      info.addYField(agg);
      vs.addAssembly(chart);
      vs.addAssembly(new TextVSAssembly(vs, TEXT));

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/Bug76862",
         null, OrganizationManager.getInstance().getCurrentOrgID());
      vbox = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
      box = new AssetQuerySandbox(ws);
      Field wbox = ViewsheetSandbox.class.getDeclaredField("wbox");
      wbox.setAccessible(true);
      wbox.set(vbox, box);
   }

   private static List<String> states(Object obj) {
      assertInstanceOf(DataSet.class, obj, "chart data");
      DataSet data = (DataSet) obj;
      List<String> states = new ArrayList<>();

      for(int r = 0; r < data.getRowCount(); r++) {
         states.add(String.valueOf(data.getData(STATE, r)));
      }

      return states;
   }

   private static void sqlTable(Worksheet ws) throws Exception {
      String text = "select st.state, st.num from st";
      UniformSQL usql = new UniformSQL();
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class, long.class);
      parse.setAccessible(true);
      parse.invoke(usql, text, 0, 4000L);
      usql.setSQLString(text, false);
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug76862");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      usql.setDataSource(ds);
      JDBCQuery query = new JDBCQuery();
      query.setName("T1");
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(usql);
      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getName(), ds.getName()));
      ColumnSelection columns = new ColumnSelection();

      for(String[] column : new String[][] { { STATE, XSchema.STRING },
                                             { "NUM", XSchema.INTEGER } })
      {
         ColumnRef ref = new ColumnRef(new AttributeRef(column[0]));
         ref.setDataType(column[1]);
         columns.addAttribute(ref);
      }

      table.setColumnSelection(columns, false);
      table.setColumnSelection((ColumnSelection) columns.clone(), true);
      ws.addAssembly(table);
   }

   private static DataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }

   private static final String CHART = "C1";
   private static final String TEXT = "Text1";
   private static final String STATE = "STATE";
   private static final String SCRIPT =
      CHART + ".bindingInfo.setTopNReverse('" + STATE + "', true, Chart.BINDING_FIELD);";
   private static final String FORMULA_SCRIPT =
      CHART + ".bindingInfo.setFormula('NUM', 'Max', Chart.BINDING_FIELD);";
   private static final List<String> TOP3 = List.of("CA", "NY", "TX");
   private static final List<String> BOTTOM3 = List.of("AZ", "CO", "MA");
   private ViewsheetSandbox vbox;
   private AssetQuerySandbox box;
   private ChartVSAssembly chart;
   private VSChartDimensionRef dim;
}
