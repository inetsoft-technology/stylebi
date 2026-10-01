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

import inetsoft.report.script.graal.ReportGraalJavaScriptEngine;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.logging.Logger;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77467: a script must not be able to obtain or operate a live JDBC connection,
 * however it reaches one. The diagnosis demonstrated the live exploit end to end (a
 * script built its own data source and read a value back from SQL it issued through a
 * pool factory, a pool library, the driver manager and a driver). This test is the
 * committed structural guard, in the same style as
 * {@link ScriptHelperClassLoadEnginesTest#jdbcHandlerIsNotScriptVisible}: it asserts,
 * through both production engines, that every way a script could reach a live handle
 * is now closed, while the value types the form write-back API relies on still work.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScriptJdbcAccessEnginesTest {
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

   /** Runs the expression and returns "OK:" + value or "ERR:" + error, like the sibling test. */
   private String run(String expr) throws Exception {
      Object result = engine.exec(engine.compile(
         "(function(){try{return 'OK:'+(" + expr + ");}catch(x){return 'ERR:'+x;}})()"),
                                  null, null);
      return String.valueOf(result);
   }

   private String typeofExpr(String expr) throws Exception {
      return run("typeof (" + expr + ")");
   }

   /**
    * The pool factory interface and its implementations are the reported bypass
    * (probes B/C/D). Denying the interface hides its static getInstance() and makes
    * the implementations unconstructible, so none can hand out a getConnectionPool().
    */
   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void connectionPoolFactoriesAreNotScriptVisible(String kind) throws Exception {
      open(kind);

      // the static accessor is gone (deny on an interface covers its statics)
      assertEquals("OK:undefined",
                   typeofExpr("Java.type('inetsoft.uql.jdbc.ConnectionPoolFactory').getInstance"));

      // the implementations can no longer even be constructed, so getConnectionPool
      // is unreachable (probes C/D)
      for(String impl : new String[] { "DefaultConnectionPoolFactory", "JNDIConnectionPoolFactory",
                                       "LegacyConnectionPoolFactory" })
      {
         String made = run("(new (Java.type('inetsoft.uql.jdbc." + impl + "'))()) != null");
         assertTrue(made.startsWith("ERR:"), impl + " was still constructible from script: " + made);
      }
   }

   /**
    * Probe E/H: the pool library itself. With com.zaxxer added to the blocked
    * packages, the type lookup fails, and the member denies also cover the types.
    */
   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void hikariPoolTypesAreNotScriptVisible(String kind) throws Exception {
      open(kind);

      assertTrue(run("Java.type('com.zaxxer.hikari.HikariDataSource') != null").startsWith("ERR:"),
                 "HikariDataSource is still reachable via Java.type");
      assertTrue(run("Java.type('com.zaxxer.hikari.HikariConfig') != null").startsWith("ERR:"),
                 "HikariConfig is still reachable via Java.type");
   }

   /**
    * Probe F/G: the driver manager and any JDBC driver on the classpath. The types
    * stay reachable (java.sql is admitted for the form value types), but the member
    * that returns a connection is denied. java.sql.DriverManager.getConnection and
    * java.sql.Driver.connect must not be callable.
    */
   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void driverManagerAndDriversCannotOpenConnections(String kind) throws Exception {
      open(kind);

      assertEquals("OK:undefined",
                   typeofExpr("Java.type('java.sql.DriverManager').getConnection"));
      // a concrete driver on the test classpath: denying java.sql.Driver covers the
      // implementor, so it cannot even be constructed, let alone have connect(...) called
      assertTrue(run("(new (Java.type('org.apache.derby.jdbc.EmbeddedDriver'))()) != null")
                    .startsWith("ERR:"),
                 "a JDBC Driver was still constructible from script");
   }

   /**
    * The core of the bug (axis 2): once a script holds any javax.sql.DataSource, member
    * access let it call getConnection() regardless of the class filter. A real
    * DataSource handed to the engine as a global must now refuse getConnection.
    */
   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void heldDataSourceRefusesGetConnection(String kind) throws Exception {
      open(kind);
      engine.put("heldDs", new ThrowingDataSource());

      assertEquals("OK:object", typeofExpr("heldDs"));
      assertEquals("OK:undefined", typeofExpr("heldDs.getConnection"),
                   "getConnection is reachable by member access on a held DataSource");
      // and invoking it fails rather than returning a connection
      assertTrue(run("heldDs.getConnection() != null").startsWith("ERR:"),
                 "a held DataSource still produced a connection");
   }

   /**
    * R5: the query engine runs a query against the data source the query carries, with
    * no data-source permission check. The two engine-side execution entry points a
    * script could reach must be closed.
    */
   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void queryEngineExecutionEntryPointsAreNotScriptVisible(String kind) throws Exception {
      open(kind);

      // getXNodeTableLens / getXNode via the session manager
      assertEquals("OK:undefined",
                   typeofExpr("Java.type('inetsoft.report.XSessionManager').getSessionManager"));
      // the execute(...) family the data service exposes (XRepository extends it); the
      // static getRepository() still returns an object, but execute is denied on it
      assertEquals("OK:undefined", typeofExpr(
         "Java.type('inetsoft.uql.XRepository').getRepository != null ? " +
         "(function(){try{return (Java.type('inetsoft.uql.XRepository').getRepository()).execute;}" +
         "catch(e){return undefined;}})() : undefined"));
   }

   /**
    * Form write-back (ViewsheetScope.createConnection -> DBScriptable) keeps the
    * Connection on the Java side and hands the script only ScriptFunctions and
    * XTableArray/primitives; its parameter binding uses java.sql.Types constants and
    * its getters return java.sql.Date/Time/Timestamp. Those value types must stay
    * usable, confirming the member denies target only the connection-bearing types.
    */
   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void jdbcValueTypesUsedByFormWriteBackStillWork(String kind) throws Exception {
      open(kind);

      // java.sql.Types constants (setNull / setObject(targetSqlType))
      assertEquals("OK:number", typeofExpr("Java.type('java.sql.Types').VARCHAR"));
      assertEquals("OK:number", typeofExpr("Java.type('java.sql.Types').TIMESTAMP"));
      // java.sql.Timestamp / Date / Time value objects (getters / setters)
      assertEquals("OK:0", run("(new (Java.type('java.sql.Timestamp'))(0)).getTime()"));
      assertEquals("OK:true", run("(new (Java.type('java.sql.Date'))(0)) != null"));
      assertEquals("OK:true", run("(new (Java.type('java.sql.Time'))(0)) != null"));
   }

   /** A minimal real DataSource used only to prove member access to getConnection is denied. */
   public static final class ThrowingDataSource implements DataSource {
      @Override
      public Connection getConnection() throws SQLException {
         throw new SQLException("must never be called from script");
      }

      @Override
      public Connection getConnection(String username, String password) throws SQLException {
         throw new SQLException("must never be called from script");
      }

      @Override
      public PrintWriter getLogWriter() {
         return null;
      }

      @Override
      public void setLogWriter(PrintWriter out) {
      }

      @Override
      public void setLoginTimeout(int seconds) {
      }

      @Override
      public int getLoginTimeout() {
         return 0;
      }

      @Override
      public Logger getParentLogger() {
         return null;
      }

      @Override
      public <T> T unwrap(Class<T> iface) {
         return null;
      }

      @Override
      public boolean isWrapperFor(Class<?> iface) {
         return false;
      }
   }
}
