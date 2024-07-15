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
package inetsoft.graph.guide.form;

import inetsoft.report.filter.Formula;

import java.io.Serializable;

/**
 * Parameters for the Graph Target which can be constant values or
 * a post-aggregate calculation
 */
public class TargetParameter implements Serializable {
   /**
    * Convenience constructor with double argument
    */
   public TargetParameter(double constValue) {
      this(null, constValue);
   }

   /**
    * Convenience constructor for non constants
    */
   public TargetParameter(Formula formula) {
      this(formula, 0);
   }

   /**
    * Create a new TargetParameter
    */
   public TargetParameter(Formula formula, double constValue) {
      setConstantValue(constValue);
      setFormula(formula);
   }

   /**
    * Convenience empty constructor.  Makes a pass-through with default value
    */
   public TargetParameter() {
      this(null, 0);
   }

   /**
    * @return the formula used to calculate the runtime value of the parameter
    */
   public Formula getFormula() {
      return formula;
   }

   /**
    * Get the value of the parameter, generally only useful for constant
    * @return constant parameter value
    */
   public double getConstantValue() {
      return constValue;
   }

   /**
    * Set the value of the parameter
    * @param newValue
    */
   public void setConstantValue(double newValue) {
      this.constValue = newValue;
   }

   /**
    * Set the formula for calculating the runtime value of the parameter
    * @param formula the formula to use
    */
   public void setFormula(Formula formula) {
      this.formula = formula;
   }

   /**
    * Calculate the runtime value of the parameter given the post aggregate data
    * @param postAggregateData aggregate values for calculating target.
    * @return The calculated value of the post aggregate formula
    */
   public double getRuntimeValue(double[] postAggregateData) {
      // If the formula is null, it's a pass-through parameter, so we can just
      // use the pass through value with no calculation required.
      if(formula == null) {
         return constValue;
      }

      // Make sure to reset the formula so we don't have old values in the calc
      formula.reset();

      // Add each value to the formula and calculate the result
      for(double d : postAggregateData) {
         formula.addValue(d);
      }

      return formula.getDoubleResult();
   }

   /**
    * Return a description of the value
    */
   public String toString() {
      String ret;

      // Use formula if exists, otherwise Dvalue
      if(formula != null) {
         ret = formula.getDisplayName();
      }
      else {
         ret = Double.toString(constValue);
      }

      // Trim if too long
      if(ret.length() > 20) {
         ret = ret.substring(0, 17) + "...";
      }

      return ret;
   }

   private Formula formula; // Parameter type
   // For constants, this represents the runtime value.
   private double constValue; // Parameter value
}
