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

import inetsoft.uql.util.XUtil;

public class ExasolHelper extends SQLHelper {
   /**
    * Creates a new instance of <tt>ExasolHelper</tt>.
    */
   public ExasolHelper() {
      // default connection
   }

   @Override
   public String getSQLHelperType() {
      return "exasol";
   }

   @Override
   public boolean isCaseSensitive() {
      return true;
   }

   @Override
   protected String getTableWithLimit(String tbl, int maxrows) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("select * from ");
      buffer.append(getQuotedTableName(tbl));
      buffer.append(" limit ");
      buffer.append(maxrows);
      return buffer.toString();
   }
}
