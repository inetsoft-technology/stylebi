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

import inetsoft.report.filter.Formula;
import inetsoft.uql.XConstants;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import it.unimi.dsi.fastutil.doubles.DoubleOpenHashSet;

/**
 * This is an implementation for distinct count for MV only.
 *
 * @version 12.2, 1/20/2016
 * @author InetSoft Technology Corp
 */
public class DistinctCountFormula 
   implements Formula, MergeableFormula<DistinctCountFormula> 
{
   /**
    * Reset the formula to start over.
    */
   @Override
   public void reset() {
      set.clear();
   }

   /**
    * Add a value to the formula.
    */
   @Override
   public void addValue(Object v) {
      throw new RuntimeException("Only double value is supported.");
   }

   /**
    * Add a double value to the formula.
    */
   @Override
   public void addValue(double v) {
      if(v == Tool.NULL_DOUBLE) {
         return;
      }

      set.add(v);
   }

   /**
    * Add double values to the formula.
    */
   @Override
   public void addValue(double[] v) {
      if(v != null && v.length > 0) {
         addValue(v[0]);
      }
   }

   /**
    * Add a float value to the formula.
    */
   @Override
   public void addValue(float v) {
      if(v == Tool.NULL_FLOAT) {
         return;
      }

      set.add(v);
   }

   /**
    * Add a long value to the formula.
    */
   @Override
   public void addValue(long v) {
      if(v == Tool.NULL_LONG) {
         return;
      }

      set.add(v);
   }

   /**
    * Add an int value to the formula.
    */
   @Override
   public void addValue(int v) {
      if(v == Tool.NULL_INTEGER) {
         return;
      }

      set.add(v);
   }

   /**
    * Add a short value to the formula.
    */
   @Override
   public void addValue(short v) {
      if(v == Tool.NULL_SHORT) {
         return;
      }

      set.add(v);
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
      return set.size();
   }

   @Override
   public Class getResultType() {
      return Integer.class;
   }

   /**
    * Get the formula double result.
    */
   @Override
   public double getDoubleResult() {
      return set.size();
   }

   /**
    * Check if the result is null.
    */
   @Override
   public boolean isNull() {
      return false;
   }

   @Override
   public Object clone() {
      try {
         DistinctCountFormula dcf = (DistinctCountFormula) super.clone();
         dcf.set = set.clone();
         return dcf;
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
      return Catalog.getCatalog().getString("DistinctCount");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.DISTINCTCOUNT_FORMULA;
   }

   @Override
   public void merge(DistinctCountFormula v) {
      set.addAll(v.set);
   }

   private DoubleOpenHashSet set = new DoubleOpenHashSet();
   private boolean def;
}
