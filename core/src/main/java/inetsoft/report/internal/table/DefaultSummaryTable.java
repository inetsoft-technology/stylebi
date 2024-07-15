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
import inetsoft.report.filter.*;
import inetsoft.report.internal.Util;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.util.Tool;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * This class can be used to create a table lens who have the same
 * table data path with the SummaryFilter but do not process summary
 * aggregation.
 *
 * @version 9.5, 12/2/2008
 * @author InetSoft Technology Corp
 */
public class DefaultSummaryTable extends AbstractGroupedTable
   implements TableFilter
{
   /**
    * Create a sorted table without actual sorting.
    * @param table base table.
    * @param cols sort columns.
    */
   public DefaultSummaryTable(TableLens table, int[] cols) {
      setTable(table);
      this.cols = cols;
   }

   /**
    * Get summary level of a row.
    * @param row the specified row.
    * @return summary level of the specified row.
    */
   @Override
   public int getSummaryLevel(int row) {
      if(row < getHeaderRowCount()) {
         return -1;
      }

      // all rows are summary rows in a SummaryFilter
      return - 1;
   }

   /**
    * Return the number of grouping columns. Multiple column group is
    * counted as one group column.
    */
   @Override
   public int getGroupColCount() {
      return cols.length;
   }

   /**
    * Check if a row is displaying group header.
    * @param r row number.
    */
   @Override
   public boolean isGroupHeaderRow(int r) {
      return false;
   }

   /**
    * Check if a cell is a group header cell. This is more accurate than
    * the isGroupHeaderRow() because it takes into consideration of the
    * in-place header rows (where it's partially a header and body).
    * @param r row number.
    * @param c column number.
    * @return true if the cell is a group header cell.
    */
   @Override
   public boolean isGroupHeaderCell(int r, int c) {
      return r >= getHeaderRowCount() && c < cols.length;
   }

   /**
    * Get the number of columns used in the specified grouping level.
    * This is normally 1 unless multiple columns are combined into
    * one group.
    */
   @Override
   public int getGroupColumns(int level) {
      return 1;
   }

   /**
    * Get the grouping level of this group header. The row number must
    * refer to a header row.
    * @return grouping level. The top-most group is level 0. Returns -1
    * if the row is not a header row.
    */
   @Override
   public int getGroupLevel(int r) {
      return -1;
   }

   /**
    * Check if the group column contents are shown. This is true
    * by default. If it's false, the group columns are hidden.
    * @return true if group columns are shown.
    */
   @Override
   public boolean isShowGroupColumns() {
      return true;
   }

   /**
    * Set the show group column contents option. If it's turned off,
    * the grouped columns will have
    * empty contents. If the ShowGroupColumns is set to false,
    * the AddGroupHeader is automatically turned on.
    * @param grp show group column contents.
    */
   @Override
   public void setShowGroupColumns(boolean grp) {
      // always true
   }

   /**
    * Check if group header is to be added to the grouped data.
    * @return true if group header is added. Default to false.
    */
   @Override
   public boolean isAddGroupHeader() {
      return false;
   }

   /**
    * Set whether group headers are added to the table. Group headers
    * are separate rows containing only the group column value for the
    * section.
    */
   @Override
   public void setAddGroupHeader(boolean h) {
      // always false
   }

   /**
    * Get the group header style.
    * @return group header style.
    */
   @Override
   public int getGroupHeaderStyle() {
      return GROUP_HEADER_IN_PLACE;
   }

   /**
    * Set the group header style. This must be called before the refresh()
    * is called.
    * @param headerS one of GROUP_HEADER_IN_PLACE, GROUP_HEADER_ROWS (default).
    */
   @Override
   public void setGroupHeaderStyle(int headerS) {
      // fixed
   }

   /**
    * Check if a row is a summary row.
    * @param row the row number.
    * @return true if the row is a summary row.
    */
   @Override
   public boolean isSummaryRow(int row) {
      return row >= getHeaderRowCount();
   }

   /**
    * Check if a column is a summary column.
    * @param col the column number.
    * @return true if the column is a summary column.
    */
   @Override
   public boolean isSummaryCol(int col) {
      return col >= cols.length;
   }

   /**
    * Check if this table contains grand summary row.
    * @return true if grand summary row exists.
    */
   @Override
   public boolean hasGrandSummary() {
      return false;
   }

   /**
    * Get the group formula of the column.
    * @param col column index in the grouped table.
    * @return the group formula of column
    */
   @Override
   public Formula getGroupFormula(int col) {
      return null;
   }

   /**
    * Get the grand total formula of the column.
    * @param col column index in the grouped table.
    * @return the grand total formula of column
    */
   @Override
   public Formula getGrandFormula(int col) {
      return null;
   }

   ////grouped table over

   /**
    * Invalidate the table filter forcely, and the table filter will perform
    * filtering calculation to validate itself.
    */
   @Override
   public void invalidate() {
      mmap = new Hashtable();
      fireChangeEvent();
   }

   /**
    * Get the original table of this filter.
    */
   @Override
   public TableLens getTable() {
      return this.table;
   }

   /**
    * Set the base table of this filter. The base table must be a sorted table.
    */
   @Override
   public void setTable(TableLens table) {
      this.table = table;
      invalidate();
      this.table.addChangeListener(new DefaultTableChangeListener(this));
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(sdescriptor == null) {
         sdescriptor = new DefaultSummaryDescriptor();
      }

      return sdescriptor;
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
      return 0;
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      return StyleConstants.H_LEFT;
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      if(r < getHeaderRowCount()) {
         return table.getFont(0, 0);
      }

      return null;
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      return table.getObject(r, c);
   }

   /**
    * Set the cell value.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      table.setObject(r, c, v);
   }

   /**
    * Dispose the table to clear up temporary resources.
    */
   @Override
   public void dispose() {
      table.dispose();
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
    * SummaryFilter data descriptor.
    */
   class DefaultSummaryDescriptor implements TableDataDescriptor {
      /**
       * Get table data path of a specified table column.
       * @param col the specified table column
       * @return table data path of the table column
       */
      @Override
      public TableDataPath getColDataPath(int col) {
         String header =
            Util.getHeader(DefaultSummaryTable.this, col).toString();
         return new TableDataPath(header);
      }

      /**
       * Get table data path of a specified table row.
       * @param row the specified table row
       * @return table data path of the table row
       */
      @Override
      public TableDataPath getRowDataPath(int row) {
         if(row < getHeaderRowCount()) { // header
            return new TableDataPath(-1, TableDataPath.HEADER);
         }
         else if(moreRows(row + getTrailerRowCount())) { // summary
            int level = getSummaryLevel(row);
            return new TableDataPath(level, TableDataPath.SUMMARY);
         }
         else { // grand total
            return new TableDataPath(-1, TableDataPath.GRAND_TOTAL);
         }
      }

      /**
       * Get table data path of a specified table cell.
       * @param row the specified table cell row
       * @param col the specified table cell col
       * @return table data path of the table cell
       */
      @Override
      public TableDataPath getCellDataPath(int row, int col) {
         String header =
            Util.getHeader(DefaultSummaryTable.this, col).toString();
         Object val = getObject(row, col);
         Class cls = val == null ? null : val.getClass();
         String dtype = Util.getDataType(cls);

         if(row < getHeaderRowCount()) { // header
            return new TableDataPath(-1, TableDataPath.HEADER, dtype,
               new String[] {header});
         }
         else if(moreRows(row + getTrailerRowCount())) { // summary
            int level = getSummaryLevel(row);
            return new TableDataPath(level, TableDataPath.SUMMARY, dtype,
               new String[] {header});
         }
         else { // grand total
            return new TableDataPath(-1, TableDataPath.GRAND_TOTAL, dtype,
               new String[] {header});
         }
      }

      /**
       * Check if a column belongs to a table data path.
       * @param col the specified table col
       * @param path the specified table data path
       * @return true if the col belongs to the table data path, false otherwise
       */
      @Override
      public boolean isColDataPath(int col, TableDataPath path) {
         String header =
            Util.getHeader(DefaultSummaryTable.this, col).toString();
         return header.equals(path.getPath()[0]);
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
         int level = path.getLevel();

         if(row < getHeaderRowCount()) { // header
            return type == TableDataPath.HEADER;
         }
         else if(moreRows(row + getTrailerRowCount())) { // summary
            return type == TableDataPath.SUMMARY;
         }
         else { // grand total
            return type == TableDataPath.GRAND_TOTAL;
         }
      }

      /**
       * Check if a cell belongs to a table data path in a loose way.
       * Note: when cheking, path in the table data path will be ignored.
       * @param row the specified table cell row
       * @param col the specified table cell col
       * @param path the specified table data path
       * @return true if the cell belongs to the table data path,
       * false otherwise
       */
      @Override
      public boolean isCellDataPathType(int row, int col, TableDataPath path) {
         int type = path.getType();
         int level = path.getLevel();

         if(row < getHeaderRowCount()) { // header
            return type == TableDataPath.HEADER;
         }
         else if(moreRows(row + getTrailerRowCount())) { // summary
            return type == TableDataPath.SUMMARY;
         }
         else { // grand total
            return type == TableDataPath.GRAND_TOTAL;
         }
      }

      /**
       * Check if a cell belongs to a table data path.
       * @param row the specified table cell row
       * @param col the specified table cell col
       * @param path the specified table data path
       * @return true if the cell belongs to the table data path,
       * false otherwise
       */
      @Override
      public boolean isCellDataPath(int row, int col, TableDataPath path) {
         if(!path.getPath()[0].
            equals(Util.getHeader(DefaultSummaryTable.this, col).toString())) {
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
         return getSummaryLevel(row);
      }

      /**
       * Get table type which is one of the table types defined in table data
       * descriptor like <tt>NORMAL_TABLE</tt>, <tt>CROSSTAB_TABLE</tt>, etc.
       * @return table type
       */
      @Override
      public int getType() {
         return SUMMARY_TABLE;
      }

      /**
       * Get table xmeta info.
       * @param path the specified table data path
       */
      @Override
      public XMetaInfo getXMetaInfo(TableDataPath path) {
         if(!path.isCell()) {
            return null;
         }

         Object obj = mmap.get(path);

         if(obj instanceof XMetaInfo) {
            return (XMetaInfo) obj;
         }
         else if(obj != null) {
            return null;
         }

         int type = path.getType();
         String dtype = path.getDataType();
         String header = path.getPath()[0];
         TableDataPath opath = path;

         if(type == TableDataPath.SUMMARY) {
            opath = new TableDataPath(-1,
               TableDataPath.DETAIL, dtype, new String[] {header});
         }
         else if(type == TableDataPath.GRAND_TOTAL) {
            if(columnIndexMap == null) {
               columnIndexMap = new ColumnIndexMap(DefaultSummaryTable.this, true);
            }

            int col = Util.findColumn(columnIndexMap, header, false);

            // only summary cols keep xmeta info
            if(isSummaryCol(col)) {
               opath = new TableDataPath(-1,
                  TableDataPath.DETAIL, dtype, new String[] {header});
            }
            else {
               opath = null;
            }
         }

         TableDataDescriptor desc = table.getDescriptor();
         XMetaInfo minfo = opath == null ? null : desc.getXMetaInfo(opath);
         mmap.put(path, minfo == null ? Tool.NULL : (Object) minfo);

         return minfo;
      }

      @Override
      public List<TableDataPath> getXMetaInfoPaths() {
         List<TableDataPath> list = new ArrayList<>();

         if(!mmap.isEmpty()) {
            list.addAll(mmap.keySet());
         }

         return list;
      }

      /**
       * Check if contains format.
       * @return true if contains format
       */
      @Override
      public boolean containsFormat() {
         return table.containsFormat();
      }

      /**
       * Check if contains drill.
       * @return <tt>true</tt> if contains drill
       */
      @Override
      public boolean containsDrill() {
         return table.containsDrill();
      }

      private transient ColumnIndexMap columnIndexMap = null;
   }

   private TableLens table;
   private int[] cols; // sorting columns
   private Hashtable mmap = new Hashtable(); // xmeta info

   private String[] headers; // summary column headers
   private TableDataDescriptor sdescriptor = null;
}
