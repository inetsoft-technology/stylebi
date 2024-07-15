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
package inetsoft.graph.aesthetic;

import com.inetsoft.build.tern.*;
import inetsoft.graph.GraphConstants;
import inetsoft.graph.internal.GTool;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.io.Serializable;

/**
 * This class is the base class for all line style aesthetics.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=GLine")
public class GLine implements Cloneable, Serializable {
   /**
    * Thin line.
    */
   @TernField
   public static final GLine THIN_LINE =
      new GLine(GraphConstants.THIN_LINE);

   /**
    * Dot line.
    */
   @TernField
   public static final GLine DOT_LINE =
      new GLine(GraphConstants.DOT_LINE);

   /**
    * Dash line.
    */
   @TernField
   public static final GLine DASH_LINE =
      new GLine(GraphConstants.DASH_LINE);

   /**
    * Medium dash line.
    */
   @TernField
   public static final GLine MEDIUM_DASH =
      new GLine(GraphConstants.MEDIUM_DASH);

   /**
    * Large dash line.
    */
   @TernField
   public static final GLine LARGE_DASH =
      new GLine(GraphConstants.LARGE_DASH);

   /**
    * Create a simple solid line.
    */
   public GLine() {
      super();
   }

   /**
    * Create GLine from a line style.
    */
   public GLine(int style) {
      dash = (style & 0xF0) >> 4;
      linew = GTool.getLineWidth(style);
   }

   /**
    * Create a line with dash size and line width.
    * @param dash the dash size.
    * @param linew the line width.
    */
   @TernConstructor
   public GLine(double dash, double linew) {
      this.dash = dash;
      this.linew = linew;
   }

   /**
    * Get the dash size.
    */
   @TernMethod
   public double getDash() {
      return dash;
   }

   /**
    * Get the line width.
    */
   @TernMethod
   public double getLineWidth() {
      return linew;
   }

   /**
    * Get the line style.
    */
   @TernMethod
   public int getStyle() {
      int fraction = (int) ((linew - (int) linew) * 16);
      return ((int) dash << 4) | (int) linew | (fraction << 12) |
         // to be consistent with the line styles defined in GraphConstants
	 GraphConstants.SOLID_MASK;
   }

   /**
    * Get the stroke to draw the line style. This doesn't support varying size
    * and color on the line. Use getShape() for line with full visual
    * attributes.
    */
   public Stroke getStroke() {
      return getStroke(linew);
   }

   /**
    * Get a stroke with the specified line width.
    */
   public Stroke getStroke(double linew) {
      float w = (float) linew;

      if(dash == 0) {
         // @by larryl, using JOIN_ROUND or CAP_ROUND causes a straight line to
         // lose a pixel at the end in some cases
         return new BasicStroke(w, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER);
      }

      // line width is added to the dash length, so a dash of 2 with a line
      // width of 2 would actually have the dashes touching each other
      double ndash = Math.max(dash + w - 0.5, 1);

      return new BasicStroke(w, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER,
                             10, new float[] {(float) ndash}, 0);
   }

   /**
    * Draw the shape according using the line style.
    * @hidden
    * @param g the Graphics2D.
    * @param start the start point of the line.
    * @param end the end point of the line.
    * @param startColor the start point color.
    * @param endColor the end point color.
    * @param shape the shape to be filled.
    */
   public static final void paint(Graphics2D g, Point2D start, Point2D end,
                                  Color startColor, Color endColor,
                                  Shape shape)
   {
      g = (Graphics2D) g.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                         RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(startColor);
      g.setPaint(new GradientPaint(start, startColor, end, endColor));
      g.fill(shape);
      g.dispose();
   }

   /**
    * Get the line shape.
    * @param start the start point of the line.
    * @param end the end point of the line.
    * @param startSize the start point line space percent.
    * @param endSize the end point line space percent.
    * @param startLine the start line style.
    * @param endLine the end line style.
    */
   public static Shape getShape(Point2D start, Point2D end,
                                double startSize, double endSize,
                                GLine startLine, GLine endLine)
   {
      if(startLine == null) {
         startLine = GLine.THIN_LINE;
      }

      if(endLine == null) {
         endLine = GLine.THIN_LINE;
      }

      double startPercent = (startLine.dash == 0)
         ? 1 : startLine.dash / FULL_DASH;
      double endPercent = (endLine.dash == 0)
         ? 1 : endLine.dash / FULL_DASH;

      double x1 = start.getX();
      double y1 = start.getY();
      double x2 = end.getX();
      double y2 = end.getY();
      double dist = start.distance(end);
      double r1 = startSize / 2;
      double r2 = endSize / 2;
      GeneralPath shape = new GeneralPath();
      double angle = GTool.getAngle(start, end);

      if(startPercent != 1 || endPercent != 1) {
         double len = dist;
         boolean dash = true;
         double r0 = r1;

         while(len > 0) {
            // dash length is proportional to the start and end dash size
            // relative to the position at the line
            double perc = startPercent * len / dist +
                          endPercent * (1 - len / dist);
            double seglen = FULL_DASH * perc;

            // this matches how java draws dashed line using BasicStroke. The
            // line width is added to each end of the line and extended into
            // the gap
            if(dash) {
               seglen += r1 + r2;
            }

            seglen = Math.min(len, seglen);
            double x = x1 + seglen * Math.cos(angle);
            double y = y1 + seglen * Math.sin(angle);
            double r = r0 + (r2 - r0) * (dist - len) / dist;

            if(dash) {
               addLineSegment(shape, x1, y1, x, y, r1, r, angle);
            }

            dash = !dash;
            x1 = x;
            y1 = y;
            r1 = r;

            len -= seglen;
         }
      }
      else {
         addLineSegment(shape, x1, y1, x2, y2, r1, r2, angle);
      }

      return shape;
   }

   /**
    * Add a line segment from x1,y1 to x2,y2.
    */
   private static void addLineSegment(GeneralPath shape, double x1, double y1,
                                      double x2, double y2,
                                      double r1, double r2, double angle)
   {
      Point2D offset1 = getOffset(r1, angle);
      Point2D offset2 = getOffset(r2, angle);

      shape.moveTo((float) (x1 - offset1.getX()),
                   (float) (y1 - offset1.getY()));
      shape.lineTo((float) (x1 + offset1.getX()),
                   (float) (y1 + offset1.getY()));
      shape.lineTo((float) (x2 + offset2.getX()),
                   (float) (y2 + offset2.getY()));
      shape.lineTo((float) (x2 - offset2.getX()),
                   (float) (y2 - offset2.getY()));
      shape.closePath();
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof GLine)) {
         return false;
      }

      return getStyle() == ((GLine) obj).getStyle();
   }

   @Override
   public int hashCode() {
      return getStyle();
   }

   /**
    * Get the distanceof X and Y from the center to the edge of the line.
    */
   private static Point2D getOffset(double r, double angle) {
      return new Point2D.Double(r * Math.sin(angle), -r * Math.cos(angle));
   }

   public String toString() {
      return super.toString() + "[" + dash + ", " + linew + "]";
   }

   // absoluate size corresponding to percent == 1
   private static final double FULL_DASH = 15;
   private static final GLine[] LINES = {
      THIN_LINE, DOT_LINE, DASH_LINE, MEDIUM_DASH, LARGE_DASH};

   private double dash = 0;
   private double linew = 1;
   private static final long serialVersionUID = 1L;
}
