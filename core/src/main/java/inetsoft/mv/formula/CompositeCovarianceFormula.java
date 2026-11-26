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
import inetsoft.util.Tool;

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
      sumwt_c1_c2 = 0;
      cnt_c1 = 0;
      sum_c1 = 0;
      sum_c2 = 0;
   }

   @Override
   public void addValue(double v) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Add the sub-aggregates of a sub-group, in the order of
    * AggregateFormula.COVARIANCE.getSubAggregates(): SUMWT(x, y), PAIRSUM(x, y),
    * PAIRSUM(y, x), PAIRCOUNT(x, y), all over the rows where both x and y are not null
    * (Bug #82375). For a covariance of a column with itself the second sum is left out
    * (3 values). A null sub-aggregate (Tool.NULL_DOUBLE, no row in the sub-group) adds 0.
    */
   @Override
   public void addValue(double[] v) {
      if(v == null || v.length < 3) {
         return;
      }

      int n = v.length == 3 ? 2 : 3;
      add(v[0], v[1], v.length == 3 ? v[1] : v[2], v[n]);
   }

   private void add(double sumwt, double sumx, double sumy, double cnt) {
      sumwt_c1_c2 += value(sumwt);
      sum_c1 += value(sumx);
      sum_c2 += value(sumy);
      cnt_c1 += value(cnt);
   }

   private static double value(double val) {
      return val == Tool.NULL_DOUBLE ? 0 : val;
   }

   @Override
   public void addValue(float v) {
      throw new RuntimeException("Unsupported method called!");
   }

   @Override
   public void addValue(long v) {
      throw new RuntimeException("Unsupported method called!");
   }

   @Override
   public void addValue(int v) {
      throw new RuntimeException("Unsupported method called!");
   }

   @Override
   public void addValue(short v) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Add the sub-aggregates as objects, in the same order as addValue(double[]).
    */
   @Override
   public void addValue(Object v) {
      if(!(v instanceof Object[] data) || data.length < 3) {
         return;
      }

      try {
         double[] vals = new double[data.length];

         for(int i = 0; i < data.length; i++) {
            vals[i] = data[i] == null ? Tool.NULL_DOUBLE : (data[i] instanceof Number)
               ? ((Number) data[i]).doubleValue() : Double.parseDouble(data[i] + "");
         }

         addValue(vals);
      }
      catch(NumberFormatException ignore) {
         // a sub-aggregate that is not a number, the sub-group is skipped
      }
   }

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
      return cnt_c1 > 0 ? Double.valueOf(covariance()) : (def ? Double.valueOf(0) : null);
   }

   @Override
   public double getDoubleResult() {
      return cnt_c1 > 0 ? covariance() : 0;
   }

   @Override
   public boolean isNull() {
      return cnt_c1 <= 0;
   }

   private double covariance() {
      return (sumwt_c1_c2 / cnt_c1) - ((sum_c1 * sum_c2) / (cnt_c1 * cnt_c1));
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
   // count of the pairs, a double so that the square does not overflow
   private double cnt_c1 = 0;
   private double sum_c1 = 0;
   private double sum_c2 = 0;
   private int[] cols;
   private boolean def;
}
