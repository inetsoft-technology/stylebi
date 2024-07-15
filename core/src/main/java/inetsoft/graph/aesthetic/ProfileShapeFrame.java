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
import inetsoft.graph.scale.Scale;

import java.awt.*;
import java.awt.geom.GeneralPath;

/**
 * A profile shape displays a set of values as lines, similar to an area
 * graph.
 *
 * @version 10.1
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=ProfileShapeFrame")
public class ProfileShapeFrame extends MultiShapeFrame {
   /**
    * Create a shape frame.
    */
   public ProfileShapeFrame() {
      setScaleOption(Scale.TICKS | Scale.ZERO);
   }

   /**
    * Create a shape frame.
    */
   @TernConstructor
   public ProfileShapeFrame(String... fields) {
      this();
      setFields(fields);
   }

   /**
    * Get a shape for the tuple. The values in the tuple have been scaled.
    */
   @Override
   protected GShape getShape(double... values) {
      return new ProfileShape(values);
   }

   private class ProfileShape extends GShape {
      public ProfileShape(double... values) {
         this.values = values;
      }

      @Override
      public double getMinSize() {
         return values.length * 5;
      }

      @Override
      public Shape getShape(double x, double y, double w, double h) {
         double barw = w / (values.length - 1);
         GeneralPath path = new GeneralPath();
	 boolean first = false;

         path.moveTo((float) x, (float) y);

         for(int i = 0; i < values.length; i++) {
	    if(Double.isNaN(values[i])) {
	       first = true;
               continue;
            }

            double barh = h * values[i];
            double barx = x + barw * i;

            if(first) {
               path.moveTo((float) barx, (float) (y + barh));
               first = false;
            }
            else {
               path.lineTo((float) barx, (float) (y + barh));
            }
         }

         path.lineTo((float) (x + w), (float) y);
         path.lineTo((float) x, (float) y);

         return path;
      }

      /**
       * Check if equals another objects.
       */
      public boolean equals(Object obj) {
         if(!super.equals(obj)) {
            return false;
         }

         return equalsArray(values, ((ProfileShape) obj).values);
      }

      private double[] values;
   }

   private static final long serialVersionUID = 1L;
}
