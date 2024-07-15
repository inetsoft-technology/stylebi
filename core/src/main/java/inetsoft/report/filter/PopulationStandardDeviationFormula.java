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

import inetsoft.report.StyleConstants;
import inetsoft.uql.XConstants;
import inetsoft.util.Catalog;

/**
 * Calculate the population standard deviation.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class PopulationStandardDeviationFormula extends
   PopulationVarianceFormula
{
   /**
    * Get the formula result.
    */
   @Override
   public Object getResult() {
      int type = getPercentageType();
      setPercentageType(StyleConstants.PERCENTAGE_NONE);
      Double val = (Double) super.getResult();

      if(val != null) {
         val = Double.valueOf(Math.sqrt(val.doubleValue()));
      }

      setPercentageType(type);

      if(val != null) {
         double result = val.doubleValue();

         if(getPercentageType() != 0) {
            return getPercentage(result);
         }

         return Double.valueOf(result);
      }

      return null;
   }

   /**
    * Get the formula double result.
    */
   @Override
   public double getDoubleResult() {
      int type = getPercentageType();
      setPercentageType(StyleConstants.PERCENTAGE_NONE);
      double val = Math.sqrt(super.getDoubleResult());
      setPercentageType(type);

      if(getPercentageType() != 0) {
         Double percentage = getPercentage(val);
         return percentage == null ? 0 : percentage.doubleValue();
      }

      return val;
   }

   @Override
   public Object clone() {
      return super.clone();
   }

   @Override
   protected VarianceFormula createVarianceFormula() {
      return new PopulationStandardDeviationFormula();
   }

   /**
    * Get formula display name.
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("PopulationStandardDeviation");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.POPULATIONSTANDARDDEVIATION_FORMULA;
   }

   @Override
   public Class getResultType() {
      return Double.class;
   }
}

