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
import inetsoft.report.filter.OthersComparator;
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
import org.junit.jupiter.params.provider.CsvSource;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78495: a chart dimension with Top N + "Group all others together" on a database source.
 * SummaryFilter puts the Others bucket last, and the group re-sort after it
 * (AssetQuery.getSortSummaryTableLens) must keep it last for every sort order. Some top values
 * (TX, WA) sort after "Others", so a plain string re-sort moves Others into the middle. An
 * embedded source never reaches that re-sort, so this needs a JDBC source. Same fixture as
 * ConditionValueSwapLostTest.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  ChartTopNOthersSortTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartTopNOthersSortTest {
   private static final String DB = "memory:bug78495";

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
         XDataService dataService = mock(XDataService.class);
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

         stmt.executeUpdate("create table st (region varchar(10), state varchar(10), num int)");
         String[][] rows = { { "W", "AZ", "1" }, { "W", "CA", "51" }, { "E", "CO", "2" },
                             { "E", "MA", "3" }, { "E", "NY", "40" }, { "E", "TX", "30" },
                             { "W", "WA", "20" } };

         for(String[] row : rows) {
            stmt.executeUpdate("insert into st values ('" + row[0] + "', '" + row[1] + "', " +
                               row[2] + ")");
         }
      }
   }

   @BeforeEach
   void setUp() {
      SreeEnv.setProperty(PoolConfig.ENABLED, "false");
      AssetDataCache.getCache().clearCache();
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
      AssetDataCache.getCache().clearCache();
   }

   /**
    * Top 4 by Sum(NUM) is CA, NY, TX, WA. Others is last for every sort order, and the
    * dataset comparator is never a bare OthersComparator (#78469).
    */
   @ParameterizedTest(name = "{0}")
   @CsvSource(delimiter = ';', value = {
      "None;     0; CA,NY,TX,WA,Others",
      "Original; 4; CA,NY,TX,WA,Others",
      "Asc;      1; CA,NY,TX,WA,Others",
      "Desc;     2; WA,TX,NY,CA,Others"
   })
   void othersStaysLast(String label, int order, String expected) throws Exception {
      DataSet data = chartData(order, XCondition.TOP_N, 4, false);
      assertEquals(List.of(expected.split(",")), states(data), label + " rows");
      assertFalse(data.getComparator(STATE) instanceof OthersComparator,
                  label + " dataset comparator " + data.getComparator(STATE));
   }

   /** Bottom 3 by Sum(NUM) is AZ, CO, MA, the rest (CA, NY, TX, WA) goes into Others. */
   @Test
   void bottomNOthersStaysLast() throws Exception {
      DataSet data = chartData(XConstants.SORT_NONE, XCondition.BOTTOM_N, 3, false);
      assertEquals(List.of("AZ", "CO", "MA", "Others"), states(data));
   }

   /**
    * Top 2 per REGION (outer, Sort None): E is NY, TX + Others(MA, CO), W is CA, WA + Others(AZ).
    * Others stays last inside each parent group.
    */
   @ParameterizedTest(name = "nested {0}")
   @CsvSource(delimiter = ';', value = {
      "None; 0; NY,TX,Others,CA,WA,Others",
      "Asc;  1; NY,TX,Others,CA,WA,Others",
      "Desc; 2; TX,NY,Others,WA,CA,Others"
   })
   void nestedOthersStaysLast(String label, int order, String expected) throws Exception {
      DataSet data = chartData(order, XCondition.TOP_N, 2, true);
      assertEquals(List.of(expected.split(",")), states(data), label + " rows");
   }

   private DataSet chartData(int order, int ranking, int n, boolean nested) throws Exception {
      Worksheet ws = new Worksheet();
      sqlTable(ws);
      Viewsheet vs = new Viewsheet();
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, ws);

      ChartVSAssembly chart = new ChartVSAssembly(vs, CHART);
      chart.setSourceInfo(new SourceInfo(XSourceInfo.ASSET, null, "T1"));
      VSChartInfo info = chart.getVSChartInfo();
      VSChartDimensionRef dim =
         new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, STATE)));
      dim.setGroupColumnValue(STATE);
      dim.setOrder(order);
      dim.setRankingOptionValue(ranking + "");
      dim.setRankingNValue(n + "");
      dim.setRankingColValue("Sum(NUM)");
      dim.setGroupOthersValue("true");

      if(nested) {
         VSChartDimensionRef region =
            new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, REGION)));
         region.setGroupColumnValue(REGION);
         region.setOrder(XConstants.SORT_NONE);
         info.addXField(region);
      }

      info.addXField(dim);
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("NUM");
      agg.setFormulaValue("Sum");
      info.addYField(agg);
      vs.addAssembly(chart);

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/Bug78495",
         null, OrganizationManager.getInstance().getCurrentOrgID());
      ViewsheetSandbox vbox = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null,
                                                   false, entry);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      boxes.add(box);
      Field wbox = ViewsheetSandbox.class.getDeclaredField("wbox");
      wbox.setAccessible(true);
      wbox.set(vbox, box);

      Object data = vbox.getData(CHART);
      assertInstanceOf(DataSet.class, data, "chart data");
      return (DataSet) data;
   }

   private static List<String> states(DataSet data) {
      List<String> states = new ArrayList<>();

      for(int r = 0; r < data.getRowCount(); r++) {
         states.add(String.valueOf(data.getData(STATE, r)));
      }

      return states;
   }

   private static void sqlTable(Worksheet ws) throws Exception {
      String text = "select st.region, st.state, st.num from st";
      UniformSQL usql = new UniformSQL();
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class, long.class);
      parse.setAccessible(true);
      parse.invoke(usql, text, 0, 4000L);
      usql.setSQLString(text, false);
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug78495");
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

      for(String[] column : new String[][] { { REGION, XSchema.STRING },
                                             { STATE, XSchema.STRING },
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
   private static final String STATE = "STATE";
   private static final String REGION = "REGION";
   private final List<AssetQuerySandbox> boxes = new ArrayList<>();
}
