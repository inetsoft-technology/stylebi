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
import inetsoft.graph.element.SchemaElement;
import inetsoft.graph.schema.SchemaPainter;
import inetsoft.graph.visual.SchemaVO;
import inetsoft.graph.visual.VisualObject;

/**
 * A schema geomtry captures the information about a schema in a coordinate
 * space.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class SchemaGeometry extends ElementGeometry {
   /**
    * Create an instance of SchemaGeometry.
    * @param elem the source graph element.
    * @param vars the variable columns.
    * @param tidx the index of the tuple or the base index of tuples.
    * @param vmodel the visual aesthetic attributes.
    * @param tuple the tuple in logic space for this geometry.
    */
   public SchemaGeometry(GraphElement elem, GGraph graph, String[] vars, int tidx,
                         VisualModel vmodel, double[] tuple) {
      super(elem, graph, vars, tidx, vmodel);
      this.tuple = tuple;
      this.painter = ((SchemaElement) elem).getPainter();

      if(painter != null) {
         painter = (SchemaPainter) painter.clone();
      }
   }

   /**
    * Create a visual object to visualize this element.
    * @param coord the coordinate the visual object is plotted on.
    * @return the new visual object.
    */
   @Override
   public VisualObject createVisual(Coordinate coord) {
      painter.init(this, coord);
      SchemaVO vo = new SchemaVO(this, coord, painter);
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
    * Get the logical (scaled) values of the variable tuple of this geometry.
    * @return the logical values.
    */
   public double[] getVarTuple() {
      GraphElement elem = getElement();
      int dcnt = elem.getDimCount();
      double[] arr = new double[tuple.length - dcnt];
      System.arraycopy(tuple, dcnt, arr, 0, arr.length);
      return arr;
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
    * Set the root row index.
    * @param ridx the specified root row index.
    */
   public void setRowIndex(int ridx) {
      this.ridx = ridx;
   }

   /**
    * Get the root row index.
    * @return the root row index.
    */
   public int getRowIndex() {
      return ridx;
   }

   /**
    * Set the sub row index.
    */
   public void setSubRowIndex(int subridx) {
      this.subridx = subridx;
   }

   /**
    * Get the sub row index.
    * @return the sub row index.
    */
   public int getSubRowIndex() {
      return subridx;
   }

   private double[] tuple; // scaled value
   private int ridx; // root row index
   private int subridx; // sub row index
   private SchemaPainter painter; // schema painter;
   private short cidx; // root col
}
