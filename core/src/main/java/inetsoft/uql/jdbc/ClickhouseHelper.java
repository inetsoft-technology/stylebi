/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClickhouseHelper extends SQLHelper {
   // Matches ClickHouse map key access expressions like m['key2']
   private static final Pattern MAP_KEY_ACCESS =
      Pattern.compile("([^\\s\\[\\]]+)\\[\\s*'([^\\s']+)'\\s*\\]");

   // Maps "tableAlias.originalColExpr" -> safe alias used in the inner query
   private final Map<String, String> subQueryMapKeyAliases = new HashMap<>();

   /**
    * Creates a new instance of <tt>ClickhouseHelper</tt>.
    */
   public ClickhouseHelper() {
      // default connection
   }

   @Override
   public String getSQLHelperType() {
      return "clickhouse";
   }

   @Override
   protected String transformDate(String str) {
      str = str.trim();

      if(str.startsWith("{ts") || str.startsWith("({ts")) {
         Pattern pattern = Pattern.compile("\\{ts\\s*'(.*?)'\\}");
         Matcher matcher = pattern.matcher(str);

         return matcher.replaceAll("toDateTime('$1')");
      }
      else if(str.startsWith("{d") || str.startsWith("({d") ) {
         Pattern pattern = Pattern.compile("\\{d\\s*'(.*?)'\\}");
         Matcher matcher = pattern.matcher(str);

         return matcher.replaceAll("toDate('$1')");
      }
      else {
         return super.transformDate(str);
      }
   }

   /**
    * Check if a word is a keyword.
    * @param word the specified keyword.
    * @return <tt>true</tt> is a keyword, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isKeyword(String word) {
      if(word.equals("default")) {
         return false;
      }

      return super.isKeyword(word);
   }

   /**
    * When the inner subquery contains ClickHouse map key access expressions like m['key2'],
    * ClickHouse cannot resolve a backtick-quoted outer column reference such as
    * `alias`.`m['key2']` — it re-interprets the brackets as map access rather than treating
    * the whole token as a column name. To work around this, we add a safe alias to each
    * such column in the inner SQL (e.g. m['key2'] AS __col_0__) and record the mapping so
    * that getValidSubAlias() can return the safe alias for use in the outer SELECT.
    */
   @Override
   protected String getSubQueryString(SelectTable table, UniformSQL sub) {
      String sql = sub.toString().trim();
      Matcher matcher = MAP_KEY_ACCESS.matcher(sql);

      if(!matcher.find()) {
         return sql;
      }

      // Only transform columns in the SELECT part (before the first FROM keyword)
      int fromIdx = findFromIndex(sql);
      String selectPart = fromIdx >= 0 ? sql.substring(0, fromIdx) : sql;
      String tail = fromIdx >= 0 ? sql.substring(fromIdx) : "";

      matcher = MAP_KEY_ACCESS.matcher(selectPart);
      StringBuilder result = new StringBuilder();
      String tableAlias = table.getAlias();
      int colIndex = 0;
      boolean modified = false;

      while(matcher.find()) {
         String expr = matcher.group();
         // Check if this expression is already followed by an AS alias
         String afterExpr = selectPart.substring(matcher.end()).stripLeading();
         boolean hasAlias = afterExpr.length() >= 3
            && afterExpr.substring(0, 2).equalsIgnoreCase("AS")
            && Character.isWhitespace(afterExpr.charAt(2));

         if(hasAlias) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(expr));
         }
         else {
            String safeAlias = "__col_" + colIndex++ + "__";
            subQueryMapKeyAliases.put(tableAlias + "." + expr, safeAlias);
            matcher.appendReplacement(result,
               Matcher.quoteReplacement(expr + " AS " + safeAlias));
            modified = true;
         }
      }

      if(!modified) {
         return sql;
      }

      matcher.appendTail(result);
      return result + tail;
   }

   /**
    * If the column in a subquery is a map key access expression for which we added a safe
    * alias in getSubQueryString(), return that safe alias so the outer SELECT references the
    * safe alias instead of the unresolvable backtick-quoted map expression.
    */
   @Override
   public String getValidSubAlias(String table, String c) {
      String safeAlias = subQueryMapKeyAliases.get(table + "." + c);

      if(safeAlias != null) {
         return safeAlias;
      }

      return super.getValidSubAlias(table, c);
   }

   /**
    * Find the index of the first FROM keyword that is not inside parentheses or quotes.
    */
   private static int findFromIndex(String sql) {
      int depth = 0;
      boolean inString = false;
      char stringChar = 0;

      for(int i = 0; i < sql.length(); i++) {
         char c = sql.charAt(i);

         if(inString) {
            if(c == stringChar) {
               inString = false;
            }
         }
         else if(c == '\'' || c == '"' || c == '`') {
            inString = true;
            stringChar = c;
         }
         else if(c == '(') {
            depth++;
         }
         else if(c == ')') {
            depth--;
         }
         else if(depth == 0 && i + 4 <= sql.length()) {
            String word = sql.substring(i, i + 4);

            if(word.equalsIgnoreCase("FROM")
               && (i == 0 || Character.isWhitespace(sql.charAt(i - 1)))
               && (i + 4 >= sql.length() || Character.isWhitespace(sql.charAt(i + 4))))
            {
               return i;
            }
         }
      }

      return -1;
   }
}
