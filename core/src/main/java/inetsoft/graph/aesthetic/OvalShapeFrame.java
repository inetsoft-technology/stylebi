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

import java.awt.*;
import java.awt.geom.Ellipse2D;

/**
 * This class defines a shape frame gradating oval for numeric values.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class OvalShapeFrame extends LinearShapeFrame {
   /**
    * Create a shape frame.
    */
   public OvalShapeFrame() {
   }

   /**
    * Create a shape frame.
    * @param field field to get value to map to shapes.
    */
   public OvalShapeFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Check if the shape should be filled.
    */
   public boolean isFill() {
      return fill;
   }

   /**
    * Set whether this shape should be filled.
    */
   public void setFill(boolean fill) {
      this.fill = fill;
   }

   /**
    * Get the shape at the ratio.
    * @param ratio the value between
    * 0 and 1, and is the position of the value in a linear scale.
    */
   @Override
   protected GShape getShape(double ratio) {
      Ellipse shape = new Ellipse(ratio);

      shape.setFill(isFill());
      return shape;
   }

   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      OvalShapeFrame frame = (OvalShapeFrame) obj;
      return fill == frame.fill;
   }

   /**
    * Ellipse shape.
    */
   private static class Ellipse extends GShape {
      /*
       * Constructor.
       * @param ratio from 0 to 1.
       */
      public Ellipse(double ratio) {
         this.ratio = ratio;
      }

      @Override
      public double getMinSize() {
         return 8;
      }

      @Override
      public Shape getShape(double x, double y, double width, double height) {
         // the maximum height is r + r/2 for ellipse shape
	 double xr = width / 2 / 1.5;
	 double yr = height / 2 / 1.5;
         double r1 = yr + (ratio - 0.5) * yr;
         double r2 = xr + (0.5 - ratio) * xr;
         // move the shape to the center of the point so the shape can be
         // properly centered
         return new Ellipse2D.Double(x + (width / 2 - r2), y, 2 * r2, 2 * r1);
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

         return ratio == ((Ellipse) obj).ratio;
      }

      private double ratio;
   }

   private boolean fill = false;
   private static final long serialVersionUID = 1L;
}
