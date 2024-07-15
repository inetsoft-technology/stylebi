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
package inetsoft.mv.formula;

import inetsoft.report.StyleConstants;
import inetsoft.report.filter.Formula2;
import inetsoft.uql.XConstants;
import inetsoft.util.Catalog;

/**
 * Calculate the averate.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public final class CompositeAverageFormula implements Formula2 {
   /**
    * Create a CompositeAverageFormula instance.
    */
   public CompositeAverageFormula() {
   }

   /**
    * Create a CompositeAverageFormula instance.
    * @param scol second column.
    */
   public CompositeAverageFormula(int scol) {
      this.col = scol;
   }

   /**
    * Reset the formula to start over.
    */
   @Override
   public void reset() {
      sum = 0;
      cnt = 0;
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
    * Get the second column index. The value on the primary column and the
    * value on the second column are passed in as an object array with
    * two elements.
    */
   @Override
   public int[] getSecondaryColumns() {
      return new int[] {col};
   }

   /**
    * Add a double value to the formula.
    */
   @Override
   public void addValue(double v) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Add a double value to the formula.
    */
   @Override
   public void addValue(double[] v) {
      sum += v[0];
      cnt += v[1];
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
      if(v == null || !(v instanceof Object[])) {
         return;
      }

      Object sumv = ((Object[]) v)[0];
      Object cntv = ((Object[]) v)[1];

      double ocnt = cnt;
      double osum = sum;

      try {
         double sv = (sumv instanceof Number) ? ((Number) sumv).doubleValue() :
            Double.parseDouble(sumv + "");
         double cv = (cntv instanceof Number) ? ((Number) cntv).doubleValue() :
            Double.parseDouble(cntv + "");
         addValue(new double[] {sv, cv});
      }
      catch(NumberFormatException e) {
         // ignore and reset
         cnt = ocnt;
         sum = osum;
      }
   }

   /**
    * Get the formula result.
    */
   @Override
   public Object getResult() {
      if(cnt == 0) {
         return def ? Double.valueOf(0) : null;
      }

      double douResult = sum / cnt;

      if(percentageType != 0) {
         double totalNum = ((Number) total).doubleValue();

         if(totalNum == 0) {
            return def ? Double.valueOf(0) :  null;
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

      double douResult = sum / cnt;

      if((percentageType != 0) && (total instanceof Number)) {
         double totalNum = (total instanceof Number) ?
            ((Number) total).doubleValue() :
            Double.parseDouble(total.toString());

         if(totalNum == 0) {
            return 0;
         }

         return douResult / totalNum;
      }

      return douResult;
   }

   /**
    * Check if the result is null.
    */
   @Override
   public boolean isNull() {
      return cnt == 0;
   }

   /**
    * Get percentage type.
    **/
   public int getPercentageType() {
      return percentageType;
   }

   /**
    * Set percentage type.
    * three types: StyleConstants.PERCENTAGE_NONE,
    *              StyleConstants.PERCENTAGE_OF_GROUP,
    *              StyleConstants.PERCENTAGE_OF_GRANDTOTAL.
    **/
   public void setPercentageType(int percentageType) {
      this.percentageType = percentageType;
   }

   /**
    * Set the total used to calculate percentage.
    * if percentage type is PERCENTAGE_NONE, it is ineffective to
    * invoke the method.
    **/
   public void setTotal(Object total) {
      this.total = total;
   }

   /**
    * Get the original formula result without percentage.
    */
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
      return Catalog.getCatalog().getString("Average");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.AVERAGE_FORMULA;
   }

   @Override
   public Class getResultType() {
      return Double.class;
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

   private int col; // second column
   private int percentageType = 0;
   private Object total = null;
   private double sum = 0;
   private double cnt = 0;
   private boolean def;
}
