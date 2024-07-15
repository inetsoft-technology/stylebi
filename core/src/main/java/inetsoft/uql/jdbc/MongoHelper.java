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

/**
 * For mongodb with unity jdbc driver.
 * @author  InetSoft Technology
 * @since   12.2
 */
public class MongoHelper extends SQLHelper {
   @Override
   public String getSQLHelperType() {
      return "mongo";
   }

   @Override
   protected String transformDate(String str) {
      str = str.trim();

      if(str.startsWith("{d")) {
         return str.substring(2, str.length() - 1).trim();
      }
      else if(str.startsWith("{ts")) {
         return str.substring(3, str.length() - 1).trim();
      }
      else if(str.startsWith("{t")) {
         return str.substring(2, str.length() - 1).trim();
      }

      return str;
   }

   @Override
   public boolean supportsOperation(String op, String info) {
      // nested queries with unity driver is very problematic. just do post processing for now.
      // (56329, 56311, 56313)
      if(MIRROR_TABLE.equals(op)) {
         return false;
      }

      // not supported by unity. (56304)
      if(CONCATENATION_TABLE.equals(op)) {
         return false;
      }

      return super.supportsOperation(op, info);
   }

   /*
   =======================================================
    this shouldn't be necessary with the mirror disabled.
   =======================================================

   @Override
   public void setUniformSql(UniformSQL uniformSql) {
      super.setUniformSql(uniformSql);
      // generateOrderByClause() depends on runtime parent UniformSQL information
      // so the sql string can't be cached.
      uniformSql.setCacheable(false);
   }

   @Override
   public String generateOrderByClause() {
      String orderBy = super.generateOrderByClause();
      UniformSQL sql = getUniformSQL();
      SelectTable[] tables = sql.getSelectTable();
      XSelection cols = sql.getSelection();

      // for nested queries, unity driver tries to optimize the query by combining
      // mirrors into the outer query, but it may lose the alias information due
      // to an internal bug. (56311)
      if((orderBy == null || orderBy.isEmpty()) && cols.getColumnCount() > 0 &&
         sql.getParent() != null && tables.length == 1)
      {
         // if a query order-by col0 and its outer query also order-by col0, the order-by
         // of the outer query is ignored and results in the same problem. we alternate
         // the order-by columns to work around this problem. (56313)
         int nestLevel = getNestLevel(sql);
         orderBy = "order by " + quotePath(cols.getColumn(nestLevel % cols.getColumnCount()));
      }

      return orderBy;
   }

   private int getNestLevel(UniformSQL sql) {
      SelectTable[] tables = sql.getSelectTable();

      if(tables.length != 1 || !(tables[0].getName() instanceof UniformSQL)) {
         return 0;
      }

      return getNestLevel((UniformSQL) tables[0].getName()) + 1;
   }
   */

   /**
    * Append the join clause to the from.
    * unity driver don't support parentheses in multi joins. (56305)
    */
   @Override
   protected void appendJoinClause(StringBuilder from,
                                   XBinaryCondition condition, String op, String table1, String table2,
                                   int top, XBinaryCondition previousJoin)
   {
      String clause = makeJoinClause(condition, op, table1, table2, previousJoin);
      from.append(clause);
   }
}
