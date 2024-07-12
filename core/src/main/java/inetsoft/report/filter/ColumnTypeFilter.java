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
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.util.SparseMatrix;
import inetsoft.util.Tool;

import java.awt.*;

/**
 * Since database like SQLite don't support some normal data type like date, time, datetime,
 * boolean, e.g., user may setting column type in logical model, then we need to keep the
 * data types in logical model when quering data and use this filter to convert the data to
 * the target types, to make sure the later usage like doing date group can work well.
 *
 * @version 13.5
 * @author InetSoft Technology Corp
 */
public class ColumnTypeFilter extends AbstractTableLens
   implements TableFilter, SortedTable, CancellableTableLens
{
   /**
    * The map[i] points to the column number in the base table. The columns
    * are reordered according to the mapping.
    */
   public ColumnTypeFilter(TableLens table, String[] types) {
      this.types = types;
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
      return table.getDescriptor();
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
      return col;
   }

   /**
    * Invalidate the table filter forcely, and the table filter will perform
    * filtering calculation to validate itself.
    */
   @Override
   public void invalidate() {
      cellValues = null;
      fireChangeEvent();
   }

   /**
    * Get the columns that the table is sorted on.
    * @return sort columns.
    */
   @Override
   public int[] getSortCols() {
      if(table instanceof SortedTable) {
         return ((SortedTable) table).getSortCols().clone();
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
         ((SortedTable) table).setComparer(col, comp);
      }
   }

   /**
    * Get the comparer for a sorting column.
    * @param col the specified table column index.
    */
   @Override
   public Comparer getComparer(int col) {
      if(table instanceof SortedTable) {
         return ((SortedTable) table).getComparer(col);
      }
      else {
         return null;
      }
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
      return table.getColCount();
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.
    */
   @Override
   public int getHeaderRowCount() {
      return table.getHeaderRowCount();
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
         return table.getColWidth(col);
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
      if(types != null && col < types.length) {
         return Tool.getDataClass(types[col]);
      }

      return table.getColType(col);
   }

   /**
    * Check if is primitive.
    * @return <tt>true</tt> if is primitive, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isPrimitive(int col) {
      return table.isPrimitive(col);
   }

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getRowBorderColor(int r, int c) {
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
      return table.getSpan(r, c);
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
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
      return table.getBackground(r, c);
   }

   @Override
   public int getAlpha(int r, int c) {
      return table.getAlpha(r, c);
   }

   /**
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isNull(int r, int c) {
      return table.isNull(r, c);
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      if(r < table.getHeaderRowCount()) {
         return table.getObject(r, c);
      }

      if(cellValues == null) {
         cellValues = new SparseMatrix();
      }

      Object val = cellValues.get(r, c);

      if(val != SparseMatrix.NULL) {
         return val;
      }

      val = table.getObject(r, c);

      if(val != null && types != null && c < types.length) {
         val = Tool.getData(types[c], val);
      }

      cellValues.set(r, c, val);

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
      return table.getBoolean(r, c);
   }

   /**
    * Set the cell value.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      table.setObject(r, c, v);
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
      table.setColumnIdentifier(col, identifier);
   }

   /**
    * Cancel the table lens and running queries if supported.
    */
   @Override
   public synchronized void cancel() {
      if(table instanceof CancellableTableLens) {
         ((CancellableTableLens) table).cancel();
      }
   }

   /**
    * Check the TableLens to see if it is cancelled.
    */
   @Override
   public synchronized boolean isCancelled() {
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

   private TableLens table;
   private String[] types;
   private String[] identifiers;
   private transient SparseMatrix cellValues;
}
