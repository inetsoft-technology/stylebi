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
package inetsoft.report.lens;

import inetsoft.report.*;
import inetsoft.report.filter.DefaultTableChangeListener;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.CancellableTableLens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * Max rows table lens returns limited table rows.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class MaxRowsTableLens extends AbstractTableLens implements TableFilter,
   CancellableTableLens
{
   /**
    * A flag indicates all rows included.
    */
   public static final int ALL = 0;

   /**
    * Constructor.
    */
   public MaxRowsTableLens() {
      super();

      max = DEFAULT_MAX_ROWS;
   }

   /**
    * Constructor.
    */
   public MaxRowsTableLens(TableLens table) {
      this(table, DEFAULT_MAX_ROWS);
   }

   /**
    * Constructor.
    */
   public MaxRowsTableLens(TableLens table, int max) {
      this();

      setTable(table);
      setMaxRows(max);
   }

   /**
    * Get the original table.
    * @return the original table of this filter.
    */
   @Override
   public TableLens getTable() {
      return table;
   }

   /**
    * Set the base table lenses.
    * @param table the specified base table lens, <tt>null</tt> is not
    * allowed.
    */
   @Override
   public void setTable(TableLens table) {
      this.table = table;
      this.table.addChangeListener(new DefaultTableChangeListener(this));
      invalidate();
   }

   /**
    * Get the max row count.
    * @return the max row count, header rows excluded.
    */
   public int getMaxRows() {
      return max;
   }

   /**
    * Set the max row count.
    * @param max the specified max row count, header rows excluded.
    */
   public void setMaxRows(int max) {
      if(max < 0) {
         return;
      }

      this.max = max + this.table.getHeaderRowCount();
      invalidate();
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
    * Invalidate the table filter forcely, and the table filter will
    * perform filtering calculation to validate itself.
    */
   @Override
   public void invalidate() {
      synchronized(rlock) {
         rcount = -1;
      }

      fireChangeEvent();
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
      synchronized(rlock) {
         if(table == null) {
            throw new RuntimeException("Table is disposed: " + this);
         }

         if(!table.moreRows(row)) {
            return false;
         }
         else if(row >= max) {
            /* move the limit message logical to VSEventUtil.addWarningText
            String limitMessage = Catalog.getCatalog().getString("common.limited.rows",
               max - 1);

            if("true".equals(table.getProperty("analysisMaxRowApplied"))) {
               limitMessage =
                  Catalog.getCatalog().getString("common.limited.analysis.rows", max - 1);
            }

            boolean messageExist = Tool.existUserMessage(limitMessage);
            Tool.addUserMessage(limitMessage);

            if(!messageExist) {
               LOG.warn(limitMessage);
            }
            */

            return false;
         }

         return true;
      }
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows. If the table is loading in background and loading
    * is not done, return the negative number of loaded rows minus 1.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      synchronized(rlock) {
         if(rcount >= 0) {
            return rcount;
         }

         int count = table.getRowCount();

         if(count < 0) {
            count = -count - 1;

            if(count < max) {
               return -count - 1;
            }
            else {
               rcount = max;
               return rcount;
            }
         }
         else {
            rcount = Math.min(count, max);
            return rcount;
         }
      }
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public int getColCount() {
      if(table == null) {
         throw new RuntimeException("Table is disposed: ");
      }

      return table.getColCount();
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.  Default is 1.
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
    * Check if is primitive.
    * @return <tt>true</tt> if is primitive, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isPrimitive(int col) {
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
      r = getBaseRowIndex(r);

      // @by stephenwebster, Fix bug1407534598791
      // Seems reasonable that if the row doesn't exist in
      // the base table, we can avoid going any further.
      if(r < 0) {
         return null;
      }
      else {
         return table.getObject(r, c);
      }
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

      return table.getBoolean(r, c);
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
      r = getBaseRowIndex(r);

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
      r = getBaseRowIndex(r);

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

      if(r >= max) {
         return -1;
      }

      return r;
   }

   /**
    * Get the base table column index corresponding to the filtered table.
    * If the column does not exist in the base table, it returns -1.
    * @param c column index in  the filtered table.
    * @return corresponding column index in the bast table.
    */
   @Override
   public int getBaseColIndex(int c) {
      return c;
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
    * Get the applied max rows.
    * @return the applied max rows.
    */
   public int getAppliedMaxRows() {
      int amax = Util.getAppliedMaxRows(table);

      if(amax <= 0) {
         int count = table.getRowCount();
         count = count < 0 ? -count - 1 : count;

         if(count > max) {
            amax = max - table.getHeaderRowCount();
         }
      }
      else if(amax > max - table.getHeaderRowCount()) {
         amax = max - table.getHeaderRowCount();
      }

      return amax;
   }

   /**
    * Check if a table is a result of timeout.
    */
   public boolean isTimeoutTable() {
      return Util.isTimeoutTable(table);
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
      return identifier == null ? table.getColumnIdentifier(getBaseColIndex(col)) : identifier;
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

   private TableLens table;       // base table
   private int max;               // max row count
   private int rcount;            // row count
   private boolean cancelled;     // cancelled flag

   private final transient Object rlock = new Object(); // table row lock

   private static final int DEFAULT_MAX_ROWS = 500000;
   private static final Logger LOG = LoggerFactory.getLogger(MaxRowsTableLens.class);
}
