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

/**
 * Implementation of <tt>SQLHelper</tt> that provides support for Hadoop Hive
 * connections.
 *
 * @author InetSoft Technology
 * @since  11.5
 */
public class HiveHelper extends SQLHelper {
   /**
    * Creates a new instance of <tt>HiveHelper</tt>.
    */
   public HiveHelper() {
      // default connection
   }

   @Override
   public String getSQLHelperType() {
      return "hadoop hive";
   }

   @Override
   protected String generateMaxRowsClause(String sql, int maxrows) {
      return "select * from (" + sql + ") inner" +
         System.currentTimeMillis() + " limit " + maxrows;
   }

   //fix Bug#3403, hive jdbc driver cannot parse the {d}/{ts}/{t} tags we use;
   //use the hive's built-in conversion function to convert string instead
   @Override
   protected String transformDate(String str) {
      str = str.trim();

      if(str.startsWith("{d")) {
         String str_hive = str.substring(2, str.length() -1).trim();
         return "cast(" + str_hive + " as Date)";
      }
      else if(str.startsWith("{ts")) {
         String str_hive = str.substring(3, str.length() -1).trim();
         return "cast(" + str_hive + " as TimeStamp)";
      }
      else if(str.startsWith("{t")) {
         return str.substring(2, str.length() -1).trim();
      }

      return str;
   }

   @Override
   public String getQuote() {
      return "`";
   }
}
