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

import inetsoft.report.StyleConstants;
import inetsoft.uql.XConstants;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calculate the first value.
 *
 * @version 11.5, 2013
 * @author InetSoft Technology Corp
 */
public class FirstFormula implements Formula2, PercentageFormula {
   /**
    * Constructor.
    */
   public FirstFormula() {
      super();
   }

   /**
    * Create a first formula.
    * @param col column number of dimension/secondary column.
    */
   public FirstFormula(int col) {
      this();
      this.col = col;
   }

   /**
    * Get the second column index. The value on the primary column and the
    * value on the second column are passed in as an object array with
    * two elements.
    */
   @Override
   public int[] getSecondaryColumns() {
      return new int[] {col};
   }

   /**
    * Reset the formula to start over.
    */
   @Override
   public void reset() {
      cnt = 0;
      dval = null;
      mval2 = null;
   }

   /**
    * Add a double value to the formula.
    */
   @Override
   public void addValue(double v) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Add double value to the formula.
    */
   @Override
   public void addValue(double[] v) {
      if(v == null || v.length == 0 || v[0] == Tool.NULL_DOUBLE) {
         return;
      }

      try {
         Double dV = v[1];
         Double mV = v[0];
         int c = compare(dV, dval);

         // first time
         if(cnt == 0 || (isFirst() && c < 0 || !isFirst() && c <= 0)) {
            dval = dV;
            mval2 = mV;
         }

         cnt++;
      }
      catch(NumberFormatException e) {
         LOG.error(e.getMessage(), e);
      }
   }

   /**
    * Add a float value to the formula.
    */
   @Override
   public void addValue(float v) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Add a long value to the formula.
    */
   @Override
   public void addValue(long v) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Add an int value to the formula.
    */
   @Override
   public void addValue(int v) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Add a short value to the formula.
    */
   @Override
   public void addValue(short v) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Add a value to the formula.
    */
   @Override
   public void addValue(Object v) {
      if(!(v instanceof Object[])) {
         return;
      }

      Object[] pair = (Object[]) v;

      if(pair[0] == null) {
         return;
      }

      Object dV = pair[1];
      Object mV = pair[0];
      int c = compare(dV, dval);

      // first time
      if(cnt == 0 || (isFirst() && c < 0 || !isFirst() && c <= 0)) {
         dval = dV;
         mval2 = mV;
      }

      cnt++;
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
      if(percentageType != 0) {
         return getDoubleResult();
      }
      else {
         return getRawResult();
      }
   }

   /**
    * Get the formula double result.
    */
   @Override
   public double getDoubleResult() {
      double douResult = mval2 instanceof Number ? ((Number) mval2).doubleValue() : 0;

      if(percentageType != 0) {
         Double percentage = getPercentage(douResult);
         return percentage == null ? 0 : percentage.doubleValue();
      }

      return douResult;
   }

   protected Object getRawResult() {
      return mval2;
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
    * Get the total used to calculate percentage.
    */
   public Object getTotal() {
      return total;
   }

   @Override
   public Object getOriginalResult() {
      int perType = getPercentageType();
      setPercentageType(StyleConstants.PERCENTAGE_NONE);
      Object oresult = getResult();
      setPercentageType(perType);

      return oresult;
   }

   /**
    * Get percentage.
    */
   public Double getPercentage(double dou) {
      double totalNum = 0;

      try {
         Object total = getTotal();

         totalNum =(total instanceof Number) ?
            ((Number) total).doubleValue() :
            Double.valueOf(total.toString()).doubleValue();

         if(totalNum <= 0) {
            return null;
         }

         return Double.valueOf(dou / totalNum);
      }
      catch(Exception e) {
         return null;
      }
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
         FirstFormula f = (FirstFormula) super.clone();
         f.dval = null;
         f.mval2 = null;
         return f;
      }
      catch(CloneNotSupportedException ex) {
         return this;
      }
   }

   /**
    * Get formula display name.
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("FirstFormula");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.FIRST_FORMULA;
   }

   protected int compare(Object v1, Object v2) {
      return Tool.compare(v1, v2);
   }

   boolean isFirst() {
      return true;
   }

   private int col;
   private Object dval = null;
   private Object mval2;
   private int cnt = 0;
   private boolean def;
   private short percentageType = (short) StyleConstants.PERCENTAGE_NONE;
   private Object total = null;
   private static final Logger LOG = LoggerFactory.getLogger(FirstFormula.class);
}
