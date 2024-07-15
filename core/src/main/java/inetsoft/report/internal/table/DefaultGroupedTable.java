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

import inetsoft.report.filter.Formula;
import inetsoft.report.filter.GroupedTable;
import inetsoft.report.lens.DefaultTableLens;

import java.awt.*;
import java.util.Hashtable;

/**
 * This is a implementation of GroupedTable.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class DefaultGroupedTable extends DefaultTableLens
   implements GroupedTable
{
   /**
    * Set the number of grouping columns.
    */
   public void setGroupColCount(int count) {
      groupColCount = count;
   }

   /**
    * Return the number of grouping columns. Multiple column group is
    * counted as one group column.
    */
   @Override
   public int getGroupColCount() {
      return groupColCount;
   }

   /**
    * Set if a row is a group header row.
    */
   public void setGroupHeaderRow(int r, boolean header) {
      groupHeaderRows.put(Integer.valueOf(r), Boolean.valueOf(header));
   }

   /**
    * Check if a row is displaying group header.
    * @param r row number.
    */
   @Override
   public boolean isGroupHeaderRow(int r) {
      Boolean val = (Boolean) groupHeaderRows.get(Integer.valueOf(r));

      return (val != null) ? val.booleanValue() : false;
   }

   /**
    * Set if a cell is group header cell.
    */
   public void setGroupHeaderCell(int r, int c, boolean header) {
      groupHeaderCells.put(new Point(c, r), Boolean.valueOf(header));
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
      Boolean val = (Boolean) groupHeaderCells.get(new Point(c, r));

      return (val != null) ? val.booleanValue() : false;
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
    * Get the grouping level of a row.
    */
   public void setGroupLevel(int r, int level) {
      groupLevels.put(Integer.valueOf(r), Integer.valueOf(level));
   }

   /**
    * Get the grouping level of this group header. The row number must
    * refer to a header row.
    * @return grouping level. The top-most group is level 0. Returns -1
    * if the row is not a header row.
    */
   @Override
   public int getGroupLevel(int r) {
      Integer val = (Integer) groupLevels.get(Integer.valueOf(r));

      return (val != null) ? val.intValue() : -1;
   }

   /**
    * Get a group col's group level.
    * @param col the group col.
    * @return the group col's group level, <tt>-1</tt> means not a group col.
    */
   @Override
   public int getGroupColLevel(int col) {
      return -1;
   }

   /**
    * Check if the group column contents are shown. This is true
    * by default. If it's false, the group columns are hidden.
    * @return true if group columns are shown.
    */
   @Override
   public boolean isShowGroupColumns() {
      return false;
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
   }

   /**
    * Get the group header style.
    * @return group header style.
    */
   @Override
   public int getGroupHeaderStyle() {
      return 0;
   }

   /**
    * Set the group header style. This must be called before the refresh()
    * is called.
    * @param headerS one of GROUP_HEADER_IN_PLACE, GROUP_HEADER_ROWS (default).
    */
   @Override
   public void setGroupHeaderStyle(int headerS) {
   }

   /**
    * Set if a row is a summary row.
    */
   public void setSummaryRow(int r, boolean summary) {
      summaryRows.put(Integer.valueOf(r), Boolean.valueOf(summary));
   }

   /**
    * Check if a row is a summary row.
    * @param row the row number.
    * @return true if the row is a summary row.
    */
   @Override
   public boolean isSummaryRow(int r) {
      Boolean val = (Boolean) summaryRows.get(Integer.valueOf(r));

      return (val != null) ? val.booleanValue() : false;
   }

   /**
    * Set if a column is a summary column.
    */
   public void setSummaryCol(int c, boolean summary) {
      summaryCols.put(Integer.valueOf(c), Boolean.valueOf(summary));
   }

   /**
    * Check if a column is a summary column.
    * @param column the column number.
    * @return true if the column is a summary column.
    */
   @Override
   public boolean isSummaryCol(int col) {
      Boolean val = (Boolean) summaryCols.get(Integer.valueOf(col));

      return (val != null) ? val.booleanValue() : false;
   }

   /**
    * Get the summary level of a row.
    */
   public void setSummaryLevel(int r, int level) {
      summaryLevels.put(Integer.valueOf(r), Integer.valueOf(level));
   }

   /**
    * Get the grouping level of a summary row. The row number must
    * refer to a summary row.
    * @return grouping level. The top-most group is level 0. Returns -1
    * if the row is not a summary row.
    */
   @Override
   public int getSummaryLevel(int r) {
      Integer val = (Integer) summaryLevels.get(Integer.valueOf(r));

      return (val != null) ? val.intValue() : -1;
   }

   /**
    * Set if the table has a grand summary.
    */
   public void setGrandSummary(boolean grand) {
      this.grand = grand;
   }

   /**
    * Check if this table contains grand summary row.
    * @return true if grand summary row exists.
    */
   @Override
   public boolean hasGrandSummary() {
      return grand;
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
      return -1;
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
      return -1;
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
      return -1;
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
      return -1;
   }

   /**
    * Get available group levels of a row when get group first/last row.
    *
    * @param row the specified row
    * @return the available group level array
    */
   @Override
   public int[] getAvailableLevels(int row) {
      return new int[0];
   }

   /**
    * Set the base row index of a row in a grouped table.
    */
   public void setBaseRowIndex(int r, int baserow) {
      baseRows.put(Integer.valueOf(r), Integer.valueOf(baserow));
   }

   /**
    * Get the base table row index corresponding to the grouped table.
    * If the row does not exist in the base table, it returns -1.
    * @param row row index in the grouped table.
    * @return corresponding row index in the base table.
    */
   @Override
   public int getBaseRowIndex(int r) {
      Integer val = (Integer) baseRows.get(Integer.valueOf(r));

      return (val != null) ? val.intValue() : -1;
   }

   /**
    * Get the original font defined by user.
    *
    * @param row the specified row
    * @param col the specified column
    * @return the original font
    */
   public Font getOriginalFont(int row, int col) {
      return null;
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

   /**
    * Set merging group cell option. If set, group cells of the same group are
    * merged into one span cell.
    */
   @Override
   public void setMergeGroupCells(boolean merge) {
      this.mergeGroup = merge;
   }

   /**
    * Check the merge group cell option.
    */
   @Override
   public boolean isMergeGroupCells() {
      return mergeGroup;
   }

   int groupColCount = 0; // grouping columns
   boolean grand = false; // grand total
   Hashtable groupHeaderRows = new Hashtable();
   Hashtable groupHeaderCells = new Hashtable();
   Hashtable groupLevels = new Hashtable();
   Hashtable summaryRows = new Hashtable();
   Hashtable summaryCols = new Hashtable();
   Hashtable summaryLevels = new Hashtable();
   Hashtable baseRows = new Hashtable();
   boolean mergeGroup = false;
}

