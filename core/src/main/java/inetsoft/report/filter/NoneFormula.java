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
import inetsoft.util.Catalog;
import inetsoft.util.Tool;

/**
 * No-op formula.
 *
 * @version 11.1
 * @author InetSoft Technology Corp
 */
public class NoneFormula implements Formula {
   /**
    * Reset the formula to start over.
    */
   @Override
   public void reset() {
      val = null;
      dval = Tool.NULL_DOUBLE;
      fval = Tool.NULL_FLOAT;
      lval = Tool.NULL_LONG;
      ival = Tool.NULL_INTEGER;
      sval = Tool.NULL_SHORT;
   }

   /**
    * Add a value to the formula.
    */
   @Override
   public void addValue(Object v) {
      val = v;
   }

   /**
    * Add a double value to the formula.
    */
   @Override
   public void addValue(double v) {
      dval = v;
   }

   /**
    * Add double values to the formula.
    */
   @Override
   public void addValue(double[] v) {
      val = v;
   }

   /**
    * Add a float value to the formula.
    */
   @Override
   public void addValue(float v) {
      fval = v;
   }

   /**
    * Add a long value to the formula.
    */
   @Override
   public void addValue(long v) {
      lval = v;
   }

   /**
    * Add an int value to the formula.
    */
   @Override
   public void addValue(int v) {
      ival = v;
   }

   /**
    * Add a short value to the formula.
    */
   @Override
   public void addValue(short v) {
      sval = v;
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
      if(dval != Tool.NULL_DOUBLE) {
         return Double.valueOf(dval);
      }
      else if(fval != Tool.NULL_FLOAT) {
         return Float.valueOf(fval);
      }
      else if(lval != Tool.NULL_LONG) {
         return Long.valueOf(lval);
      }
      else if(ival != Tool.NULL_INTEGER) {
         return Integer.valueOf(ival);
      }
      else if(sval != Tool.NULL_SHORT) {
         return Short.valueOf(sval);
      }
      else {
         return val;
      }
   }

   /**
    * Get the formula result.
    */
   @Override
   public double getDoubleResult() {
      return dval;
   }

   /**
    * Check if the result is null.
    */
   @Override
   public boolean isNull() {
      return getResult() == null;
   }

   /**
    * Clone this formula. This may or may not copy the values from this
    * formula.
    */
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
      return Catalog.getCatalog().getString("None");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.NONE_FORMULA;
   }

   private boolean def;
   private Object val;
   private double dval = Tool.NULL_DOUBLE;
   private float fval = Tool.NULL_FLOAT;
   private long lval = Tool.NULL_LONG;
   private int ival = Tool.NULL_INTEGER;
   private short sval = Tool.NULL_SHORT;
}
