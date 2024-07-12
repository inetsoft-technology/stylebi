/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.mv.formula;

/**
 * Calculate the standard deviation.
 *
 * @author InetSoft Technology Corp
 */
public class CompositeStandardDeviationFormula
   extends CompositeVarianceFormula
{
   /**
    * Create a standard deviation formula.
    */
   public CompositeStandardDeviationFormula() {
      super();
   }

   /**
    * Create a standard deviation formula.
    * @param cols secondary columns used for this formula.
    */
   public CompositeStandardDeviationFormula(int[] cols) {
      super(cols);
   }

   /**
    * Get the formula result.
    */
   @Override
   public Object getResult() {
      Object val = super.getResult();

      return val == null ? val :
         Double.valueOf(Math.sqrt(((Number) val).doubleValue()));
   }

   /**
    * Get the formula double result.
    */
   @Override
   public double getDoubleResult() {
      double val = super.getDoubleResult();

      return Math.sqrt(val);
   }

   @Override
   public Class getResultType() {
      return Double.class;
   }
}
