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
package inetsoft.report.filter;

import inetsoft.report.*;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.BinaryTableDataPath;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.internal.DateComparisonUtil;
import inetsoft.util.Tool;

import java.awt.*;
import java.io.*;
import java.util.*;

/**
 * This filter performs remapping of column numbers. The columns can be
 * reordered by specifying the new order in the column map. It can also
 * be used to extract a subset of columns from a table by using a partial
 * column number map.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public class ColumnMapFilter extends AbstractTableLens
   implements TableFilter, SortedTable, CancellableTableLens
{
   /**
    * The map[i] points to the column number in the base table. The columns
    * are reordered according to the mapping.
    */
   public ColumnMapFilter(TableLens table, int[] map) {
      this.map = map;
      setTable(table);
   }

   /**
    * Get the original table of this filter.
    */
   @Override
   public TableLens getTable() {
      return table;
   }

   /**
    * Set the base table of this filter.
    */
   @Override
   public void setTable(TableLens table) {
      this.table = table;
      invalidate();
      this.table.addChangeListener(new DefaultTableChangeListener(this));
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(descriptor == null) {
         descriptor = new ColumnMapFilterDataDescriptor(table.getDescriptor());
      }

      return descriptor;
   }

   /**
    * Get the base table row index corresponding to the filtered table.
    * If the row does not exist in the base table, it returns -1.
    * @param row row index in the filtered table.
    * @return corresponding row index in the base table.
    */
   @Override
   public int getBaseRowIndex(int row) {
      return row;
   }

   /**
    * Get the base table column index corresponding to the filtered table.
    * If the column does not exist in the base table, it returns -1.
    * @param col column index in  the filtered table.
    * @return corresponding column index in the bast table.
    */
   @Override
   public int getBaseColIndex(int col) {
      return map[col];
   }

   /**
    * Invalidate the table filter forcely, and the table filter will perform
    * filtering calculation to validate itself.
    */
   @Override
   public void invalidate() {
      descriptor = null;
      init();
      fireChangeEvent();
   }

   /**
    * Initialize any cached data.
    */
   private void init() {
      this.duptimes = new int[map.length];

      int[] cnts = new int[table.getColCount()];
      boolean dup = false;

      for(int i = 0; i < map.length; i++) {
         duptimes[i] = cnts[map[i]];
         cnts[map[i]]++;

         dup = dup || duptimes[i] > 0;
      }

      // @by larryl, if no duplicate header, skip the checking later
      if(!dup) {
         duptimes = null;
      }

      // optimization
      headerRows = (short) table.getHeaderRowCount();
      headers = null;
      resetIdentifiers();
   }

   /**
    * Get the columns that the table is sorted on.
    * @return sort columns.
    */
   @Override
   public int[] getSortCols() {
      if(table instanceof SortedTable) {
         int[] sc = ((SortedTable) table).getSortCols().clone();

         for(int i = 0; i < sc.length; i++) {
            for(int j = 0; j < map.length; j++) {
               if(map[j] == sc[i]) {
                  sc[i] = j;
                  break;
               }
            }
         }

         return sc;
      }

      return new int[0];
   }

   /**
    * Get the sorting order of the sorting columns.
    */
   @Override
   public boolean[] getOrders() {
      if(table instanceof SortedTable) {
         return ((SortedTable) table).getOrders();
      }

      return null;
   }

   /**
    * Set the comparer for a sorting column.
    * @param col table column index.
    * @param comp comparer.
    */
   @Override
   public void setComparer(int col, Comparer comp) {
      if(table instanceof SortedTable) {
         ((SortedTable) table).setComparer(map[col], comp);
      }
   }

   /**
    * Get the comparer for a sorting column.
    * @param col the specified table column index.
    */
   @Override
   public Comparer getComparer(int col) {
      if(table instanceof SortedTable) {
         return ((SortedTable) table).getComparer(map[col]);
      }
      else {
         return null;
      }
   }

   /**
    * Reverse column mapping lookup.
    */
   int originToMap(int col) {
      int result = -1;

      for(int i = 0; i < map.length; i++) {
         if(col == map[i]) {
            result = i;
            break;
         }
      }

      return result;
   }

   /**
    * Check if there are more rows. The row index is the row that will be
    * accessed. This method must block until the row is available, or
    * return false if the row does not exist in the table. This method is
    * used to iterate through the table, and allow partial table to be
    * accessed in report processing.
    * @param row row number.
    * @return true if the row exists, or false if no more rows.
    */
   @Override
   public boolean moreRows(int row) {
      return table.moreRows(row);
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      return table.getRowCount();
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public int getColCount() {
      return map.length;
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.
    */
   @Override
   public int getHeaderRowCount() {
      return headerRows; // optimization
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      return table.getHeaderColCount();
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as tail rows.
    * @return number of header rows.
    */
   @Override
   public int getTrailerRowCount() {
      return table.getTrailerRowCount();
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as tail columns.
    */
   @Override
   public int getTrailerColCount() {
      return table.getTrailerColCount();
   }

   /**
    * Get the current row heights setting. The meaning of row heights
    * depends on the table layout policy setting. If the row height
    * is to be calculated by the ReportSheet based on the content,
    * return -1.
    * @return row height.
    */
   @Override
   public int getRowHeight(int row) {
      return table.getRowHeight(row);
   }

   /**
    * Get the current column width setting. The meaning of column widths
    * depends on the table layout policy setting. If the column width
    * is to be calculated by the ReportSheet based on the content,
    * return -1. A special value, StyleConstants.REMAINDER, can be returned
    * by this method to indicate that width of this column should be
    * calculated based on the remaining space after all other columns'
    * widths are satisfied. If there are more than one column that return
    * REMAINDER as their widths, the remaining space is distributed
    * evenly among these columns.
    * @return column width.
    */
   @Override
   public int getColWidth(int col) {
      if(col >= 0 && col < getColCount()) {
         return table.getColWidth(map[col]);
      }
      else {
         return -1;
      }
   }

   /**
    * Get the current column content type.
    * @param col column number.
    * @return column type.
    */
   @Override
   public Class getColType(int col) {
      return table.getColType(map[col]);
   }

   /**
    * Check if is primitive.
    * @return <tt>true</tt> if is primitive, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isPrimitive(int col) {
      return table.isPrimitive(map[col]);
   }

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getRowBorderColor(int r, int c) {
      return table.getRowBorderColor(r, (c < 0 || c >= map.length) ? c : map[c]);
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      return table.getColBorderColor(r, (c < 0 || c >= map.length) ? c : map[c]);
   }

   /**
    * Return the style for bottom border of the specified cell. The flag
    * must be one of the style options defined in the StyleConstants
    * class. If the row number is -1, it's checking the outside ruling
    * on the top.
    * @param r row number.
    * @param c column number.
    * @return ruling flag.
    */
   @Override
   public int getRowBorder(int r, int c) {
      return table.getRowBorder(r, (c < 0 || c >= map.length) ? c : map[c]);
   }

   /**
    * Return the style for right border of the specified row. The flag
    * must be one of the style options defined in the StyleConstants
    * class. If the column number is -1, it's checking the outside ruling
    * on the left.
    * @param r row number.
    * @param c column number.
    * @return ruling flag.
    */
   @Override
   public int getColBorder(int r, int c) {
      return table.getColBorder(r, (c < 0 || c >= map.length) ? c : map[c]);
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      return table.getInsets(r, map[c]);
   }

   /**
    * Return the spanning setting for the cell. If the specified cell
    * is not a spanning cell, it returns null. Otherwise it returns
    * a Dimension object with Dimension.width equals to the number
    * of columns and Dimension.height equals to the number of rows
    * of the spanning cell.
    * @param r row number.
    * @param c column number.
    * @return span cell dimension.
    */
   @Override
   public Dimension getSpan(int r, int c) {
      return table.getSpan(r, map[c]);
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      return table.getAlignment(r, map[c]);
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      return table.getFont(r, map[c]);
   }

   /**
    * Return the per cell line wrap mode. If the line wrap mode is true,
    * lines are wrapped when the text can not fit on one line. Otherwise
    * the wrapping is never done and any overflow text will be truncated.
    * @param r row number.
    * @param c column number.
    * @return true if line wrapping should be done.
    */
   @Override
   public boolean isLineWrap(int r, int c) {
      return table.isLineWrap(r, map[c]);
   }

   /**
    * Return the per cell foreground color. Return null to use default
    * color.
    * @param r row number.
    * @param c column number.
    * @return foreground color for the specified cell.
    */
   @Override
   public Color getForeground(int r, int c) {
      return table.getForeground(r, map[c]);
   }

   /**
    * Return the per cell background color. Return null to use default
    * color.
    * @param r row number.
    * @param c column number.
    * @return background color for the specified cell.
    */
   @Override
   public Color getBackground(int r, int c) {
      return table.getBackground(r, map[c]);
   }

   @Override
   public int getAlpha(int r, int c) {
      return table.getAlpha(r, map[c]);
   }

   /**
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isNull(int r, int c) {
      if(r == 0 && headers != null && headers[c] != null) {
         return false;
      }

      return table.isNull(r, map[c]);
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      if(r == 0 && headers != null && headers[c] != null) {
         return headers[c];
      }

      // @by: ChrisSpagnoli bug1425024993426 2015-3-23
      if(c >= map.length || c < 0) {
         return null;
      }

      Object val = table.getObject(r, map[c]);

      if(r < table.getHeaderRowCount() && (val == null || "".equals(val))) {
         val = XUtil.getHeader(table, map[c]);
      }

      // avoid duplicate header (regardless of number of header rows, only the
      // top row must be unique).
      if(duptimes != null && r < headerRows && r == 0) {
         return Util.getDupHeader(val, duptimes[c]);
      }

      return val;
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the double value in the specified row.
    */
   @Override
   public final double getDouble(int r, int c) {
      return table.getDouble(r, map[c]);
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the float value in the specified row.
    */
   @Override
   public final float getFloat(int r, int c) {
      return table.getFloat(r, map[c]);
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the long value in the specified row.
    */
   @Override
   public final long getLong(int r, int c) {
      return table.getLong(r, map[c]);
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the int value in the specified row.
    */
   @Override
   public final int getInt(int r, int c) {
      return table.getInt(r, map[c]);
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the short value in the specified row.
    */
   @Override
   public final short getShort(int r, int c) {
      return table.getShort(r, map[c]);
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the byte value in the specified row.
    */
   @Override
   public final byte getByte(int r, int c) {
      return table.getByte(r, map[c]);
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the boolean value in the specified row.
    */
   @Override
   public final boolean getBoolean(int r, int c) {
      return table.getBoolean(r, map[c]);
   }

   /**
    * Set the cell value.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      if(r == 0) {
         if(headers == null) {
            headers = new Object[map.length];
         }

         headers[c] = v;
         return;
      }

      table.setObject(r, map[c], v);
   }

   /**
    * Dispose the table to clear up temporary resources.
    */
   @Override
   public void dispose() {
      table.dispose();
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
      if(identifiers == null) {
         resetIdentifiers();
      }

      return identifiers[col];
   }

   /**
    * Reset column identifiers.
    */
   private void resetIdentifiers() {
      identifiers = new String[getColCount()];

      for(int i = 0; i < identifiers.length; i++) {
         identifiers[i] = getColumnIdentifier0(i);
      }
   }

   /**
    * Get the column identifier of a column.
    * @param col the specified column index.
    * @return the column indentifier of the column. The identifier might be
    * different from the column name, for it may contain more locating
    * information than the column name.
    */
   private String getColumnIdentifier0(int col) {
      String identifier = super.getColumnIdentifier(col);
      return identifier == null ? table.getColumnIdentifier(getBaseColIndex(col)) : identifier;
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
      super.setColumnIdentifier(col, identifier);
      resetIdentifiers();
   }

   /**
    * Cancel the table lens and running queries if supported.
    */
   @Override
   public void cancel() {
      if(table instanceof CancellableTableLens) {
         ((CancellableTableLens) table).cancel();
      }
   }

   /**
    * Check the TableLens to see if it is cancelled.
    */
   @Override
   public boolean isCancelled() {
      return table instanceof CancellableTableLens &&
         ((CancellableTableLens) table).isCancelled();
   }

   /**
    * @return the report/vs name which this filter was created for,
    * and will be used when insert audit record.
    */
   @Override
   public String getReportName() {
      String name = super.getReportName();
      return name != null ? name : table != null ? table.getReportName() : null;
   }

   /**
    * @return the report type which this filter was created for:
    * ExecutionBreakDownRecord.OBJECT_TYPE_REPORT or
    * ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET
    */
   @Override
   public String getReportType() {
      String type = super.getReportType();
      return type != null ? type : table != null ? table.getReportType() : null;
   }

   @Serial
   private void readObject(ObjectInputStream in) throws ClassNotFoundException, IOException {
      in.defaultReadObject();
      setTable(table);
   }

   /**
    * ColumnMapFilter data descriptor.
    */
   private class ColumnMapFilterDataDescriptor implements TableDataDescriptor {
      /**
       * Create a ColumnMapFilterDataDescriptor.
       * @param descriptor the base descriptor
       */
      public ColumnMapFilterDataDescriptor(TableDataDescriptor descriptor) {
         this.descriptor = descriptor;
      }

      /**
       * Get table data path of a specified table column.
       * @param col the specified table column
       * @return table data path of the table column
       */
      @Override
      public TableDataPath getColDataPath(int col) {
         return (col >= 0 && col < map.length) ? descriptor.getColDataPath(map[col]) : null;
      }

      /**
       * Get table data path of a specified table row.
       * @param row the specified table row
       * @return table data path of the table row
       */
      @Override
      public TableDataPath getRowDataPath(int row) {
         return descriptor.getRowDataPath(row);
      }

      /**
       * Get table data path of a specified table cell.
       * @param row the specified table cell row
       * @param col the specified table cell col
       * @return table data path of the table cell
       */
      @Override
      public TableDataPath getCellDataPath(int row, int col) {
         return (col >= 0 && col < map.length) ? descriptor.getCellDataPath(row, map[col]) : null;
      }

      /**
       * Check if a column belongs to a table data path.
       * @param col the specified table col
       * @param path the specified table data path
       * @return true if the col belongs to the table data path, false otherwise
       */
      @Override
      public boolean isColDataPath(int col, TableDataPath path) {
         return (col >= 0 && col < map.length) ? descriptor.isColDataPath(map[col], path) : false;
      }

      /**
       * Check if a row belongs to a table data path.
       * @param row the specified table row
       * @param path the specified table data path
       * @return true if the row belongs to the table data path, false otherwise
       */
      @Override
      public boolean isRowDataPath(int row, TableDataPath path) {
         return descriptor.isRowDataPath(row, path);
      }

      /**
       * Check if a cell belongs to a table data path in a loose way.
       * Note: when cheking, path in the table data path will be ignored.
       * @param row the specified table cell row
       * @param col the specified table cell col
       * @param path the specified table data path
       * @return true if the cell belongs to the table data path,
       * false otherwise
       */
      @Override
      public boolean isCellDataPathType(int row, int col, TableDataPath path) {
         return col >= 0 && col < map.length && descriptor.isCellDataPathType(row, map[col], path);
      }

      /**
       * Check if a cell belongs to a table data path.
       * @param row the specified table cell row
       * @param col the specified table cell col
       * @param path the specified table data path
       * @return true if the cell belongs to the table data path,
       * false otherwise
       */
      @Override
      public boolean isCellDataPath(int row, int col, TableDataPath path) {
         return col >= 0 && col < map.length && descriptor.isCellDataPath(row, map[col], path);
      }

      /**
       * Get level of a specified table row, which is required for nested table.
       * The default value is <tt>-1</tt>.
       * @param row the specified table row
       * @return level of the table row
       */
      @Override
      public int getRowLevel(int row) {
         return descriptor.getRowLevel(row);
      }

      /**
       * Get table type which is one of the table types defined in table data
       * descriptor like <tt>NORMAL_TABLE</tt>, <tt>CROSSTAB_TABLE</tt>, etc.
       * @return table type
       */
      @Override
      public int getType() {
         return descriptor.getType();
      }

      /**
       * Get table xmeta info.
       * @param path the specified table data path
       * @return meta info of the table data path
       */
      @Override
      public XMetaInfo getXMetaInfo(TableDataPath path) {
         if(path == null || !path.isCell()) {
            return null;
         }

         Object obj = mmap.get(path);

         if(obj instanceof XMetaInfo) {
            return (XMetaInfo) obj;
         }
         else if(obj != null) {
            return null;
         }

         String[] paths = path.getPath();
         XMetaInfo minfo;

         if(paths.length != 1 || path instanceof BinaryTableDataPath) {
            minfo = descriptor.getXMetaInfo(path);
         }
         else {
            String header = paths[0];

            if(!containsColumn(table, header)) {
               int index = header.lastIndexOf('.');

               if(index >= 0) {
                  header = header.substring(0, index);
               }
            }

            TableDataPath opath = new TableDataPath(path.getLevel(),
               path.getType(), path.getDataType(), new String[] {header});
            minfo = descriptor.getXMetaInfo(opath);

            if(minfo == null && !DateComparisonUtil.isDCCalcDatePartRef(header)) {
               int start = header.indexOf('(');
               int end = header.lastIndexOf(')');

               if(start >= 0 && end > start) {
                  header = header.substring(start + 1, end);
               }

               opath = new TableDataPath(path.getLevel(),
                  path.getType(), path.getDataType(), new String[] {header});
               minfo = descriptor.getXMetaInfo(opath);
            }
         }

         mmap.put(path, minfo == null ? Tool.NULL : minfo);
         return minfo;
      }

      @Override
      public java.util.List<TableDataPath> getXMetaInfoPaths() {
         java.util.List<TableDataPath> list = new ArrayList<>();

         if(!mmap.isEmpty()) {
            list.addAll(mmap.keySet());
         }

         return list;
      }

      /**
       * Check if contains a column.
       */
      private boolean containsColumn(TableLens table, String header) {
         if(table == null) {
            return false;
         }

         for(int i = 0; i < table.getColCount(); i++) {
            String col = Util.getHeader(table, i).toString();

            if(col.equals(header)) {
               return true;
            }
         }

         return false;
      }

      /**
       * Check if contains format.
       * @return true if contains format.
       */
      @Override
      public boolean containsFormat() {
         return descriptor.containsFormat();
      }

      /**
       * Check if contains drill.
       * @return <tt>true</tt> if contains drill.
       */
      @Override
      public boolean containsDrill() {
         return descriptor.containsDrill();
      }

      private TableDataDescriptor descriptor;
      private Hashtable<TableDataPath, Object> mmap = new Hashtable<>();
   }

   private TableLens table;
   private int[] map; // column mapping
   private transient Object[] headers; // column header
   private transient String[] identifiers;
   private transient int[] duptimes; // header's duplicated times
   private transient short headerRows = 1;
}
