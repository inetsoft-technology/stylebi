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
package inetsoft.report;

import java.awt.*;

/**
 * This defines a size in inches.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class Size implements java.io.Serializable {
   /**
    * Create a zero size.
    */
   public Size() {
      this(0, 0);
   }

   /**
    * Create a Size of specified dimension.
    * @param w width in inches.
    * @param h height in inches.
    */
   public Size(float w, float h) {
      width = w;
      height = h;
   }

   /**
    * Create a Size of specified dimension.
    * @param w width in inches.
    * @param h height in inches.
    */
   public Size(double w, double h) {
      this((float) w, (float) h);
   }

   /**
    * Create a size from a dimension object. No resolution conversion is
    * done.
    */
   public Size(Dimension d) {
      this(d.width, d.height);
   }

   /**
    * Create a size from a size object.
    */
   public Size(Size d) {
      this(d.width, d.height);
   }

   /**
    * Create a size. The sizes are converted to inches using the pixel
    * size and resolution.
    * @param w width in pixels.
    * @param h height in pixels.
    * @param res resolution.
    */
   public Size(int w, int h, int res) {
      width = (float) w / res;
      height = (float) h / res;
   }

   /**
    * Get the size if the area is rotated (landscape mode).
    * @return a new Size object with width and height switched.
    */
   public Size rotate() {
      return new Size(height, width);
   }

   /**
    * Get a dimension object in integers.
    */
   public Dimension getDimension() {
      return new Dimension((int) width, (int) height);
   }

   /**
    * Set a dimension object in integers.
    */
   public void setDimension(Dimension dimentsion) {
      this.width = dimentsion.width;
      this.height = dimentsion.height;
   }

   public String toString() {
      return width + "x" + height;
   }

   public boolean equals(Object obj) {
      if(obj instanceof Size) {
         return width == ((Size) obj).width && height == ((Size) obj).height;
      }

      return false;
   }

   /**
    * Width in inches.
    */
   public float width;
   /**
    * Height in inches.
    */
   public float height;
}

