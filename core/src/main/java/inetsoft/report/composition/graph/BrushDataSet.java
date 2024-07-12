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
package inetsoft.report.composition.graph;

import inetsoft.graph.EGraph;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.data.*;
import inetsoft.graph.visual.ElementVO;
import inetsoft.report.composition.graph.calc.AbstractColumn;
import inetsoft.report.composition.graph.calc.PercentColumn;
import inetsoft.uql.viewsheet.graph.ChartAggregateRef;
import inetsoft.util.CoreTool;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.Format;
import java.util.*;

/**
 * Brush, the data set for brush in viewsheet.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class BrushDataSet extends AbstractDataSetFilter {
   /**
    * All header in brush.
    */
   public static final String ALL_HEADER_PREFIX = ElementVO.ALL_PREFIX;

   /**
    * Create a brush data set.
    * @param adata the specified data set without brushing.
    * @param data the specified data set with brushing.
    */
   public BrushDataSet(DataSet adata, DataSet data) {
      this(adata, data, null);
   }

   /**
    * Create a brush data set.
    * @param adata the specified data set without brushing.
    * @param data the specified data set with brushing.
    * @param mflags measure flags.
    */
   public BrushDataSet(DataSet adata, DataSet data, boolean[] mflags) {
      super(data);

      List<CalcColumn> calcs = data.getCalcColumns();
      List<CalcColumn> acalcs = adata.getCalcColumns();
      // Because calc values will be removed from base DataSet, so here we
      // should maintain the calc row correct, otherwise calc row values
      // will be lost
      // fix bug1361476647414
      List<CalcRow> rcalcs = data.getCalcRows();
      List dlist = new ArrayList();
      List mlist = new ArrayList();
      List<String> dims = new ArrayList<>();

      // remove all created calc values, they are invalid now
      data.removeCalcValues();
      adata.removeCalcValues();
      this.bcol = data.getColCount();

      if(mflags == null) {
         this.mflags = new boolean[bcol];

         for(int i = 0; i < bcol; i++) {
            String header = data.getHeader(i);
            this.mflags[i] = data.isMeasure(header) &&
               !header.startsWith(ChartAggregateRef.DISCRETE_PREFIX);
         }
      }
      else {
         this.mflags = mflags;
      }

      // @by davyc, when brush, the brush data set is less than all data, this
      // may cause the data type changed for number column, like from double
      // to long, see XBDDoubleColumn, this will cause brush chart paint wrong,
      // here add logic to fix the problem
      convert = new int[bcol];

      for(int i = 0; i < bcol; i++) {
         String header = data.getHeader(i);

         if(!this.mflags[i]) {
            dlist.add(i);

            // don't use discrete measure to get subdataset of it will always be empty.
            if(!header.startsWith(ChartAggregateRef.DISCRETE_PREFIX)) {
               dims.add(header);
            }

            Class btype = data.getType(header);
            Class atype = adata.getType(header);

            if(Number.class.isAssignableFrom(btype) &&
               Number.class.isAssignableFrom(atype) &&
               btype != atype)
            {
               // all data data type must assignable from brush data type
               convert[i] = getConvertType(atype);
            }
         }
         else {
            mlist.add(i);
         }
      }

      String[] cols = dims.toArray(new String[0]);
      MergedDataSetIndex dindex1 = new MergedDataSetIndex(adata, cols);
      MergedDataSetIndex dindex2 = new MergedDataSetIndex(data, cols);

      // @by larryl, create an intersect of the all-data and brushed data so the
      // brushed data doesn't include any rows that's not in all-data. This can
      // happen if topN is defined on a dimension and brushing causes a
      // different group to be included in the top-N list.
      data = dindex2.createSubDataSet(dindex1);

      this.adata = adata;
      this.data = data;
      this.brow = data.getRowCount();
      this.arow = adata.getRowCount();

      this.dims = toIntArray(dlist);
      this.measures = toIntArray(mlist);
      this.aheaders = new String[measures.length];

      for(int i = 0; i < measures.length; i++) {
         aheaders[i] = ALL_HEADER_PREFIX + data.getHeader(measures[i]);
      }

      // add brush data's calc columns, for all calc column type except
      // PercentColumn direct use it, but PercentColumn need to clone it,
      // to prevent change the base CalcColumn, because for bursh data, its
      // total column should use all header
      for(int i = 0; i < calcs.size(); i++) {
         if(calcs.get(i) instanceof PercentColumn) {
            PercentColumn calc = (PercentColumn) calcs.get(i);
            calc = (PercentColumn) calc.clone();
            calc.setTotalField(ALL_HEADER_PREFIX + calc.getField());
            addCalcColumn(calc);
         }
         else {
            addCalcColumn(calcs.get(i));
         }
      }

      // add all data's calc column, for all calc column, it will be changed
      // header and field to all, so need clone it to prevent change base calc
      for(int i = 0; i < acalcs.size(); i++) {
         if(acalcs.get(i) instanceof AbstractColumn) {
            AbstractColumn calc = (AbstractColumn) acalcs.get(i);
            calc = (AbstractColumn)  calc.clone();
            calc.setHeader(ALL_HEADER_PREFIX + calc.getHeader());
            calc.setField(ALL_HEADER_PREFIX + calc.getField());
            addCalcColumn(calc);
         }
         else {
            addCalcColumn(acalcs.get(i));
         }
      }

      TimeSeriesRow.MeasureChecker mchecker = null;

      for(int i = 0; i < rcalcs.size(); i++) {
         CalcRow crow = rcalcs.get(i);

         if(crow instanceof TimeSeriesRow) {
            if(mchecker == null) {
               createDataKeys(data);
               // for time series, data's dimension values must be in adata's
               // dimension values, so create time series values in data and
               // adata is equal to create time series values in brush data set,
               // BUT, for measure values, if the measure is not an all header
               // measure(which means not __all__xxx), we should only create
               // missing time gap measure values for those dimensions which is
               // in data's dimension values, this is why we create data keys
               // and measure checker here
               mchecker = new MeasureCheckerImpl();
            }

            ((TimeSeriesRow) crow).setMeasureChecker(mchecker);
         }

         addCalcRow(crow);
      }

      if(calcs.size() > 0 || acalcs.size() > 0 || rcalcs.size() > 0) {
         prepareCalc(null, null, true);
      }
   }

   @Override
   public void prepareGraph(EGraph graph, Coordinate coord, DataSet dataset) {
      // need to populate ScaleRouter with full data or calc won't work property. (52111)
      data.prepareGraph(graph, coord, adata);
      adata.prepareGraph(graph, coord, null);
   }

   /**
    * Set whether to only draw brushed data and not the all-data data points.
    */
   public void setBrushedDataOnly(boolean brushedOnly) {
      this.brushedDataOnly = brushedOnly;

      if(brushedOnly && data.getRowCount() > 0) {
         arow = 0;
      }
      else {
         arow = adata.getRowCount();
      }
   }

   /**
   * Check if only brushed data is shown.
   */
   public boolean isBrushedDataOnly() {
      return brushedDataOnly;
   }

   /**
    * Check if the brushed data is empty.
    */
   public boolean isBrushedDataEmpty() {
      return data.getRowCount() == 0;
   }

   private int getConvertType(Class type) {
      if(type == Byte.class) {
         return 1;
      }
      else if(type == Short.class) {
         return 2;
      }
      else if(type == Integer.class) {
         return 3;
      }
      else if(type == Long.class) {
         return 4;
      }
      else if(type == Float.class) {
         return 5;
      }

      return 6;
   }

   private Object convertNumber(int type, Number obj) {
      if(type == 6) {
         return obj.doubleValue();
      }
      else if(type == 5) {
         return obj.floatValue();
      }
      else if(type == 4) {
         return obj.longValue();
      }
      else if(type == 3) {
         return obj.intValue();
      }
      else if(type == 2) {
         return obj.shortValue();
      }
      else if(type == 1) {
         return obj.byteValue();
      }

      return obj;
   }

   /**
    * Get the calculated columns.
    */
   @Override
   public List<CalcColumn> getCalcColumns() {
      // we just only need self's calc column, brush data and all data's calc
      // columns are all added to BrushDataSet, and they are invalid now
      return getCalcColumns(true);
   }

   /**
    * Create int array.
    */
   private int[] toIntArray(List list) {
      int[] arr = new int[list.size()];

      for(int i = 0; i < arr.length; i++) {
         arr[i] = ((Integer) list.get(i)).intValue();
      }

      return arr;
   }

   /**
    * Check is header without brushing.
    */
   private boolean isAllHeader(String col) {
      return col != null && col.indexOf(ALL_HEADER_PREFIX) >= 0;
   }

   /**
    * Get the index mapping from base data(not all data) set to brush data set.
    */
   public int getCurrentRowFromBaseRow(int brow) {
      return omapping == null || brow < 0 || brow >= omapping.length ?
         brow : omapping[brow];
   }

   /**
    * Get the base data set.
    * @return the base data set.
    */
   @Override
   public DataSet getDataSet() {
      return getDataSet(false);
   }

   /**
    * Get the base data set.
    * @param all true to get the base data without filtering, false to
    * get the base data with filtering.
    * @return the base data set.
    */
   public DataSet getDataSet(boolean all) {
      return all ? adata : data;
   }

   /**
    * Set the order of values in the column. This can be used in javascript
    * to set the explicit ordering of values.
    */
   public void setOrder(String col, Object[] list) {
      VSDataSet dataset = findVSDataSet(adata);

      if(dataset != null) {
         dataset.setOrder(col, list);
      }

      dataset = findVSDataSet(data);

      if(dataset != null) {
         dataset.setOrder(col, list);
      }
   }

   /**
    * Find the VSDataSet if any.
    */
   private VSDataSet findVSDataSet(DataSet dataset) {
      if(dataset instanceof VSDataSet) {
         return (VSDataSet) dataset;
      }

      if(!(dataset instanceof DataSetFilter)) {
         return null;
      }

      dataset = ((DataSetFilter) dataset).getDataSet();
      return findVSDataSet(dataset);
   }

   /**
    * Get the base row index on its base data set of the specified row.
    * @param r the specified row index.
    * @return the base row index on base data set, -1 if no base row.
    */
   @Override
   public int getBaseRow(int r) {
      return r < brow ? r : -1;
   }

   @Override
   public int getRootRow(int r) {
      // the default implementation return -1 for adata rows, causing getEndRow() to
      // return only the brushed data rows and resulting in wrong stack range. (59409)
      return r;
   }

   /**
    * Get the base column index on its base data set of the specified column.
    * @param c the specified column index.
    * @return the base column index on base data set, -1 if no base column.
    */
   @Override
   public int getBaseCol(int c) {
      return c < bcol ? c : -1;
   }

   /**
    * Get the base row count.
    */
   public int getBaseRowCount() {
      return brow;
   }

   /**
    * Return the data at the specified cell.
    * @param col the specified column index.
    * @param row the specified row index.
    * @return the data at the specified cell.
    */
   @Override
   protected Object getData0(int col, int row) {
      // base data
      if(row < brow) {
         if(col < bcol) {
            Object obj = data.getData(col, row);
            return convert[col] == 0 || !(obj instanceof Number)  ?
               obj : convertNumber(convert[col], (Number) obj);
         }

         return null;
      }

      // all data and base col?
      if(col < bcol) {
         // for rows in adata range, if current row dimension is in data's
         // dimension values, we should use its original value instead of
         // using null, otherwise time series line will be broken, because
         // missing values is in table's last rows, use original value seems
         // will not cause problem, just paint two overlapped shapes
         return mflags[col] && (dataKeys == null ||
            !dataKeys.contains(createKey(adata, row - brow))) ?
            null : adata.getData(col, row - brow);
      }
      // all data and additional col?
      else {
         return adata.getData(measures[col - bcol], row - brow);
      }
   }

   /**
    * Return the column header at the specified column.
    * @param col the specified column index.
    * @return the column header at the specified column.
    */
   @Override
   protected String getHeader0(int col) {
      if(col < bcol) {
         return data.getHeader(col);
      }

      return aheaders[col - bcol];
   }

   /**
    * Get the data type of the column.
    */
   @Override
   protected Class getType0(String col) {
      if(!isAllHeader(col)) {
         return adata.getType(col);
      }

      int length = ALL_HEADER_PREFIX.length();
      col = col.substring(length);
      return adata.getType(col);
   }

   @Override
   protected int indexOfHeader0(String col, boolean all) {
      if(!isAllHeader(col)) {
         if(data instanceof AbstractDataSet) {
            return ((AbstractDataSet) data).indexOfHeader(col, all);
         }

         return data.indexOfHeader(col);
      }

      for(int i = 0; i < aheaders.length; i++) {
         if(CoreTool.equals(aheaders[i], col)) {
            return i + bcol;
         }
      }

      return -1;
   }

   /**
    * Get the comparer to sort data at the specified column.
    * @param col the specified column.
    * @return the comparer to sort data at the specified column.
    */
   @Override
   protected Comparator getComparator0(String col) {
      if(!isAllHeader(col)) {
         Comparator comp = data.getComparator(col);
         int idx = adata.indexOfHeader(col);

         // if using original order, make sure the brushing doesn't change the order. (60087)
         return comp == null && idx >= 0 ? new OriginalOrder(adata, idx) : comp;
      }

      int length = ALL_HEADER_PREFIX.length();
      col = col.substring(length);
      return adata.getComparator(col);
   }

   /**
    * Check if the column is measure.
    * @param col the specified column name.
    * @return <tt>true</true> if is measure, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isMeasure0(String col) {
      if(!isAllHeader(col)) {
         return data.isMeasure(col);
      }

      return true;
   }

   /**
    * Return the number of rows in the chart lens.
    * @return number of rows in the chart lens.
    */
   @Override
   protected int getRowCount0() {
      return brow + arow;
   }

   @Override
   protected int getRowCountUnprojected0() {
      return getRowCount0();
   }

   /**
    * Return the number of columns in the chart lens.
    * @return number of columns in the chart lens.
    */
   @Override
   protected int getColCount0() {
      return dims.length + 2 * measures.length;
   }

   @Override
   public Format getFormat(String col, int row) {
      int cidx = indexOfHeader(col);
      return cidx >= 0 ? getFormat(cidx, row) : null;
   }

   /**
    * Get the per cell format.
    * @param row row number.
    * @param col column number.
    * @return format for the specified cell.
    */
   @Override
   public Format getFormat(int col, int row) {
      // handle percent calc. (61388)
      if(col >= getColCount0()) {
         int calcIdx = col - getColCount0();
         List<CalcColumn> calcCols = getCalcColumns(true);

         if(calcIdx < calcCols.size()) {
            CalcColumn calcCol = calcCols.get(calcIdx);

            if(calcCol instanceof PercentColumn) {
               return DecimalFormat.getPercentInstance();
            }
         }
      }

      col = convertToField(col);

      // base data
      if(row < brow) {
         if(!(data instanceof AttributeDataSet)) {
            return null;
         }

         return col < bcol ? ((AttributeDataSet) data).getFormat(col, row) : null;
      }

      if(!(adata instanceof AttributeDataSet)) {
         return null;
      }

      // all data and base col?
      if(col < bcol) {
         return mflags[col] ? null : ((AttributeDataSet) adata).getFormat(col, row - brow);
      }
      // all data and additional col?
      else {
         return ((AttributeDataSet) adata).getFormat(measures[col - bcol], row - brow);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Object clone() {
      BrushDataSet obj = (BrushDataSet) super.clone();

      obj.data = (DataSet) this.data.clone();
      obj.adata = (DataSet) this.adata.clone();

      return obj;
   }

   /**
    * Tuple stores object array.
    */
   private static class Tuple implements Serializable {
      public Tuple(Object[] arr) {
         this.arr = arr;
      }

      public boolean equals(Object obj) {
         if(!(obj instanceof Tuple)) {
            return false;
         }

         Tuple tuple = (Tuple) obj;

         if(arr.length != tuple.arr.length) {
            return false;
         }

         for(int i = 0; i < arr.length; i++) {
            if(arr[i] == null) {
               if(tuple.arr[i] != null) {
                  return false;
               }
            }
            else if(!arr[i].equals(tuple.arr[i])) {
               return false;
            }
         }

         return true;
      }

      public int hashCode() {
         if(hash != 0) {
            return hash;
         }

         for(int i = 0; i < arr.length; i++) {
            if(arr[i] == null) {
               hash += 7;
            }
            else {
               hash += arr[i].hashCode();
            }
         }

         return hash;
      }

      @Override
      public String toString() {
         return "Tuple" + Arrays.toString(arr);
      }

      private Object[] arr;
      private int hash;
   }

   /**
    * MergedDataSetIndex, it stores data index for multiple columns, so could
    * create sub data set for multiple columns.
    */
   private class MergedDataSetIndex {
      /**
       * Create an index.
       * @param cols the columns as key.
       */
      public MergedDataSetIndex(DataSet dset, String[] cols) {
         this.dset = dset;
         addIndex(cols);
      }

      /**
       * Add index for the specified columns.
       */
      private void addIndex(String[] cols) {
         int length = cols.length;
         Class[] cls = new Class[length];

         for(int i = 0; i < length; i++) {
            cls[i] = dset.getType(cols[i]);
         }

         for(int i = 0; i < dset.getRowCount(); i++) {
            Object[] arr = new Object[length];

            for(int j = 0; j < length; j++) {
               Object v = dset.getData(cols[j], i);
               arr[j] = normalize(v, cls[j]);
            }

            Tuple tuple = new Tuple(arr);
            BitSet bits = map.get(tuple);

            if(bits == null) {
               map.put(tuple, bits = new BitSet());
            }

            bits.set(i);
         }
      }

      /**
       * Normalize a value.
       */
      private Object normalize(Object obj, Class dtype) {
         if(obj == null || obj.equals("") || dtype == null) {
            return obj;
         }

         if(Number.class.isAssignableFrom(dtype)) {
            dtype = Double.class;
         }

         return CoreTool.getData(dtype, obj);
      }

      /**
       * Get the data set.
       */
      public DataSet getDataSet() {
         return dset;
      }

      /**
       * Create a subset consists of rows from the intersection of this dataset
       * and the other dataset.
       */
      public DataSet createSubDataSet(MergedDataSetIndex dindex) {
         BitSet rows = new BitSet();
         DataSet dset = this.dset;
         TopDataSet gdata = null;
         rows.set(0, dset.getRowCount());

         if(dset instanceof TopDataSet) {
            gdata = (TopDataSet) dset;
            dset = gdata.getDataSet();
         }

         Map rmap = this.map;
         Map rmap2 = dindex.map;
         boolean needSubSet = false;

         for(Object key : rmap.keySet()) {
            BitSet set1 = (BitSet) rmap.get(key);
            BitSet set2 = (BitSet) rmap2.get(key);

            if(set2 == null) {
               rows.andNot(set1);
               needSubSet = true;
            }
         }

         return needSubSet ? createSubDataSet(dset, rows, gdata) : this.dset;
      }

      /**
       * Wrap data set as GeoDataSet if the original data set is a geo dataset.
       */
      private DataSet createSubDataSet(DataSet dset, BitSet rows, TopDataSet gdata) {
         int[] mapping = new int[rows.cardinality()];
         omapping = new int[dset.getRowCount()];

         for(int i = rows.nextSetBit(0), k = 0; i >= 0;
            i = rows.nextSetBit(i + 1))
         {
            omapping[i] = k;
            mapping[k++] = i;
         }

         DataSet data = new SubDataSet(dset, mapping);
         return wrapDataSet(data, gdata);
      }

      /**
       * Wrap geo data set.
       */
      private DataSet wrapDataSet(DataSet data, TopDataSet gdata) {
         if(gdata != null) {
            data = gdata.wrap(data, gdata);
         }

         return data;
      }

      private Map<Tuple, BitSet> map = new HashMap<>();
      private DataSet dset;
   }

   private void createDataKeys(DataSet dset) {
      dataKeys = new HashSet();

      for(int r = 0; r < dset.getRowCount(); r++) {
         dataKeys.add(createKey(data, r));
      }
   }

   private Object createKey(DataSet data, int r) {
      ArrayList key = new ArrayList();

      for(int i = 0; i < data.getColCount(); i++) {
         String header = data.getHeader(i);

         if(!data.isMeasure(header)) {
            key.add(data.getData(i, r));
         }
      }

      return key;
   }

   /**
    * Internal measure checker.
    */
   private class MeasureCheckerImpl implements TimeSeriesRow.MeasureChecker {
      @Override
      public boolean check(DataSet data, int r, String measure) {
         if(isAllHeader(measure) || r < brow) {
            return true;
         }

         return dataKeys.contains(createKey(data, r));
      }
   }

   private int bcol; // base col count
   private int brow; // base row count
   private int arow; // another base row count
   private int[] dims; // dimension index
   private int[] measures; // measure index
   private boolean[] mflags; // measure flags
   private String[] aheaders; // all headers
   private DataSet data; // data set with brushing
   private DataSet adata; // data set without brushing
   private int[] convert;
   private int[] omapping;
   private Set dataKeys;
   private boolean brushedDataOnly = false;
}
