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
package inetsoft.graph.coord;

import com.inetsoft.build.tern.*;
import inetsoft.graph.*;
import inetsoft.graph.data.*;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.geometry.GraphGeometry;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.guide.form.LineForm;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.*;
import inetsoft.graph.visual.ElementVO;
import inetsoft.graph.visual.FormVO;
import inetsoft.util.CoreTool;

import java.awt.*;
import java.awt.geom.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;
import java.util.*;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a rectangular 2D coordinate. X scale controls the horizontal
 * position, and Y scale controls the vertical position.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=RectCoord")
public class RectCoord extends Coordinate {
   /**
    * Default constructor.
    */
   public RectCoord() {
   }

   /**
    * Create a two-dimensional rectangular coordinate.
    * @param xscale x axis scale.
    * @param yscale y axis scale.
    */
   @TernConstructor
   public RectCoord(Scale xscale, Scale yscale) {
      this.xscale = xscale;
      this.yscale = yscale;
   }

   /**
    * Create a unique key for the rect coord.
    * @hidden
    */
   public Object createKey(boolean vertical) {
      List list = new ObjectArrayList();

      // field could be empty (fake)
      // don't confuse vertical and horizontal fake scales
      list.add(vertical);

      createKey0(list, vertical);

      // in nested coord, an outer coord may have an empty axis, and an inner
      // coord could also have an empty axis (could be rotated), we shouldn't
      // mix them (e.g. nested bullet graph).
      for(Coordinate pcoord = getParentCoordinate(); pcoord != null;
          pcoord = pcoord.getParentCoordinate())
      {
         // adding parent coord class to make sure the nested coord and
         // inner coords are distinguished in case both have empty
         // x or y fields
         list.add(pcoord.getClass());

         if(pcoord instanceof FacetCoord) {
            Coordinate outer = ((FacetCoord) pcoord).getOuterCoordinate();

            if(outer != this && outer != null) {
               ((RectCoord) outer).createKey0(list, vertical);
            }
         }
      }

      return list;
   }

   /**
    * Add the x/y fields to list.
    */
   private void createKey0(List list, boolean vertical) {
      if(vertical) {
         createKey0(list, yscale);
         createKey0(list, yscale2);
      }
      else {
         createKey0(list, xscale);
      }
   }

   /**
    * Add field to list.
    */
   private void createKey0(List list, Scale scale) {
      if(scale == null) {
         return;
      }

      String[] flds = scale.getFields();

      for(int i = 0; i < flds.length; i++) {
         list.add(flds[i]);
      }
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
      int n = 0;

      if(xscale != null) {
         x = getValue(tuple, n++);
      }
      else if(tuple.length > 1) {
         x = tuple[tuple.length - 2];
      }

      x = getPosition(xscale, x, GWIDTH);

      if(yscale != null) {
         y = getValue(tuple, n);
      }
      else if(tuple.length > 0) {
         y = tuple[tuple.length - 1];
      }

      y = getPosition(yscale, y, GHEIGHT);

      return new Point2D.Double(x, y);
   }

   /**
    * Get the interval size in this coordinate space.
    * @param interval interval value.
    * @return interval size in this coordinate space.
    */
   @Override
   @TernMethod
   public double getIntervalSize(double interval) {
      return getIntervalSize(yscale, interval, GHEIGHT);
   }

   /**
    * Create axis and other guides for the graph.
    * @param vgraph visual graph to create axis.
    */
   @Override
   public void createAxis(VGraph vgraph) {
      if(xscale != null && (xaxis1 == null || xaxis2 == null)) {
         AxisSpec spec = xscale.getAxisSpec();
         int style = spec.getAxisStyle();

         xaxis1 = createAxis(xscale, vgraph, false);
         xaxis1.setAxisType("x");
         // when both axes are show, it's nicer to have all ticks facing in
         xaxis1.setTickDown((style & 0x3) != 0x3);

         // for facet, top (x2) is primary. Otherwise bottom (x1) is primary
         boolean x1primary = (facet == null) ? (style & 0x10) == 0
            : (style & 0x10) != 0;
         boolean x1vis = (facet == null) ? (style & 0x1) != 0
            : (style & AxisSpec.AXIS_SINGLE2) != 0;
         boolean x2vis = (facet == null) ? (style & AxisSpec.AXIS_SINGLE2) != 0
            : (style & 0x1) != 0;

         xaxis1.setLength(GWIDTH);
         // apply coord transform so axis horizontal/vertical is correct when
         // calculating size. The screen transform will be reset in fit.
         xaxis1.getScreenTransform().preConcatenate(getCoordTransform());
         xaxis1.setLabelVisible(isAxisLabelVisible(getAxisPosition(500, 0)) &&
                                xaxis1.isLabelVisible() && x1primary);

         if(!isAxisTickVisible(getAxisPosition(500, 0))) {
            xaxis1.setTickVisible(false);
         }

         if(!x1vis || (facet != null && !xaxis1.isLabelVisible())) {
            xaxis1.setZIndex(-1);
         }

         xaxis2 = createAxis(xscale, vgraph, true);
         xaxis2.setAxisType("x");
         xaxis2.setTickDown(false);
         xaxis2.setLength(GWIDTH);
         xaxis2.setPrimaryAxis(xaxis1);

         boolean in_facet = getParentCoordinate() instanceof FacetCoord;

         xaxis2.setLabelVisible(isAxisLabelVisible(getAxisPosition(500,1000)) &&
                                xaxis2.isLabelVisible() &&
                                (!x1primary ||
                                 !xaxis1.isLabelVisible() && x2vis && !in_facet));

         if(!isAxisTickVisible(getAxisPosition(500, 1000))) {
            xaxis2.setTickVisible(false);
         }

         xaxis2.getScreenTransform().translate(0, GHEIGHT);
         xaxis2.getScreenTransform().preConcatenate(getCoordTransform());

         // we need to create grid line here so the grid lines exist to be
         // transformed (e.g. by PolarCoord) when fit is not called
         // facet grid created by FacetCoord.
         if(facet == null) {
            xaxis2.createGridLines(xaxis1, true);
         }

         if(!x2vis) {
            xaxis2.setZIndex(-1);
         }

         if(xaxis2.getZIndex() < 0 && xaxis2.getGridLineCount() == 0) {
            xaxis2 = null;
         }
         else {
            vgraph.addAxis(xaxis2);
         }

         // xaxis2 holds a reference to xaxis1, so only clear if xaxis2 is null, otherwise
         // the xaxis1 is orphened and layoutCompleted() won't be called
         if(xaxis1.getZIndex() < 0 && xaxis1.getGridLineCount() == 0 && xaxis2 == null) {
            xaxis1 = null;
         }
         else {
            vgraph.addAxis(xaxis1);
         }
      }

      if(yscale != null && (yaxis1 == null || yaxis2 == null)) {
         AxisSpec spec = yscale.getAxisSpec();
         int style = spec.getAxisStyle();

         yaxis1 = createAxis(yscale, vgraph, false);
         yaxis1.setAxisType("y");
         yaxis1.setLength(GHEIGHT);
         yaxis1.setTickDown((style & 0x3) != 0x3);
         yaxis1.getScreenTransform().rotate(Math.PI / 2);
         yaxis1.getScreenTransform().preConcatenate(getCoordTransform());
         yaxis1.setLabelVisible(isAxisLabelVisible(getAxisPosition(0, 500)) &&
                                yaxis1.isLabelVisible() && (style & 0x10) == 0);

         if(!isAxisTickVisible(getAxisPosition(0, 500))) {
            yaxis1.setTickVisible(false);
         }

         if((style & 0x1) == 0) {
            yaxis1.setZIndex(-1);
         }

         yaxis2 = createAxis((yscale2 == null) ? yscale : yscale2, vgraph, yscale2 == null);

         // if the y2 axis has it's own scale, don't drawn the ticks from the
         // y1 axis on the y2 axis, otherwise the ticks are wrong
         if(yscale2 == null) {
            yaxis2.setPrimaryAxis(yaxis1);
         }

         yaxis2.setAxisType("y");
         yaxis2.setLength(GHEIGHT);
         yaxis2.setTickDown(false);
         yaxis2.setLabelVisible(isAxisLabelVisible(getAxisPosition(1000,500)) &&
                                yaxis2.isLabelVisible() &&
                                ((style & 0x10) != 0 || yscale2 != null));
         yaxis2.getScreenTransform().translate(GWIDTH, 0);
         yaxis2.getScreenTransform().rotate(Math.PI / 2);
         yaxis2.getScreenTransform().preConcatenate(getCoordTransform());

         // facet grid created by FacetCoord.
         if(facet == null) {
            // if grid line is not enabled on 2nd y, check primary y
            if(!yaxis2.createGridLines(yaxis1, true) && yscale2 != null) {
               yaxis1.createGridLines(yaxis2, true);
            }
         }

         if(!isAxisTickVisible(getAxisPosition(1000, 500))) {
            yaxis2.setTickVisible(false);
         }

         if((style & AxisSpec.AXIS_SINGLE2) == 0 ||
            (facet != null && !yaxis2.isLabelVisible()))
         {
            yaxis2.setZIndex(-1);
         }

         if(yaxis2.getZIndex() < 0 && yaxis2.getGridLineCount() == 0) {
            yaxis2 = null;
         }
         else {
            vgraph.addAxis(yaxis2);
         }

         if(yaxis1.getZIndex() < 0 && yaxis1.getGridLineCount() == 0 && yaxis2 == null) {
            yaxis1 = null;
         }
         else {
            vgraph.addAxis(yaxis1);
         }
      }
   }

   protected DefaultAxis createAxis(Scale scale, VGraph vgraph, boolean secondary) {
      DefaultAxis axis = new DefaultAxis(scale, vgraph);
      axis.setCoordinate(this);

      if(secondary && scale.getAxisSpec().getLine2Color() != null) {
         axis.setLineColor(scale.getAxisSpec().getLine2Color());
      }

      return axis;
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
      // the axis size are real size, and the other VO's has logic size.
      // This would be ok unless the transformation involves rotation, which
      // would cause the real size to be scaled by a different ratio depending
      // on the width to height ratio (w -> h). We adjust the factor to
      // maintain the absolute size after transformation.
      double yaxis1w = yaxis1 == null ? 0 : yaxis1.getAxisSize() * xfactor;
      double yaxis2w = yaxis2 == null ? 0 : yaxis2.getAxisSize() * xfactor;
      double xaxis1h = xaxis1 == null ? 0 : xaxis1.getAxisSize() * yfactor;
      double xaxis2h = xaxis2 == null ? 0 : xaxis2.getAxisSize() * yfactor;

      checkFill();

      if(xscale != null && yscale != null) {
         if(xscale.getAxisSpec().getAxisStyle() == AxisSpec.AXIS_CROSS) {
            xaxis1h = 0;
         }

         if(yscale.getAxisSpec().getAxisStyle() == AxisSpec.AXIS_CROSS) {
            yaxis1w = 0;
         }
      }

      double plotw = w - yaxis1w - yaxis2w;
      plotw = plotw < 0 ? 0 : plotw;
      double ploth = h - xaxis1h - xaxis2h;
      ploth = ploth < 0 ? 0 : ploth;
      double plotx = x + yaxis1w;
      double ploty = y + xaxis1h;
      double plotw2 = plotw; // width minus the elements outside of bounds
      double ploth2 = ploth;
      double plotx2 = plotx;
      double ploty2 = ploty;

      // if facet exist, we should use facet to compute plot bounds so the
      // labels on the outer axes are centered on the plotting area instead
      // of the entire graph
      if(facet != null) {
         double leftw = facet.getAxisSize(LEFT_AXIS);
         double rightw = facet.getAxisSize(RIGHT_AXIS);
         double toph = facet.getAxisSize(TOP_AXIS);
         double bottomh = facet.getAxisSize(BOTTOM_AXIS);
         plotx2 = x + leftw;
         plotw2 = w - leftw - rightw;
         ploth2 = h - toph - bottomh;
         ploty2 = y + bottomh;
      }

      // for locked aspect ratio of background image
      if(getPlotSpec().isLockAspect()) {
         double[] whpair = applyAspectRatio(plotw, ploth);
         plotw = whpair[0];
         ploth = whpair[1];

         if(facet == null) {
            plotw2 = plotw;
            ploth2 = ploth;
         }
      }

      if(vgraph != null && vgraph.isCancelled()) {
         return;
      }

      if(vgraph != null) {
         // vgraph transform
         AffineTransform trans = new AffineTransform();

         trans.translate(plotx, ploty);
         trans.scale(plotw / GWIDTH, ploth / GHEIGHT);
         vgraph.concat(trans, true);

         // set the plot bounds before c2 transformation so the
         // getElementBounds can get the right boundary
         vgraph.setPlotBounds(new Rectangle2D.Double(plotx, ploty,
                                                     plotw + getDepth(),
                                                     ploth + getDepth()));

         // Check if the element is outside of drawing area, and re-scale if
         // necessary. This must be called after the cgraph.transform() is
         // called so the size report is already transformed.
         // left/right/bottom/top are the amount outside of the drawing area.
         Rectangle2D box = getElementBounds(vgraph, true);

         if(box != null && facet == null && !box.isEmpty()) {
            double left = getElementMargin(plotx - box.getX());
            double bottom = getElementMargin(ploty - box.getY());
            double right = getElementMargin(box.getMaxX() - plotx - plotw);
            double top = getElementMargin(box.getMaxY() - ploty - ploth);

            if(left + right + top + bottom > 0) {
               double xs = plotw / (plotw + left + right);
               double ys = ploth / (ploth + top + bottom);
               plotw2 = plotw * xs;
               ploth2 = ploth * ys;

               if(getPlotSpec().isLockAspect()) {
                  double[] whpair = applyAspectRatio(plotw2, ploth2);
                  plotw2 = whpair[0];
                  ploth2 = whpair[1];

                  // if the plotw2/ploth2 is changed in applyAspectRatio,
                  // reverse calculate the xs/ys (scale factor)
                  xs = plotw2 / plotw;
                  ys = ploth2 / ploth;
               }

               plotx2 = plotx + left * xs;
               ploty2 = ploty + bottom * ys;

               AffineTransform trans2 = new AffineTransform();
               trans2.translate(plotx2, ploty2);
               trans2.scale(xs, ys);
               trans2.translate(-plotx, -ploty);
               vgraph.concat(trans2, false);
            }
         }

         // set plot bounds with full c2 transformation.
         Rectangle2D pbox = getBounds(plotx, ploty, plotw, ploth, c2);
         pbox = new Rectangle2D.Double(pbox.getX(), pbox.getY(),
                                       pbox.getWidth() + getDepth(),
                                       pbox.getHeight() + getDepth());
         vgraph.setPlotBounds(pbox);

         // this must be called after getElementBounds since getElementBounds
         // is supposed to return the sizes before the coord transformation.
         // the margins resulting from the getElementBounds will be transformed
         // by coord transformation.
         vgraph.concat(c2, false);
      }

      if(vgraph != null && vgraph.isCancelled()) {
         return;
      }

      if(xaxis1 != null) {
         AffineTransform xtrans = new AffineTransform();
         int astyle = xscale.getAxisSpec().getAxisStyle();
         double y0 = (astyle == AxisSpec.AXIS_CROSS && yscale != null)
            ? getZeroPosition(yscale, ploty2, ploth2) : ploty;

         xtrans.translate(plotx2, y0);
         xtrans.scale(plotw2 / GWIDTH, 1);
         xtrans.preConcatenate(c2);
         xaxis1.setScreenTransform(xtrans);

         // allow x axis to use the full width since the x region include
         // the full coord bounds width
         if(getParentCoordinate() == null) {
            xaxis1.layout(getBounds(x, y, w, h, c2));
         }
         else {
            xaxis1.layout(getBounds(plotx, y, plotw, h, c2));
         }

         xaxis1.setBounds(getAxisBounds(plotx, ploty-xaxis1h, plotw, xaxis1h,
                                        c2, 'x'));
      }

      if(vgraph != null && vgraph.isCancelled()) {
         return;
      }

      if(xaxis2 != null) {
         // match how 2.5d coords are laid out
         double x2 = (facet != null) ? plotx2 + facet.getDepth() : plotx2;
         double w2 = (facet != null) ? plotw2 - facet.getDepth() : plotw2;
         AffineTransform xtrans = new AffineTransform();

         xtrans.translate(x2, ploty + ploth);
         xtrans.scale(w2 / GWIDTH, 1);
         xtrans.concatenate(GDefaults.FLIPY);
         xtrans.preConcatenate(c2);
         xaxis2.setScreenTransform(xtrans);

         // recreate the gridlines so the positions are correct. We could
         // transform the lines already created in createAxis too, but that
         // needs to handle the case where the axis has different length
         // than the plot box due to vo out of bounds condition. It's easier
         // to just create the lines again without transformation.
         // facet grid created by FacetCoord.
         if(facet == null) {
            xaxis2.createGridLines(xaxis1, true);
         }

         if(getParentCoordinate() == null) {
            xaxis2.layout(getBounds(x, y, w, h, c2));
         }
         else {
            xaxis2.layout(getBounds(plotx, y, plotw, h, c2));
         }

         xaxis2.setBounds(getAxisBounds(plotx, ploty + ploth, plotw,
                                        xaxis2h, c2, 'x'));
      }

      if(vgraph != null && vgraph.isCancelled()) {
         return;
      }

      double maxdepth = getDepth();

      if(yaxis1 != null && !isAxisLabelVisible(RIGHT_AXIS) &&
         GTool.isHorizontal(yaxis1.getScreenTransform()))
      {
         maxdepth = 0;
      }

      if(yaxis1 != null) {
         AffineTransform ytrans = new AffineTransform();
         int astyle = yscale.getAxisSpec().getAxisStyle();
         double x0 = (astyle == AxisSpec.AXIS_CROSS && xscale != null)
            ? getZeroPosition(xscale, plotx2, plotw2) : plotx;

         ytrans.translate(x0, ploty2);
         ytrans.rotate(Math.PI / 2);
         ytrans.scale(ploth2 / GHEIGHT, 1);
         ytrans.concatenate(GDefaults.FLIPY);
         ytrans.preConcatenate(c2);
         yaxis1.setScreenTransform(ytrans);
         yaxis1.layout(getBounds(x, ploty, w, ploth + maxdepth, c2));
         yaxis1.setBounds(getAxisBounds(plotx - yaxis1w, ploty, yaxis1w, ploth, c2, 'y'));
      }

      if(vgraph != null && vgraph.isCancelled()) {
         return;
      }

      if(yaxis2 != null) {
         AffineTransform ytrans = new AffineTransform();

         ytrans.translate(plotx + plotw, ploty2);
         ytrans.rotate(Math.PI / 2);
         ytrans.scale(ploth2 / GHEIGHT, 1);
         ytrans.preConcatenate(c2);
         yaxis2.setScreenTransform(ytrans);

         // facet grid created by FacetCoord.
         if(facet == null) {
            if(!yaxis2.createGridLines(yaxis1, true) && yscale2 != null) {
               yaxis1.createGridLines(yaxis2, true);
            }
         }

         yaxis2.layout(getBounds(x, ploty, w, ploth + maxdepth, c2));
         yaxis2.setBounds(getAxisBounds(plotx + plotw, ploty, yaxis2w, ploth, c2, 'y'));
      }

      if(vgraph != null && vgraph.isCancelled()) {
         return;
      }

      // remove first and last grid line if they are too closed with axis
      if(xaxis2 != null) {
         int n = xaxis2.getGridLineCount();

         // because the java graphics paint at the integer, we should use the
         // Math.floor to decrease the precision.
         if(Math.floor((plotx2 - plotx) / xfactor) <= AXIS_GRID_MIN_GAP) {
            removeMinGridLine(xaxis2);
         }

         if(Math.floor((plotx + plotw - plotx2 - plotw2) / xfactor)
            <= AXIS_GRID_MIN_GAP && n > 1)
         {
            removeMaxGridLine(xaxis2);
         }
      }

      if(vgraph != null && vgraph.isCancelled()) {
         return;
      }

      if(yaxis2 != null) {
         int n = yaxis2.getGridLineCount();

         if(Math.floor((ploty2 - ploty) / yfactor) <= AXIS_GRID_MIN_GAP) {
            removeMinGridLine(yaxis2);
         }

         if(Math.floor((ploty + ploth - ploty2 - ploth2) / yfactor)
            <= AXIS_GRID_MIN_GAP && n > 1)
         {
            removeMaxGridLine(yaxis2);
         }
      }

      if(vgraph != null && vgraph.isCancelled()) {
         return;
      }

      createCoordBorder(vgraph, plotx, ploty, plotw, ploth,
                        plotx2, ploty2, plotw2, ploth2, c2);
      layoutText(vgraph, true);
   }

   private void checkFill() {
      checkFill(xaxis1);
      checkFill(xaxis2);
      checkFill(yaxis1);
      checkFill(yaxis2);
   }

   // fill an axis with one tick causes it to be pushed to the left and the rest of the
   // space empty. (58350)
   private void checkFill(Axis axis) {
      if(axis instanceof DefaultAxis && axis.getScale() instanceof CategoricalScale &&
         !((DefaultAxis) axis).isCenterLabel() && axis.getTicks().length < 2)
      {
         ((CategoricalScale) axis.getScale()).setFill(false);
      }
   }

   /**
    * Adjust the axis bounds for depth.
    */
   private Rectangle2D getAxisBounds(double x, double y, double w, double h,
                                     AffineTransform trans, char type)
   {
      Rectangle2D box = getBounds(x, y, w, h, trans);
      boolean rotated = !GTool.isHorizontal(trans);

      if(getDepth() > 0) {
         if(type == 'x' && !rotated || type == 'y' && rotated) {
            box = new Rectangle2D.Double(box.getX(), box.getY(),
                                         box.getWidth() + getDepth(),
                                         box.getHeight());
         }
         else {
            box = new Rectangle2D.Double(box.getX(), box.getY(), box.getWidth(),
                                         box.getHeight() + getDepth());
         }
      }

      return box;
   }

   /**
    * Get the element bounds in the vgraph, without adjusting for rescale.
    */
   @Override
   Rectangle2D getElementBounds(VGraph vgraph) {
      Rectangle2D bounds = super.getElementBounds(vgraph);
      Rectangle2D plotbounds = vgraph.getPlotBounds();

      if(xscale != null && yscale != null && bounds != null) {
         if(xscale.getAxisSpec().getAxisStyle() == AxisSpec.AXIS_CROSS) {
            double zero = getZeroPosition(yscale, 0, plotbounds.getHeight());
            double h = xaxis1.getAxisSize();
            Rectangle2D axisbox = new Rectangle2D.Double(
               bounds.getX(), zero - h, 1, h);

            bounds = bounds.createUnion(axisbox);
         }

         if(yscale.getAxisSpec().getAxisStyle() == AxisSpec.AXIS_CROSS) {
            double zero = getZeroPosition(xscale, 0, plotbounds.getWidth());
            double w = yaxis1.getAxisSize();
            Rectangle2D axisbox = new Rectangle2D.Double(
               zero - w, bounds.getY(), w, 1);

            bounds = bounds.createUnion(axisbox);
         }
      }

      double ymargin = (yscale != null) ? getAxisMargin(yaxis1) : 0;
      double xmargin = (xscale != null) ? getAxisMargin(xaxis1) : 0;
      double top = 0;
      double left = 0;
      double bottom = 0;
      double right = 0;

      if(isReversed(yscale)) {
         bottom = ymargin;
      }
      else {
         top = ymargin;
      }

      if(isReversed(xscale)) {
         left = xmargin;
      }
      else {
         right = xmargin;
      }

      if(top > 0 || left > 0 || bottom > 0 || right > 0) {
         double x = getCoordBounds().getX();
         double y = getCoordBounds().getY();
         double w = getCoordBounds().getWidth() - getDepth();
         double h = getCoordBounds().getHeight() - getDepth();
         AffineTransform c2 = getScaledCoordTransform(x, y, w, h);
         double xfactor = GTool.getScaleFactor(c2, 0);
         double yfactor = GTool.getScaleFactor(c2, 90);
         double topDepth = getDepth();

         // depth is overlapped on the right in facet, don't subtract it
         // unless this is the right-most sub-graph
         if(GTool.isHorizontal(yaxis1.getScreenTransform()) &&
            !isAxisLabelVisible(RIGHT_AXIS))
         {
            topDepth = 0;
         }

         Rectangle2D axisbox = new Rectangle2D.Double(
            plotbounds.getX() - left * xfactor,
            plotbounds.getY() - bottom * yfactor,
            plotbounds.getWidth() - getDepth() + right * xfactor,
            plotbounds.getHeight() - topDepth + top * yfactor);

         if(bounds == null) {
            bounds = axisbox;
         }
         else {
            bounds = bounds.createUnion(axisbox);
         }
      }

      return bounds;
   }

   /**
    * Check if the scale is reversed.
    */
   private boolean isReversed(Scale scale) {
      return scale instanceof LinearScale && ((LinearScale) scale).isReversed();
   }

   /**
    * Get the top margin for axis max label.
    */
   private double getAxisMargin(DefaultAxis axis) {
      if(axis == null) {
         return 0;
      }

      Scale scale = axis.getScale();

      if(!scale.getAxisSpec().isInPlot()) {
         return 0;
      }

      double[] ticks = scale.getTicks();

      if(ticks.length > 0 && ticks[ticks.length - 1] == scale.getMax()) {
         VLabel[] labels = axis.getLabels();
         boolean hor = GTool.isHorizontal(axis.getScreenTransform());

         if(labels != null && labels.length > 0) {
            int n = isReversed(axis.getScale()) ? 0 : labels.length - 1;
            return hor ? labels[n].getPreferredWidth() / 2
               : labels[n].getPreferredHeight() / 2;
         }
      }

      return 0;
   }

   /**
    * Get the size that is unscaled by screen transformation.
    * @return the unscaled sizes in an array, top, left, bottom, right.
    */
   @Override
   double[] getUnscaledSizes(VGraph vgraph) {
      if(unscaledSizes2 != null) {
         return unscaledSizes2;
      }

      double[] sizes = super.getUnscaledSizes(vgraph);

      if(xscale != null && yscale != null) {
         if(xscale.getAxisSpec().getAxisStyle() == AxisSpec.AXIS_CROSS) {
            sizes[2] = Math.max(sizes[2], xaxis1.getAxisSize());
         }

         if(yscale.getAxisSpec().getAxisStyle() == AxisSpec.AXIS_CROSS) {
            sizes[1] = Math.max(sizes[1], xaxis1.getAxisSize());
         }
      }

      return unscaledSizes2 = sizes;
   }

   /**
    * Get a vgraph for calculating element bounds.
    */
   @Override
   VGraph getScaledVGraph() {
      if(scaledVGraph != null) {
         return scaledVGraph;
      }

      VGraph vgraph = (VGraph) getVGraph().clone();

      // set up the graph, the following logic must match fit
      double x = getCoordBounds().getX();
      double y = getCoordBounds().getY();
      double w = getCoordBounds().getWidth() - getDepth();
      double h = getCoordBounds().getHeight() - getDepth();
      AffineTransform c2 = getScaledCoordTransform(x, y, w, h);

      double xfactor = GTool.getScaleFactor(c2, 0);
      double yfactor = GTool.getScaleFactor(c2, 90);
      double yaxis1w = yaxis1 == null ? 0 : yaxis1.getAxisSize() * xfactor;
      double yaxis2w = yaxis2 == null ? 0 : yaxis2.getAxisSize() * xfactor;
      double xaxis1h = xaxis1 == null ? 0 : xaxis1.getAxisSize() * yfactor;
      double xaxis2h = xaxis2 == null ? 0 : xaxis2.getAxisSize() * yfactor;
      double plotw = w - yaxis1w - yaxis2w;
      double ploth = h - xaxis1h - xaxis2h;
      double plotx = x + yaxis1w;
      double ploty = y + xaxis1h;

      // vgraph transform
      AffineTransform trans = new AffineTransform();

      trans.translate(plotx, ploty);
      trans.scale(plotw / GWIDTH, ploth / GHEIGHT);
      vgraph.concat(trans, true);

      // for locked aspect ratio of background image
      if(getPlotSpec().isLockAspect()) {
         double[] whpair = applyAspectRatio(plotw, ploth);
         plotw = whpair[0];
         ploth = whpair[1];
      }

      Rectangle2D pbounds = new Rectangle2D.Double(plotx, ploty,
                                                   plotw + getDepth(),
                                                   ploth + getDepth());
      vgraph.setPlotBounds(pbounds);
      // the mergeElementBounds needs plot bounds, setting here is safe
      // as long as the getElementBounds is called before fit
      getVGraph().setPlotBounds(pbounds);

      return scaledVGraph = vgraph;
   }

   /**
    * Make sure the size is the same aspect ratio of the background image.
    */
   private double[] applyAspectRatio(double plotw, double ploth) {
      Image img = getPlotSpec().getBackgroundImage();

      if(img != null) {
         CoreTool.waitForImage(img);
         double ratio = (double) img.getWidth(null) / img.getHeight(null);

         if(plotw / ploth > ratio) {
            plotw = ratio * ploth;
         }
         else {
            ploth = plotw / ratio;
         }
      }

      return new double[] {plotw, ploth};
   }

   /**
    * Create a border along the axes.
    */
   void createCoordBorder(VGraph vgraph, double plotx, double ploty,
                          double plotw, double ploth, double plotx2,
                          double ploty2, double plotw2, double ploth2,
                          AffineTransform c2)
   {
      if(vgraph == null || facet != null || xscale == null && yscale == null) {
         return;
      }

      // if axes are around the plot area, add a box. This is only unecessary if
      // the axis are scaled to be less than the plot area, due to elements
      // out of bounds in the original scale.
      if(xaxis2 != null && xaxis2.getZIndex() >= 0 && xaxis2.isLineVisible() &&
         yaxis2 != null && yaxis2.getZIndex() >= 0 && yaxis2.isLineVisible() &&
         plotw2 == plotw && ploth2 == ploth)
      {
         return;
      }

      boolean xhide = xaxis1 != null && !xaxis1.isLineVisible();
      boolean yhide = yaxis1 != null && !yaxis1.isLineVisible();
      boolean y2hide = yaxis2 != null && !yaxis2.isLineVisible();

      // if axis line is not visible, don't add box
      // if an axis line is explicitly hide, don't add a border otherwise
      // it looks like the axis line visibility setting is not working
      if((xaxis1 == null && yaxis1 == null) || xhide || yhide || y2hide) {
         return;
      }

      int xstyle = (xscale != null) ? xscale.getAxisSpec().getAxisStyle()
         : AxisSpec.AXIS_DOUBLE;
      int ystyle = (yscale != null) ? yscale.getAxisSpec().getAxisStyle()
         : AxisSpec.AXIS_DOUBLE;
      boolean xdouble = (xstyle & 0xf) == AxisSpec.AXIS_DOUBLE;
      boolean ydouble = (ystyle & 0xf) == AxisSpec.AXIS_DOUBLE;
      boolean xcross = xstyle == AxisSpec.AXIS_CROSS;
      boolean ycross = ystyle == AxisSpec.AXIS_CROSS;

      // not double style, don't add box
      if(!xdouble || !ydouble) {
         if(xcross && ydouble || ycross && xdouble) {
            // draw border if double on one side and cross the other. otherwise
            // it looks like the box is broken
         }
         else { // don't add border
            return;
         }
      }

      DefaultAxis xaxis1 = this.xaxis1;
      DefaultAxis yaxis1 = this.yaxis1;
      DefaultAxis xaxis2 = (this.xaxis2 == null) ? xaxis1 : this.xaxis2;
      DefaultAxis yaxis2 = (this.yaxis2 == null) ? yaxis1 : this.yaxis2;

      // if xaxis2 is null then xaxis1 is null too.
      // when that's the case then use yaxis2 to get the line color
      // same logic for yaxis2
      if(xaxis2 == null) {
         xaxis1 = xaxis2 = yaxis2;
      }
      else if(yaxis2 == null) {
         yaxis1 = yaxis2 = xaxis2;
      }

      createLine(new Point2D.Double(0, 0), new Point2D.Double(plotw, 0),
                 xaxis1, c2, plotx, ploty, vgraph);
      createLine(new Point2D.Double(0, ploth), new Point2D.Double(plotw, ploth),
                 xaxis2, c2, plotx, ploty, vgraph);

      createLine(new Point2D.Double(0, 0), new Point2D.Double(0, ploth),
                 yaxis1, c2, plotx, ploty, vgraph);
      createLine(new Point2D.Double(plotw, 0), new Point2D.Double(plotw, ploth),
                 yaxis2, c2, plotx, ploty, vgraph);
   }

   /**
    * Create line.
    */
   private void createLine(Point2D pt1, Point2D pt2, DefaultAxis axis,
      AffineTransform c2, double plotx, double ploty, VGraph vgraph)
   {
      if(axis != null && !axis.isLineVisible()) {
         return;
      }

      Color clr = (axis != null) ? axis.getLineColor() : GDefaults.DEFAULT_LINE_COLOR;
      LineForm form = new LineForm(pt1, pt2);
      form.setColor(clr);

      FormVO vo = (FormVO) form.createVisual(this);
      AffineTransform trans = new AffineTransform();

      trans.translate(plotx, ploty);
      trans.preConcatenate(c2);
      vo.setFixedPosition(false);
      vo.setScreenTransform(trans);
      vo.setZIndex(GDefaults.COORD_BORDER_Z_INDEX);
      vgraph.addVisual(vo);
   }

   /**
    * Get the maximum width of an item in this coordinate without overlapping.
    */
   @TernMethod
   @Override
   public double getMaxWidth() {
      if(xscale == null) {
         return GWIDTH;
      }

      return GWIDTH / xscale.getUnitCount();
   }

   /**
    * Get the maximum height of an item in this coordinate without overlapping.
    */
   @TernMethod
   @Override
   public double getMaxHeight() {
      if(yscale == null) {
         return GHEIGHT;
      }

      return GHEIGHT / yscale.getUnitCount();
   }

   /**
    * Get the number of dimensions in this coordinate.
    */
   @TernMethod
   @Override
   public int getDimCount() {
      int cnt = 0;

      if(xscale != null) {
         cnt++;
      }

      if(yscale != null) {
         cnt++;
      }

      return cnt;
   }

   /**
    * Get the scales used in the coordinate.
    */
   @TernMethod
   @Override
   public Scale[] getScales() {
      List<Scale> vec = new ArrayList<>();

      if(xscale != null) {
         vec.add(xscale);
      }

      if(yscale != null) {
         vec.add(yscale);
      }

      if(yscale2 != null) {
         vec.add(new SecondaryScale());
      }

      return vec.toArray(new Scale[0]);
   }

   /**
    * Get the x scale used in the coordinate.
    */
   @TernMethod
   public Scale getXScale() {
      return xscale;
   }

   /**
    * Set the x scale used in the coordinate.
    */
   @TernMethod
   public void setXScale(Scale xscale) {
      this.xscale = xscale;
   }

   /**
    * Get the y scale used in the coordinate.
    */
   @TernMethod
   public Scale getYScale() {
      return yscale;
   }

   /**
    * Set the y scale used in the coordinate.
    */
   @TernMethod
   public void setYScale(Scale yscale) {
      this.yscale = yscale;
   }

   /**
    * Get the secondary y scale used in the coordinate.
    */
   @TernMethod
   public Scale getYScale2() {
      return yscale2;
   }

   /**
    * Set the secondary y scale used in the coordinate. The secondary y scale
    * is used to create the secondary Y axis. It is also used to scale any
    * variable that is associated with the scale. It's up to the caller to
    * decide whether a variable is scaled on the secondary or primary by
    * setting the fields on the scale.
    */
   @TernMethod
   public void setYScale2(Scale yscale2) {
      this.yscale2 = yscale2;
   }

   /**
    * Create graph geometry (nested chart) for this coordinate.
    * @hidden
    * @param rset the specified root data set.
    * @param ridx the specified index for root data set.
    * @param lens dataset of this graph. This may be a subset if this coordinate
    * is a nested coordinate.
    * @param egraph the top-level graph definition.
    * @param ggraph the parent ggraph.
    * @param facet the specified facet coordinate.
    * @param hint the specified position hint.
    */
   @Override
   public void createSubGGraph(DataSet rset, DataSetIndex ridx, DataSet lens,
                               EGraph egraph, GGraph ggraph, FacetCoord facet,
                               Map xmap, Map ymap, int hint)
   {
      setAxisLabelVisible(TOP_AXIS, (hint & TOP_MOST) != 0);
      setAxisLabelVisible(RIGHT_AXIS, (hint & RIGHT_MOST) != 0);
      setAxisLabelVisible(LEFT_AXIS, (hint & LEFT_MOST) != 0);
      setAxisLabelVisible(BOTTOM_AXIS, (hint & BOTTOM_MOST) != 0);

      if(xscale != null && !(xscale instanceof CategoricalScale) ||
         yscale != null && !(yscale instanceof CategoricalScale))
      {
         throw new RuntimeException("Nested coordinate with non-categorical " +
                                    "scales are not supported!");
      }

      boolean topFacet = ridx.getIndices().isEmpty();
      VGraph vgraph = getVGraph();
      Object[] xvals = xscale == null ? new Object[1] : xscale.getValues();
      Object[] yvals = yscale == null ? new Object[1] : yscale.getValues();
      String[] xcols = xscale == null ? new String[0] : xscale.getFields();
      String[] ycols = yscale == null ? new String[0] : yscale.getFields();
      TileCoord minner = facet.getProtoInnerCoord();
      boolean xnull = xscale == null;
      boolean ynull = yscale == null;
      Set<String> keys = new HashSet<>();

      if(xnull && ynull) {
         facet.setTileCoords(new TileCoord[0][0]);
         return;
      }

      if(xcols.length > 0) {
         keys.add(xcols[0]);
      }

      if(ycols.length > 0) {
         keys.add(ycols[0]);
      }

      // create sub data set index
      DataSetIndex lidx = new DataSetIndex(lens, keys, false);

      // maintain root data set index
      ridx.addIndices(keys);
      ridx.addIndices(xmap.keySet());
      ridx.addIndices(ymap.keySet());

      // avoid excessive subgraphs
      String mstr = GTool.getProperty("graph.subgraph.maxcount",
                                      GDefaults.SUBGRAPH_MAX_COUNT + "");
      int maxCount = Integer.parseInt(mstr);
      int xcount = xvals.length;
      int ycount = yvals.length;

      Map xMaxKey = new HashMap(xmap);
      xMaxKey.put("_axis_fields_", Arrays.toString(xcols));
      Map yMaxKey = new HashMap(ymap);
      yMaxKey.put("_axis_fields_", Arrays.toString(ycols));

      boolean changed = false;
      // get the already reduced count to ensure the sub-graphs have same dimensions
      // across rows/columns.
      Integer xMaxCount = ridx.getXMaxCount(xMaxKey);
      Integer yMaxCount = ridx.getYMaxCount(yMaxKey);

      if(xMaxCount != null) {
         xcount = xMaxCount;
      }

      if(yMaxCount != null) {
         ycount = yMaxCount;
      }

      if(xMaxCount == null || yMaxCount == null) {
         if(xcount * ycount > maxCount) {
            int ox = xcount;
            int oy = ycount;

            if(xMaxCount != null) {
               ycount = Math.max(1, maxCount / xcount);
            }
            else if(yMaxCount != null) {
               xcount = Math.max(1, maxCount / ycount);
            }
            else if(xcount < ycount) {
               xcount = xcount > maxCount ? Math.min(50, xvals.length) : xcount;
               ycount = Math.max(1, maxCount / xcount);
            }
            else {
               ycount = ycount > maxCount ? Math.min(50, yvals.length) : ycount;
               xcount = Math.max(1, maxCount / ycount);
            }

            changed = ox != xcount || oy != ycount;
         }
         else {
            int maxAllCount = maxCount * 5;
            int nestedX = getAllXCount();
            int nestedY = getAllYCount();

            while(xcount * nestedX * ycount * nestedY > maxAllCount) {
               if(xcount <= 1 && ycount <= 1) {
                  break;
               }

               int ox = xcount;
               int oy = ycount;

               // only y can be changed
               if(xMaxCount != null) {
                  if(ycount > 1) {
                     ycount--;
                  }
               }
               // only x can be changed
               else if(yMaxCount != null) {
                  if(xcount > 1) {
                     xcount--;
                  }
               }
               else if(ycount > xcount) {
                  ycount--;
               }
               else {
                  xcount--;
               }

               if(ox != xcount || oy != ycount) {
                  changed = true;
               }
               else {
                  break;
               }
            }
         }
      }

      // set the count to ensure all sub-graphs share the same dimension
      if(xMaxCount == null) {
         ridx.setXMaxCount(xMaxKey, xcount);
      }

      if(yMaxCount == null) {
         ridx.setYMaxCount(yMaxKey, ycount);
      }

      if(changed) {
         setMaxCount(xscale, xcount);
         setMaxCount(yscale, ycount);

         String msg = GTool.getString(
            "viewer.viewsheet.chart.nestedMax", (double) maxCount) +
            ": " + GTool.getGraphId(GTool.getTopCoordinate(this));

         // showing a warning to end user is more distractive than
         // helpful. in any case, when the max is reached, the graph
         // is generally so over crowded that any truncation is
         // unlikely to be noticeable or significant
         LOG.debug(msg);
      }

      pushCardinality(xcount, ycount);

      try {
         TileCoord[][] inners = new TileCoord[ycount][xcount];
         boolean hasX = xscale != null && xcols.length > 0;
         boolean hasY = yscale != null && ycols.length > 0;

         for(int i = 0; i < ycount; i++) {
            Map symap = new HashMap(ymap);
            int hint2 = hint;

            if(yscale != null && ycols.length > 0) {
               symap.put(ycols[0], yvals[i]);
            }

            if(i != 0) {
               hint2 &= (~BOTTOM_MOST);
            }

            if(i != inners.length - 1) {
               hint2 &= (~TOP_MOST);
            }

            for(int j = 0; j < xcount; j++) {
               Map sxmap = new HashMap(xmap);
               Map map = new HashMap();
               int hint3 = hint2;

               if(vgraph != null && vgraph.isCancelled()) {
                  return;
               }

               if(hasY) {
                  map.put(ycols[0], yvals[i]);
               }

               if(hasX) {
                  map.put(xcols[0], xvals[j]);
                  sxmap.put(xcols[0], xvals[j]);
               }

               if(j != 0) {
                  hint3 &= (~LEFT_MOST);
               }

               if(j != inners[i].length - 1) {
                  hint3 &= (~RIGHT_MOST);
               }

               TileCoord inner = (TileCoord) minner.clone();
               Coordinate[] coords = inner.getCoordinates();

               inner.setHint(hint3);
               inner.setParentCoordinate(this);

               if(hasX) {
                  inner.addParentValue(xcols[0], xvals[j], true);
               }

               if(hasY) {
                  inner.addParentValue(ycols[0], yvals[i], false);
               }

               boolean innermost = inner.getCoordinates()[0] instanceof RectCoord;
               DataSet dset = createSubDataSet(map, lidx, innermost);

               if(dset.isDisposed()) {
                  return;
               }

               inners[i][j] = inner;

               if(egraph.isScatterMatrix()) {
                  setScatterMatrixScales(egraph, inner, xvals[j], yvals[i], dset);
               }

               initScales(inner, ridx, dset, sxmap, symap, egraph);
               lidx.clearSubDataSetDuplicates(dset);

               // create facet graph
               if(inner.isFacetCoord()) {
                  FacetCoord fcoord = inner.getFacetCoord();
                  fcoord.createGGraph(rset, ridx, fcoord.getDataSet(), egraph,
                                      ggraph, sxmap, symap, hint3);
               }
               // create non-facet graph
               else {
                  double[] tuple;

                  if(!hasX && hasY) {
                     tuple = new double[]{ yscale.map(yvals[i]) };
                  }
                  else if(hasX && !hasY) {
                     tuple = new double[]{ xscale.map(xvals[j]) };
                  }
                  else {
                     tuple = new double[]{ xscale.map(xvals[j]), yscale.map(yvals[i]) };
                  }

                  for(int k = 0; k < coords.length; k++) {
                     Coordinate coord = coords[k];
                     GGraph subggraph = coord.createGGraph(rset, ridx, dset,
                                                           egraph, null, sxmap, symap, -1);
                     GraphGeometry geom = new GraphGeometry(subggraph, this, coord, tuple);
                     ggraph.addGeometry(geom);
                  }
               }
            }
         }

         // this is problematic if performed in sub-facet because it could result in uneven
         // sub-facets (sub-facets having different dimension), which violate the contract
         // of facet chart. we may need to have this logic moved up the point where the entire
         // facet has been generated to avoid this problem.
         if(topFacet) {
            // remove empty sub-graph created from change-from-previous
            TileCleaner cleaner = new TileCleaner(xscale, yscale, inners, egraph, ggraph);
            inners = cleaner.clean();
         }

         facet.setTileCoords(inners);
         this.facet = facet;
      }
      finally {
         popCardinality();
      }
   }

   // for scatter plot, share the scale only on row/col instead of across
   // the entire graph
   private void setScatterMatrixScales(EGraph egraph, TileCoord inner,
                                       Object xval, Object yval, DataSet dset)
   {
      for(Coordinate coord : inner.getCoordinates()) {
         if(coord instanceof RectCoord) {
            RectCoord rect = (RectCoord) coord;
            Scale xscale2 = rect.getXScale();
            Scale yscale2 = rect.getYScale();
            // true if the empty chart in the cross axis
            boolean empty = dset.getRowCount() == 0 && CoreTool.equals(xval, yval);

            if(xscale2 != null && xscale2.getFields().length == 1 &&
               xscale2.getFields()[0].equals(PairsDataSet.XMEASURE_VALUE))
            {
               if((xscale2 = egraph.getScale(xval + ".x")) != null) {
                  rect.setXScale(createScatterMatrixScale(
                                    xscale2, PairsDataSet.XMEASURE_VALUE,
                                    xval + "", empty));
               }
            }

            if(yscale2 != null && yscale2.getFields().length == 1 &&
               yscale2.getFields()[0].equals(PairsDataSet.YMEASURE_VALUE))
            {
               if((yscale2 = egraph.getScale(yval + ".y")) != null) {
                  rect.setYScale(createScatterMatrixScale(
                                    yscale2, PairsDataSet.YMEASURE_VALUE,
                                    yval + "", empty));
               }
            }
         }
      }
   }

   // setup a scale for the inner scatter plot on a matrix
   private Scale createScatterMatrixScale(Scale scale, String field,
                                          String measure, boolean hideGrid)
   {
      scale = (Scale) scale.clone();
      scale.setFields(field);
      scale.setMeasure(measure);

      if(hideGrid) {
         AxisSpec axis = (AxisSpec) scale.getAxisSpec().clone();
         axis.setGridStyle(GraphConstants.NONE);
         scale.setAxisSpec(axis);
      }

      return scale;
   }

   /**
    * Create a subset with only the values specified in the map.
    */
   private DataSet createSubDataSet(Map map, DataSetIndex didx, boolean innermost) {
      return didx.createSubDataSet(map, innermost);
   }

   /**
    * Init a subset.
    */
   private void initSubDataSet(EGraph egraph, String[] dims, DataSet data, Coordinate coord,
                               boolean innermost, boolean calcMeasures)
   {
      String dim = null;

      if(innermost) {
         // find real dimension, for radar measure is dim
         for(int i = dims.length - 1; i >= 0; i--) {
            int idx = data.indexOfHeader(dims[i]);

            if(idx < 0) {
               continue;
            }

            if(!data.isMeasure(dims[i])) {
               dim = dims[i];
               break;
            }
         }

         data.prepareGraph(egraph, coord, null);
      }

      data.prepareCalc(dim, null, calcMeasures);
   }

   /**
    * Initialize the scales of the inner coords.
    */
   private void initScales(TileCoord inner, DataSetIndex ridx, DataSet sublens,
                           Map xmap, Map ymap, EGraph egraph)
   {
      Coordinate[] coords = inner.getCoordinates();
      boolean innermost = coords[0] instanceof RectCoord;
      DataSet xset = createSubDataSet(xmap, ridx, false);
      DataSet yset = createSubDataSet(ymap, ridx, false);
      String[] alldims = GTool.getDims(egraph);

      for(int k = 0; k < coords.length; k++) {
         Coordinate coord = coords[k];

         if(coord instanceof RectCoord) {
            // need measure value if trimming categorical items with null measure values
            boolean trimNull = isTrimNull(coord);

            // x/y axis are always categorical in facet, so we can ignore measure
            // calc columns as an optimization.
            initSubDataSet(egraph, alldims, xset, coord, innermost, trimNull);
            initSubDataSet(egraph, alldims, yset, coord, innermost, trimNull);
            initSubDataSet(egraph, alldims, sublens, coord, !inner.isFacetCoord(), true);
            initRectCoordScales((RectCoord) coord, getInnerDataSet(xset), getInnerDataSet(yset),
                                sublens, egraph);
         }
         else if(coord instanceof FacetCoord) {
            FacetCoord fcoord = (FacetCoord) coord;
            // for facet coord, outer requires to be initialized with xset or yset
            Coordinate ocoord = fcoord.getOuterCoordinate();

            if(ocoord instanceof RectCoord) {
               // if any sub-graph trim null, we should make sure the calc columns are populated
               // so the scales will be properly initialized. (51285)
               boolean trimNull = isTrimNull(coord);
               initSubDataSet(egraph, alldims, xset, coord, innermost, trimNull);
               initSubDataSet(egraph, alldims, yset, coord, innermost, trimNull);
               initSubDataSet(egraph, alldims, sublens, coord, !inner.isFacetCoord(), true);
               initRectCoordScales((RectCoord) ocoord, xset, yset, sublens, egraph);
            }
            else if(ocoord != null) {
               initSubDataSet(egraph, alldims, sublens, coord, !inner.isFacetCoord(), true);
               ocoord.init(sublens);
            }
         }
         else if(sublens != null) {
            initSubDataSet(egraph, alldims, sublens, coord, !inner.isFacetCoord(), true);
            coord.init(sublens);
         }

         coord.setDataSet(sublens);
      }
   }

   // get the dataset for sub-graph
   private DataSet getInnerDataSet(DataSet xset) {
      if(xset instanceof SubDataSet) {
         SubDataSet innerx = (SubDataSet) xset.clone();
         // project forard needs to apply in inner rect coord (49353).
         innerx.setInnermost(true);
         return innerx;
      }

      return xset;
   }

   private static boolean isTrimNull(Coordinate coord) {
      return Arrays.stream(coord.getScales()).anyMatch(scale -> isTrimNull(scale));
   }

   private static boolean isTrimNull(Scale scale) {
      return scale != null && ((scale.getScaleOption() & Scale.NO_LEADING_NULL_VAR) != 0 ||
         (scale.getScaleOption() & Scale.NO_TRAILING_NULL_VAR) != 0);
   }

   /**
    * Initialize the scales in rect coord.
    */
   private void initRectCoordScales(RectCoord coord, DataSet xset,
                                    DataSet yset, DataSet subset,
                                    EGraph egraph)
   {
      RectCoord rcoord = coord;
      Scale bscale = rcoord.getScaleAt(BOTTOM_AXIS);
      Scale lscale = rcoord.getScaleAt(LEFT_AXIS);
      Scale tscale = rcoord.getScaleAt(TOP_AXIS);
      Scale rscale = rcoord.getScaleAt(RIGHT_AXIS);

      initSubScale(bscale, subset, xset, egraph);
      initSubScale(lscale, subset, yset, egraph);

      if(tscale != bscale && tscale != null) {
         initSubScale(tscale, subset, xset, egraph);
      }

      if(rscale != lscale && rscale != null) {
         initSubScale(rscale, subset, yset, egraph);
      }
   }

   private void initSubScale(Scale bscale, DataSet subset, DataSet xset, EGraph egraph) {
      // for linear scale, min/max is shared, and
      // scale range requires sub lens instead of xset
      if(bscale instanceof LinearScale && subset != null) {
         Set<Integer> mapping = new TreeSet<>();
         Set scalefields = new HashSet<>(Arrays.asList(bscale.getFields()));

         // multi-aesthetic, find ranges of data used in graph
         for(int i = 0; i < egraph.getElementCount(); i++) {
            GraphElement elem = egraph.getElement(i);
            String[] vars = elem.getVars();

            if(scalefields.containsAll(Arrays.asList(vars))) {
               int start = elem.getStartRow(subset);
               int end = elem.getEndRow(subset);

               if(start >= 0 && end >= 0) {
                  for(int k = start; k < end; k++) {
                     mapping.add(k);
                  }
               }
            }
         }

         if(mapping.size() > 0) {
            int[] arr = mapping.stream().mapToInt(m -> m.intValue()).toArray();
            subset = new SubDataSet(subset, arr);
         }

         // if data is empty, it would causes the range to be set to 0 to 1
         // this essentially forces 0 on the scale even if ZERO is not true
         if((bscale.getScaleOption() & Scale.ZERO) != 0 || subset.getRowCount() > 0) {
            bscale.init(subset);
         }
      }
      else if(bscale != null) {
         Coordinate topCoord = GTool.getTopCoordinate(this);

         if(bscale instanceof CategoricalScale || bscale instanceof TimeScale) {
            Scale shared = topCoord.getSharedScale(bscale, xset);

            if(shared instanceof CategoricalScale) {
               ((CategoricalScale) bscale).copyValues((CategoricalScale) shared);
               return;
            }
            else if(shared instanceof TimeScale) {
               ((TimeScale) bscale).copyValues((TimeScale) shared);
               return;
            }
         }

         bscale.init(xset);
         topCoord.addSharedScale(bscale, xset);
      }
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

      RectCoord coord = (RectCoord) obj;

      if(xscale != null) {
         if(xscale instanceof LinearScale) {
            coord.xscale = ((LinearScale) xscale).clone(srange);
         }
         else {
            coord.xscale = xscale.clone();
         }
      }

      if(yscale != null) {
         if(yscale instanceof LinearScale) {
            coord.yscale = ((LinearScale) yscale).clone(srange);
         }
         else {
            coord.yscale = yscale.clone();
         }
      }

      if(yscale2 != null) {
         if(yscale2 instanceof LinearScale) {
            coord.yscale2 = ((LinearScale) yscale2).clone(srange);
         }
         else {
            coord.yscale2 = yscale2.clone();
         }
      }

      return coord;
   }

   /**
    * Get axis minimum width or height.
    * @hidden
    */
   @Override
   public double getAxisMinSize(int axis) {
      DefaultAxis aobj = getAxisAt(axis);

      if(aobj == null || !isAxisLabelVisible(axis)) {
         return 0;
      }

      return (axis == TOP_AXIS || axis == BOTTOM_AXIS) ? aobj.getMinHeight() : aobj.getMinWidth();
   }

   /**
    * Get axis preferred width or height.
    * @hidden
    */
   @Override
   public double getAxisPreferredSize(int axis) {
      DefaultAxis aobj = getAxisAt(axis);

      if(aobj == null) {
         return 0;
      }

      return (axis == TOP_AXIS || axis == BOTTOM_AXIS)
         ? aobj.getPreferredHeight() : aobj.getPreferredWidth();
   }

   /**
    * Set the width or height of axis.
    * @hidden
    */
   @Override
   public void setAxisSize(int axis, double val) {
      DefaultAxis aobj = getAxisAt(axis);

      if(aobj != null) {
         aobj.setAxisSize(val);
      }
   }

   /**
    * Get the width or height of axis.
    * @hidden
    */
   @Override
   public double getAxisSize(int axis) {
      DefaultAxis aobj = getAxisAt(axis);

      if(aobj != null) {
         return aobj.getAxisSize();
      }

      return 0;
   }

   /**
    * Get the axis position (e.g. TOP_AXIS) from the center of the axis in
    * the default layout. For example, the xaxis1 would have (500, 0) as the
    * default position, and yaxis2 would have (1000, 500).
    */
   private int getAxisPosition(int x, int y) {
      Point2D pt = new Point2D.Double(x, y);
      pt = getCoordTransform().transform(pt, null);

      if(pt.getX() == 0) {
         return LEFT_AXIS;
      }
      else if(pt.getY() == 0) {
         return BOTTOM_AXIS;
      }
      else if(pt.getX() == 1000) {
         return RIGHT_AXIS;
      }
      else {
         return TOP_AXIS;
      }
   }

   /**
    * Get the axis at the specified position after coord transformation.
    * @hidden
    * @param position axis position, e.g. TOP_AXIS.
    */
   public DefaultAxis getAxisAt(int position) {
      DefaultAxis[] axes = {xaxis2, yaxis1, xaxis1, yaxis2};
      int idx = getAxisIndex(position);

      return (idx < 0) ? null : axes[idx];
   }

   /**
    * Get the scale at the specified position after coord transformation.
    * @hidden
    * @param position axis position, e.g. TOP_AXIS.
    */
   public Scale getScaleAt(int position) {
      Scale[] scales = {xscale, yscale, xscale,
                        (yscale2 != null) ? yscale2 : yscale};
      int idx = getAxisIndex(position);

      return (idx < 0) ? null : scales[idx];
   }

   /**
    * Get the axis (position) index. The index is from top, left, bottom, right.
    */
   private int getAxisIndex(int position) {
      // optimization, axis index is cached, assuming the rotation has been applied before
      // this method is first used.
      if(axesAt[position] != null) {
         return axesAt[position];
      }

      Point[] pts = {new Point(500, 1000), new Point(0, 500),
                     new Point(500, 0), new Point(1000, 500)};

      for(int i = 0; i < pts.length; i++) {
         if(getAxisPosition(pts[i].x, pts[i].y) == position) {
            axesAt[position] = i;
            return i;
         }
      }

      return -1;
   }

   /**
    * Get left y axis.
    */
   @TernMethod
   public DefaultAxis getYAxis1() {
      return yaxis1;
   }

   /**
    * Get right y axis.
    */
   @TernMethod
   public DefaultAxis getYAxis2() {
      return yaxis2;
   }

   /**
    * Get top x axis.
    */
   @TernMethod
   public DefaultAxis getXAxis2() {
      return xaxis2;
   }

   /**
    * Get bottom y axis.
    */
   @TernMethod
   public DefaultAxis getXAxis1() {
      return xaxis1;
   }

   /**
    * Get all axes in this coordinate.
    */
   @Override
   @TernMethod
   public Axis[] getAxes(boolean recursive) {
      List<Axis> list = new ArrayList<>();
      Axis[] arr = {xaxis1, xaxis2, yaxis1, yaxis2};

      for(int i = 0; i < arr.length; i++) {
         if(arr[i] != null) {
            list.add(arr[i]);
         }
      }

      return list.toArray(new Axis[0]);
   }

   /**
    * Get unit minimum width.
    * @hidden
    */
   @Override
   public double getUnitMinWidth() {
      return getUnitWidth(true);
   }

   /**
    * Get unit preferred width.
    * @hidden
    */
   @Override
   public double getUnitPreferredWidth() {
      return getUnitWidth(false);
   }

   /**
    * Get unit min/preferred width.
    */
   private double getUnitWidth(boolean min) {
      Scale scale = getScaleAt(BOTTOM_AXIS);
      DefaultAxis axis = getAxisAt(BOTTOM_AXIS);
      double lwidth = 0;
      double vowidth = min ? getVOMinWidth() : getVOPreferredWidth();

      if(axis != null && axis.isLabelVisible()) {
         if(scale instanceof CategoricalScale || scale instanceof TimeScale) {
            double ucnt = scale.getUnitCount();
            int tcnt = scale.getTicks().length;
            lwidth = min ? axis.getLabelMinWidth() : axis.getLabelPreferredWidth();

            // for time scale, it's possible to have unit count larger than the
            // number of ticks. Since the preferred width for label only matters
            // for ticks, we adjust here to be more accurate.
            if(ucnt > tcnt && tcnt + 1 < ucnt) {
               lwidth *= (tcnt + 1) / ucnt;
            }
         }
         else if(xscale instanceof LinearScale) {
            lwidth = min ? axis.getMinWidth() : axis.getPreferredWidth();
         }
      }

      lwidth = Math.max(vowidth, lwidth);

      double h;

      // gantt chart where time scale is used as continuous value. give small space per day.
      if(yscale instanceof TimeScale && scale == yscale) {
         h = 12;
      }
      else {
         h = min ? VO_PREFERRED_HEIGHT : VO_MIN_HEIGHT;
         h = Math.max(h, vowidth);
      }

      Rectangle2D rect = new Rectangle2D.Double(0, 0, lwidth, h);
      rect = getCoordTransform().createTransformedShape(rect).getBounds2D();

      return rect.getWidth();
   }

   /**
    * Get unit minimum height.
    * @hidden
    */
   @Override
   public double getUnitMinHeight() {
      return getUnitHeight(true);
   }

   /**
    * Get unit preferred height.
    * @hidden
    */
   @Override
   public double getUnitPreferredHeight() {
      return getUnitHeight(false);
   }

   /**
    * Get unit min/preferred height.
    */
   private double getUnitHeight(boolean min) {
      Scale scale = getScaleAt(LEFT_AXIS);
      DefaultAxis axis = getAxisAt(LEFT_AXIS);
      double lheight = 0;
      double vowidth = min ? getVOMinWidth() : getVOPreferredWidth();

      // lheight accounts for axis labels
      if(axis != null && axis.isLabelVisible()) {
         if(xscale instanceof LinearScale) {
            lheight = min ? axis.getMinHeight() : axis.getPreferredHeight();
         }
         else {
            int ucnt = scale.getUnitCount();

            lheight = min ? axis.getLabelMinHeight() : axis.getLabelPreferredHeight();
            lheight *= ucnt;
         }
      }

      lheight = Math.max(vowidth, lheight); // max label and vo height
      double h = min ? VO_MIN_HEIGHT : VO_PREFERRED_HEIGHT;

      Rectangle2D rect = new Rectangle2D.Double(0, 0, lheight, h);
      rect = getCoordTransform().createTransformedShape(rect).getBounds2D();

      double ph = rect.getHeight();

      return ph / GTool.getUnitCount(this, LEFT_AXIS, false);
   }

   /**
    * Get max vo min width.
    */
   private double getVOMinWidth() {
      return getMaxVOWidth(true);
   }

   /**
    * Get max vo preferred width.
    */
   private double getVOPreferredWidth() {
      return getMaxVOWidth(false);
   }

   /**
    * Get max visual object width.
    */
   private double getMaxVOWidth(boolean min) {
      // optimization
      if(min && this.minVOWidth != null) {
         return this.minVOWidth;
      }
      else if(!min && this.maxVOWidth != null) {
         return this.maxVOWidth;
      }

      VGraph vgraph = getVGraph();
      double maxVOWidth = 0;

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         Visualizable visual = vgraph.getVisual(i);

         if(!(visual instanceof ElementVO)) {
            continue;
         }

         ElementVO vo = (ElementVO) visual;
         double voWidth = min ? vo.getMinWidth() : vo.getPreferredWidth();

         if(maxVOWidth < voWidth) {
            maxVOWidth = voWidth;
         }
      }

      if(min) {
         this.minVOWidth = maxVOWidth;
      }
      else {
         this.maxVOWidth = maxVOWidth;
      }

      return maxVOWidth;
   }

   /**
    * Get all axes at specified position, e.g. TOP_AXIS.
    * @hidden
    */
   @Override
   public DefaultAxis[] getAxesAt(int axis) {
      DefaultAxis da = getAxisAt(axis);

      if(da == null || !isAxisLabelVisible(axis)) {
         return new DefaultAxis[0];
      }

      return new DefaultAxis[] {da};
   }

   /**
    * Get 3D depth.
    */
   @TernMethod
   public double getDepth() {
      return 0;
   }

   /**
    * Limit the values to the count.
    */
   private void setMaxCount(Scale scale, int count) {
      if(scale instanceof CategoricalScale) {
         CategoricalScale scale2 = (CategoricalScale) scale;
         Object[] vals = scale2.getValues();

         if(vals.length > count) {
            Object[] vals2 = new Object[count];
            System.arraycopy(vals, 0, vals2, 0, count);
            scale2.init(vals2);
         }
      }
   }

   /**
    * Get the zero position on the axis.
    */
   private double getZeroPosition(Scale scale, double y, double h) {
      return getPosition(scale, 0, h) + y;
   }

   /**
    * Set facet coord.
    */
   void setFacet(FacetCoord facet) {
      this.facet = facet;
   }

   /**
    * Align the zero point on primary and secondary y axes.
    */
   void alignZero() {
      if(yscale2 instanceof LinearScale && yscale instanceof LinearScale) {
         LinearScale yscaleClone = ((LinearScale) yscale).clone();
         LinearScale yscale2Clone = ((LinearScale) yscale2).clone();

         double ymin = yscaleClone.getMin();
         double ymax = yscaleClone.getMax();
         double y2min = yscale2Clone.getMin();
         double y2max = yscale2Clone.getMax();
         LinearScale s1 = (LinearScale) yscale;
         LinearScale s2 = (LinearScale) yscale2;
         boolean reversed = s1.isReversed();
         boolean reversed2 = s2.isReversed();
         // if scale contains zero
         boolean zero = Math.min(ymin, ymax) <= 0 && Math.max(ymin, ymax) > 0;
         boolean zero2 = Math.min(y2min, y2max) <= 0 && Math.max(y2min, y2max) > 0;
         // if min is explicitly set
         boolean userm = s1.getUserMin() != null || s1.getUserMax() != null;
         boolean userm2 = s2.getUserMin() != null || s2.getUserMax() != null;

         // line-up zero points on two axes
         // we only line up the zero point if both scales contains zero, or
         // if the scale is not explicitly set by user. Otherwise user
         // min/max would be lost.
         if(zero && (zero2 || !userm2) || zero2 && (zero || !userm) ||
            reversed != reversed2 && ymin != ymax && y2min != y2max)
         {
            // zero ratio is the proportion of value below zero to the entire range
            // reverse min/max in calculation if scale is reversed. (62396)
            double zero_ratio = reversed ? -ymax / (ymin - ymax) : -ymin / (ymax - ymin);
            double zero_ratio2 = reversed2 ? -y2max / (y2min - y2max) : -y2min / (y2max - y2min);

            // when we call setMin/setMax, we need to call setMax/setMin
            // to prevent the max being changed from ticks so the correct
            // ratio is maintained

            if(zero_ratio > zero_ratio2) {
               if(Math.abs(zero_ratio - 1) < 0.05) {
                  setMinMax(s2, s2.unmap(-y2max), s2.unmap(y2max));
                  setMinMax(s1, s1.unmap(ymin), s1.unmap(-ymin));
               }
               else {
                  // abs() is necessary when y2 is reversed scale
                  setMinMax(s2, s2.unmap(-y2max * Math.abs(zero_ratio / (1 - zero_ratio))),
                            s2.unmap(y2max));

                  // @by: ChrisSpagnoli bug1418333011808 2014-12-16
                  // Expand min/max so yaxis2 holds to zero after getTicks()
                  // If the user has defined an increment for this axis
                  if(((LinearScale) yscale2).getIncrement() != null) {
                     // flag to determine the least zero drift
                     double ratioBest = 100;
                     // get increament set by user for later reuse
                     double incr = ((LinearScale) yscale2).getIncrement().doubleValue();
                     // starting value of the min
                     double curMin = -y2max * zero_ratio / (1 - zero_ratio);
                     // starting value of the max
                     double newMax = y2max;

                     // loop, because the first expansion isn't always best
                     for(int loopCntr = 0; loopCntr < 10; loopCntr++) {
                        // increase min to multiple of incr, to align on zero
                        double newMin = expandToIncrement(curMin, incr);
                        // scale max up to match
                        double curMax = newMax * newMin / curMin;
                        // then expand max to a multiple of increment
                        newMax = expandToIncrement(curMax, incr);
                        // calculate ratio of expansion of max (will drift zero)
                        double ratioMax = newMax / curMax;
                        // fudge ratioMax up slightly for the loop counter
                        ratioMax += .01 * loopCntr;

                        // use smallest expansion of the max (least zero drift)
                        if(ratioMax < ratioBest) {
                           ratioBest = ratioMax;
                           setMinMax(s2, s2.unmap(newMin), s2.unmap(newMax));
                        }

                        // set up for next loop
                        curMin = newMin * ratioMax;
                     }
                  }
               }
            }
            else if(zero_ratio2 > zero_ratio) {
               // one with all positive and one with all negative,
               // make each scale has the same range on negative and positive.
               if(Math.abs(zero_ratio2 - 1) < 0.05 ||
                  // if primary has mostly positive and secondary has mostly negative, evenly
                  // split the vertical. this looks better since primary is more important
                  // have not squeezing it into smaller space is better.
                  zero_ratio < 0.5 && zero_ratio2 > 0.5)
               {
                  setMinMax(s1, s1.unmap(-ymax), s1.unmap(ymax));
                  setMinMax(s2, s2.unmap(y2min), s2.unmap(-y2min));
               }
               // equation: zero_ratio2 = -min / (max - min)
               else {
                  // abs() is necessary when y2 is reversed scale
                  setMinMax(s1, s1.unmap(-ymax * Math.abs(zero_ratio2 / (1 - zero_ratio2))),
                            s1.unmap(ymax));
               }
            }
         }
      }
   }

   private static void setMinMax(LinearScale scale, double min, double max) {
      scale.setMin(GTool.isInteger(min) ? Math.round(min) : min);
      scale.setMax(GTool.isInteger(max) ? Math.round(max) : max);
      scale.setAlignZero(true);
   }

   // @by: ChrisSpagnoli bug1418333011808 2014-12-16
   private double expandToIncrement(double n, double i) {
      if(n % i == 0) {
         return n;
      }

      double q = n / i;

      if(q < 0) {
         q = Math.ceil(q) - 1;
      }
      else {
         q = Math.floor(q) + 1;
      }

      return q * i;
   }

   @Override
   public void layoutCompleted() {
      super.layoutCompleted();
      scaledVGraph = null;

      // scale in coord can release some memory after layout
      if(xscale != null) {
         xscale.releaseValues(xaxis1 != null && xaxis1.isLabelVisible() ||
                              xaxis2 != null && xaxis2.isLabelVisible());
      }

      if(yscale != null) {
         yscale.releaseValues(yaxis1 != null && yaxis1.isLabelVisible() ||
                              yaxis2 != null && yaxis2.isLabelVisible());
      }
   }

   // push the coordinate cardinality
   private static void pushCardinality(int xcount, int ycount) {
      xCardinality.get().push(xcount);
      yCardinality.get().push(ycount);
   }

   // pop the coordinate cardinality
   private static void popCardinality() {
      xCardinality.get().pop();
      yCardinality.get().pop();
   }

   // get total number of x items include the nested levels
   private static int getAllXCount() {
      return xCardinality.get().stream().mapToInt(Integer::intValue)
         .reduce(1, (x1, x2) -> x1 * x2);
   }

   // get total number of y items include the nested levels
   private static int getAllYCount() {
      return yCardinality.get().stream().mapToInt(Integer::intValue)
         .reduce(1, (y1, y2) -> y1 * y2);
   }

   /**
    * Wrap secondary scale and map to the primary scale space.
    */
   private class SecondaryScale extends LinearScale {
      @Override

      public double mapValue(Object val) {
         double value = yscale2.mapValue(val);
         return map2Primary(value, false);
      }

      @Override
      public double unmap(double value) {
         return map2Primary(value, true);
      }

      /**
       * Map the value on the secondary axis to the primary.
       * @param reverse true to map from primary to secondary
       */
      private double map2Primary(double value, boolean reverse) {
         // map to yscale from yscale2 range if this is secondary
         if(yscale != null && !Double.isNaN(value)) {
            double ymin = yscale.getMin();
            double ymax = yscale.getMax();
            double y2min = yscale2.getMin();
            double y2max = yscale2.getMax();
            double yrange = ymax - ymin;
            double y2range = y2max - y2min;

            if(reverse) {
               value = yrange != 0 ? (value - ymin) * y2range / yrange + y2min : 0;
            }
            else {
               value = y2range != 0 ? (value - y2min) * yrange / y2range + ymin : 0;
            }
         }

         return value;
      }

      @Override
      public void init(DataSet data) {
         yscale2.init(data);
         alignZero();
      }

      @Override
      public double getMin() {
         return map2Primary(yscale2.getMin(), false);
      }

      @Override
      public double getMax() {
         return map2Primary(yscale2.getMax(), false);
      }

      @Override
      public double[] getTicks() {
         return yscale2.getTicks();
      }

      @Override
      public Object[] getValues() {
         return yscale2.getValues();
      }

      @Override
      public String[] getFields() {
         return yscale2.getFields();
      }

      @Override
      public String[] getDataFields() {
         return yscale2.getDataFields();
      }

      @Override
      public void setAxisSpec(AxisSpec axisSpec) {
         yscale2.setAxisSpec(axisSpec);
      }

      @Override
      public AxisSpec getAxisSpec() {
         return yscale2.getAxisSpec();
      }

      @Override
      public double add(double v1, double v2) {
         double top = v1 + v2;

         // need to call yscale2.add(), so we first map the value from the
         // primary scale to secondary scale, add(), and then map it back
         v1 = map2Primary(v1, true);
         // shouldn't use v2 directly, or the calculation is wrong when
         // different min is set on the primary and 2nd scales
         v2 = map2Primary(top, true) - v1;

         double v = yscale2.add(v1, v2);
         return map2Primary(v, false);
      }

      @Override
      public Number getUserMin() {
         if(yscale2 instanceof LinearScale) {
            return ((LinearScale) yscale2).getUserMin();
         }

         return null;
      }

      @Override
      public Number getUserMax() {
         if(yscale2 instanceof LinearScale) {
            return ((LinearScale) yscale2).getUserMax();
         }

         return null;
      }

      @Override
      public boolean equals(Object obj) {
         if(this == obj) {
            return true;
         }

         if(!(obj instanceof SecondaryScale)) {
            return false;
         }

         return super.equalsStrict((LinearScale) obj);
      }
   }

   @Override
   public String toString() {
      return super.toString() + "[" + xscale + "," + yscale + "]";
   }

   private void readObject(ObjectInputStream in) throws ClassNotFoundException, IOException {
      in.defaultReadObject();
      axesAt = new Integer[5];
   }

   private static final long serialVersionUID = 1L;

   private Scale xscale;
   private Scale yscale;
   private Scale yscale2;
   private DefaultAxis xaxis1; // bottom x axis
   private DefaultAxis xaxis2; // top x axis
   private DefaultAxis yaxis1; // left y axis
   private DefaultAxis yaxis2; // right y axis
   private FacetCoord facet;
   private transient VGraph scaledVGraph;
   // index position (e.g. TOP_AXIS) to axis index [x2, y1, x1, y2]
   private transient Integer[] axesAt = new Integer[5];
   // cached values
   private transient Double minVOWidth, maxVOWidth;
   private transient double[] unscaledSizes2; // cached unscaled sizes

   private static final double VO_MIN_HEIGHT = 30;
   private static final double VO_PREFERRED_HEIGHT = 80;
   private static final int AXIS_GRID_MIN_GAP = 3;
   private static final Logger LOG = LoggerFactory.getLogger(RectCoord.class);
   private static ThreadLocal<Stack<Integer>> xCardinality = ThreadLocal.withInitial(Stack::new);
   private static ThreadLocal<Stack<Integer>> yCardinality = ThreadLocal.withInitial(Stack::new);
}
