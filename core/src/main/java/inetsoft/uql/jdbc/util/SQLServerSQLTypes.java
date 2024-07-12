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

import inetsoft.uql.table.XStringColumn;
import inetsoft.uql.table.XTableColumnCreator;

import java.sql.CallableStatement;
import java.sql.ResultSet;

/**
 * SQLTypes specialization that handles SQL Server databases.
 *
 * @author  InetSoft Technology
 * @since   6.5
 */
public final class SQLServerSQLTypes extends SQLTypes {
   /**
    * Get an object from a resultset.
    */
   @Override
   public Object getObject(ResultSet result, int idx, int type)
      throws Exception
   {
      if(type == SQLSERVER_STRING || type == SQLSERVER_STRING2) {
         return result.getString(idx);
      }

      return super.getObject(result, idx, type);
   }

   /**
    * Get an object from a CallableStatement.
    */
   @Override
   public Object getObject(CallableStatement cs, int idx, int type)
      throws Exception
   {
      if(type == SQLSERVER_STRING || type == SQLSERVER_STRING2) {
         return cs.getString(idx);
      }

      return super.getObject(cs, idx, type);
   }

   /**
    * Get the table column creator.
    * @param type the specified sql type.
    * @param tname the specified sql type name.
    * @return the table column creator.
    */
   @Override
   public XTableColumnCreator getXTableColumnCreator(int type, String tname) {
      if(type == SQLSERVER_STRING || type == SQLSERVER_STRING2) {
         return XStringColumn.getCreator();
      }

      return super.getXTableColumnCreator(type, tname);
   }

   private static final int SQLSERVER_STRING = -9;
   private static final int SQLSERVER_STRING2 = -8;
}
