/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.graph.geometry;

import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.VisualModel;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.visual.Pie3DVO;
import inetsoft.graph.visual.VisualObject;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;

import java.util.Vector;

/**
 * A 3d pie geomtry captures the information about a range in a coordinate
 * space.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class Pie3DGeometry extends ElementGeometry {
   /**
    * Create an interval in a 1D coordinate.
    * @param elem the source graph element.
    * @param var the name of the variable column.
    * @param vmodel the visual aesthetic attributes.
    */
   public Pie3DGeometry(GraphElement elem, GGraph graph, String var, VisualModel vmodel) {
      super(elem, graph, var, 0, vmodel);
   }

   /**
    * Create a visual object to visualize this element.
    * @param coord the coordinate the visual object is plotted on.
    * @return the new visual object.
    */
   @Override
   public VisualObject createVisual(Coordinate coord) {
      Pie3DVO vo = new Pie3DVO(this, coord, subridxs);
      vo.setColIndex(cidx);
      vo.setSubRowIndexes(subridxs);
      vo.setRowIndexes(ridxs);

      return vo;
   }

   /**
    * Add a interval size to the gemotry.
    * @param interval the size of each arc.
    */
   public void addInterval(double interval) {
      intervals.add(interval);
   }

   /**
    * Get the interval size.
    * @return the interval size.
    */
   public double getInterval(int idx) {
      return intervals.getDouble(idx);
   }

   /**
    * Add a tuple to the line gemotry. Each tuple represent a point on the line.
    * @param tuple each value is a position in the logic space.
    */
   public void addTuple(double[] tuple) {
      tuples.add(tuple);
   }

   /**
    * Get the number of tuples in the line geometry.
    * @return the tuple count.
    */
   public int getTupleCount() {
      return tuples.size();
   }

   /**
    * Get the specified tuple.
    * @return the logical values.
    */
   public double[] getTuple(int idx) {
      return tuples.get(idx);
   }

   /**
    * Set row indexs.
    */
   public void setRowIndexes(int[] ridxs) {
      this.ridxs = ridxs;
   }

   /**
    * Get row indexs.
    */
   public int[] getRowIndexes() {
      return ridxs;
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
    * Set sub row indexs.
    */
   public void setSubRowIndexes(int[] subridxs) {
      this.subridxs = subridxs;
   }

   /**
    * Get sub row indexs.
    */
   public int[] getSubRowIndexes() {
      return subridxs;
   }

   @Override
   public void clearTuple() {
      tuples.clear();
   }

   private Vector<double[]> tuples = new Vector<>();
   private DoubleArrayList intervals = new DoubleArrayList();
   private int[] ridxs;
   private int[] subridxs;
   private short cidx;
}
