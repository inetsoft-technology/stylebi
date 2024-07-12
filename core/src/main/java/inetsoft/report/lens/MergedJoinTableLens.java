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

import inetsoft.report.TableLens;
import inetsoft.report.filter.AbstractBinaryTableFilter;
import inetsoft.report.filter.DefaultTableChangeListener;
import inetsoft.report.internal.table.CancellableTableLens;

import java.awt.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * MergedJoinTableLens merges two table lenses side by side.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class MergedJoinTableLens extends AbstractBinaryTableFilter
   implements CancellableTableLens
{
   /**
    * Constructor.
    */
   public MergedJoinTableLens() {
      super();
   }

   /**
    * Constructor.
    */
   public MergedJoinTableLens(TableLens ltable, TableLens rtable) {
      this();
      setTables(ltable, rtable);
   }

   /**
    * Get the left base table lens.
    * @return the left base table lens.
    */
   @Override
   public TableLens getLeftTable() {
      return ltable;
   }

   /**
    * Get the right base table lens.
    * @return the right base table lens.
    */
   @Override
   public TableLens getRightTable() {
      return rtable;
   }

   /**
    * Set the base table lenses.
    * @param ltable the specified left base table lens, <tt>null</tt> is not
    * allowed.
    * @param rtable the specified right base table lens, <tt>null</tt> is
    * allowed.
    */
   public void setTables(TableLens ltable, TableLens rtable) {
      this.ltable = ltable;
      this.rtable = rtable;

      this.ltable.addChangeListener(new DefaultTableChangeListener(this));

      if(rtable != null) {
         this.rtable.addChangeListener(new DefaultTableChangeListener(this));
      }

      invalidate();
   }

   /**
    * Cancel the table lens and running queries if supported.
    */
   @Override
   public void cancel() {
      cancelLock.lock();

      try {
         // only mark as cancelled if not completed
         cancelled = getRowCount() < 0;

         if(ltable instanceof CancellableTableLens) {
            ((CancellableTableLens) ltable).cancel();
         }

         if(rtable instanceof CancellableTableLens) {
            ((CancellableTableLens) rtable).cancel();
         }
      }
      finally {
         cancelLock.unlock();
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
      lcols = ltable.getColCount();
      mmap.clear();

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
      if(ltable == null || rtable == null) {
         throw new RuntimeException("Table is disposed: ");
      }

      boolean result = ltable.moreRows(row);
      result = rtable.moreRows(row) || result;

      return result;
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows. If the table is loading in background and loading
    * is not done, return the negative number of loaded rows minus 1.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      int lcount = ltable.getRowCount();
      int rcount = rtable.getRowCount();
      boolean completed = lcount >= 0 && rcount >= 0;

      lcount = lcount < 0 ? -lcount - 1 : lcount;
      rcount = rcount < 0 ? -rcount - 1 : rcount;

      int count = Math.max(lcount, rcount);
      count = completed ? count : -count - 1;

      return count;
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public int getColCount() {
      if(ltable == null || rtable == null) {
         throw new RuntimeException("Table is disposed: " + this);
      }

      return ltable.getColCount() + rtable.getColCount();
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.  Default is 1.
    */
   @Override
   public int getHeaderRowCount() {
      return Math.min(ltable.getHeaderRowCount(), rtable.getHeaderRowCount());
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      return ltable.getHeaderColCount();
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as trailer rows.
    * @return number of header rows.
    */
   @Override
   public int getTrailerRowCount() {
      return ltable.getTrailerRowCount();
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as trailer columns.
    */
   @Override
   public int getTrailerColCount() {
      return ltable.getTrailerColCount();
   }

   /**
    * Check if is primitive.
    * @return <tt>true</tt> if is primitive, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isPrimitive(int col) {
      return col < lcols ? ltable.isPrimitive(col) :
         rtable.isPrimitive(col - lcols);
   }

   /**
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isNull(int r, int c) {
      boolean lmore = ltable.moreRows(r);
      boolean rmore = rtable.moreRows(r);

      TableLens table = c < lcols ? ltable : rtable;
      boolean more = c < lcols ? lmore : rmore;
      int col = c < lcols ? c : c - lcols;

      return more ? table.isNull(r, col) : true;
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      boolean lmore = ltable.moreRows(r);
      boolean rmore = rtable.moreRows(r);

      if(!lmore && !rmore) {
         return null;
      }

      TableLens table = c < lcols ? ltable : rtable;
      boolean more = c < lcols ? lmore : rmore;
      int col = c < lcols ? c : c - lcols;

      return more ? table.getObject(r, col) : null;
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the double value in the specified row.
    */
   @Override
   public final double getDouble(int r, int c) {
      boolean lmore = ltable.moreRows(r);
      boolean rmore = rtable.moreRows(r);

      TableLens table = c < lcols ? ltable : rtable;
      boolean more = c < lcols ? lmore : rmore;
      int col = c < lcols ? c : c - lcols;

      return more ? table.getDouble(r, col) : 0D;
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the float value in the specified row.
    */
   @Override
   public final float getFloat(int r, int c) {
      boolean lmore = ltable.moreRows(r);
      boolean rmore = rtable.moreRows(r);

      TableLens table = c < lcols ? ltable : rtable;
      boolean more = c < lcols ? lmore : rmore;
      int col = c < lcols ? c : c - lcols;

      return more ? table.getFloat(r, col) : 0F;
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the long value in the specified row.
    */
   @Override
   public final long getLong(int r, int c) {
      boolean lmore = ltable.moreRows(r);
      boolean rmore = rtable.moreRows(r);

      TableLens table = c < lcols ? ltable : rtable;
      boolean more = c < lcols ? lmore : rmore;
      int col = c < lcols ? c : c - lcols;

      return more ? table.getLong(r, col) : 0L;
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the int value in the specified row.
    */
   @Override
   public final int getInt(int r, int c) {
      boolean lmore = ltable.moreRows(r);
      boolean rmore = rtable.moreRows(r);

      TableLens table = c < lcols ? ltable : rtable;
      boolean more = c < lcols ? lmore : rmore;
      int col = c < lcols ? c : c - lcols;

      return more ? table.getInt(r, col) : 0;
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the short value in the specified row.
    */
   @Override
   public final short getShort(int r, int c) {
      boolean lmore = ltable.moreRows(r);
      boolean rmore = rtable.moreRows(r);

      TableLens table = c < lcols ? ltable : rtable;
      boolean more = c < lcols ? lmore : rmore;
      int col = c < lcols ? c : c - lcols;

      return more ? table.getShort(r, col) : 0;
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the byte value in the specified row.
    */
   @Override
   public final byte getByte(int r, int c) {
      boolean lmore = ltable.moreRows(r);
      boolean rmore = rtable.moreRows(r);

      TableLens table = c < lcols ? ltable : rtable;
      boolean more = c < lcols ? lmore : rmore;
      int col = c < lcols ? c : c - lcols;

      return more ? table.getByte(r, col) : 0;
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the boolean value in the specified row.
    */
   @Override
   public final boolean getBoolean(int r, int c) {
      boolean lmore = ltable.moreRows(r);
      boolean rmore = rtable.moreRows(r);

      TableLens table = c < lcols ? ltable : rtable;
      boolean more = c < lcols ? lmore : rmore;
      int col = c < lcols ? c : c - lcols;

      return more ? table.getBoolean(r, col) : false;
   }

   /**
    * Get the current column content type.
    * @param c column number.
    * @return column type.
    */
   @Override
   public Class getColType(int c) {
      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;

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
      boolean lmore = ltable.moreRows(r);
      boolean rmore = rtable.moreRows(r);

      if(!lmore && !rmore) {
         return;
      }

      TableLens table = c < lcols ? ltable : rtable;
      boolean more = c < lcols ? lmore : rmore;
      int col = c < lcols ? c : c - lcols;

      if(!more) {
         throw new RuntimeException("Can not set object to " + r + "," + c);
      }

      table.setObject(r, col, v);
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
      boolean lmore = ltable.moreRows(r);
      boolean rmore = rtable.moreRows(r);

      if(!lmore && !rmore) {
         return -1;
      }

      TableLens table = lmore ? ltable : rtable;
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
   public int getColWidth(int c) {
      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;

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
      boolean lmore = ltable.moreRows(r);
      boolean rmore = rtable.moreRows(r);

      if(!lmore && !rmore) {
         return null;
      }

      TableLens table = c < lcols ? ltable : rtable;
      boolean more = c < lcols ? lmore : rmore;
      int col = c < lcols ? c : c - lcols;

      return more ? table.getRowBorderColor(r, col) : Color.black;
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      boolean lmore = ltable.moreRows(r);
      boolean rmore = rtable.moreRows(r);

      if(!lmore && !rmore) {
         return null;
      }

      TableLens table = c < lcols ? ltable : rtable;
      boolean more = c < lcols ? lmore : rmore;
      int col = c < lcols ? c : c - lcols;

      return more ? table.getColBorderColor(r, col) : Color.black;
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
      boolean lmore = ltable.moreRows(r);
      boolean rmore = rtable.moreRows(r);

      if(!lmore && !rmore) {
         return -1;
      }

      TableLens table = c < lcols ? ltable : rtable;
      boolean more = c < lcols ? lmore : rmore;
      int col = c < lcols ? c : c - lcols;

      return more ? table.getRowBorder(r, col) : THIN_LINE;
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
      boolean lmore = ltable.moreRows(r);
      boolean rmore = rtable.moreRows(r);

      if(!lmore && !rmore) {
         return -1;
      }

      TableLens table = c < lcols ? ltable : rtable;
      boolean more = c < lcols ? lmore : rmore;
      int col = c < lcols ? c : c - lcols;

      return more ? table.getColBorder(r, col) : THIN_LINE;
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      boolean lmore = ltable.moreRows(r);
      boolean rmore = rtable.moreRows(r);

      if(!lmore && !rmore) {
         return null;
      }

      TableLens table = c < lcols ? ltable : rtable;
      boolean more = c < lcols ? lmore : rmore;
      int col = c < lcols ? c : c - lcols;

      return more ? table.getInsets(r, col) : null;
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
      boolean lmore = ltable.moreRows(r);
      boolean rmore = rtable.moreRows(r);

      if(!lmore && !rmore) {
         return null;
      }

      TableLens table = c < lcols ? ltable : rtable;
      boolean more = c < lcols ? lmore : rmore;
      int col = c < lcols ? c : c - lcols;

      return more ? table.getSpan(r, col) : null;
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      boolean lmore = ltable.moreRows(r);
      boolean rmore = rtable.moreRows(r);

      if(!lmore && !rmore) {
         return -1;
      }

      TableLens table = c < lcols ? ltable : rtable;
      boolean more = c < lcols ? lmore : rmore;
      int col = c < lcols ? c : c - lcols;

      return more ? table.getAlignment(r, col) : (H_LEFT | V_CENTER);
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      boolean lmore = ltable.moreRows(r);
      boolean rmore = rtable.moreRows(r);

      if(!lmore && !rmore) {
         return null;
      }

      TableLens table = c < lcols ? ltable : rtable;
      boolean more = c < lcols ? lmore : rmore;
      int col = c < lcols ? c : c - lcols;

      return more ? table.getFont(r, col) : null;
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
      boolean lmore = ltable.moreRows(r);
      boolean rmore = rtable.moreRows(r);

      if(!lmore && !rmore) {
         return false;
      }

      TableLens table = c < lcols ? ltable : rtable;
      boolean more = c < lcols ? lmore : rmore;
      int col = c < lcols ? c : c - lcols;

      return more ? table.isLineWrap(r, col) : true;
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
      boolean lmore = ltable.moreRows(r);
      boolean rmore = rtable.moreRows(r);

      if(!lmore && !rmore) {
         return null;
      }

      TableLens table = c < lcols ? ltable : rtable;
      boolean more = c < lcols ? lmore : rmore;
      int col = c < lcols ? c : c - lcols;

      return more ? table.getForeground(r, col) : null;
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
      boolean lmore = ltable.moreRows(r);
      boolean rmore = rtable.moreRows(r);

      if(!lmore && !rmore) {
         return null;
      }

      TableLens table = c < lcols ? ltable : rtable;
      boolean more = c < lcols ? lmore : rmore;
      int col = c < lcols ? c : c - lcols;

      return more ? table.getBackground(r, col) : null;
   }

   /**
    * Finalize the merged join table lens.
    */
   @Override
   protected void finalize() throws Throwable {
      super.finalize();
      dispose();
   }

   /**
    * Dispose the merged join lens.
    */
   @Override
   public synchronized void dispose() {
      if(ltable != null) {
         ltable.dispose();
         ltable = null;
      }

      if(rtable != null) {
         rtable.dispose();
         rtable = null;
      }
   }

   private int lcols;         // left table columns
   private TableLens ltable;  // left table
   private TableLens rtable;  // right table
   private volatile boolean cancelled; // cancelled flag
   private final Lock cancelLock = new ReentrantLock();
}
