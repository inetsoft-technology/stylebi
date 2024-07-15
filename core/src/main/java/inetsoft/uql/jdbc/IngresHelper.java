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
 * Helper class of UniformSQL. This class generates SQL statements
 * for a Ingres database.
 *
 * @author InetSoft Technology
 * @since 8.0
 */
public class IngresHelper extends SQLHelper {
   /**
    * Get the sql helper type.
    * @return the sql helper type.
    */
   @Override
   public String getSQLHelperType() {
      return "ingres";
   }

   /**
    * Get a sql string for replacing the table name so a row limit is added
    * to restrict the number of rows from the table.
    */
   @Override
   protected String getTableWithLimit(String tbl, int maxrows) {
      // this clause is not supported in subquery by informix
      //return "select first " + maxrows + " * from " + quoteTableName(tbl);
      return null;
   }

   /**
    * Create a query that limits the output to the specified number of rows.
    */
   @Override
   protected String generateMaxRowsClause(String sql, int maxrows) {
      // this clause is not supported in subquery by informix
      /*
      return "select first " + maxrows + " * from (" + sql + ") inner" +
         System.currentTimeMillis();
      */
      return sql;
   }

   /**
    * Transform date formats.
    */
   @Override
   protected String transformDate(String str) {
      // '{d}/{t}/{ts}' are not work in ingres.
      if(str.startsWith("{d ") || str.startsWith("{t ")) {
         str = str.substring(3, str.length() - 1);
      }
      else if(str.startsWith("{ts ")) {
         str = str.substring(4, str.length() - 1);
      }

      return str;
   }
}
