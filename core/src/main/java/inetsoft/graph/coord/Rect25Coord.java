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
import inetsoft.graph.guide.axis.Axis2D;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.guide.form.DefaultForm;
import inetsoft.graph.guide.form.LineForm;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.FormVO;
import inetsoft.graph.visual.LineFormVO;

import java.awt.*;
import java.awt.geom.*;
import java.util.Map;

/**
 * A two-dimensional rectangular coordinate with an extra dimension (depth) to
 * give elements a 3D look and feel (2 1/2 dimensions). The data is plotted on
 * the two (x and y) dimensions only.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=Rect25Coord")
public class Rect25Coord extends RectCoord {
   /**
    * Default constructor.
    */
   public Rect25Coord() {
   }

   /**
    * Create a two-dimensional rectangular 2d coordinate.
    * @param xscale x axis scale.
    * @param yscale y axis scale.
    */
   @TernConstructor
   public Rect25Coord(Scale xscale, Scale yscale) {
      super(xscale, yscale);
   }

   /**
    * Create axis and other guides for the graph.
    * @param vgraph visual graph to add axes.
    */
   @Override
   public void createAxis(VGraph vgraph) {
      super.createAxis(vgraph);

      Axis2D xaxis1 = (Axis2D) getXAxis1();
      Axis2D xaxis2 = (Axis2D) getXAxis2();
      Axis2D yaxis1 = (Axis2D) getYAxis1();
      Axis2D yaxis2 = (Axis2D) getYAxis2();

      if(xaxis1 != null) {
         int style = getXScale().getAxisSpec().getAxisStyle();

         xaxis1.setDepth(getDepth());

         if(style == AxisSpec.AXIS_NONE) {
            xaxis1.setZIndex(-1);
         }
      }

      if(xaxis2 != null) {
         xaxis2.setZIndex(-1);
      }

      if(yaxis1 != null) {
         int style = getYScale().getAxisSpec().getAxisStyle();

         yaxis1.setDepth(getDepth());

         if(style == AxisSpec.AXIS_NONE) {
            yaxis1.setZIndex(-1);
         }
      }

      if(yaxis2 != null) {
         yaxis2.setZIndex(-1);
      }
   }

   /**
    * Create an axis for a scale.
    */
   @Override
   protected DefaultAxis createAxis(Scale scale, VGraph vgraph, boolean secondary) {
      Axis2D axis = new Axis2D(scale, vgraph);
      axis.setCoordinate(this);
      return axis;
   }

   /**
    * Set the screen transformation to fit the graph to the specified output
    * size.
    */
   @Override
   public void fit(double x, double y, double w, double h) {
      if(depth0 >= w || depth0 >= h) {
         //throw new DepthException();
         // this can happen in facet so don't fail the graph generation, just
         // let it proceed with smaller depth
         setDepth(Math.min(w, h));
      }

      super.fit(x, y, w - getDepth(), h - getDepth());
   }

   /**
    * Create a border along the axes.
    */
   @Override
   void createCoordBorder(VGraph vgraph, double plotx, double ploty,
                          double plotw, double ploth, double plotx2,
                          double ploty2, double plotw2, double ploth2,
                          AffineTransform c2)
   {
      DefaultAxis xaxis1 = getXAxis1();
      DefaultAxis yaxis1 = getYAxis1();

      if(xaxis1 == null || yaxis1 == null) {
         return;
      }

      Point2D p1;
      Point2D p2;
      double xfactor = GTool.getScaleFactor(c2, 0);
      double yfactor = GTool.getScaleFactor(c2, 90);
      double xdepth = getDepth() * xfactor;
      double ydepth = getDepth() * yfactor;
      Color xcolor = xaxis1.getScale().getAxisSpec().getLineColor();
      Color ycolor = yaxis1.getScale().getAxisSpec().getLineColor();
      int hi = GDefaults.AXIS_BORDER_Z_INDEX;
      int lo = GDefaults.COORD_BORDER_Z_INDEX;
      int front = GDefaults.GRIDLINE_TOP_Z_INDEX;

      if(yaxis1.isLineVisible()) {
         // add line at the right side
         p1 = new Point2D.Double(plotw + xdepth, ydepth);
         p2 = new Point2D.Double(plotw + xdepth, ploth + ydepth);
         addBorderLineVO(vgraph, p1, p2, ycolor, c2, plotx, ploty, hi);

         // add line at the bottom of left side
         p1 = new Point2D.Double(0, 0);
         p2 = new Point2D.Double(xdepth, ydepth);
         addBorderLineVO(vgraph, p1, p2, ycolor, c2, plotx, ploty, hi);

         GeneralPath fill = new GeneralPath();

         // add line at the outer left side
         p1 = new Point2D.Double(0, 0);
         p2 = new Point2D.Double(0, ploth);
         addBorderLineVO(vgraph, p1, p2, ycolor, c2, plotx, ploty, lo);

         fill.moveTo((float) p1.getX(), (float) p1.getY());
         fill.lineTo((float) p2.getX(), (float) p2.getY());

         // add line at the top of inner and outer left side
         p1 = new Point2D.Double(0, ploth);
         p2 = new Point2D.Double(xdepth, ploth + ydepth);
         addBorderLineVO(vgraph, p1, p2, ycolor, c2, plotx, ploty, hi);

         fill.lineTo((float) p2.getX(), (float) p2.getY());

         // add line at the inner left side
         p1 = new Point2D.Double(xdepth, ploth + ydepth);
         p2 = new Point2D.Double(xdepth, ydepth);
         addBorderLineVO(vgraph, p1, p2, ycolor, c2, plotx, ploty, lo);

         fill.lineTo((float) p2.getX(), (float) p2.getY());
         fill.closePath();

         // fill in the extra area on the left axis
         addBorderFillVO(vgraph, fill, c2, plotx, ploty);
      }

      if(xaxis1.isLineVisible()) {
         // add line at the top side
         p1 = new Point2D.Double(xdepth, ploth + ydepth);
         p2 = new Point2D.Double(plotw + xdepth, ploth + ydepth);
         addBorderLineVO(vgraph, p1, p2, xcolor, c2, plotx, ploty, hi);

         // add line at the left of bottom side
         p1 = new Point2D.Double(0, 0);
         p2 = new Point2D.Double(xdepth, ydepth);
         addBorderLineVO(vgraph, p1, p2, xcolor, c2, plotx, ploty, lo);

         GeneralPath fill2 = new GeneralPath();

         // add line at the top of bottom axis
         p1 = new Point2D.Double(xdepth, ydepth);
         p2 = new Point2D.Double(plotw + xdepth, ydepth);
         addBorderLineVO(vgraph, p1, p2, xcolor, c2, plotx, ploty, lo);

         fill2.moveTo((float) p1.getX(), (float) p1.getY());
         fill2.lineTo((float) p2.getX(), (float) p2.getY());

         // add line at the bottom right corner
         p1 = new Point2D.Double(plotw, 0);
         p2 = new Point2D.Double(plotw + xdepth, ydepth);
         addBorderLineVO(vgraph, p1, p2, xcolor, c2, plotx, ploty, hi);
         fill2.lineTo((float) p1.getX(), (float) p1.getY());

         // add line at the bottom of bottom axis
         p1 = new Point2D.Double(0, 0);
         p2 = new Point2D.Double(plotw, 0);
         addBorderLineVO(vgraph, p1, p2, xcolor, c2, plotx, ploty, lo);

         fill2.lineTo((float) p1.getX(), (float) p1.getY());
         fill2.closePath();

         // fill in the extra area on the bottom axis
         addBorderFillVO(vgraph, fill2, c2, plotx, ploty);

         // if both positive and negative, add a line at zero
         if(getYScale().getMin() < 0 && getYScale().getMax() >= 0) {
            Color clr = yaxis1.getScale().getAxisSpec().getLineColor();
            double y0 = getPosition(getYScale(), 0, ploth2) + ploty2 - ploty;

            // front
            p1 = new Point2D.Double(0, y0);
            p2 = new Point2D.Double(plotw, y0);
            addBorderLineVO(vgraph, p1, p2, xcolor, c2, plotx, ploty, front);

            // back
            p1 = new Point2D.Double(xdepth, y0 + ydepth);
            p2 = new Point2D.Double(plotw + xdepth, y0 + ydepth);
            addBorderLineVO(vgraph, p1, p2, xcolor, c2, plotx, ploty, hi);

            // right side
            p1 = new Point2D.Double(plotw, y0);
            p2 = new Point2D.Double(plotw + xdepth, y0 + ydepth);
            addBorderLineVO(vgraph, p1, p2, xcolor, c2, plotx, ploty, hi);

            // left side
            p1 = new Point2D.Double(0, y0);
            p2 = new Point2D.Double(xdepth, y0 + ydepth);
            addBorderLineVO(vgraph, p1, p2, clr, c2, plotx, ploty, hi);
         }
      }
   }

   /**
    * Add the border line next to the axis.
    */
   private void addBorderLineVO(VGraph vgraph, Point2D p1, Point2D p2,
                                Color color, AffineTransform c2,
                                double plotx, double ploty, int zindex)
   {
      p1 = new Point2D.Double(p1.getX(), p1.getY());
      p2 = new Point2D.Double(p2.getX(), p2.getY());

      LineForm form = new LineForm(p1, p2);
      LineFormVO vo = new LineFormVO(form, new Point2D[] {p1, p2});

      vo.setColor(color);
      form.setLine(GraphConstants.THIN_LINE);
      vo.setFixedPosition(false);

      AffineTransform trans = vo.getScreenTransform();
      vo.setZIndex(zindex);
      trans.translate(plotx, ploty);
      trans.preConcatenate(c2);

      vgraph.addVisual(vo);
   }

   /**
    * Add the border fill area.
    */
   private void addBorderFillVO(VGraph vgraph, Shape shape, AffineTransform c2,
                                double plotx, double ploty)
   {
      DefaultForm form = new DefaultForm(shape);
      FormVO vo = new FormVO(form);

      form.setColor(Axis2D.FILL_COLOR);
      form.setFill(true);
      vo.setShape(shape);
      vo.setZIndex(GDefaults.TARGET_FILL_Z_INDEX);

      AffineTransform trans = vo.getScreenTransform();
      trans.translate(plotx, ploty);
      trans.preConcatenate(c2);

      vgraph.addVisual(vo);
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
    * Set 3D depth.
    */
   @TernMethod
   public void setDepth(double depth) {
      this.depth0 = depth;
   }

   /**
    * Get 3D depth.
    */
   @Override
   @TernMethod
   public double getDepth() {
      return depth0;
   }

   private double depth0 = 15.0; // set depth
   private static final long serialVersionUID = 1L;
}
