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
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XNode;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Drivers;
import inetsoft.util.DataCache;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import javax.sql.DataSource;
import java.io.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #77483, the cache normalizer must not clear the sql string of a parse-off query whose
 * selection has columns, and must not sort its columns: the sql is sent as written. Before the
 * fix, a saved parse-off SQL-bound table ran "select X, Y" (no from clause) on every execution
 * through XSessionManager.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, JDBCQueryCacheNormalizerParseOffTest.Cfg.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class JDBCQueryCacheNormalizerParseOffTest {
   private static final String URL = "jdbc:derby:memory:bug77483;create=true";
   private static final String SQL = "select a.x, a.y from a where a.x > 1 order by a.x";
   private static final List<String> SENT = Collections.synchronizedList(new ArrayList<>());

   @Configuration
   static class Cfg {
      @Bean
      @Primary
      public ConnectionPoolFactory connectionPoolFactory() throws Exception {
         ConnectionPoolFactory factory = mock(ConnectionPoolFactory.class);
         DataSource ds = mock(DataSource.class);
         when(ds.getConnection()).thenAnswer(inv -> recording(DriverManager.getConnection(URL)));
         when(factory.getConnectionPool(any(), any())).thenReturn(ds);
         return factory;
      }

      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      @Bean
      public Config config() {
         return mock(Config.class);
      }

      @Bean
      @Primary
      public Drivers drivers() {
         return mock(Drivers.class);
      }
   }

   @BeforeAll
   static void createDatabase() throws Exception {
      try(Connection conn = DriverManager.getConnection(URL); Statement stmt = conn.createStatement()) {
         try {
            stmt.execute("drop table a");
         }
         catch(SQLException ignore) {
            // first run
         }

         stmt.execute("create table a (x int, y varchar(10))");
         stmt.execute("insert into a values (1, 'a'), (2, 'b'), (3, 'c')");
      }
   }

   @AfterAll
   static void dropDatabase() {
      try {
         DriverManager.getConnection("jdbc:derby:memory:bug77483;drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   // ---- normalizer level ----

   @Test
   void parseOffWithSelectionIsNotClearedOrSorted() {
      UniformSQL sql = parseOff("select a.y, a.x from a", "Y", "X");
      JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(query(sql));

      assertFalse(normalizer.isClearedSqlString());
      assertTrue(sql.hasSQLString());
      assertEquals("select a.y, a.x from a", sql.getSQLString());
      assertNull(normalizer.getSortedColumnMap());
      assertNull(normalizer.getOriginalColumnMap());
   }

   @Test
   void parseOffGenerateSentenceKeepsSqlString() {
      // SQLHelper.generateSentence -> generateSelectClause -> generateSortedColumnMap
      UniformSQL sql = parseOff(SQL, "x", "y");
      SQLHelper.getSQLHelper(sql).generateSentence();

      assertTrue(sql.hasSQLString());
      assertEquals(SQL, sql.getSQLString());
      assertNull(JDBCQueryCacheNormalizer.generateSortedColumnMap(sql));
   }

   @Test
   void parseOffWithoutSelectionIsUnchanged() {
      UniformSQL sql = parseOff(SQL);
      JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(query(sql));

      assertFalse(normalizer.isClearedSqlString());
      assertEquals(SQL, sql.getSQLString());
      int[] map = normalizer.getSortedColumnMap();
      assertTrue(map == null || map.length == 0, Arrays.toString(map));
   }

   @Test
   void parseOffStructureWithoutSqlStringStillSorts() {
      // a parse-off structure with no sql string is still generated and sorted
      UniformSQL sql = new UniformSQL();
      sql.setParseSQL(false);
      sql.addTable("a");
      sql.getSelection().addColumn("a.y");
      sql.getSelection().addColumn("a.x");

      assertArrayEquals(new int[] { 1, 0 }, JDBCQueryCacheNormalizer.generateSortedColumnMap(sql));
   }

   @Test
   void parseOnParsedQueryIsStillClearedAndSorted() throws Exception {
      UniformSQL sql = parseOn("select a.y, a.x from a");
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertFalse(sql.isLossy());
      JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(query(sql));

      assertTrue(normalizer.isClearedSqlString());
      assertFalse(sql.hasSQLString());
      assertArrayEquals(new int[] { 1, 0 }, normalizer.getSortedColumnMap());
      assertArrayEquals(new int[] { 1, 0 }, normalizer.getOriginalColumnMap());
   }

   // ---- real execution through JDBCHandler, the way XSessionManager runs a bound query ----

   @Test
   void savedParseOffQueryWithoutSelectionRuns() throws Exception {
      Result result = execute(saved(SQL), true);

      assertNull(result.error);
      assertEquals(List.of(SQL), result.sent);
      assertEquals("X Y | 2 b | 3 c", result.rows);
   }

   @Test
   void savedParseOffQueryWithSelectionRuns() throws Exception {
      JDBCQuery saved = saved(SQL, "X", "Y");
      Result result = execute(saved, true);

      assertNull(result.error, result.error);
      assertEquals(List.of(SQL), result.sent);
      assertEquals("X Y | 2 b | 3 c", result.rows);
      assertTrue(((UniformSQL) saved.getSQLDefinition()).hasSQLString());
   }

   @Test
   void parseOffColumnsAreNotReordered() throws Exception {
      String text = "select a.y, a.x from a where a.x > 1 order by a.x";
      Result result = execute(saved(text, "Y", "X"), true);

      assertNull(result.error, result.error);
      assertEquals(List.of(text), result.sent);
      assertEquals("Y X | b 2 | c 3", result.rows);
   }

   @Test
   void staleSelectionDoesNotDropColumns() throws Exception {
      // the selection is left from an earlier version of the sql
      String text = "select a.y, a.x, a.x + 10 as z from a where a.x > 1 order by a.x";
      Result result = execute(saved(text, "Y", "X"), true);

      assertNull(result.error, result.error);
      assertEquals(List.of(text), result.sent);
      assertEquals("Y X Z | b 2 12 | c 3 13", result.rows);
   }

   @Test
   void staleSelectionWithExtraColumnsRuns() throws Exception {
      // the selection has more entries than the sql returns
      String text = "select a.y from a where a.x > 1 order by a.x";
      Result result = execute(saved(text, "Z", "Y", "X"), true);

      assertNull(result.error, result.error);
      assertEquals(List.of(text), result.sent);
      assertEquals("Y | b | c", result.rows);
   }

   @Test
   void sameSqlSharesTheCacheKeyAcrossSelectionOrders() throws Exception {
      Result first = execute(saved(SQL, "X", "Y"), true);
      Result second = execute(saved(SQL, "Y", "X"), true);

      assertNull(first.error, first.error);
      assertNull(second.error, second.error);
      assertNotNull(first.key);
      assertEquals(first.key, second.key);
   }

   @Test
   void differentSqlWithTheSameSelectionDoesNotShareTheCacheKey() throws Exception {
      // before the fix both ran as "select X, Y" under one key
      Result first = execute(saved(SQL, "X", "Y"), true);
      Result second = execute(saved("select a.y, a.x from a where a.x > 1 order by a.x", "X", "Y"), true);

      assertNull(first.error, first.error);
      assertNull(second.error, second.error);
      assertNotEquals(first.key, second.key);
   }

   @Test
   void withoutCacheNormalizerRunsAsBefore() throws Exception {
      Result result = execute(saved(SQL, "X", "Y"), false);

      assertNull(result.error, result.error);
      assertEquals(List.of(SQL), result.sent);
      assertEquals("X Y | 2 b | 3 c", result.rows);
   }

   private static final class Result {
      List<String> sent;
      String rows;
      String error;
      String key;
   }

   /** what SQLBoundQuery, XSessionManager.getXNodeTableLens and JDBCHandler.execute do */
   private static Result execute(JDBCQuery saved, boolean viaCacheNormalizer) throws Exception {
      SENT.clear();
      // SQLBoundQuery executes a clone of the assembly's query
      JDBCQuery xquery = saved.clone();
      JDBCQueryCacheNormalizer normalizer = null;
      XSessionManager.DataCacheResult visitor = null;

      if(viaCacheNormalizer) {
         // XSessionManager.getJDBCQueryCacheNormalizer
         normalizer = new JDBCQueryCacheNormalizer(xquery);
         visitor = new XSessionManager.DataCacheResult(mock(DataCache.class), XNodeTableLens.class,
                                                       false, false, 0, normalizer);
      }

      JDBCHandler handler = new JDBCHandler();
      VariableTable vars = new VariableTable();
      handler.connect(xquery.getDataSource(), vars);
      Result result = new Result();

      try {
         XNode node = handler.execute(xquery, vars, null, visitor);
         JDBCTableNode table = (JDBCTableNode) node;
         List<Object[]> rows = new ArrayList<>();
         Object[] header = new Object[table.getColCount()];

         for(int c = 0; c < header.length; c++) {
            header[c] = table.getName(c);
         }

         rows.add(header);

         while(table.next()) {
            Object[] row = new Object[header.length];

            for(int c = 0; c < header.length; c++) {
               row[c] = table.getObject(c);
            }

            rows.add(row);
         }

         table.close();
         DefaultTableLens base = new DefaultTableLens(rows.toArray(new Object[0][]));
         base.setHeaderRowCount(1);
         // XSessionManager always applies the normalizer's original column map to the result
         TableLens lens = normalizer == null ? base : normalizer.transformTableLens(base);
         lens.moreRows(Integer.MAX_VALUE);
         StringBuilder text = new StringBuilder();

         for(int r = 0; r < lens.getRowCount(); r++) {
            if(r > 0) {
               text.append(" |");
            }

            for(int c = 0; c < lens.getColCount(); c++) {
               text.append(r == 0 && c == 0 ? "" : " ").append(lens.getObject(r, c));
            }
         }

         result.rows = text.toString();
      }
      catch(Exception e) {
         result.error = e.toString();
      }

      // the data cache key, built from the sql actually sent
      result.key = visitor != null ? visitor.getCacheKey() : null;
      result.sent = new ArrayList<>(SENT);
      return result;
   }

   private static Connection recording(Connection conn) {
      return (Connection) Proxy.newProxyInstance(
         JDBCQueryCacheNormalizerParseOffTest.class.getClassLoader(), new Class<?>[] { Connection.class },
         (proxy, method, args) -> {
            if(method.getName().startsWith("prepare") && args != null && args[0] instanceof String) {
               SENT.add((String) args[0]);
            }

            Object value = invoke(method, conn, args);

            if(value instanceof Statement stmt && !(value instanceof PreparedStatement)) {
               return Proxy.newProxyInstance(
                  JDBCQueryCacheNormalizerParseOffTest.class.getClassLoader(),
                  new Class<?>[] { Statement.class }, (proxy2, method2, args2) -> {
                     if(method2.getName().startsWith("execute") && args2 != null &&
                        args2[0] instanceof String)
                     {
                        SENT.add((String) args2[0]);
                     }

                     return invoke(method2, stmt, args2);
                  });
            }

            return value;
         });
   }

   private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
      try {
         return method.invoke(target, args);
      }
      catch(InvocationTargetException e) {
         throw e.getCause();
      }
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("derby77483");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL(URL);
      ds.setRequireLogin(false);
      ds.setRuntimeProductName("hsql");
      return ds;
   }

   /** a parse-off query as persisted by the query editor (XML round trip) */
   private static JDBCQuery saved(String text, String... columns) throws Exception {
      UniformSQL sql = parseOff(text, columns);
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      sql.writeXML(writer);
      writer.flush();
      Document doc = Tool.parseXML(new StringReader(buffer.toString()));
      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(doc.getDocumentElement());
      assertFalse(loaded.isParseSQL());
      assertEquals(columns.length, loaded.getSelection().getColumnCount());
      return query(loaded);
   }

   private static JDBCQuery query(UniformSQL sql) {
      JDBCDataSource ds = dataSource();
      JDBCQuery query = new JDBCQuery();
      query.setName("q77483");
      query.setDataSource(ds);
      sql.setDataSource(ds);
      query.setSQLDefinition(sql);
      return query;
   }

   private static UniformSQL parseOff(String text, String... columns) {
      UniformSQL sql = new UniformSQL();
      sql.setParseSQL(false);
      sql.setSQLString(text);

      for(String column : columns) {
         sql.getSelection().addColumn(column);
      }

      return sql;
   }

   private static UniformSQL parseOn(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setParseSQL(true);

      synchronized(sql) {
         sql.setSQLString(text);
         sql.wait();
      }

      return sql;
   }
}
