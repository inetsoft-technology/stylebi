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
package inetsoft.uql.jdbc.util;

import inetsoft.uql.schema.FloatType;
import inetsoft.uql.schema.XTypeNode;

import java.sql.Types;

public class H2SQLTypes extends SQLTypes {

   @Override
   public XTypeNode createTypeNode(String name, int sqltype, String tname) {
      sqltype = fixSQLType(sqltype, tname);

      if(sqltype == Types.REAL) {
         XTypeNode node = new FloatType(name);
         node.setAttribute("sqltype", sqltype);

         return node;
      }

      return super.createTypeNode(name, sqltype, tname);
   }
}
