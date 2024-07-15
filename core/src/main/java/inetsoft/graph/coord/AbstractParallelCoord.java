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
package inetsoft.graph.coord;

import com.inetsoft.build.tern.TernMethod;
import inetsoft.graph.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DataSetIndex;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.*;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.*;

/**
 * A parallel coord consists of a set of parallel vertical axes that
 * plot data points on the axes.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class AbstractParallelCoord extends Coordinate {
   /**
    * Default constructor.
    */
   public AbstractParallelCoord() {
   }

   @Override
   @TernMethod
   public Scale[] getScales() {
      return getParallelScales();
   }

   /**
    * Get the scales for the parallel axes.
    */
   public abstract Scale[] getParallelScales();

   /**
    * Get the interval size in this coordinate.
    */
   @Override
   @TernMethod
   public double getIntervalSize(double interval) {
      return 0;
   }

   /**
    * Create axis and other guides for the graph.
    */
   @Override
   public void createAxis(VGraph vgraph) {
      axes.clear();

      // if has no visual object, axis should not be created
      if(GTool.getVOs(vgraph).size() == 0) {
         return;
      }

      Scale[] scales = getParallelScales();
      double aw = GWIDTH / scales.length;

      for(int i = 0; i < scales.length; i++) {
         if(scales[i].getAxisSpec().getAxisStyle() > AxisSpec.AXIS_NONE) {
            DefaultAxis axis = new DefaultAxis(scales[i], vgraph);
            axis.setLength(GHEIGHT);
            axis.getScreenTransform().translate((i + 0.5) * aw, 0);
            axis.getScreenTransform().rotate(Math.PI / 2);

            vgraph.addAxis(axis);
            axes.add(axis);
         }
         // need this place holder, otherwise the axis will be shifted in layout
         else {
            axes.add(null);
         }
      }

      // create grid lines so it's there if fit is not called (polar)
      for(int i = 0; i < axes.size(); i++) {
         DefaultAxis axis = axes.get(i);

         if(axis == null) {
            continue;
         }

         if(i == axes.size() - 1) {
            DefaultAxis axis0 = axes.get(0);

            if(axis0 == null) {
               continue;
            }

            // the grid line from the last axis to the first axis should extend
            // right, so when it's transformed in polar, it connects to the
            // first axis directly instead of go through the other axes.
            axis0.getScreenTransform().translate(0, -aw * scales.length);
            axis.createGridLines(axis0, true);
            axis0.getScreenTransform().translate(0, aw * scales.length);
         }
         else {
            axis.createGridLines(axes.get(i + 1), true);
         }
      }

      paxis = new DefaultAxis(getAxisLabelScale(), getVGraph());
      paxis.setLength(GWIDTH);
      paxis.getScreenTransform().translate(0, GHEIGHT);
      vgraph.addAxis(paxis);
   }

   /**
    * Set the screen transformation to fit the graph to the coord bounds.
    */
   @Override
   public void fit(double x, double y, double w, double h) {
      AffineTransform c2 = getScaledCoordTransform(x, y, w, h);
      double yfactor = GTool.getScaleFactor(c2, 90);
      Scale[] scales = getParallelScales();

      if(paxis != null) {
         // height should take into consideration of rotation
         paxis.getScreenTransform().concatenate(c2);
      }

      double paxish = (paxis == null) ? 0 : paxis.getMinHeight() * yfactor;
      double plotx = x;
      double ploty = y;
      double plotw = w;
      double ploth = h - paxish;
      double plotw2 = plotw; // width minus the elements outside of bounds
      double ploth2 = ploth;
      double plotx2 = plotx;
      double ploty2 = ploty;
      VGraph vgraph = getVGraph();

      if(vgraph != null) {
         // scale visual point locations to fit the remaining area
         AffineTransform trans = new AffineTransform();

         trans.translate(plotx, ploty);
         trans.scale(plotw / GWIDTH, ploth / GHEIGHT);
         vgraph.concat(trans, true);
         vgraph.setPlotBounds(new Rectangle2D.Double(plotx, ploty, plotw,ploth));

         Rectangle2D box = getElementBounds(vgraph, true);

         if(box != null && !box.isEmpty()) {
            double left = getElementMargin(plotx - box.getX());
            double bottom = getElementMargin(ploty - box.getY());
            double right = getElementMargin(box.getX() + box.getWidth() -
                                            plotx - plotw);
            double top = getElementMargin(box.getY() + box.getHeight() -
                                          ploty - ploth);

            if(left + right + top + bottom > 0) {
               double xs = plotw / (plotw + left + right);
               double ys = ploth / (ploth + top + bottom);
               plotw2 = plotw * xs;
               ploth2 = ploth * ys;
               plotx2 = plotx + left * xs;
               ploty2 = ploty + bottom * ys;

               AffineTransform trans2 = new AffineTransform();
               trans2.translate(plotx2, ploty2);
               trans2.scale(xs, ys);
               trans2.translate(-plotx, -ploty);
               vgraph.concat(trans2, false);
            }
         }

         vgraph.concat(c2, false);
         vgraph.setPlotBounds(getBounds(plotx, ploty, plotw, ploth, c2));
      }

      // move and scale axis to fit the screen area
      for(int i = 0; i < axes.size(); i++) {
         DefaultAxis axis = axes.get(i);

         if(axis == null) {
            continue;
         }

         AffineTransform xtrans = new AffineTransform();
         double startx = plotw2 * (i + 0.5) / scales.length + plotx2;

         xtrans.translate(startx, ploty2);
         xtrans.rotate(Math.PI / 2);
         xtrans.scale(ploth2 / GHEIGHT, 1);
         xtrans.preConcatenate(c2);

         axis.setScreenTransform(xtrans);
         axis.setAxisSize(axis.getMinWidth());
         axis.layout(new Rectangle2D.Double(plotx2, y, plotw2 + 1, h + 1));

         AffineTransform gridtrans = axis.getGridLineTransform();
         gridtrans.translate(plotx, ploty);
         gridtrans.scale(plotw / GWIDTH, ploth / GHEIGHT);
         gridtrans.preConcatenate(c2);
      }

      // move paxis to top
      if(paxis != null) {
         AffineTransform ptrans = new AffineTransform();

         ptrans.translate(plotx2, ploty + ploth);
         ptrans.scale(plotw2 / GWIDTH, 1);
         ptrans.concatenate(GDefaults.FLIPY);
         ptrans.preConcatenate(c2);
         paxis.setScreenTransform(ptrans);
         paxis.setAxisSize(paxis.getMinHeight());
         paxis.layout(new Rectangle2D.Double(plotx2, y, plotw2 + 1, h + 1));
      }

      layoutText(vgraph, true);
   }

   /**
    * Create graph geometry (nested chart) for this coordinate.
    * @hidden
    * @param rset the specified root data set.
    * @param ridx the specified index for root data set.
    * @param dset dataset of this graph. This may be a subset if this coordinate
    * is a nested coordinate.
    * @param graph the top-level graph definition.
    * @param ggraph the parent ggraph.
    * @param facet the facet coordinate.
    * @param hint the specified hint.
    */
   @Override
   public void createSubGGraph(DataSet rset, DataSetIndex ridx, DataSet dset,
                               EGraph graph, GGraph ggraph, FacetCoord facet, Map xmap, Map ymap,
                               int hint)
   {
      // do nothing
   }

   /**
    * Get unit minimum width.
    * @hidden
    */
   @Override
   public double getUnitMinWidth() {
      return getScalesWidth(true);
   }

   /**
    * Get unit minimum height.
    * @hidden
    */
   @Override
   public double getUnitMinHeight() {
      return getScalesHeight(true);
   }

   /**
    * Get unit preferred width.
    * @hidden
    */
   @Override
   public double getUnitPreferredWidth() {
      return getScalesWidth(false);
   }

   /**
    * Get unit preferred height.
    * @hidden
    */
   @Override
   public double getUnitPreferredHeight() {
      return getScalesHeight(false);
   }

   /**
    * Get the scale for the axes labels.
    */
   public abstract Scale getAxisLabelScale();

   /**
    * Get all axes in this coordinate.
    */
   @Override
   @TernMethod
   public Axis[] getAxes(boolean recursive) {
      return axes.toArray(new Axis[axes.size()]);
   }

   /**
    * Get minimum or preferred width for all axes in this coordinate.
    */
   private double getScalesWidth(boolean isMin) {
      double width = 0;

      for(int i = 0; i < axes.size(); i++) {
         DefaultAxis axis = axes.get(i);

         if(axis != null) {
            width += isMin ? axis.getMinWidth() : axis.getPreferredWidth();
         }
      }

      return width + 0.5 * width;
   }

   /**
    * Get minimum or preferred height for all axes in this coordinate.
    */
   private double getScalesHeight(boolean isMin) {
      double height = 0;

      for(int i = 0; i < axes.size(); i++) {
         DefaultAxis axis = axes.get(i);

         if(axis == null) {
            continue;
         }

         Scale s = axis.getScale();

         if(s instanceof CategoricalScale || s instanceof TimeScale) {
            int len = axis.getLabels().length;
            height = Math.max(height, isMin ? axis.getMinHeight() :
                                            axis.getPreferredHeight()) * len;
         }
         else {
            height = Math.max(height, isMin ? axis.getMinHeight() :
                                            axis.getPreferredHeight());
         }
      }

      return height;
   }

   /**
    * Make a copy of this object.
    */
   @Override
   public Object clone() {
      return clone(true);
   }

   /**
    * Make a copy of this object.
    * @param srange true to share scale range, false otherwise.
    * @return an Object that is a copy of this object.
    */
   @Override
   public Object clone(boolean srange) {
      AbstractParallelCoord coord = (AbstractParallelCoord) super.clone();
      coord.axes = new ArrayList<>();

      return coord;
   }

   private transient List<DefaultAxis> axes = new ArrayList<>();
   private transient DefaultAxis paxis; // axis for axes labels
}
