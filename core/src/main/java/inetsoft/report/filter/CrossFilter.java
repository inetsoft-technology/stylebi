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

import inetsoft.graph.internal.GDefaults;
import inetsoft.report.*;
import inetsoft.report.internal.binding.CrosstabSortInfo;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;

import java.awt.*;
import java.sql.Time;
import java.util.List;
import java.util.*;

/**
 * Crosstab.
 *
 * @version 13.3
 * @author InetSoft Technology Corp
 */
public interface CrossFilter extends Crosstab, TableLens, TableFilter {
   /**
    * Row grand total header in table data path.
    */
   String ROW_GRAND_TOTAL_HEADER = "ROW_GRAND_TOTAL";

   /**
    * Col grand total header in table data path.
    */
   String COL_GRAND_TOTAL_HEADER = "COL_GRAND_TOTAL";

   /**
    * Unknown tuple type.
    */
   int UNKNOW = 0;

   /**
    * Row tuple type.
    */
   int ROW = 1;

   /**
    * Column tuple type.
    */
   int COL = 2;

   Date EPIC = null;

   /**
    * Others label.
    */
   String OTHERS = Catalog.getCatalog().getString("Others");

   /**
    * An empty string representing a null value.
    */
   String NULL = new String("");

   /**
    * Set option to set the row total area on top of the group.
    * By default the row total area is below the group.
    * @param top true to set the row total on the top.
    */
   void setRowTotalOnTop(boolean top);

   /**
    * Check if the row total is on top.
    */
   boolean isRowTotalOnTop();

   /**
    * Set option to set the col total area on first col of the group.
    * By default the col total area is on the last col of the group.
    * @param first true to set the col total on the first col.
    */
   void setColumnTotalOnFirst(boolean first);

   /**
    * Check if the col total is on first col.
    */
   boolean isColumnTotalOnFirst();

   /**
    * Get the row header count.
    */
   int getRowHeaderCount();

   /**
    * Get the column header count.
    */
   int getColHeaderCount();

   /**
    * Return the number one or more data columns
    */
   int getDataColCount();

   /**
    * Get row header name.
    */
   String getRowHeader(int idx);

   /**
    * Get col header name.
    */
   String getColHeader(int idx);

   /**
    * Get grand total label.
    */
   String getGrandTotalLabel();

   /**
    * Set the label string for total columns and rows.
    */
   void setTotalLabel(String label);

   /**
    * If this option is true, and there are multiple summary cells, they are
    * arranged side by side in the table. Otherwise they are arranged vertically.
    * Defaults to false.
    */
   void setSummarySideBySide(boolean horizontal);

   /**
    * Check if summary cells are put side by side.
    */
   boolean isSummarySideBySide();

   /**
    * @return the aggregate headers.
    */
   Object[] getHeaders();

   /**
    * Get the span contains all cells.
    * @hidden
    */
   Rectangle getVSSpan(int r, int c);

   /**
    * Check if a specifield row and column is total cell.
    * @hidden
    */
   boolean isTotalCell(int r, int c);

   /**
    * Check if a specifield row is a total row.
    */
   boolean isTotalRow(int r);

   /**
    * Check if a specifield row is a total row.
    */
   boolean isTotalCol(int c);

   /**
    * @param r row number.
    * @param c column number.
    * @return if the target cell is a corner cell.
    */
   boolean isCornerCell(int r, int c);

   /**
    * Set whether to add summary title row/column. If true, a title row is added
    * to the header rows for summary if summary side by side is on. Otherwise,
    * a title column is added to the header columns if summary side by side is
    * off.
    */
   void setShowSummaryHeaders(boolean sumTitle);

   /**
    * Check whether to add summary title row/column.
    */
   boolean isShowSummaryHeaders();

   /**
    * Set option to repeat row labels. By default the cells for the same group
    * is merged into a single cell. If the repeatRowLabels is set to true, the
    * cells are still displayed individually.
    * @param repeat true to repeat row label.
    */
   void setRepeatRowLabels(boolean repeat);

   /**
    * Check if the row label are repeated.
    */
   boolean isRepeatRowLabels();

   /**
    * Set option to set if suppress the row grand total area.
    * By default the row grand total area is displayed.
    * @param sup true to set suppress the row grand total area.
    */
   void setSuppressRowGrandTotal(boolean sup);

   /**
    * Check if the row grand total is suppressed.
    */
   boolean isSuppressRowGrandTotal();

   /**
    * Set option to set if suppress the col grand total area.
    * By default the col grand total area is displayed.
    * @param sup true to set suppress the col grand total area.
    */
   void setSuppressColumnGrandTotal(boolean sup);

   /**
    * Check if the col grand total is suppressed.
    */
   boolean isSuppressColumnGrandTotal();

   /**
    * Set option to set if suppress the row subtotal area.
    * By default the row subtotal area is displayed.
    * @param sup true to set suppress the row subtotal area.
    */
   void setSuppressRowSubtotal(boolean sup);

   /**
    * Check if the row subtotal is suppressed.
    */
   boolean isSuppressRowSubtotal();

   /**
    * Set option to set if suppress the col subtotal area.
    * By default the col subtotal area is displayed.
    * @param sup true to set suppress the col subtotal area.
    */
   void setSuppressColumnSubtotal(boolean sup);

   /**
    * Check if the col subtotal is suppressed.
    */
   boolean isSuppressColumnSubtotal();

   /**
    * Set option to set if suppress the row group total area.
    * By default the row group total area is displayed.
    * @param sup true to set suppress the row group total area.
    */
   void setSuppressRowGroupTotal(boolean sup, int i);

   /**
    * Check if the row group total is suppressed.
    */
   boolean isSuppressRowGroupTotal(int i);

   /**
    * Set option to set if suppress the col group total area.
    * By default the col group total area is displayed.
    * @param sup true to set suppress the col group total area.
    */
   void setSuppressColumnGroupTotal(boolean sup, int i);

   /**
    * Check if the col group total is suppressed.
    */
   boolean isSuppressColumnGroupTotal(int i);

   /**
    * Set percentage direction.
    * only two values: StyleConstants.PERCENTAGE_BY_COL,
    *                   StyleConstants.PERCENTAGE_BY_ROW.
    * the first one is default.
    */
   void setPercentageDirection(int percentageDir);

   /**
    * Return percentage direction.
    */
   int getPercentageDirection();

   /**
    * Set whether to ignore group total where the group contains a single value.
    * @hidden
    */
   void setIgnoreNullTotals(boolean flag);

   /**
    * Check whether to ignore group total where the group contains a single value.
    * @hidden
    */
   boolean isIgnoreNullTotals();

   /**
    * Test if the cell is a header cell.
    *
    * @param row the cell's row
    * @param col the cell's col
    * @return <code>true</code> if the cell is a header cell, <code>false</code>
    * otherwise
    */
   boolean isHeaderCell(int row, int col);

   /**
    * Test if the cell is a data cell.
    *
    * @param row the cell's row
    * @param col the cell's col
    * @return <code>true</code> if the cell is a data cell, <code>false</code>
    * otherwise
    */
   boolean isDataCell(int row, int col);

   /**
    * Test if the cell is a grandtotal cell.
    *
    * @param row the cell's row
    * @param col the cell's col
    * @return <code>true</code> if the cell is a grandtotal cell,
    * <code>false</code> otherwise
    */
   boolean isGrandTotalCell(int row, int col);

   /**
    * Get available fields of a crosstab cell.
    *
    * @param row the specified row
    * @param col the specified col
    */
   String[] getAvailableFields(int row, int col);

   /**
    * Create key value paires for hyperlink and condition to use.
    *
    * @param row the specified row
    * @param col the specified col
    * @param map the specified map, null if should create a new one
    * @return a map stores key value pairs
    */
   Map<Object, Object> getKeyValuePairs(int row, int col, Map<Object, Object> map);

   /**
    * Set a sort info.
    */
   void setSortInfo(CrosstabSortInfo sinfo);

   /**
    * Make sure crossfilter is initialized.
    */
   void checkInit();

   /**
    * @return the column indexes array for aggregates.
    */
   int[] getDataIndexes();

   /**
    * @return the row header indexes array.
    */
   int[] getRowHeaderIndexes();

   /**
    * @return the column header indexes array.
    */
   int[] getColHeaderIndexes();

   /**
    * Get data header name.
    * @param idx the index of the aggregate column in base table.
    */
   String getDataHeader(int idx);

   /**
    * Get header at an index in base table.
    * @param index the specified index
    * @return header at the specified index in base table
    */
   String getHeader(int index, int didx);

   /**
    * @param row the specific row of the crossfilter.
    * @return  the target row tuple of the crossfilter.
    */
   Tuple getRowTuple(int row);

   /**
    * @param col the specific col of the crossfilter.
    * @return  the target col tuple of the crossfilter.
    */
   Tuple getColTuple(int col);

   /**
    * @return calc headers of the crossfilter.
    */
   String[] getCalcHeaders();

   /**
    * @return the header mapping.
    */
   Hashtable<Object, Object> getHeaderMaps();

   /**
    * Get the comparer used for comparison of row header values.
    */
   Comparer[] getRowComparers();

   /**
    * Get the comparer used for comparison of column header values.
    */
   Comparer[] getColComparers();

   /**
    * Get the date comparison merge part date group name.
    */
   String getDcMergePartRef();

   /**
    * Set the date comparison merge part date group name.
    */
   String setDcMergePartRef(String dim);

   /**
    * Column tuple comparer for
    */
   class TupleColComparer implements inetsoft.report.Comparer {
      public TupleColComparer(boolean colTotalOnFirst) {
         this.colTotalOnFirst = colTotalOnFirst;
      }

      @Override
      public int compare(java.lang.Object v1, java.lang.Object v2) {
         if(!(v1 instanceof Tuple && v2 instanceof Tuple)) {
            return -1;
         }

         return ((Tuple) v1).colCompare(v2, colTotalOnFirst);
      }

      @Override
      public int compare(double v1, double v2) {
         if(v1 == Tool.NULL_DOUBLE || v2 == Tool.NULL_DOUBLE) {
            return v1 == v2 ? 0 : (v1 == Tool.NULL_DOUBLE ? -1 : 1);
         }

         double val = v1 - v2;

         if(val < NEGATIVE_DOUBLE_ERROR) {
            return -1;
         }
         else if(val > POSITIVE_DOUBLE_ERROR) {
            return 1;
         }
         else {
            return 0;
         }
      }

      @Override
      public int compare(float v1, float v2) {
         if(v1 == Tool.NULL_FLOAT || v2 == Tool.NULL_FLOAT) {
            return v1 == v2 ? 0 : (v1 == Tool.NULL_FLOAT ? -1 : 1);
         }

         float val = v1 - v2;

         if(val < NEGATIVE_FLOAT_ERROR) {
            return -1;
         }
         else if(val > POSITIVE_FLOAT_ERROR) {
            return 1;
         }
         else {
            return 0;
         }
      }

      @Override
      public int compare(long v1, long v2) {
         return Long.compare(v1, v2);
      }

      @Override
      public int compare(int v1, int v2) {
         return Integer.compare(v1, v2);
      }

      @Override
      public int compare(short v1, short v2) {
         return Short.compare(v1, v2);
      }

      private final boolean colTotalOnFirst;
   }

   /**
    * MergedTuple holds several tuples.
    */
   class MergedTuple extends Tuple {
      /**
       * Constructor, to create an empty tuple.
       */
      public MergedTuple(Object[] r, List<Tuple> tuples) {
         super(r);
         this.tuples = tuples;
      }

      List<Tuple> tuples;
   }

   /**
    * Tuple holds a sequence of values.
    */
   class Tuple implements java.io.Serializable {
      /**
       * Constructor, to create an empty tuple.
       */
      public Tuple() {
         row = new Object[] {};
         initHashCode();
      }

      /**
       * Constructor, to create an empty tuple.
       */
      public Tuple(Tuple t,  int lens) {
         this(t.getRow(), lens);
      }

      /**
       * Constructor, to create a tuple from an array.
       */
      public Tuple(Object[] r) {
         this(r, r.length);
         initHashCode();
      }

      /**
       * Constructor, to create a tuple from an array with a length.
       */
      public Tuple(Object[] tableRow, int ncol) {
         row = new Object[ncol];
         System.arraycopy(tableRow, 0, row, 0, ncol);
         initHashCode();
      }

      /**
       * Constructor, to create a tuple from a table.
       * <p>
       * the column index stored in cols might be -1, which means fetched value
       * is always null.
       * <p>
       * In this case, we will calculate named group or date group. So, the
       * values might be changed.
       */
      public Tuple(CrossFilter filter, int rown, int[] cols) {
         this(filter, rown, cols, UNKNOW);
      }

      /**
       * Constructor, to create a tuple from a table.
       * <p>
       * the column index stored in cols might be -1, which means fetched value
       * is always null.
       * <p>
       * In this case, we will calculate named group or date group. So, the
       * values might be changed.
       */
      public Tuple(CrossFilter filter, int rown, int[] cols, int type) {
         TableLens table = filter.getTable();
         row = new Object[cols.length];
         boolean cubeFilter = filter instanceof CrossTabCubeFilter;

         for(int i = 0; i < row.length; i++) {
            if(cols[i] >= 0) {
               Object v = table.getObject(rown, cols[i]);

               if(cubeFilter && v instanceof String && ((String) v).startsWith("Date.")) {
                  String header = (String) table.getObject(0, cols[i]);
                  v = ((CrossTabCubeFilter) filter).getDisplayValue(v, header);
               }

               // NULL returned as "" in crosstab, and needs to be treated as null in graph.
               row[i] = (v == null) ? GDefaults.NULL_STR : v;
            }
         }

         switch(type) {
            case UNKNOW:
               if(arrayContains(cols, filter.getRowHeaderIndexes())) {
                  comparer = filter.getRowComparers();
               }
               else if(arrayContains(cols, filter.getColHeaderIndexes())) {
                  comparer = filter.getColComparers();
               }

               break;
            case ROW:
               comparer = filter.getRowComparers();
               break;
            case COL:
               comparer = filter.getColComparers();
               break;
         }

         initRow(table, rown);
         initHashCode();
      }

      public Tuple(Tuple tuple) {
         this.row = tuple.row.clone();
         this.hash = tuple.hash;
      }

      public void setComparer(Comparer[] comparer) {
         this.comparer = comparer;
      }

      /**
       * Get size of the tuple.
       */
      public final int size() {
         return (row == null) ? 0 : row.length;
      }

      /**
       * Copy the tuple to an array.
       * @param arr destination array.
       */
      public final void copyInto(Object[] arr) {
         copyInto(arr, row, row.length);
      }

      /**
       * Copy the tuple to an array.
       * @param arr destination array.
       */
      public final void copyInto(Object[] arr, int len) {
         copyInto(arr, row, len);
      }

      /**
       * Copy the tuple to an array.
       * @param arr destination array.
       */
      private void copyInto(Object[] arr, Object[] from, int len) {
         if(Math.min(from.length, len) >= 0) {
            System.arraycopy(from, 0, arr, 0, Math.min(from.length, len));
         }
      }

      /**
       * Copy the tuple to an column in a two dimensional array.
       * @param arr destination array.
       */
      public final void copyInto(Object[][] arr, int col) {
         for(int i = 0; i < row.length; i++) {
            arr[i][col] = row[i];
         }
      }

      /**
       * Check if the two tuples contain the same values.
       */
      public final boolean equals(Object val) {
         if(val == null || val.getClass() != getClass()) {
            return false;
         }

         Tuple tuple = (Tuple) val;

         // handles null here two
         if(row == tuple.row) {
            return true;
         }

         //         if(hash != tuple.hash) {
         //            return false;
         //         }

         // one of them is null and the other is not
         if(row == null || tuple.row == null) {
            return false;
         }

         // different length, can't be equal
         if(row.length != tuple.row.length) {
            return false;
         }

         // compare row values
         for(int i = 0; i < row.length; i++) {
            if(row[i] == tuple.row[i] ||
               row[i] != null && tuple.row[i] != null && row[i].equals(tuple.row[i]))
            {
               continue;
            }

            return false;
         }

         return true;
      }

      /**
       * Compare as a row.
       * @param b true to compare in ascending order.
       */
      public final int rowCompare(Object val, boolean b) {
         lt = (byte) (b ? -1 : 1);
         gt = (byte) -lt;

         return compareTo(val);
      }

      /**
       * Compare as a col.
       * @param b true to compare in ascending order.
       */
      public final int colCompare(Object val, boolean b) {
         lt = (byte) (b ? -1 : 1);
         gt = (byte) (b ? 1 : -1);

         return compareTo(val);
      }

      /**
       * Compare with another tuple.
       */
      public final int compareTo(Object val) {
         if(!(val instanceof Tuple)) {
            return -1;
         }

         Tuple tuple = (Tuple) val;
         int l1 = (row == null) ? 0 : row.length;
         int l2 = (tuple.row == null) ? 0 : tuple.row.length;
         int len = Math.max(l1, l2);

         if(len == 0) {
            return 0;
         }

         // @by larryl, if two tuples have different length, the one that is
         // completely empty (grand total) should be less than the one that
         // contains a null value (non-total tuple). This allows the sorting
         // to move the total to the left or top according to the ordering
         if(l1 == 0) {
            return lt;
         }
         else if(l2 == 0) {
            return gt;
         }

         Object[] arr1 = new Object[len];
         Object[] arr2 = new Object[len];

         System.arraycopy(row, 0, arr1, 0, row.length);
         System.arraycopy(tuple.row, 0, arr2, 0, tuple.row.length);

         for(int i = 0; i < len; i++) {
            if(arr1[i] != null && arr2[i] != null) {
               Object val1 = arr1[i];
               Object val2 = arr2[i];

               if(!val1.equals(val2)) {
                  if(val1 instanceof DCMergeDatesCell) {
                     val1 = ((DCMergeDatesCell) val1).getOriginalData();
                  }

                  if(val2 instanceof DCMergeDatesCell) {
                     val2 = ((DCMergeDatesCell) val2).getOriginalData();
                  }

                  Comparer comp = comparer[i % comparer.length];
                  boolean sortByValue = false;

                  if(comp instanceof SortOrder) {
                     SortOrder sorder = (SortOrder) comp;

                     if(sorder.isSpecific()) {
                        if(sorder.isOriginal()) {
                           int idx1 = sorder.getGroupNameIndex(val1);
                           int idx2 = sorder.getGroupNameIndex(val2);
                           return idx1 - idx2;
                        }
                        else  {
                           // others is always the last
                           if(OTHERS.equals(val1) || OTHERS.equals(val2)) {
                              if(Tool.equals(val1, val2)) {
                                 return 0;
                              }
                              else if(OTHERS.equals(val1)) {
                                 return 1;
                              }
                              else {
                                 return -1;
                              }
                           }

                           boolean c1 = sorder.containsGroup(val1.toString());
                           boolean c2 = sorder.containsGroup(val2.toString());

                           // sort two named groups
                           if(c1 && c2) {
                              return comp.compare(val1, val2);
                           }
                           // sort two other groups
                           else if(!c1 && !c2) {
                              return comp.compare(val1, val2);
                           }
                           // named groups are always ahead of the other groups
                           else {
                              return c1 ? -1 : 1;
                           }
                        }
                     }
                     else if(sorder.getOrder() == SortOrder.SORT_VALUE_ASC ||
                        sorder.getOrder() == SortOrder.SORT_VALUE_DESC)
                     {
                        sortByValue = true;
                     }
                  }

                  if(sortByValue) {
                     return defComparer.compare(val1, val2);
                  }

                  return comp.compare(val1, val2);
               }
            }
            else if(arr1[i] == arr2[i]) {
               return 0;
            }
            else if(arr1[i] != null) {
               return gt;
            }
            else {
               return lt;
            }
         }

         return 0;
      }

      /**
       * Test if an header index array is contained in row/col header index
       * array.
       */
      private boolean arrayContains(int[] sarr, int[] arr) {
         if(sarr.length > arr.length) {
            return false;
         }

         for(int i = 0; i < sarr.length; i++) {
            // @by billh, in sub-total tuple, the value in array might be -1,
            // for -1, we just ignore it
            if(sarr[i] != -1 && sarr[i] != arr[i]) {
               return false;
            }
         }

         return true;
      }

      /**
       * Get the hash code of the tuple.
       */
      public final int hashCode() {
         return hash;
      }

      /**
       * Init the tuple row values gotten from a table.
       * <p>
       * When named group or date group is specified in an index, the value
       * will transfer to named group value or data group value.
       *
       * @param table the base table
       * @param rown the row number of the base table
       */
      private void initRow(TableLens table, int rown) {
         Object[] arr = null;

         for(int i = 0; i < row.length; i++) {
            // @by billh, null object means index is -1
            if(row[i] == null) {
               continue;
            }

            Comparer comp = comparer[i % comparer.length];

            if(comp instanceof SortOrder) {
               SortOrder order = (SortOrder) comp;

               // process named group
               if(order.isSpecific()) {
                  if(arr == null) {
                     arr = new Object[table.getColCount()];

                     for(int j = 0; j < arr.length; j++) {
                        arr[j] = table.getObject(rown, j);
                     }
                  }

                  int g1 = order.findGroup(arr);

                  if(g1 >= 0) {
                     row[i] = order.getGroupName0(g1, false);
                  }
                  else if(order.getOthers() == SortOrder.GROUP_OTHERS) {
                     row[i] = OTHERS;
                  }
                  else if(row[i] instanceof Date) {
                     //reset Date Compare flag to ensure the group date can update.
                     order.resetDateCompare();
                     // force group date to be set
                     comp.compare(EPIC, row[i]);
                     row[i] = order.getGroupDate();
                  }
               }
               // process date group
               else if(row[i] instanceof Time && XSchema.STRING.equals(order.getDataType())) {
                  continue;
               }
               else if(row[i] instanceof Date) {
                  // force group date to be set
                  ((SortOrder) comp).compare(EPIC, (Date) row[i]);
                  row[i] = order.getGroupDate();
               }
            }
         }
      }

      /**
       * Init hash code.
       */
      private void initHashCode() {
         hash = 0;

         for(Object o : row) {
            if(o != null) {
               hash += o.hashCode();
            }
         }
      }

      /**
       * Get the row object.
       */
      public Object[] getRow() {
         if(row == null) {
            return new Object[0];
         }

         return row;
      }

      /**
       * To string.
       */
      public final String toString() {
         StringBuilder str = new StringBuilder("{Tuple: ");

         for(int i = 0; i < row.length; i++) {
            if(i > 0) {
               str.append("; ");
            }

            str.append(row[i]);
         }

         return str.append('}').toString();
      }

      private final Object[] row;
      private int hash = 0;
      private byte lt = -1;
      private byte gt = 1;
      private Comparer[] comparer = {defComparer};
   }

   /**
    * Tuple row comparer for crossfilter.
    */
   class TupleRowComparer implements inetsoft.report.Comparer {
       TupleRowComparer(boolean isRowTotalOnTop) {
         this.rowTotalOnTop = isRowTotalOnTop;
      }

      @Override
      public int compare(java.lang.Object v1, java.lang.Object v2) {
         if(!(v1 instanceof Tuple && v2 instanceof Tuple)) {
            return -1;
         }

         return ((Tuple) v1).rowCompare(v2, rowTotalOnTop);
      }

      @Override
      public int compare(double v1, double v2) {
         if(v1 == Tool.NULL_DOUBLE || v2 == Tool.NULL_DOUBLE) {
            return v1 == v2 ? 0 : (v1 == Tool.NULL_DOUBLE ? -1 : 1);
         }

         double val = v1 - v2;

         if(val < NEGATIVE_DOUBLE_ERROR) {
            return -1;
         }
         else if(val > POSITIVE_DOUBLE_ERROR) {
            return 1;
         }
         else {
            return 0;
         }
      }

      @Override
      public int compare(float v1, float v2) {
         if(v1 == Tool.NULL_FLOAT || v2 == Tool.NULL_FLOAT) {
            return v1 == v2 ? 0 : (v1 == Tool.NULL_FLOAT ? -1 : 1);
         }

         float val = v1 - v2;

         if(val < NEGATIVE_FLOAT_ERROR) {
            return -1;
         }
         else if(val > POSITIVE_FLOAT_ERROR) {
            return 1;
         }
         else {
            return 0;
         }
      }

      @Override
      public int compare(long v1, long v2) {
         return Long.compare(v1, v2);
      }

      @Override
      public int compare(int v1, int v2) {
         return Integer.compare(v1, v2);
      }

      @Override
      public int compare(short v1, short v2) {
         return Short.compare(v1, v2);
      }

      private final boolean rowTotalOnTop;
   }

   DefaultComparer defComparer = new DefaultComparer(); // comparers used to compare tuples
}
