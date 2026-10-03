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

/**
 * Finds the quoted text (string literals and quoted identifiers) and the comments of sql text
 * that is sent to the database as is, so the text scanners don't take the contents of a
 * literal for sql. The quoting rules are those of the target database, which the scanners
 * don't know, so only the rules common to the databases are applied, and text is quoted
 * only if it is quoted by the rules of each database family.
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
    * @return the quoted flag of each character.
    */
   public static boolean[] findQuoted(String text) {
      boolean[] quoted = findQuoted(text, DIALECTS[0]);

      for(int d = 1; d < DIALECTS.length; d++) {
         boolean[] quoted2 = findQuoted(text, DIALECTS[d]);

         for(int i = 0; i < quoted.length; i++) {
            quoted[i] = quoted[i] && quoted2[i];
         }
      }

      return quoted;
   }

   /**
    * Find the quoted characters by the comment and quoting rules of a database family.
    */
   private static boolean[] findQuoted(String text, int rules) {
      boolean backslash = (rules & BACKSLASH) != 0;
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

         if(close != 0) {
            kind = c;

            if(unclosed.indexOf(kind) < 0) {
               end = skipQuoted(text, i, close, backslash && (c == '\'' || c == '"'));
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
   private static final int BACKSLASH = 1; // a backslash escapes in '' and "" strings
   private static final int HASH_COMMENT = 2; // # starts a comment to the end of the line
   private static final int DASH_SPACE = 4; // -- starts a comment only if a space follows
   private static final int SLASH_COMMENT = 8; // // starts a comment to the end of the line
   private static final int NESTED_COMMENT = 16; // slash-star comments nest
   private static final int DOLLAR_QUOTE = 32; // $$...$$ and $tag$...$tag$ strings
   private static final int Q_QUOTE = 64; // q'[...]' strings

   // the rules of the database families. Adding a family can only make less text quoted
   private static final int[] DIALECTS = {
      0, // ansi: sql server, db2, oracle without q quotes, ...
      BACKSLASH, // spark, hive, clickhouse, postgresql E'' strings, ...
      BACKSLASH | HASH_COMMENT | DASH_SPACE, // mysql, mariadb
      HASH_COMMENT | DASH_SPACE, // mysql with NO_BACKSLASH_ESCAPES
      NESTED_COMMENT | DOLLAR_QUOTE, // postgresql
      Q_QUOTE, // oracle
      BACKSLASH | SLASH_COMMENT | DOLLAR_QUOTE, // snowflake
   };
}
