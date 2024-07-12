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
package inetsoft.uql.jdbc;

import java.util.Collections;
import java.util.Set;

/**
 * Helper class of UniformSQL. This class generates SQL statements
 * for a Derby database.
 *
 * @author InetSoft Technology
 * @since 8.0
 */
public class DerbyHelper extends SQLHelper {
   /**
    * Get the sql helper type.
    * @return the sql helper type.
    */
   @Override
   public String getSQLHelperType() {
      return "derby";
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

   /**
    * Get a sql string for replacing the table name so a row limit is added
    * to restrict the number of rows from the table.
    */
   @Override
   protected String getTableWithLimit(String tbl, int maxrows) {
      // adding limit to tables causes derby to spend a very long timme in optimizer. (55181)
      // @by anton, the above comment only applies to when using Derby's EmbeddedDriver with subqueries that have row
      // limits. Disallowing limits in subqueries breaks the semantics of the query and can return a wrong result when
      // the subquery has an order by. (Bug #56682)
      return "select * from " + getQuotedTableName(tbl) + " fetch first " + maxrows + " rows only";
   }

   @Override
   protected boolean isTableSubquery() {
      // @see getTableWithLimit().
      return super.isTableSubquery();
   }

   /**
    * Create a query that limits the output to the specified number of rows.
    */
   @Override
   protected String generateMaxRowsClause(String sql, int maxrows) {
      return "select * from (" + sql + ") inner" + System.currentTimeMillis() +
         " fetch first " + maxrows + " rows only";
   }

   /**
    * Check if supports field sorting.
    */
   @Override
   public boolean supportsFieldSorting() {
      if(uniformSql == null || !uniformSql.isDistinct()) {
         return true;
      }

      // embedded does not support field sorting when distinct is on.
      // It seems to be a bug of derby
      JDBCDataSource dx = uniformSql.getDataSource();
      String driver = dx == null ? null : dx.getDriver();
      return driver == null || !driver.contains("EmbeddedDriver");
   }

   /**
    * Set the db product's version.
    */
   @Override
   public void setProductVersion(String version) {
      super.setProductVersion(version);

      if(version != null) {
         int idx = version.indexOf(".");

         if(idx > 0) {
            String major = version.substring(0, idx);
            String minor = version.substring(idx + 1);

            try {
               int majorVer = Integer.parseInt(major);
               int minorVer = Integer.parseInt(minor);

               if(majorVer > 10 || (majorVer == 10 && minorVer >= 3)) {
                  expgrp = true;
               }
            }
            catch(Exception e) {
               // ignore it
            }
         }
      }
   }

   /**
    * Check if supports an operation, if version is equal or greater than 10.3,
    * we would support EXPRESSION_GROUPBY operation.
    * @param op the specified operation.
    * @return <tt>true</tt> if supports, <tt>false</tt> otherwise.
    */
   @Override
   public boolean supportsOperation(String op) {
      if(expgrp && EXPRESSION_GROUPBY.equals(op)) {
         return true;
      }

      return super.supportsOperation(op);
   }

   @Override
   public String generateOrderByClause() {
      // derby throws exception with 'order by' in sub query
      // @by anton, could not reproduce the above statement. This likely only applies to very old derby JDBC drivers.
      /*if(uniformSql.isSubQuery()) {
         return "";
      }*/

      return super.generateOrderByClause();
   }

   private boolean expgrp = false;
   private static final Set<String> keywords = Collections.singleton("cluster");
}
