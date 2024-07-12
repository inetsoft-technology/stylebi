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
package inetsoft.uql.viewsheet;

import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.Serializable;

/**
 * BorderColors contains border color information. The information will be
 * applied to render top/bottom/left/right borders with associated colors.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class BorderColors implements Serializable, Cloneable {
   /**
    * Constructor.
    */
   public BorderColors() {
      super();
   }

   /**
    * Constructor.
    */
   public BorderColors(Color tcolor, Color bcolor, Color lcolor, Color rcolor) {
      this();

      this.topColor = tcolor;
      this.bottomColor = bcolor;
      this.leftColor = lcolor;
      this.rightColor = rcolor;
   }

   /**
    * Get the string pattern.
    * @return the string pattern of this border colors.
    */
   public String getPattern() {
      return getColorPattern(topColor) + "," +  getColorPattern(bottomColor) +
         "," + getColorPattern(leftColor) + "," + getColorPattern(rightColor);
   }

   /**
    * Parse a string pattern.
    * @param val the specified string pattern.
    */
   public void parsePattern(String val) {
      String[] arr = Tool.split(val, ',');
      this.topColor = parseColorPattern(arr[0]);
      this.bottomColor = parseColorPattern(arr[1]);
      this.leftColor = parseColorPattern(arr[2]);
      this.rightColor = parseColorPattern(arr[3]);
   }

   /**
    * Get the string pattern of a color.
    * @param color the specified color.
    * @return the string pattern of the color.
    */
   private static String getColorPattern(Color color) {
      return color == null ? Tool.NULL : (color.getRGB() & 0xFFFFFF) + "";
   }

   /**
    * Parse a string pattern to create the associated color.
    * @param val the specified string pattern.
    * @return the created color if any, <tt>null</tt> otherwise.
    */
   private static Color parseColorPattern(String val) {
      return Tool.NULL.equals(val) ? null : new Color(Integer.parseInt(val));
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone BorderColors", ex);
      }

      return null;
   }

   /**
    * Get the string representation.
    * @return the string representation of this object.
    */
   public String toString() {
      return "BorderColors: [" + topColor + ", " + bottomColor + ", " +
             leftColor + ", " + rightColor + "]";
   }

   /**
    * Check if equals another objects.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof BorderColors)) {
         return false;
      }

      BorderColors bc = (BorderColors) obj;

      return Tool.equals(topColor, bc.topColor) &&
         Tool.equals(bottomColor, bc.bottomColor) &&
         Tool.equals(leftColor, bc.leftColor) &&
         Tool.equals(rightColor, bc.rightColor);
   }

   /**
    * Calculate the hash.
    */
   public int hashCode() {
      int hash = 0;
      
      if(topColor != null) {
         hash += topColor.getRGB();
      }

      if(bottomColor != null) {
         hash += bottomColor.getRGB();
      }

      if(leftColor != null) {
         hash += leftColor.getRGB();
      }

      if(rightColor != null) {
         hash += rightColor.getRGB();
      }

      return hash;
   }
   
   /**
    * Set the border colors.
    */
   public void setBorderColor(int idx, Color color) {
      switch(idx) {
         case 0:
            topColor = color;
            break;
         case 1:
            leftColor = color;
            break;
         case 2:
            bottomColor = color;
            break;
         case 3:
            rightColor = color;
            break;
      }
   }

   public Color topColor;
   public Color bottomColor;
   public Color leftColor;
   public Color rightColor;

   private static final Logger LOG =
      LoggerFactory.getLogger(BorderColors.class);
}
