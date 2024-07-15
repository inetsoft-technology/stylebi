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
package inetsoft.report.filter;

import inetsoft.uql.XConstants;
import inetsoft.util.Catalog;

/**
 * No-op formula.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
class NullFormula implements Formula {
   /**
    * Reset the formula to start over.
    */
   @Override
   public void reset() {
   }

   /**
    * Add a value to the formula.
    */
   @Override
   public void addValue(Object v) {
   }

   /**
    * Add a double value to the formula.
    */
   @Override
   public void addValue(double v) {
   }

   /**
    * Add double values to the formula.
    */
   @Override
   public void addValue(double[] v) {
   }

   /**
    * Add a float value to the formula.
    */
   @Override
   public void addValue(float v) {
   }

   /**
    * Add a long value to the formula.
    */
   @Override
   public void addValue(long v) {
   }

   /**
    * Add an int value to the formula.
    */
   @Override
   public void addValue(int v) {
   }

   /**
    * Add a short value to the formula.
    */
   @Override
   public void addValue(short v) {
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
      return null;
   }

   /**
    * Get the formula result.
    */
   @Override
   public double getDoubleResult() {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Check if the result is null.
    */
   @Override
   public boolean isNull() {
      return true;
   }

   /**
    * Clone this formula. This may or may not copy the values from this
    * formula.
    */
   @Override
   public Object clone() {
      return this;
   }

   /**
    * Get formula display name.
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("Null");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.NONE_FORMULA;
   }

   private boolean def;
}
