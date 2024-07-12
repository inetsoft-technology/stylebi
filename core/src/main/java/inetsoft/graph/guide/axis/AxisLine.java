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
package inetsoft.graph.guide.axis;

import inetsoft.graph.Visualizable;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.*;

/**
 * This is an axis line including ticks.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class AxisLine extends Visualizable {
   /**
    * Create an axis line.
    */
   public AxisLine(Axis axis) {
      this.axis = axis;
   }

   /**
    * Get the axis containing this axis line.
    */
   public Axis getAxis() {
      return axis;
   }

   /**
    * Get min size of this line.
    */
   @Override
   protected double getMinWidth0() {
      return LINE_MIN_LENGTH;
   }

   /**
    * Get min height of this line.
    */
   @Override
   protected double getMinHeight0() {
      double tick = axis.isTickDown() && axis.isTickVisible() ? MAJOR_TICK_LENGTH : 0;
      return axis.isLineVisible() ? LINE_HEIGHT + tick : tick;
   }

   /**
    * Get preferred width of this visualizable.
    */
   @Override
   protected double getPreferredWidth0() {
      return getMinWidth0();
   }

   /**
    * Get the preferred height of this visualizable.
    */
   @Override
   protected double getPreferredHeight0() {
      return getMinHeight0();
   }

   /**
    * Paint the visual object on the graphics.
    */
   @Override
   public void paint(Graphics2D g) {
      // null color treated as transparent
      if(axis.getLineColor() == null) {
         return;
      }

      // @by ChrisSpagnoli bug1422595786301 2015-2-17
      // Paint the entire axis line, including trend line project forward
      Point2D pos1;
      Point2D pos2;

      if((axis instanceof DefaultAxis) && "y".equals(((DefaultAxis) axis).getAxisType())) {
         pos1 = ((DefaultAxis) axis).getAxisScreenMinPos();
         pos2 = ((DefaultAxis) axis).getAxisScreenMaxPos();
      }
      else {
         pos1 = new Point2D.Double(0, 0);
         pos2 = new Point2D.Double(((DefaultAxis) axis).getLength(), 0);
         pos1 = getScreenTransform().transform(pos1, null);
         pos2 = getScreenTransform().transform(pos2, null);
      }

      // set anti alias render hint if needed
      if(pos1.getX() != pos2.getX() && pos1.getY() != pos2.getY() || GTool.isVectorGraphics(g)) {
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      }
      else {
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
      }

      Color ocolor = g.getColor();
      g.setColor(axis.getLineColor());

      if(axis.isLineVisible()) {
         GTool.drawLine(g, new Line2D.Double(pos1, pos2));
      }

      Scale scale = axis.getScale();

      if(axis.isTickVisible()) {
         paintTicks(g, axis.getTicks(), true, pos1, pos2);

         if(scale instanceof LinearScale) {
            LinearScale lscale = (LinearScale) scale;
            double[] minor = lscale.getMinorTicks();

            if(minor != null) {
               paintTicks(g, minor, false, pos1, pos2);
            }
         }
      }

      g.setColor(ocolor);
   }

   /**
    * Paint ticks.
    */
   private void paintTicks(Graphics2D g2, double[] ticks, boolean major,
      Point2D axisPos1, Point2D axisPos2)
   {
      int tickWidth = major ? MAJOR_TICK_LENGTH : MINOR_TICK_LENGTH;
      Scale scale = axis.getScale();
      double[] tlocs = ((DefaultAxis) axis).getTickLocations(ticks);

      // @by ChrisSpagnoli feature1379102629417 2015-1-15
      // Project Y-ticks forward to end of axis.
      // @by: ChrisSpagnoli bug1421743488123 2015-1-20
      // @by: ChrisSpagnoli bug1422846693610 2015-2-6
      // Only do this for the Y axis, not X
      if(axis instanceof DefaultAxis && major) {
         DefaultAxis defaultAxis = (DefaultAxis) axis;

         if("y".equals(defaultAxis.getAxisType())) {
            ticks = projectTicksTrendLine(ticks);
            tlocs = projectTicksTrendLine(tlocs);
         }
      }

      // avoid ticks too closed or overlapped
      int inc = 1;

      if(ticks.length >= 2) {
         Point2D pos1 = getTickPosition(tlocs[0], 0);
         Point2D pos2 = getTickPosition(tlocs[1], 0);
         double dis = pos1.distance(pos2);

         if(dis < GDefaults.TICK_MIN_GAP && dis > 0) {
            inc = (int) Math.min(ticks.length, Math.ceil(GDefaults.TICK_MIN_GAP / dis));
         }

         // ignore minor tick if it's not explicitly set and the gap is
         // very small so the labels at the end won't be confused with
         // the minor tick
         if(!major && scale instanceof LinearScale &&
            ((LinearScale) scale).getMinorIncrement() == null)
         {
            FontMetrics fm = g2.getFontMetrics();
            double minGap = 0;

            // vertical axis, min gap is multiple of font height
            if(pos1.getX() == pos2.getX()) {
               minGap = fm.getHeight() * 1.5;
            }
            // horizontal axis, min gap is multiple of min/max label width
            else {
               VLabel[] labels = axis.getLabels();

               if(labels != null && labels.length > 0) {
                  minGap = labels[0].getPreferredWidth();
                  minGap = Math.max(
                     minGap, labels[labels.length - 1].getPreferredWidth());
                  minGap *= 1.8;
               }
            }

            if(dis < minGap) {
               return;
            }
         }
      }

      g2.setColor(axis.getLineColor());

      for(int i = 0; i < ticks.length; ) {
         if(!axis.isMinTickVisible() && i == 0 && major) {
            i += inc;
            continue;
         }

         if(!axis.isMaxTickVisible() && i == ticks.length - 1 && major) {
            break;
         }

         double y = axis.isTickDown() ? -tickWidth : tickWidth;
         Point2D pos1 = getTickPosition(tlocs[i], 0);
         Point2D pos2 = getTickPosition(tlocs[i], y);
         pos2 = applyLength(pos1, pos2, tickWidth);

         Point2D minPos = ((DefaultAxis) axis).getAxisScreenMinPos();
         Point2D maxPos = ((DefaultAxis) axis).getAxisScreenMaxPos();
         double minX = Math.min(minPos.getX(), maxPos.getX());
         double minY = Math.min(minPos.getY(), maxPos.getY());
         double maxX = Math.max(minPos.getX(), maxPos.getX());
         double maxY = Math.max(minPos.getY(), maxPos.getY());

         // @by ChrisSpagnoli bug1422604427332 2015-2-9
         // Never draw tick marks beyond end of the axis line
         if("y".equals(((DefaultAxis) axis).getAxisType())) {
            if(pos1.getY() < minY || pos1.getY() > maxY) {
               i += inc;
               continue;
            }
         }
         else if(axisPos1.getY() == axisPos2.getY()) {
            if(pos1.getX() < minX || pos1.getX() > maxX) {
               i += inc;
               continue;
            }
         }

         // set anti alias render hint if needed
         if(pos1.getX() != pos2.getX() && pos1.getY() != pos2.getY() ||
            GTool.isVectorGraphics(g2))
         {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
         }
         else {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_OFF);
         }

         GTool.drawLine(g2, new Line2D.Double(pos1, pos2));

         i += inc;

         if(i - inc < ticks.length - 1) {
            i = Math.min(i, ticks.length - 1);
         }
      }
   }

   /**
    * Get the tick location.
    * @param x tick x location.
    * @param yoff the offset from the tick point on the axis.
    */
   Point2D getTickPosition(double x, double yoff) {
      Point2D pos = new Point2D.Double(x, yoff);
      return getScreenTransform().transform(pos, null);
   }

   /**
    * Get the height of the axis line (or width if vertical).
    */
   public double getHeight() {
      return height;
   }

   /**
    * Set the height of the axis line (or width if vertical).
    */
   public void setHeight(double height) {
      this.height = height;
   }

   /**
    * Calculate the position from pos1 to pos2 at the specified length.
    */
   private Point2D applyLength(Point2D pos1, Point2D pos2, double length) {
      double dist = pos1.distance(pos2);
      double x = pos1.getX() + (pos2.getX() - pos1.getX()) * length / dist;
      double y = pos1.getY() + (pos2.getY() - pos1.getY()) * length / dist;

      return new Point2D.Double(x, y);
   }

   /**
    * Gets the bounds of this visual in the form of a Rectangle2D object.
    * The bounds specify this visual's width, height, and location relative to
    * the whole graph.
    */
   @Override
   public Rectangle2D getBounds() {
      /* optimization
      Rectangle2D bounds = new Rectangle2D.Double(
         0, 0, ((DefaultAxis) axis).getLength(), height);
      return getScreenTransform().createTransformedShape(bounds).getBounds2D();
      */
      Point2D pt1 = getScreenTransform().transform(new Point2D.Double(0, 0), null);
      double w = ((DefaultAxis) axis).getLength();
      Point2D pt2 = getScreenTransform().transform(new Point2D.Double(w, height), null);
      return new Rectangle2D.Double(Math.min(pt1.getX(), pt2.getX()),
                                    Math.min(pt1.getY(), pt2.getY()),
                                    Math.abs(pt1.getX() - pt2.getX()),
                                    Math.abs(pt1.getY() - pt2.getY()));
   }

   // @by ChrisSpagnoli bug1421743488123 2015-1-20
   public double getTrendLineMin() {
      if(axis.getCoordinate() == null) {
         return Double.POSITIVE_INFINITY;
      }

      return axis.getCoordinate().getTrendLineMin();
   }

   // @by ChrisSpagnoli bug1421743488123 2015-1-20
   public double getTrendLineMax() {
      if(axis.getCoordinate() == null) {
         return Double.NEGATIVE_INFINITY;
      }

      return axis.getCoordinate().getTrendLineMax();
   }

   // @by ChrisSpagnoli feature1379102629417 2015-1-15
   /**
    * Creates an expanded tick array, caused by trend line projection.
    * Same code can be used for tlocs.
    */
   public double[] projectTicksTrendLine(final double[] ticks) {
      // @by ChrisSpagnoli bug1433904338205 2015-6-11
      // Check for default in getTrendLineMin()/getTrendLineMax()
      if(getTrendLineMax() == Double.NEGATIVE_INFINITY &&
         getTrendLineMin() == Double.POSITIVE_INFINITY)
      {
         return ticks;
      }

      // @by ChrisSpagnoli bug1421743488123 2015-1-20
      if(ticks == null || ticks.length < 2) {
         return ticks;
      }

      final double ticksDelta = ticks[1] - ticks[0];

      if(ticksDelta == 0) {
         return ticks;
      }

      if(projectForward == 0 || projectBackward == 0) {
         // @by ChrisSpagnoli bug1430357109900 2015-5-11
         // Correct calculations for projecting axis ticks
         if(getTrendLineMax() != Double.NEGATIVE_INFINITY &&
            getTrendLineMax() > ticks[ticks.length-1] + ticksDelta)
         {
            projectForward = (int) Math.floor(
               (getTrendLineMax() - ticks[ticks.length-1] - ticksDelta/2) / ticksDelta);
         }

         if(getTrendLineMin() != Double.POSITIVE_INFINITY &&
            getTrendLineMin() < ticks[0] - ticksDelta)
         {
            projectBackward = (int) Math.floor(
               (ticks[0] - getTrendLineMin() - ticksDelta / 2) / ticksDelta);
         }
      }

      double[] adjustedTicks = ticks;

      // @by ChrisSpagnoli bug1433904338205 2015-6-11
      // Cap projectForward to 1000 to prevent "bad things" if calc from bad data.
      if(projectForward > 0 && projectForward < 1000) {
         double[] newTics = new double[adjustedTicks.length + projectForward];

         for(int i = 0; i < newTics.length; i++) {
            newTics[i] = i < adjustedTicks.length ?
               adjustedTicks[i] :
               newTics[i-1] + ticksDelta;
         }

         adjustedTicks = newTics;
      }

      if(projectBackward > 0 && projectBackward < 1000) {
         double[] newTics = new double[adjustedTicks.length + projectBackward];

         for(int i = newTics.length - 1, j = adjustedTicks.length - 1;
            i >= 0; i--, j--)
         {
            newTics[i] = (j > 0 ? adjustedTicks[j] : newTics[i+1] - ticksDelta);
         }

         adjustedTicks = newTics;
      }

      return adjustedTicks;
   }

   /**
    * Get the number of projected forward ticks.
    */
   public int getProjectForward() {
      return projectForward;
   }

   /**
    * Get the number of projected backward ticks.
    */
   public int getProjectBackward() {
      return projectBackward;
   }

   // Tick may be painted outside of bounds
   @Override
   public boolean isPaintInBounds() {
      return false;
   }

   static int MAJOR_TICK_LENGTH = 4;
   static int MINOR_TICK_LENGTH = 2;
   private static int LINE_HEIGHT = 1;
   private static int LINE_MIN_LENGTH = 50;

   private Axis axis;
   private double height;

   private int projectForward = 0;
   private int projectBackward = 0;

   private static final Logger LOG =
      LoggerFactory.getLogger(AxisLine.class);
}
