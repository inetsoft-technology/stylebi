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

import com.inetsoft.build.tern.TernClass;
import com.inetsoft.build.tern.TernConstructor;

import java.awt.*;
import java.awt.geom.GeneralPath;

/**
 * A star shape displays a star, with each stem representing a value. The value
 * is encoded as the length of the stem.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=SunShapeFrame")
public class SunShapeFrame extends MultiShapeFrame {
   /**
    * Create a shape frame.
    */
   public SunShapeFrame() {
   }

   /**
    * Create a shape frame.
    * @param fields fields to get value to map to shapes.
    */
   @TernConstructor
   public SunShapeFrame(String... fields) {
      this();
      setFields(fields);
   }

   /**
    * Get a shape for the tuple. The values in the tuple have been scaled.
    */
   @Override
   protected GShape getShape(double... values) {
      return new SunShape(values);
   }

   private class SunShape extends GShape {
      public SunShape(double... values) {
         this.values = values;
      }

      @Override
      public double getMinSize() {
         return 20;
      }

      @Override
      public Shape getShape(double x, double y, double w, double h) {
         double cx = x + w / 2;
         double cy = y + h / 2;
         double r = Math.min(w / 2, h / 2);
         double base = 2; // the length for the min
         GeneralPath path = new GeneralPath();

         for(int i = 0; i < values.length; i++) {
            double r0 = (r - base) * values[i] + base;

            double sx = cx + r0 * Math.cos(Math.PI * 2 * i / values.length);
            double sy = cy + r0 * Math.sin(Math.PI * 2 * i / values.length);

            path.moveTo((float) cx, (float) cy);
            path.lineTo((float) sx, (float) sy);
         }

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

         return equalsArray(values, ((SunShape) obj).values);
      }

      private double[] values;
   }

   private static final long serialVersionUID = 1L;
}
