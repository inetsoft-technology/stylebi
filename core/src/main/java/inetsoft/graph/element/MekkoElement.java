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
package inetsoft.graph.element;

import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.SortedDataSet;
import inetsoft.graph.geometry.MekkoGeometry;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.Scale;

import java.util.*;

/**
 * This defines a marimekko chart element. It should be combined with a MekkoCoord to create
 * a marimekko graph.
 *
 * @version 12.3
 * @author InetSoft Technology Corp
 */
public class MekkoElement extends GraphElement {
   /**
    * Define an empty mekko element. X and y dimensions must be added using
    * addDim/addVar/setInnerDimension.
    */
   public MekkoElement() {
   }

   /**
    * Create a mekko element.
    * @param dim the x axis dimension.
    * @param dim2 the dimension used for stacking values.
    * @param var the measure column.
    */
   public MekkoElement(String dim, String dim2, String var) {
      addDim(dim);
      this.dim2 = dim2;
      addVar(var);
   }

   /**
    * Set the stacking dimension.
    */
   public void setInnerDimension(String dim2) {
      this.dim2 = dim2;
   }

   /**
    * Get the stacking dimension.
    */
   public String getInnerDimension() {
      return dim2;
   }

   @Override
   public void createGeometry(DataSet data, GGraph graph) {
      // not defined, just show blank
      if(getDimCount() == 0 || getVarCount() == 0) {
         return;
      }

      Coordinate coord = graph.getCoordinate();
      SortedDataSet sdata = sortData(data, graph);

      if(sdata != null) {
         data = getSortedDataSetInRange(data, sdata);
         // values must be sorted for the mekko chart to render properly
         sdata.setForceSort(true);
      }

      VisualModel vmodel = createVisualModel(data);
      int max = getEndRow(data);
      String vname = getVar(0);
      Map<Object,Double> groupTotals = MekkoGeometry.calcGroupTotals(
         data, getDim(0), vname, getStartRow(data), max, graph.getCoordinate());
      double allTotal = groupTotals.values().stream()
         .mapToDouble(v -> v != null ? v.doubleValue() : 0)
         .sum();
      Map<Object, Double> bases = new HashMap<>();

      for(int i = getStartRow(data); i < max; i++) {
         if(!isAccepted(data, i)) {
            continue;
         }

         Object group = data.getData(getDim(0), i);
         double[] tuple = scale(data, i, -1, graph);
         double base = bases.getOrDefault(group, (double) 0);
         Double total = groupTotals.get(group);
         Object value = data.getData(vname, i);

         if(value == null || total == null || tuple == null) {
            continue;
         }

         double fraction = value instanceof Number && total != 0
            // mekko doesn't really support negative number, just force to be positive
            ? Math.abs(((Number) value).doubleValue()) / total : 0;
         double groupFraction = total / allTotal;
         double[] vtuple = new double[] { coord.getValue(tuple, 0) };
         MekkoGeometry gobj = new MekkoGeometry(this, graph, vname, i, vmodel, base, fraction,
                                                groupFraction, vtuple);
         int sidx = sdata == null ? i : sdata.getBaseRow(i);

         gobj.setSubRowIndex(sidx);
         gobj.setRowIndex(getRootRowIndex(data, i));
         gobj.setColIndex(getRootColIndex(data, vname));
         graph.addGeometry(gobj);
         base += fraction;
         bases.put(group, base);
      }
   }

   // sort data by dimensions
   @Override
   public SortedDataSet sortData(DataSet data, GGraph graph) {
      SortedDataSet sdata = super.sortData(data, graph);

      if(getDim(0) == null) {
         return sdata;
      }

      if(sdata == null) {
         sdata = createSortedDataSet(data, getDim(0));
      }
      else {
         sdata.addSortColumn(getDim(0), false);
      }

      // when sort by value, we sort the value of the first stack instead of the total
      // across all stacks. this matches the behavior of bars. (51018)
      if(dim2 != null) {
         sdata.addSortColumn(dim2, true);
      }

      addAestheticDims(sdata);

      return sdata;
   }

   // find the secondary sorting column.
   private void addAestheticDims(SortedDataSet sdata) {
      Set sortedCols = new HashSet(Collections.singleton(sdata.getSortColumns()));
      VisualFrame[] frames = { getColorFrame(), getTextureFrame(), getTextFrame() };

      for(VisualFrame frame : frames) {
         if(!isSupportSort(frame)) {
            continue;
         }

         String field = frame.getField();

         if(!sortedCols.contains(field) && sdata.getComparator(field) != null) {
            sdata.addSortColumn(field, true);
            sortedCols.add(field);
         }
      }
   }

   private boolean isSupportSort(VisualFrame frame) {
      if(frame instanceof CategoricalFrame || frame instanceof CompositeVisualFrame) {
         return true;
      }

      if(frame instanceof ValueTextFrame) {
         TextFrame textFrame = ((ValueTextFrame) frame).getTextFrame();
         Scale textScale = textFrame.getScale();

         return textScale instanceof CategoricalScale;
      }

      return false;
   }

   @Override
   public boolean supportsOverlay() {
      return true;
   }

   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      if(obj instanceof MekkoElement) {
         MekkoElement elem = (MekkoElement) obj;
         return Objects.equals(dim2, elem.dim2);
      }

      return false;
   }

   private String dim2; // inner stacking dimension
   private static final long serialVersionUID = 1L;
}
