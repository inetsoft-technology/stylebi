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

import inetsoft.report.filter.Formula2;
import inetsoft.uql.XConstants;
import inetsoft.util.Catalog;

/**
 * Calculate the variance.
 *
 * @author InetSoft Technology Corp
 */
public class CompositeVarianceFormula implements Formula2 {
   /**
    * Create a variance formula.
    */
   public CompositeVarianceFormula() {
      super();
   }

   /**
    * Create a variance formula.
    * @param cols secondary columns used for this formula.
    */
   public CompositeVarianceFormula(int[] cols) {
      this();
      this.cols = cols;
   }

   /**
    * Get the secondary columns
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
      cnt = 0;
      sumsq = 0;
      sum = 0;
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
      if(v == null || v.length < 3) {
         return;
      }

      cnt += v[0];
      sumsq += v[1];
      sum += v[2];
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

      int ocnt = cnt;
      double osumsq = sumsq;
      double osum = sum;

      try {
         Object[] data = (Object[]) v;
         // result = SIGMA(xV * yV) / SIGMA(yV);
         Number val1 = (data[0] instanceof Number) ?
            (Number) data[0] : Double.parseDouble(data[0] + "");
         Number val2 = (data[1] instanceof Number) ?
            (Number) data[1] : Double.parseDouble(data[1] + "");
         Number val3 = (data[2] instanceof Number) ?
            (Number) data[2] : Double.parseDouble(data[2] + "");

         if(val1 == null || val2 == null || val3 == null) {
            return;
         }

         cnt += val1.intValue();
         sumsq += val2.doubleValue();
         sum += val3.doubleValue();
      }
      catch(Exception e) {
         cnt = ocnt;
         sumsq = osumsq;
         sum = osum;
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
      if(cnt <= 1) {
         return def ? Double.valueOf(0) : null;
      }

      try {
         /*
         if("db2".equals(dbType)) {
            return Double.valueOf(((cnt * sumsq) - (sum * sum)) / (cnt * cnt));
         }
         */

         return Double.valueOf(((cnt * sumsq) - (sum * sum)) / (cnt * (cnt - 1)));
      }
      catch(Throwable e) {
         return null;
      }
   }

   /**
    * Get the formula double result.
    */
   @Override
   public double getDoubleResult() {
      if(cnt == 0) {
         return 0;
      }

      /*
      if("db2".equals(dbType)) {
         return ((cnt * sumsq) - (sum * sum)) / (cnt * cnt);
      }
      */

      return ((cnt * sumsq) - (sum * sum)) / (cnt * (cnt - 1));
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
      return Catalog.getCatalog().getString("VarianceFormula");
   }

   /**
    * Set the datebase type.
    * @param dbType the datebase type.
    */
   public void setDBType(String dbType) {
      this.dbType = dbType;
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.VARIANCE_FORMULA;
   }

   @Override
   public Class getResultType() {
      return Double.class;
   }

   protected int cnt = 0;
   protected double sumsq = 0;
   protected double sum = 0;
   protected boolean def = true; // be consistent with VarianceFormula
   protected String dbType;
   private int[] cols;
}
