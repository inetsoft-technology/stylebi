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

import inetsoft.mv.MVDef;
import inetsoft.mv.MVDispatcher;
import inetsoft.mv.MVManager;
import inetsoft.report.TableLens;
import inetsoft.report.XSessionManager;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.internal.Util;
import inetsoft.report.lens.xnode.XNodeTableLens;
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
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Drivers;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.*;
import inetsoft.util.credential.CredentialService;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77901: a database error raised while the rows of a query result are fetched (here a
 * divide by zero in row 50000 on Derby) stopped loading, and every reader got the rows read
 * before it as if they were the whole result, with no message. The worksheet table, a mirror
 * and a viewsheet table over it now warn with the database error, the failed result is not
 * reused, a scheduled run fails, and an MV is not built from it. Each case runs the real
 * AssetQuerySandbox / AssetDataCache / XSessionManager (data cache on) / JDBCHandler /
 * XNodeTable path; only XEngine.execute is stood in for by JDBCHandler.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  RowFetchFailureTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class RowFetchFailureTest {
   private static final String DB = "memory:rowfetchfailure";
   private static final int ROWS = 60000;
   private static final int FAIL_ROW = 50000;
   private static final String DB_MESSAGE = "Attempt to divide by zero.";
   private static final AtomicInteger RUN = new AtomicInteger();

   @Configuration
   static class JdbcConfig {
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
         return new Plugins(blobStorageManager.getStorage("plugins", true), cluster,
                            eventPublisher);
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

      // the data service stands in for XEngine.execute and runs the query on Derby
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
         XSessionManager manager =
            new XSessionManager(dataService, sessionService, mock(DataSourceRegistry.class));
         // the shared result cache is on, as on a server
         manager.setCacheData(true);
         return manager;
      }
   }

   @BeforeAll
   static void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table big");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table big (id int, g int)");
         conn.setAutoCommit(false);

         try(java.sql.PreparedStatement insert =
                conn.prepareStatement("insert into big values (?, ?)"))
         {
            for(int i = 1; i <= ROWS; i++) {
               insert.setInt(1, i);
               insert.setInt(2, i % 10);
               insert.addBatch();

               if(i % 5000 == 0) {
                  insert.executeBatch();
               }
            }
         }

         conn.commit();
      }
   }

   @BeforeEach
   void streaming() {
      SreeEnv.setProperty("replet.streaming", "true");
      CoreTool.clearUserMessage();
   }

   @AfterEach
   void reset() {
      SreeEnv.remove("replet.streaming");
      CoreTool.clearUserMessage();
   }

   @Test
   void worksheetTableWarnsAndIsNotReused() throws Exception {
      Worksheet ws = new Worksheet();
      sqlTable(ws, "T1", true);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      TableLens lens = box.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE, new VariableTable());
      assertEquals(FAIL_ROW - 1, dataRows(lens));
      assertWarned();

      // the failed result is run again, not taken from the sandbox
      TableLens again = box.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE, new VariableTable());
      assertNotSame(lens, again);
      dataRows(again);
      assertWarned();
   }

   @Test
   void mirrorWarns() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", true);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M1", t1);
      ws.addAssembly(mirror);
      mirror.update();
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      TableLens lens = box.getTableLens("M1", AssetQuerySandbox.RUNTIME_MODE, new VariableTable());
      assertEquals(FAIL_ROW - 1, dataRows(lens));
      assertWarned();
   }

   @Test
   void viewsheetTableWarns() throws Exception {
      Worksheet ws = new Worksheet();
      sqlTable(ws, "T1", true);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      Object data = vsTable(ws, box).getData("TableV");
      TableLens lens = data instanceof VSDataSet ? ((VSDataSet) data).getTable() : (TableLens) data;

      assertEquals(FAIL_ROW - 1, dataRows(lens));
      assertWarned();
   }

   @Test
   void successfulQueryIsUnchanged() throws Exception {
      Worksheet ws = new Worksheet();
      sqlTable(ws, "T1", false);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      TableLens lens = box.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE, new VariableTable());
      assertEquals(ROWS, dataRows(lens));
      assertNull(CoreTool.getUserMessage());
      assertSame(lens, box.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE, new VariableTable()));
   }

   @Test
   void userCancelStaysSilent() throws Exception {
      Worksheet ws = new Worksheet();
      sqlTable(ws, "T1", false);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      TableLens lens = box.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE, new VariableTable());
      XNodeTableLens xlens = (XNodeTableLens) Util.getNestedTable(lens, XNodeTableLens.class);
      xlens.cancel();
      dataRows(lens);

      assertNull(xlens.getLoadException());
      assertNull(CoreTool.getUserMessage());
   }

   @Test
   void nonStreamingReportsTheDatabaseError() throws Exception {
      SreeEnv.setProperty("replet.streaming", "false");
      Worksheet ws = new Worksheet();
      sqlTable(ws, "T1", true);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      TableLens lens = box.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE, new VariableTable());
      UserMessage message = CoreTool.getUserMessage();

      assertNull(lens);
      assertNotNull(message);
      assertTrue(message.getMessage().contains(DB_MESSAGE), message.getMessage());
      assertFalse(message.getMessage().contains(
         Catalog.getCatalog().getString("common.table.queryCancelled")), message.getMessage());
   }

   @Test
   void scheduledRunFails() throws Exception {
      Worksheet ws = new Worksheet();
      sqlTable(ws, "T1", true);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      VariableTable vars = new VariableTable();
      vars.put("__is_scheduler__", Boolean.TRUE);

      RuntimeException ex = assertThrows(RuntimeException.class, () -> dataRows(
         box.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE, vars)));
      assertTrue(String.valueOf(ex.getMessage()).contains(DB_MESSAGE), String.valueOf(ex));
   }

   @Test
   void mvIsNotBuiltFromAFailedRead() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", true);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      // as MVDispatcher.getData reads the MV data
      XTable data = AssetDataCache.getCache().getData(
         null, t1, box, null, AssetQuerySandbox.RUNTIME_MODE, false,
         System.currentTimeMillis(), box.getQueryManager());
      data.moreRows(XTable.EOT);

      MVDef def = mock(MVDef.class);
      when(def.getName()).thenReturn("mv77901");
      MVDispatcher dispatcher = new MVDispatcher(def);
      Field field = MVDispatcher.class.getDeclaredField("data");
      field.setAccessible(true);
      field.set(dispatcher, data);

      assertTrue(dispatcher.isCanceled());
   }

   private static int dataRows(TableLens lens) {
      assertNotNull(lens, "the query failed, see the log");
      lens.moreRows(XTable.EOT);
      return lens.getRowCount() - lens.getHeaderRowCount();
   }

   private static void assertWarned() {
      UserMessage message = CoreTool.getUserMessage();
      assertNotNull(message, "no message for the failed fetch");
      assertEquals(Catalog.getCatalog().getString("common.table.getDataFailed") + ": " +
                   DB_MESSAGE, message.getMessage());
   }

   // a distinct where clause per table, so no case reads a cached result of another
   private static SQLBoundTableAssembly sqlTable(Worksheet ws, String name, boolean fail)
      throws Exception
   {
      String text = "select big.id, big.g, " + (fail ? "1/(big.id-" + FAIL_ROW + ")" : "big.g") +
         " as x from big where big.id > -" + RUN.incrementAndGet();
      UniformSQL usql = new UniformSQL();
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class,
                                                        long.class);
      parse.setAccessible(true);
      // parse(String, int, long) is package private, PARSE_ALL is 0
      parse.invoke(usql, text, 0, 4000L);
      usql.setSQLString(text, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), text);

      JDBCDataSource ds = dataSource();
      usql.setDataSource(ds);
      JDBCQuery query = new JDBCQuery();
      query.setName(name);
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(usql);

      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, name);
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getName(), ds.getName()));
      table.setSQLEdited(true);

      ColumnSelection columns = new ColumnSelection();

      for(String column : new String[] { "id", "g", "x" }) {
         ColumnRef ref = new ColumnRef(new AttributeRef(column));
         ref.setDataType(XSchema.INTEGER);
         columns.addAttribute(ref);
      }

      table.setColumnSelection(columns, false);
      table.setColumnSelection((ColumnSelection) columns.clone(), true);
      ws.addAssembly(table);
      return table;
   }

   private static ViewsheetSandbox vsTable(Worksheet ws, AssetQuerySandbox box)
      throws Exception
   {
      Viewsheet vs = new Viewsheet();
      // as PooledWorksheetOpenReadAheadTest: the base worksheet is wired directly
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, ws);
      TableVSAssembly table = new TableVSAssembly(vs, "TableV");
      table.setSourceInfo(new SourceInfo(XSourceInfo.ASSET, null, "T1"));
      ColumnSelection cols = new ColumnSelection();

      for(String name : new String[] { "id", "g", "x" }) {
         cols.addAttribute(new ColumnRef(new AttributeRef(null, name)));
      }

      table.setColumnSelection(cols);
      vs.addAssembly(table);
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/RowFetchFailureTest",
         null, OrganizationManager.getInstance().getCurrentOrgID());
      ViewsheetSandbox vbox = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null,
                                                   false, entry);
      Field wbox = ViewsheetSandbox.class.getDeclaredField("wbox");
      wbox.setAccessible(true);
      wbox.set(vbox, box);
      return vbox;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("rowfetchfailure");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
   }

   private static DataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }
}
