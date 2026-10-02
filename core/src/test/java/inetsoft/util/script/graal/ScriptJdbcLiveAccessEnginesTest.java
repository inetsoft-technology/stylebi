/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.util.script.graal;

import inetsoft.report.XSessionManager;
import inetsoft.report.script.graal.ReportGraalJavaScriptEngine;
import inetsoft.report.script.viewsheet.DBScriptable;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XEngine;
import inetsoft.uql.util.*;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.sql.*;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #77467, end to end against a live embedded Derby database. Complements the
 * structural {@link ScriptJdbcAccessEnginesTest}: through both production engines,
 * each script builds its own {@code JDBCDataSource} (no registry, no permission
 * check) and tries to read a value back from SQL it issues through a pool factory,
 * a pool library, the driver manager, a driver, or the query engine. Each must fail
 * to return the value. With the sandbox fix reverted every one of them returns 42.
 * It also checks that the form write-back API ({@link DBScriptable}, which
 * {@code ViewsheetScope.createConnection} returns) still creates, inserts, binds and
 * selects through a real connection under the new denies.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  ScriptJdbcLiveAccessEnginesTest.LiveJdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScriptJdbcLiveAccessEnginesTest {
   private static final String DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";

   /**
    * The beans the JDBC and query-engine paths reach: a real pool factory and query
    * engine, and a {@code Drivers} that resolves the Derby driver from the test class
    * path, modeling an install that has the Derby driver plugin.
    */
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
         final ClassLoader loader = ScriptJdbcLiveAccessEnginesTest.class.getClassLoader();

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

   // helpers shared by every script: a JDBC URL, read 42 back from a connection, and
   // a data source and a query over it. Since round 1 a script can no longer build or
   // configure a data source or query itself, so mkds/mkq hand it one built on the
   // Java side (Fixtures). Each scenario therefore still exercises its own deny, the
   // way a script that got hold of such an object from some Java API would.
   private static final String HELPERS =
      "function url(n){return 'jdbc:derby:memory:s77467'+n+';create=true';}" +
      "function mkds(n){return fx.ds(n);}" +
      "function read42(c){var rs=c.createStatement().executeQuery('VALUES 42');rs.next();" +
      "var v=rs.getInt(1);try{c.close();}catch(e){}return 'value='+v;}" +
      "function mkq(n){return fx.query(n);}" +
      "function vars(){return new (Java.type('inetsoft.uql.VariableTable'))();}";

   private static final String SECRET = "TOPSECRET";

   /**
    * Builds, on the Java side, the data sources and queries the scripts are handed.
    * Data source n points at the in-memory Derby database "s77467" + n.
    */
   public static final class Fixtures {
      /** Body of the MARK77467 Derby procedure (Derby needs a public class). */
      public static synchronized void mark() {
         System.setProperty(MARKS, String.valueOf(marks() + 1));
      }

      public JDBCDataSource ds(String n) {
         JDBCDataSource ds = new JDBCDataSource();
         ds.setName("self" + n);
         ds.setDriver(DRIVER);
         ds.setURL(url(n));
         ds.setRequireLogin(false);
         return ds;
      }

      public JDBCQuery query(String n) {
         JDBCQuery q = new JDBCQuery();
         q.setName("q" + n);
         q.setDataSource(ds(n));
         q.setSQLDefinition(new UniformSQL("VALUES 42", false));
         return q;
      }
   }

   private static String url(String n) {
      return "jdbc:derby:memory:s77467" + n + ";create=true";
   }

   private static void sql(String n, String... statements) throws SQLException {
      try(Connection c = DriverManager.getConnection(url(n)); Statement st = c.createStatement()) {
         for(String statement : statements) {
            st.execute(statement);
         }
      }
   }

   /**
    * Counts calls of the MARK77467 stored procedure, in a system property. A
    * connectionInitSql that calls it proves the pool ran SQL the script chose. A row
    * inserted by the init SQL would not do: the pool's connections do not auto-commit,
    * so it would stay uncommitted and locked. And a static field would not do either:
    * Derby may resolve the procedure's class through a different class loader on the
    * script's thread, giving it its own copy of the field.
    */
   private static final String MARKS = "inetsoft.test.bug77467.marks";

   private static int marks() {
      return Integer.getInteger(MARKS, 0);
   }

   /** Creates database n holding a one-row table SECRET77467(V) = TOPSECRET. */
   private static void secretDb(String n) throws SQLException {
      sql(n, "CREATE TABLE SECRET77467(V VARCHAR(20))",
          "INSERT INTO SECRET77467 VALUES ('" + SECRET + "')");
   }

   @Autowired
   private DataSourceRegistry dataSourceRegistry;

   private GraalJavaScriptEngine engine;

   private void open(String kind) throws Exception {
      engine = "report".equals(kind) ? new ReportGraalJavaScriptEngine() : new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
      engine.put("fx", new Fixtures());
   }

   @AfterEach
   void teardown() {
      if(engine != null) {
         engine.close();
      }
   }

   /** Runs the function body and returns "OK:" + its value or "ERR:" + the error. */
   private String run(String body) throws Exception {
      Object result = engine.exec(engine.compile(HELPERS +
         "(function(){try{return 'OK:'+(function(){" + body + "})();}" +
         "catch(x){return 'ERR:'+x;}})()"), null, null);
      return String.valueOf(result);
   }

   private void assertNoValue(String scenario, String body) throws Exception {
      String result = run(body);
      assertTrue(result.startsWith("ERR:"),
                 scenario + ": the script read a value from SQL it issued: " + result);
   }

   private void assertNoSecret(String scenario, String body) throws Exception {
      String result = run(body);
      assertTrue(result.startsWith("ERR:") && !result.contains(SECRET),
                 scenario + ": the script read a table through a helper: " + result);
   }

   /**
    * Round 1, the primary lever: a script can no longer construct a data source or a
    * query, nor configure, read or clone one it holds. Without this a script names
    * any URL, credentials and pool properties itself and passes the object to any
    * Java helper that connects or runs it.
    */
   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void scriptsCannotBuildOrConfigureDataSourcesOrQueries(String kind) throws Exception {
      open(kind);

      for(String type : new String[] { "inetsoft.uql.jdbc.JDBCDataSource",
                                       "inetsoft.uql.xmla.XMLADataSource",
                                       "inetsoft.uql.jdbc.JDBCQuery",
                                       "inetsoft.uql.xmla.XMLAQuery" })
      {
         String made = run("return (new (Java.type('" + type + "'))()) != null;");
         assertTrue(made.startsWith("ERR:"), type + " is still constructible from script: " + made);
      }

      engine.put("heldDs", new Fixtures().ds("cfg" + kind));
      engine.put("heldQ", new Fixtures().query("cfg" + kind));

      for(String member : new String[] { "setURL", "setDriver", "setUser", "setPassword",
                                         "setPoolProperties", "getPassword", "clone" })
      {
         assertEquals("OK:undefined", run("return typeof heldDs." + member + ";"),
                      member + " is reachable on a held data source");
      }

      for(String member : new String[] { "setDataSource", "setSQLDefinition", "getDataSource",
                                         "clone" })
      {
         assertEquals("OK:undefined", run("return typeof heldQ." + member + ";"),
                      member + " is reachable on a held query");
      }
   }

   /**
    * Pool properties are applied to the Hikari config by name, so connectionInitSql
    * runs arbitrary SQL on every new connection the pool opens, with no Connection
    * ever reaching the script. A script must not be able to set them on a data source
    * it holds and then make a helper (here the table-metadata path) build the pool.
    */
   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void poolPropertiesCannotBeSetAndRun(String kind) throws Exception {
      open(kind);
      String db = "pp" + kind;
      sql(db, "CREATE TABLE MARK77467(X INT)",
          "CREATE PROCEDURE MARK77467() LANGUAGE JAVA PARAMETER STYLE JAVA NO SQL " +
          "EXTERNAL NAME '" + Fixtures.class.getName() + ".mark'");
      System.clearProperty(MARKS);
      JDBCDataSource ds = new Fixtures().ds(db);
      engine.put("heldDs", ds);

      String result = run(
         "var p=new (Java.type('java.util.TreeMap'))();" +
         "p.put('connectionInitSql','CALL MARK77467()');" +
         "heldDs.setPoolProperties(p);" +
         "Java.type('inetsoft.uql.jdbc.util.JDBCUtil').getTableColumns('APP.MARK77467',heldDs,'s');" +
         "return 'ran';");

      assertTrue(result.startsWith("ERR:"), "the script configured and opened a pool: " + result);

      // The pool outlives the script, and its key leaves out the pool properties, so
      // the script's settings apply to every later connection from it, whoever asks.
      try(Connection c = ConnectionPoolFactory.getInstance().getConnectionPool(ds, null)
         .getConnection())
      {
         assertNotNull(c);
      }

      assertEquals(0, marks(), "connectionInitSql set by the script ran");
   }

   /**
    * The tester's demonstrated residual (04-verify R5c2/R5c4): JDBCAgent.getQueryData
    * builds SELECT DISTINCT column FROM table and runs it Java-side against the data
    * source the query carries. ColumnCache.getColumnData reaches the same code.
    */
   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void queryHelpersCannotReadTables(String kind) throws Exception {
      open(kind);
      String db = "qa" + kind;
      secretDb(db);
      engine.put("heldQ", new Fixtures().query(db));

      assertNoSecret("XAgent.getAgent('jdbc').getQueryData",
         "var n=Java.type('inetsoft.uql.util.XAgent').getAgent('jdbc')" +
         ".getQueryData(heldQ,'SECRET77467','V',vars(),'s',null);n.next();" +
         "return 'value='+n.getObject(0);");
      assertNoSecret("new JDBCAgent().getQueryData",
         "var n=new (Java.type('inetsoft.uql.jdbc.util.JDBCAgent'))()" +
         ".getQueryData(heldQ,'SECRET77467','V',vars(),'s',null);n.next();" +
         "return 'value='+n.getObject(0);");
      assertNoSecret("ColumnCache.getColumnData",
         "var b=Java.type('inetsoft.uql.util.ColumnCache').getColumnCache()" +
         ".getColumnData(heldQ,'SECRET77467','V',null,vars());" +
         "return 'value='+b.values()[0];");
   }

   /**
    * Review finding 1: SQLTypes.getChildMetaData and JDBCUtil.getTableColumns call
    * XRepository.getMetaData Java-side, which connects to the data source it is given.
    */
   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void metadataHelpersCannotConnect(String kind) throws Exception {
      open(kind);
      String db = "md" + kind;
      secretDb(db);
      engine.put("heldDs", new Fixtures().ds(db));

      String meta = run("var x=Java.type('inetsoft.uql.jdbc.util.SQLTypes')" +
                        ".getChildMetaData(heldDs);return 'node='+(x!=null);");
      assertTrue(meta.startsWith("ERR:"), "SQLTypes.getChildMetaData connected: " + meta);

      String cols = run("var c=Java.type('inetsoft.uql.jdbc.util.JDBCUtil')" +
                        ".getTableColumns('APP.SECRET77467',heldDs,'s');" +
                        "return 'cols='+c.length+(c.length>0?':'+c[0].getName():'');");
      assertTrue(cols.startsWith("ERR:"), "JDBCUtil.getTableColumns connected: " + cols);
   }

   /**
    * Round 2 (07-verify-r2 probe Q): DefaultMetaDataProvider takes any data source a
    * script holds, here one the registry returned, and runs metadata against it
    * Java-side through XRepository.getMetaData with no permission check, connecting
    * with the data source's stored credentials. The database is not created
    * beforehand and the data source's URL has create=true, so it exists afterwards
    * only if the provider opened a connection. XEngine caches metadata in files that
    * outlive the JVM, keyed by data source name, so the name is unique per run;
    * otherwise a second run is answered from the cache and never connects.
    */
   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void metaDataProviderCannotConnect(String kind) throws Exception {
      open(kind);
      String db = "dmp" + kind + System.nanoTime();
      String name = "victim" + kind;
      when(dataSourceRegistry.getDataSource(eq(name))).thenReturn(new Fixtures().ds(db));
      engine.put("heldDs", dataSourceRegistry.getDataSource(name));

      String result = run(
         "var p=new (Java.type('inetsoft.uql.util.DefaultMetaDataProvider'))();" +
         "p.setDataSource(heldDs);" +
         "var t=p.getTable('APP.SECRET77467','',true);" +
         "return 'ran:'+(t!=null);");

      assertTrue(result.startsWith("ERR:"), "DefaultMetaDataProvider ran metadata: " + result);
      SQLException notFound = assertThrows(
         SQLException.class,
         () -> DriverManager.getConnection("jdbc:derby:memory:s77467" + db).close(),
         "DefaultMetaDataProvider opened a connection to the registry data source");
      assertEquals("XJ004", notFound.getSQLState());
   }

   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void poolFactoriesCannotRunSql(String kind) throws Exception {
      open(kind);

      assertNoValue("ConnectionPoolFactory.getInstance()", "return read42(" +
         "Java.type('inetsoft.uql.jdbc.ConnectionPoolFactory').getInstance()" +
         ".getConnectionPool(mkds('b" + kind + "'),null).getConnection());");
      assertNoValue("new DefaultConnectionPoolFactory()", "return read42(" +
         "new (Java.type('inetsoft.uql.jdbc.DefaultConnectionPoolFactory'))()" +
         ".getConnectionPool(mkds('c" + kind + "'),null).getConnection());");
      assertNoValue("new JNDIConnectionPoolFactory()", "return read42(" +
         "new (Java.type('inetsoft.uql.jdbc.JNDIConnectionPoolFactory'))()" +
         ".getConnectionPool(mkds('d" + kind + "'),null).getConnection());");
   }

   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void poolLibraryDriverManagerAndDriversCannotRunSql(String kind) throws Exception {
      open(kind);

      assertNoValue("HikariDataSource",
         "var h=new (Java.type('com.zaxxer.hikari.HikariDataSource'))();" +
         "h.setJdbcUrl(url('e" + kind + "'));return read42(h.getConnection());");
      assertNoValue("DriverManager.getConnection",
         "return read42(Java.type('java.sql.DriverManager').getConnection(url('f" + kind + "')));");
      assertNoValue("DriverManager.getDriver().connect",
         "return read42(Java.type('java.sql.DriverManager').getDriver(url('g" + kind + "'))" +
         ".connect(url('g" + kind + "'),new (Java.type('java.util.Properties'))()));");
      assertNoValue("Driver.connect",
         "return read42(new (Java.type('" + DRIVER + "'))()" +
         ".connect(url('h" + kind + "'),new (Java.type('java.util.Properties'))()));");
      assertNoValue("a driver's own DataSource",
         "var d=new (Java.type('org.apache.derby.jdbc.EmbeddedDataSource'))();" +
         "d.setDatabaseName('memory:s77467i" + kind + "');d.setCreateDatabase('create');" +
         "return read42(d.getConnection());");
   }

   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void queryEngineEntryPointsCannotRunSql(String kind) throws Exception {
      open(kind);

      assertNoValue("XSessionManager.getXNodeTableLens",
         "var t=Java.type('inetsoft.report.XSessionManager').getSessionManager()" +
         ".getXNodeTableLens(mkq('j" + kind + "'),vars(),null);t.moreRows(1);" +
         "return 'value='+t.getObject(1,0);");
      assertNoValue("XRepository.execute",
         "var n=Java.type('inetsoft.uql.XRepository').getRepository()" +
         ".execute('s',mkq('k" + kind + "'),vars(),null,false,null);n.next();" +
         "return 'value='+n.getObject(0);");
   }

   /**
    * ViewsheetScope.createConnection returns a DBScriptable for a registry data source.
    * It keeps the Connection and statements on the Java side, so the denies must not
    * stop it: create, insert, a bound prepared-statement update, and a select.
    */
   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void formWriteBackStillWorks(String kind) throws Exception {
      open(kind);
      JDBCDataSource form = new JDBCDataSource();
      form.setName("formds");
      form.setDriver(DRIVER);
      form.setURL("jdbc:derby:memory:s77467form" + kind + ";create=true");
      form.setRequireLogin(false);
      when(dataSourceRegistry.getDataSource(eq("formds"))).thenReturn(form);

      engine.put("db", new DBScriptable("formds", null, null, null));
      String result = run(
         "db.executeUpdate('CREATE TABLE T1(X INT)');" +
         "db.executeUpdate('INSERT INTO T1 VALUES (42)');" +
         "var ps=db.prepareStatement('INSERT INTO T1 VALUES (?)');" +
         "ps.setInt(1,43);db.update(ps);" +
         "var r=db.executeSelect('SELECT X FROM T1 ORDER BY X');" +
         "return r.length+','+r[1][0]+','+r[2][0]+','+(typeof db.conn);");

      assertEquals("OK:3,42,43,undefined", result);
   }
}
