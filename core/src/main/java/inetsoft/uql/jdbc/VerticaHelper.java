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
 * for a MySQL database.
 *
 * @author  InetSoft Technology
 * @since   6.0
 */
public class VerticaHelper extends SQLHelper {
   /**
    * Get the sql helper type.
    * @return the sql helper type.
    */
   @Override
   public String getSQLHelperType() {
      return "vertica";
   }

   @Override
   protected String getTableWithLimit(String tbl, int maxrows) {
      return "select * from " +
         quoteTableName(tbl) +
         " limit " +
         maxrows;
   }

   @Override
   protected String generateMaxRowsClause(String sql, int maxrows) {
      return "select * from (" +
         sql +
         ") inner" + System.currentTimeMillis() +
         " limit " +
         maxrows;
   }

   @Override
   public boolean isApplyMaxRowsToTopLevel() {
      return true;
   }
}
