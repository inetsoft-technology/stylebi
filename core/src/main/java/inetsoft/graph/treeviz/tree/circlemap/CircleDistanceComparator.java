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
 * Compares two circles by their distance to the origin (0,0) of the coordinate
 * system.
 *
 * @author Werner Randelshofer
 * @version 1.0 Jan 17, 2008 Created.
 */
public class CircleDistanceComparator implements Comparator<Circle> {

   private double cx, cy;

   public CircleDistanceComparator(double cx, double cy) {
      this.cx = cx;
      this.cy = cy;
   }

   public int compare(Circle c1, Circle c2) {
      double qdist1 =
         (cx - c1.cx) * (cx - c1.cx) +
            (cy - c1.cy) * (cy - c1.cy);
      double qdist2 =
         (cx - c2.cx) * (cx - c2.cx) +
            (cy - c2.cy) * (cy - c2.cy);
      double cmp = qdist1 - qdist2;
      return (cmp < 0) ? -1 : ((cmp > 0) ? 1 : 0);
   }
}
