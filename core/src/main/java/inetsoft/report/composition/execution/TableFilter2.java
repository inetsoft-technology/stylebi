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
package inetsoft.report.composition.execution;

import inetsoft.mv.*;
import inetsoft.report.*;
import inetsoft.report.filter.DefaultTableChangeListener;
import inetsoft.report.filter.SortedTable;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.report.internal.table.XTableLens;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.SparseMatrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;

/**
 * Another table filter for shared usage.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class TableFilter2 extends AbstractTableLens
   implements CancellableTableLens, TableFilter, SortedTable, DFWrapper
{
   /**
    * Get the double value.
    */
   private static double toDouble(Object val) {
      if(val instanceof Number) {
         return ((Number) val).doubleValue();
      }

      return 0D;
   }

   /**
    * Get the float value.
    */
   private static float toFloat(Object val) {
      if(val instanceof Number) {
         return ((Number) val).floatValue();
      }

      return 0F;
   }

   /**
    * Get the long value.
    */
   private static long toLong(Object val) {
      if(val instanceof Number) {
         return ((Number) val).longValue();
      }

      return 0L;
   }

   /**
    * Get the int value.
    */
   private static int toInt(Object val) {
      if(val instanceof Number) {
         return ((Number) val).intValue();
      }

      return 0;
   }

   /**
    * Get the short value.
    */
   private static short toShort(Object val) {
      if(val instanceof Number) {
         return ((Number) val).shortValue();
      }

      return 0;
   }

   /**
    * Get the byte value.
    */
   private static byte toByte(Object val) {
      if(val instanceof Number) {
         return ((Number) val).byteValue();
      }

      return 0;
   }

   /**
    * Get the boolean value.
    */
   private static boolean toBoolean(Object val) {
      return val instanceof Boolean && (Boolean) val;
   }

   /**
    * Create an empty default table filter.
    */
   public TableFilter2() {
      super();
   }

   /**
    * Create a default table filter with a table lens.
    */
   @SuppressWarnings("WeakerAccess")
   public TableFilter2(TableLens table) {
      setTable(table);
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
    * Dispose the table to clear up temporary resources.
    */
   @Override
   public void dispose() {
      // do nothing so that we may reuse the contained data
   }

   /**
    * Dispose the table internally.
    */
   @SuppressWarnings("WeakerAccess")
   public void dispose2() {
      super.dispose();
   }

   /**
    * Set the used materialized view.
    */
   public void setMV(String name) {
      mv = name;
   }

   /**
    * Get the used materialized view.
    */
   public String getMV() {
      return mv;
   }

   /**
    * Check if is changed.
    */
   public boolean isChanged() {
      MVManager manager = MVManager.getManager();
      MVDef def = manager.get(mv);

      if(def == null) {
         return false;
      }

      long modified = def.lastModified();
      return modified < 0 || modified > ts;
   }

   @Override
   public TableFilter2 clone() {
      try {
         TableFilter2 filter = (TableFilter2) super.clone();
         filter.matrix = null;
         return filter;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone table filter", ex);
      }

      return null;
   }

   /**
    * Get the String representation.
    */
   public String toString() {
      return super.toString() + "[" + getTable() + "]";
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
      coltypes = new Class[table.getColCount()];

      XTableLens etable = (XTableLens) Util.getNestedTable(table, XTableLens.class);
      embedded = etable != null && etable.getTable() instanceof XEmbeddedTable;

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
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isNull(int r, int c) {
      Object val = (matrix == null) ? SparseMatrix.NULL : matrix.get(r, c);
      return (val == SparseMatrix.NULL) ? table.isNull(r, c) : val == null;
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public final Object getObject(int r, int c) {
      Object val = (matrix == null) ? SparseMatrix.NULL : matrix.get(r, c);
      return (val == SparseMatrix.NULL) ? table.getObject(r, c) : val;
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the double value in the specified row.
    */
   @Override
   public final double getDouble(int r, int c) {
      Object val = (matrix == null) ? SparseMatrix.NULL : matrix.get(r, c);
      return (val == SparseMatrix.NULL) ? table.getDouble(r, c) :
         toDouble(val);
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the float value in the specified row.
    */
   @Override
   public final float getFloat(int r, int c) {
      Object val = (matrix == null) ? SparseMatrix.NULL : matrix.get(r, c);
      return (val == SparseMatrix.NULL) ? table.getFloat(r, c) :
         toFloat(val);
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the long value in the specified row.
    */
   @Override
   public final long getLong(int r, int c) {
      Object val = (matrix == null) ? SparseMatrix.NULL : matrix.get(r, c);
      return (val == SparseMatrix.NULL) ? table.getLong(r, c) : toLong(val);
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the int value in the specified row.
    */
   @Override
   public final int getInt(int r, int c) {
      Object val = (matrix == null) ? SparseMatrix.NULL : matrix.get(r, c);
      return (val == SparseMatrix.NULL) ? table.getInt(r, c) : toInt(val);
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the short value in the specified row.
    */
   @Override
   public final short getShort(int r, int c) {
      Object val = (matrix == null) ? SparseMatrix.NULL : matrix.get(r, c);
      return (val == SparseMatrix.NULL) ? table.getShort(r, c) :
         toShort(val);
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the byte value in the specified row.
    */
   @Override
   public final byte getByte(int r, int c) {
      Object val = (matrix == null) ? SparseMatrix.NULL : matrix.get(r, c);
      return (val == SparseMatrix.NULL) ? table.getByte(r, c) : toByte(val);
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the boolean value in the specified row.
    */
   @Override
   public final boolean getBoolean(int r, int c) {
      Object val = (matrix == null) ? SparseMatrix.NULL : matrix.get(r, c);
      return (val == SparseMatrix.NULL) ? table.getBoolean(r, c) :
         toBoolean(val);
   }

   /**
    * Set the cell value.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      // if embedded table, pass down the value so the value will persist in one runtime vs
      if(embedded) {
         table.setObject(r, c, v);
      }
      else {
         if(matrix == null) {
            matrix = new SparseMatrix();
         }

         matrix.set(r, c, v);
      }

      fireChangeEvent();
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
      if(col >= coltypes.length) {
         return String.class;
      }

      // optimization
      if(coltypes[col] == null) {
         coltypes[col] = table.getColType(col);
      }

      return coltypes[col];
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

   @Override
   public Dimension getSpan(int r, int c) {
      // TableFilter2 is return from WS execution. span is meaningless in data table
      // and could cause problem if it's passed to VS (regular) table
      return null;
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
      return identifier == null ? table.getColumnIdentifier(col) : identifier;
   }

   // SortedTable interface, allows sorting info to be passed up

   /**
    * Get the columns that the table is sorted on.
    * @return sort columns.
    */
   @Override
   public int[] getSortCols() {
      return (table instanceof SortedTable) ?
         ((SortedTable) table).getSortCols() : new int[0];
   }

   /**
    * Get the sorting order of the sorting columns.
    */
   @Override
   public boolean[] getOrders() {
      return (table instanceof SortedTable) ?
         ((SortedTable) table).getOrders() : new boolean[0];
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
      return (table instanceof SortedTable) ?
         ((SortedTable) table).getComparer(col) : null;
   }

   @Override
   public long dataId() {
      return (table instanceof DFWrapper) ? ((DFWrapper) table).dataId() : 0;
   }

   /**
    * RDD delegate methods.
    */
   @Override
   public Object getDF() {
      return (table instanceof DFWrapper) ? ((DFWrapper) table).getDF() : null;
   }

   /**
    * RDD delegate methods.
    */
   @Override
   public Object getRDD() {
      return (table instanceof DFWrapper) ? ((DFWrapper) table).getRDD() : null;
   }

   /**
    * RDD delegate methods.
    */
   @Override
   public DFWrapper getBaseDFWrapper() {
      return (table instanceof DFWrapper) ? (DFWrapper) table : null;
   }

   /**
    * RDD delegate methods.
    */
   @Override
   public String[] getHeaders() {
      return (table instanceof DFWrapper) ? ((DFWrapper) table).getHeaders() : null;
   }

   /**
    * RDD delegate methods.
    */
   @Override
   public void setXMetaInfos(XSwappableTable lens) {
      if(table instanceof DFWrapper) {
         ((DFWrapper) table).setXMetaInfos(lens);
      }
   }

   /**
    * RDD delegate methods.
    */
   @Override
   public void completed() {
      if(table instanceof DFWrapper) {
         ((DFWrapper) table).completed();
      }
   }

   @Override
   public void cancel() {
      final CancellableTableLens cancelTable = (CancellableTableLens) Util.getNestedTable(
         table, CancellableTableLens.class);

      if(cancelTable != null) {
         cancelTable.cancel();
         cancelled = cancelTable.isCancelled();
      }
   }

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

   @Serial
   private void readObject(ObjectInputStream in) throws ClassNotFoundException, IOException {
      in.defaultReadObject();
      setTable(table);
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(TableFilter2.class);
   private TableLens table;
   private transient Class[] coltypes = {};
   private SparseMatrix matrix = null;
   private String mv;
   private long ts = System.currentTimeMillis();
   private transient boolean embedded = false;
   private boolean cancelled = false;
}
