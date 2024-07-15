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

import inetsoft.report.Comparer;
import inetsoft.report.StyleConstants;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.XConstants;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * Calculate the maximum value.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class MaxFormula implements PercentageFormula {
   /**
    * Reset the formula to start over.
    */
   @Override
   public void reset() {
      max = null;
      dmax = -Double.MAX_VALUE;
      fmax = -Float.MAX_VALUE;
      lmax = Long.MIN_VALUE;
      imax = Integer.MIN_VALUE;
      smax = Short.MIN_VALUE;
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

      int result = comp.compare(dmax, v);

      if(result < 0) {
         dmax = v;
      }
   }

   /**
    * Add double values to the formula.
    */
   @Override
   public void addValue(double[] v) {
      if(v.length == 0 || v[0] == Tool.NULL_DOUBLE) {
         return;
      }

      if(comp == null) {
         comp = ImmutableDefaultComparer.getInstance();
      }

      int result = comp.compare(dmax, v[0]);

      if(result < 0) {
         dmax = v[0];
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

      int result = comp.compare(fmax, v);

      if(result < 0) {
         fmax = v;
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

      int result = comp.compare(lmax, v);

      if(result < 0) {
         lmax = v;
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

      int result = comp.compare(imax, v);

      if(result < 0) {
         imax = v;
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

      int result = comp.compare(smax, v);

      if(result < 0) {
         smax = v;
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

      int result = compare(comp, max, v);

      if(result < 0) {
         max = v;
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
      Object max = getMax();

      if(percentageType != 0 && max != null && total != null) {
         try {
            double maxNum = (max instanceof Number) ? ((Number) max).doubleValue()
               : NumberParserWrapper.getDouble(max.toString());
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

      return max == null && def ? Integer.valueOf(0) : max;
   }

   /**
    * Get the formula double result.
    */
   @Override
   public double getDoubleResult() {
      if(dmax == -Double.MAX_VALUE) {
         return 0;
      }

      if(percentageType != 0 && total != null) {
         try {
            double totalNum = (total instanceof Number) ? ((Number) total).doubleValue()
               : NumberParserWrapper.getDouble(total.toString());

            if(totalNum == 0) {
               return 0;
            }

            return dmax / totalNum;
         }
         catch(Exception ex) {
            LOG.debug("Failed to get the formula result as a double value", ex);
         }
      }

      return dmax;
   }

   /**
    * Check if the result is null.
    */
   @Override
   public boolean isNull() {
      return getMax() == null;
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
      if(parseNum && v1 instanceof String && v2 instanceof String) {
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
      return Catalog.getCatalog().getString("Max");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.MAX_FORMULA;
   }

   /**
    * Get formula name
    */
   public String toString() {
      return Catalog.getCatalog().getString("Maximum");
   }

   /**
    * Get the max value.
    */
   private Object getMax() {
      if(dmax != -Double.MAX_VALUE) {
         return dmax;
      }
      else if(fmax != -Float.MAX_VALUE) {
         return fmax;
      }
      else if(lmax != Long.MIN_VALUE) {
         return lmax;
      }
      else if(imax != Integer.MIN_VALUE) {
         return imax;
      }
      else if(sflag) {
         return smax;
      }
      else {
         return max;
      }
   }

   double dmax = -Double.MAX_VALUE;
   float fmax = -Float.MAX_VALUE;
   long lmax = Long.MIN_VALUE;
   int imax = Integer.MIN_VALUE;
   short smax = Short.MIN_VALUE;
   boolean sflag = false;

   private short percentageType = (short) StyleConstants.PERCENTAGE_NONE;
   private Object total = null;
   private Comparer comp = null;
   private Object max = null;
   private boolean def;
   private boolean parseNum = "true".equals(SreeEnv.getProperty("formula.parse.number", "true"));

   private static final Logger LOG =
      LoggerFactory.getLogger(MaxFormula.class);
}
