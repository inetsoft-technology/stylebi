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
package inetsoft.graph.treemap;

/**
 * Layout using the pivot-by-size algorithm.
 * <p>
 * This is essentially a wrapper class for the OrderedTreemap class.
 */
public class PivotBySize implements MapLayout {
   OrderedTreemap orderedTreemap;

   public PivotBySize() {
      orderedTreemap = new OrderedTreemap();
      orderedTreemap.setPivotType(OrderedTreemap.PIVOT_BY_BIGGEST);
   }

   public void layout(MapModel model, Rect bounds) {
      orderedTreemap.layout(model, bounds);
   }

   public String getName() {
      return "Pivot by Size / Ben B.";
   }

   public String getDescription() {
      return "Pivot by Size, with stopping conditions " +
         "added by Ben Bederson.";
   }
}
