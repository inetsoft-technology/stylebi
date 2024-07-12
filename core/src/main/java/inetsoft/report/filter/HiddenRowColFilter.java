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
package inetsoft.report.filter;

import inetsoft.report.*;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.report.internal.table.SpanMap;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.uql.XMetaInfo;

import java.awt.*;
import java.util.*;

/**
 * Hidden the table row or col for base table.
 */
public class HiddenRowColFilter extends AbstractTableLens implements TableFilter,
   CancellableTableLens
{
   public HiddenRowColFilter(TableLens table) {
      super();

      this.table = table;
      hiddenRows = new HashSet<>();
      hiddenCols = new HashSet<>();
      init();
   }

   /**
    * Hidden the base table row.
    * @param row row index.
    */
   public void hiddenRow(int row) {
      if(row >= table.getRowCount()) {
         return;
      }

      hiddenRows.add(row);
      clearMaps();
   }

   /**
    * Hidden the base table col.
    * @param col col index.
    */
   public void hiddenCol(int col) {
      if(col >= table.getColCount()) {
         return;
      }

      hiddenCols.add(col);
      clearMaps();
   }

   private void init() {
      table.moreRows(TableLens.EOT);
   }

   @Override
   public int getRowCount() {
      return table.getRowCount() - hiddenRows.size();
   }

   @Override
   public int getColCount() {
      return table.getColCount() - hiddenCols.size();
   }

   @Override
   public Object getObject(int r, int c) {
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);

      return table.getObject(r, c);
   }

   @Override
   public TableLens getTable() {
      return table;
   }

   @Override
   public void setTable(TableLens table) {
      this.table = table;
      this.hiddenRows.clear();
      this.hiddenCols.clear();
      clearMaps();
   }

   @Override
   public void invalidate() {
      init();
      clearMaps();
   }

   private void clearMaps() {
      this.rowMapping = null;
      this.colMapping = null;
   }

   @Override
   public int getHeaderRowCount() {
      int headerRowCount = table.getHeaderRowCount();

      for(int i = 0; i < headerRowCount; i++) {
         if(hiddenRows.contains(i)) {
            headerRowCount--;
         }
      }

      return headerRowCount;
   }

   @Override
   public int getHeaderColCount() {
      int headerColCount = table.getHeaderColCount();

      for(int i = 0; i < headerColCount; i++) {
         if(hiddenCols.contains(i)) {
            headerColCount--;
         }
      }

      return headerColCount;
   }

   @Override
   public boolean moreRows(int row) {
      if(row >= getRowCount()) {
         return false;
      }

      return table.moreRows(getBaseRowIndex(row));
   }

   private void initMapping() {
      int row = 0;
      rowMapping = new HashMap<>();

      for(int i = 0; i < table.getRowCount(); i++) {
         for(int j = 0; j < table.getColCount(); j++) {
            Dimension span = table.getSpan(i, j);

            if(span != null) {
               baseSpanMap.add(i, j, span.height, span.width);
            }
         }

         if(hiddenRows.contains(i)) {
            continue;
         }

         rowMapping.put(row, i);
         row++;
      }

      int col = 0;
      colMapping = new HashMap<>();

      for(int i = 0; i < table.getColCount(); i++) {
         if(hiddenCols.contains(i)) {
            continue;
         }

         colMapping.put(col, i);
         col++;
      }
   }

   @Override
   public String getColumnIdentifier(int col) {
      String identifier = super.getColumnIdentifier(col);
      col = getBaseColIndex(col);

      return identifier == null ? table.getColumnIdentifier(col) : identifier;
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(descriptor == null) {
         descriptor = new HiddenRowColFilterDataDescriptor(table.getDescriptor());
      }

      return descriptor;
   }

   /**
    * Cancel the table lens and running queries if supported.
    */
   @Override
   public void cancel() {
      TableLens tbl = this.table;

      if(tbl instanceof CancellableTableLens) {
         ((CancellableTableLens) tbl).cancel();
         cancelled = ((CancellableTableLens) tbl).isCancelled();
      }
   }

   /**
    * Check the TableLens to see if it is cancelled.
    */
   @Override
   public boolean isCancelled() {
      return cancelled;
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as trailer rows.
    * @return number of header rows.
    */
   @Override
   public int getTrailerRowCount() {
      return table.getTrailerRowCount();
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as trailer columns.
    */
   @Override
   public int getTrailerColCount() {
      return table.getTrailerColCount();
   }

   /**
    * Check if is primitive.
    * @return <tt>true</tt> if is primitive, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isPrimitive(int col) {
      col = getBaseColIndex(col);

      return table.isPrimitive(col);
   }

   /**
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isNull(int r, int c) {
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);

      return table.isNull(r, c);
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the double value in the specified row.
    */
   @Override
   public final double getDouble(int r, int c) {
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);

      return table.getDouble(r, c);
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the float value in the specified row.
    */
   @Override
   public final float getFloat(int r, int c) {
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);

      return table.getFloat(r, c);
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the long value in the specified row.
    */
   @Override
   public final long getLong(int r, int c) {
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);

      return table.getLong(r, c);
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the int value in the specified row.
    */
   @Override
   public final int getInt(int r, int c) {
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);

      return table.getInt(r, c);
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the short value in the specified row.
    */
   @Override
   public final short getShort(int r, int c) {
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);

      return table.getShort(r, c);
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the byte value in the specified row.
    */
   @Override
   public final byte getByte(int r, int c) {
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);

      return table.getByte(r, c);
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the boolean value in the specified row.
    */
   @Override
   public final boolean getBoolean(int r, int c) {
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);

      return table.getBoolean(r, c);
   }

   /**
    * Get the current column content type.
    * @param col column number.
    * @return column type.
    */
   @Override
   public Class getColType(int col) {
      col = getBaseColIndex(col);

      return table.getColType(col);
   }

   /**
    * Set the cell value. For table filters, the setObject() call should
    * be forwarded to the base table if possible. An implementation should
    * throw a runtime exception if this method is not supported. In that
    * case, data in a table can not be modified in scripts.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);

      table.setObject(r, c, v);
   }

   /**
    * Get the current row heights setting. The meaning of row heights
    * depends on the table layout policy setting. If the row height
    * is to be calculated by the ReportSheet based on the content,
    * return -1.
    * @return row height.
    */
   @Override
   public int getRowHeight(int r) {
      r = getBaseRowIndex(r);

      return table.getRowHeight(r);
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
      col = getBaseColIndex(col);

      return table.getColWidth(col);
   }

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getRowBorderColor(int r, int c) {
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);

      return table.getRowBorderColor(r, c);
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);

      return table.getColBorderColor(r, c);
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
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);

      return table.getRowBorder(r, c);
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
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);

      return table.getColBorder(r, c);
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);

      return table.getInsets(r, c);
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
      int baseRow = getBaseRowIndex(r);
      int baseCol = getBaseColIndex(c);
      return getBaseSpan(baseRow, baseCol);
   }

   /**
    * Get cell span of base table.
    * @param row  row of base table.
    * @param col  col of base table.
    * @return
    */
   private Dimension getBaseSpan(int row, int col) {
      if(baseSpanMap == null) {
         return null;
      }

      Rectangle rec = baseSpanMap.get(row, col);
      return rec == null ? null : new Dimension((int) rec.getWidth(), (int) rec.getHeight());
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);

      return table.getAlignment(r, c);
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);

      return table.getFont(r, c);
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
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);

      return table.isLineWrap(r, c);
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
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);

      return table.getForeground(r, c);
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
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);

      return table.getBackground(r, c);
   }

   /**
    * Get the base table row index corresponding to the filtered table.
    * If the row does not exist in the base table, it returns -1.
    * @param r row index in the filtered table.
    * @return corresponding row index in the base table.
    */
   @Override
   public int getBaseRowIndex(int r) {
      if(r < 0) {
         return r;
      }

      if(r >= getRowCount()) {
         return -1;
      }

      if(rowMapping == null || colMapping == null) {
         initMapping();
      }

      Integer baseRow = rowMapping.get(r);

      return baseRow == null ? -1 : baseRow;
   }

   /**
    * Get the base table column index corresponding to the filtered table.
    * If the column does not exist in the base table, it returns -1.
    * @param c column index in  the filtered table.
    * @return corresponding column index in the bast table.
    */
   @Override
   public int getBaseColIndex(int c) {
      if(rowMapping == null || colMapping == null) {
         initMapping();
      }

      Integer baseCol = colMapping.get(c);

      return baseCol == null ? -1 : baseCol;
   }

   /**
    * Finalize the distinct table lens.
    */
   @Override
   protected void finalize() throws Throwable {
      super.finalize();
      dispose();
   }

   /**
    * Dispose the distinct table lens.
    */
   @Override
   public void dispose() {
      synchronized(rlock) {
         if(table != null) {
            table.dispose();
         }
      }
   }

   /**
    * @return the report/vs name which this filter was created for,
    * and will be used when insert audit record.
    */
   @Override
   public String getReportName() {
      String name = super.getReportName();

      if(name == null && table != null) {
         name = table.getReportName();
      }

      return name;
   }

   /**
    * @return the report type which this filter was created for:
    * ExecutionBreakDownRecord.OBJECT_TYPE_REPORT or
    * ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET
    */
   @Override
   public String getReportType() {
      String type = super.getReportType();

      if(type == null && table != null) {
         type = table.getReportType();
      }

      return type;
   }

   /**
    * HiddenRowColFilter data descriptor.
    */
   private class HiddenRowColFilterDataDescriptor implements TableDataDescriptor {
      /**
       * Create a ColumnMapFilterDataDescriptor.
       * @param descriptor the base descriptor
       */
      public HiddenRowColFilterDataDescriptor(TableDataDescriptor descriptor) {
         this.descriptor = descriptor;
      }

      /**
       * Get table data path of a specified table column.
       * @param col the specified table column
       * @return table data path of the table column
       */
      @Override
      public TableDataPath getColDataPath(int col) {
         return descriptor.getColDataPath(getBaseColIndex(col));
      }

      /**
       * Get table data path of a specified table row.
       * @param row the specified table row
       * @return table data path of the table row
       */
      @Override
      public TableDataPath getRowDataPath(int row) {
         return descriptor.getRowDataPath(getBaseRowIndex(row));
      }

      /**
       * Get table data path of a specified table cell.
       * @param row the specified table cell row
       * @param col the specified table cell col
       * @return table data path of the table cell
       */
      @Override
      public TableDataPath getCellDataPath(int row, int col) {
         return descriptor.getCellDataPath(getBaseRowIndex(row), getBaseColIndex(col));
      }

      /**
       * Check if a column belongs to a table data path.
       * @param col the specified table col
       * @param path the specified table data path
       * @return true if the col belongs to the table data path, false otherwise
       */
      @Override
      public boolean isColDataPath(int col, TableDataPath path) {
         return descriptor.isColDataPath(getBaseColIndex(col), path);
      }

      /**
       * Check if a row belongs to a table data path.
       * @param row the specified table row
       * @param path the specified table data path
       * @return true if the row belongs to the table data path, false otherwise
       */
      @Override
      public boolean isRowDataPath(int row, TableDataPath path) {
         return descriptor.isRowDataPath(getBaseRowIndex(row), path);
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
         return descriptor.isCellDataPathType(getBaseRowIndex(row), getBaseColIndex(col), path);
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
         return descriptor.isCellDataPath(getBaseRowIndex(row), getBaseColIndex(col), path);
      }

      /**
       * Get level of a specified table row, which is required for nested table.
       * The default value is <tt>-1</tt>.
       * @param row the specified table row
       * @return level of the table row
       */
      @Override
      public int getRowLevel(int row) {
         return descriptor.getRowLevel(getBaseRowIndex(row));
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
         return descriptor.getXMetaInfo(path);
      }

      @Override
      public java.util.List<TableDataPath> getXMetaInfoPaths() {
         return descriptor.getXMetaInfoPaths();
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
   }

   private TableLens table;       // base table
   private Set<Integer> hiddenRows;
   private Set<Integer> hiddenCols;
   private Map<Integer, Integer> rowMapping;
   private Map<Integer, Integer> colMapping;
   private transient SpanMap baseSpanMap = new SpanMap();
   private boolean cancelled;     // cancelled flag
   private final transient Object rlock = new Object(); // table row lock
}
