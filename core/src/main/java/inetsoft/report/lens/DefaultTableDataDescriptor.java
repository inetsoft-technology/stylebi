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
import inetsoft.report.TableDataPath;
import inetsoft.report.internal.Util;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.XTable;

import java.util.List;

/**
 * The DefaultTableDataDescriptor class provides default implementations for most
 * of the methods defined in the TableDataDescriptor interface.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class DefaultTableDataDescriptor implements TableDataDescriptor {
   /**
    * Create a default table descriptor.
    * @param table the specified table lens
    */
   public DefaultTableDataDescriptor(XTable table) {
      this.table = table;

      if(table instanceof AttributeTableLens) {
         this.attrlens = (AttributeTableLens) table;
      }
   }

   /**
    * Get table data path of a specified table column.
    * @param col the specified table column
    * @return table data path of the table column
    */
   @Override
   public TableDataPath getColDataPath(int col) {
      String header = getHeader(col).toString();
      return new TableDataPath(header);
   }

   /**
    * Get table data path of a specified table row.
    * @param row the specified table row
    * @return table data path of the table row
    */
   @Override
   public TableDataPath getRowDataPath(int row) {
      int type = TableDataPath.TRAILER;

      if(row < table.getHeaderRowCount()) {
         type = TableDataPath.HEADER;
      }
      else if(moreRows(row + table.getTrailerRowCount())) {
         type = TableDataPath.DETAIL;
      }

      return new TableDataPath(-1, type);
   }

   /**
    * Get table data path of a specified table cell.
    * @param row the specified table cell row
    * @param col the specified table cell col
    * @return table data path of the table cell
    */
   @Override
   public TableDataPath getCellDataPath(int row, int col) {
      if(col >= table.getColCount()) {
         return null;
      }

      String header = getHeader(col).toString();
      int type = TableDataPath.TRAILER;
      Object val = moreRows(row) ? table.getObject(row, col) : null;
      Class<?> cls = val == null ? null : val.getClass();

      if(cls == null) {
         cls = table.getColType(col);
      }

      String dtype = Util.getDataType(cls);

      if(row < table.getHeaderRowCount()) {
         type = TableDataPath.HEADER;
      }
      else if(moreRows(row + table.getTrailerRowCount())) {
         type = TableDataPath.DETAIL;
      }

      return new TableDataPath(-1, type, dtype, new String[] {header});
   }

   /**
    * Check if a column belongs to a table data path.
    * @param col the specified table col
    * @param path the specified table data path
    * @return true if the col belongs to the table data path, false otherwise
    */
   @Override
   public boolean isColDataPath(int col, TableDataPath path) {
      String header = getHeader(col).toString();

      if(path.getPath().length == 0) {
         return false;
      }
      else {
         return header.equals(path.getPath()[0]);
      }
   }

   /**
    * Check if a row belongs to a table data path.
    * @param row the specified table row
    * @param path the specified table data path
    * @return true if the row belongs to the table data path, false otherwise
    */
   @Override
   public boolean isRowDataPath(int row, TableDataPath path) {
      int type = path.getType();

      if(row < table.getHeaderRowCount()) {
         return type == TableDataPath.HEADER;
      }
      else if(moreRows(row + table.getTrailerRowCount())) {
         return type == TableDataPath.DETAIL;
      }
      else {
         return type == TableDataPath.TRAILER;
      }
   }

   /**
    * Test if a cell belongs to a table data path in a not strict way.
    * @param row the specified table cell row
    * @param col the specified table cell col
    * @param path the specified table data path
    * @return true if the cell belongs to the table data path, false
    * otherwise
    */
   @Override
   public boolean isCellDataPathType(int row, int col, TableDataPath path) {
      int type = path.getType();

      if(row < table.getHeaderRowCount()) {
         return type == TableDataPath.HEADER;
      }
      else if(moreRows(row + table.getTrailerRowCount())) {
         return type == TableDataPath.DETAIL;
      }
      else {
         return type == TableDataPath.TRAILER;
      }
   }

   /**
    * Test if a cell belongs to a table data path.
    * @param row the specified table cell row
    * @param col the specified table cell col
    * @param path the specified table data path
    * @return true if the cell belongs to the table data path, false
    * otherwise
    */
   @Override
   public boolean isCellDataPath(int row, int col, TableDataPath path) {
      if(path.getPath().length == 0) {
         return false;
      }

      if(!path.getPath()[0].equals(getHeader(col).toString())) {
         return false;
      }

      return isCellDataPathType(row, col, path);
   }

   /**
    * Get level of a specified table row, which is required for nested table.
    * The default value is <tt>-1</tt>.
    * @param row the specified table row
    * @return level of the table row
    */
   @Override
   public int getRowLevel(int row) {
      return -1;
   }

   /**
    * Get table type which is one of the table types defined in table data
    * descriptor like <tt>NORMAL_TABLE</tt>, <tt>CROSSTAB_TABLE</tt>, etc.
    * @return table type
    */
   @Override
   public int getType() {
      return NORMAL_TABLE;
   }

   /**
    * Get meta info of a specified table data path.
    * @param path the specified table data path
    * @return meta info of the table data path
    */
   @Override
   public XMetaInfo getXMetaInfo(TableDataPath path) {
      return null;
   }

   @Override
   public List<TableDataPath> getXMetaInfoPaths() {
      return null;
   }

   /**
    * Check if contains format.
    * @return <tt>true</tt> if contains format, <tt>false</tt> otherwise.
    */
   @Override
   public boolean containsFormat() {
      return false;
   }

   /**
    * Check if contains drill.
    * @return <tt>true</tt> if contains drill, <tt>false</tt> otherwise
    */
   @Override
   public boolean containsDrill() {
      return false;
   }

   /**
    * Get the column header for data path.
    */
   protected Object getHeader(int col) {
      return getHeader(col, true);
   }

   /**
    * Get the column header for data path.
    */
   protected Object getHeader(int col, boolean pass) {
      if(headers == null) {
         headers = new Object[table.getColCount()];
      }

      if(col < headers.length && headers[col] != null) {
         return headers[col];
      }

      // @by mikec, use identifier is dangerous here
      // iscope like trimed version of header(no entity) but
      // report side like non-trimed version of header(with entity.attribute),
      // use identifier will not support both side.
      // Use column header instead will fail when user changed column header
      // by script, due to the fact that only attribute tablelens and
      // xnodetablelens support setObject and xnodetable have already have
      // a logic to return original column name, here we will pass on the
      // column name to the base table of an attribute table lens.
      XTable data = attrlens != null && pass ? attrlens.getTable() : table;
      return headers[col] = Util.getHeader(data, col);
   }

   // optimization, moreRows is called allow to avoid delegating if we already know the answer
   private boolean moreRows(int r) {
      if(r < rowMax) {
         return true;
      }

      boolean rc = table.moreRows(r);

      if(rc) {
         rowMax = r + 1;
      }

      return rc;
   }

   private XTable table;
   private AttributeTableLens attrlens;
   private transient Object[] headers;
   private transient int rowMax = 0;
}
