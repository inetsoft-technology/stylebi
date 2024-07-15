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
package inetsoft.report.composition.graph;

import java.util.Comparator;

/**
 * This class is a wrapper of Comparator.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
class ComparatorWrapper implements Comparator {
   /**
    * Constructor.
    * @param comp contained Comparator.
    */
   public ComparatorWrapper(Comparator comp) {
      this.comp = comp;
   }

   /**
    * This method should return > 0 if v1 is greater than v2, 0 if
    * v1 is equal to v2, or < 0 if v1 is less than v2.
    * It must handle null values for the comparison values.
    * @param v1 comparison value.
    * @param v2 comparison value.
    * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
    */
   @Override
   public int compare(Object v1, Object v2) {
      return comp.compare(v1, v2);
   }

   /**
    * Get contained Comparator.
    * @return contained Comparator.
    */
   public Comparator getComparator() {
      return comp;
   }

   private Comparator comp;
}