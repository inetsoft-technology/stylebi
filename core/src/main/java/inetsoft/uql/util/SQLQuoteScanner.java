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

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Finds the quoted text (string literals and quoted identifiers) and the comments of sql text
 * that is sent to the database as is, so the text scanners don't take the contents of a
 * literal for sql. The quoting rules are those of the target database, which the scanners
 * don't know, so the text is scanned by the rules of each database family, and text is
 * quoted only if it is quoted by the rules of each family, and comment only if it is inside
 * the same comment by the rules of each family.
 */
public final class SQLQuoteScanner {
   private SQLQuoteScanner() {
   }

   /**
    * Get the end of the quoted text opened at start. The closing quote doubled is an escape,
    * as in every database.
    * @param close the closing quote.
    * @param backslash true if a backslash escapes the next character (MySQL, ClickHouse,
    *                  Spark, Hive and PostgreSQL E'' strings).
    * @return the index after the closing quote, or -1 if the quote is not closed.
    */
   public static int skipQuoted(String text, int start, char close, boolean backslash) {
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

   /**
    * Get the end of the comment that starts at start: a -- comment or a slash-star comment.
    * @return the index of the line break ending a -- comment, the index after the closing
    * of a slash-star comment (the text length if it isn't closed), or -1 if no comment
    * starts at start.
    */
   public static int skipComment(String text, int start) {
      return skipComment(text, start, 0);
   }

   /**
    * Get the end of the comment that starts at start, by the comment rules of a database.
    */
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

   /**
    * Get the closing quote of the quoted text opened by the character: a string literal
    * ('...'), or an identifier or string ("...", `...` and [...]).
    * @return the closing quote, or 0 if the character doesn't open quoted text.
    */
   public static char getCloseQuote(char c) {
      return c == '\'' || c == '"' || c == '`' ? c : c == '[' ? ']' : 0;
   }

   /**
    * Find the characters of the sql text that are inside quoted text, quotes included. A
    * quote inside a comment doesn't open quoted text, and a quote that is not closed is
    * an ordinary character. The comment and quoting forms differ between databases (a
    * backslash escape, a # comment, a dollar quote, ...), and the database is not known, so
    * the text is scanned by the rules of each database family, and a character is quoted
    * only if it is quoted by all of them. So no text that one of these databases reads as
    * sql is treated as quoted; such text is treated as sql, as before quoted text was found.
    * This holds because each database has one family scan with all of its rules, and a
    * family only quotes text its database doesn't read as sql (a family may also mark text
    * quoted that its database reads as a comment, such as an informix {...} comment).
    * @return the quoted flag of each character.
    */
   public static boolean[] findQuoted(String text) {
      boolean[] quoted = scan(text, DIALECTS[0], null, null);

      for(int d = 1; d < DIALECTS.length; d++) {
         boolean[] quoted2 = scan(text, DIALECTS[d], null, null);

         for(int i = 0; i < quoted.length; i++) {
            quoted[i] = quoted[i] && quoted2[i];
         }
      }

      return quoted;
   }

   /**
    * Find the comments of the sql text, the comment side of findQuoted. A character is
    * inside a comment only if every database family reads it inside a comment that starts
    * at the same index, so no text that one of these databases reads as sql or as quoted
    * text is treated as a comment.
    * @param tag true at the index of a slash-star that opens a tag of the caller, such as a
    *            vpm tag, which is read as two characters of sql, not as a comment. A slash-star
    *            inside an earlier comment or quoted text is not tested.
    * @return for each character, the index where the comment holding it starts, or -1 if
    * the character is not inside a comment.
    */
   public static int[] findComments(String text, IntPredicate tag) {
      int len = text.length();
      int[] comment = null;

      for(int rules : DIALECTS) {
         int[] comment2 = new int[len];
         Arrays.fill(comment2, -1);
         scan(text, rules, tag, comment2);

         if(comment == null) {
            comment = comment2;
            continue;
         }

         for(int i = 0; i < len; i++) {
            if(comment[i] != comment2[i]) {
               comment[i] = -1;
            }
         }
      }

      return comment;
   }

   /**
    * Find the quoted characters by the comment and quoting rules of a database family.
    * @param tag the slash-stars that are not comments, or null.
    * @param comment if not null, it gets the start of the comment holding each character.
    */
   private static boolean[] scan(String text, int rules, IntPredicate tag, int[] comment) {
      int len = text.length();
      boolean[] quoted = new boolean[len];
      // the kinds of quote found not closed, the later quotes of the kind are ordinary
      // characters, so the text is scanned in linear time
      String unclosed = "";
      int i = 0;

      while(i < len) {
         if(tag != null && tag.test(i)) {
            i += 2;
            continue;
         }

         // Bug #77696, an informix {...} comment isn't sql in informix, so the family marks it
         // quoted. It is not marked comment, since a quote in it may be a literal elsewhere
         // (a clickhouse map {'k': 1})
         if((rules & BRACE_COMMENT) != 0 && text.charAt(i) == '{' && !isJdbcEscape(text, i)) {
            int end = text.indexOf('}', i + 1);
            end = end < 0 ? len : end + 1;
            Arrays.fill(quoted, i, end, true);
            i = end;
            continue;
         }

         int end = skipComment(text, i, rules);

         if(end >= 0) {
            if(comment != null) {
               Arrays.fill(comment, i, end, i);
            }

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
               // Bug #77696, a sql server [name] may hold a [ and line breaks
               end = close == ']' ? skipBracket(text, i) :
                  skipQuoted(text, i, close, isBackslash(text, i, rules));
            }
         }
         else if(c == '$' && (rules & DOLLAR_QUOTE) != 0) {
            int open = getDollarTagEnd(text, i);

            if(open > 0) {
               kind = c;

               if(unclosed.indexOf(kind) < 0) {
                  int tagEnd = text.indexOf(text.substring(i, open), open);
                  end = tagEnd < 0 ? -1 : tagEnd + open - i;
               }
            }
         }
         else if((c == 'q' || c == 'Q') && (rules & Q_QUOTE) != 0 && isQQuote(text, i)) {
            kind = 'q';

            if(unclosed.indexOf(kind) < 0) {
               int qend = text.indexOf(getQClose(text.charAt(i + 2)) + "'", i + 3);
               end = qend < 0 ? -1 : qend + 2;
            }
         }

         if(kind != 0 && end < 0 && unclosed.indexOf(kind) < 0) {
            unclosed += kind;
         }

         if(end > 0) {
            Arrays.fill(quoted, i, end, true);
            i = end;
            continue;
         }

         i++;
      }

      return quoted;
   }

   /**
    * Get the end of the sql server [name] opened at start. A ]] is an escape, and the name
    * may hold a [ and line breaks.
    * @return the index after the closing ], or -1 if it is not closed.
    */
   private static int skipBracket(String text, int start) {
      int len = text.length();

      for(int i = start + 1; i < len; i++) {
         if(text.charAt(i) == ']') {
            if(i + 1 < len && text.charAt(i + 1) == ']') {
               i++;
            }
            else {
               return i + 1;
            }
         }
      }

      return -1;
   }

   /**
    * Check if the { at start opens a jdbc escape ({d ...}, {fn ...}, {call ...}, {?= call
    * ...}, ...), which the driver replaces, not an informix comment.
    */
   private static boolean isJdbcEscape(String text, int start) {
      int len = text.length();
      int i = start + 1;

      while(i < len && Character.isWhitespace(text.charAt(i))) {
         i++;
      }

      if(i < len && text.charAt(i) == '?') {
         return true;
      }

      int j = i;

      while(j < len && Character.isLetter(text.charAt(j))) {
         j++;
      }

      if(j < len && Character.isDigit(text.charAt(j))) {
         return false;
      }

      return JDBC_ESCAPES.contains(text.substring(i, j).toLowerCase(Locale.ROOT));
   }

   /**
    * Check if a backslash escapes the next character in the quoted text opened at start.
    */
   private static boolean isBackslash(String text, int start, int rules) {
      char c = text.charAt(start);

      if(c == '"') {
         return (rules & BACKSLASH_DQ) != 0;
      }
      else if(c == '`') {
         return (rules & BACKTICK_BACKSLASH) != 0;
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

   /**
    * Get the end of the opening $tag$ (or $$) of a postgresql dollar quoted string.
    * @return the index after the opening, or -1 if no dollar quote opens at start.
    */
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

   /**
    * Check if an oracle q quoted string (q'[...]', nq'[...]') opens at the q at start.
    */
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
   private static final int BRACE_COMMENT = 1024; // {...} is a comment, unless a jdbc escape
   private static final int BACKTICK_BACKSLASH = 2048; // a backslash escapes in `` names

   // the jdbc escape keywords after a {
   private static final Set<String> JDBC_ESCAPES =
      Set.of("d", "t", "ts", "fn", "oj", "call", "escape", "limit");

   // the rules of the database families. Each database has a family with all of its rules,
   // so the text the database reads as sql is not quoted by that family's scan, and not by
   // findQuoted, and the text it reads as sql or quoted is not a comment by that family's
   // scan, and not by findComments. A family may mark text quoted that its database reads
   // as a comment (informix {...}), since that text isn't sql either. This invariant, not
   // the number of families, is what keeps sql from being taken as quoted or comment
   private static final int[] DIALECTS = {
      0, // ansi: db2, derby, exasol, vertica, oracle without q quotes, ...
      BRACKET, // sybase, access
      BRACKET | NESTED_COMMENT, // sql server
      BACKSLASH | BACKSLASH_DQ, // hive, impala
      BACKSLASH | BACKSLASH_DQ | NESTED_COMMENT, // spark, databricks
      BACKSLASH | BACKSLASH_DQ | HASH_COMMENT | DASH_SPACE, // mysql, mariadb
      HASH_COMMENT | DASH_SPACE, // mysql with NO_BACKSLASH_ESCAPES
      BACKSLASH | BACKSLASH_DQ | HASH_COMMENT | BACKTICK_BACKSLASH, // bigquery
      BACKSLASH | BACKSLASH_DQ | HASH_COMMENT | NESTED_COMMENT | BACKTICK_BACKSLASH, // clickhouse
      E_STRING | NESTED_COMMENT | DOLLAR_QUOTE, // postgresql
      Q_QUOTE, // oracle
      BACKSLASH | SLASH_COMMENT | DOLLAR_QUOTE, // snowflake
      BRACE_COMMENT, // informix
   };
}
