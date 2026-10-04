/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import java.util.*;
import java.util.regex.Pattern;

/**
 * Column iterator iterates one sql string.
 *
 * @version 11.3
 * @author InetSoft Technology Corp
 */
public class ColumnIterator {
   /**
    * Constructor.
    * @param sql the specified sql string.
    */
   public ColumnIterator(String sql) {
      this(sql, null);
   }

   /**
    * Constructor.
    * @param sql the specified sql string.
    * @param dbType the sql helper type of the database (SQLHelper.getSQLHelperType()), whose
    *               quoting and comment rules are applied, or null if it is not known.
    */
   public ColumnIterator(String sql, String dbType) {
      this.sql = sql;
      this.rules = getRules(dbType);

      this.listeners = new ArrayList();
   }

   /**
    * Get the sql string.
    * @return the sql strings.
    */
   public String getSQL() {
      return sql;
   }

   /**
    * Add a column listener.
    * @param listener the specified column listener.
    */
   public void addColumnListener(ColumnListener listener) {
      listeners.add(listener);
   }

   /**
    * remove a column listener.
    * @param listener the specified column listener.
    */
   public void removeColumnListener(ColumnListener listener) {
      listeners.remove(listener);
   }

   /**
    * Get the count of all the column listeners.
    * @return the count of all the column listener.
    */
   public int getColumnListenerCount() {
      return listeners.size();
   }

   /**
    * Get the column listener at an index.
    * @param index the specified index.
    * @return the column listener at the index.
    */
   public ColumnListener getColumnListener(int index){
      return (ColumnListener) listeners.get(index);
   }

   /**
    * Iterate the sql string.
    */
   public void iterate() {
      Set<String> columns = new HashSet<>();
      char[] sarr = sql.toCharArray();
      index = 0;
      state = NORMAL_STATE;
      // the quotes found not closed, the later quotes of the kind are ordinary characters
      String unclosed = "";

      // the end of the text ends the last name as a split char does
      for(int i = 0; i <= sarr.length; i++) {
         boolean last = i == sarr.length;
         char c = last ? ' ' : sarr[i];

         // Bug #77697, a comment (a mysql # comment) or a variable ($(a.b)) is skipped as a
         // whole. A quote in it opened a literal that swallowed the later names, and the name
         // of a variable was taken for a column
         if(!last && state == NORMAL_STATE) {
            int end = skipComment(sql, i, rules);

            if(end < 0) {
               end = skipVariable(sql, i);
            }

            if(end >= 0) {
               addName(columns, sql.substring(index, i));
               index = end;
               i = index - 1;
               continue;
            }
         }

         // Bug #77663, a string literal is skipped as a whole, a double quote or split char
         // inside it ended the literal early or opened one that swallowed the later names
         if(!last && state == NORMAL_STATE && '\'' == c) {
            int end = SQLQuoteScanner.skipQuoted(sql, i, c, isBackslashEscape(c, rules));
            String value = sql.substring(index, i);

            // Bug #77697, the name before the literal is a column (T.A^'x'), unless it is
            // the prefix of the literal, e.g. the N of N'abc'
            if(!STRING_PREFIX.matcher(value).matches()) {
               addName(columns, value);
            }

            index = end < 0 ? sarr.length : end;
            i = index - 1;
            continue;
         }

         // a quoted name ("T"."A", [T].[A]) is a part of the dotted name, including any split
         // char in it. A quote that is not closed is an ordinary character, as in the other
         // scanners, so the later names are found (e.g. after a mysql "say \"hi")
         char close = last || state != NORMAL_STATE ? 0 : getCloseQuote(sql, i, rules);

         if(close != 0 && unclosed.indexOf(c) < 0 &&
            !(c == '[' && "field".equals(sql.substring(index, i))))
         {
            int end = SQLQuoteScanner.skipQuoted(sql, i, close, isBackslashEscape(c, rules));

            if(end >= 0) {
               i = end - 1;
               continue;
            }
            // a [ that is not closed ends at a line break or another [, so the text isn't
            // scanned to the end again
            else if(c != '[') {
               unclosed += c;
            }
         }

         // c is in splits array.
         if(last || Arrays.binarySearch(splits, c) >= 0) {
            // split char is close up, not add value, only move index.
            if(index >= i) {
               index = i + 1;
               continue;
            }

            String value = sql.substring(index, i);

            //state is field and not end with "]" to continue.
            if(state == FIELD_STATE && ']' != c) {
               continue;
            }

            // start with "field[" as field state.
            if(state == NORMAL_STATE && '[' == c && "field".equals(value)) {
               state = FIELD_STATE;
            }

            index = i + 1;

            // value is not keywords and number.
            if(Arrays.binarySearch(keywords, value.toLowerCase()) < 0 &&
               !pattern.matcher(value).matches())
            {
               if(state == FIELD_STATE) {
                  // delete quote.
                  if(value.startsWith("\"") || value.startsWith("\'")) {
                     value = value.substring(1);
                  }

                  if(value.endsWith("\"") || value.endsWith("\'")) {
                     value = value.substring(0, value.length() - 1);
                  }

                  columns.add(value);
                  state = NORMAL_STATE;
               }
               else if(state == NORMAL_STATE) {
                  columns.add(value);
               }
            }
         }
      }

      //fire event
      for(String column : columns) {
         fireEvent(column);
      }
   }

   /**
    * Add a name that ends at a comment, variable or literal, if it is not a keyword or number.
    */
   private void addName(Set<String> columns, String value) {
      if(!value.isEmpty() && Arrays.binarySearch(keywords, value.toLowerCase()) < 0 &&
         !pattern.matcher(value).matches())
      {
         columns.add(value);
      }
   }

   /**
    * Get the quoting and comment rules of a database.
    * @param dbType the sql helper type of the database (SQLHelper.getSQLHelperType()), or
    *               null if it is not known.
    * @return the rules, for the other methods of this class.
    */
   public static int getRules(String dbType) {
      // the rules common to the databases, as before the database was known
      if(dbType == null) {
         return 0;
      }

      switch(dbType) {
      case "sql server":
      case "sybase":
      case "access":
         return BRACKET;
      case "mysql":
         return BACKSLASH | BACKSLASH_DQ | HASH_COMMENT | DASH_SPACE;
      case "google bigquery":
      case "clickhouse":
         return BACKSLASH | BACKSLASH_DQ | HASH_COMMENT;
      case "hadoop hive":
      case "impala":
      case "databricks":
         return BACKSLASH | BACKSLASH_DQ;
      case "snowflake":
         return BACKSLASH;
      // a generic jdbc database, or no data source
      case "default":
         return BRACKET_NAME;
      default:
         return 0;
      }
   }

   /**
    * Check if the rules quote a name in brackets ([T].[A]).
    */
   public static boolean isBracketQuote(int rules) {
      return (rules & (BRACKET | BRACKET_NAME)) != 0;
   }

   /**
    * Check if a backslash escapes the next character in the text quoted by the quote.
    */
   public static boolean isBackslashEscape(char quote, int rules) {
      return quote == '\'' && (rules & BACKSLASH) != 0 ||
         quote == '"' && (rules & BACKSLASH_DQ) != 0;
   }

   /**
    * Get the closing quote of the literal ('...'), the quoted name or string ("...", `...`)
    * or the bracket quoted name ([...]) that opens at start.
    * @return the closing quote, or 0 if no quoted text opens at start.
    */
   public static char getCloseQuote(String text, int start, int rules) {
      char c = text.charAt(start);

      if(c != '[') {
         return SQLQuoteScanner.getCloseQuote(c);
      }

      if((rules & BRACKET) != 0) {
         return ']';
      }

      // where the database is not known, a [ after a name (a[1], field[...]) is a
      // subscript, not a quoted name
      if((rules & BRACKET_NAME) != 0) {
         char p = start == 0 ? ' ' : text.charAt(start - 1);

         if(!Character.isLetterOrDigit(p) && "_$)]\"`'".indexOf(p) < 0) {
            return ']';
         }
      }

      return 0;
   }

   /**
    * Get the end of the comment that starts at start.
    * @return the index of the line break ending a -- or # comment, the index after a
    * slash-star comment (the text length if it isn't closed), or -1 if no comment starts
    * at start.
    */
   public static int skipComment(String text, int start, int rules) {
      char c = text.charAt(start);

      if(c == '#' && (rules & HASH_COMMENT) != 0) {
         int end = text.indexOf('\n', start);
         return end < 0 ? text.length() : end;
      }

      // in mysql, -- starts a comment only if a space or control char follows (5--1)
      if(c == '-' && (rules & DASH_SPACE) != 0 && start + 2 < text.length() &&
         text.charAt(start + 1) == '-' && text.charAt(start + 2) > ' ')
      {
         return -1;
      }

      return SQLQuoteScanner.skipComment(text, start);
   }

   /**
    * Get the end of the variable ($(name)) that starts at start. A variable ends at the
    * first ), as in VarSQL.
    * @return the index after the variable, or -1 if no variable starts at start.
    */
   public static int skipVariable(String text, int start) {
      if(text.charAt(start) != '$' || !text.startsWith("(", start + 1)) {
         return -1;
      }

      int end = text.indexOf(')', start + 2);
      return end < 0 ? -1 : end + 1;
   }

   /**
    * Column listener.
    */
   public static interface ColumnListener {
      /**
       * Find the next element.
       * @param value the specified element value.
       */
      public void nextElement(String token);
   }

   /**
    * Fire event.
    * @param value the event value.
    */
   private void fireEvent(String val) {
      for(int i = 0; i < getColumnListenerCount(); i++) {
         ColumnListener listener = getColumnListener(i);
         listener.nextElement(val);
      }
   }

   private String sql; // sql string
   private final int rules; // the quoting and comment rules of the database
   private List listeners; // column listeners
   private int index; // current index
   private int state; // current state
   private Pattern pattern = Pattern.compile("[0-9]*(\\.?)[0-9]*");
   private static final int NORMAL_STATE = 1; // normal
   private static final int FIELD_STATE = 2; // maybe field
   private static final int FIELD_STATE2 = 3; // field
   // Bug #77697, ^ ~ and ; end a name too (T.A^'x')
   private static final char[] splits = {'\t', '\n', '\r', ' ', '!',  '%', '&', '(',
      ')', '*', '+', ',', '-', '/', ':', ';', '<', '=', '>', '[', ']', '^', '{', '|', '}',
      '~'};
   // the prefix of a string literal (N'abc', X'0F', mysql _utf8'abc'), not a column
   private static final Pattern STRING_PREFIX =
      Pattern.compile("(?i)|n|e|x|b|q|nq|r|u&|_\\w+");
   // the quoting and comment rules that differ between databases
   private static final int BACKSLASH = 1; // a backslash escapes in '' strings
   private static final int BACKSLASH_DQ = 2; // a backslash escapes in "" strings and names
   private static final int HASH_COMMENT = 4; // # starts a comment to the end of the line
   private static final int DASH_SPACE = 8; // -- starts a comment only if a space follows
   private static final int BRACKET = 16; // [...] is a quoted name
   // [...] is a quoted name where a name starts, the database is not known
   private static final int BRACKET_NAME = 32;
   private static final String[] keywords = {"all", "and", "any",
      "approximate_num", "as", "asc", "between", "both", "by", "case", "cast",
      "char","close_paren", "close_parent", "coalesce", "colon", "colon_equ",
      "comma","concatenation_op", "convert", "corresponding", "cross", "cube",
      "current", "date", "days", "decimal","desc", "digits", "distinct", "div",
      "dolar", "dot", "else", "end", "eof", "eq","esc", "escape", "exact_num",
      "except", "exists", "exponent", "false","field", "for", "from", "full",
      "ge", "group", "grouping", "gt", "having","hex_digit", "hours", "ident",
      "in", "indicator", "inner", "insert", "integer", "intersect", "interval",
      "introducer", "is", "join", "le", "leading", "left", "length", "like",
      "lj", "lower","lt_", "ltrim", "match", "matches", "minus", "minutes",
      "ml_comment","mod","natural", "ne", "neq", "not", "null",
      "null_tree_lookahead", "nullif", "number","oj", "on", "open_paren", "or",
      "order", "outer", "over", "overlaps","partial", "partition", "plus",
      "question_mark", "real", "regexp", "replace", "right", "rj","rollup",
      "rtrim", "second", "select", "semi", "sets", "simple","single_quote",
      "sl_comment", "some", "spident", "spident2","spident_bracket",
      "spident_square", "spident_var", "star","string_literal", "substr",
      "substring", "table", "then", "time", "timestamp", "timezone", "top",
      "trailing", "translate", "trim", "true", "union", "unique", "unknown",
      "unsigned_int", "unsigned_num_lit", "upper", "using", "values", "varchar",
       "when","where", "ws", "xml2clob", "xmlagg", "xmlelement"};
}
