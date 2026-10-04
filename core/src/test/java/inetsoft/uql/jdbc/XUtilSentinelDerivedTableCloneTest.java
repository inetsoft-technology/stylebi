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
 * Bug #77606. {@link UniformSQL#clone()} shared the {@link UniformSQL} of a FROM derived
 * table with the original, so the in-place rewrites {@link JDBCHandler#execute} makes on its
 * private clone (sentinel conditions, conditions without a parameter value) changed the
 * caller's query, and a later run with another value still ran the first rewrite.
 *
 * The outer sql string is cleared, as the worksheet merge does, because
 * XUtil.validateConditions does not look into a query that still has its sql string.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  XUtilSentinelDerivedTableCloneTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XUtilSentinelDerivedTableCloneTest {
   private static final String DB = "memory:bug77606";
   private static final String EMPTY_STRING = XConstants.CONDITION_EMPTY_STRING;
   // min(a.name) is '' in every group
   private static final String HAVING_INNER =
      "select a.k, min(a.name) m from a group by a.k having count(*) > 1 and min(a.name) = $(p)";
   // a bare HAVING condition, not an AND
   private static final String BARE_HAVING_INNER =
      "select a.k, min(a.name) m from a group by a.k having min(a.name) = $(p)";
   private static final String WHERE_INNER = "select a.k from a where a.name = $(p)";

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

   // inner sql, depth of the derived table holding the parameter
   static Stream<Arguments> cases() {
      return Stream.of(
         Arguments.of(HAVING_INNER, 1), Arguments.of(HAVING_INNER, 2),
         Arguments.of(BARE_HAVING_INNER, 1), Arguments.of(BARE_HAVING_INNER, 2),
         Arguments.of(WHERE_INNER, 1), Arguments.of(WHERE_INNER, 2));
   }

   @Test
   void cloneOwnsItsDerivedTables() throws Exception {
      JDBCQuery query = newQuery(HAVING_INNER, 2);
      JDBCQuery clone = (JDBCQuery) query.clone();
      UniformSQL derived = derived(query, 1);
      UniformSQL derived2 = derived(query, 2);
      String having = derived2.getHaving().toString();
      assertTrue(having.contains("$(p)"), having);

      assertNotSame(derived, derived(clone, 1));
      assertNotSame(derived2, derived(clone, 2));

      clone.validateConditions(vars(EMPTY_STRING));
      assertEquals(having, derived2.getHaving().toString());
      assertNotEquals(having, derived(clone, 2).getHaving().toString());
   }

   // the rows a fresh query returns, which the run sequences below are compared with
   @Test
   void freshQueryRows() throws Exception {
      assertEquals(List.of(1, 2, 3), execute(newQuery(HAVING_INNER, 1), EMPTY_STRING).keys);
      assertEquals(List.of(), execute(newQuery(HAVING_INNER, 1), "n1").keys);
      assertEquals(List.of(1, 2, 3), execute(newQuery(HAVING_INNER, 1), null).keys);
      assertEquals(List.of(1), execute(newQuery(WHERE_INNER, 1), "n1").keys);
   }

   // sentinel, plain value, sentinel on the same query
   @ParameterizedTest
   @MethodSource("cases")
   void sentinelThenValueSameQuery(String inner, int depth) throws Exception {
      JDBCQuery query = newQuery(inner, depth);

      for(String p : new String[] { EMPTY_STRING, "n1", EMPTY_STRING }) {
         assertRun(execute(query, p), p, execute(newQuery(inner, depth), p).keys);
      }

      String derived = derived(query, depth).toString();
      assertTrue(derived.contains("$(p)"), derived);
   }

   // every run works on its own clone of a common original, as a worksheet clone does
   @ParameterizedTest
   @MethodSource("cases")
   void sentinelThenValueOnClones(String inner, int depth) throws Exception {
      JDBCQuery query = newQuery(inner, depth);

      for(String p : new String[] { EMPTY_STRING, "n1", EMPTY_STRING }) {
         assertRun(execute((JDBCQuery) query.clone(), p), p,
                   execute(newQuery(inner, depth), p).keys);
      }
   }

   // an unset parameter removes the condition, which must not stay removed
   @ParameterizedTest
   @MethodSource("cases")
   void unsetThenValue(String inner, int depth) throws Exception {
      JDBCQuery query = newQuery(inner, depth);

      for(String p : new String[] { null, "n1" }) {
         assertRun(execute(query, p), p, execute(newQuery(inner, depth), p).keys);
      }
   }

   private static void assertRun(Run run, String p, List<Integer> expected) {
      // VarSQL replaces a $(p) left in the sql with a ? bound to the value, which is right
      // for a plain value only
      if(EMPTY_STRING.equals(p)) {
         assertFalse(run.executedSql.contains("?"), "parameter left in " + run.executedSql);
      }

      assertEquals(expected, run.keys, run.executedSql);
   }

   private static UniformSQL derived(JDBCQuery query, int depth) {
      UniformSQL level = (UniformSQL) query.getSQLDefinition();

      for(int i = 0; i < depth; i++) {
         level = (UniformSQL) level.getSelectTable()[0].getName();
      }

      return level;
   }

   private static JDBCQuery newQuery(String inner, int depth) throws Exception {
      String sql = inner;

      for(int i = 0; i < depth; i++) {
         String alias = "t" + i;
         sql = "select " + alias + ".k from (" + sql + ") " + alias;
      }

      UniformSQL usql = new UniformSQL();
      usql.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      usql.setSQLString(sql, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sql);
      // as the worksheet merge does, otherwise validation skips the query
      usql.clearSQLString();

      JDBCQuery query = new JDBCQuery();
      query.setName("bug77606");
      query.setDataSource(dataSource());
      query.setSQLDefinition(usql);
      return query;
   }

   private static VariableTable vars(String p) {
      VariableTable vars = new VariableTable();

      if(p != null) {
         vars.put("p", p);
      }

      return vars;
   }

   private static Run execute(JDBCQuery query, String p) throws Exception {
      VariableTable vars = vars(p);
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
      ds.setName("bug77606");
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
         XUtilSentinelDerivedTableCloneTest.class.getClassLoader(), new Class<?>[] { type },
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
