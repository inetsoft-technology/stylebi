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
      SQLBoundTableAssembly table = sqlTable(ws, "S1", "select pa.id, pa.g from pa where " +
         MARK + " and pa.id > -" + RUN.incrementAndGet(), "id", "g");
      table.setLiveData(true);
      table.setRuntime(runtime);
      int mode = WorksheetEventUtil.getMode(table);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      box.setQueryManager(new QueryManager());
      ExecutorService pool = Executors.newSingleThreadExecutor();

      try {
         // the request that shows the table runs its query
         Future<TableLens> run = pool.submit(() -> box.getTableLens("S1", mode));
         assertTrue(ENTERED.get().await(30, TimeUnit.SECONDS), "the query never executed");

         long start = System.nanoTime();
         stopQuery(box, ws, "S1");
         long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

         assertTrue(millis < BLOCK_SECONDS * 1000 / 2,
                    "stop waited " + millis + " ms for the executing query instead of " +
                    "cancelling it");
         // the request that ran the query ends with it, without the rows of the query
         TableLens lens = run.get(10, TimeUnit.SECONDS);
         assertTrue(lens == null || Util.getNestedTable(lens, XNodeMetaTable.class) != null,
                    "the stopped query gives no data");

         // a later read of the table (the client loads its data) doesn't run it again
         box.getTableLens("S1", mode);
         box.getColumnInfos("S1", mode);
         assertEquals(1, EXECUTED.get(), "the stopped query must not run again");

         // Run Query resets the table and runs it again
         BLOCK.set(false);
         box.resetTableLens("S1", mode);
         lens = box.getTableLens("S1", mode);
         assertEquals(2, EXECUTED.get(), "the table runs again after it was reset");
         lens.moreRows(Integer.MAX_VALUE);
         assertEquals(4, lens.getRowCount(), "header plus three rows");
      }
      finally {
         BLOCK.set(false);
         pool.shutdownNow();
         box.dispose();
      }
   }

   // stop of a table whose result is already there (rows loading) cancels it as before and
   // doesn't run the query again
   @Test
   void stopOfALoadedTableCancelsItWithoutRunningItAgain() throws Exception {
      BLOCK.set(false);
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = sqlTable(ws, "S2", "select pa.id, pa.g from pa where " +
         MARK + " and pa.id > -" + RUN.incrementAndGet(), "id", "g");
      table.setLiveData(true);
      int mode = WorksheetEventUtil.getMode(table);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      box.setQueryManager(new QueryManager());

      try {
         TableLens lens = box.getTableLens("S2", mode);
         assertNotNull(lens);
         assertSame(lens, box.getCachedTableLens("S2", mode));
         assertEquals(1, EXECUTED.get());

         stopQuery(box, ws, "S2");

         assertFalse(box.isQueryStopped("S2", mode), "a loaded table is not marked stopped");
         assertSame(lens, box.getTableLens("S2", mode), "the loaded table is kept");
         assertEquals(1, EXECUTED.get(), "the query must not run again");
      }
      finally {
         box.dispose();
      }
   }

   /**
    * WSQueryService.stopQuery. The refresh it sends reads the table the way the table model
    * does (TableAssemblyModelFactory: the lens, then the column infos).
    */
   private static void stopQuery(AssetQuerySandbox box, Worksheet ws, String name)
      throws Exception
   {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      ViewsheetService engine = mock(ViewsheetService.class);
      Principal user = mock(Principal.class);
      when(engine.getWorksheet("ws1", user)).thenReturn(rws);
      WSQueryService service = new WSQueryService(
         engine, mock(AssetDataCache.class), mock(DataSourceRegistry.class));
      WSAssemblyEvent event = mock(WSAssemblyEvent.class);
      when(event.getAssemblyName()).thenReturn(name);

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

         service.stopQuery("ws1", event, user, mock(CommandDispatcher.class));
      }
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

   private static final AtomicBoolean BLOCK = new AtomicBoolean();
   private static final AtomicInteger EXECUTED = new AtomicInteger();
   private static final AtomicInteger RUN = new AtomicInteger();
   private static final AtomicReference<CountDownLatch> ENTERED = new AtomicReference<>();
}
