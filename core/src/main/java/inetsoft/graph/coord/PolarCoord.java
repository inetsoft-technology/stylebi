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
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DataSetIndex;
import inetsoft.graph.guide.axis.*;
import inetsoft.graph.internal.*;
import inetsoft.graph.internal.text.PolarLayoutManager;
import inetsoft.graph.internal.text.TextLayoutManager;
import inetsoft.graph.scale.*;
import inetsoft.graph.visual.VOText;
import inetsoft.sree.SreeEnv;

import java.awt.*;
import java.awt.geom.*;
import java.util.List;
import java.util.*;

/**
 * A polar coordinate transforms the visual objects and their shapes in a
 * circular arrangement around the center of the coordinate.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=PolarCoord")
public class PolarCoord extends Coordinate {
   /**
    * This causes the Y position being mapped to the theta (angle).
    */
   @TernField
   public static final int THETA = 1;
   /**
    * This causes the Y position being mapped to the radius.
    */
   @TernField
   public static final int RHO = 2;
   /**
    * This causes the mapped shapes to start from the outer circumference of
    * a circle.
    */
   @TernField
   public static final int PLUS = 4;
   /**
    * This maps X to theta and Y to r.
    */
   @TernField
   public static final int THETA_RHO = THETA | RHO;

   /**
    * Default constructor.
    */
   public PolarCoord() {
   }

   /**
    * Create a polar (circular) coordinate.
    */
   public PolarCoord(Scale yscale) {
      this(null, yscale);
      setType(THETA);
   }

   /**
    * Create a polar (circular) coordinate.
    */
   @TernConstructor
   public PolarCoord(Scale xscale, Scale yscale) {
      this(new RectCoord(xscale, yscale));

      if(xscale != null) {
	 xscale.getAxisSpec().setAxisStyle(AxisSpec.AXIS_NONE);
      }

      if(yscale != null) {
	 yscale.getAxisSpec().setAxisStyle(AxisSpec.AXIS_NONE);
      }
   }

   /**
    * Create a polar coord that applies polar transformation to the embedded
    * coord.
    */
   public PolarCoord(Coordinate coord) {
      setType(THETA_RHO);
      setCoordinate(coord);
   }

   /**
    * Set the coordinate to be transformed by this polar coord.
    */
   @TernMethod
   public void setCoordinate(Coordinate coord) {
      this.coord = coord;

      if(coord instanceof RectCoord) {
         Scale xscale = ((RectCoord) coord).getXScale();
         Scale yscale = ((RectCoord) coord).getYScale();

         // ignore ticks for full circle
         if(xscale != null) {
            xscale.setScaleOption(Scale.ZERO);
         }

         if(yscale != null) {
            yscale.setScaleOption(Scale.ZERO);
         }

         if(xscale instanceof CategoricalScale) {
            ((CategoricalScale) xscale).setFill(true);
	    // for pie, we should leave the space on the right
	    // so slices don't overlap each other
            ((CategoricalScale) xscale).setGrid(true);
         }
      }
   }

   /**
    * Get the coordinate to be transformed by this polar coord.
    */
   @TernMethod
   public Coordinate getCoordinate() {
      return coord;
   }

   /**
    * Set the polar transformation type.
    * @param type one of the constants defined in PolarCoord: THETA, THETA_RHO,
    * RHO, PLUS, RHO_PLUS.
    */
   @TernMethod
   public void setType(int type) {
      this.type = type;
   }

   /**
    * Get the polar transformation type.
    */
   @TernMethod
   public int getType() {
      return type;
   }

   /**
    * Rotate the plot counter-clock wise.
    * @param degree angle in degrees.
    */
   @Override
   @TernMethod
   public void rotate(double degree) {
      // rotate in polar should not be concerned with moving the parts outside
      // of the coord. This is taken care of in fit.
      getCoordTransform().rotate(Math.PI * degree / 180);
   }

   /**
    * Transform the coordinate so the plot is flipped vertically.
    * @param vertical true to reflect on vertical axis.
    */
   @Override
   @TernMethod
   public void reflect(boolean vertical) {
      AffineTransform trans;

      // see comments in rotate
      if(vertical) {
         trans = new AffineTransform(1, 0, 0, -1, 0, 0);
      }
      else {
         trans = new AffineTransform(-1, 0, 0, 1, 0, 0);
      }

      getCoordTransform().concatenate(trans);
   }

   /**
    * Transform a shape or point to this coordinate space.
    * @param geom a shape or a point to transform.
    * @return the transformed shape or point.
    */
   @Override
   public Object transformShape(Object geom) {
      if(geom == null) {
         return null;
      }
      else if(geom instanceof Point2D) {
         return transformPoint((Point2D) geom);
      }
      else if(geom instanceof Line2D) {
         return transformLine((Line2D) geom);
      }
      // special treatment for performance (pie)
      else if(geom instanceof Rectangle2D) {
         return transformRectangle((Rectangle2D) geom);
      }

      Shape shape = (Shape) geom;
      float[] moveto = {0, 0};
      AffineTransform trans = coord.getCoordTransform();
      GeneralPath path = new GeneralPath();

      for(PathIterator iter = shape.getPathIterator(trans); !iter.isDone();
          iter.next())
      {
         float[] coords = new float[6];
         int segmentType = iter.currentSegment(coords);
         Point2D pt;
         Point2D pt2;
         Point2D pt3;

         switch(segmentType) {
         case PathIterator.SEG_MOVETO:
            pt = transformPoint0(coords[0], coords[1]);
            path.moveTo((float) pt.getX(), (float) pt.getY());
            moveto = coords;
            break;
         case PathIterator.SEG_LINETO:
            pt = transformPoint0(coords[0], coords[1]);
            path.lineTo((float) pt.getX(), (float) pt.getY());
            break;
         case PathIterator.SEG_QUADTO:
            pt = transformPoint0(coords[0], coords[1]);
            pt2 = transformPoint0(coords[2], coords[3]);
            path.quadTo((float) pt.getX(), (float) pt.getY(),
                        (float) pt2.getX(), (float) pt2.getY());
            break;
         case PathIterator.SEG_CUBICTO:
            pt = transformPoint0(coords[0], coords[1]);
            pt2 = transformPoint0(coords[2], coords[3]);
            pt3 = transformPoint0(coords[4], coords[5]);
            path.curveTo((float) pt.getX(), (float) pt.getY(),
                         (float) pt2.getX(), (float) pt2.getY(),
                         (float) pt3.getX(), (float) pt3.getY());
            break;
         case PathIterator.SEG_CLOSE:
            path.moveTo(moveto[0], moveto[1]);
            break;
         }
      }

      return path;
   }

   /**
    * Transform a line to a curve or another line in the polar space.
    */
   private Shape transformLine(Line2D line) {
      Point2D p1 = coord.getCoordTransform().transform(line.getP1(), null);
      Point2D p2 = coord.getCoordTransform().transform(line.getP2(), null);

      // special case, vertical
      if(p1.getX() == p2.getX()) {
         p1 = transformPoint0(p1.getX(), p1.getY());
         p2 = transformPoint0(p2.getX(), p2.getY());

         return new Line2D.Double(p1, p2);
      }
      // horizontal line into arc
      else if(p1.getY() == p2.getY()) {
         double r = getR(p1.getY());
         double start = p1.getX() * 360 / GWIDTH;
         double end = p2.getX() * 360 / GWIDTH;
         double yratio = getRatioY();

         return new Arc2D.Double(-r, -r * yratio, 2 * r, 2 * r * yratio,
                                 -start, -(end - start), Arc2D.OPEN);
      }

      // convert to a curve
      GeneralPath path = new GeneralPath();

      // break into small edges
      double dist = p2.distance(p1);
      int n = (int) Math.max(dist / 5, 1);
      double xdiff = p2.getX() - p1.getX();
      double ydiff = p2.getY() - p1.getY();

      for(int i = 0; i <= n; i++) {
         Point2D pt = transformPoint0(p1.getX() + xdiff * i / n,
                                      p1.getY() + ydiff * i / n);

         if(i == 0) {
            path.moveTo((float) pt.getX(), (float) pt.getY());
         }
         else {
            path.lineTo((float) pt.getX(), (float) pt.getY());
         }
      }

      return path;
   }

   private double getR(double y) {
      double maxr = GHEIGHT / 2;
      return (type != PLUS) ? y : ((y / GHEIGHT) * (1 - holeRatio) + holeRatio) * maxr;
   }

   /**
    * Transform a rectangle to a pie slice.
    */
   private Shape transformRectangle(Rectangle2D rect) {
      // apply nested coord transform
      Shape path = coord.getCoordTransform().createTransformedShape(rect);
      rect = path.getBounds2D();

      double maxr = Math.min(GWIDTH, GHEIGHT) / 2;
      double yratio = getRatioY();

      switch(type) {
      case THETA: {
         double r = maxr;
         double start = rect.getY() / GHEIGHT * 360;
         double extent = rect.getHeight() / GHEIGHT * 360;
         return createShape(r, 0, start, extent);
      }
      case RHO: {
         double r = (rect.getY() + rect.getHeight()) * maxr / GHEIGHT;
         double r2 = rect.getY() * maxr / GHEIGHT;
         return new Donut(-r, -r * yratio, 2 * r, 2 * r * yratio,
                          2 * r2, 2 * r2 * yratio);
      }
      case PLUS: {
         double r = getR(rect.getY() + rect.getHeight());
         double r2 = getR(rect.getY());
         double start = rect.getX() * 360 / GWIDTH;
         double extent = rect.getWidth() * 360 / GWIDTH;
         return createShape(r, r2, start, extent);
      }
      case THETA_RHO: {
         double r = (rect.getY() + rect.getHeight()) * maxr / GHEIGHT;
         double r2 = rect.getY() * maxr / GHEIGHT;
         double start = rect.getX() * 360 / GWIDTH;
         double extent = rect.getWidth() * 360 / GWIDTH;
         return createShape(r, r2, start, extent);
      }
      default:
         throw new RuntimeException("Unsupported transformation: " + type);
      }
   }

   // create donut or simplified donut
   private Shape createShape(double r, double r2, double start, double extent) {
      double yratio = getRatioY();

      // donut has small margin of error that can leave a thin border at the edge in
      // some case, avoid this with Arc if donut is not necessary. it's also faster.
      if(r2 == 0) {
         // svg graphics has a bug that doesn't fill arc2d if it's a circle
         if(extent > 359.9) {
            return new Ellipse2D.Double(-r, -r * yratio, 2 * r, 2 * r * yratio);
         }

         return new Arc2D.Double(-r, -r * yratio, 2 * r, 2 * r * yratio, -start, -extent,
                                 Arc2D.PIE);
      }

      return new Donut(-r, -r * yratio, 2 * r, 2 * r * yratio,
                       2 * r2, 2 * r2 * yratio, -start, -extent);
   }

   /**
    * Transform a point in regular (rect) coordinate to polar coordinate.
    */
   private Point2D transformPoint(Point2D pt) {
      pt = coord.getCoordTransform().transform(pt, null);
      return transformPoint0(pt.getX(), pt.getY());
   }

   /**
    * Transform a point in regular (rect) coordinate to polar coordinate,
    * without applying coord transformation.
    */
   private Point2D transformPoint0(double x, double y) {
      double maxr = Math.min(GWIDTH, GHEIGHT) / 2;
      double yratio = getRatioY();

      switch(type) {
      case THETA: {
         double r = maxr;
         double theta = y * Math.PI * 2 / GWIDTH;

         return new Point2D.Double(r * Math.cos(theta),
                                   r * Math.sin(theta) * yratio);
      }
      case RHO: {
         double r = y * maxr / GHEIGHT;
         double theta = Math.PI * 2;

         return new Point2D.Double(r * Math.cos(theta),
                                   r * Math.sin(theta) * yratio);
      }
      case PLUS: {
         double r = getR(y);
         double theta = x * Math.PI * 2 / GWIDTH;

         return new Point2D.Double(r * Math.cos(theta),
                                   r * Math.sin(theta) * yratio);
      }
      case THETA_RHO: {
         double r = y * maxr / GHEIGHT;
         double theta = x * Math.PI * 2 / GWIDTH;

         return new Point2D.Double(r * Math.cos(theta),
                                   r * Math.sin(theta) * yratio);
      }
      default:
         throw new RuntimeException("Unsupported transformation: " + type);
      }
   }

   /**
    * Create axis and other guides for the graph.
    */
   @Override
   public void createAxis(VGraph vgraph) {
      int acount = vgraph.getAxisCount();
      AffineTransform ctrans = coord.getCoordTransform();
      List<Axis> vaxes = new ArrayList<>();

      // this should be called since the fit won't be called on the base
      // coord. It adds the grid lines and to be transformed in polar.
      coord.createAxis(vgraph);

      // transform axis into the current space
      for(int i = acount; i < vgraph.getAxisCount(); i++) {
         Axis axis = vgraph.getAxis(i);
         AffineTransform trans = axis.getScreenTransform();
         Point2D pos = new Point2D.Double(0, 0);
         Point2D pos2 = new Point2D.Double(pos.getX() + GWIDTH, pos.getY());
         double maxr = Math.min(GWIDTH, GHEIGHT) / 2;

         pos = trans.transform(pos, null);
         pos2 = trans.transform(pos2, null);
         pos = ctrans.transform(pos, null);
         pos2 = ctrans.transform(pos2, null);

         // horizontal axis is changed to PolarAxis
         if(pos.getY() == pos2.getY()) {
            double r = pos.getY() * maxr / GHEIGHT;

            if(r == 0) {
               vgraph.removeAxis(i--);
               continue;
            }

            PolarAxis axis2 = new PolarAxis(axis.getScale(), getVGraph());

            axis2.setTickDown(axis.isTickDown());
            axis2.setZIndex(axis.getZIndex());
            axis2.setWidth(r);
            axis2.setHeight(r);

            vgraph.setAxis(i, axis2);
            axises.add(axis2);

            // copy grid lines
            while(axis.getGridLineCount() > 0) {
               GridLine line = axis.getGridLine(0);

               axis.removeGridLine(0);
               axis2.addGridLine(line);
            }

            axis2.transformGridLines(this);
            // apply coord transform so axis rotation is correct when
            // calculating size. The screen transform will be reset in fit.
            axis2.getScreenTransform().preConcatenate(getCoordTransform());
         }
         // vertical axis is moved to the center and rotated
         else if(pos.getX() == pos2.getX()) {
            double theta = pos.getX() * Math.PI * 2 / GWIDTH;

            trans = new GTransform();
            trans.rotate(theta);

            vaxes.add(axis);
            axises.add(axis);
            axis.setScreenTransform(trans);
            axis.transformGridLines(this);
         }
         // diagonal axis not supported yet, need to be transformed into
         // a curve
         else {
            throw new RuntimeException("Unsupported axis transformation: " +
                                       axis);
         }
      }

      // multiple axes pointing to the center (e.g. radar) would have the min
      // tick interwined. Don't think it should ever be necessary.
      if(vaxes.size() > 1 && type != PLUS) {
         for(Axis axis : vaxes) {
            if(axis.getScale() instanceof LinearScale) {
               axis.setMinTickVisible(false);
            }
         }
      }
   }

   /**
    * Set the screen transformation to fit the graph to the coord bounds.
    */
   @Override
   public void fit(double x, double y, double w, double h) {
      // translates around the transformation so it's not done at an offset.
      // for polar, the rotation should be the center of the area
      double cx = x + w / 2;
      double cy = y + h / 2;
      double md = Math.min(w, h);
      AffineTransform c2 = getScaledCoordTransform(cx, cy, md, md);

      // scale visual point locations to fit the remaining area
      double d = getRadius() * 2;
      double plotd = d;
      VGraph vgraph = getVGraph();

      // reduce pie to fit
      AffineTransform trans = new AffineTransform();

      trans.translate(w / 2 + x, h / 2 + y);
      trans.scale(d / GWIDTH, d / GHEIGHT);
      vgraph.concat(trans, true);

      // check if the element is outside of drawing area, and re-scale if
      // necessary. This must be called after the vgraph.transform() is called
      // so the size report is already transformed.
      // left/right/bottom/top are the amount outside of the drawing area.
      Rectangle2D box = getElementBounds(vgraph, false);

      if(box != null && !box.isEmpty()) {
         double left = getElementMargin(x - box.getX());
         double bottom = getElementMargin(y - box.getY());
         double right = getElementMargin(-(x + w - box.getX() - box.getWidth()));
         double top = getElementMargin(-(y + h - box.getY() - box.getHeight()));

         if(left + right + top + bottom > 0) {
            double maxout = Math.max(left, right);
            double r = plotd / 2;

            maxout = Math.max(maxout, top);
            maxout = Math.max(maxout, bottom);
            plotd = (r / (maxout + r)) * plotd;
            plotd = Math.max(plotd, MIN_RADIUS);
         }
      }

      Shape outerBounds = new Rectangle2D.Double(x, y, w, h);

      // layout polar axis
      for(int i = 0; i < axises.size(); i++) {
         Axis axis = axises.get(i);

         // dont support grid line in 3d pie
         if(coord instanceof Rect25Coord) {
            axis.removeAllGridLines();
         }

         if(axis instanceof PolarAxis) {
            PolarAxis yaxis = (PolarAxis) axis;
            double axisd = plotd;

            yaxis.setWidth(axisd);
            yaxis.setHeight(axisd);
            yaxis.setPlotSize(new DimensionD(w, h));

            AffineTransform ytrans = new AffineTransform();

            ytrans.translate((w - axisd) / 2 + x, (h - axisd) / 2 + y);
            ytrans.preConcatenate(c2);
            yaxis.setScreenTransform(ytrans);

            AffineTransform gridtrans = new AffineTransform();

            gridtrans.translate(w / 2 + x, h / 2 + y);
            gridtrans.scale(plotd / GWIDTH, plotd / GHEIGHT);
            gridtrans.preConcatenate(c2);
            yaxis.setGridLineTransform(gridtrans);

            removeMinGridLine(yaxis);
            removeMaxGridLine(yaxis);
            yaxis.layout(new Rectangle2D.Double(x, y, w, h));

            outerBounds = new Ellipse2D.Double(x + (w - axisd) / 2,
                                               y + (h - axisd) / 2,
                                               axisd, axisd);
         }
      }

      // layout default axis. This must be done after the plotd has been
      // calculated
      for(int i = 0; i < axises.size(); i++) {
         Axis axis = axises.get(i);

         if(!(axis instanceof DefaultAxis)) {
            continue;
         }

         DefaultAxis yaxis = (DefaultAxis) axis;
         AffineTransform gridtrans = new AffineTransform();

         gridtrans.translate(w / 2 + x, h / 2 + y);
         gridtrans.scale(plotd / GWIDTH, plotd / GHEIGHT);
         gridtrans.preConcatenate(c2);
         yaxis.setGridLineTransform(gridtrans);

         if(axis.getZIndex() < 0) {
            continue;
         }

         GTransform atrans = (GTransform) yaxis.getScreenTransform();
         double axislen = plotd / 2;
         AffineTransform trans0 = new AffineTransform();

         if(type == PLUS) {
            trans0.translate(plotd / 6, 0);
         }

         trans0.translate(w / 2 + x, h / 2 + y);
         trans0.scale(plotd / GWIDTH, plotd / GHEIGHT);
         atrans.preConcatenate(trans0);

         double xfactor = GTool.getScaleFactor(atrans, 0);
         axislen = axislen * xfactor;

         if(type == PLUS) {
            axislen = axislen * 2 / 3;
         }

         atrans.preConcatenate(c2);
         yaxis.setLength(axislen);

         layoutGridLines(plotd, yaxis);

         yaxis.layout(outerBounds);
         // axis inside the plot area could be completely covered by vo,
         // should make it visible
         yaxis.setZIndex(GDefaults.VO_Z_INDEX + 1);

         //--bug #10925, set axisSize for radar chart/ filled radar chart axis.
         if(yaxis.getAxisSize() == 0) {
            double axisSize = yaxis.isHorizontal()
               ? yaxis.getPreferredHeight() : yaxis.getPreferredWidth();
            yaxis.setAxisSize(axisSize);
         }
      }

      // if the d is further reduced, scale the graph to the new d
      if(plotd < d) {
         trans = new AffineTransform();

         trans.scale(plotd / d, plotd / d);
         vgraph.concat(trans, true);
      }

      vgraph.concat(c2, false);
      // layout text with full size
      vgraph.setPlotBounds(getBounds(x, y, w, h, c2));
      layoutText(vgraph, true);
      // set plot size to size considering rotation
      vgraph.setPlotBounds(getBounds0(x, y, w, h, c2));

      if(pieRatio > 0) {
         hideTinyText(vgraph);
      }
   }

   // if fixed pie ratio is set, hide texts that are truncated to only 1 char, which looks
   // bad and is quite meaningless.
   private static void hideTinyText(VGraph vgraph) {
      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         Visualizable vobj = vgraph.getVisual(i);

         if(vobj instanceof VOText) {
            VOText text = (VOText) vobj;
            String[] lines = text.getDisplayLabel();
            String str = text.getText();

            if(lines.length == 1 && lines[0].endsWith("..") &&
               // hide a text if only 1 char shown (e.g. A..) and the orig str is > 4.
               lines[0].length() < 4 && str.length() > 4)
            {
               text.setZIndex(-1);
            }
         }
      }
   }

   // fit grid lines in the polar grid.
   private void layoutGridLines(double plotd, DefaultAxis yaxis) {
      AffineTransform scale = AffineTransform.getScaleInstance(plotd / GWIDTH, plotd / GHEIGHT);

      for(int k = 0; k < yaxis.getGridLineCount(); k++) {
         GridLine gridLine = yaxis.getGridLine(k);
         gridLine.setShape(scale.createTransformedShape(gridLine.getShape()));
      }

      // top grid line is at outer edge of polar coord, should be ignored.
      yaxis.removeGridLine(yaxis.getGridLineCount() - 1);
   }

   /**
    * Get the bounds with transformtion.
    */
   private static Rectangle2D getBounds0(double x, double y, double w, double h,
                                         AffineTransform trans)
   {
      // no rotation, just use full size
      if(trans.getShearX() == 0 && trans.getShearY() == 0) {
	 return getBounds(x, y, w, h, trans);
      }

      // we rotate a circle here so the bounds is correct when the rotation is
      // not a multiple of 90
      double d = Math.min(w, h);
      x = x + w / 2 - d / 2;
      y = y + h / 2 - d / 2;
      Shape shape = new Ellipse2D.Double(x, y, d, d);
      shape = trans.createTransformedShape(shape);
      return (new Area(shape)).getBounds2D();
   }

   /**
    * Initialize this coordinate for the specified chart data set.
    */
   @Override
   public void init(DataSet dset) {
      Scale[] scales = getScales();

      for(int i = 0; i < scales.length; i++) {
         if(scales[i] instanceof LinearScale) {
            ((LinearScale) scales[i]).setMax(null);
            ((LinearScale) scales[i]).setMin(null);
         }
      }

      super.init(dset);
   }

   /**
    * Map a tuple (from logic coordinate space) to the chart coordinate space.
    * @param tuple the tuple in logic space (scaled values).
    */
   @Override
   @TernMethod
   public Point2D getPosition(double[] tuple) {
      return coord.getPosition(tuple);
   }

   /**
    * Get the interval size in this coordinate.
    */
   @Override
   @TernMethod
   public double getIntervalSize(double interval) {
      return coord.getIntervalSize(interval);
   }

   /**
    * Get the maximum width of an item in this coordinate without overlapping.
    */
   @Override
   @TernMethod
   public double getMaxWidth() {
      return coord.getMaxWidth();
   }

   /**
    * Get the maximum height of an item in this coordinate without overlapping.
    */
   @Override
   @TernMethod
   public double getMaxHeight() {
      return coord.getMaxHeight();
   }

   /**
    * Get the number of dimensions in this coordinate.
    */
   @Override
   @TernMethod
   public int getDimCount() {
      return coord.getDimCount();
   }

   /**
    * Get the scales used in the coordinate.
    */
   @Override
   @TernMethod
   public Scale[] getScales() {
      return coord.getScales();
   }

   /**
    * Get all axes in this coordinate.
    */
   @Override
   @TernMethod
   public Axis[] getAxes(boolean recursive) {
      return axises.toArray(new Axis[0]);
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
      coord.createSubGGraph(rset, ridx, dset, graph, ggraph, facet, xmap, ymap,
                            hint);
   }

   /**
    * Make a copy of this object.
    */
   @Override
   public Object clone() {
      // should not share scale for pie. Radar is debatable.
      return clone(false);
   }

   /**
    * Make a copy of this object.
    * @param srange true to share range, false otherwise.
    */
   @Override
   public Object clone(boolean srange) {
      PolarCoord obj = (PolarCoord) super.clone();
      obj.coord = (Coordinate) coord.clone(srange);
      obj.axises = new Vector<>();
      return obj;
   }

   /**
    * Get unit minimum width.
    * @hidden
    */
   @Override
   public double getUnitMinWidth() {
      return getUnitPreferredSize(MIN_RADIUS);
   }

   /**
    * Get unit minimum height.
    * @hidden
    */
   @Override
   public double getUnitMinHeight() {
      return getUnitPreferredSize(MIN_RADIUS);
   }

   /**
    * Get unit preferred width.
    * @hidden
    */
   @Override
   public double getUnitPreferredWidth() {
      return getUnitPreferredSize(PREFERRED_RADIUS);
   }

   /**
    * Get unit preferred height.
    * @hidden
    */
   @Override
   public double getUnitPreferredHeight() {
      return getUnitPreferredSize(PREFERRED_RADIUS);
   }

   /**
    * Get the preferred width/height.
    */
   private double getUnitPreferredSize(double prefr) {
      // optimization
      if(Double.isNaN(rx0)) {
         VGraph graph = getVGraph();
         double r1;
         double r2;
         double d1;

         if(isPolarAxisVisible()) {
            r1 = PolarUtil.getLabelPieRadius(500, 500, this);
            r2 = PolarUtil.getLabelPieRadius(1000, 1000, this);
         }
         else if(coord instanceof Rect25Coord) {
            r1 = PolarUtil.getText3DPieRadius(500, 500, graph);
            d1 = PolarUtil.getDefaultRadius(500, 500);

            // optimization, avoid getText3DPieRadius if no text labels
            if(r1 == d1) {
               r2 = PolarUtil.getDefaultRadius(1000, 1000);
            }
            else {
               r2 = PolarUtil.getText3DPieRadius(1000, 1000, graph);
            }
         }
         else {
            r1 = PolarUtil.getTextPieRadius(500, 500, graph);
            d1 = PolarUtil.getDefaultRadius(500, 500);

            // optimization, avoid getTextPieRadius if no text labels
            if(r1 == d1) {
               r2 = PolarUtil.getDefaultRadius(1000, 1000);
            }
            else {
               r2 = PolarUtil.getTextPieRadius(1000, 1000, graph);
            }
         }

         // r is the min of (w / 2 - text_prefw) / cos(angle)
         // it can be rewritten to: w * x - c = r
         // where w is the width, x is a variable, c is a constant, and r is the
         // returned radius
         // so we can solve the equations with two r's, and calculate the w for
         // the prefr:
         // x = (r2 - r1) / (w2 - w1)
         // c = w2 * x - r2
         // w = (r + c) / x
         rx0 = Math.abs(r1 - r2) < 0.00001 ? 1 : (r2 - r1) / (1000 - 500);
         rc0 = 1000 * rx0 - r2;
      }

      double x = rx0;
      double c = rc0;

      return Math.max((prefr + c) / x, prefr * 2);
   }

   /**
    * Get the Y direction ratio.
    */
   private double getRatioY() {
      return (coord instanceof Rect25Coord) ? 0.5 : 1;
   }

   /**
    * Get polar radius.
    */
   @TernMethod
   public double getRadius() {
      if(pradius == null) {
         double radius;
         double w = getCoordBounds().getWidth();
         double h = getCoordBounds().getHeight();
         VGraph graph = GTool.getTopVGraph(this);

         if(pieRatio > 0) {
            radius = Math.min(w, h) * pieRatio / 2;
         }
         else if(isPolarAxisVisible()) {
            radius = PolarUtil.getLabelPieRadius(w, h, this);
         }
         else {
            if(coord instanceof Rect25Coord) {
               radius = PolarUtil.getText3DPieRadius(w, h, graph);
            }
            else {
               radius = PolarUtil.getTextPieRadius(w, h, graph);
            }

            double minRatio = Double.parseDouble(SreeEnv.getProperty("graph.pie.min.ratio", "0.5"));
            double minRadius = Math.min(w, h) * minRatio / 2;

            if(radius < minRadius) {
               radius = minRadius;
               minApplied = true;
            }
         }

         // leave gap so the edge doesn't overlap border
         radius = Math.min(radius - 1, Math.min(w, h) / 2 - 2);
         radius = Math.max(radius, 1);
         pradius = radius;
      }

      return pradius;
   }

   /**
    * Get the center hole size as ratio of coord width/height for PLUS, between 0 and 1.
    */
   @TernMethod
   public double getHoleRatio() {
      return holeRatio;
   }

   /**
    * Set the center hole size as ratio of coord width/height for PLUS, between 0 and 1.
    * Default value is 0.3.
    */
   @TernMethod(url = "#ProductDocs/chartAPI/html/Common_Chart_PolarCoord_setHoleRatio.htm")
   public void setHoleRatio(double holeRatio) {
      this.holeRatio = holeRatio;
   }

   /**
    * Get the pie size as ratio of coord width/height, between >0 and 1.
    */
   @TernMethod
   public double getPieRatio() {
      return pieRatio;
   }

   /**
    * Set the pie size as ratio of coord width/height, between >0 and 1. Default is 0,
    * which is to use the maximum space after reserving space for labels.
    */
   @TernMethod
   public void setPieRatio(double pieRatio) {
      this.pieRatio = pieRatio;
   }

   /**
    * Check if polar axis is visible.
    */
   private boolean isPolarAxisVisible() {
      for(int i = 0 ; i < axises.size(); i++) {
         if(axises.get(i) instanceof PolarAxis) {
            PolarAxis polar = (PolarAxis) axises.get(i);

            if(polar.getScale().getAxisSpec().isLabelVisible()) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check if pie labels can be moved. This is currently determined by whether a fixed
    * pie ratio is applied. This may change in the future.
    * @hidden
    */
   public boolean isLabelMoveable() {
      return pieRatio > 0 || minApplied;
   }

   @Override
   protected void resolveTextOverlapping() {
      TextLayoutManager layout = new PolarLayoutManager(getVGraph());
      layout.resolve();
   }

   /**
    * Check if the coordinate has the same structure as this.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      PolarCoord coord = (PolarCoord) obj;
      return type == coord.type && holeRatio == coord.holeRatio && pieRatio == coord.pieRatio;
   }

   private static final double MIN_RADIUS = 20;
   private static final double PREFERRED_RADIUS = 48;
   private static final long serialVersionUID = 1L;

   private int type = THETA_RHO; // polar transformation type
   private Coordinate coord; // inner coordinate
   private Vector<Axis> axises = new Vector<>(); // all axises in this coord
   private Double pradius = null; // polar radius
   private double holeRatio = 0.3;
   private double pieRatio = 0;
   private boolean minApplied;
   private transient double rx0 = Double.NaN; // cached radio ratio x
   private transient double rc0 = Double.NaN; // cached radio ratio c
}
