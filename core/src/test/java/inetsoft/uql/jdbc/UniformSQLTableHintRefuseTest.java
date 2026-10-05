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
 * Bug #77492, a SQL Server table hint of an unaliased table ({@code from a with (nolock)})
 * parsed as a success with the alias {@code with} and the column list {@code nolock}. The
 * regenerated sql wrote one quoted alias {@code "with(nolock)"}, which hides the table name,
 * so a qualified column ({@code a.id}) failed, and in a subquery of the same table it bound to
 * the outer query's table and silently returned wrong rows. The parse must fail at every query
 * level, so the original sql runs.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 UniformSQLTableHintRefuseTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLTableHintRefuseTest {
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

   private static final String DERBY_URL = "jdbc:derby:memory:bug77492";
   private static final String HSQLDB_URL = "jdbc:hsqldb:mem:bug77492";

   // no data source, and SQL Server with and without ansi joins
   private static final String[] TYPES = { null, "sql server", "sql server-ansi" };

   // a table hint at every query level and in every from clause position. each fails on
   // the hint itself
   private static final String[] REFUSED = {
      // the reported shape, qualified columns regenerated as a.id of the alias "with(nolock)"
      "select a.id from a with (nolock)",
      "select id, k from a with (nolock)",
      "select id, k from a as with (nolock)",
      // case, spacing and comments
      "select a.id from a WiTh(NoLock)",
      "select a.id from a WITH (NOLOCK)",
      "select a.id from a with ( nolock )",
      "select a.id from a with/*c*/(nolock)",
      "select a.id from a with -- c\n (nolock)",
      "select a.id from a with (nolock) -- trailing",
      "select a.id from a with (nolock) /* trailing */",
      "select a.id from a with (nolock, readpast)",
      "select top 10 a.id from a with (nolock)",
      "select a.id from a with (nolock) where a.k > 1 order by a.id",
      // quoted and qualified table names
      "select id from [a] with (nolock)",
      "select id from \"a\" with (nolock)",
      "select a.id from dbo.a with (nolock)",
      "select id from dbo.[a] with (nolock)",
      "select id from db.dbo.a with (nolock)",
      // comma lists
      "select a.id, b.k from a with (nolock), b where a.id = b.id",
      "select a.id, b.k from a, b with (nolock) where a.id = b.id",
      "select a.id, b.k from a with (nolock), b with (nolock) where a.id = b.id",
      // joins, the hint on either operand
      "select a.id, b.k from a with (nolock) join b on a.id = b.id",
      "select a.id, b.k from a join b with (nolock) on a.id = b.id",
      "select a.id, b.k from a with (nolock) inner join b with (nolock) on a.id = b.id",
      "select a.id, b.k from a with (nolock) left join b with (nolock) on a.id = b.id",
      "select a.id, b.k from a left outer join b with (nolock) on a.id = b.id",
      "select a.id, b.k from a right join b with (nolock) on a.id = b.id",
      "select a.id, b.k from a with (nolock) full outer join b on a.id = b.id",
      "select a.id, b.k from a with (nolock) cross join b",
      "select a.id, b.k from a cross join b with (nolock)",
      "select a.id from a join b with (nolock) using (id)",
      // nested joins
      "select a.id, b.k from (a join b with (nolock) on a.id = b.id)",
      "select a.id, b.k from (a with (nolock) join b on a.id = b.id)",
      "select a.id, c.k from ((a join b with (nolock) on a.id = b.id) join c on b.id = c.id)",
      "select a.id, c.k from a join (b join c with (nolock) on b.id = c.id) on a.id = b.id",
      // subqueries in IN, EXISTS, the select list, HAVING, ORDER BY and ON
      "select id from a where id in (select id from b with (nolock))",
      "select a.id from a where a.id in (select b.id from b with (nolock))",
      "select b.id from b where b.k in (select b.k from b with (nolock) where b.id > 1)",
      "select a.id from a where exists (select 1 from b with (nolock) where b.id = a.id)",
      "select a.id, (select max(b.k) from b with (nolock) where b.id = a.id) m from a",
      "select a.k from a where a.k = (select max(a.k) from a with (nolock))",
      "select a.k, count(*) from a group by a.k having count(*) > " +
         "(select count(*) from b with (nolock))",
      "select a.id from a order by (select max(b.k) from b with (nolock) where b.id = a.id)",
      "select a.id, b.k from a join b on a.id = b.id and b.k in " +
         "(select c.k from c with (nolock))",
      // derived tables: the body, a nested body, the alias and a join operand
      "select t.id from (select b.id from b with (nolock)) t",
      "select x.id from (select t.id from (select b.id from b with (nolock)) t) x",
      "select id from (select id from b) with (nolock)",
      "select id from (select id from b) as with (nolock)",
      "select a.id, t.k from a join (select id, k from b with (nolock)) t on a.id = t.id",
      "select a.id from a join (select id from b) with (nolock) on a.id = 1",
   };

   // a hint the grammar never matched (an aliased table, a hint with arguments or several
   // words) is a syntax error, as before
   private static final String[] SYNTAX_ERRORS = {
      "select x.id from a x with (nolock)",
      "select x.id from a as x with (nolock)",
      "select id from a with (index(ix))",
      "select id from a with (index = ix)",
      "select id from a with (updlock holdlock)",
      "select id from a with (nolock) option (maxdop 1)",
   };

   static String[] refused() {
      return REFUSED;
   }

   @ParameterizedTest
   @MethodSource("refused")
   void tableHintFailsParse(String text) throws Exception {
      for(String type : TYPES) {
         JDBCDataSource ds = type == null ? null : dataSource(type);
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(ds);
         Exception ex = assertThrows(Exception.class,
            () -> sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD), type + ": " + text);
         assertTrue(ex.getMessage().contains("Unsupported table hint: "),
                    type + ": " + text + ": " + ex.getMessage());
         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), type + ": " + text);

         if(ds != null) {
            assertFalse(XUtil.isQueryMergeable(query(text, ds)), type + ": " + text);
         }
      }

      assertParseFailed(text);
   }

   @ParameterizedTest
   @MethodSource("syntaxErrors")
   void hintSyntaxErrorFailsParse(String text) throws Exception {
      assertParseFailed(text);
   }

   static String[] syntaxErrors() {
      return SYNTAX_ERRORS;
   }

   // the refusal compares the alias with equalsIgnoreCase, so a dotted capital I under a
   // Turkish default locale doesn't matter
   @Test
   void upperCaseHintFailsParseInTurkish() throws Exception {
      Locale locale = Locale.getDefault();

      try {
         Locale.setDefault(Locale.forLanguageTag("tr-TR"));
         String text = "SELECT A.ID FROM A WITH (NOLOCK)";
         UniformSQL sql = new UniformSQL();
         Exception ex = assertThrows(Exception.class,
            () -> sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD));
         assertTrue(ex.getMessage().contains("Unsupported table hint: WITH(NOLOCK)"),
                    ex.getMessage());
         assertParseFailed(text);
      }
      finally {
         Locale.setDefault(locale);
      }
   }

   // the syntax checks of a parse with no model don't refuse the hint. XUtil falls back to
   // that parse on a refusal (Bug #77493), so a scalar subquery with a hint stays valid
   @ParameterizedTest
   @ValueSource(strings = {
      "select a.id from a with (nolock)",
      "select max(a.k) from a with (nolock, readpast)",
      "select max(a.id) from a with (nolock) join b with (nolock) on a.id = b.id",
      "select max(id) from a where id in (select id from b with (nolock))",
      "select max(t.id) from (select b.id from b with (nolock)) t",
      "select max(id) from (select id from b) with (nolock)",
   })
   void scalarSubqueryWithHintIsValid(String subquery) throws Exception {
      String exp = "(" + subquery + ")";

      // the normal parse refuses the hint of the subquery
      assertThrows(antlr.SemanticException.class,
                   () -> new SQLParser(new SQLLexer(new StringReader(exp))).value_exp(), exp);

      assertTrue(XUtil.isSQLExpressionValid(exp), exp);
      assertNotNull(XUtil.parseSQLExpressionSyntax(exp), exp);
   }

   // a table aliased with without a column list, the quoted alias the old regeneration wrote
   // (saved sql may hold it), and ordinary aliases and derived tables still parse
   @ParameterizedTest
   @ValueSource(strings = {
      "select id from a with",
      "select with.id from a as with",
      "select id from a with where id > 1",
      "select id from a \"with\"",
      "select \"with(nolock)\".id from a \"with(nolock)\"",
      "select id, k from a \"with(nolock)\"",
      "select x.id from a x",
      "select x.id from a as x where x.k > 1",
      "select t.id from (select id from b) t",
      "select a.id, b.k from a join b on a.id = b.id",
   })
   void withoutHintParses(String text) throws Exception {
      for(String type : TYPES) {
         JDBCDataSource ds = type == null ? null : dataSource(type);
         UniformSQL sql = parse(text, ds);
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), type + ": " + text);
         assertFalse(sql.isLossy(), text);
      }

      UniformSQL async = parseAsync(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, async.getParseResult(), text);
      assertTrue(XUtil.isParsedSQL(async), text);
   }

   // a self-referencing IN subquery, as written by a user who puts with (nolock) on every
   // table. before the fix it parsed, and the regenerated alias "with(nolock)" hid the inner
   // b, so the inner b.k and b.id bound to the outer b and the query returned 2 rows instead
   // of 3. now the parse fails, and the original sql is sent
   @Test
   void inSubqueryKeepsOriginalSql() throws Exception {
      String text = "select b.id from b where b.k in " +
         "(select b.k from b with (nolock) where b.id > 1)";
      // what the parse regenerated before the fix, and the original without its hint, which
      // Derby and HSQLDB don't support
      String regenerated = "select b.id from b where b.k IN " +
         "( select b.k from b \"with(nolock)\" where b.id > 1)";
      String unhinted = "select b.id from b where b.k in (select b.k from b where b.id > 1)";

      for(String url : new String[] { DERBY_URL, HSQLDB_URL }) {
         try(Connection conn = connect(url); Statement stmt = conn.createStatement()) {
            assertEquals(List.of("1", "2", "3"), rows(stmt, unhinted), url);
            assertEquals(List.of("2", "3"), rows(stmt, regenerated), url);
         }
      }

      for(String type : TYPES) {
         JDBCDataSource ds = type == null ? null : dataSource(type);
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(ds);
         assertThrows(Exception.class,
            () -> sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD), type);
         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), type);
      }

      assertParseFailed(text);

      // the data cache path sends the original sql unchanged
      UniformSQL sql = parseAsync(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
      JDBCQuery query = new JDBCQuery();
      query.setSQLDefinition(sql);
      JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(query);
      assertFalse(normalizer.isClearedSqlString());
      assertEquals(text, sql.getSQLString());
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

   private static void assertParseFailed(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), text);
      assertEquals(text, sql.getSQLString());

      UniformSQL fresh = new UniformSQL();
      fresh.setSQLString(text, false);
      assertEquals(text, fresh.getSQLString());
      assertTrue(fresh.isLossy(), text);

      // the production (asynchronous) parse
      UniformSQL async = parseAsync(text);
      assertEquals(UniformSQL.PARSE_FAILED, async.getParseResult(), text);
      assertFalse(XUtil.isParsedSQL(async), text);
      assertEquals(text, async.getSQLString());
   }

   // b = (1,100),(2,200),(3,100)
   private static Connection connect(String url) throws SQLException {
      Connection conn = DriverManager.getConnection(url + (url.equals(DERBY_URL) ? ";create=true" : ""));

      try(Statement stmt = conn.createStatement()) {
         try {
            stmt.execute("drop table b");
         }
         catch(SQLException ignore) {
            // not created yet
         }

         stmt.execute("create table b (id int, k int)");
         stmt.execute("insert into b values (1, 100), (2, 200), (3, 100)");
      }

      return conn;
   }

   private static List<String> rows(Statement stmt, String query) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(ResultSet rs = stmt.executeQuery(query)) {
         while(rs.next()) {
            rows.add(String.valueOf(rs.getObject(1)));
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
      ds.setName("ds_77492_" + type);
      ds.setProductVersion("19.0");
      ds.setDriver("com.microsoft.sqlserver.jdbc.SQLServerDriver");
      ds.setURL("jdbc:sqlserver://localhost:1433;databaseName=test");
      ds.setAnsiJoin(type.endsWith("-ansi"));
      assertEquals("sql server", SQLHelper.getSQLHelper(ds).getSQLHelperType(),
                   "helper for " + type);
      return ds;
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
