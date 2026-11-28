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
package inetsoft.web.composer.ws;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.mv.MVManager;
import inetsoft.report.TableLens;
import inetsoft.report.XSessionManager;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.DistributedTableCacheStore;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.XNodeMetaTable;
import inetsoft.sree.internal.cluster.Cluster;
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
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSAssemblyEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.*;
import java.security.Principal;
import java.sql.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82529. Stop query on a worksheet table whose statement is still executing (a slow
 * first row, e.g. PostgreSQL {@code pg_sleep}) must cancel the statement at once, and must not
 * run the stopped query again: not in the refresh that stop sends, not in the request that ran
 * the query, and not in a later read of the table, until the table is run again.
 * The statement runs through the real sandbox, AssetQuery, XSessionManager and JDBCHandler on
 * embedded Derby. Derby can't block in execute or fail a cancelled statement, so a proxy does
 * what PostgreSQL does: execute blocks until the statement is cancelled and then fails with
 * SQLState 57014.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  WSQueryServiceStopTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class WSQueryServiceStopTest {
   private static final String DB = "memory:bug82529stop";
   // the sql of the table, a statement with it blocks while BLOCK is on
   private static final String MARK = "82529 = 82529";
   // a blocked statement that is never cancelled runs after this
   private static final long BLOCK_SECONDS = 20;

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
         DataSource ds = blocking(derby());
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
         manager.setCacheData(true);
         return manager;
      }
   }

   @BeforeAll
   static void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table pa");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table pa (id int, g int)");
         stmt.executeUpdate("insert into pa values (1, 1), (2, 0), (3, 1)");
      }
   }

   @BeforeEach
   void reset() {
      BLOCK.set(true);
      EXECUTED.set(0);
      ENTERED.set(new CountDownLatch(1));
   }

   @AfterEach
   void release() {
      BLOCK.set(false);
   }

   // Show Live Data of a slow query, then Stop
   @Test
   void stopCancelsAnExecutingLiveQueryAndDoesNotRunItAgain() throws Exception {
      assertStopCancelsExecutingQuery(false);
   }

   // Run Query (runtime mode) of a slow query, then Stop
   @Test
   void stopCancelsAnExecutingRuntimeQueryAndDoesNotRunItAgain() throws Exception {
      assertStopCancelsExecutingQuery(true);
   }

   private void assertStopCancelsExecutingQuery(boolean runtime) throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = slowTable(ws, "S1");
      table.setRuntime(runtime);
      int mode = WorksheetEventUtil.getMode(table);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      box.setQueryManager(new QueryManager());

      try {
         startAndStop(box, ws, "S1", mode);

         // a later read of the table (the client loads its data) doesn't run it again
         box.getTableLens("S1", mode);
         box.getColumnInfos("S1", mode);
         assertEquals(1, EXECUTED.get(), "the stopped query must not run again");

         // Run Query resets the table and runs it again
         BLOCK.set(false);
         box.resetTableLens("S1", mode);
         assertRunsAgain(box, "S1", mode);
      }
      finally {
         BLOCK.set(false);
         box.dispose();
      }
   }

   // after a stop, switching the table to meta view and back to live data (or any other mode
   // change of the table) asks for its data again and runs the query
   @Test
   void modeChangeAfterStopRunsTheQueryAgain() throws Exception {
      Worksheet ws = new Worksheet();
      slowTable(ws, "S3");
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      box.setQueryManager(new QueryManager());

      try {
         startAndStop(box, ws, "S3", AssetQuerySandbox.LIVE_MODE);
         BLOCK.set(false);

         TableModeService service = new TableModeService(
            engine(box, ws), mock(AssetDataCache.class), mock(DataSourceRegistry.class));
         withRefresh(box, ws, "S3", () -> {
            service.setDefaultMode("ws1", event("S3"), USER, mock(CommandDispatcher.class));
            service.setLiveMode("ws1", event("S3"), USER, mock(CommandDispatcher.class));
         });

         assertEquals(2, EXECUTED.get(), "show live data after the stop runs the query again");
         assertRunsAgain(box, "S3", AssetQuerySandbox.LIVE_MODE);
      }
      finally {
         box.dispose();
      }
   }

   // the stop of a runtime table T applies to reading T only: a mirror of T runs T's query and
   // gets its rows
   @Test
   void stopInRuntimeModeDoesNotEmptyATableBuiltOnTheStoppedTable() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = slowTable(ws, "S4");
      table.setRuntime(true);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M4", table);
      ws.addAssembly(mirror);
      mirror.update();
      mirror.setLiveData(true);
      mirror.setRuntime(true);
      int mode = AssetQuerySandbox.RUNTIME_MODE;
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      box.setQueryManager(new QueryManager());

      try {
         startAndStop(box, ws, "S4", mode);
         box.getTableLens("S4", mode);
         assertEquals(1, EXECUTED.get(), "the stopped table must not run again");

         BLOCK.set(false);
         TableLens lens = box.getTableLens("M4", mode);
         assertNotNull(lens);
         lens.moreRows(Integer.MAX_VALUE);
         assertEquals(4, lens.getRowCount(), "the mirror has the rows of the table");
         assertEquals(2, EXECUTED.get(), "the mirror ran the query of the table");
      }
      finally {
         box.dispose();
      }
   }

   // stop of a table whose result is already there (rows loading) cancels that table only, as
   // before: not the other tables of the worksheet that are loading, and it reads nothing
   @Test
   void stopOfALoadedTableCancelsOnlyItsData() throws Exception {
      Worksheet ws = new Worksheet();
      slowTable(ws, "S2");
      int mode = AssetQuerySandbox.LIVE_MODE;
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      CloseWorksheetCancelTest.Cancellable loading = new CloseWorksheetCancelTest.Cancellable();
      CloseWorksheetCancelTest.Cancellable other = new CloseWorksheetCancelTest.Cancellable();
      QueryManager qmgr = new QueryManager();
      qmgr.addPending(other);
      when(box.getCachedTableLens("S2", mode)).thenReturn(loading);
      when(box.getQueryManager()).thenReturn(qmgr);
      // only the refresh after the cancel reads the table
      AtomicInteger cancelsAtRead = new AtomicInteger(-1);
      when(box.getTableLens(anyString(), anyInt())).thenAnswer(inv -> {
         cancelsAtRead.set(loading.cancels.get());
         return loading;
      });

      stopQuery(box, ws, "S2");

      assertEquals(1, loading.cancels.get(), "the data of the table is cancelled");
      assertEquals(0, other.cancels.get(), "another loading table is not cancelled");
      assertEquals(1, cancelsAtRead.get(), "the table is read only after it was cancelled");
      verify(box, never()).getTableLens(anyString(), anyInt(), any());
      verify(box, never()).setQueryStopped(anyString(), anyInt());
   }

   // runs the slow query of the table on another thread, stops it while it executes and waits
   // for the run to end
   private static void startAndStop(AssetQuerySandbox box, Worksheet ws, String name, int mode)
      throws Exception
   {
      ExecutorService pool = Executors.newSingleThreadExecutor();

      try {
         // the request that shows the table runs its query
         Future<TableLens> run = pool.submit(() -> box.getTableLens(name, mode));
         assertTrue(ENTERED.get().await(30, TimeUnit.SECONDS), "the query never executed");

         long start = System.nanoTime();
         stopQuery(box, ws, name);
         long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

         assertTrue(millis < BLOCK_SECONDS * 1000 / 2,
                    "stop waited " + millis + " ms for the executing query instead of " +
                    "cancelling it");
         // the request that ran the query ends with it, without the rows of the query
         TableLens lens = run.get(10, TimeUnit.SECONDS);
         assertTrue(lens == null || Util.getNestedTable(lens, XNodeMetaTable.class) != null,
                    "the stopped query gives no data");
         assertEquals(1, EXECUTED.get(), "the stopped query must not run again");
      }
      finally {
         BLOCK.set(false);
         pool.shutdownNow();
      }
   }

   // the table gives its three rows, from a new run of its query
   private static void assertRunsAgain(AssetQuerySandbox box, String name, int mode)
      throws Exception
   {
      TableLens lens = box.getTableLens(name, mode);
      assertNotNull(lens);
      lens.moreRows(Integer.MAX_VALUE);
      assertEquals(4, lens.getRowCount(), "header plus three rows");
      assertEquals(2, EXECUTED.get(), "the table ran again once");
   }

   /**
    * WSQueryService.stopQuery. The refresh it sends reads the table the way the table model
    * does (TableAssemblyModelFactory: the lens, then the column infos).
    */
   private static void stopQuery(AssetQuerySandbox box, Worksheet ws, String name)
      throws Exception
   {
      WSQueryService service = new WSQueryService(
         engine(box, ws), mock(AssetDataCache.class), mock(DataSourceRegistry.class));
      withRefresh(box, ws, name, () -> service.stopQuery(
         "ws1", event(name), USER, mock(CommandDispatcher.class)));
   }

   // runs the request with refreshAssembly reading the table like the table model, no layout
   private static void withRefresh(AssetQuerySandbox box, Worksheet ws, String name,
                                   Request request)
      throws Exception
   {
      try(MockedStatic<WorksheetEventUtil> util =
             mockStatic(WorksheetEventUtil.class, CALLS_REAL_METHODS))
      {
         util.when(() -> WorksheetEventUtil.refreshAssembly(
            any(), anyString(), anyBoolean(), any(), any())).thenAnswer(inv -> {
               TableAssembly table = (TableAssembly) ws.getAssembly(name);
               int mode = WorksheetEventUtil.getMode(table);
               box.getTableLens(name, mode);
               box.getColumnInfos(name, mode);
               return null;
            });
         util.when(() -> WorksheetEventUtil.layout(any(), any())).thenAnswer(inv -> null);

         request.run();
      }
   }

   private static ViewsheetService engine(AssetQuerySandbox box, Worksheet ws)
      throws Exception
   {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      ViewsheetService engine = mock(ViewsheetService.class);
      when(engine.getWorksheet("ws1", USER)).thenReturn(rws);
      return engine;
   }

   private static WSAssemblyEvent event(String name) {
      WSAssemblyEvent event = mock(WSAssemblyEvent.class);
      when(event.getAssemblyName()).thenReturn(name);
      return event;
   }

   // a live table whose query blocks in execute while BLOCK is on
   private static SQLBoundTableAssembly slowTable(Worksheet ws, String name) throws Exception {
      SQLBoundTableAssembly table = sqlTable(ws, name, "select pa.id, pa.g from pa where " +
         MARK + " and pa.id > -" + RUN.incrementAndGet(), "id", "g");
      table.setLiveData(true);
      return table;
   }

   private interface Request {
      void run() throws Exception;
   }

   private static SQLBoundTableAssembly sqlTable(Worksheet ws, String name, String text,
                                                 String... columns)
      throws Exception
   {
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
      table.setSQLMergeable(false);

      ColumnSelection selection = new ColumnSelection();

      for(String column : columns) {
         ColumnRef ref = new ColumnRef(new AttributeRef(column));
         ref.setDataType(XSchema.INTEGER);
         selection.addAttribute(ref);
      }

      table.setColumnSelection(selection, false);
      table.setColumnSelection((ColumnSelection) selection.clone(), true);
      ws.addAssembly(table);
      return table;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug82529stop");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
   }

   private static EmbeddedDataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }

   // a data source whose statements of the table's sql block in execute until cancelled
   private static DataSource blocking(DataSource ds) {
      return proxy(DataSource.class, ds, (method, args, target) -> {
         Object result = invoke(method, target, args);
         return result instanceof Connection ? connection((Connection) result) : result;
      });
   }

   private static Connection connection(Connection conn) {
      return proxy(Connection.class, conn, (method, args, target) -> {
         Object result = invoke(method, target, args);

         if(result instanceof PreparedStatement) {
            return statement(PreparedStatement.class, (PreparedStatement) result,
                             (String) args[0]);
         }

         if(result instanceof Statement) {
            return statement(Statement.class, (Statement) result, null);
         }

         return result;
      });
   }

   private static <T extends Statement> T statement(Class<T> type, T stmt, String prepared) {
      CountDownLatch cancel = new CountDownLatch(1);

      return proxy(type, stmt, (method, args, target) -> {
         String name = method.getName();

         if("cancel".equals(name)) {
            cancel.countDown();
            return null;
         }

         if(name.startsWith("execute")) {
            String sql = args != null && args.length > 0 && args[0] instanceof String ?
               (String) args[0] : prepared;

            if(sql != null && sql.contains(MARK)) {
               EXECUTED.incrementAndGet();

               if(BLOCK.get()) {
                  ENTERED.get().countDown();

                  if(cancel.await(BLOCK_SECONDS, TimeUnit.SECONDS)) {
                     throw new SQLException("canceling statement due to user request",
                                            "57014");
                  }
               }
            }
         }

         return invoke(method, target, args);
      });
   }

   private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
      try {
         return method.invoke(target, args);
      }
      catch(InvocationTargetException e) {
         throw e.getCause();
      }
   }

   @SuppressWarnings("unchecked")
   private static <T> T proxy(Class<T> type, T target, Handler handler) {
      return (T) Proxy.newProxyInstance(
         WSQueryServiceStopTest.class.getClassLoader(), new Class<?>[] { type },
         (p, method, args) -> handler.handle(method, args, target));
   }

   private interface Handler {
      Object handle(Method method, Object[] args, Object target) throws Throwable;
   }

   private static final Principal USER = mock(Principal.class);
   private static final AtomicBoolean BLOCK = new AtomicBoolean();
   private static final AtomicInteger EXECUTED = new AtomicInteger();
   private static final AtomicInteger RUN = new AtomicInteger();
   private static final AtomicReference<CountDownLatch> ENTERED = new AtomicReference<>();
}
