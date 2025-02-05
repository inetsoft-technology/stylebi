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

public class LucidDbHelper extends SQLHelper {
   /**
    * Creates a new instance of <tt>LucidDbHelper</tt>.
    */
   public LucidDbHelper() {
      // default connection
   }

   @Override
   public String getSQLHelperType() {
      return "luciddb";
   }

   @Override
   protected String quoteTableNameWithQuotedSchema(String name, boolean selectClause) {
      if(selectClause) {
         int lastIndex = name.lastIndexOf(".");
         name = name.substring(lastIndex + 1);
      }

      return name;
   }

   @Override
   public String quotePath(String path, boolean physical, boolean force, boolean selectClause) {
      if(selectClause) {
         int lastIndex = path.lastIndexOf(".");

         if(lastIndex != -1) {
            int previousIndex = path.substring(0, lastIndex).lastIndexOf(".");
            path = path.substring(previousIndex + 1);
         }
      }

      return super.quotePath(path, physical, force, selectClause);
   }
}
