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

import inetsoft.uql.XNode;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.table.*;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;

import java.io.*;
import java.sql.ResultSet;
import java.sql.Types;

/**
 * SQLTypes specialization that handles MySQL databases.
 *
 * @author  InetSoft Technology
 * @since   7.0
 */
public final class MySQLTypes extends SQLTypes {
   /**
    * Get the table column creator.
    * @param type the specified sql type.
    * @param tname the specified sql type name.
    * @return the table column creator.
    */
   @Override
   public XTableColumnCreator getXTableColumnCreator(int type, String tname) {
      if(type == Types.BIGINT) {
         return XBILongColumn.getCreator();
      }
      else if(isSpecialLong(type, tname)) {
         return XLongColumn.getCreator();
      }

      return super.getXTableColumnCreator(type, tname);
   }

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
    * Fix sql type according to type name.
    */
   @Override
   public int fixSQLType(int sqltype, String tname) {
      if(isSpecialLong(sqltype, tname)) {
         return SPECIAL_LONG;
      }

      return super.fixSQLType(sqltype, tname);
   }

   /**
    * Get an object from a resultset.
    */
   @Override
   public Object getObject(ResultSet result, int idx, int type)
      throws Exception
   {
      if(type == SPECIAL_LONG) {
         return Tool.getData(long.class, result.getString(idx));
      }

      return super.getObject(result, idx, type);
   }

   /**
    * Convert a SQL type to a Java type (class).
    */
   @Override
   public Class<?> convertToJava(int sqltype) {
      if(sqltype == SPECIAL_LONG) {
         return Long.class;
      }

      return super.convertToJava(sqltype);
   }

   @Override
   protected String getText(Reader instream) throws IOException {
      String result;

      if(instream instanceof InputStreamReader) {
         char[] charBuffer = new char[1024];
         StringBuilder clobStr = new StringBuilder();
         int cnt;

         while((cnt = instream.read(charBuffer)) >= 0) {
            boolean nullTerminated = false;

            // @by jasonshobe, bug1411479656478. MySQL binary type set to a
            // character string will be right-padded with zeroes. This causes
            // problems when encoding so drop all trailing zeroes.
            if(cnt > 0 && charBuffer[cnt - 1] == (char) 0) {
               nullTerminated = true;

               while(cnt > 0 && charBuffer[cnt - 1] == (char) 0) {
                  --cnt;
               }
            }

            clobStr.append(new String(charBuffer, 0, cnt));

            // limit the size to 100K
            if(clobStr.length() > 102400 || nullTerminated) {
               break;
            }
         }

         result = convert(clobStr.toString());
      }
      else {
         result = super.getText(instream);
      }

      return result;
   }

   /**
    * Check is special long.
    */
   private boolean isSpecialLong(int sqltype, String tname) {
      return sqltype == Types.VARCHAR && tname != null &&
         tname.toUpperCase().startsWith("UNKNOWN");
   }

   private static int SPECIAL_LONG = -10000;
}