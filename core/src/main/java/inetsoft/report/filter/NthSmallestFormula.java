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
package inetsoft.report.filter;

import inetsoft.uql.XConstants;
import inetsoft.util.Catalog;

/**
 * Calculate the nth smallest value in a column.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class NthSmallestFormula extends NthLargestFormula {
   /**
    * Create a formula to get the nth largest value.
    * @param n this value starts at 1 for the largest value.
    */
   public NthSmallestFormula(int n) {
      super(n);
   }

   public NthSmallestFormula() {
   }

   @Override
   public Object clone() {
      return super.clone();
   }

   @Override
   protected NthLargestFormula createNthFormula(int n) {
      return new NthSmallestFormula(n);
   }

   /**
    * Compare two values and return <0, 0, or >0 if the first value is less
    * than, equal to, or greater than the second value.
    */
   @Override
   int compare(Object v1, Object v2) {
      return super.compare(v2, v1);
   }

   /**
    * Get formula display name.
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("NthSmallest");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.NTHSMALLEST_FORMULA;
   }
}
