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
package inetsoft.report.filter;

import inetsoft.report.Comparer;
import inetsoft.report.StyleConstants;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.XConstants;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * Calculate the minimum value.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class MinFormula implements PercentageFormula {
   /**
    * Reset the formula to start over.
    */
   @Override
   public void reset() {
      min = null;
      dmin = Double.MAX_VALUE;
      fmin = Float.MAX_VALUE;
      lmin = Long.MAX_VALUE;
      imin = Integer.MAX_VALUE;
      smin = Short.MAX_VALUE;
   }

   /**
    * Add a double value to the formula.
    */
   @Override
   public void addValue(double v) {
      if(v == Tool.NULL_DOUBLE) {
         return;
      }

      if(comp == null) {
         comp = ImmutableDefaultComparer.getInstance();
      }

      int result = comp.compare(v, dmin);

      if(result < 0) {
         dmin = v;
      }
   }

   /**
    * Add double values to the formula.
    */
   @Override
   public void addValue(double[] v) {
      if(v[0] == Tool.NULL_DOUBLE) {
         return;
      }

      if(comp == null) {
         comp = ImmutableDefaultComparer.getInstance();
      }

      int result = comp.compare(v[0], dmin);

      if(result < 0) {
         dmin = v[0];
      }
   }

   /**
    * Add a float value to the formula.
    */
   @Override
   public void addValue(float v) {
      if(v == Tool.NULL_FLOAT) {
         return;
      }

      if(comp == null) {
         comp = ImmutableDefaultComparer.getInstance();
      }

      int result = comp.compare(v, fmin);

      if(result < 0) {
         fmin = v;
      }
   }

   /**
    * Add a long value to the formula.
    */
   @Override
   public void addValue(long v) {
      if(v == Tool.NULL_LONG) {
         return;
      }

      if(comp == null) {
         comp = ImmutableDefaultComparer.getInstance();
      }

      int result = comp.compare(v, lmin);

      if(result < 0) {
         lmin = v;
      }
   }

   /**
    * Add an int value to the formula.
    */
   @Override
   public void addValue(int v) {
      if(v == Tool.NULL_INTEGER) {
         return;
      }

      if(comp == null) {
         comp = ImmutableDefaultComparer.getInstance();
      }

      int result = comp.compare(v, imin);

      if(result < 0) {
         imin = v;
      }
   }

   /**
    * Add a short value to the formula.
    */
   @Override
   public void addValue(short v) {
      if(v == Tool.NULL_SHORT) {
         return;
      }

      if(comp == null) {
         comp = ImmutableDefaultComparer.getInstance();
         sflag = true;
      }

      int result = comp.compare(v, smin);

      if(result < 0) {
         smin = v;
      }
   }

   /**
    * Add a value to the formula.
    */
   @Override
   public void addValue(Object v) {
      if(v == null) {
         return;
      }

      if(comp == null) {
         if(v instanceof Date) {
            comp = new DateComparer();
         }
         else {
            comp = ImmutableDefaultComparer.getInstance();
         }
      }

      int result = min == null ? -1 : compare(comp, v, min);

      if(result < 0) {
         min = v;
      }
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
      Object min = getMin();

      if(percentageType != 0 && min != null && total != null) {
         try {
            double maxNum = (min instanceof Number) ? ((Number) min).doubleValue()
               : NumberParserWrapper.getDouble(min.toString());
            double totalNum = (total instanceof Number) ? ((Number) total).doubleValue()
               : NumberParserWrapper.getDouble(total.toString());

            if(totalNum == 0) {
               return def ? (double) 0 : null;
            }

            return maxNum / totalNum;
         }
         catch(Exception ex) {
            LOG.debug("Failed to get the formula result", ex);
         }
      }

      return min == null && def ? Integer.valueOf(0) : min;
   }

   /**
    * Get the formula double result.
    */
   @Override
   public double getDoubleResult() {
      // no value
      if(dmin == Double.MAX_VALUE) {
         return 0;
      }

      if(percentageType != 0 && total != null) {
         try {
            double totalNum = (total instanceof Number)
               ? ((Number) total).doubleValue()
               : Double.parseDouble(total.toString());

            if(totalNum == 0) {
               return 0;
            }

            return dmin / totalNum;
         }
         catch(Exception ex) {
            LOG.debug("Failed to get the formula result as a double value", ex);
         }
      }

      return dmin;
   }

   /**
    * Check if the result is null.
    */
   @Override
   public boolean isNull() {
      return getMin() == null;
   }

   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(CloneNotSupportedException ex) {
         return this;
      }
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
    * Compare two values and return <0, 0, or >0 if the first value is less
    * than, equal to, or greater than the second value.
    */
   private int compare(Comparer comp, Object v1, Object v2) {
      if(v1 == null) { // null < everything
         return -1;
      }

      // @by larryl, if both values are number string, use numeric comparison.
      // This is not completely consistent with the logic of comparing using
      // the actual type, but is probably more user expects. This is especially
      // true when formula is used in a fixed table, where all values are
      // string even if user entered a number
      if(v1 instanceof String && v2 instanceof String && parseNum) {
         try {
            v1 = NumberParserWrapper.getDouble((String) v1);
            v2 = NumberParserWrapper.getDouble((String) v2);
         }
         catch(Exception ex) {
            parseNum = false;
         }
      }

      return comp.compare(v1, v2);
   }

   /**
    * Get formula display name.
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("Min");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.MIN_FORMULA;
   }

   /**
    * Get formula name
    */
   public String toString() {
      return Catalog.getCatalog().getString("Minimum");
   }

   /**
    * Get the min value.
    */
   private Object getMin() {
      if(dmin != Double.MAX_VALUE) {
         return dmin;
      }
      else if(fmin != Float.MAX_VALUE) {
         return fmin;
      }
      else if(lmin != Long.MAX_VALUE) {
         return lmin;
      }
      else if(imin != Integer.MAX_VALUE) {
         return imin;
      }
      else if(sflag) {
         return smin;
      }
      else {
         return min;
      }
   }

   double dmin = Double.MAX_VALUE;
   float fmin = Float.MAX_VALUE;
   long lmin = Long.MAX_VALUE;
   int imin = Integer.MAX_VALUE;
   short smin = Short.MAX_VALUE;
   boolean sflag = false;

   private boolean parseNum = "true".equals(SreeEnv.getProperty("formula.min.number", "true"));
   private short percentageType = (short) StyleConstants.PERCENTAGE_NONE;
   private Object total = null;
   private Comparer comp = null;
   private Object min = null;
   private boolean def;

   private static final Logger LOG =
      LoggerFactory.getLogger(MinFormula.class);
}
