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
package inetsoft.report.lens;

import inetsoft.report.*;
import inetsoft.report.filter.*;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.util.Tool;
import inetsoft.util.swap.XSwappableIntList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * RankingTableLens performs one ranking operation on a table lens.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class RankingTableLens extends AbstractTableLens
   implements TableFilter, SortedTable, CancellableTableLens
{
   /**
    * Constructor.
    */
   public RankingTableLens() {
      super();
      kept = true;
   }

   /**
    * Constructor.
    * @param table the specified base table lens.
    */
   public RankingTableLens(TableLens table) {
      this();
      setTable(table);
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
    * Get the ranking column.
    * @return the ranking column.
    */
   public int getRankingColumn() {
      return rcol;
   }

   /**
    * Set the ranking column.
    * @param rcol the specified ranking column.
    */
   public void setRankingColumn(int rcol) {
      this.rcol = rcol;
      invalidate();
   }

   /**
    * Get the ranking n.
    * @return the ranking n.
    */
   public int getRankingN() {
      return n;
   }

   /**
    * Set the ranking n.
    * @param n the specified ranking n.
    */
   public void setRankingN(int n) {
      this.n = n;

      invalidate();
   }

   /**
    * Check if is top ranking.
    * @return <tt>true</tt> if top ranking, <tt>false</tt> bottom ranking.
    */
   public boolean isTopRanking() {
      return top;
   }

   /**
    * Set the top ranking option.
    * @param top <tt>true</tt> if top ranking, <tt>false</tt> bottom ranking.
    */
   public void setTopRanking(boolean top) {
      this.top = top;
   }

   /**
    * Check if keeps the equal rows.
    * @return <tt>true</tt> if keeps the equal rows, <tt>false</tt> otherwise.
    */
   public boolean isEqualityKept() {
      return kept;
   }

   /**
    * Set the keep equality option. If true and there are more items equal to
    * the last item on the list, those items are kept too.
    * @param kept <tt>true</tt> if keeps the equal rows, <tt>false</tt>
    * otherwise.
    */
   public void setEqualityKept(boolean kept) {
      this.kept = kept;

      invalidate();
   }

   /**
    * Get the columns that the table is sorted on.
    * @return sort columns.
    */
   @Override
   public int[] getSortCols() {
      return new int[] {rcol};
   }

   /**
    * Get the sorting order of the sorting columns.
    */
   @Override
   public boolean[] getOrders() {
      return new boolean[] {!top};
   }

   /**
    * Set the comparer for a sorting column.
    * @param col table column index.
    * @param comp comparer.
    */
   @Override
   public void setComparer(int col, Comparer comp) {
      if(col == rcol) {
         this.comp = comp;

         invalidate();
      }
   }

   /**
    * Get the comparer for a sorting column.
    * @param col the specified table column index.
    */
   @Override
   public Comparer getComparer(int col) {
      Comparer comparer = col == rcol ? comp : null;

      if(comparer == null && table instanceof SortedTable) {
         SortedTable stable = (SortedTable) table;
         int[] cols = stable.getSortCols();

         for(int i = 0; cols != null && i < cols.length; i++) {
            if(cols[i] == col) {
               comparer = stable.getComparer(i);
               break;
            }
         }
      }

      if(comparer instanceof SortOrder) {
         SortOrder dcomp = (SortOrder) comparer;
         dcomp = (SortOrder) dcomp.clone();
         dcomp.setAsc(true);
         comparer = dcomp;
      }

      if(comparer instanceof DefaultComparer) {
         DefaultComparer dcomp = (DefaultComparer) comparer;
         dcomp = (DefaultComparer) dcomp.clone();
         dcomp.setNegate(false);
         comparer = dcomp;
      }

      return comparer;
   }

   /**
    * Invalidate the table filter forcely, and the table filter will
    * perform filtering calculation to validate itself.
    */
   @Override
   public synchronized void invalidate() {
      if(rows != null) {
         rows.dispose();
         rows = null;
      }

      hrows = table.getHeaderRowCount();
      completed = false;
      fireChangeEvent();
   }

   /**
    * Dispose the ranking table lens.
    */
   @Override
   public synchronized void dispose() {
      if(rows != null) {
         rows.dispose();
         rows = null;
      }

      if(table != null) {
         table.dispose();
         table = null;
      }
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
    * Cancel the table lens and running queries if supported.
    */
   @Override
   public void cancel() {
      cancelLock.lock();

      try {
         cancelled = !completed;

         if(table instanceof CancellableTableLens) {
            ((CancellableTableLens) table).cancel();
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
    * Validate the ranking table lens.
    */
   private synchronized void validate() {
      if(rows != null) {
         return;
      }

      List<Integer> list = new ArrayList<>();

      for(int i = hrows; table.moreRows(i); i++) {
         list.add(i);
      }

      int size = 0;

      try {
         this.comp = getComparer(rcol);
         Comparator<Integer> comparator = new RankingComparator();
         list.sort(comparator);
         size = Math.min(list.size(), n);

         if(isEqualityKept() && size > 0) {
            Integer last = list.get(size - 1);

            for(int i = size; i < list.size(); i++) {
               if(comparator.compare(list.get(i), last) != 0) {
                  break;
               }

               size++;
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to sort list", ex);
      }

      // use swappable int list to save memory
      rows = new XSwappableIntList();

      for(int i = 0; i < size; i++) {
         rows.add(list.get(i));
      }

      rows.complete();
      completed = true;

      // notify waiting consumers
      notifyAll();
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
      if(row < hrows) {
         return true;
      }

      validate();

      return rows != null && row - hrows < rows.size();
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows. If the table is loading in background and loading
    * is not done, return the negative number of loaded rows minus 1.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      validate();

      return completed ? rows.size() + hrows : -rows.size() - hrows - 1;
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

      return table.getObject(r, c);
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

      if(!moreRows(r)) {
         return -1;
      }

      if(r < hrows) {
         return r;
      }

      return rows.get(r - hrows);
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
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      return table.getDescriptor();
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

   /**
    * Ranking comparator.
    */
   private class RankingComparator implements Comparator<Integer> {
      /**
       * Compare two values.
       * @param val1 the specified value a.
       * @param val2 the specified value b.
       * @return the compare result.
       */
      @Override
      public int compare(Integer val1, Integer val2) {
         int r1 = val1;
         int r2 = val2;

         Object obj1 = table.getObject(r1, rcol);
         Object obj2 = table.getObject(r2, rcol);
         int val = compare(obj1, obj2, comp);

         return top ? -val : val;
      }

      /**
       * Compare two values.
       * @param val1 the specified value a.
       * @param val2 the specified value b.
       * @param comparer the specified comparer.
       * @return the compare result.
       */
      private int compare(Object val1, Object val2, Comparer comparer) {
         // contains comparer?
         if(comparer != null) {
            return comparer.compare(val1, val2);
         }
         // doesn't contain comparer?
         else {
            return Tool.compare(val1, val2);
         }
      }
   }

   private int rcol;              // ranking column
   private int n;                 // ranking n
   private boolean top;           // ranking top flag
   private boolean kept;          // keep equal flag
   private TableLens table;       // the base table
   private int hrows;             // header row count
   private XSwappableIntList rows;// rows
   private boolean completed;     // completed flag
   private volatile boolean cancelled;     // cancelled flag
   private Lock cancelLock = new ReentrantLock();
   private Comparer comp;         // comparer

   private static final Logger LOG =
      LoggerFactory.getLogger(RankingTableLens.class);
}
