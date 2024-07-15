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

import com.inetsoft.build.tern.TernMethod;
import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.SortedDataSet;
import inetsoft.graph.scale.Scale;
import inetsoft.report.composition.graph.ValueOrderComparer;

import java.util.*;

/**
 * A stackable element stacks visual objects, either at each tick position, or
 * continuously through the whole series.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class StackableElement extends GraphElement {
   /**
    * Create an empty element. Dims and vars must be added explicitly.
    */
   protected StackableElement() {
   }

   /**
    * Create a point element for a single column (1d).
    */
   protected StackableElement(String field1) {
      super(field1);
   }

   /**
    * Create a point element for two columns (2d).
    */
   protected StackableElement(String field1, String field2) {
      super(field1, field2);
   }

   /**
    * Create a point element for three columns (3d).
    */
   protected StackableElement(String field1, String field2, String field3) {
      super(field1, field2, field3);
   }

   /**
    * Get whether to stack by groups.
    */
   @TernMethod
   public boolean isStackGroup() {
      return stackGroup;
   }

   /**
    * Set whether to stack by groups.
    */
   @TernMethod
   public void setStackGroup(boolean stackGroup) {
      this.stackGroup = stackGroup;
   }

   /**
    * Check if negative values are stacked separately.
    */
   @TernMethod
   public boolean isStackNegative() {
      return negGrp;
   }

   /**
    * Set whether negative values are stacked separately. If true (default), the
    * negative values are stacked downward and positive values are stacked
    * upward. Otherwise, all values are accumulated together.
    */
   @TernMethod
   public void setStackNegative(boolean negGrp) {
      this.negGrp = negGrp;
   }

   /**
    * Sort dataset to prepare for generating visual objects.
    */
   @Override
   public SortedDataSet sortData(DataSet data, GGraph graph) {
      return sortData(data, graph, false);
   }

   /**
    * Sort dataset to prepare for generating visual objects.
    */
   public SortedDataSet sortData(DataSet data, GGraph graph, boolean isStack) {
      SortedDataSet sdata = null;
      Set sorted = new HashSet(); // sorted columns

      // force the line to be sorted (if the sorting is none) so it's plotted from left to right.
      // radar is sorted in LineElement.sortGeometry() so it doesn't need to be sorted. (56815)
      if(getDimCount() > 0 && getVarCount() > 0) {
         String field = getDim(getDimCount() - 1);
         final Scale scale = (graph != null) ? graph.getScale(field) : null;

         sdata = createSortedDataSet(data, field);
         Comparator comp = data.getComparator(field);

         if(comp == null && scale != null) {
            comp = (v1, v2) -> {
               double rc1 = scale.map(v1);
               double rc2 = scale.map(v2);

               if(Double.isNaN(rc1)) {
                  if(Double.isNaN(rc2)) {
                     return 0;
                  }
                  else {
                     return -1;
                  }
               }
               else if(Double.isNaN(rc2)) {
                  return 1;
               }

               return Double.compare(rc1, rc2);
            };
            sdata.setComparator(field, comp);
         }
      }

      // all outer dimension sorting are done in facet and shouldn't be added
      // to sorting here
      for(int i = 0; i < getDimCount(); i++) {
         sorted.add(getDim(i));
      }

      // the priority if from back to front
      VisualFrame[] frames = {
         getTextFrame(),
         getSizeFrame(),
         getShapeFrame(), getTextureFrame(), getLineFrame(),
         getColorFrame(),
      };
      List<VisualFrame> sframes = new Vector<>();

      for(VisualFrame frame : frames) {
         if(frame instanceof CategoricalFrame) {
            sframes.add(frame);
         }
         else if(frame instanceof CompositeVisualFrame) {
            CompositeVisualFrame cframe = (CompositeVisualFrame) frame;

            for(int i = 0; i < cframe.getFrameCount(); i++) {
               VisualFrame sframe = cframe.getFrame(i);

               if(sframe instanceof CategoricalFrame) {
                  sframes.add(sframe);
               }
            }
         }
      }

      for(VisualFrame frame : sframes) {
         String vfld = frame.getField();

         if(vfld != null && data.getComparator(vfld) != null && !sorted.contains(vfld)) {
            sorted.add(vfld);

            if(sdata == null) {
               sdata = createSortedDataSet(data, vfld);
            }
            else {
               // @by stephenwebster, For Bug #16734
               // When sorting a stacked line or area graph with a value comparer
               // change to ignore the value, but instead sort the entire table.
               // This is a stop-gap solution to give a more natural (but arbitrary)
               // sorting which ensures that the line/area is stacked correctly.
               if(isStack && sdata.getComparator(vfld) instanceof ValueOrderComparer) {
                  sdata.addSortColumn(vfld, false);
               }
               else {
                  sdata.addSortColumn(vfld, true);
               }
            }
         }
      }

      // add any column that has a comparator but not sorted to enforce the
      // order set in the dataset (e.g. VSDataSet.setOrder).
      final int colCnt = data.getColCount();
      for(int i = 0; i < colCnt; i++) {
         String vfld = data.getHeader(i);

         if(!sorted.contains(vfld) && data.getComparator(vfld) != null) {
            if(sdata == null) {
               sdata = createSortedDataSet(data, vfld);
            }
            else {
               if(isStack && sdata.getComparator(vfld) instanceof ValueOrderComparer) {
                  sdata.addSortColumn(vfld, false);
               }
               else {
                  sdata.addSortColumn(vfld, true);
               }
            }
         }
      }

      return sdata;
   }

   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      if(obj instanceof StackableElement) {
         StackableElement elem = (StackableElement) obj;
         return stackGroup == elem.stackGroup && negGrp == elem.negGrp;
      }

      return false;
   }

   private boolean stackGroup = false;
   private boolean negGrp = true;
}
