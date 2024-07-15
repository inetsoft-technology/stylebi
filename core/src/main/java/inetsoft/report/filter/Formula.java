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

/**
 * This interface is implemented by all Formula objects. A formula is used
 * by table filters to calculate data. At beginning of the filtering process,
 * reset() is called on every formula object associated with the filter,
 * then addValue() is called zero or more times to add the data items.
 * At the end, getResult() is called to retrieve the calculated value.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface Formula extends Cloneable, java.io.Serializable {
   /**
    * Reset the formula to start over.
    */
   public void reset();

   /**
    * Add a value to the formula.
    */
   public void addValue(Object v);

   /**
    * Add a double value to the formula.
    */
   public void addValue(double v);

   /**
    * Add double values to the formula.
    */
   public void addValue(double[] vs);

   /**
    * Add a float value to the formula.
    */
   public void addValue(float v);

   /**
    * Add a long value to the formula.
    */
   public void addValue(long v);

   /**
    * Add an int value to the formula.
    */
   public void addValue(int v);

   /**
    * Add a short value to the formula.
    */
   public void addValue(short v);

   /**
    * Get the formula result.
    */
   public Object getResult();

   /**
    * Get the formula result.
    */
   public double getDoubleResult();

   /**
    * Check if the result is null.
    */
   public boolean isNull();

   /**
    * Clone this formula. This may or may not copy the values from this
    * formula.
    */
   public Object clone();

   /**
    * Get formula display name.
    */
   public String getDisplayName();

   /**
    * Get formula name.
    */
   public String getName();

   /**
    * Get the default result option of this formula.
    * @return <tt>true</tt> to use the default value of a formula if no
    * result, <tt>false</tt> to just return null.
    */
   public boolean isDefaultResult();

   /**
    * Set the default result option of this formula.
    * @param def <tt>true</tt> to use the default value of a formula if no
    * result, <tt>false</tt> to just return null.
    */
   public void setDefaultResult(boolean def);

   /**
    * Get the type of the formula result. Return null if it is the same type as the input.
    */
   default Class getResultType() {
      return null;
   }

   /**
    * Set the null as a special string.
    */
   static final String __NULL__ = "__INETSOFT_FORMULA_NULL__";
}
