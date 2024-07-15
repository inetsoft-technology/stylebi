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
package inetsoft.graph.guide.form;

import com.inetsoft.build.tern.TernMethod;
import inetsoft.graph.*;
import inetsoft.graph.coord.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.scale.Scale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.*;

/**
 * This is the base class for all form objects. A form object can be added to
 * a graph to add a drawing (or text) at any position.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class GraphForm extends Graphable {
   /**
    * Set the line styles for this guide.
    */
   @TernMethod
   public void setLine(int line) {
      this.line = line;
   }

   /**
    * Get the line styles for this guide.
    */
   @TernMethod
   public int getLine() {
      return line;
   }

   /**
    * Get the foreground color.
    */
   @TernMethod
   public Color getColor() {
      return color;
   }

   /**
    * Set the foreground color to a static color. this is a shortcut method for
    * setting a static color frame.
    */
   @TernMethod
   public void setColor(Color color) {
      this.color = color;
   }

   /**
    * Get the target alpha.
    */
   @TernMethod
   public int getAlpha() {
      return alpha;
   }

   /**
    * set the target alpha.
    */
   @TernMethod
   public void setAlpha(int alpha) {
      this.alpha = alpha;
   }
   /**
    * Get z-index property.
    */
   @TernMethod
   public int getZIndex() {
      return zIndex;
   }

   /**
    * The z-index property sets the stack order of a visual. A visual with
    * greater stack order is always in front of another visual with lower stack
    * order.
    */
   @TernMethod
   public void setZIndex(int zIndex) {
      this.zIndex = zIndex;
   }

   /**
    * Create visual objects to visualize this element.
    * @param coord the coordinate the visual object is plotted on.
    * @return the new visual objects.
    */
   public Visualizable[] createVisuals(Coordinate coord) {
      Visualizable vobj = createVisual(coord);

      return (vobj == null) ? new Visualizable[0] : new Visualizable[] {vobj};
   }

   /**
    * Set the measure this form is associated with. If set, the form is only
    * visualized on the graph that contains this measure.
    */
   @TernMethod
   public void setMeasure(String measure) {
      this.measure = measure;
   }

   /**
    * Get the measure this form is associated with.
    */
   @TernMethod
   public String getMeasure() {
      return measure;
   }

   /**
    * Set whether this form should be kept inside the plot area. If set to true
    * and the form extends outside of the plot, the plot area is scaled to push
    * the form object inside. It defaults to true if the position is specified
    * as fixed position.
    */
   @Override
   @TernMethod
   public void setInPlot(boolean inside) {
      this.inPlot = inside;
   }

   /**
    * Check if the form should be kept inside the plot area.
    */
   @Override
   @TernMethod
   public boolean isInPlot() {
      return (inPlot == null) ? !isFixedPosition() : inPlot.booleanValue();
   }

   /**
    * Set the offset to shift the shape in the chart. The shape is moved
    * right by the amount on X direction when it's painted from the original
    * specified positions.
    */
   @TernMethod
   public void setXOffset(int xoffset) {
      this.xoffset = xoffset;
   }

   /**
    * Get the x offset of the shape to shift in the chart.
    */
   @TernMethod
   public int getXOffset() {
      return xoffset;
   }

   /**
    * Set the offset to shift the shape in the chart. The shape is moved
    * up by the amount on Y direction when it's painted from the original
    * specified positions.
    */
   @TernMethod
   public void setYOffset(int yoffset) {
      this.yoffset = yoffset;
   }

   /**
    * Get the y offset of the shape to shift in the chart.
    */
   @TernMethod
   public int getYOffset() {
      return yoffset;
   }

   /**
    * Check if this form should be drawn in the coordinate.
    */
   public boolean isVisible(Coordinate coord) {
      // fixed position form only added once, to top vgraph
      if(isFixedPosition()) {
         return coord.getParentCoordinate() == null;
      }

      if(measure == null) {
         return true;
      }

      Scale[] scales = coord.getScales();

      for(int i = 0; i < scales.length; i++) {
         String smeasure = scales[i].getMeasure();

         if(measure.equals(smeasure)) {
            return true;
         }

         String[] fields = scales[i].getFields();

         for(int j = 0; j < fields.length; j++) {
            if(fields[j].equals(measure)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Create a visual object to visualize this element.
    * @param coord the coordinate the visual object is plotted on.
    * @return the new visual object.
    */
   public abstract Visualizable createVisual(Coordinate coord);

   /**
    * This method is called after FormVO has been generated.
    */
   public void layoutCompleted() {
   }

   /**
    * Apply scales to values in tuple.
    */
   protected double[] scale(Object[] tobj, Coordinate coord) {
      // need to get both x and y in RectCoord since target line form assumes x and y
      // both exist in getPosition() (and comparing against getNestTuple()). (52918)
      Scale[] scales = getAllScales(coord, true);
      double[] tuple = new double[tobj.length];

      // for a form, the tuple may include all scales so it only exist one
      // sub-graph in a facet, or it only include the inner-most scales so
      // it's added to all sub-graphs
      for(int i = tuple.length - 1, j = scales.length - 1; i >= 0; i--, j--) {
         if(j >= 0 && scales[j] != null) {
            tuple[i] = scales[j].map(tobj[i]);
         }
         else if(tobj[i] == Scale.MAX_VALUE) {
            tuple[i] = 1;
         }
         else {
            tuple[i] = 0;
         }
      }

      return tuple;
   }

   /**
    * Get all scales including the parent coordinates.
    * @param forceInnerXY true to include both X/Y in RectCord event if it's null.
    */
   Scale[] getAllScales(Coordinate coord, boolean forceInnerXY) {
      List<Scale> scales = new ArrayList();

      // get the scales for this coord, including nested coords
      while(coord != null) {
         if(!(coord instanceof FacetCoord)) {
            Scale[] arr = coord.getScales();
            // ignore secondary scale
            int dcnt = Math.min(arr.length, coord.getDimCount());

            if(forceInnerXY && coord instanceof RectCoord && dcnt < 2) {
               arr = new Scale[] { ((RectCoord) coord).getXScale(),
                                   ((RectCoord) coord).getYScale()};
               dcnt = 2;
            }

            for(int i = 0, n = 0; i < dcnt; i++) {
               // ignore the empty scale in facet, e.g. a fake
               // scale may be added if there is only one categorical
               // field bound to a facet
               if(arr[i] == null || arr[i].getFields().length > 0) {
                  scales.add(n++, arr[i]);
               }
            }

            // check if we should use secondary scale
            if(dcnt < arr.length && measure != null) {
               String[] lastfields = arr[arr.length - 1].getFields();

               if(Arrays.asList(lastfields).contains(measure)) {
                  scales.set(scales.size() - 1, arr[arr.length - 1]);
               }
            }
         }

         coord = coord.getParentCoordinate();
      }

      return scales.toArray(new Scale[scales.size()]);
   }

   /**
    * Get the position of the tuple.
    * @return position in the coord or null if the tuple is not in the coord.
    */
   protected Point2D getPosition(Coordinate coord, double[] tuple) {
      // need both x/y from RectCoord, because the comparison with getNestTuple()
      // result assumes x/y exists. (52918)
      Scale[] scales = getAllScales(coord, true);

      // if tuples are only specified for the innermost coord, include in all
      // sub-graph, otherwise only include in matching sub-graph
      if(scales.length == tuple.length) {
         double[] tuple0 = getNestTuple(coord);

         // check if the tuple is for this coord if it's nested
         for(int i = 0; i < tuple0.length && i < tuple.length; i++) {
            // in case the scale is a fake scale for facet, the tuple value
            // will be NaN, and we should ignore it (not treat it as null)
            if(!Double.isNaN(tuple[i]) && tuple[i] != tuple0[i]) {
               return null;
            }
         }
      }

      return coord.getPosition(tuple);
   }

   /**
    * Get the tuple for the parent coordinates that identify this nested coord.
    */
   private double[] getNestTuple(Coordinate coord) {
      List<Double> tuple = new ArrayList<>();

      while(coord != null) {
         // immediate parent is outer coord (rect)
         Coordinate pcoord = coord.getParentCoordinate();

         if(pcoord == null) {
            break;
         }

         // parent of outer is facet
         Coordinate gpcoord = pcoord.getParentCoordinate();

         if(gpcoord instanceof FacetCoord) {
            RectCoord outer = (RectCoord) pcoord;
            TileCoord[][] inners = ((FacetCoord) gpcoord).getTileCoords();

            for(int i = 0; i < inners.length; i++) {
               for(int j = 0; j < inners[i].length; j++) {
                  if(inners[i][j].containsCoordinate(coord)) {
                     Scale xscale = outer.getXScale();
                     Scale yscale = outer.getYScale();

                     // this is assuming the scales are categorical (fill)
                     if(yscale != null && yscale.getFields().length > 0) {
                        tuple.add(Double.valueOf(i));
                     }

                     if(xscale != null && xscale.getFields().length > 0) {
                        tuple.add(Double.valueOf(j));
                     }
                  }
               }
            }
         }

         coord = pcoord;
      }

      double[] tarr = new double[tuple.size()];

      for(int i = 0; i < tarr.length; i++) {
         tarr[i] = tuple.get(tuple.size() - i - 1);
      }

      return tarr;
   }

   /**
    * Check if form is at fixed position.
    */
   boolean isFixedPosition() {
      return false;
   }

   /**
    * Find the range of rows for this measure.
    * @param field measure column name.
    */
   protected int[] getRowRange(Coordinate coord, DataSet dset, String field) {
      if(field == null) {
         return null;
      }

      EGraph graph = coord.getVGraph().getEGraph();
      int startRow = Integer.MAX_VALUE;
      int endRow = 0;

      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);

         if(field != null && !Arrays.stream(elem.getVars()).anyMatch(v -> v.equals(field))) {
            continue;
         }

         startRow = Math.min(startRow, elem.getStartRow(dset));
         endRow = Math.max(endRow, elem.getEndRow(dset));
      }

      return (endRow > startRow) ? new int[] { startRow, endRow } : null;
   }

   /**
    * Check if equals another objects in structure.
    */
   public boolean equalsContent(Object obj) {
      if(obj == null || obj.getClass() != getClass()) {
         return false;
      }

      GraphForm form = (GraphForm) obj;
      return line == form.line &&
         zIndex == form.zIndex &&
         Objects.equals(color, form.color) &&
         alpha == form.alpha &&
         Objects.equals(measure, form.measure) &&
         Objects.equals(inPlot, form.inPlot) &&
         xoffset == form.xoffset &&
         yoffset == form.yoffset;
   }

   private int line = GraphConstants.THIN_LINE;
   private int zIndex = GDefaults.FORM_Z_INDEX;
   private Color color;
   private int alpha = 100;
   private String measure = null;
   private Boolean inPlot = null;
   private int xoffset = 0;
   private int yoffset = 0;

   private static final Logger LOG = LoggerFactory.getLogger(GraphForm.class);
}
