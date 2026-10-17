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
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.IdentityID;
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
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.CoreTool;
import inetsoft.util.Plugins;
import inetsoft.util.ThreadContext;
import inetsoft.util.UserMessage;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.*;
import java.security.Principal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #76862: a script's ranking write resets the chart's cached data, so a read in the same
 * script already sees it, while a chart whose ranking did not change keeps hitting the cache
 * (no extra query). Same JDBC fixture as {@link ChartScriptWriteDataCacheTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  ChartRankingScriptWriteTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartRankingScriptWriteTest {
   private static final String DB = "memory:bug76862snapshot";

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

   @Test
   void unchangedChartStaysACacheHit() throws Exception {
      Object before = vbox.getData(CHART);
      assertEquals(TOP3, states(before), "cache warm-up");

      assertSame(before, vbox.getData(CHART), "repeated read, " + cacheState());

      vbox.getScope().execute("var x = 1;", TEXT);
      assertSame(before, vbox.getData(CHART), "after a script not touching the chart");

      // the runtime refs are rebuilt from the unchanged design refs
      vbox.updateAssembly(chart);
      assertSame(before, vbox.getData(CHART), "after updateAssembly, " + cacheState());
   }

   @Test
   void writingTheCurrentValueStaysACacheHit() throws Exception {
      Object before = vbox.getData(CHART);

      vbox.getScope().execute(
         CHART + ".bindingInfo.setTopNReverse('" + STATE + "', false);", CHART);
      assertSame(before, vbox.getData(CHART), cacheState());
   }

   @Test
   void changedTopNIsReflectedInNextGetData() throws Exception {
      Object before = vbox.getData(CHART);

      vbox.getScope().execute(CHART + ".bindingInfo.setTopN('" + STATE + "', 2);", TEXT);

      Object after = vbox.getData(CHART);
      assertNotSame(before, after, cacheState());
      assertEquals(List.of("CA", "NY"), states(after));
   }

   @Test
   void graphAfterAnotherAssemblysWriteShowsTheWrittenRanking() throws Exception {
      Principal old = ThreadContext.getPrincipal();
      ThreadContext.setPrincipal(SUtil.getPrincipal(
         new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()),
         null, false));

      try {
         assertEquals(TOP3, graphStates(), "graph warm-up");

         vbox.getScope().execute(
            CHART + ".bindingInfo.setTopNReverse('" + STATE + "', true);", TEXT);

         assertEquals(BOTTOM3, graphStates(), cacheState());
      }
      finally {
         ThreadContext.setPrincipal(old);
      }
   }

   /**
    * A BINDING_FIELD ranking write in a chart's own script, on a field the chart has on its
    * group shelf, throws. On the graph build the first script pass ignores errors, so the
    * second pass must report it to the user. The statements after it do not run.
    */
   @Test
   void groupShelfBindingFieldWriteInChartScriptIsReportedOnTheGraphBuild() throws Exception {
      VSChartInfo info = chart.getVSChartInfo();
      info.removeXFields();
      info.addGroupField(dim);
      ChartVSAssemblyInfo ainfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      ainfo.setScriptEnabled(true);
      ainfo.setScript("bindingInfo.setTopN('" + STATE + "', 2, Chart.BINDING_FIELD);\n" +
                      "bindingInfo.setTopNReverse('" + STATE + "', true);");
      Principal old = ThreadContext.getPrincipal();
      ThreadContext.setPrincipal(SUtil.getPrincipal(
         new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()),
         null, false));

      try {
         CoreTool.clearUserMessage();
         VGraphPair pair = vbox.getVGraphPair(CHART, true, null);
         assertNotNull(pair, "the chart still renders");

         UserMessage msg = CoreTool.getUserMessage();
         assertNotNull(msg, "the script error is reported to the user");
         assertTrue(msg.getMessage().contains(STATE + " is not on the x or y shelf"),
                    msg.getMessage());
         assertTrue(msg.getMessage().contains("omit the type (or pass 0)"), msg.getMessage());
         assertEquals("3", dim.getRankingNValue(), "the throwing write changed nothing");
         assertEquals("" + XCondition.TOP_N, dim.getRankingOptionValue(),
                      "the statement after the throwing one did not run");
      }
      finally {
         ThreadContext.setPrincipal(old);
         CoreTool.clearUserMessage();
      }
   }

   /**
    * The graph-build report is only for the bindingInfo fail-loud error: any other error of
    * the chart's own script keeps its old behaviour there. On the first build that is no
    * report (the reporting second script run is skipped); a later build that runs it, e.g.
    * after a resize, reports it as it always did.
    */
   @Test
   void unrelatedChartScriptErrorIsNotNewlyReportedOnTheFirstGraphBuild() throws Exception {
      ChartVSAssemblyInfo ainfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      ainfo.setScriptEnabled(true);
      ainfo.setScript("noSuchFunction76862();");
      Principal old = ThreadContext.getPrincipal();
      ThreadContext.setPrincipal(SUtil.getPrincipal(
         new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()),
         null, false));

      try {
         CoreTool.clearUserMessage();
         assertNotNull(vbox.getVGraphPair(CHART, true, null));
         assertNull(CoreTool.getUserMessage(), "first build");
      }
      finally {
         ThreadContext.setPrincipal(old);
         CoreTool.clearUserMessage();
      }
   }

   private List<String> graphStates() throws Exception {
      VGraphPair pair = vbox.getVGraphPair(CHART, true, null);
      assertNotNull(pair, "graph");
      return states(pair.getData());
   }

   @Test
   void sameScriptReadsTheWrittenBinding() throws Exception {
      assertEquals(TOP3, states(vbox.getData(CHART)), "cache warm-up");

      Object read = vbox.getScope().execute(
         CHART + ".bindingInfo.setTopNReverse('" + STATE + "', true);" +
         "var d = " + CHART + ".data; [d[1][0], d[2][0], d[3][0]].join(',');", TEXT);

      assertEquals(String.join(",", BOTTOM3), String.valueOf(read), cacheState());
   }

   private Object dataKey() throws Exception {
      Field field = ViewsheetSandbox.class.getDeclaredField("dKeyMap");
      field.setAccessible(true);
      return ((DataMap) field.get(vbox)).get(CHART, DataMap.NORMAL);
   }

   private String cacheState() throws Exception {
      Object key = dataKey();
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
      // an explicit type: the auto type needs a chart-type permission lookup
      info.setChartType(GraphTypes.CHART_BAR);
      vs.addAssembly(chart);
      vs.addAssembly(new TextVSAssembly(vs, TEXT));

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/Bug76862Snapshot",
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
      ds.setName("bug76862snapshot");
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
   private static final List<String> TOP3 = List.of("CA", "NY", "TX");
   private static final List<String> BOTTOM3 = List.of("AZ", "CO", "MA");
   private ViewsheetSandbox vbox;
   private AssetQuerySandbox box;
   private ChartVSAssembly chart;
   private VSChartDimensionRef dim;
}
