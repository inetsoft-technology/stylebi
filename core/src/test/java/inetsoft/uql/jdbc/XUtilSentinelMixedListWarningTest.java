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
package inetsoft.uql.jdbc;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.util.*;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78386, a sentinel parameter (NULL_VALUE, EMPTY_STRING) that is kept as written, so
 * that its text is compared, must log a warning: an item of an IN list with other items,
 * e.g. t.name IN ($(p), 'x'), and a parameter with several values, one of them a sentinel.
 * The sql and the rows are unchanged (#78139 E), with the sql string cleared (worksheet
 * merge) and kept (parsed sql-edited table).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  XUtilSentinelMixedListWarningTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XUtilSentinelMixedListWarningTest {
   private static final String DB = "memory:bug78386";
   private static final String NULL_VALUE = XConstants.CONDITION_NULL_VALUE;
   private static final String EMPTY_STRING = XConstants.CONDITION_EMPTY_STRING;
   private static final String SELECT = "select t.id from t where ";

   @Configuration
   static class JdbcConfig {
      // JDBCDataSource's constructor needs CredentialService, whose constructor is
      // package-private.
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

      // the pool returns a Derby data source directly
      @Bean
      public ConnectionPoolFactory connectionPoolFactory() {
         DataSource ds = recording(derby());
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

      // DerbyHelper asks the repository for the product version
      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   @BeforeAll
   static void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table t");
         }
         catch(Exception ignore) {
            // first run
         }

         // rows holding the text of the sentinels show a parameter bound as that text
         stmt.executeUpdate("create table t (id int, name varchar(20))");
         stmt.executeUpdate("insert into t values (1, 'n1'), (2, ''), (3, null), (4, 'null'), " +
                               "(5, 'NULL_VALUE'), (6, null), (7, 'EMPTY_STRING'), (8, 'x'), " +
                               "(9, 'xy')");
      }
   }

   @AfterAll
   static void dropDatabase() {
      try {
         DriverManager.getConnection("jdbc:derby:" + DB + ";drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   @BeforeEach
   void attachAppender() {
      appender = new ListAppender<>();
      appender.start();
      logger().addAppender(appender);
   }

   @AfterEach
   void detachAppender() {
      logger().detachAppender(appender);
      appender.stop();
   }

   // where clause, sentinel value of p, a plain value of p of the same shape, kept sql
   // string, the expected ids: the sentinel text is compared (#78139 E)
   static Stream<Arguments> keptSentinels() {
      Object[] nullArray = { NULL_VALUE, "x" };
      Object[] nullArrayLast = { "x", NULL_VALUE };
      Object[] emptyArray = { EMPTY_STRING, "x" };
      Object[] plainArray = { "n1", "x" };

      return Stream.of(
         Arguments.of("t.name in ($(p), 'x')", NULL_VALUE, "n1", false, List.of(5, 8)),
         Arguments.of("t.name in ('x', $(p))", NULL_VALUE, "n1", false, List.of(5, 8)),
         Arguments.of("t.name in ($(p), 'x')", EMPTY_STRING, "n1", false, List.of(7, 8)),
         Arguments.of("t.name in ('x', $(p))", EMPTY_STRING, "n1", false, List.of(7, 8)),
         Arguments.of("t.name in ('$(p)', 'x')", NULL_VALUE, "n1", false, List.of(5, 8)),
         Arguments.of("t.name not in ($(p), 'x')", NULL_VALUE, "n1", false,
                      List.of(1, 2, 4, 7, 9)),
         Arguments.of("t.id = 1 or t.name in ($(p), 'x')", NULL_VALUE, "n1", false,
                      List.of(1, 5, 8)),
         Arguments.of("t.name in ($(p), 'x')", NULL_VALUE, "n1", true, List.of(5, 8)),
         Arguments.of("t.name in ('x', $(p))", EMPTY_STRING, "n1", true, List.of(7, 8)),
         // a parameter with several values, one of them a sentinel
         Arguments.of("t.name in ($(p))", nullArray, plainArray, false, List.of(5, 8)),
         Arguments.of("t.name in ($(p))", nullArrayLast, plainArray, false, List.of(5, 8)),
         Arguments.of("t.name in ($(p))", emptyArray, plainArray, false, List.of(7, 8)),
         Arguments.of("t.name in ($(p))", nullArray, plainArray, true, List.of(5, 8)),
         Arguments.of("t.name in ($(p))", emptyArray, plainArray, true, List.of(7, 8)));
   }

   @ParameterizedTest
   @MethodSource("keptSentinels")
   void warnsForKeptSentinel(String where, Object p, Object plain, boolean kept,
                             List<Integer> expected)
      throws Exception
   {
      JDBCQuery query = newQuery(SELECT + where, kept);
      String sentinel = p instanceof Object[] ?
         (String) Arrays.stream((Object[]) p).filter(v -> !"x".equals(v)).findFirst().get() :
         (String) p;

      // twice on the same query, one warning per execution
      for(int i = 0; i < 2; i++) {
         Run run = execute(query, p);
         assertEquals(expected, run.keys, run.executedSql);
         assertEquals(1, run.warnings.size(), where + " " + run.warnings);
         String warning = run.warnings.get(0);
         assertTrue(warning.contains("arameter p ") && warning.contains(sentinel), warning);

         // the sql is the one the same shape sends for a plain value
         Run plainRun = execute(newQuery(SELECT + where, kept), plain);
         assertEquals(plainRun.executedSql, run.executedSql.replace(sentinel, "n1"));
         assertEquals(List.of(), plainRun.warnings, where);
      }
   }

   // where clause, value of p, the expected ids: nothing to warn about
   static Stream<Arguments> noWarning() {
      return Stream.of(
         Arguments.of("t.name in ($(p), 'x')", "n1", List.of(1, 8)),
         Arguments.of("t.name in ($(p))", new Object[] { "n1", "x" }, List.of(1, 8)),
         // rewritten to IS NULL, as before
         Arguments.of("t.name in ($(p))", NULL_VALUE, List.of(3, 6)),
         Arguments.of("t.name in ($(p))", new Object[] { NULL_VALUE }, List.of(3, 6)),
         Arguments.of("t.name = $(p)", EMPTY_STRING, List.of(2)));
   }

   @ParameterizedTest
   @MethodSource("noWarning")
   void noWarningWithoutKeptSentinel(String where, Object p, List<Integer> expected)
      throws Exception
   {
      for(boolean kept : new boolean[] { false, true }) {
         Run run = execute(newQuery(SELECT + where, kept), p);
         assertEquals(expected, run.keys, where + " kept=" + kept + " " + run.executedSql);
         assertEquals(List.of(), run.warnings, where + " kept=" + kept);
      }
   }

   private static Logger logger() {
      return (Logger) LoggerFactory.getLogger(XUtil.class);
   }

   private static JDBCQuery newQuery(String sql, boolean kept) throws Exception {
      UniformSQL usql = new UniformSQL();

      if(kept) {
         // as a sql-edited worksheet table that can't be merged keeps its sql string
         usql.setDataSource(dataSource());

         synchronized(usql) {
            usql.setParseSQL(true);
            usql.setSQLString(sql, true);
            usql.wait(10000);
         }

         assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sql);
         assertTrue(usql.hasSQLString(), sql);
      }
      else {
         usql.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
         assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sql);
         // as the worksheet merge does, otherwise validation skips the query
         usql.clearSQLString();
      }

      JDBCQuery query = new JDBCQuery();
      query.setName("bug78386");
      query.setDataSource(dataSource());
      query.setSQLDefinition(usql);
      return query;
   }

   private static Run execute(JDBCQuery query, Object p) throws Exception {
      VariableTable vars = new VariableTable();

      if(p != null) {
         vars.put("p", p);
      }

      Run run = new Run();
      JDBCHandler handler = new JDBCHandler();
      handler.connect(query.getDataSource(), vars);
      executed.set(null);
      appender.list.clear();
      XNode node = handler.execute(query, vars, null, null);
      run.warnings = appender.list.stream()
         .filter(e -> e.getLevel() == Level.WARN)
         .map(ILoggingEvent::getFormattedMessage)
         .toList();

      run.executedSql = executed.get();
      assertNotNull(run.executedSql, "nothing executed");
      XNodeTableLens table = new XNodeTableLens(node);
      table.moreRows(Integer.MAX_VALUE);

      for(int r = 1; r < table.getRowCount(); r++) {
         run.keys.add(((Number) table.getObject(r, 0)).intValue());
      }

      Collections.sort(run.keys);
      return run;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug78386");
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

   /**
    * JDBCHandler generates the sql on a clone of the query, so record the sql that reaches
    * the connection instead.
    */
   private static DataSource recording(DataSource ds) {
      return proxy(DataSource.class, ds);
   }

   @SuppressWarnings("unchecked")
   private static <T> T proxy(Class<T> type, T target) {
      return (T) Proxy.newProxyInstance(
         XUtilSentinelMixedListWarningTest.class.getClassLoader(), new Class<?>[] { type },
         (p, method, args) -> {
            String name = method.getName();

            if(args != null && args.length > 0 && args[0] instanceof String &&
               (name.startsWith("prepare") || name.startsWith("execute")) &&
               ((String) args[0]).trim().toLowerCase().startsWith("select"))
            {
               executed.set((String) args[0]);
            }

            Object result;

            try {
               result = method.invoke(target, args);
            }
            catch(InvocationTargetException e) {
               throw e.getCause();
            }

            if(result instanceof Connection && type != Connection.class) {
               return proxy(Connection.class, (Connection) result);
            }

            if(result instanceof Statement && !(result instanceof PreparedStatement)) {
               return proxy(Statement.class, (Statement) result);
            }

            return result;
         });
   }

   private static final class Run {
      final List<Integer> keys = new ArrayList<>();
      String executedSql;
      List<String> warnings;
   }

   private static final ThreadLocal<String> executed = new ThreadLocal<>();
   private static ListAppender<ILoggingEvent> appender;
}
