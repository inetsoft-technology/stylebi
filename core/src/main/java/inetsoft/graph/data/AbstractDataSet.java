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
package inetsoft.graph.data;

import com.inetsoft.build.tern.TernMethod;
import inetsoft.graph.EGraph;
import inetsoft.graph.coord.Coordinate;
import inetsoft.uql.viewsheet.ColumnNotFoundException;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This is the base class for data set. It implements the calculated columns.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public abstract class AbstractDataSet implements DataSet {
   /**
    * This method must be called before the calculated columns can be used. It
    * is normally called by the graph rendering engine and doesn't need to be
    * called explicitly by caller.
    * @param dim the innermost dimension column in the graph.
    * @param rows a list of row indexes to calculate values using CalcColumn.
    * @param calcMeasures false if ignoring measure calc columns.
    */
   @Override
   public synchronized void prepareCalc(String dim, int[] rows, boolean calcMeasures) {
      if((calcs == null || calcs.size() <= 0) && (rcalcs == null || rcalcs.size() <= 0)) {
         return;
      }

      // calc not inited now
      // @by davyc, now, a discrete measure will no have sort comparator,
      // so will not cause problem, if we support sort discrete measure,
      // here need reimplemented
      innerDim = dim;
      boolean calc = isCalcColumn(dim);
      DataSet data = dim != null && !calc ? new SortedDataSet(this, dim) : this;
      boolean processed = false;
      boolean processRows = this.rcalcvals == null;
      Set<String> ovalidCalcs = validCalcs;
      validCalcs = new HashSet<>();
      idxmap = null;

      if(processRows) {
         processed = true;
         prepareCalcRow(data, true);
      }

      if(calcvals == null || (rows != null && rows.length != 0)) {
         processed = true;
         prepareCalcCol(data, rows, calcMeasures);
      }

      if(processRows) {
         processed = true;
         prepareCalcRow(data, false);
      }

      if(!processed) {
         validCalcs = ovalidCalcs;
      }
   }

   /**
    * Check if the column is a calc column.
    */
   private boolean isCalcColumn(String col) {
      return col != null && calcs != null &&
         calcs.stream().anyMatch(c -> col.equals(c.getHeader()));
   }

   /**
    * Process calc columns.
    */
   private void prepareCalcCol(DataSet data, int[] rows, boolean calcMesures) {
      Vector<Object[]> calcvals = this.calcvals;

      int n1 = (rows == null) ? 0 : rows[0];
      // @by ChrisSpagnoli bug1431071822960 2015-5-11
      // Consider trend line project forward
      int n2 = (rows == null) ? (data instanceof AbstractDataSet ?
                                 ((AbstractDataSet) data).getRowCountUnprojected() :
                                 data.getRowCount()) - 1 :
         rows[rows.length - 1];
      int cnt = (rows == null) ? (data instanceof AbstractDataSet ?
                                  ((AbstractDataSet) data).getRowCountUnprojected() :
                                  data.getRowCount()) :
         rows.length;

      if(calcvals == null) {
         calcvals = new Vector();
      }

      calcvals.setSize(data.getRowCount());
      // place holder
      CalcColumn[] calcs = this.calcs == null ? new CalcColumn[0]
         : this.calcs.toArray(new CalcColumn[0]);
      CalcColumn[] postCalcs = new CalcColumn[calcs.length];
      boolean hasPostCalc = false;
      Set<String> cnames = new HashSet();

      for(int i = 0; i < calcs.length; i++) {
         cnames.add(calcs[i].getHeader());
      }

      for(int i = calcs.length - 1; i >= 0; i--) {
         CalcColumn calc = calcs[i];
         String field = calc.getField();

         if(field != null && cnames.contains(field)) {
            // hold the place
            calcs[i] = null;
            postCalcs[i] = calc;
            hasPostCalc = true;
         }
      }

      int dataRows = data instanceof AbstractDataSet ?
         ((AbstractDataSet) data).getRowCount0() : data.getRowCount();

      for(int k = 0; k < cnt && !isDisposed(); k++) {
         Object[] row = new Object[calcs.length];
         int i = (rows == null) ? k : rows[k];
         int rowidx = data == this || i >= dataRows ? i : ((DataSetFilter) data).getBaseRow(i);

         for(int j = 0; j < calcs.length; j++) {
            if(calcs[j] != null) {
               if(calcMesures || !calcs[j].isMeasure()) {
                  if(i < dataRows) {
                     row[j] = calcs[j].calculate(data, i, i == n1, i == n2);
                  }
                  else {
                     row[j] = calcs[j].calculate(this, i, i == n1, i == n2);
                  }
               }

               if(row[j] == CalcColumn.INVALID) {
                  row[j] = null;
               }
               else {
                  validCalcs.add(calcs[j].getHeader());
               }
            }
         }

         calcvals.set(rowidx, row);
      }

      for(int j = 0; j < calcs.length; j++) {
         if(calcs[j] != null) {
            calcs[j].complete();
         }
      }

      // so the post calcs can iterator it
      this.calcvals = calcvals;

      if(hasPostCalc) {
         for(int k = 0; k < cnt && !isDisposed(); k++) {
            int i = (rows == null) ? k : rows[k];
            int rowidx = data == this ? i : ((DataSetFilter) data).getBaseRow(i);
            Object[] row = calcvals.get(rowidx);

            for(int j = 0; j < postCalcs.length; j++) {
               if(postCalcs[j] != null) {
                  row[j] = postCalcs[j].calculate(data, i, i == n1, i == n2);

                  if(row[j] == CalcColumn.INVALID) {
                     row[j] = null;
                  }
                  else {
                     validCalcs.add(postCalcs[j].getHeader());
                  }
               }
            }
         }

         for(int j = 0; j < postCalcs.length; j++) {
            if(postCalcs[j] != null) {
               postCalcs[j].complete();
            }
         }
      }

      // add column, so calc row will be full columns
      this.calcvals = calcvals;
      idxmap = null;
   }

   /**
    * Process calc rows..
    */
   private void prepareCalcRow(DataSet data, boolean preCol) {
      List<Object[]> rcalcvals = preCol ? new ArrayList<>() : this.rcalcvals;

      if(rcalcs != null) {
         for(CalcRow rcalc : rcalcs) {
            if(preCol == rcalc.isPreColumn() && !isDisposed()) {
               for(Object[] row : rcalc.calculate(data)) {
                  rcalcvals.add(row);
               }
            }
         }
      }

      this.rcalcvals = rcalcvals;
   }

   @Override
   @TernMethod
   public boolean containsValue(String ...measures) {
      if(calcs == null || calcs.size() == 0 || measures.length == 0) {
         return true;
      }

      return Arrays.stream(measures).anyMatch(m -> validCalcs.contains(m) || !isCalcColumn(m));
   }

   /**
    * Initialize any data for this graph.
    * @param graph the (innermost) egraph that will plot this dataset.
    * @param coord the (innermost) coordiante that will plot this dataset.
    */
   @Override
   public void prepareGraph(EGraph graph, Coordinate coord, DataSet dataset) {
   }

   /**
    * Return the number of rows in the data set.
    */
   @Override
   @TernMethod
   public int getRowCount() {
      int rcnt = getRowCountUnprojected();

      if(rcnt > 0) {
         rcnt += getRowsProjectedForward();
      }

      return rcnt;
   }

   /**
    * Return the number of rows in the data set, excluding projected
    */
   @TernMethod
   public int getRowCountUnprojected() {
      return getRowCountUnprojected0() + getCalcRowCount();
   }

   /**
    * Get calculated row count.
    * @hidden
    */
   public int getCalcRowCount() {
      List<Object[]> rcalcvals = this.rcalcvals;
      return rcalcvals != null ? rcalcvals.size() : 0;
   }

   /**
    * Set the projected rows in the data set
    */
   @TernMethod
   public void setRowsProjectedForward(int rows) {
      rowsProjectedForward = rows;
      projected = null;

      // @by ChrisSpagnoli bug1430357109900 2015-5-6
      // Pass the set action down to any child data sets
      try {
         subDataSetsLock.readLock().lock();

         if(subDataSets != null) {
            for(AbstractDataSet sub : subDataSets) {
               sub.setRowsProjectedForward(rows);
            }
         }
      }
      finally {
         subDataSetsLock.readLock().unlock();
      }
   }

   /**
    * Return the projected rows in the data set
    */
   @TernMethod
   public int getRowsProjectedForward() {
      return rowsProjectedForward;
   }

   /**
    * Set the column to project forward. Project all date/number columns if not set.
    */
   @TernMethod
   public void setProjectColumn(String col) {
      this.projectColumn = col;
   }

   /**
    * Get the column to project forward.
    */
   @TernMethod
   public String getProjectColumn() {
      return projectColumn;
   }

   /**
    * Return the number of columns in the data set.
    */
   @Override
   @TernMethod
   public final int getColCount() {
      int calcCols = 0;

      List<CalcColumn> calcs = this.calcs;
      Vector<Object[]> calcvals = this.calcvals;

      if(calcvals == null || calcs == null) {
         synchronized(this) {
            calcs = this.calcs;
            calcvals = this.calcvals;
         }
      }

      calcCols = calcvals != null && calcs != null ? calcs.size() : 0;
      return getColCount0() + calcCols;
   }

   /**
    * Return the data at the specified cell.
    * @param col the specified column name.
    * @param row the specified row index.
    * @return the data at the specified cell.
    */
   @Override
   @TernMethod
   public Object getData(String col, int row) {
      int cidx = indexOfHeader(col);
      return (cidx >= 0) ? getData(cidx, row) : null;
   }

   /**
    * Return the data at the specified cell.
    * @param col the specified column index.
    * @param row the specified row index.
    * @return the data at the specified cell.
    */
   @Override
   public Object getData(int col, int row) {
      boolean useBase = false;
      final int colCount0 = getColCount0();
      final int rowCount0 = getRowCount0();

      synchronized(this) {
         List<Object[]> calcvals = this.calcvals;

         // optimization, avoid calling getRowsProjectedForward() repeatedly.
         // this is assuming rowsProjectedForward is always set before getData() is called.
         if(projected == null) {
            projected = getRowsProjectedForward() > 0;
         }

         // optimization, no need to check projected rows if not projecting
         if(projected && col < colCount0) {
            final int rowCountUnprojected = getRowCountUnprojected();

            if(row >= rowCountUnprojected && row < getRowCount() && rowCountUnprojected > 0) {
               if(!shouldProject(col)) {
                  // if dimension, use the last value instead of null so the subsequent
                  // dimension can project successfully. (49385)
                  if(!isMeasure(getHeader(col))) {
                     return getData0(col, rowCountUnprojected - 1);
                  }

                  return null;
               }

               return getDataProjected(col, row);
            }
         }

         // optimization, if no calc col/row, just get from base
         // calling getColCount0/getRowCount0 could be expensive
         useBase = rcalcvals == null && calcvals == null || col < colCount0 && row < rowCount0;
      }

      if(useBase) {
         return getData0(col, row);
      }

      synchronized(this) {
         if(row >= rowCount0 && rcalcvals != null && (row - rowCount0) < rcalcvals.size() &&
            rcalcvals.get(row - rowCount0).length > col)
         {
            return rcalcvals.get(row - rowCount0)[col];
         }

         if(calcvals == null || row < 0 || row >= calcvals.size() || col - colCount0 < 0 ||
            calcvals.get(row) == null || calcvals.get(row).length < col - colCount0)
         {
            return null;
         }

         return calcvals.get(row)[col - colCount0];
      }
   }

   /**
    * Check if the specified column should be projected forward.
    */
   protected boolean shouldProject(int col) {
      // should only project dimension. measure should be null. (48342)
      return !isMeasure(getHeader(col)) &&
         (projectColumn == null || projectColumn.equals(getHeader(col)));
   }

   /**
    * Get the index of the specified header.
    * @param col the specified column header.
    */
   @Override
   public final int indexOfHeader(String col) {
      return indexOfHeader(col, false);
   }

   /**
    * Get the index of the specified header
    * @param all true to include calc column even if it hasn't been generated.
    */
   @TernMethod
   public final int indexOfHeader(String col, boolean all) {
      Object2IntOpenHashMap<String> idxmap = this.idxmap;
      int idx;

      synchronized(this) {
         idx = idxmap != null && idxmap.containsKey(col) ? idxmap.getInt(col) : -1;
      }

      if(idx >= 0) {
         // the base table calc columns may be removed so the cached index
         // may become invalid. (53628)
         if(idx < getColCount()) {
            return idx;
         }

         synchronized(this) {
            idxmap.remove(col);
         }
      }

      synchronized(this) {
         if(idxmap == null) {
            idxmap = this.idxmap = new Object2IntOpenHashMap<>();
         }

         if(calcvals != null || all) {
            List<CalcColumn> clist = getCalcColumns(true);
            int size = clist.size();

            for(int i = 0; i < size; i++) {
               if(clist.get(i).getHeader().equals(col)) {
                  idx = i + getColCount0();
                  idxmap.put(col, idx);
                  return idx;
               }
            }
         }
      }

      idx = indexOfHeader0(col, all);

      if(idx >= 0) {
         synchronized(this) {
            idxmap.put(col, idx);
         }
      }

      return idx;
   }

   /**
    * Return the column header at the specified column.
    * @param col the specified column index.
    */
   @Override
   @TernMethod
   public final String getHeader(int col) {
      return (calcs == null || col < getColCount0()) ? getHeader0(col) :
         getCalcColumns(true).get(col - getColCount0()).getHeader();
   }

   /**
    * Get the data type of the column.
    */
   @Override
   @TernMethod
   public final Class<?> getType(String col) {
      int idx = indexOfHeader(col, true);

      if(idx < 0) {
         throw new ColumnNotFoundException("Column not found: " + col + " in " + getHeadersStr());
      }

      if(idx < getColCount0()) {
         return getType0(col);
      }

      return getCalcColumns(true).get(idx - getColCount0()).getType();
   }

   /**
    * Check if the column is measure.
    * @param col the specified column name.
    */
   @Override
   @TernMethod
   public final boolean isMeasure(String col) {
      int idx = indexOfHeader(col, true);

      if(idx < 0) {
         throw new ColumnNotFoundException("Column not found: " + col + " in " + getHeadersStr());
      }

      if(idx < getColCount0()) {
         return isMeasure0(col);
      }

      return getCalcColumns(true).get(idx - getColCount0()).isMeasure();
   }

   /**
    * Get the comparer to sort data at the specified column.
    * @param col the specified column.
    */
   @Override
   public final synchronized Comparator getComparator(String col) {
      Comparator comp1 = getComparator0(col);
      Comparator comp = DataSetComparator.getComparator(comp1, this);

      if(rcalcs != null) {
         for(CalcRow rcalc : rcalcs) {
            Comparator comp2 = rcalc.getComparator(col);

            if(comp == null) {
               comp = comp2;
            }
            else if(comp2 != null) {
               comp = new CombinedDataSetComparator(col, comp, comp2);
            }
         }
      }

      return comp;
   }

   /**
    * Return the data at the specified cell.
    * @param col the specified column index.
    * @param row the specified row index.
    */
   protected abstract Object getData0(int col, int row);

   /**
    * Return the number of columns in the data set without the calculated
    * columns.
    */
   protected abstract int getColCount0();

   /**
    * Return the number of rows in the data set, without the calculated
    * rows.
    */
   protected abstract int getRowCount0();

   /**
    * Return the number of un-projected rows in the data set, without the
    * calculated rows.
    */
   protected abstract int getRowCountUnprojected0();

   /**
    * Get the index of the specified header.
    * @param col the specified column header.
    */
   protected final int indexOfHeader0(String col) {
      return indexOfHeader0(col, false);
   }

   protected abstract int indexOfHeader0(String col, boolean all);

   /**
    * Return the column header at the specified column.
    * @param col the specified column index.
    */
   protected abstract String getHeader0(int col);

   /**
    * Get the data type of the column.
    */
   protected abstract Class getType0(String col);

   /**
    * Check if the column is measure.
    * @param col the specified column name.
    */
   protected abstract boolean isMeasure0(String col);

   /**
    * Get the comparer to sort data at the specified column.
    * @param col the specified column.
    */
   protected abstract Comparator getComparator0(String col);

   @Override
   public Comparator getOrigComparator(String col) {
      return getComparator0(col);
   }

   /**
    * Add a calculated columns. If a column with the same name already exists,
    * the existing column is replaced with the new column.
    */
   @Override
   @TernMethod
   public synchronized void addCalcColumn(CalcColumn col) {
      if(calcs == null) {
         calcs = new Vector<>();
      }

      for(int i = 0; i < calcs.size(); i++) {
         if(calcs.get(i).getHeader().equals(col.getHeader())) {
            calcs.set(i, col);
            return;
         }
      }

      calcs.add(col);
      calcvals = null;
      rcalcvals = null;
      idxmap = null;
   }

   /**
    * Get the calculated columns.
    */
   @Override
   @TernMethod
   public List<CalcColumn> getCalcColumns() {
      return getCalcColumns(false);
   }

   /**
    * Get the calculated columns.
    * @param self true to get the current data, otherwise recursively get all
    * calc columns.
    */
   public List<CalcColumn> getCalcColumns(boolean self) {
      return calcs != null ? new ArrayList<>(calcs) : new ArrayList<>();
   }

   /**
    * Remove all calculated columns.
    */
   @Override
   @TernMethod
   public synchronized void removeCalcColumns() {
      calcs = null;
      idxmap = null;
   }

   /**
    * Add a calculated rows.
    */
   @Override
   @TernMethod
   public synchronized void addCalcRow(CalcRow row) {
      if(rcalcs == null) {
         rcalcs = new Vector<>();
      }

      if(!rcalcs.contains(row)) {
         rcalcs.add(row);
      }

      calcvals = null;
      rcalcvals = null;
   }

   /**
    * Get the calculated rows.
    */
   @Override
   @TernMethod
   public List<CalcRow> getCalcRows() {
      return rcalcs != null ? new ArrayList<>(rcalcs) : new ArrayList<>();
   }

   /**
    * Remove all calculated rows.
    */
   @Override
   @TernMethod
   public synchronized void removeCalcRows() {
      rcalcs = null;
   }

   /**
    * Remove calc rows with the specified type.
    */
   @Override
   public synchronized void removeCalcRows(Class cls) {
      if(rcalcs != null) {
         for(int i = 0; i < rcalcs.size(); i++) {
            if(cls.isAssignableFrom(rcalcs.get(i).getClass())) {
               rcalcs.remove(i--);
            }
         }
      }
   }

   /**
    * Clear the calculated colum and row values.
    */
   @Override
   @TernMethod
   public synchronized void removeCalcValues() {
      calcvals = null;
      rcalcvals = null;
      idxmap = null;
   }

   @Override
   @TernMethod
   public synchronized void removeCalcColValues() {
      calcvals = null;
      idxmap = null;
   }

   @Override
   @TernMethod
   public synchronized void removeCalcRowValues() {
      rcalcvals = null;
      idxmap = null;
   }

   @Override
   public Object clone() {
      return clone(false);
   }

   @Override
   public synchronized DataSet clone(boolean shallow) {
      AbstractDataSet obj = null;

      try {
         obj = (AbstractDataSet) super.clone();
      }
      catch(CloneNotSupportedException e) {
         LOG.error("Failed to clone data set", e);
         return obj;
      }

      obj.idxmap = null;
      obj.calcs = this.calcs != null ? new Vector<>(this.calcs) : null;
      obj.rcalcs = this.rcalcs != null ? new Vector<>(this.rcalcs) : null;
      // need to recalculate (51059)
      obj.cacheNumber = null;
      obj.cacheDate = null;

      if(rcalcvals != null) {
         obj.rcalcvals = new Vector<>(this.rcalcvals);
      }

      if(calcvals != null) {
         obj.calcvals = new Vector<>(this.calcvals);
      }

      // the root dataset is cloned in many places but the sub-datasets are expected to be kept.
      // need to change the places where dataset is cloned if this list is cloned. (50399)
      // always sharing subDataSets causes race condition. (52559)
      if(subDataSets != null && !shallow) {
         obj.subDataSets = new ArrayList<>(subDataSets);
      }

      return obj;
   }

   // @by ChrisSpagnoli bug1429507986738 2015-4-24
   // This method is executed by DataSetIndex.createSubDataSet(), in order to
   // save the reference to the child DataSets for later Trend Line Projection.
   /**
    * Add a SubDataSet to this DataSet
    * @hidden
    */
   public void addSubDataSet(DataSet ds) {
      if(!(ds instanceof AbstractDataSet)) {
         return;
      }

      final AbstractDataSet ads = (AbstractDataSet) ds;

      // verify col counts match, but row counts less
      if(ads.getRowCountUnprojected() == getRowCountUnprojected() ||
         ads.getColCount() != getColCount())
      {
         return;
      }

      // verify col data types match

      if(ads.getRowCount() > 0 && getRowCount() > 0) {
         for(int i = 0; i < getColCount(); i++) {
            boolean checkedEquality = false;

            try {
               Class class1 = ads.getType(ads.getHeader(i));
               Class class2 = getType(getHeader(i));

               if(class1 != null && class2 != null) {
                  checkedEquality = true;

                  if(!class1.equals(class2)) {
                     return;
                  }
               }
            }
            catch(Exception e) {
               // do nothing
            }

            // if for some reason it failed to check for equality then default to
            // the previous behavior
            if(!checkedEquality) {
               Object data1 = ads.getData(i, 0);
               Object data2 = getData(i, 0);

               if(data1 != null && data2 == null ||
                  data1 == null && data2 != null ||
                  data1 != null && data2 != null && data1.getClass() != data2.getClass())
               {
                  return;
               }
            }
         }
      }

      try {
         subDataSetsLock.writeLock().lock();

         for(AbstractDataSet sub : subDataSets) {
            if(sub.equals(ads)) {
               return;
            }
         }

         subDataSets.add(ads);
      }
      finally {
         subDataSetsLock.writeLock().unlock();
      }
   }

   /**
    * @hidden
    */
   public void clearSubDataSetDuplicates(AbstractDataSet dset) {
      try {
         subDataSetsLock.writeLock().lock();
         int index = subDataSets.indexOf(dset);

         if(index != -1) {
            for(AbstractDataSet subDataSet : subDataSets) {
               if(subDataSet.equals(dset) && subDataSet != dset) {
                  subDataSets.remove(index);
                  break;
               }
            }
         }
      }
      finally {
         subDataSetsLock.writeLock().unlock();
      }
   }

   // @by ChrisSpagnoli bug1429507986738 2015-4-24
   // This method is executed by addSubDataSet(), in order to verify that the
   // DataSet being adding to subDataSets does not already exist.
   private boolean equals(AbstractDataSet ads) {
      final int colCnt = ads.getColCount();
      final int rowCnt = ads.getRowCountUnprojected();

      if(rowCnt != getRowCountUnprojected() || colCnt != getColCount()) {
         return false;
      }

      if(getDataHash() != ads.getDataHash()) {
         return false;
      }

      for(int c = 0; c < colCnt; c++) {
         for(int r = 0; r < rowCnt; r++) {
            if(!(inetsoft.util.Tool.equals(getData(c, r), ads.getData(c, r)))) {
               return false;
            }
         }
      }

      return true;
   }

   private synchronized long getDataHash() {
      if(dataHash != Long.MIN_VALUE) {
         return dataHash;
      }

      final int colCnt = getColCount();
      final int rowCnt = getRowCountUnprojected();
      dataHash = 0;

      for(int c = 0; c < colCnt; c++) {
         for(int r = 0; r < rowCnt; r++) {
            Object val = getData(c, r);
            dataHash += (val == null ? 0 : val.hashCode()) * (long) c + r;
         }
      }

      return dataHash;
   }

   // @by: ChrisSpagnoli feature1379102629417 2015-1-10
   // This method projects the X-axis forward
   /**
    * Return the data at the specified cell.
    * @param col the specified column index.
    * @param row the specified row index.
    * @return the data at the specified cell.
    */
   private Object getDataProjected(int col, int row) {
      Object val = getData(col, getRowCountUnprojected() -1);

      // String, Boolean, Enum = cannot project

      if(val instanceof Timestamp) {     // test this before Date
         return getDataProjectedRowDate((Timestamp) val, col, row);
      }
      else if(val instanceof Date) {
         return getDataProjectedRowDate((Date) val, col, row);
      }
      // if there are date dimension, only project date and not numbers. (57885)
      else if(val instanceof Number) {
         return getDataProjectedRowNumber((Number) val, col, row);
      }

      // if data can't be projected, just return the original data.
      // this prevents null from be inserted to the scale (or legend)
      return val;
   }

   /**
    * Set the projected value calculated during chart rendering.
    */
   @TernMethod
   public void addProjectedValue(Map values) {
      if(projectedValues == null) {
         projectedValues = new ArrayList<>();
      }

      // projection may be done multiple times, we either clear or avoid duplicate values
      // at the caller, or we check it here
      if(!projectedValues.contains(values)) {
         projectedValues.add(values);
      }
   }

   /**
    * Get the number of projected values.
    */
   @TernMethod
   public int getProjectedValueCount() {
      return projectedValues != null ? projectedValues.size() : 0;
   }

   /**
    * Get the projected value calculated during chart rendering.
    * @return name value pair, column header -&gt; value
    */
   @TernMethod
   public Map getProjectedValue(int idx) {
      return projectedValues != null ? projectedValues.get(idx) : null;
   }

   // @by: ChrisSpagnoli bug1422606337989 #2 2015-2-2
   /**
    * Calculates a difference map from two given dates.
    * @param date1 the first date.
    * @param date2 the second date.
    * @return a difference map which if added to date1 would result in date2.
    */
   private static Map<Integer, Long> dateDiff(final java.util.Date date1,
      final java.util.Date date2)
   {
      Calendar cal1 = new GregorianCalendar();
      Calendar cal2 = new GregorianCalendar();
      cal1.setTime(date1);
      cal1.set(Calendar.MILLISECOND, 0);
      cal2.setTime(date2);
      cal2.set(Calendar.MILLISECOND, 0);
      cal2.add(Calendar.YEAR, - cal1.get(Calendar.YEAR) + 1000);
      cal2.add(Calendar.MONTH, - cal1.get(Calendar.MONTH));
      cal2.add(Calendar.DAY_OF_MONTH, - (cal1.get(Calendar.DAY_OF_MONTH)-1));
      cal2.add(Calendar.HOUR, - cal1.get(Calendar.HOUR));
      cal2.add(Calendar.MINUTE, - cal1.get(Calendar.MINUTE));
      cal2.add(Calendar.SECOND, - cal1.get(Calendar.SECOND));
      Map<Integer, Long> diffMap = new HashMap<>();
      diffMap.put(Calendar.YEAR, (long) (cal2.get(Calendar.YEAR) - 1000));
      diffMap.put(Calendar.MONTH, (long) cal2.get(Calendar.MONTH));
      diffMap.put(Calendar.DAY_OF_MONTH, (long) (cal2.get(Calendar.DAY_OF_MONTH)-1));
      diffMap.put(Calendar.HOUR, (long) cal2.get(Calendar.HOUR));
      diffMap.put(Calendar.MINUTE, (long) cal2.get(Calendar.MINUTE));
      diffMap.put(Calendar.SECOND, (long) cal2.get(Calendar.SECOND));
      diffMap.put(DIFF, date2.getTime() - date1.getTime());
      return diffMap;
   }

   // @by: ChrisSpagnoli bug1422606337989 #2 2015-2-2
   /**
    * Add the given difference map to a given date.
    * @param date the given date.
    * @param diffMap the difference map.
    * @return a date which is given date increased by given difference map.
    */
   private static Date dateAdd(final Date date, Map<Integer, Long> diffMap) {
      Calendar cal = new GregorianCalendar();
      cal.setTime(date);
      cal.set(Calendar.MILLISECOND, 0);
      cal.add(Calendar.YEAR, Math.toIntExact(diffMap.get(Calendar.YEAR)));
      cal.add(Calendar.MONTH, Math.toIntExact(diffMap.get(Calendar.MONTH)));
      cal.add(Calendar.DAY_OF_MONTH, Math.toIntExact(diffMap.get(Calendar.DAY_OF_MONTH)));
      cal.add(Calendar.HOUR, Math.toIntExact(diffMap.get(Calendar.HOUR)));
      cal.add(Calendar.MINUTE, Math.toIntExact(diffMap.get(Calendar.MINUTE)));
      cal.add(Calendar.SECOND, Math.toIntExact(diffMap.get(Calendar.SECOND)));

      return cal.getTime();
   }

   /**
    * Tests if a difference map is all zero.
    */
   private static boolean isZeroDateDiff(final Map<Integer, Long> diffMap) {
      return diffMap.get(Calendar.YEAR) == 0 &&
         diffMap.get(Calendar.MONTH) == 0 &&
         diffMap.get(Calendar.DAY_OF_MONTH) == 0 &&
         diffMap.get(Calendar.HOUR) == 0 &&
         diffMap.get(Calendar.MINUTE) == 0 &&
         diffMap.get(Calendar.SECOND) == 0;
   }

   /**
    * Tests if any part is negative.
    */
   private static boolean isNegativeDateDiff(final Map<Integer, Long> diffMap) {
      return diffMap.get(Calendar.YEAR) < 0 ||
         diffMap.get(Calendar.MONTH) < 0 ||
         diffMap.get(Calendar.DAY_OF_MONTH) < 0 ||
         diffMap.get(Calendar.HOUR) < 0 ||
         diffMap.get(Calendar.MINUTE) < 0 ||
         diffMap.get(Calendar.SECOND) < 0;
   }

   /**
    * This method projects a X-axis made up of Dates forward
    * Return the data at the specified cell.
    * @param col the specified column index.
    * @param row the specified row index.
    * @return the data at the specified cell.
    */
   private <T extends Date> Date getDataProjectedRowDate(T obj, int col, int row) {
      if(cacheDate == null) {
         cacheDate = new Object2ObjectOpenHashMap<>();
      }

      CacheDate cd = cacheDate.get(col);

      if(cd == null) {
         cd = new CacheDate();
         cacheDate.put(col, cd);
      }

      if(row >= getRowsProjectedForward() + getRowCountUnprojected() ||
         getRowCountUnprojected() < 2 || col >= getColCount())
      {
         return null;
      }

      // @by ChrisSpagnoli bug1429507986738 2015-4-24
      if(cd.failFlag) {
         return getDataProjectedRowDateFromMySubs(obj, col, row);
      }

      if(cd.slope == null && cd.first == null) {
         Set series = new HashSet();
         boolean first = true;

         // should not include fill-time-series rows since they are appended to the end and
         // mess up the date sequence. (53892)
         for(int i = 0; i < getRowCountUnprojected0(); i++) {
            Object o = getData0(col, i);

            // allow leading null
            if(o == null && first) {
               continue;
            }

            if(obj == null || o == null || !o.getClass().equals(obj.getClass())) {
               return null;
            }

            Date d = (Date) o;
            series.add(d);

            if(first) {
               cd.first = d;
               first = false;
            }
            // @by ChrisSpagnoli bug1428396209447 2015-4-7
            else if(cd.slope == null || isZeroDateDiff(cd.slope)) {
               cd.slope = dateDiff(cd.first, d);
            }
            else {
               // @by ChrisSpagnoli bug1427769771644 2015-4-2
               if(!isZeroDateDiff(dateDiff(cd.last, d))) {
                  final Date d2 = dateAdd(cd.last, cd.slope);

                  // if projected data and actual date at the position is different.
                  if(!d2.equals(d)) {
                     // in multi-style chart, data sets are unioned so the dim values will
                     // repeat, resulting in the project forward to fail. check this
                     // condition and allow it to project. (49882)
                     if(subDataSets.isEmpty() && isRepeatingSeries(col, i, series)) {
                        break;
                     }
                     // if no sub dataset, and the slope changed, we should just use the
                     // min slope to project. for example, a date series of:
                     // 2021-01-01, 2021-01-03, 2021-01-08
                     // should not fail, we can just project by 2 days at the end. (51032)
                     else if(subDataSets.isEmpty()) {
                        Map<Integer, Long> slope2 = dateDiff(cd.last, d);

                        // if the dataset is composed of multiple series, the first
                        // series may contain one value (e.g. 2000), and when the cd.slope
                        // is calculated, it may be computed with the first value of the
                        // next series (e.g. 1997), and resulting in a negative increment (-3).
                        // this condition corrects this value. (58116)
                        if(isNegativeDateDiff(cd.slope) && !isNegativeDateDiff(slope2) &&
                           !isZeroDateDiff(slope2))
                        {
                           cd.slope = slope2;
                        }
                        else {
                           cd.slope = getMinSlope(cd.slope, slope2);
                        }

                        if(cd.slope == null) {
                           cd.failFlag = true;
                        }
                     }
                     else {
                        cd.failFlag = true;
                        // @by ChrisSpagnoli bug1429507986738 2015-4-24
                        return getDataProjectedRowDateFromMySubs(obj, col, row);
                     }
                  }
               }
            }

            cd.last = d;
         }
      }

      if(cd.slope == null) {
         return null;
      }

      Date d3 = cd.last;

      for(int j = getRowCountUnprojected() - 1; j < row; j++) {
         d3 = dateAdd(d3, cd.slope);
      }

      return d3;
   }

   // get the min of the slope values.
   private Map<Integer, Long> getMinSlope(Map<Integer, Long> slope1, Map<Integer, Long> slope2) {
      long diff1 = slope1.get(DIFF);
      long diff2 = slope2.get(DIFF);

      if(diff1 > 0 && diff2 < 0 || diff1 < 0 && diff2 > 0) {
         return null;
      }

      return diff1 < diff2 ? slope1 : slope2;
   }

   // @by ChrisSpagnoli bug1429507986738 2015-4-24
   // This method is executed by the PARENT DataSet, and invokes the
   // getDataProjectedRowDate() method in each CHILD DataSet.
   private <T extends Date> Date getDataProjectedRowDateFromMySubs(T obj, int col, int row) {
      HashMap<Date,Integer> projectedDates = new HashMap<>();

      // Create HashMap counting the projected dates
      try {
         subDataSetsLock.readLock().lock();

         if(subDataSets != null) {
            for(AbstractDataSet sub : subDataSets) {
               final int rowDelta = row - getRowCountUnprojected();
               Date d = sub.getDataProjectedRowDateFromOneSub(obj, col, rowDelta);

               if(d != null) {
                  int nextVal = 1;

                  if(projectedDates.containsKey(d)) {
                     nextVal = projectedDates.get(d);
                     nextVal++;
                  }

                  projectedDates.put(d, nextVal);
               }
            }
         }
      }
      finally {
         subDataSetsLock.readLock().unlock();
      }

      if(!projectedDates.isEmpty()) {
         // Figure out what the max count of projected dates was
         int maxCount = -1;

         for(Integer value : projectedDates.values()) {
            maxCount = Math.max(maxCount, value);
         }

         // Pick the projected date which was the most common
         Date commonDate = null;

         for(Map.Entry<Date, Integer> entry : projectedDates.entrySet()) {
            Date key = entry.getKey();
            Integer value = entry.getValue();

            if(value == maxCount) {
               commonDate = key;
               break;
            }
         }

         // @by ChrisSpagnoli bug1430357109900 2015-5-6
         // Keep subDataSets which did not perform, for setRowsProjectedForward()
         return commonDate;
      }

      return null;
   }

   // @by ChrisSpagnoli bug1429507986738 2015-4-24
   // This method is executed in the CHILD data set, and is a wrapper to convert
   //  the "projected" row from parent to the matching "projected" row in child.
   /**
    * Invoke this "child" SubDataSet from the "parent" DataSet
    * @hidden
    */
   public <T extends Date> Date getDataProjectedRowDateFromOneSub(T obj, int col, int rowDelta) {
      // @by ChrisSpagnoli bug1429507986738.reopen#2 2015-4-29
      // Discard child "projections" from absurdly small data sets
      if(getRowCountUnprojected() < 3) {
         return null;
      }

      final int row = getRowCountUnprojected() + rowDelta;
      return getDataProjectedRowDate(obj, col, row);
   }

   /**
    * Helper method for getDataProjectedRowNumber(), to subtract two Numbers
    * @param n1 the Number to be subtracted from
    * @param n2 the Number to subtract
    * @return true if the given Number n is zero
    */
    private <T extends Number> Number diffNumbers(T n1, T n2) {
      if(n1 instanceof Byte) {
         return Byte.valueOf((byte)(n1.byteValue() - n2.byteValue()));
      }
      else if(n1 instanceof Short) {
         return Short.valueOf((short)(n1.shortValue() - n2.shortValue()));
      }
      else if(n1 instanceof Integer) {
         return Integer.valueOf(n1.intValue() - n2.intValue());
      }
      else if(n1 instanceof Long) {
         return Long.valueOf(n1.longValue() - n2.longValue());
      }
      else if(n1 instanceof Double) {
         return Double.valueOf(n1.doubleValue() - n2.doubleValue());
      }
      else if(n1 instanceof Float) {
         return Float.valueOf(n1.floatValue() - n2.floatValue());
      }

      return null;
   }

   /**
    * Helper method for getDataProjectedRowNumber(), to check if a Number
    * has a value of zero.
    * @param n the reference Number to be tested
    * @return true if the given Number n is zero
    */
    private <T extends Number> boolean isZeroNumber(T n) {
      if(n instanceof Byte) {
         return n.byteValue() == 0;
      }
      else if(n instanceof Short) {
         return n.shortValue() == 0;
      }
      else if(n instanceof Integer) {
         return n.intValue() == 0;
      }
      else if(n instanceof Long) {
         return n.longValue() == 0;
      }
      else if(n instanceof Double) {
         return n.doubleValue() == 0;
      }
      else if(n instanceof Float) {
         return n.floatValue() == 0;
      }

      return false;
   }

   /**
    * Helper method for getDataProjectedRowNumber(), to handle "projecting" a number.
    * @param intercept the reference row Number value
    * @param slope the change in Number for each row
    * @param count the number of rows to project forward, from the reference
    * @return the projected data at the specified row.
    */
   private <T extends Number> Number projectNumber(T intercept, T slope, int count) {
      if(intercept instanceof Byte) {
         return Byte.valueOf((byte)(intercept.byteValue() + slope.byteValue() * count));
      }
      else if(intercept instanceof Short) {
         return Short.valueOf((short)(intercept.shortValue() + slope.shortValue() * count));
      }
      else if(intercept instanceof Integer) {
         return Integer.valueOf((intercept.intValue() + slope.intValue() * count));
      }
      else if(intercept instanceof Long) {
         return Long.valueOf((intercept.longValue() + slope.longValue() * count));
      }
      else if(intercept instanceof Double) {
         return Double.valueOf((intercept.doubleValue() + slope.doubleValue() * count));
      }
      else if(intercept instanceof Float) {
         return Float.valueOf((intercept.floatValue() + slope.floatValue() * count));
      }

      return null;
   }

   // @by: ChrisSpagnoli feature1379102629417 2015-1-10
   // This method projects a X-axis made up of any Number forward
   /**
    * Return the data at the specified cell.
    * @param col the specified column index.
    * @param row the specified row index.
    * @return the data at the specified cell.
    */
   private <T extends Number> Number getDataProjectedRowNumber(T obj, int col, int row) {
      if(cacheNumber == null) {
         cacheNumber = new Object2ObjectOpenHashMap<>();
      }

      CacheNumber cn = cacheNumber.get(col);

      if(cn == null) {
         cn = new CacheNumber();
         cacheNumber.put(col, cn);
      }

      if(row >= getRowsProjectedForward() + getRowCountUnprojected() ||
         getRowCountUnprojected() < 2 || col >= getColCount())
      {
         return null;
      }

      // @by ChrisSpagnoli bug1429507986738 2015-4-24
      if(cn.failFlag) {
         return getDataProjectedRowNumberFromMySubs(obj, col, row);
      }

      if(cn.slope == null && cn.first == null) {
         Set series = new HashSet();

         for(int j = 0; j < getRowCountUnprojected(); j++) {
            Object oval = getData0(col, j);

            // @by ChrisSpagnoli bug1428396646039 2015-4-7
            if(oval == null || obj == null || !oval.getClass().equals(obj.getClass())) {
               return null;
            }

            T num = (T) oval;
            series.add(oval);

            if(j == 0) {
               cn.first = num;
            }
            // @by ChrisSpagnoli bug1428396209447 2015-4-7
            else if(cn.slope == null || isZeroNumber(cn.slope)) {
               cn.slope = diffNumbers(num, cn.first);
            }
            // @by ChrisSpagnoli bug1427769771644 2015-4-2
            else if(!isZeroNumber(diffNumbers(cn.last, num))) {
               final Number n2 = projectNumber(cn.last, cn.slope, 1);

               if(!isZeroNumber(diffNumbers(n2, num))) {
                  if(subDataSets.isEmpty() && isRepeatingSeries(col, j, series)) {
                     // if repeating series, this row should be the start of the next
                     // series, and we can break out here and the cn.last should already
                     // contain the last value of the previous serie. (51315, 52127)
                     break;
                  }
                  // if the gaps between points are different, use the min gap. see
                  // getDataProjectedDate. (51059)
                  else if(subDataSets.isEmpty()) {
                     Number slope2 = diffNumbers(num, cn.last);
                     cn.slope = cn.slope.doubleValue() < slope2.doubleValue() ? cn.slope : slope2;

                     if(cn.slope == null) {
                        cn.failFlag = true;
                     }
                  }
                  else {
                     cn.failFlag = true;
                     // @by ChrisSpagnoli bug1429507986738 2015-4-24
                     return getDataProjectedRowNumberFromMySubs(obj, col, row);
                  }
               }
            }

            cn.last = num;
         }
      }

      if(cn.slope != null) {
         return projectNumber(cn.last, cn.slope, row - (getRowCountUnprojected() - 1));
      }

      return cn.last;
   }

   // @by ChrisSpagnoli bug1429507986738 2015-4-24
   // This method is executed by the PARENT DataSet, and invokes the
   // getDataProjectedRowNumber() method in each CHILD DataSet.
   <T extends Number> Number getDataProjectedRowNumberFromMySubs(T obj, int col, int row) {
      HashMap<Number,Integer> projectedNumbers = new HashMap<>();

      // Create HashMap counting the projected dates
      try {
         subDataSetsLock.readLock().lock();

         if(subDataSets != null) {
            for(AbstractDataSet sub : subDataSets) {
               final int rowDelta = row - getRowCountUnprojected();
               Number n = sub.getDataProjectedRowNumberFromOneSub(obj, col, rowDelta);

               if(n != null) {
                  int nextVal = 1;

                  if(projectedNumbers.containsKey(n)) {
                     nextVal = projectedNumbers.get(n);
                     nextVal++;
                  }

                  projectedNumbers.put(n, nextVal);
               }
            }
         }
      }
      finally {
         subDataSetsLock.readLock().unlock();
      }

      if(!projectedNumbers.isEmpty()) {
         // Figure out what the max count of projected numbers was
         int maxCount = -1;

         for(Integer value : projectedNumbers.values()) {
            maxCount = Math.max(maxCount, value);
         }

         // Pick the projected number which was the most common
         Number commonNumber = null;

         for(Map.Entry<Number, Integer> entry : projectedNumbers.entrySet()) {
            Number key = entry.getKey();
            Integer value = entry.getValue();

            if(value == maxCount) {
               commonNumber = key;
               break;
            }
         }

         // @by ChrisSpagnoli bug1430357109900 2015-5-6
         // Keep subDataSets which did not perform, for setRowsProjectedForward()
         return commonNumber;
      }

      return null;
   }

   // @by ChrisSpagnoli bug1429507986738 2015-4-24
   // This method is executed in the CHILD data set, and is a wrapper to convert
   //  the "projected" row from parent to the matching "projected" row in child.
   /**
    * Invoke this "child" SubDataSet from the "parent" DataSet
    * @hidden
    */
   public <T extends Number> Number getDataProjectedRowNumberFromOneSub(
      T obj, int col, int rowDelta)
   {
      // @by ChrisSpagnoli bug1429507986738.reopen#2 2015-4-29
      // Discard child "projections" from absurdly small data sets
      if(getRowCountUnprojected() < 3) {
         return null;
      }

      final int row = getRowCountUnprojected() + rowDelta;
      return getDataProjectedRowNumber(obj, col, row);
   }

   // check if the remaining rows in this dataset is repeating the same values in series.
   private boolean isRepeatingSeries(int col, int row, Set series) {
      // last row, no rows to repeat (50645)
      if(row == getRowCount0() - 1) {
         return false;
      }

      // ignore fill time series. (53892).
      for(int i = row; i < getRowCountUnprojected0(); i++) {
         if(!series.contains(getData(col, i))) {
            return false;
         }
      }

      return true;
   }

   /**
    * Get the union of all sub-datasets which are created during facet coord processing.
    */
   @TernMethod
   public DataSet getFullProjectedDataSet() {
      subDataSetsLock.readLock().lock();

      try {
         List<AbstractDataSet> subDataSets = this.subDataSets;
         return subDataSets != null && subDataSets.size() > 1
            ? new FullProjectedDataSet(new ArrayList<>(subDataSets))
            : new FullProjectedDataSet(this);
      }
      finally {
         subDataSetsLock.readLock().unlock();
      }
   }

   private String getHeadersStr() {
      return IntStream.range(0, getColCount())
         .mapToObj(this::getHeader)
         .collect(Collectors.joining(","));
   }

   @Override
   public void dispose() {
      disposed = true;
   }

   @Override
   public boolean isDisposed() {
      return disposed;
   }

   /**
    * Get the inner dimension used for prepareCalc.
    * @hidden
    */
   protected String getInnerDim() {
      return innerDim;
   }

   private List<CalcColumn> calcs = null;
   private List<CalcRow> rcalcs = null;
   // calculated columns (appended to rows)
   private Vector<Object[]> calcvals;
   // calculated rows (appended to dataset)
   private List<Object[]> rcalcvals;
   // true if contains valid calc value
   private Set<String> validCalcs = new HashSet<>();
   private String innerDim;

   protected int rowsProjectedForward = 0;
   private String projectColumn;

   // Cache map for getDataProjectedRowDate()
   private class CacheDate {
      private Date first = null;
      private Date last = null;
      private Map<Integer, Long> slope = null;
      private boolean failFlag = false;
   }

   private transient Map<Integer,CacheDate> cacheDate;

   // Cache map for getDataProjectedRowNumber()
   private class CacheNumber {
      private Number first = null;
      private Number last = null;
      private Number slope = null;
      private boolean failFlag = false;
   }

   private static final int DIFF = -1;
   private final ReentrantReadWriteLock subDataSetsLock = new ReentrantReadWriteLock();
   private List<AbstractDataSet> subDataSets = new ArrayList<>(0);
   private List<Map> projectedValues = null;
   private transient Map<Integer, CacheNumber> cacheNumber;
   private transient Object2IntOpenHashMap<String> idxmap = new Object2IntOpenHashMap<>();
   private boolean disposed = false;
   private transient Boolean projected;
   private transient long dataHash = Long.MIN_VALUE;

   private static final Logger LOG = LoggerFactory.getLogger(AbstractDataSet.class);
}
