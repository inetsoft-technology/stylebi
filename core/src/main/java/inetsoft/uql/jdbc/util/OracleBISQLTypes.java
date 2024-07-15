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

import inetsoft.uql.XNode;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.util.XUtil;

/**
 * Implementation of <tt>SQLTypes</tt> for Oracle BI.
 *
 * @since 12.0
 */
public class OracleBISQLTypes extends SQLTypes {
   @Override
   public String getQualifiedName(XNode table, JDBCDataSource xds) {
      String tableName = table.getName();
      String catalogName = (String) table.getAttribute("catalog");
      String packageName = (String) table.getAttribute("package");
      String separator = (String) table.getAttribute("catalogSep");
      separator = separator == null ? "." : separator;
      boolean hasPackage = packageName != null && packageName.length() > 0 &&
         !tableName.startsWith(packageName + ".");

      // @by larryl, handle dot in table name
      String fixQuote = (String) table.getAttribute("fixquote");

      if(tableName.contains(separator) && !("false".equals(fixQuote))) {
         SQLHelper helper = SQLHelper.getSQLHelper(xds);
         tableName = XUtil.quoteAlias(tableName, helper);
      }

      if(hasPackage) {
         tableName = packageName + separator + tableName;
      }

      if(catalogName == null) {
         try {
            catalogName = getDefaultCatalog(getChildMetaData(xds));
         }
         catch(Exception ignore) {
         }
      }

      if(catalogName != null &&
         xds.getTableNameOption() != JDBCDataSource.TABLE_OPTION)
      {
         tableName = catalogName + separator + tableName;
      }

      return tableName;
   }
}
