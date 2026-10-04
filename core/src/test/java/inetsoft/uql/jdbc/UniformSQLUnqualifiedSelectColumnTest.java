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
import inetsoft.report.XSessionManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Drivers;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.Plugins;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.io.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77639. Without the metadata of a table, {@link JDBCUtil#fixUniformSQLInfo} builds the
 * field list from the select list, so a select column t.k is a field named t.k without a
 * table. {@link UniformSQL#syncTable()} then didn't find an unqualified ORDER BY or GROUP BY
 * name (k) and dropped it: the rows came back unsorted, and a query that lost its GROUP BY
 * failed on Derby (a lax database would return one row).
 *
 * The name is resolved to the select column only if the from clause has one table (or
 * derived table), with no known column, and one plain column of it of that name is selected
 * with the same quoting. Rows are compared with the original sql run directly on Derby,
 * through the real {@link XSessionManager#getXNodeTableLens} and {@link JDBCHandler}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  UniformSQLUnqualifiedSelectColumnTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLUnqualifiedSelectColumnTest {
   private static final String DB = "memory:bug77639";

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

   @AfterEach
   void tearDownSessions() {
      for(XSessionManager session : sessions) {
         session.tearDown();
      }

      sessions.clear();
   }

   @BeforeEach
   void createTables() throws Exception {
      // the table metadata is cached by data source name, use a new name for each case
      derbyName = "bug77639_" + (++sources) + "_" + RUN;

      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         for(String table : new String[] { "T", "G" }) {
            try {
               stmt.executeUpdate("drop table " + table);
            }
            catch(Exception ignore) {
               // first run
            }
         }

         stmt.executeUpdate("create table T (A INT, B VARCHAR(10))");
         // B sorts opposite to A, so ordering by the wrong column reverses the rows
         stmt.executeUpdate("insert into T values (1, 'z'), (2, 'y'), (3, 'x')");
         stmt.executeUpdate("create table G (K VARCHAR(10), ID INT)");
         // two groups, one row per group only with the group by
         stmt.executeUpdate("insert into G values ('a', 1), ('a', 2), ('b', 3)");
      }
   }

   private static final String[] HELPERS = { "h2", "oracle", "postgresql", "snowflake", "exasol" };

   // the reported shape, on every helper: the trailing k was dropped
   @Test
   void reportedOrderByNameIsKept() throws Exception {
      for(String key : HELPERS) {
         UniformSQL usql = fixed("select t.k, t.k, t.id from t order by 3 desc, k", key);
         String generated = norm(regenerate(usql)).replace("\"", "");

         assertEquals(2, usql.getOrderByItems().length, key + " " + orderBy(usql));
         assertTrue(generated.endsWith("order by t.id desc, t.k asc"), key + " " + generated);
         assertRoundTrip(usql, key);
      }
   }

   @Test
   void groupByNameIsKept() throws Exception {
      for(String key : HELPERS) {
         UniformSQL usql = fixed("select t.k, count(*) from t group by k", key);
         String generated = norm(regenerate(usql)).replace("\"", "");

         assertTrue(generated.endsWith(" group by t.k"), key + " " + generated);
         assertRoundTrip(usql, key);
      }
   }

   @Test
   void orderByNameSortsOnDerby() throws Exception {
      for(String sql : new String[] {
         "select T.A, T.B from T order by B",
         "select T.A, T.B from T order by b desc",
         "select x.A, x.B from T x order by B",
         "select T.A, T.\"B\" from T order by \"B\"" })
      {
         UniformSQL usql = fixedWithoutMeta(sql);

         assertEquals(1, usql.getOrderByItems().length, sql + " " + orderBy(usql));
         assertSameRows(sql, usql, true);
      }
   }

   // the query without its group by failed on Derby
   @Test
   void groupByNameGroupsOnDerby() throws Exception {
      for(String sql : new String[] {
         "select G.K, count(*) from G group by K",
         "select g.K, count(*) from G g group by k" })
      {
         UniformSQL usql = fixedWithoutMeta(sql);

         assertEquals(1, usql.getGroupBy().length, sql);
         assertSameRows(sql, usql, false);
      }

      String sql = "select G.K, count(*) from G group by K order by K desc";
      UniformSQL usql = fixedWithoutMeta(sql);

      assertEquals("[G.K:desc]", orderBy(usql));
      assertSameRows(sql, usql, true);
   }

   // a derived table is the only from item, its select list names the field t.k under x
   @Test
   void derivedTableName() throws Exception {
      String sql = "select x.B, x.A from (select T.B, T.A from T) x order by B";
      UniformSQL usql = fixedWithoutMeta(sql);

      assertEquals("[x.B:asc]", orderBy(usql));
      assertSameRows(sql, usql, true);

      sql = "select x.K, count(*) from (select G.K from G) x group by K";
      usql = fixedWithoutMeta(sql);

      assertEquals("[x.K]", Arrays.toString(usql.getGroupBy()));
      assertSameRows(sql, usql, false);

      for(String key : HELPERS) {
         usql = fixed("select x.k from (select t.k from t) x order by k", key);
         String generated = norm(regenerate(usql)).replace("\"", "");

         assertTrue(generated.endsWith("order by x.k asc"), key + " " + generated);
      }
   }

   // the qualifier of the select column is the whole table name
   @Test
   void schemaQualifiedTable() throws Exception {
      String sql = "select APP.T.A, APP.T.B from APP.T order by B";
      UniformSQL usql = fixedWithoutMeta(sql);

      assertEquals("[APP.T.B:asc]", orderBy(usql));
      assertSameRows(sql, usql, true);

      for(String key : HELPERS) {
         usql = fixed("select s.t.k, s.t.id from s.t order by k", key);

         assertEquals(1, usql.getOrderByItems().length, key);
         assertTrue(norm(orderBy(usql)).replace("\"", "").startsWith("[s.t.k:"),
                    key + " " + orderBy(usql));
      }
   }

   // a quoted name matches a quoted select column of the same text, which keeps its quotes
   @Test
   void quotedNameKeepsTheQuotingOfItsColumn() throws Exception {
      String generated = regenerate(fixed("select t.id, t.\"k\" from t order by \"k\"", "oracle"));
      assertTrue(generated.endsWith("order by t.\"k\" asc"), generated);

      generated = regenerate(fixed("select t.id, t.\"k\" from t order by \"k\"", "postgresql"));
      assertTrue(generated.endsWith("order by \"t\".\"k\" asc"), generated);

      generated = regenerate(fixed("select t.\"k\", count(*) from t group by \"k\"", "oracle"));
      assertTrue(generated.endsWith("group by t.\"k\""), generated);
   }

   // with more than one from item, a name may be the column of another table
   @Test
   void severalTablesAreNotGuessed() throws Exception {
      for(String key : HELPERS) {
         assertEquals("[]", orderBy(fixed("select t.id, t.k from t, w order by k", key)), key);
         assertEquals("[]", orderBy(fixed("select x.k, y.k from t x, t y order by k", key)), key);
         assertEquals("[t.id]", norm(Arrays.toString(
            fixed("select t.id, t.k from t, w group by t.id, k", key).getGroupBy()))
            .replace("\"", ""), key);
      }
   }

   // an alias of the name in any case is checked first, the name isn't guessed
   @Test
   void aliasOfTheNameIsNotGuessed() throws Exception {
      for(String key : HELPERS) {
         UniformSQL usql = fixed("select t.id as k, t.k from t order by k", key);
         assertFalse(norm(orderBy(usql)).replace("\"", "").contains("t.k"), key + " " + orderBy(usql));

         usql = fixed("select t.id as \"K\", t.k from t order by k", key);
         assertFalse(norm(orderBy(usql)).replace("\"", "").contains("t.k"), key + " " + orderBy(usql));

         usql = fixed("select t.id as \"K\", t.k, count(*) from t group by t.id, k", key);
         assertFalse(norm(Arrays.toString(usql.getGroupBy())).replace("\"", "").contains("t.k"),
                     key + " " + Arrays.toString(usql.getGroupBy()));
      }
   }

   // postgresql folds k to lower case: "K" is another column than t.k, and k another one
   // than t."K"
   @Test
   void otherQuotingIsNotGuessed() throws Exception {
      for(String key : HELPERS) {
         assertEquals("[]", orderBy(fixed("select t.id, t.k from t order by \"K\"", key)), key);
         assertEquals("[]", orderBy(fixed("select t.id, t.\"K\" from t order by k", key)), key);
         assertEquals("[]", orderBy(fixed("select t.id, t.\"k\" from t order by \"K\"", key)), key);
         // two select columns of the name may be one column or two
         assertEquals("[]", orderBy(fixed("select t.k, t.\"K\" from t order by k", key)), key);
      }
   }

   @Test
   void expressionsWildcardsAndOtherQualifiersAreNotGuessed() throws Exception {
      for(String key : HELPERS) {
         assertEquals("[]", orderBy(fixed("select t.k + 1, t.id from t order by k", key)), key);
         assertEquals("[]", orderBy(fixed("select t.*, t.k from t order by k", key)), key);
         assertEquals("[]", orderBy(fixed("select o.k from t order by k", key)), key);
      }
   }

   // a table with metadata finds its columns as before. Snowflake and exasol fold k to K,
   // which isn't the lower case column k, so the item is still dropped, and a column hidden
   // by vpm is left out of the metadata, which isn't guessed either
   @Test
   void tableWithMetadataIsNotGuessed() throws Exception {
      for(String key : new String[] { "snowflake", "exasol" }) {
         assertEquals("[]", orderBy(fixed("select t.k, t.id from t order by k", key, "id", "k")),
                      key);
         assertEquals(0, fixed("select t.k, count(*) from t group by k", key, "id", "k")
            .getGroupBy().length, key);
         // k isn't in the metadata
         assertEquals("[]", orderBy(fixed("select t.k, t.id from t order by k", key, "ID")),
                      key);
      }

      for(String key : new String[] { "h2", "oracle" }) {
         assertEquals("[]", orderBy(fixed("select t.k, t.id from t order by k", key, "ID")), key);
         String generated = norm(regenerate(fixed("select t.id, t.k from t order by k", key,
                                                  "ID", "K")));
         assertTrue(generated.endsWith("order by t.k asc"), key + " " + generated);
      }
   }

   // the regenerated sql parses and regenerates to itself
   private static void assertRoundTrip(UniformSQL usql, String key) throws Exception {
      String generated = usql.getSQLString();
      UniformSQL again = fixed(generated, key);
      assertEquals(norm(regenerate(usql)), norm(regenerate(again)), key);
   }

   // the order by of a query run through XSessionManager, compared with the original sql
   // @param ordered false to compare the rows in any order
   private void assertSameRows(String sql, UniformSQL usql, boolean ordered) throws Exception {
      Run run = run(newSession(), usql);
      List<List<String>> expected = direct(sql);
      List<List<String>> actual = rows(run.table);

      if(!ordered) {
         expected.sort(Comparator.comparing(Object::toString));
         actual.sort(Comparator.comparing(Object::toString));
      }

      assertFalse(expected.isEmpty());
      assertEquals(expected, actual, sql + " executed as " + run.executedSql);
   }

   private static String orderBy(UniformSQL usql) {
      List<String> items = new ArrayList<>();

      for(OrderByItem item : usql.getOrderByItems()) {
         items.add(item.getField() + ":" + item.getOrder());
      }

      return items.toString();
   }

   // the parsed sql, through the metadata step with no table columns (the column fetch
   // failed), as the query editor and a worksheet sql table run it
   private static UniformSQL fixedWithoutMeta(String sql) throws Exception {
      UniformSQL usql = parsed(sql);
      usql.setDataSource(dataSource());
      JDBCUtil.fixUniformSQLInfo(usql, repository(), null, dataSource());
      return usql;
   }

   // the parsed sql, through the metadata step with the columns of every table
   private static UniformSQL fixed(String sql, String helper, String... columns)
      throws Exception
   {
      JDBCDataSource ds = helper(helper);
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(ds);
      usql.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      usql.setSQLString(sql, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sql);
      assertFalse(usql.isLossy(), sql);
      JDBCUtil.fixUniformSQLInfo(usql, repository(columns), null, ds);
      return usql;
   }

   private static UniformSQL parsed(String sql) throws Exception {
      UniformSQL usql = new UniformSQL();
      usql.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      usql.setSQLString(sql, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sql);
      assertFalse(usql.isLossy(), sql);
      return usql;
   }

   private static XRepository repository(String... columns) throws Exception {
      XRepository repository = mock(XRepository.class);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode mtype = inv.getArgument(2);

         if("DBPROPERTIES".equals(mtype.getAttribute("type"))) {
            return new XNode("properties");
         }

         XTypeNode result = new XTypeNode("Result");

         for(String column : columns) {
            result.addChild(XSchema.createPrimitiveType(column, Integer.class));
         }

         XTypeNode meta = new XTypeNode("meta");
         meta.addChild(result);
         return meta;
      });

      return repository;
   }

   // the table metadata is cached by data source name, use a new name each time
   private static JDBCDataSource helper(String key) {
      String[] spec = switch(key) {
         case "h2" -> new String[] { "org.h2.Driver", "jdbc:h2:mem:x" };
         case "oracle" -> new String[] { "oracle.jdbc.OracleDriver",
                                         "jdbc:oracle:thin:@localhost:1521:x" };
         case "postgresql" -> new String[] { "org.postgresql.Driver",
                                             "jdbc:postgresql://localhost/db" };
         case "snowflake" -> new String[] { "net.snowflake.client.jdbc.SnowflakeDriver",
                                            "jdbc:snowflake://x" };
         case "exasol" -> new String[] { "com.exasol.jdbc.EXADriver", "jdbc:exa:x" };
         default -> throw new IllegalArgumentException(key);
      };

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77639" + key + "_" + (++sources) + "_" + RUN);
      ds.setDriver(spec[0]);
      ds.setURL(spec[1]);
      ds.setRuntimeProductName(key);
      // otherwise the mysql and oracle helpers ask the repository for it
      ds.setProductVersion("10.0");
      ds.setAnsiJoin("snowflake".equals(key));
      return ds;
   }

   private static String regenerate(UniformSQL usql) {
      usql.clearSQLString();
      return usql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static String norm(String sql) {
      return sql.replaceAll("\\s+", " ").trim().toLowerCase();
   }

   private static List<List<String>> direct(String sql) throws Exception {
      List<List<String>> rows = new ArrayList<>();

      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery(sql))
      {
         int count = rs.getMetaData().getColumnCount();

         while(rs.next()) {
            List<String> row = new ArrayList<>();

            for(int i = 1; i <= count; i++) {
               row.add(String.valueOf(rs.getObject(i)));
            }

            rows.add(row);
         }
      }

      return rows;
   }

   private static List<List<String>> rows(TableLens table) {
      table.moreRows(Integer.MAX_VALUE);
      List<List<String>> rows = new ArrayList<>();

      for(int r = 1; r < table.getRowCount(); r++) {
         List<String> row = new ArrayList<>();

         for(int c = 0; c < table.getColCount(); c++) {
            row.add(String.valueOf(table.getObject(r, c)));
         }

         rows.add(row);
      }

      return rows;
   }

   private static JDBCQuery newQuery(UniformSQL usql) {
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77639");
      query.setDataSource(dataSource());
      query.setSQLDefinition(usql);
      return query;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName(derbyName);
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
   }

   private static XSessionManager newSession() throws Exception {
      XDataService dataService = mock(XDataService.class);
      when(dataService.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> execute(inv.getArgument(1), inv.getArgument(2),
                                    inv.getArgument(3), inv.getArgument(5)));
      XSessionManager session = new XSessionManager(
         dataService, ConfigurationContext.getContext().getSpringBean(XSessionService.class),
         null);
      session.setCacheData(false);
      sessions.add(session);
      return session;
   }

   private Run run(XSessionManager session, UniformSQL usql) throws Exception {
      JDBCQuery query = newQuery(usql);
      Run run = new Run();
      currentRun.set(run);

      try {
         run.table = session.getXNodeTableLens(query, new VariableTable(), null, null, null, -1);
      }
      finally {
         currentRun.remove();
      }

      assertNotNull(run.table, "query failed, see log");
      return run;
   }

   private static XNode execute(XQuery query, VariableTable vars, java.security.Principal user,
                                inetsoft.util.DataCacheVisitor visitor) throws Exception
   {
      Run run = currentRun.get();
      JDBCHandler handler = new JDBCHandler();
      handler.connect(query.getDataSource(), vars);
      executed.set(null);
      XNode node = handler.execute(query, vars, user, visitor);
      run.executedSql = executed.get();
      return node;
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
         UniformSQLUnqualifiedSelectColumnTest.class.getClassLoader(), new Class<?>[] { type },
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
      TableLens table;
      String executedSql;
   }

   private static final long RUN = System.nanoTime();
   private static int sources;
   private static String derbyName;
   private static final ThreadLocal<String> executed = new ThreadLocal<>();
   private static final ThreadLocal<Run> currentRun = new ThreadLocal<>();
   private static final List<XSessionManager> sessions = new ArrayList<>();
}
