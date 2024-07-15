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

/**
 * Calculate the variance.
 *
 * @author InetSoft Technology Corp
 */
public class CompositePopulationVarianceFormula extends CompositeVarianceFormula
{
   /**
    * Create a variance formula.
    */
   public CompositePopulationVarianceFormula() {
      super();
   }

   /**
    * Create a variance formula.
    * @param cols secondary columns used for this formula.
    */
   public CompositePopulationVarianceFormula(int[] cols) {
      super(cols);
   }

   /**
    * Get the formula result.
    */
   @Override
   public Object getResult() {
      if(cnt == 0) {
         return def ? Double.valueOf(0) : null;
      }

      try {
         return Double.valueOf(((cnt * sumsq) - (sum * sum)) / (cnt * cnt));
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
      if(cnt == 0) {
         return 0;
      }

      return ((cnt * sumsq) - (sum * sum)) / (cnt * cnt);
   }

   /**
    * Check if the result is null.
    */
   @Override
   public boolean isNull() {
      return cnt == 0;
   }

   @Override
   public Class getResultType() {
      return Double.class;
   }
}
