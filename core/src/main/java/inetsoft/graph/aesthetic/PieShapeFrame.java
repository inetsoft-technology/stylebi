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
import inetsoft.graph.internal.Donut;
import inetsoft.graph.scale.Scale;
import inetsoft.util.CoreTool;

import java.awt.*;
import java.awt.geom.*;

/**
 * A profile shape displays a set of values as pies.
 *
 * @version 11.3
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=PieShapeFrame")
public class PieShapeFrame extends MultiShapeFrame {
   /**
    * Create a shape frame.
    */
   public PieShapeFrame() {
      setScaleOption(Scale.TICKS | Scale.ZERO);
   }

   /**
    * Create a pie shape frame.
    * @param fields the fields to plot bars in the shape.
    */
   @TernConstructor
   public PieShapeFrame(String... fields) {
      this();
      setFields(fields);
   }

   /**
    * Set the colors used to draw pie slices.
    */
   @TernMethod
   public void setColorFrame(ColorFrame colors) {
      this.colors = colors;
   }

   /**
    * Get the colors used to draw pie slices.
    */
   @TernMethod
   public ColorFrame getColorFrame() {
      return colors;
   }

   /**
    * Get a shape for the tuple. The values in the tuple have been scaled.
    */
   @Override
   @TernMethod
   protected GShape getShape(double... values) {
      return new PieShape(values);
   }

   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      PieShapeFrame frame = (PieShapeFrame) obj;
      return CoreTool.equals(colors, frame.colors);
   }

   /**
    * Check if this should draw a donut instead of pie.
    */
   @TernMethod
   public boolean isDonut() {
      return donut;
   }

   /**
    * Set whether this should draw a donut instead of pie.
    */
   @TernMethod
   public void setDonut(boolean donut) {
      this.donut = donut;
   }

   private class PieShape extends GShape {
      public PieShape(double... values) {
         this.values = values;
         total = 0;

         for(double value : values) {
            if(!Double.isNaN(value)) {
               total += value;
            }
         }
      }

      @Override
      public double getMinSize() {
         return 16;
      }

      @Override
      public void paint(Graphics2D g, double x, double y, double w, double h) {
         double startAngle = 0;
         double cx = x + w / 2;
         double cy = y + h / 2;
         double r = Math.min(w, h) / 2;

         g = (Graphics2D) g.create();
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
         g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                            RenderingHints.VALUE_STROKE_PURE);

         for(int i = 0; i < values.length; i++) {
            if(Double.isNaN(values[i])) {
               continue;
            }

            double angle = values[i] * 360 / total;

            if(i == values.length - 1) {
               angle = 360 - startAngle;
            }

            Shape shape = createSlice(cx, cy, r, -startAngle, -angle);
            Color color = (colors != null)
               ? colors.getColor(getFields()[i])
               : defcolors.getColor(i);

            g.setColor(color);
            g.fill(shape);

            startAngle += angle;
         }

         g.dispose();
      }

      @Override
      public Shape getShape(double x, double y, double w, double h) {
         double cx = x + w / 2;
         double cy = y + h / 2;
         double r = Math.min(w, h) / 2;
         return new Rectangle2D.Double(cx - r, cy - r, r * 2, r * 2);
      }

      private Shape createSlice(double cx, double cy, double r, double startAngle, double angle) {
         if(angle > 359.99) {
            if(donut) {
               return new Donut(cx - r, cy - r, r * 2, r * 2, r * 0.65, r * 0.65);
            }

            return new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
         }

         if(donut) {
            return new Donut(cx - r, cy - r, r * 2, r * 2, r * 0.75, r * 0.65, startAngle, angle);
         }

         return new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, startAngle, angle, Arc2D.PIE);
      }

      /**
       * Check if equals another objects.
       */
      public boolean equals(Object obj) {
         if(!super.equals(obj)) {
            return false;
         }

         return equalsArray(values, ((PieShape) obj).values);
      }

      private double[] values;
      private double total;
   }

   private ColorFrame colors;
   private CategoricalColorFrame defcolors = new CategoricalColorFrame();
   private boolean donut = false;
   private static final long serialVersionUID = 1L;
}
