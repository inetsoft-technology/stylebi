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
 * This class defines a shape frame fills in a circle at a ratio.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=FillShapeFrame")
public class FillShapeFrame extends LinearShapeFrame {
   /**
    * Create a shape frame.
    */
   public FillShapeFrame() {
   }

   /**
    * Create a shape frame.
    * @param field field to get value to map to shapes.
    */
   @TernConstructor
   public FillShapeFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Check if to fill the circle as a pie.
    */
   @TernMethod
   public boolean isArc() {
      return arc;
   }

   /**
    * Set whether to fill the circle as a pie.
    * @param arc true to fill the circle as a pie, false (default) to fill it
    * from bottom up.
    */
   @TernMethod
   public void setArc(boolean arc) {
      this.arc = arc;
   }

   /*
    * Get the shape.
    * @param ratio the value between
    * 0 and 1, and is the position of the value in a linear scale.
    */
   @Override
   @TernMethod
   protected GShape getShape(double ratio) {
      return new Fill(ratio);
   }

   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      FillShapeFrame frame = (FillShapeFrame) obj;
      return arc == frame.arc;
   }

   /*
    * Filled circle shape.
    */
   private class Fill extends GShape {
      public Fill(double ratio) {
         this.ratio = ratio;
         setFill(true);
      }

      @Override
      public Shape getShape(double x, double y, double w, double h) {
         return new Ellipse2D.Double(x, y, w, h);
      }

      @Override
      public void paint(Graphics2D g, double x, double y, double w, double h) {
         paint(g, getShape(x, y, w, h));
      }

      @Override
      public void paint(Graphics2D g, Shape shape) {
         g = (Graphics2D) g.create();
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
         g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                            RenderingHints.VALUE_STROKE_PURE);

         Rectangle2D box = shape.getBounds2D();

         if(arc) {
            Arc2D filled = new Arc2D.Double(box, 0, 360 * ratio, Arc2D.PIE);
            g.fill(filled);
         }
         else {
            double n = box.getHeight() * ratio;
            Area filled = new Area(new Rectangle2D.Double(box.getX(), box.getY(),
                                                          box.getWidth(), n));

            filled.intersect(new Area(shape));
            g.fill(filled);
         }

         g.draw(shape);
         g.dispose();
      }

      /**
       * Check if equals another objects.
       */
      public boolean equals(Object obj) {
         if(!super.equals(obj)) {
            return false;
         }

         return ((Fill) obj).ratio == ratio;
      }

      private double ratio;
   }

   private boolean arc;
   private static final long serialVersionUID = 1L;
}
