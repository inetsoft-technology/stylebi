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

import inetsoft.uql.XConstants;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculate the nth most frequent value in a column.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class NthMostFrequentFormula implements Formula, java.io.Serializable {
   /**
    * Create a formula to get the nth most frequent value.
    * @param n this value starts at 1 for the most frequent value.
    */
   public NthMostFrequentFormula(int n) {
      if(n <= 0) {
         n = 1;
         LOG.info("The value of {} is invalid, use 1 as default.", getDisplayName());
      }

      this.n = n;
      map.defaultReturnValue(0);
   }

   // Needed to avoid Kyro error "Class cannot be created (missing no-arg constructor):"
   public NthMostFrequentFormula() {
   }

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
      Double val = v == Tool.NULL_DOUBLE ? null : v;
      addValue(val);
   }

   /**
    * Add double values to the formula.
    */
   @Override
   public void addValue(double[] v) {
      Double val = v[0] == Tool.NULL_DOUBLE ? null : v[0];
      addValue(val);
   }

   /**
    * Add a float value to the formula.
    */
   @Override
   public void addValue(float v) {
      Float val = v == Tool.NULL_FLOAT ? null : v;
      addValue(val);
   }

   /**
    * Add a long value to the formula.
    */
   @Override
   public void addValue(long v) {
      Long val = v == Tool.NULL_LONG ? null : v;
      addValue(val);
   }

   /**
    * Add an int value to the formula.
    */
   @Override
   public void addValue(int v) {
      Integer val = v == Tool.NULL_INTEGER ? null : v;
      addValue(val);
   }

   /**
    * Add a short value to the formula.
    */
   @Override
   public void addValue(short v) {
      Short val = v == Tool.NULL_SHORT ? null : v;
      addValue(val);
   }

   /**
    * Add a value to the formula.
    */
   @Override
   public void addValue(Object v) {
      if(v == null) {
         v = __NULL__;
      }

      int freq = map.getInt(v);
      map.put(v, freq + 1);
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
      else if(obj instanceof String) {
         return Double.valueOf((String) obj);
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
      List<Object> values = new ArrayList<>(map.keySet());

      values.sort((v1, v2) -> {
         int rc = map.getInt(v2) - map.getInt(v1);

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
      nmf.map = new Object2IntOpenHashMap<>(map);

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

   private Object2IntMap<Object> map = new Object2IntOpenHashMap<>();
   private int n;
   private boolean def;

   private static final Logger LOG = LoggerFactory.getLogger(NthMostFrequentFormula.class);
}
