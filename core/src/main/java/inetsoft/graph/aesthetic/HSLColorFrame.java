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

import com.inetsoft.build.tern.TernMethod;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import inetsoft.util.css.*;

import java.awt.*;

/**
 * This class defines a color frame for continuous numeric values. This is the
 * base class for color frames that using the HSL scale.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class HSLColorFrame extends LinearColorFrame {
   /**
    * Get the base color value of the color frame.
    */
   @TernMethod
   public Color getColor() {
      return userColor != null ? userColor : cssColor != null ? cssColor :
         defaultColor;
   }

   /**
    * Set the explicit color, which overrides the default and CSS. This can be
    * used in API or script.
    */
   @TernMethod
   public void setColor(Color color) {
      setUserColor(color);
   }

   /**
    * @hidden
    */
   public Color getDefaultColor() {
      return defaultColor;
   }

   /**
    * Set the base color value to the color frame.
    * @hidden
    */
   public void setDefaultColor(Color color) {
      this.defaultColor = color;
      init();
   }

   /**
    * @hidden
    */
   public Color getUserColor() {
      return userColor;
   }

   /**
    * @hidden
    */
   public void setUserColor(Color userColor) {
      this.userColor = userColor;
      init();
   }

   /**
    * @hidden
    */
   public Color getCssColor() {
      return cssColor;
   }

   /**
    * @hidden
    */
   public void setCssColor(Color cssColor) {
      this.cssColor = cssColor;
      init();
   }

   /**
    * Initialize a color frame that contains HSL values.
    */
   protected void init() {
      int r = getColor().getRed();
      int g = getColor().getGreen();
      int b = getColor().getBlue();
      double[] hsl = rgbToHSL(r, g, b);
      hue = hsl[0];
      saturation = hsl[1];
      brightness = hsl[2];
   }

   /**
    * Convert RGB to [Hue, Saturation, Lightness].
    * Convert hue, saturation, lightness to RGB values.
    * @param r the value of red.
    * @param g the value of green.
    * @param b the value of blue.
    */
   private double[] rgbToHSL(int r, int g, int b) {
      double varR = (r / 255.0);
      double varG = (g / 255.0);
      double varB = (b / 255.0);
      double varMin = Math.min(varR, Math.min(varG, varB));
      double varMax = Math.max(varR, Math.max(varG, varB));
      double deltaMax = varMax - varMin;
      double L = (varMax + varMin) / 2.0;
      double H = 0, S = 0;

      if(deltaMax == 0) {
         H = 0;
         S = 0;
      }
      else {
         if(L < 0.5) {
            S = deltaMax / (varMax + varMin);
         }
         else {
            S = deltaMax / (2 - varMax - varMin);
         }

         double deltaR = (((varMax - varR) / 6.0) + (deltaMax / 2.0)) / deltaMax;
         double deltaG = (((varMax - varG) / 6.0) + (deltaMax / 2.0)) / deltaMax;
         double deltaB = (((varMax - varB) / 6.0) + (deltaMax / 2.0)) / deltaMax;

         if(varR == varMax) {
            H = deltaB - deltaG;
         }
         else if(varG == varMax) {
            H = (1.0 / 3) + deltaR - deltaB;
         }
         else if(varB == varMax) {
            H = (2.0 / 3) + deltaG - deltaR;
         }

         if(H < 0) {
            H += 1;
         }
         else if(H > 1) {
            H -= 1;
         }
      }

      return new double[] {H, S, L};
   }

   /**
    * Convert hue, saturation, lightness to RGB values.
    * @param h the value of hue.
    * @param s the value of saturation.
    * @param l the value of lightness.
    */
   protected int[] hslToRGB(double h, double s, double l) {
      double r = 0, g = 0, b = 0;

      if(s == 0) {
         r = l * 255;
         g = l * 255;
         b = l * 255;
      }
      else {
         double var2;

         if(l < 0.5) {
            var2 = l * (1 + s);
         }
         else {
            var2 = (l + s) - (s * l);
         }

         double var1 = 2 * l - var2;

         r = 255 * hueToRGB(var1, var2, h + (1.0 / 3));
         g = 255 * hueToRGB(var1, var2, h);
         b = 255 * hueToRGB(var1, var2, h - (1.0 / 3));
      }

      return new int[] {(int) r, (int) g, (int) b};
   }

   /**
    * Calculate the rgb value, Called by hslToRGB.
    */
   private double hueToRGB(double v1, double v2, double vH) {
      if(vH < 0) {
         vH += 1;
      }

      if(vH > 1) {
         vH -= 1;
      }

      if((6 * vH) < 1) {
         return v1 + (v2 - v1) * 6 * vH;
      }

      if((2 * vH) < 1) {
         return v2;
      }

      if((3 * vH) < 2) {
         return v1 + (v2 - v1) * ((2.0 / 3) - vH ) * 6;
      }

      return v1;
   }

   @Override
   protected void updateCSSColors() {
      if(parentParams != null) {
         CSSDictionary cssDictionary = getCSSDictionary();
         CSSParameter cssParam = new CSSParameter(CSSConstants.CHART_PALETTE,
                                                  null, null, new CSSAttr("index", 1 + ""));
         cssColor = cssDictionary.getForeground(CSSParameter.getAllCSSParams(parentParams, cssParam));
      }
      else {
         cssColor = null;
      }

      init();
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      HSLColorFrame frame2 = (HSLColorFrame) obj;
      return CoreTool.equals(defaultColor, frame2.defaultColor) &&
         Tool.equals(cssColor, frame2.cssColor) && Tool.equals(userColor,
         frame2.userColor);
   }

   protected Color defaultColor;
   protected Color cssColor;
   protected Color userColor;
   protected double hue;
   protected double saturation;
   protected double brightness;
}
