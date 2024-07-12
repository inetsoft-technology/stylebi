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

import java.util.Comparator;

/**
 * Dimension comparer.
 */
public class DimensionComparer extends DefaultComparer {
   /**
    * Constructor.
    */
   public DimensionComparer(int dimType) {
      super();

      this.dimType = dimType;
   }

   /**
    * Constructor.
    */
   public DimensionComparer(int dimType, Comparator comp) {
      this(dimType);

      this.comp = comp;
   }

   /**
    * Setter of Comparator.
    * @param comp the specified Comparator.
    */
   public void setComparator(Comparator comp) {
      this.comp = comp;
   }

   /**
    * Gettter of Comparator.
    * @return Comparator if any.
    */
   public Comparator getComparator() {
      return comp;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(Object v1, Object v2) {
      v1 = VSCubeTableLens.getComparableObject(v1, dimType);
      v2 = VSCubeTableLens.getComparableObject(v2, dimType);

      if(comp != null) {
         return getSign() * comp.compare(v1, v2);
      }

      return super.compare(v1, v2);
   }

   private int dimType;
   private Comparator comp;
}