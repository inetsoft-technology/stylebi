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

import java.util.HashSet;
import java.util.Set;

/**
 * @author  InetSoft Technology
 * @since   12.2
 */
public class H2Helper extends SQLHelper {
   @Override
   public String getSQLHelperType() {
      return "h2";
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

   /**
    * Check if a word is a keyword.
    * @param word the specified keyword.
    * @return <tt>true</tt> is a keyword, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isKeyword(String word) {
      return super.isKeyword(word) || keywords.contains(word);
   }

   private static Set keywords = new HashSet();

   static {
      keywords.add("top");
   }
}
