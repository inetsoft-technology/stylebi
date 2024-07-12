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

import inetsoft.report.TableLens;

/**
 * This interface is implemented by all grouped table to provide information
 * on the grouping.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface GroupedTable extends TableLens, RegionTable {
   /**
    * Group headers are added as new rows on top of the grouped rows.
    */
   public static final int GROUP_HEADER_ROWS = 1;
   /**
    * Group headers are displayed as part of the grouped rows.
    */
   public static final int GROUP_HEADER_IN_PLACE = 2;
   /**
    * Group headers are displayed as part of the grouped rows, and
    * all columns are included in every header row.
    */
   public static final int GROUP_HEADER_FULL = 0x1001;

   /**
    * Return the number of grouping columns. Multiple column group is
    * counted as one group column.
    */
   public int getGroupColCount();

   /**
    * Check if a row is displaying group header.
    * @param r row number.
    */
   public boolean isGroupHeaderRow(int r);

   /**
    * Check if a cell is a group header cell. This is more accurate than
    * the isGroupHeaderRow() because it takes into consideration of the
    * in-place header rows (where it's partially a header and body).
    * @param r row number.
    * @param c column number.
    * @return true if the cell is a group header cell.
    */
   @Override
   public boolean isGroupHeaderCell(int r, int c);

   /**
    * Get the number of columns used in the specified grouping level.
    * This is normally 1 unless multiple columns are combined into
    * one group.
    */
   public int getGroupColumns(int level);

   /**
    * Get the grouping level of this group header. The row number must
    * refer to a header row.
    * @return grouping level. The top-most group is level 0. Returns -1
    * if the row is not a header row.
    */
   @Override
   public int getGroupLevel(int r);

   /**
    * Get a group col's group level.
    * @param col the group col.
    * @return the group col's group level, <tt>-1</tt> means not a group col.
    */
   public int getGroupColLevel(int col);

   /**
    * Check if the group column contents are shown. This is true
    * by default. If it's false, the group columns are hidden.
    * @return true if group columns are shown.
    */
   public boolean isShowGroupColumns();

   /**
    * Set the show group column contents option. If it's turned off,
    * the grouped columns will have
    * empty contents. If the ShowGroupColumns is set to false,
    * the AddGroupHeader is automatically turned on.
    * @param grp show group column contents.
    */
   public void setShowGroupColumns(boolean grp);

   /**
    * Check if group header is to be added to the grouped data.
    * @return true if group header is added. Default to false.
    */
   public boolean isAddGroupHeader();

   /**
    * Set whether group headers are added to the table. Group headers
    * are separate rows containing only the group column value for the
    * section.
    */
   public void setAddGroupHeader(boolean h);

   /**
    * Get the group header style.
    * @return group header style.
    */
   public int getGroupHeaderStyle();

   /**
    * Set the group header style. This must be called before the refresh()
    * is called.
    * @param headerS one of GROUP_HEADER_IN_PLACE, GROUP_HEADER_ROWS (default).
    */
   public void setGroupHeaderStyle(int headerS);

   /**
    * Check if a row is a summary row.
    * @param row the row number.
    * @return true if the row is a summary row.
    */
   @Override
   public boolean isSummaryRow(int row);

   /**
    * Check if a column is a summary column.
    * @param col the column number.
    * @return true if the column is a summary column.
    */
   public boolean isSummaryCol(int col);

   /**
    * Get the grouping level of a summary row. The row number must
    * refer to a summary row.
    * @return grouping level. The top-most group is level 0. Returns -1
    * if the row is not a summary row.
    */
   @Override
   public int getSummaryLevel(int r);

   /**
    * Check if this table contains grand summary row.
    * @return true if grand summary row exists.
    */
   public boolean hasGrandSummary();

   /**
    * Get the first row at specified row and group level.
    *
    * @param row the specified row
    * @param level the specified group level
    * @return the first row, <code>-1</code> if not available
    */
   public int getGroupFirstRow(int row, int level);

   /**
    * Get the last row at specified row and group level.
    *
    * @param row the specified row
    * @param level the specified group level
    * @return the last row, <code>-1</code> if not available
    */
   public int getGroupLastRow(int row, int level);

   /**
    * Get the first row at the specified row. The default group level is
    * the highest available group level at the specified row.
    *
    * @param row the specified row
    * @return the first row, <code>-1<code> if not available
    */
   public int getGroupFirstRow(int row);

   /**
    * Get the last row at the specified row. The default group level is
    * the highest available group level at the specified row.
    *
    * @param row the specified row
    * @return the last row, <code>-1</code> if not available
    */
   public int getGroupLastRow(int row);

   /**
    * Get available group levels of a row when get group first/last row.
    *
    * @param row the specified row
    * @return the available group level array
    */
   public int[] getAvailableLevels(int row);

   /**
    * Get the base table row index corresponding to the grouped table.
    * If the row does not exist in the base table, it returns -1.
    * @param row row index in the grouped table.
    * @return corresponding row index in the base table.
    */
   public int getBaseRowIndex(int row);

   /**
    * Get the group formula of the column.
    * @param col column index in the grouped table.
    * @return the group formula of column
    */
   public Formula getGroupFormula(int col);

   /**
    * Get the grand total formula of the column.
    * @param col column index in the grouped table.
    * @return the grand total formula of column
    */
   public Formula getGrandFormula(int col);

   /**
    * Set merging group cell option. If set, group cells of the same group are
    * merged into one span cell.
    */
   public void setMergeGroupCells(boolean merge);

   /**
    * Check the merge group cell option.
    */
   public boolean isMergeGroupCells();
}