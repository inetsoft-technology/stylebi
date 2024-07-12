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
package inetsoft.report;

import inetsoft.uql.XMetaInfo;

import java.io.Serializable;
import java.util.List;

/**
 * Normally, a table lens contains a table data descriptor for users to get
 * table structural infos.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public interface TableDataDescriptor extends Serializable {
   /**
    * Unknown table.
    */
   public static final int UNKNOWN_TABLE = 0x00;
   /**
    * Normal table.
    */
   public static final int NORMAL_TABLE = 0x01;
   /**
    * Table summary table.
    */
   public static final int TABLE_SUMMARY_TABLE = 0x02;
   /**
    * Summary table.
    */
   public static final int SUMMARY_TABLE = 0x04;
   /**
    * Grouped table.
    */
   public static final int GROUPED_TABLE = 0x08;
   /**
    * Crosstab table.
    */
   public static final int CROSSTAB_TABLE = 0x10;
   /**
    * Calc table. A table generated from a CalcTable with possible dynamic
    * cell expansions.
    */
   public static final int CALC_TABLE = 0x40;

   /**
    * Get table data path of a specified table column.
    * @param col the specified table column
    * @return table data path of the table column
    */
   public TableDataPath getColDataPath(int col);

   /**
    * Get table data path of a specified table row.
    * @param row the specified table row
    * @return table data path of the table row
    */
   public TableDataPath getRowDataPath(int row);

   /**
    * Get table data path of a specified table cell.
    * @param row the specified table cell row
    * @param col the specified table cell col
    * @return table data path of the table cell
    */
   public TableDataPath getCellDataPath(int row, int col);

   /**
    * Check if a column belongs to a table data path.
    * @param col the specified table col
    * @param path the specified table data path
    * @return true if the col belongs to the table data path, false otherwise
    */
   public boolean isColDataPath(int col, TableDataPath path);

   /**
    * Check if a row belongs to a table data path.
    * @param row the specified table row
    * @param path the specified table data path
    * @return true if the row belongs to the table data path, false otherwise
    */
   public boolean isRowDataPath(int row, TableDataPath path);

   /**
    * Check if a cell belongs to a table data path in a loose way.
    * Note: when checking, path in the table data path will be ignored.
    * @param row the specified table cell row
    * @param col the specified table cell col
    * @param path the specified table data path
    * @return true if the cell belongs to the table data path, false otherwise
    */
   public boolean isCellDataPathType(int row, int col, TableDataPath path);

   /**
    * Check if a cell belongs to a table data path.
    * @param row the specified table cell row
    * @param col the specified table cell col
    * @param path the specified table data path
    * @return true if the cell belongs to the table data path, false otherwise
    */
   public boolean isCellDataPath(int row, int col, TableDataPath path);

   /**
    * Get level of a specified table row, which is required for nested table.
    * The default value is <tt>-1</tt>.
    * @param row the specified table row
    * @return level of the table row
    */
   public int getRowLevel(int row);

   /**
    * Get table type which is one of the table types defined in table data
    * descriptor like <tt>NORMAL_TABLE</tt>, <tt>CROSSTAB_TABLE</tt>, etc.
    * @return table type
    */
   public int getType();

   /**
    * Get meta info of a specified table data path.
    * @param path the specified table data path
    * @return meta info of the table data path
    */
   public XMetaInfo getXMetaInfo(TableDataPath path);

   /**
    * @return the table data paths for metainfos.
    */
   public List<TableDataPath> getXMetaInfoPaths();

   /**
    * Check if contains format.
    * @return true if contains format
    */
   public boolean containsFormat();

   /**
    * Check if contains drill.
    * @return <tt>true</tt> if contains drill, <tt>false</tt> otherwise
    */
   public boolean containsDrill();
}
