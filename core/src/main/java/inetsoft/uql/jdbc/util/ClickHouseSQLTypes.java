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
package inetsoft.uql.jdbc.util;

import inetsoft.uql.XNode;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;

import java.sql.ResultSet;
import java.sql.Types;

/**
 * SQLTypes specialization that handles MySQL databases.
 *
 * @author  InetSoft Technology
 * @since   7.0
 */
public final class ClickHouseSQLTypes extends SQLTypes {
   /**
    * Get the fully qualified table name from the table node.
    */
   @Override
   public String getQualifiedName(XNode table, JDBCDataSource xds) {
      String tblname = table.getName();
      String catalog = (String) table.getAttribute("catalog");
      String pkg = (String) table.getAttribute("package");
      String catSep = (String) table.getAttribute("catalogSep");
      catSep = catSep == null ? "." : catSep;
      boolean hasPackage = pkg != null && pkg.length() > 0 &&
         !tblname.startsWith(pkg + ".");

      // @by larryl, handle dot in table name
      String fixquote = (String) table.getAttribute("fixquote");
      SQLHelper helper = SQLHelper.getSQLHelper(xds);

      if(catalog != null && catalog.indexOf('.') >= 0 && !("false".equals(fixquote))) {
         catalog = XUtil.quoteAlias(catalog, helper);
      }

      if(tblname.indexOf(".") > 0 && !("false".equals(fixquote))) {
         tblname = XUtil.quoteAlias(tblname, helper);
      }

      if(hasPackage) {
         tblname = pkg + "." + tblname;
      }

      int toption = xds == null ? JDBCDataSource.DEFAULT_OPTION : xds.getTableNameOption();
      String defaultDb = xds == null ? null : xds.getDefaultDatabase();

      if(catalog == null) {
         try {
            catalog = getDefaultCatalog(getChildMetaData(xds));
         }
         catch(Exception ignore) {
         }
      }

      if(catalog != null && catalog.length() > 0 &&
         (toption == JDBCDataSource.CATALOG_SCHEMA_OPTION ||
         toption == JDBCDataSource.DEFAULT_OPTION &&
         !catalog.equals(defaultDb)))
      {
         tblname = catalog + catSep + tblname;
      }

      return tblname;
   }

   /**
    * Get an object from a resultset.
    */
   @Override
   public Object getObject(ResultSet result, int idx, int type)
      throws Exception
   {
      if(type == Types.ARRAY) {
         return result.getString(idx);
      }
      else if(type == Types.STRUCT) {
         String tupleString = result.getString(idx);
         tupleString = tupleString.substring(1, tupleString.length() - 1);

         return "(" + tupleString + ")";
      }

      return super.getObject(result, idx, type);
   }
}