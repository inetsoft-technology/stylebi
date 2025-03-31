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
import inetsoft.report.internal.table.CachedTableLens;
import inetsoft.uql.table.*;
import inetsoft.util.SparseMatrix;

/**
 * This class implements paged table lens. A subclass of this class must call
 * addRow() to populate the rows, including the header row. It should call
 * complete() when all rows are populated. The addRow() can be called in a
 * separate thread, and the table lens can be used for data binding before
 * the data is fully loaded.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public abstract class PagedTableLens extends AbstractTableLens
   implements CachedTableLens
{
   /**
    * Create a pgaed table lens.
    */
   public PagedTableLens() {
      super();

      delegate = new XSwappableTable();
   }

   /**
    * Set the column types.
    */
   public void setTypes(Class[] types) {
      this.types = types;
      XTableColumnCreator[] creators = new XTableColumnCreator[types.length];

      for(int i = 0; i < creators.length; i++) {
         creators[i] = XObjectColumn.getCreator(types[i]);
         creators[i].setDynamic(false);
      }

      delegate.init(creators);
   }

   @Override
   public Class<?> getColType(int col) {
      return types != null && col < types.length ? types[col] : null;
   }

   /**
    * Set the number of columns in the table.
    */
   public void setColCount(int ncol) {
      XTableColumnCreator[] creators = new XTableColumnCreator[ncol];

      for(int i = 0; i < creators.length; i++) {
         creators[i] = XObjectColumn.getCreator();
         creators[i].setDynamic(false);
      }

      delegate.init(creators);
   }

   /**
    * Get the number of columns in the table.
    */
   @Override
   public int getColCount() {
      return delegate.getColCount();
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      return delegate.getRowCount();
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
      return delegate.moreRows(row);
   }

   /**
    * Set the value of a cell.
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

      return delegate.getObject(r, c);
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      return delegate.getDescriptor();
   }

   /**
    * Add a new row to the table.
    */
   public void addRow(Object[] row) {
      delegate.addRow(row);
   }

   /**
    * This method must be called after all addRow() have been called.
    */
   public void complete() {
      delegate.complete();
   }

   /**
    * Clear all cached data.
    */
   @Override
   public void clearCache() {
      // do nothing
   }

   /**
    * Dispose the table.
    */
   @Override
   public void dispose() {
      delegate.dispose();
   }

   /**
    * finalize the object.
    */
   @Override
   protected void finalize() throws Throwable {
      dispose();

      super.finalize();
   }

   protected XSwappableTable delegate = new XSwappableTable(); // delegate table
   private Class[] types;
   private SparseMatrix matrix = null; // hold data
}
