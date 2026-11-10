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

import inetsoft.uql.XConstants;
import inetsoft.util.*;

/**
 * Calculate the correlation of two columns.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class CorrelationFormula implements Formula2, java.io.Serializable {
   /**
    * Create a correlation formula.
    */
   public CorrelationFormula() {
      super();
   }

   /**
    * Create a correlation formula.
    * @param col column number of the column to calculate correlation with.
    */
   public CorrelationFormula(int col) {
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
      n = 0;
      meanX = 0;
      meanY = 0;
      m2x = 0;
      m2y = 0;
      cxy = 0;
   }

   /**
    * Add one (x, y) pair. Uses the streaming (Welford) update of the means and the centered
    * sums of squares/products, so the result depends on the spread of the values and not on
    * their magnitude. The raw sums (n * sum(x^2) - sum(x)^2) cancel catastrophically when the
    * values are large relative to their spread (Bug #80950).
    */
   private void add(double xv, double yv) {
      n++;
      double dx = xv - meanX;
      double dy = yv - meanY;
      meanX += dx / n;
      meanY += dy / n;
      m2x += dx * (xv - meanX);
      m2y += dy * (yv - meanY);
      cxy += dx * (yv - meanY);
   }

   /**
    * Get the correlation coefficient, NaN if either column has no variance.
    */
   private double correlation() {
      double prod = m2x * m2y;
      // sqrt of the product keeps exact results (e.g. -1) for small values; fall back to the
      // separate roots only when the product underflows to 0 or overflows
      double root = prod > 0 && !Double.isInfinite(prod) ? Math.sqrt(prod)
         : Math.sqrt(m2x) * Math.sqrt(m2y);
      return cxy / root;
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
      if(v == null || v.length < 2 ||
         v[1] == Tool.NULL_DOUBLE || v[2] == Tool.NULL_DOUBLE)
      {
         return;
      }

      add(v[1], v[2]);
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

      try {
         Object[] pair = (Object[]) v;
         Number xV = (pair[0] instanceof Number) ? (Number) pair[0]
            : NumberParserWrapper.getDouble(pair[0] + "");
         Number yV = (pair[1] instanceof Number) ? (Number) pair[1]
            : NumberParserWrapper.getDouble(pair[1] + "");

         if(xV == null || yV == null) {
            return;
         }

         add(xV.doubleValue(), yV.doubleValue());
      }
      catch(NumberFormatException e) {
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
      if(n == 0) {
         return def ? Double.valueOf(0) : null;
      }

      try {
         double val = correlation();

         if(Double.isNaN(val)) {
            return def ? (double) 0 : null;
         }

         return val;
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
      if(n == 0) {
         return 0;
      }

      return correlation();
   }

   /**
    * Check if the result is null.
    */
   @Override
   public boolean isNull() {
      return n == 0;
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
      return Catalog.getCatalog().getString("Correlation");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.CORRELATION_FORMULA;
   }

   @Override
   public String toString() {
      return super.toString() + "(" + col + ")";
   }

   @Override
   public Class getResultType() {
      return Double.class;
   }

   private double n = 0;
   private double meanX = 0;
   private double meanY = 0;
   private double m2x = 0; // sum of squared deviations of x from its mean
   private double m2y = 0; // sum of squared deviations of y from its mean
   private double cxy = 0; // sum of products of deviations
   private int col; // 2nd column
   private boolean def;
}
