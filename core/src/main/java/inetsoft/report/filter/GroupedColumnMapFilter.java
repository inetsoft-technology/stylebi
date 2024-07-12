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
package inetsoft.report.filter;

import inetsoft.report.*;
import inetsoft.uql.XMetaInfo;

import java.awt.*;
import java.util.List;

/**
 * This filter performs remapping grouped table of column numbers.
 * The columns can be reordered by specifying the new order in the column map.
 * It can also be used to extract a subset of columns from a grouped table by
 * using a partial column number map.
 *
 * @version 6.0
 * @author InetSoft Technology Corp
 */
public class GroupedColumnMapFilter extends AbstractGroupedTable
   implements Cloneable {
   /**
    * The map[i] points to the column number in the base table. The columns
    * are reordered according to the mapping.
    *
    * @param table the grouped table
    * @param map the column map array
    */
   public GroupedColumnMapFilter(GroupedTable table, int[] map) {
      this.map = map;
      setTable(table);
   }

   /**
    * Get the original table of this filter.
    *
    * @return the original table
    */
   @Override
   public TableLens getTable() {
      return table;
   }

   /**
    * Set the base table of this filter. The base table must be a grouped table.
    *
    * @param table the specified grouped table
    */
   @Override
   public void setTable(TableLens table) {
      if(table instanceof GroupedTable) {
         setMergeGroupCells(((GroupedTable) table).isMergeGroupCells());
      }

      this.table = (GroupedTable) table;
      invalidate();
      this.table.addChangeListener(new DefaultTableChangeListener(this));
   }

   /**
    * Check the merge group cell option.
    */
   @Override
   public boolean isMergeGroupCells() {
      if(table instanceof GroupedTable) {
         return ((GroupedTable) table).isMergeGroupCells();
      }

      return false;
   }

   @Override
   public boolean isMergedGroup(int c) {
      if(c >= 0 && c < map.length) {
         if(table instanceof AbstractGroupedTable) {
            return ((AbstractGroupedTable) table).isMergedGroup(map[c]);
         }
      }

      return super.isMergedGroup(c);
   }

   /**
    * Get internal table data descriptor which contains table structural infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(gdescriptor == null) {
         gdescriptor =
            new GroupedMapFilterDataDescriptor(table.getDescriptor());
      }

      return gdescriptor;
   }

   /**
    * Get the base table row index corresponding to the filtered table.
    * If the row does not exist in the base table, it returns -1.
    *
    * @param row row index in the filtered table
    * @return corresponding row index in the base table
    */
   @Override
   public int getBaseRowIndex(int row) {
      return row;
   }

   /**
    * Get the base table column index corresponding to the filtered table.
    * If the column does not exist in the base table, it returns -1.
    *
    * @param col column index in  the filtered table
    * @return corresponding column index in the bast table
    */
   @Override
   public int getBaseColIndex(int col) {
      return map[col];
   }

   /**
    * Invalidate the table filter forcely, and the table filter will perform
    * filtering calculation to validate itself.
    */
   @Override
   public void invalidate() {
      gdescriptor = null;
      clearCache();
      fireChangeEvent();
   }

   /**
    * Get the number of grouping columns. Multiple column group is
    * counted as one group column.
    *
    * @return the grouping column number
    */
   @Override
   public int getGroupColCount() {
      return table.getGroupColCount();
   }

   /**
    * Check if a row is displaying group header.
    * @param r row number
    * @return true if is, false otherwise
    */
   @Override
   public boolean isGroupHeaderRow(int r) {
      return table.isGroupHeaderRow(r);
   }

   /**
    * Check if a cell is a group header cell. This is more accurate than
    * the isGroupHeaderRow() because it takes into consideration of the
    * in-place header rows (where it's partially a header and body).
    *
    * @param r row number
    * @param c column number
    * @return true if the cell is a group header cell
    */
   @Override
   public boolean isGroupHeaderCell(int r, int c) {
      if(c < 0 || c >= map.length) {
         return false;
      }

      return table.isGroupHeaderCell(r, map[c]);
   }

   /**
    * Get the number of columns used in the specified grouping level.
    * This is normally 1 unless multiple columns are combined into
    * one group.
    *
    * @param level the specified group level
    * @return the column number of the group level
    */
   @Override
   public int getGroupColumns(int level) {
      return table.getGroupColumns(level);
   }

   /**
    * Get the grouping level of this group header. The row number must
    * refer to a header row.
    *
    * @param r the specified row
    * @return grouping level. The top-most group is level 0. Returns -1
    * if the row is not a header row
    */
   @Override
   public int getGroupLevel(int r) {
      return table.getGroupLevel(r);
   }

   /**
    * Get the internal keep row level for the first row of a group.
    */
   @Override
   public int getGroupLevel0(int r) {
      if(table instanceof AbstractGroupedTable) {
         return ((AbstractGroupedTable) table).getGroupLevel0(r);
      }

      return super.getGroupLevel0(r);
   }

   /**
    * Get a group col's group level.
    *
    * @param col the group col
    * @return the group col's group level, <code>-1</code> means not a group col
    */
   @Override
   public int getGroupColLevel(int col) {
      if(col >= 0 && col < map.length) {
         return table.getGroupColLevel(map[col]);
      }

      return -1;
   }

   /**
    * Check if the group column contents are shown. This is true
    * by default. If it's false, the group columns are hidden.
    * @return true if group columns are shown.
    */
   @Override
   public boolean isShowGroupColumns() {
      return ((GroupedTable) table).isShowGroupColumns();
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
      ((GroupedTable) table).setShowGroupColumns(grp);
   }

   /**
    * Check if group header is to be added to the grouped data.
    * @return true if group header is added. Default to false.
    */
   @Override
   public boolean isAddGroupHeader() {
      return ((GroupedTable) table).isAddGroupHeader();
   }

   /**
    * Set whether group headers are added to the table. Group headers
    * are separate rows containing only the group column value for the
    * section.
    */
   @Override
   public void setAddGroupHeader(boolean h) {
      ((GroupedTable) table).setAddGroupHeader(h);
   }

   /**
    * Get the group header style.
    * @return group header style.
    */
   @Override
   public int getGroupHeaderStyle() {
      return ((GroupedTable) table).getGroupHeaderStyle();
   }

   /**
    * Set the group header style. This must be called before the refresh()
    * is called.
    * @param headerS one of GROUP_HEADER_IN_PLACE, GROUP_HEADER_ROWS (default).
    */
   @Override
   public void setGroupHeaderStyle(int headerS) {
      ((GroupedTable) table).setGroupHeaderStyle(headerS);
   }

   /**
    * Check if a row is a summary row.
    *
    * @param row the row number
    * @return true if the row is a summary row
    */
   @Override
   public boolean isSummaryRow(int row) {
      return table.isSummaryRow(row);
   }

   /**
    * Check if a column is a summary column.
    *
    * @param c the column number
    * @return true if the column is a summary column
    */
   @Override
   public boolean isSummaryCol(int c) {
      if(c < 0 || c >= map.length) {
         return false;
      }

      return table.isSummaryCol(map[c]);
   }

   /**
    * Get the grouping level of a summary row. The row number must
    * refer to a summary row.
    *
    * @return grouping level. The top-most group is level 0. Returns -1
    * if the row is not a summary row
    */
   @Override
   public int getSummaryLevel(int r) {
      return table.getSummaryLevel(r);
   }

   /**
    * Check if this table contains grand summary row.
    *
    * @return true if grand summary row exists
    */
   @Override
   public boolean hasGrandSummary() {
      return table.hasGrandSummary();
   }

   /**
    * Get the first row at specified row and group level.
    *
    * @param row the specified row
    * @param level the specified group level
    * @return the first row, <code>-1</code> if not available
    */
   @Override
   public int getGroupFirstRow(int row, int level) {
      return table.getGroupFirstRow(row, level);
   }

   /**
    * Get the last row at specified row and group level.
    *
    * @param row the specified row
    * @param level the specified group level
    * @return the last row, <code>-1</code> if not available
    */
   @Override
   public int getGroupLastRow(int row, int level) {
      return table.getGroupLastRow(row, level);
   }

   /**
    * Get the first row at the specified row. The default group level is
    * the highest available group level at the specified row.
    *
    * @param row the specified row
    * @return the first row, <code>-1<code> if not available
    */
   @Override
   public int getGroupFirstRow(int row) {
      return table.getGroupFirstRow(row);
   }

   /**
    * Get the last row at the specified row. The default group level is
    * the highest available group level at the specified row.
    *
    * @param row the specified row
    * @return the last row, <code>-1</code> if not available
    */
   @Override
   public int getGroupLastRow(int row) {
      return table.getGroupLastRow(row);
   }

   /**
    * Get available group levels of a row when get group first/last row.
    *
    * @param row the specified row
    * @return the available group level array
    */
   @Override
   public int[] getAvailableLevels(int row) {
      return table.getAvailableLevels(row);
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
      return map.length;
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
    * return -1. A special value, StyleConstants.REMAINDER, can be returned
    * by this method to indicate that width of this column should be
    * calculated based on the remaining space after all other columns'
    * widths are satisfied. If there are more than one column that return
    * REMAINDER as their widths, the remaining space is distributed
    * evenly among these columns.
    * @return column width.
    */
   @Override
   public int getColWidth(int col) {
      if(col >= 0 && col < getColCount()) {
         return table.getColWidth(map[col]);
      }
      else {
         return -1;
      }
   }

   /**
    * Get the current column content type.
    * @param col column number.
    * @return column type.
    */
   @Override
   public Class getColType(int col) {
      return table.getColType(map[col]);
   }

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getRowBorderColor(int r, int c) {
      return table.getRowBorderColor(r, (c < 0) ? c : map[c]);
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      return table.getColBorderColor(r, (c < 0) ? c : map[c]);
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
      return table.getRowBorder(r, (c < 0) ? c : map[c]);
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
      return table.getColBorder(r, (c < 0) ? c : map[c]);
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      return table.getInsets(r, map[c]);
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      return table.getAlignment(r, map[c]);
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      return table.getFont(r, map[c]);
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
      return table.isLineWrap(r, map[c]);
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
      return table.getForeground(r, map[c]);
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
      return table.getBackground(r, map[c]);
   }

   /**
    * Check if is primitive.
    * @return <tt>true</tt> if is primitive, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isPrimitive(int col) {
      return table.isPrimitive(map[col]);
   }

   /**
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isNull(int r, int c) {
      return table.isNull(r, map[c]);
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      return table.getObject(r, map[c]);
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the double value in the specified row.
    */
   @Override
   public final double getDouble(int r, int c) {
      return table.getDouble(r, map[c]);
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the float value in the specified row.
    */
   @Override
   public final float getFloat(int r, int c) {
      return table.getFloat(r, map[c]);
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the long value in the specified row.
    */
   @Override
   public final long getLong(int r, int c) {
      return table.getLong(r, map[c]);
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the int value in the specified row.
    */
   @Override
   public final int getInt(int r, int c) {
      return table.getInt(r, map[c]);
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the short value in the specified row.
    */
   @Override
   public final short getShort(int r, int c) {
      return table.getShort(r, map[c]);
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the byte value in the specified row.
    */
   @Override
   public final byte getByte(int r, int c) {
      return table.getByte(r, map[c]);
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the boolean value in the specified row.
    */
   @Override
   public final boolean getBoolean(int r, int c) {
      return table.getBoolean(r, map[c]);
   }

   /**
    * Set the cell value.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      table.setObject(r, map[c], v);
   }

   /**
    * Get the group formula of the column.
    * @param col column index in the grouped table.
    * @return the group formula of column
    */
   @Override
   public Formula getGroupFormula(int col) {
      return table.getGroupFormula(getBaseColIndex(col));
   }

   /**
    * Get the grand total formula of the column.
    * @param col column index in the grouped table.
    * @return the grand total formula of column
    */
   @Override
   public Formula getGrandFormula(int col) {
      return table.getGrandFormula(getBaseColIndex(col));
   }

   /**
    * Dispose the table to clear up temporary resources.
    */
   @Override
   public void dispose() {
      table.dispose();
   }

   /**
    * GroupedColumnMapFilter data descriptor.
    */
   private class GroupedMapFilterDataDescriptor implements TableDataDescriptor {
      /**
       * Create a GroupedMapFilterDataDescriptor.
       * @param descriptor the base descriptor
       */
      public GroupedMapFilterDataDescriptor(TableDataDescriptor descriptor) {
         this.descriptor = descriptor;
      }

      /**
       * Get table data path of a specified table column.
       * @param col the specified table column
       * @return table data path of the table column
       */
      @Override
      public TableDataPath getColDataPath(int col) {
         return descriptor.getColDataPath(map[col]);
      }

      /**
       * Get table data path of a specified table row.
       * @param row the specified table row
       * @return table data path of the table row
       */
      @Override
      public TableDataPath getRowDataPath(int row) {
         return descriptor.getRowDataPath(row);
      }

      /**
       * Get table data path of a specified table cell.
       * @param row the specified table cell row
       * @param col the specified table cell col
       * @return table data path of the table cell
       */
      @Override
      public TableDataPath getCellDataPath(int row, int col) {
         return descriptor.getCellDataPath(row, map[col]);
      }

      /**
       * Check if a column belongs to a table data path.
       * @param col the specified table col
       * @param path the specified table data path
       * @return true if the col belongs to the table data path, false otherwise
       */
      @Override
      public boolean isColDataPath(int col, TableDataPath path) {
         return descriptor.isColDataPath(map[col], path);
      }

      /**
       * Check if a row belongs to a table data path.
       * @param row the specified table row
       * @param path the specified table data path
       * @return true if the row belongs to the table data path, false otherwise
       */
      @Override
      public boolean isRowDataPath(int row, TableDataPath path) {
         return descriptor.isRowDataPath(row, path);
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
         return descriptor.isCellDataPathType(row, map[col], path);
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
         return descriptor.isCellDataPath(row, map[col], path);
      }

      /**
       * Get level of a specified table row, which is required for nested table.
       * The default value is <tt>-1</tt>.
       * @param row the specified table row
       * @return level of the table row
       */
      @Override
      public int getRowLevel(int row) {
         return descriptor.getRowLevel(row);
      }

      /**
       * Get table type which is one of the table types defined in table data
       * descriptor like <tt>NORMAL_TABLE</tt>, <tt>CROSSTAB_TABLE</tt>, etc.
       * @return table type
       */
      @Override
      public int getType() {
         return GROUPED_TABLE;
      }

      /**
       * Get meta info of a specified table data path.
       * @param path the specified table data path
       * @return meta info of the table data path
       */
      @Override
      public XMetaInfo getXMetaInfo(TableDataPath path) {
         return descriptor.getXMetaInfo(path);
      }

      @Override
      public List<TableDataPath> getXMetaInfoPaths() {
         return descriptor.getXMetaInfoPaths();
      }

      /**
       * Check if contains format.
       * @return true if contains format
       */
      @Override
      public boolean containsFormat() {
         return descriptor.containsFormat();
      }

      /**
       * Check if contains drill.
       * @return <tt>true</tt> if contains drill
       */
      @Override
      public boolean containsDrill() {
         return descriptor.containsDrill();
      }

      private TableDataDescriptor descriptor;
   }

   private GroupedTable table;
   private int[] map; // column mapping
   private TableDataDescriptor gdescriptor;
}
