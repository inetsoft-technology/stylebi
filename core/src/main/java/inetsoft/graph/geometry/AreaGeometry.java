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

import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.VisualModel;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.visual.AreaVO;
import inetsoft.graph.visual.LineVO;

import java.util.ArrayList;
import java.util.List;

/**
 * A area geomtry captures the information about a area in a coordinate
 * space.
 */
public class AreaGeometry extends LineGeometry {
   /**
    * Create a area geometry.
    * @param elem the source graph element.
    * @param var the name of the variable column.
    * @param vmodel the visual aesthetic attributes.
    */
   public AreaGeometry(GraphElement elem, GGraph graph, String var, VisualModel vmodel) {
      super(elem, graph, var, vmodel);
   }

   /**
    * Add a tuple for the base point of the area.
    * @param tuple each value is a position in the logic space.
    */
   public void addBaseTuple(double[] tuple) {
      basetuples.add(tuple);
   }

   /**
    * Remove the specified tuple.
    * @return the removed tuple.
    */
   @Override
   public double[] removeTuple(int idx) {
      basetuples.remove(idx);
      return super.removeTuple(idx);
   }

   /**
    * Get the base tuple.
    */
   public double[] getBaseTuple(int idx) {
      return basetuples.get(idx);
   }

   /**
    * Create the visual object.
    */
   @Override
   protected LineVO createVO(Coordinate coord, String[] vars, String mname) {
      return new AreaVO(this, coord, getSubRowIndexes(), vars, mname);
   }

   private List<double[]> basetuples = new ArrayList();
}
