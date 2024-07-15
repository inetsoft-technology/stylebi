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

import inetsoft.util.NumberParserWrapper;
import inetsoft.util.Tool;

/**
 * Cube measure formula.
 *
 * @version 10.1, 5/12/2009
 * @author InetSoft Technology Corp
 */
public class CubeMeasureFormula extends NullFormula {
   /**
    * Add a value to the formula.
    */
   @Override
   public void addValue(Object v) {
      if(v == null) {
         return;
      }

      try {
         sum += (v instanceof Number) ? ((Number) v).doubleValue() :
            NumberParserWrapper.getDouble(v.toString());
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

      sum += v;
   }

   /**
    * Add double values to the formula.
    */
   @Override
   public void addValue(double[] v) {
      if(v[0] == Tool.NULL_DOUBLE) {
         return;
      }

      sum += v[0];
   }

   /**
    * Add a float value to the formula.
    */
   @Override
   public void addValue(float v) {
      if(v == Tool.NULL_FLOAT) {
         return;
      }

      sum += v;
   }

   /**
    * Add a long value to the formula.
    */
   @Override
   public void addValue(long v) {
      if(v == Tool.NULL_LONG) {
         return;
      }

      sum += v;
   }

   /**
    * Add an int value to the formula.
    */
   @Override
   public void addValue(int v) {
      if(v == Tool.NULL_INTEGER) {
         return;
      }

      sum += v;
   }

   /**
    * Add a short value to the formula.
    */
   @Override
   public void addValue(short v) {
      if(v == Tool.NULL_SHORT) {
         return;
      }

      sum += v;
   }

   /**
    * Get the formula result.
    */
   @Override
   public Object getResult() {
      return Double.valueOf(sum);
   }

   /**
    * Get the formula result.
    */
   @Override
   public double getDoubleResult() {
      return sum;
   }

   /**
    * Check if the result is null.
    */
   @Override
   public boolean isNull() {
      return false;
   }

   /**
    * Reset the formula to start over.
    */
   @Override
   public void reset() {
      sum = 0;
   }

   /**
    * Clone this formula. This may or may not copy the values from this
    * formula.
    */
   @Override
   public Object clone() {
      CubeMeasureFormula cformula = new CubeMeasureFormula();
      cformula.addValue(getResult());

      return cformula;
   }

   @Override
   public Class getResultType() {
      return Double.class;
   }

   private double sum = 0;
}
