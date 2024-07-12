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
package inetsoft.graph.geometry;

import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.VisualModel;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.visual.PolygonVO;
import inetsoft.graph.visual.VisualObject;

/**
 * A polygon geomtry captures the information about a polygon in a coordinate
 * space.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class PolygonGeometry extends ElementGeometry {
   /**
    * Create a polygon in a 2D coordinate.
    * @param elem the source graph element.
    * @param var the name of the variable column.
    * @param tidx the index of the tuple or the base index of tuples.
    * @param vmodel the visual aesthetic attributes.
    * @param tuple the tuple in logic space for this geometry.
    */
   public PolygonGeometry(GraphElement elem, GGraph graph, String var, int tidx,
			  VisualModel vmodel, double[] tuple)
   {
      super(elem, graph, var, tidx, vmodel);
      this.tuple = tuple;
   }

   /**
    * Create a visual object to visualize this element.
    * @param coord the coordinate the visual object is plotted on.
    * @return the new visual object.
    */
   @Override
   public VisualObject createVisual(Coordinate coord) {
      PolygonVO vo = new PolygonVO(this, coord);
      vo.setColIndex(cidx);
      vo.setSubRowIndex(subridx);
      vo.setRowIndex(ridx);

      return vo;
   }

   /**
    * Get the logical (scaled) values of the tuple of this geometry.
    * @return the logical values.
    */
   public double[] getTuple() {
      return tuple;
   }

   /**
    * Get the col index.
    * @return col index.
    */
   public int getColIndex() {
      return cidx;
   }

   /**
    * Set the col index.
    */
   public void setColIndex(int cidx) {
      this.cidx = (short) cidx;
   }

   /**
    * Set row index.
    */
   public void setRowIndex(int ridx) {
      this.ridx = ridx;
   }

   /**
    * Get row index.
    */
   public int getRowIndex() {
      return ridx;
   }

   /**
    * Set sub row index.
    */
   public void setSubRowIndex(int subridx) {
      this.subridx = subridx;
   }

   /**
    * Get sub row index.
    */
   public int getSubRowIndex() {
      return subridx;
   }

   @Override
   public void clearTuple() {
      tuple = null;
   }

   private double[] tuple;
   private int ridx; // row index
   private int subridx; // sub row index
   private short cidx; // column index
}
