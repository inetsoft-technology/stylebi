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
package inetsoft.graph.treeviz.tree.circlemap;

import java.util.Comparator;

/**
 * Compares two circles by the size of their radius.
 *
 * @author Werner Randelshofer
 * @version 1.0 Jan 17, 2008 Created.
 */
public class CircleRadiusComparator implements Comparator<Circle> {
   private static CircleRadiusComparator ascendingInstance;
   private static CircleRadiusComparator descendingInstance;
   private int asc = 1;

   public static CircleRadiusComparator getAscendingInstance() {
      if(ascendingInstance == null) {
         ascendingInstance = new CircleRadiusComparator();
      }
      return ascendingInstance;
   }

   public static CircleRadiusComparator getDescendingInstance() {
      if(descendingInstance == null) {
         descendingInstance = new CircleRadiusComparator();
         descendingInstance.asc = -1;
      }
      return descendingInstance;
   }


   public int compare(Circle c1, Circle c2) {
      double cmp = c1.radius - c2.radius;
      return (cmp < 0) ? -asc : ((cmp > 0) ? asc : 0);
   }
}
