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
import inetsoft.report.filter.DefaultTableChangeListener;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.report.internal.table.SelfJoinOperator;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.XConstants;
import inetsoft.util.*;
import inetsoft.util.swap.XSwappableIntList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Self join table lens performs self-join on a base table lens. Any self join
 * operation MUST be one of the following join operations:<p>
 * <ul>
 * <li><code>INNER_JOIN</code></li>
 * <li><code>NOT_EQUAL_JOIN</code></li>
 * <li><code>GREATER_JOIN</code></li>
 * <li><code>GREATER_EQUAL_JOIN</code></li>
 * <li><code>LESS_JOIN</code></li>
 * <li><code>LESS_EQUAL_JOIN</code></li>
 * <ul>
 * <p>
 * In other words, of all the join operations, outer join operations like
 * <code>LEFT_JOIN<code> are disallowed.
 * <p>
 * Besides performing self-join on a base table, this table lens might
 * cooperate with <tt>JoinTableLens</tt> or <tt>CrossJoinTableLens</tt> to
 * perform non-equal join operations on two base table lenses; in this case,
 * the <tt>JoinTableLens</tt> or <tt>CrossJoinTableLens</tt> should be the
 * base table lens of this table lens.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class SelfJoinTableLens extends AbstractTableLens implements TableFilter,
   CancellableTableLens
{
   /**
    * Inner join operation.
    */
   public static final int INNER_JOIN = XConstants.INNER_JOIN;
   /**
    * Not equal join operation.
    */
   public static final int NOT_EQUAL_JOIN = XConstants.NOT_EQUAL_JOIN;
   /**
    * Greater join operation.
    */
   public static final int GREATER_JOIN = XConstants.GREATER_JOIN;
   /**
    * Greater equal join operation.
    */
   public static final int GREATER_EQUAL_JOIN = XConstants.GREATER_EQUAL_JOIN;
   /**
    * Less join operation.
    */
   public static final int LESS_JOIN = XConstants.LESS_JOIN;
   /**
    * Less equal join operation.
    */
   public static final int LESS_EQUAL_JOIN = XConstants.LESS_EQUAL_JOIN;

   /**
    * Constructor.
    */
   public SelfJoinTableLens() {
      super();

      oplist = new ArrayList();
   }

   /**
    * Constructor.
    */
   public SelfJoinTableLens(TableLens table) {
      this();

      setTable(table);
   }

   /**
    * Add one join operation.
    * @param col1 the specified column a.
    * @param op the specified join operation.
    * @param col2 the specified column b.
    */
   public void addJoin(int col1, int op, int col2) {
      SelfJoinOperator operator = new SelfJoinOperator(table, col1, op, col2);
      oplist.add(operator);

      invalidate();
   }

   /**
    * Remove all the joins.
    */
   public void removeJoins() {
      oplist.clear();

      invalidate();
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
      oplist.clear();

      invalidate();
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

      completed = false;
      fireChangeEvent();
   }

   /**
    * Validate the self join table lens.
    */
   private void validate() {
      synchronized(this) {
         if(rows != null) {
            return;
         }

         rows = new XSwappableIntList();

         for(int i = 0; i < table.getHeaderRowCount(); i++) {
            rows.add(i);
         }

         // notify waiting consumers
         notifyAll();
      }

      // concurrent process
      final XSwappableIntList rows2 = rows;
      ThreadPool.addOnDemand(() -> {
         SelfJoinOperator[] ops = new SelfJoinOperator[oplist.size()];
         oplist.toArray(ops);

         try {
            OUTER:
            for(int i = table.getHeaderRowCount(); table.moreRows(i); i++) {
               for(int j = 0; j < ops.length; j++) {
                  if(!ops[j].evaluate(i)) {
                     continue OUTER;
                  }
               }

               synchronized(SelfJoinTableLens.this) {
                  if(rows2.isDisposed()) {
                     return;
                  }

                  rows2.add(i);

                  // notify waiting consumers
                  SelfJoinTableLens.this.notifyAll();

                  if(rows2.size() >= maxRows) {
                     maxAlert = true;
                     break OUTER;
                  }
               }
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to validate table rows", ex);
         }

         synchronized(SelfJoinTableLens.this) {
            if(!rows2.isDisposed()) {
               completed = true;
               rows2.complete();

               // notify waiting consumers
               SelfJoinTableLens.this.notifyAll();
            }
         }
      });
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

      while((rows == null || row >= rows.size()) && !completed) {
         try {
            wait(50);
            validate();
         }
         catch(InterruptedException ex) {
            // ignore it
         }
      }

      if(maxAlert) {
         String message = Catalog.getCatalog().getString("join.table.limited", maxRows);
         boolean messageExist = Tool.existUserMessage(message);
         Tool.addUserMessage(message);

         if(!messageExist) {
            LOG.info(message);
         }

         if(!"true".equals(SreeEnv.getProperty("always.warn.joinMaxRows"))) {
            maxAlert = true;
         }
      }

      return rows != null && row < rows.size();
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

      return completed ? rows.size() : -rows.size() - 1;
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

      if(!moreRows(r)) {
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
    * Finalize the self join table lens.
    */
   @Override
   protected void finalize() throws Throwable {
      super.finalize();
      dispose();
   }

   /**
    * Dispose the self join table lens.
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

   private XSwappableIntList rows;// rows
   private List oplist;           // operator list
   private TableLens table;       // base table
   private boolean completed;     // completed flag
   private volatile boolean cancelled;     // cancelled flag
   private final Lock cancelLock = new ReentrantLock();
   private int maxRows = Integer.MAX_VALUE;
   private transient boolean maxAlert = false;

   private static final Logger LOG =
      LoggerFactory.getLogger(SelfJoinTableLens.class);
}
