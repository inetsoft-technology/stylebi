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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77680. {@code SQLLexer} runs with {@code filter = true}, so a character matching no token
 * rule is silently dropped instead of failing the parse (the same mechanism as #77640/#77659).
 * {@code SPIDENT_BRACKET} (the {@code {fn ...}} JDBC escape token) is a flat scan to the first
 * bare {@code '}'}, with no awareness of a bracket-quoted segment or a comment inside the escape
 * body, so a {@code --} comment inside an escape could swallow the real closing brace and leave
 * it to be silently dropped, and a stray {@code '}'} or {@code ']'} after a complete escape was
 * also silently dropped. Both the stray delimiter and the comment-aware escape body must now be
 * represented correctly: a stray delimiter fails the parse, and a comment inside an escape no
 * longer hides the real closing brace.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLJdbcEscapeDelimiterTest {
   private static final String[] TYPES = { "postgresql", "h2", "oracle" };

   // case 1: a stray '}' or ']' after a complete escape, or with no escape at all, must fail the
   // parse instead of being silently dropped
   @ParameterizedTest
   @ValueSource(strings = {
      "select {fn f(a)}} from t",
      "select {fn f(a)}] from t",
      "select a } from t",
      "select a ] from t",
   })
   void strayDelimiterFailsParse(String text) {
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

      assertFalse(XUtil.isSQLExpressionValid("{fn f(a)}}"));
      assertFalse(XUtil.isSQLExpressionValid("{fn f(a)}]"));
   }

   // case 2: a bracket-quoted segment inside the escape body is now recognized as a nested unit,
   // but the leftover 'b])' after it still can't be re-tokenized into a valid statement, so the
   // parse must still fail, safely, as it did before this fix
   @Test
   void bracketSegmentInsideEscapeStillFailsParse() {
      String text = "select {fn ucase([a]]}b])} from t";
      // the SPIDENT_BRACKET token still stops at the first bare '}', right after the bracket
      // segment [a] and the stray ']' that follows it; the leftover 'b])}' no longer loses its
      // ']' and '}' to filter mode, they are now RBRACKET and RBRACE tokens the parser rejects
      assertEquals("select SPIDENT_BRACKET IDENT RBRACKET CLOSE_PAREN RBRACE from IDENT EOF",
                   tokens(text));

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

      // before this fix the leftover 'b])}' was accepted as a trailing no-op by the expression
      // checker too (the ']' and '}' were silently dropped there as well); it now correctly
      // rejects the malformed expression instead of silently accepting it
      assertFalse(XUtil.isSQLExpressionValid("{fn ucase([a]]}b])}"));
   }

   // case 3: a '--' comment inside the escape body no longer hides the real closing brace. The
   // whole escape, including the comment and the newline, is now one token, and the statement
   // parses and round trips correctly instead of silently dropping the real closing brace
   @Test
   void lineCommentInsideEscapeIsKeptInOneToken() {
      String text = "select {fn f(a) -- }\n} from t";
      assertEquals("select SPIDENT_BRACKET from IDENT EOF", tokens(text));

      for(String type : TYPES) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = parse(text, ds);
         String generated = regenerate(sql, text);
         String message = type + ": " + text + " -> " + generated;

         assertEquals(1, sql.getTableCount(), message);
         assertEquals(1, sql.getSelection().getColumnCount(), message);
         assertTrue(generated.contains("{fn f(a) -- }"), "escape lost, " + message);
      }

      assertTrue(XUtil.isSQLExpressionValid("{fn f(a) -- }\n}"));
   }

   // '//' and '/*..*/' comments inside an escape body are recognized the same way as '--'
   @ParameterizedTest
   @ValueSource(strings = {
      "select {fn f(a) // }\n} from t",
      "select {fn f(a) /* } */} from t",
      "select {fn f(a) /* line1\nline2 } */} from t",
   })
   void otherCommentStylesInsideEscapeAreKeptInOneToken(String text) {
      assertEquals("select SPIDENT_BRACKET from IDENT EOF", tokens(text));

      for(String type : TYPES) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = parse(text, ds);
         String generated = regenerate(sql, text);
         String message = type + ": " + text + " -> " + generated;

         assertEquals(1, sql.getTableCount(), message);
         assertEquals(1, sql.getSelection().getColumnCount(), message);
      }
   }

   // a comment-opener substring inside an actual quoted literal, inside the escape body, must
   // not be treated as a comment: the quoted literal is one atomic unit, matched before any
   // comment-opener check, so '--'/'//'/'/*' inside it doesn't swallow past the real closing '}'
   @ParameterizedTest
   @ValueSource(strings = {
      "select {fn concat('a--b', s)} from t",
      "select {fn concat('a//b', s)} from t",
      "select {fn concat('a/*b', s)} from t",
      "select {fn concat(\"a--b\", s)} from t",
      "select {fn concat(`a--b`, s)} from t",
   })
   void commentOpenerInsideQuotedLiteralIsNotTreatedAsComment(String text) {
      assertEquals("select SPIDENT_BRACKET from IDENT EOF", tokens(text));

      for(String type : TYPES) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = parse(text, ds);
         String generated = regenerate(sql, text);
         String message = type + ": " + text + " -> " + generated;

         assertEquals(1, sql.getTableCount(), message);
         assertEquals(1, sql.getSelection().getColumnCount(), message);
      }
   }

   // pre-existing escape forms must still parse exactly as before: nested escapes, a bracket
   // identifier used as a plain column, and an escape with a bracket-quoted segment inside it
   @Test
   void existingEscapeFormsStillParse() throws Exception {
      for(String type : TYPES) {
         JDBCDataSource ds = dataSource(type);

         UniformSQL nested = parse("select {fn concat({fn ucase(s)}, s)} from t", ds);
         assertEquals(1, nested.getTableCount(), type);
         assertFalse(nested.isLossy(), type);

         UniformSQL bracketCol = parse("select [id] from t where [k] = 1", ds);
         assertEquals(1, bracketCol.getTableCount(), type);
         assertFalse(bracketCol.isLossy(), type);

         UniformSQL bracketInEscape = parse("select {fn ucase([a])} from t", ds);
         assertEquals(1, bracketInEscape.getTableCount(), type);
         assertFalse(bracketInEscape.isLossy(), type);
      }
   }

   // the token type names the lexer gives the parser
   private static String tokens(String text) {
      StringBuilder names = new StringBuilder();

      try {
         SQLLexer lexer = new SQLLexer(new StringReader(text));

         for(antlr.Token token = lexer.nextToken(); ; token = lexer.nextToken()) {
            names.append(SQLParser._tokenNames[token.getType()].replace("\"", ""));

            if(token.getType() == antlr.Token.EOF_TYPE) {
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
      assertFalse(sql.isLossy(), text);
      sql.clearSQLString();
      return normalize(sql.getSQLString());
   }

   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static JDBCDataSource dataSource(String type) {
      return SQLHelperNotEqualJoinTest.RowCompare.dataSource(type);
   }
}
