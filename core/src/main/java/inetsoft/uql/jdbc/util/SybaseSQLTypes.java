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
package inetsoft.uql.jdbc.util;

import inetsoft.uql.jdbc.JDBCDataSource;

/**
 * SQLTypes specialization that handles Sybase databases.
 *
 * @author  InetSoft Technology
 * @since   6.5
 */
public final class SybaseSQLTypes extends SQLTypes {
   /**
    * Strategy pattern method that allows subclasses to change how and when
    * the table name is prefixed with the schema name.
    *
    * @param schema the name of the schema.
    * @param table the name of the table.
    * @param xds the datasource containing the table.
    * @param buffer the buffer to write the qualified table name to.
    * @return <code>true</code> if the schema is used in the table name.
    * @since 6.5
    */
   @Override
   protected boolean getTableNameWithSchema(String schema, String table,
                                            JDBCDataSource xds,
                                            StringBuilder buffer) {
      boolean hasSchema = false;

      if(schema != null && schema.length() > 0 &&
         (xds == null || !schema.equals("dbo") ||
          !schema.equalsIgnoreCase(xds.getUser()))) {
         buffer.append(schema);
         buffer.append('.');
         buffer.append(table);
         hasSchema = true;
      }
      else {
         buffer.append(table);
      }

      return hasSchema;
   }
}
