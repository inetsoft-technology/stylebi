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
package inetsoft.mv.formula;

import inetsoft.report.StyleConstants;
import inetsoft.report.filter.PercentageFormula;
import inetsoft.uql.XConstants;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;

import java.util.Collections;

/**
 * Calculate the median of all numbers.
 *
 * @version 12.2, 4/10/2016
 * @author InetSoft Technology Corp
 */
public class MedianFormula 
   implements PercentageFormula, MergeableFormula<MedianFormula>
{
   /**
    * Reset the formula to start over.
    */
   @Override
   public void reset() {
      vs.clear();
   }

   /**
    * Add a double value to the formula.
    */
   @Override
   public void addValue(double v) {
      if(v == Tool.NULL_DOUBLE) {
         return;
      }

      vs.add(v);
   }

   /**
    * Add double values to the formula.
    */
   @Override
   public void addValue(double[] v) {
      if(v[0] == Tool.NULL_DOUBLE) {
         return;
      }

      vs.add(v[0]);
   }

   /**
    * Add a float value to the formula.
    */
   @Override
   public void addValue(float v) {
      if(v == Tool.NULL_FLOAT) {
         return;
      }

      Float val = Float.valueOf(v);

      vs.add(val);
   }

   /**
    * Add a long value to the formula.
    */
   @Override
   public void addValue(long v) {
      if(v == Tool.NULL_LONG) {
         return;
      }

      Long val = Long.valueOf(v);

      vs.add(val);
   }

   /**
    * Add an int value to the formula.
    */
   @Override
   public void addValue(int v) {
      if(v == Tool.NULL_INTEGER) {
         return;
      }

      Integer val = Integer.valueOf(v);

      vs.add(val);
   }

   /**
    * Add a short value to the formula.
    */
   @Override
   public void addValue(short v) {
      if(v == Tool.NULL_SHORT) {
         return;
      }

      Short val = Short.valueOf(v);

      vs.add(val);
   }

   /**
    * Add a value to the formula.
    */
   @Override
   public void addValue(Object v) {
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
      if(vs.size() == 0) {
         return def ? (double) 0 : null;
      }

      return getResultObject();
   }

   /**
    * Get the formula double result.
    */
   @Override
   public double getDoubleResult() {
      if(vs.size() == 0) {
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
      return vs.size() == 0;
   }

   /**
    * Get the formula double result.
    */
   private Object getResultObject() {
      try {
         Collections.sort(vs);

         if(vs.size() % 2 == 0) {
            double obj1 = vs.get(vs.size() / 2 - 1);
            double obj2 = vs.get(vs.size() / 2);
            double douResult = (obj1 + obj2) / 2;

            if(percentageType != 0 && total != null) {
               double totalNum = (total instanceof Number) ?
                  ((Number) total).doubleValue() :
                  Double.valueOf(total.toString());

               if(totalNum == 0) {
                  return def ? (double) 0 : null;
               }

               return douResult / totalNum;
            }

            return douResult;
         }
         else {
            double douResult = vs.get(vs.size() / 2);

            if(percentageType != 0 && total != null) {
               double totalNum = (total instanceof Number) ?
                  ((Number) total).doubleValue() :
                  Double.valueOf(total.toString());

               if(totalNum == 0) {
                  return def ? (double) 0 : null;
               }
               
               return douResult / totalNum;
            }
            
            return douResult;
         }
      }
      catch(Exception e) {
      }

      return null;
   }

   @Override
   public Object clone() {
      MedianFormula mf = new MedianFormula();

      mf.def = def;
      mf.vs = new DoubleArrayList(vs);
      mf.setPercentageType(getPercentageType());
      mf.setTotal(total);

      return mf;
   }

   /**
    * Get percentage type.
    */
   @Override
   public int getPercentageType() {
      return percentageType;
   }

   /**
    * Set percentage type.
    * three types: StyleConstants.PERCENTAGE_NONE,
    *              StyleConstants.PERCENTAGE_OF_GROUP,
    *              StyleConstants.PERCENTAGE_OF_GRANDTOTAL.
    */
   @Override
   public void setPercentageType(int percentageType) {
      this.percentageType = (short) percentageType;
   }

   /**
    * Set the total used to calculate percentage.
    * if percentage type is PERCENTAGE_NONE, it is ineffective to
    * invoke the method.
    */
   @Override
   public void setTotal(Object total) {
      this.total = total;
   }

   /**
    * Get the original formula result without percentage.
    */
   @Override
   public Object getOriginalResult() {
      int perType = getPercentageType();
      setPercentageType(StyleConstants.PERCENTAGE_NONE);
      Object oresult = getResult();
      setPercentageType(perType);

      return oresult;
   }

   /**
    * Get formula display name.
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("Median");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.MEDIAN_FORMULA;
   }

   /**
    * Get formula name
    */
   public String toString() {
      return Catalog.getCatalog().getString("Median");
   }

   @Override 
   public void merge(MedianFormula f) {
      vs.addAll(f.vs);
   }

   private short percentageType = (short) StyleConstants.PERCENTAGE_NONE;
   private Object total = null;
   private DoubleList vs = new DoubleArrayList();
   private boolean def;
}