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
import inetsoft.util.Tool;
import inetsoft.util.css.*;

import java.awt.*;

/**
 * This is a color frame that creates gradient colors
 * between the two given colors.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=GradientColorFrame")
public class GradientColorFrame extends RGBCubeColorFrame {
   /**
    * Create a color frame. The field needs to be set by calling setField.
    */
   public GradientColorFrame() {
      init();
   }

   /**
    * Create a color frame.
    * @param field field to get value to map to color.
    */
   @TernConstructor
   public GradientColorFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Initialize a color frame that contains HSL values.
    */
   private void init() {
      int rgb1 = getFromColor().getRGB();
      int rgb2 = getToColor().getRGB();
      a1 = (rgb1 >> 24) & 0xFF;
      r1 = (rgb1 >> 16) & 0xFF;
      g1 = (rgb1 >> 8) & 0xFF;
      b1 = (rgb1) & 0xFF;
      int a2 = (rgb2 >> 24) & 0xFF;
      int r2 = (rgb2 >> 16) & 0xFF;
      int g2 = (rgb2 >> 8) & 0xFF;
      int b2 = (rgb2) & 0xFF;
      disA = a2 - a1;
      disR = r2 - r1;
      disG = g2 - g1;
      disB = b2 - b1;
   }

   /**
    * Get the color.
    * @param ratio the value between 0 and 1,
    * and is the position of the value in a linear scale.
    */
   @Override
   @TernMethod
   public Color getColor(double ratio) {
      int a = (int) (a1 + disA * ratio);
      int r = (int) (r1 + disR * ratio);
      int g = (int) (g1 + disG * ratio);
      int b = (int) (b1 + disB * ratio);
      double alpha = getAlpha(ratio);

      if(alpha != 1) {
         a = (int) (alpha * 255);
      }

      int rgb = a << 24 | r << 16 | g << 8 | b;

      return process(new Color(rgb), getBrightness());
   }

   /**
    * Get the end color value.
    */
   @TernMethod
   public Color getToColor() {
      return userTo != null ? userTo : cssTo != null ? cssTo : defaultTo;
   }

   /**
    * Get the from color value.
    */
   @TernMethod
   public Color getFromColor() {
      return userFrom != null ? userFrom : cssFrom != null ? cssFrom :
         defaultFrom;
   }

   /**
    * Set the from color value.
    */
   @TernMethod
   public void setFromColor(Color from) {
      setUserFromColor(from);
   }

   /**
    * Set the end color value.
    */
   @TernMethod
   public void setToColor(Color to) {
      setUserToColor(to);
   }

   /**
    * Set the from color value.
    * @hidden
    */
   public void setDefaultFromColor(Color from) {
      this.defaultFrom = from;
      init();
   }

   /**
    * Set the end color value.
    * @hidden
    */
   public void setDefaultToColor(Color to) {
      this.defaultTo = to;
      init();
   }

   /**
    * @hidden
    */
   public Color getDefaultFromColor() {
      return defaultFrom;
   }

   /**
    * @hidden
    */
   public Color getDefaultToColor() {
      return defaultTo;
   }

   /**
    * @hidden
    */
   public Color getCssFromColor() {
      return cssFrom;
   }

   /**
    * @hidden
    */
   public void setCssFromColor(Color cssFrom) {
      this.cssFrom = cssFrom;
      init();
   }

   /**
    * @hidden
    */
   public Color getCssToColor() {
      return cssTo;
   }

   /**
    * @hidden
    */
   public void setCssToColor(Color cssTo) {
      this.cssTo = cssTo;
      init();
   }

   /**
    * @hidden
    */
   public Color getUserFromColor() {
      return userFrom;
   }

   /**
    * @hidden
    */
   public void setUserFromColor(Color userFrom) {
      this.userFrom = userFrom;
      init();
   }

   /**
    * @hidden
    */
   public Color getUserToColor() {
      return userTo;
   }

   /**
    * @hidden
    */
   public void setUserToColor(Color userTo) {
      this.userTo = userTo;
      init();
   }

   @Override
   protected void updateCSSColors() {
      if(parentParams != null) {
         CSSDictionary cssDictionary = getCSSDictionary();
         CSSParameter cssParam = new CSSParameter(CSSConstants.CHART_PALETTE,
            null, null, new CSSAttr("index", 1 + ""));
         cssFrom = cssDictionary.getForeground(CSSParameter.getAllCSSParams(parentParams, cssParam));

         cssParam = new CSSParameter(CSSConstants.CHART_PALETTE, null, null,
                                     new CSSAttr("index", 2 + ""));
         cssTo = cssDictionary.getForeground(CSSParameter.getAllCSSParams(parentParams, cssParam));
      }
      else {
         cssFrom = null;
         cssTo = null;
      }

      init();
   }

   /**
    * Check if equals another object.
    */
   @Override
   public boolean equals(Object obj) {
      if(!(super.equals(obj))) {
         return false;
      }

      GradientColorFrame frame2 = (GradientColorFrame) obj;
      return defaultFrom.equals(frame2.defaultFrom) && defaultTo
         .equals(frame2.defaultTo) && Tool.equals(cssFrom, frame2.cssFrom) &&
         Tool.equals(cssTo, frame2.cssTo) && Tool.equals(userFrom,
         frame2.userFrom) && Tool.equals(userTo, frame2.userTo);
   }

   private int a1, r1, g1, b1;
   private int disA, disR, disG, disB;
   private Color defaultFrom = new Color(0xFF99CC);
   private Color defaultTo = new Color(0x008000);
   private Color cssFrom;
   private Color cssTo;
   private Color userFrom;
   private Color userTo;
   private static final long serialVersionUID = 1L;
}
