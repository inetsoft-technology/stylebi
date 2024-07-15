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
package inetsoft.report.composition.execution;

import inetsoft.report.filter.Formula;
import inetsoft.uql.viewsheet.internal.SelectionSet;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Class used for calculating the measures of a given selection.
 *
 * @since 13.1
 */
public class SelectionMeasureAggregation {
   public SelectionMeasureAggregation(boolean IDMode) {
      this.IDMode = IDMode;
   }

   public void updateBounds() {
      final Map<Object, Object> mvalues = getMeasures();
      final Map<SelectionSet.Tuple, Formula> formulaMap = getFormulas();
      double mmin = Double.MAX_VALUE;
      double mmax = -Double.MAX_VALUE;

      for(SelectionSet.Tuple key : formulaMap.keySet()) {
         Object nkey = SelectionSet.normalize(key);

         // mvalues are populated directly in MVTableMetaData, but calculated in
         // RealtimeTableMetaData. some vs may result in a mix of realtime and MV
         // meta data (e.g. when a selection list is bound to two tables).
         // in that case, need to combine the data calculated in MVTableMetaData
         // and the value in realtime's formula.
         if(mvalues.containsKey(nkey)) {
            formulaMap.get(key).addValue(mvalues.get(nkey));
         }

         if(IDMode && key.size() == 1 && key.get(0) != null && !StringUtils.isEmpty(key.get(0))) {
            Object[] arr = new Object[2];
            arr[1] = nkey;
            SelectionSet.Tuple realKey = new SelectionSet.Tuple(arr, 2);

            if(formulaMap.containsKey(realKey)) {
               key = realKey;
            }
         }

         final Object mval = formulaMap.get(key).getResult();
         mvalues.put(nkey, mval);

         if(mval instanceof Number) {
            mmin = Math.min(mmin, ((Number) mval).doubleValue());
            mmax = Math.max(mmax, ((Number) mval).doubleValue());
         }
      }

      setMinIfSmaller(mmin);
      setMaxIfBigger(mmax);
   }

   public Map<Object, Object> getMeasures() {
      return measures;
   }

   public Map<SelectionSet.Tuple, Formula> getFormulas() {
      return formulas;
   }

   public void setMaxIfBigger(double max) {
      if(this.max == null) {
         this.max = max;
      }
      else {
         this.max = Math.max(this.max, max);
      }
   }

   public void setMinIfSmaller(double min) {
      if(this.min == null) {
         this.min = min;
      }
      else {
         this.min = Math.min(this.min, min);
      }
   }

   public double getMin() {
      return min == null ? 0 : min;
   }

   public double getMax() {
      return max == null ? 1 : max;
   }

   /**
    * @return if parent-child selection tree.
    */
   public boolean isIDMode() {
      return IDMode;
   }

   /**
    * @param IDMode true if parent-child selection tree.
    */
   public void setIDMode(boolean IDMode) {
      this.IDMode = IDMode;
   }

   private final Map<Object, Object> measures = new HashMap<>(); // keyed with selection values
   private final Map<SelectionSet.Tuple, Formula> formulas = new HashMap<>();
   private Double min = null;
   private Double max = null;
   private boolean IDMode;
}
