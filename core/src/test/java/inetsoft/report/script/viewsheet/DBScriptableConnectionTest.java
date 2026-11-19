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
package inetsoft.report.script.viewsheet;

import inetsoft.report.XSessionManager;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.XDataSource;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.script.XTableArray;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XEngine;
import inetsoft.uql.util.*;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.sql.*;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The connection object of a database script ({@code db = createConnection(source)}): the statements it prepares, the
 * transaction methods, and {@code scriptOver}, which turns every write into a no-op after the script has run once. The
 * data source is an in-memory Derby database that the data source registry hands out.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  DBScriptableConnectionTest.LiveJdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class DBScriptableConnectionTest {
   private static final String DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";

   /** The beans the JDBC path reaches: a real pool factory and query engine, and a Drivers that finds Derby. */
   @Configuration
   static class LiveJdbcConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         // package-private constructor, normally reached by component scanning
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      @Bean
      public AssetDataCache assetDataCache() {
         return mock(AssetDataCache.class);
      }

      @Bean
      public Plugins plugins(BlobStorageManager blobStorageManager, Cluster cluster,
                             ApplicationEventPublisher eventPublisher)
      {
         return new Plugins(blobStorageManager.getStorage("plugins", true), cluster, eventPublisher);
      }

      @Bean
      public Config config(Plugins plugins) {
         return new Config(plugins);
      }

      @Bean
      public ConnectionPoolFactory connectionPoolFactory() {
         return new DefaultConnectionPoolFactory();
      }

      @Bean
      public Drivers drivers(Plugins plugins, ConnectionPoolFactory connectionPoolFactory) {
         final ClassLoader loader = DBScriptableConnectionTest.class.getClassLoader();

         return new Drivers(plugins, connectionPoolFactory) {
            @Override
            public ClassLoader getDriverClassLoader(String classname, String url) {
               return loader;
            }

            @Override
            public SQLExecutor getSQLExecutor(String classname, String url) {
               return null;
            }

            @Override
            public Class<?> getDriverClass(String className) throws ClassNotFoundException {
               return Class.forName(className, true, loader);
            }

            @Override
            public <T> Class<? extends T> getDriverClass(String className, Class<T> expected)
               throws ClassNotFoundException
            {
               return Class.forName(className, false, loader).asSubclass(expected);
            }
         };
      }

      @Bean
      public DataSourceRegistry dataSourceRegistry() {
         return mock(DataSourceRegistry.class);
      }

      @Bean
      public XRepository xRepository(Cluster cluster, Config config,
                                     DataSourceRegistry dataSourceRegistry,
                                     ConnectionPoolFactory connectionPoolFactory)
      {
         return new XEngine(cluster, config, dataSourceRegistry, connectionPoolFactory);
      }

      @Bean
      public ColumnCache columnCache(DataSourceRegistry dataSourceRegistry, XRepository repository) {
         return new ColumnCache(dataSourceRegistry, repository);
      }

      @Bean
      public XSessionManager xSessionManager(XRepository repository, XSessionService sessionService,
                                             DataSourceRegistry dataSourceRegistry)
         throws Exception
      {
         return new XSessionManager(repository, sessionService, dataSourceRegistry);
      }
   }

   @Autowired
   private DataSourceRegistry dataSourceRegistry;

   private String url;
   private DBScriptable db;

   @BeforeEach
   void setUp() throws Exception {
      String name = "dbs" + UUID.randomUUID().toString().replace("-", "");
      url = "jdbc:derby:memory:" + name + ";create=true";

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("dbsource");
      ds.setDriver(DRIVER);
      ds.setURL(url);
      ds.setRequireLogin(false);
      when(dataSourceRegistry.getDataSource(eq("dbsource"))).thenReturn(ds);

      try(Connection c = DriverManager.getConnection(url); Statement st = c.createStatement()) {
         st.execute("CREATE TABLE T(X INT)");
         st.execute("INSERT INTO T VALUES (1)");
      }

      db = new DBScriptable("dbsource", null, null, null);
   }

   @AfterEach
   void tearDown() throws Throwable {
      db.setScriptOver(false);
      db.close();

      // the pool of the data source and the in-memory database are not needed after the test
      JDBCDataSource ds = (JDBCDataSource) dataSourceRegistry.getDataSource("dbsource");
      ConnectionPoolFactory.getInstance().closeConnectionPool(JDBCHandler.getConnectionPool(ds, null));

      try {
         DriverManager.getConnection(url.replace(";create=true", ";drop=true"));
      }
      catch(SQLException expected) {
         // Derby reports the dropped database as an exception
      }
   }

   /** The number of rows in T as another connection sees it, so only committed rows count. */
   private int committedRows() throws Exception {
      try(Connection c = DriverManager.getConnection(url); Statement st = c.createStatement();
          ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM T"))
      {
         rs.next();
         return rs.getInt(1);
      }
   }

   /** The size of a result: its rows plus the header row. */
   private long size(Object result) {
      return ((XTableArray) result).getArraySize();
   }

   @Test
   void classNameAndTheFunctionsItOffers() {
      assertEquals("DBScriptable", db.getClassName());

      for(String name : new String[] { "executeSelect", "executeQuery", "executeUpdate", "update", "commit",
                                       "rollback", "close", "prepareStatement", "prepareCall" })
      {
         assertTrue(db.hasMember(name), name);
         assertNotNull(db.getMember(name), name);
      }

      assertEquals(9, db.getMemberKeys().length);
      assertFalse(db.hasMember("conn"));
      assertNull(db.getMember("conn"));
   }

   @Test
   void putMemberAddsAMember() {
      db.putMember("custom", 3);

      assertEquals(3, db.getMember("custom"));
      assertTrue(db.hasMember("custom"));
   }

   @Test
   void aDataSourceThatIsNotJdbcIsRefused() {
      XDataSource other = mock(XDataSource.class);
      when(dataSourceRegistry.getDataSource(eq("other"))).thenReturn(other);

      assertThrows(RuntimeException.class, () -> new DBScriptable("other", null, null, null));
      assertThrows(RuntimeException.class, () -> new DBScriptable("noSuchSource", null, null, null));
   }

   @Test
   void executeSelectGivesTheRowsWithAHeaderRow() throws Throwable {
      assertEquals(2L, size(db.executeSelect("SELECT X FROM T")));
   }

   @Test
   void anUpdateIsVisibleToTheScriptUntilItIsRolledBack() throws Throwable {
      db.executeUpdate("INSERT INTO T VALUES (2)");

      assertEquals(3L, size(db.executeSelect("SELECT X FROM T")));

      db.rollback();

      assertEquals(1, committedRows());
      assertEquals(2L, size(db.executeSelect("SELECT X FROM T")));
   }

   @Test
   void commitMakesTheChangeVisibleToOtherConnections() throws Throwable {
      db.executeUpdate("INSERT INTO T VALUES (2)");

      db.commit();

      assertEquals(2, committedRows());
      // the cached query results that depend on the data source are dropped
      verify(AssetDataCache.getCache(), atLeastOnce()).removeCacheDependence("dbsource");
   }

   @Test
   void aPreparedStatementIsRunByUpdateAndExecuteQuery() throws Throwable {
      PreparedStatementScriptable insert = (PreparedStatementScriptable) db.prepareStatement(
         "INSERT INTO T VALUES (?)");
      insert.setInt(1, 5);
      db.update(insert);
      db.commit();

      PreparedStatementScriptable select = (PreparedStatementScriptable) db.prepareStatement(
         "SELECT X FROM T WHERE X = ?");
      select.setInt(1, 5);

      assertEquals(2L, size(db.executeQuery(select)));
      assertEquals(2, committedRows());
   }

   @Test
   void prepareCallGivesACallableStatement() throws Throwable {
      assertInstanceOf(CallableStatementScriptable.class,
                       db.prepareCall("{call SYSCS_UTIL.SYSCS_CHECKPOINT_DATABASE()}"));
   }

   @Test
   void aBadStatementIsRethrown() {
      assertThrows(SQLException.class, () -> db.executeSelect("SELECT * FROM NO_SUCH_TABLE"));
      assertThrows(SQLException.class, () -> db.executeUpdate("INSERT INTO NO_SUCH_TABLE VALUES (1)"));
      assertThrows(SQLException.class, () -> db.prepareStatement("SELEKT"));
   }

   @Test
   void closeReleasesTheConnection() throws Throwable {
      db.close();

      assertThrows(Throwable.class, () -> db.executeUpdate("INSERT INTO T VALUES (2)"));
   }

   @Test
   void scriptOverTurnsWritesAndTransactionMethodsIntoNoOps() throws Throwable {
      assertFalse(db.isScriptOver());
      PreparedStatementScriptable insert = (PreparedStatementScriptable) db.prepareStatement(
         "INSERT INTO T VALUES (?)");
      insert.setInt(1, 9);

      db.setScriptOver(true);

      assertTrue(db.isScriptOver());
      assertDoesNotThrow(() -> db.update(insert));
      assertDoesNotThrow(db::commit);
      assertDoesNotThrow(db::rollback);
      assertDoesNotThrow(db::close);
      assertEquals(1, committedRows(), "nothing was written");
   }

   @Test
   void scriptsCanDriveItThroughTheEngine() throws Exception {
      GraalJavaScriptEngine engine = new GraalJavaScriptEngine();

      try {
         engine.init(new HashMap<>());
         engine.put("db", db);
         Object result = engine.exec(engine.compile(
            "(function(){db.executeUpdate('INSERT INTO T VALUES (7)'); db.commit();" +
            "var r = db.executeSelect('SELECT X FROM T ORDER BY X'); return r.length + ',' + r[2][0];})()"),
                                     null, null);

         assertEquals("3,7", String.valueOf(result));
      }
      finally {
         engine.close();
      }
   }

   /** The number of connections of the data source pool that are in use. */
   private int activeConnections() {
      JDBCDataSource ds = (JDBCDataSource) dataSourceRegistry.getDataSource("dbsource");
      com.zaxxer.hikari.HikariDataSource pool =
         (com.zaxxer.hikari.HikariDataSource) JDBCHandler.getConnectionPool(ds, null);
      return pool.getHikariPoolMXBean().getActiveConnections();
   }

   private ViewsheetScope newScope() {
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      when(box.getViewsheet()).thenReturn(vs);
      when(box.getAssetEntry()).thenReturn(AssetEntry.createAssetEntry("1^128^__NULL__^dbscript"));
      return new ViewsheetScope(box, false);
   }

   /**
    * The script of a form table runs again on every refresh and calls createConnection each time. A script that
    * closes its connection gives it back to the pool of the data source every time.
    */
   @Test
   void aScriptThatClosesItsConnectionDoesNotUseUpThePool() throws Throwable {
      db.close();
      ViewsheetScope scope = newScope();
      int before = activeConnections();

      for(int i = 0; i < 3; i++) {
         DBScriptable connection = (DBScriptable) scope.createConnection("dbsource", null, null);
         connection.executeUpdate("INSERT INTO T VALUES (" + (10 + i) + ")");
         connection.rollback();
         connection.close();
      }

      assertEquals(before, activeConnections());
   }
}
