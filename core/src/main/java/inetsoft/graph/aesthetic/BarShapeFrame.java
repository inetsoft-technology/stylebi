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
import inetsoft.graph.scale.Scale;
import inetsoft.util.CoreTool;

import java.awt.*;
import java.awt.geom.GeneralPath;

/**
 * A profile shape displays a set of values as bars.
 *
 * @version 10.1
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=BarShapeFrame")
public class BarShapeFrame extends MultiShapeFrame {
   /**
    * Create a shape frame.
    */
   public BarShapeFrame() {
      setScaleOption(Scale.TICKS | Scale.ZERO);
   }

   /**
    * Create a bar shape frame.
    * @param fields the fields to plot bars in the shape.
    */
   @TernConstructor
   public BarShapeFrame(String... fields) {
      this();
      setFields(fields);
   }

   /**
    * Set the colors used to draw bars. If the color frame is not set, the
    * bars are drawn with outlines.
    */
   @TernMethod
   public void setColorFrame(ColorFrame colors) {
      this.colors = colors;
   }

   /**
    * Get the colors used to draw bars.
    */
   @TernMethod
   public ColorFrame getColorFrame() {
      return colors;
   }

   /**
    * Get a shape for the tuple. The values in the tuple have been scaled.
    */
   @Override
   protected GShape getShape(double... values) {
      return new BarShape(values);
   }

   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      BarShapeFrame frame = (BarShapeFrame) obj;
      return CoreTool.equals(colors, frame.colors);
   }

   private class BarShape extends GShape {
      public BarShape(double... values) {
         this.values = values;
      }

      @Override
      public double getMinSize() {
         return values.length * 5;
      }

      @Override
      public void paint(Graphics2D g, double x, double y, double w, double h) {
         double barw = w / values.length;

         g = (Graphics2D) g.create();
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

         if(colors == null && getLineColor() != null) {
            g.setColor(getLineColor());
         }

         for(int i = 0; i < values.length; i++) {
            if(Double.isNaN(values[i])) {
               continue;
            }

            GeneralPath path = new GeneralPath();
            double barh = h * values[i];
            double barx = x + barw * i;

            path.moveTo((float) barx, (float) y);
            path.lineTo((float) barx, (float) (y + barh));
            path.lineTo((float) (barx + barw), (float) (y + barh));
            path.lineTo((float) (barx + barw), (float) y);
            path.closePath();

            if(colors != null) {
               g.setColor(colors.getColor(getFields()[i]));
               g.fill(path);
            }
            else {
               g.draw(path);
            }
         }
      }

      @Override
      public Shape getShape(double x, double y, double w, double h) {
         double barw = w / values.length;
         GeneralPath path = new GeneralPath();

         for(int i = 0; i < values.length; i++) {
            if(Double.isNaN(values[i])) {
               continue;
            }

            double barh = h * values[i];
            double barx = x + barw * i;

            path.moveTo((float) barx, (float) y);
            path.lineTo((float) barx, (float) (y + barh));
            path.lineTo((float) (barx + barw), (float) (y + barh));
            path.lineTo((float) (barx + barw), (float) y);
            path.closePath();
         }

         return path;
      }

      /**
       * Check if equals another objects.
       */
      public boolean equals(Object obj) {
         if(!super.equals(obj)) {
            return false;
         }

         return equalsArray(values, ((BarShape) obj).values);
      }

      private double[] values;
   }

   private ColorFrame colors;
   private static final long serialVersionUID = 1L;
}
