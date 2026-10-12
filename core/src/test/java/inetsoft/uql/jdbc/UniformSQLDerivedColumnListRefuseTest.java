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

import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.util.sqlparser.SQLLexer;
import inetsoft.uql.util.sqlparser.SQLParser;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.StringReader;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77494, a derived column list ({@code from (select id, k from a) t(p, q)}) parsed as a
 * success with the column list appended to the alias ({@code t(p,q)}). UniformSQL has no place
 * for the list, so the regenerated sql wrote one quoted alias {@code "t(p,q)"} without the
 * column names, and sorted the derived table's select list. A reference to {@code t.p} then
 * failed, and {@code select *} silently returned the derived table's own column names in the
 * sorted order. The parse must fail at every query level, so the original sql runs.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 UniformSQLDerivedColumnListRefuseTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLDerivedColumnListRefuseTest {
   // a data source with a real driver and URL needs the credential service, and the JDBC
   // driver types of Config to pick its SQL helper
   @Configuration
   @Import(CredentialService.class)
   static class Config {
      @Bean
      inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      @Bean
      XRepository repository() {
         return mock(XRepository.class);
      }
   }

   private static final String DERBY_URL = "jdbc:derby:memory:bug77494";
   private static final String HSQLDB_URL = "jdbc:hsqldb:mem:bug77494";

   // a derived column list on a table and on a derived table, at every query level and
   // in every from clause position. each runs on Derby and HSQLDB
   private static final String[] REFUSED = {
      // table
      "select t.p, t.q from a t(p, q)",
      "select t.p, t.q from a as t(p, q)",
      // derived table, the select * shape regenerated with the wrong column names silently
      "select * from (select id, k from a) t(p, q)",
      "select t.p, t.q from (select id, k from a) t(p, q)",
      "select t.p, t.q from (select id, k from a) as t(p, q)",
      "select t.p from (select id from a) t(p)",
      "select t.id, t.k from (select k, id from a) t(id, k)",
      // DISTINCT without ORDER BY ran as written on the data cache path, and still does
      "select distinct t.p, t.q from (select id, k from a) t(p, q)",
      // comma, inner, cross and outer joins, as the first or the joined table
      "select t.p, b.k from (select id, k from a) t(p, q), b where t.p = b.id",
      "select t.p, b.k from a t(p, q), b where t.p = b.id",
      "select t.p, b.k from (select id, k from a) t(p, q) join b on t.p = b.id",
      "select a.k, t.q from a inner join (select id, k from b) t(p, q) on a.id = t.p",
      "select a.k, t.q from a join b t(p, q) on a.id = t.p",
      "select a.k, t.p from a cross join (select id from b) t(p)",
      "select a.k, t.q from a left join (select id, k from b) t(p, q) on a.id = t.p",
      "select t.p, b.k from (select id, k from a) t(p, q) left join b on t.p = b.id",
      "select t.p, b.k from a t(p, q) left outer join b on t.p = b.id",
      "select a.k, t.q from a right join b t(p, q) on a.id = t.p",
      // subqueries
      "select a.id from a where a.id in (select t.p from (select id from b) t(p))",
      "select a.id from a where exists (select 1 from (select id from b) t(p) where t.p = a.id)",
      "select a.id, (select max(t.p) from (select id from b) t(p) where t.p = a.id) m from a",
      "select x.p from (select t.p from (select id from b) t(p)) x",
      "select x.p from (select t.p from b t(p, q)) x",
   };

   // refused shapes that only some databases run
   private static final String[] REFUSED_PARSE_ONLY = {
      // the deprecated SQL Server table hint of an aliased table matches a derived column
      // list, qualified (broken on regeneration before) or not
      "select t.id from a t (nolock)",
      "select id, k from a t (nolock)",
      "select x.id, y.k from a x (nolock) join b y (nolock) on x.id = y.id",
      // the hint of an aliased table is a syntax error, as before
      "select t.id from a t with (nolock)",
      // the SQL Server table hint of an unaliased table matches the alias with and a
      // column list. it is refused too (Bug #77492, UniformSQLTableHintRefuseTest)
      "select id, k from a with (nolock)",
      "select id, k from a with(nolock)",
      "select id, k from a WITH (NOLOCK)",
      "select id, k from a with (nolock, readpast)",
      "select distinct id from a with (nolock)",
      "select id from a where id in (select id from b with (nolock))",
      "select * from a with (nolock) where id > 1",
      // a quoted with alias is a real derived column list
      "select * from (select id, k from a) \"with\"(p, q)",
      "select * from (select id, k from a) [with](p, q)",
      "select * from a as \"WITH\"(p, q)",
      "select a.id from a where a.k = (select max(t.p) from (select k from b) t(p))",
      // Derby rejects a qualified ORDER BY column of a column list
      "select t.p from (select id, k from a) t(p, q) order by t.p",
   };

   static List<String> refused() {
      List<String> list = new ArrayList<>(Arrays.asList(REFUSED));
      list.addAll(Arrays.asList(REFUSED_PARSE_ONLY));
      return list;
   }

   static String[] refusedRows() {
      return REFUSED;
   }

   @ParameterizedTest
   @MethodSource("refused")
   void derivedColumnListFailsParse(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), text);
      assertEquals(text, sql.getSQLString());

      UniformSQL fresh = new UniformSQL();
      fresh.setSQLString(text, false);
      assertEquals(text, fresh.getSQLString());
      assertTrue(fresh.isLossy(), text);

      for(String type : new String[] { "h2", "h2-ansi", "postgresql", "oracle" }) {
         assertFalse(XUtil.isQueryMergeable(query(text, dataSource(type))), type + ": " + text);
      }

      // the production (asynchronous) parse
      UniformSQL async = parseAsync(text);
      assertEquals(UniformSQL.PARSE_FAILED, async.getParseResult(), text);
      assertFalse(XUtil.isParsedSQL(async), text);
      assertEquals(text, async.getSQLString());
   }

   // the parse fails on the derived column list itself, whatever the data source
   @ParameterizedTest
   @ValueSource(strings = { "h2", "h2-ansi", "postgresql", "oracle" })
   void refusalNamesTheColumnList(String type) {
      String text = "select t.p, b.k from (select id, k from a) t(p, q), b where t.p = b.id";
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(dataSource(type));
      Exception ex = assertThrows(Exception.class,
         () -> sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD));
      assertTrue(ex.getMessage().contains("Unsupported derived column list: t(p,q)"),
                 ex.getMessage());
   }

   // the data cache path sends the original sql unchanged. before the fix it parsed, so the
   // normalizer sent the regenerated sql (the select * shape returned ID, K instead of P, Q)
   @ParameterizedTest
   @MethodSource("refusedRows")
   void originalSqlIsSent(String text) throws Exception {
      UniformSQL sql = parseAsync(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), text);

      JDBCQuery query = new JDBCQuery();
      query.setSQLDefinition(sql);
      JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(query);
      assertFalse(normalizer.isClearedSqlString(), text);
      assertEquals(text, sql.getSQLString());

      // the shape is sql a database runs
      try(Connection conn = connect(HSQLDB_URL); Statement stmt = conn.createStatement()) {
         rows(stmt, text);
      }
   }

   // with is reserved, so an unquoted with alias with a column list is not sql a database
   // runs as a column list
   @ParameterizedTest
   @ValueSource(strings = {
      "select * from (select id, k from a) with (p, q)",
      "select * from a with (p, q)",
   })
   void withAliasIsNotSql(String text) throws Exception {
      for(String url : new String[] { DERBY_URL, HSQLDB_URL }) {
         try(Connection conn = connect(url); Statement stmt = conn.createStatement()) {
            assertThrows(SQLException.class, () -> rows(stmt, text), url + ": " + text);
         }
      }
   }

   // a derived table without a column list still parses, regenerates to itself and
   // returns the same rows
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select t.id, t.k from (select id, k from a) t",
      "select t.p, t.q from (select id p, k q from a) t",
      "select t.p, t.q from (select id as p, k as q from a) as t",
      "select t.p, b.k from (select id p, k from a) t, b where t.p = b.id",
      "select t.p, b.k from (select id p, k from a) t left join b on t.p = b.id",
      "select a.id from a where a.id in (select t.id from (select id from b) t)",
   })
   void derivedTableWithoutListParses(String text) throws Exception {
      for(String type : new String[] { null, "derby", "derby-ansi" }) {
         JDBCDataSource ds = type == null ? null : dataSource(type);
         UniformSQL sql = parse(text, ds);
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), type + ": " + text);
         assertFalse(sql.isLossy(), text);

         sql.clearSQLString();
         String generated = normalize(sql.getSQLString());

         // round trip
         UniformSQL reparsed = parse(generated, ds);
         assertEquals(UniformSQL.PARSE_SUCCESS, reparsed.getParseResult(), generated);
         reparsed.clearSQLString();
         assertEquals(generated, normalize(reparsed.getSQLString()), type + " round trip");

         for(String url : new String[] { DERBY_URL, HSQLDB_URL }) {
            try(Connection conn = connect(url); Statement stmt = conn.createStatement()) {
               assertEquals(rows(stmt, text), rows(stmt, generated),
                            url + ": " + text + " -> " + generated);
            }
         }
      }
   }

   // a derived table StyleBI writes for a query built in the editor has no column list,
   // so its sql still parses
   @ParameterizedTest
   @ValueSource(strings = { "default", "derby", "derby-ansi", "h2", "h2-ansi", "postgresql",
                            "oracle" })
   void generatedDerivedTableReparses(String type) throws Exception {
      JDBCDataSource ds = "default".equals(type) ? null : dataSource(type);
      UniformSQL sub = new UniformSQL();
      sub.addTable("a");
      sub.getSelection().addColumn("a.id");
      sub.getSelection().addColumn("a.k");

      UniformSQL sql = new UniformSQL();
      sql.addTable("t", sub);
      sql.addTable("b");
      sql.getSelection().addColumn("t.id");
      sql.getSelection().addColumn("b.k");
      sql.addJoin(new XJoin(new XExpression("t.id", XExpression.FIELD),
                            new XExpression("b.id", XExpression.FIELD), "*="));
      sql.setDataSource(ds);
      sql.clearSQLString();
      String generated = normalize(sql.getSQLString());

      assertFalse(generated.contains("t("), generated);

      UniformSQL reparsed = parse(generated, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, reparsed.getParseResult(), type + ": " + generated);
      assertFalse(reparsed.isLossy(), generated);
      assertEquals("t", reparsed.getSelectTable(0).getAlias(), generated);

      // postgresql re-quotes the reparsed alias qualifier and oracle upper cases the
      // reparsed select list, which is unrelated to the derived table
      if(!"postgresql".equals(type) && !"oracle".equals(type)) {
         reparsed.clearSQLString();
         assertEquals(generated, normalize(reparsed.getSQLString()), type + " round trip");
      }
   }

   // the quoted alias the old regeneration wrote is a plain alias, so saved sql that holds
   // it still parses
   @Test
   void oldRegeneratedQuotedAliasParses() throws Exception {
      String text = "select * from (select id, k from a) \"t(p,q)\"";
      UniformSQL sql = parse(text, null);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertEquals("t(p,q)", sql.getSelectTable(0).getAlias().replace("\"", ""));
   }

   // the syntax checks of a parse with no model (a predicate) don't refuse the list.
   // XUtil.isSQLExpressionValid falls back to this parse on a refusal (Bug #77493), so a
   // scalar subquery with a derived column list stays valid (SQLExpressionValidityRefusalTest)
   @Test
   void guessParseDoesNotRefuse() throws Exception {
      SQLParser parser = new SQLParser(new SQLLexer(new StringReader(
         "(select t.p from (select id from a) t(p))")));
      parser.getInputState().guessing = 1;
      parser.value_exp();
   }

   @AfterAll
   static void dropDatabases() throws SQLException {
      try(Connection conn = DriverManager.getConnection(HSQLDB_URL);
          Statement stmt = conn.createStatement())
      {
         stmt.execute("shutdown");
      }

      try {
         DriverManager.getConnection(DERBY_URL + ";drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   // a = (1,10),(2,20),(3,null),(null,40), b = (1,100),(2,null),(4,400),(null,500)
   private static Connection connect(String url) throws SQLException {
      Connection conn = DriverManager.getConnection(url + (url.equals(DERBY_URL) ? ";create=true" : ""));

      try(Statement stmt = conn.createStatement()) {
         for(String table : new String[] { "a", "b" }) {
            try {
               stmt.execute("drop table " + table);
            }
            catch(SQLException ignore) {
               // not created yet
            }

            stmt.execute("create table " + table + " (id int, k int)");
         }

         stmt.execute("insert into a values (1, 10), (2, 20), (3, null), (null, 40)");
         stmt.execute("insert into b values (1, 100), (2, null), (4, 400), (null, 500)");
      }

      return conn;
   }

   // the regenerated select list may be reordered, so each row is compared as a map from
   // the column label to its value, and the rows as a multiset
   private static List<String> rows(Statement stmt, String query) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(ResultSet rs = stmt.executeQuery(query)) {
         ResultSetMetaData meta = rs.getMetaData();

         while(rs.next()) {
            Map<String, String> row = new TreeMap<>();

            for(int i = 1; i <= meta.getColumnCount(); i++) {
               row.put(meta.getColumnLabel(i).toUpperCase(), String.valueOf(rs.getObject(i)));
            }

            rows.add(row.toString());
         }
      }

      Collections.sort(rows);
      return rows;
   }

   private static JDBCQuery query(String text, JDBCDataSource ds) {
      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      sql.setDataSource(ds);
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      return query;
   }

   private static JDBCDataSource dataSource(String type) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds_" + type);
      ds.setProductVersion("19.0");

      switch(type.replace("-ansi", "")) {
      case "derby" -> {
         ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
         ds.setURL(DERBY_URL);
         ds.setProductVersion("10.17");
      }
      case "h2" -> {
         ds.setDriver("org.h2.Driver");
         ds.setURL("jdbc:h2:mem:test");
      }
      case "sql server" -> {
         ds.setDriver("com.microsoft.sqlserver.jdbc.SQLServerDriver");
         ds.setURL("jdbc:sqlserver://localhost:1433;databaseName=test");
      }
      case "postgresql" -> {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost:5432/test");
      }
      case "oracle" -> {
         ds.setDriver("oracle.jdbc.OracleDriver");
         ds.setURL("jdbc:oracle:thin:@localhost:1521:test");
      }
      default -> throw new IllegalArgumentException(type);
      }

      ds.setAnsiJoin(type.endsWith("-ansi"));
      String helper = SQLHelper.getSQLHelper(ds).getSQLHelperType();
      assertEquals(type.replace("-ansi", ""), helper, "helper for " + type);
      return ds;
   }

   // the generated sql is pretty-printed
   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }

   private static UniformSQL parseAsync(String text) throws Exception {
      UniformSQL sql = new UniformSQL();

      synchronized(sql) {
         sql.setSQLString(text);
         sql.wait(20000);
      }

      return sql;
   }
}
