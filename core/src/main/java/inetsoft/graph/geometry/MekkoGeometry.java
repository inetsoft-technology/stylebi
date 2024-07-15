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
import inetsoft.graph.coord.MekkoCoord;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.MekkoVO;
import inetsoft.graph.visual.VisualObject;

import java.util.HashMap;
import java.util.Map;

/**
 * This represents a mekko area.
 *
 * @version 12.3
 * @author InetSoft Technology Corp
 */
public class MekkoGeometry extends ElementGeometry {
   public MekkoGeometry(GraphElement elem, GGraph graph, String var, int tidx, VisualModel vmodel,
                        double base, double fraction, double groupFraction, double[] tuple)
   {
      super(elem, graph, var, tidx, vmodel);
      this.base = base;
      this.fraction = fraction;
      this.groupFraction = groupFraction;
      this.tuple = tuple;
   }

   /**
    * Create a visual object to visualize this element.
    * @param coord the coordinate the visual object is plotted on.
    * @return the new visual object.
    */
   @Override
   public VisualObject createVisual(Coordinate coord) {
      MekkoVO vo = new MekkoVO(this, coord, base, fraction, groupFraction, tuple);
      vo.setColIndex(cidx);
      vo.setSubRowIndex(subridx);
      vo.setRowIndex(ridx);
      return vo;
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

   /**
    * Calculate group totals
    * @hidden
    */
   public static Map<Object,Double> calcGroupTotals(DataSet data, String dim, String var,
                                                    int start, int end, Coordinate coord)
   {
      Map<Object,Double> groupTotals = new HashMap<>();
      boolean nonull = coord instanceof MekkoCoord && coord.getScales()[0] != null &&
         (coord.getScales()[0].getScaleOption() & Scale.NO_NULL) != 0;

      for(int i = start; i < end; i++) {
         Object value = data.getData(var, i);
         Object group = data.getData(dim, i);

         if(nonull && group == null) {
            continue;
         }
         
         Double total = groupTotals.get(group);

         if(total == null) {
            groupTotals.put(group, total = 0.0);
         }

         if(!(value instanceof Number)) {
            continue;
         }

         // mekko doesn't really support negative number, just force to be positive
         groupTotals.put(group, total + Math.abs(((Number) value).doubleValue()));
      }

      return groupTotals;
   }

   @Override
   public void clearTuple() {
      tuple = null;
   }

   private int ridx; // row index
   private int subridx; // sub row index
   private short cidx; // column index
   private double fraction; // fraction (0-1) within the group
   private double base; // base fraction of this area
   private double groupFraction; // fraction of the group within total area
   private double[] tuple; // tuple with the x (group) value
}
