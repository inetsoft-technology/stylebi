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

public class ClickhouseHelper extends SQLHelper {
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

      if(str.startsWith("{ts")) {
         return str.substring(3, str.length() -1).trim();
      }
      else {
         return super.transformDate(str);
      }
   }
}
