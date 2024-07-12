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
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.visual.LineVO;
import inetsoft.graph.visual.VisualObject;

import java.util.Vector;

/**
 * An line geomtry captures the information about a range in a coordinate
 * space.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class LineGeometry extends ElementGeometry {
   /**
    * Create a line geometry.
    * @param elem the source graph element.
    * @param var the name of the variable column.
    * @param vmodel the visual aesthetic attributes.
    */
   public LineGeometry(GraphElement elem, GGraph graph, String var, VisualModel vmodel) {
      super(elem, graph, var, 0, vmodel);
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
    * Remove the specified tuple.
    * @return the removed tuple.
    */
   public double[] removeTuple(int idx) {
      return tuples.remove(idx);
   }

   /**
    * Get the specified tuple.
    * @return the logical values.
    */
   public double[] getTuple(int idx) {
      return tuples.get(idx);
   }

   /**
    * Get the min X value of the line points.
    */
   public double getMinX(Coordinate coord) {
      return this.tuples.stream()
         .mapToDouble(t -> coord.getValue(t, 0)).min().orElse(Double.MAX_VALUE);
   }

   /**
    * Get the max X value of the line points.
    */
   public double getMaxX(Coordinate coord) {
      return this.tuples.stream()
         .mapToDouble(t -> coord.getValue(t, 0)).max().orElse(Double.MAX_VALUE);
   }

   /**
    * Create a visual object to visualize this element.
    * @param coord the coordinate the visual object is plotted on.
    */
   @Override
   public VisualObject createVisual(Coordinate coord) {
      String var = getVar();
      String[] vars = null;

      if(var == null) {
         GraphElement elem = getElement();
         int count = elem.getDimCount();
         DataSet data = coord.getDataSet();
         Vector variable = new Vector();
         vars = new String[] {};

         for(int i = 0; data != null && i < count; i++) {
            String dim = elem.getDim(i);

            if(data.getData(dim, pidxs[0]) != null) {
               variable.add(dim);

               // @by davyc, fix bug1240400720385, when set hyperlink for
               // nested radar, hyperlink no effect, because the measure
               // name for elementvo is setting by a dimension name
               if(data.isMeasure(dim)) {
                  var = dim;
               }
            }
            else {
               variable.add(null);
            }
         }

         vars = (String[]) variable.toArray(vars);
      }

      LineVO vo = createVO(coord, vars, var);
      vo.setColIndex(cidx);
      vo.setSubRowIndexes(pidxs);
      vo.setRowIndexes(ridxs);
      vo.setClosed(isClosed());

      return vo;
   }

   /**
    * Create the visual object.
    */
   protected LineVO createVO(Coordinate coord, String[] vars, String mname) {
      return new LineVO(this, coord, getSubRowIndexes(), vars, mname);
   }

   /**
    * Get the col index.
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
    * Set root row indexs.
    */
   public void setRowIndexes(int[] ridxs) {
      this.ridxs = ridxs;
   }

   /**
    * Get root row indexs.
    */
   public int[] getRowIndexes() {
      return ridxs;
   }

   /**
    * Set sub row index which to map the subset point on line.
    */
   public void setSubRowIndexes(int[] ridx) {
      this.pidxs = ridx;
   }

   /**
    * Get sub row index which to map the subset point on line.
    */
   public int[] getSubRowIndexes() {
      return pidxs;
   }

   /**
    * Set the index of the points within the dataset.
    */
   public void setTupleIndexes(int[] tidxs) {
      this.tidxs = tidxs;
   }

   /**
    * Get the index of the points within the dataset.
    */
   public int[] getTupleIndexes() {
      return tidxs;
   }

   /**
    * Set the line is closed(circle) or not.
    * @param isClosed the line is closed(circle) or not.
    */
   public void setClosed(boolean isClosed) {
      this.isClosed = isClosed;
   }

   /**
    * Check the line is closed(circle) or not.
    * @return <tt>true</tt> if line is closed, <tt>false</tt> otherwise.
    */
   public boolean isClosed() {
      return isClosed;
   }

   /**
    * Compare to another object.
    */
   @Override
   public int compareTo(Object obj) {
      if(!(obj instanceof LineGeometry)) {
         return 1;
      }

      // compare geometry to paint stack from top to bottom, so that
      // they will not get covered up
      // if closed (e.g. radar), we compare individual points and paint
      // the inner area on top
      LineGeometry line2 = (LineGeometry) obj;
      return comparePoints(line2);
   }

   // keep the tallest area in the back.
   private int compareMax(LineGeometry line2) {
      double ymax = tuples.stream().mapToDouble(t -> t[t.length - 1]).max().orElse(0);
      double ymax2 = line2.tuples.stream().mapToDouble(t -> t[t.length - 1]).max().orElse(0);

      // taller area should be drawn first so the shortest is in the front.
      return Double.compare(ymax2, ymax);
   }

   // draw in stacking order.
   private int comparePoints(LineGeometry line2) {
      final int tupleCount = getTupleCount();
      final int tupleCount2 = line2.getTupleCount();

      // compare point by point to find local max.
      // lines may have different number of points, so we find the points at same X location
      // to compare. (60590)
      for(int i1 = 0, i2 = 0; i1 < tupleCount && i2 < tupleCount2;) {
         final double[] tuple1 = tuples.get(i1);
         final double[] tuple2 = line2.tuples.get(i2);

         if(tuple1.length > 1 && tuple2.length > 1) {
            double x1 = tuple1[tuple1.length - 2];
            double x2 = tuple2[tuple2.length - 2];

            if(x1 != x2) {
               if(x1 < x2) {
                  i1++;
               }
               else {
                  i2++;
               }

               continue;
            }
         }

         final double y1 = tuple1[tuple1.length - 1];
         final double y2 = tuple2[tuple2.length - 1];

         // handle NaN (56815).
         if(Double.isNaN(y1)) {
            if(!Double.isNaN(y2)) {
               return 1;
            }
         }
         else if(Double.isNaN(y2)) {
            return -1;
         }
         else if(y1 != y2) {
            return y2 > y1 ? 1 : -1;
         }

         i1++;
         i2++;
      }

      return compareMax(line2);
   }

   @Override
   public void clearTuple() {
      // needed for radar tooltip. (54690)
      //tuples.clear();
   }

   private Vector<double[]> tuples = new Vector<>();
   private int[] ridxs; // root rows
   private int[] pidxs; // sub rows
   private int[] tidxs; // tuple indexes
   private boolean isClosed;
   private short cidx; // root col
}
