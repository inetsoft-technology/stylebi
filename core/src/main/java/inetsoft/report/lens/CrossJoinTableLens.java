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

import inetsoft.report.TableLens;
import inetsoft.report.filter.AbstractBinaryTableFilter;
import inetsoft.report.filter.DefaultTableChangeListener;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.awt.*;
import java.math.BigInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * CrossJoinTableLens cross joins two table lenses.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class CrossJoinTableLens extends AbstractBinaryTableFilter implements CancellableTableLens {
   /**
    * Constructor.
    */
   public CrossJoinTableLens() {
      super();

      String str = SreeEnv.getProperty("join.table.maxrows");

      if(!StringUtils.isEmpty(str)) {
         try {
            maxRows = Integer.parseInt(str);
         }
         catch(NumberFormatException ignore) {
         }
      }
   }

   /**
    * Constructor.
    */
   public CrossJoinTableLens(TableLens ltable, TableLens rtable) {
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

      lhrows = ltable.getHeaderRowCount();
      rhrows = rtable.getHeaderRowCount();
      hrows = Math.min(lhrows, rhrows);
      lcols = ltable.getColCount();
      ltable.moreRows(TableLens.EOT);
      rtable.moreRows(TableLens.EOT);

      String crossJoinMaxCell = SreeEnv.getProperty("crossJoin.maxCellCount");
      BigInteger maxCell = BigInteger.valueOf(50000000);

      if(crossJoinMaxCell != null) {
         try {
            maxCell = new BigInteger(crossJoinMaxCell);
         }
         catch(Exception ignore) {
         }

         if(maxCell.compareTo(BigInteger.valueOf(0)) <= 0) {
            maxCell = BigInteger.valueOf(50000000);
         }
      }

      if(ltable.getRowCount() > 0 && rtable.getRowCount() > 0) {
         BigInteger leftRowCount = BigInteger.valueOf(ltable.getRowCount());
         BigInteger rightRowCount = BigInteger.valueOf(rtable.getRowCount());
         BigInteger colCount = BigInteger.valueOf(ltable.getColCount() + rtable.getColCount());

         if(leftRowCount.multiply(rightRowCount).multiply(colCount).compareTo(maxCell) > 0) {
            throw new CrossJoinCellCountBeyondLimitException(
               Catalog.getCatalog().getString("composer.ws.crossJoin.limitedExceptionMessage"));
         }
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
         cancelled = !isCompleted();

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
   public synchronized void invalidate() {
      if(lthread != null) {
         lthread.dispose();
         lthread = null;
      }

      if(rthread != null) {
         rthread.dispose();
         rthread = null;
      }

      lcompleted = false;
      rcompleted = false;

      lrows = 0;
      rrows = 0;

      mtable = null;
      mrows = 0;

      mmap.clear();

      fireChangeEvent();
   }

   /**
    * Validate the cross join lens.
    */
   private synchronized void validate() {
      if(lthread != null) {
         return;
      }

      lthread = new WaitingThread(true);
      lthread.start();

      rthread = new WaitingThread(false);
      rthread.start();
   }

   /**
    * Update row count.
    * @param left <tt>true</tt> left table, <tt>false</tt> right table.
    * @param count the specified row count.
    * @param over <tt>true</tt> if over, <tt>false</tt> otherwise.
    */
   private synchronized void updateRowCount(boolean left, int count,
                                            boolean over) {
      if(left) {
         lrows = count;
      }
      else {
         rrows = count;
      }

      if(over) {
         if(left) {
            lcompleted = true;

            if(mtable == null) {
               mtable = ltable;
               mrows = count;
            }
         }
         else {
            rcompleted = true;

            if(mtable == null) {
               mtable = rtable;
               mrows = count;
            }
         }
      }

      if(lcompleted && rcompleted) {
         notifyAll();
      }
   }

   /**
    * Get row count internally.
    * @return the available row count.
    */
   private int getRowCount0() {
      if(mtable == null) {
         return hrows;
      }

      int cnt = (lrows * rrows) + hrows;

      if(cnt - hrows >= maxRows) {
         if(!maxAlerted) {
            String message = Catalog.getCatalog().getString("join.table.limited", maxRows);
            boolean messageExist = Tool.existUserMessage(message);
            Tool.addUserMessage(message);

            if(!messageExist) {
               LOG.info(message);
            }

            if(!"true".equals(SreeEnv.getProperty("always.warn.joinMaxRows"))) {
               maxAlerted = true;
            }
         }

         cnt = maxRows + hrows;
      }

      return cnt;
   }

   /**
    * Get the left base row.
    * @param row the specified row index.
    * @return the left base row of the row index.
    */
   private int getLeftBaseRow(int row) {
      if(row < hrows) {
         return row;
      }

      row -= hrows;

      if(mtable == ltable) {
         return (row % mrows) + lhrows;
      }
      else {
         return (row / mrows) + lhrows;
      }
   }

   /**
    * Get the right base row.
    * @param row the specified row index.
    * @return the right base row of the row index.
    */
   private int getRightBaseRow(int row) {
      if(row < hrows) {
         return row;
      }

      row -= hrows;

      if(mtable == rtable) {
         return (row % mrows) + rhrows;
      }
      else {
         return (row / mrows) + rhrows;
      }
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
   public synchronized boolean moreRows(int row) {
      validate();

      if(row < hrows) {
         return true;
      }

      while(row >= getRowCount0() && !isCompleted() && !disposed && !cancelled) {
         try {
            wait(500);
            validate();
         }
         catch(InterruptedException ex) {
            // ignore it
         }
      }

      return row < getRowCount0();
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows. If the table is loading in background and loading
    * is not done, return the negative number of loaded rows minus 1.
    * @return number of rows in table.
    */
   @Override
   public synchronized int getRowCount() {
      validate();

      int count = getRowCount0();

      return isCompleted() ? count : -count - 1;
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public int getColCount() {
      return lcols + rtable.getColCount();
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.  Default is 1.
    */
   @Override
   public int getHeaderRowCount() {
      return hrows;
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
      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;
      r = c < lcols ? getLeftBaseRow(r) : getRightBaseRow(r);

      return table.isNull(r, col);
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;
      r = c < lcols ? getLeftBaseRow(r) : getRightBaseRow(r);

      return table.getObject(r, col);
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the double value in the specified row.
    */
   @Override
   public final double getDouble(int r, int c) {
      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;
      r = c < lcols ? getLeftBaseRow(r) : getRightBaseRow(r);

      return table.getDouble(r, col);
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the float value in the specified row.
    */
   @Override
   public final float getFloat(int r, int c) {
      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;
      r = c < lcols ? getLeftBaseRow(r) : getRightBaseRow(r);

      return table.getFloat(r, col);
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the long value in the specified row.
    */
   @Override
   public final long getLong(int r, int c) {
      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;
      r = c < lcols ? getLeftBaseRow(r) : getRightBaseRow(r);

      return table.getLong(r, col);
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the int value in the specified row.
    */
   @Override
   public final int getInt(int r, int c) {
      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;
      r = c < lcols ? getLeftBaseRow(r) : getRightBaseRow(r);

      return table.getInt(r, col);
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the short value in the specified row.
    */
   @Override
   public final short getShort(int r, int c) {
      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;
      r = c < lcols ? getLeftBaseRow(r) : getRightBaseRow(r);

      return table.getShort(r, col);
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the byte value in the specified row.
    */
   @Override
   public final byte getByte(int r, int c) {
      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;
      r = c < lcols ? getLeftBaseRow(r) : getRightBaseRow(r);

      return table.getByte(r, col);
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the boolean value in the specified row.
    */
   @Override
   public final boolean getBoolean(int r, int c) {
      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;
      r = c < lcols ? getLeftBaseRow(r) : getRightBaseRow(r);

      return table.getBoolean(r, col);
   }

   /**
    * Get the current column content type.
    * @param col column number.
    * @return column type.
    */
   @Override
   public Class getColType(int col) {
      TableLens table = col < lcols ? ltable : rtable;
      col = col < lcols ? col : col - lcols;

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
      if(!moreRows(r)) {
         return;
      }

      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;
      r = c < lcols ? getLeftBaseRow(r) : getRightBaseRow(r);

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
      if(!moreRows(r)) {
         return -1;
      }

      r = getLeftBaseRow(r);
      return ltable.getRowHeight(r);
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
      return col < lcols ?
         ltable.getColWidth(col) :
         rtable.getColWidth(col - lcols);
   }

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getRowBorderColor(int r, int c) {
      if(!moreRows(r)) {
         return null;
      }

      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;
      r = c < lcols ? getLeftBaseRow(r) : getRightBaseRow(r);

      return table.getRowBorderColor(r, col);
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      if(!moreRows(r)) {
         return null;
      }

      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;
      r = c < lcols ? getLeftBaseRow(r) : getRightBaseRow(r);

      return table.getColBorderColor(r, col);
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
      if(!moreRows(r)) {
         return -1;
      }

      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;
      r = c < lcols ? getLeftBaseRow(r) : getRightBaseRow(r);

      return table.getRowBorder(r, col);
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
      if(!moreRows(r)) {
         return -1;
      }

      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;
      r = c < lcols ? getLeftBaseRow(r) : getRightBaseRow(r);

      return table.getColBorder(r, col);
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      if(!moreRows(r)) {
         return null;
      }

      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;
      r = c < lcols ? getLeftBaseRow(r) : getRightBaseRow(r);

      return table.getInsets(r, col);
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
      if(!moreRows(r)) {
         return null;
      }

      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;
      r = c < lcols ? getLeftBaseRow(r) : getRightBaseRow(r);

      return table.getSpan(r, col);
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      if(!moreRows(r)) {
         return -1;
      }

      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;
      r = c < lcols ? getLeftBaseRow(r) : getRightBaseRow(r);

      return table.getAlignment(r, col);
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      if(!moreRows(r)) {
         return null;
      }

      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;
      r = c < lcols ? getLeftBaseRow(r) : getRightBaseRow(r);

      return table.getFont(r, col);
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
      if(!moreRows(r)) {
         return false;
      }

      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;
      r = c < lcols ? getLeftBaseRow(r) : getRightBaseRow(r);

      return table.isLineWrap(r, col);
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
      if(!moreRows(r)) {
         return null;
      }

      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;
      r = c < lcols ? getLeftBaseRow(r) : getRightBaseRow(r);

      return table.getForeground(r, col);
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
      if(!moreRows(r)) {
         return null;
      }

      TableLens table = c < lcols ? ltable : rtable;
      int col = c < lcols ? c : c - lcols;
      r = c < lcols ? getLeftBaseRow(r) : getRightBaseRow(r);

      return table.getBackground(r, col);
   }

   /**
    * Finalize the cross join lens.
    */
   @Override
   protected void finalize() throws Throwable {
      super.finalize();
      dispose();
   }

   /**
    * Dispose the cross join lens.
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

      disposed = true;
   }

   /**
    * Check if the process is completed.
    * @return <tt>true</tt> if completed, <tt>false</tt> otherwise.
    */
   private synchronized boolean isCompleted() {
      return lcompleted && rcompleted;
   }

   /**
    * Waiting thread.
    */
   private class WaitingThread extends GroupedThread {
      public WaitingThread(boolean left) {
         super();

         this.left = left;
      }

      @Override
      public void dispose() {
         this.disposed = true;
      }

      @Override
      protected void doRun() {
         TableLens table = left ? ltable : rtable;
         int hrows = left? lhrows : rhrows;

         for(int i = hrows; table.moreRows(i); i += 100) {
            if(this.disposed || CrossJoinTableLens.this.disposed || cancelled) {
               break;
            }

            if(!this.disposed) {
               updateRowCount(left, i - hrows + 1, false);
            }
         }

         if(!this.disposed && !CrossJoinTableLens.this.disposed && !cancelled) {
            int count = table.getRowCount() - hrows;
            count = count < 0 ? 0 : count;

            updateRowCount(left, count, true);
         }
      }

      private boolean left;
      private boolean disposed;
   }

   private TableLens ltable;       // left table
   private TableLens rtable;       // right table
   private int lhrows;             // left table header rows
   private int rhrows;             // right table header rows
   private int hrows;              // header rows
   private int lrows;              // left table rows
   private int rrows;              // right table rows
   private int mrows;              // main table rows
   private TableLens mtable;       // main table
   private transient WaitingThread lthread;  // left table thread
   private transient WaitingThread rthread;  // right table thread
   private boolean lcompleted;     // left completed flag
   private boolean rcompleted;     // right completed flag
   private int lcols;              // left col count
   private volatile boolean cancelled;      // cancelled flag
   private final Lock cancelLock = new ReentrantLock();
   private boolean disposed;       // disposed flag
   private int maxRows = Integer.MAX_VALUE;
   private transient boolean maxAlerted = false;

   private static final Logger LOG =
      LoggerFactory.getLogger(CrossJoinTableLens.class);
}
