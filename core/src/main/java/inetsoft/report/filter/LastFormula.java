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
import inetsoft.util.Tool;

/**
 * Calculate the last value.
 *
 * @version 11.5, 2013
 * @author InetSoft Technology Corp
 */
public class LastFormula extends FirstFormula {
   /**
    * Constructor.
    */
   public LastFormula() {
      super();
   }

   /**
    * Create a last formula.
    * @param col column number of dimension/secondary column.
    */
   public LastFormula(int col) {
      super(col);
   }

   /**
    * Get formula display name.
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("LastFormula");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.LAST_FORMULA;
   }

   @Override
   protected int compare(Object v1, Object v2) {
      int v = Tool.compare(v1, v2);
      return -v;
   }

   @Override
   boolean isFirst() {
      return false;
   }
}
