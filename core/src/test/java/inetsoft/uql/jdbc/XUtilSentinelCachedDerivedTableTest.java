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
 * Bug #77574. A NULL_VALUE/EMPTY_STRING parameter rewrite in the HAVING of a FROM derived
 * table is not reported by XUtil.validateConditions, so a cacheable derived table above it
 * (as JoinQuery/MirrorQuery merge in) kept its cached sql string with the parameter in it,
 * and {@link JDBCHandler#execute} bound the sentinel string to it.
 *
 * Every case builds a fresh query: validation rewrites the derived tables of the caller's
 * query in place (#77606), so a reused query would carry an earlier rewrite.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  XUtilSentinelCachedDerivedTableTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XUtilSentinelCachedDerivedTableTest {
   private static final String DB = "memory:bug77574";
   private static final String NULL_VALUE = XConstants.CONDITION_NULL_VALUE;
   private static final String EMPTY_STRING = XConstants.CONDITION_EMPTY_STRING;
   // k = 3 is the only group whose max(a.id) is null, min(a.name) is '' in every group
   private static final String NULL_VALUE_INNER =
      "select a.k, max(a.id) m from a group by a.k having count(*) > 1 and max(a.id) = $(p)";
   private static final String EMPTY_STRING_INNER =
      "select a.k, min(a.name) m from a group by a.k having count(*) > 1 and min(a.name) = $(p)";
   // a bare HAVING condition, not an AND
   private static final String BARE_INNER =
      "select a.k, max(a.id) m from a group by a.k having max(a.id) = $(p)";

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

      // the DriverManager in this JVM trips over another driver's static init, so the pool
      // returns a Derby data source directly
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
            stmt.executeUpdate("drop table a");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table a (id int, k int, name varchar(20))");
         stmt.executeUpdate("insert into a values (1, 1, 'n1'), (null, 1, ''), (3, 2, null), " +
                               "(null, 2, 'null'), (5, 1, 'null'), (6, 1, null), " +
                               "(null, 3, ''), (null, 3, ''), (9, 2, '')");
      }
   }

   // inner sql, parameter, depth of the rewritten derived table, expected k
   static Stream<Arguments> cases() {
      return Stream.of(
         Arguments.of(NULL_VALUE_INNER, NULL_VALUE, 1, List.of(3)),
         Arguments.of(NULL_VALUE_INNER, NULL_VALUE, 2, List.of(3)),
         Arguments.of(NULL_VALUE_INNER, NULL_VALUE, 3, List.of(3)),
         Arguments.of(EMPTY_STRING_INNER, EMPTY_STRING, 1, List.of(1, 2, 3)),
         Arguments.of(EMPTY_STRING_INNER, EMPTY_STRING, 2, List.of(1, 2, 3)),
         Arguments.of(EMPTY_STRING_INNER, EMPTY_STRING, 3, List.of(1, 2, 3)),
         Arguments.of(BARE_INNER, NULL_VALUE, 2, List.of(3)));
   }

   // the sql string of the query was read before execution, which fills the cache of every
   // cacheable derived table
   @ParameterizedTest
   @MethodSource("cases")
   void readBeforeExecution(String inner, String p, int depth, List<Integer> expected)
      throws Exception
   {
      JDBCQuery query = newQuery(inner, depth);
      assertTrue(query.getSQLAsString().contains("$(p)"), query.getSQLAsString());

      assertRun(execute(query, p), expected);
   }

   // an earlier execution of the same query with a plain value fills the shared cache of the
   // derived tables, JDBCHandler only works on a shallow clone. The plain value rewrites
   // nothing, so the second run is not affected by #77606.
   @ParameterizedTest
   @MethodSource("cases")
   void plainValueThenSentinel(String inner, String p, int depth, List<Integer> expected)
      throws Exception
   {
      JDBCQuery query = newQuery(inner, depth);
      Run first = execute(query, NULL_VALUE.equals(p) ? "6" : "n1");
      assertEquals(NULL_VALUE.equals(p) ? List.of(1) : List.of(), first.keys,
                   first.executedSql);

      assertRun(execute(query, p), expected);
   }

   // no cached string, the rewrite was always correct
   @ParameterizedTest
   @MethodSource("cases")
   void notReadBeforeExecution(String inner, String p, int depth, List<Integer> expected)
      throws Exception
   {
      assertRun(execute(newQuery(inner, depth), p), expected);
   }

   private static void assertRun(Run run, List<Integer> expected) {
      assertFalse(run.executedSql.contains("?"), "parameter left in " + run.executedSql);
      assertEquals(expected, run.keys, run.executedSql);
   }

   /**
    * Wrap the inner sql in depth - 1 more derived tables. Every derived table is cacheable,
    * as JoinQuery.mergeFrom and MirrorQuery.mergeFrom mark the sub-query they add to FROM.
    */
   private static JDBCQuery newQuery(String inner, int depth) throws Exception {
      String sql = inner;
      String column = "k";

      for(int i = 0; i < depth; i++) {
         String alias = "t" + i;
         sql = "select " + alias + "." + column + " from (" + sql + ") " + alias;
      }

      UniformSQL usql = new UniformSQL();
      usql.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      usql.setSQLString(sql, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sql);
      usql.clearSQLString();

      UniformSQL level = usql;

      for(int i = 0; i < depth; i++) {
         level = (UniformSQL) level.getSelectTable()[0].getName();
         level.setSubQuery(true);
         level.setCacheable(true);
      }

      JDBCQuery query = new JDBCQuery();
      query.setName("bug77574");
      query.setDataSource(dataSource());
      query.setSQLDefinition(usql);
      return query;
   }

   private static Run execute(JDBCQuery query, String p) throws Exception {
      VariableTable vars = new VariableTable();
      vars.put("p", p);

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
      ds.setName("bug77574");
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
         XUtilSentinelCachedDerivedTableTest.class.getClassLoader(), new Class<?>[] { type },
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
