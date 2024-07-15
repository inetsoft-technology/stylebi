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
package inetsoft.report.internal.table;

import inetsoft.report.*;
import inetsoft.report.filter.DefaultTableChangeListener;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.uql.XMetaInfo;
import inetsoft.util.swap.XIntList;
import inetsoft.util.swap.XSwappableIntList;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import java.awt.*;
import java.text.Format;
import java.util.BitSet;
import java.util.List;

/**
 * The SpanTableLens is used internally to split up table during printing
 * so a large cell can span across pages.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class SpanTableLens extends AttributeTableLens
   implements TableFilter, CachedTableLens
{
   /**
    * Construct a table from a base table.
    * @param table original table.
    */
   public SpanTableLens(TableLens table) {
      setTable(table);
      rowarr = new XIntList();
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(cdescriptor == null) {
         cdescriptor =
            new SpanTableLensDataDescriptor(table.getDescriptor());
      }

      return cdescriptor;
   }

   /**
    * Split a row into multiple rows.
    */
   public synchronized boolean split(int row, int span) {
      if(spanrows.get(row) || rowarr == null) {
         return false;
      }

      splitmap.put(row, span);
      spanrows.set(row);

      int ncnt = row + span + newrows;
      int r = (rowarr.size() > 0) ? rowarr.get(rowarr.size() - 1) : -1;

      for(int i = rowarr.size(); i < ncnt; i++) {
         if(i < row + newrows) {
            rowarr.add(++r);
         }
         else {
            rowarr.add(row);
         }
      }

      newrows += span - 1;
      return true;
   }

   /**
    * Complete this span table lens.
    */
   public synchronized void complete() {
      // convert row arr to swappable int list
      if(rowarr != null) {
         rowmap = new XSwappableIntList(rowarr);
         rowmap.complete();
         rsize = rowmap.size();
      }

      rowarr = null;
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
      super.setTable(table);
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
   }

   /**
    * Check if the row is a split of another row or not.
    */
   public boolean isSplitRow(int row) {
      if(row == Integer.MAX_VALUE) {
         return false;
      }

      for(int r : splitmap.keySet()) {
         int span = splitmap.get(r);

         if(r + span > row && row > r) {
            return true;
         }
      }

      return false;
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
    * Get the first row index which is mapped to the base row index.
    */
   public int getFirstRowIndex(int base) {
      return getRowIndex(base, true);
   }

   /**
    * Get the last row index which is mapped to the base row index.
    */
   public int getLastRowIndex(int base) {
      return getRowIndex(base, false);
   }

   /**
    * Get the row index which is mapped to the base row index.
    */
   private int getRowIndex(int base, boolean first) {
      boolean found = false;
      int r = -1;

      for(int i = base; i < getRowCount(); i++) {
         r = getBaseRowIndex(i);

         if(found && r != base) {
            return i - 1;
         }

         if(r == base) {
            if(first) {
               return i;
            }

            found = true;
         }
      }

      return r;
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
      return table.moreRows(getR(row));
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      int count = table.getRowCount();

      return (count < 0) ? count : (count + newrows);
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
    * Get the current column content type.
    * @param col column number.
    * @return column type.
    */
   @Override
   public Class getColType(int col) {
      return table.getColType(col);
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
      row = getR(row);
      int h = table.getRowHeight(row);

      if(h > 0 && spanrows.get(row)) {
         h /= splitmap.get(row);
      }

      return h;
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
      return table.getRowBorderColor(getR(r), c);
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      return table.getColBorderColor(getR(r), c);
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
      return table.getRowBorder(getR(r), c);
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
      return table.getColBorder(getR(r), c);
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      return table.getInsets(getR(r), c);
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
      int row = getR(r);
      Dimension span = table.getSpan(row, c);

      // if the mapping row is not the original spliting row, don't get the
      // span otherwise all spanned rows would have the span defined
      if(row != getR(r - 1) && spanrows.get(row)) {
         int n = splitmap.get(row);

         if(span != null) {
            span = new Dimension(span.width, span.height);
            span.height += n - 1;
         }
         else {
            span = new Dimension(1, n);
         }
      }

      return span;
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      return table.getAlignment(getR(r), c);
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      return table.getFont(getR(r), c);
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
      return table.isLineWrap(getR(r), c);
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
      return table.getForeground(getR(r), c);
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
      return table.getBackground(getR(r), c);
   }

   /**
    * Ge the format for the specified cell.
    * @param row row number.
    * @param col column number.
    * @return column format.
    */
   @Override
   public Format getFormat(int r, int c) {
      if(attritable != null) {
         return attritable.getFormat(getR(r), c);
      }

      return super.getFormat(getR(r), c);
   }

   /**
    * Get the format for the specified cell.
    * @param row row number.
    * @param col column number.
    * @param cellOnly only get the cell format or not.
    * @return column format.
    */
   @Override
   public Format getFormat(int r, int c, boolean cellOnly) {
      if(attritable != null) {
         return attritable.getFormat(getR(r), c, cellOnly);
      }

      return super.getFormat(getR(r), c, cellOnly);

   }

   /**
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isNull(int r, int c) {
      return table.isNull(getR(r), c);
   }

   /**
    * Return the per cell suppress if zero mode.
    * @param r row number
    * @param c column number
    * @return true when suppress if zero should be done
    */
   @Override
   public boolean isSuppressIfZero(int r, int c) {
      return super.isSuppressIfZero(getR(r), c);
   }

   /**
    * Return the per cell suppress if duplicate mode.
    * @param r row number
    * @param c column number
    * @return true when suppress if duplicate should be done
    */
   @Override
   public boolean isSuppressIfDuplicate(int r, int c) {
      return super.isSuppressIfDuplicate(getR(r), c);
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      return table.getObject(getR(r), c);
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the double value in the specified row.
    */
   @Override
   public final double getDouble(int r, int c) {
      return table.getDouble(getR(r), c);
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the float value in the specified row.
    */
   @Override
   public final float getFloat(int r, int c) {
      return table.getFloat(getR(r), c);
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the long value in the specified row.
    */
   @Override
   public final long getLong(int r, int c) {
      return table.getLong(getR(r), c);
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the int value in the specified row.
    */
   @Override
   public final int getInt(int r, int c) {
      return table.getInt(getR(r), c);
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the short value in the specified row.
    */
   @Override
   public final short getShort(int r, int c) {
      return table.getShort(getR(r), c);
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the byte value in the specified row.
    */
   @Override
   public final byte getByte(int r, int c) {
      return table.getByte(getR(r), c);
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the boolean value in the specified row.
    */
   @Override
   public final boolean getBoolean(int r, int c) {
      return table.getBoolean(getR(r), c);
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
    * Set the cell value.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      table.setObject(getR(r), c, v);
   }

   /**
    * Dispose the table to clear up temporary resources.
    */
   @Override
   public void dispose() {
      table.dispose();

      if(rowmap != null) {
         rowmap.dispose();
         rowmap = null;
      }

      rowarr = null;
   }

   /**
    * Clear all cached data.
    */
   @Override
   public void clearCache() {
      // do nothing
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

      return identifier == null ? table.getColumnIdentifier(col) : identifier;
   }

   /**
    * Translate the row number to the mapped row number.
    */
   private synchronized int getR(int row) {
      if(row == Integer.MAX_VALUE || row < 0) {
         return row;
      }

      if(rowmap != null && row < rsize) {
	 if(row == lrow) {
	    return lval;
	 }
	 else {
	    lrow = row;
	    return lval = rowmap.get(lrow);
	 }
      }

      if(rowarr != null && row < rowarr.size()) {
         return rowarr.get(row);
      }

      return row - newrows;
   }

   /**
    * SpanTableLens data descriptor.
    */
   private class SpanTableLensDataDescriptor implements TableDataDescriptor {
      /**
       * Create a SpanTableLensDataDescriptor.
       * @param descriptor the base descriptor
       */
      public SpanTableLensDataDescriptor(TableDataDescriptor descriptor) {
         this.descriptor = descriptor;
      }

      /**
       * Get table data path of a specified table column.
       * @param col the specified table column
       * @return table data path of the table column
       */
      @Override
      public TableDataPath getColDataPath(int col) {
         return descriptor.getColDataPath(col);
      }

      /**
       * Get table data path of a specified table row.
       * @param row the specified table row
       * @return table data path of the table row
       */
      @Override
      public TableDataPath getRowDataPath(int row) {
         return descriptor.getRowDataPath(getR(row));
      }

      /**
       * Get table data path of a specified table cell.
       * @param row the specified table cell row
       * @param col the specified table cell col
       * @return table data path of the table cell
       */
      @Override
      public TableDataPath getCellDataPath(int row, int col) {
         return descriptor.getCellDataPath(getR(row), col);
      }

      /**
       * Check if a column belongs to a table data path.
       * @param col the specified table col
       * @param path the specified table data path
       * @return true if the col belongs to the table data path, false otherwise
       */
      @Override
      public boolean isColDataPath(int col, TableDataPath path) {
         return descriptor.isColDataPath(col, path);
      }

      /**
       * Check if a row belongs to a table data path.
       * @param row the specified table row
       * @param path the specified table data path
       * @return true if the row belongs to the table data path, false otherwise
       */
      @Override
      public boolean isRowDataPath(int row, TableDataPath path) {
         return descriptor.isRowDataPath(getR(row), path);
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
         return descriptor.isCellDataPathType(getR(row), col, path);
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
         return descriptor.isCellDataPath(getR(row), col, path);
      }

      /**
       * Get level of a specified table row, which is required for nested table.
       * The default value is <tt>-1</tt>.
       * @param row the specified table row
       * @return level of the table row
       */
      @Override
      public int getRowLevel(int row) {
         return descriptor.getRowLevel(getR(row));
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
       * Get meta info of a specified table data path.
       * @param path the specified table data path
       * @return meta info of the table data path
       */
      @Override
      public XMetaInfo getXMetaInfo(TableDataPath path) {
         return descriptor.getXMetaInfo(path);
      }

      @Override
      public List<TableDataPath> getXMetaInfoPaths() {
         return table.getDescriptor().getXMetaInfoPaths();
      }

      /**
       * Check if contains format.
       * @return true if contains format
       */
      @Override
      public boolean containsFormat() {
         return descriptor.containsFormat();
      }

      /**
       * Check if contains drill.
       * @return true if contains drill
       */
      @Override
      public boolean containsDrill() {
         return descriptor.containsDrill();
      }

      private TableDataDescriptor descriptor;
   }

   private int newrows = 0; // additional rows added by span
   private int lrow = -100;
   private int lval = -100;
   private BitSet spanrows = new BitSet(); // set for each row with span
   private Int2IntOpenHashMap splitmap = new Int2IntOpenHashMap(); // row -> span count
   private TableDataDescriptor cdescriptor;
   private transient XIntList rowarr;
   private XSwappableIntList rowmap;
   private int rsize;
}
