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

import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableLens;
import inetsoft.report.filter.BinaryTableFilter;
import inetsoft.report.filter.DefaultTableChangeListener;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.XConstants;
import inetsoft.uql.XTable;
import inetsoft.util.*;
import inetsoft.util.swap.XSwapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A JoinTableLens provides the results of a join between two other TableLens
 * objects.
 *
 * @author  InetSoft Technology
 * @version 9.1
 */
public class JoinTableLens extends AbstractTableLens
   implements CancellableTableLens, BinaryTableFilter
{
   /**
    * Constant specifying that an inner join should be used.
    */
   public static final int INNER_JOIN = XConstants.INNER_JOIN;

   /**
    * Constant specifying that a left outer join should be used. In this case,
    * all rows from the left-hand table will be included and only those rows
    * from the right-hand table that have matching join columns will be
    * included.
    */
   public static final int LEFT_OUTER_JOIN = XConstants.LEFT_JOIN;

   /**
    * Constant specifying that a right outer join should be used. In this case,
    * all rows from the right-hand table will be included and only those rows
    * from the left-hand table that have matching join columns will be
    * included.
    */
   public static final int RIGHT_OUTER_JOIN = XConstants.RIGHT_JOIN;

   /**
    * Constant specifying that a full outer join should be used. In this case,
    * all rows from the left-hand and right-hand table will be included even
    * no matching join columns.
    */
   public static final int FULL_OUTER_JOIN = XConstants.FULL_JOIN;

   /**
    * Creates a new instance of JoinTableLens using an inner join. The join
    * columns of the right-hand table will not be included in the joined table.
    *
    * @param leftTable the left-hand table to join.
    * @param rightTable the right-hand table to join.
    * @param leftCols the indices of the join columns in the left-hand table.
    * @param rightCols the indices of the join columns in the right-hand table.
    */
   public JoinTableLens(TableLens leftTable, TableLens rightTable,
                        int[] leftCols, int[] rightCols)
   {
      this(leftTable, rightTable, leftCols, rightCols, INNER_JOIN, false);
   }

   /**
    * Creates a new instance of JoinTableLens. The join columns of the
    * right-hand table will not be included in the joined table.
    *
    * @param leftTable the left-hand table to join.
    * @param rightTable the right-hand table to join.
    * @param leftCols the indices of the join columns in the left-hand table.
    * @param rightCols the indices of the join columns in the right-hand table.
    * @param joinType the type of join to use.
    */
   public JoinTableLens(TableLens leftTable, TableLens rightTable,
                        int[] leftCols, int[] rightCols, int joinType)
   {
      this(leftTable, rightTable, leftCols, rightCols, joinType, false);
   }

   /**
    * Creates a new instance of JoinTableLens.
    *
    * @param leftTable the left-hand table to join.
    * @param rightTable the right-hand table to join.
    * @param leftCols the indices of the join columns in the left-hand table.
    * @param rightCols the indices of the join columns in the right-hand table.
    * @param joinType the type of join to use.
    * @param includeRightJoinCols <code>true</code> if the join columns from the
    *                             right-hand table should be included in the
    *                             joined table.
    */
   public JoinTableLens(TableLens leftTable, TableLens rightTable,
                        int[] leftCols, int[] rightCols, int joinType,
                        boolean includeRightJoinCols)
   {
      this.leftTable = leftTable;
      this.rightTable = rightTable;
      this.leftCols = leftCols;
      this.rightCols = rightCols;
      this.joinType = joinType;
      this.includeRightJoinCols = includeRightJoinCols;
      delegate = createDelegate();
      this.leftTable.addChangeListener(new DefaultTableChangeListener(this));
      this.rightTable.addChangeListener(new DefaultTableChangeListener(this));
   }

   /**
    * Get the left base table lens.
    * @return the left base table lens.
    */
   @Override
   public TableLens getLeftTable() {
      return this.leftTable;
   }

   /**
    * Get the right base table lens.
    * @return the right base table lens.
    */
   @Override
   public TableLens getRightTable() {
      return this.rightTable;
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(descriptor == null) {
         descriptor = delegate.createDescriptor(this);
      }

      return descriptor;
   }

   /**
    * Invalidate the table filter forcely, and the table filter will perform
    * filtering calculation to validate itself.
    */
   @Override
   public void invalidate() {
      delegate = createDelegate();
      delegate.clearMetadata();
      fireChangeEvent();
   }

   /**
    * Cancel the lens and running queries if supported.
    */
   @Override
   public void cancel() {
      delegate.cancel();

      if(delegate.isCancelled()) {
         delegate.clearMetadata();
      }
   }

   /**
    * Check the TableLens to see if it is cancelled.
    */
   @Override
   public boolean isCancelled() {
      return delegate.isCancelled();
   }

   @Override
   public Class<?> getColType(int col) {
      return delegate.getColType(col);
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows. If the table is loading in background and loading
    * is not done, return the negative number of loaded rows minus 1.
    * @return number of rows in table.
    */
   @Override
   public int getColCount() {
      return delegate.getJoinColCount();
   }

   /**
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isNull(int r, int c) {
      JoinTable.TableRef ref = delegate.getTableRef(r, c);
      return ref.table.isNull(ref.row, ref.col);
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      if(matrix != null) {
         Object val = matrix.get(r, c);

         if(val != SparseMatrix.NULL) {
            return val;
         }
      }

      // if disposed, just return null instead of generating an exception.
      if(leftTable == null) {
         return null;
      }

      JoinTable.TableRef ref = delegate.getTableRef(r, c);
      return ref.table.getObject(ref.row, ref.col);
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the double value in the specified row.
    */
   @Override
   public final double getDouble(int r, int c) {
      JoinTable.TableRef ref = delegate.getTableRef(r, c);
      return ref.table.getDouble(ref.row, ref.col);
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the float value in the specified row.
    */
   @Override
   public final float getFloat(int r, int c) {
      JoinTable.TableRef ref = delegate.getTableRef(r, c);
      return ref.table.getFloat(ref.row, ref.col);
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the long value in the specified row.
    */
   @Override
   public final long getLong(int r, int c) {
      JoinTable.TableRef ref = delegate.getTableRef(r, c);
      return ref.table.getLong(ref.row, ref.col);
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the int value in the specified row.
    */
   @Override
   public final int getInt(int r, int c) {
      JoinTable.TableRef ref = delegate.getTableRef(r, c);
      return ref.table.getInt(ref.row, ref.col);
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the short value in the specified row.
    */
   @Override
   public final short getShort(int r, int c) {
      JoinTable.TableRef ref = delegate.getTableRef(r, c);
      return ref.table.getShort(ref.row, ref.col);
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the byte value in the specified row.
    */
   @Override
   public final byte getByte(int r, int c) {
      JoinTable.TableRef ref = delegate.getTableRef(r, c);
      return ref.table.getByte(ref.row, ref.col);
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the boolean value in the specified row.
    */
   @Override
   public final boolean getBoolean(int r, int c) {
      JoinTable.TableRef ref = delegate.getTableRef(r, c);
      return ref.table.getBoolean(ref.row, ref.col);
   }

   /**
    * Check if is primitive.
    * @return <tt>true</tt> if is primitive, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isPrimitive(int col) {
      JoinTable.TableRef ref = delegate.getTableRef(0, col);
      return ref.table.isPrimitive(ref.col);
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public int getRowCount() {
      return delegate.getRowCount();
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      return delegate.getHeaderColCount();
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.  Default is 1.
    */
   @Override
   public int getHeaderRowCount() {
      return delegate.getHeaderRowCount();
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as trailer columns.
    */
   @Override
   public int getTrailerColCount() {
      return delegate.getTrailerColCount();
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as trailer rows.
    * @return number of header rows.
    */
   @Override
   public int getTrailerRowCount() {
      return delegate.getTrailerRowCount();
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
      if(delegate.isMaxAlert()) {
         String message = Catalog.getCatalog().getString(
            "join.table.limited", delegate.getMaxRows());
         boolean messageExist = Tool.existUserMessage(message);
         Tool.addUserMessage(message);

         if(!messageExist) {
            LOG.warn(message);
         }

         if(!"true".equals(SreeEnv.getProperty("always.warn.joinMaxRows"))) {
            delegate.setMaxAlert(true);
         }
      }

      return delegate.moreRows(row);
   }

   /**
    * Set the cell value.
    * @param r row number.
    * @param c column number.
    * @param val cell value.
    */
   @Override
   public void setObject(int r, int c, Object val) {
      if(matrix == null) {
         matrix = new SparseMatrix();
      }

      matrix.set(r, c, val);
      fireChangeEvent();
   }

   /**
    * Dispose the join table lens.
    */
   @Override
   public void dispose() {
      cancel();

      if(delegate != null) {
         delegate.dispose();
      }

      leftTable = null;
      rightTable = null;
   }

   /**
    * @return the report/vs name which this filter was created for,
    * and will be used when insert audit record.
    */
   @Override
   public String getReportName() {
      Object value = getReportProperty(XTable.REPORT_NAME);
      return value == null ? null : value + "";
   }

   /**
    * @return the report type which this filter was created for:
    * ExecutionBreakDownRecord.OBJECT_TYPE_REPORT or
    * ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET
    */
   @Override
   public String getReportType() {
      Object value = getReportProperty(XTable.REPORT_TYPE);
      return value == null ? null : value + "";
   }

   public Object getReportProperty(String key) {
      Object value = super.getProperty(key);

      if(value == null && leftTable != null) {
         value = leftTable.getProperty(key);
      }

      if(value == null && rightTable != null) {
         value = rightTable.getProperty(key);
      }

      return value;
   }

   private JoinTable createDelegate() {
      boolean forceHash = "true".equals(SreeEnv.getProperty("join.table.forceHash"));
      boolean useHash = forceHash || XSwapper.getMemoryState() >= XSwapper.NORM_MEM;

      String str = SreeEnv.getProperty("join.table.maxrows");
      int maxRows = Integer.MAX_VALUE;

      if(str != null && !str.isEmpty()) {
         try {
            maxRows = Integer.parseInt(str);
         }
         catch(NumberFormatException e) {
            LOG.warn("Invalid value for 'join.table.maxrows' property: " + str);
         }
      }

      if(useHash) {
         return new HashJoinTable(
            leftTable, rightTable, leftCols, rightCols, joinType,
            includeRightJoinCols, maxRows);
      }
      else {
         return new MergeJoinTable(
            leftTable, rightTable, leftCols, rightCols, joinType,
            includeRightJoinCols, maxRows);
      }
   }

   private JoinTable delegate;
   private TableLens leftTable;
   private TableLens rightTable;
   private final int[] leftCols;
   private final int[] rightCols;
   private final int joinType;
   private final boolean includeRightJoinCols;
   private transient SparseMatrix matrix = null; // hold data

   private static final Logger LOG = LoggerFactory.getLogger(JoinTableLens.class);
}
