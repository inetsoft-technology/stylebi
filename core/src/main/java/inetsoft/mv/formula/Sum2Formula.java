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
import inetsoft.util.Catalog;
import inetsoft.util.Tool;

/**
 * The sum of two columns (only if both values are not null).
 *
 * @version 13.2, 9/20/2020
 * @author InetSoft Technology Corp
 */
public class Sum2Formula implements Formula2, java.io.Serializable {
   /**
    * Constructor.
    */
   public Sum2Formula() {
      super();
   }

   /**
    * Create a sum formula.
    * @param col column number of the column to calculate sum with.
    */
   public Sum2Formula(int col) {
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
      total = 0;
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
      if(v[0] == Tool.NULL_DOUBLE || v[1] == Tool.NULL_DOUBLE) {
         return;
      }

      total += v[0];
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

         if(pair[0] == null || pair[1] == null) {
            return;
         }

         double xV = (pair[0] instanceof Number)
            ? ((Number) pair[0]).doubleValue()
            : Double.parseDouble(pair[0] + "");

         total += xV;
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
      try {
         return (total != 0) ? Double.valueOf(total) :
            (def ? Double.valueOf(0) : null);
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
      return total != 0 ? total : 0;
   }

   /**
    * Check if the result is null.
    */
   @Override
   public boolean isNull() {
      return total == 0;
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
      return Catalog.getCatalog().getString("Sum2Formula");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return "Sum2";
   }

   @Override
   public Class getResultType() {
      return Double.class;
   }

   private double total = 0;
   private int col; // 2nd column
   private boolean def;
}
