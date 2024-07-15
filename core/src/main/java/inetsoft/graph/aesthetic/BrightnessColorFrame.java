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

import com.inetsoft.build.tern.*;

import java.awt.*;

/**
 * This class defines a continuous color frame that returns colors varying on
 * the brightness of the color.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=BrightnessColorFrame")
public class BrightnessColorFrame extends HSLColorFrame {
   /**
    * Create a color frame. The field needs to be set by calling setField.
    */
   public BrightnessColorFrame() {
      setDefaultColor(new Color(-8013589));
   }

   /**
    * Create a color frame.
    * @param field field to get value to map to color.
    */
   @TernConstructor
   public BrightnessColorFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Set the minimum brightness value.
    * @param min a value between 0 and 1.
    */
   @TernMethod
   public void setMinBrightness(double min) {
      this.min = min;
   }

   /**
    * Get the minimum brightness value.
    */
   @TernMethod
   public double getMinBrightness() {
      return min;
   }

   /**
    * Set the maximum brightness value.
    * @param max a value between 0 and 1.
    */
   @TernMethod
   public void setMaxBrightness(double max) {
      this.max = max;
   }

   /**
    * Get the maximum brightness value.
    */
   @TernMethod
   public double getMaxBrightness() {
      return max;
   }

   /**
    * Set whether to increase or decrease brightness for larger values.
    */
   @TernMethod
   public void setIncrease(boolean increase) {
      this.increase = increase;
   }

   /**
    * Check whether to increase or decrease brightness for larger values.
    */
   @TernMethod
   public boolean isIncrease() {
      return increase;
   }

   /*
    * Get the color.
    * @param ratio the value between 0 and 1,
    * and is the position of the value in a linear scale.
    */
   @Override
   @TernMethod
   public Color getColor(double ratio) {
      int alpha = (int) (getAlpha(ratio) * 255);
      ratio = min + (max - min) * (increase ? ratio : (1 - ratio));

      int[] rgb = hslToRGB(hue, saturation, ratio);
      return process(new Color(rgb[0], rgb[1], rgb[2], alpha), getBrightness());
   }

   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      BrightnessColorFrame frame = (BrightnessColorFrame) obj;
      return min == frame.min && max == frame.max &&
         increase == frame.increase;
   }

   // gray range, don't use white otherwise the vo disappears
   private double min = 0.2;
   private double max = 0.8;
   private boolean increase = true;
   private static final long serialVersionUID = 1L;
}
