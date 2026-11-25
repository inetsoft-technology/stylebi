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

import inetsoft.report.TableLens;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #82305. ClickHouse fills the side of an outer join with no match with type defaults
 * unless join_use_nulls = 1. A statement holding an outer join StyleBI created gets the
 * setting appended right before it runs; user-written joins keep the server's setting, and
 * a server that rejects the setting (readonly user) runs the statement without it.
 *
 * The JDBCHandler cases run the real {@link JDBCHandler#execute} on embedded Derby with a
 * data source whose product is ClickHouse. The connection records the text that reaches it
 * and strips the setting before Derby runs it, or rejects it like a ClickHouse server would.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  ClickhouseJoinUseNullsTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ClickhouseJoinUseNullsTest {
   private static final String DB = "memory:bug82305";
   private static final String SUFFIX = "\nSETTINGS join_use_nulls = 1";
   private static final String USER_LEFT_JOIN =
      "select a.A, b.A from T a left join T2 b on a.A = b.A where b.A = 0";

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

      // the pool returns a recording Derby data source directly
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

      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   @BeforeEach
   void createTables() throws Exception {
      executed.clear();
      reject = null;

      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         for(String table : new String[] { "T", "T2" }) {
            try {
               stmt.executeUpdate("drop table " + table);
            }
            catch(Exception ignore) {
               // first run
            }

            stmt.executeUpdate("create table " + table + " (A INT, B VARCHAR(10))");
         }

         stmt.executeUpdate("insert into T values (1, 'x'), (2, 'y')");
         stmt.executeUpdate("insert into T2 values (2, 'z')");
      }
   }

   @AfterEach
   void clearRecording() {
      executed.clear();
      reject = null;
   }

   // ---- the helper and the structural walk ----------------------------------------------

   @ParameterizedTest
   @ValueSource(strings = { "*=", "=*", "*=*" })
   void mergedOuterJoinGetsTheSetting(String op) throws Exception {
      assertEquals(SUFFIX, clickhouse().getExecutionSuffix(merged(ch(), op)));
   }

   @Test
   void mergedInnerJoinGetsNoSetting() throws Exception {
      assertNull(clickhouse().getExecutionSuffix(merged(ch(), "=")));
   }

   // a user's own left join keeps ClickHouse semantics, also after VPM or the sentinel
   // rewrite cleared its sql string and the text is generated from the parsed structure
   @Test
   void parsedUserOuterJoinGetsNoSettingAfterItsStringIsCleared() throws Exception {
      UniformSQL user = parse(ch(), USER_LEFT_JOIN);
      XJoin[] joins = user.getJoins();
      assertNotNull(joins);
      assertTrue(joins[0].isOuterJoin());
      assertNotEquals(XJoin.UNKNOWN_CLAUSE, joins[0].getJoinClause());

      UniformSQL cleared = (UniformSQL) user.clone();
      cleared.clearSQLString();
      assertFalse(cleared.hasSQLString());
      assertTrue(cleared.toString().toUpperCase().contains("LEFT OUTER JOIN"), cleared.toString());
      assertNull(clickhouse().getExecutionSuffix(cleared));

      UniformSQL nulled = (UniformSQL) user.clone();
      nulled.setSQLString(null);
      assertNull(clickhouse().getExecutionSuffix(nulled));
   }

   // a generated inner join around a user's derived table that has its own LEFT JOIN
   @Test
   void generatedInnerJoinAroundUserLeftJoinGetsNoSetting() throws Exception {
      for(UniformSQL userTable : new UniformSQL[] {
         parse(ch(), USER_LEFT_JOIN), new UniformSQL(USER_LEFT_JOIN, false) })
      {
         UniformSQL top = join(ch(), userTable, parse(ch(), "select T2.A from T2"), "=");
         assertNull(clickhouse().getExecutionSuffix(top));
      }
   }

   // a merged outer join used as a derived table of another merged join
   @Test
   void nestedMergedOuterJoinGetsTheSetting() throws Exception {
      UniformSQL top = join(ch(), merged(ch(), "*="), parse(ch(), "select T2.A from T2"), "=");
      assertEquals(SUFFIX, clickhouse().getExecutionSuffix(top));
   }

   // the text a ConcatenatedQuery merges carries a hint for the joins it was written from
   @Test
   void hintedTextTableGetsTheSetting() throws Exception {
      UniformSQL text = new UniformSQL("(select 1 as A) UNION ALL (select 2 as A)", false);
      UniformSQL top = new UniformSQL();
      top.setDataSource(ch());
      top.addTable("U", text);
      top.getSelection().addColumn("U.A");
      assertNull(clickhouse().getExecutionSuffix(top));

      text.setHint(UniformSQL.HINT_GENERATED_OUTER_JOIN, true);
      assertEquals(SUFFIX, clickhouse().getExecutionSuffix(top));
   }

   @Test
   void otherDialectsGetNoSetting() throws Exception {
      UniformSQL sql = merged(ch(), "*=");

      for(String product : new String[] { "postgresql", "derby", "mysql" }) {
         assertNull(SQLHelper.getSQLHelper(product).getExecutionSuffix(sql), product);
         assertFalse(SQLHelper.getSQLHelper(product).isExecutionSuffixRejected(readonly()));
      }

      assertNull(new SQLHelper().getExecutionSuffix(sql));
      assertNull(clickhouse().getExecutionSuffix(null));
   }

   @Test
   void rejectionIsRecognized() {
      SQLHelper helper = clickhouse();
      assertTrue(helper.isExecutionSuffixRejected(readonly()));
      assertTrue(helper.isExecutionSuffixRejected(
         new SQLException("Code: 452. DB::Exception: Setting join_use_nulls should not be " +
                          "changed. (SETTING_CONSTRAINT_VIOLATION)", "HY000", 452)));
      assertTrue(helper.isExecutionSuffixRejected(nullable()));
      assertTrue(helper.isExecutionSuffixRejected(
         new SQLException("Cannot modify 'join_use_nulls' setting")));
      assertFalse(helper.isExecutionSuffixRejected(unrelated()));
      assertFalse(helper.isExecutionSuffixRejected(new SQLException((String) null)));
   }

   // ---- JDBCHandler.execute ------------------------------------------------------------

   @Test
   void handlerAppendsTheSettingToAMergedLeftJoin() throws Exception {
      TableLens table = execute(merged(chOnDerby(), "*="));

      assertEquals(1, executed.size(), executed.toString());
      assertTrue(executed.get(0).endsWith(SUFFIX), executed.get(0));
      assertEquals(1, executed.get(0).split("SETTINGS", -1).length - 1, executed.get(0));
      assertEquals(3, rowCount(table), "header plus both rows of T");
   }

   @Test
   void handlerRunsWithoutTheSettingWhenReadonlyRejectsIt() throws Exception {
      reject = readonly();
      TableLens table = execute(merged(chOnDerby(), "*="));

      assertEquals(2, executed.size(), executed.toString());
      assertTrue(executed.get(0).endsWith(SUFFIX), executed.get(0));
      assertFalse(executed.get(1).contains("SETTINGS"), executed.get(1));
      assertEquals(executed.get(0), executed.get(1) + SUFFIX);
      assertEquals(3, rowCount(table), "header plus both rows of T");
   }

   @Test
   void handlerRunsWithoutTheSettingWhenAColumnCantBeNullable() throws Exception {
      reject = nullable();
      TableLens table = execute(merged(chOnDerby(), "*="));

      assertEquals(2, executed.size(), executed.toString());
      assertFalse(executed.get(1).contains("SETTINGS"), executed.get(1));
      assertEquals(3, rowCount(table), "header plus both rows of T");
   }

   @Test
   void handlerDoesNotRetryAnUnrelatedError() throws Exception {
      reject = unrelated();

      assertThrows(Exception.class, () -> execute(merged(chOnDerby(), "*=")));
      assertEquals(1, executed.size(), executed.toString());
   }

   @Test
   void handlerRunsKeptUserSqlAsWritten() throws Exception {
      UniformSQL unparsed = new UniformSQL();
      unparsed.setDataSource(chOnDerby());
      unparsed.setParseSQL(false);
      unparsed.setSQLString(USER_LEFT_JOIN, false);
      execute(unparsed);
      assertEquals(List.of(USER_LEFT_JOIN), executed);

      executed.clear();
      UniformSQL parsedKept = parse(chOnDerby(), USER_LEFT_JOIN);
      assertTrue(parsedKept.hasSQLString());
      execute(parsedKept);
      assertEquals(1, executed.size(), executed.toString());
      assertFalse(executed.get(0).contains("SETTINGS"), executed.get(0));
   }

   @Test
   void handlerAddsNothingToAClearedUserLeftJoin() throws Exception {
      UniformSQL user = parse(chOnDerby(), USER_LEFT_JOIN);
      user.clearSQLString();
      execute(user);

      assertEquals(1, executed.size(), executed.toString());
      assertTrue(executed.get(0).toUpperCase().contains("LEFT OUTER JOIN"), executed.get(0));
      assertFalse(executed.get(0).contains("SETTINGS"), executed.get(0));
   }

   @Test
   void handlerAddsNothingToAMergedInnerJoin() throws Exception {
      execute(merged(chOnDerby(), "="));

      assertEquals(1, executed.size(), executed.toString());
      assertFalse(executed.get(0).contains("SETTINGS"), executed.get(0));
   }

   @Test
   void handlerAddsNothingForAnotherDialect() throws Exception {
      JDBCDataSource derby = chOnDerby();
      derby.setRuntimeProductName("derby");
      execute(merged(derby, "*="));

      assertEquals(1, executed.size(), executed.toString());
      assertFalse(executed.get(0).contains("SETTINGS"), executed.get(0));
   }

   // ---- helpers ----------------------------------------------------------------------

   private static SQLHelper clickhouse() {
      SQLHelper helper = SQLHelper.getSQLHelper("clickhouse");
      assertInstanceOf(ClickhouseHelper.class, helper);
      return helper;
   }

   private static SQLException readonly() {
      return new SQLException("Code: 164. DB::Exception: Cannot modify 'join_use_nulls' " +
                              "setting in readonly mode. (READONLY)", "HY000", 164);
   }

   private static SQLException nullable() {
      return new SQLException("Code: 43. DB::Exception: Nested type Array(String) cannot be " +
                              "inside Nullable type. (ILLEGAL_TYPE_OF_ARGUMENT)", "HY000", 43);
   }

   private static SQLException unrelated() {
      return new SQLException("Code: 60. DB::Exception: Table default.x doesn't exist. " +
                              "(UNKNOWN_TABLE)", "HY000", 60);
   }

   /**
    * A worksheet join of two bound tables as JoinQuery.mergeFrom writes it: each side is a
    * derived table and the join is an XJoin StyleBI created.
    */
   private static UniformSQL merged(JDBCDataSource ds, String op) throws Exception {
      return join(ds, parse(ds, "select T.A, T.B from T"), parse(ds, "select T2.A from T2"),
                  op);
   }

   private static UniformSQL join(JDBCDataSource ds, UniformSQL left, UniformSQL right,
                                  String op)
   {
      UniformSQL top = new UniformSQL();
      top.setDataSource(ds);
      left.setSubQuery(true);
      right.setSubQuery(true);
      top.addTable("PA", left);
      top.addTable("PB", right);
      top.getSelection().addColumn("PA.A");
      top.getSelection().addColumn("PB.A");
      XJoin join = new XJoin(new XExpression("PA.A", XExpression.EXPRESSION),
                             new XExpression("PB.A", XExpression.EXPRESSION), op);
      join.setTable1("PA");
      join.setTable2("PB");
      top.addJoin(join);
      return top;
   }

   private static UniformSQL parse(JDBCDataSource ds, String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.setParseSQL(true);

      synchronized(sql) {
         sql.setSQLString(text);
         sql.wait(20000);
      }

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      return sql;
   }

   private static JDBCDataSource ch() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug82305-ch");
      ds.setDriver("com.clickhouse.jdbc.ClickHouseDriver");
      ds.setURL("jdbc:clickhouse://localhost:8123/default");
      ds.setRuntimeProductName("clickhouse");
      return ds;
   }

   /**
    * A ClickHouse data source whose connections the test pool serves from Derby.
    */
   private static JDBCDataSource chOnDerby() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug82305");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      ds.setRuntimeProductName("clickhouse");
      return ds;
   }

   private static XNodeTableLens execute(UniformSQL usql) throws Exception {
      JDBCQuery query = new JDBCQuery();
      query.setName("bug82305");
      query.setDataSource(usql.getDataSource());
      query.setSQLDefinition(usql);
      VariableTable vars = new VariableTable();
      JDBCHandler handler = new JDBCHandler();
      handler.connect(query.getDataSource(), vars);
      return new XNodeTableLens(handler.execute(query, vars, null, null));
   }

   private static int rowCount(TableLens table) {
      table.moreRows(Integer.MAX_VALUE);
      return table.getRowCount();
   }

   private static DataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }

   private static DataSource recording(DataSource ds) {
      return proxy(DataSource.class, ds);
   }

   /**
    * Records each select that reaches the connection. A text with the setting is rejected
    * with {@link #reject} if set, else the setting is removed before Derby runs it.
    */
   @SuppressWarnings("unchecked")
   private static <T> T proxy(Class<T> type, T target) {
      return (T) Proxy.newProxyInstance(
         ClickhouseJoinUseNullsTest.class.getClassLoader(), new Class<?>[] { type },
         (p, method, args) -> {
            String name = method.getName();

            if(args != null && args.length > 0 && args[0] instanceof String &&
               (name.startsWith("prepare") || name.startsWith("execute")) &&
               ((String) args[0]).trim().toLowerCase().startsWith("select"))
            {
               String text = (String) args[0];

               // createStatement + execute(text) is the path without bound parameters
               if(!name.startsWith("prepare")) {
                  executed.add(text);
               }

               if(text.endsWith(SUFFIX)) {
                  if(reject != null && !name.startsWith("prepare")) {
                     throw reject;
                  }

                  args = args.clone();
                  args[0] = text.substring(0, text.length() - SUFFIX.length());
               }
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

   private static final List<String> executed = new ArrayList<>();
   private static volatile SQLException reject;
}
