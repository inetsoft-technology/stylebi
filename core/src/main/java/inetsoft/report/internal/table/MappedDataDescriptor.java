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
package inetsoft.report.internal.table;

import inetsoft.report.*;
import inetsoft.uql.XMetaInfo;

import java.util.List;

/**
 * TableDataDescriptor implementation for table filters that perform row
 * mapping. The methods in this class delegate to the base data descriptor
 * with the base row index.
 *
 * @author InetSoft Technology
 * @since  7.0
 */
public class MappedDataDescriptor implements TableDataDescriptor {
   /**
    * Creates a new instance of MappedDataDescriptor.
    *
    * @param table the table for which this descriptor is being created. This is
    *              not the base table, but the table that performs the row
    *              mapping.
    * @param descriptor the data descriptor of the base table.
    */
   public MappedDataDescriptor(TableFilter table, TableDataDescriptor descriptor)
   {
      this.table = table;
      this.descriptor = descriptor;
   }

   /**
    * Get table data path of a specified table column.
    *
    * @param col the specified table column
    *
    * @return table data path of the table column
    */
   @Override
   public TableDataPath getColDataPath(int col) {
      col = getBaseColIndex(col);
      return (col < 0) ? null : descriptor.getColDataPath(col);
   }

   /**
    * Get table data path of a specified table row.
    *
    * @param row the specified table row
    *
    * @return table data path of the table row
    */
   @Override
   public TableDataPath getRowDataPath(int row) {
      row = getBaseRowIndex(row);
      return (row < 0) ? null : descriptor.getRowDataPath(row);
   }

   /**
    * Get table data path of a specified table cell.
    *
    * @param row the specified table cell row
    * @param col the specified table cell col
    *
    * @return table data path of the table cell
    */
   @Override
   public TableDataPath getCellDataPath(int row, int col) {
      row = getBaseRowIndex(row, col);
      col = getBaseColIndex(row, col);
      return (row < 0 || col < 0) ? null : descriptor.getCellDataPath(row, col);
   }

   /**
    * Check if a column belongs to a table data path.
    *
    * @param col the specified table col
    * @param path the specified table data path
    *
    * @return true if the col belongs to the table data path, false otherwise
    */
   @Override
   public boolean isColDataPath(int col, TableDataPath path) {
      col = getBaseColIndex(col);
      return (col < 0) ? false : descriptor.isColDataPath(col, path);
   }

   /**
    * Check if a row belongs to a table data path.
    *
    * @param row the specified table row
    * @param path the specified table data path
    *
    * @return true if the row belongs to the table data path, false otherwise
    */
   @Override
   public boolean isRowDataPath(int row, TableDataPath path) {
      row = getBaseRowIndex(row);
      return (row < 0) ? false : descriptor.isRowDataPath(row, path);
   }

   /**
    * Check if a cell belongs to a table data path in a loose way.
    * Note: when checking, path in the table data path will be ignored.
    *
    * @param row the specified table cell row
    * @param col the specified table cell col
    * @param path the specified table data path
    *
    * @return true if the cell belongs to the table data path, false otherwise
    */
   @Override
   public boolean isCellDataPathType(int row, int col, TableDataPath path) {
      row = getBaseRowIndex(row, col);
      col = getBaseColIndex(row, col);
      return (row < 0 || col < 0) ? false 
         : descriptor.isCellDataPathType(row, col, path);
   }

   /**
    * Check if a cell belongs to a table data path.
    *
    * @param row the specified table cell row
    * @param col the specified table cell col
    * @param path the specified table data path
    *
    * @return true if the cell belongs to the table data path, false otherwise
    */
   @Override
   public boolean isCellDataPath(int row, int col, TableDataPath path) {
      row = getBaseRowIndex(row, col);
      col = getBaseColIndex(row, col);
      return (row < 0 || col < 0) ? false 
         : descriptor.isCellDataPath(row, col, path);
   }

   /**
    * Get level of a specified table row, which is required for nested table.
    * The default value is <tt>-1</tt>.
    *
    * @param row the specified table row
    *
    * @return level of the table row
    */
   @Override
   public int getRowLevel(int row) {
      row = getBaseRowIndex(row);
      return (row < 0) ? -1 : descriptor.getRowLevel(row);
   }

   /**
    * Get table type which is one of the table types defined in table data
    * descriptor like <tt>NORMAL_TABLE</tt>, <tt>CROSSTAB_TABLE</tt>, etc.
    *
    * @return table type
    */
   @Override
   public int getType() {
      return descriptor.getType();
   }

   /**
    * Get table xmeta info.
    *
    * @param path the specified table data path
    * @return meta info of the table data path
    */
   @Override
   public XMetaInfo getXMetaInfo(TableDataPath path) {
      return (path == null) ? null : descriptor.getXMetaInfo(path);
   }

   @Override
   public List<TableDataPath> getXMetaInfoPaths() {
      return descriptor.getXMetaInfoPaths();
   }

   /**
    * Check if contains format.
    *
    * @return true if contains format
    */
   @Override
   public boolean containsFormat() {
      return descriptor.containsFormat();
   }

   /**
    * Check if contains drill.
    *
    * @return <tt>true</tt> if contains drill
    */
   @Override
   public boolean containsDrill() {
      return descriptor.containsDrill();
   }

   /**
    * Get the corresponding row index in the base table.
    */
   protected int getBaseRowIndex(int row) {
      return table.getBaseRowIndex(row);
   }

   /**
    * Get the corresponding row index in the base table.
    */
   protected int getBaseRowIndex(int row, int col) {
      return table.getBaseRowIndex(row);
   }

   /**
    * Get the corresponding column index in the base table.
    */
   protected int getBaseColIndex(int col) {
      return table.getBaseColIndex(col);
   }

   /**
    * Get the corresponding column index in the base table.
    */
   protected int getBaseColIndex(int row, int col) {
      return table.getBaseColIndex(col);
   }

   private TableFilter table;
   private TableDataDescriptor descriptor;
}
