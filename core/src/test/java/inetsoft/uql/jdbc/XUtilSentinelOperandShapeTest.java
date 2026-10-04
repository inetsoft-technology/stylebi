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
 * Bug #77707 and #77709, the sentinel operand shapes run through {@link JDBCHandler} on
 * Derby, as a worksheet query whose sql string is cleared by the merge runs them.
 * <ul>
 *    <li>#77707: a right operand that only starts with a parameter was replaced as if it were
 *    the parameter, so LIKE $(p) ESCAPE '!' must still be rewritten while compound
 *    operands are kept (XUtilSentinelOperandTest).</li>
 *    <li>#77709: a string literal that is only the parameter, '$(p)', and the subject of a
 *    BETWEEN were bound as the text of the sentinel.</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  XUtilSentinelOperandShapeTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XUtilSentinelOperandShapeTest {
   private static final String DB = "memory:bug77709";
   private static final String NULL_VALUE = XConstants.CONDITION_NULL_VALUE;
   private static final String EMPTY_STRING = XConstants.CONDITION_EMPTY_STRING;
   private static final String NULL_STRING = XConstants.CONDITION_NULL_STRING;
   private static final String SELECT = "select t.id from t where ";
   private static final List<Integer> ALL = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9);
   private static final List<Integer> NOT_NULL = List.of(1, 2, 4, 5, 7, 8, 9);

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

   // where clause, the value of p, the expected ids
   static Stream<Arguments> cases() {
      return Stream.of(
         // Bug #77707, the escape clause is part of the right operand of a LIKE
         Arguments.of("t.name like $(p) escape '!'", NULL_VALUE, List.of(3, 6)),
         Arguments.of("t.name like $(p) escape '!'", EMPTY_STRING, List.of(2)),
         Arguments.of("t.name like $(p) escape '!'", NULL_STRING, List.of(4)),
         Arguments.of("t.name like $(p) ESCAPE '\\'", EMPTY_STRING, List.of(2)),
         Arguments.of("t.name not like $(p) escape '!'", NULL_VALUE, NOT_NULL),
         Arguments.of("t.name like $(p) escape '!'", "x%", List.of(8, 9)),
         // Bug #77709, a string literal that is only the parameter
         Arguments.of("t.name = '$(p)'", NULL_VALUE, List.of(3, 6)),
         Arguments.of("t.name = '$(p)'", EMPTY_STRING, List.of(2)),
         Arguments.of("t.name = '$(p)'", NULL_STRING, List.of(4)),
         Arguments.of("t.name = '$(p)'", "n1", List.of(1)),
         Arguments.of("t.name = '$(p)'", null, ALL),
         Arguments.of("t.name <> '$(p)'", NULL_VALUE, NOT_NULL),
         Arguments.of("t.name <> '$(p)'", EMPTY_STRING, List.of(1, 4, 5, 7, 8, 9)),
         Arguments.of("'$(p)' = t.name", EMPTY_STRING, List.of(2)),
         Arguments.of("'$(p)' <> t.name", NULL_VALUE, NOT_NULL),
         Arguments.of("t.name in ('$(p)')", EMPTY_STRING, List.of(2)),
         Arguments.of("t.name not in ('$(p)')", NULL_VALUE, NOT_NULL),
         Arguments.of("t.name = '$(@p)'", EMPTY_STRING, List.of(2)),
         Arguments.of("t.name like '$(p)'", NULL_STRING, List.of(4)),
         // a string literal bound of a BETWEEN
         Arguments.of("t.name between '$(p)' and 'm'", EMPTY_STRING, List.of(2, 5, 7)),
         Arguments.of("t.name between '$(p)' and 'm'", NULL_VALUE, List.of(3, 6)),
         Arguments.of("t.name between 'a' and '$(p)'", NULL_STRING, List.of(1, 4)),
         // the subject of a BETWEEN
         Arguments.of("$(p) between t.name and 'zzz'", EMPTY_STRING, List.of(2)),
         Arguments.of("$(p) between t.name and 'zzz'", NULL_STRING, List.of(1, 2, 4, 5, 7)),
         Arguments.of("$(p) not between t.name and 'zzz'", EMPTY_STRING,
                      List.of(1, 4, 5, 7, 8, 9)),
         Arguments.of("'$(p)' between t.name and 'zzz'", EMPTY_STRING, List.of(2)));
   }

   @ParameterizedTest
   @MethodSource("cases")
   void sameRows(String where, String p, List<Integer> expected) throws Exception {
      Run run = execute(newQuery(SELECT + where), p);
      assertEquals(expected, run.keys, run.executedSql);

      if(NULL_VALUE.equals(p) || EMPTY_STRING.equals(p) || NULL_STRING.equals(p)) {
         assertFalse(run.executedSql.contains("?"), "parameter left in " + run.executedSql);
         assertFalse(run.executedSql.contains(p), "sentinel bound in " + run.executedSql);
      }
   }

   // sentinel, plain value, sentinel on the same query and on clones of it
   @Test
   void runSequence() throws Exception {
      String[] wheres = { "t.name = '$(p)'", "t.name in ('$(p)')",
                          "$(p) between t.name and 'zzz'", "t.name like $(p) escape '!'" };

      for(String where : wheres) {
         JDBCQuery query = newQuery(SELECT + where);

         for(String p : new String[] { EMPTY_STRING, "n1", EMPTY_STRING, null, NULL_VALUE }) {
            List<Integer> expected = execute(newQuery(SELECT + where), p).keys;
            assertEquals(expected, execute(query, p).keys, where + " p=" + p);
            assertEquals(expected, execute((JDBCQuery) query.clone(), p).keys,
                         where + " clone p=" + p);
         }

         String condition = ((UniformSQL) query.getSQLDefinition()).getWhere().toString();
         assertTrue(condition.contains("$(p)"), condition);
      }
   }

   private static JDBCQuery newQuery(String sql) throws Exception {
      UniformSQL usql = new UniformSQL();
      usql.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sql);
      // as the worksheet merge does, otherwise validation skips the query
      usql.clearSQLString();

      JDBCQuery query = new JDBCQuery();
      query.setName("bug77709");
      query.setDataSource(dataSource());
      query.setSQLDefinition(usql);
      return query;
   }

   private static Run execute(JDBCQuery query, String p) throws Exception {
      VariableTable vars = new VariableTable();

      if(p != null) {
         vars.put("p", p);
      }

      JDBCHandler handler = new JDBCHandler();
      handler.connect(query.getDataSource(), vars);
      executed.set(null);
      XNode node = handler.execute(query, vars, null, null);

      Run run = new Run();
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
      ds.setName("bug77709");
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
         XUtilSentinelOperandShapeTest.class.getClassLoader(), new Class<?>[] { type },
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
   }

   private static final ThreadLocal<String> executed = new ThreadLocal<>();
}
