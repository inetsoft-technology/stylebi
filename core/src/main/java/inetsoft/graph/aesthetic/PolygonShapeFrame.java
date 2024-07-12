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
import java.awt.geom.GeneralPath;

/**
 * This class defines a shape frame gradating polygon for numeric values.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=PolygonShapeFrame")
public class PolygonShapeFrame extends LinearShapeFrame {
   /**
    * Create a shape frame.
    */
   public PolygonShapeFrame() {
   }

   /**
    * Create a shape frame.
    * @param field field to get value to map to shapes.
    */
   @TernConstructor
   public PolygonShapeFrame(String field) {
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
    * Get the shape at the ratio.
    * @param ratio the value between
    * 0 and 1, and is the position of the value in a linear scale.
    */
   @Override
   protected GShape getShape(double ratio) {
      Polygon shape = new Polygon((int) (3 + ratio * 6));

      shape.setFill(isFill());
      return shape;
   }

   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      PolygonShapeFrame frame = (PolygonShapeFrame) obj;

      return fill == frame.fill;
   }

   /**
    * Polygon shape.
    */
   private static class Polygon extends GShape {
      /*
       * Constructor.
       * @param n the number of edges.
       */
      public Polygon(int n) {
         this.n = n;
      }

      @Override
      public double getMinSize() {
         return 8;
      }

      @Override
      public Shape getShape(double x, double y, double width, double height) {
         GeneralPath path = new GeneralPath();
         int edges = n;
         double inc = Math.PI * 2 / edges;
         double cx = x + width / 2;
         double cy = y + height / 2;
         float x0 = 0, y0 = 0;
         int i = 0;

         for(double angle = Math.PI / 2; angle < Math.PI * 2.5 && i < edges;
             angle += inc, i++)
         {
            float x1 = (float) (cx + Math.cos(angle) * width / 2);
            float y1 = (float) (cy + Math.sin(angle) * height / 2);

            if(i == 0) {
               x0 = (float) (cx + Math.cos(angle) * width / 2);
               y0 = (float) (cy + Math.sin(angle) * height / 2);
               path.moveTo(x1, y1);
            }
            else {
               path.lineTo(x1, y1);
            }
         }

         path.lineTo(x0, y0);

         return path;
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

         return n == ((Polygon) obj).n;
      }

      private int n;
   }

   private boolean fill = false;
   private static final long serialVersionUID = 1L;
}
