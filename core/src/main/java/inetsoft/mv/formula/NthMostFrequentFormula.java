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
package inetsoft.mv.formula;

import inetsoft.report.filter.Formula;
import inetsoft.uql.XConstants;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import it.unimi.dsi.fastutil.doubles.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculate the nth most frequent value in a column.
 *
 * @version 12.2, 4/14/2016
 * @author InetSoft Technology Corp
 */
public class NthMostFrequentFormula 
   implements Formula, MergeableFormula<NthMostFrequentFormula>
{
   /**
    * Create a formula to get the nth most frequent value.
    * @param n this value starts at 1 for the most frequent value.
    */
   public NthMostFrequentFormula(int n) {
      if(n <= 0) {
         throw new RuntimeException("N should be greater than zero");
      }

      this.n = n;
      map.defaultReturnValue(0);
   }

   // Needed to avoid Kyro error "Class cannot be created (missing no-arg constructor):"
   private NthMostFrequentFormula() { }

   /**
    * Reset the formula to start over.
    */
   @Override
   public void reset() {
      map.clear();
   }

   /**
    * Add a double value to the formula.
    */
   @Override
   public void addValue(double v) {
      int freq = map.get(v);
      map.put(v, freq + 1);
   }

   /**
    * Add double values to the formula.
    */
   @Override
   public void addValue(double[] v) {
      addValue(v[0]);
   }

   /**
    * Add a float value to the formula.
    */
   @Override
   public void addValue(float v) {
      addValue(v);
   }

   /**
    * Add a long value to the formula.
    */
   @Override
   public void addValue(long v) {
      addValue(v);
   }

   /**
    * Add an int value to the formula.
    */
   @Override
   public void addValue(int v) {
      addValue(v);
   }

   /**
    * Add a short value to the formula.
    */
   @Override
   public void addValue(short v) {
      addValue(v);
   }

   /**
    * Add a value to the formula.
    */
   @Override
   public void addValue(Object v) {
      addValue(v.hashCode());
   }

   /**
    * Set the default result option of this formula.
    * @param def <tt>true</tt> to use the default value of a formula if no
    * result, <tt>false</tt> to just return null.
    */
   @Override
   public void setDefaultResult(boolean def) {
      this.def = def;
   }

   /**
    * Get the default result option of this formula.
    * @return <tt>true</tt> to use the default value of a formula if no
    * result, <tt>false</tt> to just return null.
    */
   @Override
   public boolean isDefaultResult() {
      return def;
   }

   /**
    * Get the formula result.
    */
   @Override
   public Object getResult() {
      if(map.size() == 0) {
         return def ? 0 : null;
      }

      return getResultObject();
   }

   /**
    * Get the formula double result.
    */
   @Override
   public double getDoubleResult() {
      if(map.size() == 0) {
         return 0;
      }

      Object obj = getResultObject();

      if(obj instanceof Number) {
         return ((Number) obj).doubleValue();
      }

      return 0;
   }

   /**
    * Check if the result is null.
    */
   @Override
   public boolean isNull() {
      return map.size() == 0;
   }

   /**
    * Get the formula result object.
    */
   private Object getResultObject() {
      List<Double> values = new ArrayList<>(map.keySet());

      values.sort((v1, v2) -> {
         int rc = map.get(v2.doubleValue()) - map.get(v1.doubleValue());

         // if same frequence, sort the value so the result is not random
         if(rc == 0) {
            rc = Tool.compare(v1, v2, true, true);
         }

         return rc;
      });

      if(values.size() >= n) {
         Object val = values.get(n - 1);
         return (val == __NULL__) ? null : val;
      }

      return null;
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      NthMostFrequentFormula nmf = new NthMostFrequentFormula(n);
      nmf.def = def;
      nmf.map = new Double2IntOpenHashMap(map);

      return nmf;
   }

   /**
    * Get formula display name.
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("NthMostFrequent");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.NTHMOSTFREQUENT_FORMULA;
   }

   @Override
   public void merge(NthMostFrequentFormula f) {
      DoubleIterator iter = f.map.keySet().iterator();
      
      while(iter.hasNext()) {
         double v = iter.nextDouble();
         map.put(v, map.get(v) + f.map.get(v));
      }
   }

   private Double2IntMap map = new Double2IntOpenHashMap();
   private int n;
   private boolean def;
}
