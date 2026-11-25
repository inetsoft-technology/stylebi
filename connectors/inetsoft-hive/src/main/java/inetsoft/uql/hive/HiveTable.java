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
package inetsoft.uql.hive;

import inetsoft.uql.XMetaInfo;
import inetsoft.uql.XTableNode;
import inetsoft.uql.jdbc.util.HiveSQLTypes;
import inetsoft.util.swap.XSwappableObjectList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

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
      ResultSetMetaData meta = result.getMetaData();
      columnCount = meta.getColumnCount();
      types = new Class[columnCount];
      String[] names = new String[columnCount];

      // Bug #82332: read the columns from the metadata before the row loop, so a result
      // with no rows still has its column names and types
      for(int i = 0; i < columnCount; i++) {
         //ResultSet columns start with index 1
         int resultSetIndex = i + 1;
         types[i] = getColumnClass(meta.getColumnType(resultSetIndex));
         names[i] = meta.getColumnName(resultSetIndex);
      }

      columnNames = getColumnNames(names);

      while(result.next()) {
         ResultSetRow row = new ResultSetRow();
         table.add(row);
         row.buildRow(result);
      }

      table.complete();
   }

   /**
    * Gets the Java class of a column from its SQL type. SQLTypes.convertToJava maps only
    * Types.BIT to Boolean, and Hive reports a BOOLEAN column as Types.BOOLEAN.
    */
   private Class getColumnClass(int sqlType) {
      if(sqlType == Types.BOOLEAN) {
         return Boolean.class;
      }

      return sqlhandler.convertToJava(sqlType);
   }

   /**
    * Gets the column names without the table qualifier that HiveServer2 adds to the
    * columns of a SELECT * (hive.resultset.use.unique.column.names), so the columns
    * have the names the table describes. A column whose unqualified name is shared by
    * another column, as in a join of two tables with the same column, keeps its
    * qualified name so the columns stay distinguishable.
    *
    * @param names the column names reported by the driver.
    *
    * @return the column names.
    */
   static String[] getColumnNames(String[] names) {
      String[] shortNames = new String[names.length];
      Map<String, Integer> counts = new HashMap<>();

      for(int i = 0; i < names.length; i++) {
         shortNames[i] = getUnqualifiedName(names[i]);

         if(shortNames[i] != null) {
            counts.merge(shortNames[i], 1, Integer::sum);
         }
      }

      String[] result = new String[names.length];

      for(int i = 0; i < names.length; i++) {
         boolean unique = shortNames[i] != null && counts.get(shortNames[i]) == 1;
         result[i] = unique ? shortNames[i] : names[i];
      }

      return result;
   }

   private static String getUnqualifiedName(String name) {
      if(name == null) {
         return null;
      }

      int dot = name.lastIndexOf('.');
      return dot > 0 && dot < name.length() - 1 ? name.substring(dot + 1) : name;
   }

   /**
    * Converts a cell value to the value stored in the table. A BINARY value is returned
    * by the driver as a byte array, which has no usable text form, so it is stored as
    * text, the same form the jdbc.blob.type=text setting gives for a JDBC data source.
    */
   static Object toCellValue(Object value) {
      if(value instanceof byte[]) {
         return new String((byte[]) value, StandardCharsets.UTF_8);
      }

      return value;
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

               rowContent[i] = toCellValue(
                  sqlhandler.getObject(res, resultIndex,
                           res.getMetaData().getColumnType(resultIndex)));
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
