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

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClickhouseHelper extends SQLHelper {
   /**
    * Creates a new instance of <tt>ClickhouseHelper</tt>.
    */
   public ClickhouseHelper() {
      // default connection
   }

   /**
    * The clause appended to a statement with an outer join StyleBI created, on its own line
    * so a trailing line comment can't swallow it (Bug #82305).
    */
   static final String JOIN_USE_NULLS = "\nSETTINGS join_use_nulls = 1";

   @Override
   public String getSQLHelperType() {
      return "clickhouse";
   }

   /**
    * Clickhouse names are case-sensitive, see SQLHelper.isAliasCaseInsensitive (Bug #77644).
    */
   @Override
   public boolean isAliasCaseInsensitive() {
      return false;
   }

   /**
    * ClickHouse decodes backslash escapes inside a '...' string literal, so a backslash in an
    * inlined value must be doubled (Bug #82304).
    */
   @Override
   public boolean isBackslashEscapeChar() {
      return true;
   }

   /**
    * ClickHouse LIKE uses a built-in backslash escape for %, _ and itself (Bug #82304).
    */
   @Override
   public Character getLikeEscapeChar() {
      return '\\';
   }

   /**
    * ClickHouse has no LIKE ... ESCAPE clause; the backslash escape is built in (Bug #82304).
    */
   @Override
   public String getLikeEscapeClause(char esc) {
      return "";
   }

   /**
    * ClickHouse fills the side of an outer join with no match with the type's default value
    * (0, '') unless join_use_nulls = 1. An outer join StyleBI creates means NULL there, as its
    * in-memory join does, so the setting is added to a statement holding one. User-written
    * joins keep the server's setting (Bug #82305).
    */
   @Override
   public String getExecutionSuffix(UniformSQL sql) {
      return sql != null && sql.hasGeneratedOuterJoin() ? JOIN_USE_NULLS : null;
   }

   /**
    * A readonly = 1 user (READONLY, 164) or a settings constraint (452) can't change the
    * setting, and some column types can't be made Nullable (Bug #82305).
    */
   @Override
   public boolean isExecutionSuffixRejected(SQLException ex) {
      String msg = ex.getMessage();

      return ex.getErrorCode() == 164 || ex.getErrorCode() == 452 ||
         msg != null && (msg.contains("join_use_nulls") || msg.contains("Nullable"));
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
    * ClickHouse uses map key access syntax (e.g. m['key']) that cannot be backtick-quoted
    * as a column reference in an outer subquery SELECT. Enable the safe-alias workaround
    * in SQLHelper so the outer query references __col_N__ aliases instead.
    */
   @Override
   protected boolean supportsMapKeySubqueryAliasing() {
      return true;
   }

   /**
    * ClickHouse rejects a bare UNION unless union_default_mode is set (Bug #82306).
    */
   @Override
   public String getUnionQualifier(boolean distinct) {
      return distinct ? "DISTINCT " : "ALL ";
   }

   /**
    * ClickHouse INTERSECT/EXCEPT default to ALL semantics and keep duplicate rows, so the
    * distinct form must be explicit (Bug #82469).
    */
   @Override
   public String getIntersectExceptQualifier(boolean distinct) {
      return distinct ? "DISTINCT " : "ALL ";
   }
}
