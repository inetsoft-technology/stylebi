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
import inetsoft.report.filter.*;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.util.ThreadPool;
import inetsoft.util.Tool;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.profile.ProfileUtils;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.swap.XSwappableIntList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Distinct table lens filters out duplicate table rows. This class sorts the
 * table and extract the distinct rows.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class DistinctTableLens extends AbstractTableLens
   implements TableFilter, CancellableTableLens, SortedTable
{
   /**
    * Constructor.
    */
   public DistinctTableLens() {
      super();
   }

   /**
    * Constructor.
    */
   public DistinctTableLens(TableLens table) {
      this(table, null);
   }

   /**
    * Constructor.
    */
   public DistinctTableLens(TableLens table, int[] cols) {
      this(table, cols, false);
   }

   /**
    * Constructor.
    */
   public DistinctTableLens(TableLens table, int[] cols, int[] dimTypes,
                            Comparator[] comps) {
      this(table, cols, false, dimTypes, comps);
   }

   /**
    * Constructor.
    */
   public DistinctTableLens(TableLens table, int[] cols, boolean stable) {
      this(table, cols, stable, null, null);
   }

   /**
    * Constructor.
    */
   public DistinctTableLens(TableLens table, int[] cols, boolean stable,
                            int[] dimTypes, Comparator[] comps) {
      this();

      this.stable = stable;
      this.dimTypes = dimTypes;
      this.comps = comps;

      if(cols == null) {
         cols = new int[table.getColCount()];

         for(int i = 0; i < cols.length; i++) {
            cols[i] = i;
         }
      }

      setTable(table);
      setCols(cols);
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
    * Get the distinct columns.
    * @return the distinct columns of this filter.
    */
   public int[] getCols() {
      return cols;
   }

   /**
    * Set the distinct columns.
    * @param cols the specified distinct columns.
    */
   public void setCols(int[] cols) {
      this.cols = cols;
      invalidate();
   }

   /**
    * Get the columns that the table is sorted on.
    * @return sort columns.
    */
   @Override
   public int[] getSortCols() {
      return getCols();
   }

   /**
    * Get the sorting order of the sorting columns.
    */
   @Override
   public boolean[] getOrders() {
      boolean[] arr = new boolean[cols.length];
      Arrays.fill(arr, true);
      return arr;
   }

   /**
    * Set the comparer for a sorting column.
    * @param col table column index.
    * @param comp comparer.
    */
   @Override
   public void setComparer(int col, Comparer comp) {
      throw new RuntimeException("Operation not supported!");
   }

   /**
    * Get the comparer for a sorting column.
    * @param col the specified table column index.
    */
   @Override
   public Comparer getComparer(int col) {
      return null;
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
    * Invalidate the table filter forcely, and the table filter will
    * perform filtering calculation to validate itself.
    */
   @Override
   public synchronized void invalidate() {
      if(rows != null) {
         rows.dispose();
         rows = null;
      }

      rows = new XSwappableIntList();

      if(table != null) {
         for(int i = 0; i < table.getHeaderRowCount() && table.moreRows(i); i++)
         {
            rows.add(i);
         }

         // notify waiting consumers
         notifyAll();
      }

      completed = false;
      validated = false;
      fireChangeEvent();
   }

   /**
    * Validate the distinct table lens.
    */
   private void validate() throws Exception {
      if(validated) {
         return;
      }

      validated = true;

      boolean inExec = JavaScriptEngine.getExecScriptable() != null;

      // if this is called from JavaScriptEngine.exec(), the script engine is already
      // locked. running process() in a separate thread would create a deadlock
      // waiting forever for the JavaScriptEngine lock to be released.

      if(inExec) {
         validate0();
      }
      // concurrent process
      else {
         Runnable runnable = new ThreadPool.AbstractContextRunnable() {
            @Override
            public void run() {
               validate0();
            }
         };
         ThreadPool.addOnDemand(runnable);
      }
   }

   private void validate0() {
      if(cols.length == 1 || stable) {
         hashDistinct();
      }
      else {
         sortDistinct();
      }
   }

   /**
    * Get max row count.
    */
   private int getMaxRowCount() {
      String prop = SreeEnv.getProperty("DistinctTableLens.maxrow");
      return prop != null ? Integer.parseInt(prop) : Integer.MAX_VALUE;
   }

   /**
    * Find distinct rows through a hash map.
    */
   private void hashDistinct() {
      try {
         Set map = new ObjectOpenHashSet();

         for(int r = table.getHeaderRowCount(), max = getMaxRowCount();
             table.moreRows(r) && r < max; r++) {
            Object val = getKey(table, r);

            if(map.contains(val)) {
               continue;
            }

            if(rows == null || rows.isDisposed() || cancelled) {
               break;
            }

            map.add(val);
            rows.add(r);

            if(rows != null && rows.size() % 20 == 0) {
               synchronized(DistinctTableLens.this) {
                  // notify waiting consumers
                  DistinctTableLens.this.notifyAll();
               }
            }
         }
      }
      finally {
         synchronized(DistinctTableLens.this) {
            completed = true;

            if(rows != null) {
               rows.complete();
            }

            // notify waiting consumers
            DistinctTableLens.this.notifyAll();
         }
      }
   }

   /**
    * Get the hash key.
    */
   private Object getKey(TableLens tbl, int row) {
      return new Tuple(tbl, row);
   }

   /**
    * Find distinct rows through sorting.
    */
   private void sortDistinct() {
      try {
         // for Feature #26586, add ui processing time record.

         ProfileUtils.addExecutionBreakDownRecord(getReportName(),
            ExecutionBreakDownRecord.POST_PROCESSING_CYCLE, args -> {
               sortDistinct0();
            });

         //sortDistinct0();
      }
      catch(Exception ex) {
         LOG.error("Failed to process sort distinct", ex);
      }
   }

   /**
    * Find distinct rows through sorting.
    */
   private void sortDistinct0() {
      try {
         TableFilter sorted = createSortedTable();
         Object[] row = null;
         boolean distinct = sorted instanceof SortFilter && ((SortFilter) sorted).isDistinct();

         for(int r = sorted.getHeaderRowCount(); sorted.moreRows(r); r++) {
            boolean eq = !distinct;

            if(row == null) {
               eq = false;
               row = new Object[cols.length];
            }

            if(!distinct) {
               for(int i = 0; i < row.length; i++) {
                  Object val = sorted.getObject(r, cols[i]);

                  if(eq) {
                     eq = Tool.equals(val, row[i]);
                  }

                  row[i] = val;
               }
            }

            if(eq) {
               continue;
            }

            if(rows == null || rows.isDisposed() || cancelled) {
               break;
            }

            int baseIdx = (sorted == table) ? r : sorted.getBaseRowIndex(r);
            rows.add(baseIdx);

            if(rows != null && rows.size() % 20 == 0) {
               synchronized(DistinctTableLens.this) {
                  // notify waiting consumers
                  DistinctTableLens.this.notifyAll();
               }
            }
         }
      }
      finally {
         synchronized(DistinctTableLens.this) {
            completed = true;

            if(rows != null) {
               rows.complete();
            }

            // notify waiting consumers
            DistinctTableLens.this.notifyAll();
         }
      }
   }

   /**
    * Create a table sorted on the distinct columns.
    */
   private TableFilter createSortedTable() {
      // reuse the base table if it's already sorted
      if(table instanceof SortedTable) {
         int[] ocols = ((SortedTable) table).getSortCols();
         boolean compatible = true;
         int lastidx = -1;

         // check if the columns are sorted in the same order
         for(int i = 0; i < cols.length; i++) {
            int idx = -1;

            for(int j = 0; ocols != null && j < ocols.length; j++) {
               if(ocols[j] == cols[i]) {
                  idx = j;
                  break;
               }
            }

            if(idx <= lastidx) {
               compatible = false;
               break;
            }

            lastidx = idx;
         }

         if(compatible) {
            return (TableFilter) table;
         }
      }

      SortFilter sfilter = new SortFilter(table, cols);
      sfilter.setDistinct(true);

      if(dimTypes != null) {
         for(int i = 0; i < dimTypes.length; i++) {
            sfilter.setComparer(cols[i], new DimensionComparer(
                                   dimTypes[i], comps == null ? null : comps[i]));
         }
      }

      return sfilter;
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
      try {
         while((rows == null || row >= rows.size()) && !completed) {
            try {
               validate();
               wait(500);
            }
            catch(InterruptedException ex) {
               // ignore it
            }
         }

         return rows != null && row < rows.size();
      }
      catch(Exception ex) {
         completed = true;
         LOG.error("Failed to validate table rows when checking " +
            "if row is available: " + row, ex);
         return false;
      }
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows. If the table is loading in background and loading
    * is not done, return the negative number of loaded rows minus 1.
    * @return number of rows in table.
    */
   @Override
   public synchronized int getRowCount() {
      try {
         validate();

         return completed ? rows.size() : - rows.size() - 1;
      }
      catch(Exception ex) {
         completed = true;
         LOG.error("Failed to validate table rows when getting row count", ex);
         return -1;
      }
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

      if(!moreRows(r) && r >= rows.size()) {
         return -1;
      }

      return rows.get(r);
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
      disposeRows();
   }

   /**
    * Dispose the distinct table lens.
    */
   @Override
   public synchronized void dispose() {
      disposeRows();
      disposeTable();
   }

   private void disposeRows() {
      if(rows != null) {
         rows.dispose();
         rows = null;
      }
   }

   private void disposeTable() {
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
    * Tuple, the key in hash table.
    */
   private final class Tuple {
      public Tuple(TableLens table, int row) {
         arr = new Object[cols.length];

         for(int i = 0; i < arr.length; i++) {
            arr[i] = table.getObject(row, cols[i]);
            hash += arr[i] == null ? 0 : arr[i].hashCode();
         }
      }

      public int hashCode() {
         return hash;
      }

      public boolean equals(Object obj) {
         if(obj == null) {
            return false;
         }

         if(obj == this) {
            return true;
         }

         Tuple tuple2 = (Tuple) obj;

         if(tuple2.arr.length != arr.length) {
            return false;
         }

         for(int i = 0; i < arr.length; i++) {
            if(!Tool.equals(arr[i], tuple2.arr[i])) {
               return false;
            }
         }

         return true;
      }

      private Object[] arr;
      private int hash;
   }

   private XSwappableIntList rows;  // rows
   private TableLens table;         // base table
   private int[] cols;              // distinct columns
   private int[] dimTypes;          // column dimension type
   private Comparator[] comps;      // column comparator
   private boolean completed;       // completed flag
   private volatile boolean cancelled;       // cancelled flag
   private final Lock cancelLock = new ReentrantLock();
   private boolean stable;          // true to not reorder rows
   private boolean validated;       // check if validated

   private static final Logger LOG =
      LoggerFactory.getLogger(DistinctTableLens.class);
}
