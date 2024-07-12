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

import com.inetsoft.build.tern.*;

import java.awt.*;
import java.awt.geom.*;

/**
 * This class defines a shape frame gradating triangle for numeric values.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=TriangleShapeFrame")
public class TriangleShapeFrame extends LinearShapeFrame {
   /**
    * Create a shape frame.
    */
   public TriangleShapeFrame() {
   }

   /**
    * Create a shape frame.
    * @param field field to get value to map to shapes.
    */
   @TernConstructor
   public TriangleShapeFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Check if the shape should be filled.
    */
   @TernMethod
   public boolean isFill() {
      return fill;
   }

   /**
    * Set whether this shape should be filled.
    */
   @TernMethod
   public void setFill(boolean fill) {
      this.fill = fill;
   }

   /**
    * Get the shape for the ratio.
    * @param ratio the value between
    * 0 and 1, and is the position of the value in a linear scale.
    */
   @Override
   protected GShape getShape(double ratio) {
      Echelon shape = new Echelon(ratio);

      shape.setFill(isFill());
      return shape;
   }

   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      TriangleShapeFrame frame = (TriangleShapeFrame) obj;
      return fill == frame.fill;
   }

   /**
    * Echelon shape.
    */
   private static class Echelon extends GShape {
      /**
       * Constructor.
       * @param ratio ratio from 0 to 1.
       */
      public Echelon(double ratio) {
         this.ratio = ratio;
      }

      @Override
      public double getMinSize() {
         return 8;
      }

      @Override
      public Shape getShape(double x, double y, double width, double height) {
         GeneralPath path = new GeneralPath();
         float x1, y1, x2, y2, x3, y3;
         float b = (float) (ratio * width);
         float a = (float) (width - b);
         float x0 = (a == 0) ?
            (float) (x + width / 2) : (float) (x + width / 2 - a / 2);
         float y0 = (float) y;

         if(a == 0) {
            x1 = x0 - b / 2;
            y1 = (float) (y0 + height);
            x2 = x0 + b / 2;
            y2 = (float) (y0 + height);
            x3 = x0;
            y3 = y0;
         }
         else if(b == 0) {
            x1 = x0 + a / 2;
            y1 = (float) (y0 + height);
            x2 = x1;
            y2 = y1;
            x3 = x0 + a;
            y3 = y0;
         }
         else {
            x1 = x0 - (b - a) / 2;
            y1 = (float) (y0 + height);
            x2 = x0 + (b + a) / 2;
            y2 = (float) (y0 + height);
            x3 = x0 + a;
            y3 = y0;
         }

         path.moveTo(x0, y0);
         path.lineTo(x1, y1);
         path.lineTo(x2, y2);
         path.lineTo(x3, y3);
         path.closePath();

         Rectangle2D box = path.getBounds2D();
         AffineTransform trans = AffineTransform.getTranslateInstance(
	    x + width / 2 - box.getWidth() / 2 - box.getX(), 0);

         // make sure the shape starts at x so it's properly centered
         return trans.createTransformedShape(path);
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

         return ratio == ((Echelon) obj).ratio;
      }

      private double ratio;
   }

   private boolean fill = false;
   private static final long serialVersionUID = 1L;
}
