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
package inetsoft.report.filter;

import inetsoft.report.*;
import inetsoft.report.internal.table.*;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.util.swap.XSwappableIntList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Description of class.
 *
 * @author  InetSoft Technology
 * @since   6.5
 */
public abstract class AbstractConditionFilter extends AbstractTableLens
   implements TableFilter, CachedTableLens, CancellableTableLens
{
   /**
    * Clear all cached data.
    */
   @Override
   public void clearCache() {
      // do nothing
   }

   /**
    * Invalidate the table filter forcely, and the table filter will perform
    * filtering calculation to validate itself.
    */
   @Override
   public synchronized void invalidate() {
      invalidate(true);
   }

   /**
    * Invalidate the table filter forcely, and the table filter will perform
    * filtering calculation to validate itself.
    */
   private void invalidate(boolean fire) {
      completed = false;
      baseRow = 0;

      if(rowmap != null) {
         rowmap.dispose();
      }

      rowmap = new XSwappableIntList();
      hcount = table.getHeaderRowCount();

      for(int i = 0; i < hcount; i++) {
         rowmap.add(i);
         baseRow++;
      }

      if(fire) {
         fireChangeEvent();
      }

      if(debug) {
         LOG.debug("Invalidate condition filter: " + this,
            new Exception("Stack trace"));
      }
   }

   /**
    * Evaluate a row to check of all conditions are met.
    */
   protected abstract boolean checkCondition(int r);

   /**
    * Check init.
    */
   public void checkInit() {
      if(rowmap == null) {
         synchronized(this) {
            if(rowmap == null) {
               invalidate(false);
            }
         }
      }
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
   public synchronized void setTable(TableLens table) {
      this.table = table;
      this.table.addChangeListener(new DefaultTableChangeListener(this));
      invalidate(true);
   }

   /**
    * Get the base table row index corresponding to the filtered table.
    * If the row does not exist in the base table, it returns -1.
    * @param row row index in the filtered table.
    * @return corresponding row index in the base table.
    */
   @Override
   public final int getBaseRowIndex(int row) {
      moreRows(row);

      if(row < hcount) {
         return row;
      }
      else {
         return rowmap.get(row);
      }
   }

   /**
    * Gets the row index of this table that is mapped to the specified row in
    * the base table.
    *
    * @param row the row index of the base table.
    *
    * @return the row index of this table or -1 if the specified row is not
    *         mapped to a row in this table.
    */
   protected int getMappedRowIndex(int row) {
      int r = -1;

      for(int i = 0; i < rowmap.size(); i++) {
         if(rowmap.get(i) == row) {
            r = i;
            break;
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
      checkInit();

      // don't start process if just accessing header row
      if(row < hcount) {
         return true;
      }

      synchronized(this) {
         if(!completed) {
            boolean more = true;

            if(debug && (rowmap == null || table == null)) {
               LOG.debug("Invalid condition filter: " + table + ", " +
                  rowmap + " in " + this, new Exception("Stack trace"));
            }

            while(row >= rowmap.size() && (more = table.moreRows(baseRow)) && !cancelled) {
               if(checkCondition(baseRow)) {
                  rowmap.add(baseRow);
               }

               baseRow++;
            }

            if(!more) {
               completed = true;
               rowmap.complete();
            }
         }
      }

      return row < rowmap.size();
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      checkInit();
      return completed ? rowmap.size() : -rowmap.size() - 1;
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
      return Math.min(table.getHeaderColCount(), getColCount());
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
    * Return the number of cols on the right of the table to be treated
    * as trailer cols.
    * @return number of header cols.
    */
   @Override
   public int getTrailerColCount() {
      return Math.min(table.getTrailerColCount(),
         (getColCount() - 1) < 0 ? 0 : (getColCount() - 1));
   }

   /**
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isNull(int r, int c) {
      return table.isNull(getBaseRowIndex(r), c);
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      return table.getObject(getBaseRowIndex(r), c);
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the double value in the specified row.
    */
   @Override
   public final double getDouble(int r, int c) {
      return table.getDouble(getBaseRowIndex(r), c);
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the float value in the specified row.
    */
   @Override
   public final float getFloat(int r, int c) {
      return table.getFloat(getBaseRowIndex(r), c);
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the long value in the specified row.
    */
   @Override
   public final long getLong(int r, int c) {
      return table.getLong(getBaseRowIndex(r), c);
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the int value in the specified row.
    */
   @Override
   public final int getInt(int r, int c) {
      return table.getInt(getBaseRowIndex(r), c);
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the short value in the specified row.
    */
   @Override
   public final short getShort(int r, int c) {
      return table.getShort(getBaseRowIndex(r), c);
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the byte value in the specified row.
    */
   @Override
   public final byte getByte(int r, int c) {
      return table.getByte(getBaseRowIndex(r), c);
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the boolean value in the specified row.
    */
   @Override
   public final boolean getBoolean(int r, int c) {
      return table.getBoolean(getBaseRowIndex(r), c);
   }

   /**
    * Set the cell value.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      table.setObject(getBaseRowIndex(r), c, v);
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
      return table.getRowHeight(getBaseRowIndex(row));
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
    * Get the current column content type.
    * @param col column number.
    * @return column type.
    */
   @Override
   public Class getColType(int col) {
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
      return table.getRowBorderColor(getBaseRowIndex(r), c);
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      return table.getColBorderColor(getBaseRowIndex(r), c);
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
      return table.getRowBorder(getBaseRowIndex(r), c);
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
      return table.getColBorder(getBaseRowIndex(r), c);
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      return table.getInsets(getBaseRowIndex(r), c);
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
      return table.getSpan(getBaseRowIndex(r), c);
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      return table.getAlignment(getBaseRowIndex(r), c);
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      return table.getFont(getBaseRowIndex(r), c);
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
      return table.isLineWrap(getBaseRowIndex(r), c);
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
      return table.getForeground(getBaseRowIndex(r), c);
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
      return table.getBackground(getBaseRowIndex(r), c);
   }

   /**
    * Dispose the table to clear up temporary resources.
    */
   @Override
   public synchronized void dispose() {
      table.dispose();

      if(rowmap != null) {
         rowmap.dispose();
         rowmap = null;

         if(debug) {
            LOG.debug("Dispose condition filter: " + this,
               new Exception("Stack trace"));
         }
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
      if(hdescriptor == null) {
         hdescriptor = new MappedDataDescriptor(this, table.getDescriptor());
      }

      return hdescriptor;
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

   private static final Logger LOG = LoggerFactory.getLogger(AbstractConditionFilter.class);
   private TableLens table;
   private TableDataDescriptor hdescriptor;
   private XSwappableIntList rowmap;
   private boolean completed = false;
   private transient boolean debug = "true".equals(SreeEnv.getProperty("filter.debug", "false"));
   private int baseRow = 0;
   private int hcount;
   private volatile boolean cancelled;       // cancelled flag
   private final Lock cancelLock = new ReentrantLock();
}
