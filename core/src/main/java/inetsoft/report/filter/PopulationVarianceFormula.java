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
 * Calculate the population variance.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class PopulationVarianceFormula extends VarianceFormula {
   public PopulationVarianceFormula() {
      super(true);
   }

   @Override
   public Object getResult() {
      Number val = (Number) super.getResult();

      if(val != null) {
         double result = val.doubleValue();

         if(getPercentageType() != 0) {
            return getPercentage(result);
         }

         return Double.valueOf(result);
      }

      return null;
   }

   @Override
   public double getDoubleResult() {
      double douResult = super.getDoubleResult();

      if(getPercentageType() != 0) {
         Double percentage = getPercentage(douResult);
         return percentage == null ? 0 : percentage.doubleValue();
      }

      return douResult;
   }

   @Override
   public Object clone() {
      return super.clone();
   }

   @Override
   protected VarianceFormula createVarianceFormula() {
      return new PopulationVarianceFormula();
   }

   /**
    * Get formula display name.
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("PopulationVarianceFormula");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.POPULATIONVARIANCE_FORMULA;
   }

   @Override
   public Class getResultType() {
      return Double.class;
   }
}

