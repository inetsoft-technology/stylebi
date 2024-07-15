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
package inetsoft.graph.geometry;

import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.visual.VisualObject;

/**
 * A geometry defines the geometric object to be plotted on a graph.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class Geometry implements Comparable {
   /**
    * Create a visual object to visualize this element.
    * @param coord the coordinate the visual object is plotted on.
    * @return the new visual object.
    */
   public abstract VisualObject createVisual(Coordinate coord);

   /**
    * Compare to another object.
    */
   @Override
   public int compareTo(Object obj) {
      return 0;
   }
}
