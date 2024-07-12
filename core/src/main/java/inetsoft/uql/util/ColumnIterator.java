/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
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
      this.sql = sql;

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

      for(int i = 0; i < sarr.length; i++) {
         char c = sarr[i];

         // start with quote as string state.
         if(state == NORMAL_STATE && ('\'' == c || '\"' == c)) {
            state = STRING_STATE;
         }

         // c is in splits array.
         if(Arrays.binarySearch(splits, c) >= 0) {
            // split char is close up, not add value, only move index.
            if(index == i) {
               index = i + 1;
               continue;
            }

            String value = sql.substring(index, i);

            //value is quote, state is string and not end with quote,
            //state is field and not end with "]" to continue.
            if("\'".equals(value) || "\"".equals(value) ||
               (state == STRING_STATE && !(value.endsWith("\"") ||
               value.endsWith("\'"))) || (state == FIELD_STATE && ']' != c))
            {
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
               if(state == STRING_STATE) {
                  state = NORMAL_STATE;
               }
               else if(state == FIELD_STATE) {
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
   private List listeners; // column listeners
   private int index; // current index
   private int state; // current state
   private Pattern pattern = Pattern.compile("[0-9]*(\\.?)[0-9]*");
   private static final int NORMAL_STATE = 1; // normal
   private static final int FIELD_STATE = 2; // maybe field
   private static final int FIELD_STATE2 = 3; // field
   private static final int STRING_STATE = 4; // string
   private static final char[] splits = {'\t', '\n', ' ', '!',  '%', '&', '(',
      ')', '*', '+', ',', '-', '/', ':' , '<', '=', '>', '[', ']', '{', '|', '}'};
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
