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

import inetsoft.graph.internal.GDefaults;
import inetsoft.mv.data.BitSet;
import inetsoft.uql.viewsheet.graph.ChartAggregateRef;
import inetsoft.util.CoreTool;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.roaringbitmap.IntIterator;

import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class create an index on a dataset that allows quick retrieve of
 * sub sets. This is used in internal processing.
 *
 * @hidden
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class DataSetIndex {
   /**
    * Create an index.
    * @param keepCalc true to keep the calc rows in the base dataset.
    */
   public DataSetIndex(DataSet dset, boolean keepCalc) {
      this.dset = dset.clone(true);
      this.keepCalc = keepCalc;

      if(!keepCalc) {
         this.dset.removeCalcRowValues();
      }
   }

   /**
    * Create an index.
    * @param keys the index column names.
    * @param keepCalc true to keep the calc rows in the base dataset.
    */
   public DataSetIndex(DataSet dset, Collection<String> keys, boolean keepCalc) {
      this(dset, keepCalc);
      addIndices(keys);
   }

   /**
    * Get the data set.
    */
   public DataSet getDataSet() {
      return dset;
   }

   /**
    * Add indices for the specified columns.
    */
   public void addIndices(Collection<String> cols) {
      for(String col : cols) {
         addIndex(col);
      }

      if(cols.isEmpty()) {
         addEmptyIndex();
      }
   }

   /**
    * Add index for the specified column.
    */
   public void addIndex(String col) {
      if(imap.containsKey(col)) {
         return;
      }

      int index = findDataColumn(dset, col);

      if(index >= 0) {
         imap.put(col, createIndexMap(dset, index));
      }
   }

   // this finds a column in data set. if the column is a discrete measure, and an exact match
   // is not found, the base (without discrete prefix) column is used. this is because in
   // certain situations (nested discrete measure binding), the DiscreteColumn added in
   // VSDataSet may be stripped in wrapDataSet(). given the complex interaction of different
   // calc columns (e.g. discrete, sum in waterfaul, ...), we may need a new mechanism to
   // selectively keep/carry calc columns to avoid this special logic. (59531)
   private static int findDataColumn(DataSet dset, String col) {
      int index = dset.indexOfHeader(col);

      if(index < 0) {
         index = dset.indexOfHeader(ChartAggregateRef.getBaseName(col));
      }
      return index;
   }

   private void addEmptyIndex() {
      if(emptyDset == null) {
         DataSet dset = (DataSet) this.dset.clone();
         TopDataSet gdata = null;

         if(dset instanceof TopDataSet) {
            gdata = (TopDataSet) dset;
            dset = gdata.getDataSet();
         }

         emptyDset = wrapDataSet(new SubDataSet((DataSet) dset.clone(), new HashMap<>()), gdata);
      }
   }

   /**
    * Get all index columns.
    */
   public Collection<String> getIndices() {
      return imap.keySet();
   }

   /**
    * Create a subset.
    * @param conds this map specifies the subset condition.
    */
   public DataSet createSubDataSet(Map<String, Object> conds, boolean innermost) {
      BitSet rows = null;
      DataSet dset = this.dset.clone(true);
      TopDataSet gdata = null;

      if(dset instanceof TopDataSet) {
         gdata = (TopDataSet) dset;
         dset = gdata.getDataSet();
      }

      for(String col : conds.keySet()) {
         Object val = conds.get(col);
         Map<Object, BitSet> idx = imap.get(col);

         // if index missing, use the default filtering
         if(idx == null) {
            return wrapDataSet(new SubDataSet(dset, conds), gdata);
         }

         // discrete_measure column may not exist in data set, just use regular value. (57922)
         Class<?> cls = dset.getType(ChartAggregateRef.getBaseName(col));
         final Collection<Object> vals;

         if(val instanceof Object[]) {
            vals = Arrays.stream((Object[]) val).map(v -> {
               try {
                  return normalize(v, cls);
               }
               catch(Exception ex) {
                  // column may be missing if it's a calc column since it's removed from
                  // the base and added to sub. (53378)
               }

               return v;
            }).collect(Collectors.toList());
         }
         else {
            try {
               val = normalize(val, cls);
            }
            catch(Exception ex) {
               // column may be missing if it's a calc column since it's removed from
               // the base and added to sub. (53378)
            }

            vals = Collections.singleton(val);
         }

         BitSet condSet = new BitSet();

         for(Object v : vals) {
            BitSet valSet = idx.get(v);

            // if not found, same as empty set. just ignore since it's or'ed.
            if(valSet == null) {
               continue;
            }

            condSet = condSet.or(valSet);
         }

         if(rows == null) {
            rows = condSet;
         }
         else {
            rows = rows.and(condSet);
         }
      }

      if(conds.isEmpty()) {
         return wrapDataSet(emptyDset, gdata);
      }

      // @by ChrisSpagnoli bug1429507986738 2015-4-24
      // Save reference to the child subDatSet for later tend line projection
      DataSet dset2 = createSubDataSet(dset, rows, gdata, innermost, conds);

      if(innermost && dset instanceof AbstractDataSet) {
         ((AbstractDataSet) dset).addSubDataSet(dset2);
      }

      return dset2;
   }

   /**
    * Create a subset consists of rows from the intersection of this dataset
    * and the other dataset.
    */
   public DataSet createSubDataSet(DataSetIndex dindex) {
      BitSet rows = new BitSet();
      DataSet dset = (DataSet) this.dset.clone();
      TopDataSet gdata = null;
      // @by ChrisSpagnoli bug1429507986738 2015-4-24
      // Use getRowCountUnprojected(), if possible.
      int rowCount = dset.getRowCount();

      if(dset instanceof AbstractDataSet) {
         rowCount = ((AbstractDataSet) dset).getRowCountUnprojected();
      }

      for(int i = 0; i < rowCount; i++) {
         rows.set(i);
      }

      if(dset instanceof TopDataSet) {
         gdata = (TopDataSet) dset;
         dset = gdata.getDataSet();
      }

      for(String col : imap.keySet()) {
         Map<Object, BitSet> rmap = imap.get(col);
         Map<Object, BitSet> rmap2 = dindex.imap.get(col);

         if(rmap2 == null) {
            continue;
         }

         for(Object key : rmap.keySet()) {
            BitSet set1 = rmap.get(key);
            BitSet set2 = rmap2.get(key);

            if(set2 == null) {
               rows = rows.andNot(set1);
            }
         }
      }

      return createSubDataSet(dset, rows, gdata, true, null);
   }

   /**
    * Wrap data set as TopDataSet if the original data set is a top data set.
    */
   private DataSet createSubDataSet(DataSet dset, BitSet rows, TopDataSet gdata, boolean innermost,
                                    Map<String, Object> conds)
   {
      int[] mapping = new int[rows.rowCount()];
      IntIterator iter = rows.intIterator();

      for(int k = 0; iter.hasNext(); k++) {
         mapping[k] = iter.next();
      }

      SubDataSet sub = new SubDataSet(dset, mapping);
      sub.setInnermost(innermost);
      sub.setConditions(conds);
      return wrapDataSet(sub, gdata);
   }

   /**
    * Wrap geo data set.
    */
   private DataSet wrapDataSet(DataSet data, TopDataSet gdata) {
      SubDataSet sub = data instanceof SubDataSet ? (SubDataSet) data : null;

      if(gdata != null) {
         data = gdata.wrap(data, gdata);
      }

      if(data instanceof TopDataSet && ((TopDataSet) data).isCalcInSub()) {
         data.removeCalcColumns();
      }

      for(CalcColumn calc : dset.getCalcColumns()) {
         if(data instanceof TopDataSet && ((TopDataSet) data).isCalcInSub() && sub != null) {
            sub.addCalcColumn(calc);
         }
         else {
            data.addCalcColumn(calc);
         }
      }

      for(CalcRow calc : dset.getCalcRows()) {
         data.addCalcRow(calc);
      }

      if(sub != null && !keepCalc) {
         sub.getDataSet().removeCalcValues();
      }

      return data;
   }

   /**
    * Normalize a value.
    */
   private Object normalize(Object obj, Class<?> dtype) {
      if(obj == null || dtype == null) {
         return obj;
      }

      if(obj == GDefaults.NULL_STR) {
         return null;
      }

      if(Number.class.isAssignableFrom(dtype)) {
         dtype = Double.class;
      }
      else if(java.util.Date.class.isAssignableFrom(dtype)) {
         dtype = Timestamp.class;
      }

      Object val = CoreTool.getData(dtype, obj);
      // if string but somehow marked as double, return string instead of null (48166).
      return val == null ? obj : val;
   }

   public void clearSubDataSetDuplicates(DataSet newDataSet) {
      ((AbstractDataSet) this.dset).clearSubDataSetDuplicates((AbstractDataSet) newDataSet);
   }

   /**
    * Create an index map, from value to row BitSet.
    */
   private Map<Object, BitSet> createIndexMap(DataSet dset, int colIdx) {
      Map<Object, BitSet> map = new HashMap<>(); // value -> BitSet
      Class<?> cls = dset.getType(dset.getHeader(colIdx));
      // @by ChrisSpagnoli bug1429507986738 2015-4-24
      // Use getRowCountUnprojected(), if possible.
      int rowCount = dset.getRowCount();

      if(dset instanceof AbstractDataSet) {
         rowCount = ((AbstractDataSet)dset).getRowCountUnprojected();
      }

      for(int i = 0; i < rowCount; i++) {
         Object v = dset.getData(colIdx, i);
         v = normalize(v, cls);
         BitSet bits = map.get(v);

         if(bits == null) {
            map.put(v, bits = new BitSet());
         }

         bits.set(i);
      }

      return map;
   }

   /**
    * Get the max count for this dimension in a facet.
    */
   public Integer getXMaxCount(Object vmap) {
      return xMaxCounts.get(vmap);
   }

   /**
    * Set the max count for this dimension in a facet.
    * @param vmap the key containing values in the outer dimension for this row/column.
    * @param count the max count of the scale.
    */
   public void setXMaxCount(Object vmap, int count) {
      xMaxCounts.put(vmap, count);
   }

   /**
    * Get the max count for this dimension in a facet.
    */
   public Integer getYMaxCount(Object vmap) {
      return yMaxCounts.get(vmap);
   }

   /**
    * Set the max count for this dimension in a facet.
    * @param vmap the key containing values in the outer dimension for this row/column.
    * @param count the max count of the scale.
    */
   public void setYMaxCount(Object vmap, int count) {
      yMaxCounts.put(vmap, count);
   }

   private final Map<String, Map<Object, BitSet>> imap = new Object2ObjectOpenHashMap<>();
   private final Map<Object, Integer> xMaxCounts = new Object2ObjectOpenHashMap<>();
   private final Map<Object, Integer> yMaxCounts = new Object2ObjectOpenHashMap<>();
   private final DataSet dset;
   private DataSet emptyDset = null;
   private final boolean keepCalc;
}
