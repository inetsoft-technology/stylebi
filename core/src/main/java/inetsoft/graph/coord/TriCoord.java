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
package inetsoft.graph.coord;

import com.inetsoft.build.tern.*;
import inetsoft.graph.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DataSetIndex;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.Scale;

import java.awt.geom.*;
import java.util.Map;

/**
 * A triangular coordinate plot points at the intersection of grid lines.
 * Each element should contain three measures. The first two measures should be
 * added as dimension, and the last measure added as variable.
 * <br>
 * A shared scale is used to plot three axes. The scale should be the stacked
 * range of each row in the dataset. The sum of the three measures should be
 * constant.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=TriCoord")
public class TriCoord extends Coordinate {
   /**
    * Default constructor.
    */
   public TriCoord() {
   }

   /**
    * Create a triangular coordinate using a shared scale.
    */
   @TernConstructor
   public TriCoord(Scale scale) {
      this();
      setScale(scale);
   }

   /**
    * Set the scale for the coord. It's shared by all three axes.
    */
   @TernMethod
   public void setScale(Scale scale) {
      this.scale = scale;
      scale.setScaleOption(0);
   }

   /**
    * Get the scale of the coord.
    */
   @TernMethod
   public Scale getScale() {
      return scale;
   }

   /**
    * Map a tuple (from logic coordinate space) to the chart coordinate space.
    * @param tuple the tuple in logic space (scaled values).
    * @return position in chart coordinate space.
    */
   @Override
   @TernMethod
   public Point2D getPosition(double[] tuple) {
      double x = 0;
      double y = 0;
      double z = 0;
      double w = getAxisLength();

      x = getValue(tuple, 0);
      x = getPosition(scale, x, w);

      y = getValue(tuple, 1);
      y = getPosition(scale, y, w);

      z = getValue(tuple, 2);
      z = getPosition(scale, z, w);

      double u = (2 * Math.tan(A60) * x + Math.tan(A60) * y) / (x + y + z);
      double v = y / (x + y + z);

      u = u * w / (2 * Math.tan(A60));
      v = v * w;

      return new Point2D.Double(u, v);
   }

   /**
    * Get the interval size in this coordinate space.
    * @param interval interval value.
    * @return interval size in this coordinate space.
    */
   @Override
   @TernMethod
   public double getIntervalSize(double interval) {
      return getIntervalSize(scale, interval, getAxisLength());
   }

   /**
    * Create axis and other guides for the graph.
    * @param vgraph visual graph to create axis.
    */
   @Override
   public void createAxis(VGraph vgraph) {
      if(scale == null || scale.getAxisSpec().getAxisStyle() == AxisSpec.AXIS_NONE) {
         return;
      }

      double axisw = getAxisLength();
      axes = new DefaultAxis[3];

      for(int i = 0; i < axes.length; i++) {
         axes[i] = new DefaultAxis(scale, vgraph);
         axes[i].setAxisType("y");
         axes[i].setTickDown(true);
         axes[i].setLength(axisw);

         vgraph.addAxis(axes[i]);
      }

      AffineTransform xtrans = new AffineTransform();
      xtrans.translate(GWIDTH / 2, GWIDTH * Math.sin(A60));
      xtrans.rotate(-Math.PI * 2 / 3);
      axes[0].setScreenTransform(xtrans);

      xtrans = new AffineTransform();
      xtrans.translate((GWIDTH - axisw) / 2 + axisw, 0);
      xtrans.rotate(Math.PI * 2 / 3);
      axes[1].setScreenTransform(xtrans);

      xtrans = new AffineTransform();
      xtrans.translate((GWIDTH - axisw) / 2, 0);
      axes[2].setScreenTransform(xtrans);

      axes[0].createGridLines(axes[2], false);
      axes[1].createGridLines(axes[0], false);
      axes[2].createGridLines(axes[1], false);
   }

   /**
    * Set the screen transformation to fit the graph to the specified output
    * size.
    * @param x horizontal position.
    * @param y vertical position.
    * @param w width to scale to.
    * @param h height to scale to.
    */
   @Override
   public void fit(double x, double y, double w, double h) {
      AffineTransform c2 = getScaledCoordTransform(x, y, w, h);

      VGraph vgraph = getVGraph();
      double xfactor = GTool.getScaleFactor(c2, 0);
      double yfactor = GTool.getScaleFactor(c2, 90);
      double leftw = (axes.length < 3) ? 0 : axes[0].getMinWidth() * xfactor;
      double rightw = (axes.length < 3) ? 0 : axes[1].getMinWidth() * xfactor;
      double bottomh = (axes.length < 3) ? 0 : axes[2].getMinHeight() * yfactor;
      double plotw = w - leftw - rightw;
      // reserve space for top label too, assuming it's same as
      // bottom label height
      double ploth = h - bottomh * 2;
      double axisw = Math.min(plotw / 2 / Math.cos(A60),
                              ploth / Math.sin(A60));
      double plotx = x + w / 2 - axisw / 2;
      double ploty = y + bottomh;
      plotw = axisw;
      ploth = axisw * Math.sin(A60);

      if(vgraph != null) {
         // vgraph transform
         AffineTransform trans = new AffineTransform();

         trans.translate(plotx, ploty);
         trans.scale(plotw / GWIDTH, ploth / GHEIGHT);
         vgraph.concat(trans, true);

         // set the plot bounds before c2 transformation so the
         // getElementBounds can get the right boundary
         vgraph.setPlotBounds(new Rectangle2D.Double(plotx, ploty, plotw,ploth));

         // this must be called after the scale and before c2 is applied
         Rectangle2D box = getElementBounds(vgraph, true);

         if(box != null && !box.isEmpty()) {
            double left = getElementMargin(plotx - box.getX());
            double bottom = getElementMargin(ploty - box.getY());
            double right = getElementMargin(box.getX() + box.getWidth() -
                                            plotx - plotw);
            double top = getElementMargin(box.getY() + box.getHeight() -
                                          ploty - ploth);

            if(left + right + top + bottom > 0) {
               double plotx0 = plotx, ploty0 = ploty;
               double xs = plotw / (plotw + left + right);
               double ys = ploth / (ploth + top + bottom);
               plotw = plotw * xs;
               ploth = ploth * ys;
               plotx = plotx + left * xs;
               ploty = ploty + bottom * ys;

               axisw = Math.min(plotw / 2 / Math.cos(A60),
                  ploth / Math.sin(A60));

               AffineTransform trans2 = new AffineTransform();
               trans2.translate(plotx, ploty);
               trans2.scale(xs, ys);
               trans2.translate(-plotx0, -ploty0);
               vgraph.concat(trans2, false);
            }
         }

         vgraph.concat(c2, false);
         vgraph.setPlotBounds(getBounds(plotx, ploty, plotw, ploth, c2));
      }

      if(axes.length > 2) {
         AffineTransform xtrans = new AffineTransform();
         xtrans.translate(plotx + axisw / 2, ploty + axisw * Math.sin(A60));
         xtrans.rotate(-Math.PI * 2 / 3);
         xtrans.scale(axisw / GWIDTH, 1);
         xtrans.preConcatenate(c2);
         axes[0].setScreenTransform(xtrans);
         axes[0].layout(new Rectangle2D.Double(x, y, w, h));

         xtrans = new AffineTransform();
         xtrans.translate(plotx + axisw, ploty);
         xtrans.rotate(Math.PI * 2 / 3);
         xtrans.scale(axisw / GWIDTH, 1);
         xtrans.preConcatenate(c2);
         axes[1].setScreenTransform(xtrans);
         axes[1].layout(new Rectangle2D.Double(x, y, w, h));

         xtrans = new AffineTransform();
         xtrans.translate(plotx, ploty);
         xtrans.scale(axisw / GWIDTH, 1);
         xtrans.preConcatenate(c2);
         axes[2].setScreenTransform(xtrans);
         axes[2].layout(new Rectangle2D.Double(x, y, w, h));

         axes[0].createGridLines(axes[2], false);
         axes[1].createGridLines(axes[0], false);
         axes[2].createGridLines(axes[1], false);
      }

      layoutText(vgraph, true);
   }

   /**
    * Get the number of dimensions in this coordinate.
    */
   @Override
   @TernMethod
   public int getDimCount() {
      return 3;
   }

   /**
    * Get the scales used in the coordinate.
    */
   @Override
   @TernMethod
   public Scale[] getScales() {
      return new Scale[] {scale, scale, scale};
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
    * @param facet the specified facet coordinate.
    * @param hint the specified position hint.
    */
   @Override
   public void createSubGGraph(DataSet rset, DataSetIndex ridx, DataSet dset,
                               EGraph graph, GGraph ggraph, FacetCoord facet, Map xmap, Map ymap,
                               int hint)
   {
      // do nothing
   }

   /**
    * Get all axes in this coordinate.
    */
   @Override
   @TernMethod
   public Axis[] getAxes(boolean recursive) {
      return axes;
   }

   /**
    * Get unit minimum width.
    * @hidden
    */
   @Override
   public double getUnitMinWidth() {
      return getUnitBounds(false).getHeight();
   }

   /**
    * Get unit minimum height.
    * @hidden
    */
   @Override
   public double getUnitMinHeight() {
      return getUnitBounds(false).getHeight();
   }

   /**
    * Get unit preferred width.
    * @hidden
    */
   @Override
   public double getUnitPreferredWidth() {
      return getUnitBounds(false).getWidth();
   }

   /**
    * Get unit preferred height.
    * @hidden
    */
   @Override
   public double getUnitPreferredHeight() {
      return getUnitBounds(false).getWidth();
   }

   /**
    * Get axis min or preferred size.
    */
   private Rectangle2D getUnitBounds(boolean min) {
      double w = 0;

      if(axes.length > 2 && axes[2] != null) {
         w = min ? axes[2].getMinWidth() : axes[2].getPreferredWidth();
      }

      Rectangle2D bounds = new Rectangle2D.Double(0, 0, w, w);
      return getCoordTransform().createTransformedShape(bounds).getBounds2D();
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
    */
   @Override
   public Object clone(boolean srange) {
      Object obj = super.clone();

      if(obj == null) {
         return null;
      }

      TriCoord coord = (TriCoord) obj;

      if(scale != null) {
         coord.scale = (Scale) scale.clone();
      }

      return coord;
   }

   /**
    * Get the axis length.
    */
   private double getAxisLength() {
      return Math.min(GWIDTH / 2 / Math.cos(A60), GHEIGHT / Math.sin(A60));
   }

   private static final double A60 = Math.PI / 3;
   private static final long serialVersionUID = 1L;

   private Scale scale;
   private DefaultAxis[] axes = {}; // left, right, bottom
}
