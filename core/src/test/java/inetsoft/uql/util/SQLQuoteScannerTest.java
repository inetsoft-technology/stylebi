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
package inetsoft.uql.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77696, the quote scan had no rules for an informix {...} comment, a backslash in a
 * bigquery or clickhouse `name`, and a sql server [name] holding a [ or a line break, so a
 * quote in them opened a literal that hid the later sql. Bug #77695, the comments every
 * database reads are found by findComments.
 */
@Tag("core")
class SQLQuoteScannerTest {
   // an informix {...} comment is not sql, a quote in it doesn't open a literal
   @Test
   void braceCommentDoesNotOpenLiteral() {
      String sql = "select a { it's } from t where b = 'x'";

      assertNotQuoted(sql, " from t where b = ");
      assertQuoted(Before.findQuoted(sql), sql, " from t where b = ");
   }

   // a jdbc escape is not an informix comment, a } in its literal doesn't end it
   @Test
   void jdbcEscapeIsNotBraceComment() {
      for(String sql : new String[] {
         "select {fn locate('}', a)}, 'x' from t",
         "select { FN locate('}', a)}, 'x' from t",
         "select * from {oj t left outer join u on t.a = '}'}, 'x'",
         "select {d '}'}, 'x' from t" })
      {
         assertQuoted(SQLQuoteScanner.findQuoted(sql), sql, "'x'");
         assertArrayEquals(Before.findQuoted(sql), SQLQuoteScanner.findQuoted(sql), sql);
      }
   }

   // a backslash escapes a backtick in a bigquery or clickhouse `name`
   @Test
   void backslashInBacktickName() {
      String sql = "select `a\\`b`, c from t where `d` = 1";

      assertNotQuoted(sql, ", c from t where ");
      assertQuoted(Before.findQuoted(sql), sql, ", c from t where ");
   }

   // a sql server [name] may hold a [ or a line break, only ]] escapes in it
   @Test
   void bracketNameHoldsBracketAndLineBreak() {
      for(String sql : new String[] {
         "select [it's [x]] y], a from t where b = 'z'",
         "select [it's\nname], a from t where b = 'z'" })
      {
         assertNotQuoted(sql, ", a from t where b = ");
         assertQuoted(Before.findQuoted(sql), sql, ", a from t where b = ");
      }
   }

   // a bigquery '''...''' string is not read, so the ansi idioms with doubled quotes are
   // quoted as before
   @Test
   void tripleQuotesAreReadAsBefore() {
      for(String sql : new String[] {
         "select replace(q, '''', '') as q, '--' as n from T",
         "select '''' || x || '''' as q, '--' as n from T",
         "select '''x''' as a, 'b' from T",
         "select '''''' as a, '--' as b from T",
         "select '''it's''' as a, '--' as b from T",
         "select '''' || a || ''',''' || b || '''' as line,\n 'header\n-- sep' as h from T",
         "select concat('''', a, ''', ''', b, '''') as csv, 'x\n-- y' as z from T",
         "select '''a' as x, '''b' as y, 'Total\n-- end' as s from T" })
      {
         assertArrayEquals(Before.findQuoted(sql), SQLQuoteScanner.findQuoted(sql), sql);
      }

      String sql = "select '''' || x || '''' as q, '--' as n from T";
      assertQuoted(SQLQuoteScanner.findQuoted(sql), sql, "'--'");
   }

   // a comment is found only where every database reads one
   @Test
   void commentsOfEveryDatabase() {
      String sql = "select a -- x\nfrom t /* y */ where b = 1";
      int[] comment = SQLQuoteScanner.findComments(sql, i -> false);
      int dash = sql.indexOf("--");
      int slash = sql.indexOf("/*");

      assertEquals(dash, comment[dash]);
      assertEquals(dash, comment[sql.indexOf('x')]);
      assertEquals(-1, comment[sql.indexOf('\n')]);
      assertEquals(slash, comment[sql.indexOf('y')]);
      assertEquals(slash, comment[sql.indexOf("*/") + 1]);
      assertEquals(-1, comment[sql.indexOf("where")]);

      // in a literal, after a postgresql #>> (a mysql # comment) or a mysql \'
      for(String text : new String[] {
         "select d #>> '{a}' as v, '--' as s from t",
         "select 'O\\'Brien -- x' from t",
         "select 'O\\'Brien /* x' from t" })
      {
         int[] c = SQLQuoteScanner.findComments(text, i -> false);
         int start = Math.max(text.indexOf("--"), text.indexOf("/*"));
         assertEquals(-1, c[start], text);
         assertEquals(-1, c[start + 1], text);
      }

      // -- without a space is not a comment in mysql (5--1)
      String text = "select 5 --x\nfrom t";
      assertEquals(-1, SQLQuoteScanner.findComments(text, i -> false)[text.indexOf("--")]);

      // a nested comment ends at its inner */ in most databases
      text = "select /* a /* b */ c */ from t";
      int[] c = SQLQuoteScanner.findComments(text, i -> false);
      assertEquals(7, c[text.indexOf('b')]);
      assertEquals(-1, c[text.indexOf('c')]);
   }

   // a slash-star the caller marks as a tag is sql, not a comment, unless it is in a comment
   @Test
   void tagIsNotComment() {
      String sql = "select /*<where>*/a/*</where>*/ from t";
      int open = sql.indexOf("/*<");
      int[] comment = SQLQuoteScanner.findComments(sql, i -> i == open);

      assertEquals(-1, comment[open]);
      assertEquals(-1, comment[sql.indexOf('a', open)]);
      assertEquals(-1, comment[sql.indexOf("from")]);

      sql = "select /* a /*<where>*/ x */ from t";
      int open2 = sql.indexOf("/*<");
      comment = SQLQuoteScanner.findComments(sql, i -> i == open2);

      assertEquals(7, comment[open2]);
      assertEquals(7, comment[open2 + 2]);
   }

   // the new rules only quote more text in a [name], and only quote less text in texts with
   // a {, a backtick or a [
   @Test
   void quotedTextChangesOnlyWithNewForms() {
      Random random = new Random(77696);
      int grow = 0;
      int shrink = 0;
      String sample = null;

      for(int k = 0; k < 100_000; k++) {
         StringBuilder sb = new StringBuilder();

         for(int i = 2 + random.nextInt(12); i > 0; i--) {
            sb.append(TOKENS[random.nextInt(TOKENS.length)]);
         }

         String text = sb.toString();
         boolean[] before = Before.findQuoted(text);
         boolean[] after = SQLQuoteScanner.findQuoted(text);
         boolean bracket = text.indexOf('[') >= 0;
         boolean other = text.indexOf('{') >= 0 || text.indexOf('`') >= 0;

         for(int i = 0; i < text.length(); i++) {
            if(after[i] && !before[i] && !bracket) {
               grow++;
               sample = text;
               break;
            }

            if(before[i] && !after[i] && !bracket && !other) {
               shrink++;
               sample = text;
               break;
            }
         }
      }

      assertEquals(0, grow, sample);
      assertEquals(0, shrink, sample);
   }

   // the quoted text of ansi sql, with its literals, jdbc escapes and quoted names, is found
   // as before
   @Test
   void quotedTextOfAnsiSqlIsUnchanged() {
      Random random = new Random(5);
      String[] tokens = { "''''", "'it''s'", "'''x'''", "''''''", "'a'''", "'''a'", "'x'", "''",
         "'--'", "'/*'", "'/*<where>*/'", "a", "b", "||", ",", "(", ")", "replace(q,", "=", "<>",
         "and", "{fn ucase(a)}", "{d '2020-01-01'}", "[a]", "`a`", "\"a\"", "\n" };

      for(int k = 0; k < 100_000; k++) {
         StringBuilder sb = new StringBuilder("select ");

         for(int i = 2 + random.nextInt(10); i > 0; i--) {
            sb.append(tokens[random.nextInt(tokens.length)]);
            sb.append(random.nextBoolean() ? " " : "");
         }

         String text = sb.toString();
         assertArrayEquals(Before.findQuoted(text), SQLQuoteScanner.findQuoted(text), text);
      }
   }

   private static void assertNotQuoted(String sql, String part) {
      boolean[] quoted = SQLQuoteScanner.findQuoted(sql);
      int start = sql.indexOf(part);

      for(int i = start; i < start + part.length(); i++) {
         assertFalse(quoted[i], sql + " at " + i);
      }
   }

   private static void assertQuoted(boolean[] quoted, String sql, String part) {
      int start = sql.indexOf(part);

      for(int i = start; i < start + part.length(); i++) {
         assertTrue(quoted[i], sql + " at " + i);
      }
   }

   private static final String[] TOKENS = {
      "'", "''", "'''", "''''", "'''x'''", "\"", "\"\"\"", "`", "\\", "[", "]", "{", "}", "{fn ",
      "{d ", "--", "-- ", "--x", "/*", "*/", "#", "//", "\n", "$$", "$a$", "q'[", "]'", "E'", "a",
      " ", "x", "5--1", "/*<where>*/", "/*</where>*/", "/*<1>*/", "/*</1>*/", "-- vpm.tables: T\n",
      "'it''s'", "'a\nb'", "(", ")", ",", "{'k': 1}", "[a[b]", "'x\\''" };

   // a frozen copy of the scanner before Bug #77695, #77696 (community 0a420f07f), the fuzz
   // compares the quoted text found by the two
   private static final class Before {
      static int skipQuoted(String text, int start, char close, boolean backslash) {
         boolean bracket = close == ']';
         int len = text.length();

         for(int i = start + 1; i < len; i++) {
            char c = text.charAt(i);

            if(backslash && c == '\\') {
               i++;
            }
            else if(c == close) {
               if(i + 1 < len && text.charAt(i + 1) == close) {
                  i++;
               }
               else {
                  return i + 1;
               }
            }
            // a [name] doesn't span lines or nest, so a [ that doesn't open a name (an array
            // subscript) can't hide the following lines
            else if(bracket && (c == '\n' || c == '[')) {
               return -1;
            }
         }

         return -1;
      }

      static int skipComment(String text, int start) {
         return skipComment(text, start, 0);
      }

      private static int skipComment(String text, int start, int rules) {
         int len = text.length();
         char c = text.charAt(start);

         if(c == '#' && (rules & HASH_COMMENT) != 0) {
            return getLineEnd(text, start + 1);
         }

         if(start + 1 >= len) {
            return -1;
         }

         char next = text.charAt(start + 1);

         if(c == '-' && next == '-') {
            // in mysql, -- starts a comment only if a space or control char follows (5--1)
            if((rules & DASH_SPACE) != 0 && start + 2 < len && text.charAt(start + 2) > ' ') {
               return -1;
            }

            return getLineEnd(text, start + 2);
         }
         else if(c == '/' && next == '/' && (rules & SLASH_COMMENT) != 0) {
            return getLineEnd(text, start + 2);
         }
         else if(c == '/' && next == '*') {
            if((rules & NESTED_COMMENT) == 0) {
               int end = text.indexOf("*/", start + 2);
               return end < 0 ? len : end + 2;
            }

            int depth = 1;
            int i = start + 2;

            while(i + 1 < len) {
               if(text.charAt(i) == '/' && text.charAt(i + 1) == '*') {
                  depth++;
                  i += 2;
               }
               else if(text.charAt(i) == '*' && text.charAt(i + 1) == '/') {
                  i += 2;

                  if(--depth == 0) {
                     return i;
                  }
               }
               else {
                  i++;
               }
            }

            return len;
         }

         return -1;
      }

      private static int getLineEnd(String text, int start) {
         int end = text.indexOf('\n', start);
         return end < 0 ? text.length() : end;
      }

      static char getCloseQuote(char c) {
         return c == '\'' || c == '"' || c == '`' ? c : c == '[' ? ']' : 0;
      }

      static boolean[] findQuoted(String text) {
         boolean[] quoted = findQuoted(text, DIALECTS[0]);

         for(int d = 1; d < DIALECTS.length; d++) {
            boolean[] quoted2 = findQuoted(text, DIALECTS[d]);

            for(int i = 0; i < quoted.length; i++) {
               quoted[i] = quoted[i] && quoted2[i];
            }
         }

         return quoted;
      }

      private static boolean[] findQuoted(String text, int rules) {
         int len = text.length();
         boolean[] quoted = new boolean[len];
         // the kinds of quote found not closed, the later quotes of the kind are ordinary
         // characters, so the text is scanned in linear time
         String unclosed = "";
         int i = 0;

         while(i < len) {
            int end = skipComment(text, i, rules);

            if(end >= 0) {
               i = end;
               continue;
            }

            char c = text.charAt(i);
            char close = getCloseQuote(c);
            char kind = 0;

            // a [ is an array subscript, not a quoted name, in most databases
            if(close == ']' && (rules & BRACKET) == 0) {
               close = 0;
            }

            if(close != 0) {
               kind = c;

               if(unclosed.indexOf(kind) < 0) {
                  end = skipQuoted(text, i, close, isBackslash(text, i, rules));
               }
            }
            else if(c == '$' && (rules & DOLLAR_QUOTE) != 0) {
               int open = getDollarTagEnd(text, i);

               if(open > 0) {
                  kind = c;

                  if(unclosed.indexOf(kind) < 0) {
                     int tag = text.indexOf(text.substring(i, open), open);
                     end = tag < 0 ? -1 : tag + open - i;
                  }
               }
            }
            else if((c == 'q' || c == 'Q') && (rules & Q_QUOTE) != 0 && isQQuote(text, i)) {
               kind = 'q';

               if(unclosed.indexOf(kind) < 0) {
                  int tag = text.indexOf(getQClose(text.charAt(i + 2)) + "'", i + 3);
                  end = tag < 0 ? -1 : tag + 2;
               }
            }

            // a [ ends at a line break or another [, so it isn't scanned to the end
            if(kind != 0 && kind != '[' && end < 0 && unclosed.indexOf(kind) < 0) {
               unclosed += kind;
            }

            if(end > 0) {
               for(int j = i; j < end; j++) {
                  quoted[j] = true;
               }

               i = end;
               continue;
            }

            i++;
         }

         return quoted;
      }

      private static boolean isBackslash(String text, int start, int rules) {
         char c = text.charAt(start);

         if(c == '"') {
            return (rules & BACKSLASH_DQ) != 0;
         }
         else if(c != '\'') {
            return false;
         }
         else if((rules & BACKSLASH) != 0) {
            return true;
         }

         // a postgresql E'...' string, the E is not the end of a name
         return (rules & E_STRING) != 0 && start > 0 &&
            (text.charAt(start - 1) == 'E' || text.charAt(start - 1) == 'e') &&
            (start == 1 || !isNameChar(text.charAt(start - 2)));
      }

      private static int getDollarTagEnd(String text, int start) {
         int len = text.length();

         // a $ inside a name (a$b) doesn't open a dollar quote
         if(start > 0 && isNameChar(text.charAt(start - 1))) {
            return -1;
         }

         int i = start + 1;

         // a tag doesn't start with a digit ($1 is a parameter)
         if(i < len && (Character.isLetter(text.charAt(i)) || text.charAt(i) == '_')) {
            while(i < len && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
               i++;
            }
         }

         return i < len && text.charAt(i) == '$' ? i + 1 : -1;
      }

      private static boolean isQQuote(String text, int start) {
         if(start + 2 >= text.length() || text.charAt(start + 1) != '\'' ||
            Character.isWhitespace(text.charAt(start + 2)))
         {
            return false;
         }

         if(start == 0 || !isNameChar(text.charAt(start - 1))) {
            return true;
         }

         char n = text.charAt(start - 1);
         return (n == 'n' || n == 'N') && (start == 1 || !isNameChar(text.charAt(start - 2)));
      }

      private static char getQClose(char open) {
         switch(open) {
         case '[':
            return ']';
         case '{':
            return '}';
         case '(':
            return ')';
         case '<':
            return '>';
         default:
            return open;
         }
      }

      private static boolean isNameChar(char c) {
         return Character.isLetterOrDigit(c) || c == '_' || c == '$';
      }

      // the comment and quoting rules that differ between databases
      private static final int BACKSLASH = 1; // a backslash escapes in '' strings
      private static final int HASH_COMMENT = 2; // # starts a comment to the end of the line
      private static final int DASH_SPACE = 4; // -- starts a comment only if a space follows
      private static final int SLASH_COMMENT = 8; // // starts a comment to the end of the line
      private static final int NESTED_COMMENT = 16; // slash-star comments nest
      private static final int DOLLAR_QUOTE = 32; // $$...$$ and $tag$...$tag$ strings
      private static final int Q_QUOTE = 64; // q'[...]' strings
      private static final int BACKSLASH_DQ = 128; // a backslash escapes in "" strings and names
      private static final int E_STRING = 256; // a backslash escapes in E'' strings only
      private static final int BRACKET = 512; // [...] is a quoted name, not an array subscript

      // the rules of the database families. Each database has a family with all of its rules,
      // so the text the database reads as sql is not quoted by that family's scan, and not by
      // findQuoted. Adding a family can only make less text quoted
      private static final int[] DIALECTS = {
         0, // ansi: db2, derby, informix, exasol, vertica, oracle without q quotes, ...
         BRACKET, // sybase, access
         BRACKET | NESTED_COMMENT, // sql server
         BACKSLASH | BACKSLASH_DQ, // hive, impala
         BACKSLASH | BACKSLASH_DQ | NESTED_COMMENT, // spark, databricks
         BACKSLASH | BACKSLASH_DQ | HASH_COMMENT | DASH_SPACE, // mysql, mariadb
         HASH_COMMENT | DASH_SPACE, // mysql with NO_BACKSLASH_ESCAPES
         BACKSLASH | BACKSLASH_DQ | HASH_COMMENT, // bigquery
         BACKSLASH | BACKSLASH_DQ | HASH_COMMENT | NESTED_COMMENT, // clickhouse
         E_STRING | NESTED_COMMENT | DOLLAR_QUOTE, // postgresql
         Q_QUOTE, // oracle
         BACKSLASH | SLASH_COMMENT | DOLLAR_QUOTE, // snowflake
      };
   }
}
