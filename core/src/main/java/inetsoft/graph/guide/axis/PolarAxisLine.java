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
package inetsoft.graph.guide.axis;

import inetsoft.graph.internal.PolarUtil;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;

import java.awt.*;
import java.awt.geom.*;

/**
 * Visual oral axis line.
 *
 * @hidden
 * @version 10.0
 * @author InetSoft Technology
 */
class PolarAxisLine extends AxisLine {
   /**
    * Constructor.
    */
   public PolarAxisLine(Axis axis) {
      super(axis);
   }

   /**
    * Get min size of this line.
    */
   @Override
   protected double getMinWidth0() {
      return width;
   }

   /**
    * Get min height of this line.
    * @return min height of this line.
    */
   @Override
   protected double getMinHeight0() {
      return height;
   }

   /**
    * Set the width of the axis (oval).
    * @param width new width of this axis in logic coordinate.
    */
   public void setWidth(double width) {
      this.width = width;
   }

   /**
    * Set the height of the axis (oval).
    * @param height new height of this axis in logic coordinate.
    */
   @Override
   public void setHeight(double height) {
      this.height = height;
   }

   /**
    * Paint the visual object on the graphics.
    * @param g the graphic.
    */
   @Override
   public void paint(Graphics2D g) {
      Graphics2D g2 = (Graphics2D) g.create();
      RenderingHints hints = new RenderingHints(null);
      hints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHints(hints);
      g2.setColor(getAxis().getLineColor());

      if(getAxis().isLineVisible()) {
         Ellipse2D oval = new Ellipse2D.Double(0, 0, width, height);
         Shape path = getScreenTransform().createTransformedShape(oval);

         g2.draw(path);
      }

      if(getAxis().isTickVisible()) {
         double tickw = getAxis().isTickDown()
            ? MAJOR_TICK_LENGTH : -MAJOR_TICK_LENGTH;
         double[] ticks = getAxis().getScale().getTicks();

         paintTicks(g2, ticks, tickw);

         if(getAxis().getScale() instanceof LinearScale) {
            LinearScale lscale = (LinearScale) getAxis().getScale();
            double[] minor = lscale.getMinorTicks();
	    tickw = getAxis().isTickDown()
	       ? MINOR_TICK_LENGTH : -MINOR_TICK_LENGTH;

            if(minor != null) {
               paintTicks(g, minor, tickw);
            }
         }
      }

      g2.dispose();
   }

   /**
    * Paint the axis ticks.
    */
   private void paintTicks(Graphics2D g2, double[] ticks, double tickw) {
      Axis axis = getAxis();
      Scale scale = axis.getScale();
      Point2D center = new Point2D.Double(width / 2, height / 2);

      ticks = PolarUtil.getTickLocations(scale, ticks);
      g2.setColor(axis.getLineColor());

      for(int i = 0; i < ticks.length; i++) {
         if(!axis.isMinTickVisible() && i == 0 && ticks[i] == scale.getMin()) {
            continue;
         }

         if(!axis.isMaxTickVisible() && i == ticks.length - 1 &&
            ticks[i] == scale.getMax())
         {
            continue;
         }

         Point2D loc = new Point2D.Double(
            Math.cos(ticks[i]) * width / 2 + width / 2,
            Math.sin(ticks[i]) * height / 2 + height / 2);
         double distance = center.distance(loc);
         double tickx = tickw * (center.getX() - loc.getX()) / distance;
         double ticky = tickw * (center.getY() - loc.getY()) / distance;
         // loc2 is the end of tick
         Point2D loc2 = new Point2D.Double(loc.getX() - tickx,
                                           loc.getY() - ticky);

         loc = getScreenTransform().transform(loc, null);
         loc2 = getScreenTransform().transform(loc2, null);
         g2.draw(new Line2D.Double(loc, loc2));
      }
   }

   /**
    * Gets the bounds of this visual in the form of a Rectangle2D object.
    * The bounds specify this visual's width, height, and location relative to
    * the whole graph.
    */
   @Override
   public Rectangle2D getBounds() {
      Point2D pos1 = new Point2D.Double(0, 0);
      pos1 = getScreenTransform().transform(pos1, null);

      return new Rectangle2D.Double(pos1.getX(), pos1.getY(), width, height);
   }

   private double width;
   private double height;
}
