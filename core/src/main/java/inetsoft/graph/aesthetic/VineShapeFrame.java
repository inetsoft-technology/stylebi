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
package inetsoft.graph.aesthetic;

import com.inetsoft.build.tern.*;
import inetsoft.graph.scale.Scale;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;

/**
 * A vine shape supports up to three variables. It is drawn as a circle with
 * a line extending from the center of the circle to a direction and length
 * controlled by a variable. The values are plotted as the direction of a
 * line, length of the line, and the size of a circle, respectively.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=VineShapeFrame")
public class VineShapeFrame extends MultiShapeFrame {
   /**
    * Create a shape frame.
    */
   public VineShapeFrame() {
      setScaleOption(Scale.ZERO);
   }

   /**
    * Create a shape frame.
    * @param fields fields to get value to map to shapes.
    */
   @TernConstructor
   public VineShapeFrame(String... fields) {
      this();
      setFields(fields);
   }

   /**
    * Set the stem starting angle (degrees).
    */
   @TernMethod
   public void setStartAngle(int angle) {
      startAngle = angle;
   }

   /**
    * Get the stem starting angle.
    */
   @TernMethod
   public int getStartAngle() {
      return startAngle;
   }

   /**
    * Set the stem ending angle (degrees).
    */
   @TernMethod
   public void setEndAngle(int angle) {
      endAngle = angle;
   }

   /**
    * Get the stem ending angle.
    */
   @TernMethod
   public int getEndAngle() {
      return endAngle;
   }

   /**
    * Get a shape for the tuple. The values in the tuple have been scaled.
    */
   @TernMethod
   @Override
   protected GShape getShape(double... values) {
      return new VineShape(values);
   }

   /**
    * Don't share scale.
    */
   @Override
   @TernMethod
   protected boolean isSharedScale() {
      return false;
   }

   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      VineShapeFrame frame = (VineShapeFrame) obj;
      return startAngle == frame.startAngle &&
         endAngle == frame.endAngle;
   }

   private class VineShape extends GShape {
      public VineShape(double... values) {
         this.values = values;
      }

      @Override
      public double getMinSize() {
         return 24;
      }

      @Override
      public Shape getShape(double x, double y, double w, double h) {
         GeneralPath path = new GeneralPath();
         Line2D line = getStem(x, y, w, h);

         path.moveTo((float) line.getX1(), (float) line.getY1());
         path.lineTo((float) line.getX2(), (float) line.getY2());

         return path;
      }

      @Override
      public void paint(Graphics2D g, double x, double y, double w, double h) {
         double size = (values.length > 2) ? values[2] : 0.2;
         double r = 1 + 3 * size;

         GShape.CIRCLE.paint(g, x + w / 2 - r, y + h / 2 - r, 2 * r, 2* r);
         g.draw(getStem(x, y, w, h));
      }

      private Line2D getStem(double x, double y, double w, double h) {
         double direction = (values.length > 0) ? values[0] : 1;
         double length = (values.length > 1) ? values[1] : 1;
         double angle = startAngle + (endAngle - startAngle) * direction;
         double r = Math.max(w, h) / 2;
         double cx = x + w / 2;
         double cy = y + h / 2;

         r = 2 + (r - 2) * length;
         angle = angle * Math.PI / 180;

         return new Line2D.Double(cx, cy, cx + Math.cos(angle) * r,
                                  cy + Math.sin(angle) * r);

      }

      @Override
      protected boolean isAntiAlias() {
         return true;
      }

      /**
       * Check if equals another objects.
       */
      public boolean equals(Object obj) {
         if(!super.equals(obj)) {
            return false;
         }

         return equalsArray(values, ((VineShape) obj).values);
      }

      private double[] values;
   }

   private int startAngle = 0; // starting angle in degrees
   private int endAngle = 90; // ending angle in degrees
   private static final long serialVersionUID = 1L;
}
