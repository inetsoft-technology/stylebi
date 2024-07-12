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
import java.awt.geom.Rectangle2D;

/**
 * A thermo shape displays a thermometer like symbol. The fields controls
 * the height of the fill inside the thermometer, and the width of the
 * thermometer, respectively. The width field is optional.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=ThermoShapeFrame")
public class ThermoShapeFrame extends MultiShapeFrame {
   /**
    * Create a shape frame.
    */
   public ThermoShapeFrame() {
   }

   /**
    * Create a shape frame.
    * @param fields fields to get value to map to shapes.
    */
   @TernConstructor
   public ThermoShapeFrame(String... fields) {
      this();
      setFields(fields);
   }

   /**
    * Get a shape for the tuple. The values in the tuple have been scaled.
    */
   @Override
   protected GShape getShape(double... values) {
      return new ThermoShape(values);
   }

   private class ThermoShape extends GShape {
      public ThermoShape(double... values) {
         this.values = values;
         hratio = (values.length > 0) ? values[0] : 1;
         wratio = (values.length > 1) ? values[1] : 0.6;
      }

      @Override
      public double getMinSize() {
         return 8;
      }

      @Override
      public Shape getShape(double x, double y, double w, double h) {
         double w2 = 4 + wratio * (w - 4);

         x = x + (w - w2) / 2;
         return new Rectangle2D.Double(x, y, w2, h);
      }

      @Override
      public void paint(Graphics2D g, double x, double y, double w, double h) {
         paint(g, getShape(x, y, w, h));
      }

      @Override
      public void paint(Graphics2D g, Shape shape) {
         Color ocolor = g.getColor();

         if(getLineColor() != null) {
            g.setColor(getLineColor());
         }

         g.draw(shape);

         Rectangle2D box = shape.getBounds2D();
         double n = box.getHeight() * hratio;
         Shape oclip = g.getClip();

         if(getFillColor() != null) {
            g.setColor(getFillColor());
         }
         else {
            g.setColor(ocolor);
         }

         g.clip(shape);
         g.fill(new Rectangle2D.Double(box.getX(), box.getY(), box.getWidth(), n));

         g.setClip(oclip);
         g.setColor(ocolor);
      }

      /**
       * Check if equals another objects.
       */
      public boolean equals(Object obj) {
         if(!super.equals(obj)) {
            return false;
         }

         return hratio == ((ThermoShape) obj).hratio &&
            wratio == ((ThermoShape) obj).wratio &&
            equalsArray(values, ((ThermoShape) obj).values);
      }

      private double[] values;
      private double hratio;
      private double wratio;
   }

   private static final long serialVersionUID = 1L;
}
