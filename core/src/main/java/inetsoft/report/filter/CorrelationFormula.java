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
      xy = 0;
      x = 0;
      y = 0;
      x2 = 0; // x square
      y2 = 0; // y square
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

      n++;
      x += v[1];
      y += v[2];
      x2 += v[1] * v[1];
      y2 += v[2] * v[2];
      xy += v[1] * v[2];
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

         n++;
         x += xV.doubleValue();
         y += yV.doubleValue();
         x2 += xV.doubleValue() * xV.doubleValue();
         y2 += yV.doubleValue() * yV.doubleValue();
         xy += xV.doubleValue() * yV.doubleValue();
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
         double root = Math.sqrt((n * x2 - x * x) * (n * y2 - y * y));
         double val = (n * xy - x * y) / root;

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

      return (n * xy - x * y) / Math.sqrt((n * x2 - x * x) * (n * y2 - y * y));
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
   private double xy = 0;
   private double x = 0;
   private double y = 0;
   private double x2 = 0; // x square
   private double y2 = 0; // y square
   private int col; // 2nd column
   private boolean def;
}
