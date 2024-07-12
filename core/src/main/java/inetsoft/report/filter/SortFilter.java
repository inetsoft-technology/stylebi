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
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.*;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.util.Collator_CN;
import inetsoft.util.CoreTool;
import inetsoft.util.algo.*;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.profile.ProfileUtils;
import inetsoft.util.script.ExpressionFailedException;
import inetsoft.util.swap.XSwappableIntList;
import inetsoft.util.swap.XSwapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This filter sorts a table on the specified columns. Comparison algorithms
 * can be specified by setting the comparer for each column. The default
 * comparison uses the DefaultComparer.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class SortFilter extends AbstractTableLens
   implements TableFilter, SortedTable, Cloneable, CachedTableLens, DataTableLens,
              CancellableTableLens
{
   /**
    * Create a sorted table and sort on all columns.
    * @param table base table.
    */
   public SortFilter(TableLens table) {
      this(table, new int[0], true);

      cols = new int[table.getColCount()];

      for(int i = 0; i < cols.length; i++) {
         cols[i] = i;
      }

      sorted = new boolean[cols.length];
   }

   /**
    * Create a sorted table.
    * @param table base table.
    * @param cols sort columns.
    */
   public SortFilter(TableLens table, int[] cols) {
      this(table, cols, true);
   }

   /**
    * Create a sorted table.
    * @param table base table.
    * @param cols sort columns.
    * @param asc true to sort in ascending order. False to sorted in
    * descending order.
    */
   public SortFilter(TableLens table, int[] cols, boolean asc) {
      this(table, cols, new boolean[] {asc});
   }

   /**
    * Create a sorted table.
    * @param table base table.
    * @param cols sort columns.
    * @param asc true to sort in ascending order. False to sorted in
    * descending order.
    */
   public SortFilter(TableLens table, int[] cols, boolean[] asc) {
      this.cols = cols;
      this.asc = asc;

      if(asc == null || asc.length == 0) {
         this.asc = new boolean[] { true };
      }

      setTable(table);

      comparers = new Comparer[table.getColCount()];
      sorted = new boolean[cols.length];
   }

   /**
    * Sort and remove duplicate rows.
    */
   public void setDistinct(boolean distinct) {
      this.distinct = distinct;
   }

   /**
    * Check if removing duplicate rows.
    */
   public boolean isDistinct() {
      return distinct;
   }

   /**
    * Clear all cached data.
    */
   @Override
   public void clearCache() {
      // do nothing
   }

   /**
    * Set the original sort column.
    * @param nosort array of original sort setting.
    */
   public void setOriginalSortColumn(boolean[] nosort) {
      for(int i = 0; i < Math.min(sorted.length, nosort.length); i++) {
         sorted[i] = nosort[i];
      }
   }

   /**
    * Set the comparer for a sorting column.
    * @param col table column index.
    * @param comp comparer.
    */
   @Override
   public void setComparer(int col, Comparer comp) {
      if(comp instanceof SortOrder) {
         // by billh, ascending or descending option is managed in SortFilter's
         // compare logic, so here comparer should compare objects ascendingly
         SortOrder order = (SortOrder) comp;
         order = (SortOrder) order.clone();
         order.setAsc(true);
         comp = order;
      }

      comparers[col] = comp;
   }

   /**
    * Get the comparer for the specified column. Null if no comparer
    * is defined.
    * @param col column number.
    * @return comparer or null.
    */
   @Override
   public Comparer getComparer(int col) {
      return comparers[col];
   }

   /**
    * Invalidate the table filter forcely, and the table filter will perform
    * filtering calculation to validate itself.
    */
   @Override
   public void invalidate() {
      synchronized(lock) {
         if(rowmap != null) {
            rowmap.dispose();
            rowmap = null;
         }
      }

      fireChangeEvent();
   }

   /**
    * This method is called before a report is generated to allow a filter
    * to refresh cached values.
    */
   private void process() {
      try {
         // for Feature #26586, add post processing time record for current report/vs.

         ProfileUtils.addExecutionBreakDownRecord(getReportName(),
            ExecutionBreakDownRecord.POST_PROCESSING_CYCLE, args -> {
               sort(asc);
            });

         //sort(asc);
      }
      catch(ExpressionFailedException scriptException) {
         LOG.warn("Failed to process sort filter: {}", scriptException.getMessage());
         CoreTool.addUserMessage(scriptException.getMessage());
      }
      catch(Exception ex) {
         LOG.error("Failed to process sort filter", ex);
      }
   }

   /**
    * Get the columns that the table is sorted on.
    * @return sort columns.
    */
   @Override
   public int[] getSortCols() {
      return cols;
   }

   /**
    * Get the sorting order of the sorting columns.
    */
   @Override
   public boolean[] getOrders() {
      return asc;
   }

   /**
    * Perform the sorting on the table.
    * @param asc true if sorting in ascending order.
    */
   private void sort(boolean[] asc) {
      if(cancelled) {
         return;
      }

      table.moreRows(Integer.MAX_VALUE);
      int len = table.getRowCount();

      if(cancelled || len < 0) {
         return;
      }

      boolean isSnapShot = isBaseBySnapshot(table);
      Comparer comp = new DefaultComparer();
      // @by larryl, 1/19/04, use collator to sort string to be locale sensitive
      Comparer strcomp = Locale.getDefault().getLanguage().equals("en") ? comp
         : new TextComparer(Collator_CN.getCollator(), isSnapShot);

      for(int i = 0; i < cols.length && cols[i] < comparers.length; i++) {
         if(comparers[cols[i]] == null) {
            if(table.getColType(cols[i]) == String.class) {
               comparers[cols[i]] = strcomp;
            }
            else {
               comparers[cols[i]] = comp;
            }
         }
      }

      if(cancelled) {
         return;
      }

      int ccount = cols.length;
      int[] rowmap = new int[len];

      for(int i = 0; i < rowmap.length; i++) {
         rowmap[i] = i;
      }

      cache = new ColumnList[cols.length];
      boolean useCache = len < MAX_CACHE;

      for(int i = 0; i < cols.length; i++) {
         boolean prim = table.isPrimitive(cols[i]);

         if(!prim) {
            if(useCache) {
               cache[i] = new CachedObjectColumnList(len, valueNormalizer);
            }
            else {
               cache[i] = new DefaultObjectColumnList(table, cols[i], valueNormalizer);
            }
            continue;
         }

         Class cls = table.getColType(cols[i]);

         if(Double.class.isAssignableFrom(cls)) {
            if(useCache) {
               cache[i] = new CachedDoubleColumnList(len);
            }
            else {
               cache[i] = new DefaultDoubleColumnList(table, cols[i]);
            }
         }
         else if(Float.class.isAssignableFrom(cls)) {
            if(useCache) {
               cache[i] = new CachedFloatColumnList(len);
            }
            else {
               cache[i] = new DefaultFloatColumnList(table, cols[i]);
            }
         }
         else if(Long.class.isAssignableFrom(cls)) {
            if(useCache) {
               cache[i] = new CachedLongColumnList(len);
            }
            else {
               cache[i] = new DefaultLongColumnList(table, cols[i]);
            }
         }
         else if(Integer.class.isAssignableFrom(cls)) {
            if(useCache) {
               cache[i] = new CachedIntColumnList(len);
            }
            else {
               cache[i] = new DefaultIntColumnList(table, cols[i]);
            }
         }
         else if(Short.class.isAssignableFrom(cls)) {
            if(useCache) {
               cache[i] = new CachedShortColumnList(len);
            }
            else {
               cache[i] = new DefaultShortColumnList(table, cols[i]);
            }
         }
         else {
            if(useCache) {
               cache[i] = new CachedObjectColumnList(len, valueNormalizer);
            }
            else {
               cache[i] = new DefaultObjectColumnList(table, cols[i], valueNormalizer);
            }
         }
      }

      if(useCache) {
         if(table instanceof CancellableTableLens && ((CancellableTableLens) table).isCancelled()) {
            return;
         }

         for(int i = 0; i < len; i++) {
            for(int j = 0; j < ccount; j++) {
               cache[j].prepare(i, cols[j], table);
            }
         }
      }

      if(comparers != null && comparers.length > 0) {
         for(int i = 0; i < comparers.length; i++) {
            if(comparers[i] == null || !(comparers[i] instanceof SortOrder)) {
               continue;
            }

            ((SortOrder) comparers[i]).setStarted(true);
         }
      }

      rowmap = mergeSort(rowmap, asc, hrow, rowmap.length - 1 - trow);
      cache = null;

      this.rowmap = new XSwappableIntList(rowmap);
      this.rowmap.complete();
      completed = true;
   }

   private boolean isBaseBySnapshot(TableLens table) {
      if(table == null) {
         return false;
      }

      if(table instanceof AbstractTableLens && ((AbstractTableLens) table).isSnapshot()) {
         return true;
      }

      if(table instanceof TableFilter) {
         return isBaseBySnapshot(((TableFilter) table).getTable());
      }

      return false;
   }

   /**
    * Compare two rows at the specified columns. Return 1, 0, or -1 for
    * greater than, equal to, and less than conditions.
    * @param r1 row one.
    * @param r2 row two.
    */
   private int compare0(int r1, int r2, boolean[] asc) {
      for(int i = 0; i < cols.length; i++) {
         if(sorted[i]) {
            continue;
         }

         Comparer comp = comparers[cols[i]];
         int rc = cache[i].compare(r1, r2, comp);

         if(comp instanceof SortOrder && ((SortOrder) comp).isSpecific()) {
            return rc;
         }

         if(rc != 0) {
            return asc[i % asc.length] ? rc : -rc;
         }
      }

      return 0;
   }

   /**
    * Sort the range of rows using merge sort.
    */
   private int[] mergeSort(int[] rowmap, final boolean[] asc, int lo0, int hi0) {
      if(rowmap.length > MAX_CACHE) {
         XSwapper.getSwapper().waitForMemory();
      }

      /*
      int[] aux = rowmap.clone();
      mergeSort(aux, rowmap, asc, lo0, hi0 + 1);
      */

      if(hi0 > lo0) {
         // use tim sort which works better with partially sorted arrays
         sortObj = distinct ? new IntArrayDistinctMergeSort() : new IntArrayTimSort();

         try {
            rowmap = sortObj.sort(rowmap, lo0, hi0 + 1, new IntArraySort.IntComparator() {
               public int compare(int v1, int v2) {
                  return compare0(v1, v2, asc);
               }
            });
         }
         catch(Exception ex) {
            // parsing number from string for numeric comparison may cause the comparison to
            // violate transitory property. if it fails, turn it off. (61642, group on Bethlehem)
            Arrays.stream(comparers).filter(c -> c instanceof DefaultComparer)
               .forEach(c -> ((DefaultComparer) c).setParseNumber(false));
            rowmap = sortObj.sort(rowmap, lo0, hi0 + 1, new IntArraySort.IntComparator() {
               public int compare(int v1, int v2) {
                  return compare0(v1, v2, asc);
               }
            });
         }
         sortObj = null;
      }

      return rowmap;
   }

   /**
    * Get the original table of this filter.
    */
   @Override
   public TableLens getTable() {
      return table;
   }

   /**
    * Set the base table of this filter.
    */
   @Override
   public void setTable(TableLens table) {
      this.table = table;
      this.hrow = table.getHeaderRowCount();
      this.trow = table.getTrailerRowCount();
      invalidate();
      this.table.addChangeListener(new DefaultTableChangeListener(this));
   }

   /**
    * Get the base table row index corresponding to the grouped table.
    * If the row does not exist in the base table, it returns -1.
    * @param row row index in the grouped table.
    * @return corresponding row index in the base table.
    */
   @Override
   public int getBaseRowIndex(int row) {
      if(row < hrow) {
         return row;
      }

      XSwappableIntList rowmap = this.rowmap;

      if(rowmap == null) {
         rowmap = checkInit();
      }

      return rowmap == null ? row : rowmap.get(row);
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
      if(row < hrow) {
         return true;
      }

      XSwappableIntList rowmap = this.rowmap;

      if(rowmap == null) {
         rowmap = checkInit();
      }

      return rowmap != null && row < rowmap.size();
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
      return hrow;
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
      return trow;
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
      return table.getRowHeight(row < hrow ? row : rowmap.get(row));
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
      return table.getColWidth(col);
   }

   /**
    * Get the current column content type.
    * @param col column number.
    * @return column type.
    */
   @Override
   public Class getColType(int col) {
      // @by mikec, call table's getColType so that the sort filter
      // will not return a wrong col type when there is null value
      // and be sorted to the first non-header row which will cause
      // the col type be set to String.
      return table.getColType(col);
   }

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getRowBorderColor(int r, int c) {
      return table.getRowBorderColor(r < hrow ? r : rowmap.get(r), c);
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      return table.getColBorderColor(r < hrow ? r : rowmap.get(r), c);
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
      return table.getRowBorder(r < hrow ? r : rowmap.get(r), c);
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
      return table.getColBorder(r < hrow ? r : rowmap.get(r), c);
   }

   /**
    * Return the cell gap space.
    * @param r row number.
    * @param c column number.
    * @return cell gap space.
    */
   @Override
   public Insets getInsets(int r, int c) {
      return table.getInsets(r < hrow ? r : rowmap.get(r), c);
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
      Dimension span = table.getSpan(r < hrow ? r : rowmap.get(r), c);

      if(span != null) {
         int rows = getRowCount();

         // @by davidd bug1366256113810
         // Ensure span height does not exceed available rows
         if(span.height + r > rows) {
            span.height = rows - r;
         }
      }

      return span;
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number.
    * @return cell alignment.
    */
   @Override
   public int getAlignment(int r, int c) {
      return table.getAlignment(r < hrow ? r : rowmap.get(r), c);
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      return table.getFont(r < hrow ? r : rowmap.get(r), c);
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
      return table.isLineWrap(r < hrow ? r : rowmap.get(r), c);
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
      return table.getForeground(r < hrow ? r : rowmap.get(r), c);
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
      return table.getBackground(r < hrow ? r : rowmap.get(r), c);
   }

   /**
    * Check if is primitive.
    * @return <tt>true</tt> if is primitive, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isPrimitive(int col) {
      return table.isPrimitive(col);
   }

   /**
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isNull(int r, int c) {
      return table.isNull(r < hrow ? r : rowmap.get(r), c);
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public final Object getObject(int r, int c) {
      return table.getObject(getBaseRowIndex(r), c);
   }

   /**
    * Get data.
    * @param r row index.
    * @param c column index.
    * @return data in the specified cell.
    */
   @Override
   public Object getData(int r, int c) {
      if(table instanceof DataTableLens) {
         return ((DataTableLens) table).getData(
            r < hrow ? r : rowmap.get(r), c);
      }

      return getObject(r, c);
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the double value in the specified row.
    */
   @Override
   public final double getDouble(int r, int c) {
      return table.getDouble(r < hrow ? r : rowmap.get(r), c);
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the float value in the specified row.
    */
   @Override
   public final float getFloat(int r, int c) {
      return table.getFloat(r < hrow ? r : rowmap.get(r), c);
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the long value in the specified row.
    */
   @Override
   public final long getLong(int r, int c) {
      return table.getLong(r < hrow ? r : rowmap.get(r), c);
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the int value in the specified row.
    */
   @Override
   public final int getInt(int r, int c) {
      return table.getInt(r < hrow ? r : rowmap.get(r), c);
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the short value in the specified row.
    */
   @Override
   public final short getShort(int r, int c) {
      return table.getShort(r < hrow ? r : rowmap.get(r), c);
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the byte value in the specified row.
    */
   @Override
   public final byte getByte(int r, int c) {
      return table.getByte(r < hrow ? r : rowmap.get(r), c);
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the boolean value in the specified row.
    */
   @Override
   public final boolean getBoolean(int r, int c) {
      return table.getBoolean(r < hrow ? r : rowmap.get(r), c);
   }

   /**
    * Set the cell value.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      table.setObject(r < hrow ? r : rowmap.get(r), c, v);
   }

   /**
    * Dispose the table to clear up temporary resources.
    */
   @Override
   public void dispose() {
      table.dispose();

      if(rowmap != null) {
         rowmap.dispose();
         rowmap = null;
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

      return identifier == null ? table.getColumnIdentifier(col) : identifier;
   }

   /**
    * Check init.
    */
   private XSwappableIntList checkInit() {
      synchronized(lock) {
         if(rowmap == null) {
            process();
         }

         return rowmap;
      }
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(descriptor == null) {
         descriptor = new MappedDataDescriptor(this, table.getDescriptor());
      }

      return descriptor;
   }

   /**
    * Cancel the table lens and running queries if supported.
    */
   @Override
   public void cancel() {
      cancelLock.lock();

      try {
         cancelled = !completed;

         if(table instanceof CancellableTableLens) {
            ((CancellableTableLens) table).cancel();
         }

         IntArraySort sortObj = this.sortObj;

         if(sortObj != null) {
            sortObj.cancel();
         }
      }
      finally {
         cancelLock.unlock();
      }
   }

   /**
    * Check the TableLens to see if it is cancelled.
    */
   @Override
   public boolean isCancelled() {
      return cancelled;
   }

   /**
    * Get the value normalizer.
    */
   public Function<Object, Object> getValueNormalizer() {
      return valueNormalizer;
   }

   /**
    * Set the value normalizer. The function will be called to convert values for comparison.
    */
   public void setValueNormalizer(Function<Object, Object> valueNormalizer) {
      this.valueNormalizer = valueNormalizer;
   }

   /**
    * Column list stores the values of a column.
    */
   private static class ColumnList {
      public void prepare(int r, int c, TableLens table) {
         this.table = table;
         this.col = c;
      }

      public int compare(int r1, int r2, Comparer comp) {
         System.err.println("c1:");
         return comp.compare(table.getObject(r1, col), table.getObject(r2, col));
      }

      protected TableLens table;
      protected int col;
   }

   /**
    * Object column list stores the values of a object column.
    */
   private static final class CachedObjectColumnList extends ColumnList {
      public CachedObjectColumnList(int len, Function valueNormalizer) {
         arr = new Object[len];
         this.valueNormalizer = valueNormalizer;
      }

      @Override
      public void prepare(int r, int c, TableLens table) {
         FormatTableLens2 formatTable = null;

         // optimization
         if(otable == table) {
            formatTable = this.formatTable;
         }
         else {
            formatTable = (FormatTableLens2) Util.getNestedTable(table, FormatTableLens2.class);
            otable = table;
            this.formatTable = formatTable;
         }

         if(formatTable != null) {
            arr[r] = formatTable.getTable().getObject(r, c);
         }
         else {
            arr[r] = table.getObject(r, c);
         }

         if(valueNormalizer != null) {
            arr[r] = valueNormalizer.apply(arr[r]);
         }
      }

      @Override
      public int compare(int r1, int r2, Comparer comp) {
         return comp.compare(arr[r1], arr[r2]);
      }

      private Object[] arr;
      private FormatTableLens2 formatTable;
      private TableLens otable;
      private Function valueNormalizer;
   }

   private static final class DefaultObjectColumnList extends ColumnList {
      public DefaultObjectColumnList(TableLens table, int col, Function valueNormalizer) {
         this.table = table;
         this.col = col;
         this.valueNormalizer = valueNormalizer;
      }

      public int compare(int r1, int r2, Comparer comp) {
         Object v1 = table.getObject(r1, col);
         Object v2 = table.getObject(r2, col);

         if(valueNormalizer != null) {
            v1 = valueNormalizer.apply(v1);
            v2 = valueNormalizer.apply(v2);
         }

         return comp.compare(v1, v2);
      }

      private Function valueNormalizer;
   }

   /**
    * Double list stores the values of a double column.
    */
   private static final class CachedDoubleColumnList extends ColumnList {
      public CachedDoubleColumnList(int len) {
         arr = new double[len];
      }

      @Override
      public void prepare(int r, int c, TableLens table) {
         arr[r] = table.getDouble(r, c);
      }

      @Override
      public int compare(int r1, int r2, Comparer comp) {
         return comp.compare(arr[r1], arr[r2]);
      }

      private double[] arr;
   }

   private static final class DefaultDoubleColumnList extends ColumnList {
      public DefaultDoubleColumnList(TableLens table, int col) {
         this.table = table;
         this.col = col;
      }

      @Override
      public int compare(int r1, int r2, Comparer comp) {
         return comp.compare(table.getDouble(r1, col), table.getDouble(r2, col));
      }
   }

   /**
    * Float column list stores the values of a float column.
    */
   private static final class CachedFloatColumnList extends ColumnList {
      public CachedFloatColumnList(int len) {
         arr = new float[len];
      }

      @Override
      public void prepare(int r, int c, TableLens table) {
         arr[r] = table.getFloat(r, c);
      }

      @Override
      public int compare(int r1, int r2, Comparer comp) {
         return comp.compare(arr[r1], arr[r2]);
      }

      private float[] arr;
   }

   private static final class DefaultFloatColumnList extends ColumnList {
      public DefaultFloatColumnList(TableLens table, int col) {
         this.table = table;
         this.col = col;
      }

      @Override
      public int compare(int r1, int r2, Comparer comp) {
         return comp.compare(table.getFloat(r1, col), table.getFloat(r2, col));
      }
   }

   /**
    * Long column list stores the values of a long column.
    */
   private static final class CachedLongColumnList extends ColumnList {
      public CachedLongColumnList(int len) {
         arr = new long[len];
      }

      @Override
      public void prepare(int r, int c, TableLens table) {
         arr[r] = table.getLong(r, c);
      }

      @Override
      public int compare(int r1, int r2, Comparer comp) {
         return comp.compare(arr[r1], arr[r2]);
      }

      private long[] arr;
   }

   private static final class DefaultLongColumnList extends ColumnList {
      public DefaultLongColumnList(TableLens table, int col) {
         this.table = table;
         this.col = col;
      }

      @Override
      public int compare(int r1, int r2, Comparer comp) {
         return comp.compare(table.getLong(r1, col), table.getLong(r2, col));
      }
   }

   /**
    * Int column list stores the values of an int column.
    */
   private static final class CachedIntColumnList extends ColumnList {
      public CachedIntColumnList(int len) {
         arr = new int[len];
      }

      @Override
      public void prepare(int r, int c, TableLens table) {
         arr[r] = table.getInt(r, c);
      }

      @Override
      public int compare(int r1, int r2, Comparer comp) {
         return comp.compare(arr[r1], arr[r2]);
      }

      private int[] arr;
   }

   private static final class DefaultIntColumnList extends ColumnList {
      public DefaultIntColumnList(TableLens table, int col) {
         this.table = table;
         this.col = col;
      }

      @Override
      public int compare(int r1, int r2, Comparer comp) {
         return comp.compare(table.getInt(r1, col), table.getInt(r2, col));
      }
   }

   /**
    * Short column list stores the values of a short column.
    */
   private static final class CachedShortColumnList extends ColumnList {
      public CachedShortColumnList(int len) {
         arr = new short[len];
         bits = new boolean[len];
      }

      @Override
      public void prepare(int r, int c, TableLens table) {
         arr[r] = table.getShort(r, c);
         bits[r] = table.isNull(r, c);
      }

      @Override
      public int compare(int r1, int r2, Comparer comp) {
         if(bits[r1] || bits[r2]) {
            if(bits[r1] == bits[r2]) {
               return 0;
            }

            return bits[r1] ? -1 : 1;
         }

         return comp.compare(arr[r1], arr[r2]);
      }

      private short[] arr;
      private boolean[] bits;
   }

   private static final class DefaultShortColumnList extends ColumnList {
      public DefaultShortColumnList(TableLens table, int col) {
         this.table = table;
         this.col = col;
      }

      @Override
      public int compare(int r1, int r2, Comparer comp) {
         return comp.compare(table.getShort(r1, col), table.getShort(r2, col));
      }
   }

   /**
    * @return the report/vs name which this filter was created for,
    * and will be used when insert audit record.
    */
   @Override
   public String getReportName() {
      String name = super.getReportName();
      return name != null ? name : table == null ? null : table.getReportName();
   }

   /**
    * @return the report type which this filter was created for:
    * ExecutionBreakDownRecord.OBJECT_TYPE_REPORT or
    * ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET
    */
   @Override
   public String getReportType() {
      String type = super.getReportType();
      return type != null ? type : table == null ? null : table.getReportType();
   }

   private static final int MAX_CACHE = 10000000;
   private TableLens table;
   private int hrow = 1; // header row count
   private int trow = 0; // header row count
   private int[] cols; // sorting columns
   private boolean[] asc = { true };
   private boolean[] sorted;
   private boolean distinct;
   private ColumnList[] cache; // cache of the sorting keys
   private final Object lock = new String("lock");
   private transient Comparer[] comparers;
   private transient XSwappableIntList rowmap;
   private transient IntArraySort sortObj;
   private boolean completed;       // completed flag
   private volatile boolean cancelled;       // cancelled flag
   private Function<Object, Object> valueNormalizer;
   private final Lock cancelLock = new ReentrantLock();
   private static final Logger LOG = LoggerFactory.getLogger(SortFilter.class);
}
