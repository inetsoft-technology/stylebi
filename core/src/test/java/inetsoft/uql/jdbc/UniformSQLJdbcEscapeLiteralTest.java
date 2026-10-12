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

import antlr.Token;
import inetsoft.test.*;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.util.sqlparser.SQLLexer;
import inetsoft.uql.util.sqlparser.SQLParser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.StringReader;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77662, a brace inside a quoted unit of a JDBC escape, e.g. {fn concat('}', s)}, ended
 * the escape. The escape token counted every '{' and '}' as a brace, including one inside a
 * literal or a quoted name, so it stopped at the quoted brace and the rest of the escape
 * started an unterminated literal. The query failed to parse, and the SQL expression checks
 * (calc fields, the expression dialog, the logical model and the query manager) rejected the
 * expression, which they accepted before #77640 only because the lexer dropped the rest.
 *
 * An escape must keep a '..', "..", `..` or [..] unit whole, so a brace inside one is text.
 * An unterminated unit inside an escape must fail the parse so the original SQL runs, and so
 * must an escape used as a table, e.g. {oj ...} in FROM, which was written back as one quoted
 * table name.
 *
 * A string alias holding a double quote, e.g. 'a"b', is written as ALIAS_n, but the column
 * keeps its name, so that part of the bug needs no change and is only pinned here.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLJdbcEscapeLiteralTest {
   private static final String DERBY_URL = "jdbc:derby:memory:bug77662;create=true";
   private static final String HSQLDB_URL = "jdbc:hsqldb:mem:bug77662";
   // the PostgreSQL family quotes names in-band, h2 doesn't, oracle is the non-ANSI dialect
   private static final String[] TYPES =
      { "postgresql", "snowflake", "exasol", "h2", "h2-ansi", "oracle", "derby" };

   // a brace inside a quoted unit of an escape. All failed the parse. The second value is the
   // escape that must be written back verbatim, the third whether the SQL runs on Derby and
   // HSQLDB.
   static Stream<Arguments> braceInQuotedUnit() {
      return Stream.of(
         Arguments.of("select {fn concat('}', s)} from t", "{fn concat('}', s)}", true),
         Arguments.of("select {fn concat('{', s)} from t", "{fn concat('{', s)}", true),
         Arguments.of("select {fn concat('}''', s)} as x from t where k = 1",
                      "{fn concat('}''', s)}", true),
         Arguments.of("select {fn concat('a''}', s)} from t", "{fn concat('a''}', s)}", true),
         Arguments.of("select id from t where s = {fn concat('}', 'x')}",
                      "{fn concat('}', 'x')}", true),
         // the second escape was split into two escapes with no lexer error
         Arguments.of("select {fn concat('{}', s)} as x, {fn concat('}{', s)} as y from t",
                      "{fn concat('}{', s)}", true),
         Arguments.of("select {fn ucase(\"a}b\")} from t", "{fn ucase(\"a}b\")}", true),
         Arguments.of("select {fn ucase(`a}b`)} from t", "{fn ucase(`a}b`)}", false),
         Arguments.of("select {fn ucase([a}b])} from t", "{fn ucase([a}b])}", false),
         // a doubled quote is read as two adjacent quoted units
         Arguments.of("select {fn ucase(\"a\"\"}b\")} from t", "{fn ucase(\"a\"\"}b\")}", true));
   }

   // quoted units holding the other quote characters, which already parsed and must keep
   // doing so, and escapes that hold no quoted brace
   static Stream<Arguments> validEscapes() {
      return Stream.of(
         Arguments.of("select {fn ucase(\"it's\")} from t", "{fn ucase(\"it's\")}", true),
         Arguments.of("select {fn ucase(`it's`)} from t", "{fn ucase(`it's`)}", false),
         Arguments.of("select {fn ucase([it's])} from t", "{fn ucase([it's])}", false),
         Arguments.of("select {fn concat('a', s)} from t", "{fn concat('a', s)}", true),
         Arguments.of("select {fn concat('it''s', s)} from t", "{fn concat('it''s', s)}", true),
         Arguments.of("select {fn concat({fn ucase(s)}, s)} from t",
                      "{fn concat({fn ucase(s)}, s)}", true),
         Arguments.of("select {fn abs(arr[1])} from t", "{fn abs(arr[1])}", false));
   }

   @BeforeAll
   static void createTables() throws Exception {
      for(Connection conn : new Connection[] { derby(), hsqldb() }) {
         try(conn; Statement stmt = conn.createStatement()) {
            try {
               stmt.execute("drop table t");
            }
            catch(SQLException ignore) {
               // first run
            }

            stmt.execute("create table t (id int, s varchar(20), k int, d date, " +
                            "\"a}b\" varchar(10), \"it's\" varchar(10), " +
                            "\"a\"\"}b\" varchar(10))");
            stmt.execute("insert into t values " +
               "(1, '}x', 1, cast('2020-01-01' as date), 'p}', 'q', 'u'), " +
               "(2, '{', 2, null, null, 'it''s', '}'), " +
               "(3, 'a', null, cast('2021-06-30' as date), '{}', null, null), " +
               "(4, null, 1, null, 'r', '}', 'v{')");
         }
      }
   }

   @AfterAll
   static void dropDatabases() throws Exception {
      try(Connection conn = DriverManager.getConnection(HSQLDB_URL);
          Statement stmt = conn.createStatement())
      {
         stmt.execute("shutdown");
      }

      try {
         derbyDriver().connect("jdbc:derby:memory:bug77662;drop=true", new Properties());
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   // the cause: the whole escape must reach the parser as one token
   @Test
   void escapeWithQuotedBraceIsOneToken() {
      assertEquals(List.of("{fn concat('}', s)}"),
                   escapeTokens("select {fn concat('}', s)} from t"));
      assertEquals("select SPIDENT_BRACKET from IDENT EOF",
                   tokens("select {fn concat('}', s)} from t"));
      assertEquals(List.of("{fn concat('{}', s)}", "{fn concat('}{', s)}"), escapeTokens(
         "select {fn concat('{}', s)} as x, {fn concat('}{', s)} as y from t"));
      assertEquals(List.of("{fn ucase(\"a}b\")}"),
                   escapeTokens("select {fn ucase(\"a}b\")} from t"));
   }

   @Test
   void escapeWithoutQuotedBraceIsOneToken() {
      assertEquals("select SPIDENT_BRACKET from IDENT EOF",
                   tokens("select {fn concat('a', s)} from t"));
      assertEquals(List.of("{fn ucase(\"it's\")}"),
                   escapeTokens("select {fn ucase(\"it's\")} from t"));
   }

   @ParameterizedTest
   @MethodSource("braceInQuotedUnit")
   void quotedBraceInEscapeIsKept(String text, String escape, boolean rows) throws Exception {
      assertEscapeKept(text, escape, rows);
   }

   @ParameterizedTest
   @MethodSource("validEscapes")
   void validEscapeStillParses(String text, String escape, boolean rows) throws Exception {
      assertEscapeKept(text, escape, rows);
   }

   // date and timestamp escapes are still read as dates: oracle writes them as To_Date, h2
   // writes them back
   @Test
   void dateEscapeIsStillADate() throws Exception {
      String text = "select id from t where d = {d '2020-01-01'}";
      String ts = "select {ts '2020-01-01 00:00:00'} as x from t";

      for(String type : TYPES) {
         JDBCDataSource ds = dataSource(type);
         assertRoundTrip(regenerate(parse(text, ds), text), ds, type + ": " + text);
         assertRoundTrip(regenerate(parse(ts, ds), ts), ds, type + ": " + ts);
      }

      String oracle = regenerate(parse(text, dataSource("oracle")), text);
      assertTrue(oracle.contains("To_Date('2020-01-01', 'YYYY-MM-DD')"), oracle);
      String h2 = regenerate(parse(text, dataSource("h2")), text);
      assertTrue(h2.endsWith("where d = {d '2020-01-01'}"), h2);
      h2 = regenerate(parse(ts, dataSource("h2")), ts);
      assertTrue(h2.endsWith("{ts '2020-01-01 00:00:00'} as x from t"), h2);
      assertSameRows(text);
   }

   // the SQL expression checks accept an escape with a quoted brace
   @ParameterizedTest
   @ValueSource(strings = {
      "{fn concat('}', s)}",
      "{fn concat('{', s)}",
      "{fn ucase(\"a}b\")}",
      "{fn ucase(`a}b`)}",
      "{fn ucase([a}b])}",
      "{fn ucase(\"a\"\"}b\")}",
      "{fn concat('}', s)} + 1",
   })
   void expressionWithQuotedBraceIsValid(String exp) {
      assertDoesNotThrow(() -> XUtil.parseSQLExpressionSyntax(exp), exp);
      assertTrue(XUtil.isSQLExpressionValid(exp), exp);
   }

   // and still accept the other quote characters inside an escape
   @ParameterizedTest
   @ValueSource(strings = {
      "{fn ucase(\"it's\")}",
      "{fn ucase(`it's`)}",
      "{fn ucase([it's])}",
      "{fn concat('a', s)}",
      "{fn concat({fn ucase(a)}, b)}",
      "{d '2020-01-01'}",
   })
   void expressionWithOtherQuotesIsValid(String exp) {
      assertTrue(XUtil.isSQLExpressionValid(exp), exp);
   }

   // an unterminated quoted unit inside an escape, or an escape that isn't closed, can't be
   // represented, so the parse fails and the original SQL runs. A backslash-escaped quote
   // inside an escape leaves a unit that doesn't close, as it does outside one (#77640).
   @ParameterizedTest
   @ValueSource(strings = {
      "select {fn concat('a, s)} from t",
      "select {fn ucase(\"a, s)} from t",
      "select {fn ucase(`a, s)} from t",
      "select {fn ucase([a, s)} from t",
      "select {fn concat('}', s) from t",
      "select {fn concat('a\\'b', s)} from t",
   })
   void unterminatedUnitInEscapeFailsParse(String text) throws Exception {
      assertParseFails(text);
   }

   // a JDBC escape can't be a table: it was written back as one quoted table name, e.g.
   // from "{oj t left outer join u on t.id = u.id}", so the parse must fail and the original
   // SQL run. With a quoted brace it failed before, since the escape was cut short.
   @ParameterizedTest
   @ValueSource(strings = {
      "select * from {oj t left outer join u on t.s = '}'}",
      "select t.id from {oj t left outer join u on t.id = u.id}",
      "select * from a, {oj t left outer join u on t.id = u.id}",
      // a join operand, and a table of a subquery
      "select * from a inner join {oj t left outer join u on t.id = u.id} on a.id = t.id",
      "select id from a where id in (select id from {oj t left outer join u on t.id = u.id})",
   })
   void escapeAsTableFailsParse(String text) throws Exception {
      assertParseFails(text);
   }

   private static void assertParseFails(String text) throws Exception {
      for(String type : TYPES) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(ds);
         new SQLProcessor(sql).parse(text);
         String message = type + ": " + text;

         if(sql.getParseResult() == UniformSQL.PARSE_SUCCESS) {
            sql.clearSQLString();
            message += " -> " + normalize(sql.getSQLString());
         }

         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), message);
         assertEquals(text, sql.getSQLString(), message);

         UniformSQL fresh = new UniformSQL();
         fresh.setDataSource(ds);
         fresh.setSQLString(text, false);
         assertTrue(fresh.isLossy(), message);
         assertFalse(XUtil.isQueryMergeable(query(text, ds)), message);
      }
   }

   // a string alias holding a double quote keeps its name, although the SQL names the column
   // ALIAS_n. On Derby, the name StyleBI gives the result column is the alias.
   @Test
   void stringAliasWithDoubleQuoteKeepsItsName() throws Exception {
      String text = "select id as 'a\"b', s from t";

      for(String type : TYPES) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = parse(text, ds);
         String generated = regenerate(sql, text);
         String message = type + ": " + generated;

         assertEquals("a\"b", sql.getSelection().getAlias(0), message);
         assertEquals(1, sql.getTableCount(), message);
         assertRoundTrip(generated, ds, message);
      }

      UniformSQL sql = parse(text, dataSource("derby"));
      String generated = regenerate(sql, text);

      try(Connection conn = derby(); PreparedStatement stmt = conn.prepareStatement(generated)) {
         String[] names = SQLSelection.getColumnNames(
            sql.getSelection(), stmt.getMetaData(), "org.apache.derby.jdbc.EmbeddedDriver",
            conn, null);
         assertEquals(List.of("a\"b", "s"), List.of(names), generated);
      }
   }

   private static void assertEscapeKept(String text, String escape, boolean rows)
      throws Exception
   {
      for(String type : TYPES) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = parse(text, ds);
         String generated = regenerate(sql, text);
         String message = type + ": " + text + " -> " + generated;

         assertEquals(1, sql.getTableCount(), "FROM lost, " + message);
         assertTrue(generated.contains(escape), "escape not kept, " + message);
         assertEquals(text.contains(" where "), sql.getWhere() != null, "WHERE, " + message);
         assertEquals(1, count(generated, " from "), message);
         assertRoundTrip(generated, ds, message);
      }

      if(rows) {
         assertSameRows(text);
      }
   }

   /**
    * The original and the regenerated SQL return the same rows: on Derby the SQL the derby
    * helper writes, both as the query editor regenerates it and as the data cache normalizer
    * runs it for a plain query, and on HSQLDB the SQL the h2 helper writes.
    */
   private static void assertSameRows(String text) throws Exception {
      JDBCDataSource derbySource = dataSource("derby");
      String generated = regenerate(parse(text, derbySource), text);
      String normalized = normalized(text, derbySource);

      try(Connection conn = derby()) {
         List<String> expected = rows(conn, text);
         assertFalse(expected.isEmpty(), text);
         assertEquals(expected, rows(conn, generated), "derby: " + text + " -> " + generated);
         assertEquals(expected, rows(conn, normalized),
                      "derby, cache normalizer: " + text + " -> " + normalized);
      }

      String h2 = regenerate(parse(text, dataSource("h2")), text);

      try(Connection conn = hsqldb()) {
         assertEquals(rows(conn, text), rows(conn, h2), "hsqldb: " + text + " -> " + h2);
      }
   }

   // what JDBCQueryCacheNormalizer runs for a parsed, not lossy query: the sql string is
   // cleared and the SQL is regenerated with a sorted select list
   private static String normalized(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = parse(text, ds);
      JDBCQuery query = new JDBCQuery();
      query.setName("q77662");
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(query);
      assertTrue(normalizer.isClearedSqlString(), text);
      return normalize(sql.getSQLString());
   }

   private static List<String> rows(Connection conn, String query) {
      try {
         return SQLHelperNotEqualJoinTest.RowCompare.rows(conn, query);
      }
      catch(SQLException ex) {
         return fail("doesn't run: " + query + ": " + ex.getMessage());
      }
   }

   /**
    * The regenerated SQL re-parses to itself. A re-parse can move a constant select item
    * ahead of the columns, since the select list is sorted by its stored text
    * (JDBCQueryCacheNormalizer.generateSortedColumnMap), so then the select list is compared
    * as a multiset and the second generation must be the fixed point. The space after a comma
    * isn't compared, since oracle writes {d '..'} as To_Date('..', '..') and keeps the
    * re-parsed To_Date without the space.
    */
   private static void assertRoundTrip(String generated, JDBCDataSource ds, String message)
      throws Exception
   {
      String again = regenerate(parse(generated, ds), generated);
      generated = generated.replace(", ", ",");
      again = again.replace(", ", ",");

      if(!again.equals(generated)) {
         assertEquals(sorted(selectItems(generated)), sorted(selectItems(again)),
                      "round trip select list, " + message + " -> " + again);
         assertEquals(afterSelectList(generated), afterSelectList(again),
                      "round trip, " + message + " -> " + again);
         assertEquals(again, regenerate(parse(again, ds), again).replace(", ", ","),
                      "second round trip, " + message);
      }
   }

   // the items of the select list, split at commas outside quotes, braces and parentheses
   private static List<String> selectItems(String sql) {
      List<String> items = new ArrayList<>();
      int start = "select ".length();
      int end = selectListEnd(sql);
      int depth = 0;
      char quote = 0;

      for(int i = start; i < end; i++) {
         char c = sql.charAt(i);

         if(quote != 0) {
            quote = c == quote ? 0 : quote;
         }
         else if(c == '\'' || c == '"') {
            quote = c;
         }
         else if(c == '(' || c == '{') {
            depth++;
         }
         else if(c == ')' || c == '}') {
            depth--;
         }
         else if(c == ',' && depth == 0) {
            items.add(sql.substring(start, i).trim());
            start = i + 1;
         }
      }

      items.add(sql.substring(start, end).trim());
      return items;
   }

   private static String afterSelectList(String sql) {
      return sql.substring(selectListEnd(sql));
   }

   // the index of the first " from " outside quotes, braces and parentheses, or the end
   private static int selectListEnd(String sql) {
      int depth = 0;
      char quote = 0;

      for(int i = 0; i < sql.length(); i++) {
         char c = sql.charAt(i);

         if(quote != 0) {
            quote = c == quote ? 0 : quote;
         }
         else if(c == '\'' || c == '"') {
            quote = c;
         }
         else if(c == '(' || c == '{') {
            depth++;
         }
         else if(c == ')' || c == '}') {
            depth--;
         }
         else if(depth == 0 && sql.startsWith(" from ", i)) {
            return i;
         }
      }

      return sql.length();
   }

   private static List<String> sorted(List<String> items) {
      return items.stream().sorted().toList();
   }

   private static int count(String text, String part) {
      int n = 0;

      for(int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) {
         n++;
      }

      return n;
   }

   // the token type names the lexer gives the parser
   private static String tokens(String text) {
      StringBuilder names = new StringBuilder();

      try {
         SQLLexer lexer = new SQLLexer(new StringReader(text));

         for(Token token = lexer.nextToken(); ; token = lexer.nextToken()) {
            names.append(SQLParser._tokenNames[token.getType()].replace("\"", ""));

            if(token.getType() == Token.EOF_TYPE) {
               break;
            }

            names.append(' ');
         }
      }
      catch(Exception ex) {
         names.append(" ").append(ex);
      }

      return names.toString().replace("<EOF>", "EOF");
   }

   // the text of each JDBC escape token, then the lexer error if there is one
   private static List<String> escapeTokens(String text) {
      List<String> escapes = new ArrayList<>();

      try {
         SQLLexer lexer = new SQLLexer(new StringReader(text));

         for(Token token = lexer.nextToken(); token.getType() != Token.EOF_TYPE;
             token = lexer.nextToken())
         {
            if("SPIDENT_BRACKET".equals(SQLParser._tokenNames[token.getType()])) {
               escapes.add(token.getText());
            }
         }
      }
      catch(Exception ex) {
         escapes.add(ex.toString());
      }

      return escapes;
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
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      sql.clearSQLString();
      return normalize(sql.getSQLString());
   }

   private static JDBCQuery query(String text, JDBCDataSource ds) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      new SQLProcessor(sql).parse(text);
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      return query;
   }

   private static JDBCDataSource dataSource(String type) {
      if(type.startsWith("h2") || type.equals("derby") || type.equals("postgresql") ||
         type.equals("oracle"))
      {
         return SQLHelperNotEqualJoinTest.RowCompare.dataSource(type);
      }

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds_" + type);
      ds.setProductVersion("19.0");

      switch(type) {
      case "snowflake" -> {
         ds.setDriver("net.snowflake.client.jdbc.SnowflakeDriver");
         ds.setURL("jdbc:snowflake://x.snowflakecomputing.com");
      }
      case "exasol" -> {
         ds.setDriver("com.exasol.jdbc.EXADriver");
         ds.setURL("jdbc:exa:localhost:8563");
      }
      default -> throw new IllegalArgumentException(type);
      }

      assertEquals(type, SQLHelper.getSQLHelper(ds).getSQLHelperType(), "helper for " + type);
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

   private static Connection hsqldb() throws SQLException {
      return DriverManager.getConnection(HSQLDB_URL);
   }
}
