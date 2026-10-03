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
 * Bug #77640, a string literal containing a double quote, e.g. 'x"A"y', parsed as a non-lossy
 * success but regenerated without the literal and everything after it (FROM, WHERE, ORDER BY).
 * The lexer's literal rule excluded '"', and the lexer's filter mode silently dropped the failed
 * literal and the unterminated quoted name that followed it, so the parser saw a shorter,
 * complete statement. The same filter mode silently dropped any other unterminated quoted token
 * at the end of the input (a backslash-escaped quote, a stray backtick or quote).
 *
 * A literal must keep '"' and the '' escape, and an unterminated quoted token must fail the
 * parse so the original SQL runs.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLLiteralQuoteTest {
   private static final String DERBY_URL = "jdbc:derby:memory:bug77640;create=true";
   private static final String HSQLDB_URL = "jdbc:hsqldb:mem:bug77640";
   // the PostgreSQL family quotes names in-band, h2 doesn't, oracle is the non-ANSI dialect
   private static final String[] TYPES =
      { "postgresql", "snowflake", "exasol", "h2", "h2-ansi", "oracle", "derby" };

   // a literal holding a double quote, followed by more of the statement. All parsed as a
   // non-lossy success and regenerated without the rest of the statement.
   static Stream<Arguments> truncatedBefore() {
      return Stream.of(
         Arguments.of("select id as A, 'x\"A\"y' as s from t order by a desc", "'x\"A\"y'"),
         Arguments.of("select id as A, 'x\"B\"y' as s from t order by a desc", "'x\"B\"y'"),
         Arguments.of("select id as A, 'x\"A\"y' as s from t", "'x\"A\"y'"),
         Arguments.of("select id, 'x\"A\"y' as s from t", "'x\"A\"y'"),
         Arguments.of("select id, 'x\"A\"y' from t", "'x\"A\"y'"),
         Arguments.of("select id as A from t where s = 'x\"A\"y' order by a desc", "'x\"A\"y'"),
         Arguments.of("select id from t where s = 'x\"B\"y' and k = 2", "'x\"B\"y'"),
         Arguments.of("select id from t where s = 'it\"s' order by id desc", "'it\"s'"));
   }

   // a literal holding a double quote or a '' escape that failed the parse
   static Stream<Arguments> refusedBefore() {
      return Stream.of(
         Arguments.of("select id from t where 'q' <> '\"A\"'", "'\"A\"'"),
         Arguments.of("select id from t where s = '\"A\"'", "'\"A\"'"),
         Arguments.of("select id from t where s = 'it\"s'", "'it\"s'"),
         Arguments.of("select id, 'it''s' as s from t", "'it''s'"),
         Arguments.of("select id from t where s = 'it''s'", "'it''s'"));
   }

   // a literal that starts like a date or a time and goes on after a '' escape. It lexed as a
   // DATE or TIME token followed by a string alias.
   static Stream<Arguments> dateTimeWithEscape() {
      return Stream.of(
         Arguments.of("select '2020-01-01''s data' from t", "'2020-01-01''s data'"),
         Arguments.of("select '12:00:00''x' from t", "'12:00:00''x'"));
   }

   // the date format literals of the oracle and postgresql fullyear/fullday functions in
   // sqlhelper.xml, i.e. SQL that StyleBI writes itself
   static Stream<Arguments> styleBILiterals() {
      return Stream.of(
         Arguments.of("select to_char(d,'\"Date.\"YYYY') from t", "'\"Date.\"YYYY'"),
         Arguments.of("select to_char(d,'\"Date.\"YYYY.\"Qtr\"Q.MM.DD') as fd from t",
                      "'\"Date.\"YYYY.\"Qtr\"Q.MM.DD'"));
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

            stmt.execute("create table t (id int, s varchar(20), k int, d date)");
            stmt.execute("insert into t values " +
               "(1, 'x\"A\"y', 1, cast('2020-01-01' as date)), (2, 'it\"s', 2, null), " +
               "(3, 'it''s', null, cast('2021-06-30' as date)), (4, '\"A\"', 1, null), " +
               "(5, null, 3, cast('2020-01-01' as date)), (6, '', 1, null), " +
               "(7, 'x\"B\"y', 2, cast('2020-01-01' as date))");
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
         derbyDriver().connect("jdbc:derby:memory:bug77640;drop=true", new Properties());
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   // the cause: the literal must reach the parser as one token, and the rest of the statement
   // must not be dropped by the lexer
   @Test
   void literalWithDoubleQuoteIsOneToken() {
      assertEquals("select IDENT as IDENT COMMA STRING_LITERAL as IDENT from IDENT order by " +
                      "IDENT desc EOF",
                   tokens("select id as A, 'x\"A\"y' as s from t order by a desc"));
      assertEquals("select IDENT from IDENT where STRING_LITERAL NEQ STRING_LITERAL EOF",
                   tokens("select id from t where 'q' <> '\"A\"'"));
      assertEquals("select IDENT COMMA STRING_LITERAL as IDENT from IDENT EOF",
                   tokens("select id, 'it''s' as s from t"));
   }

   // a date or time literal is still its own token, unless a '' escape goes on after it
   @Test
   void dateAndTimeLiteralTokens() {
      assertEquals("select DATE as IDENT from IDENT EOF", tokens("select '2020-01-01' as d from t"));
      assertEquals("select TIME as IDENT from IDENT EOF", tokens("select '12:00:00' as d from t"));
      assertEquals("select IDENT from IDENT where IDENT EQ DATE EOF",
                   tokens("select id from t where d = '2020-01-01'"));
      assertEquals("select STRING_LITERAL from IDENT EOF",
                   tokens("select '2020-01-01''s data' from t"));
      assertEquals("select STRING_LITERAL from IDENT EOF", tokens("select '12:00:00''x' from t"));
   }

   @ParameterizedTest
   @MethodSource({ "truncatedBefore", "refusedBefore", "dateTimeWithEscape" })
   void literalAndRestOfStatementAreKept(String text, String literal) throws Exception {
      for(String type : TYPES) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = parse(text, ds);
         String generated = regenerate(sql, text);
         String message = type + ": " + text + " -> " + generated;

         assertEquals(1, sql.getTableCount(), "FROM lost, " + message);
         assertTrue(generated.contains(literal), "literal lost, " + message);
         assertEquals(text.contains(" where "), sql.getWhere() != null, "WHERE, " + message);
         Object[] orderBy = sql.getOrderByFields();
         assertEquals(text.contains(" order by ") ? 1 : 0, orderBy == null ? 0 : orderBy.length,
                      "ORDER BY, " + message);
         assertEquals(1, count(generated, " from "), message);
         assertRoundTrip(generated, ds, message);
      }

      assertSameRows(text);
   }

   // a literal StyleBI writes itself must parse, keep the literal and round trip (TO_CHAR
   // doesn't run on Derby or HSQLDB, so rows aren't compared)
   @ParameterizedTest
   @MethodSource("styleBILiterals")
   void styleBIDateFormatLiteralRoundTrips(String text, String literal) throws Exception {
      for(String type : TYPES) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = parse(text, ds);
         String generated = regenerate(sql, text);
         String message = type + ": " + text + " -> " + generated;

         assertEquals(1, sql.getTableCount(), "FROM lost, " + message);
         assertEquals(1, sql.getSelection().getColumnCount(), message);
         assertTrue(generated.contains(literal), "literal lost, " + message);
         assertRoundTrip(generated, ds, message);
      }
   }

   // a string alias with the '' escape names the column with one quote
   @Test
   void stringAliasIsUnescaped() throws Exception {
      String text = "select id as 'it''s' from t";

      for(String type : TYPES) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = parse(text, ds);
         String generated = regenerate(sql, text);
         String message = type + ": " + generated;

         assertEquals("it's", sql.getSelection().getAlias(0), message);
         assertEquals(1, sql.getTableCount(), message);
         assertRoundTrip(generated, ds, message);
      }
   }

   // an unterminated quoted token or block comment at the end of the input can't be
   // represented, so the parse fails and the original SQL runs. A backslash-escaped quote
   // (MySQL, Hive, Spark, BigQuery, ClickHouse) leaves a backslash outside any token, or a
   // literal that doesn't close: either way the parse must fail, whatever the number of
   // escaped literals. The two after the brackets already failed, and so did the escaped
   // literals before #77640 admitted the '' escape.
   @ParameterizedTest
   @ValueSource(strings = {
      "select id, 'a\\'b' as s from t order by id",
      "select '\\'', s, '\\'' from t",
      "select '\\'' as q, s from t where s = '\\''",
      "select id, '\\'' from t where k = 1 order by '\\''",
      "select s, '\\'' as a, k from t where k = '\\''",
      "select 'a\\'' as x, k from t where s = 'b\\''",
      "select 'O\\'Brien', s, 'O\\'Brien' from t",
      "select 'a\\'', s, 'b\\'', k, 'c\\'' from t",
      "select '\\'' as a, '\\'' as b, '\\'' as c, '\\'' as d from t",
      "select id from t where k = 1 /* unterminated",
      "select id from t where k = 1 ` c = 2",
      "select id from t where k = 1 \"c = 2",
      "select id from t where k = 1 'c = 2",
      "select id from t where k = 1 [c = 2",
      "select id from t where k = 1 {c = 2",
      "select id, \"unterminated from t",
      "select id from t where k = 1 $(c = 2",
   })
   void unterminatedQuoteFailsParse(String text) throws Exception {
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

   // quoted names, JDBC escapes, variables, comments and literals holding comment markers
   // already parse and must keep doing so. The fragment is from the end of the h2 SQL, so a
   // dropped tail shows.
   @ParameterizedTest
   @MethodSource("validDelimitedTokens")
   void validDelimitedTokensStillParse(String text, String fragment, boolean rows)
      throws Exception
   {
      for(String type : TYPES) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = parse(text, ds);
         String generated = regenerate(sql, text);
         String message = type + ": " + text + " -> " + generated;

         assertEquals(1, sql.getTableCount(), message);
         assertEquals(text.contains(" where "), sql.getWhere() != null, message);

         if("h2".equals(type)) {
            assertTrue(generated.endsWith(fragment), message);
         }

         assertRoundTrip(generated, ds, message);
      }

      if(rows) {
         assertSameRows(text);
      }
   }

   static Stream<Arguments> validDelimitedTokens() {
      return Stream.of(
         Arguments.of("select [id] from t where [k] = 1", "from t where k = 1", false),
         Arguments.of("select {fn ucase(s)} from t", "select {fn ucase(s)} from t", true),
         Arguments.of("select id from t where d = {d '2020-01-01'}",
                      "where d = {d '2020-01-01'}", true),
         Arguments.of("select id from t where k = $(var)", "where k = $(var)", false),
         Arguments.of("select $(var) from t", "select $(var) from t", false),
         Arguments.of("select `id` from `t`", "select \"id\" from \"t\"", false),
         Arguments.of("select id from t /* c */ where k = 1", "from t where k = 1", true),
         Arguments.of("select id from t where k = 1 -- c", "from t where k = 1", false),
         Arguments.of("select id from t where k = 1 -- c\n", "from t where k = 1", true),
         Arguments.of("select id, 'a--b' as s from t", "'a--b' as s, id from t", true),
         Arguments.of("select id, 'a/*b' as s from t", "'a/*b' as s, id from t", true),
         Arguments.of("select id, '' as s from t", "'' as s, id from t", true),
         Arguments.of("select id from t where s = ''", "where s = ''", true),
         Arguments.of("select id as 'my alias' from t", "select id as \"my alias\" from t", false),
         Arguments.of("select id from t where d > current_date - interval '1' day",
                      "interval '1' day", false),
         // more JDBC escapes, delimiters inside literals, quotes inside comments, '' escapes
         Arguments.of("select {ts '2020-01-01 00:00:00'} as x from t",
                      "{ts '2020-01-01 00:00:00'} as x from t", false),
         Arguments.of("select {fn timestampadd(SQL_TSI_DAY, 1, d)} as x from t",
                      "{fn timestampadd(SQL_TSI_DAY, 1, d)} as x from t", false),
         Arguments.of("select id from t where s like '[a-z]%'", "'[a-z]%'", false),
         Arguments.of("select id, '[' as w, '{' as x, '`' as y, '$(' as z from t", "from t", true),
         Arguments.of("select id from t where k = 1 -- it's \"x", "from t where k = 1", false),
         Arguments.of("select id from t /* it's \"x */ where k = 1", "from t where k = 1", true),
         Arguments.of("select id from t where s = ''''", "where s = ''''", false),
         Arguments.of("select id, 'a''' as x from t", "'a''' as x, id from t", true),
         // a nested JDBC escape is one token and is written back verbatim
         Arguments.of("select {fn concat({fn ucase(s)}, s)} from t",
                      "select {fn concat({fn ucase(s)}, s)} from t", true),
         // date and time literals, a hint (dropped as any comment is), and backslashes inside
         // literals, where standard SQL keeps them as plain characters
         Arguments.of("select '2020-01-01' as x from t", "select '2020-01-01' as x from t", true),
         Arguments.of("select '12:00:00' as x from t", "select '12:00:00' as x from t", true),
         Arguments.of("select id from t where d = '2020-01-01'", "'2020-01-01'", true),
         Arguments.of("select /*+ index(t) */ id from t", "select id from t", true),
         Arguments.of("select id from t where s like 'a\\_b' escape '\\'", "'\\'", false),
         Arguments.of("select 'C:\\dir\\' as p, 'x' as q from t", "from t", true),
         Arguments.of("select id from t where s = 'a\\\\b'", "'a\\\\b'", false));
   }

   // a bracket identifier holds the CJK characters from U+80FE up, which ended the token
   // before, and a backslash, as in a SQL Server domain user name
   @ParameterizedTest
   @ValueSource(strings = { "销售", "一二", "a", "a\\b", "DOMAIN\\user" })
   void bracketIdentifierIsOneToken(String name) throws Exception {
      String text = "select [" + name + "] from t where [" + name + "] = 1";
      assertEquals("select SPIDENT_SQUARE from IDENT where SPIDENT_SQUARE EQ UNSIGNED_NUM_LIT EOF",
                   tokens(text));

      for(String type : TYPES) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = parse(text, ds);
         String generated = regenerate(sql, text);
         String message = type + ": " + text + " -> " + generated;

         assertEquals(1, sql.getTableCount(), message);
         // oracle writes an unquoted select column in upper case
         assertTrue(name.equalsIgnoreCase(sql.getSelection().getColumn(0).replace("\"", "")),
                    message);
         assertEquals(2, count(generated.toLowerCase(), name.toLowerCase()), message);
         assertRoundTrip(generated, ds, message);
      }
   }

   // a [ inside a bracket identifier ends it, so a nested subscript can't be read as one
   // bracket name. The parse fails instead of turning part of the text into an alias.
   @ParameterizedTest
   @ValueSource(strings = { "select arr[idx[1]] from t", "select [a[b] from t" })
   void bracketInBracketIdentifierFailsParse(String text) {
      for(String type : TYPES) {
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(dataSource(type));
         new SQLProcessor(sql).parse(text);
         String message = type + ": " + text;

         if(sql.getParseResult() == UniformSQL.PARSE_SUCCESS) {
            sql.clearSQLString();
            message += " -> " + normalize(sql.getSQLString());
         }

         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), message);
         assertEquals(text, sql.getSQLString(), message);
      }
   }

   // an unbalanced nested JDBC escape and a variable name with a dot can't be lexed. Both
   // failed the parse before #77640 as well, and must keep failing it rather than drop part
   // of the text.
   @ParameterizedTest
   @ValueSource(strings = {
      "select {fn concat({fn ucase(s), s)} from t",
      "select id from t where k = $(a.b)",
      "select $(a.b) from t",
   })
   void malformedEscapeFailsParse(String text) {
      for(String type : TYPES) {
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(dataSource(type));
         new SQLProcessor(sql).parse(text);

         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), type + ": " + text);
         assertEquals(text, sql.getSQLString(), type);
      }
   }

   // the SQL expression checks (calc fields, the expression dialog, the logical model and the
   // query manager) accept a nested JDBC escape as before, and reject one that isn't balanced.
   // A variable name with a dot is rejected; before #77640 it passed as mangled tokens.
   @Test
   void expressionCheckOfJdbcEscapes() {
      assertTrue(XUtil.isSQLExpressionValid("{fn concat({fn ucase(a)}, b)}"));
      assertTrue(XUtil.isSQLExpressionValid("{fn ucase(a)}"));
      assertFalse(XUtil.isSQLExpressionValid("{fn concat({fn ucase(a), b)}"));
      assertFalse(XUtil.isSQLExpressionValid("$(a.b)"));
   }

   // a bracket name with a backslash is a valid expression, as it was before #77640
   @Test
   void expressionCheckOfBracketIdentifiers() {
      assertTrue(XUtil.isSQLExpressionValid("[a\\b]"));
      assertTrue(XUtil.isSQLExpressionValid("[DOMAIN\\user] + 1"));
      assertTrue(XUtil.isSQLExpressionValid("[a]"));
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
      query.setName("q77640");
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

   // the items of the select list, split at commas outside quotes and parentheses
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
         else if(c == '(') {
            depth++;
         }
         else if(c == ')') {
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

   // the index of the first " from " outside quotes and parentheses, or the end
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
         else if(c == '(') {
            depth++;
         }
         else if(c == ')') {
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
