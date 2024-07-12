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
package inetsoft.report.filter;

import inetsoft.report.StyleConstants;
import inetsoft.uql.XConstants;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.jnumbers.NumberParser;

/**
 * Calculate the product of all values.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ProductFormula implements PercentageFormula {
   /**
    * Reset the formula to start over.
    */
   @Override
   public void reset() {
      product = 1;
      cnt = 0;
   }

   /**
    * Add a value to the formula.
    */
   @Override
   public void addValue(Object v) {
      if(v == null) {
         return;
      }

      try {
         cnt++;
         product *= (v instanceof Number) ? ((Number) v).doubleValue() :
            NumberParser.getDouble(v.toString());
      }
      catch(NumberFormatException e) {
      }
   }

   /**
    * Add a double value to the formula.
    */
   @Override
   public void addValue(double v) {
      if(v == Tool.NULL_DOUBLE) {
         return;
      }

      cnt++;
      product *= v;
   }

   /**
    * Add double values to the formula.
    */
   @Override
   public void addValue(double[] v) {
      if(v != null && v.length > 0) {
         addValue(v[0]);
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

      cnt++;
      product *= v;
   }

   /**
    * Add a long value to the formula.
    */
   @Override
   public void addValue(long v) {
      if(v == Tool.NULL_LONG) {
         return;
      }

      cnt++;
      product *= v;
   }

   /**
    * Add an int value to the formula.
    */
   @Override
   public void addValue(int v) {
      if(v == Tool.NULL_INTEGER) {
         return;
      }

      cnt++;
      product *= v;
   }

   /**
    * Add a short value to the formula.
    */
   @Override
   public void addValue(short v) {
      if(v == Tool.NULL_SHORT) {
         return;
      }

      cnt++;
      product *= v;
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
      if(cnt == 0) {
         return def ? Double.valueOf(0) : null;
      }

      double douResult = product;

      if((percentageType != 0) && (total instanceof Number)) {
         double totalNum = (total instanceof Number) ?
            ((Number) total).doubleValue() :
            Double.valueOf(total.toString()).doubleValue();

         if(totalNum == 0) {
            return def ? Double.valueOf(0) : null;
         }

         return Double.valueOf(douResult / totalNum);
      }

      return Double.valueOf(douResult);
   }

   /**
    * Get the formula double result.
    */
   @Override
   public double getDoubleResult() {
      if(cnt == 0) {
         return 0;
      }

      if(percentageType != 0 && (total instanceof Number)) {
         double totalNum = (total instanceof Number) ?
            ((Number) total).doubleValue() :
            Double.valueOf(total.toString()).doubleValue();

         if(totalNum == 0) {
            return 0;
         }

         return product / totalNum;
      }

      return product;
   }

   /**
    * Check if the result is null.
    */
   @Override
   public boolean isNull() {
      return cnt == 0;
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
    * Get formula display name.
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("Product");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.PRODUCT_FORMULA;
   }

   @Override
   public Class getResultType() {
      return Double.class;
   }

   private short percentageType = (short) StyleConstants.PERCENTAGE_NONE;
   private Object total = null;
   private double product = 1;
   private int cnt = 0;
   private boolean def;
}
