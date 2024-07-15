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
package inetsoft.report.composition.graph;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DataSetComparator;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.visual.ElementVO;
import inetsoft.report.filter.*;
import inetsoft.util.Tool;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sort the rows by the values of a column.
 */
public class ValueOrderComparer implements DataSetComparator, Serializable {
   /**
    * Constructor.
    * @param col the sorting column.
    * @param vcol the column to be used for actual comparison.
    */
   public ValueOrderComparer(String col, String vcol, Formula formula, boolean asc) {
      this.col = col;
      this.vcol = vcol;
      this.formula = formula;

      // only support aggregate of aggregate for sum and avg. all others take
      // the largest value in case of facet. Otherwise the value is wrong (e.g.
      // count would always return 1 for non-facet).
      if(!(formula instanceof SumFormula) && !(formula instanceof AverageFormula) &&
         !(formula instanceof FirstFormula) && !(formula instanceof LastFormula))
      {
         this.formula = new MaxFormula();
      }

      comp.setNegate(!asc);
   }

   /**
    * Set the formula for totaling multiple values for a dimension for
    * comparison.
    */
   public void setFormula(Formula formula) {
      this.formula = formula;
   }

   /**
    * Get the group columns.
    */
   public String[] getGroups() {
      return groups;
   }

   /**
    * If group columns are set, values are grouped with the groups instead of the dimension column.
    */
   public void setGroups(String[] groups) {
      this.groups = groups == null ? new String[0] : groups;
      this.vmap = null;
   }

   /**
    * Compare the two rows in the dataset.
    */
   @Override
   public int compare(DataSet data, int row1, int row2) {
      if(groups.length > 0) {
         Object v1 = getSortKey(data, row1, col);
         Object v2 = getSortKey(data, row2, col);

         return compare(v1, v2);
      }

      String vcol = getRealValueCol();
      return comp.compare(data.getData(vcol, row1), data.getData(vcol, row2));
   }

   /**
    * The vmap instance variable is only accessed within this method, which is protected by
    * vmapLock. No other code should reference this.vmap, otherwise race conditions may occur.
    */
   private Map<Object, Object> getValueMap() {
      // Keep a weak reference to the data set that was used to populate the value map. That way we
      // can check if the mapped values are out of date and need to be reloaded. This avoids race
      // conditions that cause null pointer exceptions when vmap is set to null in setDataSet() or
      // deadlocks that occur when the same lock is shared by setDataSet() and getValueMap().
      // (DataSet.getRowCount() may try to lock the DataSet after this method has obtained the vmap
      // lock, setDataSet() may be called by a thread that owns the DataSet lock and then tries to
      // get the vmap lock. The current implementation avoids that.)
      //
      // Note that it is possible to have a "stale" value map due to a race condition where
      // setDataSet() is called after the call to getDataSet() but before the following check to see
      // if vmap is out of date. However, this is generally preferable to null pointer exceptions or
      // deadlocks.
      Map<Object, Object> vmap0 = this.vmap;
      DataSet mapData = this.data;

      if(vmap0 != null) {
         return vmap0;
      }

      vmapLock.lock();

      try {
         if(vmap == null) {
            Map<Object, Object> newMap = new HashMap<>();

            if(mapData != null) {
               Map<Object, Formula> fmap = new HashMap<>();
               int[] col2 = formula instanceof Formula2
                  ? ((Formula2) formula).getSecondaryColumns() : null;
               String vcol = getRealValueCol();
               String col = this.col;

               // since all data is used for the actual layout of graph, should sort
               // all data if it's present. (47194)
               if(mapData.indexOfHeader(ElementVO.ALL_PREFIX + vcol) >= 0) {
                  vcol = ElementVO.ALL_PREFIX + vcol;
               }

               for(int i = 0; i < mapData.getRowCount(); i++) {
                  Object v1 = getSortKey(mapData, i, col);
                  Object v2 = mapData.getData(vcol, i);

                  if(col2 != null && col2.length > 0) {
                     Object v3 = mapData.getData(col2[0], i);
                     v2 = new Object[] { v2, v3 };
                  }

                  try {
                     fmap.computeIfAbsent(GDefaults.getValue(v1), v -> (Formula) formula.clone()).addValue(v2);
                  }
                  catch(Exception ex) {
                     LOG.warn("Failed to clone formula", ex);
                  }
               }

               fmap.entrySet().stream()
                  .filter(e -> e.getValue() != null)
                  .forEach(e -> newMap.put(e.getKey(), e.getValue().getResult()));
            }

            vmap = newMap;
         }

         return vmap;
      }
      finally {
         vmapLock.unlock();
      }
   }

   private Object getSortKey(DataSet data, int row, String col) {
      if(groups.length > 0) {
         return Arrays.stream(groups).map(g -> data.getData(g, row)).collect(Collectors.toList());
      }

      return data.getData(col, row);
   }

   /**
    * This method should return > 0 if v1 is greater than v2, 0 if
    * v1 is equal to v2, or < 0 if v1 is less than v2.
    * It must handle null values for the comparison values.
    * @param v1 comparison value.
    * @param v2 comparison value.
    * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
    */
   @Override
   public int compare(Object v1, Object v2) {
      Map<Object, Object> valueMap = getValueMap();
      int result = comp.compare(valueMap.get(v1), valueMap.get(v2));

      if(result == 0) {
         result = Tool.compare(v1, v2);
      }

      return result;
   }

   /**
    * Set value column.
    */
   public void setValueCol(String vcol) {
      this.vcol = vcol;
      this.dataVcol = null;
   }

   /**
    * Get value column.
    */
   public String getValueCol() {
      return vcol;
   }

   @Override
   public Comparator getComparator(int row) {
      return this;
   }

   @Override
   public DataSetComparator getComparator(DataSet data) {
      if(data == this.data) {
         return this;
      }

      if(this.data == null) {
         this.data = data;
         this.dataVcol = getRealValueCol(data);
         return this;
      }

      ValueOrderComparer comp = new ValueOrderComparer(col, getRealValueCol(data), formula,
                                                       !this.comp.isNegate());
      comp.data = data;
      comp.groups = this.groups;
      return comp;
   }

   // if data is brushed, use the 'all' measure so the rendering won't shift after
   // brushing. (54566)
   private String getRealValueCol(DataSet data) {
      if(data == null) {
         return vcol;
      }

      if(vcol != null && !vcol.startsWith(ElementVO.ALL_PREFIX)) {
         String allVcol = ElementVO.ALL_PREFIX + vcol;

         if(data.indexOfHeader(allVcol) >= 0) {
            return allVcol;
         }
      }

      return vcol;
   }

   // get the col value actual comparison.
   private String getRealValueCol() {
      return dataVcol != null ? dataVcol : vcol;
   }

   @Override
   public String toString() {
      return super.toString() + "(" + col + ", " + vcol + ", " +
         (comp.isNegate() ? "desc" : "asc") + ")";
   }

   private DataSet data;
   private String col, vcol;
   private String[] groups = {};
   private transient Map<Object, Object> vmap;
   private transient Lock vmapLock = new ReentrantLock();
   private transient String dataVcol;
   private Formula formula;
   private final DefaultComparer comp = new DefaultComparer();

   private static final Logger LOG = LoggerFactory.getLogger(ValueOrderComparer.class);
}
