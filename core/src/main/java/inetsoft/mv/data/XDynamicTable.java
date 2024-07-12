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
package inetsoft.mv.data;

import inetsoft.mv.DateMVColumn;
import inetsoft.mv.XDynamicMVColumn;
import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.report.lens.DefaultTableDataDescriptor;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.XTable;

import java.util.HashSet;
import java.util.Set;

/**
 * XDynamicTable, it expands the base table by appending dynamic mv columns.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class XDynamicTable extends AbstractTableLens {
   /**
    * Create an instance of XDynamicTable.
    */
   public XDynamicTable(XTable data, XDynamicMVColumn[] cols, int[] indics) {
      super();

      this.data = data;
      this.cols = cols;
      this.bcol = data.getColCount();
      this.ecol = cols.length;
      this.indics = indics;
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
      return data.moreRows(row);
   }

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows. If the table is loading in background and loading
    * is not done, return the negative number of loaded rows minus 1.
    * @return number of rows in table.
    */
   @Override
   public int getRowCount() {
      return data.getRowCount();
   }

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   @Override
   public int getColCount() {
      return bcol + ecol;
   }

   /**
    * Get the base col count.
    */
   public int getBaseColCount() {
      return bcol;
   }

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.  Default is 1.
    */
   @Override
   public int getHeaderRowCount() {
      return 1;
   }

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   @Override
   public int getHeaderColCount() {
      return data.getHeaderColCount();
   }

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as trailer rows.
    * @return number of header rows.
    */
   @Override
   public int getTrailerRowCount() {
      return data.getTrailerRowCount();
   }

   /**
    * Return the number of columns on the right of the table to be
    * treated as trailer columns.
    */
   @Override
   public int getTrailerColCount() {
      return data.getTrailerColCount();
   }

   /**
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isNull(int r, int c) {
      return c < bcol ? data.isNull(r, c) : getObject(r, c) == null;
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      if(c < bcol) {
         return data.getObject(r, c);
      }

      c = c - bcol;

      if(r == 0) {
         // here name is alias or attribute only
         return cols[c].getName();
      }

      Object obj = data.getObject(r, indics[c]);
      return cols[c].convert(obj);
   }

   /**
    * Return the value from the base table.
    */
   public Object getBaseObject(int r, int c) {
      if(c < bcol) {
         return data.getObject(r, c);
      }

      c = c - bcol;

      if(r == 0) {
         // here name is alias or attribute only
         return cols[c].getName();
      }

      return data.getObject(r, indics[c]);
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(descriptor == null) {
         descriptor = new XDynamicTableDescriptor();
      }

      return descriptor;
   }

   /**
    * Get the column identifier.
    */
   @Override
   public String getColumnIdentifier(int col) {
      if(col < bcol) {
         return data.getColumnIdentifier(col);
      }

      return null;
   }

   public boolean isDateMvColumn(int col) {
      col = col - bcol;
      return col >= 0 && col < cols.length && cols[col] instanceof DateMVColumn;
   }

   /**
    * Table data descriptor.
    */
   private class XDynamicTableDescriptor extends DefaultTableDataDescriptor {
      public XDynamicTableDescriptor() {
         super(XDynamicTable.this);
         int ccnt = data.getColCount();
         desc = data.getDescriptor();

         for(int i = 0; i < ccnt; i++) {
            String header = getHeader(i, true).toString();
            headers.add(header);
         }
      }

      @Override
      public boolean containsFormat() {
         return desc.containsFormat();
      }

      @Override
      public boolean containsDrill() {
         return desc.containsDrill();
      }

      @Override
      public XMetaInfo getXMetaInfo(TableDataPath path) {
         if(!path.isCell()) {
            return null;
         }

         String[] paths = path.getPath();

         if(paths.length == 0) {
            return null;
         }

         String col = paths[0];

         if(!headers.contains(col)) {
            return null;
         }

         return desc.getXMetaInfo(path);
      }

      @Override
      public TableDataPath getCellDataPath(int row, int col) {
         if(col < bcol) {
            return desc.getCellDataPath(row, col);
         }

         return null;
      }

      private final Set<String> headers = new HashSet<>();
      private TableDataDescriptor desc;
   }

   private final XTable data;
   private final XDynamicMVColumn[] cols;
   private final int[] indics;
   private final int bcol;
   private final int ecol;
}
