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
 * don't know, so only the rules common to the databases are applied.
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
    * Get the end of the comment that starts at start.
    * @return the index of the line break ending a -- comment, the index after the closing
    * of a slash-star comment (the text length if it isn't closed), or -1 if no comment
    * starts at start.
    */
   public static int skipComment(String text, int start) {
      int len = text.length();

      if(start + 1 >= len) {
         return -1;
      }

      char c = text.charAt(start);
      char next = text.charAt(start + 1);

      if(c == '-' && next == '-') {
         int end = text.indexOf('\n', start + 2);
         return end < 0 ? len : end;
      }
      else if(c == '/' && next == '*') {
         int end = text.indexOf("*/", start + 2);
         return end < 0 ? len : end + 2;
      }

      return -1;
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
    * an ordinary character. A backslash escapes a quote in some databases only, so a
    * character is quoted only if it is with and without backslash escapes. A text that
    * is quoted under one rule only (an odd count of \' and a later literal) is treated as
    * sql, as before quoted text was found.
    * @return the quoted flag of each character.
    */
   public static boolean[] findQuoted(String text) {
      boolean[] quoted = findQuoted(text, false);
      boolean[] quoted2 = findQuoted(text, true);

      for(int i = 0; i < quoted.length; i++) {
         quoted[i] = quoted[i] && quoted2[i];
      }

      return quoted;
   }

   private static boolean[] findQuoted(String text, boolean backslash) {
      int len = text.length();
      boolean[] quoted = new boolean[len];
      // the quotes found not closed, the later quotes of the kind are ordinary characters,
      // so the text is scanned in linear time
      String unclosed = "";
      int i = 0;

      while(i < len) {
         int end = skipComment(text, i);

         if(end >= 0) {
            i = end;
            continue;
         }

         char c = text.charAt(i);
         char close = getCloseQuote(c);

         if(close != 0 && unclosed.indexOf(c) < 0) {
            end = skipQuoted(text, i, close, backslash && (c == '\'' || c == '"'));

            // a [ ends at a line break or another [, so it isn't scanned to the end
            if(end < 0 && c != '[') {
               unclosed += c;
            }

            if(end > 0) {
               for(int j = i; j < end; j++) {
                  quoted[j] = true;
               }

               i = end;
               continue;
            }
         }

         i++;
      }

      return quoted;
   }
}
