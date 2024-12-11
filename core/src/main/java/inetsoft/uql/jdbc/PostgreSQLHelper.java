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
package inetsoft.uql.jdbc;

import java.util.HashSet;
import java.util.Set;

/**
 * Helper class of UniformSQL. This class generates SQL statements
 * for a PostgreSQL database.
 *
 * @author  InetSoft Technology
 * @since   11.0
 */
public class PostgreSQLHelper extends SQLHelper {
   /**
    * Check if is case sensitive when quote table or column or schema.
    */
   @Override
   public boolean isCaseSensitive() {
      return true;
   }

   /**
    * Get the sql helper type.
    * @return the sql helper type.
    */
   @Override
   public String getSQLHelperType() {
      return "postgresql";
   }

   /**
    * Get the function used for converting to lower case.
    */
   public String getDbLowerCaseFunction() {
      return "LOWER";
   }

   /**
    * Get a sql string for replacing the table name so a row limit is added
    * to restrict the number of rows from the table.
    */
   @Override
   protected String getTableWithLimit(String tbl, int maxrows) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("select * from ");
      buffer.append(getQuotedTableName(tbl));
      buffer.append(" limit ");
      buffer.append(maxrows);
      return buffer.toString();
   }

   /**
    * Create a query that limits the output to the specified number of rows.
    */
   @Override
   protected String generateMaxRowsClause(String sql, int maxrows) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("select * from (");
      buffer.append(sql);
      buffer.append(") inner");
      buffer.append(System.currentTimeMillis());
      buffer.append(" limit ");
      buffer.append(maxrows);
      return buffer.toString();
   }

   protected String getValidAlias(JDBCSelection jsel, int col, String alias) {
      return jsel.getValidAlias(col, alias, this);
   }

   /**
    * Check if alias length is limited.
    */
   public boolean isLimitAlias() {
      return true;
   }

   /**
    * Check if a word is a keyword.
    * @param word the specified keyword.
    * @return <tt>true</tt> is a keyword, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isKeyword(String word) {
      return super.isKeyword(word) || keywords.contains(word);
   }

   @Override
   protected String quoteColumnAlias(String alias, boolean same, boolean part) {
      return (alias.indexOf('"') < 0) ?
         '"' + alias + '"' : super.quoteColumnAlias(alias, same,part);
   }

   @Override
   public String fixSQLExpression(String sql) {
      if(sql != null && sql.trim().matches("^'[^']*'$")) {
         // expression is a string constant, cannot be used in group by clause in PostgreSQL,
         // convert to an identity function
         return "CAST(" + sql + " AS VARCHAR)";
      }

      return sql;
   }

   private static Set keywords = new HashSet(); // keywords

   static {
      keywords.add("analyse");
      keywords.add("analyze");
      keywords.add("array");
      keywords.add("asymmetric");
      keywords.add("binary");
      keywords.add("current_role");
      keywords.add("do");
      keywords.add("ilike");
      keywords.add("isnull");
      keywords.add("limit");
      keywords.add("localtime");
      keywords.add("localtimestamp");
      keywords.add("new");
      keywords.add("off");
      keywords.add("offset");
      keywords.add("old");
      keywords.add("placing");
      keywords.add("similar");
      keywords.add("verbose");
   }
}
