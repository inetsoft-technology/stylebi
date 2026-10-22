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
package inetsoft.web.wiz.script;

import inetsoft.mv.MVManager;
import inetsoft.report.XSessionManager;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.DistributedTableCacheStore;
import inetsoft.report.composition.execution.ViewsheetSandbox;
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
import inetsoft.web.wiz.script.model.ScriptExecResult;
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
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79051: a script run adds an undo checkpoint only when it changed the viewsheet. Runs the
 * real {@link ScriptExecuteService} against a real {@link RuntimeViewsheet} holding a chart over
 * a JDBC table (same fixture as ScriptExecuteServiceChangeDetectionTest), so the data-read case exercises
 * the real chart query.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  ScriptExecuteServiceChangeDetectionTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScriptExecuteServiceChangeDetectionTest {
   private static final String DB = "memory:bug79051";

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

   /** Reading C1.dataset runs the real chart query. */
   @Test
   void chartDataReadAddsNoCheckpoint() throws Exception {
      warmChart();
      int size = rvs.size();
      int revision = rvs.getWriteRevision();

      ScriptExecResult result = run(DATA_READ);

      assertTrue(result.ok(), String.valueOf(result));
      assertEquals(size, rvs.size());
      assertEquals(size - 1, rvs.getCurrent());
      assertEquals(revision, rvs.getWriteRevision());
   }

   @Test
   void propertyReadAddsNoCheckpoint() throws Exception {
      int size = rvs.size();
      int revision = rvs.getWriteRevision();

      run("var t = C1.visible;");

      assertEquals(size, rvs.size());
      assertEquals(revision, rvs.getWriteRevision());
   }

   @Test
   void propertyWriteAddsACheckpoint() throws Exception {
      int size = rvs.size();
      int revision = rvs.getWriteRevision();

      ScriptExecResult result = run("C1.visible = false;");

      assertTrue(result.ok(), String.valueOf(result));
      assertEquals(size + 1, rvs.size());
      assertEquals(size, rvs.getCurrent());
      assertEquals(revision + 1, rvs.getWriteRevision());
      assertNotEquals(rvs.getSavePoint(), rvs.getCurrent());
   }

   @Test
   void aWriteThatThrowsAfterChangingTheViewsheetStillAddsACheckpoint() throws Exception {
      int size = rvs.size();
      int revision = rvs.getWriteRevision();

      run("C1.visible = false; throw new Error('after the write');");

      assertEquals(size + 1, rvs.size());
      assertEquals(revision + 1, rvs.getWriteRevision());
   }

   /** A read after a remove must not add an undo step. */
   @Test
   void undoAfterARemoveAndAReadRestoresTheAssemblyAtTheFirstUndo() throws Exception {
      warmChart();
      remove(TEXT);

      run(DATA_READ);

      assertTrue(rvs.undo(null));
      assertNotNull(rvs.getViewsheet().getAssembly(TEXT));
   }

   /** A read between undos must neither truncate the redo tail nor stop the undo moving. */
   @Test
   void aReadBetweenUndosKeepsTheRedoTailAndTheUndoMoving() throws Exception {
      warmChart();
      remove(TEXT);
      remove(TEXT2);

      assertTrue(rvs.undo(null));
      run(DATA_READ);
      assertTrue(rvs.undo(null), "the read must not make the undo stick");
      assertNotNull(rvs.getViewsheet().getAssembly(TEXT));
      assertTrue(rvs.redo(null), "the read must not truncate the redo tail");
   }

   /** The first data fetch fills in chart metadata, so it is the one read that can add a checkpoint. */
   private void warmChart() throws Exception {
      assertTrue(run(DATA_READ).ok());
   }

   private void install(String script) {
      rvs.getViewsheet().getViewsheetInfo().setOnInit(script);
      rvs.getViewsheet().getViewsheetInfo().setScriptEnabled(true);
   }

   private ScriptExecResult run(String script) throws Exception {
      install(script);
      return new ScriptExecuteService(new ScriptReadService())
         .runLive(rvs, ScriptTarget.parse("vs-init"), true);
   }

   private void remove(String name) {
      rvs.getViewsheet().removeAssembly(name);
      rvs.addCheckpoint(rvs.getViewsheet().prepareCheckpoint());
   }

   private void buildViewsheet() throws Exception {
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
      dim.setOrder(XConstants.SORT_ASC);
      info.addXField(dim);
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("NUM");
      agg.setFormulaValue("Sum");
      info.addYField(agg);
      vs.addAssembly(chart);
      vs.addAssembly(new TextVSAssembly(vs, TEXT));
      vs.addAssembly(new TextVSAssembly(vs, TEXT2));

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/Bug79051",
         null, OrganizationManager.getInstance().getCurrentOrgID());
      rvs = new RuntimeViewsheet(entry, vs, null, mock(AssetRepository.class),
                                 mock(WorksheetService.class), null, false);
      ViewsheetSandbox vbox = rvs.getViewsheetSandbox().orElseThrow();
      Field wbox = ViewsheetSandbox.class.getDeclaredField("wbox");
      wbox.setAccessible(true);
      box = new AssetQuerySandbox(ws);
      wbox.set(vbox, box);
   }

   private static void sqlTable(Worksheet ws) throws Exception {
      String text = "select st.state, st.num from st";
      UniformSQL usql = new UniformSQL();
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class, long.class);
      parse.setAccessible(true);
      parse.invoke(usql, text, 0, 4000L);
      usql.setSQLString(text, false);
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug79051");
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

   private static final String DATA_READ = "var d = C1.dataset; var n = d.getRowCount();";
   private static final String CHART = "C1";
   private static final String TEXT = "Text1";
   private static final String TEXT2 = "Text2";
   private static final String STATE = "STATE";
   private RuntimeViewsheet rvs;
   private AssetQuerySandbox box;
}
