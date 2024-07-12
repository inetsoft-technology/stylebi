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

import inetsoft.graph.internal.GDefaults;
import inetsoft.report.Hyperlink;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.util.CoreTool;
import inetsoft.util.swap.XIntFragment;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class extracts a subset of tuples from a dataset using simple
 * conditions.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class SubDataSet extends AbstractDataSetFilter {
   /**
    * Create a subset of tuples. The conditions are specified as a map from
    * column names to the column values.
    */
   public SubDataSet(DataSet dset, Map<String, Object> conds) {
      super(dset);
      this.conds = conds;
   }

   private void init() {
      if(inited) {
         return;
      }

      DataSet dset = getDataSet();
      // make sure dset is initialized to avoid deadlock. (51137)
      dset.getColCount();

      synchronized(this) {
         if(inited) {
            return;
         }

         if(mapping == null && mapping2 == null && conds != null) {
            IntList mvec = new IntArrayList();

            for(int i = 0; i < dset.getRowCount() && !isDisposed(); i++) {
               if(match(dset, i, conds)) {
                  mvec.add(i);
               }
            }

            mapping = mvec.toIntArray();
            mapping2 = null;

            if(mapping.length == dset.getRowCount()) {
               mapping = null;
               identical = true;
            }
            else {
               checkMappingSize();
            }
         }

         inited = true;
      }
   }

   private void checkMappingSize() {
      if(mapping != null && mapping.length > 1000) {
         mapping2 = new XIntFragment(mapping);
         mapping2cnt = mapping2.size();
         mapping = null;
      }
   }

   /**
    * Create a subset of tuples.
    * @param start starting row.
    * @param end ending row, non-inclusive;
    */
   public SubDataSet(DataSet dset, int start, int end) {
      super(dset);

      mapping = new int[end - start];

      for(int i = 0; i < mapping.length; i++) {
         mapping[i] = start + i;
      }

      checkMappingSize();
   }

   /**
    * Create a subset of the data.
    * @param mapping the row mapping to the base dataset.
    */
   public SubDataSet(DataSet dset, int[] mapping) {
      super(dset);
      this.mapping = mapping;
      checkMappingSize();
   }

   /**
    * Check if the row matches condition.
    */
   private boolean match(DataSet dset, int r, Map<String, Object> conds) {
      for(String key : conds.keySet()) {
         Object val = conds.get(key);
         Object[] vals = null;
         int col = colIndexes.computeIfAbsent(key, k -> dset.indexOfHeader(k, true));
         Object dval = dset.getData(col, r);
         dval = GDefaults.getValue(dval);

         if(val instanceof Object[]) {
            vals = (Object[]) val;
         }
         else {
            vals = new Object[] {val};
         }

         boolean res = false;

         for(int i = 0; i < vals.length; i++) {
            if(CoreTool.equals(vals[i], dval)) {
               res = true;
               break;
            }
         }

         if(!res) {
            return false;
         }
      }

      return true;
   }

   /* if this sub-dataset applies project forward, the getRowCount0() will return the
   base unprojected row count, so we don't need the following logic anymore. (52361)
   @Override
   public int getRowCount() {
      // if mapping/mapping2 exists, the rows are literal rows without projection applied,
      // so we call super.getRowCount(). otherwise, getRowCount0() calls dset.getRowCount()
      // which will apply the rowsProjectForward, so we should return getRowCountUnprojected().
      // if not, projected rows will be added to the base row count twice. (48394, 49277, 48342)
      return mapping == null && mapping2 == null ? getRowCountUnprojected() : super.getRowCount();
   }
    */

   public void setConditions(Map<String, Object> conds) {
      this.conds = conds;
   }

   public Map<String, Object> getConditions() {
      return conds;
   }

   /**
    * Return the number of rows in the chartLens.
    * @return number of rows in table.
    */
   @Override
   protected int getRowCount0() {
      init();

      if(mapping != null) {
         return mapping.length;
      }
      else if(mapping2 != null) {
         return mapping2.size();
      }

      DataSet dset = getDataSet();
      return getRowsProjectedForward() > 0 && dset instanceof AbstractDataSet
         // if this sub-dataset will apply project forward, don't include the projected
         // rows from the base. (52361)
         ? ((AbstractDataSet) dset).getRowCountUnprojected()
         : dset.getRowCount();
   }

   @Override
   protected int getRowCountUnprojected0() {
      return getRowCount0();
   }

   /**
    * Get the base row index on its base data set of the specified row.
    * @param r the specified row index.
    * @return the base row index on base data set, -1 if no base row.
    */
   @Override
   public int getBaseRow(int r) {
      if(identical || r < 0) {
         return r;
      }

      init();

      if(mapping != null && r < mapping.length) {
         return mapping[r];
      }
      else if(mapping2 != null && r < mapping2cnt) {
         return mapping2.getSafely(r);
      }

      return -1;
   }

   /**
    * Get the row index corresponding to the base row. This does a reverse
    * lookup of the base row index in the mapping, and return a position
    * by binarySearch().
    */
   public int getRowFromBase(int baserow) {
      init();

      int row = baserow;

      if(mapping != null) {
         row = Arrays.binarySearch(mapping, baserow);
      }
      else if(mapping2 != null) {
         row = Arrays.binarySearch(mapping2.getArray(), baserow);
      }
      // if this is passing through, make sure we don't count the calc rows since they
      // are added back in SortedDataSet.sort(). (60820)
      else {
         row = Math.min(row, getRowCount0());
      }

      DataSet dset = getDataSet();

      // since the baserow is from the root dataset, need to deal with nested subset. (50276)
      if(row < 0 && dset instanceof SubDataSet) {
         int row2 = ((SubDataSet) dset).getRowFromBase(baserow);

         if(row2 >= 0 && row2 != baserow) {
            return getRowFromBase(row2);
         }
      }

      return row;
   }

   @Override
   public SubDataSet clone() {
      SubDataSet obj = (SubDataSet) super.clone();
      // shouldn't be necessary. there will be a lot of SubDataSet with one for each sub-graph
      //obj.dset = (DataSet) this.dset.clone();

      if(conds != null) {
         obj.conds = new HashMap(conds);
      }

      return obj;
   }

   @Override
   public void addProjectedValue(Map values) {
      // merge the current outer dimension values into the row value.
      if(conds != null) {
         Map values2 = new HashMap(values);
         values2.putAll(conds);
         values = values2;
      }

      super.addProjectedValue(values);
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

   @Override
   public void addSubDataSet(DataSet ds) {
      super.addSubDataSet(ds);
      DataSet dset = getDataSet();
      // needed for project forward of multi-level facet (49309).
      ((AbstractDataSet) dset).addSubDataSet(ds);
   }

   @Override
   public void clearSubDataSetDuplicates(AbstractDataSet dset) {
      super.clearSubDataSetDuplicates(dset);
      final DataSet dataSet = getDataSet();
      ((AbstractDataSet) dataSet).clearSubDataSetDuplicates(dset);
   }

   /**
    * Check if this data set is for the innermost coord of a facet (or not a facet).
    */
   public boolean isInnermost() {
      return innermost;
   }

   /**
    * Set if this data set is for the innermost coord of a facet (or not a facet).
    */
   public void setInnermost(boolean innermost) {
      this.innermost = innermost;
   }

   @Override
   protected boolean shouldProject(int col) {
      if(!super.shouldProject(col)) {
         return false;
      }

      Class type = getType(getHeader(col));
      // don't project forward on outer dimensions on facet (49321).
      // returning null for facet dimension causes dims to have different number of values (49376).
      return innermost || !(Number.class.isAssignableFrom(type) ||
         Date.class.isAssignableFrom(type));
   }

   @Override
   public HRef getHyperlink(int col, int row) {
      HRef href = super.getHyperlink(col, row);
      DataSet dset = getDataSet();

      // trend/comparison is only valid within the subdataset, so reset it here. (50171)
      if(href != null && dset instanceof VSDataSet) {
         Hyperlink link = ((VSDataSet) dset).getHyperlink(getHeader(col));

         if(link != null) {
            for(String name : link.getParameterNames()) {
               String field = link.getParameterField(name);
               int idx = indexOfHeader(field);

               if(idx >= 0) {
                  Object value = getData(idx, row);
                  href.setParameter(name, value);
               }
            }
         }
      }

      return href;
   }

   /**
    * Get the condition that is used to extract the subset data.
    */
   public Object getConditionKey() {
      if(conds != null) {
         return conds;
      }
      else if(mapping != null && mapping.length > 0) {
         return mapping.length + ":" + mapping[0] + "-" + mapping[mapping.length - 1];
      }
      else if(mapping2 != null && mapping2.size() > 0) {
         return mapping2.size() + ":" + mapping2.get(0) + "-" + mapping2.get(mapping2.size() - 1);
      }

      return null;
   }

   @Override
   public String toString() {
      Object key = getConditionKey();

      if(key != null) {
         return super.toString() + "[" + key + "]";
      }

      return super.toString();
   }

   private transient XIntFragment mapping2; // row -> base row
   private transient int[] mapping;
   private transient boolean inited = false;
   private Map<String, Object> conds;
   private final AtomicBoolean disposed = new AtomicBoolean();
   private boolean identical;
   private boolean innermost = true;
   private final Map<String, Integer> colIndexes = new HashMap<>();
   private int mapping2cnt;

   private static final Logger LOG = LoggerFactory.getLogger(SubDataSet.class);

}
