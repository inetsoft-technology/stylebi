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
package inetsoft.report.lens;

import inetsoft.report.*;
import inetsoft.report.filter.DefaultTableChangeListener;
import inetsoft.report.internal.table.MappedDataDescriptor;
import inetsoft.util.Tool;

import java.awt.*;

/**
 * The SubTableLens extracts a region of an existing table and present
 * the region as a table. It can extract rows, columns, or a continuous
 * region of cells in a table. The most common use of SubTableLens is
 * to extract a subset of columns from a tale.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class SubTableLens extends AbstractTableLens implements TableFilter {
   /**
    * Construct a sub table of the specified rows and columns from the
    * original table. A null value for either rows or cols means the
    * dimension is not translated.
    * @param table original table.
    * @param rows rows in the original table.
    * @param cols columns in the original table.
    */
   public SubTableLens(TableLens table, int[] rows, int[] cols) {
      init(table, rows, cols, true, true);
   }

   /**
    * Construct a sub table of a region in the original mode.
    * @param table original table. If the row or col value is
    * less than 0, the dimension is not translated.
    * @param row row number of the upper-left corner of region.
    * @param col column number of the upper-left corner of region.
    * @param nrow number of rows.
    * @param ncol number of columns.
    */
   public SubTableLens(TableLens table, int row, int col, int nrow, int ncol) {
      this(table, row, col, nrow, ncol, true, true);
   }

   /**
    * Construct a sub table of a region in the original mode.
    * @param table original table. If the row or col value is
    * less than 0, the dimension is not translated.
    * @param row row number of the upper-left corner of region.
    * @param col column number of the upper-left corner of region.
    * @param nrow number of rows.
    * @param ncol number of columns.
    * @param keepHeaderRow whether or not keep the original table's header rows
    * @param keepHeaderCol whether or not keep the original table's header cols
    */
   public SubTableLens(TableLens table, int row, int col, int nrow, int ncol,
		       boolean keepHeaderRow, boolean keepHeaderCol) {
      int[] rows = null;
      int[] cols = null;

      if(row >= 0) {
         rows = new int[nrow];
         for(int i = 0; i < nrow; i++) {
            rows[i] = i + row;
         }
      }

      if(col >= 0) {
         cols = new int[ncol];

         for(int i = 0; i < ncol; i++) {
            cols[i] = i + col;
         }
      }

      init(table, rows, cols, keepHeaderRow, keepHeaderCol);
   }

   /**
    * Construct a sub table of the specified region in the original table.
    * @param table original table.
    * @param region region in original table.
    */
   public SubTableLens(TableLens table, Rectangle region) {
      this(table, region.y, region.x, region.height, region.width);
   }

   /**
    * Construct a sub table of the specified rows and columns from the
    * original table. A null value for either rows or cols means the
    * dimension is not translated.
    * @param table original table.
    * @param rows rows in the original table.
    * @param cols columns in the original table.
    * @parem keepHeaderRow whether or not keep the original table's header rows
    * @parem keepHeaderCol whether or not keep the original table's header cols
    */
   private void init(TableLens table, int[] rows, int[] cols,
                     boolean keepHeaderRow, boolean keepHeaderCol) {
      setTable(table);
      this.rows = rows;
      this.cols = cols;
      adjustHeader(keepHeaderRow, keepHeaderCol);
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
    * Invalidate the table filter forcely, and the table filter will perform
    * filtering calculation to validate itself.
    */
   @Override
   public void invalidate() {
      fireChangeEvent();

      // @by henryh, if base table is modified to remove some columns, the
      // cols[] cannot match correct columns. And it will throw
      // ArrayIndexOutofBounds exception. Here we remove those columns from
      // cols[] map.
      if(cols != null && table != null) {
         int[] tmp = new int[cols.length];
         int size = 0;

         for(int i = 0; i < cols.length; i++) {
            if(cols[i] < table.getColCount()) {
               tmp[size] = cols[i];
               size++;
            }
         }

         int[] columns = new int[size];
         System.arraycopy(tmp, 0, columns, 0, size);

         cols = columns;
      }
   }

   /**
    * Get the base table row index corresponding to the filtered table.
    * If the row does not exist in the base table, it returns -1.
    * @param row row index in the filtered table.
    * @return corresponding row index in the base table.
    */
   @Override
   public int getBaseRowIndex(int row) {
      return getR(row);
   }

   /**
    * Get the base table column index corresponding to the filtered table.
    * If the column does not exist in the base table, it returns -1.
    * @param col column index in  the filtered table.
    * @return corresponding column index in the bast table.
    */
   @Override
   public int getBaseColIndex(int col) {
      return getC(col);
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
      if(row < 0) {
         return true;
      }

      if(!checkR(row)) {
         return false;
      }

      return table.moreRows(getR(row));
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      int nrow = table.getRowCount();

      if(rows == null) {
         return nrow;
      }
      else if(nrow < 0) {
         return Math.min(-nrow-1, rows.length);
      }

      return Math.min(nrow, rows.length);
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public int getColCount() {
      return (cols != null) ? cols.length : table.getColCount();
   }

   /**
    * Get the current column content type.
    * @param col column number.
    * @return column type.
    */
   @Override
   public Class getColType(int col) {
      return checkC(col) ? table.getColType(getC(col)) : String.class;
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.
    */
   @Override
   public int getHeaderRowCount() {
      if(rows == null) {
         return table.getHeaderRowCount();
      }

      int n;

      for(n = 0; n < rows.length && rows[n] < table.getHeaderRowCount(); n++) {
      }

      return n;
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      if(cols == null) {
         return table.getHeaderColCount();
      }

      int n;

      for(n = 0; n < cols.length && cols[n] < table.getHeaderColCount(); n++) {
      }

      return n;
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as tail rows.
    * @return number of header rows.
    */
   @Override
   public int getTrailerRowCount() {
      if(rows == null) {
         return table.getTrailerRowCount();
      }

      int n;
      table.moreRows(Integer.MAX_VALUE);

      for(n = 0; n < rows.length &&
         rows[n] >= (table.getRowCount() - table.getTrailerRowCount()); n++) {
      }

      return n;
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as tail columns.
    */
   @Override
   public int getTrailerColCount() {
      if(cols == null) {
         return table.getTrailerColCount();
      }

      int n;

      for(n = 0; n < cols.length &&
         cols[n] >= (table.getColCount() - table.getTrailerColCount()); n++) {
      }

      return n;
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
      return checkR(row) ? table.getRowHeight(getR(row)) : -1;
   }

   /**
    * Get the current column width setting. The meaning of column widths
    * depends on the table layout policy setting. If the column width
    * is to be calculated by the ReportSheet based on the content,
    * return -1.
    * @return column width.
    */
   @Override
   public int getColWidth(int col) {
      return checkC(col) ? table.getColWidth(getC(col)) : -1;
   }

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getRowBorderColor(int r, int c) {
      return table.getRowBorderColor(getBorderR(r), getBorderC(c));
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      return table.getColBorderColor(getBorderR(r), getBorderC(c));
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
      return table.getRowBorder(getBorderR(r), getBorderC(c));
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
      return table.getColBorder(getBorderR(r), getBorderC(c));
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      return checkRC(r, c) ? table.getInsets(getR(r), getC(c)) : null;
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
      return checkRC(r, c) ? table.getSpan(getR(r), getC(c)) : null;
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      return checkRC(r, c) ? table.getAlignment(getR(r), getC(c)) :
                             H_LEFT | V_CENTER;
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      return checkRC(r, c) ? table.getFont(getR(r), getC(c)) : null;
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
      return checkRC(r, c) ? table.isLineWrap(getR(r), getC(c)) : true;
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
      return checkRC(r, c) ? table.getForeground(getR(r), getC(c)) : null;
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
      return checkRC(r, c) ? table.getBackground(getR(r), getC(c)) : null;
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      return checkRC(r, c) ? table.getObject(getR(r), getC(c)) : null;
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the double value in the specified row.
    */
   @Override
   public final double getDouble(int r, int c) {
      return checkRC(r, c) ? table.getDouble(getR(r), getC(c)) : Tool.NULL_DOUBLE;
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the float value in the specified row.
    */
   @Override
   public final float getFloat(int r, int c) {
      return checkRC(r, c) ? table.getFloat(getR(r), getC(c)) : Tool.NULL_FLOAT;
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the long value in the specified row.
    */
   @Override
   public final long getLong(int r, int c) {
      return checkRC(r, c) ? table.getLong(getR(r), getC(c)) : Tool.NULL_LONG;
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the int value in the specified row.
    */
   @Override
   public final int getInt(int r, int c) {
      return checkRC(r, c) ? table.getInt(getR(r), getC(c)) : Tool.NULL_INTEGER;
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the short value in the specified row.
    */
   @Override
   public final short getShort(int r, int c) {
      return checkRC(r, c) ? table.getShort(getR(r), getC(c)) : Tool.NULL_SHORT;
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the byte value in the specified row.
    */
   @Override
   public final byte getByte(int r, int c) {
      return checkRC(r, c) ? table.getByte(getR(r), getC(c)) : Byte.MIN_VALUE;
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the boolean value in the specified row.
    */
   @Override
   public final boolean getBoolean(int r, int c) {
      return checkRC(r, c) ? table.getBoolean(getR(r), getC(c)) : false;
   }

   /**
    * Check if is primitive.
    * @return <tt>true</tt> if is primitive, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isPrimitive(int col) {
      return checkC(col) ? table.isPrimitive(getC(col)) : false;
   }

   /**
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isNull(int r, int c) {
      return checkRC(r, c) ? table.isNull(getR(r), getC(c)) : true;
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   public Object getData(int r, int c) {
      return getObject(r, c);
   }

   /**
    * Set a cell value.
    * @param r row index.
    * @param c column index.
    * @param val cell value.
    */
   @Override
   public void setObject(int r, int c, Object val) {
      if(checkRC(r, c)) {
         table.setObject(getR(r), getC(c), val);
      }
   }

   /**
    * Set a cell value.
    * @param r row index.
    * @param c column index.
    * @param val cell value.
    */
   public void setData(int r, int c, Object val) {
      setObject(r, c, val);
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
      String identifier = super.getColumnIdentifier(col);
      col = getBaseColIndex(col);

      return identifier == null && col >= 0 ? table.getColumnIdentifier(col) :
                                              identifier;
   }

   /**
    * Translate the row number to the mapped row number.
    */
   protected int getR(int row) {
      return rows == null || rows.length == 0 || row < 0 ?
         row : row >= rows.length ? -1 : rows[row];
   }

   /**
    * Translate the column number to the mapped column number.
    */
   protected int getC(int col) {
      return cols == null || cols.length == 0 || col < 0 ?
         col : col >= cols.length ? -1 : cols[col];
   }

   private boolean checkRC(int r, int c) {
      return checkR(r) && checkC(c);
   }

   private boolean checkR(int r) {
      return getR(r) >= 0;
   }

   private boolean checkC(int c) {
      return getC(c) >= 0;
   }

   /**
    * Translate the row number to the mapped row number. This has special
    * handling for border so if the border is the bottom border of
    * the table, use the bottom border of the original table.
    */
   private int getBorderR(int row) {
      return (row == getRowCount() - 1) ? table.getRowCount() - 1 : getR(row);
   }

   /**
    * Translate the column number to the mapped column number. This has special
    * handling for border so if the border is the right border of
    * the table, use the right border of the original table.
    */
   private int getBorderC(int col) {
      return (col == getColCount() - 1) ? table.getColCount() - 1 : getC(col);
   }

   /**
    * Make sure the row/column headers are included.
    */
   protected void adjustHeader(boolean keepHR, boolean keepHC) {
      int hrow = table.getHeaderRowCount();

      if(rows != null && rows.length > 0 && keepHR &&
         hrow > 0 && rows[0] >= hrow) {
         int[] nrows = new int[rows.length + hrow];

         System.arraycopy(rows, 0, nrows, hrow, rows.length);
         for(int i = 0; i < hrow; i++) {
            nrows[i] = i;
         }

         rows = nrows;
      }

      int hcol = table.getHeaderColCount();

      if(cols != null && cols.length >0 && keepHC &&
         hcol > 0 && cols[0] >= hcol) {
         int[] ncols = new int[cols.length + hcol];

         System.arraycopy(cols, 0, ncols, hcol, cols.length);
         for(int i = 0; i < hcol; i++) {
            ncols[i] = i;
         }

         cols = ncols;
      }
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(descriptor == null) {
         descriptor = new MappedDataDescriptor(this, table.getDescriptor());
      }

      return descriptor;
   }

   /**
    * @return the report/vs name which this filter was created for,
    * and will be used when insert audit record.
    */
   @Override
   public String getReportName() {
      String name = super.getReportName();
      return name != null ? name : table == null ? null : table.getReportName();
   }

   /**
    * @return the report type which this filter was created for:
    * ExecutionBreakDownRecord.OBJECT_TYPE_REPORT or
    * ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET
    */
   @Override
   public String getReportType() {
      String type = super.getReportType();
      return type != null ? type : table == null ? null : table.getReportType();
   }

   private TableLens table;
   private TableDataDescriptor descriptor = null;
   protected int[] rows, cols;
}
