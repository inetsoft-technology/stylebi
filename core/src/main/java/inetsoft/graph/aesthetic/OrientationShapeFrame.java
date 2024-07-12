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
package inetsoft.graph.aesthetic;

import com.inetsoft.build.tern.TernClass;
import com.inetsoft.build.tern.TernConstructor;

import java.awt.*;
import java.awt.geom.Line2D;

/**
 * This class defines a shape frame with rotated lines.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=OrientationShapeFrame")
public class OrientationShapeFrame extends LinearShapeFrame {
   /**
    * Create a shape frame.
    */
   public OrientationShapeFrame() {
   }

   /**
    * Create a shape frame.
    * @param field field to get value to map to shapes.
    */
   @TernConstructor
   public OrientationShapeFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Get the shape at the ratio.
    * @param ratio the value between
    * 0 and 1, and is the position of the value in a linear scale.
    */
   @Override
   protected GShape getShape(double ratio) {
      return new OrientationShape(ratio * Math.PI / 2);
   }

   /**
    * Polygon shape.
    */
   private static class OrientationShape extends GShape {
      /**
       * Constructor.
       * @param angle a radian of rotation angle.
       */
      public OrientationShape(double angle) {
         this.angle = angle;
      }

      /**
       * Get the shape object for this shape.
       * @param x the lower-left corner x position.
       * @param y the lower-left corner y position.
       * @param width the width of the shape in points.
       * @param height the height of the shape in points.
       */
      @Override
      public Shape getShape(double x, double y, double width, double height) {
         double cx = x + width / 2;
         double cy = y + height / 2;
         double r = Math.min(width / 2, height / 2);
         double x1 = cx - Math.cos(angle) * r;
         double y1 = cy + Math.sin(angle) * r;
         double x2 = cx + Math.cos(angle) * r;
         double y2 = cy - Math.sin(angle) * r;

         return new Line2D.Double(x1, y1, x2, y2);
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

         return angle == ((OrientationShape) obj).angle;
      }

      private double angle;
   }

   private static final long serialVersionUID = 1L;
}
