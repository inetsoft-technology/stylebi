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
import inetsoft.graph.GraphConstants;
import inetsoft.graph.aesthetic.GShape;
import inetsoft.graph.aesthetic.VisualModel;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.ParaboxElement;
import inetsoft.graph.visual.ParaboxPointVO;
import inetsoft.graph.visual.VisualObject;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.awt.*;
import java.util.Arrays;
import java.util.Objects;

/**
 * A parabox geomtry captures represents a bubble (point) on a parabox diagram.
 *
 * @version 13.4
 * @author InetSoft Technology Corp
 */
public class ParaboxPointGeometry extends ElementGeometry {
   /**
    * Create a point in a 2D coordinate.
    * @param elem the source graph element.
    * @param var the name of the variable column.
    * @param tidx the index of the tuple or the base index of tuples.
    * @param vmodel the visual aesthetic attributes.
    * @param pval the value of this point (on the var axis).
    */
   public ParaboxPointGeometry(GraphElement elem, GGraph graph, String var, int tidx,
                               VisualModel vmodel, double pval)
   {
      super(elem, graph, var, tidx, vmodel);
      this.pval = pval;
   }

   /**
    * Get a tuple to be used by parallel coord scale.
    */
   public double[] getTuple() {
      return getTuple((ParaboxElement) getElement(), getVar(), pval);
   }

   // get a tuple with the value for var (and NaN for others).
   static double[] getTuple(ParaboxElement elem, String var, double val) {
      return Arrays.stream(elem.getParaboxFields())
         .mapToDouble(v -> Objects.equals(v, var) ? val : Double.NaN)
         .toArray();
   }

   @Override
   public VisualObject createVisual(Coordinate coord) {
      ParaboxPointVO vo = new ParaboxPointVO(this, coord);
      return vo;
   }

   /**
    * Set row index this corresopnds to in the top data set.
    */
   public void addRowIndex(int ridx) {
      this.ridxs.add(ridx);
   }

   /**
    * Get row indexes this corresopnds to in the top data set.
    */
   public int[] getRowIndexes() {
      return ridxs.toIntArray();
   }

   /**
    * Get the value of this point (of dimension).
    */
   public double getParaboxValue() {
      return pval;
   }

   @Override
   public Color getColor(int idx) {
      return getElement().getColorFrame() != null
         ? getElement().getColorFrame().getColor(weight) : super.getColor(idx);
   }

   @Override
   public double getSize(int idx) {
      return getElement().getSizeFrame() != null
         ? getElement().getSizeFrame().getSize(weight) : super.getSize(idx);
   }

   @Override
   public GShape getShape(int idx) {
      return getElement().getShapeFrame() != null
         ? getElement().getShapeFrame().getShape(weight) : super.getShape(idx);
   }

   /**
    * Get the bubble weight.
    */
   public Object getWeight() {
      return weight;
   }

   /**
    * Set the bubble weight.
    */
   public void setWeight(Object weight) {
      this.weight = weight;
   }

   @Override
   public int getLabelPlacement() {
      int pos = super.getLabelPlacement();
      ParaboxElement elem = (ParaboxElement) getElement();

      if(pos == GraphConstants.AUTO && elem.getParaboxFieldCount() > 0) {
         if(Objects.equals(getVar(), elem.getParaboxField(0))) {
            return GraphConstants.LEFT;
         }
         else if(Objects.equals(getVar(), elem.getParaboxField(elem.getParaboxFieldCount() - 1))) {
            return GraphConstants.RIGHT;
         }

         return GraphConstants.LEFT;
      }

      return pos;
   }

   private double pval;
   private Object weight;
   private IntSet ridxs = new IntArraySet(); // row indexes
}
