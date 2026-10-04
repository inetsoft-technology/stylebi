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
import inetsoft.uql.path.XSelection;
import inetsoft.uql.util.*;
import inetsoft.util.ConfigurationContext;
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
import java.lang.reflect.Constructor;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77620, a parameter holding NULL_VALUE, EMPTY_STRING or NULL_STRING in a scalar subquery
 * of the select list was bound as the text of the sentinel. The parser stores such a subquery
 * as the text of the column, and XUtil.validateConditions didn't look at the select list.
 * <p>
 * A parameter left in the sql is bound here the way VarSQL binds it, as the text of its
 * value, so the rows show a missed rewrite. Table b has a row holding the text NULL_VALUE.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  XUtilSentinelSelectListTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XUtilSentinelSelectListTest {
   private static final String DB = "memory:bug77620";
   private static final String NULL_VALUE = XConstants.CONDITION_NULL_VALUE;
   private static final String EMPTY_STRING = XConstants.CONDITION_EMPTY_STRING;
   private static final String NULL_STRING = XConstants.CONDITION_NULL_STRING;
   private static final String COUNT = "(select count(*) from b where ";
   // SQLHelper, DerbyHelper and OracleSQLHelper, with and without ANSI joins
   private static final String[] TYPES = { "default", "derby", "derby-ansi", "oracle",
                                           "oracle-ansi" };

   @Configuration
   static class JdbcConfig {
      // JDBCDataSource's constructor needs CredentialService, whose constructor is
      // package-private
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
         DataSource ds = derby();
         ConnectionPoolFactory factory = mock(ConnectionPoolFactory.class);
         when(factory.getConnectionPool(any(), any())).thenReturn(ds);
         return factory;
      }

      @Bean
      public Drivers drivers(Plugins plugins, ConnectionPoolFactory connectionPoolFactory) {
         return new Drivers(plugins, connectionPoolFactory);
      }

      @Bean
      public inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      // the derby helper asks the repository for the database version
      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   @BeforeEach
   void createTables() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         for(String table : new String[] { "a", "b", "\"c\"", "C" }) {
            try {
               stmt.executeUpdate("drop table " + table);
            }
            catch(SQLException ignore) {
               // first run
            }
         }

         stmt.executeUpdate("create table a (id int)");
         stmt.executeUpdate("insert into a values (1), (3), (5)");
         // k is VARCHAR: a sentinel bound as text against a numeric column is a type error
         stmt.executeUpdate("create table b (id int, k varchar(20))");
         stmt.executeUpdate("insert into b values (1, '2'), (1, '2'), (3, '2'), (3, '3'), " +
                               "(1, null), (3, null), (5, ''), (5, 'null'), " +
                               "(5, 'NULL_VALUE'), (7, null)");
         // a table written quoted ("c") and one unquoted (C), which Derby stores as "C"
         stmt.executeUpdate("create table \"c\" (ID int)");
         stmt.executeUpdate("insert into \"c\" values (1), (3)");
         stmt.executeUpdate("create table C (ID int, K varchar(20))");
         stmt.executeUpdate("insert into C values (1, null), (1, 'x'), (3, null), (3, null), " +
                               "(5, null)");
      }
   }

   // sql with $(p), the value of p, hand-written sql with the expected rows
   static Stream<Arguments> rowCases() {
      return Stream.of(
         // the reported shape, unaliased
         Arguments.of("select a.id, " + COUNT + "b.k = $(p)) from a", NULL_VALUE,
                      "select a.id, " + COUNT + "b.k is null) from a"),
         Arguments.of("select a.id, " + COUNT + "b.k = $(p)) from a", EMPTY_STRING,
                      "select a.id, " + COUNT + "b.k = '') from a"),
         Arguments.of("select a.id, " + COUNT + "b.k = $(p)) from a", NULL_STRING,
                      "select a.id, " + COUNT + "b.k = 'null') from a"),
         Arguments.of("select a.id, " + COUNT + "b.k <> $(p)) from a", NULL_VALUE,
                      "select a.id, " + COUNT + "b.k is not null) from a"),
         // aliased
         Arguments.of("select a.id, " + COUNT + "b.k = $(p)) as cnt from a", NULL_VALUE,
                      "select a.id, " + COUNT + "b.k is null) as cnt from a"),
         Arguments.of("select a.id, cnt = " + COUNT + "b.k = $(p)) from a", NULL_VALUE,
                      "select a.id, " + COUNT + "b.k is null) as cnt from a"),
         // an aggregate of a column
         Arguments.of("select a.id, (select max(b.id) from b where b.k = $(p)) from a",
                      NULL_VALUE,
                      "select a.id, (select max(b.id) from b where b.k is null) from a"),
         // the subquery as the only column
         Arguments.of("select " + COUNT + "b.k = $(p)) from a", NULL_VALUE,
                      "select " + COUNT + "b.k is null) from a"),
         // correlated
         Arguments.of("select a.id, " + COUNT + "b.id = a.id and b.k = $(p)) from a",
                      NULL_VALUE,
                      "select a.id, " + COUNT + "b.id = a.id and b.k is null) from a"),
         Arguments.of("select a.id, " + COUNT + "b.id = a.id and b.k = $(p)) from a",
                      EMPTY_STRING,
                      "select a.id, " + COUNT + "b.id = a.id and b.k = '') from a"),
         // two subquery columns
         Arguments.of("select a.id, " + COUNT + "b.k = $(p)), " + COUNT + "b.k = '2') from a",
                      NULL_VALUE,
                      "select a.id, " + COUNT + "b.k is null), " + COUNT + "b.k = '2') from a"),
         // the operand shapes of #77619 inside the subquery
         Arguments.of("select a.id, " + COUNT + "$(p) = b.k) from a", NULL_VALUE,
                      "select a.id, " + COUNT + "b.k is null) from a"),
         Arguments.of("select a.id, " + COUNT + "b.k in ($(p))) from a", NULL_VALUE,
                      "select a.id, " + COUNT + "b.k is null) from a"),
         // a select list subquery of a derived table
         Arguments.of("select d.id, d.cnt from (select a.id, " + COUNT +
                         "b.k = $(p)) as cnt from a) d", NULL_VALUE,
                      "select d.id, d.cnt from (select a.id, " + COUNT +
                         "b.k is null) as cnt from a) d"),
         // a correlated select list subquery referring to a derived table of the query
         Arguments.of("select d.id, " + COUNT + "b.id = d.id and b.k = $(p)) " +
                         "from (select a.id from a) d", NULL_VALUE,
                      "select d.id, " + COUNT + "b.id = d.id and b.k is null) " +
                         "from (select a.id from a) d"),
         // a correlated subquery against a table written quoted, the quotes of its references
         // in the subquery are added when the sql is generated (#77569)
         Arguments.of("select \"c\".ID, (select count(*) from C where C.ID = \"c\".ID and " +
                         "C.K = $(p)) from \"c\"", NULL_VALUE,
                      "select \"c\".ID, (select count(*) from C where C.ID = \"c\".ID and " +
                         "C.K is null) from \"c\""));
   }

   @ParameterizedTest
   @MethodSource("rowCases")
   void sameRows(String sql, String p, String expected) throws Exception {
      try(Connection conn = derby().getConnection()) {
         for(String type : TYPES) {
            // generated by the same helper, which sorts the select list
            List<String> expectedRows = rows(conn, generate(parse(expected, type)));
            UniformSQL usql = parse(sql, type);
            List<String> columns = columns(usql);
            String generated = validate(usql, p);

            assertFalse(generated.contains("$(p)"), type + " not rewritten: " + generated);
            assertEquals(expectedRows, rows(conn, bind(generated, p)),
                         type + "\ngenerated: " + generated);
            // the column names, which name the result columns, are kept
            assertEquals(columns, columns(usql), type);
         }
      }
   }

   // an unset parameter in a select list subquery is left as it is (bound as null)
   @ParameterizedTest
   @MethodSource("rowCases")
   void unsetUnchanged(String sql, String p, String expected) throws Exception {
      for(String type : TYPES) {
         String original = generate(parse(sql, type));
         UniformSQL usql = parse(sql, type);
         VariableTable vars = new VariableTable();
         XUtil.validateConditions(null, usql, vars, true, false);
         assertEquals(original, generate(usql), type);
      }
   }

   // a value that isn't a sentinel is left as it is
   @Test
   void plainValueUnchanged() throws Exception {
      String sql = "select a.id, " + COUNT + "b.k = $(p)) from a";

      for(String type : TYPES) {
         assertEquals(generate(parse(sql, type)), validate(parse(sql, type), "2"), type);
      }
   }

   // the select list subquery of a derived table, run on a clone of the query as JDBCHandler
   // runs it: the rewrite of one run must not stay for the next run or in the original
   @Test
   void derivedTableRunTwice() throws Exception {
      String sql = "select d.id, d.cnt from (select a.id, " + COUNT +
         "b.k = $(p)) as cnt from a) d";

      try(Connection conn = derby().getConnection()) {
         for(String type : TYPES) {
            UniformSQL original = parse(sql, type);
            String before = generate(original);

            String first = validate(original.clone(), NULL_VALUE);
            assertEquals(rows(conn, generate(parse(sql.replace("= $(p)", "is null"), type))),
                         rows(conn, first), type + " " + first);

            String second = validate(original.clone(), "3");
            assertEquals(rows(conn, bind(before, "3")), rows(conn, bind(second, "3")),
                         type + " " + second);
            assertTrue(second.contains("$(p)"), second);

            assertEquals(before, generate(original), type);
         }
      }
   }

   // the same query object validated with a sentinel and then with a plain value
   @Test
   void sameObjectRunTwice() throws Exception {
      String sql = "select a.id, " + COUNT + "b.k = $(p)) from a";
      UniformSQL usql = parse(sql, "derby");
      String plain = generate(parse(sql, "derby"));

      assertFalse(validate(usql, NULL_VALUE).contains("$(p)"));
      assertEquals(plain, validate(usql, "3"));
   }

   // the result column of an unaliased subquery is named by its text, and the column order is
   // restored with a map computed before the parameters are applied. Both must be the same
   // whatever the value of the parameter.
   @Test
   void headersAndOrderThroughJDBCHandler() throws Exception {
      // the subquery with the parameter sorts before the other one ($ before '), and after
      // it once the parameter is rewritten (I after ')
      String sql = "select a.id, " + COUNT + "b.k = '2'), " + COUNT + "b.k = $(p)) from a";
      UniformSQL usql = parsed(sql);
      List<String> columns = columns(usql);
      VariableTable vars = new VariableTable();
      vars.put("p", NULL_VALUE);

      TableLens table = run(usql, vars);
      table.moreRows(Integer.MAX_VALUE);
      assertEquals(3, table.getColCount());

      for(int c = 0; c < 3; c++) {
         String header = String.valueOf(table.getObject(0, c));
         assertTrue(header.equalsIgnoreCase(columns.get(c)) ||
                       c == 0 && header.equalsIgnoreCase("id"),
                    "header " + c + ": " + header + " expected " + columns.get(c));
      }

      // a.id, count of '2' (3), count of null (3) - with the bound text it would be 1
      List<String> rows = new ArrayList<>();

      for(int r = 1; table.moreRows(r); r++) {
         rows.add(table.getObject(r, 0) + "|" + table.getObject(r, 1) + "|" +
                     table.getObject(r, 2));
      }

      Collections.sort(rows);
      assertEquals(List.of("1|3|3", "3|3|3", "5|3|3"), rows);
   }

   // the same saved query run through JDBCHandler with a sentinel, then a plain value, then the
   // sentinel again: no run reuses the rewrite of another, and the saved query is unchanged
   @Test
   void runTwiceThroughJDBCHandler() throws Exception {
      String[] sqls = {
         "select a.id, " + COUNT + "b.k = $(p)) as cnt from a",
         "select d.id, d.cnt from (select a.id, " + COUNT + "b.k = $(p)) as cnt from a) d"
      };

      for(String sql : sqls) {
         UniformSQL usql = parsed(sql);
         // b.k is null in 3 rows, '3' in 1 row
         String[][] runs = { { NULL_VALUE, "3" }, { "3", "1" }, { NULL_VALUE, "3" } };

         for(String[] run : runs) {
            VariableTable vars = new VariableTable();
            vars.put("p", run[0]);
            TableLens table = run(usql, vars);
            List<String> rows = new ArrayList<>();

            for(int r = 1; table.moreRows(r); r++) {
               rows.add(table.getObject(r, 0) + "|" + table.getObject(r, 1));
            }

            Collections.sort(rows);
            assertEquals(List.of("1|" + run[1], "3|" + run[1], "5|" + run[1]), rows,
                         sql + " p=" + run[0]);
            // the saved query may get its sql string regenerated, never with the rewrite
            String now = usql.getSQLString();
            assertTrue(now.contains("$(p)"), now);
            assertFalse(now.toUpperCase().contains("IS NULL"), now);
            assertNoColumnSQL(usql);
         }
      }
   }

   private static void assertNoColumnSQL(UniformSQL usql) {
      JDBCSelection selection = (JDBCSelection) usql.getSelection();

      for(int i = 0; i < selection.getColumnCount(); i++) {
         assertNull(selection.getColumnSQL(i), selection.getColumn(i));
      }

      for(SelectTable table : usql.getSelectTable()) {
         if(table.getName() instanceof UniformSQL) {
            assertNoColumnSQL((UniformSQL) table.getName());
         }
      }
   }

   // a query that keeps its sql string isn't validated at all, as before
   @Test
   void keptSqlStringUnchanged() throws Exception {
      String sql = "select a.id, " + COUNT + "b.k = $(p)) from a";
      UniformSQL usql = parse(sql, "derby");
      usql.setSQLString(sql, false);
      VariableTable vars = new VariableTable();
      vars.put("p", NULL_VALUE);
      XUtil.validateConditions(null, usql, vars, true, false);
      assertEquals(sql, usql.getSQLString());
   }

   // the select list subquery is rewritten on the vpm path too, which only rewrites sentinels
   @Test
   void vpmPath() throws Exception {
      String sql = "select a.id, " + COUNT + "b.k = $(p)) from a";

      try(Connection conn = derby().getConnection()) {
         UniformSQL usql = parse(sql, "derby");
         VariableTable vars = new VariableTable();
         vars.put("p", NULL_VALUE);
         XUtil.validateConditions(null, usql, vars, true, true);
         String generated = generate(usql);
         assertEquals(rows(conn, generate(parse(sql.replace("= $(p)", "is null"), "derby"))),
                      rows(conn, generated), generated);
      }
   }

   // a copy of the validated query generates the rewritten sql, the saved query doesn't keep it
   @Test
   void cloneCarriesRewriteXmlDoesNot() throws Exception {
      String sql = "select a.id, " + COUNT + "b.k = $(p)) from a";
      UniformSQL usql = parse(sql, "derby");
      String rewritten = validate(usql, NULL_VALUE);
      assertFalse(rewritten.contains("$(p)"), rewritten);
      assertEquals(rewritten, generate(usql.clone()));

      java.io.StringWriter buf = new java.io.StringWriter();
      java.io.PrintWriter writer = new java.io.PrintWriter(buf);
      usql.writeXML(writer);
      writer.flush();
      assertTrue(buf.toString().contains("$(p)"), buf.toString());
      assertFalse(buf.toString().contains("IS NULL"), buf.toString());
   }

   // the column is changed, removed or the list cleared: the rewritten sql isn't used
   @Test
   void rewriteFollowsColumn() throws Exception {
      String sql = "select a.id, " + COUNT + "b.k = $(p)) from a";
      UniformSQL usql = parse(sql, "default");
      validate(usql, NULL_VALUE);
      JDBCSelection selection = (JDBCSelection) usql.getSelection();
      int idx = selection.getColumn(0).startsWith("(") ? 0 : 1;
      String rewritten = selection.getColumnSQL(idx);
      assertNotNull(rewritten);
      assertTrue(rewritten.contains("IS NULL"), rewritten);

      // a column removed before it: the entry moves with the column
      selection.removeColumn(1 - idx);
      assertEquals(rewritten, selection.getColumnSQL(0));

      selection.setColumn(0, selection.getColumn(0));
      assertNull(selection.getColumnSQL(0));

      selection.setColumnSQL(0, rewritten);
      selection.clear();
      selection.addColumn("x");
      assertNull(selection.getColumnSQL(0));
   }

   // text that isn't one subquery generated by the parser is left as it is
   @Test
   void roundTripGuard() throws Exception {
      String column = parse("select " + COUNT + "b.k = $(p)) from a", "default")
         .getSelection().getColumn(0);
      assertNotNull(UniformSQL.parseSelectListSubquery(column, null));

      // not as the parser generates it
      String[] texts = {
         "(SELECT count(*) FROM b WHERE b.k = $(p))",
         column + " + 1",
         column + " + " + column,
         "(1 + 2)",
         "(select from)",
         "b.k",
         null
      };

      for(String text : texts) {
         assertNull(UniformSQL.parseSelectListSubquery(text, null), text);
      }

      // postgresql writes the names quoted, the subquery is parsed for the data source
      JDBCDataSource pg = SQLHelperNotEqualJoinTest.RowCompare.dataSource("postgresql");
      String pgColumn = parse("select " + COUNT + "b.k = $(p)) from a", "postgresql")
         .getSelection().getColumn(0);
      assertTrue(pgColumn.contains("\"b\""), pgColumn);
      assertNull(UniformSQL.parseSelectListSubquery(pgColumn, null), pgColumn);
      assertNotNull(UniformSQL.parseSelectListSubquery(pgColumn, pg), pgColumn);
   }

   // a subquery inside an expression isn't rewritten (a follow-up), and isn't broken
   @Test
   void expressionUnchanged() throws Exception {
      String sql = "select a.id, " + COUNT + "b.k = $(p)) + 1 from a";

      for(String type : TYPES) {
         assertEquals(generate(parse(sql, type)), validate(parse(sql, type), NULL_VALUE), type);
      }
   }

   // postgresql quotes the references to a table written quoted in the rewritten subquery
   @Test
   void postgresqlQuotedOuterTable() throws Exception {
      String sql = "select \"c\".ID, (select count(*) from C where C.ID = \"c\".ID and " +
         "C.K = $(p)) from \"c\"";
      String original = generate(parse(sql, "postgresql"));
      String generated = validate(parse(sql, "postgresql"), NULL_VALUE);
      assertEquals(original.replace("= $(p)", "IS NULL"), generated);
      assertTrue(generated.contains("= \"c\".\"ID\""), generated);

      // in a derived table, which gets the data source of its query
      sql = "select d.ID from (select \"c\".ID, (select count(*) from C where " +
         "C.K = $(p)) as cnt from \"c\") d";
      original = generate(parse(sql, "postgresql"));
      generated = validate(parse(sql, "postgresql"), NULL_VALUE);
      assertEquals(original.replace("= $(p)", "IS NULL"), generated);
   }

   // the keyword check doesn't depend on the locale (surefire pins en_US)
   @Test
   void turkishLocale() throws Exception {
      Locale locale = Locale.getDefault();

      try {
         Locale.setDefault(Locale.forLanguageTag("tr-TR"));
         String generated = validate(parse("select a.id, " + COUNT + "b.k = $(p)) from a",
                                           "default"), NULL_VALUE);
         assertFalse(generated.contains("$(p)"), generated);
      }
      finally {
         Locale.setDefault(locale);
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

   private static List<String> rows(Connection conn, String sql) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
         int count = rs.getMetaData().getColumnCount();

         while(rs.next()) {
            StringBuilder row = new StringBuilder();

            for(int i = 1; i <= count; i++) {
               row.append(rs.getString(i)).append('|');
            }

            rows.add(row.toString());
         }
      }

      Collections.sort(rows);
      return rows;
   }

   // a parameter left in the sql is bound by VarSQL as its value, as text here
   private static String bind(String sql, String p) {
      return sql.replace("$(p)", "'" + p + "'");
   }

   private static List<String> columns(UniformSQL usql) {
      List<String> columns = new ArrayList<>();
      XSelection selection = usql.getSelection();

      for(int i = 0; i < selection.getColumnCount(); i++) {
         columns.add(selection.getColumn(i));
      }

      return columns;
   }

   private static String validate(UniformSQL usql, String p) {
      VariableTable vars = new VariableTable();
      vars.put("p", p);
      XUtil.validateConditions(null, usql, vars, true, false);
      return generate(usql);
   }

   private static String generate(UniformSQL usql) {
      usql.clearSQLString();
      return usql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   // a new query for every case and run, the rewrite changes the parsed query in place
   private static UniformSQL parse(String text, String type) throws Exception {
      UniformSQL sql = new UniformSQL();

      if(!"default".equals(type)) {
         sql.setDataSource(SQLHelperNotEqualJoinTest.RowCompare.dataSource(type));
      }

      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      sql.clearSQLString();
      return sql;
   }

   // a query of a data source as it's saved: parsed, with its sql string
   private static UniformSQL parsed(String sql) throws Exception {
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(dataSource());
      usql.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      usql.setSQLString(sql, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult());
      return usql;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77620");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
   }

   // run a query through XSessionManager and JDBCHandler, with the cache normalizer
   private static TableLens run(UniformSQL usql, VariableTable vars) throws Exception {
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77620");
      query.setDataSource(dataSource());
      query.setSQLDefinition(usql);

      XDataService dataService = mock(XDataService.class);
      when(dataService.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> {
            XQuery q = inv.getArgument(1);
            JDBCHandler handler = new JDBCHandler();
            handler.connect(q.getDataSource(), inv.getArgument(2));
            return handler.execute(q, inv.getArgument(2), inv.getArgument(3),
                                   inv.getArgument(5));
         });
      XSessionManager session = new XSessionManager(
         dataService, ConfigurationContext.getContext().getSpringBean(XSessionService.class),
         null);
      session.setCacheData(false);

      try {
         TableLens table = session.getXNodeTableLens(query, vars, null, null, null, -1);
         assertNotNull(table, "query failed, see log");
         table.moreRows(Integer.MAX_VALUE);
         return table;
      }
      finally {
         session.tearDown();
      }
   }

   private static DataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }
}
