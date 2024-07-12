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
package inetsoft.graph.data;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class is used to create a boxplot. For each measure column, five new
 * columns are created: min, lower quartile, median, upper quartile, and max.
 * The outliers are left in the measure column.
 * <br>
 * To create a boxplot, create a schema element with BoxPainter, and bind max,
 * upper quartile, median, lower quartile, and min to the schema. Then create
 * a point element and bind it to the measure.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class BoxDataSet extends AbstractDataSet {
   /**
    * The the prefix for the minimum column.
    */
   public static final String MIN_PREFIX = "Min_";
   /**
    * The the prefix for the lower quartile column.
    */
   public static final String Q25_PREFIX = "Q25_";
   /**
    * The the prefix for the median column.
    */
   public static final String MEDIUM_PREFIX = "Medium_";
   /**
    * The the prefix for the upper quartile column.
    */
   public static final String Q75_PREFIX = "Q75_";
   /**
    * The the prefix for the maximum column.
    */
   public static final String MAX_PREFIX = "Max_";
   /**
    * The  prefix for the outlier column.
    */
   public static final String OUTLIER_PREFIX = "Outlier_";

   /**
    * Create a boxplot dataset.
    * @param base the specified base data set.
    * @param dims the dimension to group the data.
    * @param measures the measures to calculate box ranges.
    */
   public BoxDataSet(DataSet base, String[] dims, String[] measures) {
      this.base = base;
      this.dims.addAll(Arrays.asList(dims));
      this.measures = measures;
   }

   /**
    * Get the base dataset.
    */
   public DataSet getDataSet() {
      return base;
   }

   /**
    * Add to the dimension used for grouping by this data set.
    */
   public void addDim(String dim) {
      for(String s : dims) {
         if(Objects.equals(s, dim)) {
            return;
         }
      }

      this.dims.add(dim);

      synchronized(this) {
         this.rows = new ArrayList<>();
         inited1 = inited2 = false;
      }
   }

   /**
    * Add to the measure calculated by this data set.
    */
   public void addVar(String var) {
      for(String s : measures) {
         if(Objects.equals(s, var)) {
            return;
         }
      }

      String[] measures = new String[this.measures.length + 1];
      System.arraycopy(this.measures, 0, measures, 0, this.measures.length);
      measures[measures.length - 1] = var;
      this.measures = measures;

      synchronized(this) {
         this.rows = new ArrayList<>();
         inited1 = inited2 = false;
      }
   }

   /**
    * Get all dimensions.
    */
   public String[] getDims() {
      return dims.toArray(new String[0]);
   }

   /**
    * Get all measures.
    */
   public String[] getVars() {
      return measures;
   }

   private void init1() {
      if(inited1) {
         return;
      }

      synchronized(this) {
         if(!inited1) {
            outliers.clear();
            outlierSet.clear();

            // sort the dims to the same order as the base dataset
            // condition (e.g. brushing) may depend on the BoxDataSet dimensions having same
            // order as the base
            Collections.sort(dims, (a, b) -> {
               int idx1 = base.indexOfHeader(a);
               int idx2 = base.indexOfHeader(b);
               return idx1 - idx2;
            });
            // the columns are: dims, measures, [min,q25,medium,q75,max] for each measure
            headers = new ArrayList<>();
            headers.addAll(dims);
            headers.addAll(Arrays.asList(measures));

            for(int i = 0; i < measures.length; i++) {
               headers.add(MIN_PREFIX + measures[i]);
               headers.add(Q25_PREFIX + measures[i]);
               headers.add(MEDIUM_PREFIX + measures[i]);
               headers.add(Q75_PREFIX + measures[i]);
               headers.add(MAX_PREFIX + measures[i]);
            }

            inited1 = true;
         }
      }
   }

   private void init2() {
      if(inited2) {
         return;
      }

      synchronized(this) {
         if(!inited2) {
            init1();

            int[] dimcols = new int[dims.size()];
            int[] mcols = new int[measures.length];

            for(int i = 0; i < dims.size(); i++) {
               dimcols[i] = base.indexOfHeader(dims.get(i));

               // in case a column is used as measure and dimension. (53660)
               if(dimcols[i] < 0) {
                  dimcols[i] = base.indexOfHeader(getBaseName(dims.get(i)));
               }
            }

            for(int i = 0; i < measures.length; i++) {
               mcols[i] = base.indexOfHeader(measures[i]);
            }

            process(base, dimcols, mcols);
            inited2 = true;
         }
      }
   }

   /**
    * Calculate the quartiles.
    */
   private void process(DataSet base, int[] dimcols, int[] mcols) {
      // dim tuple -> {measure -> measure values in the group}
      Map<List,Map<Integer, FloatList>> grpmap = new Object2ObjectOpenHashMap<>();

      // collect all values for each group
      for(int i = 0; i < base.getRowCount() && !isDisposed(); i++) {
         List grp = getValues(base, i, dimcols);

         if(grpmap.get(grp) == null) {
            grpmap.put(grp, new HashMap<>());
         }

         for(int j = 0; j < mcols.length; j++) {
            int mcol = mcols[j];
            Object val = base.getData(mcol, i);

            if(!(val instanceof Number)) {
               continue;
            }

            FloatList values = grpmap.get(grp).get(mcol);

            if(values == null) {
               values = new FloatArrayList();
               grpmap.get(grp).put(mcol, values);
            }

            values.add(((Number) val).floatValue());
         }
      }

      // sort the values
      for(Map<Integer, FloatList> map : grpmap.values()) {
         for(FloatList values : map.values()) {
            values.sort(null);
         }
      }

      // calculate quartiles for each group
      for(List grp : grpmap.keySet()) {
         List row = new ArrayList();

         row.addAll(grp);

         for(int c = 0; c < mcols.length; c++) {
            int mcol = mcols[c];
            List qs = calculateQuartiles(grpmap.get(grp).get(mcol), grp, c);

            row.addAll(qs);
         }

         rows.add(row);
      }

      outlierSet = new HashSet<>();
   }

   /**
    * Calculate the quartile and return the values as
    * [min, q25, medium, q75, max], add add the outliers to the outliers list.
    * @param values the group values.
    * @param grp the dimension values.
    * @param midx the measure index (in the measure list).
    */
   private List calculateQuartiles(FloatList values, List grp, int midx) {
      List<Number> qs = new ArrayList<>();

      if(values == null || values.size() == 0) {
         qs.add(null); qs.add(null); qs.add(null); qs.add(null); qs.add(null);
         return qs;
      }
      else if(values.size() == 1) {
         Number v = values.get(0);
         qs.add(v); qs.add(v); qs.add(v); qs.add(v); qs.add(v);
         return qs;
      }
      else if(values.size() == 2) {
         Float v1 = values.get(0);
         Float v2 = values.get(1);
         qs.add(v1); qs.add(v1); qs.add((v1 + v2) / 2); qs.add(v2); qs.add(v2);
         return qs;
      }

      double min = values.getFloat(0);
      final int n = values.size() - 1;
      double max = values.getFloat(n);
      double medium = getValue2(values, n * 0.5);
      double q25 = getValue2(values, n * 0.25);
      double q75 = getValue2(values, n * 0.75);
      // inter quartile range
      double iqr = q75 - q25;

      // find the outliers
      if(max > q75 + iqr * 1.5) {
         max = q75 + iqr * 1.5;

         for(int i = values.size() - 1; i >= 0; i--) {
            if(values.getFloat(i) > max) {
               addOutlier(grp, midx, values.get(i));
            }
         }
      }

      if(min < q25 - iqr * 1.5) {
         min = q25 - iqr * 1.5;

         for(int i = 0; i < values.size(); i++) {
            if(values.getFloat(i) < min) {
               addOutlier(grp, midx, values.get(i));
            }
         }
      }

      qs.add(min); qs.add(q25); qs.add(medium); qs.add(q75); qs.add(max);
      return qs;
   }

   /**
    * Add an outlier row.
    */
   private void addOutlier(List grp, int midx, Object mval) {
      List row = new ArrayList();

      row.addAll(grp);

      for(int i = 0; i < midx; i++) {
         row.add(null);
      }

      row.add(mval);

      if(!outlierSet.contains(row)) {
         outliers.add(row);
         outlierSet.add(row);
      }
   }

   /**
    * Get the value at the index if it's an integer, or the average of the
    * two adjacent values if not.
    */
   private double getValue2(FloatList values, double idx) {
      return (idx == Math.ceil(idx))
         ? values.getFloat((int) idx)
         : (values.getFloat((int) Math.floor(idx)) + values.getFloat((int) Math.ceil(idx))) / 2;
   }

   /**
    * Get the values from the row and columns.
    */
   private List getValues(DataSet base, int row, int[] cols) {
      List values = new ArrayList();

      for(int i = 0; i < cols.length; i++) {
         values.add(base.getData(cols[i], row));
      }

      return values;
   }

   /**
    * Return the number of rows in the chart lens.
    */
   @Override
   protected int getRowCount0() {
      init2();
      return rows.size() + outliers.size();
   }

   // placeholder for now, unprojected count would be non-trivial, TBD if needed
   @Override
   protected int getRowCountUnprojected0() {
      return getRowCount0();
   }

   /**
    * Return the number of columns in the data set without the calculated
    * column.
    */
   @Override
   protected int getColCount0() {
      init1();
      return dims.size() + measures.length * 6;
   }

   /**
    * Return the data at the specified cell.
    */
   @Override
   protected Object getData0(int col, int row) {
      init2();

      if(col < dims.size()) {
         if(row < rows.size()) {
            return rows.get(row).get(col);
         }

         List orow = outliers.get(row - rows.size());
         return col < orow.size() ? orow.get(col) : null;
      }

      // quartile data
      if(row < rows.size()) {
         if(col < dims.size() + measures.length) {
            return null;
         }

         col -= measures.length;
         return rows.get(row).get(col);
      }

      row -= rows.size();

      return (col < outliers.get(row).size()) ? outliers.get(row).get(col) : null;
   }

   @Override
   protected int indexOfHeader0(String col, boolean all) {
      init1();
      return headers.indexOf(col);
   }

   /**
    * Return the column header at the specified column.
    * @param col the specified column index.
    */
   @Override
   protected String getHeader0(int col) {
      init1();
      return headers.get(col);
   }

   /**
    * Get the data type of the column.
    */
   @Override
   protected Class getType0(String col) {
      return !isMeasure0(col) ? base.getType(col) : Double.class;
   }

   /**
    * Check if the column is measure.
    * @param col the specified column name.
    */
   @Override
   protected boolean isMeasure0(String col) {
      return indexOfHeader(col) >= dims.size();
   }

   /**
    * Get the comparer to sort data at the specified column.
    * @param col the specified column.
    */
   @Override
   protected Comparator getComparator0(String col) {
      return base.getComparator(col);
   }

   @Override
   public void dispose() {
      if(base != null) {
         base.dispose();
      }

      disposed.set(true);
   }

   @Override
   public boolean isDisposed() {
      boolean disposed = this.disposed.get();

      if(!disposed && base != null) {
         disposed = base.isDisposed();
      }

      return disposed;
   }

   @Override
   public DataSet clone(boolean shallow) {
      BoxDataSet dset = (BoxDataSet) super.clone(shallow);
      dset.inited1 = dset.inited2 = false;
      dset.rows = new ArrayList<>();
      dset.outliers = new ArrayList<>();
      dset.outlierSet = new HashSet<>();
      return dset;
   }

   /**
    * Get column name without prefix.
    */
   public static String getBaseName(String name) {
      if(name == null) {
         return name;
      }

      if(name.startsWith(MAX_PREFIX) ||
         name.startsWith(Q75_PREFIX) ||
         name.startsWith(MEDIUM_PREFIX) ||
         name.startsWith(Q25_PREFIX) ||
         name.startsWith(MIN_PREFIX))
      {
         return name.substring(name.indexOf('_') + 1);
      }

      return name;
   }

   private DataSet base;
   private String[] measures;
   private List<String> dims = new ArrayList<>();
   private List<String> headers = new ArrayList<>();
   private boolean inited1 = false;
   private boolean inited2 = false;
   // rows for quartiles: dim + [min, q25, medium, q75, max]
   private List<List> rows = new ArrayList<>();
   // outliers: dim + measures
   private List<List> outliers = new ArrayList<>();
   private Set<List> outlierSet = new HashSet<>();
   private final AtomicBoolean disposed = new AtomicBoolean();
}
