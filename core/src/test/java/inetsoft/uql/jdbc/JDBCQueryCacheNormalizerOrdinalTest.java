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
 * Bug #77557. {@link JDBCQueryCacheNormalizer} sorts the select list of a regenerated query so
 * that queries differing only in column order share a cache entry. An ordinal in ORDER BY or
 * GROUP BY refers to a select list position, so sorting the select list made it refer to
 * another column. A query with an ordinal must not be sorted, and must return the same rows,
 * in the same order, as its original SQL.
 *
 * Queries run through the real {@link XSessionManager#getXNodeTableLens} (normalizer, transform,
 * cache) and {@link JDBCHandler#execute} on embedded Derby, and are compared, as ordered row
 * lists, with the original SQL run directly on Derby.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  JDBCQueryCacheNormalizerOrdinalTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class JDBCQueryCacheNormalizerOrdinalTest {
   private static final String DB = "memory:bug77557";

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

         // B sorts opposite to A, so ordering by the wrong column reverses the rows
         stmt.executeUpdate("insert into T values (1, 'z'), (2, 'y'), (3, 'x')");
         stmt.executeUpdate("insert into T2 values (1, 'p'), (3, 'q')");
      }
   }

   @Test
   void orderByOrdinal() throws Exception {
      assertSameRows("select T.B, T.A from T order by 1", 0);
   }

   @Test
   void orderByOrdinalDesc() throws Exception {
      assertSameRows("select T.B, T.A from T order by 1 desc", 0);
   }

   @Test
   void orderByMultipleOrdinals() throws Exception {
      assertSameRows("select T.B, T.A, T2.B from T, T2 where T.A = T2.A order by 3 desc, 1", 0);
   }

   @Test
   void orderByNameThenOrdinal() throws Exception {
      assertSameRows("select T2.B bb, T.B tb, T.A from T left join T2 on T.A = T2.A " +
                        "order by bb, 2", 0);
   }

   @Test
   void orderByOrdinalLeftJoin() throws Exception {
      assertSameRows("select T.B bb, T.A aa from T left join T2 on T.A = T2.A order by 1", 0);
   }

   @Test
   void orderByOrdinalDistinct() throws Exception {
      assertSameRows("select distinct T.B, T.A from T order by 1", 0);
   }

   // a row limit returned another row, not just another order
   @Test
   void orderByOrdinalWithMaxRows() throws Exception {
      assertSameRows("select T.B, T.A from T order by 1", 1);
      assertSameRows("select T.B bb, T.A aa from T left join T2 on T.A = T2.A order by 1 desc",
                     1);
   }

   // the inner level has an ordinal and inherits the sorted hint from the outer level
   @Test
   void derivedTableOrdinalInheritingSortedHint() throws Exception {
      UniformSQL usql = parsed("select s.B, s.A from (select T.B, T.A from T order by 1) s " +
                                  "order by s.B", 2);
      JDBCQuery query = newQuery(usql);
      JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(query);

      // the outer level has no ordinal and is still sorted
      assertArrayEquals(new int[] { 1, 0 }, normalizer.getSortedColumnMap());
      String generated = norm(query.getSQLAsString());
      assertTrue(generated.startsWith("select s.a, s.b from"), generated);
      assertTrue(generated.contains("( select t.b, t.a from t order by 1 asc) s"), generated);

      assertSameRows("select s.B, s.A from (select T.B, T.A from T order by 1) s order by 1", 1);

      // control without any ordinal: both levels sorted, columns restored, same rows
      String control = "select s.B, s.A from (select T.B, T.A from T) s order by s.B";
      Run run = run(newSession(false), parsed(control, 2), 0);
      assertTrue(norm(run.executedSql).startsWith("select s.a, s.b from ( select t.a, t.b"),
                 run.executedSql);
      assertEquals(direct(control, 0), rows(run.table));
   }

   // a sorted hint left on the query by an earlier generation must not let an ordinal sort
   @Test
   void staleSortedHintDoesNotSortOrdinalQuery() throws Exception {
      UniformSQL usql = parsed("select T.B, T.A from T order by 1", 2);
      usql.setHint(UniformSQL.HINT_SORTED_SQL, true);

      assertNull(JDBCQueryCacheNormalizer.generateSortedColumnMap(usql));
      usql.clearSQLString();
      assertEquals("select t.b, t.a from t order by 1 asc", norm(usql.getSQLString()));
   }

   // a saved query reloads 'order by 1' as the String "1"
   @Test
   void savedQueryWithStringOrdinal() throws Exception {
      String sql = "select T.B, T.A from T order by 1";
      UniformSQL loaded = xmlRoundTrip(parsed(sql, 2));
      assertEquals("1", String.valueOf(loaded.getOrderByFields()[0]));
      assertTrue(loaded.getOrderByFields()[0] instanceof String);

      Run run = run(newSession(false), loaded, 0);
      assertEquals(sql, run.executedSql);
      assertEquals(direct(sql, 0), rows(run.table));

      UniformSQL loaded2 = xmlRoundTrip(parsed(sql, 2));
      Run limited = run(newSession(false), loaded2, 1);
      assertEquals(direct(sql, 1), rows(limited.table));
   }

   @Test
   void groupByOrdinalIsNotSorted() throws Exception {
      String sql = "select T.B, count(*), T.A from T group by 1, 3";
      UniformSQL usql = parsed(sql, 3);
      JDBCQuery query = newQuery(usql);
      JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(query);

      assertNull(normalizer.getSortedColumnMap());
      assertNull(normalizer.getOriginalColumnMap());
      assertEquals(sql, query.getSQLAsString());

      // a later regeneration (merge, VPM) keeps the select order
      usql.clearSQLString();
      String generated = norm(usql.getSQLString());
      assertTrue(generated.startsWith("select t.b, count(*), t.a from t"), generated);
      assertTrue(generated.endsWith("group by 1, 3"), generated);
   }

   // control: a query without an ordinal is still sorted and still shares the cache entry of
   // its reordered variant
   @Test
   void nonOrdinalOrderByIsStillSortedAndShared() throws Exception {
      XSessionManager session = newSession(true);
      String sql = "select T.B bb, T.A aa from T order by bb";
      Run first = run(session, parsed(sql, 2), 0);

      assertTrue(norm(first.executedSql).startsWith("select t.a as aa, t.b as bb"),
                 first.executedSql);
      assertEquals(direct(sql, 0), rows(first.table));

      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         stmt.executeUpdate("insert into T values (4, 'w')");
      }

      Run second = run(session, parsed("select T.A aa, T.B bb from T order by bb", 2), 0);
      assertEquals(3, rows(second.table).size(), "expected a cache hit");
      assertEquals("[[3, x], [2, y], [1, z]]", rows(second.table).toString());
   }

   // Informix can't group by an expression, so SQLHelper writes its select list position.
   // The position must be the one the column was written at in the sorted select list.
   @Test
   void informixExpressionGroupByUsesSortedPosition() throws Exception {
      String sql = "select T.A + 1, T2.B, T.A from T, T2 where T.A = T2.A " +
         "group by T.A + 1, T2.B, T.A";
      UniformSQL usql = parsed(sql, 3);
      JDBCQuery query = newQuery(usql);
      query.setDataSource(informix());
      JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(query);

      assertArrayEquals(new int[] { 2, 0, 1 }, normalizer.getSortedColumnMap());
      String generated = norm(query.getSQLAsString());
      assertTrue(generated.startsWith("select t.a, t.a+1, t2.b from"), generated);
      assertTrue(generated.contains("group by 2, t2.b, t.a"), generated);
   }

   // a group by expression that isn't in the select list must not index the map with -1
   @Test
   void informixGroupByExpressionNotInSelection() throws Exception {
      UniformSQL usql = parsed("select T.A + 1, T2.B, T.A from T, T2 where T.A = T2.A " +
                                  "group by T.A + 1, T2.B, T.A", 3);
      usql.setGroupBy(new Object[] { "T.A + 2", "T2.B", "T.A" });
      JDBCQuery query = newQuery(usql);
      query.setDataSource(informix());
      new JDBCQueryCacheNormalizer(query);

      String generated = norm(assertDoesNotThrow(query::getSQLAsString));
      assertTrue(generated.contains("group by t.a + 2, t2.b, t.a"), generated);
   }

   /**
    * Run the query through XSessionManager and compare its rows, in order, with the original
    * sql run directly on Derby. A query with an ordinal runs as written.
    */
   private void assertSameRows(String sql, int maxRows) throws Exception {
      UniformSQL usql = parsed(sql, -1);
      Run run = run(newSession(false), usql, maxRows);

      List<List<String>> expected = direct(sql, maxRows);
      assertFalse(expected.isEmpty());
      assertEquals(expected, rows(run.table), sql);
      assertEquals(sql, run.executedSql);
   }

   private static List<List<String>> direct(String sql, int maxRows) throws Exception {
      List<List<String>> rows = new ArrayList<>();

      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         stmt.setMaxRows(maxRows);

         try(ResultSet rs = stmt.executeQuery(sql)) {
            int count = rs.getMetaData().getColumnCount();

            while(rs.next()) {
               List<String> row = new ArrayList<>();

               for(int i = 1; i <= count; i++) {
                  row.add(String.valueOf(rs.getObject(i)));
               }

               rows.add(row);
            }
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

   private static String norm(String sql) {
      return sql == null ? null : sql.replaceAll("\\s+", " ").trim().toLowerCase();
   }

   private static UniformSQL xmlRoundTrip(UniformSQL usql) throws Exception {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         usql.writeXML(writer);
      }

      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(buffer.toString())).getDocumentElement());
      return loaded;
   }

   private static UniformSQL parsed(String sql, int columns) throws Exception {
      UniformSQL usql = new UniformSQL();
      usql.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      usql.setSQLString(sql, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult());
      assertFalse(usql.isLossy());

      if(columns >= 0) {
         assertEquals(columns, usql.getSelection().getColumnCount());
      }

      return usql;
   }

   private static JDBCQuery newQuery(UniformSQL usql) {
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77557");
      query.setDataSource(dataSource());
      query.setSQLDefinition(usql);
      return query;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77557");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
   }

   private static JDBCDataSource informix() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77557informix");
      ds.setDriver("com.informix.jdbc.IfxDriver");
      ds.setURL("jdbc:informix-sqli://localhost:9088/db:INFORMIXSERVER=ids");
      ds.setRequireLogin(false);
      return ds;
   }

   private static XSessionManager newSession(boolean cache) throws Exception {
      XDataService dataService = mock(XDataService.class);
      when(dataService.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> execute(inv.getArgument(1), inv.getArgument(2),
                                    inv.getArgument(3), inv.getArgument(5)));
      XSessionManager session = new XSessionManager(
         dataService, ConfigurationContext.getContext().getSpringBean(XSessionService.class),
         null);
      session.setCacheData(cache);
      sessions.add(session);
      return session;
   }

   private Run run(XSessionManager session, UniformSQL usql, int maxRows) throws Exception {
      JDBCQuery query = newQuery(usql);
      query.setMaxRows(maxRows);
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
         JDBCQueryCacheNormalizerOrdinalTest.class.getClassLoader(), new Class<?>[] { type },
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

   private static final ThreadLocal<String> executed = new ThreadLocal<>();
   private static final ThreadLocal<Run> currentRun = new ThreadLocal<>();
   private static final List<XSessionManager> sessions = new ArrayList<>();
}
