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

import inetsoft.uql.XConstants;
import inetsoft.util.Catalog;

/**
 * Calculate the mode of all values.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ModeFormula extends NthMostFrequentFormula {
   /**
    * Create a formula to get the most frequent value in a collection.
    */
   public ModeFormula() {
      super(1);
   }

   @Override
   public Object clone() {
      return super.clone();
   }

   /**
    * Get formula display name.
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("Mode");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.MODE_FORMULA;
   }
}

