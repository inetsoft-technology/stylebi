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
package inetsoft.graph.rgb;

import inetsoft.graph.aesthetic.LinearColorFrame;

import java.awt.*;
import java.io.ObjectInputStream;
import java.util.function.Function;

/**
 * This class defines a color frame for numeric values using B-spline interpolation.
 *
 * @version 12.3
 * @author InetSoft Technology
 */
public abstract class SplineColorFrame extends LinearColorFrame {
   /**
    * Get a color at the relative scale.
    * @param ratio a value from 0 to 1.
    */
   @Override
   public Color getColor(double ratio) {
      Color color = interpolator.apply(ratio);
      double alpha = getAlpha(ratio);

      if(alpha != 1) {
         color = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (alpha * 255));
      }

      return color;
   }

   /**
    * Set the colors to interpolate.
    */
   protected void setColors(Color[] colors) {
      this.colors = colors;
      this.interpolator = Rgb.rgbBasis.apply(colors);
   }

   private void readObject(ObjectInputStream s) throws ClassNotFoundException, java.io.IOException {
      s.defaultReadObject();
      this.setColors(colors);
   }

   @Override
   public boolean equals(Object obj) {
      // equals used to see if two frames product the same mapping and legend.
      // SplineColorFrame is hardcoded (e.g. BluesColorFrame) so only need to compare class.
      return obj != null && obj.getClass() == getClass();
   }

   @Override
   public int hashCode() {
      return getClass().hashCode();
   }

   private transient Function<Double, Color> interpolator;
   private Color[] colors;
}
