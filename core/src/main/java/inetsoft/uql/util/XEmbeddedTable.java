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
package inetsoft.uql.util;

import inetsoft.report.*;
import inetsoft.report.internal.Util;
import inetsoft.report.lens.DefaultTableDataDescriptor;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.XTable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.table.*;
import inetsoft.util.*;
import inetsoft.util.swap.XSwappableObjectList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.awt.event.ActionListener;
import java.io.*;
import java.text.Format;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;

/**
 * XEmbeddedTable stores embedded data.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class XEmbeddedTable
   implements Serializable, Cloneable, XTable, XMLSerializable, DataSerializable
{
   /**
    * Constructor.
    */
   public XEmbeddedTable() {
      this(true);
   }

   /**
    * Constructor.
    * @param initing true to initialize data.
    */
   public XEmbeddedTable(boolean initing) {
      super();

      types = new String[] {XSchema.STRING};
      xtable = createTable();

      if(initing) {
         Object[] header = new Object[]{ "col1" };
         Object[] row = new Object[]{ null };
         xtable.addRow(header);
         xtable.addRow(row);
         complete();
      }

      identifiers = new XIdentifierContainer(this);
   }

   /**
    * Constructor.
    */
   public XEmbeddedTable(String[] types, Object[][] arr) {
      this(false);

      if(types.length < 0 || arr.length < 0) {
         throw new RuntimeException("invalid types or data found!");
      }

      this.types = types;
      this.xtable = createTable();

      for(int i = 0; i < types.length; i++) {
         types[i] = types[i] == null ? XSchema.STRING : types[i];
      }

      for(Object[] row : arr) {
         xtable.addRow(row);
      }

      complete();
   }

   /**
    * Constructor.
    */
   public XEmbeddedTable(String[] types, XSwappableObjectList data) {
      this(false);

      this.types = types;
      this.xtable = createTable();

      for(int i = 0; i < types.length; i++) {
         types[i] = types[i] == null ? XSchema.STRING : types[i];
      }

      for(int i = 0; i < data.size(); i++) {
         xtable.addRow((Object[]) data.get(i));
      }

      complete();
   }

   /**
    * Constructor.
    */
   public XEmbeddedTable(String[] types, Object[][] data, Object[][] links) {
      this(types, data);

      for(int i = 0; i < data.length; i++) {
         for(int j = 0; j < this.types.length; j++) {
            setHyperlinks(i, j, (Object[]) links[i][j]);
         }
      }
   }

   /**
    * Constructor.
    */
   public XEmbeddedTable(XTable table) {
      this(createTypes(table), table);
   }

   public static String[] createTypes(XTable table) {
      if(table == null) {
         return new String[0];
      }

      int col = table.getColCount();
      String[] types = new String[col];

      for(int i = 0; i < types.length; i++) {
         Class cls = table.getColType(i);
         types[i] = Tool.getDataType(cls);
      }

      return types;
   }

   public XEmbeddedTable(String[] types, XTable table) {
      this(false);
      table.moreRows(Integer.MAX_VALUE);

      int row = table.getRowCount();
      int col = table.getColCount();

      if(col <= 0) {
         throw new EmptyTableToEmbeddedException("Empty table is not supported: " + table);
      }

      this.types = types;

      if(table instanceof XSwappableTable) {
         this.xtable = (XSwappableTable) table;
      }
      else {
         this.xtable = createTable();

         for(int i = 0; i < row; i++) {
            Object[] arr = new Object[col];

            for(int j = 0; j < col; j++) {
               arr[j] = table.getObject(i, j);
            }

            xtable.addRow(arr);
         }
      }

      complete();
   }

   /**
    * Set hyperlinks.
    * @param row the specified table row.
    * @param col the specified table column.
    * @param refs the specified hyperlinks array.
    */
   public void setHyperlinks(int row, int col, Object[] refs) {
      if(links == null) {
         if(getRowCount() < 0) {
            throw new RuntimeException(tableNotReady);
         }

         links = new Object[getRowCount()][getColCount()];
      }

      links[row][col] = refs;
   }

   /**
    * Get the hyperlinks.
    * @param row the specified table row.
    * @param col the specified table column.
    * @return the specified hyperlinks array.
    */
   public Object getHyperlinks(int row, int col) {
      return links == null ? null : links[row][col];
   }

   /**
    * Check if there are more rows. The row index is the row that will be
    * accessed. This method must block until the row is available, or
    * return false if the row does not exist in the table. This method is
    * used to iterate through the table, and allow partial table to be
    * accessed in report processing.
    * @param row row number. If EOT is passed in, this method should wait
    * until the table is fully loaded.
    * @return true if the row exists, or false if no more rows.
    */
   @Override
   public boolean moreRows(int row) {
      if(!completed) {
         rowLock.lock();

         try {
            while(row >= xtable.getRowCount() && !completed) {
               try {
                  rowCondition.await(500L, TimeUnit.MILLISECONDS);
               }
               catch(Exception ignore) {
                  // ignore it
               }
            }
         }
         finally {
            rowLock.unlock();
         }
      }

      return row < xtable.getRowCount();
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows. If the table is loading in background and loading
    * is not done, return the negative number of loaded rows minus 1.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      return xtable.getRowCount();
   }

   private XSwappableTable createTable() {
      XSwappableTable table = new XSwappableTable();
      XTableColumnCreator[] creators = new XTableColumnCreator[types.length];

      for(int i = 0; i < types.length; i++) {
         creators[i] = XObjectColumn.getCreator();
         creators[i].setDynamic(false);
      }

      table.init(creators);
      return table;
   }

   private void copyTable(XSwappableTable table, int offset, int count) {
      int last = offset + count;

      for(int row = offset; row < last && xtable.moreRows(row); row++) {
         Object[] rowData = new Object[types.length];

         for(int column = 0; column < types.length; column++) {
            rowData[column] = xtable.getObject(row, column);
         }

         table.addRow(rowData);
      }
   }

   private void swapTable(XSwappableTable table) {
      synchronized(this) {
         this.xtable = table;
      }
   }

   /**
    * Set the row count.
    * @param count the specified row count.
    */
   public void setRowCount(int count) {
      if(count < 0) {
         throw new RuntimeException("Invalid row count found!");
      }

      if(getRowCount() < 0) {
         throw new RuntimeException(tableNotReady);
      }

      if(count == xtable.getRowCount()) {
         return;
      }

      XSwappableTable nDataTable = createTable();

      // cut?
      if(count < xtable.getRowCount()) {
         copyTable(nDataTable, 0, count);
      }
      // append?
      else {
         int len = count - xtable.getRowCount();
         copyTable(nDataTable, 0, xtable.getRowCount());

         for(int i = 0; i < len; i++) {
            Object[] arr = new Object[types.length];
            nDataTable.addRow(arr);
         }
      }

      nDataTable.complete();
      swapTable(nDataTable);
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public int getColCount() {
      return types.length;
   }

   /**
    * Set the column count.
    * @param count the specified column count.
    */
   public void setColCount(int count) {
      if(count < 0) {
         throw new RuntimeException("Invalid column count found!");
      }

      if(getRowCount() < 0) {
         throw new RuntimeException(tableNotReady);
      }

      if(count == types.length) {
         return;
      }

      int ocount = types.length;
      String[] ntypes = new String[count];
      System.arraycopy(types, 0, ntypes, 0, Math.min(ocount, count));

      for(int i = ocount; i < count; i++) {
         ntypes[i] = XSchema.STRING;
      }

      types = ntypes;
      int clen = Math.min(ocount, count);
      int len = xtable.getRowCount();
      XSwappableTable nDataTable = createTable();

      for(int i = 0; i < len; i++) {
         Object[] narr = new Object[types.length];

         for(int j = 0; j < clen; j++) {
            narr[j] = xtable.getObject(i, j);
         }

         nDataTable.addRow(narr);
      }

      nDataTable.complete();
      swapTable(nDataTable);
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows. Default is 1.
    */
   @Override
   public int getHeaderRowCount() {
      return 1;
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      return 0;
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as trailer rows.
    * @return number of header rows.
    */
   @Override
   public int getTrailerRowCount() {
      return 0;
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as trailer columns.
    */
   @Override
   public int getTrailerColCount() {
      return 0;
   }

   /**
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isNull(int r, int c) {
      if(!completed) {
         rowLock.lock();

         try {
            return xtable.getObject(r, c) == null;
         }
         finally {
            rowLock.unlock();
         }
      }

      return xtable.getObject(r, c) == null;
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      if(!completed) {
         rowLock.lock();

         try {
            return xtable.getObject(r, c);
         }
         finally {
            rowLock.unlock();
         }
      }

      return xtable.getObject(r, c);
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the double value in the specified row.
    */
   @Override
   public final double getDouble(int r, int c) {
      try {
         return ((Number) getObject(r, c)).doubleValue();
      }
      catch(Throwable ex) {
         return 0;
      }
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the float value in the specified row.
    */
   @Override
   public final float getFloat(int r, int c) {
      try {
         return ((Number) getObject(r, c)).floatValue();
      }
      catch(Throwable ex) {
         return 0;
      }
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the long value in the specified row.
    */
   @Override
   public final long getLong(int r, int c) {
      try {
         return ((Number) getObject(r, c)).longValue();
      }
      catch(Throwable ex) {
         return 0;
      }
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the int value in the specified row.
    */
   @Override
   public final int getInt(int r, int c) {
      try {
         return ((Number) getObject(r, c)).intValue();
      }
      catch(Throwable ex) {
         return 0;
      }
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the short value in the specified row.
    */
   @Override
   public final short getShort(int r, int c) {
      try {
         return ((Number) getObject(r, c)).shortValue();
      }
      catch(Throwable ex) {
         return 0;
      }
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the byte value in the specified row.
    */
   @Override
   public final byte getByte(int r, int c) {
      try {
         return ((Number) getObject(r, c)).byteValue();
      }
      catch(Throwable ex) {
         return 0;
      }
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the boolean value in the specified row.
    */
   @Override
   public final boolean getBoolean(int r, int c) {
      try {
         return (Boolean) getObject(r, c);
      }
      catch(Throwable ex) {
         return false;
      }
   }

   /**
    * Set the object.
    * @param r the specified row.
    * @param c the specified column.
    * @param obj the specified object.
    */
   @Override
   public void setObject(int r, int c, Object obj) {
      if(getRowCount() < 0) {
         throw new RuntimeException(tableNotReady);
      }

      Object[] row = new Object[types.length];

      for(int i = 0; i < types.length; i++) {
         row[i] = xtable.getObject(r, i);

         if(r >= getHeaderRowCount()) {
            Object obj2 = Tool.getData(types[c], obj);

            if(obj != null && obj2 == null) {
               return;
            }

            obj = obj2;
         }

         if(c == i) {
            row[i] = obj;
         }
      }

      replaceRow(r, row);
   }

   public void setHeaders(String[] headers) {
      replaceRow(0, headers);
   }

   private void replaceRow(int r, Object[] row) {
      XSwappableTable nDataTable = createTable();
      copyTable(nDataTable, 0, r);

      nDataTable.addRow(row);

      copyTable(nDataTable, r + 1, xtable.getRowCount() - r - 1);
      nDataTable.complete();
      swapTable(nDataTable);
      this.ts = System.currentTimeMillis();
   }

   /**
    * Get the current column content type.
    * @param col column number.
    * @return column type.
    */
   @Override
   public Class getColType(int col) {
      return col < types.length ? Tool.getDataClass(types[col]) : String.class;
   }

   /**
    * Check if is primitive.
    * @return <tt>true</tt> if is primitive, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isPrimitive(int col) {
      return false;
   }

   /**
    * Insert a row.
    * @param row the specified row index.
    */
   public void insertRow(int row) {
      if(getRowCount() < 0) {
         throw new RuntimeException(tableNotReady);
      }

      if(row > 0 && row <= getRowCount()) {
         XSwappableTable nDataTable = createTable();
         copyTable(nDataTable, 0, row);
         nDataTable.addRow(new Object[types.length]);
         copyTable(nDataTable, row, xtable.getRowCount() - row);
         nDataTable.complete();
         swapTable(nDataTable);
      }
   }

   /**
    * Insert a column.
    * @param col the specified column index.
    */
   public void insertCol(int col) {
      int rowCount = getRowCount();

      if(rowCount < 0) {
         throw new RuntimeException(tableNotReady);
      }

      if(col >= 0 && col <= getColCount()) {
         String[] nTypes = new String[types.length + 1];
         System.arraycopy(types, 0, nTypes, 0, col);
         nTypes[col] = XSchema.STRING;
         System.arraycopy(types, col, nTypes, col + 1, types.length - col);
         types = nTypes;

         XSwappableTable nDataTable = createTable();

         for(int r = 0; r < rowCount; r++) {
            Object[] row = new Object[types.length];

            for(int c = 0; c < col; c++) {
               row[c] = xtable.getObject(r, c);
            }

            row[col] = null;

            for(int c = col + 1; c < types.length; c++) {
               row[c] = xtable.getObject(r, c - 1);
            }

            nDataTable.addRow(row);
         }

         nDataTable.complete();
         swapTable(nDataTable);
      }
   }

   /**
    * Delete a row.
    * @param row the specified row.
    */
   public void deleteRow(int row) {
      if(getRowCount() < 0) {
         throw new RuntimeException(tableNotReady);
      }

      if(row > 0 && row < getRowCount()) {
         XSwappableTable nDataTable = createTable();
         copyTable(nDataTable, 0, row);
         copyTable(nDataTable, row + 1, getRowCount() - row - 1);
         nDataTable.complete();
         swapTable(nDataTable);
      }
   }

   /**
    * Delete a column.
    * @param col the specified column index.
    */
   public void deleteCol(int col) {
      if(getRowCount() < 0) {
         throw new RuntimeException(tableNotReady);
      }

      if(col >= 0 && col < getColCount()) {
         String[] nTypes = new String[types.length - 1];
         System.arraycopy(types, 0, nTypes, 0, col);
         System.arraycopy(types, col + 1, nTypes, col, types.length - col - 1);
         types = nTypes;
         identifiers.removeIdentifier(col);

         XSwappableTable nDataTable = createTable();
         int rowCount = xtable.getRowCount();
         int colCount = types.length;

         for(int r = 0; r < rowCount; r++) {
            Object[] row = new Object[colCount];

            for(int c = 0; c < col; c++) {
               row[c] = xtable.getObject(r, c);
            }

            for(int c = col; c < colCount; c++) {
               row[c] = xtable.getObject(r, c + 1);
            }

            nDataTable.addRow(row);
         }

         nDataTable.complete();
         swapTable(nDataTable);
      }
   }

   /**
    * Get the data type.
    * @param col the specified column.
    * @return the data type of the column.
    */
   public String getDataType(int col) {
      return types[col];
   }

   /**
    * Set the data type.
    * @param col the specified column.
    * @param type the specified data type;
    *
    */
   /**
    * Set the data type.
    * @param col     the specified column.
    * @param type    the specified data type;
    * @param format  the specified data format;
    * @param originalTable the orignal swaptable of the snapshot table.
    * @param force   if true, force to parse the data to target type and set the data to null
    *                when parse failed, else throw exception when parse failed.
    * @throws Exception
    */
   public synchronized void setDataType(int col, String type, Format format,
                                        XSwappableTable originalTable, boolean force)
      throws Exception
   {
      if(getRowCount() < 0) {
         throw new RuntimeException(tableNotReady);
      }

      types[col] = type;
      XSwappableTable nDataTable = createTable();
      copyTable(nDataTable, 0, getHeaderRowCount());
      // get data from the original table if type changed or has been set back to its original type
      boolean typeChanged = !Tool.equals(type, Tool.getDataType(xtable.getColType(col))) ||
         (originalTable != null &&
            Tool.equals(type, Tool.getDataType(originalTable.getColType(col))));

      // cache the converted data
      final int maxCacheSize = 10000;
      Map<Object, Object> convertMap = new LinkedHashMap<Object, Object>() {
         @Override
         protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
            return size() > maxCacheSize;
         }
      };

      for(int i = getHeaderRowCount(); xtable.moreRows(i); i++) {
         Object[] arr = new Object[types.length];

         for(int j = 0; j < types.length; j++) {
            // get the original data instead of the data which
            // maybe resetted after setting data type(52298).
            if(typeChanged && j == col && originalTable != null &&
               i < originalTable.getRowCount() && j < originalTable.getColCount())
            {
               arr[j] = originalTable.getObject(i, j);
            }
            else {
               arr[j] = xtable.getObject(i, j);
            }
         }

         Object oval = arr[col] ;

         if(convertMap.containsKey(oval)) {
            arr[col] = convertMap.get(oval);
         }
         else {
            try {
               arr[col] = Tool.transform(oval, type, format, true);
               arr[col] = Tool.getData(type, arr[col]);
            }
            catch(Exception ex) {
               if(force || XSchema.isNumericType(type) && Util.isNotApplicableValue(arr[col])) {
                  arr[col] = null;
               }
               else {
                  throw ex;
               }
            }

            convertMap.put(oval, arr[col]);
         }

         nDataTable.addRow(arr);
      }

      nDataTable.complete();
      swapTable(nDataTable);
   }

   /**
    * Set the data type.
    * @param changeCols     the change type columns.
    * @param changeTypes    the change data type of the change columns;
    * @param changeFormats  the change data format of the change columns;
    * @param originalTable the orignal swaptable of the snapshot table.
    * @param changeForces   if true, force to parse the data to target type and set the data to null
    *                when parse failed, else throw exception when parse failed.
    * @throws Exception
    */
   public synchronized void setDataTypes(List<Integer> changeCols, List<String> changeTypes, List<Format> changeFormats,
                                        XSwappableTable originalTable, List<Boolean> changeForces)
      throws Exception
   {
      if(getRowCount() < 0) {
         throw new RuntimeException(tableNotReady);
      }

      for(int i = 0; i < changeCols.size(); i++) {
         types[changeCols.get(i)] = changeTypes.get(i);
      }

      XSwappableTable nDataTable = createTable();
      copyTable(nDataTable, 0, getHeaderRowCount());
      // get data from the original table if type changed or has been set back to its original type
      boolean[] typesChanged = new boolean[types.length];

      for(int i = 0; i < typesChanged.length; i++) {
         typesChanged[i] = changeCols.contains(i) && (!Tool.equals(types[i], Tool.getDataType(xtable.getColType(i))) ||
            (originalTable != null && Tool.equals(types[i], Tool.getDataType(originalTable.getColType(i)))));
      }

      // cache the converted data
      final int maxCacheSize = 10000 / changeCols.size();
      Map<Integer, Map<Object, Object>> convertMaps = new HashMap<>();

      for(Integer changeCol : changeCols) {
         convertMaps.put(changeCol, new LinkedHashMap<Object, Object>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
               return size() > maxCacheSize;
            }
         });
      }

      for(int i = getHeaderRowCount(); xtable.moreRows(i); i++) {
         Object[] arr = new Object[types.length];

         for(int j = 0; j < types.length; j++) {
            // get the original data instead of the data which
            // maybe resetted after setting data type(52298).
            if(typesChanged[j] && originalTable != null &&
               i < originalTable.getRowCount() && j < originalTable.getColCount())
            {
               arr[j] = originalTable.getObject(i, j);
            }
            else {
               arr[j] = xtable.getObject(i, j);
            }

            if(changeCols.contains(j)) {
               Object oval = arr[j] ;

               if(convertMaps.get(j).containsKey(oval)) {
                  arr[j] = convertMaps.get(j).get(oval);
               }
               else {
                  try {
                     arr[j] = Tool.transform(oval, types[j], changeFormats.get(changeCols.indexOf(j)), true);
                     arr[j] = Tool.getData(types[j], arr[j]);
                  }
                  catch(Exception ex) {
                     if(changeForces.get(changeCols.indexOf(j)) ||
                        XSchema.isNumericType(types[j]) && Util.isNotApplicableValue(arr[j]))
                     {
                        arr[j] = null;
                     }
                     else {
                        throw ex;
                     }
                  }

                  convertMaps.get(j).put(oval, arr[j]);
               }
            }
         }

         nDataTable.addRow(arr);
      }

      nDataTable.complete();
      swapTable(nDataTable);
   }

   /**
    * Get the column identifier of a column.
    * @param col the specified column index.
    * @return the column indentifier of the column. The identifier might be
    * different from the column name, for it may contain more locating
    * information than the column name.
    */
   @Override
   public String getColumnIdentifier(int col) {
      return identifiers.getColumnIdentifier(col);
   }

   /**
    * Set the column identifier of a column.
    * @param col the specified column index.
    * @param identifier the column indentifier of the column. The identifier
    * might be different from the column name, for it may contain more
    * locating information than the column name.
    */
   @Override
   public void setColumnIdentifier(int col, String identifier) {
      identifiers.setColumnIdentifier(col, identifier);
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<xEmbeddedTable");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.println("</xEmbeddedTable>");
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      parseAttributes(elem);
      parseContents(elem);
   }

   /**
    * Reset for write data by piece.
    */
   public void reset() {
      start = 0;
   }

   /**
    * Check has more piece blocks.
    */
   public boolean hasNextBlock() {
      if(getRowCount() < 0) {
         throw new RuntimeException(tableNotReady);
      }

      return start < xtable.getRowCount();
   }

   /**
    * Write data to a DataOutputStream.
    * @param dos the destination DataOutputStream.
    */
   @Override
   public void writeData(DataOutputStream dos) {
      writeData(dos, false);
   }

   /**
    * Write data block to a DataOutputStream, if piece, please make sure reset
    * function is called before write data:
    * 1: reset()
    * 2: moreBlock()?
    * 3: writeData
    * @param dos the destination DataOutputStream.
    * @param piece write data to piece block.
    */
   public void writeData(DataOutputStream dos, boolean piece) {
      if(getRowCount() < 0) {
         throw new RuntimeException(tableNotReady);
      }

      if(piece) {
         writePieceData(dos);
      }
      else {
         writeFullData(dos);
      }
   }

   private void writeFullData(DataOutputStream dos) {
      try {
         dos.writeInt(xtable.getRowCount());
         dos.writeInt(types.length);

         for(String type : types) {
            dos.writeUTF(type);
         }

         dos.writeBoolean(getLinkURI() == null);
         int rcnt = xtable.getRowCount();

         for(int i = 0; i < rcnt; i++) {
            for(int j = 0; j < types.length; j++) {
               Tool.writeUTF(dos, getFixedDataString(xtable.getObject(i, j)));

               if(getLinkURI() == null) {
                  continue;
               }

               Hyperlink.Ref[] refs = links == null ? null :
                  (Hyperlink.Ref[]) links[i][j];
               int len = refs == null ? 0 : refs.length;
               dos.writeInt(len);

               if(len == 0) {
                  continue;
               }

               for(int k = 0; k < len; k++) {
                  Hyperlink.Ref ref = refs[k];
                  dos.writeUTF(ref.getName());
                  String cmd = XUtil.getCommand(ref, getLinkURI());
                  dos.writeUTF(cmd);
                  String tooltip = ref.getToolTip();
                  dos.writeBoolean(tooltip == null);

                  if(tooltip != null) {
                     dos.writeUTF(tooltip);
                  }
               }
            }
         }
      }
      catch(Exception e) {
         // ignore it
      }
   }

   private void writePieceData(DataOutputStream dos) {
      int rcnt = Math.min(xtable.getRowCount() - start, MAX_COUNT);

      try {
         dos.writeBoolean(start == 0);
         dos.writeInt(rcnt);
         dos.writeInt(types.length);

         if(start == 0) {
            for(String type : types) {
               dos.writeUTF(type);
            }
         }

         dos.writeBoolean(getLinkURI() == null);

         for(int i = 0; i < rcnt; i++) {
            for(int j = 0; j < types.length; j++) {
               Tool.writeUTF(dos, getFixedDataString(
                               xtable.getObject(start + i, j)));

               if(getLinkURI() == null) {
                  continue;
               }

               Hyperlink.Ref[] refs = links == null ? null :
                  (Hyperlink.Ref[]) links[start + i][j];
               int len = refs == null ? 0 : refs.length;
               dos.writeInt(len);

               if(len == 0) {
                  continue;
               }

               for(int k = 0; k < len; k++) {
                  Hyperlink.Ref ref = refs[k];
                  dos.writeUTF(ref.getName());
                  String cmd = XUtil.getCommand(ref, getLinkURI());
                  dos.writeUTF(cmd);
                  String tooltip = ref.getToolTip();
                  dos.writeBoolean(tooltip == null);

                  if(tooltip != null) {
                     dos.writeUTF(tooltip);
                  }
               }
            }
         }
      }
      catch(Exception e) {
         // ignore it
      }

      start = start + rcnt;
   }

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @return <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean parseData(DataInputStream input) {
      return parseData(input, false, true);
   }

   /**
    * @param piece true if parsing fragments of a table.
    * @param last true if this is the last fragment.
    */
   public boolean parseData(DataInputStream input, boolean piece, boolean last) {
      if(piece) {
         return parsePieceData(input, last);
      }
      else {
         return parseFullData(input);
      }
   }

   private boolean parseFullData(DataInputStream input) {
      try {
         int row = input.readInt();
         int col = input.readInt();
         types = new String[col];
         xtable = createTable();

         for(int i = 0; i < col; i++) {
            types[i] = input.readUTF();
         }

         boolean noLinks = input.readBoolean();

         for(int i = 0; i < row; i++) {
            Object[] arr = new Object[col];

            for(int j = 0; j < col; j++) {
               String val = Tool.readUTF(input);
               arr[j] = (i < getHeaderRowCount()) ? val : getPersistentData(types[j], val);

               if(noLinks) {
                  continue;
               }

               int len = input.readInt();

               // continue if no links
               if(len == 0) {
                  continue;
               }

               // links are not kept permanently in xml, since this is only
               // called to parse it back from xml, we throw out the link
               // information
               for(int k = 0; k < len; k++) {
                  input.readUTF();
                  input.readUTF();

                  if(!input.readBoolean()) {
                     input.readUTF();
                  }
               }
            }

            xtable.addRow(arr);
         }

      }
      catch(Exception ex) {
         LOG.error("Failed to parse embedded table data", ex);
      }
      finally {
         xtable.complete();
      }

      return true;
   }

   private boolean parsePieceData(DataInputStream input, boolean last) {
      try {
         boolean begin = input.readBoolean();
         int row = input.readInt();
         int col = input.readInt();

         if(begin) {
            types = new String[col];
            xtable = createTable();

            for(int i = 0; i < col; i++) {
               types[i] = input.readUTF();
            }
         }

         boolean noLinks = input.readBoolean();

         for(int i = 0; i < row; i++) {
            Object[] arr = new Object[col];

            for(int j = 0; j < col; j++) {
               String val = Tool.readUTF(input);
               arr[j] = (xtable.getRowCount() == 0 || begin && i < getHeaderRowCount()) ?
                  val : getPersistentData(types[j], val);

               if(noLinks) {
                  continue;
               }

               int len = input.readInt();

               // continue if no links
               if(len == 0) {
                  continue;
               }

               // links are not kept permanently in xml, since this is only
               // called to parse it back from xml, we throw out the link
               // information
               for(int k = 0; k < len; k++) {
                  input.readUTF();
                  input.readUTF();

                  if(!input.readBoolean()) {
                     input.readUTF();
                  }
               }
            }

            xtable.addRow(arr);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to parse embedded table block", ex);
      }
      finally {
         if(last) {
            xtable.complete();
         }
      }

      return true;
   }

   /**
    * Set the uri.
    * @param uri the specified service request uri.
    */
   public void setLinkURI(String uri) {
      this.luri = uri;
   }

   /**
    * Get the specified service request uri.
    * @return the uri.
    */
   public String getLinkURI() {
      return luri;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   private void writeAttributes(PrintWriter writer) {
      if(getRowCount() < 0) {
         throw new RuntimeException(tableNotReady);
      }

      writer.print(" row=\"" + xtable.getRowCount() +
                   "\" col=\"" + types.length + "\"" +
                   "\" strictNull=\"true\"");
   }

   /**
    * Parse the attributes.
    * @param elem the specified xml node.
    */
   @SuppressWarnings("UnusedParameters")
   private void parseAttributes(Element elem) throws Exception {
      strictNull = "true".equalsIgnoreCase(Tool.getAttribute(elem, "strictNull"));
   }

   /**
    * write contents.
    * @param writer the destination print writer.
    */
   protected void writeContents(PrintWriter writer) {
      if(getRowCount() < 0) {
         throw new RuntimeException(tableNotReady);
      }

      writer.println("<types>");

      for(String type : types) {
         writer.print("<type>");
         writer.print("<![CDATA[" + type + "]]>");
         writer.println("</type>");
      }

      writer.println("</types>");
      writer.println("<values>");
      int rcount = xtable.getRowCount();

      for(int i = 0; i < rcount; i++) {
         writer.println("<row>");

         for(int j = 0; j < types.length; j++) {
            String str = getFixedDataString(xtable.getObject(i, j));

            writer.print("<value>");
            writer.print("<![CDATA[" + Tool.byteEncode2(str) + "]]>");
            writer.print("</value>");

            if(getLinkURI() == null) {
               continue;
            }

            Hyperlink.Ref[] refs = links == null ? null :
               (Hyperlink.Ref[]) links[i][j];
            int len = refs == null ? 0 : refs.length;
            writer.println("<cellLink>");

            for(int k = 0; k < len; k++) {
               Hyperlink.Ref ref = refs[k];
               writer.println("<hyperlink>");
               writer.println("<name>");
               writer.println("<![CDATA[" + Tool.byteEncode2(ref.getName()) +
                  "]]>");
               writer.println("</name>");
               String cmd = XUtil.getCommand(ref, getLinkURI());
               writer.println("<value>");
               writer.println("<![CDATA[" + Tool.byteEncode2(cmd) + "]]>");
               writer.println("</value>");
               String tooltip = ref.getToolTip();

               if(tooltip != null) {
                  writer.print("<tooltip>");
                  writer.println("<![CDATA[" +
                     Tool.byteEncode2(tooltip) + "]]>");
                  writer.print("</tooltip>");
               }

               writer.println("</hyperlink>");
            }

            writer.println("</cellLink>");
         }

         writer.println("</row>");
      }

      writer.println("</values>");
   }

   /**
    * Fix string which replace NUL by SP.
    */
   private String getFixedDataString(Object obj) {
      String str = Tool.getPersistentDataString(obj);
      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < str.length(); i++) {
         char ch = str.charAt(i);
         sb.append(ch == '\0' ? (char) 32 : ch);
      }

      return sb.toString();
   }

   /**
    * write contents.
    * @param dos the destination OutputStream.
    */
   protected void writeContents2(DataOutputStream dos) {
      if(getRowCount() < 0) {
         throw new RuntimeException(tableNotReady);
      }

      try {
         for(String type : types) {
            dos.writeUTF(type);
         }

         dos.writeBoolean(getLinkURI() == null);
         int rcnt = xtable.getRowCount();

         for(int i = 0; i < rcnt; i++) {
            for(int j = 0; j < types.length; j++) {
               Tool.writeUTF(dos, getFixedDataString(xtable.getObject(i, j)));

               if(getLinkURI() == null) {
                  continue;
               }

               Hyperlink.Ref[] refs = links == null ? null :
                  (Hyperlink.Ref[]) links[i][j];
               int len = refs == null ? 0 : refs.length;
               dos.writeInt(len);

               if(len == 0) {
                  continue;
               }

               for(int k = 0; k < len; k++) {
                  Hyperlink.Ref ref = refs[k];
                  dos.writeUTF(ref.getName());
                  String cmd = XUtil.getCommand(ref, getLinkURI());
                  dos.writeUTF(cmd);
                  String tooltip = ref.getToolTip();
                  dos.writeBoolean(tooltip == null);

                  if(tooltip != null) {
                     dos.writeUTF(tooltip);
                  }
               }
            }
         }
      }
      catch(IOException ignore) {
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      int row = Integer.parseInt(Tool.getAttribute(elem, "row"));
      int col = Integer.parseInt(Tool.getAttribute(elem, "col"));
      types = new String[col];
      Element tsnode = Tool.getChildNodeByTagName(elem, "types");
      NodeList tnodes = Tool.getChildNodesByTagName(tsnode, "type");

      if(tnodes.getLength() != col) {
         throw new Exception("invalid types node found: " + tsnode);
      }

      for(int i = 0; i < tnodes.getLength(); i++) {
         Element tnode = (Element) tnodes.item(i);
         types[i] = Tool.getValue(tnode);
      }

      Element vsnode = Tool.getChildNodeByTagName(elem, "values");
      NodeList vnodes = Tool.getChildNodesByTagName(vsnode, "row");

      if(vnodes.getLength() != row) {
         throw new Exception("invalid values node found: " + vsnode);
      }

      xtable = createTable();

      try {
         for(int i = 0; i < vnodes.getLength(); i++) {
            Element svnode = (Element) vnodes.item(i);
            Object[] arr = new Object[col];
            // optimization, iterate through child node instead of getChildNodes
            NodeList clist = svnode.getChildNodes();
            int len = clist.getLength();

            for(int c = 0, j = 0; j < len; j++) {
               Node cnode = clist.item(j);

               if(!(cnode instanceof Element)) {
                  continue;
               }

               Element vnode = (Element) cnode;

               if(vnode.getTagName().equals("value")) {
                  String val = Tool.byteDecode(Tool.getValue(vnode));
                  Object obj = i < getHeaderRowCount() ?
                     val : getPersistentData(types[c], val);

                  arr[c++] = obj;
               }
            }

            xtable.addRow(arr);
         }
      }
      finally {
         xtable.complete();
      }
   }

   public Object getPersistentData(String type, String val) {
      return strictNull ? Tool.getPersistentData(type, val) : Tool.getData(type, val);
   }

   @Override
   public void dispose() {
      // don't dispose xtable since it may be referenced outside
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public XEmbeddedTable clone() {
      synchronized(this) {
         try {
            XEmbeddedTable table = (XEmbeddedTable) super.clone();
            table.types = types.clone();
            table.identifiers = (XIdentifierContainer) identifiers.clone();
            table.identifiers.setTable(table);
            return table;
         }
         catch(Exception ex) {
            LOG.error("Failed to clone XEmbeddedTable", ex);
            return null;
         }
      }
   }

   /**
    * Get the hash code value.
    * @return the hash code value.
    */
   public int hashCode() {
      int hash = 0;
      hash = hash ^ getColCount();
      hash = hash ^ getRowCount();

      for(String type : types) {
         hash = hash ^ type.hashCode();
      }

      return hash;
   }

   @Override
   public String toString() {
      return "XEmbeddedTable@" + super.hashCode();
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   public boolean printKey(PrintWriter writer) throws Exception {
      int ccnt = getColCount();
      int rcnt = getRowCount();
      writer.print("EMB[");
      writer.print(ccnt);
      writer.print(",");
      writer.print(rcnt);

      for(String type : types) {
         writer.print(",");
         writer.print(type);
      }

      int head = Math.min(50, rcnt);

      for(int i = 0; i < head; i++) {
         for(int j = 0; j < ccnt; j++) {
            Object obj = xtable.getObject(i, j);
            writer.print(",");
            writer.print(obj);
         }
      }

      // for embedded table with row count > 50, the remaining rows will
      // be used partially. In this way, we want to avoid too fat key
      /* this is random and kind of meaningless, use timestamp for modification instead
      if(rcnt > head) {
         int delta = Math.max(1, (rcnt - head) / 30);

         for(int i = head; i < rcnt; i += delta) {
            for(int j = 0; j < ccnt; j++) {
               Object obj = xtable.getObject(i, j);
               writer.print(",");
               writer.print(obj);
            }
         }
      }
      */
      writer.print(",");
      writer.print(ts);

      writer.print("]");
      return true;
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals the object, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(obj == this) {
         return true;
      }

      if(!(obj instanceof XEmbeddedTable)) {
         return false;
      }

      XEmbeddedTable table = (XEmbeddedTable) obj;

      if(table.getColCount() != getColCount()) {
         return false;
      }

      if(table.getRowCount() != getRowCount()) {
         return false;
      }

      for(int i = 0; i < types.length; i++) {
         if(!Tool.equals(types[i], table.types[i])) {
            return false;
         }
      }

      int len = xtable.getRowCount();

      for(int i = 0; i < len; i++) {
         for(int j = 0; j < types.length; j++) {
            Object obj1 = xtable.getObject(i, j);
            Object obj2 = table.xtable.getObject(i, j);

            if(!Tool.equals(obj1, obj2)) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Set the meta info to one column.
    */
   public void setXMetaInfo(String header, XMetaInfo info) {
      if(mmap != null) {
         mmap.put(header, info);
      }
   }

   /**
    * Get the meta info of one column.
    */
   public XMetaInfo getXMetaInfo(String header) {
      return mmap == null ? null : mmap.get(header);
   }

   /**
    * Add state change listener.
    * Any data change will remove data file in sree home for large table.
    */
   public void addDataChangeListener(ActionListener listener) {
      this.listener = listener;
   }

   /**
    * Get state change listener.
    * Any data change will remove data file in sree home for large table.
    */
   public ActionListener getDataChangeListener() {
      return listener;
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      return new DefaultTableDataDescriptor(this) {
         @Override
         public final XMetaInfo getXMetaInfo(TableDataPath path) {
            if(mmap != null) {
               if(!path.isCell()) {
                  return null;
               }

               String header = path.getPath()[0];
               return mmap.get(header);
            }

            return null;
         }

         @Override
         public boolean containsFormat() {
            return mmap == null ? false : mmap.size() > 0;
         }

         @Override
         public boolean containsDrill() {
            return mmap == null ? false : mmap.size() > 0;
         }
      };
   }

   public final void complete() {
      rowLock.lock();

      try {
         if(completed) {
            return;
         }

         if(xtable != null) {
            xtable.complete();
         }

         completed = true;
         rowCondition.signalAll();
      }
      finally {
         rowLock.unlock();
      }
   }

   /**
    * Get the inner table that holds the data.
    * @hidden
    */
   public XSwappableTable getDataTable() {
      return this.xtable;
   }


   public boolean isStrictNull() {
      return strictNull;
   }

   /**
    * @param strictNull if true, use strict null rule to parse the data, else not,
    *                   this is used to handle bc issue.
    */
   public void setStrictNull(boolean strictNull) {
      this.strictNull = strictNull;
   }

   private static final int MAX_COUNT = 1000; // 1000 rows one block
   private static final String tableNotReady =
      "Embedded table is not completely loaded!";

   private Lock rowLock = new ReentrantLock();
   private Condition rowCondition = rowLock.newCondition();

   private boolean completed = false;
   private boolean strictNull = true; // for bc
   private String[] types;
   private XSwappableTable xtable;
   private Object[][] links;
   private XIdentifierContainer identifiers = null;
   private transient String luri;
   private transient int start = 0;
   private transient Map<String, XMetaInfo> mmap = new HashMap<>();
   private transient ActionListener listener;
   private transient long ts = 0;

   private static final Logger LOG = LoggerFactory.getLogger(XEmbeddedTable.class);
}
