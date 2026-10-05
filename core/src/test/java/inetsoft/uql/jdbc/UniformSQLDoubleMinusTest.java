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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77754, a binary minus whose right operand starts with a unary minus, e.g.
 * {@code b.a - -1}, was stored by the parser as {@code b.a-- 1}: the binary operator joins
 * its operands without spaces and the unary minus is written as {@code - 1}. Every
 * regenerated query then held a {@code --} line comment, which silently changed the rows
 * (the rest of the line was dropped) or broke the SQL.
 *
 * The operator and a right operand starting with a minus must be separated by a space, and
 * every other expression text must stay as it was, since it names unaliased select columns.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLDoubleMinusTest {
   private static final String DERBY_URL = "jdbc:derby:memory:bug77754;create=true";
   private static final String[] TYPES =
      { "default", "h2", "h2-ansi", "postgresql", "oracle", "mysql", "sql server", "derby" };

   @BeforeAll
   static void createTables() throws Exception {
      try(Connection conn = derby(); Statement stmt = conn.createStatement()) {
         // b for the SQL as written, "b" for the SQL the postgresql helper writes, which
         // quotes every name in lower case
         for(String table : new String[] { "b", "\"b\"" }) {
            try {
               stmt.execute("drop table " + table);
            }
            catch(SQLException ignore) {
               // first run
            }
         }

         stmt.execute("create table b (id int, k int, a int, c int, owner varchar(10))");
         stmt.execute("create table \"b\" (\"id\" int, \"k\" int, \"a\" int, \"c\" int, " +
                         "\"owner\" varchar(10))");

         for(String table : new String[] { "b", "\"b\"" }) {
            stmt.execute("insert into " + table + " values (6, 1, 6, 2, 'u1'), " +
                            "(8, 1, 7, -1, 'u2'), (9, 1, 9, 3, 'u1')");
         }
      }
   }

   @AfterAll
   static void dropDatabase() throws Exception {
      try {
         derbyDriver().connect("jdbc:derby:memory:bug77754;drop=true", new Properties());
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   // the text the default helper writes, with the minus signs apart, in every clause and at
   // every query level
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select b.id from b where b.id = 5 - -1|select b.id from b where b.id = 5- - 1",
      "select b.id from b where b.id = b.a - -b.c|select b.id from b where b.id = b.a- - b.c",
      "select b.id from b where b.id = -b.a - -1|select b.id from b where b.id = - b.a- - 1",
      "select b.id from b where b.id = b.a - - 1|select b.id from b where b.id = b.a- - 1",
      "select b.id from b where b.id = b.a- -1|select b.id from b where b.id = b.a- - 1",
      "select b.id from b where b.id = b.a - -(b.c)|" +
         "select b.id from b where b.id = b.a- - (b.c)",
      "select b.id from b where b.id = b.a - -abs(b.c)|" +
         "select b.id from b where b.id = b.a- - abs(b.c)",
      "select b.id from b where b.id = b.a - -b.c * 2|" +
         "select b.id from b where b.id = b.a- - b.c*2",
      "select b.id from b where b.id = 1 - 2 - -3|select b.id from b where b.id = 1-2- - 3",
      "select b.id from b where b.a - -1 = b.id|select b.id from b where b.a- - 1 = b.id",
      "select b.id from b where b.id between b.a - -1 and 9|" +
         "select b.id from b where b.id BETWEEN b.a- - 1 and 9",
      "select b.id from b where b.id in (b.a - -1, 3)|" +
         "select b.id from b where b.id IN (b.a- - 1,3)",
      "select b.a - -1 as x from b|select b.a- - 1 as x from b",
      "select b.id from b order by b.a - -1|select b.id from b order by b.a- - 1 asc",
      "select b.owner from b group by b.owner having sum(b.a) - -1 > 0|" +
         "select b.owner from b group by b.owner having sum(b.a)- - 1 > 0",
      "select b.id from b where b.id = (select max(c.a) - -1 from b c)|" +
         "select b.id from b where b.id = ( select max(c.a)- - 1 from b c)",
   })
   void minusSignsAreKeptApart(String text, String expected) throws Exception {
      JDBCDataSource ds = dataSource("default");
      String generated = regenerate(parse(text, ds), text);

      assertEquals(expected, generated);
      assertRoundTrip(generated, ds, text);
   }

   // every helper writes the model text, so none of them may write "--"
   @ParameterizedTest
   @ValueSource(strings = {
      "select b.id from b where b.id = b.a - -1 and b.owner = 'u1'",
      "select b.id from b where b.k = 1 and b.id = b.a - -b.c",
      "select b.id, b.a - -1 from b",
      "select b.id, b.a - -1 as x from b where b.a - -1 > 7 order by b.a - -1",
      "select b.owner, sum(b.a) as s from b group by b.owner having sum(b.a - -1) > 0",
      "select b.id, case when b.c - -1 > 0 then 1 else 0 end as f from b",
      "select b.id, (select max(c.a) - -1 from b c where c.owner = b.owner) as m from b",
      "select b.id from b where b.id in (select c.id from b c where c.id = c.a - -1)",
      "select b.id from b where exists (select c.id from b c where c.id = b.a - -2)",
      "select b.a - -1 as x, count(b.id) as n from b group by b.a - -1",
   })
   void noHelperWritesALineComment(String text) throws Exception {
      for(String type : TYPES) {
         JDBCDataSource ds = dataSource(type);
         String generated = regenerate(parse(text, ds), text);
         String message = type + ": " + text + " -> " + generated;

         assertFalse(generated.contains("--"), message);
         assertTrue(generated.contains("- -"), message);

         // postgresql writes a select list subquery that re-parses differently whatever its
         // operators are, see selectListSubqueryPassesRoundTripGuard
         if(!type.equals("postgresql") || !text.contains(", (select")) {
            assertRoundTrip(generated, ds, message);
         }
      }
   }

   // the original and the regenerated SQL return the same rows on Derby, both as the query
   // editor regenerates it and as the data cache normalizer runs a plain query. The SQL runs
   // as generated, one condition per line, where a line comment drops the rest of its line.
   @ParameterizedTest
   @ValueSource(strings = {
      "select b.id from b where b.id = b.a - -1 and b.owner = 'u1'",
      "select b.id from b where b.k = 1 and b.id = b.a - -1",
      "select b.id from b where b.id = b.a - -1 and b.k = 1 and b.owner = 'u2'",
      "select b.id from b where b.id = (select max(c.a) - -1 from b c where c.owner = 'u1')",
      "select b.id from b where b.id in (select c.id from b c where c.owner = 'u2' and " +
         "c.id = c.a - -1)",
      "select b.id from b where b.id in (b.a - -1, 100) and b.owner = 'u1'",
      "select b.id, b.a - -1 as x from b where b.owner = 'u1'",
      "select b.id, b.a - -1 from b where b.k = 1",
      "select b.id, (select max(c.a) - -1 from b c where c.owner = b.owner) as m from b",
      "select b.owner, sum(b.a) as s from b group by b.owner having sum(b.a) - -1 > 10",
      "select b.id, case when b.c - -1 > 0 then 1 else 0 end as f from b",
      "select b.id from b where b.a - b.c - -b.c = b.a and b.owner = 'u1'",
   })
   void sameRowsOnDerby(String text) throws Exception {
      JDBCDataSource ds = dataSource("derby");
      String generated = generate(parse(text, ds), text);
      String normalized = normalized(text, ds);

      try(Connection conn = derby()) {
         List<String> expected = rows(conn, text);
         assertEquals(expected, rows(conn, generated), text + " -> " + generated);
         assertEquals(expected, rows(conn, normalized),
                      "cache normalizer: " + text + " -> " + normalized);
      }
   }

   // the value of a select list scalar subquery, by row
   @Test
   void selectListSubqueryValue() throws Exception {
      String text = "select b.id, (select max(c.a) - -1 from b c where c.owner = b.owner) as m " +
         "from b";
      String generated = generate(parse(text, dataSource("derby")), text);

      try(Connection conn = derby()) {
         assertEquals(List.of("6|10|", "8|8|", "9|10|"), rows(conn, text));
         assertEquals(rows(conn, text), rows(conn, generated), generated);
      }
   }

   // GROUP BY an expression on postgresql, which writes the names quoted, run on Derby
   // against a table with the quoted lower case names
   @Test
   void groupByOnPostgresql() throws Exception {
      String text = "select b.a - -1 as x, count(b.id) as n from b group by b.a - -1";
      String generated = generate(parse(text, dataSource("postgresql")), text);

      assertTrue(normalize(generated).contains("group by \"b\".\"a\"- - 1"), generated);

      try(Connection conn = derby()) {
         assertEquals(List.of("1|10|", "1|7|", "1|8|"), rows(conn, text));
         assertEquals(rows(conn, text), rows(conn, generated), generated);
      }
   }

   // a freshly parsed select list subquery is generated to its stored text again, so the
   // sentinel rewrite of its parameters (#77706) can replace it. On postgresql the alias of
   // the subquery's table is written unquoted and its columns are then re-parsed as
   // c."owner", so the guard keeps the text as written for every operator, as it does for
   // the + 1 control.
   @Test
   void selectListSubqueryPassesRoundTripGuard() {
      String text = "select b.id, (select max(c.a) - -1 from b c where c.owner = $(p)) as m " +
         "from b";
      String control = text.replace("- -1", "+ 1");

      for(String type : TYPES) {
         JDBCDataSource ds = dataSource(type);
         String column = subqueryColumn(parse(text, ds));
         String controlColumn = subqueryColumn(parse(control, ds));

         assertFalse(column.contains("--"), type + ": " + column);
         assertEquals(UniformSQL.parseSelectListSubquery(controlColumn, ds) != null,
                      UniformSQL.parseSelectListSubquery(column, ds) != null,
                      type + ": " + column);

         if(!type.equals("postgresql")) {
            assertNotNull(UniformSQL.parseSelectListSubquery(column, ds), type + ": " + column);
         }
      }
   }

   // the column text names an unaliased select item, which has no line comment now
   @Test
   void unaliasedSelectColumnText() {
      UniformSQL sql = parse("select b.a - -1, b.id from b", dataSource("default"));
      assertEquals("b.a- - 1", sql.getSelection().getColumn(0));
   }

   // the text of every other expression is unchanged, since it names unaliased select items
   // and is matched against saved column names
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "b.a + -1|b.a+- 1",
      "b.a * -1|b.a*- 1",
      "b.a / -1|b.a/- 1",
      "b.a - (-1)|b.a-(- 1)",
      "b.a - +1|b.a-+ 1",
      "b.a - 1|b.a-1",
      "b.a-1|b.a-1",
      "b.a * 2|b.a*2",
      "b.a*b.c|b.a*b.c",
      "-b.a|- b.a",
      "b.a + b.c - 1|b.a+b.c-1",
   })
   void otherExpressionTextIsUnchanged(String expression, String expected) throws Exception {
      String text = "select " + expression + ", b.id from b where " + expression + " > 0";
      UniformSQL sql = parse(text, dataSource("default"));

      assertEquals(expected, sql.getSelection().getColumn(0));
      assertEquals("select " + expected + ", b.id from b where " + expected + " > 0",
                   regenerate(sql, text));
   }

   private static String subqueryColumn(UniformSQL sql) {
      JDBCSelection selection = (JDBCSelection) sql.getSelection();

      for(int i = 0; i < selection.getColumnCount(); i++) {
         if(selection.getColumn(i).startsWith("(")) {
            return selection.getColumn(i);
         }
      }

      return fail("no subquery column: " + sql);
   }

   // what JDBCQueryCacheNormalizer runs for a parsed, not lossy query: the sql string is
   // cleared and the SQL is regenerated with a sorted select list
   private static String normalized(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = parse(text, ds);
      JDBCQuery query = new JDBCQuery();
      query.setName("q77754");
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(query);
      assertTrue(normalizer.isClearedSqlString(), text);
      return sql.getSQLString();
   }

   private static List<String> rows(Connection conn, String query) {
      try {
         return SQLHelperNotEqualJoinTest.RowCompare.rows(conn, query);
      }
      catch(SQLException ex) {
         return fail("doesn't run: " + query + ": " + ex.getMessage());
      }
   }

   // the regenerated SQL re-parses and is generated to itself
   private static void assertRoundTrip(String generated, JDBCDataSource ds, String message) {
      assertEquals(generated, regenerate(parse(generated, ds), generated),
                   "round trip, " + message);
   }

   // parse the way a query of the data source is parsed
   private static UniformSQL parse(String text, JDBCDataSource ds) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);

      try {
         sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      }
      catch(Exception ex) {
         fail("parse failed: " + text + ": " + ex.getMessage());
      }

      return sql;
   }

   private static String regenerate(UniformSQL sql, String text) {
      return normalize(generate(sql, text));
   }

   // the SQL as it's generated and sent to the database
   private static String generate(UniformSQL sql, String text) {
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      sql.clearSQLString();
      return sql.getSQLString();
   }

   private static JDBCDataSource dataSource(String type) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77754_" + type);
      ds.setProductVersion("19.0");

      switch(type.replace("-ansi", "")) {
      case "default" -> {
         ds.setDriver("org.example.GenericDriver");
         ds.setURL("jdbc:example://localhost/test");
      }
      case "h2" -> {
         ds.setDriver("org.h2.Driver");
         ds.setURL("jdbc:h2:mem:test");
      }
      case "postgresql" -> {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost:5432/test");
      }
      case "oracle" -> {
         ds.setDriver("oracle.jdbc.OracleDriver");
         ds.setURL("jdbc:oracle:thin:@localhost:1521:test");
      }
      case "mysql" -> {
         ds.setDriver("com.mysql.cj.jdbc.Driver");
         ds.setURL("jdbc:mysql://localhost:3306/test");
      }
      case "sql server" -> {
         ds.setDriver("com.microsoft.sqlserver.jdbc.SQLServerDriver");
         ds.setURL("jdbc:sqlserver://localhost:1433;databaseName=test");
      }
      case "derby" -> {
         ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
         ds.setURL("jdbc:derby:memory:test;create=true");
         ds.setProductVersion("10.17");
      }
      default -> throw new IllegalArgumentException(type);
      }

      ds.setAnsiJoin(type.endsWith("-ansi"));
      assertEquals(type.replace("-ansi", ""), SQLHelper.getSQLHelper(ds).getSQLHelperType(),
                   "helper for " + type);
      return ds;
   }

   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static Driver derbyDriver() throws Exception {
      return (Driver) Class.forName("org.apache.derby.iapi.jdbc.AutoloadedDriver")
         .getDeclaredConstructor().newInstance();
   }

   private static Connection derby() throws Exception {
      return derbyDriver().connect(DERBY_URL, new Properties());
   }
}
