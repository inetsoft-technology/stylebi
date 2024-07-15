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

import inetsoft.report.filter.Formula2;
import inetsoft.uql.XConstants;
import inetsoft.util.Catalog;

/**
 * Calculate the covariance.
 *
 * @author InetSoft Technology Corp
 */
public class CompositeCovarianceFormula implements Formula2 {
   /**
    * Create a covariance formula.
    */
   public CompositeCovarianceFormula() {
      super();
   }

   /**
    * Create a covariance formula.
    * @param cols indexs of columns used for this formula
    */
   public CompositeCovarianceFormula(int[] cols) {
      this.cols = cols;
   }

   /**
    * Get the secondary columns.
    */
   @Override
   public int[] getSecondaryColumns() {
      return cols;
   }

   /**
    * Reset the formula to start over.
    */
   @Override
   public void reset() {
      sumwt_c1_c2= 0;
      cnt_c1 = 0;
      sum_c1 = 0;
      sum_c2 = 0;
      cnt = 0;
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
      if(v == null || v.length < 4) {
         return;
      }

      cnt++;
      sumwt_c1_c2 += v[0];
      sum_c1 += v[1];
      sum_c2 += v[2];
      cnt_c1 += v[3];
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

      double osumwt_c1_c2 = sumwt_c1_c2;
      int ocnt_c1 = cnt_c1;
      double osum_c1 = sum_c1;
      double osum_c2 = sum_c2;

      try {
         Object[] data = (Object[]) v;
         Number val1 = (data[0] instanceof Number) ?
            (Number) data[0] : Double.parseDouble(data[0] + "");
         Number val2 = (data[1] instanceof Number) ?
            (Number) data[1] : Double.parseDouble(data[1] + "");
         int idx = data.length == 3 ? 1 : 2;
         Number val3 = (data[idx] instanceof Number) ?
            (Number) data[idx] : Double.parseDouble(data[idx] + "");
         idx = data.length == 3 ? 2 : 3;
         Number val4 = (data[idx] instanceof Number) ?
            (Number) data[idx] : Double.parseDouble(data[idx] + "");

         if(val1 == null || val2 == null || val3 == null || val4 == null) {
            return;
         }

         cnt++;
         sumwt_c1_c2 += val1.doubleValue();
         sum_c1 += val2.intValue();
         sum_c2 += val3.intValue();
         cnt_c1 += val4.intValue();
      }
      catch(NumberFormatException nfe) {
         sumwt_c1_c2 = osumwt_c1_c2;
         cnt_c1 = ocnt_c1;
         sum_c1 = osum_c1;
         sum_c2 = osum_c2;
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
      if(cnt == 0) {
         return def ? Double.valueOf(0) : null;
      }

      try {
         return (cnt_c1 != 0) ?
            Double.valueOf((sumwt_c1_c2 / cnt_c1) - ((sum_c1 * sum_c2) /
                                                 (cnt_c1 * cnt_c1)))
            : (def ? Double.valueOf(0) : null);
      }
      catch(Throwable e) {
         return null;
      }
   }

   /**
    * Get the formula result.
    */
   @Override
   public double getDoubleResult() {
      if(cnt == 0) {
         return 0;
      }

      return cnt_c1 != 0 ?
         (sumwt_c1_c2 / cnt_c1) - ((sum_c1 * sum_c2) / (cnt_c1 * cnt_c1)) : 0;
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
    * Get formula display name.
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("CovarianceFormula");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.COVARIANCE_FORMULA;
   }

   @Override
   public Class getResultType() {
      return Double.class;
   }

   private double sumwt_c1_c2 = 0;
   private int cnt_c1 = 0;
   private double sum_c1 = 0;
   private double sum_c2 = 0;
   private int cnt = 0;
   private int[] cols;
   private boolean def;
}
