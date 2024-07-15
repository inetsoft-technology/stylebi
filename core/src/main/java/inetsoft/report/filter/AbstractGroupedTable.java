/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.filter;

import inetsoft.report.TableDataPath;
import inetsoft.report.TableFilter;
import inetsoft.report.internal.Util;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.viewsheet.internal.DateComparisonUtil;

import java.awt.*;
import java.util.*;

/**
 * An abstract grouped table contains common methods of grouped table.
 *
 * @version 6.0
 * @author InetSoft Technology Corp
 */
public abstract class AbstractGroupedTable extends AbstractTableLens
   implements TableFilter, GroupedTable
{
   /**
    * Return the number of grouping columns. Multiple column group is
    * counted as one group column.
    */
   @Override
   public abstract int getGroupColCount();

   /**
    * Check if a row is displaying group header.
    * @param r row number.
    */
   @Override
   public abstract boolean isGroupHeaderRow(int r);

   /**
    * Check if a cell is a group header cell. This is more accurate than
    * the isGroupHeaderRow() because it takes into consideration of the
    * in-place header rows (where it's partially a header and body).
    * @param r row number.
    * @param c column number.
    * @return true if the cell is a group header cell.
    */
   @Override
   public abstract boolean isGroupHeaderCell(int r, int c);

   /**
    * Get the number of columns used in the specified grouping level.
    * This is normally 1 unless multiple columns are combined into
    * one group.
    */
   @Override
   public abstract int getGroupColumns(int level);

   /**
    * Get the grouping level of this group header. The row number must
    * refer to a header row.
    * @return grouping level. The top-most group is level 0. Returns -1
    * if the row is not a header row.
    */
   @Override
   public abstract int getGroupLevel(int r);

   /**
    * Check if the group column contents are shown. This is true
    * by default. If it's false, the group columns are hidden.
    * @return true if group columns are shown.
    */
   @Override
   public abstract boolean isShowGroupColumns();

   /**
    * Set the show group column contents option. If it's turned off,
    * the grouped columns will have
    * empty contents. If the ShowGroupColumns is set to false,
    * the AddGroupHeader is automatically turned on.
    * @param grp show group column contents.
    */
   @Override
   public abstract void setShowGroupColumns(boolean grp);

   /**
    * Check if group header is to be added to the grouped data.
    * @return true if group header is added. Default to false.
    */
   @Override
   public abstract boolean isAddGroupHeader();

   /**
    * Set whether group headers are added to the table. Group headers
    * are separate rows containing only the group column value for the
    * section.
    */
   @Override
   public abstract void setAddGroupHeader(boolean h);

   /**
    * Get the group header style.
    * @return group header style.
    */
   @Override
   public abstract int getGroupHeaderStyle();

   /**
    * Set the group header style. This must be called before the refresh()
    * is called.
    * @param headerS one of GROUP_HEADER_IN_PLACE, GROUP_HEADER_ROWS (default).
    */
   @Override
   public abstract void setGroupHeaderStyle(int headerS);

   /**
    * Check if a row is a summary row.
    * @param row the row number.
    * @return true if the row is a summary row.
    */
   @Override
   public abstract boolean isSummaryRow(int row);

   /**
    * Check if a column is a summary column.
    * @param col the column number.
    * @return true if the column is a summary column.
    */
   @Override
   public abstract boolean isSummaryCol(int col);

   /**
    * Get the grouping level of a summary row. The row number must
    * refer to a summary row.
    * @return grouping level. The top-most group is level 0. Returns -1
    * if the row is not a summary row.
    */
   @Override
   public abstract int getSummaryLevel(int r);

   /**
    * Check if this table contains grand summary row.
    * @return true if grand summary row exists.
    */
   @Override
   public abstract boolean hasGrandSummary();

   /**
    * Get the base table row index corresponding to the grouped table.
    * If the row does not exist in the base table, it returns -1.
    * @param row row index in the grouped table.
    * @return corresponding row index in the base table.
    */
   @Override
   public abstract int getBaseRowIndex(int row);

   /**
    * Get the group formula of the column.
    * @param col column index in the grouped table.
    * @return the group formula of column
    */
   @Override
   public abstract Formula getGroupFormula(int col);

   /**
    * Get the grand total formula of the column.
    * @param col column index in the grouped table.
    * @return the grand total formula of column
    */
   @Override
   public abstract Formula getGrandFormula(int col);

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

   /**
    * Get real group column count of a grouped table.
    *
    * @return real group column count of the grouped table
    */
   public int getRealGroupColCount() {
      if(count == -1) {
         int count = 0;

         for(int i = 0; i < getGroupColCount(); i++) {
            count += getGroupColumns(i);
         }

         this.count = count;
      }

      return count;
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
      moreRows(Integer.MAX_VALUE);

      int min = getHeaderRowCount();
      int max = hasGrandSummary() ? (getRowCount() - 2) : (getRowCount() - 1);

      if(row < min || row > max || level < 0) {
         return -1;
      }

      int groupLevel = getGroupLevel(row);
      int summaryLevel = getSummaryLevel(row);

      if(groupLevel != -1) {
         if(!isGroupHeaderRow(row)) {
            if(level >= getGroupColCount()) {
               return -1;
            }
         }
         else {
            if(level > groupLevel) {
               return -1;
            }
         }
      }
      else if(summaryLevel != -1) {
         if(level > summaryLevel) {
            return -1;
         }
      }
      else {
         if(level >= getGroupColCount()) {
            return -1;
         }
      }

      return getGroupFirstRow0(row, level);
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
      moreRows(Integer.MAX_VALUE);

      int min = getHeaderRowCount();
      int max = hasGrandSummary() ? (getRowCount() - 2) : (getRowCount() - 1);

      if(row < min || row > max || level < 0) {
         return -1;
      }

      int groupLevel = getGroupLevel(row);
      int summaryLevel = getSummaryLevel(row);

      if(groupLevel != -1) {
         if(!isGroupHeaderRow(row)) {
            if(level >= getGroupColCount()) {
               return -1;
            }
         }
      }
      else if(summaryLevel != -1) {
         if(level > summaryLevel) {
            return -1;
         }
      }
      else {
         if(level >= getGroupColCount()) {
            return -1;
         }
      }

      return getGroupLastRow0(row, level);
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
      moreRows(Integer.MAX_VALUE);

      int min = getHeaderRowCount();
      int max = hasGrandSummary() ? (getRowCount() - 2) : (getRowCount() - 1);

      if(row < min || row > max) {
         return -1;
      }

      int level = getGroupLevel(row);

      if(level < 0) {
         level = getSummaryLevel(row);
      }

      if(level < 0) {
         level = getGroupColCount() - 1;
      }

      return getGroupFirstRow0(row, level);
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
      moreRows(Integer.MAX_VALUE);

      int min = getHeaderRowCount();
      int max = hasGrandSummary() ? (getRowCount() - 2) : (getRowCount() - 1);

      if(row < min || row > max) {
         return -1;
      }

      int level = getGroupLevel(row);

      if(level < 0) {
         level = getSummaryLevel(row);
      }

      if(level < 0) {
         level = getGroupColCount() - 1;
      }

      return getGroupLastRow0(row, level);
   }

   /**
    * Get available group levels of a row when get group first/last row.
    *
    * @param row the specified row
    * @return the available group level array
    */
   @Override
   public int[] getAvailableLevels(int row) {
      moreRows(Integer.MAX_VALUE);

      int min = getHeaderRowCount();
      int max = hasGrandSummary() ? (getRowCount() - 2) : (getRowCount() - 1);

      if(row < min || row > max) {
         return new int[0];
      }

      int groupLevel = getGroupLevel(row);
      int summaryLevel = getSummaryLevel(row);

      if(groupLevel != -1) {
         if(!isGroupHeaderRow(row)) {
            int[] arr = new int[getGroupColCount()];

            for(int i = 0; i < arr.length; i++) {
               arr[i] = i;
            }

            return arr;
         }
         else {
            int[] arr = new int[groupLevel + 1];

            for(int i = 0; i < arr.length; i++) {
               arr[i] = i;
            }

            return arr;
         }
      }
      else if(summaryLevel != -1) {
         int[] arr = new int[summaryLevel + 1];

         for(int i = 0; i < arr.length; i++) {
            arr[i] = i;
         }

         return arr;
      }
      else {
         int[] arr = new int[getGroupColCount()];

         for(int i = 0; i < arr.length; i++) {
            arr[i] = i;
         }

         return arr;
      }
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

      return identifier == null ?
         getTable().getColumnIdentifier(col) :
         identifier;
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
      boolean supportMerge = c < getRealGroupColCount() + 1;
      boolean groupMerge = supportMerge &
         (isMergeGroupCells() || isMergedGroup(c));
      boolean summaryMerge = supportMerge & isMergeGroupCells();

      // @by larryl, merge group cells. Use a hashtable as a cache instead of
      // sparsematrix because we only cache at the cells that must have a span.
      // This is more efficient since it does not waste storage for empty spans
      if(groupMerge || summaryMerge) {
         int lvl = getGroupLevel0(r);

         // group cell, start span
         if(lvl >= 0 && (!isGroupHeaderRow(r) || getGroupColLevel(c) == lvl)) {
            if(groupMerge) {
               Dimension span = (Dimension) spanmap.get(new Point(c, r));

               if(span != null) {
                  return span;
               }

               int clevel = getGroupColLevel(c);

               if(lvl >= 0 && lvl <= clevel) {
                  for(int i = r + 1; moreRows(i); i++) {
                     int slevel = getSummaryLevel(i);
                     boolean grand = hasGrandSummary() && !moreRows(i + 1);

                     if(slevel < 0 && !grand) {
                        int glevel = getGroupLevel(i);

                        if(glevel < 0) {
                           glevel = getGroupLevel0(i);
                        }

                        if(glevel >= 0 && glevel <= clevel) {
                           span = new Dimension(1, i - r);
                        }
                     }
                     // @by larryl, if we hit a summary row of the current group
                     // or the parent group (in case the current group is not
                     // summarized), we set the span
                     else if(slevel < clevel || grand) {
                        span = new Dimension(1, i - r);
                     }
                     else if(slevel == clevel) {
                        span = new Dimension(1, i - r + 1);
                     }

                     // if we reach the end of table before finishing the group,
                     // it means there is no summary row and we set the span to
                     // the current row
                     if(span == null && !moreRows(i + 1)) {
                        span = new Dimension(1, i - r + 1);
                     }

                     if(span != null && (span.width > 0 && span.height > 0)) {
                        spanmap.put(new Point(c, r), span);
                        return span;
                     }
                  }
               }
            }
         }
         // set span on summary label
         else if(summaryMerge) {
            lvl = getSummaryLevel(r);
            int slevel = 0;

            if(lvl >= 0) {
               for(int i = 0; i <= lvl; i++) {
                  slevel += getGroupColumns(i);
               }
            }
            else {
               slevel = lvl;
            }

            // take care of grand total row
            if(slevel < 0 && hasGrandSummary() && !moreRows(r + 1)) {
               slevel = 0;
            }

            if(slevel == c) {
               Dimension span = (Dimension) spanmap.get(new Point(c, r));

               if(span != null) {
                  return span;
               }

               span = new Dimension(firstSummaryColumn() - c, 1);

               if(span.width > 0 && span.height > 0) {
                  spanmap.put(new Point(c, r), span);
                  return span;
               }
            }
         }
      }

      return null;
   }

   /**
    * Internal method to get the first row at the specified row and group lvl.
    *
    * @param row the specified row
    * @param lvl the specified group lvl
    * @return the first row, -1 if the group first row is not found.
    */
   protected int getGroupFirstRow0(int row, int lvl) {
      int min = getHeaderRowCount();

      for(int i = row; i >= min; i--) {
         int glevel = getGroupLevel(i);

         if(glevel >= 0 && glevel <= lvl) {
            return i;
         }

         if(i > 0) {
            int plevel = getSummaryLevel(i - 1);

            if(plevel >= 0 && plevel <= lvl) {
               return i;
            }
         }
      }

      return min;
   }

   /**
    * Internal method to get the last row at the specified row and group lvl.
    *
    * @param row the specified row
    * @param lvl the specified group lvl
    * @return the last row, -1 if the group last row is not found.
    */
   protected int getGroupLastRow0(int row, int lvl) {
      int max = hasGrandSummary() ? (getRowCount() - 2) : (getRowCount() - 1);

      for(int i = row; i <= max; i++) {
         int summaryLevel = getSummaryLevel(i);
         int groupLevel = getGroupLevel(i);

         if(summaryLevel == lvl) {
            return i;
         }

         if(groupLevel >= 0 && groupLevel <= lvl && i > row) {
            return i - 1;
         }
      }

      return max;
   }

   /**
    * Get a group col's group lvl.
    *
    * @param col the group col
    * @return the group col's group lvl, <code>-1</code> means not a group col
    */
   @Override
   public int getGroupColLevel(int col) {
      return -1;
   }

   /**
    * Clear the cached data.
    */
   public void clearCache() {
      spanmap.clear();
      count = -1;
   }

   /**
    * Get the internal keep row lvl for the first row of a group.
    */
   public int getGroupLevel0(int r) {
      return -1;
   }

   /**
    * Return the first summary column number.
    */
   public int firstSummaryColumn() {
      return 0;
   }

   /**
    * Get the origial data path from the specified path.
    * @param path the path used to get original data path.
    * @param aggregated a indicator to check the path is a summary column.
    * @param special identical to get the original path by special case, such
    *  as remove the DateRangeRef's special prefix.
    * @return the original path.
    */
   protected TableDataPath getOriginalPath(TableDataPath path,
                                           BooleanObject aggregated,
                                           boolean special) {
      int type = path.getType();
      String dtype = path.getDataType();
      String header = path.getPath()[0];
      String header2 = DateComparisonUtil.isDCCalcDatePartRef(header) ? header :
         getHeader2(header, special);
      TableDataPath opath = path;
      aggregated.setValue(false);

      // special case, and header2 is same as header, not need to find again
      if(special && (header2 == null || header2.equals(header))) {
         return null;
      }

      if(columnIndexMap == null) {
         columnIndexMap = new ColumnIndexMap(this, true);
      }

      if(type == TableDataPath.SUMMARY) {
         int col = Util.findColumn(columnIndexMap, header, false);
         opath = new TableDataPath(-1, TableDataPath.DETAIL, dtype, new String[] {header2});

         if(col < 0) {
            return null;
         }

         aggregated.setValue(isSummaryCol(col));
      }
      else if(type == TableDataPath.GRAND_TOTAL) {
         int col = Util.findColumn(columnIndexMap, header, false);

         // only summary cols keep xmeta info
         if(col >= 0 && isSummaryCol(col)) {
            opath = new TableDataPath(-1, TableDataPath.DETAIL, dtype,
               new String[] {header2});
         }
         else {
            opath = null;
         }

         aggregated.setValue(true);
      }
      // detail cell also need check, otherwise if CrosstabFilter wraps a
      // SummaryFilter, in CrosstabFilter has already changed to detail
      else if(type == TableDataPath.DETAIL) {
         opath = new TableDataPath(-1, TableDataPath.DETAIL, dtype, new String[] {header2});
      }

      return opath;
   }

   /**
    * Get a special header string from the given header.
    * @param header the given header to get another header string.
    * @param special to special case of the header.
    * @return another header string.
    */
   protected String getHeader2(String header, boolean special) {
      if(!special || header == null) {
         return header;
      }

      String header2 = header;
      int start = header2.indexOf("(");
      int end = header2.lastIndexOf(")");

      if(start >= 0 && end > start) {
         header2 = header2.substring(start + 1, end);

         int comma = header2.lastIndexOf(',');

         if(comma > 0) {
            header2 = header2.substring(0, comma);
         }
      }

      return header2;
   }

   /**
    * A class wrap a boolean object.
    */
   protected class BooleanObject {
      public void setValue(boolean b) {
         this.b = b;
      }

      public boolean getValue() {
         return b;
      }

      private boolean b = false;
   }

   /**
    * Check the merge group cell for given index group column.
    */
   public boolean isMergedGroup(int index) {
      return merge == null || index < 0 || index >= merge.length ?
         false : merge[index];
   }

   private boolean mergeGroup = false; // merge group cells
   // merge group cells, handle on every group column, for bc conside,
   // not delete mergeGroup property, if merge is null, use mergeGroup like old
   private boolean[] merge = null;
   private transient Hashtable spanmap = new Hashtable();
   private transient int count = -1; // group count
   private transient ColumnIndexMap columnIndexMap = null;
}