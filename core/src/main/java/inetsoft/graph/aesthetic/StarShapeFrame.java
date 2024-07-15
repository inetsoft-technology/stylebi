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

import com.inetsoft.build.tern.TernClass;
import com.inetsoft.build.tern.TernConstructor;

import java.awt.*;
import java.awt.geom.GeneralPath;

/**
 * A polygon shape displays a set of values as the points on the polygon
 * with the value as the distance of the points from the center of
 * the polygon.
 *
 * @version 10.1
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=StarShapeFrame")
public class StarShapeFrame extends MultiShapeFrame {
   /**
    * Create a shape frame.
    */
   public StarShapeFrame() {
   }

   /**
    * Create a shape frame.
    * @param fields fields to get value to map to shapes.
    */
   @TernConstructor
   public StarShapeFrame(String... fields) {
      this();
      setFields(fields);
   }

   /**
    * Get a shape for the tuple. The values in the tuple have been scaled.
    */
   @Override
   protected GShape getShape(double... values) {
      return new StarShape(values);
   }

   /**
    * Get the tuple for legend.
    * @param val the legend item field name.
    */
   @Override
   protected double[] getLegendTuple(Object val) {
      double[] values = super.getLegendTuple(val);

      // make the legend look like a start shape instead of a line
      for(int i = 0; i < values.length; i++) {
         values[i] = Math.max(values[i], 0.2);
      }

      return values;
   }

   private class StarShape extends GShape {
      public StarShape(double... values) {
         this.values = values;
      }

      @Override
      public double getMinSize() {
         return 32;
      }

      @Override
      public Shape getShape(double x, double y, double w, double h) {
         double slice = (Math.PI * 2) / values.length;
         double r = Math.min(w, h) / 2;
         double cx = x + w / 2;
         double cy = y + h / 2;
         GeneralPath path = new GeneralPath();

         for(int i = 0; i < values.length; i++) {
            double r2 = r * values[i];
            double x2 = cx + r2 * Math.cos(slice * i);
            double y2 = cy + r2 * Math.sin(slice * i);

            if(i == 0) {
               path.moveTo((float) x2, (float) y2);
            }
            else {
               path.lineTo((float) x2, (float) y2);
            }
         }

         path.closePath();

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

         return equalsArray(values, ((StarShape) obj).values);
      }

      private double[] values;
   }

   private static final long serialVersionUID = 1L;
}
