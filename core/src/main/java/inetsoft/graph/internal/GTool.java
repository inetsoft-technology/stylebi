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
package inetsoft.graph.internal;

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.coord.*;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.scale.*;
import inetsoft.graph.visual.*;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Catalog;
import inetsoft.util.CoreTool;
import inetsoft.util.graphics.SVGSupport;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.jafama.FastMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.FilteredImageSource;
import java.lang.reflect.Array;
import java.text.*;
import java.util.List;
import java.util.*;

/**
 * Common functions used in the graph packages.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public final class GTool {
   /**
    * Convert an object to a string for displaying on a graph,
    */
   public static String toString(Object val) {
      if(val instanceof double[]) {
         StringBuilder sb = new StringBuilder();
         double[] arr = (double[]) val;

         for(int i = 0; i < arr.length; i++) {
            if(i > 0) {
               sb.append(',');
            }

            sb.append(CoreTool.toString(arr[i]));
         }

         return sb.toString();
      }
      else if(val instanceof String) {
         return (String) val;
      }
      // handle special types (e.g. MemberObject)
      else if(!(val instanceof Number || val instanceof java.util.Date)) {
         String view = XUtil.toView(val);

         if(view != null) {
            return view;
         }
      }

      return CoreTool.toString(val);
   }

   /**
    * Convert an object to a string for displaying on a graph.
    */
   public static String toString(double val) {
      return GTool.toString(Double.valueOf(val));
   }

   /**
    * Returns a brighter version of this color.
    * @param color original color.
    * @return brighter version of the original color.
    */
   public static Color brighten(Color color) {
      return new Color(Math.min((int) (color.getRed() * (1 / 0.9)), 255),
                       Math.min((int) (color.getGreen() * (1 / 0.9)), 255),
                       Math.min((int) (color.getBlue() * (1 / 0.9)), 255),
                       color.getAlpha());
   }

   /**
    * Returns a darker version of this color.
    * @param color original color.
    * @return darker version of the original color.
    */
   public static Color darken(Color color) {
      return darken(color, 0.8);
   }

   /**
    * Returns a darker version of this color.
    * @param color original color.
    * @param perc percentage to change.
    * @return darker version of the original color.
    */
   public static Color darken(Color color, double perc) {
      return new Color(Math.max((int) (color.getRed() * perc), 0),
                       Math.max((int) (color.getGreen() * perc), 0),
                       Math.max((int) (color.getBlue() * perc), 0),
                       color.getAlpha());
   }

   /**
    * Get transformed bounds.
    * @param bounds the bounds to be transformed.
    * @param trans AffineTransform.
    * @return transformed bounds.
    */
   public static Rectangle2D transform(Rectangle2D bounds,
                                       AffineTransform trans) {
      double w0 = bounds.getWidth();
      double h0 = bounds.getHeight();
      Point2D ptSrc = new Point2D.Double(bounds.getX(), bounds.getY());
      Point2D ptDst = new Point2D.Double();
      ptDst = trans.transform(ptSrc, ptDst);

      return new Rectangle2D.Double(ptDst.getX(), ptDst.getY() - h0, w0, h0);
   }

   /**
    * Scale the shape.
    */
   public static Shape scale(Shape shape, double xs, double ys) {
      boolean rect = shape instanceof Rectangle2D;
      AffineTransform trans = new AffineTransform();

      trans.scale(xs, ys);
      shape = trans.createTransformedShape(shape);

      return rect ? shape.getBounds2D() : shape;
   }

   /**
    * Move (translate) the shape.
    */
   public static Shape move(Shape shape, double xoff, double yoff) {
      boolean rect = shape instanceof Rectangle2D;
      AffineTransform trans = new AffineTransform();

      trans.translate(xoff, yoff);
      shape = trans.createTransformedShape(shape);

      return rect ? shape.getBounds2D() : shape;
   }

   /**
    * Get visual objects from visual graph.
    * @return all vos in visual graph.
    */
   public static List<VisualObject> getVOs(VGraph graph) {
      List<VisualObject> vos = new ArrayList<>();

      for(int i = 0; i < graph.getVisualCount(); i++) {
         Visualizable v = graph.getVisual(i);

         if(v instanceof VisualObject) {
            if(v instanceof GraphVO) {
               VGraph vgraph = ((GraphVO) v).getVGraph();
               vos.addAll(getVOs(vgraph));
            }
            else {
               vos.add(((VisualObject) v));
            }
         }
      }

      return vos;
   }

   /**
    * Check if the result of the transformation is horizontal.
    */
   public static boolean isHorizontal(AffineTransform trans) {
      return trans.getShearX() == 0 && trans.getShearY() == 0;
   }

   /**
    * Check if the rotation in transformation is multiple of 90 degrees.
    */
   public static boolean isVertical(AffineTransform trans) {
      Point2D p1 = new Point2D.Double(0, 0);
      Point2D p2 = new Point2D.Double(100, 0);

      p1 = trans.transform(p1, null);
      p2 = trans.transform(p2, null);

      return Math.abs(p1.getX() - p2.getX()) < 0.0001;
   }

   /**
    * Check if this is a pie.
    */
   public static boolean isPolar(Coordinate coord) {
      return isCoordType(coord, PolarCoord.class);
   }

   /**
    * Check if this is a tree/network.
    */
   public static boolean isRelation(Coordinate coord) {
      return isCoordType(coord, RelationCoord.class);
   }

   private static boolean isCoordType(Coordinate coord, Class coordType) {
      if(coord instanceof FacetCoord) {
         for(Coordinate coord2 : ((FacetCoord) coord).getInnerCoordinates()) {
            if(isCoordType(coord2, coordType)) {
               return true;
            }
         }
      }

      return coordType.isAssignableFrom(coord.getClass());
   }

   /**
    * Append arr2 to arr.
    */
   public static Object[] concatArray(Object[] arr, Object[] arr2) {
      Class cls = arr.getClass().getComponentType();
      Object[] narr = (Object[]) Array.newInstance(cls, arr.length+arr2.length);

      System.arraycopy(arr, 0, narr, 0, arr.length);
      System.arraycopy(arr2, 0, narr, arr.length, arr2.length);

      return narr;
   }

   /**
    * Get the minimum size factor amount all element visual objects.
    */
   public static double getMinSizeFactor(VContainer graph) {
      double min = Double.MAX_VALUE; // size factor

      for(int i = 0; i < graph.getVisualCount(); i++) {
         if(!(graph.getVisual(i) instanceof ElementVO)) {
            continue;
         }

         ElementVO vo = (ElementVO) graph.getVisual(i);

         min = Math.min(min, vo.getSizeFactor());
      }

      return min;
   }

   /**
    * Set all element visual objects to the size factor.
    */
   public static void scaleToSizeFactor(VContainer graph, double factor) {
      for(int i = 0; i < graph.getVisualCount(); i++) {
         if(!(graph.getVisual(i) instanceof ElementVO)) {
            continue;
         }

         ElementVO vo = (ElementVO) graph.getVisual(i);
         vo.scaleToSizeFactor(factor);
      }
   }

   /**
    * Get flip y transform.
    * @return flip y transform;
    */
   public static AffineTransform getFlipYTransform(Visualizable vo) {
      AffineTransform transform = new AffineTransform();
      Rectangle2D bounds = vo.getBounds();
      transform.translate(0, bounds.getY() * 2 + bounds.getHeight());
      transform.concatenate(GDefaults.FLIPY);

      return transform;
   }

   /**
    * Get the number of items on the specified axis position.
    * @param linearTicks true if treat ticks as units on linear scales.
    */
   public static int getUnitCount(Coordinate coord, int axis, boolean linearTicks) {
      if(coord instanceof FacetCoord) {
         return (axis == Coordinate.LEFT_AXIS || axis == Coordinate.RIGHT_AXIS)
            ? ((FacetCoord) coord).getYUnitCount()
            : ((FacetCoord) coord).getXUnitCount();
      }

      if(!(coord instanceof RectCoord)) {
         return 1;
      }

      Scale scale = ((RectCoord) coord).getScaleAt(axis);

      // this method is only called to calculate the unit count for distributing
      // space in a facet. For linear, we use the same width regardless of the
      // interval for preferred size calculation
      if(scale instanceof LinearScale) {
         return linearTicks ? scale.getTicks().length - 1 : 1;
      }

      return (scale != null) ? scale.getUnitCount() : 1;
   }

   /**
    * Get the rotation radian in the transformation.
    */
   public static double getRotation(AffineTransform trans) {
      Point2D p1 = new Point2D.Double(0, 0);
      Point2D p2 = new Point2D.Double(100, 0);

      p1 = trans.transform(p1, null);
      p2 = trans.transform(p2, null);

      return getAngle(p1, p2);
   }

   /**
    * Get the angle (radian) from p1 to p2.
    */
   public static double getAngle(Point2D p1, Point2D p2) {
      double v = 0;

      if(p1.getX() == p2.getX()) {
         v = (p2.getY() > p1.getY()) ? Math.PI / 2 : -Math.PI / 2;
      }
      else {
         v = FastMath.atan((p2.getY() - p1.getY()) / (p2.getX() - p1.getX()));

         if(p2.getX() < p1.getX()) {
            v += Math.PI;
         }
      }

      while(v < 0) {
         v += Math.PI * 2;
      }

      return v;
   }

   /**
    * Get the scale factor that would be caused by applying the transformation
    * on the positions at the specified direction.
    * @param direction angle in degrees.
    */
   public static double getScaleFactor(AffineTransform trans, double direction) {
      double angle = (direction * Math.PI) / 180;
      Point2D pt1 = new Point2D.Double(0, 0);
      Point2D pt2 = new Point2D.Double(100 * Math.cos(angle),
                                       100 * Math.sin(angle));
      pt1 = trans.transform(pt1, null);
      pt2 = trans.transform(pt2, null);

      double dist = pt1.distance(pt2);

      return 100 / dist;
   }

   /**
    * Apply an alpha to color.
    */
   public static Color getColor(Color color, double alpha) {
      // if transparency is already defined in color, don't override it
      if(color == null || color.getAlpha() < 255) {
         return color;
      }

      float[] comps = color.getColorComponents(null);
      return new Color(comps[0], comps[1], comps[2], (float) alpha);
   }

   /**
    * Create a stroke object from a line style.
    */
   public static Stroke getStroke(int style) {
      Stroke stroke = null;
      int cap = BasicStroke.CAP_BUTT;
      int join = BasicStroke.JOIN_MITER;

      // the cap style seems to override the dash setting, don't use both
      if((style & GraphConstants.DASH_MASK) == 0) {
         if((style & GraphConstants.LINECAP_ROUND) != 0) {
            cap = BasicStroke.CAP_ROUND;
         }
         else if((style & GraphConstants.LINECAP_SQUARE) != 0) {
            cap = BasicStroke.CAP_SQUARE;
         }
      }

      if((style & GraphConstants.LINEJOIN_ROUND) != 0) {
         join = BasicStroke.JOIN_ROUND;
      }
      else if((style & GraphConstants.LINEJOIN_BEVEL) != 0) {
         join = BasicStroke.JOIN_BEVEL;
      }

      float w = getLineWidth(style);

      if((style & GraphConstants.DASH_MASK) != 0) {
         float dlen = (style & GraphConstants.DASH_MASK) >> 4;
         stroke = new BasicStroke(w, cap, join, 10, new float[] {dlen}, 0);
      }
      else {
         stroke = new BasicStroke(w, cap, join);
      }

      return stroke;
   }

   /**
    * Get the width of a line style.
    */
   public static float getLineWidth(int style) {
      return (style & GraphConstants.WIDTH_MASK) +
         ((style & GraphConstants.FRACTION_WIDTH_MASK) >> 16) / 16f;
   }

   /**
    * Draw an arrow at the p2 point.
    * @param size arrow size.
    */
   public static void drawArrow(Graphics2D g, Point2D p1, Point2D p2, double size) {
      Point2D[] pts = getArrowPoints(p1.getX(), p1.getY(), p2.getX(), p2.getY(), size);

      g = (Graphics2D) g.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.draw(new Line2D.Double(pts[0], p2));
      g.draw(new Line2D.Double(pts[1], p2));
      g.dispose();
   }

   /**
    * Get points of the arrow.
    */
   private static Point2D[] getArrowPoints(double x1, double y1,
                                           double x2, double y2, double size)
   {
      double H = size;
      double L = size;
      double awrad = Math.atan(L / H);
      double arrow_len = Math.sqrt(L * L + H * H);
      double[] XY_1 = rotateVec(x2 - x1, y2 - y1, awrad, true, arrow_len);
      double[] XY_2 = rotateVec(x2 - x1, y2 - y1, -awrad, true, arrow_len);

      return new Point2D[] {
         new Point2D.Double(x2 - XY_1[0], y2 - XY_1[1]),
         new Point2D.Double(x2 - XY_2[0], y2 - XY_2[1])};
   }

   /*
    * Get the points of the arrow
    */
   private static double[] rotateVec(double px, double py, double ang,
                                     boolean isChLen, double newLen)
   {
      double[] mathstr = new double[2];
      double vx = px * Math.cos(ang) - py * Math.sin(ang);
      double vy = px * Math.sin(ang) + py * Math.cos(ang);

      if(isChLen) {
         double d = Math.sqrt(vx * vx + vy * vy);
         vx = vx / d * newLen;
         vy = vy / d * newLen;
         mathstr[0] = vx;
         mathstr[1] = vy;
      }

      return mathstr;
   }

   public static String getFrameType(Class cls) {
      while(!cls.getSuperclass().equals(VisualFrame.class)) {
         cls = cls.getSuperclass();
      }

      String name = cls.getName();

      name = name.substring(name.lastIndexOf('.') + 1);

      if(name.endsWith("Frame")) {
         name = name.substring(0, name.length() - 5);
      }

      return name;
   }

   /**
    * Center the interval on the coordinate.
    */
   public static Shape centerShape(Shape shape, boolean horizontal) {
      // normalize negative width/height
      if(shape instanceof Rectangle2D && ((Rectangle2D) shape).getHeight() < 0) {
         Rectangle2D rectShape = (Rectangle2D) shape;
         shape = new Rectangle2D.Double(rectShape.getX(), rectShape.getY() + rectShape.getHeight(),
                                        rectShape.getWidth(), -rectShape.getHeight());
      }

      if(shape instanceof Rectangle2D && ((Rectangle2D) shape).getHeight() < 0) {
         Rectangle2D rectShape = (Rectangle2D) shape;
         shape = new Rectangle2D.Double(rectShape.getX() + rectShape.getWidth(), rectShape.getY(),
                                        -rectShape.getWidth(), rectShape.getHeight());
      }

      Rectangle2D box = shape.getBounds2D();
      return centerShape(shape, horizontal, box.getWidth(), box.getHeight());
   }

   /**
    * Center the interval on the coordinate.
    */
   public static Shape centerShape(Shape shape, boolean horizontal,
                                   double width, double height)
   {
      boolean rect = shape instanceof Rectangle2D;
      double boxw = horizontal ? width : height;
      double w = Math.min(1000, boxw);
      AffineTransform trans = new AffineTransform();

      if(horizontal) {
         trans.translate(-w / 2, 0);
      }
      else {
         trans.translate(0, -w / 2);
      }

      if(w < boxw) {
         if(horizontal) {
            trans.scale(w / boxw, 1);
         }
         else {
            trans.scale(1, w / boxw);
         }
      }

      shape = trans.createTransformedShape(shape);
      // maintain Rectangle2D (avoid changing to GeneralPath)
      return rect ? shape.getBounds2D() : shape;
   }

   /**
    * Get the max width of the lines.
    */
   public static double stringWidth(String[] lines, Font fn) {
      int len = lines.length;

      if(len == 1) {
         return impl.stringWidth(lines[0], fn);
      }

      double lwidth = 0;

      for(int i = 0; i < len; i++) {
         lwidth = Math.max(lwidth, impl.stringWidth(lines[i], fn));
      }

      return lwidth;
   }

   /**
    * Break the text into lines.
    */
   public static String[] breakLine(String text, Font fn, int spacing,
                                    double width, double height)
   {
      return impl.breakLine(text, fn, spacing, (float) width, (float) height);
   }

   /**
    * Get the topmost coordinate.
    */
   public static Coordinate getTopCoordinate(Coordinate coord) {
      while(coord.getParentCoordinate() != null) {
         coord = coord.getParentCoordinate();
      }

      return coord;
   }

   /**
    * Get the topmost VGraph.
    */
   public static VGraph getTopVGraph(Coordinate coord) {
      return getTopCoordinate(coord).getVGraph();
   }

   /**
    * Handle ratio and negative distance (from top/right) in fixed position.
    */
   public static Point2D transformFixedPosition(Coordinate coord, Point2D pt) {
      return transformFixedPosition(coord, pt, false);
   }

   /**
    * Handle ratio and negative distance (from top/right) in fixed position,
    * if the position is ratio, we will treat it as the distance from plot area.
    */
   public static Point2D transformFixedPosition(Coordinate coord, Point2D pt,
      boolean plot)
   {
      if(coord == null) {
         return pt;
      }

      VGraph vgraph = GTool.getTopVGraph(coord);
      Rectangle2D plotBox = vgraph.getPlotBounds();
      Rectangle2D box = vgraph.getBounds();
      double bx = plot ? plotBox.getX() : box.getX();
      double by = plot ? plotBox.getY() : box.getY();
      double bw = plot ? plotBox.getWidth() : box.getWidth();
      double bh = plot ? plotBox.getHeight() : box.getHeight();
      double x = pt.getX();
      double y = pt.getY();

      // 0 - 1 is a ratio of width
      if(Math.abs(x) < 1 && x != 0) {
         if(x > 0) {
            x = bx + bw * x;
         }
         else {
            x = bx + bw * (1 + x);
         }
      }
      // negative value is value from top/right
      else if(x < 0) {
         x = box.getX() + box.getWidth() + x;
      }

      if(Math.abs(y) < 1 && y != 0) {
         if(y > 0) {
            y = by + bh * y;
         }
         else {
            y = by + bh * (1 + y);
         }
      }
      else if(y < 0) {
         y = box.getY() + box.getHeight() + y;
      }

      return new Point2D.Double(x, y);
   }

   /**
    * Format an object with the specified format.
    */
   public static String format(Format format, Object val) {
      if(format == null || val == null) {
         return val == null ? "" : CoreTool.toString(val);
      }

      if("".equals(val)) {
         return "";
      }

      if(format instanceof NumberFormat && !(val instanceof Number)) {
         try {
            return format.format(Double.valueOf(val.toString()));
         }
         catch(Exception ex) {
            LOG.debug("Failed to format number: " + val, ex);
         }

         return CoreTool.toString(val);
      }

      if(format instanceof DateFormat && !(val instanceof Date) && !(val instanceof Number)) {
         return CoreTool.toString(val);
      }

      if(format instanceof MessageFormat && !(val instanceof Object[])) {
         val = new Object[] {val};
      }

      try {
         return format.format(val);
      }
      catch(Exception ex) {
         if(LOG.isDebugEnabled()) {
            LOG.warn("Failed to format value: " + val, ex);
         }
         else {
            LOG.info("Failed to format value: " + val);
         }
         throw new RuntimeException(Catalog.getCatalog().getString("This format is not available"));
      }
   }

   /**
    * This method should be called to draw a line if the line must be lined
    * up with another line. For example, the border around a coord may be
    * drawn with grid line, axis line, and line form. If one of them uses
    * a different rounding or stroke option, they may be drawn at one pixel
    * offset and cause the border to look thicker. This method handles
    * setting of stroke option and also any rounding of the points.
    */
   public static void drawLine(Graphics2D g, Shape line) {
      if(!(line instanceof Line2D)) {
         g.draw(line);
         return;
      }

      Line2D line0 = (Line2D) line;
      double x1 = line0.getX1();
      double y1 = line0.getY1();
      double x2 = line0.getX2();
      double y2 = line0.getY2();

      if(x1 == x2 && !GTool.isInteger(x1) || y1 == y2 && !GTool.isInteger(y1)) {
         /*
         g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
         */
         // lines at fractional position may disappear (completely or partially). (57760)
         if(isAWT()) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         }
      }
      else {
         // if the value is treated as an integer (e.g. 5.99999999), we round
         // it here instead of relying on graphics to treat 5.9999999 same as 6
         if(x1 == x2) {
            x1 = x2 = Math.round(x1);
         }

         if(y1 == y2) {
            y1 = y2 = Math.round(y1);
         }
      }

      // @by larryl, using different stroke (e.g. PURE and DEFAULT) for lines
      // with same length could result in the lines being drawn in different
      // length. We use the same stroke for all to avoid ticks being draw at
      // different length.
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

      g.draw(new Line2D.Double(x1, y1, x2, y2));
   }

   /**
    * Get all dimension used by the graph elements. The order of the dimensions
    * are the order of the dimensions in the graph elements, and in the overall
    * order of the elements.
    */
   public static String[] getDims(EGraph graph) {
      // use linked hashset since the order is significant.
      Set dims = new ObjectLinkedOpenHashSet<>();

      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);

         for(int j = 0; j < elem.getDimCount(); j++) {
            dims.add(elem.getDim(j));
         }
      }

      return (String[]) dims.toArray(new String[dims.size()]);
   }

   /**
    * Find an increment to produce nice numbers on a scale. The algorithm is
    * based on Wilkinson 6.2.2.1.
    *
    * @param dmin minimum value of the data range.
    * @param dmax maximum value of the data range.
    * @param umin user defined minimum value.
    * @param umax user defined maximum value.
    * @param m the ideal number of ticks.
    */
   public static double[] getNiceNumbers(double dmin, double dmax,
                                         double umin, double umax, int m) {
      return getNiceNumbers(dmin, dmax, umin, umax, m, true);
   }

   /**
    * Find an increment to produce nice numbers on a scale. The algorithm is
    * based on Wilkinson 6.2.2.1:
    *
    * The idea is to try out different parameters to arrive at an optimal
    * nice index. The following values are used in the calculation:
    *
    * Q: {1, 5, 2, 25, 3}
    * n: 5 (cardinality of Q}
    * i: 1..n (an index into Q}
    * k: number of ticks on a scale
    * rd: data range
    * rs: scale range
    * v: 1 if S contains 0, 0 otherwise.
    *
    * The nice number should be R = {q * 10 ** z: q in Q, z in Z} where Z
    * is the set of integers.
    * The above values (plus m) is used to calculate the following scores:
    * simplicity: s = 1 - i/n + v/n
    * granularity: g = (0 < k < 2m) ? (1 - abs(k - m) / m) : 0
    * coverage: c = rd/rs
    *
    * The overall score is calculated as: w = (s + g + c) / 3
    * The goal is the come out with a number that produces the highest w.
    *
    * @param dmin minimum value of the data range.
    * @param dmax maximum value of the data range.
    * @param umin user defined minimum value.
    * @param umax user defined maximum value.
    * @param m the ideal number of ticks.
    * @param zero include zero or not.
    * @return [min, max, increment] that defines the nice number sequence.
    */
   public static double[] getNiceNumbers(double dmin, double dmax, double umin,
                                         double umax, int m, boolean zero) {
      double[] Q = new double[] {1, 5, 2, 25, 3};
      int n = Q.length;
      double rd = dmax - dmin;
      double lastw = 0;
      double min = 0;
      double max = 0;
      double inc = 0;
      int m2 = (m > 2) ? m - 1 : m; // number of ranges

      for(int i = 0; i < n; i++) {
         double q0 = Q[i];
         double q = q0;
         int z = 0;
         int zsign = (q > rd / m2) ? -1 : 1;

         // find out which z to try
         do {
            q = q0 * Math.pow(10, z);

            // try out different ranges
            double[] minarr = Double.isNaN(umin)
               ? ((dmin > 0 && zero)
                  ? new double[] {0, dmin}
                  : new double[] {dmin})
               : new double[] {umin};
            boolean nochange = true;

            for(int minidx = 0; minidx < minarr.length; minidx++) {
               double min1 = minarr[minidx];
               double max1 = Double.isNaN(umax) ? dmax : umax;

               if(min1 >= max1) {
                  continue;
               }

               nochange = false;
               min1 = fixMin(min1, dmin, umin, q);
               max1 = fixMax(max1, dmin, umax, q);
               q = fixIncrement(min1, max1, q);

               double rs = max1 - min1;
               int k = (int) (rs / q);
               int v = (min1 == 0 && zero) ? 1 : 0;

               double s = 1 - (i + 1.0) / n + (v + 0.0) / n;
               double g = (0 < k && k < 2 * m)
                  ? (1 - Math.abs(k - m + 0.0) / m) : 0;
               double c = rd / rs;
               double w = (s + g + c) / 3.0;

               // if zero should be included and the inc will hit zero, increase it's weight.
               if(zero && min1 < 0 && max1 > 0 && isInteger(min1 / q)) {
                  // weight should be sufficiently large. (56681)
                  w += 0.2;
               }

               // less decimal place is more attractive
               int decimals = getDecimalPlace(min1) + getDecimalPlace(max1) +
                  getDecimalPlace(q);
               w -= Math.min(0.15, decimals / 50.0);

               if(w > lastw) {
                  min = min1;
                  max = max1;
                  inc = q;
                  lastw = w;
               }
            }

            // avoid infinite loop
            if(nochange) {
               break;
            }

            z += zsign;
         }
         while(zsign < 0 && q > rd / m || zsign > 0 && q < rd / m);
      }

      // If the increment is still too small, and will result in a high number
      // of ticks, then increase its size.
      if((max - min) / inc > 1000.0) {
         inc = (max - min) / 1000.0;
      }

      min = roundDecimal(min);
      max = roundDecimal(max);
      inc = roundDecimal(inc);

      return new double[] {min, max, inc};
   }

   /**
    * Make sure min is at a round number.
    */
   private static double fixMin(double min, double dmin, double umin,
                                double inc)
   {
      // fix min
      if(Double.isNaN(umin) && Math.abs((long) (min/inc) * inc - min) > 0.0001) {
         min -= inc;
         min = ((long) (min / inc)) * inc;

         if(min + inc < dmin) {
            min += inc;
         }

         // avoid 0.1 becoming 0.09999999999999 after calculation
         min = inc * Math.round(min / inc);
      }

      return min;
   }

   /**
    * Make sure max is at a round number.
    */
   private static double fixMax(double max, double dmin, double umax,
                                double inc)
   {
      // fix max
      if(Double.isNaN(umax) && Math.abs((long) (max / inc) * inc - max) > 0.01) {
         max += inc;
         max = ((long) Math.ceil(max / inc)) * inc;

         if(max - inc > dmin) {
            max -= inc;
         }

         // avoid 0.1 becoming 0.09999999999999 after calculation
         max = inc * Math.round(max / inc);
      }

      return max;
   }

   /**
    * Round 1.9999 to 2 and 2.00001 to 2.
    */
   private static double roundDecimal(double v) {
      return Double.parseDouble(toString(v));
   }

   /**
    * Get the number of decimal place.
    */
   public static int getDecimalPlace(double val) {
      double fraction = val - (long) val;

      if(fraction == 0) {
         return 0;
      }

      String str = Long.toString((long) (fraction * 1000000000));

      for(int i = str.length() - 1; i >= 0; i--) {
         if(str.charAt(i) != '0') {
            return i + (10 - str.length());
         }
      }

      return 0;
   }

   /**
    * Make sure max is multiple of increment.
    */
   private static double fixIncrement(double min, double max, double inc) {
      double n = (max - min) / inc;
      int m = 6;

      if(n == Math.ceil(n)) {
         return inc;
      }

      int[] multiples = {1, 10, 100, 1000};
      int[][] ranges = {{m, m + 3}, {m - 2, m}, {m + 3, m + 6}};

      for(int multiple : multiples) {
         for(int i = 0; i < ranges.length; i++) {
            for(int k = ranges[i][0]; k < ranges[i][1]; k++) {
               double inc0 = (max - min) / k;

               if(inc0 * multiple == Math.ceil(inc0 * multiple)) {
                  return inc0;
               }
            }
         }
      }

      return inc;
   }

   /**
    * Check if it is integer.
    */
   public static boolean isInteger(double num) {
      return Math.abs(num - Math.round(num)) < 0.000001;
   }

   /**
    * Create a fake scale for facet coord.
    * @param other the other scale on the coordinate to copy attribute from.
    */
   public static Scale createFakeScale(Scale other) {
      CategoricalScale scale = new CategoricalScale();
      AxisSpec spec = scale.getAxisSpec();
      int grid = (other == null) ? GraphConstants.THIN_LINE : other.getAxisSpec().getGridStyle();
      Color gcolor = (other == null) ? null : other.getAxisSpec().getGridColor();
      scale.init("");
      spec.setAxisStyle(AxisSpec.AXIS_NONE);
      spec.setGridStyle(grid);
      spec.setLabelVisible(false);

      if(gcolor != null) {
         spec.setGridColor(gcolor);
      }

      return scale;
   }

   /**
    * Check if JVM is 1.6 or higher.
    */
   public static boolean isJDK16() {
      return jdk16;
   }

   /**
    * Get the font metrics of the font.
    */
   public static FontMetrics getFontMetrics(Font font) {
      return impl.getFontMetrics(font);
   }

   /**
    * Draw a string on the graphics output.
    */
   public static void drawString(Graphics2D g, String str, double x, double y) {
      Graphics2D g2 = (Graphics2D) g.create();
      AffineTransform trans = g.getTransform();
      // draw the text from top down in vertical arrangement (for CJK).
      // pdf is flipped so we compare 270 instead of 90. (57642)
      int rotation = (int) Math.toDegrees(getRotation(trans));
      boolean vertical = (rotation % 360) == (isPDF() ? 270 : 90) && isCJK(str);

      g2.transform(GDefaults.FLIPY);

      // @by larryl, overall, it seems the text output quality is better with
      // anti-aliasing on for both jdk1.5 and jdk1.6. There are cases where
      // anti-aliasing may degrade the output quality, but it's very hard to
      // predict those cases, so let's keep it on all the time.
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                          RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      // text may be drawn as glyph, which would need regular anti-aliasing in addition
      // to text antialiasing.
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      if(vertical) {
         FontMetrics fm = g2.getFontMetrics();
         g2.rotate(-Math.PI / 2);
         y += fm.getAscent();

         for(int i = 0; i < str.length(); i++) {
            String c = str.substring(i, i + 1);
            impl.drawString(g2, c, x, y);
            // use width instead of height so the size is same as the
            // stringWidth of the whole string
            y += fm.stringWidth(c);
         }
      }
      else if(SVGSupport.getInstance().isSVGGraphics(g) ||
              // PNG text anti-aliasing is not clear, especially for CJK. force glyph. (57643)
              isInteger(trans.getShearX()) && isInteger(trans.getShearY()) && !isPNG())
      {
         impl.drawString(g2, str, x, -y);
      }
      // draw shape for rotated text otherwise it appears a little crooked
      else {
         CoreTool.drawGlyph(g2, str, x, y);
      }

      g2.dispose();
   }

   /**
    * Get a string to uniquely identify a graph.
    */
   public static String getGraphId(Coordinate coord) {
      StringBuilder str = new StringBuilder();
      Scale[] scales = coord.getScales();

      for(int i = 0; i < scales.length; i++) {
         String[] fld = scales[i].getFields();

         for(int j = 0; j < fld.length; j++) {
            str.append(fld[j]);

            if(i != scales.length - 1) {
               str.append(",");
            }
         }
      }

      return str.toString();
   }

   /**
    * Check if the string is CJK characters.
    */
   public static boolean isCJK(String str) {
      for(int i = 0; i < str.length(); i++) {
         char c = str.charAt(i);

         if(c < 0x3400 || c > 0x9ffff) {
            return false;
         }
      }

      return true;
   }

   /**
    * Change the bitmap to same hue as the specified color.
    */
   public static Image changeHue(Image img, Color hue) {
      HueImageFilter filter = new HueImageFilter(hue);
      FilteredImageSource filtered =
         new FilteredImageSource(img.getSource(), filter);

      // Create the filtered image
      img = Toolkit.getDefaultToolkit().createImage(filtered);
      CoreTool.waitForImage(img);

      return img;
   }

   /**
    * Check if the shape is rectangular.
    */
   public static boolean isRectangular(Shape shape) {
      PathIterator iter = shape.getPathIterator(new AffineTransform());
      boolean rectangular = true;
      double[] pts = new double[6];
      Set xset = new HashSet();
      Set yset = new HashSet();

      for(; !iter.isDone(); iter.next()) {
         switch(iter.currentSegment(pts)) {
         case PathIterator.SEG_QUADTO:
         case PathIterator.SEG_CUBICTO:
            rectangular = false;
            break;
         default:
            xset.add(pts[0]);
            yset.add(pts[1]);
         }
      }

      return rectangular && xset.size() == 2 && yset.size() == 2;
   }

   /**
    * Get a property defined in the configuration.
    */
   public static String getProperty(String name, String def) {
      return impl.getProperty(name, def);
   }

   /**
    * Set a property defined in the configuration.
    */
   public static void setProperty(String name, String val) {
      impl.setProperty(name, val);
   }

   /**
    * Get a localized string.
    */
   public static String getString(String str, Object... params) {
      return impl.getString(str, params);
   }

   /**
    * Unwrap a script value.
    */
   public static Object unwrap(Object val) {
      return impl.unwrap(val);
   }

   /**
    * Unwrap an array of script values.
    */
   public static Object[] unwrapArray(Object[] arr) {
      if(arr == null) {
         return arr;
      }

      Object[] arr2 = new Object[arr.length];

      for(int i = 0; i < arr.length; i++) {
         arr2[i] = impl.unwrap(arr[i]);
      }

      return arr2;
   }

   /**
    * Get text color for best contrast on the specified background.
    */
   public static Color getTextColor(Color bg) {
      double bgL = getLuminance(bg);
      /*
      stackoverflow.com/questions/3942878/how-to-decide-font-color-in-white-or-black-depending-on-background-color
      return (bgL + 0.05) / (0.0 + 0.05) > (1.0 + 0.05) / (bgL + 0.05)
         ? Color.BLACK : Color.WHITE;
      */
      // prefer white label
      return bgL > 0.250 ? Color.BLACK : Color.WHITE;
   }

   /**
    * Get the color with the highest contrast with the bg.
    */
   public static Color getComplementaryColor(Color bg) {
      return new Color(255 - bg.getRed(), 255 - bg.getGreen(), 255 - bg.getBlue());
   }

   public static double getLuminance(Color c) {
      return getLuminance(c.getRed(), c.getGreen(), c.getBlue());
   }

   /**
    * Calculate luminance (ITU-R recommendation BT.709).
    */
   public static double getLuminance(int r, int g, int b) {
      return 0.2126 * getL(r) + 0.7152 * getL(g) + 0.0722 * getL(b);
   }

   private static double getL(double c) {
      c = c / 255.0;
      return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
   }

   /**
    * Sets the current thread local.
    * @param is_pdf true if this is a PDF generating thread.
    */
   public static void setIsPDF(boolean is_pdf) {
      if(is_pdf) {
         PDF.set(is_pdf);
      }
      else {
         PDF.remove();
      }
   }

   /**
    * Checks to see if this is a PDF generating thread.
    */
   public static boolean isPDF() {
      return PDF.get();
   }

   /**
    * Sets the current thread local.
    * @param is_png true if this is a PNG generating thread.
    */
   public static void setIsPNG(boolean is_png) {
      if(is_png) {
         PNG.set(is_png);
      }
      else {
         PNG.remove();
      }
   }

   /**
    * Checks to see if this is a PNG generating thread.
    */
   public static boolean isPNG() {
      return PNG.get();
   }

   /**
    * Sets the current thread local.
    * @param is_awt true if this is a AWT generating thread.
    */
   public static void setIsAWT(boolean is_awt) {
      if(is_awt) {
         AWT.set(is_awt);
      }
      else {
         AWT.remove();
      }
   }

   /**
    * Checks to see if this is a AWT generating thread.
    */
   public static boolean isAWT() {
      return AWT.get();
   }

   public static boolean isVectorGraphics(Graphics g) {
      return SVGSupport.getInstance().isSVGGraphics(g);
   }

   public static void setRenderingHint(Graphics2D g, boolean antiAlias) {
      boolean svg = GTool.isVectorGraphics(g);

      if(antiAlias || "true".equals(g.getRenderingHint(GHints.CURVE)) || svg) {
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

         // stroke_pure actually turns off antialiasing in svg
         if(!svg) {
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                               RenderingHints.VALUE_STROKE_PURE);
         }
      }
      else {
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_OFF);
         g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                            RenderingHints.VALUE_STROKE_NORMALIZE);
      }
   }

   /**
    * Handle negative width/height.
    */
   public static Rectangle2D normalizeRect(Rectangle2D rect) {
      if(rect.getHeight() < 0) {
         rect = new Rectangle2D.Double(rect.getX(), rect.getY() + rect.getHeight(),
                                       rect.getWidth(), -rect.getHeight());
      }

      if(rect.getWidth() < 0) {
         rect = new Rectangle2D.Double(rect.getX() + rect.getWidth(), rect.getY(),
                                       -rect.getWidth(), rect.getHeight());
      }

      return rect;
   }

   /**
    * Set the alpha in the color.
    */
   public static Color applyAlpha(Color clr, double alpha) {
      if(clr == null) {
         return null;
      }

      return new Color(clr.getRed(), clr.getGreen(), clr.getBlue(), (int) (alpha * 255));
   }

   private static GImpl impl;
   private static boolean jdk16 = false;
   // @by stephenwebster, For bug1416439678841
   // introduced a thread local variable to indicate whether the process
   // is used for PDF generation.  This is used in the SRImpl class to indicate
   // which FontMetrics to use to get the most accurate output for graphs.
   private static ThreadLocal<Boolean> PDF = ThreadLocal.withInitial(() -> Boolean.FALSE);
   private static ThreadLocal<Boolean> PNG = ThreadLocal.withInitial(() -> Boolean.FALSE);
   private static ThreadLocal<Boolean> AWT = ThreadLocal.withInitial(() -> Boolean.FALSE);

   static {
      try {
         Class.forName("inetsoft.report.internal.Common");
         impl = (GImpl) Class.forName("inetsoft.graph.internal.SRImpl").
            newInstance();
      }
      catch(Throwable ex) {
         impl = new DefaultImpl();
      }

      try {
         Class.forName("java.util.Deque");
         jdk16 = true;
      }
      catch(Throwable ignored) {
         // ignored
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(GTool.class);
}
