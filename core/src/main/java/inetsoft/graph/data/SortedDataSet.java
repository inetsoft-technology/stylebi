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
package inetsoft.graph.data;

import inetsoft.util.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * SortedDataSet sorts the base data set by comparing the values of a
 * measure or dimension.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class SortedDataSet extends AbstractDataSetFilter {
   /**
    * Create an instance of SortedDataSet.
    * @param base the specified base data set.
    */
   public SortedDataSet(DataSet base) {
      super(base);
      this.base = base;
   }

   /**
    * Create an instance of SortedDataSet.
    * @param base the specified base data set.
    * @param cols the sorting column.
    */
   public SortedDataSet(DataSet base, String... cols) {
      this(base);

      for(String col : cols) {
         addSortColumn(col, false);
      }
   }

   /**
    * Set whether to force all sort columns to be sorted regardless of whether the
    * base dataset has comparator for the column.
    */
   public void setForceSort(boolean force) {
      this.forceSort = force;
   }

   /**
    * Check whether to force all sort columns to be sorted regardless of whether the
    * base dataset has comparator for the column.
    */
   public boolean isForceSort() {
      return forceSort;
   }

   /**
    * Add a column to be sorted.
    * @param col the sorting column.
    * @param sortrow true to sort using the row comparison of DataSetComparator.
    */
   public void addSortColumn(String col, boolean sortrow) {
      int cidx = base.indexOfHeader(col);

      if(cidx < 0) {
         LOG.warn("Column not found: " + col);
         throw new MessageException(Catalog.getCatalog().getString(
            "common.invalidTableColumn", col));
      }

      String header = base.getHeader(cidx);
      Comparator comp = base.getComparator(header);

      // @by billh, fix customer bug bug1303420551227
      // if sorting order is original order, we should not create comparator
      /*
      if(comp == null) {
         comp = new DefaultComparator();
      }
      */

      compmap.put(header, comp);
      sortrows.put(header, sortrow);
      cols.add(cidx);
      initMapping();
   }

   /**
    * Get the sorting columns.
    */
   public String[] getSortColumns() {
      String[] arr = new String[cols.size()];

      for(int i = 0; i < cols.size(); i++) {
         arr[i] = base.getHeader(cols.get(i));
      }

      return arr;
   }

   /**
    * Create a default mapping.
    */
   private synchronized void initMapping() {
      // @by ChrisSpagnoli bug1422603731496 2015-2-18
      int projected = base instanceof AbstractDataSet
         ? ((AbstractDataSet) base).getRowCountUnprojected() : base.getRowCount();
      this.mapping = new int[projected];

      for(int i = 0; i < mapping.length; i++) {
         mapping[i] = i;
      }

      comps.clear();
      rows.clear();

      for(int col : cols) {
         String hdr = base.getHeader(col);
         Comparator comp = compmap.get(hdr);
         comps.add(comp);

         if(sortrows.get(hdr)) {
            rows.set(comps.size() - 1);
         }
      }
   }

   /**
    * This method must be called before the calculated columns can be used.
    */
   @Override
   public void prepareCalc(String dim, int[] rows, boolean calcMeasures) {
      super.prepareCalc(dim, rows, calcMeasures);
      sorted = false;
      initMapping();
   }

   /**
    * Set the comparator for sorting.
    */
   public void setComparator(String col, Comparator comp) {
      int cidx = base.indexOfHeader(col);

      if(cidx < 0) {
         LOG.warn("Column not found: " + col);
         throw new MessageException(Catalog.getCatalog().getString(
            "common.invalidTableColumn", col));
      }

      String header = base.getHeader(cidx);
      compmap.put(header, comp);
      initMapping();
   }

   /**
    * Get the comparator for sorting.
    */
   @Override
   public Comparator getComparator0(String col) {
      Comparator comp = compmap.get(col);

      if(comp == null) {
         comp = super.getComparator0(col);
      }

      return comp;
   }

   /**
    * Sort the dataset.
    */
   private void sort() {
      sorted = true;

      for(int col : cols) {
         if(col < 0) {
            return;
         }
      }

      for(int i = 0; i < comps.size(); i++) {
         Comparator comp = comps.get(i);

         if(forceSort && comp == null) {
            comp = new DefaultComparator();
         }

         if(comp instanceof DataSetComparator) {
            comp = ((DataSetComparator) comp).getComparator(startRow);
         }

         comp = DataSetComparator.getComparator(comp, this);
         comps.set(i, comp);
      }

      int[] mapping2 = mapping.clone();
      int endRow = (this.endRow < 0) ? mapping.length : this.endRow;
      startRow = Math.max(0, startRow);

      // if base dataset has calculated rows, and the sort dataset is only sorting a
      // sub range of rows (for multi-style chart), we should include the calculated
      // rows in the range (see GraphElement.getEndRow(DataSet)), otherwise the
      // calc rows will be missing on the sub-graph. (51046)
      if(base instanceof AbstractDataSet && this.endRow > 0) {
         int calcRows = ((AbstractDataSet) base).getCalcRowCount();

         if(calcRows > 0) {
            if(mapping2.length + calcRows > mapping2.length) {
               mapping2 = Arrays.copyOf(mapping2, mapping2.length + calcRows);
            }

            int endBase = mapping.length - calcRows;
            int n = 0;
            Set dimKeys = buildGroupKeys(base);
            tsColumn = base.getCalcRows().stream().filter(c -> c instanceof TimeSeriesRow)
               .map(t -> ((TimeSeriesRow) t).getTimeColumn()).findFirst().orElse(null);
            Map<Object, Set> tsVals = buildTimeSeries(base);

            // copy the calc row index into the sub-range
            for(int s = 0; s < calcRows; s++) {
               // for multi-style, only pick up rows for the vars used by this element. (52604)
               if(!isQualified(base, endBase + s, dimKeys, tsVals)) {
                  continue;
               }

               mapping2[endRow + n] = endBase + s;
               mapping2[endBase + n] = endRow + s;
               n++;
            }

            mapping = mapping2.clone();
            // extend endRow to include the calc rows
            endRow += n;
         }
      }

      mergeSort(mapping2, mapping, startRow, endRow);
      effectiveEndRow = endRow;
   }

   // build a set of all dim keys in the start/end range.
   private Set buildGroupKeys(DataSet base) {
      Set keys = new ObjectOpenHashSet();

      for(int i = startRow; i < endRow; i++) {
         keys.add(createGroupKey(base, i));
      }

      return keys;
   }

   // create a key that uniquely identifies dims in a row.
   private Object createGroupKey(DataSet base, int row) {
      return groupFields == null ? null : Arrays.stream(groupFields)
         .map(dim -> base.getData(dim, row))
         .collect(Collectors.toList());
   }

   // create a set of existing values in the time series.
   // groupKey -> timeSeriesValues
   private Map<Object, Set> buildTimeSeries(DataSet base) {
      Map<Object, Set> tsVals = new Object2ObjectOpenHashMap<>();

      if(tsColumn != null) {
         for(int i = startRow; i < endRow; i++) {
            Object key = createGroupKey(base, i);
            Set vals = tsVals.computeIfAbsent(key, k -> new ObjectOpenHashSet());
            vals.add(base.getData(tsColumn, i));
         }
      }

      return tsVals;
   }

   // check if a (calc) row is included in this data set.
   private boolean isQualified(DataSet base, int row, Set dimKeys, Map<Object, Set> tsVals) {
      boolean noNull = base.getCalcRows().stream().allMatch(c -> c.isNoNull());

      // for multi-style, only pick up rows for the vars used by this element. (52604)
      if(vars != null && vars.length > 0 && noNull) {
         for(String var : vars) {
            Object val = base.getData(var, row);

            if(val == null || ignoreZeroCalc &&
               val instanceof Number && ((Number) val).doubleValue() == 0)
            {
               return false;
            }
         }
      }

      if(groupFields != null || tsColumn != null) {
         Object groupKey = createGroupKey(base, row);

         // for time series in multi-style chart, it may be created for the 'other' element
         // so we check if it matches the groups in the current set. (52540)
         if(groupFields != null && !dimKeys.contains(groupKey)) {
            return false;
         }

         if(tsColumn != null) {
            Set ts = tsVals.get(groupKey);

            // don't pickup rows if it's mot missing in the current series. (52604)
            if(ts != null && ts.contains(base.getData(tsColumn, row))) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Merge sort the mapping.
    */
   private void mergeSort(int[] src, int[] dest, int low, int high) {
      int length = high - low;

      if(isDisposed()) {
         return;
      }

      if(length < 7) {
         for(int i = low; i < high; i++) {
            for(int j = i; j > low && compare(dest[j - 1], dest[j]) > 0; j--) {
               swap(dest, j, j - 1);
            }
         }

         return;
      }

      int mid = (low + high) >> 1;
      mergeSort(dest, src, low, mid);
      mergeSort(dest, src, mid, high);

      if(compare(src[mid - 1], src[mid]) <= 0) {
         System.arraycopy(src, low, dest, low, length);
         return;
      }

      for(int i = low, p = low, q = mid; i < high; i++) {
         if(q >= high || p < mid && compare(src[p], src[q]) <= 0) {
            dest[i] = src[p++];
         }
         else {
            dest[i] = src[q++];
         }
      }
   }

   /**
    * Compare two rows.
    */
   private int compare(int r1, int r2) {
      for(int i = 0; i < cols.size(); i++) {
         int col = cols.get(i);
         Comparator comp = comps.get(i);
         int rc = 0;

         if(rows.get(i) && comp instanceof DataSetComparator) {
            rc = ((DataSetComparator) comp).compare(base, r1, r2);
         }
         else if(comp != null) {
            Object obj1 = base.getData(col, r1);
            Object obj2 = base.getData(col, r2);
            rc = comp.compare(obj1, obj2);
         }

         if(rc != 0) {
            return rc;
         }
      }

      return 0;
   }

   /**
    * Swap x[a] with x[b].
    */
   private void swap(int[] x, int a, int b) {
      int t = x[a];
      x[a] = x[b];
      x[b] = t;
   }

   /**
    * Check whether to ignore zero calc values.
    */
   public boolean isIgnoreZeroCalc() {
      return ignoreZeroCalc;
   }

   /**
    * Set whether to ignore zero calc values.
    */
   public void setIgnoreZeroCalc(boolean ignoreZeroCalc) {
      this.ignoreZeroCalc = ignoreZeroCalc;
   }

   /**
    * Get the base row index on its base data set of the specified row.
    * @param r the specified row index.
    * @return the base row index on base data set, -1 if no base row.
    */
   @Override
   public int getBaseRow(int r) {
      // initialize comparator outside of synchronized block to avoid deadlock. (52741)
      if(!sorted) {
         comps.stream().filter(comp -> comp != null).forEach(comp -> {
            try {
               comp.compare(null, null);
            }
            catch(Throwable ex) {
               // ignore
            }
         });
      }

      synchronized(this) {
         if(!sorted) {
            sort();
         }

         // if project forward is set, will result in getRowCount() > mapping.length. (49322)
         return r < mapping.length ? mapping[r] : r;
      }
   }

   /**
    * Return the number of rows in the chart lens.
    */
   @Override
   public synchronized int getRowCount0() {
      return mapping.length;
   }

   /**
    * Set the starting row to sort. The default is 0.
    */
   public void setStartRow(int start) {
      this.startRow = start;
   }

   /**
    * Get the starting row to sort.
    */
   public int getStartRow() {
      return startRow;
   }

   /**
    * Set the ending row to sort.
    * @param end the ending row (non-inclusive). Use -1 to use all rows.
    */
   public void setEndRow(int end) {
      this.endRow = end;
   }

   /**
    * Get the ending row to sort.
    */
   public int getEndRow() {
      return endRow;
   }

   /**
    * Get the end row index including calc rows.
    */
   public int getEffectiveEndRow() {
      getBaseRow(0); // init
      return effectiveEndRow;
   }

   /**
    * Get the measures that will be plotted using this dataset.
    */
   public String[] getVars() {
      return vars;
   }

   /**
    * Set the measures that will be plotted using this dataset.
    */
   public void setVars(String[] vars) {
      this.vars = vars;
   }

   /**
    * Get all the group fields.
    */
   public String[] getGroupFields() {
      return groupFields;
   }

   /**
    * Set all the group fields (dimension except the inner dimension, same as TimeSeriesRow).
    */
   public void setGroupFields(String[] groupFields) {
      this.groupFields = groupFields;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public SortedDataSet clone() {
      SortedDataSet obj = (SortedDataSet) super.clone();

      obj.base = (DataSet) this.base.clone();
      obj.cols = new ArrayList<>(this.cols);
      obj.comps = Tool.deepCloneCollection(this.comps);
      obj.rows = (BitSet) this.rows.clone();
      obj.compmap = Tool.deepCloneMap(this.compmap);
      obj.sortrows = new HashMap<>(this.sortrows);
      obj.mapping = this.mapping.clone();

      return obj;
   }

   @Override
   public void dispose() {
      super.dispose();
      disposed.set(true);
   }

   @Override
   public boolean isDisposed() {
      return disposed.get() || super.isDisposed();
   }

   private DataSet base; // base data set
   private List<Integer> cols = new ArrayList<>(); // columns to compare
   private List<Comparator> comps = new ArrayList<>(); // comparer
   private BitSet rows = new BitSet();
   private Map<String, Comparator> compmap = new HashMap<>(); // col -> Comparator
   private Map<String, Boolean> sortrows = new HashMap<>(); // col -> sortrow
   private int[] mapping; // row mapping
   private boolean sorted;
   private int startRow = 0;
   private int endRow = -1;
   private int effectiveEndRow = -1;
   private String[] vars;
   private String[] groupFields;
   private final AtomicBoolean disposed = new AtomicBoolean();
   private boolean forceSort = false;
   private transient String tsColumn = null;
   private boolean ignoreZeroCalc = false;

   private static final Logger LOG = LoggerFactory.getLogger(SortedDataSet.class);
}
