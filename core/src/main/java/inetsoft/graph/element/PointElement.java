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

import com.inetsoft.build.tern.*;
import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.LinearSizeFrame;
import inetsoft.graph.aesthetic.VisualModel;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.SortedDataSet;
import inetsoft.graph.geometry.PointGeometry;
import inetsoft.graph.scale.Scale;
import inetsoft.util.DefaultComparator;

import java.awt.*;
import java.util.*;

/**
 * A point element is used to add point visualization to a graph.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=PointElement")
public class PointElement extends StackableElement {
   /**
    * Create an empty element. Dims and vars must be added explicitly.
    */
   public PointElement() {
   }

   /**
    * Create a point element for a single column (1d).
    */
   public PointElement(String field1) {
      super(field1);
   }

   /**
    * Create a point element for two columns (2d).
    */
   public PointElement(String field1, String field2) {
      super(field1, field2);
   }

   /**
    * Create a point element for three columns (3d).
    */
   @TernConstructor
   public PointElement(String field1, String field2, String field3) {
      super(field1, field2, field3);
   }

   // default transparency
   {
      setHint(HINT_ALPHA, 0.8);
   }

   /**
    * Set whether to stack values of the points. By default, if stack is set as
    * the collision modifier for a point element, the points are plotted using
    * the original value, and moved to avoid collision afterward. If stackValue
    * is set to true, the point values are stacked in the same way as line or
    * area. The collision modifier has to set to STACK for this to be applied.
    */
   @TernMethod
   public void setStackValue(boolean stack) {
      this.stackValue = stack;
   }

   /**
    * Check if stacking values for the stack modifier.
    */
   @TernMethod
   public boolean isStackValue() {
      return stackValue;
   }

   @Override
   @TernMethod
   public boolean isStack() {
      return super.isStack() || stackValue;
   }

   /**
    * Set whether this point represent a word cloud (label). The shape should
    * be set to Nil to avoid a point being drawn. Size frame is applied to
    * label font size, and the labels mapped to the same point are laid out
    * in a circule layout.
    */
   @TernMethod
   public void setWordCloud(boolean wordCloud) {
      this.wordCloud = wordCloud;

      // word cloud labels could be outside and can't be removed, so
      // we need to clip to avoid it drawn out of plot
      setHint(GraphElement.HINT_CLIP, wordCloud + "");
      setHint(HINT_ALPHA, null);
   }

   /**
    * Check if this is a word cloud element.
    */
   @TernMethod
   public boolean isWordCloud() {
      return wordCloud;
   }

   /**
    * Set whether this is a (empty) point on a contour graph.
    */
   @TernMethod
   public void setContour(boolean contour) {
      this.contour = contour;
   }

   /**
    * Check if this is a (empty) point on a contour graph.
    */
   @TernMethod
   public boolean isContour() {
      return contour;
   }

   /**
    * Get the point outline color.
    */
   @TernMethod
   public Color getOutlineColor() {
      return outlineColor;
   }

   /**
    * Set the point outline color. If set, a outer border is drawn around the shape.
    */
   @TernMethod
   public void setOutlineColor(Color outlineColor) {
      this.outlineColor = outlineColor;
   }

   /**
    * Set whether data should be sorted for plotting. This is generally only necessary
    * if points need to be in the same order as line when they are plotted together.
    */
   @TernMethod
   public void setSortData(boolean sort) {
      this.sortData = sort;
   }

   /**
    * Check if data needs to be sorte.
    */
   @TernMethod
   public boolean isSortData() {
      return sortData;
   }

   /**
    * Get the scale to apply to the font size for word cloud. The scale is multipled with
    * the point size to generate the label font size. Higher value results in larger font
    * size for the largest label in the word cloud.
    */
   @TernMethod
   public double getFontScale() {
      return fontScale;
   }

   /**
    * Get the word cloud font scale.
    */
   @TernMethod
   public void setFontScale(double fontScale) {
      this.fontScale = fontScale;
   }

   /**
    * Create the geometry objects for the chartLens.
    * @hidden
    * @param data the chartLens to plot using this element.
    * @param graph the containing graph.
    */
   @Override
   public void createGeometry(DataSet data, GGraph graph) {
      SortedDataSet sdata = sortData(data, graph);
      data = getSortedDataSetInRange(data, sdata);

      VisualModel vmodel = createVisualModel(data);
      int max = getEndRow(data);
      boolean stack = isStack();
      PointTracker tracker = new PointTracker(this, graph, 20);

      if(getVarCount() == 0) {
         for(int i = getStartRow(data); i < max; i++) {
            if(!isAccepted(data, i)) {
               continue;
            }

            // if no var, that means the values are plotted on axes and we will
            // create one tuple for each dimension value instead of one per var
            for(int v = 0; v < getDimCount(); v++) {
               double[] tuple = scale(data, i, -(v + 1), graph);

               if(tuple == null) {
                  continue;
               }

               String vname = getDim(v);
               PointGeometry gobj = new PointGeometry(this, graph, vname, i, vmodel, tuple);
               int sidx = sdata == null ? i : sdata.getBaseRow(i);

               gobj.setSubRowIndex(sidx);
               gobj.setRowIndex(getRootRowIndex(data, i));
               gobj.setColIndex(getRootColIndex(data, vname));
               graph.addGeometry(gobj);
            }
         }
      }
      else if(!stack) {
         // mapping each variable
         for(int i = getStartRow(data); i < max; i++) {
            if(!isAccepted(data, i)) {
               continue;
            }

            for(int v = 0; v < getVarCount(); v++) {
               double[] tuple = scale(data, i, v, graph);

               if(tuple == null || !tracker.checkPoint(tuple, v)) {
                  continue;
               }

               String vname = getVar(v);
               PointGeometry gobj = new PointGeometry(this, graph, vname, i, vmodel, tuple);
               int sidx = sdata == null ? i : sdata.getBaseRow(i);

               gobj.setSubRowIndex(sidx);
               gobj.setRowIndex(getRootRowIndex(data, i));
               gobj.setColIndex(getRootColIndex(data, vname));
               graph.addGeometry(gobj);
            }
         }
      }
      else {
         Map<Object,Double> topmap = new HashMap<>();

         // mapping each variable
         for(int i = getStartRow(data); i < max; i++) {
            if(!isAccepted(data, i)) {
               continue;
            }

            for(int v = 0; v < getVarCount(); v++) {
               double[] tuple = scale(data, i, v, graph);
               String vname = getVar(v);
               Scale scale = null;

               // support stack line for line element
               if(vname != null) {
                  scale = graph.getScale(vname);
               }

               if(tuple == null || !tracker.checkPoint(tuple, v)) {
                  continue;
               }

               Object topkey = getTopKey(tuple);
               Double top = topmap.get(topkey);

               if(top == null) {
                  top = tuple[tuple.length - 1];
               }
               else if(scale != null) {
                  double interval = IntervalElement.getInterval(
                     scale, top, tuple[tuple.length - 1], 0, false);
                  top = scale.add(top, interval);
               }

               tuple[tuple.length - 1] = top;
               topmap.put(topkey, top);

               PointGeometry gobj = new PointGeometry(this, graph, vname, i, vmodel, tuple);
               int sidx = sdata == null ? i : sdata.getBaseRow(i);

               gobj.setSubRowIndex(sidx);
               gobj.setRowIndex(getRootRowIndex(data, i));
               gobj.setColIndex(getRootColIndex(data, vname));
               graph.addGeometry(gobj);
            }
         }
      }
   }

   /**
    * Sort dataset to prepare for generating visual objects.
    */
   @Override
   public SortedDataSet sortData(DataSet data, GGraph graph) {
      // should match sorting of line for line-showing-points. (52455)
      SortedDataSet sdata = sortData ? super.sortData(data, graph, isStack()) : null;

      // sort to make small bubble on top
      if(getSizeFrame() instanceof LinearSizeFrame) {
         if(sdata == null) {
            sdata = createSortedDataSet(data, getSizeFrame().getField());
         }
         else {
            sdata.addSortColumn(getSizeFrame().getField(), false);
         }

         DefaultComparator comp = new DefaultComparator();
         comp.setNegate(true);
         sdata.setComparator(getSizeFrame().getField(), comp);
      }

      return sdata;
   }

   /**
    * Get a key the uniquely identifies a top value for stacking.
    */
   private Object getTopKey(double[] tuple) {
      boolean negGrp = isStackNegative();
      boolean stackGroup = isStackGroup();
      StringBuilder key = new StringBuilder();

      if(stackGroup && tuple.length >= 2) {
         key.append(tuple[tuple.length - 2]);
      }

      key.append(":");

      if(negGrp) {
         if(tuple[tuple.length - 1] >= 0) {
            key.append("p");
         }
         else {
            key.append("n");
         }
      }

      return key.toString();
   }

   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      if(obj instanceof PointElement) {
         PointElement elem = (PointElement) obj;
         return stackValue == elem.stackValue && wordCloud == elem.wordCloud &&
            contour == elem.contour && sortData == elem.sortData && fontScale == elem.fontScale &&
            Objects.equals(outlineColor, elem.outlineColor);
      }

      return false;
   }

   private boolean stackValue = false;
   private boolean wordCloud = false;
   private boolean contour = false;
   private boolean sortData = true;
   private Color outlineColor = null;
   private double fontScale;
   private static final long serialVersionUID = 1L;
}
