/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.jdbc;

import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;

import java.util.HashSet;
import java.util.Set;

/**
 * Helper class of UniformSQL. This class generate SQL statement
 * from db2 Database.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
class DB2SQLHelper extends SQLHelper {
   /**
    * Get the sql helper type.
    * @return the sql helper type.
    */
   @Override
   public String getSQLHelperType() {
      return "db2";
   }

   /**
    * Check if a word is a keyword.
    * @param word the specified keyword.
    * @return <tt>true</tt> is a keyword, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isKeyword(String word) {
      return super.isKeyword(word) || keywords.contains(word) ||
         (XUtil.isQualifiedName(word) && Tool.containsCJK(word));
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
      buffer.append(" fetch first ");
      buffer.append(maxrows);
      buffer.append(" rows only");
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
      buffer.append(" fetch first ");
      buffer.append(maxrows);
      buffer.append(" rows only");
      return buffer.toString();
   }

   /**
    * DB2 have limitation on alias length, validate it.
    */
   @Override
   protected String getValidAlias(JDBCSelection jsel, int col, String alias) {
      return jsel.getValidAlias(col, alias, this);
   }

   /**
    * Check if alias length is limited.
    */
   @Override
   public boolean isLimitAlias() {
      return true;
   }

   private static Set keywords = new HashSet(); // keywords

   static {
      keywords.add("always");
      keywords.add("blob");
      keywords.add("call");
      keywords.add("concat");
      keywords.add("data");
      keywords.add("database");
      keywords.add("encrypt");
      keywords.add("encryption");
      keywords.add("exclusion");
      keywords.add("explain");
      keywords.add("generated");
      keywords.add("limit");
      keywords.add("lock");
      keywords.add("mode");
      keywords.add("new");
      keywords.add("queryno");
      keywords.add("reorg");
      keywords.add("share");
      keywords.add("use");
      keywords.add("type");
   }
}
