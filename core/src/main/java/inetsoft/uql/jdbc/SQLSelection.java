/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.uql.path.XSelection;

import java.sql.*;
import java.util.*;

/**
 * The SQLSelection object contains information in the SQL select
 * column list and from clause. It is used to convert a resultset
 * to a table node.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class SQLSelection extends XSelection {
   /**
    * Add the table to the from clause.
    */
   public void addTable(String table) {
      tables.addElement(table);
   }

   /**
    * Get the number of tables in the from clause.
    */
   public int getTableCount() {
      return tables.size();
   }

   /**
    * Get the specified table from the 'from' list.
    */
   public String getTable(int idx) {
      return (String) tables.elementAt(idx);
   }

   /**
    * Set the sorting order on a column.
    */
   public void setSorting(String column, String order) {
      orders.put(column, order);
   }

   public StructuredSQL createStructuredSQL() {
      StructuredSQL sql = new StructuredSQL();

      sql.setSelection(this);

      for(int i = 0; i < tables.size(); i++) {
         sql.addTable((String) tables.elementAt(i));
      }

      Enumeration keys = orders.keys();

      while(keys.hasMoreElements()) {
         String col = (String) keys.nextElement();

         sql.setSorting(col, (String) orders.get(col));
      }

      return sql;
   }

   /**
    * Get the column names to be used with resultset.
    */
   public static String[] getColumnNames(XSelection xselect, ResultSetMetaData meta,
                                         String driver, Connection conn, int[] columnMap)
         throws SQLException
   {
      int ncol = meta == null ? 0 : meta.getColumnCount();
      String[] names = new String[ncol];
      boolean selectOK = ncol == xselect.getColumnCount();
      //@by jasons, workaround for MySQL 5 unparsable query having case
      //sensitivity issues
      boolean mysql = driver != null && (driver.toLowerCase().contains("mysql") ||
         driver.toLowerCase().contains("mariadb"));
      boolean forceMetaDataAlias = xselect.getColumnCount() == 0 && mysql &&
         (conn.getMetaData().getDatabaseMajorVersion() >= 5);

      for(int i = 0; i < ncol; i++) {
         int xIdx = columnMap != null && columnMap.length > 0 ? columnMap[i] : i;
         String colName = meta == null ? null : meta.getColumnName(i + 1);
         String tableName;
         String selName = selectOK ? xselect.getColumn(xIdx) : null;
         String alias = forceMetaDataAlias && meta != null ? meta.getColumnLabel(i + 1)
            : (selName != null ? xselect.getAlias(xIdx) : null);
         String origAlias = selectOK && xselect instanceof JDBCSelection
            ? ((JDBCSelection) xselect).getOriginalAlias(selName) : null;

         if(alias == null && !forceMetaDataAlias) {
            try {
               // @by jasonshobe, bug1410463894949. Because we are using the
               // meta-data from the prepared statement, DB2 returns the
               // column name for meta.getColumnName() and the alias from the
               // "AS" clause in meta.getColumnLabel(). We need to use this
               // value in this case so that the behavior of using the result
               // set's meta-data is preserved.
               alias = meta == null ? null : meta.getColumnLabel(i + 1);

               if(alias != null && alias.equals(colName)) {
                  alias = null;
               }
            }
            catch(SQLException ignore) {
            }
         }

         // @by larryl, sybase driver does not support getTableName, so we
         // ignore it and defaults to no empty. When that happens, the
         // column name of the resultset is used instead of the selection
         // list name, which seems to be the safer option
         try {
            tableName = meta == null ? null : meta.getTableName(i + 1);
         }
         catch(Throwable ex) {
            tableName = "unknown";
         }

         // @by larryl 2003-9-28, the alias defined in selection list has
         // the highest precedency. (50877)
         // @by larryl 2003-9-25, if this is an expression (no table name),
         // and the column is on selection list, then use the actual expr
         // as the column header. this is done since the name assigned by
         // database for expressions are random. could be problem if the
         // selection list is out of sync with the sql, so we only use it
         // if the selection list has the exact same count as resultset
         if(alias != null && alias.length() > 0) {
            names[i] = xselect instanceof JDBCSelection ?
               ((JDBCSelection) xselect).getOriginalAlias(alias) : alias;
         }
         // if a column is mapped to a valias (e.g. ALIAS_0), the name will be carried
         // through to subsequent queries. when we output it, we should always map
         // it back to the original name (45764).
         else if(origAlias != null && !origAlias.equals(selName)) {
            names[i] = origAlias;
         }
         else {
            names[i] = (tableName == null || tableName.equals("") || selectOK) ? selName : colName;
            names[i] = (names[i] == null) ? colName : names[i];

            // @by larryl 2003-9-25, if the above fails to work, and the
            // returned column name is a part of the selection name,
            // use the returned name since it is unlikely
            // an expression (getTableName does not work for oracle)
            //****
            //@by tomzhang 2012-8-13, for mysql, the colName is in lower case
            //while the names[i] ends with upper case of colName, both the
            //cases should be compared as a result
            if(names[i].endsWith("." + colName)) {
               names[i] = colName;
            }
            else if(names[i].endsWith("." + colName.toLowerCase())) {
               int index = names[i].lastIndexOf(".");
               names[i] = names[i].substring(index + 1);
            }
            // fix bug1346843276484
            /*
              if(names[i].toLowerCase().endsWith("." + colName.toLowerCase()))
              {
              int index = names[i].lastIndexOf(".");
              names[i] = names[i].substring(index + 1);
              CUSTOMERS:RESELLER
              }
            */

            // @by mikec, should not use alias here since the xselection
            // will filter the dataset according to column name only later.
         }

         // strip quotes around the label
         if(names[i].startsWith("'") && names[i].endsWith("'") && names[i].length() > 1) {
            names[i] = names[i].substring(1, names[i].length() - 1);
         }
      }

      return names;
   }

   private Vector tables = new Vector(); // tables
   private Hashtable orders = new Hashtable(); // column -> asc|desc
}
