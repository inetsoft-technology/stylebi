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
package inetsoft.graph.internal;

import java.awt.*;
import java.awt.image.RGBImageFilter;

/**
 * Change the hue of an image.
 *
 * @version 9.5
 * @author InetSoft Technology Corp
 */
class HueImageFilter extends RGBImageFilter {
   /**
    * Change to the specified rgb's hue.
    */
   public HueImageFilter(Color color) {
      this.color = color;

      a0 = color.getAlpha();
      r0 = color.getRed();
      g0 = color.getGreen();
      b0 = color.getBlue();
      
      canFilterIndexColorModel = true;

      float[] hsb = Color.RGBtoHSB(r0, g0, b0, null);
      hue = hsb[0];
      gray = hsb[1] == 0; // saturation == 0 
   }
    
   @Override
   public int filterRGB(int x, int y, int rgb) {
      int rgb2 = (rgb & 0xFFFFFF);

      if((rgb & 0xFF000000) == 0) { // transparent
	 return rgb;
      }
      else if(rgb2 == 0xffffff) { // most likely background
         return rgb;
      }
      
      int a = rgb & 0xFF000000;
      int r = rgb >>> 16 & 0xFF;
      int g = rgb >>> 8 & 0xFF;
      int b = rgb & 0xFF;

      // apply alpha
      if(a0 != 255) {
         a = (a >>> 24) * a0 / 255;
         a = a << 24;
      }

      if(r == g && g == b) { // gray scale -> color
         if(rgb2 == 0) {
            return color.getRGB();
         }

         double ratio = (255 - r) / 255.0;
         
         r = (int) (r0 * ratio);
         g = (int) (g0 * ratio);
         b = (int) (b0 * ratio);

         return 0xFF000000 | (r << 16) | (g << 8) | b;
      }
      
      float[] hsb = Color.RGBtoHSB((rgb >>> 16) & 0xFF, (rgb >>> 8) & 0xFF,
                                   rgb & 0xFF, null);
      // gray color should not be changed into color
      float sat = gray ? 0 : hsb[1];

      return Color.HSBtoRGB(hue, sat, hsb[2]) & 0xFFFFFF | a;
   }

   private float hue;
   private boolean gray;
   private Color color;
   private int a0;
   private int r0;
   private int g0;
   private int b0;
}
