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
package inetsoft.graph.element;

import com.inetsoft.build.tern.*;
import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.VisualModel;
import inetsoft.graph.coord.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.SortedDataSet;
import inetsoft.graph.geometry.IntervalGeometry;
import inetsoft.graph.geometry.Pie3DGeometry;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;

import java.util.*;

/**
 * An interval element is used to add bar or range visualization to a graph.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=IntervalElement")
public class IntervalElement extends StackableElement {
   /**
    * Create an empty element. Dims and vars must be added explicitly.
    */
   public IntervalElement() {
   }

   /**
    * Create an interval element for a single column (1d).
    */
   public IntervalElement(String field1) {
      // super() can't be called since 'bases' has not been init'ed
      addVar(field1);
   }

   /**
    * Create an interval element for two columns (2d).
    */
   public IntervalElement(String field1, String field2) {
      // super() can't be called since 'bases' has not been init'ed
      addDim(field1);
      addVar(field2);
   }

   /**
    * Create an interval element for three columns (3d).
    */
   @TernConstructor
   public IntervalElement(String field1, String field2, String field3) {
      // super() can't be called since 'bases' has not been init'ed
      addDim(field1);
      addDim(field2);
      addVar(field3);
   }

   // set dodge so bars don't overlap
   {
      setCollisionModifier(DODGE_SYMMETRIC);
   }

   /**
    * Add a variable to be plotted using this element.
    * @param col the variable identifier.
    */
   @Override
   @TernMethod
   public void addVar(String col) {
      addInterval(null, col);
   }

   /**
    * Remove the variable at the specified index.
    * @param idx the dim index.
    */
   @Override
   @TernMethod
   public void removeVar(int idx) {
      super.removeVar(idx);
      bases.remove(idx);
   }

   /**
    * Set the variable for the base of the interval.
    */
   @TernMethod
   public void setBaseVar(String base) {
      for(int i = 0; i < bases.size(); i++) {
         bases.set(i, base);
      }
   }

   /**
    * Get the interval base variable.
    */
   @TernMethod
   public String getBaseVar(int i) {
      return i < bases.size() ? bases.get(i) : null;
   }

   /**
    * Add an interval to this element.
    * @param col1 the lower bound of the interval.
    * @param col2 the upper bound of the interval.
    */
   @TernMethod
   public void addInterval(String col1, String col2) {
      super.addVar(col2);

      if(col2 != null) {
         bases.add(col1);
      }
   }

   /**
    * Remove all variables.
    */
   @TernMethod
   @Override
   public void clearVars() {
      super.clearVars();
      bases.clear();
   }

   /**
    * Get all the variables.
    */
   @TernMethod
   @Override
   public String[] getVars() {
      List<String> list = new ArrayList();

      for(int i = 0; i < getVarCount(); i++) {
         list.add(getVar(i));
      }

      for(String obj : bases) {
         if(obj != null) {
            list.add(obj);
         }
      }

      return list.toArray(new String[list.size()]);
   }

   /**
    * Create the geometry objects for the chartLens.
    * @hidden
    * @param data the chartLens to plot using this element.
    * @param graph the containing graph.
    */
   @Override
   public void createGeometry(DataSet data, GGraph graph) {
      Coordinate coord = graph.getCoordinate();

      if(is3DPie(coord)) {
         create3DPie(data, graph);
         return;
      }

      boolean stackGroup = isStackGroup();
      boolean stack = isStack();

      // sort data to paint bar from left to right. This is required to
      // paint 3d bar and stack bar properly
      SortedDataSet sdata = sortData(data, graph);

      if(sdata != null) {
         data = sdata;
      }

      VisualModel vmodel = createVisualModel(data);
      double top = Double.NaN, negtop = 0;
      double groupIdx = 0;
      Vector geoms = new Vector(); // geometry objects
      int max = getEndRow(data);
      boolean negGrp = isStackNegative();
      boolean first = true; // first bar on a stack

      for(int i = getStartRow(data); i < max; i++) {
         if(!isAccepted(data, i)) {
            continue;
         }

         for(int v = 0; v < getVarCount(); v++) {
            double[] tuple = scale(data, i, v, graph);

            if(tuple == null) {
               continue;
            }

            String vname = getVar(v);
            Scale scale = getOrCreateScale(graph, vname, data);
            String basename = bases.get(v);
            IntervalGeometry gobj = null;
            double interval = 0;
            double[] vtuple;
            double base = Math.max(0, scale.getMin());

            // min of max for reversed scale for reversed scale
            if(scale.getMin() > scale.getMax()) {
               base = Math.min(base, Math.max(0, scale.getMax()));
            }

            // top is the current base for stacking
            if(Double.isNaN(top)) {
               top = base;
               first = true;
            }

            if(basename != null) {
               base = scale.map(data.getData(basename, i));
               // if base is specified, it should always be used regardless
               // whether stacking negative values
               top = negtop = base;
               first = true;
               addGeometries(graph, geoms);
            }

            double interval2;

            switch(coord.getDimCount()) {
            case 1:
               interval = coord.getValue(tuple, 0);
               double from = (interval < base && negGrp) ? negtop : top;
               vtuple = new double[] {getBase(scale, from)};
               interval2 = getInterval(scale, from, interval, base, first);
               gobj = new IntervalGeometry(this, graph, vname, i, vmodel, vtuple, interval2);
               break;
            case 2:
               if(stackGroup && groupIdx != coord.getValue(tuple, 0)) {
                  if(negGrp) {
                     if(base < 0) {
                        negtop = base;
                        top = 0;
                     }
                     else {
                        top = base;
                        negtop = 0;
                     }
                  }
                  else {
                     top = base;
                  }

                  addGeometries(graph, geoms);
                  groupIdx = coord.getValue(tuple, 0);
                  first = true;
               }

               interval = coord.getValue(tuple, 1);
               from = (interval < base && negGrp) ? negtop : top;
               vtuple = new double[] {coord.getValue(tuple, 0), getBase(scale, from)};
               interval2 = getInterval(scale, from, interval, base, first);
               gobj = new IntervalGeometry(this, graph, vname, i, vmodel, vtuple, interval2);
               break;
            case 3:
               interval = coord.getValue(tuple, 2);
               from = (interval < base && negGrp) ? negtop : top;
               interval2 = getInterval(scale, from, interval, base, first);

               vtuple = new double[] {
                  coord.getValue(tuple, 0),
                  coord.getValue(tuple, 1),
                  getBase(scale, from),
               };
               gobj = new IntervalGeometry(this, graph, vname, i, vmodel, vtuple, interval2);
               break;
            default:
               throw new RuntimeException(
                  "Unsupported number of dimensions: " + tuple.length);
            }

            if(stack) {
               if(interval < base && negGrp) {
                  //negtop = scale.add(negtop, interval2);
                  negtop = negtop + interval2;
               }
               else {
                  // we shouldn't need to use scale.add() here. The value for
                  // top and interval2 are the actual distance on the plot,
                  // after the values are already mapped in scale. using
                  // scale.add() cause the result to be wrong. This is because
                  // the add() assumes interval is the value from the base, but
                  // the interval here is actually the value from the previous
                  // top.
                  //top = scale.add(top, interval2);
                  top = top + interval2;
               }
            }

            int ridx = sdata == null ? i : sdata.getBaseRow(i);
            gobj.setSubRowIndex(ridx);
            gobj.setRowIndex(getRootRowIndex(data, i));
            gobj.setColIndex(getRootColIndex(data, vname));

            if(stack) {
               first = false;
            }

            if(interval < base && negGrp) {
               geoms.add(0, gobj);
            }
            else {
               geoms.add(gobj);
            }
         }
      }

      addGeometries(graph, geoms);
   }

   /**
    * Add geometry objects to ggraph.
    */
   private void addGeometries(GGraph graph, Vector geoms) {
      // add shape from the bottom to top (for 3D bar painting)
      for(int k = 0; k < geoms.size(); k++) {
         IntervalGeometry gobj = (IntervalGeometry) geoms.get(k);
         graph.addGeometry(gobj);
      }

      geoms.clear();
   }

   /**
    * Create the geometry objects for the chartLens.
    * @param data the chartLens to plot using this element.
    * @param graph the containing graph.
    */
   private void create3DPie(DataSet data, GGraph graph) {
      VisualModel vmodel = createVisualModel(data);
      SortedDataSet sdata = sortData(data, graph);
      data = getSortedDataSetInRange(data, sdata);

      boolean stack = isStack();
      Coordinate coord = graph.getCoordinate();
      double top = 0;
      Pie3DGeometry[] pieobjs = new Pie3DGeometry[getVarCount()];
      Vector<Integer>[] rowsv = new Vector[pieobjs.length];

      for(int v = 0; v < getVarCount(); v++) {
         pieobjs[v] = new Pie3DGeometry(this, graph, getVar(v), vmodel);
         rowsv[v] = new Vector();
      }

      int max = getEndRow(data);

      for(int i = getStartRow(data); i < max; i++) {
         if(!isAccepted(data, i)) {
            continue;
         }

         for(int v = 0; v < getVarCount(); v++) {
            double[] tuple = scale(data, i, v, graph);

            if(tuple == null) {
               continue;
            }

            String basename = bases.get(v);
            String vname = getVar(v);
            Scale scale = getOrCreateScale(graph.getEGraph(), vname, data);
            double base = Math.max(0, scale.getMin());

            if(basename != null) {
               base = scale.map(data.getData(basename, i));
               top = base;
            }

            switch(coord.getDimCount()) {
            case 1:
               pieobjs[v].addTuple(new double[] {getBase(scale, top)});
               pieobjs[v].addInterval(coord.getValue(tuple, 0) - base);
               break;
            case 2:
               pieobjs[v].addTuple(new double[] {coord.getValue(tuple, 0),
                                                 getBase(scale, top)});
               pieobjs[v].addInterval(coord.getValue(tuple, 1) - base);
               break;
            case 3:
               pieobjs[v].addTuple(new double[] {coord.getValue(tuple, 0),
                                                 coord.getValue(tuple, 1)});
               pieobjs[v].addInterval(coord.getValue(tuple, 2) - base);
               break;
            default:
               throw new RuntimeException(
                  "Unsupported number of dimensions: " + tuple.length);
            }

            if(stack) {
               top = scale.add(top, pieobjs[v].getInterval(pieobjs[v].getTupleCount() - 1));
            }

            rowsv[v].add(i);
         }
      }

      for(int i = 0; i < getVarCount(); i++) {
         int[] srows = new int[rowsv[i].size()];
         int[] rows = new int[rowsv[i].size()];

         for(int k = 0; k < rows.length; k++) {
            rows[k] = rowsv[i].get(k);
            srows[k] = sdata == null ? rows[k] :  sdata.getBaseRow(rows[k]);
         }

         pieobjs[i].setSubRowIndexes(srows); // sub row
         pieobjs[i].setRowIndexes(getRootRowIndexes(data, rows)); // root row
         pieobjs[i].setColIndex(getRootColIndex(data, pieobjs[i].getVar()));
         graph.addGeometry(pieobjs[i]);
      }
   }

   /**
    * Check if this is a 3d pie.
    */
   private boolean is3DPie(Coordinate coord) {
      if(!(coord instanceof PolarCoord)) {
         return false;
      }

      return ((PolarCoord) coord).getCoordinate() instanceof Rect25Coord;
   }

   /**
    * Get the base of the interval.
    * @param base the specified start point.
    */
   private double getBase(Scale scale, double base) {
      double min = scale.getMin();
      double max = scale.getMax();

      // for reversed scale
      if(min > max) {
         base = Math.max(base, max);
      }
      else {
         base = min > 0 ? Math.max(base, min) : base;
      }

      return base;
   }

   /**
    * Get the interval value. It will be fixed with offset and scale.
    * @param from the specified start point.
    * @param interval the specified interval.
    * @param first true if the first bar on a stack.
    * @return the interval value.
    */
   static double getInterval(Scale scale, double from, double interval,
                             double base, boolean first)
   {
      // the position on the scale (mapped value) of the top of the interval
      double sd = 0;

      // the first bar on a stack should subtract base (if min is not 0)
      if(first) {
         sd = interval - base + from;
      }
      else {
         // for mapping from 2nd scale, when min is set to none-0
         if(scale instanceof LinearScale) {
            interval -= scale.map(0);
         }

         sd = scale.add(from, interval);
      }

      // @by larryl, in case of 2nd y axis, base value of 0 may map to a
      // non-zero value on y2, so the value for base may be different
      // from the actual 'from'
      return sd - scale.add(from, 0);
   }

   @Override
   @TernMethod
   public boolean supportsOverlay() {
      return true;
   }

   /**
    * Get the minimum height of bars.
    */
   @TernMethod
   public int getZeroHeight() {
      return zeroHeight;
   }

   /**
    * Set the minimum height of bars.
    * @param zeroHeight height for interval if actual interval is zero.
    */
   @TernMethod
   public void setZeroHeight(int zeroHeight) {
      this.zeroHeight = zeroHeight;
   }

   /**
    * Set whether the intervals are visually stacked. A bar may be stacked in value but
    * not visually stacked (e.g. waterfall).
    */
   @TernMethod
   public void setVisualStacked(Boolean stack) {
      this.visualStacked = stack;
   }

   /**
    * Check if the intervals are visually stacked.
    */
   @TernMethod
   public Boolean isVisualStacked() {
      return visualStacked;
   }

   @Override
   public SortedDataSet sortData(DataSet data, GGraph graph) {
      SortedDataSet sorted = super.sortData(data, graph);

      if(sorted != null) {
         // fill-with-zero may cause bar width to change. (53697)
         sorted.setIgnoreZeroCalc(true);
      }

      return sorted;
   }

   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      if(obj instanceof IntervalElement) {
         IntervalElement elem = (IntervalElement) obj;
         return bases.equals(elem.bases) && zeroHeight == elem.zeroHeight &&
            Objects.equals(visualStacked, elem.visualStacked);
      }

      return false;
   }

   private List<String> bases = new Vector<>(); // interval base columns
   private int zeroHeight = 0;
   private Boolean visualStacked;
   private static final long serialVersionUID = 1L;
}
