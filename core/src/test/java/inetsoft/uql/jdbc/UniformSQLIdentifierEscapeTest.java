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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.StringReader;
import java.sql.*;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77661, a quoted identifier containing an escaped delimiter ("a""b", [a]]b]), or a
 * subscript/map-key access (a[1], m['key']), was silently read as a bare expression followed
 * by an implicit alias, because the lexer had no escape mechanism for a doubled delimiter and
 * the grammar's as_clause accepts any quoted/bracketed identifier as an alias without a
 * preceding AS. A name with an embedded quote also came back malformed on regeneration, since
 * XUtil's identifier-quoting helpers wrapped a name without escaping an embedded quote char.
 *
 * Fix: SPIDENT and SPIDENT_SQUARE now support "" and ]] as escaped literal delimiters (like
 * the existing '' escape in STRING_LITERAL), so "a""b" and [a]]b] are read as one identifier.
 * XUtil.quoteName/quoteNameSegment/quoteAlias now escape an embedded quote char by doubling it
 * when wrapping a name, so the round trip is lossless. A bracket-quoted alias ([b]) is only
 * accepted without a preceding AS is now refused: this disambiguates a[1]/m['key'] (a genuine
 * subscript/map-key access, not an escaping problem) from an alias, since there's no dedicated
 * subscript grammar; "refuse the parse" is an accepted outcome for this bug per the report.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLIdentifierEscapeTest {
   private static final String DERBY_URL = "jdbc:derby:memory:bug77661;create=true";
   private static final String[] TYPES = { "postgresql", "h2", "oracle", "derby" };

   @BeforeAll
   static void createTable() throws Exception {
      try(Connection conn = derby(); Statement stmt = conn.createStatement()) {
         try {
            stmt.execute("drop table t");
         }
         catch(SQLException ignore) {
            // first run
         }

         stmt.execute("create table t (id int, \"a\"\"b\" int)");
         stmt.execute("insert into t values (1, 5), (2, 6)");
      }
   }

   @AfterAll
   static void dropDatabase() {
      try {
         derbyDriver().connect("jdbc:derby:memory:bug77661;drop=true", new Properties());
      }
      catch(Exception ignore) {
         // derby reports a dropped database with an exception
      }
   }

   // the cause: "" and ]] no longer end the token early, so the whole quoted/bracketed name
   // is one token and there's no trailing identifier left to misread as an alias
   @Test
   void escapedDelimiterIsOneToken() {
      assertEquals("select SPIDENT from IDENT EOF", tokens("select \"a\"\"b\" from t"));
      assertEquals("select SPIDENT_SQUARE from IDENT EOF", tokens("select [a]]b] from t"));
   }

   // Case 1: a double-quoted name with an embedded "" escape parses as one field, not an
   // expression aliased by a second identifier
   @Test
   void doubleQuoteEscapeRoundTrips() throws Exception {
      String text = "select \"a\"\"b\" from t";

      for(String type : TYPES) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = parse(text, ds);

         assertEquals(1, sql.getSelection().getColumnCount(), type);
         assertNull(sql.getSelection().getAlias(0), type + ": got an alias");
         assertEquals("a\"b", unwrapColumn(sql.getSelection().getColumn(0)), type);

         String generated = regenerate(sql, text);
         assertRoundTripsToSameColumn(generated, ds, type);
      }

      assertSameRows(text);
   }

   // Case 2: a bracket name with an embedded ]] escape parses as one field
   @Test
   void bracketEscapeRoundTrips() throws Exception {
      String text = "select [a]]b] from t";

      for(String type : TYPES) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = parse(text, ds);

         assertEquals(1, sql.getSelection().getColumnCount(), type);
         assertNull(sql.getSelection().getAlias(0), type + ": got an alias");
         assertEquals("a]b", unwrapColumn(sql.getSelection().getColumn(0)), type);

         String generated = regenerate(sql, text);
         assertRoundTripsToSameColumn(generated, ds, type);
      }
   }

   // Case 5: a bracket name holding a literal '"' must come back with the quote escaped,
   // not as a malformed, unparseable fragment
   @Test
   void embeddedQuoteIsEscapedOnRegeneration() throws Exception {
      String text = "select [a\"b] from t";
      JDBCDataSource ds = dataSource("postgresql");
      UniformSQL sql = parse(text, ds);

      assertEquals("a\"b", unwrapColumn(sql.getSelection().getColumn(0)));

      String generated = regenerate(sql, text);
      assertTrue(generated.contains("\"a\"\"b\""), generated);
      assertRoundTripsToSameColumn(generated, ds, "postgresql");
   }

   // Boundary case found in review: a bracket name whose literal content starts AND ends
   // with the dialect's quote char (e.g. ["ab"], content "ab", not an escaped "" pair) must
   // still be quoted and escaped on regeneration, not mistaken for an already-fully-wrapped
   // string and passed through unwrapped/unescaped (#77661 review round 1)
   @Test
   void boundaryQuoteCharAtBothEndsIsEscaped() throws Exception {
      String text = "select [\"ab\"] from t";
      JDBCDataSource ds = dataSource("postgresql");
      UniformSQL sql = parse(text, ds);

      assertEquals("\"ab\"", unwrapColumn(sql.getSelection().getColumn(0)));

      String generated = regenerate(sql, text);
      assertTrue(generated.contains("\"\"\"ab\"\"\""), generated);
      assertRoundTripsToSameColumn(generated, ds, "postgresql");

      // re-parsed, the regenerated form is a bare double-quoted name (not bracket-sourced),
      // which special_identifier stores raw rather than quoteDot-wrapped, so no unwrapColumn
      UniformSQL again = parse(generated, ds);
      assertEquals("\"ab\"", again.getSelection().getColumn(0), generated);
   }

   // XUtil.quoteName/quoteNameSegment/quoteAlias intentionally still pass a name that starts
   // and ends with the quote char through unwrapped: that shape is also how a caller
   // assembling a qualified name re-quotes an already-quoted segment (e.g. building
   // "a"."id"), and that legitimate, far more common use depends on this exact shortcut (see
   // isSpecialName()'s comment) - removing it regressed 32 existing tests. The one call site
   // proven to always receive raw, never-pre-wrapped content for this shape (quoteDot, in
   // SQLParser.g, reached from a bracket-sourced identifier) is fixed directly instead; see
   // boundaryQuoteCharAtBothEndsIsEscaped above for its round-trip coverage.

   // the stored column path for a special/bracket-sourced name may already be wrapped in the
   // dialect's quote char with its '""' escape applied (quoteDot, at parse time); unwrap it to
   // compare the underlying name, same as the raw (unwrapped) form a plain SPIDENT stores
   private static String unwrapColumn(String col) {
      if(col.length() > 1 && col.charAt(0) == '"' && col.charAt(col.length() - 1) == '"') {
         return col.substring(1, col.length() - 1).replace("\"\"", "\"");
      }

      return col;
   }

   // Cases 3/4: a[1] and m['key'] are a genuine ambiguity (subscript/map-key access vs. an
   // implicit alias), not an escaping problem - the grammar now requires an explicit AS to
   // accept a bracket name as an alias, so these fail the parse instead of silently mis-aliasing
   @ParameterizedTest
   @ValueSource(strings = { "select a[1] from t", "select m['key'] from t" })
   void subscriptWithoutAsFailsParse(String text) {
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
      }
   }

   // a bracket name is still a valid alias when AS is explicit (e.g. SQL Server style)
   @Test
   void bracketAliasWithExplicitAsStillParses() throws Exception {
      String text = "select a as [b] from t";

      for(String type : TYPES) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = parse(text, ds);

         assertEquals("b", sql.getSelection().getAlias(0), type);
         assertRoundTrip(regenerate(sql, text), ds, type);
      }
   }

   // an ordinary implicit alias (no AS, a plain or double-quoted name) is unaffected
   @Test
   void ordinaryImplicitAliasStillParses() throws Exception {
      for(String text : new String[] { "select a b from t", "select a \"b\" from t" }) {
         for(String type : TYPES) {
            JDBCDataSource ds = dataSource(type);
            UniformSQL sql = parse(text, ds);

            assertEquals("b", sql.getSelection().getAlias(0), type + ": " + text);
            assertRoundTrip(regenerate(sql, text), ds, type + ": " + text);
         }
      }
   }

   // the generation-side fix in isolation: wrapping a name/alias holding the dialect's quote
   // char must escape it, not just drop it in unescaped
   @Test
   void xutilEscapesEmbeddedQuoteChar() {
      SQLHelper helper = SQLHelper.getSQLHelper(dataSource("postgresql"));

      assertEquals("\"a\"\"b\"", XUtil.quoteName("a\"b", helper));
      assertEquals("\"a\"\"b\"", XUtil.quoteNameSegment("a\"b", helper));
      assertEquals("\"a\"\"b\"", XUtil.quoteAlias("a\"b", helper));

      // unaffected: a name with no special character round trips unquoted (postgresql quotes
      // every name regardless of content, since it's case-sensitive, so use h2 here instead)
      SQLHelper h2 = SQLHelper.getSQLHelper(dataSource("h2"));
      assertEquals("ab", XUtil.quoteName("ab", h2));
   }

   /**
    * The original and the regenerated SQL return the same rows on Derby, the way the query
    * editor regenerates SQL.
    */
   private static void assertSameRows(String text) throws Exception {
      JDBCDataSource derbySource = dataSource("derby");
      String generated = regenerate(parse(text, derbySource), text);

      try(Connection conn = derby()) {
         List<String> expected = rows(conn, text);
         assertFalse(expected.isEmpty(), text);
         assertEquals(expected, rows(conn, generated), text + " -> " + generated);
      }
   }

   // the regenerated SQL, reparsed, still refers to a single column with no implicit alias
   // (whatever quoting style the dialect writes it with)
   private static void assertRoundTripsToSameColumn(String generated, JDBCDataSource ds,
                                                     String message)
   {
      UniformSQL again = parse(generated, ds);
      assertEquals(1, again.getSelection().getColumnCount(), message + " -> " + generated);
      assertNull(again.getSelection().getAlias(0),
                 message + " -> " + generated + ": got an alias");
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
    * The regenerated SQL re-parses without error and the select list stays the same size.
    */
   private static void assertRoundTrip(String generated, JDBCDataSource ds, String message)
      throws Exception
   {
      UniformSQL again = parse(generated, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, again.getParseResult(), message + " -> " + generated);
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
      sql.clearSQLString();
      return normalize(sql.getSQLString());
   }

   private static JDBCDataSource dataSource(String type) {
      return SQLHelperNotEqualJoinTest.RowCompare.dataSource(type);
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
