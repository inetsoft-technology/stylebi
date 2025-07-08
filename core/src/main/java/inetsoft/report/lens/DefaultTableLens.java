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
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.util.XUtil;
import inetsoft.util.SparseIndexedMatrix;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * The DefaultTableLens class provides a default implementation of the
 * TableLens interface. It allows users to store the values of the table
 * as well as the attributes in the object. Since table data is normally
 * retrieved from other sources, it is usually more appropriate to
 * use one of the built-in table lens or creating an application
 * specific table lens to map a data result to a table. Using a
 * DefaultTableLens to created a table will create a copy of the
 * original data.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class DefaultTableLens extends AttributeTableLens {
   /**
    * Create an empty table.
    */
   public DefaultTableLens() {
      this(0, 0);
   }

   /**
    * Create a table width specified number of rows and columns.
    * @param rows number of rows.
    * @param cols number of columns.
    */
   public DefaultTableLens(int rows, int cols) {
      super.setTable(new Table(rows, cols));
   }

   /**
    * Create a copy of a table lens.
    */
   public DefaultTableLens(TableLens lens) {
      this(lens, false);
   }

   /**
    * Create a copy of a table lens.
    * @param dataonly true if only copy the data from table lens.
    */
   public DefaultTableLens(TableLens lens, boolean dataonly) {
      lens.moreRows(Integer.MAX_VALUE);
      super.setTable(new Table(lens.getRowCount(), lens.getColCount()));

      for(int i = -1; lens.moreRows(i); i++) {
         for(int j = -1; j < lens.getColCount(); j++) {
            // @by larryl, when copying from CalcTableLens to
            // RuntimeCalcTableLens, we should copy the original formula
            // instead of the string. This is a hack but to get around this
            // properly requires additional functions and does not seem to
            // be worth it
            if(i > -1 && j > -1) {
               if(lens instanceof DefaultTableLens) {
                  values.get(i)[j] = ((DefaultTableLens) lens).values.get(i)[j];
               }
               else {
                  setObject(i, j, lens.getObject(i, j));
               }
            }

            if(!dataonly) {
               if(i == 0 && j > -1) {
                  int w = lens.getColWidth(j);

                  if(w >= 0) {
                     setColWidth(j, lens.getColWidth(j));
                  }
               }

               if(j > -1) {
                  setRowBorder(i, j, lens.getRowBorder(i, j));
                  setRowBorderColor(i, j, lens.getRowBorderColor(i, j));
               }

               if(i > -1) {
                  setColBorder(i, j, lens.getColBorder(i, j));
                  setColBorderColor(i, j, lens.getColBorderColor(i, j));
               }

               if(i > -1 && j > -1) {
                  setSpan(i, j, lens.getSpan(i, j));
                  setAlignment(i, j, lens.getAlignment(i, j));
                  setFont(i, j, lens.getFont(i, j));
                  setLineWrap(i, j, lens.isLineWrap(i, j));
                  setForeground(i, j, lens.getForeground(i, j));
                  setBackground(i, j, lens.getBackground(i, j));
                  TableDataDescriptor desc = lens.getDescriptor();
                  TableDataPath path = desc.getCellDataPath(i, j);
                  setXMetaInfo(i, j, desc.getXMetaInfo(path));
               }
            }
         }

         if(!dataonly && i > -1) {
            int h = lens.getRowHeight(i);

            if(h >= 0) {
               setRowHeight(i, h);
            }
         }
      }

      setHeaderRowCount(lens.getHeaderRowCount());
      setHeaderColCount(lens.getHeaderColCount());
      setTrailerRowCount(lens.getTrailerRowCount());
      setTrailerColCount(lens.getTrailerColCount());
   }

   /**
    * Create a table with initial data. The dimension of the table is
    * derived from the data array dimensions.
    * @param data table data.
    */
   public DefaultTableLens(Object[][] data) {
      setData(data);
   }

   /**
    * Set the base table to be used with the attribute table table.
    * @param table base table.
    */
   @Override
   public void setTable(TableLens table) {
      throw new RuntimeException("setTable() not allowed on DefaultTableLens");
   }

   /**
    * Set the number of rows and columns.
    * @param rows number of rows.
    * @param cols number of columns.
    */
   public void setDimension(int rows, int cols) {
      if(rows != getRowCount() || cols != getColCount()) {
         super.setTable(new Table(rows, cols));
      }
   }

   /**
    * Set the number of rows.
    * @param rows number of rows.
    */
   @Override
   public void setRowCount(int rows) {
      if(rows != getRowCount()) {
         super.setTable(new Table(rows, getColCount()));
      }
   }

   /**
    * Set the number of columns.
    * @param cols number of columns.
    */
   @Override
   public void setColCount(int cols) {
      if(cols != getColCount()) {
         super.setTable(new Table(getRowCount(), cols));
      }
   }

   /**
    * Set the number of header rows.
    * @param nrow number of header.
    */
   @Override
   public void setHeaderRowCount(int nrow) {
      hrow = nrow;
   }

   /**
    * Set the number of header columns.
    * @param ncol number of header.
    */
   @Override
   public void setHeaderColCount(int ncol) {
      hcol = ncol;
   }

   /**
    * Set the number of tail rows.
    * @param nrow number of tail.
    */
   @Override
   public void setTrailerRowCount(int nrow) {
      trow = nrow;
   }

   /**
    * Set the number of tail columns.
    * @param ncol number of tail.
    */
   @Override
   public void setTrailerColCount(int ncol) {
      tcol = ncol;
   }

   /**
    * Set the cell value.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      values.get(r)[c] = v;
      fireChangeEvent();
   }

   /**
    * Set the data in this table. The number of rows and columns are derived
    * from the data matrix.
    * @param data table data.
    */
   public void setData(Object[][] data) {
      int cols = 0;

      for(int i = 0; i < data.length; i++) {
         cols = Math.max(cols, data[i].length);
      }

      super.setTable(new Table(data.length, cols));

      // set the new data
      for(int i = 0; i < data.length; i++) {
         for(int j = 0; j < data[i].length; j++) {
            setObject(i, j, data[i][j]);
         }
      }

      invalidate();
   }

   /**
    * Add a row to the end of the table.
    */
   public void addRow() {
      insertRow(values.size());
   }

   /**
    * Insert a new row above the specified row.
    */
   public void insertRow(int row) {
      insertRow(row, 1);
   }

   /**
    * Insert a new row above the specified row.
    * @param row row index to insert to.
    * @param n number of rows to insert.
    */
   public void insertRow(int row, int n) {
      // adjust span
      if(spanmap != null && !spanmap.isEmpty()) {
         int maxh = 0;

         // optimization, find the max height of all span
         Object[] spans = spanmap.getValues();

         for(int i = 0; i < spans.length && spans[i] != null; i++) {
            maxh = Math.max(maxh, ((Dimension) spans[i]).height);
         }

         // optimization, don't look past the largest row that may overlap
         // the new inserted rows
         int start = Math.max(0, row - maxh);

         // @by larryl optimization, only check for rows that has span set
         for(int i = row - 1; i >= start; i--) {
            for(int j = 0; j < ncol; j++) {
               // move span
               Dimension span = (Dimension) spanmap.get(i, j);

               if(span != null && i + span.height > row && i < row) {
                  spanmap.set(i, j, new Dimension(span.width, span.height + n));
               }
            }
         }

         spanmap.insertRow(row, n);
      }

      // move the row heights
      rowHeights.insert(row, n);

      insertRow(colbordermap, row, n);
      insertRow(rowbordermap, row, n);
      insertRow(colborderCmap, row, n);
      insertRow(rowborderCmap, row, n);
      insertRow(foregroundmap, row, n);
      insertRow(backgroundmap, row, n);
      insertRow(alignmap, row, n);
      insertRow(wrapmap, row, n);
      insertRow(fontmap, row, n);

      // adjust header/trailer row count if necessary
      if(row < getHeaderRowCount() && row + n <= getHeaderRowCount()) {
         setHeaderRowCount(getHeaderRowCount() + n);
      }

      if(row > getRowCount() - getTrailerRowCount()) {
         setTrailerRowCount(getTrailerRowCount() + n);
      }

      for(int r = row; r < row + n; r++) {
         values.add(r, new Object[ncol]);
      }

      nrow += n;
      invalidate();
   }

   /**
    * Insert row for attrs.
    */
   private void insertRow(SparseIndexedMatrix matrix, int row, int n) {
      if(matrix != null) {
         matrix.insertRow(row, n);
      }
   }

   /**
    * Remove the specified row.
    */
   public void removeRow(int row) {
      removeRow(row, 1);
   }

   /**
    * Remove the specified row.
    */
   public void removeRow(int row, int n) {
      // adjust span
      if(spanmap != null && !spanmap.isEmpty()) {
         int maxh = 0;

         // optimization, find the max height of all span
         Object[] spans = spanmap.getValues();

         for(int i = 0; i < spans.length && spans[i] != null; i++) {
            maxh = Math.max(maxh, ((Dimension) spans[i]).height);
         }

         // optimization, don't look past the largest row that may overlap
         // the new inserted rows
         int start = Math.max(0, row - maxh);

         // @by larryl optimization, only check for rows that has span set
         for(int i = start; i < row; i++) {
            for(int j = 0; j < ncol; j++) {
               // move span
               Dimension span = (Dimension) spanmap.get(i, j);

               if(span != null && i + span.height > row && i < row) {
                  Dimension nspan = new Dimension(span.width, span.height - n);

                  if(nspan.width <= 1 && nspan.height <= 1) {
                     nspan = null;
                  }

                  setSpan(i, j, nspan);
               }
            }
         }

         spanmap.removeRow(row, n);
      }

      // move the row heights
      rowHeights.remove(row, n);

      removeRow(colbordermap, row, n);
      removeRow(rowbordermap, row, n);
      removeRow(colborderCmap, row, n);
      removeRow(rowborderCmap, row, n);
      removeRow(foregroundmap, row, n);
      removeRow(backgroundmap, row, n);
      removeRow(alignmap, row, n);
      removeRow(wrapmap, row, n);
      removeRow(fontmap, row, n);

      if(row < getHeaderRowCount()) {
         setHeaderRowCount(getHeaderRowCount() - n);
      }

      if(row >= getRowCount() - getTrailerRowCount()) {
         setTrailerRowCount(getTrailerRowCount() - n);
      }

      for(int i = 0; i < n; i++) {
         values.remove(row);
      }

      nrow -= n;
      invalidate();
   }

   /**
    * Remove row for attrs.
    */
   private void removeRow(SparseIndexedMatrix matrix, int row, int n) {
      if(matrix != null) {
         matrix.removeRow(row, n);
      }
   }

   /**
    * Add a column to the end of the table.
    */
   public void addColumn() {
      insertColumn(ncol);
   }

   /**
    * Insert a new column to the left of the specified column.
    */
   public void insertColumn(int col) {
      insertColumn(col, 1);
   }

   // @by ChrisSpagnoli feature1414607346853 2014-11-12
   // Preserve existing method signature / behaviour
   public void insertColumn(int col, int n) {
      insertColumn(col, n, true);
   }

   /**
    * Insert a new column to the left of the specified column.
    * @param col column index to insert to.
    * @param n number of columns to insert.
    */
   // @by ChrisSpagnoli feature1414607346853 2014-11-12
   // Add "adjSpan" parameter, to allow a new column to be added WITHOUT going
   // back and increasing the existing spans of previous columns.
   public void insertColumn(int col, int n, boolean adjSpan) {
      // adjust span
      if(spanmap != null && !spanmap.isEmpty()) {
         if(adjSpan) {
            for(int i = 0; i < getRowCount(); i++) {
               for(int j = 0; j < ncol; j++) {
                  // move span
                  Dimension span = (Dimension) spanmap.get(i, j);

                  if(span != null && j + span.width > col && j < col) {
                     setSpan(i, j, new Dimension(span.width + n, span.height));
                  }
               }
            }
         }

         spanmap.insertColumn(col, n);
      }

      // adjust span
      colWidths.insert(col, n);

      insertCol(colbordermap, col, n);
      insertCol(rowbordermap, col, n);
      insertCol(colborderCmap, col, n);
      insertCol(rowborderCmap, col, n);
      insertCol(foregroundmap, col, n);
      insertCol(backgroundmap, col, n);
      insertCol(alignmap, col, n);
      insertCol(wrapmap, col, n);
      insertCol(fontmap, col, n);

      if(col < getHeaderColCount()) {
         setHeaderColCount(getHeaderColCount() + n);
      }

      if(col >= getColCount() - getTrailerColCount()) {
         setTrailerColCount(getTrailerColCount() + n);
      }

      // insert into data
      for(int i = 0; i < values.size(); i++) {
         Object[] val = values.get(i);
         Object[] row = new Object[ncol + n];

         System.arraycopy(val, 0, row, 0, col);
         System.arraycopy(val, col, row, col + n, val.length - col);
         values.set(i, row);
      }

      ncol += n;
      invalidate();
   }

   /**
    * Insert col for attrs.
    */
   private void insertCol(SparseIndexedMatrix matrix, int col, int n) {
      if(matrix != null) {
         matrix.insertColumn(col, n);
      }
   }

   /**
    * Remove a column at the specified location.
    */
   public void removeColumn(int col) {
      removeColumn(col, 1);
   }

   /**
    * Remove a column at the specified location.
    */
   public void removeColumn(int col, int n) {
      // adjust span
      if(spanmap != null && !spanmap.isEmpty()) {
         for(int i = 0; i < getRowCount(); i++) {
            for(int j = 0; j < ncol; j++) {
               // move span
               Dimension span = getSpan(i, j);

               if(span != null && j + span.width > col && j <= col) {
                  Dimension nspan = new Dimension(span.width-n, span.height);

                  if(nspan.width <= 1 && nspan.height <= 1) {
                     nspan = null;
                  }

                  setSpan(i, j, nspan);
               }
            }
         }

         spanmap.removeColumn(col, n);
      }

      // adjust column width
      colWidths.remove(col, n);

      removeCol(colbordermap, col, n);
      removeCol(rowbordermap, col, n);
      removeCol(colborderCmap, col, n);
      removeCol(rowborderCmap, col, n);
      removeCol(foregroundmap, col, n);
      removeCol(backgroundmap, col, n);
      removeCol(alignmap, col, n);
      removeCol(wrapmap, col, n);
      removeCol(fontmap, col, n);

      if(col < getHeaderColCount()) {
         setHeaderColCount(getHeaderColCount() - n);
      }

      if(col >= getColCount() - getTrailerColCount()) {
         setTrailerColCount(getTrailerColCount() - n);
      }

      // remove column from data
      for(int i = 0; i < values.size(); i++) {
         Object[] val = values.get(i);
         Object[] row = new Object[ncol - n];

         System.arraycopy(val, 0, row, 0, col);
         System.arraycopy(val, col + n, row, col, val.length - col - n);

         values.set(i, row);
      }

      ncol -= n;
      invalidate();
   }


   /**
    * Remove col for attrs.
    */
   private void removeCol(SparseIndexedMatrix matrix, int col, int n) {
      if(matrix != null) {
         matrix.removeColumn(col, n);
      }
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
      // @by larryl, optimization, for default table, the base table can
      // never have span so skip the check.
      return getSpan0(r, c);
   }

   class Table extends AbstractTableLens {
      /**
       * Create an instance of the table with out changing the existing data.
       */
      Table() {
      }

      /**
       * Create a table with the specified data.
       */
      public Table(int rows, int cols) {
         List<Object[]> ovalues = values;
         values = new ArrayList<>(rows);
         nrow = rows;
         ncol = cols;

         int currentSize = 0;

         if(ovalues != null) {
            for(Object[] val : ovalues) {
               Object[] nval = new Object[cols];
               System.arraycopy(val, 0, nval, 0, Math.min(cols, val.length));
               values.add(nval);
               currentSize++;
            }
         }

         for(int i = currentSize; i < rows; i++) {
            values.add(new Object[cols]);
         }
      }

      /**
       * Return the number of rows in the table. The number of rows includes
       * the header rows.
       * @return number of rows in table.
       */
      @Override
      public int getRowCount() {
         return nrow;
      }

      /**
       * Return the number of columns in the table. The number of columns
       * includes the header columns.
       * @return number of columns in table.
       */
      @Override
      public int getColCount() {
         return ncol;
      }

      /**
       * Return the value at the specified cell.
       * @param r row number.
       * @param c column number.
       * @return the value at the location.
       */
      @Override
      public Object getObject(int r, int c) {
         if(values == null || values.get(r) == null || c >= values.get(r).length) {
            return null;
         }

         return values.get(r)[c];
      }

      /**
       * Set the cell value.
       * @param r row number.
       * @param c column number.
       * @param v cell value.
       */
      @Override
      public void setObject(int r, int c, Object v) {
         values.get(r)[c] = v;
         fireChangeEvent();
      }

      /**
       * Get the current column width setting. The meaning of column widths
       * depends on the table layout policy setting. If the column width
       * is to be calculated by the ReportSheet based on the content,
       * return StyleConstants.REMAINDER if the cell content is null.
       * @return column width.
       */
      @Override
      public int getColWidth(int col) {
         return -1;
      }

      /**
       * Return the number of rows on the top of the table to be treated
       * as header rows.
       * @return number of header rows.
       */
      @Override
      public int getHeaderRowCount() {
         return hrow;
      }

      /**
       * Return the number of columns on the left of the table to be
       * treated as header columns.
       */
      @Override
      public int getHeaderColCount() {
         return hcol;
      }

      /**
       * Return the number of rows on the bottom of the table to be treated
       * as tail rows.
       * @return number of tail rows.
       */
      @Override
      public int getTrailerRowCount() {
         return trow;
      }

      /**
       * Return the number of columns on the right of the table to be
       * treated as tail columns.
       */
      @Override
      public int getTrailerColCount() {
         return tcol;
      }
   }

   @Override
   public DefaultTableLens clone() {
      DefaultTableLens tbl = (DefaultTableLens) super.clone();

      if(values != null) {
         List<Object[]> nvalues = new ArrayList<>(values.size());

         for(int i = 0; i < values.size(); i++) {
            nvalues.add(i, values.get(i).clone());

            for(int j = 0; j < nvalues.get(i).length; j++) {
               nvalues.get(i)[j] = cloneObject(values.get(i)[j]);
            }
         }

         tbl.values = nvalues;
      }

      tbl.table = tbl.new Table();
      return tbl;
   }

   /**
    * Set table meta info.
    * @param row row number.
    * @param col column number.
    */
   public void setXMetaInfo(int row, int col, XMetaInfo minfo) {
      if(minfo != null) {
         mmap.put(new Point(row, col), minfo);
      }
   }

   /**
    * Invalidate the table filter forcely, and the table filter will perform
    * filtering calculation to validate itself.
    */
   @Override
   public void invalidate() {
      mmap.clear();
      super.invalidate();
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(descriptor == null) {
         descriptor = new DefaultTableDataDescriptor(this) {
            /**
             * Get meta info of a specified table data path.
             * @param path the specified table data path
             * @return meta info of the table data path
             */
            @Override
            public XMetaInfo getXMetaInfo(TableDataPath path) {
               if(!path.isCell()) {
                  return null;
               }

               XMetaInfo minfo = metaMap.get(path);

               if(minfo != null) {
                  return minfo;
               }

               Enumeration<Point> e = mmap.keys();

               while(e.hasMoreElements()) {
                  Point p = e.nextElement();
                  TableDataPath npath = getCellDataPath(p.x, p.y);

                  if(!path.equals(npath)) {
                     continue;
                  }

                  minfo = mmap.get(p);

                  if(minfo == null) {
                     continue;
                  }

                  metaMap.put(path, minfo);
                  return minfo;
               }

               return null;
            }

            @Override
            public List<TableDataPath> getXMetaInfoPaths() {
               List<TableDataPath> list = new ArrayList<>();

               if(metaMap.isEmpty()) {
                  list.addAll(metaMap.keySet());
               }

               return list;
            }

            /**
             * Check if contains format.
             * @return true if contains format
             */
            @Override
            public boolean containsFormat() {
               if(cformat == 0) {
                  cformat = XUtil.containsFormat(mmap) ? CONTAINED : NOT_CONTAINED;
               }

               return cformat == CONTAINED;
            }

            /**
             * Check if contains drill.
             * @return <tt>true</tt> if contains drill, <tt>false</tt> otherwise
             */
            @Override
            public boolean containsDrill() {
               if(cdrill == 0) {
                  cdrill = XUtil.containsDrill(mmap) ? CONTAINED :
                     NOT_CONTAINED;
               }

               return cdrill == CONTAINED;
            }

            private static final int NOT_CONTAINED = 1;
            private static final int CONTAINED = 2;
            private final Hashtable<TableDataPath, XMetaInfo> metaMap = new Hashtable<>();
            private int cformat = 0;
            private int cdrill = 0;
         };
      }

      return descriptor;
   }

   /**
    * Perform object level post clone operation.
    */
   protected Object cloneObject(Object obj) {
      return obj;
   }

   private List<Object[]> values = new ArrayList<>();
   private int nrow, ncol;
   private int hrow = 1, hcol;
   private int trow, tcol;
   private final Hashtable<Point, XMetaInfo> mmap = new Hashtable<>();
}
