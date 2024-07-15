/*
 * inetsoft-hive - StyleBI is a business intelligence web application.
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
package inetsoft.uql.hive;

import inetsoft.uql.XMetaInfo;
import inetsoft.uql.XTableNode;
import inetsoft.uql.jdbc.util.HiveSQLTypes;
import inetsoft.util.swap.XSwappableObjectList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Class that represents a result set from a Hive server
 */
public class HiveTable extends XTableNode {

   /**
    * Constructor
    *
    * @param result ResultSet from the Hive JDBC driver
    * @throws SQLException
    */
   public HiveTable (ResultSet result) throws Exception{
      columnCount = result.getMetaData().getColumnCount();
      types = new Class[columnCount];
      columnNames = new String[columnCount];

      while (result.next()) {
         for(int i = 0; i < columnCount; i++) {
            //ResultSet columns start with index 1
            int resultSetIndex = i + 1;

            if(types[i] == null) {
               types[i] = sqlhandler.convertToJava(
                  result.getMetaData().getColumnType(resultSetIndex));
            }

            if(columnNames[i] == null) {
               columnNames[i] =
                  result.getMetaData().getColumnName(resultSetIndex);
            }
         }

         ResultSetRow row = new ResultSetRow();
         table.add(row);
         row.buildRow(result);
      }

      table.complete();
   }

   /**
    * Check if there are more rows. The first time this method is called,
    * the cursor is positioned at the first row of the table.
    *
    * @return true if there are more rows.
    */
   @Override
   public synchronized boolean next() {
      rowIndex++;

      return rowIndex < table.size();
   }

   /**
    * Get the number of columns in the table.
    */
   @Override
   public int getColCount() {
      return columnCount;
   }

   /**
    * Get the column name.
    *
    * @param col column index.
    *
    * @return column name or alias if defined.
    */
   @Override
   public String getName(int col) {
      return columnNames[col];
   }

   /**
    * Get the column type.
    *
    * @param col column index.
    *
    * @return column data type.
    */
   @Override
   public Class getType(int col) {
      return types[col];
   }

   /**
    * Get the value in the current row at the specified column.
    *
    * @param col column index.
    *
    * @return column value.
    */
   @Override
   public synchronized Object getObject(int col) {
      ResultSetRow row = table.get(rowIndex);
      return row.getValue(col);
   }

   /**
    * Get the meta info at the specified column.
    *
    * @param col column index.
    *
    * @return the meta info.
    */
   @Override
   public XMetaInfo getXMetaInfo(int col) {
      return null;
   }

   /**
    * Move the cursor to the beginning. This is ignored if the cursor
    * is already at the beginning.
    *
    * @return true if the rewinding is successful.
    */
   @Override
   public synchronized boolean rewind() {
      rowIndex = -1;

      return true;
   }

   /**
    * Check if the cursor can be rewinded.
    *
    * @return true if the cursor can be rewinded.
    */
   @Override
   public boolean isRewindable() {
      return true;
   }

   /**
    * This class represents a row in ResultSet
    */
   private class ResultSetRow {
      /**
       * constructor
       */
      public ResultSetRow () {
         rowContent = new Object[columnCount];
      }

      /**
       * load the ResultSet row content into Inetsoft table node
       *
       * @param res ResultSet from query
       * @throws SQLException
       */
      public void buildRow(ResultSet res) throws Exception{
         try {
            for(int i = 0; i < columnCount; i++) {
               //ResultSet column starts with Index 1
               int resultIndex = i + 1;

               rowContent[i] =
                  sqlhandler.getObject(res, resultIndex,
                           res.getMetaData().getColumnType(resultIndex));
            }
         }
         catch(Exception ex) {
            LOG.warn("Failed to read result", ex);
            throw ex;
         }
      }

      /**
       * get the object at the specified column in current row
       *
       * @param col column index
       * @return object at specified column
       */
      public Object getValue(int col) {
         return rowContent[col];
      }

      private Object[] rowContent;
   }

   private XSwappableObjectList<ResultSetRow> table = new XSwappableObjectList<>(null);
   private HiveSQLTypes sqlhandler = new HiveSQLTypes();
   private Class[] types;
   private int columnCount;
   private int rowIndex = -1;
   private String[] columnNames;

   private static final Logger LOG = LoggerFactory.getLogger(HiveTable.class.getName());
}
