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

import inetsoft.report.composition.execution.VSCubeTableLens;
import inetsoft.uql.erm.DataRef;

import java.util.Comparator;

/**
 * Object comparison for cube dimension.
 *
 * @version 10.1, 3/30/2009
 * @author InetSoft Technology Corp
 */
public class DimensionSortOrder extends SortOrder {
   /**
    * Constructor.
    * @param type type of order, one of SORT_ASC, SORT_DESC,
    * SORT_ORIGINAL and SORT_SPECIFIC.
    * @param dimType the specified dimension type.
    */
   public DimensionSortOrder(int type, int dimType, Comparator comp) {
      super(type);

      this.dimType = dimType;
      this.comp = comp;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(Object v1, Object v2) {
      if((dimType & DataRef.MEASURE) != DataRef.MEASURE) {
         v1 = VSCubeTableLens.getComparableObject(v1, dimType);
         v2 = VSCubeTableLens.getComparableObject(v2, dimType);
      }

      if(comp != null) {
         int result = comp.compare(v1, v2);
         return isAsc() ? result : -result;
      }

      return super.compare(v1, v2);
   }

   private int dimType;
   private Comparator comp;
}
