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
      public XSessionManager xSessionManager(XRepository repository, XSessionService sessionService,
                                             DataSourceRegistry dataSourceRegistry)
         throws Exception
      {
         return new XSessionManager(repository, sessionService, dataSourceRegistry);
      }
   }

   // helpers shared by every script: a self-built data source, a JDBC URL, read 42
   // back from a connection, and a self-built query over a self-built data source
   private static final String HELPERS =
      "function url(n){return 'jdbc:derby:memory:s77467'+n+';create=true';}" +
      "function mkds(n){var ds=new (Java.type('inetsoft.uql.jdbc.JDBCDataSource'))();" +
      "ds.setName('self'+n);ds.setDriver('" + DRIVER + "');ds.setURL(url(n));" +
      "ds.setRequireLogin(false);return ds;}" +
      "function read42(c){var rs=c.createStatement().executeQuery('VALUES 42');rs.next();" +
      "var v=rs.getInt(1);try{c.close();}catch(e){}return 'value='+v;}" +
      "function mkq(n){var q=new (Java.type('inetsoft.uql.jdbc.JDBCQuery'))();q.setName('q'+n);" +
      "q.setDataSource(mkds(n));" +
      "q.setSQLDefinition(new (Java.type('inetsoft.uql.jdbc.UniformSQL'))('VALUES 42',false));" +
      "return q;}" +
      "function vars(){return new (Java.type('inetsoft.uql.VariableTable'))();}";

   @Autowired
   private DataSourceRegistry dataSourceRegistry;

   private GraalJavaScriptEngine engine;

   private void open(String kind) throws Exception {
      engine = "report".equals(kind) ? new ReportGraalJavaScriptEngine() : new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
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
